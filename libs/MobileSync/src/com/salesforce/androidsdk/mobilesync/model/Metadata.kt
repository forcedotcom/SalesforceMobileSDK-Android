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
package com.salesforce.androidsdk.mobilesync.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Represents the metadata of a Salesforce object.
 * @see [https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/dome_sobject_describe.htm](https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/dome_sobject_describe.htm)
 */
data class Metadata (
    val isActivateable : Boolean,
    val isCompactLayoutable : Boolean,
    val isCreateable : Boolean,
    val isCustom : Boolean,
    val isCustomSetting : Boolean,
    val isDeletable : Boolean,
    val isDeprecatedAndHidden : Boolean,
    val isFeedEnabled: Boolean,
    val childRelationships: JSONArray,
    val hasSubtypes: Boolean,
    val isSubtype: Boolean,
    val keyPrefix: String,
    val label: String,
    val labelPlural: String,
    val isLayoutable: Boolean,
    val isMergeable: Boolean,
    val mruEnabled: Boolean,
    val name: String,
    val fields: JSONArray,
    val networkScopeFieldName: String,
    val isQueryable: Boolean,
    val isReplicateable: Boolean,
    val isRetrieveable: Boolean,
    val isSearchLayoutable: Boolean,
    val isSearchable: Boolean,
    val isTriggerable: Boolean,
    val isUndeletable: Boolean,
    val isUpdateable: Boolean,
    val urls: JSONObject,
    val rawData: JSONObject
) {
    companion object {
        private const val ACTIVATEABLE = "activateable"
        private const val COMPACT_LAYOUTABLE = "compactLayoutable"
        private const val CREATEABLE = "createable"
        private const val CUSTOM = "custom"
        private const val CUSTOM_SETTING = "customSetting"
        private const val DELETABLE = "deletable"
        private const val DEPRECATED_AND_HIDDEN = "deprecatedAndHidden"
        private const val FEED_ENABLED = "feedEnabled"
        private const val CHILD_RELATIONSHIPS = "childRelationships"
        private const val HAS_SUBTYPES = "hasSubtypes"
        private const val IS_SUBTYPE = "isSubtype"
        private const val KEY_PREFIX = "keyPrefix"
        private const val LABEL = "label"
        private const val LABEL_PLURAL = "labelPlural"
        private const val LAYOUTABLE = "layoutable"
        private const val MERGEABLE = "mergeable"
        private const val MRU_ENABLED = "mruEnabled"
        private const val NAME = "name"
        private const val FIELDS = "fields"
        private const val NETWORK_SCOPE_FIELD_NAME = "networkScopeFieldName"
        private const val QUERYABLE = "queryable"
        private const val REPLICATEABLE = "replicateable"
        private const val RETRIEVEABLE = "retrieveable"
        private const val SERACH_LAYOUTABLE = "searchLayoutable"
        private const val SEARCHABLE = "searchable"
        private const val TRIGGERABLE = "triggerable"
        private const val UNDELETABLE = "undeletable"
        private const val UPDATEABLE = "updateable"
        private const val URLS = "urls"

        /**
         * Creates an instance of this class from its JSON representation.
         *
         * @param object JSON object.
         * @return Instance of this class.
         */
        fun fromJSON(obj: JSONObject): Metadata {
            return Metadata(
                obj.optBoolean(ACTIVATEABLE),
                obj.optBoolean(COMPACT_LAYOUTABLE),
                obj.optBoolean(CREATEABLE),
                obj.optBoolean(CUSTOM),
                obj.optBoolean(CUSTOM_SETTING),
                obj.optBoolean(DELETABLE),
                obj.optBoolean(DEPRECATED_AND_HIDDEN),
                obj.optBoolean(FEED_ENABLED),
                obj.optJSONArray(CHILD_RELATIONSHIPS) ?: JSONArray(),
                obj.optBoolean(HAS_SUBTYPES),
                obj.optBoolean(IS_SUBTYPE),
                obj.optString(KEY_PREFIX),
                obj.optString(LABEL),
                obj.optString(LABEL_PLURAL),
                obj.optBoolean(LAYOUTABLE),
                obj.optBoolean(MERGEABLE),
                obj.optBoolean(MRU_ENABLED),
                obj.optString(NAME),
                obj.optJSONArray(FIELDS) ?: JSONArray(),
                obj.optString(NETWORK_SCOPE_FIELD_NAME),
                obj.optBoolean(QUERYABLE),
                obj.optBoolean(REPLICATEABLE),
                obj.optBoolean(RETRIEVEABLE),
                obj.optBoolean(SERACH_LAYOUTABLE),
                obj.optBoolean(SEARCHABLE),
                obj.optBoolean(TRIGGERABLE),
                obj.optBoolean(UNDELETABLE),
                obj.optBoolean(UPDATEABLE),
                obj.optJSONObject(URLS) ?: JSONObject(),
                obj
            )
        }
    }
}