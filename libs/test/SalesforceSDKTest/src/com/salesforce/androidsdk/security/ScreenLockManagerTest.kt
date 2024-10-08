/*
 * Copyright (c) 2023-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.security

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.accounts.UserAccountTest.TEST_ACCOUNT_NAME
import com.salesforce.androidsdk.accounts.UserAccountTest.TEST_AUTH_TOKEN
import com.salesforce.androidsdk.accounts.UserAccountTest.TEST_COMMUNITY_ID
import com.salesforce.androidsdk.accounts.UserAccountTest.TEST_COMMUNITY_URL
import com.salesforce.androidsdk.accounts.UserAccountTest.TEST_DISPLAY_NAME
import com.salesforce.androidsdk.accounts.UserAccountTest.TEST_EMAIL
import com.salesforce.androidsdk.accounts.UserAccountTest.TEST_FIRST_NAME
import com.salesforce.androidsdk.accounts.UserAccountTest.TEST_IDENTITY_URL
import com.salesforce.androidsdk.accounts.UserAccountTest.TEST_INSTANCE_URL
import com.salesforce.androidsdk.accounts.UserAccountTest.TEST_LANGUAGE
import com.salesforce.androidsdk.accounts.UserAccountTest.TEST_LAST_NAME
import com.salesforce.androidsdk.accounts.UserAccountTest.TEST_LOCALE
import com.salesforce.androidsdk.accounts.UserAccountTest.TEST_LOGIN_URL
import com.salesforce.androidsdk.accounts.UserAccountTest.TEST_ORG_ID
import com.salesforce.androidsdk.accounts.UserAccountTest.TEST_PHOTO_URL
import com.salesforce.androidsdk.accounts.UserAccountTest.TEST_REFRESH_TOKEN
import com.salesforce.androidsdk.accounts.UserAccountTest.TEST_THUMBNAIL_URL
import com.salesforce.androidsdk.accounts.UserAccountTest.TEST_USERNAME
import com.salesforce.androidsdk.accounts.UserAccountTest.TEST_USER_ID
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.security.ScreenLockManager.Companion.MOBILE_POLICY_PREF
import com.salesforce.androidsdk.security.ScreenLockManager.Companion.SCREEN_LOCK
import com.salesforce.androidsdk.security.ScreenLockManager.Companion.SCREEN_LOCK_TIMEOUT
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class ScreenLockManagerTest {
    private lateinit var screenLockManager: ScreenLockManager
    private val userAccount = buildTestUserAccount()
    private val ctx = SalesforceSDKManager.getInstance().appContext
    private val globalPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF, Context.MODE_PRIVATE)
    private val accountPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF
                + userAccount.userLevelFilenameSuffix, Context.MODE_PRIVATE
    )

    @Before
    fun setUp() {
        screenLockManager = ScreenLockManager()
    }

    @After
    fun tearDown() {
        globalPrefs.edit().remove(SCREEN_LOCK).remove(SCREEN_LOCK_TIMEOUT).apply()
        accountPrefs.edit().remove(SCREEN_LOCK).remove(SCREEN_LOCK_TIMEOUT).apply()
    }

    @Test
    fun testShouldNotLock() {
        Assert.assertFalse("Should not be locked by default.", screenLockManager.shouldLock())
        screenLockManager.onAppBackgrounded()
        Assert.assertFalse("Should not be locked without mobile policy set.", screenLockManager.shouldLock())
        screenLockManager.storeMobilePolicy(userAccount, false, 0)
        Assert.assertFalse("Should not be locked without mobile policy set.", screenLockManager.shouldLock())
        screenLockManager.storeMobilePolicy(userAccount, false, 0)
        Assert.assertFalse("Should not be locked if shouldLock is false.", screenLockManager.shouldLock())
    }

    @Test
    @Throws(InterruptedException::class)
    fun testShouldLock() {
        Assert.assertFalse("Should not be locked by default.", screenLockManager.shouldLock())
        screenLockManager.onAppBackgrounded()
        screenLockManager.storeMobilePolicy(userAccount, true, 1)
        Thread.sleep(10)
        Assert.assertTrue("Screen should lock.", screenLockManager.shouldLock())
    }

    @Test
    fun testStoreMobilePolicy() {
        Assert.assertFalse("Global Mobile Policy should not be set yet.", globalPrefs.getBoolean(SCREEN_LOCK, false))
        Assert.assertFalse("User Mobile Policy should not be set yet.", accountPrefs.getBoolean(SCREEN_LOCK, false))
        Assert.assertEquals(
            "User timeout should not be set yet.",
            -100,
            accountPrefs.getInt(SCREEN_LOCK_TIMEOUT, -100).toLong()
        )
        screenLockManager.storeMobilePolicy(userAccount, true, TIMEOUT)
        Assert.assertTrue("Global Mobile Policy should be set.", globalPrefs.getBoolean(SCREEN_LOCK, false))
        Assert.assertEquals(
            "Global timeout should be set",
            TIMEOUT.toLong(),
            globalPrefs.getInt(SCREEN_LOCK_TIMEOUT, -100).toLong()
        )
        Assert.assertTrue("User Mobile Policy should be set.", accountPrefs.getBoolean(SCREEN_LOCK, false))
        Assert.assertEquals(
            "User timeout should be set.",
            TIMEOUT.toLong(),
            accountPrefs.getInt(SCREEN_LOCK_TIMEOUT, -100).toLong()
        )
    }

    @Test
    @Throws(InterruptedException::class)
    fun testLockOnPause() {
        Assert.assertFalse("Should not be locked by default.", screenLockManager.shouldLock())
        screenLockManager.storeMobilePolicy(userAccount, true, 1)
        screenLockManager.onAppBackgrounded()
        Thread.sleep(10)
        Assert.assertTrue("Screen should lock.", screenLockManager.shouldLock())
    }

    @Test
    fun testLowestTimeout() {
        // Test low remains low
        screenLockManager.storeMobilePolicy(userAccount, true, TIMEOUT)
        Assert.assertEquals(
            "Baseline timeout",
            TIMEOUT.toLong(),
            globalPrefs.getInt(SCREEN_LOCK_TIMEOUT, -100).toLong()
        )
        screenLockManager.storeMobilePolicy(userAccount, true, LONG_TIMEOUT)
        Assert.assertEquals(
            "Timeout should still be low.",
            TIMEOUT.toLong(),
            globalPrefs.getInt(SCREEN_LOCK_TIMEOUT, -100).toLong()
        )
        screenLockManager.reset()
        // Test high gets lower
        screenLockManager.storeMobilePolicy(userAccount, true, LONG_TIMEOUT)
        Assert.assertEquals(
            "Baseline timeout",
            LONG_TIMEOUT.toLong(),
            globalPrefs.getInt(SCREEN_LOCK_TIMEOUT, -100).toLong()
        )
        screenLockManager.storeMobilePolicy(userAccount, true, TIMEOUT)
        Assert.assertEquals(
            "Timeout should be lowered.",
            TIMEOUT.toLong(),
            globalPrefs.getInt(SCREEN_LOCK_TIMEOUT, -100).toLong()
        )
    }

    @Test
    fun testReset() {
        screenLockManager.storeMobilePolicy(userAccount, true, TIMEOUT)
        screenLockManager.reset()
        Assert.assertFalse("Global Mobile Policy should not be set.", globalPrefs.getBoolean(SCREEN_LOCK, false))
        Assert.assertEquals(
            "Global timeout should not be set.",
            -100,
            globalPrefs.getInt(SCREEN_LOCK_TIMEOUT, -100).toLong()
        )
    }

    @Test
    fun testCleanUp() {
        UserAccountManager.getInstance().createAccount(userAccount);
        val storedUser = SalesforceSDKManager.getInstance().userAccountManager.authenticatedUsers[0]
        val storedUserPrefs = ctx.getSharedPreferences(
            (MOBILE_POLICY_PREF
                    + storedUser.userLevelFilenameSuffix), Context.MODE_PRIVATE
        )
        screenLockManager.storeMobilePolicy(storedUser, true, 60)
        screenLockManager.cleanUp(storedUser)
        Assert.assertFalse("User Mobile Policy should not be set.", storedUserPrefs.getBoolean(SCREEN_LOCK, false))
        Assert.assertFalse("Global Mobile Policy should not be set.", globalPrefs.getBoolean(SCREEN_LOCK, false))
    }

    @Test
    fun testIsEnabled() {
        Assert.assertFalse("Should not be enabled by default.", screenLockManager.enabled)
        screenLockManager.storeMobilePolicy(userAccount, false, 0)
        Assert.assertFalse("Should not be enabled without mobile policy set.", screenLockManager.enabled)
        screenLockManager.storeMobilePolicy(userAccount, true, 1)
        Assert.assertTrue("Should be enabled when user has mobile policy.", screenLockManager.enabled)
    }

    companion object {
        const val TIMEOUT = 60000
        const val LONG_TIMEOUT = 100000

        fun buildTestUserAccount(): UserAccount {
            return UserAccountBuilder.getInstance().authToken(TEST_AUTH_TOKEN)
                .refreshToken(TEST_REFRESH_TOKEN).loginServer(TEST_LOGIN_URL)
                .idUrl(TEST_IDENTITY_URL).instanceServer(TEST_INSTANCE_URL)
                .orgId(TEST_ORG_ID).userId(TEST_USER_ID)
                .username(TEST_USERNAME).accountName(TEST_ACCOUNT_NAME)
                .communityId(TEST_COMMUNITY_ID).communityUrl(TEST_COMMUNITY_URL)
                .firstName(TEST_FIRST_NAME).lastName(TEST_LAST_NAME)
                .displayName(TEST_DISPLAY_NAME).email(TEST_EMAIL)
                .photoUrl(TEST_PHOTO_URL).thumbnailUrl(TEST_THUMBNAIL_URL)
                .additionalOauthValues(null).language(TEST_LANGUAGE).locale(TEST_LOCALE)
                .build()
        }
    }
}