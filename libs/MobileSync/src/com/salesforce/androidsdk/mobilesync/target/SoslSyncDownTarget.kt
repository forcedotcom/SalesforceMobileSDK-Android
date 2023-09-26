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
import com.salesforce.androidsdk.rest.RestRequest
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Target for sync defined by a SOSL query
 */
open class SoslSyncDownTarget : SyncDownTarget {
    /**
     * @return sosl query for this target
     */
    var query: String
        private set

    /**
     * Construct SoslSyncDownTarget from json
     * @param target
     * @throws JSONException
     */
    constructor(target: JSONObject) : super(target) {
        query = target.getString(QUERY)
    }

    /**
     * Construct SoslSyncDownTarget from sosl query
     * @param query
     */
    constructor(query: String) : super() {
        queryType = QueryType.sosl
        this.query = query
    }

    /**
     * @return json representation of target
     * @throws JSONException
     */
    @Throws(JSONException::class)
    override fun asJSON(): JSONObject {
        return with(super.asJSON()) {
            put(QUERY, query)
        }
    }

    @Throws(IOException::class, JSONException::class)
    override fun startFetch(syncManager: SyncManager, maxTimeStamp: Long): JSONArray {
        return startFetch(syncManager, query)
    }

    @Throws(IOException::class, JSONException::class)
    private fun startFetch(
        syncManager: SyncManager,
        queryRun: String
    ): JSONArray {
        val request = RestRequest.getRequestForSearch(syncManager.apiVersion, queryRun)
        val response = syncManager.sendSyncWithMobileSyncUserAgent(request)
        val records = response.asJSONObject().getJSONArray(SEARCH_RECORDS)

        // Recording total size
        totalSize = records.length()
        return records
    }

    override fun continueFetch(syncManager: SyncManager): JSONArray? {
        return null
    }

    @Throws(IOException::class, JSONException::class)
    override fun getRemoteIds(syncManager: SyncManager, localIds: Set<String>): Set<String> {
        // Makes network request and parses the response.
        val records = startFetch(syncManager, query)
        return HashSet(parseIdsFromResponse(records))
    }

    companion object {
        const val QUERY = "query"
        const val SEARCH_RECORDS = "searchRecords"
    }
}