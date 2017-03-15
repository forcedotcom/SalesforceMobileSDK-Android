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

import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartsync.manager.SyncManager;
import com.salesforce.androidsdk.smartsync.target.ParentChildrenSyncTargetHelper.RelationshipType;
import com.salesforce.androidsdk.smartsync.util.ChildrenInfo;
import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.androidsdk.smartsync.util.ParentInfo;
import com.salesforce.androidsdk.smartsync.util.SOQLBuilder;
import com.salesforce.androidsdk.smartsync.util.SyncManagerLogger;
import com.salesforce.androidsdk.smartsync.util.SyncState;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Target for sync that uploads parent with children records
 */
public class ParentChildrenSyncUpTarget extends SyncUpTarget implements AdvancedSyncUpTarget {

    // Constants
    private static final String TAG = "Parent..SyncUpTarget";
    public static final String CHILDREN_CREATE_FIELDLIST = "childrenCreateFieldlist";
    public static final String CHILDREN_UPDATE_FIELDLIST = "childrenUpdateFieldlist";
    public static final String REF_TIME_STAMPS = "refTimeStamps";

    public static final String COMPOSITE_RESPONSE = "compositeResponse";
    public static final String REFERENCE_ID = "referenceId";
    public static final String BODY = "body";
    public static final String HTTP_STATUS_CODE = "httpStatusCode";

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
    public static final String DELETE_ON_SERVER_RECORD_REMOTELY_DELETED_LEAVING_THINGS_UNCHANGED = "delete on server:record remotely deleted - leaving things unchanged";
    public static final String DELETE_ON_SERVER_RECORD_REMOTELY_UPDATED_LEAVING_THINGS_UNCHANGED = "delete on server:record remotely updated - leaving things unchanged";

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
    public String createOnServer(SyncManager syncManager, JSONObject record, List<String> fieldlist) throws JSONException, IOException {
        throw new UnsupportedOperationException("For advanced sync up target, call syncUpOneRecord");
    }

    @Override
    public int deleteOnServer(SyncManager syncManager, JSONObject record) throws JSONException, IOException {
        throw new UnsupportedOperationException("For advanced sync up target, call syncUpOneRecord");
    }

    @Override
    public int updateOnServer(SyncManager syncManager, JSONObject record, List<String> fieldlist) throws JSONException, IOException {
        throw new UnsupportedOperationException("For advanced sync up target, call syncUpOneRecord");
    }

    @Override
    public void syncUpRecord(SyncManager syncManager, JSONObject record, List<String> fieldlist, SyncState.MergeMode mergeMode) throws JSONException, IOException {
        boolean isCreate = isLocallyCreated(record);
        boolean isDelete = isLocallyDeleted(record);

        // Preparing requests for parent
        LinkedHashMap<String, RestRequest> refIdToRequests = new LinkedHashMap<>();
        String parentId = record.getString(getIdFieldName());
        RestRequest parentRequest = buildRequestForRecord(syncManager.apiVersion,
                record,
                createFieldlist == null ? fieldlist : createFieldlist,
                updateFieldlist == null ? fieldlist : updateFieldlist,
                parentInfo,
                null,
                mergeMode, syncManager.getLogger());

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
                    isDelete ? null : parentId, mergeMode, syncManager.getLogger());
            if (childRequest != null) refIdToRequests.put(childId, childRequest);
        }

        // Parent request goes last when it's a delete
        if (parentRequest != null && isDelete)
            refIdToRequests.put(parentId, parentRequest);

        // Sending composite request
        Map<String, JSONObject> refIdToResponses = sendCompositeRequest(syncManager, refIdToRequests);

        // Build refId to server id / status code / time stamp maps
        Map<String, String> refIdToServerId = parseIdsFromResponse(refIdToResponses);
        Map<String, Integer> refIdToHttpStatusCode = parseStatusCodesFromResponse(refIdToResponses);

        // Update parent in local store
        if (isDirty(record)) {
            updateRecordInLocalStore(syncManager, record, true, refIdToHttpStatusCode, refIdToServerId, mergeMode);
        }

        // Update children local store
        for (int i = 0; i < children.length(); i++) {
            JSONObject childRecord = children.getJSONObject(i);
            if (isDirty(childRecord) || isCreate) {
                updateRecordInLocalStore(syncManager, childRecord, false, refIdToHttpStatusCode, refIdToServerId, mergeMode);
            }
        }
    }

    /**
     * @param syncManager
     * @param record
     * @param isParent
     * @param refIdToHttpStatusCode
     * @param refIdToServerId       @throws JSONException
     * @param mergeMode
     */
    protected void updateRecordInLocalStore(SyncManager syncManager, JSONObject record, boolean isParent, Map<String, Integer> refIdToHttpStatusCode, Map<String, String> refIdToServerId, SyncState.MergeMode mergeMode) throws JSONException {
        final String soupName = isParent ? parentInfo.soupName : childrenInfo.soupName;
        final String idFieldName = isParent ? getIdFieldName() : childrenInfo.idFieldName;
        final String refId = record.getString(idFieldName);

        final Integer statusCode = refIdToHttpStatusCode.containsKey(refId) ? refIdToHttpStatusCode.get(refId) : -1;

        // Delete case
        if (isLocallyDeleted(record)) {
            if (isLocallyCreated(record)) { // we didn't go to the sever
                syncManager.getLogger().d(this, DELETE_ON_SERVER_NO_NEED_TO_DELETE_ON_SERVER_LOCALLY_CREATED_DELETING_LOCALLY, record);
            } else if (RestResponse.isSuccess(statusCode) // or we successfully deleted on the server
                    || statusCode == HttpURLConnection.HTTP_NOT_FOUND) // or the record was already deleted on the server
            {
                syncManager.getLogger().d(this, DELETE_ON_SERVER_SERVER_DELETE_SUCCEEDED_DELETING_LOCALLY, record);
                deleteFromLocalStore(syncManager, soupName, record);
            }
            // Something went wrong
            else {
                throw new SyncManager.SmartSyncException(DELETE_ON_SERVER_FAILED + statusCode);
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

                syncManager.getLogger().d(this, UPDATE_ON_SERVER_SERVER_UPDATE_SUCCEEDED_SAVING_LOCALLY, record);

                // Clean and save
                cleanAndSaveInLocalStore(syncManager, soupName, record);
            }
            // Handling remotely deleted records
            else if (statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
                if (mergeMode == SyncState.MergeMode.OVERWRITE) {
                    syncManager.getLogger().d(this, UPDATE_ON_SERVER_RECORD_REMOTELY_DELETED_RE_CREATING, record);
                    // TBD createOnServer(syncManager, record, createFieldlist != null ? createFieldlist : fieldlist, soupName, mergeMode);
                } else {
                    syncManager.getLogger().d(this, UPDATE_ON_SERVER_RECORD_REMOTELY_DELETED_LEAVING_THINGS_UNCHANGED, record);
                }
            }
            // Remote record was left alone because it has recently changed
            else if (statusCode == HttpURLConnection.HTTP_PRECON_FAILED && mergeMode == SyncState.MergeMode.LEAVE_IF_CHANGED) {
                syncManager.getLogger().d(this, UPDATE_ON_SERVER_RECORD_REMOTELY_UPDATED_LEAVING_THINGS_UNCHANGED, record);
            }
            // Unexpected
            else {
                throw new SyncManager.SmartSyncException(UPDATE_ON_SERVER_GOT_UNEXPECTED_STATUS + statusCode);
            }
        }
    }

    protected RestRequest buildRequestForRecord(String apiVersion,
                                                JSONObject record,
                                                List<String> createFieldlist,
                                                List<String> updateFieldlist,
                                                ParentInfo info,
                                                String parentId, SyncState.MergeMode mergeMode, SyncManagerLogger logger) throws IOException, JSONException {

        String id = record.getString(info.idFieldName);

        if (super.isLocallyDeleted(record) && !super.isLocallyCreated(record)) {

            return RestRequest.getRequestForDelete(apiVersion,
                    info.sobjectType,
                    id);

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
                    fields);
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

    @Override
    public boolean isNewerThanServer(SyncManager syncManager, JSONObject record) throws JSONException, IOException {
        if (isLocallyCreated(record)) {
            return true;
        }

        Map<String, String> idToLocalTimestamps = getLocalLastModifiedDates(syncManager, record);
        Map<String, String> idToRemoteTimestamps = fetchLastModifiedDates(syncManager, record);

        for (String id : idToLocalTimestamps.keySet()) {
            if (idToLocalTimestamps.get(id) != null
                && idToRemoteTimestamps.get(id) != null
                && idToRemoteTimestamps.get(id).compareTo(idToLocalTimestamps.get(id)) > 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Get local last modified dates for a given record and its children
     * @param syncManager
     * @param record
     * @return
     */
    protected Map<String, String> getLocalLastModifiedDates(SyncManager syncManager, JSONObject record) throws JSONException, IOException {
        Map<String, String> idToLocalTimestamps = new HashMap<>();

        final String parentModDate = record.getString(getModificationDateFieldName());
        if (parentModDate != null) {
            idToLocalTimestamps.put(record.getString(getIdFieldName()), parentModDate);
        }

        JSONArray children = ParentChildrenSyncTargetHelper.getChildrenFromLocalStore(
                syncManager.getSmartStore(),
                parentInfo.soupName,
                record,
                childrenInfo);

        for (int i=0; i<children.length(); i++) {
            JSONObject childRecord = children.getJSONObject(i);
            final String childModDate = childRecord.getString(childrenInfo.modificationDateFieldName);
            if (childModDate != null) {
                idToLocalTimestamps.put(childRecord.getString(childrenInfo.idFieldName), childModDate);
            }
        }

        return idToLocalTimestamps;
    }

    /**
     * Fetch last modified dates for a given record and its chidlren
     * @param syncManager
     * @param record
     * @return
     */
    protected Map<String, String> fetchLastModifiedDates(SyncManager syncManager, JSONObject record) throws JSONException, IOException {
        Map<String, String> idToRemoteTimestamps = new HashMap<>();

        if (!isLocallyCreated(record)) {
            String parentId = record.getString(getIdFieldName());
            RestRequest lastModRequest = getRequestForTimestamps(syncManager.apiVersion, parentId);
            RestResponse lastModResponse = syncManager.sendSyncWithSmartSyncUserAgent(lastModRequest);
            JSONArray rows = lastModResponse.isSuccess() ? lastModResponse.asJSONObject().getJSONArray(Constants.RECORDS) : null;

            if (rows != null && rows.length() > 0) {
                JSONObject row = rows.getJSONObject(0);
                idToRemoteTimestamps.put(row.getString(getIdFieldName()), row.getString(getModificationDateFieldName()));
                if (row.has(childrenInfo.sobjectTypePlural) && !row.isNull(childrenInfo.sobjectTypePlural)) {
                    JSONArray childrenRows = row.getJSONObject(childrenInfo.sobjectTypePlural).getJSONArray(Constants.RECORDS);
                    for (int i = 0; i < childrenRows.length(); i++) {
                        final JSONObject childRow = childrenRows.getJSONObject(i);
                        idToRemoteTimestamps.put(childRow.getString(childrenInfo.idFieldName), childRow.getString(childrenInfo.modificationDateFieldName));
                    }
                }
            }
        }

        return idToRemoteTimestamps;
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
