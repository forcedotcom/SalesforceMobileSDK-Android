package com.salesforce.samples.contactexplorer;

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
        
		new ClientManager(this).getRestClient(this, new RestClientCallback() {
			public void authenticatedRestClient(RestClient client) {
				if (client == null) {
					ForceApp.APP.logout(ContactExplorerActivity.this);
					return;
				}

				StringBuilder jsCall = new StringBuilder()
					.append("(function() {")
					.append("  var e = document.createEvent('Events');")
					.append("  e.initEvent('salesforce_oauth_login');")
					.append("  e.data = {")
					.append("    'accessToken': '").append(client.getAuthToken()).append("',")
					.append("    'refreshToken': '").append(client.getRefreshToken()).append("',")
					.append("    'clientId': '").append(getString(R.string.oauth_client_id)).append("',")
					.append("    'loginUrl': '").append(getString(R.string.login_url)).append("',")
					.append("    'instanceUrl': '").append(client.getBaseUrl().toString()).append("',")
					.append("    'apiVersion': '").append(getString(R.string.api_version)).append("',")
					.append("    'userId': '").append(client.getUserId()).append("',")
					.append("    'username': '").append(client.getUsername()).append("',")
					.append("    'orgId': '").append(client.getOrgId()).append("',")
					.append("    'userAgent': '").append(uaStr).append("'")
					.append("  };")
					.append("  document.dispatchEvent(e);")
					.append("})();");
				
				sendJavascript(jsCall.toString());
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