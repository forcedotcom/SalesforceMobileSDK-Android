/*
 * Copyright (c) 2014-2105, salesforce.com, inc.
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

import android.text.TextUtils;
import android.util.Log;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.rest.ApiVersionStrings;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestRequest.RestMethod;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartstore.app.SalesforceSDKManagerWithSmartStore;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.androidsdk.smartsync.util.SOQLBuilder;
import com.salesforce.androidsdk.smartsync.util.SyncOptions;
import com.salesforce.androidsdk.smartsync.util.SyncState;
import com.salesforce.androidsdk.smartsync.util.SyncState.MergeMode;
import com.salesforce.androidsdk.smartsync.util.SyncTarget;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sync Manager
 */
public class SyncManager {

    // Constants
    public static final int PAGE_SIZE = 2000;
    private static final int UNCHANGED = -1;

    // For user agent
    private static final String SMART_SYNC = "SmartSync";

    // Local fields
    public static final String LOCALLY_CREATED = "__locally_created__";
    public static final String LOCALLY_UPDATED = "__locally_updated__";
    public static final String LOCALLY_DELETED = "__locally_deleted__";
    public static final String LOCAL = "__local__";

    // Static member
    private static Map<String, SyncManager> INSTANCES;

    // Time stamp format
    public static final DateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    // Members
    private final ExecutorService threadPool = Executors.newFixedThreadPool(1);
    private String apiVersion;
	private SmartStore smartStore;
	private RestClient restClient;

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
     * Get details of a sync state
     * @param syncId
     * @return
     * @throws JSONException
     */
    public SyncState getSyncStatus(long syncId) throws JSONException {
    	return SyncState.byId(smartStore, syncId);
    }

    /**
     * Create and run a sync down that will overwrite any modified records
     * @param target
     * @param soupName
     * @param callback
     * @return
     * @throws JSONException
     */
    public SyncState syncDown(SyncTarget target, String soupName, SyncUpdateCallback callback) throws JSONException {
        SyncOptions options = SyncOptions.optionsForSyncDown(MergeMode.OVERWRITE);
        return syncDown(target, options, soupName, callback);
    }

    /**
     * Create and run a sync down
     * @param target
     * @param options
      *@param soupName
     * @param callback
     * @return
     * @throws JSONException
     */
    public SyncState syncDown(SyncTarget target, SyncOptions options, String soupName, SyncUpdateCallback callback) throws JSONException {
    	SyncState sync = SyncState.createSyncDown(smartStore, target, options, soupName);
		runSync(sync, callback);
		return sync;
    }

    /**
     * Re-run sync but only fetch new/modified records
     * @param syncId
     * @param callback
     * @throws JSONException
     */
    public SyncState reSync(long syncId, SyncUpdateCallback callback) throws JSONException {
        SyncState sync = SyncState.byId(smartStore, syncId);
        if (sync == null) {
            throw new SmartSyncException("Cannot run reSync:" + syncId + ": no sync found");
        }
        if (sync.getType() != SyncState.Type.syncDown || sync.getTarget().getQueryType() != SyncTarget.QueryType.soql) {
            throw new SmartSyncException("Cannot run reSync:" + syncId + ": wrong type:" + sync.getType());
        }
        if (sync.getTarget().getQueryType() != SyncTarget.QueryType.soql) {
            throw new SmartSyncException("Cannot run reSync:" + syncId + ": wrong query type:" + sync.getTarget().getQueryType());
        }
        if (sync.getStatus() != SyncState.Status.DONE) {
            throw new SmartSyncException("Cannot run reSync:" + syncId + ": not done:" + sync.getStatus());
        }
        sync.setTotalSize(-1);
        runSync(sync, callback);
        return sync;
    }

	/**
	 * Run a sync
	 * @param sync
	 * @param callback 
	 */
	public void runSync(final SyncState sync, final SyncUpdateCallback callback) {
		updateSync(sync, SyncState.Status.RUNNING, 0, callback);
		threadPool.execute(new Runnable() {
			@Override
			public void run() {
				try {
					switch(sync.getType()) {
					case syncDown: syncDown(sync, callback); break;
					case syncUp:   syncUp(sync, callback); break;
					}
					updateSync(sync, SyncState.Status.DONE, 100, callback);
				}
				catch (Exception e) {
					Log.e("SmartSyncManager:runSync", "Error during sync: " + sync.getId(), e);
					// Update status to failed
					updateSync(sync, SyncState.Status.FAILED,  UNCHANGED, callback);
				}
			}
		});
	}

    /**
     * Create and run a sync up
     * @param options
     * @param soupName
     * @param callback
     * @return
     * @throws JSONException
     */
    public SyncState syncUp(SyncOptions options, String soupName, SyncUpdateCallback callback) throws JSONException {
    	SyncState sync = SyncState.createSyncUp(smartStore, options, soupName);
    	runSync(sync, callback);
    	return sync;
    }

    /**
	 * Private parameterized constructor.
	 *
	 * @param account User account.
	 * @param communityId Community ID.
	 */
	private SyncManager(UserAccount account, String communityId) {
	    apiVersion = ApiVersionStrings.VERSION_NUMBER;
	    smartStore = CacheManager.getInstance(account, communityId).getSmartStore();
        restClient = SalesforceSDKManager.getInstance().getClientManager().peekRestClient(account);
		SyncState.setupSyncsSoupIfNeeded(smartStore);
	}

	/**
     * Update sync with new status, progress, totalSize
     * @param sync 
	 * @param status
	 * @param progress pass -1 to keep the current value
	 * @param callback
     */
    private void updateSync(SyncState sync, SyncState.Status status, int progress, SyncUpdateCallback callback) {
    	try {
    		sync.setStatus(status);
    		if (progress != UNCHANGED) sync.setProgress(progress);
    		sync.save(smartStore);
	    	callback.onUpdate(sync);
    	}
    	catch (JSONException e) {
    		Log.e("SmartSyncManager:updateSync", "Unexpected json error for sync: " + sync.getId(), e);
    	}
    }
    
    private void syncUp(SyncState sync, SyncUpdateCallback callback) throws Exception {
		final String soupName = sync.getSoupName();
		final SyncOptions options = sync.getOptions();
		final List<String> fieldlist = options.getFieldlist();
		final MergeMode mergeMode = options.getMergeMode();
        final Set<String> dirtyRecordIds = getDirtyRecordIds(soupName, SmartStore.SOUP_ENTRY_ID);
		int totalSize = dirtyRecordIds.size();
        sync.setTotalSize(totalSize);
        updateSync(sync, SyncState.Status.RUNNING, 0, callback);
        int i = 0;
        for (final String id : dirtyRecordIds) {
            JSONObject record = smartStore.retrieve(soupName, Long.valueOf(id)).getJSONObject(0);
            syncUpOneRecord(soupName, fieldlist, record, mergeMode);

            // Updating status
            int progress = (i + 1) * 100 / totalSize;
            if (progress < 100) {
                updateSync(sync, SyncState.Status.RUNNING, progress, callback);
            }

            // Incrementing i
            i++;
        }
	}

    private boolean isNewerThanServer(String objectType, String objectId,
    		long lastModifiedDate) throws JSONException, IOException {
    	boolean isNewer = false;
    	long serverLastModified = UNCHANGED;
    	final SOQLBuilder builder = SOQLBuilder.getInstanceWithFields(Constants.LAST_MODIFIED_DATE);
        builder.from(objectType);
        builder.where(Constants.ID + " = '" + objectId + "'");
        final String query = builder.build();
        RestResponse lastModResponse = null;
        lastModResponse = sendSyncWithSmartSyncUserAgent(RestRequest.getRequestForQuery(apiVersion, query));
        if (lastModResponse != null && lastModResponse.isSuccess()) {
            final JSONObject responseJSON = lastModResponse.asJSONObject();
            if (responseJSON != null) {
                final JSONArray records = responseJSON.optJSONArray("records");
                if (records != null && records.length() > 0) {
                	final JSONObject obj = records.optJSONObject(0);
                	if (obj != null) {
                    	final String lastModStr = obj.optString(Constants.LAST_MODIFIED_DATE);
                        if (!TextUtils.isEmpty(lastModStr)) {
                        	try {
                        		serverLastModified = TIMESTAMP_FORMAT.parse(lastModStr).getTime();
                        	} catch (ParseException e) {
                        		Log.e("SmartSyncManager:isNewerThanServer", "Error during date parsing", e);
                        	}
                        }
                	}
                }
            }
        }
        if (serverLastModified <= lastModifiedDate) {
        	isNewer = true;
        }
    	return isNewer;
    }

    private boolean syncUpOneRecord(String soupName, List<String> fieldlist,
    		JSONObject record, MergeMode mergeMode) throws JSONException, IOException {

        // Do we need to do a create, update or delete
        Action action = null;
        if (record.getBoolean(LOCALLY_DELETED))
            action = Action.delete;
        else if (record.getBoolean(LOCALLY_CREATED))
            action = Action.create;
        else if (record.getBoolean(LOCALLY_UPDATED))
            action = Action.update;
        if (action == null) {

            // Nothing to do for this record
            return true;
        }

        // Getting type and id
        final String objectType = (String) SmartStore.project(record, Constants.SOBJECT_TYPE);
        final String objectId = record.getString(Constants.ID);
        long lastModifiedDate = UNCHANGED;
        final String lastModStr = record.optString(Constants.LAST_MODIFIED_DATE);
        if (!TextUtils.isEmpty(lastModStr)) {
        	try {
        		lastModifiedDate = TIMESTAMP_FORMAT.parse(lastModStr).getTime();
        	} catch (ParseException e) {
        		Log.e("SmartSyncManager:syncUpOneRecord", "Error during date parsing", e);
        	}
        }

        /*
         * Checks if we are attempting to update a record that has been updated
         * on the server AFTER the client's last sync down. If the merge mode
         * passed in tells us to leave the record alone under these
         * circumstances, we will do nothing and return here.
         */
        if (mergeMode == MergeMode.LEAVE_IF_CHANGED &&
        		(action == Action.update || action == Action.delete) &&
        		!isNewerThanServer(objectType, objectId, lastModifiedDate)) {

        	// Nothing to do for this record
        	return true;
        }

        // Fields to save (in the case of create or update)
        Map<String, Object> fields = new HashMap<String, Object>();
        if (action == Action.create || action == Action.update) {
            for (String fieldName : fieldlist) {
                if (!fieldName.equals(Constants.ID)) {
                    fields.put(fieldName, record.get(fieldName));
                }
            }
        }

        // Building create/update/delete request
        RestRequest request = null;
        switch (action) {
            case create:
                request = RestRequest.getRequestForCreate(apiVersion, objectType, fields);
                break;
            case delete:
                request = RestRequest.getRequestForDelete(apiVersion, objectType, objectId);
                break;
            case update:
                request = RestRequest.getRequestForUpdate(apiVersion, objectType, objectId, fields);
                break;
            default:
                break;

        }

        // Call server
        RestResponse response = sendSyncWithSmartSyncUserAgent(request);

        // Update smartstore
        if (response.isSuccess()) {
            // Replace id with server id during create
            if (action == Action.create) {
                record.put(Constants.ID, response.asJSONObject().get(Constants.LID));
            }
            // Set local flags to false
            record.put(LOCAL, false);
            record.put(LOCALLY_CREATED, false);
            record.put(LOCALLY_UPDATED, false);
            record.put(LOCALLY_DELETED, false);

            // Remove entry on delete
            if (action == Action.delete) {
                smartStore.delete(soupName, record.getLong(SmartStore.SOUP_ENTRY_ID));
            }
            // Update entry otherwise
            else {
                smartStore.update(soupName, record, record.getLong(SmartStore.SOUP_ENTRY_ID));
            }
        }
        return false;
    }

    private void syncDown(SyncState sync, SyncUpdateCallback callback) throws Exception {
		switch(sync.getTarget().getQueryType()) {
		case mru:  syncDownMru(sync, callback); break;
		case soql: syncDownSoql(sync, callback); break;
		case sosl: syncDownSosl(sync, callback); break;
		}
	}
	
	private void syncDownMru(SyncState sync, SyncUpdateCallback callback) throws Exception {
		SyncTarget target = sync.getTarget();
        MergeMode mergeMode = sync.getMergeMode();
		String sobjectType = target.getObjectType();
		List<String>fieldlist = target.getFieldlist();
    	String soupName = sync.getSoupName();
    	
    	// Get recent items ids from server
		RestRequest request = RestRequest.getRequestForMetadata(apiVersion, sobjectType);
		RestResponse response = sendSyncWithSmartSyncUserAgent(request);
		List<String> recentItems = pluck(response.asJSONObject().getJSONArray(Constants.RECENT_ITEMS), Constants.ID);

		// Building SOQL query to get requested at
		String soql = SOQLBuilder.getInstanceWithFields(fieldlist).from(sobjectType).where("Id IN ('" + TextUtils.join("', '", recentItems) + "')").build();

		// Get recent items attributes from server
		request = RestRequest.getRequestForQuery(apiVersion, soql);
		response = sendSyncWithSmartSyncUserAgent(request);
		JSONObject responseJson = response.asJSONObject();
		JSONArray records = responseJson.getJSONArray(Constants.RECORDS);
		int totalSize = records.length();
        sync.setTotalSize(totalSize);

		// Save to smartstore
		updateSync(sync, SyncState.Status.RUNNING, 0, callback);
		if (totalSize > 0)
			saveRecordsToSmartStore(soupName, records, mergeMode);
	}

	private void syncDownSoql(SyncState sync, SyncUpdateCallback callback) throws Exception {
		String soupName = sync.getSoupName();	
		SyncTarget target = sync.getTarget();
        MergeMode mergeMode = sync.getMergeMode();
		String query = target.getQuery();

        // Is is a resync?
        long maxTimeStamp = sync.getMaxTimeStamp();
        query = addFilterForReSync(query, maxTimeStamp);

        RestRequest request = RestRequest.getRequestForQuery(apiVersion, query);
	
		// Call server
		RestResponse response = sendSyncWithSmartSyncUserAgent(request);
		JSONObject responseJson = response.asJSONObject();

		int countSaved = 0;
		int totalSize = responseJson.getInt(Constants.TOTAL_SIZE);
        sync.setTotalSize(totalSize);
        updateSync(sync, SyncState.Status.RUNNING, 0, callback);
		
		do {
			JSONArray records = responseJson.getJSONArray(Constants.RECORDS);
			// Save to smartstore
			saveRecordsToSmartStore(soupName, records, mergeMode);
			countSaved += records.length();
            maxTimeStamp = Math.max(maxTimeStamp, getMaxTimeStamp(records));

			// Update sync status
            if (countSaved < totalSize)
                updateSync(sync, SyncState.Status.RUNNING, countSaved*100 / totalSize, callback);

			// Fetch next records if any
			String nextRecordsUrl = JSONObjectHelper.optString(responseJson, Constants.NEXT_RECORDS_URL);
			RestRequest restRequest = new RestRequest(RestMethod.GET, nextRecordsUrl, null);
			responseJson = nextRecordsUrl == null ? null : sendSyncWithSmartSyncUserAgent(restRequest).asJSONObject();
		}
		while (responseJson != null);
        sync.setMaxTimeStamp(maxTimeStamp);
	}

    public String addFilterForReSync(String query, long maxTimeStamp) {
        if (maxTimeStamp != UNCHANGED) {
            String extraPredicate = Constants.LAST_MODIFIED_DATE + " > " + TIMESTAMP_FORMAT.format(new Date(maxTimeStamp));
            query = query.toLowerCase().contains(" where ")
                        ? query.replaceFirst("( [wW][hH][eE][rR][eE] )", "$1" + extraPredicate + " and ")
                        : query.replaceFirst("( [fF][rR][oO][mM][ ]+[^ ]*)", "$1 where " + extraPredicate);
        }
        return query;
    }

    private void syncDownSosl(SyncState sync, SyncUpdateCallback callback) throws Exception {
		String soupName = sync.getSoupName();	
		SyncTarget target = sync.getTarget();
        MergeMode mergeMode = sync.getMergeMode();
        String query = target.getQuery();
		RestRequest request = RestRequest.getRequestForSearch(apiVersion, query);
	
		// Call server
		RestResponse response = sendSyncWithSmartSyncUserAgent(request);
	
		// Parse response
		JSONArray records = response.asJSONArray();
		int totalSize = records.length();
        sync.setTotalSize(totalSize);

		// Save to smartstore
		updateSync(sync, SyncState.Status.RUNNING, 0, callback);
		if (totalSize > 0)
			saveRecordsToSmartStore(soupName, records, mergeMode);
	}

	@SuppressWarnings("unchecked")
	private <T> List<T> pluck(JSONArray jsonArray, String key) throws JSONException {
		List<T> arr = new ArrayList<T>();
		for (int i=0; i<jsonArray.length(); i++) {
			arr.add((T) jsonArray.getJSONObject(i).get(key));
		}
		return arr;
	}

    private long getMaxTimeStamp(JSONArray jsonArray) throws JSONException {
        long maxTimeStamp = UNCHANGED;
        for (int i = 0; i < jsonArray.length(); i++) {
            String timeStampStr = JSONObjectHelper.optString(jsonArray.getJSONObject(i), Constants.LAST_MODIFIED_DATE);
            if (timeStampStr == null) {
                maxTimeStamp = UNCHANGED;
                break; // LastModifiedDate field not present
            }
            try {
                long timeStamp = TIMESTAMP_FORMAT.parse(timeStampStr).getTime();
                maxTimeStamp = Math.max(timeStamp, maxTimeStamp);
            } catch (Exception e) {
                Log.w("SmartSync.getMaxTimeStamp", "Could not parse LastModifiedDate", e);
                maxTimeStamp = UNCHANGED;
                break;
            }
        }
        return maxTimeStamp;
    }

    private Set<String> toSet(JSONArray jsonArray) throws JSONException {
        Set<String> set = new HashSet<String>();
        for (int i=0; i<jsonArray.length(); i++) {
            set.add(jsonArray.getJSONArray(i).getString(0));
        }
        return set;
    }
	
	private void saveRecordsToSmartStore(String soupName, JSONArray records, MergeMode mergeMode)
			throws JSONException {
        // Gather ids of dirty records
        Set<String> idsToSkip = null;
        if (mergeMode == MergeMode.LEAVE_IF_CHANGED) {
            idsToSkip = getDirtyRecordIds(soupName, Constants.ID);
        }
        smartStore.beginTransaction();
		for (int i = 0; i < records.length(); i++) {
			JSONObject record = records.getJSONObject(i);

            // Skip?
            if (mergeMode == MergeMode.LEAVE_IF_CHANGED) {
                String id = JSONObjectHelper.optString(record, Constants.ID);
                if (id != null && idsToSkip.contains(id)) {
                    continue; // don't write over dirty record
                }
            }

            // Save
            record.put(LOCAL, false);
            record.put(LOCALLY_CREATED, false);
            record.put(LOCALLY_UPDATED, false);
            record.put(LOCALLY_DELETED, false);
            smartStore.upsert(soupName, records.getJSONObject(i), Constants.ID, false);
		}
		smartStore.setTransactionSuccessful();
		smartStore.endTransaction();
	}

    private Set<String> getDirtyRecordIds(String soupName, String idField) throws JSONException {
        Set<String> idsToSkip = new HashSet<String>();
        String dirtyRecordsSql = String.format("SELECT {%s:%s} FROM {%s} WHERE {%s:%s} = 'true'", soupName, idField, soupName, soupName, LOCAL);
        final QuerySpec smartQuerySpec = QuerySpec.buildSmartQuerySpec(dirtyRecordsSql, PAGE_SIZE);
        boolean hasMore = true;
        for (int pageIndex = 0; hasMore; pageIndex++) {
            JSONArray results = smartStore.query(smartQuerySpec, pageIndex);
            hasMore = (results.length() == PAGE_SIZE);
            idsToSkip.addAll(toSet(results));
        }
        return idsToSkip;
    }

    /**
     * Send request after adding user-agent header that says SmartSync
	 * @param restRequest
	 * @return
	 * @throws IOException
	 */
	private RestResponse sendSyncWithSmartSyncUserAgent(RestRequest restRequest) throws IOException {
		Map<String, String> headers = restRequest.getAdditionalHttpHeaders();
		if (headers == null)
			headers = new HashMap<String, String>();
		headers.put(HttpAccess.USER_AGENT, SalesforceSDKManager.getInstance().getUserAgent(SMART_SYNC));
		return restClient.sendSync(restRequest.getMethod(), restRequest.getPath(), restRequest.getRequestEntity(), headers);
	}

	/**
     * Enum for action
     *
     */
    public enum Action {
    	create,
    	update,
    	delete
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
    

    /**
     * Sets the rest client to be used.
     * This is primarily used only by tests.
     * 
     * @param restClient
     */
    public void setRestClient(RestClient restClient) {
        this.restClient = restClient;
    }
    
    
	/**
	 * Callback to get sync status udpates
	 */
	public interface SyncUpdateCallback {
		void onUpdate(SyncState sync);
	}
}