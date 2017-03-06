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
import android.util.Log;

import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartSqlHelper;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartsync.util.ChildrenInfo;

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

    public static String getDirtyRecordIdsSql(String soupName, String idField, ChildrenInfo childrenInfo) {
        return String.format(
                "SELECT DISTINCT {%s:%s} FROM {%s},{%s} WHERE {%s:%s} = {%s:%s} AND ({%s:%s} = 'true' OR {%s:%s} = 'true')",
                soupName, idField,
                childrenInfo.soupName, soupName,
                childrenInfo.soupName, childrenInfo.parentLocalIdFieldName,
                soupName, SmartStore.SOUP_ENTRY_ID,
                soupName, SyncTarget.LOCAL,
                childrenInfo.soupName, SyncTarget.LOCAL);
    }

    public static String getNonDirtyRecordIdsSql(String soupName, String idField, ChildrenInfo childrenInfo) {
        return String.format(
                "SELECT {%s:%s} FROM {%s} WHERE {%s:%s} NOT IN (%s)",
                soupName, idField,
                soupName,
                soupName, SmartStore.SOUP_ENTRY_ID,
                getDirtyRecordIdsSql(soupName, SmartStore.SOUP_ENTRY_ID, childrenInfo)
        );
    }

    public static void deleteChildrenFromLocalStore(SmartStore smartStore, String soupName, String[] ids, String idField, ChildrenInfo childrenInfo) {
        QuerySpec querySpec = getQueryForChildren(soupName, SmartStore.SOUP_ENTRY_ID, ids, idField, childrenInfo);
        smartStore.deleteByQuery(childrenInfo.soupName, querySpec);
    }

    public static JSONArray getChildrenFromLocalStore(SmartStore smartStore, String soupName, JSONObject parent, ChildrenInfo childrenInfo) throws JSONException {
        QuerySpec  querySpec = getQueryForChildren(soupName, SmartSqlHelper.SOUP, new String[] {parent.getString(SmartStore.SOUP_ENTRY_ID)}, SmartStore.SOUP_ENTRY_ID, childrenInfo);
        JSONArray rows = smartStore.query(querySpec, 0);
        JSONArray children = new JSONArray();
        for (int i=0; i<rows.length(); i++) {
            JSONArray row = rows.getJSONArray(i);
            children.put(row.getJSONObject(0));
        }
        return children;
    }

    protected static QuerySpec getQueryForChildren(String soupName, String fieldToSelect, String[] ids, String idField, ChildrenInfo childrenInfo) {
        String smartSql = String.format(
                "SELECT {%s:%s} FROM {%s},{%s} WHERE {%s:%s} = {%s:%s} AND {%s:%s} IN (%s)",
                childrenInfo.soupName, fieldToSelect,
                childrenInfo.soupName, soupName,
                childrenInfo.soupName, childrenInfo.parentLocalIdFieldName,
                soupName, SmartStore.SOUP_ENTRY_ID,
                soupName, idField,
                "'" + TextUtils.join("', '", ids) + "'");

        return QuerySpec.buildSmartQuerySpec(smartSql, Integer.MAX_VALUE);
    }


}
