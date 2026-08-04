package com.salesforce.androidsdk.rest

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Bundle
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.accounts.UserAccountManagerTest
import com.salesforce.androidsdk.analytics.EventBuilderHelper
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.auth.OAuth2.LogoutReason.CLIENT_BLOCKED
import com.salesforce.androidsdk.auth.OAuth2.LogoutReason.REFRESH_TOKEN_EXPIRED
import com.salesforce.androidsdk.rest.ClientManager.ACCESS_TOKEN_REFRESH_INTENT
import com.salesforce.androidsdk.rest.ClientManager.ACCESS_TOKEN_REVOKE_INTENT
import com.salesforce.androidsdk.rest.ClientManager.EXTRA_TOKEN_ERROR
import com.salesforce.androidsdk.rest.ClientManager.EXTRA_TOKEN_ERROR_DESCRIPTION
import com.salesforce.androidsdk.rest.ClientManager.INSTANCE_URL_UPDATE_INTENT
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private const val OLD_ACCESS_TOKEN = "old-token"
private const val REFRESHED_ACCESS_TOKEN = "refreshed-auth-token"
private const val REFRESH_TOKEN = "refresh-token"
private const val ROTATED_REFRESH_TOKEN = "rotated-refresh-token"

@SmallTest
class ClientManagerMockTest {
    private lateinit var mockSDKManager: SalesforceSDKManager
    private lateinit var mockAppContext: Context
    private lateinit var mockUserAccountManager: UserAccountManager
    private lateinit var mockOkHttpClient: OkHttpClient
    private lateinit var refreshResponse: Response

    @Before
    fun setUp() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        mockUserAccountManager = mockk(relaxed = true)
        mockAppContext = mockk(relaxed = true) {
            every { packageName } returns "packageName"
            every { sendBroadcast(any()) } just runs
            every { externalCacheDir } returns null
            every { filesDir } returns targetContext.filesDir
            every { getSharedPreferences(any(), any()) } answers {
                targetContext.getSharedPreferences(firstArg(), MODE_PRIVATE)
            }
        }

        mockkObject(SalesforceSDKManager)
        mockSDKManager = mockk<SalesforceSDKManager> {
            every {
                logout(any(), any(), any(), any())
            } returns Unit
            every { registerUsedAppFeature(any()) } returns true
            every { registerUsedAppFeature(any(), any()) } just runs
            every { unregisterUsedAppFeature(any()) } returns true
            every { userAccountManager } returns mockUserAccountManager
            every { deviceId } returns "test-device-id-123"
            every { additionalOauthKeys } returns emptyList()
            every { useHybridAuthentication } returns true
            every { appAttestationClient } returns null
            every { appContext } returns mockAppContext
            every { isDevSupportEnabled() } returns true
            every { useDPoP } returns false
        }
        every { SalesforceSDKManager.getInstance() } returns mockSDKManager
        mockkStatic(UserAccountManager::class)
        every { UserAccountManager.getInstance() } returns mockUserAccountManager
        mockkStatic(EventBuilderHelper::class)
        every { EventBuilderHelper.createAndStoreEvent(any(), any(), any(), any()) } just runs

        val responseBody = """
                {
                    "access_token": "$REFRESHED_ACCESS_TOKEN",
                    "instance_url": "https://login.salesforce.com",
                    "id": "https://login.salesforce.com/id/orgId/userId",
                    "token_type": "Bearer",
                    "issued_at": "1234567890",
                    "signature": "mock-signature"
                }
            """.trimIndent().toResponseBody("application/json; charset=utf-8".toMediaType())
        refreshResponse = mockk<Response>(relaxed = true) {
            every { isSuccessful } returns true
            every { close() } just runs
            every { body } returns responseBody
        }

        mockkObject(HttpAccess.DEFAULT)
        mockOkHttpClient = mockk {
            every { newCall(any()) } returns mockk<Call> {
                every { execute() } returns refreshResponse
            }
        }
        every { HttpAccess.DEFAULT.okHttpClient } returns mockOkHttpClient

        // REFRESH_STATES is static and survives across Robolectric/instrumented tests;
        // unmockkAll() won't clear it. Reset so a leftover refreshing=true can't corrupt
        // later tests.
        ClientManager.AccMgrAuthTokenProvider.resetRefreshStateForTest()
    }

    @After
    fun tearDown() {
        ClientManager.AccMgrAuthTokenProvider.resetRefreshStateForTest()
        unmockkAll()
    }

    @Test
    fun testGetNewAuthToken_MatchingAccount() {
        val userSlot = slot<UserAccount>()
        val broadcastIntentSlot = slot<Intent>()
        val mockAccount = mockk<Account>(relaxed = true)
        val mockUser = mockk<UserAccount>(relaxed = true) {
            every { authToken } returns OLD_ACCESS_TOKEN
            every { refreshToken } returns REFRESH_TOKEN
            every { instanceServer } returns "https://login.salesforce.com"
            every { loginServer } returns "https://login.salesforce.com"
        }
        val mockClientManager = boundClientManager(mockAccount, arrayOf(mockAccount))
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } returns Bundle()
        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(mockClientManager)

        val result = authTokenProvider.getNewAuthToken()
        assertEquals(REFRESHED_ACCESS_TOKEN, result)
        assertTrue(authTokenProvider.lastRefreshTime > 0)

        verify(exactly = 0) {
            mockSDKManager.logout(any(), any(), any(), any())
        }
        verify(exactly = 1) {
            mockUserAccountManager.updateAccount(mockAccount, capture(userSlot))
            mockAppContext.sendBroadcast(capture(broadcastIntentSlot))
        }
        assertEquals(REFRESHED_ACCESS_TOKEN, userSlot.captured.authToken)
        assertEquals(ACCESS_TOKEN_REFRESH_INTENT, broadcastIntentSlot.captured.action)
    }

    @Test
    fun testGetNewAuthToken_InstanceUrlChange() {
        val userSlot = slot<UserAccount>()
        val broadcastIntentSlot = slot<Intent>()
        val mockAccount = mockk<Account>(relaxed = true)
        val mockUser = mockk<UserAccount>(relaxed = true) {
            every { authToken } returns OLD_ACCESS_TOKEN
            every { refreshToken } returns REFRESH_TOKEN
            every { loginServer } returns "https://login.salesforce.com"
        }
        val mockClientManager = boundClientManager(mockAccount, arrayOf(mockAccount))
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } returns Bundle()
        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(mockClientManager)

        val result = authTokenProvider.getNewAuthToken()
        assertEquals(REFRESHED_ACCESS_TOKEN, result)

        verify(exactly = 0) {
            mockSDKManager.logout(any(), any(), any(), any())
        }
        verify(exactly = 1) {
            mockUserAccountManager.updateAccount(mockAccount, capture(userSlot))
            mockAppContext.sendBroadcast(capture(broadcastIntentSlot))
        }
        assertEquals(REFRESHED_ACCESS_TOKEN, userSlot.captured.authToken)
        assertEquals(INSTANCE_URL_UPDATE_INTENT, broadcastIntentSlot.captured.action)
    }

    @Test
    fun testGetNewAuthToken_NoAccounts() {
        val mockAccount = mockk<Account>(relaxed = true)
        val mockUser = mockk<UserAccount>(relaxed = true) {
            every { authToken } returns OLD_ACCESS_TOKEN
            every { refreshToken } returns REFRESH_TOKEN
            every { loginServer } returns "https://login.salesforce.com"
        }
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        val mockClientManager = boundClientManager(mockAccount, arrayOf(mockAccount))
        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(mockClientManager)
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns null

        assertNull(authTokenProvider.getNewAuthToken())
        verify(exactly = 0) {
            mockOkHttpClient.newCall(any())
            mockSDKManager.logout(any(), any(), any(), any())
            mockAppContext.sendBroadcast(any())
        }
    }

    @Test
    fun testGetNewAuthToken_NoMatchingAccount() {
        val mockAccount = mockk<Account>(relaxed = true)
        val mockUser = mockk<UserAccount>(relaxed = true) {
            every { authToken } returns "not-matching"
            every { refreshToken } returns "not-matching"
        }
        val mockClientManager = boundClientManager(mockAccount, arrayOf(mockAccount))
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(mockClientManager)

        assertNull(authTokenProvider.getNewAuthToken())
        verify(exactly = 0) {
            mockSDKManager.logout(any(), any(), any(), any())
            mockAppContext.sendBroadcast(any())
        }
    }

    @Test
    fun testGetNewAuthToken_NullAuthToken() {
        val mockAccount = mockk<Account>(relaxed = true)
        val mockUser = mockk<UserAccount>(relaxed = true) {
            every { authToken } returns "not-matching"
            every { refreshToken } returns "not-matching"
        }
        val mockClientManager = boundClientManager(mockAccount, arrayOf(mockAccount))
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(mockClientManager)

        assertNull(authTokenProvider.getNewAuthToken())
        verify(exactly = 0) {
            mockSDKManager.logout(any(), any(), any(), any())
            mockAppContext.sendBroadcast(any())
        }
    }

    @Test
    fun testGetNewAuthToken_Multiuser() {
        val user2Token = "user2-token"
        val userSlot = slot<UserAccount>()
        val mockAccount = mockk<Account>(relaxed = true)
        val mockAccount2 = mockk<Account>(relaxed = true)
        val mockUser = mockk<UserAccount>(relaxed = true) {
            every { authToken } returns OLD_ACCESS_TOKEN
            every { refreshToken } returns REFRESH_TOKEN
            every { loginServer } returns "https://login.salesforce.com"
        }
        val mockUser2 = mockk<UserAccount>(relaxed = true) {
            every { authToken } returns user2Token
            every { refreshToken } returns "user2Refresh"
            every { loginServer } returns "https://login.salesforce.com"
        }
        val mockClientManager = boundClientManager(mockAccount, arrayOf(mockAccount, mockAccount2))
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount2) } returns mockUser2
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } returns Bundle()
        every { mockUserAccountManager.updateAccount(mockAccount2, any()) } returns Bundle()
        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(mockClientManager)

        assertEquals(REFRESHED_ACCESS_TOKEN, authTokenProvider.getNewAuthToken())
        verify(exactly = 0) {
            mockSDKManager.logout(any(), any(), any(), any())
            mockUserAccountManager.updateAccount(mockAccount2, any())
        }
        verify(exactly = 1) {
            mockUserAccountManager.updateAccount(mockAccount, capture(userSlot))
        }
        assertEquals(REFRESHED_ACCESS_TOKEN, userSlot.captured.authToken)
    }

    @Test
    fun testGetNewAuthToken_Revoked() {
        every { HttpAccess.DEFAULT.okHttpClient } returns mockk<OkHttpClient> {
            every { newCall(any()) } returns mockk<Call> {
                every { execute() } returns invalidGrantResponse()
            }
        }
        val broadcastIntentSlot = slot<Intent>()
        val mockAccount = mockk<Account>(relaxed = true)
        val mockUser = mockk<UserAccount>(relaxed = true) {
            every { authToken } returns OLD_ACCESS_TOKEN
            every { refreshToken } returns REFRESH_TOKEN
            every { loginServer } returns "https://login.salesforce.com"
        }

        // Use a bound manager so terminal cleanup targets this exact account.
        val clientManagerSpy = boundClientManager(mockAccount, arrayOf(mockAccount))
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } returns Bundle()
        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(clientManagerSpy)

        assertNull(authTokenProvider.getNewAuthToken())
        verify(exactly = 0) {
            mockUserAccountManager.updateAccount(any(), any())
        }
        verify(exactly = 1) {
            mockSDKManager.logout(mockAccount, any(), true, REFRESH_TOKEN_EXPIRED)
            mockAppContext.sendBroadcast(capture(broadcastIntentSlot))
        }
        assertEquals(ACCESS_TOKEN_REVOKE_INTENT, broadcastIntentSlot.captured.action)
    }

    /*
        Server-side Refresh Token Rotation (RTR): when the token endpoint returns
        a rotated refresh_token, the provider must update its cached refresh
        token so subsequent calls don't reuse the now-invalidated previous one.
     */
    @Test
    fun testGetNewAuthToken_RefreshTokenRotation_UpdatesCachedRefreshToken() {
        val responseBody = """
                {
                    "access_token": "$REFRESHED_ACCESS_TOKEN",
                    "refresh_token": "$ROTATED_REFRESH_TOKEN",
                    "instance_url": "https://login.salesforce.com",
                    "id": "https://login.salesforce.com/id/orgId/userId",
                    "token_type": "Bearer",
                    "issued_at": "1234567890",
                    "signature": "mock-signature"
                }
            """.trimIndent().toResponseBody("application/json; charset=utf-8".toMediaType())
        val rotatedResponse = mockk<Response>(relaxed = true) {
            every { isSuccessful } returns true
            every { close() } just runs
            every { body } returns responseBody
        }
        every { HttpAccess.DEFAULT.okHttpClient } returns mockk<OkHttpClient> {
            every { newCall(any()) } returns mockk<Call> {
                every { execute() } returns rotatedResponse
            }
        }

        val userSlot = slot<UserAccount>()
        val mockAccount = mockk<Account>(relaxed = true)
        val mockUser = mockk<UserAccount>(relaxed = true) {
            every { authToken } returns OLD_ACCESS_TOKEN
            every { refreshToken } returns REFRESH_TOKEN
            every { loginServer } returns "https://login.salesforce.com"
        }
        val mockClientManager = boundClientManager(mockAccount, arrayOf(mockAccount))
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } returns Bundle()

        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(mockClientManager)

        // First refresh: server rotates the refresh token.
        assertEquals(REFRESHED_ACCESS_TOKEN, authTokenProvider.getNewAuthToken())

        // The persisted account should be updated with the rotated refresh token...
        verify(exactly = 1) {
            mockUserAccountManager.updateAccount(mockAccount, capture(userSlot))
        }
        assertEquals(ROTATED_REFRESH_TOKEN, userSlot.captured.refreshTokenForPersistence)
        // ...and so should the provider's in-memory cache, so that subsequent
        // refreshes (and getRefreshToken consumers) use the rotated token.
        assertEquals(ROTATED_REFRESH_TOKEN, authTokenProvider.refreshToken)
        /*
         * The confirmed-rotation timestamp must be stamped on the account
         * captured by this single primary updateAccount call — i.e. persisted
         * by the authoritative write, NOT as a side effect of
         * registerUsedAppFeature (mocked out here, hence updateAccount
         * exactly=1). Guards against the timestamp silently ceasing to persist
         * if feature registration ever short-circuits.
         */
        assertNotNull(
            "Rotation timestamp must be persisted by the primary updateAccount call",
            userSlot.captured.lastTokenRotationTime
        )
    }

    /*
        Server-side Refresh Token Rotation (RTR): after a refresh that rotates
        the refresh token, the provider's cached refresh token must reflect
        the new value so that a subsequent refresh sends the current token
        and the per-account lookup matches the rotated value persisted to
        the account.
     */
    @Test
    fun testGetNewAuthToken_RefreshTokenRotation_SubsequentRefreshSucceeds() {
        val firstRotated = ROTATED_REFRESH_TOKEN
        val secondRotated = "rotated-refresh-token-2"

        fun rotationResponse(rt: String): Response {
            val responseBody = """
                {
                    "access_token": "$REFRESHED_ACCESS_TOKEN",
                    "refresh_token": "$rt",
                    "instance_url": "https://login.salesforce.com",
                    "id": "https://login.salesforce.com/id/orgId/userId",
                    "token_type": "Bearer",
                    "issued_at": "1234567890",
                    "signature": "mock-signature"
                }
                """.trimIndent().toResponseBody("application/json; charset=utf-8".toMediaType())
            return mockk<Response>(relaxed = true) {
                every { isSuccessful } returns true
                every { close() } just runs
                every { body } returns responseBody
            }
        }

        // Return a different rotated refresh token on each refresh.
        every { HttpAccess.DEFAULT.okHttpClient } returns mockk<OkHttpClient> {
            every { newCall(any()) } returnsMany listOf(
                mockk<Call> { every { execute() } returns rotationResponse(firstRotated) },
                mockk<Call> { every { execute() } returns rotationResponse(secondRotated) },
            )
        }

        val mockAccount = mockk<Account>(relaxed = true)
        // The persisted account's tokens follow whatever updateAccount was last called with
        // (i.e., the most recent rotated values). Both access and refresh tokens advance
        // together, as they do in production when a refreshed UserAccount is persisted.
        var persistedAuthToken = OLD_ACCESS_TOKEN
        var persistedRefreshToken = REFRESH_TOKEN
        val mockUser = mockk<UserAccount>(relaxed = true) {
            every { authToken } answers { persistedAuthToken }
            every { refreshToken } answers { persistedRefreshToken }
            every { loginServer } returns "https://login.salesforce.com"
        }
        val mockClientManager = boundClientManager(mockAccount, arrayOf(mockAccount))
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } answers {
            persistedAuthToken = secondArg<UserAccount>().authToken
            persistedRefreshToken = secondArg<UserAccount>().refreshToken
            Bundle()
        }

        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(mockClientManager)

        // First refresh succeeds, rotates to firstRotated.
        assertEquals(REFRESHED_ACCESS_TOKEN, authTokenProvider.getNewAuthToken())
        assertEquals(firstRotated, authTokenProvider.refreshToken)
        assertEquals(firstRotated, persistedRefreshToken)

        // Second refresh, ensure each rotation is stored.
        assertEquals(REFRESHED_ACCESS_TOKEN, authTokenProvider.getNewAuthToken())
        assertEquals(secondRotated, authTokenProvider.refreshToken)
        verify(exactly = 0) {
            mockSDKManager.logout(any(), any(), any(), any())
        }
    }

    /*
        Non-current user tests the scenario of attempting to make a
        network call as the previous user on user account switch, but
        requiring a token refresh.
     */
    @Test
    fun testGetNewAuthToken_Multiuser_NonCurrentUser() {
        val user2Token = "user2-token"
        val userSlot = slot<UserAccount>()
        val mockAccount = mockk<Account>(relaxed = true)
        val mockAccount2 = mockk<Account>(relaxed = true)
        val mockUser = mockk<UserAccount>(relaxed = true) {
            every { authToken } returns OLD_ACCESS_TOKEN
            every { refreshToken } returns REFRESH_TOKEN
            every { loginServer } returns "https://login.salesforce.com"
        }
        val mockUser2 = mockk<UserAccount>(relaxed = true) {
            every { authToken } returns user2Token
            every { refreshToken } returns "user2Refresh"
            every { loginServer } returns "https://login.salesforce.com"
        }
        val mockClientManager = boundClientManager(mockAccount, arrayOf(mockAccount, mockAccount2))
        // The account that we are not refreshing for is the current account.
        every { mockUserAccountManager.currentUser } returns mockUser2
        every { mockUserAccountManager.currentAccount } returns mockAccount2
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount2) } returns mockUser2
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } returns Bundle()
        every { mockUserAccountManager.updateAccount(mockAccount2, any()) } returns Bundle()
        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(mockClientManager)

        assertEquals(REFRESHED_ACCESS_TOKEN, authTokenProvider.getNewAuthToken())
        verify(exactly = 0) {
            mockSDKManager.logout(any(), any(), any(), any())
            mockUserAccountManager.updateAccount(mockAccount2, any())
        }
        verify(exactly = 1) {
            mockUserAccountManager.updateAccount(mockAccount, capture(userSlot))
        }
        assertEquals(REFRESHED_ACCESS_TOKEN, userSlot.captured.authToken)
    }

    /*
        Non-current user tests the scenario of attempting to make a
        network call as the previous user on user account switch, but
        requiring a token refresh.
     */
    @Test
    fun testGetNewAuthToken_Multiuser_RevokeNonCurrentUser() {
        every { HttpAccess.DEFAULT.okHttpClient } returns mockk<OkHttpClient> {
            every { newCall(any()) } returns mockk<Call> {
                every { execute() } returns invalidGrantResponse()
            }
        }
        val broadcastIntentSlot = slot<Intent>()
        val user2Token = "user2-token"
        val mockAccount = mockk<Account>(relaxed = true)
        val mockAccount2 = mockk<Account>(relaxed = true)
        val mockUser = mockk<UserAccount>(relaxed = true) {
            every { authToken } returns OLD_ACCESS_TOKEN
            every { refreshToken } returns REFRESH_TOKEN
            every { loginServer } returns "https://login.salesforce.com"
        }
        val mockUser2 = mockk<UserAccount>(relaxed = true) {
            every { authToken } returns user2Token
            every { refreshToken } returns "user2Refresh"
            every { loginServer } returns "https://login.salesforce.com"
        }
        // The account that we are not refreshing for is the current account.
        every { mockUserAccountManager.currentUser } returns mockUser2
        every { mockUserAccountManager.currentAccount } returns mockAccount2
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount2) } returns mockUser2
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } returns Bundle()
        every { mockUserAccountManager.updateAccount(mockAccount2, any()) } returns Bundle()
        // Use a bound manager so terminal cleanup targets the non-current account.
        val clientManagerSpy = boundClientManager(mockAccount, arrayOf(mockAccount, mockAccount2))
        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(clientManagerSpy)

        assertNull(authTokenProvider.getNewAuthToken())
        verify(exactly = 0) {
            mockUserAccountManager.updateAccount(any(), any())
            mockSDKManager.logout(mockAccount2, any(), any(), any())
            mockSDKManager.logout(null, any(), any(), any())
            mockUserAccountManager.updateAccount(mockAccount2, any())
        }

        verify(exactly = 1) {
            mockSDKManager.logout(mockAccount, any(), false, REFRESH_TOKEN_EXPIRED)
            mockAppContext.sendBroadcast(capture(broadcastIntentSlot))
        }
        assertEquals(ACCESS_TOKEN_REVOKE_INTENT, broadcastIntentSlot.captured.action)
    }

    private data class TokenErrorResult(
        val authTokenProvider: ClientManager.AccMgrAuthTokenProvider,
        val broadcastIntentSlot: CapturingSlot<Intent>,
        val mockAccount: Account,
    )

    private fun setupTokenErrorScenario(
        error: String,
        errorDescription: String,
        httpStatus: Int = 400,
    ): TokenErrorResult {
        val errorBody = """
            {"error": "$error", "error_description": "$errorDescription"}
        """.trimIndent().toResponseBody("application/json; charset=utf-8".toMediaType())
        every { HttpAccess.DEFAULT.okHttpClient } returns mockk<OkHttpClient> {
            every { newCall(any()) } returns mockk<Call> {
                every { execute() } returns mockk<Response>(relaxed = true) {
                    every { isSuccessful } returns false
                    every { code } returns httpStatus
                    every { body } returns errorBody
                }
            }
        }
        val mockAccount = mockk<Account>(relaxed = true)
        val mockUser = mockk<UserAccount>(relaxed = true) {
            every { authToken } returns OLD_ACCESS_TOKEN
            every { refreshToken } returns REFRESH_TOKEN
            every { loginServer } returns "https://login.salesforce.com"
        }
        val clientManagerSpy = boundClientManager(mockAccount, arrayOf(mockAccount))
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser

        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(clientManagerSpy)
        return TokenErrorResult(authTokenProvider, slot(), mockAccount)
    }

    @Test
    fun testGetNewAuthToken_ClientBlocked_LogsOutWithClientBlockedReason() {
        val result = setupTokenErrorScenario("client_blocked", "Device failed integrity check")

        assertNull(result.authTokenProvider.getNewAuthToken())
        verify(exactly = 1) {
            mockSDKManager.logout(result.mockAccount, any(), true, CLIENT_BLOCKED)
            mockAppContext.sendBroadcast(capture(result.broadcastIntentSlot))
        }
        assertEquals(ACCESS_TOKEN_REVOKE_INTENT, result.broadcastIntentSlot.captured.action)
        assertEquals("client_blocked", result.broadcastIntentSlot.captured.getStringExtra(EXTRA_TOKEN_ERROR))
        assertEquals("Device failed integrity check", result.broadcastIntentSlot.captured.getStringExtra(EXTRA_TOKEN_ERROR_DESCRIPTION))
    }

    @Test
    fun testGetNewAuthToken_ClientBlockedRetry_DoesNotLogout() {
        val result = setupTokenErrorScenario("client_blocked_retry", "Attestation verification pending")

        assertNull(result.authTokenProvider.getNewAuthToken())
        verify(exactly = 0) {
            mockSDKManager.logout(any(), any(), any(), any())
        }
        verify(exactly = 1) {
            mockAppContext.sendBroadcast(capture(result.broadcastIntentSlot))
        }
        assertEquals(ACCESS_TOKEN_REVOKE_INTENT, result.broadcastIntentSlot.captured.action)
        assertEquals("client_blocked_retry", result.broadcastIntentSlot.captured.getStringExtra(EXTRA_TOKEN_ERROR))
        assertEquals("Attestation verification pending", result.broadcastIntentSlot.captured.getStringExtra(EXTRA_TOKEN_ERROR_DESCRIPTION))
    }

    @Test
    fun testGetNewAuthToken_InvalidGrant_LogsOutWithRefreshTokenExpired() {
        val result = setupTokenErrorScenario("invalid_grant", "expired authorization code")

        assertNull(result.authTokenProvider.getNewAuthToken())
        verify(exactly = 1) {
            mockSDKManager.logout(result.mockAccount, any(), true, REFRESH_TOKEN_EXPIRED)
            mockAppContext.sendBroadcast(capture(result.broadcastIntentSlot))
        }
        assertEquals(ACCESS_TOKEN_REVOKE_INTENT, result.broadcastIntentSlot.captured.action)
        assertEquals("invalid_grant", result.broadcastIntentSlot.captured.getStringExtra(EXTRA_TOKEN_ERROR))
        assertEquals("expired authorization code", result.broadcastIntentSlot.captured.getStringExtra(EXTRA_TOKEN_ERROR_DESCRIPTION))
    }

    @Test
    fun testGetNewAuthToken_UnparseableErrorResponse_BroadcastsWithoutExtras() {
        val malformedBody = "not json at all"
            .toResponseBody("text/plain".toMediaType())
        every { HttpAccess.DEFAULT.okHttpClient } returns mockk<OkHttpClient> {
            every { newCall(any()) } returns mockk<Call> {
                every { execute() } returns mockk<Response>(relaxed = true) {
                    every { isSuccessful } returns false
                    every { code } returns 400
                    every { body } returns malformedBody
                }
            }
        }
        val broadcastIntentSlot = slot<Intent>()
        val mockAccount = mockk<Account>(relaxed = true)
        val mockUser = mockk<UserAccount>(relaxed = true) {
            every { authToken } returns OLD_ACCESS_TOKEN
            every { refreshToken } returns REFRESH_TOKEN
            every { loginServer } returns "https://login.salesforce.com"
        }
        val clientManagerSpy = boundClientManager(mockAccount, arrayOf(mockAccount))
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser

        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(clientManagerSpy)

        assertNull(authTokenProvider.getNewAuthToken())
        verify(exactly = 1) {
            mockSDKManager.logout(mockAccount, any(), true, REFRESH_TOKEN_EXPIRED)
            mockAppContext.sendBroadcast(capture(broadcastIntentSlot))
        }
        assertEquals(ACCESS_TOKEN_REVOKE_INTENT, broadcastIntentSlot.captured.action)
        assertNull(broadcastIntentSlot.captured.getStringExtra(EXTRA_TOKEN_ERROR))
        assertNull(broadcastIntentSlot.captured.getStringExtra(EXTRA_TOKEN_ERROR_DESCRIPTION))
    }

    @Test
    fun testGetNewAuthToken_ClientBlockedRetry_SubsequentCallRemainsRetryable() {
        val result = setupTokenErrorScenario("client_blocked_retry", "Attestation verification pending")
        val clientManagerSpy = boundClientManager(result.mockAccount, arrayOf(result.mockAccount))
        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(clientManagerSpy)
        val firstHttpClient = HttpAccess.DEFAULT.okHttpClient

        assertNull(authTokenProvider.getNewAuthToken())
        assertEquals(-1L, authTokenProvider.lastRefreshTime)
        verify(exactly = 1) { firstHttpClient.newCall(any()) }

        // Set up a second error response for the second call.
        val errorBody2 = """
            {"error": "client_blocked_retry", "error_description": "Still pending"}
        """.trimIndent().toResponseBody("application/json; charset=utf-8".toMediaType())
        val secondHttpClient = mockk<OkHttpClient> {
            every { newCall(any()) } returns mockk<Call> {
                every { execute() } returns mockk<Response>(relaxed = true) {
                    every { isSuccessful } returns false
                    every { code } returns 400
                    every { body } returns errorBody2
                }
            }
        }
        every { HttpAccess.DEFAULT.okHttpClient } returns secondHttpClient

        // The second attempt remains retryable and has no terminal cleanup side effect.
        assertNull(authTokenProvider.getNewAuthToken())
        assertEquals(-1L, authTokenProvider.lastRefreshTime)
        verify(exactly = 1) { secondHttpClient.newCall(any()) }
        verify(exactly = 0) { mockSDKManager.logout(any(), any(), any(), any()) }
    }

    @Test
    fun testGetNewAuthToken_TerminalError_AlwaysLogsOutBoundAccount() {
        val result = setupTokenErrorScenario(
            "client_blocked", "Device failed integrity check",
        )

        assertNull(result.authTokenProvider.getNewAuthToken())
        verify(exactly = 1) {
            mockSDKManager.logout(result.mockAccount, any(), true, CLIENT_BLOCKED)
        }
        verify(exactly = 1) { mockAppContext.sendBroadcast(capture(result.broadcastIntentSlot)) }
        assertEquals(ACCESS_TOKEN_REVOKE_INTENT, result.broadcastIntentSlot.captured.action)
        assertEquals("client_blocked", result.broadcastIntentSlot.captured.getStringExtra(EXTRA_TOKEN_ERROR))
    }

    @Test
    fun testGetNewAuthToken_OAuthErrorRemainsTerminalRegardlessOfHttpStatus() {
        val result = setupTokenErrorScenario(
            "client_blocked",
            "Device failed integrity check",
            httpStatus = 500,
        )

        assertNull(result.authTokenProvider.getNewAuthToken())
        verify(exactly = 1) {
            mockSDKManager.logout(result.mockAccount, any(), true, CLIENT_BLOCKED)
        }
        verify(exactly = 1) {
            mockAppContext.sendBroadcast(capture(result.broadcastIntentSlot))
        }
        assertEquals(ACCESS_TOKEN_REVOKE_INTENT, result.broadcastIntentSlot.captured.action)
    }

    @Test
    fun testGetNewAuthToken_NullInstanceUrl_BroadcastsRefreshIntent() {
        val responseBody = """
                {
                    "access_token": "$REFRESHED_ACCESS_TOKEN",
                    "id": "https://login.salesforce.com/id/orgId/userId",
                    "token_type": "Bearer",
                    "issued_at": "1234567890",
                    "signature": "mock-signature"
                }
            """.trimIndent().toResponseBody("application/json; charset=utf-8".toMediaType())
        every { HttpAccess.DEFAULT.okHttpClient } returns mockk<OkHttpClient> {
            every { newCall(any()) } returns mockk<Call> {
                every { execute() } returns mockk<Response>(relaxed = true) {
                    every { isSuccessful } returns true
                    every { close() } just runs
                    every { body } returns responseBody
                }
            }
        }
        val broadcastIntentSlot = slot<Intent>()
        val mockAccount = mockk<Account>(relaxed = true)
        val mockUser = mockk<UserAccount>(relaxed = true) {
            every { authToken } returns OLD_ACCESS_TOKEN
            every { refreshToken } returns REFRESH_TOKEN
            every { loginServer } returns "https://login.salesforce.com"
            every { instanceServer } returns null
        }
        val mockClientManager = boundClientManager(mockAccount, arrayOf(mockAccount))
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } returns Bundle()

        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(mockClientManager)

        val result = authTokenProvider.getNewAuthToken()
        assertEquals(REFRESHED_ACCESS_TOKEN, result)
        verify(exactly = 1) { mockAppContext.sendBroadcast(capture(broadcastIntentSlot)) }
        assertEquals(ACCESS_TOKEN_REFRESH_INTENT, broadcastIntentSlot.captured.action)
    }

    @Test
    fun testGetNewAuthToken_MalformedResponse_MissingAccessToken_LogsOut() {
        val responseBody = """
                {
                    "instance_url": "https://login.salesforce.com",
                    "id": "https://login.salesforce.com/id/orgId/userId",
                    "token_type": "Bearer",
                    "issued_at": "1234567890",
                    "signature": "mock-signature"
                }
            """.trimIndent().toResponseBody("application/json; charset=utf-8".toMediaType())
        every { HttpAccess.DEFAULT.okHttpClient } returns mockk<OkHttpClient> {
            every { newCall(any()) } returns mockk<Call> {
                every { execute() } returns mockk<Response>(relaxed = true) {
                    every { isSuccessful } returns true
                    every { close() } just runs
                    every { body } returns responseBody
                }
            }
        }
        val broadcastIntentSlot = slot<Intent>()
        val mockAccount = mockk<Account>(relaxed = true)
        val mockUser = mockk<UserAccount>(relaxed = true) {
            every { authToken } returns OLD_ACCESS_TOKEN
            every { refreshToken } returns REFRESH_TOKEN
            every { loginServer } returns "https://login.salesforce.com"
        }
        val clientManagerSpy = boundClientManager(mockAccount, arrayOf(mockAccount))
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } returns Bundle()

        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(clientManagerSpy)

        assertNull(authTokenProvider.getNewAuthToken())
        verify(exactly = 1) {
            mockSDKManager.logout(mockAccount, any(), true, REFRESH_TOKEN_EXPIRED)
            mockAppContext.sendBroadcast(capture(broadcastIntentSlot))
        }
        assertEquals(ACCESS_TOKEN_REVOKE_INTENT, broadcastIntentSlot.captured.action)
        assertNull(broadcastIntentSlot.captured.getStringExtra(EXTRA_TOKEN_ERROR))
        assertNull(broadcastIntentSlot.captured.getStringExtra(EXTRA_TOKEN_ERROR_DESCRIPTION))
    }

    // region Concurrent Refresh Tests

    /*
        Concurrency / Refresh Token Rotation (RTR): when several providers that
        share one account all need to refresh at once (e.g. on resume), exactly
        one provider (the "winner") must hit the token endpoint. The others
        ("losers") must wait for and adopt the winner's fresh access token —
        never POST in parallel, never log the user out. Without app-global,
        per-account serialization a loser would POST an already-rotated refresh
        token, get invalid_grant, and log out.
     */
    @Test
    fun testGetNewAuthToken_ConcurrentBurst_SingleRefresh_NoLogout() {
        val tokenEndpointCalls = AtomicInteger(0)
        // Winner signals it is inside the network call (holding refreshing=true).
        val winnerInExecute = CountDownLatch(1)
        // Held until losers are confirmed parked; releasing lets the winner publish.
        val releaseWinner = CountDownLatch(1)

        every { HttpAccess.DEFAULT.okHttpClient } returns mockk<OkHttpClient> {
            every { newCall(any()) } returns mockk<Call> {
                every { execute() } answers {
                    tokenEndpointCalls.incrementAndGet()
                    winnerInExecute.countDown()
                    releaseWinner.await(5, TimeUnit.SECONDS)
                    successResponse(ROTATED_REFRESH_TOKEN)
                }
            }
        }

        val fixture = boundFixture()

        val providers = (0 until 4).map {
            ClientManager.AccMgrAuthTokenProvider(fixture.manager)
        }

        val results = arrayOfNulls<String>(providers.size)
        // Start the winner first and wait until it is actually inside execute(), so the
        // other three are guaranteed to become losers and park (no fixed sleep).
        val winnerThread = Thread { results[0] = providers[0].getNewAuthToken() }
        winnerThread.start()
        assertEquals(true, winnerInExecute.await(5, TimeUnit.SECONDS))

        val loserThreads = (1 until providers.size).map { i ->
            Thread { results[i] = providers[i].getNewAuthToken() }
        }
        loserThreads.forEach { it.start() }
        awaitThreadsParked(loserThreads, loserThreads.size)

        // Losers are parked; release the winner to publish its result.
        releaseWinner.countDown()
        (loserThreads + winnerThread).forEach { it.join(TimeUnit.SECONDS.toMillis(5)) }

        // Exactly one rotation hit the token endpoint.
        assertEquals(1, tokenEndpointCalls.get())
        // All four callers received the refreshed access token.
        results.forEach { assertEquals(REFRESHED_ACCESS_TOKEN, it) }
        // Only the winner updates the account; no logout for losers.
        verify(exactly = 0) { mockSDKManager.logout(any(), any(), any(), any()) }
        verify(exactly = 1) {
            mockUserAccountManager.updateAccount(fixture.account, any())
        }
    }

    /*
        Recheck-under-lock guardrail (the idle-provider scenario). A provider that did not take
        part in the resume burst still carries an OLD access + refresh token. It later makes a
        request, gets a 401, and calls getNewAuthToken(). By then another provider already
        refreshed and storage holds the NEW access + refresh token. The winner must NOT POST a
        (redundant, rotation-triggering) refresh — it must detect that storage advanced past its
        own tokens and ADOPT them.

        - Correct code: zero token-endpoint POSTs, returns the stored (new) access token, adopts
          the rotated refresh token, zero logout.
        - Without the guardrail: a needless POST would occur (asserted by postedTokens being empty).
     */
    @Test
    fun testGetNewAuthToken_StorageAdvanced_AdoptsWithoutRefreshing() {
        val newAccessToken = REFRESHED_ACCESS_TOKEN     // already in storage from another refresh
        val newRefreshToken = ROTATED_REFRESH_TOKEN     // already rotated into storage
        val postedTokens = mutableListOf<String?>()

        // Any token-endpoint call would be a bug; record posts so we can assert none happened.
        every { HttpAccess.DEFAULT.okHttpClient } returns mockk<OkHttpClient> {
            every { newCall(any()) } answers {
                postedTokens.add(postedRefreshToken(firstArg()))
                mockk<Call> { every { execute() } answers { successResponse(newRefreshToken) } }
            }
        }

        val fixture = boundFixture()
        // Construct while storage still has the old generation, then simulate another provider
        // atomically advancing the persisted access/refresh/instance tuple.
        val provider = ClientManager.AccMgrAuthTokenProvider(fixture.manager)
        fixture.liveUser.set(testUser(newAccessToken, newRefreshToken))

        // Adopts the stored access token without any network refresh.
        assertEquals(newAccessToken, provider.getNewAuthToken())
        assertTrue(provider.lastRefreshTime > 0)
        assertEquals(emptyList<String?>(), postedTokens)
        assertEquals(newRefreshToken, provider.refreshToken)
        verify(exactly = 0) { mockSDKManager.logout(any(), any(), any(), any()) }
        verify(exactly = 0) { mockUserAccountManager.updateAccount(any(), any()) }
    }

    @Test
    fun testGetNewAuthToken_ConcurrentBurst_WinnerFails_LosersReturnNullNoLogout() {
        val tokenEndpointCalls = AtomicInteger(0)
        val winnerInExecute = CountDownLatch(1)
        val releaseWinner = CountDownLatch(1)

        every { HttpAccess.DEFAULT.okHttpClient } returns mockk<OkHttpClient> {
            every { newCall(any()) } returns mockk<Call> {
                every { execute() } answers {
                    tokenEndpointCalls.incrementAndGet()
                    winnerInExecute.countDown()
                    releaseWinner.await(5, TimeUnit.SECONDS)
                    invalidGrantResponse()
                }
            }
        }

        val mockAccount = mockk<Account>(relaxed = true)
        val mockUser = mockk<UserAccount>(relaxed = true) {
            every { authToken } returns OLD_ACCESS_TOKEN
            every { refreshToken } returns REFRESH_TOKEN
            every { loginServer } returns "https://login.salesforce.com"
            every { userId } returns "userId"
            every { orgId } returns "orgId"
        }
        // Bound manager ensures the winner's terminal cleanup targets the exact account.
        val clientManagerSpy = boundClientManager(mockAccount, arrayOf(mockAccount))
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser

        val providers = (0 until 4).map {
            ClientManager.AccMgrAuthTokenProvider(clientManagerSpy)
        }
        val results = arrayOfNulls<String>(providers.size)

        val winnerThread = Thread { results[0] = providers[0].getNewAuthToken() }
        winnerThread.start()
        assertEquals(true, winnerInExecute.await(5, TimeUnit.SECONDS))

        val loserThreads = (1 until providers.size).map { i ->
            Thread { results[i] = providers[i].getNewAuthToken() }
        }
        loserThreads.forEach { it.start() }
        awaitThreadsParked(loserThreads, loserThreads.size)

        releaseWinner.countDown()
        (loserThreads + winnerThread).forEach { it.join(TimeUnit.SECONDS.toMillis(5)) }

        // Exactly one POST (the winner's). Losers never re-attempted.
        assertEquals(1, tokenEndpointCalls.get())
        // All callers got null (winner failed, losers adopt the failed result).
        results.forEach { assertNull(it) }
        // Only the winner may attempt logout; it does so exactly once.
        verify(exactly = 1) { mockSDKManager.logout(mockAccount, any(), true, REFRESH_TOKEN_EXPIRED) }
        // Only one broadcast total (the winner's revoke broadcast); losers do not broadcast.
        verify(exactly = 1) { mockAppContext.sendBroadcast(any()) }
    }

    /*
        Null instance URL must not strand losers. The winner refreshes successfully but the
        response carries no instance_url. A parked loser must still return a non-null token and a
        non-null getInstanceUrl() (falling back to its own constructor instance URL), so that
        RestClient.refreshAccessToken does not throw.
     */
    @Test
    fun testGetNewAuthToken_ConcurrentBurst_NullInstanceUrlLoser_KeepsOwnInstanceUrl() {
        val winnerInExecute = CountDownLatch(1)
        val releaseWinner = CountDownLatch(1)

        every { HttpAccess.DEFAULT.okHttpClient } returns mockk<OkHttpClient> {
            every { newCall(any()) } returns mockk<Call> {
                every { execute() } answers {
                    winnerInExecute.countDown()
                    releaseWinner.await(5, TimeUnit.SECONDS)
                    successResponse(refreshToken = null, instanceUrl = null)
                }
            }
        }

        val fixture = boundFixture(instanceUrl = "https://account.instance.url")

        val winner = ClientManager.AccMgrAuthTokenProvider(
            fixture.manager, "https://winner.instance.url",
        )
        val loser = ClientManager.AccMgrAuthTokenProvider(
            fixture.manager, "https://loser.instance.url",
        )

        val results = arrayOfNulls<String>(2)
        val winnerThread = Thread { results[0] = winner.getNewAuthToken() }
        winnerThread.start()
        assertEquals(true, winnerInExecute.await(5, TimeUnit.SECONDS))

        val loserThread = Thread { results[1] = loser.getNewAuthToken() }
        loserThread.start()
        awaitThreadsParked(listOf(loserThread), 1)

        releaseWinner.countDown()
        listOf(winnerThread, loserThread).forEach { it.join(TimeUnit.SECONDS.toMillis(5)) }

        // Loser received the refreshed token.
        assertEquals(REFRESHED_ACCESS_TOKEN, results[1])
        // Loser kept its own (non-null) instance URL since the winner published none.
        assertEquals("https://loser.instance.url", loser.instanceUrl)
        verify(exactly = 0) { mockSDKManager.logout(any(), any(), any(), any()) }
    }

    /*
        Interrupted loser. A loser interrupted while parked must return cleanly (here: null,
        because no result was published yet) and must NEVER fall through into the winner block
        (which would double-refresh / risk a stale POST). It also re-asserts the interrupt flag.
     */
    @Test
    fun testGetNewAuthToken_InterruptedLoser_ReturnsCleanlyWithoutWinnerBody() {
        val tokenEndpointCalls = AtomicInteger(0)
        val winnerInExecute = CountDownLatch(1)
        val releaseWinner = CountDownLatch(1)

        every { HttpAccess.DEFAULT.okHttpClient } returns mockk<OkHttpClient> {
            every { newCall(any()) } returns mockk<Call> {
                every { execute() } answers {
                    tokenEndpointCalls.incrementAndGet()
                    winnerInExecute.countDown()
                    releaseWinner.await(5, TimeUnit.SECONDS)
                    successResponse(ROTATED_REFRESH_TOKEN)
                }
            }
        }

        val mockAccount = mockk<Account>(relaxed = true)
        val mockUser = mockk<UserAccount>(relaxed = true) {
            every { authToken } returns OLD_ACCESS_TOKEN
            every { refreshToken } returns REFRESH_TOKEN
            every { loginServer } returns "https://login.salesforce.com"
            every { userId } returns "userId"
            every { orgId } returns "orgId"
        }
        val mockClientManager = boundClientManager(mockAccount, arrayOf(mockAccount))
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } returns Bundle()

        val winner = ClientManager.AccMgrAuthTokenProvider(mockClientManager)
        val loser = ClientManager.AccMgrAuthTokenProvider(mockClientManager)

        val winnerThread = Thread { winner.getNewAuthToken() }
        winnerThread.start()
        assertEquals(true, winnerInExecute.await(5, TimeUnit.SECONDS))

        val loserResult = arrayOfNulls<String>(1)
        val loserThrew = arrayOfNulls<Throwable>(1)
        val loserThread = Thread {
            try {
                loserResult[0] = loser.getNewAuthToken()
            } catch (t: Throwable) {
                loserThrew[0] = t
            }
        }
        loserThread.start()
        awaitThreadsParked(listOf(loserThread), 1)

        // Interrupt the parked loser; it must return cleanly without entering the winner body.
        loserThread.interrupt()
        loserThread.join(TimeUnit.SECONDS.toMillis(5))

        assertNull(loserThrew[0])
        assertNull(loserResult[0])
        // The loser did NOT perform its own refresh (winner is still the only POST so far).
        assertEquals(1, tokenEndpointCalls.get())

        // Let the winner finish cleanly.
        releaseWinner.countDown()
        winnerThread.join(TimeUnit.SECONDS.toMillis(5))
        assertEquals(1, tokenEndpointCalls.get())
        verify(exactly = 0) { mockSDKManager.logout(any(), any(), any(), any()) }
    }

    /*
        Fresh-arriver recency adopt. A provider that arrives just after a refresh cycle completed
        (refreshing==false) and whose own stale token differs from the freshly-published one adopts
        that recent result instead of issuing a redundant network refresh. Closes the consecutive-
        cycle race for fresh arrivers and avoids an RTR-rotating redundant POST.
     */
    @Test
    fun testGetNewAuthToken_FreshArriver_RecentPublish_AdoptsWithoutRefreshing() {
        val tokenEndpointCalls = AtomicInteger(0)
        every { HttpAccess.DEFAULT.okHttpClient } returns mockk<OkHttpClient> {
            every { newCall(any()) } returns mockk<Call> {
                every { execute() } answers {
                    tokenEndpointCalls.incrementAndGet()
                    successResponse(ROTATED_REFRESH_TOKEN)
                }
            }
        }

        val fixture = boundFixture()

        // Both providers capture the old generation before the winner advances live storage.
        val winner = ClientManager.AccMgrAuthTokenProvider(fixture.manager)
        val freshArriver = ClientManager.AccMgrAuthTokenProvider(fixture.manager)

        // First provider performs the real refresh and publishes the result into the shared state.
        assertEquals(REFRESHED_ACCESS_TOKEN, winner.getNewAuthToken())
        assertEquals(1, tokenEndpointCalls.get())

        // Fresh arriver still holding the OLD (now-401'd) access token. It differs from the recently
        // published token, so the recency window lets it adopt without a second POST.
        assertEquals(REFRESHED_ACCESS_TOKEN, freshArriver.getNewAuthToken())

        // No second network refresh occurred — the fresh arriver adopted the recent result.
        assertEquals(1, tokenEndpointCalls.get())
        verify(exactly = 0) { mockSDKManager.logout(any(), any(), any(), any()) }
    }

    /*
        Recency-adopt difference guard. A fresh arriver whose own last token EQUALS the recently
        published token (e.g. it was itself the recent winner and just got a 401 on that very token)
        must NOT re-adopt it — that would re-serve the dead token and 401 again. It must perform a
        real refresh. Two sequential refreshes by the same provider exercise this directly.
     */
    @Test
    fun testGetNewAuthToken_FreshArriver_SameTokenAsPublished_DiffGuardForcesRefresh() {
        val tokenEndpointCalls = AtomicInteger(0)
        // Each refresh rotates to a distinct refresh token and advances persisted storage so the
        // recheck-under-lock guardrail does not short-circuit the second (legitimately needed) POST.
        val firstRotated = ROTATED_REFRESH_TOKEN
        val secondRotated = "rotated-refresh-token-2"
        var persistedAuthToken = OLD_ACCESS_TOKEN
        var persistedRefreshToken = REFRESH_TOKEN

        every { HttpAccess.DEFAULT.okHttpClient } returns mockk<OkHttpClient> {
            every { newCall(any()) } returns mockk<Call> {
                every { execute() } answers {
                    val rt = if (tokenEndpointCalls.incrementAndGet() == 1) firstRotated else secondRotated
                    successResponse(rt)
                }
            }
        }

        val mockAccount = mockk<Account>(relaxed = true)
        val mockUser = mockk<UserAccount>(relaxed = true) {
            every { authToken } answers { persistedAuthToken }
            every { refreshToken } answers { persistedRefreshToken }
            every { loginServer } returns "https://login.salesforce.com"
            every { userId } returns "userId"
            every { orgId } returns "orgId"
        }
        val mockClientManager = boundClientManager(mockAccount, arrayOf(mockAccount))
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } answers {
            persistedAuthToken = secondArg<UserAccount>().authToken
            persistedRefreshToken = secondArg<UserAccount>().refreshToken
            Bundle()
        }

        val provider = ClientManager.AccMgrAuthTokenProvider(mockClientManager)

        // First refresh: real POST, publishes REFRESHED_ACCESS_TOKEN; provider's lastNewAuthToken
        // now equals the published token.
        assertEquals(REFRESHED_ACCESS_TOKEN, provider.getNewAuthToken())
        assertEquals(1, tokenEndpointCalls.get())

        // Second refresh immediately after (well within the recency window). The diff guard must
        // block recency-adopt because state.newAuthToken == this.lastNewAuthToken, forcing a real
        // POST rather than re-handing the just-401'd token.
        assertEquals(REFRESHED_ACCESS_TOKEN, provider.getNewAuthToken())
        assertEquals(2, tokenEndpointCalls.get())
        verify(exactly = 0) { mockSDKManager.logout(any(), any(), any(), any()) }
    }

    @Test
    fun testGetNewAuthToken_RemovedWaitingAccountCannotAdoptWinner() {
        val fixture = boundFixture()
        val broadcastReached = CountDownLatch(1)
        val releaseBroadcast = CountDownLatch(1)
        every { mockAppContext.sendBroadcast(any()) } answers {
            broadcastReached.countDown()
            releaseBroadcast.await(5, TimeUnit.SECONDS)
            Unit
        }
        every { HttpAccess.DEFAULT.okHttpClient } returns mockk {
            every { newCall(any()) } returns mockk<Call> {
                every { execute() } returns successResponse(ROTATED_REFRESH_TOKEN)
            }
        }
        val winner = ClientManager.AccMgrAuthTokenProvider(fixture.manager)
        val loser = ClientManager.AccMgrAuthTokenProvider(fixture.manager)
        val results = arrayOfNulls<String>(2)
        val winnerThread = Thread { results[0] = winner.getNewAuthToken() }
        winnerThread.start()
        assertEquals(true, broadcastReached.await(5, TimeUnit.SECONDS))
        val loserThread = Thread { results[1] = loser.getNewAuthToken() }
        loserThread.start()
        awaitThreadsParked(listOf(loserThread), 1)
        fixture.liveUser.set(null)

        releaseBroadcast.countDown()
        listOf(winnerThread, loserThread).forEach { it.join(TimeUnit.SECONDS.toMillis(5)) }

        assertEquals(REFRESHED_ACCESS_TOKEN, results[0])
        assertNull(results[1])
    }

    @Test
    fun testGetNewAuthToken_EqualTokensStillUpdateOnlyBoundAccount() {
        val accountA = mockk<Account>(relaxed = true)
        val accountB = mockk<Account>(relaxed = true)
        val userA = testUser(OLD_ACCESS_TOKEN, REFRESH_TOKEN, "user-a", "org-a")
        val userB = testUser(OLD_ACCESS_TOKEN, REFRESH_TOKEN, "user-b", "org-b")
        every { mockUserAccountManager.buildUserAccount(accountA) } returns userA
        every { mockUserAccountManager.buildUserAccount(accountB) } returns userB
        every {
            mockUserAccountManager.updateAccount(accountA, any())
        } returns Bundle()
        val managerA = boundClientManager(accountA, arrayOf(accountA, accountB))

        assertEquals(
            REFRESHED_ACCESS_TOKEN,
            ClientManager.AccMgrAuthTokenProvider(managerA).getNewAuthToken(),
        )

        verify(exactly = 1) { mockUserAccountManager.updateAccount(accountA, any()) }
        verify(exactly = 0) { mockUserAccountManager.updateAccount(accountB, any()) }
    }

    @Test
    fun testRoutingOverrideSurvivesAccountInstanceUpdate() {
        val fixture = boundFixture(instanceUrl = "https://old.instance.example")
        val broadcast = slot<Intent>()
        every { HttpAccess.DEFAULT.okHttpClient } returns mockk {
            every { newCall(any()) } returns mockk<Call> {
                every { execute() } returns successResponse(
                    ROTATED_REFRESH_TOKEN,
                    "https://new.instance.example",
                )
            }
        }
        val provider = ClientManager.AccMgrAuthTokenProvider(
            fixture.manager,
            "https://special.route.example",
        )

        assertEquals(REFRESHED_ACCESS_TOKEN, provider.getNewAuthToken())

        assertEquals("https://special.route.example", provider.instanceUrl)
        verify { mockAppContext.sendBroadcast(capture(broadcast)) }
        assertEquals(INSTANCE_URL_UPDATE_INTENT, broadcast.captured.action)
    }

    @Test
    @Suppress("DEPRECATION")
    fun testDeprecatedTokenSnapshotConstructorIgnoresSnapshots() {
        val userA = testUser()
        val userB = testUser(
            authToken = "b-access-token",
            refreshToken = "b-refresh-token",
            userId = "user-b",
            orgId = "org-b",
            instanceUrl = "https://b.instance.example",
        )
        val accountA = Account(userA.accountName, "test-account-type")
        val accountB = Account(userB.accountName, "test-account-type")
        val liveUserA = AtomicReference<UserAccount?>(userA)
        val liveUserB = AtomicReference<UserAccount?>(userB)
        val accountManager = mockk<AccountManager> {
            every { getAccountsByType(accountA.type) } returns arrayOf(accountA, accountB)
        }
        every { mockUserAccountManager.buildUserAccount(accountA) } answers { liveUserA.get() }
        every { mockUserAccountManager.buildUserAccount(accountB) } answers { liveUserB.get() }
        every {
            mockUserAccountManager.updateAccount(accountA, any())
        } answers {
            liveUserA.set(secondArg())
            Bundle()
        }
        val managerA = ClientManager(accountManager, accountA)

        val providerWithBSnapshots = ClientManager.AccMgrAuthTokenProvider(
            managerA,
            userB.instanceServer,
            userB.authToken,
            userB.refreshTokenForPersistence,
        )

        assertEquals("https://login.salesforce.com", providerWithBSnapshots.instanceUrl)
        assertEquals(REFRESH_TOKEN, providerWithBSnapshots.refreshToken)
        assertEquals(REFRESHED_ACCESS_TOKEN, providerWithBSnapshots.getNewAuthToken())

        val providerWithNullSnapshots = ClientManager.AccMgrAuthTokenProvider(
            managerA,
            null,
            null,
            null,
        )
        assertEquals("https://login.salesforce.com", providerWithNullSnapshots.instanceUrl)
        assertEquals(REFRESH_TOKEN, providerWithNullSnapshots.refreshToken)
        assertEquals("b-access-token", liveUserB.get()?.authToken)
        assertEquals("b-refresh-token", liveUserB.get()?.refreshTokenForPersistence)
        verify(exactly = 1) { mockOkHttpClient.newCall(any()) }
        verify(exactly = 1) {
            mockUserAccountManager.updateAccount(accountA, any())
        }
        verify(exactly = 0) {
            mockUserAccountManager.updateAccount(accountB, any())
            mockSDKManager.logout(accountB, any(), any(), any())
        }
    }

    @Test
    fun testProviderUsesExactAccountRefreshTokenSnapshot() {
        val user = mockk<UserAccount>(relaxed = true) {
            every { authToken } returns OLD_ACCESS_TOKEN
            every { refreshToken } returns "replacement-account-token"
            every { refreshTokenForPersistence } returns REFRESH_TOKEN
            every { instanceServer } returns "https://login.salesforce.com"
        }
        val manager = mockk<ClientManager>(relaxed = true) {
            every { getValidatedUser(any()) } returns user
        }

        val provider = ClientManager.AccMgrAuthTokenProvider(manager)

        assertEquals(REFRESH_TOKEN, provider.refreshToken)
    }

    // endregion

    // region Concurrency test helpers

    private data class BoundFixture(
        val account: Account,
        val liveUser: AtomicReference<UserAccount?>,
        val manager: ClientManager,
    )

    private fun boundFixture(
        instanceUrl: String = "https://login.salesforce.com",
    ): BoundFixture {
        val account = mockk<Account>(relaxed = true)
        val liveUser = AtomicReference<UserAccount?>(testUser(instanceUrl = instanceUrl))
        every { mockUserAccountManager.buildUserAccount(account) } answers { liveUser.get() }
        every {
            mockUserAccountManager.updateAccount(account, any())
        } answers {
            liveUser.set(secondArg())
            Bundle()
        }
        return BoundFixture(
            account,
            liveUser,
            boundClientManager(account, arrayOf(account)),
        )
    }

    private fun testUser(
        authToken: String? = OLD_ACCESS_TOKEN,
        refreshToken: String? = REFRESH_TOKEN,
        userId: String = "userId",
        orgId: String = "orgId",
        instanceUrl: String? = "https://login.salesforce.com",
    ): UserAccount = UserAccountBuilder.getInstance()
        .accountName("account-$userId")
        .username("user@example.com")
        .authToken(authToken)
        .refreshToken(refreshToken)
        .instanceServer(instanceUrl)
        .loginServer("https://login.salesforce.com")
        .idUrl("https://login.salesforce.com/id/$orgId/$userId")
        .clientId("client-id")
        .orgId(orgId)
        .userId(userId)
        .build()

    private fun boundClientManager(
        boundAccount: Account,
        accounts: Array<Account>,
    ): ClientManager = mockk(relaxed = true) {
        every { getAccount() } returns boundAccount
        every { getValidatedUser(any()) } answers {
            val storedUser = mockUserAccountManager.buildUserAccount(boundAccount)
            val persistedRefreshToken = storedUser?.refreshTokenForPersistence
                ?.takeUnless(String::isEmpty)
                ?: storedUser?.refreshToken
            val user = if (storedUser != null
                && persistedRefreshToken != storedUser.refreshTokenForPersistence) {
                UserAccountBuilder.getInstance()
                    .populateFromUserAccount(storedUser)
                    .refreshToken(persistedRefreshToken)
                    .build()
            } else {
                storedUser
            }
            if (firstArg<Boolean>() && (user?.refreshTokenForPersistence == null
                        || user.loginServer == null
                        || user.clientIdForRefresh == null)) {
                null
            } else {
                user
            }
        }
        every { getBoundAccountCount() } returns accounts.size
    }

    /** Reads the refresh_token value out of an OkHttp token-endpoint request's form body. */
    private fun postedRefreshToken(request: Request): String? {
        return postedFormValue(request, "refresh_token")
    }

    private fun postedFormValue(request: Request, key: String): String? {
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        return buffer.readUtf8().split("&")
            .firstOrNull { it.startsWith("$key=") }
            ?.substringAfter("$key=")
            ?.let { URLDecoder.decode(it, "UTF-8") }
    }

    /** Deterministically blocks until [count] threads are parked in WAITING/TIMED_WAITING. */
    private fun awaitThreadsParked(threads: List<Thread>, count: Int) {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5)
        while (System.currentTimeMillis() < deadline) {
            val parked = threads.count {
                it.state == Thread.State.WAITING || it.state == Thread.State.TIMED_WAITING
            }
            if (parked >= count) return
            Thread.sleep(50)
        }
        throw AssertionError("Timed out waiting for $count threads to park; states=${threads.map { it.state }}")
    }

    private fun successResponse(refreshToken: String?, instanceUrl: String? = "https://login.salesforce.com"): Response {
        val instanceLine = if (instanceUrl != null) "\"instance_url\": \"$instanceUrl\"," else ""
        val refreshLine = if (refreshToken != null) "\"refresh_token\": \"$refreshToken\"," else ""
        val responseBody = """
                {
                    "access_token": "$REFRESHED_ACCESS_TOKEN",
                    $refreshLine
                    $instanceLine
                    "id": "https://login.salesforce.com/id/orgId/userId",
                    "token_type": "Bearer",
                    "issued_at": "1234567890",
                    "signature": "mock-signature"
                }
            """.trimIndent().toResponseBody("application/json; charset=utf-8".toMediaType())
        return mockk(relaxed = true) {
            every { isSuccessful } returns true
            every { close() } just runs
            every { body } returns responseBody
        }
    }

    private fun invalidGrantResponse(): Response {
        val errorBody = """
            {"error": "invalid_grant", "error_description": "expired access/refresh token"}
        """.trimIndent().toResponseBody("application/json; charset=utf-8".toMediaType())
        return mockk(relaxed = true) {
            every { isSuccessful } returns false
            every { code } returns 400
            every { body } returns errorBody
        }
    }

    // endregion
}
