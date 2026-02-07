/*
 * Copyright (c) 2026-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.accounts

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.OAuthConfig
import com.salesforce.androidsdk.ui.TokenMigrationActivity
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for UserAccountManager.migrateRefreshToken extension function.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class UserAccountManagerMigrateTokenTest {

    private lateinit var mockUserAccountManager: UserAccountManager
    private lateinit var mockContext: Context
    private lateinit var mockSdkManager: SalesforceSDKManager
    private lateinit var mockOAuthConfig: OAuthConfig

    private val onMigrationSuccess: (UserAccount) -> Unit = mockk(relaxed = true)
    private val onMigrationError: (String, String?, Throwable?) -> Unit = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockUserAccountManager = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        mockSdkManager = mockk(relaxed = true)
        mockOAuthConfig = mockk(relaxed = true)

        mockkObject(SalesforceSDKManager)
        every { SalesforceSDKManager.getInstance() } returns mockSdkManager
        every { mockSdkManager.appContext } returns mockContext
        mockkStatic(SalesforceSDKLogger::class)
        every { SalesforceSDKLogger.e(any(), any()) } just runs
        every { SalesforceSDKLogger.e(any(), any(), any()) } just runs
        every { SalesforceSDKLogger.i(any(), any()) } just runs

        mockkObject(MigrationCallbackRegistry)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun migrateRefreshToken_withNullUserAccount_callsOnError() {
        // Given
        mockkStatic(UserAccountManager::class)
        every { UserAccountManager.getInstance() } returns mockUserAccountManager
        every { mockUserAccountManager.currentUser } returns null

        // When
        mockUserAccountManager.migrateRefreshToken(
            userAccount = null,
            appConfig = mockOAuthConfig,
            onMigrationSuccess = onMigrationSuccess,
            onMigrationError = onMigrationError
        )

        // Then
        verify(exactly = 1) { onMigrationError.invoke("User account, userId or orgId is null.", null, null) }
        verify(exactly = 0) { onMigrationSuccess.invoke(any()) }
        verify(exactly = 0) { mockContext.startActivity(any()) }
    }

    @Test
    fun migrateRefreshToken_withNullUserId_callsOnError() {
        // Given
        val mockUserAccount: UserAccount = mockk(relaxed = true)
        every { mockUserAccount.userId } returns null
        every { mockUserAccount.orgId } returns "testOrgId"

        // When
        mockUserAccountManager.migrateRefreshToken(
            userAccount = mockUserAccount,
            appConfig = mockOAuthConfig,
            onMigrationSuccess = onMigrationSuccess,
            onMigrationError = onMigrationError
        )

        // Then
        verify(exactly = 1) { onMigrationError.invoke("User account, userId or orgId is null.", null, null) }
        verify(exactly = 0) { onMigrationSuccess.invoke(any()) }
        verify(exactly = 0) { mockContext.startActivity(any()) }
    }

    @Test
    fun migrateRefreshToken_withNullOrgId_callsOnError() {
        // Given
        val mockUserAccount: UserAccount = mockk(relaxed = true)
        every { mockUserAccount.userId } returns "testUserId"
        every { mockUserAccount.orgId } returns null

        // When
        mockUserAccountManager.migrateRefreshToken(
            userAccount = mockUserAccount,
            appConfig = mockOAuthConfig,
            onMigrationSuccess = onMigrationSuccess,
            onMigrationError = onMigrationError
        )

        // Then
        verify(exactly = 1) { onMigrationError.invoke("User account, userId or orgId is null.", null, null) }
        verify(exactly = 1) { SalesforceSDKLogger.e(any(), "User account, userId or orgId is null.") }
        verify(exactly = 0) { onMigrationSuccess.invoke(any()) }
        verify(exactly = 0) { mockContext.startActivity(any()) }
    }

    @Test
    fun migrateRefreshToken_withValidUserAccount_registersCallbackAndStartsActivity() {
        // Given
        val testUserId = "testUserId123"
        val testOrgId = "testOrgId456"
        val testCallbackKey = "test-callback-key"

        val mockUserAccount: UserAccount = mockk(relaxed = true)
        every { mockUserAccount.userId } returns testUserId
        every { mockUserAccount.orgId } returns testOrgId

        every { MigrationCallbackRegistry.register(any()) } returns testCallbackKey

        val intentSlot = slot<Intent>()
        every { mockContext.startActivity(capture(intentSlot)) } just Runs

        // When
        mockUserAccountManager.migrateRefreshToken(
            userAccount = mockUserAccount,
            appConfig = mockOAuthConfig,
            onMigrationSuccess = onMigrationSuccess,
            onMigrationError = onMigrationError
        )

        // Then
        verify(exactly = 1) { MigrationCallbackRegistry.register(any()) }
        verify(exactly = 1) { mockContext.startActivity(any()) }

        val capturedIntent = intentSlot.captured
        assertNotNull("Intent should not be null", capturedIntent)
        assertEquals("Intent should have NEW_TASK flag",
            Intent.FLAG_ACTIVITY_NEW_TASK,
            capturedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK
        )
        assertEquals("Intent should have correct org id",
            testOrgId,
            capturedIntent.getStringExtra(TokenMigrationActivity.EXTRA_ORG_ID)
        )
        assertEquals("Intent should have correct user id",
            testUserId,
            capturedIntent.getStringExtra(TokenMigrationActivity.EXTRA_USER_ID)
        )
        assertEquals("Intent should have correct callback key",
            testCallbackKey,
            capturedIntent.getStringExtra(TokenMigrationActivity.EXTRA_CALLBACK_ID)
        )
    }

    @Test
    fun migrateRefreshToken_withValidUserAccount_passesOAuthConfigInIntent() {
        // Given
        val mockUserAccount: UserAccount = mockk(relaxed = true)
        every { mockUserAccount.userId } returns "testUserId"
        every { mockUserAccount.orgId } returns "testOrgId"

        every { MigrationCallbackRegistry.register(any()) } returns "callback-key"

        val intentSlot = slot<Intent>()
        every { mockContext.startActivity(capture(intentSlot)) } just Runs

        // When
        mockUserAccountManager.migrateRefreshToken(
            userAccount = mockUserAccount,
            appConfig = mockOAuthConfig,
            onMigrationSuccess = onMigrationSuccess,
            onMigrationError = onMigrationError
        )

        // Then
        val capturedIntent = intentSlot.captured
        assertTrue("Intent should contain OAuthConfig extra",
            capturedIntent.hasExtra(TokenMigrationActivity.EXTRA_OAUTH_CONFIG)
        )
    }

    @Test
    fun migrateRefreshToken_registersCorrectCallbacks() {
        // Given
        val mockUserAccount: UserAccount = mockk(relaxed = true)
        every { mockUserAccount.userId } returns "testUserId"
        every { mockUserAccount.orgId } returns "testOrgId"
        every { mockUserAccount.username } returns ""
        every { mockUserAccount.instanceServer } returns ""

        val callbacksSlot = slot<MigrationCallbackRegistry.MigrationCallbacks>()
        every { MigrationCallbackRegistry.register(capture(callbacksSlot)) } returns "callback-key"
        every { mockContext.startActivity(any()) } just Runs

        // When
        mockUserAccountManager.migrateRefreshToken(
            userAccount = mockUserAccount,
            appConfig = mockOAuthConfig,
            onMigrationSuccess = onMigrationSuccess,
            onMigrationError = onMigrationError
        )

        // Then
        val capturedCallbacks = callbacksSlot.captured
        assertNotNull("Callbacks should not be null", capturedCallbacks)

        // Verify success callback is properly wrapped
        val testAccount: UserAccount = mockk(relaxed = true)
        capturedCallbacks.onMigrationSuccess(testAccount)
        verify(exactly = 1) { onMigrationSuccess.invoke(testAccount) }

        // Verify error callback is properly wrapped
        val testError = "Test error"
        val testDesc = "Test description"
        val testThrowable = RuntimeException("Test")
        capturedCallbacks.onMigrationError(testError, testDesc, testThrowable)
        verify(exactly = 1) { onMigrationError.invoke(testError, testDesc, testThrowable) }
    }

    @Test
    fun migrateRefreshToken_usesCurrentUserWhenUserAccountNotProvided() {
        // Given
        val testUserId = "currentUserId"
        val testOrgId = "currentOrgId"

        val mockCurrentUser: UserAccount = mockk(relaxed = true)
        every { mockCurrentUser.userId } returns testUserId
        every { mockCurrentUser.orgId } returns testOrgId

        mockkStatic(UserAccountManager::class)
        every { UserAccountManager.getInstance() } returns mockUserAccountManager
        every { mockUserAccountManager.currentUser } returns mockCurrentUser

        every { MigrationCallbackRegistry.register(any()) } returns "callback-key"

        val intentSlot = slot<Intent>()
        every { mockContext.startActivity(capture(intentSlot)) } just Runs

        // When - call with default userAccount parameter (null -> uses currentUser)
        mockUserAccountManager.migrateRefreshToken(
            appConfig = mockOAuthConfig,
            onMigrationSuccess = onMigrationSuccess,
            onMigrationError = onMigrationError
        )

        // Then
        val capturedIntent = intentSlot.captured
        assertEquals("Intent should have current user's org id",
            testOrgId,
            capturedIntent.getStringExtra(TokenMigrationActivity.EXTRA_ORG_ID)
        )
        assertEquals("Intent should have current user's user id",
            testUserId,
            capturedIntent.getStringExtra(TokenMigrationActivity.EXTRA_USER_ID)
        )
    }
}
