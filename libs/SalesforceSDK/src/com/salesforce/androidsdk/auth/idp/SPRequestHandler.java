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

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.auth.OAuth2;
import com.salesforce.androidsdk.config.BootConfig;
import com.salesforce.androidsdk.security.SalesforceKeyGenerator;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import java.net.URI;

/**
 * This class performs requests made by the SP app based on the IDP app's response.
 *
 * @author bhariharan
 */
public class SPRequestHandler {

    public static final int IDP_REQUEST_CODE = 375;
    private static final String CODE_CHALLENGE_KEY = "code_challenge_key";
    private static final String TAG = "SPRequestHandler";

    private String codeVerifier;
    private String codeChallenge;
    private String loginUrl;
    private SPConfig spConfig;

    /**
     * Parameterized constructor.
     *
     * @param loginUrl Login URL.
     */
    public SPRequestHandler(String loginUrl) {
        codeVerifier = SalesforceKeyGenerator.getEncryptionKey(CODE_CHALLENGE_KEY);
        codeChallenge = SalesforceKeyGenerator.getUniqueId(CODE_CHALLENGE_KEY);
        this.loginUrl = loginUrl;
        spConfig = buildSPConfig();
    }

    /**
     * Launches the IDP app.
     */
    public void launchIDPApp() {
        /*
         * TODO: Launch the subclass of user switcher screen for IDP that selects user.
         * This user should take the SPConfig extra coming from here and add the selected
         * UserAccount extra to it and then launch IDCodeGeneratorActivity.
         */
    }

    /**
     * Handles the response from the IDP app.
     *
     * @param resultCode Result code.
     * @param data Data returned from the IDP app.
     */
    public void handleIDPResponse(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_CANCELED) {
            final String error = data.getStringExtra(IDPCodeGeneratorActivity.ERROR_KEY);
            handleError(error);
        } else if (resultCode == Activity.RESULT_OK) {
            final String code = data.getStringExtra(IDPCodeGeneratorActivity.CODE_KEY);
            final String loginUrl = data.getStringExtra(IDPCodeGeneratorActivity.LOGIN_URL_KEY);
            handleSuccess(code, loginUrl);
        }
    }

    /**
     * Returns the SP config associated with this app.
     *
     * @return SPConfig instance.
     */
    public SPConfig getSpConfig() {
        return spConfig;
    }

    private SPConfig buildSPConfig() {
        final BootConfig bootConfig = BootConfig.getBootConfig(SalesforceSDKManager.getInstance().getAppContext());
        return new SPConfig(bootConfig.getRemoteAccessConsumerKey(), bootConfig.getOauthRedirectURI(),
                codeChallenge, bootConfig.getOauthScopes(), loginUrl, null);
    }

    private void handleError(String error) {
        SalesforceSDKLogger.e(TAG, "Error received from IDP app: " + error);
    }

    private void handleSuccess(String code, String loginUrl) {
        /*
         * TODO: Take the code and login URL that's returned and make a token endpoint
         * request followed by the identity URL request and complete the rest of the
         * login flow (similar to the completion steps in LoginActivity and OAuthWebViewHelper.
         */

        new TokenEndpointTask(code, loginUrl).execute();
    }

    private class TokenEndpointTask extends AsyncTask<Void, Void, OAuth2.TokenEndpointResponse> {

        private String code;
        private String loginUrl;

        public TokenEndpointTask(String code, String loginUrl) {
            this.code = code;
            this.loginUrl = loginUrl;
        }

        @Override
        protected OAuth2.TokenEndpointResponse doInBackground(Void... nothings) {
            OAuth2.TokenEndpointResponse tokenResponse = null;
            try {
                tokenResponse = OAuth2.getSPCredentials(HttpAccess.DEFAULT,
                        URI.create(loginUrl), spConfig.getOauthClientId(), code, codeVerifier,
                        spConfig.getOauthCallbackUrl());
            } catch (Exception e) {
                SalesforceSDKLogger.e(TAG, "Exception occurred while making token request", e);
            }
            return tokenResponse;
        }

        @Override
        protected void onPostExecute(OAuth2.TokenEndpointResponse tokenResponse) {
            Log.e("*******************", "Response");
        }
    }
}
