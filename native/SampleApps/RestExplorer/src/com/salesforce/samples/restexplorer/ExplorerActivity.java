/*
 * Copyright (c) 2011-2014, salesforce.com, inc.
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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.RestClientCallback;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestClient.AsyncRequestCallback;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestRequest.RestMethod;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.security.PasscodeManager;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.UserSwitchReceiver;

/**
 * Main activity for REST explorer.
 */
public class ExplorerActivity extends Activity {

	private static final String DOUBLE_LINE = "==============================================================================";
	private static final String SINGLE_LINE = "------------------------------------------------------------------------------";

	private PasscodeManager passcodeManager;
	private String apiVersion;
	private RestClient client;
	private TextView resultText;
    private UserSwitchReceiver userSwitchReceiver;
    private TabHost tabHost;
    private LogoutDialogFragment logoutConfirmationDialog;

	// Use for objectId fields auto-complete.
	private TreeSet<String> knownIds = new TreeSet<String>();

	RestClient getClient() {
		return client;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		passcodeManager = SalesforceSDKManager.getInstance().getPasscodeManager();
		apiVersion = getString(R.string.api_version);
		setContentView(R.layout.explorer);
		tabHost = (TabHost) findViewById(android.R.id.tabhost);
		tabHost.setup();
		addTab("versions", R.string.versions_tab, R.id.versions_tab);
		addTab("resources", R.string.resources_tab, R.id.resources_tab);
		addTab("describe_global", R.string.describe_global_tab, R.id.describe_global_tab);
		addTab("metadata", R.string.metadata_tab, R.id.metadata_tab);
		addTab("describe", R.string.describe_tab, R.id.describe_tab);
		addTab("create", R.string.create_tab, R.id.create_tab);
		addTab("retrieve", R.string.retrieve_tab, R.id.retrieve_tab);
		addTab("update", R.string.update_tab, R.id.update_tab);
		addTab("upsert", R.string.upsert_tab, R.id.upsert_tab);
		addTab("delete", R.string.delete_tab, R.id.delete_tab);
		addTab("query", R.string.query_tab, R.id.query_tab);
		addTab("search", R.string.search_tab, R.id.search_tab);
		addTab("manual", R.string.manual_request_tab, R.id.manual_request_tab);

		// Makes the result area scrollable.
		resultText = (TextView) findViewById(R.id.result_text);
		resultText.setMovementMethod(new ScrollingMovementMethod());
        userSwitchReceiver = new ExplorerUserSwitchReceiver();
        registerReceiver(userSwitchReceiver, new IntentFilter(UserAccountManager.USER_SWITCH_INTENT_ACTION));
        logoutConfirmationDialog = new LogoutDialogFragment();
	}

	@Override 
	public void onResume() {
		super.onResume();
		
		// Hides everything until we are logged in.
		findViewById(R.id.root).setVisibility(View.INVISIBLE);
		
		// Brings up the passcode screen if needed.
		if (passcodeManager.onResume(this)) {
			String accountType = SalesforceSDKManager.getInstance().getAccountType();

			// Gets a rest client.
			new ClientManager(this, accountType, SalesforceSDKManager.getInstance().getLoginOptions(),
					SalesforceSDKManager.getInstance().shouldLogoutWhenTokenRevoked()).getRestClient(this, new RestClientCallback() {

				@Override
				public void authenticatedRestClient(RestClient client) {
					if (client == null) {
						SalesforceSDKManager.getInstance().logout(ExplorerActivity.this);
						return;
					}
					ExplorerActivity.this.client = client;
					
					// Shows everything.
					findViewById(R.id.root).setVisibility(View.VISIBLE);				
				}
			});
		}
	}

    @Override
    public void onPause() {
    	super.onPause();
    	passcodeManager.onPause(this);
    }

    @Override
    public void onDestroy() {
    	unregisterReceiver(userSwitchReceiver);
    	super.onDestroy();
    }

	@Override
	public void onUserInteraction() {
		passcodeManager.recordUserInteraction();
	}

	/**
	 * Returns the logout dialog fragment (used mainly by tests).
	 *
	 * @return Logout dialog fragment.
	 */
	public LogoutDialogFragment getLogoutConfirmationDialog() {
		return logoutConfirmationDialog;
	}

	private void addTab(String tag, int titleId, int tabId) {
		tabHost.addTab(tabHost.newTabSpec(tag).setIndicator(getString(titleId))
				.setContent(tabId));
	}

	/**************************************************************************************************
	 * 
	 * Buttons click handlers
	 * 
	 **************************************************************************************************/

	/**
	 * Called when the "print info" button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onPrintInfoClick(View v) {
		printInfo();
	}

	/**
	 * Called when the "clear" button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onClearClick(View v) {
		resultText.setText("");
	}

	/**
	 * Called when the "get versions" button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onGetVersionsClick(View v) {
		sendRequest(RestRequest.getRequestForVersions());
	}

	/**
	 * Called when the "Switch Account" button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onSwitchAccClick(View v) {
		final Intent i = new Intent(this, SalesforceSDKManager.getInstance().getAccountSwitcherActivityClass());
		i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		this.startActivity(i);
	}

	/**
	 * Called when the "get resources" button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onGetResourcesClick(View v) {
		sendRequest(RestRequest.getRequestForResources(apiVersion));
	}

	/**
	 * Called when the "describe global" button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onDescribeGlobalClick(View v) {
		sendRequest(RestRequest.getRequestForDescribeGlobal(apiVersion));
	}

	/**
	 * Called when the "metadata" button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onGetMetadataClick(View v) {
		final String objectType = ((EditText) findViewById(R.id.metadata_object_type_text))
				.getText().toString();
		sendRequest(RestRequest.getRequestForMetadata(apiVersion, objectType));
	}

	/**
	 * Called when the "describe" button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onDescribeClick(View v) {
		final String objectType = ((EditText) findViewById(R.id.describe_object_type_text))
				.getText().toString();
		sendRequest(RestRequest.getRequestForDescribe(apiVersion, objectType));
	}

	/**
	 * Called when the "create" button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onCreateClick(View v) {
		final String objectType = ((EditText) findViewById(R.id.create_object_type_text))
				.getText().toString();
		final Map<String, Object> fields = parseFieldMap(R.id.create_fields_text);
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
	 * Called when the "retrieve" button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onRetrieveClick(View v) {
		final String objectType = ((EditText) findViewById(R.id.retrieve_object_type_text))
				.getText().toString();
		final String objectId = ((EditText) findViewById(R.id.retrieve_object_id_text))
				.getText().toString();
		final List<String> fieldList = parseFieldList(R.id.retrieve_field_list_text);
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
	 * Called when the "update" button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onUpdateClick(View v) {
		final String objectType = ((EditText) findViewById(R.id.update_object_type_text))
				.getText().toString();
		final String objectId = ((EditText) findViewById(R.id.update_object_id_text))
				.getText().toString();
		final Map<String, Object> fields = parseFieldMap(R.id.update_fields_text);
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
	 * Called when the "upsert" button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onUpsertClick(View v) {
		final String objectType = ((EditText) findViewById(R.id.upsert_object_type_text))
				.getText().toString();
		final String externalIdField = ((EditText) findViewById(R.id.upsert_external_id_field_text))
				.getText().toString();
		final String externalId = ((EditText) findViewById(R.id.upsert_external_id_text))
				.getText().toString();
		final Map<String, Object> fields = parseFieldMap(R.id.upsert_fields_text);
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
	 * Called when the "delete" button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onDeleteClick(View v) {
		final String objectType = ((EditText) findViewById(R.id.delete_object_type_text))
				.getText().toString();
		final String objectId = ((EditText) findViewById(R.id.delete_object_id_text))
				.getText().toString();
		sendRequest(RestRequest.getRequestForDelete(apiVersion, objectType,
				objectId));
	}

	/**
	 * Called when the "query" button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onQueryClick(View v) {
		final String soql = ((EditText) findViewById(R.id.query_soql_text)).getText()
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
	 * Called when the "search" button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onSearchClick(View v) {
		final String sosl = ((EditText) findViewById(R.id.search_sosl_text))
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
	 * Called when the "manual" button is clicked.
	 *
	 * @param v View that was clicked.
	 */
	public void onManualRequestClick(View v) {
		RestRequest request = null;
		try {
			final EditText editText = (EditText) findViewById(R.id.manual_request_path_text);
			final String hintText = String.format(getResources().getString(R.string.path_hint),
					getResources().getString(R.string.api_version));
			editText.setHint(hintText);
			final String path = editText.getText().toString();
			final HttpEntity paramsEntity = getParamsEntity(R.id.manual_request_params_text);
			final RestMethod method = getMethod(R.id.manual_request_method_radiogroup);
			request = new RestRequest(method, path, paramsEntity);
		} catch (UnsupportedEncodingException e) {
			printHeader("Could not build manual request");
			printException(e);
			return;
		}
		sendRequest(request);
	}

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

	private RestMethod getMethod(int methodRadioGroup) {
		RadioGroup radioGroup = (RadioGroup) findViewById(methodRadioGroup);
		RadioButton radioButton = (RadioButton) findViewById(radioGroup
				.getCheckedRadioButtonId());
		RestMethod method = RestMethod.valueOf((String) radioButton.getTag());
		return method;
	}

	/**
	 * Called when "Logout" button is clicked. Brings up the
	 * logout confirmation dialog.
	 *
	 * @param v View that was clicked.
	 */
	public void onLogoutClick(View v) {
		logoutConfirmationDialog.show(getFragmentManager(), "LogoutDialog");
	}

	/**
	 * Helper to read a JSON string representing field name-value map.
	 *
	 * @param jsonTextField Text field.
	 * @return Map of JSON.
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

	private List<String> parseFieldList(int retrieveFieldsListText) {
		String fieldsCsv = ((EditText) findViewById(retrieveFieldsListText))
				.getText().toString();
		return Arrays.asList(fieldsCsv.split(","));
	}

	/**
	 * Helper that sends a request to the server and prints the result in a text field.
	 *
	 * @param request REST request.
	 */
	private void sendRequest(RestRequest request) {
		hideKeyboard();
		println("");
		printHeader(request);
		try {
			sendFromUIThread(request);
		} catch (Exception e) {
			printException(e);
		}
	}

	/**
	 * Sends a REST request using RestClient's sendAsync method.
	 * Note: Synchronous calls are not allowed from code running on the UI thread.
	 *
	 * @param restRequest REST request.
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
	 * Helper method to hide the soft keyboard.
	 */
	private void hideKeyboard() {
		final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
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
	 * Helper method to pretty print objects in the result_text field.
	 *
	 * @param object Object to print.
	 */
	private void println(Object object) {
		if (resultText == null) {
			return;
		}
		final StringBuffer sb = new StringBuffer(resultText.getText());
		String text;
		if (object == null) {
			text = "null";
		} else {
			text = object.toString();
		}
		sb.append(text).append("\n");
		resultText.setText(sb);

		// Auto scrolls to the bottom if needed.
		if (resultText.getLayout() != null) {
			int scroll = resultText.getLayout().getLineTop(
					resultText.getLineCount())
					- resultText.getHeight();
			resultText.scrollTo(0, scroll > 0 ? scroll : 0);
		}
	}

	/**
	 * Dumps info about the app and rest client.
	 */
	private void printInfo() {
		printHeader("Info");
		println(SalesforceSDKManager.getInstance());
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
			if (v != null) {
				v.setVisibility(show ? View.VISIBLE : View.GONE);
			}
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

    /**
     * Refreshes the client if the user has been switched.
     */
	private void refreshIfUserSwitched() {
		if (passcodeManager.onResume(this)) {
			final String accountType = SalesforceSDKManager.getInstance().getAccountType();

			// Gets a rest client.
			new ClientManager(this, accountType, SalesforceSDKManager.getInstance().getLoginOptions(),
					SalesforceSDKManager.getInstance().shouldLogoutWhenTokenRevoked()).getRestClient(this, new RestClientCallback() {

				@Override
				public void authenticatedRestClient(RestClient client) {
					if (client == null) {
						SalesforceSDKManager.getInstance().logout(ExplorerActivity.this);
						return;
					}
					ExplorerActivity.this.client = client;			
				}
			});
		}
	}

    /**
     * Acts on the user switch event.
     *
     * @author bhariharan
     */
    private class ExplorerUserSwitchReceiver extends UserSwitchReceiver {

		@Override
		protected void onUserSwitch() {
			refreshIfUserSwitched();
		}
    }
}
