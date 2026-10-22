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
import com.salesforce.samples.authflowtester.pageObjects.ChromeCustomTabPageObject
import com.salesforce.samples.authflowtester.testUtility.AuthFlowTest
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.CA_OPAQUE
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig.COMMUNITY_AUTH
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig.REGULAR_AUTH
import com.salesforce.samples.authflowtester.testUtility.KnownUserConfig
import com.salesforce.samples.authflowtester.testUtility.NativeLoginConfigException
import com.salesforce.samples.authflowtester.testUtility.NativeLoginSetup
import com.salesforce.samples.authflowtester.testUtility.testConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Native Login (username and password in the app's own form) against the community host named by
 * the `nativeLogin` section of `ui_test_config.json`.
 *
 * Two headless External Client Apps are used: one where DPoP is optional and one where it is
 * required. The four-case matrix below crosses them with the app's DPoP setting; the remaining
 * tests cover cancellation, the browser fallback, same-user re-authentication, Activity recreation
 * and multi-user isolation.
 *
 * Every test skips cleanly when `nativeLogin` is absent or incomplete. The app starts in Native
 * mode (see [beforeActivityLaunch]); [AuthFlowTest.cleanup] restores Browser mode.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class NativeLoginTests : AuthFlowTest() {

    private lateinit var setup: NativeLoginSetup

    /** Selects Native mode before the app launches so the first login attempt shows the native form. */
    override fun beforeActivityLaunch() {
        val nativeSetup = runCatching { testConfig.nativeLoginSetup() }.getOrNull() ?: return
        val app = nativeSetup.optionalDPoPApp
        AuthModeStore.nativeSettings = NativeLoginSettings(app.consumerKey, app.redirectUri, nativeSetup.host.url)
        AuthModeStore.applyMode(AuthMode.NATIVE)
    }

    @Before
    fun skipIfNativeLoginNotConfigured() {
        assumeTrue(
            "nativeLogin section missing or incomplete in ui_test_config.json; skipping Native Login tests",
            testConfig.hasNativeLoginConfig,
        )
        setup = testConfig.nativeLoginSetup()
    }

    // region Four-case matrix

    // Optional-DPoP ECA with DPoP off: Bearer session.
    @Test
    fun testNativeLogin_OptionalEca_DPoPOff_Bearer() {
        loginNativeAndValidate(setup.optionalDPoPApp, useDPoP = false)
        assertRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = true, // The headless ECAs have refresh token rotation enabled.
            knownLoginHostConfig = COMMUNITY_AUTH,
            expectAdvancedAuth = false,
            expectedAMarker = Features.FEATURE_AUTH_TYPE_NATIVE,
            isJwt = true,
            expectLoginServerMarker = false,
        )
    }

    // Optional-DPoP ECA with DPoP on: DPoP-bound session.
    @Test
    fun testNativeLogin_OptionalEca_DPoPOn_DPoP() {
        loginNativeAndValidate(setup.optionalDPoPApp, useDPoP = true)
        assertRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = true, // The headless ECAs have refresh token rotation enabled.
            isDpop = true,
            knownLoginHostConfig = COMMUNITY_AUTH,
            expectAdvancedAuth = false,
            expectedAMarker = Features.FEATURE_AUTH_TYPE_NATIVE,
            isJwt = true,
            expectLoginServerMarker = false,
        )
    }

    // Required-DPoP ECA with DPoP off: the server rejects the unbound request and no account is added.
    @Test
    fun testNativeLogin_RequiredEca_DPoPOff_RejectedNoAccount() {
        val rejection = loginNativeExpectingRejection(setup.requiredDPoPApp, useDPoP = false)
        assertTrue("Expected a rejection error, got '${rejection.error}'", rejection.error.isNotBlank())
        // Enforced-DPoP ECA: 400 invalid_request for the missing dpop_jkt. invalid_grant would mean a
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

    // Required-DPoP ECA with DPoP on: DPoP-bound session.
    @Test
    fun testNativeLogin_RequiredEca_DPoPOn_DPoP() {
        loginNativeAndValidate(setup.requiredDPoPApp, useDPoP = true)
        assertRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = true, // The headless ECAs have refresh token rotation enabled.
            isDpop = true,
            knownLoginHostConfig = COMMUNITY_AUTH,
            expectAdvancedAuth = false,
            expectedAMarker = Features.FEATURE_AUTH_TYPE_NATIVE,
            isJwt = true,
            expectLoginServerMarker = false,
        )
    }

    // endregion

    // region Cancellation and fallback

    // Cancel is offered only when there is an account to return to. Cancelling an add-account
    // attempt, idle or mid-login, leaves the existing session untouched.
    @Test
    fun testNativeLogin_CancelDuringLogin_NoNewAccount() {
        loginNativeAndValidate(setup.optionalDPoPApp, useDPoP = false)
        val (accessTokenBefore, _) = app.getTokens()

        // Idle cancel: deterministic.
        app.addNewAccount()
        nativeLogin.waitForNativeLogin()
        assertTrue("Cancel should be offered when an account exists", nativeLogin.hasCancelButton())
        nativeLogin.enterCredentials(setup.user)
        nativeLogin.tapCancel()
        app.waitForAppLoad()
        assertEquals(1, SalesforceSDKManager.getInstance().userAccountManager.authenticatedUsers?.size)
        assertEquals(accessTokenBefore, app.getTokens().accessToken)

        // Cancel right after submitting: the in-flight login must not add or replace an account.
        app.addNewAccount()
        nativeLogin.enterCredentials(setup.user)
        nativeLogin.tapLogin()
        nativeLogin.tapCancel()
        app.waitForAppLoad()
        Thread.sleep(LOGIN_SETTLE_MS)
        assertEquals(1, SalesforceSDKManager.getInstance().userAccountManager.authenticatedUsers?.size)
    }

    // The fallback button hands off to the SDK's browser login, which uses the SDK's browser
    // configuration (boot config) rather than the Native Login ECA.
    @Test
    fun testNativeLogin_BrowserFallback_UsesBrowserConfig() {
        nativeLogin.waitForNativeLogin()
        with(SalesforceSDKManager.getInstance()) {
            // The browser fallback logs in to the selected login server; point it at the community.
            loginServerManager.getLoginServerFromURL(setup.host.url)?.let {
                loginServerManager.setSelectedLoginServer(it)
            }
        }
        nativeLogin.tapBrowserFallback()

        val chrome = ChromeCustomTabPageObject(composeTestRule)
        chrome.login(COMMUNITY_AUTH, KnownUserConfig.FIRST)
        app.waitForAppLoad()

        app.validateUserAgentFlags(present = setOf(Features.FEATURE_BROWSER_LOGIN_FORCE_FLAG))
        app.validateNativeOAuthValues(testConfig.getApp(CA_OPAQUE), isDpop = false)
        assertNotEquals(
            "Browser fallback must not use the Native Login ECA",
            setup.optionalDPoPApp.consumerKey,
            SalesforceSDKManager.getInstance().userAccountManager.currentUser?.clientId,
        )
    }

    // endregion

    // region Re-authentication, recreation and multi-user

    // The same user logs in again with DPoP off after a DPoP login: one account remains and its
    // session is Bearer, with no DPoP marker left over.
    @Test
    fun testNativeLogin_SameUserDPoPToBearerReauth() {
        loginNativeAndValidate(setup.optionalDPoPApp, useDPoP = true)
        assertEquals("DPoP", app.getTokenType())

        app.addNewAccount()
        loginNativeAndValidate(setup.optionalDPoPApp, useDPoP = false)

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
        loginNativeAndValidate(setup.optionalDPoPApp, useDPoP = false)
    }

    // A DPoP Native Login user and a Bearer browser user coexist with independent sessions.
    @Test
    fun testNativeLogin_MultiUser_Isolation() {
        loginNativeAndValidate(setup.optionalDPoPApp, useDPoP = true)
        val (nativeAccessToken, nativeRefreshToken) = app.getTokens()
        val nativeUsername = setup.user.username

        // Second account through the browser login: switch the form to Browser mode, then log in.
        app.addNewAccount()
        nativeLogin.waitForNativeLogin()
        nativeLogin.openLoginOptions()
        nativeLogin.selectMode(AuthMode.BROWSER)
        nativeLogin.closeLoginOptions()
        loginAndValidate(
            knownAppConfig = CA_OPAQUE,
            knownLoginHostConfig = REGULAR_AUTH,
            knownUserConfig = otherUser,
            isMultiUser = true,
        )
        val (browserAccessToken, _) = app.getTokens()
        assertNotEquals(nativeAccessToken, browserAccessToken)
        assertEquals("Bearer", app.getTokenType())

        // Switching back, the Native Login user is unchanged and still DPoP-bound.
        val users = SalesforceSDKManager.getInstance().userAccountManager.authenticatedUsers.orEmpty()
        assertEquals(2, users.size)
        app.switchToUser(KnownUserConfig.FIRST, COMMUNITY_AUTH)
        assertEquals(nativeUsername, SalesforceSDKManager.getInstance().userAccountManager.currentUser?.username)
        assertEquals("DPoP", app.getTokenType())
        assertEquals(nativeAccessToken, app.getTokens().accessToken)
        assertEquals(nativeRefreshToken, app.getTokens().refreshToken)
    }

    // endregion

    private fun recreateResumedActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val activity: Activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .firstOrNull { it is NativeLoginActivity }
                ?: throw AssertionError("No resumed NativeLoginActivity to recreate")
            activity.recreate()
        }
        composeTestRule.waitForIdle()
    }

    private companion object {
        const val LOGIN_SETTLE_MS = 5_000L
    }
}

/** Validates the `nativeLogin` section shape on its own, so a bad config fails with a clear message. */
@RunWith(AndroidJUnit4::class)
class UITestConfigNativeLoginTest {

    @Test
    fun testNativeLoginConfigIsValid() {
        assumeTrue("nativeLogin not configured", testConfig.nativeLogin != null)
        val setup = try {
            testConfig.nativeLoginSetup()
        } catch (e: NativeLoginConfigException) {
            throw AssertionError("Invalid nativeLogin configuration: ${e.message}", e)
        }
        assertEquals(COMMUNITY_AUTH.name.lowercase(), setup.host.name)
        assertTrue(setup.optionalDPoPApp.consumerKey.isNotBlank())
        assertTrue(setup.requiredDPoPApp.consumerKey.isNotBlank())
        assertNotEquals(setup.optionalDPoPApp.consumerKey, setup.requiredDPoPApp.consumerKey)
        assertFalse(setup.user.username.isBlank())
    }
}
