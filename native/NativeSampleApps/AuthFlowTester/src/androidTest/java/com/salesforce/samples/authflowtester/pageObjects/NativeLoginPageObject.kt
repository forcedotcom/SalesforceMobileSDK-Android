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

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso.closeSoftKeyboard
import com.salesforce.samples.authflowtester.AUTH_MODE_BROWSER_TAG
import com.salesforce.samples.authflowtester.AUTH_MODE_NATIVE_TAG
import com.salesforce.samples.authflowtester.AuthMode
import com.salesforce.samples.authflowtester.BROWSER_LOGIN_OPTIONS_BUTTON_TAG
import com.salesforce.samples.authflowtester.LOGIN_OPTIONS_APPLY_TAG
import com.salesforce.samples.authflowtester.NATIVE_BROWSER_FALLBACK_TAG
import com.salesforce.samples.authflowtester.NATIVE_CALLBACK_URL_TAG
import com.salesforce.samples.authflowtester.NATIVE_CANCEL_BUTTON_TAG
import com.salesforce.samples.authflowtester.NATIVE_CLIENT_ID_TAG
import com.salesforce.samples.authflowtester.NATIVE_COMMUNITY_URL_TAG
import com.salesforce.samples.authflowtester.NATIVE_DIAGNOSTICS_TAG
import com.salesforce.samples.authflowtester.NATIVE_DPOP_TOGGLE_TAG
import com.salesforce.samples.authflowtester.NATIVE_ERROR_DETAIL_TAG
import com.salesforce.samples.authflowtester.NATIVE_LOADING_TAG
import com.salesforce.samples.authflowtester.NATIVE_LOGIN_BUTTON_TAG
import com.salesforce.samples.authflowtester.NATIVE_LOGIN_OPTIONS_TAG
import com.salesforce.samples.authflowtester.NATIVE_PASSWORD_TAG
import com.salesforce.samples.authflowtester.NATIVE_RESULT_TAG
import com.salesforce.samples.authflowtester.NATIVE_USERNAME_TAG
import com.salesforce.samples.authflowtester.testUtility.AppConfig
import com.salesforce.samples.authflowtester.testUtility.LoginHost
import com.salesforce.samples.authflowtester.testUtility.User

private const val NATIVE_SCREEN_TIMEOUT_MS = 30_000L
private const val NATIVE_LOGIN_TIMEOUT_MS = 60_000L

/**
 * Page object for the AuthFlowTester's own Native Login form and Login Options screen, both
 * Compose screens addressed by test tag (shared with the iOS accessibility identifiers).
 */
class NativeLoginPageObject(composeTestRule: ComposeTestRule) : BasePageObject(composeTestRule) {

    private fun exists(tag: String) = try {
        composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    } catch (_: IllegalStateException) {
        false // Compose hierarchy temporarily unavailable
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = NATIVE_SCREEN_TIMEOUT_MS) {
        try {
            composeTestRule.waitUntil(timeoutMillis) { exists(tag) }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("Timed out after ${timeoutMillis}ms waiting for '$tag'", e)
        }
    }

    private fun tap(tag: String) {
        waitForTag(tag)
        composeTestRule.onNodeWithTag(tag).performScrollTo().performClick()
        composeTestRule.waitForIdle()
    }

    private fun text(tag: String): String =
        composeTestRule.onNodeWithTag(tag).fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString("") { it.text }

    // region Native Login form

    fun waitForNativeLogin() = waitForTag(NATIVE_LOGIN_BUTTON_TAG)

    fun isNativeLoginShowing() = exists(NATIVE_LOGIN_BUTTON_TAG)

    fun hasCancelButton() = exists(NATIVE_CANCEL_BUTTON_TAG)

    fun enterCredentials(user: User) {
        waitForNativeLogin()
        composeTestRule.onNodeWithTag(NATIVE_USERNAME_TAG).performTextReplacement(user.username)
        composeTestRule.onNodeWithTag(NATIVE_PASSWORD_TAG).performTextReplacement(user.password)
        closeSoftKeyboard()
        composeTestRule.waitForIdle()
    }

    fun enterUsernameOnly(username: String) {
        waitForNativeLogin()
        composeTestRule.onNodeWithTag(NATIVE_USERNAME_TAG).performTextReplacement(username)
        closeSoftKeyboard()
    }

    fun usernameFieldText(): String =
        composeTestRule.onNodeWithTag(NATIVE_USERNAME_TAG).fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text

    /** True when the password field holds text. The value itself is never read into test output. */
    fun passwordFieldHasText(): Boolean =
        composeTestRule.onNodeWithTag(NATIVE_PASSWORD_TAG).fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text.isNotEmpty()

    fun tapLogin() = tap(NATIVE_LOGIN_BUTTON_TAG)

    fun tapCancel() = tap(NATIVE_CANCEL_BUTTON_TAG)

    fun tapBrowserFallback() = tap(NATIVE_BROWSER_FALLBACK_TAG)

    fun isLoading() = exists(NATIVE_LOADING_TAG)

    fun waitForLoading(timeoutMillis: Long = NATIVE_SCREEN_TIMEOUT_MS) = waitForTag(NATIVE_LOADING_TAG, timeoutMillis)

    /** Waits for the rejected state and returns its error detail, e.g. `InvalidCredentials`. */
    fun waitForRejection(timeoutMillis: Long = NATIVE_LOGIN_TIMEOUT_MS): String {
        waitForTag(NATIVE_RESULT_TAG, timeoutMillis)
        return text(NATIVE_ERROR_DETAIL_TAG)
    }

    fun isRejected() = exists(NATIVE_RESULT_TAG)

    fun diagnostics(): String = if (exists(NATIVE_DIAGNOSTICS_TAG)) text(NATIVE_DIAGNOSTICS_TAG) else ""

    // endregion

    // region Login Options

    fun openLoginOptions() = tap(NATIVE_LOGIN_OPTIONS_TAG)

    fun selectMode(mode: AuthMode) = tap(
        if (mode == AuthMode.NATIVE) AUTH_MODE_NATIVE_TAG else AUTH_MODE_BROWSER_TAG,
    )

    fun setNativeSettings(app: AppConfig, host: LoginHost) {
        waitForTag(NATIVE_CLIENT_ID_TAG)
        setField(NATIVE_CLIENT_ID_TAG, app.consumerKey)
        setField(NATIVE_CALLBACK_URL_TAG, app.redirectUri)
        setField(NATIVE_COMMUNITY_URL_TAG, host.url)
    }

    fun setDPoP(enabled: Boolean) {
        waitForTag(NATIVE_DPOP_TOGGLE_TAG)
        val node = composeTestRule.onNodeWithTag(NATIVE_DPOP_TOGGLE_TAG).performScrollTo()
        val isOn = node.fetchSemanticsNode().config[SemanticsProperties.ToggleableState] == ToggleableState.On
        if (isOn != enabled) node.performClick()
        composeTestRule.waitForIdle()
    }

    fun openBrowserLoginOptions() = tap(BROWSER_LOGIN_OPTIONS_BUTTON_TAG)

    fun apply() = tap(LOGIN_OPTIONS_APPLY_TAG)

    private fun setField(tag: String, value: String) {
        composeTestRule.onNodeWithTag(tag).performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag(tag).performTextInput(value)
    }

    /** Opens Login Options from the Native Login form, applies [app]/[host]/[dpop] and returns. */
    fun configureNative(app: AppConfig, host: LoginHost, dpop: Boolean) {
        openLoginOptions()
        selectMode(AuthMode.NATIVE)
        setNativeSettings(app, host)
        setDPoP(dpop)
        closeSoftKeyboard()
        apply()
        waitForNativeLogin()
    }

    // endregion
}
