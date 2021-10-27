/*
 * Copyright (c) 2021-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.smartstore.store;

import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Concurrency tests for smartstore
 */

@RunWith(AndroidJUnit4.class)
@MediumTest
public class SmartStoreConcurrencyTest extends SmartStoreTestCase {

    private static final String TAG = SmartStoreConcurrencyTest.class.getSimpleName();

    protected static final String TEST_SOUP = "test_soup";

    private static final int MAX_READERS = 50;
    private static final int MAX_WRITERS = 50;
    private static final int READS_PER_READER = 50;
    private static final int WRITES_PER_WRITER = 50;

    private ExecutorService pool;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        Assert.assertFalse("Table for test_soup should not exist", hasTable("TABLE_1"));
        Assert.assertFalse("Soup test_soup should not exist", store.hasSoup(TEST_SOUP));
        registerSoup(store, TEST_SOUP, new IndexSpec[] { new IndexSpec("key", Type.string) });
        Assert.assertEquals("Table for test_soup was expected to be called TABLE_1", "TABLE_1", getSoupTableName(TEST_SOUP));
        Assert.assertTrue("Table for test_soup should now exist", hasTable("TABLE_1"));
        Assert.assertTrue("Soup test_soup should now exist", store.hasSoup(TEST_SOUP));

        // Setting up thread pool
        pool = Executors.newFixedThreadPool(MAX_READERS + MAX_WRITERS);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    @Override
    protected String getEncryptionKey() {
        return "";
    }


    @Test
    public void testOneReader() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        spawnReaders(1, READS_PER_READER, latch);
        latch.await();
        checkRowsExact(0, 0);
    }

    @Test
    public void testOneWriter() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        spawnWriters(1, WRITES_PER_WRITER, latch);
        latch.await();
        checkRowsExact(1, WRITES_PER_WRITER);
    }


    @Test
    public void testOneReaderOneWriter() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        spawnReaders(1, READS_PER_READER, latch);
        spawnWriters(1, WRITES_PER_WRITER, latch);
        latch.await();
        checkRowsExact(1, WRITES_PER_WRITER);
    }


    @Test
    public void testManyReadersOneWriter() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(MAX_READERS+1);
        spawnReaders(MAX_READERS, READS_PER_READER, latch);
        spawnWriters(1, WRITES_PER_WRITER, latch);
        latch.await();
        checkRowsExact(1, WRITES_PER_WRITER);
    }

    @Test
    public void testOneReaderManyWriters() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(MAX_WRITERS+1);
        spawnReaders(1, READS_PER_READER, latch);
        spawnWriters(MAX_WRITERS, WRITES_PER_WRITER, latch);
        latch.await();
        checkRowsExact(MAX_WRITERS, WRITES_PER_WRITER);
    }

    @Test
    public void testManyReadersManyWriters() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(MAX_READERS+MAX_WRITERS);
        spawnReaders(MAX_READERS, READS_PER_READER, latch);
        spawnWriters(MAX_WRITERS, WRITES_PER_WRITER, latch);
        latch.await();
        checkRowsExact(MAX_WRITERS, WRITES_PER_WRITER);
    }


    //
    // Helper functions
    //

    /**
     * Make sure the database contains json objects of the right shape
     * @throws JSONException
     */
    private void checkRows() throws JSONException {
        try {
            JSONArray rows = store.query(QuerySpec.buildSmartQuerySpec(
                "select {test_soup:key}, {test_soup:value} from {test_soup} order by {test_soup:key}, {test_soup:value}",
                MAX_WRITERS*WRITES_PER_WRITER), 0);

            for (int i=0; i<rows.length(); i++) {
                JSONArray row = rows.getJSONArray(i);
                Assert.assertEquals(2, row.length());
                String key = row.getString(0);
                String value = row.getString(1);
                Assert.assertTrue("Wrong key:" + key, key.startsWith("writer-000"));
                Assert.assertTrue("Wrong value: " + value, value.startsWith("seq-000"));
            }

        } catch (JSONException e) {
            Assert.fail("Unexpected error: " + e);
        }
    }

    /**
     * Make sure the database contains EXACTLY the expected json objects
     * @throws JSONException
     */
    private void checkRowsExact(int countWriterThreads, int countWritesPerThread) {
        try {
            JSONArray rows = store.query(QuerySpec.buildSmartQuerySpec(
                "select {test_soup:key}, {test_soup:value} from {test_soup} order by {test_soup:key}, {test_soup:value}",
                MAX_WRITERS*WRITES_PER_WRITER), 0);

            Assert.assertEquals(countWriterThreads*countWritesPerThread, rows.length());

            for (int i=0; i<rows.length(); i++) {
                JSONArray row = rows.getJSONArray(i);
                Assert.assertEquals(2, row.length());
                String key = row.getString(0);
                String value = row.getString(1);
                String expectedKey =  "writer-" + padNumber(i/countWritesPerThread);
                String expectedValue = "seq-" + padNumber(i % countWritesPerThread);
                Assert.assertEquals(expectedKey, key);
                Assert.assertEquals(expectedValue, value);
            }

        } catch (JSONException e) {
            Assert.fail("Unexpected error: " + e);
        }
    }

    /**
     * Read all the rows from the test soup
     * @param threadId
     * @param seq
     */
    private void readAll(String threadId, String seq) {
        try {
            Log.i(TAG, "Starting " + seq + " for " + threadId);
            checkRows();
            Log.i(TAG, "Done     " + seq + " for " + threadId);
        } catch (JSONException e) {
            Assert.fail("Unexpected error: " + e);
        }
    }

    /**
     * Write one row to the test soup using threadId for the key field and seq for the value field
     * @param threadId
     * @param seq
     */
    private void writeOne(String threadId, String seq) {
        try {
            Log.i(TAG, "Starting " + seq + " for " + threadId);
            store.upsert(TEST_SOUP,
                new JSONObject("{\"key\": \"" + threadId + "\"; \"value\": \"" + seq + "\"}"),
                SmartStore.SOUP_ENTRY_ID, false);
            Log.i(TAG, "Done     " + seq + " for " + threadId);
        } catch (JSONException e) {
            Assert.fail("Unexpected error: " + e);
        }
    }

    /**
     * Spawn countThreads of reader threads that will each do countReadsPerThread queries
     * @param countReaderThreads
     * @param countReadsPerThread
     * @param latch
     */
    private void spawnReaders(int countReaderThreads, int countReadsPerThread, CountDownLatch latch) {
        for (int i = 0; i < countReaderThreads; i++) {
            int j = i;
            pool.execute(() -> {
                for (int seq = 0; seq < countReadsPerThread; seq++) {
                    readAll("reader-" + padNumber(j), "seq-" + padNumber(seq));
                }
                latch.countDown();
            });
        }
    }

    /**
     * Spawn countThreads of writer threads that will each do countWritesPerThread upserts
     * @param countWriterThreads
     * @param countWritesPerThread
     * @param latch
     */
    private void spawnWriters(int countWriterThreads, int countWritesPerThread, CountDownLatch latch) {
        for (int i = 0; i < countWriterThreads; i++) {
            int j = i;
            pool.execute(() -> {
                store.beginTransaction();
                for (int seq = 0; seq < countWritesPerThread; seq++) {
                    writeOne("writer-" + padNumber(j), "seq-" + padNumber(seq));
                }
                store.setTransactionSuccessful();
                store.endTransaction();
                latch.countDown();
            });
        }
    }

    private String padNumber(int n) {
        return String.format("%05d", n);
    }


}
