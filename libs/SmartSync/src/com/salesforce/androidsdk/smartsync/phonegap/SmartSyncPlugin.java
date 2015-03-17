/*
 * Copyright (c) 2014, salesforce.com, inc.
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
package com.salesforce.androidsdk.smartsync.phonegap;

import android.util.Log;

import com.salesforce.androidsdk.phonegap.ForcePlugin;
import com.salesforce.androidsdk.phonegap.JavaScriptPluginVersion;
import com.salesforce.androidsdk.smartsync.manager.SyncManager;
import com.salesforce.androidsdk.smartsync.manager.SyncManager.SyncUpdateCallback;
import com.salesforce.androidsdk.smartsync.util.SyncDownTarget;
import com.salesforce.androidsdk.smartsync.util.SyncOptions;
import com.salesforce.androidsdk.smartsync.util.SyncState;
import com.salesforce.androidsdk.smartsync.util.SyncUpTarget;

import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * PhoneGap plugin for smart sync.
 */
public class SmartSyncPlugin extends ForcePlugin {

    // Keys in json from/to javascript
    static final String TARGET = "target";
    static final String SOUP_NAME = "soupName";
    static final String OPTIONS = "options";
    static final String SYNC_ID = "syncId";

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
        reSync
    }
    
    @Override
    public boolean execute(String actionStr, JavaScriptPluginVersion jsVersion, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        final long start = System.currentTimeMillis();
        // Figure out action
        final Action action;
        try {
            action = Action.valueOf(actionStr);
        }
        catch (IllegalArgumentException e) {
            Log.e("SmartSyncPlugin.execute", "Unknown action " + actionStr);
            return false;
        }

        // Not running smartstore action on the main thread
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                // All smart store action need to be serialized
                synchronized(SmartSyncPlugin.class) {
                    try {
                        switch(action) {
                          case syncUp:             syncUp(args, callbackContext); break;
                          case syncDown:           syncDown(args, callbackContext); break;
                          case getSyncStatus:      getSyncStatus(args, callbackContext); break;
                          case reSync:             reSync(args, callbackContext); break;
                          default: throw new RuntimeException("No handler for action " + action);
                        }
                    }
                    catch (Exception e) {
                        Log.w("SmartSyncPlugin.execute", e.getMessage(), e);
                        callbackContext.error(e.getMessage());
                    }                   
                    Log.d("SmartSyncPlugin.execute", "Total time for " + action + "->" + (System.currentTimeMillis() - start));
                }
            }
        });
        Log.d("SmartSyncPlugin.execute", "Main thread time for " + action + "->" + (System.currentTimeMillis() - start));
        return true;
    }

    /**
     * Native implementation of syncUp
     * @param args
     * @param callbackContext
     * @throws JSONException 
     */
    private void syncUp(JSONArray args, CallbackContext callbackContext) throws JSONException {
        // Parse args
        JSONObject arg0 = args.getJSONObject(0);
        JSONObject target = arg0.getJSONObject(TARGET);
        String soupName = arg0.getString(SOUP_NAME);
        JSONObject options = arg0.optJSONObject(OPTIONS);

        SyncManager syncManager = SyncManager.getInstance(null);
        SyncState sync = syncManager.syncUp(SyncUpTarget.fromJSON(target), SyncOptions.fromJSON(options), soupName, new SyncUpdateCallback() {
            @Override
            public void onUpdate(SyncState sync) {
                handleSyncUpdate(sync);
            }
        });
        callbackContext.success(sync.asJSON());
    }

    /**
     * Native implementation of syncDown
     * @param args
     * @param callbackContext
     * @throws JSONException 
     */
    private void syncDown(JSONArray args, CallbackContext callbackContext) throws JSONException {
        // Parse args
        JSONObject arg0 = args.getJSONObject(0);
        JSONObject target = arg0.getJSONObject(TARGET);
        String soupName = arg0.getString(SOUP_NAME);
        JSONObject options = arg0.getJSONObject(OPTIONS);
        
        SyncManager syncManager = SyncManager.getInstance(null);
        SyncState sync = syncManager.syncDown(SyncDownTarget.fromJSON(target), SyncOptions.fromJSON(options), soupName, new SyncUpdateCallback() {
            @Override
            public void onUpdate(SyncState sync) {
                handleSyncUpdate(sync);
            }
        });
        callbackContext.success(sync.asJSON());
    }
    
    /**
     * Native implementation of getSyncStatus
     * @param args
     * @param callbackContext
     * @throws JSONException 
     */ 
    private void getSyncStatus(JSONArray args, CallbackContext callbackContext) throws JSONException {
        // Parse args
        JSONObject arg0 = args.getJSONObject(0);
        long syncId = arg0.getLong(SYNC_ID);
        
        SyncManager syncManager = SyncManager.getInstance(null);
        SyncState sync = syncManager.getSyncStatus(syncId);
        
        callbackContext.success(sync.asJSON());
    }

    /**
     * Native implementatino of reSync
     * @param args
     * @param callbackContext
     * @throws JSONException
     */
    private void reSync(JSONArray args, CallbackContext callbackContext) throws JSONException {
        // Parse args
        JSONObject arg0 = args.getJSONObject(0);
        long syncId = arg0.getLong(SYNC_ID);

        SyncManager syncManager = SyncManager.getInstance(null);
        SyncState sync = syncManager.reSync(syncId, new SyncUpdateCallback() {
            @Override
            public void onUpdate(SyncState sync) {
                handleSyncUpdate(sync);
            }
        });

        callbackContext.success(sync.asJSON());
    }

    private void handleSyncUpdate(final SyncState sync) {
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                try {
                    String syncAsString = sync.asJSON().toString();
                    String js = "javascript:document.dispatchEvent(new CustomEvent(\"" + SYNC_EVENT_TYPE + "\", { \"" + DETAIL + "\": " + syncAsString + "}))";
                    webView.loadUrl(js);
                }
                catch (Exception e) {
                    Log.e("SmartSyncPlugin.handleSyncUpdate", "Failed to dispatch event", e);
                }
            }
        });
    }
}
