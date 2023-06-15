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
import android.view.View;
import android.webkit.ClientCertRequest;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.browser.customtabs.CustomTabsIntent;

import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountBuilder;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.analytics.EventBuilderHelper;
import com.salesforce.androidsdk.app.Features;
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
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.security.BiometricAuthenticationManager;
import com.salesforce.androidsdk.security.SalesforceKeyGenerator;
import com.salesforce.androidsdk.security.ScreenLockManager;
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

import okhttp3.Request;
import okhttp3.Response;

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
    public static final String BIOMETRIC_PROMPT = "mobilesdk://biometric/authentication/prompt";
    private static final String TAG = "OAuthWebViewHelper";
    private static final String ACCOUNT_OPTIONS = "accountOptions";
    private static final String PROMPT_LOGIN = "&prompt=login";
    private String codeVerifier;

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
     * Constructs a new OAuthWebviewHelper and perform the initial configuration of the web view.
     *
     * @param activity Activity that's using this.
     * @param callback Callback to be triggered.
     * @param options Login options.
     * @param webview Webview instance.
     * @param savedInstanceState Bundle of saved instance.
     */
	public OAuthWebviewHelper(Activity activity, OAuthWebviewHelperEvents callback,
			LoginOptions options, WebView webview, Bundle savedInstanceState) {
        this(activity, callback, options, webview, savedInstanceState, true);
	}

    /**
     * Constructs a new OAuthWebviewHelper and perform the initial configuration of the web view.
     *
     * @param activity Activity that's using this.
     * @param callback Callback to be triggered.
     * @param options Login options.
     * @param webview Webview instance.
     * @param savedInstanceState Bundle of saved instance.
     * @param shouldReloadPage True - if page should be reloaded on relaunch, False - otherwise.
     */
    public OAuthWebviewHelper(Activity activity, OAuthWebviewHelperEvents callback, LoginOptions options,
                              WebView webview, Bundle savedInstanceState, boolean shouldReloadPage) {
        assert options != null && callback != null && webview != null && activity != null;
        this.context = webview.getContext();
        this.activity = activity;
        this.callback = callback;
        this.loginOptions = options;
        this.webview = webview;
        this.shouldReloadPage = shouldReloadPage;
        final WebSettings webSettings = webview.getSettings();
        webSettings.setJavaScriptEnabled(true);
        String origUserAgent = webSettings.getUserAgentString();
        origUserAgent = (origUserAgent == null) ? "" : origUserAgent;
        final String msdkUserAgent = SalesforceSDKManager.getInstance().getUserAgent();
        webSettings.setUserAgentString(String.format("%s %s", msdkUserAgent, origUserAgent));
        webview.setWebViewClient(makeWebViewClient());
        webview.setWebChromeClient(makeWebChromeClient());
        boolean isDarkTheme = SalesforceSDKManager.getInstance().isDarkTheme();
        activity.setTheme(isDarkTheme ? R.style.SalesforceSDK_Dark_Login : R.style.SalesforceSDK);

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

    public OAuthWebviewHelper(Context context, OAuthWebviewHelperEvents callback, LoginOptions options) {
        this.context = context;
        this.callback = callback;
        this.loginOptions = options;
        this.webview = null;
        this.activity = null;
        this.shouldReloadPage = true;
    }

    private final OAuthWebviewHelperEvents callback;
    protected final LoginOptions loginOptions;
    private final WebView webview;
    private AccountOptions accountOptions;
    private final Context context;
    private final Activity activity;
    private PrivateKey key;
    private X509Certificate[] certChain;
    private final boolean shouldReloadPage;

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

    /**
     * Returns whether the login page should be reloaded when the app is backgrounded and
     * foregrounded. By default, this is set to 'true' in the SDK, in order to support various
     * supported OAuth flows. Subclasses may override this for cases where they need to
     * display the page as-is, such as TBID or social login pages where a code is typed in.
     *
     * @return True - if the page should be reloaded, False - otherwise.
     */
    protected boolean shouldReloadPage() {
        return shouldReloadPage;
    }

    public void clearCookies() {
        CookieManager.getInstance().removeAllCookies(null);
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
        return context;
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
        SalesforceSDKLogger.e(TAG, error + ": " + errorDesc, e);

        // Broadcast a notification that the auth flow failed.
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

        // Displays the error in a Toast and reloads the login page after clearing cookies.
        activity.runOnUiThread(() -> {
            final Toast t = Toast.makeText(webview.getContext(), error + " : " + errorDesc,
                    Toast.LENGTH_LONG);
            webview.postDelayed(new Runnable() {
                @Override
                public void run() {
                    clearCookies();
                    loadLoginPage();
                }
            }, t.getDuration());
            t.show();
        });
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
            doLoadPage();
        } else {
            new SwapJWTForAccessTokenTask().execute(loginOptions);
        }
    }

    private void doLoadPage() {
        try {
            boolean isBrowserLoginEnabled = SalesforceSDKManager.getInstance().isBrowserLoginEnabled();
            boolean useWebServerAuthentication = isBrowserLoginEnabled || SalesforceSDKManager.getInstance().shouldUseWebServerAuthentication();
            boolean useHybridAuthentication = SalesforceSDKManager.getInstance().shouldUseHybridAuthentication();

            URI uri = getAuthorizationUrl(useWebServerAuthentication, useHybridAuthentication);
            callback.loadingLoginPage(loginOptions.getLoginUrl());
            if (SalesforceSDKManager.getInstance().isBrowserLoginEnabled()) {
                if(!SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled()){
                    String urlString = uri.toString();
                    uri = new URI(urlString.concat(PROMPT_LOGIN));
                }
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
        intentBuilder.setToolbarColor(getContext().getColor(R.color.sf__primary_color));

        // Adds a menu item to change server.
        final Intent changeServerIntent = new Intent(activity, ServerPickerActivity.class);
        final PendingIntent changeServerPendingIntent = PendingIntent.getActivity(activity,
                LoginActivity.PICK_SERVER_REQUEST_CODE, changeServerIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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
        if (shouldReloadPage) {
            customTabsIntent.intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        }
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

    protected URI getAuthorizationUrl(boolean useWebServerAuthentication, boolean useHybridAuthentication) throws URISyntaxException {
        boolean jwtFlow = !TextUtils.isEmpty(loginOptions.getJwt());
        Map<String, String> addlParams = jwtFlow ? null : loginOptions.getAdditionalParameters();
        // NB code verifier / code challenge are only used when useWebServerAuthentication is true
        codeVerifier = SalesforceKeyGenerator.getRandom128ByteKey();
        String codeChallenge = SalesforceKeyGenerator.getSHA256Hash(codeVerifier);
        URI authorizationUrl = OAuth2.getAuthorizationUrl(useWebServerAuthentication, useHybridAuthentication, new URI(loginOptions.getLoginUrl()), getOAuthClientId(), loginOptions.getOauthCallbackUrl(), loginOptions.getOauthScopes(), getAuthorizationDisplayType(), codeChallenge, addlParams);

        if (jwtFlow) {
            return OAuth2.getFrontdoorUrl(authorizationUrl,
                    loginOptions.getJwt(),
                    loginOptions.getLoginUrl(),
                    loginOptions.getAdditionalParameters());
        } else {
            return authorizationUrl;
        }

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
            // Hide spinner / show web view
            final RelativeLayout parentView = (RelativeLayout) view.getParent();
            if (parentView != null) {
                final ProgressBar progressBar = parentView.findViewById(R.id.sf__loading_spinner);
                if (progressBar != null) {
                    progressBar.setVisibility(View.INVISIBLE);
                }
            }
            view.setVisibility(View.VISIBLE);

            // Remove the native login buttons (biometric, IDP) once on the allow/deny screen
            if (url.contains("frontdoor.jsp")) {
                if (parentView != null) {
                    final Button idpButton = parentView.findViewById(R.id.sf__idp_login_button);
                    if (idpButton != null) {
                        idpButton.setVisibility(View.INVISIBLE);
                    }
                    final Button bioButton = parentView.findViewById(R.id.sf__bio_login_button);
                    if (bioButton != null) {
                        bioButton.setVisibility(View.INVISIBLE);
                    }
                }
            }
            EventsObservable.get().notifyEvent(EventType.AuthWebViewPageFinished, url);
            super.onPageFinished(view, url);
		}

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            boolean useWebFlowAuthentication = SalesforceSDKManager.getInstance().shouldUseWebServerAuthentication();
            Uri uri = request.getUrl();

            // Login webview embedded button has sent the signal to show the biometric prompt.
            if (uri.toString().equals(BIOMETRIC_PROMPT)) {
                com.salesforce.androidsdk.security.interfaces.BiometricAuthenticationManager bioAuthManager =
                        SalesforceSDKManager.getInstance().getBiometricAuthenticationManager();
                if (bioAuthManager.hasBiometricOptedIn() && bioAuthManager.hasBiometricOptedIn()) {
                    if (activity != null && activity instanceof LoginActivity) {
                        ((LoginActivity) activity).presentBiometric();
                    }
                }

                return true;
            }

			boolean isDone = uri.toString().replace("///", "/").toLowerCase(Locale.US).startsWith(loginOptions.getOauthCallbackUrl().replace("///", "/").toLowerCase(Locale.US));
            if (isDone) {
                Map<String, String> params = UriFragmentParser.parse(uri);
                String error = params.get("error");
                // Did we fail?
                if (error != null) {
                    String errorDesc = params.get("error_description");
                    onAuthFlowError(error, errorDesc, null);
                }
                // Or succeed?
                else {
                    if (useWebFlowAuthentication) {
                        String code = params.get("code");
                        onWebServerFlowComplete(code);
                    } else {
                        TokenEndpointResponse tr = new TokenEndpointResponse(params);
                        onAuthFlowComplete(tr);
                    }
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
    public void onAuthFlowComplete(TokenEndpointResponse tr) {
        SalesforceSDKLogger.d(TAG, "token response -> " +  tr);
        FinishAuthTask t = new FinishAuthTask();
        t.execute(tr);
    }
    protected void onWebServerFlowComplete(String code) {
        new CodeExchangeEndpointTask(code).execute();
    }

    private class CodeExchangeEndpointTask extends AsyncTask<Void, Void, TokenEndpointResponse> {

        private String code;

        public CodeExchangeEndpointTask(String code) {
            this.code = code;
        }

        @Override
        protected OAuth2.TokenEndpointResponse doInBackground(Void... nothings) {
            OAuth2.TokenEndpointResponse tokenResponse = null;
            try {
                tokenResponse = OAuth2.exchangeCode(HttpAccess.DEFAULT,
                        URI.create(loginOptions.getLoginUrl()), loginOptions.getOauthClientId(), code, codeVerifier,
                        loginOptions.getOauthCallbackUrl());
            } catch (Exception e) {
                SalesforceSDKLogger.e(TAG, "Exception occurred while making token request", e);
                onAuthFlowError("Token Request Error", e.getMessage(), e);
            }
            return tokenResponse;
        }

        @Override
        protected void onPostExecute(OAuth2.TokenEndpointResponse tokenResponse) {
            onAuthFlowComplete(tokenResponse);
        }
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
                doLoadPage();
            } else {
                doLoadPage();
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

        /**
         * Indicates if authentication is blocked for the current user due to
         * the block Salesforce integration user option.
         */
        protected volatile boolean shouldBlockSalesforceIntegrationUser = false;

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
            if (shouldBlockSalesforceIntegrationUser) {
                /*
                 * Salesforce integration users are prohibited from successfully
                 * completing authentication. This alleviates the Restricted
                 * Product Approval requirement on Salesforce Integration add-on
                 * SKUs and conforms to Legal and Product Strategy requirements.
                 */
                SalesforceSDKLogger.w(TAG, "Salesforce integration users are prohibited from successfully authenticating.");
                onAuthFlowError( // Issue the generic authentication error.
                        getContext().getString(R.string.sf__generic_authentication_error_title),
                        getContext().getString(R.string.sf__generic_authentication_error), backgroundException);
                callback.finish(null);
                return;
            }
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
                    id.displayName, id.email, id.pictureUrl, id.thumbnailUrl, tr.additionalOauthValues,
                    tr.lightningDomain, tr.lightningSid, tr.vfDomain, tr.vfSid, tr.contentDomain,
                    tr.contentSid, tr.csrfToken);

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
                    lightningDomain(accountOptions.lightningDomain).lightningSid(accountOptions.lightningSid).
                    vfDomain(accountOptions.vfDomain).vfSid(accountOptions.vfSid).
                    contentDomain(accountOptions.contentDomain).contentSid(accountOptions.contentSid).
                    csrfToken(accountOptions.csrfToken).additionalOauthValues(accountOptions.additionalOauthValues).
                    build();
            account.downloadProfilePhoto();
            if (id.customAttributes != null) {
                mgr.getAdminSettingsManager().setPrefs(id.customAttributes, account);
            }
            if (id.customPermissions != null) {
                mgr.getAdminPermsManager().setPrefs(id.customPermissions, account);
            }

            List<UserAccount> existingUsers = mgr.getUserAccountManager().getAuthenticatedUsers();
            if (existingUsers != null) {
                // Check if the user already exists
                if (existingUsers.contains(account)) {
                    UserAccount duplicateUserAccount = existingUsers.remove(existingUsers.indexOf(account));
                    RestClient.clearCaches();
                    UserAccountManager.getInstance().clearCachedCurrentUser();

                    // Revoke existing refresh token
                    if (!account.getRefreshToken().equals(duplicateUserAccount.getRefreshToken())) {
                        new RevokeTokenTask(duplicateUserAccount.getRefreshToken(),
                                duplicateUserAccount.getInstanceServer()).execute();
                    }
                }

                // If this account has Biometric Authentication enabled remove any others that also have it.
                if (id.biometricAuth) {
                    existingUsers.forEach(existingUser -> {
                        if (BiometricAuthenticationManager.Companion.isEnabled(existingUser)) {
                            activity.runOnUiThread(() -> {
                                String toastMessage = activity.getString(R.string.sf__biometric_signout_user,
                                        existingUser.getUsername());
                                Toast.makeText(activity, toastMessage, Toast.LENGTH_LONG).show();
                            });

                            mgr.getUserAccountManager().signoutUser(existingUser, activity, false);
                        }
                    });
                }
            }

            // Save the user account
            addAccount(account);

            // Screen lock required by mobile policy.
            if (id.screenLockTimeout > 0) {
                SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_SCREEN_LOCK);
                int timeoutInMills = id.screenLockTimeout * 1000 * 60;
                ((ScreenLockManager) mgr.getScreenLockManager())
                        .storeMobilePolicy(account, id.screenLock, timeoutInMills);
            }

            // Biometric Auth required by mobile policy.
            if (id.biometricAuth) {
                SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_BIOMETRIC_AUTH);
                BiometricAuthenticationManager bioAuthManager =
                        (BiometricAuthenticationManager) mgr.getBiometricAuthenticationManager();
                int timeoutInMills = id.biometricAuthTimeout * 60 * 1000;
                bioAuthManager.storeMobilePolicy(account, id.biometricAuth, timeoutInMills);
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

                // Request the authenticated user's information to determine if it is a Salesforce integration user.  This is a synchronous network request, so it must be performed here in the background stage.
                shouldBlockSalesforceIntegrationUser = SalesforceSDKManager.getInstance().shouldBlockSalesforceIntegrationUser() && fetchIsSalesforceIntegrationUser(tr);
            } catch (Exception e) {
                backgroundException = e;
            }
            return tr;
        }

        /**
         * Requests the user's information from the network and returns the
         * user's integration user state.
         *
         * @param tokenEndpointResponse The user's authentication token endpoint
         *                              response
         * @return Boolean true indicates the user is a Salesforce integration
         * user. False indicates otherwise.
         * @throws Exception Any exception that prevents returning the result
         */
        private boolean fetchIsSalesforceIntegrationUser(
                TokenEndpointResponse tokenEndpointResponse
        ) throws Exception {
            final String url = getLoginUrl() + "/services/oauth2/userinfo";
            final Request.Builder builder = new Request.Builder().url(url).get();
            OAuth2.addAuthorizationHeader(builder, tokenEndpointResponse.authToken);
            final Request request = builder.build();
            final Response response = HttpAccess.DEFAULT.getOkHttpClient().newCall(request).execute();
            final String responseString = response.body() == null ? null : response.body().string();

            return responseString != null && new JSONObject(responseString).getBoolean("is_salesforce_integration_user");
        }
    }

    /**
     * TODO: This has been duplicated from SalesforceSDKManager to keep that instance private.
     * If it remains private we don't have to deprecate and wait for a major version to replace with
     * a proper (work manager) solution.
     */
    private static class RevokeTokenTask extends AsyncTask<Void, Void, Void> {

        private final String refreshToken;
        private final String loginServer;

        public RevokeTokenTask(String refreshToken, String loginServer) {
            this.refreshToken = refreshToken;
            this.loginServer = loginServer;
        }

        @Override
        protected Void doInBackground(Void... nothings) {
            try {
                OAuth2.revokeRefreshToken(HttpAccess.DEFAULT, new URI(loginServer), refreshToken);
            } catch (Exception e) {
                SalesforceSDKLogger.w(TAG, "Revoking token failed", e);
            }
            return null;
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
                accountOptions.additionalOauthValues,
                accountOptions.lightningDomain,
                accountOptions.lightningSid,
                accountOptions.vfDomain,
                accountOptions.vfSid,
                accountOptions.contentDomain,
                accountOptions.contentSid,
                accountOptions.csrfToken);

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
        private static final String LIGHTNING_DOMAIN = "lightning_domain";
        private static final String LIGHTNING_SID = "lightning_sid";
        private static final String VF_DOMAIN = "visualforce_domain";
        private static final String VF_SID = "visualforce_sid";
        private static final String CONTENT_DOMAIN = "content_domain";
        private static final String CONTENT_SID = "content_sid";
        private static final String CSRF_TOKEN = "csrf_token";

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
        public final String lightningDomain;
        public final String lightningSid;
        public final String vfDomain;
        public final String vfSid;
        public final String contentDomain;
        public final String contentSid;
        public final String csrfToken;
        private Bundle bundle;

        public AccountOptions(String username, String refreshToken,
                String authToken, String identityUrl, String instanceUrl,
                String orgId, String userId, String communityId, String communityUrl,
                String firstName, String lastName, String displayName, String email,
                String photoUrl, String thumbnailUrl, Map<String, String> additionalOauthValues,
                String lightningDomain, String lightningSid, String vfDomain, String vfSid,
                String contentDomain, String contentSid, String csrfToken) {
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
            this.lightningDomain = lightningDomain;
            this.lightningSid = lightningSid;
            this.vfDomain = vfDomain;
            this.vfSid = vfSid;
            this.contentDomain = contentDomain;
            this.contentSid = contentSid;
            this.csrfToken = csrfToken;
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
            bundle.putString(LIGHTNING_DOMAIN, lightningDomain);
            bundle.putString(LIGHTNING_SID, lightningSid);
            bundle.putString(VF_DOMAIN, vfDomain);
            bundle.putString(VF_SID, vfSid);
            bundle.putString(CONTENT_DOMAIN, contentDomain);
            bundle.putString(CONTENT_SID, contentSid);
            bundle.putString(CSRF_TOKEN, csrfToken);
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
                    getAdditionalOauthValues(options),
                    options.getString(LIGHTNING_DOMAIN),
                    options.getString(LIGHTNING_SID),
                    options.getString(VF_DOMAIN),
                    options.getString(VF_SID),
                    options.getString(CONTENT_DOMAIN),
                    options.getString(CONTENT_SID),
                    options.getString(CSRF_TOKEN)
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
