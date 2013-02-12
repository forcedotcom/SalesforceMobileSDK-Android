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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.cordova.api.CallbackContext;
import org.apache.cordova.api.PluginResult;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
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
import com.salesforce.androidsdk.ui.SalesforceDroidGapActivity;
import com.salesforce.androidsdk.ui.SalesforceGapViewClient;

/**
 * PhoneGap plugin for Salesforce OAuth.
 */
public class SalesforceOAuthPlugin extends ForcePlugin {
	// Keys in oauth properties map
    private static final String OAUTH_REDIRECT_URI = "oauthRedirectURI";
    private static final String OAUTH_SCOPES = "oauthScopes";
    private static final String REMOTE_ACCESS_CONSUMER_KEY = "remoteAccessConsumerKey";
    private static final String OAUTH_PROPERTIES = "oauthProperties";
    
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
    
    // Used in refresh REST call
	private static final String API_VERSION = "v26.0";

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

    /**
     * If an action causes a redirect to the login page, this method will be called.
     * It causes the session to be refreshed and reloads url through the front door.
     * @param ctx the calling activity
     * @param webView the WebView running the application.
     * @param url the page to load once the session has been refreshed.
     */
    public static void refresh(final Activity ctx, final WebView webView, final String url) {
        Log.i("SalesforceOAuthPlugin.refresh", "refresh called");
        // Do a cheap rest call - access token will be refreshed if needed
        client.sendAsync(RestRequest.getRequestForResources(API_VERSION), new AsyncRequestCallback() {
                @Override
                public void onSuccess(RestRequest request, RestResponse response) {
                    Log.i("SalesforceOAuthPlugin.refresh", "Refresh succeeded");
                    setSidCookies(webView, SalesforceOAuthPlugin.client);
                    // XXX Does it still make sense to fire a refresh event?
                    // Log.i("SalesforceOAuthPlugin.refresh", "Firing salesforceSessionRefresh event");
                    // ctx.sendJavascript("cordova.fireDocumentEvent('salesforceSessionRefresh'," + getJSONCredentials(SalesforceOAuthPlugin.client).toString() + ");");
                    String frontDoorUrl = getFrontDoorUrl(url);
                    ((SalesforceDroidGapActivity) ctx).loadUrl(frontDoorUrl);
                }

                @Override
                public void onError(Exception exception) {
                    Log.w("SalesforceOAuthPlugin.refresh", "Refresh failed - " + exception);

                    // Only logout if we are NOT offline
                    if (!(exception instanceof NoNetworkException)) {
                        logout(((SalesforceDroidGapActivity) ctx));
                    }
                }
            });
    }    
    
    @Override
    public boolean execute(String actionStr, JavaScriptPluginVersion jsVersion, JSONArray args, CallbackContext callbackContext) throws JSONException {
        // Figure out action
        Action action = null;
        try {
            action = Action.valueOf(actionStr);
            switch(action) {
                case authenticate:       	authenticate(args, callbackContext); return true;
                case getAuthCredentials: 	getAuthCredentials(callbackContext); return true;
                case logoutCurrentUser:		logoutCurrentUser(callbackContext); return true;
                case getAppHomeUrl:			getAppHomeUrl(callbackContext); return true;
                default: return false;
            }
        }
        catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Native implementation for "authenticate" action
     * @param args The arguments used for authentication.
     * @param callbackContext Used when calling back into Javascript.
     * @return NO_RESULT since authentication is asynchronous.
     * @throws JSONException
     */
    protected void authenticate(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        Log.i("SalesforceOAuthPlugin.authenticate", "authenticate called");
        JSONObject oauthProperties = args.getJSONObject(0).getJSONObject(OAUTH_PROPERTIES);
        LoginOptions loginOptions = parseLoginOptions(oauthProperties);
        clientManager = new ClientManager(cordova.getActivity(), ForceApp.APP.getAccountType(), loginOptions, ForceApp.APP.shouldLogoutWhenTokenRevoked());
        clientManager.getRestClient(cordova.getActivity(), new RestClientCallback() {

        	@Override
            public void authenticatedRestClient(RestClient c) {
                if (c == null) {
                    Log.w("SalesforceOAuthPlugin.authenticate", "authenticate failed - logging out");
                    logout(cordova.getActivity());
                } else {
                    Log.i("SalesforceOAuthPlugin.authenticate", "authenticate successful");
                    SalesforceOAuthPlugin.client = c;

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
                    SalesforceOAuthPlugin.client.sendAsync(RestRequest.getRequestForResources(API_VERSION), new AsyncRequestCallback() {

                    	@Override
                        public void onSuccess(RestRequest request, RestResponse response) {
                    		setSidCookies(webView, SalesforceOAuthPlugin.client);
                            callbackContext.success(getJSONCredentials(SalesforceOAuthPlugin.client));
                        }

                    	@Override
                        public void onError(Exception exception) {
                    		callbackContext.error(exception.getMessage());
                        }
                    });
                }
            }
        });

        // Done
        PluginResult noop = new PluginResult(PluginResult.Status.NO_RESULT);
        noop.setKeepCallback(true);
        callbackContext.sendPluginResult(noop);
    }

    /**
     * Native implementation for "getAuthCredentials" action.
     * @param callbackContext Used when calling back into Javascript.
     * @throws JSONException
     */
    protected void getAuthCredentials(CallbackContext callbackContext) throws JSONException {
        Log.i("SalesforceOAuthPlugin.getAuthCredentials", "getAuthCredentials called");
        if (client == null) {
            Log.w("SalesforceOAuthPlugin.getAuthCredentials", "getAuthCredentials failed - never authenticated");
            callbackContext.error("Never authenticated");
        } else {
            Log.i("SalesforceOAuthPlugin.getAuthCredentials", "getAuthCredentials successful");
            callbackContext.success(getJSONCredentials(client));
        }
    }

    /**
     * @param callbackContext Used when calling back into Javascript.
     */
    protected void getAppHomeUrl(CallbackContext callbackContext)  {
        Log.i("SalesforceOAuthPlugin.getAppHomeUrl", "getAppHomeUrl called");
        SharedPreferences sp = cordova.getActivity().getSharedPreferences(SalesforceGapViewClient.SFDC_WEB_VIEW_CLIENT_SETTINGS, Context.MODE_PRIVATE);
        String url = sp.getString(SalesforceGapViewClient.APP_HOME_URL_PROP_KEY, null);
        callbackContext.success(url);
    }

    /**
     * Native implementation for "logout" action
     * @param callbackContext Used when calling back into Javascript.
     */
    protected void logoutCurrentUser(CallbackContext callbackContext) {
        Log.i("SalesforceOAuthPlugin.logoutCurrentUser", "logoutCurrentUser called");
        logout(cordova.getActivity());
        callbackContext.success();
    }

    /**
     * @param url
     * @return front-door url
     */
    private static String getFrontDoorUrl(String url) {
    	Log.i("SalesforceOAuthPlugin.loadPageThroughFrontDoor", "loading: " + url);
		String frontDoorUrl = client.getClientInfo().instanceUrl.toString() + "/secur/frontdoor.jsp?";
		List<NameValuePair> params = new LinkedList<NameValuePair>();
		params.add(new BasicNameValuePair("sid", client.getAuthToken()));
		params.add(new BasicNameValuePair("retURL", url));
		params.add(new BasicNameValuePair("display", "touch"));
		frontDoorUrl += URLEncodedUtils.format(params, "UTF-8");
		return frontDoorUrl;
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
