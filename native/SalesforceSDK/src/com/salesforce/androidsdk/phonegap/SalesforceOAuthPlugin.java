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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebView;

import com.phonegap.api.Plugin;
import com.phonegap.api.PluginResult;
import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;
import com.salesforce.androidsdk.rest.ClientManager.RestClientCallback;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestClient.ClientInfo;
import com.salesforce.androidsdk.ui.SalesforceDroidGapActivity;

/**
 * PhoneGap plugin for PhoneGap-based Force applications.
 */
public class SalesforceOAuthPlugin extends Plugin {
	// Keys in credentials map
	private static final String USER_AGENT = "userAgent";
	private static final String INSTANCE_URL = "instanceUrl";
	private static final String LOGIN_URL = "loginUrl";
	private static final String CLIENT_ID = "clientId";
	private static final String ORG_ID = "orgId";
	private static final String USER_ID = "userId";
	private static final String REFRESH_TOKEN = "refreshToken";
	private static final String ACCESS_TOKEN = "accessToken";

	// Min refresh interval (when auto refresh is on)
	private static final int MIN_REFRESH_INTERVAL = 120*1000; // 2 minutes
	
	/**
	 * Supported plugin actions that the client can take.
	 */
	enum Action {
		authenticate,
		getAuthCredentials,
		logoutCurrentUser
	}
	
	/* static because it needs to survive plugin being torn down when a new URL is loaded */
	private static ClientManager clientManager;
	private static RestClient client;
	private static boolean autoRefresh;
	private static long lastRefreshTime = -1;
	
	/**
	 * If auto-refresh is enabled, this method will be called when the app resumes, and
	 * automatically refresh the user's OAuth credentials in the app, ensuring a valid
	 * session.
	 * @param webView The WebView running the application.
	 * @param ctx The main activity/context for the app.
	 */
	public static void autoRefreshIfNeeded(final WebView webView, final SalesforceDroidGapActivity ctx) {
		Log.i("SalesforceOAuthPlugin.autoRefreshIfNeeded", "Checking if auto refresh needed");
		if (shouldAutoRefresh()) {
			Log.i("SalesforceOAuthPlugin.autoRefreshIfNeeded", "Starting auto-refresh");
			clientManager.invalidateToken(client.getAuthToken());
			clientManager.getRestClient(ctx, new RestClientCallback() {
				@Override
				public void authenticatedRestClient(RestClient c) {
					if (c == null) {
						Log.w("SalesforceOAuthPlugin.autoRefreshIfNeeded", "Auto-refresh failed - logging out");
						logout(ctx);
					}
					else {
						Log.i("SalesforceOAuthPlugin.autoRefreshIfNeeded", "Auto-refresh succeeded");
						updateRefreshTime();
						SalesforceOAuthPlugin.client = c;
						setSidCookies(webView, SalesforceOAuthPlugin.client);
						Log.i("SalesforceOAuthPlugin.autoRefreshIfNeeded", "Firing salesforceSessionRefresh event");
						ctx.sendJavascript("PhoneGap.fireDocumentEvent('salesforceSessionRefresh'," + getJSONCredentials(SalesforceOAuthPlugin.client).toString() + ");");
					}
				}
			});
    	}
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
		clientManager = new ClientManager(ctx, ForceApp.APP.getAccountType(), loginOptions);
		autoRefresh = oauthProperties.getBoolean("autoRefreshOnForeground");
		clientManager.getRestClient(ctx, new RestClientCallback() {
			@Override
			public void authenticatedRestClient(RestClient c) {
				if (c == null) {
					Log.w("SalesforceOAuthPlugin.authenticate", "authenticate failed - logging out");
					logout(ctx);
				}
				else {
					Log.i("SalesforceOAuthPlugin.authenticate", "authenticate successful");
					// Only updating time if we went through login
					if (SalesforceOAuthPlugin.client == null) {
						updateRefreshTime();
					}
					SalesforceOAuthPlugin.client = c;						
					callAuthenticateSuccess(callbackId);
				}
			}
		});

		// Done
		PluginResult noop = new PluginResult(PluginResult.Status.NO_RESULT);
		noop.setKeepCallback(true);
		return noop;
	}

	private void callAuthenticateSuccess(final String callbackId) {
		Log.i("SalesforceOAuthPlugin.callAuthenticateSuccess", "Calling authenticate success callback");
		setSidCookies(webView, SalesforceOAuthPlugin.client);
		success(new PluginResult(PluginResult.Status.OK, getJSONCredentials(SalesforceOAuthPlugin.client)), callbackId);
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
	
	/**
	 * Native implementation for "logout" action
	 * @return This should always return the ok plugin result.
	 */
	protected PluginResult logoutCurrentUser() {
		Log.i("SalesforceOAuthPlugin.logoutCurrentUser", "logoutCurrentUser called");
		logout(ctx);
		return new PluginResult(PluginResult.Status.OK);
	}

	/**************************************************************************************************
	 * 
	 * Helper methods for auto-refresh
	 * 
	 **************************************************************************************************/
	
	/**
	 * Return true if one should auto-refresh the oauth token now
	 */
	private static boolean shouldAutoRefresh() {
		return autoRefresh // auto-refresh is turned on
			&& lastRefreshTime > 0 // we have authenticated 
			&&  (System.currentTimeMillis() - lastRefreshTime > MIN_REFRESH_INTERVAL); // at least 2 minutes went by
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
		JSONArray scopesJson = oauthProperties.getJSONArray("oauthScopes");
		String[] scopes = jsonArrayToArray(scopesJson);
		
		LoginOptions loginOptions = new LoginOptions(
				null, // set by app 
				ForceApp.APP.getPasscodeHash(),
				oauthProperties.getString("oauthRedirectURI"),
				oauthProperties.getString("remoteAccessConsumerKey"),
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
		SalesforceOAuthPlugin.autoRefresh = false;
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
    	cookieMgr.removeSessionCookie();
    	
    	SystemClock.sleep(250); // removeSessionCookies kicks out a thread - let it finish

    	String accessToken = client.getAuthToken();
    	String domain = client.getClientInfo().instanceUrl.getHost();

    	//set the cookie on all possible domains we could access
    	addSidCookieForDomain(cookieMgr,domain,accessToken);
    	addSidCookieForDomain(cookieMgr,".force.com",accessToken);
    	addSidCookieForDomain(cookieMgr,".salesforce.com",accessToken);

	    cookieSyncMgr.sync();
    }

    private static void addSidCookieForDomain(CookieManager cookieMgr, String domain, String sid) {
        String cookieStr = "sid=" + sid + "; domain=" + domain;
    	cookieMgr.setCookie(domain, cookieStr);
    }

}
