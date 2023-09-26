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
import com.salesforce.androidsdk.mobilesync.app.Features
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager
import com.salesforce.androidsdk.mobilesync.manager.SyncManager
import com.salesforce.androidsdk.mobilesync.target.ParentChildrenSyncTargetHelper.RelationshipType
import com.salesforce.androidsdk.mobilesync.util.ChildrenInfo
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.ParentInfo
import com.salesforce.androidsdk.mobilesync.util.SOQLBuilder
import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Date
import kotlin.math.max

/**
 * Target for sync that downloads parent with children records
 */
open class ParentChildrenSyncDownTarget(
    private val parentInfo: ParentInfo,
    private val parentFieldlist: List<String>,
    private val parentSoqlFilter: String?,
    private val childrenInfo: ChildrenInfo,
    private val childrenFieldlist: List<String>,
    private val relationshipType: RelationshipType
) : SoqlSyncDownTarget(parentInfo.idFieldName, parentInfo.modificationDateFieldName, "") {

    /**
     * Construct ParentChildrenSyncDownTarget from parentType, childrenType etc
     */
    init {
        queryType = QueryType.parent_children
        MobileSyncSDKManager.getInstance()
            .registerUsedAppFeature(Features.FEATURE_RELATED_RECORDS)
    }

    /**
     * Construct ParentChildrenSyncDownTarget from json
     * @param target
     * @throws JSONException
     */
    constructor(target: JSONObject) : this(
        ParentInfo(target.getJSONObject(ParentChildrenSyncTargetHelper.PARENT)),
        JSONObjectHelper.toList<String>(target.optJSONArray(PARENT_FIELDLIST)),
        target.getString(PARENT_SOQL_FILTER),
        ChildrenInfo(target.getJSONObject(ParentChildrenSyncTargetHelper.CHILDREN)),
        JSONObjectHelper.toList<String>(target.optJSONArray(CHILDREN_FIELDLIST)),
        RelationshipType.valueOf(target.getString(ParentChildrenSyncTargetHelper.RELATIONSHIP_TYPE))
    )

    /**
     * @return json representation of target
     * @throws JSONException
     */
    @Throws(JSONException::class)
    override fun asJSON(): JSONObject {
        return with(super.asJSON()) {
            put(ParentChildrenSyncTargetHelper.PARENT, parentInfo.asJSON())
            put(PARENT_FIELDLIST, JSONArray(parentFieldlist))
            put(PARENT_SOQL_FILTER, parentSoqlFilter)
            put(ParentChildrenSyncTargetHelper.CHILDREN, childrenInfo.asJSON())
            put(CHILDREN_FIELDLIST, JSONArray(childrenFieldlist))
            put(ParentChildrenSyncTargetHelper.RELATIONSHIP_TYPE, relationshipType.name)
        }
    }

    // This is for clean re-sync ghosts
    //
    // This is the soql to identify parents
    override val soqlForRemoteIds: String
        get() {
            // This is for clean re-sync ghosts
            //
            // This is the soql to identify parents
            val fields: MutableList<String> = ArrayList()
            fields.add(idFieldName)
            val builder: SOQLBuilder = SOQLBuilder.getInstanceWithFields(fields)
            builder.from(parentInfo.sobjectType)
            builder.where(parentSoqlFilter)
            return builder.build()
        }
    // This is for clean re-sync ghosts
    //
    // This is the soql to identify children

    // We are doing
    //  select Id, (select Id from children) from Parents where soqlParentFilter
    // It could be better to do
    //  select Id from child where qualified-soqlParentFilter (e.g. if filter is Name = 'A' then we would use Parent.Name = 'A')
    // But "qualifying" parentSoqlFilter without parsing it could prove tricky

    // Nested query
    protected val soqlForRemoteChildrenIds: String
        get() {
            // This is for clean re-sync ghosts
            //
            // This is the soql to identify children

            // We are doing
            //  select Id, (select Id from children) from Parents where soqlParentFilter
            // It could be better to do
            //  select Id from child where qualified-soqlParentFilter (e.g. if filter is Name = 'A' then we would use Parent.Name = 'A')
            // But "qualifying" parentSoqlFilter without parsing it could prove tricky

            // Nested query
            val nestedFields: MutableList<String> = ArrayList()
            nestedFields.add(childrenInfo.idFieldName)
            val builderNested: SOQLBuilder =
                SOQLBuilder.getInstanceWithFields(nestedFields)
            builderNested.from(childrenInfo.sobjectTypePlural)

            // Parent query
            val fields: MutableList<String> = ArrayList()
            fields.add(idFieldName)
            fields.add("(${builderNested.build()})")
            val builder: SOQLBuilder = SOQLBuilder.getInstanceWithFields(fields)
            builder.from(parentInfo.sobjectType)
            builder.where(parentSoqlFilter)
            return builder.build()
        }

    @Throws(JSONException::class, IOException::class)
    override fun cleanGhosts(syncManager: SyncManager, soupName: String, syncId: Long): Int {
        // Taking care of ghost parents
        val localIdsSize = super.cleanGhosts(syncManager, soupName, syncId)

        // Taking care of ghost children

        // NB: ParentChildrenSyncDownTarget's getNonDirtyRecordIdsSql does a join between parent and children soups
        // We only want to look at the children soup, so using SoqlSyncDownTarget's getNonDirtyRecordIdsSql
        val localChildrenIds: MutableSet<String> = getIdsWithQuery(
            syncManager, super.getNonDirtyRecordIdsSql(
                childrenInfo.soupName,
                childrenInfo.idFieldName,
                buildSyncIdPredicateIfIndexed(syncManager, childrenInfo.soupName, syncId)
            )
        )
        val remoteChildrenIds = getChildrenRemoteIdsWithSoql(syncManager, soqlForRemoteChildrenIds)
        localChildrenIds.removeAll(remoteChildrenIds)
        if (localChildrenIds.size > 0) {
            deleteRecordsFromLocalStore(
                syncManager,
                childrenInfo.soupName,
                localChildrenIds,
                childrenInfo.idFieldName
            )
        }
        return localIdsSize
    }

    @Throws(IOException::class, JSONException::class)
    protected fun getChildrenRemoteIdsWithSoql(
        syncManager: SyncManager,
        soqlForChildrenRemoteIds: String
    ): Set<String> {

        // Makes network request and parses the response.
        var records = startFetch(syncManager, soqlForChildrenRemoteIds)
        val remoteChildrenIds: MutableSet<String> = HashSet(parseChildrenIdsFromResponse(records))
        while (records != null) {
            syncManager.checkAcceptingSyncs()
            // Fetch next records, if any.
            records = continueFetch(syncManager) ?: break
            remoteChildrenIds.addAll(parseIdsFromResponse(records))
        }
        return remoteChildrenIds
    }

    protected fun parseChildrenIdsFromResponse(records: JSONArray): Set<String> {
        return with(HashSet<String>()) {
            JSONObjectHelper
                .toList<JSONObject>(records)
                .forEach { record ->
                    val childrenRecords =
                        record.optJSONArray(childrenInfo.sobjectTypePlural) ?: JSONArray()
                    this.addAll(parseIdsFromResponse(childrenRecords))
                }
            this
        }
    }

    override fun getQuery(maxTimeStamp: Long): String {
        val childrenWhere = StringBuilder()
        val parentWhere = StringBuilder()
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
            childrenWhere.append(
                buildModificationDateFilter(
                    childrenInfo.modificationDateFieldName,
                    maxTimeStamp
                )
            )
            parentWhere.append(buildModificationDateFilter(modificationDateFieldName, maxTimeStamp))
                .append(if (parentSoqlFilter.isNullOrEmpty()) "" else " and ")
        }
        parentWhere.append(parentSoqlFilter)

        // Nested query
        val nestedFields: MutableList<String> = ArrayList(childrenFieldlist)
        if (!nestedFields.contains(childrenInfo.idFieldName)) nestedFields.add(childrenInfo.idFieldName)
        if (!nestedFields.contains(childrenInfo.modificationDateFieldName)) nestedFields.add(
            childrenInfo.modificationDateFieldName
        )
        val builderNested: SOQLBuilder = SOQLBuilder.getInstanceWithFields(nestedFields)
        builderNested.from(childrenInfo.sobjectTypePlural)
        builderNested.where(childrenWhere.toString())

        // Parent query
        val fields: MutableList<String> = ArrayList(parentFieldlist)
        if (!fields.contains(idFieldName)) fields.add(idFieldName)
        if (!fields.contains(modificationDateFieldName)) fields.add(modificationDateFieldName)
        fields.add("(" + builderNested.build() + ")")
        val builder: SOQLBuilder = SOQLBuilder.getInstanceWithFields(fields)
        builder.from(parentInfo.sobjectType)
        builder.where(parentWhere.toString())
        builder.orderBy(parentInfo.modificationDateFieldName)
        return builder.build()
    }

    override val isSyncDownSortedByLatestModification: Boolean
        get() = true

    private fun buildModificationDateFilter(
        modificationDateFieldName: String?,
        maxTimeStamp: Long
    ): StringBuilder {
        val filter = StringBuilder()
        filter.append(modificationDateFieldName)
            .append(" > ")
            .append(Constants.TIMESTAMP_FORMAT.format(Date(maxTimeStamp)))
        return filter
    }

    @Throws(JSONException::class)
    override fun getRecordsFromResponseJson(responseJson: JSONObject): JSONArray {
        val records = responseJson.getJSONArray(Constants.RECORDS)
        for (i in 0 until records.length()) {
            val record = records.getJSONObject(i)
            val childrenRecords =
                if (record.has(childrenInfo.sobjectTypePlural) && !record.isNull(
                        childrenInfo.sobjectTypePlural
                    )
                ) record.getJSONObject(childrenInfo.sobjectTypePlural).getJSONArray(
                    Constants.RECORDS
                ) else JSONArray()
            // Cleaning up record
            record.put(childrenInfo.sobjectTypePlural, childrenRecords)
            // XXX what if not all children were fetched
        }
        return records
    }

    @Throws(JSONException::class)
    override fun getLatestModificationTimeStamp(records: JSONArray): Long {
        // NB: method is called during sync down so for this target records contain parent and children

        // Compute max time stamp of parents
        var maxTimeStamp = super.getLatestModificationTimeStamp(records)

        // Compute max time stamp of parents and children
        for (i in 0 until records.length()) {
            val record = records.getJSONObject(i)
            val children = record.getJSONArray(childrenInfo.sobjectTypePlural)
            maxTimeStamp = max(
                maxTimeStamp,
                getLatestModificationTimeStamp(children, childrenInfo.modificationDateFieldName)
            )
        }
        return maxTimeStamp
    }

    public override fun getDirtyRecordIdsSql(soupName: String, idField: String): String {
        return ParentChildrenSyncTargetHelper.getDirtyRecordIdsSql(
            parentInfo,
            childrenInfo,
            idField
        )
    }

    override fun getNonDirtyRecordIdsSql(
        soupName: String,
        idField: String,
        additionalPredicate: String
    ): String {
        return ParentChildrenSyncTargetHelper.getNonDirtyRecordIdsSql(
            parentInfo,
            childrenInfo,
            idField,
            additionalPredicate
        )
    }

    @Throws(JSONException::class)
    override fun saveRecordsToLocalStore(
        syncManager: SyncManager,
        soupName: String,
        records: JSONArray,
        syncId: Long
    ) {
        // NB: method is called during sync down so for this target records contain parent and children
        ParentChildrenSyncTargetHelper.saveRecordTreesToLocalStore(
            syncManager,
            this,
            parentInfo,
            childrenInfo,
            records,
            syncId
        )
    }

    companion object {
        const val PARENT_FIELDLIST = "parentFieldlist"
        const val PARENT_SOQL_FILTER = "parentSoqlFilter"
        const val CHILDREN_FIELDLIST = "childrenFieldlist"
    }
}