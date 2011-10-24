package com.salesforce.samples.contactexplorer;

import java.util.HashMap;

import org.json.JSONObject;

import android.content.Intent;
import android.os.Bundle;

import com.phonegap.DroidGap;
import com.salesforce.androidsdk.auth.AbstractLoginActivity;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.RestClientCallback;
import com.salesforce.androidsdk.rest.RestClient;

public class ContactExplorerActivity extends DroidGap {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ContactExplorerActivity.super.loadUrl("file:///android_asset/www/index.html");
        
        final String accountType = getString(R.string.account_type);
		new ClientManager(this, accountType).getRestClient(this, new RestClientCallback() {
			public void authenticatedRestClient(RestClient client) {
				if (client == null) {
					// Go back to login screen
			    	new ClientManager(ContactExplorerActivity.this, accountType).removeAccountAsync(null);
			    	Intent i = new Intent(AbstractLoginActivity.ACTION_LOGIN);
			    	i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			    	startActivity(i);
			    	return;
				}
				
				HashMap<String, String> data = new HashMap<String, String>();
				data.put("clientId", getString(R.string.oauth_client_id));
				data.put("loginUrl", getString(R.string.login_url));
				data.put("apiVersion", getString(R.string.api_version));
				data.put("authToken", client.getAuthToken());
				// TODO: also send down instanceUrl and refreshToken
				
				String eventJs = "{'data':" + new JSONObject(data).toString() + "}";
				String jsCall = "onSalesforceOAuthLogin(" + eventJs + ")";
				sendJavascript(jsCall);
			}
		});
    }
}