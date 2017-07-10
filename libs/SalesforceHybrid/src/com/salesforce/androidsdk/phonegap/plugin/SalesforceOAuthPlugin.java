/*
 * Copyright (c) 2011-present, salesforce.com, inc.
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

import com.salesforce.androidsdk.phonegap.ui.SalesforceDroidGapActivity;
import com.salesforce.androidsdk.phonegap.ui.SalesforceWebViewClientHelper;
import com.salesforce.androidsdk.phonegap.util.SalesforceHybridLogger;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

/**
 * PhoneGap plugin for Salesforce OAuth.
 */
public class SalesforceOAuthPlugin extends ForcePlugin {

    private static String TAG = "SalesforceOAuthPlugin";

    /**
     * Supported plugin actions that the client can take.
     */
    enum Action {
        authenticate,
        getAuthCredentials,
        logoutCurrentUser,
        getAppHomeUrl
    }
    
    @Override
    public boolean execute(final String actionStr, JavaScriptPluginVersion jsVersion,
                           final JSONArray args, final CallbackContext callbackContext) throws JSONException {

        // Not running plugin actions on the main thread.
        cordova.getThreadPool().execute(new Runnable() {

            @Override
            public void run() {

            // Figure out action.
            Action action = null;
            try {
                action = Action.valueOf(actionStr);
                switch(action) {
                    case authenticate:       	authenticate(callbackContext); break;
                    case getAuthCredentials: 	getAuthCredentials(callbackContext); break;
                    case logoutCurrentUser:		logoutCurrentUser(callbackContext); break;
                    case getAppHomeUrl:			getAppHomeUrl(callbackContext); break;
                    default: throw new Exception("No handler for action " + action);
                }
            } catch (Exception e) {
                SalesforceHybridLogger.w(TAG, "execute: " + e.getMessage(), e);
                callbackContext.error(e.getMessage());
            }
            }
        });
        return true;
    }

    /**
     * Native implementation for "authenticate" action
     * @param callbackContext Used when calling back into Javascript.
     * @return NO_RESULT since authentication is asynchronous.
     * @throws JSONException
     */
    protected void authenticate(final CallbackContext callbackContext) throws JSONException {
        SalesforceHybridLogger.i(TAG, "authenticate called");
        ((SalesforceDroidGapActivity) cordova.getActivity()).authenticate(callbackContext);

        // Done.
        PluginResult noop = new PluginResult(PluginResult.Status.NO_RESULT);
        noop.setKeepCallback(true);
        callbackContext.sendPluginResult(noop);
    }

    /**
     * Native implementation for "getAuthCredentials" action.
     * @param callbackContext Used when calling back into Javascript.
     * @throws JSONException
     */
    protected void getAuthCredentials(CallbackContext callbackContext) throws JSONException {
        SalesforceHybridLogger.i(TAG, "getAuthCredentials called");
        ((SalesforceDroidGapActivity) cordova.getActivity()).getAuthCredentials(callbackContext);
    }

    /**
     * Native implementation for getAppHomeUrl
     * @param callbackContext Used when calling back into Javascript.
     */
    protected void getAppHomeUrl(CallbackContext callbackContext)  {
        SalesforceHybridLogger.i(TAG, "getAppHomeUrl called");
        callbackContext.success(SalesforceWebViewClientHelper.getAppHomeUrl(cordova.getActivity()));
    }

    /**
     * Native implementation for "logout" action
     * @param callbackContext Used when calling back into Javascript.
     */
    protected void logoutCurrentUser(CallbackContext callbackContext) {
        SalesforceHybridLogger.i(TAG, "logoutCurrentUser called");
        ((SalesforceDroidGapActivity) cordova.getActivity()).logout(callbackContext);
    }
}
