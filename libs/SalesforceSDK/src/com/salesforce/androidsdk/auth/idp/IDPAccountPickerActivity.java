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
import android.content.Intent;
import android.os.Bundle;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.ui.AccountSwitcherActivity;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

/**
 * This class provides UI to select an existing signed in user account, or add a new account.
 * It kicks off the IDP authentication flow once the selection is made. This screen is popped
 * off the activity stack once the flow is complete, passing back control to the SP app.
 *
 * @author bhariharan
 */
public class IDPAccountPickerActivity extends AccountSwitcherActivity {

    private static final int LOGIN_REQUEST_CODE = 999;
    private static final String TAG = "IDPAccountPickerActivity";

    private SPConfig spConfig;

    @Override
    public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);

        // Fetches the required extras.
        final Intent intent = getIntent();
        final Bundle extras = intent.getExtras();
        if (extras != null) {
            spConfig = new SPConfig(extras.getBundle(IDPCodeGeneratorActivity.SP_CONFIG_BUNDLE_KEY));
        }
        /*
         * TODO: Take the SPConfig extra coming from here and add the selected
         * UserAccount extra to it and then launch IDCodeGeneratorActivity.
         */
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
        } else {
            kickOffNewUserFlow();
        }
    }

    @Override
    protected void finishActivity() {
        SalesforceSDKLogger.d(TAG, "Inside finish activity!");

        // Do nothing in here, since we don't want to finish this activity until the IDP flow is complete.
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        SalesforceSDKLogger.d(TAG, "New user flow complete!");

        // Kicks off the SP app's authentication call, since the IDP app is now authenticated.
        final UserAccount account = userAccMgr.getCurrentUser();
        if (account != null) {
            proceedWithIDPAuthFlow(account);
        }
    }

    private void kickOffNewUserFlow() {
        SalesforceSDKLogger.d(TAG, "Kicking off new user flow!");
        final Bundle reply = new Bundle();
        final Bundle options = SalesforceSDKManager.getInstance().getLoginOptions().asBundle();
        final Intent i = new Intent(this, SalesforceSDKManager.getInstance().getLoginActivityClass());
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtras(options);
        reply.putParcelable(AccountManager.KEY_INTENT, i);
        startActivityForResult(i, LOGIN_REQUEST_CODE);
    }

    private void proceedWithIDPAuthFlow(UserAccount account) {
        SalesforceSDKLogger.d(TAG, "Proceeding with IDP auth flow, account: " + account.toString());
        finish();
        // TODO:
    }
}
