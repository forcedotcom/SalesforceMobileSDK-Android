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

import android.R.attr.windowLightStatusBar
import android.app.admin.DevicePolicyManager.ACTION_SET_NEW_PASSWORD
import android.content.Context
import android.content.Context.ACCESSIBILITY_SERVICE
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.PackageManager.FEATURE_FACE
import android.content.pm.PackageManager.FEATURE_IRIS
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.provider.Settings.ACTION_BIOMETRIC_ENROLL
import android.provider.Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED
import android.view.WindowManager.LayoutParams.FLAG_SECURE
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
import android.view.accessibility.AccessibilityManager
import androidx.activity.result.ActivityResultLauncher
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED
import androidx.biometric.BiometricManager.BIOMETRIC_STATUS_UNKNOWN
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat.getString
import androidx.core.content.res.ResourcesCompat.getDrawable
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salesforce.androidsdk.R.drawable.sf__salesforce_logo
import com.salesforce.androidsdk.R.string.sf__screen_lock_auth_error
import com.salesforce.androidsdk.R.string.sf__screen_lock_auth_failed
import com.salesforce.androidsdk.R.string.sf__screen_lock_auth_success
import com.salesforce.androidsdk.R.string.sf__screen_lock_error
import com.salesforce.androidsdk.R.string.sf__screen_lock_error_hw_unavailable
import com.salesforce.androidsdk.R.string.sf__screen_lock_retry_button
import com.salesforce.androidsdk.R.string.sf__screen_lock_setup_button
import com.salesforce.androidsdk.R.string.sf__screen_lock_setup_required
import com.salesforce.androidsdk.R.string.sf__screen_lock_subtitle
import com.salesforce.androidsdk.R.string.sf__screen_lock_title
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.OAuth2.LogoutReason.USER_LOGOUT
import com.salesforce.androidsdk.security.ScreenLockManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenLockActivityScenarioTest {

    @Test
    fun screenLockActivity_appliesDefaults_whenCreated() {
        launch<ScreenLockActivity>(
            Intent(
                getApplicationContext(),
                ScreenLockActivity::class.java,
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->

                val sdkManager = mockk<SalesforceSDKManager>(relaxed = true)
                every { sdkManager.isDarkTheme } returns false

                activity.create(sdkManager = sdkManager)

                noOp() // This is a no-op and called only for coverage.

                assertTrue(activity.window.attributes.flags and FLAG_SECURE != 0)

                val attrs = intArrayOf(windowLightStatusBar)
                val a = activity.theme.obtainStyledAttributes(attrs)
                try {
                    val windowLightStatusBar = a.getBoolean(0, false)
                    assertTrue(windowLightStatusBar)
                } finally {
                    a.recycle()
                }
            }
        }
    }

    @Test
    fun screenLockActivity_appliesDefaults_whenCreated_api32Minus() {
        launch<ScreenLockActivity>(
            Intent(
                getApplicationContext(),
                ScreenLockActivity::class.java,
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->

                val sdkManager = mockk<SalesforceSDKManager>(relaxed = true)
                every { sdkManager.isDarkTheme } returns false

                activity.create(sdkConfiguration = AndroidSdkConfigurationS, sdkManager = sdkManager)

                assertTrue(activity.window.attributes.flags and FLAG_SECURE != 0)

                val attrs = intArrayOf(windowLightStatusBar)
                val a = activity.theme.obtainStyledAttributes(attrs)
                try {
                    val windowLightStatusBar = a.getBoolean(0, false)
                    assertTrue(windowLightStatusBar)
                } finally {
                    a.recycle()
                }
            }
        }
    }

    @Test
    fun screenLockActivity_appliesCustomizations_whenCreatedWithCustomizations() {
        launch<ScreenLockActivity>(
            Intent(
                getApplicationContext(),
                ScreenLockActivity::class.java,
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->

                val sdkManager = mockk<SalesforceSDKManager>(relaxed = true)
                every { sdkManager.isDarkTheme } returns true

                activity.create(sdkManager = sdkManager)

                val attrs = intArrayOf(windowLightStatusBar)
                val a = activity.theme.obtainStyledAttributes(attrs)
                try {
                    val windowLightStatusBar = a.getBoolean(0, true)
                    assertFalse(windowLightStatusBar)
                } finally {
                    a.recycle()
                }
            }
        }
    }

    @Test
    fun screenLockActivity_getsAppIcon_whenApplicationIconIsNonNull() {
        launch<ScreenLockActivity>(
            Intent(
                getApplicationContext(),
                ScreenLockActivity::class.java,
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->

                val appIcon = activity.getAppIcon()

                val expected = activity.packageManager.getApplicationIcon(activity.applicationInfo.packageName)
                requireNotNull(appIcon)

                val expectedBitmap = expected.renderToBitmap()
                val actualBitmap = appIcon.renderToBitmap()

                assertTrue(expectedBitmap.sameAs(actualBitmap))
            }
        }
    }

    @Test
    fun screenLockActivity_getsDefaultAppIcon_whenApplicationIconIsNull() {
        launch<ScreenLockActivity>(
            Intent(
                getApplicationContext(),
                ScreenLockActivity::class.java,
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->

                val packageManager = mockk<PackageManager>(relaxed = true)
                every { packageManager.getApplicationIcon(any<String>()) } throws NameNotFoundException()

                val appIcon = activity.getAppIcon(packageManager = packageManager)

                val expected = getDrawable(
                    activity.resources,
                    sf__salesforce_logo,
                    null
                )
                requireNotNull(expected)
                requireNotNull(appIcon)

                assertEquals(expected.constantState, appIcon.constantState)
            }
        }
    }

    @Test
    fun screenLockActivity_presentsBiometricAuthentication_whenBiometricSetupActivityResultLauncherLaunches() {
        launch<ScreenLockActivity>(
            Intent(
                getApplicationContext(),
                ScreenLockActivity::class.java,
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->
                val expectedValue = activity.getString(sf__screen_lock_setup_required, activity.viewModel.appName())
                activity.onBiometricSetupActivityResult(mockk())
                assertEquals(expectedValue, activity.viewModel.setupMessageText.value)
            }
        }
    }

    @Test
    fun screenLockActivity_PresentsBiometricAuthentication_whenBiometricManagerCanAuthenticate() {
        launch<ScreenLockActivity>(
            Intent(
                getApplicationContext(),
                ScreenLockActivity::class.java,
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->

                val biometricManager = mockk<BiometricManager>(relaxed = true)
                every { biometricManager.canAuthenticate(any()) } returns BIOMETRIC_SUCCESS
                val biometricPrompt = mockk<BiometricPrompt>(relaxed = true)

                activity.presentBiometricAuthentication(
                    biometricManager = biometricManager,
                    biometricPrompt = biometricPrompt,
                )

                verify(exactly = 1) { biometricPrompt.authenticate(any()) }
                assertFalse(activity.viewModel.logoutButtonVisible.value)
                assertFalse(activity.viewModel.setupButtonVisible.value)
                assertFalse(activity.viewModel.setupMessageVisible.value)
            }
        }
    }

    @Test
    fun screenLockActivity_SetsErrorMessage_whenBiometricManagerCannotAuthenticateOtherKnownErrors() {
        launch<ScreenLockActivity>(
            Intent(
                getApplicationContext(),
                ScreenLockActivity::class.java,
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->

                val biometricManager = mockk<BiometricManager>(relaxed = true)
                every { biometricManager.canAuthenticate(any()) } returns BIOMETRIC_ERROR_NO_HARDWARE
                val biometricPrompt = mockk<BiometricPrompt>(relaxed = true)

                activity.presentBiometricAuthentication(
                    biometricManager = biometricManager,
                    biometricPrompt = biometricPrompt,
                )

                assertEquals(getString(activity, sf__screen_lock_error), activity.viewModel.setupMessageText.value)
                verify(exactly = 0) { biometricPrompt.authenticate(any()) }


                every { biometricManager.canAuthenticate(any()) } returns BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED
                activity.viewModel.setupMessageText.value = "Unexpected Value"
                activity.presentBiometricAuthentication(
                    biometricManager = biometricManager,
                    biometricPrompt = biometricPrompt,
                )

                assertEquals(getString(activity, sf__screen_lock_error), activity.viewModel.setupMessageText.value)
                verify(exactly = 0) { biometricPrompt.authenticate(any()) }


                every { biometricManager.canAuthenticate(any()) } returns BIOMETRIC_ERROR_UNSUPPORTED
                activity.viewModel.setupMessageText.value = "Unexpected Value"
                activity.presentBiometricAuthentication(
                    biometricManager = biometricManager,
                    biometricPrompt = biometricPrompt,
                )

                assertEquals(getString(activity, sf__screen_lock_error), activity.viewModel.setupMessageText.value)
                verify(exactly = 0) { biometricPrompt.authenticate(any()) }


                every { biometricManager.canAuthenticate(any()) } returns BIOMETRIC_STATUS_UNKNOWN
                activity.viewModel.setupMessageText.value = "Unexpected Value"
                activity.presentBiometricAuthentication(
                    biometricManager = biometricManager,
                    biometricPrompt = biometricPrompt,
                )

                assertEquals(getString(activity, sf__screen_lock_error), activity.viewModel.setupMessageText.value)
                verify(exactly = 0) { biometricPrompt.authenticate(any()) }
            }
        }
    }

    @Test
    fun screenLockActivity_PresentsBiometricAuthentication_whenBiometricManagerCannotAuthenticateOtherUnknownErrors() {
        launch<ScreenLockActivity>(
            Intent(
                getApplicationContext(),
                ScreenLockActivity::class.java,
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->

                val biometricManager = mockk<BiometricManager>(relaxed = true)
                every { biometricManager.canAuthenticate(any()) } returns 67 /* Unanticipated value */
                val biometricPrompt = mockk<BiometricPrompt>(relaxed = true)

                activity.presentBiometricAuthentication(
                    biometricManager = biometricManager,
                    biometricPrompt = biometricPrompt,
                )
            }

            /* Intentionally Blank Until Android's BiometricManager provides type-safe error codes */
        }
    }

    @Test
    fun screenLockActivity_SetsErrorMessage_whenBiometricManagerCannotAuthenticateHardwareUnavailable() {
        launch<ScreenLockActivity>(
            Intent(
                getApplicationContext(),
                ScreenLockActivity::class.java,
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->

                val biometricManager = mockk<BiometricManager>(relaxed = true)
                every { biometricManager.canAuthenticate(any()) } returns BIOMETRIC_ERROR_HW_UNAVAILABLE
                val biometricPrompt = mockk<BiometricPrompt>(relaxed = true)

                activity.presentBiometricAuthentication(
                    biometricManager = biometricManager,
                    biometricPrompt = biometricPrompt,
                )

                assertEquals(getString(activity, sf__screen_lock_error_hw_unavailable), activity.viewModel.setupMessageText.value)
                verify(exactly = 0) { biometricPrompt.authenticate(any()) }
            }
        }
    }

    @Test
    fun screenLockActivity_presentsEnrollment_whenBiometricManagerCannotAuthenticateNoneEnrolled_api30Plus() {
        launch<ScreenLockActivity>(
            Intent(
                getApplicationContext(),
                ScreenLockActivity::class.java,
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->

                val biometricManager = mockk<BiometricManager>(relaxed = true)
                every { biometricManager.canAuthenticate(any()) } returns BIOMETRIC_ERROR_NONE_ENROLLED
                val biometricPrompt = mockk<BiometricPrompt>(relaxed = true)
                val biometricSetupActivityResultLauncher = mockk<ActivityResultLauncher<Intent>>(relaxed = true)
                val intent = slot<Intent>()

                activity.presentBiometricAuthentication(
                    biometricManager = biometricManager,
                    biometricPrompt = biometricPrompt,
                    biometricSetupActivityResultLauncher = biometricSetupActivityResultLauncher,
                    sdkConfiguration = AndroidSdkConfigurationR,
                )
                activity.viewModel.setupButtonAction.value()

                assertEquals(activity.getString(sf__screen_lock_setup_required, activity.viewModel.appName()), activity.viewModel.setupMessageText.value)
                verify(exactly = 1) { biometricSetupActivityResultLauncher.launch(capture(intent)) }
                assertEquals(ACTION_BIOMETRIC_ENROLL, intent.captured.action)
                assertEquals(activity.viewModel.biometricAuthenticators(), intent.captured.getIntExtra(EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, -1))
                verify(exactly = 0) { biometricPrompt.authenticate(any()) }
            }
        }
    }

    @Test
    fun screenLockActivity_presentsEnrollment_whenBiometricManagerCannotAuthenticateNoneEnrolled_api29Minus() {
        launch<ScreenLockActivity>(
            Intent(
                getApplicationContext(),
                ScreenLockActivity::class.java,
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->

                val biometricManager = mockk<BiometricManager>(relaxed = true)
                every { biometricManager.canAuthenticate(any()) } returns BIOMETRIC_ERROR_NONE_ENROLLED
                val biometricPrompt = mockk<BiometricPrompt>(relaxed = true)
                val biometricSetupActivityResultLauncher = mockk<ActivityResultLauncher<Intent>>(relaxed = true)
                val intent = slot<Intent>()

                activity.presentBiometricAuthentication(
                    biometricManager = biometricManager,
                    biometricPrompt = biometricPrompt,
                    biometricSetupActivityResultLauncher = biometricSetupActivityResultLauncher,
                    sdkConfiguration = AndroidSdkConfigurationQ,
                )
                activity.viewModel.setupButtonAction.value()

                assertEquals(activity.getString(sf__screen_lock_setup_required, activity.viewModel.appName()), activity.viewModel.setupMessageText.value)
                verify(exactly = 1) { biometricSetupActivityResultLauncher.launch(capture(intent)) }
                assertEquals(ACTION_SET_NEW_PASSWORD, intent.captured.action)
                assertEquals(-1, intent.captured.getIntExtra(EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, -1))
                assertEquals(activity.getString(sf__screen_lock_setup_button), activity.viewModel.setupButtonLabel.value)
                assertTrue(activity.viewModel.setupButtonVisible.value)
                verify(exactly = 0) { biometricPrompt.authenticate(any()) }
            }
        }
    }

    @Test
    fun screenLockActivity_getBiometricPromptInfoWithoutFeatures_returnsBiometricPromptInfoWithoutConfirmation_api29Plus() {
        launch<ScreenLockActivity>(
            Intent(
                getApplicationContext(),
                ScreenLockActivity::class.java,
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->

                val packageManager = mockk<PackageManager>(relaxed = true)
                every { packageManager.hasSystemFeature(FEATURE_FACE) } returns false
                every { packageManager.hasSystemFeature(FEATURE_IRIS) } returns false
                val result = activity.getBiometricPromptInfo(
                    packageManager = packageManager,
                    sdkConfiguration = AndroidSdkConfigurationQ,
                )
                assertEquals(activity.getString(sf__screen_lock_title, activity.viewModel.appName()), result.title)
                assertEquals(activity.getString(sf__screen_lock_subtitle, activity.viewModel.appName()), result.subtitle)
                assertEquals(activity.viewModel.biometricAuthenticators(), result.allowedAuthenticators)
                assertFalse(result.isConfirmationRequired)
            }
        }
    }

    @Test
    fun screenLockActivity_getBiometricPromptInfoWithoutFaceFeature_returnsBiometricPromptInfoWithConfirmation_api29Plus() {
        launch<ScreenLockActivity>(
            Intent(
                getApplicationContext(),
                ScreenLockActivity::class.java,
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->

                val packageManager = mockk<PackageManager>(relaxed = true)
                every { packageManager.hasSystemFeature(FEATURE_FACE) } returns true
                every { packageManager.hasSystemFeature(FEATURE_IRIS) } returns false
                val result = activity.getBiometricPromptInfo(
                    packageManager = packageManager,
                    sdkConfiguration = AndroidSdkConfigurationQ,
                )
                assertEquals(activity.getString(sf__screen_lock_title, activity.viewModel.appName()), result.title)
                assertEquals(activity.getString(sf__screen_lock_subtitle, activity.viewModel.appName()), result.subtitle)
                assertEquals(activity.viewModel.biometricAuthenticators(), result.allowedAuthenticators)
                assertTrue(result.isConfirmationRequired)
            }
        }
    }

    @Test
    fun screenLockActivity_getBiometricPromptInfoWithoutIrisFeature_returnsBiometricPromptInfoWithConfirmation_api29Plus() {
        launch<ScreenLockActivity>(
            Intent(
                getApplicationContext(),
                ScreenLockActivity::class.java,
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->

                val packageManager = mockk<PackageManager>(relaxed = true)
                every { packageManager.hasSystemFeature(FEATURE_FACE) } returns false
                every { packageManager.hasSystemFeature(FEATURE_IRIS) } returns true
                val result = activity.getBiometricPromptInfo(
                    packageManager = packageManager,
                    sdkConfiguration = AndroidSdkConfigurationQ,
                )
                assertEquals(activity.getString(sf__screen_lock_title, activity.viewModel.appName()), result.title)
                assertEquals(activity.getString(sf__screen_lock_subtitle, activity.viewModel.appName()), result.subtitle)
                assertEquals(activity.viewModel.biometricAuthenticators(), result.allowedAuthenticators)
                assertTrue(result.isConfirmationRequired)
            }
        }
    }

    @Test
    fun screenLockActivity_getBiometricPromptInfoWithFeatures_returnsBiometricPromptInfoWithConfirmation_api29Plus() {
        launch<ScreenLockActivity>(
            Intent(
                getApplicationContext(),
                ScreenLockActivity::class.java,
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->

                val packageManager = mockk<PackageManager>(relaxed = true)
                every { packageManager.hasSystemFeature(FEATURE_FACE) } returns true
                every { packageManager.hasSystemFeature(FEATURE_IRIS) } returns true
                val result = activity.getBiometricPromptInfo(
                    packageManager = packageManager,
                    sdkConfiguration = AndroidSdkConfigurationQ,
                )
                assertEquals(activity.getString(sf__screen_lock_title, activity.viewModel.appName()), result.title)
                assertEquals(activity.getString(sf__screen_lock_subtitle, activity.viewModel.appName()), result.subtitle)
                assertEquals(activity.viewModel.biometricAuthenticators(), result.allowedAuthenticators)
                assertTrue(result.isConfirmationRequired)
            }
        }
    }

    @Test
    fun screenLockActivity_getBiometricPromptInfo_returnsBiometricPromptInfoWithoutConfirmation_api28Minus() {

        launch<ScreenLockActivity>(
            Intent(
                getApplicationContext(),
                ScreenLockActivity::class.java,
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->

                val packageManager = mockk<PackageManager>(relaxed = true)
                every { packageManager.hasSystemFeature(FEATURE_FACE) } returns true
                every { packageManager.hasSystemFeature(FEATURE_IRIS) } returns true
                val result = activity.getBiometricPromptInfo(
                    packageManager = packageManager,
                    sdkConfiguration = AndroidSdkConfigurationP,
                )
                assertEquals(activity.getString(sf__screen_lock_title, activity.viewModel.appName()), result.title)
                assertEquals(activity.getString(sf__screen_lock_subtitle, activity.viewModel.appName()), result.subtitle)
                assertEquals(activity.viewModel.biometricAuthenticators(), result.allowedAuthenticators)
                assertFalse(result.isConfirmationRequired)
            }
        }
    }

    @Ignore
    @Test
    fun screenLockActivity_onAuthError_sendsAccessibilityEvent() {
        launch<ScreenLockActivity>(
            Intent(
                getApplicationContext(),
                ScreenLockActivity::class.java,
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->

                val errorString = "Error String"
                val authenticationErrorString = activity.getString(sf__screen_lock_auth_error)
                val accessibilityManager = mockk<AccessibilityManager>(relaxed = true)
                every { accessibilityManager.isEnabled } returns true
                val capturingSlot = slot<AccessibilityEvent>()
                activity.onAuthError(
                    accessibilityManager = accessibilityManager,
                    errString = errorString
                )

                verify(exactly = 1) { accessibilityManager.sendAccessibilityEvent(capture(capturingSlot)) }
                assertTrue(capturingSlot.captured.text.toString().contains(authenticationErrorString))

                assertEquals(errorString, activity.viewModel.setupMessageText.value)
                assertTrue(activity.viewModel.logoutButtonVisible.value)
                assertTrue(activity.viewModel.setupButtonVisible.value)
                assertEquals(activity.getString(sf__screen_lock_retry_button), activity.viewModel.setupButtonLabel.value)
                assertTrue(activity.viewModel.setupMessageVisible.value)
            }
        }
    }

    @Test
    fun screenLockActivity_onAuthError_sendsAccessibilityEventForEmptyString() {
        launch<ScreenLockActivity>(
            Intent(
                getApplicationContext(),
                ScreenLockActivity::class.java,
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->

                val errorString = ""
                val authenticationErrorString = activity.getString(sf__screen_lock_auth_error)
                val accessibilityManager = mockk<AccessibilityManager>(relaxed = true)
                every { accessibilityManager.isEnabled } returns true
                val capturingSlot = slot<AccessibilityEvent>()
                activity.onAuthError(
                    accessibilityManager = accessibilityManager,
                    errString = errorString
                )

                verify(exactly = 1) { accessibilityManager.sendAccessibilityEvent(capture(capturingSlot)) }
                assertTrue(capturingSlot.captured.text.toString().contains(authenticationErrorString))

                assertEquals(authenticationErrorString, activity.viewModel.setupMessageText.value)
                assertTrue(activity.viewModel.logoutButtonVisible.value)
                assertTrue(activity.viewModel.setupButtonVisible.value)
                assertEquals(activity.getString(sf__screen_lock_retry_button), activity.viewModel.setupButtonLabel.value)
                assertTrue(activity.viewModel.setupMessageVisible.value)
            }
        }
    }

    @Test
    fun screenLockActivity_finishSuccess_sendsAccessibilityEvent() {
        launch<ScreenLockActivity>(
            Intent(
                getApplicationContext(),
                ScreenLockActivity::class.java,
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->

                val accessibilityManager = mockk<AccessibilityManager>(relaxed = true)
                every { accessibilityManager.isEnabled } returns true
                val capturingSlot = slot<AccessibilityEvent>()
                val screenLockManager = mockk<ScreenLockManager>(relaxed = true)
                activity.finishSuccess(
                    accessibilityManager = accessibilityManager,
                    screenLockManager = screenLockManager,
                )

                verify(exactly = 1) { accessibilityManager.sendAccessibilityEvent(capture(capturingSlot)) }
                assertTrue(capturingSlot.captured.text.toString().contains(activity.getString(sf__screen_lock_auth_success)))

                verify(exactly = 1) { screenLockManager.onUnlock() }

                assertFalse(activity.viewModel.logoutButtonVisible.value)
                assertFalse(activity.viewModel.setupButtonVisible.value)
                assertFalse(activity.viewModel.setupMessageVisible.value)
            }
        }
    }

    @Test
    fun screenLockActivity_finishSuccess_sendsAccessibilityEventForNullScreenLockManager() {
        launch<ScreenLockActivity>(
            Intent(
                getApplicationContext(),
                ScreenLockActivity::class.java,
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->

                val accessibilityManager = mockk<AccessibilityManager>(relaxed = true)
                every { accessibilityManager.isEnabled } returns true
                val capturingSlot = slot<AccessibilityEvent>()
                activity.finishSuccess(
                    accessibilityManager = accessibilityManager,
                    screenLockManager = null,
                )

                verify(exactly = 1) { accessibilityManager.sendAccessibilityEvent(capture(capturingSlot)) }
                assertTrue(capturingSlot.captured.text.toString().contains(activity.getString(sf__screen_lock_auth_success)))

                assertFalse(activity.viewModel.logoutButtonVisible.value)
                assertFalse(activity.viewModel.setupButtonVisible.value)
                assertFalse(activity.viewModel.setupMessageVisible.value)
            }
        }
    }

    @Test
    fun screenLockActivity_finishSuccess_sendsAccessibilityEvent_api29Minus() {
        launch<ScreenLockActivity>(
            Intent(
                getApplicationContext(),
                ScreenLockActivity::class.java,
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->

                val accessibilityManager = mockk<AccessibilityManager>(relaxed = true)
                every { accessibilityManager.isEnabled } returns true
                val capturingSlot = slot<AccessibilityEvent>()
                val screenLockManager = mockk<ScreenLockManager>(relaxed = true)
                activity.finishSuccess(
                    accessibilityManager = accessibilityManager,
                    screenLockManager = screenLockManager,
                    sdkConfiguration = AndroidSdkConfigurationQ
                )

                verify(exactly = 1) { accessibilityManager.sendAccessibilityEvent(capture(capturingSlot)) }
                assertTrue(capturingSlot.captured.text.toString().contains(activity.getString(sf__screen_lock_auth_success)))
                assertEquals(TYPE_WINDOW_STATE_CHANGED, capturingSlot.captured.eventType)
                assertEquals(ScreenLockActivity::class.java.name, capturingSlot.captured.className)
                assertEquals(null, capturingSlot.captured.packageName)

                verify(exactly = 1) { screenLockManager.onUnlock() }

                assertFalse(activity.viewModel.logoutButtonVisible.value)
                assertFalse(activity.viewModel.setupButtonVisible.value)
                assertFalse(activity.viewModel.setupMessageVisible.value)
            }
        }
    }

    @Test
    fun screenLockActivity_logoutScreenLockUsers_logsOutUsers() {
        launch<ScreenLockActivity>(
            Intent(
                getApplicationContext(),
                ScreenLockActivity::class.java,
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->

                val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
                every { sharedPreferences.getBoolean(any(), any()) } returns true
                val context = mockk<Context>(relaxed = true)
                every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
                val userAccount = mockk<UserAccount>(relaxed = true)
                every { userAccount.userId } returns "expectedUserId"
                val authenticatedUsers = List(1) { userAccount }
                val userAccountManager = mockk<UserAccountManager>(relaxed = true)
                every { userAccountManager.authenticatedUsers } returns authenticatedUsers
                activity.logoutScreenLockUsers(
                    context = context,
                    userAccountManager = userAccountManager,
                )

                val userAccountSlot = slot<UserAccount>()
                verify(exactly = 1) { userAccountManager.signoutUser(capture(userAccountSlot), null, true, USER_LOGOUT) }
                assertEquals("expectedUserId", userAccountSlot.captured.userId)
            }
        }
    }

    @Test
    fun screenLockActivity_logoutScreenLockUsers_ignoresUsersWithoutScreenLock() {
        launch<ScreenLockActivity>(
            Intent(
                getApplicationContext(),
                ScreenLockActivity::class.java,
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->

                val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
                every { sharedPreferences.getBoolean(any(), any()) } returns false
                val context = mockk<Context>(relaxed = true)
                every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
                val userAccount = mockk<UserAccount>(relaxed = true)
                every { userAccount.userId } returns "expectedUserId"
                val authenticatedUsers = List(1) { userAccount }
                val userAccountManager = mockk<UserAccountManager>(relaxed = true)
                every { userAccountManager.authenticatedUsers } returns authenticatedUsers
                activity.logoutScreenLockUsers(
                    context = context,
                    userAccountManager = userAccountManager,
                )

                verify(exactly = 0) { userAccountManager.signoutUser(any(), null, true, USER_LOGOUT) }
            }
        }
    }

    @Test
    fun screenLockActivity_logoutScreenLockUsers_defaultParameters() {
        launch<ScreenLockActivity>(
            Intent(
                getApplicationContext(),
                ScreenLockActivity::class.java,
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->

                activity.logoutScreenLockUsers() // Assert default parameters do not throw exception.
            }
        }
    }

    @Test
    fun screenLockActivity_logoutScreenLockUsers_ignoresNullUsersList() {
        launch<ScreenLockActivity>(
            Intent(
                getApplicationContext(),
                ScreenLockActivity::class.java,
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->

                val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
                every { sharedPreferences.getBoolean(any(), any()) } returns true
                val context = mockk<Context>(relaxed = true)
                every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
                val authenticatedUsers = null
                val userAccountManager = mockk<UserAccountManager>(relaxed = true)
                every { userAccountManager.authenticatedUsers } returns authenticatedUsers
                activity.logoutScreenLockUsers(
                    context = context,
                    userAccountManager = userAccountManager,
                )

                verify(exactly = 0) { userAccountManager.signoutUser(any(), null, true, USER_LOGOUT) }
            }
        }
    }

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

    private fun Drawable.renderToBitmap(): Bitmap {
        val w = if (intrinsicWidth > 0) intrinsicWidth else 1
        val h = if (intrinsicHeight > 0) intrinsicHeight else 1

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)

        return bitmap
    }
}

val AndroidSdkConfigurationP = mockk<AndroidSdkConfiguration>().apply {
    every { isP } returns true
    every { isQ } returns false
    every { isR } returns false
    every { isS } returns false
    every { isTiramisu } returns false
}

val AndroidSdkConfigurationQ = mockk<AndroidSdkConfiguration>().apply {
    every { isP } returns true
    every { isQ } returns true
    every { isR } returns false
    every { isS } returns false
    every { isTiramisu } returns false
}

val AndroidSdkConfigurationR = mockk<AndroidSdkConfiguration>().apply {
    every { isP } returns true
    every { isQ } returns true
    every { isR } returns true
    every { isS } returns false
    every { isTiramisu } returns false
}

val AndroidSdkConfigurationS = mockk<AndroidSdkConfiguration>().apply {
    every { isP } returns true
    every { isQ } returns true
    every { isR } returns true
    every { isS } returns true
    every { isTiramisu } returns false
}
