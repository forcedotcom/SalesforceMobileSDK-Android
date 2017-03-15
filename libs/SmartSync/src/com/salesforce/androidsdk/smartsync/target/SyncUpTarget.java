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
package com.salesforce.androidsdk.smartsync.target;

import android.util.Pair;

import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartsync.manager.SyncManager;
import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.androidsdk.smartsync.util.SyncManagerLogger;
import com.salesforce.androidsdk.smartsync.util.SyncState;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.HttpURLConnection;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Target for sync up:
 * - what records to upload to server
 * - how to upload those records
 */
public class SyncUpTarget extends SyncTarget {

    // Constants
    public static final String TAG = "SyncUpTarget";
    public static final String CREATE_FIELDLIST = "createFieldlist";
    public static final String UPDATE_FIELDLIST = "updateFieldlist";
    public static final String COMPOSITE_RESPONSE = "compositeResponse";
    public static final String REFERENCE_ID = "referenceId";
    public static final String BODY = "body";
    public static final String HTTP_STATUS_CODE = "httpStatusCode";
    public static final String HAS_ERRORS = "hasErrors";
    public static final String REF_UPDATE = "refUpdate";
    public static final String REF_RETRIEVE = "refRetrieve";

    // For SyncManagerLogger
    public static final String UPDATE_ON_SERVER_RECORD_REMOTELY_DELETED_RE_CREATING = "update on server:record remotely deleted - re-creating";
    public static final String UPDATE_ON_SERVER_RECORD_REMOTELY_DELETED_LEAVING_THINGS_UNCHANGED = "update on server:record remotely deleted - leaving things unchanged";
    public static final String UPDATE_ON_SERVER_RECORD_REMOTELY_UPDATED_LEAVING_THINGS_UNCHANGED = "update on server:record remotely updated - leaving things unchanged";
    public static final String UPDATE_ON_SERVER_GOT_UNEXPECTED_STATUS = "update on server: got unexpected status:";
    public static final String CREATE_ON_SERVER_SERVER_CREATE_SUCCEEDED_SAVING_LOCALLY = "create on server:server create succeeded - saving locally";
    public static final String DELETE_ON_SERVER_NO_NEED_TO_DELETE_ON_SERVER_LOCALLY_CREATED_DELETING_LOCALLY = "delete on server:no need to delete on server (locally created) - deleting locally";
    public static final String DELETE_ON_SERVER_SERVER_DELETE_SUCCEEDED_DELETING_LOCALLY = "delete on server:server delete succeeded - deleting locally";
    public static final String DELETE_ON_SERVER_FAILED = "delete on server failed:";
    public static final String UPDATE_ON_SERVER_SERVER_UPDATE_SUCCEEDED_SAVING_LOCALLY = "update on server:server update succeeded - saving locally";
    public static final String CREATE_ON_SERVER_FAILED = "create on server failed:";

    // Fields
    protected List<String> createFieldlist;
    protected List<String> updateFieldlist;

    /**
     * Build SyncUpTarget from json
     *
     * @param target as json
     * @return
     * @throws JSONException
     */
    @SuppressWarnings("unchecked")
    public static SyncUpTarget fromJSON(JSONObject target) throws JSONException {
        // Default sync up target
        if (target == null || target.isNull(ANDROID_IMPL) || SyncUpTarget.class.getName().equals(target.getString(ANDROID_IMPL))) {
            return new SyncUpTarget(target);
        }

        // Non default sync up target
        try {
            Class<? extends SyncUpTarget> implClass = (Class<? extends SyncUpTarget>) Class.forName(target.getString(ANDROID_IMPL));
            Constructor<? extends SyncUpTarget> constructor = implClass.getConstructor(JSONObject.class);
            return constructor.newInstance(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Construct SyncUpTarget
     */
    public SyncUpTarget() {
        this(null, null);
    }

    /**
     * Construct SyncUpTarget
     */
    public SyncUpTarget(List<String> createFieldlist, List<String> updateFieldlist) {
        super();
        this.createFieldlist = createFieldlist;
        this.updateFieldlist = updateFieldlist;
    }

    /**
     * Construct SyncUpTarget from json
     * @param target
     * @throws JSONException
     */
    public SyncUpTarget(JSONObject target) throws JSONException {
        super(target);
        this.createFieldlist = JSONObjectHelper.toList(target.optJSONArray(CREATE_FIELDLIST));
        this.updateFieldlist = JSONObjectHelper.toList(target.optJSONArray(UPDATE_FIELDLIST));
    }

    /**
     * @return json representation of target
     * @throws JSONException
     */
    public JSONObject asJSON() throws JSONException {
        JSONObject target = super.asJSON();
        if (createFieldlist != null) target.put(CREATE_FIELDLIST, new JSONArray(createFieldlist));
        if (updateFieldlist != null) target.put(UPDATE_FIELDLIST, new JSONArray(updateFieldlist));
        return target;
    }

    /**
     * Save locally created record back to server and update local store afterwards
     * @param syncManager
     * @param record
     * @param fieldlist fields to sync up (this.createFieldlist will be used instead if provided)
     * @param soupName
     * @param mergeMode
     * @return true if successful
     * @throws JSONException
     * @throws IOException
     */
    public void createOnServer(SyncManager syncManager, JSONObject record, List<String> fieldlist, String soupName, SyncState.MergeMode mergeMode) throws JSONException, IOException {
        fieldlist = this.createFieldlist != null ? this.createFieldlist : fieldlist;
        final String objectType = (String) SmartStore.project(record, Constants.SOBJECT_TYPE);

        // Get values
        Map<String, Object> fields = buildFieldsMap(record, fieldlist, getIdFieldName(), getModificationDateFieldName());

        // Create on server
        String serverId = createOnServer(syncManager, objectType, fields);
        syncManager.getLogger().d(this, CREATE_ON_SERVER_SERVER_CREATE_SUCCEEDED_SAVING_LOCALLY, record);

        // Update local store
        record.put(getIdFieldName(), serverId);
        cleanAndSaveInLocalStore(syncManager, soupName, record);
    }

    /**
     * Build map with the values for the fields in fieldlist from record
     * @param record
     * @param fieldlist
     * @param idFieldName
     * @param modificationDateFieldName
     * @return
     */
    protected Map<String, Object> buildFieldsMap(JSONObject record, List<String> fieldlist, String idFieldName, String modificationDateFieldName) {
        Map<String,Object> fields = new HashMap<>();
        for (String fieldName : fieldlist) {
            if (!fieldName.equals(idFieldName) && !fieldName.equals(modificationDateFieldName)) {
                fields.put(fieldName, SmartStore.project(record, fieldName));
            }
        }
        return fields;
    }

    /**
     * Save locally created record back to server (original method)
     * Called by createOnServer(SyncManager syncManager, JSONObject record, List<String> fieldlist)
     * @param syncManager
     * @param objectType
     * @param fields
     * @return server record id
     * @throws IOException
     * @throws JSONException
     */
    protected String createOnServer(SyncManager syncManager, String objectType, Map<String, Object> fields) throws IOException, JSONException {
        RestRequest request = RestRequest.getRequestForCreate(syncManager.apiVersion, objectType, fields);
        RestResponse response = syncManager.sendSyncWithSmartSyncUserAgent(request);

        if (response.isSuccess()) {
            return response.asJSONObject().getString(Constants.LID);
        }
        else {
            throw new SyncManager.SmartSyncException(CREATE_ON_SERVER_FAILED + response.asString());
        }
    }

    /**
     * Delete locally deleted record from server and then from local store
     * @param syncManager
     * @param record
     * @param soupName
     * @param mergeMode
     * @throws JSONException
     * @throws IOException
     */
    public void deleteOnServer(SyncManager syncManager, JSONObject record, String soupName, SyncState.MergeMode mergeMode) throws JSONException, IOException {
        final String objectType = (String) SmartStore.project(record, Constants.SOBJECT_TYPE);
        final String objectId = record.getString(getIdFieldName());

        // Compute if-unmodified-since date if applicable
        final Date ifUnmodifiedSinceDate = mergeMode == SyncState.MergeMode.LEAVE_IF_CHANGED
                ? getModificationDate(syncManager.getLogger(), getModificationDateFieldName(), record)
                : null;

        // Go to server if needed
        if (isLocallyCreated(record)) {
            syncManager.getLogger().d(this, DELETE_ON_SERVER_NO_NEED_TO_DELETE_ON_SERVER_LOCALLY_CREATED_DELETING_LOCALLY, record);
        }
        else {
            deleteOnServer(syncManager, objectType, objectId);
            syncManager.getLogger().d(this, DELETE_ON_SERVER_SERVER_DELETE_SUCCEEDED_DELETING_LOCALLY, record);
        }

        // Delete locally
        deleteFromLocalStore(syncManager, soupName, record);
    }

    /**
     * Delete locally deleted record from server - closest to original signature
     * Called by deleteOnServer(SyncManager syncManager, JSONObject record)
     * @param syncManager
     * @param objectType
     * @param objectId
     * @throws IOException
     */
    protected void deleteOnServer(SyncManager syncManager, String objectType, String objectId) throws IOException {
        RestRequest request = RestRequest.getRequestForDelete(syncManager.apiVersion, objectType, objectId);
        RestResponse response = syncManager.sendSyncWithSmartSyncUserAgent(request);

        if (response.isSuccess() || response.getStatusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
            return;
        }
        else {
            throw new SyncManager.SmartSyncException(DELETE_ON_SERVER_FAILED + response.asString());
        }
    }

    /**
     * Save locally updated record back to server and update local store
     * @param syncManager
     * @param record
     * @param fieldlist fields to sync up (this.updateFieldlist will be used instead if provided)
     * @param soupName
     * @param mergeMode to be used to handle case where record was remotely deleted
     * @throws JSONException
     * @throws IOException
     */
    public void updateOnServer(SyncManager syncManager, JSONObject record, List<String> fieldlist, String soupName, SyncState.MergeMode mergeMode) throws JSONException, IOException {
        final String objectType = (String) SmartStore.project(record, Constants.SOBJECT_TYPE);
        final String objectId = record.getString(getIdFieldName());

        // Get values
        Map<String,Object> fields = new HashMap<>();
        for (String fieldName : updateFieldlist != null ? updateFieldlist : fieldlist) {
            if (!fieldName.equals(getIdFieldName()) && !fieldName.equals(MODIFICATION_DATE_FIELD_NAME)) {
                fields.put(fieldName, SmartStore.project(record, fieldName));
            }
        }

        // Compute if-unmodified-since date if applicable
        final Date ifUnmodifiedSinceDate = mergeMode == SyncState.MergeMode.LEAVE_IF_CHANGED
                ? getModificationDate(syncManager.getLogger(), getModificationDateFieldName(), record)
                : null;

        // Go to server
        Pair<Integer, String> result = updateOnServer(syncManager, objectType, objectId, fields, ifUnmodifiedSinceDate);
        int statusCode = result.first;
        String updatedModificationDate = result.second;

        syncManager.getLogger().d(this, "updateOnServer:", String.format("status:%d,updatedDate:%s", result.first, result.second));

        if (RestResponse.isSuccess(statusCode)) {
            syncManager.getLogger().d(this, UPDATE_ON_SERVER_SERVER_UPDATE_SUCCEEDED_SAVING_LOCALLY, record);
            if (updatedModificationDate != null) record.put(getModificationDateFieldName(), updatedModificationDate);
            cleanAndSaveInLocalStore(syncManager, soupName, record);
        }
        // Handling remotely deleted records
        else if (statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
            if (mergeMode == SyncState.MergeMode.OVERWRITE) {
                syncManager.getLogger().d(this, UPDATE_ON_SERVER_RECORD_REMOTELY_DELETED_RE_CREATING, record);
                createOnServer(syncManager, record, createFieldlist != null ? createFieldlist : fieldlist, soupName, mergeMode);
            }
            else {
                syncManager.getLogger().d(this, UPDATE_ON_SERVER_RECORD_REMOTELY_DELETED_LEAVING_THINGS_UNCHANGED, record);
            }
        }
        // Remote record was left alone because if recently changed
        else if (statusCode == HttpURLConnection.HTTP_PRECON_FAILED && mergeMode == SyncState.MergeMode.LEAVE_IF_CHANGED) {
            syncManager.getLogger().d(this, UPDATE_ON_SERVER_RECORD_REMOTELY_UPDATED_LEAVING_THINGS_UNCHANGED, record);
        }
        // Unexpected
        else {
            throw new SyncManager.SmartSyncException(UPDATE_ON_SERVER_GOT_UNEXPECTED_STATUS + statusCode);
        }
    }

    /**
     * Get record modification date if there is one
     *
     * @param logger
     * @param modificationDateFieldName
     * @param record
     * @return
     * @throws JSONException
     */
    protected Date getModificationDate(SyncManagerLogger logger, String modificationDateFieldName, JSONObject record) throws JSONException {
        if (modificationDateFieldName != null) {
            String modificationDate = JSONObjectHelper.optString(record, modificationDateFieldName, null);
            if (modificationDate != null) {
                try {
                    return Constants.TIMESTAMP_FORMAT.parse(modificationDate);
                } catch (ParseException e) {
                    logger.e(this, "getModificationDate: could not format modification date: ", e);
                }
            }
        }
        return null;
    }

    /**
     * Save locally updated record back to server - closest to original signature
     * Called by updateOnServer(SyncManager syncManager, JSONObject record, List<String> fieldlist)
     * @param syncManager
     * @param objectType
     * @param objectId
     * @param fields
     * @param ifUnmodifiedSinceDate
     * @return status code and (new) modified date
     * @throws IOException
     */
    protected Pair<Integer, String> updateOnServer(SyncManager syncManager, String objectType, String objectId, Map<String, Object> fields, Date ifUnmodifiedSinceDate) throws IOException, JSONException {
        LinkedHashMap<String, RestRequest> refIdToRequests = new LinkedHashMap<>();
        refIdToRequests.put("refUpdate", RestRequest.getRequestForUpdate(syncManager.apiVersion, objectType, objectId, fields, ifUnmodifiedSinceDate));
        if (getModificationDateFieldName() != null) {
            refIdToRequests.put("refRetrieve", RestRequest.getRequestForRetrieve(syncManager.apiVersion, objectType, objectId, Arrays.asList(new String[]{getIdFieldName(), getModificationDateFieldName()})));
        }
        Map<String, JSONObject> refIdToResponses = sendCompositeRequest(syncManager, refIdToRequests);

        int statusCode = refIdToResponses.get(REF_UPDATE).getInt(HTTP_STATUS_CODE);
        String updatedModificationDate = null;

        if (refIdToResponses.containsKey(REF_RETRIEVE) && RestResponse.isSuccess(refIdToResponses.get(REF_RETRIEVE).getInt(HTTP_STATUS_CODE))) {
            updatedModificationDate = refIdToResponses.get(REF_RETRIEVE).getJSONObject(BODY).getString(getModificationDateFieldName());
        }

        return new Pair<>(statusCode, updatedModificationDate);
    }

    protected Map<String, JSONObject> sendCompositeRequest(SyncManager syncManager, LinkedHashMap<String, RestRequest> refIdToRequests) throws JSONException, IOException {
        RestRequest compositeRequest = RestRequest.getCompositeRequest(syncManager.apiVersion, true, refIdToRequests);
        RestResponse compositeResponse = syncManager.sendSyncWithSmartSyncUserAgent(compositeRequest);

        if (!compositeResponse.isSuccess()) {
            throw new SyncManager.SmartSyncException("sendCompositeRequest:" + compositeResponse.toString());
        }

        JSONArray responses = compositeResponse.asJSONObject().getJSONArray(COMPOSITE_RESPONSE);
        Map<String, JSONObject> refIdToResponses = new HashMap<>();
        for (int i = 0; i < responses.length(); i++) {
            JSONObject response = responses.getJSONObject(i);
            refIdToResponses.put(response.getString(REFERENCE_ID), response);
        }
        return refIdToResponses;
    }

    /**
     * Return ids of records to sync up
     * @param syncManager
     * @param soupName
     * @return
     */
    public Set<String> getIdsOfRecordsToSyncUp(SyncManager syncManager, String soupName) throws JSONException {
        return getDirtyRecordIds(syncManager, soupName, SmartStore.SOUP_ENTRY_ID);
    }

    /**
     * Given a record, return true if it was locally created
     * @param record
     * @return
     * @throws JSONException
     */
    public boolean isLocallyCreated(JSONObject record) throws JSONException {
        return record.getBoolean(LOCALLY_CREATED);
    }

    /**
     * Given a record, return true if it was locally updated
     * @param record
     * @return
     * @throws JSONException
     */
    public boolean isLocallyUpdated(JSONObject record) throws JSONException {
        return record.getBoolean(LOCALLY_UPDATED);
    }

    /**
     * Given a record, return true if it was locally deleted
     * @param record
     * @return
     * @throws JSONException
     */
    public boolean isLocallyDeleted(JSONObject record) throws JSONException {
        return record.getBoolean(LOCALLY_DELETED);
    }

    /**
     * Given a record, return true if it was locally created/updated or deleted
     * @param record
     * @return
     * @throws JSONException
     */
    public boolean isDirty(JSONObject record) throws JSONException {
        return record.getBoolean(LOCAL);
    }

    /**
     * Get record from local store by storeId
     * @param syncManager
     * @param storeId
     * @throws  JSONException
     */
    public JSONObject getFromLocalStore(SyncManager syncManager, String soupName, String storeId) throws JSONException {
        return syncManager.getSmartStore().retrieve(soupName, Long.valueOf(storeId)).getJSONObject(0);
    }

    /**
     * Delete record from local store
     * @param syncManager
     * @param soupName
     * @param record
     */
    public void deleteFromLocalStore(SyncManager syncManager, String soupName, JSONObject record) throws JSONException {
        syncManager.getSmartStore().delete(soupName, record.getLong(SmartStore.SOUP_ENTRY_ID));
    }


}