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
package com.salesforce.androidsdk.smartsync.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.text.TextUtils;
import android.util.Log;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.rest.ApiVersionStrings;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestRequest.RestMethod;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartstore.app.SalesforceSDKManagerWithSmartStore;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;


/**
 * Sync Manager
 */
public class SyncManager {
	private static Map<String, SyncManager> INSTANCES;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(1);
	
    private String apiVersion;
    private CacheManager cacheManager;
    private NetworkManager networkManager;
    private String communityId;
	private SmartStore smartStore;
	private RestClient restClient;
	
	// SmartStore
    private static final String SYNCS_SOUP = "syncs_soup";
	private static final String SYNC_TYPE = "type";
	private static final String SYNC_TARGET = "target";
	private static final String SYNC_SOUP_NAME = "soupName";
	private static final String SYNC_STATUS = "status";
	private static final String SYNC_PROGRESS = "progress";
	
	// Target
	private static final String QUERY_TYPE = "type";
	private static final String QUERY = "query";

	// Server response
	private static final String RECORDS = "records";
	private static final String ID = "Id";
	private static final String NEXT_RECORDS_URL = "nextRecordsUrl";
	private static final String TOTAL_SIZE = "totalSize";
	
	// Local fields
	private static final String LOCALLY_CREATED = "__locally_created__";
	private static final String LOCALLY_UPDATED = "__locally_updated__";
	private static final String LOCALLY_DELETED = "__locally_deleted__";
	private static final String LOCAL = "__local__";
	
	/**
     * Returns the instance of this class associated with this user account.
     *
     * @param account User account.
     * @return Instance of this class.
     */
    public static synchronized SyncManager getInstance(UserAccount account) {
        return getInstance(account, null);
    }

    /**
     * Returns the instance of this class associated with this user and community.
     *
     * @param account User account.
     * @param communityId Community ID.
     * @return Instance of this class.
     */
    public static synchronized SyncManager getInstance(UserAccount account, String communityId) {
        if (account == null) {
            account = SalesforceSDKManagerWithSmartStore.getInstance().getUserAccountManager().getCurrentUser();
        }
        if (account == null) {
            return null;
        }
        String uniqueId = account.getUserId();
        if (UserAccount.INTERNAL_COMMUNITY_ID.equals(communityId)) {
            communityId = null;
        }
        if (!TextUtils.isEmpty(communityId)) {
            uniqueId = uniqueId + communityId;
        }
        SyncManager instance = null;
        if (INSTANCES == null) {
            INSTANCES = new HashMap<String, SyncManager>();
            instance = new SyncManager(account, communityId);
            INSTANCES.put(uniqueId, instance);
        } else {
            instance = INSTANCES.get(uniqueId);
        }
        if (instance == null) {
            instance = new SyncManager(account, communityId);
            INSTANCES.put(uniqueId, instance);
        }
        return instance;
    }

    /**
     * Resets the Sync manager associated with this user account.
     *
     * @param account User account.
     */
    public static synchronized void reset(UserAccount account) {
        reset(account, null);
    }

    /**
     * Resets the Sync manager associated with this user and community.
     *
     * @param account User account.
     * @param communityId Community ID.
     */
    public static synchronized void reset(UserAccount account, String communityId) {
        if (account == null) {
            account = SalesforceSDKManagerWithSmartStore.getInstance().getUserAccountManager().getCurrentUser();
        }
        if (account != null) {
            String uniqueId = account.getUserId();
            if (UserAccount.INTERNAL_COMMUNITY_ID.equals(communityId)) {
                communityId = null;
            }
            if (!TextUtils.isEmpty(communityId)) {
                uniqueId = uniqueId + communityId;
            }
            if (INSTANCES != null) {
                INSTANCES.remove(uniqueId);
            }
        }
    }

    /**
     * Private parameterized constructor.
     *
     * @param account User account.
     * @param communityId Community ID.
     */
    private SyncManager(UserAccount account, String communityId) {
        apiVersion = ApiVersionStrings.VERSION_NUMBER;
        this.communityId = communityId;
        cacheManager = CacheManager.getInstance(account, communityId);
        networkManager = NetworkManager.getInstance(account, communityId);
        smartStore = cacheManager.getSmartStore();
        restClient = networkManager.getRestClient();
        
    	setupSyncsSoupIfNeeded();
    }
    
    /**
     * 
     */
    private void setupSyncsSoupIfNeeded() {
    	if (smartStore.hasSoup(SYNCS_SOUP)) 
    		return;
    	
    	final IndexSpec[] indexSpecs = {
    			new IndexSpec(SYNC_TYPE, SmartStore.Type.string)
    	};    	
		smartStore.registerSoup(SYNCS_SOUP, indexSpecs);
    }
    
    public JSONObject recordSync(Type type, JSONObject target, String soupName) throws JSONException {
    	JSONObject sync = new JSONObject();
    	sync.put(SYNC_TYPE, type.name());
    	sync.put(SYNC_TARGET, target);
    	sync.put(SYNC_SOUP_NAME, soupName);
    	sync.put(SYNC_STATUS, Status.NEW.name());

    	sync = smartStore.upsert(SYNCS_SOUP, sync);
    	return sync;
    }
    
    public JSONObject getSyncStatus(long syncId) throws JSONException {
    	JSONArray syncs = smartStore.retrieve(SYNCS_SOUP, syncId);

    	if (syncs == null || syncs.length() == 0) 
    		return null;
    	
    	return syncs.getJSONObject(0);
    }

    public void runSync(final long syncId) throws JSONException {
    	
    	JSONArray syncs = smartStore.retrieve(SYNCS_SOUP, syncId);
    	
    	if (syncs == null || syncs.length() == 0) 
    		throw new SmartSyncException("Sync not found: " + syncId);
    	
    	final JSONObject sync = syncs.getJSONObject(0);
    	final Type type = Type.valueOf(sync.getString(SYNC_TYPE));
    	
    	// Update status to running
		updateSync(sync, Status.RUNNING, 0);

    	// Run (on a separate thread)
		threadPool.execute(new Runnable() {
			@Override
			public void run() {
				try {
			    	switch (type) {
					case SYNC_DOWN:
						syncDown(sync);
						break;
					case SYNC_UP:
						syncUp(sync);
						break;
					default:
						throw new SmartSyncException("Unknown sync type: " + type);
			    	}
			    	
			    	// Update status to done
					updateSync(sync, Status.DONE, 100);
				}
				catch (Exception e) {
					Log.e("SmartSyncManager:runSync", "Error during sync: " + syncId, e);
					// Update status to failed
					updateSync(sync, Status.FAILED, 0);
				}
			}
		});
    }

    private void updateSync(JSONObject sync, Status status, int progress) {
    	long syncId = -1;
    	try {
        	syncId = sync.getLong(SmartStore.SOUP_ENTRY_ID);
    		sync.put(SYNC_STATUS, status.name());
			sync.put(SYNC_PROGRESS, progress);
	    	smartStore.update(SYNCS_SOUP, sync, syncId);
	    	EventsObservable.get().notifyEvent(EventType.Sync, sync);
    	}
    	catch (JSONException e) {
    		Log.e("SmartSyncManager:updateSync", "Unexpected json error for sync: " + syncId, e);
    	}
    	
    	// TBD notify plugin
    }
    
    private void syncUp(JSONObject sync) {
    	// TODO Auto-generated method stub
	}

	private void syncDown(JSONObject sync) throws Exception {
    	JSONObject target = sync.getJSONObject(SYNC_TARGET);
    	String soupName = sync.getString(SYNC_SOUP_NAME);	
		QueryType queryType = QueryType.valueOf(target.getString(QUERY_TYPE));
		String query = target.getString(QUERY);
		RestRequest request = null;
		
		switch(queryType) {
		/*
		case MRU:
			break;
			*/
		case soql:
			request = RestRequest.getRequestForQuery(apiVersion, query);
			break;
		case sosl:
			request = RestRequest.getRequestForSearch(apiVersion, query);
			break;
		default:
			throw new SmartSyncException("Unknown query type: " + queryType);
		}

		// Call server
		RestResponse response = restClient.sendSync(request);

		// Counting records
		int countFetched = 0;
		
		while(response != null) {
			// Parse response
			JSONObject responseJson = response.asJSONObject();
			JSONArray records = responseJson.getJSONArray(RECORDS);
			int totalSize = responseJson.getInt(TOTAL_SIZE);
			
			// No records returned
			if (totalSize == 0) 
				break;
			
			// Save to SmartStore
			smartStore.beginTransaction();
			for (int i = 0; i < records.length(); i++) {
				JSONObject record = records.getJSONObject(i);
				record.put(LOCAL, false);
				record.put(LOCALLY_CREATED, false);
				record.put(LOCALLY_UPDATED, false);
				record.put(LOCALLY_DELETED, false);
				smartStore.upsert(soupName, records.getJSONObject(i), ID, false);
			}
			smartStore.setTransactionSuccessful();
			smartStore.endTransaction();

			// Updating count fetched
			countFetched += records.length();
			
			// Updating status
			updateSync(sync, Status.RUNNING, countFetched*100/totalSize);
			
			// Fetch next records if any
			String nextRecordsUrl = responseJson.optString(NEXT_RECORDS_URL, null);
			response = nextRecordsUrl == null ? null : restClient.sendSync(RestMethod.GET, nextRecordsUrl, null);
		}
	}
    
    /**
     * Enum for sync type
     */
    public enum Type {
        SYNC_DOWN,
        SYNC_UP
    }
    
    /**
     * Enum for sync status
     *
     */
    public enum Status {
    	NEW,
    	RUNNING,
    	DONE,
    	FAILED
    }
    
    /**
     * Enum for query type
     */
    public enum QueryType {
    	mru,
    	sosl,
    	soql
    }
    
    /**
     * Exception thrown by smart sync manager
     *
     */
    public static class SmartSyncException extends RuntimeException {

    	public SmartSyncException(String message) {
            super(message);
        }

		private static final long serialVersionUID = 1L;
    }    
}