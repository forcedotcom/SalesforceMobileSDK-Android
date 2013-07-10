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
import android.graphics.Color;
import android.graphics.Typeface;
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
								double col4 = Double.parseDouble(jsonObj.get(3).toString());
								addRow(jsonObj.get(0).toString(), jsonObj.get(1).toString(), col3, col4);
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
		final TableRow tableRow = new TableRow(this);
		tableRow.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT,
				TableRow.LayoutParams.WRAP_CONTENT));
		tableRow.addView(styleHeaderRowElement("Account Name", Gravity.LEFT));
		tableRow.addView(styleHeaderRowElement("Opps", Gravity.RIGHT));
		tableRow.addView(styleHeaderRowElement("Total", Gravity.RIGHT));
		tableRow.addView(styleHeaderRowElement("Average", Gravity.RIGHT));
		tableLayout.addView(tableRow);
	}

	/**
	 * Adds style to a header row element.
	 *
	 * @param text Text displayed in the TextView.
	 * @param gravity Gravity for the text.
	 * @return TextView instance.
	 */
	private TextView styleHeaderRowElement(String text, int gravity) {
		final TextView textView = new TextView(this);
		textView.setText(text);
		textView.setTextColor(Color.GREEN);
		textView.setTypeface(null, Typeface.BOLD);
		textView.setTextSize(16f);
		textView.setGravity(gravity);
		textView.setPadding(20, 20, 20, 20);
		return textView;
	}

	/**
	 * Adds a row to the table.
	 *
	 * @param column1 Data contained in the first column.
	 * @param column2 Data contained in the second column.
	 * @param column3 Data contained in the third column.
	 * @param column4 Data contained in the fourth column.
	 */
	private void addRow(String column1, String column2, double column3, double column4) {
		final TableRow tableRow = new TableRow(this);
		tableRow.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT,
				TableRow.LayoutParams.WRAP_CONTENT));
		tableRow.addView(styleDataRowElement(column1, Gravity.LEFT));
		tableRow.addView(styleDataRowElement(column2, Gravity.RIGHT));
		tableRow.addView(styleDataRowElement(column3, Gravity.RIGHT));
		tableRow.addView(styleDataRowElement(column4, Gravity.RIGHT));
		tableLayout.addView(tableRow);
	}

	/**
	 * Adds style to a data row element.
	 *
	 * @param text Text displayed in the TextView.
	 * @param gravity Gravity for the text.
	 * @return TextView instance.
	 */
	private TextView styleDataRowElement(String text, int gravity) {
		final TextView textView = new TextView(this);
		textView.setText(text);
		textView.setGravity(gravity);
		textView.setPadding(20, 10, 20, 10);
		return textView;
	}

	/**
	 * Adds style to a data row element.
	 *
	 * @param text Text displayed in the TextView.
	 * @param gravity Gravity for the text.
	 * @return TextView instance.
	 */
	private TextView styleDataRowElement(double text, int gravity) {
		final TextView textView = new TextView(this);
		final NumberFormat baseFormat = NumberFormat.getCurrencyInstance();
		double col = text/1000;
		final String colString = baseFormat.format(col) + "k";
		textView.setText(colString);
		if (col > 1000) {
			textView.setTextColor(Color.RED);
		}
		textView.setGravity(gravity);
		textView.setPadding(20, 10, 20, 10);
		return textView;
	}
}
