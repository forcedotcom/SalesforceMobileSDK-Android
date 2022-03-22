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
package com.salesforce.androidsdk.mobilesync.target;

import com.salesforce.androidsdk.mobilesync.app.Features;
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager;
import com.salesforce.androidsdk.mobilesync.manager.SyncManager;
import com.salesforce.androidsdk.mobilesync.util.Constants;
import com.salesforce.androidsdk.rest.PrimingRecordsResponse;
import com.salesforce.androidsdk.rest.PrimingRecordsResponse.PrimingRecord;
import com.salesforce.androidsdk.rest.RestRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Target for sync that downloads records using the briefcase (priming records) API
 */
public class BriefcaseSyncDownTarget extends RefreshSyncDownTarget {

    public static final String RECORD_TYPE = "recordType";

    private String recordType;

    // NB: For each sync run - a fresh sync down target is created (by deserializing it from smartstore)
    // The following members are specific to a run
    private List<String> idsToFetch;

    /**
     * Construct BriefcaseSyncDownTarget from json
     * @param target
     * @throws JSONException
     */
    public BriefcaseSyncDownTarget(JSONObject target) throws JSONException {
        super(target);
        this.recordType = target.optString(RECORD_TYPE, Constants.DEFAULT_RECORD_TYPE);

        this.queryType = QueryType.briefcase;
        MobileSyncSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_RELATED_RECORDS);
    }

    /**
     * Construct BriefcaseSyncDownTarget
     *
     * @param fieldlist
     * @param objectType
     * @param soupName
     */
    public BriefcaseSyncDownTarget(List<String> fieldlist, String objectType, String soupName) {
        this(fieldlist, objectType, soupName, Constants.DEFAULT_RECORD_TYPE);
    }

    /**
     * Construct BriefcaseSyncDownTarget
     *
     * @param fieldlist
     * @param objectType
     * @param soupName
     * @param recordType
     */
    public BriefcaseSyncDownTarget(List<String> fieldlist, String objectType, String soupName, String recordType) {
        super(fieldlist, objectType, soupName);
        this.recordType = recordType;

        this.queryType = QueryType.briefcase;
        MobileSyncSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_RELATED_RECORDS);
    }

    /**
     * @return json representation of target
     * @throws JSONException
     */
    public JSONObject asJSON() throws JSONException {
        JSONObject target = super.asJSON();
        target.put(RECORD_TYPE, recordType);
        return target;
    }


    @Override
    public JSONArray startFetch(SyncManager syncManager, long maxTimeStamp) throws IOException, JSONException {
        try {
            idsToFetch = getIdsToFetch(syncManager, maxTimeStamp);
        } catch (Exception e) {
            throw new IOException(e);
        }
        return getIdsToFetchAndFetchFromServer(syncManager);
    }

    @Override
    protected JSONArray getIdsToFetchAndFetchFromServer(SyncManager syncManager) throws IOException, JSONException {
        // If fetch is starting, figuring out totalSize
        if (page == 0) {
            totalSize = idsToFetch.size();
        }

        // Slice of interest
        int startIndex = Math.min(countIdsPerSoql*page, totalSize);
        int endIndex = Math.min(countIdsPerSoql*(page+1), totalSize);
        List<String> idsForCurrentPage = idsToFetch.subList(startIndex, endIndex);

        if (idsForCurrentPage.size() > 0) {
            final JSONArray records = fetchFromServer(syncManager, idsForCurrentPage, fieldlist,
                0 /* no need to filter here, we filtered the list that came back from the briefcase API*/);

            // Increment page if there is more to fetch
            boolean done = endIndex == totalSize;
            page = (done ? 0 : page + 1);
            return records;
        }
        else {
            page = 0; // done
            return null;
        }
    }

    /**
     * Method that calls the priming records API to get all the ids to fetch
     * @param syncManager
     * @param maxTimeStamp
     * @return
     */
    private List<String> getIdsToFetch(SyncManager syncManager, long maxTimeStamp) throws Exception {
        List<String> idsToFetch = new ArrayList<>();
        boolean hasMore = true;
        String relayToken = null;
        do {
            RestRequest request = RestRequest.getRequestForPrimingRecords(syncManager.apiVersion, relayToken);
            PrimingRecordsResponse response = new PrimingRecordsResponse(
                syncManager.sendSyncWithMobileSyncUserAgent(request).asJSONObject());
            Map<String, Map<String, List<PrimingRecord>>> allPrimingRecords = response.primingRecords;
            if (allPrimingRecords.containsKey(objectType) && allPrimingRecords.get(objectType)
                .containsKey(recordType)) {

                List<PrimingRecord> primingRecords = allPrimingRecords.get(objectType)
                    .get(recordType);

                for (PrimingRecord primingRecord : primingRecords) {
                    if (primingRecord.systemModStamp.getTime() >= maxTimeStamp) {
                        idsToFetch.add(primingRecord.id);
                    }
                }
                relayToken = response.relayToken;
                hasMore = relayToken != null;
            } else {
                hasMore = false;
            }
        }
        while (hasMore);

        return idsToFetch;
    }
}
