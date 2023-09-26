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
package com.salesforce.androidsdk.mobilesync.manager

import com.salesforce.androidsdk.analytics.EventBuilderHelper
import com.salesforce.androidsdk.mobilesync.manager.SyncManager.SyncManagerStoppedException
import com.salesforce.androidsdk.mobilesync.manager.SyncManager.SyncUpdateCallback
import com.salesforce.androidsdk.mobilesync.util.MobileSyncLogger
import com.salesforce.androidsdk.mobilesync.util.SyncState
import com.salesforce.androidsdk.mobilesync.util.SyncState.Status.NEW
import com.salesforce.androidsdk.mobilesync.util.SyncState.Status.RUNNING
import com.salesforce.androidsdk.mobilesync.util.SyncState.Status.STOPPED
import com.salesforce.androidsdk.rest.RestClient.RefreshTokenRevokedException
import com.salesforce.androidsdk.smartstore.store.SmartStore.SmartStoreException
import org.json.JSONException
import org.json.JSONObject

/**
 * Abstract super class of runnable classes responsible for running syncs
 */
abstract class SyncTask(
    protected val syncManager: SyncManager,
    protected val sync: SyncState,
    protected val callback: SyncUpdateCallback?
) : Runnable {
    init {
        syncManager.addToActiveSyncs(this)
        updateSync(sync, RUNNING, 0, callback)
        // XXX not actually running on worker thread until run() gets invoked
        //     may be we should introduce another state?
    }

    val syncId: Long
        get() = sync.id

    /**
     * Check if stop was called
     * Throw a SyncManagerStoppedException if it was
     */
    fun checkIfStopRequested() {
        syncManager.checkAcceptingSyncs()
    }

    override fun run() {
        try {
            checkIfStopRequested()
            runSync()
            updateSync(sync, SyncState.Status.DONE, 100, callback)
        } catch (se: SyncManagerStoppedException) {
            MobileSyncLogger.d(TAG, "Sync stopped")
            // Update status to stopped
            updateSync(sync, STOPPED, UNCHANGED, callback)
        } catch (re: RefreshTokenRevokedException) {
            MobileSyncLogger.e(TAG, "Exception thrown running sync", re)
            // Do not do anything - let the logout go through!
        } catch (e: Exception) {
            MobileSyncLogger.e(TAG, "Exception thrown running sync", e)

            //Set error message to sync state
            sync.error = e.message
            // Update status to failed
            updateSync(sync, SyncState.Status.FAILED, UNCHANGED, callback)
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
    protected open fun updateSync(
        sync: SyncState,
        status: SyncState.Status,
        progress: Int,
        callback: SyncUpdateCallback?
    ) {
        try {
            sync.status = status
            if (progress != UNCHANGED) {
                sync.progress = progress
            }
            when (status) {
                NEW, RUNNING -> {}
                STOPPED, SyncState.Status.DONE, SyncState.Status.FAILED -> {
                    val totalSize = sync.totalSize
                    val attributes = JSONObject()
                    try {
                        if (totalSize > 0) {
                            attributes.put("numRecords", totalSize)
                        }
                        attributes.put("syncId", sync.id)
                        attributes.put("syncTarget", sync.target.javaClass.name)
                        attributes.put(EventBuilderHelper.START_TIME, sync.startTime)
                        attributes.put(EventBuilderHelper.END_TIME, sync.endTime)
                    } catch (e: JSONException) {
                        MobileSyncLogger.e(TAG, "Exception thrown while building attributes", e)
                    }
                    EventBuilderHelper.createAndStoreEvent(sync.type.name, null, TAG, attributes)
                }
            }
            sync.save(syncManager.smartStore)
        } catch (e: JSONException) {
            MobileSyncLogger.e(TAG, "Unexpected JSON error for sync: ${sync.id}", e)
        } catch (e: SmartStoreException) {
            MobileSyncLogger.e(TAG, "Unexpected smart store error for sync: ${sync.id}", e)
        } finally {
            // Removing from active syncs before calling callback
            if (!sync.isRunning) {
                syncManager.removeFromActiveSyncs(this)
            }
            callback?.onUpdate(sync)
        }
    }

    @Throws(Exception::class)
    protected abstract fun runSync()

    companion object {
        // Constant
        private const val TAG = "SyncTask"
        const val UNCHANGED = -1
    }
}