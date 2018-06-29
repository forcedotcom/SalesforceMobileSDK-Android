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

import com.salesforce.androidsdk.smartsync.app.Features;
import com.salesforce.androidsdk.smartsync.app.SmartSyncSDKManager;
import com.salesforce.androidsdk.smartsync.manager.SyncManager;
import com.salesforce.androidsdk.smartsync.target.ParentChildrenSyncTargetHelper.RelationshipType;
import com.salesforce.androidsdk.smartsync.util.ChildrenInfo;
import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.androidsdk.smartsync.util.ParentInfo;
import com.salesforce.androidsdk.smartsync.util.SOQLBuilder;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Target for sync that downloads parent with children records
 */
public class ParentChildrenSyncDownTarget extends SoqlSyncDownTarget {

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
     * Construct ParentChildrenSyncDownTarget from json
     * @param target
     * @throws JSONException
     */
    public ParentChildrenSyncDownTarget(JSONObject target) throws JSONException {
        this(
                new ParentInfo(target.getJSONObject(ParentChildrenSyncTargetHelper.PARENT)),
                JSONObjectHelper.<String>toList(target.optJSONArray(PARENT_FIELDLIST)),
                target.getString(PARENT_SOQL_FILTER),
                new ChildrenInfo(target.getJSONObject(ParentChildrenSyncTargetHelper.CHILDREN)),
                JSONObjectHelper.<String>toList(target.optJSONArray(CHILDREN_FIELDLIST)),
                RelationshipType.valueOf(target.getString(ParentChildrenSyncTargetHelper.RELATIONSHIP_TYPE))
        );
    }

    /**
     * Construct ParentChildrenSyncDownTarget from parentType, childrenType etc
     */
    public ParentChildrenSyncDownTarget(ParentInfo parentInfo, List<String> parentFieldlist, String parentSoqlFilter, ChildrenInfo childrenInfo, List<String> childrenFieldlist, RelationshipType relationshipType) {
        super(parentInfo.idFieldName, parentInfo.modificationDateFieldName, null);
        this.queryType = QueryType.parent_children;
        this.parentInfo = parentInfo;
        this.parentFieldlist = parentFieldlist;
        this.parentSoqlFilter = parentSoqlFilter;
        this.childrenInfo = childrenInfo;
        this.childrenFieldlist = childrenFieldlist;
        this.relationshipType = relationshipType;
        SmartSyncSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_RELATED_RECORDS);
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
        target.put(ParentChildrenSyncTargetHelper.PARENT, parentInfo.asJSON());
        target.put(PARENT_FIELDLIST, new JSONArray(parentFieldlist));
        target.put(PARENT_SOQL_FILTER, parentSoqlFilter);
        target.put(ParentChildrenSyncTargetHelper.CHILDREN, childrenInfo.asJSON());
        target.put(CHILDREN_FIELDLIST, new JSONArray(childrenFieldlist));
        target.put(ParentChildrenSyncTargetHelper.RELATIONSHIP_TYPE, relationshipType.name());
        return target;
    }

    @Override
    protected String getSoqlForRemoteIds() {
        // This is for clean re-sync ghosts
        //
        // This is the soql to identify parents

        List<String> fields = new ArrayList<>();
        fields.add(getIdFieldName());
        SOQLBuilder builder = SOQLBuilder.getInstanceWithFields(fields);
        builder.from(parentInfo.sobjectType);
        builder.where(parentSoqlFilter);

        return builder.build();
    }

    protected String getSoqlForRemoteChildrenIds() {
        // This is for clean re-sync ghosts
        //
        // This is the soql to identify children

        // We are doing
        //  select Id, (select Id from children) from Parents where soqlParentFilter
        // It could be better to do
        //  select Id from child where qualified-soqlParentFilter (e.g. if filter is Name = 'A' then we would use Parent.Name = 'A')
        // But "qualifying" parentSoqlFilter without parsing it could prove tricky

        // Nested query
        List<String> nestedFields = new ArrayList<>();
        nestedFields.add(childrenInfo.idFieldName);
        SOQLBuilder builderNested = SOQLBuilder.getInstanceWithFields(nestedFields);
        builderNested.from(childrenInfo.sobjectTypePlural);

        // Parent query
        List<String> fields = new ArrayList<>();
        fields.add(getIdFieldName());
        fields.add("(" + builderNested.build() + ")");
        SOQLBuilder builder = SOQLBuilder.getInstanceWithFields(fields);
        builder.from(parentInfo.sobjectType);
        builder.where(parentSoqlFilter);

        return builder.build();
    }


    @Override
    public int cleanGhosts(SyncManager syncManager, String soupName, long syncId) throws JSONException, IOException {
        // Taking care of ghost parents
        int localIdsSize = super.cleanGhosts(syncManager, soupName, syncId);

        // Taking care of ghost children

        // NB: ParentChildrenSyncDownTarget's getNonDirtyRecordIdsSql does a join between parent and children soups
        // We only want to look at the children soup, so using SoqlSyncDownTarget's getNonDirtyRecordIdsSql
        final Set<String> localChildrenIds = getIdsWithQuery(syncManager, super.getNonDirtyRecordIdsSql(childrenInfo.soupName, childrenInfo.idFieldName, buildSyncIdPredicateIfIndexed(syncManager, childrenInfo.soupName, syncId)));
        final Set<String> remoteChildrenIds = getChildrenRemoteIdsWithSoql(syncManager, getSoqlForRemoteChildrenIds());
        if (remoteChildrenIds != null) {
            localChildrenIds.removeAll(remoteChildrenIds);
        }
        if (localChildrenIds.size() > 0) {
            deleteRecordsFromLocalStore(syncManager, childrenInfo.soupName, localChildrenIds, childrenInfo.idFieldName);
        }
        return localIdsSize;
    }

    protected Set<String> getChildrenRemoteIdsWithSoql(SyncManager syncManager, String soqlForChildrenRemoteIds) throws IOException, JSONException {

        // Makes network request and parses the response.
        JSONArray records = startFetch(syncManager, soqlForChildrenRemoteIds);
        final Set<String> remoteChildrenIds = new HashSet<>(parseChildrenIdsFromResponse(records));
        while (records != null) {

            // Fetch next records, if any.
            records = continueFetch(syncManager);
            remoteChildrenIds.addAll(parseIdsFromResponse(records));
        }
        return remoteChildrenIds;
    }

    protected Set<String> parseChildrenIdsFromResponse(JSONArray records) {
        final Set<String> remoteChildrenIds = new HashSet<>();
        if (records != null) {
            for (int i = 0; i < records.length(); i++) {
                final JSONObject record = records.optJSONObject(i);
                if (record != null) {
                    JSONArray childrenRecords = record.optJSONArray(childrenInfo.sobjectTypePlural);
                    remoteChildrenIds.addAll(parseIdsFromResponse(childrenRecords));
                }
            }
        }
        return remoteChildrenIds;
    }


    @Override
    public String getQuery(long maxTimeStamp) {
        StringBuilder childrenWhere = new StringBuilder();
        StringBuilder parentWhere = new StringBuilder();
        if (maxTimeStamp > 0) {
            // This is for re-sync
            //
            // Ideally we should target parent-children 'groups' where the parent changed or a child changed
            //
            // But that is not possible with SOQL:
            //   select fields, (select childrenFields from children where lastModifiedDate > xxx)
            //   from parent
            //   where lastModifiedDate > xxx
            //   or Id in (select parent-id from children where lastModifiedDate > xxx)
            // Gives the following error: semi join sub-selects are not allowed with the 'OR' operator
            //
            // Also if we do:
            //   select fields, (select childrenFields from children where lastModifiedDate > xxx)
            //   from parent
            //   where Id in (select parent-id from children where lastModifiedDate > xxx or parent.lastModifiedDate > xxx)
            // Then we miss parents without children
            //
            // So we target parent-children 'goups' where the parent changed
            // And we only download the changed children
            childrenWhere.append(buildModificationDateFilter(childrenInfo.modificationDateFieldName, maxTimeStamp));
            parentWhere.append(buildModificationDateFilter(getModificationDateFieldName(), maxTimeStamp))
                    .append(TextUtils.isEmpty(parentSoqlFilter) ? "" : " and ");
        }
        parentWhere.append(parentSoqlFilter);

        // Nested query
        List<String> nestedFields = new ArrayList<>(childrenFieldlist);
        if (!nestedFields.contains(childrenInfo.idFieldName)) nestedFields.add(childrenInfo.idFieldName);
        if (!nestedFields.contains(childrenInfo.modificationDateFieldName)) nestedFields.add(childrenInfo.modificationDateFieldName);
        SOQLBuilder builderNested = SOQLBuilder.getInstanceWithFields(nestedFields);
        builderNested.from(childrenInfo.sobjectTypePlural);
        builderNested.where(childrenWhere.toString());

        // Parent query
        List<String> fields = new ArrayList<>(parentFieldlist);
        if (!fields.contains(getIdFieldName())) fields.add(getIdFieldName());
        if (!fields.contains(getModificationDateFieldName())) fields.add(getModificationDateFieldName());
        fields.add("(" + builderNested.build() + ")");
        SOQLBuilder builder = SOQLBuilder.getInstanceWithFields(fields);
        builder.from(parentInfo.sobjectType);
        builder.where(parentWhere.toString());
        return builder.build();
    }

    private StringBuilder buildModificationDateFilter(String modificationDateFieldName, long maxTimeStamp) {
        StringBuilder filter = new StringBuilder();
        filter.append(modificationDateFieldName)
                .append(" > ")
                .append(Constants.TIMESTAMP_FORMAT.format(new Date(maxTimeStamp)));
        return filter;
    }

    @Override
    protected JSONArray getRecordsFromResponseJson(JSONObject responseJson) throws JSONException {
        JSONArray records = responseJson.getJSONArray(Constants.RECORDS);
        for (int i=0; i<records.length(); i++) {
            JSONObject record = records.getJSONObject(i);
            JSONArray childrenRecords = (record.has(childrenInfo.sobjectTypePlural) && !record.isNull(childrenInfo.sobjectTypePlural)
                    ? record.getJSONObject(childrenInfo.sobjectTypePlural).getJSONArray(Constants.RECORDS)
                    : new JSONArray());
            // Cleaning up record
            record.put(childrenInfo.sobjectTypePlural, childrenRecords);
            // XXX what if not all children were fetched
        }
        return records;
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
        return ParentChildrenSyncTargetHelper.getDirtyRecordIdsSql(parentInfo, childrenInfo, idField);
    }

    @Override
    protected String getNonDirtyRecordIdsSql(String soupName, String idField, String additionalPredicate) {
        return ParentChildrenSyncTargetHelper.getNonDirtyRecordIdsSql(parentInfo, childrenInfo, idField, additionalPredicate);
    }

    @Override
    public void saveRecordsToLocalStore(SyncManager syncManager, String soupName, JSONArray records, long syncId) throws JSONException {
        // NB: method is called during sync down so for this target records contain parent and children
        ParentChildrenSyncTargetHelper.saveRecordTreesToLocalStore(syncManager, this, parentInfo, childrenInfo, records, syncId);
    }
}
