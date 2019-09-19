/*
 * Copyright (c) 2018-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.mobilesync.manager;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.mobilesync.app.Features;
import com.salesforce.androidsdk.mobilesync.model.Metadata;
import com.salesforce.androidsdk.mobilesync.target.MetadataSyncDownTarget;
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget;
import com.salesforce.androidsdk.mobilesync.util.Constants;
import com.salesforce.androidsdk.mobilesync.util.MobileSyncLogger;
import com.salesforce.androidsdk.mobilesync.util.SyncOptions;
import com.salesforce.androidsdk.mobilesync.util.SyncState;

import org.json.JSONArray;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Provides an easy way to fetch metadata using {@link com.salesforce.androidsdk.mobilesync.target.MetadataSyncDownTarget}.
 * This class handles creating a soup, storing synched data and reading it into
 * a meaningful data structure, i.e. {@link com.salesforce.androidsdk.mobilesync.model.Metadata}.
 *
 * @author bhariharan
 */
public class MetadataSyncManager {

    static final String SOUP_NAME = "sfdcMetadata";
    static final String QUERY = "SELECT {" + SOUP_NAME + ":_soup} FROM {" + SOUP_NAME +
            "} WHERE {" + SOUP_NAME + ":" + Constants.ID + "} = '%s'";
    private static final String TAG = "MetadataSyncManager";
    private static final IndexSpec[] INDEX_SPECS = new IndexSpec[] {
            new IndexSpec(Constants.ID, SmartStore.Type.json1)
    };

    private static Map<String, MetadataSyncManager> INSTANCES = new HashMap<>();

    private SmartStore smartStore;
    private SyncManager syncManager;

    /**
     * Returns the instance of this class associated with current user.
     *
     * @return Instance of this class.
     */
    public static synchronized MetadataSyncManager getInstance() {
        return getInstance(null, null);
    }

    /**
     * Returns the instance of this class associated with this user account.
     *
     * @param account User account.
     * @return Instance of this class.
     */
    public static synchronized MetadataSyncManager getInstance(UserAccount account) {
        return getInstance(account, null);
    }

    /**
     * Returns the instance of this class associated with this user and community.
     *
     * @param account User account.
     * @param communityId Community ID.
     * @return Instance of this class.
     */
    public static synchronized MetadataSyncManager getInstance(UserAccount account, String communityId) {
        return getInstance(account, communityId, null);
    }

    /**
     * Returns the instance of this class associated with this user, community and SmartStore.
     *
     * @param account User account. Pass null to use current user.
     * @param communityId Community ID. Pass null if not applicable.
     * @param smartStore SmartStore instance. Pass null to use current user default SmartStore.
     * @return Instance of this class.
     */
    public static synchronized MetadataSyncManager getInstance(UserAccount account, String communityId,
                                                             SmartStore smartStore) {
        if (account == null) {
            account = MobileSyncSDKManager.getInstance().getUserAccountManager().getCachedCurrentUser();
        }
        if (smartStore == null) {
            smartStore = MobileSyncSDKManager.getInstance().getSmartStore(account, communityId);
        }
        final SyncManager syncManager = SyncManager.getInstance(account, communityId, smartStore);
        final String uniqueId = (account != null ? account.getUserId() : "") + ":"
                + smartStore.getDatabase().getPath();
        MetadataSyncManager instance = INSTANCES.get(uniqueId);
        if (instance == null) {
            instance = new MetadataSyncManager(smartStore, syncManager);
            INSTANCES.put(uniqueId, instance);
        }
        SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_METADATA_SYNC);
        return instance;
    }

    /**
     * Resets all the metadata sync managers.
     */
    public static synchronized void reset() {
        INSTANCES.clear();
    }

    /**
     * Resets the metadata sync manager for this user account.
     *
     * @param account User account.
     */
    public static synchronized void reset(UserAccount account) {
        if (account != null) {
            final Set<String> keysToRemove = new HashSet<>();
            for (String key : INSTANCES.keySet()) {
                if (key.startsWith(account.getUserId())) {
                    keysToRemove.add(key);
                }
            }
            INSTANCES.keySet().removeAll(keysToRemove);
        }
    }

    /**
     * Returns the SmartStore instance used by this instance of MetadataSyncManager.
     *
     * @return SmartStore instance.
     */
    public SmartStore getSmartStore() {
        return smartStore;
    }

    /**
     * Returns the SyncManager instance used by this instance of MetadataSyncManager.
     *
     * @return SyncManager instance.
     */
    public SyncManager getSyncManager() {
        return syncManager;
    }

    /**
     * Fetches metadata for the specified object type using the specified
     * mode and triggers the supplied callback once complete.
     *
     * @param objectType Object type.
     * @param mode Fetch mode. See {@link com.salesforce.androidsdk.mobilesync.util.Constants.Mode} for available modes.
     * @param syncCallback Metadata sync callback.
     */
    public void fetchMetadata(String objectType, Constants.Mode mode,
                            MetadataSyncCallback syncCallback) {
        switch (mode) {
            case CACHE_ONLY:
                fetchFromCache(objectType, syncCallback, false);
                break;
            case CACHE_FIRST:
                fetchFromCache(objectType, syncCallback, true);
                break;
            case SERVER_FIRST:
                fetchFromServer(objectType, syncCallback);
                break;
        }
    }

    private MetadataSyncManager(SmartStore smartStore, SyncManager syncManager) {
        this.smartStore = smartStore;
        this.syncManager = syncManager;
        initializeSoup();
    }

    private void fetchFromServer(final String objectType, final MetadataSyncCallback syncCallback) {
        final SyncDownTarget target = new MetadataSyncDownTarget(objectType);
        final SyncOptions options = SyncOptions.optionsForSyncDown(SyncState.MergeMode.OVERWRITE);
        try {
            syncManager.syncDown(target, options, SOUP_NAME, new SyncManager.SyncUpdateCallback() {

                @Override
                public void onUpdate(SyncState sync) {
                    if (SyncState.Status.DONE.equals(sync.getStatus())) {
                        fetchFromCache(objectType, syncCallback, false);
                    }
                }
            });
        } catch (Exception e) {
            MobileSyncLogger.e(TAG, "Exception occurred while reading metadata from the server", e);
        }
    }

    private void fetchFromCache(String objectType, MetadataSyncCallback syncCallback,
                                boolean fallbackOnServer) {
        final QuerySpec querySpec = QuerySpec.buildSmartQuerySpec(String.format(QUERY,
                objectType), 1);
        try {
            final JSONArray results = smartStore.query(querySpec, 0);
            if (results == null || results.length() == 0) {
                if (fallbackOnServer) {
                    fetchFromServer(objectType, syncCallback);
                } else {
                    onSyncComplete(syncCallback, null);
                }
            } else {
                onSyncComplete(syncCallback, Metadata.fromJSON(results.optJSONArray(0).optJSONObject(0)));
            }
        } catch (Exception e) {
            MobileSyncLogger.e(TAG, "Exception occurred while reading metadata from the cache", e);
        }
    }

    private void onSyncComplete(MetadataSyncCallback syncCallback, Metadata metadata) {
        if (syncCallback != null) {
            syncCallback.onSyncComplete(metadata);
        }
    }

    private void initializeSoup() {
        if (!smartStore.hasSoup(SOUP_NAME)) {
            smartStore.registerSoup(SOUP_NAME, INDEX_SPECS);
        }
    }

    /**
     * Callback interface for metadata sync.
     *
     * @author bhariharan
     */
    public interface MetadataSyncCallback {

        /**
         * Callback triggered when metadata sync completes.
         *
         * @param metadata Metadata.
         */
        void onSyncComplete(Metadata metadata);
    }
}
