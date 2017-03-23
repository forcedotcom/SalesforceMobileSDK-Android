/*
 * Copyright (c) 2017-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.smartsync.util;

import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Simple object to capture details of children in parent-child relationship
 */
public class ChildrenInfo extends ParentInfo {

    // Constants
    public static final String SOBJECT_TYPE_PLURAL = "sobjectTypePlural";
    public static final String PARENT_ID_FIELD_NAME = "parentIdFieldName"; // name of field on  holding parent server id

    // Fields
    public final String sobjectTypePlural;
    public final String parentIdFieldName;

    public ChildrenInfo(JSONObject json) throws JSONException {
        this(
                json.getString(SOBJECT_TYPE),
                json.getString(SOBJECT_TYPE_PLURAL),
                json.getString(SOUP_NAME), json.getString(PARENT_ID_FIELD_NAME),
                JSONObjectHelper.optString(json, ID_FIELD_NAME),
                JSONObjectHelper.optString(json, MODIFICATION_DATE_FIELD_NAME)
        );
    }

    public ChildrenInfo(String sobjectType, String sobjectTypePlural, String soupName, String parentIdFieldName) {
        this(sobjectType, sobjectTypePlural, soupName, parentIdFieldName, null, null);
    }

    public ChildrenInfo(String sobjectType, String sobjectTypePlural, String soupName, String parentIdFieldName, String idFieldName, String modificationDateFieldName) {
        super(sobjectType, soupName, idFieldName, modificationDateFieldName);
        this.sobjectTypePlural = sobjectTypePlural;
        this.parentIdFieldName = parentIdFieldName;
    }

    public JSONObject asJSON() throws JSONException {
        JSONObject json = super.asJSON();
        json.put(SOBJECT_TYPE_PLURAL, sobjectTypePlural);
        json.put(PARENT_ID_FIELD_NAME, parentIdFieldName);
        return json;
    }
}
