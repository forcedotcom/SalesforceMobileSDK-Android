/*
 * Copyright (c) 2026-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.mobilesync.manager

import com.salesforce.androidsdk.mobilesync.manager.SyncManager.MobileSyncException
import com.salesforce.androidsdk.mobilesync.manager.SyncManager.SyncUpdateCallback
import com.salesforce.androidsdk.mobilesync.target.QueueSyncUpTarget
import com.salesforce.androidsdk.mobilesync.target.QueueSyncUpTarget.Outcome
import com.salesforce.androidsdk.mobilesync.target.SyncTarget
import com.salesforce.androidsdk.mobilesync.util.MobileSyncLogger
import com.salesforce.androidsdk.mobilesync.util.SyncQueue
import com.salesforce.androidsdk.mobilesync.util.SyncState
import com.salesforce.androidsdk.smartstore.store.SmartStore
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Task running a sync up whose target is a [QueueSyncUpTarget]: it replays the pending entries of the
 * sync-up change queue in `sequence` order, strictly one at a time (never reordered, never batched).
 *
 * - Success: the entry is removed. A CREATE gives the record its server id and the local id is
 *   replaced by the server id in the pending entries; an UPDATE cleans the record unless a later
 *   entry for it is pending; a DELETE removes the record.
 * - Record-level error (HTTP 4xx, missing record, soup or type): the sync goes on. The error is
 *   written in the entry (`lastError`, `attempts` + 1) and in the `__last_error__` field of the record.
 *   The later entries of that record, and those pointing to it, are deferred to the next run (left
 *   as they are), so that a stale edit is never sent after a newer one.
 * - Transport error (no response, 5xx, `REQUEST_LIMIT_EXCEEDED`): `attempts` + 1 on the entry, which
 *   stays first in line, and the sync fails. Running the sync again retries it.
 */
open class QueueSyncUpTask(syncManager: SyncManager, sync: SyncState, callback: SyncUpdateCallback?) :
    SyncTask(syncManager, sync, callback) {

    /** (soup name, local id) to server id of the records created during this run (not persisted) */
    private val idMap = HashMap<Pair<String, String>, String>()

    /** Local id to server id, for the references to records (any soup) found in `changes` */
    private val refMap = HashMap<String, String>()

    /** Records (soup name, id) with an entry rejected during this run: their later entries wait for the next run */
    private val blockedRecords = HashSet<Pair<String, String>>()

    /** (object type, id) of the blocked records: what an upsert lookup is compared with */
    private val blockedTypedIds = HashSet<Pair<String, String>>()

    /** (object type, external id value) of the blocked upsert CREATEs, which have no server id to compare */
    private val blockedUpserts = HashSet<Pair<String, String>>()

    /**
     * Object types whose later entries for a server id wait: an upsert CREATE was rejected and the
     * record it would land on could not be determined (inconclusive lookup)
     */
    private val blockedTypes = HashSet<String>()

    /** Destination lookups of this run, by (object type, external id value): each value is looked up once
     * as long as no write succeeded since (a write may move an external id, so the cache is cleared) */
    private val destinations = HashMap<Pair<String, String>, QueueSyncUpTarget.Destination>()

    /** Number of entries after the one being replayed (a destination lookup is useless when 0) */
    private var remaining = 0

    /** Ids of the blocked records, to also defer the entries that point to them */
    private val blockedIds = HashSet<String>()

    private val store: SmartStore
        get() = syncManager.smartStore

    @Throws(Exception::class)
    override fun runSync() {
        // One queue replay at a time per store, for the whole run (done, failed or stopped): a queue
        // sync of another sync manager on the same store waits here, then reads the fresh queue state
        lockFor(store).withLock { replayQueue(sync.target as QueueSyncUpTarget) }
    }

    @Throws(Exception::class)
    private fun replayQueue(target: QueueSyncUpTarget) {
        // Entries are read once; each one is read again right before it is replayed since replaying
        // a CREATE rewrites the entries that follow
        val entryIds = SyncQueue.pendingEntryIds(store)
        sync.totalSize = entryIds.size
        updateSync(sync, SyncState.Status.RUNNING, 0, callback)
        for ((i, entryId) in entryIds.withIndex()) {
            checkIfStopRequested()
            val entry = SyncQueue.readEntry(store, entryId)
            remaining = entryIds.size - i - 1
            if (entry != null && entry.optString(SyncQueue.STATUS) == SyncQueue.Status.PENDING.name) {
                if (isDeferred(target, entryId, entry)) {
                    defer(target, entryId, entry)
                } else {
                    replay(target, entryId, entry)
                }
            }
            val progress = (i + 1) * 100 / entryIds.size
            if (progress < 100) {
                updateSync(sync, SyncState.Status.RUNNING, progress, callback)
            }
        }
    }

    /**
     * True if the entry must wait for the next run: an earlier entry of the same record was rejected
     * (sending this one first would let a stale edit be overwritten by a newer one), or the entry
     * points to such a record (`parentRef` or a top-level value of `changes`). An upsert CREATE is
     * also deferred when it would land on such a record: same external id as a blocked upsert, or,
     * after a lookup (only while something is blocked), the id of a blocked record of its type.
     * A failing lookup fails the sync like any transport error; an inconclusive one defers the CREATE.
     */
    @Throws(Exception::class)
    private fun isDeferred(target: QueueSyncUpTarget, entryId: Long, entry: JSONObject): Boolean {
        if (blockedRecords.isEmpty()) return false
        val soupName = entry.optString(SyncQueue.SOUP_NAME)
        val sobjectType = entry.optString(SyncQueue.SOBJECT_TYPE)
        val recordId = mapId(soupName, entry.optString(SyncQueue.RECORD_ID))
        val kind = entry.optString(SyncQueue.KIND)
        // Deleting a record that never reached the server makes no call: nothing to order
        if (kind == SyncQueue.Kind.DELETE.name && SyncTarget.isLocalId(recordId)) return false
        if (SyncTarget.isLocalId(recordId)) {
            // Local ids are unique in the store: the soup tells nothing more
            if ((soupName to recordId) in blockedRecords) return true
        } else {
            // A server record is one record whatever the soup that holds a copy of it
            if ((sobjectType to recordId) in blockedTypedIds || sobjectType in blockedTypes) return true
        }
        if (entry.optString(SyncQueue.PARENT_REF) in blockedIds) return true
        val changes = entry.optJSONObject(SyncQueue.CHANGES)
        if (changes != null && changes.keys().asSequence().any { (changes.opt(it) as? String) in blockedIds }) return true
        // An upsert can land on an existing record that has a blocked entry
        val externalId = if (kind == SyncQueue.Kind.CREATE.name) target.entryUpsertExternalId(entry) else null
        if (externalId != null) {
            if ((sobjectType to externalId) in blockedUpserts) return true
            val destination = resolveDestination(target, entryId, entry, sobjectType, externalId)
            return when (destination.kind) {
                QueueSyncUpTarget.DestinationKind.FOUND -> (sobjectType to destination.id!!) in blockedTypedIds
                QueueSyncUpTarget.DestinationKind.NOT_FOUND -> false
                else -> {
                    blockedTypes.add(sobjectType)
                    true
                }
            }
        }
        return false
    }

    /**
     * Leaves the entry untouched (no send, no attempt, no error), and blocks what depends on it. A deferred
     * upsert CREATE may land on an existing record: that destination is resolved too, so that later
     * entries for it wait (unless nothing follows).
     */
    @Throws(Exception::class)
    private fun defer(target: QueueSyncUpTarget, entryId: Long, entry: JSONObject) {
        val soupName = entry.optString(SyncQueue.SOUP_NAME)
        block(target, soupName, mapId(soupName, entry.optString(SyncQueue.RECORD_ID)), entry)
        if (remaining > 0) blockDestination(target, entryId, entry, counted = false)
    }

    private fun block(target: QueueSyncUpTarget, soupName: String, recordId: String, entry: JSONObject) {
        val sobjectType = entry.optString(SyncQueue.SOBJECT_TYPE)
        if (entry.optString(SyncQueue.KIND) == SyncQueue.Kind.CREATE.name) {
            target.entryUpsertExternalId(entry)?.let { blockedUpserts.add(sobjectType to it) }
        }
        if (recordId.isNotEmpty()) {
            blockedTypedIds.add(sobjectType to recordId)
            blockedRecords.add(soupName to recordId)
            blockedIds.add(recordId)
        }
    }

    @Throws(Exception::class)
    private fun replay(target: QueueSyncUpTarget, entryId: Long, entry: JSONObject) {
        val soupName = entry.optString(SyncQueue.SOUP_NAME)
        val recordId = mapId(soupName, entry.optString(SyncQueue.RECORD_ID))
        val sobjectType = entry.optString(SyncQueue.SOBJECT_TYPE)
        val kind = SyncQueue.Kind.entries.firstOrNull { it.name == entry.optString(SyncQueue.KIND) }
        val changes = entry.optJSONObject(SyncQueue.CHANGES)?.let { mapChanges(it) }

        // Problems with the entry or its record are record-level errors
        val recordEntryId = findRecordEntryId(target, soupName, recordId)
        val problem = when {
            kind == null -> "Unknown kind of queue entry: ${entry.opt(SyncQueue.KIND)}"
            sobjectType.isEmpty() -> "Queue entry has no ${SyncQueue.SOBJECT_TYPE}"
            kind == SyncQueue.Kind.CREATE && target.entryUnsupportedExternalIdType(entry) != null ->
                target.entryUnsupportedExternalIdType(entry)
            kind == SyncQueue.Kind.UPDATE && target.entryImpreciseUpdateProblem(entry) != null ->
                target.entryImpreciseUpdateProblem(entry)
            !store.hasSoup(soupName) -> "Soup $soupName does not exist"
            recordEntryId == -1L -> "Record $recordId does not exist in $soupName"
            else -> null
        }
        if (problem != null || kind == null) {
            recordError(target, entryId, entry, soupName, recordId, recordEntryId, problem ?: "Invalid queue entry", sent = false)
            return
        }

        // A record that was never sent cannot exist on the server: nothing to delete there
        val result = if (kind == SyncQueue.Kind.DELETE && SyncTarget.isLocalId(recordId)) {
            QueueSyncUpTarget.ReplayResult(Outcome.SUCCESS, serverChanged = false)
        } else try {
            target.replay(syncManager, kind, sobjectType, recordId, changes)
        } catch (e: Exception) {
            // No usable answer (IOException, unparseable body, missing id...): the sync fails
            recordFailure(entryId, entry, e.message ?: e.toString())
            throw e
        }
        when (result.outcome) {
            Outcome.SUCCESS -> {
                // Only a write the server accepted (2xx) can move an external id
                if (result.serverChanged) destinations.clear()
                onSuccess(target, entryId, entry, kind, soupName, recordId, recordEntryId, result.serverId)
            }
            Outcome.RECORD_ERROR -> recordError(target, entryId, entry, soupName, recordId, recordEntryId, result.error ?: "Unknown error")
            Outcome.TRANSPORT_ERROR -> {
                recordFailure(entryId, entry, result.error ?: "Unknown error")
                throw MobileSyncException("Sync up of $kind $sobjectType $recordId failed: ${result.error}")
            }
        }
    }

    /** Returns the soup entry id of the record, or -1 if the soup, the index or the record is missing */
    @Throws(JSONException::class)
    private fun findRecordEntryId(target: QueueSyncUpTarget, soupName: String, recordId: String): Long =
        if (store.hasSoup(soupName) && store.hasIndexForPath(soupName, target.idFieldName)) {
            store.lookupSoupEntryId(soupName, target.idFieldName, recordId)
        } else {
            -1L
        }

    @Throws(JSONException::class)
    private fun onSuccess(
        target: QueueSyncUpTarget,
        entryId: Long,
        entry: JSONObject,
        kind: SyncQueue.Kind,
        soupName: String,
        recordId: String,
        recordEntryId: Long,
        serverId: String?
    ) {
        SyncQueue.inTransaction(store, soupName) {
            store.delete(SyncQueue.QUEUE_SOUP, entryId)
            val record = store.retrieve(soupName, recordEntryId).optJSONObject(0)
            when (kind) {
                SyncQueue.Kind.CREATE -> {
                    if (serverId.isNullOrBlank()) throw MobileSyncException("No id returned for $recordId")
                    idMap[soupName to recordId] = serverId
                    refMap[recordId] = serverId
                    // Rewrite the entries first: the check below must see them under the server id
                    rewritePendingEntries(soupName, recordId, serverId)
                    if (record != null) {
                        // The server record may already be in the soup (sync down, upsert that hit an existing record)
                        val existing = store.lookupSoupEntryId(soupName, target.idFieldName, serverId)
                        if (existing != -1L && existing != recordEntryId) {
                            // Fields the upsert did not carry stay as the existing row has them; the CREATE row's own win
                            store.retrieve(soupName, existing).optJSONObject(0)?.let { other ->
                                for (key in other.keys()) {
                                    if (!record.has(key) && key != SmartStore.SOUP_ENTRY_ID && key != SmartStore.SOUP_LAST_MODIFIED_DATE) {
                                        record.put(key, other.get(key))
                                    }
                                }
                            }
                            store.delete(soupName, existing)
                            // The deleted row held the later local edits of that record: they are
                            // queued after this CREATE, so they go over the surviving row, in order
                            applyLaterEntries(record, soupName, serverId, entry.getLong(SyncQueue.SEQUENCE))
                        }
                        record.put(target.idFieldName, serverId)
                        record.remove(SyncTarget.LAST_ERROR) // the CREATE went through
                        updateFlags(target, record, SyncQueue.otherEntryKinds(store, soupName, serverId, entryId), created = true)
                        updateOrThrow(soupName, record, recordEntryId)
                    } else {
                        MobileSyncLogger.w(TAG, "Record $recordId is gone from $soupName, only queue entries are updated")
                    }
                }

                SyncQueue.Kind.UPDATE -> {
                    if (record != null) {
                        updateFlags(target, record, SyncQueue.otherEntryKinds(store, soupName, recordId, entryId), created = false)
                        updateOrThrow(soupName, record, recordEntryId)
                    }
                }

                SyncQueue.Kind.DELETE -> {
                    // The record is gone: its other entries (a rejected CREATE or UPDATE, for instance)
                    // have nothing left to apply to
                    SyncQueue.deleteEntriesForRecord(store, soupName, recordId)
                    if (record != null) store.delete(soupName, recordEntryId)
                }
            }
        }
    }

    /** Applies the `changes` of the entries queued after [sequence] for the record, in sequence order, and its pending DELETE */
    @Throws(JSONException::class)
    private fun applyLaterEntries(record: JSONObject, soupName: String, recordId: String, sequence: Long) {
        for (later in SyncQueue.entriesAfter(store, soupName, recordId, sequence)) {
            when (later.optString(SyncQueue.KIND)) {
                SyncQueue.Kind.UPDATE.name -> later.optJSONObject(SyncQueue.CHANGES)?.let { changes ->
                    for (key in changes.keys()) record.put(key, changes.get(key))
                }
                SyncQueue.Kind.DELETE.name -> record.put(SyncTarget.LOCALLY_DELETED, true)
            }
        }
    }

    /**
     * Sets the dirty flags of a record after one of its entries was replayed: clean when no later
     * entry is pending, otherwise still dirty for them (`__locally_deleted__` is left as it is, so a
     * pending DELETE stays flagged). `__last_error__` is removed only when the record is cleaned.
     */
    @Throws(JSONException::class)
    private fun updateFlags(target: QueueSyncUpTarget, record: JSONObject, others: Set<SyncQueue.Kind>, created: Boolean) {
        if (others.isEmpty()) {
            target.cleanRecord(record)
            return
        }
        // Still dirty: the other entries (earlier failed ones included) are unsent edits, so the
        // error of the record is kept
        if (created) record.put(SyncTarget.LOCALLY_CREATED, false)
        record.put(SyncTarget.LOCAL, true)
        record.put(SyncTarget.LOCALLY_UPDATED, SyncQueue.Kind.UPDATE in others)
    }

    /** Replaces [localId] by [serverId] in the pending entries that reference it: as `recordId` for the
     * entries of [soupName] (the soup of the created record), as `parentRef` or `changes` value for any entry */
    @Throws(JSONException::class)
    private fun rewritePendingEntries(soupName: String, localId: String, serverId: String) {
        for (pending in SyncQueue.findPendingEntriesReferencing(store, localId)) {
            if (SyncQueue.replaceId(pending, soupName, localId, serverId)) {
                updateOrThrow(SyncQueue.QUEUE_SOUP, pending, pending.getLong(SmartStore.SOUP_ENTRY_ID))
            }
        }
    }

    /** SmartStore answers null when it did not write: throwing rolls the whole transaction back */
    @Throws(JSONException::class)
    private fun updateOrThrow(soupName: String, element: JSONObject, soupEntryId: Long, handleTx: Boolean = false) {
        store.update(soupName, element, soupEntryId, handleTx)
            ?: throw MobileSyncException("Failed to update entry $soupEntryId of $soupName")
    }

    /**
     * Records the error in the entry and in the record, then lets the sync go on with unrelated
     * records: the later entries of this record, and those pointing to it, wait for the next run
     */
    @Throws(JSONException::class)
    private fun recordError(
        target: QueueSyncUpTarget,
        entryId: Long,
        entry: JSONObject,
        soupName: String,
        recordId: String,
        recordEntryId: Long,
        error: String,
        sent: Boolean = true
    ) {
        MobileSyncLogger.w(TAG, "Queue entry $entryId failed: $error")
        block(target, soupName, recordId, entry)
        SyncQueue.inTransaction(store, soupName) {
            if (recordEntryId != -1L && store.hasSoup(soupName)) {
                store.retrieve(soupName, recordEntryId).optJSONObject(0)?.let { record ->
                    record.put(SyncTarget.LAST_ERROR, error)
                    updateOrThrow(soupName, record, recordEntryId)
                }
            }
            entry.put(SyncQueue.LAST_ERROR, error)
            entry.put(SyncQueue.ATTEMPTS, entry.optInt(SyncQueue.ATTEMPTS) + 1)
            updateOrThrow(SyncQueue.QUEUE_SOUP, entry, entryId)
        }
        if (sent && remaining > 0) blockDestination(target, entryId, entry, counted = true)
    }

    /**
     * An upsert CREATE that was rejected or deferred may be meant for an existing record: later entries
     * that address that record (any soup) must not go before it. The destination is resolved by the id
     * lookup: found, its id is blocked; not found, nothing more to block; inconclusive, every later entry
     * of the type for a server id waits; a failed lookup fails the sync (one attempt counted on the entry).
     */
    @Throws(Exception::class)
    private fun blockDestination(target: QueueSyncUpTarget, entryId: Long, entry: JSONObject, counted: Boolean) {
        if (entry.optString(SyncQueue.KIND) != SyncQueue.Kind.CREATE.name) return
        val externalId = target.entryUpsertExternalId(entry) ?: return
        val sobjectType = entry.optString(SyncQueue.SOBJECT_TYPE)
        val destination = resolveDestination(target, entryId, entry, sobjectType, externalId, counted)
        when (destination.kind) {
            QueueSyncUpTarget.DestinationKind.FOUND -> {
                blockedTypedIds.add(sobjectType to destination.id!!)
                blockedIds.add(destination.id)
            }
            QueueSyncUpTarget.DestinationKind.NOT_FOUND -> {}
            else -> blockedTypes.add(sobjectType)
        }
    }

    /**
     * Looks up the record an upsert would land on, once per (object type, value) in a run. A failed
     * lookup fails the sync and counts one attempt on the entry, unless [counted] (the entry was just rejected).
     */
    @Throws(Exception::class)
    private fun resolveDestination(
        target: QueueSyncUpTarget,
        entryId: Long,
        entry: JSONObject,
        sobjectType: String,
        externalId: String,
        counted: Boolean = false
    ): QueueSyncUpTarget.Destination {
        destinations[sobjectType to externalId]?.let { return it }
        val destination = try {
            target.lookupDestination(syncManager, sobjectType, externalId)
        } catch (e: Exception) {
            if (!counted) recordFailure(entryId, entry, e.message ?: e.toString())
            throw e
        }
        if (destination.kind == QueueSyncUpTarget.DestinationKind.TRANSPORT_ERROR) {
            if (!counted) recordFailure(entryId, entry, destination.error ?: "Unknown error")
            throw MobileSyncException("Lookup of the destination of $sobjectType $externalId failed: ${destination.error}")
        }
        destinations[sobjectType to externalId] = destination
        return destination
    }

    /** Counts a failed attempt on the entry, which stays pending, and keeps the error */
    @Throws(JSONException::class)
    private fun recordFailure(entryId: Long, entry: JSONObject, error: String) {
        entry.put(SyncQueue.LAST_ERROR, error)
        entry.put(SyncQueue.ATTEMPTS, entry.optInt(SyncQueue.ATTEMPTS) + 1)
        updateOrThrow(SyncQueue.QUEUE_SOUP, entry, entryId, handleTx = true)
    }

    /** Safety net: maps the local id of a record created earlier in this run, unknown ids are kept */
    private fun mapId(soupName: String, id: String): String =
        if (SyncTarget.isLocalId(id)) idMap[soupName to id] ?: id else id

    /** Same for a reference to a record, whatever its soup */
    private fun mapRef(id: String): String = if (SyncTarget.isLocalId(id)) refMap[id] ?: id else id

    @Throws(JSONException::class)
    private fun mapChanges(changes: JSONObject): JSONObject {
        val mapped = JSONObject(changes.toString())
        for (key in mapped.keys().asSequence().toList()) {
            val value = mapped.opt(key)
            if (value is String) {
                mapped.put(key, mapRef(value))
            }
        }
        return mapped
    }

    companion object {
        private const val TAG = "QueueSyncUpTask"

        private val locks = ConcurrentHashMap<String, ReentrantLock>()

        /**
         * The lock of a store (by database path): queue syncs of different sync managers on the same
         * store must not replay the same entry at the same time.
         */
        internal fun lockFor(store: SmartStore): ReentrantLock =
            locks.getOrPut(store.database.path) { ReentrantLock() }
    }
}
