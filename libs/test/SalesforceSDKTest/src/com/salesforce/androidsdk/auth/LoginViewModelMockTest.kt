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
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.ui.LoginViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI

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

        // Mock OAuth2 and AuthenticationUtilities
        mockkStatic(OAuth2::class)
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
                tokenResponse = eq(mockTokenResponse),
                loginServer = eq(testServer),
                consumerKey = eq(bootConfig.remoteAccessConsumerKey),
                onAuthFlowError = any(),
                onAuthFlowSuccess = any(),
                buildAccountName = any(),
                nativeLogin = any(),
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
                tokenResponse = eq(mockTokenResponse),
                loginServer = eq(""),
                consumerKey = any(),
                onAuthFlowError = any(),
                onAuthFlowSuccess = any(),
                buildAccountName = any(),
                nativeLogin = any(),
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
    fun doCodeExchange_CallsExchangeCode_WithCorrectParameters() = runBlocking {
        val testServer = "https://test.salesforce.com"
        val testCode = "test_auth_code_123"
        val mockTokenResponse = mockk<TokenEndpointResponse>(relaxed = true)
        val mockOnError: (String, String?, Throwable?) -> Unit = mockk(relaxed = true)
        val mockOnSuccess: (UserAccount) -> Unit = mockk(relaxed = true)
        
        // Mock AuthenticationUtilities.onAuthFlowComplete to prevent actual execution
        coEvery {
            onAuthFlowComplete(
                tokenResponse = any(),
                loginServer = any(),
                consumerKey = any(),
                onAuthFlowError = any(),
                onAuthFlowSuccess = any(),
                buildAccountName = any(),
                nativeLogin = any(),
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
        
        // Mock exchangeCode to return our mock token response
        every {
            OAuth2.exchangeCode(any(), any(), any(), any(), any(), any())
        } returns mockTokenResponse
        
        // Set up the view model state
        viewModel.selectedServer.value = testServer
        Thread.sleep(100)
        
        // Call the method under test via onWebServerFlowComplete
        viewModel.onWebServerFlowComplete(testCode, mockOnError, mockOnSuccess)
        
        // Give time for the coroutine to execute
        Thread.sleep(200)
        
        // Verify exchangeCode was called with correct parameters
        verify {
            OAuth2.exchangeCode(
                HttpAccess.DEFAULT,
                URI.create(testServer),
                bootConfig.remoteAccessConsumerKey,
                testCode,
                viewModel.codeVerifier,
                bootConfig.oauthRedirectURI,
            )
        }
    }

    @Test
    fun doCodeExchange_WithFrontDoorBridge_UsesCorrectServerAndVerifier() = runBlocking {
        val frontDoorServer = "https://frontdoor.salesforce.com"
        val frontDoorUrl = "$frontDoorServer/frontdoor.jsp?sid=test_session"
        val frontDoorVerifier = "frontdoor_verifier_789"
        val testCode = "test_auth_code_123"
        val mockTokenResponse = mockk<TokenEndpointResponse>(relaxed = true)
        val mockOnError: (String, String?, Throwable?) -> Unit = mockk(relaxed = true)
        val mockOnSuccess: (UserAccount) -> Unit = mockk(relaxed = true)
        
        // Mock AuthenticationUtilities.onAuthFlowComplete
        coEvery {
            onAuthFlowComplete(
                tokenResponse = any(),
                loginServer = any(),
                consumerKey = any(),
                onAuthFlowError = any(),
                onAuthFlowSuccess = any(),
                buildAccountName = any(),
                nativeLogin = any(),
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
        
        // Mock exchangeCode
        every {
            OAuth2.exchangeCode(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns mockTokenResponse
        
        // Set up front door bridge
        viewModel.loginWithFrontDoorBridgeUrl(frontDoorUrl, frontDoorVerifier)
        Thread.sleep(100)
        
        // Call the method under test
        viewModel.onWebServerFlowComplete(testCode, mockOnError, mockOnSuccess)
        
        // Give time for the coroutine to execute
        Thread.sleep(200)
        
        // Verify exchangeCode uses frontdoor server and verifier
        verify {
            OAuth2.exchangeCode(
                HttpAccess.DEFAULT,
                URI.create(frontDoorServer),
                bootConfig.remoteAccessConsumerKey,
                testCode,
                frontDoorVerifier,
                bootConfig.oauthRedirectURI,
            )
        }
    }

    @Test
    fun doCodeExchange_WithNullCode_PassesNullToExchangeCode() = runBlocking {
        val testServer = "https://test.salesforce.com"
        val mockTokenResponse = mockk<TokenEndpointResponse>(relaxed = true)
        val mockOnError: (String, String?, Throwable?) -> Unit = mockk(relaxed = true)
        val mockOnSuccess: (UserAccount) -> Unit = mockk(relaxed = true)
        
        // Mock AuthenticationUtilities.onAuthFlowComplete
        coEvery {
            onAuthFlowComplete(
                tokenResponse = any(),
                loginServer = any(),
                consumerKey = any(),
                onAuthFlowError = any(),
                onAuthFlowSuccess = any(),
                buildAccountName = any(),
                nativeLogin = any(),
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
        
        // Mock exchangeCode
        every {
            OAuth2.exchangeCode(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns mockTokenResponse
        
        // Set up the view model state
        viewModel.selectedServer.value = testServer
        Thread.sleep(100)
        
        // Call with null code
        viewModel.onWebServerFlowComplete(null, mockOnError, mockOnSuccess)
        
        // Give time for the coroutine to execute
        Thread.sleep(200)
        
        // Verify exchangeCode was called with null code
        verify {
            OAuth2.exchangeCode(
                HttpAccess.DEFAULT,
                URI.create(testServer),
                bootConfig.remoteAccessConsumerKey,
                null,
                viewModel.codeVerifier,
                bootConfig.oauthRedirectURI,
            )
        }
    }
}
