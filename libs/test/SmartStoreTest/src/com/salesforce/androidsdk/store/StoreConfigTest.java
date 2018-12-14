/*
 * Copyright (c) 2017-present, salesforce.com, inc.
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
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.salesforce.androidsdk.MainActivity;
import com.salesforce.androidsdk.smartstore.app.SmartStoreSDKManager;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.ui.LoginActivity;

import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class StoreConfigTest extends SmartStoreTestCase {

    private SmartStoreSDKManager sdkManager;
    private SmartStore globalStore;
    private SmartStore userStore;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        SmartStoreSDKTestManager.init(InstrumentationRegistry.getInstrumentation().getTargetContext(), store);
        sdkManager = SmartStoreSDKTestManager.getInstance();
        globalStore = sdkManager.getGlobalSmartStore();
        userStore = sdkManager.getSmartStore();
    }

    @Override
    protected String getEncryptionKey() {
        return sdkManager.getEncryptionKey();
    }

    @After
    public void tearDown() throws Exception {
        sdkManager.removeAllGlobalStores();
        super.tearDown();
    }

    @Test
    public void testSetupGlobalStoreFromDefaultConfig() throws JSONException {
        Assert.assertFalse(globalStore.hasSoup("globalSoup1"));
        Assert.assertFalse(globalStore.hasSoup("globalSoup2"));

        // Setting up soup
        sdkManager.setupGlobalStoreFromDefaultConfig();

        // Checking smartstore
        Assert.assertTrue(globalStore.hasSoup("globalSoup1"));
        Assert.assertTrue(globalStore.hasSoup("globalSoup2"));
        List<String> actualSoupNames = globalStore.getAllSoupNames();
        Assert.assertEquals("Wrong soups found", 2, actualSoupNames.size());
        Assert.assertTrue(actualSoupNames.contains("globalSoup1"));
        Assert.assertTrue(actualSoupNames.contains("globalSoup2"));

        // Checking first soup in details
        checkIndexSpecs("globalSoup1", new IndexSpec[]{
                new IndexSpec("stringField1", SmartStore.Type.string, "TABLE_1_0"),
                new IndexSpec("integerField1", SmartStore.Type.integer, "TABLE_1_1"),
                new IndexSpec("floatingField1", SmartStore.Type.floating, "TABLE_1_2"),
                new IndexSpec("json1Field1", SmartStore.Type.json1, "json_extract(soup, '$.json1Field1')"),
                new IndexSpec("ftsField1", SmartStore.Type.full_text, "TABLE_1_4"),
        });

        // Checking second soup in details
        checkIndexSpecs("globalSoup2", new IndexSpec[]{
                new IndexSpec("stringField2", SmartStore.Type.string, "TABLE_2_0"),
                new IndexSpec("integerField2", SmartStore.Type.integer, "TABLE_2_1"),
                new IndexSpec("floatingField2", SmartStore.Type.floating, "TABLE_2_2"),
                new IndexSpec("json1Field2", SmartStore.Type.json1, "json_extract(soup, '$.json1Field2')"),
                new IndexSpec("ftsField2", SmartStore.Type.full_text, "TABLE_2_4"),
        });
    }

    @Test
    public void testSetupUserStoreFromDefaultConfig() throws JSONException {
        Assert.assertFalse(userStore.hasSoup("userSoup1"));
        Assert.assertFalse(userStore.hasSoup("userSoup2"));

        // Setting up soup
        sdkManager.setupUserStoreFromDefaultConfig();

        // Checking smartstore
        Assert.assertTrue(userStore.hasSoup("userSoup1"));
        Assert.assertTrue(userStore.hasSoup("userSoup2"));
        List<String> actualSoupNames = userStore.getAllSoupNames();
        Assert.assertEquals("Wrong soups found", 2, actualSoupNames.size());
        Assert.assertTrue(actualSoupNames.contains("userSoup1"));
        Assert.assertTrue(actualSoupNames.contains("userSoup2"));

        // Checking first soup in details
        checkIndexSpecs("userSoup1", new IndexSpec[]{
                new IndexSpec("stringField1", SmartStore.Type.string, "TABLE_1_0"),
                new IndexSpec("integerField1", SmartStore.Type.integer, "TABLE_1_1"),
                new IndexSpec("floatingField1", SmartStore.Type.floating, "TABLE_1_2"),
                new IndexSpec("json1Field1", SmartStore.Type.json1, "json_extract(soup, '$.json1Field1')"),
                new IndexSpec("ftsField1", SmartStore.Type.full_text, "TABLE_1_4"),
        });

        // Checking second soup in details
        checkIndexSpecs("userSoup2", new IndexSpec[]{
                new IndexSpec("stringField2", SmartStore.Type.string, "TABLE_2_0"),
                new IndexSpec("integerField2", SmartStore.Type.integer, "TABLE_2_1"),
                new IndexSpec("floatingField2", SmartStore.Type.floating, "TABLE_2_2"),
                new IndexSpec("json1Field2", SmartStore.Type.json1, "json_extract(soup, '$.json1Field2')"),
                new IndexSpec("ftsField2", SmartStore.Type.full_text, "TABLE_2_4"),
        });
    }

    /**
     * Mock version of SmartStoreSDKManager - that gets passed the current user store in init()
     * That way we don't actually have to setup a user account
     */
    private static class SmartStoreSDKTestManager extends SmartStoreSDKManager {

        // We don't want to be using INSTANCE defined in SmartStoreSDKManager
        // Otherwise tests in other suites could fail after we call resetInstance(...)
        private static SmartStoreSDKTestManager TEST_INSTANCE = null;

        private final SmartStore userStore;

        /**
         * Protected constructor.
         *
         * @param context       Application context.
         *  @param userStore    The store to return from getSmartStore()
         */
        protected SmartStoreSDKTestManager(Context context, SmartStore userStore) {
            super(context, MainActivity.class, LoginActivity.class);
            this.userStore = userStore;
        }

        /**
         * Initializes this component.
         *  @param context      Application context.
         *  @param userStore    The store to return from getSmartStore()
         */
        public static void init(Context context, SmartStore userStore) {
            if (TEST_INSTANCE == null) {
                TEST_INSTANCE = new SmartStoreSDKTestManager(context, userStore);
            }
            initInternal(context);
        }

        /**
         * Returns a singleton instance of this class.
         *
         * @return Singleton instance of SmartStoreSDKManager.
         */
        public static SmartStoreSDKManager getInstance() {
            if (TEST_INSTANCE != null) {
                return TEST_INSTANCE;
            } else {
                throw new RuntimeException("Applications need to call SmartStoreSDKManager.init() first.");
            }
        }

        @Override
        public SmartStore getSmartStore() {
            return userStore;
        }
    }
}
