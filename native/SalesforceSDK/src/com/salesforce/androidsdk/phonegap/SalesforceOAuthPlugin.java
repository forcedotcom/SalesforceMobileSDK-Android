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
package com.salesforce.androidsdk.phonegap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.cordova.DroidGap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebView;

import com.phonegap.api.Plugin;
import com.phonegap.api.PluginResult;
import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.auth.HttpAccess.NoNetworkException;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;
import com.salesforce.androidsdk.rest.ClientManager.RestClientCallback;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestClient.AsyncRequestCallback;
import com.salesforce.androidsdk.rest.RestClient.ClientInfo;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.ui.SalesforceGapViewClient;

/**
 * PhoneGap plugin for Salesforce OAuth.
 */
public class SalesforceOAuthPlugin extends Plugin {
    // Keys in oauth properties map
    private static final String AUTO_REFRESH_ON_FOREGROUND = "autoRefreshOnForeground";
    private static final String AUTO_REFRESH_PERIODICALLY = "autoRefreshPeriodically";
    private static final String OAUTH_REDIRECT_URI = "oauthRedirectURI";
    private static final String OAUTH_SCOPES = "oauthScopes";
    private static final String REMOTE_ACCESS_CONSUMER_KEY = "remoteAccessConsumerKey";

    // Keys in credentials map
    private static final String USER_AGENT = "userAgent";
    private static final String INSTANCE_URL = "instanceUrl";
    private static final String LOGIN_URL = "loginUrl";
    private static final String IDENTITY_URL = "identityUrl";
    private static final String CLIENT_ID = "clientId";
    private static final String ORG_ID = "orgId";
    private static final String USER_ID = "userId";
    private static final String REFRESH_TOKEN = "refreshToken";
    private static final String ACCESS_TOKEN = "accessToken";

    // Min refresh interval (needs to be shorter than shortest session setting)
    private static final int MIN_REFRESH_INTERVAL_MILLISECONDS = 10*60*1000; // 10 minutes

    // Used in refresh REST call
    private static final String API_VERSION = "v23.0";


    /**
     * Supported plugin actions that the client can take.
     */
    enum Action {
        authenticate,
        getAuthCredentials,
        logoutCurrentUser,
        getAppHomeUrl
    }

    /* static because it needs to survive plugin being torn down when a new URL is loaded */
    private static ClientManager clientManager;
    private static RestClient client;
    private static boolean autoRefreshOnForeground;
    private static boolean autoRefreshPeriodically;
    private static long lastRefreshTime = -1;

    /**
     * If auto-refresh on foreground is enabled, this method will be called when the app resumes.
     * If auto-refresh periodically is enabled, this method will be called periodically.
     * Does a cheap rest call:
     * - if session has already expired, the access token will be refreshed
     * - otherwise it will get extended
     * @param webView the WebView running the application.
     * @param ctx the Phonegap activity for the app.
     */
    public static void autoRefresh(final WebView webView, final DroidGap ctx) {
        Log.i("SalesforceOAuthPlugin.autoRefresh", "autoRefresh called");
        // Do a cheap rest call - access token will be refreshed if needed
        client.sendAsync(RestRequest.getRequestForResources(API_VERSION), new AsyncRequestCallback() {
            @Override
            public void onSuccess(RestRequest request, RestResponse response) {
                Log.i("SalesforceOAuthPlugin.autoRefreshIfNeeded", "Auto-refresh succeeded");
                updateRefreshTime();
                setSidCookies(webView, SalesforceOAuthPlugin.client);
                Log.i("SalesforceOAuthPlugin.autoRefreshIfNeeded", "Firing salesforceSessionRefresh event");
                ctx.sendJavascript("cordova.fireDocumentEvent('salesforceSessionRefresh'," + getJSONCredentials(SalesforceOAuthPlugin.client).toString() + ");");
            }

            @Override
            public void onError(Exception exception) {
                Log.w("SalesforceOAuthPlugin.autoRefreshIfNeeded", "Auto-refresh failed - " + exception);

                // Only logout if we are NOT offline
                if (!(exception instanceof NoNetworkException)) {
                    logout(ctx);
                }
            }
        });
    }

    /**
     * Executes the plugin request and returns PluginResult.
     *
     * @param actionStr     The action to execute.
     * @param args          JSONArray of arguments for the plugin.
     * @param callbackId    The callback ID used when calling back into JavaScript.
     * @return              A PluginResult object with a status and message.
     */
    public PluginResult execute(String actionStr, JSONArray args, String callbackId) {
        Log.i("SalesforceOAuthPlugin.execute", "actionStr: " + actionStr);
        // Figure out action
        Action action = null;
        try {
            action = Action.valueOf(actionStr);
            switch(action) {
                case authenticate:       	return authenticate(args, callbackId);
                case getAuthCredentials: 	return getAuthCredentials(callbackId);
                case logoutCurrentUser:		return logoutCurrentUser();
                case getAppHomeUrl:			return this.getAppHomeUrl(callbackId);
                default: return new PluginResult(PluginResult.Status.INVALID_ACTION, actionStr); // should never happen
            }
        }
        catch (IllegalArgumentException e) {
            return new PluginResult(PluginResult.Status.INVALID_ACTION, e.getMessage());
        }
        catch (JSONException e) {
            return new PluginResult(PluginResult.Status.JSON_EXCEPTION, e.getMessage());
        }
    }

    /**
     * Native implementation for "authenticate" action
     * @param args The arguments used for authentication.
     * @param callbackId The callback ID used when calling back into Javascript.
     * @return NO_RESULT since authentication is asynchronous.
     * @throws JSONException
     */
    protected PluginResult authenticate(JSONArray args, final String callbackId) throws JSONException {
        Log.i("SalesforceOAuthPlugin.authenticate", "authenticate called");
        JSONObject oauthProperties = new JSONObject((String) args.get(0));
        LoginOptions loginOptions = parseLoginOptions(oauthProperties);
        clientManager = new ClientManager(ctx.getContext(), ForceApp.APP.getAccountType(), loginOptions);
        autoRefreshOnForeground = oauthProperties.getBoolean(AUTO_REFRESH_ON_FOREGROUND);
        autoRefreshPeriodically = oauthProperties.getBoolean(AUTO_REFRESH_PERIODICALLY);
        clientManager.getRestClient((Activity) ctx, new RestClientCallback() {
            @Override
            public void authenticatedRestClient(RestClient c) {
                if (c == null) {
                    Log.w("SalesforceOAuthPlugin.authenticate", "authenticate failed - logging out");
                    logout((Activity) ctx);
                }
                else {
                    Log.i("SalesforceOAuthPlugin.authenticate", "authenticate successful");
                    SalesforceOAuthPlugin.client = c;

                    // Do a cheap rest call - access token will be refreshed if needed
                    // If the login took place a while back (e.g. the already logged in application was restarted)
                    // Then the returned session id (access token) might be stale
                    // It's not an issue if one uses exclusively RestClient for calling the server
                    // because it takes care of refreshing the access token when needed
                    // But a stale session id will cause the webview to redirect to the web login
                    SalesforceOAuthPlugin.client.sendAsync(RestRequest.getRequestForResources(API_VERSION), new AsyncRequestCallback() {
                        @Override
                        public void onSuccess(RestRequest request, RestResponse response) {
                            updateRefreshTime();
                            setSidCookies(webView, SalesforceOAuthPlugin.client);
                            success(new PluginResult(PluginResult.Status.OK, getJSONCredentials(SalesforceOAuthPlugin.client)), callbackId);
                        }

                        @Override
                        public void onError(Exception exception) {
                            error(exception.getMessage(), callbackId);
                        }
                    });
                }
            }
        });

        // Done
        PluginResult noop = new PluginResult(PluginResult.Status.NO_RESULT);
        noop.setKeepCallback(true);
        return noop;
    }

    /**
     * Native implementation for "getAuthCredentials" action.
     * @param callbackId The callback ID used when calling back into Javascript.
     * @return The plugin result (ok if authenticated, error otherwise).
     * @throws JSONException
     */
    protected PluginResult getAuthCredentials(String callbackId) throws JSONException {
        Log.i("SalesforceOAuthPlugin.getAuthCredentials", "getAuthCredentials called");
        if (client == null) {
            Log.w("SalesforceOAuthPlugin.getAuthCredentials", "getAuthCredentials failed - never authenticated");
            return new PluginResult(PluginResult.Status.ERROR, "Never authenticated");
        }
        else {
            Log.i("SalesforceOAuthPlugin.getAuthCredentials", "getAuthCredentials successful");
            return new PluginResult(PluginResult.Status.OK, getJSONCredentials(client));
        }
    }

    protected PluginResult getAppHomeUrl(String callbackId)  {
        Log.i("SalesforceOAuthPlugin.getAppHomeUrl", "getAppHomeUrl called");

        SharedPreferences sp = ((Activity) this.ctx).getSharedPreferences(SalesforceGapViewClient.SFDC_WEB_VIEW_CLIENT_SETTINGS, Context.MODE_PRIVATE);
        String url = sp.getString(SalesforceGapViewClient.APP_HOME_URL_PROP_KEY, null);

        PluginResult result = new PluginResult(PluginResult.Status.OK,url);
        return result;
    }



    /**
     * Native implementation for "logout" action
     * @return This should always return the ok plugin result.
     */
    protected PluginResult logoutCurrentUser() {
        Log.i("SalesforceOAuthPlugin.logoutCurrentUser", "logoutCurrentUser called");
        logout((Activity) ctx);
        return new PluginResult(PluginResult.Status.OK);
    }

    /**************************************************************************************************
     *
     * Helper methods for auto-refresh
     *
     **************************************************************************************************/

    /**
     * @return true if periodic auto-refresh should take place now
     */
    public static boolean shouldAutoRefreshPeriodically() {
        Log.i("SalesforceOAuthPlugin.shouldAutoRefreshPeriodically", "" + autoRefreshPeriodically);
        return autoRefreshPeriodically;
    }

    /**
     * @return true if periodic auto-refresh on foreground should take place now
     */
    public static boolean shouldAutoRefreshOnForeground() {
        boolean b = autoRefreshOnForeground
                && lastRefreshTime > 0 // we have authenticated
                &&  (System.currentTimeMillis() - lastRefreshTime > MIN_REFRESH_INTERVAL_MILLISECONDS);
        Log.i("SalesforceOAuthPlugin.shouldAutoRefreshOnForeground", "" + b);
        return b;
    }

    /**
     * Update last refresh time to avoid un-necessary auto-refresh
     */
    private static void updateRefreshTime() {
        Log.i("SalesforceOAuthPlugin.updateRefreshTime", "lastRefreshTime before: " + lastRefreshTime);
        lastRefreshTime = System.currentTimeMillis();
        Log.i("SalesforceOAuthPlugin.updateRefreshTime", "lastRefreshTime after: " + lastRefreshTime);
    }

    /**************************************************************************************************
     *
     * Helper methods for building js credentials
     *
     **************************************************************************************************/

    /**
     * @return credentials as JSONObject
     */
    private static JSONObject getJSONCredentials(RestClient client) {
        ClientInfo clientInfo = client.getClientInfo();
        Map<String, String> data = new HashMap<String, String>();
        data.put(ACCESS_TOKEN, client.getAuthToken());
        data.put(REFRESH_TOKEN, client.getRefreshToken());
        data.put(USER_ID, clientInfo.userId);
        data.put(ORG_ID, clientInfo.orgId);
        data.put(CLIENT_ID, clientInfo.clientId);
        data.put(LOGIN_URL, clientInfo.loginUrl.toString());
        data.put(IDENTITY_URL, clientInfo.identityUrl.toString());
        data.put(INSTANCE_URL, clientInfo.instanceUrl.toString());
        data.put(USER_AGENT, ForceApp.APP.getUserAgent());
        return new JSONObject(data);
    }


    /**************************************************************************************************
     *
     * Helper methods for parsing oauth properties
     *
     **************************************************************************************************/

    private LoginOptions parseLoginOptions(JSONObject oauthProperties) throws JSONException {
        JSONArray scopesJson = oauthProperties.getJSONArray(OAUTH_SCOPES);
        String[] scopes = jsonArrayToArray(scopesJson);

        LoginOptions loginOptions = new LoginOptions(
                null, // set by app
                ForceApp.APP.getPasscodeHash(),
                oauthProperties.getString(OAUTH_REDIRECT_URI),
                oauthProperties.getString(REMOTE_ACCESS_CONSUMER_KEY),
                scopes);

        return loginOptions;
    }

    private String[] jsonArrayToArray(JSONArray jsonArray) throws JSONException {
        List<String> list = new ArrayList<String>(jsonArray.length());
        for (int i=0; i<jsonArray.length(); i++) {
            list.add(jsonArray.getString(i));
        }
        return list.toArray(new String[0]);
    }


    /**
     * Logout and reset static fields
     * @param ctx
     */
    protected static void logout(Activity ctx) {
        ForceApp.APP.logout(ctx);
        SalesforceOAuthPlugin.client = null;
        SalesforceOAuthPlugin.autoRefreshOnForeground = false;
        SalesforceOAuthPlugin.autoRefreshPeriodically = false;
        SalesforceOAuthPlugin.lastRefreshTime = -1;
    }


    /**************************************************************************************************
     *
     * Helper methods for managing cookies
     *
     **************************************************************************************************/

    /**
     * Set cookies on cookie manager
     * @param client
     */
    private static void setSidCookies(WebView webView, RestClient client) {
        Log.i("SalesforceOAuthPlugin.setSidCookies", "setting cookies");
        CookieSyncManager cookieSyncMgr = CookieSyncManager.getInstance();

        CookieManager cookieMgr = CookieManager.getInstance();
        cookieMgr.setAcceptCookie(true);  // Required to set additional cookies that the auth process will return.
        cookieMgr.removeSessionCookie();

        SystemClock.sleep(250); // removeSessionCookies kicks out a thread - let it finish

        String accessToken = client.getAuthToken();

        // Android 3.0+ clients want to use the standard .[domain] format. Earlier clients will only work
        // with the [domain] format.  Set them both; each platform will leverage its respective format.
        addSidCookieForDomain(cookieMgr,"salesforce.com", accessToken);
        addSidCookieForDomain(cookieMgr,".salesforce.com", accessToken);
        // Log.i("SalesforceOAuthPlugin.setSidCookies", "accessToken=" + accessToken);

        cookieSyncMgr.sync();
    }

    private static void addSidCookieForDomain(CookieManager cookieMgr, String domain, String sid) {
        String cookieStr = "sid=" + sid;
        cookieMgr.setCookie(domain, cookieStr);
    }

}
