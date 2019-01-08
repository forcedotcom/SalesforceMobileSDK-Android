/*
 * Copyright (c) 2011-present, salesforce.com, inc.
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

import android.database.Cursor;
import android.os.SystemClock;
import androidx.test.filters.MediumTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.salesforce.androidsdk.smartstore.store.DBHelper;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec.Order;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;
import com.salesforce.androidsdk.smartstore.store.SoupSpec;
import com.salesforce.androidsdk.util.test.JSONTestHelper;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Main test suite for SmartStore
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class SmartStoreTest extends SmartStoreTestCase {

	protected static final String TEST_SOUP = "test_soup";
	protected static final String OTHER_TEST_SOUP = "other_test_soup";
	private static final String THIRD_TEST_SOUP = "third_test_soup";
	private static final String FOURTH_TEST_SOUP = "fourth_test_soup";

	@Before
	public void setUp() throws Exception {
		super.setUp();
        store.setCaptureExplainQueryPlan(true);
        Assert.assertFalse("Table for test_soup should not exist", hasTable("TABLE_1"));
        Assert.assertFalse("Soup test_soup should not exist", store.hasSoup(TEST_SOUP));
		registerSoup(store, TEST_SOUP, new IndexSpec[] { new IndexSpec("key", Type.string) });
        Assert.assertEquals("Table for test_soup was expected to be called TABLE_1", "TABLE_1", getSoupTableName(TEST_SOUP));
        Assert.assertTrue("Table for test_soup should now exist", hasTable("TABLE_1"));
        Assert.assertTrue("Soup test_soup should now exist", store.hasSoup(TEST_SOUP));
	}

	@After
	public void tearDown() throws Exception {
	    super.tearDown();
    }

    @Override
    protected String getEncryptionKey() {
        return "test123";
    }

    /**
	 * Checking compile options
	 */
    @Test
	public void testCompileOptions() {
		List<String> compileOptions = store.getCompileOptions();
        Assert.assertTrue("ENABLE_FTS4 flag not found in compile options", compileOptions.contains("ENABLE_FTS4"));
        Assert.assertTrue("ENABLE_FTS3_PARENTHESIS flag not found in compile options", compileOptions.contains("ENABLE_FTS3_PARENTHESIS"));
        Assert.assertTrue("ENABLE_FTS5 flag not found in compile options", compileOptions.contains("ENABLE_FTS5"));
        Assert.assertTrue("ENABLE_JSON1 flag not found in compile options", compileOptions.contains("ENABLE_JSON1"));
	}

	/**
	 * Method to check soup blob with one stored by db. Can be overridden to check external storage if necessary.
	 */
	protected void assertSameSoupAsDB(JSONObject soup, Cursor c, String soupName, Long id) throws JSONException {
		JSONTestHelper.assertSameJSON("Wrong value in soup column", soup, new JSONObject(c.getString(c.getColumnIndex("soup"))));
	}

	/**
	 * Testing method with paths to top level string/integer/array/map as well as edge cases (null object/null or empty path)
	 * @throws JSONException 
	 */
    @Test
	public void testProjectTopLevel() throws JSONException {
		JSONObject json = new JSONObject("{'a':'va', 'b':2, 'c':[0,1,2], 'd': {'d1':'vd1', 'd2':'vd2', 'd3':[1,2], 'd4':{'e':5}}}");

		// Null object
        Assert.assertNull("Should have been null", SmartStore.project(null, "path"));
		
		// Root
		JSONTestHelper.assertSameJSON("Should have returned whole object", json, SmartStore.project(json, null));
		JSONTestHelper.assertSameJSON("Should have returned whole object", json, SmartStore.project(json, ""));
		
		// Top-level elements
        Assert.assertEquals("Wrong value for key a", "va", SmartStore.project(json, "a"));
        Assert.assertEquals("Wrong value for key b", 2, SmartStore.project(json, "b"));
		JSONTestHelper.assertSameJSON("Wrong value for key c", new JSONArray("[0,1,2]"), SmartStore.project(json, "c"));
		JSONTestHelper.assertSameJSON("Wrong value for key d", new JSONObject("{'d1':'vd1','d2':'vd2','d3':[1,2],'d4':{'e':5}}"), (JSONObject) SmartStore.project(json, "d"));
	}

	/**
	 * Testing method with paths to non-top level string/integer/array/map
	 * @throws JSONException 
	 */
    @Test
	public void testProjectNested() throws JSONException {
		JSONObject json = new JSONObject("{'a':'va', 'b':2, 'c':[0,1,2], 'd': {'d1':'vd1', 'd2':'vd2', 'd3':[1,2], 'd4':{'e':5}}}");
		
		// Nested elements
        Assert.assertEquals("Wrong value for key d.d1", "vd1", SmartStore.project(json, "d.d1"));
        Assert.assertEquals("Wrong value for key d.d2", "vd2", SmartStore.project(json, "d.d2"));
		JSONTestHelper.assertSameJSON("Wrong value for key d.d3", new JSONArray("[1,2]"), SmartStore.project(json, "d.d3"));
		JSONTestHelper.assertSameJSON("Wrong value for key d.d4", new JSONObject("{'e':5}"), SmartStore.project(json, "d.d4"));
        Assert.assertEquals("Wrong value for key d.d4.e", 5, SmartStore.project(json, "d.d4.e"));
	}

	/**
	 * Testing method with path through arrays
	 * @throws JSONException
	 */
    @Test
	public void testProjectThroughArrays() throws JSONException {
		JSONObject json = new JSONObject("{\"a\":\"a1\", \"b\":2, \"c\":[{\"cc\":\"cc1\"}, {\"cc\":2}, {\"cc\":[1,2,3]}, {}, {\"cc\":{\"cc5\":5}}], \"d\":[{\"dd\":[{\"ddd\":\"ddd11\"},{\"ddd\":\"ddd12\"}]}, {\"dd\":[{\"ddd\":\"ddd21\"}]}, {\"dd\":[{\"ddd\":\"ddd31\"},{\"ddd3\":\"ddd32\"}]}]}");
		JSONTestHelper.assertSameJSON("Wrong value for key c", new JSONArray("[{\"cc\":\"cc1\"}, {\"cc\":2}, {\"cc\":[1,2,3]}, {}, {\"cc\":{\"cc5\":5}}]"), SmartStore.project(json, "c"));
		JSONTestHelper.assertSameJSON("Wrong value for key c.cc", new JSONArray("[\"cc1\",2, [1,2,3], {\"cc5\":5}]"), SmartStore.project(json, "c.cc"));
		JSONTestHelper.assertSameJSON("Wrong value for key c.cc.cc5", new JSONArray("[5]"), SmartStore.project(json, "c.cc.cc5"));
		JSONTestHelper.assertSameJSON("Wrong value for key d", new JSONArray("[{\"dd\":[{\"ddd\":\"ddd11\"},{\"ddd\":\"ddd12\"}]}, {\"dd\":[{\"ddd\":\"ddd21\"}]}, {\"dd\":[{\"ddd\":\"ddd31\"},{\"ddd3\":\"ddd32\"}]}]"), SmartStore.project(json, "d"));
		JSONTestHelper.assertSameJSON("Wrong value for key d.dd", new JSONArray("[[{\"ddd\":\"ddd11\"},{\"ddd\":\"ddd12\"}], [{\"ddd\":\"ddd21\"}], [{\"ddd\":\"ddd31\"},{\"ddd3\":\"ddd32\"}]]"), SmartStore.project(json, "d.dd"));
		JSONTestHelper.assertSameJSON("Wrong value for key d.dd.ddd", new JSONArray("[[\"ddd11\",\"ddd12\"],[\"ddd21\"],[\"ddd31\"]]"), SmartStore.project(json, "d.dd.ddd"));
		JSONTestHelper.assertSameJSON("Wrong value for key d.dd.ddd3", new JSONArray("[[\"ddd32\"]]"), SmartStore.project(json, "d.dd.ddd3"));
	}

	/**
	 * Check that the meta data table (soup index map) has been created
	 */
    @Test
	public void testMetaDataTableCreated() {
        Assert.assertTrue("Table soup_index_map not found", hasTable("soup_index_map"));
	}

	/**
	 * Test register/drop soup
	 */
    @Test
	public void testRegisterDropSoup() {

		// Before
        Assert.assertNull("getSoupTableName should have returned null", getSoupTableName(THIRD_TEST_SOUP));
        Assert.assertFalse("Soup third_test_soup should not exist", store.hasSoup(THIRD_TEST_SOUP));
		
		// Register
		registerSoup(store, THIRD_TEST_SOUP, new IndexSpec[] { new IndexSpec("key", Type.string), new IndexSpec("value", Type.string) });
		String soupTableName = getSoupTableName(THIRD_TEST_SOUP);
        Assert.assertEquals("getSoupTableName should have returned TABLE_2", "TABLE_2", soupTableName);
        Assert.assertTrue("Table for soup third_test_soup does exist", hasTable(soupTableName));
        Assert.assertTrue("Register soup call failed", store.hasSoup(THIRD_TEST_SOUP));

        // Check soup indexes
        final IndexSpec[] indexSpecs = store.getSoupIndexSpecs(THIRD_TEST_SOUP);
        Assert.assertEquals("Wrong path", "key", indexSpecs[0].path);
        Assert.assertEquals("Wrong type", Type.string, indexSpecs[0].type);
        Assert.assertEquals("Wrong column name", soupTableName + "_0", indexSpecs[0].columnName);
        Assert.assertEquals("Wrong path", "value", indexSpecs[1].path);
        Assert.assertEquals("Wrong type", Type.string, indexSpecs[1].type);
        Assert.assertEquals("Wrong column name", soupTableName + "_1", indexSpecs[1].columnName);

        // Check db indexes
        checkDatabaseIndexes(soupTableName, Arrays.asList(new String[] {
                "CREATE INDEX " + soupTableName + "_0_idx on " + soupTableName + " ( " + soupTableName + "_0 )",
                "CREATE INDEX " + soupTableName + "_1_idx on " + soupTableName + " ( " + soupTableName + "_1 )",
                "CREATE INDEX " + soupTableName + "_created_idx on " + soupTableName + " ( created )",
                "CREATE INDEX " + soupTableName + "_lastModified_idx on " + soupTableName + " ( lastModified )"
        }));

		// Drop
		store.dropSoup(THIRD_TEST_SOUP);
		
		// After
        Assert.assertFalse("Soup third_test_soup should no longer exist", store.hasSoup(THIRD_TEST_SOUP));
        Assert.assertNull("getSoupTableName should have returned null", getSoupTableName(THIRD_TEST_SOUP));
        Assert.assertFalse("Table for soup third_test_soup does exist", hasTable(soupTableName));
	}

	/**
	 * Testing getAllSoupNames: register a new soup and then drop it and call getAllSoupNames before and after
	 */
    @Test
	public void testGetAllSoupNames() {

		// Before
        Assert.assertEquals("One soup name expected", 1, store.getAllSoupNames().size());
        Assert.assertTrue(TEST_SOUP + " should have been returned by getAllSoupNames", store.getAllSoupNames().contains(TEST_SOUP));

		// Register another soup
		registerSoup(store, THIRD_TEST_SOUP, new IndexSpec[] { new IndexSpec("key", Type.string), new IndexSpec("value", Type.string) });
        Assert.assertEquals("Two soup names expected", 2, store.getAllSoupNames().size());
        Assert.assertTrue(TEST_SOUP + " should have been returned by getAllSoupNames", store.getAllSoupNames().contains(TEST_SOUP));
        Assert.assertTrue(THIRD_TEST_SOUP + " should have been returned by getAllSoupNames", store.getAllSoupNames().contains(THIRD_TEST_SOUP));

		// Drop the latest soup
		store.dropSoup(THIRD_TEST_SOUP);
        Assert.assertEquals("One soup name expected", 1, store.getAllSoupNames().size());
        Assert.assertTrue(TEST_SOUP + " should have been returned by getAllSoupNames", store.getAllSoupNames().contains(TEST_SOUP));
	}
	
	/**
	 * Testing dropAllSoups: register a couple of soups then drop them all
	 */
    @Test
	public void testDropAllSoups() {

		// Register another soup
        Assert.assertEquals("One soup name expected", 1, store.getAllSoupNames().size());
		registerSoup(store, THIRD_TEST_SOUP, new IndexSpec[] { new IndexSpec("key", Type.string), new IndexSpec("value", Type.string) });
        Assert.assertEquals("Two soup names expected", 2, store.getAllSoupNames().size());

		// Drop all
		store.dropAllSoups();
        Assert.assertEquals("No soup name expected", 0, store.getAllSoupNames().size());
        Assert.assertFalse("Soup " + THIRD_TEST_SOUP + " should no longer exist", store.hasSoup(THIRD_TEST_SOUP));
        Assert.assertFalse("Soup " + TEST_SOUP + " should no longer exist", store.hasSoup(TEST_SOUP));
	}

	/**
	 * Testing create: create a single element with a single index pointing to a top level attribute
	 * @throws JSONException 
	 */
    @Test
	public void testCreateOne() throws JSONException {
		JSONObject soupElt = new JSONObject("{'key':'ka', 'value':'va'}");
		JSONObject soupEltCreated = store.create(TEST_SOUP, soupElt);
		
		// Check DB
		Cursor c = null;
		try {
			final SQLiteDatabase db = dbOpenHelper.getWritableDatabase(getEncryptionKey());
			String soupTableName = getSoupTableName(TEST_SOUP);
			c = DBHelper.getInstance(db).query(db, soupTableName, null, null, null, null);
            Assert.assertTrue("Expected a soup element", c.moveToFirst());
            Assert.assertEquals("Expected one soup element only", 1, c.getCount());
            Assert.assertEquals("Wrong id", idOf(soupEltCreated), c.getLong(c.getColumnIndex("id")));
            Assert.assertEquals("Wrong created date", soupEltCreated.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
            Assert.assertEquals("Wrong value in index column", "ka", c.getString(c.getColumnIndex(soupTableName + "_0")));
			assertSameSoupAsDB(soupEltCreated, c, soupTableName, idOf(soupEltCreated));
            Assert.assertEquals("Created date and last modified date should be equal", c.getLong(c.getColumnIndex("created")),  c.getLong(c.getColumnIndex("lastModified")));
		}
		finally {
			safeClose(c);
		}
	}

	/**
	 * Testing create: create multiple elements with multiple indices not just pointing to top level attributes 
	 * @throws JSONException 
	 */
    @Test
	public void testCreateMultiple() throws JSONException {
        Assert.assertFalse("Soup other_test_soup should not exist", store.hasSoup(OTHER_TEST_SOUP));
		registerSoup(store, OTHER_TEST_SOUP, new IndexSpec[] { new IndexSpec("lastName", Type.string), new IndexSpec("address.city", Type.string) });
        Assert.assertTrue("Register soup call failed", store.hasSoup(OTHER_TEST_SOUP));
		JSONObject soupElt1 = new JSONObject("{'lastName':'Doe', 'address':{'city':'San Francisco','street':'1 market'}}");
		JSONObject soupElt2 = new JSONObject("{'lastName':'Jackson', 'address':{'city':'Los Angeles','street':'100 mission'}}");
		JSONObject soupElt3 = new JSONObject("{'lastName':'Watson', 'address':{'city':'London','street':'50 market'}}");
		JSONObject soupElt1Created = store.create(OTHER_TEST_SOUP, soupElt1);
		JSONObject soupElt2Created = store.create(OTHER_TEST_SOUP, soupElt2);
		JSONObject soupElt3Created = store.create(OTHER_TEST_SOUP, soupElt3);

		// Check DB
		Cursor c = null;
		try {
			String soupTableName = getSoupTableName(OTHER_TEST_SOUP);
            Assert.assertEquals("Table for other_test_soup was expected to be called TABLE_2", "TABLE_2", soupTableName);
            Assert.assertTrue("Table for other_test_soup should now exist", hasTable("TABLE_2"));
			final SQLiteDatabase db = dbOpenHelper.getWritableDatabase(getEncryptionKey());
			c = DBHelper.getInstance(db).query(db, soupTableName, null, "id ASC", null, null);
            Assert.assertTrue("Expected a soup element", c.moveToFirst());
            Assert.assertEquals("Expected three soup elements", 3, c.getCount());
            Assert.assertEquals("Wrong id", idOf(soupElt1Created), c.getLong(c.getColumnIndex("id")));
            Assert.assertEquals("Wrong created date", soupElt1Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
            Assert.assertEquals("Wrong value in index column", "Doe", c.getString(c.getColumnIndex(soupTableName + "_0")));
            Assert.assertEquals("Wrong value in index column", "San Francisco", c.getString(c.getColumnIndex(soupTableName + "_1")));
			assertSameSoupAsDB(soupElt1Created, c, soupTableName, idOf(soupElt1Created));
			c.moveToNext();
            Assert.assertEquals("Wrong id", idOf(soupElt2Created), c.getLong(c.getColumnIndex("id")));
            Assert.assertEquals("Wrong created date", soupElt2Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
            Assert.assertEquals("Wrong value in index column", "Jackson", c.getString(c.getColumnIndex(soupTableName + "_0")));
            Assert.assertEquals("Wrong value in index column", "Los Angeles", c.getString(c.getColumnIndex(soupTableName + "_1")));
			assertSameSoupAsDB(soupElt2Created, c, soupTableName, idOf(soupElt2Created));
			c.moveToNext();
            Assert.assertEquals("Wrong id", idOf(soupElt3Created), c.getLong(c.getColumnIndex("id")));
            Assert.assertEquals("Wrong created date", soupElt3Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
            Assert.assertEquals("Wrong value in index column", "Watson", c.getString(c.getColumnIndex(soupTableName + "_0")));
            Assert.assertEquals("Wrong value in index column", "London", c.getString(c.getColumnIndex(soupTableName + "_1")));
			assertSameSoupAsDB(soupElt3Created, c, soupTableName, idOf(soupElt3Created));
		}
		finally {
			safeClose(c);
		}
	}

	/**
	 * Testing update: create multiple soup elements and update one of them, check them all
	 * @throws JSONException 
	 */
    @Test
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
		JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt1Created, soupElt1Retrieved);
		JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt2Updated, soupElt2Retrieved);
		JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt3Created, soupElt3Retrieved);

		// Check DB
		Cursor c = null;
		try {
			final SQLiteDatabase db = dbOpenHelper.getWritableDatabase(getEncryptionKey());
			String soupTableName = getSoupTableName(TEST_SOUP);		
			c = DBHelper.getInstance(db).query(db, soupTableName, null, "id ASC", null, null);
            Assert.assertTrue("Expected a soup element", c.moveToFirst());
            Assert.assertEquals("Expected three soup elements", 3, c.getCount());
            Assert.assertEquals("Wrong id", idOf(soupElt1Created), c.getLong(c.getColumnIndex("id")));
            Assert.assertEquals("Wrong created date", soupElt1Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
            Assert.assertEquals("Created date and last modified date should be equal", c.getLong(c.getColumnIndex("created")),  c.getLong(c.getColumnIndex("lastModified")));
			c.moveToNext();
            Assert.assertEquals("Wrong id", idOf(soupElt2Created), c.getLong(c.getColumnIndex("id")));
            Assert.assertEquals("Wrong created date", soupElt2Updated.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
            Assert.assertTrue("Last modified date should be more recent than created date", c.getLong(c.getColumnIndex("created")) < c.getLong(c.getColumnIndex("lastModified")));
			c.moveToNext();
            Assert.assertEquals("Wrong id", idOf(soupElt3Created), c.getLong(c.getColumnIndex("id")));
            Assert.assertEquals("Wrong created date", soupElt3Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
            Assert.assertEquals("Created date and last modified date should be equal", c.getLong(c.getColumnIndex("created")),  c.getLong(c.getColumnIndex("lastModified")));
		}
		finally {
			safeClose(c);
		}
	}
	
	/**
	 * Testing upsert: upsert multiple soup elements and re-upsert one of them, check them all
	 * @throws JSONException
	 */
    @Test
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
		JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt1Upserted, soupElt1Retrieved);
		JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt2Updated, soupElt2Retrieved);
		JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt3Upserted, soupElt3Retrieved);
		
		// Check DB
		Cursor c = null;
		try {
			final SQLiteDatabase db = dbOpenHelper.getWritableDatabase(getEncryptionKey());
			String soupTableName = getSoupTableName(TEST_SOUP);			
			c = DBHelper.getInstance(db).query(db, soupTableName, null, "id ASC", null, null);
            Assert.assertTrue("Expected a soup element", c.moveToFirst());
            Assert.assertEquals("Expected three soup elements", 3, c.getCount());
            Assert.assertEquals("Wrong id", idOf(soupElt1Upserted), c.getLong(c.getColumnIndex("id")));
            Assert.assertEquals("Wrong created date", soupElt1Upserted.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
            Assert.assertEquals("Created date and last modified date should be equal", c.getLong(c.getColumnIndex("created")),  c.getLong(c.getColumnIndex("lastModified")));
			c.moveToNext();
            Assert.assertEquals("Wrong id", idOf(soupElt2Upserted), c.getLong(c.getColumnIndex("id")));
            Assert.assertEquals("Wrong created date", soupElt2Updated.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
            Assert.assertTrue("Last modified date should be more recent than created date", c.getLong(c.getColumnIndex("created")) < c.getLong(c.getColumnIndex("lastModified")));
			c.moveToNext();
            Assert.assertEquals("Wrong id", idOf(soupElt3Upserted), c.getLong(c.getColumnIndex("id")));
            Assert.assertEquals("Wrong created date", soupElt3Upserted.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
            Assert.assertEquals("Created date and last modified date should be equal", c.getLong(c.getColumnIndex("created")),  c.getLong(c.getColumnIndex("lastModified")));
		}
		finally {
			safeClose(c);
		}
	}

	/**
	 * Testing upsert with external id: upsert multiple soup elements and re-upsert one of them, check them all
	 * @throws JSONException
	 */
    @Test
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
		JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt1Upserted, soupElt1Retrieved);
		JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt2Updated, soupElt2Retrieved);
		JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt3Upserted, soupElt3Retrieved);

		// Check DB
		Cursor c = null;
		try {
			final SQLiteDatabase db = dbOpenHelper.getWritableDatabase(getEncryptionKey());
			String soupTableName = getSoupTableName(TEST_SOUP);			
			c = DBHelper.getInstance(db).query(db, soupTableName, null, "id ASC", null, null);
            Assert.assertTrue("Expected a soup element", c.moveToFirst());
            Assert.assertEquals("Expected three soup elements", 3, c.getCount());
            Assert.assertEquals("Wrong id", idOf(soupElt1Upserted), c.getLong(c.getColumnIndex("id")));
            Assert.assertEquals("Wrong created date", soupElt1Upserted.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
            Assert.assertEquals("Created date and last modified date should be equal", c.getLong(c.getColumnIndex("created")),  c.getLong(c.getColumnIndex("lastModified")));
			c.moveToNext();
            Assert.assertEquals("Wrong id", idOf(soupElt2Upserted), c.getLong(c.getColumnIndex("id")));
            Assert.assertEquals("Wrong created date", soupElt2Updated.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
            Assert.assertTrue("Last modified date should be more recent than created date", c.getLong(c.getColumnIndex("created")) < c.getLong(c.getColumnIndex("lastModified")));
			c.moveToNext();
            Assert.assertEquals("Wrong id", idOf(soupElt3Upserted), c.getLong(c.getColumnIndex("id")));
            Assert.assertEquals("Wrong created date", soupElt3Upserted.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
            Assert.assertEquals("Created date and last modified date should be equal", c.getLong(c.getColumnIndex("created")),  c.getLong(c.getColumnIndex("lastModified")));
		}
		finally {
			safeClose(c);
		}
	}

	/**
	 * Testing upsert passing a non-indexed path for the external id (should fail)
	 * @throws JSONException
	 */
    @Test
	public void testUpsertWithNonIndexedExternalId() throws JSONException {
		JSONObject soupElt = new JSONObject("{'key':'ka1', 'value':'va1'}");
		try {
			store.upsert(TEST_SOUP, soupElt, "value");
            Assert.fail("Exception was expected: value is not an indexed field");
		} catch (RuntimeException e) {
            Assert.assertTrue("Wrong exception", e.getMessage().contains("does not have an index"));
		}
	}

	/**
	 * Testing upsert by user-defined external id without value (should fail)
	 * @throws JSONException
	 */
	@Test
	public void testUpsertByUserDefinedExternalIdWithoutValue() throws JSONException {
		JSONObject soupElt = new JSONObject("{'value':'va1'}");
		try {
			store.upsert(TEST_SOUP, soupElt, "key");
			Assert.fail("Exception was expected: value cannot be empty for upsert by user-defined external id");
		} catch (RuntimeException e) {
			Assert.assertTrue("Wrong exception",
					e.getMessage().contains("For upsert with external ID path")
							&& e.getMessage().contains("value cannot be empty for any entries"));
		}
	}

	/**
	 * Testing upsert with an external id that is not unique in the soup
	 * @throws JSONException
	 */
    @Test
	public void testUpsertWithNonUniqueExternalId() throws JSONException {
		JSONObject soupElt1 = new JSONObject("{'key':'ka', 'value':'va1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'ka', 'value':'va2'}");
		JSONObject soupElt3 = new JSONObject("{'key':'ka', 'value':'va3'}");
		JSONObject soupElt1Upserted = store.upsert(TEST_SOUP, soupElt1);
		JSONObject soupElt2Upserted = store.upsert(TEST_SOUP, soupElt2);
		JSONObject soupElt1Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt1Upserted)).getJSONObject(0);
		JSONObject soupElt2Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt2Upserted)).getJSONObject(0);
		JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt1Upserted, soupElt1Retrieved);
		JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt2Upserted, soupElt2Retrieved);
		try {
			store.upsert(TEST_SOUP, soupElt3, "key");
            Assert.fail("Exception was expected: key is not unique in the soup");
		} catch (RuntimeException e) {
            Assert.assertTrue("Wrong exception", e.getMessage().contains("are more than one soup elements"));
		}
	}
	
	/**
	 * Testing retrieve: create multiple soup elements and retrieves them back
	 * @throws JSONException 
	 */
    @Test
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
		JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt1Created, soupElt1Retrieved);
		JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt2Created, soupElt2Retrieved);
		JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt3Created, soupElt3Retrieved);
	}

	/**
	 * Testing delete: create soup elements, delete element by id and check database directly that it is in fact gone
	 * @throws JSONException 
	 */
    @Test
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
		JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt1Created, soupElt1Retrieved);
        Assert.assertEquals("Should be empty", 0, soupElt2Retrieved.length());
		JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt3Created, soupElt3Retrieved);
		
		// Check DB
		Cursor c = null;
		try {
			final SQLiteDatabase db = dbOpenHelper.getWritableDatabase(getEncryptionKey());
			String soupTableName = getSoupTableName(TEST_SOUP);
			c = DBHelper.getInstance(db).query(db, soupTableName, null, "id ASC", null, null);
            Assert.assertTrue("Expected a soup element", c.moveToFirst());
            Assert.assertEquals("Expected two soup elements", 2, c.getCount());
            Assert.assertEquals("Wrong id", idOf(soupElt1Created), c.getLong(c.getColumnIndex("id")));
			c.moveToNext();
            Assert.assertEquals("Wrong id", idOf(soupElt3Created), c.getLong(c.getColumnIndex("id")));
		} finally {
			safeClose(c);
		}
	}

	/**
	 * Testing delete: create soup elements, delete by query and check database directly that deleted entries are in fact gone
	 * @throws JSONException
	 */
    @Test
	public void testDeleteByQuery() throws JSONException {
        tryDeleteByQuery(null, null);
	}

	/**
     * Testing delete: create soup elements, delete by query and check database directly that deleted entries are in fact gone
     * Populate idsDeleted and idsNotDeleted if not null
	 * @param idsDeleted
     * @param idsNotDeleted
     */
	protected void tryDeleteByQuery(List<Long> idsDeleted, List<Long> idsNotDeleted) throws JSONException {
		JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'ka2', 'value':'va2'}");
		JSONObject soupElt3 = new JSONObject("{'key':'ka3', 'value':'va3'}");
		JSONObject soupElt1Created = store.create(TEST_SOUP, soupElt1);
		JSONObject soupElt2Created = store.create(TEST_SOUP, soupElt2);
		JSONObject soupElt3Created = store.create(TEST_SOUP, soupElt3);
		long id1 = soupElt1Created.getLong(SmartStore.SOUP_ENTRY_ID);
		long id2 = soupElt2Created.getLong(SmartStore.SOUP_ENTRY_ID);
		long id3 = soupElt3Created.getLong(SmartStore.SOUP_ENTRY_ID);
		QuerySpec querySpec = QuerySpec.buildRangeQuerySpec(TEST_SOUP, "key", "ka1", "ka2", "key", Order.ascending, 2);
		store.deleteByQuery(TEST_SOUP, querySpec);
		JSONArray soupElt1Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt1Created));
		JSONArray soupElt2Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt2Created));
		JSONObject soupElt3Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt3Created)).getJSONObject(0);
        Assert.assertEquals("Should be empty", 0, soupElt1Retrieved.length());
        Assert.assertEquals("Should be empty", 0, soupElt2Retrieved.length());
		JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt3Created, soupElt3Retrieved);

		// Check DB
		Cursor c = null;
		try {
			final SQLiteDatabase db = dbOpenHelper.getWritableDatabase(getEncryptionKey());
			String soupTableName = getSoupTableName(TEST_SOUP);
			c = DBHelper.getInstance(db).query(db, soupTableName, null, "id ASC", null, null);
            Assert.assertTrue("Expected a soup element", c.moveToFirst());
            Assert.assertEquals("Expected one soup elements", 1, c.getCount());
            Assert.assertEquals("Wrong id", idOf(soupElt3Created), c.getLong(c.getColumnIndex("id")));
		} finally {
			safeClose(c);
		}

        // Populate idsDeleted
        if (idsDeleted != null) {
            idsDeleted.add(id1);
            idsDeleted.add(id2);
        }

        // Populate idsNotDeleted
        if (idsNotDeleted != null) {
            idsNotDeleted.add(id3);
        }
	}

	/**
	 * Testing clear soup: create soup elements, clear soup and check database directly that there are in fact gone
	 * @throws JSONException 
	 */
    @Test
	public void testClearSoup() throws JSONException {
		JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'ka2', 'value':'va2'}");
		JSONObject soupElt3 = new JSONObject("{'key':'ka3', 'value':'va3'}");
		JSONObject soupElt1Created = store.create(TEST_SOUP, soupElt1);
		JSONObject soupElt2Created = store.create(TEST_SOUP, soupElt2);
		JSONObject soupElt3Created = store.create(TEST_SOUP, soupElt3);
		store.clearSoup(TEST_SOUP);
		JSONArray soupElt1Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt1Created));
		JSONArray soupElt2Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt2Created));
		JSONArray soupElt3Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt3Created));
        Assert.assertEquals("Should be empty", 0, soupElt1Retrieved.length());
        Assert.assertEquals("Should be empty", 0, soupElt2Retrieved.length());
        Assert.assertEquals("Should be empty", 0, soupElt3Retrieved.length());

		// Check DB
		Cursor c = null;
		try {
			final SQLiteDatabase db = dbOpenHelper.getWritableDatabase(getEncryptionKey());
			String soupTableName = getSoupTableName(TEST_SOUP);
			c = DBHelper.getInstance(db).query(db, soupTableName, null, "id ASC", null, null);
            Assert.assertFalse("Expected no soup element", c.moveToFirst());
		} finally {
			safeClose(c);
		}
	}
	
	/**
	 * Test query when looking for all elements when soup has string index
	 * @throws JSONException 
	 */
    @Test
	public void testAllQueryWithStringIndex() throws JSONException {
        tryAllQuery(Type.string);
    }

    /**
     * Test query when looking for all elements when soup has json1 index
     * @throws JSONException
     */
    @Test
    public void testAllQueryWithJSON1Index() throws JSONException {
        tryAllQuery(Type.json1);
    }

    /**
     * Test query when looking for all elements
     * @throws JSONException
     */
    public void tryAllQuery(Type type) throws JSONException {

        // Before
        Assert.assertFalse("Soup other_test_soup should not exist", store.hasSoup(OTHER_TEST_SOUP));

        // Register
        store.registerSoup(OTHER_TEST_SOUP, new IndexSpec[] {new IndexSpec("key", type)});
        Assert.assertTrue("Register soup call failed", store.hasSoup(OTHER_TEST_SOUP));
		JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1', 'otherValue':'ova1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'ka2', 'value':'va2', 'otherValue':'ova2'}");
		JSONObject soupElt3 = new JSONObject("{'key':'ka3', 'value':'va3', 'otherValue':'ova3'}");
		JSONObject soupElt1Created = store.create(OTHER_TEST_SOUP, soupElt1);
		JSONObject soupElt2Created = store.create(OTHER_TEST_SOUP, soupElt2);
		JSONObject soupElt3Created = store.create(OTHER_TEST_SOUP, soupElt3);

		// Query all - small page
		runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP,
                QuerySpec.buildAllQuerySpec(OTHER_TEST_SOUP, "key", Order.ascending, 2),
                0, false, "SCAN", soupElt1Created, soupElt2Created);

		// Query all - next small page
		runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP,
                QuerySpec.buildAllQuerySpec(OTHER_TEST_SOUP, "key", Order.ascending, 2),
                1, false, "SCAN", soupElt3Created);

		// Query all - large page
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP,
                QuerySpec.buildAllQuerySpec(OTHER_TEST_SOUP, "key", Order.ascending, 10),
                0, false, "SCAN", soupElt1Created, soupElt2Created, soupElt3Created);

		// Query all with select paths
		runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP,
                QuerySpec.buildAllQuerySpec(OTHER_TEST_SOUP, new String[]{"key"}, "key", Order.ascending, 10),
                0, type != Type.json1, "SCAN", new JSONArray("['ka1']"),  new JSONArray("['ka2']"),  new JSONArray("['ka3']"));
	}
	
	/**
	 * Test query when looking for a specific element with a string index
	 * @throws JSONException 
	 */
    @Test
	public void testExactQueryWithStringIndex() throws JSONException {
        tryExactQuery(Type.string);
	}

    /**
     * Test query when looking for a specific element with a json1 index
     * @throws JSONException
     */
    @Test
    public void testExactQueryWithJSON1Index() throws JSONException {
        tryExactQuery(Type.json1);
    }

    private void tryExactQuery(Type type) throws JSONException {

        // Before
        Assert.assertFalse("Soup other_test_soup should not exist", store.hasSoup(OTHER_TEST_SOUP));

        // Register
        store.registerSoup(OTHER_TEST_SOUP, new IndexSpec[] {new IndexSpec("key", type)});
        Assert.assertTrue("Register soup call failed", store.hasSoup(OTHER_TEST_SOUP));
        JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1', 'otherValue':'ova1'}");
        JSONObject soupElt2 = new JSONObject("{'key':'ka2', 'value':'va2', 'otherValue':'ova2'}");
        JSONObject soupElt3 = new JSONObject("{'key':'ka3', 'value':'va3', 'otherValue':'ova3'}");
        store.create(OTHER_TEST_SOUP, soupElt1);
        JSONObject soupElt2Created= store.create(OTHER_TEST_SOUP, soupElt2);
        store.create(OTHER_TEST_SOUP, soupElt3);

        // Exact match
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP,
                QuerySpec.buildExactQuerySpec(OTHER_TEST_SOUP, "key", "ka2", null, null, 10),
                0, false, "SEARCH", soupElt2Created);
    }

    /**
     * Query test looking for a range of elements (with ascending or descending ordering) with a string index
     * @throws JSONException
     */
    @Test
    public void testRangeQueryWithStringIndex() throws JSONException {
        tryRangeQuery(Type.string);
    }

	/**
	 * Query test looking for a range of elements (with ascending or descending ordering) with a json1 index
	 * @throws JSONException 
	 */
    @Test
    public void testRangeQueryWithJSON1Index() throws JSONException {
        tryRangeQuery(Type.json1);
    }

	private void tryRangeQuery(Type type) throws JSONException {

        // Before
        Assert.assertFalse("Soup other_test_soup should not exist", store.hasSoup(OTHER_TEST_SOUP));

        // Register
        store.registerSoup(OTHER_TEST_SOUP, new IndexSpec[] {new IndexSpec("key", type)});
        Assert.assertTrue("Register soup call failed", store.hasSoup(OTHER_TEST_SOUP));
        JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1', 'otherValue':'ova1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'ka2', 'value':'va2', 'otherValue':'ova2'}");
		JSONObject soupElt3 = new JSONObject("{'key':'ka3', 'value':'va3', 'otherValue':'ova3'}");
		
		store.create(OTHER_TEST_SOUP, soupElt1);
		JSONObject soupElt2Created = store.create(OTHER_TEST_SOUP, soupElt2);
		JSONObject soupElt3Created = store.create(OTHER_TEST_SOUP, soupElt3);

		// Range query
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP,
                QuerySpec.buildRangeQuerySpec(OTHER_TEST_SOUP, "key", "ka2", "ka3", "key", Order.ascending, 10),
                0, false, "SEARCH", soupElt2Created, soupElt3Created);

		// Range query - descending order
		runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP,
                QuerySpec.buildRangeQuerySpec(OTHER_TEST_SOUP, "key", "ka2", "ka3", "key", Order.descending, 10),
                0, false, "SEARCH", soupElt3Created, soupElt2Created);

		// Range query with select paths
		runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP,
                QuerySpec.buildRangeQuerySpec(OTHER_TEST_SOUP, new String[]{"key"}, "key", "ka2", "ka3", "key", Order.descending, 10),
                0, type != Type.json1, "SEARCH", new JSONArray("['ka3']"), new JSONArray("['ka2']"));
	}

	/**
	 * Query test looking using like (with ascending or descending ordering) and a string index
	 * @throws JSONException 
	 */
    @Test
    public void testLikeQueryWithStringIndex() throws JSONException {
        tryLikeQuery(Type.string);
    }

    /**
     * Query test looking using like (with ascending or descending ordering) and a json1 index
     * @throws JSONException
     */
    @Test
    public void testLikeQueryWithJSON1Index() throws JSONException {
        tryLikeQuery(Type.json1);
    }

	private void tryLikeQuery(Type type) throws JSONException {
        Assert.assertFalse("Soup other_test_soup should not exist", store.hasSoup(OTHER_TEST_SOUP));
        store.registerSoup(OTHER_TEST_SOUP, new IndexSpec[] {new IndexSpec("key", type)});
        Assert.assertTrue("Register soup call failed", store.hasSoup(OTHER_TEST_SOUP));
		JSONObject soupElt1 = new JSONObject("{'key':'abcd', 'value':'va1', 'otherValue':'ova1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'bbcd', 'value':'va2', 'otherValue':'ova2'}");
		JSONObject soupElt3 = new JSONObject("{'key':'abcc', 'value':'va3', 'otherValue':'ova3'}");
		JSONObject soupElt4 = new JSONObject("{'key':'defg', 'value':'va4', 'otherValue':'ova3'}");
		JSONObject soupElt1Created = store.create(OTHER_TEST_SOUP, soupElt1);
		JSONObject soupElt2Created = store.create(OTHER_TEST_SOUP, soupElt2);
		JSONObject soupElt3Created = store.create(OTHER_TEST_SOUP, soupElt3);
		store.create(OTHER_TEST_SOUP, soupElt4);

        // Like query (starts with)
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP, QuerySpec.buildLikeQuerySpec(OTHER_TEST_SOUP, "key", "abc%", "key", Order.ascending, 10), 0, false, "SCAN", soupElt3Created, soupElt1Created);

        // Like query (ends with)
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP, QuerySpec.buildLikeQuerySpec(OTHER_TEST_SOUP, "key", "%bcd", "key", Order.ascending, 10), 0, false, "SCAN", soupElt1Created, soupElt2Created);

        // Like query (starts with) - descending order
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP, QuerySpec.buildLikeQuerySpec(OTHER_TEST_SOUP, "key", "abc%", "key", Order.descending, 10), 0, false, "SCAN", soupElt1Created, soupElt3Created);

        // Like query (ends with) - descending order
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP, QuerySpec.buildLikeQuerySpec(OTHER_TEST_SOUP, "key", "%bcd", "key", Order.descending, 10), 0, false, "SCAN", soupElt2Created, soupElt1Created);

        // Like query (contains)
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP, QuerySpec.buildLikeQuerySpec(OTHER_TEST_SOUP, "key", "%bc%", "key", Order.ascending, 10), 0, false, "SCAN", soupElt3Created, soupElt1Created, soupElt2Created);

        // Like query (contains) - descending order
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP, QuerySpec.buildLikeQuerySpec(OTHER_TEST_SOUP, "key", "%bc%", "key", Order.descending, 10), 0, false, "SCAN", soupElt2Created, soupElt1Created, soupElt3Created);

		// Like query (contains) with select paths
		runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP,
                QuerySpec.buildLikeQuerySpec(OTHER_TEST_SOUP, new String[] {"key"}, "key", "%bc%", "key", Order.descending, 10), 0, type != Type.json1, "SCAN",
				new JSONArray("['bbcd']"), new JSONArray("['abcd']"), new JSONArray("['abcc']"));
	}

	/**
	 * Test query against soup with special characters when soup has string index
	 * @throws JSONException
	 */
	@Test
	public void testQueryDataWithSpecialCharactersWithStringIndex() throws JSONException {
		tryQueryDataWithSpecialCharacters(Type.string);
	}

	/**
	 * Test query against soup with special characters when soup has json1 index
	 * @throws JSONException
	 */
	@Test
	public void testQueryDataWithSpecialCharactersWithJSON1Index() throws JSONException {
		tryQueryDataWithSpecialCharacters(Type.json1);
	}

	private void tryQueryDataWithSpecialCharacters(Type type) throws JSONException {
		// Before
		Assert.assertFalse("Soup other_test_soup should not exist", store.hasSoup(OTHER_TEST_SOUP));

		// Register
		store.registerSoup(OTHER_TEST_SOUP, new IndexSpec[] {new IndexSpec("key", type), new IndexSpec("value", type)});
		Assert.assertTrue("Register soup call failed", store.hasSoup(OTHER_TEST_SOUP));

		StringBuffer value = new StringBuffer();
		for (int i=1; i<1000; i++) {
			value.append(new Character((char) i));
		}
		String valueForAbcd = "abcd" + value;
		String valueForDefg = "defg" + value;

		// Populate soup
		JSONObject soupElt1 = new JSONObject();
		soupElt1.put("key", "abcd");
		soupElt1.put("value", valueForAbcd);

		JSONObject soupElt2 = new JSONObject("{'key':'defg'}");
		soupElt2.put("key", "defg");
		soupElt2.put("value", valueForDefg);

		store.create(OTHER_TEST_SOUP, soupElt1);
		store.create(OTHER_TEST_SOUP, soupElt2);

		// Smart query
		String sql = String.format("SELECT {%1$s:value} FROM {%1$s} ORDER BY {%1$s:key}", OTHER_TEST_SOUP);
		runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP, QuerySpec.buildSmartQuerySpec(sql, 10), 0, false, null,
				new JSONArray(Collections.singletonList(valueForAbcd)), new JSONArray(Collections.singletonList(valueForDefg)));
	}

    protected void runQueryCheckResultsAndExplainPlan(String soupName, QuerySpec querySpec, int page, boolean covering, String expectedDbOperation, JSONObject... expectedResults) throws JSONException {

        // Run query
        JSONArray result = store.query(querySpec, page);

        // Check results
        Assert.assertEquals("Wrong number of results", expectedResults.length, result.length());
        for (int i=0; i<expectedResults.length; i++) {
            JSONTestHelper.assertSameJSON("Wrong result for query", expectedResults[i], result.getJSONObject(i));
        }

        // Check explain plan and make sure index was used
        checkExplainQueryPlan(soupName, 0, covering, expectedDbOperation);
    }

	private void runQueryCheckResultsAndExplainPlan(String soupName, QuerySpec querySpec, int page, boolean covering, String expectedDbOperation, JSONArray... expectedRows) throws JSONException {

        // Run query
		JSONArray result = store.query(querySpec, page);

		// Check results
        Assert.assertEquals("Wrong number of rows", expectedRows.length, result.length());
		for (int i = 0; i < expectedRows.length; i++) {
			JSONTestHelper.assertSameJSON("Wrong result for query", expectedRows[i], result.getJSONArray(i));
		}

		// Check explain plan and make sure index was used
		if (expectedDbOperation != null) {
			checkExplainQueryPlan(soupName, 0, covering, expectedDbOperation);
		}
	}

	/**
	 * Test smart sql returning entire soup elements (i.e. select {soup:_soup} from {soup})
	 * @throws JSONException
	 */
    @Test
	public void testSelectUnderscoreSoup() throws JSONException {

	    // Create soup elements
		JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'ka2', 'value':'va2'}");
		JSONObject soupElt3 = new JSONObject("{'key':'ka3', 'value':'va3'}");
		JSONObject soupElt4 = new JSONObject("{'key':'ka4', 'value':'va4'}");
		JSONObject soupElt1Created = store.create(TEST_SOUP, soupElt1);
		JSONObject soupElt2Created = store.create(TEST_SOUP, soupElt2);
		JSONObject soupElt3Created = store.create(TEST_SOUP, soupElt3);
		JSONObject soupElt4Created = store.create(TEST_SOUP, soupElt4);
		final String smartSql = "SELECT {" + TEST_SOUP + ":_soup} FROM {" + TEST_SOUP + "} ORDER BY {" + TEST_SOUP + ":key}";
		final QuerySpec querySpec = QuerySpec.buildSmartQuerySpec(smartSql, 25);
		final JSONArray result = store.query(querySpec, 0);
        Assert.assertNotNull("Result should not be null", result);
        Assert.assertEquals("Four results expected", 4, result.length());
		JSONTestHelper.assertSameJSON("Wrong result for query - row 0", new JSONArray(new JSONObject[] { soupElt1Created}), result.get(0));
        JSONTestHelper.assertSameJSON("Wrong result for query - row 1", new JSONArray(new JSONObject[] { soupElt2Created}), result.get(1));
        JSONTestHelper.assertSameJSON("Wrong result for query - row 2", new JSONArray(new JSONObject[] { soupElt3Created}), result.get(2));
        JSONTestHelper.assertSameJSON("Wrong result for query - row 3", new JSONArray(new JSONObject[] { soupElt4Created}), result.get(3));
	}

	/**
	 * Test smart sql returning entire soup elements from multiple soups
	 * @throws JSONException
	 */
	@Test
	public void testSelectUnderscoreSoupFromMultipleSoups() throws JSONException {

		JSONObject soupElt1 = new JSONObject("{'key':'ka', 'value':'va'}");
		JSONObject soupElt1Created = store.create(TEST_SOUP, soupElt1);

		store.registerSoup(OTHER_TEST_SOUP, new IndexSpec[] {new IndexSpec("key", Type.string)});
		JSONObject soupElt2 = new JSONObject("{'key':'abcd', 'value':'va1', 'otherValue':'ova1'}");
		JSONObject soupElt2Created = store.create(OTHER_TEST_SOUP, soupElt2);

		final String smartSql = "SELECT {" + TEST_SOUP + ":_soup}, {" + OTHER_TEST_SOUP + ":_soup} FROM {" + TEST_SOUP + "}, {" + OTHER_TEST_SOUP + "}";
		final QuerySpec querySpec = QuerySpec.buildSmartQuerySpec(smartSql, 25);
		final JSONArray result = store.query(querySpec, 0);
		Assert.assertNotNull("Result should not be null", result);
		Assert.assertEquals("One row expected", 1, result.length());
		JSONArray firstRow = result.getJSONArray(0);
		JSONTestHelper.assertSameJSON("Wrong result for query - row 0 - first soup elt", soupElt1Created, firstRow.getJSONObject(0));
		JSONTestHelper.assertSameJSON("Wrong result for query - row 0 - second soup elt", soupElt2Created, firstRow.getJSONObject(1));
	}

	/**
	 *  Test smart sql select with null value in string indexed field
	 *  @throws JSONException
	 */
    @Test
	public void testSelectWithNullInStringIndexedField() throws JSONException {
		trySelectWithNullInIndexedField(Type.string);
	}

	/**
	 *  Test smart sql select with null value in json1 indexed field
	 *  @throws JSONException
	 */
    @Test
	public void testSelectWithNullInJSON1IndexedField() throws JSONException {
		trySelectWithNullInIndexedField(Type.json1);
	}

	private void trySelectWithNullInIndexedField(Type type) throws JSONException {

		// Before
        Assert.assertFalse("Soup third_test_soup should not exist", store.hasSoup(THIRD_TEST_SOUP));

		// Register
		registerSoup(store, THIRD_TEST_SOUP, new IndexSpec[] { new IndexSpec("key", type), new IndexSpec("value", type) });
        Assert.assertTrue("Register soup call failed", store.hasSoup(THIRD_TEST_SOUP));

		// Upsert
		JSONObject soupElt1 = new JSONObject("{'key':'ka', 'value':null}");
		JSONObject soupElt1Upserted = store.upsert(THIRD_TEST_SOUP, soupElt1);

		// Smart sql
		final String smartSql = "SELECT {" + THIRD_TEST_SOUP + ":value}, {" + THIRD_TEST_SOUP + ":key}  FROM {" + THIRD_TEST_SOUP + "} WHERE {" + THIRD_TEST_SOUP + ":key} = 'ka'";
		final QuerySpec querySpec = QuerySpec.buildSmartQuerySpec(smartSql, 25);
		final JSONArray result = store.query(querySpec, 0);

		// Check
        Assert.assertNotNull("Result should not be null", result);
        Assert.assertEquals("One result expected", 1, result.length());
		JSONTestHelper.assertSameJSON("Wrong result for query", new JSONArray("[[null, 'ka']]"), result);
	}

	/**
	 * Test upsert soup element with null value in string indexed field
	 * @throws JSONException 
	 */
    @Test
    public void testUpsertWithNullInStringIndexedField() throws JSONException {
        tryUpsertWithNullInIndexedField(Type.string);
    }

    /**
     * Test upsert soup element with null value in json1 indexed field
     * @throws JSONException
     */
    @Test
    public void testUpsertWithNullInJSON1IndexedField() throws JSONException {
        tryUpsertWithNullInIndexedField(Type.json1);
    }

	private void tryUpsertWithNullInIndexedField(Type type) throws JSONException {

		// Before
        Assert.assertFalse("Soup third_test_soup should not exist", store.hasSoup(THIRD_TEST_SOUP));

		// Register
		registerSoup(store, THIRD_TEST_SOUP, new IndexSpec[] { new IndexSpec("key", type), new IndexSpec("value", type) });
        Assert.assertTrue("Register soup call failed", store.hasSoup(THIRD_TEST_SOUP));

		// Upsert
		JSONObject soupElt1 = new JSONObject("{'key':'ka', 'value':null}");
		JSONObject soupElt1Upserted = store.upsert(THIRD_TEST_SOUP, soupElt1);
		
		// Check
		JSONObject soupElt1Retrieved = store.retrieve(THIRD_TEST_SOUP, idOf(soupElt1Upserted)).getJSONObject(0);		
		JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt1Upserted, soupElt1Retrieved);
	}

	/**
	 * Test to verify an aggregate query on floating point values indexed as floating.
	 *
	 * @throws JSONException
	 */
    @Test
    public void testAggregateQueryOnFloatingIndexedField() throws JSONException {
        tryAggregateQueryOnIndexedField(Type.floating);
    }

    /**
     * Test to verify an aggregate query on floating point values indexed as JSON1.
     *
     * @throws JSONException
     */
    @Test
    public void testAggregateQueryOnJSON1IndexedField() throws JSONException {
        tryAggregateQueryOnIndexedField(Type.json1);
    }

	private void tryAggregateQueryOnIndexedField(Type type) throws JSONException {
		final JSONObject soupElt1 = new JSONObject("{'amount':10.2}");
		final JSONObject soupElt2 = new JSONObject("{'amount':9.9}");
		final IndexSpec[] indexSpecs = { new IndexSpec("amount", type) };
		registerSoup(store, FOURTH_TEST_SOUP, indexSpecs);
        Assert.assertTrue("Soup " + FOURTH_TEST_SOUP + " should have been created", store.hasSoup(FOURTH_TEST_SOUP));
		store.upsert(FOURTH_TEST_SOUP, soupElt1);
		store.upsert(FOURTH_TEST_SOUP, soupElt2);
		final String smartSql = "SELECT SUM({" + FOURTH_TEST_SOUP + ":amount}) FROM {" + FOURTH_TEST_SOUP + "}";
		final QuerySpec querySpec = QuerySpec.buildSmartQuerySpec(smartSql, 1);
		final JSONArray result = store.query(querySpec, 0);
        Assert.assertNotNull("Result should not be null", result);
        Assert.assertEquals("One result expected", 1, result.length());
        Assert.assertEquals("Incorrect result received", 20.1, result.getJSONArray(0).getDouble(0), 0);
		store.dropSoup(FOURTH_TEST_SOUP);
        Assert.assertFalse("Soup " + FOURTH_TEST_SOUP + " should have been deleted", store.hasSoup(FOURTH_TEST_SOUP));
	}

    /**
     * Test to verify an count query for a query with group by when the soup uses string indexes.
     *
     * @throws JSONException
     */
    @Test
    public void testCountQueryWithGroupByUsingStringIndexes() throws JSONException {
        tryCountQueryWithGroupBy(Type.string);
    }

    /**
     * Test to verify an count query for a query with group by when the soup uses json1 indexes.
     *
     * @throws JSONException
     */
    @Test
    public void testCountQueryWithGroupByUsingJSON1Indexes() throws JSONException {
        tryCountQueryWithGroupBy(Type.json1);
    }

    private void tryCountQueryWithGroupBy(Type type) throws JSONException {

        // Before
        Assert.assertFalse("Soup third_test_soup should not exist", store.hasSoup(THIRD_TEST_SOUP));

        // Register
        registerSoup(store, THIRD_TEST_SOUP, new IndexSpec[] { new IndexSpec("key", type), new IndexSpec("value", type) });
        Assert.assertTrue("Register soup call failed", store.hasSoup(THIRD_TEST_SOUP));
        JSONObject soupElt1 = new JSONObject("{'key':'a', 'value':'va1'}");
        JSONObject soupElt2 = new JSONObject("{'key':'b', 'value':'va1'}");
        JSONObject soupElt3 = new JSONObject("{'key':'c', 'value':'va2'}");
        JSONObject soupElt4 = new JSONObject("{'key':'d', 'value':'va3'}");
        JSONObject soupElt5 = new JSONObject("{'key':'e', 'value':'va3'}");
        store.create(THIRD_TEST_SOUP, soupElt1);
        store.create(THIRD_TEST_SOUP, soupElt2);
        store.create(THIRD_TEST_SOUP, soupElt3);
		store.create(THIRD_TEST_SOUP, soupElt4);
        store.create(THIRD_TEST_SOUP, soupElt5);
        final String smartSql = "SELECT {" + THIRD_TEST_SOUP + ":value}, count(*) FROM {" + THIRD_TEST_SOUP + "} GROUP BY {" + THIRD_TEST_SOUP + ":value} ORDER BY {" + THIRD_TEST_SOUP + ":value}";
        final QuerySpec querySpec = QuerySpec.buildSmartQuerySpec(smartSql, 25);
        final JSONArray result = store.query(querySpec, 0);
        Assert.assertNotNull("Result should not be null", result);
        Assert.assertEquals("Three results expected", 3, result.length());
        JSONTestHelper.assertSameJSON("Wrong result for query", new JSONArray("[['va1', 2], ['va2', 1], ['va3', 2]]"), result);
        final int count = store.countQuery(querySpec);
        Assert.assertEquals("Incorrect count query", "SELECT count(*) FROM (" + smartSql + ")", querySpec.countSmartSql);
        Assert.assertEquals("Incorrect count", 3, count);
    }

	/**
	 * Test to verify proper indexing of integer and longs
	 */
    @Test
	public void testIntegerIndexedField() throws JSONException {
		registerSoup(store, FOURTH_TEST_SOUP, new IndexSpec[] { new IndexSpec("amount", Type.integer) });
		tryNumber(Type.integer, Integer.MIN_VALUE, Integer.MIN_VALUE);
		tryNumber(Type.integer, Integer.MAX_VALUE, Integer.MAX_VALUE);
		tryNumber(Type.integer, Long.MIN_VALUE, Long.MIN_VALUE);
		tryNumber(Type.integer, Long.MIN_VALUE, Long.MIN_VALUE);
		tryNumber(Type.integer, Double.MIN_VALUE, (long) Double.MIN_VALUE);
		tryNumber(Type.integer, Double.MAX_VALUE, (long) Double.MAX_VALUE);
	}

	/**
	 * Test to verify proper indexing of doubles
	 */
    @Test
	public void testFloatingIndexedField() throws JSONException {
		registerSoup(store, FOURTH_TEST_SOUP, new IndexSpec[] { new IndexSpec("amount", Type.floating) });
		tryNumber(Type.floating, Integer.MIN_VALUE, (double) Integer.MIN_VALUE);
		tryNumber(Type.floating, Integer.MAX_VALUE, (double) Integer.MAX_VALUE);
		tryNumber(Type.floating, Long.MIN_VALUE, (double) Long.MIN_VALUE);
		tryNumber(Type.floating, Long.MIN_VALUE, (double) Long.MIN_VALUE);
		tryNumber(Type.floating, Double.MIN_VALUE, Double.MIN_VALUE);
		tryNumber(Type.floating, Double.MAX_VALUE, Double.MAX_VALUE);
	}

	/**
	 * Helper method for testIntegerIndexedField and testFloatingIndexedField
	 * Insert soup element with number and check db 
	 * @param fieldType
	 * @param valueIn
	 * @param valueOut
	 * @throws JSONException 
	 */
	private void tryNumber(Type fieldType, Number valueIn, Number valueOut) throws JSONException {
		JSONObject elt = new JSONObject();
		elt.put("amount", valueIn);
		Long id = store.upsert(FOURTH_TEST_SOUP, elt).getLong(SmartStore.SOUP_ENTRY_ID);
		Cursor c = null;
		try {
			final SQLiteDatabase db = dbOpenHelper.getWritableDatabase(getEncryptionKey());
			String soupTableName = getSoupTableName(FOURTH_TEST_SOUP);
			String amountColumnName = store.getSoupIndexSpecs(FOURTH_TEST_SOUP)[0].columnName;
			c = DBHelper.getInstance(db).query(db, soupTableName, new String[] { amountColumnName }, null, null, "id = " + id);
            Assert.assertTrue("Expected a soup element", c.moveToFirst());
            Assert.assertEquals("Expected one soup element", 1, c.getCount());
			if (fieldType == Type.integer)
                Assert.assertEquals("Not the value expected", valueOut.longValue(), c.getLong(0));
			else if (fieldType == Type.floating)
                Assert.assertEquals("Not the value expected", valueOut.doubleValue(), c.getDouble(0), 0);
		} finally {
			safeClose(c);
		}
	}
	
	/**
	 * Test using smart sql to retrieve integer indexed fields
	 */
    @Test
	public void testIntegerIndexedFieldWithSmartSql() throws JSONException {
		registerSoup(store, FOURTH_TEST_SOUP, new IndexSpec[] { new IndexSpec("amount", Type.integer) });
		tryNumberWithSmartSql(Type.integer, Integer.MIN_VALUE, Integer.MIN_VALUE);
		tryNumberWithSmartSql(Type.integer, Integer.MAX_VALUE, Integer.MAX_VALUE);
		tryNumberWithSmartSql(Type.integer, Long.MIN_VALUE, Long.MIN_VALUE);
		tryNumberWithSmartSql(Type.integer, Long.MIN_VALUE, Long.MIN_VALUE);
		tryNumberWithSmartSql(Type.integer, Double.MIN_VALUE, (long) Double.MIN_VALUE);
		tryNumberWithSmartSql(Type.integer, Double.MAX_VALUE, (long) Double.MAX_VALUE);
	}

	/**
	 * Test using smart sql to retrieve indexed fields holding doubles
	 * NB smart sql will return a long when querying a double field that contains a long
	 */
    @Test
	public void testFloatingIndexedFieldWithSmartSql() throws JSONException {
		registerSoup(store, FOURTH_TEST_SOUP, new IndexSpec[] { new IndexSpec("amount", Type.floating) });
		tryNumberWithSmartSql(Type.floating, Integer.MIN_VALUE, Integer.MIN_VALUE);
		tryNumberWithSmartSql(Type.floating, Integer.MAX_VALUE, Integer.MAX_VALUE);
		tryNumberWithSmartSql(Type.floating, Long.MIN_VALUE, Long.MIN_VALUE);
		tryNumberWithSmartSql(Type.floating, Long.MIN_VALUE, Long.MIN_VALUE);
		tryNumberWithSmartSql(Type.floating, Double.MIN_VALUE, Double.MIN_VALUE);
		tryNumberWithSmartSql(Type.floating, Double.MAX_VALUE, Double.MAX_VALUE);
	}

    /**
     * Test using smart sql to retrieve number fields indexed with json1
     */
    @Test
    public void testNumberFieldWithJSON1IndexWithSmartSql() throws JSONException {
        store.registerSoup(FOURTH_TEST_SOUP, new IndexSpec[] { new IndexSpec("amount", Type.json1) });
        tryNumberWithSmartSql(Type.integer, Integer.MIN_VALUE, Integer.MIN_VALUE);
        tryNumberWithSmartSql(Type.integer, Integer.MAX_VALUE, Integer.MAX_VALUE);
        tryNumberWithSmartSql(Type.integer, Long.MIN_VALUE, Long.MIN_VALUE);
        tryNumberWithSmartSql(Type.integer, Long.MIN_VALUE, Long.MIN_VALUE);
        tryNumberWithSmartSql(Type.floating, Math.PI, Math.PI);
    }

    /**
	 * Helper method for testIntegerIndexedFieldWithSmartSql and testFloatingIndexedFieldWithSmartSql
	 * Insert soup element with number and retrieve it back using smartsql
	 * @param fieldType
	 * @param valueIn
	 * @param valueOut
	 * @throws JSONException 
	 */
	private void tryNumberWithSmartSql(Type fieldType, Number valueIn, Number valueOut) throws JSONException {
		String smartSql = "SELECT {" + FOURTH_TEST_SOUP + ":amount} FROM {" + FOURTH_TEST_SOUP + "} WHERE {" + FOURTH_TEST_SOUP + ":_soupEntryId} = ";
		JSONObject elt = new JSONObject();
		elt.put("amount", valueIn);
		Long id = store.upsert(FOURTH_TEST_SOUP, elt).getLong(SmartStore.SOUP_ENTRY_ID);
		Number actualValueOut = (Number) store.query(QuerySpec.buildSmartQuerySpec(smartSql + id, 1), 0).getJSONArray(0).get(0);
		if (fieldType == Type.integer)
            Assert.assertEquals("Not the value expected", valueOut.longValue(), actualValueOut.longValue());
		else if (fieldType == Type.floating)
            Assert.assertEquals("Not the value expected", valueOut.doubleValue(), actualValueOut.doubleValue(), 0);
	}

	/**
	 * Test for getDatabaseSize
	 * 
	 * @throws JSONException
	 */
    @Test
	public void testGetDatabaseSize() throws JSONException {
		int initialSize = store.getDatabaseSize();
		for (int i=0; i<100; i++) {
			JSONObject soupElt = new JSONObject("{'key':'abcd" + i + "', 'value':'va" + i + "', 'otherValue':'ova" + i + "'}");
			store.create(TEST_SOUP, soupElt);
		}
        Assert.assertTrue("Database should be larger now", store.getDatabaseSize() > initialSize);
	}

	/**
	 * Test registerSoup with json1 indexes
     * Register soup with multiple json1 indexes and a string index, check the underlying table and indexes in the database
	 */
    @Test
    public void testRegisterSoupWithJSON1() throws JSONException {
        Assert.assertFalse("Soup other_test_soup should not exist", store.hasSoup(OTHER_TEST_SOUP));
        store.registerSoup(OTHER_TEST_SOUP, new IndexSpec[] {new IndexSpec("lastName", Type.json1), new IndexSpec("address.city", Type.json1), new IndexSpec("address.zipcode", Type.string)});
        Assert.assertTrue("Register soup call failed", store.hasSoup(OTHER_TEST_SOUP));

        // Check columns of soup table
        String soupTableName = getSoupTableName(OTHER_TEST_SOUP);
        checkColumns(soupTableName, Arrays.asList(new String[] {"id", "soup", "created", "lastModified", soupTableName + "_2"}));

        // Check soup indexes
        final IndexSpec[] indexSpecs = store.getSoupIndexSpecs(OTHER_TEST_SOUP);
        Assert.assertEquals("Wrong path", "lastName", indexSpecs[0].path);
        Assert.assertEquals("Wrong type", Type.json1, indexSpecs[0].type);
        Assert.assertEquals("Wrong column name", "json_extract(soup, '$.lastName')", indexSpecs[0].columnName);
        Assert.assertEquals("Wrong path", "address.city", indexSpecs[1].path);
        Assert.assertEquals("Wrong type", Type.json1, indexSpecs[1].type);
        Assert.assertEquals("Wrong column name", "json_extract(soup, '$.address.city')", indexSpecs[1].columnName);
        Assert.assertEquals("Wrong path", "address.zipcode", indexSpecs[2].path);
        Assert.assertEquals("Wrong type", Type.string, indexSpecs[2].type);
        Assert.assertEquals("Wrong column name", soupTableName + "_2", indexSpecs[2].columnName);

        // Check db indexes
        checkDatabaseIndexes(soupTableName, Arrays.asList(new String[] {
            "CREATE INDEX " + soupTableName + "_0_idx on " + soupTableName + " ( json_extract(soup, '$.lastName') )",
            "CREATE INDEX " + soupTableName + "_1_idx on " + soupTableName + " ( json_extract(soup, '$.address.city') )",
            "CREATE INDEX " + soupTableName + "_2_idx on " + soupTableName + " ( " + soupTableName + "_2 )",
            "CREATE INDEX " + soupTableName + "_created_idx on " + soupTableName + " ( created )",
            "CREATE INDEX " + soupTableName + "_lastModified_idx on " + soupTableName + " ( lastModified )"
        }));
    }

	/**
     * Testing Delete: create multiple soup elements and alter the soup, after that delete a entry, then check them all
     * @throws JSONException
     */
    @Test
    public void testDeleteAgainstChangedSoup() throws JSONException {

        //create a new soup with multiple entries
        JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1'}");
        JSONObject soupElt2 = new JSONObject("{'key':'ka2', 'value':'va2'}");
        JSONObject soupElt3 = new JSONObject("{'key':'ka3', 'value':'va3'}");
        JSONObject soupElt4 = new JSONObject("{'key':'ka4', 'value':'va4'}");
        JSONObject soupElt1Created = store.create(TEST_SOUP, soupElt1);
        JSONObject soupElt2Created = store.create(TEST_SOUP, soupElt2);
        JSONObject soupElt3Created = store.create(TEST_SOUP, soupElt3);
        JSONObject soupElt4Created = store.create(TEST_SOUP, soupElt4);

        //CASE 1: index spec from key to value
        tryAllQueryOnChangedSoupWithUpdate(TEST_SOUP, soupElt2Created, "value",
                new IndexSpec[]{new IndexSpec("value", Type.string)},
                soupElt1Created, soupElt3Created, soupElt4Created);

        //CASE 2: index spec from string to json1
        tryAllQueryOnChangedSoupWithUpdate(TEST_SOUP, soupElt4Created, "key",
                new IndexSpec[]{new IndexSpec("key", Type.json1)},
                soupElt1Created, soupElt3Created);

        //CASE 3: add a index spec field
        tryAllQueryOnChangedSoupWithUpdate(TEST_SOUP, soupElt4Created, "key",
                new IndexSpec[]{new IndexSpec("key", Type.json1), new IndexSpec("value", Type.string)},
                soupElt1Created, soupElt3Created);
    }

    protected void tryAllQueryOnChangedSoupWithUpdate(String soupName, JSONObject deletedEntry, String orderPath,
                                                    IndexSpec[] newIndexSpecs, JSONObject... expectedResults) throws JSONException {

        //alert the soup
        store.alterSoup(soupName, newIndexSpecs, true);

        //delete an entry
        store.delete(soupName, idOf(deletedEntry));

        // Query all - small page
        runQueryCheckResultsAndExplainPlan(soupName,
                QuerySpec.buildAllQuerySpec(soupName, orderPath, Order.ascending, 5),
                0, false, "SCAN", expectedResults);
    }

    /**
     * Testing Upsert: create multiple soup elements and alter the soup, after that upsert a entry, then check them all
     * @throws JSONException
     */
    @Test
    public void testUpsertAgainstChangedSoup() throws JSONException {

        //create a new soup with multiple entries
        JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1'}");
        JSONObject soupElt2 = new JSONObject("{'key':'ka2', 'value':'va2'}");
        JSONObject soupElt3 = new JSONObject("{'key':'ka3', 'value':'va3'}");
        JSONObject soupElt1Created = store.create(TEST_SOUP, soupElt1);
        JSONObject soupElt2Created = store.create(TEST_SOUP, soupElt2);
        JSONObject soupElt3Created = store.create(TEST_SOUP, soupElt3);
        JSONObject soupElt1ForUpsert = new JSONObject("{'key':'ka1u', 'value':'va1u'}");
        JSONObject soupElt2ForUpsert = new JSONObject("{'key':'ka2u', 'value':'va2u'}");
        JSONObject soupElt3ForUpsert = new JSONObject("{'key':'ka3u', 'value':'va3u'}");

        //CASE 1: index spec from key to value
        store.alterSoup(TEST_SOUP, new IndexSpec[]{new IndexSpec("value", Type.string)}, true);

        //upsert an entry
        JSONObject soupElt1Upserted = store.upsert(TEST_SOUP, soupElt1ForUpsert);

        // Query all - small page
        runQueryCheckResultsAndExplainPlan(TEST_SOUP,
                QuerySpec.buildAllQuerySpec(TEST_SOUP, "value", Order.ascending, 10),
                0, false, "SCAN", soupElt1Created, soupElt1Upserted, soupElt2Created, soupElt3Created);

        //CASE 2: index spec from string to json1
        store.alterSoup(TEST_SOUP, new IndexSpec[]{new IndexSpec("key", Type.json1)}, true);

        //upsert an entry
        JSONObject soupElt2Upserted = store.upsert(TEST_SOUP, soupElt2ForUpsert);

        // Query all - small page
        runQueryCheckResultsAndExplainPlan(TEST_SOUP,
                QuerySpec.buildAllQuerySpec(TEST_SOUP, "key", Order.ascending, 10),
                0, false, "SCAN", soupElt1Created, soupElt1Upserted, soupElt2Created, soupElt2Upserted, soupElt3Created);

        //CASE 3: add a index spec field
        store.alterSoup(TEST_SOUP, new IndexSpec[]{new IndexSpec("key", Type.json1), new IndexSpec("value", Type.string)}, true);

        //upsert an entry
        JSONObject soupElt3Upserted = store.upsert(TEST_SOUP, soupElt3ForUpsert);

        // Query all - small page
        runQueryCheckResultsAndExplainPlan(TEST_SOUP,
                QuerySpec.buildAllQuerySpec(TEST_SOUP, "key", Order.ascending, 10),
                0, false, "SCAN", soupElt1Created, soupElt1Upserted, soupElt2Created, soupElt2Upserted, soupElt3Created, soupElt3Upserted);
    }

    /**
     * Testing Delete: create multiple soup elements and alter the soup, after that delete a entry, then check them all
     * @throws JSONException
     */
    @Test
    public void testExactQueryAgainstChangedSoup() throws JSONException {

        //create a new soup with multiple entries
        JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1'}");
        JSONObject soupElt2 = new JSONObject("{'key':'ka1-', 'value':'va1*'}");
        JSONObject soupElt3 = new JSONObject("{'key':'ka1 ', 'value':'va1%'}");
        JSONObject soupElt1Created = store.create(TEST_SOUP, soupElt1);
        JSONObject soupElt2Created = store.create(TEST_SOUP, soupElt2);
        JSONObject soupElt3Created = store.create(TEST_SOUP, soupElt3);

        //CASE 1: index spec from key to value
        tryExactQueryOnChangedSoup(TEST_SOUP, "value", "va1",
                new IndexSpec[]{new IndexSpec("value", Type.string)},
                soupElt1Created);

        //CASE 2: index spec from string to json1
        tryExactQueryOnChangedSoup(TEST_SOUP, "key", "ka1",
                new IndexSpec[]{new IndexSpec("key", Type.json1)},
                soupElt1Created);

        //CASE 3: add a index spec field
        tryExactQueryOnChangedSoup(TEST_SOUP, "key", "ka1 ",
                new IndexSpec[]{new IndexSpec("key", Type.json1), new IndexSpec("value", Type.string)},
                soupElt3Created);
    }

    protected void tryExactQueryOnChangedSoup(String soupName, String orderPath, String value,
                                                    IndexSpec[] newIndexSpecs, JSONObject expectedResult) throws JSONException {

        // Alter the soup
        store.alterSoup(soupName, newIndexSpecs, true);

        // Exact Query
        runQueryCheckResultsAndExplainPlan(soupName,
                QuerySpec.buildExactQuerySpec(soupName, orderPath, value, null, null, 5),
                0, false, "SEARCH", expectedResult);
    }

	/**
	 * Test updateSoupNamesToAttrs
	 */
    @Test
	public void testUpdateTableNameAndAddColumns() {

		// Setup db and test values
		final SQLiteDatabase db = dbOpenHelper.getWritableDatabase(getEncryptionKey());
		final String TEST_TABLE = "test_table";
		final String NEW_TEST_TABLE = "new_test_table";
		final String NEW_COLUMN = "new_column";
		db.execSQL("CREATE TABLE " + TEST_TABLE + " (id INTEGER PRIMARY KEY)");

		// Ensure old table doesn't already exist
		Cursor cursor = db.query("sqlite_master", new String[] { "sql" }, "name = ?", new String[] { NEW_TEST_TABLE }, null, null, null);
        Assert.assertEquals("New table should not already be in db.", 0, cursor.getCount());
		cursor.close();

		// Test table renamed and column added
		SmartStore.updateTableNameAndAddColumns(db, TEST_TABLE, NEW_TEST_TABLE, new String[] { NEW_COLUMN });

		// Ensure new table has replaced old table
		cursor = db.query("sqlite_master", new String[] { "sql" }, "name = ?", new String[] { NEW_TEST_TABLE }, null, null, null);
		cursor.moveToFirst();
		String schema = cursor.getString(0);
		cursor.close();
        Assert.assertTrue("New table not found", schema.contains(NEW_TEST_TABLE));
        Assert.assertTrue("New column not found", schema.contains(NEW_COLUMN));

		// Clean up
		db.execSQL("DROP TABLE " + NEW_TEST_TABLE);
	}

	/**
	 * Ensure correct soup spec is returned from getSoupSpec
	 */
    @Test
	public void testGetSoupSpec() throws JSONException {
		final String SOUP_SPEC_TEST = "soup_spec_test";
		IndexSpec[] indexSpecs = new IndexSpec[] {new IndexSpec("index", Type.string)};
		SoupSpec TEST_SPEC = new SoupSpec(SOUP_SPEC_TEST, SoupSpec.FEATURE_EXTERNAL_STORAGE);
		store.registerSoupWithSpec(TEST_SPEC, indexSpecs);

		// Act
		SoupSpec result = store.getSoupSpec(TEST_SPEC.getSoupName());

		// Verify the result
        Assert.assertEquals("Soup name in soup spec is incorrect", SOUP_SPEC_TEST, result.getSoupName());
        Assert.assertEquals("Feature set in soup spec is incorrect", SoupSpec.FEATURE_EXTERNAL_STORAGE, result.getFeatures().get(0));

		// Verify JSON form
        Assert.assertEquals("Soup name in json of soup spec is incorrect", SOUP_SPEC_TEST, result.toJSON().getString("name"));
        Assert.assertEquals("Feature set in json of soup spec is incorrect", SoupSpec.FEATURE_EXTERNAL_STORAGE, result.toJSON().getJSONArray("features").get(0));
	}
}
