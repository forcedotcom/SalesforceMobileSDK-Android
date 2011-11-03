package com.salesforce.samples.contactexplorer;

import java.util.HashMap;

import org.json.JSONObject;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebSettings;

import com.phonegap.DroidGap;

import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.RestClientCallback;
import com.salesforce.androidsdk.rest.RestClient;

public class ContactExplorerActivity extends DroidGap {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        final String uaStr = getUserAgent();
        WebSettings webSettings = this.appView.getSettings();
        webSettings.setUserAgentString(uaStr);
        
        super.loadUrl("file:///android_asset/www/index.html");
        
        final String accountType = getString(R.string.account_type);
		new ClientManager(this, accountType).getRestClient(this, new RestClientCallback() {
			public void authenticatedRestClient(RestClient client) {
				if (client == null) {
					ForceApp.APP.logout(accountType);
					return;
				}
				
				HashMap<String, String> data = new HashMap<String, String>();
				data.put("clientId", getString(R.string.oauth_client_id));
				data.put("loginUrl", getString(R.string.login_url));
				data.put("apiVersion", getString(R.string.api_version));
				data.put("accessToken", client.getAuthToken());
				data.put("instanceUrl", client.getBaseUrl().toString());
				data.put("refreshToken", client.getRefreshToken());
				data.put("userId", client.getUserId());
				data.put("username", client.getUsername());
				data.put("orgId", client.getOrgId());
				data.put("userAgent", uaStr);
				
				String eventJs = "{'data':" + new JSONObject(data).toString() + "}";
				String jsCall = "onSalesforceOAuthLogin(" + eventJs + ")";
				sendJavascript(jsCall);
				
				
			}
		});
    }
    
    
	/**
	 * @return user agent string to use for all requests
	 */
	private String getUserAgent() {
		
		String sdkVersion = "0.9";
				
        //set a user agent string based on the mobile sdk version
        //We are building a user agent of the form:
		//SalesforceMobileSDK-hREST/1.0 android/3.2.0 

	    try {
	    	//attempt to pull version string from package info
	    	PackageManager pkgMgr = this.getPackageManager();
	    	PackageInfo pkgInfo = pkgMgr.getPackageInfo("com.salesforce.androidsdk", PackageManager.GET_META_DATA);
	        sdkVersion = pkgInfo.versionName;
	    } catch (Exception ex) {
	        Log.e(this.getClass().getSimpleName(), "Could not get version: ", ex);
	    }

	    String constructedUserAgent =  "SalesforceMobileSDK-hREST/" + sdkVersion + " android/"+ Build.VERSION.RELEASE  ;
	    return constructedUserAgent;
	}
}