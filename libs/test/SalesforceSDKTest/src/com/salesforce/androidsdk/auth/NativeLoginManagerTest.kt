package com.salesforce.androidsdk.auth

import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.accounts.UserAccountTest
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.security.BiometricAuthenticationManager
import com.salesforce.androidsdk.security.BiometricAuthenticationManager.Companion.SHOW_BIOMETRIC
import io.mockk.mockk
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
        Assert.assertFalse("Should return false when not locked.", mgr.presentBiometricAuth(activity))
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
        Assert.assertFalse("Should return false when not opted in.", mgr.presentBiometricAuth(activity))
    }

    @Test
    fun testPresentBiometricAuthReturnsFalseWhenNoUser() {
        // No user account — bio auth manager won't be locked.
        val activity = mockk<FragmentActivity>(relaxed = true)
        Assert.assertFalse("Should return false with no user.", mgr.presentBiometricAuth(activity))
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