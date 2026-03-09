/*
 * Copyright (c) 2025-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
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
package com.salesforce.androidsdk.auth

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.app.Features.FEATURE_BIOMETRIC_AUTH
import com.salesforce.androidsdk.app.Features.FEATURE_SCREEN_LOCK
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.RuntimeConfig
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.security.BiometricAuthenticationManager
import com.salesforce.androidsdk.security.ScreenLockManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI

/**
 * Tests for AuthenticationUtilities.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
@ExperimentalCoroutinesApi
class AuthenticationUtilitiesTest {

    private lateinit var testContext: Context
    private val mockUserAccountManager: UserAccountManager = mockk()
    private val mockRuntimeConfig: RuntimeConfig = mockk()
    private val onAuthFlowError: (String, String?, Throwable?) -> Unit = mockk()
    private val onAuthFlowSuccess: (UserAccount) -> Unit = mockk()
    private val buildAccountName: (String?, String?) -> String = { username, instanceServer -> "$username ($instanceServer)" }
    private val updateLoggingPrefs: (UserAccount) -> Unit = mockk()
    private val fetchUserIdentity: suspend (OAuth2.TokenEndpointResponse) -> OAuth2.IdServiceResponse? = mockk()
    private val startMainActivity: () -> Unit = mockk()
    private val setAdministratorPreferences: (OAuth2.IdServiceResponse?, UserAccount) -> Unit = mockk()
    private val addAccount: (UserAccount) -> Unit = mockk()
    private val handleScreenLockPolicy: (OAuth2.IdServiceResponse?, UserAccount) -> Unit = mockk()
    private val handleBiometricAuthPolicy: (OAuth2.IdServiceResponse?, UserAccount) -> Unit = mockk()
    private val handleDuplicateUserAccount: (UserAccountManager, UserAccount, OAuth2.IdServiceResponse?) -> Unit = mockk()

    @Before
    fun setUp() {
        // Setup test context
        testContext = InstrumentationRegistry.getInstrumentation().targetContext

        // Setup mock runtime config
        every { mockRuntimeConfig.isManagedApp } returns false

        // Setup mock user account manager
        every { mockUserAccountManager.authenticatedUsers } returns mutableListOf()

        // Setup mock behaviors
        every { onAuthFlowError.invoke(any(), any(), any()) } returns Unit
        every { onAuthFlowSuccess.invoke(any()) } returns Unit
        every { updateLoggingPrefs.invoke(any()) } returns Unit
        every { startMainActivity.invoke() } returns Unit
        every { setAdministratorPreferences.invoke(any(), any()) } returns Unit
        every { addAccount.invoke(any()) } returns Unit
        every { handleScreenLockPolicy.invoke(any(), any()) } returns Unit
        every { handleBiometricAuthPolicy.invoke(any(), any()) } returns Unit
        every { handleDuplicateUserAccount.invoke(any(), any(), any()) } returns Unit

        // Setup mock for UserAccountManager methods
        every { mockUserAccountManager.createAccount(any()) } returns mockk<android.os.Bundle>()
        every { mockUserAccountManager.switchToUser(any()) } returns Unit
        every { mockUserAccountManager.sendUserSwitchIntent(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testOnAuthFlowComplete_blockIntegrationUser_shouldCallError() = runTest {

        // When
        callOnAuthFlowComplete(blockIntegrationUser = true)

        // Then
        verify { onAuthFlowError.invoke("Error", "Authentication error. Please try again.", null) }
        verify(exactly = 0) { onAuthFlowSuccess.invoke(any()) }
        verify(exactly = 0) { mockUserAccountManager.createAccount(any()) }
        verify(exactly = 0) { mockUserAccountManager.switchToUser(any()) }
    }

    @Test
    fun testOnAuthFlowComplete_managedAppRequirement_shouldCallError() = runTest {
        // Given
        val userIdentityWithManagedAppRequirement = createIdServiceResponse(
            customPermissions = JSONObject().apply {
                put("must_be_managed_app", true)
            }
        )

        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentityWithManagedAppRequirement
        every { mockRuntimeConfig.isManagedApp } returns false

        // When
        callOnAuthFlowComplete()

        // Then
        verify { onAuthFlowError.invoke("Error", "Authentication only allowed from managed device.", null) }
        verify(exactly = 0) { onAuthFlowSuccess.invoke(any()) }
        verify(exactly = 0) { mockUserAccountManager.createAccount(any()) }
        verify(exactly = 0) { mockUserAccountManager.switchToUser(any()) }
    }

    @Test
    fun testOnAuthFlowComplete_successfulFlow_shouldCallSuccess() = runTest {
        // Given
        val tokenResponse = createTokenEndpointResponse()
        val userIdentity = createIdServiceResponse()
        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentity

        // Create the expected UserAccount object like in AuthenticationUtilities.kt
        val expectedAccount = UserAccountBuilder.getInstance()
            .populateFromTokenEndpointResponse(tokenResponse)
            .populateFromIdServiceResponse(userIdentity)
            .accountName(buildAccountName(userIdentity.username, tokenResponse.instanceUrl))
            .loginServer("https://login.salesforce.com")
            .clientId("test_consumer_key")
            .nativeLogin(false)
            .build()

        // When
        callOnAuthFlowComplete()

        // Then
        verify(exactly = 0) { onAuthFlowError.invoke(any(), any(), any()) }
        verify { onAuthFlowSuccess.invoke(expectedAccount) }
        verify { mockUserAccountManager.createAccount(expectedAccount) }
        verify { mockUserAccountManager.switchToUser(expectedAccount) }
        verify { setAdministratorPreferences.invoke(userIdentity, expectedAccount) }
        verify { handleDuplicateUserAccount.invoke(mockUserAccountManager, expectedAccount, userIdentity) }
        verify { addAccount.invoke(expectedAccount) }
        verify { updateLoggingPrefs.invoke(expectedAccount) }
        verify { startMainActivity.invoke() }
        verify { handleScreenLockPolicy.invoke(userIdentity, expectedAccount) }
        verify { handleBiometricAuthPolicy.invoke(userIdentity, expectedAccount) }
    }

    @Test
    fun testOnAuthFlowComplete_successfulFlowWithoutIdScope_shouldCallSuccess() = runTest {
        // Given
        val tokenResponseWithoutIdScope = createTokenEndpointResponse(
            scope = "refresh_token" // Missing id scope
        )

        // Mock fetchUserIdentity to return null (simulating no id scope)
        coEvery { fetchUserIdentity.invoke(any()) } returns null

        // Create the expected UserAccount object without IdServiceResponse population
        val expectedAccount = UserAccountBuilder.getInstance()
            .populateFromTokenEndpointResponse(tokenResponseWithoutIdScope)
            .populateFromIdServiceResponse(null) // No identity service response
            .accountName(buildAccountName(null, tokenResponseWithoutIdScope.instanceUrl))
            .loginServer("https://login.salesforce.com")
            .clientId("test_consumer_key")
            .nativeLogin(false)
            .build()

        // When
        callOnAuthFlowComplete(tokenResponseWithoutIdScope)

        // Then
        verify(exactly = 0) { onAuthFlowError.invoke(any(), any(), any()) }
        verify { onAuthFlowSuccess.invoke(expectedAccount) }
        verify { mockUserAccountManager.createAccount(expectedAccount) }
        verify { mockUserAccountManager.switchToUser(expectedAccount) }
        verify { setAdministratorPreferences.invoke(null, expectedAccount) }
        verify { handleDuplicateUserAccount.invoke(mockUserAccountManager, expectedAccount, null) }
        verify { addAccount.invoke(expectedAccount) }
        verify { updateLoggingPrefs.invoke(expectedAccount) }
        verify { startMainActivity.invoke() }
        verify { handleScreenLockPolicy.invoke(null, expectedAccount) }
        verify { handleBiometricAuthPolicy.invoke(null, expectedAccount) }

        // Verify that fetchUserIdentity was called but returned null
        coVerify(exactly = 1) { fetchUserIdentity.invoke(tokenResponseWithoutIdScope) }
    }
    @Test
    fun testOnAuthFlowComplete_withNativeLogin_shouldCallSuccess() = runTest {
        // Given
        val tokenResponseWithoutIdScope = createTokenEndpointResponse(
            scope = "refresh_token" // Missing id scope
        )

        // Mock fetchUserIdentity to return null (simulating no id scope)
        coEvery { fetchUserIdentity.invoke(any()) } returns null

        // Create the expected UserAccount object without IdServiceResponse population
        val expectedAccount = UserAccountBuilder.getInstance()
            .populateFromTokenEndpointResponse(tokenResponseWithoutIdScope)
            .populateFromIdServiceResponse(null) // No identity service response
            .accountName(buildAccountName(null, tokenResponseWithoutIdScope.instanceUrl))
            .loginServer("https://login.salesforce.com")
            .clientId("test_consumer_key")
            .nativeLogin(true) // Expect true
            .build()

        // When
        callOnAuthFlowComplete(
            customTokenResponse = tokenResponseWithoutIdScope,
            nativeLogin = true,
        )

        // Then
        verify(exactly = 0) { onAuthFlowError.invoke(any(), any(), any()) }
        verify { onAuthFlowSuccess.invoke(expectedAccount) }
        verify { mockUserAccountManager.createAccount(expectedAccount) }
        verify { mockUserAccountManager.switchToUser(expectedAccount) }
        verify { setAdministratorPreferences.invoke(null, expectedAccount) }
        verify { handleDuplicateUserAccount.invoke(mockUserAccountManager, expectedAccount, null) }
        verify { addAccount.invoke(expectedAccount) }
        verify { updateLoggingPrefs.invoke(expectedAccount) }
        verify { startMainActivity.invoke() }
        verify { handleScreenLockPolicy.invoke(null, expectedAccount) }
        verify { handleBiometricAuthPolicy.invoke(null, expectedAccount) }

        // Verify that fetchUserIdentity was called but returned null
        coVerify(exactly = 1) { fetchUserIdentity.invoke(tokenResponseWithoutIdScope) }
    }


    // region Token Migration Tests

    @Test
    fun testOnAuthFlowComplete_tokenMigration_shouldNotCallStartMainActivity() = runTest {
        // Given
        val userIdentity = createIdServiceResponse()
        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentity
        setupPersistAccountMocks()

        // When - tokenMigration is true
        callOnAuthFlowComplete(tokenMigration = true)

        // Then - startMainActivity should NOT be called during token migration
        verify(exactly = 0) { startMainActivity.invoke() }
        // But onAuthFlowSuccess should still be called
        verify(exactly = 1) { onAuthFlowSuccess.invoke(any()) }
    }

    @Test
    fun testOnAuthFlowComplete_tokenMigration_shouldCallSuccess() = runTest {
        // Given
        val tokenResponse = createTokenEndpointResponse()
        val userIdentity = createIdServiceResponse()
        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentity
        setupPersistAccountMocks()

        // Create the expected UserAccount object
        val expectedAccount = UserAccountBuilder.getInstance()
            .populateFromTokenEndpointResponse(tokenResponse)
            .populateFromIdServiceResponse(userIdentity)
            .accountName(buildAccountName(userIdentity.username, tokenResponse.instanceUrl))
            .loginServer("https://login.salesforce.com")
            .clientId("test_consumer_key")
            .nativeLogin(false)
            .build()

        // When - tokenMigration is true
        callOnAuthFlowComplete(tokenMigration = true)

        // Then - onAuthFlowSuccess should be called with the account
        verify(exactly = 1) { onAuthFlowSuccess.invoke(expectedAccount) }
        verify(exactly = 0) { onAuthFlowError.invoke(any(), any(), any()) }
    }

    @Test
    fun testOnAuthFlowComplete_tokenMigration_shouldPersistAccount() = runTest {
        // Given
        val userIdentity = createIdServiceResponse()
        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentity
        setupPersistAccountMocks()

        // When - tokenMigration is true
        callOnAuthFlowComplete(tokenMigration = true)

        // Then - persistAccount should be used instead of createAccount/switchToUser
        verify(exactly = 1) { addAccount.invoke(any()) }
        verify(exactly = 1) { mockUserAccountManager.updateAccount(any<Account>(), any<UserAccount>()) }
        verify(exactly = 0) { mockUserAccountManager.createAccount(any()) }
        verify(exactly = 0) { mockUserAccountManager.switchToUser(any()) }
    }

    @Test
    fun testOnAuthFlowComplete_tokenMigration_shouldHandleLockPolicies() = runTest {
        // Given
        val userIdentity = createIdServiceResponse()
        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentity
        setupPersistAccountMocks()

        // When - tokenMigration is true
        callOnAuthFlowComplete(tokenMigration = true)

        // Then - policies should still be handled
        verify(exactly = 1) { handleScreenLockPolicy.invoke(any(), any()) }
        verify(exactly = 1) { handleBiometricAuthPolicy.invoke(any(), any()) }
    }

    @Test
    fun testOnAuthFlowComplete_tokenMigration_withBlockedIntegrationUser_shouldCallError() = runTest {
        // When - tokenMigration is true but user is blocked
        callOnAuthFlowComplete(
            blockIntegrationUser = true,
            tokenMigration = true,
        )

        // Then - error should still be called for blocked users
        verify { onAuthFlowError.invoke("Error", "Authentication error. Please try again.", null) }
        verify(exactly = 0) { onAuthFlowSuccess.invoke(any()) }
        verify(exactly = 0) { startMainActivity.invoke() }
    }

    @Test
    fun testOnAuthFlowComplete_tokenMigration_withManagedAppRequirement_shouldCallError() = runTest {
        // Given
        val userIdentityWithManagedAppRequirement = createIdServiceResponse(
            customPermissions = JSONObject().apply {
                put("must_be_managed_app", true)
            }
        )
        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentityWithManagedAppRequirement
        every { mockRuntimeConfig.isManagedApp } returns false

        // When - tokenMigration is true but managed app required
        callOnAuthFlowComplete(tokenMigration = true)

        // Then - error should still be called
        verify { onAuthFlowError.invoke("Error", "Authentication only allowed from managed device.", null) }
        verify(exactly = 0) { onAuthFlowSuccess.invoke(any()) }
        verify(exactly = 0) { startMainActivity.invoke() }
    }

    @Test
    fun testOnAuthFlowComplete_tokenMigration_persistsNewAccountToAccountManager() = runTest {
        // Given
        val userIdentity = createIdServiceResponse()
        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentity
        val mockAcctManager = setupPersistAccountMocks(addAccountExplicitlyReturns = true)

        // When
        callOnAuthFlowComplete(tokenMigration = true)

        // Then - new account should be added to AccountManager
        verify { mockAcctManager.addAccountExplicitly(
            match { it.name == "test@example.com (https://test.salesforce.com)" && it.type == "test_account_type" },
            any(),
            any()
        ) }
        verify(exactly = 0) { mockAcctManager.setPassword(any(), any()) }
        verify { mockAcctManager.setAuthToken(any(), eq(AccountManager.KEY_AUTHTOKEN), any()) }
    }

    @Test
    fun testOnAuthFlowComplete_tokenMigration_existingAccount_updatesPassword() = runTest {
        // Given
        val userIdentity = createIdServiceResponse()
        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentity
        val mockAcctManager = setupPersistAccountMocks(addAccountExplicitlyReturns = false)

        // When
        callOnAuthFlowComplete(tokenMigration = true)

        // Then - existing account should have password updated
        verify { mockAcctManager.setPassword(any(), any()) }
        verify { mockAcctManager.setAuthToken(any(), eq(AccountManager.KEY_AUTHTOKEN), any()) }
        verify { mockUserAccountManager.updateAccount(any<Account>(), any<UserAccount>()) }
    }

    @Test
    fun testOnAuthFlowComplete_tokenMigration_doesNotCallNonMigrationFlowSteps() = runTest {
        // Given
        val userIdentity = createIdServiceResponse()
        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentity
        setupPersistAccountMocks()

        // When
        callOnAuthFlowComplete(tokenMigration = true)

        // Then - non-migration flow steps should NOT be called
        verify(exactly = 0) { mockUserAccountManager.createAccount(any()) }
        verify(exactly = 0) { mockUserAccountManager.switchToUser(any()) }
        verify(exactly = 0) { startMainActivity.invoke() }
        verify(exactly = 0) { updateLoggingPrefs.invoke(any()) }
        verify(exactly = 0) { mockUserAccountManager.sendUserSwitchIntent(any(), any()) }
    }

    // endregion

    // region handleScreenLockPolicy Tests

    @Test
    fun testHandleScreenLockPolicy_positiveTimeout_registersFeatureAndStoresPolicy() {
        // Given
        val mockScreenLockManager = mockk<ScreenLockManager>(relaxed = true)
        val mockSdkManager = setupMockSdkManager(screenLockManager = mockScreenLockManager)

        val userIdentity = createIdServiceResponse()
        userIdentity.screenLock = true
        userIdentity.screenLockTimeout = 10

        val account = mockk<UserAccount>()

        // When
        com.salesforce.androidsdk.auth.handleScreenLockPolicy(userIdentity, account)

        // Then
        verify { mockSdkManager.registerUsedAppFeature(FEATURE_SCREEN_LOCK) }
        verify { mockScreenLockManager.storeMobilePolicy(account, enabled = true, 600000) }
    }

    @Test
    fun testHandleScreenLockPolicy_zeroTimeout_enabledManager_unregistersFeatureAndCleansUp() {
        // Given
        val mockScreenLockManager = mockk<ScreenLockManager>(relaxed = true) {
            every { enabled } returns true
        }
        val mockSdkManager = setupMockSdkManager(screenLockManager = mockScreenLockManager)

        val userIdentity = createIdServiceResponse()
        userIdentity.screenLockTimeout = 0

        val account = mockk<UserAccount>()

        // When
        com.salesforce.androidsdk.auth.handleScreenLockPolicy(userIdentity, account)

        // Then
        verify { mockSdkManager.unregisterUsedAppFeature(FEATURE_SCREEN_LOCK) }
        verify { mockScreenLockManager.cleanUp(account) }
    }

    @Test
    fun testHandleScreenLockPolicy_negativeTimeout_disabledManager_noOp() {
        // Given
        val mockScreenLockManager = mockk<ScreenLockManager>(relaxed = true) {
            every { enabled } returns false
        }
        val mockSdkManager = setupMockSdkManager(screenLockManager = mockScreenLockManager)

        val userIdentity = createIdServiceResponse()
        val account = mockk<UserAccount>()

        // When
        com.salesforce.androidsdk.auth.handleScreenLockPolicy(userIdentity, account)

        // Then
        verify(exactly = 0) { mockSdkManager.registerUsedAppFeature(any()) }
        verify(exactly = 0) { mockSdkManager.unregisterUsedAppFeature(any()) }
        verify(exactly = 0) { mockScreenLockManager.storeMobilePolicy(any(), any(), any()) }
        verify(exactly = 0) { mockScreenLockManager.cleanUp(any()) }
    }

    @Test
    fun testHandleScreenLockPolicy_nullIdentity_enabledManager_unregistersAndCleansUp() {
        // Given
        val mockScreenLockManager = mockk<ScreenLockManager>(relaxed = true) {
            every { enabled } returns true
        }
        val mockSdkManager = setupMockSdkManager(screenLockManager = mockScreenLockManager)

        val account = mockk<UserAccount>()

        // When
        com.salesforce.androidsdk.auth.handleScreenLockPolicy(null, account)

        // Then
        verify { mockSdkManager.unregisterUsedAppFeature(FEATURE_SCREEN_LOCK) }
        verify { mockScreenLockManager.cleanUp(account) }
    }

    @Test
    fun testHandleScreenLockPolicy_nullIdentity_disabledManager_noOp() {
        // Given
        val mockScreenLockManager = mockk<ScreenLockManager>(relaxed = true) {
            every { enabled } returns false
        }
        val mockSdkManager = setupMockSdkManager(screenLockManager = mockScreenLockManager)

        val account = mockk<UserAccount>()

        // When
        com.salesforce.androidsdk.auth.handleScreenLockPolicy(null, account)

        // Then
        verify(exactly = 0) { mockSdkManager.registerUsedAppFeature(any()) }
        verify(exactly = 0) { mockSdkManager.unregisterUsedAppFeature(any()) }
        verify(exactly = 0) { mockScreenLockManager.storeMobilePolicy(any(), any(), any()) }
        verify(exactly = 0) { mockScreenLockManager.cleanUp(any()) }
    }

    // endregion

    // region handleBiometricAuthPolicy Tests

    @Test
    fun testHandleBiometricAuthPolicy_biometricEnabled_registersFeatureAndStoresPolicy() {
        // Given
        val mockBioAuthManager = mockk<BiometricAuthenticationManager>(relaxed = true)
        val mockSdkManager = setupMockSdkManager(biometricAuthenticationManager = mockBioAuthManager)

        val userIdentity = createIdServiceResponse()
        userIdentity.biometricAuth = true
        userIdentity.biometricAuthTimeout = 15

        val account = mockk<UserAccount>()

        // When
        com.salesforce.androidsdk.auth.handleBiometricAuthPolicy(userIdentity, account)

        // Then
        verify { mockSdkManager.registerUsedAppFeature(FEATURE_BIOMETRIC_AUTH) }
        verify { mockBioAuthManager.storeMobilePolicy(account, enabled = true, 900000) }
    }

    @Test
    fun testHandleBiometricAuthPolicy_biometricDisabled_enabledManager_unregistersFeatureAndCleansUp() {
        // Given
        val mockBioAuthManager = mockk<BiometricAuthenticationManager>(relaxed = true) {
            every { enabled } returns true
        }
        val mockSdkManager = setupMockSdkManager(biometricAuthenticationManager = mockBioAuthManager)

        val userIdentity = createIdServiceResponse()
        userIdentity.biometricAuth = false

        val account = mockk<UserAccount>()

        // When
        com.salesforce.androidsdk.auth.handleBiometricAuthPolicy(userIdentity, account)

        // Then
        verify { mockSdkManager.unregisterUsedAppFeature(FEATURE_BIOMETRIC_AUTH) }
        verify { mockBioAuthManager.cleanUp(account) }
    }

    @Test
    fun testHandleBiometricAuthPolicy_biometricDisabled_disabledManager_noOp() {
        // Given
        val mockBioAuthManager = mockk<BiometricAuthenticationManager>(relaxed = true) {
            every { enabled } returns false
        }
        val mockSdkManager = setupMockSdkManager(biometricAuthenticationManager = mockBioAuthManager)

        val userIdentity = createIdServiceResponse()
        userIdentity.biometricAuth = false

        val account = mockk<UserAccount>()

        // When
        com.salesforce.androidsdk.auth.handleBiometricAuthPolicy(userIdentity, account)

        // Then
        verify(exactly = 0) { mockSdkManager.registerUsedAppFeature(any()) }
        verify(exactly = 0) { mockSdkManager.unregisterUsedAppFeature(any()) }
        verify(exactly = 0) { mockBioAuthManager.storeMobilePolicy(any(), any(), any()) }
        verify(exactly = 0) { mockBioAuthManager.cleanUp(any()) }
    }

    @Test
    fun testHandleBiometricAuthPolicy_nullIdentity_enabledManager_unregistersAndCleansUp() {
        // Given
        val mockBioAuthManager = mockk<BiometricAuthenticationManager>(relaxed = true) {
            every { enabled } returns true
        }
        val mockSdkManager = setupMockSdkManager(biometricAuthenticationManager = mockBioAuthManager)

        val account = mockk<UserAccount>()

        // When
        com.salesforce.androidsdk.auth.handleBiometricAuthPolicy(null, account)

        // Then
        verify { mockSdkManager.unregisterUsedAppFeature(FEATURE_BIOMETRIC_AUTH) }
        verify { mockBioAuthManager.cleanUp(account) }
    }

    @Test
    fun testHandleBiometricAuthPolicy_nullIdentity_disabledManager_noOp() {
        // Given
        val mockBioAuthManager = mockk<BiometricAuthenticationManager>(relaxed = true) {
            every { enabled } returns false
        }
        val mockSdkManager = setupMockSdkManager(biometricAuthenticationManager = mockBioAuthManager)

        val account = mockk<UserAccount>()

        // When
        com.salesforce.androidsdk.auth.handleBiometricAuthPolicy(null, account)

        // Then
        verify(exactly = 0) { mockSdkManager.registerUsedAppFeature(any()) }
        verify(exactly = 0) { mockSdkManager.unregisterUsedAppFeature(any()) }
        verify(exactly = 0) { mockBioAuthManager.storeMobilePolicy(any(), any(), any()) }
        verify(exactly = 0) { mockBioAuthManager.cleanUp(any()) }
    }

    // endregion

    // region handleDuplicateUserAccount Tests

    @Test
    fun testHandleDuplicateUserAccount_nullAuthenticatedUsers_noOp() {
        // Given
        val mockUam = mockk<UserAccountManager>(relaxed = true) {
            every { authenticatedUsers } returns null
        }
        val account = buildTestUserAccount()

        // When
        com.salesforce.androidsdk.auth.handleDuplicateUserAccount(mockUam, account, null)

        // Then
        verify(exactly = 0) { mockUam.clearCachedCurrentUser() }
    }

    @Test
    fun testHandleDuplicateUserAccount_noDuplicate_noRemoval() {
        // Given
        val otherUser = buildTestUserAccount(userId = "005OTHER")
        val mockUam = mockk<UserAccountManager>(relaxed = true) {
            every { authenticatedUsers } returns mutableListOf(otherUser)
        }
        val account = buildTestUserAccount()

        // When
        com.salesforce.androidsdk.auth.handleDuplicateUserAccount(mockUam, account, null)

        // Then
        verify(exactly = 0) { mockUam.clearCachedCurrentUser() }
        verify(exactly = 0) { mockUam.buildAccount(any()) }
    }

    @Test
    fun testHandleDuplicateUserAccount_duplicateFound_sameRefreshToken_removesAccountOnly() {
        // Given
        val mockClientManager = mockk<ClientManager>(relaxed = true)
        setupMockSdkManager(clientManager = mockClientManager)

        val duplicateUser = buildTestUserAccount(refreshToken = "same_token")
        val mockUam = mockk<UserAccountManager>(relaxed = true) {
            every { authenticatedUsers } returns mutableListOf(duplicateUser)
        }
        val account = buildTestUserAccount(refreshToken = "same_token")

        // When
        com.salesforce.androidsdk.auth.handleDuplicateUserAccount(mockUam, account, null)

        // Then
        verify { mockUam.clearCachedCurrentUser() }
    }

    @Test
    fun testHandleDuplicateUserAccount_duplicateFound_differentRefreshToken_revokesToken() {
        // Given
        val mockClientManager = mockk<ClientManager>(relaxed = true)
        setupMockSdkManager(clientManager = mockClientManager)
        val mockRevokeRefreshToken = mockk<(HttpAccess, URI, String, OAuth2.LogoutReason) -> Unit>(relaxed = true)

        val duplicateUser = buildTestUserAccount(
            refreshToken = "old_token",
            instanceServer = "https://test.salesforce.com"
        )
        val mockAccount = mockk<Account>()
        val mockUam = mockk<UserAccountManager>(relaxed = true) {
            every { authenticatedUsers } returns mutableListOf(duplicateUser)
            every { buildAccount(duplicateUser) } returns mockAccount
        }
        val account = buildTestUserAccount(refreshToken = "new_token")

        // When
        com.salesforce.androidsdk.auth.handleDuplicateUserAccount(mockUam, account, null, mockRevokeRefreshToken)
        Thread.sleep(500)

        // Then
        verify { mockUam.clearCachedCurrentUser() }
        verify {
            mockRevokeRefreshToken.invoke(
                any(),
                any(),
                eq("old_token"),
                eq(OAuth2.LogoutReason.REFRESH_TOKEN_ROTATED)
            )
        }
    }

    @Test
    fun testHandleDuplicateUserAccount_duplicateFound_differentRefreshToken_biometricEnabled_unlocks() {
        // Given
        val mockBioAuthManager = mockk<BiometricAuthenticationManager>(relaxed = true)
        val mockClientManager = mockk<ClientManager>(relaxed = true)
        val mockSdkManager = setupMockSdkManager(
            biometricAuthenticationManager = mockBioAuthManager,
            clientManager = mockClientManager
        )
        setupBiometricEnabledPrefs(mockSdkManager)
        val mockRevokeRefreshToken = mockk<(HttpAccess, URI, String, OAuth2.LogoutReason) -> Unit>(relaxed = true)

        val duplicateUser = buildTestUserAccount(
            refreshToken = "old_token",
            instanceServer = "https://test.salesforce.com"
        )
        val mockAccount = mockk<Account>()
        val mockUam = mockk<UserAccountManager>(relaxed = true) {
            every { authenticatedUsers } returns mutableListOf(duplicateUser)
            every { buildAccount(duplicateUser) } returns mockAccount
        }
        val account = buildTestUserAccount(refreshToken = "new_token")

        // When
        com.salesforce.androidsdk.auth.handleDuplicateUserAccount(mockUam, account, null, mockRevokeRefreshToken)
        Thread.sleep(500)

        // Then
        verify { mockBioAuthManager.onUnlock() }
        verify {
            mockRevokeRefreshToken.invoke(
                any(),
                any(),
                eq("old_token"),
                eq(OAuth2.LogoutReason.REFRESH_TOKEN_ROTATED)
            )
        }
    }

    @Test
    fun testHandleDuplicateUserAccount_biometricIdentity_signsOutExistingBiometricUsers() {
        // Given
        val mockSdkManager = setupMockSdkManager()
        setupBiometricEnabledPrefs(mockSdkManager)

        val existingBioUser = buildTestUserAccount(userId = "005BIO_USER")

        val mockUam = mockk<UserAccountManager>(relaxed = true) {
            every { authenticatedUsers } returns mutableListOf(existingBioUser)
        }
        val account = buildTestUserAccount()
        val userIdentity = createIdServiceResponse()
        userIdentity.biometricAuth = true

        // When
        com.salesforce.androidsdk.auth.handleDuplicateUserAccount(mockUam, account, userIdentity)

        // Then
        verify { mockUam.signoutUser(existingBioUser, null, false, OAuth2.LogoutReason.UNEXPECTED) }
    }

    @Test
    fun testHandleDuplicateUserAccount_nullIdentity_skipsBiometricSignout() {
        // Given
        val mockSdkManager = setupMockSdkManager()
        setupBiometricEnabledPrefs(mockSdkManager)

        val existingBioUser = buildTestUserAccount(userId = "005BIO_USER")

        val mockUam = mockk<UserAccountManager>(relaxed = true) {
            every { authenticatedUsers } returns mutableListOf(existingBioUser)
        }
        val account = buildTestUserAccount()

        // When
        com.salesforce.androidsdk.auth.handleDuplicateUserAccount(mockUam, account, null)

        // Then
        verify(exactly = 0) { mockUam.signoutUser(any(), any(), any(), any<OAuth2.LogoutReason>()) }
    }

    // endregion

    private fun buildTestUserAccount(
        userId: String = "005000000000000AAA",
        orgId: String = "00D000000000000EAA",
        refreshToken: String = "test_refresh_token",
        instanceServer: String = "https://test.salesforce.com"
    ): UserAccount {
        return UserAccountBuilder.getInstance()
            .userId(userId)
            .orgId(orgId)
            .refreshToken(refreshToken)
            .instanceServer(instanceServer)
            .authToken("test_auth_token")
            .loginServer("https://login.salesforce.com")
            .idUrl("https://test.salesforce.com/id/$orgId/$userId")
            .accountName("test_account")
            .build()
    }

    private fun setupPersistAccountMocks(addAccountExplicitlyReturns: Boolean = true): AccountManager {
        val mockAcctManager = mockk<AccountManager>(relaxed = true)

        mockkObject(SalesforceSDKManager)
        val mockSdkManager = mockk<SalesforceSDKManager>(relaxed = true)
        every { SalesforceSDKManager.getInstance() } returns mockSdkManager
        every { mockSdkManager.accountType } returns "test_account_type"
        every { mockSdkManager.appContext } returns testContext
        every { SalesforceSDKManager.encryptionKey } returns "test_encryption_key"
        every { SalesforceSDKManager.encrypt(any(), any()) } answers { firstArg<String?>() }

        mockkStatic(AccountManager::class)
        every { AccountManager.get(any()) } returns mockAcctManager
        every { mockAcctManager.addAccountExplicitly(any(), any(), any()) } returns addAccountExplicitlyReturns

        every { mockUserAccountManager.updateAccount(any<Account>(), any<UserAccount>()) } returns mockk<android.os.Bundle>()

        return mockAcctManager
    }

    private fun setupBiometricEnabledPrefs(mockSdkManager: SalesforceSDKManager) {
        val mockPrefs = mockk<android.content.SharedPreferences>(relaxed = true) {
            every { getBoolean("bio_auth_enabled", false) } returns true
        }
        val mockContext = mockk<Context>(relaxed = true) {
            every { getSharedPreferences(any<String>(), any()) } returns mockPrefs
        }
        every { mockSdkManager.appContext } returns mockContext
    }

    private fun setupMockSdkManager(
        screenLockManager: ScreenLockManager? = null,
        biometricAuthenticationManager: BiometricAuthenticationManager? = null,
        clientManager: ClientManager? = null
    ): SalesforceSDKManager {
        mockkObject(SalesforceSDKManager)
        val mockSdkManager = mockk<SalesforceSDKManager>(relaxed = true)
        every { SalesforceSDKManager.getInstance() } returns mockSdkManager
        screenLockManager?.let { every { mockSdkManager.screenLockManager } returns it }
        biometricAuthenticationManager?.let { every { mockSdkManager.biometricAuthenticationManager } returns it }
        clientManager?.let { every { mockSdkManager.clientManager } returns it }
        return mockSdkManager
    }

    private suspend fun callOnAuthFlowComplete(
        customTokenResponse: OAuth2.TokenEndpointResponse? = null,
        nativeLogin: Boolean = false,
        tokenMigration: Boolean = false,
        blockIntegrationUser: Boolean = false,
    ) {
        onAuthFlowComplete(
            tokenResponse = customTokenResponse ?: createTokenEndpointResponse(),
            loginServer = "https://login.salesforce.com",
            consumerKey = "test_consumer_key",
            onAuthFlowError = onAuthFlowError,
            onAuthFlowSuccess = onAuthFlowSuccess,
            buildAccountName = buildAccountName,
            nativeLogin = nativeLogin,
            tokenMigration = tokenMigration,
            context = testContext,
            userAccountManager = mockUserAccountManager,
            blockIntegrationUser = blockIntegrationUser,
            runtimeConfig = mockRuntimeConfig,
            updateLoggingPrefs = updateLoggingPrefs,
            fetchUserIdentity = fetchUserIdentity,
            startMainActivity = startMainActivity,
            setAdministratorPreferences = setAdministratorPreferences,
            addAccount = addAccount,
            handleScreenLockPolicy = handleScreenLockPolicy,
            handleBiometricAuthPolicy = handleBiometricAuthPolicy,
            handleDuplicateUserAccount = handleDuplicateUserAccount
        )
    }

    private fun createTokenEndpointResponse(
        accessToken: String = "test_access_token",
        refreshToken: String? = "test_refresh_token",
        instanceUrl: String = "https://test.salesforce.com",
        idUrl: String = "https://test.salesforce.com/id/00D000000000000EAA/005000000000000AAA",
        scope: String = "refresh_token id"
    ): OAuth2.TokenEndpointResponse {
        val params = mutableMapOf<String, String>(
            "access_token" to accessToken,
            "instance_url" to instanceUrl,
            "id" to idUrl,
            "scope" to scope
        )
        refreshToken?.let { params["refresh_token"] = it }
        return OAuth2.TokenEndpointResponse(params)
    }

    private fun createIdServiceResponse(
        username: String = "test@example.com",
        email: String = "test@example.com",
        firstName: String = "Test",
        lastName: String = "User",
        displayName: String = "Test User",
        nickname: String = "testuser",
        userType: String = "STANDARD",
        language: String = "en_US",
        locale: String = "en_US",
        lastModifiedDate: String = "2023-01-01T00:00:00Z",
        userId: String = "005000000000000AAA",
        organizationId: String = "00D000000000000EAA",
        idUrl: String = "https://test.salesforce.com/id/00D000000000000EAA/005000000000000AAA",
        active: Boolean = true,
        utcOffset: Int = -28800000,
        pictureUrl: String = "https://test.salesforce.com/profilephoto/005/F",
        thumbnailUrl: String = "https://test.salesforce.com/profilephoto/005/T",
        customPermissions: JSONObject? = null
    ): OAuth2.IdServiceResponse {
        return OAuth2.IdServiceResponse(JSONObject().apply {
            put("id", idUrl)
            put("username", username)
            put("email", email)
            put("first_name", firstName)
            put("last_name", lastName)
            put("display_name", displayName)
            put("nick_name", nickname)
            put("user_type", userType)
            put("language", language)
            put("locale", locale)
            put("last_modified_date", lastModifiedDate)
            put("user_id", userId)
            put("organization_id", organizationId)
            put("active", active)
            put("utcOffset", utcOffset)
            put("photos", JSONObject().apply {
                put("picture", pictureUrl)
                put("thumbnail", thumbnailUrl)
            })
            put("urls", JSONObject().apply {
                put("enterprise", "https://test.salesforce.com/services/Soap/c/{version}/00D000000000000EAA")
                put("metadata", "https://test.salesforce.com/services/Soap/m/{version}/00D000000000000EAA")
                put("partner", "https://test.salesforce.com/services/Soap/u/{version}/00D000000000000EAA")
                put("rest", "https://test.salesforce.com/services/data/v{version}/")
                put("sobjects", "https://test.salesforce.com/services/data/v{version}/sobjects/")
                put("search", "https://test.salesforce.com/services/data/v{version}/search/")
                put("query", "https://test.salesforce.com/services/data/v{version}/query/")
                put("recent", "https://test.salesforce.com/services/data/v{version}/recent/")
                put("profile", "https://test.salesforce.com/005000000000000AAA")
                put("feeds", "https://test.salesforce.com/services/data/v{version}/chatter/feeds")
                put("groups", "https://test.salesforce.com/services/data/v{version}/chatter/groups")
                put("users", "https://test.salesforce.com/services/data/v{version}/chatter/users")
                put("feed_items", "https://test.salesforce.com/services/data/v{version}/chatter/feed-items")
            })
            customPermissions?.let { put("custom_permissions", it) }
        })
    }
}