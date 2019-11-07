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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CompositeRequest: Class to represent a composite request.
 *
 */
public class CompositeRequest extends RestRequest {

    public final LinkedHashMap<String, RestRequest> refIdToRequests;
    public final boolean allOrNone;

    public CompositeRequest(String apiVersion, boolean allOrNone, LinkedHashMap<String, RestRequest> refIdToRequests) throws JSONException {
        super(RestMethod.POST, RestAction.COMPOSITE.getPath(apiVersion), computeCompositeRequestJson(allOrNone, refIdToRequests));
        this.refIdToRequests = refIdToRequests;
        this.allOrNone = allOrNone;
    }

    public static JSONObject computeCompositeRequestJson(boolean allOrNone, LinkedHashMap<String, RestRequest> refIdToRequests) throws JSONException {
        JSONArray requestsArrayJson = new JSONArray();
        for (Map.Entry<String,RestRequest> entry : refIdToRequests.entrySet()) {
            String referenceId = entry.getKey();
            RestRequest request = entry.getValue();
            JSONObject requestJson = request.asJSON();
            requestJson.put(REFERENCE_ID, referenceId);
            requestsArrayJson.put(requestJson);
        }
        JSONObject compositeRequestJson =  new JSONObject();
        compositeRequestJson.put(COMPOSITE_REQUEST, requestsArrayJson);
        compositeRequestJson.put(ALL_OR_NONE, allOrNone);
        return compositeRequestJson;
    }

    /**
     * Builder class for CompositeRequest
     */
    public static class CompositeRequestBuilder {

        private LinkedHashMap<String, RestRequest> refIdToRequests = new LinkedHashMap<>();
        private boolean allOrNone;

        public CompositeRequestBuilder addRequest(String refId, RestRequest request) {
            refIdToRequests.put(refId, request);
            return this;
        }


        public CompositeRequestBuilder setAllOrNone(boolean b) {
            allOrNone = b;
            return this;
        }

        public CompositeRequest build(String apiVersion) throws JSONException {
            return new CompositeRequest(apiVersion, allOrNone, refIdToRequests);
        }
    }

}
