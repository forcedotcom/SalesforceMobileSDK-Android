package com.salesforce.androidsdk.rest

import android.accounts.Account
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.UserAccount
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
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private const val OLD_ACCESS_TOKEN = "old-token"
private const val REFRESHED_ACCESS_TOKEN = "refreshed-auth-token"
private const val REFRESH_TOKEN = "refresh-token"
private const val ROTATED_REFRESH_TOKEN = "rotated-refresh-token"

@SmallTest
class ClientManagerMockTest {
    private lateinit var clientManager: ClientManager
    private lateinit var mockSDKManager: SalesforceSDKManager
    private lateinit var mockAppContext: Context
    private lateinit var mockUserAccountManager: UserAccountManager
    private lateinit var refreshResponse: Response

    @Before
    fun setUp() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        clientManager = ClientManager(targetContext, UserAccountManagerTest.TEST_ACCOUNT_TYPE, true)
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
            every { unregisterUsedAppFeature(any()) } returns true
            every { userAccountManager } returns mockUserAccountManager
            every { deviceId } returns "test-device-id-123"
            every { additionalOauthKeys } returns emptyList()
            every { useHybridAuthentication } returns true
            every { appAttestationClient } returns null
            every { appContext } returns mockAppContext
            every { isDevSupportEnabled() } returns true
        }
        every { SalesforceSDKManager.getInstance() } returns mockSDKManager
        mockkStatic(UserAccountManager::class)
        every { UserAccountManager.getInstance() } returns mockUserAccountManager
        mockkStatic(EventBuilderHelper::class)
        every { EventBuilderHelper.createAndStoreEvent(any(), any(), any(), any()) } just runs

        val responseBody = """
                {
                    "access_token": $REFRESHED_ACCESS_TOKEN,
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
        every { HttpAccess.DEFAULT.okHttpClient } returns mockk<OkHttpClient> {
            every { newCall(any()) } returns mockk<Call> {
                every { execute() } returns refreshResponse
            }
        }
    }

    @After
    fun tearDown() {
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
            every { loginServer } returns "https://login.salesforce.com"
        }
        val mockClientManager = mockk<ClientManager>(relaxed = true) {
            every { accounts } returns arrayOf(mockAccount)
        }
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } returns mockk()
        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(
            mockClientManager,
            "https://login.salesforce.com",
            OLD_ACCESS_TOKEN,
            REFRESH_TOKEN,
        )

        val result = authTokenProvider.getNewAuthToken()
        assertEquals(REFRESHED_ACCESS_TOKEN, result)

        verify(exactly = 0) {
            mockSDKManager.logout(any(), any(), any(), any())
        }
        verify(exactly = 1) {
            mockClientManager.invalidateToken(OLD_ACCESS_TOKEN)
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
        val mockClientManager = mockk<ClientManager>(relaxed = true) {
            every { accounts } returns arrayOf(mockAccount)
        }
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } returns mockk()
        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(
            mockClientManager,
            "https://not.login.salesforce.com",
            OLD_ACCESS_TOKEN,
            REFRESH_TOKEN,
        )

        val result = authTokenProvider.getNewAuthToken()
        assertEquals(REFRESHED_ACCESS_TOKEN, result)

        verify(exactly = 0) {
            mockSDKManager.logout(any(), any(), any(), any())
        }
        verify(exactly = 1) {
            mockClientManager.invalidateToken(OLD_ACCESS_TOKEN)
            mockUserAccountManager.updateAccount(mockAccount, capture(userSlot))
            mockAppContext.sendBroadcast(capture(broadcastIntentSlot))
        }
        assertEquals(REFRESHED_ACCESS_TOKEN, userSlot.captured.authToken)
        assertEquals(INSTANCE_URL_UPDATE_INTENT, broadcastIntentSlot.captured.action)
    }

    @Test
    fun testGetNewAuthToken_NoAccounts() {
        val mockClientManager = mockk<ClientManager>(relaxed = true) {
            every { accounts } returns emptyArray<Account>()
        }
        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(
            mockClientManager,
            "",
            OLD_ACCESS_TOKEN,
            REFRESH_TOKEN,
        )

        assertNull(authTokenProvider.getNewAuthToken())
        verify(exactly = 0) {
            mockSDKManager.logout(any(), any(), any(), any())
            mockClientManager.invalidateToken(any())
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
        val mockClientManager = mockk<ClientManager>(relaxed = true) {
            every { accounts } returns arrayOf(mockAccount)
        }
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(
            mockClientManager,
            "",
            OLD_ACCESS_TOKEN,
            REFRESH_TOKEN,
        )

        assertNull(authTokenProvider.getNewAuthToken())
        verify(exactly = 0) {
            mockSDKManager.logout(any(), any(), any(), any())
            mockClientManager.invalidateToken(any())
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
        val mockClientManager = mockk<ClientManager>(relaxed = true) {
            every { accounts } returns arrayOf(mockAccount)
        }
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(
            mockClientManager,
            "",
            null,
            REFRESH_TOKEN,
        )

        assertNull(authTokenProvider.getNewAuthToken())
        verify(exactly = 0) {
            mockSDKManager.logout(any(), any(), any(), any())
            mockClientManager.invalidateToken(any())
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
        val mockClientManager = mockk<ClientManager>(relaxed = true) {
            every { accounts } returns arrayOf(mockAccount, mockAccount2)
        }
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount2) } returns mockUser2
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } returns mockk()
        every { mockUserAccountManager.updateAccount(mockAccount2, any()) } returns mockk()
        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(
            mockClientManager,
            "https://login.salesforce.com",
            OLD_ACCESS_TOKEN,
            REFRESH_TOKEN,
        )

        assertEquals(REFRESHED_ACCESS_TOKEN, authTokenProvider.getNewAuthToken())
        verify(exactly = 0) {
            mockClientManager.invalidateToken(user2Token)
            mockSDKManager.logout(any(), any(), any(), any())
            mockUserAccountManager.updateAccount(mockAccount2, any())
        }
        verify(exactly = 1) {
            mockClientManager.invalidateToken(OLD_ACCESS_TOKEN)
            mockUserAccountManager.updateAccount(mockAccount, capture(userSlot))
        }
        assertEquals(REFRESHED_ACCESS_TOKEN, userSlot.captured.authToken)
    }

    @Test
    fun testGetNewAuthToken_Revoked() {
        every { HttpAccess.DEFAULT.okHttpClient } returns mockk<OkHttpClient> {
            every { newCall(any()) } returns mockk<Call> {
                every { execute() } returns mockk<Response>(relaxed = true) {
                    every { isSuccessful } returns false
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

        // Use the real clientManager instead of a full mock because revokedTokenShouldLogout is private.
        val clientManagerSpy = spyk(clientManager)
        every { clientManagerSpy.accounts } returns arrayOf(mockAccount)
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } returns mockk()
        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(
            clientManagerSpy,
            "https://login.salesforce.com",
            OLD_ACCESS_TOKEN,
            REFRESH_TOKEN,
        )

        assertNull(authTokenProvider.getNewAuthToken())
        verify(exactly = 0) {
            mockUserAccountManager.updateAccount(any(), any())
        }
        verify(exactly = 1) {
            clientManagerSpy.invalidateToken(OLD_ACCESS_TOKEN)
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
        val mockClientManager = mockk<ClientManager>(relaxed = true) {
            every { accounts } returns arrayOf(mockAccount)
        }
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } returns mockk()

        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(
            mockClientManager,
            "https://login.salesforce.com",
            OLD_ACCESS_TOKEN,
            REFRESH_TOKEN,
        )

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
        // The persisted account's refresh token follows whatever updateAccount
        // was last called with (i.e., the most recent rotated value).
        var persistedRefreshToken = REFRESH_TOKEN
        val mockUser = mockk<UserAccount>(relaxed = true) {
            every { authToken } returns OLD_ACCESS_TOKEN
            every { refreshToken } answers { persistedRefreshToken }
            every { loginServer } returns "https://login.salesforce.com"
        }
        val mockClientManager = mockk<ClientManager>(relaxed = true) {
            every { accounts } returns arrayOf(mockAccount)
        }
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } answers {
            persistedRefreshToken = secondArg<UserAccount>().refreshToken
            mockk()
        }

        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(
            mockClientManager,
            "https://login.salesforce.com",
            OLD_ACCESS_TOKEN,
            REFRESH_TOKEN,
        )

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
        val mockClientManager = mockk<ClientManager>(relaxed = true) {
            every { accounts } returns arrayOf(mockAccount, mockAccount2)
        }
        // The account that we are not refreshing for is the current account.
        every { mockUserAccountManager.currentUser } returns mockUser2
        every { mockUserAccountManager.currentAccount } returns mockAccount2
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount2) } returns mockUser2
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } returns mockk()
        every { mockUserAccountManager.updateAccount(mockAccount2, any()) } returns mockk()
        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(
            mockClientManager,
            "https://login.salesforce.com",
            OLD_ACCESS_TOKEN,
            REFRESH_TOKEN,
        )

        assertEquals(REFRESHED_ACCESS_TOKEN, authTokenProvider.getNewAuthToken())
        verify(exactly = 0) {
            mockClientManager.invalidateToken(user2Token)
            mockSDKManager.logout(any(), any(), any(), any())
            mockUserAccountManager.updateAccount(mockAccount2, any())
        }
        verify(exactly = 1) {
            mockClientManager.invalidateToken(OLD_ACCESS_TOKEN)
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
                every { execute() } returns mockk<Response>(relaxed = true) {
                    every { isSuccessful } returns false
                }
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
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } returns mockk()
        every { mockUserAccountManager.updateAccount(mockAccount2, any()) } returns mockk()
        // Use the real clientManager instead of a full mock because revokedTokenShouldLogout is private.
        val clientManagerSpy = spyk(clientManager)
        every { clientManagerSpy.accounts } returns arrayOf(mockAccount, mockAccount2)
        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(
            clientManagerSpy,
            "https://login.salesforce.com",
            OLD_ACCESS_TOKEN,
            REFRESH_TOKEN,
        )

        assertNull(authTokenProvider.getNewAuthToken())
        verify(exactly = 0) {
            clientManagerSpy.invalidateToken(user2Token)
            mockUserAccountManager.updateAccount(any(), any())
            mockSDKManager.logout(mockAccount2, any(), any(), any())
            mockSDKManager.logout(null, any(), any(), any())
            mockUserAccountManager.updateAccount(mockAccount2, any())
        }

        verify(exactly = 1) {
            clientManagerSpy.invalidateToken(OLD_ACCESS_TOKEN)
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
        revokedTokenShouldLogout: Boolean = true,
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
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val cm = ClientManager(targetContext, UserAccountManagerTest.TEST_ACCOUNT_TYPE, revokedTokenShouldLogout)
        val clientManagerSpy = spyk(cm)
        every { clientManagerSpy.accounts } returns arrayOf(mockAccount)
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser

        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(
            clientManagerSpy,
            "https://login.salesforce.com",
            OLD_ACCESS_TOKEN,
            REFRESH_TOKEN,
        )
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
        val clientManagerSpy = spyk(clientManager)
        every { clientManagerSpy.accounts } returns arrayOf(mockAccount)
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser

        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(
            clientManagerSpy,
            "https://login.salesforce.com",
            OLD_ACCESS_TOKEN,
            REFRESH_TOKEN,
        )

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
    fun testGetNewAuthToken_ClientBlockedRetry_SubsequentCallSkipsInvalidateToken() {
        val result = setupTokenErrorScenario("client_blocked_retry", "Attestation verification pending")
        val clientManagerSpy = spyk(clientManager)
        every { clientManagerSpy.accounts } returns arrayOf(result.mockAccount)

        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(
            clientManagerSpy,
            "https://login.salesforce.com",
            OLD_ACCESS_TOKEN,
            REFRESH_TOKEN,
        )

        // First call: client_blocked_retry clears lastNewAuthToken to null.
        assertNull(authTokenProvider.getNewAuthToken())
        verify(exactly = 1) { clientManagerSpy.invalidateToken(OLD_ACCESS_TOKEN) }

        // Set up a second error response for the second call.
        val errorBody2 = """
            {"error": "client_blocked_retry", "error_description": "Still pending"}
        """.trimIndent().toResponseBody("application/json; charset=utf-8".toMediaType())
        every { HttpAccess.DEFAULT.okHttpClient } returns mockk<OkHttpClient> {
            every { newCall(any()) } returns mockk<Call> {
                every { execute() } returns mockk<Response>(relaxed = true) {
                    every { isSuccessful } returns false
                    every { code } returns 400
                    every { body } returns errorBody2
                }
            }
        }

        // Second call: lastNewAuthToken is null, so invalidateToken should be skipped.
        assertNull(authTokenProvider.getNewAuthToken())
        verify(exactly = 0) { mockSDKManager.logout(any(), any(), any(), any()) }
        // invalidateToken still only called once total (from first call when token was non-null).
        verify(exactly = 1) { clientManagerSpy.invalidateToken(any()) }
    }

    @Test
    fun testGetNewAuthToken_TerminalError_RevokedTokenShouldNotLogout_SkipsLogout() {
        val result = setupTokenErrorScenario(
            "client_blocked", "Device failed integrity check",
            revokedTokenShouldLogout = false,
        )

        assertNull(result.authTokenProvider.getNewAuthToken())
        verify(exactly = 0) { mockSDKManager.logout(any(), any(), any(), any()) }
        verify(exactly = 1) { mockAppContext.sendBroadcast(capture(result.broadcastIntentSlot)) }
        assertEquals(ACCESS_TOKEN_REVOKE_INTENT, result.broadcastIntentSlot.captured.action)
        assertEquals("client_blocked", result.broadcastIntentSlot.captured.getStringExtra(EXTRA_TOKEN_ERROR))
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
        val mockClientManager = mockk<ClientManager>(relaxed = true) {
            every { accounts } returns arrayOf(mockAccount)
        }
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } returns mockk()

        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(
            mockClientManager,
            null,
            OLD_ACCESS_TOKEN,
            REFRESH_TOKEN,
        )

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
        val clientManagerSpy = spyk(clientManager)
        every { clientManagerSpy.accounts } returns arrayOf(mockAccount)
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } returns mockk()

        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(
            clientManagerSpy,
            "https://login.salesforce.com",
            OLD_ACCESS_TOKEN,
            REFRESH_TOKEN,
        )

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
    fun testGetNewAuthToken_NullUserAccount_LogsOut() {
        val broadcastIntentSlot = slot<Intent>()
        val mockAccount = mockk<Account>(relaxed = true)
        val mockUser = mockk<UserAccount>(relaxed = true) {
            every { authToken } returns OLD_ACCESS_TOKEN
            every { refreshToken } returns REFRESH_TOKEN
            every { loginServer } returns "https://login.salesforce.com"
        }
        val clientManagerSpy = spyk(clientManager)
        every { clientManagerSpy.accounts } returns arrayOf(mockAccount)
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser

        val authTokenProvider = spyk(
            ClientManager.AccMgrAuthTokenProvider(
                clientManagerSpy,
                "https://login.salesforce.com",
                OLD_ACCESS_TOKEN,
                REFRESH_TOKEN,
            )
        )
        every { authTokenProvider["refreshStaleToken"](any<Account>()) } returns null

        assertNull(authTokenProvider.getNewAuthToken())
        verify(exactly = 1) {
            mockSDKManager.logout(mockAccount, any(), true, REFRESH_TOKEN_EXPIRED)
            mockAppContext.sendBroadcast(capture(broadcastIntentSlot))
        }
        assertEquals(ACCESS_TOKEN_REVOKE_INTENT, broadcastIntentSlot.captured.action)
        assertNull(broadcastIntentSlot.captured.getStringExtra(EXTRA_TOKEN_ERROR))
    }
}

