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

import static com.salesforce.androidsdk.smartstore.tests.R.drawable.sf__icon;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.salesforce.androidsdk.analytics.security.Encryptor;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.smartstore.app.SmartStoreSDKManager;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Tests for MemCachedKeyValueStore */
@RunWith(AndroidJUnit4.class)
public class MemCachedKeyValueStoreTest {
    static final String TEST_STORE = "TEST_STORE";
    static final int NUM_ENTRIES = 25;
    static final int CACHE_SIZE = 10;

    private Context context;
    private KeyValueEncryptedFileStore store;
    private MemCachedKeyValueStore memCachedStore;

    @Before
    public void setUp() {
        // Throw an exception if stream is not closed
        try {
            Class.forName("dalvik.system.CloseGuard")
                .getMethod("setEnabled", boolean.class)
                .invoke(null, true);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }

        context =
            InstrumentationRegistry.getInstrumentation()
                .getTargetContext()
                .getApplicationContext();
        
        SmartStoreSDKManager.initNative(context, null);
        store = new KeyValueEncryptedFileStore(context, TEST_STORE, SalesforceSDKManager.getEncryptionKey());
        memCachedStore = new MemCachedKeyValueStore(store, CACHE_SIZE);
    }

    @After
    public void tearDown() {
        store.deleteAll();
    }

    /** Test saving values and counting them  */
    @Test
    public void testSaveValueCount() {
        for (int i=0; i<NUM_ENTRIES; i++) {
            String key = "key" + i;
            String value = "value" + i;
            Assert.assertEquals("Wrong count before save", i, memCachedStore.count());
            Assert.assertEquals("Wrong count before save", i, store.count());
            memCachedStore.saveValue(key, value);
            Assert.assertEquals("Wrong count after save", i + 1, memCachedStore.count());
            Assert.assertEquals("Wrong count after save", i + 1, store.count());
        }
    }

    /** Test saving from streams and counting them  */
    @Test
    public void testSaveStreamCount() throws IOException {
        for (int i=0; i<NUM_ENTRIES; i++) {
            String key = "key" + i;
            InputStream stream = stringToStream("value" + i);
            Assert.assertEquals("Wrong count before save", i, memCachedStore.count());
            Assert.assertEquals("Wrong count before save", i, store.count());
            memCachedStore.saveStream(key, stream);
            Assert.assertEquals("Wrong count after save", i + 1, memCachedStore.count());
            Assert.assertEquals("Wrong count after save", i + 1, store.count());
        }
    }

    /** Test saving values and getting them back when mem cache hits */
    @Test
    public void testSaveValueGetWhenMemCacheHits() {
        memCachedStore.saveValue("key1", "value1");
        Assert.assertEquals(1, memCachedStore.memCache.putCount());

        Assert.assertEquals("value1", memCachedStore.getValue("key1"));
        Assert.assertEquals(1, memCachedStore.memCache.hitCount());
        Assert.assertEquals("value1", store.getValue("key1"));

        Assert.assertEquals("value1", streamToString(memCachedStore.getStream("key1")));
        Assert.assertEquals(2, memCachedStore.memCache.hitCount());
        Assert.assertEquals("value1", streamToString(store.getStream("key1")));

        Assert.assertEquals("value1", new String(memCachedStore.memCache.get("key1"), StandardCharsets.UTF_8));
    }

    /** Test saving from streams and getting them back when mem cache hits */
    @Test
    public void testSaveStreamGetWhenMemCacheHits() throws IOException {
        memCachedStore.saveStream("key1", stringToStream("value1"));
        Assert.assertEquals(1, memCachedStore.memCache.putCount());

        Assert.assertEquals("value1", memCachedStore.getValue("key1"));
        Assert.assertEquals(1, memCachedStore.memCache.hitCount());
        Assert.assertEquals("value1", store.getValue("key1"));

        Assert.assertEquals("value1", streamToString(memCachedStore.getStream("key1")));
        Assert.assertEquals(2, memCachedStore.memCache.hitCount());
        Assert.assertEquals("value1", streamToString(store.getStream("key1")));

        Assert.assertEquals("value1", new String(memCachedStore.memCache.get("key1"), StandardCharsets.UTF_8));
    }

    /** Test saving values and getting them back when mem cache misses */
    @Test
    public void testSaveValueGetWhenMemCacheMisses() {
        memCachedStore.saveValue("key1", "value1");
        Assert.assertEquals(1, memCachedStore.memCache.putCount());

        memCachedStore.memCache.evictAll();
        Assert.assertEquals("value1", memCachedStore.getValue("key1"));
        Assert.assertEquals(1, memCachedStore.memCache.missCount());
        Assert.assertEquals("value1", store.getValue("key1"));

        memCachedStore.memCache.evictAll();
        Assert.assertEquals("value1", streamToString(memCachedStore.getStream("key1")));
        Assert.assertEquals(2, memCachedStore.memCache.missCount());
        Assert.assertEquals("value1", streamToString(store.getStream("key1")));
    }

    /** Test saving from streams and getting them back when mem cache misses */
    @Test
    public void testSaveStreamGetWhenMemCacheMisses() throws IOException {
        memCachedStore.saveStream("key1", stringToStream("value1"));
        Assert.assertEquals(1, memCachedStore.memCache.putCount());

        memCachedStore.memCache.evictAll();
        Assert.assertEquals("value1", memCachedStore.getValue("key1"));
        Assert.assertEquals(1, memCachedStore.memCache.missCount());
        Assert.assertEquals("value1", store.getValue("key1"));

        memCachedStore.memCache.evictAll();
        Assert.assertEquals("value1", streamToString(memCachedStore.getStream("key1")));
        Assert.assertEquals(2, memCachedStore.memCache.missCount());
        Assert.assertEquals("value1", streamToString(store.getStream("key1")));
    }

    /** Test that get stream populates mem cache */
    @Test
    public void testGetStreamPopulatesMemCache() {
        memCachedStore.saveValue("key1", "value1");
        Assert.assertEquals(1, memCachedStore.memCache.putCount());

        memCachedStore.memCache.evictAll();
        Assert.assertEquals("value1", streamToString(memCachedStore.getStream("key1")));
        Assert.assertEquals(1, memCachedStore.memCache.missCount());
        Assert.assertEquals("value1", streamToString(memCachedStore.getStream("key1")));
        Assert.assertEquals(1, memCachedStore.memCache.hitCount());

        Assert.assertEquals("value1", new String(memCachedStore.memCache.get("key1"), StandardCharsets.UTF_8));
    }

    /** Test that get value populates mem cache */
    @Test
    public void testGetValuePopulatesMemCache() {
        memCachedStore.saveValue("key1", "value1");
        Assert.assertEquals(1, memCachedStore.memCache.putCount());

        memCachedStore.memCache.evictAll();
        Assert.assertEquals("value1", streamToString(memCachedStore.getStream("key1")));
        Assert.assertEquals(1, memCachedStore.memCache.missCount());
        Assert.assertEquals("value1", streamToString(memCachedStore.getStream("key1")));
        Assert.assertEquals(1, memCachedStore.memCache.hitCount());

        Assert.assertEquals("value1", new String(memCachedStore.memCache.get("key1"), StandardCharsets.UTF_8));
    }

    /** Test saving values and deleting them  */
    @Test
    public void testSaveValueDelete() {
        for (int i=0; i<NUM_ENTRIES; i++) {
            String key = "key" + i;
            String value = "value" + i;
            memCachedStore.saveValue(key, value);
        }
        for (int i=0; i<NUM_ENTRIES; i++) {
            String key = "key" + i;
            Assert.assertNotNull("No value found for key when expected:$key", memCachedStore.getValue(key));
            Assert.assertEquals("Wrong count before delete", NUM_ENTRIES - i, memCachedStore.count());
            Assert.assertNotNull("No value found for key when expected:$key", store.getValue(key));
            Assert.assertEquals("Wrong count before delete", NUM_ENTRIES - i, store.count());
            memCachedStore.deleteValue(key);
            Assert.assertNull("Value found for key when not expected:$key", memCachedStore.getValue(key));
            Assert.assertEquals("Wrong count after delete", NUM_ENTRIES - i - 1, memCachedStore.count());
            Assert.assertNull("Value found for key when not expected:$key", store.getValue(key));
            Assert.assertEquals("Wrong count after delete", NUM_ENTRIES - i - 1, store.count());
        }
    }

    /** Test saving values and deleting them all at once  */
    @Test
    public void testSaveValueDeleteAll() {
        for (int i=0; i<NUM_ENTRIES; i++) {
            String key = "key" + i;
            String value = "value" + i;
            memCachedStore.saveValue(key, value);
        }

        Assert.assertEquals("Wrong count before deleteAll", NUM_ENTRIES, memCachedStore.count());
        Assert.assertEquals("Wrong count before deleteAll", NUM_ENTRIES, store.count());
        memCachedStore.deleteAll();
        Assert.assertEquals("Wrong count after deleteAll", 0, memCachedStore.count());
        Assert.assertEquals("Wrong count after deleteAll", 0, store.count());
    }

    /** Test new lines are preserved when value retrieved from memory  */
    @Test
    public void testNewLinesPreservedWhenMemCacheHits() throws IOException {
        String codeBlock = "var fun = function() {\r // comment \n var i = 100; } \r\n // comment";

        memCachedStore.saveStream("js1", stringToStream(codeBlock));
        memCachedStore.saveValue("js2", codeBlock);
        Assert.assertEquals(codeBlock, memCachedStore.getValue("js1"));
        Assert.assertEquals(codeBlock, streamToString(memCachedStore.getStream("js1")));
        Assert.assertEquals(codeBlock, memCachedStore.getValue("js2"));
        Assert.assertEquals(codeBlock, streamToString(memCachedStore.getStream("js2")));
        Assert.assertEquals(codeBlock, store.getValue("js1"));
        Assert.assertEquals(codeBlock, streamToString(store.getStream("js1")));
        Assert.assertEquals(codeBlock, store.getValue("js2"));
        Assert.assertEquals(codeBlock, streamToString(store.getStream("js2")));
    }

    /** Test new lines are preserved when value retrieved from file system  */
    @Test
    public void testNewLinesPreservedWhenMemCacheMisses() throws IOException {
        String codeBlock = "var fun = function() {\r // comment \n var i = 100; } \r\n // comment";

        memCachedStore.saveStream("js1", stringToStream(codeBlock));
        memCachedStore.saveValue("js2", codeBlock);

        // Evicting all from memCache
        memCachedStore.memCache.evictAll();
        Assert.assertEquals(codeBlock, memCachedStore.getValue("js1"));
        Assert.assertEquals(codeBlock, memCachedStore.getValue("js2"));

        // Evicting all from memCache
        memCachedStore.memCache.evictAll();
        Assert.assertEquals(codeBlock, streamToString(memCachedStore.getStream("js1")));
        Assert.assertEquals(codeBlock, streamToString(memCachedStore.getStream("js2")));
    }

    /** Test calling keySet() after saving and deleting values */
    @Test
    public void testSaveDeleteKeySet() {
        Assert.assertTrue(memCachedStore.keySet().isEmpty());
        for (int i = 0; i < NUM_ENTRIES; i++) {
            String key = "key" + i;
            String value = "value" + i;
            Assert.assertEquals(i, memCachedStore.keySet().size());
            Assert.assertFalse(memCachedStore.keySet().contains(key));
            memCachedStore.saveValue(key, value);
            Assert.assertTrue(memCachedStore.keySet().contains(key));
            Assert.assertEquals(i+1, memCachedStore.keySet().size());
        }
        for (int i = 0; i < NUM_ENTRIES; i++) {
            String key = "key" + i;
            Assert.assertEquals(NUM_ENTRIES - i, memCachedStore.keySet().size());
            Assert.assertTrue(memCachedStore.keySet().contains(key));
            memCachedStore.deleteValue(key);
            Assert.assertFalse(memCachedStore.keySet().contains(key));
            Assert.assertEquals(NUM_ENTRIES - (i+1), memCachedStore.keySet().size());
        }
    }

    /**
     * Read some binary file from assets, save it to the key value store then get it back
     * Make sure it's identical to the original file
     */
    @Test
    public void testBinaryStorage() throws IOException {
        // Saving resource icon to key value store
        memCachedStore.saveStream("icon", getResourceIconStream());

        // Retrieving icon back from key value store
        byte[] savedIconBytes = Encryptor.getByteArrayStreamFromStream(memCachedStore.getStream("icon")).toByteArray();

        // Comparing bytes
        byte[] resourceIconBytes = Encryptor.getByteArrayStreamFromStream(getResourceIconStream()).toByteArray();
        Assert.assertEquals(resourceIconBytes.length, savedIconBytes.length);
        for (int i=0; i<resourceIconBytes.length; i++) {
            Assert.assertEquals(resourceIconBytes[i], savedIconBytes[i]);
        }
    }

    /** Test calling contains after saving and deleting values */
    @Test
    public void testContains() {
        Assert.assertFalse(memCachedStore.contains("key1"));
        Assert.assertFalse(memCachedStore.contains("key2"));
        Assert.assertFalse(memCachedStore.contains("key3"));
        Assert.assertFalse(store.contains("key1"));
        Assert.assertFalse(store.contains("key2"));
        Assert.assertFalse(store.contains("key3"));

        // Save one
        memCachedStore.saveValue("key1", "value1");
        Assert.assertTrue(memCachedStore.contains("key1"));
        Assert.assertFalse(memCachedStore.contains("key2"));
        Assert.assertFalse(memCachedStore.contains("key3"));
        Assert.assertTrue(store.contains("key1"));
        Assert.assertFalse(store.contains("key2"));
        Assert.assertFalse(store.contains("key3"));

        // Save another into underlying store directly
        store.saveValue("key2", "value2");
        Assert.assertTrue(memCachedStore.contains("key1"));
        Assert.assertTrue(memCachedStore.contains("key2"));
        Assert.assertFalse(memCachedStore.contains("key3"));
        Assert.assertTrue(store.contains("key1"));
        Assert.assertTrue(store.contains("key2"));
        Assert.assertFalse(store.contains("key3"));

        // Save third
        memCachedStore.saveValue("key3", "value3");
        Assert.assertTrue(memCachedStore.contains("key1"));
        Assert.assertTrue(memCachedStore.contains("key2"));
        Assert.assertTrue(memCachedStore.contains("key3"));
        Assert.assertTrue(store.contains("key1"));
        Assert.assertTrue(store.contains("key2"));
        Assert.assertTrue(store.contains("key3"));

        // Delete one
        memCachedStore.deleteValue("key1");
        Assert.assertFalse(memCachedStore.contains("key1"));
        Assert.assertTrue(memCachedStore.contains("key2"));
        Assert.assertTrue(memCachedStore.contains("key3"));
        Assert.assertFalse(store.contains("key1"));
        Assert.assertTrue(store.contains("key2"));
        Assert.assertTrue(store.contains("key3"));

        // Delete all
        memCachedStore.deleteAll();
        Assert.assertFalse(memCachedStore.contains("key1"));
        Assert.assertFalse(memCachedStore.contains("key2"));
        Assert.assertFalse(memCachedStore.contains("key3"));
        Assert.assertFalse(store.contains("key1"));
        Assert.assertFalse(store.contains("key2"));
        Assert.assertFalse(store.contains("key3"));
    }

    //
    // Helper methods
    //
    private InputStream getResourceIconStream() {
        return context.getResources().openRawResource(sf__icon);
    }

    private InputStream stringToStream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private String streamToString(InputStream inputStream) {
        if (inputStream == null) {
            return null;
        }
        try {
            return Encryptor.getStringFromStream(inputStream);
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