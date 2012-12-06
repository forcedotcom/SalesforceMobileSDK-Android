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

import net.sqlcipher.database.SQLiteDatabase;

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
	private SQLiteDatabase db;
	private SmartStore store;
	
	@Override
	public void setUp() throws Exception {
		super.setUp();
		targetContext = getInstrumentation().getTargetContext();
		DBHelper.INSTANCE.reset(targetContext); // start clean
		db = getWritableDatabase();
		store = new SmartStore(db);
		
		assertFalse("Table for test_soup should not exist", hasTable("TABLE_1"));
		assertFalse("Soup test_soup should not exist", store.hasSoup(TEST_SOUP));
		store.registerSoup(TEST_SOUP, new IndexSpec[] {new IndexSpec("key", Type.string)});
		assertEquals("Table for test_soup was expected to be called TABLE_1", "TABLE_1", store.getSoupTableName(TEST_SOUP));
		assertTrue("Table for test_soup should now exist", hasTable("TABLE_1"));
		assertTrue("Soup test_soup should now exist", store.hasSoup(TEST_SOUP));
	}
	
	protected abstract SQLiteDatabase getWritableDatabase();

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
	}

	/**
	 * Testing method with paths to non-top level string/integer/array/map
	 * @throws JSONException 
	 */
	public void testProjectNested() throws JSONException {
		JSONObject json = new JSONObject("{'a':'va', 'b':2, 'c':[0,1,2], 'd': {'d1':'vd1', 'd2':'vd2', 'd3':[1,2], 'd4':{'e':5}}}");
		
		// Nested elements
		assertEquals("Wrong value for key d.d1", "vd1", SmartStore.project(json, "d.d1"));
		assertEquals("Wrong value for key d.d2", "vd2", SmartStore.project(json, "d.d2"));
		assertSameJSON("Wrong value for key d.d3", new JSONArray("[1,2]"), SmartStore.project(json, "d.d3"));
		assertSameJSON("Wrong value for key d.d4", new JSONObject("{'e':5}"), SmartStore.project(json, "d.d4"));
		assertEquals("Wrong value for key d.d4.e", 5, SmartStore.project(json, "d.d4.e"));
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
		// Before
		assertNull("getSoupTableName should have returned null", store.getSoupTableName(THIRD_TEST_SOUP));
		assertFalse("Soup third_test_soup should not exist", store.hasSoup(THIRD_TEST_SOUP));
		
		// Register
		store.registerSoup(THIRD_TEST_SOUP, new IndexSpec[] {new IndexSpec("key", Type.string), new IndexSpec("value", Type.string)});
		String soupTableName = store.getSoupTableName(THIRD_TEST_SOUP);
		assertEquals("getSoupTableName should have returned TABLE_2", "TABLE_2", soupTableName);
		assertTrue("Table for soup third_test_soup does exist", hasTable(soupTableName));
		assertTrue("Register soup call failed", store.hasSoup(THIRD_TEST_SOUP));
		
		// Drop
		store.dropSoup(THIRD_TEST_SOUP);
		
		// After
		assertFalse("Soup third_test_soup should no longer exist", store.hasSoup(THIRD_TEST_SOUP));
		assertNull("getSoupTableName should have returned null", store.getSoupTableName(THIRD_TEST_SOUP));
		assertFalse("Table for soup third_test_soup does exist", hasTable(soupTableName));
	}

	/**
	 * Testing getAllSoupNames: register a new soup and then drop it and call getAllSoupNames before and after
	 */
	public void testGetAllSoupNames() {
		// Before
		assertEquals("One soup name expected", 1, store.getAllSoupNames().size());
		assertTrue(TEST_SOUP + " should have been returned by getAllSoupNames", store.getAllSoupNames().contains(TEST_SOUP));

		// Register another soup
		store.registerSoup(THIRD_TEST_SOUP, new IndexSpec[] {new IndexSpec("key", Type.string), new IndexSpec("value", Type.string)});
		assertEquals("Two soup names expected", 2, store.getAllSoupNames().size());
		assertTrue(TEST_SOUP + " should have been returned by getAllSoupNames", store.getAllSoupNames().contains(TEST_SOUP));
		assertTrue(THIRD_TEST_SOUP + " should have been returned by getAllSoupNames", store.getAllSoupNames().contains(THIRD_TEST_SOUP));

		// Drop the latest soup
		store.dropSoup(THIRD_TEST_SOUP);
		assertEquals("One soup name expected", 1, store.getAllSoupNames().size());
		assertTrue(TEST_SOUP + " should have been returned by getAllSoupNames", store.getAllSoupNames().contains(TEST_SOUP));
	}
	
	/**
	 * Testing dropAllSoups: register a couple of soups then drop them all
	 */
	public void testDropAllSoups() {
		// Register another soup
		assertEquals("One soup name expected", 1, store.getAllSoupNames().size());
		store.registerSoup(THIRD_TEST_SOUP, new IndexSpec[] {new IndexSpec("key", Type.string), new IndexSpec("value", Type.string)});
		assertEquals("Two soup names expected", 2, store.getAllSoupNames().size());

		// Drop all
		store.dropAllSoups();
		assertEquals("No soup name expected", 0, store.getAllSoupNames().size());
		assertFalse("Soup " + THIRD_TEST_SOUP + " should no longer exist", store.hasSoup(THIRD_TEST_SOUP));
		assertFalse("Soup " + TEST_SOUP + " should no longer exist", store.hasSoup(TEST_SOUP));
	}
	
	
	/**
	 * Testing create: create a single element with a single index pointing to a top level attribute
	 * @throws JSONException 
	 */
	public void testCreateOne() throws JSONException {
		JSONObject soupElt = new JSONObject("{'key':'ka', 'value':'va'}");
		JSONObject soupEltCreated = store.create(TEST_SOUP, soupElt);
		
		// Check DB
		Cursor c = null;
		try {
			String soupTableName = store.getSoupTableName(TEST_SOUP);
			c = DBHelper.INSTANCE.query(db, soupTableName, null, null, null, null);
			assertTrue("Expected a soup element", c.moveToFirst());
			assertEquals("Expected one soup element only", 1, c.getCount());
			assertEquals("Wrong id", idOf(soupEltCreated), c.getLong(c.getColumnIndex("id")));
			assertEquals("Wrong created date", soupEltCreated.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
			assertEquals("Wrong value in index column", "ka", c.getString(c.getColumnIndex(soupTableName + "_0")));
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
		assertFalse("Soup other_test_soup should not exist", store.hasSoup(OTHER_TEST_SOUP));
		store.registerSoup(OTHER_TEST_SOUP, new IndexSpec[] {new IndexSpec("lastName", Type.string), new IndexSpec("address.city", Type.string)});
		assertTrue("Register soup call failed", store.hasSoup(OTHER_TEST_SOUP));
		
		
		
		JSONObject soupElt1 = new JSONObject("{'lastName':'Doe', 'address':{'city':'San Francisco','street':'1 market'}}");
		JSONObject soupElt2 = new JSONObject("{'lastName':'Jackson', 'address':{'city':'Los Angeles','street':'100 mission'}}");
		JSONObject soupElt3 = new JSONObject("{'lastName':'Watson', 'address':{'city':'London','street':'50 market'}}");
		
		JSONObject soupElt1Created = store.create(OTHER_TEST_SOUP, soupElt1);
		JSONObject soupElt2Created = store.create(OTHER_TEST_SOUP, soupElt2);
		JSONObject soupElt3Created = store.create(OTHER_TEST_SOUP, soupElt3);
		
		// Check DB
		Cursor c = null;
		try {
			String soupTableName = store.getSoupTableName(OTHER_TEST_SOUP);
			assertEquals("Table for other_test_soup was expected to be called TABLE_2", "TABLE_2", soupTableName);
			assertTrue("Table for other_test_soup should now exist", hasTable("TABLE_2"));
			
			c = DBHelper.INSTANCE.query(db, soupTableName, null, "id ASC", null, null);
			assertTrue("Expected a soup element", c.moveToFirst());
			assertEquals("Expected three soup elements", 3, c.getCount());
			
			assertEquals("Wrong id", idOf(soupElt1Created), c.getLong(c.getColumnIndex("id")));
			assertEquals("Wrong created date", soupElt1Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
			assertEquals("Wrong value in index column", "Doe", c.getString(c.getColumnIndex(soupTableName + "_0")));
			assertEquals("Wrong value in index column", "San Francisco", c.getString(c.getColumnIndex(soupTableName + "_1")));
			assertSameJSON("Wrong value in soup column", soupElt1Created, new JSONObject(c.getString(c.getColumnIndex("soup"))));
			
			c.moveToNext();
			assertEquals("Wrong id", idOf(soupElt2Created), c.getLong(c.getColumnIndex("id")));
			assertEquals("Wrong created date", soupElt2Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
			assertEquals("Wrong value in index column", "Jackson", c.getString(c.getColumnIndex(soupTableName + "_0")));
			assertEquals("Wrong value in index column", "Los Angeles", c.getString(c.getColumnIndex(soupTableName + "_1")));
			assertSameJSON("Wrong value in soup column", soupElt2Created, new JSONObject(c.getString(c.getColumnIndex("soup"))));

			c.moveToNext();
			assertEquals("Wrong id", idOf(soupElt3Created), c.getLong(c.getColumnIndex("id")));
			assertEquals("Wrong created date", soupElt3Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
			assertEquals("Wrong value in index column", "Watson", c.getString(c.getColumnIndex(soupTableName + "_0")));
			assertEquals("Wrong value in index column", "London", c.getString(c.getColumnIndex(soupTableName + "_1")));
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
		
		JSONObject soupElt1Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt1Created)).getJSONObject(0);
		JSONObject soupElt2Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt2Created)).getJSONObject(0);
		JSONObject soupElt3Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt3Created)).getJSONObject(0);

		assertSameJSON("Retrieve mismatch", soupElt1Created, soupElt1Retrieved);
		assertSameJSON("Retrieve mismatch", soupElt2Updated, soupElt2Retrieved);
		assertSameJSON("Retrieve mismatch", soupElt3Created, soupElt3Retrieved);

		// Check DB
		Cursor c = null;
		try {
			String soupTableName = store.getSoupTableName(TEST_SOUP);			
			c = DBHelper.INSTANCE.query(db, soupTableName, null, "id ASC", null, null);
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
		
		JSONObject soupElt1Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt1Upserted)).getJSONObject(0);
		JSONObject soupElt2Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt2Upserted)).getJSONObject(0);
		JSONObject soupElt3Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt3Upserted)).getJSONObject(0);

		assertSameJSON("Retrieve mismatch", soupElt1Upserted, soupElt1Retrieved);
		assertSameJSON("Retrieve mismatch", soupElt2Updated, soupElt2Retrieved);
		assertSameJSON("Retrieve mismatch", soupElt3Upserted, soupElt3Retrieved);
		
		// Check DB
		Cursor c = null;
		try {
			String soupTableName = store.getSoupTableName(TEST_SOUP);			
			c = DBHelper.INSTANCE.query(db, soupTableName, null, "id ASC", null, null);
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
	 * Testing upsert with external id: upsert multiple soup elements and re-upsert one of them, check them all
	 * @throws JSONException
	 */
	public void testUpsertWithExternalId() throws JSONException {
		JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'ka2', 'value':'va2'}");
		JSONObject soupElt3 = new JSONObject("{'key':'ka3', 'value':'va3'}");
		
		JSONObject soupElt1Upserted = store.upsert(TEST_SOUP, soupElt1, "key");
		JSONObject soupElt2Upserted = store.upsert(TEST_SOUP, soupElt2, "key");
		JSONObject soupElt3Upserted = store.upsert(TEST_SOUP, soupElt3, "key");

		SystemClock.sleep(10); // to get a different last modified date
		JSONObject soupElt2ForUpdate = new JSONObject("{'key':'ka2', 'value':'va2u'}");
		JSONObject soupElt2Updated = store.upsert(TEST_SOUP, soupElt2ForUpdate, "key");
		
		JSONObject soupElt1Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt1Upserted)).getJSONObject(0);
		JSONObject soupElt2Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt2Upserted)).getJSONObject(0);
		JSONObject soupElt3Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt3Upserted)).getJSONObject(0);

		assertSameJSON("Retrieve mismatch", soupElt1Upserted, soupElt1Retrieved);
		assertSameJSON("Retrieve mismatch", soupElt2Updated, soupElt2Retrieved);
		assertSameJSON("Retrieve mismatch", soupElt3Upserted, soupElt3Retrieved);

		// Check DB
		Cursor c = null;
		try {
			String soupTableName = store.getSoupTableName(TEST_SOUP);			
			c = DBHelper.INSTANCE.query(db, soupTableName, null, "id ASC", null, null);
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
	 * Testing upsert passing a non-indexed path for the external id (should fail)
	 * @throws JSONException
	 */
	public void testUpsertWithNonIndexedExternalId() throws JSONException {
		JSONObject soupElt = new JSONObject("{'key':'ka1', 'value':'va1'}");
		
		try {
			store.upsert(TEST_SOUP, soupElt, "value");
			fail("Exception was expected: value is not an indexed field");
		}
		catch (RuntimeException e) {
			assertTrue("Wrong exception", e.getMessage().contains("does not have an index"));
		}
	}

	/**
	 * Testing upsert with an external id that is not unique in the soup
	 * @throws JSONException
	 */
	public void testUpsertWithNonUniqueExternalId() throws JSONException {
		JSONObject soupElt1 = new JSONObject("{'key':'ka', 'value':'va1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'ka', 'value':'va2'}");
		JSONObject soupElt3 = new JSONObject("{'key':'ka', 'value':'va3'}");
		
		JSONObject soupElt1Upserted = store.upsert(TEST_SOUP, soupElt1);
		JSONObject soupElt2Upserted = store.upsert(TEST_SOUP, soupElt2);

		JSONObject soupElt1Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt1Upserted)).getJSONObject(0);
		JSONObject soupElt2Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt2Upserted)).getJSONObject(0);

		assertSameJSON("Retrieve mismatch", soupElt1Upserted, soupElt1Retrieved);
		assertSameJSON("Retrieve mismatch", soupElt2Upserted, soupElt2Retrieved);
		
		try {
			store.upsert(TEST_SOUP, soupElt3, "key");
			fail("Exception was expected: key is not unique in the soup");
		}
		catch (RuntimeException e) {
			assertTrue("Wrong exception", e.getMessage().contains("are more than one soup elements"));
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
		
		JSONObject soupElt1Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt1Created)).getJSONObject(0);
		JSONObject soupElt2Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt2Created)).getJSONObject(0);
		JSONObject soupElt3Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt3Created)).getJSONObject(0);

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

		JSONObject soupElt1Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt1Created)).getJSONObject(0);
		JSONArray soupElt2Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt2Created));
		JSONObject soupElt3Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt3Created)).getJSONObject(0);

		assertSameJSON("Retrieve mismatch", soupElt1Created, soupElt1Retrieved);
		assertEquals("Should be empty", 0, soupElt2Retrieved.length());
		assertSameJSON("Retrieve mismatch", soupElt3Created, soupElt3Retrieved);
		
		// Check DB
		Cursor c = null;
		try {
			String soupTableName = store.getSoupTableName(TEST_SOUP);
			c = DBHelper.INSTANCE.query(db, soupTableName, null, "id ASC", null, null);
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
	 * Test query when looking for all elements
	 * @throws JSONException 
	 */
	public void testAllQuery() throws JSONException {
		JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1', 'otherValue':'ova1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'ka2', 'value':'va2', 'otherValue':'ova2'}");
		JSONObject soupElt3 = new JSONObject("{'key':'ka3', 'value':'va3', 'otherValue':'ova3'}");
		
		JSONObject soupElt1Created = store.create(TEST_SOUP, soupElt1);
		JSONObject soupElt2Created = store.create(TEST_SOUP, soupElt2);
		JSONObject soupElt3Created = store.create(TEST_SOUP, soupElt3);

		// Query all - small page
		JSONArray result = store.querySoup(TEST_SOUP, QuerySpec.buildAllQuerySpec(Order.ascending, 2), 0);
		assertEquals("Two elements expected", 2, result.length());
		assertSameJSON("Wrong result for query", soupElt1Created, result.getJSONObject(0));
		assertSameJSON("Wrong result for query", soupElt2Created, result.getJSONObject(1));

		// Query all - next small page
		result = store.querySoup(TEST_SOUP, QuerySpec.buildAllQuerySpec(Order.ascending, 2), 1);
		assertEquals("One element expected", 1, result.length());
		assertSameJSON("Wrong result for query", soupElt3Created, result.getJSONObject(0));

		// Query all - large page
		result = store.querySoup(TEST_SOUP, QuerySpec.buildAllQuerySpec(Order.ascending, 10), 0);
		assertEquals("Three elements expected", 3, result.length());
		assertSameJSON("Wrong result for query", soupElt1Created, result.getJSONObject(0));
		assertSameJSON("Wrong result for query", soupElt2Created, result.getJSONObject(1));
		assertSameJSON("Wrong result for query", soupElt3Created, result.getJSONObject(2));
	
	}
	
	/**
	 * Test query when looking for a specific element
	 * @throws JSONException 
	 */
	public void testMatchQuery() throws JSONException {
		JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1', 'otherValue':'ova1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'ka2', 'value':'va2', 'otherValue':'ova2'}");
		JSONObject soupElt3 = new JSONObject("{'key':'ka3', 'value':'va3', 'otherValue':'ova3'}");
		
		store.create(TEST_SOUP, soupElt1);
		JSONObject soupElt2Created= store.create(TEST_SOUP, soupElt2);
		store.create(TEST_SOUP, soupElt3);

		// Exact match
		JSONArray result = store.querySoup(TEST_SOUP, QuerySpec.buildExactQuerySpec("key", "ka2", 10), 0);
		assertEquals("One result expected", 1, result.length());
		assertSameJSON("Wrong result for query", soupElt2Created, result.getJSONObject(0));

	}

	/**
	 * Query test looking for a range of elements (with ascending or descending ordering)
	 * @throws JSONException 
	 */
	public void testRangeQuery() throws JSONException {
		JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1', 'otherValue':'ova1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'ka2', 'value':'va2', 'otherValue':'ova2'}");
		JSONObject soupElt3 = new JSONObject("{'key':'ka3', 'value':'va3', 'otherValue':'ova3'}");
		
		/*JSONObjectsoupElt1Created = */store.create(TEST_SOUP, soupElt1);
		JSONObject soupElt2Created = store.create(TEST_SOUP, soupElt2);
		JSONObject soupElt3Created = store.create(TEST_SOUP, soupElt3);

		// Range query
		JSONArray result = store.querySoup(TEST_SOUP, QuerySpec.buildRangeQuerySpec("key", "ka2", "ka3", Order.ascending, 10), 0);
		assertEquals("Two results expected", 2, result.length());
		assertSameJSON("Wrong result for query", soupElt2Created, result.getJSONObject(0));
		assertSameJSON("Wrong result for query", soupElt3Created, result.getJSONObject(1));

		// Range query - descending order
		result = store.querySoup(TEST_SOUP, QuerySpec.buildRangeQuerySpec("key", "ka2", "ka3", Order.descending, 10), 0);
		assertEquals("Two results expected", 2, result.length());
		assertSameJSON("Wrong result for query", soupElt3Created, result.getJSONObject(0));
		assertSameJSON("Wrong result for query", soupElt2Created, result.getJSONObject(1));
	}

	/**
	 * Query test looking using like (with ascending or descending ordering)
	 * @throws JSONException 
	 */
	public void testLikeQuery() throws JSONException {
		JSONObject soupElt1 = new JSONObject("{'key':'abcd', 'value':'va1', 'otherValue':'ova1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'bbcd', 'value':'va2', 'otherValue':'ova2'}");
		JSONObject soupElt3 = new JSONObject("{'key':'abcc', 'value':'va3', 'otherValue':'ova3'}");
		JSONObject soupElt4 = new JSONObject("{'key':'defg', 'value':'va4', 'otherValue':'ova3'}");
		
		JSONObject soupElt1Created = store.create(TEST_SOUP, soupElt1);
		JSONObject soupElt2Created = store.create(TEST_SOUP, soupElt2);
		JSONObject soupElt3Created = store.create(TEST_SOUP, soupElt3);
		/*JSONObject soupElt4Created = */ store.create(TEST_SOUP, soupElt4);

		// Like query (starts with)
		JSONArray result = store.querySoup(TEST_SOUP, QuerySpec.buildLikeQuerySpec("key", "abc%", Order.ascending, 10), 0);
		assertEquals("Two results expected", 2, result.length());
		assertSameJSON("Wrong result for query", soupElt3Created, result.getJSONObject(0));
		assertSameJSON("Wrong result for query", soupElt1Created, result.getJSONObject(1));

		// Like query (ends with)
		result = store.querySoup(TEST_SOUP, QuerySpec.buildLikeQuerySpec("key", "%bcd", Order.ascending, 10), 0);
		assertEquals("Two results expected", 2, result.length());
		assertSameJSON("Wrong result for query", soupElt1Created, result.getJSONObject(0));
		assertSameJSON("Wrong result for query", soupElt2Created, result.getJSONObject(1));

		// Like query (starts with) - descending order
		result = store.querySoup(TEST_SOUP, QuerySpec.buildLikeQuerySpec("key", "abc%", Order.descending, 10), 0);
		assertEquals("Two results expected", 2, result.length());
		assertSameJSON("Wrong result for query", soupElt1Created, result.getJSONObject(0));
		assertSameJSON("Wrong result for query", soupElt3Created, result.getJSONObject(1));

		// Like query (ends with) - descending order
		result = store.querySoup(TEST_SOUP, QuerySpec.buildLikeQuerySpec("key", "%bcd", Order.descending, 10), 0);
		assertEquals("Two results expected", 2, result.length());
		assertSameJSON("Wrong result for query", soupElt2Created, result.getJSONObject(0));
		assertSameJSON("Wrong result for query", soupElt1Created, result.getJSONObject(1));

		// Like query (contains)
		result = store.querySoup(TEST_SOUP, QuerySpec.buildLikeQuerySpec("key", "%bc%", Order.ascending, 10), 0);
		assertEquals("Three results expected", 3, result.length());
		assertSameJSON("Wrong result for query", soupElt3Created, result.getJSONObject(0));
		assertSameJSON("Wrong result for query", soupElt1Created, result.getJSONObject(1));
		assertSameJSON("Wrong result for query", soupElt2Created, result.getJSONObject(2));

		// Like query (contains) - descending order
		result = store.querySoup(TEST_SOUP, QuerySpec.buildLikeQuerySpec("key", "%bc%", Order.descending, 10), 0);
		assertEquals("Three results expected", 3, result.length());
		assertSameJSON("Wrong result for query", soupElt2Created, result.getJSONObject(0));
		assertSameJSON("Wrong result for query", soupElt1Created, result.getJSONObject(1));
		assertSameJSON("Wrong result for query", soupElt3Created, result.getJSONObject(2));
	}
	
	/**
	 * Test upsert soup element with null value in indexed field
	 * @throws JSONException 
	 */
	public void testUpsertWithNullInIndexedField() throws JSONException {
		// Before
		assertFalse("Soup third_test_soup should not exist", store.hasSoup(THIRD_TEST_SOUP));
		
		// Register
		store.registerSoup(THIRD_TEST_SOUP, new IndexSpec[] {new IndexSpec("key", Type.string), new IndexSpec("value", Type.string)});
		assertTrue("Register soup call failed", store.hasSoup(THIRD_TEST_SOUP));

		// Upsert
		JSONObject soupElt1 = new JSONObject("{'key':'ka', 'value':null}");
		JSONObject soupElt1Upserted = store.upsert(THIRD_TEST_SOUP, soupElt1);
		
		// Check
		JSONObject soupElt1Retrieved = store.retrieve(THIRD_TEST_SOUP, idOf(soupElt1Upserted)).getJSONObject(0);		
		assertSameJSON("Retrieve mismatch", soupElt1Upserted, soupElt1Retrieved);
	}
	
	
	/**
	 * Helper method to check that a table exists in the database
	 * @param tableName
	 * @return
	 */
	private boolean hasTable(String tableName) {
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
	private void safeClose(Cursor c) {
		if (c != null) {
			c.close();
		}
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