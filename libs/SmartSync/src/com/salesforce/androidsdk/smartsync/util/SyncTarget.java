/*
 * Copyright (c) 2014, salesforce.com, inc.
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

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Target for sync u i.e. set of objects to download from server
 */
public class SyncTarget {
	
	public static final String QUERY_TYPE = "type";
	public static final String QUERY = "query";
	public static final String FIELDLIST = "fieldlist";
	public static final String SOBJECT_TYPE = "sobjectType";	
	
	private QueryType queryType;
	private String query;
	private List<String> fieldlist;
	private String objectType;
	
	/**
	 * Build SyncTarget from json
	 * @param target as json
	 * @return
	 * @throws JSONException 
	 */
	public static SyncTarget fromJSON(JSONObject target) throws JSONException {
		if (target == null)
			return null;
		
		QueryType queryType = QueryType.valueOf(target.getString(QUERY_TYPE));
		String query = target.optString(QUERY, null);
		List<String> fieldlist = toList(target.optJSONArray(FIELDLIST));
		String objectType = target.optString(SOBJECT_TYPE, null);
		return new SyncTarget(queryType, query, fieldlist, objectType);
	}

	/**
	 * Build SyncTarget for soql target
	 * @param soql
	 * @return
	 */
	public static SyncTarget targetForSOQLSyncDown(String soql) {
		return new SyncTarget(QueryType.soql, soql, null, null);
	}
	
	/**
	 * Build SyncTarget for sosl target
	 * @param soql
	 * @return
	 */
	public static SyncTarget targetForSOSLSyncDown(String sosl) {
		return new SyncTarget(QueryType.sosl, sosl, null, null);
	}

	/**
	 * Build SyncTarget for mru target
	 * @param objectType
	 * @param fieldlist
	 * @return
	 */
	public static SyncTarget targetForMRUSyncDown(String objectType, List<String> fieldlist) {
		return new SyncTarget(QueryType.mru, null, fieldlist, objectType);
	}
	
	/**
	 * Private constructor
	 * @param queryType
	 * @param query
	 * @param fieldlist
	 * @param objectType
	 */
	private SyncTarget(QueryType queryType, String query, List<String> fieldlist, String objectType) {
		this.queryType = queryType;
		this.query = query;
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
		if (query != null) target.put(QUERY, query);
		if (fieldlist != null) target.put(FIELDLIST, new JSONArray(fieldlist));
		if (objectType != null) target.put(SOBJECT_TYPE, objectType);
		return target;
	}

	public QueryType getQueryType() {
		return queryType;
	}
	
	public String getQuery() {
		return query;
	}
	
	public List<String> getFieldlist() {
		return fieldlist;
	}
	
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

    /**
     * Enum for query type
     */
    public enum QueryType {
    	mru,
    	sosl,
    	soql
    }
}
