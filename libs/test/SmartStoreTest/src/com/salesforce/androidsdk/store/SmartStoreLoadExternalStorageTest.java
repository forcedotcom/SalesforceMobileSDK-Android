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

import com.salesforce.androidsdk.analytics.security.Encryptor;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;
import com.salesforce.androidsdk.smartstore.store.SoupSpec;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

/**
 * Set of tests for the smart store loading numerous and/or large entries and querying them back - using external storage
 */
@RunWith(Parameterized.class)
@LargeTest
public class SmartStoreLoadExternalStorageTest extends SmartStoreLoadTest {

    @Override
    protected String getEncryptionKey() {
        return Encryptor.hash("test123", "hashing-key");
    }

    @Override
    protected void registerSoup(SmartStore store, String soupName, IndexSpec[] indexSpecs) {
        store.registerSoupWithSpec(new SoupSpec(soupName, SoupSpec.FEATURE_EXTERNAL_STORAGE), indexSpecs);
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"UpsertQuery1StringIndex1field20characters", Type.string, NUMBER_ENTRIES, 1, 20, 1},
                {"UpsertQuery1StringIndex1field1000characters", Type.string, NUMBER_ENTRIES, 1, 1000, 1},
                {"UpsertQuery1StringIndex10fields20characters", Type.string, NUMBER_ENTRIES, 10, 20, 1},
                {"UpsertQuery10StringIndexes10fields20characters", Type.string, NUMBER_ENTRIES, 10, 20, 10}
        });
    }
}
