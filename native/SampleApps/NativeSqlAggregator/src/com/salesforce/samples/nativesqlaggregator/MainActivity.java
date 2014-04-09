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

import org.json.JSONArray;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestClient.AsyncRequestCallback;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartstore.ui.SmartStoreInspectorActivity;
import com.salesforce.androidsdk.smartstore.app.SalesforceSDKManagerWithSmartStore;
import com.salesforce.androidsdk.ui.sfnative.SalesforceActivity;

/**
 * Main activity.
 *
 * @author bhariharan
 */
public class MainActivity extends SalesforceActivity {

    private RestClient client;
    private SmartStoreInterface smartStoreIntf;
    private ProgressDialog progressDialog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		// Configures options for the progress indicator.
		progressDialog = new ProgressDialog(this);
		progressDialog.setTitle("Fetching records...");
        progressDialog.setMessage("Please wait...");
        progressDialog.setCancelable(true);
        progressDialog.setIndeterminate(true);
	}

	@Override
	public void onResume() {

		// Hide the view until we are logged in.
		findViewById(R.id.root).setVisibility(View.INVISIBLE);
		super.onResume();
	}

	@Override
	protected void refreshIfUserSwitched() {
		smartStoreIntf = new SmartStoreInterface();

		// Creates soups, if they don't exist already.
		smartStoreIntf.createAccountsSoup();
		smartStoreIntf.createOpportunitiesSoup();
	}

	@Override
	public void onResume(RestClient client) {
        this.client = client;
		smartStoreIntf = new SmartStoreInterface();

		// Creates soups, if they don't exist already.
		smartStoreIntf.createAccountsSoup();
		smartStoreIntf.createOpportunitiesSoup();

		// Show the view.
		findViewById(R.id.root).setVisibility(View.VISIBLE);
	}

	/**
	 * Launches the activity to show results.
	 *
	 * @param results Results to be shown.
	 */
	private void launchResultActivity(String results) {
		final Intent intent = new Intent(ResultActivity.RESULT_INTENT_ACTION);
		intent.addCategory(Intent.CATEGORY_DEFAULT);
		intent.putExtra(ResultActivity.DATA_EXTRA, results);
		this.startActivity(intent);
	}

	/**
	 * Called when "Logout" button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onLogoutClick(View v) {
		 SalesforceSDKManagerWithSmartStore.getInstance().logout(this);
	}

	/**
	 * Called when "Switch" button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onSwitchClick(View v) {
		final Intent i = new Intent(SalesforceSDKManager.getInstance().getAppContext(),
				SalesforceSDKManager.getInstance().getAccountSwitcherActivityClass());
		i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		SalesforceSDKManager.getInstance().getAppContext().startActivity(i);
	}
	
	/**
	 * Called when "Inspect" button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onInspectClick(View v) {
		final Intent i = new Intent(this, SmartStoreInspectorActivity.class);
		this.startActivity(i);
	}

	/**
	 * Called when "Clear" button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onClearClick(View v) {
		smartStoreIntf.deleteAccountsSoup();
		smartStoreIntf.deleteOpportunitiesSoup();
		smartStoreIntf.createAccountsSoup();
		smartStoreIntf.createOpportunitiesSoup();
		Toast.makeText(this, "Offline data has been cleared.", Toast.LENGTH_SHORT).show();
	}

	/**
	 * Called when the "Save Records Offline" button is clicked.
	 *
	 * @param v View that was clicked.
	 * @throws UnsupportedEncodingException
	 */
	public void onSaveOfflineClick(View v) throws UnsupportedEncodingException {
		progressDialog.show();
		sendRequest("SELECT Name, Id, OwnerId FROM Account", "Account");
		sendRequest("SELECT Name, Id, AccountId, OwnerId, Amount FROM Opportunity", "Opportunity");
	}

	/**
	 * Called when the join query button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onJoinQueryClick(View v) {
		final String smartSql = getString(R.string.join_button_query);
		final JSONArray opportunities = smartStoreIntf.query(smartSql);
		if (opportunities != null) {
			launchResultActivity(opportunities.toString());	
		}
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
					final JSONArray records = result.asJSONObject().getJSONArray("records");
					if (obj.equals("Account")) {
						smartStoreIntf.insertAccounts(records);
					} else if (obj.equals("Opportunity")) {
						smartStoreIntf.insertOpportunities(records);
					} else {

						/*
						 * If the object is not an account or opportunity,
						 * we do nothing. This block can be used to save
						 * other types of records.
						 */
					}
				} catch (Exception e) {
					onError(e);
				} finally {
					progressDialog.dismiss();
					Toast.makeText(MainActivity.this, "Records ready for offline access.",
							Toast.LENGTH_SHORT).show();
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
