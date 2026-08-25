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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.salesforce.androidsdk.ui.components.LoginViewTestTags
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import com.salesforce.androidsdk.app.Features
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
import org.junit.Before
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

    /**
     * Establishes a Bearer baseline before every test.
     *
     * SDK 14.0 defaults [SalesforceSDKManager.useDPoP] to true, but the UI tests build a fresh
     * OAuth authorization URL from this global flag.  On the CA_OPAQUE all-defaults path the login
     * helper skips the Login Options screen (see [loginAndValidate]'s `needsLoginOptions` gate), so
     * the flag is never toggled there and would otherwise leave those logins DPoP-bound while the
     * assertions expect Bearer.  Pinning the flag off here — before the login surface reads it —
     * keeps that skip path honest.  DPoP tests re-enable it explicitly through the Login Options
     * toggle (their non-CA_OPAQUE configs always open that screen), which writes this same flag back
     * to true for the duration of the login.  This mirrors iOS, where the DPoP toggle is always
     * applied on the login-options surface rather than inferred from a helper parameter.
     */
    @Before
    open fun baselineDPoPOff() {
        SalesforceSDKManager.getInstance().useDPoP = false
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

            // Restore the mutable auth options to the test baseline.  useDPoP is pinned off (the
            // Bearer baseline established by baselineDPoPOff), not the SDK 14.0 production default of
            // true, so a DPoP test that enabled the flag mid-run cannot leak it into the next test.
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
     * authentication so the surface reloads as the in-app WebView.  Backing out also guarantees
     * the launch-time auth-config fetch has completed — the tab only appears once it resolves —
     * so the browser-login state has settled and can be safely overridden below without an
     * in-flight fetch clobbering it.
     *
     * @param expectCustomTab True when the caller wants a Chrome Custom Tab as the login surface.
     * False when the in-app WebView is needed (User Agent Flow, or HTTPS callback URIs that
     * cannot be verified as App Links).
     * @param forceAdvancedAuthentication Whether [SalesforceSDKManager.forceAdvancedAuthentication]
     * should be left enabled. When false, both the force flag and the derived
     * [SalesforceSDKManager.isBrowserLoginEnabled] cache are cleared so the re-selection below
     * loads the in-app WebView. This is independent of [expectCustomTab]: a caller can disable
     * forced advanced authentication while still expecting a Custom Tab when the OAuth callback
     * redirect is handled by the WebView internally (e.g. HTTPS sandbox callback URIs).
     */
    private fun ensureRegularAuthServer(expectCustomTab: Boolean, forceAdvancedAuthentication: Boolean = true) {
        val chromePage = ChromeCustomTabPageObject(composeTestRule)
        val regularAuthUrl = testConfig.getLoginHost(REGULAR_AUTH).url
        val loginServerManager = SalesforceSDKManager.getInstance().loginServerManager
        val alreadyOnRegularAuth =
            loginServerManager.selectedLoginServer?.url?.trim() == regularAuthUrl.trim()

        val sdkForcedAdvancedAuth = SalesforceSDKManager.getInstance().forceAdvancedAuthentication
        if (expectCustomTab && forceAdvancedAuthentication && alreadyOnRegularAuth && sdkForcedAdvancedAuth) {
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

        // Explicitly sync the SDK's forceAdvancedAuthentication to the caller's intent.
        // A prior loginAndValidate call with a different forceAdvancedAuthentication value
        // may have left it in the wrong state, causing the login surface to differ from
        // what the caller expects.
        setForcedAdvancedAuthEnabled(forceAdvancedAuthentication)

        // Switch to REGULAR_AUTH using LoginServerManager. With the activity resumed this fires the
        // pending-server observers.  A real server change re-runs the auth-config fetch; a
        // same-server re-selection (the flag-off case, already on REGULAR_AUTH) just reloads the
        // login surface.
        val regularAuthServer = loginServerManager.getLoginServerFromURL(regularAuthUrl)
        if (regularAuthServer != null) {
            // If the picker is showing after backOutToLoginActivity(), dismiss it by tapping the
            // regular-auth row — this triggers reloadWebView and closes the sheet. When the picker
            // is not showing, call setSelectedLoginServer directly as before.
            val loginPage = LoginPageObject(composeTestRule)
            val pickerShowing = composeTestRule
                .onAllNodesWithTag(LoginViewTestTags.SERVER_PICKER)
                .fetchSemanticsNodes()
                .isNotEmpty()
            if (pickerShowing) {
                loginPage.changeServerByUrl(regularAuthUrl)
            } else {
                loginServerManager.setSelectedLoginServer(regularAuthServer)
            }

            if (expectCustomTab) {
                // Reaching here means the server actually changed (a sticky ADVANCED_AUTH
                // selection) or forced advanced auth was just disabled but we still want a Custom
                // Tab (e.g. HTTPS callback URI path).  The re-launch is asynchronous: the SDK first
                // runs an auth-config network fetch (bounded by a multi-second timeout) and only
                // then generates the OAuth URL and launches the Custom Tab.  Wait for that tab to
                // actually appear rather than sleeping a fixed interval.
                chromePage.waitForCustomTab()
            } else if (pickerShowing) {
                // When the picker was showing, changeServerByUrl() tapped the server row which
                // triggers an auth-config fetch and then reloads the WebView. Wait for the
                // MORE_OPTIONS_BUTTON to confirm the LoginActivity's Compose is idle and the login
                // screen is fully in front before returning — this ensures the WebView reload has
                // begun and retryWebAction has the full timeout budget to wait for the login form.
                loginPage.waitForLoginScreen()
            }
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
        forceAdvancedAuthentication: Boolean = true,
        useWelcomeDiscovery: Boolean = false,
        isMultiUser: Boolean = false,
        useLoginPoolHost: Boolean = false,
    ) {
        // When forceAdvancedAuthentication is true (default) every login completes in a Custom Tab:
        // a ChromeCustomTabPageObject serves both roles — its inherited Compose actions
        // (openLoginOptions/changeServer) drive the LoginActivity top bar after backing out of the
        // tab, and its overridden credential actions drive the tab itself.
        //
        // When forceAdvancedAuthentication is false, a regular host uses the in-app WebView (see
        // [ensureRegularAuthServer]), while an ADVANCED_AUTH host still opens a Custom Tab because
        // its server configuration requires browser login. The WebView path supports User Agent
        // Flow and HTTPS callbacks that cannot be verified as App Links. The base
        // LoginPageObject's backOutToLoginActivity() is a no-op, so the shared flow is safe either
        // way.
        val loginPage: LoginPageObject =
            if (forceAdvancedAuthentication) ChromeCustomTabPageObject(composeTestRule)
            else LoginPageObject(composeTestRule)
        val authenticationPage: LoginPageObject =
            if (forceAdvancedAuthentication || knownLoginHostConfig == ADVANCED_AUTH) {
                ChromeCustomTabPageObject(composeTestRule)
            } else {
                loginPage
            }

        ensureRegularAuthServer(expectCustomTab = forceAdvancedAuthentication, forceAdvancedAuthentication = forceAdvancedAuthentication)

        val needsLoginOptions = !useWebServerFlow || !useHybridAuthToken || useDPoP ||
                !forceAdvancedAuthentication || knownAppConfig != CA_OPAQUE ||
                scopeSelection != EMPTY || useWelcomeDiscovery

        if (needsLoginOptions) {

            // Reach Login Options via the top bar.  For the Custom Tab flow the auto-launched tab
            // covers the top bar, so back out first; for the WebView flow this is a no-op (the top
            // bar is already in front).
            loginPage.backOutToLoginActivity()
            loginPage.openLoginOptions()

            if (useWebServerFlow) loginOptions.enableWebServerFlow()
            else loginOptions.disableWebServerFlow()

            if (useHybridAuthToken) loginOptions.enableHybridAuthToken()
            else loginOptions.disableHybridAuthToken()

            if (useDPoP) loginOptions.enableDPoP()
            else loginOptions.disableDPoP()

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
            authenticationPage.welcomeLogin(knownLoginHostConfig, knownUserConfig)
        } else if (useLoginPoolHost) {
            // Use the pool server URL from ui_test_config.json for the login host.
            // Credentials are taken from knownLoginHostConfig — same org, different login entry point.
            loginPage.backOutToLoginActivity()
            loginPage.changeServerByUrl(testConfig.requireLoginPoolHost())
            authenticationPage.login(knownLoginHostConfig, knownUserConfig)
        } else {
            if (knownLoginHostConfig != REGULAR_AUTH) {
                // Switching servers is a top-bar action, so surface LoginActivity first. Selecting
                // ADVANCED_AUTH launches a Custom Tab whether browser login was client-forced or
                // required by that server's authentication configuration.
                loginPage.backOutToLoginActivity()
                loginPage.changeServer(knownLoginHostConfig)
            }

            // Enter credentials in the Custom Tab for client-forced or server-required browser
            // login, and in the WebView otherwise.
            authenticationPage.login(knownLoginHostConfig, knownUserConfig)
        }
        app.waitForAppLoad()

        val expectedBMarker = when {
            forceAdvancedAuthentication -> Features.FEATURE_BROWSER_LOGIN_FORCE_FLAG
            knownLoginHostConfig == ADVANCED_AUTH ->
                Features.FEATURE_BROWSER_LOGIN_SERVER_AUTH_CONFIG
            else -> null
        }
        val expectedLMarker = when {
            useWelcomeDiscovery -> Features.FEATURE_LOGIN_SERVER_WELCOME_DISCOVERY
            // Pool server (login.salesforce.com, login.*.salesforce.com) registers L1, not L4.
            useLoginPoolHost -> Features.FEATURE_LOGIN_SERVER_PRODUCTION
            else -> Features.FEATURE_LOGIN_SERVER_MY_DOMAIN
        }
        val expectedAMarker = when {
            useWebServerFlow && useHybridAuthToken -> Features.FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID
            useWebServerFlow && !useHybridAuthToken -> Features.FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID
            !useWebServerFlow && useHybridAuthToken -> Features.FEATURE_AUTH_TYPE_USER_AGENT_HYBRID
            else -> Features.FEATURE_AUTH_TYPE_USER_AGENT_NON_HYBRID
        }
        val appConfig = testConfig.getApp(knownAppConfig)
        app.validateUser(
            knownLoginHostConfig,
            knownUserConfig,
            useWelcomeDiscovery,
            isMultiUser,
            expectAdvancedAuth = forceAdvancedAuthentication,
            isDpop = useDPoP,
            expectedBMarker = expectedBMarker,
            expectedLMarker = expectedLMarker,
            expectedAMarker = expectedAMarker,
            isJwt = appConfig.issuesJwt,
            isBeacon = appConfig.isBeacon,
        )
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
        expectAdvancedAuth: Boolean = true,
        isDpop: Boolean = false,
        useWebServerFlow: Boolean = true,
        useHybridAuthToken: Boolean = true,
    ) {
        restartApp()
        val shouldHaveBW = expectAdvancedAuth || knownLoginHostConfig == ADVANCED_AUTH
        val expectedBMarker = if (shouldHaveBW) Features.FEATURE_BROWSER_LOGIN_FORCE_FLAG else null
        val expectedLMarker = if (usesWelcomeDiscovery) {
            Features.FEATURE_LOGIN_SERVER_WELCOME_DISCOVERY
        } else {
            Features.FEATURE_LOGIN_SERVER_MY_DOMAIN
        }
        val expectedAMarker = when {
            useWebServerFlow && useHybridAuthToken -> Features.FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID
            useWebServerFlow && !useHybridAuthToken -> Features.FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID
            !useWebServerFlow && useHybridAuthToken -> Features.FEATURE_AUTH_TYPE_USER_AGENT_HYBRID
            else -> Features.FEATURE_AUTH_TYPE_USER_AGENT_NON_HYBRID
        }
        val appConfig = testConfig.getApp(knownAppConfig)
        app.validateUser(
            knownLoginHostConfig,
            knownUserConfig,
            usesWelcomeDiscovery,
            expectAdvancedAuth = expectAdvancedAuth,
            isDpop = isDpop,
            expectedBMarker = expectedBMarker,
            expectedLMarker = expectedLMarker,
            expectedAMarker = expectedAMarker,
            isJwt = appConfig.issuesJwt,
            isBeacon = appConfig.isBeacon,
        )
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
        forceAdvancedAuthentication: Boolean = true,
    ) {
        app.addNewAccount()
        loginAndValidate(
            knownAppConfig = knownAppConfig,
            scopeSelection = scopeSelection,
            useWebServerFlow = useWebServerFlow,
            useHybridAuthToken = useHybridAuthToken,
            useDPoP = useDPoP,
            forceAdvancedAuthentication = forceAdvancedAuthentication,
            knownLoginHostConfig = knownLoginHostConfig,
            knownUserConfig = otherUser,
            isMultiUser = true,
        )
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

        ensureRegularAuthServer(expectCustomTab = useWebServerFlow, forceAdvancedAuthentication = useWebServerFlow)

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

        // Dismissing Login Options re-launches the Custom Tab on the forced-advanced-auth path.
        // After backing out of that tab, the non-dismissable login picker is shown (W-23731759).
        // To reach the overflow menu, disable forced advanced authentication before closing the tab
        // so that ChromeCustomTabPageObject.tapLoginForAdminsMenuItem can dismiss the picker by
        // re-selecting the current server (which triggers reloadWebView with isBrowserLoginEnabled=false,
        // loading the in-app WebView instead of yet another Custom Tab).
        if (useWebServerFlow) {
            setForcedAdvancedAuthEnabled(false)
        }
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
        val appConfig = testConfig.getApp(knownAppConfig)
        app.validateUser(REGULAR_AUTH, user, expectAdvancedAuth = true, isDpop = useDPoP, expectedBMarker = Features.FEATURE_BROWSER_LOGIN_FOR_ADMIN, expectedLMarker = Features.FEATURE_LOGIN_SERVER_MY_DOMAIN, expectedAMarker = Features.FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID, isJwt = appConfig.issuesJwt, isBeacon = appConfig.isBeacon)
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
     *
     * When [expectDPoPBindingError] is true, additionally assert that the Custom Tab shows the
     * enforced-ECA DPoP rejection error page (`missing required dpop_jkt for code binding`), pinning
     * the failure to the specific server reason rather than mere absence of a user. Only the
     * DPoP-enforcement case renders this page; the invalid-consumer-key / invalid-scope negative
     * tests produce different errors and leave this off.
     */
    fun loginAndExpectFailure(
        consumerKey: String,
        redirectUri: String,
        scopes: String? = null,
        knownUserConfig: KnownUserConfig = user,
        useDPoP: Boolean = false,
        expectDPoPBindingError: Boolean = false,
    ) {
        val loginPage = ChromeCustomTabPageObject(composeTestRule)
        ensureRegularAuthServer(expectCustomTab = true)

        val userAccountManager = SalesforceSDKManager.getInstance().userAccountManager
        val initialUserCount = userAccountManager.authenticatedUsers?.size ?: 0

        // Back out of the auto-launched Custom Tab to reach Login Options, apply the override,
        // then let the Custom Tab re-launch with the new dynamic config.
        loginPage.backOutToLoginActivity()
        loginPage.openLoginOptions()

        // Deterministically set the DPoP precondition (mirrors loginAndValidate). Login Options
        // is always opened above for the dynamic config override, so this is a no-op toggle click
        // when the DPoP toggle is already in the desired state (e.g. the default off state that
        // cleanup() restores between tests).
        if (useDPoP) loginOptions.enableDPoP() else loginOptions.disableDPoP()

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

        // For DPoP enforcement, pin the failure to the specific server reason: the enforced ECA
        // rejects the unbound /authorize with an OAuth error page naming the missing dpop_jkt.
        if (expectDPoPBindingError) {
            assert(loginPage.isShowingDPoPBindingError()) {
                "Expected the Custom Tab to show the DPoP binding error (missing required dpop_jkt " +
                    "for code binding) after a rejected unbound login"
            }
        }
    }

    fun migrateAndValidate(
        knownAppConfig: KnownAppConfig,
        knownLoginHostConfig: KnownLoginHostConfig = REGULAR_AUTH,
        scopeSelection: ScopeSelection = EMPTY,
        knownUserConfig: KnownUserConfig = user,
        expectAdvancedAuth: Boolean = true,
        isMultiUser: Boolean = false,
        isDpop: Boolean = false,
        expectedAMarker: String? = Features.FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID,
    ) {
        val (preAccessToken, preRefreshToken) = app.getTokens()
        app.migrateToNewApp(knownAppConfig, scopeSelection)
        val (postAccessToken, postRefreshToken) = app.getTokens()

        // Assert tokens are new
        assert(preAccessToken != postAccessToken)
        assert(preRefreshToken != postRefreshToken)

        val shouldHaveBW = expectAdvancedAuth || knownLoginHostConfig == ADVANCED_AUTH
        val expectedBMarker = if (shouldHaveBW) Features.FEATURE_BROWSER_LOGIN_FORCE_FLAG else null
        val appConfig = testConfig.getApp(knownAppConfig)
        app.validateUser(
            knownLoginHostConfig,
            knownUserConfig,
            isMultiUser = isMultiUser,
            expectAdvancedAuth = expectAdvancedAuth,
            isDpop = isDpop,
            expectedBMarker = expectedBMarker,
            expectedAMarker = expectedAMarker,
            wasMigrated = true,
            isJwt = appConfig.issuesJwt,
            isBeacon = appConfig.isBeacon,
        )
        app.validateOAuthValues(knownAppConfig, scopeSelection)

        // Assert new tokens work
        app.revokeAccessToken()
        app.validateApiRequest()
    }

    /**
     * Drives the "Upgrade to DPoP" affordance for the current user's existing Bearer session and
     * validates the resulting DPoP-bound credential — same consumer key, new tokens, non-empty
     * nonce, valid thumbprint. Mirrors [migrateAndValidate] but for the same-config, DPoP-only
     * [com.salesforce.androidsdk.accounts.upgradeToDPoP] convenience rather than a full app
     * migration, so it takes no target [KnownAppConfig]/scopes — the config is unchanged.
     */
    fun upgradeToDPoPAndValidate(
        knownAppConfig: KnownAppConfig,
        knownLoginHostConfig: KnownLoginHostConfig = REGULAR_AUTH,
        knownUserConfig: KnownUserConfig = user,
        expectAdvancedAuth: Boolean = true,
        expectedAMarker: String? = Features.FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID,
    ) {
        val (preAccessToken, preRefreshToken) = app.getTokens()
        app.upgradeToDPoP()
        val (postAccessToken, postRefreshToken) = app.getTokens()

        // Assert tokens are new.
        assert(preAccessToken != postAccessToken)
        assert(preRefreshToken != postRefreshToken)

        val shouldHaveBW = expectAdvancedAuth || knownLoginHostConfig == ADVANCED_AUTH
        val expectedBMarker = if (shouldHaveBW) Features.FEATURE_BROWSER_LOGIN_FORCE_FLAG else null
        val appConfig = testConfig.getApp(knownAppConfig)
        app.validateUser(
            knownLoginHostConfig,
            knownUserConfig,
            expectAdvancedAuth = expectAdvancedAuth,
            isDpop = true,
            expectedBMarker = expectedBMarker,
            expectedAMarker = expectedAMarker,
            wasMigrated = true,
            isJwt = appConfig.issuesJwt,
            isBeacon = appConfig.isBeacon,
        )

        // The consumer key is unchanged — same app, only the DPoP binding changed.
        app.validateOAuthValues(knownAppConfig, scopeSelection = EMPTY)

        // Assert the newly DPoP-bound tokens work. upgradeToDPoP delegates to the refresh-token
        // migration path, so the "TM" (token-migration) UA feature flag is legitimately registered
        // and persists across subsequent refreshes — the marker tracks the migration mechanism, not
        // whether the connected app changed. Assert its presence.
        assertRevokeAndRefreshWorks(isRtr = false, isDpop = true, wasMigrated = true, isJwt = appConfig.issuesJwt)
    }

    /**
     * Drives the "Downgrade from DPoP" affordance for the current user's existing DPoP-bound
     * session and validates the resulting Bearer credential — same consumer key, new tokens, no
     * DPoP nonce. Mirrors [upgradeToDPoPAndValidate] but for the inverse, same-config
     * [com.salesforce.androidsdk.accounts.downgradeFromDPoP] convenience, so it takes no target
     * [KnownAppConfig]/scopes — the config is unchanged.
     */
    fun downgradeFromDPoPAndValidate(
        knownAppConfig: KnownAppConfig,
        knownLoginHostConfig: KnownLoginHostConfig = REGULAR_AUTH,
        knownUserConfig: KnownUserConfig = user,
        expectAdvancedAuth: Boolean = true,
        expectedAMarker: String? = Features.FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID,
    ) {
        val (preAccessToken, preRefreshToken) = app.getTokens()
        app.downgradeFromDPoP()
        val (postAccessToken, postRefreshToken) = app.getTokens()

        // Assert tokens are new.
        assert(preAccessToken != postAccessToken)
        assert(preRefreshToken != postRefreshToken)

        val shouldHaveBW = expectAdvancedAuth || knownLoginHostConfig == ADVANCED_AUTH
        val expectedBMarker = if (shouldHaveBW) Features.FEATURE_BROWSER_LOGIN_FORCE_FLAG else null
        val appConfig = testConfig.getApp(knownAppConfig)
        app.validateUser(
            knownLoginHostConfig,
            knownUserConfig,
            expectAdvancedAuth = expectAdvancedAuth,
            isDpop = false,
            expectedBMarker = expectedBMarker,
            expectedAMarker = expectedAMarker,
            wasMigrated = true,
            isJwt = appConfig.issuesJwt,
            isBeacon = appConfig.isBeacon,
        )

        // The consumer key is unchanged — same app, only the DPoP binding changed.
        app.validateOAuthValues(knownAppConfig, scopeSelection = EMPTY)

        // Assert the newly Bearer tokens work with no DPoP proof. downgradeFromDPoP delegates to
        // the refresh-token migration path, so the "TM" (token-migration) UA feature flag is
        // legitimately registered and persists across subsequent refreshes — the marker tracks the
        // migration mechanism, not whether the connected app changed. Assert its presence.
        assertRevokeAndRefreshWorks(isRtr = false, isDpop = false, wasMigrated = true, isJwt = appConfig.issuesJwt)
    }

    fun assertRevokeAndRefreshWorks(
        isRtr: Boolean,
        isDpop: Boolean = false,
        knownLoginHostConfig: KnownLoginHostConfig = REGULAR_AUTH,
        expectAdvancedAuth: Boolean = true,
        isMultiUser: Boolean = false,
        expectedAMarker: String? = Features.FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID,
        wasMigrated: Boolean = false,
        isJwt: Boolean = false,
        useLoginPoolHost: Boolean = false,
    ) {
        val (preAccessToken, preRefreshToken) = app.getTokens()
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
        }
        val expectedBMarker = if (expectAdvancedAuth) {
            Features.FEATURE_BROWSER_LOGIN_FORCE_FLAG
        } else {
            null
        }
        val expectedLMarker = if (useLoginPoolHost) {
            Features.FEATURE_LOGIN_SERVER_PRODUCTION
        } else {
            Features.FEATURE_LOGIN_SERVER_MY_DOMAIN
        }
        app.validateUserAgent(
            knownLoginHostConfig = knownLoginHostConfig,
            expectAdvancedAuth = expectAdvancedAuth,
            isMultiUser = isMultiUser,
            isRtr = isRtr,
            isDpop = isDpop,
            expectedBMarker = expectedBMarker,
            expectedLMarker = expectedLMarker,
            expectedAMarker = expectedAMarker,
            wasMigrated = wasMigrated,
            isJwt = isJwt,
        )
    }

    /**
     * Switches to a user already logged in and validates. Mirrors iOS `switchToUserAndValidateUser`.
     */
    fun switchToUserAndValidateUser(
        knownUserConfig: KnownUserConfig,
        knownLoginHostConfig: KnownLoginHostConfig = REGULAR_AUTH,
        expectAdvancedAuth: Boolean = true,
        isDpop: Boolean = false,
        expectedAMarker: String? = Features.FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID,
        isJwt: Boolean = false,
    ) {
        app.switchToUser(knownUserConfig)
        composeTestRule.waitForIdle()
        val shouldHaveBW = expectAdvancedAuth || knownLoginHostConfig == ADVANCED_AUTH
        val expectedBMarker = if (shouldHaveBW) Features.FEATURE_BROWSER_LOGIN_FORCE_FLAG else null
        app.validateUser(
            knownLoginHostConfig,
            knownUserConfig,
            isMultiUser = true,
            expectAdvancedAuth = expectAdvancedAuth,
            isDpop = isDpop,
            expectedBMarker = expectedBMarker,
            expectedAMarker = expectedAMarker,
            isJwt = isJwt,
        )
    }
}
