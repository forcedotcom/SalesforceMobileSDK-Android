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

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.salesforce.androidsdk.smartsync.util.Constants;

/**
 * This class represents the search layout associated with an object type.
 *
 * @author bhariharan
 */
public class SalesforceObjectTypeLayout {

    private final String objectType;
    private int limit;
    private final JSONObject rawData;
    private final List<SalesforceObjectLayoutColumn> columns = new ArrayList<SalesforceObjectLayoutColumn>();

    /**
     * Parameterized constructor.
     *
     * @param objType Object type.
     * @param object Raw data.
     */
    public SalesforceObjectTypeLayout(String objType, JSONObject object) {
        objectType = objType;
        rawData = object;
        parseFields();
    }

    /**
     * Returns the object type.
     *
     * @return Object type.
     */
    public String getObjectType() {
        return objectType;
    }

    /**
     * Returns the limit on number of columns.
     *
     * @return Limit on number of columns.
     */
    public int getLimit() {
        return limit;
    }

    /**
     * Returns the list of search layout columns.
     *
     * @return List of search layout columns.
     */
    public List<SalesforceObjectLayoutColumn> getColumns() {
        return columns;
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
     * Parses the fields from the raw data and sets them.
     */
    private void parseFields() {
        if (rawData != null) {
            limit = rawData.optInt(Constants.LAYOUT_LIMITS_FIELD);
            final JSONArray searchColumns = rawData.optJSONArray(Constants.LAYOUT_COLUMNS_FIELD);
            if (searchColumns != null) {
                for (int i = 0; i < searchColumns.length(); i++) {
                    final JSONObject columnData = searchColumns.optJSONObject(i);
                    if (columnData != null) {
                        columns.add(new SalesforceObjectLayoutColumn(columnData));
                    }
                }
            }
        }
    }

    @Override
    public String toString() {
        return String.format("objectType: [%s], limit: [%d], rawData: [%s]",
                objectType, limit, rawData);
    }

    @Override
    public boolean equals(Object object) {
        if (object == null || !(object instanceof SalesforceObjectTypeLayout)) {
            return false;
        }
        final SalesforceObjectTypeLayout obj = (SalesforceObjectTypeLayout) object;
        if (objectType == null || obj.getObjectType() == null ||
                !objectType.equals(obj.getObjectType())) {
            return false;
        }
        return compareColumns(obj);
    }

    @Override
    public int hashCode() {
        return objectType.hashCode();
    }

    /**
     * Returns whether the two objects have the same columns.
     *
     * @param object SalesforceObjectTypeLayout instance.
     * @return True - if the columns are the same, False - otherwise.
     */
    private boolean compareColumns(SalesforceObjectTypeLayout object) {
    	if (object == null) {
    		return false;
    	}
    	final List<SalesforceObjectLayoutColumn> objColumns = object.getColumns();
    	if ((objColumns == null || objColumns.size() == 0)
    			&& (columns == null || columns.size() == 0)) {
    		return true;
    	}
    	int objColumnSize = objColumns.size();
    	if (objColumnSize != columns.size()) {
    		return false;
    	}
    	for (final SalesforceObjectLayoutColumn objColumn : objColumns) {
    		if (!columns.contains(objColumn)) {
    			return false;
    		}
    	}
    	return true;
    }
}
