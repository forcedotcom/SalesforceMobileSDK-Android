/*
 * Copyright (c) 2017-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.mobilesync.config;

import android.content.Context;

import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.mobilesync.manager.SyncManager;
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget;
import com.salesforce.androidsdk.mobilesync.target.SyncUpTarget;
import com.salesforce.androidsdk.mobilesync.util.SmartSyncLogger;
import com.salesforce.androidsdk.mobilesync.util.SyncOptions;
import com.salesforce.androidsdk.mobilesync.util.SyncState;
import com.salesforce.androidsdk.util.ResourceReaderHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static com.salesforce.androidsdk.smartstore.config.StoreConfig.SOUP_NAME;

/**
 * Class encapsulating syncs definition.
 *
 * Config expected in a resource or assets file in JSON with the following:
 * {
 *     syncs: [
 *          {
 *              syncType: syncUp | syncDown
 *              syncName: xxx
 *              soupName: yyy
 *              target: { depends on target - see SyncTarget.java  }
 *              options: { also depends on target - see SyncOptions.java }
 *          }
 *     ]
 * }
 */

public class SyncsConfig {

    private static final String TAG = "SyncsConfig";

    public static final String SYNCS = "syncs";
    public static final String TARGET = "target";
    public static final String OPTIONS = "options";
    public static final String SYNC_NAME = "syncName";
    public static final String SYNC_TYPE = "syncType";

    private JSONArray syncConfigs;

    /**
     * Constructor for config stored in resource file
     * @param ctx Context.
     * @param resourceId Id of resource file.
     */
    public SyncsConfig(Context ctx, int resourceId) {
        this(ResourceReaderHelper.readResourceFile(ctx, resourceId));
    }

    /**
     * Constructor for config stored in asset file
     * @param ctx Context.
     * @param assetPath Path of assets file.
     */
    public SyncsConfig(Context ctx, String assetPath) {
        this(ResourceReaderHelper.readAssetFile(ctx, assetPath));
    }

    private SyncsConfig(String str) {
        try {
            if (str == null) {
                syncConfigs = null;
            } else {
                JSONObject config = new JSONObject(str);
                syncConfigs = config.getJSONArray(SYNCS);
            }
        } catch (JSONException e) {
            SmartSyncLogger.e(TAG, "Unhandled exception parsing json", e);
        }
    }

    /**
     * Return true if syncs are defined in config
     * @return
     */
    public boolean hasSyncs() {
        return syncConfigs != null && syncConfigs.length() > 0;
    }

    /**
     * Create the syncs from the config in the given store
     * NB: only feedback is through the logs - the config is static so getting it right is something the developer should do while writing the app
     * @param store
     */
    public void createSyncs(SmartStore store) {
        if (syncConfigs == null) {
            SmartSyncLogger.d(TAG, "No syncs config available");
            return;
        }
        SyncManager syncManager = SyncManager.getInstance(null, null, store);
        for (int i = 0; i< syncConfigs.length(); i++) {
            try {
                JSONObject syncConfig = syncConfigs.getJSONObject(i);
                String syncName = syncConfig.getString(SYNC_NAME);

                // Leaving sync alone if it already exists
                if (syncManager.hasSyncWithName(syncName)) {
                    SmartSyncLogger.d(TAG, "Sync already exists:" + syncName + " - skipping");
                    continue;
                }
                SyncState.Type syncType = SyncState.Type.valueOf(syncConfig.getString(SYNC_TYPE));
                SyncOptions options = SyncOptions.fromJSON(syncConfig.getJSONObject(OPTIONS));
                String soupName = syncConfig.getString(SOUP_NAME);
                SmartSyncLogger.d(TAG, "Creating sync:" + syncName);
                switch (syncType) {
                    case syncDown:
                        syncManager.createSyncDown(SyncDownTarget.fromJSON(syncConfig.getJSONObject(TARGET)), options, soupName, syncName);
                        break;
                    case syncUp:
                        syncManager.createSyncUp(SyncUpTarget.fromJSON(syncConfig.getJSONObject(TARGET)), options, soupName, syncName);
                        break;
                }
            } catch (JSONException e) {
                SmartSyncLogger.e(TAG, "Unhandled exception parsing json", e);
            }
        }
    }
}
