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
package com.salesforce.androidsdk.auth.idp;

import android.accounts.AccountManager;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.TextUtils;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.app.Features;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.config.LoginServerManager;
import com.salesforce.androidsdk.ui.AccountSwitcherActivity;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * This class provides UI to select an existing signed in user account, or add a new account.
 * It kicks off the IDP authentication flow once the selection is made. This screen is popped
 * off the activity stack once the flow is complete, passing back control to the SP app.
 *
 * @author bhariharan
 */
public class IDPAccountPickerActivity extends AccountSwitcherActivity {

    public static final String USER_ACCOUNT_KEY = "user_account";
    public static final String IDP_LOGIN_COMPLETE_ACTION = "com.salesforce.androidsdk.auth.idp.IDP_LOGIN_COMPLETE";
    private static final String COLON = ":";
    private static final String TAG = "IDPAccountPickerActivity";

    private SPConfig spConfig;
    private IDPLoginCompleteReceiver idpLoginCompleteReceiver;

    @Override
    public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_APP_IS_IDP);

        // Fetches the required extras.
        final Intent intent = getIntent();
        final Bundle extras = intent.getExtras();
        if (extras != null) {
            spConfig = new SPConfig(extras.getBundle(IDPCodeGeneratorActivity.SP_CONFIG_BUNDLE_KEY));
        }
        idpLoginCompleteReceiver = new IDPLoginCompleteReceiver();
        registerReceiver(idpLoginCompleteReceiver, new IntentFilter(IDP_LOGIN_COMPLETE_ACTION));
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(idpLoginCompleteReceiver);
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        UserAccount selectedAccount = null;
        final String userHint = spConfig.getUserHint();
        if (!TextUtils.isEmpty(userHint)) {
            final String[] userParts = userHint.split(COLON);

            /*
             * The value for 'user_hint' should be of the format 'orgId:userId' and should
             * use the 18-character versions of 'orgId' and 'userId'.
             */
            if (userParts.length == 2) {
                final String orgId = userParts[0];
                final String userId = userParts[1];
                selectedAccount = SalesforceSDKManager.getInstance().
                        getUserAccountManager().getUserFromOrgAndUserId(orgId, userId);
            }
        }

        /*
         * If we could build a user account from the 'user_hint' value passed in,
         * launches SP login flow for that account. Otherwise, we launch the new user
         * login flow directly (because selectedAccount will be null).
         */
        if (selectedAccount != null || getAccounts() == null) {
            accountSelected(selectedAccount);
        }
    }

    @Override
    protected List<UserAccount> getAccounts() {
        final List<UserAccount> accounts = userAccMgr.getAuthenticatedUsers();

        // If no login server is passed in, return all user accounts.
        if (TextUtils.isEmpty(spConfig.getLoginUrl())) {
            return accounts;
        }
        final List<UserAccount> filteredAccounts = new ArrayList<>();
        if (accounts != null) {
            for (final UserAccount account : accounts) {

                // If user account has the same login server, add it to the list.
                if (spConfig.getLoginUrl().equals(account.getLoginServer())) {
                    filteredAccounts.add(account);
                }
            }
        }
        if (filteredAccounts.size() == 0) {
            return null;
        }
        return filteredAccounts;
    }

    @Override
    protected void accountSelected(UserAccount account) {
        SalesforceSDKLogger.d(TAG, "Account selected: " + account);

        /*
         * If an account is passed in, proceeds with IDP auth flow directly. This will kick off
         * authentication for the SP app without messing with the state of the IDP. If no account
         * is passed in, kicks off new user login flow, sets current user on the IDP to the newly
         * logged in user, and then resumes authentication for the SP app with that user.
         */
        if (account != null) {
            proceedWithIDPAuthFlow(account);
        } else if (!SalesforceSDKManager.getInstance().isIDPAppLoginFlowActive()) {
            kickOffNewUserFlow();
        }
    }

    @Override
    protected void finishActivity() {

        // Do nothing in here, since we don't want to finish this activity until the IDP flow is complete.
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SPRequestHandler.IDP_REQUEST_CODE) {
            SalesforceSDKLogger.d(TAG, "Activity result obtained - IDP code exchange complete");
            SalesforceSDKManager.getInstance().setIDPAppLoginFlowActive(false);
            if (resultCode == Activity.RESULT_OK) {
                setResult(RESULT_OK, data);
            } else {
                setResult(RESULT_CANCELED, data);
            }
            finish();
        }
    }

    private Intent getIDPLoginFailureIntent() {
        final Intent intent = new Intent();
        intent.putExtra(IDPCodeGeneratorActivity.ERROR_KEY, "Failed to log in to IDP app");
        return intent;
    }

    private void kickOffNewUserFlow() {
        SalesforceSDKLogger.d(TAG, "Kicking off new user flow within IDP");
        final Bundle reply = new Bundle();
        final String loginUrl = spConfig.getLoginUrl();

        /*
         * If a login URL is passed in from the SP app, sets that server as the selected
         * server before launching LoginActivity. If the server passed in is not available
         * on the IDP, returns an error back to the SP app. If no login URL is passed in,
         * launches LoginActivity on the IDP with the current selection.
         */
        if (!TextUtils.isEmpty(loginUrl)) {
            final LoginServerManager.LoginServer loginServer = getLoginServer(loginUrl);
            if (loginServer == null) {
                final Intent intent = new Intent();
                intent.putExtra(IDPCodeGeneratorActivity.ERROR_KEY,
                        "Specified login server does not exist on IDP app");
                setResult(RESULT_CANCELED, intent);
                finish();
            } else {
                SalesforceSDKManager.getInstance().getLoginServerManager().setSelectedLoginServer(loginServer);
            }
        }
        final Bundle options = SalesforceSDKManager.getInstance().getLoginOptions().asBundle();
        final Intent i = new Intent(this, SalesforceSDKManager.getInstance().getLoginActivityClass());
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtras(options);
        reply.putParcelable(AccountManager.KEY_INTENT, i);
        SalesforceSDKManager.getInstance().setIDPAppLoginFlowActive(true);
        startActivity(i);
    }

    private LoginServerManager.LoginServer getLoginServer(String loginUrl) {
        final LoginServerManager loginServerManager = SalesforceSDKManager.getInstance().getLoginServerManager();
        final List<LoginServerManager.LoginServer> loginServers = loginServerManager.getLoginServers();
        if (loginServers == null || loginServers.size() == 0) {
            return null;
        }
        for (final LoginServerManager.LoginServer server : loginServers) {
            if (server.url.equals(loginUrl)) {
                return server;
            }
        }
        return null;
    }

    private void proceedWithIDPAuthFlow(UserAccount account) {
        SalesforceSDKLogger.d(TAG, "Kicking off code exchange flow within IDP for account: " + account);
        final Intent intent = new Intent(this, IDPCodeGeneratorActivity.class);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.putExtra(IDPCodeGeneratorActivity.SP_CONFIG_BUNDLE_KEY, spConfig.toBundle());
        intent.putExtra(IDPCodeGeneratorActivity.USER_ACCOUNT_BUNDLE_KEY, account.toBundle());
        startActivityForResult(intent, SPRequestHandler.IDP_REQUEST_CODE);
    }

    /**
     * A simple receiver that listens for IDP login completion. It fetches the user account
     * that's passed back and kicks off the IDP code exchange flow. We use a receiver here
     * since we can't use the standard 'onActivityResult()' flow with LoginActivity, since
     * LoginActivity uses the 'singleInstance' flag. We need this flag for other actions
     * to work properly, such as log in using Chrome and other login flows.
     */
    private class IDPLoginCompleteReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && IDP_LOGIN_COMPLETE_ACTION.equals(intent.getAction())) {
                SalesforceSDKLogger.d(TAG, "Activity result obtained - IDP login complete");
                final Bundle userAccountBundle = intent.getBundleExtra(USER_ACCOUNT_KEY);
                if (userAccountBundle != null) {
                    final UserAccount account = new UserAccount(userAccountBundle);
                    proceedWithIDPAuthFlow(account);
                } else {
                    IDPAccountPickerActivity.this.setResult(RESULT_CANCELED, getIDPLoginFailureIntent());
                    IDPAccountPickerActivity.this.finish();
                }
            } else {
                IDPAccountPickerActivity.this.setResult(RESULT_CANCELED, getIDPLoginFailureIntent());
                IDPAccountPickerActivity.this.finish();
            }
        }
    }
}
