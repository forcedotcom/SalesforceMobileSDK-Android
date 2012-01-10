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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.database.Cursor;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;

import com.salesforce.androidsdk.store.SmartStore.IndexSpec;
import com.salesforce.androidsdk.store.SmartStore.Order;
import com.salesforce.androidsdk.store.SmartStore.QuerySpec;
import com.salesforce.androidsdk.store.SmartStore.Type;

/**
 * Abstract super class for plain and encrypted smart store tests
 *
 */
public abstract class AbstractSmartStoreTest extends InstrumentationTestCase {

	private static final String TEST_SOUP = "test_soup";
	private static final String OTHER_TEST_SOUP = "other_test_soup";
	
	protected Context targetContext;
	private Database db;
	private SmartStore store;
	
	@Override
	public void setUp() throws Exception {
		super.setUp();
		targetContext = getInstrumentation().getTargetContext();
		DBOperations.resetDatabase(targetContext); // start clean
		db = getWritableDatabase();
		store = new SmartStore(db);
		
		assertFalse("Table test_soup should not exist", hasTable(TEST_SOUP));
		store.registerSoup(TEST_SOUP, new IndexSpec[] {new IndexSpec("key", Type.TEXT)});
		assertTrue("Register soup call failed", hasTable(TEST_SOUP));
	}
	
	protected abstract Database getWritableDatabase();

	@Override
	protected void tearDown() throws Exception {
		db.close();
		// Not cleaning up after the test to make diagnosing issues easier
		super.tearDown();
	}


	/**
	 * Testing method with paths to top level string/integer/array/map as well as edge cases (null object/null or empty path)
	 * @throws JSONException 
	 */
	public void testProjectTopLevel() throws JSONException {
		JSONObject json = new JSONObject("{'a':'va', 'b':2, 'c':[0,1,2], 'd': {'d1':'vd1', 'd2':'vd2', 'd3':[1,2], 'd4':{'e':5}}}");

		// Null object
		assertNull("Should have been null", SmartStore.project(null, "path"));
		
		// Root
		assertEquals("Should have returned whole object", json, SmartStore.project(json, null));
		assertEquals("Should have returned whole object", json, SmartStore.project(json, ""));
		
		// Top-level elements
		assertEquals("Wrong value for key a", "va", SmartStore.project(json, "a"));
		assertEquals("Wrong value for key b", 2, SmartStore.project(json, "b"));
		assertEquals("Wrong value for key c", "[0,1,2]", ((JSONArray) SmartStore.project(json, "c")).toString());
		assertEquals("Wrong value for key d", new JSONObject("{'d1':'vd1','d2':'vd2','d3':[1,2],'d4':{'e':5}}").toString(), SmartStore.project(json, "d").toString());

		// With leading /
		assertEquals("Should have returned whole object", json, SmartStore.project(json, "/"));
		assertEquals("Wrong value for key /a", "va", SmartStore.project(json, "/a"));
		assertEquals("Wrong value for key /b", 2, SmartStore.project(json, "/b"));
		assertEquals("Wrong value for key /c", "[0,1,2]", ((JSONArray) SmartStore.project(json, "/c")).toString());
		assertEquals("Wrong value for key /d", new JSONObject("{'d1':'vd1','d2':'vd2','d3':[1,2],'d4':{'e':5}}").toString(), SmartStore.project(json, "/d").toString());
	}

	/**
	 * Testing method with paths to non-top level string/integer/array/map
	 * @throws JSONException 
	 */
	public void testProjectNested() throws JSONException {
		JSONObject json = new JSONObject("{'a':'va', 'b':2, 'c':[0,1,2], 'd': {'d1':'vd1', 'd2':'vd2', 'd3':[1,2], 'd4':{'e':5}}}");
		
		// Nested elements
		assertEquals("Wrong value for key d/d1", "vd1", SmartStore.project(json, "d/d1"));
		assertEquals("Wrong value for key d/d2", "vd2", SmartStore.project(json, "d/d2"));
		assertEquals("Wrong value for key d/d3", "[1,2]", ((JSONArray) SmartStore.project(json, "d/d3")).toString());
		assertEquals("Wrong value for key d/d4", new JSONObject("{'e':5}").toString(), SmartStore.project(json, "d/d4").toString());
		assertEquals("Wrong value for key d/d4/e", 5, SmartStore.project(json, "d/d4/e"));
		
		// With leading /
		assertEquals("Wrong value for key /d/d1", "vd1", SmartStore.project(json, "/d/d1"));
		assertEquals("Wrong value for key /d/d2", "vd2", SmartStore.project(json, "/d/d2"));
		assertEquals("Wrong value for key /d/d3", "[1,2]", ((JSONArray) SmartStore.project(json, "/d/d3")).toString());
		assertEquals("Wrong value for key /d/d4", new JSONObject("{'e':5}").toString(), SmartStore.project(json, "/d/d4").toString());
		assertEquals("Wrong value for key /d/d4/e", 5, SmartStore.project(json, "/d/d4/e"));
	}

	/**
	 * Check that the meta data table (soup index map) has been created
	 */
	public void testMetaDataTableCreated() {
		assertTrue("Table soup_index_map not found", hasTable("soup_index_map"));
	}

	/**
	 * Basic create test (create a single element with a single index pointing to a top level attribute)
	 * @throws JSONException 
	 */
	public void testCreateOne() throws JSONException {
		JSONObject soupElt = new JSONObject("{'key':'ka', 'value':'va'}");
		long soupEntryId = store.create(TEST_SOUP, soupElt);
		
		// Check DB
		Cursor c = null;
		try {
			c = db.query(TEST_SOUP, null, null, null);
			assertTrue("Expected a soup element", c.moveToFirst());
			assertEquals("Expected one soup element only", 1, c.getCount());
			assertEquals("Wrong id", soupEntryId, c.getLong(c.getColumnIndex("id")));
			assertEquals("Wrong value in index column", "ka", c.getString(c.getColumnIndex(TEST_SOUP + "_0")));
			assertEquals("Wrong value in soup column", soupElt.toString(), c.getString(c.getColumnIndex("soup")));
			assertEquals("Created date and last modified date should be equal", c.getLong(c.getColumnIndex("created")),  c.getLong(c.getColumnIndex("lastModified")));
		}
		finally {
			safeClose(c);
		}
	}
	
	/**
	 * More complex create test (create multiple elements with multiple indices not just pointing to top level attributes) 
	 * @throws JSONException 
	 */
	public void testCreateMultiple() throws JSONException {
		try {
			assertFalse("Table test_soup should not exist", hasTable(OTHER_TEST_SOUP));
			store.registerSoup(OTHER_TEST_SOUP, new IndexSpec[] {new IndexSpec("lastName", Type.TEXT), new IndexSpec("address/city", Type.TEXT)});
			assertTrue("Register soup call failed", hasTable(OTHER_TEST_SOUP));
			
			JSONObject soupElt1 = new JSONObject("{'lastName':'Doe', 'address':{'street':'1 market', 'city':'San Francisco'}}");
			JSONObject soupElt2 = new JSONObject("{'lastName':'Jackson', 'address':{'street':'100 mission', 'city':'Los Angeles'}}");
			JSONObject soupElt3 = new JSONObject("{'lastName':'Watson', 'address':{'street':'50 market', 'city':'London'}}");
			
			long soupEntryId1 = store.create(OTHER_TEST_SOUP, soupElt1);
			long soupEntryId2 = store.create(OTHER_TEST_SOUP, soupElt2);
			long soupEntryId3 = store.create(OTHER_TEST_SOUP, soupElt3);
	
			// Check DB
			Cursor c = null;
			try {
				c = db.query(OTHER_TEST_SOUP, null, null, "id ASC");
				assertTrue("Expected a soup element", c.moveToFirst());
				assertEquals("Expected three soup elements", 3, c.getCount());
				
				assertEquals("Wrong id", soupEntryId1, c.getLong(c.getColumnIndex("id")));
				assertEquals("Wrong value in index column", "Doe", c.getString(c.getColumnIndex(OTHER_TEST_SOUP + "_0")));
				assertEquals("Wrong value in index column", "San Francisco", c.getString(c.getColumnIndex(OTHER_TEST_SOUP + "_1")));
				assertEquals("Wrong value in soup column", soupElt1.toString(), c.getString(c.getColumnIndex("soup")));
				
				c.moveToNext();
				assertEquals("Wrong id", soupEntryId2, c.getLong(c.getColumnIndex("id")));
				assertEquals("Wrong value in index column", "Jackson", c.getString(c.getColumnIndex(OTHER_TEST_SOUP + "_0")));
				assertEquals("Wrong value in index column", "Los Angeles", c.getString(c.getColumnIndex(OTHER_TEST_SOUP + "_1")));
				assertEquals("Wrong value in soup column", soupElt2.toString(), c.getString(c.getColumnIndex("soup")));
	
				c.moveToNext();
				assertEquals("Wrong id", soupEntryId3, c.getLong(c.getColumnIndex("id")));
				assertEquals("Wrong value in index column", "Watson", c.getString(c.getColumnIndex(OTHER_TEST_SOUP + "_0")));
				assertEquals("Wrong value in index column", "London", c.getString(c.getColumnIndex(OTHER_TEST_SOUP + "_1")));
				assertEquals("Wrong value in soup column", soupElt3.toString(), c.getString(c.getColumnIndex("soup")));
				
			}
			finally {
				safeClose(c);
			}
		}
		finally {
			store.dropSoup(OTHER_TEST_SOUP);
			assertFalse("Drop soup call failed", hasTable(OTHER_TEST_SOUP));
		}
	}
	
	/**
	 * Update test (create multiple soup elements and update one of them, check them all)
	 * @throws JSONException 
	 */
	public void testUpdate() throws JSONException {
		JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'ka2', 'value':'va2'}");
		JSONObject soupElt3 = new JSONObject("{'key':'ka3', 'value':'va3'}");
		
		long soupEntryId1 = store.create(TEST_SOUP, soupElt1);
		long soupEntryId2 = store.create(TEST_SOUP, soupElt2);
		long soupEntryId3 = store.create(TEST_SOUP, soupElt3);

		SystemClock.sleep(10); // to get a different last modified date
		JSONObject soupElt2updated = new JSONObject("{'key':'ka2u', 'value':'va2u'}");
		store.update(TEST_SOUP, soupElt2updated, soupEntryId2);
		
		JSONObject soupEltRetrieved1 = store.retrieve(TEST_SOUP, soupEntryId1);
		JSONObject soupEltRetrieved2 = store.retrieve(TEST_SOUP, soupEntryId2);
		JSONObject soupEltRetrieved3 = store.retrieve(TEST_SOUP, soupEntryId3);

		assertEquals("Retrieve mismatch", soupElt1.put(SmartStore.SOUP_ENTRY_ID, soupEntryId1).toString(), soupEltRetrieved1.toString());
		assertEquals("Retrieve mismatch", soupElt2updated.put(SmartStore.SOUP_ENTRY_ID, soupEntryId2).toString(), soupEltRetrieved2.toString());
		assertEquals("Retrieve mismatch", soupElt3.put(SmartStore.SOUP_ENTRY_ID, soupEntryId3).toString(), soupEltRetrieved3.toString());

		// Check DB
		Cursor c = null;
		try {
			c = db.query(TEST_SOUP, null, null, "id ASC");
			assertTrue("Expected a soup element", c.moveToFirst());
			assertEquals("Expected three soup elements", 3, c.getCount());
			
			assertEquals("Wrong id", soupEntryId1, c.getLong(c.getColumnIndex("id")));
			assertEquals("Created date and last modified date should be equal", c.getLong(c.getColumnIndex("created")),  c.getLong(c.getColumnIndex("lastModified")));				
			
			c.moveToNext();
			assertEquals("Wrong id", soupEntryId2, c.getLong(c.getColumnIndex("id")));
			assertTrue("Last modified date should be more recent than created date", c.getLong(c.getColumnIndex("created")) < c.getLong(c.getColumnIndex("lastModified")));				

			c.moveToNext();
			assertEquals("Wrong id", soupEntryId3, c.getLong(c.getColumnIndex("id")));
			assertEquals("Created date and last modified date should be equal", c.getLong(c.getColumnIndex("created")),  c.getLong(c.getColumnIndex("lastModified")));				
		}
		finally {
			safeClose(c);
		}
	}
	
	/**
	 * Upsert test (use upsert to create and update soup elements) 
	 * @throws JSONException
	 */
	public void testUpsert() throws JSONException {
		JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'ka2', 'value':'va2'}");
		JSONObject soupElt3 = new JSONObject("{'key':'ka3', 'value':'va3'}");
		
		long soupEntryId1 = store.upsert(TEST_SOUP, soupElt1);
		long soupEntryId2 = store.upsert(TEST_SOUP, soupElt2);
		long soupEntryId3 = store.upsert(TEST_SOUP, soupElt3);

		SystemClock.sleep(10); // to get a different last modified date
		JSONObject soupElt2updated = new JSONObject("{'key':'ka2u', 'value':'va2u', '_soupEntryId': " + soupEntryId2 + "}");
		store.upsert(TEST_SOUP, soupElt2updated);
		
		JSONObject soupEltRetrieved1 = store.retrieve(TEST_SOUP, soupEntryId1);
		JSONObject soupEltRetrieved2 = store.retrieve(TEST_SOUP, soupEntryId2);
		JSONObject soupEltRetrieved3 = store.retrieve(TEST_SOUP, soupEntryId3);

		assertEquals("Retrieve mismatch", soupElt1.put(SmartStore.SOUP_ENTRY_ID, soupEntryId1).toString(), soupEltRetrieved1.toString());
		assertEquals("Retrieve mismatch", soupElt2updated.put(SmartStore.SOUP_ENTRY_ID, soupEntryId2).toString(), soupEltRetrieved2.toString());
		assertEquals("Retrieve mismatch", soupElt3.put(SmartStore.SOUP_ENTRY_ID, soupEntryId3).toString(), soupEltRetrieved3.toString());
		
		// Check DB
		Cursor c = null;
		try {
			c = db.query(TEST_SOUP, null, null, "id ASC");
			assertTrue("Expected a soup element", c.moveToFirst());
			assertEquals("Expected three soup elements", 3, c.getCount());
			
			assertEquals("Wrong id", soupEntryId1, c.getLong(c.getColumnIndex("id")));
			assertEquals("Created date and last modified date should be equal", c.getLong(c.getColumnIndex("created")),  c.getLong(c.getColumnIndex("lastModified")));				
			
			c.moveToNext();
			assertEquals("Wrong id", soupEntryId2, c.getLong(c.getColumnIndex("id")));
			assertTrue("Last modified date should be more recent than created date", c.getLong(c.getColumnIndex("created")) < c.getLong(c.getColumnIndex("lastModified")));				

			c.moveToNext();
			assertEquals("Wrong id", soupEntryId3, c.getLong(c.getColumnIndex("id")));
			assertEquals("Created date and last modified date should be equal", c.getLong(c.getColumnIndex("created")),  c.getLong(c.getColumnIndex("lastModified")));				
		}
		finally {
			safeClose(c);
		}
	}

	/**
	 * Call register soup, then create multiple soup elements, update one of them and do retrieve and direct SQL for validation
	 * @throws JSONException 
	 */
	public void testRetrieve() throws JSONException {
		JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'ka2', 'value':'va2'}");
		JSONObject soupElt3 = new JSONObject("{'key':'ka3', 'value':'va3'}");
		
		long soupEntryId1 = store.create(TEST_SOUP, soupElt1);
		long soupEntryId2 = store.create(TEST_SOUP, soupElt2);
		long soupEntryId3 = store.create(TEST_SOUP, soupElt3);
		
		JSONObject soupEltRetrieved1 = store.retrieve(TEST_SOUP, soupEntryId1);
		JSONObject soupEltRetrieved2 = store.retrieve(TEST_SOUP, soupEntryId2);
		JSONObject soupEltRetrieved3 = store.retrieve(TEST_SOUP, soupEntryId3);
		
		assertEquals("Retrieve mismatch", soupElt1.put(SmartStore.SOUP_ENTRY_ID, soupEntryId1).toString(), soupEltRetrieved1.toString());
		assertEquals("Retrieve mismatch", soupElt2.put(SmartStore.SOUP_ENTRY_ID, soupEntryId2).toString(), soupEltRetrieved2.toString());
		assertEquals("Retrieve mismatch", soupElt3.put(SmartStore.SOUP_ENTRY_ID, soupEntryId3).toString(), soupEltRetrieved3.toString());
	}

	/**
	 * Call register soup, then create a soup element, deletes and check database directly that it is in fact gone.
	 * @throws JSONException 
	 */
	public void testDelete() throws JSONException {
		JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'ka2', 'value':'va2'}");
		JSONObject soupElt3 = new JSONObject("{'key':'ka3', 'value':'va3'}");
		
		long soupEntryId1 = store.create(TEST_SOUP, soupElt1);
		long soupEntryId2 = store.create(TEST_SOUP, soupElt2);
		long soupEntryId3 = store.create(TEST_SOUP, soupElt3);
		
		store.delete(TEST_SOUP, soupEntryId2);

		JSONObject soupEltRetrieved1 = store.retrieve(TEST_SOUP, soupEntryId1);
		JSONObject soupEltRetrieved2 = store.retrieve(TEST_SOUP, soupEntryId2);
		JSONObject soupEltRetrieved3 = store.retrieve(TEST_SOUP, soupEntryId3);
		
		assertEquals("Retrieve mismatch", soupElt1.put(SmartStore.SOUP_ENTRY_ID, soupEntryId1).toString(), soupEltRetrieved1.toString());
		assertNull("Should be null", soupEltRetrieved2);
		assertEquals("Retrieve mismatch", soupElt3.put(SmartStore.SOUP_ENTRY_ID, soupEntryId3).toString(), soupEltRetrieved3.toString());
		
		// Check DB
		Cursor c = null;
		try {
			c = db.query(TEST_SOUP, null, null, "id ASC");
			assertTrue("Expected a soup element", c.moveToFirst());
			assertEquals("Expected three soup elements", 2, c.getCount());
			
			assertEquals("Wrong id", soupEntryId1, c.getLong(c.getColumnIndex("id")));
			
			c.moveToNext();
			assertEquals("Wrong id", soupEntryId3, c.getLong(c.getColumnIndex("id")));
		}
		finally {
			safeClose(c);
		}
	}

	/**
	 * Test query when looking for a specific element (with and without projections)
	 * @throws JSONException 
	 */
	public void testMatchQuery() throws JSONException {
		JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1', 'otherValue':'ova1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'ka2', 'value':'va2', 'otherValue':'ova2'}");
		JSONObject soupElt3 = new JSONObject("{'key':'ka3', 'value':'va3', 'otherValue':'ova3'}");
		
		/*long soupEntryId1 = */store.create(TEST_SOUP, soupElt1);
		long soupEntryId2 = store.create(TEST_SOUP, soupElt2);
		/*long soupEntryId3 = */store.create(TEST_SOUP, soupElt3);

		// Exact match - whole soup element
		JSONArray result = store.querySoup(TEST_SOUP, new QuerySpec("key", "ka2"));
		assertEquals("One result expected", 1, result.length());
		assertEquals("Wrong result for query", soupElt2.put(SmartStore.SOUP_ENTRY_ID, soupEntryId2).toString(), result.getJSONObject(0).toString());

		// Exact match - specified projections
		result = store.querySoup(TEST_SOUP, new QuerySpec("key", "ka2", new String[] {"otherValue", "value"}));
		assertEquals("One result expected", 1, result.length());
		assertEquals("Wrong result for query", new JSONObject("{'_soupEntryId':" + soupEntryId2 + ",'otherValue':'ova2', 'value':'va2'}").toString(), result.getJSONObject(0).toString());
		
	}

	/**
	 * Query test looking for a range of elements (with and without projections / with ascending or descending ordering)
	 * @throws JSONException 
	 */
	public void testRangeQuery() throws JSONException {
		JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1', 'otherValue':'ova1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'ka2', 'value':'va2', 'otherValue':'ova2'}");
		JSONObject soupElt3 = new JSONObject("{'key':'ka3', 'value':'va3', 'otherValue':'ova3'}");
		
		/*long soupEntryId1 = */store.create(TEST_SOUP, soupElt1);
		long soupEntryId2 = store.create(TEST_SOUP, soupElt2);
		long soupEntryId3 = store.create(TEST_SOUP, soupElt3);

		// Range query - whole soup elements
		JSONArray result = store.querySoup(TEST_SOUP, new QuerySpec("key", "ka2", "ka3"));
		assertEquals("Two results expected", 2, result.length());
		assertEquals("Wrong result for query", soupElt2.put(SmartStore.SOUP_ENTRY_ID, soupEntryId2).toString(), result.getJSONObject(0).toString());
		assertEquals("Wrong result for query", soupElt3.put(SmartStore.SOUP_ENTRY_ID, soupEntryId3).toString(), result.getJSONObject(1).toString());

		// Range query - whole soup elements - descending order
		result = store.querySoup(TEST_SOUP, new QuerySpec("key", "ka2", "ka3", Order.DESC));
		assertEquals("Two results expected", 2, result.length());
		assertEquals("Wrong result for query", soupElt3.put(SmartStore.SOUP_ENTRY_ID, soupEntryId3).toString(), result.getJSONObject(0).toString());
		assertEquals("Wrong result for query", soupElt2.put(SmartStore.SOUP_ENTRY_ID, soupEntryId2).toString(), result.getJSONObject(1).toString());
		
		// Range query - specified projections
		result = store.querySoup(TEST_SOUP, new QuerySpec("key", "ka2", "ka3", new String[] {"otherValue", "value"}));
		assertEquals("Two results expected", 2, result.length());
		assertEquals("Wrong result for query", new JSONObject("{'_soupEntryId': " + soupEntryId2 + ", 'otherValue':'ova2', 'value':'va2'}").toString(), result.getJSONObject(0).toString());
		assertEquals("Wrong result for query", new JSONObject("{'_soupEntryId': " + soupEntryId3 + ", 'otherValue':'ova3', 'value':'va3'}").toString(), result.getJSONObject(1).toString());
		
		// Range query - specified projections - descending order
		result = store.querySoup(TEST_SOUP, new QuerySpec("key", "ka2", "ka3", new String[] {"otherValue", "value"}, Order.DESC));
		assertEquals("Two results expected", 2, result.length());
		assertEquals("Wrong result for query", new JSONObject("{'_soupEntryId': " + soupEntryId3 + ", 'otherValue':'ova3', 'value':'va3'}").toString(), result.getJSONObject(0).toString());
		assertEquals("Wrong result for query", new JSONObject("{'_soupEntryId': " + soupEntryId2 + ", 'otherValue':'ova2', 'value':'va2'}").toString(), result.getJSONObject(1).toString());
	}
	
	
	/**
	 * Helper method to check that a table exists in the database
	 * @param tableName
	 * @return
	 */
	private boolean hasTable(String tableName) {
		Cursor c = null;
		try {
			c = db.query("sqlite_master", null, "type = 'table' and name = '" + tableName + "'", null);
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
	private void safeClose(Cursor c) {
		if (c != null) {
			c.close();
		}
	}
	
	
}