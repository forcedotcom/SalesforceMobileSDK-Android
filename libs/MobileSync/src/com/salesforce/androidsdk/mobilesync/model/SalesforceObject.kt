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
open class SalesforceObject(obj: JSONObject) {
    /**
     * Returns the object type.
     *
     * @return Object type.
     */
    /**
     * Sets the object type.
     *
     * @param objectType Object type.
     */
    @JvmField
    var objectType: String? = null
    /**
     * Returns the name.
     *
     * @return Name.
     */
    /**
     * Sets the object name.
     *
     * @param name Object name.
     */
    @JvmField
    var name: String? = null

    /**
     * Returns the object ID.
     *
     * @return Object ID.
     */
    var objectId: String?

    /**
     * Returns the complete metadata.
     *
     * @return Complete metadata.
     */
    @JvmField
    val rawData: JSONObject

    /**
     * Parameterized constructor.
     *
     * @param object Raw data for object.
     */
    init {
        objectId = obj.optString(Constants.ID)
        if (objectId == null || Constants.EMPTY_STRING == objectId) {
            objectId = obj.optString(Constants.ID.lowercase())
            objectType = obj.optString(Constants.TYPE.lowercase())
            name = obj.optString(Constants.NAME.lowercase())
        } else {
            name = obj.optString(Constants.NAME)
            val attributes = obj.optJSONObject(Constants.ATTRIBUTES)
            if (attributes != null) {
                objectType = attributes.optString(Constants.TYPE.lowercase())
                if (objectType == null || Constants.RECENTLY_VIEWED == objectType || Constants.NULL_STRING == objectType) {
                    objectType = obj.optString(Constants.TYPE)
                }
            }
        }
        rawData = obj
    }

    override fun toString(): String {
        return String.format(
            "name: [%s], objectId: [%s], type: [%s], rawData: " +
                    "[%s]", name, objectId, objectType, rawData
        )
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null || obj !is SalesforceObject) {
            return false
        }
        val obj = obj
        if (objectId == null || obj.objectId == null || objectId != obj.objectId) {
            return false
        }
        if (name == null || obj.name == null || name != obj.name) {
            return false
        }
        return !(objectType == null || obj.objectType == null || objectType != obj.objectType)
    }

    override fun hashCode(): Int {
        var result = objectId.hashCode()
        result = result xor rawData.hashCode() + result * 37
        return result
    }
}