/*
 * Copyright (c) 2019-present, salesforce.com, inc.
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * BatchResponse: Class to represent response for a CompositeRequest.
 *
 */
public class CompositeResponse {

    public static final String COMPOSITE_RESPONSE = "compositeResponse";
    public static final String REFERENCE_ID = "referenceId";
    public static final String HTTP_STATUS_CODE = "httpStatusCode";
    public static final String HTTP_HEADERS = "httpHeaders";
    public static final String BODY = "body";

    public final List<CompositeSubResponse> subResponses = new ArrayList<>();

    public CompositeResponse(JSONObject responseJson) throws JSONException {
        JSONArray results = responseJson.getJSONArray(COMPOSITE_RESPONSE);
        for (int i = 0; i < results.length(); i++) {
            JSONObject result = results.getJSONObject(i);
            subResponses.add(new CompositeSubResponse(result));
        }
    }

    public static class CompositeSubResponse {
        public final Map<String, String> httpHeaders;
        public final int httpStatusCode;
        public final String referenceId;
        public final JSONObject json;

        public CompositeSubResponse(JSONObject subResponseJson) throws JSONException {
            json = subResponseJson;
            httpHeaders = JSONObjectHelper.toMap(subResponseJson.getJSONObject(HTTP_HEADERS));
            httpStatusCode = subResponseJson.getInt(HTTP_STATUS_CODE);
            referenceId = subResponseJson.getString(REFERENCE_ID);
        }

        public JSONObject bodyAsJSONObject() throws JSONException {
            return json.getJSONObject(BODY);
        }

        public JSONArray bodyAsJSONArray() throws JSONException {
            return json.getJSONArray(BODY);
        }

        @Override
        public String toString() {
            return json.toString();
        }
    }
}