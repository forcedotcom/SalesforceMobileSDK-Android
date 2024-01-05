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
            final String loginServer = SalesforceSDKManager.decrypt(mgr.getUserData(account, AuthenticatorService.KEY_LOGIN_URL), encryptionKey);
            final String clientId = SalesforceSDKManager.decrypt(mgr.getUserData(account, AuthenticatorService.KEY_CLIENT_ID), encryptionKey);
            final String instServer = SalesforceSDKManager.decrypt(mgr.getUserData(account, AuthenticatorService.KEY_INSTANCE_URL), encryptionKey);
            final String userId = SalesforceSDKManager.decrypt(mgr.getUserData(account, AuthenticatorService.KEY_USER_ID), encryptionKey);
            final String orgId = SalesforceSDKManager.decrypt(mgr.getUserData(account, AuthenticatorService.KEY_ORG_ID), encryptionKey);
            final String username = SalesforceSDKManager.decrypt(mgr.getUserData(account, AuthenticatorService.KEY_USERNAME), encryptionKey);
            final String lastName = SalesforceSDKManager.decrypt(mgr.getUserData(account, AuthenticatorService.KEY_LAST_NAME), encryptionKey);
            final String email = SalesforceSDKManager.decrypt(mgr.getUserData(account, AuthenticatorService.KEY_EMAIL), encryptionKey);
            final String encFirstName = mgr.getUserData(account, AuthenticatorService.KEY_FIRST_NAME);
            String firstName = null;
            if (encFirstName != null) {
                 firstName = SalesforceSDKManager.decrypt(encFirstName, encryptionKey);
            }
            final String encDisplayName = mgr.getUserData(account, AuthenticatorService.KEY_DISPLAY_NAME);
            String displayName = null;
            if (encDisplayName != null) {
                displayName = SalesforceSDKManager.decrypt(encDisplayName, encryptionKey);
            }
            final String encPhotoUrl = mgr.getUserData(account, AuthenticatorService.KEY_PHOTO_URL);
            String photoUrl = null;
            if (encPhotoUrl != null) {
                photoUrl = SalesforceSDKManager.decrypt(encPhotoUrl, encryptionKey);
            }
            final String encThumbnailUrl = mgr.getUserData(account, AuthenticatorService.KEY_THUMBNAIL_URL);
            String thumbnailUrl = null;
            if (encThumbnailUrl != null) {
                thumbnailUrl = SalesforceSDKManager.decrypt(encThumbnailUrl, encryptionKey);
            }
            final String encLightningDomain = mgr.getUserData(account, AuthenticatorService.KEY_LIGHTNING_DOMAIN);
            String lightningDomain = null;
            if (encLightningDomain != null) {
                lightningDomain = SalesforceSDKManager.decrypt(encLightningDomain, encryptionKey);
            }
            final String encLightningSid = mgr.getUserData(account, AuthenticatorService.KEY_LIGHTNING_SID);
            String lightningSid = null;
            if (encLightningSid != null) {
                lightningSid = SalesforceSDKManager.decrypt(encLightningSid, encryptionKey);
            }
            final String encVFDomain = mgr.getUserData(account, AuthenticatorService.KEY_VF_DOMAIN);
            String vfDomain = null;
            if (encVFDomain != null) {
                vfDomain = SalesforceSDKManager.decrypt(encVFDomain, encryptionKey);
            }
            final String encVFSid = mgr.getUserData(account, AuthenticatorService.KEY_VF_SID);
            String vfSid = null;
            if (encVFSid != null) {
                vfSid = SalesforceSDKManager.decrypt(encVFSid, encryptionKey);
            }
            final String encContentDomain = mgr.getUserData(account, AuthenticatorService.KEY_CONTENT_DOMAIN);
            String contentDomain = null;
            if (encContentDomain != null) {
                contentDomain = SalesforceSDKManager.decrypt(encContentDomain, encryptionKey);
            }
            final String encContentSid = mgr.getUserData(account, AuthenticatorService.KEY_CONTENT_SID);
            String contentSid = null;
            if (encContentSid != null) {
                contentSid = SalesforceSDKManager.decrypt(encContentSid, encryptionKey);
            }
            final String encCSRFToken = mgr.getUserData(account, AuthenticatorService.KEY_CSRF_TOKEN);
            String csrfToken = null;
            if (encCSRFToken != null) {
                csrfToken = SalesforceSDKManager.decrypt(encCSRFToken, encryptionKey);
            }
            final List<String> additionalOauthKeys = SalesforceSDKManager.getInstance().getAdditionalOauthKeys();
            Map<String, String> values = null;
            if (additionalOauthKeys != null && !additionalOauthKeys.isEmpty()) {
                values = new HashMap<>();
                for (final String key : additionalOauthKeys) {
                    final String encValue = mgr.getUserData(account, key);
                    if (encValue != null) {
                        final String value = SalesforceSDKManager.decrypt(encValue, encryptionKey);
                        values.put(key, value);
                    }
                }
            }
            final Map<String,String> addlParamsMap = SalesforceSDKManager.getInstance().getLoginOptions().getAdditionalParameters();
            final String encCommunityId = mgr.getUserData(account, AuthenticatorService.KEY_COMMUNITY_ID);
            String communityId = null;
            if (encCommunityId != null) {
            	communityId = SalesforceSDKManager.decrypt(encCommunityId, encryptionKey);
            }
            final String encCommunityUrl = mgr.getUserData(account, AuthenticatorService.KEY_COMMUNITY_URL);
            String communityUrl = null;
            if (encCommunityUrl != null) {
            	communityUrl = SalesforceSDKManager.decrypt(encCommunityUrl, encryptionKey);
            }
            final Bundle resBundle = new Bundle();
            try {
                final TokenEndpointResponse tr = OAuth2.refreshAuthToken(HttpAccess.DEFAULT,
                        new URI(loginServer), clientId, refreshToken, addlParamsMap);

                // Handle the case where the org has been migrated to a new instance, or has turned on my domains.
                if (!instServer.equalsIgnoreCase(tr.instanceUrl)) {
                    mgr.setUserData(account, AuthenticatorService.KEY_INSTANCE_URL, SalesforceSDKManager.encrypt(tr.instanceUrl, encryptionKey));
                }

                // Update auth token in account.
                mgr.setUserData(account, AccountManager.KEY_AUTHTOKEN, SalesforceSDKManager.encrypt(tr.authToken, encryptionKey));
                resBundle.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
                resBundle.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
                resBundle.putString(AccountManager.KEY_AUTHTOKEN, SalesforceSDKManager.encrypt(tr.authToken, encryptionKey));
                resBundle.putString(AuthenticatorService.KEY_LOGIN_URL, SalesforceSDKManager.encrypt(loginServer, encryptionKey));
                resBundle.putString(AuthenticatorService.KEY_INSTANCE_URL, SalesforceSDKManager.encrypt(tr.instanceUrl, encryptionKey));
                resBundle.putString(AuthenticatorService.KEY_CLIENT_ID, SalesforceSDKManager.encrypt(clientId, encryptionKey));
                resBundle.putString(AuthenticatorService.KEY_USERNAME, SalesforceSDKManager.encrypt(username, encryptionKey));
                resBundle.putString(AuthenticatorService.KEY_USER_ID, SalesforceSDKManager.encrypt(userId, encryptionKey));
                resBundle.putString(AuthenticatorService.KEY_ORG_ID, SalesforceSDKManager.encrypt(orgId, encryptionKey));
                resBundle.putString(AuthenticatorService.KEY_LAST_NAME, SalesforceSDKManager.encrypt(lastName, encryptionKey));
                resBundle.putString(AuthenticatorService.KEY_EMAIL, SalesforceSDKManager.encrypt(email, encryptionKey));
                String encrFirstName = null;
                if (firstName != null) {
                    encrFirstName = SalesforceSDKManager.encrypt(firstName, encryptionKey);
                }
                resBundle.putString(AuthenticatorService.KEY_FIRST_NAME, encrFirstName);
                String encrDisplayName = null;
                if (displayName != null) {
                    encrDisplayName = SalesforceSDKManager.encrypt(displayName, encryptionKey);
                }
                resBundle.putString(AuthenticatorService.KEY_DISPLAY_NAME, encrDisplayName);
                String encrPhotoUrl = null;
                if (photoUrl != null) {
                    encrPhotoUrl = SalesforceSDKManager.encrypt(photoUrl, encryptionKey);
                }
                resBundle.putString(AuthenticatorService.KEY_PHOTO_URL, encrPhotoUrl);
                String encrThumbnailUrl = null;
                if (thumbnailUrl != null) {
                    encrThumbnailUrl = SalesforceSDKManager.encrypt(thumbnailUrl, encryptionKey);
                }
                resBundle.putString(AuthenticatorService.KEY_THUMBNAIL_URL, encrThumbnailUrl);
                String encrLightningDomain = null;
                if (lightningDomain != null) {
                    encrLightningDomain = SalesforceSDKManager.encrypt(lightningDomain, encryptionKey);
                }
                resBundle.putString(AuthenticatorService.KEY_LIGHTNING_DOMAIN, encrLightningDomain);
                String encrLightningSid = null;
                if (lightningSid != null) {
                    encrLightningSid = SalesforceSDKManager.encrypt(lightningSid, encryptionKey);
                }
                resBundle.putString(AuthenticatorService.KEY_LIGHTNING_SID, encrLightningSid);
                String encrVFDomain = null;
                if (vfDomain != null) {
                    encrVFDomain = SalesforceSDKManager.encrypt(vfDomain, encryptionKey);
                }
                resBundle.putString(AuthenticatorService.KEY_VF_DOMAIN, encrVFDomain);
                String encrVFSid = null;
                if (vfSid != null) {
                    encrVFSid = SalesforceSDKManager.encrypt(vfSid, encryptionKey);
                }
                resBundle.putString(AuthenticatorService.KEY_VF_SID, encrVFSid);
                String encrContentDomain = null;
                if (contentDomain != null) {
                    encrContentDomain = SalesforceSDKManager.encrypt(contentDomain, encryptionKey);
                }
                resBundle.putString(AuthenticatorService.KEY_CONTENT_DOMAIN, encrContentDomain);
                String encrContentSid = null;
                if (contentSid != null) {
                    encrContentSid = SalesforceSDKManager.encrypt(contentSid, encryptionKey);
                }
                resBundle.putString(AuthenticatorService.KEY_CONTENT_SID, encrContentSid);
                String encrCSRFToken = null;
                if (csrfToken != null) {
                    encrCSRFToken = SalesforceSDKManager.encrypt(csrfToken, encryptionKey);
                }
                resBundle.putString(AuthenticatorService.KEY_CSRF_TOKEN, encrCSRFToken);

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
                                final String encrNewValue = SalesforceSDKManager.encrypt(newValue, encryptionKey);
                                resBundle.putString(key, encrNewValue);
                                mgr.setUserData(account, key, encrNewValue);
                            }
                        } else if (values != null && values.containsKey(key)) {
                            final String value = values.get(key);
                            if (value != null) {
                                final String encrValue = SalesforceSDKManager.encrypt(value, encryptionKey);
                                resBundle.putString(key, encrValue);
                            }
                        }
                    }
                }
                String encrCommunityId = null;
                if (communityId != null) {
                	encrCommunityId = SalesforceSDKManager.encrypt(communityId, encryptionKey);
                }
                resBundle.putString(AuthenticatorService.KEY_COMMUNITY_ID, encrCommunityId);
                String encrCommunityUrl = null;
                if (communityUrl != null) {
                	encrCommunityUrl = SalesforceSDKManager.encrypt(communityUrl, encryptionKey);
                }
                resBundle.putString(AuthenticatorService.KEY_COMMUNITY_URL, encrCommunityUrl);
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
