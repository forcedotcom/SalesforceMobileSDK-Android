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
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;

import com.phonegap.api.Plugin;
import com.phonegap.api.PluginResult;
import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;
import com.salesforce.androidsdk.rest.ClientManager.RestClientCallback;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestClient.ClientInfo;

/**
 * Phonegap plugin for Force
 */
public class SalesforceOAuthPlugin extends Plugin {

	public static final String TAG = "SalesforceOAuthPlugin";
	
	/**
	 * Supported actions
	 */
	enum Action {
		authenticate,
		getAuthCredentials,
		logoutCurrentUser
	}
	
	/* static because it needs to survice plugin being torn down when a new URL is loaded */
	public static RestClient client;
	public static boolean autoRefresh;
	
	
    /**
     * Executes the request and returns PluginResult.
     * 
     * @param actionStr     The action to execute.
     * @param args          JSONArray of arguments for the plugin.
     * @param callbackId    The callback id used when calling back into JavaScript.
     * @return              A PluginResult object with a status and message.
     */
    public PluginResult execute(String actionStr, JSONArray args, String callbackId) {
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
		// Get login options
		JSONObject oauthProperties = new JSONObject((String) args.get(0));
		LoginOptions loginOptions = parseLoginOptions(oauthProperties);
		autoRefresh = oauthProperties.getBoolean("autoRefreshOnForeground");

		// Authenticate
		new ClientManager(ctx, ForceApp.APP.getAccountType(), loginOptions).getRestClient(ctx, new RestClientCallback() {
			@Override
			public void authenticatedRestClient(RestClient c) {
				if (c == null) {
					ForceApp.APP.logout(ctx);
				}
				else {
					SalesforceOAuthPlugin.client = c;
					setSidCookies(client);
					success(new PluginResult(PluginResult.Status.OK, getJSONCredentials(client)), callbackId);
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
	 */
	protected PluginResult getAuthCredentials(String callbackId) {
		if (client == null) {
			return new PluginResult(PluginResult.Status.ERROR, "Never authenticated");
		}
		else {
			return new PluginResult(PluginResult.Status.OK, getJSONCredentials(client));				
		}
	}
	
	/**
	 * Native implementation for "logout" action
	 * @return ok plugin result
	 */
	protected PluginResult logoutCurrentUser() {
		Log.i(TAG, "logoutCurrentUser " + this.ctx);
		ForceApp.APP.logout(this.ctx);
		return new PluginResult(PluginResult.Status.OK);
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

	/**************************************************************************************************
	 * 
	 * Helper methods for managing cookies
	 * 
	 **************************************************************************************************/

	private void addSidCookieForDomain(CookieManager cookieMgr, String domain, String sid) {
        String cookieStr = "sid=" + sid + "; domain=" + domain;
    	cookieMgr.setCookie(domain, cookieStr);
    }
    
    private void setSidCookies(RestClient client) {
    	CookieSyncManager cookieSyncMgr = CookieSyncManager.getInstance();
    	
    	CookieManager cookieMgr = CookieManager.getInstance();
    	cookieMgr.removeSessionCookie();

    	String accessToken = client.getAuthToken();
    	String domain = client.getClientInfo().instanceUrl.getHost();

    	//set the cookie on all possible domains we could access
    	addSidCookieForDomain(cookieMgr,domain,accessToken);
    	addSidCookieForDomain(cookieMgr,".force.com",accessToken);
    	addSidCookieForDomain(cookieMgr,".salesforce.com",accessToken);

	    cookieSyncMgr.sync();
    }

	/**************************************************************************************************
	 * 
	 * Helper method for building js credentials
	 * 
	 **************************************************************************************************/

    private JSONObject getJSONCredentials(RestClient client) {
    	assert client != null : "Client is null";
    	
		ClientInfo clientInfo = client.getClientInfo();
		Map<String, String> data = new HashMap<String, String>();
		data.put("accessToken", client.getAuthToken());
		data.put("refreshToken", client.getRefreshToken());
		data.put("userId", clientInfo.userId);
		data.put("orgId", clientInfo.orgId);
		data.put("clientId", clientInfo.clientId);
		data.put("loginUrl", clientInfo.loginUrl.toString()); 
		data.put("instanceUrl", clientInfo.instanceUrl.toString());
		data.put("userAgent", ForceApp.APP.getUserAgent());
		return new JSONObject(data);
    }
}
