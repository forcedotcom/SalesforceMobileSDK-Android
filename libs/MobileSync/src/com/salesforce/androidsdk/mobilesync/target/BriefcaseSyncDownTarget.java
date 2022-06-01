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

import com.salesforce.androidsdk.mobilesync.app.Features;
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager;
import com.salesforce.androidsdk.mobilesync.manager.SyncManager;
import com.salesforce.androidsdk.mobilesync.util.BriefcaseObjectInfo;
import com.salesforce.androidsdk.mobilesync.util.Constants;
import com.salesforce.androidsdk.mobilesync.util.MobileSyncLogger;
import com.salesforce.androidsdk.rest.PrimingRecordsResponse;
import com.salesforce.androidsdk.rest.PrimingRecordsResponse.PrimingRecord;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.util.JSONObjectHelper;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Target for sync that downloads records using the briefcase (priming records) API
 */
public class BriefcaseSyncDownTarget extends SyncDownTarget {
    private static final String TAG = "BriefcaseSyncDownTarget";

    public static final String INFOS = "infos";
    public static final String COUNT_IDS_PER_RETRIEVE = "countIdsPerRetrieve";

    private List<BriefcaseObjectInfo> infos;
    private Map<String, BriefcaseObjectInfo> infosMap;

    // NB: For each sync run - a fresh sync down target is created (by deserializing it from smartstore)
    // The following members are specific to a run
    protected long maxTimeStamp = 0L;
    protected String relayToken = null;
    // When we get many ids, we don't fetch all the records for them at once
    protected TypedIds fetchedTypedIds = null;
    protected int currentSliceIndex = 0;

    // Number of records to fetch per call (with ids obtained from priming record api)
    private int countIdsPerRetrieve;
    private static final int MAX_COUNT_IDS_PER_RETRIEVE = RestRequest.MAX_COLLECTION_RETRIEVE_SIZE;

    /**
     * Construct BriefcaseSyncDownTarget from json
     * @param target
     * @throws JSONException
     */
    public BriefcaseSyncDownTarget(JSONObject target) throws JSONException {
        this(
            BriefcaseObjectInfo.fromJSONArray(target.getJSONArray(INFOS)),
            target.optInt(COUNT_IDS_PER_RETRIEVE, MAX_COUNT_IDS_PER_RETRIEVE)
        );
    }

    /**
     * Construct BriefcaseSyncDownTarget
     *
     * @param infos
     */
    public BriefcaseSyncDownTarget(List<BriefcaseObjectInfo> infos) {
        this(infos, MAX_COUNT_IDS_PER_RETRIEVE);
    }

    BriefcaseSyncDownTarget(List<BriefcaseObjectInfo> infos, int countIdsPerRetrieve) {
        this.infos = infos;
        this.queryType = QueryType.briefcase;
        this.countIdsPerRetrieve = Math.min(countIdsPerRetrieve, MAX_COUNT_IDS_PER_RETRIEVE);
        MobileSyncSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_BRIEFCASE);

        // Build infosMap
        infosMap = new HashMap<>();
        for (BriefcaseObjectInfo info : infos) {
            infosMap.put(info.sobjectType, info);
        }
     }

    /**
     * @return json representation of target
     * @throws JSONException
     */
    public JSONObject asJSON() throws JSONException {
        JSONObject target = super.asJSON();
        JSONArray infosJson = new JSONArray();
        for (BriefcaseObjectInfo info : infos) {
            infosJson.put(info.asJSON());
        }
        target.put(INFOS, infosJson);
        target.put(COUNT_IDS_PER_RETRIEVE, countIdsPerRetrieve);
        return target;
    }

    @Override
    public JSONArray startFetch(SyncManager syncManager, long maxTimeStamp) throws IOException, JSONException {
        this.maxTimeStamp = maxTimeStamp;
        this.relayToken = null;
        this.totalSize = -1;
        return getIdsFromBriefcasesAndFetchFromServer(syncManager);
    }

    @Override
    public JSONArray continueFetch(SyncManager syncManager) throws IOException, JSONException {
        if (relayToken == null && fetchedTypedIds == null) {
            return null;
        } else {
            return getIdsFromBriefcasesAndFetchFromServer(syncManager);
        }
    }

    @Override
    public int cleanGhosts(SyncManager syncManager, String soupName, long syncId) throws JSONException, IOException {
        int countGhosts = 0;

        // Get all ids
        TypedIds typedIds = new TypedIds();
        String relayToken = null;
        do {
            relayToken = getIdsFromBriefcases(syncManager, typedIds, relayToken, 0);
        } while (relayToken != null);
        Map<String, List<String>> objectTypeToIds = typedIds.toMap();

        // Cleaning up ghosts one object type at a time
        for (Entry<String, List<String>> entry : objectTypeToIds.entrySet()) {
            String objectType = entry.getKey();
            BriefcaseObjectInfo info = infosMap.get(objectType);
            SortedSet<String> remoteIds = new TreeSet<>(entry.getValue());
            SortedSet<String> localIds = getNonDirtyRecordIds(syncManager, info.soupName, info.idFieldName,
                buildSyncIdPredicateIfIndexed(syncManager, info.soupName, syncId));
            localIds.removeAll(remoteIds);
            int localIdSize = localIds.size();
            if (localIdSize > 0) {
                deleteRecordsFromLocalStore(syncManager, info.soupName, localIds, info.idFieldName);
            }
            countGhosts += localIdSize;
        }

        return countGhosts;
    }

    @Override
    public Set<String> getIdsToSkip(SyncManager syncManager, String soupName) throws JSONException {
        Set<String> dirtyRecordIds = new HashSet<>();
        // Aggregating ids of dirty records across all the soups
        for (BriefcaseObjectInfo info : infos) {
            dirtyRecordIds.addAll(getDirtyRecordIds(syncManager, info.soupName, info.idFieldName));
        }
        return dirtyRecordIds;
    }

    @Override
    protected Set<String> getRemoteIds(SyncManager syncManager, Set<String> localIds)
        throws IOException, JSONException {
        // Not used - we are overriding cleanGhosts entirely since we could have multiple soups
        return null;
    }

    /**
     * Method that calls the priming records API to get ids to fetch
     * then use sObject collection retrieve to get record fields
     *
     * @param syncManager
     * @return
     */
    private JSONArray getIdsFromBriefcasesAndFetchFromServer(SyncManager syncManager)
        throws IOException, JSONException {
        JSONArray records = new JSONArray();

        // Run priming record request unless we have fetched typed ids we have not yet processed
        if (fetchedTypedIds == null) {
            fetchedTypedIds = new TypedIds();
            currentSliceIndex = 0;
            relayToken = getIdsFromBriefcases(syncManager, fetchedTypedIds, relayToken, maxTimeStamp);
        }

        // Getting ids of records to fetch in a map
        Map<String, List<String>> objectTypeToIds = fetchedTypedIds.slice(currentSliceIndex,
            countIdsPerRetrieve).toMap();

        // Get records using sObject collection retrieve one object type at a time
        for (Entry<String, List<String>> entry : objectTypeToIds.entrySet()) {
            String objectType = entry.getKey();
            List<String> idsToFetch = entry.getValue();
            if (idsToFetch.size() > 0) {
                BriefcaseObjectInfo info = infosMap.get(objectType);

                ArrayList<String> fieldlistToFetch = new ArrayList<>(info.fieldlist);
                for (String fieldName : Arrays.asList(info.idFieldName, info.modificationDateFieldName)) {
                    if (!fieldlistToFetch.contains(fieldName)) {
                        fieldlistToFetch.add(fieldName);
                    }
                }

                JSONArray fetchedRecords = fetchFromServer(syncManager, info.sobjectType, idsToFetch, fieldlistToFetch);
                JSONObjectHelper.addAll(records, fetchedRecords);
            }
        }

        if (totalSize == -1) {
            // FIXME once 238 is GA
            //  - this will only be correct if there is only one "page" of results
            //  - using response.stats.recordCountTotal would only be correct if the filtering by
            //  timestamp did not exclude any results
            //  - also in 236, response.stats.recordCountTotal seems wrong (it says 1000 all the time)
            totalSize = fetchedTypedIds.size();
        }

        // Incrementing current slice index and checking if we have reached the end
        currentSliceIndex++;
        if (currentSliceIndex >= fetchedTypedIds.countSlices(countIdsPerRetrieve)) {
            fetchedTypedIds = null;
            currentSliceIndex = 0;
        }

        return records;
    }

    /**
     * Go to the priming record API and return ids (grouped by object type)
     *
     * @param syncManager
     * @param typedIds - gets populated from the response to the priming records API
     * @param relayToken
     * @param maxTimeStamp - only ids with a greater time stamp are returned
     * @return new relay token
     * @throws JSONException
     * @throws IOException
     */
    protected String getIdsFromBriefcases(SyncManager syncManager, TypedIds typedIds, String relayToken, long maxTimeStamp)
        throws JSONException, IOException {
        RestRequest request = RestRequest.getRequestForPrimingRecords(syncManager.apiVersion, relayToken, maxTimeStamp);

        PrimingRecordsResponse response;
        try {
            response = new PrimingRecordsResponse(
                syncManager.sendSyncWithMobileSyncUserAgent(request).asJSONObject());
        } catch (ParseException e) {
            throw new IOException("Could not parse response from priming record API", e);
        }

        Map<String, Map<String, List<PrimingRecord>>> allPrimingRecords = response.primingRecords;
        for (BriefcaseObjectInfo info : infos) {
            if (allPrimingRecords.containsKey(info.sobjectType)) {
                for (List<PrimingRecord> primingRecords : allPrimingRecords.get(info.sobjectType)
                    .values()) {
                    for (PrimingRecord primingRecord : primingRecords) {
                        typedIds.add(info.sobjectType, primingRecord.id);
                    }
                }
            }
        }

        return response.relayToken;
    }

    protected JSONArray fetchFromServer(SyncManager syncManager, String sobjectType, List<String> ids, List<String> fieldlist) throws IOException, JSONException {
        syncManager.checkAcceptingSyncs();
        final RestRequest request = RestRequest.getRequestForCollectionRetrieve(syncManager.apiVersion, sobjectType, ids, fieldlist);
        final RestResponse response = syncManager.sendSyncWithMobileSyncUserAgent(request);
        return response.asJSONArray();
    }

    /**
     * Overriding saveRecordsToLocalStore since we might want records in different soups
     *
     * @param syncManager
     * @param soupName
     * @param records
     * @param syncId
     * @throws JSONException
     */
    @Override
    public void saveRecordsToLocalStore(SyncManager syncManager, String soupName, JSONArray records,
        long syncId) throws JSONException {
        SmartStore smartStore = syncManager.getSmartStore();
        synchronized (smartStore.getDatabase()) {
            try {
                smartStore.beginTransaction();
                for (int i = 0; i < records.length(); i++) {
                    JSONObject record = records.getJSONObject(i);
                    BriefcaseObjectInfo info = getMatchingBriefcaseInfo(record);
                    if (info != null) {
                        addSyncId(record, syncId);
                        cleanAndSaveInSmartStore(smartStore, info.soupName, record,
                            info.idFieldName,
                            false);
                    } else {
                        // That should never happened
                        MobileSyncLogger.e(TAG, String.format("No matching briefcase info - Don't know how to save record %s", record.toString()));
                    }
                }
                smartStore.setTransactionSuccessful();
            } finally {
                smartStore.endTransaction();
            }
        }
    }

    protected String getObjectType(JSONObject record) throws JSONException {
        JSONObject attributes = record.getJSONObject(Constants.ATTRIBUTES);
        if (attributes != null) {
            return attributes.getString(Constants.LTYPE);
        } else {
            return null;
        }
    }

    protected BriefcaseObjectInfo getMatchingBriefcaseInfo(JSONObject record) throws JSONException {
        String sobjectType = getObjectType(record);
        if (sobjectType != null) {
            return infosMap.get(sobjectType);
        }
        return null;
    }
}

class TypedId {
    String sobjectType;
    String id;

    TypedId(String sobjectType, String id) {
        this.sobjectType = sobjectType;
        this.id = id;
    }
}

class TypedIds {
    List<TypedId> listTypedIds;

    TypedIds() {
        this(new ArrayList<>());
    }

    TypedIds(List<TypedId> ids) {
        this.listTypedIds = ids;
    }

    void add(String objectType, String id) {
        listTypedIds.add(new TypedId(objectType, id));
    }

    int countSlices(int sliceSize) {
        return (int) Math.ceil((double) listTypedIds.size() / sliceSize);
    }

    int size() {
        return listTypedIds.size();
    }

    TypedIds slice(int sliceIndex, int sliceSize) {
        List<TypedId> idsOfSlice = listTypedIds
            .subList(sliceIndex * sliceSize, Math.min(listTypedIds.size(), (sliceIndex + 1) * sliceSize));
        return new TypedIds(idsOfSlice);
    }

    Map<String, List<String>> toMap() {
        Map<String, List<String>> typeToIds = new HashMap<>();
        for (TypedId typedId : listTypedIds) {
            if (typeToIds.get(typedId.sobjectType) == null) {
                typeToIds.put(typedId.sobjectType, new ArrayList<>());
            }
            typeToIds.get(typedId.sobjectType).add(typedId.id);
        }
        return typeToIds;
    }
}