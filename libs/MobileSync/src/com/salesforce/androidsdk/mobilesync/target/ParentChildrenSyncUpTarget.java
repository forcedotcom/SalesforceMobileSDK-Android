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
package com.salesforce.androidsdk.mobilesync.target;

import com.salesforce.androidsdk.mobilesync.app.Features;
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager;
import com.salesforce.androidsdk.mobilesync.manager.SyncManager;
import com.salesforce.androidsdk.mobilesync.target.ParentChildrenSyncTargetHelper.RelationshipType;
import com.salesforce.androidsdk.mobilesync.util.ChildrenInfo;
import com.salesforce.androidsdk.mobilesync.util.Constants;
import com.salesforce.androidsdk.mobilesync.util.MobileSyncLogger;
import com.salesforce.androidsdk.mobilesync.util.ParentInfo;
import com.salesforce.androidsdk.mobilesync.util.SOQLBuilder;
import com.salesforce.androidsdk.mobilesync.util.SyncState;
import com.salesforce.androidsdk.rest.CompositeResponse.CompositeSubResponse;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
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
    public static final String CHILDREN_CREATE_FIELDLIST = "childrenCreateFieldlist";
    public static final String CHILDREN_UPDATE_FIELDLIST = "childrenUpdateFieldlist";
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
        MobileSyncSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_RELATED_RECORDS);
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
        return ParentChildrenSyncTargetHelper.getDirtyRecordIdsSql(parentInfo, childrenInfo, idField);
    }

    @Override
    public String createOnServer(SyncManager syncManager, JSONObject record, List<String> fieldlist) {
        throw new UnsupportedOperationException("For advanced sync up target, call syncUpOneRecord");
    }

    @Override
    public int deleteOnServer(SyncManager syncManager, JSONObject record) {
        throw new UnsupportedOperationException("For advanced sync up target, call syncUpOneRecord");
    }

    @Override
    public int updateOnServer(SyncManager syncManager, JSONObject record, List<String> fieldlist) {
        throw new UnsupportedOperationException("For advanced sync up target, call syncUpOneRecord");
    }

    @Override
    public int getMaxBatchSize() {
        return 1;
    }

    @Override
    public void syncUpRecords(SyncManager syncManager, List<JSONObject> records, List<String> fieldlist, SyncState.MergeMode mergeMode, String syncSoupName) throws JSONException, IOException {
        if (records.size() > 1) {
            throw new SyncManager.MobileSyncException(getClass().getSimpleName() + ":syncUpRecords can handle only 1 record at a time");
        }

        if (!records.isEmpty()) {
            syncUpRecord(syncManager, records.get(0), fieldlist, mergeMode);
        }
    }

    private void syncUpRecord(SyncManager syncManager, JSONObject record, List<String> fieldlist, SyncState.MergeMode mergeMode) throws JSONException, IOException {

        boolean isCreate = isLocallyCreated(record);
        boolean isDelete = isLocallyDeleted(record);

        // Getting children
        JSONArray children = (relationshipType == RelationshipType.MASTER_DETAIL && isDelete && !isCreate)
                // deleting master in a master-detail relationship will delete the children
                // so no need to actually do any work on the children
                ? new JSONArray()
                : ParentChildrenSyncTargetHelper.getChildrenFromLocalStore(
                    syncManager.getSmartStore(),
                    parentInfo,
                    childrenInfo,
                    record);

        syncUpRecord(syncManager, record, children, fieldlist, mergeMode);
    }

    private void syncUpRecord(SyncManager syncManager, JSONObject record, JSONArray children, List<String> fieldlist, SyncState.MergeMode mergeMode) throws JSONException, IOException {
        boolean isCreate = isLocallyCreated(record);
        boolean isDelete = isLocallyDeleted(record);

        // Preparing request for parent
        LinkedHashMap<String, RestRequest> refIdToRequests = new LinkedHashMap<>();
        String parentId = record.getString(getIdFieldName());
        RestRequest parentRequest = buildRequestForParentRecord(syncManager.apiVersion, record, fieldlist);

        // Parent request goes first unless it's a delete
        if (parentRequest != null && !isDelete)
            refIdToRequests.put(parentId, parentRequest);

        // Preparing requests for children
        for (int i = 0; i < children.length(); i++) {
            JSONObject childRecord = children.getJSONObject(i);
            String childId = childRecord.getString(childrenInfo.idFieldName);

            // Parent will get a server id
            // Children need to be updated
            if (isCreate) {
                childRecord.put(LOCAL, true);
                childRecord.put(LOCALLY_UPDATED, true);
            }
            RestRequest childRequest = buildRequestForChildRecord(syncManager.apiVersion, childRecord,
                    isCreate,
                    isDelete ? null : parentId);
            if (childRequest != null) refIdToRequests.put(childId, childRequest);
        }

        // Parent request goes last when it's a delete
        if (parentRequest != null && isDelete)
            refIdToRequests.put(parentId, parentRequest);

        // Sending composite request
        Map<String, CompositeSubResponse> refIdToResponses = CompositeRequestHelper.sendCompositeRequest(syncManager, false, refIdToRequests);

        // Build refId to server id / status code / time stamp maps
        Map<String, String> refIdToServerId = CompositeRequestHelper.parseIdsFromResponses(refIdToResponses.values());

        // Will a re-run be required?
        boolean needReRun = false;

        // Update parent in local store
        if (isDirty(record)) {
            needReRun = updateParentRecordInLocalStore(syncManager, record, children, mergeMode, refIdToServerId,
                    refIdToResponses.get(record.getString(getIdFieldName())));
        }

        // Update children local store
        for (int i = 0; i < children.length(); i++) {
            JSONObject childRecord = children.getJSONObject(i);

            if (isDirty(childRecord) || isCreate) {
                needReRun = needReRun || updateChildRecordInLocalStore(syncManager, childRecord, record, mergeMode, refIdToServerId,
                        refIdToResponses.get(childRecord.getString(childrenInfo.idFieldName)));
            }
        }

        // Re-run if required
        if (needReRun) {
            MobileSyncLogger.d(TAG, "syncUpOneRecord", record);
            syncUpRecord(syncManager, record, children, fieldlist, mergeMode);
        }
    }

    protected boolean updateParentRecordInLocalStore(SyncManager syncManager, JSONObject record, JSONArray children, SyncState.MergeMode mergeMode, Map<String, String> refIdToServerId, CompositeSubResponse response) throws JSONException, IOException {
        boolean needReRun = false;
        final String soupName = parentInfo.soupName;
        final Integer statusCode = response != null ? response.httpStatusCode : -1;

        // Delete case
        if (isLocallyDeleted(record)) {
            if (isLocallyCreated(record)  // we didn't go to the sever
                || RestResponse.isSuccess(statusCode) // or we successfully deleted on the server
                || statusCode == HttpURLConnection.HTTP_NOT_FOUND) // or the record was already deleted on the server
            {
                if (relationshipType == RelationshipType.MASTER_DETAIL) {
                    ParentChildrenSyncTargetHelper.deleteChildrenFromLocalStore(syncManager.getSmartStore(), parentInfo, childrenInfo, record.getString(getIdFieldName()));
                }

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
            if (RestResponse.isSuccess(statusCode))
            {
                // Plugging server id in id field
                CompositeRequestHelper.updateReferences(record, getIdFieldName(), refIdToServerId);

                // Clean and save
                cleanAndSaveInLocalStore(syncManager, soupName, record);
            }
            // Handling remotely deleted records
            else if (statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
                // Record needs to be recreated
                if (mergeMode == SyncState.MergeMode.OVERWRITE) {
                    record.put(LOCAL, true);
                    record.put(LOCALLY_CREATED, true);

                    // Children need to be updated or recreated as well (since the parent will get a new server id)
                    for (int i=0; i<children.length(); i++) {
                        JSONObject childRecord = children.getJSONObject(i);
                        childRecord.put(LOCAL, true);
                        childRecord.put(relationshipType == RelationshipType.MASTER_DETAIL ? LOCALLY_CREATED : LOCALLY_UPDATED, true);
                    }
                    needReRun = true;
                }
            }
            // Failure
            else {
                saveRecordToLocalStoreWithError(syncManager, soupName, record, response != null ? response.toString() : null);
            }
        }
        return needReRun;
    }

    protected boolean updateChildRecordInLocalStore(SyncManager syncManager, JSONObject record, JSONObject parentRecord, SyncState.MergeMode mergeMode, Map<String, String> refIdToServerId, CompositeSubResponse response) throws JSONException {
        boolean needReRun = false;
        final String soupName = childrenInfo.soupName;
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
            if (RestResponse.isSuccess(statusCode))
            {
                // Plugging server id in id field
                CompositeRequestHelper.updateReferences(record, childrenInfo.idFieldName, refIdToServerId);

                // Plugging server id in parent id field
                CompositeRequestHelper.updateReferences(record, childrenInfo.parentIdFieldName, refIdToServerId);

                // Clean and save
                cleanAndSaveInLocalStore(syncManager, soupName, record);
            }
            // Handling remotely deleted records
            else if (statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
                // Record needs to be recreated
                if (mergeMode == SyncState.MergeMode.OVERWRITE) {

                    record.put(LOCAL, true);
                    record.put(LOCALLY_CREATED, true);

                    // We need a re-run
                    needReRun = true;
                }
            }
            // Handling remotely deleted parent
            else if(isEntityDeleted(response)) {
                // Parent record needs to be recreated
                if (mergeMode == SyncState.MergeMode.OVERWRITE) {
                    parentRecord.put(LOCAL, true);
                    parentRecord.put(LOCALLY_CREATED, true);

                    // We need a re-run
                    needReRun = true;
                }
            }
            // Failure
            else {
                saveRecordToLocalStoreWithError(syncManager, soupName, record, response != null ? response.toString() : null);
            }
        }
        return needReRun;
    }

    protected RestRequest buildRequestForParentRecord(String apiVersion,
                                                JSONObject record,
                                                List<String> fieldlist) throws IOException, JSONException {
        return buildRequestForRecord(apiVersion, record, fieldlist, true, false, null);
    }

    protected RestRequest buildRequestForChildRecord(String apiVersion,
                                                JSONObject record,
                                                boolean useParentIdReference,
                                                String parentId) throws IOException, JSONException {
        return buildRequestForRecord(apiVersion, record, null, false, useParentIdReference, parentId);
    }

    protected RestRequest buildRequestForRecord(String apiVersion,
                                                JSONObject record,
                                                List<String> fieldlist,
                                                boolean isParent,
                                                boolean useParentIdReference,
                                                String parentId) throws IOException, JSONException {
        if (!isDirty(record)) {
            return null; // nothing to do
        }
        ParentInfo info = isParent ? parentInfo : childrenInfo;
        String id = record.getString(info.idFieldName);

        // Delete case
        boolean isDelete = isLocallyDeleted(record);
        boolean isCreate = isLocallyCreated(record);
        if (isDelete) {
            if (isCreate) {
                return null; // no need to go to server
            }
            else {
                return RestRequest.getRequestForDelete(apiVersion,
                        info.sobjectType,
                        id);
            }
        }
        // Create/update cases
        else {
            fieldlist = isParent
                    ? isCreate
                        ? (this.createFieldlist == null ? fieldlist : this.createFieldlist)
                        : (this.updateFieldlist == null ? fieldlist : this.updateFieldlist)
                    : isCreate
                        ? childrenCreateFieldlist
                        : childrenUpdateFieldlist
                    ;
            Map<String, Object> fields = buildFieldsMap(record, fieldlist, info.idFieldName, info.modificationDateFieldName);
            if (parentId != null) {
                fields.put(
                        ((ChildrenInfo) info).parentIdFieldName,
                        useParentIdReference
                                ? String.format("@{%s.%s}", parentId, Constants.LID)
                                : parentId
                );
            }
            if (isCreate) {
                return RestRequest.getRequestForCreate(apiVersion,
                        info.sobjectType,
                        fields);
            }
            else {
                return RestRequest.getRequestForUpdate(apiVersion,
                        info.sobjectType,
                        id,
                        fields);
            }
        }
    }

    @Override
    public boolean isNewerThanServer(SyncManager syncManager, JSONObject record) throws JSONException, IOException {
        if (isLocallyCreated(record)) {
            return true;
        }
        Map<String, RecordModDate> idToLocalTimestamps = getLocalLastModifiedDates(syncManager, record);
        Map<String, String> idToRemoteTimestamps = fetchLastModifiedDates(syncManager, record);
        for (String id : idToLocalTimestamps.keySet()) {
            final RecordModDate localModDate = idToLocalTimestamps.get(id);
            final String remoteTimestamp = idToRemoteTimestamps.get(id);
            final RecordModDate remoteModDate = new RecordModDate(
                remoteTimestamp,
                remoteTimestamp == null // if it wasn't returned by fetchLastModifiedDates, then the record must have been deleted
            );
            if (!super.isNewerThanServer(localModDate, remoteModDate)) {
                return false; // no need to go further
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
    protected Map<String, RecordModDate> getLocalLastModifiedDates(SyncManager syncManager, JSONObject record) throws JSONException {
        Map<String, RecordModDate> idToLocalTimestamps = new HashMap<>();
        final boolean isParentDeleted = isLocallyDeleted(record);
        final RecordModDate parentModDate = new RecordModDate(
                JSONObjectHelper.optString(record, getModificationDateFieldName()),
                isParentDeleted
        );
        idToLocalTimestamps.put(record.getString(getIdFieldName()), parentModDate);
        JSONArray children = ParentChildrenSyncTargetHelper.getChildrenFromLocalStore(
                syncManager.getSmartStore(),
                parentInfo,
                childrenInfo,
                record
        );
        for (int i=0; i<children.length(); i++) {
            JSONObject childRecord = children.getJSONObject(i);
            final RecordModDate childModDate = new RecordModDate(
                    JSONObjectHelper.optString(childRecord, childrenInfo.modificationDateFieldName),
                    isLocallyDeleted(childRecord) || (isParentDeleted && relationshipType == RelationshipType.MASTER_DETAIL)
            );
            idToLocalTimestamps.put(childRecord.getString(childrenInfo.idFieldName), childModDate);
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
            RestResponse lastModResponse = syncManager.sendSyncWithMobileSyncUserAgent(lastModRequest);
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
    protected RestRequest getRequestForTimestamps(String apiVersion, String parentId) throws UnsupportedEncodingException {
        SOQLBuilder builderNested = SOQLBuilder.getInstanceWithFields(childrenInfo.idFieldName, childrenInfo.modificationDateFieldName);
        builderNested.from(childrenInfo.sobjectTypePlural);
        SOQLBuilder builder = SOQLBuilder.getInstanceWithFields(getIdFieldName(), getModificationDateFieldName(), String.format("(%s)", builderNested.build()));
        builder.from(parentInfo.sobjectType);
        builder.where(String.format("%s = '%s'", getIdFieldName(), parentId));
        return RestRequest.getRequestForQuery(apiVersion, builder.build());
    }

    protected boolean isEntityDeleted(CompositeSubResponse response) {
        try {
            return response != null && "ENTITY_IS_DELETED".equals(response.bodyAsJSONArray().getJSONObject(0).getString("errorCode"));
        } catch (JSONException e) {
            return false;
        }
    }
}
