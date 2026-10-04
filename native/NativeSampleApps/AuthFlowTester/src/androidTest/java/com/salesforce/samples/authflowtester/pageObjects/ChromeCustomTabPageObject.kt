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

import android.os.Bundle
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
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
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig.COMMUNITY_AUTH
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
 * Maximum number of actual submission attempts when Chrome UI intercepts the form. A missing button
 * does not consume an attempt: the page object keeps looking throughout the server-render timeout.
 * Enter is used at most once, and only while the intended Chrome credential field still has focus.
 */
private const val MAX_LOGIN_SUBMISSION_ATTEMPTS = 3

/**
 * Maximum number of retype attempts when a readback shows a credential field empty or mismatched.
 * Used only on the community host (see [ChromeCustomTabPageObject.login]), whose single-page login
 * form has been observed to re-render shortly after being filled, discarding the typed keystrokes.
 */
private const val MAX_FIELD_RETYPE_ATTEMPTS = 3

private enum class CredentialField {
    USERNAME,
    PASSWORD,
}

/**
 * Handles Custom Tab interactions.
 * UiAutomator is required here because the browser (often Chrome) runs in a
 * separate process that Espresso and Compose Test APIs cannot access.
 */
class ChromeCustomTabPageObject(composeTestRule: ComposeTestRule): LoginPageObject(composeTestRule) {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private var lastFocusedCredentialField: CredentialField? = null

    override fun login(knownLoginHostConfig: KnownLoginHostConfig, knownUserConfig: KnownUserConfig) {
        waitForCustomTabOnExpectedHost(knownLoginHostConfig)
        skipGoogleSignIn()
        val (username, password) = testConfig.getUser(knownLoginHostConfig, knownUserConfig)
        // The community host's single-page login form has been observed to re-render shortly after
        // being filled, discarding the typed keystrokes (iOS hit the same page-shape issue). Scoped
        // to that host rather than applied generically, since every other host's form has not shown
        // this behavior.
        val verifyTypedFields = knownLoginHostConfig == COMMUNITY_AUTH
        setUsername(username)
        if (verifyTypedFields) {
            verifyFieldOrRetype(CredentialField.USERNAME, username) { setUsername(username) }
        }
        // A combined Salesforce My Domain page renders username + password on ONE screen, so the
        // password field is already present after typing the username. A two-step flow instead
        // shows a username-only page and needs a Log In tap to advance to the password page.
        // Only tap to advance in the two-step case: tapping Log In on a combined page submits an
        // empty password, which triggers the client-side "Please enter your password" error and
        // re-renders the form (previously this stray tap, combined with setPassword grabbing the
        // first text field, caused the password to be typed into the username field).
        val passwordAlreadyVisible = isPasswordStepVisible()
        if (!passwordAlreadyVisible) {
            advanceToPasswordStep()
        }
        submitPassword(
            password,
            verifyTypedFields = verifyTypedFields,
            // Only re-check the username field right before submission on the combined-form case:
            // on a two-step form the username field is no longer on screen once advanceToPasswordStep
            // has run, and re-typing into whatever EditText(0) resolves to there would corrupt the
            // password field instead.
            username = username.takeIf { verifyTypedFields && passwordAlreadyVisible },
        )
        // Under forced advanced authentication every login completes in the Custom Tab, so the
        // OAuth approval page is always rendered there regardless of the configured host.
        AuthorizationPageObject(composeTestRule).tapAllowAfterLogin(ADVANCED_AUTH)
    }

    override fun welcomeLogin(knownLoginHostConfig: KnownLoginHostConfig, knownUserConfig: KnownUserConfig) {
        skipGoogleSignIn()
        lastFocusedCredentialField = null
        val (username, password) = testConfig.getUser(knownLoginHostConfig, knownUserConfig)
        // The OAuth login_hint already pre-filled the username; advance, enter password, submit.
        advanceToPasswordStep()
        submitPassword(password)
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
                .takeIf { it.waitForExists(QUICK_CHECK_TIMEOUT_MS) }
        if (usernameField != null) {
            usernameField.click()
            usernameField.setText(name)
            lastFocusedCredentialField = CredentialField.USERNAME
            return
        }

        // API 35 Chrome intermittently focuses the WebEmailAddress editor (confirmed by the IME)
        // while UiSelector exposes no EditText at all. Accessibility focus still identifies the
        // HTML input in that state, and ACTION_SET_TEXT avoids an unreliable coordinate tap.
        if (!setTextUsingAccessibility(name, expectPassword = false)) {
            throw AssertionError("Username field not found in Custom Tab")
        }
        lastFocusedCredentialField = CredentialField.USERNAME
    }

    override fun setPassword(password: String) {
        // Current Chrome exposes the HTML password element's ID as its accessibility resource ID.
        // Prefer that stable signal, then support combined forms by position. The final instance(0)
        // fallback is safe after advanceToPasswordStep has confirmed that the password screen is
        // visible; using it before that confirmation could overwrite the username during a render.
        val passwordField = device.findObject(UiSelector().resourceId(PASSWORD_ID))
            .takeIf { it.waitForExists(QUICK_CHECK_TIMEOUT_MS) }
            ?: combinedFormPasswordField()
            .takeIf { it.waitForExists(QUICK_CHECK_TIMEOUT_MS) }
            ?: device.findObject(UiSelector().className("android.widget.EditText").instance(0))
                .takeIf { it.waitForExists(QUICK_CHECK_TIMEOUT_MS) }
        if (passwordField != null) {
            passwordField.click()
            passwordField.setText(password)
            lastFocusedCredentialField = CredentialField.PASSWORD
            return
        }

        if (!setTextUsingAccessibility(password, expectPassword = true)) {
            throw AssertionError("Password field not found in Custom Tab")
        }
        lastFocusedCredentialField = CredentialField.PASSWORD
    }

    /**
     * Reads back [field]'s current on-screen text via UiAutomator, trying the same selectors
     * [setUsername]/[setPassword] use to locate it. Returns null if no matching field is found.
     */
    private fun currentFieldText(field: CredentialField): String? {
        val resourceId = if (field == CredentialField.PASSWORD) PASSWORD_ID else USERNAME_ID
        val byResourceId = device.findObject(UiSelector().resourceId(resourceId))
        if (byResourceId.exists()) return runCatching { byResourceId.text }.getOrNull()

        val byPosition = if (field == CredentialField.PASSWORD) {
            combinedFormPasswordField()
        } else {
            device.findObject(UiSelector().className("android.widget.EditText").instance(0))
        }
        if (byPosition.exists()) return runCatching { byPosition.text }.getOrNull()

        return findVisibleChromeInput(expectPassword = field == CredentialField.PASSWORD)?.text?.toString()
    }

    /**
     * True when [field]'s current readback looks like [expectedValue] was actually accepted.
     * Compared by exact text for the username (visible as typed), and by non-empty length match
     * for the password, since Chrome's accessibility tree may expose a masked value (e.g. bullet
     * characters) rather than the literal characters — a dot count is as much as can be verified
     * without reading the real value. Never logs [expectedValue] itself.
     */
    private fun fieldLooksFilled(field: CredentialField, expectedValue: String): Boolean {
        val currentText = currentFieldText(field) ?: return false
        return if (field == CredentialField.PASSWORD) {
            currentText.isNotEmpty() && currentText.length == expectedValue.length
        } else {
            currentText == expectedValue
        }
    }

    /**
     * Community host only (see [login]): the single-page login form has been observed to
     * re-render shortly after being filled, discarding the typed keystrokes (iOS hit the same
     * page-shape issue against the same page). Reads [field] back via [fieldLooksFilled] and
     * retypes it with [retype] when it's empty or doesn't match, up to [MAX_FIELD_RETYPE_ATTEMPTS]
     * times, then fails fast with a clear message rather than proceeding to submit a form known to
     * still be wrong. Never logs the expected or actual value — only whether a given attempt's
     * readback was empty/mismatched.
     */
    private fun verifyFieldOrRetype(field: CredentialField, expectedValue: String, retype: () -> Unit) {
        repeat(MAX_FIELD_RETYPE_ATTEMPTS) { attempt ->
            if (fieldLooksFilled(field, expectedValue)) return
            android.util.Log.i(
                "ChromeCustomTabPageObject",
                "Community $field field empty or mismatched on readback attempt ${attempt + 1}; retyping.",
            )
            retype()
        }
        if (!fieldLooksFilled(field, expectedValue)) {
            throw AssertionError(
                "Community $field field is still empty or incorrect after " +
                    "$MAX_FIELD_RETYPE_ATTEMPTS retype attempts",
            )
        }
    }

    /**
     * Waits for a two-step Salesforce login page to replace the username input with the password
     * input before typing. The previous page can remain accessible for several seconds after its
     * Log In button is tapped; falling back to EditText instance(0) during that window overwrites
     * the username with the password.
     */
    fun advanceToPasswordStep() {
        // Chrome may show an autofill suggestion anchored below the focused username field. On
        // the FTL viewport that popup covers Log In, so dismiss the focused-field UI before the
        // checked tap. The guarded Back press closes the IME/popup without leaving the Custom Tab.
        dismissKeyboard()
        tapLoginUntil(
            failureMessage = "Password step did not replace the username field in Custom Tab",
            expectedFocusedField = CredentialField.USERNAME,
        ) {
            isPasswordStepVisible()
        }
    }

    /**
     * Enters and submits a password, confirming that Chrome actually left the login form. This
     * catches both an autofill popup intercepting the tap and an input event dropped by Chrome.
     *
     * [verifyTypedFields] (community host only — see [login]) reads the password field back after
     * typing and retypes it if it's empty or doesn't match. When [username] is also non-null
     * (combined-form case only), both fields are read back and retyped once more right before the
     * submission tap, since the whole form has been observed to re-render shortly after being
     * filled, discarding either field's keystrokes.
     */
    fun submitPassword(password: String, verifyTypedFields: Boolean = false, username: String? = null) {
        setPassword(password)
        if (verifyTypedFields) {
            verifyFieldOrRetype(CredentialField.PASSWORD, password) { setPassword(password) }
        }
        // On the combined page the Log In button sits directly below the password field, so the
        // soft keyboard raised by setPassword covers it. Back closes the IME without leaving the
        // Custom Tab, making the button visible before the checked tap.
        dismissKeyboard()
        if (username != null) {
            // Final check right before tapping Log In: re-verify both fields once more rather than
            // just the one most recently typed, since a re-render can clear either.
            verifyFieldOrRetype(CredentialField.USERNAME, username) { setUsername(username) }
            verifyFieldOrRetype(CredentialField.PASSWORD, password) { setPassword(password) }
        }
        var loginFormWasGone = false
        tapLoginUntil(
            failureMessage = "Login form remained on screen after password submission",
            expectedFocusedField = CredentialField.PASSWORD,
        ) {
            val loginFormIsGone = !isLoginButtonVisible() && !isPasswordStepVisible()
            // Chrome can briefly remove the Log In accessibility node while an autofill popup
            // handles the tap or the form re-renders. Require two consecutive observations of the
            // whole form being gone before treating the submission as complete.
            if (loginFormIsGone && loginFormWasGone) {
                true
            } else {
                loginFormWasGone = loginFormIsGone
                false
            }
        }
    }

    private fun isPasswordStepVisible(): Boolean =
        device.findObject(UiSelector().resourceId(PASSWORD_ID)).exists() ||
            combinedFormPasswordField().exists() ||
            hasVisiblePasswordInput()

    /**
     * Finds a password input by accessibility semantics. On API 35 Chrome exposes a two-step
     * password field as the first EditText with no HTML resource ID; its `password` property is the
     * stable distinction from the username field while the page is replacing one with the other.
     */
    private fun hasVisiblePasswordInput(): Boolean =
        findVisibleChromeInput(expectPassword = true) != null

    /**
     * Sets an HTML input when UiSelector cannot see Chrome's focused editor. Prefer the focused
     * node, then traverse visible Chrome inputs while the page finishes rendering.
     */
    private fun setTextUsingAccessibility(text: String, expectPassword: Boolean): Boolean {
        val deadline = System.currentTimeMillis() + WEBVIEW_ACTION_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val input = findVisibleChromeInput(expectPassword)
            if (input != null) {
                if (!input.isFocused) {
                    input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    input.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                val arguments = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        text,
                    )
                }
                if (input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                    return true
                }
            }
            if (setTextInFocusedChromeEditor(text)) return true
            Thread.sleep(QUICK_CHECK_TIMEOUT_MS)
        }
        return false
    }

    /**
     * Types into Chrome's focused HTML editor when the control is visible and focused but absent
     * from UiAutomation's accessibility tree. This state occurs intermittently on FTL API 35/37.
     * The caller has already established whether it is on the username or password step; requiring
     * the IME to repeat that subtype is unreliable because API 35 can reset it to an unspecified
     * Chrome editor while the HTML field visibly remains focused.
     */
    private fun setTextInFocusedChromeEditor(text: String): Boolean {
        val inputMethodState = runCatching {
            device.executeShellCommand("dumpsys input_method").replace(" ", "")
        }.getOrDefault("")
        val chromeHasInputFocus = inputMethodState.contains("packageName=com.android.chrome")
        if (device.currentPackageName != "com.android.chrome" || !chromeHasInputFocus) {
            return false
        }

        // Replace any browser-restored value instead of appending to it.
        device.pressKeyCode(KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON)
        val keyEvents = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
            .getEvents(text.toCharArray())
            ?: return false
        val keyDownEvents = keyEvents.filter { it.action == KeyEvent.ACTION_DOWN }
        return keyDownEvents.isNotEmpty() &&
            keyDownEvents.all { device.pressKeyCode(it.keyCode, it.metaState) }
    }

    private fun findVisibleChromeInput(expectPassword: Boolean): AccessibilityNodeInfo? {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val focusedInput = runCatching {
            uiAutomation.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        }.getOrNull()
        if (focusedInput.isMatchingChromeInput(expectPassword)) {
            return focusedInput
        }

        val windows = uiAutomation.windows
        for (window in windows) {
            val root = window.root ?: continue
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                if (node.packageName == "com.android.chrome" &&
                    node.isEditable &&
                    node.isPassword == expectPassword &&
                    node.isVisibleToUser
                ) {
                    return node
                }
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let(queue::addLast)
                }
            }
        }
        return null
    }

    private fun AccessibilityNodeInfo?.isMatchingChromeInput(expectPassword: Boolean): Boolean =
        this != null &&
            packageName == "com.android.chrome" &&
            isEditable &&
            isPassword == expectPassword &&
            isVisibleToUser

    /**
     * Returns the password field on a combined username/password form. Chrome does not expose HTML
     * element IDs as Android resource IDs, so the second EditText is the reliable selector here.
     */
    private fun combinedFormPasswordField() = device.findObject(
        UiSelector().className("android.widget.EditText").instance(1)
    )

    override fun tapLogin() {
        val loginButton = findLoginButton(TIMEOUT_MS)
            ?: throw AssertionError("Log In button not found in Custom Tab")
        loginButton.click()
    }

    /**
     * Taps Log In and checks the resulting screen state instead of trusting click injection. Chrome
     * autofill can cover the button while leaving the underlying accessibility node discoverable,
     * so the first click may dismiss/select the suggestion without submitting the form.
     */
    private fun tapLoginUntil(
        failureMessage: String,
        expectedFocusedField: CredentialField,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + WEBVIEW_ACTION_TIMEOUT_MS
        var submissionAttempts = 0
        var nextSubmissionAt = System.currentTimeMillis()
        var enterFallbackUsed = false

        while (System.currentTimeMillis() < deadline) {
            if (condition()) return

            val now = System.currentTimeMillis()
            if (submissionAttempts < MAX_LOGIN_SUBMISSION_ATTEMPTS && now >= nextSubmissionAt) {
                val loginButton = findLoginButton(QUICK_CHECK_TIMEOUT_MS)
                val submitted = when {
                    loginButton != null -> {
                        loginButton.click()
                        true
                    }
                    !enterFallbackUsed && isExpectedCredentialFieldFocused(expectedFocusedField) -> {
                        // API 35/37 Chrome can omit the button from UiAutomator while keeping the
                        // intended HTML editor focused. Enter submits that form without relying on
                        // an unsafe coordinate click.
                        device.pressEnter()
                        enterFallbackUsed = true
                        true
                    }
                    else -> false
                }
                if (submitted) {
                    submissionAttempts++
                    nextSubmissionAt = System.currentTimeMillis() + SLEEP_TIME_MS
                }
            }

            val remaining = deadline - System.currentTimeMillis()
            if (remaining > 0) {
                Thread.sleep(minOf(QUICK_CHECK_TIMEOUT_MS, remaining))
            }
        }

        throw AssertionError(failureMessage)
    }

    private fun isExpectedCredentialFieldFocused(expectedField: CredentialField): Boolean {
        val expectsPassword = expectedField == CredentialField.PASSWORD
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val focusedInput = runCatching {
            uiAutomation.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        }.getOrNull()
        if (focusedInput.isMatchingChromeInput(expectsPassword) && focusedInput?.isFocused == true) {
            return true
        }

        // When Chrome omits the focused HTML node from the accessibility tree, require both the
        // field most recently focused by this page object and Chrome's active input connection.
        val chromeHasInputFocus = runCatching {
            device.executeShellCommand("dumpsys input_method")
                .replace(" ", "")
                .contains("packageName=com.android.chrome")
        }.getOrDefault(false)
        return lastFocusedCredentialField == expectedField && chromeHasInputFocus
    }

    private fun isLoginButtonVisible(): Boolean =
        device.findObject(UiSelector().resourceId(LOGIN_BUTTON_ID)).exists() ||
            device.findObject(
                // textMatches regex with the (?i) flag instead of textContains, since UiSelector's
                // textContains is case-sensitive and a community ECA's hosted login page is free to
                // render the button as "Log in" rather than Chrome's own "Log In".
                UiSelector().className("android.widget.Button").textMatches("(?i).*log in.*")
            ).exists()

    private fun findLoginButton(fallbackTimeoutMs: Long) =
        device.findObject(UiSelector().resourceId(LOGIN_BUTTON_ID))
            .takeIf { it.waitForExists(QUICK_CHECK_TIMEOUT_MS) }
            ?: device.findObject(
                UiSelector().className("android.widget.Button").textMatches("(?i).*log in.*")
            ).takeIf { it.waitForExists(fallbackTimeoutMs) }

    /**
     * Dismisses the soft keyboard if it is showing.
     *
     * Uses UiAutomator's [UiDevice.pressBack] (like [LoginOptionsPageObject]) rather than
     * `Espresso.closeSoftKeyboard()`: the keyboard is raised over the Chrome Custom Tab, which runs
     * in a separate process Espresso cannot reach. When the IME is visible, Back dismisses it
     * without leaving the tab. The `dumpsys input_method` guard ensures we only press Back when the
     * keyboard is actually shown, so this never accidentally navigates the tab when it is hidden.
     */
    private fun dismissKeyboard() {
        // Default to true if the shell probe fails: callers invoke this immediately after typing
        // into a login field, so the keyboard is reliably up and dismissing it is safe.
        val keyboardShown = runCatching {
            device.executeShellCommand("dumpsys input_method")
                .replace(" ", "")
                .contains("mInputShown=true")
        }.getOrDefault(true)
        if (keyboardShown) {
            device.pressBack()
            device.waitForIdle(QUICK_CHECK_TIMEOUT_MS)
        }
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

    /**
     * Waits, before any typing starts, for the Custom Tab to be in front and showing the host the
     * test selected. The SDK can fire several VIEW intents in quick succession while it configures
     * login options and finally launches the chosen host (see AuthFlowTest.loginAndValidate): the
     * default server's own auth config fires first, then re-configuring login options (app/DPoP)
     * relaunches the tab, then selecting the target host launches it again. Acting on the first tab
     * to appear risks typing into a page that is about to be replaced. Keyed on
     * [knownLoginHostConfig] rather than anything community-specific, so this helps every host.
     *
     * Primary signal is the toolbar's url_bar, which shows the loaded page's host. Chrome does not
     * always expose it (observed intermittently on FTL), so when it is missing or empty this falls
     * back to the Custom Tab package being present with a login form that has rendered and held
     * for two consecutive checks — the same "stable observation" debounce [submitPassword] already
     * uses to confirm a form actually left the screen, applied here in reverse to confirm one has
     * actually arrived and settled.
     */
    private fun waitForCustomTabOnExpectedHost(knownLoginHostConfig: KnownLoginHostConfig) {
        val expectedHost = testConfig.getLoginHost(knownLoginHostConfig).url
            .substringAfter("://")
            .substringBefore("/")
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        var formSeenStable = false
        while (System.currentTimeMillis() < deadline) {
            // Idempotent, and also clears the FRE, which otherwise hides the toolbar/form.
            skipGoogleSignIn()
            if (!isCustomTabDisplayed()) {
                formSeenStable = false
                Thread.sleep(QUICK_CHECK_TIMEOUT_MS)
                continue
            }
            val urlBar = device.findObject(UiSelector().resourceId("com.android.chrome:id/url_bar"))
            if (urlBar.exists()) {
                val urlBarText = runCatching { urlBar.text }.getOrDefault("")
                if (urlBarText.contains(expectedHost, ignoreCase = true)) {
                    return
                }
                formSeenStable = false
            } else {
                val formIsVisible = isLoginButtonVisible() || isPasswordStepVisible()
                if (formIsVisible && formSeenStable) {
                    return
                }
                formSeenStable = formIsVisible
            }
            Thread.sleep(QUICK_CHECK_TIMEOUT_MS)
        }
        android.util.Log.w(
            "ChromeCustomTabPageObject",
            "Timed out waiting for the Custom Tab to show $expectedHost; proceeding anyway.",
        )
    }
}
