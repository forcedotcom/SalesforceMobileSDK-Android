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
package com.salesforce.androidsdk.ui

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.view.KeyEvent
import androidx.activity.result.ActivityResult
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.security.BiometricAuthenticationManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginActivityCancellationTest {

    private lateinit var sdkManager: SalesforceSDKManager
    private lateinit var biometricManager: BiometricAuthenticationManager

    @Before
    fun setUp() {
        sdkManager = mockk(relaxed = true)
        biometricManager = mockk(relaxed = true)
        mockkObject(SalesforceSDKManager)
        every { SalesforceSDKManager.getInstance() } returns sdkManager
        every { sdkManager.appContext } returns
                InstrumentationRegistry.getInstrumentation().targetContext
        every { sdkManager.nativeLoginActivity } returns null
        every { sdkManager.biometricAuthenticationManager } returns biometricManager
        every { biometricManager.locked } returns false
        every { sdkManager.userAccountManager.authenticatedUsers } returns listOf(mockk())
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun backWithExistingAccount_notifiesBeforeFinishing() = onMain {
        val activity = recordingActivity()
        activity.handleBackBehavior()
        assertEquals(listOf("cancel", "finish"), activity.events)
    }

    @Test
    fun legacyBackKey_notifiesBeforeFinishing() = onMain {
        val activity = recordingActivity()
        activity.onKeyDown(KeyEvent.KEYCODE_BACK, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        assertEquals(listOf("cancel", "finish"), activity.events)
    }

    @Test
    fun backFromNativeLoginFallback_notifiesBeforeFinishing() = onMain {
        every { sdkManager.nativeLoginActivity } returns LoginActivity::class.java
        val activity = recordingActivity()
        activity.handleBackBehavior()
        assertEquals(listOf("cancel", "finish"), activity.events)
    }

    @Test
    fun backWithoutAccounts_onlyBackgroundsTask() = onMain {
        every { sdkManager.userAccountManager.authenticatedUsers } returns null
        val activity = recordingActivity()
        activity.handleBackBehavior()
        assertEquals(listOf("background"), activity.events)
    }

    @Test
    fun backWhenBiometricLocked_doesNotNotify() = onMain {
        every { biometricManager.locked } returns true
        val activity = recordingActivity()
        activity.handleBackBehavior()
        assertEquals(emptyList<String>(), activity.events)
    }

    @Test
    fun singleServerBrowserDismissal_notifiesBeforeFinishing() = onMain {
        val activity = recordingActivity()
        val model = activity.viewModel
        every { model.singleServerCustomTabActivity } returns true
        activity.CustomTabActivityResult(activity).onActivityResult(cancelledResult)
        activity.resolvePendingCustomTabCancellation()
        assertEquals(listOf("cancel", "finish"), activity.events)
    }

    @Test
    fun browserDismissalReturningToPicker_notifiesBeforeClearing() = onMain {
        val activity = recordingActivity()
        activity.CustomTabActivityResult(activity).onActivityResult(cancelledResult)
        activity.resolvePendingCustomTabCancellation()
        assertEquals(listOf("cancel", "clear:true"), activity.events)
    }

    @Test
    fun sharedBrowserDismissal_preservesPickerBehavior() = onMain {
        every { sdkManager.isShareBrowserSessionEnabled } returns true
        val activity = recordingActivity()
        activity.CustomTabActivityResult(activity).onActivityResult(cancelledResult)
        activity.resolvePendingCustomTabCancellation()
        assertEquals(listOf("cancel", "clear:false"), activity.events)
    }

    @Test
    fun browserSuccessfulResult_doesNotNotify() = onMain {
        val activity = recordingActivity()
        activity.CustomTabActivityResult(activity).onActivityResult(ActivityResult(RESULT_OK, null))
        assertEquals(emptyList<String>(), activity.events)
    }

    @Test
    fun browserResultAfterOAuthError_doesNotNotifyOrClearLogin() = onMain {
        val activity = recordingActivity()
        deliverOAuthRedirect(activity, "error=access_denied")
        activity.CustomTabActivityResult(activity).onActivityResult(cancelledResult)
        assertEquals(listOf("error"), activity.events)
    }

    @Test
    fun browserResultDuringCodeExchange_doesNotNotifyOrFinish() = onMain {
        val activity = recordingActivity()
        val model = activity.viewModel
        every { model.singleServerCustomTabActivity } returns true
        deliverOAuthRedirect(activity, "code=test_code")
        activity.CustomTabActivityResult(activity).onActivityResult(cancelledResult)
        assertEquals(emptyList<String>(), activity.events)
    }

    @Test
    fun browserResultBeforeOAuthRedirect_doesNotNotify() = onMain {
        val activity = recordingActivity()
        activity.CustomTabActivityResult(activity).onActivityResult(cancelledResult)
        deliverOAuthRedirect(activity, "error=access_denied")
        activity.resolvePendingCustomTabCancellation()
        assertEquals(listOf("error"), activity.events)
    }

    @Test
    fun browserResultBeforeSuccessfulOAuthRedirect_doesNotNotify() = onMain {
        val activity = recordingActivity()
        activity.CustomTabActivityResult(activity).onActivityResult(cancelledResult)
        deliverOAuthRedirect(activity, "code=test_code")
        activity.resolvePendingCustomTabCancellation()
        assertEquals(emptyList<String>(), activity.events)
    }

    @Test
    fun duplicateBrowserResult_notifiesOnlyOnce() = onMain {
        val activity = recordingActivity()
        val callback = activity.CustomTabActivityResult(activity)
        callback.onActivityResult(cancelledResult)
        callback.onActivityResult(cancelledResult)
        activity.resolvePendingCustomTabCancellation()
        activity.resolvePendingCustomTabCancellation()
        assertEquals(listOf("cancel", "clear:true"), activity.events)
    }

    @Test
    fun browserResultAfterActivityFinished_doesNotNotify() = onMain {
        val activity = recordingActivity()
        every { activity.isFinishing } returns true
        activity.CustomTabActivityResult(activity).onActivityResult(cancelledResult)
        assertEquals(emptyList<String>(), activity.events)
    }

    @Test
    fun browserResultAfterActivityDestroyed_doesNotNotify() = onMain {
        val activity = recordingActivity()
        every { activity.isDestroyed } returns true
        activity.CustomTabActivityResult(activity).onActivityResult(cancelledResult)
        assertEquals(emptyList<String>(), activity.events)
    }

    @Test
    fun backDuringCompletedWebViewAuthentication_doesNotNotify() = onMain {
        val activity = recordingActivity()
        activity.viewModel.authFinished.value = true
        activity.handleBackBehavior()
        assertEquals(listOf("finish"), activity.events)
    }

    @Test
    fun successfulAuthenticationFinish_doesNotNotify() = onMain {
        val activity = recordingActivity()
        activity.onAuthFlowFinished { activity.events += "proceed" }
        assertEquals(listOf("proceed", "finish"), activity.events)
    }

    @Test
    fun tokenRefreshFinish_doesNotNotify() = onMain {
        every { sdkManager.clientManager?.peekRestClient() } returns null
        val activity = recordingActivity()
        LoginActivity::class.java.getDeclaredMethod("doTokenRefresh", LoginActivity::class.java)
            .apply { isAccessible = true }
            .invoke(activity, activity)
        assertEquals(listOf("finish"), activity.events)
    }

    private fun recordingActivity(): RecordingLoginActivity {
        val original = RecordingLoginActivity()
        val model = original.viewModel
        return spyk(original).also { activity ->
            every { activity.viewModel } returns model
            every { activity.clearWebView(any()) } answers {
                activity.events += "clear:${firstArg<Boolean>()}"
            }
        }
    }

    private fun deliverOAuthRedirect(activity: LoginActivity, query: String) =
        LoginActivity::class.java.getDeclaredMethod("completeAdvAuthFlow", Intent::class.java)
            .apply { isAccessible = true }
            .invoke(activity, Intent().setData("test://callback?$query".toUri()))

    private fun onMain(block: () -> Unit) =
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private val cancelledResult = ActivityResult(RESULT_CANCELED, null)

    private class RecordingLoginActivity : LoginActivity() {
        val events = mutableListOf<String>()
        override val viewModel = mockk<LoginViewModel>(relaxed = true).apply {
            every { authFinished } returns mutableStateOf(false)
            every { singleServerCustomTabActivity } returns false
        }

        override fun onAuthFlowCancelled() {
            events += "cancel"
        }

        override fun finish() {
            events += "finish"
        }

        override fun moveTaskToBack(nonRoot: Boolean): Boolean {
            events += "background"
            return true
        }

        override fun onAuthFlowError(error: String, errorDesc: String?, e: Throwable?) {
            events += "error"
        }
    }
}
