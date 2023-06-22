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
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountTest
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.rest.ClientManagerTest
import com.salesforce.androidsdk.security.BiometricAuthenticationManager.Companion.BIO_AUTH_POLICY
import com.salesforce.androidsdk.security.BiometricAuthenticationManager.Companion.BIO_AUTH_ENABLED
import com.salesforce.androidsdk.security.BiometricAuthenticationManager.Companion.BIO_AUTH_TIMEOUT
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class BiometricAuthenticationManagerTest {
    private val ctx = SalesforceSDKManager.getInstance().appContext
    private lateinit var bioAuthManager: BiometricAuthenticationManager
    private lateinit var userAccount: UserAccount
    private lateinit var accountPrefs: SharedPreferences

    @Before
    fun setUp() {
        bioAuthManager = BiometricAuthenticationManager()
        userAccount = ScreenLockManagerTest.buildTestUserAccount()
        accountPrefs = ctx.getSharedPreferences(
            BIO_AUTH_POLICY
                + userAccount.userLevelFilenameSuffix, Context.MODE_PRIVATE
        )

        val loginOptions = ClientManager.LoginOptions(
            UserAccountTest.TEST_LOGIN_URL, ClientManagerTest.TEST_CALLBACK_URL,
            ClientManagerTest.TEST_CLIENT_ID, ClientManagerTest.TEST_SCOPES
        )
        val clientManager = ClientManager(ctx, ClientManagerTest.TEST_ACCOUNT_TYPE, loginOptions, true)
        clientManager.createNewAccount(userAccount.accountName, userAccount.username, userAccount.refreshToken, userAccount.authToken,
            userAccount.instanceServer, userAccount.loginServer, userAccount.idUrl, userAccount.userId, userAccount.orgId,
            userAccount.userId, userAccount.communityId, userAccount.communityId, userAccount.firstName, userAccount.lastName,
            userAccount.displayName, userAccount.email, userAccount.photoUrl, userAccount.thumbnailUrl, userAccount.additionalOauthValues,
            userAccount.lightningDomain, userAccount.lightningSid, userAccount.vfDomain, userAccount.vfSid, userAccount.contentDomain,
            userAccount.contentSid, userAccount.csrfToken
        )
    }

    @After
    fun tearDown() {
        bioAuthManager.cleanUp(userAccount)
    }

    @Test
    fun testShouldNotLock() {
        Assert.assertFalse("Should not be locked by default.", bioAuthManager.shouldLock())
        bioAuthManager.onAppBackgrounded()
        Assert.assertFalse("Should not be locked without mobile policy set.", bioAuthManager.shouldLock())
        bioAuthManager.storeMobilePolicy(userAccount, false, 0)
        Assert.assertFalse("Should not be locked without mobile policy set.", bioAuthManager.shouldLock())
        bioAuthManager.storeMobilePolicy(userAccount, false, 0)
        Assert.assertFalse("Should not be locked if shouldLock is false.", bioAuthManager.shouldLock())
    }

    @Test
    fun testShouldLock() {
        Assert.assertFalse("Should not be locked by default.", bioAuthManager.shouldLock())
        bioAuthManager.storeMobilePolicy(userAccount, true, 1)
        bioAuthManager.onAppBackgrounded()
        Thread.sleep(10)
        Assert.assertTrue("Screen should lock.", bioAuthManager.shouldLock())
    }

    @Test
    fun testStoreMobilePolicy() {
        Assert.assertFalse("User Mobile Policy should not be set yet.", accountPrefs.getBoolean(BIO_AUTH_ENABLED, false))
        Assert.assertEquals(
            "User timeout should not be set yet.",
            -100,
            accountPrefs.getInt(BIO_AUTH_TIMEOUT, -100).toLong()
        )
        bioAuthManager.storeMobilePolicy(userAccount, true, ScreenLockManagerTest.TIMEOUT)
        Assert.assertTrue("User Mobile Policy should be set.", accountPrefs.getBoolean(BIO_AUTH_ENABLED, false))
        Assert.assertEquals(
            "User timeout should be set.",
            ScreenLockManagerTest.TIMEOUT.toLong(),
            accountPrefs.getInt(BIO_AUTH_TIMEOUT, -100).toLong()
        )
    }

    @Test
    fun testLockOnPause() {
        Assert.assertFalse("Should not be locked by default.", bioAuthManager.shouldLock())
        bioAuthManager.storeMobilePolicy(userAccount, true, 1)
        bioAuthManager.onAppBackgrounded()
        Thread.sleep(10)
        Assert.assertTrue("Screen should lock.", bioAuthManager.shouldLock())
    }

    @Test
    fun testCleanUp() {
        val storedUser = SalesforceSDKManager.getInstance().userAccountManager.authenticatedUsers[0]
        val storedUserPrefs = ctx.getSharedPreferences((BIO_AUTH_POLICY + storedUser.userLevelFilenameSuffix), Context.MODE_PRIVATE)
        bioAuthManager.storeMobilePolicy(storedUser, true, 60)
        bioAuthManager.cleanUp(storedUser)
        Assert.assertFalse("User Mobile Policy should not be set.", storedUserPrefs.getBoolean(BIO_AUTH_POLICY, false))
    }

    @Test
    fun testBiometricOptIn() {
        Assert.assertFalse("Should not be locked without mobile policy set.", bioAuthManager.hasBiometricOptedIn())
        bioAuthManager.storeMobilePolicy(userAccount, true, 0)
        Assert.assertFalse("Should not be opted in by default.", bioAuthManager.hasBiometricOptedIn())

        bioAuthManager.biometricOptIn(true)
        Assert.assertTrue("Should be opted in", bioAuthManager.hasBiometricOptedIn())

        bioAuthManager.biometricOptIn(false)
        Assert.assertFalse("User should have opted out again", bioAuthManager.hasBiometricOptedIn())
    }

    @Test
    fun testNativeBiometricLoginButton() {
        Assert.assertTrue("Should default to true.", bioAuthManager.isNativeBiometricLoginButtonEnabled())
        bioAuthManager.storeMobilePolicy(userAccount, true, 0)
        Assert.assertTrue("Should default to true.", bioAuthManager.isNativeBiometricLoginButtonEnabled())

        bioAuthManager.enableNativeBiometricLoginButton(false)
        Assert.assertFalse("Should be opted in", bioAuthManager.isNativeBiometricLoginButtonEnabled())

        bioAuthManager.enableNativeBiometricLoginButton(true)
        Assert.assertTrue("User should have opted in again", bioAuthManager.isNativeBiometricLoginButtonEnabled())
    }

    @Test
    fun testShouldAllowRefresh() {
        Assert.assertTrue("Should allow refresh by default.", bioAuthManager.shouldAllowRefresh())

        bioAuthManager.storeMobilePolicy(userAccount, false, 0)
        Assert.assertTrue("Should allow refresh with feature not enabled.", bioAuthManager.shouldAllowRefresh())

        bioAuthManager.storeMobilePolicy(userAccount, true, 1)
        Assert.assertTrue("Should allow refresh when app is not locked.", bioAuthManager.shouldAllowRefresh())

        bioAuthManager.lock()
        Assert.assertFalse("Should not alow refresh when app is locked.", bioAuthManager.shouldAllowRefresh())
    }

    @Test
    fun testIsEnabled() {
        Assert.assertFalse("Should not be enabled by default.", bioAuthManager.enabled)

        bioAuthManager.storeMobilePolicy(userAccount, false, 0)
        Assert.assertFalse("Should not be enabled without mobile policy set.", bioAuthManager.enabled)

        bioAuthManager.storeMobilePolicy(userAccount, true, 1)
        Assert.assertTrue("Should be enabled when user has mobile policy.", bioAuthManager.enabled)
    }

    @Test
    fun testIsLocked() {
        Assert.assertFalse("Should not be locked by default.", bioAuthManager.locked)

        bioAuthManager.storeMobilePolicy(userAccount, false, 0)
        Assert.assertFalse("Should not be enabled without mobile policy set.", bioAuthManager.locked)

        bioAuthManager.storeMobilePolicy(userAccount, true, 1)
        Assert.assertFalse("Should not be locked by default.", bioAuthManager.locked)

        bioAuthManager.onAppBackgrounded()
        Thread.sleep(10)
        bioAuthManager.onAppForegrounded()
        Assert.assertTrue("Should be locked upon background for timeout.", bioAuthManager.locked)

        bioAuthManager.locked = false
        Assert.assertFalse("Should be unlocked.", bioAuthManager.locked)

        bioAuthManager.lock()
        Assert.assertTrue("Should be locked by lock() API.", bioAuthManager.locked)
    }
}