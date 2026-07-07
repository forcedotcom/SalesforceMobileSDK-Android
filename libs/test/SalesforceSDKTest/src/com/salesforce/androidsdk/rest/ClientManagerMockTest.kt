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
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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
            every { registerUsedAppFeature(any(), any()) } just runs
            every { unregisterUsedAppFeature(any()) } returns true
            every { userAccountManager } returns mockUserAccountManager
            every { deviceId } returns "test-device-id-123"
            every { additionalOauthKeys } returns emptyList()
            every { useHybridAuthentication } returns true
            every { appAttestationClient } returns null
            every { appContext } returns mockAppContext
            every { isDevSupportEnabled() } returns true
            every { isUseDPoP() } returns false
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
        every { HttpAccess.DEFAULT.okHttpClient } returns mockk<OkHttpClient> {
            every { newCall(any()) } returns mockk<Call> {
                every { execute() } returns refreshResponse
            }
        }

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
        val mockClientManager = mockk<ClientManager>(relaxed = true) {
            every { accounts } returns arrayOf(mockAccount)
        }
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } answers {
            persistedAuthToken = secondArg<UserAccount>().authToken
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

        val mockAccount = mockk<Account>(relaxed = true)
        val mockUser = mockk<UserAccount>(relaxed = true) {
            every { authToken } returns OLD_ACCESS_TOKEN
            every { refreshToken } returns REFRESH_TOKEN
            every { loginServer } returns "https://login.salesforce.com"
            every { userId } returns "userId"
            every { orgId } returns "orgId"
        }
        val mockClientManager = mockk<ClientManager>(relaxed = true) {
            every { accounts } returns arrayOf(mockAccount)
        }
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } returns mockk()

        val providers = (0 until 4).map {
            ClientManager.AccMgrAuthTokenProvider(
                mockClientManager,
                "https://login.salesforce.com",
                OLD_ACCESS_TOKEN,
                REFRESH_TOKEN,
            )
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
        verify(exactly = 1) { mockUserAccountManager.updateAccount(mockAccount, any()) }
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

        val mockAccount = mockk<Account>(relaxed = true)
        // Storage already holds the NEW tokens (a concurrent/earlier provider refreshed).
        val mockUser = mockk<UserAccount>(relaxed = true) {
            every { authToken } returns newAccessToken
            every { refreshToken } returns newRefreshToken
            every { instanceServer } returns "https://login.salesforce.com"
            every { loginServer } returns "https://login.salesforce.com"
            every { userId } returns "userId"
            every { orgId } returns "orgId"
        }
        val mockClientManager = mockk<ClientManager>(relaxed = true) {
            every { accounts } returns arrayOf(mockAccount)
        }
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser

        // Provider holds the OLD access token but the current (already-rotated) refresh token
        // from storage — it was idle during the burst, so its access token is stale while its
        // refresh token already matches what the winner persisted. The account match succeeds on
        // that refresh token, and the recheck-under-lock then adopts the stored access token
        // instead of POSTing.
        val provider = ClientManager.AccMgrAuthTokenProvider(
            mockClientManager,
            "https://login.salesforce.com",
            OLD_ACCESS_TOKEN,
            newRefreshToken,
        )

        // Adopts the stored access token without any network refresh.
        assertEquals(newAccessToken, provider.getNewAuthToken())
        assertEquals(emptyList<String?>(), postedTokens)
        assertEquals(newRefreshToken, provider.refreshToken)
        verify(exactly = 0) { mockSDKManager.logout(any(), any(), any(), any()) }
        verify(exactly = 0) { mockUserAccountManager.updateAccount(any(), any()) }
    }

    /*
        Winner fails: when the winner's refresh returns invalid_grant, the parked losers must each
        return null WITHOUT logging out and WITHOUT broadcasting. Only the winner may attempt
        logout/broadcast.
     */
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
        // Real (spied) client manager so revokedTokenShouldLogout=true logout path is reachable.
        val clientManagerSpy = spyk(clientManager)
        every { clientManagerSpy.accounts } returns arrayOf(mockAccount)
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser

        val providers = (0 until 4).map {
            ClientManager.AccMgrAuthTokenProvider(
                clientManagerSpy,
                "https://login.salesforce.com",
                OLD_ACCESS_TOKEN,
                REFRESH_TOKEN,
            )
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

        val mockAccount = mockk<Account>(relaxed = true)
        val mockUser = mockk<UserAccount>(relaxed = true) {
            every { authToken } returns OLD_ACCESS_TOKEN
            every { refreshToken } returns REFRESH_TOKEN
            every { loginServer } returns "https://login.salesforce.com"
            every { instanceServer } returns null
            every { userId } returns "userId"
            every { orgId } returns "orgId"
        }
        val mockClientManager = mockk<ClientManager>(relaxed = true) {
            every { accounts } returns arrayOf(mockAccount)
        }
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } returns mockk()

        val winner = ClientManager.AccMgrAuthTokenProvider(
            mockClientManager, "https://winner.instance.url", OLD_ACCESS_TOKEN, REFRESH_TOKEN,
        )
        val loser = ClientManager.AccMgrAuthTokenProvider(
            mockClientManager, "https://loser.instance.url", OLD_ACCESS_TOKEN, REFRESH_TOKEN,
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
        val mockClientManager = mockk<ClientManager>(relaxed = true) {
            every { accounts } returns arrayOf(mockAccount)
        }
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } returns mockk()

        val winner = ClientManager.AccMgrAuthTokenProvider(
            mockClientManager, "https://login.salesforce.com", OLD_ACCESS_TOKEN, REFRESH_TOKEN,
        )
        val loser = ClientManager.AccMgrAuthTokenProvider(
            mockClientManager, "https://login.salesforce.com", OLD_ACCESS_TOKEN, REFRESH_TOKEN,
        )

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

        val mockAccount = mockk<Account>(relaxed = true)
        val mockUser = mockk<UserAccount>(relaxed = true) {
            every { authToken } returns OLD_ACCESS_TOKEN
            every { refreshToken } returns REFRESH_TOKEN
            every { loginServer } returns "https://login.salesforce.com"
            every { userId } returns "userId"
            every { orgId } returns "orgId"
        }
        val mockClientManager = mockk<ClientManager>(relaxed = true) {
            every { accounts } returns arrayOf(mockAccount)
        }
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } returns mockk()

        // First provider performs the real refresh and publishes the result into the shared state.
        val winner = ClientManager.AccMgrAuthTokenProvider(
            mockClientManager, "https://login.salesforce.com", OLD_ACCESS_TOKEN, REFRESH_TOKEN,
        )
        assertEquals(REFRESHED_ACCESS_TOKEN, winner.getNewAuthToken())
        assertEquals(1, tokenEndpointCalls.get())

        // Fresh arriver still holding the OLD (now-401'd) access token. It differs from the recently
        // published token, so the recency window lets it adopt without a second POST.
        val freshArriver = ClientManager.AccMgrAuthTokenProvider(
            mockClientManager, "https://login.salesforce.com", OLD_ACCESS_TOKEN, REFRESH_TOKEN,
        )
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
        val mockClientManager = mockk<ClientManager>(relaxed = true) {
            every { accounts } returns arrayOf(mockAccount)
        }
        every { mockUserAccountManager.currentUser } returns mockUser
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser
        every { mockUserAccountManager.updateAccount(mockAccount, any()) } answers {
            persistedAuthToken = secondArg<UserAccount>().authToken
            persistedRefreshToken = secondArg<UserAccount>().refreshToken
            mockk()
        }

        val provider = ClientManager.AccMgrAuthTokenProvider(
            mockClientManager, "https://login.salesforce.com", OLD_ACCESS_TOKEN, REFRESH_TOKEN,
        )

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

    // endregion

    // region Concurrency test helpers

    /** Reads the refresh_token value out of an OkHttp token-endpoint request's form body. */
    private fun postedRefreshToken(request: Request): String? {
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        // Form body looks like: grant_type=...&client_id=...&refresh_token=VALUE&format=json
        return buffer.readUtf8().split("&")
            .firstOrNull { it.startsWith("refresh_token=") }
            ?.substringAfter("refresh_token=")
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

