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

import com.salesforce.androidsdk.smartsync.manager.SyncManager;
import com.salesforce.androidsdk.smartsync.util.Constants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Custom sync down target for tests.
 */
public class TestSyncDownTarget extends SyncDownTarget {

    // Fields in records
    public static final String NAME = "name";
    public static final String SEQ = "seq";

    // All the records
    private final JSONObject[] records;

    // Target config
    private final int numberOfRecords;
    private final int numberOfRecordsPerPage;

    // Target state
    private int position = 0;

    public TestSyncDownTarget(int numberOfRecords, int numberOfRecordsPerPage) throws JSONException {
        this.numberOfRecords = numberOfRecords;
        this.numberOfRecordsPerPage = numberOfRecordsPerPage;
        this.records = new JSONObject[numberOfRecords];
        for (int i=0; i<numberOfRecords; i++) {
            JSONObject record = new JSONObject();
            record.put(Constants.ID, "" + (10000 + i));
            record.put(NAME, "record" + i);
            record.put(SEQ, i);
            this.records[i] = record;
        }
    }

    public JSONArray recordsFromPosition() {
        if (this.position >= this.numberOfRecords) {
            return null;
        }

        JSONArray arrayForPage = new JSONArray();
        int i = this.position;
        int limit = Math.min(this.position + this.numberOfRecordsPerPage, this.numberOfRecords);
        do {
            arrayForPage.put(records[i]);
            i++;
        } while (i < limit);
        this.position = i;
        return arrayForPage;
    }

    @Override
    public boolean isSyncDownSortedByLatestModification() {
        return true;
    }

    @Override
    public String getModificationDateFieldName() {
        return SEQ;
    }

    @Override
    public JSONArray startFetch(SyncManager syncManager, long maxTimeStamp) throws IOException, JSONException {
        this.position = (int) maxTimeStamp;
        return recordsFromPosition();
    }

    @Override
    public JSONArray continueFetch(SyncManager syncManager) throws IOException, JSONException {
        return recordsFromPosition();
    }

    @Override
    protected Set<String> getRemoteIds(SyncManager syncManager, Set<String> localIds) throws IOException, JSONException {
        Set<String> remoteIds = new HashSet<>();
        for (JSONObject record : records) {
            remoteIds.add(record.getString(Constants.ID));
        }
        return remoteIds;
    }
}
