/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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

import com.salesforce.androidsdk.mobilesync.manager.SyncManager
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget.QueryType.briefcase
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget.QueryType.custom
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget.QueryType.layout
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget.QueryType.metadata
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget.QueryType.mru
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget.QueryType.parent_children
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget.QueryType.refresh
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget.QueryType.soql
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget.QueryType.sosl
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.MobileSyncLogger
import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.SortedSet
import kotlin.math.max

/**
 * Target for sync down:
 * - what records to download from server
 * - how to download those records
 */
abstract class SyncDownTarget : SyncTarget {
    /**
     * @return QueryType of this target
     */
    // Fields
    @JvmField
    var queryType: QueryType? = null

    /**
     * @return number of records expected to be fetched - is set when startFetch() is called
     */
    @JvmField
    var totalSize = 0 // set during a fetch

    /**
     * Construct SyncDownTarget
     */
    constructor() : super()
    constructor(idFieldName: String?, modificationDateFieldName: String?) : super(
        idFieldName,
        modificationDateFieldName
    )

    /**
     * Construct SyncDownTarget from json
     * @param target
     * @throws JSONException
     */
    constructor(target: JSONObject) : super(target) {
        queryType = QueryType.valueOf(target.getString(QUERY_TYPE))
    }

    /**
     * @return json representation of target
     * @throws JSONException
     */
    @Throws(JSONException::class)
    override fun asJSON(): JSONObject {
        return with(super.asJSON()) {
            queryType?.let { put(QUERY_TYPE, it.name) }
            this
        }
    }

    /**
     * Start fetching records conforming to target
     * If a value for maxTimeStamp greater than 0 is passed in, only records created/modified after maxTimeStamp should be returned
     * @param syncManager
     * @param maxTimeStamp
     * @throws IOException, JSONException
     */
    @Throws(IOException::class, JSONException::class)
    abstract fun startFetch(syncManager: SyncManager, maxTimeStamp: Long): JSONArray?

    /**
     * Continue fetching records conforming to target if any
     * @param syncManager
     * @return null if there are no more records to fetch
     * @throws IOException, JSONException
     */
    @Throws(IOException::class, JSONException::class)
    abstract fun continueFetch(syncManager: SyncManager): JSONArray?

    /**
     * Delete from local store records that a full sync down would no longer download
     * @param syncManager
     * @param soupName
     * @param syncId
     * @return
     * @throws JSONException, IOException
     */
    @Throws(JSONException::class, IOException::class)
    open fun cleanGhosts(syncManager: SyncManager, soupName: String, syncId: Long): Int {

        // Fetches list of IDs present in local soup that have not been modified locally.
        val localIds: MutableSet<String> = getNonDirtyRecordIds(
            syncManager, soupName, idFieldName,
            buildSyncIdPredicateIfIndexed(syncManager, soupName, syncId)
        )

        // Fetches list of IDs still present on the server from the list of local IDs
        // and removes the list of IDs that are still present on the server.
        val remoteIds = getRemoteIds(syncManager, localIds)
        localIds.removeAll(remoteIds)

        // Deletes extra IDs from SmartStore.
        val localIdSize = localIds.size
        if (localIdSize > 0) {
            deleteRecordsFromLocalStore(syncManager, soupName, localIds, idFieldName)
        }
        return localIdSize
    }

    /**
     * Return predicate to target records with this sync id if there is an index on __sync_id__
     * @param syncManager
     * @param soupName
     * @param syncId
     * @return
     */
    protected fun buildSyncIdPredicateIfIndexed(
        syncManager: SyncManager,
        soupName: String,
        syncId: Long
    ): String {
        var additionalPredicate = ""
        val indexSpecs = syncManager.smartStore.getSoupIndexSpecs(soupName)
        for (indexSpec in indexSpecs) {
            if (indexSpec.path == SYNC_ID) {
                additionalPredicate = String.format(
                    Locale.US,
                    "AND {%s:%s} = %d",
                    soupName,
                    SYNC_ID,
                    syncId
                )
                break
            }
        }
        return additionalPredicate
    }

    /**
     * Return ids of non-dirty records (records NOT locally created/updated or deleted)
     * @param syncManager
     * @param soupName
     * @param idField
     * @param additionalPredicate
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    fun getNonDirtyRecordIds(
        syncManager: SyncManager, soupName: String,
        idField: String, additionalPredicate: String
    ): SortedSet<String> {
        val nonDirtyRecordsSql = getNonDirtyRecordIdsSql(soupName, idField, additionalPredicate)
        return getIdsWithQuery(syncManager, nonDirtyRecordsSql)
    }

    /**
     * Return SmartSQL to identify non-dirty records
     * @param soupName
     * @param idField
     * @param additionalPredicate
     * @return
     */
    protected open fun getNonDirtyRecordIdsSql(
        soupName: String,
        idField: String,
        additionalPredicate: String
    ): String {
        return String.format(
            "SELECT {%s:%s} FROM {%s} WHERE {%s:%s} = 'false' %s ORDER BY {%s:%s} ASC",
            soupName,
            idField,
            soupName,
            soupName,
            LOCAL,
            additionalPredicate,
            soupName,
            idField
        )
    }

    /**
     * Fetches remote IDs still present on the server from the list of local IDs.
     *
     * @param syncManager SyncManager instance.
     * @return List of IDs still present on the server.
     */
    @Throws(IOException::class, JSONException::class)
    protected abstract fun getRemoteIds(
        syncManager: SyncManager,
        localIds: Set<String>
    ): Set<String>

    /**
     * Gets the latest modification timestamp from the array of records.
     * @param records
     * @return latest modification time stamp
     * @throws JSONException
     */
    @Throws(JSONException::class)
    open fun getLatestModificationTimeStamp(records: JSONArray): Long {
        return getLatestModificationTimeStamp(records, modificationDateFieldName)
    }

    /**
     * When sync down fetches records from older to newer, the maxTimeStamp for the sync
     * can be updated throughout the sync, and as a result running a paused (or killed) sync
     * does not refetch all records.
     * @return true if sync down is sorted by latest modification time stamp
     */
    open val isSyncDownSortedByLatestModification: Boolean = false

    /**
     * Gets the latest modification timestamp from the array of records.
     * @param records
     * @param modifiedDateFieldName
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun getLatestModificationTimeStamp(
        records: JSONArray,
        modifiedDateFieldName: String?
    ): Long {
        var maxTimeStamp: Long = -1
        for (i in 0 until records.length()) {
            val timeStampStr =
                JSONObjectHelper.optString(records.getJSONObject(i), modifiedDateFieldName)
            if (timeStampStr == null) {
                maxTimeStamp = -1
                break // field not present
            }
            try {
                val timeStamp = Constants.TIMESTAMP_FORMAT.parse(timeStampStr)?.time ?: -1
                maxTimeStamp = max(timeStamp, maxTimeStamp)
            } catch (e: Exception) {
                MobileSyncLogger.d(
                    TAG,
                    "Could not parse modification date field: $modifiedDateFieldName",
                    e
                )
                maxTimeStamp = -1
                break
            }
        }
        return maxTimeStamp
    }

    /**
     * Return ids of records that should not be written over
     * during a sync down with merge mode leave-if-changed
     * @return set of ids
     * @throws JSONException
     */
    @Throws(JSONException::class)
    open fun getIdsToSkip(syncManager: SyncManager, soupName: String): Set<String> {
        return getDirtyRecordIds(syncManager, soupName, idFieldName)
    }

    /**
     * Enum for query type.
     */
    enum class QueryType {
        mru, sosl, soql, refresh, parent_children, custom, metadata, layout, briefcase
    }

    /**
     * Helper method to parse IDs from a network response to a SOQL query.
     *
     * @param records SObject records.
     * @return Set of IDs.
     */
    protected fun parseIdsFromResponse(records: JSONArray): Set<String> {
        return with(HashSet<String>()) {
            JSONObjectHelper
                .toList<JSONObject>(records)
                .forEach { idJson ->
                    this.add(idJson.optString(idFieldName))
                }
            this
        }
    }

    companion object {
        // Constants
        private const val TAG = "SyncDownTarget"
        const val QUERY_TYPE = "type"

        /**
         * Build SyncDownTarget from json
         * @param target as json
         * @return
         * @throws JSONException
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun fromJSON(target: JSONObject): SyncDownTarget {
            return when (QueryType.valueOf(target.getString(QUERY_TYPE))) {
                mru -> {
                    MruSyncDownTarget(target)
                }

                sosl -> {
                    SoslSyncDownTarget(target)
                }

                soql -> {
                    SoqlSyncDownTarget(target)
                }

                refresh -> {
                    RefreshSyncDownTarget(target)
                }

                parent_children -> {
                    ParentChildrenSyncDownTarget(target)
                }

                metadata -> {
                    MetadataSyncDownTarget(target)
                }

                layout -> {
                    LayoutSyncDownTarget(target)
                }

                briefcase -> {
                    BriefcaseSyncDownTarget(target)
                }

                custom -> {
                    try {
                        val implClass =
                            Class.forName(target.getString(ANDROID_IMPL)) as Class<out SyncDownTarget>
                        val constructor = implClass.getConstructor(
                            JSONObject::class.java
                        )
                        constructor.newInstance(target)
                    } catch (e: Exception) {
                        throw RuntimeException(e)
                    }
                }
            }
        }
    }
}