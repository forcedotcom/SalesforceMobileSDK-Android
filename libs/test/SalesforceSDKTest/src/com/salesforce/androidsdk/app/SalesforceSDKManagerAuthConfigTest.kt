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
package com.salesforce.androidsdk.app

import android.app.Activity
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.MainActivity
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.config.OAuthConfig
import com.salesforce.androidsdk.ui.LoginActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for SalesforceSDKManager's appConfigForLoginHost and debugOverrideAppConfig functionality.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class SalesforceSDKManagerAuthConfigTest {

    private lateinit var sdkManager: TestSalesforceSDKManager
    private lateinit var targetContext: Context

    @Before
    fun setUp() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        TestSalesforceSDKManager.init(targetContext, MainActivity::class.java)
        sdkManager = TestSalesforceSDKManager.getInstance() as TestSalesforceSDKManager
        sdkManager.isTestRun = true
    }

    @After
    fun tearDown() {
        // Reset debug override
        sdkManager.debugOverrideAppConfig = null
        TestSalesforceSDKManager.resetInstance()
    }

    @Test
    fun testAppConfigForLoginHostDefaultBehavior() = runBlocking {
        val bootConfig = BootConfig.getBootConfig(InstrumentationRegistry.getInstrumentation().targetContext)
        // Default implementation should return config from BootConfig
        val config = sdkManager.appConfigForLoginHost("https://login.salesforce.com")!!

        assertNotNull("Config should not be null", config)
        assertEquals(
            "Consumer key should match BootConfig",
            bootConfig.remoteAccessConsumerKey,
            config.consumerKey
        )
        assertEquals(
            "Redirect URI should match BootConfig",
            bootConfig.oauthRedirectURI,
            config.redirectUri
        )
    }

    @Test
    fun testAppConfigForLoginHostWithDifferentServers() = runBlocking {
        // Default implementation ignores server parameter and returns same config
        val loginConfig = sdkManager.appConfigForLoginHost("https://login.salesforce.com")
        val testConfig = sdkManager.appConfigForLoginHost("https://test.salesforce.com")
        val customConfig = sdkManager.appConfigForLoginHost("https://custom.my.salesforce.com")

        assertEquals("All servers should return same config by default", loginConfig, testConfig)
        assertEquals("All servers should return same config by default", loginConfig, customConfig)
    }

    @Test
    fun testAppConfigForLoginHostCanBeReassigned() = runBlocking {
        // First implementation
        sdkManager.appConfigForLoginHost = { _ ->
            OAuthConfig("key1", "uri1", listOf("api"))
        }
        val config1 = sdkManager.appConfigForLoginHost("https://test.com")!!
        assertEquals("key1", config1.consumerKey)

        // Reassign to different implementation
        sdkManager.appConfigForLoginHost = { _ ->
            OAuthConfig("key2", "uri2", listOf("web"))
        }
        val config2 = sdkManager.appConfigForLoginHost("https://test.com")!!
        assertEquals("key2", config2.consumerKey)
    }

    @Test
    fun testDebugOverrideAppConfigDefaultIsNull() {
        assertNull("debugOverrideAppConfig should be null by default", sdkManager.debugOverrideAppConfig)
    }

    @Test
    fun testDebugOverrideAppConfigCanBeSet() {
        val overrideConfig = OAuthConfig(
            consumerKey = "override_key",
            redirectUri = "override://callback",
            scopes = listOf("api", "web", "refresh_token")
        )

        sdkManager.debugOverrideAppConfig = overrideConfig

        assertNotNull("debugOverrideAppConfig should not be null", sdkManager.debugOverrideAppConfig)
        assertEquals("override_key", sdkManager.debugOverrideAppConfig?.consumerKey)
        assertEquals("override://callback", sdkManager.debugOverrideAppConfig?.redirectUri)
        assertEquals(listOf("api", "web", "refresh_token"), sdkManager.debugOverrideAppConfig?.scopes)
    }

    @Test
    fun testDebugOverrideAppConfigCanBeCleared() {
        val overrideConfig = OAuthConfig(
            consumerKey = "override_key",
            redirectUri = "override://callback",
            scopes = listOf("api")
        )

        sdkManager.debugOverrideAppConfig = overrideConfig
        assertNotNull("debugOverrideAppConfig should be set", sdkManager.debugOverrideAppConfig)

        sdkManager.debugOverrideAppConfig = null
        assertNull("debugOverrideAppConfig should be null after clearing", sdkManager.debugOverrideAppConfig)
    }

    /**
     * Test version of SalesforceSDKManager that doesn't interfere with other tests.
     */
    private class TestSalesforceSDKManager(
        context: Context,
        mainActivity: Class<out Activity>,
        loginActivity: Class<out Activity>,
    ) : SalesforceSDKManager(context, mainActivity, loginActivity) {

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
