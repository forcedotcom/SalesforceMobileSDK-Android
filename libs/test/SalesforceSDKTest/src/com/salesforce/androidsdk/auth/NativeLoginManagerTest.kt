package com.salesforce.androidsdk.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.rest.ClientManagerTest
import com.salesforce.androidsdk.rest.ClientManagerTest.TEST_ACCOUNT_NAME
import com.salesforce.androidsdk.rest.ClientManagerTest.TEST_AUTH_TOKEN
import com.salesforce.androidsdk.rest.ClientManagerTest.TEST_CALLBACK_URL
import com.salesforce.androidsdk.rest.ClientManagerTest.TEST_CLIENT_ID
import com.salesforce.androidsdk.rest.ClientManagerTest.TEST_IDENTITY_URL
import com.salesforce.androidsdk.rest.ClientManagerTest.TEST_INSTANCE_URL
import com.salesforce.androidsdk.rest.ClientManagerTest.TEST_LANGUAGE
import com.salesforce.androidsdk.rest.ClientManagerTest.TEST_LOCALE
import com.salesforce.androidsdk.rest.ClientManagerTest.TEST_LOGIN_URL
import com.salesforce.androidsdk.rest.ClientManagerTest.TEST_ORG_ID
import com.salesforce.androidsdk.rest.ClientManagerTest.TEST_REFRESH_TOKEN
import com.salesforce.androidsdk.rest.ClientManagerTest.TEST_SCOPES
import com.salesforce.androidsdk.rest.ClientManagerTest.TEST_USERNAME
import com.salesforce.androidsdk.rest.ClientManagerTest.TEST_USER_ID
import com.salesforce.androidsdk.security.BiometricAuthenticationManager
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
        bioAuthManager.storeMobilePolicy(account, true, 15)
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
        bioAuthManager.storeMobilePolicy(account, true, 15)
        Assert.assertNull("Should not return username when not locked.", mgr.biometricAuthenticationUsername)

        bioAuthManager.lock()
        Assert.assertEquals("Should return username.", "test_username", mgr.biometricAuthenticationUsername)
    }


    private fun addUserAccount() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val loginOptions = ClientManager.LoginOptions(
            TEST_LOGIN_URL,
            TEST_CALLBACK_URL,
            TEST_CLIENT_ID,
            TEST_SCOPES,
        )
        val clientManager = ClientManager(
            targetContext,
            ClientManagerTest.TEST_ACCOUNT_TYPE, loginOptions, true,
        )
        clientManager.createNewAccount(
            TEST_ACCOUNT_NAME,
            TEST_USERNAME, TEST_REFRESH_TOKEN,
            TEST_AUTH_TOKEN, TEST_INSTANCE_URL,
            TEST_LOGIN_URL, TEST_IDENTITY_URL,
            TEST_CLIENT_ID, TEST_ORG_ID,
            TEST_USER_ID, null, null, null,
            null, null, null, null, null,
            null, null, null, null, null,
            null, null, null, true,
            TEST_LANGUAGE, TEST_LOCALE
        )
    }
}