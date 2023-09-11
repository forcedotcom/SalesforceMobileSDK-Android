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
package com.salesforce.androidsdk.mobilesync.target;

import com.salesforce.androidsdk.mobilesync.manager.SyncManager;
import com.salesforce.androidsdk.mobilesync.manager.SyncManager.MobileSyncException;
import com.salesforce.androidsdk.mobilesync.util.Constants;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 *   a) if calls getFromLocalStore to get the record itself
 *
 *   b) if merge mode is leave-if-changed, it calls isNewerThanServer, if that returns false, it goes to the next id
 *
 *   c) otherwise it does one of the following three operations:
 *      - calls deleteOnServer if isLocallyDeleted returns true for the record (unless it is also locally created, in which case it gets deleted locally right away)
 *        if successful or not found is returned, it calls deleteFromLocalStore to delete record locally
 *
 *      - calls createOnServer if isLocallyCreated returns true for the record
 *        if successful, it updates the id to be the id returned by the server and then calls cleanAndSaveInSmartstore to reset local flags and save the record locally
 *
 *      - calls updateOnServer if isLocallyUpdated returns true for the record
 *        if successful, it calls cleanAndSaveInSmartstore to reset local flags and save the record
 *        if not found and merge mode is overwrite, it calls createOnServer to recreate the record on the server
 *
 */
public class SyncUpTarget extends SyncTarget {

    // Constants
    public static final String TAG = "SyncUpTarget";
    public static final String CREATE_FIELDLIST = "createFieldlist";
    public static final String UPDATE_FIELDLIST = "updateFieldlist";
    public static final String EXTERNAL_ID_FIELD_NAME = "externalIdFieldName";

    // Fields
    protected List<String> createFieldlist;
    protected List<String> updateFieldlist;
    protected String externalIdFieldName;

    // Last sync error
    protected String lastError;

    /**
     * Build SyncUpTarget from json
     *
     * @param target as json
     * @return
     * @throws JSONException
     */
    @SuppressWarnings("unchecked")
    public static SyncUpTarget fromJSON(JSONObject target) throws JSONException {
        // Default sync up target (it's CollectionSyncUpTarget starting in Mobile SDK 10.1)
        if (target == null || target.isNull(ANDROID_IMPL)) {
            return new CollectionSyncUpTarget(target);
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
        this(createFieldlist, updateFieldlist, null, null, null);
    }

    /**
     * Construct SyncUpTarget
     */
    public SyncUpTarget(List<String> createFieldlist, List<String> updateFieldlist, String idFieldName, String modificationDateFieldName, String externalIdFieldName) {
        super(idFieldName, modificationDateFieldName);
        this.createFieldlist = createFieldlist;
        this.updateFieldlist = updateFieldlist;
        this.externalIdFieldName = externalIdFieldName;
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
        this.externalIdFieldName = JSONObjectHelper.optString(target, EXTERNAL_ID_FIELD_NAME);
    }

    /**
     * @return json representation of target
     * @throws JSONException
     */
    public JSONObject asJSON() throws JSONException {
        JSONObject target = super.asJSON();
        if (createFieldlist != null) target.put(CREATE_FIELDLIST, new JSONArray(createFieldlist));
        if (updateFieldlist != null) target.put(UPDATE_FIELDLIST, new JSONArray(updateFieldlist));
        if (externalIdFieldName != null) target.put(EXTERNAL_ID_FIELD_NAME, externalIdFieldName);
        return target;
    }

    /**
     * @return The field name of an external id field of the record.  Default to null.
     */
    public String getExternalIdFieldName() {
        return externalIdFieldName;
    }

    /**
     * Save record with last error if any
     * @param syncManager
     * @param soupName
     * @param record
     * @throws JSONException
     */
    public void saveRecordToLocalStoreWithLastError(SyncManager syncManager, String soupName, JSONObject record) throws JSONException {
        saveRecordToLocalStoreWithError(syncManager, soupName, record, lastError);
        lastError = null;
    }

    protected void saveRecordToLocalStoreWithError(SyncManager syncManager, String soupName, JSONObject record, String error) throws JSONException {
        if (error != null) {
            record.put(SyncTarget.LAST_ERROR, error);
            saveInLocalStore(syncManager, soupName, record);
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
    public String createOnServer(SyncManager syncManager, JSONObject record, List<String> fieldlist) throws JSONException, IOException {
        fieldlist = this.createFieldlist != null ? this.createFieldlist : fieldlist;
        final String objectType = (String) SmartStore.project(record, Constants.SOBJECT_TYPE);
        final Map<String,Object> fields = buildFieldsMap(record, fieldlist, getIdFieldName(), getModificationDateFieldName());
        final String externalId = externalIdFieldName != null ? JSONObjectHelper.optString(record, externalIdFieldName) : null;
        if (externalId != null
            // the following check is there for the case
            // where the the external id field is the id field
            // and the field is populated by a local id
            && !isLocalId(externalId)) {
            return upsertOnServer(syncManager, objectType, fields, externalId);
        } else {
            return createOnServer(syncManager, objectType, fields);
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
     */
    protected String createOnServer(SyncManager syncManager, String objectType, Map<String, Object> fields) throws IOException, JSONException {
        RestRequest request = RestRequest.getRequestForCreate(syncManager.apiVersion, objectType, fields);
        return sendCreateOrUpsertRequest(syncManager, request);
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
     */

    protected String upsertOnServer(SyncManager syncManager, String objectType, Map<String, Object> fields, String externalId) throws IOException, JSONException {
        RestRequest request = RestRequest.getRequestForUpsert(syncManager.apiVersion, objectType, externalIdFieldName, externalId, fields);
        return sendCreateOrUpsertRequest(syncManager, request);
    }

    /**
     * Send create or upsert request
     * @param syncManager
     * @param request
     * @return server record id or null if creation or upsert failed
     */
    protected String sendCreateOrUpsertRequest(SyncManager syncManager, RestRequest request) throws IOException, JSONException {
        RestResponse response = syncManager.sendSyncWithMobileSyncUserAgent(request);

        if (!response.isSuccess()) {
            lastError = response.asString();
        }

        return response.isSuccess()
                ? response.asJSONObject().getString(Constants.LID)
                : null;
    }

    /**
     * Delete locally deleted record from server
     * @param syncManager
     * @param record
     * @return server response status code
     * @throws JSONException
     * @throws IOException
     */
    public int deleteOnServer(SyncManager syncManager, JSONObject record) throws JSONException, IOException {
        final String objectType = (String) SmartStore.project(record, Constants.SOBJECT_TYPE);
        final String objectId = record.getString(getIdFieldName());
        return deleteOnServer(syncManager, objectType, objectId);
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
    protected int deleteOnServer(SyncManager syncManager, String objectType, String objectId) throws IOException {
        RestRequest request = RestRequest.getRequestForDelete(syncManager.apiVersion, objectType, objectId);
        RestResponse response = syncManager.sendSyncWithMobileSyncUserAgent(request);

        if (!response.isSuccess()) {
            lastError = response.asString();
        }

        return response.getStatusCode();
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
    public int updateOnServer(SyncManager syncManager, JSONObject record, List<String> fieldlist) throws JSONException, IOException {
        fieldlist = this.updateFieldlist != null ? this.updateFieldlist : fieldlist;
        final String objectType = (String) SmartStore.project(record, Constants.SOBJECT_TYPE);
        final String objectId = record.getString(getIdFieldName());
        final Map<String,Object> fields = buildFieldsMap(record, fieldlist, getIdFieldName(), getModificationDateFieldName());
        return updateOnServer(syncManager, objectType, objectId, fields);
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
     */
    protected int updateOnServer(SyncManager syncManager, String objectType, String objectId, Map<String, Object> fields) throws IOException {
        RestRequest request = RestRequest.getRequestForUpdate(syncManager.apiVersion, objectType, objectId, fields);
        RestResponse response = syncManager.sendSyncWithMobileSyncUserAgent(request);

        if (!response.isSuccess()) {
            lastError = response.asString();
        }

        return response.getStatusCode();
    }

    /**
     * Fetch last modified date for a given record
     * @param syncManager
     * @param record
     * @return
     */
    protected RecordModDate fetchLastModifiedDate(SyncManager syncManager, JSONObject record) throws JSONException, IOException {
        final String objectType = (String) SmartStore.project(record, Constants.SOBJECT_TYPE);
        final String objectId = record.getString(getIdFieldName());
        RestRequest lastModRequest = RestRequest.getRequestForRetrieve(syncManager.apiVersion, objectType, objectId, Arrays.asList(getModificationDateFieldName()));
        RestResponse lastModResponse = syncManager.sendSyncWithMobileSyncUserAgent(lastModRequest);
        return new RecordModDate(
                lastModResponse.isSuccess() ? lastModResponse.asJSONObject().getString(getModificationDateFieldName()) : null,
                lastModResponse.getStatusCode() == HttpURLConnection.HTTP_NOT_FOUND
        );
    }

    /**
     * Same as fetchLastModifiedDate but operating over a list of records (expected to be of the same sobject type)
     *
     * @param syncManager
     * @param records
     * @return
     */
    protected Map<String, RecordModDate> fetchLastModifiedDates(SyncManager syncManager, List<JSONObject> records) throws JSONException, IOException {
        Map<String, RecordModDate> recordIdToLastModifiedDate = new HashMap<>();

        int totalSize = records.size();
        if (totalSize > 0) {
            String objectType = (String) SmartStore.project(records.get(0), Constants.SOBJECT_TYPE);
            List<String> batchStoreIds = new ArrayList<>();
            List<String> batchServerIds = new ArrayList<>();

            for (int i = 0; i < totalSize; i++) {
                JSONObject record = records.get(i);
                if (!objectType.equals(SmartStore.project(record, Constants.SOBJECT_TYPE))) {
                    throw new MobileSyncException("All records should have same sobject type");
                }

                batchStoreIds.add(record.getString(SmartStore.SOUP_ENTRY_ID));
                batchServerIds.add(record.getString(getIdFieldName()));

                // Process batch if max batch size reached or at the end of records
                if (batchServerIds.size() == RestRequest.MAX_COLLECTION_RETRIEVE_SIZE || i == totalSize - 1) {
                    RestRequest request = RestRequest.getRequestForCollectionRetrieve(syncManager.apiVersion, objectType, batchServerIds, Arrays.asList(getModificationDateFieldName()));
                    RestResponse response = syncManager.sendSyncWithMobileSyncUserAgent(request);
                    JSONArray responseAsArray = response.asJSONArray();
                    for (int j = 0; j < responseAsArray.length(); j++) {
                        String storeId = batchStoreIds.get(j);
                        JSONObject lastModResponse = responseAsArray.isNull(j) ? null : responseAsArray.getJSONObject(j);
                        RecordModDate serverModDate = new RecordModDate(
                            lastModResponse != null ? lastModResponse.getString(getModificationDateFieldName()) : null,
                            lastModResponse == null
                        );
                        recordIdToLastModifiedDate.put(storeId, serverModDate);
                    }

                    batchServerIds.clear();
                    batchStoreIds.clear();
                }
            }
        }

        return recordIdToLastModifiedDate;
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
    public boolean isNewerThanServer(SyncManager syncManager, JSONObject record) throws JSONException, IOException {
        if (isLocallyCreated(record)) {
            return true;
        }
        final RecordModDate localModDate = new RecordModDate(
                JSONObjectHelper.optString(record, getModificationDateFieldName()),
                isLocallyDeleted(record)
        );
        final RecordModDate remoteModDate = fetchLastModifiedDate(syncManager, record);
        return isNewerThanServer(localModDate, remoteModDate);
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
    public Map<String, Boolean> areNewerThanServer(SyncManager syncManager, List<JSONObject> records) throws JSONException, IOException {
        Map<String, Boolean> storeIdToNewerThanServer = new HashMap<>();

        for (JSONObject record : records) {
            String storeId = record.getString(SmartStore.SOUP_ENTRY_ID);
            storeIdToNewerThanServer.put(storeId, isNewerThanServer(syncManager, record));
        }

        return storeIdToNewerThanServer;
    }

    /**
     * Return true if local mod date is greater than remote mod date
     * NB: also return true if both were deleted or if local mod date is missing
     *
     * @param localModDate
     * @param remoteModDate
     * @return
     */
    protected boolean isNewerThanServer(RecordModDate localModDate, RecordModDate remoteModDate) {
        return (localModDate.timestamp != null && remoteModDate.timestamp != null
                && localModDate.timestamp.compareTo(remoteModDate.timestamp) >= 0) // we got a local and remote mod date and the local one is greater
                || (localModDate.isDeleted && remoteModDate.isDeleted)                 // or we have a local delete and a remote delete
                || localModDate.timestamp == null;
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
                Object fieldValue = SmartStore.projectReturningNULLObject(record, fieldName);
                if (fieldValue != null) {
                    fields.put(fieldName, fieldValue == JSONObject.NULL ? null : fieldValue);
                }
            }
        }
        return fields;
    }

    /**
     * Helper class used by isNewerThanServer
     */
    protected static class RecordModDate {

        public final String timestamp;   // time stamp in the Constants.TIMESTAMP_FORMAT format - can be null if unknown
        public final boolean isDeleted;  // true if the record was deleted

        public RecordModDate(String timestamp, boolean isDeleted) {
            this.timestamp = timestamp;
            this.isDeleted = isDeleted;
        }
    }
}
