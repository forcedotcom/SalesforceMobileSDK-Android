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

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.HttpStatus;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.rest.ApiVersionStrings;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartstore.app.SalesforceSDKManagerWithSmartStore;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartsync.app.SmartSyncSDKManager;
import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.androidsdk.smartsync.util.SyncDownTarget;
import com.salesforce.androidsdk.smartsync.util.SyncOptions;
import com.salesforce.androidsdk.smartsync.util.SyncState;
import com.salesforce.androidsdk.smartsync.util.SyncState.MergeMode;
import com.salesforce.androidsdk.smartsync.util.SyncUpTarget;
import com.salesforce.androidsdk.util.JSONObjectHelper;

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
    private static Map<String, SyncManager> INSTANCES = new HashMap<String, SyncManager>();

    // Members
    private Set<Long> runningSyncIds = new HashSet<Long>();
    public final String apiVersion;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(1);
	private SmartStore smartStore;
	private RestClient restClient;

    /**
     * Private constructor
     * @param smartStore
     */
    private SyncManager(SmartStore smartStore, RestClient restClient) {
        apiVersion = ApiVersionStrings.VERSION_NUMBER;
        this.smartStore = smartStore;
        this.restClient = restClient;
        SyncState.setupSyncsSoupIfNeeded(smartStore);
    }

    /**
     * Returns the instance of this class associated with current user.
     *
     * @return Instance of this class.
     */
    public static synchronized SyncManager getInstance() {
        return getInstance(null, null);
    }

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
     * Sync manager returned is ready to use.
     *
     * @param account User account.
     * @param communityId Community ID.
     * @return Instance of this class.
     */
    public static synchronized SyncManager getInstance(UserAccount account, String communityId) {
        return getInstance(account, communityId, null);
    }

    /**
     * Returns the instance of this class associated with this user, community and smartstore.
     *
     * @param account User account. Pass null to user current user.
     * @param communityId Community ID. Pass null if not applicable
     * @param smartStore SmartStore instance. Pass null to use current user default smartstore.
     *
     * @return Instance of this class.
     */
    public static synchronized SyncManager getInstance(UserAccount account, String communityId, SmartStore smartStore) {
        if (account == null) {
            account = SalesforceSDKManagerWithSmartStore.getInstance().getUserAccountManager().getCurrentUser();
        }
        if (smartStore == null) {
            smartStore = SmartSyncSDKManager.getInstance().getSmartStore(account, communityId);
        }
        String uniqueId = (account != null ? account.getUserId() : "") + ":"
                    + smartStore.getDatabase().getPath();
        SyncManager instance = INSTANCES.get(uniqueId);
        if (instance == null) {
            RestClient restClient = null;

            /*
             * If account is still null, there is no user logged in, which means, the default
             * RestClient should be set to the unauthenticated RestClient instance.
             */
            if (account == null) {
                restClient = SalesforceSDKManager.getInstance().getClientManager().peekUnauthenticatedRestClient();
            } else {
                restClient = SalesforceSDKManager.getInstance().getClientManager().peekRestClient(account);
            }
            instance = new SyncManager(smartStore, restClient);
            INSTANCES.put(uniqueId, instance);
        }
        return instance;
    }

    /**
     * Resets all the sync managers
     */
    public static synchronized void reset() {
        INSTANCES.clear();
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
    public SyncState syncDown(SyncDownTarget target, String soupName, SyncUpdateCallback callback) throws JSONException {
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
    public SyncState syncDown(SyncDownTarget target, SyncOptions options, String soupName, SyncUpdateCallback callback) throws JSONException {
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
        if (runningSyncIds.contains(syncId)) {
            throw new SmartSyncException("Cannot run reSync:" + syncId + ": still running");
        }

        SyncState sync = SyncState.byId(smartStore, syncId);
        if (sync == null) {
            throw new SmartSyncException("Cannot run reSync:" + syncId + ": no sync found");
        }
        if (sync.getType() != SyncState.Type.syncDown) {
            throw new SmartSyncException("Cannot run reSync:" + syncId + ": wrong type:" + sync.getType());
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
                    switch (sync.getType()) {
                        case syncDown:
                            syncDown(sync, callback);
                            break;
                        case syncUp:
                            syncUp(sync, callback);
                            break;
                    }
                    updateSync(sync, SyncState.Status.DONE, 100, callback);
                } catch (Exception e) {
                    Log.e("SmartSyncManager:runSync", "Error during sync: " + sync.getId(), e);
                    // Update status to failed
                    updateSync(sync, SyncState.Status.FAILED, UNCHANGED, callback);
                }
            }
        });
	}

    /**
     * Create and run a sync up
     * @param target
     * @param options
     * @param soupName
     * @param callback
     * @return
     * @throws JSONException
     */
    public SyncState syncUp(SyncUpTarget target, SyncOptions options, String soupName, SyncUpdateCallback callback) throws JSONException {
    	SyncState sync = SyncState.createSyncUp(smartStore, target, options, soupName);
    	runSync(sync, callback);
    	return sync;
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

            switch (status) {

                case NEW:
                    break;
                case RUNNING:
                    runningSyncIds.add(sync.getId());
                    break;
                case DONE:
                case FAILED:
                    runningSyncIds.remove(sync.getId());
                    break;
            }

	    	callback.onUpdate(sync);
    	}
    	catch (JSONException e) {
    		Log.e("SmartSyncManager:updateSync", "Unexpected json error for sync: " + sync.getId(), e);
    	}
    }
    
    private void syncUp(SyncState sync, SyncUpdateCallback callback) throws Exception {
		final String soupName = sync.getSoupName();
        final SyncUpTarget target = (SyncUpTarget) sync.getTarget();
		final SyncOptions options = sync.getOptions();
		final List<String> fieldlist = options.getFieldlist();
		final MergeMode mergeMode = options.getMergeMode();
        final Set<String> dirtyRecordIds = target.getIdsOfRecordsToSyncUp(this, soupName);
		int totalSize = dirtyRecordIds.size();
        sync.setTotalSize(totalSize);
        updateSync(sync, SyncState.Status.RUNNING, 0, callback);
        int i = 0;
        for (final String id : dirtyRecordIds) {
            JSONObject record = smartStore.retrieve(soupName, Long.valueOf(id)).getJSONObject(0);
            syncUpOneRecord(target, soupName, fieldlist, record, mergeMode);

            // Updating status
            int progress = (i + 1) * 100 / totalSize;
            if (progress < 100) {
                updateSync(sync, SyncState.Status.RUNNING, progress, callback);
            }

            // Incrementing i
            i++;
        }
	}

    private boolean isNewerThanServer(SyncUpTarget target, String objectType, String objectId, String lastModStr) throws JSONException, IOException {
        if (lastModStr == null) {
            // We didn't capture the last modified date so we can't really enforce merge mode, returning true so that we will behave like an "overwrite" merge mode
            return true;
        }

        try {
            String serverLastModStr = target.fetchLastModifiedDate(this, objectType, objectId);

            if (serverLastModStr == null) {
                // We were unable to get the last modified date from the server
                return true;
            }

            long lastModifiedDate = Constants.TIMESTAMP_FORMAT.parse(lastModStr).getTime();
            long serverLastModifiedDate = Constants.TIMESTAMP_FORMAT.parse(serverLastModStr).getTime();

            return (serverLastModifiedDate <= lastModifiedDate);
        } catch (Exception e) {
            Log.e("SmartSyncManager:isNewerThanServer", "Couldn't figure out last modified date", e);
            throw new SmartSyncException(e);
        }
    }

    private void syncUpOneRecord(SyncUpTarget target, String soupName, List<String> fieldlist,
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
            return;
        }

        // Getting type and id
        final String objectType = (String) SmartStore.project(record, Constants.SOBJECT_TYPE);
        final String objectId = record.getString(target.getIdFieldName());
        final String lastModStr = record.optString(target.getModificationDateFieldName());

        /*
         * Checks if we are attempting to update a record that has been updated
         * on the server AFTER the client's last sync down. If the merge mode
         * passed in tells us to leave the record alone under these
         * circumstances, we will do nothing and return here.
         */
        if (mergeMode == MergeMode.LEAVE_IF_CHANGED &&
        		(action == Action.update || action == Action.delete) &&
        		!isNewerThanServer(target, objectType, objectId, lastModStr)) {

        	// Nothing to do for this record
    		Log.i("SmartSyncManager:syncUpOneRecord",
    				"Record not synced since client does not have the latest from server");
        	return;
        }

        // Fields to save (in the case of create or update)
        Map<String, Object> fields = new HashMap<String, Object>();
        if (action == Action.create || action == Action.update) {
            for (String fieldName : fieldlist) {
                if (!fieldName.equals(target.getIdFieldName()) && !fieldName.equals(SyncUpTarget.MODIFICATION_DATE_FIELD_NAME)) {
                    fields.put(fieldName, SmartStore.project(record, fieldName));
                }
            }
        }

        // Create/update/delete record on server and update smartstore
        String recordServerId;
        int statusCode;
        switch (action) {
            case create:
                recordServerId = target.createOnServer(this, objectType, fields);
                if (recordServerId != null) {
                    record.put(target.getIdFieldName(), recordServerId);
                    cleanAndSaveRecord(soupName, record);
                }
                break;
            case delete:
                statusCode = target.deleteOnServer(this, objectType, objectId);
                if (RestResponse.isSuccess(statusCode) || statusCode == HttpStatus.SC_NOT_FOUND) {
                    smartStore.delete(soupName, record.getLong(SmartStore.SOUP_ENTRY_ID));
                }
                break;
            case update:
                statusCode = target.updateOnServer(this, objectType, objectId, fields);
                if (RestResponse.isSuccess(statusCode)) {
                    cleanAndSaveRecord(soupName, record);
                }
                // Handling remotely deleted records
                else if (statusCode == HttpStatus.SC_NOT_FOUND) {
                    if (mergeMode == MergeMode.OVERWRITE) {
                        recordServerId = target.createOnServer(this, objectType, fields);
                        if (recordServerId != null) {
                            record.put(target.getIdFieldName(), recordServerId);
                            cleanAndSaveRecord(soupName, record);
                        }
                    }
                    else {
                        // Leave local record alone
                    }
                }
                break;
        }
    }

    private void cleanAndSaveRecord(String soupName, JSONObject record) throws JSONException {
        record.put(LOCAL, false);
        record.put(LOCALLY_CREATED, false);
        record.put(LOCALLY_UPDATED, false);
        record.put(LOCALLY_DELETED, false);
        smartStore.update(soupName, record, record.getLong(SmartStore.SOUP_ENTRY_ID));
    }

    private void syncDown(SyncState sync, SyncUpdateCallback callback) throws Exception {
        String soupName = sync.getSoupName();
        SyncDownTarget target = (SyncDownTarget) sync.getTarget();
        MergeMode mergeMode = sync.getMergeMode();
        long maxTimeStamp = sync.getMaxTimeStamp();

        JSONArray records = target.startFetch(this, maxTimeStamp);
        int countSaved = 0;
        int totalSize = target.getTotalSize();
        sync.setTotalSize(totalSize);
        updateSync(sync, SyncState.Status.RUNNING, 0, callback);
        final String idField = sync.getTarget().getIdFieldName();
        while (records != null) {
            // Save to smartstore
            saveRecordsToSmartStore(soupName, records, mergeMode, idField);
            countSaved += records.length();
            maxTimeStamp = Math.max(maxTimeStamp, target.getLatestModificationTimeStamp(records));

            // Update sync status
            if (countSaved < totalSize)
                updateSync(sync, SyncState.Status.RUNNING, countSaved*100 / totalSize, callback);

            // Fetch next records if any
            records = target.continueFetch(this);
        }
        sync.setMaxTimeStamp(maxTimeStamp);
	}

    private Set<String> toSet(JSONArray jsonArray) throws JSONException {
        Set<String> set = new HashSet<String>();
        for (int i=0; i<jsonArray.length(); i++) {
            set.add(jsonArray.getJSONArray(i).getString(0));
        }
        return set;
    }
	
	private void saveRecordsToSmartStore(String soupName, JSONArray records, MergeMode mergeMode, String idField)
			throws JSONException {
        // Gather ids of dirty records
        Set<String> idsToSkip = null;
        if (mergeMode == MergeMode.LEAVE_IF_CHANGED) {
            idsToSkip = getDirtyRecordIds(soupName, idField);
        }

        synchronized(smartStore.getDatabase()) {
            try {
                smartStore.beginTransaction();
                for (int i = 0; i < records.length(); i++) {
                    JSONObject record = records.getJSONObject(i);

                    // Skip?
                    if (mergeMode == MergeMode.LEAVE_IF_CHANGED) {
                        String id = JSONObjectHelper.optString(record, idField);
                        if (id != null && idsToSkip.contains(id)) {
                            continue; // don't write over dirty record
                        }
                    }

                    // Save
                    record.put(LOCAL, false);
                    record.put(LOCALLY_CREATED, false);
                    record.put(LOCALLY_UPDATED, false);
                    record.put(LOCALLY_DELETED, false);
                    smartStore.upsert(soupName, records.getJSONObject(i), idField, false);
                }
                smartStore.setTransactionSuccessful();
            }
            finally {
                smartStore.endTransaction();
            }
        }
	}

    public Set<String> getDirtyRecordIds(String soupName, String idField) throws JSONException {
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
	public RestResponse sendSyncWithSmartSyncUserAgent(RestRequest restRequest) throws IOException {
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

        public SmartSyncException(Throwable e) {
            super(e);
        }

		private static final long serialVersionUID = 1L;
    }
    

    /**
     * Sets the rest client to be used.
     *
     * @param restClient
     */
    public void setRestClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * @return rest client in use
     */
    public RestClient getRestClient() {
        return this.restClient;
    }
    
    
	/**
	 * Callback to get sync status udpates
	 */
	public interface SyncUpdateCallback {
		void onUpdate(SyncState sync);
	}
}
