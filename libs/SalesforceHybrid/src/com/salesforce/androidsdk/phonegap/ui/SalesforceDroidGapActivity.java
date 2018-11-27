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
package com.salesforce.androidsdk.phonegap.ui;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.HttpAccess.NoNetworkException;
import com.salesforce.androidsdk.config.BootConfig;
import com.salesforce.androidsdk.config.LoginServerManager;
import com.salesforce.androidsdk.phonegap.app.SalesforceHybridSDKManager;
import com.salesforce.androidsdk.phonegap.util.SalesforceHybridLogger;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.AccountInfoNotFoundException;
import com.salesforce.androidsdk.rest.ClientManager.RestClientCallback;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestClient.AsyncRequestCallback;
import com.salesforce.androidsdk.rest.RestClient.ClientInfo;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.ui.SalesforceActivityDelegate;
import com.salesforce.androidsdk.ui.SalesforceActivityInterface;
import com.salesforce.androidsdk.util.AuthConfigUtil;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaActivity;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaWebViewEngine;
import org.apache.cordova.CordovaWebViewImpl;
import org.json.JSONObject;

import java.net.URI;

import okhttp3.HttpUrl;

/**
 * Class that defines the main activity for a PhoneGap-based application.
 */
public class SalesforceDroidGapActivity extends CordovaActivity implements SalesforceActivityInterface {

    private static final String TAG = "SfDroidGapActivity";

    // Delegate
    private final SalesforceActivityDelegate delegate;

    // Rest client
    private RestClient client;
    private ClientManager clientManager;

    // Config
    private BootConfig bootconfig;
    private AuthConfigUtil.MyDomainAuthConfig authConfig;

    // Web app loaded?
    private boolean webAppLoaded = false;

    public SalesforceDroidGapActivity() {
        super();
        delegate = new SalesforceActivityDelegate(this);
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        init();

        // Get bootconfig
        bootconfig = BootConfig.getBootConfig(this);

        // Get clientManager
        clientManager = buildClientManager();

        // Setup global stores and syncs defined in static configs
        SalesforceHybridSDKManager.getInstance().setupGlobalStoreFromDefaultConfig();
        SalesforceHybridSDKManager.getInstance().setupGlobalSyncsFromDefaultConfig();

        // Delegate create
        delegate.onCreate();
    }

    protected ClientManager buildClientManager() {
        return new ClientManager(this, SalesforceSDKManager.getInstance().getAccountType(),
                SalesforceSDKManager.getInstance().getLoginOptions(),
                SalesforceSDKManager.getInstance().shouldLogoutWhenTokenRevoked());
    }

    @Override
    public void init() {
        super.init();
        EventsObservable.get().notifyEvent(EventType.GapWebViewCreateComplete, appView);
    }

    @Override
    protected CordovaWebViewEngine makeWebViewEngine() {
        final String className = SalesforceWebViewEngine.class.getCanonicalName();
        preferences.set("webview", className);
        return CordovaWebViewImpl.createEngine(this, preferences);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Fetches auth config if required.
        try {
            (new FetchAuthConfigTask()).execute().get();
        } catch (Exception e) {
            SalesforceHybridLogger.e(TAG, "Exception occurred while fetching auth config", e);
        }
        delegate.onResume(false);
        // will call this.onResume(RestClient client) with a null client
    }

    @Override
    public void onResume(RestClient c) {
        // Called from delegate with null

        // Get client (if already logged in)
        try {
            client = clientManager.peekRestClient();
        } catch (AccountInfoNotFoundException e) {
            client = null;
        }

        // Not logged in
        if (client == null) {
            if (!webAppLoaded) {
                onResumeNotLoggedIn();
            } else {
                SalesforceHybridLogger.i(TAG, "onResume - unauthenticated web app already loaded");
            }
        }

        // Logged in
        else {

            // Web app never loaded
            if (!webAppLoaded) {
                onResumeLoggedInNotLoaded();
            }

            // Web app already loaded
            else {
                SalesforceHybridLogger.i(TAG, "onResume - already logged in/web app already loaded");
            }
        }
    }

    /**
     * Returns the auth config associated with the current login server, if it exists.
     *
     * @return Auth config.
     */
    public AuthConfigUtil.MyDomainAuthConfig getAuthConfig() {
        return authConfig;
    }

    /**
     * Called when resuming activity and user is not authenticated
     */
    private void onResumeNotLoggedIn() {
        try {
            BootConfig.validateBootConfig(bootconfig);

            // Need to be authenticated
            if (bootconfig.shouldAuthenticate()) {

                // Online
                if (SalesforceSDKManager.getInstance().hasNetwork()) {
                    SalesforceHybridLogger.i(TAG, "onResumeNotLoggedIn - should authenticate/online - authenticating");
                    authenticate(null);
                }

                // Offline
                else {
                    SalesforceHybridLogger.w(TAG, "onResumeNotLoggedIn - should authenticate/offline - can not proceed");
                    loadErrorPage();
                }
            }

            // Does not need to be authenticated
            else {

                // Local
                if (bootconfig.isLocal()) {
                    SalesforceHybridLogger.i(TAG, "onResumeNotLoggedIn - should not authenticate/local start page - loading web app");
                    loadLocalStartPage();
                }

                // Remote
                else {
                    SalesforceHybridLogger.w(TAG, "onResumeNotLoggedIn - should not authenticate/remote start page - loading web app");
                    loadRemoteStartPage(bootconfig.getUnauthenticatedStartPage(), false);
                }
            }
        } catch (BootConfig.BootConfigException e) {
            SalesforceHybridLogger.w(TAG, "onResumeNotLoggedIn - Boot config did not pass validation: "
                    + e.getMessage()
                    + " - cannot proceed");
            loadErrorPage();
        }
    }

    /**
     * Called when resuming activity and user is authenticated but webview has not been loaded yet
     */
    private void onResumeLoggedInNotLoaded() {

        // Setup user stores and syncs defined in static configs
        SalesforceHybridSDKManager.getInstance().setupUserStoreFromDefaultConfig();
        SalesforceHybridSDKManager.getInstance().setupUserSyncsFromDefaultConfig();

        // Local
        if (bootconfig.isLocal()) {
            SalesforceHybridLogger.i(TAG, "onResumeLoggedInNotLoaded - local start page - loading web app");
            loadLocalStartPage();
        }

        // Remote
        else {

            // Online
            if (SalesforceSDKManager.getInstance().hasNetwork()) {
                SalesforceHybridLogger.i(TAG, "onResumeLoggedInNotLoaded - remote start page/online - loading web app");
                loadRemoteStartPage(bootconfig.getStartPage(), true);
            }

            // Offline
            else {
                // Has cached version
                if (SalesforceWebViewClientHelper.hasCachedAppHome(this)) {
                    SalesforceHybridLogger.i(TAG, "onResumeLoggedInNotLoaded - remote start page/offline/cached - loading cached web app");
                    loadCachedStartPage();
                }

                // No cached version
                else {
                    SalesforceHybridLogger.i(TAG, "onResumeLoggedInNotLoaded - remote start page/offline/not cached - can not proceed");
                    loadErrorPage();
                }
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        delegate.onPause();
    }

    @Override
    public void onDestroy() {
        delegate.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onUserInteraction() {
        delegate.onUserInteraction();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return delegate.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event);
    }

    public BootConfig getBootConfig() {
        return bootconfig;
    }

    public RestClient getRestClient() {
        return client;
    }

    public void logout(CallbackContext callbackContext) {
        SalesforceHybridLogger.i(TAG, "logout called");
        SalesforceSDKManager.getInstance().logout(this);
        if (callbackContext != null) {
            callbackContext.success();
        }
    }

    /**
     * Get a RestClient and refresh the auth token
     *
     * @param callbackContext when not null credentials/errors are sent through to callbackContext.success()/error()
     */
    public void authenticate(final CallbackContext callbackContext) {
        SalesforceHybridLogger.i(TAG, "authenticate called");
        clientManager.getRestClient(this, new RestClientCallback() {

            @Override
            public void authenticatedRestClient(RestClient client) {
                if (client == null) {
                    SalesforceHybridLogger.i(TAG, "authenticate callback triggered with null client");
                    logout(null);
                } else {
                    SalesforceHybridLogger.i(TAG, "authenticate callback triggered with actual client");
                    SalesforceDroidGapActivity.this.client = client;

                    /*
                     * Do a cheap REST call to refresh the access token if needed.
                     * If the login took place a while back (e.g. the already logged
                     * in application was restarted), then the returned session ID
                     * (access token) might be stale. This is not an issue if one
                     * uses exclusively RestClient for calling the server because
                     * it takes care of refreshing the access token when needed,
                     * but a stale session ID will cause the WebView to redirect
                     * to the web login.
                     */
                    SalesforceDroidGapActivity.this.client.sendAsync(RestRequest.getRequestForUserInfo(), new AsyncRequestCallback() {

                        @Override
                        public void onSuccess(RestRequest request, RestResponse response) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    /*
                                     * The client instance being used here needs to be
                                     * refreshed, to ensure we use the new access token.
                                     */
                                    SalesforceDroidGapActivity.this.client = SalesforceDroidGapActivity.this.clientManager.peekRestClient();
                                    setSidCookies();
                                    loadVFPingPage();
                                    getAuthCredentials(callbackContext);
                                }
                            });
                        }

                        @Override
                        public void onError(Exception exception) {
                            if (callbackContext != null) {
                                callbackContext.error(exception.getMessage());
                            }
                        }
                    });
                }
            }
        });
    }

    /**
     * Get json for credentials
     *
     * @param callbackContext
     */
    public void getAuthCredentials(CallbackContext callbackContext) {
        SalesforceHybridLogger.i(TAG, "getAuthCredentials called");
        if (client != null) {
            JSONObject credentials = client.getJSONCredentials();
            if (callbackContext != null) {
                callbackContext.success(credentials);
            }
        } else {
            if (callbackContext != null) {
                callbackContext.error("Never authenticated");
            }
        }
    }

    /**
     * If an action causes a redirect to the login page, this method will be called.
     * It causes the session to be refreshed and reloads url through the front door.
     *
     * @param url the page to load once the session has been refreshed.
     */
    public void refresh(final String url) {
        SalesforceHybridLogger.i(TAG, "refresh called");
        client.sendAsync(RestRequest.getRequestForUserInfo(), new AsyncRequestCallback() {

            @Override
            public void onSuccess(RestRequest request, RestResponse response) {
                SalesforceHybridLogger.i(TAG, "refresh callback - refresh succeeded");
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        /*
                         * The client instance being used here needs to be
                         * refreshed, to ensure we use the new access token.
                         */
                        SalesforceDroidGapActivity.this.client = SalesforceDroidGapActivity.this.clientManager.peekRestClient();
                        setSidCookies();
                        loadVFPingPage();
                        final String frontDoorUrl = getFrontDoorUrl(url, BootConfig.isAbsoluteUrl(url));
                        loadUrl(frontDoorUrl);
                    }
                });
            }

            @Override
            public void onError(Exception exception) {
                SalesforceHybridLogger.w(TAG, "refresh callback - refresh failed", exception);

                // Only logout if we are NOT offline
                if (!(exception instanceof NoNetworkException)) {
                    logout(null);
                }
            }
        });
    }

    /**
     * Loads the VF ping page and sets cookies.
     */
    private void loadVFPingPage() {
        if (!bootconfig.isLocal()) {
            final ClientInfo clientInfo = SalesforceDroidGapActivity.this.client.getClientInfo();
            URI instanceUrl = null;
            if (clientInfo != null) {
                instanceUrl = clientInfo.getInstanceUrl();
            }
            setVFCookies(instanceUrl);
        }
    }

    /**
     * Sets VF domain cookies by loading the VF ping page on an invisible WebView.
     *
     * @param instanceUrl Instance URL.
     */
    private static void setVFCookies(URI instanceUrl) {
        if (instanceUrl != null) {
            final WebView view = new WebView(SalesforceSDKManager.getInstance().getAppContext());
            view.setVisibility(View.GONE);
            view.setWebViewClient(new WebViewClient() {

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    final CookieManager cookieMgr = CookieManager.getInstance();
                    cookieMgr.setAcceptCookie(true);
                    SalesforceSDKManager.getInstance().syncCookies();
                    return true;
                }
            });
            view.loadUrl(instanceUrl.toString() + "/visualforce/session?url=/apexpages/utils/ping.apexp&autoPrefixVFDomain=true");
        }
    }

    /**
     * Load local start page
     */
    public void loadLocalStartPage() {
        assert bootconfig.isLocal();
        String startPage = bootconfig.getStartPage();
        SalesforceHybridLogger.i(TAG, "loadLocalStartPage called - loading: " + startPage);
        loadUrl("file:///android_asset/www/" + startPage);
        webAppLoaded = true;
    }

    /**
     * Load remote start page (front-doored)
     */
    public void loadRemoteStartPage() {
        loadRemoteStartPage(bootconfig.getStartPage(), true);
    }

    /**
     * Load the remote start page.
     * @param startPageUrl The start page to load.
     * @param loadThroughFrontDoor Whether or not to load through front-door.
     */
    private void loadRemoteStartPage(String startPageUrl, boolean loadThroughFrontDoor) {
        assert !bootconfig.isLocal();
        String url = startPageUrl;
        if (loadThroughFrontDoor) {
            url = getFrontDoorUrl(url, BootConfig.isAbsoluteUrl(url));
        }
        SalesforceHybridLogger.i(TAG, "loadRemoteStartPage called - loading: " + url);
        loadUrl(url);
        webAppLoaded = true;
    }

    /**
     * Returns the front-doored URL of a URL passed in.
     *
     * @param url      URL to be front-doored.
     * @param isAbsUrl True - if the URL should be used as is, False - otherwise.
     * @return Front-doored URL.
     */
    public String getFrontDoorUrl(String url, boolean isAbsUrl) {

        /*
         * We need to use the absolute URL in some cases and relative URL in some
         * other cases, because of differences between instance URL and community
         * URL. Community URL can be custom and the logic of determining which
         * URL to use is in the 'resolveUrl' method in 'ClientInfo'.
         */
        url = (isAbsUrl ? url : client.getClientInfo().resolveUrl(url).toString());
        final HttpUrl frontDoorUrl = HttpUrl.parse(client.getClientInfo().getInstanceUrlAsString()
                + "/secur/frontdoor.jsp?").newBuilder()
                .addQueryParameter("sid", client.getAuthToken())
                .addQueryParameter("retURL", url)
                .addQueryParameter("display", "touch")
                .build();
        return frontDoorUrl.toString();
    }

    /**
     * Load cached start page
     */
    private void loadCachedStartPage() {
        String url = SalesforceWebViewClientHelper.getAppHomeUrl(this);
        loadUrl(url);
        webAppLoaded = true;
    }

    /**
     * Load error page
     */
    public void loadErrorPage() {
        String errorPage = bootconfig.getErrorPage();
        SalesforceHybridLogger.i(TAG, "getErrorPageUrl called - local error page: " + errorPage);
        loadUrl("file:///android_asset/www/" + errorPage);
    }

    /**
     * Returns the WebView being used.
     *
     * @return WebView being used.
     */
    public CordovaWebView getAppView() {
        return appView;
    }

    /**
     * Set cookies on cookie manager.
     */
    private void setSidCookies() {
        SalesforceHybridLogger.i(TAG, "setSidCookies called");
        CookieManager cookieMgr = CookieManager.getInstance();
        cookieMgr.setAcceptCookie(true);  // Required to set additional cookies that the auth process will return.
        SalesforceSDKManager.getInstance().removeSessionCookies();
        SystemClock.sleep(250); // removeSessionCookies kicks out a thread - let it finish
        String accessToken = client.getAuthToken();
        addSidCookieForInstance(cookieMgr, accessToken);
        SalesforceSDKManager.getInstance().syncCookies();
    }

    private void addSidCookieForInstance(CookieManager cookieMgr, String sid) {
        final ClientInfo clientInfo = SalesforceDroidGapActivity.this.client.getClientInfo();
        URI instanceUrl = null;
        if (clientInfo != null) {
            instanceUrl = clientInfo.getInstanceUrl();
        }
        String host = null;
        if (instanceUrl != null) {
            host = instanceUrl.getHost();
        }
        if (host != null) {
            addSidCookieForDomain(cookieMgr, host, sid);
        }
    }

    private void addSidCookieForDomain(CookieManager cookieMgr, String domain, String sid) {
        String cookieStr = "sid=" + sid;
        cookieMgr.setCookie(domain, cookieStr);
    }

    @Override
    public void onLogoutComplete() {
    }

    @Override
    public void onUserSwitched() {
        if (client != null) {
            try {
                RestClient currentClient = clientManager.peekRestClient();
                if (currentClient != null && !currentClient.getClientInfo().userId.equals(client.getClientInfo().userId)) {
                    this.recreate();
                }
            } catch (AccountInfoNotFoundException e) {
                SalesforceHybridLogger.i(TAG, "restartIfUserSwitched - no user account found");
            }
        }
    }

    private class FetchAuthConfigTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... nothings) {
            final String loginServer = SalesforceHybridSDKManager.getInstance().getLoginServerManager().getSelectedLoginServer().url.trim();
            if (loginServer.equals(LoginServerManager.PRODUCTION_LOGIN_URL) ||
                    loginServer.equals(LoginServerManager.SANDBOX_LOGIN_URL) ||
                    !URLUtil.isHttpsUrl(loginServer) || HttpUrl.parse(loginServer) == null) {
                return null;
            }
            authConfig = AuthConfigUtil.getMyDomainAuthConfig(loginServer);
            return null;
        }
    }
}
