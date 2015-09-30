/*
 * Copyright (c) 2014, salesforce.com, inc.
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
package com.salesforce.androidsdk.smartstore.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.GridLayoutAnimationController;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.MultiAutoCompleteTextView;
import android.widget.MultiAutoCompleteTextView.Tokenizer;

import com.salesforce.androidsdk.smartstore.R;
import com.salesforce.androidsdk.smartstore.app.SalesforceSDKManagerWithSmartStore;
import com.salesforce.androidsdk.smartstore.store.DBOpenHelper;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartSqlHelper;
import com.salesforce.androidsdk.smartstore.store.SmartStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedList;
import java.util.List;

public class SmartStoreInspectorActivity extends Activity {

	// Keys for extras bundle
	private static final String IS_GLOBAL_STORE = "isGlobalStore";
	private static final String DB_NAME = "dbName";

	// Default page size / index
	private static final int DEFAULT_PAGE_SIZE = 10;
	private static final int DEFAULT_PAGE_INDEX = 0;

	// Store
	private String dbName;
	private boolean isGlobal;
	private SmartStore smartStore;

	// View elements
	private MultiAutoCompleteTextView queryText;
	private EditText pageSizeText;
	private EditText pageIndexText;
	private GridView resultGrid;
	
	// Test support
	private String lastAlertTitle;
	private String lastAlertMessage;
	private JSONArray lastResults;

	/**
	 * Create intent to bring up inspector
	 * @param parentActivity
	 * @param isGlobal pass true to get an inspector for the default global smartstore
	 *                 pass false to get an inspector for the default user smartstore
	 * @param dbName
	 * @return
	 */
	public static Intent getIntent(Activity parentActivity, boolean isGlobal, String dbName) {
		final Bundle bundle = new Bundle();
		bundle.putBoolean(IS_GLOBAL_STORE, isGlobal);
		bundle.putString(DB_NAME, dbName);

		final Intent intent = new Intent(parentActivity, SmartStoreInspectorActivity.class);
		intent.putExtras(bundle);
		return intent;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		readExtras();
		setContentView(R.layout.sf__inspector);
		queryText = (MultiAutoCompleteTextView) findViewById(R.id.sf__inspector_query_text);
		pageSizeText = (EditText) findViewById(R.id.sf__inspector_pagesize_text);
		pageIndexText = (EditText) findViewById(R.id.sf__inspector_pageindex_text);
		resultGrid = (GridView) findViewById(R.id.sf__inspector_result_grid);
	}

	@Override
	protected void onResume() {
		super.onResume();
		final SalesforceSDKManagerWithSmartStore manager = SalesforceSDKManagerWithSmartStore.getInstance();
		smartStore = isGlobal
				? manager.getGlobalSmartStore(dbName)
				: manager.getSmartStore(dbName, manager.getUserAccountManager().getCurrentUser(), null);
		setupAutocomplete(queryText);
	}

	private void readExtras() {
		Bundle bundle = getIntent().getExtras();
		isGlobal = bundle == null ? false : bundle.getBoolean(IS_GLOBAL_STORE, false);
		dbName = bundle == null ? DBOpenHelper.DEFAULT_DB_NAME : bundle.getString(DB_NAME, DBOpenHelper.DEFAULT_DB_NAME);
	}

	/**
	 * Called when "Clear" button is clicked
	 * 
	 * @param v
	 */
	public void onClearClick(View v) {
		reset();
	}

	/**
	 * Reset activity to its original state
	 */
	public void reset() {
		queryText.setText("");
		pageSizeText.setText("");
		pageIndexText.setText("");
		resultGrid.setAdapter(null);
		lastAlertTitle = null;
		lastAlertMessage = null;
		lastResults = null;
	}
	
	/**
	 * @return title of last alert shown (used by tests)
	 */
	public String getLastAlertTitle() {
		return lastAlertTitle;
	}

	/**
	 * @return message of last alert shown (used by tests)
	 */
	public String getLastAlertMessage() {
		return lastAlertMessage;
	}
	
	/**
	 * @return last results shown (used by tests)
	 */
	public JSONArray getLastResults() {
		return lastResults;
	}

	/**
	 * Called when "Run" button is clicked
	 * 
	 * @param v
	 */
	public void onRunClick(View v) {
		runQuery();
	}

	/**
	 * Called when "Soups" button is clicked
	 * 
	 * @param v
	 */
	public void onSoupsClick(View v) {
		List<String> names = smartStore.getAllSoupNames();

		if (names.size() > 10) {
			queryText.setText(getString(R.string.sf__inspector_soups_query));
		} else {
			StringBuilder sb = new StringBuilder();
			boolean first = true;
			for (String name : names) {
				if (!first)
					sb.append(" union ");
				sb.append("select '");
				sb.append(name);
				sb.append("', count(*) from {");
				sb.append(name);
				sb.append("}");
				first = false;
			}
			queryText.setText(sb.toString());
		}
		runQuery();
	}

	/**
	 * Called when "Indices" button is clicked
	 * 
	 * @param v
	 */
	public void onIndicesClick(View v) {
		queryText
				.setText(getString(R.string.sf__inspector_indices_query));
		runQuery();
	}

	/**
	 * Helper method that builds query spec from typed query, runs it and
	 * updates result grid
	 */
	private void runQuery() {
		try {
			String query = queryText.getText().toString();
			if (query.length() == 0) {
				showAlert(null, getString(R.string.sf__inspector_no_query_specified));
				return;
			}
			int pageSize = getInt(pageSizeText, DEFAULT_PAGE_SIZE);
			int pageIndex = getInt(pageIndexText, DEFAULT_PAGE_INDEX);
			QuerySpec querySpec = QuerySpec
					.buildSmartQuerySpec(query, pageSize);
			showResult(smartStore.query(querySpec, pageIndex));
		} catch (Exception e) {
			showAlert(e.getClass().getSimpleName(), e.getMessage());
		}
	}

	/**
	 * Helper function to get integer typed in a text field Returns defaultValue
	 * if no integer were typed
	 * 
	 * @param textField
	 * @param defaultValue
	 * @return
	 */
	private int getInt(EditText textField, int defaultValue) {
		String s = textField.getText().toString();
		if (s.length() == 0) {
			return defaultValue;
		} else {
			return Integer.parseInt(s);
		}
	}

	/**
	 * Helper method to show an alert
	 * 
	 * @param e
	 */
	private void showAlert(String title, String message) {
		lastAlertTitle = title;
		lastAlertMessage = message;
		new AlertDialog.Builder(this).setTitle(title)
				.setMessage(message).show();
	}

	/**
	 * Helper method to populate result grid with query result set (expected to
	 * be a JSONArray of JSONArray's)
	 * 
	 * @param result
	 * @throws JSONException
	 */
	private void showResult(JSONArray result) throws JSONException {
		lastResults = result;
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
				R.layout.sf__inspector_result_cell);

		if (result.length() == 0) {
			showAlert(null, getString(R.string.sf__inspector_no_rows_returned));
		}

		for (int j = 0; j < result.length(); j++) {
			JSONArray row = result.getJSONArray(j);
			for (int i = 0; i < row.length(); i++) {
				Object val = row.get(i);
				adapter.add(val instanceof JSONObject ? ((JSONObject) val)
						.toString(2) : val.toString());
			}
		}

		int numColumns = (result.length() > 0 ? result.getJSONArray(0).length()
				: 0);
		resultGrid.setNumColumns(numColumns);
		resultGrid.setAdapter(adapter);
		animateGridView(resultGrid);
	}

	/**
	 * Helper method to attach animation to grid view
	 * 
	 * @param gridView
	 */
	private void animateGridView(GridView gridView) {
		Animation animation = AnimationUtils.loadAnimation(this,
				android.R.anim.fade_in);
		GridLayoutAnimationController animationController = new GridLayoutAnimationController(
				animation, 0f, 0.1f);
		gridView.setLayoutAnimation(animationController);
		animationController.start();
	}

	/**
	 * Helper method to setup auto-complete for query input field
	 * 
	 * @param textView
	 */
	private void setupAutocomplete(MultiAutoCompleteTextView textView) {
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_dropdown_item_1line);

		// Adding {soupName} and {soupName:specialField}
		List<String> names = new  LinkedList<String>();
		names.addAll(smartStore.getAllSoupNames());
		for (String name : names) {
			adapter.add("{" + name + "}");
			adapter.add("{" + name + ":" + SmartSqlHelper.SOUP + "}");
			adapter.add("{" + name + ":" + SmartStore.SOUP_ENTRY_ID + "}");
			adapter.add("{" + name + ":" + SmartStore.SOUP_LAST_MODIFIED_DATE
					+ "}");
		}

		// Adding {soupName:indexedPath}
		try {
			JSONArray result = smartStore.query(QuerySpec.buildSmartQuerySpec(
					"SELECT soupName, path FROM soup_index_map", 1000), 0);
			for (int j = 0; j < result.length(); j++) {
				JSONArray row = result.getJSONArray(j);
				adapter.add("{" + row.getString(0) + ":" + row.getString(1)
						+ "}");
			}
		} catch (JSONException e) {
			Log.e("SmartStoreInspector", "getIndices", e);
		}

		// Adding some SQL keywords
		adapter.add("select");
		adapter.add("from");
		adapter.add("where");
		adapter.add("order by");
		adapter.add("asc");
		adapter.add("desc");
		adapter.add("group by");

		textView.setAdapter(adapter);
		textView.setTokenizer(new QueryTokenizer());
	}

}

/**
 * Tokenized used by query auto-complete field
 * 
 * @author wmathurin
 * 
 */
class QueryTokenizer implements Tokenizer {

	public int findTokenStart(CharSequence text, int cursor) {
		int i = cursor;

		while (i > 0 && text.charAt(i - 1) != ' ') {
			i--;
		}

		return i;
	}

	public int findTokenEnd(CharSequence text, int cursor) {
		int i = cursor;
		int len = text.length();

		while (i < len) {
			if (text.charAt(i) == ' ') {
				return i;
			} else {
				i++;
			}
		}

		return len;
	}

	public CharSequence terminateToken(CharSequence text) {
		int i = text.length();

		while (i > 0 && text.charAt(i - 1) == ' ') {
			i--;
		}

		if (i > 0 && text.charAt(i - 1) == ' ') {
			return text;
		} else {
			if (text instanceof Spanned) {
				SpannableString sp = new SpannableString(text + " ");
				TextUtils.copySpansFrom((Spanned) text, 0, text.length(),
						Object.class, sp, 0);
				return sp;
			} else {
				return text;
			}
		}
	}
}