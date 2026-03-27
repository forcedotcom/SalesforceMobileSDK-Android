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
import com.salesforce.androidsdk.security.BiometricAuthenticationManager
import com.salesforce.androidsdk.security.BiometricAuthenticationManager.Companion.SHOW_BIOMETRIC
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.rest.ClientManager.RestClientCallback
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestClient.OAuthRefreshInterceptor
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert
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
        Assert.assertEquals("Should return username.", "test_username", mgr.biometricAuthenticationUsername)
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
        Assert.assertEquals(
            "Should return username for native login user when locked.",
            "test_username",
            mgr.biometricAuthenticationUsername
        )
    }

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
}