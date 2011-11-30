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

import android.util.Log;

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
 * Phonegap plugin for Force
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
	
	private static final int MIN_REFRESH_INTERVAL = 120*1000; // 2 minutes
	
	/**
	 * Supported actions
	 */
	enum Action {
		authenticate,
		getAuthCredentials,
		logoutCurrentUser
	}
	
	/* static because it needs to survive plugin being torn down when a new URL is loaded */
	public static ClientManager clientManager;
	public static Map<String, String> lastCredentials;
	public static boolean autoRefresh;
	private static long lastRefreshTime = -1;
	
    /**
     * Executes the request and returns PluginResult.
     * 
     * @param actionStr     The action to execute.
     * @param args          JSONArray of arguments for the plugin.
     * @param callbackId    The callback id used when calling back into JavaScript.
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
	 * @param args
	 * @param callbackId
	 * @return NO_RESULT since authentication is asynchronous
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
			public void authenticatedRestClient(RestClient client) {
				if (client == null) {
					Log.w("SalesforceOAuthPlugin.authenticate", "authenticate failed - logging out");
					ForceApp.APP.logout(ctx);
				}
				else {
					Log.i("SalesforceOAuthPlugin.authenticate", "authenticate successful");
					updateRefreshTime();
					((SalesforceDroidGapActivity) ctx).setSidCookies(client);
					lastCredentials = getJSONCredentials(client);
					success(new PluginResult(PluginResult.Status.OK, new JSONObject(lastCredentials)), callbackId);
				}
			}
		});

		// Done
		PluginResult noop = new PluginResult(PluginResult.Status.NO_RESULT);
		noop.setKeepCallback(true);
		return noop;
	}
	

	/**
	 * Native implementation for "getAuthCredentials" action
	 * @param callbackId
	 * @return plugin result (ok if authenticated, error otherwise)
	 * @throws JSONException
	 */
	protected PluginResult getAuthCredentials(String callbackId) throws JSONException {
		Log.i("SalesforceOAuthPlugin.getAuthCredentials", "getAuthCredentials called");
		if (lastCredentials == null) {
			Log.w("SalesforceOAuthPlugin.getAuthCredentials", "getAuthCredentials failed - never authenticated");
			return new PluginResult(PluginResult.Status.ERROR, "Never authenticated");
		}
		else {
			Log.i("SalesforceOAuthPlugin.getAuthCredentials", "getAuthCredentials successful");
			return new PluginResult(PluginResult.Status.OK, new JSONObject(lastCredentials));
		}
	}
	
	/**
	 * Native implementation for "logout" action
	 * @return ok plugin result
	 */
	protected PluginResult logoutCurrentUser() {
		Log.i("SalesforceOAuthPlugin.logoutCurrentUser", "logoutCurrentUser called");
		ForceApp.APP.logout(this.ctx);
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
	public static boolean shouldAutoRefresh() {
		return autoRefresh // auto-refresh is turned on
			&& lastRefreshTime > 0 // we have authenticated 
			&&  (System.currentTimeMillis() - lastRefreshTime > MIN_REFRESH_INTERVAL); // at least 2 minutes went by
	}
	
	/**
	 * Update last refresh time to avoid un-necessary auto-refresh
	 */
	public static void updateRefreshTime() {
		Log.i("SalesforceOAuthPlugin.updateRefreshTime", "lastRefreshTime before: " + lastRefreshTime);
		lastRefreshTime = System.currentTimeMillis();
		Log.i("SalesforceOAuthPlugin.updateRefreshTime", "lastRefreshTime after: " + lastRefreshTime);
	}
	
	/**
	 * @return auth token (extracted from saved credentials)
	 */
	public static String getLastAuthToken() {
		return lastCredentials.get(ACCESS_TOKEN);
	}
	
	/**
	 * Update auth token in saved credentials (Also updates last refresh time)
	 * @param newAuthToken
	 */
	public static void updateAuthToken(String newAuthToken) {
		lastCredentials.put(ACCESS_TOKEN, newAuthToken);
		updateRefreshTime();
	}

	
	/**************************************************************************************************
	 * 
	 * Helper methods for building js credentials
	 * 
	 **************************************************************************************************/
	
	/**
	 * Get map for credentials
	 * @return
	 */
	public static Map<String, String> getJSONCredentials(RestClient client) {
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
		return data;
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
}
