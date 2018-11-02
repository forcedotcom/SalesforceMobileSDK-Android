/*
 * Copyright (c) 2015-present, salesforce.com, inc.
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

import androidx.test.filters.LargeTest;
import android.util.Log;

import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

/**
 * Tests to compare speed of smartstore full-text-search indices with regular indices
 */
@RunWith(Parameterized.class)
@LargeTest
public class SmartStoreFullTextSearchSpeedTest extends SmartStoreTestCase {

    public static final String TAG = "SmartStoreFTSSpeedTest";

    // Animals A..Y
    public static final String[] ANIMALS = new String[]{"alligator", "ant", "bear", "bee", "bird", "camel", "cat",
            "cheetah", "chicken", "chimpanzee", "cow", "crocodile", "deer", "dog", "dolphin",
            "duck", "eagle", "elephant", "fish", "fly", "fox", "frog", "giraffe", "goat",
            "goldfish", "hamster", "hippopotamus", "horse", "iguana", "impala", "jaguar", "jellyfish", "kangaroo", "kitten", "lion",
            "lobster", "monkey", "nightingale", "octopus", "owl", "panda", "pig", "puppy", "quail", "rabbit", "rat",
            "scorpion", "seal", "shark", "sheep", "snail", "snake", "spider", "squirrel",
            "tiger", "turtle", "umbrellabird", "vulture", "wolf", "xantus", "xerus", "yak"};

    public static final String ANIMALS_SOUP = "animals";
    public static final String TEXT_COL = "text";

    @Parameterized.Parameter(0) public String testName;
    @Parameterized.Parameter(1) public int rowsPerAnimal;
    @Parameterized.Parameter(2) public int matchingRowsPerAnimal;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"Search1000RowsOneMatch", 40, 1},
                {"Search1000RowsManyMatches", 40, 40},
                {"Search10000RowsOneMatch", 400, 1},
                {"Search10000RowsManyMatches", 400, 400} //,
                // {"testSearch100000RowsOneMatch", 4000, 1} // Slow - uncomment when collecting performance data
        });
    }

    @Override
    protected String getEncryptionKey() {
        return "";
    }

    @Test
    public void test() throws JSONException {
        trySearch(rowsPerAnimal, matchingRowsPerAnimal);
    }

    private void trySearch(int rowsPerAnimal, int matchingRowsPerAnimal) throws JSONException {
        double totalInsertTimeString = setupData(Type.string, rowsPerAnimal, matchingRowsPerAnimal);
        double avgQueryTimeString = queryData(Type.string, rowsPerAnimal, matchingRowsPerAnimal);
        store.dropAllSoups();
        double totalInsertTimeFullText = setupData(Type.full_text, rowsPerAnimal, matchingRowsPerAnimal);
        double avgQueryTimeFullText = queryData(Type.full_text, rowsPerAnimal, matchingRowsPerAnimal);
        store.dropAllSoups();
        Log.i(TAG, String.format("Search rows=%d matchingRows=%d avgQueryTimeString=%.4fs avgQueryTimeFullText=%.4fs (%.2f%%) totalInsertTimeString=%.3fs totalInsertTimeFullText=%.3fs (%.2f%%)",
                    rowsPerAnimal * 25,
                    matchingRowsPerAnimal,
                    avgQueryTimeString,
                    avgQueryTimeFullText,
                    100*avgQueryTimeFullText / avgQueryTimeString,
                    totalInsertTimeString,
                    totalInsertTimeFullText,
                    100*totalInsertTimeFullText / totalInsertTimeString));
    }

    /**
     * @return total insert time in seconds
     */
    private double setupData(Type textFieldType, int rowsPerAnimal, int matchingRowsPerAnimal) throws JSONException {
        long totalInsertTime = 0;
        store.registerSoup(ANIMALS_SOUP, new IndexSpec[]{new IndexSpec(TEXT_COL, textFieldType)});
        try {
            store.beginTransaction();
            for (int i=0; i < 25; i++) {
                int charToMatch = i + 'a';
                for (int j=0; j < rowsPerAnimal; j++) {
                    String prefix = String.format("%07d", j % (rowsPerAnimal / matchingRowsPerAnimal));
                    StringBuilder text = new StringBuilder();
                    for (String animal : ANIMALS) {
                        if (animal.charAt(0) == charToMatch) {
                            text.append(prefix).append(animal).append(" ");
                        }
                    }
                    JSONObject elt = new JSONObject();
                    elt.put(TEXT_COL, text.toString());
                    long start = System.nanoTime();
                    store.create(ANIMALS_SOUP, elt, false);
                    totalInsertTime += System.nanoTime() - start;
                }
            }
            store.setTransactionSuccessful();
        } finally {
            store.endTransaction();
        }
        return nanosToSeconds(totalInsertTime);
    }

    /**
     * @return avg query time in seconds
     */
    private double queryData(Type textFieldType, int rowsPerAnimal, int matchingRowsPerAnimal) throws JSONException {
        long totalQueryTime = 0;
        for (String animal : ANIMALS) {
            String prefix = String.format("%07d", (int) (Math.random()*(rowsPerAnimal/matchingRowsPerAnimal)));
            String stringToMatch = prefix + animal;
            QuerySpec querySpec = textFieldType == Type.full_text
                    ? QuerySpec.buildMatchQuerySpec(ANIMALS_SOUP, TEXT_COL, stringToMatch, null, null, rowsPerAnimal)
                    : QuerySpec.buildLikeQuerySpec(ANIMALS_SOUP, TEXT_COL, "%" + stringToMatch + "%", null, null, rowsPerAnimal);
            long start = System.nanoTime();
            JSONArray results = store.query(querySpec, 0);
            totalQueryTime += System.nanoTime() - start;
            validateResults(matchingRowsPerAnimal, stringToMatch, results);
        }
        return nanosToSeconds(totalQueryTime)/ANIMALS.length;
    }

    private void validateResults(int expectedRows, String stringToMatch, JSONArray results) throws JSONException {
        Assert.assertEquals("Wrong number of results", expectedRows, results.length());
        for (int i=0; i<results.length(); i++) {
            String text = results.getJSONObject(i).getString(TEXT_COL);
            Assert.assertTrue("Invalid result [" + text + "] for search on [" + stringToMatch + "]", text.contains(stringToMatch));
        }
    }

    private double nanosToSeconds(long nanos) {
        return nanos / 1000000000.0;
    }
}
