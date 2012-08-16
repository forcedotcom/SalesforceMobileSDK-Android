/*
 * Copyright (c) 2011, salesforce.com, inc.
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
package com.salesforce.androidsdk.auth;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.client.ClientProtocolException;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.auth.OAuth2.OAuthFailedException;
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;

/**
 * The service used for taking care of authentication for a Salesforce-based application.
 * See {@link <a href="http://developer.android.com/reference/android/accounts/AbstractAccountAuthenticator.html">AbstractAccountAuthenticator</a>}.
 */
public class AuthenticatorService extends Service {

    private static Authenticator authenticator;

    // Keys to extra info in the account
    public static final String KEY_LOGIN_URL = "loginUrl";
    public static final String KEY_INSTANCE_URL = "instanceUrl";
    public static final String KEY_USER_ID = "userId";
    public static final String KEY_CLIENT_ID = "clientId";
    public static final String KEY_ORG_ID = "orgId";
    public static final String KEY_USERNAME = "username";
    public static final String KEY_ID_URL = "id";
    public static final String KEY_CLIENT_SECRET = "clientSecret";

    private Authenticator getAuthenticator() {
        if (authenticator == null)
            authenticator = new Authenticator(this);
        return authenticator;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (intent.getAction().equals(AccountManager.ACTION_AUTHENTICATOR_INTENT))
            return getAuthenticator().getIBinder();
        return null;
    }

    /**
     * The Authenticator for salesforce accounts.
     * - addAccount Start the login flow (by launching the activity filtering the salesforce.intent.action.LOGIN intent).
     * - getAuthToken Refresh the token by calling {@link OAuth2#refreshAuthToken(HttpAccess, URI, String, String) OAuth2.refreshAuthToken}.
     */
    private static class Authenticator extends AbstractAccountAuthenticator {

        private final Context context;

        Authenticator(Context ctx) {
            super(ctx);
            this.context = ctx;
        }

        @Override
        public Bundle addAccount(
                        AccountAuthenticatorResponse response,
                        String accountType,
                        String authTokenType,
                        String[] requiredFeatures,
                        Bundle options)
                throws NetworkErrorException {
            // Log.i("Authenticator:addAccount", "Options: " + options);
            return makeAuthIntentBundle(response, options);
        }

        /**
         * Uses the refresh token to get a new access token.
         * Remember that the authenticator runs under its own separate process, so if you want to debug you
         * need to attach to the :auth process, and not the main chatter process.
         */
        @Override
        public Bundle getAuthToken(
                            AccountAuthenticatorResponse response,
                            Account account,
                            String authTokenType,
                            Bundle options) throws NetworkErrorException {
            // Log.i("Authenticator:getAuthToken", "Get auth token for " + account.name);
            final AccountManager mgr = AccountManager.get(context);
            final String passcodeHash = LoginOptions.fromBundle(options).passcodeHash;
            final String refreshToken = ForceApp.decryptWithPasscode(mgr.getPassword(account), passcodeHash);
            final String loginServer = ForceApp.decryptWithPasscode(mgr.getUserData(account, AuthenticatorService.KEY_LOGIN_URL), passcodeHash);
            final String clientId = ForceApp.decryptWithPasscode(mgr.getUserData(account, AuthenticatorService.KEY_CLIENT_ID), passcodeHash);
            final String instServer = ForceApp.decryptWithPasscode(mgr.getUserData(account, AuthenticatorService.KEY_INSTANCE_URL), passcodeHash);
            final String userId = ForceApp.decryptWithPasscode(mgr.getUserData(account, AuthenticatorService.KEY_USER_ID), passcodeHash);
            final String orgId = ForceApp.decryptWithPasscode(mgr.getUserData(account, AuthenticatorService.KEY_ORG_ID), passcodeHash);
            final String username = ForceApp.decryptWithPasscode(mgr.getUserData(account, AuthenticatorService.KEY_USERNAME), passcodeHash);
            final String encClientSecret = mgr.getUserData(account, AuthenticatorService.KEY_CLIENT_SECRET);
            String clientSecret = null;
            if (encClientSecret != null) {
                clientSecret = ForceApp.decryptWithPasscode(encClientSecret, passcodeHash);
            }
            final Bundle resBundle = new Bundle();
            try {
                final TokenEndpointResponse tr = OAuth2.refreshAuthToken(HttpAccess.DEFAULT, new URI(loginServer), clientId, refreshToken, clientSecret);

                // Handle the case where the org has been migrated to a new instance, or has turned on my domains.
                if (!instServer.equalsIgnoreCase(tr.instanceUrl)) {
                    mgr.setUserData(account, AuthenticatorService.KEY_INSTANCE_URL, ForceApp.encryptWithPasscode(tr.instanceUrl, passcodeHash));
                }

                // Update auth token in account.
                mgr.setUserData(account, AccountManager.KEY_AUTHTOKEN, ForceApp.encryptWithPasscode(tr.authToken, passcodeHash));
                resBundle.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
                resBundle.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
                resBundle.putString(AccountManager.KEY_AUTHTOKEN, tr.authToken);
                resBundle.putString(AuthenticatorService.KEY_LOGIN_URL, ForceApp.encryptWithPasscode(loginServer, passcodeHash));
                resBundle.putString(AuthenticatorService.KEY_INSTANCE_URL, ForceApp.encryptWithPasscode(instServer, passcodeHash));
                resBundle.putString(AuthenticatorService.KEY_CLIENT_ID, ForceApp.encryptWithPasscode(clientId, passcodeHash));
                resBundle.putString(AuthenticatorService.KEY_USERNAME, ForceApp.encryptWithPasscode(username, passcodeHash));
                resBundle.putString(AuthenticatorService.KEY_USER_ID, ForceApp.encryptWithPasscode(userId, passcodeHash));
                resBundle.putString(AuthenticatorService.KEY_ORG_ID, ForceApp.encryptWithPasscode(orgId, passcodeHash));
                String encrClientSecret = null;
                if (clientSecret != null) {
                    encrClientSecret = ForceApp.encryptWithPasscode(clientSecret, passcodeHash);
                }
                resBundle.putString(AuthenticatorService.KEY_CLIENT_SECRET, encrClientSecret);
                // Log.i("Authenticator:getAuthToken", "Returning auth bundle for " + account.name);
            } catch (ClientProtocolException e) {
                Log.w("Authenticator:getAuthToken", "", e);
                throw new NetworkErrorException(e);
            } catch (IOException e) {
                Log.w("Authenticator:getAuthToken", "", e);
                throw new NetworkErrorException(e);
            } catch (URISyntaxException e) {
                Log.w("Authenticator:getAuthToken", "", e);
                throw new NetworkErrorException(e);
            } catch (OAuthFailedException e) {
                if (e.isRefreshTokenInvalid()) {
                    // the exception explicitly indicates that the refresh token is no longer valid.
                    return makeAuthIntentBundle(response, options);
                }
                resBundle.putString(AccountManager.KEY_ERROR_CODE, e.response.error);
                resBundle.putString(AccountManager.KEY_ERROR_MESSAGE, e.response.errorDescription);
            }
            // Log.i("Authenticator:getAuthToken", "Result: " + resBundle);
            return resBundle;
        }

        /**
         * Return bundle with intent to start the login flow.
         *
         * @param response
         * @param options
         * @return
         */
        private Bundle makeAuthIntentBundle(AccountAuthenticatorResponse response, Bundle options) {
            Bundle reply = new Bundle();
            Intent i = new Intent(context, ForceApp.APP.getLoginActivityClass());
            i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            i.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
            if (options != null)
                i.putExtras(options);
            reply.putParcelable(AccountManager.KEY_INTENT, i);
            return reply;
        }

        @Override
        public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options) throws NetworkErrorException {
            return null;
        }

        @Override
        public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options) throws NetworkErrorException {
            return null;
        }

        @Override
        public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
            return null;
        }

        @Override
        public String getAuthTokenLabel(String authTokenType) {
            return null;
        }

        @Override
        public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features) throws NetworkErrorException {
            return null;
        }
    }
}
