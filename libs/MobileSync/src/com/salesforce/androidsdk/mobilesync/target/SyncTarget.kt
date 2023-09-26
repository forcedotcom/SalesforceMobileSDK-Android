/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.mobilesync.target

import android.text.TextUtils
import android.text.TextUtils.join
import com.salesforce.androidsdk.mobilesync.manager.SyncManager
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.MobileSyncLogger
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import com.salesforce.androidsdk.smartstore.store.SmartStore
import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.Long.valueOf
import java.util.SortedSet
import java.util.TreeSet

/**
 * Abstract super class for SyncUpTarget and SyncDownTarget
 *
 * Targets handle interactions with local store and with remote server.
 *
 * Default targets use SmartStore for local store and __local_*__ fields to flag dirty (i.e. locally created/updated/deleted) records.
 * Custom targets can use a different local store and/or different fields to flag dirty records.
 *
 * Default targets use SObject Rest API to read/write records to the server.
 * Custom targets can use different end points to read/write records to the server.
 */
abstract class SyncTarget @JvmOverloads constructor(
    idFieldName: String? = null,
    modificationDateFieldName: String? = null
) {
    /**
     * @return The field name of the ID field of the record.  Defaults to "Id".
     */
    val idFieldName: String

    /**
     * @return The field name of the modification date field of the record.  Defaults to "LastModifiedDate".
     */
    val modificationDateFieldName: String

    constructor(target: JSONObject?) : this(
        if (target != null) JSONObjectHelper.optString(target, ID_FIELD_NAME) else null,
        if (target != null) JSONObjectHelper.optString(
            target,
            MODIFICATION_DATE_FIELD_NAME
        ) else null
    )

    init {
        this.idFieldName = idFieldName ?: Constants.ID
        this.modificationDateFieldName = modificationDateFieldName
            ?: Constants.LAST_MODIFIED_DATE
    }

    /**
     * @return json representation of target
     * @throws JSONException
     */
    @Throws(JSONException::class)
    open fun asJSON(): JSONObject {
        return with(JSONObject()) {
            put(ANDROID_IMPL, this@SyncTarget.javaClass.name)
            put(ID_FIELD_NAME, idFieldName)
            put(MODIFICATION_DATE_FIELD_NAME, modificationDateFieldName)
        }
    }

    /**
     * Return ids of "dirty" records (records locally created/upated or deleted)
     * @param syncManager
     * @param soupName
     * @param idField
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    fun getDirtyRecordIds(
        syncManager: SyncManager,
        soupName: String,
        idField: String
    ): SortedSet<String> {
        val dirtyRecordsSql = getDirtyRecordIdsSql(soupName, idField)
        return getIdsWithQuery(syncManager, dirtyRecordsSql)
    }

    /**
     * Return SmartSQL to identify dirty records
     * @param soupName
     * @param idField
     * @return
     */
    protected open fun getDirtyRecordIdsSql(soupName: String, idField: String): String {
        return String.format(
            "SELECT {%s:%s} FROM {%s} WHERE {%s:%s} = 'true' ORDER BY {%s:%s} ASC",
            soupName,
            idField,
            soupName,
            soupName,
            LOCAL,
            soupName,
            idField
        )
    }

    @Throws(JSONException::class)
    protected fun getIdsWithQuery(syncManager: SyncManager, idsSql: String): SortedSet<String> {
        val ids: SortedSet<String> = TreeSet()
        val smartQuerySpec = QuerySpec.buildSmartQuerySpec(idsSql, PAGE_SIZE)
        var hasMore = true
        var pageIndex = 0
        while (hasMore) {
            val results = syncManager.smartStore.query(smartQuerySpec, pageIndex)
            hasMore = results.length() == PAGE_SIZE
            ids.addAll(toSortedSet(results))
            pageIndex++
        }
        return ids
    }

    /**
     * Save cleaned record in local store
     * @param syncManager
     * @param soupName
     * @param record
     */
    @Throws(JSONException::class)
    fun cleanAndSaveInLocalStore(syncManager: SyncManager, soupName: String, record: JSONObject) {
        cleanAndSaveInSmartStore(syncManager.smartStore, soupName, record, idFieldName, true)
        MobileSyncLogger.d(TAG, "cleanAndSaveInLocalStore", record)
    }

    /**
     * Save record in local store
     * @param syncManager
     * @param soupName
     * @param record
     */
    @Throws(JSONException::class)
    protected fun saveInLocalStore(
        syncManager: SyncManager,
        soupName: String,
        record: JSONObject
    ) {
        saveInSmartStore(syncManager.smartStore, soupName, record, idFieldName, true)
        MobileSyncLogger.d(TAG, "saveInLocalStore", record)
    }

    @Throws(JSONException::class)
    fun cleanAndSaveInSmartStore(
        smartStore: SmartStore,
        soupName: String,
        record: JSONObject,
        idFieldName: String?,
        handleTx: Boolean
    ) {
        cleanRecord(record)
        saveInSmartStore(smartStore, soupName, record, idFieldName, handleTx)
    }

    @Throws(JSONException::class)
    protected fun saveInSmartStore(
        smartStore: SmartStore,
        soupName: String,
        record: JSONObject,
        idFieldName: String?,
        handleTx: Boolean
    ) {
        if (record.has(SmartStore.SOUP_ENTRY_ID)) {
            // Record came from smartstore
            smartStore.update(
                soupName,
                record,
                record.getLong(SmartStore.SOUP_ENTRY_ID),
                handleTx
            )
        } else {
            // Record came from server
            smartStore.upsert(soupName, record, idFieldName, handleTx)
        }
    }

    @Throws(JSONException::class)
    fun cleanRecord(record: JSONObject) {
        record.put(LOCAL, false)
        record.put(LOCALLY_CREATED, false)
        record.put(LOCALLY_UPDATED, false)
        record.put(LOCALLY_DELETED, false)
        record.put(LAST_ERROR, null)
    }

    /**
     * Save records to local store
     * @param syncManager
     * @param soupName
     * @param records
     * @param syncId
     * @throws JSONException
     */
    @Throws(JSONException::class)
    open fun saveRecordsToLocalStore(
        syncManager: SyncManager,
        soupName: String,
        records: JSONArray,
        syncId: Long
    ) {
        val smartStore = syncManager.smartStore
        synchronized(smartStore.database) {
            try {
                smartStore.beginTransaction()
                for (i in 0 until records.length()) {
                    val record = JSONObject(records.getJSONObject(i).toString())
                    addSyncId(record, syncId)
                    cleanAndSaveInSmartStore(
                        syncManager.smartStore,
                        soupName,
                        record,
                        idFieldName,
                        false
                    )
                }
                smartStore.setTransactionSuccessful()
            } finally {
                smartStore.endTransaction()
            }
        }
    }

    @Throws(JSONException::class)
    fun addSyncId(record: JSONObject, syncId: Long) {
        if (syncId >= 0) {
            record.put(SYNC_ID, syncId)
        }
    }

    /**
     * Delete the records with the given ids
     * @param syncManager
     * @param soupName
     * @param ids
     * @param idField
     */
    protected fun deleteRecordsFromLocalStore(
        syncManager: SyncManager,
        soupName: String,
        ids: Set<String>,
        idField: String?
    ) {
        if (ids.isNotEmpty()) {
            val smartSql =
                "SELECT {$soupName:${SmartStore.SOUP_ENTRY_ID}} FROM {$soupName} WHERE {$soupName:$idField} IN ('${
                    join(/* delimiter = */ "', '", /* tokens = */ ids)
                }')"
            val querySpec = QuerySpec.buildSmartQuerySpec(smartSql, Int.MAX_VALUE)
            syncManager.smartStore.deleteByQuery(soupName, querySpec)
        }
    }

    @Throws(JSONException::class)
    private fun toSortedSet(jsonArray: JSONArray): SortedSet<String> {
        val set: SortedSet<String> = TreeSet()
        for (i in 0 until jsonArray.length()) {
            set.add(jsonArray.getJSONArray(i).getString(0))
        }
        return set
    }

    /**
     * Given a record, return true if it was locally created
     * @param record
     * @return
     */
    fun isLocallyCreated(record: JSONObject): Boolean {
        return record.optBoolean(LOCALLY_CREATED)
    }

    /**
     * Given a record, return true if it was locally updated
     * @param record
     * @return
     */
    fun isLocallyUpdated(record: JSONObject): Boolean {
        return record.optBoolean(LOCALLY_UPDATED)
    }

    /**
     * Given a record, return true if it was locally deleted
     * @param record
     * @return
     */
    fun isLocallyDeleted(record: JSONObject): Boolean {
        return record.optBoolean(LOCALLY_DELETED)
    }

    /**
     * Given a record, return true if it was locally created/updated or deleted
     * @param record
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    fun isDirty(record: JSONObject): Boolean {
        return record.getBoolean(LOCAL)
    }

    /**
     * Get record from local store by storeId
     * @param syncManager
     * @param storeId
     * @throws  JSONException
     */
    @Throws(JSONException::class)
    fun getFromLocalStore(
        syncManager: SyncManager,
        soupName: String,
        storeId: String
    ): JSONObject {
        return syncManager.smartStore.retrieve(soupName, valueOf(storeId))
            .getJSONObject(0)
    }

    /**
     * Get records from local store by storeId
     * @param syncManager
     * @param storeIds
     * @throws  JSONException
     */
    @Throws(JSONException::class)
    fun getFromLocalStore(
        syncManager: SyncManager,
        soupName: String,
        storeIds: List<String>
    ): List<JSONObject> {
        val storeIdsLong = arrayOfNulls<Long>(storeIds.size)
        for (i in storeIds.indices) {
            storeIdsLong[i] = valueOf(storeIds[i])
        }
        return JSONObjectHelper.toList(syncManager.smartStore.retrieve(soupName, *storeIdsLong))
    }

    /**
     * Delete record from local store
     * @param syncManager
     * @param soupName
     * @param record
     */
    @Throws(JSONException::class)
    fun deleteFromLocalStore(syncManager: SyncManager, soupName: String, record: JSONObject) {
        MobileSyncLogger.d(TAG, "deleteFromLocalStore", record)
        syncManager.smartStore.delete(soupName, record.getLong(SmartStore.SOUP_ENTRY_ID))
    }

    companion object {
        // Sync targets expect the following fields in locally stored records
        const val LOCALLY_CREATED = "__locally_created__"
        const val LOCALLY_UPDATED = "__locally_updated__"
        const val LOCALLY_DELETED = "__locally_deleted__"
        const val LOCAL = "__local__"
        const val LOCAL_ID_PREFIX = "local_"

        // Field added to record to capture last sync error if any
        const val LAST_ERROR = "__last_error__"

        // Field added to record to remember sync it came through
        const val SYNC_ID = "__sync_id__"
        private const val TAG = "SyncTarget"

        // Page size used when reading from smartstore
        private const val PAGE_SIZE = 2000
        const val ANDROID_IMPL = "androidImpl"
        const val ID_FIELD_NAME = "idFieldName"
        const val MODIFICATION_DATE_FIELD_NAME = "modificationDateFieldName"

        /**
         * Generate local id for record
         * @return generated id
         */
        @JvmStatic
        fun createLocalId(): String {
            return LOCAL_ID_PREFIX + System.nanoTime()
        }

        /**
         * Return true if id was generated locally
         * @param id
         * @return true if generated locally
         */
        fun isLocalId(id: String): Boolean {
            return id.startsWith(LOCAL_ID_PREFIX)
        }
    }
}