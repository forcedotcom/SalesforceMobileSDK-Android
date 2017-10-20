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

import android.os.Bundle;

import com.salesforce.androidsdk.auth.OAuth2;

/**
 * This class performs represents an SP app's config.
 *
 * @author bhariharan
 */
public class SPConfig {

    private static final String OAUTH_CLIENT_ID_KEY = "oauth_client_id";
    private static final String OAUTH_CALLBACK_URL_KEY = "oauth_callback_url";
    private static final String CODE_CHALLENGE_KEY = "code_challenge";
    private static final String OAUTH_SCOPES_KEY = "oauth_scopes";
    private static final String LOGIN_URL_KEY = "login_url";
    private static final String USER_HINT_KEY = "user_hint";
    private static final String COMPUTED_SCOPE_PARAMETER_KEY = "computed_scope_parameter";

    private String oauthClientId;
    private String oauthCallbackUrl;
    private String codeChallenge;
    private String[] oauthScopes;
    private String loginUrl;
    private String userHint;
    private String computedScopeParameter;

    /**
     * Parameterized constructor.
     *
     * @param oauthClientId OAuth client ID.
     * @param oauthCallbackUrl OAuth callback URL.
     * @param codeChallenge Code challenge.
     * @param oauthScopes OAuth scopes.
     * @param loginUrl Login URL.
     * @param userHint User hint.
     */
    public SPConfig(String oauthClientId, String oauthCallbackUrl, String codeChallenge,
                    String[] oauthScopes, String loginUrl, String userHint) {
        this.oauthClientId = oauthClientId;
        this.oauthCallbackUrl = oauthCallbackUrl;
        this.codeChallenge = codeChallenge;
        this.oauthScopes = oauthScopes;
        this.loginUrl = loginUrl;
        this.userHint = userHint;
        computedScopeParameter = OAuth2.computeScopeParameter(oauthScopes);
    }

    /**
     * Parameterized constructor for internal use only.
     *
     * @param config Bundle containing config.
     */
    SPConfig(Bundle config) {
        oauthClientId = config.getString(OAUTH_CLIENT_ID_KEY);
        oauthCallbackUrl = config.getString(OAUTH_CALLBACK_URL_KEY);
        codeChallenge = config.getString(CODE_CHALLENGE_KEY);
        oauthScopes = config.getStringArray(OAUTH_SCOPES_KEY);
        loginUrl = config.getString(LOGIN_URL_KEY);
        userHint = config.getString(USER_HINT_KEY);
        computedScopeParameter = config.getString(COMPUTED_SCOPE_PARAMETER_KEY);
    }

    /**
     * Returns the bundle representation of this class.
     *
     * @return Bundle representation.
     */
    public Bundle toBundle() {
        final Bundle bundle = new Bundle();
        bundle.putString(OAUTH_CLIENT_ID_KEY, oauthClientId);
        bundle.putString(OAUTH_CALLBACK_URL_KEY, oauthCallbackUrl);
        bundle.putString(CODE_CHALLENGE_KEY, codeChallenge);
        bundle.putStringArray(OAUTH_SCOPES_KEY, oauthScopes);
        bundle.putString(LOGIN_URL_KEY, loginUrl);
        bundle.putString(USER_HINT_KEY, userHint);
        bundle.putString(COMPUTED_SCOPE_PARAMETER_KEY, computedScopeParameter);
        return bundle;
    }

    /**
     * Returns the OAuth client ID for the SP app.
     *
     * @return OAuth client ID.
     */
    public String getOauthClientId() {
        return oauthClientId;
    }

    /**
     * Returns the OAuth callback URL for the SP app.
     *
     * @return OAuth callback URL.
     */
    public String getOauthCallbackUrl() {
        return oauthCallbackUrl;
    }

    /**
     * Returns the code challenge for the SP app.
     *
     * @return Code challenge.
     */
    public String getCodeChallenge() {
        return codeChallenge;
    }

    /**
     * Returns the OAuth scopes for the SP app.
     *
     * @return OAuth scopes.
     */
    public String[] getOauthScopes() {
        return oauthScopes;
    }

    /**
     * Returns the login URL for the SP app.
     *
     * @return Login URL.
     */
    public String getLoginUrl() {
        return loginUrl;
    }

    /**
     * Returns the user hint for the SP app.
     *
     * @return User hint.
     */
    public String getUserHint() {
        return userHint;
    }

    /**
     * Returns the computed scope parameter for the SP app.
     *
     * @return Computed scope parameter.
     */
    public String getComputedScopeParameter() {
        return computedScopeParameter;
    }
}
