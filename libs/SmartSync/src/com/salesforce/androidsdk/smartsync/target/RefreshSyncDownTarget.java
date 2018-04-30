/*
 * Copyright (c) 2016-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.smartsync.target;

import android.text.TextUtils;

import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartsync.manager.SyncManager;
import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.androidsdk.smartsync.util.SOQLBuilder;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Target for sync which syncs down the records currently in a soup
 */
public class RefreshSyncDownTarget extends SyncDownTarget {

    private static final String TAG = "RefreshSyncDownTarget";
    public static final String FIELDLIST = "fieldlist";
    public static final String SOBJECT_TYPE = "sobjectType";
    public static final String SOUP_NAME = "soupName";
    public static final String COUNT_IDS_PER_SOQL = "coundIdsPerSoql";
    private List<String> fieldlist;
    private String objectType;
    private String soupName;
    private int countIdsPerSoql;
    private static final int defaultCountIdsPerSoql = 500;

    // NB: For each sync run - a fresh sync down target is created (by deserializing it from smartstore)
    // The following members are specific to a run
    // page will change during a run as we call start/continueFetch
    private boolean isResync = false;
    private int page = 0;

    /**
     * Return number of ids to pack in a single SOQL call
     */
    public int getCountIdsPerSoql() {
        return countIdsPerSoql;
    }

    /**
     * Set the number of ids to pack in a single SOQL call
     * SOQL query size limit is 10,000 characters (so ~500 ids)
     * This setter is to be used by tests primarily
     * @param count
     */
    public void setCountIdsPerSoql(int count) {
        countIdsPerSoql = count;
    }

    /**
     * Construct RefreshSyncDownTarget from json
     * @param target
     * @throws JSONException
     */
    public RefreshSyncDownTarget(JSONObject target) throws JSONException {
        super(target);
        this.fieldlist = JSONObjectHelper.toList(target.getJSONArray(FIELDLIST));
        this.objectType = target.getString(SOBJECT_TYPE);
        this.soupName = target.getString(SOUP_NAME);
        this.countIdsPerSoql = target.optInt(COUNT_IDS_PER_SOQL, defaultCountIdsPerSoql);
    }

    /**
     * Constructor
     * @param fieldlist
     * @param objectType
     */
    public RefreshSyncDownTarget(List<String> fieldlist, String objectType, String soupName) {
        super();
        this.queryType = QueryType.refresh;
        this.fieldlist = fieldlist;
        this.objectType = objectType;
        this.soupName = soupName;
        this.countIdsPerSoql = defaultCountIdsPerSoql;
    }

    /**
     * @return json representation of target
     * @throws JSONException
     */
    public JSONObject asJSON() throws JSONException {
        JSONObject target = super.asJSON();
        target.put(FIELDLIST, new JSONArray(fieldlist));
        target.put(SOBJECT_TYPE, objectType);
        target.put(SOUP_NAME, soupName);
        target.put(COUNT_IDS_PER_SOQL, countIdsPerSoql);
        return target;
    }

    @Override
    public JSONArray startFetch(SyncManager syncManager, long maxTimeStamp) throws IOException, JSONException {
        // During reSync, we can't make use of the maxTimeStamp that was captured during last refresh
        // since we expect records to have been fetched from the server and written to the soup directly outside a sync down operation
        // Instead during a reSymc, we compute maxTimeStamp from the records in the soup
        isResync = maxTimeStamp > 0;
        return  getIdsFromSmartStoreAndFetchFromServer(syncManager);
    }

    @Override
    public JSONArray continueFetch(SyncManager syncManager) throws IOException, JSONException {
        return page > 0 ? getIdsFromSmartStoreAndFetchFromServer(syncManager) : null;
    }

    private JSONArray getIdsFromSmartStoreAndFetchFromServer(SyncManager syncManager) throws IOException, JSONException {
        // Read from smartstore
        final QuerySpec querySpec;
        final List<String> idsInSmartStore = new ArrayList<>();
        final long maxTimeStamp;
        if (isResync) {
            // Getting full records from SmartStore to compute maxTimeStamp
            // So doing more db work in the hope of doing less server work
            querySpec = QuerySpec.buildAllQuerySpec(soupName, getIdFieldName(), QuerySpec.Order.ascending, getCountIdsPerSoql());
            JSONArray recordsFromSmartStore = syncManager.getSmartStore().query(querySpec, page);

            // Compute max time stamp
            maxTimeStamp = getLatestModificationTimeStamp(recordsFromSmartStore);

            // Get ids
            for (int i = 0; i < recordsFromSmartStore.length(); i++) {
                idsInSmartStore.add(recordsFromSmartStore.getJSONObject(i).getString(getIdFieldName()));
            }
        }
        else {
            querySpec = QuerySpec.buildSmartQuerySpec("SELECT {" + soupName + ":" + getIdFieldName()
                    + "} FROM {" + soupName + "} ORDER BY {" + soupName + ":" + getIdFieldName() + "} ASC", getCountIdsPerSoql());
            JSONArray result = syncManager.getSmartStore().query(querySpec, page);

            // Not a resync
            maxTimeStamp = 0;

            // Get ids
            for (int i = 0; i < result.length(); i++) {
                idsInSmartStore.add(result.getJSONArray(i).getString(0));
            }
        }

        // If fetch is starting, figuring out totalSize
        // NB: it might not be the correct value during resync
        //     since not all records will have changed
        if (page == 0) {
            totalSize = syncManager.getSmartStore().countQuery(querySpec);
        }
        if (idsInSmartStore.size() > 0) {
            // Get records from server that have changed after maxTimeStamp
            final JSONArray records = fetchFromServer(syncManager, idsInSmartStore, fieldlist, maxTimeStamp);

            // Increment page if there is more to fetch
            boolean done = getCountIdsPerSoql() * (page + 1) >= totalSize;
            page = (done ? 0 : page + 1);
            return records;
        }
        else {
            page = 0; // done
            return null;
        }
    }

    private JSONArray fetchFromServer(SyncManager syncManager, List<String> ids, List<String> fieldlist, long maxTimeStamp) throws IOException, JSONException {
        final String whereClause = ""
                + getIdFieldName() + " IN ('" + TextUtils.join("', '", ids) + "')"
                + (maxTimeStamp > 0 ? " AND " + getModificationDateFieldName() + " > " + Constants.TIMESTAMP_FORMAT.format(new Date(maxTimeStamp))
                : "");
        final String soql = SOQLBuilder.getInstanceWithFields(fieldlist).from(objectType).where(whereClause).build();
        final RestRequest request = RestRequest.getRequestForQuery(syncManager.apiVersion, soql);
        final RestResponse response = syncManager.sendSyncWithSmartSyncUserAgent(request);
        JSONObject responseJson = response.asJSONObject();
        return responseJson.getJSONArray(Constants.RECORDS);
    }

    @Override
    protected Set<String> getRemoteIds(SyncManager syncManager, Set<String> localIds) throws IOException, JSONException {
        if (localIds == null) {
            return null;
        }
        Set<String> remoteIds = new HashSet<>();
        List<String> localIdsList = new ArrayList<>(localIds);
        int sliceSize = getCountIdsPerSoql();
        int countSlices = (int) Math.ceil((double) localIds.size() / sliceSize);
        for (int slice = 0; slice < countSlices; slice++) {
            List<String> idsToFetch = localIdsList.subList(slice * sliceSize, Math.min(localIdsList.size(), (slice + 1) * sliceSize));
            JSONArray records = fetchFromServer(syncManager, idsToFetch, Arrays.asList(getIdFieldName()), 0 /* get all */);
            remoteIds.addAll(parseIdsFromResponse(records));
        }
        return remoteIds;
    }

    /**
     * @return field list for this target
     */
    public List<String> getFieldlist() {
        return fieldlist;
    }

    /**
     * @return object type for this target
     */
    public String getObjectType() {
        return objectType;
    }
}
