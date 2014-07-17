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
package com.salesforce.androidsdk.smartsync.model;

import org.json.JSONArray;
import org.json.JSONObject;

import com.salesforce.androidsdk.smartsync.util.Constants;

/**
 * This class contains metadata that can be used to
 * ascertain the object type of a Salesforce object.
 *
 * @author bhariharan
 */
public class SalesforceObjectType {

    private String keyPrefix;
    private final String name;
    private String label;
    private String labelPlural;
    private String nameField;
    private JSONArray fields;
    private JSONObject rawData;

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
        keyPrefix = object.optString(Constants.KEYPREFIX_FIELD);
        name = object.optString(Constants.NAME_FIELD);
        label = object.optString(Constants.LABEL_FIELD);
        labelPlural = object.optString(Constants.LABELPLURAL_FIELD);
        if (label == null || Constants.EMPTY_STRING.equals(label)) {
            label = name;
        }
        if (labelPlural == null || Constants.EMPTY_STRING.equals(labelPlural)) {
            labelPlural = label;
        }
        rawData = object;
        fields = rawData.optJSONArray(Constants.FIELDS_FIELD);

        /*
         * Extracts a few flagship fields and sets them to instance variables
         * for easy retrieval.
         */
        if (fields != null) {
            for (int i = 0; i < fields.length(); i++) {
                final JSONObject field = fields.optJSONObject(i);
                if (field != null) {
                    boolean nameFieldPresent = field.optBoolean(Constants.NAMEFIELD_FIELD);
                    if (nameFieldPresent) {

                        /*
                         * Some objects, such as 'Account', have more than one
                         * name field, like 'Name', 'First Name', and 'Last Name'.
                         * This check exists to ensure that we use the first
                         * name field, which is the flagship name field, and
                         * not the last one. If it is already set, we won't
                         * overwrite it.
                         */
                        if (nameField == null || Constants.EMPTY_STRING.equals(nameField)
                                || Constants.NULL_STRING.equals(nameField)) {
                            nameField = field.optString(Constants.NAME_FIELD);
                        }
                    }
                }
            }
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
        if (nameField == null || Constants.EMPTY_STRING.equals(nameField)) {
            final JSONArray dataFields = rawData.optJSONArray(Constants.FIELDS_FIELD);
            if (dataFields != null) {
                for (int i = 0; i < dataFields.length(); i++) {
                    final JSONObject field = dataFields.optJSONObject(i);
                    if (field != null) {
                        boolean nameFieldPresent = field.optBoolean(Constants.NAMEFIELD_FIELD);
                        if (nameFieldPresent) {
                            nameField = field.optString(Constants.NAME_FIELD);
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
        return (rawData != null && !rawData.optBoolean(Constants.HIDDEN_FIELD)
                && rawData.optBoolean(Constants.SEARCHABLE_FIELD));
    }

    /**
     * Returns if this object type is layoutable or not.
     *
     * @return True - if layoutable, False - otherwise.
     */
    public boolean isLayoutable() {
        return (rawData != null && !rawData.optBoolean(Constants.HIDDEN_FIELD)
                && rawData.optBoolean(Constants.LAYOUTABLE_FIELD));
    }

    /**
     * Returns the fields.
     *
     * @return Fields.
     */
    public JSONArray getFields() {
        if (fields == null || fields.length() == 0) {
            fields = rawData.optJSONArray(Constants.FIELDS_FIELD);
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
        if (name == null || obj.getName() == null || !name.equals(obj.getName())) {
            return false;
        }
        if (keyPrefix == null || obj.getKeyPrefix() == null || !keyPrefix.equals(obj.getKeyPrefix())) {
            return false;
        }
        if (label == null || obj.getLabel() == null || !label.equals(obj.getLabel())) {
            return false;
        }
        if (labelPlural == null || obj.getLabelPlural() == null || !labelPlural.equals(obj.getLabelPlural())) {
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
