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
package com.salesforce.androidsdk.auth

import android.os.Build.VERSION_CODES.Q
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.ui.ScreenLockViewModel
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenLockViewModelTest {
    private val viewModel = ScreenLockViewModel()

    @Test
    fun viewModel_appName_usesDefaultAppName() {
        val sdkManager = mockk<SalesforceSDKManager>(relaxed = true)
        every { sdkManager.appName } returns null
        val appName = viewModel.appName(sdkManager)
        assertEquals("App", appName)
    }

    @Test
    fun viewModel_appName_usesSdkManagerAppName() {
        val appName = viewModel.appName()
        assertEquals("SalesforceSDKTest", appName)
    }

    @Test
    fun viewModel_biometricAuthenticators_updatesOn_api30Plus() {
        val biometricAuthenticators = viewModel.biometricAuthenticators()
        assertEquals(BIOMETRIC_STRONG or DEVICE_CREDENTIAL, biometricAuthenticators)
    }

    @Test
    fun viewModel_biometricAuthenticators_updatesOn_api29Minus() {
        val biometricAuthenticators = viewModel.biometricAuthenticators(Q)
        assertEquals(BIOMETRIC_WEAK or DEVICE_CREDENTIAL, biometricAuthenticators)
    }
}
