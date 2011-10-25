package com.salesforce.samples.vfconnector;

import java.util.HashMap;

import org.apache.http.cookie.Cookie;
import org.json.JSONObject;

import android.os.Bundle;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;

import com.phonegap.DroidGap;
import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.RestClientCallback;
import com.salesforce.androidsdk.rest.RestClient;

public class VFConnectorActivity extends DroidGap {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //ensure that we allow urls from all salesforce domains to be loaded
        this.addWhiteListEntry("force.com", true);
        this.addWhiteListEntry("salesforce.com", true);
        
        //super.loadUrl("file:///android_asset/www/unauthorized.html");
        
        final String accountType = getString(R.string.account_type);
		new ClientManager(this, accountType).getRestClient(this, new RestClientCallback() {
			public void authenticatedRestClient(RestClient client) {
				if (client == null) {
					ForceApp.APP.logout(accountType);
					return;
				}
				
				loggedIn(client);			
			}
		});
    }
    
    protected void loggedIn(RestClient client) {
    	//TODO setup cookies
    	
    	//TODO load real content
        //super.loadUrl("file:///android_asset/www/index.html");

    	this.setSidCookies(client);
    

    	String host = client.getBaseUrl().getHost();
    	String finalURL = "https://" + host + "/apex/BasicVFPage";
    	super.loadUrl(finalURL);
    	
    }
    
    
    protected void addSidCookieForDomain(CookieManager cookieMgr, String domain, String sid) {
        String cookieStr = "sid=" + sid + "; domain=" + domain;
    	cookieMgr.setCookie(domain, cookieStr);
    	Log.i("Cookiez","addSidCookie: " + cookieStr);
    }
    
    protected void setSidCookies(RestClient client) {
    	//ensure we have a CookieSyncManager
    	CookieSyncManager cookieSyncMgr = CookieSyncManager.createInstance(this);

    	CookieManager cookieMgr = CookieManager.getInstance();
    	//cookieMgr.removeSessionCookie(); //tODO check
    	String accessToken = client.getAuthToken();
    	String domain = client.getBaseUrl().getHost();

    	addSidCookieForDomain(cookieMgr,".force.com",accessToken);
    	addSidCookieForDomain(cookieMgr,".salesforce.com",accessToken);
    	addSidCookieForDomain(cookieMgr,domain,accessToken);

	    cookieSyncMgr.sync();
	    
    	


    }
    
//    protected void sendJavascriptLoginEvent(RestClient client) {
//		HashMap<String, String> data = new HashMap<String, String>();
//		data.put("clientId", getString(R.string.oauth_client_id));
//		data.put("loginUrl", getString(R.string.login_url));
//		data.put("apiVersion", getString(R.string.api_version));
//		data.put("accessToken", client.getAuthToken());
//		data.put("instanceUrl", client.getBaseUrl().toString());
//		data.put("refreshToken", client.getRefreshToken());
//		
//		String eventJs = "{'data':" + new JSONObject(data).toString() + "}";
//		String jsCall = "onSalesforceOAuthLogin(" + eventJs + ")";
//		sendJavascript(jsCall);
//    }
}