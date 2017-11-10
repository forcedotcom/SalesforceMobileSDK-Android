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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.app.SalesforceSDKManager;

/**
 * This class receives a request to login from an IDP app and triggers the
 * IDP login flow from within the SP app based on the 'user_hint' passed in.
 *
 * @author bhariharan
 */
public class IDPInititatedLoginReceiver extends BroadcastReceiver {

    public static final String IDP_LOGIN_REQUEST_ACTION = "com.salesforce.IDP_LOGIN_REQUEST";
    public static final String USER_HINT_KEY = "user_hint";
    public static final String IDP_INIT_LOGIN_KEY = "idp_init_login";
    private static final String COLON = ":";

    private String userHint;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null) {
            if (IDP_LOGIN_REQUEST_ACTION.equals(intent.getAction())) {
                final Bundle extras = intent.getExtras();
                if (extras != null) {
                    userHint = extras.getString(USER_HINT_KEY);
                }

                // Launches login flow if the user doesn't already exist on the SP app.
                if (!doesUserExist()) {
                    launchLoginActivity();
                }
            }
        }
    }

    private boolean doesUserExist() {
        boolean accountExists = false;
        if (!TextUtils.isEmpty(userHint)) {
            final String[] userParts = userHint.split(COLON);

            /*
             * The value for 'user_hint' should be of the format 'orgId:userId' and should
             * use the 18-character versions of 'orgId' and 'userId'.
             */
            if (userParts.length == 2) {
                final String orgId = userParts[0];
                final String userId = userParts[1];
                final UserAccount account = SalesforceSDKManager.getInstance().
                        getUserAccountManager().getUserFromOrgAndUserId(orgId, userId);
                if (account != null) {
                    accountExists = true;
                }
            }
        }
        return accountExists;
    }

    private void launchLoginActivity() {
        final Bundle options = SalesforceSDKManager.getInstance().getLoginOptions().asBundle();
        final Intent intent = new Intent(SalesforceSDKManager.getInstance().getAppContext(),
                SalesforceSDKManager.getInstance().getLoginActivityClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtras(options);
        intent.putExtra(USER_HINT_KEY, userHint);
        intent.putExtra(IDP_INIT_LOGIN_KEY, true);
        SalesforceSDKManager.getInstance().getAppContext().startActivity(intent);
    }
}
