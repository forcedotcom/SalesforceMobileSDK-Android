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
package com.salesforce.androidsdk.smartsync.model;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Represents the metadata of a Salesforce object.
 *
 * @author bhariharan
 * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/dome_sobject_describe.htm">https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/dome_sobject_describe.htm</a>
 */
public class Metadata {

    private static final String ACTIVATEABLE = "activateable";
    private static final String COMPACT_LAYOUTABLE = "compactLayoutable";
    private static final String CREATEABLE = "createable";
    private static final String CUSTOM = "custom";
    private static final String CUSTOM_SETTING = "customSetting";
    private static final String DELETABLE = "deletable";
    private static final String DEPRECATED_AND_HIDDEN = "deprecatedAndHidden";
    private static final String FEED_ENABLED = "feedEnabled";
    private static final String CHILD_RELATIONSHIPS = "childRelationships";
    private static final String HAS_SUBTYPES = "hasSubtypes";
    private static final String IS_SUBTYPE = "isSubtype";
    private static final String KEY_PREFIX = "keyPrefix";
    private static final String LABEL = "label";
    private static final String LABEL_PLURAL = "labelPlural";
    private static final String LAYOUTABLE = "layoutable";
    private static final String MERGEABLE = "mergeable";
    private static final String MRU_ENABLED = "mruEnabled";
    private static final String NAME = "name";
    private static final String FIELDS = "fields";
    private static final String NETWORK_SCOPE_FIELD_NAME = "networkScopeFieldName";
    private static final String QUERYABLE = "queryable";
    private static final String REPLICATEABLE = "replicateable";
    private static final String RETRIEVEABLE = "retrieveable";
    private static final String SERACH_LAYOUTABLE = "searchLayoutable";
    private static final String SEARCHABLE = "searchable";
    private static final String TRIGGERABLE = "triggerable";
    private static final String UNDELETABLE = "undeletable";
    private static final String UPDATEABLE = "updateable";
    private static final String URLS = "urls";

    private boolean activateable;
    private boolean compactLayoutable;
    private boolean createable;
    private boolean custom;
    private boolean customSetting;
    private boolean deletable;
    private boolean deprecatedAndHidden;
    private boolean feedEnabled;
    private JSONArray childRelationships;
    private boolean hasSubtypes;
    private boolean isSubtype;
    private String keyPrefix;
    private String label;
    private String labelPlural;
    private boolean layoutable;
    private boolean mergeable;
    private boolean mruEnabled;
    private String name;
    private JSONArray fields;
    private String networkScopeFieldName;
    private boolean queryable;
    private boolean replicateable;
    private boolean retrieveable;
    private boolean searchLayoutable;
    private boolean searchable;
    private boolean triggerable;
    private boolean undeletable;
    private boolean updateable;
    private JSONObject urls;
    private JSONObject rawData;

    /**
     * Creates an instance of this class from its JSON representation.
     *
     * @param object JSON object.
     * @return Instance of this class.
     */
    public static Metadata fromJSON(JSONObject object) {
        Metadata metadata = null;
        if (object != null) {
            metadata = new Metadata();
            metadata.rawData = object;
            metadata.activateable = object.optBoolean(ACTIVATEABLE);
            metadata.compactLayoutable = object.optBoolean(COMPACT_LAYOUTABLE);
            metadata.createable = object.optBoolean(CREATEABLE);
            metadata.custom = object.optBoolean(CUSTOM);
            metadata.customSetting = object.optBoolean(CUSTOM_SETTING);
            metadata.deletable = object.optBoolean(DELETABLE);
            metadata.deprecatedAndHidden = object.optBoolean(DEPRECATED_AND_HIDDEN);
            metadata.feedEnabled = object.optBoolean(FEED_ENABLED);
            metadata.childRelationships = object.optJSONArray(CHILD_RELATIONSHIPS);
            metadata.hasSubtypes = object.optBoolean(HAS_SUBTYPES);
            metadata.isSubtype = object.optBoolean(IS_SUBTYPE);
            metadata.keyPrefix = object.optString(KEY_PREFIX);
            metadata.label = object.optString(LABEL);
            metadata.labelPlural = object.optString(LABEL_PLURAL);
            metadata.layoutable = object.optBoolean(LAYOUTABLE);
            metadata.mergeable = object.optBoolean(MERGEABLE);
            metadata.mruEnabled = object.optBoolean(MRU_ENABLED);
            metadata.name = object.optString(NAME);
            metadata.fields = object.optJSONArray(FIELDS);
            metadata.networkScopeFieldName = object.optString(NETWORK_SCOPE_FIELD_NAME);
            metadata.queryable = object.optBoolean(QUERYABLE);
            metadata.replicateable = object.optBoolean(REPLICATEABLE);
            metadata.retrieveable = object.optBoolean(RETRIEVEABLE);
            metadata.searchLayoutable = object.optBoolean(SERACH_LAYOUTABLE);
            metadata.searchable = object.optBoolean(SEARCHABLE);
            metadata.triggerable = object.optBoolean(TRIGGERABLE);
            metadata.undeletable = object.optBoolean(UNDELETABLE);
            metadata.updateable = object.optBoolean(UPDATEABLE);
            metadata.urls = object.optJSONObject(URLS);
        }
        return metadata;
    }

    /**
     * Returns whether this object is activateable or not.
     *
     * @return True - if activateable, False - otherwise.
     */
    public boolean isActivateable() {
        return activateable;
    }

    /**
     * Returns whether this object is compact layoutable or not.
     *
     * @return True - if compact layoutable, False - otherwise.
     */
    public boolean isCompactLayoutable() {
        return compactLayoutable;
    }

    /**
     * Returns whether this object is createable or not.
     *
     * @return True - if createable, False - otherwise.
     */
    public boolean isCreateable() {
        return createable;
    }

    /**
     * Returns whether this object is custom or not.
     *
     * @return True - if custom, False - otherwise.
     */
    public boolean isCustom() {
        return custom;
    }

    /**
     * Returns whether this object has a custom setting or not.
     *
     * @return True - if it has a custom setting, False - otherwise.
     */
    public boolean isCustomSetting() {
        return customSetting;
    }

    /**
     * Returns whether this object is deletable or not.
     *
     * @return True - if deletable, False - otherwise.
     */
    public boolean isDeletable() {
        return deletable;
    }

    /**
     * Returns whether this object is deprecated and hidden or not.
     *
     * @return True - if deprecated and hidden, False - otherwise.
     */
    public boolean isDeprecatedAndHidden() {
        return deprecatedAndHidden;
    }

    /**
     * Returns whether this object has feed enabled or not.
     *
     * @return True - if it has feed enabled, False - otherwise.
     */
    public boolean isFeedEnabled() {
        return feedEnabled;
    }

    /**
     * Returns the child relationships of this object.
     *
     * @return Child relationships of this object.
     */
    public JSONArray getChildRelationships() {
        return childRelationships;
    }

    /**
     * Returns whether this object has subtypes or not.
     *
     * @return True - if it has subtypes, False - otherwise.
     */
    public boolean hasSubtypes() {
        return hasSubtypes;
    }

    /**
     * Returns whether this object is a subtype or not.
     *
     * @return True - if subtype, False - otherwise.
     */
    public boolean isSubtype() {
        return isSubtype;
    }

    /**
     * Returns the key prefix of this object.
     *
     * @return Key prefix of this object.
     */
    public String getKeyPrefix() {
        return keyPrefix;
    }

    /**
     * Returns the label of this object.
     *
     * @return Label of this object.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Returns the label plural of this object.
     *
     * @return Label plural of this object.
     */
    public String getLabelPlural() {
        return labelPlural;
    }

    /**
     * Returns whether this object is layoutable or not.
     *
     * @return True - if layoutable, False - otherwise.
     */
    public boolean isLayoutable() {
        return layoutable;
    }

    /**
     * Returns whether this object is mergeable or not.
     *
     * @return True - if mergeable, False - otherwise.
     */
    public boolean isMergeable() {
        return mergeable;
    }

    /**
     * Returns whether this object has MRU enabled or not.
     *
     * @return True - if it has MRU enabled, False - otherwise.
     */
    public boolean hasMruEnabled() {
        return mruEnabled;
    }

    /**
     * Returns the name of this object.
     *
     * @return Name of this object.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the fields of this object.
     *
     * @return Fields of this object.
     */
    public JSONArray getFields() {
        return fields;
    }

    /**
     * Returns the network scope field name associated with this object.
     *
     * @return Network scope field name associated with this object.
     */
    public String getNetworkScopeFieldName() {
        return networkScopeFieldName;
    }

    /**
     * Returns whether this object is queryable or not.
     *
     * @return True - if queryable, False - otherwise.
     */
    public boolean isQueryable() {
        return queryable;
    }

    /**
     * Returns whether this object is replicateable or not.
     *
     * @return True - if replicateable, False - otherwise.
     */
    public boolean isReplicateable() {
        return replicateable;
    }

    /**
     * Returns whether this object is retrieveable or not.
     *
     * @return True - if retrieveable, False - otherwise.
     */
    public boolean isRetrieveable() {
        return retrieveable;
    }

    /**
     * Returns whether this object is search layoutable or not.
     *
     * @return True - if search layoutable, False - otherwise.
     */
    public boolean isSearchLayoutable() {
        return searchLayoutable;
    }

    /**
     * Returns whether this object is searchable or not.
     *
     * @return True - if searchable, False - otherwise.
     */
    public boolean isSearchable() {
        return searchable;
    }

    /**
     * Returns whether this object is triggerable or not.
     *
     * @return True - if triggerable, False - otherwise.
     */
    public boolean isTriggerable() {
        return triggerable;
    }

    /**
     * Returns whether this object is undeletable or not.
     *
     * @return True - if undeletable, False - otherwise.
     */
    public boolean isUndeletable() {
        return undeletable;
    }

    /**
     * Returns whether this object is updateable or not.
     *
     * @return True - if updateable, False - otherwise.
     */
    public boolean isUpdateable() {
        return updateable;
    }

    /**
     * Returns the URLs associated with this object.
     *
     * @return URLs associated with this object.
     */
    public JSONObject getUrls() {
        return urls;
    }

    /**
     * Returns the raw data of this object metadata.
     *
     * @return Raw data of this object metadata.
     */
    public JSONObject getRawData() {
        return rawData;
    }
}
