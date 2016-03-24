/*
 * Copyright (c) 2015, salesforce.com, inc.
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
package com.salesforce.androidsdk.smartsync.util;

import android.text.TextUtils;
import android.util.Log;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.ApiVersionStrings;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartsync.manager.SyncManager;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
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
        this.query = target.getString(QUERY);
        addSpecialFieldsIfRequired();
    }

	/**
     * Construct SoqlSyncDownTarget from soql query
	 * @param query
	 */
	public SoqlSyncDownTarget(String query) {
        super();
        this.queryType = QueryType.soql;
        this.query = query;
        addSpecialFieldsIfRequired();
	}

    private void addSpecialFieldsIfRequired() {
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
    }

	/**
	 * @return json representation of target
	 * @throws JSONException
	 */
	public JSONObject asJSON() throws JSONException {
		JSONObject target = super.asJSON();
        target.put(QUERY, query);
		return target;
	}

    @Override
    public JSONArray startFetch(SyncManager syncManager, long maxTimeStamp) throws IOException, JSONException {
        String queryToRun = maxTimeStamp > 0 ? SoqlSyncDownTarget.addFilterForReSync(query, maxTimeStamp) : query;
        RestRequest request = RestRequest.getRequestForQuery(syncManager.apiVersion, queryToRun);
        RestResponse response = syncManager.sendSyncWithSmartSyncUserAgent(request);
        JSONObject responseJson = response.asJSONObject();
        JSONArray records = responseJson.getJSONArray(Constants.RECORDS);

        // Record total size
        totalSize = responseJson.getInt(Constants.TOTAL_SIZE);

        // Capture next records url
        nextRecordsUrl = JSONObjectHelper.optString(responseJson, Constants.NEXT_RECORDS_URL);
        return records;
    }

    @Override
    public JSONArray continueFetch(SyncManager syncManager) throws IOException, JSONException {
        if (nextRecordsUrl == null) {
            return null;
        }
        RestRequest request = new RestRequest(RestRequest.RestMethod.GET, nextRecordsUrl, null);
        RestResponse response = syncManager.sendSyncWithSmartSyncUserAgent(request);
        JSONObject responseJson = response.asJSONObject();
        JSONArray records = responseJson.getJSONArray(Constants.RECORDS);

        // Capture next records url
        nextRecordsUrl = JSONObjectHelper.optString(responseJson, Constants.NEXT_RECORDS_URL);
        return records;
    }

    @Override
    public Set<String> getListOfRemoteIds(SyncManager syncManager, Set<String> localIds) {
        if (localIds == null) {
            return null;
        }
        final String idFieldName = getIdFieldName();
        final Set<String> remoteIds = new HashSet<String>();

        /*
         * Compute how many SOQL queries are required to get the IDs.
         * SOQL has a limit of 2000 records per query, so we need to break it up.
         */
        final List<Set<String>> uberQueryList = new ArrayList<Set<String>>();
        Set<String> smallSet = null;
        for (final String value : localIds) {
            if (smallSet == null || smallSet.size() == 2000) {
                uberQueryList.add(smallSet = new HashSet<String>());
            }
            smallSet.add(value);
        }

        // Iterates through the uber list of sets and creates multiple SOQL queries.
        for (final Set<String> queryList : uberQueryList) {

            // Constructs a SOQL query to get IDs.
            final StringBuilder soql = new StringBuilder("SELECT ");
            soql.append(idFieldName);
            soql.append(" FROM ");

            // Reads SObject name from the SOQL query itself.
            final String[] result = query.toLowerCase().split("from");
            result[1] = result[1].trim();
            final String[] sObject = result[1].split(" ");
            soql.append(sObject[0]);
            soql.append(" WHERE ");
            soql.append(idFieldName);
            soql.append(" IN (");
            for (final String localId : queryList) {
                soql.append("'");
                soql.append(localId);
                soql.append("',");
            }
            soql.deleteCharAt(soql.length() - 1);
            soql.append(")");

            // Makes network request and parses the response.
            try {
                final RestRequest request = RestRequest.getRequestForQuery(ApiVersionStrings.getVersionNumber(SalesforceSDKManager.getInstance().getAppContext()), soql.toString());
                final RestResponse response = syncManager.sendSyncWithSmartSyncUserAgent(request);
                if (response != null && response.isSuccess()) {
                    final JSONObject responseJson = response.asJSONObject();
                    if (responseJson != null) {
                        final JSONArray records = responseJson.getJSONArray(Constants.RECORDS);
                        if (records != null) {
                            for (int i = 0; i < records.length(); i++) {
                                final JSONObject idJson = records.optJSONObject(i);
                                if (idJson != null) {
                                    remoteIds.add(idJson.optString(idFieldName));
                                }
                            }
                        }
                    }
                }
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "UnsupportedEncodingException thrown while making REST request", e);
            } catch (IOException e) {
                Log.e(TAG, "IOException thrown while making REST request", e);
            } catch (JSONException e) {
                Log.e(TAG, "JSONException thrown while making REST request", e);
            }
        }
        return remoteIds;
    }

    public static String addFilterForReSync(String query, long maxTimeStamp) {
        if (maxTimeStamp > 0) {
            String extraPredicate = Constants.LAST_MODIFIED_DATE + " > " + Constants.TIMESTAMP_FORMAT.format(new Date(maxTimeStamp));
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
		return query;
	}
}
