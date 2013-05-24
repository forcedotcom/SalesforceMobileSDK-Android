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

import java.text.NumberFormat;

import org.json.JSONArray;
import org.json.JSONException;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.ui.sfnative.SalesforceActivity;

/**
 * Displays results from queries.
 *
 * @author bhariharan
 */
public class ResultActivity extends SalesforceActivity {

	private static final String TAG = "NativeSqlAggregator: ResultActivity";
	public static final String RESULT_INTENT_ACTION = "com.salesforce.intent.SHOW_RESULT";
	public static final String DATA_EXTRA = "data";

	private TableLayout tableLayout;

	@Override
	public void onCreate(Bundle savedInstance) {
		super.onCreate(savedInstance);
		setContentView(R.layout.result);
		tableLayout = (TableLayout) findViewById(R.id.table_layout);
		tableLayout.setVisibility(View.VISIBLE);
		final Intent intent = getIntent();
		if (intent != null && intent.getAction().equals(RESULT_INTENT_ACTION)) {
			final String results = intent.getStringExtra(DATA_EXTRA);
			if (results != null && !results.trim().equals("")) {
				final JSONArray resultJsonArray = convertToJsonArray(results);
				if (resultJsonArray != null) {
					addHeaderRow();
					for (int i = 0; i < resultJsonArray.length(); i++) {
						try {
							final JSONArray jsonObj = resultJsonArray.getJSONArray(i);
							if (jsonObj != null) {
								double col3 = Double.parseDouble(jsonObj.get(2).toString());
								final NumberFormat baseFormat = NumberFormat.getCurrencyInstance();
								baseFormat.setMinimumFractionDigits(0);
								final String col3String = baseFormat.format(col3);
								double col4 = Double.parseDouble(jsonObj.get(3).toString());
								final String col4String = baseFormat.format(col4);

								/*
								 * A little hack to show just the fields
								 * we care about, with adequate spacing.
								 */
								addRow(jsonObj.get(0).toString(), jsonObj.get(1).toString(), col3String, col4String);
							}
						} catch (JSONException e) {
							Log.e(TAG, "Error occurred while parsing JSON.");
						}
					}
				}
			}
		}
	}

	@Override
	public void onResume(RestClient client) {
	}

	/**
	 * Converts a string to JSONArray.
	 *
	 * @param results Results.
	 * @return JSONArray.
	 */
	private JSONArray convertToJsonArray(String results) {
		JSONArray jsonArr = null;
		try {
			jsonArr = new JSONArray(results);	
		} catch (JSONException e) {
			Log.e(TAG, "Error occurred while converting String to JSONArray.");
		}
		return jsonArr;
	}

	/**
	 * Adds the row to the table.
	 */
	private void addHeaderRow() {
		final TextView textView1 = new TextView(this);
		textView1.setText("Account Name");
		textView1.setTextSize(16f);
		textView1.setGravity(Gravity.LEFT);
		textView1.setPadding(20, 20, 20, 20);
		final TextView textView2 = new TextView(this);
		textView2.setText("Opps");
		textView2.setTextSize(16f);
		textView2.setGravity(Gravity.RIGHT);
		textView2.setPadding(20, 20, 20, 20);
		final TextView textView3 = new TextView(this);
		textView3.setText("Total");
		textView3.setTextSize(16f);
		textView3.setGravity(Gravity.RIGHT);
		textView3.setPadding(20, 20, 20, 20);
		final TextView textView4 = new TextView(this);
		textView4.setText("Average");
		textView4.setTextSize(16f);
		textView4.setGravity(Gravity.RIGHT);
		textView4.setPadding(20, 20, 20, 20);
		final TableRow tableRow = new TableRow(this);
		tableRow.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT,
				TableRow.LayoutParams.WRAP_CONTENT));
		tableRow.addView(textView1);
		tableRow.addView(textView2);
		tableRow.addView(textView3);
		tableRow.addView(textView4);
		tableLayout.addView(tableRow);
	}

	/**
	 * Adds a row to the table.
	 *
	 * @param column1 Data contained in the first column.
	 * @param column2 Data contained in the second column.
	 * @param column3 Data contained in the third column.
	 * @param column4 Data contained in the fourth column.
	 */
	private void addRow(String column1, String column2, String column3, String column4) {
		final TextView textView1 = new TextView(this);
		textView1.setText(column1);
		textView1.setGravity(Gravity.LEFT);
		textView1.setPadding(20, 10, 20, 10);
		final TextView textView2 = new TextView(this);
		textView2.setText(column2);
		textView2.setGravity(Gravity.RIGHT);
		textView2.setPadding(20, 10, 20, 10);
		final TextView textView3 = new TextView(this);
		textView3.setText(column3);
		textView3.setGravity(Gravity.RIGHT);
		textView3.setPadding(20, 10, 20, 10);
		final TextView textView4 = new TextView(this);
		textView4.setText(column4);
		textView4.setGravity(Gravity.RIGHT);
		textView4.setPadding(20, 10, 20, 10);
		final TableRow tableRow = new TableRow(this);
		tableRow.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT,
				TableRow.LayoutParams.WRAP_CONTENT));
		tableRow.addView(textView1);
		tableRow.addView(textView2);
		tableRow.addView(textView3);
		tableRow.addView(textView4);
		tableLayout.addView(tableRow);
	}
}
