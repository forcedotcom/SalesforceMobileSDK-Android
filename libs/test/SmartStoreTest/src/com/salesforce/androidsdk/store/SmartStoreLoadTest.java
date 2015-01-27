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
import net.sqlcipher.database.SQLiteOpenHelper;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.test.InstrumentationTestCase;
import android.util.Log;

import com.salesforce.androidsdk.smartstore.store.DBHelper;
import com.salesforce.androidsdk.smartstore.store.DBOpenHelper;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec.Order;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;

/**
 * Set of tests for the smart store loading numerous and/or large entries and querying them back
 */
public class SmartStoreLoadTest extends InstrumentationTestCase {

	
	private static final int MAX_NUMBER_ENTRIES = 2048;
	private static final int MAX_NUMBER_FIELDS = 2048;
	private static final int MAX_FIELD_LENGTH = 65536;
	private static final int NUMBER_FIELDS_PER_ENTRY = 128;
	private static final int NUMBER_ENTRIES_PER_BATCH = 64;
	private static final int NUMBER_BATCHES = 128;
	private static final int QUERY_PAGE_SIZE = 64;
	private static final int NUMBER_BATCHES_ALTER_TEST = 64;
	
	private static final String TEST_SOUP = "test_soup";
	
	protected Context targetContext;
	private SmartStore store;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		targetContext = getInstrumentation().getTargetContext();
		final SQLiteOpenHelper dbOpenHelper = DBOpenHelper.getOpenHelper(targetContext, null);
		DBHelper.getInstance(dbOpenHelper.getWritableDatabase(getPasscode())).reset(targetContext, null);
		store = new SmartStore(dbOpenHelper, getPasscode());
		store.dropAllSoups();
		assertFalse("Soup test_soup should not exist", store.hasSoup(TEST_SOUP));
		store.registerSoup(TEST_SOUP, new IndexSpec[] {new IndexSpec("key", Type.string)});
		assertTrue("Soup test_soup should now exist", store.hasSoup(TEST_SOUP));
	}

	protected String getPasscode() {
		return "";
	}

	@Override
	protected void tearDown() throws Exception {
		final SQLiteDatabase db = DBOpenHelper.getOpenHelper(targetContext, null).getWritableDatabase(getPasscode());
		db.close();
		super.tearDown();
	}

    /**
     * TEST: Upsert 1,2,...,MAX_NUMBER_ENTRIES entries (with just a couple of fields) into a soup
     * @throws JSONException 
     */
    public void testUpsertManyEntries() throws JSONException {
        Log.i("SmartStoreLoadTest", "In testUpsertManyEntries");
        for (int k=1; k<MAX_NUMBER_ENTRIES; k*=2) {        
        	upsertManyEntries(k);
        }
    }

    private void upsertManyEntries(int k) throws JSONException {
        List<Long> times = new ArrayList<Long>();
    	store.beginTransaction();
        for (int i=0; i<k; i++) {
            JSONObject entry = new JSONObject();
            entry.put("key", "k_" + k + "_" + i);
            entry.put("value", "x");
            long start = System.currentTimeMillis();
            store.upsert(TEST_SOUP, entry);
            long end = System.currentTimeMillis();
            times.add(end-start);
        }
        store.setTransactionSuccessful();
        store.endTransaction();
        
        // Compute average time taken
        long avg = 0;
        for (int i=0; i<times.size(); i++) {
            avg += times.get(i);
        }
        avg /= times.size();
			
        // Log avg time taken
        Log.i("SmartStoreLoadTest", "upserting " + k + " entries avg time taken: " + avg + " ms");
    }


    /**
     * TEST: Upsert entries with 1,2,...,MAX_NUMBER_FIELDS into a soup
     * @throws JSONException 
     */
    public void testNumerousFields() throws JSONException {
        Log.i("SmartStoreLoadTest", "In testNumerousFields");
        for (int k=1; k<MAX_NUMBER_FIELDS; k*=2) {
        	upsertEntryWithNumerousFields(k);
        }
    }

    private void upsertEntryWithNumerousFields(int k) throws JSONException {
    	JSONObject entry = new JSONObject();
    	
        for (int i=0; i<k; i++) {
            entry.put("v"+i, "value_" + i);
        }

        // Upsert
        upsertEntry("upserting entry with " + k + "+ fields", entry);
    }

	private void upsertEntry(String msg, JSONObject entry) throws JSONException {
		long start = System.currentTimeMillis();
    	store.beginTransaction();
        store.upsert(TEST_SOUP, entry);
        store.setTransactionSuccessful();
        store.endTransaction();
        long end = System.currentTimeMillis();
        
        // Log time taken
        Log.i("SmartStoreLoadTest", msg + " time taken: " + (end-start) + " ms");
	}


    /**
     * TEST: Upsert entry with a value field that is 1,2, ... , MAX_FIELD_LENGTH long into a soup
     * @throws JSONException 
     */
    public void testIncreasingFieldLength() throws JSONException {
    	Log.i("SmartStoreLoadTest", "In testIncreasingFieldLength");
        for (int k=1; k<MAX_FIELD_LENGTH; k*=2) {
        	upsertEntryWithLargeField(k);
        }
    }

    private void upsertEntryWithLargeField(int k) throws JSONException {
    	Log.i("SmartStoreLoadTest", "upsertNextLargerFieldEntry " + k);

        StringBuilder sb = new StringBuilder();
        for (int i=0; i< k; i++) {
            sb.append("x");
        }

        JSONObject entry = new JSONObject();
        entry.put("key", "k" + k);
        entry.put("value", sb.toString());
	
        // Upsert
        upsertEntry("upserting entry with field with " + k + " characters", entry);
    }
    
    /**
     * TEST: Upsert MAX_NUMBER_ENTRIES entries into a soup and retrieve them back
     * @throws JSONException 
     */
	public void testAddAndRetrieveManyEntries() throws JSONException {
    	Log.i("SmartStoreLoadTest", "In testAddAndRetrieveManyEntries");

    	List<Long> soupEntryIds = new ArrayList<Long>();
        List<Long> times = new ArrayList<Long>();
    	store.beginTransaction();
    	for (int i=0; i < MAX_NUMBER_ENTRIES; i++) {
            String paddedIndex = String.format("%05d", i);
    		JSONObject entry = new JSONObject();
            entry.put("Name", "Todd Stellanova" + paddedIndex);
            entry.put("Id", "003" + paddedIndex);
            JSONObject attributes = new JSONObject();
            attributes.put("type", "Contact");
            attributes.put("url", "/foo/Contact" + paddedIndex);
            entry.put("attributes", attributes);

            long start = System.currentTimeMillis();
            JSONObject upsertedEntry = store.upsert(TEST_SOUP, entry);
            Long soupEntryId = upsertedEntry.getLong(SmartStore.SOUP_ENTRY_ID);
            soupEntryIds.add(soupEntryId);
            long end = System.currentTimeMillis();
            times.add(end-start);
        }
        store.setTransactionSuccessful();
        store.endTransaction();

        // Compute average time taken
        long avg = 0;
        for (int i=0; i<times.size(); i++) {
            avg += times.get(i);
        }
        avg /= times.size();
			
        // Log avg time taken
        Log.i("SmartStoreLoadTest", "upserting " + MAX_NUMBER_ENTRIES + " entries avg time taken: " + avg + " ms");

        // Retrieve
        long start = System.currentTimeMillis();
    	store.retrieve(TEST_SOUP, soupEntryIds.toArray(new Long[]{}));
        long end = System.currentTimeMillis();

        // Log retrieve time taken
        Log.i("SmartStoreLoadTest", "retrieve " + MAX_NUMBER_ENTRIES + " entries time taken: " + (end-start) + " ms");
    }

    /**
     * TEST: Upsert NUMBER_BATCHES batches of NUMBER_ENTRIES_PER_BATCH entries with NUMBER_FIELDS_PER_ENTRY fields into a soup and query all (fetching only a page of QUERY_PAGE_SIZE entries)
     * @throws JSONException 
     */
    public void testUpsertAndQueryEntries() throws JSONException {
        Log.i("SmartStoreLoadTest", "In testUpsertAndQueryEntries");
        for (int i=0; i<NUMBER_BATCHES; i++) {
        	upsertManyEntriesWithManyFields(i);
        	queryEntries(i);
        }
    }

    private void upsertManyEntriesWithManyFields(int batch) throws JSONException {
        int startKey = batch * NUMBER_ENTRIES_PER_BATCH;
        int endKey = (batch+1) * NUMBER_ENTRIES_PER_BATCH;

        List<Long> times = new ArrayList<Long>();
    	store.beginTransaction();
        for (int i=startKey; i<endKey; i++) {
            JSONObject entry = new JSONObject();
            entry.put("key", "k_" + i);
            entry.put("value", "x");
            for (int j=0; j<NUMBER_FIELDS_PER_ENTRY; j++) {
            	entry.put("v" + j, "value_" + j);
            }
            long start = System.currentTimeMillis();
            store.upsert(TEST_SOUP, entry);
            long end = System.currentTimeMillis();
            times.add(end-start);
        }
        store.setTransactionSuccessful();
        store.endTransaction();
        
        // Compute average time taken
        long avg = 0;
        for (int i=0; i<times.size(); i++) {
            avg += times.get(i);
        }
        avg /= times.size();
			
        // Log avg time taken
        Log.i("SmartStoreLoadTest", "upserting " + NUMBER_ENTRIES_PER_BATCH + " entries avg time taken: " + avg + " ms");
    }

	private void queryEntries(int batch) throws JSONException {
		// Query all
        QuerySpec qs = QuerySpec.buildAllQuerySpec(TEST_SOUP, "key", Order.ascending, QUERY_PAGE_SIZE);        
        long start = System.currentTimeMillis();
        store.query(qs, 0);
        long end = System.currentTimeMillis();
        
        // Log query time
        Log.i("SmartStoreLoadTest", "querying out of soup with " + (batch+1)*NUMBER_ENTRIES_PER_BATCH + " entries time taken: " + (end-start) + " ms");
	}

    /**
     * TEST: NUMBER_BATCHES batches of NUMBER_ENTRIES_PER_BATCH entries with NUMBER_FIELDS_PER_ENTRY fields into a soup and alter the soup several times (adding/dropping indexes with/without re-indexing)
     * @throws JSONException
     */
    public void testAlterSoup() throws JSONException {
    	Log.i("SmartStoreLoadTest", "In testAlterSoup");
    	Log.i("SmartStoreLoadTest.testAlterSoup", "Initial database size: " + store.getDatabaseSize() + " bytes");
        for (int i=0; i<NUMBER_BATCHES_ALTER_TEST; i++) {
        	upsertManyEntriesWithManyFields(i);
        }
    	Log.i("SmartStoreLoadTest.testAlterSoup", "Database size after inserting " + NUMBER_ENTRIES_PER_BATCH * NUMBER_BATCHES_ALTER_TEST + " records with " + NUMBER_FIELDS_PER_ENTRY + " fields: " + store.getDatabaseSize() + " bytes");

    	// Without indexing for new index specs
    	alterSoup("Adding one index / no re-indexing", false, new IndexSpec[] {new IndexSpec("key", Type.string), new IndexSpec("v0", Type.string)});
    	alterSoup("Adding one index / dropping one index / no re-indexing", false, new IndexSpec[] {new IndexSpec("key", Type.string), new IndexSpec("v1", Type.string)});
    	alterSoup("Dropping two indexes / no re-indexing", false, new IndexSpec[] {new IndexSpec("key", Type.string)});

    	// With indexing for new index specs
    	alterSoup("Adding one index / with re-indexing", true, new IndexSpec[] {new IndexSpec("key", Type.string), new IndexSpec("v0", Type.string)});
    	alterSoup("Adding one index / dropping one index / with re-indexing", true, new IndexSpec[] {new IndexSpec("key", Type.string), new IndexSpec("v1", Type.string)});
    	alterSoup("Dropping two indexes / with re-indexing", true, new IndexSpec[] {new IndexSpec("key", Type.string)});
    }
    
    private void alterSoup(String msg, boolean reIndexData, IndexSpec[] indexSpecs) throws JSONException {
        long start = System.currentTimeMillis();
        store.alterSoup(TEST_SOUP, indexSpecs, reIndexData);
        long duration = System.currentTimeMillis() - start;
        Log.i("SmartStoreLoadTest.alterSoup", msg + " completed in: " + duration + " ms");
    }
}
