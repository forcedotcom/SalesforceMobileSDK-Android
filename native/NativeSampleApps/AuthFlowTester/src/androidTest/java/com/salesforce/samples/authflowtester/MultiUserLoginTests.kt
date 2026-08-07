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
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.app.Features
import com.salesforce.androidsdk.app.Features.FEATURE_AUTH_TYPE_USER_AGENT_HYBRID
import com.salesforce.androidsdk.app.Features.FEATURE_AUTH_TYPE_USER_AGENT_NON_HYBRID
import com.salesforce.androidsdk.app.Features.FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID
import com.salesforce.androidsdk.app.Features.FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.auth.OAuth2
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.samples.authflowtester.testUtility.AuthFlowTest
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.BEACON_JWT
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.BEACON_OPAQUE
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.CA_OPAQUE
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.ECA_JWT
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.ECA_OPAQUE
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig.ADVANCED_AUTH
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig.REGULAR_AUTH
import com.salesforce.samples.authflowtester.testUtility.KnownUserConfig
import com.salesforce.samples.authflowtester.testUtility.ScopeSelection
import com.salesforce.samples.authflowtester.testUtility.ScopeSelection.ALL
import com.salesforce.samples.authflowtester.testUtility.ScopeSelection.EMPTY
import com.salesforce.samples.authflowtester.testUtility.ScopeSelection.SUBSET
import com.salesforce.samples.authflowtester.testUtility.testConfig
import okhttp3.FormBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI

/**
 * Tests for multi-user login scenarios.
 *
 * Tests login with two users using various configurations:
 *  - Static vs dynamic app configuration
 *  - Same or different app types (opaque vs JWT)
 *  - Same or different scopes
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class MultiUserLoginTests: AuthFlowTest() {

    // Both users use the same default app type and default scopes, with additional token validation.
    @Test
    fun testSameApp_SameScopes_uniqueTokens() {
        // Initial user
        loginAndValidate(knownAppConfig = CA_OPAQUE)
        val (userAccessToken, userRefreshToken) = app.getTokens()

        // Other user
        loginOtherUserAndValidate(knownAppConfig = CA_OPAQUE)
        val (otherUserAccessToken, otherUserRefreshToken) = app.getTokens()

        // Ensure unique tokens
        assertNotEquals(userAccessToken, otherUserAccessToken)
        assertNotEquals(userRefreshToken, otherUserRefreshToken)

        // Switch back to initial user
        switchToUserAndValidate(user)
        app.validateOAuthValues(knownAppConfig = CA_OPAQUE, scopeSelection = EMPTY)
        val (userSwitchAccessToken, userSwitchRefreshToken) = app.getTokens()

        // Ensure Correct Tokens Displayed
        assertEquals(userAccessToken, userSwitchAccessToken)
        assertEquals(userRefreshToken, userSwitchRefreshToken)

        // Ensure correct tokens refreshed
        app.revokeAccessToken()
        app.validateApiRequest()
        val (userRevokeAccessToken, userRevokeRefreshToken) = app.getTokens()
        assertNotEquals(userAccessToken, userRevokeAccessToken)
        assertEquals(userRefreshToken, userRevokeRefreshToken)

        // Switch back to other user
        switchToUserAndValidate(otherUser)
        app.validateOAuthValues(knownAppConfig = CA_OPAQUE, scopeSelection = EMPTY)
        val (otherUserSwitchAccessToken, otherUserSwitchRefreshToken) = app.getTokens()
        assert(otherUserAccessToken == otherUserSwitchAccessToken)
        assert(otherUserRefreshToken == otherUserSwitchRefreshToken)

        // Ensure correct tokens refreshed
        app.revokeAccessToken()
        app.validateApiRequest()
        val (otherUserRevokeAccessToken, otherUserRevokeRefreshToken) = app.getTokens()
        assert(otherUserAccessToken != otherUserRevokeAccessToken)
        assert(otherUserRefreshToken == otherUserRevokeRefreshToken)
    }

    /**
     * Retains User A's real SDK client, makes User B current, then forces A through the complete
     * 401 -> token refresh -> request retry path without switching the application back to A.
     */
    @Test
    fun testRetainedUserClient_refreshesWhileOtherUserCurrent() {
        loginAndValidate(knownAppConfig = CA_OPAQUE)

        val sdkManager = SalesforceSDKManager.getInstance()
        val userAccountManager = sdkManager.userAccountManager
        val userA = requireNotNull(userAccountManager.currentUser)
        val managerA = ClientManager(
            InstrumentationRegistry.getInstrumentation().targetContext,
            userA,
        )
        val clientA = requireNotNull(managerA.peekRestClient())
        assertTrue(
            "Retained client should belong to User A",
            userA.userId == clientA.clientInfo.userId && userA.orgId == clientA.clientInfo.orgId,
        )
        val originalAAccessToken = requireNotNull(userA.authToken)

        loginOtherUserAndValidate(knownAppConfig = CA_OPAQUE)
        val userB = requireNotNull(userAccountManager.currentUser)
        val accountB = requireNotNull(userAccountManager.buildAccount(userB))
        val originalBAccessToken = requireNotNull(userB.authToken)
        val originalBRefreshToken = requireNotNull(userB.refreshTokenForPersistence)

        revokeAccessToken(clientA, originalAAccessToken)
        val response = clientA.sendSync(RestRequest.getRequestForUserInfo())
        try {
            assertTrue("Retained User A request should succeed after refresh", response.isSuccess)
            val responseUser = response.asJSONObject()
            assertTrue(
                "The retained client request should authenticate as User A",
                userA.userId == responseUser.optString("user_id") &&
                        userA.orgId == responseUser.optString("organization_id"),
            )
        } finally {
            response.consumeQuietly()
        }

        val refreshedA = requireNotNull(
            userAccountManager.buildUserAccount(requireNotNull(managerA.account))
        )
        val unchangedB = requireNotNull(userAccountManager.buildUserAccount(accountB))
        assertTrue(
            "User A's access token should change after refresh",
            originalAAccessToken != refreshedA.authToken,
        )
        assertTrue(
            "User B's access token should not change during User A's refresh",
            originalBAccessToken == unchangedB.authToken,
        )
        assertTrue(
            "User B's refresh token should not change during User A's refresh",
            originalBRefreshToken == unchangedB.refreshTokenForPersistence,
        )
        assertTrue("User B should remain current", userB == userAccountManager.currentUser)

        app.validateUser(
            knownLoginHostConfig = REGULAR_AUTH,
            knownUserConfig = otherUser,
            isMultiUser = true,
            expectAdvancedAuth = true,
            expectedBMarker = Features.FEATURE_BROWSER_LOGIN_FORCE_FLAG,
            expectedAMarker = FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID,
        )
        app.validateApiRequest()
    }

    // Both users use the same ECA JWT app type and different scopes.
    @Test
    fun testSameApp_ECA_DifferentScopes() {
        // Initial user
        loginAndValidate(
            knownAppConfig = ECA_JWT,
            scopeSelection = SUBSET,
        )

        // Other user
        loginOtherUserAndValidate(
            knownAppConfig = ECA_JWT,
            scopeSelection = ALL,
        )

        // Switch back to initial user
        switchToUserAndValidate(user)
        app.validateOAuthValues(knownAppConfig = KnownAppConfig.ECA_JWT, scopeSelection = SUBSET)

        // Switch back to other user
        switchToUserAndValidate(otherUser)
        app.validateOAuthValues(knownAppConfig = ECA_JWT, scopeSelection = ALL)
    }

    // Both users use the same Beacon Opaque app type and different scopes.
    @Test
    fun testSameApp_Beacon_DifferentScopes() {
        // Initial user
        loginAndValidate(
            knownAppConfig = BEACON_OPAQUE,
            scopeSelection = EMPTY,
        )

        // Other user
        loginOtherUserAndValidate(
            knownAppConfig = BEACON_OPAQUE,
            scopeSelection = SUBSET,
        )

        // Switch back to initial user
        switchToUserAndValidate(user)
        app.validateOAuthValues(knownAppConfig = BEACON_OPAQUE, scopeSelection = EMPTY)

        // Switch back to other user
        switchToUserAndValidate(otherUser)
        app.validateOAuthValues(knownAppConfig = BEACON_OPAQUE, scopeSelection = SUBSET)
    }

    // First user boot config, second user dynamic config, different apps, same scopes (default).
    @Test
    fun testFirstStatic_SecondDynamic_DifferentApps() {
        // Initial user
        loginAndValidate(knownAppConfig = CA_OPAQUE)

        // Other user
        loginOtherUserAndValidate(knownAppConfig = BEACON_JWT)

        // Switch back to initial user
        switchToUserAndValidate(user)
        app.validateOAuthValues(knownAppConfig = CA_OPAQUE, scopeSelection = EMPTY)

        // Switch back to other user
        switchToUserAndValidate(otherUser)
        app.validateOAuthValues(knownAppConfig = BEACON_JWT, scopeSelection = EMPTY)
    }

    // First user dynamic config, second user boot config, different apps, same scopes (default).
    @Test
    fun testFirstDynamic_SecondStatic_DifferentApps() {
        // Initial user
        loginAndValidate(knownAppConfig = ECA_JWT)

        // Other user
        loginOtherUserAndValidate(knownAppConfig = CA_OPAQUE)

        // Switch back to initial user
        switchToUserAndValidate(user)
        app.validateOAuthValues(knownAppConfig = ECA_JWT, scopeSelection = EMPTY)

        // Switch back to other user
        switchToUserAndValidate(otherUser)
        app.validateOAuthValues(knownAppConfig = CA_OPAQUE, scopeSelection = EMPTY)
    }

    // Both users use different app types and differetn scopes.
    @Test
    fun testDifferentApps_differentScopes() {
        // Initial user
        loginAndValidate(knownAppConfig = BEACON_OPAQUE, scopeSelection = SUBSET)

        // Other user
        loginOtherUserAndValidate(knownAppConfig = ECA_JWT)

        // Switch back to initial user
        switchToUserAndValidate(user)
        app.validateOAuthValues(knownAppConfig = BEACON_OPAQUE, scopeSelection = SUBSET)

        // Switch back to other user
        switchToUserAndValidate(otherUser)
        app.validateOAuthValues(knownAppConfig = ECA_JWT, scopeSelection = EMPTY)
    }

    // Test MultiUser Token Migration.  This test also demonstrates the app restart validation
    // since tokens are read from disk, not memory, on user switch.
    @Test
    fun testMultiUser_tokenMigration() {
        // Initial user
        loginAndValidate(knownAppConfig = BEACON_JWT, scopeSelection = SUBSET)
        val (userAccessToken, userRefreshToken) = app.getTokens()

        // Other user
        loginOtherUserAndValidate(knownAppConfig = CA_OPAQUE)

        // Migrate current user (both users logged in → isMultiUser = true)
        migrateAndValidate(
            knownAppConfig = BEACON_OPAQUE,
            knownUserConfig = otherUser,
            isMultiUser = true,
        )

        // Switch back to initial user and assert unaltered.
        switchToUserAndValidate(user)
        app.validateOAuthValues(knownAppConfig = BEACON_JWT, scopeSelection = SUBSET)
        val (userSwitchAccessToken, userSwitchRefreshToken) = app.getTokens()
        assertEquals(userAccessToken, userSwitchAccessToken)
        assertEquals(userRefreshToken, userSwitchRefreshToken)
    }

    /**
     * Revokes the secondary user's refresh token server-side and verifies
     * that the SDK logs that user out on the next refresh attempt while
     * leaving the primary user untouched.
     */
    @Test
    fun testMultiUser_revokeOtherUserRefreshToken() {
        // Initial user (User A) logs in with the static boot config (CA Opaque).
        loginAndValidate(knownAppConfig = CA_OPAQUE)
        val (userAccessToken, userRefreshToken) = app.getTokens()

        // Other user (User B) logs in with a dynamic config (ECA Opaque).
        loginOtherUserAndValidate(knownAppConfig = ECA_OPAQUE)

        // Snapshot User B's account before revocation.
        val userAccountManager = SalesforceSDKManager.getInstance().userAccountManager
        val otherUserAccount = userAccountManager.authenticatedUsers
            ?.find { it.username == testConfig.getUser(REGULAR_AUTH, otherUser).username }
            ?: throw AssertionError("Other user account not found")
        val otherUserRefreshToken = otherUserAccount.refreshToken
        val otherUserLoginServer = otherUserAccount.loginServer

        // Invalidate User B's access token first, while it is still valid.
        // The in-app revoke button uses the access token to authenticate
        // the POST, so it must run before any server-side revocation that
        // could invalidate the access token as a side effect.
        app.revokeAccessToken()

        // Server-side revoke User B's refresh token. The next refresh
        // attempt will fail and the SDK will log User B out.
        OAuth2.revokeRefreshToken(
            HttpAccess.DEFAULT,
            URI(otherUserLoginServer),
            otherUserRefreshToken,
            OAuth2.LogoutReason.UNKNOWN,
        )

        // Trigger an API request. The SDK detects the missing access token,
        // attempts a refresh, the refresh fails (refresh token revoked
        // above), getNewAuthToken returns null, and SalesforceSDKManager
        // logs User B out.
        app.triggerApiRequestIgnoringResult()

        // Poll until User B is gone, rather than sleeping a fixed duration.
        waitForUserCount(userAccountManager, expectedCount = 1)

        val remainingUsers = userAccountManager.authenticatedUsers ?: emptyList()
        assertEquals(
            "Expected exactly one user (User A) to remain after revoking User B's refresh token",
            1, remainingUsers.size,
        )
        val userAUsername = testConfig.getUser(REGULAR_AUTH, user).username
        assertEquals(userAUsername, remainingUsers.first().username)

        // With User A, validate the original tokens are intact and a refresh still succeeds.
        app.validateUser(
            REGULAR_AUTH,
            user,
            expectAdvancedAuth = true,
            expectedBMarker = Features.FEATURE_BROWSER_LOGIN_FORCE_FLAG,
            expectedAMarker = FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID,
        )
        app.validateOAuthValues(knownAppConfig = CA_OPAQUE, scopeSelection = EMPTY)
        val (userPostAccessToken, userPostRefreshToken) = app.getTokens()
        assertEquals(userAccessToken, userPostAccessToken)
        assertEquals(userRefreshToken, userPostRefreshToken)

        app.revokeAccessToken()
        app.validateApiRequest()
    }

    @Test
    fun testMultiUser_tokenMigration_backgroundUser() {
        // Initial user
        loginAndValidate(knownAppConfig = CA_OPAQUE, scopeSelection = SUBSET)
        val (userAccessToken, userRefreshToken) = app.getTokens()

        // Other user
        loginOtherUserAndValidate(knownAppConfig = ECA_OPAQUE)
        val (otherUserAccessToken, otherUserRefreshToken) = app.getTokens()

        // Migrate initial "user" while "otherUser" is current
        app.migrateToNewApp(
            knownAppConfig = ECA_JWT,
            scopeSelection = EMPTY,
            knownUserConfig = user,
        )

        // Validate nothing changed for "otherUser" before user switch
        val (otherUserPostAccessToken, otherUserPostRefreshToken) = app.getTokens()
        app.validateUser(
            knownLoginHostConfig = REGULAR_AUTH,
            knownUserConfig = otherUser,
            isMultiUser = true,
            expectAdvancedAuth = true,
            expectedBMarker = Features.FEATURE_BROWSER_LOGIN_FORCE_FLAG,
            expectedAMarker = FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID,
        )
        app.validateOAuthValues(knownAppConfig = ECA_OPAQUE, scopeSelection = EMPTY)
        assertEquals(otherUserAccessToken, otherUserPostAccessToken)
        assertEquals(otherUserRefreshToken, otherUserPostRefreshToken)

        // Switch back to initial user
        switchToUserAndValidate(user)
        val (userPostAccessToken, userPostRefreshToken) = app.getTokens()
        app.validateOAuthValues(knownAppConfig = ECA_JWT, scopeSelection = EMPTY)
        assertNotEquals(userAccessToken, userPostAccessToken)
        assertNotEquals(userRefreshToken, userPostRefreshToken)

        // Switch back to other user
        switchToUserAndValidate(otherUser)
        app.validateOAuthValues(knownAppConfig = ECA_OPAQUE, scopeSelection = EMPTY)

        // Assert refresh on correct app
        app.revokeAccessToken()
        app.validateApiRequest()
        val (otherUserAccessTokenAfterRefresh, otherUserRefreshTokenAfterRefresh) = app.getTokens()
        assertNotEquals(otherUserAccessToken, otherUserAccessTokenAfterRefresh)
        assertEquals(otherUserRefreshToken, otherUserRefreshTokenAfterRefresh)
    }

    private fun loginOtherUserAndValidate(
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
            knownAppConfig,
            scopeSelection,
            useWebServerFlow,
            useHybridAuthToken,
            useDPoP,
            knownLoginHostConfig,
            otherUser,
            forceAdvancedAuthentication = forceAdvancedAuthentication,
            isMultiUser = true,
        )
    }

    private fun switchToUserAndValidate(
        knownUserConfig: KnownUserConfig,
        knownLoginHostConfig: KnownLoginHostConfig = REGULAR_AUTH,
        expectAdvancedAuth: Boolean = true,
        expectedBMarker: String? = if (expectAdvancedAuth) {
            Features.FEATURE_BROWSER_LOGIN_FORCE_FLAG
        } else {
            null
        },
        expectedAMarker: String? = FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID,
        isJwt: Boolean = false,
        isBeacon: Boolean = false,
        wasMigrated: Boolean = false,
    ) {
        app.switchToUser(knownUserConfig, knownLoginHostConfig)
        composeTestRule.waitForIdle()
        app.validateUser(
            knownLoginHostConfig,
            knownUserConfig,
            isMultiUser = true,
            expectAdvancedAuth = expectAdvancedAuth,
            expectedBMarker = expectedBMarker,
            expectedAMarker = expectedAMarker,
            isJwt = isJwt,
            isBeacon = isBeacon,
            wasMigrated = wasMigrated,
        )
    }

    /** Revokes [accessToken] through [client] without consulting the application current user. */
    private fun revokeAccessToken(client: RestClient, accessToken: String) {
        val body = FormBody.Builder()
            .add("token", accessToken)
            .build()
        val request = RestRequest(
            RestRequest.RestMethod.POST,
            RestRequest.RestEndpoint.INSTANCE,
            "/services/oauth2/revoke",
            body,
            emptyMap(),
        )

        val response = client.sendSync(request)
        try {
            assertTrue("Access-token revocation should succeed", response.isSuccess)
        } finally {
            response.consumeQuietly()
        }
    }

    /**
     * Polls the user account manager until the authenticated user count
     * reaches [expectedCount]. Used after triggering an automatic logout to
     * avoid a fixed-duration sleep.
     */
    private fun waitForUserCount(
        userAccountManager: com.salesforce.androidsdk.accounts.UserAccountManager,
        expectedCount: Int,
        timeoutMs: Long = USER_COUNT_TIMEOUT_MS,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val count = userAccountManager.authenticatedUsers?.size ?: 0
            if (count == expectedCount) return
            Thread.sleep(POLL_INTERVAL_MS)
        }
        val finalCount = userAccountManager.authenticatedUsers?.size ?: 0
        throw AssertionError(
            "Timed out after ${timeoutMs}ms waiting for user count to reach " +
                "$expectedCount (was $finalCount)"
        )
    }

    /**
     * A1 (web-server non-hybrid) vs A2 (web-server hybrid) across two live users.
     * Detects A-marker leakage: if User B's A2 bleeds into User A's session on switch, the
     * expectedAMarker = A1 assertion fires.
     */
    @Test
    fun testMultiUser_A1OT_vs_A2JT() {
        // User A: web-server non-hybrid → A1, OT
        loginAndValidate(
            knownAppConfig = ECA_OPAQUE,
            useHybridAuthToken = false,
        )

        // User B: web-server hybrid, JWT app → A2, JT
        loginOtherUserAndValidate(knownAppConfig = ECA_JWT)

        // Switch back to User A — must still have A1, OT (not A2 or JT)
        switchToUserAndValidate(
            user,
            expectedAMarker = FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID,
            isJwt = false,
        )
        app.validateOAuthValues(knownAppConfig = ECA_OPAQUE, scopeSelection = EMPTY)

        // Switch back to User B — must still have A2, JT
        switchToUserAndValidate(
            otherUser,
            expectedAMarker = FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID,
            isJwt = true,
        )
        app.validateOAuthValues(knownAppConfig = ECA_JWT, scopeSelection = EMPTY)
    }

    /**
     * Maximum orthogonality: A-marker (A2 vs A1), token format (JT vs OT), and beacon (BN vs
     * no BN) all differ simultaneously. A single per-user flag leakage manifests on at least
     * two of the three axes.
     */
    @Test
    fun testMultiUser_A2JT_BN_vs_A1OT() {
        // User A: web-server hybrid, beacon JWT → A2, JT, BN
        loginAndValidate(knownAppConfig = BEACON_JWT)

        // User B: web-server non-hybrid, ECA opaque → A1, OT, no BN
        loginOtherUserAndValidate(
            knownAppConfig = ECA_OPAQUE,
            useHybridAuthToken = false,
        )

        // Switch back to User A — must still have A2, JT, BN
        switchToUserAndValidate(
            user,
            expectedAMarker = FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID,
            isJwt = true,
            isBeacon = true,
        )
        app.validateOAuthValues(knownAppConfig = BEACON_JWT, scopeSelection = EMPTY)

        // Switch back to User B — must still have A1, OT, no BN
        switchToUserAndValidate(
            otherUser,
            expectedAMarker = FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID,
            isJwt = false,
            isBeacon = false,
        )
        app.validateOAuthValues(knownAppConfig = ECA_OPAQUE, scopeSelection = EMPTY)
    }

    /**
     * Web-server hybrid beacon (A2, OT, BN) vs web-server non-hybrid (A1, OT, no BN).
     * Tests A-marker and BN leakage in both switch directions.
     *
     * Note: BN (beacon child consumer key) is only returned in the token-endpoint
     * code-exchange response (web server flow), not in the user-agent flow's URL-fragment
     * redirect. Both users therefore use web server flow; the beacon vs non-beacon dimension
     * is what generates the detectable difference on this axis.
     */
    @Test
    fun testMultiUser_A4OT_BN_vs_A2JT() {
        // User A: web-server hybrid, beacon opaque → A2, OT, BN
        loginAndValidate(
            knownAppConfig = BEACON_OPAQUE,
        )

        // User B: web-server non-hybrid, ECA opaque → A1, OT, no BN
        loginOtherUserAndValidate(
            knownAppConfig = ECA_OPAQUE,
            useHybridAuthToken = false,
        )

        // Switch back to User A — must still have A2, OT, BN
        switchToUserAndValidate(
            user,
            expectedAMarker = FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID,
            isJwt = false,
            isBeacon = true,
        )
        app.validateOAuthValues(knownAppConfig = BEACON_OPAQUE, scopeSelection = EMPTY)

        // Switch back to User B — must still have A1, OT, no BN
        switchToUserAndValidate(
            otherUser,
            expectedAMarker = FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID,
            isJwt = false,
            isBeacon = false,
        )
        app.validateOAuthValues(knownAppConfig = ECA_OPAQUE, scopeSelection = EMPTY)
    }

    /**
     * Verifies TM does not bleed from migrated User A to non-migrated User B, and that JT/OT
     * and BN flags remain isolated after migration.
     *
     * User A: migrated from ECA_OPAQUE → ECA_JWT (gains TM + JT).
     * User B: beacon opaque (OT, BN, no TM).
     */
    @Test
    fun testMultiUser_TM_isolation() {
        // User A: ECA opaque login → A2, OT
        loginAndValidate(knownAppConfig = ECA_OPAQUE)

        // User B: beacon opaque login → A2, OT, BN
        loginOtherUserAndValidate(knownAppConfig = BEACON_OPAQUE)

        // Migrate User B to ECA JWT — User B gains TM + JT (stays current user)
        migrateAndValidate(
            knownAppConfig = ECA_JWT,
            knownUserConfig = otherUser,
            isMultiUser = true,
        )

        // Switch to User A — must have OT, no TM, no BN (migration of User B must not bleed)
        switchToUserAndValidate(
            user,
            expectedAMarker = FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID,
            isJwt = false,
            isBeacon = false,
            wasMigrated = false,
        )
        app.validateOAuthValues(knownAppConfig = ECA_OPAQUE, scopeSelection = EMPTY)

        // Switch back to User B — must have JT + TM (migration survived round-trip)
        switchToUserAndValidate(
            otherUser,
            expectedAMarker = FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID,
            isJwt = true,
            isBeacon = false,
            wasMigrated = true,
        )
        app.validateOAuthValues(knownAppConfig = ECA_JWT, scopeSelection = EMPTY)
    }

    /**
     * Verifies BW (browser-login) flag isolation between two users on the same REGULAR_AUTH
     * server: User A uses user-agent flow (no BW), User B uses forced advanced auth (BW).
     * Both users are on REGULAR_AUTH so they have distinct Salesforce user identities and
     * their per-user feature sets are separate key entries in the SDK's perUserFeatures map.
     * Mixing REGULAR_AUTH and ADVANCED_AUTH users risks them sharing the same orgId/userId
     * if the advanced-auth domain is a custom My Domain for the same org, which would make
     * per-user feature isolation untestable.
     */
    @Test
    fun testAdvancedAuthUser_HasBWFlag_RegularAuthUser_DoesNot() {
        // User A: user-agent flow (no BW, no forced advanced auth)
        loginAndValidate(
            knownAppConfig = ECA_OPAQUE,
            knownLoginHostConfig = REGULAR_AUTH,
            knownUserConfig = user,
            useWebServerFlow = false,
            forceAdvancedAuthentication = false,
            isMultiUser = false,
        )

        // User B: forced advanced auth via REGULAR_AUTH server — has BW (B4); now 2 users → MU
        loginOtherUserAndValidate(
            knownAppConfig = BEACON_OPAQUE,
            knownLoginHostConfig = REGULAR_AUTH,
            forceAdvancedAuthentication = true,
        )

        // Switch to User A — no BW, MU still present
        switchToUserAndValidate(
            user,
            expectAdvancedAuth = false,
            expectedBMarker = null,
            expectedAMarker = FEATURE_AUTH_TYPE_USER_AGENT_HYBRID,
        )

        // Switch back to User B — BW (B4) back, MU still present
        switchToUserAndValidate(
            otherUser,
            REGULAR_AUTH,
            expectedBMarker = Features.FEATURE_BROWSER_LOGIN_FORCE_FLAG,
            expectedAMarker = FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID,
            isBeacon = true,
        )

        // Log out User B via SDK — auto-switches to User A; MU must be gone
        val sdkManager = SalesforceSDKManager.getInstance()
        val otherUserAccount = sdkManager.userAccountManager.authenticatedUsers
            ?.find { it.username == testConfig.getUser(REGULAR_AUTH, otherUser).username }
            ?: throw AssertionError("Other user account not found")
        sdkManager.logout(
            account = sdkManager.userAccountManager.buildAccount(otherUserAccount),
            frontActivity = null,
            showLoginPage = false,
        )
        waitForUserCount(sdkManager.userAccountManager, expectedCount = 1)
        app.waitForAppLoad()

        // Back on User A — MU gone, no BW (useWebServerFlow=false, useHybridAuthToken=true → A4)
        app.validateUserAgent(
            REGULAR_AUTH,
            isMultiUser = false,
            expectAdvancedAuth = false,
            expectedLMarker = Features.FEATURE_LOGIN_SERVER_MY_DOMAIN,
            expectedAMarker = FEATURE_AUTH_TYPE_USER_AGENT_HYBRID,
        )
    }

    companion object {
        private const val USER_COUNT_TIMEOUT_MS = 15_000L
        private const val POLL_INTERVAL_MS = 250L
    }
}
