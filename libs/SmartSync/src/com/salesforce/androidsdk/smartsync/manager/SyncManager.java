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
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.rest.ApiVersionStrings;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartsync.app.Features;
import com.salesforce.androidsdk.smartsync.app.SmartSyncSDKManager;
import com.salesforce.androidsdk.smartsync.target.AdvancedSyncUpTarget;
import com.salesforce.androidsdk.smartsync.target.SyncDownTarget;
import com.salesforce.androidsdk.smartsync.target.SyncUpTarget;
import com.salesforce.androidsdk.smartsync.util.SmartSyncLogger;
import com.salesforce.androidsdk.smartsync.util.SyncOptions;
import com.salesforce.androidsdk.smartsync.util.SyncState;
import com.salesforce.androidsdk.smartsync.util.SyncState.MergeMode;

import org.json.JSONException;

import java.io.IOException;
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
    private static final String TAG = "SyncManager";

    // For user agent
    private static final String SMART_SYNC = "SmartSync";

    // Static member
    private static Map<String, SyncManager> INSTANCES = new HashMap<String, SyncManager>();

    // Keeping track of active syncs (could be waiting for thread or running on a thread)
    private Map<Long, SyncTask> activeSyncs = new HashMap<>();

    // Flag set when stop is requested
    private boolean stopRequested = false;

    // Thread pool for running syncs
    private final ExecutorService threadPool = Executors.newFixedThreadPool(1);

    // Backing smartstore
    private SmartStore smartStore;

    // Rest client for network calls
    private RestClient restClient;

    // Api version
    public final String apiVersion;

    /**
     * Private constructor
     *
     * @param smartStore
     */
    private SyncManager(SmartStore smartStore, RestClient restClient) {
        apiVersion = ApiVersionStrings.getVersionNumber(SalesforceSDKManager.getInstance().getAppContext());
        this.smartStore = smartStore;
        this.restClient = restClient;
        SyncState.setupSyncsSoupIfNeeded(smartStore);
        SyncState.cleanupSyncsSoupIfNeeded(smartStore);
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
     * @param account     User account.
     * @param communityId Community ID.
     * @return Instance of this class.
     */
    public static synchronized SyncManager getInstance(UserAccount account, String communityId) {
        return getInstance(account, communityId, null);
    }

    /**
     * Returns the instance of this class associated with this user, community and smartstore.
     *
     * @param account     User account. Pass null to user current user.
     * @param communityId Community ID. Pass null if not applicable
     * @param smartStore  SmartStore instance. Pass null to use current user default smartstore.
     * @return Instance of this class.
     */
    public static synchronized SyncManager getInstance(UserAccount account, String communityId, SmartStore smartStore) {
        if (account == null) {
            account = SmartSyncSDKManager.getInstance().getUserAccountManager().getCachedCurrentUser();
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
        SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_SMART_SYNC);
        return instance;
    }

    /**
     * Resets all the sync managers
     */
    public static synchronized void reset() {
        for (SyncManager syncManager : INSTANCES.values()) {
            syncManager.stop();
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
        if (account != null) {
            Set<String> keysToRemove = new HashSet<>();
            for (String key : INSTANCES.keySet()) {
                if (key.startsWith(account.getUserId())) {
                    keysToRemove.add(key);
                    SyncManager syncManager = INSTANCES.get(key);
                    syncManager.stop();
                    syncManager.threadPool.shutdownNow();
                }
            }
            // NB: keySet returns a Set view of the keys contained in this map.
            // The set is backed by the map, so changes to the map are reflected in the set, and vice-versa.
            INSTANCES.keySet().removeAll(keysToRemove);
        }
    }

    /**
     * Stop the sync manager
     * It might take a while for active syncs to actually get stopped
     * Call isStopped() to see if syncManager is fully paused
     */
    public void stop() {
        stopRequested = true;
        for (SyncTask syncTask : activeSyncs.values()) {
            syncTask.stop();
        }
    }

    /**
     * @return true if stop was requested but there are still active syncs
     */
    public boolean isStopping() {
        return stopRequested && activeSyncs.size() > 0;
    }

    /**
     * @return true if stop was requested and there no syncs are active anymore
     */
    public boolean isStopped() {
        return stopRequested && activeSyncs.size() == 0;
    }

    /**
     * Check if stop was called
     * Throw a SyncManagerStoppedException if it was
     */
    public void checkIfStopRequested() {
        if(this.stopRequested) {
            throw new SyncManagerStoppedException("sync manager stopped");
        }
    }

    /**
     * Resume this sync manager
     * Restart all stopped syncs if restartStoppedSyncs is true
     *
     * @param restartStoppedSyncs
     * @param callback
     * @throws JSONException
     */
    public void resume(boolean restartStoppedSyncs, SyncUpdateCallback callback) throws JSONException {
        stopRequested = false;
        if (restartStoppedSyncs) {
            List<SyncState> stoppedSyncs = SyncState.getSyncsWithStatus(this.smartStore, SyncState.Status.STOPPED);
            for (SyncState sync : stoppedSyncs) {
                SmartSyncLogger.d(TAG, "resuming", sync);
                sync.setTotalSize(-1); // should we not do that?
                runSync(sync, callback);
            }
        }
    }

    /**
     * Add to active syncs map
     * @param syncTask
     */
    synchronized void addToActiveSyncs(SyncTask syncTask) {
        activeSyncs.put(syncTask.getSyncId(), syncTask);
    }

    /**
     * Remove from active syncs map
     * @param syncTask
     */
    synchronized void removeFromActiveSyncs(SyncTask syncTask) {
        activeSyncs.remove(syncTask.getSyncId());
    }

    /**
     * Get details of a sync by id
     *
     * @param syncId
     * @return
     * @throws JSONException
     */
    public SyncState getSyncStatus(long syncId) throws JSONException {
        return SyncState.byId(smartStore, syncId);
    }

    /**
     * Get details of a sync by name
     *
     * @param name
     * @return
     * @throws JSONException
     */
    public SyncState getSyncStatus(String name) throws JSONException {
        return SyncState.byName(smartStore, name);
    }

    /**
     * Return true if there is a sync with the given name
     *
     * @param name
     * @return
     */
    public boolean hasSyncWithName(String name) {
        return SyncState.hasSyncWithName(smartStore, name);
    }

    /**
     * Delete sync by id
     *
     * @param syncId
     * @return
     */
    public void deleteSync(long syncId) {
        SyncState.deleteSync(smartStore, syncId);
    }

    /**
     * Delete sync by name
     *
     * @param name
     * @return
     */
    public void deleteSync(String name) {
        SyncState.deleteSync(smartStore, name);
    }

    /**
     * Create and run a sync down that will overwrite any modified records
     *
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
     * Create and run a sync down without a name
     *
     * @param target
     * @param options
     * @param soupName
     * @param callback
     * @return
     * @throws JSONException
     */
    public SyncState syncDown(SyncDownTarget target, SyncOptions options, String soupName, SyncUpdateCallback callback) throws JSONException {
        return syncDown(target, options, soupName, null, callback);
    }

    /**
     * Create and run a sync down
     *
     * @param target
     * @param options
     * @param soupName
     * @param syncName
     * @param callback
     * @return
     * @throws JSONException
     */
    public SyncState syncDown(SyncDownTarget target, SyncOptions options, String soupName, String syncName, SyncUpdateCallback callback) throws JSONException {
        SyncState sync = createSyncDown(target, options, soupName, syncName);
        SmartSyncLogger.d(TAG, "syncDown called", sync);
        runSync(sync, callback);
        return sync;
    }

    /**
     * Create a sync down
     *
     * @param target
     * @param options
     * @param soupName
     * @param syncName
     * @return
     * @throws JSONException
     */
    public SyncState createSyncDown(SyncDownTarget target, SyncOptions options, String soupName, String syncName) throws JSONException {
        return SyncState.createSyncDown(smartStore, target, options, soupName, syncName);
    }

    /**
     * Re-run sync but only fetch new/modified records
     *
     * @param syncId
     * @param callback
     * @throws JSONException
     */
    public SyncState reSync(long syncId, SyncUpdateCallback callback) throws JSONException {
        SyncState sync = SyncState.byId(smartStore, syncId);
        if (sync == null) {
            throw new SmartSyncException("Cannot run reSync:" + syncId + ": no sync found");
        }
        sync.setTotalSize(-1);
        SmartSyncLogger.d(TAG, "reSync called", sync);
        runSync(sync, callback);
        return sync;
    }

    /**
     * Re-run sync but only fetch new/modified records
     *
     * @param syncName
     * @param callback
     * @throws JSONException
     */
    public SyncState reSync(String syncName, SyncUpdateCallback callback) throws JSONException {
        SyncState sync = getSyncStatus(syncName);
        if (sync == null) {
            throw new SmartSyncException("Cannot run reSync:" + syncName + ": no sync found");
        }
        return reSync(sync.getId(), callback);
    }

    /**
     * Run a sync
     *
     * @param sync
     * @param callback
     */
    public void runSync(final SyncState sync, final SyncUpdateCallback callback) {
        validateNotRunning("runSync", sync.getId());
        validateNotStopping("runSync");

        SyncTask syncTask = null;
        switch(sync.getType()) {
            case syncDown:
                syncTask = new SyncDownTask(this, sync, callback);
                break;
            case syncUp:
                if (sync.getTarget() instanceof AdvancedSyncUpTarget) {
                    syncTask = new AdvancedSyncUpTask(this, sync, callback);
                } else {
                    syncTask = new SyncUpTask(this, sync, callback);
                }
                break;
        }

        threadPool.execute(syncTask);
    }

    /**
     * Create and run a sync up without a name
     *
     * @param target
     * @param options
     * @param soupName
     * @param callback
     * @return
     * @throws JSONException
     */
    public SyncState syncUp(SyncUpTarget target, SyncOptions options, String soupName, SyncUpdateCallback callback) throws JSONException {
        return syncUp(target, options, soupName, null, callback);
    }

    /**
     * Create and run a sync up
     *
     * @param target
     * @param options
     * @param soupName
     * @param callback
     * @return
     * @throws JSONException
     */
    public SyncState syncUp(SyncUpTarget target, SyncOptions options, String soupName, String syncName, SyncUpdateCallback callback) throws JSONException {
        SyncState sync = createSyncUp(target, options, soupName, syncName);
        SmartSyncLogger.d(TAG, "syncUp called", sync);
        runSync(sync, callback);
        return sync;
    }

    /**
     * Create a sync up
     *
     * @param target
     * @param options
     * @param soupName
     * @param syncName
     * @return
     * @throws JSONException
     */
    public SyncState createSyncUp(SyncUpTarget target, SyncOptions options, String soupName, String syncName) throws JSONException {
        return SyncState.createSyncUp(smartStore, target, options, soupName, syncName);
    }

    /**
     * Removes local copies of records that have been deleted on the server
     * or do not match the query results on the server anymore.
     *
     * @param syncId Sync ID.
     * @throws JSONException
     * @throws IOException
     */
    public void cleanResyncGhosts(final long syncId) throws JSONException, IOException {
        cleanResyncGhosts(syncId, null);
    }


    /**
     * Removes local copies of records that have been deleted on the server
     * or do not match the query results on the server anymore.
     *
     * @param syncId
     * @param callback Callback to get clean resync ghosts completion status.
     * @throws JSONException
     * @throws IOException
     */
    public void cleanResyncGhosts(final long syncId, final CleanResyncGhostsCallback callback) throws JSONException {
        validateNotRunning("cleanResyncGhosts", syncId);
        validateNotStopping("cleanResyncGhosts");

        final SyncState sync = SyncState.byId(smartStore, syncId);
        if (sync == null) {
            throw new SmartSyncException("Cannot run cleanResyncGhosts:" + syncId + ": no sync found");
        }
        if (sync.getType() != SyncState.Type.syncDown) {
            throw new SmartSyncException("Cannot run cleanResyncGhosts:" + syncId + ": wrong type:" + sync.getType());
        }

        // Ask target to clean up ghosts
        SmartSyncLogger.d(TAG, "cleanResyncGhosts called", sync);
        threadPool.execute(new CleanSyncGhostsTask(this, sync, callback));

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
        return restResponse;
    }

    /**
     * @return SmartStore used by this SyncManager
     */
    public SmartStore getSmartStore() {
        return smartStore;
    }

    /**
     * Throw exception if syncId is already running
     * @param operation
     * @param syncId
     */
    private void validateNotRunning(String operation, long syncId) {
        if (activeSyncs.containsKey(syncId)) {
            throw new SmartSyncException("Cannot run " + operation + " " + syncId + " - sync is still running");
        }
    }

    /**
     * Throw exception if sync manager is stopping or stopped
     * @param operation
     */
    private void validateNotStopping(String operation) {
        if (stopRequested) {
            throw new SmartSyncException("Cannot run " + operation + " - sync manager is stopping or stopped");
        }
    }

    /**
     * Exception thrown by smart sync manager
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
     * Exception thrown when sync manager is stopped
     */
    public static class SyncManagerStoppedException extends SmartSyncException {

        public SyncManagerStoppedException(String message) {
            super(message);
        }

        public SyncManagerStoppedException(Throwable e) {
            super(e);
        }
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

    /**
     * Callback to get clean resync ghosts completion status
     */
    public interface CleanResyncGhostsCallback {
        /**
         * Called when clean resync ghosts completes successfully
         * @param numRecords Number of local ghosts found (and removed)
         */
        void onSuccess(int numRecords);

        /**
         * Called when clean resync ghosts fails with an error
         * @param e Error
         */
        void onError(Exception e);
    }
}
