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
import com.salesforce.androidsdk.app.Features.FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID
import com.salesforce.androidsdk.app.SalesforceSDKManager
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
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
        assertRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = false,
            knownLoginHostConfig = COMMUNITY_AUTH,
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
        assertRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = false,
            knownLoginHostConfig = COMMUNITY_AUTH,
            expectedAMarker = FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID,
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
        assertRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = false,
            knownLoginHostConfig = COMMUNITY_AUTH,
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
        assertRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = false,
            isDpop = true,
            knownLoginHostConfig = COMMUNITY_AUTH,
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
        assertRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = false,
            isDpop = true,
            knownLoginHostConfig = COMMUNITY_AUTH,
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
        assertRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = false,
            isDpop = true,
            knownLoginHostConfig = COMMUNITY_AUTH,
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

        assertRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = true,
            isDpop = true,
            knownLoginHostConfig = COMMUNITY_AUTH,
            isJwt = true,
        )
        assertEquals(keyThumbprintAfterLogin, app.getDpopInfo().keyThumbprint)

        assertRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = true,
            isDpop = true,
            knownLoginHostConfig = COMMUNITY_AUTH,
            isJwt = true,
        )
        assertEquals(keyThumbprintAfterLogin, app.getDpopInfo().keyThumbprint)
    }

    // endregion

    // region Restart Tests

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

        assertRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = false,
            isDpop = true,
            knownLoginHostConfig = COMMUNITY_AUTH,
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

    // Full logout (not just revoke) followed by a fresh DPoP login: the account is fully removed,
    // and the subsequent login establishes new tokens and a new DPoP key pair rather than reusing
    // anything left over from the previous session.
    @Test
    fun testCommunity_LogoutAndRelogin_DPoP() {
        loginAndValidate(
            knownAppConfig = ECA_JWT_DPOP,
            knownLoginHostConfig = COMMUNITY_AUTH,
            knownUserConfig = KnownUserConfig.FIRST,
            useDPoP = true,
        )
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
            knownAppConfig = ECA_JWT_DPOP,
            knownLoginHostConfig = COMMUNITY_AUTH,
            knownUserConfig = KnownUserConfig.FIRST,
            useDPoP = true,
        )
        val (accessTokenAfterRelogin, refreshTokenAfterRelogin) = app.getTokens()
        assertNotEquals(accessTokenBeforeLogout, accessTokenAfterRelogin)
        assertNotEquals(refreshTokenBeforeLogout, refreshTokenAfterRelogin)
        assertNotEquals(
            "A fresh login after full logout should generate a new DPoP key pair, not reuse the old one",
            keyThumbprintBeforeLogout,
            app.getDpopInfo().keyThumbprint,
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
        app.validateOAuthValues(knownAppConfig = ECA_JWT_DPOP, scopeSelection = EMPTY)
        assertRevokeAndRefreshWorks(
            expectsRefreshTokenRotation = false,
            isDpop = true,
            isMultiUser = true,
            knownLoginHostConfig = COMMUNITY_AUTH,
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
}
