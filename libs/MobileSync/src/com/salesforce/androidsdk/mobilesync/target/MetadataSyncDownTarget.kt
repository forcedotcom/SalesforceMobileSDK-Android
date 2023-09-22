/*
 * Copyright (c) 2018-present, salesforce.com, inc.
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
import com.salesforce.androidsdk.rest.RestRequest
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Sync down target for object metadata. This uses the 'describe' API to fetch object metadata.
 *
 * @author bhariharan
 */
open class MetadataSyncDownTarget : SyncDownTarget {
    /**
     * Returns the object type associated with this target.
     *
     * @return Object type.
     */
    var objectType: String
        private set

    /**
     * Parameterized constructor.
     *
     * @param target JSON representation.
     * @throws JSONException Exception thrown.
     */
    constructor(target: JSONObject) : super(target) {
        objectType = target.getString(SOBJECT_TYPE)
    }

    /**
     * Parameterized constructor.
     *
     * @param objectType Object type.
     */
    constructor(objectType: String) : super() {
        queryType = QueryType.metadata
        this.objectType = objectType
    }

    /**
     * JSON representation of this target.
     *
     * @return JSON representation of this target.
     * @throws JSONException Exception thrown.
     */
    @Throws(JSONException::class)
    override fun asJSON(): JSONObject {
        return with(super.asJSON()) {
            put(SOBJECT_TYPE, objectType)
        }
    }

    @Throws(IOException::class, JSONException::class)
    override fun startFetch(syncManager: SyncManager, maxTimeStamp: Long): JSONArray {
        val request = RestRequest.getRequestForDescribe(syncManager.apiVersion, objectType)
        val response = syncManager.sendSyncWithMobileSyncUserAgent(request)
        val responseJSON = response.asJSONObject()
        responseJSON?.put(Constants.ID, objectType)
        val records = JSONArray()
        records.put(response.asJSONObject())

        // Recording total size.
        totalSize = 1
        return records
    }

    override fun continueFetch(syncManager: SyncManager): JSONArray? {
        return null
    }

    override fun getRemoteIds(syncManager: SyncManager, localIds: Set<String>): Set<String> {
        return emptySet()
    }

    override fun cleanGhosts(syncManager: SyncManager, soupName: String, syncId: Long): Int {
        return 0
    }

    companion object {
        const val SOBJECT_TYPE = "sobjectType"
    }
}