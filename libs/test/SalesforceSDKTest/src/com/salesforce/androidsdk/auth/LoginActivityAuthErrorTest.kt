/*
 * Copyright (c) 2025-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.auth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.auth.OAuth2.CLIENT_BLOCKED_ERROR
import com.salesforce.androidsdk.auth.OAuth2.OAuthFailedException
import com.salesforce.androidsdk.ui.LoginActivity
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Test subclass that exposes the protected onAuthFlowError for testing.
 */
class TestLoginActivity : LoginActivity() {
    public override fun onAuthFlowError(error: String, errorDesc: String?, e: Throwable?) {
        super.onAuthFlowError(error, errorDesc, e)
    }

    override fun onAuthFlowSuccess(userAccount: UserAccount) {
        // No-op for tests
    }
}

@RunWith(AndroidJUnit4::class)
class LoginActivityAuthErrorTest {

    private companion object {
        const val AUTHENTICATION_FAILED_INTENT = "com.salesforce.auth.intent.AUTHENTICATION_ERROR"
        const val HTTP_ERROR_RESPONSE_CODE_INTENT = "com.salesforce.auth.intent.HTTP_RESPONSE_CODE"
        const val RESPONSE_ERROR_INTENT = "com.salesforce.auth.intent.RESPONSE_ERROR"
        const val RESPONSE_ERROR_DESCRIPTION_INTENT = "com.salesforce.auth.intent.RESPONSE_ERROR_DESCRIPTION"
    }

    @Before
    fun setup() {
        OAuth2.TIMESTAMP_FORMAT
        mockkStatic(OAuth2::class)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun onAuthFlowError_givenClientBlocked_broadcastsWithCorrectExtras() {
        val tokenErrorResponse = mockk<OAuth2.TokenErrorResponse>(relaxed = true)
        every { tokenErrorResponse.error } returns CLIENT_BLOCKED_ERROR
        every { tokenErrorResponse.errorDescription } returns "App is blocked by admin"
        val oauthException = OAuthFailedException(tokenErrorResponse, 403)

        every {
            OAuth2.exchangeCode(any(), any(), any(), any(), any(), any())
        } throws oauthException

        val latch = CountDownLatch(1)
        var receivedIntent: Intent? = null

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                receivedIntent = intent
                latch.countDown()
            }
        }

        val context: Context = getApplicationContext()
        context.registerReceiver(
            receiver,
            IntentFilter(AUTHENTICATION_FAILED_INTENT),
            Context.RECEIVER_EXPORTED
        )

        try {
            launch<TestLoginActivity>(
                Intent(context, TestLoginActivity::class.java)
            ).use { activityScenario ->
                activityScenario.onActivity { activity ->
                    activity.viewModel.onWebServerFlowComplete(
                        "test_code",
                        { error, errorDesc, e -> activity.onAuthFlowError(error, errorDesc, e) },
                        { },
                    )
                }

                assertTrue("Broadcast should be received within 5 seconds", latch.await(5, TimeUnit.SECONDS))
                assertNotNull(receivedIntent)
                assertEquals(403, receivedIntent!!.getIntExtra(HTTP_ERROR_RESPONSE_CODE_INTENT, 0))
                assertEquals(CLIENT_BLOCKED_ERROR, receivedIntent!!.getStringExtra(RESPONSE_ERROR_INTENT))
                assertEquals("App is blocked by admin", receivedIntent!!.getStringExtra(RESPONSE_ERROR_DESCRIPTION_INTENT))
            }
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    @Test
    fun onAuthFlowError_givenClientBlocked_showsAppBlockedToast() {
        val tokenErrorResponse = mockk<OAuth2.TokenErrorResponse>(relaxed = true)
        every { tokenErrorResponse.error } returns CLIENT_BLOCKED_ERROR
        every { tokenErrorResponse.errorDescription } returns "App is blocked"
        val oauthException = OAuthFailedException(tokenErrorResponse, 403)

        every {
            OAuth2.exchangeCode(any(), any(), any(), any(), any(), any())
        } throws oauthException

        launch<TestLoginActivity>(
            Intent(getApplicationContext(), TestLoginActivity::class.java)
        ).use { activityScenario ->
            activityScenario.onActivity { activity ->
                activity.viewModel.onWebServerFlowComplete(
                    "test_code",
                    { error, errorDesc, e -> activity.onAuthFlowError(error, errorDesc, e) },
                    { },
                )
            }

            // Allow time for the coroutine + runOnUiThread to complete
            Thread.sleep(500)

            activityScenario.onActivity { activity ->
                val expectedMessage = activity.getString(
                    com.salesforce.androidsdk.R.string.sf__app_blocked_error
                )
                assertEquals(
                    "This app has been blocked. Contact your administrator for assistance.",
                    expectedMessage
                )
            }
        }
    }

    @Test
    fun onAuthFlowError_givenGenericOAuthError_broadcastsWithCorrectExtras() {
        val tokenErrorResponse = mockk<OAuth2.TokenErrorResponse>(relaxed = true)
        every { tokenErrorResponse.error } returns "invalid_grant"
        every { tokenErrorResponse.errorDescription } returns "Expired authorization code"
        val oauthException = OAuthFailedException(tokenErrorResponse, 400)

        every {
            OAuth2.exchangeCode(any(), any(), any(), any(), any(), any())
        } throws oauthException

        val latch = CountDownLatch(1)
        var receivedIntent: Intent? = null

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                receivedIntent = intent
                latch.countDown()
            }
        }

        val context: Context = getApplicationContext()
        context.registerReceiver(
            receiver,
            IntentFilter(AUTHENTICATION_FAILED_INTENT),
            Context.RECEIVER_EXPORTED
        )

        try {
            launch<TestLoginActivity>(
                Intent(context, TestLoginActivity::class.java)
            ).use { activityScenario ->
                activityScenario.onActivity { activity ->
                    activity.viewModel.onWebServerFlowComplete(
                        "test_code",
                        { error, errorDesc, e -> activity.onAuthFlowError(error, errorDesc, e) },
                        { },
                    )
                }

                assertTrue("Broadcast should be received within 5 seconds", latch.await(5, TimeUnit.SECONDS))
                assertNotNull(receivedIntent)
                assertEquals(400, receivedIntent!!.getIntExtra(HTTP_ERROR_RESPONSE_CODE_INTENT, 0))
                assertEquals("invalid_grant", receivedIntent!!.getStringExtra(RESPONSE_ERROR_INTENT))
                assertEquals("Expired authorization code", receivedIntent!!.getStringExtra(RESPONSE_ERROR_DESCRIPTION_INTENT))
            }
        } finally {
            context.unregisterReceiver(receiver)
        }
    }
}
