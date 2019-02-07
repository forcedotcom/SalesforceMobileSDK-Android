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
package com.salesforce.androidsdk.smartsync.target;

import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartsync.manager.SyncManager;
import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class CompositeRequestHelper {

    // Constants
    public static final String COMPOSITE_RESPONSE = "compositeResponse";
    public static final String REFERENCE_ID = "referenceId";
    public static final String HTTP_STATUS_CODE = "httpStatusCode";
    public static final String BODY = "body";

    /**
     * Build and send composite request
     * @param syncManager
     * @param allOrNone
     * @param refIdToRequests
     * @return
     * @throws JSONException
     * @throws IOException
     */
     public static Map<String, JSONObject> sendCompositeRequest(SyncManager syncManager, boolean allOrNone, LinkedHashMap<String, RestRequest> refIdToRequests) throws JSONException, IOException {
        RestRequest compositeRequest = RestRequest.getCompositeRequest(syncManager.apiVersion, allOrNone, refIdToRequests);
        RestResponse compositeResponse = syncManager.sendSyncWithSmartSyncUserAgent(compositeRequest);
        if (!compositeResponse.isSuccess()) {
            throw new SyncManager.SmartSyncException("sendCompositeRequest:" + compositeResponse.toString());
        }
        JSONArray responses = compositeResponse.asJSONObject().getJSONArray(COMPOSITE_RESPONSE);
        Map<String, JSONObject> refIdToResponses = new HashMap<>();
        for (int i = 0; i < responses.length(); i++) {
            JSONObject response = responses.getJSONObject(i);
            refIdToResponses.put(response.getString(REFERENCE_ID), response);
        }
        return refIdToResponses;
    }

    /**
     * @return ref id to server id map if successful
     */
    public static Map<String, String> parseIdsFromResponse(Map<String, JSONObject> refIdToResponses) throws JSONException {
        Map<String, String> refIdtoId = new HashMap<>();
        for (String refId : refIdToResponses.keySet()) {
            JSONObject response = refIdToResponses.get(refId);
            if (response.getInt(HTTP_STATUS_CODE) == HttpURLConnection.HTTP_CREATED) {
                String serverId = response.getJSONObject(BODY).getString(Constants.LID);
                refIdtoId.put(refId, serverId);
            }
        }
        return refIdtoId;
    }


    /**
     * Update id field with server id
     * @param record
     * @param fieldWithRefId
     * @param refIdToServerId
     * @throws JSONException
     */
    public static void updateReferences(JSONObject record, String fieldWithRefId, Map<String, String> refIdToServerId) throws JSONException {
        String refId = JSONObjectHelper.optString(record, fieldWithRefId);
        if (refId != null && refIdToServerId.containsKey(refId)) {
            record.put(fieldWithRefId, refIdToServerId.get(refId));
        }
    }

}
