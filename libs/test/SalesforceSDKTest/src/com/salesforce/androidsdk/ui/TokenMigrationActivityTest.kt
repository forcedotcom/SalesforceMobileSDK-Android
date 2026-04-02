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
package com.salesforce.androidsdk.ui

import android.content.Intent
import androidx.lifecycle.Lifecycle.State.DESTROYED
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salesforce.androidsdk.accounts.MigrationCallbackRegistry
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.analytics.AnalyticsPublishingWorker
import com.salesforce.androidsdk.analytics.logger.SalesforceLogger
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.OAuth2.FRONTDOOR_URL_KEY
import com.salesforce.androidsdk.config.OAuthConfig
import com.salesforce.androidsdk.rest.RestClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal const val VALID_ORG = "valid-org"
internal const val VALID_USER = "valid-user"
internal const val INVALID_ORG = "invalid-org"
internal const val INVALID_USER = "invalid-user"

/**
 * Tests for TokenMigrationActivity using ActivityScenario.
 */
@RunWith(AndroidJUnit4::class)
class TokenMigrationActivityTest {

    val mockOAuthConfig = OAuthConfig(
        consumerKey = "test_consumer_key",
        redirectUri = "testapp://oauth/callback",
        scopes = listOf("api", "refresh_token"),
    )

    private lateinit var mockUserAccountManager: UserAccountManager
    private lateinit var mockSdkManager: SalesforceSDKManager
    private lateinit var mockRestClient: RestClient
    private lateinit var mockUser: UserAccount

    @Before
    fun setUp() {
        // Mock SalesforceLogger.getLogger to prevent readLoggerPrefs from being called
        val mockLogger: SalesforceLogger = mockk(relaxed = true)
        mockkStatic(SalesforceLogger::class)
        every { SalesforceLogger.getLogger(any(), any()) } returns mockLogger
        every { SalesforceLogger.getLogger(any(), any(), any()) } returns mockLogger

        // Reset logger prefs as backup
        SalesforceLogger.flushComponents()
        SalesforceLogger.resetLoggerPrefs(getApplicationContext())

        mockUserAccountManager = mockk(relaxed = true)
        mockSdkManager = mockk(relaxed = true)
        mockRestClient = mockk(relaxed = true)
        mockUser = mockk(relaxed = true)

        // Mock user properties needed for getAuthorizationUrl
        every { mockUser.instanceServer } returns "https://test.salesforce.com"

        mockkStatic(UserAccountManager::class)
        every { UserAccountManager.getInstance() } returns mockUserAccountManager
        every {
            mockUserAccountManager.getUserFromOrgAndUserId(VALID_ORG, VALID_USER)
        } returns mockUser
        every {
            mockUserAccountManager.getUserFromOrgAndUserId(INVALID_ORG, INVALID_USER)
        } returns null

        mockkObject(SalesforceSDKManager)
        mockkObject(SalesforceSDKManager.Companion)
        every { SalesforceSDKManager.getInstance() } returns mockSdkManager
        every { mockSdkManager.appContext } returns getApplicationContext()
        every { mockSdkManager.clientManager.peekRestClient(any<UserAccount>()) } returns mockRestClient
        every { mockSdkManager.useHybridAuthentication } returns false
        every { mockSdkManager.userAgent } returns "MockUserAgent"

        // Default mock for sendSync to prevent hanging - tests can override this
        val mockResponse = mockk<com.salesforce.androidsdk.rest.RestResponse>(relaxed = true)
        every { mockResponse.isSuccess } returns true
        every { mockResponse.asString() } returns """{"$FRONTDOOR_URL_KEY": "https://test.salesforce.com/frontdoor"}"""
        every { mockRestClient.sendSync(any()) } returns mockResponse

        // Mock AnalyticsPublishingWorker to prevent NPE during activity lifecycle
        mockkObject(AnalyticsPublishingWorker.Companion)
        every { AnalyticsPublishingWorker.enqueueAnalyticsPublishWorkRequest(any(), any()) } returns null
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun onCreate_withMissingCallbackId_finishesActivity() {
        // Given - Intent without callback ID
        val intent = Intent(getApplicationContext(), TokenMigrationActivity::class.java)
        // No EXTRA_CALLBACK_ID set

        // When
        launch<TokenMigrationActivity>(intent).use { scenario ->
            // Then - Activity should finish immediately
            assertEquals(
                "Activity should be destroyed when callback ID is missing",
                DESTROYED,
                scenario.state,
            )
        }
    }

    @Test
    fun onCreate_withInvalidCallbackId_finishesActivity() {
        // Given - Intent with callback ID that's not in the registry
        val intent = Intent(getApplicationContext(), TokenMigrationActivity::class.java).apply {
            putExtra(TokenMigrationActivity.EXTRA_CALLBACK_ID, "invalid-callback-id")
        }

        // When
        launch<TokenMigrationActivity>(intent).use { scenario ->
            // Then - Activity should finish because callback is not in registry
            assertEquals(
                "Activity should be destroyed when callback ID is invalid",
                DESTROYED,
                scenario.state,
            )
        }
    }

    @Test
    fun onCreate_withMissingOAuthConfig_callsErrorCallbackAndFinishes() {
        // Given
        var errorCalled = false
        var errorMessage: String? = null
        val latch = CountDownLatch(1)

        val callbackKey = MigrationCallbackRegistry.register(
            MigrationCallbackRegistry.MigrationCallbacks(
                onMigrationSuccess = { },
                onMigrationError = { error, _, _ ->
                    errorCalled = true
                    errorMessage = error
                    latch.countDown()
                },
            )
        )

        val intent = Intent(getApplicationContext(), TokenMigrationActivity::class.java).apply {
            putExtra(TokenMigrationActivity.EXTRA_CALLBACK_ID, callbackKey)
            // No EXTRA_OAUTH_CONFIG set
        }

        // When
        launch<TokenMigrationActivity>(intent).use { scenario ->
            // Then
            latch.await(500, TimeUnit.MILLISECONDS)
            assertTrue("Error callback should be called", errorCalled)
            assertEquals(ERROR_PARSE_OAUTH_CONFIG, errorMessage)
            assertEquals(DESTROYED, scenario.state)
        }
    }

    @Test
    fun onCreate_withMissingOrgId_callsErrorCallbackAndFinishes() {
        // Given
        var errorCalled = false
        val latch = CountDownLatch(1)

        val callbackKey = MigrationCallbackRegistry.register(
            MigrationCallbackRegistry.MigrationCallbacks(
                onMigrationSuccess = { },
                onMigrationError = { _, _, _ ->
                    errorCalled = true
                    latch.countDown()
                },
            )
        )

        val intent = Intent(getApplicationContext(), TokenMigrationActivity::class.java).apply {
            putExtra(TokenMigrationActivity.EXTRA_CALLBACK_ID, callbackKey)
            putExtra(TokenMigrationActivity.EXTRA_OAUTH_CONFIG, mockOAuthConfig)
            putExtra(TokenMigrationActivity.EXTRA_USER_ID, VALID_USER)
            // No EXTRA_ORG_ID set
        }

        // When
        launch<TokenMigrationActivity>(intent).use { scenario ->
            // Then
            latch.await(500, TimeUnit.MILLISECONDS)
            assertTrue("Error callback should be called", errorCalled)
            assertEquals(DESTROYED, scenario.state)
        }
    }

    @Test
    fun onCreate_withMissingUserId_callsErrorCallbackAndFinishes() {
        // Given
        var errorCalled = false
        val latch = CountDownLatch(1)

        val callbackKey = MigrationCallbackRegistry.register(
            MigrationCallbackRegistry.MigrationCallbacks(
                onMigrationSuccess = { },
                onMigrationError = { _, _, _ ->
                    errorCalled = true
                    latch.countDown()
                },
            )
        )

        val intent = Intent(getApplicationContext(), TokenMigrationActivity::class.java).apply {
            putExtra(TokenMigrationActivity.EXTRA_CALLBACK_ID, callbackKey)
            putExtra(TokenMigrationActivity.EXTRA_OAUTH_CONFIG, mockOAuthConfig)
            putExtra(TokenMigrationActivity.EXTRA_ORG_ID, VALID_ORG)
            // No EXTRA_USER_ID set
        }

        // When
        launch<TokenMigrationActivity>(intent).use { scenario ->
            // Then
            latch.await(500, TimeUnit.MILLISECONDS)
            assertTrue("Error callback should be called", errorCalled)
            assertEquals(DESTROYED, scenario.state)
        }
    }

    @Test
    fun onCreate_withUserNotFound_callsErrorCallbackAndFinishes() {
        // Given
        var errorCalled = false
        var errorMessage: String? = null
        val latch = CountDownLatch(1)

        val callbackKey = MigrationCallbackRegistry.register(
            MigrationCallbackRegistry.MigrationCallbacks(
                onMigrationSuccess = { },
                onMigrationError = { error, _, _ ->
                    errorCalled = true
                    errorMessage = error
                    latch.countDown()
                },
            )
        )

        val intent = Intent(getApplicationContext(), TokenMigrationActivity::class.java).apply {
            putExtra(TokenMigrationActivity.EXTRA_CALLBACK_ID, callbackKey)
            putExtra(TokenMigrationActivity.EXTRA_OAUTH_CONFIG, mockOAuthConfig)
            putExtra(TokenMigrationActivity.EXTRA_ORG_ID, INVALID_ORG)
            putExtra(TokenMigrationActivity.EXTRA_USER_ID, INVALID_USER)
        }

        // When
        launch<TokenMigrationActivity>(intent).use { scenario ->
            // Then
            latch.await(500, TimeUnit.MILLISECONDS)
            assertTrue("Error callback should be called", errorCalled)
            assertEquals(ERROR_BUILD_USER_ACCOUNT, errorMessage)
            assertEquals(DESTROYED, scenario.state)
        }
    }

    @Test
    fun onCreate_withClientException_callsErrorCallbackAndFinishes() {
        // Given
        var errorCalled = false
        var errorMessage: String? = null
        val latch = CountDownLatch(1)

        val callbackKey = MigrationCallbackRegistry.register(
            MigrationCallbackRegistry.MigrationCallbacks(
                onMigrationSuccess = { },
                onMigrationError = { error, _, _ ->
                    errorCalled = true
                    errorMessage = error
                    latch.countDown()
                },
            )
        )

        val intent = Intent(getApplicationContext(), TokenMigrationActivity::class.java).apply {
            putExtra(TokenMigrationActivity.EXTRA_CALLBACK_ID, callbackKey)
            putExtra(TokenMigrationActivity.EXTRA_OAUTH_CONFIG, mockOAuthConfig)
            putExtra(TokenMigrationActivity.EXTRA_ORG_ID, VALID_ORG)
            putExtra(TokenMigrationActivity.EXTRA_USER_ID, VALID_USER)
        }

        // Throw exception when getting client
        every {
            mockSdkManager.clientManager.peekRestClient(any<UserAccount>())
        } throws RuntimeException("Account not found")

        // When
        launch<TokenMigrationActivity>(intent).use { scenario ->
            // Then
            latch.await(500, TimeUnit.MILLISECONDS)
            assertTrue("Error callback should be called", errorCalled)
            assertEquals(ERROR_BUILD_REST_CLIENT, errorMessage)
            assertEquals(DESTROYED, scenario.state)
        }
    }

    @Test
    fun onCreate_withNullFrontDoorUrl_callsErrorCallbackAndFinishes() {
        // Given
        var errorCalled = false
        var errorMessage: String? = null
        var errorDesc: String? = null
        val latch = CountDownLatch(1)

        val callbackKey = MigrationCallbackRegistry.register(
            MigrationCallbackRegistry.MigrationCallbacks(
                onMigrationSuccess = { },
                onMigrationError = { error, desc, _ ->
                    errorCalled = true
                    errorMessage = error
                    errorDesc = desc
                    latch.countDown()
                },
            )
        )

        val intent = Intent(getApplicationContext(), TokenMigrationActivity::class.java).apply {
            putExtra(TokenMigrationActivity.EXTRA_CALLBACK_ID, callbackKey)
            putExtra(TokenMigrationActivity.EXTRA_OAUTH_CONFIG, mockOAuthConfig)
            putExtra(TokenMigrationActivity.EXTRA_ORG_ID, VALID_ORG)
            putExtra(TokenMigrationActivity.EXTRA_USER_ID, VALID_USER)
        }

        // Mock response without frontdoor_uri key
        every { mockRestClient.sendSync(any()) } returns mockk(relaxed = true) {
            every { isSuccess } returns true
            every { asString() } returns "{}"
        }

        // Mock loginViewModelFactory
        every { mockSdkManager.loginViewModelFactory } returns object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return mockk<LoginViewModel>(relaxed = true) as T
            }
        }

        // When
        launch<TokenMigrationActivity>(intent).use { scenario ->
            // Then
            latch.await(500, TimeUnit.MILLISECONDS)
            assertTrue("Error callback should be called", errorCalled)
            assertEquals(ERROR_SINGLE_ACCESS_FAILED, errorMessage)
            assertEquals(ERROR_TOKEN_INVALID_DESC, errorDesc)
            assertEquals(DESTROYED, scenario.state)
        }
    }
}
