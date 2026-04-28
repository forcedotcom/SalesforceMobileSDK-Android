package com.salesforce.androidsdk.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.accounts.UserAccountTest
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.OAuth2.OAUTH_AUTH_PATH
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.rest.ClientManager.RestClientCallback
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestClient.OAuthRefreshInterceptor
import com.salesforce.androidsdk.rest.RestResponse
import com.salesforce.androidsdk.security.BiometricAuthenticationManager
import com.salesforce.androidsdk.security.BiometricAuthenticationManager.Companion.SHOW_BIOMETRIC
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class NativeLoginManagerTest {
    private lateinit var mgr: NativeLoginManager
    private lateinit var bioAuthManager: BiometricAuthenticationManager
    @Before
    fun setUp() {
        mgr = NativeLoginManager("clientId", "redirect", "loginUrl")
    }

    @After
    fun tearDown() {
        SalesforceSDKManager.getInstance().userAccountManager
            .signoutCurrentUser(null, true, OAuth2.LogoutReason.USER_LOGOUT)
        SalesforceSDKManager.getInstance().appAttestationClient = null
        unmockkAll()
    }

    @Test
    fun testIsValidUsername() {
        Assert.assertFalse("Should not allow empty username.", mgr.isValidUsername(""))
        Assert.assertFalse("Should not allow invalid username.", mgr.isValidUsername("test@c"))
        // Success
        Assert.assertTrue(mgr.isValidUsername("test@c.co"))
    }

    @Test
    fun testIsValidPassword() {
        Assert.assertFalse("Should not allow empty password.", mgr.isValidPassword(""))
        Assert.assertFalse("Should not allow password shorter than 8 chars.", mgr.isValidPassword("test123"))
        Assert.assertFalse("Should not allow password without any letter chars.", mgr.isValidPassword("123456789"))
        Assert.assertFalse("Should not allow password without any numbers.", mgr.isValidPassword("abcdefghi"))
        // Success
        Assert.assertTrue(mgr.isValidPassword("test1234"))
    }

    @Test
    fun testShouldShowBackButton() {
        Assert.assertFalse("Should not show back button with no users logged in.", mgr.shouldShowBackButton)

        addUserAccount()
        Assert.assertTrue("Should show back button when there is a logged in user.", mgr.shouldShowBackButton)

        SalesforceSDKManager.getInstance().userAccountManager
            .signoutCurrentUser(null, true, OAuth2.LogoutReason.USER_LOGOUT)
        Assert.assertFalse("Should not show back button with no users logged in.", mgr.shouldShowBackButton)
    }

    @Test
    fun testShouldShowBackButtonBioAuth() {
        bioAuthManager = SalesforceSDKManager.getInstance().biometricAuthenticationManager as BiometricAuthenticationManager
        addUserAccount()
        Assert.assertTrue("Should show back button when there is a logged in user.", mgr.shouldShowBackButton)

        val account = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        bioAuthManager.storeMobilePolicy(account, enabled = true, timeout = 15)
        Assert.assertTrue("Should show back if not locked.", mgr.shouldShowBackButton)

        bioAuthManager.lock()
        Assert.assertFalse("Should not show back when bio auth locked.", mgr.shouldShowBackButton)
    }

    @Test
    fun testBiometricAuthenticationUsername() {
        Assert.assertNull("Should not return username with no user logged in.", mgr.biometricAuthenticationUsername)

        bioAuthManager = SalesforceSDKManager.getInstance().biometricAuthenticationManager as BiometricAuthenticationManager
        addUserAccount()
        Assert.assertNull("Should not return username when bio auth is not enabled.", mgr.biometricAuthenticationUsername)

        val account = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        bioAuthManager.storeMobilePolicy(account, enabled = true, timeout = 15)
        Assert.assertNull("Should not return username when not locked.", mgr.biometricAuthenticationUsername)

        bioAuthManager.lock()
        assertEquals("Should return username.", "test_username", mgr.biometricAuthenticationUsername)
    }

    @Test
    fun nativeLoginManager_createRequestBody_filtersNullValues() {

        val result = mgr.createRequestBody("key1" to "value1", "key2" to null)

        val buffer = okio.Buffer()
        result.writeTo(buffer)
        assertEquals("key1=value1", buffer.readUtf8())
    }

    @Test
    fun testPresentBiometricAuthReturnsFalseWhenNotLocked() {
        bioAuthManager = SalesforceSDKManager.getInstance().biometricAuthenticationManager as BiometricAuthenticationManager
        addUserAccount()
        val account = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        bioAuthManager.storeMobilePolicy(account, enabled = true, timeout = 15)
        bioAuthManager.biometricOptIn(true)
        // Not locked — should return false.
        val activity = mockk<FragmentActivity>(relaxed = true)
        val mockBiometricManager = mockk<BiometricManager>()
        val mockBiometricPrompt = mockk<BiometricPrompt>(relaxed = true)
        Assert.assertFalse("Should return false when not locked.", mgr.buildAndShowBiometricAuth(activity, mockBiometricManager, mockBiometricPrompt))
    }

    @Test
    fun testPresentBiometricAuthReturnsFalseWhenNotOptedIn() {
        bioAuthManager = SalesforceSDKManager.getInstance().biometricAuthenticationManager as BiometricAuthenticationManager
        addUserAccount()
        val account = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        bioAuthManager.storeMobilePolicy(account, enabled = true, timeout = 15)
        // Opted out, but locked.
        bioAuthManager.lock()
        val activity = mockk<FragmentActivity>(relaxed = true)
        val mockBiometricManager = mockk<BiometricManager>()
        val mockBiometricPrompt = mockk<BiometricPrompt>(relaxed = true)
        Assert.assertFalse("Should return false when not opted in.", mgr.buildAndShowBiometricAuth(activity, mockBiometricManager, mockBiometricPrompt))
    }

    @Test
    fun testPresentBiometricAuthReturnsFalseWhenNoUser() {
        // No user account — bio auth manager won't be locked.
        val activity = mockk<FragmentActivity>(relaxed = true)
        val mockBiometricManager = mockk<BiometricManager>()
        val mockBiometricPrompt = mockk<BiometricPrompt>(relaxed = true)
        Assert.assertFalse("Should return false with no user.", mgr.buildAndShowBiometricAuth(activity, mockBiometricManager, mockBiometricPrompt))
    }

    @Test
    fun testGetFallbackWebAuthenticationIntentShowBiometricFalse() {
        val intent = mgr.getFallbackWebAuthenticationIntent()
        Assert.assertFalse(
            "Fallback web auth intent should always have SHOW_BIOMETRIC=false.",
            intent.extras?.getBoolean(SHOW_BIOMETRIC) ?: true
        )
    }

    @Test
    fun testPresentBiometricAuthReturnsFalseWhenBioAuthManagerIsNull() {
        val sdkManager = SalesforceSDKManager.getInstance()
        val originalBioAuthManager = sdkManager.biometricAuthenticationManager
        try {
            sdkManager.biometricAuthenticationManager = null
            val activity = mockk<FragmentActivity>(relaxed = true)
            val mockBiometricManager = mockk<BiometricManager>()
            val mockBiometricPrompt = mockk<BiometricPrompt>(relaxed = true)
            Assert.assertFalse(
                "Should return false when bio auth manager is null.",
                mgr.buildAndShowBiometricAuth(activity, mockBiometricManager, mockBiometricPrompt)
            )
        } finally {
            sdkManager.biometricAuthenticationManager = originalBioAuthManager
        }
    }

    @Test
    fun testPresentBiometricAuthReturnsFalseWhenCannotAuthenticate() {
        bioAuthManager = SalesforceSDKManager.getInstance().biometricAuthenticationManager
                as BiometricAuthenticationManager
        addUserAccount()
        val account = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        bioAuthManager.storeMobilePolicy(account, enabled = true, timeout = 15)
        bioAuthManager.biometricOptIn(true)
        bioAuthManager.lock()

        val mockBiometricManager = mockk<BiometricManager>()
        every { mockBiometricManager.canAuthenticate(any()) } returns BIOMETRIC_ERROR_NO_HARDWARE
        val mockBiometricPrompt = mockk<BiometricPrompt>(relaxed = true)

        val activity = mockk<FragmentActivity>(relaxed = true)
        Assert.assertFalse(
            "Should return false when biometric hardware unavailable.",
            mgr.buildAndShowBiometricAuth(activity, mockBiometricManager, mockBiometricPrompt)
        )
    }

    @Test
    fun testPresentBiometricAuthReturnsTrueWhenAllConditionsMet() {
        bioAuthManager = SalesforceSDKManager.getInstance().biometricAuthenticationManager
                as BiometricAuthenticationManager
        addUserAccount()
        val account = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        bioAuthManager.storeMobilePolicy(account, enabled = true, timeout = 15)
        bioAuthManager.biometricOptIn(true)
        bioAuthManager.lock()

        val mockBiometricManager = mockk<BiometricManager>()
        every { mockBiometricManager.canAuthenticate(any()) } returns BIOMETRIC_SUCCESS
        val mockBiometricPrompt = mockk<BiometricPrompt>(relaxed = true)

        val activity = mockk<FragmentActivity>(relaxed = true)
        every { activity.resources.getString(any()) } returns "Biometric Auth"

        Assert.assertTrue(
            "Should return true when locked, opted in, and biometric available.",
            mgr.buildAndShowBiometricAuth(activity, mockBiometricManager, mockBiometricPrompt)
        )
    }

    @Test
    fun testOnBiometricAuthenticationSucceededRefreshesAndUnlocks() {
        bioAuthManager = SalesforceSDKManager.getInstance().biometricAuthenticationManager
                as BiometricAuthenticationManager
        addUserAccount()
        val account = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        bioAuthManager.storeMobilePolicy(account, enabled = true, timeout = 15)
        bioAuthManager.lock()
        Assert.assertTrue("Should be locked.", bioAuthManager.locked)

        val mockInterceptor = mockk<OAuthRefreshInterceptor>(relaxed = true)
        val mockClient = mockk<RestClient>(relaxed = true)
        every { mockClient.oAuthRefreshInterceptor } returns mockInterceptor

        val mockClientManager = mockk<ClientManager>()
        every { mockClientManager.getRestClient(any(), any<RestClientCallback>()) } answers {
            secondArg<RestClientCallback>().authenticatedRestClient(mockClient)
        }

        val activity = mockk<FragmentActivity>(relaxed = true)
        mgr.onBiometricAuthenticationSucceeded(activity, mockClientManager)

        verify { mockInterceptor.refreshAccessToken() }
        Assert.assertFalse("Should be unlocked after success.", bioAuthManager.locked)
        verify { activity.finish() }
    }

    @Test
    fun testOnBiometricAuthenticationSucceededHandlesRefreshFailure() {
        bioAuthManager = SalesforceSDKManager.getInstance().biometricAuthenticationManager
                as BiometricAuthenticationManager
        addUserAccount()
        val account = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        bioAuthManager.storeMobilePolicy(account, enabled = true, timeout = 15)
        bioAuthManager.lock()

        val mockInterceptor = mockk<OAuthRefreshInterceptor>(relaxed = true)
        every { mockInterceptor.refreshAccessToken() } throws RuntimeException("Token refresh failed")
        val mockClient = mockk<RestClient>(relaxed = true)
        every { mockClient.oAuthRefreshInterceptor } returns mockInterceptor

        val mockClientManager = mockk<ClientManager>()
        every { mockClientManager.getRestClient(any(), any<RestClientCallback>()) } answers {
            secondArg<RestClientCallback>().authenticatedRestClient(mockClient)
        }

        val activity = mockk<FragmentActivity>(relaxed = true)
        mgr.onBiometricAuthenticationSucceeded(activity, mockClientManager)

        // Should still unlock and finish even if refresh fails.
        Assert.assertFalse("Should be unlocked even after refresh failure.", bioAuthManager.locked)
        verify { activity.finish() }
    }

    @Test
    fun testBiometricAuthenticationUsernameWithNativeLoginUser() {
        bioAuthManager = SalesforceSDKManager.getInstance().biometricAuthenticationManager as BiometricAuthenticationManager
        addNativeLoginUserAccount()
        val account = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        bioAuthManager.storeMobilePolicy(account, enabled = true, timeout = 15)
        bioAuthManager.lock()
        assertEquals(
            "Should return username for native login user when locked.",
            "test_username",
            mgr.biometricAuthenticationUsername
        )
    }

    /**
     * Tests that native login uses the app attestation during login.  This test
     * can be removed when a comprehensive test of native login is created so
     * long as that test covers the inclusion of the attestation parameter.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun nativeLoginManager_login_collectsAppAttestation() = runTest {

        installAppAttestationClient(attestation = TEST_APP_ATTESTATION)
        val restClient = createRestClientStubbingFailedLoginResponse()
        mgr = createNativeLoginManagerForTest(restClient = restClient)

        mgr.login(TEST_USERNAME, TEST_PASSWORD)
        advanceUntilIdle()

        verify(exactly = 1) {
            restClient.sendAsync(match {
                it.path == "$TEST_LOGIN_URL$OAUTH_AUTH_PATH?attestation=$TEST_APP_ATTESTATION"
            }, any())
        }
    }

    /**
     * Tests that native login does not include app attestation during login
     * when the app attestation client is set but
     * [AppAttestationClient.createAppAttestation] returns null (for example,
     * because the Google Play Integrity API could not produce a token).  This
     * test can be removed when a comprehensive test of native login is created
     * so long as that test covers the exclusion of the attestation parameter.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun nativeLoginManager_login_doesNotCollectAppAttestationWhenCreateAppAttestationReturnsNull() = runTest {

        installAppAttestationClient(attestation = null)
        val restClient = createRestClientStubbingFailedLoginResponse()
        mgr = createNativeLoginManagerForTest(restClient = restClient)

        mgr.login(TEST_USERNAME, TEST_PASSWORD)
        advanceUntilIdle()

        verify(exactly = 1) {
            restClient.sendAsync(match {
                it.path == "$TEST_LOGIN_URL$OAUTH_AUTH_PATH"
            }, any())
        }
    }

    /**
     * Tests that native login does not include app attestation during login
     * when it is not applicable.  This test can be removed when a comprehensive
     * test of native login is created so long as that test covers the exclusion
     * of the attestation parameter.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun nativeLoginManager_login_doesNotCollectAppAttestationWhenAppAttestationClientIsNotSet() = runTest {

        val restClient = createRestClientStubbingFailedLoginResponse()
        mgr = createNativeLoginManagerForTest(restClient = restClient)

        mgr.login(TEST_USERNAME, TEST_PASSWORD)
        advanceUntilIdle()

        verify(exactly = 1) {
            restClient.sendAsync(match {
                it.path == "$TEST_LOGIN_URL$OAUTH_AUTH_PATH"
            }, any())
        }
    }

    // region Helpers used by attestation tests

    private fun installAppAttestationClient(attestation: String?) {
        val appAttestationClient = mockk<AppAttestationClient>(relaxed = true).apply {
            every { fetchMobileAppAttestationChallenge() } returns TEST_CHALLENGE_VALUE
            coEvery {
                createAppAttestation(appAttestationChallenge = TEST_CHALLENGE_VALUE)
            } returns attestation
        }
        SalesforceSDKManager.getInstance().appAttestationClient = appAttestationClient
    }

    private fun createRestClientStubbingFailedLoginResponse(): RestClient {
        val mockResponse = mockk<RestResponse>(relaxed = true).apply {
            every { isSuccess } returns false
        }
        return mockk<RestClient>(relaxed = true).apply {
            every { sendAsync(any(), any()) } answers {
                val callback = secondArg<RestClient.AsyncRequestCallback>()
                callback.onSuccess(firstArg(), mockResponse)
                mockk<Call>(relaxed = true)
            }
        }
    }

    private fun createNativeLoginManagerForTest(restClient: RestClient): NativeLoginManager =
        NativeLoginManager(
            clientId = TEST_CLIENT_ID,
            redirectUri = TEST_REDIRECT_URI,
            loginUrl = TEST_LOGIN_URL,
            restClient = restClient,
        )

    // endregion Helpers used by attestation tests

    private fun addUserAccount() {
        UserAccountManager.getInstance().createAccount(UserAccountTest.createTestAccount())
    }

    private fun addNativeLoginUserAccount() {
        val account = UserAccountBuilder.getInstance()
            .populateFromUserAccount(UserAccountTest.createTestAccount())
            .nativeLogin(true)
            .build()
        UserAccountManager.getInstance().createAccount(account)
    }

    private companion object {
        const val TEST_CLIENT_ID = "clientId"
        const val TEST_REDIRECT_URI = "redirect"
        const val TEST_LOGIN_URL = "loginUrl"
        const val TEST_USERNAME = "TestUser@Example.com"
        const val TEST_PASSWORD = "test123456"
        const val TEST_CHALLENGE_VALUE = "__TEST_CHALLENGE_VALUE__"
        const val TEST_APP_ATTESTATION = "__TEST_APP_ATTESTATION__"
    }
}