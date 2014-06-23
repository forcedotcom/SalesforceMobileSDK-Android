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

import org.json.JSONObject;

import com.salesforce.androidsdk.smartsync.util.Constants;

/**
 * This class contains information about a
 * column returned by search layout.
 *
 * @author bhariharan
 */
public class SalesforceObjectLayoutColumn {

    private final String name;
    private final String field;
    private final String format;
    private final String label;
    private JSONObject rawData;

    /**
     * Returns the name field value.
     *
     * @return Name field value.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the field field value.
     *
     * @return Field field value.
     */
    public String getField() {
        return field;
    }

    /**
     * Returns the format field value.
     *
     * @return Format field value.
     */
    public String getFormat() {
        return format;
    }

    /**
     * Returns the label field value.
     *
     * @return Label field value.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Returns the raw data.
     *
     * @return Raw data.
     */
    public JSONObject getRawData() {
        return rawData;
    }

    /**
     * Parameterized constructor.
     *
     * @param object Layout column JSON object.
     */
    public SalesforceObjectLayoutColumn(JSONObject object) {
        name = object.optString(Constants.LAYOUT_NAME_FIELD);
        field = object.optString(Constants.LAYOUT_FIELD_FIELD);
        format = object.optString(Constants.LAYOUT_FORMAT_FIELD);
        label = object.optString(Constants.LAYOUT_LABEL_FIELD);
        rawData = object;
    }

    @Override
    public String toString() {
        return String.format("name: [%s], field: [%s], format: [%s], label: [%s], rawData: [%s]",
                name, field, format, label, rawData);
    }

    @Override
    public boolean equals(Object object) {
        if (object == null || !(object instanceof SalesforceObjectLayoutColumn)) {
            return false;
        }
        final SalesforceObjectLayoutColumn obj = (SalesforceObjectLayoutColumn) object;
        if (name == null || obj.getName() == null ||
                !name.equals(obj.getName())) {
            return false;
        }
        if (field == null || obj.getField() == null ||
                !field.equals(obj.getField())) {
            return false;
        }
        if (format == null || obj.getFormat() == null ||
                !format.equals(obj.getFormat())) {
            return false;
        }
        if (label == null || obj.getLabel() == null ||
                !label.equals(obj.getLabel())) {
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
