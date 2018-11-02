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

import androidx.test.filters.LargeTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import android.util.Log;

import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * More load tests for smartstore
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class SmartStoreOtherLoadTest extends SmartStoreLoadTestCase {

    @Test
    public void testAlterSoupClassicIndexing() throws JSONException {
        tryAlterSoup(Type.string);
    }

    @Test
    public void testAlterSoupJSON1Indexing() throws JSONException {
        tryAlterSoup(Type.json1);
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
}
