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
package com.salesforce.androidsdk.store;

import java.util.HashSet;
import java.util.Set;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.MultiAutoCompleteTextView;
import android.widget.TextView;

import com.salesforce.androidsdk.smartstore.R;
import com.salesforce.androidsdk.smartstore.store.DBHelper;
import com.salesforce.androidsdk.smartstore.store.DBOpenHelper;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;
import com.salesforce.androidsdk.smartstore.ui.SmartStoreInspectorActivity;

/**
 * Tests for ServerPickerActivity
 */
public class SmartStoreInspectorActivityTest extends
	ActivityInstrumentationTestCase2<SmartStoreInspectorActivity> {

	private static final String TEST_SOUP = "test_soup";
	private static final String OTHER_TEST_SOUP = "other_test_soup";
	private static final int NUMBER_ROWS_TEST_SOUP = 5;
	private static final int NUMBER_ROWS_OTHER_TEST_SOUP = 10;

	private Context targetContext;
	private SmartStore store;

	public SmartStoreInspectorActivityTest() {
		super("com.salesforce.samples.templateapp",
				SmartStoreInspectorActivity.class);
	}

	@Override
	public void setUp() throws Exception {
		super.setUp();
		setActivityInitialTouchMode(false);
		targetContext = getInstrumentation().getTargetContext();
		createStore();
		createSoups();
		populateSoup(TEST_SOUP, NUMBER_ROWS_TEST_SOUP);
		populateSoup(OTHER_TEST_SOUP, NUMBER_ROWS_OTHER_TEST_SOUP);

		// Check initial state
		checkInspectorIsReset();
	}

	/**
	 * Testing "clear" button
	 */
	public void testClickingClear() {
		clickButton(R.id.sf__inspector_indices_button);
		assertNotNull(getActivity().getLastResults());

		clickButton(R.id.sf__inspector_clear_button);
		checkInspectorIsReset();
	}

	/**
	 * Testing "soups" button
	 */
	public void testClickingSoups() {
		clickButton(R.id.sf__inspector_soups_button);
		checkInspectorState(
				"select 'other_test_soup', count(*) from {other_test_soup} union select 'test_soup', count(*) from {test_soup}",
				"", "", null, null,
				"[[\"other_test_soup\",10],[\"test_soup\",5]]");
	}

	/**
	 * Testing "indices" button
	 */
	public void testClickingIndices() {
		clickButton(R.id.sf__inspector_indices_button);
		checkInspectorState(
				"select soupName, path, columnType from soup_index_map", "",
				"", null, null,
				"[[\"test_soup\",\"key\",\"string\"],[\"other_test_soup\",\"key\",\"string\"]]");
	}

	/**
	 * Testing "run" button without specifying a query
	 */
	public void testClickingRunWithoutQuery() {
		clickButton(R.id.sf__inspector_run_button);
		checkInspectorState("", "", "", null, "No query specified", null);
	}

	/**
	 * Testing "run" button with invalid query
	 */
	public void testClickingRunWithInvalidQuery() {
		String query = "SELECT {test_soup:key} FROM {test_soup2}";
		setText(R.id.sf__inspector_query_text, query);
		clickButton(R.id.sf__inspector_run_button);
		checkInspectorState(query, "", "", "SmartSqlException",
				"Unknown soup test_soup2", null);
	}

	/**
	 * Testing "run" button with a valid query that returns no results
	 */
	public void testClickingRunWithValidQueryNoResults() {
		String query = "SELECT {test_soup:key} FROM {test_soup} WHERE {test_soup:key} == 'non-existent-key'";
		setText(R.id.sf__inspector_query_text, query);
		clickButton(R.id.sf__inspector_run_button);

		checkInspectorState(query, "", "", null, "No rows returned", "[]");
	}

	/**
	 * Testing "run" button with a valid query
	 */
	public void testClickingRunWithValidQuery() {
		String query = "SELECT {test_soup:key} FROM {test_soup} WHERE {test_soup:key} == 'k_test_soup_1'";
		setText(R.id.sf__inspector_query_text, query);
		clickButton(R.id.sf__inspector_run_button);

		checkInspectorState(query, "", "", null, null, "[[\"k_test_soup_1\"]]");
	}

	/**
	 * Testing "run" button with a valid query and specified page size
	 */
	public void testClickingRunWithValidQueryAndPageSize() {
		String query = "SELECT {test_soup:key} FROM {test_soup} ORDER BY {test_soup:key}";
		String pageSize = "2";
		setText(R.id.sf__inspector_query_text, query);
		setText(R.id.sf__inspector_pagesize_text, pageSize);
		clickButton(R.id.sf__inspector_run_button);
		checkInspectorState(query, pageSize, "", null, null,
				"[[\"k_test_soup_0\"],[\"k_test_soup_1\"]]");
	}

	/**
	 * Testing "run" button with a valid query and specified page size and page
	 * index
	 */
	public void testClickingRunWithValidQueryAndPageSizeAndPageIndex() {
		String query = "SELECT {test_soup:key} FROM {test_soup} ORDER BY {test_soup:key}";
		String pageSize = "2";
		String pageIndex = "1";
		setText(R.id.sf__inspector_query_text, query);
		setText(R.id.sf__inspector_pagesize_text, pageSize);
		setText(R.id.sf__inspector_pageindex_text, pageIndex);
		clickButton(R.id.sf__inspector_run_button);
		checkInspectorState(query, pageSize, pageIndex, null, null,
				"[[\"k_test_soup_2\"],[\"k_test_soup_3\"]]");
	}

	/**
	 * Testing autocomplete
	 */
	public void testAutoComplete() {
		MultiAutoCompleteTextView queryTextView = (MultiAutoCompleteTextView) getActivity()
				.findViewById(R.id.sf__inspector_query_text);
		ListAdapter adapter = queryTextView.getAdapter();
		Set<String> values = new HashSet<String>();
		for (int i = 0; i < adapter.getCount(); i++) {
			values.add((String) adapter.getItem(i));
		}

		String[] expectedValues = { "select", "from", "where", "group by",
				"order by", "{test_soup}", "{test_soup:key}",
				"{test_soup:_soupEntryId}", "{test_soup:_soup}",
				"{test_soup:_soupLastModifiedDate}", "{other_test_soup}",
				"{other_test_soup:key}", "{other_test_soup:_soupEntryId}",
				"{other_test_soup:_soup}",
				"{other_test_soup:_soupLastModifiedDate}" };
		for (String expectedValue : expectedValues) {
			assertTrue("Autocomplete should offer " + expectedValue,
					values.contains(expectedValue));
		}
	}

	private void createStore() {
		DBHelper.INSTANCE.reset(targetContext, null); // start clean
		SQLiteDatabase db = DBOpenHelper.getOpenHelper(targetContext, null)
				.getWritableDatabase("");
		store = new SmartStore(db);
	}

	private void createSoups() {
		for (String soupName : new String[] { TEST_SOUP, OTHER_TEST_SOUP }) {
			assertFalse("Soup " + soupName + " should not exist",
					store.hasSoup(soupName));
			store.registerSoup(soupName, new IndexSpec[] { new IndexSpec("key",
					Type.string) });
			assertTrue("Soup " + soupName + " should now exist",
					store.hasSoup(soupName));
		}
	}

	private void populateSoup(String soupName, int numberRows)
			throws JSONException {
		for (int i = 0; i < numberRows; i++) {
			JSONObject soupElt = new JSONObject("{'key':'k_" + soupName + "_"
					+ i + "', 'value':'v_" + soupName + "_" + i + "'}");
			store.create(soupName, soupElt);
		}
	}

	private void clickButton(int id) {
		clickView(getActivity().findViewById(id));
		waitSome();
	}

	private void checkText(String message, int id, String expectedString) {
		TextView view = (TextView) getActivity().findViewById(id);
		assertNotNull("TextView not found", view);
		assertEquals(message, expectedString, view.getText().toString());
	}

	private void checkInspectorIsReset() {
		checkInspectorState("", "", "", null, null, null);
	}

	/**
	 * Check inspector state
	 * 
	 * @param query
	 *            expected value for query input field
	 * @param pageSize
	 *            expected value for page size input field
	 * @param pageIndex
	 *            expected value for page index input field
	 * @param expectedAlertTitle
	 *            expected value for last alert title
	 * @param expectedAlertMessageSubstring
	 *            expected substring of last alert message
	 * @param expectedResultsAsString
	 *            expected results (stringified)
	 */
	private void checkInspectorState(String query, String pageSize,
			String pageIndex, String expectedAlertTitle,
			String expectedAlertMessageSubstring, String expectedResultsAsString) {
		// Check input fields
		checkText("Wrong query", R.id.sf__inspector_query_text, query);
		checkText("Wrong page size", R.id.sf__inspector_pagesize_text, pageSize);
		checkText("Wrong page index", R.id.sf__inspector_pageindex_text,
				pageIndex);

		// Check results
		if (expectedResultsAsString == null) {
			assertNull("Wrong results", getActivity().getLastResults());
		} else {
			assertEquals("Wrong results", expectedResultsAsString,
					getActivity().getLastResults().toString());
		}

		// Check alert
		assertEquals("Wrong alert title", expectedAlertTitle, getActivity()
				.getLastAlertTitle());
		String actualAlertMessage = getActivity().getLastAlertMessage();
		if (expectedAlertMessageSubstring == null
				|| expectedAlertMessageSubstring.length() == 0) {
			assertEquals("Wrong alert message", expectedAlertMessageSubstring,
					actualAlertMessage);
		} else {
			assertTrue("Wrong alert message",
					actualAlertMessage.contains(expectedAlertMessageSubstring));
		}
	}

    private void clickView(final View v) {
        try {
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    v.performClick();
                }
            });
        } catch (Throwable t) {
            fail("Failed to click view " + v);
        }
    }
    
    private void setText(final int textViewId, final String text) {
        try {
            runTestOnUiThread(new Runnable() {
                @Override public void run() {
                    TextView v = (TextView) getActivity().findViewById(textViewId);
                    v.setText(text);
                    if (v instanceof EditText)
                        ((EditText) v).setSelection(v.getText().length());
                }
            });
        } catch (Throwable t) {
            fail("Failed to set text " + text);
        }
    }
    
    private void waitSome() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }
    }
}
