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
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig
import com.salesforce.samples.authflowtester.testUtility.KnownUserConfig

private const val RETRY_COUNT = 3

/**
 * Handles Custom Tab interactions.
 * UiAutomator is required here because the browser (often Chrome) runs in a
 * separate process that Espresso and Compose Test APIs cannot access.
 */
class ChromeCustomTabPageObject(composeTestRule: ComposeTestRule): LoginPageObject(composeTestRule) {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    override fun login(knownLoginHostConfig: KnownLoginHostConfig, knownUserConfig: KnownUserConfig) {
        skipGoogleSignIn()
        super.login(knownLoginHostConfig, knownUserConfig)
    }

    override fun setUsername(name: String) {
        val usernameField = device.findObject(
            UiSelector().className("android.widget.EditText").instance(0)
        )
        if (!usernameField.waitForExists(TIMEOUT_MS)) {
            throw AssertionError("Username field not found in Custom Tab")
        }
        usernameField.clearTextField()
        usernameField.setText(name)
    }

    override fun setPassword(password: String) {
        val passwordField = device.findObject(
            UiSelector().className("android.widget.EditText").instance(1)
        )
        if (!passwordField.waitForExists(TIMEOUT_MS)) {
            throw AssertionError("Password field not found in Custom Tab")
        }
        passwordField.clearTextField()
        passwordField.setText(password)
    }

    override fun tapLogin() {
        val loginButton = device.findObject(
            UiSelector().className("android.widget.Button").textContains("Log In")
        )
        if (!loginButton.waitForExists(TIMEOUT_MS)) {
            throw AssertionError("Log In button not found in Custom Tab")
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
            if (continueButton.waitForExists(TIMEOUT_MS)) {
                continueButton.click()
                return@repeat
            } else if (legacyContinueButton.waitForExists(TIMEOUT_MS)) {
                legacyContinueButton.click()
                return@repeat
            }
        }

        if (noButton.waitForExists(TIMEOUT_MS)) {
            noButton.click()
            return
        }
    }

    fun tapCloseButton() {
        val closeButton = device.findObject(
            UiSelector().resourceId("com.android.chrome:id/close_button")
        )
        if (closeButton.waitForExists(TIMEOUT_MS)) {
            closeButton.click()
        }
    }
}
