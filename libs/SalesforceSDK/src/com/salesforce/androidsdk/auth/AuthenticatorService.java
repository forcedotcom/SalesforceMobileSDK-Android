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

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountBuilder;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.OAuth2.OAuthFailedException;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import java.net.URI;
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

            UserAccount originalUserAccount = UserAccountManager.getInstance().buildUserAccount(account);
            final Map<String,String> addlParamsMap = SalesforceSDKManager.getInstance().getLoginOptions().getAdditionalParameters();
            try {
                final OAuth2.TokenEndpointResponse tr = OAuth2.refreshAuthToken(HttpAccess.DEFAULT,
                        new URI(originalUserAccount.getLoginServer()), originalUserAccount.getClientId(), originalUserAccount.getRefreshToken(), addlParamsMap);

                UserAccount updatedUserAccount = UserAccountBuilder.getInstance()
                        .populateFromUserAccount(originalUserAccount)
                        .allowUnset(false)
                        .populateFromTokenEndpointResponse(tr)
                        .build();

                Bundle resBundle = UserAccountManager.getInstance().updateAccount(account, updatedUserAccount);
                updatedUserAccount.downloadProfilePhoto();
                UserAccountManager.getInstance().clearCachedCurrentUser();

                return resBundle;
            } catch (OAuthFailedException ofe) {
                if (ofe.isRefreshTokenInvalid()) {
                    SalesforceSDKLogger.i(TAG, "Invalid Refresh Token: (Error: " +
                            ofe.response.error + ", Status Code: " + ofe.httpStatusCode + ")", ofe);
                    return makeAuthIntentBundle(response, options);
                }

                Bundle resBundle = new Bundle();
                resBundle.putString(AccountManager.KEY_ERROR_CODE, ofe.response.error);
                resBundle.putString(AccountManager.KEY_ERROR_MESSAGE, ofe.response.errorDescription);
                return resBundle;

            } catch (Exception e) {
                SalesforceSDKLogger.w(TAG, "Exception thrown while getting new auth token", e);
                throw new NetworkErrorException(e);
            }
        }

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
