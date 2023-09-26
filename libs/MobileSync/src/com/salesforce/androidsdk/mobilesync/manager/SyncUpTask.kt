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

import com.salesforce.androidsdk.mobilesync.manager.SyncManager.SyncUpdateCallback
import com.salesforce.androidsdk.mobilesync.manager.SyncUpTask.Action.create
import com.salesforce.androidsdk.mobilesync.manager.SyncUpTask.Action.delete
import com.salesforce.androidsdk.mobilesync.manager.SyncUpTask.Action.update
import com.salesforce.androidsdk.mobilesync.target.SyncUpTarget
import com.salesforce.androidsdk.mobilesync.util.MobileSyncLogger
import com.salesforce.androidsdk.mobilesync.util.SyncOptions
import com.salesforce.androidsdk.mobilesync.util.SyncState
import com.salesforce.androidsdk.mobilesync.util.SyncState.MergeMode
import com.salesforce.androidsdk.rest.RestResponse
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection

/**
 * Runnable class responsible for running a sync up
 */
open class SyncUpTask(syncManager: SyncManager, sync: SyncState, callback: SyncUpdateCallback?) :
    SyncTask(syncManager, sync, callback) {
    @Throws(Exception::class)
    override fun runSync() {
        val target = sync.target as SyncUpTarget
        val dirtyRecordIds: List<String> = ArrayList(
            target.getIdsOfRecordsToSyncUp(syncManager, sync.soupName)
        )
        sync.totalSize = dirtyRecordIds.size
        syncUp(sync, callback, dirtyRecordIds)
    }

    @Throws(JSONException::class, IOException::class)
    protected open fun syncUp(
        sync: SyncState,
        callback: SyncUpdateCallback?,
        dirtyRecordIds: List<String>
    ) {
        val soupName = sync.soupName
        val target = sync.target as SyncUpTarget
        val options = sync.options
        val totalSize = dirtyRecordIds.size
        updateSync(sync, SyncState.Status.RUNNING, 0, callback)
        for ((i, id) in dirtyRecordIds.withIndex()) {
            checkIfStopRequested()
            val record = target.getFromLocalStore(syncManager, soupName, id)
            if (shouldSyncUpRecord(target, record, options)) {
                syncUpOneRecord(target, soupName, record, options)
            }

            // Updating status
            val progress = (i + 1) * 100 / totalSize
            if (progress < 100) {
                updateSync(sync, SyncState.Status.RUNNING, progress, callback)
            }

            // Incrementing i
        }
    }

    @Throws(IOException::class, JSONException::class)
    protected fun shouldSyncUpRecord(
        target: SyncUpTarget,
        record: JSONObject,
        options: SyncOptions
    ): Boolean {
        /*
         * Checks if we are attempting to sync up a record that has been updated
         * on the server AFTER the client's last sync down. If the merge mode
         * passed in tells us to leave the record alone under these
         * circumstances, we will do nothing and return here.
         */
        return if (options.mergeMode == MergeMode.LEAVE_IF_CHANGED &&
            !target.isNewerThanServer(syncManager, record)
        ) {

            // Nothing to do for this record
            MobileSyncLogger.d(
                TAG,
                "syncUpOneRecord: Record not synched since client does not have the latest from server",
                record
            )
            false
        } else {
            true
        }
    }

    @Throws(JSONException::class, IOException::class)
    private fun syncUpOneRecord(
        target: SyncUpTarget, soupName: String,
        record: JSONObject, options: SyncOptions
    ) {
        MobileSyncLogger.d(TAG, "syncUpOneRecord called", record)


        // Do we need to do a create, update or delete
        val locallyDeleted = target.isLocallyDeleted(record)
        val locallyCreated = target.isLocallyCreated(record)
        val locallyUpdated = target.isLocallyUpdated(record)
        val action: Action =
            if (locallyDeleted) delete else if (locallyCreated) create else if (locallyUpdated) update else return

        // Create/update/delete record on server and update smartstore
        val recordServerId: String?
        val statusCode: Int
        when (action) {
            create -> {
                recordServerId = target.createOnServer(syncManager, record, options.fieldlist)
                // Success
                if (recordServerId != null) {
                    record.put(target.idFieldName, recordServerId)
                    target.cleanAndSaveInLocalStore(syncManager, soupName, record)
                } else {
                    target.saveRecordToLocalStoreWithLastError(syncManager, soupName, record)
                }
            }

            delete -> {
                statusCode =
                    if (locallyCreated) HttpURLConnection.HTTP_NOT_FOUND // if locally created it can't exist on the server - we don't need to actually do the deleteOnServer call
                    else target.deleteOnServer(syncManager, record)
                // Success
                if (RestResponse.isSuccess(statusCode) || statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    target.deleteFromLocalStore(syncManager, soupName, record)
                } else {
                    target.saveRecordToLocalStoreWithLastError(syncManager, soupName, record)
                }
            }

            update -> {
                statusCode = target.updateOnServer(syncManager, record, options.fieldlist)
                // Success
                if (RestResponse.isSuccess(statusCode)) {
                    target.cleanAndSaveInLocalStore(syncManager, soupName, record)
                } else if (statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    if (options.mergeMode == MergeMode.OVERWRITE) {
                        recordServerId =
                            target.createOnServer(syncManager, record, options.fieldlist)
                        if (recordServerId != null) {
                            record.put(target.idFieldName, recordServerId)
                            target.cleanAndSaveInLocalStore(syncManager, soupName, record)
                        }
                    } else {
                        // Leave local record alone
                    }
                } else {
                    target.saveRecordToLocalStoreWithLastError(syncManager, soupName, record)
                }
            }
        }
    }

    /**
     * Enum for action
     *
     */
    enum class Action {
        create, update, delete
    }

    companion object {
        private const val TAG = "SyncUpTask"
    }
}