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
import android.app.ActivityManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.OAuth2.OAuthFailedException;
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public static final String KEY_COMMUNITY_ID = "communityId";
    public static final String KEY_COMMUNITY_URL = "communityUrl";
    public static final String KEY_EMAIL = "email";
    public static final String KEY_FIRST_NAME = "first_name";
    public static final String KEY_LAST_NAME = "last_name";
    public static final String KEY_DISPLAY_NAME = "display_name";
    public static final String KEY_PHOTO_URL = "photoUrl";
    public static final String KEY_THUMBNAIL_URL = "thumbnailUrl";

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

    	private static final String SETTINGS_PACKAGE_NAME = "com.android.settings";
    	private static final String ANDROID_PACKAGE_NAME = "androidPackageName";

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
        	if (isAddFromSettings(options)) {
        		options.putAll(SalesforceSDKManager.getInstance().getLoginOptions().asBundle());
        	}
        	return makeAuthIntentBundle(response, options);
        }

        private boolean isAddFromSettings(Bundle options) {
			// Is there a better way? 
        	return options.containsKey(ANDROID_PACKAGE_NAME) && options.getString(ANDROID_PACKAGE_NAME).equals(SETTINGS_PACKAGE_NAME);
		}

        @SuppressWarnings("deprecation")
		@Override
        public Bundle getAccountRemovalAllowed(AccountAuthenticatorResponse response, Account account) {
            final Bundle result = new Bundle();
            final ActivityManager manager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);

            /*
             * Allowing account removal from the Settings app is quite messy,
             * since we don't know which account is being removed. Hence, we
             * check which package the account removal call is coming from,
             * and decide whether to allow it or not. Unfortunately, the only
             * way to do this is the convoluted way used below, which basically
             * gets a list of running tasks and get the topmost activity on
             * the task in focus. If the call is coming from the Settings app,
             * the topmost activity's package will be the Settings app.
             *
             * FIXME: The following piece of code does nothing on Lollipop and
             * above, since Google has revoked the ability to get the list of
             * running tasks outside of the application stack. We'll need to
             * figure out a different strategy to handle this. One approach
             * is to launch a custom logout flow for 'Settings' (if that's possible).
             */
            boolean isNotRemoveFromSettings = true;
            if (manager != null) {
                final List<ActivityManager.RunningTaskInfo> task = manager.getRunningTasks(1);
                if (task != null && task.size() > 0) {
                    final ComponentName componentInfo = task.get(0).topActivity;
                    if (componentInfo != null) {
                        if (SETTINGS_PACKAGE_NAME.equals(componentInfo.getPackageName())) {
                            isNotRemoveFromSettings = false;
                        }
                    }
                }
            }
            result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, isNotRemoveFromSettings);
            return result;
        }

		/**
         * Uses the refresh token to get a new access token.
         */
        @Override
        public Bundle getAuthToken(
                            AccountAuthenticatorResponse response,
                            Account account,
                            String authTokenType,
                            Bundle options) throws NetworkErrorException {
            final String TAG = "Auth..Ser..:getAuthT..";
            final AccountManager mgr = AccountManager.get(context);
            final String passcodeHash = SalesforceSDKManager.getInstance().getPasscodeHash();
            final String refreshToken = SalesforceSDKManager.decryptWithPasscode(mgr.getPassword(account), passcodeHash);
            final String loginServer = SalesforceSDKManager.decryptWithPasscode(mgr.getUserData(account, AuthenticatorService.KEY_LOGIN_URL), passcodeHash);
            final String clientId = SalesforceSDKManager.decryptWithPasscode(mgr.getUserData(account, AuthenticatorService.KEY_CLIENT_ID), passcodeHash);
            final String instServer = SalesforceSDKManager.decryptWithPasscode(mgr.getUserData(account, AuthenticatorService.KEY_INSTANCE_URL), passcodeHash);
            final String userId = SalesforceSDKManager.decryptWithPasscode(mgr.getUserData(account, AuthenticatorService.KEY_USER_ID), passcodeHash);
            final String orgId = SalesforceSDKManager.decryptWithPasscode(mgr.getUserData(account, AuthenticatorService.KEY_ORG_ID), passcodeHash);
            final String username = SalesforceSDKManager.decryptWithPasscode(mgr.getUserData(account, AuthenticatorService.KEY_USERNAME), passcodeHash);
            final String lastName = SalesforceSDKManager.decryptWithPasscode(mgr.getUserData(account, AuthenticatorService.KEY_LAST_NAME), passcodeHash);
            final String email = SalesforceSDKManager.decryptWithPasscode(mgr.getUserData(account, AuthenticatorService.KEY_EMAIL), passcodeHash);
            final String encFirstName = mgr.getUserData(account, AuthenticatorService.KEY_FIRST_NAME);
            String firstName = null;
            if (encFirstName != null) {
                 firstName = SalesforceSDKManager.decryptWithPasscode(encFirstName, passcodeHash);
            }
            final String encDisplayName = mgr.getUserData(account, AuthenticatorService.KEY_DISPLAY_NAME);
            String displayName = null;
            if (encDisplayName != null) {
                displayName = SalesforceSDKManager.decryptWithPasscode(encDisplayName, passcodeHash);
            }
            final String encPhotoUrl = mgr.getUserData(account, AuthenticatorService.KEY_PHOTO_URL);
            String photoUrl = null;
            if (encPhotoUrl != null) {
                photoUrl = SalesforceSDKManager.decryptWithPasscode(encPhotoUrl, passcodeHash);
            }
            final String encThumbnailUrl = mgr.getUserData(account, AuthenticatorService.KEY_THUMBNAIL_URL);
            String thumbnailUrl = null;
            if (encThumbnailUrl != null) {
                thumbnailUrl = SalesforceSDKManager.decryptWithPasscode(encThumbnailUrl, passcodeHash);
            }
            final String encClientSecret = mgr.getUserData(account, AuthenticatorService.KEY_CLIENT_SECRET);
            String clientSecret = null;
            if (encClientSecret != null) {
                clientSecret = SalesforceSDKManager.decryptWithPasscode(encClientSecret, passcodeHash);
            }
            final List<String> additionalOauthKeys = SalesforceSDKManager.getInstance().getAdditionalOauthKeys();
            Map<String, String> values = null;
            if (additionalOauthKeys != null && !additionalOauthKeys.isEmpty()) {
                values = new HashMap<>();
                for (final String key : additionalOauthKeys) {
                    final String encValue = mgr.getUserData(account, key);
                    if (encValue != null) {
                        final String value = SalesforceSDKManager.decryptWithPasscode(encValue,
                                SalesforceSDKManager.getInstance().getPasscodeHash());
                        values.put(key, value);
                    }
                }
            }
            Map<String,String> addlParamsMap = SalesforceSDKManager.getInstance().getLoginOptions().getAdditionalParameters();

            final String encCommunityId = mgr.getUserData(account, AuthenticatorService.KEY_COMMUNITY_ID);
            String communityId = null;
            if (encCommunityId != null) {
            	communityId = SalesforceSDKManager.decryptWithPasscode(encCommunityId,
            			SalesforceSDKManager.getInstance().getPasscodeHash());
            }
            final String encCommunityUrl = mgr.getUserData(account, AuthenticatorService.KEY_COMMUNITY_URL);
            String communityUrl = null;
            if (encCommunityUrl != null) {
            	communityUrl = SalesforceSDKManager.decryptWithPasscode(encCommunityUrl,
            			SalesforceSDKManager.getInstance().getPasscodeHash());
            }
            final Bundle resBundle = new Bundle();
            try {
                final TokenEndpointResponse tr = OAuth2.refreshAuthToken(HttpAccess.DEFAULT, new URI(loginServer), clientId, refreshToken, clientSecret, addlParamsMap);

                // Handle the case where the org has been migrated to a new instance, or has turned on my domains.
                if (!instServer.equalsIgnoreCase(tr.instanceUrl)) {
                    mgr.setUserData(account, AuthenticatorService.KEY_INSTANCE_URL, SalesforceSDKManager.encryptWithPasscode(tr.instanceUrl, passcodeHash));
                }

                // Update auth token in account.
                mgr.setUserData(account, AccountManager.KEY_AUTHTOKEN, SalesforceSDKManager.encryptWithPasscode(tr.authToken, passcodeHash));
                resBundle.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
                resBundle.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
                resBundle.putString(AccountManager.KEY_AUTHTOKEN, SalesforceSDKManager.encryptWithPasscode(tr.authToken, passcodeHash));
                resBundle.putString(AuthenticatorService.KEY_LOGIN_URL, SalesforceSDKManager.encryptWithPasscode(loginServer, passcodeHash));
                resBundle.putString(AuthenticatorService.KEY_INSTANCE_URL, SalesforceSDKManager.encryptWithPasscode(tr.instanceUrl, passcodeHash));
                resBundle.putString(AuthenticatorService.KEY_CLIENT_ID, SalesforceSDKManager.encryptWithPasscode(clientId, passcodeHash));
                resBundle.putString(AuthenticatorService.KEY_USERNAME, SalesforceSDKManager.encryptWithPasscode(username, passcodeHash));
                resBundle.putString(AuthenticatorService.KEY_USER_ID, SalesforceSDKManager.encryptWithPasscode(userId, passcodeHash));
                resBundle.putString(AuthenticatorService.KEY_ORG_ID, SalesforceSDKManager.encryptWithPasscode(orgId, passcodeHash));
                resBundle.putString(AuthenticatorService.KEY_LAST_NAME, SalesforceSDKManager.encryptWithPasscode(lastName, passcodeHash));
                resBundle.putString(AuthenticatorService.KEY_EMAIL, SalesforceSDKManager.encryptWithPasscode(email, passcodeHash));
                String encrFirstName = null;
                if (firstName != null) {
                    encrFirstName = SalesforceSDKManager.encryptWithPasscode(firstName, passcodeHash);
                }
                resBundle.putString(AuthenticatorService.KEY_FIRST_NAME, encrFirstName);
                String encrDisplayName = null;
                if (displayName != null) {
                    encrDisplayName = SalesforceSDKManager.encryptWithPasscode(displayName, passcodeHash);
                }
                resBundle.putString(AuthenticatorService.KEY_DISPLAY_NAME, encrDisplayName);
                String encrPhotoUrl = null;
                if (photoUrl != null) {
                    encrPhotoUrl = SalesforceSDKManager.encryptWithPasscode(photoUrl, passcodeHash);
                }
                resBundle.putString(AuthenticatorService.KEY_PHOTO_URL, encrPhotoUrl);
                String encrThumbnailUrl = null;
                if (thumbnailUrl != null) {
                    encrThumbnailUrl = SalesforceSDKManager.encryptWithPasscode(thumbnailUrl, passcodeHash);
                }

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
                                final String encrNewValue = SalesforceSDKManager.encryptWithPasscode(newValue, passcodeHash);
                                resBundle.putString(key, encrNewValue);
                            }
                        } else if (values != null && values.containsKey(key)) {
                            final String value = values.get(key);
                            if (value != null) {
                                final String encrValue = SalesforceSDKManager.encryptWithPasscode(value, passcodeHash);
                                resBundle.putString(key, encrValue);
                            }
                        }
                    }
                }
                resBundle.putString(AuthenticatorService.KEY_THUMBNAIL_URL, encrThumbnailUrl);
                String encrClientSecret = null;
                if (clientSecret != null) {
                    encrClientSecret = SalesforceSDKManager.encryptWithPasscode(clientSecret, passcodeHash);
                }
                resBundle.putString(AuthenticatorService.KEY_CLIENT_SECRET, encrClientSecret);
                String encrCommunityId = null;
                if (communityId != null) {
                	encrCommunityId = SalesforceSDKManager.encryptWithPasscode(communityId, passcodeHash);
                }
                resBundle.putString(AuthenticatorService.KEY_COMMUNITY_ID, encrCommunityId);
                String encrCommunityUrl = null;
                if (communityUrl != null) {
                	encrCommunityUrl = SalesforceSDKManager.encryptWithPasscode(communityUrl, passcodeHash);
                }
                resBundle.putString(AuthenticatorService.KEY_COMMUNITY_URL, encrCommunityUrl);
            } catch (IOException e) {
                Log.w(TAG, "", e);
                throw new NetworkErrorException(e);
            } catch (URISyntaxException e) {
                Log.w(TAG, "", e);
                throw new NetworkErrorException(e);
            } catch (OAuthFailedException e) {
                if (e.isRefreshTokenInvalid()) {
                	Log.i(TAG, "Invalid Refresh Token: (Error: " + e.response.error + ", Status Code: " + e.httpStatusCode + ")");
                    // the exception explicitly indicates that the refresh token is no longer valid.
                    return makeAuthIntentBundle(response, options);
                }
                resBundle.putString(AccountManager.KEY_ERROR_CODE, e.response.error);
                resBundle.putString(AccountManager.KEY_ERROR_MESSAGE, e.response.errorDescription);
            }
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
            Intent i = new Intent(context, SalesforceSDKManager.getInstance().getLoginActivityClass());
            i.setPackage(context.getPackageName());
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
