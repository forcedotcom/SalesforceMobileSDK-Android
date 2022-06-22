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
package com.salesforce.androidsdk.mobilesync.target;

import com.salesforce.androidsdk.mobilesync.manager.SyncManager;
import com.salesforce.androidsdk.mobilesync.target.CompositeRequestHelper.RecordRequest;
import com.salesforce.androidsdk.mobilesync.target.CompositeRequestHelper.RecordResponse;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.util.JSONObjectHelper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Subclass of SyncUpTarget that batches create/update/delete operations by using sobject collection apis
 */
public class CollectionSyncUpTarget extends BatchSyncUpTarget {

    // Constants
    public static final int MAX_RECORDS_SOBJECT_COLLECTION_API = 200;

    /**
     * Construct CollectionSyncUpTarget
     */
    public CollectionSyncUpTarget() {
        this(null, null);
    }

    /**
     * Construct CollectionSyncUpTarget
     */
    public CollectionSyncUpTarget(List<String> createFieldlist, List<String> updateFieldlist) {
        this(createFieldlist, updateFieldlist, null, null, null, MAX_RECORDS_SOBJECT_COLLECTION_API);
    }

    /**
     * Construct CollectionSyncUpTarget with a different maxBatchSize (NB: cannot exceed MAX_RECORDS_SOBJECT_COLLECTION_API)
     */
    public CollectionSyncUpTarget(List<String> createFieldlist, List<String> updateFieldlist, int maxBatchSize) {
        this(createFieldlist, updateFieldlist, null, null, null, maxBatchSize);
    }

    /**
     * Construct CollectionSyncUpTarget with given id/modifiedDate/externalId fields
     */
    public CollectionSyncUpTarget(List<String> createFieldlist, List<String> updateFieldlist, String idFieldName, String modificationDateFieldName, String externalIdFieldName) {
        this(createFieldlist, updateFieldlist, idFieldName, modificationDateFieldName, externalIdFieldName, MAX_RECORDS_SOBJECT_COLLECTION_API);
    }

    /**
     * Construct CollectionSyncUpTarget with a different maxBatchSize and id/modifiedDate/externalId fields
     */
    public CollectionSyncUpTarget(List<String> createFieldlist, List<String> updateFieldlist, String idFieldName, String modificationDateFieldName, String externalIdFieldName, int maxBatchSize) {
        super(createFieldlist, updateFieldlist, idFieldName, modificationDateFieldName, externalIdFieldName);
        this.maxBatchSize = Math.min(maxBatchSize, MAX_RECORDS_SOBJECT_COLLECTION_API); // soject collection apis allows up to 200 records
    }

    /**
     * Construct SyncUpTarget from json
     *
     * @param target
     * @throws JSONException
     */
    public CollectionSyncUpTarget(JSONObject target) throws JSONException {
        super(target);
        this.maxBatchSize = Math.min(target.optInt(MAX_BATCH_SIZE, MAX_RECORDS_SOBJECT_COLLECTION_API), MAX_RECORDS_SOBJECT_COLLECTION_API); // soject collection apis allows up to 200 records
    }

    @Override
    protected Map<String, RecordResponse> sendRecordRequests(SyncManager syncManager, List<RecordRequest> recordRequests) throws JSONException, IOException {
        return CompositeRequestHelper.sendAsCollectionRequests(syncManager, false, recordRequests);
    }

    @Override
    public Map<String, Boolean> areNewerThanServer(SyncManager syncManager, List<JSONObject> records) throws JSONException, IOException {
        Map<String, Boolean> storeIdToNewerThanServer = new HashMap<>();

        List<JSONObject> nonLocallyCreatedRecords = new ArrayList<>();
        for (JSONObject record : records) {
            if (isLocallyCreated(record) || !record.has(getIdFieldName())) {
                String storeId = record.getString(SmartStore.SOUP_ENTRY_ID);
                storeIdToNewerThanServer.put(storeId, true);
            } else {
                nonLocallyCreatedRecords.add(record);
            }
        }

        Map<String, RecordModDate> recordIdToRemoteModDate = fetchLastModifiedDates(syncManager, nonLocallyCreatedRecords);

        for (JSONObject record : nonLocallyCreatedRecords) {
            String storeId = record.getString(SmartStore.SOUP_ENTRY_ID);
            RecordModDate localModDate = new RecordModDate(
                JSONObjectHelper.optString(record, getModificationDateFieldName()),
                isLocallyDeleted(record)
            );
            RecordModDate remoteModDate = recordIdToRemoteModDate.get(storeId);
            storeIdToNewerThanServer.put(storeId, isNewerThanServer(localModDate, remoteModDate));
        }

        return storeIdToNewerThanServer;
    }
}