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
import com.salesforce.androidsdk.smartsync.util.SOQLBuilder;
import com.salesforce.androidsdk.smartsync.util.SyncState;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
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

    // Constants
    private static final String TAG = "Parent..SyncUpTarget";
    public static final String CHILDREN_CREATE_FIELDLIST = "childrenCreateFieldlist";
    public static final String CHILDREN_UPDATE_FIELDLIST = "childrenUpdateFieldlist";
    public static final String REF_TIME_STAMPS = "refTimeStamps";

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
        boolean isCreate = isLocallyCreated(record);
        boolean isDelete = isLocallyDeleted(record);

        Log.i("--updateOnServer-->", record.toString(2));

        // Preparing requests for parent
        LinkedHashMap<String, RestRequest> refIdToRequests = new LinkedHashMap<>();
        String parentId = record.getString(getIdFieldName());
        RestRequest parentRequest = buildRequestForRecord(syncManager.apiVersion,
                record,
                createFieldlist == null ? fieldlist : createFieldlist,
                updateFieldlist == null ? fieldlist : updateFieldlist,
                parentInfo,
                null,
                mergeMode);

        // Parent request goes first unless it's a delete
        if (parentRequest != null && !isDelete)
            refIdToRequests.put(parentId, parentRequest);

        // Getting children
        JSONArray children = ParentChildrenSyncTargetHelper.getChildrenFromLocalStore(
                syncManager.getSmartStore(),
                parentInfo.soupName,
                record,
                childrenInfo);

        // Preparing requests for children
        for (int i = 0; i < children.length(); i++) {
            JSONObject childRecord = children.getJSONObject(i);
            String childId = childRecord.getString(childrenInfo.idFieldName);
            RestRequest childRequest = buildRequestForRecord(syncManager.apiVersion,
                    childRecord,
                    childrenCreateFieldlist,
                    childrenUpdateFieldlist,
                    childrenInfo,
                    isDelete ? null : parentId, mergeMode);
            if (childRequest != null) refIdToRequests.put(childId, childRequest);
        }

        // Parent request goes last when it's a delete
        if (parentRequest != null && isDelete)
            refIdToRequests.put(parentId, parentRequest);

        // Add SOQL query to get updated modification date in the case of an update
        if (!isCreate && !isDelete)
            refIdToRequests.put(REF_TIME_STAMPS, getRequestForTimestamps(syncManager.apiVersion, parentId));

        // Sending composite request
        Map<String, JSONObject> refIdToResponses = sendCompositeRequest(syncManager, refIdToRequests);

        // Build refId to server id / status code / time stamp maps
        Map<String, String> refIdToServerId = parseIdsFromResponse(refIdToResponses);
        Map<String, Integer> refIdToHttpStatusCode = parseStatusCodesFromResponse(refIdToResponses);
        Map<String, String> refIdToTimestamps = parseTimestampsFromResponse(refIdToResponses);

        // Update parent in local store
        updateRecordInLocalStore(syncManager, record, true, refIdToHttpStatusCode, refIdToTimestamps, refIdToServerId);

        // Update children local store
        for (int i = 0; i < children.length(); i++) {
            JSONObject childRecord = children.getJSONObject(i);
            if (isDirty(childRecord) || isCreate) {
                updateRecordInLocalStore(syncManager, childRecord, false, refIdToHttpStatusCode, refIdToTimestamps, refIdToServerId);
            }
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
     * @param syncManager
     * @param record
     * @param isParent
     *@param refIdToHttpStatusCode
     * @param refIdToTimestamps
     * @param refIdToServerId    @throws JSONException
     */
    protected void updateRecordInLocalStore(SyncManager syncManager, JSONObject record, boolean isParent, Map<String, Integer> refIdToHttpStatusCode, Map<String, String> refIdToTimestamps, Map<String, String> refIdToServerId) throws JSONException {
        final String soupName = isParent ? parentInfo.soupName : childrenInfo.soupName;
        final String idFieldName = isParent ? getIdFieldName() : childrenInfo.idFieldName;
        final String refId = record.getString(idFieldName);
        final Integer statusCode = refIdToHttpStatusCode.containsKey(refId) ? refIdToHttpStatusCode.get(refId) : -1;

        // Delete case
        if (isLocallyDeleted(record)) {
            if (isLocallyCreated(record) // we didn't go to the sever
                    || RestResponse.isSuccess(statusCode) // or we successfully deleted on the server
                    || statusCode == HttpURLConnection.HTTP_NOT_FOUND) // or the record was already deleted on the server
            {
                deleteFromLocalStore(syncManager, soupName, record);
            }
            // Otherwise leave record alone
            else {
                Log.i(TAG, String.format("Locally deleted record %s not deleted on server (server responded:%d)", refId, statusCode));
            }
        }

        // Create / update case
        else {
            if (RestResponse.isSuccess(statusCode)) // we successfully updated on the server
            {
                // Replace local id by server id
                updateReferences(record, idFieldName, refIdToServerId);

                // Replace parent local id by server id (in children)
                if (!isParent)
                    updateReferences(record, childrenInfo.parentIdFieldName, refIdToServerId);

                // Replace time stamp
                if (refIdToTimestamps != null) {
                    final String modificationDateFieldName = isParent ? getModificationDateFieldName() : childrenInfo.modificationDateFieldName;
                    record.put(modificationDateFieldName, refIdToTimestamps.get(refId));
                }

                // Clean and save
                cleanAndSaveInLocalStore(syncManager, soupName, record);
            }
            // Otherwise leave record alone
            else {
                Log.i(TAG, String.format("Locally %s record %s not synced up to server (server responded:%d)", (isLocallyCreated(record) ? "created":  "updated"), refId, statusCode));
            }
        }
    }

    protected RestRequest buildRequestForRecord(String apiVersion,
                                                JSONObject record,
                                                List<String> createFieldlist,
                                                List<String> updateFieldlist,
                                                ParentInfo info,
                                                String parentId, SyncState.MergeMode mergeMode) throws IOException, JSONException {

        String id = record.getString(info.idFieldName);

        final Map<String, String> additionalHttpHeaders = getAdditionalHttpHeaders(record, mergeMode);

        if (super.isLocallyDeleted(record) && !super.isLocallyCreated(record)) {
            return RestRequest.getRequestForDelete(apiVersion,
                    info.sobjectType,
                    id,
                    additionalHttpHeaders);
        } else if (super.isLocallyCreated(record)) {
            Map<String, Object> fields = buildFieldsMap(record, createFieldlist, info.idFieldName, info.modificationDateFieldName);
            if (info instanceof ChildrenInfo && parentId != null) {
                fields.put(((ChildrenInfo) info).parentIdFieldName, String.format("@{%s.%s}", parentId, Constants.LID));
            }
            return RestRequest.getRequestForCreate(apiVersion,
                    info.sobjectType,
                    fields);
        } else if (super.isLocallyUpdated(record)) {
            Map<String, Object> fields = buildFieldsMap(record, updateFieldlist, info.idFieldName, info.modificationDateFieldName);
            return RestRequest.getRequestForUpdate(apiVersion,
                    info.sobjectType,
                    id,
                    fields,
                    additionalHttpHeaders);
        } else {
            return null;
        }
    }

    /**
     * Return ref id to server id map if successful or null otherwise
     */
    protected Map<String, String> parseIdsFromResponse(Map<String, JSONObject> refIdToResponses) throws JSONException, IOException {
        Map<String, String> refIdtoId = new HashMap<>();
        for (String refId : refIdToResponses.keySet()) {
            JSONObject response = refIdToResponses.get(refId);
            if (response.getInt(HTTP_STATUS_CODE) == HttpURLConnection.HTTP_CREATED) {
                String serverId = response.getJSONObject(BODY).getString(Constants.LID);
                refIdtoId.put(refId, serverId);
            }
        }
        return refIdtoId;
    }

    /**
     * Return ref id to http status code map if successful or null otherwise
     */
    protected Map<String, Integer> parseStatusCodesFromResponse(Map<String, JSONObject> refIdToResponses) throws JSONException, IOException {
        Map<String, Integer> refIdToStatusCode = new HashMap<>();
        for (String refId : refIdToResponses.keySet()) {
            JSONObject response = refIdToResponses.get(refId);
                int httpStatusCode = response.getInt(HTTP_STATUS_CODE);
                refIdToStatusCode.put(refId, httpStatusCode);
            }
        return refIdToStatusCode;
    }

    /**
     * Return ref id to time stamp
     */
    private Map<String, String> parseTimestampsFromResponse(Map<String, JSONObject> refIdToResponses) throws IOException, JSONException {
        if (refIdToResponses.containsKey(REF_TIME_STAMPS) && RestResponse.isSuccess(refIdToResponses.get(REF_TIME_STAMPS).getInt(HTTP_STATUS_CODE))) {
            Map<String, String> refIdToTimestamps = new HashMap<>();
            JSONObject response = refIdToResponses.get(REF_TIME_STAMPS).getJSONObject(BODY).getJSONArray(Constants.RECORDS).getJSONObject(0);

            refIdToTimestamps.put(response.getString(getIdFieldName()), response.getString(getModificationDateFieldName()));
            if (response.has(childrenInfo.sobjectTypePlural) && !response.isNull(childrenInfo.sobjectTypePlural)) {
                JSONArray childrenRows = response.getJSONObject(childrenInfo.sobjectTypePlural).getJSONArray(Constants.RECORDS);
                for (int i = 0; i < childrenRows.length(); i++) {
                    final JSONObject childRow = childrenRows.getJSONObject(i);
                    refIdToTimestamps.put(childRow.getString(childrenInfo.idFieldName), childRow.getString(childrenInfo.modificationDateFieldName));
                }
            }
            return refIdToTimestamps;
        }
        else {
            return null;
        }
    }

    /**
     * Build SOQL request to get current time stamps
     *
     * @param apiVersion
     * @param parentId
     * @return
     * @throws UnsupportedEncodingException
     */
    private RestRequest getRequestForTimestamps(String apiVersion, String parentId) throws UnsupportedEncodingException {
        SOQLBuilder builderNested = SOQLBuilder.getInstanceWithFields(childrenInfo.idFieldName, childrenInfo.modificationDateFieldName);
        builderNested.from(childrenInfo.sobjectTypePlural);

        SOQLBuilder builder = SOQLBuilder.getInstanceWithFields(getIdFieldName(), getModificationDateFieldName(), String.format("(%s)", builderNested.build()));
        builder.from(parentInfo.sobjectType);
        builder.where(String.format("%s = '%s'", getIdFieldName(), parentId));

        RestRequest request = RestRequest.getRequestForQuery(apiVersion, builder.build());

        return request;
    }




    protected void updateReferences(JSONObject record, String fieldWithRefId, Map<String, String> refIdToServerId) throws JSONException {
        String refId = record.getString(fieldWithRefId);
        if (refIdToServerId.containsKey(refId)) {
            record.put(fieldWithRefId, refIdToServerId.get(refId));
        }
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
