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
package com.salesforce.samples.authflowtester.testUtility

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.samples.authflowtester.AuthFlowTesterActivity
import com.salesforce.samples.authflowtester.pageObjects.AuthFlowTesterPageObject
import com.salesforce.samples.authflowtester.pageObjects.AuthorizationPageObject
import com.salesforce.samples.authflowtester.pageObjects.LoginOptionsPageObject
import com.salesforce.samples.authflowtester.pageObjects.LoginPageObject
import com.salesforce.samples.authflowtester.pageObjects.ChromeCustomTabPageObject
import com.salesforce.samples.authflowtester.testUtility.ScopeSelection.EMPTY
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig.REGULAR_AUTH
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig.ADVANCED_AUTH
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.CA_OPAQUE
import org.junit.After
import org.junit.Rule

/**
 * Total polling window after submitting credentials we expect to be
 * rejected. During this window the test repeatedly checks that no new
 * user account has been created and the AuthFlowTester app has not
 * loaded; either condition would indicate that login unexpectedly
 * succeeded.
 */
private const val LOGIN_FAILURE_SETTLE_MS: Long = 5_000

private const val POLL_INTERVAL_MS: Long = 500

abstract class AuthFlowTest {

    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @get:Rule(order = 2)
    val activityRule = ActivityScenarioRule<AuthFlowTesterActivity>(
        Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AuthFlowTesterActivity::class.java,
        ).putExtra(AuthFlowTesterActivity.EXTRA_IS_UI_TESTING, true)
    )

    val loginOptions = LoginOptionsPageObject(composeTestRule)
    val app = AuthFlowTesterPageObject(composeTestRule)

    val user: KnownUserConfig by lazy {
        val minSdk = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationInfo.minSdkVersion
        val userNumber = (Build.VERSION.SDK_INT - minSdk) % KnownUserConfig.entries.toTypedArray().count()
        KnownUserConfig.entries[userNumber]
    }

    // For MultiUser tests
    val otherUser: KnownUserConfig by lazy {
        val userNumber = (user.ordinal + 1) % KnownUserConfig.entries.toTypedArray().count()
        KnownUserConfig.entries[userNumber]
    }

    @After
    open fun cleanup() {
        with(SalesforceSDKManager.getInstance()) {
            userAccountManager.authenticatedUsers?.forEach { userAccount ->
                logout(
                    account = userAccountManager.buildAccount(userAccount),
                    frontActivity = null,
                    showLoginPage = false,
                )
            }

            // Restore the mutable SDK auth options to their defaults.
            forceAdvancedAuthentication = true
            useWebServerAuthentication = true
            useHybridAuthentication = true
            useDPoP = false

            // Reset the selected login server back to REGULAR_AUTH.
            val regularAuthUrl = testConfig.getLoginHost(REGULAR_AUTH).url
            loginServerManager.getLoginServerFromURL(regularAuthUrl)?.let { regularAuthServer ->
                loginServerManager.setSelectedLoginServer(regularAuthServer)
            }
        }
    }

    /**
     * Sets the mutable [SalesforceSDKManager] options that select the login surface.
     *
     * Disabling forced advanced authentication is how the User Agent Flow tests reach the in-app
     * WebView: it is the pre-14.0 behavior the flag now defaults away from.  Both the force flag
     * and the derived [SalesforceSDKManager.isBrowserLoginEnabled] cache are written because the
     * latter is only recomputed on a real server change (via `fetchAuthenticationConfiguration`),
     * not on a same-server WebView reload — see [ensureRegularAuthServer].  [cleanup] restores the
     * defaults so this never leaks into the forced-advanced-auth majority of tests.
     */
    private fun setForcedAdvancedAuthEnabled(enabled: Boolean) {
        with(SalesforceSDKManager.getInstance()) {
            forceAdvancedAuthentication = enabled
            isBrowserLoginEnabled = enabled
        }
    }

    /**
     * Ensures we're on the REGULAR_AUTH server before driving the login flow.
     * Server selection is "sticky" so a previous test might have left it on ADVANCED_AUTH.
     *
     * The LoginActivity always launches with the default `forceAdvancedAuthentication = true`, so
     * it auto-launches a Custom Tab over itself.  When we are already on REGULAR_AUTH and
     * want the Custom Tab, that auto-launched tab is exactly the surface we want and we leave it
     * untouched.
     *
     * We only back out (and dismiss the resulting server picker) when we actually need to change
     * something: switch off a sticky ADVANCED_AUTH selection, or turn off forced advanced
     * authentication for the User Agent Flow so the surface reloads as the in-app WebView.  Backing
     * out also guarantees the launch-time auth-config fetch has completed — the tab only appears
     * once it resolves — so the browser-login state has settled and can be safely overridden below
     * without an in-flight fetch clobbering it.
     *
     * @param expectCustomTab True for the default forced-advanced-auth (Custom Tab) flows; when a
     * server switch is needed the REGULAR_AUTH re-selection re-launches the tab and we wait for it.
     * False for the User Agent Flow cases, which disable forced advanced authentication so the
     * re-selection loads the in-app WebView instead.
     */
    private fun ensureRegularAuthServer(expectCustomTab: Boolean) {
        val chromePage = ChromeCustomTabPageObject(composeTestRule)
        val regularAuthUrl = testConfig.getLoginHost(REGULAR_AUTH).url
        val loginServerManager = SalesforceSDKManager.getInstance().loginServerManager
        val alreadyOnRegularAuth =
            loginServerManager.selectedLoginServer?.url?.trim() == regularAuthUrl.trim()

        if (expectCustomTab && alreadyOnRegularAuth) {
            // Majority path: forced advanced authentication auto-launched the Custom Tab on the
            // already-selected REGULAR_AUTH server, so it is exactly the surface we want.  Leave it
            // in front and let the caller complete login in it; only make sure it has finished
            // launching (the launch is async, gated on the auth-config fetch) before returning.
            chromePage.waitForCustomTab()
            return
        }

        // We need to change the server and/or the login surface.  Resume the LoginActivity by
        // backing out of the auto-launched Custom Tab first.
        chromePage.backOutToLoginActivity()

        if (!expectCustomTab) {
            // User Agent Flow requires the in-app WebView, which is only used when browser (Custom
            // Tab) login is disabled.  Turn off forced advanced authentication so the re-selection
            // below recomputes browser login as disabled, and clear the cached flag directly so a
            // same-server re-selection (which only reloads the WebView without re-running the
            // auth-config fetch) also sees it disabled.  Safe here because the back-out above proves
            // the launch-time fetch has settled.  See LoginViewModel.useWebServerFlow / the
            // browserCustomTab launch gate.
            setForcedAdvancedAuthEnabled(false)
        }

        // Switch to REGULAR_AUTH using LoginServerManager. With the activity resumed this fires the
        // pending-server observers.  A real server change re-runs the auth-config fetch; a
        // same-server re-selection (the flag-off case, already on REGULAR_AUTH) just reloads the
        // login surface — now as the in-app WebView.
        val regularAuthServer = loginServerManager.getLoginServerFromURL(regularAuthUrl)
        if (regularAuthServer != null) {
            loginServerManager.setSelectedLoginServer(regularAuthServer)

            if (expectCustomTab) {
                // Reaching here means the server actually changed (a sticky ADVANCED_AUTH
                // selection).  The re-launch is asynchronous: the SDK first runs an auth-config
                // network fetch (bounded by a multi-second timeout) and only then generates the
                // OAuth URL and launches the Custom Tab. Wait for that tab to actually appear rather
                // than sleeping a fixed interval, so the harness is settled on the REGULAR_AUTH tab
                // before the caller's next step. (No-op if no tab launches within the window.)
                chromePage.waitForCustomTab()
            }
            // For the WebView path there is nothing to wait for: the WebView page-object actions
            // retry internally until the reloaded login form is ready.
        }
    }

    open fun loginAndValidate(
        knownAppConfig: KnownAppConfig,
        scopeSelection: ScopeSelection = EMPTY,
        useWebServerFlow: Boolean = true,
        useHybridAuthToken: Boolean = true,
        useDPoP: Boolean = false,
        knownLoginHostConfig: KnownLoginHostConfig = REGULAR_AUTH,
        knownUserConfig: KnownUserConfig = user,
        useWelcomeDiscovery: Boolean = false,
        isMultiUser: Boolean = false,
    ) {
        // Under the default forced advanced authentication (useWebServerFlow = true) every login
        // completes in a Custom Tab: a ChromeCustomTabPageObject serves both roles — its
        // inherited Compose actions (openLoginOptions/changeServer) drive the LoginActivity top bar
        // after backing out of the tab, and its overridden credential actions drive the tab itself.
        //
        // The User Agent Flow (useWebServerFlow = false) cannot run through the Custom Tab — browser
        // login forces Web Server Flow/PKCE — so those cases disable forced advanced authentication
        // (see [ensureRegularAuthServer]) and drive the in-app WebView via the base LoginPageObject
        // instead.  Its backOutToLoginActivity() is a no-op, so the shared flow below is safe either
        // way.
        val loginPage: LoginPageObject =
            if (useWebServerFlow) ChromeCustomTabPageObject(composeTestRule)
            else LoginPageObject(composeTestRule)

        ensureRegularAuthServer(expectCustomTab = useWebServerFlow)

        val needsLoginOptions = !useWebServerFlow || !useHybridAuthToken || useDPoP ||
                knownAppConfig != CA_OPAQUE || scopeSelection != EMPTY ||
                useWelcomeDiscovery

        if (needsLoginOptions) {

            // Reach Login Options via the top bar.  For the Custom Tab flow the auto-launched tab
            // covers the top bar, so back out first; for the WebView flow this is a no-op (the top
            // bar is already in front).
            loginPage.backOutToLoginActivity()
            loginPage.openLoginOptions()

            if (!useWebServerFlow) {
                loginOptions.disableWebServerFlow()
            }

            if (!useHybridAuthToken) {
                loginOptions.disableHybridAuthToken()
            }

            if (useDPoP) {
                loginOptions.enableDPoP()
            }

            // Set simulated discovery result first - its Save does NOT dismiss the activity,
            // unlike the boot-config Save below which calls activity.finish().
            if (useWelcomeDiscovery) {
                val (username, _) = testConfig.getUser(knownLoginHostConfig, knownUserConfig)
                val targetHost = testConfig.getLoginHost(knownLoginHostConfig).url
                    .removePrefix("https://").removePrefix("http://")
                loginOptions.setSimulatedDiscoveryResult(
                    loginHost = targetHost,
                    username = username,
                )
            }

            if (knownAppConfig == CA_OPAQUE && scopeSelection == EMPTY) {
                // No boot config override needed; nothing to save in that section, so dismiss with a
                // system back press (which finishes the activity just like the Save button would).
                loginOptions.dismiss()
            } else {
                // setOverrideBootConfig taps Save which calls activity.finish().
                loginOptions.setOverrideBootConfig(knownAppConfig, scopeSelection)
            }
            // Dismissing Login Options re-arms the dev-menu reload (loginDevMenuReload), so the
            // SDK regenerates the OAuth URL and re-launches the Custom Tab on LoginActivity.onResume.
        }

        if (useWelcomeDiscovery) {
            // Drive the flow through the SDK's server picker via Welcome Discovery URL.
            // The SDK's switchDefaultOrSalesforceWelcomeDiscoveryLogin path consumes the
            // armed simulatedDiscoveryResult and routes the OAuth authorize URL to the
            // simulated host with the simulated login_hint.  We then complete login with
            // the standard flow (which retypes the username; the pre-fill is exercised
            // server-side via the OAuth login_hint parameter).
            //
            // Changing the server is a top-bar action, so back out of the (re-launched) Custom
            // Tab first.  Forced advanced authentication then drives the simulated My Domain into
            // the Custom Tab for both hosts, where only the password step remains.
            loginPage.backOutToLoginActivity()
            loginPage.changeServerByUrl(WELCOME_DISCOVERY_URL)
            loginPage.welcomeLogin(knownLoginHostConfig, knownUserConfig)
        } else {
            if (knownLoginHostConfig != REGULAR_AUTH) {
                // A non-regular host only occurs on the forced-advanced-auth (Custom Tab) path;
                // switching servers is a top-bar action, so back out of the tab first.  Selecting
                // the new server re-launches the Custom Tab on that host.
                loginPage.backOutToLoginActivity()
                loginPage.changeServer(knownLoginHostConfig)
            }

            // Credentials are entered on the surface now in front: the Custom Tab for the
            // forced-advanced-auth flow, or the in-app WebView for the User Agent Flow.
            loginPage.login(knownLoginHostConfig, knownUserConfig)
        }
        app.waitForAppLoad()

        val isDpop = useDPoP
        app.validateUser(knownLoginHostConfig, knownUserConfig, useWelcomeDiscovery, isMultiUser, isDpop = isDpop)
        app.validateOAuthValues(knownAppConfig, scopeSelection)
        app.validateApiRequest()
    }

    /**
     * Kills the app process and relaunches it, so the SDK reloads all state from disk.
     *
     * `am force-stop <package>` kills the instrumentation process too because the test
     * runner shares the app's process. Instead, we get all PIDs for the package via
     * `pidof`, exclude our own instrumentation PID, and kill only the app PID(s).
     * This leaves the instrumentation alive while forcing the app to restart cold.
     *
     * After the kill, we relaunch via an explicit intent so the SDK re-runs
     * `hydratePerUserFeatures()` from disk, exercising the same code path as a real restart.
     */
    fun restartApp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val packageName = context.packageName
        val device = UiDevice.getInstance(instrumentation)
        val myPid = android.os.Process.myPid()

        // Kill all processes in the package except the instrumentation runner's own PID.
        val pidOutput = device.executeShellCommand("pidof $packageName").trim()
        if (pidOutput.isNotEmpty()) {
            pidOutput.split("\\s+".toRegex())
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it != myPid }
                .forEach { pid -> device.executeShellCommand("kill -9 $pid") }
        }
        Thread.sleep(1_000)

        val launchIntent = Intent(context, AuthFlowTesterActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(AuthFlowTesterActivity.EXTRA_IS_UI_TESTING, true)
        }
        context.startActivity(launchIntent)
        app.waitForAppLoad()
    }

    /**
     * Force-stops and relaunches the app, then validates the persisted user session.
     *
     * Mirrors iOS `restartAndValidateUser`.
     */
    fun restartAndValidateUser(
        knownAppConfig: KnownAppConfig,
        knownLoginHostConfig: KnownLoginHostConfig = REGULAR_AUTH,
        knownUserConfig: KnownUserConfig = user,
        usesWelcomeDiscovery: Boolean = false,
        expectAdvancedAuth: Boolean = false,
    ) {
        restartApp()
        app.validateUser(knownLoginHostConfig, knownUserConfig, usesWelcomeDiscovery, expectAdvancedAuth = expectAdvancedAuth)
    }

    /**
     * Adds a second account by tapping "Add New Account", logs in, and validates.
     * Mirrors iOS `loginOtherUserAndValidate`.
     */
    fun addOtherUserAndValidate(
        knownAppConfig: KnownAppConfig,
        scopeSelection: ScopeSelection = EMPTY,
        useWebServerFlow: Boolean = true,
        useHybridAuthToken: Boolean = true,
        useDPoP: Boolean = false,
        knownLoginHostConfig: KnownLoginHostConfig = REGULAR_AUTH,
    ) {
        app.addNewAccount()
        loginAndValidate(
            knownAppConfig = knownAppConfig,
            scopeSelection = scopeSelection,
            useWebServerFlow = useWebServerFlow,
            useHybridAuthToken = useHybridAuthToken,
            useDPoP = useDPoP,
            knownLoginHostConfig = knownLoginHostConfig,
            knownUserConfig = otherUser,
            isMultiUser = true,
        )
    }

    /**
     * Switches to a user already logged in and validates. Mirrors iOS `switchToUserAndValidateUser`.
     */
    fun switchToUserAndValidateUser(
        knownUserConfig: KnownUserConfig,
        knownLoginHostConfig: KnownLoginHostConfig = REGULAR_AUTH,
    ) {
        app.switchToUser(knownUserConfig)
        composeTestRule.waitForIdle()
        app.validateUser(knownLoginHostConfig, knownUserConfig, isMultiUser = true)
    }

    companion object {
        @VisibleForTesting
        const val WELCOME_DISCOVERY_URL = "https://welcome.salesforce.com/discovery"
    }

    /**
     * Exercises the "Login for Admins" flow: starts on the REGULAR_AUTH server, opens the
     * overflow menu, taps "Login for Admins" to launch a Chrome Custom Tab, completes login in
     * Chrome, and validates the resulting user/tokens.
     *
     * "Login for Admins" always hands off to a Custom Tab built from the cached
     * [com.salesforce.androidsdk.ui.LoginViewModel.browserCustomTabUrl], which is always the Web
     * Server Flow/PKCE URL regardless of `useWebServerFlow` — that is the point of the feature: an
     * org can require a browser-based admin sign-in even when the app itself uses the in-app WebView.
     *
     * @param useWebServerFlow True (default) exercises the forced-advanced-auth login surface: the
     * LoginActivity auto-launches a Custom Tab, so each top-bar action (Login Options, then Login
     * for Admins) is preceded by a back-out — which `clearWebView` performs while preserving the
     * cached admin tab URL.  False disables forced advanced authentication so the base login uses
     * the in-app WebView (with Web Server Flow off); the admin hand-off still launches its Custom
     * Tab.  The back-outs are no-ops on the WebView path, so both share one code path.
     */
    fun adminLoginAndValidate(
        useWebServerFlow: Boolean = true,
        knownAppConfig: KnownAppConfig = KnownAppConfig.BEACON_OPAQUE,
        useDPoP: Boolean = false,
    ) {
        // The top-bar surface (Login Options / Login for Admins menu) is the Custom Tab for the
        // default forced-advanced-auth flow, or the in-app WebView when it is disabled.  The admin
        // hand-off always completes in a Custom Tab.
        val topBarPage: LoginPageObject =
            if (useWebServerFlow) ChromeCustomTabPageObject(composeTestRule)
            else LoginPageObject(composeTestRule)
        val chromePage = ChromeCustomTabPageObject(composeTestRule)

        ensureRegularAuthServer(expectCustomTab = useWebServerFlow)

        // Reach Login Options via the top bar (back-out is a no-op on the WebView path).
        topBarPage.backOutToLoginActivity()
        topBarPage.openLoginOptions()
        if (!useWebServerFlow) {
            loginOptions.disableWebServerFlow()
        }
        if (useDPoP) {
            loginOptions.enableDPoP()
        }
        loginOptions.setOverrideBootConfig(knownAppConfig, scopeSelection = EMPTY)

        // Dismissing Login Options re-launches the Custom Tab on the forced-advanced-auth path;
        // back out again to reach the overflow menu (no-op on the WebView path), then launch the
        // dedicated admin custom tab.
        topBarPage.backOutToLoginActivity()
        topBarPage.tapLoginForAdminsMenuItem()

        // Complete login in Chrome. User credentials are the REGULAR_AUTH server's users
        // since that is the selected login host; the admin flow just swaps the surface
        // (WebView -> Chrome Custom Tab) without changing the target server.
        chromePage.skipGoogleSignIn()
        val (username, password) = testConfig.getUser(REGULAR_AUTH, user)
        chromePage.setUsername(username)
        chromePage.tapLogin()
        chromePage.setPassword(password)
        chromePage.tapLogin()

        // OAuth approval page is rendered inside the Chrome Custom Tab.
        AuthorizationPageObject(composeTestRule).tapAllowAfterLogin(ADVANCED_AUTH)

        app.waitForAppLoad()
        app.validateUser(REGULAR_AUTH, user, expectAdvancedAuth = true)
        app.validateOAuthValues(knownAppConfig, scopeSelection = EMPTY)
        app.validateApiRequest()
    }

    /**
     * Opens Login Options, applies the supplied dynamic boot-config override
     * (arbitrary consumer key, redirect URI, scopes), submits credentials, and
     * expects login to fail. Asserts that no new authenticated user account
     * was created and the login flow (Custom Tab) remains in front.
     *
     * Under forced advanced authentication credentials are entered in the
     * Chrome Custom Tab. On failure the OAuth flow never redirects to the
     * callback URL, so the tab stays in front (showing either the OAuth error
     * page or the still-unsubmitted login form) and no user is created.
     *
     * Uses bounded polling to confirm the failure: at any point during the
     * wait, if a new user account appears or the AuthFlowTester app loads,
     * the test fails immediately (login should not have succeeded). The
     * polling window terminates once the Custom Tab remains in front without a
     * user being created — the steady-state we expect for a rejected login.
     */
    fun loginAndExpectFailure(
        consumerKey: String,
        redirectUri: String,
        scopes: String? = null,
        knownUserConfig: KnownUserConfig = user,
    ) {
        val loginPage = ChromeCustomTabPageObject(composeTestRule)
        ensureRegularAuthServer(expectCustomTab = true)

        val userAccountManager = SalesforceSDKManager.getInstance().userAccountManager
        val initialUserCount = userAccountManager.authenticatedUsers?.size ?: 0

        // Back out of the auto-launched Custom Tab to reach Login Options, apply the override,
        // then let the Custom Tab re-launch with the new dynamic config.
        loginPage.backOutToLoginActivity()
        loginPage.openLoginOptions()
        loginOptions.setOverrideBootConfigRaw(consumerKey, redirectUri, scopes)

        // Submit credentials in the Custom Tab. Some failure modes (e.g. invalid consumer key)
        // cause the OAuth /authorize endpoint to render an error page rather than the username
        // form, in which case the tab never exposes the username/password fields and the
        // page-object actions throw. That is itself a successful failure: the user could not
        // log in.
        loginPage.skipGoogleSignIn()
        val (username, password) = testConfig.getUser(REGULAR_AUTH, knownUserConfig)
        try {
            loginPage.setUsername(username)
            loginPage.tapLogin()
            loginPage.setPassword(password)
            loginPage.tapLogin()
        } catch (e: AssertionError) {
            // Verify the failure was due to a missing login form, not a different
            // test-infrastructure issue. The Custom Tab must still be in front (otherwise we
            // crashed somewhere unexpected).
            assert(loginPage.isCustomTabDisplayed()) {
                "Custom Tab page-object action threw without the Custom Tab in front: ${e.message}"
            }
        }

        val deadline = System.currentTimeMillis() + LOGIN_FAILURE_SETTLE_MS
        while (System.currentTimeMillis() < deadline) {
            val currentUserCount = userAccountManager.authenticatedUsers?.size ?: 0
            assert(currentUserCount == initialUserCount) {
                "Login should have failed but a new user account was created " +
                    "(count went from $initialUserCount to $currentUserCount)"
            }
            assert(!app.isAppLoaded()) {
                "Login should have failed but AuthFlowTester app loaded"
            }
            Thread.sleep(POLL_INTERVAL_MS)
        }

        // After the polling window, confirm we are still in the login flow (Custom Tab).
        assert(loginPage.isCustomTabDisplayed()) {
            "Expected to remain in the login flow (Custom Tab) after a failed login"
        }
    }

    fun migrateAndValidate(
        knownAppConfig: KnownAppConfig,
        knownLoginHostConfig: KnownLoginHostConfig = REGULAR_AUTH,
        scopeSelection: ScopeSelection = EMPTY,
        knownUserConfig: KnownUserConfig = user,
    ) {
        val (preAccessToken, preRefreshToken) = app.getTokens()
        app.migrateToNewApp(knownAppConfig, scopeSelection)
        val (postAccessToken, postRefreshToken) = app.getTokens()

        // Assert tokens are new
        assert(preAccessToken != postAccessToken)
        assert(preRefreshToken != postRefreshToken)

        app.validateUser(knownLoginHostConfig, knownUserConfig)
        app.validateOAuthValues(knownAppConfig, scopeSelection)

        // Assert new tokens work
        app.revokeAccessToken()
        app.validateApiRequest()
    }

    fun assertRevokeAndRefreshWorks(
        isRtr: Boolean,
        isDpop: Boolean = false,
        knownLoginHostConfig: KnownLoginHostConfig = REGULAR_AUTH,
    ) {
        val (preAccessToken, preRefreshToken) = app.getTokens()
        val preNonce = if (isDpop) app.getDpopInfo().nonce else null
        app.revokeAccessToken()
        app.validateApiRequest()
        val (postAccessToken, postRefreshToken) = app.getTokens()

        assert(preAccessToken != postAccessToken) { "Access token should have been refreshed" }

        if (isRtr) {
            assert(preRefreshToken != postRefreshToken) { "Refresh token should have rotated (RTR app)" }
        } else {
            assert(preRefreshToken == postRefreshToken) { "Refresh token should not have changed (non-RTR app)" }
        }

        if (isDpop) {
            val postNonce = app.getDpopInfo().nonce
            assert(postNonce.isNotEmpty()) { "DPoP nonce should be non-empty after refresh" }
            assert(preNonce != postNonce) { "DPoP nonce should have changed after token refresh (server issues new nonce with each /token response)" }
        }

        app.validateUserAgent(knownLoginHostConfig = knownLoginHostConfig, isRtr = isRtr, isDpop = isDpop)
    }
}