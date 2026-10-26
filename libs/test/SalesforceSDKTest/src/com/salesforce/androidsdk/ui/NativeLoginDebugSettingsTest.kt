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
package com.salesforce.androidsdk.ui

import android.app.Activity
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.app.Features.FEATURE_AUTH_TYPE_NATIVE
import com.salesforce.androidsdk.app.Features.FEATURE_NATIVE_LOGIN
import com.salesforce.androidsdk.app.SalesforceSDKManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val CONSUMER_KEY = "test-consumer-key"
private const val REDIRECT_URI = "https://example.com/callback"
private const val COMMUNITY_URL = "https://example.my.site.com/community"

@RunWith(AndroidJUnit4::class)
class NativeLoginDebugSettingsTest {

    private lateinit var context: Context
    private lateinit var sdkManager: SalesforceSDKManager
    private lateinit var settings: NativeLoginDebugSettings
    private val completeConfig = NativeLoginDebugConfig(CONSUMER_KEY, REDIRECT_URI, COMMUNITY_URL)

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        sdkManager = mockk(relaxed = true) {
            every { isDevSupportEnabled() } returns true
            every { nativeLoginActivity } returns null
        }
        settings = NativeLoginDebugSettings(context, sdkManager)
        settings.reset()
    }

    @After
    fun tearDown() {
        settings.reset()
        SalesforceSDKManager.getInstance().debugLoginSettings.reset()
    }

    @Test
    fun test_givenNoSettings_whenRead_thenBrowserModeAndEmptyConfig() {
        assertTrue(settings.isAvailable)
        assertEquals(AuthenticationMode.BROWSER, settings.mode)
        assertEquals(NativeLoginDebugConfig(), settings.config)
        assertFalse(settings.config.isComplete)
    }

    @Test
    fun test_givenSavedSettings_whenReadByNewInstance_thenPersisted() {
        settings.config = NativeLoginDebugConfig(" $CONSUMER_KEY ", "$REDIRECT_URI ", " $COMMUNITY_URL", useDPoP = false, useHybridFlow = false)
        settings.mode = AuthenticationMode.NATIVE

        val reloaded = NativeLoginDebugSettings(context, sdkManager)
        assertEquals(AuthenticationMode.NATIVE, reloaded.mode)
        assertEquals(completeConfig.copy(useDPoP = false, useHybridFlow = false), reloaded.config)
    }

    @Test
    fun test_givenDevSupportDisabled_whenUsed_thenInert() {
        settings.config = completeConfig
        settings.mode = AuthenticationMode.NATIVE
        every { sdkManager.isDevSupportEnabled() } returns false

        assertFalse(settings.isAvailable)
        assertEquals(AuthenticationMode.BROWSER, settings.mode)
        settings.config = NativeLoginDebugConfig()
        assertThrows(IllegalStateException::class.java) { settings.apply("{}") }

        every { sdkManager.isDevSupportEnabled() } returns true
        assertEquals(AuthenticationMode.NATIVE, settings.mode)
        assertEquals(completeConfig, settings.config)
    }

    @Test
    fun test_givenAppNativeLoginActivity_whenUsed_thenUnavailable() {
        settings.config = completeConfig
        settings.mode = AuthenticationMode.NATIVE
        every { sdkManager.nativeLoginActivity } returns Activity::class.java

        assertFalse(settings.isAvailable)
        assertEquals(AuthenticationMode.BROWSER, settings.mode)
    }

    @Test
    fun test_givenNativeMode_whenSwitchedToBrowser_thenNativeFeaturesUnregistered() {
        settings.config = completeConfig
        settings.mode = AuthenticationMode.NATIVE
        verify(exactly = 0) { sdkManager.unregisterUsedAppFeature(any()) }

        settings.mode = AuthenticationMode.BROWSER

        verify { sdkManager.unregisterUsedAppFeature(FEATURE_NATIVE_LOGIN) }
        verify { sdkManager.unregisterUsedAppFeature(FEATURE_AUTH_TYPE_NATIVE) }
    }

    @Test
    fun test_givenCompleteJson_whenApplied_thenConfigAndModeSaved() {
        settings.apply(
            """
            {"consumerKey": "$CONSUMER_KEY", "redirectUri": "$REDIRECT_URI", "communityUrl": "$COMMUNITY_URL",
             "useDPoP": false, "useHybridFlow": false, "authenticationMode": "Native"}
            """.trimIndent(),
        )

        assertEquals(AuthenticationMode.NATIVE, settings.mode)
        assertEquals(completeConfig.copy(useDPoP = false, useHybridFlow = false), settings.config)
    }

    @Test
    fun test_givenPartialJson_whenApplied_thenAbsentKeysKept() {
        settings.config = completeConfig
        settings.mode = AuthenticationMode.NATIVE

        settings.apply("""{"useDPoP": false}""")

        assertEquals(AuthenticationMode.NATIVE, settings.mode)
        assertEquals(completeConfig.copy(useDPoP = false), settings.config)
    }

    @Test
    fun test_givenInvalidJson_whenApplied_thenRejectedAndNothingSaved() {
        settings.config = completeConfig
        listOf(
            "not json",
            """{"useDPoP": "maybe"}""",
            """{"authenticationMode": "carrier-pigeon"}""",
            """{"redirectUri": "http://example.com/callback", "authenticationMode": "native"}""",
            """{"consumerKey": " ", "authenticationMode": "native"}""",
        ).forEach { json ->
            assertThrows(json, IllegalArgumentException::class.java) { settings.apply(json) }
        }

        assertEquals(AuthenticationMode.BROWSER, settings.mode)
        assertEquals(completeConfig, settings.config)
    }

    @Test
    fun test_givenBrowserMode_whenIncompleteJsonApplied_thenSaved() {
        settings.apply("""{"consumerKey": "$CONSUMER_KEY"}""")

        assertEquals(AuthenticationMode.BROWSER, settings.mode)
        assertEquals(NativeLoginDebugConfig(consumerKey = CONSUMER_KEY), settings.config)
    }

    @Test
    fun test_givenSettings_whenReset_thenDefaults() {
        settings.config = completeConfig.copy(useDPoP = false)
        settings.mode = AuthenticationMode.NATIVE

        settings.reset()

        assertEquals(AuthenticationMode.BROWSER, settings.mode)
        assertEquals(NativeLoginDebugConfig(), settings.config)
    }

    @Test
    fun test_givenConfigs_whenValidated_thenFirstProblemReported() {
        assertNull(completeConfig.validationError)
        assertEquals("Client ID is required", completeConfig.copy(consumerKey = "").validationError)
        assertEquals(
            "Native Callback URL must be an https:// URL",
            completeConfig.copy(redirectUri = "com.example.app://callback").validationError,
        )
        assertEquals(
            "Community Login URL must be an https:// URL",
            completeConfig.copy(communityUrl = "https://").validationError,
        )
    }

    @Test
    fun test_givenSdkManager_whenModeChanges_thenLoginActivityClassFollows() {
        val sdk = SalesforceSDKManager.getInstance()
        val debugSettings = sdk.debugLoginSettings
        val originalUseDPoP = sdk.useDPoP
        val originalUseHybrid = sdk.useHybridAuthentication

        assertEquals(sdk.webViewLoginActivityClass, sdk.loginActivityClass)

        debugSettings.config = completeConfig.copy(useDPoP = !originalUseDPoP, useHybridFlow = !originalUseHybrid)
        debugSettings.mode = AuthenticationMode.NATIVE
        assertEquals(DebugNativeLoginActivity::class.java, sdk.loginActivityClass)
        assertEquals(originalUseDPoP, sdk.useDPoP)
        assertEquals(originalUseHybrid, sdk.useHybridAuthentication)

        debugSettings.mode = AuthenticationMode.BROWSER
        assertEquals(sdk.webViewLoginActivityClass, sdk.loginActivityClass)
    }
}
