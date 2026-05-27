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
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.samples.authflowtester.pageObjects.LoginPageObject
import com.salesforce.samples.authflowtester.testUtility.AuthFlowTest
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.CA_OPAQUE
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.ECA_OPAQUE
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig.REGULAR_AUTH
import com.salesforce.samples.authflowtester.testUtility.ScopeSelection.EMPTY
import com.salesforce.samples.authflowtester.testUtility.testConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Negative-path tests for runtime consumer-key/dynamic-config selection.
 * Covers invalid consumer keys, invalid scopes, and dynamic-config changes
 * that the user does not commit by logging in.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class NegativeLoginTests : AuthFlowTest() {

    /**
     * Dynamic config with an invalid consumer key. Login must fail and no
     * user account may be created.
     */
    @Test
    fun testInvalidConsumerKey_loginFails() {
        loginAndExpectFailure(
            consumerKey = INVALID_CONSUMER_KEY,
            redirectUri = testConfig.getApp(ECA_OPAQUE).redirectUri,
        )
    }

    /**
     * Dynamic config with an invalid scope. Login must fail and no user
     * account may be created.
     */
    @Test
    fun testInvalidScope_loginFails() {
        loginAndExpectFailure(
            consumerKey = testConfig.getApp(ECA_OPAQUE).consumerKey,
            redirectUri = testConfig.getApp(ECA_OPAQUE).redirectUri,
            scopes = INVALID_SCOPE,
        )
    }

    /**
     * Change dynamic config but do not log in. After a baseline login with
     * the static boot config, opening Login Options and saving a different
     * dynamic configuration without logging in must not affect the existing
     * user's tokens, configuration, or ability to make API calls.
     */
    @Test
    fun testChangeDynamicConfigWithoutLogin_existingUserUnaffected() {
        // Baseline: log in with static config (CA opaque) and snapshot state.
        loginAndValidate(knownAppConfig = CA_OPAQUE)
        val (userAccessToken, userRefreshToken) = app.getTokens()

        // Open the user picker and add a new account, which routes through the
        // login screen. Set a different dynamic config (ECA Opaque) but do not
        // submit credentials.
        app.addNewAccount()
        val loginPage = LoginPageObject(composeTestRule)
        loginPage.openLoginOptions()
        loginOptions.setOverrideBootConfig(ECA_OPAQUE, EMPTY)
        navigateBackToApp()

        // The existing user must remain the only authenticated account.
        val authenticatedUsers =
            SalesforceSDKManager.getInstance().userAccountManager.authenticatedUsers
                ?: emptyList()
        assertEquals(
            "Expected exactly one authenticated user after changing dynamic config without login",
            1, authenticatedUsers.size,
        )
        assertEquals(
            testConfig.getUser(REGULAR_AUTH, user).username,
            authenticatedUsers.first().username,
        )

        // Verify the original user's tokens and config are still intact and
        // a refresh succeeds. navigateBackToApp() above already waited for
        // the AuthFlowTester main screen to reload.
        app.validateUser(REGULAR_AUTH, user)
        app.validateOAuthValues(knownAppConfig = CA_OPAQUE, scopeSelection = EMPTY)
        val (postAccessToken, postRefreshToken) = app.getTokens()
        assertEquals(userAccessToken, postAccessToken)
        assertEquals(userRefreshToken, postRefreshToken)

        app.revokeAccessToken()
        app.validateApiRequest()
    }

    /**
     * Press back repeatedly, polling for the AuthFlowTester main screen to
     * become visible. Uses UiAutomator's device-level back press rather
     * than Espresso's because the LoginActivity launched from the user
     * picker via [com.salesforce.androidsdk.accounts.UserAccountManager
     * .switchToNewUser] sits in its own task and Espresso treats
     * finishing-the-only-activity-in-a-task as "killed the app." The
     * AuthFlowTesterActivity in the original task simply resumes.
     */
    private fun navigateBackToApp() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        repeat(BACK_PRESS_LIMIT) {
            if (app.isAppLoaded()) return
            device.pressBack()

            val deadline = System.currentTimeMillis() + PER_BACK_PRESS_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                if (app.isAppLoaded()) return
                Thread.sleep(POLL_INTERVAL_MS)
            }
        }
        if (!app.isAppLoaded()) {
            throw AssertionError(
                "Failed to return to AuthFlowTester main screen after " +
                    "$BACK_PRESS_LIMIT back presses"
            )
        }
    }

    companion object {
        // Deliberately malformed consumer key. Salesforce returns
        // invalid_client_id for any unknown 3MVG... key.
        private const val INVALID_CONSUMER_KEY = "3MVG_invalid_consumer_key_for_negative_tests"

        // Scope name that does not match any Salesforce OAuth scope. Server
        // returns invalid_scope.
        private const val INVALID_SCOPE = "invalid_scope_for_negative_tests"

        // Maximum number of back-presses to walk from a saved-but-unused
        // dynamic config back to the AuthFlowTester main screen.
        // LoginOptions has been dismissed by Save, so worst-case stack is
        // LoginActivity -> AccountSwitcher -> AuthFlowTester (2 presses);
        // an extra press accommodates devices that are slow to dismiss
        // dialogs or transitions.
        private const val BACK_PRESS_LIMIT = 4
        private const val PER_BACK_PRESS_TIMEOUT_MS = 3_000L
        private const val POLL_INTERVAL_MS = 250L
    }
}
