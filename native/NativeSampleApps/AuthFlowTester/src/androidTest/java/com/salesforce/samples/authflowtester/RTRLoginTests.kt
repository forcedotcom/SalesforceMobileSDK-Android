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
import com.salesforce.androidsdk.app.Features.FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID
import com.salesforce.androidsdk.app.Features.FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID
import com.salesforce.androidsdk.app.Features.FEATURE_RTR
import com.salesforce.androidsdk.app.Features.FEATURE_TOKEN_FORMAT_OPAQUE
import com.salesforce.samples.authflowtester.testUtility.AuthFlowTest
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.ECA_JWT_RTR
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.ECA_OPAQUE_RTR
import com.salesforce.samples.authflowtester.components.ManyRequestInterruption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for login flows using External Client App (ECA) configurations with Refresh Token Rotation (RTR).
 *
 * NB: Tests use the first user from ui_test_config.json
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class RTRLoginTests : AuthFlowTest() {

    @Test
    fun testECAJwtRtr_RevokedBeforeManyRequests_OneRefreshAndAllSucceed() {
        loginAndValidate(knownAppConfig = ECA_JWT_RTR)
        val beforeTokens = app.getTokens()
        app.revokeAccessToken()
        val tokenRequestsBeforeBatch = app.getCapturedTokenRequestCount()

        app.startManyRequests()

        val counts = app.waitForManyRequestsToComplete(configuredCount = 20)
        assertEquals(20, counts.successes)
        assertEquals(0, counts.failures)
        val afterTokens = app.getTokens()
        assertNotEquals(beforeTokens.accessToken, afterTokens.accessToken)
        assertNotEquals(beforeTokens.refreshToken, afterTokens.refreshToken)
        assertEquals(
            "Concurrent 401 responses should share one RTR token refresh",
            1,
            app.getCapturedTokenRequestCount() - tokenRequestsBeforeBatch,
        )
    }

    @Test
    fun testECAJwtRtr_RevokeWhenInFlight_BatchAndFollowUpRecover() {
        loginAndValidate(knownAppConfig = ECA_JWT_RTR)
        val beforeTokens = app.getTokens()
        app.selectManyRequestInterruption(ManyRequestInterruption.REVOKE)

        app.startManyRequests()

        app.waitForManyRequestsSubmitted()
        app.waitForManyRequestInterruption("Revoke completed")
        app.waitForManyRequestsToComplete(configuredCount = 20)
        app.validateApiRequest()
        val afterTokens = app.getTokens()
        assertNotEquals(beforeTokens.accessToken, afterTokens.accessToken)
        assertNotEquals(beforeTokens.refreshToken, afterTokens.refreshToken)
    }

    /**
     * A user that has already rotated must send RT on the first token request after restart.
     * This asserts the final header captured by the OkHttp network interceptor, not the user agent
     * recomputed after the refresh response registers RT again.
     */
    @Test
    fun testECAOpaqueRtr_Hybrid_WithRestart() {
        loginAndValidate(knownAppConfig = ECA_OPAQUE_RTR)
        assertRevokeAndRefreshWorks(expectsRefreshTokenRotation = true)

        restartApp()
        assertRevokeAndRefreshWorks(expectsRefreshTokenRotation = true)
        app.validateLastTokenRequestUserAgent(
            FEATURE_RTR,
            FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID,
            FEATURE_TOKEN_FORMAT_OPAQUE,
        )
    }

    // region ECA JWT RTR Tests

    // Login with ECA JWT RTR using hybrid auth token flow.
    @Test
    fun testECAJwtRtr_Hybrid() {
        loginAndValidate(knownAppConfig = ECA_JWT_RTR)
        assertRevokeAndRefreshWorks(expectsRefreshTokenRotation = true, isJwt = true)
        assertRevokeAndRefreshWorks(expectsRefreshTokenRotation = true, isJwt = true)
    }

    // Login with ECA JWT RTR without hybrid auth token.
    @Test
    fun testECAJwtRtr_NoHybrid() {
        loginAndValidate(knownAppConfig = ECA_JWT_RTR, useHybridAuthToken = false)
        assertRevokeAndRefreshWorks(expectsRefreshTokenRotation = true, expectedAMarker = FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID, isJwt = true)
        assertRevokeAndRefreshWorks(expectsRefreshTokenRotation = true, expectedAMarker = FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID, isJwt = true)
    }

    // endregion

    // region ECA Opaque RTR Tests

    // Login with ECA Opaque RTR using hybrid auth token flow.
    @Test
    fun testECAOpaqueRtr_Hybrid() {
        loginAndValidate(knownAppConfig = ECA_OPAQUE_RTR)
        assertRevokeAndRefreshWorks(expectsRefreshTokenRotation = true)
        assertRevokeAndRefreshWorks(expectsRefreshTokenRotation = true)
    }

    // Login with ECA Opaque RTR without hybrid auth token.
    @Test
    fun testECAOpaqueRtr_NoHybrid() {
        loginAndValidate(knownAppConfig = ECA_OPAQUE_RTR, useHybridAuthToken = false)
        assertRevokeAndRefreshWorks(expectsRefreshTokenRotation = true, expectedAMarker = FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID)
        assertRevokeAndRefreshWorks(expectsRefreshTokenRotation = true, expectedAMarker = FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID)
    }

    // endregion
}
