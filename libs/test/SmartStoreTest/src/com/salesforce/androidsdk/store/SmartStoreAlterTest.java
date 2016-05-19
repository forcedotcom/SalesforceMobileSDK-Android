/*
 * Copyright (c) 2015, salesforce.com, inc.
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
import android.text.TextUtils;
import android.util.Log;

import com.salesforce.androidsdk.smartstore.store.AlterSoupLongOperation;
import com.salesforce.androidsdk.smartstore.store.DBHelper;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.LongOperation;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartSqlHelper;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.util.test.JSONTestHelper;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tests to compare speed of smartstore full-text-search indices with regular indices
 */
public class SmartStoreAlterTest extends SmartStoreTestCase {

    private static final String TEST_SOUP = "test_soup";
    private static final String TEST_SOUP_TABLE_NAME = "TABLE_1";
    private static final String CITY = "city";
    private static final String CITY_COL = TEST_SOUP_TABLE_NAME + "_0";
    private static final String COUNTRY = "country";
    private static final String COUNTRY_COL = TEST_SOUP_TABLE_NAME + "_1";
    private static final String SAN_FRANCISCO = "San Francisco";
    private static final String PARIS = "Paris";
    private static final String USA = "United States";
    private static final String FRANCE = "France";

    protected String getPasscode() {
        return "";
    }

    /**
     * Test for getSoupIndexSpecs
     *
     * @throws JSONException
     */
    public void testGetSoupIndexSpecs() throws JSONException {
        IndexSpec[] indexSpecs = new IndexSpec[] {
                new IndexSpec("lastName", SmartStore.Type.string),
                new IndexSpec("address.city", SmartStore.Type.string),
                new IndexSpec("salary", SmartStore.Type.integer),
                new IndexSpec("interest", SmartStore.Type.floating),
                new IndexSpec("note", SmartStore.Type.full_text),
                new IndexSpec("other", SmartStore.Type.json1)
        };

        assertFalse("Soup test_soup should not exist", store.hasSoup(TEST_SOUP));
        store.registerSoup(TEST_SOUP, indexSpecs);
        assertTrue("Register soup call failed", store.hasSoup(TEST_SOUP));

        checkIndexSpecs(TEST_SOUP, new IndexSpec[] {
                new IndexSpec("lastName", SmartStore.Type.string, TEST_SOUP_TABLE_NAME + "_0"),
                new IndexSpec("address.city", SmartStore.Type.string, TEST_SOUP_TABLE_NAME + "_1"),
                new IndexSpec("salary", SmartStore.Type.integer, TEST_SOUP_TABLE_NAME + "_2"),
                new IndexSpec("interest", SmartStore.Type.floating, TEST_SOUP_TABLE_NAME + "_3"),
                new IndexSpec("note", SmartStore.Type.full_text, TEST_SOUP_TABLE_NAME + "_4"),
                new IndexSpec("other", SmartStore.Type.json1, "json_extract(soup, '$.other')")
        });
    }

    /**
     * Test for alterSoup with reIndexData = false
     *
     * @throws JSONException
     */
    public void testAlterSoupNoReIndexing() throws JSONException {
        alterSoupHelper(false);
    }

    /**
     * Test for alterSoup with reIndexData = true
     *
     * @throws JSONException
     */
    public void testAlterSoupWithReIndexing() throws JSONException {
        alterSoupHelper(true);
    }

    /**
     * Test for alterSoup with column type change from string to integer
     *
     * throws JSONException
     */
    public void testAlterSoupTypeChangeStringToInteger() throws JSONException {
        IndexSpec[] indexSpecs = new IndexSpec[] {new IndexSpec("name", SmartStore.Type.string), new IndexSpec("population", SmartStore.Type.string)};

        assertFalse("Soup test_soup should not exist", store.hasSoup(TEST_SOUP));
        store.registerSoup(TEST_SOUP, indexSpecs);
        assertTrue("Register soup call failed", store.hasSoup(TEST_SOUP));

        JSONObject soupElt1 = new JSONObject("{'name': 'San Francisco', 'population': 825863}");
        JSONObject soupElt2 = new JSONObject("{'name': 'Paris', 'population': 2234105}");

        store.create(TEST_SOUP, soupElt1);
        store.create(TEST_SOUP, soupElt2);

        // Query all sorted by population ascending - we should get Paris first because we indexed population as a string
        JSONArray results = store.query(QuerySpec.buildAllQuerySpec(TEST_SOUP, "population", QuerySpec.Order.ascending, 2), 0);
        assertEquals("Paris should be first", "Paris", results.getJSONObject(0).get("name"));
        assertEquals("San Francisco should be second", "San Francisco", results.getJSONObject(1).get("name"));

        // Alter soup - index population as integer
        IndexSpec[] indexSpecsNew = new IndexSpec[] {new IndexSpec("name", SmartStore.Type.string), new IndexSpec("population", SmartStore.Type.integer)};
        store.alterSoup(TEST_SOUP, indexSpecsNew, true);

        // Query all sorted by population ascending - we should get San Francisco first because we indexed population as an integer
        JSONArray results2 = store.query(QuerySpec.buildAllQuerySpec(TEST_SOUP, "population", QuerySpec.Order.ascending, 2), 0);
        assertEquals("San Francisco should be first", "San Francisco", results2.getJSONObject(0).get("name"));
        assertEquals("Paris should be first", "Paris", results2.getJSONObject(1).get("name"));
    }

    /**
     * Test for alterSoup with column type change from string to full_text
     *
     * throws JSONException
     */
    public void testAlterSoupTypeChangeStringToFullText() throws JSONException {
        tryAlterSoupTypeChange(SmartStore.Type.string, SmartStore.Type.full_text);
    }

    /**
     * Test for alterSoup with column type change from full_text to string
     *
     * throws JSONException
     */
    public void testAlterSoupTypeChangeFullTextToString() throws JSONException {
        tryAlterSoupTypeChange(SmartStore.Type.full_text, SmartStore.Type.string);
    }

    /**
     * Test for alterSoup with column type change from string to json1
     *
     * throws JSONException
     */
    public void testAlterSoupTypeChangeStringToJSON1() throws JSONException {
        tryAlterSoupTypeChange(SmartStore.Type.string, SmartStore.Type.json1);
    }

    /**
     * Test for alterSoup with column type change from json1 to string
     *
     * throws JSONException
     */
    public void testAlterSoupTypeChangeJSON1toString() throws JSONException {
        tryAlterSoupTypeChange(SmartStore.Type.json1, SmartStore.Type.string);
    }

    /**
     * Test for alterSoup with column type change from full_text to json1
     *
     * throws JSONException
     */
    public void testAlterSoupTypeChangeFullTextToJSON1() throws JSONException {
        tryAlterSoupTypeChange(SmartStore.Type.full_text, SmartStore.Type.json1);
    }

    /**
     * Test for alterSoup with column type change from json1 to full_text
     *
     * throws JSONException
     */
    public void testAlterSoupTypeChangeJSON1toFullText() throws JSONException {
        tryAlterSoupTypeChange(SmartStore.Type.json1, SmartStore.Type.full_text);
    }

    /**
     * Test for alterSoup passing in same index specs (string)
     * Make sure db table / indexes are recreated
     * That way soup created before 4.2 can get the new indexes (create/lastModified) by calling alterSoup
     */
    public void testAlterSoupWithStringIndexesToGetIndexesOnCreatedAndLastModified() throws JSONException{
        tryAlterSoupToGetIndexesOnCreatedAndLastModified(SmartStore.Type.string);
    }

    /**
     * Test for alterSoup passing in same index specs (json1)
     * Make sure db table / indexes are recreated
     * That way soup created before 4.2 can get the new indexes (create/lastModified) by calling alterSoup
     */
    public void testAlterSoupWithJSON1IndexesToGetIndexesOnCreatedAndLastModified() throws JSONException {
        tryAlterSoupToGetIndexesOnCreatedAndLastModified(SmartStore.Type.json1);
    }

    /**
     * Test for alterSoup passing in same index specs (full_text)
     * Make sure db table / indexes are recreated
     * That way soup created before 4.2 can get the new indexes (create/lastModified) by calling alterSoup
     */
    public void testAlterSoupWithFullTextIndexesToGetIndexesOnCreatedAndLastModified() throws JSONException {
        tryAlterSoupToGetIndexesOnCreatedAndLastModified(SmartStore.Type.full_text);
    }

    private void tryAlterSoupToGetIndexesOnCreatedAndLastModified(SmartStore.Type indexType) throws JSONException {
        IndexSpec[] indexSpecs = new IndexSpec[]{new IndexSpec(CITY, indexType), new IndexSpec(COUNTRY, indexType)};

        assertFalse("Soup test_soup should not exist", store.hasSoup(TEST_SOUP));
        store.registerSoup(TEST_SOUP, indexSpecs);
        assertTrue("Register soup call failed", store.hasSoup(TEST_SOUP));

        JSONObject soupElt1 = new JSONObject();
        soupElt1.put(CITY, SAN_FRANCISCO);
        soupElt1.put(COUNTRY, USA);
        JSONObject soupElt2 = new JSONObject();
        soupElt2.put(CITY, PARIS);
        soupElt2.put(COUNTRY, FRANCE);

        long elt1Id = idOf(store.create(TEST_SOUP, soupElt1));
        long elt2Id = idOf(store.create(TEST_SOUP, soupElt2));

        // Checking db
        checkDb(new long[]{elt1Id, elt2Id}, indexSpecs[0].type, indexSpecs[1].type);

        // Drop db indexes on created and lastModified to simulate soup having been created before SDK 4.2
        String dropIndexSqlFormat = "DROP INDEX %s_%s_idx";
        final SQLiteDatabase db = dbOpenHelper.getWritableDatabase(getPasscode());
        db.execSQL(String.format(dropIndexSqlFormat, TEST_SOUP_TABLE_NAME, "created"));
        db.execSQL(String.format(dropIndexSqlFormat, TEST_SOUP_TABLE_NAME, "lastModified"));

        // Check db indexes after the drop - created and lastModified should be gone
        String expectedCityCol = indexType == SmartStore.Type.json1 ? String.format("json_extract(soup, '$.%s')", CITY) : CITY_COL;
        String expectedCountryCol = indexType == SmartStore.Type.json1 ? String.format("json_extract(soup, '$.%s')", COUNTRY) : COUNTRY_COL;

        checkDatabaseIndexes(TEST_SOUP_TABLE_NAME, Arrays.asList(new String[]{
                "CREATE INDEX " + TEST_SOUP_TABLE_NAME + "_0_idx on " + TEST_SOUP_TABLE_NAME + " ( " + expectedCityCol + " )",
                "CREATE INDEX " + TEST_SOUP_TABLE_NAME + "_1_idx on " + TEST_SOUP_TABLE_NAME + " ( " + expectedCountryCol + " )",
        }));

        // Alter soup - passing same indexSpecs as before
        store.alterSoup(TEST_SOUP, indexSpecs, true);

        // Check db - created and lastModified indexes should be there
        checkDb(new long[]{elt1Id, elt2Id}, indexSpecs[0].type, indexSpecs[1].type);
    }

    public void tryAlterSoupTypeChange(SmartStore.Type fromType, SmartStore.Type toType) throws JSONException {
        IndexSpec[] indexSpecs = new IndexSpec[] {new IndexSpec(CITY, fromType), new IndexSpec(COUNTRY, fromType)};

        assertFalse("Soup test_soup should not exist", store.hasSoup(TEST_SOUP));
        store.registerSoup(TEST_SOUP, indexSpecs);
        assertTrue("Register soup call failed", store.hasSoup(TEST_SOUP));

        JSONObject soupElt1 = new JSONObject();
        soupElt1.put(CITY, SAN_FRANCISCO);
        soupElt1.put(COUNTRY, USA);
        JSONObject soupElt2 = new JSONObject();
        soupElt2.put(CITY, PARIS);
        soupElt2.put(COUNTRY, FRANCE);

        long elt1Id = idOf(store.create(TEST_SOUP, soupElt1));
        long elt2Id = idOf(store.create(TEST_SOUP, soupElt2));

        // Checking db
        checkDb(new long[]{elt1Id, elt2Id}, indexSpecs[0].type, indexSpecs[1].type);

        // Alter soup - country now full_text
        IndexSpec[] indexSpecsNew = new IndexSpec[] {new IndexSpec(CITY, fromType), new IndexSpec(COUNTRY, toType)};
        store.alterSoup(TEST_SOUP, indexSpecsNew, true);

        // Checking db
        checkDb(new long[]{elt1Id, elt2Id}, indexSpecsNew[0].type, indexSpecsNew[1].type);

        // Alter soup - city now full_text
        indexSpecsNew = new IndexSpec[] {new IndexSpec(CITY, toType), new IndexSpec(COUNTRY, toType)};
        store.alterSoup(TEST_SOUP, indexSpecsNew, true);

        // Checking db
        checkDb(new long[]{elt1Id, elt2Id}, indexSpecsNew[0].type, indexSpecsNew[1].type);
    }


    private void checkDb(long[] expectedIds, SmartStore.Type cityColType, SmartStore.Type countryColType) throws JSONException {
        String[] cities = new String[] {SAN_FRANCISCO, PARIS};
        String[] countries = new String[] {USA, FRANCE};

        // Check columns of soup table
        List<String> expectedColumnNames = new ArrayList<>(Arrays.asList("id", "soup", "created", "lastModified"));
        if (cityColType != SmartStore.Type.json1) expectedColumnNames.add(CITY_COL);
        if (countryColType != SmartStore.Type.json1) expectedColumnNames.add(COUNTRY_COL);
        checkColumns(TEST_SOUP_TABLE_NAME, expectedColumnNames);

        // Check soup indexes
        String expectedCityCol = cityColType == SmartStore.Type.json1 ? String.format("json_extract(soup, '$.%s')", CITY) : CITY_COL;
        String expectedCountryCol = countryColType == SmartStore.Type.json1 ? String.format("json_extract(soup, '$.%s')", COUNTRY) : COUNTRY_COL;
        checkIndexSpecs(TEST_SOUP, new IndexSpec[]{ new IndexSpec(CITY, cityColType, expectedCityCol), new IndexSpec(COUNTRY, countryColType, expectedCountryCol)});

        // Check db indexes
        checkDatabaseIndexes(TEST_SOUP_TABLE_NAME, Arrays.asList(new String[]{
                "CREATE INDEX " + TEST_SOUP_TABLE_NAME + "_0_idx on " + TEST_SOUP_TABLE_NAME + " ( " + expectedCityCol + " )",
                "CREATE INDEX " + TEST_SOUP_TABLE_NAME + "_1_idx on " + TEST_SOUP_TABLE_NAME + " ( " + expectedCountryCol + " )",
                "CREATE INDEX " + TEST_SOUP_TABLE_NAME + "_created_idx on " + TEST_SOUP_TABLE_NAME + " ( created )",
                "CREATE INDEX " + TEST_SOUP_TABLE_NAME + "_lastModified_idx on " + TEST_SOUP_TABLE_NAME + " ( lastModified )",

        }));

        // Check rows of soup table
        Cursor c = null;
        try {
            final SQLiteDatabase db = dbOpenHelper.getWritableDatabase(getPasscode());
            c = DBHelper.getInstance(db).query(db, TEST_SOUP_TABLE_NAME, expectedColumnNames.toArray(new String[0]), "id ASC", null, null);
            assertTrue("Expected a row", c.moveToFirst());
            assertEquals("Wrong number of rows", expectedIds.length, c.getCount());

            for (int i = 0; i < expectedIds.length; i++) {
                assertEquals("Wrong id", expectedIds[i], c.getLong(c.getColumnIndex("id")));
                if (cityColType != SmartStore.Type.json1)
                    assertEquals("Wrong value in index column", cities[i], c.getString(c.getColumnIndex(CITY_COL)));
                if (countryColType != SmartStore.Type.json1)
                    assertEquals("Wrong value in index column", countries[i], c.getString(c.getColumnIndex(COUNTRY_COL)));
                c.moveToNext();
            }
        }
        finally{
            safeClose(c);
        }

        // Check fts table exists
        boolean hasFts = cityColType == SmartStore.Type.full_text || countryColType == SmartStore.Type.full_text;
        assertEquals(hasFts, hasTable(TEST_SOUP_TABLE_NAME + SmartStore.FTS_SUFFIX));

        if (!hasFts) {
            return;
        }

        // Check columns of fts table
        List<String> expectedFtsColumnNames = new ArrayList<>(); // NB: docid not returned by pragma table_info for fts virtual table
        if (cityColType == SmartStore.Type.full_text) expectedFtsColumnNames.add(CITY_COL);
        if (countryColType == SmartStore.Type.full_text) expectedFtsColumnNames.add(COUNTRY_COL);
        checkColumns(TEST_SOUP_TABLE_NAME + SmartStore.FTS_SUFFIX, expectedFtsColumnNames);

        // Check rows of fts table
        try {
            final SQLiteDatabase db = dbOpenHelper.getWritableDatabase(getPasscode());
            expectedFtsColumnNames.add(0, "docid");
            c = DBHelper.getInstance(db).query(db, TEST_SOUP_TABLE_NAME + SmartStore.FTS_SUFFIX, expectedFtsColumnNames.toArray(new String[0]), "docid ASC", null, null);
            assertTrue("Expected a row", c.moveToFirst());
            assertEquals("Wrong number of rows", expectedIds.length, c.getCount());

            for (int i = 0; i < expectedIds.length; i++) {
                assertEquals("Wrong docid", expectedIds[i], c.getLong(c.getColumnIndex("docid")));
                if (cityColType == SmartStore.Type.full_text)
                    assertEquals("Wrong value in index column", cities[i], c.getString(c.getColumnIndex(CITY_COL)));
                if (countryColType == SmartStore.Type.full_text)
                    assertEquals("Wrong value in index column", countries[i], c.getString(c.getColumnIndex(COUNTRY_COL)));
                c.moveToNext();
            }
        }
        finally{
            safeClose(c);
        }
    }

    /**
     * Helper method for alter soup tests
     * @param reIndexData
     * @throws JSONException
     */
    private void alterSoupHelper(boolean reIndexData) throws JSONException {
        IndexSpec[] indexSpecs = new IndexSpec[] {new IndexSpec("lastName", SmartStore.Type.string), new IndexSpec("address.city", SmartStore.Type.string)};

        assertFalse("Soup test_soup should not exist", store.hasSoup(TEST_SOUP));
        store.registerSoup(TEST_SOUP, indexSpecs);
        assertTrue("Register soup call failed", store.hasSoup(TEST_SOUP));

        // Populate soup
        JSONObject soupElt1Created = store.create(TEST_SOUP, new JSONObject("{'lastName':'Doe', 'address':{'city':'San Francisco','street':'1 market'}}"));
        JSONObject soupElt2Created = store.create(TEST_SOUP, new JSONObject("{'lastName':'Jackson', 'address':{'city':'Los Angeles','street':'100 mission'}}"));
        JSONObject soupElt3Created = store.create(TEST_SOUP, new JSONObject("{'lastName':'Watson', 'address':{'city':'London','street':'50 market'}}"));

        // Alter soup
        IndexSpec[] indexSpecsNew = new IndexSpec[] {new IndexSpec("lastName", SmartStore.Type.string), new IndexSpec("address.street", SmartStore.Type.string)};
        store.alterSoup(TEST_SOUP, indexSpecsNew, reIndexData);

        // Check index specs
        checkIndexSpecs(TEST_SOUP, new IndexSpec[] {
                new IndexSpec("lastName", SmartStore.Type.string, TEST_SOUP_TABLE_NAME + "_0"),
                new IndexSpec("address.street", SmartStore.Type.string, TEST_SOUP_TABLE_NAME + "_1")
        });

        // Check DB
        Cursor c = null;
        try {
            final SQLiteDatabase db = dbOpenHelper.getWritableDatabase(getPasscode());
            String soupTableName = getSoupTableName(TEST_SOUP);
            assertEquals("Wrong table for test_soup", TEST_SOUP_TABLE_NAME, soupTableName);
            assertTrue("Table for test_soup should now exist", hasTable(TEST_SOUP_TABLE_NAME));

            c = DBHelper.getInstance(db).query(db, soupTableName, null, "id ASC", null, null);
            assertTrue("Expected a soup element", c.moveToFirst());
            assertEquals("Expected three soup elements", 3, c.getCount());

            assertEquals("Wrong id", idOf(soupElt1Created), c.getLong(c.getColumnIndex("id")));
            assertEquals("Wrong created date", soupElt1Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
            assertEquals("Wrong value in index column", "Doe", c.getString(c.getColumnIndex(soupTableName + "_0")));
            if (reIndexData)
                assertEquals("Wrong value in index column", "1 market", c.getString(c.getColumnIndex(soupTableName + "_1")));
            else
                assertNull("Wrong value in index column", c.getString(c.getColumnIndex(soupTableName + "_1")));
            JSONTestHelper.assertSameJSON("Wrong value in soup column", soupElt1Created, new JSONObject(c.getString(c.getColumnIndex("soup"))));

            c.moveToNext();
            assertEquals("Wrong id", idOf(soupElt2Created), c.getLong(c.getColumnIndex("id")));
            assertEquals("Wrong created date", soupElt2Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
            assertEquals("Wrong value in index column", "Jackson", c.getString(c.getColumnIndex(soupTableName + "_0")));
            if (reIndexData)
                assertEquals("Wrong value in index column", "100 mission", c.getString(c.getColumnIndex(soupTableName + "_1")));
            else
                assertNull("Wrong value in index column", c.getString(c.getColumnIndex(soupTableName + "_1")));
            JSONTestHelper.assertSameJSON("Wrong value in soup column", soupElt2Created, new JSONObject(c.getString(c.getColumnIndex("soup"))));

            c.moveToNext();
            assertEquals("Wrong id", idOf(soupElt3Created), c.getLong(c.getColumnIndex("id")));
            assertEquals("Wrong created date", soupElt3Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
            assertEquals("Wrong value in index column", "Watson", c.getString(c.getColumnIndex(soupTableName + "_0")));
            if (reIndexData)
                assertEquals("Wrong value in index column", "50 market", c.getString(c.getColumnIndex(soupTableName + "_1")));
            else
                assertNull("Wrong value in index column", c.getString(c.getColumnIndex(soupTableName + "_1")));
            JSONTestHelper.assertSameJSON("Wrong value in soup column", soupElt3Created, new JSONObject(c.getString(c.getColumnIndex("soup"))));

        }
        finally {
            safeClose(c);
        }
    }

    /**
     * Test reIndexSoup
     * @throws JSONException
     */
    public void testReIndexSoup() throws JSONException {
        IndexSpec[] indexSpecs = new IndexSpec[] {new IndexSpec("lastName", SmartStore.Type.string)};

        assertFalse("Soup test_soup should not exist", store.hasSoup(TEST_SOUP));
        store.registerSoup(TEST_SOUP, indexSpecs);
        assertTrue("Register soup call failed", store.hasSoup(TEST_SOUP));

        JSONObject soupElt1 = new JSONObject("{'lastName':'Doe', 'address':{'city':'San Francisco','street':'1 market'}}");

        store.create(TEST_SOUP, soupElt1);

        // Find by last name
        assertRowCount(1, "lastName", "Doe");

        // Find by city - error expected - field is not yet indexed
        try {
            assertRowCount(1, "address.city", "San Francisco");
            fail("Expected smart sql exception");
        }
        catch (SmartSqlHelper.SmartSqlException e) {
            // as expected
        }

        // Alter soup - add city + street
        IndexSpec[] indexSpecsNew = new IndexSpec[] {new IndexSpec("lastName", SmartStore.Type.string), new IndexSpec("address.city", SmartStore.Type.string), new IndexSpec("address.street", SmartStore.Type.string)};
        store.alterSoup(TEST_SOUP, indexSpecsNew, false);

        // Find by city - no rows expected (we have not re-indexed yet)
        assertRowCount(0, "address.city", "San Francisco");

        // Re-index city
        store.reIndexSoup(TEST_SOUP, new String[] {"address.city"}, true);

        // Find by city
        assertRowCount(1, "address.city", "San Francisco");

        // Find by street - no rows expected (we have not re-indexed yet)
        assertRowCount(0, "address.street", "1 market");

        // Re-index street
        store.reIndexSoup(TEST_SOUP, new String[] {"address.street"}, true);

        // Find by street
        assertRowCount(1, "address.street", "1 market");
    }

    /**
     * Helper function for testReIndexSoup: count rows where field has value
     * @param expectedCount
     * @param field
     * @param value
     * @throws JSONException
     */
    private void assertRowCount(int expectedCount, String field, String value) throws JSONException {
        String smartSql = "SELECT count(*) FROM {" + TEST_SOUP + "} WHERE {" + TEST_SOUP + ":" + field + "} = '" + value + "'";
        int actualCount = store.query(QuerySpec.buildSmartQuerySpec(smartSql, 1), 0).getJSONArray(0).getInt(0);
        assertEquals("Should have found " + expectedCount + " rows", expectedCount, actualCount);
    }

    /**
     * Test alter soup interrupted and resumed after step RENAME_OLD_SOUP_TABLE
     * @throws JSONException
     */
    public void testAlterSoupResumeAfterRenameOldSoupTable() throws JSONException {
        tryAlterSoupInterruptResume(AlterSoupLongOperation.AlterSoupStep.RENAME_OLD_SOUP_TABLE);
    }

    /**
     * Test alter soup interrupted and resumed after step DROP_OLD_INDEXES
     * @throws JSONException
     */
    public void testAlterSoupResumeAfterDropOldIndexed() throws JSONException {
        tryAlterSoupInterruptResume(AlterSoupLongOperation.AlterSoupStep.DROP_OLD_INDEXES);
    }

    /**
     * Test alter soup interrupted and resumed after step REGISTER_SOUP_USING_TABLE_NAME
     * @throws JSONException
     */
    public void testAlterSoupResumeAfterRegisterSoupUsingTableName() throws JSONException {
        tryAlterSoupInterruptResume(AlterSoupLongOperation.AlterSoupStep.REGISTER_SOUP_USING_TABLE_NAME);
    }

    /**
     * Test alter soup interrupted and resumed after step COPY_TABLE
     * @throws JSONException
     */
    public void testAlterSoupResumeAfterCopyTable() throws JSONException {
        tryAlterSoupInterruptResume(AlterSoupLongOperation.AlterSoupStep.COPY_TABLE);
    }

    /**
     * Test alter soup interrupted and resumed after step RE_INDEX_SOUP
     * @throws JSONException
     */
    public void testAlterSoupResumeAfterReIndexSoup() throws JSONException {
        tryAlterSoupInterruptResume(AlterSoupLongOperation.AlterSoupStep.RE_INDEX_SOUP);
    }

    /**
     * Test alter soup interrupted and resumed after step DROP_OLD_TABLE
     * @throws JSONException
     */
    public void testAlterSoupResumeAfterDropOldTable() throws JSONException {
        tryAlterSoupInterruptResume(AlterSoupLongOperation.AlterSoupStep.DROP_OLD_TABLE);
    }

    /**
     * Helper for testAlterSoupInterruptResume
     * @throws JSONException
     */
    private void tryAlterSoupInterruptResume(AlterSoupLongOperation.AlterSoupStep toStep) throws JSONException {
        final SQLiteDatabase db = dbOpenHelper.getWritableDatabase(getPasscode());
        assertFalse("Soup test_soup should not exist", store.hasSoup(TEST_SOUP));
        IndexSpec[] indexSpecs = new IndexSpec[] {new IndexSpec("lastName", SmartStore.Type.string)};
        store.registerSoup(TEST_SOUP, indexSpecs);
        IndexSpec[] oldIndexSpecs = store.getSoupIndexSpecs(TEST_SOUP); // with column names
        String soupTableName = getSoupTableName(TEST_SOUP);
        assertTrue("Register soup call failed", store.hasSoup(TEST_SOUP));

        // Populate soup
        JSONObject soupElt1Created = store.create(TEST_SOUP, new JSONObject("{'lastName':'Doe', 'address':{'city':'San Francisco','street':'1 market'}}"));
        JSONObject soupElt2Created = store.create(TEST_SOUP, new JSONObject("{'lastName':'Jackson', 'address':{'city':'Los Angeles','street':'100 mission'}}"));

        // Partial alter - up to toStep included
        IndexSpec[] indexSpecsNew = new IndexSpec[] {new IndexSpec("lastName", SmartStore.Type.string), new IndexSpec("address.city", SmartStore.Type.string), new IndexSpec("address.street", SmartStore.Type.string)};
        AlterSoupLongOperation operation = new AlterSoupLongOperation(store, TEST_SOUP, indexSpecsNew, true);
        operation.run(toStep);

        // Validate long_operations_status table
        LongOperation[] operations = store.getLongOperations();
        int expectedCount = (toStep == AlterSoupLongOperation.AlterSoupStep.LAST ? 0 : 1);
        assertEquals("Wrong number of long operations found", expectedCount, operations.length);

        if (operations.length > 0) {
            // Check details
            JSONObject actualDetails = operations[0].getDetails();
            assertEquals("Wrong soup name", TEST_SOUP, actualDetails.getString("soupName"));
            assertEquals("Wrong soup table name", soupTableName, actualDetails.getString("soupTableName"));
            JSONTestHelper.assertSameJSON("Wrong old indexes", IndexSpec.toJSON(oldIndexSpecs), actualDetails.getJSONArray("oldIndexSpecs"));
            // new index specs in details might or might not have column names based on step so not comparing with JSONTestHelper.assertSameJSON however checkIndexSpecs below should catch any discrepancies
            assertEquals("Wrong re-index data", true, actualDetails.getBoolean("reIndexData"));

            // Check last step completed
            assertEquals("Wrong step", toStep, ((AlterSoupLongOperation) operations[0]).getLastStepCompleted());

            // Simulate restart (clear cache and call resumeLongOperations)
            DBHelper.getInstance(db).clearMemoryCache();
            store.resumeLongOperations();

            // Check index specs
            checkIndexSpecs(TEST_SOUP, new IndexSpec[] {
                    new IndexSpec("lastName", SmartStore.Type.string, TEST_SOUP_TABLE_NAME + "_0"),
                    new IndexSpec("address.city", SmartStore.Type.string, TEST_SOUP_TABLE_NAME + "_1"),
                    new IndexSpec("address.street", SmartStore.Type.string, TEST_SOUP_TABLE_NAME + "_2")
            });

            // Check DB
            Cursor c = null;
            try {
                assertEquals("Wrong table for test_soup", TEST_SOUP_TABLE_NAME, soupTableName);
                assertTrue("Table for test_soup should now exist", hasTable(TEST_SOUP_TABLE_NAME));

                c = DBHelper.getInstance(db).query(db, soupTableName, null, "id ASC", null, null);
                assertTrue("Expected a soup element", c.moveToFirst());
                assertEquals("Expected three soup elements", 2, c.getCount());

                assertEquals("Wrong id", idOf(soupElt1Created), c.getLong(c.getColumnIndex("id")));
                assertEquals("Wrong created date", soupElt1Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
                assertEquals("Wrong value in index column", "Doe", c.getString(c.getColumnIndex(soupTableName + "_0")));
                assertEquals("Wrong value in index column", "San Francisco", c.getString(c.getColumnIndex(soupTableName + "_1")));
                assertEquals("Wrong value in index column", "1 market", c.getString(c.getColumnIndex(soupTableName + "_2")));
                JSONTestHelper.assertSameJSON("Wrong value in soup column", soupElt1Created, new JSONObject(c.getString(c.getColumnIndex("soup"))));

                c.moveToNext();
                assertEquals("Wrong id", idOf(soupElt2Created), c.getLong(c.getColumnIndex("id")));
                assertEquals("Wrong created date", soupElt2Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")));
                assertEquals("Wrong value in index column", "Jackson", c.getString(c.getColumnIndex(soupTableName + "_0")));
                assertEquals("Wrong value in index column", "Los Angeles", c.getString(c.getColumnIndex(soupTableName + "_1")));
                assertEquals("Wrong value in index column", "100 mission", c.getString(c.getColumnIndex(soupTableName + "_2")));
                JSONTestHelper.assertSameJSON("Wrong value in soup column", soupElt2Created, new JSONObject(c.getString(c.getColumnIndex("soup"))));
            }
            finally {
                safeClose(c);
            }
        }
    }

}