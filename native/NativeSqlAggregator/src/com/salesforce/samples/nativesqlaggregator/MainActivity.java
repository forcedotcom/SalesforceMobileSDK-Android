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
package com.salesforce.samples.nativesqlaggregator;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import org.json.JSONArray;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestClient.AsyncRequestCallback;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartstore.app.SalesforceSDKManagerWithSmartStore;
import com.salesforce.androidsdk.ui.sfnative.SalesforceActivity;

/**
 * Main activity.
 *
 * @author bhariharan
 */
public class MainActivity extends SalesforceActivity {

    private RestClient client;
    private ArrayAdapter<String> listAdapter;
    private SmartStoreInterface smartStoreIntf;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		smartStoreIntf = new SmartStoreInterface();

		// Creates soups, if they don't exist already.
		smartStoreIntf.createAccountsSoup();
		smartStoreIntf.createOpportunitiesSoup();
	}

	@Override
	public void onResume() {

		// Hide the view until we are logged in.
		findViewById(R.id.root).setVisibility(View.INVISIBLE);

		// Create list adapter.
		listAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
		((ListView) findViewById(R.id.contacts_list)).setAdapter(listAdapter);			
		super.onResume();
	}		

	@Override
	public void onResume(RestClient client) {
        this.client = client;

		// Show the view.
		findViewById(R.id.root).setVisibility(View.VISIBLE);
	}

	/**
	 * Called when "Logout" button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onLogoutClick(View v) {
		SalesforceSDKManager.getInstance().logout(this);
	}

	/**
	 * Called when "Clear" button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onClearClick(View v) {
		listAdapter.clear();
		smartStoreIntf.deleteAccountsSoup();
		smartStoreIntf.deleteOpportunitiesSoup();
		smartStoreIntf.createAccountsSoup();
		smartStoreIntf.createOpportunitiesSoup();
		Toast.makeText(this, "SmartStore Reset Successful!", Toast.LENGTH_LONG).show();
	}

	/**
	 * Called when the "Fetch Opportunities" button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onFetchOpportunitiesClick(View v) throws UnsupportedEncodingException {
        sendRequest("SELECT Name, Id, AccountId, OwnerId, Amount FROM Opportunity", "Opportunity");
	}

	/**
	 * Called when the "Fetch Accounts" button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onFetchAccountsClick(View v) throws UnsupportedEncodingException {
		sendRequest("SELECT Name, Id, OwnerId, AnnualRevenue FROM Account", "Account");
	}

	/**
	 * Called when the "Show Saved Opportunities" button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onDisplayOpportunitiesClick(View v) {
		final JSONArray opportunities = smartStoreIntf.getOpportunities();
	}

	/**
	 * Called when the "Show Saved Accounts" button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onDisplayAccountsClick(View v) {
		final JSONArray accounts = smartStoreIntf.getAccounts();
	}

	/**
	 * Sends a REST request.
	 *
	 * @param soql SOQL query.
	 * @param obj Object being queried.
	 */
	private void sendRequest(String soql, final String obj) throws UnsupportedEncodingException {
		final RestRequest restRequest = RestRequest.getRequestForQuery(getString(R.string.api_version), soql);
		client.sendAsync(restRequest, new AsyncRequestCallback() {

			@Override
			public void onSuccess(RestRequest request, RestResponse result) {
				try {
					listAdapter.clear();
					final JSONArray records = result.asJSONObject().getJSONArray("records");
					if (obj.equals("Account")) {
						smartStoreIntf.insertAccounts(records);
						Toast.makeText(MainActivity.this,
								"Successfully Saved Accounts in SmartStore!",
								Toast.LENGTH_LONG).show();
					} else if (obj.equals("Opportunity")) {
						smartStoreIntf.insertOpportunities(records);
						Toast.makeText(MainActivity.this,
								"Successfully Saved Opportunities in SmartStore!",
								Toast.LENGTH_LONG).show();
					} else {

						/*
						 * If the object is not an account or opportunity,
						 * we simply display the names of the records.
						 */
						for (int i = 0; i < records.length(); i++) {
							listAdapter.add(records.getJSONObject(i).getString("Name"));
						}
					}				
				} catch (Exception e) {
					onError(e);
				}
			}

			@Override
			public void onError(Exception exception) {
                Toast.makeText(MainActivity.this, 
                		MainActivity.this.getString(SalesforceSDKManagerWithSmartStore.getInstance()
                		.getSalesforceR().stringGenericError(), exception.toString()),
                        Toast.LENGTH_LONG).show();
			}
		});
	}
}
