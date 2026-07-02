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

private const val RETRY_COUNT = 3
/**
 * Short timeout for checking optional local Chrome UI elements that either appear
 * immediately or not at all (e.g., first-run dialogs, password save prompts).
 * These are not dependent on server-side rendering.
 */
private const val QUICK_CHECK_TIMEOUT_MS = 500L

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
     * Forced advanced authentication auto-launches a Chrome Custom Tab over the Compose
     * LoginActivity, so its top bar (and the "More Options" menu the [LoginPageObject] Compose
     * actions depend on) is not reachable while the tab is in front. This backs out of the tab
     * to surface the LoginActivity again.
     *
     * Backing out returns `RESULT_CANCELED`, which the SDK handles by clearing the WebView and
     * raising the login-server-picker bottom sheet (`clearWebView(showServerPicker = true)`). The
     * picker's scrim sits over the top bar, so it is dismissed here as well, leaving the bare
     * LoginActivity ready for a Compose top-bar action (Login Options, Change Server, Login for
     * Admins). After that action changes the dynamic config or server, the SDK re-launches the
     * Custom Tab (debug `loginDevMenuReload` -> `reloadWebView`) and login completes in the tab.
     *
     * The Custom Tab launches asynchronously (after the auth-config network fetch resolves and the
     * OAuth URL is generated), so this waits up to [TIMEOUT_MS] for the tab to appear before
     * backing out. No-op when no Custom Tab appears within the timeout (we are already on the
     * LoginActivity).
     */
    override fun backOutToLoginActivity() {
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

    fun skipGoogleSignIn() {
        val continueButton = device.findObject(
            UiSelector().resourceId("com.android.chrome:id/signin_fre_dismiss_button")
        )
        val noButton = device.findObject(
            UiSelector().resourceId("com.android.chrome:id/negative_button")
        )
        val legacyContinueButton = device.findObject(
            UiSelector().resourceId("com.android.chrome:id/terms_accept")
        )

        repeat(times = RETRY_COUNT) {
            if (continueButton.waitForExists(QUICK_CHECK_TIMEOUT_MS)) {
                continueButton.click()
                return@repeat
            } else if (legacyContinueButton.waitForExists(QUICK_CHECK_TIMEOUT_MS)) {
                legacyContinueButton.click()
                return@repeat
            }
        }

        if (noButton.waitForExists(QUICK_CHECK_TIMEOUT_MS)) {
            noButton.click()
            return
        }
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
     * Waits up to [timeoutMs] for a Chrome Custom Tab to come to the front (detected via its close
     * button) and returns whether it appeared.
     *
     * Unlike [isCustomTabDisplayed] (which uses [QUICK_CHECK_TIMEOUT_MS] for tabs that either show
     * immediately or not at all), this uses the full [TIMEOUT_MS] window so it can wait out the
     * asynchronous auth-config fetch that precedes a tab (re)launch under forced advanced
     * authentication. Best-effort: a `false` return simply means no tab launched within the
     * window, in which case the caller proceeds as if already on the LoginActivity.
     */
    fun waitForCustomTab(timeoutMs: Long = TIMEOUT_MS): Boolean {
        val closeButton = device.findObject(
            UiSelector().resourceId("com.android.chrome:id/close_button")
        )
        return closeButton.waitForExists(timeoutMs)
    }
}
