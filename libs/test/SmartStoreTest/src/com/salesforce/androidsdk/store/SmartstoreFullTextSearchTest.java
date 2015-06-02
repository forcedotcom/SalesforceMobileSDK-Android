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

import com.salesforce.androidsdk.smartstore.store.DBHelper;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

/**
 * Tests for full-text search with smartstore
 *
 */
public class SmartStoreFullTextSearchTest extends SmartStoreTestCase {

    private static final String EMPLOYEE_ID = "employeeId";
    private static final String LAST_NAME = "lastName";
    private static final String FIRST_NAME = "firstName";
    private static final String EMPLOYEES_SOUP = "employees";

    private static final String TABLE_NAME = "TABLE_1";
    private static final String FIRST_NAME_COL = TABLE_NAME + "_0";
    private static final String LAST_NAME_COL = TABLE_NAME + "_1";
    private static final String EMPLOYEE_ID_COL = TABLE_NAME + "_2";
    
    // Populated by loadData()
    private long christineHaasId;
    private long michaelThompsonId;
    private long aliHaasId;
    private long johnGeyerId;
    private long irvingSternId;
    private long evaPulaskiId;
    private long eileenEvaId;

    @Override
    protected String getPasscode() {
        return "";
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        store.registerSoup(EMPLOYEES_SOUP, new IndexSpec[]{   // should be TABLE_1
                new IndexSpec(FIRST_NAME, Type.full_text),    // should be TABLE_1_0
                new IndexSpec(LAST_NAME, Type.full_text),     // should be TABLE_1_1
                new IndexSpec(EMPLOYEE_ID, Type.string),      // should be TABLE_1_2
        });
    }

    /**
     * Test register/drop soup that uses full-text search indices
     */
    public void testRegisterDropSoup() {
        String soupTableName = getSoupTableName(EMPLOYEES_SOUP);
        assertEquals("getSoupTableName should have returned TABLE_1", TABLE_NAME, soupTableName);
        assertTrue("Table for soup employees does exist", hasTable(soupTableName));
        assertTrue("FTS table for soup employees does exist", hasTable(soupTableName + SmartStore.FTS_SUFFIX));
        assertTrue("Register soup call failed", store.hasSoup(EMPLOYEES_SOUP));

        // Drop
        store.dropSoup(EMPLOYEES_SOUP);

        // After
        assertFalse("Soup employees should no longer exist", store.hasSoup(EMPLOYEES_SOUP));
        assertNull("getSoupTableName should have returned null", getSoupTableName(EMPLOYEES_SOUP));
        assertFalse("Table for soup employees should not exist", hasTable(soupTableName));
        assertFalse("FTS table for soup employees should not exist", hasTable(soupTableName + SmartStore.FTS_SUFFIX));
    }

    /**
     * Test inserting rows
     */
    public void testInsert() throws JSONException {
        // Insert a couple of rows
        long firstEmployeeId = createEmployee("Christine", "Haas", "00010");
        long secondEmployeeId = createEmployee("Michael", "Thompson", "00020");

        // Check DB
        Cursor c = null;
        try {
            String soupTableName = getSoupTableName(EMPLOYEES_SOUP);
            assertEquals("getSoupTableName should have returned TABLE_1", "TABLE_1", soupTableName);
            assertTrue("Table for soup employees does exist", hasTable(soupTableName));

            final SQLiteDatabase db = dbOpenHelper.getWritableDatabase(getPasscode());

            // Check soup table
            c = DBHelper.getInstance(db).query(db, soupTableName, null, "id ASC", null, null);
            assertTrue("Expected a row", c.moveToFirst());
            assertEquals("Expected two rows", 2, c.getCount());
            assertTrue("Wrong columns", Arrays.deepEquals(new String[]{"id", "soup", "created", "lastModified", FIRST_NAME_COL, LAST_NAME_COL, EMPLOYEE_ID_COL}, c.getColumnNames()));

            assertEquals("Wrong id", firstEmployeeId, c.getLong(c.getColumnIndex("id")));
            assertEquals("Wrong value in index column", "Christine", c.getString(c.getColumnIndex(FIRST_NAME_COL)));
            assertEquals("Wrong value in index column", "Haas", c.getString(c.getColumnIndex(LAST_NAME_COL)));
            assertEquals("Wrong value in index column", "00010", c.getString(c.getColumnIndex(EMPLOYEE_ID_COL)));

            c.moveToNext();
            assertEquals("Wrong id", secondEmployeeId, c.getLong(c.getColumnIndex("id")));
            assertEquals("Wrong value in index column", "Michael", c.getString(c.getColumnIndex(FIRST_NAME_COL)));
            assertEquals("Wrong value in index column", "Thompson", c.getString(c.getColumnIndex(LAST_NAME_COL)));
            assertEquals("Wrong value in index column", "00020", c.getString(c.getColumnIndex(EMPLOYEE_ID_COL)));

            safeClose(c);

            // Check fts table columns
            c = DBHelper.getInstance(db).query(db, soupTableName + SmartStore.FTS_SUFFIX, null, "docid ASC", null, null);
            assertTrue("Expected a row", c.moveToFirst());
            assertTrue("Wrong columns", Arrays.deepEquals(new String[]{FIRST_NAME_COL, LAST_NAME_COL}, c.getColumnNames()));

            safeClose(c);

            // Check fts table data
            c = DBHelper.getInstance(db).query(db, soupTableName + SmartStore.FTS_SUFFIX, new String[] {"docid", FIRST_NAME_COL, LAST_NAME_COL}, "docid ASC", null, null);
            assertTrue("Expected a row", c.moveToFirst());
            assertEquals("Expected two rows", 2, c.getCount());

            assertEquals("Wrong id", firstEmployeeId, c.getLong(c.getColumnIndex("docid")));
            assertEquals("Wrong value in index column", "Christine", c.getString(c.getColumnIndex(FIRST_NAME_COL)));
            assertEquals("Wrong value in index column", "Haas", c.getString(c.getColumnIndex(LAST_NAME_COL)));

            c.moveToNext();
            assertEquals("Wrong id", secondEmployeeId, c.getLong(c.getColumnIndex("docid")));
            assertEquals("Wrong value in index column", "Michael", c.getString(c.getColumnIndex(FIRST_NAME_COL)));
            assertEquals("Wrong value in index column", "Thompson", c.getString(c.getColumnIndex(LAST_NAME_COL)));
        }
        finally {
            safeClose(c);
        }
    }

    /**
     * Test deleting rows
     */
    public void testDelete() throws JSONException {
        // Insert a couple of rows
        long firstEmployeeId = createEmployee("Christine", "Haas", "00010");
        long secondEmployeeId = createEmployee("Michael", "Thompson", "00020");

        // Check DB
        Cursor c = null;
        try {
            String soupTableName = getSoupTableName(EMPLOYEES_SOUP);
            final SQLiteDatabase db = dbOpenHelper.getWritableDatabase(getPasscode());

            // Check soup table
            c = DBHelper.getInstance(db).query(db, soupTableName, null, "id ASC", null, null);
            assertTrue("Expected a row", c.moveToFirst());
            assertEquals("Expected two rows", 2, c.getCount());

            safeClose(c);

            // Check fts table data
            c = DBHelper.getInstance(db).query(db, soupTableName + SmartStore.FTS_SUFFIX, null, "docid ASC", null, null);
            assertTrue("Expected a row", c.moveToFirst());
            assertEquals("Expected two rows", 2, c.getCount());
        }
        finally {
            safeClose(c);
        }

        // Delete second employee
        store.delete(EMPLOYEES_SOUP, secondEmployeeId);

        // Check DB
        try {
            String soupTableName = getSoupTableName(EMPLOYEES_SOUP);
            final SQLiteDatabase db = dbOpenHelper.getWritableDatabase(getPasscode());

            // Check soup table
            c = DBHelper.getInstance(db).query(db, soupTableName, null, "id ASC", null, null);
            assertTrue("Expected a row", c.moveToFirst());
            assertEquals("Expected one row", 1, c.getCount());
            assertEquals("Wrong id", firstEmployeeId, c.getLong(c.getColumnIndex("id")));

            safeClose(c);

            // Check fts table data
            c = DBHelper.getInstance(db).query(db, soupTableName + SmartStore.FTS_SUFFIX, new String[] {"docid", FIRST_NAME_COL, LAST_NAME_COL}, "docid ASC", null, null);
            assertTrue("Expected a row", c.moveToFirst());
            assertEquals("Expected one row", 1, c.getCount());
            assertEquals("Wrong id", firstEmployeeId, c.getLong(c.getColumnIndex("docid")));
        }
        finally {
            safeClose(c);
        }

        // Delete first employee
        store.delete(EMPLOYEES_SOUP, firstEmployeeId);

        // Check DB
        try {
            String soupTableName = getSoupTableName(EMPLOYEES_SOUP);
            final SQLiteDatabase db = dbOpenHelper.getWritableDatabase(getPasscode());

            // Check soup table
            c = DBHelper.getInstance(db).query(db, soupTableName, null, "id ASC", null, null);
            assertFalse("Expected no rows", c.moveToFirst());

            safeClose(c);

            // Check fts table data
            c = DBHelper.getInstance(db).query(db, soupTableName + SmartStore.FTS_SUFFIX, null, "docid ASC", null, null);
            assertFalse("Expected no rows", c.moveToFirst());
        }
        finally {
            safeClose(c);
        }
    }

    /**
     * Test clearing soup
     */
    public void testClear() throws JSONException {
        // Insert a couple of rows
        long firstEmployeeId = createEmployee("Christine", "Haas", "00010");
        long secondEmployeeId = createEmployee("Michael", "Thompson", "00020");

        // Check DB
        Cursor c = null;
        try {
            String soupTableName = getSoupTableName(EMPLOYEES_SOUP);
            final SQLiteDatabase db = dbOpenHelper.getWritableDatabase(getPasscode());

            // Check soup table
            c = DBHelper.getInstance(db).query(db, soupTableName, null, "id ASC", null, null);
            assertTrue("Expected a row", c.moveToFirst());
            assertEquals("Expected two rows", 2, c.getCount());

            safeClose(c);

            // Check fts table data
            c = DBHelper.getInstance(db).query(db, soupTableName + SmartStore.FTS_SUFFIX, null, "docid ASC", null, null);
            assertTrue("Expected a row", c.moveToFirst());
            assertEquals("Expected two rows", 2, c.getCount());
        }
        finally {
            safeClose(c);
        }

        // Clear soup
        store.clearSoup(EMPLOYEES_SOUP);

        // Check DB
        try {
            String soupTableName = getSoupTableName(EMPLOYEES_SOUP);
            final SQLiteDatabase db = dbOpenHelper.getWritableDatabase(getPasscode());

            // Check soup table
            c = DBHelper.getInstance(db).query(db, soupTableName, null, "id ASC", null, null);
            assertFalse("Expected no rows", c.moveToFirst());

            safeClose(c);

            // Check fts table data
            c = DBHelper.getInstance(db).query(db, soupTableName + SmartStore.FTS_SUFFIX, null, "docid ASC", null, null);
            assertFalse("Expected no rows", c.moveToFirst());
        }
        finally {
            safeClose(c);
        }
    }

    /**
     * Test updating rows
     */
    public void testUpdate() throws JSONException {
        // Insert a couple of rows
        long firstEmployeeId = createEmployee("Christine", "Haas", "00010");
        long secondEmployeeId = createEmployee("Michael", "Thompson", "00020");


        // Update second employee
        JSONObject updatedEmployee = new JSONObject();
        updatedEmployee.put(FIRST_NAME, "Michael-updated");
        updatedEmployee.put(LAST_NAME, "Thompson");
        updatedEmployee.put(EMPLOYEE_ID, "00020-updated");
        store.update(EMPLOYEES_SOUP, updatedEmployee, secondEmployeeId);

        // Check DB
        Cursor c = null;
        try {
            String soupTableName = getSoupTableName(EMPLOYEES_SOUP);
            assertEquals("getSoupTableName should have returned TABLE_1", "TABLE_1", soupTableName);
            assertTrue("Table for soup employees does exist", hasTable(soupTableName));

            final SQLiteDatabase db = dbOpenHelper.getWritableDatabase(getPasscode());

            // Check soup table
            c = DBHelper.getInstance(db).query(db, soupTableName, null, "id ASC", null, null);
            assertTrue("Expected a row", c.moveToFirst());
            assertEquals("Expected two rows", 2, c.getCount());

            assertEquals("Wrong id", firstEmployeeId, c.getLong(c.getColumnIndex("id")));
            assertEquals("Wrong value in index column", "Christine", c.getString(c.getColumnIndex(FIRST_NAME_COL)));
            assertEquals("Wrong value in index column", "Haas", c.getString(c.getColumnIndex(LAST_NAME_COL)));
            assertEquals("Wrong value in index column", "00010", c.getString(c.getColumnIndex(EMPLOYEE_ID_COL)));

            c.moveToNext();
            assertEquals("Wrong id", secondEmployeeId, c.getLong(c.getColumnIndex("id")));
            assertEquals("Wrong value in index column", "Michael-updated", c.getString(c.getColumnIndex(FIRST_NAME_COL)));
            assertEquals("Wrong value in index column", "Thompson", c.getString(c.getColumnIndex(LAST_NAME_COL)));
            assertEquals("Wrong value in index column", "00020-updated", c.getString(c.getColumnIndex(EMPLOYEE_ID_COL)));

            safeClose(c);

            // Check fts table data
            c = DBHelper.getInstance(db).query(db, soupTableName + SmartStore.FTS_SUFFIX, new String[] {"docid", FIRST_NAME_COL, LAST_NAME_COL}, "docid ASC", null, null);
            assertTrue("Expected a row", c.moveToFirst());
            assertEquals("Expected two rows", 2, c.getCount());

            assertEquals("Wrong id", firstEmployeeId, c.getLong(c.getColumnIndex("docid")));
            assertEquals("Wrong value in index column", "Christine", c.getString(c.getColumnIndex(FIRST_NAME_COL)));
            assertEquals("Wrong value in index column", "Haas", c.getString(c.getColumnIndex(LAST_NAME_COL)));

            c.moveToNext();
            assertEquals("Wrong id", secondEmployeeId, c.getLong(c.getColumnIndex("docid")));
            assertEquals("Wrong value in index column", "Michael-updated", c.getString(c.getColumnIndex(FIRST_NAME_COL)));
            assertEquals("Wrong value in index column", "Thompson", c.getString(c.getColumnIndex(LAST_NAME_COL)));
        }
        finally {
            safeClose(c);
        }
    }

    /**
     * Test search on single field returning no results
     */
    public void testSearchSingleFielNoResults() throws JSONException {
        loadData();

        // One field - full word - no results
        trySearch(new long[]{}, FIRST_NAME, "Christina", null);
        trySearch(new long[]{}, LAST_NAME, "Sternn", null);

        // One field - prefix - no results
        trySearch(new long[]{}, FIRST_NAME, "Christo*", null);
        trySearch(new long[]{}, LAST_NAME, "Stel*", null);

        // One field - set operation - no results
        trySearch(new long[]{}, FIRST_NAME, "Ei* NOT Eileen", null);
    }

    /**
     * Test search on single field returning a single result
     */
    public void testSearchSingleFieldSingleResult() throws JSONException {
        loadData();
        
        // One field - full word - one result
        trySearch(new long[]{christineHaasId}, FIRST_NAME, "Christine", null);
        trySearch(new long[]{irvingSternId}, LAST_NAME, "Stern", null);

        // One field - prefix - one result
        trySearch(new long[]{christineHaasId}, FIRST_NAME, "Christ*", null);
        trySearch(new long[]{irvingSternId}, LAST_NAME, "Ste*", null);

        // One field - set operation - one result
        trySearch(new long[]{eileenEvaId}, FIRST_NAME, "E* NOT Eva", null);
    }

    /**
     * Test search on single field returning multiple results - testing ordering
     */
    public void testSearchSingleFieldMultipleResults() throws JSONException {
        loadData();
        
        // One field - full word - more than one results
        trySearch(new long[]{christineHaasId, aliHaasId}, LAST_NAME, "Haas", EMPLOYEE_ID);
        trySearch(new long[]{aliHaasId, christineHaasId}, LAST_NAME, "Haas", FIRST_NAME);

        // One field - prefix - more than one results
        trySearch(new long[]{evaPulaskiId, eileenEvaId}, FIRST_NAME, "E*", EMPLOYEE_ID);
        trySearch(new long[]{eileenEvaId, evaPulaskiId}, FIRST_NAME, "E*", FIRST_NAME);

        // One field - set operation - more than one results
        trySearch(new long[]{evaPulaskiId, eileenEvaId}, FIRST_NAME, "Eva OR Eileen", EMPLOYEE_ID);
        trySearch(new long[]{eileenEvaId, evaPulaskiId}, FIRST_NAME, "Eva OR Eileen", FIRST_NAME);

    }

    /**
     * Test search on all fields returning no results
     */
    public void testSearchAllFieldsNoResults() throws JSONException {
        loadData();

        // All fields - full word - no results
        trySearch(new long[]{}, null, "Sternn", null);

        // All fields - prefix - no results
        trySearch(new long[]{}, null, "Stel*", null);

        // All fields - multiple words - no results
        trySearch(new long[]{}, null, "Haas Christina", null);

        // All fields - set operation - no results
        trySearch(new long[]{}, null, "Christine NOt Haas", null);
    }

    /**
     * Test search on all fields returning a single result
     */
    public void testSearchAllFieldsSingleResult() throws JSONException {
        loadData();

        // All fields - full word - one result
        trySearch(new long[]{irvingSternId}, null, "Stern", null);

        // All fields - prefix - one result
        trySearch(new long[]{irvingSternId}, null, "St*", null);

        // All fields - multiple words - one result
        trySearch(new long[]{christineHaasId}, null, "Haas Christine", null);

        // All fields - set operation - one result
        trySearch(new long[]{aliHaasId}, null, "Haas NOT Christine", null);
    }

    /**
     * Test search on all fields returning multiple results - testing ordering
     */
    public void testSearchAllFieldMultipleResults() throws JSONException {
        loadData();

        // All fields - full word - more than one results
        trySearch(new long[]{evaPulaskiId, eileenEvaId}, null, "Eva", EMPLOYEE_ID);
        trySearch(new long[]{eileenEvaId, evaPulaskiId}, null, "Eva", LAST_NAME);

        // All fields - prefix - more than one results
        trySearch(new long[]{evaPulaskiId, eileenEvaId}, null, "Ev*", EMPLOYEE_ID);
        trySearch(new long[]{eileenEvaId, evaPulaskiId}, null, "Ev*", LAST_NAME);

        // All fields - set operation - more than result
        trySearch(new long[]{michaelThompsonId, aliHaasId}, null, "Thompson OR Ali", EMPLOYEE_ID);
        trySearch(new long[]{aliHaasId, michaelThompsonId}, null, "Thompson OR Ali", FIRST_NAME);
        trySearch(new long[]{christineHaasId, evaPulaskiId, eileenEvaId}, null, "Eva OR Haas NOT Ali", EMPLOYEE_ID);
        trySearch(new long[]{christineHaasId, eileenEvaId, evaPulaskiId}, null, "Eva OR Haas NOT Ali", FIRST_NAME);
    }

    /**
     * Test search with queries that have field:value predicates
     */
    public void testSearchWithFieldColonQueries() throws JSONException {
        loadData();

        // All fields - full word - no results
        trySearch(new long[]{}, null, "{employees:firstName}:Haas", null);

        // All fields - full word - one result
        trySearch(new long[]{evaPulaskiId}, null, "{employees:firstName}:Eva", null);
        trySearch(new long[]{eileenEvaId}, null, "{employees:lastName}:Eva", null);

        // All fields - full word - more than one results
        trySearch(new long[]{christineHaasId, aliHaasId}, null, "{employees:lastName}:Haas", EMPLOYEE_ID);

        // All fields - prefix - more than one results
        trySearch(new long[]{evaPulaskiId, eileenEvaId}, null, "{employees:firstName}:E*", EMPLOYEE_ID);
        trySearch(new long[]{christineHaasId, aliHaasId}, null, "{employees:lastName}:H*", EMPLOYEE_ID);

        // All fields - set operation - more than result
        trySearch(new long[]{michaelThompsonId, aliHaasId}, null, "{employees:lastName}:Thompson OR {employees:firstName}:Ali", EMPLOYEE_ID);
        trySearch(new long[]{aliHaasId, michaelThompsonId}, null, "{employees:lastName}:Thompson OR {employees:firstName}:Ali", FIRST_NAME);
        trySearch(new long[]{christineHaasId, eileenEvaId}, null, "{employees:lastName}:Eva OR Haas NOT Ali", EMPLOYEE_ID);
        trySearch(new long[]{eileenEvaId, christineHaasId}, null, "{employees:lastName}:Eva OR Haas NOT Ali", LAST_NAME);
    }

    private void trySearch(long[] expectedIds, String path, String matchKey, String orderPath) throws JSONException {
        JSONArray results = store.query(QuerySpec.buildMatchQuerySpec(EMPLOYEES_SOUP, path, matchKey, orderPath, QuerySpec.Order.ascending, 25), 0);
        assertEquals("Wrong number of results", expectedIds.length, results.length());
        for (int i=0; i<results.length(); i++) {
            assertEquals("Wrong result", expectedIds[i], idOf(results.getJSONObject(i)));
        }
    }

    private void loadData() throws JSONException {
        christineHaasId = createEmployee("Christine", "Haas", "00010");
        michaelThompsonId = createEmployee("Michael", "Thompson", "00020");
        aliHaasId = createEmployee("Ali", "Haas", "00030");
        johnGeyerId = createEmployee("John", "Geyer", "00040");
        irvingSternId = createEmployee("Irving", "Stern", "00050");
        evaPulaskiId = createEmployee("Eva", "Pulaski", "00060");
        eileenEvaId = createEmployee("Eileen", "Eva", "00070");
    }
    
    private long createEmployee(String firstName, String lastName, String employeeId) throws JSONException {
        JSONObject employee = new JSONObject();
        employee.put(FIRST_NAME, firstName);
        employee.put(LAST_NAME, lastName);
        employee.put(EMPLOYEE_ID, employeeId);
        JSONObject employeeSaved = store.create(EMPLOYEES_SOUP, employee);
        return idOf(employeeSaved);
    }
}
