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


import com.salesforce.androidsdk.analytics.EventBuilderHelper;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.mobilesync.util.MobileSyncLogger;
import com.salesforce.androidsdk.mobilesync.util.SyncState;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Abstract super class of runnable classes responsible for running syncs
 */
public abstract class SyncTask implements Runnable {

    // Constant
    private static final String TAG = "SyncTask";
    static final int UNCHANGED = -1;

    // Fields
    protected final SyncManager syncManager;
    protected final SyncState sync;
    protected final SyncManager.SyncUpdateCallback callback;

    public SyncTask(SyncManager syncManager, SyncState sync, SyncManager.SyncUpdateCallback  callback) {
        this.syncManager = syncManager;
        this.sync = sync;
        this.callback = callback;

        syncManager.addToActiveSyncs(this);
        updateSync(sync, SyncState.Status.RUNNING, 0, callback);
        // XXX not actually running on worker thread until run() gets invoked
        //     may be we should introduce another state?
    }

    public Long getSyncId() {
        return sync.getId();
    }

    /**
     * Check if stop was called
     * Throw a SyncManagerStoppedException if it was
     */
    public void checkIfStopRequested() {
        syncManager.checkAcceptingSyncs();
    }

    @Override
    public void run() {
        try {
            checkIfStopRequested();
            runSync();
            updateSync(sync, SyncState.Status.DONE, 100, callback);
        } catch (SyncManager.SyncManagerStoppedException se) {
            MobileSyncLogger.d(TAG, "Sync stopped", se);
            // Update status to failed
            updateSync(sync, SyncState.Status.STOPPED, UNCHANGED, callback);
        } catch (RestClient.RefreshTokenRevokedException re) {
            MobileSyncLogger.e(TAG, "Exception thrown running sync", re);
            // Do not do anything - let the logout go through!
        } catch (Exception e) {
            MobileSyncLogger.e(TAG, "Exception thrown running sync", e);

            //Set error message to sync state
            sync.setError(e.getMessage());
            // Update status to failed
            updateSync(sync, SyncState.Status.FAILED, UNCHANGED, callback);
        } finally {
            syncManager.removeFromActiveSyncs(this);
        }
    }

    /**
     * Update sync with new status, progress, totalSize
     *
     * @param sync
     * @param status
     * @param progress pass -1 to keep the current value
     * @param callback
     */
    protected void updateSync(SyncState sync, SyncState.Status status, int progress, SyncManager.SyncUpdateCallback callback) {
        try {
            sync.setStatus(status);
            if (progress != UNCHANGED) {
                sync.setProgress(progress);
            }
            switch (status) {
                case NEW:
                case RUNNING:
                    break;
                case STOPPED:
                case DONE:
                case FAILED:
                    int totalSize = sync.getTotalSize();
                    final JSONObject attributes = new JSONObject();
                    try {
                        if (totalSize > 0) {
                            attributes.put("numRecords", totalSize);
                        }
                        attributes.put("syncId", sync.getId());
                        attributes.put("syncTarget", sync.getTarget().getClass().getName());
                        attributes.put(EventBuilderHelper.START_TIME, sync.getStartTime());
                        attributes.put(EventBuilderHelper.END_TIME, sync.getEndTime());
                    } catch (JSONException e) {
                        MobileSyncLogger.e(TAG, "Exception thrown while building attributes", e);
                    }
                    EventBuilderHelper.createAndStoreEvent(sync.getType().name(), null, TAG, attributes);
                    break;
            }
            sync.save(syncManager.getSmartStore());
        } catch (JSONException e) {
            MobileSyncLogger.e(TAG, "Unexpected JSON error for sync: " + sync.getId(), e);
        } catch (SmartStore.SmartStoreException e) {
            MobileSyncLogger.e(TAG, "Unexpected smart store error for sync: " + sync.getId(), e);
        } finally {
            if (callback != null) {
                callback.onUpdate(sync);
            }
        }
    }


    protected abstract void runSync() throws Exception;

}
