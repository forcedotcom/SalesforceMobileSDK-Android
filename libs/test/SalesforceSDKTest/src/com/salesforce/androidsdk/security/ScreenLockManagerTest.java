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
package com.salesforce.androidsdk.security;

import static com.salesforce.androidsdk.accounts.UserAccountTest.TEST_LOGIN_URL;
import static com.salesforce.androidsdk.rest.ClientManagerTest.TEST_ACCOUNT_TYPE;
import static com.salesforce.androidsdk.rest.ClientManagerTest.TEST_CALLBACK_URL;
import static com.salesforce.androidsdk.rest.ClientManagerTest.TEST_CLIENT_ID;
import static com.salesforce.androidsdk.rest.ClientManagerTest.TEST_SCOPES;
import static com.salesforce.androidsdk.security.ScreenLockManager.MOBILE_POLICY_PREF;
import static com.salesforce.androidsdk.security.ScreenLockManager.SCREEN_LOCK;
import static com.salesforce.androidsdk.security.ScreenLockManager.SCREEN_LOCK_TIMEOUT;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountBuilder;
import com.salesforce.androidsdk.accounts.UserAccountTest;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.ClientManager;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for ScreenLockManager
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class ScreenLockManagerTest {
    private static final int TIMEOUT = 60000;
    private static final int LONG_TIMEOUT = 100000;

    private ScreenLockManager screenLockManager;
    private UserAccount userAccount = buildTestUserAccount();
    private Context ctx = SalesforceSDKManager.getInstance().getAppContext();
    private SharedPreferences sharedPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF, Context.MODE_PRIVATE);
    private SharedPreferences accountPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF
            + userAccount.getUserLevelFilenameSuffix(), Context.MODE_PRIVATE);

    @Before
    public void setUp() {
        screenLockManager = new ScreenLockManager();
    }

    @After
    public void tearDown() {
        sharedPrefs.edit().remove(SCREEN_LOCK).remove(SCREEN_LOCK_TIMEOUT).apply();
        accountPrefs.edit().remove(SCREEN_LOCK).remove(SCREEN_LOCK_TIMEOUT).apply();
    }

    @Test
    public void testShouldNotLock() {
        Assert.assertFalse("Should not be locked by default.", screenLockManager.shouldLock());
        screenLockManager.onAppBackgrounded();
        Assert.assertFalse("Should not be locked without mobile policy set.", screenLockManager.shouldLock());
        screenLockManager.storeMobilePolicy(userAccount, false, 0);
        Assert.assertFalse("Should not be locked without mobile policy set.", screenLockManager.shouldLock());

        screenLockManager.storeMobilePolicy(userAccount, false, 0);
        Assert.assertFalse("Should not be locked if shouldLock is false.", screenLockManager.shouldLock());
    }

    @Test
    public void testShouldLock() throws InterruptedException {
        Assert.assertFalse("Should not be locked by default.", screenLockManager.shouldLock());
        screenLockManager.onAppBackgrounded();
        screenLockManager.storeMobilePolicy(userAccount, true, 1);
        Thread.sleep(10);
        Assert.assertTrue("Screen should lock.", screenLockManager.shouldLock());
    }

    @Test
    public void testStoreMobilePolicy() {
        Assert.assertFalse("Global Mobile Policy should not be set yet.", sharedPrefs.getBoolean(SCREEN_LOCK, false));
        Assert.assertFalse("User Mobile Policy should not be set yet.", accountPrefs.getBoolean(SCREEN_LOCK, false));
        Assert.assertEquals("User timeout should not be set yet.", -100, accountPrefs.getInt(SCREEN_LOCK_TIMEOUT, -100));

        screenLockManager.storeMobilePolicy(userAccount, true, TIMEOUT);
        Assert.assertTrue("Global Mobile Policy should be set.", sharedPrefs.getBoolean(SCREEN_LOCK, false));
        Assert.assertEquals("Global timeout should be set", TIMEOUT, sharedPrefs.getInt(SCREEN_LOCK_TIMEOUT, -100));
        Assert.assertTrue("User Mobile Policy should be set.", accountPrefs.getBoolean(SCREEN_LOCK, false));
        Assert.assertEquals("User timeout should be set.", TIMEOUT, accountPrefs.getInt(SCREEN_LOCK_TIMEOUT, -100));
    }

    @Test
    public void testLockOnPause() throws InterruptedException {
        Assert.assertFalse("Should not be locked by default.", screenLockManager.shouldLock());
        screenLockManager.storeMobilePolicy(userAccount, true, 1);
        screenLockManager.onAppBackgrounded();
        Thread.sleep(10);
        Assert.assertTrue("Screen should lock.", screenLockManager.shouldLock());
    }

    @Test
    public void testLowestTimeout() {
        // Test low remains low
        screenLockManager.storeMobilePolicy(userAccount, true, TIMEOUT);
        Assert.assertEquals("Baseline timeout", TIMEOUT, sharedPrefs.getInt(SCREEN_LOCK_TIMEOUT, -100));
        screenLockManager.storeMobilePolicy(userAccount, true, LONG_TIMEOUT);
        Assert.assertEquals("Timeout should still be low.", TIMEOUT, sharedPrefs.getInt(SCREEN_LOCK_TIMEOUT, -100));

        screenLockManager.reset();
        // Test high gets lower
        screenLockManager.storeMobilePolicy(userAccount, true, LONG_TIMEOUT);
        Assert.assertEquals("Baseline timeout", LONG_TIMEOUT, sharedPrefs.getInt(SCREEN_LOCK_TIMEOUT, -100));
        screenLockManager.storeMobilePolicy(userAccount, true, TIMEOUT);
        Assert.assertEquals("Timeout should be lowered.", TIMEOUT, sharedPrefs.getInt(SCREEN_LOCK_TIMEOUT, -100));
    }

    @Test
    public void testReset() {
        screenLockManager.storeMobilePolicy(userAccount, true, TIMEOUT);
        screenLockManager.reset();
        Assert.assertFalse("Global Mobile Policy should not be set.", sharedPrefs.getBoolean(SCREEN_LOCK, false));
        Assert.assertEquals("Global timeout should not be set.", -100, sharedPrefs.getInt(SCREEN_LOCK_TIMEOUT, -100));
    }

    @Test
    public void testCleanUp() {
        final ClientManager.LoginOptions loginOptions = new ClientManager.LoginOptions(TEST_LOGIN_URL, TEST_CALLBACK_URL,
                TEST_CLIENT_ID, TEST_SCOPES);
        ClientManager clientManager = new ClientManager(ctx, TEST_ACCOUNT_TYPE, loginOptions, true);
        clientManager.createNewAccount(userAccount.getAccountName(), userAccount.getUsername(), userAccount.getRefreshToken(),
                userAccount.getAuthToken(), userAccount.getInstanceServer(), userAccount.getLoginServer(), userAccount.getIdUrl(),
                userAccount.getUserId(), userAccount.getOrgId(), userAccount.getUserId(), userAccount.getCommunityId(), userAccount.getCommunityId(),
                userAccount.getFirstName(), userAccount.getLastName(), userAccount.getDisplayName(), userAccount.getEmail(), userAccount.getPhotoUrl(),
                userAccount.getThumbnailUrl(), userAccount.getAdditionalOauthValues(),
                userAccount.getLightningDomain(), userAccount.getLightningSid(), userAccount.getVFDomain(), userAccount.getVFSid(),
                userAccount.getContentDomain(), userAccount.getContentSid(), userAccount.getCSRFToken());
        UserAccount storedUser = SalesforceSDKManager.getInstance().getUserAccountManager().getAuthenticatedUsers().get(0);
        SharedPreferences storedUserPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF
                + storedUser.getUserLevelFilenameSuffix(), Context.MODE_PRIVATE);

        screenLockManager.storeMobilePolicy(storedUser, true, 60);
        screenLockManager.cleanUp(storedUser);
        Assert.assertFalse("User Mobile Policy should not be set.", storedUserPrefs.getBoolean(SCREEN_LOCK, false));
        Assert.assertFalse("Global Mobile Policy should not be set.", sharedPrefs.getBoolean(SCREEN_LOCK, false));
    }

    private UserAccount buildTestUserAccount() {
        return UserAccountBuilder.getInstance().authToken(UserAccountTest.TEST_AUTH_TOKEN).
                refreshToken(UserAccountTest.TEST_REFRESH_TOKEN).loginServer(TEST_LOGIN_URL).
                idUrl(UserAccountTest.TEST_IDENTITY_URL).instanceServer(UserAccountTest.TEST_INSTANCE_URL).
                orgId(UserAccountTest.TEST_ORG_ID).userId(UserAccountTest.TEST_USER_ID).
                username(UserAccountTest.TEST_USERNAME).accountName(UserAccountTest.TEST_ACCOUNT_NAME).
                communityId(UserAccountTest.TEST_COMMUNITY_ID).communityUrl(UserAccountTest.TEST_COMMUNITY_URL).
                firstName(UserAccountTest.TEST_FIRST_NAME).lastName(UserAccountTest.TEST_LAST_NAME).
                displayName(UserAccountTest.TEST_DISPLAY_NAME).email(UserAccountTest.TEST_EMAIL).
                photoUrl(UserAccountTest.TEST_PHOTO_URL).thumbnailUrl(UserAccountTest.TEST_THUMBNAIL_URL).
                additionalOauthValues(null).build();
    }
}
