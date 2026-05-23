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

import android.content.Context.ACCESSIBILITY_SERVICE
import android.view.accessibility.AccessibilityManager
import androidx.biometric.BiometricPrompt
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salesforce.androidsdk.R.string.sf__screen_lock_auth_failed
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [ScreenLockActivity.BiometricAuthenticationCallback].
 *
 * Extracted from [ScreenLockActivityScenarioTest] because each test mocks the outer
 * [ScreenLockActivity] via `mockk<ScreenLockActivity>(relaxed = true)`. mockk's Android
 * dispatcher bytecode-instruments the entire class hierarchy of any mocked class for the
 * JVM process lifetime — including [android.view.ContextThemeWrapper], whose `getResources`
 * is called thousands of times per Compose frame. Once that happens, every Compose-rendering
 * test in the same process slows from ~2s to ~27s. Keeping these mockk-of-Activity tests in
 * a separate class lets the test runner schedule them in their own shard, isolated from the
 * Compose-rendering tests in [ScreenLockActivityScenarioTest].
 */
@RunWith(AndroidJUnit4::class)
class BiometricAuthenticationCallbackTest {

    @Test
    fun biometricAuthenticationCallback_onAuthenticationError_callsOnAuthError() {

        val accessibilityManager = mockk<AccessibilityManager>(relaxed = true)
        val activity = mockk<ScreenLockActivity>(relaxed = true)
        every { activity.getSystemService(ACCESSIBILITY_SERVICE) } returns accessibilityManager
        val biometricAuthenticationCallback = activity.BiometricAuthenticationCallback()

        val expectedErrorString = "Expected Error String"

        biometricAuthenticationCallback.onAuthenticationError(0, expectedErrorString)

        verify(exactly = 1) { activity.onAuthError(accessibilityManager = any(), errString = expectedErrorString) }
    }

    @Test
    fun biometricAuthenticationCallback_onAuthenticationSucceeded_callsFinishSuccess() {

        val accessibilityManager = mockk<AccessibilityManager>(relaxed = true)
        val activity = mockk<ScreenLockActivity>(relaxed = true)
        every { activity.getSystemService(ACCESSIBILITY_SERVICE) } returns accessibilityManager
        val biometricAuthenticationCallback = activity.BiometricAuthenticationCallback()
        val biometricResult = mockk<BiometricPrompt.AuthenticationResult>(relaxed = true)

        biometricAuthenticationCallback.onAuthenticationSucceeded(biometricResult)

        verify(exactly = 1) {
            activity.finishSuccess(
                accessibilityManager = any(),
                screenLockManager = any(),
                sdkConfiguration = any(),
            )
        }
    }

    @Test
    fun biometricAuthenticationCallback_onAuthenticationFailed_callsActivityMethods() {

        val accessibilityManager = mockk<AccessibilityManager>(relaxed = true)
        val activity = mockk<ScreenLockActivity>(relaxed = true)
        every { activity.getSystemService(ACCESSIBILITY_SERVICE) } returns accessibilityManager
        val sendAccessibilityCapturingSlot = slot<String>()
        val setErrorMessageCapturingSlot = slot<String>()
        val biometricAuthenticationCallback = activity.BiometricAuthenticationCallback()

        biometricAuthenticationCallback.onAuthenticationFailed()

        verify(exactly = 1) { activity.setErrorMessage(capture(setErrorMessageCapturingSlot)) }
        assertEquals(activity.getString(sf__screen_lock_auth_failed), setErrorMessageCapturingSlot.captured)
        verify(exactly = 1) {
            activity.sendAccessibilityEvent(
                accessibilityManager = any(),
                eventText = capture(sendAccessibilityCapturingSlot),
                sdkConfiguration = any(),
            )
        }
        assertTrue(sendAccessibilityCapturingSlot.captured.contains(activity.getString(sf__screen_lock_auth_failed)))
    }
}
