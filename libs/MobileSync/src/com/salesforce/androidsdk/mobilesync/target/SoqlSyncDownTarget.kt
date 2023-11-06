/*
 * Copyright (c) 2015-present, salesforce.com, inc.
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
import com.salesforce.androidsdk.mobilesync.manager.SyncManager.MobileSyncException
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.SOQLMutator
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestResponse
import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Date

/**
 * Target for sync defined by a SOQL query
 */
open class SoqlSyncDownTarget : SyncDownTarget {
    private val query: String
    protected var maxBatchSize = 0
    private var nextRecordsUrl: String? = null

    /**
     * Construct SoqlSyncDownTarget from json
     * @param target
     * @throws JSONException
     */
    @Throws(MobileSyncException::class)
    constructor(target: JSONObject) : super(target) {
        val queryFromJson = JSONObjectHelper.optString(target, QUERY)
            ?: throw MobileSyncException("No query defined")
        query = modifyQueryIfNeeded(queryFromJson)
        maxBatchSize = target.optInt(MAX_BATCH_SIZE, RestRequest.DEFAULT_BATCH_SIZE)
    }

    /**
     * Construct SoqlSyncDownTarget from soql query
     * @param query
     */
    constructor(query: String) : this(null, null, query)

    /**
     * Construct SoqlSyncDownTarget from soql query
     * @param idFieldName
     * @param modificationDateFieldName
     * @param query
     * @param maxBatchSize - must be between 200 and 2000
     */
    @JvmOverloads
    constructor(
        idFieldName: String?,
        modificationDateFieldName: String?,
        query: String,
        maxBatchSize: Int = RestRequest.DEFAULT_BATCH_SIZE
    ) : super(idFieldName, modificationDateFieldName) {
        queryType = QueryType.soql
        this.query = modifyQueryIfNeeded(query)
        this.maxBatchSize = maxBatchSize
    }

    private fun modifyQueryIfNeeded(query: String): String {
        if (query.isEmpty())
            return query

        val mutator = SOQLMutator(query)
        var mutated = false

        // Inserts the mandatory 'LastModifiedDate' field if it doesn't exist.
        val lastModFieldName = modificationDateFieldName
        if (!mutator.isSelectingField(lastModFieldName)) {
            mutated = true
            mutator.addSelectFields(lastModFieldName)
        }

        // Inserts the mandatory 'Id' field if it doesn't exist.
        val idFieldName = idFieldName
        if (!mutator.isSelectingField(idFieldName)) {
            mutated = true
            mutator.addSelectFields(idFieldName)
        }

        // Order by 'LastModifiedDate' field if no order by specified
        if (!mutator.hasOrderBy()) {
            mutated = true
            mutator.replaceOrderBy(lastModFieldName)
        }
        return if (mutated) {
            mutator.asBuilder().build()
        } else {
            query
        }
    }

    override val isSyncDownSortedByLatestModification: Boolean
        get() = SOQLMutator(query).isOrderingBy(modificationDateFieldName)

    /**
     * @return json representation of target
     * @throws JSONException
     */
    @Throws(JSONException::class)
    override fun asJSON(): JSONObject {
        return with(super.asJSON()) {
            put(QUERY, query)
            put(MAX_BATCH_SIZE, maxBatchSize)
        }
    }

    @Throws(IOException::class, JSONException::class)
    override fun startFetch(syncManager: SyncManager, maxTimeStamp: Long): JSONArray? {
        return startFetch(syncManager, getQuery(maxTimeStamp))
    }

    @Throws(IOException::class, JSONException::class)
    protected fun startFetch(syncManager: SyncManager, query: String): JSONArray {
        val request = RestRequest.getRequestForQuery(syncManager.apiVersion, query, maxBatchSize)
        val response = syncManager.sendSyncWithMobileSyncUserAgent(request)
        val responseJson = getResponseJson(response)
        val records = getRecordsFromResponseJson(responseJson)

        // Records total size.
        totalSize = responseJson.getInt(Constants.TOTAL_SIZE)

        // Captures next records URL.
        nextRecordsUrl = JSONObjectHelper.optString(responseJson, Constants.NEXT_RECORDS_URL)
        return records
    }

    @Throws(IOException::class, MobileSyncException::class)
    protected fun getResponseJson(response: RestResponse): JSONObject {
        val responseJson: JSONObject = try {
            response.asJSONObject()
        } catch (e: JSONException) {
            // Rest API errors are returned as JSON array
            throw MobileSyncException(response.asString())
        }
        return responseJson
    }

    @Throws(JSONException::class)
    protected open fun getRecordsFromResponseJson(responseJson: JSONObject): JSONArray {
        return responseJson.getJSONArray(Constants.RECORDS)
    }

    @Throws(IOException::class, JSONException::class)
    override fun continueFetch(syncManager: SyncManager): JSONArray? {
        if (nextRecordsUrl == null) {
            return null
        }
        val request = RestRequest(RestRequest.RestMethod.GET, nextRecordsUrl)
        val response = syncManager.sendSyncWithMobileSyncUserAgent(request)
        val responseJson = getResponseJson(response)
        val records = getRecordsFromResponseJson(responseJson)

        // Captures next records URL.
        nextRecordsUrl = JSONObjectHelper.optString(responseJson, Constants.NEXT_RECORDS_URL)
        return records
    }

    @Throws(IOException::class, JSONException::class)
    override fun getRemoteIds(syncManager: SyncManager, localIds: Set<String>): Set<String> {
        return getRemoteIdsWithSoql(syncManager, soqlForRemoteIds)
    }

    @Throws(IOException::class, JSONException::class)
    protected fun getRemoteIdsWithSoql(
        syncManager: SyncManager,
        soqlForRemoteIds: String
    ): Set<String> {
        val remoteIds: MutableSet<String> = HashSet()

        // Makes network request and parses the response.
        var records = startFetch(syncManager, soqlForRemoteIds)
        while (records.length() > 0) {
            syncManager.checkAcceptingSyncs()
            remoteIds.addAll(parseIdsFromResponse(records))
            records = continueFetch(syncManager) ?: JSONArray()
        }
        return remoteIds
    }

    open val soqlForRemoteIds: String
        get() {
            val fullQuery = getQuery(0)
            return SOQLMutator(fullQuery).replaceSelectFields(idFieldName).replaceOrderBy("")
                .asBuilder().build()
        }

    /**
     * @return soql query for this target
     */
    fun getQuery(): String {
        return getQuery(0)
    }

    /**
     * @return soql query for this target
     * @param maxTimeStamp
     */
    open fun getQuery(maxTimeStamp: Long): String {
        return if (maxTimeStamp > 0) addFilterForReSync(
            query,
            modificationDateFieldName,
            maxTimeStamp
        ) else query
    }

    companion object {
        const val QUERY = "query"
        const val MAX_BATCH_SIZE = "maxBatchSize"

        @JvmStatic
        fun addFilterForReSync(
            query: String,
            modificationFieldDatName: String?,
            maxTimeStamp: Long
        ): String {
            return if (maxTimeStamp > 0) {
                val extraPredicate = buildString {
                    append(modificationFieldDatName)
                    append(" > ")
                    append(Constants.TIMESTAMP_FORMAT.format(Date(maxTimeStamp)))
                }
                SOQLMutator(query).addWherePredicates(extraPredicate).asBuilder().build()
            } else {
                query
            }
        }
    }
}