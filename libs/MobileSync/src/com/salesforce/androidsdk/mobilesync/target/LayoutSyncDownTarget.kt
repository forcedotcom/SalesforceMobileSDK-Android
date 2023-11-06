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
import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Sync down target for object layouts. This uses the '/ui-api/layout' API to fetch object layouts.
 * The easiest way to use this sync target is through [com.salesforce.androidsdk.mobilesync.manager.LayoutSyncManager].
 *
 * @author bhariharan
 */
open class LayoutSyncDownTarget : SyncDownTarget {
    /**
     * Returns the object API name associated with this target.
     *
     * @return Object API name.
     */
    var objectAPIName: String
        private set

    /**
     * Returns the form factor associated with this target.
     *
     * @return Form factor.
     */
    var formFactor: String?
        private set

    /**
     * Returns the layout type associated with this target.
     *
     * @return Layout type.
     */
    var layoutType: String
        private set

    /**
     * Returns the mode associated with this target.
     *
     * @return Mode.
     */
    var mode: String
        private set

    /**
     * Returns the record type ID associated with this target.
     *
     * @return Record type ID.
     */
    var recordTypeId: String?
        private set

    /**
     * Parameterized constructor.
     *
     * @param target JSON representation.
     * @throws JSONException Exception thrown.
     */
    constructor(target: JSONObject) : super(target) {
        objectAPIName = target.getString(SOBJECT_TYPE)
        formFactor = JSONObjectHelper.optString(target, FORM_FACTOR)
        layoutType = JSONObjectHelper.optString(target, LAYOUT_TYPE)
        mode = JSONObjectHelper.optString(target, MODE)
        recordTypeId = JSONObjectHelper.optString(target, RECORD_TYPE_ID)
    }

    /**
     * Parameterized constructor.
     *
     * @param objectAPIName Object API name.
     * @param formFactor Form factor.
     * @param layoutType Layout type.
     * @param mode Mode.
     * @param recordTypeId Record type ID.
     */
    constructor(
        objectAPIName: String, formFactor: String, layoutType: String,
        mode: String, recordTypeId: String?
    ) : super() {
        queryType = QueryType.layout
        this.objectAPIName = objectAPIName
        this.formFactor = formFactor
        this.layoutType = layoutType
        this.mode = mode
        this.recordTypeId = recordTypeId
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
            put(SOBJECT_TYPE, objectAPIName)
            put(FORM_FACTOR, formFactor)
            put(LAYOUT_TYPE, layoutType)
            put(MODE, mode)
            put(RECORD_TYPE_ID, recordTypeId)
        }
    }

    @Throws(IOException::class, JSONException::class)
    override fun startFetch(syncManager: SyncManager, maxTimeStamp: Long): JSONArray {
        val request = RestRequest.getRequestForObjectLayout(
            syncManager.apiVersion,
            objectAPIName, formFactor, layoutType, mode, recordTypeId
        )
        val response = syncManager.sendSyncWithMobileSyncUserAgent(request)
        val responseJSON = response.asJSONObject()
        responseJSON?.put(
            Constants.ID, String.format(
                ID_FIELD_VALUE, objectAPIName,
                formFactor, layoutType, mode, recordTypeId
            )
        )
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
        const val FORM_FACTOR = "formFactor"
        const val LAYOUT_TYPE = "layoutType"
        const val MODE = "mode"
        const val RECORD_TYPE_ID = "recordTypeId"
        const val ID_FIELD_VALUE = "%s-%s-%s-%s-%s"
    }
}