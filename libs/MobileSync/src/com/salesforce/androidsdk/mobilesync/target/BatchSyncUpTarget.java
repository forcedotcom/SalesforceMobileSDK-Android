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
import com.salesforce.androidsdk.mobilesync.util.SyncState;
import com.salesforce.androidsdk.rest.CompositeResponse.CompositeSubResponse;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Subclass of SyncUpTarget that batches create/update/delete operations by using composite api
 */
public class BatchSyncUpTarget extends SyncUpTarget implements AdvancedSyncUpTarget {

    // Constants
    public static final int MAX_SUB_REQUESTS_COMPOSITE_API = 25;
    public static final String MAX_BATCH_SIZE = "maxBatchSize";

    // Max batch size
    protected int maxBatchSize;

    /**
     * Construct SyncUpTarget
     */
    public BatchSyncUpTarget() {
        this(null, null);
    }

    /**
     * Construct SyncUpTarget
     */
    public BatchSyncUpTarget(List<String> createFieldlist, List<String> updateFieldlist) {
        this(createFieldlist, updateFieldlist, null, null, null, MAX_SUB_REQUESTS_COMPOSITE_API);
    }

    /**
     * Construct SyncUpTarget with a different maxBatchSize (NB: cannot exceed MAX_SUB_REQUESTS_COMPOSITE_API)
     */
    public BatchSyncUpTarget(List<String> createFieldlist, List<String> updateFieldlist, int maxBatchSize) {
        this(createFieldlist, updateFieldlist, null, null, null, maxBatchSize);
    }

    /**
     * Construct SyncUpTarget with given id/modifiedDate/externalId fields
     */
    public BatchSyncUpTarget(List<String> createFieldlist, List<String> updateFieldlist, String idFieldName, String modificationDateFieldName, String externalIdFieldName) {
        this(createFieldlist, updateFieldlist, idFieldName, modificationDateFieldName, externalIdFieldName, MAX_SUB_REQUESTS_COMPOSITE_API);
    }

    /**
     * Construct BatchSyncUpTarget with a different maxBatchSize and id/modifiedDate/externalId fields
     */
    public BatchSyncUpTarget(List<String> createFieldlist, List<String> updateFieldlist, String idFieldName, String modificationDateFieldName, String externalIdFieldName, int maxBatchSize) {
        super(createFieldlist, updateFieldlist, idFieldName, modificationDateFieldName, externalIdFieldName);
        this.maxBatchSize = Math.min(maxBatchSize, MAX_SUB_REQUESTS_COMPOSITE_API); // composite api allows up to 25 subrequests
    }

    /**
     * Construct SyncUpTarget from json
     *
     * @param target
     * @throws JSONException
     */
    public BatchSyncUpTarget(JSONObject target) throws JSONException {
        super(target);
        this.maxBatchSize = Math.min(target.optInt(MAX_BATCH_SIZE, MAX_SUB_REQUESTS_COMPOSITE_API), MAX_SUB_REQUESTS_COMPOSITE_API); // composite api allows up to 25 subrequests
    }

    /**
     * @return json representation of target
     * @throws JSONException
     */
    public JSONObject asJSON() throws JSONException {
        JSONObject target = super.asJSON();
        target.put(MAX_BATCH_SIZE, maxBatchSize);
        return target;
    }

    @Override
    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    @Override
    public void syncUpRecords(SyncManager syncManager, List<JSONObject> records, List<String> fieldlist, SyncState.MergeMode mergeMode, String syncSoupName) throws JSONException, IOException {
        syncUpRecords(syncManager, records, fieldlist, mergeMode, syncSoupName, false);
    }

    private void syncUpRecords(SyncManager syncManager, List<JSONObject> records, List<String> fieldlist, SyncState.MergeMode mergeMode, String syncSoupName, boolean isReRun) throws JSONException, IOException {

        if (records.size() > getMaxBatchSize()) {
            throw new SyncManager.MobileSyncException(getClass().getSimpleName() + ":syncUpRecords can handle up to " + getMaxBatchSize() + " records");
        }

        if (records.isEmpty()) {
            return;
        }

        LinkedHashMap<String, RestRequest> refIdToRequests = new LinkedHashMap<>();
        for (int i = 0; i < records.size(); i++) {
            JSONObject record = records.get(i);
            String id = JSONObjectHelper.optString(record, getIdFieldName());

            if (id == null) {
                // create local id - needed for refId
                id = createLocalId(record);
                record.put(getIdFieldName(), id);
            }

            RestRequest request = buildRequestForRecord(syncManager.apiVersion, record, fieldlist);
            if (request != null) refIdToRequests.put(id, request);
        }

        // Sending composite request
        Map<String, CompositeSubResponse> refIdToResponses = CompositeRequestHelper.sendCompositeRequest(syncManager, false, refIdToRequests);

        // Build refId to server id / status code / time stamp maps
        Map<String, String> refIdToServerId = CompositeRequestHelper.parseIdsFromResponses(refIdToResponses.values());

        // Will a re-run be required?
        boolean needReRun = false;

        // Update local store
        for (int i = 0; i < records.size(); i++) {
            JSONObject record = records.get(i);
            String id = record.getString(getIdFieldName());

            if (isDirty(record)) {
                needReRun = needReRun || updateRecordInLocalStore(syncManager, syncSoupName, record, mergeMode, refIdToServerId, refIdToResponses.get(id), isReRun);
            }
        }

        // Re-run if required
        if (needReRun && !isReRun) {
            syncUpRecords(syncManager, records, fieldlist, mergeMode, syncSoupName, true);
        }

    }

    protected RestRequest buildRequestForRecord(String apiVersion,
                                                JSONObject record,
                                                List<String> fieldlist) throws JSONException {

        if (!isDirty(record)) {
            return null; // nothing to do
        }

        String objectType = (String) SmartStore.project(record, Constants.SOBJECT_TYPE);
        String id = record.getString(getIdFieldName());

        // Delete case
        boolean isDelete = isLocallyDeleted(record);
        boolean isCreate = isLocallyCreated(record);
        if (isDelete) {
            if (isCreate) {
                return null; // no need to go to server
            }
            else {
                return RestRequest.getRequestForDelete(apiVersion, objectType, id);
            }
        }
        // Create/update cases
        else {
            Map<String, Object> fields;
            if (isCreate) {
                fieldlist = this.createFieldlist != null ? this.createFieldlist : fieldlist;
                fields = buildFieldsMap(record, fieldlist, getIdFieldName(), getModificationDateFieldName());
                String externalId = getExternalIdFieldName() != null ? JSONObjectHelper.optString(record, getExternalIdFieldName()) : null;

                // Do upsert if externalId specified
                if (externalId != null
                        // the following check is there for the case
                        // where the the external id field is the id field
                        // and the empty id field was populated by BatchSyncUpTarget using createLocalId()
                        && !externalId.equals(createLocalId(record))) {
                    return RestRequest.getRequestForUpsert(apiVersion, objectType, getExternalIdFieldName(), externalId, fields);
                }
                // Do a create otherwise
                else {
                    return RestRequest.getRequestForCreate(apiVersion, objectType, fields);
                }
            }
            else {
                fieldlist = this.updateFieldlist != null ? this.updateFieldlist : fieldlist;
                fields = buildFieldsMap(record, fieldlist, getIdFieldName(), getModificationDateFieldName());
                return RestRequest.getRequestForUpdate(apiVersion, objectType, id, fields);
            }
        }
    }


    protected boolean updateRecordInLocalStore(SyncManager syncManager, String soupName, JSONObject record, SyncState.MergeMode mergeMode, Map<String, String> refIdToServerId, CompositeSubResponse response, boolean isReRun) throws JSONException, IOException {

        boolean needReRun = false;
        final Integer statusCode = response != null ? response.httpStatusCode : -1;

        // Delete case
        if (isLocallyDeleted(record)) {
            if (isLocallyCreated(record)  // we didn't go to the sever
                    || RestResponse.isSuccess(statusCode) // or we successfully deleted on the server
                    || statusCode == HttpURLConnection.HTTP_NOT_FOUND) // or the record was already deleted on the server

            {
                deleteFromLocalStore(syncManager, soupName, record);
            }
            // Failure
            else {
                saveRecordToLocalStoreWithError(syncManager, soupName, record, response != null ? response.toString() : null);
            }
        }
        // Create / update case
        else {
            // Success case
            if (RestResponse.isSuccess(statusCode)) {
                // Plugging server id in id field
                CompositeRequestHelper.updateReferences(record, getIdFieldName(), refIdToServerId);

                // Clean and save
                cleanAndSaveInLocalStore(syncManager, soupName, record);
            }
            // Handling remotely deleted records
            else if (statusCode == HttpURLConnection.HTTP_NOT_FOUND
                    && mergeMode == SyncState.MergeMode.OVERWRITE // Record needs to be recreated
                    && !isReRun) {
                record.put(LOCAL, true);
                record.put(LOCALLY_CREATED, true);
                needReRun = true;
            }
            // Failure
            else {
                saveRecordToLocalStoreWithError(syncManager, soupName, record, response != null ? response.toString() : null);
            }
        }
        return needReRun;
    }

    // Create a local id (based on the internal soup entry id)
    private String createLocalId(JSONObject record) throws JSONException {
        return "local_" + record.getLong(SmartStore.SOUP_ENTRY_ID);
    }
}