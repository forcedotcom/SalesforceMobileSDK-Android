/*
 * Copyright (c) 2019-present, salesforce.com, inc.
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
import com.salesforce.androidsdk.mobilesync.target.CompositeRequestHelper.RecordRequest
import com.salesforce.androidsdk.mobilesync.target.CompositeRequestHelper.RecordResponse
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.SyncState.MergeMode
import com.salesforce.androidsdk.smartstore.store.SmartStore
import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.LinkedList
import kotlin.math.min

/**
 * Subclass of SyncUpTarget that batches create/update/delete operations by using composite api
 */
open class BatchSyncUpTarget : SyncUpTarget, AdvancedSyncUpTarget {
    // Max batch size
    final override var maxBatchSize: Int
        protected set

    /**
     * Construct SyncUpTarget with a different maxBatchSize (NB: cannot exceed MAX_SUB_REQUESTS_COMPOSITE_API)
     */
    constructor(
        createFieldlist: List<String>?,
        updateFieldlist: List<String>?,
        maxBatchSize: Int
    ) : this(createFieldlist, updateFieldlist, null, null, null, maxBatchSize)

    /**
     * Construct BatchSyncUpTarget
     */
    @JvmOverloads
    constructor(
        createFieldlist: List<String>? = null,
        updateFieldlist: List<String>? = null,
        idFieldName: String? = null,
        modificationDateFieldName: String? = null,
        externalIdFieldName: String? = null,
        maxBatchSize: Int = MAX_SUB_REQUESTS_COMPOSITE_API
    ) : super(
        createFieldlist,
        updateFieldlist,
        idFieldName,
        modificationDateFieldName,
        externalIdFieldName
    ) {
        this.maxBatchSize = min(
            maxBatchSize,
            MAX_SUB_REQUESTS_COMPOSITE_API
        ) // composite api allows up to 25 subrequests
    }

    /**
     * Construct SyncUpTarget from json
     *
     * @param target
     * @throws JSONException
     */
    constructor(target: JSONObject) : super(target) {
        maxBatchSize = min(
            target.optInt(MAX_BATCH_SIZE, MAX_SUB_REQUESTS_COMPOSITE_API),
            MAX_SUB_REQUESTS_COMPOSITE_API
        ) // composite api allows up to 25 subrequests
    }

    /**
     * @return json representation of target
     * @throws JSONException
     */
    @Throws(JSONException::class)
    override fun asJSON(): JSONObject {
        return with(super.asJSON()) {
            put(MAX_BATCH_SIZE, maxBatchSize)
        }
    }

    @Throws(JSONException::class, IOException::class)
    override fun syncUpRecords(
        syncManager: SyncManager,
        records: List<JSONObject>,
        fieldlist: List<String>?,
        mergeMode: MergeMode,
        syncSoupName: String
    ) {
        syncUpRecords(syncManager, records, fieldlist, mergeMode, syncSoupName, false)
    }

    @Throws(JSONException::class, IOException::class, MobileSyncException::class)
    private fun syncUpRecords(
        syncManager: SyncManager,
        records: List<JSONObject>,
        fieldlist: List<String>?,
        mergeMode: MergeMode,
        syncSoupName: String,
        isReRun: Boolean
    ) {
        if (records.size > maxBatchSize) {
            throw MobileSyncException("${javaClass.simpleName}:syncUpRecords can handle up to $maxBatchSize records")
        }
        if (records.isEmpty()) {
            return
        }
        val recordRequests: MutableList<RecordRequest> = LinkedList()
        records.forEach { record ->
            val id = JSONObjectHelper.optString(record, idFieldName)
            // create local id - needed for refId
                ?: createLocalId().also {
                    record.put(idFieldName, it)
                }
            val request = buildRequestForRecord(record, fieldlist)
            if (request != null) {
                request.referenceId = id
                recordRequests.add(request)
            }
        }

        // Sending requests
        val refIdToRecordResponses = sendRecordRequests(syncManager, recordRequests)

        // Build refId to server id map
        val refIdToServerId = CompositeRequestHelper.parseIdsFromResponses(refIdToRecordResponses)

        // Will a re-run be required?
        var needReRun = false

        // Update local store
        for (i in records.indices) {
            val record = records[i]
            val id = record.getString(idFieldName)
            if (isDirty(record)) {
                needReRun = needReRun || updateRecordInLocalStore(
                    syncManager,
                    syncSoupName,
                    record,
                    mergeMode,
                    refIdToServerId,
                    refIdToRecordResponses[id],
                    isReRun
                )
            }
        }

        // Re-run if required
        if (needReRun && !isReRun) {
            syncUpRecords(syncManager, records, fieldlist, mergeMode, syncSoupName, true)
        }
    }

    @Throws(JSONException::class, IOException::class)
    protected open fun sendRecordRequests(
        syncManager: SyncManager,
        recordRequests: List<RecordRequest>
    ): Map<String, RecordResponse> {
        return CompositeRequestHelper.sendAsCompositeBatchRequest(
            syncManager,
            false,
            recordRequests
        )
    }

    @Throws(JSONException::class, MobileSyncException::class)
    protected fun buildRequestForRecord(
        record: JSONObject,
        fieldlist: List<String>?
    ): RecordRequest? {
        if (!isDirty(record)) {
            return null // nothing to do
        }
        val objectType = SmartStore.project(record, Constants.SOBJECT_TYPE) as? String ?: "null"
        val id = record.getString(idFieldName)

        // Delete case
        val isDelete = isLocallyDeleted(record)
        val isCreate = isLocallyCreated(record)
        return if (isDelete) {
            if (isCreate) {
                null // no need to go to server
            } else {
                RecordRequest.requestForDelete(objectType, id)
            }
        } else {
            val fields: Map<String, Any?>
            if (isCreate) {
                val fieldlistToUse =
                    createFieldlist ?: fieldlist ?: throw MobileSyncException("No fields specified")
                fields =
                    buildFieldsMap(record, fieldlistToUse, idFieldName, modificationDateFieldName)
                val externalId = if (externalIdFieldName != null) JSONObjectHelper.optString(
                    record,
                    externalIdFieldName
                ) else null

                // Do upsert if externalId specified
                if (externalId != null // the following check is there for the case
                    // where the the external id field is the id field
                    // and the field is populated by a local id
                    && !isLocalId(externalId)
                ) {
                    RecordRequest.requestForUpsert(
                        objectType,
                        externalIdFieldName,
                        externalId,
                        fields
                    )
                } else {
                    RecordRequest.requestForCreate(objectType, fields)
                }
            } else {
                val fieldlistToUse =
                    updateFieldlist ?: fieldlist ?: throw MobileSyncException("No fields specified")
                fields =
                    buildFieldsMap(record, fieldlistToUse, idFieldName, modificationDateFieldName)
                RecordRequest.requestForUpdate(objectType, id, fields)
            }
        }
    }

    @Throws(JSONException::class, IOException::class)
    protected fun updateRecordInLocalStore(
        syncManager: SyncManager,
        soupName: String,
        record: JSONObject,
        mergeMode: MergeMode,
        refIdToServerId: Map<String, String>,
        response: RecordResponse?,
        isReRun: Boolean
    ): Boolean {
        var needReRun = false
        val lastError = response?.errorJson?.toString()

        // Delete case
        if (isLocallyDeleted(record)) {
            if (isLocallyCreated(record) // we didn't go to the server
                || response?.success == true     // or we successfully deleted on the server
                || response?.recordDoesNotExist == true // or the record was already deleted on the server
            ) {
                deleteFromLocalStore(syncManager, soupName, record)
            } else {
                saveRecordToLocalStoreWithError(syncManager, soupName, record, lastError)
            }
        } else {
            // Success case
            if (response?.success == true) {
                // Plugging server id in id field
                CompositeRequestHelper.updateReferences(record, idFieldName, refIdToServerId)

                // Clean and save
                cleanAndSaveInLocalStore(syncManager, soupName, record)
            } else if (response?.recordDoesNotExist == true && mergeMode == MergeMode.OVERWRITE // Record needs to be recreated
                && !isReRun
            ) {
                record.put(LOCAL, true)
                record.put(LOCALLY_CREATED, true)
                needReRun = true
            } else {
                saveRecordToLocalStoreWithError(syncManager, soupName, record, lastError)
            }
        }
        return needReRun
    }

    companion object {
        // Constants
        @JvmField
        val MAX_SUB_REQUESTS_COMPOSITE_API = 25
        @JvmField
        val MAX_BATCH_SIZE = "maxBatchSize"
    }
}