/*
 * Copyright (c) 2012, salesforce.com, inc.
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

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.database.Cursor;
import android.test.InstrumentationTestCase;

import com.salesforce.androidsdk.smartstore.store.DBHelper;
import com.salesforce.androidsdk.smartstore.store.SmartStore;

/**
 * Abstract super class for smart store tests
 *
 */
public abstract class SmartStoreTestCase extends InstrumentationTestCase {

	protected Context targetContext;
	protected SQLiteDatabase db;
	protected SmartStore store;
	
	@Override
	public void setUp() throws Exception {
		super.setUp();
		targetContext = getInstrumentation().getTargetContext();
		DBHelper.INSTANCE.reset(targetContext); // start clean
		db = getWritableDatabase();
		store = new SmartStore(db);
	}
	
	protected abstract SQLiteDatabase getWritableDatabase();

	@Override
	protected void tearDown() throws Exception {
		db.close();
		// Not cleaning up after the test to make diagnosing issues easier
		super.tearDown();
	}

	/**
	 * Helper method to check that a table exists in the database
	 * @param tableName
	 * @return
	 */
	protected boolean hasTable(String tableName) {
		Cursor c = null;
		try {
			c = DBHelper.INSTANCE.query(db, "sqlite_master", null, null, null, "type = ? and name = ?", "table", tableName);
			return c.getCount() == 1;
		}
		finally {
			safeClose(c);
		}
	}
	
	/**
	 * Close cursor if not null
	 * @param c
	 */
	protected void safeClose(Cursor c) {
		if (c != null) {
			c.close();
		}
	}

	/**
	 * @param soupElt
	 * @return _soupEntryId field value
	 * @throws JSONException
	 */
	protected long idOf(JSONObject soupElt) throws JSONException {
		return soupElt.getLong(SmartStore.SOUP_ENTRY_ID);
	}

	/**
	 * Compare two JSON
	 * @param message
	 * @param expected
	 * @param actual
	 * @throws JSONException
	 */
	protected void assertSameJSON(String message, Object expected, Object actual) throws JSONException {
		// At least one null
		if (expected == null || actual == null) {
			// Both null
			if (expected == null && actual == null) {
				return;
			}
			// One null, not the other
			else {
				assertTrue(message, false);
			}
		}
		// Both arrays
		else if (expected instanceof JSONArray && actual instanceof JSONArray) {
			assertSameJSONArray(message, (JSONArray) expected, (JSONArray) actual); 
		}
		// Both maps
		else if (expected instanceof JSONObject && actual instanceof JSONObject) {
			assertSameJSONObject(message, (JSONObject) expected, (JSONObject) actual); 
		}
		// Atomic types
		else {
			// Comparing string representations, to avoid things like new Long(n) != new Integer(n) 
			assertEquals(message, expected.toString(), actual.toString());
		}
	}
	
	/**
	 * Compare two JSON arrays
	 * @param message
	 * @param expected
	 * @param actual
	 * @throws JSONException
	 */
	protected void assertSameJSONArray(String message, JSONArray expected, JSONArray actual) throws JSONException {
		// First compare length
		assertEquals(message, expected.length(), actual.length());
		
		// If string value match we are done
		if (expected.toString().equals(actual.toString())) {
			// Done
			return;
		}
		// If string values don't match, it might still be the same object (toString does not sort fields of maps)
		else {
			// Compare values
			for (int i=0; i<expected.length(); i++) {
				assertSameJSON(message, expected.get(i), actual.get(i));
			}
		}
	}
	
	/**
	 * Compare two JSON maps
	 * @param message
	 * @param expected
	 * @param actual
	 * @throws JSONException
	 */
	protected void assertSameJSONObject(String message, JSONObject expected, JSONObject actual) throws JSONException {
		// First compare length
		assertEquals(message, expected.length(), actual.length());
		
		// If string value match we are done
		if (expected.toString().equals(actual.toString())) {
			// Done
			return;
		}
		// If string values don't match, it might still be the same object (toString does not sort fields of maps)
		else {
			// Compare keys / values
			JSONArray expectedNames = expected.names();
			JSONArray actualNames = actual.names();
			assertEquals(message, expectedNames.length(), actualNames.length());
			JSONArray expectedValues = expected.toJSONArray(expectedNames);
			JSONArray actualValues = actual.toJSONArray(expectedNames);
			assertSameJSONArray(message, expectedValues, actualValues);
		}
	}

	/**
	 * @param soupName
	 * @return table name for soup
	 */
	protected String getSoupTableName(String soupName) {
		return DBHelper.INSTANCE.getSoupTableName(db, soupName);
	}
}