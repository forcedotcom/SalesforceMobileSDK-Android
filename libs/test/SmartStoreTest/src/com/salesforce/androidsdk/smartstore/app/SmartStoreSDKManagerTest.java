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
package com.salesforce.androidsdk.smartstore.app;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.smartstore.store.DBOpenHelper;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.KeyValueEncryptedFileStore;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;
import com.salesforce.androidsdk.util.ManagedFilesHelper;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SmartStoreSDKManagerTest {

    private Context context;
    private SmartStoreSDKManager manager;

    @Before
    public void setUp() throws Exception {
        context =
            InstrumentationRegistry.getInstrumentation()
                .getTargetContext()
                .getApplicationContext();
        SmartStoreSDKManager.initNative(context, null);
        manager = SmartStoreSDKManager.getInstance();
    }

    @After
    public void tearDown() throws Exception {
        // Nuking the keyvalustores directory
        ManagedFilesHelper.deleteFile(KeyValueEncryptedFileStore.computeParentDir(context));
        // Nuking user smartstores of all users
        DBOpenHelper.deleteAllUserDatabases(context);
        // Nuking global smartstores
        manager.removeAllGlobalStores();
    }

    /**
     * Create a smartstore by calling getSmartStore
     * Get that store again by calling getSmartStore
     * Make sure it's for the same database
     */
    @Test
    public void testGetSmartStoreReturnsSameStore() throws JSONException {
        UserAccount user = createTestAccount("00Dorg", "user", null, "first@test.com");
        try {
            SmartStore store = manager.getSmartStore(user);
            SmartStore storeSecondInstance = manager.getSmartStore(user);
            Assert.assertSame("Expect same database", store.getDatabase(),
                storeSecondInstance.getDatabase());
        } finally {
            manager.removeSmartStore(user);
        }
    }

    /**
     * Create a global smartstore by calling getGlobalSmartStore
     * Get that store again by calling getGlobalSmartStore
     * Make sure it's for the same database
     */
    @Test
    public void testGetGlobalSmartStoreReturnsSameStore() throws JSONException {
        try {
            SmartStore store = manager.getGlobalSmartStore();
            SmartStore storeSecondInstance = manager.getGlobalSmartStore();
            Assert.assertSame("Expect same database", store.getDatabase(),
                storeSecondInstance.getDatabase());
        } finally {
            manager.removeGlobalSmartStore(DBOpenHelper.DEFAULT_DB_NAME);
        }
    }


    /**
     * Create a store by calling getKeyValueStore
     * Populate that store
     * Get that store by calling getKeyValueStore
     * Make sure we find values stored through the first instance
     */
    @Test
    public void testGetKeyValueStoreReturnsSameStore() throws JSONException {
        UserAccount user = createTestAccount("00Dorg", "user", null,"first@test.com");
        KeyValueEncryptedFileStore store = manager.getKeyValueStore("store", user);
        Assert.assertTrue("Store should be empty", store.isEmpty());
        store.saveValue("key1", "value1");
        store.saveValue("key2", "value2");
        store.saveValue("key3", "value3");
        Assert.assertTrue("Store should not be empty", !store.isEmpty());
        Assert.assertEquals("Store should have 3 values", 3, store.count());
        KeyValueEncryptedFileStore storeSecondInstance = manager.getKeyValueStore("store", user);
        Assert.assertTrue("Store should not be empty", !storeSecondInstance.isEmpty());
        Assert.assertEquals("Store should have 3 values", 3, storeSecondInstance.count());
        Assert.assertEquals("Wrong value", "value1", storeSecondInstance.getValue("key1"));
        Assert.assertEquals("Wrong value", "value2", storeSecondInstance.getValue("key2"));
        Assert.assertEquals("Wrong value", "value3", storeSecondInstance.getValue("key3"));
    }

    /**
     * Create a global store by calling getGlobalKeyValueStore
     * Populate that store
     * Get that store by calling getGlobalKeyValueStore
     * Make sure we find values stored through the first instance
     */
    @Test
    public void testGlobalGetKeyValueStoreReturnsSameStore() throws JSONException {
        KeyValueEncryptedFileStore store = manager.getGlobalKeyValueStore("store");
        Assert.assertTrue("Store should be empty", store.isEmpty());
        store.saveValue("key1", "value1");
        store.saveValue("key2", "value2");
        store.saveValue("key3", "value3");
        Assert.assertTrue("Store should not be empty", !store.isEmpty());
        Assert.assertEquals("Store should have 3 values", 3, store.count());
        KeyValueEncryptedFileStore storeSecondInstance = manager.getGlobalKeyValueStore("store");
        Assert.assertTrue("Store should not be empty", !storeSecondInstance.isEmpty());
        Assert.assertEquals("Store should have 3 values", 3, storeSecondInstance.count());
        Assert.assertEquals("Wrong value", "value1", storeSecondInstance.getValue("key1"));
        Assert.assertEquals("Wrong value", "value2", storeSecondInstance.getValue("key2"));
        Assert.assertEquals("Wrong value", "value3", storeSecondInstance.getValue("key3"));
    }

    /**
     * Using getSmartStore / hasSmartStore / getUserStoresPrefixList / removeSmartStore
     * with a single user and single store
     */
    @Test
    public void testSmartStoreOperationsWithOneUserOneStore() throws JSONException {
        UserAccount user = createTestAccount("00Dorg", "user", null,"first@test.com");

        // No store initially
        Assert.assertFalse("Store should not be found", manager.hasSmartStore("store", user, null));
        Assert.assertEquals("No stores should be found", 0, manager.getUserStoresPrefixList(user).size());

        // Create store
        SmartStore store = createAndPopulateSmartStore("store", user);
        Assert.assertEquals("Wrong db path", computeExpectedSmartStorePath("store", user), store.getDatabase().getPath());
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store", user, null));
        Assert.assertEquals("Wrong store names", 1, manager.getUserStoresPrefixList(user).size());
        Assert.assertEquals("Wrong store names", "store", manager.getUserStoresPrefixList(user).get(0));

        // Remove store
        manager.removeSmartStore("store", user, null);
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store", user, null));
        Assert.assertEquals("No stores should be found", 0, manager.getUserStoresPrefixList(user).size());
    }

    /**
     * Using getGlobalSmartStore / hasGlobalSmartStore / geGlobalStoresPrefixList / removeGlobalSmartStore
     * with a single user and single store
     */
    @Test
    public void testGlobalSmartStoreOperationsWithOneUserOneStore() throws JSONException {
        // No store initially
        Assert.assertFalse("Store should not be found", manager.hasGlobalSmartStore("store"));
        Assert.assertEquals("No stores should be found", 0, manager.getGlobalStoresPrefixList().size());

        // Create store
        SmartStore store = createAndPopulateGlobalSmartStore("store");
        Assert.assertEquals("Wrong db path", computeExpectedGlobalSmartStorePath("store"), store.getDatabase().getPath());
        Assert.assertTrue("Store should be found", manager.hasGlobalSmartStore("store"));
        Assert.assertEquals("Wrong store names", 1, manager.getGlobalStoresPrefixList().size());
        Assert.assertEquals("Wrong store names", "store", manager.getGlobalStoresPrefixList().get(0));

        // Remove store
        manager.removeGlobalSmartStore("store");
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalSmartStore("store"));
        Assert.assertEquals("No stores should be found", 0, manager.getGlobalStoresPrefixList().size());
    }

    /**
     * Using getKeyValueStore / hasKeyValueStore / getKeyValueStoresPrefixList / removeKeyValueStore
     * with a single user and single store
     */
    @Test
    public void testKeyValueStoreOperationsWithOneUserOneStore() throws JSONException {
        UserAccount user = createTestAccount("00Dorg", "user", null,"first@test.com");

        // No store initially
        Assert.assertFalse("Store should not be found", manager.hasKeyValueStore("store", user));
        Assert.assertEquals("No stores should be found", 0, manager.getKeyValueStoresPrefixList(user).size());

        // Create store
        KeyValueEncryptedFileStore store = createAndPopulateKeyValueStore("store", user);
        Assert.assertEquals("Wrong store dir", computeExpectedKeyValueStorePath("store", user), store.getStoreDir().getAbsolutePath());
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store", user));
        Assert.assertEquals("Wrong store names", 1, manager.getKeyValueStoresPrefixList(user).size());
        Assert.assertEquals("Wrong store names", "store", manager.getKeyValueStoresPrefixList(user).get(0));

        // Remove store
        manager.removeKeyValueStore("store", user);
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store", user));
        Assert.assertEquals("No stores should be found", 0, manager.getKeyValueStoresPrefixList(user).size());
    }

    /**
     * Using getGlobalKeyValueStore / hasGlobalKeyValueStore / getGlobalKeyValueStoresPrefixList / removeGlobalKeyValueStore
     * with a single user and single store
     */
    @Test
    public void testGlobalKeyValueStoreOperationsWithOneUserOneStore() throws JSONException {
        // No store initially
        Assert.assertFalse("Store should not be found", manager.hasGlobalKeyValueStore("store"));
        Assert.assertEquals("No stores should be found", 0, manager.getGlobalKeyValueStoresPrefixList().size());

        // Create store
        KeyValueEncryptedFileStore store = createAndPopulateGlobalKeyValueStore("store");
        Assert.assertEquals("Wrong store dir", computeExpectedGlobalKeyValueStorePath("store"), store.getStoreDir().getAbsolutePath());
        Assert.assertTrue("Store should be found", manager.hasGlobalKeyValueStore("store"));
        Assert.assertEquals("Wrong store names", 1, manager.getGlobalKeyValueStoresPrefixList().size());
        Assert.assertEquals("Wrong store names", "store", manager.getGlobalKeyValueStoresPrefixList().get(0));

        // Remove store
        manager.removeGlobalKeyValueStore("store");
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalKeyValueStore("store"));
        Assert.assertEquals("No stores should be found", 0, manager.getGlobalKeyValueStoresPrefixList().size());
    }

    /**
     * Using getSmartStore / hasSmartStore / getUserStoresPrefixList / removeSmartStore / removeAllUserStores
     * with a single user and multiple stores
     */
    @Test
    public void testSmartStoreOperationsWithOneUserMultipleStores() throws JSONException {
        UserAccount user = createTestAccount("00Dorg", "user", null,"first@test.com");

        // No store initially
        Assert.assertFalse("Store1 should not be found", manager.hasSmartStore("store1", user, null));
        Assert.assertFalse("Store2 should not be found", manager.hasSmartStore("store2", user, null));
        Assert.assertFalse("Store3 should not be found", manager.hasSmartStore("store3", user, null));
        Assert.assertEquals("No stores should be found", 0, manager.getUserStoresPrefixList(user).size());

        // Create stores
        SmartStore store1 = createAndPopulateSmartStore("store1", user);
        Assert.assertEquals("Wrong db path", computeExpectedSmartStorePath("store1", user), store1.getDatabase().getPath());
        SmartStore store2 = createAndPopulateSmartStore("store2", user);
        Assert.assertEquals("Wrong db path", computeExpectedSmartStorePath("store2", user), store2.getDatabase().getPath());
        SmartStore store3 = createAndPopulateSmartStore("store3", user);
        Assert.assertEquals("Wrong db path", computeExpectedSmartStorePath("store3", user), store3.getDatabase().getPath());

        Assert.assertTrue("Store should be found", manager.hasSmartStore("store1", user, null));
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store2", user, null));
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store3", user, null));

        Assert.assertEquals("Wrong store names", 3, manager.getUserStoresPrefixList(user).size());
        Assert.assertTrue("Wrong store names", manager.getUserStoresPrefixList(user).contains("store1"));
        Assert.assertTrue("Wrong store names", manager.getUserStoresPrefixList(user).contains("store2"));
        Assert.assertTrue("Wrong store names", manager.getUserStoresPrefixList(user).contains("store3"));

        // Remove one store with removeSmartStore
        manager.removeSmartStore("store1", user, null);
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store1", user, null));

        // Remove all remaining stores with removeAllUserStores
        manager.removeAllUserStores(user);
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store2", user, null));
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store3", user, null));
        Assert.assertEquals("No stores should be found", 0, manager.getUserStoresPrefixList(user).size());
    }

    /**
     * Using getGlobalSmartStore / hasGlobalSmartStore / getGlobalStoresPrefixList / removeGlobalSmartStore / removeAllGlobalStores
     * with multiple global smartstores
     */
    @Test
    public void testGlobalSmartStoreOperationsWithMultipleStores() throws JSONException {
        // No store initially
        Assert.assertFalse("Store1 should not be found", manager.hasGlobalSmartStore("store1"));
        Assert.assertFalse("Store2 should not be found", manager.hasGlobalSmartStore("store2"));
        Assert.assertFalse("Store3 should not be found", manager.hasGlobalSmartStore("store3"));
        Assert.assertEquals("No stores should be found", 0, manager.getGlobalStoresPrefixList().size());

        // Create stores
        SmartStore store1 = createAndPopulateGlobalSmartStore("store1");
        Assert.assertEquals("Wrong store dir", computeExpectedGlobalSmartStorePath("store1"), store1.getDatabase().getPath());
        SmartStore store2 = createAndPopulateGlobalSmartStore("store2");
        Assert.assertEquals("Wrong store dir", computeExpectedGlobalSmartStorePath("store2"), store2.getDatabase().getPath());
        SmartStore store3 = createAndPopulateGlobalSmartStore("store3");
        Assert.assertEquals("Wrong store dir", computeExpectedGlobalSmartStorePath("store3"), store3.getDatabase().getPath());

        Assert.assertTrue("Store should be found", manager.hasGlobalSmartStore("store1"));
        Assert.assertTrue("Store should be found", manager.hasGlobalSmartStore("store2"));
        Assert.assertTrue("Store should be found", manager.hasGlobalSmartStore("store3"));

        Assert.assertEquals("Wrong store names", 3, manager.getGlobalStoresPrefixList().size());
        Assert.assertTrue("Wrong store names", manager.getGlobalStoresPrefixList().contains("store1"));
        Assert.assertTrue("Wrong store names", manager.getGlobalStoresPrefixList().contains("store2"));
        Assert.assertTrue("Wrong store names", manager.getGlobalStoresPrefixList().contains("store3"));

        // Remove one store with removeGlobalKeyValueStore
        manager.removeGlobalSmartStore("store1");
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalSmartStore("store1"));

        // Remove all remaining stores with removeAllGlobalKeyValueStores
        manager.removeAllGlobalStores();
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalSmartStore("store2"));
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalSmartStore("store3"));
        Assert.assertEquals("No stores should be found", 0, manager.getGlobalStoresPrefixList().size());
    }

    /**
     * Using getKeyValueStore / hasKeyValueStore / getKeyValueStoresPrefixList / removeKeyValueStore / removeAllKeyValueStores
     * with a single user and multiple stores
     */
    @Test
    public void testKeyValueStoreOperationsWithOneUserMultipleStores() throws JSONException {
        UserAccount user = createTestAccount("00Dorg", "user", null,"first@test.com");

        // No store initially
        Assert.assertFalse("Store1 should not be found", manager.hasKeyValueStore("store1", user));
        Assert.assertFalse("Store2 should not be found", manager.hasKeyValueStore("store2", user));
        Assert.assertFalse("Store3 should not be found", manager.hasKeyValueStore("store3", user));
        Assert.assertEquals("No stores should be found", 0, manager.getKeyValueStoresPrefixList(user).size());

        // Create stores
        KeyValueEncryptedFileStore store1 = createAndPopulateKeyValueStore("store1", user);
        Assert.assertEquals("Wrong store dir", computeExpectedKeyValueStorePath("store1", user), store1.getStoreDir().getAbsolutePath());
        KeyValueEncryptedFileStore store2 = createAndPopulateKeyValueStore("store2", user);
        Assert.assertEquals("Wrong store dir", computeExpectedKeyValueStorePath("store2", user), store2.getStoreDir().getAbsolutePath());
        KeyValueEncryptedFileStore store3 = createAndPopulateKeyValueStore("store3", user);
        Assert.assertEquals("Wrong store dir", computeExpectedKeyValueStorePath("store3", user), store3.getStoreDir().getAbsolutePath());

        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store1", user));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store3", user));

        Assert.assertEquals("Wrong store names", 3, manager.getKeyValueStoresPrefixList(user).size());
        Assert.assertTrue("Wrong store names", manager.getKeyValueStoresPrefixList(user).contains("store1"));
        Assert.assertTrue("Wrong store names", manager.getKeyValueStoresPrefixList(user).contains("store2"));
        Assert.assertTrue("Wrong store names", manager.getKeyValueStoresPrefixList(user).contains("store3"));

        // Remove one store with removeKeyValueStore
        manager.removeKeyValueStore("store1", user);
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user));

        // Remove all remaining stores with removeAllKeyValueStores
        manager.removeAllKeyValueStores(user);
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store2", user));
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store3", user));
        Assert.assertEquals("No stores should be found", 0, manager.getKeyValueStoresPrefixList(user).size());
    }

    /**
     * Using getGlobalKeyValueStore / hasGlobalKeyValueStore / getGlobalKeyValueStoresPrefixList / removeGlobalKeyValueStore / removeAllGlobalKeyValueStores
     * with multiple global stores
     */
    @Test
    public void testGlobalKeyValueStoreOperationsWithMultipleStores() throws JSONException {
        // No store initially
        Assert.assertFalse("Store1 should not be found", manager.hasGlobalKeyValueStore("store1"));
        Assert.assertFalse("Store2 should not be found", manager.hasGlobalKeyValueStore("store2"));
        Assert.assertFalse("Store3 should not be found", manager.hasGlobalKeyValueStore("store3"));
        Assert.assertEquals("No stores should be found", 0, manager.getGlobalKeyValueStoresPrefixList().size());

        // Create stores
        KeyValueEncryptedFileStore store1 = createAndPopulateGlobalKeyValueStore("store1");
        Assert.assertEquals("Wrong store dir", computeExpectedGlobalKeyValueStorePath("store1"), store1.getStoreDir().getAbsolutePath());
        KeyValueEncryptedFileStore store2 = createAndPopulateGlobalKeyValueStore("store2");
        Assert.assertEquals("Wrong store dir", computeExpectedGlobalKeyValueStorePath("store2"), store2.getStoreDir().getAbsolutePath());
        KeyValueEncryptedFileStore store3 = createAndPopulateGlobalKeyValueStore("store3");
        Assert.assertEquals("Wrong store dir", computeExpectedGlobalKeyValueStorePath("store3"), store3.getStoreDir().getAbsolutePath());

        Assert.assertTrue("Store should be found", manager.hasGlobalKeyValueStore("store1"));
        Assert.assertTrue("Store should be found", manager.hasGlobalKeyValueStore("store2"));
        Assert.assertTrue("Store should be found", manager.hasGlobalKeyValueStore("store3"));

        Assert.assertEquals("Wrong store names", 3, manager.getGlobalKeyValueStoresPrefixList().size());
        Assert.assertTrue("Wrong store names", manager.getGlobalKeyValueStoresPrefixList().contains("store1"));
        Assert.assertTrue("Wrong store names", manager.getGlobalKeyValueStoresPrefixList().contains("store2"));
        Assert.assertTrue("Wrong store names", manager.getGlobalKeyValueStoresPrefixList().contains("store3"));

        // Remove one store with removeGlobalKeyValueStore
        manager.removeGlobalKeyValueStore("store1");
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalKeyValueStore("store1"));

        // Remove all remaining stores with removeAllGlobalKeyValueStores
        manager.removeAllGlobalKeyValueStores();
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalKeyValueStore("store2"));
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalKeyValueStore("store3"));
        Assert.assertEquals("No stores should be found", 0, manager.getGlobalKeyValueStoresPrefixList().size());
    }

    /**
     * Using getSmartStore / hasSmartStore / getUserStoresPrefixList / removSmartStore / removeAllUserStores
     * with a single user and multiple communities
     */
    @Test
    public void testSmartStoreOperationsWithOneUserMultipleComunities() throws JSONException {
        UserAccount user = createTestAccount("00Dorg", "user", null,"first@test.com");

        // No store initially
        Assert.assertEquals("No stores should be found", 0, manager.getUserStoresPrefixList(user).size());

        // Create stores
        SmartStore store1comm1 = createAndPopulateSmartStore("store1", user, "c1");
        Assert.assertEquals("Wrong db path", computeExpectedSmartStorePath("store1", user, "c1"), store1comm1.getDatabase().getPath());
        SmartStore store2comm1 = createAndPopulateSmartStore("store2", user, "c1");
        Assert.assertEquals("Wrong db path", computeExpectedSmartStorePath("store2", user, "c1"), store2comm1.getDatabase().getPath());
        SmartStore store1comm2 = createAndPopulateSmartStore("store1", user, "c2");
        Assert.assertEquals("Wrong db path", computeExpectedSmartStorePath("store1", user, "c2"), store1comm2.getDatabase().getPath());
        SmartStore store2comm2 = createAndPopulateSmartStore("store2", user, "c2");
        Assert.assertEquals("Wrong db path", computeExpectedSmartStorePath("store2", user, "c2"), store2comm2.getDatabase().getPath());

        Assert.assertTrue("Store should be found", manager.hasSmartStore("store1", user, "c1"));
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store2", user, "c1"));
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store1", user, "c2"));
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store2", user, "c2"));

        // NB: getUserStoresPrefixList only returns stores of the user (for the user's community if any)
        Assert.assertEquals("Wrong store names", 0, manager.getUserStoresPrefixList(user).size());

        // Remove one store with removeSmartStore
        manager.removeSmartStore("store1", user, "c1");
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store1", user, "c1"));
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store2", user, "c1"));
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store1", user, "c2"));
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store2", user, "c2"));

        // Remove all remaining stores with removeAllUserStores
        manager.removeAllUserStores(user);
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store1", user, "c1"));
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store2", user, "c1"));
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store1", user, "c2"));
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store2", user, "c2"));
        Assert.assertEquals("Wrong store names", 0, manager.getUserStoresPrefixList(user).size());
    }

    /**
     * Using getKeyValueStore / hasKeyValueStore / getKeyValueStoresPrefixList / removeKeyValueStore / removeAllKeyValueStores
     * with a single user and multiple communities
     */
    @Test
    public void testKeyValueStoreOperationsWithOneUserMultipleComunities() throws JSONException {
        UserAccount user = createTestAccount("00Dorg", "user", null,"first@test.com");

        // No store initially
        Assert.assertEquals("No stores should be found", 0, manager.getKeyValueStoresPrefixList(user).size());

        // Create stores
        KeyValueEncryptedFileStore store1comm1 = createAndPopulateKeyValueStore("store1", user, "c1");
        Assert.assertEquals("Wrong store dir", computeExpectedKeyValueStorePath("store1", user, "c1"), store1comm1.getStoreDir().getAbsolutePath());
        KeyValueEncryptedFileStore store2comm1 = createAndPopulateKeyValueStore("store2", user, "c1");
        Assert.assertEquals("Wrong store dir", computeExpectedKeyValueStorePath("store2", user, "c1"), store2comm1.getStoreDir().getAbsolutePath());
        KeyValueEncryptedFileStore store1comm2 = createAndPopulateKeyValueStore("store1", user, "c2");
        Assert.assertEquals("Wrong store dir", computeExpectedKeyValueStorePath("store1", user, "c2"), store1comm2.getStoreDir().getAbsolutePath());
        KeyValueEncryptedFileStore store2comm2 = createAndPopulateKeyValueStore("store2", user, "c2");
        Assert.assertEquals("Wrong store dir", computeExpectedKeyValueStorePath("store2", user, "c2"), store2comm2.getStoreDir().getAbsolutePath());

        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store1", user, "c1"));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user, "c1"));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store1", user, "c2"));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user, "c2"));

        // NB: getKeyValueStoresPrefix only returns stores of the user (for the user's community if any)
        Assert.assertEquals("Wrong store names", 0, manager.getKeyValueStoresPrefixList(user).size());

        // Remove one store with removeKeyValueStore
        manager.removeKeyValueStore("store1", user, "c1");
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user, "c1"));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user, "c1"));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store1", user, "c2"));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user, "c2"));

        // Remove all remaining stores with removeAllKeyValueStores
        manager.removeAllKeyValueStores(user);
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user, "c1"));
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store2", user, "c1"));
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user, "c2"));
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store2", user, "c2"));
        Assert.assertEquals("Wrong store names", 0, manager.getKeyValueStoresPrefixList(user).size());
    }

    /**
     * Using getSmartStore / hasSmartStore / getUserStoresPrefixList / removeSmartStore / removeAllUserStores
     * with a multiple users and store
     */
    @Test
    public void testSmartStoreOperationsWithMultipleUsers() throws JSONException {
        UserAccount user1 = createTestAccount("00Dorg1", "user1", null,"first@test.com");
        UserAccount user2 = createTestAccount("00Dorg2", "user2", null,"second@test.com");

        // No store initially
        Assert.assertEquals("No stores should be found", 0, manager.getUserStoresPrefixList(user1).size());
        Assert.assertEquals("No stores should be found", 0, manager.getUserStoresPrefixList(user2).size());

        // Create stores
        SmartStore store1user1 = createAndPopulateSmartStore("store1", user1, null);
        SmartStore store2user1 = createAndPopulateSmartStore("store2", user1, null);
        SmartStore store1user2 = createAndPopulateSmartStore("store1", user2, null);
        SmartStore store2user2 = createAndPopulateSmartStore("store2", user2, null);

        Assert.assertTrue("Store should be found", manager.hasSmartStore("store1", user1, null));
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store2", user1, null));
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store1", user2, null));
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store2", user2, null));
        Assert.assertEquals("Wrong store names", 2, manager.getUserStoresPrefixList(user1).size());
        Assert.assertEquals("Wrong store names", 2, manager.getUserStoresPrefixList(user2).size());

        // Remove one store of user1 with removeSmartStore
        manager.removeSmartStore("store1", user1, null);
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store1", user1, null));
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store2", user1, null));
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store1", user2, null));
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store2", user2, null));
        Assert.assertEquals("Wrong store names", 1, manager.getUserStoresPrefixList(user1).size());
        Assert.assertEquals("Wrong store names", 2, manager.getUserStoresPrefixList(user2).size());

        // Remove all stores of user2 with removeAllUserStores
        manager.removeAllUserStores(user2);
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store1", user1, null));
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store2", user1, null));
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store1", user2, null));
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store2", user2, null));
        Assert.assertEquals("Wrong store names", 1, manager.getUserStoresPrefixList(user1).size());
        Assert.assertEquals("Wrong store names", 0, manager.getUserStoresPrefixList(user2).size());

        // Remove all remaining stores of user1 with removeAllUserStores
        manager.removeAllUserStores(user1);
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store1", user1, null));
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store2", user1, null));
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store1", user2, null));
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store2", user2, null));
        Assert.assertEquals("Wrong store names", 0, manager.getUserStoresPrefixList(user1).size());
        Assert.assertEquals("Wrong store names", 0, manager.getUserStoresPrefixList(user2).size());
    }

    /**
     * Using getKeyValueStore / hasKeyValueStore / getKeyValueStoresPrefixList / removeKeyValueStore / removeAllKeyValueStores
     * with a multiple users and store
     */
    @Test
    public void testKeyValueStoreOperationsWithMultipleUsers() throws JSONException {
        UserAccount user1 = createTestAccount("00Dorg1", "user1", null,"first@test.com");
        UserAccount user2 = createTestAccount("00Dorg2", "user2", null,"second@test.com");

        // No store initially
        Assert.assertEquals("No stores should be found", 0, manager.getKeyValueStoresPrefixList(user1).size());
        Assert.assertEquals("No stores should be found", 0, manager.getKeyValueStoresPrefixList(user2).size());

        // Create stores
        KeyValueEncryptedFileStore store1user1 = createAndPopulateKeyValueStore("store1", user1);
        KeyValueEncryptedFileStore store2user1 = createAndPopulateKeyValueStore("store2", user1);
        KeyValueEncryptedFileStore store1user2 = createAndPopulateKeyValueStore("store1", user2);
        KeyValueEncryptedFileStore store2user2 = createAndPopulateKeyValueStore("store2", user2);

        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store1", user1));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user1));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store1", user2));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user2));
        Assert.assertEquals("Wrong store names", 2, manager.getKeyValueStoresPrefixList(user1).size());
        Assert.assertEquals("Wrong store names", 2, manager.getKeyValueStoresPrefixList(user2).size());

        // Remove one store of user1 with removeKeyValueStore
        manager.removeKeyValueStore("store1", user1);
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user1));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user1));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store1", user2));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user2));
        Assert.assertEquals("Wrong store names", 1, manager.getKeyValueStoresPrefixList(user1).size());
        Assert.assertEquals("Wrong store names", 2, manager.getKeyValueStoresPrefixList(user2).size());

        // Remove all stores of user2 with removeAllKeyValueStores
        manager.removeAllKeyValueStores(user2);
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user1));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user1));
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user2));
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store2", user2));
        Assert.assertEquals("Wrong store names", 1, manager.getKeyValueStoresPrefixList(user1).size());
        Assert.assertEquals("Wrong store names", 0, manager.getKeyValueStoresPrefixList(user2).size());

        // Remove all remaining stores of user1 with removeAllKeyValueStores
        manager.removeAllKeyValueStores(user1);
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user1));
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store2", user1));
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user2));
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store2", user2));
        Assert.assertEquals("Wrong store names", 0, manager.getKeyValueStoresPrefixList(user1).size());
        Assert.assertEquals("Wrong store names", 0, manager.getKeyValueStoresPrefixList(user2).size());
    }

    /**
     * Using smartstores operations with a mix of global stores and user stores
     */
    @Test
    public void testSmartStoreOperationsWithUserAndGlobalStores() throws JSONException {
        UserAccount user = createTestAccount("00Dorg", "user", null,"first@test.com");

        // No store initially
        Assert.assertEquals("No stores should be found", 0, manager.getUserStoresPrefixList(user).size());
        Assert.assertEquals("No global stores should be found", 0, manager.getGlobalKeyValueStoresPrefixList().size());

        // Create stores
        SmartStore store1user = createAndPopulateSmartStore("store1", user);
        SmartStore store2user = createAndPopulateSmartStore("store2", user);
        SmartStore store1global = createAndPopulateGlobalSmartStore("store1");
        SmartStore store2global = createAndPopulateGlobalSmartStore("store2");

        Assert.assertTrue("Store should be found", manager.hasSmartStore("store1", user, null));
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store2", user, null));
        Assert.assertTrue("Store should be found", manager.hasGlobalSmartStore("store1"));
        Assert.assertTrue("Store should be found", manager.hasGlobalSmartStore("store2"));
        Assert.assertEquals("Wrong store names", 2, manager.getUserStoresPrefixList(user).size());
        Assert.assertEquals("Wrong store names", 2, manager.getGlobalStoresPrefixList().size());

        // Remove one store of user with removeSmartStore
        manager.removeSmartStore("store1", user, null);
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store1", user, null));
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store2", user, null));
        Assert.assertTrue("Store should be found", manager.hasGlobalSmartStore("store1"));
        Assert.assertTrue("Store should be found", manager.hasGlobalSmartStore("store2"));
        Assert.assertEquals("Wrong store names", 1, manager.getUserStoresPrefixList(user).size());
        Assert.assertEquals("Wrong store names", 2, manager.getGlobalStoresPrefixList().size());

        // Remove all global stores with removeAllGlobalStores
        manager.removeAllGlobalStores();
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store1", user, null));
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store2", user, null));
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalSmartStore("store1"));
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalSmartStore("store2"));
        Assert.assertEquals("Wrong store names", 1, manager.getUserStoresPrefixList(user).size());
        Assert.assertEquals("Wrong store names", 0, manager.getGlobalStoresPrefixList().size());

        // Remove all remaining stores of user with removeAllKeyValueStores
        manager.removeAllUserStores(user);
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store1", user, null));
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store2", user, null));
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalSmartStore("store1"));
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalSmartStore("store2"));
        Assert.assertEquals("Wrong store names", 0, manager.getUserStoresPrefixList(user).size());
        Assert.assertEquals("Wrong store names", 0, manager.getGlobalStoresPrefixList().size());
    }


    /**
     * Using key value stores operations with a mix of global stores and user stores
     */
    @Test
    public void testKeyValueStoreOperationsWithUserAndGlobalStores() throws JSONException {
        UserAccount user = createTestAccount("00Dorg", "user", null,"first@test.com");

        // No store initially
        Assert.assertEquals("No stores should be found", 0, manager.getKeyValueStoresPrefixList(user).size());
        Assert.assertEquals("No global stores should be found", 0, manager.getGlobalKeyValueStoresPrefixList().size());

        // Create stores
        KeyValueEncryptedFileStore store1user = createAndPopulateKeyValueStore("store1", user);
        KeyValueEncryptedFileStore store2user = createAndPopulateKeyValueStore("store2", user);
        KeyValueEncryptedFileStore store1global = createAndPopulateGlobalKeyValueStore("store1");
        KeyValueEncryptedFileStore store2global = createAndPopulateGlobalKeyValueStore("store2");

        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store1", user));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user));
        Assert.assertTrue("Store should be found", manager.hasGlobalKeyValueStore("store1"));
        Assert.assertTrue("Store should be found", manager.hasGlobalKeyValueStore("store2"));
        Assert.assertEquals("Wrong store names", 2, manager.getKeyValueStoresPrefixList(user).size());
        Assert.assertEquals("Wrong store names", 2, manager.getGlobalKeyValueStoresPrefixList().size());

        // Remove one store of user with removeKeyValueStore
        manager.removeKeyValueStore("store1", user);
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user));
        Assert.assertTrue("Store should be found", manager.hasGlobalKeyValueStore("store1"));
        Assert.assertTrue("Store should be found", manager.hasGlobalKeyValueStore("store2"));
        Assert.assertEquals("Wrong store names", 1, manager.getKeyValueStoresPrefixList(user).size());
        Assert.assertEquals("Wrong store names", 2, manager.getGlobalKeyValueStoresPrefixList().size());

        // Remove all global stores with removeAllGlobalKeyValueStores
        manager.removeAllGlobalKeyValueStores();
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user));
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalKeyValueStore("store1"));
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalKeyValueStore("store2"));
        Assert.assertEquals("Wrong store names", 1, manager.getKeyValueStoresPrefixList(user).size());
        Assert.assertEquals("Wrong store names", 0, manager.getGlobalKeyValueStoresPrefixList().size());

        // Remove all remaining stores of user with removeAllKeyValueStores
        manager.removeAllKeyValueStores(user);
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user));
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store2", user));
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalKeyValueStore("store1"));
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalKeyValueStore("store2"));
        Assert.assertEquals("Wrong store names", 0, manager.getKeyValueStoresPrefixList(user).size());
        Assert.assertEquals("Wrong store names", 0, manager.getGlobalKeyValueStoresPrefixList().size());
    }

    //
    // Helper methods
    //
    private String computeExpectedSmartStorePath(String storeName, UserAccount account) {
        return context.getApplicationInfo().dataDir + "/" + DBOpenHelper.DATABASES + "/" + storeName + account.getCommunityLevelFilenameSuffix() + ".db";
    }

    private String computeExpectedSmartStorePath(String storeName, UserAccount account, String communityId) {
        return context.getApplicationInfo().dataDir + "/" + DBOpenHelper.DATABASES + "/" + storeName + account.getCommunityLevelFilenameSuffix(communityId) + ".db";
    }

    private String computeExpectedGlobalSmartStorePath(String storeName) {
        return context.getApplicationInfo().dataDir + "/" + DBOpenHelper.DATABASES + "/" + storeName + ".db";
    }

    private String computeExpectedKeyValueStorePath(String storeName, UserAccount account) {
        return context.getApplicationInfo().dataDir + "/" + KeyValueEncryptedFileStore.KEY_VALUE_STORES + "/" + storeName + account.getCommunityLevelFilenameSuffix();
    }

    private String computeExpectedKeyValueStorePath(String storeName, UserAccount account, String communityId) {
        return context.getApplicationInfo().dataDir + "/" + KeyValueEncryptedFileStore.KEY_VALUE_STORES + "/" + storeName + account.getCommunityLevelFilenameSuffix(communityId);
    }

    private String computeExpectedGlobalKeyValueStorePath(String storeName) {
        return context.getApplicationInfo().dataDir + "/" + KeyValueEncryptedFileStore.KEY_VALUE_STORES + "/" + storeName + SmartStoreSDKManager.GLOBAL_SUFFIX;
    }

    private UserAccount createTestAccount(String orgId, String userId, String communityId, String username) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(UserAccount.AUTH_TOKEN, "test-auth-token");
        jsonObject.put(UserAccount.REFRESH_TOKEN, "test-refresh-token");
        jsonObject.put(UserAccount.LOGIN_SERVER, "https://test.salesforce.com");
        jsonObject.put(UserAccount.ID_URL, "https://cs1.salesforce.com/idurl");
        jsonObject.put(UserAccount.INSTANCE_SERVER, "https://cs1.salesforce.com");
        jsonObject.put(UserAccount.ORG_ID, orgId);
        jsonObject.put(UserAccount.USER_ID, userId);
        jsonObject.put(UserAccount.USERNAME, username);
        jsonObject.put(UserAccount.COMMUNITY_ID, communityId);
        jsonObject.put(UserAccount.COMMUNITY_URL, "https://mycommunity.salesforce.com");
        jsonObject.put(UserAccount.FIRST_NAME, "first-name-" + username);
        jsonObject.put(UserAccount.LAST_NAME, "last-name-" + username);
        jsonObject.put(UserAccount.DISPLAY_NAME, "display-name" + username);
        jsonObject.put(UserAccount.EMAIL, username );
        jsonObject.put(UserAccount.PHOTO_URL, "https://cs1.salesforce.com/photourl");
        jsonObject.put(UserAccount.THUMBNAIL_URL, "https://cs1.salesforce.com/thumbnailurl");

        return new UserAccount(jsonObject);
    }

    private KeyValueEncryptedFileStore createAndPopulateKeyValueStore(String storeName, UserAccount user) {
        KeyValueEncryptedFileStore store = manager.getKeyValueStore(storeName, user);
        populateKeyValueStore(store);
        return store;
    }

    private KeyValueEncryptedFileStore createAndPopulateKeyValueStore(String storeName, UserAccount user, String communityId) {
        KeyValueEncryptedFileStore store = manager.getKeyValueStore(storeName, user, communityId);
        populateKeyValueStore(store);
        return store;
    }

    private KeyValueEncryptedFileStore createAndPopulateGlobalKeyValueStore(String storeName) {
        KeyValueEncryptedFileStore store = manager.getGlobalKeyValueStore(storeName);
        populateKeyValueStore(store);
        return store;
    }

    private void populateKeyValueStore(KeyValueEncryptedFileStore store) {
        store.saveValue("key1", "value1");
        store.saveValue("key2", "value2");
        store.saveValue("key3", "value3");
    }

    private SmartStore createAndPopulateSmartStore(String storeName, UserAccount user) {
        SmartStore store = manager.getSmartStore(storeName, user, null);
        populateSmartStore(store);
        return store;
    }

    private SmartStore createAndPopulateSmartStore(String storeName, UserAccount user, String communityId) {
        SmartStore store = manager.getSmartStore(storeName, user, communityId);
        populateSmartStore(store);
        return store;
    }

    private SmartStore createAndPopulateGlobalSmartStore(String storeName) {
        SmartStore store = manager.getGlobalSmartStore(storeName);
        populateSmartStore(store);
        return store;
    }

    private void populateSmartStore(SmartStore store) {
        store.registerSoup("test_soup", new IndexSpec[] { new IndexSpec("key", Type.string) });
    }

}