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
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.ECA_JWT_DPOP
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig.DPOP_AUTH
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for login flows using a DPoP-enabled External Client App (ECA) on the sdb6-2 org.
 *
 * NB: Tests use the first user from the dpop_auth section of ui_test_config.json.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class DPoPLoginTests : AuthFlowTest() {

    // region ECA JWT DPoP Tests

    // Login with ECA JWT DPoP using hybrid auth token flow.
    @Test
    fun testECAJwtDPoP_Hybrid() {
        loginAndValidate(knownAppConfig = ECA_JWT_DPOP, useDPoP = true, knownLoginHostConfig = DPOP_AUTH)
        assertRevokeAndRefreshWorks(isRtr = false, knownLoginHostConfig = DPOP_AUTH)
        assertRevokeAndRefreshWorks(isRtr = false, knownLoginHostConfig = DPOP_AUTH)
    }

    // Login with ECA JWT DPoP without hybrid auth token.
    @Test
    fun testECAJwtDPoP_NoHybrid() {
        loginAndValidate(knownAppConfig = ECA_JWT_DPOP, useHybridAuthToken = false, useDPoP = true, knownLoginHostConfig = DPOP_AUTH)
        assertRevokeAndRefreshWorks(isRtr = false, knownLoginHostConfig = DPOP_AUTH)
        assertRevokeAndRefreshWorks(isRtr = false, knownLoginHostConfig = DPOP_AUTH)
    }

    // endregion
}
