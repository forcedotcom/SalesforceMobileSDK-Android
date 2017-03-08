/*
 * Copyright (c) 2015-present, salesforce.com, inc.
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
import com.salesforce.androidsdk.smartsync.manager.SyncManager;
import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Target for sync defined by a SOQL query
 */
public class SoqlSyncDownTarget extends SyncDownTarget {

	public static final String QUERY = "query";
    private static final String TAG = "SoqlSyncDownTarget";
	private String query;
    private String nextRecordsUrl;

    /**
     * Construct SoqlSyncDownTarget from json
     * @param target
     * @throws JSONException
     */
    public SoqlSyncDownTarget(JSONObject target) throws JSONException {
        super(target);
        this.query = addSpecialFieldsIfRequired(JSONObjectHelper.optString(target, QUERY));
    }

	/**
     * Construct SoqlSyncDownTarget from soql query
	 * @param query
	 */
	public SoqlSyncDownTarget(String query) {
        this(null, null, query);
	}

    /**
     * Construct SoqlSyncDownTarget from soql query
     * @param query
     */
    public SoqlSyncDownTarget(String idFieldName, String modificationDateFieldName, String query) {
        super(idFieldName, modificationDateFieldName);
        this.queryType = QueryType.soql;
        this.query = addSpecialFieldsIfRequired(query);
    }

    private String addSpecialFieldsIfRequired(String query) {
        if (!TextUtils.isEmpty(query)) {

            // Inserts the mandatory 'LastModifiedDate' field if it doesn't exist.
            final String lastModFieldName = getModificationDateFieldName();
            if (!query.contains(lastModFieldName)) {
                query = query.replaceFirst("([sS][eE][lL][eE][cC][tT] )", "select " + lastModFieldName + ", ");
            }

            // Inserts the mandatory 'Id' field if it doesn't exist.
            final String idFieldName = getIdFieldName();
            if (!query.contains(idFieldName)) {
                query = query.replaceFirst("([sS][eE][lL][eE][cC][tT] )", "select " + idFieldName + ", ");
            }
        }
        return query;
    }

	/**
	 * @return json representation of target
	 * @throws JSONException
	 */
	public JSONObject asJSON() throws JSONException {
		JSONObject target = super.asJSON();
        if (query != null) target.put(QUERY, query);
		return target;
	}

    @Override
    public JSONArray startFetch(SyncManager syncManager, long maxTimeStamp) throws IOException, JSONException {
        return startFetch(syncManager, getQuery(maxTimeStamp));
    }

    protected JSONArray startFetch(SyncManager syncManager, String query) throws IOException, JSONException {
        RestRequest request = RestRequest.getRequestForQuery(syncManager.apiVersion, query);
        RestResponse response = syncManager.sendSyncWithSmartSyncUserAgent(request);
        JSONObject responseJson = getResponseJson(response);
        JSONArray records = getRecordsFromResponseJson(responseJson);

        // Records total size.
        totalSize = responseJson.getInt(Constants.TOTAL_SIZE);

        // Captures next records URL.
        nextRecordsUrl = JSONObjectHelper.optString(responseJson, Constants.NEXT_RECORDS_URL);
        return records;
    }

    protected JSONObject getResponseJson(RestResponse response) throws IOException {
        JSONObject responseJson;
        try {
            responseJson = response.asJSONObject();
        }
        catch (JSONException e) {
            // Rest API errors are returned as JSON array
            throw new SyncManager.SmartSyncException(response.asString());
        }
        return responseJson;
    }

    protected JSONArray getRecordsFromResponseJson(JSONObject responseJson) throws JSONException {
        return responseJson.getJSONArray(Constants.RECORDS);
    }

    @Override
    public JSONArray continueFetch(SyncManager syncManager) throws IOException, JSONException {
        if (nextRecordsUrl == null) {
            return null;
        }
        RestRequest request = new RestRequest(RestRequest.RestMethod.GET, nextRecordsUrl);
        RestResponse response = syncManager.sendSyncWithSmartSyncUserAgent(request);
        JSONObject responseJson = getResponseJson(response);
        JSONArray records = getRecordsFromResponseJson(responseJson);

        // Captures next records URL.
        nextRecordsUrl = JSONObjectHelper.optString(responseJson, Constants.NEXT_RECORDS_URL);
        return records;
    }

    @Override
    protected Set<String> getRemoteIds(SyncManager syncManager, Set<String> localIds) throws IOException, JSONException {
        return getRemoteIdsWithSoql(syncManager, getSoqlForRemoteIds());
    }

    protected Set<String> getRemoteIdsWithSoql(SyncManager syncManager, String soqlForRemoteIds) throws IOException, JSONException {
        final Set<String> remoteIds = new HashSet<String>();

        // Makes network request and parses the response.
        JSONArray records = startFetch(syncManager, soqlForRemoteIds);
        remoteIds.addAll(parseIdsFromResponse(records));
        while (records != null) {

            // Fetch next records, if any.
            records = continueFetch(syncManager);
            remoteIds.addAll(parseIdsFromResponse(records));
        }
        return remoteIds;
    }

    protected String getSoqlForRemoteIds() {
        // Alters the SOQL query to get only IDs.
        final StringBuilder soql = new StringBuilder("SELECT ");
        soql.append(getIdFieldName());
        soql.append(" FROM ");
        final String[] fromClause = getQuery(0).split("([ ][fF][rR][oO][mM][ ])");
        soql.append(fromClause[1]);
        return soql.toString();
    }

    protected static String addFilterForReSync(String query, String modificationFieldDatName, long maxTimeStamp) {
        if (maxTimeStamp > 0) {
            String extraPredicate = modificationFieldDatName + " > " + Constants.TIMESTAMP_FORMAT.format(new Date(maxTimeStamp));
            query = query.toLowerCase().contains(" where ")
                    ? query.replaceFirst("( [wW][hH][eE][rR][eE] )", "$1" + extraPredicate + " and ")
                    : query.replaceFirst("( [fF][rR][oO][mM][ ]+[^ ]*)", "$1 where " + extraPredicate);
        }
        return query;
    }

    /**
     * @return soql query for this target
     */
    public String getQuery() {
        return getQuery(0);
    }

    /**
     * @return soql query for this target
     * @param maxTimeStamp
     */
	public String getQuery(long maxTimeStamp) {
        return maxTimeStamp > 0 ? SoqlSyncDownTarget.addFilterForReSync(query, getModificationDateFieldName(), maxTimeStamp) : query;
	}
}
