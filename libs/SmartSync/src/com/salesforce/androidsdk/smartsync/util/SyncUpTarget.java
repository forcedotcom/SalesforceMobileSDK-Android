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
package com.salesforce.androidsdk.smartsync.util;

import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartsync.manager.SyncManager;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.HashMap;
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
    public static final String CREATE_FIELDLIST = "createFieldlist";
    public static final String UPDATE_FIELDLIST = "updateFieldlist";

    // Fields
    private List<String> createFieldlist;
    private List<String> updateFieldlist;

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

        // Get values
        Map<String,Object> fields = new HashMap<>();
        for (String fieldName : fieldlist) {
            if (!fieldName.equals(getIdFieldName()) && !fieldName.equals(SyncUpTarget.MODIFICATION_DATE_FIELD_NAME)) {
                fields.put(fieldName, SmartStore.project(record, fieldName));
            }
        }

        return createOnServer(syncManager, objectType, fields);
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
    public String createOnServer(SyncManager syncManager, String objectType, Map<String, Object> fields) throws IOException, JSONException {
        RestRequest request = RestRequest.getRequestForCreate(syncManager.apiVersion, objectType, fields);
        RestResponse response = syncManager.sendSyncWithSmartSyncUserAgent(request);

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
    public int deleteOnServer(SyncManager syncManager, String objectType, String objectId) throws IOException {
        RestRequest request = RestRequest.getRequestForDelete(syncManager.apiVersion, objectType, objectId);
        RestResponse response = syncManager.sendSyncWithSmartSyncUserAgent(request);

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

        // Get values
        Map<String,Object> fields = new HashMap<>();
        for (String fieldName : fieldlist) {
            if (!fieldName.equals(getIdFieldName()) && !fieldName.equals(SyncUpTarget.MODIFICATION_DATE_FIELD_NAME)) {
                fields.put(fieldName, SmartStore.project(record, fieldName));
            }
        }

        return updateOnServer(syncManager, objectType, objectId, fields);
    }

    /**
     * Save locally updated record back to server - original signature
     * Called by updateOnServer(SyncManager syncManager, JSONObject record, List<String> fieldlist)
     * @param syncManager
     * @param objectType
     * @param objectId
     * @param fields
     * @return true if successful
     * @throws IOException
     */
    public int updateOnServer(SyncManager syncManager, String objectType, String objectId, Map<String, Object> fields) throws IOException {
        RestRequest request = RestRequest.getRequestForUpdate(syncManager.apiVersion, objectType, objectId, fields);
        RestResponse response = syncManager.sendSyncWithSmartSyncUserAgent(request);

        return response.getStatusCode();
    }

    /**
     * Fetch last modified date for a given record
     * @param syncManager
     * @param record
     * @return
     */
    public String fetchLastModifiedDate(SyncManager syncManager, JSONObject record) {
        try {
            final String objectType = (String) SmartStore.project(record, Constants.SOBJECT_TYPE);
            final String objectId = record.getString(getIdFieldName());

            final String query = SOQLBuilder.getInstanceWithFields(getModificationDateFieldName())
                    .from(objectType)
                    .where(getIdFieldName() + " = '" + objectId + "'")
                    .build();

            RestResponse lastModResponse = syncManager.sendSyncWithSmartSyncUserAgent(RestRequest.getRequestForQuery(syncManager.apiVersion, query));
            JSONArray records = lastModResponse.asJSONObject().optJSONArray(Constants.RECORDS);
            return records != null && records.length() > 0 ? records.optJSONObject(0).optString(getModificationDateFieldName()) : null;
        }
        catch (Exception e) {
            // Caller expects null to be returned if the last modified date could not be fetched
            return null;
        }
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
}