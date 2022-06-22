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
package com.salesforce.androidsdk.mobilesync.target;

 import com.salesforce.androidsdk.mobilesync.manager.SyncManager;
 import com.salesforce.androidsdk.mobilesync.util.Constants;
 import com.salesforce.androidsdk.rest.CollectionResponse;
 import com.salesforce.androidsdk.rest.CollectionResponse.CollectionSubResponse;
 import com.salesforce.androidsdk.rest.CompositeResponse;
 import com.salesforce.androidsdk.rest.CompositeResponse.CompositeSubResponse;
 import com.salesforce.androidsdk.rest.RestRequest;
 import com.salesforce.androidsdk.rest.RestResponse;
 import com.salesforce.androidsdk.util.JSONObjectHelper;
 import java.io.IOException;
 import java.io.UnsupportedEncodingException;
 import java.net.HttpURLConnection;
 import java.util.HashMap;
 import java.util.HashSet;
 import java.util.LinkedHashMap;
 import java.util.LinkedList;
 import java.util.List;
 import java.util.Map;
 import org.json.JSONArray;
 import org.json.JSONException;
 import org.json.JSONObject;

public class CompositeRequestHelper {

    /**
     * Send record requests using a composite batch request
     * @param syncManager
     * @param allOrNone
     * @param recordRequests
     * @return map of reference id to record responses
     * @throws JSONException
     * @throws IOException
     */
     public static Map<String, RecordResponse> sendAsCompositeBatchRequest(SyncManager syncManager, boolean allOrNone, List<RecordRequest> recordRequests) throws JSONException, IOException {
         LinkedHashMap<String, RestRequest> refIdToRequests = new LinkedHashMap<>();
         for (RecordRequest recordRequest : recordRequests) {
             refIdToRequests.put(recordRequest.referenceId, recordRequest.asRestRequest(syncManager.apiVersion));
         }

         RestRequest compositeRequest = RestRequest.getCompositeRequest(syncManager.apiVersion, allOrNone, refIdToRequests);
         RestResponse response = syncManager.sendSyncWithMobileSyncUserAgent(compositeRequest);
         if (!response.isSuccess()) {
             throw new SyncManager.MobileSyncException("sendCompositeRequest:" + response);
         }
         CompositeResponse compositeResponse = new CompositeResponse(response.asJSONObject());
         Map<String, RecordResponse> refIdToRecordResponses = new LinkedHashMap<>();
         for (CompositeSubResponse subResponse : compositeResponse.subResponses) {
             RecordResponse recordResponse = RecordResponse.fromCompositeSubResponse(subResponse);
             refIdToRecordResponses.put(subResponse.referenceId, recordResponse);
         }
         return refIdToRecordResponses;
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
    public static Map<String, RecordResponse> sendAsCollectionRequests(SyncManager syncManager, boolean allOrNone, List<RecordRequest> recordRequests) throws JSONException, IOException {
        Map<String, RecordResponse> refIdToRecordResponses = new LinkedHashMap<>();

        for (RequestType requestType : RequestType.values()) {
            List<String> refIds = RecordRequest.getRefIds(recordRequests, requestType);
            if (refIds.size() > 0) {
                RestRequest request = RecordRequest
                    .getCollectionRequest(syncManager.apiVersion, allOrNone, recordRequests, requestType);
                RestResponse response = syncManager.sendSyncWithMobileSyncUserAgent(request);
                if (!response.isSuccess()) {
                    throw new SyncManager.MobileSyncException(
                        "sendAsCollectionRequests:" + response);
                } else {
                    List<CollectionSubResponse> subResponses = new CollectionResponse(
                        response.asJSONArray()).subResponses;
                    for (int i = 0; i < subResponses.size(); i++) {
                        String refId = refIds.get(i);
                        RecordResponse recordResponse = RecordResponse.fromCollectionSubResponse(subResponses.get(i));
                        refIdToRecordResponses.put(refId, recordResponse);
                    }
                }
            }
        }

        return refIdToRecordResponses;
    }

    /**
     * @return ref id to server id map if successful
     */
    public static Map<String, String> parseIdsFromResponses(Map<String, RecordResponse> refIdToRecordResponse) throws JSONException {
        Map<String, String> refIdToServerId = new HashMap<>();
        for (Map.Entry<String, RecordResponse> entry : refIdToRecordResponse.entrySet()) {
            String refId = entry.getKey();
            RecordResponse recordResponse = entry.getValue();
            if (recordResponse.id != null) {
                refIdToServerId.put(refId, recordResponse.id);
            }
        }
        return refIdToServerId;
    }

    /**
     * Update id field with server id
     * @param record
     * @param fieldWithRefId
     * @param refIdToServerId
     * @throws JSONException
     */
    public static void updateReferences(JSONObject record, String fieldWithRefId, Map<String, String> refIdToServerId) throws JSONException {
        String refId = JSONObjectHelper.optString(record, fieldWithRefId);
        if (refId != null && refIdToServerId.containsKey(refId)) {
            record.put(fieldWithRefId, refIdToServerId.get(refId));
        }
    }

    /**
     * Response object abstracting away differences between /composite/batch and /commposite/sobject sub-responses
     */
    static class RecordResponse {
        boolean success;
        String id;
        boolean recordDoesNotExist;
        boolean relatedRecordDoesNotExist;
        JSONObject errorJson;
        JSONObject json;

        private RecordResponse(boolean success, String id, boolean recordDoesNotExist, boolean relatedRecordDoesNotExist, JSONObject errorJson, JSONObject json) {
            this.success = success;
            this.id = id;
            this.recordDoesNotExist = recordDoesNotExist;
            this.relatedRecordDoesNotExist = relatedRecordDoesNotExist;
            this.errorJson = errorJson;
            this.json = json;
        }

        @Override
        public String toString() {
            return json.toString();
        }

        static RecordResponse fromCompositeSubResponse(CompositeSubResponse compositeSubResponse) throws JSONException {
            boolean success = compositeSubResponse.isSuccess();
            String id = null;
            boolean recordDoesNotExist = false;
            boolean relatedRecordDoesNotExist = false;
            JSONObject errorJson = null;

            if (success) {
                JSONObject responseBodyResponse = compositeSubResponse.bodyAsJSONObject();
                if (responseBodyResponse != null) {
                    id = JSONObjectHelper.optString(responseBodyResponse, Constants.LID);
                }
            } else {
                recordDoesNotExist = compositeSubResponse.httpStatusCode == HttpURLConnection.HTTP_NOT_FOUND;
                JSONArray bodyArray = compositeSubResponse.bodyAsJSONArray();
                errorJson = bodyArray != null && bodyArray.length() > 0 ? bodyArray.getJSONObject(0) : null;
                relatedRecordDoesNotExist = errorJson != null ? "ENTITY_IS_DELETED".equals(errorJson.getString("errorCode")) : false;
            }
            return new RecordResponse(success, id, recordDoesNotExist, relatedRecordDoesNotExist, errorJson, compositeSubResponse.json);
        }

        static RecordResponse fromCollectionSubResponse(CollectionSubResponse collectionSubResponse) {
            boolean success = collectionSubResponse.success;
            String id = collectionSubResponse.id;
            boolean recordDoesNotExist = false;
            boolean relatedRecordDoesNotExist = false;
            JSONObject errorJson = null;

            if (!collectionSubResponse.success && !collectionSubResponse.errors.isEmpty()) {
                errorJson = collectionSubResponse.errors.get(0).json;
                String error = collectionSubResponse.errors.get(0).statusCode;
                recordDoesNotExist = "INVALID_CROSS_REFERENCE_KEY".equals(error)
                    || "ENTITY_IS_DELETED".equals(error);
                relatedRecordDoesNotExist = "ENTITY_IS_DELETED".equals(error); // XXX ambiguous
            }
            return new RecordResponse(success, id, recordDoesNotExist, relatedRecordDoesNotExist, errorJson, collectionSubResponse.json);
        }
    }

    /**
     * Request object abstracting away differences between /composite/batch and /commposite/sobject sub-requests
     */
    static class RecordRequest {
        String referenceId;
        RequestType requestType;
        String objectType;
        Map<String, Object> fields;
        String id;
        String externalId;
        String externalIdFieldName;

        private RecordRequest(RequestType requestType, String objectType, Map<String, Object> fields, String id, String externalId, String externalIdFieldName) {
            this.requestType = requestType;
            this.objectType = objectType;
            this.fields = fields;
            this.id = id;
            this.externalId = externalId;
            this.externalIdFieldName = externalIdFieldName;
        }

        public RestRequest asRestRequest(String apiVersion) {
            switch (requestType) {
                case CREATE:
                    return RestRequest.getRequestForCreate(apiVersion, objectType, fields);
                case UPDATE:
                    return RestRequest.getRequestForUpdate(apiVersion, objectType, id, fields);
                case UPSERT:
                    return RestRequest.getRequestForUpsert(apiVersion, objectType, externalIdFieldName, externalId, fields);
                case DELETE:
                    return RestRequest.getRequestForDelete(apiVersion, objectType, id);
            }
            // We should never get here
            return null;
        }

        JSONObject asJSONObjectForCollectionRequest() throws JSONException {
            JSONObject record = new JSONObject();
            JSONObject attributes = new JSONObject();
            attributes.put(Constants.LTYPE, objectType);
            record.put(Constants.ATTRIBUTES, attributes);
            if (fields != null) {
                for (Map.Entry<String, Object> entry : fields.entrySet()) {
                    record.put(entry.getKey(), entry.getValue());
                }
            }

            if (requestType == RequestType.UPDATE) {
                record.put(Constants.ID, id);
            }

            if (requestType == RequestType.UPSERT) {
                record.put(externalIdFieldName, externalId);
            }

            return record;
        }

        static RecordRequest requestForCreate(String objectType, Map<String, Object> fields) {
            return new RecordRequest(RequestType.CREATE, objectType, fields, null, null, null);
        }

        static RecordRequest requestForUpdate(String objectType, String id, Map<String, Object> fields) {
            return new RecordRequest(RequestType.UPDATE, objectType, fields, id, null, null);
        }

        static RecordRequest requestForUpsert(String objectType, String externalIdFieldName, String externalId, Map<String, Object> fields) {
            return new RecordRequest(RequestType.UPSERT, objectType, fields, null, externalId, externalIdFieldName);
        }

        static RecordRequest requestForDelete(String objectType, String id) {
            return new RecordRequest(RequestType.DELETE, objectType, null, id, null, null);
        }

        static List<String> getRefIds(List<RecordRequest> recordRequests, RequestType requestType) {
            List<String> refIds = new LinkedList<>();
            for (RecordRequest recordRequest : recordRequests) {
                if (recordRequest.requestType == requestType) {
                    refIds.add(recordRequest.referenceId);
                }
            }
            return refIds;
        }

        static List<String> getIds(List<RecordRequest> recordRequests, RequestType requestType) {
            List<String> ids = new LinkedList<>();
            for (RecordRequest recordRequest : recordRequests) {
                if (recordRequest.requestType == requestType) {
                    ids.add(recordRequest.id);
                }
            }
            return ids;
        }

        static List<String> getObjectTypes(List<RecordRequest> recordRequests, RequestType requestType) {
            List<String> objectTypes = new LinkedList<>();
            for (RecordRequest recordRequest : recordRequests) {
                if (recordRequest.requestType == requestType) {
                    objectTypes.add(recordRequest.objectType);
                }
            }
            return objectTypes;
        }

        static List<String> getExternalIdFieldNames(List<RecordRequest> recordRequests, RequestType requestType) {
            List<String> externalIdFieldNames = new LinkedList<>();
            for (RecordRequest recordRequest : recordRequests) {
                if (recordRequest.requestType == requestType) {
                    externalIdFieldNames.add(recordRequest.externalIdFieldName);
                }
            }
            return externalIdFieldNames;
        }

        static JSONArray getJSONArrayForCollectionRequest(List<RecordRequest> recordRequests, RequestType requestType)
            throws JSONException {
            JSONArray jsonArray = new JSONArray();
            for (RecordRequest recordRequest : recordRequests) {
                if (recordRequest.requestType == requestType) {
                    jsonArray.put(recordRequest.asJSONObjectForCollectionRequest());
                }
            }
            return jsonArray;
        }

        static RestRequest getCollectionRequest(String apiVersion, boolean allOrNone, List<RecordRequest> recordRequests, RequestType requestType)
            throws JSONException, UnsupportedEncodingException {
            switch(requestType) {
                case CREATE:
                    return RestRequest.getRequestForCollectionCreate(apiVersion, allOrNone, getJSONArrayForCollectionRequest(recordRequests, RequestType.CREATE));
                case UPDATE:
                    return RestRequest.getRequestForCollectionUpdate(apiVersion, allOrNone, getJSONArrayForCollectionRequest(recordRequests, RequestType.UPDATE));
                case UPSERT:
                    JSONArray records = getJSONArrayForCollectionRequest(recordRequests, RequestType.UPSERT);

                    if (records.length() > 0) {
                        List<String> objectTypes = getObjectTypes(recordRequests, RequestType.UPSERT);
                        List<String> externalIdFieldNames = getExternalIdFieldNames(recordRequests, RequestType.UPSERT);

                        if (objectTypes.size() == 0 || externalIdFieldNames.size() == 0) {
                            throw new SyncManager.MobileSyncException("Missing sobjectType or externalIdFieldName");
                        }

                        if (new HashSet<>(objectTypes).size() > 1) {
                            throw new SyncManager.MobileSyncException("All records must have same sobjectType");
                        }

                        String objectType = objectTypes.get(0);
                        String externalIdFieldName = externalIdFieldNames.get(0);

                        return RestRequest
                                .getRequestForCollectionUpsert(apiVersion,
                                        allOrNone,
                                        objectType,
                                        externalIdFieldName,
                                        records);
                    }
                case DELETE:
                    return RestRequest.getRequestForCollectionDelete(apiVersion, false, getIds(recordRequests, RequestType.DELETE));
            }

            // We should never get here
            return null;
        }
    }

    static enum RequestType {
        CREATE,
        UPDATE,
        UPSERT,
        DELETE
    }
}
