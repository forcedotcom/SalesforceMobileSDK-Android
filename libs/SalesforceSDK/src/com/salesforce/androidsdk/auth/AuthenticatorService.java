/*
 * Copyright (c) 2011-present, salesforce.com, inc.
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

import static android.accounts.AccountManager.KEY_ACCOUNT_NAME;
import static android.accounts.AccountManager.KEY_ACCOUNT_TYPE;
import static android.accounts.AccountManager.KEY_AUTHTOKEN;

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

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.OAuth2.OAuthFailedException;
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The service used for taking care of authentication for a Salesforce-based application.
 * See {@link <a href="http://developer.android.com/reference/android/accounts/AbstractAccountAuthenticator.html">AbstractAccountAuthenticator</a>}.
 */
public class AuthenticatorService extends Service {

    private static Authenticator AUTHENTICATOR;

    // Keys to extra info in the account.
    public static final String KEY_LOGIN_URL = "loginUrl";
    public static final String KEY_INSTANCE_URL = "instanceUrl";
    public static final String KEY_USER_ID = "userId";
    public static final String KEY_CLIENT_ID = "clientId";
    public static final String KEY_ORG_ID = "orgId";
    public static final String KEY_USERNAME = "username";
    public static final String KEY_ID_URL = "id";
    public static final String KEY_COMMUNITY_ID = "communityId";
    public static final String KEY_COMMUNITY_URL = "communityUrl";
    public static final String KEY_EMAIL = "email";
    public static final String KEY_FIRST_NAME = "first_name";
    public static final String KEY_LAST_NAME = "last_name";
    public static final String KEY_DISPLAY_NAME = "display_name";
    public static final String KEY_PHOTO_URL = "photoUrl";
    public static final String KEY_THUMBNAIL_URL = "thumbnailUrl";
    public static final String KEY_LIGHTNING_DOMAIN = "lightningDomain";
    public static final String KEY_LIGHTNING_SID = "lightningSid";
    public static final String KEY_VF_DOMAIN = "vfDomain";
    public static final String KEY_VF_SID = "vfSid";
    public static final String KEY_CONTENT_DOMAIN = "contentDomain";
    public static final String KEY_CONTENT_SID = "contentSid";
    public static final String KEY_CSRF_TOKEN = "csrfToken";
    public static final String KEY_NATIVE_LOGIN = "nativeLogin";
    public static final String KEY_LANGUAGE = "language";
    public static final String KEY_LOCALE = "locale";
    public static final String KEY_COOKIE_CLIENT_SRC = "cookie-clientSrc";
    public static final String KEY_COOKIE_SID_CLIENT = "cookie-sid_Client";
    public static final String KEY_SID_COOKIE_NAME = "sidCookieName";

    private static final String TAG = "AuthenticatorService";

    private Authenticator getAuthenticator() {
        if (AUTHENTICATOR == null) {
            AUTHENTICATOR = new Authenticator(this);
        }
        return AUTHENTICATOR;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (AccountManager.ACTION_AUTHENTICATOR_INTENT.equals(intent.getAction())) {
            return getAuthenticator().getIBinder();
        }
        return null;
    }

    private static class Authenticator extends AbstractAccountAuthenticator {

    	private static final String SETTINGS_PACKAGE_NAME = "com.android.settings";
    	private static final String ANDROID_PACKAGE_NAME = "androidPackageName";

        private final Context context;

        Authenticator(Context ctx) {
            super(ctx);
            this.context = ctx;
        }

        @Override
        public Bundle addAccount(AccountAuthenticatorResponse response,
                        String accountType,
                        String authTokenType,
                        String[] requiredFeatures,
                        Bundle options) {
        	if (isAddFromSettings(options)) {
        		options.putAll(SalesforceSDKManager.getInstance().getLoginOptions().asBundle());
        	}
        	return makeAuthIntentBundle(response, options);
        }

        private boolean isAddFromSettings(Bundle options) {
			return options.containsKey(ANDROID_PACKAGE_NAME)
                    && SETTINGS_PACKAGE_NAME.equals(options.getString(ANDROID_PACKAGE_NAME));
		}

        @Override
        public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account,
                            String authTokenType, Bundle options) throws NetworkErrorException {
            final AccountManager mgr = AccountManager.get(context);
            final String encryptionKey = SalesforceSDKManager.getEncryptionKey();
            final String refreshToken = SalesforceSDKManager.decrypt(mgr.getPassword(account), encryptionKey);
            final String loginServer = decryptUserData(mgr, account, AuthenticatorService.KEY_LOGIN_URL, encryptionKey);
            final String clientId = decryptUserData(mgr, account, AuthenticatorService.KEY_CLIENT_ID, encryptionKey);
            final String username = decryptUserData(mgr, account, AuthenticatorService.KEY_USERNAME, encryptionKey);
            final String lastName = decryptUserData(mgr, account, AuthenticatorService.KEY_LAST_NAME, encryptionKey);
            final String email = decryptUserData(mgr, account, AuthenticatorService.KEY_EMAIL, encryptionKey);
            final String language = decryptUserData(mgr, account, AuthenticatorService.KEY_LANGUAGE, encryptionKey);
            final String locale = decryptUserData(mgr, account, AuthenticatorService.KEY_LOCALE, encryptionKey);
            final String firstName = decryptUserData(mgr, account, AuthenticatorService.KEY_FIRST_NAME, encryptionKey);
            final String displayName = decryptUserData(mgr, account, AuthenticatorService.KEY_DISPLAY_NAME, encryptionKey);
            final String photoUrl = decryptUserData(mgr, account, AuthenticatorService.KEY_PHOTO_URL, encryptionKey);
            final String thumbnailUrl = decryptUserData(mgr, account, AuthenticatorService.KEY_THUMBNAIL_URL, encryptionKey);

            final List<String> additionalOauthKeys = SalesforceSDKManager.getInstance().getAdditionalOauthKeys();
            Map<String, String> values = null;
            if (additionalOauthKeys != null && !additionalOauthKeys.isEmpty()) {
                values = new HashMap<>();
                for (final String key : additionalOauthKeys) {
                    values.put(key, decryptUserData(mgr, account, key, encryptionKey));
                }
            }

            final Map<String,String> addlParamsMap = SalesforceSDKManager.getInstance().getLoginOptions().getAdditionalParameters();
            final Bundle resBundle = new Bundle();
            try {
                final TokenEndpointResponse tr = OAuth2.refreshAuthToken(HttpAccess.DEFAULT,
                        new URI(loginServer), clientId, refreshToken, addlParamsMap);

                encryptUserData(mgr, account, resBundle, KEY_ACCOUNT_NAME, account.name, encryptionKey);
                encryptUserData(mgr, account, resBundle, KEY_ACCOUNT_TYPE, account.type, encryptionKey);
                encryptUserData(mgr, account, resBundle, KEY_AUTHTOKEN, tr.authToken, encryptionKey);
                encryptUserData(mgr, account, resBundle, AuthenticatorService.KEY_LOGIN_URL, loginServer, encryptionKey);
                encryptUserData(mgr, account, resBundle, AuthenticatorService.KEY_INSTANCE_URL, tr.instanceUrl, encryptionKey);
                encryptUserData(mgr, account, resBundle, AuthenticatorService.KEY_CLIENT_ID, clientId, encryptionKey);
                encryptUserData(mgr, account, resBundle, AuthenticatorService.KEY_USERNAME, username, encryptionKey);
                encryptUserData(mgr, account, resBundle, AuthenticatorService.KEY_USER_ID, tr.userId, encryptionKey);
                encryptUserData(mgr, account, resBundle, AuthenticatorService.KEY_ORG_ID, tr.orgId, encryptionKey);
                encryptUserData(mgr, account, resBundle, AuthenticatorService.KEY_LAST_NAME, lastName, encryptionKey);
                encryptUserData(mgr, account, resBundle, AuthenticatorService.KEY_EMAIL, email, encryptionKey);
                encryptUserData(mgr, account, resBundle, AuthenticatorService.KEY_LANGUAGE, language, encryptionKey);
                encryptUserData(mgr, account, resBundle, AuthenticatorService.KEY_LOCALE, locale, encryptionKey);
                encryptUserData(mgr, account, resBundle, AuthenticatorService.KEY_FIRST_NAME, firstName, encryptionKey);
                encryptUserData(mgr, account, resBundle, AuthenticatorService.KEY_DISPLAY_NAME, displayName, encryptionKey);
                encryptUserData(mgr, account, resBundle, AuthenticatorService.KEY_PHOTO_URL, photoUrl, encryptionKey);
                encryptUserData(mgr, account, resBundle, AuthenticatorService.KEY_THUMBNAIL_URL, thumbnailUrl, encryptionKey);
                encryptUserData(mgr, account, resBundle, AuthenticatorService.KEY_LIGHTNING_DOMAIN, tr.lightningDomain, encryptionKey);
                encryptUserData(mgr, account, resBundle, AuthenticatorService.KEY_LIGHTNING_SID, tr.lightningSid, encryptionKey);
                encryptUserData(mgr, account, resBundle, AuthenticatorService.KEY_VF_DOMAIN, tr.vfDomain, encryptionKey);
                encryptUserData(mgr, account, resBundle, AuthenticatorService.KEY_VF_SID, tr.vfSid, encryptionKey);
                encryptUserData(mgr, account, resBundle, AuthenticatorService.KEY_CONTENT_DOMAIN, tr.contentDomain, encryptionKey);
                encryptUserData(mgr, account, resBundle, AuthenticatorService.KEY_CONTENT_SID, tr.contentSid, encryptionKey);
                encryptUserData(mgr, account, resBundle, AuthenticatorService.KEY_CSRF_TOKEN, tr.csrfToken, encryptionKey);
                encryptUserData(mgr, account, resBundle, AuthenticatorService.KEY_COOKIE_CLIENT_SRC, tr.cookieClientSrc, encryptionKey );
                encryptUserData(mgr, account, resBundle, AuthenticatorService.KEY_SID_COOKIE_NAME, tr.sidCookieName, encryptionKey);
                encryptUserData(mgr, account, resBundle, AuthenticatorService.KEY_COMMUNITY_ID, tr.communityId, encryptionKey);
                encryptUserData(mgr, account, resBundle, AuthenticatorService.KEY_COMMUNITY_URL, tr.communityUrl, encryptionKey);

                /*
                 * Checks if the additional OAuth keys have new values returned after a token
                 * refresh. If so, update the values stored with the new ones. If not, fall back
                 * on the existing values stored.
                 */
                if (additionalOauthKeys != null && !additionalOauthKeys.isEmpty()) {
                    for (final String key : additionalOauthKeys) {
                        if (tr.additionalOauthValues != null && tr.additionalOauthValues.containsKey(key)) {
                            final String newValue = tr.additionalOauthValues.get(key);
                            if (newValue != null) {
                                encryptUserData(mgr, account, resBundle, key, newValue, encryptionKey);
                            }
                        } else if (values != null && values.containsKey(key)) {
                            final String value = values.get(key);
                            if (value != null) {
                                encryptUserData(mgr, account, resBundle, key, value, encryptionKey);
                            }
                        }
                    }
                }

            } catch (OAuthFailedException ofe) {
                if (ofe.isRefreshTokenInvalid()) {
                    SalesforceSDKLogger.i(TAG, "Invalid Refresh Token: (Error: " +
                            ofe.response.error + ", Status Code: " + ofe.httpStatusCode + ")", ofe);
                    return makeAuthIntentBundle(response, options);
                }
                resBundle.putString(AccountManager.KEY_ERROR_CODE, ofe.response.error);
                resBundle.putString(AccountManager.KEY_ERROR_MESSAGE, ofe.response.errorDescription);
            } catch (Exception e) {
                SalesforceSDKLogger.w(TAG, "Exception thrown while getting new auth token", e);
                throw new NetworkErrorException(e);
            }
            return resBundle;
        }

        /**
         * Encrypt the given data
         * Store it in the account manager and add it to the resBundle passed in
         *
         * @param mgr
         * @param account
         * @param resBundle
         * @param key
         * @param data
         * @param encryptionKey
         */
        private void encryptUserData(AccountManager mgr, Account account, Bundle resBundle, String key, String data, String encryptionKey) {
            String encData = SalesforceSDKManager.encrypt(data, encryptionKey);
            mgr.setUserData(account, key, encData);
            resBundle.putString(key, encData);
        }

        /**
         * Decrypt the user data from the account for the given key
         *
         * @param mgr
         * @param account
         * @param key
         * @param encryptionKey
         * @return
         */
        private String decryptUserData(AccountManager mgr, Account account, String key, String encryptionKey) {
            return  SalesforceSDKManager.decrypt(mgr.getUserData(account, key), encryptionKey);
        }

        private Bundle makeAuthIntentBundle(AccountAuthenticatorResponse response, Bundle options) {
            final Bundle reply = new Bundle();
            final Intent i = new Intent(context, SalesforceSDKManager.getInstance().getLoginActivityClass());
            i.setPackage(context.getPackageName());
            i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            i.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
            if (options != null) {
                i.putExtras(options);
            }
            reply.putParcelable(AccountManager.KEY_INTENT, i);
            return reply;
        }

        @Override
        public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account,
                                        String authTokenType, Bundle options) {
            return null;
        }

        @Override
        public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account,
                                         Bundle options) {
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
        public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account,
                                  String[] features) {
            return null;
        }
    }
}
