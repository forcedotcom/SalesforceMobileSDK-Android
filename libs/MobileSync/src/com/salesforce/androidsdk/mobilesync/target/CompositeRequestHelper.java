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
package com.salesforce.androidsdk.mobilesync.target;

import com.salesforce.androidsdk.mobilesync.manager.SyncManager;
import com.salesforce.androidsdk.mobilesync.util.Constants;
import com.salesforce.androidsdk.rest.CompositeResponse;
import com.salesforce.androidsdk.rest.CompositeResponse.CompositeSubResponse;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class CompositeRequestHelper {

    /**
     * Build and send composite request
     * @param syncManager
     * @param allOrNone
     * @param refIdToRequests
     * @return map of ref id to composite sub response
     * @throws JSONException
     * @throws IOException
     */
     public static Map<String, CompositeSubResponse> sendCompositeRequest(SyncManager syncManager, boolean allOrNone, LinkedHashMap<String, RestRequest> refIdToRequests) throws JSONException, IOException {
        RestRequest compositeRequest = RestRequest.getCompositeRequest(syncManager.apiVersion, allOrNone, refIdToRequests);
        RestResponse response = syncManager.sendSyncWithMobileSyncUserAgent(compositeRequest);
        if (!response.isSuccess()) {
            throw new SyncManager.MobileSyncException("sendCompositeRequest:" + response.toString());
        }
        CompositeResponse compositeResponse = new CompositeResponse(response.asJSONObject());
        Map<String, CompositeSubResponse> refIdToResponses = new HashMap<>();
        for (CompositeSubResponse subResponse : compositeResponse.subResponses) {
            refIdToResponses.put(subResponse.referenceId, subResponse);
        }
        return refIdToResponses;
    }

    /**
     * @return ref id to server id map if successful
     */
    public static Map<String, String> parseIdsFromResponses(Collection<CompositeSubResponse> responses) throws JSONException {
        Map<String, String> refIdToId = new HashMap<>();
        for (CompositeSubResponse response : responses) {
            // Status code will be 201 if record just got created.
            // However if:
            // - we are upserting by external id a locally created record
            // - and the network got disconnected after request was processed by server but before response made it to the client,
            // - and this is our second attempt to run the sync up
            // Then the status code will be 200 since the record already exists
            // See:
            // - https://github.com/forcedotcom/SalesforceMobileSDK-iOS/issues/3258
            // - https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/dome_upsert.htm
            // So the code checks for success without expecting to find an id in all cases
            if (response.isSuccess()) {
                JSONObject responseBodyResponse = response.bodyAsJSONObject();
                if (responseBodyResponse != null) {
                    String serverId = JSONObjectHelper.optString(response.bodyAsJSONObject(), Constants.LID);
                    if (serverId != null) {
                        refIdToId.put(response.referenceId, serverId);
                    }
                }
            }
        }
        return refIdToId;
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
