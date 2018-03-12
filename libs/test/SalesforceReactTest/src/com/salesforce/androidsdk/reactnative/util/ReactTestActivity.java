/*
 * Copyright (c) 2018-present, salesforce.com, inc.
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

package com.salesforce.androidsdk.reactnative.util;

import com.facebook.react.ReactActivityDelegate;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.reactnative.ui.SalesforceReactActivity;
import com.salesforce.androidsdk.rest.ClientManager;

/**
 * Sub-class of SalesforceReactActivity that authenticates using hard-coded credentials.
 *
 * Also uses ReactActivityTestDelegate as delegate
 */
public class ReactTestActivity extends SalesforceReactActivity {

    static String username = "testuser@cs4.com";
    static String accountName = "testuser@cs4.com (https://cs4.salesforce.com) (test)";
    static String refreshToken = "5Aep8610_HRVGlMVK1Ii_.X.2OmSMyFBQAlqyGWdLwdJtsKFYt.3jKG0KkuLUZvsCjW5PHu2F5lpuaAWE0vt3D7";
    static String authToken = "--will-be-set-through-refresh--";
    static String identityUrl = "https://test.salesforce.com/id/00DP00000002p6hMAA/005P0000001np0OIAQ";
    static String instanceUrl = "https://images.cs4.my.salesforce.com";
    static String loginUrl = "https://test.salesforce.com";
    static String orgId = "00DP00000002p6hMAA";
    static String userId = "005P0000001np0OIAQ";

    @Override
    protected ClientManager buildClientManager() {
        final ClientManager clientManager = super.buildClientManager();
        final ClientManager.LoginOptions loginOptions = SalesforceSDKManager.getInstance().getLoginOptions();
        clientManager.createNewAccount(accountName,
                username, refreshToken,
                authToken, instanceUrl,
                loginUrl, identityUrl,
                loginOptions.getOauthClientId(), orgId,
                userId, null, null, null, null, null, null, null, null, null);
        return clientManager;
    }

    protected ReactActivityDelegate createReactActivityDelegate() {
        return new ReactActivityTestDelegate(this, null);
    }

}
