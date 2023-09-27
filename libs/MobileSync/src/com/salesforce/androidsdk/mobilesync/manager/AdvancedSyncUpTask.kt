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

import com.salesforce.androidsdk.mobilesync.manager.SyncManager.MobileSyncException
import com.salesforce.androidsdk.mobilesync.manager.SyncManager.SyncUpdateCallback
import com.salesforce.androidsdk.mobilesync.target.AdvancedSyncUpTarget
import com.salesforce.androidsdk.mobilesync.target.SyncUpTarget
import com.salesforce.androidsdk.mobilesync.util.SyncOptions
import com.salesforce.androidsdk.mobilesync.util.SyncState
import com.salesforce.androidsdk.mobilesync.util.SyncState.MergeMode
import com.salesforce.androidsdk.smartstore.store.SmartStore
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Runnable class responsible for running a sync up that uses and AdvancedSyncUpTarget
 */
class AdvancedSyncUpTask(syncManager: SyncManager, sync: SyncState, callback: SyncUpdateCallback?) :
    SyncUpTask(syncManager, sync, callback) {
    @Throws(JSONException::class, IOException::class, MobileSyncException::class)
    override fun syncUp(
        sync: SyncState,
        callback: SyncUpdateCallback?,
        dirtyRecordIds: List<String>
    ) {
        val soupName = sync.soupName
        val target =
            sync.target as? SyncUpTarget ?: throw MobileSyncException("A SyncUpTarget was expected")
        val options = sync.options
        val totalSize = dirtyRecordIds.size
        val maxBatchSize = (target as AdvancedSyncUpTarget).maxBatchSize
        val batch = ArrayList<JSONObject>()
        updateSync(sync, SyncState.Status.RUNNING, 0, callback)
        val dirtyRecords = target.getFromLocalStore(syncManager, soupName, dirtyRecordIds)

        // Figuring out what records need to be synced up based on merge mode and last mod date on server
        val recordIdToShouldSyncUp = shouldSyncUpRecords(syncManager, target, dirtyRecords, options)

        // Syncing up records
        for (i in 0 until totalSize) {
            checkIfStopRequested()
            val record = dirtyRecords[i]
            val recordId = record.getString(SmartStore.SOUP_ENTRY_ID)
            if (recordIdToShouldSyncUp[recordId] == true) {
                batch.add(record)
            }

            // Process batch if max batch size reached or at the end of dirtyRecordIds
            if (batch.size == maxBatchSize || i == totalSize - 1) {
                (target as AdvancedSyncUpTarget).syncUpRecords(
                    syncManager,
                    batch,
                    options.fieldlist,
                    options.mergeMode,
                    sync.soupName
                )
                batch.clear()
            }

            // Updating status
            val progress = (i + 1) * 100 / totalSize
            if (progress < 100) {
                updateSync(sync, SyncState.Status.RUNNING, progress, callback)
            }
        }
    }

    @Throws(IOException::class, JSONException::class, MobileSyncException::class)
    protected fun shouldSyncUpRecords(
        syncManager: SyncManager,
        target: SyncUpTarget,
        records: List<JSONObject>,
        options: SyncOptions
    ): Map<String, Boolean> {
        var recordIdToShouldSyncUp: MutableMap<String, Boolean> = HashMap()
        if (options.mergeMode == MergeMode.OVERWRITE) {
            for (record in records) {
                val recordId = record.getString(SmartStore.SOUP_ENTRY_ID)
                    ?: throw MobileSyncException("No id found on record")
                recordIdToShouldSyncUp[recordId] = true
            }
        } else {
            recordIdToShouldSyncUp = target.areNewerThanServer(syncManager, records)
        }
        return recordIdToShouldSyncUp
    }
}