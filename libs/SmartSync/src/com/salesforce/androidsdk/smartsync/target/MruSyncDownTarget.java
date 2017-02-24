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
import com.salesforce.androidsdk.smartsync.util.SOQLBuilder;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Target for sync that downloads most recently used records
 */
public class MruSyncDownTarget extends SyncDownTarget {
	
	public static final String FIELDLIST = "fieldlist";
	public static final String SOBJECT_TYPE = "sobjectType";
    private static final String TAG = "MruSyncDownTarget";
	private List<String> fieldlist;
	private String objectType;

    /**
     * Construct MruSyncDownTarget from json
     * @param target
     * @throws JSONException
     */
	public MruSyncDownTarget(JSONObject target) throws JSONException {
        super(target);
        this.fieldlist = JSONObjectHelper.toList(target.getJSONArray(FIELDLIST));
        this.objectType = target.getString(SOBJECT_TYPE);
	}

	/**
	 * Constructor
	 * @param fieldlist
	 * @param objectType
	 */
	public MruSyncDownTarget(List<String> fieldlist, String objectType) throws JSONException {
        super();
        this.queryType = QueryType.mru;
        this.fieldlist = fieldlist;
        this.objectType = objectType;
	}

    /**
     * @return json representation of target
     * @throws JSONException
     */
    public JSONObject asJSON() throws JSONException {
        JSONObject target = super.asJSON();
		target.put(FIELDLIST, new JSONArray(fieldlist));
		target.put(SOBJECT_TYPE, objectType);
		return target;
	}

    @Override
    public JSONArray startFetch(SyncManager syncManager, long maxTimeStamp) throws IOException, JSONException {
        final RestRequest request = RestRequest.getRequestForMetadata(syncManager.apiVersion, objectType);
        final RestResponse response = syncManager.sendSyncWithSmartSyncUserAgent(request);
        final List<String> recentItems = JSONObjectHelper.pluck(response.asJSONObject().getJSONArray(Constants.RECENT_ITEMS), Constants.ID);

        // Building SOQL query to get requested at.
        final String soql = SOQLBuilder.getInstanceWithFields(fieldlist).from(objectType).where(getIdFieldName()
                + " IN ('" + TextUtils.join("', '", recentItems) + "')").build();
        return startFetch(syncManager, maxTimeStamp, soql);
    }

    private JSONArray startFetch(SyncManager syncManager, long maxTimeStamp, String queryRun) throws IOException, JSONException {
        final RestRequest request = RestRequest.getRequestForQuery(syncManager.apiVersion, queryRun);
        final RestResponse response = syncManager.sendSyncWithSmartSyncUserAgent(request);
        final JSONObject responseJson = response.asJSONObject();
        final JSONArray records = responseJson.getJSONArray(Constants.RECORDS);

        // Recording total size
        totalSize = records.length();
        return records;
    }

    @Override
    public JSONArray continueFetch(SyncManager syncManager) throws IOException, JSONException {
        return null;
    }

    @Override
    protected Set<String> getRemoteIds(SyncManager syncManager, Set<String> localIds) throws IOException, JSONException {
        if (localIds == null) {
            return null;
        }
        final String idFieldName = getIdFieldName();
        final Set<String> remoteIds = new HashSet<String>();

        // Alters the SOQL query to get only IDs.
        final String soql = SOQLBuilder.getInstanceWithFields(idFieldName).from(objectType).where(idFieldName
                + " IN ('" + TextUtils.join("', '", localIds) + "')").build();

        // Makes network request and parses the response.
        final JSONArray records = startFetch(syncManager, 0, soql);
        remoteIds.addAll(parseIdsFromResponse(records));

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
