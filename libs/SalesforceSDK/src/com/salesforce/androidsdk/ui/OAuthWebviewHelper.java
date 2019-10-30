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
package com.salesforce.androidsdk.ui;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.net.http.SslError;
import android.os.AsyncTask;
import android.os.Bundle;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.security.KeyChainException;
import android.text.TextUtils;
import android.webkit.ClientCertRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountBuilder;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.analytics.EventBuilderHelper;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.auth.OAuth2;
import com.salesforce.androidsdk.auth.OAuth2.IdServiceResponse;
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse;
import com.salesforce.androidsdk.config.BootConfig;
import com.salesforce.androidsdk.config.LoginServerManager;
import com.salesforce.androidsdk.config.RuntimeConfig;
import com.salesforce.androidsdk.push.PushMessaging;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;
import com.salesforce.androidsdk.security.PasscodeManager;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.MapUtil;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;
import com.salesforce.androidsdk.util.UriFragmentParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.browser.customtabs.CustomTabsIntent;

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
    public static final String AUTHENTICATION_FAILED_INTENT = "com.salesforce.auth.intent.AUTHENTICATION_ERROR";
    public static final String HTTP_ERROR_RESPONSE_CODE_INTENT = "com.salesforce.auth.intent.HTTP_RESPONSE_CODE";
    public static final String RESPONSE_ERROR_INTENT = "com.salesforce.auth.intent.RESPONSE_ERROR";
    public static final String RESPONSE_ERROR_DESCRIPTION_INTENT = "com.salesforce.auth.intent.RESPONSE_ERROR_DESCRIPTION";
    private static final String TAG = "OAuthWebViewHelper";
    private static final String ACCOUNT_OPTIONS = "accountOptions";

    // background executor
    private final ExecutorService threadPool = Executors.newFixedThreadPool(1);

    /**
     * the host activity/fragment should pass in an implementation of this
     * interface so that it can notify it of things it needs to do as part of
     * the oauth process.
     */
    public interface OAuthWebviewHelperEvents {

        /** we're starting to load this login page into the webview */
        void loadingLoginPage(String loginUrl);

        /** We've completed the auth process and here's the resulting Authentication Result bundle to return to the Authenticator */
        void onAccountAuthenticatorResult(Bundle authResult);

        /** we're in some end state and requesting that the host activity be finished/closed. */
        void finish(UserAccount userAccount);
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
        final WebSettings webSettings = webview.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setUserAgentString(SalesforceSDKManager.getInstance().getUserAgent());
        webview.setWebViewClient(makeWebViewClient());
        webview.setWebChromeClient(makeWebChromeClient());

        /*
         * Restores WebView's state if available.
         * This ensures the user is not forced to type in credentials again
         * once the auth process has been kicked off.
         */
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

    /** Factory method for the WebViewClient, you can replace this with something else if you need to */
    protected WebViewClient makeWebViewClient() {
    	return new AuthWebViewClient();
    }

    /** Factory method for the WebChromeClient, you can replace this with something else if you need to */
    protected WebChromeClient makeWebChromeClient() {
        return new WebChromeClient();
    }

    protected Context getContext() {
        return webview.getContext();
    }

    /**
     * Called when the user facing part of the auth flow completed with an error.
     * We show the user an error and end the activity.
     *
     * @param error Error.
     * @param errorDesc Error description.
     * @param e Exception.
     */
    protected void onAuthFlowError(String error, String errorDesc, Exception e) {
        SalesforceSDKLogger.w(TAG, error + ": " + errorDesc, e);

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
                    callback.finish(null);
                }
            }, t.getDuration());
            t.show();
        }
        final Intent intent = new Intent(AUTHENTICATION_FAILED_INTENT);
        if (e instanceof OAuth2.OAuthFailedException) {
            final OAuth2.OAuthFailedException exception = (OAuth2.OAuthFailedException) e;
            int statusCode = exception.getHttpStatusCode();
            intent.putExtra(HTTP_ERROR_RESPONSE_CODE_INTENT, statusCode);
            final OAuth2.TokenErrorResponse errorResponse = exception.getTokenErrorResponse();
            if (errorResponse != null) {
                final String tokenError = errorResponse.error;
                final String tokenErrorDesc = errorResponse.errorDescription;
                intent.putExtra(RESPONSE_ERROR_INTENT, tokenError);
                intent.putExtra(RESPONSE_ERROR_DESCRIPTION_INTENT, tokenErrorDesc);
            }
        }
        SalesforceSDKManager.getInstance().getAppContext().sendBroadcast(intent);
    }

    protected void showError(Exception exception) {
        Toast.makeText(getContext(),
                getContext().getString(R.string.sf__generic_error, exception.toString()),
                Toast.LENGTH_LONG).show();
    }

    /**
     * Tells the webview to load the authorization page.
     * We also update the window title, so its easier to
     * see which system you're logging in to
     */
    public void loadLoginPage() {
        if (TextUtils.isEmpty(loginOptions.getJwt())) {
            loginOptions.setLoginUrl(getLoginUrl());
            doLoadPage(false);
        } else {
            new SwapJWTForAccessTokenTask().execute(loginOptions);
        }
    }

    private void doLoadPage(boolean jwtFlow) {
        try {
            URI uri = getAuthorizationUrl(jwtFlow);
            callback.loadingLoginPage(loginOptions.getLoginUrl());
            if (SalesforceSDKManager.getInstance().isBrowserLoginEnabled()) {
                loadLoginPageInChrome(uri);
            } else {
                webview.loadUrl(uri.toString());
            }
        } catch (URISyntaxException ex) {
            showError(ex);
        }
    }

    private void loadLoginPageInChrome(URI uri) {
        final Uri url = Uri.parse(uri.toString());
        final CustomTabsIntent.Builder intentBuilder = new CustomTabsIntent.Builder();

        /*
         * Sets custom animation to slide in and out for Chrome custom tab so that
         * it doesn't look like a swizzle out of the app and back in.
         */
        intentBuilder.setStartAnimations(activity, android.R.anim.slide_in_left,
                android.R.anim.slide_out_right);
        intentBuilder.setExitAnimations(activity, android.R.anim.slide_in_left,
                android.R.anim.slide_out_right);

        // Replaces default 'Close Tab' button with a custom back arrow instead of 'x'.
        final Resources resources = activity.getResources();
        intentBuilder.setCloseButtonIcon(BitmapFactory.decodeResource(resources,
                R.drawable.sf__action_back));
        intentBuilder.setToolbarColor(resources.getColor(R.color.sf__primary_color));

        // Adds a menu item to change server.
        final Intent changeServerIntent = new Intent(activity, ServerPickerActivity.class);
        final PendingIntent changeServerPendingIntent = PendingIntent.getActivity(activity,
                LoginActivity.PICK_SERVER_REQUEST_CODE, changeServerIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        intentBuilder.addMenuItem(activity.getString(R.string.sf__pick_server), changeServerPendingIntent);
        final CustomTabsIntent customTabsIntent = intentBuilder.build();

        /*
         * Sets the package explicitly to Google Chrome to avoid other browsers. This
         * ensures that we don't display a popup allowing the user to select a browser
         * because some browsers don't support certain authentication schemes. If Chrome
         * is not available, we will use the default browser that the device uses.
         */
        if (doesChromeExist()) {
            customTabsIntent.intent.setPackage("com.android.chrome");
        }

        /*
         * Prevents Chrome custom tab from staying in the activity history stack. This flag
         * ensures that Chrome custom tab is dismissed once the login process is complete.
         */
        customTabsIntent.intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        try {
            customTabsIntent.launchUrl(activity, url);
        } catch (ActivityNotFoundException e) {
            SalesforceSDKLogger.w(TAG, "Browser not installed on this device", e);
            Toast.makeText(getContext(), "Browser not installed on this device",
                    Toast.LENGTH_LONG).show();
            callback.finish(null);
        }
    }

    private boolean doesChromeExist() {
        boolean exists = false;
        final PackageManager packageManager = activity.getPackageManager();
        ApplicationInfo applicationInfo = null;
        try {
            applicationInfo = packageManager.getApplicationInfo("com.android.chrome", 0);
        } catch (PackageManager.NameNotFoundException e) {
            SalesforceSDKLogger.w(TAG, "Chrome does not exist on this device", e);
        }
        if (applicationInfo != null) {
            exists = true;
        }
        return exists;
    }

    protected String getOAuthClientId() {
    	return loginOptions.getOauthClientId();
    }

    protected URI getAuthorizationUrl(Boolean jwtFlow) throws URISyntaxException {
        if (jwtFlow) {
            return OAuth2.getAuthorizationUrl(new URI(loginOptions.getLoginUrl()),
                    getOAuthClientId(),
                    loginOptions.getOauthCallbackUrl(),
                    loginOptions.getOauthScopes(),
                    getAuthorizationDisplayType(),
                    loginOptions.getJwt(),
                    loginOptions.getLoginUrl(),
                    loginOptions.getAdditionalParameters());
        }
        return OAuth2.getAuthorizationUrl(new URI(loginOptions.getLoginUrl()),
                getOAuthClientId(),
                loginOptions.getOauthCallbackUrl(),
                loginOptions.getOauthScopes(),
                getAuthorizationDisplayType(),
                loginOptions.getAdditionalParameters());
    }

    protected URI getAuthorizationUrl() throws URISyntaxException {
        return getAuthorizationUrl(false);
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
     */
    protected class AuthWebViewClient extends WebViewClient {

        @Override
		public void onPageFinished(WebView view, String url) {
        	EventsObservable.get().notifyEvent(EventType.AuthWebViewPageFinished, url);
        	super.onPageFinished(view, url);
		}

		@Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
			boolean isDone = url.replace("///", "/").toLowerCase(Locale.US).startsWith(loginOptions.getOauthCallbackUrl().replace("///", "/").toLowerCase(Locale.US));
            if (isDone) {
                Uri callbackUri = Uri.parse(url);
                Map<String, String> params = UriFragmentParser.parse(callbackUri);
                String error = params.get("error");
                // Did we fail?
                if (error != null) {
                    String errorDesc = params.get("error_description");
                    onAuthFlowError(error, errorDesc, null);
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
            int primErrorStringId = R.string.sf__ssl_unknown_error;
            switch (primError) {
                case SslError.SSL_EXPIRED:      primErrorStringId = R.string.sf__ssl_expired; break;
                case SslError.SSL_IDMISMATCH:   primErrorStringId = R.string.sf__ssl_id_mismatch; break;
                case SslError.SSL_NOTYETVALID:  primErrorStringId = R.string.sf__ssl_not_yet_valid; break;
                case SslError.SSL_UNTRUSTED:    primErrorStringId = R.string.sf__ssl_untrusted; break;
            }

            // Building text message to show
            String text = getContext().getString(R.string.sf__ssl_error, getContext().getString(primErrorStringId));
            SalesforceSDKLogger.e(TAG, "Received SSL error for server: " + text);

            // Bringing up toast
            Toast.makeText(getContext(), text, Toast.LENGTH_LONG).show();
            handler.cancel();
        }

		@Override
        public void onReceivedClientCertRequest(WebView view, ClientCertRequest request) {
            SalesforceSDKLogger.d(TAG, "Received client certificate request from server");
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

    private class SwapJWTForAccessTokenTask extends BaseFinishAuthFlowTask<LoginOptions> {

        @Override
        protected TokenEndpointResponse performRequest(LoginOptions options) {
            try {
                return OAuth2.swapJWTForTokens(HttpAccess.DEFAULT, new URI(options.getLoginUrl()), options.getJwt());
            } catch (Exception e) {
                backgroundException = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(TokenEndpointResponse tr) {
            if (backgroundException != null) {
                handleJWTError();
                loginOptions.setJwt(null);
                return;
            }
            if (tr != null && tr.authToken != null) {
                loginOptions.setJwt(tr.authToken);
                doLoadPage(true);
            } else {
                doLoadPage(false);
                handleJWTError();
            }
            loginOptions.setJwt(null);
        }

        private void handleJWTError() {
            final SalesforceSDKManager mgr = SalesforceSDKManager.getInstance();
            onAuthFlowError(getContext().getString(R.string.sf__generic_authentication_error_title),
                    getContext().getString(R.string.sf__jwt_authentication_error), backgroundException);
        }
    }

    /**
     * Base class with common code for the background task that finishes off the auth process.
     */
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

            // Failure cases.
            if (backgroundException != null) {
                SalesforceSDKLogger.w(TAG, "Exception thrown while retrieving token response", backgroundException);
                onAuthFlowError(getContext().getString(R.string.sf__generic_authentication_error_title),
                        getContext().getString(R.string.sf__generic_authentication_error), backgroundException);
                callback.finish(null);
                return;
            }
            if (id.customPermissions != null) {
                final boolean mustBeManagedApp = id.customPermissions.optBoolean(MUST_BE_MANAGED_APP_PERM);
                if (mustBeManagedApp && !RuntimeConfig.getRuntimeConfig(getContext()).isManagedApp()) {
                    onAuthFlowError(getContext().getString(R.string.sf__generic_authentication_error_title),
                            getContext().getString(R.string.sf__managed_app_error), backgroundException);
                    callback.finish(null);
                    return;
                }
            }

            // Putting together all the information needed to create the new account.
            accountOptions = new AccountOptions(id.username, tr.refreshToken,
                    tr.authToken, tr.idUrl, tr.instanceUrl, tr.orgId, tr.userId,
                    tr.communityId, tr.communityUrl, id.firstName, id.lastName,
                    id.displayName, id.email, id.pictureUrl, id.thumbnailUrl, tr.additionalOauthValues);

            // Sets additional admin prefs, if they exist.
            final UserAccount account = UserAccountBuilder.getInstance().authToken(accountOptions.authToken).
                    refreshToken(accountOptions.refreshToken).loginServer(loginOptions.getLoginUrl()).
                    idUrl(accountOptions.identityUrl).instanceServer(accountOptions.instanceUrl).
                    orgId(accountOptions.orgId).userId(accountOptions.userId).username(accountOptions.username).
                    accountName(buildAccountName(accountOptions.username, accountOptions.instanceUrl)).
                    communityId(accountOptions.communityId).communityUrl(accountOptions.communityUrl).
                    firstName(accountOptions.firstName).lastName(accountOptions.lastName).
                    displayName(accountOptions.displayName).email(accountOptions.email).
                    photoUrl(accountOptions.photoUrl).thumbnailUrl(accountOptions.thumbnailUrl).
                    additionalOauthValues(accountOptions.additionalOauthValues).build();
            account.downloadProfilePhoto();
            if (id.customAttributes != null) {
                mgr.getAdminSettingsManager().setPrefs(id.customAttributes, account);
            }
            if (id.customPermissions != null) {
                mgr.getAdminPermsManager().setPrefs(id.customPermissions, account);
            }

            // Save the user account
            addAccount(account);

            // Screen lock required by mobile policy.
            if (id.screenLockTimeout > 0) {

                // Stores the mobile policy for the org.
                final PasscodeManager passcodeManager = mgr.getPasscodeManager();
                passcodeManager.storeMobilePolicyForOrg(account, id.screenLockTimeout * 1000 * 60, id.pinLength, id.biometricUnlockAlowed);
                passcodeManager.setTimeoutMs(id.screenLockTimeout * 1000 * 60);
                // NB setPasscodeLength(...)
                //    If there was a passcode and the length is increased, the passcode manager will remember that a passcode change is required
                //    The next SalesforceActivity to resume, will cause the locking screen to popup in passcode change mode
                passcodeManager.setPasscodeLength((Activity) getContext(), id.pinLength);
                passcodeManager.setBiometricAllowed((Activity) getContext(), id.biometricUnlockAlowed);
            }

            // No screen lock required or no mobile policy specified.
            else {
                final PasscodeManager passcodeManager = mgr.getPasscodeManager();
                passcodeManager.storeMobilePolicyForOrg(account, 0, PasscodeManager.MIN_PASSCODE_LENGTH , true);
            }

            // All done
            callback.finish(account);
        }

        protected void handleException(Exception ex) {
            if (ex.getMessage() != null) {
                SalesforceSDKLogger.w(TAG, "Exception thrown", ex);
            }
            backgroundException = ex;
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

    protected void addAccount(final UserAccount account) {
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
                loginOptions.getLoginUrl(),
                accountOptions.identityUrl,
                getOAuthClientId(),
                accountOptions.orgId,
                accountOptions.userId,
                accountOptions.communityId,
                accountOptions.communityUrl,
                accountOptions.firstName,
                accountOptions.lastName,
                accountOptions.displayName,
                accountOptions.email,
                accountOptions.photoUrl,
                accountOptions.thumbnailUrl,
                accountOptions.additionalOauthValues);

    	/*
    	 * Registers for push notifications, if push notification client ID is present.
    	 * This step needs to happen after the account has been added by client
    	 * manager, so that the push service has all the account info it needs.
    	 */
        final Context appContext = SalesforceSDKManager.getInstance().getAppContext();
        final String pushNotificationId = BootConfig.getBootConfig(appContext).getPushNotificationClientId();
        if (!TextUtils.isEmpty(pushNotificationId)) {
            PushMessaging.register(appContext, account);
        }

        callback.onAccountAuthenticatorResult(extras);
        if (SalesforceSDKManager.getInstance().getIsTestRun()) {
            logAddAccount(account);
        } else {
            threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    logAddAccount(account);
                }
            });
        }
    }

    /**
     * Log the addition of a new account.
     * @param account
     */
    private void logAddAccount(UserAccount account) {
        final JSONObject attributes = new JSONObject();
        try {
            final List<UserAccount> users = UserAccountManager.getInstance().getAuthenticatedUsers();
            attributes.put("numUsers", (users == null) ? 0 : users.size());

            final List<LoginServerManager.LoginServer> servers = SalesforceSDKManager.getInstance().getLoginServerManager().getLoginServers();
            attributes.put("numLoginServers", (servers == null) ? 0 : servers.size());
            if (servers != null) {
                final JSONArray serversJson = new JSONArray();
                for (final LoginServerManager.LoginServer server : servers) {
                    if (server != null) {
                        serversJson.put(server.url);
                    }
                }
                attributes.put("loginServers", serversJson);
            }
            EventBuilderHelper.createAndStoreEventSync("addUser", account, TAG, attributes);
        } catch (JSONException e) {
            SalesforceSDKLogger.e(TAG, "Exception thrown while creating JSON", e);
        }
    }

    /**
     * @return name to be shown for account in Settings -> Accounts & Sync
     */
    protected String buildAccountName(String username, String instanceServer) {
        return String.format("%s (%s) (%s)", username, instanceServer,
        		SalesforceSDKManager.getInstance().getApplicationName());
    }

    /**
     * Class encapsulating the parameters required to create a new account.
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
        private static final String FIRST_NAME = "firstName";
        private static final String LAST_NAME = "lastName";
        private static final String DISPLAY_NAME = "displayName";
        private static final String EMAIL = "email";
        private static final String PHOTO_URL = "photoUrl";
        private static final String THUMBNAIL_URL = "thumbnailUrl";

        public final String username;
        public final String refreshToken;
        public final String authToken;
        public final String identityUrl;
        public final String instanceUrl;
        public final String orgId;
        public final String userId;
        public final String communityId;
        public final String communityUrl;
        public final String firstName;
        public final String lastName;
        public final String displayName;
        public final String email;
        public final String photoUrl;
        public final String thumbnailUrl;
        public final Map<String, String> additionalOauthValues;
        private Bundle bundle;

        public AccountOptions(String username, String refreshToken,
                String authToken, String identityUrl, String instanceUrl,
                String orgId, String userId, String communityId, String communityUrl,
                String firstName, String lastName, String displayName, String email,
                String photoUrl, String thumbnailUrl, Map<String, String> additionalOauthValues) {
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
            this.firstName = firstName;
            this.lastName = lastName;
            this.displayName = displayName;
            this.email = email;
            this.photoUrl = photoUrl;
            this.thumbnailUrl = thumbnailUrl;
            this.additionalOauthValues = additionalOauthValues;
            bundle = new Bundle();
            bundle.putString(USERNAME, username);
            bundle.putString(REFRESH_TOKEN, refreshToken);
            bundle.putString(AUTH_TOKEN, authToken);
            bundle.putString(IDENTITY_URL, identityUrl);
            bundle.putString(INSTANCE_URL, instanceUrl);
            bundle.putString(ORG_ID, orgId);
            bundle.putString(USER_ID, userId);
            bundle.putString(COMMUNITY_ID, communityId);
            bundle.putString(COMMUNITY_URL, communityUrl);
            bundle.putString(FIRST_NAME, firstName);
            bundle.putString(LAST_NAME, lastName);
            bundle.putString(DISPLAY_NAME, displayName);
            bundle.putString(EMAIL, email);
            bundle.putString(PHOTO_URL, photoUrl);
            bundle.putString(THUMBNAIL_URL, thumbnailUrl);
            bundle = MapUtil.addMapToBundle(additionalOauthValues,
                    SalesforceSDKManager.getInstance().getAdditionalOauthKeys(), bundle);
        }

        public Bundle asBundle() {
            return bundle;
        }

        public static AccountOptions fromBundle(Bundle options) {
            if (options == null) {
                return null;
            }
            return new AccountOptions(
                    options.getString(USERNAME),
                    options.getString(REFRESH_TOKEN),
                    options.getString(AUTH_TOKEN),
                    options.getString(IDENTITY_URL),
                    options.getString(INSTANCE_URL),
                    options.getString(ORG_ID),
                    options.getString(USER_ID),
                    options.getString(COMMUNITY_ID),
                    options.getString(COMMUNITY_URL),
                    options.getString(FIRST_NAME),
                    options.getString(LAST_NAME),
                    options.getString(DISPLAY_NAME),
                    options.getString(EMAIL),
                    options.getString(PHOTO_URL),
                    options.getString(THUMBNAIL_URL),
                    getAdditionalOauthValues(options)
                    );
        }

        private static Map<String, String> getAdditionalOauthValues(Bundle options) {
            return MapUtil.addBundleToMap(options,
                    SalesforceSDKManager.getInstance().getAdditionalOauthKeys(), null);
        }
    }

	@Override
	public void alias(String alias) {
		try {
            SalesforceSDKLogger.d(TAG, "Keychain alias callback received");
			certChain = KeyChain.getCertificateChain(activity, alias);
			key = KeyChain.getPrivateKey(activity, alias);
			activity.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                	loadLoginPage();
                }
            });
		} catch (KeyChainException | InterruptedException e) {
            SalesforceSDKLogger.e(TAG, "Exception thrown while retrieving X.509 certificate", e);
		}
	}
}
