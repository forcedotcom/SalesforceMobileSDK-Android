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
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.ECA_JWT_RTR
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.ECA_OPAQUE_RTR
import org.junit.Ignore
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

    // region ECA JWT RTR Tests

    // Login with ECA JWT RTR using hybrid auth token flow.
    // Expected to fail until W-22512846 (Enable Named JWTs for Hybrid Flows) is resolved.
    // The server currently returns invalid_grant when RTR is used with JWT tokens in hybrid flow.
    @Ignore("Won't pass until server completes W-22512846")
    @Test
    fun testECAJwtRtr_Hybrid() {
        loginAndValidate(knownAppConfig = ECA_JWT_RTR)
        assertRevokeAndRefreshWorks(isRtr = true)
        assertRevokeAndRefreshWorks(isRtr = true)
    }

    // Login with ECA JWT RTR without hybrid auth token.
    @Test
    fun testECAJwtRtr_NoHybrid() {
        loginAndValidate(knownAppConfig = ECA_JWT_RTR, useHybridAuthToken = false)
        assertRevokeAndRefreshWorks(isRtr = true)
        assertRevokeAndRefreshWorks(isRtr = true)
    }

    // endregion

    // region ECA Opaque RTR Tests

    // Login with ECA Opaque RTR using hybrid auth token flow.
    @Test
    fun testECAOpaqueRtr_Hybrid() {
        loginAndValidate(knownAppConfig = ECA_OPAQUE_RTR)
        assertRevokeAndRefreshWorks(isRtr = true)
        assertRevokeAndRefreshWorks(isRtr = true)
    }

    // Login with ECA Opaque RTR without hybrid auth token.
    @Test
    fun testECAOpaqueRtr_NoHybrid() {
        loginAndValidate(knownAppConfig = ECA_OPAQUE_RTR, useHybridAuthToken = false)
        assertRevokeAndRefreshWorks(isRtr = true)
        assertRevokeAndRefreshWorks(isRtr = true)
    }

    // endregion
}
