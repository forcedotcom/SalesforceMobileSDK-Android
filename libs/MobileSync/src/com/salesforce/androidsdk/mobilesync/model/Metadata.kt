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
 *
 * @author bhariharan
 * @see [https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/dome_sobject_describe.htm](https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/dome_sobject_describe.htm)
 */
class Metadata {
    /**
     * Returns whether this object is activateable or not.
     *
     * @return True - if activateable, False - otherwise.
     */
    var isActivateable = false
        private set

    /**
     * Returns whether this object is compact layoutable or not.
     *
     * @return True - if compact layoutable, False - otherwise.
     */
    var isCompactLayoutable = false
        private set

    /**
     * Returns whether this object is createable or not.
     *
     * @return True - if createable, False - otherwise.
     */
    var isCreateable = false
        private set

    /**
     * Returns whether this object is custom or not.
     *
     * @return True - if custom, False - otherwise.
     */
    var isCustom = false
        private set

    /**
     * Returns whether this object has a custom setting or not.
     *
     * @return True - if it has a custom setting, False - otherwise.
     */
    var isCustomSetting = false
        private set

    /**
     * Returns whether this object is deletable or not.
     *
     * @return True - if deletable, False - otherwise.
     */
    var isDeletable = false
        private set

    /**
     * Returns whether this object is deprecated and hidden or not.
     *
     * @return True - if deprecated and hidden, False - otherwise.
     */
    var isDeprecatedAndHidden = false
        private set

    /**
     * Returns whether this object has feed enabled or not.
     *
     * @return True - if it has feed enabled, False - otherwise.
     */
    var isFeedEnabled = false
        private set

    /**
     * Returns the child relationships of this object.
     *
     * @return Child relationships of this object.
     */
    var childRelationships: JSONArray? = null
        private set
    private var hasSubtypes = false

    /**
     * Returns whether this object is a subtype or not.
     *
     * @return True - if subtype, False - otherwise.
     */
    var isSubtype = false
        private set

    /**
     * Returns the key prefix of this object.
     *
     * @return Key prefix of this object.
     */
    var keyPrefix: String? = null
        private set

    /**
     * Returns the label of this object.
     *
     * @return Label of this object.
     */
    var label: String? = null
        private set

    /**
     * Returns the label plural of this object.
     *
     * @return Label plural of this object.
     */
    var labelPlural: String? = null
        private set

    /**
     * Returns whether this object is layoutable or not.
     *
     * @return True - if layoutable, False - otherwise.
     */
    var isLayoutable = false
        private set

    /**
     * Returns whether this object is mergeable or not.
     *
     * @return True - if mergeable, False - otherwise.
     */
    var isMergeable = false
        private set
    private var mruEnabled = false

    /**
     * Returns the name of this object.
     *
     * @return Name of this object.
     */
    var name: String? = null
        private set

    /**
     * Returns the fields of this object.
     *
     * @return Fields of this object.
     */
    var fields: JSONArray? = null
        private set

    /**
     * Returns the network scope field name associated with this object.
     *
     * @return Network scope field name associated with this object.
     */
    var networkScopeFieldName: String? = null
        private set

    /**
     * Returns whether this object is queryable or not.
     *
     * @return True - if queryable, False - otherwise.
     */
    var isQueryable = false
        private set

    /**
     * Returns whether this object is replicateable or not.
     *
     * @return True - if replicateable, False - otherwise.
     */
    var isReplicateable = false
        private set

    /**
     * Returns whether this object is retrieveable or not.
     *
     * @return True - if retrieveable, False - otherwise.
     */
    var isRetrieveable = false
        private set

    /**
     * Returns whether this object is search layoutable or not.
     *
     * @return True - if search layoutable, False - otherwise.
     */
    var isSearchLayoutable = false
        private set

    /**
     * Returns whether this object is searchable or not.
     *
     * @return True - if searchable, False - otherwise.
     */
    var isSearchable = false
        private set

    /**
     * Returns whether this object is triggerable or not.
     *
     * @return True - if triggerable, False - otherwise.
     */
    var isTriggerable = false
        private set

    /**
     * Returns whether this object is undeletable or not.
     *
     * @return True - if undeletable, False - otherwise.
     */
    var isUndeletable = false
        private set

    /**
     * Returns whether this object is updateable or not.
     *
     * @return True - if updateable, False - otherwise.
     */
    var isUpdateable = false
        private set

    /**
     * Returns the URLs associated with this object.
     *
     * @return URLs associated with this object.
     */
    var urls: JSONObject? = null
        private set

    /**
     * Returns the raw data of this object metadata.
     *
     * @return Raw data of this object metadata.
     */
    var rawData: JSONObject? = null
        private set

    /**
     * Returns whether this object has subtypes or not.
     *
     * @return True - if it has subtypes, False - otherwise.
     */
    fun hasSubtypes(): Boolean {
        return hasSubtypes
    }

    /**
     * Returns whether this object has MRU enabled or not.
     *
     * @return True - if it has MRU enabled, False - otherwise.
     */
    fun hasMruEnabled(): Boolean {
        return mruEnabled
    }

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
        fun fromJSON(`object`: JSONObject?): Metadata? {
            var metadata: Metadata? = null
            if (`object` != null) {
                metadata = Metadata()
                metadata.rawData = `object`
                metadata.isActivateable = `object`.optBoolean(ACTIVATEABLE)
                metadata.isCompactLayoutable = `object`.optBoolean(COMPACT_LAYOUTABLE)
                metadata.isCreateable = `object`.optBoolean(CREATEABLE)
                metadata.isCustom = `object`.optBoolean(CUSTOM)
                metadata.isCustomSetting = `object`.optBoolean(CUSTOM_SETTING)
                metadata.isDeletable = `object`.optBoolean(DELETABLE)
                metadata.isDeprecatedAndHidden = `object`.optBoolean(DEPRECATED_AND_HIDDEN)
                metadata.isFeedEnabled = `object`.optBoolean(FEED_ENABLED)
                metadata.childRelationships = `object`.optJSONArray(CHILD_RELATIONSHIPS)
                metadata.hasSubtypes = `object`.optBoolean(HAS_SUBTYPES)
                metadata.isSubtype = `object`.optBoolean(IS_SUBTYPE)
                metadata.keyPrefix = `object`.optString(KEY_PREFIX)
                metadata.label = `object`.optString(LABEL)
                metadata.labelPlural = `object`.optString(LABEL_PLURAL)
                metadata.isLayoutable = `object`.optBoolean(LAYOUTABLE)
                metadata.isMergeable = `object`.optBoolean(MERGEABLE)
                metadata.mruEnabled = `object`.optBoolean(MRU_ENABLED)
                metadata.name = `object`.optString(NAME)
                metadata.fields = `object`.optJSONArray(FIELDS)
                metadata.networkScopeFieldName = `object`.optString(NETWORK_SCOPE_FIELD_NAME)
                metadata.isQueryable = `object`.optBoolean(QUERYABLE)
                metadata.isReplicateable = `object`.optBoolean(REPLICATEABLE)
                metadata.isRetrieveable = `object`.optBoolean(RETRIEVEABLE)
                metadata.isSearchLayoutable = `object`.optBoolean(SERACH_LAYOUTABLE)
                metadata.isSearchable = `object`.optBoolean(SEARCHABLE)
                metadata.isTriggerable = `object`.optBoolean(TRIGGERABLE)
                metadata.isUndeletable = `object`.optBoolean(UNDELETABLE)
                metadata.isUpdateable = `object`.optBoolean(UPDATEABLE)
                metadata.urls = `object`.optJSONObject(URLS)
            }
            return metadata
        }
    }
}