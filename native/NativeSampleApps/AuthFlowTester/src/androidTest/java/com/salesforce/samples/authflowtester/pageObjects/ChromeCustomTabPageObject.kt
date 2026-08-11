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
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.salesforce.androidsdk.R
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
     * Surfaces the LoginActivity by backing out of the Custom Tab that forced advanced auth
     * auto-launches over it (the tab hides the Compose top bar the [LoginPageObject] actions need).
     * Backing out returns `RESULT_CANCELED`, which the SDK handles by raising the server-picker
     * bottom sheet; that is dismissed here too so a subsequent top-bar action can re-launch the tab.
     * Waits up to [TIMEOUT_MS] for the async tab launch; no-op if no tab appears (already on the
     * LoginActivity).
     */
    override fun backOutToLoginActivity() {
        // Clear the FRE first; until it is gone it covers the tab toolbar (the close button).
        skipGoogleSignIn()
        val closeButton = device.findObject(
            UiSelector().resourceId("com.android.chrome:id/close_button")
        )
        if (!closeButton.waitForExists(TIMEOUT_MS)) {
            return
        }
        closeButton.click()
        dismissServerPickerIfPresent()
    }

    /** Dismisses the login-server-picker bottom sheet via its Close button, if it is showing. */
    private fun dismissServerPickerIfPresent() {
        val closeDescription = getString(R.string.sf__server_close_button_content_description)
        val appeared = try {
            composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
                composeTestRule.onAllNodesWithContentDescription(closeDescription)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            true
        } catch (_: ComposeTimeoutException) {
            // The picker never appeared (e.g. shared browser session re-auth); nothing to dismiss.
            false
        }

        if (appeared) {
            composeTestRule.onNodeWithContentDescription(closeDescription).performClick()
            composeTestRule.waitForIdle()
        }
    }

    override fun setUsername(name: String) {
        var usernameField = device.findObject(UiSelector().resourceId(USERNAME_ID))
        if (!usernameField.waitForExists(TIMEOUT_MS)) {
            usernameField = device.findObject(
                UiSelector().className("android.widget.EditText").instance(0)
            )
            if (!usernameField.waitForExists(TIMEOUT_MS)) {
                throw AssertionError("Username field not found in Custom Tab")
            }
        }
        usernameField.click()
        usernameField.setText(name)
    }

    override fun setPassword(password: String) {
        var passwordField = device.findObject(UiSelector().resourceId(PASSWORD_ID))
        if (!passwordField.waitForExists(TIMEOUT_MS)) {
            passwordField = device.findObject(
                UiSelector().className("android.widget.EditText").instance(0)
            )
            if (!passwordField.waitForExists(TIMEOUT_MS)) {
                throw AssertionError("Password field not found in Custom Tab")
            }
        }
        passwordField.click()
        passwordField.setText(password)
    }

    override fun tapLogin() {
        var loginButton = device.findObject(UiSelector().resourceId(LOGIN_BUTTON_ID))
        if (!loginButton.waitForExists(TIMEOUT_MS)) {
            loginButton = device.findObject(
                UiSelector().className("android.widget.Button").textContains("Log In")
            )
            if (!loginButton.waitForExists(TIMEOUT_MS)) {
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
