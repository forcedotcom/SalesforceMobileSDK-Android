/*
 * Copyright (c) 2022-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.mobilesync.util

import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Capture fields that we want to sync down for a given object type (and record type) in a briefcase
 */
class BriefcaseObjectInfo @JvmOverloads constructor(
    val soupName: String,
    val sobjectType: String,
    val fieldlist: List<String>,
    idFieldName: String? = null,
    modificationDateFieldName: String? = null
) {
    val idFieldName: String
    val modificationDateFieldName: String

    constructor(json: JSONObject) : this(
        json.getString(SOUP_NAME),
        json.optString(SOBJECT_TYPE),
        JSONObjectHelper.toList<String>(json.getJSONArray(FIELD_LIST)),
        JSONObjectHelper.optString(json, ID_FIELD_NAME),
        JSONObjectHelper.optString(json, MODIFICATION_DATE_FIELD_NAME)
    )

    init {
        this.idFieldName = idFieldName ?: Constants.ID
        this.modificationDateFieldName = modificationDateFieldName
            ?: Constants.LAST_MODIFIED_DATE
    }

    @Throws(JSONException::class)
    fun asJSON(): JSONObject {
        return with(JSONObject()) {
            put(SOUP_NAME, soupName)
            put(SOBJECT_TYPE, sobjectType)
            put(FIELD_LIST, JSONArray(fieldlist))
            put(ID_FIELD_NAME, idFieldName)
            put(MODIFICATION_DATE_FIELD_NAME, modificationDateFieldName)
        }
    }

    companion object {
        // Constants
        const val SOBJECT_TYPE = "sobjectType"
        const val SOUP_NAME = "soupName"
        const val FIELD_LIST = "fieldlist"
        const val ID_FIELD_NAME = "idFieldName"
        const val MODIFICATION_DATE_FIELD_NAME = "modificationDateFieldName"

        @Throws(JSONException::class)
        fun fromJSONArray(json: JSONArray): List<BriefcaseObjectInfo> {
            return JSONObjectHelper
                .toList<JSONObject>(json)
                .map { BriefcaseObjectInfo(it) }
        }
    }
}