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

import android.text.TextUtils;

import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartSqlHelper;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.mobilesync.manager.SyncManager;
import com.salesforce.androidsdk.mobilesync.util.ChildrenInfo;
import com.salesforce.androidsdk.mobilesync.util.ParentInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Shared code for ParentChildrenSyncDownTarget and ParentChildrenSyncUpTarget
 */

public class ParentChildrenSyncTargetHelper {

    public static final String PARENT = "parent";
    public static final String CHILDREN = "children";
    public static final String RELATIONSHIP_TYPE = "relationshipType";

    /**
     * Enum for relationship types
     */
    public enum RelationshipType {
        MASTER_DETAIL,
        LOOKUP;
    }

    public static void saveRecordTreesToLocalStore(SyncManager syncManager, SyncTarget target, ParentInfo parentInfo, ChildrenInfo childrenInfo, JSONArray recordTrees, long syncId) throws JSONException {
        SmartStore smartStore = syncManager.getSmartStore();
        synchronized(smartStore.getDatabase()) {
            try {
                smartStore.beginTransaction();
                for (int i = 0; i < recordTrees.length(); i++) {
                    JSONObject record = recordTrees.getJSONObject(i);
                    JSONObject parent = new JSONObject(record.toString());

                    // Separating parent from children
                    JSONArray children = (JSONArray) parent.remove(childrenInfo.sobjectTypePlural);

                    // Saving parent
                    target.addSyncId(parent, syncId);
                    target.cleanRecord(parent);
                    target.cleanAndSaveInSmartStore(smartStore, parentInfo.soupName, parent, parentInfo.idFieldName, false);

                    // Put server id of parent in children
                    if (children != null) {
                        for (int j = 0; j < children.length(); j++) {
                            JSONObject child = children.getJSONObject(j);
                            child.put(childrenInfo.parentIdFieldName, parent.get(parentInfo.idFieldName));

                            // Saving child
                            target.addSyncId(child, syncId);
                            target.cleanRecord(child);
                            target.cleanAndSaveInSmartStore(smartStore, childrenInfo.soupName, child, childrenInfo.idFieldName, false);
                        }
                    }
                }
                smartStore.setTransactionSuccessful();
            } finally {
                smartStore.endTransaction();
            }
        }
    }

    public static String getDirtyRecordIdsSql(ParentInfo parentInfo, ChildrenInfo childrenInfo, String parentFieldToSelect) {
        return String.format(
                "SELECT DISTINCT {%s:%s} FROM {%s} WHERE {%s:%s} = 'true' OR EXISTS (SELECT {%s:%s} FROM {%s} WHERE {%s:%s} = {%s:%s} AND {%s:%s} = 'true')",
                parentInfo.soupName, parentFieldToSelect, parentInfo.soupName, parentInfo.soupName, SyncTarget.LOCAL,
                childrenInfo.soupName, childrenInfo.idFieldName, childrenInfo.soupName, childrenInfo.soupName, childrenInfo.parentIdFieldName, parentInfo.soupName, parentInfo.idFieldName, childrenInfo.soupName, SyncTarget.LOCAL);
    }

    public static String getNonDirtyRecordIdsSql(ParentInfo parentInfo, ChildrenInfo childrenInfo, String parentFieldToSelect, String additionalPredicate) {
        return String.format(
                "SELECT DISTINCT {%s:%s} FROM {%s} WHERE {%s:%s} = 'false' %s AND NOT EXISTS (SELECT {%s:%s} FROM {%s} WHERE {%s:%s} = {%s:%s} AND {%s:%s} = 'true')",
                parentInfo.soupName, parentFieldToSelect, parentInfo.soupName, parentInfo.soupName, SyncTarget.LOCAL,
                additionalPredicate,
                childrenInfo.soupName, childrenInfo.idFieldName, childrenInfo.soupName, childrenInfo.soupName, childrenInfo.parentIdFieldName, parentInfo.soupName, parentInfo.idFieldName, childrenInfo.soupName, SyncTarget.LOCAL);
    }

    public static void deleteChildrenFromLocalStore(SmartStore smartStore, ParentInfo parentInfo, ChildrenInfo childrenInfo, String... parentIds) {
        QuerySpec querySpec = getQueryForChildren(parentInfo, childrenInfo, SmartStore.SOUP_ENTRY_ID, parentIds);
        smartStore.deleteByQuery(childrenInfo.soupName, querySpec);
    }

    public static JSONArray getChildrenFromLocalStore(SmartStore smartStore, ParentInfo parentInfo, ChildrenInfo childrenInfo, JSONObject parent) throws JSONException {
        QuerySpec  querySpec = getQueryForChildren(parentInfo, childrenInfo, SmartSqlHelper.SOUP, parent.getString(parentInfo.idFieldName));
        JSONArray rows = smartStore.query(querySpec, 0);
        JSONArray children = new JSONArray();
        for (int i=0; i<rows.length(); i++) {
            JSONArray row = rows.getJSONArray(i);
            children.put(row.getJSONObject(0));
        }
        return children;
    }

    protected static QuerySpec getQueryForChildren(ParentInfo parentInfo, ChildrenInfo childrenInfo, String childFieldToSelect, String... parentIds) {
        String smartSql = String.format(
                "SELECT {%s:%s} FROM {%s},{%s} WHERE {%s:%s} = {%s:%s} AND {%s:%s} IN (%s)",
                childrenInfo.soupName, childFieldToSelect,
                childrenInfo.soupName, parentInfo.soupName,
                childrenInfo.soupName, childrenInfo.parentIdFieldName,
                parentInfo.soupName, parentInfo.idFieldName,
                parentInfo.soupName, parentInfo.idFieldName,
                "'" + TextUtils.join("', '", parentIds) + "'");

        return QuerySpec.buildSmartQuerySpec(smartSql, Integer.MAX_VALUE);
    }
}
