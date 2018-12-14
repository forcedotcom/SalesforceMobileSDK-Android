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

import com.salesforce.androidsdk.analytics.security.Encryptor;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;
import com.salesforce.androidsdk.smartstore.store.SoupSpec;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * More load tests for smartstore - using external storage
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class SmartStoreOtherLoadExternalStorageTest extends SmartStoreOtherLoadTest {

    static final int LARGE_BYTES = 512 * 1024;

    @Override
    protected String getEncryptionKey() {
        return Encryptor.hash("test123", "hashing-key");
    }

    @Override
    protected void registerSoup(SmartStore store, String soupName, IndexSpec[] indexSpecs) {
        store.registerSoupWithSpec(new SoupSpec(soupName, SoupSpec.FEATURE_EXTERNAL_STORAGE), indexSpecs);
    }

    // Test very large payloads for smartstore
    @Test
    public void testUpsertLargePayload() throws JSONException {
        setupSoup(TEST_SOUP, 1, Type.string);
        JSONObject entry = new JSONObject();
        for (int i = 0; i < 5; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < LARGE_BYTES; j++) {
                sb.append(i);
            }
            entry.put("value_" + i, sb.toString());
        }

        // Upsert
        long start = System.currentTimeMillis();
        store.upsert(TEST_SOUP, entry);
        long end = System.currentTimeMillis();

        // Log time taken
        Log.i("SmartStoreLoadTest", "Upserting 5MB+ payload time taken: " + (end - start) + " ms");

        // Verify
        JSONArray result = store.retrieve(TEST_SOUP, 1L);
        for (int i = 0; i < 5; i++) {
            Assert.assertTrue("Value at index " + i + " is incorrect", result.getJSONObject(0).getString("value_" + i).startsWith("" + i));
        }
    }

    @Override
    public void testAlterSoupJSON1Indexing() throws JSONException {
        // json1 is not compatible with external storage.
    }
}
