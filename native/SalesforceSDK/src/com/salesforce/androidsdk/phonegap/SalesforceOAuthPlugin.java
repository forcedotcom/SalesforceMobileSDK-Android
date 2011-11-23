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

import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
    
    /**
     * Executes the request and returns PluginResult.
     * 
     * @param action        The action to execute.
     * @param args          JSONArry of arguments for the plugin.
     * @param callbackId    The callback id used when calling back into JavaScript.
     * @return              A PluginResult object with a status and message.
     */
    public PluginResult execute(String action, JSONArray args, String callbackId) {
        PluginResult.Status status = PluginResult.Status.INVALID_ACTION;
        String result = "Unsupported Operation: " + action; 
                
        if (action.equals("authenticate")) {
        	
        	LoginOptions loginOptions = null;
        	try {
	        	JSONArray scopesJson = args.getJSONObject(0).getJSONArray("oauthScopes");
	        	String[] scopes = scopesJson == null ? null : scopesJson.join(",").split(",");
	        	
	        	loginOptions = new LoginOptions(
	        			"https://test.salesforce.com", /* FIXME should be args.getJSONObject(0).getString("loginUrl")*/ 
	        			ForceApp.APP.getPasscodeHash(),
	        			args.getJSONObject(0).getString("oauthRedirectURI"),
	        			args.getJSONObject(0).getString("remoteAccessConsumerKey"),
	        			scopes);
        	}
        	catch (JSONException e) {
        		return new PluginResult(PluginResult.Status.JSON_EXCEPTION, e.getMessage());	
        	}
        			
        	//
        	// TODO don't ignore userAccountIdentifier, autoRefreshOnForeground
        	//
        	
        	final String cId = callbackId;
			new ClientManager(ctx, ForceApp.APP.getAccountType(), loginOptions).getRestClient(ctx, new RestClientCallback() {
				@Override
				public void authenticatedRestClient(RestClient client) {
					if (client == null) {
						SalesforceOAuthPlugin.this.error("Authentication failed", cId);						
					}
					else {
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
			
						setSidCookies(client);
						SalesforceOAuthPlugin.this.success(new PluginResult(PluginResult.Status.OK, new JSONObject(data)), cId);
					}
				}
			});
        	
            return new PluginResult(PluginResult.Status.OK);
        }
        
        return new PluginResult(status, result);
    }
    
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
    
}
