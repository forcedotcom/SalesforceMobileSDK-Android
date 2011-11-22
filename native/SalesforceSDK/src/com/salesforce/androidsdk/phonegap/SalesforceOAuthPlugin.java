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
import org.json.JSONObject;

import com.phonegap.api.Plugin;
import com.phonegap.api.PluginResult;
import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.rest.ClientManager;
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
        	final String cId = callbackId;
			new ClientManager(ctx).getRestClient(ctx, new RestClientCallback() {
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
						
						SalesforceOAuthPlugin.this.success(new PluginResult(PluginResult.Status.OK, new JSONObject(data), "JSON.parse"), cId);
					}
				}
			});
        	
            return new PluginResult(PluginResult.Status.OK);
        }
        
        return new PluginResult(status, result);
    }
}
