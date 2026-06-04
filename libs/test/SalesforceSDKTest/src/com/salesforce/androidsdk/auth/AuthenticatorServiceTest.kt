package com.salesforce.androidsdk.auth

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountManager.KEY_ERROR_CODE
import android.accounts.AccountManager.KEY_ERROR_MESSAGE
import android.accounts.AccountManager.KEY_INTENT
import android.content.Context
import android.content.Context.MODE_PRIVATE
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.app.SalesforceSDKManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@SmallTest
class AuthenticatorServiceTest {

    private lateinit var authenticator: AbstractAccountAuthenticator
    private lateinit var mockUserAccountManager: UserAccountManager
    private lateinit var mockAppContext: Context
    private lateinit var mockAccount: Account
    private lateinit var mockUser: UserAccount

    @Before
    fun setUp() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        mockAppContext = mockk(relaxed = true) {
            every { packageName } returns "packageName"
            every { filesDir } returns targetContext.filesDir
            every { getSharedPreferences(any(), any()) } answers {
                targetContext.getSharedPreferences(firstArg(), MODE_PRIVATE)
            }
        }

        mockUserAccountManager = mockk(relaxed = true)

        mockkObject(SalesforceSDKManager)
        val mockSDKManager = mockk<SalesforceSDKManager> {
            every { userAccountManager } returns mockUserAccountManager
            every { appContext } returns mockAppContext
            every { deviceId } returns "test-device-id"
            every { additionalOauthKeys } returns emptyList()
            every { useHybridAuthentication } returns true
            every { appAttestationClient } returns null
            @Suppress("UNCHECKED_CAST")
            every { loginActivityClass } returns Class.forName("com.salesforce.androidsdk.ui.LoginActivity") as Class<out android.app.Activity>
        }
        every { SalesforceSDKManager.getInstance() } returns mockSDKManager

        mockkStatic(UserAccountManager::class)
        every { UserAccountManager.getInstance() } returns mockUserAccountManager

        mockkObject(HttpAccess.DEFAULT)

        mockAccount = mockk(relaxed = true)
        mockUser = mockk<UserAccount>(relaxed = true) {
            every { loginServer } returns "https://login.salesforce.com"
            every { refreshToken } returns "refresh-token"
            every { clientIdForRefresh } returns "client-id"
        }
        every { mockUserAccountManager.buildUserAccount(mockAccount) } returns mockUser

        // Instantiate the private Authenticator inner class via reflection.
        val authenticatorClass = Class.forName("com.salesforce.androidsdk.auth.AuthenticatorService\$Authenticator")
        val constructor = authenticatorClass.getDeclaredConstructor(Context::class.java)
        constructor.isAccessible = true
        authenticator = constructor.newInstance(targetContext) as AbstractAccountAuthenticator
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun setupTokenErrorResponse(error: String, errorDescription: String, httpStatus: Int = 400) {
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
    }

    @Test
    fun testGetAuthToken_clientBlockedRetry_returnsErrorBundle() {
        setupTokenErrorResponse("client_blocked_retry", "Attestation verification pending")

        val result = authenticator.getAuthToken(null, mockAccount, "authTokenType", null)

        assertEquals("client_blocked_retry", result.getString(KEY_ERROR_CODE))
        assertEquals("Attestation verification pending", result.getString(KEY_ERROR_MESSAGE))
        assertNull(result.getParcelable<android.content.Intent>(KEY_INTENT))
    }

    @Test
    fun testGetAuthToken_clientBlocked_returnsLoginIntent() {
        setupTokenErrorResponse("client_blocked", "Device failed integrity check")

        val result = authenticator.getAuthToken(null, mockAccount, "authTokenType", null)

        assertNotNull(result.getParcelable<android.content.Intent>(KEY_INTENT))
        assertNull(result.getString(KEY_ERROR_CODE))
    }

    @Test
    fun testGetAuthToken_invalidGrant_returnsLoginIntent() {
        setupTokenErrorResponse("invalid_grant", "expired authorization code")

        val result = authenticator.getAuthToken(null, mockAccount, "authTokenType", null)

        assertNotNull(result.getParcelable<android.content.Intent>(KEY_INTENT))
        assertNull(result.getString(KEY_ERROR_CODE))
    }

    @Test
    fun testGetAuthToken_serverError500_returnsErrorBundle() {
        setupTokenErrorResponse("server_error", "Internal server error", httpStatus = 500)

        val result = authenticator.getAuthToken(null, mockAccount, "authTokenType", null)

        assertEquals("server_error", result.getString(KEY_ERROR_CODE))
        assertEquals("Internal server error", result.getString(KEY_ERROR_MESSAGE))
        assertNull(result.getParcelable<android.content.Intent>(KEY_INTENT))
    }
}
