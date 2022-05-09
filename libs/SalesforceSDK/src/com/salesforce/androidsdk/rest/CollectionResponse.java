/*
 * Copyright (c) 2022-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.rest;

import com.salesforce.androidsdk.util.JSONObjectHelper;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * CollectionCreateResponse: Class to represent response for a sobject collection create/update/upsert or delete request.
 */
public class CollectionResponse {

    public final List<CollectionSubResponse> subResponses = new ArrayList<>();

    public CollectionResponse(JSONArray responseJsonArray) throws JSONException {
        for (int i = 0; i < responseJsonArray.length(); i++) {
            JSONObject result = responseJsonArray.getJSONObject(i);
            subResponses.add(new CollectionSubResponse(result));
        }
    }

    public static class CollectionSubResponse {
        public static final String ID = "id";
        public static final String SUCCESS = "success";
        public static final String ERRORS = "errors";

        public final String id;
        public final boolean success;
        public final List<ErrorResponse> errors;
        public final JSONObject json;

        public CollectionSubResponse(JSONObject subResponseJson) throws JSONException {
            json = subResponseJson;
            id = JSONObjectHelper.optString(subResponseJson, ID);
            success = subResponseJson.getBoolean(SUCCESS);
            errors = new ArrayList<>();
            for (JSONObject errorJson : JSONObjectHelper.<JSONObject>toList(subResponseJson.getJSONArray(ERRORS))) {
                errors.add(new ErrorResponse(errorJson));
            }
        }

        @Override
        public String toString() {
            return json.toString();
        }
    }

    public static class ErrorResponse {
        public static final String STATUS_CODE = "statusCode";
        public static final String MESSAGE = "message";
        public static final String FIELDS = "fields";

        public final String statusCode;
        public final String message;
        public final List<String> fields;
        public final JSONObject json;

        public ErrorResponse(JSONObject errorJson) throws JSONException {
            json = errorJson;
            statusCode = errorJson.getString(STATUS_CODE);
            message = errorJson.getString(MESSAGE);
            fields = JSONObjectHelper.toList(errorJson.getJSONArray(FIELDS));
        }

        @Override
        public String toString() {
            return json.toString();
        }
    }
}