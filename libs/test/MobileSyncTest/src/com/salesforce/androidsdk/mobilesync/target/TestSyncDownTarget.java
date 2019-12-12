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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Set;

/**
 * Custom sync down target for tests.
 */
public class TestSyncDownTarget extends SyncDownTarget {

    // Fields in serialized target
    public static final String PREFIX = "prefix";
    public static final String NUMBER_OF_RECORDS_PER_PAGE = "numberOfRecordsPerPage";
    public static final String NUMBER_OF_RECORDS = "numberOfRecords";
    public static final String SLEEP_PER_FETCH = "sleepPerFetch";

    // Target config
    private final String prefix;
    private final int numberOfRecords;
    private final int numberOfRecordsPerPage;
    private final int sleepPerFetch;

    // Target state
    private int position = 0;
    private int totalSize;

    // All the records
    private final JSONObject[] records;

    public TestSyncDownTarget(JSONObject target) throws JSONException {
        this(target.getString(PREFIX), target.getInt(NUMBER_OF_RECORDS), target.getInt(NUMBER_OF_RECORDS_PER_PAGE), target.getInt(SLEEP_PER_FETCH));
    }

    @Override
    public JSONObject asJSON() throws JSONException {
        JSONObject target = super.asJSON();
        target.put(PREFIX, this.prefix);
        target.put(NUMBER_OF_RECORDS, this.numberOfRecords);
        target.put(NUMBER_OF_RECORDS_PER_PAGE, this.numberOfRecordsPerPage);
        target.put(SLEEP_PER_FETCH, this.sleepPerFetch);
        return target;
    }

    public TestSyncDownTarget(String prefix, int numberOfRecords, int numberOfRecordsPerPage, int sleepPerFetch) throws JSONException {
        this.queryType = QueryType.custom;
        this.prefix = prefix;
        this.numberOfRecords = numberOfRecords;
        this.numberOfRecordsPerPage = numberOfRecordsPerPage;
        this.sleepPerFetch = sleepPerFetch;
        this.records = new JSONObject[numberOfRecords];
        for (int i=0; i<numberOfRecords; i++) {
            JSONObject record = new JSONObject();
            record.put(Constants.ID, idForPosition(i));
            record.put(Constants.LAST_MODIFIED_DATE, Constants.TIMESTAMP_FORMAT.format(dateForPosition(i)));
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
    public int getTotalSize() {
        return this.totalSize;
    }

    @Override
    public JSONArray startFetch(SyncManager syncManager, long maxTimeStamp) {
        this.position = positionForDate(maxTimeStamp);
        this.totalSize = numberOfRecords - this.position;
        sleepIfNeeded();
        return recordsFromPosition();
    }

    private void sleepIfNeeded() {
        if (sleepPerFetch > 0) {
            try {
                Thread.sleep(sleepPerFetch);
            } catch (InterruptedException e) {
            }
        }
    }

    @Override
    public JSONArray continueFetch(SyncManager syncManager) throws IOException, JSONException {
        sleepIfNeeded();
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

    public String idForPosition(int i) {
        return this.prefix + (10000 + i);
    }

    public Date dateForPosition(int i) {
        return new GregorianCalendar(2019, Calendar.MARCH, 1, 12, i /60, i % 60).getTime();
    }

    public int positionForDate(long time) {
        for (int i=0; i<records.length; i++) {
            if (dateForPosition(i).getTime() > time) {
                return i;
            }
        }
        return records.length;
    }

    public String getIdPrefix() {
        return this.prefix;
    }
}
