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
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget
import com.salesforce.androidsdk.mobilesync.util.SyncState
import com.salesforce.androidsdk.mobilesync.util.SyncState.MergeMode
import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONArray
import org.json.JSONException
import kotlin.math.max

/**
 * Runnable class responsible for running a sync down
 */
class SyncDownTask(syncManager: SyncManager, sync: SyncState, callback: SyncUpdateCallback?) :
    SyncTask(syncManager, sync, callback) {
    @Throws(Exception::class)
    override fun runSync() {
        val soupName = sync.soupName
        val target = sync.target as SyncDownTarget
        val mergeMode = sync.mergeMode
        var maxTimeStamp = sync.maxTimeStamp
        var records = target.startFetch(syncManager, maxTimeStamp)
        var countSaved = 0
        val totalSize = target.totalSize
        sync.totalSize = totalSize
        updateSync(sync, SyncState.Status.RUNNING, 0, callback)
        val idField = sync.target.idFieldName

        // Get ids of records to leave alone
        var idsToSkip: Set<String>? = null
        if (mergeMode == MergeMode.LEAVE_IF_CHANGED) {
            idsToSkip = target.getIdsToSkip(syncManager, soupName)
        }
        while (records != null) {
            val recordsIn = records
            checkIfStopRequested()

            // Figure out records to save
            val recordsToSave =
                idsToSkip?.let { removeWithIds(recordsIn, it, idField) } ?: recordsIn

            // Save to smartstore.
            target.saveRecordsToLocalStore(syncManager, soupName, recordsToSave, sync.id)
            countSaved += recordsIn.length()
            maxTimeStamp = max(maxTimeStamp, target.getLatestModificationTimeStamp(recordsIn))

            // Updating maxTimeStamp as we go if records are ordered by latest modification
            if (target.isSyncDownSortedByLatestModification) {
                sync.maxTimeStamp = maxTimeStamp
            }

            // Update sync status.
            if (countSaved < totalSize) {
                updateSync(sync, SyncState.Status.RUNNING, countSaved * 100 / totalSize, callback)
            }

            // Fetch next records, if any.
            records = target.continueFetch(syncManager)
        }

        // Updating maxTimeStamp once at the end if records are NOT ordered by latest modification
        if (!target.isSyncDownSortedByLatestModification) {
            sync.maxTimeStamp = maxTimeStamp
        }
    }

    @Throws(JSONException::class)
    private fun removeWithIds(
        records: JSONArray,
        idsToSkip: Set<String>,
        idField: String
    ): JSONArray {
        val arr = JSONArray()
        for (i in 0 until records.length()) {
            val record = records.getJSONObject(i)

            // Keep ?
            val id = JSONObjectHelper.optString(record, idField)
            if (id == null || !idsToSkip.contains(id)) {
                arr.put(record)
            }
        }
        return arr
    }
}