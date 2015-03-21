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

import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartsync.manager.SyncManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Target for sync that downloads most recently used records
 */
public class MruSyncDownTarget extends SyncDownTarget {
	
	public static final String FIELDLIST = "fieldlist";
	public static final String SOBJECT_TYPE = "sobjectType";	
	private List<String> fieldlist;
	private String objectType;
	
	/**
	 * Build SyncDownTarget from json
	 * @param target as json
	 * @return
	 * @throws JSONException 
	 */
	public static SyncDownTarget fromJSON(JSONObject target) throws JSONException {
		if (target == null)
			return null;
		
		List<String> fieldlist = toList(target.optJSONArray(FIELDLIST));
		String objectType = target.optString(SOBJECT_TYPE, null);
		return new MruSyncDownTarget(fieldlist, objectType);
	}

	/**
	 * Build SyncDownTarget for mru target
	 * @param objectType
	 * @param fieldlist
	 * @return
	 */
	public static SyncDownTarget targetForMRUSyncDown(String objectType, List<String> fieldlist) {
		return new MruSyncDownTarget(fieldlist, objectType);
	}
	
	/**
	 * Constructor
	 * @param fieldlist
	 * @param objectType
	 */
	public MruSyncDownTarget(List<String> fieldlist, String objectType) {
		this.queryType = QueryType.mru;
		this.fieldlist = fieldlist;
		this.objectType = objectType;
	}
	
	/**
	 * @return json representation of target
	 * @throws JSONException
	 */
	public JSONObject asJSON() throws JSONException {
		JSONObject target = new JSONObject();
		target.put(QUERY_TYPE, queryType.name());
		target.put(FIELDLIST, new JSONArray(fieldlist));
		target.put(SOBJECT_TYPE, objectType);
		return target;
	}

    @Override
    public JSONArray startFetch(SyncManager syncManager, long maxTimeStamp) throws IOException, JSONException {
        RestRequest request = RestRequest.getRequestForMetadata(syncManager.apiVersion, objectType);
        RestResponse response = syncManager.sendSyncWithSmartSyncUserAgent(request);
        List<String> recentItems = pluck(response.asJSONObject().getJSONArray(Constants.RECENT_ITEMS), Constants.ID);

        // Building SOQL query to get requested at
        String soql = SOQLBuilder.getInstanceWithFields(fieldlist).from(objectType).where("Id IN ('" + TextUtils.join("', '", recentItems) + "')").build();

        // Get recent items attributes from server
        request = RestRequest.getRequestForQuery(syncManager.apiVersion, soql);
        response = syncManager.sendSyncWithSmartSyncUserAgent(request);
        JSONObject responseJson = response.asJSONObject();
        JSONArray records = responseJson.getJSONArray(Constants.RECORDS);

        // Recording total size
        totalSize = records.length();

        return records;
    }

    @Override
    public JSONArray continueFetch(SyncManager syncManager) throws IOException, JSONException {
        return null;
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
	
	@SuppressWarnings("unchecked")
	private static <T> List<T> toList(JSONArray jsonArray) throws JSONException {
		if (jsonArray == null) {
			return null;
		}
		List<T> arr = new ArrayList<T>();
		for (int i=0; i<jsonArray.length(); i++) {
			arr.add((T) jsonArray.get(i));
		}
		return arr;
	}

    @SuppressWarnings("unchecked")
    private <T> List<T> pluck(JSONArray jsonArray, String key) throws JSONException {
        List<T> arr = new ArrayList<T>();
        for (int i=0; i<jsonArray.length(); i++) {
            arr.add((T) jsonArray.getJSONObject(i).get(key));
        }
        return arr;
    }
}
