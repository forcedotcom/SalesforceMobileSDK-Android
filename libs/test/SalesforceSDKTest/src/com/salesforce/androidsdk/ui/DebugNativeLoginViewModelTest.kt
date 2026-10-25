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
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salesforce.androidsdk.auth.interfaces.NativeLoginManager
import com.salesforce.androidsdk.auth.interfaces.NativeLoginManager.StartRegistrationResult
import com.salesforce.androidsdk.auth.interfaces.NativeLoginResult
import com.salesforce.androidsdk.auth.interfaces.OtpRequestResult
import com.salesforce.androidsdk.auth.interfaces.OtpVerificationMethod
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Cancellation and replacement of in-flight attempts in the debug Native Login form. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DebugNativeLoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun test_givenLoginInFlight_whenConfigChangesAndCancellationCleanupIsDelayed_thenFormStaysIdle() = runTest(dispatcher) {
        val cleanup = CompletableDeferred<Unit>()
        val clientA = FakeNativeLoginManager {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { cleanup.await() }
            }
        }
        val viewModel = DebugNativeLoginViewModel(SavedStateHandle())
        viewModel.configure(CONFIG_A) { clientA }
        viewModel.login()
        advanceUntilIdle()
        assertEquals(DebugNativeLoginState.Loading, viewModel.state.value)

        viewModel.configure(CONFIG_B) { FakeNativeLoginManager { NativeLoginResult.Success } }
        assertEquals(DebugNativeLoginState.Idle, viewModel.state.value)

        cleanup.complete(Unit)
        advanceUntilIdle()
        assertEquals(DebugNativeLoginState.Idle, viewModel.state.value)
    }

    @Test
    fun test_givenCancelledLogin_whenItCompletesAnyway_thenResultIsNotPublished() = runTest(dispatcher) {
        val response = CompletableDeferred<NativeLoginResult>()
        val viewModel = DebugNativeLoginViewModel(SavedStateHandle())
        viewModel.configure(CONFIG_A) {
            FakeNativeLoginManager { withContext(NonCancellable) { response.await() } }
        }
        viewModel.login()
        advanceUntilIdle()

        viewModel.cancel()
        response.complete(NativeLoginResult.Success)
        advanceUntilIdle()
        assertEquals(DebugNativeLoginState.Idle, viewModel.state.value)
    }

    @Test
    fun test_givenRetryAfterCancel_whenItCompletes_thenOnlyTheRetryIsPublished() = runTest(dispatcher) {
        val firstResponse = CompletableDeferred<NativeLoginResult>()
        val results = ArrayDeque(listOf<suspend () -> NativeLoginResult>(
            { withContext(NonCancellable) { firstResponse.await() } },
            { NativeLoginResult.InvalidCredentials },
        ))
        val viewModel = DebugNativeLoginViewModel(SavedStateHandle())
        viewModel.configure(CONFIG_A) { FakeNativeLoginManager { results.removeFirst()() } }
        viewModel.login()
        advanceUntilIdle()
        viewModel.cancel()

        viewModel.login()
        advanceUntilIdle()
        firstResponse.complete(NativeLoginResult.Success)
        advanceUntilIdle()
        val state = viewModel.state.value
        assertEquals(NativeLoginResult.InvalidCredentials, (state as DebugNativeLoginState.Failed).result)
    }

    private class FakeNativeLoginManager(
        private val onLogin: suspend () -> NativeLoginResult,
    ) : NativeLoginManager {
        override val shouldShowBackButton = false
        override val biometricAuthenticationUsername: String? = null
        override suspend fun login(username: String, password: String) = onLogin()
        override fun getFallbackWebAuthenticationIntent() = Intent()
        override fun presentBiometricAuth(activity: FragmentActivity) = false
        override suspend fun startRegistration(
            email: String,
            firstName: String,
            lastName: String,
            username: String,
            newPassword: String,
            reCaptchaToken: String,
            otpVerificationMethod: OtpVerificationMethod,
        ): StartRegistrationResult = error("Not used by the debug form")
        override suspend fun completeRegistration(
            otp: String,
            requestIdentifier: String,
            otpVerificationMethod: OtpVerificationMethod,
        ): NativeLoginResult = error("Not used by the debug form")
        override suspend fun startPasswordReset(username: String, reCaptchaToken: String): NativeLoginResult =
            error("Not used by the debug form")
        override suspend fun completePasswordReset(username: String, otp: String, newPassword: String): NativeLoginResult =
            error("Not used by the debug form")
        override suspend fun submitOtpRequest(
            username: String,
            reCaptchaToken: String,
            otpVerificationMethod: OtpVerificationMethod,
        ): OtpRequestResult = error("Not used by the debug form")
        override suspend fun submitPasswordlessAuthorizationRequest(
            otp: String,
            otpIdentifier: String,
            otpVerificationMethod: OtpVerificationMethod,
        ): NativeLoginResult = error("Not used by the debug form")
    }

    private companion object {
        val CONFIG_A = NativeLoginDebugConfig(
            consumerKey = "client_a",
            redirectUri = "https://example.my.site.com/a/callback",
            communityUrl = "https://example.my.site.com/a",
        )
        val CONFIG_B = CONFIG_A.copy(consumerKey = "client_b", redirectUri = "https://example.my.site.com/b/callback")
    }
}
