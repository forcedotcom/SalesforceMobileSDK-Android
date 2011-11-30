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
package com.salesforce.androidsdk.ui;

import org.json.JSONObject;

import android.os.Bundle;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebSettings;

import com.phonegap.DroidGap;
import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.phonegap.SalesforceOAuthPlugin;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.RestClientCallback;
import com.salesforce.androidsdk.rest.RestClient;

public class SalesforceDroidGapActivity extends DroidGap {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    	//ensure we have a CookieSyncManager
    	CookieSyncManager.createInstance(this);
        
        //ensure that we allow urls from all salesforce domains to be loaded
        this.addWhiteListEntry("force.com", true);
        this.addWhiteListEntry("salesforce.com", true);
        
        // Load bootstrap
        super.loadUrl("file:///android_asset/www/bootstrap.html");
    }
    
    @Override
    public void init() {
    	super.init();
		final String uaStr = ForceApp.APP.getUserAgent();
		if (null != this.appView) {
	        WebSettings webSettings = this.appView.getSettings();
	        webSettings.setUserAgentString(uaStr);
		}
    }
    
    @Override
    public void onResume() {
    	CookieSyncManager.getInstance().startSync();

    	// Auto refresh
		if (SalesforceOAuthPlugin.shouldAutoRefresh()) {
			Log.i("SalesforceDroidGapActivity.onResume", "Starting auto-refresh");
			ClientManager clientManager = SalesforceOAuthPlugin.clientManager;
			clientManager.invalidateToken(SalesforceOAuthPlugin.getLastAuthToken());
			clientManager.getRestClient(this, new RestClientCallback() {
				@Override
				public void authenticatedRestClient(RestClient client) {
					if (client == null) {
						Log.w("SalesforceDroidGapActivity.onResume", "Auto-refresh failed - logging out");
						ForceApp.APP.logout(SalesforceDroidGapActivity.this);
					}
					else {
						Log.i("SalesforceDroidGapActivity.onResume", "Auto-refresh succeeded");
						SalesforceOAuthPlugin.updateAuthToken(client.getAuthToken());
						
						setSidCookies(client);
						sendJavascript("PhoneGap.fireDocumentEvent('salesforceSessionRefresh'," + new JSONObject(SalesforceOAuthPlugin.lastCredentials).toString() + ");");
					}
				}
			});
    	}
    	super.onResume();
    }
    
    @Override
    public void onPause() {
    	CookieSyncManager.getInstance().stopSync();
    	super.onPause();
    }
    

	/**************************************************************************************************
	 * 
	 * Helper methods for managing cookies
	 * 
	 **************************************************************************************************/

    public void setSidCookies(RestClient client) {
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

    private void addSidCookieForDomain(CookieManager cookieMgr, String domain, String sid) {
        String cookieStr = "sid=" + sid + "; domain=" + domain;
    	cookieMgr.setCookie(domain, cookieStr);
    }
    
}
