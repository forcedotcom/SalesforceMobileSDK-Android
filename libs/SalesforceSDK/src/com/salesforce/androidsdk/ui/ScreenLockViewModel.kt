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

import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.R
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.Factory
import androidx.lifecycle.viewmodel.CreationExtras
import com.salesforce.androidsdk.app.SalesforceSDKManager

/**
 * A view model for the screen lock view and activity.
 * @param appName The displayed name of the app. This parameter is intended for
 * testing purposes only.  Defaults to "App"
 * @param biometricAuthenticators The biometric authenticators to use. This
 * parameter is intended for testing purposes only.  Defaults to appropriate
 * values for the current Android SDK
 */
internal class ScreenLockViewModel() : ViewModel() {

    // region Observables

    /** The visibility of the log out button */
    val logoutButtonVisible = mutableStateOf(true)

    /** The action to be taken when the setup button is tapped */
    val setupButtonAction = mutableStateOf({})

    /** The label for the setup button */
    val setupButtonLabel = mutableStateOf(null as String?)

    /** The visibility of the setup button */
    val setupButtonVisible = mutableStateOf(true)

    /** The text for the setup message */
    val setupMessageText = mutableStateOf(null as String?)

    /** The visibility of the setup message */
    val setupMessageVisible = mutableStateOf(true)

    // endregion
    // region Computed Values

    /**
     * The displayed name of the app.
     * @param sdkManager The SalesforceSDKManager to use. This parameter is
     * intended for testing purposes only. Defaults to the current
     * SalesforceSDKManager instance
     * @return The displayed name of the app
     */
    fun appName(
        sdkManager: SalesforceSDKManager = SalesforceSDKManager.getInstance(),
    ) = sdkManager.appName ?: "App"

    /**
     * Returns the biometric authenticators to use.
     * @param build The Android SDK build. This parameter is intended for
     * testing purposes only. Defaults to the current Android SDK build
     */
    fun biometricAuthenticators(
        build: Int = SDK_INT,
    ) = if (build >= R) {
        BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    } else {
        BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    }

    // endregion

    companion object {

        /** View model factory */
        internal val Factory = object : Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras,
            ): T {
                return ScreenLockViewModel() as T
            }
        }
    }
}
