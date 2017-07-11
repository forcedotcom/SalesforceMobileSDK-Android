/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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

import com.salesforce.androidsdk.smartsync.manager.SyncManager;
import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.androidsdk.smartsync.util.SmartSyncLogger;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;

/**
 * Target for sync down:
 * - what records to download from server
 * - how to download those records
 */
public abstract class SyncDownTarget extends SyncTarget {

    // Constants
    private static final String TAG = "SyncDownTarget";
	public static final String QUERY_TYPE = "type";

    // Fields
	protected QueryType queryType;
    protected int totalSize; // set during a fetch

    /**
	 * Build SyncDownTarget from json
	 * @param target as json
	 * @return
	 * @throws JSONException
	 */
	@SuppressWarnings("unchecked")
	public static SyncDownTarget fromJSON(JSONObject target) throws JSONException {
		if (target == null)
			return null;

		QueryType queryType = QueryType.valueOf(target.getString(QUERY_TYPE));

        switch (queryType) {
        case mru:     return new MruSyncDownTarget(target);
        case sosl:    return new SoslSyncDownTarget(target);
        case soql:    return new SoqlSyncDownTarget(target);
        case refresh: return new RefreshSyncDownTarget(target);
            case parent_children: return new ParentChildrenSyncDownTarget(target);
        case custom:
        default:
            try {
                Class<? extends SyncDownTarget> implClass = (Class<? extends SyncDownTarget>) Class.forName(target.getString(ANDROID_IMPL));
                Constructor<? extends SyncDownTarget> constructor = implClass.getConstructor(JSONObject.class);
                return constructor.newInstance(target);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
	}

    /**
     * Construct SyncDownTarget
     */
    public SyncDownTarget() {
        super();
    }


    public SyncDownTarget(String idFieldName, String modificationDateFieldName) {
        super(idFieldName, modificationDateFieldName);
    }


    /**
     * Construct SyncDownTarget from json
     * @param target
     * @throws JSONException
     */
    public SyncDownTarget(JSONObject target) throws JSONException {
        super(target);
        queryType = QueryType.valueOf(target.getString(QUERY_TYPE));
    }

    /**
     * @return json representation of target
     * @throws JSONException
     */
    public JSONObject asJSON() throws JSONException {
        JSONObject target = super.asJSON();
        target.put(QUERY_TYPE, queryType.name());
        return target;
    }

    /**
     * Start fetching records conforming to target
     * If a value for maxTimeStamp greater than 0 is passed in, only records created/modified after maxTimeStamp should be returned
     * @param syncManager
     * @param maxTimeStamp
     * @throws IOException, JSONException
     */
    public abstract JSONArray startFetch(SyncManager syncManager, long maxTimeStamp) throws IOException, JSONException;

    /**
     * Continue fetching records conforming to target if any
     * @param syncManager
     * @return null if there are no more records to fetch
     * @throws IOException, JSONException
     */
    public abstract JSONArray continueFetch(SyncManager syncManager) throws IOException, JSONException;


    /**
     * Delete from local store records that a full sync down would no longer download
     * @param syncManager
     * @param soupName
     * @return
     * @throws JSONException, IOException
     */
    public int cleanGhosts(SyncManager syncManager, String soupName) throws JSONException, IOException {
         // Fetches list of IDs present in local soup that have not been modified locally.
        final Set<String> localIds = getNonDirtyRecordIds(syncManager, soupName, getIdFieldName());

         // Fetches list of IDs still present on the server from the list of local IDs
         // and removes the list of IDs that are still present on the server.
        final Set<String> remoteIds = getRemoteIds(syncManager, localIds);
        if (remoteIds != null) {
            localIds.removeAll(remoteIds);
        }

        // Deletes extra IDs from SmartStore.
        int localIdSize = localIds.size();
        if (localIdSize > 0) {
            deleteRecordsFromLocalStore(syncManager, soupName, localIds, getIdFieldName());
        }

        return localIdSize;
    }

    /**
     * Return ids of non-dirty records (records NOT locally created/updated or deleted)
     * @param syncManager
     * @param soupName
     * @param idField
     * @return
     * @throws JSONException
     */
    protected SortedSet<String> getNonDirtyRecordIds(SyncManager syncManager, String soupName, String idField) throws JSONException {
        String nonDirtyRecordsSql = getNonDirtyRecordIdsSql(soupName, idField);
        return getIdsWithQuery(syncManager, nonDirtyRecordsSql);
    }

    /**
     * Return SmartSQL to identify non-dirty records
     * @param soupName
     * @param idField
     * @return
     */
    protected String getNonDirtyRecordIdsSql(String soupName, String idField) {
        return String.format("SELECT {%s:%s} FROM {%s} WHERE {%s:%s} = 'false' ORDER BY {%s:%s} ASC", soupName, idField, soupName, soupName, LOCAL, soupName, idField);
    }



    /**
     * Fetches remote IDs still present on the server from the list of local IDs.
     *
     * @param syncManager SyncManager instance.
     * @return List of IDs still present on the server.
     */
    protected abstract Set<String> getRemoteIds(SyncManager syncManager, Set<String> localIds) throws IOException, JSONException;

    /**
     * @return number of records expected to be fetched - is set when startFetch() is called
     */
    public int getTotalSize() {
        return totalSize;
    }

    /**
     * @return QueryType of this target
     */
    public QueryType getQueryType() {
        return queryType;
    }

    /**
     * Gets the latest modification timestamp from the array of records.
     * @param records
     * @return latest modification time stamp
     * @throws JSONException
     */
    public long getLatestModificationTimeStamp(JSONArray records) throws JSONException {
        return getLatestModificationTimeStamp(records, getModificationDateFieldName());
    }

    /**
     * Gets the latest modification timestamp from the array of records.
     * @param records
     * @param modifiedDateFieldName
     * @return
     * @throws JSONException
     */
    protected long getLatestModificationTimeStamp(JSONArray records, String modifiedDateFieldName) throws JSONException {
        long maxTimeStamp = -1;
        for (int i = 0; i < records.length(); i++) {
            String timeStampStr = JSONObjectHelper.optString(records.getJSONObject(i), modifiedDateFieldName);
            if (timeStampStr == null) {
                maxTimeStamp = -1;
                break; // field not present
            }
            try {
                long timeStamp = Constants.TIMESTAMP_FORMAT.parse(timeStampStr).getTime();
                maxTimeStamp = Math.max(timeStamp, maxTimeStamp);
            } catch (Exception e) {
                SmartSyncLogger.w(TAG, "Could not parse modification date field: " + modifiedDateFieldName, e);
                maxTimeStamp = -1;
                break;
            }
        }
        return maxTimeStamp;
    }

    /**
     * Return ids of records that should not be written over
     * during a sync down with merge mode leave-if-changed
     * @return set of ids 
     * @throws JSONException
     */
    public Set<String> getIdsToSkip(SyncManager syncManager, String soupName) throws JSONException {
        return getDirtyRecordIds(syncManager, soupName, getIdFieldName());
    }

    /**
     * Enum for query type
     */
    public enum QueryType {
    	mru,
    	sosl,
    	soql,
        refresh,
        parent_children,
        custom
    }

    /**
     * Helper method to parse IDs from a network response to a SOQL query.
     *
     * @param records SObject records.
     * @return Set of IDs.
     */
    protected Set<String> parseIdsFromResponse(JSONArray records) {
        final Set<String> remoteIds = new HashSet<String>();
        if (records != null) {
            for (int i = 0; i < records.length(); i++) {
                final JSONObject idJson = records.optJSONObject(i);
                if (idJson != null) {
                    remoteIds.add(idJson.optString(getIdFieldName()));
                }
            }
        }
        return remoteIds;
    }
}
