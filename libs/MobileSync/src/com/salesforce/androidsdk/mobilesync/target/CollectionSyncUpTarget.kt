/*
 * Copyright (c) 2022-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.mobilesync.target

import com.salesforce.androidsdk.mobilesync.manager.SyncManager
import com.salesforce.androidsdk.mobilesync.target.CompositeRequestHelper.RecordRequest
import com.salesforce.androidsdk.mobilesync.target.CompositeRequestHelper.RecordResponse
import com.salesforce.androidsdk.smartstore.store.SmartStore
import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import kotlin.math.min

/**
 * Subclass of SyncUpTarget that batches create/update/delete operations by using sobject collection apis
 */
open class CollectionSyncUpTarget : BatchSyncUpTarget {
    /**
     * Construct CollectionSyncUpTarget with a different maxBatchSize (NB: cannot exceed MAX_RECORDS_SOBJECT_COLLECTION_API)
     */
    constructor(
        createFieldlist: List<String>?,
        updateFieldlist: List<String>?,
        maxBatchSize: Int
    ) : this(createFieldlist, updateFieldlist, null, null, null, maxBatchSize)

    /**
     * Construct CollectionSyncUpTarget
     */
    @JvmOverloads
    constructor(
        createFieldlist: List<String>? = null,
        updateFieldlist: List<String>? = null,
        idFieldName: String? = null,
        modificationDateFieldName: String? = null,
        externalIdFieldName: String? = null,
        maxBatchSize: Int = MAX_RECORDS_SOBJECT_COLLECTION_API
    ) : super(
        createFieldlist,
        updateFieldlist,
        idFieldName,
        modificationDateFieldName,
        externalIdFieldName
    ) {
        this.maxBatchSize = min(
            maxBatchSize,
            MAX_RECORDS_SOBJECT_COLLECTION_API
        ) // soject collection apis allows up to 200 records
    }

    /**
     * Construct SyncUpTarget from json
     *
     * @param target
     * @throws JSONException
     */
    constructor(target: JSONObject) : super(target) {
        maxBatchSize = min(
            target.optInt(
                MAX_BATCH_SIZE,
                MAX_RECORDS_SOBJECT_COLLECTION_API
            ), MAX_RECORDS_SOBJECT_COLLECTION_API
        ) // soject collection apis allows up to 200 records
    }

    @Throws(JSONException::class, IOException::class)
    override fun sendRecordRequests(
        syncManager: SyncManager,
        recordRequests: List<RecordRequest>
    ): Map<String, RecordResponse> {
        return CompositeRequestHelper.sendAsCollectionRequests(syncManager, false, recordRequests)
    }

    @Throws(JSONException::class, IOException::class)
    override fun areNewerThanServer(
        syncManager: SyncManager,
        records: List<JSONObject>
    ): MutableMap<String, Boolean> {
        val storeIdToNewerThanServer: MutableMap<String, Boolean> = HashMap()
        val nonLocallyCreatedRecords: MutableList<JSONObject> = ArrayList()
        for (record in records) {
            if (isLocallyCreated(record) || !record.has(idFieldName)) {
                val storeId = record.getString(SmartStore.SOUP_ENTRY_ID)
                storeIdToNewerThanServer[storeId] = true
            } else {
                nonLocallyCreatedRecords.add(record)
            }
        }
        val recordIdToRemoteModDate = fetchLastModifiedDates(syncManager, nonLocallyCreatedRecords)
        for (record in nonLocallyCreatedRecords) {
            val storeId = record.getString(SmartStore.SOUP_ENTRY_ID)
            val localModDate = RecordModDate(
                JSONObjectHelper.optString(record, modificationDateFieldName),
                isLocallyDeleted(record)
            )
            val remoteModDate = recordIdToRemoteModDate[storeId]
                ?: throw SyncManager.MobileSyncException("No remote mod date for $storeId") // NB should never happened
            storeIdToNewerThanServer[storeId] = isNewerThanServer(localModDate, remoteModDate)
        }
        return storeIdToNewerThanServer
    }

    companion object {
        // Constants
        const val MAX_RECORDS_SOBJECT_COLLECTION_API = 200
    }
}