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
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.salesforce.androidsdk.ui.AuthenticationMode
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.AUTH_MODE_BROWSER
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.AUTH_MODE_NATIVE
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.BROWSER_FALLBACK_BUTTON
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.CALLBACK_URL_FIELD
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.CANCEL_BUTTON
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.CLIENT_ID_FIELD
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.COMMUNITY_URL_FIELD
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.DIAGNOSTICS_LABEL
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.ERROR_DETAIL
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.LOADING_INDICATOR
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.LOGIN_BUTTON
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.LOGIN_OPTIONS_BUTTON
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.PASSWORD_FIELD
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.RESULT_LABEL
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.USERNAME_FIELD
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.USE_DPOP_TOGGLE
import com.salesforce.samples.authflowtester.testUtility.AppConfig
import com.salesforce.samples.authflowtester.testUtility.LoginHost
import com.salesforce.samples.authflowtester.testUtility.User

private const val NATIVE_SCREEN_TIMEOUT_MS = 30_000L
private const val NATIVE_LOGIN_TIMEOUT_MS = 60_000L

/**
 * Page object for the SDK's debug Native Login form and the Authentication Mode section of its
 * Login Options screen, both Compose screens addressed by the SDK's test tags (shared with the iOS
 * accessibility identifiers).
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

    fun waitForNativeLogin() = waitForTag(LOGIN_BUTTON)

    fun isNativeLoginShowing() = exists(LOGIN_BUTTON)

    fun hasCancelButton() = exists(CANCEL_BUTTON)

    fun enterCredentials(user: User) {
        waitForNativeLogin()
        composeTestRule.onNodeWithTag(USERNAME_FIELD).performTextReplacement(user.username)
        composeTestRule.onNodeWithTag(PASSWORD_FIELD).performTextReplacement(user.password)
        closeSoftKeyboard()
        composeTestRule.waitForIdle()
    }

    fun enterUsernameOnly(username: String) {
        waitForNativeLogin()
        composeTestRule.onNodeWithTag(USERNAME_FIELD).performTextReplacement(username)
        closeSoftKeyboard()
    }

    fun usernameFieldText(): String =
        composeTestRule.onNodeWithTag(USERNAME_FIELD).fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text

    /** True when the password field holds text. The value itself is never read into test output. */
    fun passwordFieldHasText(): Boolean =
        composeTestRule.onNodeWithTag(PASSWORD_FIELD).fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text.isNotEmpty()

    fun tapLogin() = tap(LOGIN_BUTTON)

    fun tapCancel() = tap(CANCEL_BUTTON)

    fun tapBrowserFallback() = tap(BROWSER_FALLBACK_BUTTON)

    fun isLoading() = exists(LOADING_INDICATOR)

    fun waitForLoading(timeoutMillis: Long = NATIVE_SCREEN_TIMEOUT_MS) = waitForTag(LOADING_INDICATOR, timeoutMillis)

    /** Waits for the rejected state and returns its error detail, e.g. `InvalidCredentials`. */
    fun waitForRejection(timeoutMillis: Long = NATIVE_LOGIN_TIMEOUT_MS): String {
        waitForTag(RESULT_LABEL, timeoutMillis)
        return text(ERROR_DETAIL)
    }

    fun isRejected() = exists(RESULT_LABEL)

    /** The error detail and redacted OAuth trace shown by the form, or empty when it shows none. */
    fun diagnostics(): String = listOf(ERROR_DETAIL, DIAGNOSTICS_LABEL)
        .filter(::exists)
        .joinToString(" | ") { text(it) }

    // endregion

    // region Login Options

    fun openLoginOptions() = tap(LOGIN_OPTIONS_BUTTON)

    fun selectMode(mode: AuthenticationMode) = tap(
        if (mode == AuthenticationMode.NATIVE) AUTH_MODE_NATIVE else AUTH_MODE_BROWSER,
    )

    fun setNativeSettings(app: AppConfig, host: LoginHost) {
        waitForTag(CLIENT_ID_FIELD)
        setField(CLIENT_ID_FIELD, app.consumerKey)
        setField(CALLBACK_URL_FIELD, app.redirectUri)
        setField(COMMUNITY_URL_FIELD, host.url)
    }

    fun setDPoP(enabled: Boolean) {
        waitForTag(USE_DPOP_TOGGLE)
        val node = composeTestRule.onNodeWithTag(USE_DPOP_TOGGLE).performScrollTo()
        val isOn = node.fetchSemanticsNode().config[SemanticsProperties.ToggleableState] == ToggleableState.On
        if (isOn != enabled) node.performClick()
        composeTestRule.waitForIdle()
    }

    /** Closes the SDK's Login Options screen; the chosen mode and settings apply as it closes. */
    fun closeLoginOptions() {
        closeSoftKeyboard()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        composeTestRule.waitForIdle()
    }

    private fun setField(tag: String, value: String) {
        composeTestRule.onNodeWithTag(tag).performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag(tag).performTextInput(value)
    }

    /** Opens the SDK Login Options from the Native Login form, applies [app]/[host]/[dpop] and returns. */
    fun configureNative(app: AppConfig, host: LoginHost, dpop: Boolean) {
        openLoginOptions()
        selectMode(AuthenticationMode.NATIVE)
        setNativeSettings(app, host)
        setDPoP(dpop)
        closeLoginOptions()
        waitForNativeLogin()
    }

    // endregion
}
