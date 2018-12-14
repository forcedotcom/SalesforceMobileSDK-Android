/*
 * Copyright (c) 2018-present, salesforce.com, inc.
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

import androidx.test.platform.app.InstrumentationRegistry;
import android.util.Log;

import com.salesforce.androidsdk.smartstore.store.DBOpenHelper;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Super class for smartstore load tests
 */
public class SmartStoreLoadTestCase extends SmartStoreTestCase {

    protected static final String TEST_SOUP = "test_soup";

    protected static final int NUMBER_ENTRIES = 1000;
    protected static final int NUMBER_ENTRIES_PER_BATCH = 100;
    protected static final int NS_IN_MS = 1000000;

    @Before
    public void setUp() throws Exception {
        final String dbPath = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationInfo().dataDir + "/databases";
        final File fileDir = new File(dbPath);
        DBOpenHelper.deleteAllUserDatabases(InstrumentationRegistry.getInstrumentation().getTargetContext());
        DBOpenHelper.deleteDatabase(InstrumentationRegistry.getInstrumentation().getTargetContext(), null);
        DBOpenHelper.removeAllFiles(fileDir);
        super.setUp();
    }

    protected String getEncryptionKey() {
        return "";
    }

    protected String getTag() {
        return getClass().getSimpleName();
    }

    protected void setupSoup(String soupName, int numberIndexes, Type indexType) {
        IndexSpec[] indexSpecs = new IndexSpec[numberIndexes];
        for (int indexNumber=0; indexNumber<numberIndexes; indexNumber++) {
            indexSpecs[indexNumber] = new IndexSpec("k_" + indexNumber, indexType);
        }
        registerSoup(store, soupName, indexSpecs);
        Log.i(getTag(), String.format("Creating table with %d %s indexes", numberIndexes, indexType));
    }

    protected void upsertEntries(int numberBatches, int numberEntriesPerBatch, int numberFieldsPerEntry, int numberCharactersPerField) throws JSONException {
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

    protected String pad(String s, int numberCharacters) {
        StringBuffer sb = new StringBuffer(numberCharacters);
        sb.append(s);
        for (int i=s.length(); i<numberCharacters; i++) {
            sb.append("x");
        }
        return sb.toString();
    }

    protected double average(List<Long> times) {
        double avg = 0;
        for (int i=0; i<times.size(); i++) {
            avg += times.get(i);
        }
        avg /= times.size();
        return avg;
    }

    protected void alterSoup(String msg, boolean reIndexData, IndexSpec[] indexSpecs) throws JSONException {
        long start = System.nanoTime();
        store.alterSoup(TEST_SOUP, indexSpecs, reIndexData);
        double duration = System.nanoTime() - start;
        Log.i(getTag(), String.format("%s completed in: %.3f ms", msg, duration/ NS_IN_MS));
    }
}
