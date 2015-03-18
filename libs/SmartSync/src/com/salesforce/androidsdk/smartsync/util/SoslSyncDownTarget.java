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

import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartsync.manager.SyncManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Target for sync defined by a SOSL query
 */
public class SoslSyncDownTarget extends SyncDownTarget {
	
	public static final String QUERY = "query";
	private String query;
	
	/**
	 * Build SyncDownTarget from json
	 * @param target as json
	 * @return
	 * @throws JSONException 
	 */
	public static SyncDownTarget fromJSON(JSONObject target) throws JSONException {
		if (target == null)
			return null;
		
		String query = target.getString(QUERY);
		return new SoslSyncDownTarget(query);
	}

	/**
	 * Build SyncDownTarget for sosl target
	 * @param sosl
	 * @return
	 */
	public static SyncDownTarget targetForSOSLSyncDown(String sosl) {
		return new SoslSyncDownTarget(sosl);
	}

	/**
     * Private constructor
	 * @param query
	 */
	private SoslSyncDownTarget(String query) {
		this.queryType = QueryType.sosl;
		this.query = query;
	}
	
	/**
	 * @return json representation of target
	 * @throws JSONException
	 */
	public JSONObject asJSON() throws JSONException {
		JSONObject target = new JSONObject();
		target.put(QUERY_TYPE, queryType.name());
		target.put(QUERY, query);
		return target;
	}

    @Override
    public JSONArray startFetch(SyncManager syncManager, long maxTimeStamp) throws IOException, JSONException {
        RestRequest request = RestRequest.getRequestForSearch(syncManager.apiVersion, query);
        RestResponse response = syncManager.sendSyncWithSmartSyncUserAgent(request);
        JSONArray records = response.asJSONArray();

        // Recording total size
        totalSize = records.length();

        return records;
    }

    @Override
    public JSONArray continueFetch(SyncManager syncManager) throws IOException, JSONException {
        return null;
    }

    /**
     * @return sosl query for this target
     */
	public String getQuery() {
		return query;
	}
	
}
