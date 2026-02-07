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
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.accounts.MigrationCallbackRegistry
import com.salesforce.androidsdk.config.OAuthConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Tests for TokenMigrationActivity using ActivityScenario.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class TokenMigrationActivityTest {

    // region onCreate Error Handling Tests

    @Test
    fun onCreate_withMissingCallbackId_finishesActivity() {
        // Given - Intent without callback ID
        val intent = Intent(getApplicationContext(), TokenMigrationActivity::class.java)
        // No EXTRA_CALLBACK_ID set

        // When
        val scenario = launch<TokenMigrationActivity>(intent)

        // Then - Activity should finish immediately
        assertEquals(
            "Activity should be destroyed when callback ID is missing",
            DESTROYED,
            scenario.state,
        )
    }

    @Test
    fun onCreate_withInvalidCallbackId_finishesActivity() {
        // Given - Intent with callback ID that's not in the registry
        val intent = Intent(getApplicationContext(), TokenMigrationActivity::class.java).apply {
            putExtra(TokenMigrationActivity.EXTRA_CALLBACK_ID, "invalid-callback-id")
        }

        // When
        val scenario = launch<TokenMigrationActivity>(intent)

        // Then - Activity should finish because callback is not in registry
        assertEquals(
            "Activity should be destroyed when callback ID is invalid",
            DESTROYED,
            scenario.state,
        )
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
        val scenario = launch<TokenMigrationActivity>(intent)

        // Then
        latch.await(2, TimeUnit.SECONDS)
        assertTrue("Error callback should be called", errorCalled)
        assertEquals("Unable to parse OAuthConfig.", errorMessage)
        assertEquals(DESTROYED, scenario.state)
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

        val mockOAuthConfig = OAuthConfig(
            consumerKey = "test_consumer_key",
            redirectUri = "testapp://oauth/callback",
            scopes = listOf("api", "refresh_token"),
        )

        val intent = Intent(getApplicationContext(), TokenMigrationActivity::class.java).apply {
            putExtra(TokenMigrationActivity.EXTRA_CALLBACK_ID, callbackKey)
            putExtra(TokenMigrationActivity.EXTRA_OAUTH_CONFIG, mockOAuthConfig)
            putExtra(TokenMigrationActivity.EXTRA_USER_ID, "testUserId")
            // No EXTRA_ORG_ID set
        }

        // When
        val scenario = launch<TokenMigrationActivity>(intent)

        // Then
        latch.await(2, TimeUnit.SECONDS)
        assertTrue("Error callback should be called", errorCalled)
        assertEquals(DESTROYED, scenario.state)
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

        val mockOAuthConfig = OAuthConfig(
            consumerKey = "test_consumer_key",
            redirectUri = "testapp://oauth/callback",
            scopes = listOf("api", "refresh_token")
        )

        val intent = Intent(getApplicationContext(), TokenMigrationActivity::class.java).apply {
            putExtra(TokenMigrationActivity.EXTRA_CALLBACK_ID, callbackKey)
            putExtra(TokenMigrationActivity.EXTRA_OAUTH_CONFIG, mockOAuthConfig)
            putExtra(TokenMigrationActivity.EXTRA_ORG_ID, "testOrgId")
            // No EXTRA_USER_ID set
        }

        // When
        val scenario = launch<TokenMigrationActivity>(intent)

        // Then
        latch.await(2, TimeUnit.SECONDS)
        assertTrue("Error callback should be called", errorCalled)
        assertEquals(DESTROYED, scenario.state)
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
                }
            )
        )

        val mockOAuthConfig = OAuthConfig(
            consumerKey = "test_consumer_key",
            redirectUri = "testapp://oauth/callback",
            scopes = listOf("api", "refresh_token")
        )

        val intent = Intent(getApplicationContext(), TokenMigrationActivity::class.java).apply {
            putExtra(TokenMigrationActivity.EXTRA_CALLBACK_ID, callbackKey)
            putExtra(TokenMigrationActivity.EXTRA_OAUTH_CONFIG, mockOAuthConfig)
            putExtra(TokenMigrationActivity.EXTRA_ORG_ID, "nonexistent-org")
            putExtra(TokenMigrationActivity.EXTRA_USER_ID, "nonexistent-user")
        }

        // When
        val scenario = launch<TokenMigrationActivity>(intent)

        // Then
        latch.await(2, TimeUnit.SECONDS)
        assertTrue("Error callback should be called", errorCalled)
        assertEquals("Unable to build user account.", errorMessage)
        assertEquals(DESTROYED, scenario.state)
    }

    // endregion
}
