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

import com.salesforce.androidsdk.mobilesync.manager.SyncManager
import com.salesforce.androidsdk.mobilesync.manager.SyncManager.MobileSyncException
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.smartstore.store.SmartStore
import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection

/**
 * Target for sync up:
 * - what records to upload to server
 * - how to upload those records
 *
 * During a sync up, sync manager does the following:
 *
 * 1) it calls getIdsOfRecordsToSyncUp to get the ids of records to sync up
 *
 * 2) for each id it does:
 *
 * a) if calls getFromLocalStore to get the record itself
 *
 * b) if merge mode is leave-if-changed, it calls isNewerThanServer, if that returns false, it goes to the next id
 *
 * c) otherwise it does one of the following three operations:
 * - calls deleteOnServer if isLocallyDeleted returns true for the record (unless it is also locally created, in which case it gets deleted locally right away)
 * if successful or not found is returned, it calls deleteFromLocalStore to delete record locally
 *
 * - calls createOnServer if isLocallyCreated returns true for the record
 * if successful, it updates the id to be the id returned by the server and then calls cleanAndSaveInSmartstore to reset local flags and save the record locally
 *
 * - calls updateOnServer if isLocallyUpdated returns true for the record
 * if successful, it calls cleanAndSaveInSmartstore to reset local flags and save the record
 * if not found and merge mode is overwrite, it calls createOnServer to recreate the record on the server
 *
 */
open class SyncUpTarget : SyncTarget {
    // Fields
    @JvmField
    var createFieldlist: List<String>?

    @JvmField
    var updateFieldlist: List<String>?

    /**
     * @return The field name of an external id field of the record.  Default to null.
     */
    var externalIdFieldName: String?
        protected set

    // Last sync error
    protected var lastError: String? = null

    /**
     * Construct SyncUpTarget
     */
    @JvmOverloads
    constructor(
        createFieldlist: List<String>? = null,
        updateFieldlist: List<String>? = null,
        idFieldName: String? = null,
        modificationDateFieldName: String? = null,
        externalIdFieldName: String? = null
    ) : super(idFieldName, modificationDateFieldName) {
        this.createFieldlist = createFieldlist
        this.updateFieldlist = updateFieldlist
        this.externalIdFieldName = externalIdFieldName
    }

    /**
     * Construct SyncUpTarget from json
     * @param target
     * @throws JSONException
     */
    constructor(target: JSONObject) : super(target) {
        createFieldlist = JSONObjectHelper.toList(target.optJSONArray(CREATE_FIELDLIST))
        updateFieldlist = JSONObjectHelper.toList(target.optJSONArray(UPDATE_FIELDLIST))
        externalIdFieldName = JSONObjectHelper.optString(target, EXTERNAL_ID_FIELD_NAME)
    }

    /**
     * @return json representation of target
     * @throws JSONException
     */
    @Throws(JSONException::class)
    override fun asJSON(): JSONObject {
        return with(super.asJSON()) {
            if (createFieldlist != null) put(CREATE_FIELDLIST, JSONArray(createFieldlist))
            if (updateFieldlist != null) put(UPDATE_FIELDLIST, JSONArray(updateFieldlist))
            if (externalIdFieldName != null) put(EXTERNAL_ID_FIELD_NAME, externalIdFieldName)
            this
        }
    }

    /**
     * Save record with last error if any
     * @param syncManager
     * @param soupName
     * @param record
     * @throws JSONException
     */
    @Throws(JSONException::class)
    fun saveRecordToLocalStoreWithLastError(
        syncManager: SyncManager,
        soupName: String,
        record: JSONObject
    ) {
        saveRecordToLocalStoreWithError(syncManager, soupName, record, lastError)
        lastError = null
    }

    @Throws(JSONException::class)
    protected fun saveRecordToLocalStoreWithError(
        syncManager: SyncManager,
        soupName: String,
        record: JSONObject,
        error: String?
    ) {
        if (error != null) {
            record.put(LAST_ERROR, error)
            saveInLocalStore(syncManager, soupName, record)
        }
    }

    /**
     * Save locally created record back to server
     * @param syncManager
     * @param record
     * @param fieldlist fields to sync up (this.createFieldlist will be used instead if provided)
     * @return server record id or null if creation failed
     * @throws JSONException
     * @throws IOException
     */
    @Throws(JSONException::class, IOException::class, MobileSyncException::class)
    open fun createOnServer(
        syncManager: SyncManager,
        record: JSONObject,
        fieldlist: List<String>?
    ): String? {
        val fieldlistToUse =
            createFieldlist ?: fieldlist ?: throw MobileSyncException("No fields specified")
        val objectType = SmartStore.project(record, Constants.SOBJECT_TYPE) as? String ?: "null"
        val fields = buildFieldsMap(record, fieldlistToUse, idFieldName, modificationDateFieldName)
        val externalId = if (externalIdFieldName != null) JSONObjectHelper.optString(
            record,
            externalIdFieldName
        ) else null
        return if (externalId != null // the following check is there for the case
            // where the the external id field is the id field
            // and the field is populated by a local id
            && !isLocalId(externalId)
        ) {
            upsertOnServer(syncManager, objectType, fields, externalId)
        } else {
            createOnServer(syncManager, objectType, fields)
        }
    }

    /**
     * Save locally created record back to server (original method)
     * Called by createOnServer(SyncManager syncManager, JSONObject record, List<String> fieldlist)
     * @param syncManager
     * @param objectType
     * @param fields
     * @return server record id or null if creation failed
     * @throws IOException
     * @throws JSONException
    </String> */
    @Throws(IOException::class, JSONException::class)
    protected open fun createOnServer(
        syncManager: SyncManager,
        objectType: String,
        fields: Map<String, Any>
    ): String? {
        val request = RestRequest.getRequestForCreate(syncManager.apiVersion, objectType, fields)
        return sendCreateOrUpsertRequest(syncManager, request)
    }

    /**
     * Save locally created record back to server doing an upsert
     * Called by createOnServer(SyncManager syncManager, JSONObject record, List<String> fieldlist)
     * @param syncManager
     * @param objectType
     * @param fields
     * @param externalId
     * @return server record id or null if creation failed
     * @throws IOException
     * @throws JSONException
    </String> */
    @Throws(IOException::class, JSONException::class)
    protected fun upsertOnServer(
        syncManager: SyncManager,
        objectType: String,
        fields: Map<String, Any>,
        externalId: String?
    ): String? {
        val request = RestRequest.getRequestForUpsert(
            syncManager.apiVersion,
            objectType,
            externalIdFieldName,
            externalId,
            fields
        )
        return sendCreateOrUpsertRequest(syncManager, request)
    }

    /**
     * Send create or upsert request
     * @param syncManager
     * @param request
     * @return server record id or null if creation or upsert failed
     */
    @Throws(IOException::class, JSONException::class)
    protected fun sendCreateOrUpsertRequest(
        syncManager: SyncManager,
        request: RestRequest
    ): String? {
        val response = syncManager.sendSyncWithMobileSyncUserAgent(request)
        if (!response.isSuccess) {
            lastError = response.asString()
        }
        return if (response.isSuccess) response.asJSONObject().getString(Constants.LID) else null
    }

    /**
     * Delete locally deleted record from server
     * @param syncManager
     * @param record
     * @return server response status code
     * @throws JSONException
     * @throws IOException
     */
    @Throws(JSONException::class, IOException::class)
    open fun deleteOnServer(syncManager: SyncManager, record: JSONObject): Int {
        val objectType = SmartStore.project(record, Constants.SOBJECT_TYPE) as String
        val objectId = record.getString(idFieldName)
        return deleteOnServer(syncManager, objectType, objectId)
    }

    /**
     * Delete locally deleted record from server (original method)
     * Called by deleteOnServer(SyncManager syncManager, JSONObject record)
     * @param syncManager
     * @param objectType
     * @param objectId
     * @return server response status code
     * @throws IOException
     */
    @Throws(IOException::class)
    protected open fun deleteOnServer(
        syncManager: SyncManager,
        objectType: String,
        objectId: String
    ): Int {
        val request = RestRequest.getRequestForDelete(syncManager.apiVersion, objectType, objectId)
        val response = syncManager.sendSyncWithMobileSyncUserAgent(request)
        if (!response.isSuccess) {
            lastError = response.asString()
        }
        return response.statusCode
    }

    /**
     * Save locally updated record back to server
     * @param syncManager
     * @param record
     * @param fieldlist fields to sync up (this.updateFieldlist will be used instead if provided)
     * @return true if successful
     * @throws JSONException
     * @throws IOException
     */
    @Throws(JSONException::class, IOException::class, MobileSyncException::class)
    open fun updateOnServer(
        syncManager: SyncManager,
        record: JSONObject,
        fieldlist: List<String>?
    ): Int {
        val fieldlistToUse =
            updateFieldlist ?: fieldlist ?: throw MobileSyncException("No fields specified")
        val objectType = SmartStore.project(record, Constants.SOBJECT_TYPE) as String
        val objectId = record.getString(idFieldName)
        val fields = buildFieldsMap(record, fieldlistToUse, idFieldName, modificationDateFieldName)
        return updateOnServer(syncManager, objectType, objectId, fields)
    }

    /**
     * Save locally updated record back to server (original method)
     * Called by updateOnServer(SyncManager syncManager, JSONObject record, List<String> fieldlist)
     * @param syncManager
     * @param objectType
     * @param objectId
     * @param fields
     * @return true if successful
     * @throws IOException
    </String> */
    @Throws(IOException::class)
    protected open fun updateOnServer(
        syncManager: SyncManager,
        objectType: String,
        objectId: String,
        fields: Map<String, Any>
    ): Int {
        val request =
            RestRequest.getRequestForUpdate(syncManager.apiVersion, objectType, objectId, fields)
        val response = syncManager.sendSyncWithMobileSyncUserAgent(request)
        if (!response.isSuccess) {
            lastError = response.asString()
        }
        return response.statusCode
    }

    /**
     * Fetch last modified date for a given record
     * @param syncManager
     * @param record
     * @return
     */
    @Throws(JSONException::class, IOException::class)
    protected fun fetchLastModifiedDate(
        syncManager: SyncManager,
        record: JSONObject
    ): RecordModDate {
        val objectType = SmartStore.project(record, Constants.SOBJECT_TYPE) as String
        val objectId = record.getString(idFieldName)
        val lastModRequest = RestRequest.getRequestForRetrieve(
            syncManager.apiVersion, objectType, objectId, listOf(
                modificationDateFieldName
            )
        )
        val lastModResponse = syncManager.sendSyncWithMobileSyncUserAgent(lastModRequest)
        return RecordModDate(
            if (lastModResponse.isSuccess) lastModResponse.asJSONObject().getString(
                modificationDateFieldName
            ) else null,
            lastModResponse.statusCode == HttpURLConnection.HTTP_NOT_FOUND
        )
    }

    /**
     * Same as fetchLastModifiedDate but operating over a list of records (expected to be of the same sobject type)
     *
     * @param syncManager
     * @param records
     * @return
     */
    @Throws(JSONException::class, IOException::class, MobileSyncException::class)
    protected fun fetchLastModifiedDates(
        syncManager: SyncManager,
        records: List<JSONObject>
    ): Map<String, RecordModDate> {
        val recordIdToLastModifiedDate: MutableMap<String, RecordModDate> = HashMap()
        val totalSize = records.size
        if (totalSize > 0) {
            val objectType = SmartStore.project(records[0], Constants.SOBJECT_TYPE) as String
            val batchStoreIds: MutableList<String> = ArrayList()
            val batchServerIds: MutableList<String> = ArrayList()
            for (i in 0 until totalSize) {
                val record = records[i]
                if (objectType != SmartStore.project(
                        record,
                        Constants.SOBJECT_TYPE
                    )
                ) {
                    throw MobileSyncException("All records should have same sobject type")
                }
                batchStoreIds.add(record.getString(SmartStore.SOUP_ENTRY_ID))
                batchServerIds.add(record.getString(idFieldName))

                // Process batch if max batch size reached or at the end of records
                if (batchServerIds.size == RestRequest.MAX_COLLECTION_RETRIEVE_SIZE || i == totalSize - 1) {
                    val request = RestRequest.getRequestForCollectionRetrieve(
                        syncManager.apiVersion, objectType, batchServerIds, listOf(
                            modificationDateFieldName
                        )
                    )
                    val response = syncManager.sendSyncWithMobileSyncUserAgent(request)
                    val responseAsArray = response.asJSONArray()
                    for (j in 0 until responseAsArray.length()) {
                        val storeId = batchStoreIds[j]
                        val lastModResponse =
                            if (responseAsArray.isNull(j)) null else responseAsArray.getJSONObject(j)
                        val serverModDate = RecordModDate(
                            lastModResponse?.getString(modificationDateFieldName),
                            lastModResponse == null
                        )
                        recordIdToLastModifiedDate[storeId] = serverModDate
                    }
                    batchServerIds.clear()
                    batchStoreIds.clear()
                }
            }
        }
        return recordIdToLastModifiedDate
    }

    /**
     * Return true if record is more recent than corresponding record on server
     * NB: also return true if both were deleted or if local mod date is missing
     *
     * Used to decide whether a record should be synced up or not when using merge mode leave-if-changed
     *
     * @param syncManager
     * @param record
     * @return
     * @throws JSONException
     * @throws IOException
     */
    @Throws(JSONException::class, IOException::class)
    open fun isNewerThanServer(syncManager: SyncManager, record: JSONObject): Boolean {
        if (isLocallyCreated(record)) {
            return true
        }
        val localModDate = RecordModDate(
            JSONObjectHelper.optString(record, modificationDateFieldName),
            isLocallyDeleted(record)
        )
        val remoteModDate = fetchLastModifiedDate(syncManager, record)
        return isNewerThanServer(localModDate, remoteModDate)
    }

    /**
     * Same as isNewerThanServer but operating over a list of records (expected to be of the same sobject type)
     * Return map from record store id to boolean
     *
     * @param syncManager
     * @param records
     * @return
     * @throws JSONException
     * @throws IOException
     */
    @Throws(JSONException::class, IOException::class)
    open fun areNewerThanServer(
        syncManager: SyncManager,
        records: List<JSONObject>
    ): MutableMap<String, Boolean> {
        val storeIdToNewerThanServer: MutableMap<String, Boolean> = HashMap()
        for (record in records) {
            val storeId = record.getString(SmartStore.SOUP_ENTRY_ID)
            storeIdToNewerThanServer[storeId] = isNewerThanServer(syncManager, record)
        }
        return storeIdToNewerThanServer
    }

    /**
     * Return true if local mod date is greater than remote mod date
     * NB: also return true if both were deleted or if local mod date is missing
     *
     * @param localModDate
     * @param remoteModDate
     * @return
     */
    protected fun isNewerThanServer(
        localModDate: RecordModDate?,
        remoteModDate: RecordModDate
    ): Boolean {
        return (localModDate?.timestamp != null && remoteModDate.timestamp != null && localModDate.timestamp >= remoteModDate.timestamp)
                || (localModDate?.isDeleted == true && remoteModDate.isDeleted)
                || (localModDate?.timestamp == null)
    }

    /**
     * Return ids of records to sync up
     * @param syncManager
     * @param soupName
     * @return
     */
    @Throws(JSONException::class)
    fun getIdsOfRecordsToSyncUp(syncManager: SyncManager, soupName: String): Set<String> {
        return getDirtyRecordIds(syncManager, soupName, SmartStore.SOUP_ENTRY_ID)
    }

    /**
     * Build map with the values for the fields in fieldlist from record
     * @param record
     * @param fieldlist
     * @param idFieldName
     * @param modificationDateFieldName
     * @return
     */
    protected fun buildFieldsMap(
        record: JSONObject,
        fieldlist: List<String>,
        idFieldName: String,
        modificationDateFieldName: String
    ): MutableMap<String, Any> {
        val fields: MutableMap<String, Any> = HashMap()
        for (fieldName in fieldlist) {
            if (fieldName != idFieldName && fieldName != modificationDateFieldName) {
                val fieldValue = SmartStore.projectReturningNULLObject(record, fieldName)
                if (fieldValue != null) {
                    fields[fieldName] = fieldValue
                }
            }
        }
        return fields
    }

    /**
     * Helper class used by isNewerThanServer
     */
    protected class RecordModDate(
        val timestamp: String?, // time stamp in the Constants.TIMESTAMP_FORMAT format - can be null if unknown
        val isDeleted: Boolean  // true if the record was deleted
    )

    companion object {
        // Constants
        const val TAG = "SyncUpTarget"
        @JvmField
        val CREATE_FIELDLIST = "createFieldlist"
        @JvmField
        val UPDATE_FIELDLIST = "updateFieldlist"
        @JvmField
        val EXTERNAL_ID_FIELD_NAME = "externalIdFieldName"

        /**
         * Build SyncUpTarget from json
         *
         * @param target as json
         * @return
         * @throws JSONException
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun fromJSON(target: JSONObject): SyncUpTarget {
            // Default sync up target (it's CollectionSyncUpTarget starting in Mobile SDK 10.1)
            return if (target.isNull(ANDROID_IMPL)) {
                CollectionSyncUpTarget(target)
            } else try {
                val implClass =
                    Class.forName(target.getString(ANDROID_IMPL)) as Class<out SyncUpTarget>
                val constructor = implClass.getConstructor(
                    JSONObject::class.java
                )
                constructor.newInstance(target)
            } catch (e: Exception) {
                throw RuntimeException(e)
            }

            // Non default sync up target
        }
    }
}