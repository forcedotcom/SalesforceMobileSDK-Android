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
import com.salesforce.androidsdk.mobilesync.target.CompositeRequestHelper.RequestType.CREATE
import com.salesforce.androidsdk.mobilesync.target.CompositeRequestHelper.RequestType.DELETE
import com.salesforce.androidsdk.mobilesync.target.CompositeRequestHelper.RequestType.UPDATE
import com.salesforce.androidsdk.mobilesync.target.CompositeRequestHelper.RequestType.UPSERT
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.rest.CollectionResponse
import com.salesforce.androidsdk.rest.CollectionResponse.CollectionSubResponse
import com.salesforce.androidsdk.rest.CompositeResponse
import com.salesforce.androidsdk.rest.CompositeResponse.CompositeSubResponse
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection

object CompositeRequestHelper {
    /**
     * Send record requests using a composite batch request
     * @param syncManager
     * @param allOrNone
     * @param recordRequests
     * @return map of reference id to record responses
     * @throws JSONException
     * @throws IOException
     */
    @Throws(JSONException::class, IOException::class, MobileSyncException::class)
    fun sendAsCompositeBatchRequest(
        syncManager: SyncManager,
        allOrNone: Boolean,
        recordRequests: List<RecordRequest>
    ): Map<String, RecordResponse> {
        val refIdToRequests = LinkedHashMap<String, RestRequest?>()
        for (recordRequest in recordRequests) {
            val refId = recordRequest.referenceId ?: continue
            refIdToRequests[refId] = recordRequest.asRestRequest(syncManager.apiVersion)
        }
        val compositeRequest: RestRequest =
            RestRequest.getCompositeRequest(syncManager.apiVersion, allOrNone, refIdToRequests)
        val response = syncManager.sendSyncWithMobileSyncUserAgent(compositeRequest)
        if (!response.isSuccess) {
            throw MobileSyncException("sendCompositeRequest:$response")
        }
        val compositeResponse = CompositeResponse(response.asJSONObject())
        val refIdToRecordResponses: MutableMap<String, RecordResponse> = LinkedHashMap()
        for (subResponse in compositeResponse.subResponses) {
            val recordResponse = RecordResponse.fromCompositeSubResponse(subResponse)
            refIdToRecordResponses[subResponse.referenceId] = recordResponse
        }
        return refIdToRecordResponses
    }

    /**
     * Send record requests using sobject collection requests
     * @param syncManager
     * @param allOrNone
     * @param recordRequests
     * @return map of ref id to record responses
     * @throws JSONException
     * @throws IOException
     */
    @Throws(JSONException::class, IOException::class, MobileSyncException::class)
    fun sendAsCollectionRequests(
        syncManager: SyncManager,
        allOrNone: Boolean,
        recordRequests: List<RecordRequest>
    ): Map<String, RecordResponse> {
        val refIdToRecordResponses: MutableMap<String, RecordResponse> = LinkedHashMap()
        for (requestType in RequestType.values()) {
            val refIds = RecordRequest.getRefIds(recordRequests, requestType)
            if (refIds.isNotEmpty()) {
                val request = RecordRequest.getCollectionRequest(
                    syncManager.apiVersion,
                    allOrNone,
                    recordRequests,
                    requestType
                )
                val response = syncManager.sendSyncWithMobileSyncUserAgent(request)
                if (!response.isSuccess) {
                    throw MobileSyncException(
                        "sendAsCollectionRequests:$response"
                    )
                } else {
                    val subResponses = CollectionResponse(
                        response.asJSONArray()
                    ).subResponses
                    for (i in subResponses.indices) {
                        val refId = refIds[i]
                        val recordResponse = RecordResponse.fromCollectionSubResponse(
                            subResponses[i]
                        )
                        refIdToRecordResponses[refId] = recordResponse
                    }
                }
            }
        }
        return refIdToRecordResponses
    }

    /**
     * @return ref id to server id map if successful
     */
    @Throws(JSONException::class)
    fun parseIdsFromResponses(refIdToRecordResponse: Map<String, RecordResponse>): Map<String, String> {
        val refIdToServerId: MutableMap<String, String> = HashMap()
        for ((refId, recordResponse) in refIdToRecordResponse) {
            refIdToServerId[refId] = recordResponse.id ?: continue
        }
        return refIdToServerId
    }

    /**
     * Update id field with server id
     * @param record
     * @param fieldWithRefId
     * @param refIdToServerId
     * @throws JSONException
     */
    @Throws(JSONException::class)
    fun updateReferences(
        record: JSONObject,
        fieldWithRefId: String,
        refIdToServerId: Map<String, String>
    ) {
        val refId = JSONObjectHelper.optString(record, fieldWithRefId)
        if (refId != null && refIdToServerId.containsKey(refId)) {
            record.put(fieldWithRefId, refIdToServerId[refId])
        }
    }

    /**
     * Response object abstracting away differences between /composite/batch and /composite/sobject sub-responses
     */
    class RecordResponse private constructor(
        var success: Boolean,
        var id: String?,
        var recordDoesNotExist: Boolean,
        var relatedRecordDoesNotExist: Boolean,
        var errorJson: JSONObject?,
        var json: JSONObject
    ) {
        override fun toString(): String {
            return json.toString()
        }

        companion object {
            @Throws(JSONException::class)
            fun fromCompositeSubResponse(compositeSubResponse: CompositeSubResponse): RecordResponse {
                val success = compositeSubResponse.isSuccess
                var id: String? = null
                var recordDoesNotExist = false
                var relatedRecordDoesNotExist = false
                var errorJson: JSONObject? = null
                if (success) {
                    val responseBodyResponse = compositeSubResponse.bodyAsJSONObject()
                    if (responseBodyResponse != null) {
                        id = JSONObjectHelper.optString(responseBodyResponse, Constants.LID)
                    }
                } else {
                    recordDoesNotExist =
                        compositeSubResponse.httpStatusCode == HttpURLConnection.HTTP_NOT_FOUND
                    val bodyArray = compositeSubResponse.bodyAsJSONArray()
                    errorJson =
                        if (bodyArray != null && bodyArray.length() > 0) bodyArray.getJSONObject(0) else null
                    relatedRecordDoesNotExist =
                        if (errorJson != null) "ENTITY_IS_DELETED" == errorJson.getString("errorCode") else false
                }
                return RecordResponse(
                    success,
                    id,
                    recordDoesNotExist,
                    relatedRecordDoesNotExist,
                    errorJson,
                    compositeSubResponse.json
                )
            }

            fun fromCollectionSubResponse(collectionSubResponse: CollectionSubResponse): RecordResponse {
                val success = collectionSubResponse.success
                val id = collectionSubResponse.id
                var recordDoesNotExist = false
                var relatedRecordDoesNotExist = false
                var errorJson: JSONObject? = null
                if (!collectionSubResponse.success && collectionSubResponse.errors.isNotEmpty()) {
                    errorJson = collectionSubResponse.errors[0].json
                    val error = collectionSubResponse.errors[0].statusCode
                    recordDoesNotExist =
                        "INVALID_CROSS_REFERENCE_KEY" == error || "ENTITY_IS_DELETED" == error
                    relatedRecordDoesNotExist = "ENTITY_IS_DELETED" == error // XXX ambiguous
                }
                return RecordResponse(
                    success,
                    id,
                    recordDoesNotExist,
                    relatedRecordDoesNotExist,
                    errorJson,
                    collectionSubResponse.json
                )
            }
        }
    }

    /**
     * Request object abstracting away differences between /composite/batch and /composite/sobject sub-requests
     */
    class RecordRequest private constructor(
        var requestType: RequestType,
        var objectType: String?,
        var fields: Map<String, Any?>?,
        var id: String?,
        var externalId: String?,
        var externalIdFieldName: String?
    ) {
        var referenceId: String? = null
        fun asRestRequest(apiVersion: String): RestRequest {
            return when (requestType) {
                CREATE -> RestRequest.getRequestForCreate(
                    apiVersion,
                    objectType,
                    fields
                )

                UPDATE -> RestRequest.getRequestForUpdate(
                    apiVersion,
                    objectType,
                    id,
                    fields
                )

                UPSERT -> RestRequest.getRequestForUpsert(
                    apiVersion,
                    objectType,
                    externalIdFieldName,
                    externalId,
                    fields
                )

                DELETE -> RestRequest.getRequestForDelete(
                    apiVersion,
                    objectType,
                    id
                )
            }
        }

        @Throws(JSONException::class)
        fun asJSONObjectForCollectionRequest(): JSONObject {
            val record = JSONObject()
            val attributes = JSONObject()
            attributes.put(Constants.LTYPE, objectType)
            record.put(Constants.ATTRIBUTES, attributes)
            fields?.forEach { (key, value) ->
                record.put(key, value)
            }
            if (requestType == UPDATE) {
                record.put(Constants.ID, id)
            }
            if (requestType == UPSERT) {
                externalIdFieldName?.let { record.put(it, externalId) }
            }
            return record
        }

        companion object {
            fun requestForCreate(objectType: String?, fields: Map<String, Any?>): RecordRequest {
                return RecordRequest(CREATE, objectType, fields, null, null, null)
            }

            fun requestForUpdate(
                objectType: String,
                id: String?,
                fields: Map<String, Any?>
            ): RecordRequest {
                return RecordRequest(UPDATE, objectType, fields, id, null, null)
            }

            fun requestForUpsert(
                objectType: String,
                externalIdFieldName: String?,
                externalId: String?,
                fields: Map<String, Any?>
            ): RecordRequest {
                return RecordRequest(
                    UPSERT,
                    objectType,
                    fields,
                    null,
                    externalId,
                    externalIdFieldName
                )
            }

            fun requestForDelete(objectType: String, id: String): RecordRequest {
                return RecordRequest(DELETE, objectType, null, id, null, null)
            }

            fun getRefIds(
                recordRequests: List<RecordRequest>,
                requestType: RequestType
            ): List<String> {
                return recordRequests
                    .filter { it.requestType == requestType }
                    .mapNotNull { it.referenceId }
            }

            fun getIds(
                recordRequests: List<RecordRequest>,
                requestType: RequestType
            ): List<String> {
                return recordRequests
                    .filter { it.requestType == requestType }
                    .mapNotNull { it.id }
            }

            fun getObjectTypes(
                recordRequests: List<RecordRequest>,
                requestType: RequestType
            ): List<String> {
                return recordRequests
                    .filter { it.requestType == requestType }
                    .mapNotNull { it.objectType }
            }

            fun getExternalIdFieldNames(
                recordRequests: List<RecordRequest>,
                requestType: RequestType
            ): List<String> {
                return recordRequests
                    .filter { it.requestType == requestType }
                    .mapNotNull { it.externalIdFieldName }
            }

            @Throws(JSONException::class)
            fun getJSONArrayForCollectionRequest(
                recordRequests: List<RecordRequest>,
                requestType: RequestType
            ): JSONArray {
                return JSONArray().also { jsonArr ->
                    recordRequests
                        .filter { it.requestType == requestType }
                        .forEach { jsonArr.put(it.asJSONObjectForCollectionRequest()) }
                }
            }

            @Throws(
                JSONException::class, UnsupportedEncodingException::class,
                MobileSyncException::class
            )
            fun getCollectionRequest(
                apiVersion: String?,
                allOrNone: Boolean,
                recordRequests: List<RecordRequest>,
                requestType: RequestType
            ): RestRequest {
                when (requestType) {
                    CREATE -> return RestRequest.getRequestForCollectionCreate(
                        apiVersion,
                        allOrNone,
                        getJSONArrayForCollectionRequest(recordRequests, CREATE)
                    )

                    UPDATE -> return RestRequest.getRequestForCollectionUpdate(
                        apiVersion,
                        allOrNone,
                        getJSONArrayForCollectionRequest(recordRequests, UPDATE)
                    )

                    UPSERT -> {
                        val records =
                            getJSONArrayForCollectionRequest(recordRequests, UPSERT)
                        if (records.length() > 0) {
                            val objectTypes = getObjectTypes(recordRequests, UPSERT)
                            val externalIdFieldNames =
                                getExternalIdFieldNames(recordRequests, UPSERT)
                            if (objectTypes.size == 0 || externalIdFieldNames.isEmpty()) {
                                throw MobileSyncException("Missing sobjectType or externalIdFieldName")
                            }
                            if (HashSet(objectTypes).size > 1) {
                                throw MobileSyncException("All records must have same sobjectType")
                            }
                            val objectType = objectTypes[0]
                            val externalIdFieldName = externalIdFieldNames[0]
                            return RestRequest
                                .getRequestForCollectionUpsert(
                                    apiVersion,
                                    allOrNone,
                                    objectType,
                                    externalIdFieldName,
                                    records
                                )
                        }
                        return RestRequest.getRequestForCollectionDelete(
                            apiVersion,
                            false,
                            getIds(recordRequests, DELETE)
                        )
                    }

                    DELETE -> return RestRequest.getRequestForCollectionDelete(
                        apiVersion,
                        false,
                        getIds(recordRequests, DELETE)
                    )
                }
            }
        }
    }

    enum class RequestType {
        CREATE, UPDATE, UPSERT, DELETE
    }
}