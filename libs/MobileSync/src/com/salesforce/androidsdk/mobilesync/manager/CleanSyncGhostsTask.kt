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
import com.salesforce.androidsdk.mobilesync.manager.SyncManager.CleanResyncGhostsCallback
import com.salesforce.androidsdk.mobilesync.manager.SyncManager.SyncUpdateCallback
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget
import com.salesforce.androidsdk.mobilesync.util.MobileSyncLogger
import com.salesforce.androidsdk.mobilesync.util.SyncState
import org.json.JSONException
import org.json.JSONObject

/**
 * Runnable class responsible for running clean sync ghosts operation
 */
class CleanSyncGhostsTask(
    syncManager: SyncManager,
    sync: SyncState,
    private val cleanSyncCallback: CleanResyncGhostsCallback?
) : SyncTask(syncManager, sync, null) {
    override fun updateSync(
        sync: SyncState,
        status: SyncState.Status,
        progress: Int,
        callback: SyncUpdateCallback?
    ) {
        // Not a true sync
        // Leaving sync state alone
    }

    @Throws(Exception::class)
    override fun runSync() {
        try {
            val syncId = sync.id
            val soupName = sync.soupName
            val target = sync.target as SyncDownTarget
            val localIdSize = target.cleanGhosts(syncManager, soupName, syncId)
            val attributes = JSONObject()
            if (localIdSize > 0) {
                try {
                    attributes.put("numRecords", localIdSize)
                    attributes.put("syncId", sync.id)
                    attributes.put("syncTarget", target.javaClass.name)
                    EventBuilderHelper.createAndStoreEventSync(
                        "cleanResyncGhosts",
                        null,
                        TAG,
                        attributes
                    )
                } catch (e: JSONException) {
                    MobileSyncLogger.e(
                        TAG,
                        "Unexpected JSON error for cleanResyncGhosts sync tag: ${sync.id}",
                        e
                    )
                }
            }
            syncManager.removeFromActiveSyncs(this)
            cleanSyncCallback?.onSuccess(localIdSize)
        } catch (e: Exception) {
            MobileSyncLogger.e(TAG, "Exception thrown cleaning resync ghosts", e)
            syncManager.removeFromActiveSyncs(this)
            cleanSyncCallback?.onError(e)
        }
    }

    companion object {
        // Constants
        private const val TAG = "CleanSyncGhostsTask"
    }
}