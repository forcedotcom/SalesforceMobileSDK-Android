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
package com.salesforce.androidsdk.smartstore.store;

import static com.salesforce.androidsdk.smartstore.tests.R.drawable.sf__icon;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.salesforce.androidsdk.analytics.security.Encryptor;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.security.SalesforceKeyGenerator;
import com.salesforce.androidsdk.smartstore.app.SmartStoreSDKManager;
import com.salesforce.androidsdk.util.ManagedFilesHelper;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class KeyValueEncryptedFileStoreTest {

    public static final String TEST_STORE = "TEST_STORE";
    public static final int NUM_ENTRIES = 25;

    private Context context;
    private KeyValueEncryptedFileStore keyValueStore;

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
        keyValueStore =
                new KeyValueEncryptedFileStore(
                        context, TEST_STORE, SalesforceSDKManager.getEncryptionKey());
        Assert.assertTrue("Store directory should exist", getStoreDir(TEST_STORE).exists());
        Assert.assertTrue("Store should be empty", keyValueStore.isEmpty());
    }

    @After
    public void tearDown() {
        ManagedFilesHelper.deleteFile(getStoreDir(TEST_STORE));
    }

    /** Test getStoreVersion() */
    @Test
    public void testGetStoreVersion() {
        Assert.assertEquals("Wrong kv store version", KeyValueEncryptedFileStore.KV_VERSION,
            keyValueStore.getStoreVersion());
    }

    /** Test getStoreVersion() when there is on version file (v1 store) */
    @Test
    public void testGetStoreVersionWithoutVersionFile() {
        File versionFile = new File(getStoreDir(TEST_STORE), "version");
        Assert.assertTrue(versionFile.exists());
        versionFile.delete();
        KeyValueEncryptedFileStore keyValueStoreWithoutVersionFile =
            new KeyValueEncryptedFileStore(
                context, TEST_STORE, SalesforceSDKManager.getEncryptionKey());
        Assert.assertEquals("Wrong kv store version", 1, keyValueStoreWithoutVersionFile.getStoreVersion());
        Assert.assertFalse(versionFile.exists());
    }

    /** Test isValidStoreName() */
    @Test
    public void isValidStoreName() {
        // Basic tests
        Assert.assertFalse("Store name is invalid", KeyValueEncryptedFileStore.isValidStoreName(null));
        Assert.assertFalse("Store name is invalid", KeyValueEncryptedFileStore.isValidStoreName(""));
        Assert.assertFalse("Store name is invalid", KeyValueEncryptedFileStore.isValidStoreName("abc!def"));
        Assert.assertFalse("Store name is invalid", KeyValueEncryptedFileStore.isValidStoreName("abc def"));
        Assert.assertFalse("Store name is invalid", KeyValueEncryptedFileStore.isValidStoreName("abc/def"));
        Assert.assertTrue("Store name is valid", KeyValueEncryptedFileStore.isValidStoreName("abc_def"));
        Assert.assertTrue("Store name is valid", KeyValueEncryptedFileStore.isValidStoreName("abc_def_ABC_DEF_012"));
        String generateStoreName = "";
        // Trying various lengths
        for (int i=0; i<KeyValueEncryptedFileStore.MAX_STORE_NAME_LENGTH * 2; i++) {
            generateStoreName += "x";
            Assert.assertEquals("Wrong value returned by isValidStoreName(\"" + generateStoreName + "\")",
                generateStoreName.length() <= KeyValueEncryptedFileStore.MAX_STORE_NAME_LENGTH,
                KeyValueEncryptedFileStore.isValidStoreName(generateStoreName));
        }
        // Trying various characters
        for (int i=0; i<256; i++) {
            generateStoreName = Character.toString((char) i);
            Assert.assertEquals("Wrong value returned by isValidStoreName(\"" + generateStoreName + "\")",
                (i >= 'a' && i <= 'z') || (i >= 'A' && i <= 'Z') || (i >= '0' && i <= '9') || i == '_',
                KeyValueEncryptedFileStore.isValidStoreName(generateStoreName));
        }
    }

    /** Test computeParentDir() */
    @Test
    public void testComputeParentDir() {
        Assert.assertEquals("Wrong value returned by computeParentDir()", getStoreDir("").getAbsolutePath(), KeyValueEncryptedFileStore.computeParentDir(context).getAbsolutePath());
    }

    /** Test getStoreDir() */
    @Test
    public void testGetStoreDir() {
        Assert.assertEquals("Wrong value returned by getStoreDir()",
            getStoreDir(TEST_STORE).getAbsolutePath(),
            keyValueStore.getStoreDir().getAbsolutePath());
    }

    /** Test getStoreName() */
    @Test
    public void testGetStoreName() {
        Assert.assertEquals(
                "Wrong value returned by getStoreDir()", TEST_STORE, keyValueStore.getStoreName());
    }

    /** Test that constructor fails if store name provided is invalid */
    @Test
    public void testFailedCreateBadName() {
        try {
            new KeyValueEncryptedFileStore(context, "", "");
            Assert.fail("An exception should have been thrown");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue("Wrong exception", e.getMessage().contains("Invalid store name"));
        }
    }

    /** Test that constructor fails if a file exists where the store dir should be created */
    @Test
    public void testFailedCreateFileExist() throws IOException {
        File file = getStoreDir("file");
        file.delete(); // starting clean
        Assert.assertTrue("Test file creation failed", file.createNewFile());
        try {
            new KeyValueEncryptedFileStore(context, "file", "");
            Assert.fail("An exception should have been thrown");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue("Wrong exception", e.getMessage().contains("Failed to create directory"));
        }
        file.delete();
    }

    /**
     * Test hasKeyValueStore()
     * Call hasKeyValueStore for existing store and non-existent store
     */
    @Test
    public void testHasKeyValueStore() {
        Assert.assertTrue("Store should have been found", KeyValueEncryptedFileStore.hasKeyValueStore(context, TEST_STORE));
        Assert.assertFalse("No store should have been found", KeyValueEncryptedFileStore.hasKeyValueStore(context, "non_existent_store"));
    }

    /**
     * Test remove key value store
     * Check new store does not exist, add new store, check it now exists, then remove it, check it no longer exists
     */
    @Test
    public void testRemoveKeyValueStore() {
        Assert.assertFalse("No store should have been found", KeyValueEncryptedFileStore.hasKeyValueStore(context, "new_store"));
        KeyValueEncryptedFileStore store = new KeyValueEncryptedFileStore(context, "new_store", "");
        Assert.assertTrue("Store should have been found", KeyValueEncryptedFileStore.hasKeyValueStore(context, "new_store"));
        Assert.assertTrue("Store dir should exist", getStoreDir("new_store").exists());
        KeyValueEncryptedFileStore.removeKeyValueStore(context, "new_store");
        Assert.assertFalse("No store should have been found", KeyValueEncryptedFileStore.hasKeyValueStore(context, "new_store"));
        Assert.assertFalse("Store dir should be gone", getStoreDir("new_store").exists());
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

    /** Test saving from streams and counting them */
    @Test
    public void testSaveStreamCount() {
        for (int i = 0; i < NUM_ENTRIES; i++) {
            String key = "key" + i;
            InputStream stream = stringToStream("value" + i);
            Assert.assertEquals("Wrong count before save", i, keyValueStore.count());
            keyValueStore.saveStream(key, stream);
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

    /** Test saving from streams and getting them back as values */
    @Test
    public void testSaveStreamGetValue() {
        for (int i = 0; i < NUM_ENTRIES; i++) {
            String key = "key" + i;
            InputStream stream = stringToStream("value" + i);
            keyValueStore.saveStream(key, stream);
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

    /** Test saving from streams and getting them back as streams */
    @Test
    public void testSaveStreamGetStream() {
        for (int i = 0; i < NUM_ENTRIES; i++) {
            String key = "key" + i;
            InputStream stream = stringToStream("value" + i);
            keyValueStore.saveStream(key, stream);
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

    /** Test saving values and checking the file system */
    @Test
    public void testSaveValueCheckFiles() throws IOException {
        Assert.assertEquals(1 /* version file */, getStoreDir(TEST_STORE).list().length);
        for (int i = 0; i < NUM_ENTRIES; i++) {
            String key = "key" + i;
            String value = "value" + i;
            keyValueStore.saveValue(key, value);
            File keyFile = new File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash(key) + ".key");
            File valueFile = new File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash(key) + ".value");
            Assert.assertTrue(keyFile.exists());
            Assert.assertTrue(valueFile.exists());
            Assert.assertEquals(1 /* version file */ + 2*(i+1), getStoreDir(TEST_STORE).list().length);
            Assert.assertEquals("key" + i, keyValueStore.decryptFileAsString(keyFile, SalesforceSDKManager.getEncryptionKey()));
            Assert.assertEquals("value" + i, keyValueStore.decryptFileAsString(valueFile, SalesforceSDKManager.getEncryptionKey()));
        }
    }

    /** Test saving streams and checking the file system */
    @Test
    public void testSaveStreamsCheckFiles() throws IOException {
        Assert.assertEquals(1 /* version file */, getStoreDir(TEST_STORE).list().length);
        for (int i = 0; i < NUM_ENTRIES; i++) {
            String key = "key" + i;
            InputStream stream = stringToStream("value" + i);
            keyValueStore.saveStream(key, stream);
            File keyFile = new File(getStoreDir(TEST_STORE),
                SalesforceKeyGenerator.getSHA256Hash(key) + ".key");
            File valueFile = new File(getStoreDir(TEST_STORE),
                SalesforceKeyGenerator.getSHA256Hash(key) + ".value");
            Assert.assertTrue(keyFile.exists());
            Assert.assertTrue(valueFile.exists());
            Assert.assertEquals(1 /* version file */ + 2*(i+1), getStoreDir(TEST_STORE).list().length);
            Assert.assertEquals("key" + i, keyValueStore.decryptFileAsString(keyFile, SalesforceSDKManager.getEncryptionKey()));
            Assert.assertEquals("value" + i, keyValueStore.decryptFileAsString(valueFile, SalesforceSDKManager.getEncryptionKey()));
        }
    }

    /** Test checking file system after saving then deleting values */
    @Test
    public void testSaveDeleteCheckFiles() {
        Assert.assertEquals(1 /* version file */, getStoreDir(TEST_STORE).list().length);
        for (int i = 0; i < NUM_ENTRIES; i++) {
            String key = "key" + i;
            String value = "value" + i;
            keyValueStore.saveValue(key, value);
        }
        for (int i = 0; i < NUM_ENTRIES; i++) {
            String key = "key" + i;
            File keyFile = new File(getStoreDir(TEST_STORE),
                SalesforceKeyGenerator.getSHA256Hash(key) + ".key");
            File valueFile = new File(getStoreDir(TEST_STORE),
                SalesforceKeyGenerator.getSHA256Hash(key) + ".value");
            Assert.assertTrue(keyFile.exists());
            Assert.assertTrue(valueFile.exists());
            Assert.assertEquals(1 /* version file */ + 2*(NUM_ENTRIES-i), getStoreDir(TEST_STORE).list().length);
            keyValueStore.deleteValue(key);
            Assert.assertEquals(1 /* version file */ + 2*(NUM_ENTRIES-(i+1)), getStoreDir(TEST_STORE).list().length);
            Assert.assertFalse(keyFile.exists());
            Assert.assertFalse(valueFile.exists());
        }
    }

    /** Test checking file system after saving then deleting all values */
    @Test
    public void testSaveDeleteAllCheckFiles() {
        Assert.assertEquals(1 /* version file */, getStoreDir(TEST_STORE).list().length);
        for (int i = 0; i < NUM_ENTRIES; i++) {
            String key = "key" + i;
            String value = "value" + i;
            keyValueStore.saveValue(key, value);
        }
        Assert.assertEquals(1 /* version file */ + 2*NUM_ENTRIES, getStoreDir(TEST_STORE).list().length);
        keyValueStore.deleteAll();
        Assert.assertEquals(1 /* version file */, getStoreDir(TEST_STORE).list().length);
    }


    /** Test saving values and calling keySet() */
    @Test
    public void testSaveValueKeySet() {
        for (int i = 0; i < NUM_ENTRIES; i++) {
            String key = "key" + i;
            String value = "value" + i;
            Assert.assertEquals(i, keyValueStore.keySet().size());
            Assert.assertFalse(keyValueStore.keySet().contains(key));
            keyValueStore.saveValue(key, value);
            Assert.assertTrue(keyValueStore.keySet().contains(key));
            Assert.assertEquals(i+1, keyValueStore.keySet().size());
        }
    }

    /** Test saving streams and calling keySet() */
    @Test
    public void testSaveStreamKeySet() {
        for (int i = 0; i < NUM_ENTRIES; i++) {
            String key = "key" + i;
            InputStream stream = stringToStream("value" + i);
            Assert.assertEquals(i, keyValueStore.keySet().size());
            Assert.assertFalse(keyValueStore.keySet().contains(key));
            keyValueStore.saveStream(key, stream);
            Assert.assertTrue(keyValueStore.keySet().contains(key));
            Assert.assertEquals(i+1, keyValueStore.keySet().size());
        }
    }

    /** Test calling keySet() after saving then deleting values */
    @Test
    public void testSaveDeleteKeySet() {
        Assert.assertTrue(keyValueStore.keySet().isEmpty());
        for (int i = 0; i < NUM_ENTRIES; i++) {
            String key = "key" + i;
            String value = "value" + i;
            keyValueStore.saveValue(key, value);
        }
        for (int i = 0; i < NUM_ENTRIES; i++) {
            String key = "key" + i;
            Assert.assertEquals(NUM_ENTRIES - i, keyValueStore.keySet().size());
            Assert.assertTrue(keyValueStore.keySet().contains(key));
            keyValueStore.deleteValue(key);
            Assert.assertFalse(keyValueStore.keySet().contains(key));
            Assert.assertEquals(NUM_ENTRIES - (i+1), keyValueStore.keySet().size());
        }
    }

    /** Test calling keySet() after saving then deleting all values */
    @Test
    public void testSaveDeleteAllKeySet() {
        Assert.assertTrue(keyValueStore.keySet().isEmpty());
        for (int i = 0; i < NUM_ENTRIES; i++) {
            String key = "key" + i;
            String value = "value" + i;
            keyValueStore.saveValue(key, value);
        }
        Assert.assertEquals(NUM_ENTRIES, keyValueStore.keySet().size());
        keyValueStore.deleteAll();
        Assert.assertTrue(keyValueStore.keySet().isEmpty());
    }

    /** Making sure various operations won't NPE if storeDir was deleted */
    @Test
    public void testNoNPEIfStoreDirDeleted() {
        ManagedFilesHelper.deleteFile(keyValueStore.getStoreDir());
        Assert.assertNull("Expected null for files in deleted stored dir", keyValueStore.getStoreDir().listFiles());
        try {
            Assert.assertEquals(TEST_STORE, keyValueStore.getStoreName());
            Assert.assertEquals(null, keyValueStore.getValue("xyz"));
            Assert.assertEquals(false, keyValueStore.saveValue("xyz", "abc"));
            Assert.assertEquals(null, keyValueStore.getValue("xyz"));
            Assert.assertEquals(false, keyValueStore.deleteValue("xyz"));
            Assert.assertEquals(null, keyValueStore.getStream("xyz"));
            Assert.assertEquals(false, keyValueStore.saveStream("xyz", stringToStream("abc")));
            Assert.assertEquals(null, keyValueStore.getStream("xyz"));
            Assert.assertEquals(0, keyValueStore.count());
            Assert.assertEquals(true, keyValueStore.isEmpty());
            keyValueStore.deleteAll();
        } catch (NullPointerException e) {
            Assert.fail("NPE was not expected");
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

    /** Test that data is indeed stored encrypted */
    @Test
    public void testStoreIsEncrypted() throws FileNotFoundException {
        // Populate store
        keyValueStore.saveValue("key1", "value1");
        keyValueStore.saveValue("key2", "value2");

        // Look at the raw content of value files
        File[] valueFiles = keyValueStore.getStoreDir().listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return s.endsWith(".value");
            }
        });
        Assert.assertEquals("Wrong number of files", 2, valueFiles.length);

        // Make sure the actual value can't be found
        Assert.assertFalse("File should have been encrypted", streamToString(new FileInputStream(valueFiles[0])).contains("value"));
        Assert.assertFalse("File should have been encrypted", streamToString(new FileInputStream(valueFiles[1])).contains("value"));

        // Look at the raw content of key files
        File[] keyFiles = keyValueStore.getStoreDir().listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return s.endsWith(".key");
            }
        });
        Assert.assertEquals("Wrong number of files", 2, keyFiles.length);

        // Make sure the actual key can't be found
        Assert.assertFalse("File should have been encrypted", streamToString(new FileInputStream(keyFiles[0])).contains("key"));
        Assert.assertFalse("File should have been encrypted", streamToString(new FileInputStream(keyFiles[1])).contains("key"));
    }

    /** Test changing encryption key */
    @Test
    public void testChangeEncryptionKey() throws FileNotFoundException {
        // Populate store
        keyValueStore.saveValue("key1", "value1");
        keyValueStore.saveValue("key2", "value2");
        // Check store
        Assert.assertEquals("Wrong count", 2, keyValueStore.count());
        Assert.assertEquals("Wrong value for key1", "value1", keyValueStore.getValue("key1"));
        Assert.assertEquals("Wrong value for key2", "value2", keyValueStore.getValue("key2"));
        // Getting raw content of files
        File[] files = keyValueStore.getStoreDir().listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return s.endsWith(".value");
            }
        });
        Assert.assertEquals("Wrong number of files", 2, files.length);
        String file1raw = streamToString(new FileInputStream(files[0]));
        String file2raw = streamToString(new FileInputStream(files[1]));
        // Generate new key
        String newEncryptionKey = SalesforceKeyGenerator.getEncryptionKey("new");
        // Make sure it's a different key
        Assert.assertNotEquals("New encryption key should be different", newEncryptionKey, SalesforceSDKManager.getEncryptionKey());
        // Change encryption key
        Assert.assertTrue("Changing key should have succeeded", keyValueStore.changeEncryptionKey(newEncryptionKey));
        // Make sure we can still read all the values from the store
        Assert.assertEquals("Wrong count", 2, keyValueStore.count());
        Assert.assertEquals("Wrong value for key1", "value1", keyValueStore.getValue("key1"));
        Assert.assertEquals("Wrong value for key2", "value2", keyValueStore.getValue("key2"));
        // Getting raw content of files
        String file1rawAfter = streamToString(new FileInputStream(files[0]));
        String file2rawAfter = streamToString(new FileInputStream(files[1]));
        Assert.assertNotEquals("Raw content should have changed", file1rawAfter, file1raw);
        Assert.assertNotEquals("Raw content should have changed", file2rawAfter, file2raw);
    }

    /** Test code block with comment and newline */
    @Test
    public void testCodeBlock() {
        String codeBlock = "var fun = function() {" + "\n\t// comment" + "\n\tvar i = 100;\n}";
        String minifiedBlock = "function minified(){var n=Math.floor(Math.random());return n>50?7*n:n/2}";
        keyValueStore.saveValue("js1", codeBlock);
        keyValueStore.saveValue("js2", minifiedBlock);
        Assert.assertEquals("Code block was not retrieved correctly.", codeBlock, keyValueStore.getValue("js1"));
        Assert.assertEquals("Code block was not retrieved correctly.", minifiedBlock, keyValueStore.getValue("js2"));
    }

    /** Test save/get/delete/count with v1 store */
    @Test
    public void testSaveGetDeleteCountOnV1Store() throws IOException {
        keyValueStore = turnIntoV1Store(keyValueStore);

        // Saving values
        keyValueStore.saveValue("key1", "value1");
        keyValueStore.saveStream("key2", stringToStream("value2"));
        keyValueStore.saveValue("key3", "value3");
        keyValueStore.saveStream("key4", stringToStream("value4"));

        // Getting values back
        Assert.assertEquals("value1", streamToString(keyValueStore.getStream("key1")));
        Assert.assertEquals("value2", keyValueStore.getValue("key2"));
        Assert.assertEquals("value3", keyValueStore.getValue("key3"));
        Assert.assertEquals("value4", streamToString(keyValueStore.getStream("key4")));

        // Checking count
        Assert.assertEquals(4, keyValueStore.count());

        // Checking files
        Assert.assertEquals(4, getStoreDir(TEST_STORE).list().length);
        File value1 = new File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key1"));
        File value2 = new File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key2"));
        File value3 = new File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key3"));
        File value4 = new File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key4"));
        Assert.assertTrue(value1.exists());
        Assert.assertTrue(value2.exists());
        Assert.assertTrue(value3.exists());
        Assert.assertTrue(value4.exists());
        Assert.assertEquals("value1", keyValueStore.decryptFileAsString(value1, SalesforceSDKManager.getEncryptionKey()));
        Assert.assertEquals("value2", keyValueStore.decryptFileAsString(value2, SalesforceSDKManager.getEncryptionKey()));
        Assert.assertEquals("value3", keyValueStore.decryptFileAsString(value3, SalesforceSDKManager.getEncryptionKey()));
        Assert.assertEquals("value4", keyValueStore.decryptFileAsString(value4, SalesforceSDKManager.getEncryptionKey()));

        // Deleting one
        keyValueStore.deleteValue("key2");
        Assert.assertNull(keyValueStore.getValue("key2"));
        Assert.assertNull(keyValueStore.getStream("key2"));

        // Checking count
        Assert.assertEquals(3, keyValueStore.count());

        // Checking files
        Assert.assertEquals(3, getStoreDir(TEST_STORE).list().length);
        Assert.assertFalse(value2.exists());

        // Deleting all
        keyValueStore.deleteAll();
        Assert.assertNull(keyValueStore.getValue("key1"));
        Assert.assertNull(keyValueStore.getStream("key1"));

        // Checking count
        Assert.assertEquals(0, keyValueStore.count());

        // Checking files
        Assert.assertEquals(0, getStoreDir(TEST_STORE).list().length);
        Assert.assertFalse(value1.exists());
        Assert.assertFalse(value2.exists());
        Assert.assertFalse(value3.exists());
        Assert.assertFalse(value4.exists());
    }

    /** Test keySet() with v1 store - should throw exception */
    @Test
    public void testKeySetOnV1Store() throws FileNotFoundException {
        keyValueStore = turnIntoV1Store(keyValueStore);
        try {
            keyValueStore.keySet();
            Assert.fail("Exception was expected");
        } catch (UnsupportedOperationException e) {
            Assert.assertTrue(e.getMessage().contains("keySet() not supported on v1 stores"));
        }
    }

    /**
     * Making sure that keySet(), deleteAll(), count() work even if there is a bad key file
     * Bad key file should not happen unless files were tampered with directly
     * @throws IOException
     */
    @Test
    public void  testKeySetCountDeleteAllWithBadKeyFile() throws IOException {
        keyValueStore.saveValue("key1", "value1");
        keyValueStore.saveValue("key2", "value2");

        // Calling count() -  should return 2
        Assert.assertEquals(2, keyValueStore.count());

        // Getting file objects
        File key1File = new File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key1") + ".key");
        File key2File = new File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key2") + ".key");
        File value1File = new File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key1") + ".value");
        File value2File = new File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key2") + ".value");

        // Making sure all 4 files exist
        Assert.assertTrue(key1File.exists());
        Assert.assertTrue(key2File.exists());
        Assert.assertTrue(value1File.exists());
        Assert.assertTrue(value2File.exists());

        // Tampering with one of the key file
        key1File.delete();
        key1File.createNewFile();
        Assert.assertEquals(1, keyValueStore.count());
        Assert.assertTrue(key1File.exists());
        Assert.assertTrue(key2File.exists());
        Assert.assertTrue(value1File.exists());
        Assert.assertTrue(value2File.exists());

        // Calling keySet() - should not return bad key
        String[] foundKeys = keyValueStore.keySet().toArray(new String[0]);
        Assert.assertEquals(1, foundKeys.length);
        Assert.assertEquals("key2", foundKeys[0]);

        // Calling count() -  should return 1
        Assert.assertEquals(1, keyValueStore.count());

        // Calling deleteAll() - should also delete bad key file
        keyValueStore.deleteAll();
        Assert.assertFalse(key1File.exists());
        Assert.assertFalse(key2File.exists());
        Assert.assertFalse(value1File.exists());
        Assert.assertFalse(value2File.exists());

        // Calling count() -  should return 0
        Assert.assertEquals(0, keyValueStore.count());

    }

    /**
     * Read some binary file from assets, save it to the key value store then get it back
     * Make sure it's identical to the original file
     */
    @Test
    public void testBinaryStorage() throws IOException {
        // Saving resource icon to key value store
        keyValueStore.saveStream("icon", getResourceIconStream());

        // Retrieving icon back from key value store
        byte[] savedIconBytes = Encryptor.getByteArrayStreamFromStream(keyValueStore.getStream("icon")).toByteArray();

        // Comparing bytes
        byte[] resourceIconBytes = Encryptor.getByteArrayStreamFromStream(getResourceIconStream()).toByteArray();
        Assert.assertEquals(resourceIconBytes.length, savedIconBytes.length);
        for (int i=0; i<resourceIconBytes.length; i++) {
            Assert.assertEquals(resourceIconBytes[i], savedIconBytes[i]);
        }
    }

    //
    // Helper methods
    //
    private InputStream getResourceIconStream() {
        return context.getResources().openRawResource(sf__icon);
    }

    private KeyValueEncryptedFileStore turnIntoV1Store(KeyValueEncryptedFileStore store) {
        if (!store.isEmpty()) {
            throw new RuntimeException("turnIntoV1Store() should be called on empty store");
        }
        // Delete version file (they did not exist in v1)
        new File(store.getStoreDir(), "version").delete();

        KeyValueEncryptedFileStore storerV1 = new KeyValueEncryptedFileStore(
            context, store.getStoreName(), SalesforceSDKManager.getEncryptionKey());

        Assert.assertEquals(1, keyValueStore.readVersion());
        Assert.assertEquals("Directory should be empty", 0, keyValueStore.getStoreDir().list().length);

        return storerV1;
    }

    private File getStoreDir(String storeName) {
        return new File(context.getApplicationInfo().dataDir + "/keyvaluestores", storeName);
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
