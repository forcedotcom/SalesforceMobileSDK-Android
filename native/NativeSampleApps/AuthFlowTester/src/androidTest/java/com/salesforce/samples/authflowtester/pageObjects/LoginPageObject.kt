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
import com.salesforce.androidsdk.R
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig
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
class LoginPageObject(
    composeTestRule: ComposeTestRule,
    val isAdvancedAuth: Boolean = false,
): BasePageObject(composeTestRule) {

    fun login(knownLoginHostConfig: KnownLoginHostConfig, knownUserConfig: KnownUserConfig) {
        val (username, password) = testConfig.getUser(knownLoginHostConfig, knownUserConfig)
        setUsername(username)
        setPassword(password)
        tapLogin()

        if (isAdvancedAuth) {
            CustomTabPageObject().handleSignIn()
        }

        AuthorizationPageObject(composeTestRule).tapAllowAfterLogin()
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

        // Tap "Login Options" in the native AlertDialog (not Compose)
        onView(withText(getString(R.string.sf__dev_support_login_options_title)))
            .inRoot(isDialog())
            .perform(click())
        composeTestRule.waitForIdle()
    }

    private fun setUsername(name: String) {
        retryWebAction {
            onWebView().withElement(findElement(Locator.ID, USERNAME_ID))
                .perform(clearElement())
                .perform(webKeys(name))
        }
    }

    private fun setPassword(password: String) {
        retryWebAction {
            onWebView().withElement(findElement(Locator.ID, PASSWORD_ID))
                .perform(clearElement())
                .perform(webKeys(password))
        }
    }

    private fun tapLogin() {
        retryWebAction {
            onWebView().withElement(findElement(Locator.ID, LOGIN_BUTTON_ID))
                .perform(webClick())
        }
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
