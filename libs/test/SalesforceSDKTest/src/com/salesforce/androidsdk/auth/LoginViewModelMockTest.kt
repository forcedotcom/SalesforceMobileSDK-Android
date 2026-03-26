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

import android.webkit.CookieManager
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.accounts.UserAccountTest
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.security.BiometricAuthenticationManager
import com.salesforce.androidsdk.ui.LoginViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for LoginViewModel that require mocking.
 * These tests are separated from LoginViewModelTest to isolate mock usage.
 */
@RunWith(AndroidJUnit4::class)
class LoginViewModelMockTest {
    @get:Rule
    val instantExecutorRule: InstantTaskExecutorRule = InstantTaskExecutorRule()

    private val context = InstrumentationRegistry.getInstrumentation().context
    private val bootConfig = BootConfig.getBootConfig(context)
    private lateinit var viewModel: LoginViewModel
    private lateinit var mockCookieManager: CookieManager

    @Before
    fun setup() {
        // Mock CookieManager to avoid WebView dependencies
        mockkStatic(CookieManager::class)
        mockCookieManager = mockk<CookieManager>(relaxed = true)
        every { CookieManager.getInstance() } returns mockCookieManager

        // Mock AuthenticationUtilities
        mockkStatic("com.salesforce.androidsdk.auth.AuthenticationUtilitiesKt")

        // Create view model after mocking
        viewModel = LoginViewModel(bootConfig)
        
        // This is required for the LiveData to actually update during the test
        viewModel.selectedServer.observeForever { }
        viewModel.loginUrl.observeForever { }
        
        // Give the LiveData sources time to propagate
        Thread.sleep(100)
    }

    @After
    fun teardown() {
        SalesforceSDKManager.getInstance().loginServerManager.reset()
        SalesforceSDKManager.getInstance().debugOverrideAppConfig = null
        unmockkAll()
    }

    @Test
    fun onAuthFlowComplete_CallsAuthenticationUtilities_WithCorrectParameters() = runBlocking {
        // Mock the AuthenticationUtilities.onAuthFlowComplete function
        
        // Mock the function to do nothing (just capture parameters)
        coEvery {
            onAuthFlowComplete(
                tokenResponse = any(),
                loginServer = any(),
                consumerKey = any(),
                onAuthFlowError = any(),
                onAuthFlowSuccess = any(),
                buildAccountName = any(),
                nativeLogin = any(),
                tokenMigration = any(),
                context = any(),
                userAccountManager = any(),
                blockIntegrationUser = any(),
                runtimeConfig = any(),
                updateLoggingPrefs = any(),
                fetchUserIdentity = any(),
                startMainActivity = any(),
                setAdministratorPreferences = any(),
                addAccount = any(),
                handleScreenLockPolicy = any(),
                handleBiometricAuthPolicy = any(),
                handleDuplicateUserAccount = any(),
            )
        } returns Unit
        
        // Create test data
        val testServer = "https://test.salesforce.com"
        val mockTokenResponse = mockk<TokenEndpointResponse>(relaxed = true)
        val mockOnError: (String, String?, Throwable?) -> Unit = mockk(relaxed = true)
        val mockOnSuccess: (UserAccount) -> Unit = mockk(relaxed = true)
        
        // Set up the view model state
        viewModel.selectedServer.value = testServer
        Thread.sleep(100)
        
        // Call the method under test
        viewModel.onAuthFlowComplete(mockTokenResponse, mockOnError, mockOnSuccess)
        
        // Verify AuthenticationUtilities.onAuthFlowComplete was called with correct parameters
        coVerify {
            onAuthFlowComplete(
                tokenResponse = mockTokenResponse,
                loginServer = testServer,
                consumerKey = bootConfig.remoteAccessConsumerKey,
                onAuthFlowError = any(),
                onAuthFlowSuccess = any(),
                buildAccountName = any(),
                nativeLogin = any(),
                tokenMigration = false,
                context = any(),
                userAccountManager = any(),
                blockIntegrationUser = any(),
                runtimeConfig = any(),
                updateLoggingPrefs = any(),
                fetchUserIdentity = any(),
                startMainActivity = any(),
                setAdministratorPreferences = any(),
                addAccount = any(),
                handleScreenLockPolicy = any(),
                handleBiometricAuthPolicy = any(),
                handleDuplicateUserAccount = any(),
            )
        }
    }

    @Test
    fun onAuthFlowComplete_CallsAuthenticationUtilitiesSuccessfully() = runBlocking {
        // Mock the AuthenticationUtilities.onAuthFlowComplete function
        
        coEvery {
            onAuthFlowComplete(
                tokenResponse = any(),
                loginServer = any(),
                consumerKey = any(),
                onAuthFlowError = any(),
                onAuthFlowSuccess = any(),
                buildAccountName = any(),
                nativeLogin = any(),
                tokenMigration = any(),
                context = any(),
                userAccountManager = any(),
                blockIntegrationUser = any(),
                runtimeConfig = any(),
                updateLoggingPrefs = any(),
                fetchUserIdentity = any(),
                startMainActivity = any(),
                setAdministratorPreferences = any(),
                addAccount = any(),
                handleScreenLockPolicy = any(),
                handleBiometricAuthPolicy = any(),
                handleDuplicateUserAccount = any(),
            )
        } returns Unit
        
        val mockTokenResponse = mockk<TokenEndpointResponse>(relaxed = true)
        val mockOnError: (String, String?, Throwable?) -> Unit = mockk(relaxed = true)
        val mockOnSuccess: (UserAccount) -> Unit = mockk(relaxed = true)
        
        // Set up the view model state
        viewModel.selectedServer.value = "https://test.salesforce.com"
        Thread.sleep(100)
        
        // Call the method under test
        viewModel.onAuthFlowComplete(mockTokenResponse, mockOnError, mockOnSuccess)

        // Verify onAuthFlowComplete was called, which confirms the method executed successfully
        coVerify {
            mockCookieManager.removeAllCookies(null)
            onAuthFlowComplete(
                tokenResponse = mockTokenResponse,
                loginServer = any(),
                consumerKey = any(),
                onAuthFlowError = any(),
                onAuthFlowSuccess = any(),
                buildAccountName = any(),
                nativeLogin = any(),
                tokenMigration = false,
                context = any(),
                userAccountManager = any(),
                blockIntegrationUser = any(),
                runtimeConfig = any(),
                updateLoggingPrefs = any(),
                fetchUserIdentity = any(),
                startMainActivity = any(),
                setAdministratorPreferences = any(),
                addAccount = any(),
                handleScreenLockPolicy = any(),
                handleBiometricAuthPolicy = any(),
                handleDuplicateUserAccount = any(),
            )
        }
    }

    @Test
    fun onAuthFlowComplete_ResetsAuthCodeForJwtFlow() = runBlocking {
        // Mock the AuthenticationUtilities.onAuthFlowComplete function
        
        coEvery {
            onAuthFlowComplete(
                tokenResponse = any(),
                loginServer = any(),
                consumerKey = any(),
                onAuthFlowError = any(),
                onAuthFlowSuccess = any(),
                buildAccountName = any(),
                nativeLogin = any(),
                tokenMigration = any(),
                context = any(),
                userAccountManager = any(),
                blockIntegrationUser = any(),
                runtimeConfig = any(),
                updateLoggingPrefs = any(),
                fetchUserIdentity = any(),
                startMainActivity = any(),
                setAdministratorPreferences = any(),
                addAccount = any(),
                handleScreenLockPolicy = any(),
                handleBiometricAuthPolicy = any(),
                handleDuplicateUserAccount = any(),
            )
        } returns Unit
        
        val mockTokenResponse = mockk<TokenEndpointResponse>(relaxed = true)
        val mockOnError: (String, String?, Throwable?) -> Unit = mockk(relaxed = true)
        val mockOnSuccess: (UserAccount) -> Unit = mockk(relaxed = true)
        
        // Set up the view model state with JWT flow
        viewModel.selectedServer.value = "https://test.salesforce.com"
        viewModel.authCodeForJwtFlow = "test_jwt_auth_code"
        Thread.sleep(100)
        
        // Verify authCodeForJwtFlow is set
        assertNotNull("authCodeForJwtFlow should be set before call", viewModel.authCodeForJwtFlow)
        
        // Call the method under test
        viewModel.onAuthFlowComplete(mockTokenResponse, mockOnError, mockOnSuccess)
        
        // Verify authCodeForJwtFlow is reset to null
        assertNull("authCodeForJwtFlow should be null after call", viewModel.authCodeForJwtFlow)
    }

    @Test
    fun onAuthFlowComplete_UsesEmptyString_WhenSelectedServerIsNull() = runBlocking {
        // Mock the AuthenticationUtilities.onAuthFlowComplete function
        
        coEvery {
            onAuthFlowComplete(
                tokenResponse = any(),
                loginServer = any(),
                consumerKey = any(),
                onAuthFlowError = any(),
                onAuthFlowSuccess = any(),
                buildAccountName = any(),
                nativeLogin = any(),
                tokenMigration = any(),
                context = any(),
                userAccountManager = any(),
                blockIntegrationUser = any(),
                runtimeConfig = any(),
                updateLoggingPrefs = any(),
                fetchUserIdentity = any(),
                startMainActivity = any(),
                setAdministratorPreferences = any(),
                addAccount = any(),
                handleScreenLockPolicy = any(),
                handleBiometricAuthPolicy = any(),
                handleDuplicateUserAccount = any(),
            )
        } returns Unit
        
        val mockTokenResponse = mockk<TokenEndpointResponse>(relaxed = true)
        val mockOnError: (String, String?, Throwable?) -> Unit = mockk(relaxed = true)
        val mockOnSuccess: (UserAccount) -> Unit = mockk(relaxed = true)
        
        // Set selectedServer to null
        viewModel.selectedServer.value = null
        Thread.sleep(100)
        
        // Call the method under test
        viewModel.onAuthFlowComplete(mockTokenResponse, mockOnError, mockOnSuccess)
        
        // Verify empty string is used when selectedServer is null
        coVerify {
            onAuthFlowComplete(
                tokenResponse = mockTokenResponse,
                loginServer = "",
                consumerKey = any(),
                onAuthFlowError = any(),
                onAuthFlowSuccess = any(),
                buildAccountName = any(),
                nativeLogin = any(),
                tokenMigration = false,
                context = any(),
                userAccountManager = any(),
                blockIntegrationUser = any(),
                runtimeConfig = any(),
                updateLoggingPrefs = any(),
                fetchUserIdentity = any(),
                startMainActivity = any(),
                setAdministratorPreferences = any(),
                addAccount = any(),
                handleScreenLockPolicy = any(),
                handleBiometricAuthPolicy = any(),
                handleDuplicateUserAccount = any(),
            )
        }
    }

    @Test
    fun onWebServerFlowComplete_CallsDoCodeExchange_WithCorrectParameters() = runBlocking {
        val testServer = "https://test.salesforce.com"
        val testCode = "test_auth_code_123"
        val mockOnError: (String, String?, Throwable?) -> Unit = mockk(relaxed = true)
        val mockOnSuccess: (UserAccount) -> Unit = mockk(relaxed = true)
        
        // Create a spy of viewModel to verify and mock doCodeExchange
        val spyViewModel = spyk(viewModel)
        
        // Mock doCodeExchange to prevent actual execution
        coEvery {
            spyViewModel.doCodeExchange(any(), any(), any(), any(), any())
        } returns Result.success(Unit)

        // Set up the view model state
        spyViewModel.selectedServer.value = testServer
        Thread.sleep(100)
        
        // Call the method under test
        spyViewModel.onWebServerFlowComplete(testCode, mockOnError, mockOnSuccess)
        
        // Give time for the coroutine to execute
        Thread.sleep(200)
        
        // Verify doCodeExchange was called with correct parameters
        coVerify {
            spyViewModel.doCodeExchange(
                testCode,
                mockOnError,
                mockOnSuccess,
                loginServer = null,
                tokenMigration = false,
            )
        }
    }

    @Test
    fun onTokenMigration_CallsDoCodeExchange_WithCorrectParameters() = runBlocking {
        val testServer = "https://test.salesforce.com"
        val testCode = "test_auth_code_123"
        val mockOnError: (String, String?, Throwable?) -> Unit = mockk(relaxed = true)
        val mockOnSuccess: (UserAccount) -> Unit = mockk(relaxed = true)
        val migrationLoginServer = "migration_login_server"

        // Create a spy of viewModel to verify and mock doCodeExchange
        val spyViewModel = spyk(viewModel)

        // Mock doCodeExchange to prevent actual execution
        coEvery {
            spyViewModel.doCodeExchange(any(), any(), any(), any(), any())
        } returns Result.success(Unit)

        // Set up the view model state
        spyViewModel.selectedServer.value = testServer
        Thread.sleep(100)

        // Call the method under test
        spyViewModel.onWebServerFlowComplete(
            testCode,
            mockOnError,
            mockOnSuccess,
            migrationLoginServer,
            tokenMigration = true,
        )

        // Give time for the coroutine to execute
        Thread.sleep(200)

        // Verify doCodeExchange was called with correct parameters
        coVerify {
            spyViewModel.doCodeExchange(
                testCode,
                mockOnError,
                mockOnSuccess,
                loginServer = migrationLoginServer,
                tokenMigration = true,
            )
        }
    }

    @Test
    fun onWebServerFlowComplete_WithFrontDoorBridge_UsesCorrectServerAndVerifier() = runBlocking {
        val frontDoorServer = "https://frontdoor.salesforce.com"
        val frontDoorUrl = "$frontDoorServer/frontdoor.jsp?sid=test_session"
        val frontDoorVerifier = "frontdoor_verifier_789"
        val testCode = "test_auth_code_123"
        val mockOnError: (String, String?, Throwable?) -> Unit = mockk(relaxed = true)
        val mockOnSuccess: (UserAccount) -> Unit = mockk(relaxed = true)
        
        // Create a spy of viewModel to verify and mock doCodeExchange
        val spyViewModel = spyk(viewModel)
        
        // Mock doCodeExchange to prevent actual execution
        coEvery {
            spyViewModel.doCodeExchange(any(), any(), any(), any(), any())
        } returns Result.success(Unit)

        // Set up front door bridge
        spyViewModel.loginWithFrontDoorBridgeUrl(frontDoorUrl, frontDoorVerifier)
        Thread.sleep(100)
        
        // Call the method under test
        spyViewModel.onWebServerFlowComplete(testCode, mockOnError, mockOnSuccess)
        
        // Give time for the coroutine to execute
        Thread.sleep(200)

        // Verify doCodeExchange was called with null loginServer and false tokenMigration
        coVerify {
            spyViewModel.doCodeExchange(
                testCode,
                mockOnError,
                mockOnSuccess,
                loginServer = null,
                tokenMigration = false,
            )
        }
    }

    @Test
    fun onWebServerFlowComplete_WithNullCode_CallsDoCodeExchangeWithNull() = runBlocking {
        val testServer = "https://test.salesforce.com"
        val mockOnError: (String, String?, Throwable?) -> Unit = mockk(relaxed = true)
        val mockOnSuccess: (UserAccount) -> Unit = mockk(relaxed = true)
        
        // Create a spy of viewModel to verify and mock doCodeExchange
        val spyViewModel = spyk(viewModel)
        
        // Mock doCodeExchange to prevent actual execution
        coEvery {
            spyViewModel.doCodeExchange(any(), any(), any(), any(), any())
        } returns Result.success(Unit)

        // Set up the view model state
        spyViewModel.selectedServer.value = testServer
        Thread.sleep(100)
        
        // Call with null code
        spyViewModel.onWebServerFlowComplete(null, mockOnError, mockOnSuccess)
        
        // Give time for the coroutine to execute
        Thread.sleep(200)

        // Verify doCodeExchange was called with null code, null loginServer, and false tokenMigration
        coVerify {
            spyViewModel.doCodeExchange(
                null,
                mockOnError,
                mockOnSuccess,
                loginServer = null,
                tokenMigration = false,
            )
        }
    }

    // region Token Migration Tests

    @Test
    fun onAuthFlowComplete_WithTokenMigration_PassesCorrectParameters() = runBlocking {
        // Mock the AuthenticationUtilities.onAuthFlowComplete function
        coEvery {
            onAuthFlowComplete(
                tokenResponse = any(),
                loginServer = any(),
                consumerKey = any(),
                onAuthFlowError = any(),
                onAuthFlowSuccess = any(),
                buildAccountName = any(),
                nativeLogin = any(),
                tokenMigration = any(),
                context = any(),
                userAccountManager = any(),
                blockIntegrationUser = any(),
                runtimeConfig = any(),
                updateLoggingPrefs = any(),
                fetchUserIdentity = any(),
                startMainActivity = any(),
                setAdministratorPreferences = any(),
                addAccount = any(),
                handleScreenLockPolicy = any(),
                handleBiometricAuthPolicy = any(),
                handleDuplicateUserAccount = any(),
            )
        } returns Unit

        val mockTokenResponse = mockk<TokenEndpointResponse>(relaxed = true)
        val mockOnError: (String, String?, Throwable?) -> Unit = mockk(relaxed = true)
        val mockOnSuccess: (UserAccount) -> Unit = mockk(relaxed = true)

        // Set up the view model state
        viewModel.selectedServer.value = "https://test.salesforce.com"
        Thread.sleep(100)

        // Call the method under test with tokenMigration = true
        viewModel.onAuthFlowComplete(mockTokenResponse, mockOnError, mockOnSuccess, tokenMigration = true)

        // Verify tokenMigration parameter is passed as true
        coVerify {
            onAuthFlowComplete(
                tokenResponse = mockTokenResponse,
                loginServer = any(),
                consumerKey = any(),
                onAuthFlowError = any(),
                onAuthFlowSuccess = any(),
                buildAccountName = any(),
                nativeLogin = any(),
                tokenMigration = true,
                context = any(),
                userAccountManager = any(),
                blockIntegrationUser = any(),
                runtimeConfig = any(),
                updateLoggingPrefs = any(),
                fetchUserIdentity = any(),
                startMainActivity = any(),
                setAdministratorPreferences = any(),
                addAccount = any(),
                handleScreenLockPolicy = any(),
                handleBiometricAuthPolicy = any(),
                handleDuplicateUserAccount = any(),
            )
        }
    }

    @Test
    fun onWebServerFlowComplete_WithTokenMigration_PassesCorrectParameters() = runBlocking {
        val customLoginServer = "https://custom.salesforce.com"
        val testCode = "test_auth_code"
        val mockOnError: (String, String?, Throwable?) -> Unit = mockk(relaxed = true)
        val mockOnSuccess: (UserAccount) -> Unit = mockk(relaxed = true)

        // Create a spy of viewModel to verify and mock doCodeExchange
        val spyViewModel = spyk(viewModel)
        
        // Mock doCodeExchange to prevent actual execution
        coEvery {
            spyViewModel.doCodeExchange(any(), any(), any(), any(), any())
        } returns Result.success(Unit)

        // Set up the view model state with different server
        spyViewModel.selectedServer.value = "https://different.salesforce.com"
        Thread.sleep(100)

        // Call the method under test with custom loginServer and tokenMigration
        spyViewModel.onWebServerFlowComplete(
            testCode,
            mockOnError,
            mockOnSuccess,
            loginServer = customLoginServer,
            tokenMigration = true
        )

        // Give time for the coroutine to execute
        Thread.sleep(200)

        // Verify doCodeExchange was called with the correct loginServer and tokenMigration
        coVerify {
            spyViewModel.doCodeExchange(
                testCode,
                mockOnError,
                mockOnSuccess,
                loginServer = customLoginServer,
                tokenMigration = true,
            )
        }
    }

    @Test
    fun doCodeExchange_UtilizesOAuth2_AndFinishesAuth() = runBlocking {
        val testCode = "test_auth_code"
        val mockOnError: (String, String?, Throwable?) -> Unit = mockk(relaxed = true)
        val mockOnSuccess: (UserAccount) -> Unit = mockk(relaxed = true)
        val mockTokenResponse: TokenEndpointResponse = mockk(relaxed = true)

        // Create a spy of viewModel to verify and mock doCodeExchange
        val spyViewModel = spyk(viewModel)

        // Force OAuth2 class initialization before mocking to avoid ExceptionInInitializerError
        OAuth2.TIMESTAMP_FORMAT
        mockkStatic(OAuth2::class)
        every {
            OAuth2.exchangeCode(any(), any(), any(), any(), any(), any())
        } returns mockTokenResponse

        // Mock doCodeExchange to prevent actual execution
        coEvery {
            spyViewModel.onAuthFlowComplete(any(), any(), any(), any(), any())
        } just runs

        // Set up required state
        spyViewModel.selectedServer.value = "https://test.salesforce.com"
        Thread.sleep(100)

        // Call function under test
        spyViewModel.doCodeExchange(
            testCode,
            mockOnError,
            mockOnSuccess,
        )

        // Give time for the coroutine to execute
        Thread.sleep(200)

        coVerify {
            spyViewModel.onAuthFlowComplete(
                mockTokenResponse,
                mockOnError,
                mockOnSuccess,
                tokenMigration = false,
                loginServer = "https://test.salesforce.com",
            )
        }
    }

    @Test
    fun doCodeExchange_TokenMigration_PassesCorrectValues() = runBlocking {
        val testCode = "test_auth_code"
        val mockOnError: (String, String?, Throwable?) -> Unit = mockk(relaxed = true)
        val mockOnSuccess: (UserAccount) -> Unit = mockk(relaxed = true)
        val mockTokenResponse: TokenEndpointResponse = mockk(relaxed = true)
        val migrationServer = "migration_server"

        // Create a spy of viewModel to verify and mock doCodeExchange
        val spyViewModel = spyk(viewModel)

        // Force OAuth2 class initialization before mocking to avoid ExceptionInInitializerError
        OAuth2.TIMESTAMP_FORMAT
        mockkStatic(OAuth2::class)
        every {
            OAuth2.exchangeCode(any(), any(), any(), any(), any(), any())
        } returns mockTokenResponse

        // Mock doCodeExchange to prevent actual execution
        coEvery {
            spyViewModel.onAuthFlowComplete(any(), any(), any(), any(), any())
        } just runs

        // Set up required state
        spyViewModel.selectedServer.value = "https://test.salesforce.com"
        Thread.sleep(100)

        // Call function under test
        spyViewModel.doCodeExchange(
            testCode,
            mockOnError,
            mockOnSuccess,
            migrationServer,
            tokenMigration = true,
        )

        // Give time for the coroutine to execute
        Thread.sleep(200)

        coVerify {
            spyViewModel.onAuthFlowComplete(
                mockTokenResponse,
                mockOnError,
                mockOnSuccess,
                tokenMigration = true,
                loginServer = migrationServer,
            )
        }
    }

    // endregion

    // region showBiometricAuthenticationButton Tests

    @Test
    fun showBiometricAuthenticationButton_ReturnsFalse_ByDefault() {
        assertFalse(
            "Should not be biometric locked by default.",
            viewModel.showBiometricAuthenticationButton.value
        )
    }

    @Test
    fun showBiometricAuthenticationButton_ReturnsFalse_ForNativeLoginUser() {
        val bioAuthManager = SalesforceSDKManager.getInstance().biometricAuthenticationManager
                as BiometricAuthenticationManager
        val nativeLoginUser = UserAccountBuilder.getInstance()
            .populateFromUserAccount(UserAccountTest.createTestAccount())
            .nativeLogin(isNativeLogin = true)
            .build()
        UserAccountManager.getInstance().createAccount(nativeLoginUser)
        bioAuthManager.storeMobilePolicy(nativeLoginUser, enabled = true, timeout = 15)
        bioAuthManager.biometricOptIn(true)
        bioAuthManager.lock()

        assertFalse(
            "Should not report biometric locked for native login user.",
            viewModel.showBiometricAuthenticationButton.value
        )

        bioAuthManager.locked = false
        bioAuthManager.cleanUp(nativeLoginUser)
    }

    @Test
    fun showBiometricAuthenticationButton_ReturnsTrue_ForNonNativeLoginUser() {
        val bioAuthManager = SalesforceSDKManager.getInstance().biometricAuthenticationManager
                as BiometricAuthenticationManager
        val account = UserAccountTest.createTestAccount()
        UserAccountManager.getInstance().createAccount(account)
        bioAuthManager.storeMobilePolicy(account, enabled = true, timeout = 15)
        bioAuthManager.biometricOptIn(true)
        bioAuthManager.lock()

        assertTrue(
            "Should report biometric locked for non-native login user.",
            viewModel.showBiometricAuthenticationButton.value
        )

        bioAuthManager.locked = false
        bioAuthManager.cleanUp(account)
    }

    @Test
    fun showBiometricAuthenticationButton_ReturnsFalse_WhenNotOptedIn() {
        val bioAuthManager = SalesforceSDKManager.getInstance().biometricAuthenticationManager
                as BiometricAuthenticationManager
        val account = UserAccountTest.createTestAccount()
        UserAccountManager.getInstance().createAccount(account)
        bioAuthManager.storeMobilePolicy(account, enabled = true, timeout = 15)
        // Not opted in.
        bioAuthManager.lock()

        assertFalse(
            "Should not report biometric locked when not opted in.",
            viewModel.showBiometricAuthenticationButton.value
        )

        bioAuthManager.locked = false
        bioAuthManager.cleanUp(account)
    }

    @Test
    fun showBiometricAuthenticationButton_ReturnsFalse_WhenNotLocked() {
        val bioAuthManager = SalesforceSDKManager.getInstance().biometricAuthenticationManager
                as BiometricAuthenticationManager
        val account = UserAccountTest.createTestAccount()
        UserAccountManager.getInstance().createAccount(account)
        bioAuthManager.storeMobilePolicy(account, enabled = true, timeout = 15)
        bioAuthManager.biometricOptIn(true)
        // Not locked.

        assertFalse(
            "Should not report biometric locked when not locked.",
            viewModel.showBiometricAuthenticationButton.value
        )

        bioAuthManager.cleanUp(account)
    }

    // endregion
}
