/*
 * Copyright (c) 2015-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.reactnative.bridge;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.salesforce.androidsdk.reactnative.util.SalesforceReactLogger;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.mobilesync.manager.SyncManager;
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget;
import com.salesforce.androidsdk.mobilesync.target.SyncUpTarget;
import com.salesforce.androidsdk.mobilesync.util.SyncOptions;
import com.salesforce.androidsdk.mobilesync.util.SyncState;

import org.json.JSONObject;

public class SmartSyncReactBridge extends ReactContextBaseJavaModule {

    // Keys in json from/to javascript
    static final String TARGET = "target";
    static final String SOUP_NAME = "soupName";
    static final String OPTIONS = "options";
    static final String SYNC_ID = "syncId";
    static final String SYNC_NAME = "syncName";
    public static final String TAG = "SmartSyncReactBridge";

    public SmartSyncReactBridge(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return TAG;
    }

    /**
     * Native implementation of syncUp
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    public void syncUp(ReadableMap args,
                       final Callback successCallback, final Callback errorCallback) {
        // Parse args
        JSONObject target = new JSONObject(ReactBridgeHelper.toJavaMap(args.getMap(TARGET)));
        String soupName = args.getString(SOUP_NAME);
        JSONObject options = new JSONObject(ReactBridgeHelper.toJavaMap(args.getMap(OPTIONS)));
        String syncName = args.hasKey(SYNC_NAME) ? args.getString(SYNC_NAME) : null;
        try {
            final SyncManager syncManager = getSyncManager(args);
            syncManager.syncUp(SyncUpTarget.fromJSON(target), SyncOptions.fromJSON(options), soupName, syncName, new SyncManager.SyncUpdateCallback() {
                @Override
                public void onUpdate(SyncState sync) {
                    handleSyncUpdate(sync, successCallback, errorCallback);
                }
            });
        } catch (Exception e) {
            SalesforceReactLogger.e(TAG, "syncUp call failed", e);
            errorCallback.invoke(e.toString());
        }
    }

    /**
     * Native implementation of syncDown
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    public void syncDown(ReadableMap args,
                         final Callback successCallback, final Callback errorCallback) {
        // Parse args
        JSONObject target = new JSONObject(ReactBridgeHelper.toJavaMap(args.getMap(TARGET)));
        String soupName = args.getString(SOUP_NAME);
        JSONObject options = new JSONObject(ReactBridgeHelper.toJavaMap(args.getMap(OPTIONS)));
        String syncName = args.hasKey(SYNC_NAME) ? args.getString(SYNC_NAME) : null;
        try {
            final SyncManager syncManager = getSyncManager(args);
            syncManager.syncDown(SyncDownTarget.fromJSON(target), SyncOptions.fromJSON(options), soupName, syncName, new SyncManager.SyncUpdateCallback() {
                @Override
                public void onUpdate(SyncState sync) {
                    handleSyncUpdate(sync, successCallback, errorCallback);
                }
            });
        } catch (Exception e) {
            SalesforceReactLogger.e(TAG, "syncDown call failed", e);
            errorCallback.invoke(e.toString());
        }
    }

    /**
     * Native implementation of getSyncStatus
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    public void getSyncStatus(ReadableMap args,
                              final Callback successCallback, final Callback errorCallback) {
        try {
            SyncState sync;
            final SyncManager syncManager = getSyncManager(args);
            if (args.hasKey(SYNC_ID) && !args.isNull(SYNC_ID)) {
                sync = syncManager.getSyncStatus(args.getInt(SYNC_ID));
            }
            else if (args.hasKey(SYNC_NAME) && !args.isNull(SYNC_NAME)) {
                sync = syncManager.getSyncStatus(args.getString(SYNC_NAME));
            }
            else {
                throw new SyncManager.SmartSyncException("neither " + SYNC_ID + " nor " + SYNC_NAME + " were specified");
            }
            ReactBridgeHelper.invoke(successCallback, sync == null ? null : sync.asJSON());
        } catch (Exception e) {
            SalesforceReactLogger.e(TAG, "getSyncStatusByName call failed", e);
            errorCallback.invoke(e.toString());
        }
    }

    /**
     * Native implementation of deleteSync
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    public void deleteSync(ReadableMap args,
                               final Callback successCallback, final Callback errorCallback) {
        try {
            final SyncManager syncManager = getSyncManager(args);
            if (args.hasKey(SYNC_ID) && !args.isNull(SYNC_ID)) {
                syncManager.deleteSync(args.getInt(SYNC_ID));
            }
            else if (args.hasKey(SYNC_NAME) && !args.isNull(SYNC_NAME)) {
                syncManager.deleteSync(args.getString(SYNC_NAME));
            }
            else {
                throw new SyncManager.SmartSyncException("neither " + SYNC_ID + " nor " + SYNC_NAME + " were specified");
            }
            successCallback.invoke();
        } catch (Exception e) {
            SalesforceReactLogger.e(TAG, "deleteSyncById call failed", e);
            errorCallback.invoke(e.toString());
        }
    }

    /**
     * Native implementation of reSync
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    public void reSync(ReadableMap args,
                       final Callback successCallback, final Callback errorCallback) {
        try {
            final SyncManager syncManager = getSyncManager(args);
            SyncManager.SyncUpdateCallback callback = new SyncManager.SyncUpdateCallback() {
                @Override
                public void onUpdate(SyncState sync) {
                    handleSyncUpdate(sync, successCallback, errorCallback);
                }
            };

            if (args.hasKey(SYNC_ID) && !args.isNull(SYNC_ID)) {
                syncManager.reSync(args.getInt(SYNC_ID), callback);
            }
            else if (args.hasKey(SYNC_NAME) && !args.isNull(SYNC_NAME)) {
                syncManager.reSync(args.getString(SYNC_NAME), callback);
            }
            else {
                throw new SyncManager.SmartSyncException("neither " + SYNC_ID + " nor " + SYNC_NAME + " were specified");
            }
        } catch (Exception e) {
            SalesforceReactLogger.e(TAG, "reSync call failed", e);
            errorCallback.invoke(e.toString());
        }
    }

    /**
     * Native implementation of cleanResyncGhosts
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    public void cleanResyncGhosts(ReadableMap args,
                       final Callback successCallback, final Callback errorCallback) {
        // Parse args
        long syncId = args.getInt(SYNC_ID);
        try {
            final SyncManager syncManager = getSyncManager(args);
            syncManager.cleanResyncGhosts(syncId, new SyncManager.CleanResyncGhostsCallback() {
                @Override
                public void onSuccess(int numRecords) {
                    successCallback.invoke(numRecords);
                }

                @Override
                public void onError(Exception e) {
                    errorCallback.invoke(e.toString());
                }
            });
        } catch (Exception e) {
            SalesforceReactLogger.e(TAG, "cleanResyncGhosts call failed", e);
            errorCallback.invoke(e.toString());
        }
    }

    /**
     * Sync update handler
     * @param sync
     * @param errorCallback
     */
    private void handleSyncUpdate(final SyncState sync, Callback successCallback, Callback errorCallback) {
        try {
            switch (sync.getStatus()) {
                case NEW:
                    break;
                case RUNNING:
                    break;
                case DONE:
                    ReactBridgeHelper.invoke(successCallback, sync.asJSON());
                    break;
                case FAILED:
                    //Return sync to React Native with the error message in the JSON
                    ReactBridgeHelper.invoke(errorCallback, sync.asJSON());
                    break;
            }
        } catch (Exception e) {
            SalesforceReactLogger.e(TAG, "handleSyncUpdate call failed", e);
        }
    }

    /**
     * Return sync manager to use
     * @param args Arguments passed to the bridge
     * @return
     */
    private SyncManager getSyncManager(ReadableMap args) throws Exception {
        final SmartStore smartStore = SmartStoreReactBridge.getSmartStore(args);
        final SyncManager syncManager = SyncManager.getInstance(null, null, smartStore);
        return syncManager;
    }
}

