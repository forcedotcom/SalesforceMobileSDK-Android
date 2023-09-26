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
package com.salesforce.androidsdk.mobilesync.target

import android.text.TextUtils
import android.text.TextUtils.join
import com.salesforce.androidsdk.mobilesync.manager.SyncManager
import com.salesforce.androidsdk.mobilesync.util.ChildrenInfo
import com.salesforce.androidsdk.mobilesync.util.ParentInfo
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import com.salesforce.androidsdk.smartstore.store.SmartSqlHelper
import com.salesforce.androidsdk.smartstore.store.SmartStore
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Shared code for ParentChildrenSyncDownTarget and ParentChildrenSyncUpTarget
 */
object ParentChildrenSyncTargetHelper {
    const val PARENT = "parent"
    const val CHILDREN = "children"
    const val RELATIONSHIP_TYPE = "relationshipType"

    @Throws(JSONException::class)
    fun saveRecordTreesToLocalStore(
        syncManager: SyncManager,
        target: SyncTarget,
        parentInfo: ParentInfo,
        childrenInfo: ChildrenInfo,
        recordTrees: JSONArray,
        syncId: Long
    ) {
        val smartStore = syncManager.smartStore
        synchronized(smartStore.database) {
            try {
                smartStore.beginTransaction()
                for (i in 0 until recordTrees.length()) {
                    val record = recordTrees.getJSONObject(i)
                    val parent = JSONObject(record.toString())

                    // Separating parent from children
                    val children = parent.remove(childrenInfo.sobjectTypePlural) as JSONArray

                    // Saving parent
                    target.addSyncId(parent, syncId)
                    target.cleanRecord(parent)
                    target.cleanAndSaveInSmartStore(
                        smartStore,
                        parentInfo.soupName,
                        parent,
                        parentInfo.idFieldName,
                        false
                    )

                    // Put server id of parent in children
                    for (j in 0 until children.length()) {
                        val child = children.getJSONObject(j)
                        child.put(
                            childrenInfo.parentIdFieldName,
                            parent[parentInfo.idFieldName]
                        )

                        // Saving child
                        target.addSyncId(child, syncId)
                        target.cleanRecord(child)
                        target.cleanAndSaveInSmartStore(
                            smartStore,
                            childrenInfo.soupName,
                            child,
                            childrenInfo.idFieldName,
                            false
                        )
                    }
                }
                smartStore.setTransactionSuccessful()
            } finally {
                smartStore.endTransaction()
            }
        }
    }

    fun getDirtyRecordIdsSql(
        parentInfo: ParentInfo,
        childrenInfo: ChildrenInfo,
        parentFieldToSelect: String
    ): String {
        return String.format(
            "SELECT DISTINCT {%s:%s} FROM {%s} WHERE {%s:%s} = 'true' OR EXISTS (SELECT {%s:%s} FROM {%s} WHERE {%s:%s} = {%s:%s} AND {%s:%s} = 'true')",
            parentInfo.soupName,
            parentFieldToSelect,
            parentInfo.soupName,
            parentInfo.soupName,
            SyncTarget.LOCAL,
            childrenInfo.soupName,
            childrenInfo.idFieldName,
            childrenInfo.soupName,
            childrenInfo.soupName,
            childrenInfo.parentIdFieldName,
            parentInfo.soupName,
            parentInfo.idFieldName,
            childrenInfo.soupName,
            SyncTarget.LOCAL
        )
    }

    fun getNonDirtyRecordIdsSql(
        parentInfo: ParentInfo,
        childrenInfo: ChildrenInfo,
        parentFieldToSelect: String,
        additionalPredicate: String
    ): String {
        return String.format(
            "SELECT DISTINCT {%s:%s} FROM {%s} WHERE {%s:%s} = 'false' %s AND NOT EXISTS (SELECT {%s:%s} FROM {%s} WHERE {%s:%s} = {%s:%s} AND {%s:%s} = 'true')",
            parentInfo.soupName,
            parentFieldToSelect,
            parentInfo.soupName,
            parentInfo.soupName,
            SyncTarget.LOCAL,
            additionalPredicate,
            childrenInfo.soupName,
            childrenInfo.idFieldName,
            childrenInfo.soupName,
            childrenInfo.soupName,
            childrenInfo.parentIdFieldName,
            parentInfo.soupName,
            parentInfo.idFieldName,
            childrenInfo.soupName,
            SyncTarget.LOCAL
        )
    }

    fun deleteChildrenFromLocalStore(
        smartStore: SmartStore,
        parentInfo: ParentInfo,
        childrenInfo: ChildrenInfo,
        vararg parentIds: String
    ) {
        val querySpec =
            getQueryForChildren(parentInfo, childrenInfo, SmartStore.SOUP_ENTRY_ID, *parentIds)
        smartStore.deleteByQuery(childrenInfo.soupName, querySpec)
    }

    @Throws(JSONException::class)
    fun getChildrenFromLocalStore(
        smartStore: SmartStore,
        parentInfo: ParentInfo,
        childrenInfo: ChildrenInfo,
        parent: JSONObject
    ): JSONArray {
        val querySpec = getQueryForChildren(
            parentInfo,
            childrenInfo,
            SmartSqlHelper.SOUP,
            parent.getString(parentInfo.idFieldName)
        )
        val rows = smartStore.query(querySpec, 0)
        val children = JSONArray()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONArray(i)
            children.put(row.getJSONObject(0))
        }
        return children
    }

    internal fun getQueryForChildren(
        parentInfo: ParentInfo,
        childrenInfo: ChildrenInfo,
        childFieldToSelect: String,
        vararg parentIds: String
    ): QuerySpec {
        val smartSql = String.format(
            "SELECT {%s:%s} FROM {%s},{%s} WHERE {%s:%s} = {%s:%s} AND {%s:%s} IN (%s)",
            childrenInfo.soupName, childFieldToSelect,
            childrenInfo.soupName, parentInfo.soupName,
            childrenInfo.soupName, childrenInfo.parentIdFieldName,
            parentInfo.soupName, parentInfo.idFieldName,
            parentInfo.soupName, parentInfo.idFieldName,
            "'${join("', '", parentIds)}'"
        )
        return QuerySpec.buildSmartQuerySpec(smartSql, Int.MAX_VALUE)
    }

    /**
     * Enum for relationship types
     */
    enum class RelationshipType {
        MASTER_DETAIL, LOOKUP
    }
}