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
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Target for sync that uploads parent with children records
 */
public class ParentChildrenSyncUpTarget extends SyncUpTarget {
    private static final String TAG = "Parent..SyncUpTarget";

    public static final String CHILDREN_CREATE_FIELDLIST = "childrenCreateFieldlist";
    public static final String CHILDREN_UPDATE_FIELDLIST = "childrenUpdateFieldlist";
    public static final String RESULTS = "results";
    public static final String REFERENCE_ID = "referenceId";
    public static final String REF_1 = "ref1";

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
    public JSONObject createOnServer(SyncManager syncManager, JSONObject record, List<String> fieldlist) throws JSONException, IOException {
        // Fields for parent
        fieldlist = createFieldlist != null ? createFieldlist : fieldlist;
        Map<String, Object> parentFields = buildFieldsMap(record, fieldlist);
        parentFields.put(RestRequest.REFERENCE_ID, REF_1);


        // Getting children
        JSONArray children = ParentChildrenSyncTargetHelper.getChildrenFromLocalStore(
                syncManager.getSmartStore(),
                parentInfo.soupName,
                record,
                childrenInfo);

        // Prepare tree
        List<Map<String, Object>> childrenFields = new ArrayList<>();
        for (int i=0; i<children.length(); i++) {
            JSONObject child = children.getJSONObject(i);
            Map<String, Object> childFields = buildFieldsMap(child, childrenCreateFieldlist);
            childFields.put(RestRequest.REFERENCE_ID, getRefForChild(i));
            childrenFields.add(childFields);
        }
        Map<String, List<Map<String, Object>>> objectTypeToListChildrenFields = new HashMap<>();
        objectTypeToListChildrenFields.put(childrenInfo.sobjectType, childrenFields);

        Map<String, Map<String, List<Map<String, Object>>>> refIdToObjectTypeToListChildrenFields = new HashMap<>();
        refIdToObjectTypeToListChildrenFields.put(REF_1, objectTypeToListChildrenFields);

        Map<String, String> objectTypeToObjectTypePlural = new HashMap<>();
        objectTypeToObjectTypePlural.put(childrenInfo.sobjectType, childrenInfo.sobjectTypePlural);

        // Building request
        RestRequest request = RestRequest.getRequestForSObjectTree(syncManager.apiVersion, parentInfo.sobjectType, parentFields, refIdToObjectTypeToListChildrenFields, objectTypeToObjectTypePlural);

        // Sending request
        RestResponse response = syncManager.sendSyncWithSmartSyncUserAgent(request);

        Log.i("--response-->", response.asString());

        // Updated record
        Map<String, String> refIdToId = parseIdsFromResponse(response);
        if (refIdToId == null) {
            return null;
        }
        else {
            JSONObject updatedRecord = new JSONObject(record.toString());
            updatedRecord.put(getIdFieldName(), refIdToId.get(REF_1));
            JSONArray updatedChildren = new JSONArray();
            for (int i=0; i<children.length(); i++) {
                JSONObject updatedChild = children.getJSONObject(i);
                updatedChild.put(childrenInfo.idFieldName, refIdToId.get(getRefForChild(i)));
                updatedChildren.put(updatedChild);
            }
            updatedRecord.put(childrenInfo.sobjectTypePlural, updatedChildren);

            return updatedRecord;
        }
    }

    protected String getRefForChild(int childIndex) {
        return  "ref" + (childIndex+2); // first child should be ref2, parent if ref1
    }

    /**
     * Return ref id to server id if successful or null otherwise
     */
    protected Map<String, String> parseIdsFromResponse(RestResponse response) throws JSONException, IOException {
        if (response.isSuccess()) {
            Map<String, String> refIdtoId = new HashMap<>();
            JSONArray results = response.asJSONObject().getJSONArray(RESULTS);
            for (int i=0; i<results.length(); i++) {
                JSONObject result = results.getJSONObject(i);
                String refId = result.getString(REFERENCE_ID);
                String serverId = result.getString(Constants.LID);
                refIdtoId.put(refId, serverId);
            }
            return refIdtoId;
        }
        else {
            Log.e(TAG, "createOnServer failed:" + response.asString());
            return null;
        }
    }

    @Override
    public int deleteOnServer(SyncManager syncManager, JSONObject record) throws JSONException, IOException {
        // TBD
        return super.deleteOnServer(syncManager, record);
    }

    @Override
    public int updateOnServer(SyncManager syncManager, JSONObject record, List<String> fieldlist) throws JSONException, IOException {
        // TBD
        return super.updateOnServer(syncManager, record, fieldlist);
    }

    @Override
    public String fetchLastModifiedDate(SyncManager syncManager, JSONObject record) {
        // TBD
        return super.fetchLastModifiedDate(syncManager, record);
    }

    @Override
    public void saveInLocalStore(SyncManager syncManager, String soupName, JSONObject record) throws JSONException {
        // NB: method is called during sync up so for this target records contain parent and children
        JSONArray recordTrees = new JSONArray();
        recordTrees.put(record);
        ParentChildrenSyncTargetHelper.saveRecordTreesToLocalStore(syncManager, this, parentInfo, childrenInfo, recordTrees);
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
