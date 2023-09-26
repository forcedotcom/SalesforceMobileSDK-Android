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
package com.salesforce.androidsdk.mobilesync.model

import com.salesforce.androidsdk.mobilesync.util.Constants
import org.json.JSONObject

/**
 * This class represents a typical Salesforce object.
 *
 * @author bhariharan
 */
open class SalesforceObject(@JvmField val rawData: JSONObject) {

    @JvmField
    var objectType: String?
    @JvmField
    var name: String?
    @JvmField
    var objectId: String

    init {
        if (rawData.optString(Constants.ID).isNullOrEmpty()) {
            objectType = rawData.optString(Constants.TYPE.lowercase())
            name = rawData.optString(Constants.NAME.lowercase())
            objectId = rawData.optString(Constants.ID.lowercase())
        } else {
            var oType: String? = null
            val attributes = rawData.optJSONObject(Constants.ATTRIBUTES)
            if (attributes != null) {
                oType = attributes.optString(Constants.TYPE.lowercase())
                if (oType == null || Constants.RECENTLY_VIEWED == oType || Constants.NULL_STRING == oType) {
                    oType = rawData.optString(Constants.TYPE)
                }
            }
            objectType = oType
            objectId = rawData.optString(Constants.ID)
            name = rawData.optString(Constants.NAME)
        }
    }

    override fun toString(): String {
        return "name: [$name], objectId: [$objectId], type: [$objectType], rawData: [$rawData]"
    }

    override fun equals(other: Any?): Boolean {
        return (other is SalesforceObject) &&
                (objectId == other.objectId) &&
                (name == other.name) &&
                (objectType == other.objectType)
    }

    override fun hashCode(): Int {
        val result = objectId.hashCode()
        return result xor rawData.hashCode() + result * 37
    }
}