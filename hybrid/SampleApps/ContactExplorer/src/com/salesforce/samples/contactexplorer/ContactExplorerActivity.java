package com.salesforce.samples.contactexplorer;

import java.util.HashMap;

import org.json.JSONObject;

import android.os.Bundle;
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
        
        super.loadUrl("file:///android_asset/www/index.html");
        
		new ClientManager(this, ForceApp.APP.getAccountType(), null /* FIXME build hash from user pin */).getRestClient(this, new RestClientCallback() {
			public void authenticatedRestClient(RestClient client) {
				if (client == null) {
					ForceApp.APP.logout();
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
    
    @Override
    public void init() {
    	super.init();
		final String uaStr = ForceApp.APP.getUserAgent();
		if (null != this.appView) {
	        WebSettings webSettings = this.appView.getSettings();
	        webSettings.setUserAgentString(uaStr);
		}
    }
 
}