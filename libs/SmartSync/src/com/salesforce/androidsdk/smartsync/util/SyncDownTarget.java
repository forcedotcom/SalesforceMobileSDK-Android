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

import java.io.IOException;
import java.lang.reflect.Method;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.salesforce.androidsdk.smartsync.manager.SyncManager;

/**
 * Target for sync down:
 * - what records to download from server
 * - how to download those records
 */
public abstract class SyncDownTarget implements SyncTarget {

    // Constants
	public static final String QUERY_TYPE = "type";
    public static final String ANDROID_IMPL = "androidImpl";

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
        case mru:     return MruSyncDownTarget.fromJSON(target);
        case sosl:    return SoslSyncDownTarget.fromJSON(target);
        case soql:    return SoqlSyncDownTarget.fromJSON(target);
        case custom:
        default:
            try {
                Class<? extends SyncDownTarget> implClass = (Class<? extends SyncDownTarget>) Class.forName(target.getString(ANDROID_IMPL));
                Method method = implClass.getMethod("fromJSON", JSONObject.class);
                return (SyncDownTarget) method.invoke(null, target);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
	}

	/**
	 * @return json representation of target
	 * @throws JSONException
	 */
	public abstract JSONObject asJSON() throws JSONException;

    /**
     * @param maxTimeStamp
     * @return next record fetched
     */

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
     * Enum for query type
     */
    public enum QueryType {
    	mru,
    	sosl,
    	soql,
        custom
    }
}
