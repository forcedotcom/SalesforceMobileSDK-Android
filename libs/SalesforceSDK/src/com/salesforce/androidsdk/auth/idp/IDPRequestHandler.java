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

import android.content.Context;
import android.text.TextUtils;
import android.webkit.WebView;

import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.OAuth2;
import com.salesforce.androidsdk.config.BootConfig;
import com.salesforce.androidsdk.rest.ApiVersionStrings;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import java.io.IOException;
import java.net.URI;

/**
 * This class performs requests made by an app acting as the IDP app using the SP app's
 * config and processes the response received before passing it back to the SP app.
 *
 * @author bhariharan
 */
public class IDPRequestHandler {

    private static final String TAG = "IDPRequestHandler";

    private SPConfig spConfig;
    private UserAccount userAccount;
    private RestClient restClient;
    private String loginUrl;

    /**
     * Paramterized constructor.
     *
     * @param spConfig SP app's config.
     * @param userAccount User account.
     * @throws IDPRequestHandlerException
     */
    public IDPRequestHandler(SPConfig spConfig, UserAccount userAccount) throws IDPRequestHandlerException {
        this.spConfig = spConfig;
        this.userAccount = userAccount;
        basicValidation();
        loginUrl = userAccount.getLoginServer();
        if (TextUtils.isEmpty(loginUrl)) {
            loginUrl = spConfig.getLoginUrl();
        }
        buildRestClient();
    }

    /**
     * Kicks off the 'frontdoor' request in the supplied WebView instance.
     *
     * @param accessToken Valid access token.
     * @param webView WebView instance.
     * @throws IDPRequestHandlerException
     */
    public void makeFrontDoorRequest(String accessToken, WebView webView) throws IDPRequestHandlerException {
        final Context context = SalesforceSDKManager.getInstance().getAppContext();
        final URI frontdoorUrl = OAuth2.getIDPFrontdoorUrl(userAccount.getInstanceServer(),
                accessToken, loginUrl, context.getString(R.string.oauth_display_type),
                spConfig.getOauthClientId(), spConfig.getOauthCallbackUrl(),
                spConfig.getOauthScopes(), spConfig.getCodeChallenge());
        webView.loadUrl(frontdoorUrl.toString());
    }

    /**
     * Makes a simple REST call to and returns a valid access token or null if refresh failed.
     * This method should NOT be called from the main thread since it makes a network request.
     *
     * @return Valid access token.
     * @throws IDPRequestHandlerException
     */
    public String getValidAccessToken() throws IDPRequestHandlerException {

        /*
         * Ensures the access token associated with this user account is valid and refreshes the
         * token if it's not valid. Any request to 'frontdoor' must be made with a valid token.
         */
        final Context context = SalesforceSDKManager.getInstance().getAppContext();
        RestResponse restResponse = null;
        Exception exception = null;
        String accessToken = null;
        try {
            restResponse = restClient.sendSync(RestRequest.getRequestForUserInfo());
        } catch (IOException e) {
            exception = e;
        } finally {
            if (restResponse == null || !restResponse.isSuccess()) {
                handleError("Invalid REST client", exception);
            } else {
                accessToken = restClient.getAuthToken();
            }
        }
        return accessToken;
    }

    /**
     * Returns the login URL being used.
     *
     * @return Login URL.
     */
    public String getLoginUrl() {
        return loginUrl;
    }

    private void basicValidation() throws IDPRequestHandlerException {

        // Ensures that we have all the values we need in order to perform an IDP request.
        if (spConfig == null || TextUtils.isEmpty(spConfig.getOauthClientId()) ||
                TextUtils.isEmpty(spConfig.getOauthCallbackUrl()) ||
                (spConfig.getOauthScopes() == null) ||
                TextUtils.isEmpty(spConfig.getCodeChallenge())) {
            handleError("Incomplete SP app configuration - missing fields", null);
        }
        if (userAccount == null) {
            handleError("Invalid user configuration - null user account", null);
        }
    }

    private void buildRestClient() {
        final Context context = SalesforceSDKManager.getInstance().getAppContext();
        final BootConfig bootConfig = BootConfig.getBootConfig(context);
        final String idpCallbackUrl = bootConfig.getOauthRedirectURI();
        final String idpClientId = bootConfig.getRemoteAccessConsumerKey();
        final String[] idpScopes = bootConfig.getOauthScopes();
        final ClientManager.LoginOptions loginOptions = new ClientManager.LoginOptions(
                loginUrl, idpCallbackUrl, idpClientId, idpScopes);
        final String idpAccountType = SalesforceSDKManager.getInstance().getAccountType();
        final ClientManager clientManager = new ClientManager(context, idpAccountType,
                loginOptions, false);
        restClient = clientManager.peekRestClient(userAccount);
    }

    private void handleError(String errorMessage, Throwable e) throws IDPRequestHandlerException {
        SalesforceSDKLogger.e(TAG, "Exception thrown: " + errorMessage, e);
        throw new IDPRequestHandlerException(errorMessage, e);
    }

    /**
     * Exception thrown for any error case with the IDP authentication flow.
     *
     * @author bhariharan
     */
    public static class IDPRequestHandlerException extends Exception {

        public IDPRequestHandlerException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
