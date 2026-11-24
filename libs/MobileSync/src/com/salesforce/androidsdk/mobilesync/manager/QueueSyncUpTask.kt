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
import java.io.IOException

/**
 * Task running a sync up whose target is a [QueueSyncUpTarget]: it replays the pending entries of the
 * sync-up change queue in `sequence` order, strictly one at a time (never reordered, never batched).
 *
 * - Success: the entry is removed. A CREATE gives the record its server id and the local id is
 *   replaced by the server id in the pending entries; an UPDATE cleans the record unless a later
 *   entry for it is pending; a DELETE removes the record.
 * - Record-level error (HTTP 4xx, missing record, soup or type): the sync goes on. The error is
 *   written in the entry (`lastError`, `attempts` + 1) and in the `__last_error__` field of the record.
 * - Transport error (no response, 5xx, `REQUEST_LIMIT_EXCEEDED`): `attempts` + 1 on the entry, which
 *   stays first in line, and the sync fails. Running the sync again retries it.
 */
open class QueueSyncUpTask(syncManager: SyncManager, sync: SyncState, callback: SyncUpdateCallback?) :
    SyncTask(syncManager, sync, callback) {

    /** Local id to server id of the records created during this run (not persisted) */
    private val idMap = HashMap<String, String>()

    private val store: SmartStore
        get() = syncManager.smartStore

    @Throws(Exception::class)
    override fun runSync() {
        val target = sync.target as QueueSyncUpTarget
        // Entries are read once; each one is read again right before it is replayed since replaying
        // a CREATE rewrites the entries that follow
        val entryIds = SyncQueue.pendingEntryIds(store)
        sync.totalSize = entryIds.size
        updateSync(sync, SyncState.Status.RUNNING, 0, callback)
        for ((i, entryId) in entryIds.withIndex()) {
            checkIfStopRequested()
            val entry = SyncQueue.readEntry(store, entryId)
            if (entry != null && entry.optString(SyncQueue.STATUS) == SyncQueue.Status.PENDING.name) {
                replay(target, entryId, entry)
            }
            val progress = (i + 1) * 100 / entryIds.size
            if (progress < 100) {
                updateSync(sync, SyncState.Status.RUNNING, progress, callback)
            }
        }
    }

    @Throws(JSONException::class, IOException::class)
    private fun replay(target: QueueSyncUpTarget, entryId: Long, entry: JSONObject) {
        val soupName = entry.optString(SyncQueue.SOUP_NAME)
        val recordId = mapId(entry.optString(SyncQueue.RECORD_ID))
        val sobjectType = entry.optString(SyncQueue.SOBJECT_TYPE)
        val kind = SyncQueue.Kind.entries.firstOrNull { it.name == entry.optString(SyncQueue.KIND) }
        val changes = entry.optJSONObject(SyncQueue.CHANGES)?.let { mapChanges(it) }

        // Problems with the entry or its record are record-level errors
        val recordEntryId = findRecordEntryId(target, soupName, recordId)
        val problem = when {
            kind == null -> "Unknown kind of queue entry: ${entry.opt(SyncQueue.KIND)}"
            sobjectType.isEmpty() -> "Queue entry has no ${SyncQueue.SOBJECT_TYPE}"
            !store.hasSoup(soupName) -> "Soup $soupName does not exist"
            recordEntryId == -1L -> "Record $recordId does not exist in $soupName"
            else -> null
        }
        if (problem != null || kind == null) {
            recordError(target, entryId, entry, soupName, recordEntryId, problem ?: "Invalid queue entry")
            return
        }

        // A record that was never sent cannot exist on the server: nothing to delete there
        val result = if (kind == SyncQueue.Kind.DELETE && SyncTarget.isLocalId(recordId)) {
            QueueSyncUpTarget.ReplayResult(Outcome.SUCCESS)
        } else try {
            target.replay(syncManager, kind, sobjectType, recordId, changes)
        } catch (e: IOException) {
            incrementAttempts(entryId, entry)
            throw e
        }
        when (result.outcome) {
            Outcome.SUCCESS -> onSuccess(target, entryId, entry, kind, soupName, recordId, recordEntryId, result.serverId)
            Outcome.RECORD_ERROR -> recordError(target, entryId, entry, soupName, recordEntryId, result.error ?: "Unknown error")
            Outcome.TRANSPORT_ERROR -> {
                incrementAttempts(entryId, entry)
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
        val sequence = entry.optLong(SyncQueue.SEQUENCE)
        SyncQueue.inTransaction(store, soupName) {
            store.delete(SyncQueue.QUEUE_SOUP, entryId)
            val record = store.retrieve(soupName, recordEntryId).optJSONObject(0)
            when (kind) {
                SyncQueue.Kind.CREATE -> {
                    if (serverId == null) throw MobileSyncException("No id returned for $recordId")
                    idMap[recordId] = serverId
                    if (record != null) {
                        record.put(target.idFieldName, serverId)
                        target.cleanRecord(record)
                        store.update(soupName, record, recordEntryId, false)
                    } else {
                        MobileSyncLogger.w(TAG, "Record $recordId is gone from $soupName, only queue entries are updated")
                    }
                    rewritePendingEntries(recordId, serverId)
                }

                SyncQueue.Kind.UPDATE -> {
                    // Later entries still need the record to be dirty
                    if (record != null && !SyncQueue.hasLaterPendingEntry(store, soupName, recordId, sequence)) {
                        target.cleanRecord(record)
                        store.update(soupName, record, recordEntryId, false)
                    }
                }

                SyncQueue.Kind.DELETE -> if (record != null) store.delete(soupName, recordEntryId)
            }
        }
    }

    /** Replaces [localId] by [serverId] in the pending entries that reference it (any soup) */
    @Throws(JSONException::class)
    private fun rewritePendingEntries(localId: String, serverId: String) {
        for (pending in SyncQueue.findPendingEntriesReferencing(store, localId)) {
            if (SyncQueue.replaceId(pending, localId, serverId)) {
                store.update(SyncQueue.QUEUE_SOUP, pending, pending.getLong(SmartStore.SOUP_ENTRY_ID), false)
            }
        }
    }

    /** Records the error in the entry and in the record, then lets the sync go on */
    @Throws(JSONException::class)
    private fun recordError(
        target: QueueSyncUpTarget,
        entryId: Long,
        entry: JSONObject,
        soupName: String,
        recordEntryId: Long,
        error: String
    ) {
        MobileSyncLogger.w(TAG, "Queue entry $entryId failed: $error")
        SyncQueue.inTransaction(store, soupName) {
            entry.put(SyncQueue.LAST_ERROR, error)
            entry.put(SyncQueue.ATTEMPTS, entry.optInt(SyncQueue.ATTEMPTS) + 1)
            store.update(SyncQueue.QUEUE_SOUP, entry, entryId, false)
            if (recordEntryId != -1L && store.hasSoup(soupName)) {
                store.retrieve(soupName, recordEntryId).optJSONObject(0)?.let { record ->
                    record.put(SyncTarget.LAST_ERROR, error)
                    store.update(soupName, record, recordEntryId, false)
                }
            }
        }
    }

    @Throws(JSONException::class)
    private fun incrementAttempts(entryId: Long, entry: JSONObject) {
        entry.put(SyncQueue.ATTEMPTS, entry.optInt(SyncQueue.ATTEMPTS) + 1)
        store.update(SyncQueue.QUEUE_SOUP, entry, entryId, true)
    }

    /** Safety net: maps a local id created earlier in this run, unknown local ids are kept */
    private fun mapId(id: String): String = if (SyncTarget.isLocalId(id)) idMap[id] ?: id else id

    @Throws(JSONException::class)
    private fun mapChanges(changes: JSONObject): JSONObject {
        val mapped = JSONObject(changes.toString())
        for (key in mapped.keys().asSequence().toList()) {
            val value = mapped.opt(key)
            if (value is String) {
                mapped.put(key, mapId(value))
            }
        }
        return mapped
    }

    companion object {
        private const val TAG = "QueueSyncUpTask"
    }
}
