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
import com.salesforce.samples.authflowtester.testUtility.AuthFlowTest
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.BEACON_OPAQUE
import com.salesforce.samples.authflowtester.testUtility.ScopeSelection.SUBSET
import com.salesforce.samples.authflowtester.testUtility.ScopeSelection.ALL
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for login flows using Beacon app configurations.
 * Beacon apps are lightweight authentication apps for specific use cases.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BeaconLoginTests: AuthFlowTest() {
    // region Beacon Opaque Tests

    // Login with Beacon opaque using default scopes and web server flow.
    @Test
    fun testBeaconOpaque_DefaultScopes() {
        loginAndValidate(knownAppConfig = BEACON_OPAQUE)
    }

    // Login with Beacon opaque using subset of scopes and web server flow.
    @Test
    fun testBeaconOpaque_SubsetScopes() {
        loginAndValidate(knownAppConfig = BEACON_OPAQUE, scopeSelection = SUBSET)
    }

    // Login with Beacon opaque using all scopes and web server flow.
    @Test
    fun testBeaconOpaque_AllScopes() {
        loginAndValidate(knownAppConfig = BEACON_OPAQUE, scopeSelection = ALL)
    }

    // endregion

    // region Beacon JWT Tests

    // Login with Beacon JWT using default scopes and web server flow.
    @Test
    fun testBeaconJwt_DefaultScopes() {
        loginAndValidate(knownAppConfig = KnownAppConfig.BEACON_JWT)
    }

    // Login with Beacon JWT using subset of scopes and web server flow.
    @Test
    fun testBeaconJwt_SubsetScopes() {
        loginAndValidate(knownAppConfig = KnownAppConfig.BEACON_JWT, scopeSelection = SUBSET)
    }

    // Login with Beacon JWT using all scopes and web server flow.
    @Test
    fun testBeaconJwt_AllScopes() {
        loginAndValidate(knownAppConfig = KnownAppConfig.BEACON_JWT, scopeSelection = ALL)
    }

    // endregion
}