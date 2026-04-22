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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.core.net.toUri
import androidx.lifecycle.MediatorLiveData
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.R.string.oauth_display_type
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.OAuth2.getFrontdoorUrl
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.config.LoginServerManager.LoginServer
import com.salesforce.androidsdk.config.LoginServerManager.WELCOME_LOGIN_URL
import com.salesforce.androidsdk.config.OAuthConfig
import com.salesforce.androidsdk.security.SalesforceKeyGenerator.getSHA256Hash
import com.salesforce.androidsdk.ui.LoginActivity.Companion.ABOUT_BLANK
import com.salesforce.androidsdk.ui.LoginViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI

private const val FAKE_SERVER_URL = "shouldMatchNothing.salesforce.com"
private const val FAKE_JWT = "1234"
private const val FAKE_JWT_FLOW_AUTH = "5678"
private const val TEST_ATTESTATION_SERVER = "test.salesforce.com"
private const val TEST_CHALLENGE_VALUE = "__TEST_CHALLENGE_VALUE__"
private const val TEST_APP_ATTESTATION = "__TEST_APP_ATTESTATION__"
private const val ATTESTATION_QUERY_PARAM_PREFIX = "attestation="

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class LoginViewModelTest {
    @get:Rule
    val instantExecutorRule: InstantTaskExecutorRule = InstantTaskExecutorRule()

    private val context = InstrumentationRegistry.getInstrumentation().context
    private val bootConfig = BootConfig.getBootConfig(context)
    private val viewModel = LoginViewModel(bootConfig)

    @Before
    fun setup() {
        // This is required for the LiveData to actually update during the test
        // because it isn't actually being observed since there is no lifecycle.
        viewModel.pendingServer.observeForever {
            // This completes the validation of the pending login server usually performed by the login activity before setting selected server.
            viewModel.selectedServer.value = it
        }
        viewModel.selectedServer.observeForever { }
        viewModel.loginUrl.observeForever { }

        // Give the LiveData sources time to propagate through the MediatorLiveData
        Thread.sleep(100)
    }

    @After
    fun teardown() {
        SalesforceSDKManager.getInstance().loginServerManager.reset()
        SalesforceSDKManager.getInstance().debugOverrideAppConfig = null
    }

    // Google's recommended naming scheme for view model test is "thingUnderTest_TriggerOfTest_ResultOfTest"
    @Test
    fun selectedServer_UpdatesOn_loginServerManagerChange() {
        val loginServerManager = SalesforceSDKManager.getInstance().loginServerManager
        assertEquals(loginServerManager.selectedLoginServer.url, viewModel.selectedServer.value)
        assertNotEquals(FAKE_SERVER_URL, viewModel.selectedServer.value)

        loginServerManager.addCustomLoginServer("fake", FAKE_SERVER_URL)
        assertEquals(FAKE_SERVER_URL, viewModel.selectedServer.value)
    }

    @Test
    fun defaultTitleText_Init_HasExpectedDefault() {
        val regex = "https://login.salesforce.com".toRegex()
        assertTrue(regex.matches(viewModel.defaultTitleText))
    }

    @Test
    fun defaultTitleText_UpdatesOn_LoginUrlIsAboutBlank() {
        viewModel.loginUrl.value = ABOUT_BLANK
        assertEquals("", viewModel.defaultTitleText)
    }

    @Test
    fun defaultTitle_UpdatesOn_loginUrlIsCustom() {
        val customLoginUrl = "https://www.example.com"

        SalesforceSDKManager.getInstance().loginServerManager.setSelectedLoginServer(
            LoginServer(
                "Example",
                customLoginUrl,
                true
            )
        )

        assertEquals(customLoginUrl, viewModel.defaultTitleText)
    }

    @Test
    fun loginUrl_UpdatesOn_selectedServerChange() {
        // Wait for initial values to be set
        assertNotNull(viewModel.selectedServer.value)
        assertNotNull(viewModel.loginUrl.value)

        assertNotEquals(FAKE_SERVER_URL, viewModel.selectedServer.value)
        assertTrue(viewModel.loginUrl.value!!.startsWith(viewModel.selectedServer.value!!))
        assertFalse(viewModel.loginUrl.value!!.contains(FAKE_SERVER_URL))

        viewModel.selectedServer.value = FAKE_SERVER_URL

        // Wait for loginUrl to update after selectedServer change (async coroutine)
        Thread.sleep(200)
        assertNotNull(viewModel.loginUrl.value)
        // LoginUrlSource prepends https:// to scheme-less servers before URL generation.
        assertTrue(viewModel.loginUrl.value!!.startsWith("https://$FAKE_SERVER_URL"))
    }

    // region Login for Admin (browserCustomTabUrl) Tests

    @Test
    fun browserCustomTabUrl_IsPopulated_AfterAuthorizationUrlGeneration() {
        // Observe so the MediatorLiveData actually propagates in the test environment.
        viewModel.browserCustomTabUrl.observeForever { }

        // The setup() already triggers URL generation; wait for async completion.
        Thread.sleep(200)

        val browserCustomTabUrl = viewModel.browserCustomTabUrl.value
        assertNotNull("browserCustomTabUrl should be populated for the admin flow", browserCustomTabUrl)
        assertTrue(
            "browserCustomTabUrl should start with the selected server",
            browserCustomTabUrl!!.startsWith(viewModel.selectedServer.value!!)
        )
        assertTrue(
            "browserCustomTabUrl should point at the OAuth authorize endpoint",
            browserCustomTabUrl.contains("/services/oauth2/authorize")
        )
    }

    @Test
    fun browserCustomTabUrl_UsesWebServerFlow_EvenWhenUserAgentFlowIsActive() {
        viewModel.browserCustomTabUrl.observeForever { }
        viewModel.loginUrl.observeForever { }

        try {
            // Force User Agent flow for the WebView.
            SalesforceSDKManager.getInstance().useWebServerAuthentication = false

            viewModel.reloadWebView()
            Thread.sleep(200)

            val browserCustomTabUrl = viewModel.browserCustomTabUrl.value
            val loginUrl = viewModel.loginUrl.value
            assertNotNull("browserCustomTabUrl should always be generated", browserCustomTabUrl)
            assertNotNull("loginUrl should be generated", loginUrl)
            assertFalse( browserCustomTabUrl == loginUrl)

            // browserCustomTabUrl is the Web Server Flow URL, so it must carry a PKCE code challenge
            // and request a 'code' response type (this is what the admin custom tab needs to complete
            // via the onNewIntent -> completeAdvAuthFlow path).
            assertTrue(
                "browserCustomTabUrl should use response_type=code. URL: $browserCustomTabUrl",
                browserCustomTabUrl!!.contains("response_type=code")
            )
            assertTrue(
                "browserCustomTabUrl should include a PKCE code_challenge. URL: $browserCustomTabUrl",
                browserCustomTabUrl.contains("code_challenge=")
            )

            // In User Agent mode the WebView URL differs from the browser tab URL: it must not be
            // the Web Server Flow (i.e., no response_type=code). The exact response_type depends on
            // whether hybrid authentication is enabled (`token` vs `hybrid_token`), which is not
            // relevant to the admin-flow contract we're validating here.
            assertFalse(
                "loginUrl should NOT use response_type=code when User Agent flow is active. URL: $loginUrl",
                loginUrl!!.contains("response_type=code")
            )
        } finally {
            SalesforceSDKManager.getInstance().useWebServerAuthentication = true
        }
    }

    @Test
    fun browserCustomTabUrl_UpdatesOn_selectedServerChange() {
        viewModel.browserCustomTabUrl.observeForever { }

        // Wait for initial generation.
        Thread.sleep(200)
        val initialUrl = viewModel.browserCustomTabUrl.value
        assertNotNull(initialUrl)
        assertFalse(
            "Initial browserCustomTabUrl should not reference the fake server",
            initialUrl!!.contains(FAKE_SERVER_URL)
        )

        viewModel.selectedServer.value = FAKE_SERVER_URL
        Thread.sleep(200)

        val updatedUrl = viewModel.browserCustomTabUrl.value
        assertNotNull(updatedUrl)
        assertFalse(initialUrl == updatedUrl)
        // LoginUrlSource prepends https:// to scheme-less servers before URL generation.
        assertTrue(
            "browserCustomTabUrl should start with the new server after selectedServer change",
            updatedUrl!!.startsWith("https://$FAKE_SERVER_URL")
        )
    }

    @Test
    fun generateAuthorizationUrl_InvokesOnBrowserCustomTabReady_WhenBrowserLoginEnabled() {
        val sdkManagerMock = mockk<SalesforceSDKManager>(relaxed = true)
        every { sdkManagerMock.isDebugBuild } returns false
        every { sdkManagerMock.useHybridAuthentication } returns false
        every { sdkManagerMock.isBrowserLoginEnabled } returns true
        every { sdkManagerMock.appConfigForLoginHost } returns { _ -> null }
        every { sdkManagerMock.debugOverrideAppConfig } returns null

        val capturedUrls = mutableListOf<String>()
        viewModel.onBrowserCustomTabReady = { url -> capturedUrls.add(url) }

        runBlocking { viewModel.generateAuthorizationUrl("test.salesforce.com", sdkManagerMock) }

        assertEquals(1, capturedUrls.size)
        assertEquals(viewModel.browserCustomTabUrl.value, capturedUrls.single())
    }

    @Test
    fun generateAuthorizationUrl_InvokesOnBrowserCustomTabReady_WhenSingleServerCustomTabActivity() {
        val vm = object : LoginViewModel(bootConfig) {
            override val singleServerCustomTabActivity = true
        }

        val sdkManagerMock = mockk<SalesforceSDKManager>(relaxed = true)
        every { sdkManagerMock.isDebugBuild } returns false
        every { sdkManagerMock.useHybridAuthentication } returns false
        // Explicitly NOT browser-login-enabled — singleServerCustomTabActivity alone must trigger.
        every { sdkManagerMock.isBrowserLoginEnabled } returns false
        every { sdkManagerMock.appConfigForLoginHost } returns { _ -> null }
        every { sdkManagerMock.debugOverrideAppConfig } returns null

        val capturedUrls = mutableListOf<String>()
        vm.onBrowserCustomTabReady = { url -> capturedUrls.add(url) }

        runBlocking { vm.generateAuthorizationUrl("test.salesforce.com", sdkManagerMock) }

        assertEquals(1, capturedUrls.size)
    }

    @Test
    fun generateAuthorizationUrl_DoesNotInvokeOnBrowserCustomTabReady_ByDefault() {
        val sdkManagerMock = mockk<SalesforceSDKManager>(relaxed = true)
        every { sdkManagerMock.isDebugBuild } returns false
        every { sdkManagerMock.useHybridAuthentication } returns false
        every { sdkManagerMock.isBrowserLoginEnabled } returns false
        every { sdkManagerMock.appConfigForLoginHost } returns { _ -> null }
        every { sdkManagerMock.debugOverrideAppConfig } returns null

        val capturedUrls = mutableListOf<String>()
        viewModel.onBrowserCustomTabReady = { url -> capturedUrls.add(url) }

        runBlocking { viewModel.generateAuthorizationUrl("test.salesforce.com", sdkManagerMock) }

        assertTrue(
            "onBrowserCustomTabReady must NOT fire when neither browser login nor single-server custom tab is active",
            capturedUrls.isEmpty(),
        )
    }

    // endregion

    // region frontDoorBridgeUrl Tests

    @Test
    fun isUsingFrontDoorBridge_FalseByDefault() {
        // A fresh ViewModel must report isUsingFrontDoorBridge=false when no frontdoor URL is set.
        val vm = LoginViewModel(bootConfig)
        assertFalse(vm.isUsingFrontDoorBridge)
        assertNull(vm.frontDoorBridgeUrl.value)
    }

    @Test
    fun loginWithFrontDoorBridgeUrl_SetsFrontDoorBridgeUrl_AndIsUsingFrontDoorBridge() {
        val vm = LoginViewModel(bootConfig)
        val frontDoorUrl = "https://test.salesforce.com/frontdoor.jsp?sid=test_session"

        vm.loginWithFrontDoorBridgeUrl(frontDoorUrl, pkceCodeVerifier = "__VERIFIER__")

        assertEquals(frontDoorUrl, vm.frontDoorBridgeUrl.value)
        assertTrue(vm.isUsingFrontDoorBridge)
        assertEquals("__VERIFIER__", vm.frontdoorBridgeCodeVerifier)
    }

    @Test
    fun resetFrontDoorBridgeUrl_ClearsFrontDoorBridgeUrl() {
        val vm = LoginViewModel(bootConfig)
        vm.loginWithFrontDoorBridgeUrl(
            frontdoorBridgeUrl = "https://test.salesforce.com/frontdoor.jsp?sid=test_session",
            pkceCodeVerifier = "__VERIFIER__",
        )
        assertTrue("Precondition: isUsingFrontDoorBridge should be true", vm.isUsingFrontDoorBridge)

        vm.resetFrontDoorBridgeUrl()

        assertNull(vm.frontDoorBridgeUrl.value)
        assertFalse(vm.isUsingFrontDoorBridge)
        assertNull(vm.frontdoorBridgeServer)
        assertNull(vm.frontdoorBridgeCodeVerifier)
    }

    // endregion

    @Test
    fun selectedServer_Changes_GenerateCorrectAuthorizationUrl() {
        val originalServer = viewModel.selectedServer.value!!
        val originalCodeChallenge = getSHA256Hash(viewModel.codeVerifier)
        val originalAuthUrl = generateExpectedAuthorizationUrl(originalServer, originalCodeChallenge)
        assertEquals(originalAuthUrl, viewModel.loginUrl.value)

        viewModel.selectedServer.value = FAKE_SERVER_URL
        // Wait for async update
        Thread.sleep(200)
        val newCodeChallenge = getSHA256Hash(viewModel.codeVerifier)
        assertNotEquals(originalCodeChallenge, newCodeChallenge)
        // LoginUrlSource prepends https:// to scheme-less servers before URL generation.
        val newAuthUrl = generateExpectedAuthorizationUrl("https://$FAKE_SERVER_URL", newCodeChallenge)
        assertEquals(newAuthUrl, viewModel.loginUrl.value)
    }

    @Test
    fun codeVerifier_UpdatesOn_WebViewRefresh() {
        val originalCodeChallenge = getSHA256Hash(viewModel.codeVerifier)
        assertTrue(viewModel.loginUrl.value!!.contains(originalCodeChallenge))

        viewModel.reloadWebView()
        // Wait for async update
        Thread.sleep(200)
        val newCodeChallenge = getSHA256Hash(viewModel.codeVerifier)
        assertNotNull(newCodeChallenge)
        assertNotEquals(originalCodeChallenge, newCodeChallenge)
        assertTrue(viewModel.loginUrl.value!!.contains(newCodeChallenge))
    }

    @Test
    fun jwtFlow_Changes_loginUrl() {
        val server = viewModel.selectedServer.value!!
        var codeChallenge = getSHA256Hash(viewModel.codeVerifier)
        val expectedUrl = generateExpectedAuthorizationUrl(server, codeChallenge)
        assertEquals(expectedUrl, viewModel.loginUrl.value)

        viewModel.jwt = FAKE_JWT
        viewModel.authCodeForJwtFlow = FAKE_JWT_FLOW_AUTH
        viewModel.reloadWebView()
        // Wait for async update
        Thread.sleep(200)
        assertNotEquals(expectedUrl, viewModel.loginUrl.value)

        codeChallenge = getSHA256Hash(viewModel.codeVerifier)
        val authUrl = generateExpectedAuthorizationUrl(server, codeChallenge)
        val expectedJwtFlowUrl = getFrontdoorUrl(URI(authUrl), FAKE_JWT_FLOW_AUTH, server, mapOf<String, String>()).toString()
        assertEquals(expectedJwtFlowUrl, viewModel.loginUrl.value)
    }

    @Test
    fun loginHint_Changes_loginUrl() {
        val server = viewModel.selectedServer.value!!
        val codeChallenge = getSHA256Hash(viewModel.codeVerifier)
        val result = generateExpectedAuthorizationUrl(
            server = server,
            codeChallenge = codeChallenge,
            loginHint = "ietf_example_domain_reserved_for_test@example.com",
        )
        val expectedResult =
            "https://login.salesforce.com/services/oauth2/authorize\\?display=touch&response_type=code&client_id=__CONSUMER_KEY__&scope=api%20openid%20refresh_token%20web&login_hint=ietf_example_domain_reserved_for_test%40example.com&redirect_uri=__REDIRECT_URI__&device_id=[^=]+&code_challenge=[^=]+".toRegex()
        assertTrue(expectedResult.matches(result))
    }

    @Test
    fun testGetValidSeverUrl() {
        assertNull(viewModel.getValidServerUrl(""))
        assertNull(viewModel.getValidServerUrl("not_a_url_at_all"))
        assertNull(viewModel.getValidServerUrl("https://stillnotaurl"))
        assertNull(viewModel.getValidServerUrl("a.com."))
        assertEquals("https://a.com", viewModel.getValidServerUrl("a.com"))
        assertEquals("https://a.b", viewModel.getValidServerUrl("http://a.b"))
        val unchangedUrl = "https://login.salesforce.com"
        assertEquals(unchangedUrl, viewModel.getValidServerUrl(unchangedUrl))
        val endingSlash = "$unchangedUrl/"
        assertEquals(unchangedUrl, viewModel.getValidServerUrl(endingSlash))
    }

    @Test
    fun generateAuthorizationUrl_UsesDebugOverrideAppConfig_WhenSet() {
        // Set custom OAuth config via debugOverrideAppConfig
        val customConsumerKey = "custom_consumer_key_123"
        val customRedirectUri = "custom://redirect"
        val customScopes = listOf("api", "web", "custom_scope")
        SalesforceSDKManager.getInstance().debugOverrideAppConfig = OAuthConfig(
            consumerKey = customConsumerKey,
            redirectUri = customRedirectUri,
            scopes = customScopes
        )

        // Trigger URL generation
        viewModel.reloadWebView()
        Thread.sleep(200)

        // Verify the URL contains the custom consumer key and redirect URI
        val loginUrl = viewModel.loginUrl.value!!
        assertTrue("URL should contain custom consumer key", loginUrl.contains(customConsumerKey))
        assertTrue("URL should contain custom redirect URI", loginUrl.contains("redirect_uri=custom://redirect"))
        assertTrue("URL should contain custom scope", loginUrl.contains("custom_scope"))
    }

    @Test
    fun generateAuthorizationUrl_UsesBootConfig_WhenDebugOverrideAppConfigIsNull() {
        // Ensure debugOverrideAppConfig is null
        SalesforceSDKManager.getInstance().debugOverrideAppConfig = null

        // Trigger URL generation
        viewModel.reloadWebView()
        Thread.sleep(200)

        // Verify the URL contains the boot config values
        val loginUrl = viewModel.loginUrl.value!!
        assertTrue("URL should contain boot config consumer key",
            loginUrl.contains(bootConfig.remoteAccessConsumerKey))
        assertTrue("URL should contain boot config redirect URI",
            loginUrl.contains("redirect_uri=${bootConfig.oauthRedirectURI}"))
    }

    @Test
    fun generateAuthorizationUrl_UsesAppConfigForLoginHost_WhenDebugOverrideIsNull() {
        val sdkManager = SalesforceSDKManager.getInstance()
        val originalAppConfigForLoginHost = sdkManager.appConfigForLoginHost

        try {
            // Ensure debugOverrideAppConfig is null
            sdkManager.debugOverrideAppConfig = null

            // Set custom appConfigForLoginHost
            val customConsumerKey = "app_config_consumer_key_456"
            val customRedirectUri = "appconfig://redirect"
            val customScopes = listOf("api", "refresh_token", "app_config_scope")
            sdkManager.appConfigForLoginHost = { _ ->
                OAuthConfig(
                    consumerKey = customConsumerKey,
                    redirectUri = customRedirectUri,
                    scopes = customScopes,
                )
            }

            // Trigger URL generation
            viewModel.reloadWebView()
            Thread.sleep(200)

            // Verify the URL contains the custom app config values
            val loginUrl = viewModel.loginUrl.value!!
            assertTrue("URL should contain app config consumer key", loginUrl.contains(customConsumerKey))
            assertTrue("URL should contain app config redirect URI",
                loginUrl.contains("redirect_uri=appconfig://redirect"))
            assertTrue("URL should contain app config scope", loginUrl.contains("app_config_scope"))
        } finally {
            sdkManager.appConfigForLoginHost = originalAppConfigForLoginHost
        }
    }

    @Test
    fun generateAuthorizationUrl_PrefersDebugOverrideAppConfig_OverAppConfigForLoginHost() {
        val sdkManager = SalesforceSDKManager.getInstance()
        val originalAppConfigForLoginHost = sdkManager.appConfigForLoginHost

        try {
            // Set both debugOverrideAppConfig and appConfigForLoginHost
            val debugConsumerKey = "debug_override_key_789"
            val debugRedirectUri = "debug://redirect"
            val debugScopes = listOf("api", "debug_scope")
            sdkManager.debugOverrideAppConfig = OAuthConfig(
                consumerKey = debugConsumerKey,
                redirectUri = debugRedirectUri,
                scopes = debugScopes,
            )

            val appConfigConsumerKey = "app_config_key_should_not_be_used"
            val appConfigRedirectUri = "appconfig://should_not_be_used"
            sdkManager.appConfigForLoginHost = { _ ->
                OAuthConfig(
                    consumerKey = appConfigConsumerKey,
                    redirectUri = appConfigRedirectUri,
                    scopes = listOf("api"),
                )
            }

            // Trigger URL generation
            viewModel.reloadWebView()
            Thread.sleep(200)

            // Verify the URL contains the debug override values, not app config values
            val loginUrl = viewModel.loginUrl.value!!
            assertTrue("URL should contain debug override consumer key",
                loginUrl.contains(debugConsumerKey))
            assertTrue("URL should contain debug override redirect URI",
                loginUrl.contains("redirect_uri=debug://redirect"))
            assertTrue("URL should contain debug scope", loginUrl.contains("debug_scope"))

            // Verify app config values are NOT in the URL
            assertFalse("URL should NOT contain app config consumer key",
                loginUrl.contains(appConfigConsumerKey))
            assertFalse("URL should NOT contain app config redirect URI",
                loginUrl.contains("should_not_be_used"))
        } finally {
            sdkManager.appConfigForLoginHost = originalAppConfigForLoginHost
        }
    }

    @Test
    fun generateAuthorizationUrl_ReleaseBuildIgnoresDebugOverrideAppConfig_OverAppConfigForLoginHost() {
        val sdkManagerMock = mockk<SalesforceSDKManager>(relaxed = false)
        val appConfigConsumerKey = "app_config_key_should_not_be_used"
        val appConfigRedirectUri = "appconfig://should_not_be_used"
        every { sdkManagerMock.isDebugBuild } returns false
        every { sdkManagerMock.useHybridAuthentication } returns false
        // generateAuthorizationUrl reads isBrowserLoginEnabled to decide whether to invoke
        // the onBrowserCustomTabReady callback; not relevant to this assertion but must be stubbed.
        every { sdkManagerMock.isBrowserLoginEnabled } returns false
        every { sdkManagerMock.appConfigForLoginHost } returns { _ ->
            OAuthConfig(
                consumerKey = appConfigConsumerKey,
                redirectUri = appConfigRedirectUri,
                scopes = listOf("api"),
            )
        }
        every { sdkManagerMock.appAttestationClient } returns null
        val debugConsumerKey = "debug_override_key_789"
        val debugRedirectUri = "debug://redirect"
        val debugScopes = listOf("api", "debug_scope")
        every { sdkManagerMock.debugOverrideAppConfig } returns OAuthConfig(
            consumerKey = debugConsumerKey,
            redirectUri = debugRedirectUri,
            scopes = debugScopes,
        )

        // Verify the URL contains the app config values, not the debug override config values
        runBlocking { viewModel.generateAuthorizationUrl("test.salesforce.com", sdkManagerMock) }
        val loginUrlValue = viewModel.loginUrl.value!!
        assertFalse(
            "URL should not contain debug override consumer key",
            loginUrlValue.contains(debugConsumerKey)
        )
        assertFalse(
            "URL should not contain debug override redirect URI",
            loginUrlValue.contains("redirect_uri=debug://redirect")
        )
        assertFalse("URL should not contain debug scope", loginUrlValue.contains("debug_scope"))

        // Verify app config values are in the URL
        assertTrue(
            "URL should contain app config consumer key",
            loginUrlValue.contains(appConfigConsumerKey)
        )
        assertTrue(
            "URL should contain app config redirect URI",
            loginUrlValue.contains("should_not_be_used")
        )
    }

    @Test
    fun generateMigrationAuthorizationPath_UsesMigrationConfig_OverAppConfigForLoginHost() {
        val sdkManagerMock = mockk<SalesforceSDKManager>(relaxed = false)
        val appConfigConsumerKey = "app_config_key_should_not_be_used"
        val appConfigRedirectUri = "appconfig://should_not_be_used"
        every { sdkManagerMock.useHybridAuthentication } returns false
        every { sdkManagerMock.appConfigForLoginHost } returns { _ ->
            OAuthConfig(
                consumerKey = appConfigConsumerKey,
                redirectUri = appConfigRedirectUri,
                scopes = listOf("api"),
            )
        }
        every { sdkManagerMock.appAttestationClient } returns null
        val debugConsumerKey = "debug_override_key_789"
        val debugRedirectUri = "debug://redirect"
        val debugScopes = listOf("api", "debug_scope")
        every { sdkManagerMock.debugOverrideAppConfig } returns OAuthConfig(
            consumerKey = debugConsumerKey,
            redirectUri = debugRedirectUri,
            scopes = debugScopes,
        )
        val migrationConsumerKey = "migration_override_key_789"
        val migrationRedirectUri = "migration://redirect"
        val migrationScopes = listOf("api", "migration_scope")

        // Verify the URL contains the app config values, not the debug override config values
        val loginUrl = viewModel.generateMigrationAuthorizationPath(
            server = "test.salesforce.com",
            migrationOAuthConfig = OAuthConfig(
                migrationConsumerKey,
                migrationRedirectUri,
                migrationScopes,
            )
        )
        assertFalse(
            "URL should not contain debug override consumer key",
            loginUrl.contains(debugConsumerKey)
        )
        assertFalse(
            "URL should not contain debug override redirect URI",
            loginUrl.contains("redirect_uri=debug://redirect")
        )
        assertFalse("URL should not contain debug scope", loginUrl.contains("debug_scope"))

        // Verify migration config values are in the URL
        assertTrue(
            "URL should contain migration consumer key",
            loginUrl.contains(migrationConsumerKey)
        )
        migrationScopes.forEach { scope ->
            assertTrue(loginUrl.contains(scope))
        }
    }

    @Test
    fun generateAuthorizationUrl_UsesServerSpecificConfig_FromAppConfigForLoginHost() {
        val sdkManager = SalesforceSDKManager.getInstance()
        val originalAppConfigForLoginHost = sdkManager.appConfigForLoginHost

        try {
            // Ensure debugOverrideAppConfig is null
            sdkManager.debugOverrideAppConfig = null

            // Set appConfigForLoginHost that returns different configs based on server
            sdkManager.appConfigForLoginHost = { server ->
                when {
                    server.contains("test.salesforce.com") -> OAuthConfig(
                        consumerKey = "test_consumer_key",
                        redirectUri = "test://redirect",
                        scopes = listOf("api", "test_scope"),
                    )
                    server.contains("login.salesforce.com") -> OAuthConfig(
                        consumerKey = "prod_consumer_key",
                        redirectUri = "prod://redirect",
                        scopes = listOf("api", "prod_scope"),
                    )
                    else -> OAuthConfig(bootConfig)
                }
            }

            // Test with test server
            viewModel.selectedServer.value = "https://test.salesforce.com"
            Thread.sleep(200)
            var loginUrl = viewModel.loginUrl.value!!
            assertTrue("URL should contain test consumer key. URL: $loginUrl",
                loginUrl.contains("test_consumer_key"))
            assertTrue("URL should contain test redirect URI. URL: $loginUrl",
                loginUrl.contains("redirect_uri=test://redirect"))
            assertTrue("URL should contain test scope. URL: $loginUrl",
                loginUrl.contains("test_scope"))

            // Test with production server
            viewModel.selectedServer.value = "https://login.salesforce.com"
            Thread.sleep(200)
            loginUrl = viewModel.loginUrl.value!!
            assertTrue("URL should contain prod consumer key. URL: $loginUrl",
                loginUrl.contains("prod_consumer_key"))
            assertTrue("URL should contain prod redirect URI. URL: $loginUrl",
                loginUrl.contains("redirect_uri=prod://redirect"))
            assertTrue("URL should contain prod scope. URL: $loginUrl",
                loginUrl.contains("prod_scope"))
        } finally {
            sdkManager.appConfigForLoginHost = originalAppConfigForLoginHost
        }
    }

    @Test
    fun generateAuthorizationUrl_HandlesNullScopes_InOAuthConfig() {
        // Set OAuth config with null scopes
        val customConsumerKey = "no_scopes_consumer_key"
        val customRedirectUri = "noscopes://redirect"
        SalesforceSDKManager.getInstance().debugOverrideAppConfig = OAuthConfig(
            consumerKey = customConsumerKey,
            redirectUri = customRedirectUri,
            scopes = null,
        )

        // Trigger URL generation
        viewModel.reloadWebView()
        Thread.sleep(200)

        // Verify the URL is generated correctly without scopes
        val loginUrl = viewModel.loginUrl.value!!
        assertTrue("URL should contain custom consumer key", loginUrl.contains(customConsumerKey))
        assertTrue("URL should contain custom redirect URI",
            loginUrl.contains("redirect_uri=noscopes://redirect"))
        // URL should still be valid even without explicit scopes
        assertTrue("URL should be a valid OAuth URL",
            loginUrl.contains("/services/oauth2/authorize"))
    }

    @Test
    fun reloadWebView_WithFrontDoorBridge_DoesNotReloadUrl() {
        // Set up front door bridge
        val frontDoorUrl = "https://test.salesforce.com/frontdoor.jsp?sid=test_session"
        viewModel.loginWithFrontDoorBridgeUrl(frontDoorUrl, null)

        // Verify front door bridge is active
        assertTrue("isUsingFrontDoorBridge should be true", viewModel.isUsingFrontDoorBridge)
        assertEquals("frontDoorBridgeUrl should be front door URL", frontDoorUrl, viewModel.frontDoorBridgeUrl.value)

        // Call reloadWebView
        viewModel.reloadWebView()
        Thread.sleep(200)

        // Verify URL did not change
        assertEquals("frontDoorBridgeUrl should still be front door URL", frontDoorUrl, viewModel.frontDoorBridgeUrl.value)
    }

    @Test
    fun reloadWebView_WithUserAgentFlow_SetsAboutBlankFirst() {
        try {
            // Set to User Agent Flow (not Web Server Flow)
            SalesforceSDKManager.getInstance().useWebServerAuthentication = false

            // Ensure we're not using front door bridge
            assertFalse("isUsingFrontDoorBridge should be false", viewModel.isUsingFrontDoorBridge)

            // Get initial URL
            val initialUrl = viewModel.loginUrl.value
            assertNotNull("Initial URL should not be null", initialUrl)
            assertNotEquals("Initial URL should not be ABOUT_BLANK", ABOUT_BLANK, initialUrl)

            // Call reloadWebView
            viewModel.reloadWebView()

            // Verify URL was set to ABOUT_BLANK for User Agent Flow
            // NOTE:  If this is flaky we should use Turbine to test the actual state changes.
            assertEquals("loginUrl should be set to ABOUT_BLANK for User Agent Flow",
                ABOUT_BLANK, viewModel.loginUrl.value)

            // Wait for the new authorization URL to be generated
            Thread.sleep(200)

            // Verify a new URL was generated
            val newUrl = viewModel.loginUrl.value
            assertNotNull("New URL should not be null", newUrl)
            assertNotEquals("New URL should not be ABOUT_BLANK", ABOUT_BLANK, newUrl)
            assertNotEquals("New URL should be different from initial", initialUrl, newUrl)
        } finally {
            SalesforceSDKManager.getInstance().useWebServerAuthentication = true
        }
    }

    @Test
    fun reloadWebView_WithWebServerFlow_DoesNotSetAboutBlank() {
        assert(SalesforceSDKManager.getInstance().useWebServerAuthentication)
        // Ensure we're not using front door bridge
        assertFalse("isUsingFrontDoorBridge should be false", viewModel.isUsingFrontDoorBridge)

        // Get initial URL
        val initialUrl = viewModel.loginUrl.value
        assertNotNull("Initial URL should not be null", initialUrl)

        // Call reloadWebView
        viewModel.reloadWebView()

        // Give a brief moment to check if ABOUT_BLANK would be set
        Thread.sleep(50)

        // Verify URL was NOT set to ABOUT_BLANK for Web Server Flow
        assertNotEquals("loginUrl should NOT be ABOUT_BLANK for Web Server Flow",
            ABOUT_BLANK, viewModel.loginUrl.value)

        // Wait for the new authorization URL to be generated
        Thread.sleep(200)

        // Verify a new URL was generated with different code challenge
        val newUrl = viewModel.loginUrl.value
        assertNotNull("New URL should not be null", newUrl)
        assertNotEquals("New URL should not be ABOUT_BLANK", ABOUT_BLANK, newUrl)
        assertNotEquals("New URL should be different from initial (different code challenge)",
            initialUrl, newUrl)
    }

    @Test
    fun reloadWebView_WithNullSelectedServer_DoesNothing() {
        val initialUrl = "test"
        viewModel.loginUrl.value = initialUrl

        // Set selectedServer to null
        viewModel.selectedServer.value = null
        Thread.sleep(100)

        // Call reloadWebView
        viewModel.reloadWebView()
        Thread.sleep(200)

        // Verify URL did not change
        assertEquals("loginUrl should not change when selectedServer is null",
            initialUrl, viewModel.loginUrl.value)
    }

    @Test
    fun generateAuthorizationUrl_UsesBootConfig_WhenAppConfigForLoginHostReturnsNull() {
        val sdkManager = SalesforceSDKManager.getInstance()
        val originalAppConfigForLoginHost = sdkManager.appConfigForLoginHost

        try {
            // Ensure debugOverrideAppConfig is null
            sdkManager.debugOverrideAppConfig = null

            // Set appConfigForLoginHost to return null
            sdkManager.appConfigForLoginHost = { _ -> null }

            // Trigger URL generation
            viewModel.reloadWebView()
            Thread.sleep(200)

            // Verify the URL contains the boot config values (fallback)
            val loginUrl = viewModel.loginUrl.value!!
            assertTrue("URL should contain boot config consumer key when appConfigForLoginHost returns null",
                loginUrl.contains(bootConfig.remoteAccessConsumerKey))
            assertTrue("URL should contain boot config redirect URI when appConfigForLoginHost returns null",
                loginUrl.contains("redirect_uri=${bootConfig.oauthRedirectURI}"))

            // Verify boot config scopes are present
            bootConfig.oauthScopes.forEach { scope ->
                assertTrue("URL should contain boot config scope '$scope' when appConfigForLoginHost returns null",
                    loginUrl.contains(scope))
            }
        } finally {
            sdkManager.appConfigForLoginHost = originalAppConfigForLoginHost
        }
    }

    @Test
    fun getAuthorizationUrl_WithNullAppAttestationClient_OmitsAttestationParam() = runBlocking {
        val sdkManagerMock = createSdkManagerMockForAttestation(appAttestationClient = null)
        val freshViewModel = LoginViewModel(bootConfig)

        val loginUrl = freshViewModel.getAuthorizationUrl(TEST_ATTESTATION_SERVER, sdkManagerMock)

        assertFalse(
            "URL should NOT contain an attestation parameter but was '$loginUrl'.",
            loginUrl.contains(ATTESTATION_QUERY_PARAM_PREFIX),
        )
    }

    @Test
    fun getAuthorizationUrl_WithAppAttestationClient_IncludesAttestationParam() = runBlocking {
        val appAttestationClient = createMockAppAttestationClient(attestation = TEST_APP_ATTESTATION)
        val sdkManagerMock = createSdkManagerMockForAttestation(appAttestationClient = appAttestationClient)
        val freshViewModel = LoginViewModel(bootConfig)

        val loginUrl = freshViewModel.getAuthorizationUrl(TEST_ATTESTATION_SERVER, sdkManagerMock)

        assertTrue(
            "URL should contain '$ATTESTATION_QUERY_PARAM_PREFIX$TEST_APP_ATTESTATION' but was '$loginUrl'.",
            loginUrl.contains("$ATTESTATION_QUERY_PARAM_PREFIX$TEST_APP_ATTESTATION"),
        )
        coVerify(exactly = 1) {
            appAttestationClient.fetchMobileAppAttestationChallenge()
            appAttestationClient.createAppAttestation(appAttestationChallenge = TEST_CHALLENGE_VALUE)
        }
    }

    @Test
    fun getAuthorizationUrl_WhenCreateAppAttestationReturnsNull_OmitsAttestationParam() = runBlocking {
        val appAttestationClient = createMockAppAttestationClient(attestation = null)
        val sdkManagerMock = createSdkManagerMockForAttestation(appAttestationClient = appAttestationClient)
        val freshViewModel = LoginViewModel(bootConfig)

        val loginUrl = freshViewModel.getAuthorizationUrl(TEST_ATTESTATION_SERVER, sdkManagerMock)

        assertFalse(
            "URL should NOT contain an attestation parameter but was '$loginUrl'.",
            loginUrl.contains(ATTESTATION_QUERY_PARAM_PREFIX),
        )
        coVerify(exactly = 1) {
            appAttestationClient.fetchMobileAppAttestationChallenge()
            appAttestationClient.createAppAttestation(appAttestationChallenge = TEST_CHALLENGE_VALUE)
        }
    }

    @Test
    fun loginViewModel_applyPendingLoginServer_returns_onNullPendingLoginServer() {

        val sdkManager = mockk<SalesforceSDKManager>(relaxed = true)

        viewModel.applyPendingServer(sdkManager = sdkManager, pendingLoginServer = null)
        assert(viewModel.previousPendingServer == null)
        verify(exactly = 0) { sdkManager.fetchAuthenticationConfiguration(any(), any()) }
    }

    @Test
    fun loginViewModel_applyPendingLoginServer_setsSelectedServer_whenSingleServerCustomTabActivity() {

        val viewModel = object : LoginViewModel(bootConfig) {
            override val singleServerCustomTabActivity = true
        }

        val sdkManager = mockk<SalesforceSDKManager>(relaxed = true)
        val exampleUrl = "https://www.example.com" // IETF-Reserved Test Domain

        viewModel.applyPendingServer(sdkManager = sdkManager, pendingLoginServer = exampleUrl)

        assert(viewModel.previousPendingServer == exampleUrl)
        assert(viewModel.selectedServer.value == exampleUrl)
        verify(exactly = 0) { sdkManager.fetchAuthenticationConfiguration(any(), any()) }
    }

    @Test
    fun loginViewModel_applyPendingLoginServer_setsSelectedServerWithFetchedAuthConfig() {

        val sdkManager = mockk<SalesforceSDKManager>(relaxed = true)
        val callbackSlot = slot<() -> Unit>()
        every { sdkManager.fetchAuthenticationConfiguration(any(), capture(callbackSlot)) } answers {
            callbackSlot.captured.invoke()
            mockk<Job>()
        }
        val exampleUrl = "https://www.example.com" // IETF-Reserved Test Domain

        val job = mockk<Job>(relaxed = true)
        viewModel.authenticationConfigurationFetchJob = job
        viewModel.applyPendingServer(sdkManager = sdkManager, pendingLoginServer = exampleUrl)

        assert(viewModel.previousPendingServer == exampleUrl)
        assert(viewModel.selectedServer.value == exampleUrl)
        verify(exactly = 1) { sdkManager.fetchAuthenticationConfiguration(any(), any()) }
        verify(exactly = 1) { job.cancel() }
    }

    @Test
    fun loginViewModel_isSwitchFromSalesforceWelcomeDiscoveryToDefaultLogin_returnsFalseOnNullPreviousPendingLoginServer() {

        val exampleUrl = "https://www.example.com" // IETF-Reserved Test Domain

        viewModel.previousPendingServer = null
        assertFalse(viewModel.isSwitchFromSalesforceWelcomeDiscoveryToDefaultLogin(exampleUrl.toUri()))
    }

    @Test
    fun loginViewModel_isSwitchFromSalesforceWelcomeDiscoveryToDefaultLogin_returnsFalseOnUnparsablePreviousPendingLoginServer() {

        val exampleUrl = "https://www.example.com" // IETF-Reserved Test Domain

        viewModel.previousPendingServer = "_invalid_uri_"
        assertFalse(viewModel.isSwitchFromSalesforceWelcomeDiscoveryToDefaultLogin(exampleUrl.toUri()))
    }

    @Test
    fun loginViewModel_isSwitchFromSalesforceWelcomeDiscoveryToDefaultLogin_returnsTrueOnSwitchToDefault() {

        val exampleUrl = "https://www.example.com" // IETF-Reserved Test Domain

        viewModel.previousPendingServer = WELCOME_LOGIN_URL
        assertTrue(viewModel.isSwitchFromSalesforceWelcomeDiscoveryToDefaultLogin(exampleUrl.toUri()))
    }

    @Test
    fun loginViewModel_isSwitchFromSalesforceWelcomeDiscoveryToDefaultLogin_returnsFalseOnSwitchToWelcome() {

        val exampleUrl = "https://www.example.com" // IETF-Reserved Test Domain

        viewModel.previousPendingServer = exampleUrl
        assertFalse(viewModel.isSwitchFromSalesforceWelcomeDiscoveryToDefaultLogin(WELCOME_LOGIN_URL.toUri()))
    }

    @Test
    fun loginViewModel_isSwitchFromSalesforceWelcomeDiscoveryToDefaultLogin_returnsFalseSwitchBetweenDefaultLoginUrls() {

        val exampleUrl = "https://www.example.com" // IETF-Reserved Test Domain

        viewModel.previousPendingServer = "https://other.example.com" // IETF-Reserved Test Domain
        assertFalse(viewModel.isSwitchFromSalesforceWelcomeDiscoveryToDefaultLogin(exampleUrl.toUri()))
    }

    @Test
    fun loginViewModel_isSwitchFromSalesforceWelcomeDiscoveryToDefaultLogin_returnsFalseSwitchBetweenWelcomeLoginUrls() {

        viewModel.previousPendingServer = WELCOME_LOGIN_URL
        assertFalse(viewModel.isSwitchFromSalesforceWelcomeDiscoveryToDefaultLogin(WELCOME_LOGIN_URL.toUri()))
    }

    @Test
    fun loginViewModel_pendingServerObserver_setsPendingServer() {

        val value = LoginServer("Example", "https://www.example.com", true) // IETF-Reserved Test Domain

        val viewModel = mockk<LoginViewModel>(relaxed = true)
        val pendingServer = mockk<MediatorLiveData<String>>(relaxed = true)
        every { pendingServer.value } returns null
        every { viewModel.pendingServer } returns pendingServer
        val observer = viewModel.PendingServerSource(viewModel)

        observer.onChanged(value)

        verify(exactly = 1) { pendingServer.value = value.url }
    }

    @Test
    fun loginViewModel_pendingServerObserver_reloadsWebViewOnRepeatValue() {

        val value = LoginServer("Example", "https://www.example.com", true) // IETF-Reserved Test Domain

        val viewModel = mockk<LoginViewModel>(relaxed = true)
        val pendingServer = mockk<MediatorLiveData<String>>(relaxed = true)
        every { pendingServer.value } returns value.url
        every { viewModel.pendingServer } returns pendingServer
        val observer = viewModel.PendingServerSource(viewModel)

        observer.onChanged(value)

        verify(exactly = 1) { viewModel.reloadWebView() }
    }

    @Test
    fun loginViewModel_loginUrlObserver_generatesUrlForNewServer() = runTest {

        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)

        val viewModel = mockk<LoginViewModel>(relaxed = true)
        val loginUrl = mockk<MediatorLiveData<String>>(relaxed = true)
        every { loginUrl.value } returns null
        every { viewModel.loginUrl } returns loginUrl
        val observer = viewModel.LoginUrlSource(viewModel, scope)

        val valueNew = "https://www.example.com" // IETF-Reserved Test Domain
        observer.onChanged(valueNew)

        advanceUntilIdle()

        coVerify(exactly = 1) {
            viewModel.generateAuthorizationUrl(
                server = valueNew,
                any(),
                any(),
            )
        }
    }

    @Test
    fun loginViewModel_loginUrlObserver_generatesUrlWhenHostChanges() = runTest {

        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)

        val viewModel = mockk<LoginViewModel>(relaxed = true)
        val loginUrl = mockk<MediatorLiveData<String>>(relaxed = true)
        // loginUrl currently has a URL with host "other.example.com"
        every { loginUrl.value } returns "https://other.example.com/services/oauth2/authorize?display=touch"
        every { viewModel.loginUrl } returns loginUrl
        val observer = viewModel.LoginUrlSource(viewModel, scope)

        val valueNew = "https://www.example.com" // IETF-Reserved Test Domain
        observer.onChanged(valueNew)

        advanceUntilIdle()

        coVerify(exactly = 1) {
            viewModel.generateAuthorizationUrl(
                server = valueNew,
                any(),
                any(),
            )
        }
    }

    @Test
    fun loginViewModel_loginUrlObserver_ignoresWhenSameHost() = runTest {

        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)

        val viewModel = mockk<LoginViewModel>(relaxed = true)
        val loginUrl = mockk<MediatorLiveData<String>>(relaxed = true)
        // loginUrl currently has a URL with host "www.example.com"
        every { loginUrl.value } returns "https://www.example.com/services/oauth2/authorize?display=touch"
        every { viewModel.loginUrl } returns loginUrl
        val observer = viewModel.LoginUrlSource(viewModel, scope)

        // Same host
        observer.onChanged("https://www.example.com")

        advanceUntilIdle()

        coVerify(exactly = 0) {
            viewModel.generateAuthorizationUrl(any(), any(), any())
        }
    }

    @Test
    fun loginViewModel_loginUrlObserver_ignoresNull() = runTest {

        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)

        val viewModel = mockk<LoginViewModel>(relaxed = true)
        val loginUrl = mockk<MediatorLiveData<String>>(relaxed = true)
        every { loginUrl.value } returns "https://other.example.com/services/oauth2/authorize"
        every { viewModel.loginUrl } returns loginUrl
        val observer = viewModel.LoginUrlSource(viewModel, scope)

        observer.onChanged(null)

        advanceUntilIdle()

        coVerify(exactly = 0) {
            viewModel.generateAuthorizationUrl(any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun loginViewModel_buildAccountName_returnsExpectedValue() {

        // IETF-Reserved Test Domain
        assertEquals(viewModel.buildAccountName("Example@Example.com", "https://www.example.com"), "Example@Example.com (https://www.example.com) (SalesforceSDKTest)")
    }

    @Test
    fun loginViewModel_consumerKey_returnsExpectedValue() {

        assertEquals("__CONSUMER_KEY__", viewModel.consumerKey)
    }

    @Test
    fun loginViewModel_consumerKey_returnsClientIdWhenDifferentThanConsumerKey() {

        viewModel.clientId = "__CLIENT_ID__"
        assertEquals("__CLIENT_ID__", viewModel.consumerKey)
    }

    @Test
    fun loginViewModel_getValidServerUrl_returns() {

        val value = "https://www.example.com" // IETF-Reserved Test Domain

        assertEquals("https://www.example.com", viewModel.getValidServerUrl(value))
    }

    @Test
    fun loginViewModel_getValidServerUrl_returnsNullWhenUrlMissingPeriod() {

        val value = "https://www_example_com" // IETF-Reserved Test Domain

        assertNull(viewModel.getValidServerUrl(value))
    }

    @Test
    fun loginViewModel_getValidServerUrl_returnsNullWhenUrlEndsWithPeriod() {

        val value = "https://www.example." // IETF-Reserved Test Domain

        assertNull(viewModel.getValidServerUrl(value))
    }

    @Test
    fun loginViewModel_getValidServerUrl_returnsNullWhenUrlUnparsable() {

        val value = "(.*&^@Q#Q@#(*&^@Q#@%)"

        assertNull(viewModel.getValidServerUrl(value))
    }

    @Test
    fun loginViewModel_getValidServerUrl_returnsForHttpUrl() {

        val value = "http://www.example.com" // IETF-Reserved Test Domain
        val result = "https://www.example.com" // IETF-Reserved Test Domain

        assertEquals(result, viewModel.getValidServerUrl(value))
    }

    @Test
    fun loginViewModel_getValidServerUrl_returnsForHttpUrlUnparsable() {
        assertNull(viewModel.getValidServerUrl(".!"))
    }

    @Test
    fun loginViewModel_getValidServerUrl_returnsUrlWithoutScheme() {

        val value = "www.example.com" // IETF-Reserved Test Domain
        val result = "https://www.example.com" // IETF-Reserved Test Domain

        assertEquals(result, viewModel.getValidServerUrl(value))
    }

    private fun createSdkManagerMockForAttestation(
        appAttestationClient: AppAttestationClient?,
    ): SalesforceSDKManager = mockk<SalesforceSDKManager>(relaxed = true).also { mock ->
        every { mock.useHybridAuthentication } returns false
        every { mock.isDebugBuild } returns false
        every { mock.debugOverrideAppConfig } returns null
        every { mock.appConfigForLoginHost } returns { _ -> null }
        every { mock.appAttestationClient } returns appAttestationClient
    }

    private fun createMockAppAttestationClient(
        attestation: String?,
    ): AppAttestationClient = mockk<AppAttestationClient>(relaxed = true).also { client ->
        every { client.fetchMobileAppAttestationChallenge() } returns TEST_CHALLENGE_VALUE
        coEvery {
            client.createAppAttestation(appAttestationChallenge = TEST_CHALLENGE_VALUE)
        } returns attestation
    }

    private fun generateExpectedAuthorizationUrl(
        server: String,
        codeChallenge: String,
        loginHint: String? = null,
    ) = OAuth2.getAuthorizationUrl(
        true,
        true,
        URI(server),
        bootConfig.remoteAccessConsumerKey,
        bootConfig.oauthRedirectURI,
        bootConfig.oauthScopes,
        loginHint,
        SalesforceSDKManager.getInstance().appContext.getString(oauth_display_type),
        codeChallenge,
        hashMapOf<String, String>()
    ).toString()
}
