/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.analytics.EventBuilderHelper;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.rest.ApiVersionStrings;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartstore.app.SmartStoreSDKManager;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.store.SmartStore.SmartStoreException;
import com.salesforce.androidsdk.smartsync.app.SmartSyncSDKManager;
import com.salesforce.androidsdk.smartsync.target.AdvancedSyncUpTarget;
import com.salesforce.androidsdk.smartsync.target.SyncDownTarget;
import com.salesforce.androidsdk.smartsync.target.SyncUpTarget;
import com.salesforce.androidsdk.smartsync.util.SmartSyncLogger;
import com.salesforce.androidsdk.smartsync.util.SyncOptions;
import com.salesforce.androidsdk.smartsync.util.SyncState;
import com.salesforce.androidsdk.smartsync.util.SyncState.MergeMode;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sync Manager
 */
public class SyncManager {

    // Constants
    private static final int UNCHANGED = -1;
    private static final String TAG = "SyncManager";

    // For user agent
    private static final String SMART_SYNC = "SmartSync";

    private static final String FEATURE_SMART_SYNC = "SY";

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
        apiVersion = ApiVersionStrings.getVersionNumber(SalesforceSDKManager.getInstance().getAppContext());
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
            account = SmartStoreSDKManager.getInstance().getUserAccountManager().getCurrentUser();
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
        SalesforceSDKManager.getInstance().registerUsedAppFeature(FEATURE_SMART_SYNC);
        return instance;
    }

    /**
     * Resets all the sync managers
     */
    public static synchronized void reset() {
        for (SyncManager syncManager : INSTANCES.values()) {
            syncManager.threadPool.shutdownNow();
        }
        INSTANCES.clear();
    }

    /**
     * Resets the sync managers for this user account
     *
     * @param account User account.
     */
    public static synchronized void reset(UserAccount account) {
        Set<String> keysToRemove = new HashSet<>();
        for (String key : INSTANCES.keySet()) {
            if (key.startsWith(account.getUserId())) {
                keysToRemove.add(key);
                SyncManager syncManager = INSTANCES.get(key);
                syncManager.threadPool.shutdownNow();
            }
        }
        // NB: keySet returns a Set view of the keys contained in this map.
        // The set is backed by the map, so changes to the map are reflected in the set, and vice-versa.
        INSTANCES.keySet().removeAll(keysToRemove);
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
        SmartSyncLogger.d(TAG, "syncDown called", sync);
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
        SmartSyncLogger.d(TAG, "reSync called", sync);
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
                } catch (RestClient.RefreshTokenRevokedException re) {
                    SmartSyncLogger.e(TAG, "Exception thrown in runSync", re);
                    // Do not do anything - let the logout go through!
                } catch (Exception e) {
                    SmartSyncLogger.e(TAG, "Exception thrown in runSync", e);
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
        SmartSyncLogger.d(TAG, "syncUp called", sync);
        runSync(sync, callback);
    	return sync;
    }

    /**
     * Removes local copies of records that have been deleted on the server
     * or do not match the query results on the server anymore.
     *
     * @param syncId Sync ID.
     * @throws JSONException
     * @throws IOException
     */
    public void cleanResyncGhosts(long syncId) throws JSONException, IOException {
        if (runningSyncIds.contains(syncId)) {
            throw new SmartSyncException("Cannot run cleanResyncGhosts:" + syncId + ": still running");
        }
        final SyncState sync = SyncState.byId(smartStore, syncId);
        if (sync == null) {
            throw new SmartSyncException("Cannot run cleanResyncGhosts:" + syncId + ": no sync found");
        }
        if (sync.getType() != SyncState.Type.syncDown) {
            throw new SmartSyncException("Cannot run cleanResyncGhosts:" + syncId + ": wrong type:" + sync.getType());
        }
        SmartSyncLogger.d(TAG, "cleanResyncGhosts called", sync);
        final String soupName = sync.getSoupName();
        final SyncDownTarget target = (SyncDownTarget) sync.getTarget();

        // Ask target to clean up ghosts
        final int localIdSize = target.cleanGhosts(this, soupName);
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                final JSONObject attributes = new JSONObject();
                if (localIdSize > 0) {
                    try {
                        attributes.put("numRecords", localIdSize);
                        attributes.put("syncId", sync.getId());
                        attributes.put("syncTarget", target.getClass().getName());
                        EventBuilderHelper.createAndStoreEventSync("cleanResyncGhosts", null, TAG, attributes);
                    } catch (JSONException e) {
                        SmartSyncLogger.e(TAG, "Unexpected JSON error for cleanResyncGhosts sync tag: " + sync.getId(), e);
                    }
                }
            }
        });

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
    		if (progress != UNCHANGED) {
                sync.setProgress(progress);
            }
            switch (status) {
                case NEW:
                    break;
                case RUNNING:
                    runningSyncIds.add(sync.getId());
                    break;
                case DONE:
                case FAILED:
                    int totalSize = sync.getTotalSize();
                    final JSONObject attributes = new JSONObject();
                    try {
                        if (totalSize > 0) {
                            attributes.put("numRecords", totalSize);
                        }
                        attributes.put("syncId", sync.getId());
                        attributes.put("syncTarget", sync.getTarget().getClass().getName());
                        attributes.put(EventBuilderHelper.START_TIME, sync.getStartTime());
                        attributes.put(EventBuilderHelper.END_TIME, sync.getEndTime());
                    } catch (JSONException e) {
                        SmartSyncLogger.e(TAG, "Exception thrown while building attributes", e);
                    }
                    EventBuilderHelper.createAndStoreEvent(sync.getType().name(), null, TAG, attributes);
                    runningSyncIds.remove(sync.getId());
                    break;
            }
            sync.save(smartStore);
    	} catch (JSONException e) {
            SmartSyncLogger.e(TAG, "Unexpected JSON error for sync: " + sync.getId(), e);
    	} catch (SmartStoreException e) {
            SmartSyncLogger.e(TAG, "Unexpected smart store error for sync: " + sync.getId(), e);
        } finally {
            callback.onUpdate(sync);
        }
    }

    private void syncUp(SyncState sync, SyncUpdateCallback callback) throws Exception {
		final String soupName = sync.getSoupName();
        final SyncUpTarget target = (SyncUpTarget) sync.getTarget();
		final SyncOptions options = sync.getOptions();
        final Set<String> dirtyRecordIds = target.getIdsOfRecordsToSyncUp(this, soupName);
		int totalSize = dirtyRecordIds.size();
        sync.setTotalSize(totalSize);
        updateSync(sync, SyncState.Status.RUNNING, 0, callback);
        int i = 0;
        for (final String id : dirtyRecordIds) {
            JSONObject record = target.getFromLocalStore(this, soupName, id);
            syncUpOneRecord(target, soupName, record, options);

            // Updating status
            int progress = (i + 1) * 100 / totalSize;
            if (progress < 100) {
                updateSync(sync, SyncState.Status.RUNNING, progress, callback);
            }

            // Incrementing i
            i++;
        }
	}

    private void syncUpOneRecord(SyncUpTarget target, String soupName,
                                 JSONObject record, SyncOptions options) throws JSONException, IOException {
        SmartSyncLogger.d(TAG, "syncUpOneRecord called", record);

        /*
         * Checks if we are attempting to sync up a record that has been updated
         * on the server AFTER the client's last sync down. If the merge mode
         * passed in tells us to leave the record alone under these
         * circumstances, we will do nothing and return here.
         */
        final MergeMode mergeMode = options.getMergeMode();
        if (mergeMode == MergeMode.LEAVE_IF_CHANGED &&
                !target.isNewerThanServer(this, record)) {

            // Nothing to do for this record
            SmartSyncLogger.d(TAG, "syncUpOneRecord: Record not synched since client does not have the latest from server", record);
            return;
        }

        // Advanced sync up target take it from here
        if (target instanceof AdvancedSyncUpTarget) {
            ((AdvancedSyncUpTarget) target).syncUpRecord(this, record, options.getFieldlist(), options.getMergeMode());
            return;
        }

        // Do we need to do a create, update or delete
        boolean locallyDeleted = target.isLocallyDeleted(record);
        boolean locallyCreated = target.isLocallyCreated(record);
        boolean locallyUpdated = target.isLocallyUpdated(record);

        Action action = null;
        if (locallyDeleted)
            action = Action.delete;
        else if (locallyCreated)
            action = Action.create;
        else if (locallyUpdated)
            action = Action.update;

        if (action == null) {
            // Nothing to do for this record
            return;
        }

        // Create/update/delete record on server and update smartstore
        String recordServerId;
        int statusCode;
        switch (action) {
            case create:
                recordServerId = target.createOnServer(this, record, options.getFieldlist());
                if (recordServerId != null) {
                    record.put(target.getIdFieldName(), recordServerId);
                    target.cleanAndSaveInLocalStore(this, soupName, record);
                }
                break;
            case delete:
                statusCode = (locallyCreated
                        ? HttpURLConnection.HTTP_NOT_FOUND // if locally created it can't exist on the server - we don't need to actually do the deleteOnServer call
                        : target.deleteOnServer(this, record));
                if (RestResponse.isSuccess(statusCode) || statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    target.deleteFromLocalStore(this, soupName, record);
                }
                break;
            case update:
                statusCode = target.updateOnServer(this, record, options.getFieldlist());
                if (RestResponse.isSuccess(statusCode)) {
                    target.cleanAndSaveInLocalStore(this, soupName, record);
                }
                // Handling remotely deleted records
                else if (statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    if (mergeMode == MergeMode.OVERWRITE) {
                        recordServerId = target.createOnServer(this, record, options.getFieldlist());
                        if (recordServerId != null) {
                            record.put(target.getIdFieldName(), recordServerId);
                            target.cleanAndSaveInLocalStore(this, soupName, record);
                        }
                    }
                    else {
                        // Leave local record alone
                    }
                }
                break;
        }
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

        // Get ids of records to leave alone
        Set<String> idsToSkip = null;
        if (mergeMode == MergeMode.LEAVE_IF_CHANGED) {
            idsToSkip = target.getIdsToSkip(this, soupName);
        }

        while (records != null) {
            // Figure out records to save
            JSONArray recordsToSave = idsToSkip == null ? records : removeWithIds(records, idsToSkip, idField);

            // Save to smartstore.
            target.saveRecordsToLocalStore(this, soupName, recordsToSave);
            countSaved += records.length();
            maxTimeStamp = Math.max(maxTimeStamp, target.getLatestModificationTimeStamp(records));

            // Update sync status.
            if (countSaved < totalSize) {
                updateSync(sync, SyncState.Status.RUNNING, countSaved*100 / totalSize, callback);
            }

            // Fetch next records, if any.
            records = target.continueFetch(this);
        }
        sync.setMaxTimeStamp(maxTimeStamp);
	}

    private JSONArray removeWithIds(JSONArray records, Set<String> idsToSkip, String idField) throws JSONException {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.getJSONObject(i);

            // Keep ?
            String id = JSONObjectHelper.optString(record, idField);
            if (id == null || !idsToSkip.contains(id)) {
                arr.put(record);
            }
        }
        return arr;
    }

    /**
     * Send request after adding user-agent header that says SmartSync
	 * @param restRequest
	 * @return
	 * @throws IOException
	 */
	public RestResponse sendSyncWithSmartSyncUserAgent(RestRequest restRequest) throws IOException {
        SmartSyncLogger.d(TAG, "sendSyncWithSmartSyncUserAgent called with request: ", restRequest);
        RestResponse restResponse = restClient.sendSync(restRequest, new HttpAccess.UserAgentInterceptor(SalesforceSDKManager.getInstance().getUserAgent(SMART_SYNC)));
        if (restResponse.isSuccess()) {
            SmartSyncLogger.d(TAG, "sendSyncWithSmartSyncUserAgent received response: ", restResponse);
        } else {
            SmartSyncLogger.w(TAG, "sendSyncWithSmartSyncUserAgent received response: ", restResponse);
        }
        return restResponse;
    }

    /**
     * @return SmartStore used by this SyncManager
     */
    public SmartStore getSmartStore() {
        return smartStore;
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
