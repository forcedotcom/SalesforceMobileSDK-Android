/*
 * Copyright (c) 2014, salesforce.com, inc.
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
package com.salesforce.androidsdk.sobjectsdk.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This class contains metadata that can be used to
 * ascertain the object type of a Salesforce object.
 *
 * @author bhariharan
 */
public class SalesforceObjectType {

    public static final String SF_OBJECTYPE_KEYPREFIX_FIELD = "keyPrefix";
    public static final String SF_OBJECTYPE_NAME_FIELD = "name";
    public static final String SF_OBJECTYPE_LAST_VIEWED_DATE_FIELD_NAME = "LastViewedDate";
    public static final String SF_OBJECTYPE_RECORD_TYPE_INFOS = "recordTypeInfos";
    public static final String SF_OBJECTYPE_CHILD_RELATIONSHIPS = "childRelationships";
    public static final String SF_OBJECTYPE_LABEL_FIELD = "label";
    public static final String SF_OBJECTYPE_LABELPLURAL_FIELD = "labelPlural";
    public static final String SF_OBJECTYPE_FIELDS_FIELD = "fields";
    public static final String SF_OBJECTYPE_UPDATEABLE_FIELD = "updateable";
    public static final String SF_OBJECTYPE_QUERYABLE_FIELD = "queryable";
    public static final String SF_OBJECTYPE_LAYOUTABLE_FIELD = "layoutable";
    public static final String SF_OBJECTYPE_SEARCHABLE_FIELD = "searchable";
    public static final String SF_OBJECTYPE_FEEDENABLED_FIELD = "feedEnabled";
    public static final String SF_OBJECTYPE_HIDDEN_FIELD = "deprecatedAndHidden";
    public static final String SF_OBJECTTYPE_NAMEFIELD_FIELD = "nameField";
    private static final String SF_OBJECTTYPE_NETWORKID_FIELD = "NetworkId";
    private static final String SF_OBJECTTYPE_NETWORKSCOPE_FIELD = "NetworkScope";

    private String keyPrefix;
    private final String name;
    private String label;
    private String labelPlural;
    private String nameField;
    private JSONArray fields;
    private JSONObject rawData;
    private String networkFieldName = "NetworkId";

    /**
     * Parameterized constructor.
     *
     * @param name Object name.
     */
    public SalesforceObjectType(String name) {
        this.name = name;
    }

    /**
     * Parameterized constructor.
     *
     * @param name Object name.
     * @param keyPrefix Key prefix.
     */
    public SalesforceObjectType(String name, String keyPrefix) {
        this.name = name;
        this.keyPrefix = keyPrefix;
    }

    /**
     * Parameterized constructor.
     *
     * @param object Raw data for object.
     */
    public SalesforceObjectType(JSONObject object) {
        this.keyPrefix = object.optString(SF_OBJECTYPE_KEYPREFIX_FIELD);
        this.name = object.optString(SF_OBJECTYPE_NAME_FIELD);
        this.label = object.optString(SF_OBJECTYPE_LABEL_FIELD);
        this.labelPlural = object.optString(SF_OBJECTYPE_LABELPLURAL_FIELD);
        if (this.label == null || this.label.trim().isEmpty()) {
            label = name;
        }
        if (this.labelPlural == null || this.labelPlural.trim().isEmpty()) {
            labelPlural = label;
        }
        rawData = object;
        fields = rawData.optJSONArray(SF_OBJECTYPE_FIELDS_FIELD);

        /*
         * Extracts just the fields we care about - 'LastViewedDate', static field name constants,
         * and those fields with 'nameField = true'. Dumps the other fields to improve performance.
         * Parsing and caching unnecessary fields causes a significant drop in performance.
         */
        final JSONArray fieldArray = new JSONArray();
        if (fields != null) {
            final String[] fieldNameConstants = SalesforceObjectFieldNameConstants.getAllFieldNameConstants();
            for (int i = 0; i < fields.length(); i++) {
                final JSONObject field = fields.optJSONObject(i);
                if (field != null) {
                    String nameStr = field.optString(SF_OBJECTYPE_NAME_FIELD);
                    if (nameStr != null) {
                        nameStr = nameStr.trim();
                        if (nameStr.equals(SF_OBJECTYPE_LAST_VIEWED_DATE_FIELD_NAME)) {
                            fieldArray.put(field);
                        } else {

                            /*
                             * Checks if this field is one of the static fields we are interested in.
                             * If so, adds it to the field array. If not, nothing happens.
                             */
                            for (int j = 0; j < fieldNameConstants.length; j++) {
                                if (nameStr.equals(fieldNameConstants[j])) {
                                    fieldArray.put(field);
                                }
                            }
                        }
                    }
                    if (nameStr != null && nameStr.trim().equals(SF_OBJECTYPE_LAST_VIEWED_DATE_FIELD_NAME)) {
                        fieldArray.put(field);
                    } else if (nameStr != null
                            && (SF_OBJECTTYPE_NETWORKID_FIELD.equals(nameStr.trim())
                                    || SF_OBJECTTYPE_NETWORKSCOPE_FIELD
                                    .equals(nameStr.trim()))) {
                        networkFieldName = nameStr;
                    }
                    boolean nameFieldPresent = field.optBoolean(SF_OBJECTTYPE_NAMEFIELD_FIELD);
                    if (nameFieldPresent) {

                        /*
                         * Some objects, such as 'Account', have more than one
                         * name field, like 'Name', 'First Name', and 'Last Name'.
                         * This check exists to ensure that we use the first
                         * name field, which is the flagship name field, and
                         * not the last one. If it is already set, we won't
                         * overwrite it.
                         */
                        if (nameField == null || nameField.trim().isEmpty()
                                || "null".equals(nameField.trim())) {
                            nameField = field.optString(SF_OBJECTYPE_NAME_FIELD);
                        }
                        fieldArray.put(field);
                    }
                }
            }
        }
        if (fieldArray.length() > 0) {
            fields = fieldArray;
            try {
                rawData.put(SF_OBJECTYPE_FIELDS_FIELD, fieldArray);
            } catch (JSONException e) {
                Log.e(TAG, "Error occurred while parsing JSON", e);
            }
        }

        /*
         * Dumps 'recordTypeInfos' and 'childRelationships' from the
         * metadata since we don't care about them, to improve performance.
         * This can be re-enabled at any point if we need this metadata.
         */
        try {
            if (rawData.optJSONArray(SF_OBJECTYPE_RECORD_TYPE_INFOS) != null) {
                rawData.put(SF_OBJECTYPE_RECORD_TYPE_INFOS, null);
            }
            if (rawData.optJSONArray(SF_OBJECTYPE_CHILD_RELATIONSHIPS) != null) {
                rawData.put(SF_OBJECTYPE_CHILD_RELATIONSHIPS, null);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error occurred while parsing JSON", e);
        }
    }

    /**
     * Returns the key prefix.
     *
     * @return Key prefix.
     */
    public String getKeyPrefix() {
        return keyPrefix;
    }

    /**
     * Returns the name.
     *
     * @return Name.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the label.
     *
     * @return Label.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Returns the plural label.
     *
     * @return Plural label.
     */
    public String getLabelPlural() {
        return labelPlural;
    }

    /**
     * Returns the name field.
     *
     * @return Name field.
     */
    public String getNameField() {
        if (nameField == null || nameField.trim().isEmpty()) {
            final JSONArray dataFields = rawData.optJSONArray(SF_OBJECTYPE_FIELDS_FIELD);
            if (dataFields != null) {
                for (int i = 0; i < dataFields.length(); i++) {
                    final JSONObject field = dataFields.optJSONObject(i);
                    if (field != null) {
                        boolean nameFieldPresent = field.optBoolean(SF_OBJECTTYPE_NAMEFIELD_FIELD);
                        if (nameFieldPresent) {
                            nameField = field.optString(SF_OBJECTYPE_NAME_FIELD);
                        }
                    }
                }
            }
        }
        return nameField;
    }

    /**
     * Returns if this object type is searchable or not.
     *
     * @return True - if searchable, False - otherwise.
     */
    public boolean isSearchable() {
        return (rawData != null && !rawData.optBoolean(SF_OBJECTYPE_HIDDEN_FIELD)
                && rawData.optBoolean(SF_OBJECTYPE_SEARCHABLE_FIELD));
    }

    /**
     * Returns if this object type is layoutable or not.
     *
     * @return True - if layoutable, False - otherwise.
     */
    public boolean isLayoutable() {
        return (rawData != null && !rawData.optBoolean(SF_OBJECTYPE_HIDDEN_FIELD)
                && rawData.optBoolean(SF_OBJECTYPE_LAYOUTABLE_FIELD));
    }

    /**
     * Returns the fields.
     *
     * @return Fields.
     */
    public JSONArray getFields() {
        if (fields == null || fields.length() == 0) {
            fields = rawData.optJSONArray(SF_OBJECTYPE_FIELDS_FIELD);
        }
        return fields;
    }

    /**
     * Returns the complete metadata.
     *
     * @return Complete metadata.
     */
    public JSONObject getRawData() {
        return rawData;
    }

    /**
     * Returns the name of the field used for network aware queries and searches.
     *
     * @return Network field name.
     */
    public String getNetworkFieldName() {
        return networkFieldName;
    }

    @Override
    public String toString() {
        return String.format("keyPrefix: [%s], name: [%s], label: [%s], labelPlural: " +
                "[%s], nameField: [%s], rawData: [%s]", keyPrefix, name, label,
                labelPlural, nameField, rawData);
    }

    @Override
    public boolean equals(Object object) {
        if (object == null || !(object instanceof SalesforceObjectType)) {
            return false;
        }
        final SalesforceObjectType obj = (SalesforceObjectType) object;
        if (name == null || obj.getName() == null || !name.trim().equals(obj.getName().trim())) {
            return false;
        }
        if (rawData == null || obj.getRawData() == null || !rawData.equals(obj.getRawData())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result ^= rawData.hashCode() + result * 37;
        return result;
    }
}
