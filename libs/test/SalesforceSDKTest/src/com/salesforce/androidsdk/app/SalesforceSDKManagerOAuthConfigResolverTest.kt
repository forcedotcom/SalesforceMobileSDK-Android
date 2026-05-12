/*
 * Copyright (c) 2026-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.app

import android.app.Activity
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.salesforce.androidsdk.MainActivity
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.config.BootConfig.getBootConfig
import com.salesforce.androidsdk.config.OAuthConfig
import com.salesforce.androidsdk.ui.LoginActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for SalesforceSDKManager OAuth config resolution methods.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class SalesforceSDKManagerOAuthConfigResolverTest {

    private lateinit var sdkManager: TestSalesforceSDKManager
    private lateinit var targetContext: Context
    private lateinit var bootConfig: BootConfig

    @Before
    fun setUp() {
        targetContext = getInstrumentation().targetContext
        bootConfig = getBootConfig(targetContext)
        TestSalesforceSDKManager.init(targetContext, MainActivity::class.java)
        sdkManager = TestSalesforceSDKManager.getInstance() as TestSalesforceSDKManager
        sdkManager.isTestRun = true
    }

    @After
    fun tearDown() {
        // Reset state
        sdkManager.debugOverrideAppConfig = null
        sdkManager.appConfigForLoginHost = { OAuthConfig(getBootConfig(targetContext)) }
        sdkManager.isDebugBuildOverride = null
        TestSalesforceSDKManager.resetInstance()
    }

    @Test
    fun test_givenDebugOverride_whenResolveOAuthConfig_thenReturnsDebugOverride() = runBlocking {
        // Given: Debug override config is set
        val overrideConfig = OAuthConfig(
            consumerKey = "debug-consumer-key",
            redirectUri = "debug://callback",
            scopes = listOf("api", "web")
        )
        sdkManager.debugOverrideAppConfig = overrideConfig

        // When: Resolving OAuth config for any server
        val result = sdkManager.resolveOAuthConfigForLoginServer("https://test.salesforce.com")

        // Then: Debug override config is returned
        assertEquals("Debug override consumer key should be returned", "debug-consumer-key", result.consumerKey)
        assertEquals("Debug override redirect URI should be returned", "debug://callback", result.redirectUri)
    }

    @Test
    fun test_givenNoDebugOverrideAndAppConfig_whenResolveOAuthConfig_thenReturnsAppConfig() = runBlocking {
        // Given: No debug override but app config returns custom config
        val appConfig = OAuthConfig(
            consumerKey = "app-consumer-key",
            redirectUri = "app://callback",
            scopes = listOf("api")
        )
        sdkManager.debugOverrideAppConfig = null
        sdkManager.appConfigForLoginHost = { appConfig }

        // When: Resolving OAuth config for a server
        val result = sdkManager.resolveOAuthConfigForLoginServer("https://test.salesforce.com")

        // Then: App config is returned
        assertEquals("App config consumer key should be returned", "app-consumer-key", result.consumerKey)
        assertEquals("App config redirect URI should be returned", "app://callback", result.redirectUri)
    }

    @Test
    fun test_givenNoDebugOverrideAndNoAppConfig_whenResolveOAuthConfig_thenReturnsBootConfig() = runBlocking {
        // Given: No debug override and app config returns null
        sdkManager.debugOverrideAppConfig = null
        sdkManager.appConfigForLoginHost = { null }

        // When: Resolving OAuth config for a server
        val result = sdkManager.resolveOAuthConfigForLoginServer("https://test.salesforce.com")

        // Then: Boot config is returned
        assertEquals(
            "Boot config consumer key should be returned",
            bootConfig.remoteAccessConsumerKey,
            result.consumerKey
        )
        assertEquals(
            "Boot config redirect URI should be returned",
            bootConfig.oauthRedirectURI,
            result.redirectUri
        )
    }

    @Test
    fun test_givenDebugBuildButNoOverride_whenResolveOAuthConfig_thenReturnsAppConfig() = runBlocking {
        // Given: Debug build but no debug override config set
        val appConfig = OAuthConfig(
            consumerKey = "app-consumer-key",
            redirectUri = "app://callback",
            scopes = listOf("api")
        )
        sdkManager.debugOverrideAppConfig = null
        sdkManager.appConfigForLoginHost = { appConfig }

        // When: Resolving OAuth config
        val result = sdkManager.resolveOAuthConfigForLoginServer("https://test.salesforce.com")

        // Then: App config is returned (debug override not applied without override set)
        assertEquals("App config should be used", "app-consumer-key", result.consumerKey)
    }

    @Test
    fun test_givenOAuthConfig_whenExtractConsumerKey_thenReturnsConsumerKey() = runBlocking {
        // Given: App config with specific consumer key
        val appConfig = OAuthConfig(
            consumerKey = "expected-consumer-key",
            redirectUri = "app://callback",
            scopes = listOf("api")
        )
        sdkManager.debugOverrideAppConfig = null
        sdkManager.appConfigForLoginHost = { appConfig }

        // When: Resolving OAuth config and extracting consumer key
        val result = sdkManager.resolveOAuthConfigForLoginServer("https://test.salesforce.com").consumerKey

        // Then: Consumer key from the resolved config is returned
        assertEquals("Consumer key should match", "expected-consumer-key", result)
    }

    @Test
    fun test_givenDebugOverride_whenExtractConsumerKey_thenReturnsDebugOverrideKey() = runBlocking {
        // Given: Debug override with specific consumer key
        val overrideConfig = OAuthConfig(
            consumerKey = "debug-key",
            redirectUri = "debug://callback",
            scopes = listOf("api")
        )
        sdkManager.debugOverrideAppConfig = overrideConfig

        // When: Resolving OAuth config and extracting consumer key
        val result = sdkManager.resolveOAuthConfigForLoginServer("https://test.salesforce.com").consumerKey

        // Then: Debug override consumer key is returned
        assertEquals("Debug override consumer key should be returned", "debug-key", result)
    }

    @Test
    fun test_givenDifferentServers_whenResolveOAuthConfig_thenPassesServerToAppConfig() = runBlocking {
        // Given: App config that returns different configs per server
        var lastServer: String? = null
        sdkManager.debugOverrideAppConfig = null
        sdkManager.appConfigForLoginHost = { server ->
            lastServer = server
            OAuthConfig(
                consumerKey = "key-for-$server",
                redirectUri = "app://callback",
                scopes = listOf("api")
            )
        }

        // When: Resolving OAuth config for specific server
        val result = sdkManager.resolveOAuthConfigForLoginServer("https://custom.salesforce.com")

        // Then: Server parameter is passed to appConfigForLoginHost
        assertEquals("Server should be passed to appConfigForLoginHost", "https://custom.salesforce.com", lastServer)
        assertEquals("Consumer key should include server", "key-for-https://custom.salesforce.com", result.consumerKey)
    }

    @Test
    fun test_givenReleaseBuildWithDebugOverride_whenResolveOAuthConfig_thenIgnoresDebugOverride() = runBlocking {
        // Given: Release build (isDebugBuild = false) with debug override set
        val overrideConfig = OAuthConfig(
            consumerKey = "debug-consumer-key",
            redirectUri = "debug://callback",
            scopes = listOf("api")
        )
        val appConfig = OAuthConfig(
            consumerKey = "app-consumer-key",
            redirectUri = "app://callback",
            scopes = listOf("api")
        )
        sdkManager.isDebugBuildOverride = false
        sdkManager.debugOverrideAppConfig = overrideConfig
        sdkManager.appConfigForLoginHost = { appConfig }

        // When: Resolving OAuth config
        val result = sdkManager.resolveOAuthConfigForLoginServer("https://test.salesforce.com")

        // Then: Debug override is ignored and app config is returned
        assertEquals("App config consumer key should be returned", "app-consumer-key", result.consumerKey)
        assertEquals("App config redirect URI should be returned", "app://callback", result.redirectUri)
    }

    /**
     * Test version of SalesforceSDKManager that doesn't interfere with other tests.
     */
    private class TestSalesforceSDKManager(
        context: Context,
        mainActivity: Class<out Activity>,
        loginActivity: Class<out Activity>,
    ) : SalesforceSDKManager(context, mainActivity, loginActivity) {

        var isDebugBuildOverride: Boolean? = null

        override val isDebugBuild: Boolean
            get() = isDebugBuildOverride ?: super.isDebugBuild

        companion object {
            private var TEST_INSTANCE: TestSalesforceSDKManager? = null

            fun init(context: Context, mainActivity: Class<out Activity>) {
                if (TEST_INSTANCE == null) {
                    TEST_INSTANCE = TestSalesforceSDKManager(context, mainActivity, LoginActivity::class.java)
                }
                initInternal(context)
            }

            fun getInstance(): SalesforceSDKManager {
                return TEST_INSTANCE ?: throw RuntimeException(
                    "Applications need to call TestSalesforceSDKManager.init() first."
                )
            }

            fun resetInstance() {
                TEST_INSTANCE = null
            }
        }
    }
}
