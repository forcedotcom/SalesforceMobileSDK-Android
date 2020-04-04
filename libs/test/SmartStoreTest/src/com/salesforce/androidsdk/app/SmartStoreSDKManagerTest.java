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
import android.text.TextUtils;
import androidx.core.widget.TextViewCompat.AutoSizeTextType;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.smartstore.app.SmartStoreSDKManager;
import com.salesforce.androidsdk.smartstore.store.KeyValueEncryptedFileStore;
import com.salesforce.androidsdk.util.MapUtil;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

    /**
     * Using getKeyValueStore / hasKeyValueStore / removeKeyValueStore / removeAllKeyValueStore
     * with a single user and single store
     */
    @Test
    public void testKeyValueStoreOperationsWithOneUserOneStore() throws JSONException {
        UserAccount user = createTestAccount("org", "user", null,"first@test.com");

        // No store initially
        Assert.assertFalse("Store should not be found", manager.hasKeyValueStore("store", user));

        // Create store
        KeyValueEncryptedFileStore store = manager.getKeyValueStore("store", user);
        Assert.assertEquals("Wrong store dir", computeExpectedStorePath("store", user), store.getStoreDir().getAbsolutePath());
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store", user));

        // Remove store
        manager.removeKeyValueStore("store", user);
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store", user));
    }

    //
    // Helper methods
    //
    private String computeExpectedStorePath(String storeName, UserAccount account) {
        return context.getApplicationInfo().dataDir + "/" + KeyValueEncryptedFileStore.KEY_VALUE_STORES + "/" + storeName + account.getCommunityLevelFilenameSuffix();
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

}