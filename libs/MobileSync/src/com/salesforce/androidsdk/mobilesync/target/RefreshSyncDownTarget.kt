/*
 * Copyright (c) 2016-present, salesforce.com, inc.
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
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.SOQLBuilder
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Date
import kotlin.math.ceil
import kotlin.math.min

/**
 * Target for sync which syncs down the records currently in a soup
 */
open class RefreshSyncDownTarget internal constructor(
    fieldlist: List<String>,
    objectType: String,
    soupName: String,
    countIdsPerSoql: Int
) : SyncDownTarget() {
    /**
     * @return field list for this target
     */
    val fieldlist: List<String>

    /**
     * @return object type for this target
     */
    val objectType: String
    private val soupName: String
    private val countIdsPerSoql: Int

    // NB: SOQL query length limit is 100k but not over REST
    //     Too many ids will cause a "414 - URI Too Long" error
    // NB: For each sync run - a fresh sync down target is created (by deserializing it from smartstore)
    // The following members are specific to a run
    // page will change during a run as we call start/continueFetch
    private var isResync = false
    private var page = 0

    /**
     * Construct RefreshSyncDownTarget from json
     * @param target
     * @throws JSONException
     */
    constructor(target: JSONObject) : this(
        JSONObjectHelper.toList<String>(target.getJSONArray(FIELDLIST)),
        target.getString(SOBJECT_TYPE),
        target.getString(SOUP_NAME),
        target.optInt(COUNT_IDS_PER_SOQL, MAX_COUNT_IDS_PER_SOQL)
    )

    /**
     * Constructor
     * @param fieldlist
     * @param objectType
     */
    constructor(fieldlist: List<String>, objectType: String, soupName: String) : this(
        fieldlist,
        objectType,
        soupName,
        MAX_COUNT_IDS_PER_SOQL
    )

    init {
        queryType = QueryType.refresh
        this.fieldlist = fieldlist
        this.objectType = objectType
        this.soupName = soupName
        this.countIdsPerSoql = min(countIdsPerSoql, MAX_COUNT_IDS_PER_SOQL)
    }

    /**
     * @return json representation of target
     * @throws JSONException
     */
    @Throws(JSONException::class)
    override fun asJSON(): JSONObject {
        return with(super.asJSON()) {
            put(FIELDLIST, JSONArray(fieldlist))
            put(SOBJECT_TYPE, objectType)
            put(SOUP_NAME, soupName)
            put(COUNT_IDS_PER_SOQL, countIdsPerSoql)
        }
    }

    @Throws(IOException::class, JSONException::class)
    override fun startFetch(syncManager: SyncManager, maxTimeStamp: Long): JSONArray? {
        // During reSync, we can't make use of the maxTimeStamp that was captured during last refresh
        // since we expect records to have been fetched from the server and written to the soup directly outside a sync down operation
        // Instead during a reSymc, we compute maxTimeStamp from the records in the soup
        isResync = maxTimeStamp > 0
        return getIdsFromSmartStoreAndFetchFromServer(syncManager)
    }

    @Throws(IOException::class, JSONException::class)
    override fun continueFetch(syncManager: SyncManager): JSONArray? {
        return if (page > 0) getIdsFromSmartStoreAndFetchFromServer(syncManager) else null
    }

    @Throws(IOException::class, JSONException::class)
    private fun getIdsFromSmartStoreAndFetchFromServer(syncManager: SyncManager): JSONArray? {
        // Read from smartstore
        val querySpec: QuerySpec
        val idsInSmartStore: MutableList<String> = ArrayList()
        val maxTimeStamp: Long
        if (isResync) {
            // Getting full records from SmartStore to compute maxTimeStamp
            // So doing more db work in the hope of doing less server work
            querySpec = QuerySpec.buildAllQuerySpec(
                soupName,
                idFieldName,
                QuerySpec.Order.ascending,
                countIdsPerSoql
            )
            val recordsFromSmartStore = syncManager.smartStore.query(querySpec, page)

            // Compute max time stamp
            maxTimeStamp = getLatestModificationTimeStamp(recordsFromSmartStore)

            // Get ids
            for (i in 0 until recordsFromSmartStore.length()) {
                idsInSmartStore.add(recordsFromSmartStore.getJSONObject(i).getString(idFieldName))
            }
        } else {
            querySpec = QuerySpec.buildSmartQuerySpec(
                "SELECT {$soupName:$idFieldName} FROM {$soupName} ORDER BY {$soupName:$idFieldName} ASC",
                countIdsPerSoql
            )
            val result = syncManager.smartStore.query(querySpec, page)

            // Not a resync
            maxTimeStamp = 0

            // Get ids
            for (i in 0 until result.length()) {
                idsInSmartStore.add(result.getJSONArray(i).getString(0))
            }
        }

        // If fetch is starting, figuring out totalSize
        // NB: it might not be the correct value during resync
        //     since not all records will have changed
        if (page == 0) {
            totalSize = syncManager.smartStore.countQuery(querySpec)
        }
        return if (idsInSmartStore.size > 0) {
            // Get records from server that have changed after maxTimeStamp
            val fieldlistToFetch = ArrayList(fieldlist)
            for (fieldName in listOf(idFieldName, modificationDateFieldName)) {
                if (!fieldlistToFetch.contains(fieldName)) {
                    fieldlistToFetch.add(fieldName)
                }
            }
            val records =
                fetchFromServer(syncManager, idsInSmartStore, fieldlistToFetch, maxTimeStamp)

            // Increment page if there is more to fetch
            val done = countIdsPerSoql * (page + 1) >= totalSize
            page = if (done) 0 else page + 1
            records
        } else {
            page = 0 // done
            null
        }
    }

    @Throws(IOException::class, JSONException::class)
    private fun fetchFromServer(
        syncManager: SyncManager,
        ids: List<String>,
        fieldlist: List<String>,
        maxTimeStamp: Long
    ): JSONArray {
        val whereClause = buildString {
            append(idFieldName)
            append(" IN ('")
            append(ids.joinToString("', '"))
            append("')")

            if (maxTimeStamp > 0) {
                append(
                    " AND $modificationDateFieldName > ${
                        Constants.TIMESTAMP_FORMAT.format(
                            Date(
                                maxTimeStamp
                            )
                        )
                    }"
                )
            }
        }
        val soql: String = SOQLBuilder.getInstanceWithFields(fieldlist).from(objectType)
            .where(whereClause).build()
        val request = RestRequest.getRequestForQuery(syncManager.apiVersion, soql)
        val response = syncManager.sendSyncWithMobileSyncUserAgent(request)
        val responseJson = response.asJSONObject()
        return responseJson.getJSONArray(Constants.RECORDS)
    }

    @Throws(IOException::class, JSONException::class)
    override fun getRemoteIds(syncManager: SyncManager, localIds: Set<String>): Set<String> {
        val remoteIds: MutableSet<String> = HashSet()
        val localIdsList: List<String> = ArrayList(localIds)
        val countSlices = ceil(localIds.size.toDouble() / countIdsPerSoql).toInt()
        for (slice in 0 until countSlices) {
            syncManager.checkAcceptingSyncs()
            val idsToFetch = localIdsList.subList(
                slice * countIdsPerSoql,
                min(localIdsList.size, (slice + 1) * countIdsPerSoql)
            )
            val records = fetchFromServer(
                syncManager, idsToFetch, listOf(
                    idFieldName
                ), 0 /* get all */
            )
            remoteIds.addAll(parseIdsFromResponse(records))
        }
        return remoteIds
    }

    companion object {
        private const val TAG = "RefreshSyncDownTarget"
        const val FIELDLIST = "fieldlist"
        const val SOBJECT_TYPE = "sobjectType"
        const val SOUP_NAME = "soupName"
        const val COUNT_IDS_PER_SOQL = "countIdsPerSoql"
        private const val MAX_COUNT_IDS_PER_SOQL = 500
    }
}