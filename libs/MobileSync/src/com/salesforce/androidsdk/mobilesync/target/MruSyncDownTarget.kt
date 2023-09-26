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

import android.text.TextUtils
import android.text.TextUtils.*
import com.salesforce.androidsdk.mobilesync.manager.SyncManager
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.SOQLBuilder
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Target for sync that downloads most recently used records
 */
open class MruSyncDownTarget : SyncDownTarget {
    /**
     * @return field list for this target
     */
    var fieldlist: List<String>
        private set

    /**
     * @return object type for this target
     */
    var objectType: String
        private set

    /**
     * Construct MruSyncDownTarget from json
     * @param target
     * @throws JSONException
     */
    constructor(target: JSONObject) : super(target) {
        fieldlist = JSONObjectHelper.toList(target.getJSONArray(FIELDLIST))
        objectType = target.getString(SOBJECT_TYPE)
    }

    /**
     * Constructor
     * @param fieldlist
     * @param objectType
     */
    constructor(fieldlist: List<String>, objectType: String) : super() {
        queryType = QueryType.mru
        this.fieldlist = fieldlist
        this.objectType = objectType
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
        }
    }

    @Throws(IOException::class, JSONException::class)
    override fun startFetch(syncManager: SyncManager, maxTimeStamp: Long): JSONArray {
        val request = RestRequest.getRequestForMetadata(syncManager.apiVersion, objectType)
        val response = syncManager.sendSyncWithMobileSyncUserAgent(request)
        val recentItems = JSONObjectHelper.pluck<String>(
            response.asJSONObject().getJSONArray(Constants.RECENT_ITEMS), Constants.ID
        )

        // Building SOQL query to get requested at.
        val soql: String =
            SOQLBuilder.getInstanceWithFields(fieldlist).from(objectType).where(
                "$idFieldName IN ('${join("', '", recentItems)}')"
            ).build()
        return startFetch(syncManager, soql)
    }

    @Throws(IOException::class, JSONException::class)
    private fun startFetch(
        syncManager: SyncManager,
        queryRun: String
    ): JSONArray {
        val request = RestRequest.getRequestForQuery(syncManager.apiVersion, queryRun)
        val response = syncManager.sendSyncWithMobileSyncUserAgent(request)
        val responseJson = response.asJSONObject()
        val records = responseJson.getJSONArray(Constants.RECORDS)

        // Recording total size
        totalSize = records.length()
        return records
    }

    override fun continueFetch(syncManager: SyncManager): JSONArray? {
        return null
    }

    @Throws(IOException::class, JSONException::class)
    override fun getRemoteIds(syncManager: SyncManager, localIds: Set<String>): Set<String> {
        val idFieldName = idFieldName

        // Alters the SOQL query to get only IDs.
        val soql: String =
            SOQLBuilder.getInstanceWithFields(idFieldName).from(objectType).where(
                idFieldName
                        + " IN ('" + join("', '", localIds) + "')"
            ).build()

        // Makes network request and parses the response.
        val records = startFetch(syncManager, soql)
        return HashSet(parseIdsFromResponse(records))
    }

    companion object {
        const val FIELDLIST = "fieldlist"
        const val SOBJECT_TYPE = "sobjectType"
    }
}