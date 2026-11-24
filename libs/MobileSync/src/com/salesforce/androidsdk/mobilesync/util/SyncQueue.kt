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
package com.salesforce.androidsdk.mobilesync.util

import android.database.SQLException
import com.salesforce.androidsdk.mobilesync.manager.SyncManager.MobileSyncException
import com.salesforce.androidsdk.mobilesync.target.SyncTarget
import com.salesforce.androidsdk.smartstore.store.IndexSpec
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import com.salesforce.androidsdk.smartstore.store.SmartStore
import com.salesforce.androidsdk.smartstore.store.SmartStore.SmartStoreException
import org.json.JSONException
import org.json.JSONObject

/**
 * The sync-up change queue: an ordered list of local changes (one entry per change) kept in the
 * [QUEUE_SOUP] soup of a store, and the helpers that write to it.
 *
 * Entries are appended by the local write API of [com.salesforce.androidsdk.mobilesync.manager.SyncManager]
 * (`createLocal`, `updateLocal`, `deleteLocal`), in the same SmartStore transaction as the record write.
 * Entries are ordered by their numeric [SEQUENCE].
 */
object SyncQueue {

    /** Name of the queue soup (one per store) */
    const val QUEUE_SOUP = "mobilesync_queue"

    // Entry fields
    const val SEQUENCE = "sequence"
    const val SOUP_NAME = "soupName"
    const val RECORD_ID = "recordId"
    const val SOBJECT_TYPE = "sobjectType"
    const val KIND = "kind"
    const val CHANGES = "changes"

    /** Reserved, never written by the write API yet */
    const val BASE = "base"
    const val STATUS = "status"
    const val LAST_ERROR = "lastError"
    const val ATTEMPTS = "attempts"

    /** Reserved, never written by the write API yet */
    const val PARENT_REF = "parentRef"

    /** Index specs of the queue soup: the single source of truth for registration and validation */
    private val INDEX_SPECS = listOf(
        IndexSpec(SEQUENCE, SmartStore.Type.integer),
        IndexSpec(SOUP_NAME, SmartStore.Type.string),
        IndexSpec(RECORD_ID, SmartStore.Type.string),
        IndexSpec(SOBJECT_TYPE, SmartStore.Type.string),
        IndexSpec(KIND, SmartStore.Type.string),
        IndexSpec(CHANGES, SmartStore.Type.json1),
        IndexSpec(BASE, SmartStore.Type.json1),
        IndexSpec(STATUS, SmartStore.Type.string),
        IndexSpec(LAST_ERROR, SmartStore.Type.json1),
        IndexSpec(ATTEMPTS, SmartStore.Type.integer),
        IndexSpec(PARENT_REF, SmartStore.Type.string),
    )

    /** Id field of the records in record soups */
    private const val ID_FIELD = Constants.ID

    /** Fields that can never be part of the changes of a queue entry */
    private val INTERNAL_FIELDS = setOf(
        ID_FIELD,
        Constants.ATTRIBUTES,
        SyncTarget.LOCAL,
        SyncTarget.LOCALLY_CREATED,
        SyncTarget.LOCALLY_UPDATED,
        SyncTarget.LOCALLY_DELETED,
        SyncTarget.LAST_ERROR,
        SyncTarget.SYNC_ID,
        "__display__",
    )

    /** Kind of change recorded by a queue entry */
    enum class Kind { CREATE, UPDATE, DELETE }

    /** Status of a queue entry */
    enum class Status { PENDING, PARKED }

    /**
     * Registers the queue soup if it does not exist yet.
     * If it exists but its index specs differ from the expected ones (different paths or types),
     * it is altered and re-indexed.
     *
     * @param store The store that gets the queue soup
     * @throws MobileSyncException if the soup could not be altered
     */
    @Throws(MobileSyncException::class)
    @JvmStatic
    fun setupQueueSoupIfNeeded(store: SmartStore) {
        val exists = store.hasSoup(QUEUE_SOUP)
        if (exists && hasExpectedIndexSpecs(store)) {
            return
        }
        val indexSpecs = INDEX_SPECS.toTypedArray()
        try {
            if (exists) {
                store.alterSoup(QUEUE_SOUP, indexSpecs, true)
            } else {
                store.registerSoup(QUEUE_SOUP, indexSpecs)
            }
        } catch (e: JSONException) {
            throw MobileSyncException(cause = e)
        }
    }

    private fun hasExpectedIndexSpecs(store: SmartStore): Boolean =
        store.getSoupIndexSpecs(QUEUE_SOUP).map { it.path to it.type }.toSet() ==
            INDEX_SPECS.map { it.path to it.type }.toSet()

    /**
     * Returns the sequence number for the next queue entry: the highest sequence + 1, or 1 if the
     * queue is empty. Call it inside the write transaction so that the value is unique.
     *
     * Not public API: it does not reserve the value.
     *
     * @param store The store holding the queue soup
     */
    @Throws(JSONException::class)
    internal fun nextSequence(store: SmartStore): Long {
        val rows = store.query(
            QuerySpec.buildSmartQuerySpec("SELECT MAX({$QUEUE_SOUP:$SEQUENCE}) FROM {$QUEUE_SOUP}", 1),
            0
        )
        // MAX over no rows still returns one row holding SQL NULL
        val row = rows.optJSONArray(0)
        val max = if (row == null || row.isNull(0)) 0L else row.getLong(0)
        return max + 1
    }

    // region Local write API (called by SyncManager)

    internal fun createLocal(store: SmartStore, soupName: String, record: JSONObject): JSONObject {
        checkRecordSoup(store, soupName)
        val copy = deepCopy(record, "Record")
        for (key in copy.keys()) {
            if (key.startsWith("_soup")) {
                throw MobileSyncException("Record cannot contain the SmartStore field $key")
            }
        }
        val sobjectType = objectType(copy)
        if (sobjectType == null) {
            throw MobileSyncException("Record must have ${Constants.SOBJECT_TYPE}")
        }
        val givenId = if (copy.isNull(ID_FIELD)) null else copy.get(ID_FIELD)
        if (givenId != null && (givenId !is String || !SyncTarget.isLocalId(givenId))) {
            throw MobileSyncException("createLocal only accepts a local id, got: $givenId")
        }
        val changes = JSONObject()
        for (key in copy.keys()) {
            if (key !in INTERNAL_FIELDS) {
                changes.put(key, copy.get(key))
            }
        }
        return inTransaction(store, soupName) {
            val id = if (givenId != null) {
                if (store.lookupSoupEntryId(soupName, ID_FIELD, givenId as String) != -1L) {
                    throw MobileSyncException("A record with id $givenId already exists in $soupName")
                }
                givenId
            } else {
                var candidate = SyncTarget.createLocalId()
                while (store.lookupSoupEntryId(soupName, ID_FIELD, candidate) != -1L) {
                    candidate = SyncTarget.createLocalId()
                }
                candidate
            }
            copy.put(ID_FIELD, id)
            setFlags(copy, created = true, updated = false, deleted = false)
            copy.remove(SyncTarget.LAST_ERROR)
            val saved = store.create(soupName, copy, false)
                ?: throw MobileSyncException("Failed to create record in $soupName")
            appendEntry(store, soupName, id, sobjectType, Kind.CREATE, changes)
            saved
        }
    }

    internal fun updateLocal(store: SmartStore, soupName: String, id: String, changes: JSONObject): JSONObject {
        checkRecordSoup(store, soupName)
        val changesCopy = deepCopy(changes, "Changes")
        if (changesCopy.length() == 0) {
            throw MobileSyncException("Changes cannot be empty")
        }
        for (key in changesCopy.keys()) {
            if (key in INTERNAL_FIELDS || key.startsWith("_soup")) {
                throw MobileSyncException("Changes cannot contain the internal field $key")
            }
        }
        return inTransaction(store, soupName) {
            val (entryId, record) = findRecord(store, soupName, id)
            for (key in changesCopy.keys()) {
                record.put(key, changesCopy.get(key))
            }
            record.put(SyncTarget.LOCAL, true)
            record.put(SyncTarget.LOCALLY_UPDATED, true)
            val saved = store.update(soupName, record, entryId, false)
                ?: throw MobileSyncException("Failed to update record $id in $soupName")
            appendEntry(store, soupName, id, objectType(record), Kind.UPDATE, changesCopy)
            saved
        }
    }

    internal fun deleteLocal(store: SmartStore, soupName: String, id: String): JSONObject {
        checkRecordSoup(store, soupName)
        return inTransaction(store, soupName) {
            val (entryId, record) = findRecord(store, soupName, id)
            record.put(SyncTarget.LOCAL, true)
            record.put(SyncTarget.LOCALLY_DELETED, true)
            val saved = store.update(soupName, record, entryId, false)
                ?: throw MobileSyncException("Failed to delete record $id in $soupName")
            appendEntry(store, soupName, id, objectType(record), Kind.DELETE, null)
            saved
        }
    }

    // endregion

    // region Replay support (used by QueueSyncUpTask)

    /** Page size of the queries reading the queue (the default page size of smart queries is capped) */
    private const val QUERY_PAGE_SIZE = 2000

    /** Characters allowed in a local id for the LIKE pre-filter of [findPendingEntriesReferencing] */
    private val SIMPLE_ID = Regex("[A-Za-z0-9_-]+")

    /**
     * Returns the soup entry ids of the entries with status [Status.PENDING], ordered by [SEQUENCE].
     * Parked entries are skipped.
     */
    @Throws(JSONException::class)
    internal fun pendingEntryIds(store: SmartStore): List<Long> {
        val sql = "SELECT {$QUEUE_SOUP:${SmartStore.SOUP_ENTRY_ID}} FROM {$QUEUE_SOUP} " +
            "WHERE {$QUEUE_SOUP:$STATUS} = '${Status.PENDING.name}' ORDER BY {$QUEUE_SOUP:$SEQUENCE} ASC"
        return queryAll(store, sql) { row -> row.getLong(0) }
    }

    /** Returns the entry stored under [entryId], or null if it does not exist anymore */
    @Throws(JSONException::class)
    internal fun readEntry(store: SmartStore, entryId: Long): JSONObject? =
        store.retrieve(QUEUE_SOUP, entryId).optJSONObject(0)

    /**
     * Returns the kinds of the pending entries for the same record with a sequence greater than
     * [sequence] (empty when no later entry is pending).
     */
    @Throws(JSONException::class)
    internal fun laterPendingKinds(store: SmartStore, soupName: String, recordId: String, sequence: Long): Set<Kind> {
        val sql = "SELECT DISTINCT {$QUEUE_SOUP:$KIND} FROM {$QUEUE_SOUP} " +
            "WHERE {$QUEUE_SOUP:$SOUP_NAME} = '${sqlEscape(soupName)}' " +
            "AND {$QUEUE_SOUP:$RECORD_ID} = '${sqlEscape(recordId)}' " +
            "AND {$QUEUE_SOUP:$STATUS} = '${Status.PENDING.name}' " +
            "AND {$QUEUE_SOUP:$SEQUENCE} > $sequence"
        return queryAll(store, sql) { row -> Kind.valueOf(row.getString(0)) }.toSet()
    }

    /**
     * Returns the pending entries that may reference [localId] (as `recordId`, `parentRef` or a
     * value of `changes`). The result can contain entries that do not reference it: callers check.
     */
    @Throws(JSONException::class)
    internal fun findPendingEntriesReferencing(store: SmartStore, localId: String): List<JSONObject> {
        val pending = "{$QUEUE_SOUP:$STATUS} = '${Status.PENDING.name}'"
        // The raw JSON of an id made of simple characters appears verbatim in the entry, so a LIKE
        // narrows the scan (`_` in the pattern is a wildcard, which can only widen the result)
        val sql = if (SIMPLE_ID.matches(localId)) {
            "SELECT {$QUEUE_SOUP:_soup} FROM {$QUEUE_SOUP} WHERE $pending AND {$QUEUE_SOUP:_soup} LIKE '%$localId%'"
        } else {
            "SELECT {$QUEUE_SOUP:_soup} FROM {$QUEUE_SOUP} WHERE $pending"
        }
        return queryAll(store, "$sql ORDER BY {$QUEUE_SOUP:$SEQUENCE} ASC") { row ->
            row.get(0).let { if (it is JSONObject) it else JSONObject(it.toString()) }
        }
    }

    /**
     * Replaces [localId] by [serverId] in [entry]: `recordId`, `parentRef`, and the top-level string
     * values of `changes` that are exactly [localId].
     *
     * @return true if the entry was modified
     */
    @Throws(JSONException::class)
    internal fun replaceId(entry: JSONObject, localId: String, serverId: String): Boolean {
        var modified = false
        for (field in listOf(RECORD_ID, PARENT_REF)) {
            if (entry.opt(field) == localId) {
                entry.put(field, serverId)
                modified = true
            }
        }
        val changes = entry.optJSONObject(CHANGES)
        if (changes != null) {
            for (key in changes.keys().asSequence().toList()) {
                if (changes.opt(key) == localId) {
                    changes.put(key, serverId)
                    modified = true
                }
            }
        }
        return modified
    }

    private fun <T> queryAll(store: SmartStore, sql: String, mapRow: (org.json.JSONArray) -> T): List<T> {
        val result = mutableListOf<T>()
        var page = 0
        while (true) {
            val rows = store.query(QuerySpec.buildSmartQuerySpec(sql, QUERY_PAGE_SIZE), page)
            for (i in 0 until rows.length()) {
                result.add(mapRow(rows.getJSONArray(i)))
            }
            if (rows.length() < QUERY_PAGE_SIZE) break
            page++
        }
        return result
    }

    private fun sqlEscape(value: String): String = value.replace("'", "''")

    // endregion

    private fun setFlags(record: JSONObject, created: Boolean, updated: Boolean, deleted: Boolean) {
        record.put(SyncTarget.LOCAL, true)
        record.put(SyncTarget.LOCALLY_CREATED, created)
        record.put(SyncTarget.LOCALLY_UPDATED, updated)
        record.put(SyncTarget.LOCALLY_DELETED, deleted)
    }

    private fun objectType(record: JSONObject): String? =
        (record.optJSONObject(Constants.ATTRIBUTES)?.opt(Constants.LTYPE) as? String)?.takeIf { it.isNotEmpty() }

    /**
     * Deep-copies [source] through its JSON text. `toString()` returns null (it does not throw) when
     * nested content cannot be serialized, e.g. a boxed NaN inside an array.
     */
    private fun deepCopy(source: JSONObject, what: String): JSONObject {
        val text = source.toString() ?: throw MobileSyncException("$what cannot be serialized to JSON")
        return try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw MobileSyncException("$what cannot be serialized to JSON", e)
        }
    }

    /**
     * Checks done before the transaction: the record soup exists and indexes the id field, and the
     * queue soup is (re-)registered, since a cached sync manager does not do it again after an app
     * dropped all soups.
     */
    private fun checkRecordSoup(store: SmartStore, soupName: String) {
        if (!store.hasSoup(soupName)) {
            throw MobileSyncException("Soup $soupName does not exist")
        }
        if (!store.hasIndexForPath(soupName, ID_FIELD)) {
            throw MobileSyncException("Soup $soupName must have an index on $ID_FIELD")
        }
        setupQueueSoupIfNeeded(store)
    }

    /** Returns the soup entry id and the stored record, failing if it is missing or already deleted */
    private fun findRecord(store: SmartStore, soupName: String, id: String): Pair<Long, JSONObject> {
        val entryId = store.lookupSoupEntryId(soupName, ID_FIELD, id)
        if (entryId == -1L) {
            throw MobileSyncException("Record $id does not exist in $soupName")
        }
        val record = store.retrieve(soupName, entryId).optJSONObject(0)
            ?: throw MobileSyncException("Record $id could not be read from $soupName")
        if (record.optBoolean(SyncTarget.LOCALLY_DELETED, false)) {
            throw MobileSyncException("Record $id in $soupName is already deleted")
        }
        return Pair(entryId, record)
    }

    private fun appendEntry(
        store: SmartStore,
        soupName: String,
        recordId: String,
        sobjectType: String?,
        kind: Kind,
        changes: JSONObject?
    ) {
        val entry = JSONObject().apply {
            put(SEQUENCE, nextSequence(store))
            put(SOUP_NAME, soupName)
            put(RECORD_ID, recordId)
            sobjectType?.let { put(SOBJECT_TYPE, it) }
            put(KIND, kind.name)
            changes?.let { put(CHANGES, it) }
            put(STATUS, Status.PENDING.name)
            put(ATTEMPTS, 0)
        }
        store.create(QUEUE_SOUP, entry, false)
            ?: throw MobileSyncException("Failed to append entry to $QUEUE_SOUP")
    }

    /**
     * Runs [block] in one SmartStore transaction: everything it writes is committed, or nothing is.
     * Failures are reported as [MobileSyncException].
     */
    internal fun <T> inTransaction(store: SmartStore, soupName: String, block: () -> T): T {
        synchronized(store.database) {
            store.beginTransaction()
            try {
                val result = block()
                store.setTransactionSuccessful()
                return result
            } catch (e: MobileSyncException) {
                throw e
            } catch (e: JSONException) {
                throw MobileSyncException("Local write to $soupName failed", e)
            } catch (e: SmartStoreException) {
                throw MobileSyncException("Local write to $soupName failed", e)
            } catch (e: SQLException) {
                throw MobileSyncException("Local write to $soupName failed", e)
            } finally {
                store.endTransaction()
            }
        }
    }
}
