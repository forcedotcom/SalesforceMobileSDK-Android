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
package com.salesforce.androidsdk.ui;

import android.app.Activity;
import android.content.IntentFilter;
import android.view.KeyEvent;

import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.security.PasscodeManager;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.LogoutCompleteReceiver;
import com.salesforce.androidsdk.util.UserSwitchReceiver;

/**
 * Class taking care of common behavior of Salesforce*Activity classes
 */

public class SalesforceActivityDelegate {

    private Activity activity;
    private PasscodeManager passcodeManager;
    private UserSwitchReceiver userSwitchReceiver;
    private LogoutCompleteReceiver logoutCompleteReceiver;


    public SalesforceActivityDelegate(Activity activity) {
        this.activity = activity;
    }

    public void onCreate() {
        // Gets an instance of the passcode manager.
        passcodeManager = SalesforceSDKManager.getInstance().getPasscodeManager();
        userSwitchReceiver = new ActivityUserSwitchReceiver();
        activity.registerReceiver(userSwitchReceiver, new IntentFilter(UserAccountManager.USER_SWITCH_INTENT_ACTION));
        logoutCompleteReceiver = new ActivityLogoutCompleteReceiver();
        activity.registerReceiver(logoutCompleteReceiver, new IntentFilter(SalesforceSDKManager.LOGOUT_COMPLETE_INTENT_ACTION));

        // Lets observers know that activity creation is complete.
        EventsObservable.get().notifyEvent(EventsObservable.EventType.MainActivityCreateComplete, this);
    }

    /**
     * Brings up passcode screen if needed
     * Build RestClient if requested and then calls activity.onResume(restClient)
     * Otherwise calls activity.onResume(null)
     *
     * @param buildRestClient
     */
    public void onResume(boolean buildRestClient) {
        // Brings up the passcode screen if needed.
        if (passcodeManager.onResume(activity)) {
            if (buildRestClient) {
                // Gets login options.
                final String accountType = SalesforceSDKManager.getInstance().getAccountType();
                final ClientManager.LoginOptions loginOptions = SalesforceSDKManager.getInstance().getLoginOptions();

                // Gets a rest client.
                new ClientManager(activity, accountType, loginOptions,
                        SalesforceSDKManager.getInstance().shouldLogoutWhenTokenRevoked()).getRestClient(activity, new ClientManager.RestClientCallback() {

                    @Override
                    public void authenticatedRestClient(RestClient client) {
                        if (client == null) {
                            SalesforceSDKManager.getInstance().logout(activity);
                            return;
                        }
                        ((SalesforceActivityInterface) activity).onResume(client);

                        // Lets observers know that rendition is complete.
                        EventsObservable.get().notifyEvent(EventsObservable.EventType.RenditionComplete);
                    }
                });
            }
            else {
                ((SalesforceActivityInterface) activity).onResume(null);
            }
        }
    }

    public void onUserInteraction() {
        passcodeManager.recordUserInteraction();
    }

    public void onPause() {
        passcodeManager.onPause(activity);
    }

    public void onDestroy() {
        activity.unregisterReceiver(userSwitchReceiver);
        activity.unregisterReceiver(logoutCompleteReceiver);
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (SalesforceSDKManager.getInstance().isDevSupportEnabled()) {
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                SalesforceSDKManager.getInstance().showDevSupportDialog(activity);
                return true;
            }
        }
        return false;
    }


    /**
     * Acts on the user switch event.
     */
    private class ActivityUserSwitchReceiver extends UserSwitchReceiver {

        @Override
        protected void onUserSwitch() {
            ((SalesforceActivityInterface) activity).onUserSwitched();
        }
    }

    /**
     * Acts on the logout complete event.
     */
    private class ActivityLogoutCompleteReceiver extends LogoutCompleteReceiver {

        @Override
        protected void onLogoutComplete() {
            ((SalesforceActivityInterface) activity).onLogoutComplete();
        }
    }
}