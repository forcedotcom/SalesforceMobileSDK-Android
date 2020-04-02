/*
 * Copyright (c) 2020-present, salesforce.com, inc.
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
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.smartstore.app.SmartStoreSDKManager;
import com.salesforce.androidsdk.smartstore.store.KeyValueEncryptedFileStore;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class KeyValueEncryptedFileStoreTest {

    public static final String TEST_STORE = "TEST_STORE";
    public static final int NUM_ENTRIES = 100;

    private Context context;
    private KeyValueEncryptedFileStore keyValueStore;

    @Before
    public void setUp() throws Exception {
        context =
                InstrumentationRegistry.getInstrumentation()
                        .getTargetContext()
                        .getApplicationContext();
        SmartStoreSDKManager.initNative(context, null);
        keyValueStore =
                new KeyValueEncryptedFileStore(
                        context, TEST_STORE, SalesforceSDKManager.getEncryptionKey());
        Assert.assertTrue("Store directory should exist", getStoreDir(TEST_STORE).exists());
        Assert.assertTrue("Store should be empty", keyValueStore.isEmpty());
    }

    @After
    public void tearDown() throws Exception {
        keyValueStore.deleteAll();
        getStoreDir(TEST_STORE).delete();
    }

    /** Test saving values and counting them */
    @Test
    public void testSaveValueCount() {
        for (int i = 0; i < NUM_ENTRIES; i++) {
            String key = "key" + i;
            String value = "value" + i;
            Assert.assertEquals("Wrong count before save", i, keyValueStore.count());
            keyValueStore.saveValue(key, value);
            Assert.assertEquals("Wrong count after save", i + 1, keyValueStore.count());
        }
    }

    /** Test saving values and getting them back */
    @Test
    public void testSaveValueGetValue() {
        for (int i = 0; i < NUM_ENTRIES; i++) {
            String key = "key" + i;
            String value = "value" + i;
            keyValueStore.saveValue(key, value);
        }

        for (int i = 0; i < NUM_ENTRIES; i++) {
            String key = "key" + i;
            String expectedValue = "value" + i;
            Assert.assertEquals(
                    "Wrong value for key: " + key, expectedValue, keyValueStore.getValue(key));
        }
    }

    /** Test saving values and getting them back as streams */
    @Test
    public void testSaveValueGetStream() {
        for (int i = 0; i < NUM_ENTRIES; i++) {
            String key = "key" + i;
            String value = "value" + i;
            keyValueStore.saveValue(key, value);
        }

        for (int i = 0; i < NUM_ENTRIES; i++) {
            String key = "key" + i;
            String expectedValue = "value" + i;
            Assert.assertEquals(
                    "Wrong value (from stream) for key: " + key,
                    expectedValue,
                    streamToString(keyValueStore.getStream(key)));
        }
    }

    /** Test saving values and deleting them */
    @Test
    public void testSaveValueDelete() {
        for (int i = 0; i < NUM_ENTRIES; i++) {
            String key = "key" + i;
            String value = "value" + i;
            keyValueStore.saveValue(key, value);
        }

        for (int i = 0; i < NUM_ENTRIES; i++) {
            String key = "key" + i;
            Assert.assertNotNull(
                    "No value found for key when expected:" + key, keyValueStore.getValue(key));
            Assert.assertEquals(
                    "Wrong count before delete", NUM_ENTRIES - i, keyValueStore.count());
            keyValueStore.deleteValue(key);
            Assert.assertEquals(
                    "Wrong count after delete", NUM_ENTRIES - (i + 1), keyValueStore.count());
            Assert.assertNull(
                    "Value found for key when not expected:" + key, keyValueStore.getValue(key));
        }
    }

    /** Test saving values and deleting them all at once */
    @Test
    public void testSaveValueDeleteAll() {
        for (int i = 0; i < NUM_ENTRIES; i++) {
            String key = "key" + i;
            String value = "value" + i;
            keyValueStore.saveValue(key, value);
        }

        Assert.assertEquals("Wrong count before deleteAll", NUM_ENTRIES, keyValueStore.count());
        keyValueStore.deleteAll();
        Assert.assertEquals("Wrong count after deleteAll", 0, keyValueStore.count());

        for (int i = 0; i < NUM_ENTRIES; i++) {
            String key = "key" + i;
            Assert.assertNull(
                    "Value found for key when not expected:" + key, keyValueStore.getValue(key));
        }
    }

    /** Test saving value with invalid key */
    @Test
    public void testSaveValueInvalidKey() {
        Assert.assertFalse(
                "Save should have returned false for \"\" key",
                keyValueStore.saveValue("", "value"));
        Assert.assertNull("Value found for key when not expected", keyValueStore.getValue(""));
        Assert.assertEquals("Wrong count for store", 0, keyValueStore.count());

        Assert.assertFalse(
                "Save should have returned false for null key",
                keyValueStore.saveValue(null, "value"));
        Assert.assertNull("Value found for key when not expected", keyValueStore.getValue(null));
        Assert.assertEquals("Wrong count for store", 0, keyValueStore.count());
    }

    /** Test saving invalid value */
    @Test
    public void testSaveValueInvalidValue() {
        Assert.assertFalse(
                "Save should have returned false for null value",
                keyValueStore.saveValue("key", null));
        Assert.assertNull("Value found for key when not expected", keyValueStore.getValue("key"));
        Assert.assertEquals("Wrong count for store", 0, keyValueStore.count());
    }

    //
    // Helper methods
    //
    private File getStoreDir(String storeName) {
        return new File(context.getApplicationInfo().dataDir + "/keyvaluestores", storeName);
    }

    private String streamToString(InputStream inputStream) {
        if (inputStream == null) {
            return null;
        }

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line);
            }

            return out.toString();

        } catch (IOException e) {
            Assert.fail("Failed to read from stream");

            return null;

        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                Assert.fail("Stream failed to close");
            }
        }
    }
}
