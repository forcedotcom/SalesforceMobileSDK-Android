/*
 * Copyright (c) 2026-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
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
package com.salesforce.androidsdk.app

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.auth.AuthenticatorService
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.ui.LoginActivity
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** Credential-free behavior coverage for the SDK-level authenticated-client factories. */
@RunWith(AndroidJUnit4::class)
@SmallTest
class SalesforceSDKManagerClientManagerTest {

    private val sdkManager by lazy { SalesforceSDKManager.getInstance() }
    private val userAccountManager by lazy { sdkManager.userAccountManager }
    private val accountManager by lazy { AccountManager.get(sdkManager.appContext) }

    private var originalUser: UserAccount? = null

    @Before
    fun setUp() {
        ensureSdkManagerInitialized()
        originalUser = userAccountManager.currentUser?.takeUnless {
            it.accountName.startsWith(TEST_ACCOUNT_PREFIX)
        }
        removeTestAccounts()
        userAccountManager.clearStoredCurrentUserInfo()
        RestClient.clearCaches()
    }

    @After
    fun tearDown() {
        RestClient.clearCaches()
        removeTestAccounts()
        val userToRestore = originalUser
        if (userToRestore != null && userAccountManager.buildAccount(userToRestore) != null) {
            userAccountManager.storeCurrentUserInfo(userToRestore.userId, userToRestore.orgId)
        } else {
            userAccountManager.clearStoredCurrentUserInfo()
        }
    }

    @Test
    fun clientManager_afterCurrentUserSwitch_retainsAAndFreshGetterUsesB() {
        val userA = persistUser("a")
        val retainedManagerA = requireNotNull(sdkManager.clientManager)

        val userB = persistUser("b")
        val freshManagerB = requireNotNull(sdkManager.clientManager)

        assertManagerBoundTo(retainedManagerA, userA)
        assertManagerBoundTo(freshManagerB, userB)
        assertManagerBoundTo(retainedManagerA, userA)
    }

    @Test
    fun clientManager_repeatedAccessForSameCurrentUser_returnsSameCachedInstance() {
        /*
         * Regression guard for the account-resolution caching fix: repeated
         * access for an unchanged current user must not reconstruct
         * ClientManager (and re-resolve its backing Account) on every call.
         */
        persistUser("cached")
        val first = requireNotNull(sdkManager.clientManager)
        val second = requireNotNull(sdkManager.clientManager)
        val third = requireNotNull(sdkManager.clientManager)

        assertTrue("Repeated access for the same current user must return the cached instance", first === second)
        assertTrue("Repeated access for the same current user must return the cached instance", second === third)
    }

    @Test
    fun clientManager_survivesTwoSequentialRefreshTokenRotationsWithoutReconstructionOrLogout() {
        /*
         * Regression guard: the cached clientManager must keep working
         * correctly across repeated server-side refresh token rotations
         * (RTR), not just a single one. Guards the token-provider
         * rehydration path (peekRestClient() -> getValidatedUser() ->
         * UserAccountManager.buildUserAccount()) against silently breaking
         * under a cached manager in a future change: if the cache ever held
         * a stale reference across a rotation, the second refresh would
         * submit the ORIGINAL refresh token (already invalidated by the
         * server after the first rotation) instead of the newly-rotated
         * one, and/or the manager identity would change between accesses.
         */
        val user = persistUser("rtr-two-rounds")
        val managerBeforeFirstRefresh = requireNotNull(sdkManager.clientManager)
        val client = requireNotNull(managerBeforeFirstRefresh.peekRestClient())
        val account = requireNotNull(managerBeforeFirstRefresh.account)

        val firstRequest = slot<Request>()
        mockRefreshHttpClient(
            firstRequest,
            successfulRefreshResponse(
                suffix = "rtr-two-rounds",
                accessToken = "auth-token-rtr-round-1",
                refreshToken = "refresh-token-rtr-round-1",
            ),
        )
        try {
            client.refreshAccessToken()
        } finally {
            unmockkObject(HttpAccess.DEFAULT)
        }
        assertEquals(
            "First refresh must submit the originally-persisted refresh token",
            "refresh-token-rtr-two-rounds",
            requestFormValue(firstRequest.captured, "refresh_token"),
        )

        val managerAfterFirstRefresh = requireNotNull(sdkManager.clientManager)
        assertTrue(
            "The cached manager must be reused across a refresh, not reconstructed",
            managerBeforeFirstRefresh === managerAfterFirstRefresh,
        )

        val secondRequest = slot<Request>()
        val secondHttpClient = mockRefreshHttpClient(
            secondRequest,
            successfulRefreshResponse(
                suffix = "rtr-two-rounds",
                accessToken = "auth-token-rtr-round-2",
                refreshToken = "refresh-token-rtr-round-2",
            ),
        )
        try {
            managerAfterFirstRefresh.peekRestClient()!!.refreshAccessToken()
        } finally {
            unmockkObject(HttpAccess.DEFAULT)
        }

        assertEquals(
            "The second refresh must submit the FIRST rotation's refresh token, not the " +
                "original one, proving the cached manager rehydrated the latest persisted state",
            "refresh-token-rtr-round-1",
            requestFormValue(secondRequest.captured, "refresh_token"),
        )
        val finalUser = requireNotNull(userAccountManager.buildUserAccount(account))
        assertEquals(
            "The second rotation's refresh token must be persisted",
            "refresh-token-rtr-round-2",
            finalUser.refreshTokenForPersistence,
        )
        assertEquals("auth-token-rtr-round-2", finalUser.authToken)
        assertEquals(
            "No logout must occur across either rotation",
            user.userId,
            userAccountManager.currentUser?.userId,
        )
        assertEquals(user.orgId, userAccountManager.currentUser?.orgId)
        verify(exactly = 1) { secondHttpClient.newCall(any()) }
    }

    @Test
    fun clientManager_repeatedAccess_doesNotForceFreshCurrentUserResolution() {
        /*
         * Regression guard: clientManager's identity lookup must not
         * unconditionally re-resolve the current user via AccountManager on
         * every access. UserAccountManager.getCurrentUser() always
         * allocates a new UserAccount by decrypting AccountManager-backed
         * fields, so its cached reference changes identity every time it
         * runs. Seeding that cache once and then confirming the reference
         * is unchanged after repeated clientManager access proves those
         * accesses used the cached identity lookup instead of re-invoking
         * getCurrentUser().
         */
        persistUser("no-fresh-lookup")
        val seededCurrentUser = requireNotNull(userAccountManager.currentUser)

        requireNotNull(sdkManager.clientManager)
        requireNotNull(sdkManager.clientManager)
        requireNotNull(sdkManager.clientManager)

        assertTrue(
            "Repeated clientManager access must not force a fresh currentUser resolution",
            seededCurrentUser === userAccountManager.cachedCurrentUser,
        )
    }

    @Test
    fun clientManager_afterCurrentUserSwitch_freshGetterReturnsNewInstanceBoundToNewUser() {
        /*
         * Regression guard: switching the current user must invalidate the
         * cache so the next access resolves a client bound to the new user,
         * not a stale cached instance from the old user.
         */
        val userA = persistUser("switch-a")
        val managerForA = requireNotNull(sdkManager.clientManager)

        val userB = persistUser("switch-b")
        val managerForB = requireNotNull(sdkManager.clientManager)

        assertTrue(
            "Switching the current user must produce a differently-bound ClientManager instance",
            managerForA !== managerForB,
        )
        assertManagerBoundTo(managerForA, userA)
        assertManagerBoundTo(managerForB, userB)
    }

    @Test
    fun clientManager_afterCachedAccountIsRemovedAndReAdded_returnsFreshlyBoundInstance() {
        /*
         * Regression guard: the cache must not survive removal of the
         * account it is bound to. If the same identity is re-added
         * afterward, the next access must resolve a fresh ClientManager
         * bound to the new persisted Account, not the stale cached instance
         * from before the removal.
         */
        val user = persistUser("removed-and-readded")
        val staleManager = requireNotNull(sdkManager.clientManager)
        val staleAccount = requireNotNull(staleManager.account)

        sdkManager.logout(staleAccount, null, false)

        userAccountManager.createAccount(user)
        val freshManager = requireNotNull(sdkManager.clientManager)

        assertTrue(
            "A cache entry must not survive removal of the account it is bound to",
            staleManager !== freshManager,
        )
        assertManagerBoundTo(freshManager, user)
    }

    @Test
    fun clientManager_afterLogoutAndReloginAsSameIdentity_startsWithClearedFeatureMarkers() {
        /*
         * Regression guard: logging out and back in as the same identity
         * must not carry the previous session's per-user feature markers
         * forward. cleanUp() must drop this identity's perUserFeatures
         * entry so a fresh login starts from an empty set and can select a
         * different login type without the old session's markers lingering.
         */
        val user = persistUser("relogin-same-identity")
        sdkManager.registerUsedAppFeature(Features.FEATURE_AUTH_TYPE_NATIVE, user)
        assertTrue(
            "Marker must be registered before logout",
            sdkManager.isUserFeatureRegistered(Features.FEATURE_AUTH_TYPE_NATIVE, user),
        )

        sdkManager.logout(requireNotNull(userAccountManager.buildAccount(user)), null, false)

        userAccountManager.createAccount(user)
        assertFalse(
            "A fresh login as the same identity must not inherit the previous " +
                "session's feature markers",
            sdkManager.isUserFeatureRegistered(Features.FEATURE_AUTH_TYPE_NATIVE, user),
        )

        sdkManager.registerUsedAppFeature(Features.FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID, user)
        assertTrue(
            "The new session must be able to select a different login type",
            sdkManager.isUserFeatureRegistered(Features.FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID, user),
        )
        assertFalse(
            "The old session's login-type marker must not carry forward",
            sdkManager.isUserFeatureRegistered(Features.FEATURE_AUTH_TYPE_NATIVE, user),
        )
    }

    @Test
    fun retainedClient_refreshesPersistedAWhileBRemainsCurrent() {
        val userA = persistUser("refresh-a")
        val managerA = requireNotNull(sdkManager.clientManager)
        val clientA = requireNotNull(managerA.peekRestClient())
        val accountA = requireNotNull(managerA.account)

        val userB = persistUser("refresh-b")
        val accountB = requireNotNull(userAccountManager.buildAccount(userB))
        val submittedRequest = slot<Request>()
        val httpClient = mockRefreshHttpClient(
            submittedRequest,
            successfulRefreshResponse(
                suffix = "refresh-a",
                accessToken = "refreshed-auth-token-a",
                refreshToken = "rotated-refresh-token-a",
            ),
        )

        try {
            clientA.refreshAccessToken()
        } finally {
            unmockkObject(HttpAccess.DEFAULT)
        }

        val refreshedA = requireNotNull(userAccountManager.buildUserAccount(accountA))
        val unchangedB = requireNotNull(userAccountManager.buildUserAccount(accountB))
        assertEquals(
            "refresh-token-refresh-a",
            requestFormValue(submittedRequest.captured, "refresh_token"),
        )
        assertEquals("refreshed-auth-token-a", refreshedA.authToken)
        assertEquals("rotated-refresh-token-a", refreshedA.refreshTokenForPersistence)
        assertEquals(userB.authToken, unchangedB.authToken)
        assertEquals(userB.refreshTokenForPersistence, unchangedB.refreshTokenForPersistence)
        assertEquals(userB.userId, userAccountManager.currentUser?.userId)
        assertEquals(userB.orgId, userAccountManager.currentUser?.orgId)
        verify(exactly = 1) { httpClient.newCall(any()) }
    }

    @Test
    fun retainedClient_afterAIsRemoved_failsWithoutTouchingCurrentB() {
        persistUser("removed-a")
        val managerA = requireNotNull(sdkManager.clientManager)
        val clientA = requireNotNull(managerA.peekRestClient())
        val accountA = requireNotNull(managerA.account)

        val userB = persistUser("removed-b")
        val accountB = requireNotNull(userAccountManager.buildAccount(userB))
        assertTrue(accountManager.removeAccountExplicitly(accountA))

        val httpClient = mockk<OkHttpClient>(relaxed = true)
        mockkObject(HttpAccess.DEFAULT)
        every { HttpAccess.DEFAULT.okHttpClient } returns httpClient

        try {
            assertThrows(IOException::class.java) {
                clientA.refreshAccessToken()
            }
        } finally {
            unmockkObject(HttpAccess.DEFAULT)
        }

        val unchangedB = requireNotNull(userAccountManager.buildUserAccount(accountB))
        assertEquals(userB.authToken, unchangedB.authToken)
        assertEquals(userB.refreshTokenForPersistence, unchangedB.refreshTokenForPersistence)
        assertEquals(userB.userId, userAccountManager.currentUser?.userId)
        assertEquals(userB.orgId, userAccountManager.currentUser?.orgId)
        verify(exactly = 0) { httpClient.newCall(any()) }
    }

    @Test
    fun retainedClient_afterTransportFailure_retriesAWithoutTouchingCurrentB() {
        val userA = persistUser("retry-a")
        val managerA = requireNotNull(sdkManager.clientManager)
        val clientA = requireNotNull(managerA.peekRestClient())
        val accountA = requireNotNull(managerA.account)

        val userB = persistUser("retry-b")
        val accountB = requireNotNull(userAccountManager.buildAccount(userB))
        var attempts = 0
        val submittedRequests = mutableListOf<Request>()
        val httpClient = mockk<OkHttpClient> {
            every { newCall(capture(submittedRequests)) } answers {
                mockk<Call> {
                    every { execute() } answers {
                        attempts++
                        if (attempts == 1) {
                            throw IOException("offline")
                        }
                        successfulRefreshResponse(
                            suffix = "retry-a",
                            accessToken = "refreshed-auth-token-retry-a",
                            refreshToken = "rotated-refresh-token-retry-a",
                        )
                    }
                }
            }
        }
        mockkObject(HttpAccess.DEFAULT)
        every { HttpAccess.DEFAULT.okHttpClient } returns httpClient

        try {
            assertThrows(IOException::class.java) {
                clientA.refreshAccessToken()
            }
            val unchangedA = requireNotNull(userAccountManager.buildUserAccount(accountA))
            val unchangedB = requireNotNull(userAccountManager.buildUserAccount(accountB))
            assertEquals(userA.authToken, unchangedA.authToken)
            assertEquals(userA.refreshTokenForPersistence, unchangedA.refreshTokenForPersistence)
            assertEquals(userB.authToken, unchangedB.authToken)
            assertEquals(userB.refreshTokenForPersistence, unchangedB.refreshTokenForPersistence)
            assertEquals(userB, userAccountManager.currentUser)

            clientA.refreshAccessToken()
        } finally {
            unmockkObject(HttpAccess.DEFAULT)
        }

        val refreshedA = requireNotNull(userAccountManager.buildUserAccount(accountA))
        val unchangedB = requireNotNull(userAccountManager.buildUserAccount(accountB))
        assertEquals("refreshed-auth-token-retry-a", refreshedA.authToken)
        assertEquals("rotated-refresh-token-retry-a", refreshedA.refreshTokenForPersistence)
        assertEquals(userB.authToken, unchangedB.authToken)
        assertEquals(userB.refreshTokenForPersistence, unchangedB.refreshTokenForPersistence)
        assertEquals(userB, userAccountManager.currentUser)
        assertEquals(2, attempts)
        assertEquals(2, submittedRequests.size)
        submittedRequests.forEach { request ->
            assertEquals(
                "refresh-token-retry-a",
                requestFormValue(request, "refresh_token"),
            )
        }
        verify(exactly = 2) { httpClient.newCall(any()) }
    }

    @Test
    fun refreshToken_withNullUser_refreshesCurrentPersistedUser() {
        val currentUser = persistUser("current-refresh")
        val currentAccount = requireNotNull(userAccountManager.buildAccount(currentUser))
        val submittedRequest = slot<Request>()
        val httpClient = mockRefreshHttpClient(
            submittedRequest,
            successfulRefreshResponse(
                suffix = "current-refresh",
                accessToken = "refreshed-current-access-token",
                refreshToken = "rotated-current-refresh-token",
            ),
        )

        try {
            userAccountManager.refreshToken(null)
        } finally {
            unmockkObject(HttpAccess.DEFAULT)
        }

        val refreshedUser = requireNotNull(
            userAccountManager.buildUserAccount(currentAccount)
        )
        assertEquals(
            currentUser.refreshTokenForPersistence,
            requestFormValue(submittedRequest.captured, "refresh_token"),
        )
        assertEquals(
            if (sdkManager.useHybridAuthentication) "hybrid_refresh" else "refresh_token",
            requestFormValue(submittedRequest.captured, "grant_type"),
        )
        assertEquals("refreshed-current-access-token", refreshedUser.authToken)
        assertEquals(
            "rotated-current-refresh-token",
            refreshedUser.refreshTokenForPersistence,
        )
        assertEquals(currentUser.userId, userAccountManager.currentUser?.userId)
        assertEquals(currentUser.orgId, userAccountManager.currentUser?.orgId)
        verify(exactly = 1) { httpClient.newCall(any()) }
    }

    @Test
    fun getRestClient_withCurrentUser_returnsThatUsersClient() {
        val user = persistUser("callback")
        val activity = mockk<Activity>(relaxed = true)
        val clients = mutableListOf<RestClient>()

        sdkManager.getRestClient(activity) { client -> clients += client }

        assertEquals(1, clients.size)
        assertClientFor(clients.single(), user)
        verify(exactly = 0) {
            activity.startActivityForResult(any<Intent>(), any())
        }
    }

    @Test
    fun getRestClient_withCurrentUser_populatesClientManagerCacheWithDeliveredInstance() {
        /*
         * Regression guard for the account-resolution caching fix:
         * getRestClient() must resolve through the cached clientManager
         * property rather than constructing a new ClientManager per call,
         * since it is invoked from SalesforceActivityDelegate's onResume()
         * on every Activity resume. Before the fix, getRestClient() built
         * its own ClientManager directly and never touched the cache field,
         * so this assertion fails against the pre-fix implementation.
         */
        val user = persistUser("resume")
        val activity = mockk<Activity>(relaxed = true)
        val deliveredManagers = mutableListOf<RestClient>()
        clearCachedClientManagerField()

        sdkManager.getRestClient(activity) { client -> deliveredManagers += client }

        assertEquals(1, deliveredManagers.size)
        assertClientFor(deliveredManagers.single(), user)
        val cachedEntry = readCachedClientManagerField()
        assertTrue(
            "getRestClient() must populate the clientManager cache, not bypass it",
            cachedEntry != null,
        )
    }

    @Test
    fun getRestClient_withUnusableCurrentUser_removesExactAccountWithoutCallback() {
        val user = persistUser("unusable")
        val account = requireNotNull(userAccountManager.buildAccount(user))
        accountManager.setUserData(account, AuthenticatorService.KEY_LOGIN_URL, null)
        val activity = mockk<Activity>(relaxed = true)
        var callbackCount = 0

        sdkManager.getRestClient(activity) { callbackCount++ }

        assertEquals(0, callbackCount)
        assertFalse(accountManager.getAccountsByType(account.type).contains(account))
        verify(exactly = 0) {
            activity.startActivityForResult(any<Intent>(), any())
        }
    }

    @Test
    fun getRestClient_withMalformedCurrentAccount_removesItWithoutCallback() {
        val user = persistUser("malformed")
        val account = requireNotNull(userAccountManager.buildAccount(user))
        accountManager.setUserData(account, AccountManager.KEY_AUTHTOKEN, null)
        val activity = mockk<Activity>(relaxed = true)
        var callbackCount = 0

        sdkManager.getRestClient(activity) { callbackCount++ }

        assertEquals(0, callbackCount)
        assertFalse(accountManager.getAccountsByType(account.type).contains(account))
        verify(exactly = 0) {
            activity.startActivityForResult(any<Intent>(), any())
        }
    }

    @Test
    fun getRestClient_resolvesCurrentAccountExactlyOnce() {
        /*
         * Regression guard for a switch race: before the fix, account came
         * from userAccountManager.currentAccount and client came from
         * clientManager, which independently re-resolves identity via
         * cachedCurrentUser — two separate current-user reads that could
         * observe different users if the current user switched in between,
         * e.g. delivering one user's client while logging out a different,
         * stale-snapshot account. The fix reads currentAccount exactly once
         * and derives the client from that same account (via
         * buildUserAccount + the shared resolveClientManager helper), so
         * there is only one identity read for the whole call, closing the
         * window by construction. Asserting the call count directly catches
         * a future regression that reintroduces a second independent read.
         */
        persistUser("single-read")
        val spyUserAccountManager = spyk(userAccountManager)
        val spySdkManager = spyk(sdkManager)
        every { spySdkManager.userAccountManager } returns spyUserAccountManager
        val activity = mockk<Activity>(relaxed = true)
        var callbackCount = 0

        spySdkManager.getRestClient(activity) { callbackCount++ }

        assertEquals(1, callbackCount)
        verify(exactly = 1) { spyUserAccountManager.currentAccount }
    }

    @Test
    fun getRestClient_withNoCurrentUser_launchesConfiguredLoginWithoutCallback() {
        userAccountManager.clearStoredCurrentUserInfo()
        val activity = mockk<Activity>(relaxed = true)
        every { activity.packageName } returns sdkManager.appContext.packageName
        val launchedIntent = slot<Intent>()
        var callbackCount = 0

        sdkManager.getRestClient(activity) { callbackCount++ }

        assertEquals(0, callbackCount)
        verify(exactly = 1) {
            activity.startActivityForResult(capture(launchedIntent), 0)
        }
        assertEquals(
            sdkManager.loginActivityClass.name,
            launchedIntent.captured.component?.className,
        )
        assertEquals(sdkManager.appContext.packageName, launchedIntent.captured.`package`)
        assertTrue(
            launchedIntent.captured.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0,
        )
    }

    private fun persistUser(suffix: String): UserAccount = buildUser(suffix).also { user ->
        userAccountManager.createAccount(user)
        requireNotNull(userAccountManager.buildAccount(user))
    }

    private fun assertManagerBoundTo(manager: ClientManager, user: UserAccount) {
        assertEquals(user.accountName, requireNotNull(manager.account).name)
        assertClientFor(requireNotNull(manager.peekRestClient()), user)
    }

    private fun assertClientFor(client: RestClient, user: UserAccount) {
        assertEquals(user.accountName, client.clientInfo.accountName)
        assertEquals(user.userId, client.clientInfo.userId)
        assertEquals(user.orgId, client.clientInfo.orgId)
    }

    private fun cachedClientManagerField() =
        SalesforceSDKManager::class.java.getDeclaredField("cachedClientManager").apply {
            isAccessible = true
        }

    private fun clearCachedClientManagerField() {
        cachedClientManagerField().set(sdkManager, null)
    }

    private fun readCachedClientManagerField(): Any? =
        cachedClientManagerField().get(sdkManager)

    private fun mockRefreshHttpClient(
        request: CapturingSlot<Request>,
        response: Response,
    ): OkHttpClient {
        val client = mockk<OkHttpClient> {
            every { newCall(capture(request)) } returns mockk<Call> {
                every { execute() } returns response
            }
        }
        mockkObject(HttpAccess.DEFAULT)
        every { HttpAccess.DEFAULT.okHttpClient } returns client
        return client
    }

    private fun successfulRefreshResponse(
        suffix: String,
        accessToken: String,
        refreshToken: String,
    ): Response {
        val body = """
            {
              "access_token": "$accessToken",
              "refresh_token": "$refreshToken",
              "instance_url": "https://instance-$suffix.example.com",
              "id": "https://login.example.com/id/org-$suffix/user-$suffix",
              "token_type": "Bearer",
              "issued_at": "1234567890",
              "signature": "test-signature"
            }
        """.trimIndent().toResponseBody("application/json; charset=utf-8".toMediaType())
        return Response.Builder()
            .request(Request.Builder().url("https://login.example.com/services/oauth2/token").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()
    }

    private fun requestFormValue(request: Request, key: String): String? {
        val body = request.body as? FormBody ?: return null
        return (0 until body.size)
            .firstOrNull { index -> body.name(index) == key }
            ?.let(body::value)
    }

    private fun buildUser(suffix: String): UserAccount = UserAccountBuilder.getInstance()
        .accountName("$TEST_ACCOUNT_PREFIX$suffix")
        .username("sdk-manager-$suffix@example.com")
        .authToken("auth-token-$suffix")
        .refreshToken("refresh-token-$suffix")
        .instanceServer("https://instance-$suffix.example.com")
        .loginServer("https://login.example.com")
        .idUrl("https://login.example.com/id/org-$suffix/user-$suffix")
        .clientId("client-$suffix")
        .orgId("org-$suffix")
        .userId("user-$suffix")
        .build()

    private fun removeTestAccounts() {
        accountManager.getAccountsByType(sdkManager.accountType)
            .filter { account -> account.name.startsWith(TEST_ACCOUNT_PREFIX) }
            .forEach { account: Account -> accountManager.removeAccountExplicitly(account) }
    }

    private fun ensureSdkManagerInitialized() {
        try {
            SalesforceSDKManager.getInstance()
        } catch (e: RuntimeException) {
            if (e.message?.contains("SalesforceSDKManager.init") == true) {
                SalesforceSDKManager.initNative(
                    getInstrumentation().targetContext,
                    LoginActivity::class.java,
                )
            } else {
                throw e
            }
        }
    }

    companion object {
        private const val TEST_ACCOUNT_PREFIX = "sdk-manager-client-test-"
    }
}
