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
package com.salesforce.samples.authflowtester.testUtility

import android.Manifest
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.samples.authflowtester.AuthFlowTesterActivity
import com.salesforce.samples.authflowtester.pageObjects.AuthFlowTesterPageObject
import com.salesforce.samples.authflowtester.pageObjects.AuthorizationPageObject
import com.salesforce.samples.authflowtester.pageObjects.LoginOptionsPageObject
import com.salesforce.samples.authflowtester.pageObjects.ChromeCustomTabPageObject
import com.salesforce.samples.authflowtester.pageObjects.LoginPageObject
import com.salesforce.samples.authflowtester.testUtility.ScopeSelection.EMPTY
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig.REGULAR_AUTH
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig.ADVANCED_AUTH
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.CA_OPAQUE
import org.junit.After
import org.junit.Rule

abstract class AuthFlowTest {
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @get:Rule(order = 2)
    val activityRule = ActivityScenarioRule(AuthFlowTesterActivity::class.java)

    val loginOptions = LoginOptionsPageObject(composeTestRule)
    val app = AuthFlowTesterPageObject(composeTestRule)

    val user: KnownUserConfig by lazy {
        val minSdk = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationInfo.minSdkVersion
        val userNumber = (Build.VERSION.SDK_INT - minSdk) % KnownUserConfig.values().count()
        KnownUserConfig.values()[userNumber]
    }

    // For MultiUser tests
    val otherUser: KnownUserConfig by lazy {
        val userNumber = (user.ordinal + 1) % KnownUserConfig.values().count()
        KnownUserConfig.values()[userNumber]
    }

    @After
    open fun cleanup() {
        with(SalesforceSDKManager.getInstance()) {
            // Reset Welcome Discovery simulation between tests (mirrors iOS tearDown).
            simulatedDiscoveryResult = null
            userAccountManager.authenticatedUsers?.forEach { userAccount ->
                logout(
                    account = userAccountManager.buildAccount(userAccount),
                    frontActivity = null,
                    showLoginPage = false,
                )
            }
        }
    }

    /**
     * Ensures we're on REGULAR_AUTH server before opening Login Options.
     * Server selection is "sticky" so previous test might have left it on ADVANCED_AUTH.
     */
    private fun ensureRegularAuthServer() {
        // Close any Chrome Custom Tab that might be open from previous test
        val chromeTab = ChromeCustomTabPageObject(composeTestRule)
        if (chromeTab.tapCloseButton()) {
            Thread.sleep(500)  // Wait for Chrome tab to close
        }

        // Switch back to REGULAR_AUTH using LoginServerManager
        val regularAuthUrl = testConfig.getLoginHost(REGULAR_AUTH).url
        val loginServerManager = SalesforceSDKManager.getInstance().loginServerManager
        val regularAuthServer = loginServerManager.getLoginServerFromURL(regularAuthUrl)
        if (regularAuthServer != null) {
            loginServerManager.setSelectedLoginServer(regularAuthServer)
            Thread.sleep(500)  // Wait for server change to take effect
        }
    }

    open fun loginAndValidate(
        knownAppConfig: KnownAppConfig,
        scopeSelection: ScopeSelection = EMPTY,
        useWebServerFlow: Boolean = true,
        useHybridAuthToken: Boolean = true,
        knownLoginHostConfig: KnownLoginHostConfig = REGULAR_AUTH,
        knownUserConfig: KnownUserConfig = user,
        useWelcomeDiscovery: Boolean = false,
    ) {
        val loginPage = when(knownLoginHostConfig) {
            REGULAR_AUTH -> LoginPageObject(composeTestRule)
            ADVANCED_AUTH -> ChromeCustomTabPageObject(composeTestRule)
        }

        ensureRegularAuthServer()

        val needsLoginOptions = !useWebServerFlow || !useHybridAuthToken ||
                knownAppConfig != CA_OPAQUE || scopeSelection != EMPTY ||
                useWelcomeDiscovery

        if (needsLoginOptions) {

            loginPage.openLoginOptions()

            if (!useWebServerFlow) {
                loginOptions.disableWebServerFlow()
            }

            if (!useHybridAuthToken) {
                loginOptions.disableHybridAuthToken()
            }

            // Set simulated discovery result first - its Save does NOT dismiss the activity,
            // unlike the boot-config Save below which calls activity.finish().
            if (useWelcomeDiscovery) {
                val (username, _) = testConfig.getUser(knownLoginHostConfig, knownUserConfig)
                val targetHost = testConfig.getLoginHost(knownLoginHostConfig).url
                    .removePrefix("https://").removePrefix("http://")
                loginOptions.setSimulatedDiscoveryResult(
                    loginHost = targetHost,
                    username = username,
                )
            }

            if (knownAppConfig == CA_OPAQUE && scopeSelection == EMPTY) {
                // No boot config override needed; nothing to save in that section.
                Espresso.pressBack()
            } else {
                // setOverrideBootConfig taps Save which calls activity.finish().
                loginOptions.setOverrideBootConfig(knownAppConfig, scopeSelection)
            }
        }

        if (useWelcomeDiscovery) {
            // Drive the flow through the SDK's server picker via Welcome Discovery URL.
            // The SDK's switchDefaultOrSalesforceWelcomeDiscoveryLogin path consumes the
            // armed simulatedDiscoveryResult and routes the OAuth authorize URL to the
            // simulated host with the simulated login_hint.  We then complete login with
            // the standard flow (which retypes the username; the pre-fill is exercised
            // server-side via the OAuth login_hint parameter).
            val webViewLoginPage = LoginPageObject(composeTestRule)
            webViewLoginPage.changeServerByUrl(WELCOME_DISCOVERY_URL)

            // The simulated host determines the surface: regular_auth -> in-app WebView,
            // advanced_auth -> Chrome Custom Tab.  In both cases the OAuth login_hint
            // already pre-filled the username; only the password step remains.
            val welcomeLoginPage: LoginPageObject = when (knownLoginHostConfig) {
                REGULAR_AUTH -> webViewLoginPage
                ADVANCED_AUTH -> {
                    val chrome = ChromeCustomTabPageObject(composeTestRule)
                    chrome.skipGoogleSignIn()
                    chrome
                }
            }
            welcomeLoginPage.welcomeLogin(knownLoginHostConfig, knownUserConfig)
        } else {
            if (knownLoginHostConfig != REGULAR_AUTH) {
                loginPage.changeServer(knownLoginHostConfig)
            }

            loginPage.login(knownLoginHostConfig, knownUserConfig)
        }
        app.waitForAppLoad()

        app.validateUser(knownLoginHostConfig, knownUserConfig)
        app.validateOAuthValues(knownAppConfig, scopeSelection)
        app.validateApiRequest()
    }

    companion object {
        @VisibleForTesting
        const val WELCOME_DISCOVERY_URL = "https://welcome.salesforce.com/discovery"
    }

    /**
     * Exercises the "Login for Admins" flow: starts on the REGULAR_AUTH server (in-app
     * WebView), opens the overflow menu, taps "Login for Admins" to launch a Chrome
     * Custom Tab, completes login in Chrome, and validates the resulting user/tokens.
     */
    fun adminLoginAndValidate(useWebServerFlow: Boolean = true) {
        val loginPage = LoginPageObject(composeTestRule)
        val chromePage = ChromeCustomTabPageObject(composeTestRule)

        ensureRegularAuthServer()

        loginPage.openLoginOptions()
        if (!useWebServerFlow) {
            loginOptions.disableWebServerFlow()
        }
        loginOptions.setOverrideBootConfig(KnownAppConfig.BEACON_OPAQUE, scopeSelection = EMPTY)

        // Launch the admin custom tab from the WebView login view.
        loginPage.tapLoginForAdminsMenuItem()

        // Complete login in Chrome. User credentials are the REGULAR_AUTH server's users
        // since that is the selected login host; the admin flow just swaps the surface
        // (WebView -> Chrome Custom Tab) without changing the target server.
        chromePage.skipGoogleSignIn()
        val (username, password) = testConfig.getUser(REGULAR_AUTH, user)
        chromePage.setUsername(username)
        chromePage.tapLogin()
        chromePage.setPassword(password)
        chromePage.tapLogin()

        // OAuth approval page is rendered inside the Chrome Custom Tab.
        AuthorizationPageObject(composeTestRule).tapAllowAfterLogin(ADVANCED_AUTH)

        app.waitForAppLoad()
        app.validateUser(REGULAR_AUTH, user)
        app.validateOAuthValues(KnownAppConfig.BEACON_OPAQUE, scopeSelection = EMPTY)
        app.validateApiRequest()
    }

    fun migrateAndValidate(
        knownAppConfig: KnownAppConfig,
        knownLoginHostConfig: KnownLoginHostConfig = REGULAR_AUTH,
        scopeSelection: ScopeSelection = EMPTY,
        knownUserConfig: KnownUserConfig = user,
    ) {
        val (preAccessToken, preRefreshToken) = app.getTokens()
        app.migrateToNewApp(knownAppConfig, scopeSelection)
        val (postAccessToken, postRefreshToken) = app.getTokens()

        // Assert tokens are new
        assert(preAccessToken != postAccessToken)
        assert(preRefreshToken != postRefreshToken)

        app.validateUser(knownLoginHostConfig, knownUserConfig)
        app.validateOAuthValues(knownAppConfig, scopeSelection)

        // Assert new tokens work
        app.revokeAccessToken()
        app.validateApiRequest()
    }

    fun assertRevokeAndRefreshWorks(isRtr: Boolean) {
        val (preAccessToken, preRefreshToken) = app.getTokens()
        app.revokeAccessToken()
        app.validateApiRequest()
        val (postAccessToken, postRefreshToken) = app.getTokens()

        assert(preAccessToken != postAccessToken) { "Access token should have been refreshed" }

        if (isRtr) {
            assert(preRefreshToken != postRefreshToken) { "Refresh token should have rotated (RTR app)" }
        } else {
            assert(preRefreshToken == postRefreshToken) { "Refresh token should not have changed (non-RTR app)" }
        }
    }
}