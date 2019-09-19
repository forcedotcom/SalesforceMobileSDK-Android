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


import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.mobilesync.target.SyncUpTarget;
import com.salesforce.androidsdk.mobilesync.util.MobileSyncLogger;
import com.salesforce.androidsdk.mobilesync.util.SyncOptions;
import com.salesforce.androidsdk.mobilesync.util.SyncState;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

/**
 * Runnable class responsible for running a sync up
 */
public class SyncUpTask extends SyncTask {

    private static final String TAG = "SyncUpTask";

    public SyncUpTask(SyncManager syncManager, SyncState sync, SyncManager.SyncUpdateCallback callback) {
        super(syncManager, sync, callback);
    }

    @Override
    protected void runSync() throws Exception {
        final SyncUpTarget target = (SyncUpTarget) sync.getTarget();
        final List<String> dirtyRecordIds = new ArrayList<>(target.getIdsOfRecordsToSyncUp(syncManager, sync.getSoupName()));
        sync.setTotalSize(dirtyRecordIds.size());
        syncUp(sync, callback, dirtyRecordIds);
    }

    protected void syncUp(SyncState sync, SyncManager.SyncUpdateCallback callback, List<String> dirtyRecordIds) throws JSONException, IOException {
        final String soupName = sync.getSoupName();
        final SyncUpTarget target = (SyncUpTarget) sync.getTarget();
        final SyncOptions options = sync.getOptions();
        int totalSize = dirtyRecordIds.size();

        updateSync(sync, SyncState.Status.RUNNING, 0, callback);
        int i = 0;
        for (final String id : dirtyRecordIds) {
            checkIfStopRequested();

            JSONObject record = target.getFromLocalStore(syncManager, soupName, id);

            if (shouldSyncUpRecord(target, record, options)) {
                syncUpOneRecord(target, soupName, record, options);
            }

            // Updating status
            int progress = (i + 1) * 100 / totalSize;
            if (progress < 100) {
                updateSync(sync, SyncState.Status.RUNNING, progress, callback);
            }

            // Incrementing i
            i++;
        }
    }

    protected boolean shouldSyncUpRecord(SyncUpTarget target, JSONObject record, SyncOptions options) throws IOException, JSONException {
        /*
         * Checks if we are attempting to sync up a record that has been updated
         * on the server AFTER the client's last sync down. If the merge mode
         * passed in tells us to leave the record alone under these
         * circumstances, we will do nothing and return here.
         */
        if (options.getMergeMode() == SyncState.MergeMode.LEAVE_IF_CHANGED &&
                !target.isNewerThanServer(syncManager, record)) {

            // Nothing to do for this record
            MobileSyncLogger.d(TAG, "syncUpOneRecord: Record not synched since client does not have the latest from server", record);
            return false;
        }
        else {
            return true;
        }
    }

    private void syncUpOneRecord(SyncUpTarget target, String soupName,
                                 JSONObject record, SyncOptions options) throws JSONException, IOException {
        MobileSyncLogger.d(TAG, "syncUpOneRecord called", record);


        // Do we need to do a create, update or delete
        boolean locallyDeleted = target.isLocallyDeleted(record);
        boolean locallyCreated = target.isLocallyCreated(record);
        boolean locallyUpdated = target.isLocallyUpdated(record);

        Action action = null;
        if (locallyDeleted)
            action = Action.delete;
        else if (locallyCreated)
            action = Action.create;
        else if (locallyUpdated)
            action = Action.update;

        if (action == null) {
            // Nothing to do for this record
            return;
        }

        // Create/update/delete record on server and update smartstore
        String recordServerId;
        int statusCode;
        switch (action) {
            case create:
                recordServerId = target.createOnServer(syncManager, record, options.getFieldlist());
                // Success
                if (recordServerId != null) {
                    record.put(target.getIdFieldName(), recordServerId);
                    target.cleanAndSaveInLocalStore(syncManager, soupName, record);
                }
                // Failure
                else {
                    target.saveRecordToLocalStoreWithLastError(syncManager, soupName, record);
                }
                break;
            case delete:
                statusCode = (locallyCreated
                        ? HttpURLConnection.HTTP_NOT_FOUND // if locally created it can't exist on the server - we don't need to actually do the deleteOnServer call
                        : target.deleteOnServer(syncManager, record));
                // Success
                if (RestResponse.isSuccess(statusCode) || statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    target.deleteFromLocalStore(syncManager, soupName, record);
                }
                // Failure
                else {
                    target.saveRecordToLocalStoreWithLastError(syncManager, soupName, record);
                }
                break;
            case update:
                statusCode = target.updateOnServer(syncManager, record, options.getFieldlist());
                // Success
                if (RestResponse.isSuccess(statusCode)) {
                    target.cleanAndSaveInLocalStore(syncManager, soupName, record);
                }
                // Handling remotely deleted records
                else if (statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    if (options.getMergeMode() == SyncState.MergeMode.OVERWRITE) {
                        recordServerId = target.createOnServer(syncManager, record, options.getFieldlist());
                        if (recordServerId != null) {
                            record.put(target.getIdFieldName(), recordServerId);
                            target.cleanAndSaveInLocalStore(syncManager, soupName, record);
                        }
                    }
                    else {
                        // Leave local record alone
                    }
                }
                // Failure
                else {
                    target.saveRecordToLocalStoreWithLastError(syncManager, soupName, record);
                }
                break;
        }
    }

    /**
     * Enum for action
     *
     */
    public enum Action {
        create,
        update,
        delete
    }
}
