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

import android.content.Context;
import android.test.InstrumentationTestCase;
import android.util.Log;

import com.salesforce.androidsdk.smartstore.store.DBHelper;
import com.salesforce.androidsdk.smartstore.store.DBOpenHelper;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Set of tests for the smart store loading numerous and/or large entries and querying them back
 */
public class SmartStoreLoadTest extends SmartStoreTestCase {

    protected static final String TEST_SOUP = "test_soup";

    private static final int NUMBER_ENTRIES = 1000;
    private static final int NUMBER_ENTRIES_PER_BATCH = 100;
    private static final int NS_IN_MS = 1000000;

    //
    // Tests
    //

    public void testUpsertQuery1StringIndex1field20characters() throws JSONException {
        tryUpsertQuery(Type.string, NUMBER_ENTRIES, 1, 20, 1);
    }

    public void testUpsertQuery1StringIndex1field1000characters() throws JSONException {
        tryUpsertQuery(Type.string, NUMBER_ENTRIES, 1, 1000, 1);
    }

    public void testUpsertQuery1StringIndex10fields20characters() throws JSONException {
        tryUpsertQuery(Type.string, NUMBER_ENTRIES, 10, 20, 1);
    }

    public void testUpsertQuery10StringIndexes10fields20characters() throws JSONException {
        tryUpsertQuery(Type.string, NUMBER_ENTRIES, 10, 20, 10);
    }

    public void testUpsertQuery1JSON1Index1field20characters() throws JSONException {
        tryUpsertQuery(Type.json1, NUMBER_ENTRIES, 1, 20, 1);
    }

    public void testUpsertQuery1JSON1Index1field1000characters() throws JSONException {
        tryUpsertQuery(Type.json1, NUMBER_ENTRIES, 1, 1000, 1);
    }

    public void testUpsertQuery1JSON1Index10fields20characters() throws JSONException {
        tryUpsertQuery(Type.json1, NUMBER_ENTRIES, 10, 20, 1);
    }

    public void testUpsertQuery10JSON1Indexes10fields20characters() throws JSONException {
        tryUpsertQuery(Type.json1, NUMBER_ENTRIES, 10, 20, 10);
    }

    public void testAlterSoupClassicIndexing() throws JSONException {
        tryAlterSoup(Type.string);
    }

    public void testAlterSoupJSON1Indexing() throws JSONException {
        tryAlterSoup(Type.json1);
    }


    //
    // Helper methods
    //
    protected String getPasscode() {
        return "";
    }

    protected String getTag() {
        return getClass().getSimpleName();
    }

    private void tryUpsertQuery(Type indexType, int numberEntries, int numberFieldsPerEntry, int numberCharactersPerField, int numberIndexes) throws JSONException {
        setupSoup(TEST_SOUP, numberIndexes, indexType);
        upsertEntries(numberEntries / NUMBER_ENTRIES_PER_BATCH, NUMBER_ENTRIES_PER_BATCH, numberFieldsPerEntry, numberCharactersPerField);
        queryEntries();
    }

    protected void setupSoup(String soupName, int numberIndexes, Type indexType) {
        IndexSpec[] indexSpecs = new IndexSpec[numberIndexes];
        for (int indexNumber=0; indexNumber<numberIndexes; indexNumber++) {
            indexSpecs[indexNumber] = new IndexSpec("k_" + indexNumber, indexType);
        }
        registerSoup(store, soupName, indexSpecs);
        Log.i(getTag(), String.format("Creating table with %d %s indexes", numberIndexes, indexType));
    }

    private void upsertEntries(int numberBatches, int numberEntriesPerBatch, int numberFieldsPerEntry, int numberCharactersPerField) throws JSONException {
        List<Long> times = new ArrayList<Long>();
        for (int batchNumber=0; batchNumber<numberBatches; batchNumber++) {
            long start = System.nanoTime();
            store.beginTransaction();
            for (int entryNumber=0; entryNumber<numberEntriesPerBatch; entryNumber++) {
                JSONObject entry = new JSONObject();
                for (int fieldNumber=0; fieldNumber<numberFieldsPerEntry; fieldNumber++) {
                    String value = pad( "v_" + batchNumber + "_" + entryNumber + "_" + fieldNumber + "_", numberCharactersPerField);
                    entry.put("k_" + fieldNumber, value);
                }
                store.upsert(TEST_SOUP, entry, SmartStore.SOUP_ENTRY_ID, false);
            }
            store.setTransactionSuccessful();
            store.endTransaction();
            long end = System.nanoTime();
            times.add(end - start);
        }
        double avgMilliseconds = average(times) / NS_IN_MS;
        Log.i(getTag(), String.format("Upserting %d entries with %d per batch with %d fields with %d characters: average time per batch --> %.3f ms",
                numberBatches * numberEntriesPerBatch, numberEntriesPerBatch, numberFieldsPerEntry, numberCharactersPerField, avgMilliseconds));
    }

    private void queryEntries() throws JSONException {
        // Should find all
        queryEntries(QuerySpec.buildAllQuerySpec(TEST_SOUP, null, null, 1));
        queryEntries(QuerySpec.buildAllQuerySpec(TEST_SOUP, null, null, 10));
        queryEntries(QuerySpec.buildAllQuerySpec(TEST_SOUP, null, null, 100));

        // Should find 100
        queryEntries(QuerySpec.buildLikeQuerySpec(TEST_SOUP, "k_0", "v_0_%", null, null, 1));
        queryEntries(QuerySpec.buildLikeQuerySpec(TEST_SOUP, "k_0", "v_0_%", null, null, 10));
        queryEntries(QuerySpec.buildLikeQuerySpec(TEST_SOUP, "k_0", "v_0_%", null, null, 100));

        // Should find 10
        queryEntries(QuerySpec.buildLikeQuerySpec(TEST_SOUP, "k_0", "v_0_0_%", null, null, 1));
        queryEntries(QuerySpec.buildLikeQuerySpec(TEST_SOUP, "k_0", "v_0_0_%", null, null, 10));

        // Should find none
        queryEntries(QuerySpec.buildExactQuerySpec(TEST_SOUP, "k_0", "missing", null, null, 1));
    }


    private void queryEntries(QuerySpec querySpec) throws JSONException {
        List<Long> times = new ArrayList<Long>();
        int countMatches = 0;
        boolean hasMore = true;
        for (int pageIndex = 0; hasMore; pageIndex++) {
            long start = System.nanoTime();
            JSONArray results = store.query(querySpec, pageIndex);
            long end = System.nanoTime();
            times.add(end - start);
            hasMore = (results.length() == querySpec.pageSize);
            countMatches += results.length();
        }
        double avgMilliseconds = average(times) / NS_IN_MS;
        Log.i(getTag(), String.format("Querying with %s query matching %d entries and %d page size: average time per page --> %.3f ms",
                querySpec.queryType, countMatches, querySpec.pageSize, avgMilliseconds));
    }

    private String pad(String s, int numberCharacters) {
        StringBuffer sb = new StringBuffer(numberCharacters);
        sb.append(s);
        for (int i=s.length(); i<numberCharacters; i++) {
            sb.append("x");
        }
        return sb.toString();
    }

    private double average(List<Long> times) {
        double avg = 0;
        for (int i=0; i<times.size(); i++) {
            avg += times.get(i);
        }
        avg /= times.size();
        return avg;
    }

    private void tryAlterSoup(Type indexType) throws JSONException {
        Log.i(getTag(), "In testAlterSoup");
        Log.i(getTag(), String.format("Initial database size: %d bytes", store.getDatabaseSize()));
        setupSoup(TEST_SOUP, 1, indexType);
        upsertEntries(NUMBER_ENTRIES / NUMBER_ENTRIES_PER_BATCH, NUMBER_ENTRIES_PER_BATCH, 10, 20);
        Log.i(getTag(), String.format("Database size after: %d bytes", store.getDatabaseSize()));

        // Without indexing for new index specs
        alterSoup("Adding one index / no re-indexing", false, new IndexSpec[]{new IndexSpec("k_0", indexType), new IndexSpec("k_1", indexType)});
        alterSoup("Adding one index / dropping one index / no re-indexing", false, new IndexSpec[] {new IndexSpec("k_0", indexType), new IndexSpec("k_2", indexType)});
        alterSoup("Dropping one index / no re-indexing", false, new IndexSpec[] {new IndexSpec("k_0", indexType)});

        // With indexing for new index specs
        alterSoup("Adding one index / with re-indexing", true, new IndexSpec[] {new IndexSpec("k_0", indexType), new IndexSpec("k_1", indexType)});
        alterSoup("Adding one index / dropping one index / with re-indexing", true, new IndexSpec[] {new IndexSpec("k_0", indexType), new IndexSpec("k_2", indexType)});
        alterSoup("Dropping one index / with re-indexing", true, new IndexSpec[] {new IndexSpec("k_0", indexType)});
    }
    
    private void alterSoup(String msg, boolean reIndexData, IndexSpec[] indexSpecs) throws JSONException {
        long start = System.nanoTime();
        store.alterSoup(TEST_SOUP, indexSpecs, reIndexData);
        double duration = System.nanoTime() - start;
        Log.i(getTag(), String.format("%s completed in: %.3f ms", msg, duration/ NS_IN_MS));
    }
}
