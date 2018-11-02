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

import androidx.test.filters.LargeTest;
import android.util.Log;

import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;

import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Set of tests for the smart store loading numerous and/or large entries and querying them back
 */
@RunWith(Parameterized.class)
@LargeTest
public class SmartStoreLoadTest extends SmartStoreLoadTestCase {

    @Parameterized.Parameter(0) public String testName;
    @Parameterized.Parameter(1) public Type indexType;
    @Parameterized.Parameter(2) public int numberEntries;
    @Parameterized.Parameter(3) public int numberFieldsPerEntry;
    @Parameterized.Parameter(4) public int numberCharactersPerField;
    @Parameterized.Parameter(5) public int numberIndexes;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"UpsertQuery1StringIndex1field20characters", Type.string, NUMBER_ENTRIES, 1, 20, 1},
                {"UpsertQuery1StringIndex1field1000characters", Type.string, NUMBER_ENTRIES, 1, 1000, 1},
                {"UpsertQuery1StringIndex10fields20characters", Type.string, NUMBER_ENTRIES, 10, 20, 1},
                {"UpsertQuery10StringIndexes10fields20characters", Type.string, NUMBER_ENTRIES, 10, 20, 10},
                {"UpsertQuery1JSON1Index1field20characters", Type.json1, NUMBER_ENTRIES, 1, 20, 1},
                {"UpsertQuery1JSON1Index1field1000characters", Type.json1, NUMBER_ENTRIES, 1, 1000, 1},
                {"UpsertQuery1JSON1Index10fields20characters", Type.json1, NUMBER_ENTRIES, 10, 20, 1},
                {"UpsertQuery10JSON1Indexes10fields20characters", Type.json1, NUMBER_ENTRIES, 10, 20, 10}
        });
    }

    @Test
    public void test() throws JSONException {
        tryUpsertQuery(indexType, numberEntries, numberFieldsPerEntry, numberCharactersPerField, numberIndexes);
    }

    private void tryUpsertQuery(Type indexType, int numberEntries, int numberFieldsPerEntry, int numberCharactersPerField, int numberIndexes) throws JSONException {
        setupSoup(TEST_SOUP, numberIndexes, indexType);
        upsertEntries(numberEntries / NUMBER_ENTRIES_PER_BATCH, NUMBER_ENTRIES_PER_BATCH, numberFieldsPerEntry, numberCharactersPerField);
        queryEntries();
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
}
