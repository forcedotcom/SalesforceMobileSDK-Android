package com.salesforce.samples.vfconnector;

import java.net.URI;


import android.os.Bundle;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebSettings;

import com.phonegap.DroidGap;
import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.RestClientCallback;
import com.salesforce.androidsdk.rest.RestClient;

public class VFConnectorActivity extends DroidGap {
    public static String TAG = "VFConnector";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    	//ensure we have a CookieSyncManager
    	CookieSyncManager.createInstance(this);
        
        //ensure that we allow urls from all salesforce domains to be loaded
        this.addWhiteListEntry("force.com", true);
        this.addWhiteListEntry("salesforce.com", true);
                
		new ClientManager(this)
			.getRestClient(this, new RestClientCallback() {
			public void authenticatedRestClient(RestClient client) {
				if (client == null) {
					ForceApp.APP.logout(VFConnectorActivity.this);
					return;
				}
				
				loggedIn(client);			
			}
		});
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
    	super.onResume();
    }
    
    @Override
    public void onPause() {
    	CookieSyncManager.getInstance().stopSync();
    	super.onPause();
    }
    
    protected void loggedIn(RestClient client) {    	
    	this.setSidCookies(client);
    	this.loadStartPage(client.getClientInfo().instanceUrl);    	
    }
    
    protected void loadStartPage(URI baseUrl) {
    	String host = baseUrl.getHost();
    	String finalURL = "https://" + host + "/apex/BasicVFPage";
    	super.loadUrl(finalURL);
    }
    
    
    protected void addSidCookieForDomain(CookieManager cookieMgr, String domain, String sid) {
        String cookieStr = "sid=" + sid + "; domain=" + domain;
    	cookieMgr.setCookie(domain, cookieStr);
    	Log.i(TAG,"addSidCookieForDomain: " + domain);
    }
    
    protected void setSidCookies(RestClient client) {
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