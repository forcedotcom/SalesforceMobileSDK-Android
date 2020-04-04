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
package com.salesforce.androidsdk.app;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.smartstore.app.SmartStoreSDKManager;
import com.salesforce.androidsdk.smartstore.store.KeyValueEncryptedFileStore;
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
    }

    /**
     * Create a store by calling getKeyValueStore
     * Populate that store
     * Get that store by calling getKeyValueStore
     * Make sure we find values stored through the first instance
     */
    @Test
    public void testGetKeyValueStoreReturnsSameStore() throws JSONException {
        UserAccount user = createTestAccount("org", "user", null,"first@test.com");
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
     * Using getKeyValueStore / hasKeyValueStore / getKeyValueStoresPrefix / removeKeyValueStore
     * with a single user and single store
     */
    @Test
    public void testKeyValueStoreOperationsWithOneUserOneStore() throws JSONException {
        UserAccount user = createTestAccount("org", "user", null,"first@test.com");

        // No store initially
        Assert.assertFalse("Store should not be found", manager.hasKeyValueStore("store", user));
        Assert.assertEquals("No stores should be found", 0, manager.getKeyValueStoresPrefix(user).size());

        // Create store
        KeyValueEncryptedFileStore store = createAndPopulateStore("store", user);
        Assert.assertEquals("Wrong store dir", computeExpectedStorePath("store", user), store.getStoreDir().getAbsolutePath());
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store", user));
        Assert.assertEquals("Wrong store names", 1, manager.getKeyValueStoresPrefix(user).size());
        Assert.assertEquals("Wrong store names", "store", manager.getKeyValueStoresPrefix(user).get(0));

        // Remove store
        manager.removeKeyValueStore("store", user);
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store", user));
        Assert.assertEquals("No stores should be found", 0, manager.getKeyValueStoresPrefix(user).size());
    }

    /**
     * Using getKeyValueStore / hasKeyValueStore / getKeyValueStoresPrefix / removeKeyValueStore / removeAllKeyValueStores
     * with a single user and multiple stores
     */
    @Test
    public void testKeyValueStoreOperationsWithOneUserMultipleStores() throws JSONException {
        UserAccount user = createTestAccount("org", "user", null,"first@test.com");

        // No store initially
        Assert.assertFalse("Store1 should not be found", manager.hasKeyValueStore("store1", user));
        Assert.assertFalse("Store2 should not be found", manager.hasKeyValueStore("store2", user));
        Assert.assertFalse("Store3 should not be found", manager.hasKeyValueStore("store3", user));
        Assert.assertEquals("No stores should be found", 0, manager.getKeyValueStoresPrefix(user).size());

        // Create stores
        KeyValueEncryptedFileStore store1 = createAndPopulateStore("store1", user);
        Assert.assertEquals("Wrong store dir", computeExpectedStorePath("store1", user), store1.getStoreDir().getAbsolutePath());
        KeyValueEncryptedFileStore store2 = createAndPopulateStore("store2", user);
        Assert.assertEquals("Wrong store dir", computeExpectedStorePath("store2", user), store2.getStoreDir().getAbsolutePath());
        KeyValueEncryptedFileStore store3 = createAndPopulateStore("store3", user);
        Assert.assertEquals("Wrong store dir", computeExpectedStorePath("store3", user), store3.getStoreDir().getAbsolutePath());

        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store1", user));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store3", user));

        Assert.assertEquals("Wrong store names", 3, manager.getKeyValueStoresPrefix(user).size());
        Assert.assertTrue("Wrong store names", manager.getKeyValueStoresPrefix(user).contains("store1"));
        Assert.assertTrue("Wrong store names", manager.getKeyValueStoresPrefix(user).contains("store2"));
        Assert.assertTrue("Wrong store names", manager.getKeyValueStoresPrefix(user).contains("store3"));

        // Remove one store with removeKeyValueStore
        manager.removeKeyValueStore("store1", user);
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user));

        // Remove all remaining stores with removeAllKeyValueStores
        manager.removeAllKeyValueStores(user);
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store2", user));
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store3", user));
        Assert.assertEquals("No stores should be found", 0, manager.getKeyValueStoresPrefix(user).size());
    }

    /**
     * Using getKeyValueStore / hasKeyValueStore / getKeyValueStoresPrefix / removeKeyValueStore / removeAllKeyValueStores
     * with a single user and multiple communities
     */
    @Test
    public void testKeyValueStoreOperationsWithOneUserMultipleComunities() throws JSONException {
        UserAccount user = createTestAccount("org", "user", null,"first@test.com");

        // No store initially
        Assert.assertEquals("No stores should be found", 0, manager.getKeyValueStoresPrefix(user).size());

        // Create stores
        KeyValueEncryptedFileStore store1comm1 = createAndPopulateStore("store1", user, "c1");
        Assert.assertEquals("Wrong store dir", computeExpectedStorePath("store1", user, "c1"), store1comm1.getStoreDir().getAbsolutePath());
        KeyValueEncryptedFileStore store2comm1 = createAndPopulateStore("store2", user, "c1");
        Assert.assertEquals("Wrong store dir", computeExpectedStorePath("store2", user, "c1"), store2comm1.getStoreDir().getAbsolutePath());
        KeyValueEncryptedFileStore store1comm2 = createAndPopulateStore("store1", user, "c2");
        Assert.assertEquals("Wrong store dir", computeExpectedStorePath("store1", user, "c2"), store1comm2.getStoreDir().getAbsolutePath());
        KeyValueEncryptedFileStore store2comm2 = createAndPopulateStore("store2", user, "c2");
        Assert.assertEquals("Wrong store dir", computeExpectedStorePath("store2", user, "c2"), store2comm2.getStoreDir().getAbsolutePath());

        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store1", user, "c1"));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user, "c1"));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store1", user, "c2"));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user, "c2"));

// FIXME getKeyValueStoresPrefix does not see commmunity scoped stores
//        Assert.assertEquals("Wrong store names", 2, manager.getKeyValueStoresPrefix(user).size());

        // Remove one store with removeKeyValueStore
        manager.removeKeyValueStore("store1", user, "c1");
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user, "c1"));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user, "c1"));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store1", user, "c2"));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user, "c2"));
//        Assert.assertEquals("Wrong store names", 1, manager.getKeyValueStoresPrefix(user1).size());
//        Assert.assertEquals("Wrong store names", 2, manager.getKeyValueStoresPrefix(user2).size());

        // Remove all remaining stores with removeAllKeyValueStores
        manager.removeAllKeyValueStores(user);
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user, "c1"));
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store2", user, "c1"));
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user, "c2"));
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store2", user, "c2"));
        Assert.assertEquals("Wrong store names", 0, manager.getKeyValueStoresPrefix(user).size());
    }

    /**
     * Using getKeyValueStore / hasKeyValueStore / getKeyValueStoresPrefix / removeKeyValueStore / removeAllKeyValueStores
     * with a multiple users and store
     */
    @Test
    public void testKeyValueStoreOperationsWithMultipleUsers() throws JSONException {
        UserAccount user1 = createTestAccount("org1", "user1", null,"first@test.com");
        UserAccount user2 = createTestAccount("org2", "user2", null,"second@test.com");

        // No store initially
        Assert.assertEquals("No stores should be found", 0, manager.getKeyValueStoresPrefix(user1).size());
        Assert.assertEquals("No stores should be found", 0, manager.getKeyValueStoresPrefix(user2).size());

        // Create stores
        KeyValueEncryptedFileStore store1user1 = createAndPopulateStore("store1", user1);
        KeyValueEncryptedFileStore store2user1 = createAndPopulateStore("store2", user1);
        KeyValueEncryptedFileStore store1user2 = createAndPopulateStore("store1", user2);
        KeyValueEncryptedFileStore store2user2 = createAndPopulateStore("store2", user2);

        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store1", user1));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user1));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store1", user2));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user2));
        Assert.assertEquals("Wrong store names", 2, manager.getKeyValueStoresPrefix(user1).size());
        Assert.assertEquals("Wrong store names", 2, manager.getKeyValueStoresPrefix(user2).size());

        // Remove one store of user1 with removeKeyValueStore
        manager.removeKeyValueStore("store1", user1);
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user1));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user1));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store1", user2));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user2));
        Assert.assertEquals("Wrong store names", 1, manager.getKeyValueStoresPrefix(user1).size());
        Assert.assertEquals("Wrong store names", 2, manager.getKeyValueStoresPrefix(user2).size());

        // Remove all stores of user2 with removeAllKeyValueStores
        manager.removeAllKeyValueStores(user2);
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user1));
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user1));
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user2));
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store2", user2));
        Assert.assertEquals("Wrong store names", 1, manager.getKeyValueStoresPrefix(user1).size());
        Assert.assertEquals("Wrong store names", 0, manager.getKeyValueStoresPrefix(user2).size());

        // Remove all remaining stores of user1 with removeAllKeyValueStores
        manager.removeAllKeyValueStores(user1);
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user1));
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store2", user1));
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user2));
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store2", user2));
        Assert.assertEquals("Wrong store names", 0, manager.getKeyValueStoresPrefix(user1).size());
        Assert.assertEquals("Wrong store names", 0, manager.getKeyValueStoresPrefix(user2).size());
    }


    //
    // Helper methods
    //
    private String computeExpectedStorePath(String storeName, UserAccount account) {
        return context.getApplicationInfo().dataDir + "/" + KeyValueEncryptedFileStore.KEY_VALUE_STORES + "/" + storeName + account.getCommunityLevelFilenameSuffix();
    }

    private String computeExpectedStorePath(String storeName, UserAccount account, String communityId) {
        return context.getApplicationInfo().dataDir + "/" + KeyValueEncryptedFileStore.KEY_VALUE_STORES + "/" + storeName + account.getCommunityLevelFilenameSuffix(communityId);
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

    private KeyValueEncryptedFileStore createAndPopulateStore(String storeName, UserAccount user) {
        KeyValueEncryptedFileStore store = manager.getKeyValueStore(storeName, user);
        // Storing some values
        store.saveValue("key1", "value1");
        store.saveValue("key2", "value2");
        store.saveValue("key3", "value3");
        return store;
    }

    private KeyValueEncryptedFileStore createAndPopulateStore(String storeName, UserAccount user, String communityId) {
        KeyValueEncryptedFileStore store = manager.getKeyValueStore(storeName, user, communityId);
        // Storing some values
        store.saveValue("key1", "value1");
        store.saveValue("key2", "value2");
        store.saveValue("key3", "value3");
        return store;
    }

}