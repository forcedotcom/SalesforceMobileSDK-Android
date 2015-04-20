/*
 * Copyright (c) 2011-2015, salesforce.com, inc.
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
package com.salesforce.androidsdk.ui;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.util.Map;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.net.http.SslError;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.security.KeyChainException;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.ClientCertRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.auth.OAuth2;
import com.salesforce.androidsdk.auth.OAuth2.IdServiceResponse;
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse;
import com.salesforce.androidsdk.config.BootConfig;
import com.salesforce.androidsdk.config.RuntimeConfig;
import com.salesforce.androidsdk.push.PushMessaging;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;
import com.salesforce.androidsdk.security.PasscodeManager;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.UriFragmentParser;

/**
 * Helper class to manage a WebView instance that is going through the OAuth login process.
 * Basic flow is
 * 	a) load and show the login page to the user
 * 	b) user logins in and authorizes app
 *  c) we see the navigation to the auth complete Url, and grab the tokens
 *  d) we call the Id service to obtain additional info about the user
 *  e) we create a local account, and return an authentication result bundle.
 *  f) done!
 *
 */
public class OAuthWebviewHelper implements KeyChainAliasCallback {

    // Set a custom permission on your connected application with that name if you want
    // the application to be restricted to managed devices
    public static final String MUST_BE_MANAGED_APP_PERM = "must_be_managed_app";

    private static final String ACCOUNT_OPTIONS = "accountOptions";

    /**
     * the host activity/fragment should pass in an implementation of this
     * interface so that it can notify it of things it needs to do as part of
     * the oauth process.
     */
    public interface OAuthWebviewHelperEvents {

        /** we're starting to load this login page into the webview */
        void loadingLoginPage(String loginUrl);

        /**
         * progress update of loading the webview, totalProgress will go from
         * 0..10000 (you can pass this directly to the activity progressbar)
         */
        void onLoadingProgress(int totalProgress);

        /** We're doing something that takes some unknown amount of time */
        void onIndeterminateProgress(boolean show);

        /** We've completed the auth process and here's the resulting Authentication Result bundle to return to the Authenticator */
        void onAccountAuthenticatorResult(Bundle authResult);

        /** we're in some end state and requesting that the host activity be finished/closed. */
        void finish();
    }

    /**
     * Construct a new OAuthWebviewHelper and perform the initial configuration of the Webview.
     */
    @Deprecated
	public OAuthWebviewHelper(OAuthWebviewHelperEvents callback,
			LoginOptions options, WebView webview, Bundle savedInstanceState) {
    	this(new LoginActivity(), callback, options, webview, savedInstanceState);
    }

    /**
     * Construct a new OAuthWebviewHelper and perform the initial configuration of the Webview.
     */
	public OAuthWebviewHelper(Activity activity, OAuthWebviewHelperEvents callback,
			LoginOptions options, WebView webview, Bundle savedInstanceState) {
        assert options != null && callback != null && webview != null && activity != null;
        this.activity = activity;
        this.callback = callback;
        this.loginOptions = options;
        this.webview = webview;
        webview.getSettings().setJavaScriptEnabled(true);
        webview.setWebViewClient(makeWebViewClient());
        webview.setWebChromeClient(makeWebChromeClient());

        // Restore webview's state if available.
        // This ensures the user is not forced to type in credentials again
        // once the auth process has been kicked off.
        if (savedInstanceState != null) {
            webview.restoreState(savedInstanceState);
            accountOptions = AccountOptions.fromBundle(savedInstanceState.getBundle(ACCOUNT_OPTIONS));
        } else {
            clearCookies();
        }
	}

    private final OAuthWebviewHelperEvents callback;
    protected final LoginOptions loginOptions;
    private final WebView webview;
    private AccountOptions accountOptions;
    private Activity activity;
    private PrivateKey key;
    private X509Certificate[] certChain;

    public void saveState(Bundle outState) {
        webview.saveState(outState);
        if (accountOptions != null) {
            // we have completed the auth flow but not created the account, because we need to create a pin
            outState.putBundle(ACCOUNT_OPTIONS, accountOptions.asBundle());
        }
    }

    public WebView getWebView() {
        return webview;
    }

    public void clearCookies() {
    	SalesforceSDKManager.getInstance().removeAllCookies();
    }

    public void clearView() {
    	webview.loadUrl("about:blank");
    }

    /**
     * Method called by login activity when it resumes after the passcode activity
     *
     * When the server has a mobile policy requiring a passcode, we start the passcode activity after completing the
     * auth flow (see onAuthFlowComplete).
     * When the passcode activity completes, the login activity's onActivityResult gets invoked, and it calls this method
     * to finalize the account creation.
     */
    public void onNewPasscode() {

    	/*
    	 * Re-encryption of existing accounts with the new passcode is taken
    	 * care of in the 'Confirm Passcode' step in PasscodeActivity.
    	 */
        if (accountOptions != null) {
            loginOptions.passcodeHash = SalesforceSDKManager.getInstance().getPasscodeHash();
            addAccount();
            callback.finish();
        }
    }

    /** Factory method for the WebViewClient, you can replace this with something else if you need to */
    protected WebViewClient makeWebViewClient() {
    	return new AuthWebViewClient();
    }
    
    /** Factory method for the WebChromeClient, you can replace this with something else if you need to */
    protected WebChromeClient makeWebChromeClient() {
        return new AuthWebChromeClient();
    }

    protected Context getContext() {
        return webview.getContext();
    }

    /**
     * Called when the user facing part of the auth flow completed with an error.
     * We show the user an error and end the activity.
     */
    protected void onAuthFlowError(String error, String errorDesc) {
        Log.w("LoginActivity:onAuthFlowError", error + ":" + errorDesc);

        // look for deny. kick them back to login, so clear cookies and repoint browser
        if ("access_denied".equals(error)
                && "end-user denied authorization".equals(errorDesc)) {
            webview.post(new Runnable() {
                @Override
                public void run() {
                    clearCookies();
                    loadLoginPage();
                }
            });
        } else {
            Toast t = Toast.makeText(webview.getContext(), error + " : " + errorDesc,
                    Toast.LENGTH_LONG);
            webview.postDelayed(new Runnable() {
                @Override
                public void run() {
                    callback.finish();
                }
            }, t.getDuration());
            t.show();
        }
    }

    protected void showError(Exception exception) {
        Toast.makeText(getContext(),
                getContext().getString(SalesforceSDKManager.getInstance().getSalesforceR().stringGenericError(), exception.toString()),
                Toast.LENGTH_LONG).show();
    }

    /**
     * Tells the webview to load the authorization page.
     * We also update the window title, so its easier to
     * see which system you're logging in to
     */
    public void loadLoginPage() {

        // Filling in loginUrl.
        loginOptions.loginUrl = getLoginUrl();
        try {
            URI uri = getAuthorizationUrl();
            callback.loadingLoginPage(loginOptions.loginUrl);
            webview.loadUrl(uri.toString());
        } catch (URISyntaxException ex) {
            showError(ex);
        }
    }

    protected String getOAuthClientId() {
    	return loginOptions.oauthClientId;
    }
    
    protected URI getAuthorizationUrl() throws URISyntaxException {
        return OAuth2.getAuthorizationUrl(
                new URI(loginOptions.loginUrl),
                getOAuthClientId(),
                loginOptions.oauthCallbackUrl,
                loginOptions.oauthScopes,
                null,
                getAuthorizationDisplayType());
    }

   	/** 
   	 * Override this to replace the default login webview's display param with
   	 * your custom display param. You can override this by either subclassing this class,
   	 * or adding "<string name="sf__oauth_display_type">desiredDisplayParam</string>"
   	 * to your app's resource so that it overrides the default value in the SDK library.
   	 *
   	 * @return the OAuth login display type, e.g. 'mobile', 'touch',
   	 * see the OAuth docs for the complete list of valid values.
   	 */
    protected String getAuthorizationDisplayType() {
    	return this.getContext().getString(R.string.oauth_display_type);
    }
    
    /**
     * Override this method to customize the login url.
     * @return login url
     */
    protected String getLoginUrl() {
    	return SalesforceSDKManager.getInstance().getLoginServerManager().getSelectedLoginServer().url.trim();
    }

    /**
     * WebViewClient which intercepts the redirect to the oauth callback url.
     * That redirect marks the end of the user facing portion of the authentication flow.
     *
     */
    protected class AuthWebViewClient extends WebViewClient {

        @Override
		public void onPageFinished(WebView view, String url) {
        	EventsObservable.get().notifyEvent(EventType.AuthWebViewPageFinished, url);
        	super.onPageFinished(view, url);
		}

		@Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
			boolean isDone = url.replace("///", "/").toLowerCase(Locale.US).startsWith(loginOptions.oauthCallbackUrl.replace("///", "/").toLowerCase(Locale.US));
            if (isDone) {
                Uri callbackUri = Uri.parse(url);
                Map<String, String> params = UriFragmentParser.parse(callbackUri);
                String error = params.get("error");
                // Did we fail?
                if (error != null) {
                    String errorDesc = params.get("error_description");
                    onAuthFlowError(error, errorDesc);
                }
                // Or succeed?
                else {
                    TokenEndpointResponse tr = new TokenEndpointResponse(params);
                    onAuthFlowComplete(tr);
                }
            }
            return isDone;
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            int primError = error.getPrimaryError();

            // Figuring out string resource id
            SalesforceR r = SalesforceSDKManager.getInstance().getSalesforceR();
            int primErrorStringId = r.stringSSLUnknownError();
            switch (primError) {
            case SslError.SSL_EXPIRED:      primErrorStringId = r.stringSSLExpired(); break;
            case SslError.SSL_IDMISMATCH:   primErrorStringId = r.stringSSLIdMismatch(); break;
            case SslError.SSL_NOTYETVALID:  primErrorStringId = r.stringSSLNotYetValid(); break;
            case SslError.SSL_UNTRUSTED:    primErrorStringId = r.stringSSLUntrusted(); break;
            }

            // Building text message to show
            String text = getContext().getString(r.stringSSLError(), getContext().getString(primErrorStringId));

            // Bringing up toast
            Toast.makeText(getContext(), text, Toast.LENGTH_LONG).show();
            handler.cancel();
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
		@Override
        public void onReceivedClientCertRequest(WebView view, ClientCertRequest request) {
        	request.proceed(key, certChain);
        }
    }

    /**
     * Called when the user facing part of the auth flow completed successfully.
     * The last step is to call the identity service to get the username.
     */
    protected void onAuthFlowComplete(TokenEndpointResponse tr) {
        FinishAuthTask t = new FinishAuthTask();
        t.execute(tr);
    }

     // base class with common code for the background task that finishes off the auth process
    protected abstract class BaseFinishAuthFlowTask<RequestType> extends AsyncTask<RequestType, Boolean, TokenEndpointResponse> {

        protected volatile Exception backgroundException;
        protected volatile IdServiceResponse id = null;

        public BaseFinishAuthFlowTask() {

        }

        @SafeVarargs
		@Override
        protected final TokenEndpointResponse doInBackground(RequestType ... params) {
            try {
                publishProgress(true);
                return performRequest(params[0]);
            } catch (Exception ex) {
                handleException(ex);
            }
            return null;
        }

        protected abstract TokenEndpointResponse performRequest(RequestType param) throws Exception;

        @Override
        protected void onPostExecute(OAuth2.TokenEndpointResponse tr) {
            final SalesforceSDKManager mgr = SalesforceSDKManager.getInstance();

            //
            // Failure cases.
            //
            if (backgroundException != null) {
                Log.w("LoginActiviy.onAuthFlowComplete", backgroundException);
                // Error
                onAuthFlowError(getContext().getString(mgr.getSalesforceR().stringGenericAuthenticationErrorTitle()),
                        getContext().getString(mgr.getSalesforceR().stringGenericAuthenticationErrorBody()));
                callback.finish();
                return;
            }

            if (id.customPermissions != null) {
                final boolean mustBeManagedApp = id.customPermissions.optBoolean(MUST_BE_MANAGED_APP_PERM);
                if (mustBeManagedApp && !RuntimeConfig.getRuntimeConfig(getContext()).isManagedApp()) {
                    onAuthFlowError(getContext().getString(mgr.getSalesforceR().stringGenericAuthenticationErrorTitle()),
                            getContext().getString(mgr.getSalesforceR().stringManagedAppError()));
                    callback.finish();
                    return;
                }
            }

            //
            // Putting together all the information needed to create the new account.
            //
            accountOptions = new AccountOptions(id.username, tr.refreshToken,
                    tr.authToken, tr.idUrl, tr.instanceUrl, tr.orgId, tr.userId,
                    tr.communityId, tr.communityUrl);

            // Sets additional admin prefs, if they exist.
            final UserAccount account = new UserAccount(accountOptions.authToken,
                    accountOptions.refreshToken, loginOptions.loginUrl,
                    accountOptions.identityUrl, accountOptions.instanceUrl,
                    accountOptions.orgId, accountOptions.userId,
                    accountOptions.username, buildAccountName(accountOptions.username,
                    accountOptions.instanceUrl), loginOptions.clientSecret,
                    accountOptions.communityId, accountOptions.communityUrl);

            if (id.customAttributes != null) {
                mgr.getAdminSettingsManager().setPrefs(id.customAttributes, account);
            }

            if (id.customPermissions != null) {
                mgr.getAdminPermsManager().setPrefs(id.customPermissions, account);
            }


            // Screen lock required by mobile policy
            if (id.screenLockTimeout > 0) {

                // Stores the mobile policy for the org.
                final PasscodeManager passcodeManager = mgr.getPasscodeManager();
                passcodeManager.storeMobilePolicyForOrg(account, id.screenLockTimeout * 1000 * 60, id.pinLength);
                passcodeManager.setTimeoutMs(id.screenLockTimeout * 1000 * 60);
                passcodeManager.setMinPasscodeLength(id.pinLength);

                /*
                 * Checks if a passcode already exists. If a passcode has NOT
                 * been created yet, the user is taken through the passcode
                 * creation flow, at the end of which account data is encrypted
                 * with a hash of the passcode. Other existing accounts are
                 * also re-encrypted behind the scenes at this point. If a
                 * passcode already exists, the existing hash is used and the
                 * account is added at this point.
                 */
                if (!passcodeManager.hasStoredPasscode(mgr.getAppContext())) {
                    // This will bring up the create passcode screen - we will create the account in onResume
                    mgr.getPasscodeManager().setEnabled(true);
                    mgr.getPasscodeManager().lockIfNeeded((Activity) getContext(), true);
                } else {
                    loginOptions.passcodeHash = mgr.getPasscodeHash();
                    addAccount();
                    callback.finish();
                }
            }
            // No screen lock required or no mobile policy specified
            else {
                final PasscodeManager passcodeManager = mgr.getPasscodeManager();
                passcodeManager.storeMobilePolicyForOrg(account, 0, PasscodeManager.MIN_PASSCODE_LENGTH);
                loginOptions.passcodeHash = mgr.getPasscodeHash();
                addAccount();
                callback.finish();
            }
        }

        protected void handleException(Exception ex) {
            if (ex.getMessage() != null)
                Log.w("BaseFinishAuthFlowTask", "handleException", ex);
            backgroundException = ex;
        }

        @Override
        protected void onProgressUpdate(Boolean... values) {
            callback.onIndeterminateProgress(values[0]);
        }
    }

    /**
     * This is a background process that will call the identity service to get the info we need from
     * the Identity service, and finally wrap up and create account.
     */
    private class FinishAuthTask extends BaseFinishAuthFlowTask<TokenEndpointResponse> {
        @Override
        protected TokenEndpointResponse performRequest(TokenEndpointResponse tr) throws Exception {
            try {
                id = OAuth2.callIdentityService(
                    HttpAccess.DEFAULT, tr.idUrlWithInstance, tr.authToken);
            } catch(Exception e) {
                backgroundException = e;
            }
            return tr;
        }
    }

    protected void addAccount() {
        ClientManager clientManager = new ClientManager(getContext(),
        		SalesforceSDKManager.getInstance().getAccountType(),
        		loginOptions, SalesforceSDKManager.getInstance().shouldLogoutWhenTokenRevoked());

        // Create account name (shown in Settings -> Accounts & sync)
        String accountName = buildAccountName(accountOptions.username,
        		accountOptions.instanceUrl);

        // New account
        Bundle extras = clientManager.createNewAccount(accountName,
                accountOptions.username,
                accountOptions.refreshToken,
                accountOptions.authToken,
                accountOptions.instanceUrl,
                loginOptions.loginUrl,
                accountOptions.identityUrl,
                getOAuthClientId(),
                accountOptions.orgId,
                accountOptions.userId,
                loginOptions.passcodeHash,
                loginOptions.clientSecret,
                accountOptions.communityId,
                accountOptions.communityUrl);

    	/*
    	 * Registers for push notifications, if push notification client ID is present.
    	 * This step needs to happen after the account has been added by client
    	 * manager, so that the push service has all the account info it needs.
    	 */
    	final Context appContext = SalesforceSDKManager.getInstance().getAppContext();
    	final String pushNotificationId = BootConfig.getBootConfig(appContext).getPushNotificationClientId();
    	if (!TextUtils.isEmpty(pushNotificationId)) {
            final UserAccount account = new UserAccount(accountOptions.authToken,
            		accountOptions.refreshToken, loginOptions.loginUrl,
            		accountOptions.identityUrl, accountOptions.instanceUrl,
            		accountOptions.orgId, accountOptions.userId,
            		accountOptions.username, accountName,
            		loginOptions.clientSecret, accountOptions.communityId,
            		accountOptions.communityUrl);
        	PushMessaging.register(appContext, account);
    	}
        callback.onAccountAuthenticatorResult(extras);
    }

    /**
     * @return name to be shown for account in Settings -> Accounts & Sync
     */
    protected String buildAccountName(String username, String instanceServer) {
        return String.format("%s (%s) (%s)", username, instanceServer,
        		SalesforceSDKManager.getInstance().getApplicationName());
    }

    /**
     * WebChromeClient used to report back loading progress.
     */
    protected class AuthWebChromeClient extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            callback.onLoadingProgress(newProgress * 100);
        }
    }

    /**
     * Class encapsulating the parameters required to create a new account
     */
    public static class AccountOptions {

        private static final String USER_ID = "userId";
        private static final String ORG_ID = "orgId";
        private static final String IDENTITY_URL = "identityUrl";
        private static final String INSTANCE_URL = "instanceUrl";
        private static final String AUTH_TOKEN = "authToken";
        private static final String REFRESH_TOKEN = "refreshToken";
        private static final String USERNAME = "username";
        private static final String COMMUNITY_ID = "communityId";
        private static final String COMMUNITY_URL = "communityUrl";

        public final String username;
        public final String refreshToken;
        public final String authToken;
        public final String identityUrl;
        public final String instanceUrl;
        public final String orgId;
        public final String userId;
        public final String communityId;
        public final String communityUrl;

        private final Bundle bundle;

        public AccountOptions(String username, String refreshToken,
                String authToken, String identityUrl, String instanceUrl,
                String orgId, String userId, String communityId, String communityUrl) {
            super();
            this.username = username;
            this.refreshToken = refreshToken;
            this.authToken = authToken;
            this.identityUrl = identityUrl;
            this.instanceUrl = instanceUrl;
            this.orgId = orgId;
            this.userId = userId;
            this.communityId = communityId;
            this.communityUrl = communityUrl;

            bundle = new Bundle();
            bundle.putString(USERNAME, username);
            bundle.putString(REFRESH_TOKEN, refreshToken);
            bundle.putString(AUTH_TOKEN, authToken);
            bundle.putString(INSTANCE_URL, instanceUrl);
            bundle.putString(ORG_ID, orgId);
            bundle.putString(USER_ID, userId);
            bundle.putString(COMMUNITY_ID, communityId);
            bundle.putString(COMMUNITY_URL, communityUrl);
        }

        public Bundle asBundle() {
            return bundle;
        }

        public static AccountOptions fromBundle(Bundle options) {
            if (options == null) return null;
            return new AccountOptions(
                    options.getString(USERNAME),
                    options.getString(REFRESH_TOKEN),
                    options.getString(AUTH_TOKEN),
                    options.getString(IDENTITY_URL),
                    options.getString(INSTANCE_URL),
                    options.getString(ORG_ID),
                    options.getString(USER_ID),
                    options.getString(COMMUNITY_ID),
                    options.getString(COMMUNITY_URL)
                    );
        }
    }

	@Override
	public void alias(String alias) {
		try {
			certChain = KeyChain.getCertificateChain(activity, alias);
			key = KeyChain.getPrivateKey(activity, alias);
			activity.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                	loadLoginPage();
                }
            });
		} catch (KeyChainException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
