/*
 * Copyright (c) 2017-present, salesforce.com, inc.
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

import android.util.Log;

import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartsync.manager.SyncManager;
import com.salesforce.androidsdk.smartsync.target.ParentChildrenSyncTargetHelper.RelationshipType;
import com.salesforce.androidsdk.smartsync.util.ChildrenInfo;
import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.androidsdk.smartsync.util.ParentInfo;
import com.salesforce.androidsdk.smartsync.util.SyncState;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Target for sync that uploads parent with children records
 */
public class ParentChildrenSyncUpTarget extends SyncUpTarget {
    private static final String TAG = "Parent..SyncUpTarget";

    public static final String CHILDREN_CREATE_FIELDLIST = "childrenCreateFieldlist";
    public static final String CHILDREN_UPDATE_FIELDLIST = "childrenUpdateFieldlist";
    public static final String REFERENCE_ID = "referenceId";
    public static final String COMPOSITE_RESPONSE = "compositeResponse";
    public static final String BODY = "body";
    public static final String HTTP_STATUS_CODE = "httpStatusCode";

    private ParentInfo parentInfo;
    private ChildrenInfo childrenInfo;
    private List<String> childrenCreateFieldlist;
    private List<String> childrenUpdateFieldlist;
    private RelationshipType relationshipType;

    public ParentChildrenSyncUpTarget(JSONObject target) throws JSONException {
        this(
                new ParentInfo(target.getJSONObject(ParentChildrenSyncTargetHelper.PARENT)),
                JSONObjectHelper.<String>toList(target.optJSONArray(CREATE_FIELDLIST)),
                JSONObjectHelper.<String>toList(target.optJSONArray(UPDATE_FIELDLIST)),

                new ChildrenInfo(target.getJSONObject(ParentChildrenSyncTargetHelper.CHILDREN)),
                JSONObjectHelper.<String>toList(target.optJSONArray(CHILDREN_CREATE_FIELDLIST)),
                JSONObjectHelper.<String>toList(target.optJSONArray(CHILDREN_UPDATE_FIELDLIST)),

                RelationshipType.valueOf(target.getString(ParentChildrenSyncTargetHelper.RELATIONSHIP_TYPE))
        );
    }

    public ParentChildrenSyncUpTarget(ParentInfo parentInfo,
                                      List<String> parentCreateFieldlist,
                                      List<String> parentUpdateFieldlist,
                                      ChildrenInfo childrenInfo,
                                      List<String> childrenCreateFieldlist,
                                      List<String> childrenUpdateFieldlist,
                                      RelationshipType relationshipType) {

        super(parentCreateFieldlist, parentUpdateFieldlist);
        this.parentInfo = parentInfo;
        this.childrenInfo = childrenInfo;
        this.childrenCreateFieldlist = childrenCreateFieldlist;
        this.childrenUpdateFieldlist = childrenUpdateFieldlist;
        this.relationshipType = relationshipType;
    }

    @Override
    public JSONObject asJSON() throws JSONException {
        JSONObject target = super.asJSON();
        target.put(ParentChildrenSyncTargetHelper.PARENT, parentInfo.asJSON());
        target.put(ParentChildrenSyncTargetHelper.CHILDREN, childrenInfo.asJSON());
        target.put(CHILDREN_CREATE_FIELDLIST, new JSONArray(childrenCreateFieldlist));
        target.put(CHILDREN_UPDATE_FIELDLIST, new JSONArray(childrenUpdateFieldlist));
        target.put(ParentChildrenSyncTargetHelper.RELATIONSHIP_TYPE, relationshipType.name());
        return target;
    }

    @Override
    protected String getDirtyRecordIdsSql(String soupName, String idField) {
        return ParentChildrenSyncTargetHelper.getDirtyRecordIdsSql(soupName, idField, childrenInfo);
    }

    @Override
    public boolean createOnServer(SyncManager syncManager, JSONObject record, List<String> fieldlist, String soupName, SyncState.MergeMode mergeMode) throws JSONException, IOException {
        // updateOnServer handle all cases
        return updateOnServer(syncManager, record, fieldlist, soupName, mergeMode);
    }

    @Override
    public boolean deleteOnServer(SyncManager syncManager, JSONObject record, String soupName, SyncState.MergeMode mergeMode) throws JSONException, IOException {
        // updateOnServer handle all cases
        // the field list does not matter because the parent record is getting deleted
        return updateOnServer(syncManager, record, new ArrayList<String>(), soupName, mergeMode);
    }

    @Override
    public boolean updateOnServer(SyncManager syncManager, JSONObject record, List<String> fieldlist, String soupName, SyncState.MergeMode mergeMode) throws JSONException, IOException {
        // Preparing requests for parent
        LinkedHashMap<String, RestRequest> refIdToRequests = new LinkedHashMap<>();
        String parentId = record.getString(getIdFieldName());
        RestRequest parentRequest = buildRequestForRecord(syncManager.apiVersion,
                record,
                createFieldlist == null ? fieldlist : createFieldlist,
                updateFieldlist == null ? fieldlist : updateFieldlist,
                parentInfo,
                null);

        // Parent request goes first unless it's a delete
        if (parentRequest != null && !isLocallyDeleted(record)) refIdToRequests.put(parentId, parentRequest);

        // Getting children
        JSONArray children = ParentChildrenSyncTargetHelper.getChildrenFromLocalStore(
                syncManager.getSmartStore(),
                parentInfo.soupName,
                record,
                childrenInfo);

        // Preparing requests for children
        for (int i=0; i<children.length(); i++) {
            JSONObject childRecord = children.getJSONObject(i);
            String childId = childRecord.getString(childrenInfo.idFieldName);
            RestRequest childRequest = buildRequestForRecord(syncManager.apiVersion,
                    childRecord,
                    childrenCreateFieldlist,
                    childrenUpdateFieldlist,
                    childrenInfo,
                    isLocallyDeleted(record) ? null : parentId);
            if (childRequest != null) refIdToRequests.put(childId, childRequest);
        }

        // Parent request goes last when it's a delete
        if (parentRequest != null && isLocallyDeleted(record)) refIdToRequests.put(parentId, parentRequest);

        // Building composite request
        RestRequest request = RestRequest.getCompositeRequest(syncManager.apiVersion, true, refIdToRequests);

        // Sending request
        RestResponse response = syncManager.sendSyncWithSmartSyncUserAgent(request);

        // Build refId to serverId map
        Map<String, String> refIdToServerId = parseIdsFromResponse(response);
        Map<String, Integer> refIdToHttpStatusCode = parseStatusCodesFromResponse(response);

        // Update parent in local store
        updateRecordInLocalStore(syncManager, record, soupName, getIdFieldName(), null, refIdToServerId, refIdToHttpStatusCode);

        // Update children local store
        for (int i = 0; i < children.length(); i++) {
            JSONObject childRecord = children.getJSONObject(i);
            updateRecordInLocalStore(syncManager, childRecord, childrenInfo.soupName, childrenInfo.idFieldName, childrenInfo.parentIdFieldName, refIdToServerId, refIdToHttpStatusCode);
        }

        // Did all the requests go through
        boolean success = true;
        for (int statusCode : refIdToHttpStatusCode.values()) {
            if (!RestResponse.isSuccess(statusCode)) {
                success = false;
                break;
            }
        }

        return success;
    }

    /**
     *
     * @param syncManager
     * @param record
     * @param soupName
     * @param idFieldName               Field containing server id of record
     * @param parentIdFieldName         Field containing server id of parent record (only in children)
     * @param refIdToServerId
     * @param refIdToHttpStatusCode
     * @throws JSONException
     */
    protected void updateRecordInLocalStore(SyncManager syncManager, JSONObject record, String soupName, String idFieldName, String parentIdFieldName, Map<String, String> refIdToServerId, Map<String, Integer> refIdToHttpStatusCode) throws JSONException {
        if (isLocallyDeleted(record)) {
            if (isLocallyCreated(record) // we didn't go to the sever
                || RestResponse.isSuccess(refIdToHttpStatusCode.get(record.getString(idFieldName)))) // or we successfully deleted on the server
            {
                deleteFromLocalStore(syncManager, soupName, record);
            }
            // Otherwise leave record alone
        }
        else {
            // Replace local id by server id
            updateReferences(record, idFieldName, refIdToServerId);
            // Replace parent local id by server id (in children)
            if (parentIdFieldName != null) updateReferences(record, parentIdFieldName, refIdToServerId);
            // Clean and save
            cleanAndSaveInLocalStore(syncManager, soupName, record);
        }
    }

    protected RestRequest buildRequestForRecord(String apiVersion,
                                                     JSONObject record,
                                                     List<String> createFieldlist,
                                                     List<String> updateFieldlist,
                                                     ParentInfo info,
                                                     String parentId) throws IOException, JSONException {

        String id = record.getString(info.idFieldName);

        if (super.isLocallyDeleted(record) && !super.isLocallyCreated(record)) {
            return RestRequest.getRequestForDelete(apiVersion,
                            info.sobjectType,
                            id);
        }
        else if (super.isLocallyCreated(record)) {
            Map<String, Object> fields = buildFieldsMap(record, createFieldlist, info.idFieldName, info.modificationDateFieldName);
            if (info instanceof ChildrenInfo && parentId != null) {
                fields.put(((ChildrenInfo) info).parentIdFieldName, String.format("@{%s.%s}", parentId, Constants.LID));
            }
            return RestRequest.getRequestForCreate(apiVersion,
                            info.sobjectType,
                            fields);
        }
        else if (super.isLocallyUpdated(record)) {
            Map<String, Object> fields = buildFieldsMap(record, updateFieldlist, info.idFieldName, info.modificationDateFieldName);
            return RestRequest.getRequestForUpdate(apiVersion,
                            info.sobjectType,
                            id,
                            fields);
        }
        else {
            return null;
        }
    }

    /**
     * Return ref id to server id map if successful or null otherwise
     */
    protected Map<String, String> parseIdsFromResponse(RestResponse response) throws JSONException, IOException {
        if (response.isSuccess()) {
            Map<String, String> refIdtoId = new HashMap<>();
            JSONArray results = response.asJSONObject().getJSONArray(COMPOSITE_RESPONSE);
            for (int i=0; i<results.length(); i++) {
                JSONObject result = results.getJSONObject(i);
                if (result.getInt(HTTP_STATUS_CODE) == HttpURLConnection.HTTP_CREATED) {
                    String refId = result.getString(REFERENCE_ID);
                    String serverId = result.getJSONObject(BODY).getString(Constants.LID);
                    refIdtoId.put(refId, serverId);
                }
            }
            return refIdtoId;
        }
        else {
            Log.e(TAG, "parseIdsFromResponse failed:" + response.asString());
            return null;
        }
    }

    /**
     * Return ref id to http status code map if successful or null otherwise
     */
    protected Map<String, Integer> parseStatusCodesFromResponse(RestResponse response) throws JSONException, IOException {
        if (response.isSuccess()) {
            Map<String, Integer> refIdToStatusCode = new HashMap<>();
            JSONArray results = response.asJSONObject().getJSONArray(COMPOSITE_RESPONSE);
            for (int i=0; i<results.length(); i++) {
                JSONObject result = results.getJSONObject(i);
                String refId = result.getString(REFERENCE_ID);
                int httpStatusCode = result.getInt(HTTP_STATUS_CODE);
                refIdToStatusCode.put(refId, httpStatusCode);
            }
            return refIdToStatusCode;
        }
        else {
            Log.e(TAG, "parseStatusCodesFromResponse failed:" + response.asString());
            return null;
        }
    }


    protected void updateReferences(JSONObject record, String fieldWithRefId, Map<String, String> refIdToServerId) throws JSONException {
        String refId = record.getString(fieldWithRefId);
        if (refIdToServerId.containsKey(refId)) {
            record.put(fieldWithRefId, refIdToServerId.get(refId));
        }
    }

    @Override
    public String fetchLastModifiedDate(SyncManager syncManager, JSONObject record) {
        // TBD
        return super.fetchLastModifiedDate(syncManager, record);
    }

    @Override
    public void deleteFromLocalStore(SyncManager syncManager, String soupName, JSONObject record) throws JSONException {
        if (relationshipType == RelationshipType.MASTER_DETAIL) {
            ParentChildrenSyncTargetHelper.deleteChildrenFromLocalStore(syncManager.getSmartStore(),
                    soupName, new String[]{record.getString(SmartStore.SOUP_ENTRY_ID)}, SmartStore.SOUP_ENTRY_ID, childrenInfo);
        }
        super.deleteFromLocalStore(syncManager, soupName, record);
    }
}
