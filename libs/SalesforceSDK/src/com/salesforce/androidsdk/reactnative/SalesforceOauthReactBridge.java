/*
 * Copyright (c) 2015, salesforce.com, inc.
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
package com.salesforce.androidsdk.reactnative;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.RestClient;

import java.util.HashMap;
import java.util.Map;

public class SalesforceOauthReactBridge extends ReactContextBaseJavaModule {

    // Keys in credentials map
    private static final String INSTANCE_URL = "instanceUrl";
    private static final String LOGIN_URL = "loginUrl";
    private static final String IDENTITY_URL = "identityUrl";
    private static final String CLIENT_ID = "clientId";
    private static final String ORG_ID = "orgId";
    private static final String USER_ID = "userId";
    private static final String REFRESH_TOKEN = "refreshToken";
    private static final String ACCESS_TOKEN = "accessToken";
    private static final String COMMUNITY_ID = "communityId";
    private static final String COMMUNITY_URL = "communityUrl";


    public SalesforceOauthReactBridge(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "SalesforceOauthReactBridge";
    }

    @ReactMethod
    public void logoutCurrentUser(ReadableMap args,
                                  Callback successCallback, Callback errorCallback) {
        UserAccount account = SalesforceSDKManager.getInstance().getUserAccountManager().getCurrentUser();
        SalesforceSDKManager.getInstance().getUserAccountManager().signoutUser(account, null);

        successCallback.invoke();
    }

    @ReactMethod
    public void getAuthCredentials(ReadableMap args,
                                   Callback successCallback, Callback errorCallback) {
        ClientManager clientManager = new ClientManager(getReactApplicationContext(), SalesforceSDKManager.getInstance().getAccountType(),
                SalesforceSDKManager.getInstance().getLoginOptions(),
                SalesforceSDKManager.getInstance().shouldLogoutWhenTokenRevoked());
        RestClient client = clientManager.peekRestClient();

        RestClient.ClientInfo clientInfo = client.getClientInfo();
        Map<String, String> data = new HashMap<String, String>();
        data.put(ACCESS_TOKEN, client.getAuthToken());
        data.put(REFRESH_TOKEN, client.getRefreshToken());
        data.put(USER_ID, clientInfo.userId);
        data.put(ORG_ID, clientInfo.orgId);
        data.put(CLIENT_ID, clientInfo.clientId);
        data.put(LOGIN_URL, clientInfo.loginUrl.toString());
        data.put(IDENTITY_URL, clientInfo.identityUrl.toString());
        data.put(INSTANCE_URL, clientInfo.instanceUrl.toString());
        data.put(COMMUNITY_ID, clientInfo.communityId);
        data.put(COMMUNITY_URL, clientInfo.communityUrl);

        successCallback.invoke(data.toString());
    }
}
