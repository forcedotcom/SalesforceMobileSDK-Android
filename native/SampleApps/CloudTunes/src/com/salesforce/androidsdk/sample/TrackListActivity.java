package com.salesforce.androidsdk.sample;

import org.json.JSONArray;
import org.json.JSONObject;

import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.rest.ClientManager.RestClientCallback;
import com.salesforce.androidsdk.rest.RestClient.AsyncRequestCallback;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class TrackListActivity  extends Activity{
	
    private String[] tracks;
    private ListView lv;
    private RestClient client;
    private String apiVersion;
    
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.track_list);
        apiVersion = getString(R.string.api_version);
        
        lv = (ListView)findViewById(R.id.trackList);
        
		lv.setEnabled(false);
    }
    
	public void doneBtnInvoked(View v)
	{
		setResult(RESULT_OK);
        finish();		
	}
	
	@Override
	public void onResume(){
		super.onResume();
		
		// Login options
		String accountType = ForceApp.APP.getAccountType();
		LoginOptions loginOptions = ForceApp.APP.getLoginOptions();
		
		new ClientManager(this, accountType, loginOptions).getRestClient(this, new RestClientCallback() {
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
			
	}
	
	private void getTracks(){
		try {
			String albumId = getIntent().getStringExtra("ALBUM_ID");
			String soql = "select Id, Name, Price__c from Track__c where Album__c = '"+albumId+"'";
			RestRequest request = RestRequest.getRequestForQuery(apiVersion, soql);

			client.sendAsync(request, new AsyncRequestCallback() {

				@Override
				public void onSuccess(RestRequest request, RestResponse response) {
					try {
						if (response == null || response.asJSONObject() == null)
							return;
						
						JSONArray records = response.asJSONObject().getJSONArray("records");
	
						if (records.length() == 0)
							return;
										
						tracks = new String[records.length()];
						
						for (int i = 0; i < records.length(); i++){
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
        ArrayAdapter<String> ad = new ArrayAdapter<String>(	TrackListActivity.this, 
        													R.layout.list_item, 
        													new String[]{"Error retrieving Track data - "+error});
        lv.setAdapter(ad);
		
	}

}
