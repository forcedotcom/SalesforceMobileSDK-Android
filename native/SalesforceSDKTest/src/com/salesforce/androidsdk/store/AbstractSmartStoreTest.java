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
	private static final String THIRD_TEST_SOUP = "third_test_soup";
	
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
		assertFalse("Soup test_soup should not exist", store.hasSoup(TEST_SOUP));
		store.registerSoup(TEST_SOUP, new IndexSpec[] {new IndexSpec("key", Type.string)});
		assertTrue("Table test_soup should now exist", hasTable(TEST_SOUP));
		assertTrue("Soup test_soup should now exist", store.hasSoup(TEST_SOUP));
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
		assertSameJSON("Should have returned whole object", json, SmartStore.project(json, null));
		assertSameJSON("Should have returned whole object", json, SmartStore.project(json, ""));
		
		// Top-level elements
		assertEquals("Wrong value for key a", "va", SmartStore.project(json, "a"));
		assertEquals("Wrong value for key b", 2, SmartStore.project(json, "b"));
		assertSameJSON("Wrong value for key c", new JSONArray("[0,1,2]"), SmartStore.project(json, "c"));
		assertSameJSON("Wrong value for key d", new JSONObject("{'d1':'vd1','d2':'vd2','d3':[1,2],'d4':{'e':5}}"), (JSONObject) SmartStore.project(json, "d"));

		// With leading /
		assertSameJSON("Should have returned whole object", json, SmartStore.project(json, "/"));
		assertEquals("Wrong value for key /a", "va", SmartStore.project(json, "/a"));
		assertEquals("Wrong value for key /b", 2, SmartStore.project(json, "/b"));
		assertSameJSON("Wrong value for key /c", new JSONArray("[0,1,2]"), SmartStore.project(json, "/c"));
		assertSameJSON("Wrong value for key /d", new JSONObject("{'d1':'vd1','d2':'vd2','d3':[1,2],'d4':{'e':5}}"), SmartStore.project(json, "/d"));
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
		assertSameJSON("Wrong value for key d/d3", new JSONArray("[1,2]"), SmartStore.project(json, "d/d3"));
		assertSameJSON("Wrong value for key d/d4", new JSONObject("{'e':5}"), SmartStore.project(json, "d/d4"));
		assertEquals("Wrong value for key d/d4/e", 5, SmartStore.project(json, "d/d4/e"));
		
		// With leading /
		assertEquals("Wrong value for key /d/d1", "vd1", SmartStore.project(json, "/d/d1"));
		assertEquals("Wrong value for key /d/d2", "vd2", SmartStore.project(json, "/d/d2"));
		assertSameJSON("Wrong value for key /d/d3", new JSONArray("[1,2]"), SmartStore.project(json, "/d/d3"));
		assertSameJSON("Wrong value for key /d/d4", new JSONObject("{'e':5}"), SmartStore.project(json, "/d/d4"));
		assertEquals("Wrong value for key /d/d4/e", 5, SmartStore.project(json, "/d/d4/e"));
	}

	/**
	 * Check that the meta data table (soup index map) has been created
	 */
	public void testMetaDataTableCreated() {
		assertTrue("Table soup_index_map not found", hasTable("soup_index_map"));
	}

	/**
	 * Test register/drop soup
	 */
	public void testRegisterDropSoup() {
		assertFalse("Table third_test_soup should not exist", hasTable(THIRD_TEST_SOUP));
		store.registerSoup(THIRD_TEST_SOUP, new IndexSpec[] {new IndexSpec("key", Type.string), new IndexSpec("value", Type.string)}); 
		assertTrue("Register soup call failed", hasTable(THIRD_TEST_SOUP));
		store.dropSoup(THIRD_TEST_SOUP);
		assertFalse("Table third_test_soup should no longer exist", hasTable(THIRD_TEST_SOUP));		
	}
	
	/**
	 * Testing create: create a single element with a single index pointing to a top level attribute
	 * @throws JSONException 
	 */
	public void testCreateOne() throws JSONException {
		JSONObject soupElt = new JSONObject("{'key':'ka', 'value':'va'}");
		JSONObject soupEltCreated = store.create(TEST_SOUP, soupElt);
		
		assertSameJSON("Wrong created soup element returned", soupElt, removeExtraFields(soupEltCreated));		
		
		// Check DB
		Cursor c = null;
		try {
			c = db.query(TEST_SOUP, null, null, null, null);
			assertTrue("Expected a soup element", c.moveToFirst());
			assertEquals("Expected one soup element only", 1, c.getCount());
			assertEquals("Wrong id", idOf(soupEltCreated), c.getLong(c.getColumnIndex("id")));
			assertEquals("Wrong created date", soupEltCreated.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
			assertEquals("Wrong value in index column", "ka", c.getString(c.getColumnIndex(TEST_SOUP + "_0")));
			assertSameJSON("Wrong value in soup column", soupEltCreated, new JSONObject(c.getString(c.getColumnIndex("soup"))));
			assertEquals("Created date and last modified date should be equal", c.getLong(c.getColumnIndex("created")),  c.getLong(c.getColumnIndex("lastModified")));
		}
		finally {
			safeClose(c);
		}
	}

	/**
	 * Testing create: create multiple elements with multiple indices not just pointing to top level attributes 
	 * @throws JSONException 
	 */
	public void testCreateMultiple() throws JSONException {
		assertFalse("Table test_soup should not exist", hasTable(OTHER_TEST_SOUP));
		store.registerSoup(OTHER_TEST_SOUP, new IndexSpec[] {new IndexSpec("lastName", Type.string), new IndexSpec("address/city", Type.string)});
		assertTrue("Register soup call failed", hasTable(OTHER_TEST_SOUP));
		
		JSONObject soupElt1 = new JSONObject("{'lastName':'Doe', 'address':{'city':'San Francisco','street':'1 market'}}");
		JSONObject soupElt2 = new JSONObject("{'lastName':'Jackson', 'address':{'city':'Los Angeles','street':'100 mission'}}");
		JSONObject soupElt3 = new JSONObject("{'lastName':'Watson', 'address':{'city':'London','street':'50 market'}}");
		
		JSONObject soupElt1Created = store.create(OTHER_TEST_SOUP, soupElt1);
		JSONObject soupElt2Created = store.create(OTHER_TEST_SOUP, soupElt2);
		JSONObject soupElt3Created = store.create(OTHER_TEST_SOUP, soupElt3);
		
		assertSameJSON("Wrong created soup element returned", soupElt1, removeExtraFields(soupElt1Created));
		assertSameJSON("Wrong created soup element returned", soupElt2, removeExtraFields(soupElt2Created));
		assertSameJSON("Wrong created soup element returned", soupElt3, removeExtraFields(soupElt3Created));
		
		// Check DB
		Cursor c = null;
		try {
			c = db.query(OTHER_TEST_SOUP, null, "id ASC", null, null);
			assertTrue("Expected a soup element", c.moveToFirst());
			assertEquals("Expected three soup elements", 3, c.getCount());
			
			assertEquals("Wrong id", idOf(soupElt1Created), c.getLong(c.getColumnIndex("id")));
			assertEquals("Wrong created date", soupElt1Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
			assertEquals("Wrong value in index column", "Doe", c.getString(c.getColumnIndex(OTHER_TEST_SOUP + "_0")));
			assertEquals("Wrong value in index column", "San Francisco", c.getString(c.getColumnIndex(OTHER_TEST_SOUP + "_1")));
			assertSameJSON("Wrong value in soup column", soupElt1Created, new JSONObject(c.getString(c.getColumnIndex("soup"))));
			
			c.moveToNext();
			assertEquals("Wrong id", idOf(soupElt2Created), c.getLong(c.getColumnIndex("id")));
			assertEquals("Wrong created date", soupElt2Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
			assertEquals("Wrong value in index column", "Jackson", c.getString(c.getColumnIndex(OTHER_TEST_SOUP + "_0")));
			assertEquals("Wrong value in index column", "Los Angeles", c.getString(c.getColumnIndex(OTHER_TEST_SOUP + "_1")));
			assertSameJSON("Wrong value in soup column", soupElt2Created, new JSONObject(c.getString(c.getColumnIndex("soup"))));

			c.moveToNext();
			assertEquals("Wrong id", idOf(soupElt3Created), c.getLong(c.getColumnIndex("id")));
			assertEquals("Wrong created date", soupElt3Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
			assertEquals("Wrong value in index column", "Watson", c.getString(c.getColumnIndex(OTHER_TEST_SOUP + "_0")));
			assertEquals("Wrong value in index column", "London", c.getString(c.getColumnIndex(OTHER_TEST_SOUP + "_1")));
			assertSameJSON("Wrong value in soup column", soupElt3Created, new JSONObject(c.getString(c.getColumnIndex("soup"))));
			
		}
		finally {
			safeClose(c);
		}
	}
	
	/**
	 * Testing update: create multiple soup elements and update one of them, check them all
	 * @throws JSONException 
	 */
	public void testUpdate() throws JSONException {
		JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'ka2', 'value':'va2'}");
		JSONObject soupElt3 = new JSONObject("{'key':'ka3', 'value':'va3'}");
		
		JSONObject soupElt1Created = store.create(TEST_SOUP, soupElt1);
		JSONObject soupElt2Created = store.create(TEST_SOUP, soupElt2);
		JSONObject soupElt3Created = store.create(TEST_SOUP, soupElt3);
		
		SystemClock.sleep(10); // to get a different last modified date
		JSONObject soupElt2ForUpdate = new JSONObject("{'key':'ka2u', 'value':'va2u'}");
		JSONObject soupElt2Updated = store.update(TEST_SOUP, soupElt2ForUpdate, idOf(soupElt2Created));
		
		JSONObject soupElt1Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt1Created));
		JSONObject soupElt2Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt2Created));
		JSONObject soupElt3Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt3Created));

		assertSameJSON("Retrieve mismatch", soupElt1Created, soupElt1Retrieved);
		assertSameJSON("Retrieve mismatch", soupElt2Updated, soupElt2Retrieved);
		assertSameJSON("Retrieve mismatch", soupElt3Created, soupElt3Retrieved);

		// Check DB
		Cursor c = null;
		try {
			c = db.query(TEST_SOUP, null, "id ASC", null, null);
			assertTrue("Expected a soup element", c.moveToFirst());
			assertEquals("Expected three soup elements", 3, c.getCount());
			
			assertEquals("Wrong id", idOf(soupElt1Created), c.getLong(c.getColumnIndex("id")));
			assertEquals("Wrong created date", soupElt1Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
			assertEquals("Created date and last modified date should be equal", c.getLong(c.getColumnIndex("created")),  c.getLong(c.getColumnIndex("lastModified")));				
			
			c.moveToNext();
			assertEquals("Wrong id", idOf(soupElt2Created), c.getLong(c.getColumnIndex("id")));
			assertEquals("Wrong created date", soupElt2Updated.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
			assertTrue("Last modified date should be more recent than created date", c.getLong(c.getColumnIndex("created")) < c.getLong(c.getColumnIndex("lastModified")));				

			c.moveToNext();
			assertEquals("Wrong id", idOf(soupElt3Created), c.getLong(c.getColumnIndex("id")));
			assertEquals("Wrong created date", soupElt3Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
			assertEquals("Created date and last modified date should be equal", c.getLong(c.getColumnIndex("created")),  c.getLong(c.getColumnIndex("lastModified")));				
		}
		finally {
			safeClose(c);
		}
	}
	
	/**
	 * Testing upsert: upsert multiple soup elements and re-upsert one of them, check them all
	 * @throws JSONException
	 */
	public void testUpsert() throws JSONException {
		JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'ka2', 'value':'va2'}");
		JSONObject soupElt3 = new JSONObject("{'key':'ka3', 'value':'va3'}");
		
		JSONObject soupElt1Upserted = store.upsert(TEST_SOUP, soupElt1);
		JSONObject soupElt2Upserted = store.upsert(TEST_SOUP, soupElt2);
		JSONObject soupElt3Upserted = store.upsert(TEST_SOUP, soupElt3);

		SystemClock.sleep(10); // to get a different last modified date
		JSONObject soupElt2ForUpdate = new JSONObject("{'key':'ka2u', 'value':'va2u', '_soupEntryId': " + idOf(soupElt2Upserted) + "}");
		JSONObject soupElt2Updated = store.upsert(TEST_SOUP, soupElt2ForUpdate);
		
		JSONObject soupElt1Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt1Upserted));
		JSONObject soupElt2Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt2Upserted));
		JSONObject soupElt3Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt3Upserted));

		assertSameJSON("Retrieve mismatch", soupElt1Upserted, soupElt1Retrieved);
		assertSameJSON("Retrieve mismatch", soupElt2Updated, soupElt2Retrieved);
		assertSameJSON("Retrieve mismatch", soupElt3Upserted, soupElt3Retrieved);
		
		// Check DB
		Cursor c = null;
		try {
			c = db.query(TEST_SOUP, null, "id ASC", null, null);
			assertTrue("Expected a soup element", c.moveToFirst());
			assertEquals("Expected three soup elements", 3, c.getCount());
			
			assertEquals("Wrong id", idOf(soupElt1Upserted), c.getLong(c.getColumnIndex("id")));
			assertEquals("Wrong created date", soupElt1Upserted.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
			assertEquals("Created date and last modified date should be equal", c.getLong(c.getColumnIndex("created")),  c.getLong(c.getColumnIndex("lastModified")));				
			
			c.moveToNext();
			assertEquals("Wrong id", idOf(soupElt2Upserted), c.getLong(c.getColumnIndex("id")));
			assertEquals("Wrong created date", soupElt2Updated.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
			assertTrue("Last modified date should be more recent than created date", c.getLong(c.getColumnIndex("created")) < c.getLong(c.getColumnIndex("lastModified")));				

			c.moveToNext();
			assertEquals("Wrong id", idOf(soupElt3Upserted), c.getLong(c.getColumnIndex("id")));
			assertEquals("Wrong created date", soupElt3Upserted.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
			assertEquals("Created date and last modified date should be equal", c.getLong(c.getColumnIndex("created")),  c.getLong(c.getColumnIndex("lastModified")));				
		}
		finally {
			safeClose(c);
		}
	}

	/**
	 * Testing retrieve: create multiple soup elements and retrieves them back
	 * @throws JSONException 
	 */
	public void testRetrieve() throws JSONException {
		JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'ka2', 'value':'va2'}");
		JSONObject soupElt3 = new JSONObject("{'key':'ka3', 'value':'va3'}");
		
		JSONObject soupElt1Created = store.create(TEST_SOUP, soupElt1);
		JSONObject soupElt2Created = store.create(TEST_SOUP, soupElt2);
		JSONObject soupElt3Created = store.create(TEST_SOUP, soupElt3);
		
		JSONObject soupElt1Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt1Created));
		JSONObject soupElt2Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt2Created));
		JSONObject soupElt3Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt3Created));

		assertSameJSON("Retrieve mismatch", soupElt1Created, soupElt1Retrieved);
		assertSameJSON("Retrieve mismatch", soupElt2Created, soupElt2Retrieved);
		assertSameJSON("Retrieve mismatch", soupElt3Created, soupElt3Retrieved);
	}

	/**
	 * Testing delete: create a soup element, deletes and check database directly that it is in fact gone
	 * @throws JSONException 
	 */
	public void testDelete() throws JSONException {
		JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'ka2', 'value':'va2'}");
		JSONObject soupElt3 = new JSONObject("{'key':'ka3', 'value':'va3'}");
		
		JSONObject soupElt1Created = store.create(TEST_SOUP, soupElt1);
		JSONObject soupElt2Created = store.create(TEST_SOUP, soupElt2);
		JSONObject soupElt3Created = store.create(TEST_SOUP, soupElt3);
		
		store.delete(TEST_SOUP, idOf(soupElt2Created));

		JSONObject soupElt1Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt1Created));
		JSONObject soupElt2Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt2Created));
		JSONObject soupElt3Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt3Created));

		assertSameJSON("Retrieve mismatch", soupElt1Created, soupElt1Retrieved);
		assertNull("Should be null", soupElt2Retrieved);
		assertSameJSON("Retrieve mismatch", soupElt3Created, soupElt3Retrieved);
		
		// Check DB
		Cursor c = null;
		try {
			c = db.query(TEST_SOUP, null, "id ASC", null, null);
			assertTrue("Expected a soup element", c.moveToFirst());
			assertEquals("Expected three soup elements", 2, c.getCount());
			
			assertEquals("Wrong id", idOf(soupElt1Created), c.getLong(c.getColumnIndex("id")));
			
			c.moveToNext();
			assertEquals("Wrong id", idOf(soupElt3Created), c.getLong(c.getColumnIndex("id")));
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
		
		store.create(TEST_SOUP, soupElt1);
		JSONObject soupElt2Created= store.create(TEST_SOUP, soupElt2);
		store.create(TEST_SOUP, soupElt3);

		// Exact match - whole soup element
		JSONArray result = store.querySoup(TEST_SOUP, new QuerySpec("key", "ka2"), 0);
		assertEquals("One result expected", 1, result.length());
		assertSameJSON("Wrong result for query", soupElt2Created, result.getJSONObject(0));

		// Exact match - specified projections
		result = store.querySoup(TEST_SOUP, new QuerySpec("key", "ka2", new String[] {"otherValue", "value"}), 0);
		assertEquals("One result expected", 1, result.length());
		assertSameJSON("Wrong result for query", new JSONObject("{'otherValue':'ova2', 'value':'va2'}"), result.getJSONObject(0));
		
	}

	/**
	 * Query test looking for a range of elements (with and without projections / with ascending or descending ordering)
	 * @throws JSONException 
	 */
	public void testRangeQuery() throws JSONException {
		JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1', 'otherValue':'ova1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'ka2', 'value':'va2', 'otherValue':'ova2'}");
		JSONObject soupElt3 = new JSONObject("{'key':'ka3', 'value':'va3', 'otherValue':'ova3'}");
		
		/*JSONObjectsoupElt1Created = */store.create(TEST_SOUP, soupElt1);
		JSONObject soupElt2Created = store.create(TEST_SOUP, soupElt2);
		JSONObject soupElt3Created = store.create(TEST_SOUP, soupElt3);

		// Range query - whole soup elements
		JSONArray result = store.querySoup(TEST_SOUP, new QuerySpec("key", "ka2", "ka3"), 0);
		assertEquals("Two results expected", 2, result.length());
		assertSameJSON("Wrong result for query", soupElt2Created, result.getJSONObject(0));
		assertSameJSON("Wrong result for query", soupElt3Created, result.getJSONObject(1));

		// Range query - whole soup elements - descending order
		result = store.querySoup(TEST_SOUP, new QuerySpec("key", "ka2", "ka3", Order.descending), 0);
		assertEquals("Two results expected", 2, result.length());
		assertSameJSON("Wrong result for query", soupElt3Created, result.getJSONObject(0));
		assertSameJSON("Wrong result for query", soupElt2Created, result.getJSONObject(1));
		
		// Range query - specified projections
		result = store.querySoup(TEST_SOUP, new QuerySpec("key", "ka2", "ka3", new String[] {"otherValue", "value"}), 0);
		assertEquals("Two results expected", 2, result.length());
		assertSameJSON("Wrong result for query", new JSONObject("{'otherValue':'ova2', 'value':'va2'}"), result.getJSONObject(0));
		assertSameJSON("Wrong result for query", new JSONObject("{'otherValue':'ova3', 'value':'va3'}"), result.getJSONObject(1));
		
		// Range query - specified projections - descending order
		result = store.querySoup(TEST_SOUP, new QuerySpec("key", "ka2", "ka3", new String[] {"otherValue", "value"}, Order.descending), 0);
		assertEquals("Two results expected", 2, result.length());
		assertSameJSON("Wrong result for query", new JSONObject("{'otherValue':'ova3', 'value':'va3'}"), result.getJSONObject(0));
		assertSameJSON("Wrong result for query", new JSONObject("{'otherValue':'ova2', 'value':'va2'}"), result.getJSONObject(1));
	}
	
	
	/**
	 * Helper method to check that a table exists in the database
	 * @param tableName
	 * @return
	 */
	private boolean hasTable(String tableName) {
		Cursor c = null;
		try {
			c = db.query("sqlite_master", null, null, null, "type = ? and name = ?", "table", tableName);
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

	/**
	 * Remove soup entry id, last modified date 
	 * @param retrievedSoupElt
	 * @return
	 * @throws JSONException
	 */
	private JSONObject removeExtraFields(JSONObject retrievedSoupElt) throws JSONException {
		
		JSONObject cleansedElt = new JSONObject(retrievedSoupElt.toString());
		cleansedElt.remove(SmartStore.SOUP_ENTRY_ID);
		cleansedElt.remove(SmartStore.SOUP_LAST_MODIFIED_DATE);

		return cleansedElt;
	}

	/**
	 * @param soupElt
	 * @return _soupEntryId field value
	 * @throws JSONException
	 */
	private long idOf(JSONObject soupElt) throws JSONException {
		return soupElt.getLong(SmartStore.SOUP_ENTRY_ID);
	}

	/**
	 * Compare two JSON
	 * @param message
	 * @param expected
	 * @param actual
	 * @throws JSONException
	 */
	private void assertSameJSON(String message, Object expected, Object actual) throws JSONException {
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
	private void assertSameJSONArray(String message, JSONArray expected, JSONArray actual) throws JSONException {
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
	private void assertSameJSONObject(String message, JSONObject expected, JSONObject actual) throws JSONException {
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
	
}