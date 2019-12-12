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
package com.salesforce.androidsdk.mobilesync.manager;


import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget;
import com.salesforce.androidsdk.mobilesync.util.SyncState;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;

/**
 * Runnable class responsible for running a sync down
 */
public class SyncDownTask extends SyncTask {

    public SyncDownTask(SyncManager syncManager, SyncState sync, SyncManager.SyncUpdateCallback callback) {
        super(syncManager, sync, callback);
    }

    @Override
    protected void runSync() throws Exception {
        String soupName = sync.getSoupName();
        SyncDownTarget target = (SyncDownTarget) sync.getTarget();
        SyncState.MergeMode mergeMode = sync.getMergeMode();
        long maxTimeStamp = sync.getMaxTimeStamp();
        JSONArray records = target.startFetch(syncManager, maxTimeStamp);
        int countSaved = 0;
        int totalSize = target.getTotalSize();
        sync.setTotalSize(totalSize);
        updateSync(sync, SyncState.Status.RUNNING, 0, callback);
        final String idField = sync.getTarget().getIdFieldName();

        // Get ids of records to leave alone
        Set<String> idsToSkip = null;
        if (mergeMode == SyncState.MergeMode.LEAVE_IF_CHANGED) {
            idsToSkip = target.getIdsToSkip(syncManager, soupName);
        }

        while (records != null) {
            checkIfStopRequested();

            // Figure out records to save
            JSONArray recordsToSave = idsToSkip == null ? records : removeWithIds(records, idsToSkip, idField);

            // Save to smartstore.
            target.saveRecordsToLocalStore(syncManager, soupName, recordsToSave, sync.getId());
            countSaved += records.length();
            maxTimeStamp = Math.max(maxTimeStamp, target.getLatestModificationTimeStamp(records));

            // Updating maxTimeStamp as we go if records are ordered by latest modification
            if (target.isSyncDownSortedByLatestModification()) {
                sync.setMaxTimeStamp(maxTimeStamp);
            }

            // Update sync status.
            if (countSaved < totalSize) {
                updateSync(sync, SyncState.Status.RUNNING, countSaved*100 / totalSize, callback);
            }

            // Fetch next records, if any.
            records = target.continueFetch(syncManager);
        }

        // Updating maxTimeStamp once at the end if records are NOT ordered by latest modification
        if (!target.isSyncDownSortedByLatestModification()) {
            sync.setMaxTimeStamp(maxTimeStamp);
        }
    }

    private JSONArray removeWithIds(JSONArray records, Set<String> idsToSkip, String idField) throws JSONException {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.getJSONObject(i);

            // Keep ?
            String id = JSONObjectHelper.optString(record, idField);
            if (id == null || !idsToSkip.contains(id)) {
                arr.put(record);
            }
        }
        return arr;
    }


}
