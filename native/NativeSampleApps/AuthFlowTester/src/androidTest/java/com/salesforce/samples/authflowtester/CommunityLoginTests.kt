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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.salesforce.androidsdk.app.Features.FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID
import com.salesforce.androidsdk.app.Features.FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.samples.authflowtester.testUtility.AuthFlowTest
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.ECA_JWT
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.ECA_JWT_DPOP
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.ECA_JWT_DPOP_RTR
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.ECA_OPAQUE
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig.COMMUNITY_AUTH
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig.REGULAR_AUTH
import com.salesforce.samples.authflowtester.testUtility.KnownUserConfig
import com.salesforce.samples.authflowtester.testUtility.ScopeSelection.EMPTY
import com.salesforce.samples.authflowtester.testUtility.testConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Tests for login flows against a community (Experience Cloud) login host, using the
 * `community_auth` login host from `ui_test_config.json`. Follows up on the W-24342940
 * investigation into community DPoP login: community sites route through an Experience Cloud
 * front door rather than a plain My Domain host, so these tests exist to confirm the existing
 * login/DPoP/multi-user/restart/migration helpers behave the same way against that front door as
 * they do against `regular_auth`.
 *
 * There is no dedicated "community" app config: the community in the AuthFlowTester test org
 * (`authflowtesting.msdk.sdb38.com`) is set up (W-24381845) so the existing apps' consumer
 * keys/redirect URIs/scopes work unchanged for the community user. Each test below picks the same
 * [com.salesforce.samples.authflowtester.testUtility.KnownAppConfig] its equivalent
 * `DPoPLoginTests`/`RTRLoginTests` scenario uses, so the app name's own `_jwt`/`_dpop`/`_rtr`
 * conventions continue to drive [AppConfig.issuesJwt]/[AppConfig.isDpop]/
 * [AppConfig.expectsRefreshTokenRotation] correctly with no community-specific overrides.
 *
 * `community_auth` requires a dedicated Experience Cloud site and is not provisioned in every
 * environment, so every test here starts with [assumeTrue] and skips cleanly when the host is
 * absent from `ui_test_config.json` (see [testConfig.hasLoginHost]).
 *
 * The `community_auth` login host only provisions a single user ([KnownUserConfig.FIRST]), so
 * every call below passes that explicitly rather than relying on the base class's
 * [user]/[otherUser] lazy properties (which pick an index up to [KnownUserConfig.FIFTH] depending
 * on API level and would go out of bounds against this host's single-user list).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class CommunityLoginTests : AuthFlowTest() {

    @Before
    fun skipIfCommunityAuthNotConfigured() {
        assumeTrue(
            "community_auth login host not present in ui_test_config.json; skipping community login tests",
            testConfig.hasLoginHost(COMMUNITY_AUTH),
        )
    }

    // region Basic Login Tests

    // Login to the community host using the hybrid auth token flow (Bearer); revoke+refresh works.
    @Test
    fun testCommunity_Hybrid() {
        loginAndValidate(
            knownAppConfig = ECA_OPAQUE,
            knownLoginHostConfig = COMMUNITY_AUTH,
            knownUserConfig = KnownUserConfig.FIRST,
        )
        assertCommunityRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = false,
        )
    }

    // Login to the community host without the hybrid auth token; revoke+refresh works.
    @Test
    fun testCommunity_NoHybrid() {
        loginAndValidate(
            knownAppConfig = ECA_OPAQUE,
            knownLoginHostConfig = COMMUNITY_AUTH,
            knownUserConfig = KnownUserConfig.FIRST,
            useHybridAuthToken = false,
        )
        assertCommunityRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = false,
            expectedAMarker = FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID,
        )
    }

    // Login to the community host with a JWT access token (ECA_JWT, Bearer, no DPoP) using the
    // hybrid auth token flow; revoke+refresh works.
    @Test
    fun testCommunityJwt_Hybrid() {
        loginAndValidate(
            knownAppConfig = ECA_JWT,
            knownLoginHostConfig = COMMUNITY_AUTH,
            knownUserConfig = KnownUserConfig.FIRST,
        )
        assertCommunityRevokeAndRefreshWorks(expectsRefreshTokenRotation = false, isJwt = true)
    }

    // Same as [testCommunityJwt_Hybrid] without the hybrid auth token.
    @Test
    fun testCommunityJwt_NoHybrid() {
        loginAndValidate(
            knownAppConfig = ECA_JWT,
            knownLoginHostConfig = COMMUNITY_AUTH,
            knownUserConfig = KnownUserConfig.FIRST,
            useHybridAuthToken = false,
        )
        assertCommunityRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = false,
            expectedAMarker = FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID,
            isJwt = true,
        )
    }

    // Login to the community host via the in-app WebView instead of the default Chrome Custom Tab
    // (forceAdvancedAuthentication = false). Exercises AuthorizationPageObject.tapAllowAfterLogin's
    // COMMUNITY_AUTH branch, which only ever fires on this WebView path (the Custom Tab path always
    // routes through ChromeCustomTabPageObject, which hardcodes ADVANCED_AUTH regardless of host).
    @Test
    fun testCommunity_ViaAlternateAuthSurface_WebView() {
        loginAndValidate(
            knownAppConfig = ECA_OPAQUE,
            knownLoginHostConfig = COMMUNITY_AUTH,
            knownUserConfig = KnownUserConfig.FIRST,
            forceAdvancedAuthentication = false,
        )
        assertCommunityRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = false,
            expectAdvancedAuth = false,
        )
    }

    // endregion

    // region DPoP Login Tests

    // Login to the community host with DPoP enabled using the hybrid auth token flow;
    // revoke+refresh works and the DPoP nonce rotates.
    @Test
    fun testCommunityDPoP_Hybrid() {
        loginAndValidate(
            knownAppConfig = ECA_JWT_DPOP,
            knownLoginHostConfig = COMMUNITY_AUTH,
            knownUserConfig = KnownUserConfig.FIRST,
            useDPoP = true,
        )
        assertCommunityRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = false,
            isDpop = true,
            isJwt = true,
        )
    }

    // Login to the community host with DPoP enabled, without the hybrid auth token.
    @Test
    fun testCommunityDPoP_NoHybrid() {
        loginAndValidate(
            knownAppConfig = ECA_JWT_DPOP,
            knownLoginHostConfig = COMMUNITY_AUTH,
            knownUserConfig = KnownUserConfig.FIRST,
            useHybridAuthToken = false,
            useDPoP = true,
        )
        assertCommunityRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = false,
            isDpop = true,
            expectedAMarker = FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID,
            isJwt = true,
        )
    }

    // Login to the community host with DPoP enabled via the in-app WebView surface.
    @Test
    fun testCommunityDPoP_ViaAlternateAuthSurface_WebView() {
        loginAndValidate(
            knownAppConfig = ECA_JWT_DPOP,
            knownLoginHostConfig = COMMUNITY_AUTH,
            knownUserConfig = KnownUserConfig.FIRST,
            useDPoP = true,
            forceAdvancedAuthentication = false,
        )
        assertCommunityRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = false,
            isDpop = true,
            expectAdvancedAuth = false,
            isJwt = true,
        )
    }

    // ECA JWT DPoP RTR has refresh token rotation (RTR) enabled, so this isn't exercising
    // something unique to the community host — it's the scenario (DPoPLoginTests.testECAJwtDPoP_
    // Hybrid's binding check plus RTRLoginTests.testECAOpaqueRtr_Hybrid's rotation check combined)
    // that also confirms the DPoP key pair/binding (thumbprint) stays steady across repeated
    // rotating refreshes, not just one, now against a community login host.
    @Test
    fun testCommunityDPoP_RefreshRotatesTokenAndPreservesBinding() {
        loginAndValidate(
            knownAppConfig = ECA_JWT_DPOP_RTR,
            knownLoginHostConfig = COMMUNITY_AUTH,
            knownUserConfig = KnownUserConfig.FIRST,
            useDPoP = true,
        )
        val keyThumbprintAfterLogin = app.getDpopInfo().keyThumbprint

        assertCommunityRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = true,
            isDpop = true,
            isJwt = true,
        )
        assertEquals(keyThumbprintAfterLogin, app.getDpopInfo().keyThumbprint)

        assertCommunityRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = true,
            isDpop = true,
            isJwt = true,
        )
        assertEquals(keyThumbprintAfterLogin, app.getDpopInfo().keyThumbprint)
    }

    // endregion

    // region Restart Tests

    // Login to the community host without DPoP (ECA_OPAQUE), restart the app, and confirm the
    // session survives the restart and revoke+refresh still targets the community.
    @Test
    fun testCommunity_WithRestart() {
        loginAndValidate(
            knownAppConfig = ECA_OPAQUE,
            knownLoginHostConfig = COMMUNITY_AUTH,
            knownUserConfig = KnownUserConfig.FIRST,
        )
        restartAndValidateUser(
            knownAppConfig = ECA_OPAQUE,
            knownLoginHostConfig = COMMUNITY_AUTH,
            knownUserConfig = KnownUserConfig.FIRST,
        )
        assertCommunityRevokeAndRefreshWorks(expectsRefreshTokenRotation = false)
    }

    // Login to the community host with DPoP, restart the app, and confirm the DPoP key pair
    // (loaded from AndroidKeyStore, not regenerated) and the session both survive the restart.
    @Test
    fun testCommunityDPoP_WithRestart() {
        loginAndValidate(
            knownAppConfig = ECA_JWT_DPOP,
            knownLoginHostConfig = COMMUNITY_AUTH,
            knownUserConfig = KnownUserConfig.FIRST,
            useDPoP = true,
        )
        val keyThumbprintBeforeRestart = app.getDpopInfo().keyThumbprint

        restartAndValidateUser(
            knownAppConfig = ECA_JWT_DPOP,
            knownLoginHostConfig = COMMUNITY_AUTH,
            knownUserConfig = KnownUserConfig.FIRST,
            isDpop = true,
        )
        assertEquals(keyThumbprintBeforeRestart, app.getDpopInfo().keyThumbprint)

        assertCommunityRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = false,
            isDpop = true,
            isJwt = true,
        )
    }

    // endregion

    // region Migration Tests

    // In-place upgrade: a Bearer session on an (unenforced) ECA against the community host is
    // bound to DPoP via the "Upgrade to DPoP" affordance, with no re-consent needed since the
    // consumer key/redirect URI/scopes are unchanged. Matches DPoPLoginTests.
    // testUpgrade_NonDPoP_InPlace_ToDPoP's app choice.
    @Test
    fun testCommunityUpgradeToDPoP_InPlace() {
        loginAndValidate(
            knownAppConfig = ECA_JWT,
            knownLoginHostConfig = COMMUNITY_AUTH,
            knownUserConfig = KnownUserConfig.FIRST,
            useDPoP = false,
        )
        upgradeToDPoPAndValidate(
            knownAppConfig = ECA_JWT,
            knownLoginHostConfig = COMMUNITY_AUTH,
            knownUserConfig = KnownUserConfig.FIRST,
        )
    }

    // In-place downgrade: a DPoP-bound session on an ECA against the community host is rolled
    // back to Bearer via the "Downgrade from DPoP" affordance. Matches DPoPLoginTests.
    // testDowngrade_DPoP_InPlace_ToBearer's app choice.
    @Test
    fun testCommunityDowngradeFromDPoP_InPlace() {
        loginAndValidate(
            knownAppConfig = ECA_JWT,
            knownLoginHostConfig = COMMUNITY_AUTH,
            knownUserConfig = KnownUserConfig.FIRST,
            useDPoP = true,
        )
        downgradeFromDPoPAndValidate(
            knownAppConfig = ECA_JWT,
            knownLoginHostConfig = COMMUNITY_AUTH,
            knownUserConfig = KnownUserConfig.FIRST,
        )
    }

    // endregion

    // region Logout Tests

    // Full logout (not just revoke) of a Bearer session followed by a fresh DPoP login: the initial
    // login is Bearer and the relogin is DPoP, so a stale token type retained across logout would
    // fail the token type assertion. The account is fully removed, and the subsequent login
    // establishes new tokens and a new DPoP key pair rather than reusing anything left over.
    @Test
    fun testCommunity_BearerLogoutThenReloginWithDPoP() {
        loginAndValidate(
            knownAppConfig = ECA_JWT,
            knownLoginHostConfig = COMMUNITY_AUTH,
            knownUserConfig = KnownUserConfig.FIRST,
            useDPoP = false,
        )
        assertEquals("Bearer", app.getDpopInfo().tokenType)
        val (accessTokenBeforeLogout, refreshTokenBeforeLogout) = app.getTokens()
        val keyThumbprintBeforeLogout = app.getDpopInfo().keyThumbprint

        val sdkManager = SalesforceSDKManager.getInstance()
        val communityUsername = testConfig.getUser(COMMUNITY_AUTH, KnownUserConfig.FIRST).username
        val communityAccount = sdkManager.userAccountManager.authenticatedUsers
            ?.find { it.username == communityUsername }
            ?: throw AssertionError("Community user account not found")
        sdkManager.logout(
            account = sdkManager.userAccountManager.buildAccount(communityAccount),
            frontActivity = null,
            showLoginPage = false,
        )
        waitForUserCount(sdkManager.userAccountManager, expectedCount = 0)

        loginAndValidate(
            knownAppConfig = ECA_JWT,
            knownLoginHostConfig = COMMUNITY_AUTH,
            knownUserConfig = KnownUserConfig.FIRST,
            useDPoP = true,
        )
        val dpopInfoAfterRelogin = app.getDpopInfo()
        assertEquals("DPoP", dpopInfoAfterRelogin.tokenType)
        val (accessTokenAfterRelogin, refreshTokenAfterRelogin) = app.getTokens()
        assertNotEquals(accessTokenBeforeLogout, accessTokenAfterRelogin)
        assertNotEquals(refreshTokenBeforeLogout, refreshTokenAfterRelogin)
        assertNotEquals(
            "A fresh login after full logout should generate a new DPoP key pair, not reuse the old one",
            keyThumbprintBeforeLogout,
            dpopInfoAfterRelogin.keyThumbprint,
        )
    }

    /**
     * Polls the user account manager until the authenticated user count reaches [expectedCount].
     * Used after triggering a logout to avoid a fixed-duration sleep. Mirrors the private helper of
     * the same name in MultiUserLoginTests.
     */
    private fun waitForUserCount(
        userAccountManager: com.salesforce.androidsdk.accounts.UserAccountManager,
        expectedCount: Int,
        timeoutMs: Long = 10_000L,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val count = userAccountManager.authenticatedUsers?.size ?: 0
            if (count == expectedCount) return
            Thread.sleep(250L)
        }
        val finalCount = userAccountManager.authenticatedUsers?.size ?: 0
        throw AssertionError(
            "Timed out after ${timeoutMs}ms waiting for user count to reach $expectedCount (was $finalCount)"
        )
    }

    // endregion

    // region Multi-Host Multi-User Tests

    // The community login host only provisions one user, so same-host multi-user isolation can't be
    // exercised the way DPoPLoginTests.testECAJwtDPoP_MultiUser_UniqueTokens does. Instead, pair the
    // single community user with a second, regular_auth-hosted DPoP user: tokens, DPoP key material,
    // and nonces must stay isolated per credential, and switching back and forth must not leak either
    // session's auth scheme or markers into the other.
    @Test
    fun testCommunityDPoP_And_RegularAuthDPoP_MultiHost_UniqueTokensAndIsolatedNonces() {
        loginAndValidate(
            knownAppConfig = ECA_JWT_DPOP,
            knownLoginHostConfig = COMMUNITY_AUTH,
            knownUserConfig = KnownUserConfig.FIRST,
            useDPoP = true,
        )
        val (communityAccessToken, communityRefreshToken) = app.getTokens()
        val communityKeyThumbprint = app.getDpopInfo().keyThumbprint

        addOtherUserAndValidate(
            knownAppConfig = ECA_JWT_DPOP,
            knownLoginHostConfig = REGULAR_AUTH,
            useDPoP = true,
        )
        val (otherAccessToken, otherRefreshToken) = app.getTokens()
        val otherKeyThumbprint = app.getDpopInfo().keyThumbprint

        // Tokens and DPoP key material must be unique across the two users/hosts.
        assertNotEquals(communityAccessToken, otherAccessToken)
        assertNotEquals(communityRefreshToken, otherRefreshToken)
        assertNotEquals(communityKeyThumbprint, otherKeyThumbprint)

        // Switch back to the community user; revoke+refresh must work with its own DPoP nonce.
        switchToUserAndValidateUser(
            KnownUserConfig.FIRST,
            knownLoginHostConfig = COMMUNITY_AUTH,
            isDpop = true,
            isJwt = true,
        )
        app.validateOAuthValues(knownAppConfig = ECA_JWT_DPOP, scopeSelection = EMPTY, knownLoginHostConfig = COMMUNITY_AUTH)
        assertCommunityRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = false,
            isDpop = true,
            isMultiUser = true,
            isJwt = true,
        )

        // Switch to the regular_auth user; revoke+refresh must work independently with its own
        // nonce.
        switchToUserAndValidateUser(
            otherUser,
            knownLoginHostConfig = REGULAR_AUTH,
            isDpop = true,
            isJwt = true,
        )
        app.validateOAuthValues(knownAppConfig = ECA_JWT_DPOP, scopeSelection = EMPTY)
        assertRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = false,
            isDpop = true,
            isMultiUser = true,
            isJwt = true,
        )
    }

    // endregion

    // region Community refresh endpoint helpers

    /**
     * Records every request URL sent through the SDK's shared [HttpAccess] so a test can observe
     * where a refresh actually went. Wraps the client via `newBuilder()` so the connection pool and
     * the SDK's network interceptors are preserved.
     */
    private class RecordingHttpAccess(
        private val delegate: HttpAccess,
        private val requestUrls: MutableList<String>,
    ) : HttpAccess(null, delegate.userAgent) {
        override fun getOkHttpClient() = delegate.okHttpClient.newBuilder()
            .addInterceptor { chain ->
                requestUrls.add(chain.request().url.toString())
                chain.proceed(chain.request())
            }
            .build()

        override fun hasNetwork() = delegate.hasNetwork()
    }

    private val requestUrls = CopyOnWriteArrayList<String>()
    private var originalHttpAccess: HttpAccess? = null

    @After
    fun restoreHttpAccess() {
        originalHttpAccess?.let { HttpAccess.DEFAULT = it }
        originalHttpAccess = null
    }

    // Idempotent: a restart can replace HttpAccess.DEFAULT, so this is re-run before each refresh.
    private fun recordHttpRequests() {
        val current = HttpAccess.DEFAULT
        if (current is RecordingHttpAccess) return
        originalHttpAccess = current
        HttpAccess.DEFAULT = RecordingHttpAccess(current, requestUrls)
    }

    /** Scheme + host + port + path of a URL, dropping any query, with no trailing slash. */
    private fun urlWithoutQuery(url: String) =
        url.toHttpUrl().newBuilder().query(null).build().toString().trimEnd('/')

    /**
     * [assertRevokeAndRefreshWorks] against the community host, additionally asserting that the
     * stored community URL equals the configured community (host and path) and that the refresh
     * request itself went to that community's token endpoint rather than the instance URL.
     */
    private fun assertCommunityRevokeAndRefreshWorks(
        expectsRefreshTokenRotation: Boolean,
        isDpop: Boolean = false,
        expectAdvancedAuth: Boolean = true,
        isMultiUser: Boolean = false,
        expectedAMarker: String? = FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID,
        isJwt: Boolean = false,
    ) {
        val configuredCommunityUrl = urlWithoutQuery(testConfig.getLoginHost(COMMUNITY_AUTH).url)
        val storedCommunityUrl = SalesforceSDKManager.getInstance().userAccountManager
            .currentUser?.communityUrl
        assertEquals(
            "Stored community URL should equal the configured community (host and path)",
            configuredCommunityUrl,
            storedCommunityUrl?.let { urlWithoutQuery(it) },
        )

        recordHttpRequests()
        requestUrls.clear()
        assertRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = expectsRefreshTokenRotation,
            isDpop = isDpop,
            knownLoginHostConfig = COMMUNITY_AUTH,
            expectAdvancedAuth = expectAdvancedAuth,
            isMultiUser = isMultiUser,
            expectedAMarker = expectedAMarker,
            isJwt = isJwt,
        )
        val tokenRequests = requestUrls.filter { it.toHttpUrl().encodedPath.endsWith("/services/oauth2/token") }
        assertTrue("Expected at least one token endpoint request during refresh", tokenRequests.isNotEmpty())
        tokenRequests.forEach {
            assertEquals(
                "Refresh should go to the community token endpoint, not the instance URL",
                "$configuredCommunityUrl/services/oauth2/token",
                urlWithoutQuery(it),
            )
        }
    }

    // endregion
}
