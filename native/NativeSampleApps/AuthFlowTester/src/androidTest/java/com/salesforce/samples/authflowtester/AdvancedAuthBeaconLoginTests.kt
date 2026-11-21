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
package com.salesforce.samples.authflowtester

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.salesforce.androidsdk.app.Features
import com.salesforce.samples.authflowtester.testUtility.AuthFlowTest
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.BEACON_OPAQUE
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig.ADVANCED_AUTH
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Temporary compatibility coverage for a server that requires browser login when the deprecated
 * forceAdvancedAuthentication opt-out is disabled. Remove with that opt-out and WebView login in
 * SDK 15.0.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class AdvancedAuthBeaconLoginTests : AuthFlowTest() {

    @Test
    fun test_givenServerRequiresBrowserLogin_whenForceAdvancedAuthDisabled_thenUsesCustomTabWithB1() {
        loginAndValidate(
            knownAppConfig = BEACON_OPAQUE,
            knownLoginHostConfig = ADVANCED_AUTH,
            knownUserConfig = userConfigForApiLevel(ADVANCED_AUTH),
            forceAdvancedAuthentication = false,
        )
        app.validateUserAgentFlags(
            present = setOf("BW", Features.FEATURE_BROWSER_LOGIN_SERVER_AUTH_CONFIG),
            absent = setOf(
                Features.FEATURE_BROWSER_LOGIN_FOR_ADMIN,
                Features.FEATURE_BROWSER_LOGIN_FORCE_FLAG,
            ),
        )
    }
}
