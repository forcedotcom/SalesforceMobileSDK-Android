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

import android.database.Cursor;

import com.salesforce.androidsdk.smartstore.store.DBHelper;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;
import com.salesforce.androidsdk.util.test.JSONTestHelper;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Tests for full-text search with smartstore
 *
 */
public class SmartstoreFullTextSearchTest extends SmartStoreTestCase {

    private static final String EMPLOYEE_ID = "employeeId";
    private static final String LAST_NAME = "lastName";
    private static final String FIRST_NAME = "firstName";
    private static final String EMPLOYEES_SOUP = "employees";

    private static final String TABLE_NAME = "TABLE_1";
    private static final String FIRST_NAME_COL = TABLE_NAME + "_0";
    private static final String LAST_NAME_COL = TABLE_NAME + "_1";
    private static final String EMPLOYEE_ID_COL = TABLE_NAME + "_2";

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
    public void testRegisterDropSoupWithFTS() {
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
     * Test inserting rows in soup that uses full-text search indices
     */
    public void testInsertWithFTS() throws JSONException {
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
            JSONTestHelper.assertSameJSONArray("Wrong columns", new JSONArray(new String[]{"id", "soup", "created", "lastModified", FIRST_NAME_COL, LAST_NAME_COL, EMPLOYEE_ID_COL}), new JSONArray(c.getColumnNames()));

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
            JSONTestHelper.assertSameJSONArray("Wrong columns", new JSONArray(new String[]{FIRST_NAME_COL, LAST_NAME_COL}), new JSONArray(c.getColumnNames()));

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
     * Test deleting rows in soup that uses full-text search indices
     */
    public void testDeleteWithFTS() throws JSONException {
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
     * Load some datq in the smart store
     * @throws JSONException
     */
    private void loadData() throws JSONException {
        // Employees
        createEmployee("Christine", "Haas", "00010");
        createEmployee("Michael", "Thompson", "00020");
        createEmployee("Sally", "Kwan", "00310");
        createEmployee("John", "Geyer", "00040");
        createEmployee("Irving", "Stern", "00050");
        createEmployee("Eva", "Pulaski", "00060");
        createEmployee("Eileen", "Henderson", "00070");
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
