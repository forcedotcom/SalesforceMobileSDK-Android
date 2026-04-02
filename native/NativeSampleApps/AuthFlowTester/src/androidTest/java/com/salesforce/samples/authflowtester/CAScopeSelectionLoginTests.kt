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
package com.salesforce.samples.authflowtester

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.salesforce.samples.authflowtester.testUtility.AuthFlowTest
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.CA_OPAQUE
import com.salesforce.samples.authflowtester.testUtility.ScopeSelection.SUBSET
import com.salesforce.samples.authflowtester.testUtility.ScopeSelection.ALL
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for legacy login flows including:
 *  - Connected App (CA) configurations (traditional OAuth connected apps)
 *  - User agent flow tests
 *  - Non-hybrid flow tests
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class CAScopeSelectionLoginTests: AuthFlowTest() {
    // region CA Web Server Flow Tests

    // Login with CA opaque using subset of scopes and web server flow.
    @Test
    fun testCAOpaque_SubsetScopes_WebServerFlow() {
        loginAndValidate(
            knownAppConfig = CA_OPAQUE,
            scopeSelection = SUBSET,
            useHybridAuthToken = false,
        )
    }

    // Login with CA opaque using all scopes and web server flow.
    @Test
    fun testCAOpaque_AllScopes_WebServerFlow() {
        loginAndValidate(
            knownAppConfig = CA_OPAQUE,
            scopeSelection = ALL,
        )
    }

    // endregion
    // region CA Non-hybrid Web Server Flow Tests

    // Login with CA opaque using subset of scopes and (non-hybrid) web server flow.
    @Test
    fun testCAOpaque_SubsetScopes_WebServerFlow_NotHybrid() {
        loginAndValidate(
            knownAppConfig = CA_OPAQUE,
            scopeSelection = SUBSET,
            useHybridAuthToken = false,
        )
    }

    // Login with CA opaque using all scopes and (non-hybrid) web server  flow.
    @Test
    fun testCAOpaque_AllScopes_WebServerFlow_NotHybrid() {
        loginAndValidate(
            knownAppConfig = CA_OPAQUE,
            scopeSelection = ALL,
            useHybridAuthToken = false,
        )
    }

    // endregion
    // region CA User Agent Flow Tests

    // Login with CA opaque using subset of scopes and user agent flow.
    @Test
    fun testCAOpaque_SubsetScopes_UserAgentFlow() {
        loginAndValidate(
            knownAppConfig = CA_OPAQUE,
            scopeSelection = SUBSET,
            useWebServerFlow = false,
        )
    }

    // Login with CA opaque using all scopes and user agent flow.
    @Test
    fun testCAOpaque_AllScopes_UserAgentFlow() {
        loginAndValidate(
            knownAppConfig = CA_OPAQUE,
            scopeSelection = ALL,
            useWebServerFlow = false,
        )
    }

    // endregion
    // region CA Non-hybrid User Agent Flow Tests

    // Login with CA opaque using subset of scopes and (non-hybrid) user agent flow.
    @Test
    fun testCAOpaque_SubsetScopes_UserAgentFlow_NotHybrid() {
        loginAndValidate(
            knownAppConfig = CA_OPAQUE,
            scopeSelection = SUBSET,
            useWebServerFlow = false,
            useHybridAuthToken = false,
        )
    }

    // Login with CA opaque using all scopes and (non-hybrid) user agent  flow.
    @Test
    fun testCAOpaque_AllScopes_UserAgentFlow_NotHybrid() {
        loginAndValidate(
            knownAppConfig = CA_OPAQUE,
            scopeSelection = ALL,
            useWebServerFlow = false,
            useHybridAuthToken = false,
        )
    }

    // endregion
}