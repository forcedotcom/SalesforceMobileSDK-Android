/*
 * Copyright (c) 2011, salesforce.com, inc.
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
package com.salesforce.samples.restexplorer;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TabActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TabHost;
import android.widget.TextView;

import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;
import com.salesforce.androidsdk.rest.ClientManager.RestClientCallback;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestClient.AsyncRequestCallback;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestRequest.RestMethod;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

/**
 * Activity for explorer
 * 
 */
public class ExplorerActivity extends TabActivity {

	private static final String DOUBLE_LINE = "==============================================================================";
	private static final String SINGLE_LINE = "------------------------------------------------------------------------------";
	private static final int LOGOUT_CONFIRMATION_DIALOG_ID = 0;

	private String apiVersion;
	private RestClient client;
	private TextView resultText;
	AlertDialog logoutConfirmationDialog;

	// Use for objectId fields auto-complete
	private TreeSet<String> knownIds = new TreeSet<String>();

	RestClient getClient() {
		return client;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// ApiVersion
		apiVersion = getString(R.string.api_version);

		// Setup view
		setContentView(R.layout.explorer);
		
		// Setup tabs
		TabHost tabHost = (TabHost) findViewById(android.R.id.tabhost);
		addTab(tabHost, "versions", R.string.versions_tab, R.id.versions_tab);
		addTab(tabHost, "resources", R.string.resources_tab, R.id.resources_tab);
		addTab(tabHost, "describe_global", R.string.describe_global_tab,
				R.id.describe_global_tab);
		addTab(tabHost, "metadata", R.string.metadata_tab, R.id.metadata_tab);
		addTab(tabHost, "describe", R.string.describe_tab, R.id.describe_tab);
		addTab(tabHost, "create", R.string.create_tab, R.id.create_tab);
		addTab(tabHost, "retrieve", R.string.retrieve_tab, R.id.retrieve_tab);
		addTab(tabHost, "update", R.string.update_tab, R.id.update_tab);
		addTab(tabHost, "upsert", R.string.upsert_tab, R.id.upsert_tab);
		addTab(tabHost, "delete", R.string.delete_tab, R.id.delete_tab);
		addTab(tabHost, "query", R.string.query_tab, R.id.query_tab);
		addTab(tabHost, "search", R.string.search_tab, R.id.search_tab);
		addTab(tabHost, "manual", R.string.manual_request_tab,
				R.id.manual_request_tab);

		// Make result area scrollable
		resultText = (TextView) findViewById(R.id.result_text);
		resultText.setMovementMethod(new ScrollingMovementMethod());
	}
	
	@Override 
	public void onResume() {
		super.onResume();
		
		// Hide everything until we are logged in
		findViewById(R.id.root).setVisibility(View.INVISIBLE);
		
		// Bring up passcode screen if needed
		ForceApp.APP.getPasscodeManager().lockIfNeeded(this, true);
		
		// Do nothing - when the app gets unlocked we will be back here
		if (ForceApp.APP.getPasscodeManager().isLocked()) {
			return;
		}
		
		// Login options
		String accountType = ForceApp.APP.getAccountType();
    	LoginOptions loginOptions = new LoginOptions(
    			null, // gets overridden by LoginActivity based on server picked by uuser 
    			ForceApp.APP.getPasscodeHash(),
    			getString(R.string.oauth_callback_url),
    			getString(R.string.oauth_client_id),
    			new String[] {"api"});
		
		// Get a rest client
		new ClientManager(this, accountType, loginOptions).getRestClient(this, new RestClientCallback() {
			@Override
			public void authenticatedRestClient(RestClient client) {
				if (client == null) {
					ForceApp.APP.logout(ExplorerActivity.this);
					return;
				}
				ExplorerActivity.this.client = client;
				
				// Show everything
				findViewById(R.id.root).setVisibility(View.VISIBLE);				
			}
		});
	}
	
	@Override
	public void onUserInteraction() {
		ForceApp.APP.getPasscodeManager().recordUserInteraction();
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		if (id == LOGOUT_CONFIRMATION_DIALOG_ID) {
			logoutConfirmationDialog = new AlertDialog.Builder(this)
				.setTitle(R.string.logout_title)
				.setPositiveButton(R.string.logout_yes,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								ForceApp.APP.logout(ExplorerActivity.this);
							}
						})
				.setNegativeButton(R.string.logout_cancel, null)
				.create();
			return logoutConfirmationDialog;
		}
		return super.onCreateDialog(id);
	}
	
	
	private void addTab(TabHost tabHost, String tag, int titleId, int tabId) {
		tabHost.addTab(tabHost.newTabSpec(tag).setIndicator(getString(titleId))
				.setContent(tabId));
	}

	/**************************************************************************************************
	 * 
	 * Buttons click handlers
	 * 
	 **************************************************************************************************/

	/**
	 * Called when "print info" button is clicked.
	 * 
	 * @param v
	 */
	public void onPrintInfoClick(View v) {
		printInfo();
	}

	/**
	 * Called when "clear" button is clicked.
	 * 
	 * @param v
	 */
	public void onClearClick(View v) {
		resultText.setText("");
	}

	/**
	 * Called when "get versions" button is clicked.
	 * 
	 * @param v
	 */
	public void onGetVersionsClick(View v) {
		sendRequest(RestRequest.getRequestForVersions());
	}

	/**
	 * Called when "get resources" button is clicked.
	 * 
	 * @param v
	 */
	public void onGetResourcesClick(View v) {
		sendRequest(RestRequest.getRequestForResources(apiVersion));
	}

	/**
	 * Called when "describe global" button is clicked.
	 * 
	 * @param v
	 */
	public void onDescribeGlobalClick(View v) {
		sendRequest(RestRequest.getRequestForDescribeGlobal(apiVersion));
	}

	/**
	 * Called when "get metadata" button is clicked.
	 * 
	 * @param v
	 */
	public void onGetMetadataClick(View v) {
		String objectType = ((EditText) findViewById(R.id.metadata_object_type_text))
				.getText().toString();
		sendRequest(RestRequest.getRequestForMetadata(apiVersion, objectType));
	}

	/**
	 * Called when "describe" button is clicked.
	 * 
	 * @param v
	 */
	public void onDescribeClick(View v) {
		String objectType = ((EditText) findViewById(R.id.describe_object_type_text))
				.getText().toString();
		sendRequest(RestRequest.getRequestForDescribe(apiVersion, objectType));
	}

	/**
	 * Called when "create" button is clicked.
	 * 
	 * @param v
	 */
	public void onCreateClick(View v) {
		String objectType = ((EditText) findViewById(R.id.create_object_type_text))
				.getText().toString();
		Map<String, Object> fields = parseFieldMap(R.id.create_fields_text);

		RestRequest request = null;
		try {
			request = RestRequest.getRequestForCreate(apiVersion, objectType,
					fields);
		} catch (Exception e) {
			printHeader("Could not build create request");
			printException(e);
			return;
		}
		sendRequest(request);
	}

	/**
	 * Called when "retrieve" button is clicked.
	 * 
	 * @param v
	 */
	public void onRetrieveClick(View v) {
		String objectType = ((EditText) findViewById(R.id.retrieve_object_type_text))
				.getText().toString();
		String objectId = ((EditText) findViewById(R.id.retrieve_object_id_text))
				.getText().toString();
		List<String> fieldList = parseFieldList(R.id.retrieve_field_list_text);

		RestRequest request = null;
		try {
			request = RestRequest.getRequestForRetrieve(apiVersion, objectType,
					objectId, fieldList);
		} catch (Exception e) {
			printHeader("Could not build retrieve request");
			printException(e);
			return;
		}
		sendRequest(request);
	}

	/**
	 * Called when "update" button is clicked.
	 * 
	 * @param v
	 */
	public void onUpdateClick(View v) {
		String objectType = ((EditText) findViewById(R.id.update_object_type_text))
				.getText().toString();
		String objectId = ((EditText) findViewById(R.id.update_object_id_text))
				.getText().toString();
		Map<String, Object> fields = parseFieldMap(R.id.update_fields_text);

		RestRequest request = null;
		try {
			request = RestRequest.getRequestForUpdate(apiVersion, objectType,
					objectId, fields);
		} catch (Exception e) {
			printHeader("Could not build update request");
			printException(e);
			return;
		}
		sendRequest(request);
	}

	/**
	 * Called when "upsert" button is clicked.
	 * 
	 * @param v
	 */
	public void onUpsertClick(View v) {
		String objectType = ((EditText) findViewById(R.id.upsert_object_type_text))
				.getText().toString();
		String externalIdField = ((EditText) findViewById(R.id.upsert_external_id_field_text))
				.getText().toString();
		String externalId = ((EditText) findViewById(R.id.upsert_external_id_text))
				.getText().toString();
		Map<String, Object> fields = parseFieldMap(R.id.upsert_fields_text);

		RestRequest request = null;
		try {
			request = RestRequest.getRequestForUpsert(apiVersion, objectType,
					externalIdField, externalId, fields);
		} catch (Exception e) {
			printHeader("Could not build upsert request");
			printException(e);
			return;
		}
		sendRequest(request);
	}

	/**
	 * Called when "delete" button is clicked.
	 * 
	 * @param v
	 */
	public void onDeleteClick(View v) {
		String objectType = ((EditText) findViewById(R.id.delete_object_type_text))
				.getText().toString();
		String objectId = ((EditText) findViewById(R.id.delete_object_id_text))
				.getText().toString();

		sendRequest(RestRequest.getRequestForDelete(apiVersion, objectType,
				objectId));
	}

	/**
	 * Called when "query" button is clicked.
	 * 
	 * @param v
	 */
	public void onQueryClick(View v) {
		String soql = ((EditText) findViewById(R.id.query_soql_text)).getText()
				.toString();
		RestRequest request = null;
		try {
			request = RestRequest.getRequestForQuery(apiVersion, soql);
		} catch (UnsupportedEncodingException e) {
			printHeader("Could not build query request");
			printException(e);
			return;
		}

		sendRequest(request);
	}

	/**
	 * Called when "search" button is clicked.
	 * 
	 * @param v
	 */
	public void onSearchClick(View v) {
		String sosl = ((EditText) findViewById(R.id.search_sosl_text))
				.getText().toString();
		RestRequest request = null;
		try {
			request = RestRequest.getRequestForSearch(apiVersion, sosl);
		} catch (UnsupportedEncodingException e) {
			printHeader("Could not build search request");
			printException(e);
			return;
		}

		sendRequest(request);
	}

	/**
	 * Called when "manual" button is clicked.
	 * 
	 * @param v
	 */
	public void onManualRequestClick(View v) {
		RestRequest request = null;
		try {
			String path = ((EditText) findViewById(R.id.manual_request_path_text))
					.getText().toString();
			HttpEntity paramsEntity = getParamsEntity(R.id.manual_request_params_text);
			RestMethod method = getMethod(R.id.manual_request_method_radiogroup);
			request = new RestRequest(method, path, paramsEntity);
		} catch (UnsupportedEncodingException e) {
			printHeader("Could not build manual request");
			printException(e);
			return;
		}

		sendRequest(request);
	}

	/**
	 * @param manualRequestParamsText
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	private HttpEntity getParamsEntity(int manualRequestParamsText)
			throws UnsupportedEncodingException {
		Map<String, Object> params = parseFieldMap(R.id.manual_request_params_text);
		if (params == null) {
			params = new HashMap<String, Object>();
		}
		List<NameValuePair> paramsList = new ArrayList<NameValuePair>();
		for (Entry<String, Object> param : params.entrySet()) {
			paramsList.add(new BasicNameValuePair(param.getKey(),
					(String) param.getValue()));
		}
		return new UrlEncodedFormEntity(paramsList);
	}

	/**
	 * @param methodRadioGroup
	 * @return
	 */
	private RestMethod getMethod(int methodRadioGroup) {
		RadioGroup radioGroup = (RadioGroup) findViewById(methodRadioGroup);
		RadioButton radioButton = (RadioButton) findViewById(radioGroup
				.getCheckedRadioButtonId());
		RestMethod method = RestMethod.valueOf((String) radioButton.getTag());
		return method;
	}

	/**
	 * Called when "Logout" button is clicked. Brings up the logout confirmation
	 * dialog.
	 * 
	 * @param v
	 */
	public void onLogoutClick(View v) {
		showDialog(LOGOUT_CONFIRMATION_DIALOG_ID);
	}

	/**
	 * Helper to read json string representing field name-value map
	 * 
	 * @param jsonTextField
	 * @return
	 */
	private Map<String, Object> parseFieldMap(int jsonTextField) {
		String fieldsString = ((EditText) findViewById(jsonTextField))
				.getText().toString();
		if (fieldsString.length() == 0) {
			return null;
		}

		try {
			JSONObject fieldsJson = new JSONObject(fieldsString);
			Map<String, Object> fields = new HashMap<String, Object>();
			JSONArray names = fieldsJson.names();
			for (int i = 0; i < names.length(); i++) {
				String name = (String) names.get(i);
				fields.put(name, fieldsJson.get(name));
			}
			return fields;

		} catch (Exception e) {
			printHeader("Could not parse: " + fieldsString);
			printException(e);
			return null;
		}
	}

	/**
	 * @param retrieveFieldsListText
	 * @return
	 */
	private List<String> parseFieldList(int retrieveFieldsListText) {
		String fieldsCsv = ((EditText) findViewById(retrieveFieldsListText))
				.getText().toString();
		return Arrays.asList(fieldsCsv.split(","));
	}

	/**
	 * Helper that sends request to server and print result in text field
	 * 
	 * @param request
	 */
	private void sendRequest(RestRequest request) {
		hideKeyboard();

		println("");
		printHeader(request);

		try {
			sendFromUIThread(request);
			// response is printed by RestCallTask:onPostExecute
		} catch (Exception e) {
			printException(e);
		}
	}

	/**
	 * Send restRequest using RestClient's sendAsync method.
	 * Note: Synchronous calls are not allowed from code running on the UI thread. 
	 * @param restRequest
	 */
	private void sendFromUIThread(RestRequest restRequest) {
		client.sendAsync(restRequest, new AsyncRequestCallback() {
			private long start = System.nanoTime();

			@Override
			public void onSuccess(RestRequest request, RestResponse result) {
				try {
					long duration = System.nanoTime() - start;
					println(result);
					int size = result.asString().length();
					int statusCode = result.getStatusCode();
					printRequestInfo(duration, size, statusCode);
					extractIdsFromResponse(result.asString());
				} catch (Exception e) {
					printException(e);
				}
			
				EventsObservable.get().notifyEvent(EventType.RenditionComplete);
			}
			
			@Override
			public void onError(Exception exception) {
				printException(exception);
				EventsObservable.get().notifyEvent(EventType.RenditionComplete);				
			}
		});
	}

	/**
	 * Helper method to hide soft keyboard
	 */
	private void hideKeyboard() {
		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(resultText.getWindowToken(), 0);
	}
	
	
	/**************************************************************************************************
	 * 
	 * Pretty printing helpers
	 * 
	 **************************************************************************************************/

	private void printRequestInfo(long nanoDuration, int characterLength, int statusCode) {
		println(SINGLE_LINE);
		println("Time (ms): " + nanoDuration / 1000000);
		println("Size (chars): " + characterLength);
		println("Status code: " + statusCode);
	}

	private void printException(Exception e) {
		println("Error: " + e.getClass().getSimpleName());
		println(e.getMessage());
	}

	private void printHeader(Object obj) {
		println(DOUBLE_LINE);
		println(obj);
		println(SINGLE_LINE);
	}

	/**
	 * Helper method to pretty print object in the result_text field
	 * 
	 * @param object
	 */
	private void println(Object object) {
		if (resultText == null)
			return;

		StringBuffer sb = new StringBuffer(resultText.getText());
		String text;
		if (object == null) {
			text = "null";
		} else {
			text = object.toString();
		}
		sb.append(text).append("\n");
		resultText.setText(sb);

		// Auto scroll to bottom if needed
		if (resultText.getLayout() != null) {
			int scroll = resultText.getLayout().getLineTop(
					resultText.getLineCount())
					- resultText.getHeight();
			resultText.scrollTo(0, scroll > 0 ? scroll : 0);
		}
	}
	
	/**
	 * Dump info about app and rest client 
	 */
	private void printInfo() {
		printHeader("Info");
		println(ForceApp.APP);
		println(client);
	}

	/**
	 * Helper to show/hide several views
	 * 
	 * @param resIds
	 */
	public void showHide(boolean show, int... resIds) {
		for (int resId : resIds) {
			View v = findViewById(resId);
			if (v != null)
				v.setVisibility(show ? View.VISIBLE : View.GONE);
		}
	}

	/**************************************************************************************************
	 * 
	 * Extracting ids from response for auto-complete
	 * 
	 **************************************************************************************************/

	private Pattern idPattern = Pattern.compile("0[0-9a-zA-Z]{17}");

	private void extractIdsFromResponse(String responseString) {
		Matcher matcher = idPattern.matcher(responseString);
		List<String> ids = new ArrayList<String>();
		while (matcher.find()) {
			ids.add(matcher.group());
		}
		knownIds.addAll(ids);
		fixAutoCompleteFields(R.id.retrieve_object_id_text, R.id.update_object_id_text,
				R.id.delete_object_id_text);
	}

	private void fixAutoCompleteFields(int... fieldIds) {
		for (int fieldId : fieldIds) {
			AutoCompleteTextView tv = (AutoCompleteTextView) findViewById(fieldId);
			tv.setAdapter(new ArrayAdapter<String>(this,
					android.R.layout.simple_dropdown_item_1line, knownIds
							.toArray(new String[] {})));
		}
	}

}