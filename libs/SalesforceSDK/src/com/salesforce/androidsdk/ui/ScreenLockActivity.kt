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

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager.ACTION_SET_NEW_PASSWORD
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.FEATURE_FACE
import android.content.pm.PackageManager.FEATURE_IRIS
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Build.VERSION_CODES.R
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Bundle
import android.provider.Settings.ACTION_BIOMETRIC_ENROLL
import android.provider.Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED
import android.view.WindowManager.LayoutParams.FLAG_SECURE
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
import android.view.accessibility.AccessibilityManager
import android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED
import androidx.biometric.BiometricManager.BIOMETRIC_STATUS_UNKNOWN
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.AuthenticationCallback
import androidx.biometric.BiometricPrompt.AuthenticationResult
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.FragmentActivity
import com.google.accompanist.drawablepainter.rememberDrawablePainter
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
import com.salesforce.androidsdk.R.style.SalesforceSDK_ScreenLock
import com.salesforce.androidsdk.R.style.SalesforceSDK_ScreenLock_Dark
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.app.SalesforceSDKManager.Companion.getInstance
import com.salesforce.androidsdk.auth.OAuth2.LogoutReason.USER_LOGOUT
import com.salesforce.androidsdk.security.ScreenLockManager
import com.salesforce.androidsdk.security.ScreenLockManager.Companion.MOBILE_POLICY_PREF
import com.salesforce.androidsdk.security.ScreenLockManager.Companion.SCREEN_LOCK
import com.salesforce.androidsdk.ui.ScreenLockViewModel.Companion.Factory
import com.salesforce.androidsdk.ui.components.ScreenLockView
import com.salesforce.androidsdk.util.SalesforceSDKLogger

/**
 * An activity that locks the app behind the operating system's provided
 * authentication or guides the user through biometric enrollment.
 */
class ScreenLockActivity : FragmentActivity() {

    /** View model */
    @VisibleForTesting
    internal val viewModel: ScreenLockViewModel
            by viewModels { Factory }

    /** The activity result for the biometric setup */
    private val biometricSetupActivityResultLauncher = registerForActivityResult(StartActivityForResult(), ::onBiometricSetupActivityResult)

    @Override
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        create()
    }

    /**
     * Implements the creation of the activity.
     * @param build The Android SDK build. This parameter is intended for
     * testing purposes only. Defaults to the current Android SDK build
     * @param packageManager The PackageManager to use. This parameter is
     * intended for testing purposes only. Defaults to the current
     * PackageManager instance
     * @param sdkManager The SalesforceSDKManager to use. This parameter is
     * intended for testing purposes only. Defaults to the current
     * SalesforceSDKManager instance
     */
    @VisibleForTesting
    internal fun create(
        build: Int = SDK_INT,
        packageManager: PackageManager = this.packageManager,
        sdkManager: SalesforceSDKManager = getInstance(),
    ) {
        enableEdgeToEdge()

        // Protect against screenshots.
        window.setFlags(FLAG_SECURE, FLAG_SECURE)

        // Set the theme.
        val isDarkTheme = sdkManager.isDarkTheme
        setTheme(if (isDarkTheme) SalesforceSDK_ScreenLock_Dark else SalesforceSDK_ScreenLock)

        // Make the navigation bar visible on light themes.
        getInstance().setViewNavigationVisibility(this)

        // Get the app icon.
        val appIcon = getAppIcon(packageManager)

        // Set the content.
        setContent {
            MaterialTheme(colorScheme = getInstance().colorScheme()) {
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                ) { innerPadding ->
                    ScreenLockView(
                        appName = viewModel.appName(),
                        appIcon = rememberDrawablePainter(appIcon),
                        innerPadding = innerPadding,
                        logoutAction = ::logoutScreenLockUsers,
                    )
                }
            }
        }

        // TODO: Remove this when min API > 33
        // Disable back navigation.
        if (build >= TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                PRIORITY_DEFAULT, ::noOp
            )
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        // Present biometric authentication or enrollment.
        presentBiometricAuthentication()
    }

    /**
     * Handles the result of the biometric setup activity.
     * @param result The result of the biometric setup activity
     */
    @VisibleForTesting
    internal fun onBiometricSetupActivityResult(result: ActivityResult) {
        /*
         * Present authentication again after the user has come back from
         * security settings to ensure they actually set up a secure lock screen
         * such as pin, pattern, password etc. instead of swipe or none.
         */
        presentBiometricAuthentication()
    }

    /** A callback to disable back navigation */
    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            // Purposefully blank
        }
    }

    /**
     * Gets the app icon.
     * @param packageManager The PackageManager to use. This parameter is
     * intended for testing purposes only. Defaults to the current
     * PackageManager instance
     */
    @VisibleForTesting
    internal fun getAppIcon(
        packageManager: PackageManager = this.packageManager,
    ) = runCatching {
        packageManager.getApplicationIcon(applicationInfo.packageName)
    }.getOrNull() ?: ResourcesCompat.getDrawable(resources, sf__salesforce_logo, null)

    /**
     * Challenges the user to authenticate using biometric authentication when
     * enrolled or prompts the user to enroll in biometric authentication before
     * allowing the user to proceed.
     * @param biometricManager The biometric manager to use for authentication.
     * This parameter is intended for testing purposes only.  Defaults to a new
     * BiometricManager created from this activity
     * @param biometricPrompt The biometric prompt to use for authentication.
     * This parameter is intended for testing purposes only.  Defaults to a new
     * BiometricPrompt
     * @param biometricSetupActivityResultLauncher The activity result launcher
     * to use for biometric setup. This parameter is intended for testing
     * purposes only.  Defaults to the biometric setup activity result launcher
     * @param build The Android SDK build. This parameter is intended for
     * testing purposes only. Defaults to the current Android SDK build
     */
    @VisibleForTesting
    internal fun presentBiometricAuthentication(
        biometricManager: BiometricManager = BiometricManager.from(this),
        biometricPrompt: BiometricPrompt = getBiometricPrompt(),
        biometricSetupActivityResultLauncher: ActivityResultLauncher<Intent> = this.biometricSetupActivityResultLauncher,
        build: Int = SDK_INT,
    ) {
        when (biometricManager.canAuthenticate(viewModel.biometricAuthenticators())) {
            BIOMETRIC_ERROR_NO_HARDWARE, BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED, BIOMETRIC_ERROR_UNSUPPORTED, BIOMETRIC_STATUS_UNKNOWN -> {
                // This should never happen.
                val error = getString(sf__screen_lock_error)
                SalesforceSDKLogger.e(TAG, "Biometric manager cannot authenticate. $error")
                setErrorMessage(error)
            }

            BIOMETRIC_ERROR_HW_UNAVAILABLE -> setErrorMessage(getString(sf__screen_lock_error_hw_unavailable))
            BIOMETRIC_ERROR_NONE_ENROLLED -> {
                setErrorMessage(getString(sf__screen_lock_setup_required, viewModel.appName()))

                // Prompts the user to setup the operating system screen lock and biometrics.
                if (build >= R) { // TODO: Remove when min API > 29.
                    viewModel.setupButtonAction.value = {
                        biometricSetupActivityResultLauncher.launch(Intent(ACTION_BIOMETRIC_ENROLL).apply {
                            putExtra(EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, viewModel.biometricAuthenticators())
                        })
                    }
                } else {
                    viewModel.setupButtonAction.value = {
                        biometricSetupActivityResultLauncher.launch(Intent(ACTION_SET_NEW_PASSWORD))
                    }
                }
                viewModel.setupButtonLabel.value = getString(sf__screen_lock_setup_button)
                viewModel.setupButtonVisible.value = true
            }

            BIOMETRIC_SUCCESS -> {
                resetUI()
                biometricPrompt.authenticate(getBiometricPromptInfo())
            }
        }
    }

    /**
     * Determines how the biometric prompt should appear and behave.
     * @param build The Android SDK build. This parameter is intended for
     * testing purposes only. Defaults to the current Android SDK build
     * @param packageManager The package manager to use for authentication.
     * This parameter is intended for testing purposes only. Defaults to the
     * current package manager
     */
    @VisibleForTesting
    internal fun getBiometricPromptInfo(
        build: Int = SDK_INT,
        packageManager: PackageManager = this.packageManager,
    ): PromptInfo {
        val hasFaceUnlock = if (build >= Q) { // TODO: Remove when min API > 28.
            packageManager.hasSystemFeature(FEATURE_FACE) || (packageManager.hasSystemFeature(FEATURE_IRIS))
        } else {
            false
        }

        return PromptInfo.Builder()
            .setTitle(getString(sf__screen_lock_title, viewModel.appName()))
            .setSubtitle(getString(sf__screen_lock_subtitle, viewModel.appName()))
            .setAllowedAuthenticators(viewModel.biometricAuthenticators())
            .setConfirmationRequired(hasFaceUnlock)
            .build()
    }

    /**
     * Returns a biometric prompt.
     * @return The biometric prompt
     */
    private fun getBiometricPrompt(): BiometricPrompt {
        return BiometricPrompt(
            this as FragmentActivity,
            ContextCompat.getMainExecutor(this),
            BiometricAuthenticationCallback()
        )
    }

    /**
     * Handles an authentication error.
     * @param accessibilityManager The accessibility manager to use for sending
     * the event. This parameter is intended for testing purposes only. Defaults
     * to the current accessibility manager
     * @param errString The error string
     */
    @VisibleForTesting
    internal fun onAuthError(
        accessibilityManager: AccessibilityManager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager,
        errString: CharSequence,
    ) {
        val authError = getString(sf__screen_lock_auth_error)
        val errString = errString.ifEmpty { authError }

        setErrorMessage(errString.toString())
        sendAccessibilityEvent(
            accessibilityManager = accessibilityManager,
            eventText = authError,
        )

        viewModel.setupButtonAction.value = { presentBiometricAuthentication() }
        viewModel.setupButtonLabel.value = getString(sf__screen_lock_retry_button)
        viewModel.setupButtonVisible.value = true
    }

    /**
     * Finishes the activity successfully on biometric authentication success.
     * @param accessibilityManager The accessibility manager to use for sending
     * the event. This parameter is intended for testing purposes only. Defaults
     * to the current accessibility manager
     * @param build The Android SDK build. This parameter is intended for
     * testing purposes only. Defaults to the current Android SDK build
     * @param screenLockManager The screen lock manager to use for
     * authentication. This parameter is intended for testing purposes only.
     * Defaults to the current screen lock manager
     */
    @VisibleForTesting
    internal fun finishSuccess(
        build: Int = SDK_INT,
        accessibilityManager: AccessibilityManager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager,
        screenLockManager: ScreenLockManager? = getInstance().screenLockManager as ScreenLockManager?,
    ) {
        resetUI()
        sendAccessibilityEvent(
            accessibilityManager = accessibilityManager,
            build = build,
            eventText = getString(sf__screen_lock_auth_success),
        )
        screenLockManager?.onUnlock()
        finish()
    }

    /**
     * Logs out all users requiring screen lock.
     * @param context The application context to use for querying user
     * preferences. This parameter is intended for testing purposes only.
     * Defaults to the current application context.
     * @param userAccountManager The user account manager to use for querying
     * and logging out users. This parameter is intended for testing purposes
     * only. Defaults to the current user account manager
     */
    @VisibleForTesting
    internal fun logoutScreenLockUsers(
        context: Context = getInstance().appContext,
        userAccountManager: UserAccountManager = getInstance().userAccountManager,
    ) {
        val accounts = userAccountManager.getAuthenticatedUsers()

        accounts?.forEach { account ->
            val accountPreferences = context.getSharedPreferences(
                "$MOBILE_POLICY_PREF${account.getUserLevelFilenameSuffix()}",
                MODE_PRIVATE
            )
            if (accountPreferences.getBoolean(SCREEN_LOCK, false)) {
                userAccountManager.signoutUser(account, null, true, USER_LOGOUT)
            }
        }

        sendAccessibilityEvent(eventText = "You are logged out.")
        finish()
    }

    /**
     * Sets the error message.
     * @param message The error message
     */
    @VisibleForTesting
    internal fun setErrorMessage(message: String) {
        viewModel.logoutButtonVisible.value = true
        viewModel.setupMessageText.value = message
        viewModel.setupMessageVisible.value = true
    }

    /**
     * Resets the UI.
     */
    private fun resetUI() {
        viewModel.logoutButtonVisible.value = false
        viewModel.setupButtonVisible.value = false
        viewModel.setupMessageVisible.value = false
    }

    /**
     * Sends an accessibility event.
     * @param accessibilityManager The accessibility manager to use for sending
     * the event. This parameter is intended for testing purposes only. Defaults
     * to the current accessibility manager
     * @param build The Android SDK build. This parameter is intended for
     * testing purposes only. Defaults to the current Android SDK build
     * @param eventText The event text
     */
    @VisibleForTesting
    internal fun sendAccessibilityEvent(
        accessibilityManager: AccessibilityManager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager,
        build: Int = SDK_INT,
        eventText: String?,
    ) {
        if (accessibilityManager.isEnabled) {
            accessibilityManager.sendAccessibilityEvent(
                if (build >= R) {
                    AccessibilityEvent()
                } else {
                    // TODO: Remove when min API > 29.
                    @Suppress("DEPRECATION")
                    AccessibilityEvent.obtain()
                }.apply {
                    setEventType(TYPE_WINDOW_STATE_CHANGED)
                    setClassName(this@ScreenLockActivity.javaClass.getName())
                    setPackageName(this@ScreenLockActivity::javaClass.get().packageName)
                    text.add(eventText)
                })
        }
    }

    companion object {
        private const val TAG = "ScreenLockActivity"
    }

    /**
     * A biometric authentication callback.
     * @param activity The activity to use for authentication.  This parameter
     * is intended for testing purposes only. Defaults to this inner class
     * receiver
     */
    @VisibleForTesting
    internal inner class BiometricAuthenticationCallback(
        private val activity: ScreenLockActivity = this@ScreenLockActivity,
    ) : AuthenticationCallback() {

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            activity.onAuthError(
                errString = errString
            )
        }

        override fun onAuthenticationSucceeded(result: AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            activity.finishSuccess()
        }

        override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
            activity.setErrorMessage(getString(sf__screen_lock_auth_failed))
            activity.sendAccessibilityEvent(eventText = getString(sf__screen_lock_auth_failed))
        }
    }
}
