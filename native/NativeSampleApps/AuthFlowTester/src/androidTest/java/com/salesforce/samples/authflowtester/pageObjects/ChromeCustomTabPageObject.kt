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
package com.salesforce.samples.authflowtester.pageObjects

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.ui.components.LoginViewTestTags
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig.ADVANCED_AUTH
import com.salesforce.samples.authflowtester.testUtility.KnownUserConfig
import com.salesforce.samples.authflowtester.testUtility.testConfig

/**
 * Short timeout for checking optional local Chrome UI elements that either appear
 * immediately or not at all (e.g., first-run dialogs, password save prompts).
 * These are not dependent on server-side rendering.
 */
private const val QUICK_CHECK_TIMEOUT_MS = 500L

/**
 * Ceiling for clearing Chrome's First Run Experience, which on a cold profile (every FTL device) is
 * a slow multi-page sequence. Longer than [TIMEOUT_MS]; only fully spent when no tab ever appears.
 */
private const val FRE_DISMISS_TIMEOUT_MS = 30_000L

/**
 * Handles Custom Tab interactions.
 * UiAutomator is required here because the browser (often Chrome) runs in a
 * separate process that Espresso and Compose Test APIs cannot access.
 */
class ChromeCustomTabPageObject(composeTestRule: ComposeTestRule): LoginPageObject(composeTestRule) {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    override fun login(knownLoginHostConfig: KnownLoginHostConfig, knownUserConfig: KnownUserConfig) {
        skipGoogleSignIn()
        val (username, password) = testConfig.getUser(knownLoginHostConfig, knownUserConfig)
        setUsername(username)
        tapLogin()
        setPassword(password)
        tapLogin()
        // Under forced advanced authentication every login completes in the Custom Tab, so the
        // OAuth approval page is always rendered there regardless of the configured host.
        AuthorizationPageObject(composeTestRule).tapAllowAfterLogin(ADVANCED_AUTH)
    }

    override fun welcomeLogin(knownLoginHostConfig: KnownLoginHostConfig, knownUserConfig: KnownUserConfig) {
        skipGoogleSignIn()
        val (_, password) = testConfig.getUser(knownLoginHostConfig, knownUserConfig)
        // The OAuth login_hint already pre-filled the username; advance, enter password, submit.
        tapLogin()
        setPassword(password)
        tapLogin()
        AuthorizationPageObject(composeTestRule).tapAllowAfterLogin(ADVANCED_AUTH)
    }

    /**
     * Opens the top bar overflow menu and taps the "Login for Admins" item.
     *
     * Overrides [LoginPageObject.tapLoginForAdminsMenuItem] to handle the case where the login
     * server picker is showing after [backOutToLoginActivity] closed the forced-advanced-auth tab.
     * The picker is non-dismissable except by selecting a server.  Selecting the current server
     * calls [LoginViewModel.reloadWebView], which checks [SalesforceSDKManager.isBrowserLoginEnabled]
     * to decide whether to launch a Custom Tab or load the in-app WebView.  We ensure the flag is
     * false before the tap so the reload uses the WebView path and the picker is dismissed without
     * launching another tab — [waitForLoginScreen] then confirms the top app bar is reachable.
     */
    override fun tapLoginForAdminsMenuItem() {
        val pickerShowing = composeTestRule
            .onAllNodesWithTag(LoginViewTestTags.SERVER_PICKER)
            .fetchSemanticsNodes()
            .isNotEmpty()
        if (pickerShowing) {
            val currentUrl = SalesforceSDKManager.getInstance()
                .loginServerManager.selectedLoginServer?.url
            if (currentUrl != null) {
                // Belt-and-suspenders: ensure browser-login is off before the row tap so that the
                // reloadWebView() call inside onNewLoginServerSelected loads the WebView rather than
                // relaunching a Custom Tab (which would show the picker again when closed).
                SalesforceSDKManager.getInstance().run {
                    forceAdvancedAuthentication = false
                    isBrowserLoginEnabled = false
                }
                composeTestRule
                    .onAllNodesWithText(currentUrl, substring = true)
                    .filterToOne(hasClickAction())
                    .performClick()
                composeTestRule.waitForIdle()
                // Picker is now dismissed (showServerPicker.value = false). Wait for the
                // MORE_OPTIONS_BUTTON to appear in the top app bar before calling super.
                waitForLoginScreen()
            }
        }
        super.tapLoginForAdminsMenuItem()
    }

    /**
     * Surfaces the LoginActivity (or the server picker) by closing the Custom Tab that forced
     * advanced auth auto-launches over it. The login picker is non-dismissable, so callers that
     * need the top bar (e.g. [changeServerByUrl]) must select a server from the picker first;
     * callers that need Login Options can use the picker's dev-support button via
     * [LoginPageObject.openLoginOptions]. This method only closes the tab and waits for Compose
     * to be ready — it does NOT attempt to dismiss the picker.
     */
    override fun backOutToLoginActivity() {
        skipGoogleSignIn()
        val closeButton = device.findObject(
            UiSelector().resourceId("com.android.chrome:id/close_button")
        )
        if (!closeButton.waitForExists(TIMEOUT_MS)) {
            return
        }
        closeButton.click()
        // Wait for either the picker or the top bar to be reachable.
        try {
            composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
                composeTestRule.onAllNodesWithTag(LoginViewTestTags.SERVER_PICKER)
                    .fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithTag(LoginViewTestTags.MORE_OPTIONS_BUTTON)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        } catch (_: ComposeTimeoutException) {
            // Best-effort; caller action will fail with a clear message if neither is reachable.
        }
        composeTestRule.waitForIdle()
    }

    override fun setUsername(name: String) {
        // UiSelector.resourceId("username") matches Android View resource IDs, not HTML element
        // IDs inside Chrome — the quick check is a low-cost probe before the full wait.
        val usernameField = device.findObject(UiSelector().resourceId(USERNAME_ID))
            .takeIf { it.waitForExists(QUICK_CHECK_TIMEOUT_MS) }
            ?: device.findObject(UiSelector().className("android.widget.EditText").instance(0))
                .also {
                    // Use the extended WebView timeout: the Salesforce login page can take
                    // 20–30 s to render the first input field after the tab toolbar appears.
                    if (!it.waitForExists(WEBVIEW_ACTION_TIMEOUT_MS)) {
                        throw AssertionError("Username field not found in Custom Tab")
                    }
                }
        usernameField.click()
        usernameField.setText(name)
    }

    override fun setPassword(password: String) {
        val passwordField = device.findObject(UiSelector().resourceId(PASSWORD_ID))
            .takeIf { it.waitForExists(QUICK_CHECK_TIMEOUT_MS) }
            ?: device.findObject(UiSelector().className("android.widget.EditText").instance(0))
                .also {
                    if (!it.waitForExists(WEBVIEW_ACTION_TIMEOUT_MS)) {
                        throw AssertionError("Password field not found in Custom Tab")
                    }
                }
        passwordField.click()
        passwordField.setText(password)
    }

    override fun tapLogin() {
        val loginButton = device.findObject(UiSelector().resourceId(LOGIN_BUTTON_ID))
            .takeIf { it.waitForExists(QUICK_CHECK_TIMEOUT_MS) }
            ?: device.findObject(UiSelector().className("android.widget.Button").textContains("Log In"))
                .also {
                    if (!it.waitForExists(TIMEOUT_MS)) {
                        throw AssertionError("Log In button not found in Custom Tab")
                    }
                }
        loginButton.click()
    }

    /**
     * Clears Chrome's First Run Experience so the Custom Tab can render. On a cold Chrome (every FTL
     * device, since `clearPackageData=true` wipes Chrome per test) the tab's first launch is covered
     * by a multi-page FRE that renders slowly, hiding the tab toolbar every tab-facing helper keys
     * off. Loops until the tab is in front, dismissing whichever FRE control shows each pass. Called
     * first by every entry point that touches the tab, and idempotent: a warm Chrome returns at once.
     */
    fun skipGoogleSignIn() {
        val deadline = System.currentTimeMillis() + FRE_DISMISS_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (isCustomTabDisplayed()) {
                return
            }
            if (!dismissOneFreControl()) {
                // Nothing to dismiss yet; the next FRE page may still be rendering, so re-check.
                device.waitForIdle(QUICK_CHECK_TIMEOUT_MS)
            }
        }
    }

    /**
     * Dismisses a single FRE control if one is on screen, returning true if it clicked something.
     * Decline buttons are tried before accept buttons so the flow advances without a Google sign-in;
     * each is matched by resource id first, then by its en-locale label (FTL pins `locale=en`).
     */
    private fun dismissOneFreControl(): Boolean {
        val dismissByIdOrText = listOf(
            "com.android.chrome:id/signin_fre_dismiss_button",
            "com.android.chrome:id/negative_button",
            // Newer Chrome FRE "Set Chrome as default" page uses skip_button.
            "com.android.chrome:id/skip_button",
        )
        for (resourceId in dismissByIdOrText) {
            val button = device.findObject(UiSelector().resourceId(resourceId))
            if (button.waitForExists(QUICK_CHECK_TIMEOUT_MS)) {
                button.click()
                return true
            }
        }
        for (label in listOf("Use without an account", "No thanks", "No Thanks", "Skip", "Not now")) {
            val button = device.findObject(UiSelector().textContains(label))
            if (button.exists()) {
                button.click()
                return true
            }
        }

        // The initial UMA/ToS page has no decline option; accepting it is required to advance.
        val acceptByIdOrText = listOf(
            "com.android.chrome:id/terms_accept",
            "com.android.chrome:id/positive_button",
        )
        for (resourceId in acceptByIdOrText) {
            val button = device.findObject(UiSelector().resourceId(resourceId))
            if (button.waitForExists(QUICK_CHECK_TIMEOUT_MS)) {
                button.click()
                return true
            }
        }
        for (label in listOf("Accept & continue", "Got it")) {
            val button = device.findObject(UiSelector().textContains(label))
            if (button.exists()) {
                button.click()
                return true
            }
        }

        return false
    }

    /**
     * True when a Chrome Custom Tab is currently in front, detected via its close button.
     * Used by negative tests to assert the user remained in the auth flow (tab) rather than
     * advancing into the app.
     */
    fun isCustomTabDisplayed(): Boolean {
        val closeButton = device.findObject(
            UiSelector().resourceId("com.android.chrome:id/close_button")
        )
        return closeButton.waitForExists(QUICK_CHECK_TIMEOUT_MS)
    }

    /**
     * True when the Custom Tab is showing the OAuth error page produced by a DPoP-enforced ECA that
     * rejected an unbound `/authorize` request. The enforced server responds with
     * `error=invalid_request&error_description=missing required dpop_jkt for code binding`, which
     * Chrome renders as page text. We match on the URL-encoded `dpop_jkt` token — the distinctive,
     * server-stable part of the description — rather than the full phrase, which is URL-encoded in the
     * rendered text and could be reworded server-side. Uses [WEBVIEW_ACTION_TIMEOUT_MS] because the
     * error page is server-rendered and subject to the same latency as the login form.
     */
    fun isShowingDPoPBindingError(): Boolean {
        val errorText = device.findObject(
            UiSelector().packageName("com.android.chrome").textContains("dpop_jkt")
        )
        return errorText.waitForExists(WEBVIEW_ACTION_TIMEOUT_MS)
    }

    /**
     * Waits up to [timeoutMs] for the Custom Tab to come to the front, returning whether it did.
     * Uses the full [TIMEOUT_MS] window (vs [isCustomTabDisplayed]) to wait out the async auth-config
     * fetch preceding a tab (re)launch. Best-effort: `false` means no tab launched, so the caller
     * proceeds as if already on the LoginActivity.
     */
    fun waitForCustomTab(timeoutMs: Long = TIMEOUT_MS): Boolean {
        // Clear the FRE first; until it is gone it covers the tab toolbar (the close button).
        skipGoogleSignIn()
        val closeButton = device.findObject(
            UiSelector().resourceId("com.android.chrome:id/close_button")
        )
        return closeButton.waitForExists(timeoutMs)
    }
}
