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
package com.salesforce.androidsdk.smartsync.manager;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.smartstore.app.SmartStoreSDKManager;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartsync.app.SmartSyncSDKManager;
import com.salesforce.androidsdk.smartsync.model.Layout;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Provides an easy way to fetch layout data using {@link com.salesforce.androidsdk.smartsync.target.LayoutSyncDownTarget}.
 * This class handles creating a soup, storing synched data and reading it into
 * a meaningful data structure, i.e. {@link com.salesforce.androidsdk.smartsync.model.Layout}.
 *
 * @author bhariharan
 */
public class LayoutSyncManager {

    private static final String FEATURE_LAYOUT_SYNC = "LY";

    private static Map<String, LayoutSyncManager> INSTANCES = new HashMap<>();

    private SmartStore smartStore;
    private RestClient restClient;

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
            account = SmartStoreSDKManager.getInstance().getUserAccountManager().getCurrentUser();
        }
        if (smartStore == null) {
            smartStore = SmartSyncSDKManager.getInstance().getSmartStore(account, communityId);
        }
        final String uniqueId = (account != null ? account.getUserId() : "") + ":"
                + smartStore.getDatabase().getPath();
        LayoutSyncManager instance = INSTANCES.get(uniqueId);
        if (instance == null) {
            RestClient restClient;

            /*
             * If account is still null, there is no user logged in, which means the default
             * RestClient should be set to the unauthenticated RestClient instance.
             */
            if (account == null) {
                restClient = SalesforceSDKManager.getInstance().getClientManager().peekUnauthenticatedRestClient();
            } else {
                restClient = SalesforceSDKManager.getInstance().getClientManager().peekRestClient(account);
            }
            instance = new LayoutSyncManager(smartStore, restClient);
            INSTANCES.put(uniqueId, instance);
        }
        SalesforceSDKManager.getInstance().registerUsedAppFeature(FEATURE_LAYOUT_SYNC);
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
     * Returns the RestClient instance used by this instance of LayoutSyncManager.
     *
     * @return RestClient instance.
     */
    public RestClient getRestClient() {
        return restClient;
    }

    /**
     * Fetches layout data using the specified mode and triggers the supplied callback once complete.
     *
     * @param mode Fetch mode. See {@link Mode} for available modes.
     * @param syncCallback Layout sync callback.
     */
    public void fetchLayout(Mode mode, LayoutSyncCallback syncCallback) {

    }

    private LayoutSyncManager(SmartStore smartStore, RestClient restClient) {
        this.smartStore = smartStore;
        this.restClient = restClient;
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

    /**
     * Enum for available data fetch modes.
     *
     * CACHE_ONLY - Fetches data from the cache and returns null if no data is available.
     * CACHE_FIRST - Fetches data from the cache and falls back on the server if no data is available.
     * SERVER_FIRST - Fetches data from the server and falls back on the cache if the server doesn't
     * return data. The data fetched from the server is automatically cached.
     */
    public enum Mode {
        CACHE_ONLY,
        CACHE_FIRST,
        SERVER_FIRST
    }
}
