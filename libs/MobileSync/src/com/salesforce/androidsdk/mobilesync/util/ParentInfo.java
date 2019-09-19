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
package com.salesforce.androidsdk.mobilesync.util;

import org.json.JSONException;
import org.json.JSONObject;

import com.salesforce.androidsdk.util.JSONObjectHelper;

/**
 * Simple object to capture details of parent in parent-child relationship
 */
public class ParentInfo {

    // Constants
    public static final String SOBJECT_TYPE = "sobjectType";
    public static final String SOUP_NAME = "soupName";
    public static final String ID_FIELD_NAME = "idFieldName";
    public static final String MODIFICATION_DATE_FIELD_NAME = "modificationDateFieldName";

    // Fields
    public final String sobjectType;
    public final String idFieldName;
    public final String modificationDateFieldName;
    public final String soupName;

    public ParentInfo(JSONObject json) throws JSONException {
        this(
                json.getString(SOBJECT_TYPE),
                json.getString(SOUP_NAME), JSONObjectHelper.optString(json, ID_FIELD_NAME), JSONObjectHelper.optString(json, MODIFICATION_DATE_FIELD_NAME)
        );
    }

    public ParentInfo(String sobjectType, String soupName) {
        this(sobjectType, soupName, null, null);
    }

    public ParentInfo(String sobjectType, String soupName, String idFieldName, String modificationDateFieldName) {
        this.sobjectType = sobjectType;
        this.soupName = soupName;
        this.idFieldName = idFieldName != null ? idFieldName : Constants.ID;
        this.modificationDateFieldName = modificationDateFieldName != null ? modificationDateFieldName : Constants.LAST_MODIFIED_DATE;
    }

    public JSONObject asJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(SOUP_NAME, soupName);
        json.put(SOBJECT_TYPE, sobjectType);
        json.put(ID_FIELD_NAME, idFieldName);
        json.put(MODIFICATION_DATE_FIELD_NAME, modificationDateFieldName);
        return json;
    }
}
