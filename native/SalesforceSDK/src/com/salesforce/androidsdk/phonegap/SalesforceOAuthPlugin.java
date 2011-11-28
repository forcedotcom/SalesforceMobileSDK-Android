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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

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
	
	private RestClient client;
    
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
    	}
    	catch (IllegalArgumentException e) {
    		return new PluginResult(PluginResult.Status.INVALID_ACTION);
    	}
    	
    	// Run action
		switch(action) {
			case authenticate:       	authenticate(args, callbackId); break; 
			case getAuthCredentials: 	getAuthCredentials(callbackId); break; 
			case logoutCurrentUser:		logoutCurrentUser(); break;
    	}

		// Done
		return new PluginResult(PluginResult.Status.OK);
    }

	/**
	 * Native implementation for "authenticate" action
	 * @param args
	 * @param callbackId
	 */
	protected void authenticate(JSONArray args, String callbackId) {
		// Get login options
		LoginOptions loginOptions = null;
		try {
			loginOptions = parseLoginOptions(args);
		}
		catch (JSONException e) {
			error(new PluginResult(PluginResult.Status.JSON_EXCEPTION), e.getMessage());	
		}

		// Block until login completes
		final BlockingQueue<RestClient> q = new ArrayBlockingQueue<RestClient>(1);
		new ClientManager(ctx, ForceApp.APP.getAccountType(), loginOptions).getRestClient(ctx, new RestClientCallback() {
			@Override
			public void authenticatedRestClient(RestClient client) {
				q.offer(client);
			}
		});
		
		try {
			client = q.take();
		}
		catch (InterruptedException e) {
			error(new PluginResult(PluginResult.Status.ERROR), e.getMessage());
		}
		
		// Update cookies
		setSidCookies(client);
		
		// Return credentials
		getAuthCredentials(callbackId);
	}

	/**
	 * Native implementation for "getAuthCredentials" action
	 * @param callbackId
	 */
	protected void getAuthCredentials(String callbackId) {
		if (client == null) {
			error(new PluginResult(PluginResult.Status.ERROR), "Never authenticated");
		}
		else {
			success(buildCredentialsResult(client), callbackId);				
		}
	}
	
	/**
	 * Native implementation for "logout" action
	 */
	protected void logoutCurrentUser() {
		Log.i(TAG, "logoutCurrentUser " + this.ctx);
		ForceApp.APP.logout(this.ctx);
	}

	/**************************************************************************************************
	 * 
	 * Helper methods for parsing oauth properties
	 * 
	 **************************************************************************************************/

	private LoginOptions parseLoginOptions(JSONArray args) throws JSONException {
		LoginOptions loginOptions;
		JSONObject oauthProperties = new JSONObject((String) args.get(0));

		JSONArray scopesJson = oauthProperties.getJSONArray("oauthScopes");
		String[] scopes = jsonArrayToArray(scopesJson);
		
		loginOptions = new LoginOptions(
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

    private PluginResult buildCredentialsResult(RestClient client) {
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
		JSONObject credentials = new JSONObject(data);
		
		return new PluginResult(PluginResult.Status.OK, credentials);
    }
}
