/*
 * Copyright (c) 2026-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 * - Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written permission
 * of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.samples.authflowtester

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.samples.authflowtester.components.ManyRequestInterruption
import com.salesforce.samples.authflowtester.pageObjects.LoginPageObject
import com.salesforce.samples.authflowtester.testUtility.AuthFlowTest
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.ECA_JWT_RTR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class ConcurrentRestRequestTests : AuthFlowTest() {

    @Test
    fun testECAJwtRtr_ManyMixedRequests_DefaultsAndSuccess() {
        loginAndValidate(knownAppConfig = ECA_JWT_RTR)

        app.validateDefaultManyRequestOptions()
        app.startManyRequests()

        val counts = app.waitForManyRequestsToComplete(configuredCount = 20)
        assertEquals(20, counts.successes)
        assertEquals(0, counts.failures)
        app.validateMixedSuccessfulRequests(count = 20)
        assertTrue("App should remain responsive after the batch", app.isAppLoaded())
    }

    @Test
    fun testFailedRequest_TappingSquareShowsErrorDetails() {
        loginAndValidate(knownAppConfig = ECA_JWT_RTR)
        app.configureFailedManyRequest(index = 2)
        app.selectManyRequestCount(5)

        app.startManyRequests()

        val counts = app.waitForManyRequestsToComplete(configuredCount = 5)
        assertEquals(4, counts.successes)
        assertEquals(1, counts.failures)

        val details = app.tapFailedManyRequest(
            index = 2,
            type = ConcurrentRequestType.DESCRIBE_GLOBAL,
        )
        assertTrue(details.contains("Request: 3"))
        assertTrue(details.contains("Type: Describe Global"))
        assertTrue(details.contains("authflowtester-invalid-endpoint-2"))
        assertTrue(details.contains("HTTP status: 404"))
        assertTrue(details.contains("Error:"))
        app.validateManyRequestErrorCopyAvailable()
    }

    @Test
    fun testECAJwtRtr_LogoutWhenInFlight_RemainsLoggedOutAfterRestart() {
        loginAndValidate(knownAppConfig = ECA_JWT_RTR)
        app.selectManyRequestInterruption(ManyRequestInterruption.LOGOUT)

        app.startManyRequests()

        app.waitForManyRequestsSubmitted()
        app.waitForManyRequestInterruption("Logout requested")
        waitForAuthenticatedUserCount(expectedCount = 0)
        assertFalse("Session detail screen should close after logout", app.isAppLoaded())

        restartApp(waitForAuthenticatedApp = false)
        waitForAuthenticatedUserCount(expectedCount = 0)
        val loginPage = LoginPageObject(composeTestRule)
        loginPage.waitForLoginScreen()
        assertTrue("Cold relaunch should remain at login", loginPage.isLoginScreenVisible())
    }

    private fun waitForAuthenticatedUserCount(expectedCount: Int) {
        val userAccountManager = SalesforceSDKManager.getInstance().userAccountManager
        val deadline = System.currentTimeMillis() + USER_COUNT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if ((userAccountManager.authenticatedUsers?.size ?: 0) == expectedCount) return
            Thread.sleep(POLL_INTERVAL_MS)
        }
        val actual = userAccountManager.authenticatedUsers?.size ?: 0
        throw AssertionError(
            "Timed out waiting for authenticated user count $expectedCount; found $actual"
        )
    }

    companion object {
        private const val USER_COUNT_TIMEOUT_MS = 30_000L
        private const val POLL_INTERVAL_MS = 250L
    }
}
