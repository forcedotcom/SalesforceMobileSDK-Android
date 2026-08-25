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
import com.salesforce.samples.authflowtester.testUtility.ScopeSelection
import com.salesforce.samples.authflowtester.testUtility.testConfig
import org.junit.Assert.assertNotEquals
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for all DPoP-enabled login flows: basic login, RTR, multi-user, migration, and restart.
 *
 * DPoP is toggled on via `LoginOptions` before each login; the base-class `@Before` pins the flag
 * off (the Bearer baseline) and `cleanup()` restores it to off after each test. All DPoP tests use
 * the `regular_auth` login host (sdb38) — DPoP is an ECA property, not an org property.
 *
 * NB: Tests use the first user from ui_test_config.json
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class DPoPLoginTests : AuthFlowTest() {

    // region ECA JWT DPoP Tests

    // Login with ECA JWT DPoP using hybrid auth token flow.
    @Test
    fun testECAJwtDPoP_Hybrid() {
        loginAndValidate(knownAppConfig = ECA_JWT_DPOP, useDPoP = true)
        assertRevokeAndRefreshWorks(isRtr = false, isDpop = true, isJwt = true)
        assertRevokeAndRefreshWorks(isRtr = false, isDpop = true, isJwt = true)
    }

    // Login with ECA JWT DPoP without hybrid auth token.
    @Test
    fun testECAJwtDPoP_NoHybrid() {
        loginAndValidate(knownAppConfig = ECA_JWT_DPOP, useHybridAuthToken = false, useDPoP = true)
        assertRevokeAndRefreshWorks(isRtr = false, isDpop = true, expectedAMarker = FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID, isJwt = true)
        assertRevokeAndRefreshWorks(isRtr = false, isDpop = true, expectedAMarker = FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID, isJwt = true)
    }

    // endregion

    // region ECA JWT DPoP RTR Tests

    // TODO: W-22512846 — Re-enable when server enables Named JWTs for Hybrid Flows.
    // Server currently returns invalid_grant for RTR + JWT tokens in hybrid flow.
    @Ignore("TODO: W-22512846 — Re-enable when server enables Named JWTs for Hybrid Flows")
    @Test
    fun testECAJwtDPoPRtr_Hybrid() {
        loginAndValidate(knownAppConfig = ECA_JWT_DPOP_RTR, useDPoP = true)
        assertRevokeAndRefreshWorks(isRtr = true, isDpop = true, isJwt = true)
        assertRevokeAndRefreshWorks(isRtr = true, isDpop = true, isJwt = true)
    }

    // Login with ECA JWT DPoP RTR without hybrid auth token.
    @Test
    fun testECAJwtDPoPRtr_NoHybrid() {
        loginAndValidate(knownAppConfig = ECA_JWT_DPOP_RTR, useHybridAuthToken = false, useDPoP = true)
        assertRevokeAndRefreshWorks(isRtr = true, isDpop = true, expectedAMarker = FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID, isJwt = true)
        assertRevokeAndRefreshWorks(isRtr = true, isDpop = true, expectedAMarker = FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID, isJwt = true)
    }

    // endregion

    // region DPoP Multi-User Tests

    // Both users log in with a DPoP ECA; tokens are unique and revoke+refresh works per-user.
    @Test
    fun testECAJwtDPoP_MultiUser_UniqueTokens() {
        // Initial user with DPoP
        loginAndValidate(knownAppConfig = ECA_JWT_DPOP, useDPoP = true)
        val (userAccessToken, userRefreshToken) = app.getTokens()

        // Other user with DPoP
        addOtherUserAndValidate(knownAppConfig = ECA_JWT_DPOP, useDPoP = true)
        val (otherUserAccessToken, otherUserRefreshToken) = app.getTokens()

        // Tokens must be unique across users
        assertNotEquals(userAccessToken, otherUserAccessToken)
        assertNotEquals(userRefreshToken, otherUserRefreshToken)

        // Switch back to initial user; revoke + refresh must work with DPoP nonce rotation
        switchToUserAndValidateUser(user, isDpop = true, isJwt = true)
        app.validateOAuthValues(knownAppConfig = ECA_JWT_DPOP, scopeSelection = ScopeSelection.EMPTY)
        assertRevokeAndRefreshWorks(isRtr = false, isDpop = true, isMultiUser = true, isJwt = true)

        // Switch to other user; revoke + refresh must work independently with its own nonce
        switchToUserAndValidateUser(otherUser, isDpop = true, isJwt = true)
        app.validateOAuthValues(knownAppConfig = ECA_JWT_DPOP, scopeSelection = ScopeSelection.EMPTY)
        assertRevokeAndRefreshWorks(isRtr = false, isDpop = true, isMultiUser = true, isJwt = true)
    }

    // Mixed DPoP + non-DPoP users with the process-wide DPoP flag flipped off after both are
    // logged in. Each user's post-flip refresh + revoke must use its own auth scheme
    // independently — DPoP for the DPoP-bound credential, Bearer for the non-DPoP one — gated by
    // per-credential state (tokenType or persisted key material), not the global flag.
    @Test
    fun testECAJwtDPoP_And_NonDPoP_MultiUser_FlagOff_IndependentProofs() {
        // User A: DPoP ECA. useDPoP=true at login-time so /authorize gets dpop_jkt and the
        // credential is persisted with tokenType="DPoP" plus a key pair in AndroidKeyStore.
        loginAndValidate(knownAppConfig = ECA_JWT_DPOP, useDPoP = true)

        // User B: non-DPoP ECA (Bearer). useDPoP=false at login-time so the credential is
        // persisted with tokenType absent/"Bearer" and no key material.
        addOtherUserAndValidate(knownAppConfig = ECA_JWT, useDPoP = false)

        // Simulate an app upgrade or config change that flips the process-wide flag OFF.
        // Existing DPoP-bound credentials must survive; new logins from now on would be Bearer.
        SalesforceSDKManager.getInstance().useDPoP = false

        // Switch to user A (DPoP-bound). Refresh + REST GET must attach a DPoP proof —
        // gated by credential state, not the global flag.
        // ECA_JWT_DPOP issues JWT tokens, so isJwt=true is required to expect JT in the UA.
        switchToUserAndValidateUser(user, isDpop = true, isJwt = true)
        app.validateOAuthValues(knownAppConfig = ECA_JWT_DPOP, scopeSelection = ScopeSelection.EMPTY)
        assertRevokeAndRefreshWorks(isRtr = false, isDpop = true, isMultiUser = true, isJwt = true)

        // Switch to user B (Bearer). Refresh + REST GET must NOT attach DPoP anywhere.
        // ECA_JWT issues JWT tokens, so isJwt=true is required to expect JT (not OT) in the UA.
        switchToUserAndValidateUser(otherUser, isDpop = false, isJwt = true)
        app.validateOAuthValues(knownAppConfig = ECA_JWT, scopeSelection = ScopeSelection.EMPTY)
        assertRevokeAndRefreshWorks(isRtr = false, isDpop = false, isMultiUser = true, isJwt = true)
    }

    // endregion

    // region DPoP Migration Tests

    // Login with DPoP ECA, migrate to same ECA with more scopes — DPoP binding preserved.
    @Test
    fun testMigrate_ECAJwtDPoP_AddMoreScopes() {
        loginAndValidate(
            knownAppConfig = ECA_JWT_DPOP,
            scopeSelection = ScopeSelection.SUBSET,
            useDPoP = true,
        )
        migrateAndValidate(
            ECA_JWT_DPOP,
            scopeSelection = ScopeSelection.ALL,
            isDpop = true,
        )
    }

    // Login with DPoP ECA, migrate to DPoP+RTR ECA — refresh token rotation now enabled.
    // Uses useHybridAuthToken = false: the server rejects hybrid grants with RTR + JWT enabled
    // (W-22512846), so the non-hybrid path is used as a workaround.
    @Test
    fun testMigrate_ECAJwtDPoP_To_ECAJwtDPoPRtr() {
        loginAndValidate(
            knownAppConfig = ECA_JWT_DPOP,
            useHybridAuthToken = false,
            useDPoP = true,
        )
        migrateAndValidate(
            ECA_JWT_DPOP_RTR,
            isDpop = true,
            expectedAMarker = FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID,
        )
    }

    // endregion

    // region DPoP Enforcement Tests

    // A DPoP-enforced ECA must reject an unbound login: the enforced ECA requires a dpop_jkt on
    // /authorize to bind the auth code, and with the DPoP Login Option off no dpop_jkt is sent,
    // so no authenticated user is added and the app never loads.
    @Test
    fun testLogin_DPoP_ECA_Without_DPoP_Fails() {
        loginAndExpectFailure(
            consumerKey = testConfig.getApp(ECA_JWT_DPOP).consumerKey,
            redirectUri = testConfig.getApp(ECA_JWT_DPOP).redirectUri,
            useDPoP = false,
            expectDPoPBindingError = true,
        )
    }

    // endregion

    // region DPoP Upgrade Tests

    // In-place upgrade: a Bearer session on an unenforced ECA is bound to DPoP via the
    // "Upgrade to DPoP" affordance, with the process-wide DPoP flag left OFF the entire time.
    // Proves the migration re-auth honors the per-call intent (LoginViewModel.dpopOverride)
    // rather than SalesforceSDKManager.useDPoP, and that no re-consent is needed since the
    // consumer key/redirect URI/scopes are unchanged.
    @Test
    fun testUpgrade_NonDPoP_InPlace_ToDPoP() {
        loginAndValidate(knownAppConfig = ECA_JWT, useDPoP = false)
        upgradeToDPoPAndValidate(knownAppConfig = ECA_JWT)
    }

    // endregion

    // region DPoP Downgrade Tests

    // In-place downgrade: a DPoP-bound session on an unenforced ECA is rolled back to Bearer via
    // the "Downgrade from DPoP" affordance, with the process-wide DPoP flag left ON the entire
    // time. Proves the migration re-auth honors the per-call intent (LoginViewModel.dpopOverride)
    // rather than SalesforceSDKManager.useDPoP, and that no re-consent is needed since the
    // consumer key/redirect URI/scopes are unchanged.
    @Test
    fun testDowngrade_DPoP_InPlace_ToBearer() {
        loginAndValidate(knownAppConfig = ECA_JWT, useDPoP = true)
        downgradeFromDPoPAndValidate(knownAppConfig = ECA_JWT)
    }

    // endregion

    // region DPoP Restart Tests

    /**
     * Login with DPoP ECA, restart app, verify the DPoP key pair and session survive the restart
     * and that revoke+refresh still works (key pair reloaded from AndroidKeyStore, not regenerated).
     *
     * Note: restartApp() kills the process and relaunches it, which re-runs
     * Application.onCreate(). A real app would call setUseDPoP(true) there; AuthFlowTester
     * does not, so we re-enable DPoP explicitly via the hydratePerUserFeatures path that
     * reads the per-user DPoP flag from the persisted UserAccount. This test validates that
     * the key pair stored in AndroidKeyStore survives across process restarts.
     */
    @Test
    fun testECAJwtDPoP_WithRestart() {
        loginAndValidate(
            knownAppConfig = ECA_JWT_DPOP,
            useDPoP = true,
        )
        restartAndValidateUser(
            knownAppConfig = ECA_JWT_DPOP,
            isDpop = true,
        )
        // After restart the key pair must still be valid — revoke+refresh proves it.
        // The nonce-change assertion also confirms the server accepted the DPoP proof
        // built with the key pair loaded from AndroidKeyStore after restart.
        assertRevokeAndRefreshWorks(isRtr = false, isDpop = true, isJwt = true)
    }

    // endregion

    // endregion

    // region DPoP Pool Server Tests

    // Login via the pool server (login.test1.pc-rnd.salesforce.com) with DPoP enabled
    // and verify dpop_jkt was accepted and DPoP binding holds after a revoke+refresh.
    //
    // Skipped: server-side bug W-23864247 — the pool login server returns
    // invalid_dpop_proof on the authorization-code token exchange even though the
    // DPoP proof is cryptographically valid and the JWK thumbprint exactly matches
    // the dpop_jkt sent in /authorize.  Re-enable when the server fix is confirmed.
    @Ignore("W-23864247: pool login server rejects valid dpop_jkt token exchange")
    @Test
    fun testECAJwtDPoP_ViaLoginPoolServer() {
        loginAndValidate(
            knownAppConfig = ECA_JWT_DPOP,
            useHybridAuthToken = false,
            useDPoP = true,
            useLoginPoolHost = true,
        )
        assertRevokeAndRefreshWorks(isRtr = false, isDpop = true, isJwt = true)
    }

    // endregion

    // region DPoP Login for Admins Tests

    // Login for Admins with DPoP ECA; verifies the admin Custom Tab hand-off works with DPoP.
    @Test
    fun testLoginForAdmin_DPoP() {
        adminLoginAndValidate(knownAppConfig = ECA_JWT_DPOP, useDPoP = true)
    }

    // endregion
}
