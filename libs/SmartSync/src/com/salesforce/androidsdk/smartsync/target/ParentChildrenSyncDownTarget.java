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

import android.text.TextUtils;

import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartsync.manager.SyncManager;
import com.salesforce.androidsdk.smartsync.util.ChildrenInfo;
import com.salesforce.androidsdk.smartsync.util.ParentInfo;
import com.salesforce.androidsdk.smartsync.util.SOQLBuilder;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Target for sync that downloads parent with children records
 */
public class ParentChildrenSyncDownTarget extends SoqlSyncDownTarget {

    private static final String TAG = "ParentChildrenSyncDownTarget";

    public static final String PARENT = "parent";
    public static final String CHILDREN = "children";
    public static final String RELATIONSHIP_TYPE = "relationshipType";

    public static final String PARENT_FIELDLIST = "parentFieldlist";
    public static final String PARENT_SOQL_FILTER = "parentSoqlFilter";
    public static final String CHILDREN_FIELDLIST = "childrenFieldlist";

    private ParentInfo parentInfo;
    private List<String> parentFieldlist;
    private String parentSoqlFilter;
    private ChildrenInfo childrenInfo;
    private List<String> childrenFieldlist;
    private RelationshipType relationshipType;

    /**
     * Enum for relationship types
     */
    public enum RelationshipType {
        MASTER_DETAIL,
        LOOKUP;
    }

    /**
     * Construct ParentChildrenSyncDownTarget from json
     * @param target
     * @throws JSONException
     */
    public ParentChildrenSyncDownTarget(JSONObject target) throws JSONException {
        this(
                new ParentInfo(target.getJSONObject(PARENT)),
                JSONObjectHelper.<String>toList(target.optJSONArray(PARENT_FIELDLIST)),
                target.getString(PARENT_SOQL_FILTER),

                new ChildrenInfo(target.getJSONObject(CHILDREN)),
                JSONObjectHelper.<String>toList(target.optJSONArray(CHILDREN_FIELDLIST)),

                RelationshipType.valueOf(target.getString(RELATIONSHIP_TYPE))
        );
    }

    /**
     * Construct ParentChildrenSyncDownTarget from parentType, childrenType etc
     */
    public ParentChildrenSyncDownTarget(ParentInfo parentInfo, List<String> parentFieldlist, String parentSoqlFilter, ChildrenInfo childrenInfo, List<String> childrenFieldlist, RelationshipType relationshipType) {
        super(parentInfo.idFieldName, parentInfo.modificationDateFieldName, null);
        this.parentInfo = parentInfo;
        this.parentFieldlist = parentFieldlist;
        this.parentSoqlFilter = parentSoqlFilter;
        this.childrenInfo = childrenInfo;
        this.childrenFieldlist = childrenFieldlist;
        this.relationshipType = relationshipType;
    }

    /**
     * Construct ParentChildrenSyncDownTarget from soql query - not allowed
     */
    public ParentChildrenSyncDownTarget(String query) {
        super(query);
        throw new UnsupportedOperationException("Cannot construct ParentChildrenSyncDownTarget from SOQL query");
    }

    /**
     * @return json representation of target
     * @throws JSONException
     */
    public JSONObject asJSON() throws JSONException {
        JSONObject target = super.asJSON();
        target.put(PARENT, parentInfo.asJSON());
        target.put(PARENT_FIELDLIST, new JSONArray(parentFieldlist));
        target.put(PARENT_SOQL_FILTER, parentSoqlFilter);
        target.put(CHILDREN, childrenInfo.asJSON());
        target.put(CHILDREN_FIELDLIST, new JSONArray(childrenFieldlist));
        target.put(RELATIONSHIP_TYPE, relationshipType.name());
        return target;
    }

    @Override
    public String getSoqlForRemoteIds() {
        SOQLBuilder builderNested = SOQLBuilder.getInstanceWithFields(childrenInfo.idFieldName);
        builderNested.from(childrenInfo.sobjectTypePlural);

        List<String> fields = new ArrayList<>();
        fields.add(getIdFieldName());
        fields.add("(" + builderNested.build() + ")");
        SOQLBuilder builder = SOQLBuilder.getInstanceWithFields(fields);
        builder.from(parentInfo.sobjectType);
        builder.where(parentSoqlFilter);

        return builder.build();
    }

    @Override
    public String getQuery() {
        List<String> nestedFields = new ArrayList<>(childrenFieldlist);
        nestedFields.add(childrenInfo.idFieldName);
        nestedFields.add(childrenInfo.modificationDateFieldName);
        SOQLBuilder builderNested = SOQLBuilder.getInstanceWithFields(nestedFields);
        builderNested.from(childrenInfo.sobjectTypePlural);

        List<String> fields = new ArrayList<>(parentFieldlist);
        fields.add(getIdFieldName());
        fields.add(getModificationDateFieldName());
        fields.add("(" + builderNested.build() + ")");

        SOQLBuilder builder = SOQLBuilder.getInstanceWithFields(fields);
        builder.from(parentInfo.sobjectType);
        builder.where(parentSoqlFilter);

        return builder.build();
    }

    @Override
    public long getLatestModificationTimeStamp(JSONArray records) throws JSONException {
        // NB: method is called during sync down so for this target records contain parent and children

        // Compute max time stamp of parents
        long maxTimeStamp = super.getLatestModificationTimeStamp(records);

        // Compute max time stamp of parents and children
        for (int i=0; i<records.length(); i++) {
            JSONObject record = records.getJSONObject(i);
            JSONArray children = record.getJSONArray(childrenInfo.sobjectTypePlural);
            maxTimeStamp = Math.max(maxTimeStamp, getLatestModificationTimeStamp(children, childrenInfo.modificationDateFieldName));
        }

        return maxTimeStamp;
    }


    @Override
    protected String getDirtyRecordIdsSql(String soupName, String idField) {
        return String.format(
                "SELECT DISTINCT {%s:%s} FROM {%s},{%s} WHERE {%s:%s} = {%s:%s} AND ({%s:%s} = 'true' OR {%s:%s} = 'true')",
                soupName, idField,
                childrenInfo.soupName, soupName,
                childrenInfo.soupName, childrenInfo.parentLocalIdFieldName,
                soupName, SmartStore.SOUP_ENTRY_ID,
                soupName, LOCAL,
                childrenInfo.soupName, LOCAL);
    }

    @Override
    protected String getNonDirtyRecordIdsSql(String soupName, String idField) {
        return String.format(
                "SELECT {%s:%s} FROM {%s} WHERE {%s:%s} NOT IN (%s)",
                soupName, idField,
                soupName,
                soupName, SmartStore.SOUP_ENTRY_ID,
                getDirtyRecordIdsSql(soupName, SmartStore.SOUP_ENTRY_ID)
        );
    }

    @Override
    public void saveRecordsToLocalStore(SyncManager syncManager, String soupName, JSONArray records) throws JSONException {
        // NB: method is called during sync down so for this target records contain parent and children
        SmartStore smartStore = syncManager.getSmartStore();
        synchronized(smartStore.getDatabase()) {
            try {
                smartStore.beginTransaction();

                for (int i=0; i<records.length(); i++) {
                    JSONObject record = records.getJSONObject(i);
                    JSONObject parent = new JSONObject(record.toString());

                    // Separating parent from children
                    JSONArray children = (JSONArray) parent.remove(childrenInfo.sobjectTypePlural);

                    // Saving parent
                    cleanAndSaveInLocalStore(syncManager, soupName, parent, false);

                    // Put local id of parent in children
                    for (int j = 0; j < children.length(); j++) {
                        JSONObject child = children.getJSONObject(j);
                        child.put(childrenInfo.parentLocalIdFieldName, parent.get(SmartStore.SOUP_ENTRY_ID));

                        // Saving child
                        cleanAndSaveInLocalStore(syncManager, childrenInfo.soupName, child, false);
                    }
                }

                smartStore.setTransactionSuccessful();

            }
            finally {
                smartStore.endTransaction();
            }
        }
    }

    @Override
    public void deleteFromLocalStore(SyncManager syncManager, String soupName, JSONObject record) throws JSONException {
        if (relationshipType == RelationshipType.MASTER_DETAIL) {
            deleteChildrenFromLocalStore(syncManager, soupName, new String[]{record.getString(SmartStore.SOUP_ENTRY_ID)}, SmartStore.SOUP_ENTRY_ID);
        }
        super.deleteFromLocalStore(syncManager, soupName, record);
    }

    @Override
    public void deleteRecordsFromLocalStore(SyncManager syncManager, String soupName, Set<String> ids, String idField) {
        if (relationshipType == RelationshipType.MASTER_DETAIL) {
            deleteChildrenFromLocalStore(syncManager, soupName, ids.toArray(new String[0]), idField);
        }
        super.deleteRecordsFromLocalStore(syncManager, soupName, ids, idField);
    }

    protected void deleteChildrenFromLocalStore(SyncManager syncManager, String soupName, String[] ids, String idField) {
        String smartSql = String.format(
                "SELECT {%s:%s} FROM {%s},{%s} WHERE {%s:%s} = {%s:%s} AND {%s:%s} IN (%s)",
                childrenInfo.soupName, SmartStore.SOUP_ENTRY_ID,
                childrenInfo.soupName, soupName,
                childrenInfo.soupName, childrenInfo.parentLocalIdFieldName,
                soupName, SmartStore.SOUP_ENTRY_ID,
                soupName, idField,
                "'" + TextUtils.join("', '", ids) + "'");
        syncManager.getSmartStore().deleteByQuery(childrenInfo.soupName, QuerySpec.buildSmartQuerySpec(smartSql, Integer.MAX_VALUE));
    }
}
