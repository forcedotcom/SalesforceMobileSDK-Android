/*
 * Copyright (c) 2023-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.security.interfaces

import androidx.fragment.app.FragmentManager

/**
 * Manages if the user should re-authenticate to use the app.
 */
// @Suppress is necessary due to a Kotlin bug:  https://youtrack.jetbrains.com/issue/KT-31420
@Suppress("INAPPLICABLE_JVM_NAME")
interface BiometricAuthenticationManager {

    /**
     * If the feature is enabled for the current user.
     */
    @get:JvmName("isEnabled")
    val enabled: Boolean

    /**
     * If the device is currently locked.  Authenticated rest requests will fail.
     */
    @get:JvmName("isLocked")
    val locked: Boolean

    /**
     * Locks the device immediately.  Authenticated rest requests will fail until the user unlocks the app.
     */
    fun lock()

    /**
     * Enables or disables the use of biometric to skip username password authentication on the
     * login screen to unlock the app for the current user.
     *
     * @param optIn True to enable or false to disable
     */
    fun biometricOptIn(optIn: Boolean = true)

    /**
     * If the current user has opted in to biometric unlock or not.
     *
     * @return True if the current user has opted in, false if not
     */
    fun hasBiometricOptedIn(): Boolean

    /**
     * Presents a dialog to the user asking them to opt-in to biometric authentication.
     *
     * @param fragmentManager The fragment manager used to display the dialog
     */
    fun presentOptInDialog(fragmentManager: FragmentManager)

    /**
     * Enables or disables a native button on the login screen that allows the user to bypass
     * username password authentication with biometric.  By default the button is enabled.
     *
     * @param enabled True to use the native button or false to hide it
     */
    fun enableNativeBiometricLoginButton(enabled: Boolean = true)
}