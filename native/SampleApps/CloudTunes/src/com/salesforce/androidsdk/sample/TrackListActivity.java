/*
 * Copyright (c) 2013, salesforce.com, inc.
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
package com.salesforce.androidsdk.sample;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Activity;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;
import com.salesforce.androidsdk.rest.ClientManager.RestClientCallback;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestClient.AsyncRequestCallback;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.TokenRevocationReceiver;

public class TrackListActivity  extends Activity {

    private String[] tracks;
    private ListView lv;
    private RestClient client;
    private String apiVersion;
    private TokenRevocationReceiver tokenRevocationReceiver;

	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.track_list);
        apiVersion = getString(R.string.api_version);
        lv = (ListView)findViewById(R.id.trackList);
		lv.setEnabled(false);
    }
    
	public void doneBtnInvoked(View v) {
		setResult(RESULT_OK);
        finish();		
	}

	@Override
	public void onResume() {
		super.onResume();
		
		// Login options
		String accountType = getString(R.string.account_type);
		LoginOptions loginOptions = new LoginOptions(
				null, // gets overridden by LoginActivity based on server picked by uuser 
				ForceApp.APP.getPasscodeHash(),
				getString(R.string.oauth_callback_url),
				getString(R.string.oauth_client_id),
				new String[] {"api"});
		new ClientManager(this, accountType, loginOptions, ForceApp.APP.shouldLogoutWhenTokenRevoked()).getRestClient(this, new RestClientCallback() {

			@Override
			public void authenticatedRestClient(RestClient client) {
				if (client == null) {
					ForceApp.APP.logout(TrackListActivity.this);
					return;
				}
				TrackListActivity.this.client = client;
				getTracks();
			}
		});
		tokenRevocationReceiver = new TokenRevocationReceiver(this);
		registerReceiver(tokenRevocationReceiver, new IntentFilter(ClientManager.ACCESS_TOKEN_REVOKE_INTENT));
	}

	@Override
	public void onPause() {
		super.onPause();
		unregisterReceiver(tokenRevocationReceiver);
		tokenRevocationReceiver = null;
	}

	private void getTracks() {
		try {
			String albumId = getIntent().getStringExtra("ALBUM_ID");
			String soql = "select Id, Name, Price__c from Track__c where Album__c = '"+albumId+"'";
			RestRequest request = RestRequest.getRequestForQuery(apiVersion, soql);
			client.sendAsync(request, new AsyncRequestCallback() {

				@Override
				public void onSuccess(RestRequest request, RestResponse response) {
					try {
						if (response == null || response.asJSONObject() == null) {
							return;
						}
						JSONArray records = response.asJSONObject().getJSONArray("records");
						if (records.length() == 0) {
							return;
						}	
						tracks = new String[records.length()];
						for (int i = 0; i < records.length(); i++) {
							JSONObject album = (JSONObject)records.get(i);
							tracks[i] = (i+1) + ".  "+ album.getString("Name");
			
						}
				        ArrayAdapter<String> ad = new ArrayAdapter<String>(TrackListActivity.this, 
				        		R.layout.list_item, 
				        		tracks);
				        lv.setAdapter(ad);
				        EventsObservable.get().notifyEvent(EventType.RenditionComplete);
					} catch (Exception e) {
						e.printStackTrace();
						displayError(e.getMessage());
					}
				}
				
				@Override
				public void onError(Exception exception) {
					displayError(exception.getMessage());
					EventsObservable.get().notifyEvent(EventType.RenditionComplete);
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
			displayError(e.getMessage());
		}		
	}

	private void displayError(String error)	{
        ArrayAdapter<String> ad = new ArrayAdapter<String>(TrackListActivity.this, 
        		R.layout.list_item, 
        		new String[]{"Error retrieving Track data - "+error});
        lv.setAdapter(ad);	
	}
}
