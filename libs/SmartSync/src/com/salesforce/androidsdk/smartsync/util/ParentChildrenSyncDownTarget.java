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
package com.salesforce.androidsdk.smartsync.util;

import com.salesforce.androidsdk.smartsync.manager.SyncManager;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

/**
 * Target for sync that downloads parent with children records
 */
public class ParentChildrenSyncDownTarget extends SoqlSyncDownTarget {

    private static final String TAG = "ParentChildrenSyncDownTarget";

    public static final String PARENT_TYPE = "parentType";
    public static final String PARENT_FIELDLIST = "parentFieldlist";
    public static final String PARENT_SOQL_FILTER = "parentSoqlFilter";

    public static final String CHILDREN_TYPE = "childrenType";
    public static final String CHILDREN_TYPE_PLURAL = "childrenTypePlural";
    public static final String CHILDREN_FIELDLIST = "childrenFieldlist";
    public static final String CHILDREN_ID_FIELD_NAME = "childrenIdFieldName";
    public static final String CHILDREN_MODIFICATION_DATE_FIELD_NAME = "childrenModificationDateFieldName";
    public static final String CHILDREN_SOUP_NAME = "childrenSoupName";

    private String parentType;
    private List<String> parentFieldlist;
    private String parentSoqlFilter;

    private String childrenType;
    private String childrenTypePlural;
    private List<String> childrenFieldlist;
    private String childrenIdFieldName;
    private String childrenModificationDateFieldName;
    private String childrenSoupName;


    /**
     * Construct ParentChildrenSyncDownTarget from json
     * @param target
     * @throws JSONException
     */
    public ParentChildrenSyncDownTarget(JSONObject target) throws JSONException {
        this(
                target.getString(PARENT_TYPE),
                JSONObjectHelper.<String>toList(target.optJSONArray(PARENT_FIELDLIST)),
                JSONObjectHelper.optString(target, ID_FIELD_NAME),
                JSONObjectHelper.optString(target, MODIFICATION_DATE_FIELD_NAME),
                target.getString(PARENT_SOQL_FILTER),
                target.getString(CHILDREN_TYPE),
                target.getString(CHILDREN_TYPE_PLURAL),
                JSONObjectHelper.<String>toList(target.optJSONArray(CHILDREN_FIELDLIST)),
                JSONObjectHelper.optString(target, CHILDREN_ID_FIELD_NAME),
                JSONObjectHelper.optString(target, CHILDREN_MODIFICATION_DATE_FIELD_NAME),
                target.getString(CHILDREN_SOUP_NAME)
        );
    }

    /**
     * Construct ParentChildrenSyncDownTarget from parentType, childrenType etc
     * @param parentType
     * @param parentFieldlist
     * @param childrenType
     * @param childrenTypePlural
     * @param childrenFieldlist
     * @param childrenSoupName
     */
    public ParentChildrenSyncDownTarget(String parentType, List<String> parentFieldlist, String parentSoqlFilter, String childrenType, String childrenTypePlural, List<String> childrenFieldlist, String childrenSoupName) {
        this(parentType, parentFieldlist, null, null, parentSoqlFilter, childrenType, childrenTypePlural, childrenFieldlist, null, null, childrenSoupName);
    }

    /**
     * Construct ParentChildrenSyncDownTarget from parentType, childrenType etc
     * @param parentType
     * @param parentFieldlist
     * @param idFieldName
     * @param modificationDateFieldName
     * @param parentSoqlFilter
     * @param childrenType
     * @param childrenTypePlural
     * @param childrenFieldlist
     * @param childrenIdFieldName
     * @param childrenModificationDateFieldName
     * @param childrenSoupName
     */
    public ParentChildrenSyncDownTarget(String parentType, List<String> parentFieldlist, String idFieldName, String modificationDateFieldName, String parentSoqlFilter, String childrenType, String childrenTypePlural, List<String> childrenFieldlist, String childrenIdFieldName, String childrenModificationDateFieldName, String childrenSoupName) {
        super(idFieldName, modificationDateFieldName, null);
        this.queryType = QueryType.parent_children;
        this.parentType = parentType;
        this.parentFieldlist = parentFieldlist;
        this.childrenType = childrenType;
        this.childrenTypePlural = childrenTypePlural;
        this.parentSoqlFilter = parentSoqlFilter;
        this.childrenFieldlist = childrenFieldlist;
        this.childrenSoupName = childrenSoupName;
        this.childrenIdFieldName = childrenIdFieldName != null ? childrenIdFieldName : Constants.ID;
        this.childrenModificationDateFieldName = childrenModificationDateFieldName != null ? childrenModificationDateFieldName : Constants.LAST_MODIFIED_DATE;
    }

    /**
     * Construct ParentChildrenSyncDownTarget from soql query - not allowed
     */
    public ParentChildrenSyncDownTarget(String query) {
        super(query);
        throw new RuntimeException("Cannot construct ParentChildrenSyncDownTarget from SOQL query");
    }

    /**
     * @return json representation of target
     * @throws JSONException
     */
    public JSONObject asJSON() throws JSONException {
        JSONObject target = super.asJSON();
        target.put(PARENT_TYPE, parentType);
        target.put(PARENT_FIELDLIST, new JSONArray(parentFieldlist));
        target.put(PARENT_SOQL_FILTER, parentSoqlFilter);
        target.put(CHILDREN_TYPE, childrenType);
        target.put(CHILDREN_TYPE_PLURAL, childrenTypePlural);
        target.put(CHILDREN_FIELDLIST, new JSONArray(childrenFieldlist));
        target.put(CHILDREN_ID_FIELD_NAME, childrenIdFieldName);
        target.put(CHILDREN_MODIFICATION_DATE_FIELD_NAME, childrenModificationDateFieldName);
        target.put(CHILDREN_SOUP_NAME, childrenSoupName);
        return target;
    }

    @Override
    public String getSoqlForRemoteIds() {
        SOQLBuilder builderNested = SOQLBuilder.getInstanceWithFields(childrenIdFieldName);
        builderNested.from(childrenTypePlural);

        List<String> fields = new ArrayList<>();
        fields.add(getIdFieldName());
        fields.add("(" + builderNested.build() + ")");
        SOQLBuilder builder = SOQLBuilder.getInstanceWithFields(fields);
        builder.from(parentType);
        builder.where(parentSoqlFilter);

        return builder.build();
    }

    @Override
    public String getQuery() {
        List<String> nestedFields = new ArrayList<>(childrenFieldlist);
        nestedFields.add(childrenIdFieldName);
        nestedFields.add(childrenModificationDateFieldName);
        SOQLBuilder builderNested = SOQLBuilder.getInstanceWithFields(nestedFields);
        builderNested.from(childrenTypePlural);

        List<String> fields = new ArrayList<>(parentFieldlist);
        fields.add(getIdFieldName());
        fields.add(getModificationDateFieldName());
        fields.add("(" + builderNested.build() + ")");

        SOQLBuilder builder = SOQLBuilder.getInstanceWithFields(fields);
        builder.from(parentType);
        builder.where(parentSoqlFilter);

        return builder.build();
    }

    @Override
    public long getLatestModificationTimeStamp(JSONArray records) throws JSONException {
        return super.getLatestModificationTimeStamp(records);
    }

    @Override
    public Set<String> getIdsToSkip(SyncManager syncManager, String soupName) throws JSONException {
        return super.getIdsToSkip(syncManager, soupName);
    }

    @Override
    public SortedSet<String> getDirtyRecordIds(SyncManager syncManager, String soupName, String idField) throws JSONException {
        return super.getDirtyRecordIds(syncManager, soupName, idField);
    }

    @Override
    public SortedSet<String> getNonDirtyRecordIds(SyncManager syncManager, String soupName, String idField) throws JSONException {
        return super.getNonDirtyRecordIds(syncManager, soupName, idField);
    }

    @Override
    public boolean isLocallyCreated(JSONObject record) throws JSONException {
        return super.isLocallyCreated(record);
    }

    @Override
    public boolean isLocallyUpdated(JSONObject record) throws JSONException {
        return super.isLocallyUpdated(record);
    }

    @Override
    public boolean isLocallyDeleted(JSONObject record) throws JSONException {
        return super.isLocallyDeleted(record);
    }

    @Override
    public void saveRecordsToLocalStore(SyncManager syncManager, String soupName, JSONArray records) throws JSONException {
        super.saveRecordsToLocalStore(syncManager, soupName, records);
    }

    @Override
    public void deleteRecordsFromLocalStore(SyncManager syncManager, String soupName, Set<String> ids) {
        super.deleteRecordsFromLocalStore(syncManager, soupName, ids);
    }
}
