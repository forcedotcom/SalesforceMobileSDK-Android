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
package com.salesforce.samples.authflowtester

import android.app.Activity
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.Stage
import com.salesforce.androidsdk.app.Features
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.auth.interfaces.NativeLoginResult
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.ui.AuthenticationMode
import com.salesforce.androidsdk.ui.DebugNativeLoginActivity
import com.salesforce.androidsdk.ui.NativeLoginDebugSettings
import com.salesforce.samples.authflowtester.pageObjects.ChromeCustomTabPageObject
import com.salesforce.samples.authflowtester.testUtility.AppConfig
import com.salesforce.samples.authflowtester.testUtility.AuthFlowTest
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.CA_OPAQUE
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig.NATIVE_LOGIN
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig.REGULAR_AUTH
import com.salesforce.samples.authflowtester.testUtility.KnownUserConfig
import com.salesforce.samples.authflowtester.testUtility.LoginHost
import com.salesforce.samples.authflowtester.testUtility.NativeLoginConfigException
import com.salesforce.samples.authflowtester.testUtility.NativeLoginSetup
import com.salesforce.samples.authflowtester.testUtility.UITestConfig
import com.salesforce.samples.authflowtester.testUtility.User
import com.salesforce.samples.authflowtester.testUtility.testConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.json.JSONObject
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger

/**
 * Native Login (username and password in the SDK's debug Native Login form) against the
 * `native_login` login host of `ui_test_config.json`.
 *
 * Two headless External Client Apps from `apps` are used: `eca_jwt_headless` accepts DPoP and
 * `eca_jwt_dpop_headless` requires it. The four-case matrix below crosses them with the app's DPoP
 * setting; the remaining tests cover a relaunch of the session (an Activity relaunch in the
 * same process, not a cold restart), cancellation, the browser fallback, same-user
 * re-authentication, Activity recreation and multi-user isolation.
 *
 * Every test skips when neither headless ECA is configured, and fails when only one of them is or
 * `native_login` is missing. The app starts in Native mode (see [beforeActivityLaunch]);
 * [AuthFlowTest.cleanup] restores Browser mode.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class NativeLoginTests : AuthFlowTest() {

    private lateinit var setup: NativeLoginSetup
    private val nativeUserConfig by lazy { userConfigForApiLevel(NATIVE_LOGIN) }

    /** Selects Native mode before the app launches so the first login attempt shows the native form. */
    override fun beforeActivityLaunch() {
        val nativeSetup = runCatching { testConfig.nativeLoginSetup(nativeUserConfig) }.getOrNull() ?: return
        val app = nativeSetup.ecaJwtHeadless
        SalesforceSDKManager.getInstance().debugLoginSettings.apply(
            JSONObject()
                .put(NativeLoginDebugSettings.JSON_CONSUMER_KEY, app.consumerKey)
                .put(NativeLoginDebugSettings.JSON_REDIRECT_URI, app.redirectUri)
                .put(NativeLoginDebugSettings.JSON_COMMUNITY_URL, nativeSetup.host.url)
                .put(NativeLoginDebugSettings.JSON_AUTHENTICATION_MODE, "native")
                .toString(),
        )
    }

    @Before
    fun resolveNativeLoginSetup() {
        assumeTrue(
            "Neither eca_jwt_headless nor eca_jwt_dpop_headless is in ui_test_config.json; skipping Native Login tests",
            testConfig.hasNativeLoginApps,
        )
        setup = try {
            testConfig.nativeLoginSetup(nativeUserConfig)
        } catch (e: NativeLoginConfigException) {
            throw AssertionError("Invalid Native Login configuration: ${e.message}", e)
        }
    }

    // region Four-case matrix

    // eca_jwt_headless with DPoP off: Bearer session.
    @Test
    fun testECAJwtHeadless_DPoPOff_Bearer() {
        loginNativeAndValidate(setup.ecaJwtHeadless, useDPoP = false)
        assertNativeRevokeAndRefreshWorks(isDpop = false)
    }

    // eca_jwt_headless with DPoP on: DPoP-bound session.
    @Test
    fun testECAJwtHeadless_DPoPOn_DPoP() {
        loginNativeAndValidate(setup.ecaJwtHeadless, useDPoP = true)
        assertNativeRevokeAndRefreshWorks(isDpop = true)
    }

    // eca_jwt_dpop_headless with DPoP off: the server rejects the unbound request and no account is added.
    @Test
    fun testECAJwtDPoPHeadless_DPoPOff_RejectedNoAccount() {
        val rejection = loginNativeExpectingRejection(setup.ecaJwtDPoPHeadless, useDPoP = false)
        // The SDK reports an authorize failure as InvalidCredentials; UnknownError would mean a later stage failed.
        assertTrue(
            "Expected the InvalidCredentials result, got '${rejection.error}'",
            rejection.error.startsWith(NativeLoginResult.InvalidCredentials.name),
        )
        // DPoP-required ECA: 400 invalid_request for the missing dpop_jkt. invalid_grant would mean a
        // credential or user problem, and a 5xx an outage; neither proves the DPoP requirement.
        assertTrue(
            "Expected an authorize rejection for the missing dpop_jkt, without a DPoP proof: ${rejection.diagnostics}",
            rejection.diagnostics.contains("stage=authorize status=400 error=invalid_request dpop=false") &&
                rejection.diagnostics.contains("dpop_jkt"),
        )
        assertFalse(
            "The rejection must not be invalid_grant: ${rejection.diagnostics}",
            rejection.diagnostics.contains("invalid_grant"),
        )
        assertEquals(null, SalesforceSDKManager.getInstance().userAccountManager.authenticatedUsers)
    }

    // eca_jwt_dpop_headless with DPoP on: DPoP-bound session.
    @Test
    fun testECAJwtDPoPHeadless_DPoPOn_DPoP() {
        loginNativeAndValidate(setup.ecaJwtDPoPHeadless, useDPoP = true)
        assertNativeRevokeAndRefreshWorks(isDpop = true)
    }

    // endregion

    // region Relaunch

    // The restored Bearer session after an Activity relaunch in the same process (not a cold
    // restart: the test runner shares the app's process).
    @Test
    fun testECAJwtHeadless_DPoPOff_Relaunch_Bearer() {
        loginNativeAndValidate(setup.ecaJwtHeadless, useDPoP = false)
        relaunchNativeAndValidate(setup, setup.ecaJwtHeadless, useDPoP = false)
        assertNativeRevokeAndRefreshWorks(isDpop = false)
    }

    // The restored DPoP-bound session after an Activity relaunch in the same process; the binding
    // survives a refresh after the relaunch.
    @Test
    fun testECAJwtDPoPHeadless_DPoPOn_Relaunch_DPoP() {
        loginNativeAndValidate(setup.ecaJwtDPoPHeadless, useDPoP = true)
        relaunchNativeAndValidate(setup, setup.ecaJwtDPoPHeadless, useDPoP = true)
        assertNativeRevokeAndRefreshWorks(isDpop = true)
    }

    // endregion

    // region Cancellation and fallback

    // Cancel is offered only when there is an account to return to. Cancelling an add-account
    // attempt, idle or mid-login, leaves the existing session untouched.
    @Test
    fun testNativeLogin_CancelDuringLogin_NoNewAccount() {
        loginNativeAndValidate(setup.ecaJwtHeadless, useDPoP = false)
        val (accessTokenBefore, refreshTokenBefore) = app.getTokens()

        // Idle cancel.
        app.addNewAccount()
        nativeLogin.waitForNativeLogin()
        assertTrue("Cancel should be offered when an account exists", nativeLogin.hasCancelButton())
        nativeLogin.enterCredentials(setup.user)
        nativeLogin.tapCancel()
        app.waitForAppLoad()
        assertEquals(1, SalesforceSDKManager.getInstance().userAccountManager.authenticatedUsers?.size)
        assertEquals(accessTokenBefore, app.getTokens().accessToken)

        // In-flight cancel: the token response is held until after the cancel, then delivered. The
        // late response must not add or replace an account, or change the session.
        val tokenTypeBefore = app.getTokenType()
        val userAgentBefore = app.getUserAgent()
        val holdingHttpAccess = holdTokenResponses()
        app.addNewAccount()
        nativeLogin.enterCredentials(setup.user)
        nativeLogin.tapLogin()
        assertTrue(
            "The Native Login token request was not sent",
            holdingHttpAccess.responseHeld.await(HOLD_TIMEOUT_S, SECONDS),
        )
        // Only a successful token response proves the cancel is what keeps the session unchanged.
        holdingHttpAccess.failure?.let { throw AssertionError("The Native Login token request failed", it) }
        assertEquals("Held token response status", 200, holdingHttpAccess.heldStatus)
        nativeLogin.tapCancel()
        app.waitForAppLoad()
        holdingHttpAccess.release.countDown()
        assertTrue(
            "The held token response was not delivered",
            holdingHttpAccess.responseDelivered.await(HOLD_TIMEOUT_S, SECONDS),
        )
        Thread.sleep(LATE_COMPLETION_SETTLE_MS)
        composeTestRule.waitForIdle()

        assertEquals(1, holdingHttpAccess.tokenRequestCount.get())
        assertFalse("The cancelled login reopened the form", isNativeLoginResumed())
        val userAccountManager = SalesforceSDKManager.getInstance().userAccountManager
        assertEquals(1, userAccountManager.authenticatedUsers?.size)
        assertEquals(accessTokenBefore, userAccountManager.currentUser?.authToken)
        assertEquals(accessTokenBefore, app.getTokens().accessToken)
        assertEquals(refreshTokenBefore, app.getTokens().refreshToken)
        assertEquals(tokenTypeBefore, app.getTokenType())
        assertEquals(userAgentBefore, app.getUserAgent())
    }

    // The fallback button hands off to the SDK's browser login, which uses the SDK's browser
    // configuration (boot config) rather than the Native Login ECA.
    @Test
    fun testNativeLogin_BrowserFallback_UsesBrowserConfig() {
        nativeLogin.waitForNativeLogin()
        with(SalesforceSDKManager.getInstance()) {
            // The fallback uses the selected browser-login server. Register this endpoint when it
            // is not already present so its native-login credentials are never sent to regular_auth.
            loginServerManager.addCustomLoginServer("Native Login", setup.host.url)
        }
        nativeLogin.tapBrowserFallback()

        val chrome = ChromeCustomTabPageObject(composeTestRule)
        chrome.login(NATIVE_LOGIN, setup.knownUserConfig)
        app.waitForAppLoad()

        app.validateUserAgentFlags(present = setOf(Features.FEATURE_BROWSER_LOGIN_FORCE_FLAG))
        app.validateNativeOAuthValues(testConfig.getApp(CA_OPAQUE), isDpop = false)
        assertNotEquals(
            "Browser fallback must not use the Native Login ECA",
            setup.ecaJwtHeadless.consumerKey,
            SalesforceSDKManager.getInstance().userAccountManager.currentUser?.clientId,
        )
    }

    // endregion

    // region Re-authentication, recreation and multi-user

    // The same user logs in again with DPoP off after a DPoP login: one account remains and its
    // session is Bearer, with no DPoP marker left over.
    @Test
    fun testNativeLogin_SameUserDPoPToBearerReauth() {
        loginNativeAndValidate(setup.ecaJwtHeadless, useDPoP = true)
        assertEquals("DPoP", app.getTokenType())

        app.addNewAccount()
        loginNativeAndValidate(setup.ecaJwtHeadless, useDPoP = false)

        assertEquals(1, SalesforceSDKManager.getInstance().userAccountManager.authenticatedUsers?.size)
        assertEquals("Bearer", app.getTokenType())
        app.validateUserAgentFlags(present = setOf(Features.FEATURE_NATIVE_LOGIN), absent = setOf("DP"))
    }

    // Recreating the Native Login Activity keeps the typed username and the screen remains usable.
    @Test
    fun testNativeLogin_ActivityRecreation() {
        nativeLogin.waitForNativeLogin()
        nativeLogin.enterUsernameOnly(setup.user.username)

        recreateResumedActivity()
        nativeLogin.waitForNativeLogin()
        assertEquals(setup.user.username, nativeLogin.usernameFieldText())

        // The form still logs in after recreation.
        loginNativeAndValidate(setup.ecaJwtHeadless, useDPoP = false)
    }

    // A DPoP Native Login user and a Bearer browser user coexist with independent sessions.
    @Test
    fun testNativeLogin_MultiUser_Isolation() {
        loginNativeAndValidate(setup.ecaJwtHeadless, useDPoP = true)
        val (nativeAccessToken, nativeRefreshToken) = app.getTokens()
        val nativeUsername = setup.user.username

        // Second account through the browser login: switch the form to Browser mode, then log in.
        app.addNewAccount()
        nativeLogin.waitForNativeLogin()
        nativeLogin.openLoginOptions()
        nativeLogin.selectMode(AuthenticationMode.BROWSER)
        nativeLogin.closeLoginOptions()
        loginAndValidate(
            knownAppConfig = CA_OPAQUE,
            knownLoginHostConfig = REGULAR_AUTH,
            knownUserConfig = user,
            isMultiUser = true,
        )
        val (browserAccessToken, _) = app.getTokens()
        assertNotEquals(nativeAccessToken, browserAccessToken)
        assertEquals("Bearer", app.getTokenType())

        // Switching back, the Native Login user is unchanged and still DPoP-bound.
        val users = SalesforceSDKManager.getInstance().userAccountManager.authenticatedUsers.orEmpty()
        assertEquals(2, users.size)
        app.switchToUser(setup.knownUserConfig, NATIVE_LOGIN)
        assertEquals(nativeUsername, SalesforceSDKManager.getInstance().userAccountManager.currentUser?.username)
        assertEquals("DPoP", app.getTokenType())
        assertEquals(nativeAccessToken, app.getTokens().accessToken)
        assertEquals(nativeRefreshToken, app.getTokens().refreshToken)
    }

    // endregion

    /**
     * Revokes the access token, refreshes and validates the Native Login session markers (A5, L5,
     * NL, and DP only for DPoP) after the refresh.
     */
    private fun assertNativeRevokeAndRefreshWorks(isDpop: Boolean) {
        assertRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = true, // The headless ECAs have refresh token rotation enabled.
            isDpop = isDpop,
            knownLoginHostConfig = NATIVE_LOGIN,
            expectAdvancedAuth = false,
            expectedAMarker = Features.FEATURE_AUTH_TYPE_NATIVE,
            isJwt = true,
        )
        app.validateUserAgentFlags(present = setOf(Features.FEATURE_NATIVE_LOGIN))
    }

    private var originalHttpAccess: HttpAccess? = null

    @After
    fun restoreHttpAccess() {
        val current = HttpAccess.DEFAULT
        if (current is TokenResponseHoldingHttpAccess) current.release.countDown()
        originalHttpAccess?.let { HttpAccess.DEFAULT = it }
        originalHttpAccess = null
        RestClient.clearCaches(null)
    }

    /**
     * Routes the next Native Login through [TokenResponseHoldingHttpAccess]. The Native Login
     * manager is built when its form opens, from the cached unauthenticated client, so that cache
     * is cleared here, before the form opens.
     */
    private fun holdTokenResponses(): TokenResponseHoldingHttpAccess {
        val current = HttpAccess.DEFAULT
        originalHttpAccess = current
        return TokenResponseHoldingHttpAccess(current).also {
            HttpAccess.DEFAULT = it
            RestClient.clearCaches(null)
        }
    }

    /**
     * Sends `/services/oauth2/token` requests to the server, then holds each response until
     * [release], so a cancel can land while the login is in flight. A transport failure is recorded
     * in [failure] and the response status in [heldStatus], so the test can require a successful
     * response before cancelling.
     */
    private class TokenResponseHoldingHttpAccess(private val delegate: HttpAccess) : HttpAccess(null, delegate.userAgent) {
        val responseHeld = CountDownLatch(1)
        val release = CountDownLatch(1)
        val responseDelivered = CountDownLatch(1)
        val tokenRequestCount = AtomicInteger()
        @Volatile var heldStatus: Int? = null
        @Volatile var failure: IOException? = null

        override fun createNewClientBuilder(): OkHttpClient.Builder =
            delegate.createNewClientBuilder().addInterceptor { chain ->
                val request = chain.request()
                if (!request.url.encodedPath.endsWith(TOKEN_ENDPOINT_PATH)) return@addInterceptor chain.proceed(request)
                tokenRequestCount.incrementAndGet()
                try {
                    val response = try {
                        chain.proceed(request)
                    } catch (e: IOException) {
                        failure = e
                        throw e
                    }
                    heldStatus = response.code
                    responseHeld.countDown()
                    release.await(HOLD_TIMEOUT_S, SECONDS)
                    response
                } finally {
                    responseHeld.countDown()
                    responseDelivered.countDown()
                }
            }

        override fun hasNetwork() = delegate.hasNetwork()
    }

    private fun isNativeLoginResumed(): Boolean {
        var resumed = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            resumed = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .any { it is DebugNativeLoginActivity }
        }
        return resumed
    }

    private fun recreateResumedActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val activity: Activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .firstOrNull { it is DebugNativeLoginActivity }
                ?: throw AssertionError("No resumed DebugNativeLoginActivity to recreate")
            activity.recreate()
        }
        composeTestRule.waitForIdle()
    }

    private companion object {
        const val TOKEN_ENDPOINT_PATH = "/services/oauth2/token"
        const val HOLD_TIMEOUT_S = 60L
        const val LATE_COMPLETION_SETTLE_MS = 2_000L
    }
}

/** Validates the Native Login fixtures on their own, so a bad config fails with a clear message. */
@RunWith(AndroidJUnit4::class)
class UITestConfigNativeLoginTest {

    @Test
    fun testNativeLoginConfigIsValid() {
        assumeTrue("Native Login apps not configured", testConfig.hasNativeLoginApps)
        val setup = try {
            testConfig.nativeLoginSetup()
        } catch (e: NativeLoginConfigException) {
            throw AssertionError("Invalid Native Login configuration: ${e.message}", e)
        }
        assertEquals(NATIVE_LOGIN.name.lowercase(), setup.host.name)
        assertTrue(setup.ecaJwtHeadless.consumerKey.isNotBlank())
        assertTrue(setup.ecaJwtDPoPHeadless.consumerKey.isNotBlank())
        assertNotEquals(setup.ecaJwtHeadless.consumerKey, setup.ecaJwtDPoPHeadless.consumerKey)
        assertFalse(setup.user.username.isBlank())
    }
}

/** The Native Login fixture rules, on in-memory configs with placeholder values. */
@RunWith(AndroidJUnit4::class)
class NativeLoginConfigRulesTest {

    @Test
    fun test_givenNoHeadlessApps_whenChecked_thenNativeLoginIsNotConfigured() {
        val config = config(apps = emptyList())
        assertFalse(config.hasNativeLoginApps)
    }

    @Test
    fun test_givenOneHeadlessApp_whenResolved_thenFailsNamingTheMissingApp() {
        val config = config(apps = listOf(app("eca_jwt_headless")))
        assertTrue(config.hasNativeLoginApps)
        val error = assertThrows(NativeLoginConfigException::class.java) { config.nativeLoginSetup() }
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("missing: eca_jwt_dpop_headless"))
    }

    @Test
    fun test_givenNoNativeLoginHost_whenResolved_thenFailsNamingTheHost() {
        val config = config(hosts = emptyList())
        val error = assertThrows(NativeLoginConfigException::class.java) { config.nativeLoginSetup() }
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("native_login"))
    }

    @Test
    fun test_givenNativeLoginHostWithoutUsers_whenResolved_thenFails() {
        val config = config(hosts = listOf(LoginHost("native_login", HOST_URL, emptyList())))
        val error = assertThrows(NativeLoginConfigException::class.java) { config.nativeLoginSetup() }
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("has no users"))
    }

    @Test
    fun test_givenEmptyConsumerKey_whenResolved_thenFailsNamingTheApp() {
        val config = config(apps = listOf(app("eca_jwt_headless").copy(consumerKey = " "), app("eca_jwt_dpop_headless")))
        val error = assertThrows(NativeLoginConfigException::class.java) { config.nativeLoginSetup() }
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("'eca_jwt_headless'"))
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("empty consumerKey"))
    }

    @Test
    fun test_givenNonHttpsRedirectUri_whenResolved_thenFailsNamingTheApp() {
        val config = config(apps = listOf(
            app("eca_jwt_headless"),
            app("eca_jwt_dpop_headless").copy(redirectUri = "sfdc://oauth/done"),
        ))
        val error = assertThrows(NativeLoginConfigException::class.java) { config.nativeLoginSetup() }
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("'eca_jwt_dpop_headless'"))
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("https:// redirectUri"))
    }

    @Test
    fun test_givenCompleteFixtures_whenResolved_thenUsesAppsAndSelectedNativeLoginUser() {
        val setup = config().nativeLoginSetup(KnownUserConfig.THIRD)
        assertEquals("eca_jwt_headless", setup.ecaJwtHeadless.name)
        assertEquals("eca_jwt_dpop_headless", setup.ecaJwtDPoPHeadless.name)
        assertEquals(HOST_URL, setup.host.url)
        assertEquals("third@example.com", setup.user.username)
        assertEquals(KnownUserConfig.THIRD, setup.knownUserConfig)
    }

    @Test
    fun test_givenThreeNativeLoginUsers_whenSelectedByApiLevel_thenRotatesAcrossConfiguredUsers() {
        val config = config()

        assertEquals(KnownUserConfig.FIRST, config.userConfigForApiLevel(KnownLoginHostConfig.NATIVE_LOGIN, apiLevel = 31, minSdk = 31))
        assertEquals(KnownUserConfig.SECOND, config.userConfigForApiLevel(KnownLoginHostConfig.NATIVE_LOGIN, apiLevel = 32, minSdk = 31))
        assertEquals(KnownUserConfig.THIRD, config.userConfigForApiLevel(KnownLoginHostConfig.NATIVE_LOGIN, apiLevel = 33, minSdk = 31))
        assertEquals(KnownUserConfig.FIRST, config.userConfigForApiLevel(KnownLoginHostConfig.NATIVE_LOGIN, apiLevel = 34, minSdk = 31))
    }

    @Test
    fun test_givenTenRegularUsers_whenSelectedByApiLevel_thenUsesEveryConfiguredUser() {
        val users = (1..10).map { index -> User("user$index@example.com", "placeholder") }
        val config = config(hosts = listOf(LoginHost("regular_auth", HOST_URL, users)))

        assertEquals(
            KnownUserConfig.entries.toList(),
            (31..40).map { apiLevel ->
                config.userConfigForApiLevel(KnownLoginHostConfig.REGULAR_AUTH, apiLevel, minSdk = 31)
            },
        )
    }

    @Test
    fun test_givenTenRegularUsers_whenSelectingOtherUser_thenUsesNextConfiguredUser() {
        val users = (1..10).map { index -> User("user$index@example.com", "placeholder") }
        val config = config(hosts = listOf(LoginHost("regular_auth", HOST_URL, users)))

        assertEquals(
            KnownUserConfig.EIGHTH,
            config.otherUserConfig(KnownLoginHostConfig.REGULAR_AUTH, KnownUserConfig.SEVENTH),
        )
    }

    @Test
    fun test_givenOneRegularUser_whenSelectedForCrossHostLogin_thenResolvesFirstUser() {
        val config = config(hosts = listOf(LoginHost("regular_auth", HOST_URL, listOf(User("user@example.com", "placeholder")))))

        assertEquals(
            KnownUserConfig.FIRST,
            config.userConfigForApiLevel(KnownLoginHostConfig.REGULAR_AUTH, apiLevel = 31, minSdk = 31),
        )
    }

    @Test
    fun test_givenThreeCommunityUsers_whenSelectedByApiLevel_thenRotatesAcrossConfiguredUsers() {
        val users = (1..3).map { index -> User("community$index@example.com", "placeholder") }
        val config = config(hosts = listOf(LoginHost("community_auth", HOST_URL, users)))

        assertEquals(
            listOf(
                KnownUserConfig.FIRST,
                KnownUserConfig.SECOND,
                KnownUserConfig.THIRD,
                KnownUserConfig.FIRST,
            ),
            (31..34).map { apiLevel ->
                config.userConfigForApiLevel(KnownLoginHostConfig.COMMUNITY_AUTH, apiLevel, minSdk = 31)
            },
        )
    }

    @Test
    fun test_givenNoNativeLoginHost_whenSelectedByApiLevel_thenFailsNamingTheHost() {
        val error = assertThrows(NativeLoginConfigException::class.java) {
            config(hosts = emptyList()).userConfigForApiLevel(
                KnownLoginHostConfig.NATIVE_LOGIN,
                apiLevel = 31,
                minSdk = 31,
            )
        }

        assertTrue(error.message.orEmpty().contains("native_login"))
    }

    private fun config(
        apps: List<AppConfig> = listOf(app("eca_jwt_headless"), app("eca_jwt_dpop_headless")),
        hosts: List<LoginHost> = listOf(
            LoginHost("native_login", HOST_URL, listOf(
                User("first@example.com", "placeholder"),
                User("second@example.com", "placeholder"),
                User("third@example.com", "placeholder"),
            )),
        ),
    ) = UITestConfig(loginHosts = hosts, apps = apps)

    private fun app(name: String) = AppConfig(name, "key_$name", "$HOST_URL/services/oauth2/echo", "api refresh_token")

    private companion object {
        const val HOST_URL = "https://example.my.site.com/path"
    }
}
