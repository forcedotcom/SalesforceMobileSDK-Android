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
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.mobilesync.app.Features;
import com.salesforce.androidsdk.mobilesync.app.SmartSyncSDKManager;
import com.salesforce.androidsdk.mobilesync.model.Layout;
import com.salesforce.androidsdk.mobilesync.target.LayoutSyncDownTarget;
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget;
import com.salesforce.androidsdk.mobilesync.util.Constants;
import com.salesforce.androidsdk.mobilesync.util.SmartSyncLogger;
import com.salesforce.androidsdk.mobilesync.util.SyncOptions;
import com.salesforce.androidsdk.mobilesync.util.SyncState;

import org.json.JSONArray;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Provides an easy way to fetch layout data using {@link com.salesforce.androidsdk.mobilesync.target.LayoutSyncDownTarget}.
 * This class handles creating a soup, storing synched data and reading it into
 * a meaningful data structure, i.e. {@link com.salesforce.androidsdk.mobilesync.model.Layout}.
 *
 * @author bhariharan
 */
public class LayoutSyncManager {

    static final String SOUP_NAME = "sfdcLayouts";
    static final String QUERY = "SELECT {" + SOUP_NAME + ":_soup} FROM {" + SOUP_NAME +
            "} WHERE {" + SOUP_NAME + ":" + Constants.ID + "} = '%s-%s'";
    private static final String TAG = "LayoutSyncManager";
    private static final IndexSpec[] INDEX_SPECS = new IndexSpec[] {
        new IndexSpec(Constants.ID, SmartStore.Type.json1)
    };

    private static Map<String, LayoutSyncManager> INSTANCES = new HashMap<>();

    private SmartStore smartStore;
    private SyncManager syncManager;

    /**
     * Returns the instance of this class associated with current user.
     *
     * @return Instance of this class.
     */
    public static synchronized LayoutSyncManager getInstance() {
        return getInstance(null, null);
    }

    /**
     * Returns the instance of this class associated with this user account.
     *
     * @param account User account.
     * @return Instance of this class.
     */
    public static synchronized LayoutSyncManager getInstance(UserAccount account) {
        return getInstance(account, null);
    }

    /**
     * Returns the instance of this class associated with this user and community.
     *
     * @param account User account.
     * @param communityId Community ID.
     * @return Instance of this class.
     */
    public static synchronized LayoutSyncManager getInstance(UserAccount account, String communityId) {
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
    public static synchronized LayoutSyncManager getInstance(UserAccount account, String communityId,
                                                             SmartStore smartStore) {
        if (account == null) {
            account = SmartSyncSDKManager.getInstance().getUserAccountManager().getCachedCurrentUser();
        }
        if (smartStore == null) {
            smartStore = SmartSyncSDKManager.getInstance().getSmartStore(account, communityId);
        }
        final SyncManager syncManager = SyncManager.getInstance(account, communityId, smartStore);
        final String uniqueId = (account != null ? account.getUserId() : "") + ":"
                + smartStore.getDatabase().getPath();
        LayoutSyncManager instance = INSTANCES.get(uniqueId);
        if (instance == null) {
            instance = new LayoutSyncManager(smartStore, syncManager);
            INSTANCES.put(uniqueId, instance);
        }
        SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_LAYOUT_SYNC);
        return instance;
    }

    /**
     * Resets all the layout sync managers.
     */
    public static synchronized void reset() {
        INSTANCES.clear();
    }

    /**
     * Resets the layout sync manager for this user account.
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
     * Returns the SmartStore instance used by this instance of LayoutSyncManager.
     *
     * @return SmartStore instance.
     */
    public SmartStore getSmartStore() {
        return smartStore;
    }

    /**
     * Returns the SyncManager instance used by this instance of LayoutSyncManager.
     *
     * @return SyncManager instance.
     */
    public SyncManager getSyncManager() {
        return syncManager;
    }

    /**
     * Fetches layout data for the specified object type and layout type using the specified
     * mode and triggers the supplied callback once complete.
     *
     * @param objectType Object type.
     * @param layoutType Layout type. Defaults to "Full" if null is passed in.
     * @param mode Fetch mode. See {@link com.salesforce.androidsdk.mobilesync.util.Constants.Mode} for available modes.
     * @param syncCallback Layout sync callback.
     */
    public void fetchLayout(String objectType, String layoutType, Constants.Mode mode,
                            LayoutSyncCallback syncCallback) {
        switch (mode) {
            case CACHE_ONLY:
                fetchFromCache(objectType, layoutType, syncCallback, false);
                break;
            case CACHE_FIRST:
                fetchFromCache(objectType, layoutType, syncCallback, true);
                break;
            case SERVER_FIRST:
                fetchFromServer(objectType, layoutType, syncCallback);
                break;
        }
    }

    private LayoutSyncManager(SmartStore smartStore, SyncManager syncManager) {
        this.smartStore = smartStore;
        this.syncManager = syncManager;
        initializeSoup();
    }

    private void fetchFromServer(final String objectType, final String layoutType,
                                 final LayoutSyncCallback syncCallback) {
        final SyncDownTarget target = new LayoutSyncDownTarget(objectType, layoutType);
        final SyncOptions options = SyncOptions.optionsForSyncDown(SyncState.MergeMode.OVERWRITE);
        try {
            syncManager.syncDown(target, options, SOUP_NAME, new SyncManager.SyncUpdateCallback() {

                @Override
                public void onUpdate(SyncState sync) {
                    if (SyncState.Status.DONE.equals(sync.getStatus())) {
                        fetchFromCache(objectType, layoutType, syncCallback, false);
                    }
                }
            });
        } catch (Exception e) {
            SmartSyncLogger.e(TAG, "Exception occurred while reading layout data from the server", e);
        }
    }

    private void fetchFromCache(String objectType, String layoutType,
                                LayoutSyncCallback syncCallback, boolean fallbackOnServer) {
        final QuerySpec querySpec = QuerySpec.buildSmartQuerySpec(String.format(QUERY,
                objectType, layoutType), 1);
        try {
            final JSONArray results = smartStore.query(querySpec, 0);
            if (results == null || results.length() == 0) {
                if (fallbackOnServer) {
                    fetchFromServer(objectType, layoutType, syncCallback);
                } else {
                    onSyncComplete(objectType, syncCallback, null);
                }
            } else {
                onSyncComplete(objectType, syncCallback,
                        Layout.fromJSON(results.optJSONArray(0).optJSONObject(0)));
            }
        } catch (Exception e) {
            SmartSyncLogger.e(TAG, "Exception occurred while reading layout data from the cache", e);
        }
    }

    private void onSyncComplete(String objectType, LayoutSyncCallback syncCallback, Layout layout) {
        if (syncCallback != null) {
            syncCallback.onSyncComplete(objectType, layout);
        }
    }

    private void initializeSoup() {
        if (!smartStore.hasSoup(SOUP_NAME)) {
            smartStore.registerSoup(SOUP_NAME, INDEX_SPECS);
        }
    }

    /**
     * Callback interface for layout sync.
     *
     * @author bhariharan
     */
    public interface LayoutSyncCallback {

        /**
         * Callback triggered when layout sync completes.
         *
         * @param objectType Object type.
         * @param layout Layout.
         */
        void onSyncComplete(String objectType, Layout layout);
    }
}
