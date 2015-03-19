/*
 * Copyright (c) 2015, salesforce.com, inc.
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
package com.salesforce.androidsdk.smartsync.manager;

import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.androidsdk.smartsync.util.SyncDownTarget;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;


/**
 * Custom sync down target for tests
 */
public class TestSyncDownTarget extends SyncDownTarget {

    public static final String FAIL_FETCH = "failFetch";
    public static final String FETCH_COUNTS = "fetchCounts";
    private final boolean failFetch;
    private final int[] fetchCounts;
    private int atFetch;

    public TestSyncDownTarget(boolean failFetch, int[] fetchCounts) {
        this.failFetch = failFetch;
        this.fetchCounts = fetchCounts;
    }

    public static SyncDownTarget fromJSON(JSONObject target) throws JSONException {
        boolean failFetch = target.getBoolean(FAIL_FETCH);
        JSONArray fetchCountsJson = target.getJSONArray(FETCH_COUNTS);
        int[] fetchCounts = new int[fetchCountsJson.length()];
        for (int i =0; i<fetchCountsJson.length(); i++) {
            fetchCounts[i] = fetchCountsJson.getInt(i);
        }
        return new TestSyncDownTarget(failFetch, fetchCounts);
    }

    @Override
    public JSONObject asJSON() throws JSONException {
        JSONObject target = new JSONObject();
        target.put(ANDROID_IMPL, getClass().getName());
        target.put(FAIL_FETCH, failFetch);
        JSONArray fetchCountsJson = new JSONArray();
        for (int c : fetchCounts) {
            fetchCountsJson.put(c);
        }
        target.put(FETCH_COUNTS, fetchCountsJson);
        return target;
    }

    @Override
    public JSONArray startFetch(SyncManager syncManager, long maxTimeStamp) throws IOException, JSONException {
        if (failFetch) {
            throw new RuntimeException("Failing fetch");
        }

        int totalCount = 0;
        for (int c : fetchCounts) {
            totalCount += c;
        }
        totalSize = totalCount;

        atFetch = 0;
        return getRecords(atFetch, fetchCounts[atFetch]);
    }

    @Override
    public JSONArray continueFetch(SyncManager syncManager) throws IOException, JSONException {
        atFetch ++;

        if (atFetch >= fetchCounts.length) {
            return null;
        }

        return getRecords(atFetch, fetchCounts[atFetch]);
    }

    private JSONArray getRecords(int page, int count) throws JSONException {
        JSONArray result = new JSONArray();
        for (int i=0; i<count; i++) {
            JSONObject obj = new JSONObject();
            obj.put(Constants.ID, Constants.ID + "." + page + "." + i);
            obj.put(Constants.NAME, Constants.NAME + "." + page + "." + i);
            result.put(obj);
        }
        return result;
    }

    @Override
    public int getTotalSize() {
        return super.getTotalSize();
    }
}