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
        
        final String uaStr = ForceApp.APP.getUserAgent();
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
    
 
}