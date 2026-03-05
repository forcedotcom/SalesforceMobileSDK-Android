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

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.espresso.web.webdriver.DriverAtoms.clearElement
import androidx.test.espresso.web.webdriver.DriverAtoms.findElement
import androidx.test.espresso.web.webdriver.DriverAtoms.webClick
import androidx.test.espresso.web.webdriver.DriverAtoms.webKeys
import androidx.test.espresso.web.webdriver.Locator
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.salesforce.androidsdk.R
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig.ADVANCED_AUTH
import com.salesforce.samples.authflowtester.testUtility.KnownUserConfig
import com.salesforce.samples.authflowtester.testUtility.testConfig

private const val USERNAME_ID = "username"
private const val PASSWORD_ID = "password"
private const val LOGIN_BUTTON_ID = "Login"

/**
 * Page object for the Salesforce login WebView.
 * Uses Espresso WebView APIs since the login form is an in-app WebView
 * embedded via AndroidView in the SDK's LoginActivity Compose layout.
 */
open class LoginPageObject(composeTestRule: ComposeTestRule): BasePageObject(composeTestRule) {

    private val device by lazy { UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()) }

    open fun login(knownLoginHostConfig: KnownLoginHostConfig, knownUserConfig: KnownUserConfig) {
        val (username, password) = testConfig.getUser(knownLoginHostConfig, knownUserConfig)
        setUsername(username)
        setPassword(password)
        tapLogin()
        AuthorizationPageObject(composeTestRule).tapAllowAfterLogin(knownLoginHostConfig)
    }

    fun openLoginOptions() {
        // Tap "More Options" three-dot menu (Compose IconButton)
        composeTestRule.onNodeWithContentDescription(getString(R.string.sf__more_options))
            .performClick()
        composeTestRule.waitForIdle()

        // Tap "Developer Support" dropdown menu item
        composeTestRule.onNodeWithText(getString(R.string.sf__dev_support_title_menu_item))
            .performClick()
        composeTestRule.waitForIdle()

        // Wait for the AlertDialog to be fully rendered and ready
        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
                onView(withText(getString(R.string.sf__dev_support_login_options_title)))
                    .inRoot(isDialog())
                    .check { _, _ -> }
                true
        }

        // Tap "Login Options" in the native AlertDialog (not Compose)
        onView(withText(getString(R.string.sf__dev_support_login_options_title)))
            .inRoot(isDialog())
            .perform(click())

        // Wait for LoginOptionsActivity's Compose content to render.
        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeTestRule.onAllNodesWithContentDescription(
                getString(R.string.sf__login_options_dynamic_config_toggle_content_description)
            ).fetchSemanticsNodes().isNotEmpty()
        }
        Thread.sleep(TIMEOUT_MS / 4)
    }

    fun changeServer(knownLoginHostConfig: KnownLoginHostConfig) {
        val url = testConfig.getLoginHost(knownLoginHostConfig).url

        // Tap "More Options" three-dot menu (Compose IconButton)
        composeTestRule.onNodeWithContentDescription(getString(R.string.sf__more_options))
            .performClick()
        composeTestRule.waitForIdle()

        // Tap "Change Server" dropdown menu item
        composeTestRule.onNodeWithText(getString(R.string.sf__pick_server))
            .performClick()

        // Wait for server picker bottom sheet to appear
        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeTestRule.onAllNodesWithContentDescription(
                getString(R.string.sf__server_picker_content_description)
            ).fetchSemanticsNodes().isNotEmpty()
        }

        // Select the server matching the URL
        composeTestRule.onNodeWithText(url, substring = true).performClick()
        composeTestRule.waitForIdle()
    }

    open fun setUsername(name: String) {
        retryWebAction {
            onWebView().withElement(findElement(Locator.ID, USERNAME_ID))
                .perform(clearElement())
                .perform(webKeys(name))
        }
    }

    open fun setPassword(password: String) {
        retryWebAction {
            onWebView().withElement(findElement(Locator.ID, PASSWORD_ID))
                .perform(clearElement())
                .perform(webKeys(password))
        }
    }

    open fun tapLogin() {
        retryWebAction {
            onWebView().withElement(findElement(Locator.ID, LOGIN_BUTTON_ID))
                .perform(webClick())
        }
    }

    /** Enters credentials and taps login in a Chrome Custom Tab via UIAutomator. */
    private fun loginInCustomTab(username: String, password: String) {
        val usernameField = device.findObject(
            UiSelector().className("android.widget.EditText").instance(0)
        )
        if (!usernameField.waitForExists(TIMEOUT_MS * 5)) {
            throw AssertionError("Username field not found in Custom Tab")
        }
        usernameField.clearTextField()
        usernameField.setText(username)

        val passwordField = device.findObject(
            UiSelector().className("android.widget.EditText").instance(1)
        )
        if (!passwordField.waitForExists(TIMEOUT_MS * 5)) {
            throw AssertionError("Password field not found in Custom Tab")
        }
        passwordField.clearTextField()
        passwordField.setText(password)

        val loginButton = device.findObject(
            UiSelector().className("android.widget.Button").textContains("Log In")
        )
        if (!loginButton.waitForExists(TIMEOUT_MS * 5)) {
            throw AssertionError("Log In button not found in Custom Tab")
        }
        loginButton.click()
    }

    /** Retries a WebView action until it succeeds or times out. */
    private fun <T> retryWebAction(
        timeoutMs: Long = TIMEOUT_MS * 5,
        action: () -> T,
    ): T {
        val endTime = System.currentTimeMillis() + timeoutMs
        var lastException: Exception? = null
        while (System.currentTimeMillis() < endTime) {
            try {
                return action()
            } catch (e: Exception) {
                lastException = e
                Thread.sleep(TIMEOUT_MS / 4)
            }
        }
        throw AssertionError(
            "WebView action failed after ${timeoutMs}ms",
            lastException,
        )
    }
}
