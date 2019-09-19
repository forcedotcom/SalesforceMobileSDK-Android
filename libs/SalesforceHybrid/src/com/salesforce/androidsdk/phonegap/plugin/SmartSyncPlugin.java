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
package com.salesforce.androidsdk.phonegap.plugin;

import com.salesforce.androidsdk.phonegap.util.SalesforceHybridLogger;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.mobilesync.manager.SyncManager;
import com.salesforce.androidsdk.mobilesync.manager.SyncManager.SyncUpdateCallback;
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget;
import com.salesforce.androidsdk.mobilesync.target.SyncUpTarget;
import com.salesforce.androidsdk.mobilesync.util.SyncOptions;
import com.salesforce.androidsdk.mobilesync.util.SyncState;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static com.salesforce.androidsdk.phonegap.plugin.PluginConstants.IS_GLOBAL_STORE;
import static com.salesforce.androidsdk.phonegap.plugin.PluginConstants.OPTIONS;
import static com.salesforce.androidsdk.phonegap.plugin.PluginConstants.SOUP_NAME;
import static com.salesforce.androidsdk.phonegap.plugin.PluginConstants.STORE_NAME;
import static com.salesforce.androidsdk.phonegap.plugin.PluginConstants.SYNC_NAME;
import static com.salesforce.androidsdk.phonegap.plugin.PluginConstants.TARGET;

/**
 * PhoneGap plugin for smart sync.
 */
public class SmartSyncPlugin extends ForcePlugin {

    // Keys in json from/to javascript
    private static final String SYNC_ID = "syncId";
    private static final String TAG = "SmartSyncPlugin";

    // Event
    private static final String SYNC_EVENT_TYPE = "sync";
    private static final String DETAIL = "detail";

    /**
     * Supported plugin actions that the client can take.
     */
    enum Action {
        syncUp,
        syncDown,
        getSyncStatus,
        reSync,
        cleanResyncGhosts,
        deleteSync
    }
    
    @Override
    public boolean execute(String actionStr, JavaScriptPluginVersion jsVersion, final JSONArray args,
                           final CallbackContext callbackContext) throws JSONException {
        final long start = System.currentTimeMillis();

        // Figure out action.
        final Action action;
        try {
            action = Action.valueOf(actionStr);
        } catch (IllegalArgumentException e) {
            SalesforceHybridLogger.e(TAG, "Unknown action " + actionStr, e);
            return false;
        }

        // Not running smartstore action on the main thread.
        cordova.getThreadPool().execute(new Runnable() {

            @Override
            public void run() {

                // All smart store actions need to be serialized.
                synchronized(SmartSyncPlugin.class) {
                    try {
                        switch(action) {
                          case syncUp:
                              syncUp(args, callbackContext);
                              break;
                          case syncDown:
                              syncDown(args, callbackContext);
                              break;
                          case getSyncStatus:
                              getSyncStatus(args, callbackContext);
                              break;
                          case reSync:
                              reSync(args, callbackContext);
                              break;
                          case cleanResyncGhosts:
                              cleanResyncGhosts(args, callbackContext);
                              break;
                          case deleteSync:
                              deleteSync(args, callbackContext);
                              break;
                          default:
                              throw new RuntimeException("No handler for action " + action);
                        }
                    } catch (Exception e) {
                        SalesforceHybridLogger.e(TAG, "Exception thrown", e);
                        callbackContext.error(e.getMessage());
                    }
                    SalesforceHybridLogger.d(TAG, "Total time for " + action + " -> " + (System.currentTimeMillis() - start));
                }
            }
        });
        SalesforceHybridLogger.d(TAG, "Main thread time for " + action + " -> " + (System.currentTimeMillis() - start));
        return true;
    }

    /**
     * Native implementation of syncUp.
     *
     * @param args
     * @param callbackContext
     * @throws JSONException 
     */
    private void syncUp(JSONArray args, CallbackContext callbackContext) throws Exception {

        // Parse args.
        JSONObject arg0 = args.getJSONObject(0);
        JSONObject target = arg0.getJSONObject(TARGET);
        String soupName = arg0.getString(SOUP_NAME);
        JSONObject options = arg0.optJSONObject(OPTIONS);
        String syncName = JSONObjectHelper.optString(arg0, SYNC_NAME);
        final boolean isGlobal = SmartStorePlugin.getIsGlobal(arg0);
        final String storeName = SmartStorePlugin.getStoreName(arg0);
        SyncManager syncManager = getSyncManager(arg0);
        SyncState sync = syncManager.syncUp(SyncUpTarget.fromJSON(target), SyncOptions.fromJSON(options), soupName, syncName, new SyncUpdateCallback() {

            @Override
            public void onUpdate(SyncState sync) {
                handleSyncUpdate(sync, isGlobal,storeName);
            }
        });
        callbackContext.success(sync.asJSON());
    }

    /**
     * Native implementation of syncDown.
     *
     * @param args
     * @param callbackContext
     * @throws JSONException 
     */
    private void syncDown(JSONArray args, CallbackContext callbackContext) throws Exception {

        // Parse args.
        JSONObject arg0 = args.getJSONObject(0);
        JSONObject target = arg0.getJSONObject(TARGET);
        String soupName = arg0.getString(SOUP_NAME);
        JSONObject options = arg0.getJSONObject(OPTIONS);
        String syncName = JSONObjectHelper.optString(arg0, SYNC_NAME);
        final boolean isGlobal = SmartStorePlugin.getIsGlobal(arg0);
        final String storeName = SmartStorePlugin.getStoreName(arg0);
        SyncManager syncManager = getSyncManager(arg0);
        SyncState sync = syncManager.syncDown(SyncDownTarget.fromJSON(target), SyncOptions.fromJSON(options), soupName, syncName, new SyncUpdateCallback() {

            @Override
            public void onUpdate(SyncState sync) {
                handleSyncUpdate(sync, isGlobal,storeName);
            }
        });
        callbackContext.success(sync.asJSON());
    }
    
    /**
     * Native implementation of getSyncStatus.
     *
     * @param args
     * @param callbackContext
     * @throws JSONException 
     */ 
    private void getSyncStatus(JSONArray args, CallbackContext callbackContext) throws Exception {

        // Parse args.
        JSONObject arg0 = args.getJSONObject(0);
        SyncManager syncManager = getSyncManager(arg0);

        SyncState sync;
        if (arg0.has(SYNC_ID) && !arg0.isNull(SYNC_ID)) {
            sync = syncManager.getSyncStatus(arg0.getLong(SYNC_ID));
        }
        else if (arg0.has(SYNC_NAME) && !arg0.isNull(SYNC_NAME)) {
            sync = syncManager.getSyncStatus(arg0.getString(SYNC_NAME));
        }
        else {
            throw new SyncManager.SmartSyncException("neither " + SYNC_ID + " nor " + SYNC_NAME + " were specified");
        }

        // cordova can't return null, so returning {} when sync is not found
        // cordova.force.js turns it back into a null
        callbackContext.success(sync == null ? new JSONObject() : sync.asJSON());
    }

    /**
     * Native implementation of deleteSync.
     *
     * @param args
     * @param callbackContext
     * @throws JSONException
     */
    private void deleteSync(JSONArray args, CallbackContext callbackContext) throws Exception {

        // Parse args.
        JSONObject arg0 = args.getJSONObject(0);
        SyncManager syncManager = getSyncManager(arg0);

        if (arg0.has(SYNC_ID) && !arg0.isNull(SYNC_ID)) {
            syncManager.deleteSync(arg0.getLong(SYNC_ID));
        }
        else if (arg0.has(SYNC_NAME) && !arg0.isNull(SYNC_NAME)) {
            syncManager.deleteSync(arg0.getString(SYNC_NAME));
        }
        else {
            throw new SyncManager.SmartSyncException("neither " + SYNC_ID + " nor " + SYNC_NAME + " were specified");
        }

        callbackContext.success();
    }

    /**
     * Native implementation of reSync.
     *
     * @param args
     * @param callbackContext
     * @throws JSONException
     */
    private void reSync(JSONArray args, CallbackContext callbackContext) throws Exception {

        // Parse args.
        JSONObject arg0 = args.getJSONObject(0);
        final boolean isGlobal = SmartStorePlugin.getIsGlobal(arg0);
        final String storeName = SmartStorePlugin.getStoreName(arg0);
        final SyncManager syncManager = getSyncManager(arg0);

        SyncState sync;
        SyncUpdateCallback callback = new SyncUpdateCallback() {
            @Override
            public void onUpdate(SyncState sync) {
                handleSyncUpdate(sync, isGlobal, storeName);
            }
        };

        if (arg0.has(SYNC_ID) && !arg0.isNull(SYNC_ID)) {
            sync = syncManager.reSync(arg0.getLong(SYNC_ID), callback);
        }
        else if (arg0.has(SYNC_NAME) && !arg0.isNull(SYNC_NAME)) {
            sync = syncManager.reSync(arg0.getString(SYNC_NAME), callback);
        }
        else {
            throw new SyncManager.SmartSyncException("neither " + SYNC_ID + " nor " + SYNC_NAME + " were specified");
        }
        callbackContext.success(sync.asJSON());
    }

    /**
     * Native implementation of cleanResyncGhosts.
     *
     * @param args
     * @param callbackContext
     * @throws JSONException
     */
    private void cleanResyncGhosts(JSONArray args, final CallbackContext callbackContext) throws Exception {

        // Parse args.
        final JSONObject arg0 = args.getJSONObject(0);
        long syncId = arg0.getLong(SYNC_ID);
        final SyncManager syncManager = getSyncManager(arg0);
        syncManager.cleanResyncGhosts(syncId, new SyncManager.CleanResyncGhostsCallback() {
            @Override
            public void onSuccess(int numRecords) {
                callbackContext.success(numRecords);
            }

            @Override
            public void onError(Exception e) {
                callbackContext.error(e.getMessage());
            }
        });
    }

    /**
     * Sync update handler.
     *
     * @param sync
     */
    private void handleSyncUpdate(final SyncState sync, final boolean isGlobal, final String storeName) {
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                try {
                    JSONObject jsonObject = sync.asJSON();
                    jsonObject.put(IS_GLOBAL_STORE,isGlobal);
                    jsonObject.put(STORE_NAME,storeName);
                    String syncAsString = jsonObject.toString();
                    String js = "javascript:document.dispatchEvent(new CustomEvent(\"" + SYNC_EVENT_TYPE + "\", { \"" + DETAIL + "\": " + syncAsString + "}))";
                    webView.loadUrl(js);
                } catch (Exception e) {
                    SalesforceHybridLogger.e(TAG, "Failed to dispatch event", e);
                }
            }
        });
    }

    /**
     * Return sync manager to use.
     *
     * @param arg0
     * @return SyncManager
     */
    private SyncManager getSyncManager(JSONObject arg0) throws Exception {
        final SmartStore smartStore = SmartStorePlugin.getSmartStore(arg0);
        final SyncManager syncManager = SyncManager.getInstance(null, null, smartStore);
        return syncManager;
    }
}
