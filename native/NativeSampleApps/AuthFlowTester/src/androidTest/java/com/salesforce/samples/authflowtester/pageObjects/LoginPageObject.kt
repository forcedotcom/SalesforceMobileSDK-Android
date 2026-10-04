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
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.web.model.Atoms
import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.espresso.web.webdriver.DriverAtoms.clearElement
import androidx.test.espresso.web.webdriver.DriverAtoms.findElement
import androidx.test.espresso.web.webdriver.DriverAtoms.webClick
import androidx.test.espresso.web.webdriver.DriverAtoms.webKeys
import androidx.test.espresso.web.webdriver.Locator
import com.salesforce.androidsdk.R
import com.salesforce.androidsdk.ui.components.LoginViewTestTags
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig
import com.salesforce.samples.authflowtester.testUtility.KnownUserConfig
import com.salesforce.samples.authflowtester.testUtility.testConfig

internal const val USERNAME_ID = "username"
internal const val PASSWORD_ID = "password"
internal const val LOGIN_BUTTON_ID = "Login"

/**
 * CSS selectors used in place of the fixed element IDs above on community login pages. Confirmed
 * via a WebView DOM dump that the community page never renders
 * `id="username"`/`id="password"`/`id="Login"` — it uses platform-generated numeric ids (e.g.
 * `148:0`) instead, stable for the full page lifetime (no late render, no iframe). This mirrors
 * iOS, which never looks up these fields by id either — [LoginPageObject.swift]'s
 * `performLogin` locates them by XCUIElement type (`.textField`/`.secureTextField`). Matching that
 * approach here with a type-based CSS selector, scoped to [KnownLoginHostConfig.COMMUNITY_AUTH]
 * only, since the fixed ids are confirmed to still work for every other known host.
 */
private const val COMMUNITY_USERNAME_SELECTOR = "input[type='text']"
private const val COMMUNITY_PASSWORD_SELECTOR = "input[type='password']"
private const val COMMUNITY_LOGIN_BUTTON_SELECTOR = "button"

/**
 * Interval between retries of the dev-support dialog tap in [LoginPageObject.openLoginOptions].
 * Short enough to re-issue a tap promptly once the dialog's fade-in animation completes.
 */
private const val DIALOG_TAP_RETRY_INTERVAL_MS = 250L

/**
 * Page object for the Salesforce login WebView.
 * Uses Espresso WebView APIs since the login form is an in-app WebView
 * embedded via AndroidView in the SDK's LoginActivity Compose layout.
 */
open class LoginPageObject(composeTestRule: ComposeTestRule): BasePageObject(composeTestRule) {

    /**
     * Surfaces the LoginActivity so its Compose top bar can be driven.  For the in-app WebView
     * login this is a no-op: the WebView is embedded in the LoginActivity, so the top bar is
     * already in front.  [ChromeCustomTabPageObject] overrides this to back out of the Chrome
     * Custom Tab that forced advanced authentication launches over the activity.
     */
    open fun backOutToLoginActivity() {
        // No-op: the WebView login is already on the LoginActivity.
    }

    open fun login(knownLoginHostConfig: KnownLoginHostConfig, knownUserConfig: KnownUserConfig) {
        activeLoginHostConfig = knownLoginHostConfig
        val (username, password) = testConfig.getUser(knownLoginHostConfig, knownUserConfig)
        waitForPageLoad()
        retryWebAction(timeoutMs = WEBVIEW_ACTION_TIMEOUT_MS) {
            val (locator, value) = usernameLocator()
            onWebView().withElement(findElement(locator, value))
                .perform(clearElement())
                .perform(webKeys(username))
            notifyValueChanged(locator, value)
        }
        // The community host's single-page login form renders username + password together, so
        // the password field is already present right after typing the username. A two-step form
        // instead shows a username-only page and needs a Log In tap to advance to the password
        // page. Tapping Log In on a combined page submits an empty password, which the server
        // rejects and re-renders the form (confirmed via a DOM dump) —
        // mirrors ChromeCustomTabPageObject.login's passwordAlreadyVisible check for the same page.
        val passwordAlreadyVisible = isPasswordFieldVisible()
        if (!passwordAlreadyVisible) {
            tapLogin()
        }
        setPassword(password)
        tapLogin()
        AuthorizationPageObject(composeTestRule).tapAllowAfterLogin(knownLoginHostConfig)
    }

    /**
     * Host config for the login currently in progress. Set by [login]/[welcomeLogin] and
     * consulted by [setUsername]/[setPassword]/[tapLogin] to pick an element locator that matches
     * the actual markup of that host's login page (see [COMMUNITY_USERNAME_SELECTOR] and friends).
     * Left `null` (meaning: use the fixed element ids) when neither entry point has run yet.
     */
    private var activeLoginHostConfig: KnownLoginHostConfig? = null

    private fun usernameLocator(): Pair<Locator, String> =
        if (activeLoginHostConfig == KnownLoginHostConfig.COMMUNITY_AUTH) {
            Locator.CSS_SELECTOR to COMMUNITY_USERNAME_SELECTOR
        } else {
            Locator.ID to USERNAME_ID
        }

    private fun passwordLocator(): Pair<Locator, String> =
        if (activeLoginHostConfig == KnownLoginHostConfig.COMMUNITY_AUTH) {
            Locator.CSS_SELECTOR to COMMUNITY_PASSWORD_SELECTOR
        } else {
            Locator.ID to PASSWORD_ID
        }

    private fun loginButtonLocator(): Pair<Locator, String> =
        if (activeLoginHostConfig == KnownLoginHostConfig.COMMUNITY_AUTH) {
            Locator.CSS_SELECTOR to COMMUNITY_LOGIN_BUTTON_SELECTOR
        } else {
            Locator.ID to LOGIN_BUTTON_ID
        }

    /**
     * Single, non-retrying check for whether the password field is already present in the
     * WebView DOM — i.e. the page is a combined single-page form rather than a two-step flow.
     * `withElement(findElement(...))` throws synchronously when the element isn't found, so that
     * is treated as "not visible yet" rather than retried; callers that need to wait for a step
     * transition should poll separately.
     */
    private fun isPasswordFieldVisible(): Boolean =
        try {
            val (locator, value) = passwordLocator()
            onWebView().withElement(findElement(locator, value))
            true
        } catch (_: Exception) {
            false
        }

    /**
     * Exits the login flow when the non-dismissable login-server picker (W-23731759) is in front.
     *
     * Since the picker became non-dismissable its [androidx.compose.material3.ModalBottomSheet]
     * swallows device back presses (the sheet's back handler tries to hide it, which
     * `confirmValueChange` rejects), so a caller cannot walk back to the app with `pressBack()`
     * while the picker is up. Instead we tap the picker header's login-exit back button, which
     * invokes `LoginActivity.handleBackBehavior()` and finishes the activity — the same effect a
     * back press had before the picker was made modal. That button is only rendered when there is
     * an authenticated user (`LoginViewModel.shouldShowBackButton`), which is the case for callers
     * that reached the picker via "Add New Account".
     *
     * @return true if the picker was showing and its back button was tapped; false otherwise.
     */
    fun exitServerPickerIfShowing(): Boolean {
        val pickerShowing = composeTestRule
            .onAllNodesWithTag(LoginViewTestTags.SERVER_PICKER)
            .fetchSemanticsNodes()
            .isNotEmpty()
        if (!pickerShowing) return false

        composeTestRule.onNodeWithTag(LoginViewTestTags.PICKER_LOGIN_BACK_BUTTON)
            .performClick()
        composeTestRule.waitForIdle()
        return true
    }

    /**
     * Returns true when the LoginActivity top bar is currently in front
     * (detected via the SDK's locale-invariant "More Options" test tag). Used by
     * negative tests to assert the user did not advance past login.
     */
    fun isLoginScreenVisible(): Boolean =
        try {
            composeTestRule
                .onAllNodesWithTag(LoginViewTestTags.MORE_OPTIONS_BUTTON)
                .fetchSemanticsNodes()
                .isNotEmpty()
        } catch (_: Throwable) {
            false
        }

    /**
     * Waits for the LoginActivity top bar to be visible (MORE_OPTIONS_BUTTON present and Compose
     * idle). Used after [changeServerByUrl] dismisses the server picker: the picker close and
     * subsequent WebView reload are asynchronous; waiting here ensures the LoginActivity Compose
     * hierarchy is fully settled before the caller proceeds.
     */
    fun waitForLoginScreen() {
        try {
            composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
                composeTestRule
                    .onAllNodesWithTag(LoginViewTestTags.MORE_OPTIONS_BUTTON)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        } catch (_: androidx.compose.ui.test.ComposeTimeoutException) {
            // Best-effort: if the top bar is not reachable within the timeout the caller's
            // subsequent actions will fail with descriptive messages.
            android.util.Log.w("LoginPageObject", "waitForLoginScreen: timed out after ${TIMEOUT_MS}ms waiting for MORE_OPTIONS_BUTTON")
        }
    }

    /**
     * Waits for the in-app WebView page to finish loading by watching the LOADING_INDICATOR:
     * phase 1 waits for the indicator to appear (confirming a reload has started); phase 2 waits
     * for it to disappear (confirming [LoginActivity.LoginWebViewClient.onPageFinished] has fired).
     *
     * This is called at the top of [login] to handle the case where a reload was triggered by
     * [LoginOptionsPageObject.setOverrideBootConfig] → [LoginOptionsActivity.finish()] →
     * [LoginActivity.onResume] → [LoginViewModel.reloadWebView]. The Salesforce sandbox page can
     * take 20–30 s to render the login form, and [retryWebAction]'s 15 s budget would expire
     * before the `username` element appears without this explicit wait.
     *
     * Best-effort on both phases so slow or already-loaded pages degrade gracefully: if the
     * indicator never appears the page was already loaded (or loaded faster than we checked) and
     * we proceed immediately; if phase 2 times out [retryWebAction] keeps retrying as a fallback.
     */
    private fun waitForPageLoad() {
        // Phase 1: Wait for the LOADING_INDICATOR to appear, confirming a reload is in progress.
        // Short timeout: if the reload was so fast we missed the indicator, proceed immediately.
        val indicatorAppeared = try {
            composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
                composeTestRule
                    .onAllNodesWithTag(LoginViewTestTags.LOADING_INDICATOR)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            true
        } catch (_: ComposeTimeoutException) {
            false
        }

        if (!indicatorAppeared) {
            // The indicator never showed: page was already loaded or the reload is still pending.
            // Proceed — retryWebAction will handle any remaining wait.
            return
        }

        // Phase 2: Wait for the LOADING_INDICATOR to disappear, confirming onPageFinished fired.
        // Use a generous multiple of TIMEOUT_MS to accommodate slow sandbox pages (observed ~26 s).
        try {
            composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS * 3) {
                composeTestRule
                    .onAllNodesWithTag(LoginViewTestTags.LOADING_INDICATOR)
                    .fetchSemanticsNodes()
                    .isEmpty()
            }
        } catch (_: ComposeTimeoutException) {
            // Best-effort: page is still loading but retryWebAction will keep trying.
        }
    }

    /**
     * Welcome Discovery login: the OAuth `login_hint` already pre-filled the username
     * field on page 1; we still tap Continue to advance to page 2, then enter the password
     * and submit.  Mirrors iOS performWelcomeLogin.
     */
    open fun welcomeLogin(knownLoginHostConfig: KnownLoginHostConfig, knownUserConfig: KnownUserConfig) {
        activeLoginHostConfig = knownLoginHostConfig
        val (_, password) = testConfig.getUser(knownLoginHostConfig, knownUserConfig)
        tapLogin()
        setPassword(password)
        tapLogin()
        AuthorizationPageObject(composeTestRule).tapAllowAfterLogin(knownLoginHostConfig)
    }

    fun openLoginOptions() {
        // If the login-server picker is showing, the top app bar is behind its modal scrim.
        // In that case, tap the picker's own dev-support button instead (PICKER_DEV_SUPPORT_BUTTON
        // is visible in the picker header for debug builds). Otherwise use the normal top-bar path.
        val pickerShowing = composeTestRule
            .onAllNodesWithTag(LoginViewTestTags.SERVER_PICKER)
            .fetchSemanticsNodes()
            .isNotEmpty()

        if (pickerShowing) {
            composeTestRule.onNodeWithTag(LoginViewTestTags.PICKER_DEV_SUPPORT_BUTTON)
                .performClick()
        } else {
            // Tap "More Options" three-dot menu (Compose IconButton)
            composeTestRule.onNodeWithTag(LoginViewTestTags.MORE_OPTIONS_BUTTON)
                .performClick()
            composeTestRule.waitForIdle()

            // Tap "Developer Support" dropdown menu item
            composeTestRule.onNodeWithTag(LoginViewTestTags.MENU_ITEM_DEV_SUPPORT)
                .performClick()
        }
        composeTestRule.waitForIdle()

        // Wait for the AlertDialog to be fully rendered and ready
        try {
            composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
                onView(withText(getString(R.string.sf__dev_support_login_options_title)))
                    .inRoot(isDialog())
                    .check { _, _ -> }
                true
            }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("Timed out after ${TIMEOUT_MS}ms waiting for Developer Support dialog to appear", e)
        }

        // Tap "Login Options" in the native AlertDialog (not Compose) and wait for
        // LoginOptionsActivity's Compose content to render.
        //
        // The dev-support dialog is shown from a coroutine (SalesforceSDKManager.showDevSupportDialog)
        // and its window fades in.  On a device with animations enabled (unlike Firebase Test Lab,
        // which disables them), a single tap issued the instant the dialog view exists can land while
        // the window opacity is still below Android's anti-tap-jacking threshold, in which case
        // InputDispatcher silently drops the touch ("Not sending motion ... opacity ... is below the
        // threshold") and the activity never launches.  Re-issue the tap until the screen renders so a
        // touch dropped during the fade-in is simply retried once the window is opaque.
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        var rendered = false
        while (!rendered && System.currentTimeMillis() < deadline) {
            // Re-issue the tap only while the dialog is still up.  A successful tap dismisses the
            // dialog, so once it is gone a prior tap landed and we just keep waiting for the render;
            // tapping a missing dialog would throw NoMatchingRootException.
            try {
                onView(withText(getString(R.string.sf__dev_support_login_options_title)))
                    .inRoot(isDialog())
                    .perform(click())
            } catch (_: RuntimeException) {
                // Dialog no longer present (a prior tap already dismissed it); fall through to wait.
            }

            rendered = try {
                composeTestRule.waitUntil(timeoutMillis = DIALOG_TAP_RETRY_INTERVAL_MS) {
                    composeTestRule.onAllNodesWithContentDescription(
                        getString(R.string.sf__login_options_dynamic_config_toggle_content_description)
                    ).fetchSemanticsNodes().isNotEmpty()
                }
                true
            } catch (_: ComposeTimeoutException) {
                false
            }
        }

        if (!rendered) {
            throw AssertionError("Timed out after ${TIMEOUT_MS}ms waiting for Login Options screen to render")
        }
        Thread.sleep(TIMEOUT_MS / 4)
    }

    /**
     * Opens the top bar overflow menu and taps the "Login for Admins" item.
     * The SDK then launches the OAuth authorize URL in a Chrome Custom Tab while
     * the in-app WebView remains loaded underneath.
     */
    open fun tapLoginForAdminsMenuItem() {
        // Tap "More Options" three-dot menu (Compose IconButton)
        composeTestRule.onNodeWithTag(LoginViewTestTags.MORE_OPTIONS_BUTTON)
            .performClick()
        composeTestRule.waitForIdle()

        // Tap "Login for Admins" dropdown menu item
        composeTestRule.onNodeWithTag(LoginViewTestTags.MENU_ITEM_LOGIN_FOR_ADMINS)
            .performClick()
        composeTestRule.waitForIdle()
    }

    fun changeServer(knownLoginHostConfig: KnownLoginHostConfig) {
        changeServerByUrl(testConfig.getLoginHost(knownLoginHostConfig).url)
    }

    /**
     * Selects a server from the server picker bottom sheet by matching its URL substring.
     * Used for servers that aren't represented in `ui_test_config.json` (e.g.
     * `welcome.salesforce.com/discovery`).
     *
     * If the picker is already showing (e.g. because [backOutToLoginActivity] left it up after
     * the tab closed), skip opening it and select directly.
     */
    fun changeServerByUrl(url: String) {
        val pickerAlreadyShowing = composeTestRule
            .onAllNodesWithTag(LoginViewTestTags.SERVER_PICKER)
            .fetchSemanticsNodes()
            .isNotEmpty()

        if (!pickerAlreadyShowing) {
            // Tap "More Options" three-dot menu (Compose IconButton)
            composeTestRule.onNodeWithTag(LoginViewTestTags.MORE_OPTIONS_BUTTON)
                .performClick()
            composeTestRule.waitForIdle()

            // Tap "Change Server" dropdown menu item
            composeTestRule.onNodeWithTag(LoginViewTestTags.MENU_ITEM_PICK_SERVER)
                .performClick()

            // Wait for server picker bottom sheet to appear
            try {
                composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
                    composeTestRule.onAllNodesWithTag(LoginViewTestTags.SERVER_PICKER)
                        .fetchSemanticsNodes().isNotEmpty()
                }
            } catch (e: ComposeTimeoutException) {
                throw AssertionError("Timed out after ${TIMEOUT_MS}ms waiting for server picker bottom sheet to appear", e)
            }
        }

        // Select the server matching the URL (filter for clickable node if multiple matches)
        composeTestRule.onAllNodesWithText(url, substring = true)
            .filterToOne(hasClickAction())
            .performClick()
        composeTestRule.waitForIdle()
    }

    open fun setUsername(name: String) {
        retryWebAction {
            val (locator, value) = usernameLocator()
            onWebView().withElement(findElement(locator, value))
                .perform(clearElement())
                .perform(webKeys(name))
            notifyValueChanged(locator, value)
        }
    }

    open fun setPassword(password: String) {
        retryWebAction {
            val (locator, value) = passwordLocator()
            onWebView().withElement(findElement(locator, value))
                .perform(clearElement())
                .perform(webKeys(password))
            notifyValueChanged(locator, value)
        }
    }

    open fun tapLogin() {
        retryWebAction {
            val (locator, value) = loginButtonLocator()
            onWebView().withElement(findElement(locator, value))
                .perform(webClick())
        }
    }

    /**
     * Fires the DOM events a framework-rendered form listens for. The community login page is an
     * Aura component that only updates its model on `change`/`keyup`/`blur`; Espresso-Web's
     * `webKeys` sets the value without those, so the page would report "Enter a value in the
     * User Name field." on submit even though the fields look filled. Only the community page
     * needs this, so other hosts are left untouched.
     */
    private fun notifyValueChanged(locator: Locator, value: String) {
        if (activeLoginHostConfig != KnownLoginHostConfig.COMMUNITY_AUTH || locator != Locator.CSS_SELECTOR) return
        onWebView().perform(
            Atoms.script(
                "var e = document.querySelector(\"$value\");" +
                    "['input', 'keyup', 'change', 'blur'].forEach(function(t) {" +
                    "e.dispatchEvent(new Event(t, {bubbles: true}));});" +
                    "return true;"
            )
        )
    }

    /** Retries a WebView action until it succeeds or times out. */
    private fun <T> retryWebAction(
        timeoutMs: Long = TIMEOUT_MS,
        action: () -> T,
    ): T {
        val endTime = System.currentTimeMillis() + timeoutMs
        var lastException: Exception? = null
        while (System.currentTimeMillis() < endTime) {
            try {
                return action()
            } catch (e: Exception) {
                lastException = e
                Thread.sleep(SLEEP_TIME_MS)
            }
        }
        throw AssertionError(
            "WebView action failed after ${timeoutMs}ms",
            lastException,
        )
    }
}
