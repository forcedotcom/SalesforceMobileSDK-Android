package com.salesforce.androidsdk.rest

import android.accounts.Account
import android.content.Context
import android.content.Intent
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.accounts.UserAccountManagerTest
import com.salesforce.androidsdk.analytics.EventBuilderHelper
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.auth.OAuth2
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
import org.junit.Assert
import org.junit.Before
import org.junit.Test

private const val OLD_ACCESS_TOKEN = "old-token"
private const val REFRESHED_ACCESS_TOKEN = "refreshed-auth-token"
private const val REFRESH_TOKEN = "refresh-token"

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
            every { appContext } returns mockAppContext
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
        Assert.assertEquals(REFRESHED_ACCESS_TOKEN, result)

        verify(exactly = 0) {
            mockSDKManager.logout(any(), any(), any(), any())
        }
        verify(exactly = 1) {
            mockClientManager.invalidateToken(OLD_ACCESS_TOKEN)
            mockUserAccountManager.updateAccount(mockAccount, capture(userSlot))
            mockAppContext.sendBroadcast(capture(broadcastIntentSlot))
        }
        Assert.assertEquals(REFRESHED_ACCESS_TOKEN, userSlot.captured.authToken)
        Assert.assertEquals(ClientManager.ACCESS_TOKEN_REFRESH_INTENT, broadcastIntentSlot.captured.action)
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
        Assert.assertEquals(REFRESHED_ACCESS_TOKEN, result)

        verify(exactly = 0) {
            mockSDKManager.logout(any(), any(), any(), any())
        }
        verify(exactly = 1) {
            mockClientManager.invalidateToken(OLD_ACCESS_TOKEN)
            mockUserAccountManager.updateAccount(mockAccount, capture(userSlot))
            mockAppContext.sendBroadcast(capture(broadcastIntentSlot))
        }
        Assert.assertEquals(REFRESHED_ACCESS_TOKEN, userSlot.captured.authToken)
        Assert.assertEquals(ClientManager.INSTANCE_URL_UPDATE_INTENT, broadcastIntentSlot.captured.action)
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

        Assert.assertNull(authTokenProvider.getNewAuthToken())
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

        Assert.assertNull(authTokenProvider.getNewAuthToken())
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

        Assert.assertNull(authTokenProvider.getNewAuthToken())
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

        Assert.assertEquals(REFRESHED_ACCESS_TOKEN, authTokenProvider.getNewAuthToken())
        verify(exactly = 0) {
            mockClientManager.invalidateToken(user2Token)
            mockSDKManager.logout(any(), any(), any(), any())
            mockUserAccountManager.updateAccount(mockAccount2, any())
        }
        verify(exactly = 1) {
            mockClientManager.invalidateToken(OLD_ACCESS_TOKEN)
            mockUserAccountManager.updateAccount(mockAccount, capture(userSlot))
        }
        Assert.assertEquals(REFRESHED_ACCESS_TOKEN, userSlot.captured.authToken)
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
        val accountSlot = slot<Account>()
        val reasonSlot = slot<OAuth2.LogoutReason>()
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

        Assert.assertNull(authTokenProvider.getNewAuthToken())
        verify(exactly = 0) {
            mockUserAccountManager.updateAccount(any(), any())
        }
        verify(exactly = 1) {
            clientManagerSpy.invalidateToken(OLD_ACCESS_TOKEN)
            mockSDKManager.logout(capture(accountSlot), any(), any(), capture(reasonSlot))
            mockAppContext.sendBroadcast(capture(broadcastIntentSlot))
        }
        Assert.assertEquals(mockAccount, accountSlot.captured)
        Assert.assertEquals(OAuth2.LogoutReason.REFRESH_TOKEN_EXPIRED, reasonSlot.captured)
        Assert.assertEquals(ClientManager.ACCESS_TOKEN_REVOKE_INTENT, broadcastIntentSlot.captured.action)
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

        Assert.assertEquals(REFRESHED_ACCESS_TOKEN, authTokenProvider.getNewAuthToken())
        verify(exactly = 0) {
            mockClientManager.invalidateToken(user2Token)
            mockSDKManager.logout(any(), any(), any(), any())
            mockUserAccountManager.updateAccount(mockAccount2, any())
        }
        verify(exactly = 1) {
            mockClientManager.invalidateToken(OLD_ACCESS_TOKEN)
            mockUserAccountManager.updateAccount(mockAccount, capture(userSlot))
        }
        Assert.assertEquals(REFRESHED_ACCESS_TOKEN, userSlot.captured.authToken)
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
        val accountSlot = slot<Account>()
        val reasonSlot = slot<OAuth2.LogoutReason>()
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
        // Use the real clientManager instead of a full mock because revokedTokenShouldLogout is private.
        val clientManagerSpy = spyk(clientManager)
        every { clientManagerSpy.accounts } returns arrayOf(mockAccount)
        val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(
            clientManagerSpy,
            "https://login.salesforce.com",
            OLD_ACCESS_TOKEN,
            REFRESH_TOKEN,
        )

        Assert.assertNull(authTokenProvider.getNewAuthToken())
        verify(exactly = 0) {
            mockClientManager.invalidateToken(user2Token)
            mockUserAccountManager.updateAccount(any(), any())
            mockSDKManager.logout(mockAccount2, any(), any(), any())
            mockSDKManager.logout(null, any(), any(), any())
            mockUserAccountManager.updateAccount(mockAccount2, any())
        }

        verify(exactly = 1) {
            clientManagerSpy.invalidateToken(OLD_ACCESS_TOKEN)
            mockSDKManager.logout(capture(accountSlot), any(), any(), capture(reasonSlot))
            mockAppContext.sendBroadcast(capture(broadcastIntentSlot))
        }
        Assert.assertEquals(mockAccount, accountSlot.captured)
        Assert.assertEquals(OAuth2.LogoutReason.REFRESH_TOKEN_EXPIRED, reasonSlot.captured)
        Assert.assertEquals(ClientManager.ACCESS_TOKEN_REVOKE_INTENT, broadcastIntentSlot.captured.action)
    }
}

