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
package com.salesforce.androidsdk.store;

import java.util.ArrayList;
import java.util.List;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.test.InstrumentationTestCase;
import android.util.Log;

import com.salesforce.androidsdk.store.SmartStore.IndexSpec;
import com.salesforce.androidsdk.store.SmartStore.QuerySpec;
import com.salesforce.androidsdk.store.SmartStore.Type;

/**
 * Set of tests for the smart store loading numerous and/or large entries and querying them back
 */
public class SmartStoreLoadTest extends InstrumentationTestCase {

	private static final int LOG2_MAX_NUMBER_ENTRIES = 10 /* stopping at 1024 entries */;	
	private static final int LOG2_MAX_LENGTH_FIELD = 16 /* stopping at 65536 characters */;
	private static final int LOG2_MAX_NUMBER_FIELDS = 10 /* stopping at 1024 fields */;

	private static final String TEST_SOUP = "test_soup";
	
	protected Context targetContext;
	private SQLiteDatabase db;
	private SmartStore store;
	
	@Override
	public void setUp() throws Exception {
		super.setUp();
		targetContext = getInstrumentation().getTargetContext();
		DBHelper.INSTANCE.reset(targetContext); // start clean
		db = getWritableDatabase();
		store = new SmartStore(db);
		
		assertFalse("Soup test_soup should not exist", store.hasSoup(TEST_SOUP));
		store.registerSoup(TEST_SOUP, new IndexSpec[] {new IndexSpec("key", Type.string)});
		assertTrue("Soup test_soup should now exist", store.hasSoup(TEST_SOUP));
	}
	
	protected SQLiteDatabase getWritableDatabase() {
		return DBOpenHelper.getOpenHelper(targetContext).getWritableDatabase("");
	}

	@Override
	protected void tearDown() throws Exception {
		db.close();
		// Not cleaning up after the test to make diagnosing issues easier
		super.tearDown();
	}

	/**
	 * Load more and more entries
	 * @throws JSONException 
	 */
	public void testLoadManyEntries() throws JSONException {
		int n = 1;
		for (int k=0; k <= LOG2_MAX_NUMBER_ENTRIES; k++) {
			// Upsert more entries
			List<Long> times = new ArrayList<Long>();
			for (int i=n/2; i<n; i++) {
				JSONObject entry = new JSONObject();
				entry.put("key", "k" + i);
				entry.put("value", "x");
				long start = System.currentTimeMillis();
				store.upsert(TEST_SOUP, entry);
				long end = System.currentTimeMillis();
				times.add(end-start);
				
			}

			// Compute average time taken
			long avg = 0;
			for (int i=0; i<times.size(); i++) {
				avg += times.get(i);
			}
			avg /= times.size();
			
			// Log avg time taken
			Log.i("SmartStoreLoadTest", "upserting entries [" + (n/2) + "," + n + "[,"
					+ " avg time taken: " + avg + " ms");
			
			
			// Query one of them
			QuerySpec query = QuerySpec.buildExactQuerySpec("key", "k" + ((n/2 + n) / 2), 1);
			long start = System.currentTimeMillis();
			JSONArray resultSet = store.querySoup(TEST_SOUP, query, 0);
			long end = System.currentTimeMillis();
			assertEquals("Should have returned one entry", 1, resultSet.length());
			Log.i("SmartStoreLoadTest", "querying entry out of " + n + ","
					+ " time taken: " + (end - start) + " ms");
			
			n *= 2;
		}
	}
	
	/**
	 * Load entries with more and more fields
	 * @throws JSONException 
	 */
	public void testLoadEntriesWithManyFields() throws JSONException {
		// Loading entries with 1,2,4,... fields
		int n = 1;
		for (int k=0; k < LOG2_MAX_NUMBER_FIELDS; k++) {
			n *= 2;
			
			JSONObject entry = new JSONObject();
			entry.put("key", "k" + n);
			for (int i=0; i<n-1; i++) {
				entry.put("v" + i, "value_" + i);
			}
			long start = System.currentTimeMillis();
			store.upsert(TEST_SOUP, entry);
			Log.i("SmartStoreLoadTest", "upserting entry with " + n + " fields,"
					+ " raw length: " +  entry.toString().length() + ", "
					+ " time taken: " + (System.currentTimeMillis() - start) + " ms");
		}
		// Validation
		n = 1;
		for (int k=0; k < LOG2_MAX_NUMBER_FIELDS; k++) {
			n *= 2;
			QuerySpec query = QuerySpec.buildExactQuerySpec("key", "k" + n, 1);
			long start = System.currentTimeMillis();
			JSONArray resultSet = store.querySoup(TEST_SOUP, query, 0);
			long end = System.currentTimeMillis();
			assertEquals("Should have returned one entry", 1, resultSet.length());
			JSONObject entry = resultSet.getJSONObject(0);
			Log.i("SmartStoreLoadTest", "querying entry with " + n + " fields,"
					+ " raw length: " +  entry.toString().length() + ", "
					+ " time taken: " + (end - start) + " ms");
			
			assertEquals("Wrong key", "k" + n, entry.getString("key"));
			for (int i=0; i<n-1; i++) {
				assertEquals("Wrong value", "value_" + i, entry.getString("v" + i));
			}
		}
	}
	
	/**
	 * Load entries with a key and a value - getting larger and larger
	 * @throws JSONException 
	 */
	public void testLoadEntriesWithLargeField() throws JSONException {
		// Loading entries with 1,2,4,... characters field
		int n = 1;
		for (int k=0; k <= LOG2_MAX_LENGTH_FIELD ; k++) {
			JSONObject entry = new JSONObject();
			entry.put("key", "k" + n);
			StringBuilder sb = new StringBuilder(n);
			for (int i=0; i<n; i++) {
				sb.append("x");
			}
			entry.put("value", sb.toString());

			long start = System.currentTimeMillis();
			store.upsert(TEST_SOUP, entry);
			Log.i("SmartStoreLoadTest", "upserting entry with " + n + " chars field, "
					+ " raw length: " +  entry.toString().length() + ", "
					+ " time taken: " + (System.currentTimeMillis() - start) + " ms");

			n *= 2;
		}

		// Validation
		n = 1;
		for (int k=0; k <= LOG2_MAX_LENGTH_FIELD; k++) {
			QuerySpec query = QuerySpec.buildExactQuerySpec("key", "k" + n, 1);
			long start = System.currentTimeMillis();
			JSONArray resultSet = store.querySoup(TEST_SOUP, query, 0);
			long end = System.currentTimeMillis();
			assertEquals("Should have returned one entry", 1, resultSet.length());
			JSONObject entry = resultSet.getJSONObject(0);
			Log.i("SmartStoreLoadTest", "querying entry with " + n + " chars field,"
					+ " raw length: " +  entry.toString().length() + ", "
					+ " time taken: " + (end - start) + " ms");
			
			assertEquals("Wrong key", "k" + n, entry.getString("key"));
			assertEquals("Wrong value", n, entry.getString("value").length());
			
			n *= 2;
		}
	}
	
}
