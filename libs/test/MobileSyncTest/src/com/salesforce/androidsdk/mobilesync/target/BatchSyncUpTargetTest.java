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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.salesforce.androidsdk.mobilesync.manager.SyncManager;
import com.salesforce.androidsdk.mobilesync.target.CompositeRequestHelper.RecordRequest;
import com.salesforce.androidsdk.mobilesync.target.CompositeRequestHelper.RecordResponse;
import com.salesforce.androidsdk.mobilesync.util.Constants;
import com.salesforce.androidsdk.mobilesync.util.SyncOptions;
import com.salesforce.androidsdk.mobilesync.util.SyncState.MergeMode;
import com.salesforce.androidsdk.rest.CollectionResponse.CollectionSubResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test class for BatchSyncUpTarget.
 * Running all the same tests as SyncUpTargetTest but using a BatchSyncUpTarget
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class BatchSyncUpTargetTest extends SyncUpTargetTest {

    @Test
    public void testProcessesEveryBatchResponseBeforeRetry() throws Exception {
        String missingRecordName = createRecordName(Constants.ACCOUNT);
        String successfulRecordName = createRecordName(Constants.ACCOUNT);
        JSONObject[] records = createAccountsLocally(
                new String[] { missingRecordName, successfulRecordName });
        String missingRecordId = records[0].getString(Constants.ID);
        String successfulRecordId = records[1].getString(Constants.ID);
        String serverId = "001000000000001AAA";
        ResponseRecordingBatchSyncUpTarget target = new ResponseRecordingBatchSyncUpTarget(
                missingRecordId, successfulRecordId, serverId);

        target.syncUpRecords(
                syncManager,
                Arrays.asList(records),
                null,
                MergeMode.OVERWRITE,
                ACCOUNTS_SOUP);

        Assert.assertEquals(
                "The failed record should be retried",
                Integer.valueOf(2),
                target.requestCounts.get(missingRecordId));
        Assert.assertEquals(
                "The successful record should only be sent once",
                Integer.valueOf(1),
                target.requestCounts.get(successfulRecordId));
        Map<String, Map<String, Object>> syncedRecords = getIdToFieldsByName(
                ACCOUNTS_SOUP,
                new String[] { Constants.NAME },
                Constants.NAME,
                new String[] { successfulRecordName });
        Assert.assertTrue(
                "Successful response should store the server ID",
                syncedRecords.containsKey(serverId));
        checkDbStateFlags(
                Arrays.asList(serverId),
                false,
                false,
                false,
                ACCOUNTS_SOUP);
    }

    @Override
    protected void trySyncUp(int numberChanges, SyncOptions options, List<String> createFieldlist, List<String> updateFieldlist, String externalIdFieldName) throws JSONException {
        trySyncUp(new BatchSyncUpTarget(createFieldlist, updateFieldlist, null, null, externalIdFieldName), numberChanges, options, false);
    }

    private static class ResponseRecordingBatchSyncUpTarget extends BatchSyncUpTarget {

        private final String missingRecordId;
        private final String successfulRecordId;
        private final String serverId;
        private final Map<String, Integer> requestCounts = new HashMap<>();

        ResponseRecordingBatchSyncUpTarget(
                String missingRecordId,
                String successfulRecordId,
                String serverId) {
            super(Arrays.asList(Constants.NAME, Constants.DESCRIPTION), null);
            this.missingRecordId = missingRecordId;
            this.successfulRecordId = successfulRecordId;
            this.serverId = serverId;
        }

        @Override
        protected Map<String, RecordResponse> sendRecordRequests(
                SyncManager syncManager,
                List<RecordRequest> recordRequests) throws JSONException, IOException {
            Map<String, RecordResponse> responses = new HashMap<>();
            for (RecordRequest request : recordRequests) {
                String referenceId = request.getReferenceId();
                requestCounts.put(referenceId, requestCounts.getOrDefault(referenceId, 0) + 1);
                if (missingRecordId.equals(referenceId)) {
                    responses.put(referenceId, createResponse(false, null, "ENTITY_IS_DELETED"));
                } else if (successfulRecordId.equals(referenceId)) {
                    responses.put(referenceId, createResponse(true, serverId, null));
                }
            }
            return responses;
        }

        private RecordResponse createResponse(boolean success, String id, String errorCode)
                throws JSONException {
            JSONObject response = new JSONObject();
            response.put("success", success);
            response.put("errors", new JSONArray());
            if (success) {
                response.put("id", id);
            } else {
                JSONObject error = new JSONObject();
                error.put("statusCode", errorCode);
                error.put("message", "Test record is unavailable");
                error.put("fields", new JSONArray());
                response.getJSONArray("errors").put(error);
            }
            return RecordResponse.Companion.fromCollectionSubResponse(
                    new CollectionSubResponse(response));
        }
    }
}
