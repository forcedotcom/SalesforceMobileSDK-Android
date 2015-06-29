/*
 * Copyright (c) 2014-2015, salesforce.com, inc.
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Set;

/**
 * Target for sync up:
 * - what records to upload to server
 * - how to upload those records
 */
public class SyncUpTarget extends SyncTarget {

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
     * Construct SyncDownTarget
     */
    public SyncUpTarget() {
        super();
    }

    /**
     * Construct SyncUpTarget from json
     * @param target
     * @throws JSONException
     */
    public SyncUpTarget(JSONObject target) throws JSONException {
        super(target);
    }

    /**
     * Save locally created record back to server
     * @param syncManager
     * @param objectType
     * @param fields
     * @return server record id or null if creation failed
     * @throws JSONException
     * @throws IOException
     */
    public String createOnServer(SyncManager syncManager, String objectType, Map<String, Object> fields) throws JSONException, IOException {
        RestRequest request = RestRequest.getRequestForCreate(syncManager.apiVersion, objectType, fields);
        RestResponse response = syncManager.sendSyncWithSmartSyncUserAgent(request);

        return response.isSuccess()
                ? response.asJSONObject().getString(Constants.LID)
                : null;
    }

    /**
     * Delete locally deleted record from server
     * @param syncManager
     * @param objectType
     * @param objectId
     * @return true if successful
     * @throws JSONException
     * @throws IOException
     */
    public int deleteOnServer(SyncManager syncManager, String objectType, String objectId) throws JSONException, IOException {
        RestRequest request = RestRequest.getRequestForDelete(syncManager.apiVersion, objectType, objectId);
        RestResponse response = syncManager.sendSyncWithSmartSyncUserAgent(request);

        return response.getStatusCode();
    }

    /**
     * Save locally updated record back to server
     * @param syncManager
     * @param objectType
     * @param objectId
     * @param fields
     * @return true if successful
     * @throws JSONException
     * @throws IOException
     */
    public int updateOnServer(SyncManager syncManager, String objectType, String objectId, Map<String, Object> fields) throws JSONException, IOException {
        RestRequest request = RestRequest.getRequestForUpdate(syncManager.apiVersion, objectType, objectId, fields);
        RestResponse response = syncManager.sendSyncWithSmartSyncUserAgent(request);

        return response.getStatusCode();
    }

    /**
     * Fetch last modified date for a given record
     * @param syncManager
     * @param objectType
     * @param objectId
     * @return
     * @throws JSONException
     * @throws IOException
     */
    public String fetchLastModifiedDate(SyncManager syncManager, String objectType, String objectId) throws JSONException, IOException {
        final String query = SOQLBuilder.getInstanceWithFields(Constants.LAST_MODIFIED_DATE)
                .from(objectType)
                .where(Constants.ID + " = '" + objectId + "'")
                .build();

        RestResponse lastModResponse = syncManager.sendSyncWithSmartSyncUserAgent(RestRequest.getRequestForQuery(syncManager.apiVersion, query));
        JSONArray records = lastModResponse.asJSONObject().optJSONArray(Constants.RECORDS);
        return records != null && records.length() > 0 ? records.optJSONObject(0).optString(Constants.LAST_MODIFIED_DATE) : null;
    }

    /**
     * Return ids of records to sync up
     * @param syncManager
     * @param soupName
     * @return
     */
    public Set<String> getIdsOfRecordsToSyncUp(SyncManager syncManager, String soupName) throws JSONException {
        return syncManager.getDirtyRecordIds(soupName, SmartStore.SOUP_ENTRY_ID);
    }
}