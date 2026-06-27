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
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.BEACON_JWT
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.BEACON_OPAQUE
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.CA_OPAQUE
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.ECA_JWT
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.ECA_OPAQUE
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig.ADVANCED_AUTH
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig.REGULAR_AUTH
import com.salesforce.samples.authflowtester.testUtility.ScopeSelection.SUBSET
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for verifying that user sessions and per-user feature flags persist across app restarts.
 *
 * Mirrors iOS `LoginWithRestartTests.swift`. Each test logs in, force-stops the process via
 * `am force-stop`, relaunches, and asserts that both the session credentials and the feature
 * flags encoded in the user agent string are reloaded correctly from disk.
 *
 * NB: Tests use the `user` and `otherUser` derived from the device SDK level (see AuthFlowTest).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class LoginWithRestartTests : AuthFlowTest() {

    // MARK: - Legacy Login Persistence

    /** Login with CA Opaque, restart app, verify session persists. */
    @Test
    fun testCAOpaque_DefaultScopes_WithRestart() {
        loginAndValidate(
            knownAppConfig = CA_OPAQUE,
            knownLoginHostConfig = REGULAR_AUTH,
        )
        restartAndValidateUser(
            knownAppConfig = CA_OPAQUE,
            knownLoginHostConfig = REGULAR_AUTH,
        )
    }

    // MARK: - ECA Login Persistence

    /** Login with ECA Opaque, restart app, verify session persists. */
    @Test
    fun testECAOpaque_DefaultScopes_WithRestart() {
        loginAndValidate(
            knownAppConfig = ECA_OPAQUE,
            knownLoginHostConfig = REGULAR_AUTH,
        )
        restartAndValidateUser(
            knownAppConfig = ECA_OPAQUE,
            knownLoginHostConfig = REGULAR_AUTH,
        )
    }

    // MARK: - Beacon Login Persistence

    /** Login with Beacon Opaque, restart app, verify session and child key persist. */
    @Test
    fun testBeaconOpaque_DefaultScopes_WithRestart() {
        loginAndValidate(
            knownAppConfig = BEACON_OPAQUE,
            knownLoginHostConfig = REGULAR_AUTH,
        )
        restartAndValidateUser(
            knownAppConfig = BEACON_OPAQUE,
            knownLoginHostConfig = REGULAR_AUTH,
        )
    }

    // MARK: - ECA Dynamic Configuration

    /** Login with ECA JWT via dynamic config, restart app, verify session persists. */
    @Test
    fun testECAJwt_DefaultScopes_DynamicConfiguration_WithRestart() {
        loginAndValidate(
            knownAppConfig = ECA_JWT,
            knownLoginHostConfig = REGULAR_AUTH,
        )
        restartAndValidateUser(
            knownAppConfig = ECA_JWT,
            knownLoginHostConfig = REGULAR_AUTH,
        )
    }

    /** Login with ECA JWT using subset scopes via dynamic config, restart app, verify session persists. */
    @Test
    fun testECAJwt_SubsetScopes_DynamicConfiguration_WithRestart() {
        loginAndValidate(
            knownAppConfig = ECA_JWT,
            scopeSelection = SUBSET,
            knownLoginHostConfig = REGULAR_AUTH,
        )
        restartAndValidateUser(
            knownAppConfig = ECA_JWT,
            knownLoginHostConfig = REGULAR_AUTH,
        )
    }

    // MARK: - Beacon Dynamic Configuration

    /** Login with Beacon JWT via dynamic config, restart app, verify session persists. */
    @Test
    fun testBeaconJwt_DefaultScopes_DynamicConfiguration_WithRestart() {
        loginAndValidate(
            knownAppConfig = BEACON_JWT,
            knownLoginHostConfig = REGULAR_AUTH,
        )
        restartAndValidateUser(
            knownAppConfig = BEACON_JWT,
            knownLoginHostConfig = REGULAR_AUTH,
        )
    }

    /** Login with Beacon JWT using subset scopes via dynamic config, restart app, verify session persists. */
    @Test
    fun testBeaconJwt_SubsetScopes_DynamicConfiguration_WithRestart() {
        loginAndValidate(
            knownAppConfig = BEACON_JWT,
            scopeSelection = SUBSET,
            knownLoginHostConfig = REGULAR_AUTH,
        )
        restartAndValidateUser(
            knownAppConfig = BEACON_JWT,
            knownLoginHostConfig = REGULAR_AUTH,
        )
    }

    // MARK: - Feature Flag Persistence After Restart

    /** Login via advanced auth (BW flag set), restart app, verify BW flag persists in user agent. */
    @Test
    fun testAdvancedAuth_WithRestart() {
        loginAndValidate(
            knownAppConfig = BEACON_OPAQUE,
            knownLoginHostConfig = ADVANCED_AUTH,
        )
        restartAndValidateUser(
            knownAppConfig = BEACON_OPAQUE,
            knownLoginHostConfig = ADVANCED_AUTH,
            expectAdvancedAuth = true,
        )
    }

    /** Login via welcome discovery (WD flag set), restart app, verify WD flag persists in user agent. */
    @Test
    fun testWelcomeDiscovery_WithRestart() {
        loginAndValidate(
            knownAppConfig = ECA_OPAQUE,
            knownLoginHostConfig = REGULAR_AUTH,
            useWelcomeDiscovery = true,
        )
        restartAndValidateUser(
            knownAppConfig = ECA_OPAQUE,
            knownLoginHostConfig = REGULAR_AUTH,
            usesWelcomeDiscovery = true,
        )
    }

    // MARK: - Multi-User Restart

    /** Login two users, restart app, verify both sessions and user agent flags persist. */
    @Test
    fun testMultiUserRestart() {
        // Login user A
        loginAndValidate(
            knownAppConfig = ECA_OPAQUE,
            knownLoginHostConfig = REGULAR_AUTH,
        )

        // Login user B
        addOtherUserAndValidate(
            knownAppConfig = ECA_JWT,
            knownLoginHostConfig = REGULAR_AUTH,
        )

        // Force-stop and relaunch — credentials must reload from disk
        restartApp()

        // Verify and switch to user A
        switchToUserAndValidateUser(user)
        app.validateApiRequest()

        // Verify and switch to user B
        switchToUserAndValidateUser(otherUser)
        app.validateApiRequest()
    }
}
