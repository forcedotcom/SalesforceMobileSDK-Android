/*
 * Copyright (c) 2016, salesforce.com, inc.
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
package com.salesforce.androidsdk.reactnative.ui;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.facebook.react.ReactActivity;
import com.facebook.react.bridge.Callback;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.reactnative.R;
import com.salesforce.androidsdk.reactnative.bridge.ReactBridgeHelper;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.RestClientCallback;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.security.PasscodeManager;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

/**
 * Super class for all Salesforce activities.
 */
public abstract class SalesforceReactActivity extends ReactActivity {

    private static final String TAG = "SfReactActivity";

    private RestClient client;
    private ClientManager clientManager;
    private PasscodeManager passcodeManager;

    /**
     * @return true if you want login to happen as soon as activity is loaded
     *         false if you want do login at a later point
     */
    public boolean shouldAuthenticate() {
        return true;
    }

    /**
     * Called if shouldAuthenticate() returned true but device is offline
     */
    public void onErrorAuthenticateOffline() {
        Toast t = Toast.makeText(this, R.string.sf__should_authenticate_but_is_offline, Toast.LENGTH_LONG);
        t.show();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate called");
        super.onCreate(savedInstanceState);

        // Get clientManager
        clientManager = buildClientManager();

        // Gets an instance of the passcode manager.
        passcodeManager = SalesforceSDKManager.getInstance().getPasscodeManager();

        // TODO
        // Have a user switcher once we have an account manager bridge for react native

        // Let observers know
        EventsObservable.get().notifyEvent(EventType.MainActivityCreateComplete, this);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Brings up the passcode screen if needed.
        if (passcodeManager.onResume(this)) {

            // Get client (if already logged in)
            try {
                client = clientManager.peekRestClient();
            } catch (ClientManager.AccountInfoNotFoundException e) {
                client = null;
            }

            // Not logged in
            if (client == null) {
                onResumeNotLoggedIn();
            }
            // Logged in
            else {
                Log.i(TAG, "onResume - Already logged in");
            }
        }
    }

    /**
     * Called when resuming activity and user is not authenticated
     */
    private void onResumeNotLoggedIn() {

        // Need to be authenticated
        if (shouldAuthenticate()) {

            // Online
            if (SalesforceSDKManager.getInstance().hasNetwork()) {
                Log.i(TAG, "onResumeNotLoggedIn - Should authenticate / online - authenticating");
                login();
            }

            // Offline
            else {
                Log.w(TAG, "onResumeNotLoggedIn - Should authenticate / offline - cannot proceed");
                onErrorAuthenticateOffline();
            }
        }

        // Does not need to be authenticated
        else {
            Log.i(TAG, "onResumeNotLoggedIn - Should not authenticate");
        }
    }


    @Override
    public void onUserInteraction() {
        passcodeManager.recordUserInteraction();
    }

    @Override
    public void onPause() {
        super.onPause();
        passcodeManager.onPause(this);
    }

    protected void login() {
        Log.i(TAG, "login called");
        clientManager.getRestClient(this, new RestClientCallback() {
            @Override
            public void authenticatedRestClient(RestClient client) {
                if (client == null) {
                    Log.i(TAG, "login - authenticatedRestClient called with null client");
                    logout(null);
                } else {
                    Log.i(TAG, "login - authenticatedRestClient called with actual client");
                    SalesforceReactActivity.this.recreate(); // starting fresh
                }
            }
        });
    }

    /**
     * Method called from bridge to logout
     * @param successCallback
     */
    public void logout(Callback successCallback) {
        Log.i(TAG, "logout called");
        SalesforceSDKManager.getInstance().logout(this);
    }


    /**
     * Method called from bridge to authenticate
     * @param successCallback
     * @param errorCallback
     */
    public void authenticate(final Callback successCallback, final Callback errorCallback) {
        Log.i(TAG, "authenticate called");
        clientManager.getRestClient(this, new RestClientCallback() {
            @Override
            public void authenticatedRestClient(RestClient client) {
                SalesforceReactActivity.this.client = client;
                getAuthCredentials(successCallback, errorCallback);
            }
        });
    }

    /**
     * Method called from bridge to get auth credentials
     * @param successCallback
     * @param errorCallback
     */
    public void getAuthCredentials(Callback successCallback, Callback errorCallback) {
        Log.i(TAG, "getAuthCredentials called");
        if (client != null) {
            if (successCallback != null) {
                ReactBridgeHelper.invokeSuccess(successCallback, client.getJSONCredentials());
            }
        } else {
            if (errorCallback != null) {
                errorCallback.invoke("Not authenticated");
            }
        }
    }

    public RestClient getRestClient() {
        return client;
    }

    protected ClientManager buildClientManager() {
        return new ClientManager(this, SalesforceSDKManager.getInstance().getAccountType(),
                SalesforceSDKManager.getInstance().getLoginOptions(),
                SalesforceSDKManager.getInstance().shouldLogoutWhenTokenRevoked());
    }

}
