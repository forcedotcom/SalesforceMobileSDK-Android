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
package com.salesforce.androidsdk.ui

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.salesforce.androidsdk.R
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.BootConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginOptionsActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<LoginOptionsActivity>()

    // TODO: Remove if when min SDK version is 33
    @get:Rule
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    private var originalUseWebServer: Boolean = false
    private var originalUseHybridToken: Boolean = false
    private var originalSupportsWelcomeDiscovery: Boolean = false

    @Before
    fun setup() {
        // Save original values
        originalUseWebServer = SalesforceSDKManager.getInstance().useWebServerAuthentication
        originalUseHybridToken = SalesforceSDKManager.getInstance().useHybridAuthentication
        originalSupportsWelcomeDiscovery = SalesforceSDKManager.getInstance().supportsWelcomeDiscovery
        SalesforceSDKManager.getInstance().loginDevMenuReload = false
    }

    @After
    fun teardown() {
        // Restore original values
        SalesforceSDKManager.getInstance().useWebServerAuthentication = originalUseWebServer
        SalesforceSDKManager.getInstance().useHybridAuthentication = originalUseHybridToken
        SalesforceSDKManager.getInstance().supportsWelcomeDiscovery = originalSupportsWelcomeDiscovery
    }

    @Test
    fun loginOptionsActivity_DisplaysBootConfigValues() {
        val bootConfig = BootConfig.getBootConfig(composeTestRule.activity)
        
        // Check that boot config values are displayed
        composeTestRule.onNodeWithText(bootConfig.remoteAccessConsumerKey).assertIsDisplayed()
        composeTestRule.onNodeWithText(bootConfig.oauthRedirectURI).assertIsDisplayed()
        
        val scopes = bootConfig.oauthScopes?.joinToString(separator = ", ") ?: ""
        if (scopes.isNotEmpty()) {
            composeTestRule.onNodeWithText(scopes).assertIsDisplayed()
        }
    }

    @Test
    fun loginOptionsActivity_WebServerFlowToggle_UpdatesSdkManager() {
        // Set initial state via the activity's LiveData
        composeTestRule.activity.runOnUiThread {
            SalesforceSDKManager.getInstance().useWebServerAuthentication = false
            composeTestRule.activity.useWebServer.value = false
        }
        composeTestRule.waitForIdle()
        
        // Verify initial state
        assertFalse(
            "Use Web Server Authentication should be disabled initially",
            SalesforceSDKManager.getInstance().useWebServerAuthentication
        )

        val toggleDescriptor = composeTestRule.activity.getString(R.string.sf__login_options_webserver_toggle_content_description)
        val webserverToggle = composeTestRule.onNodeWithContentDescription(toggleDescriptor)
        webserverToggle.assertIsDisplayed()
        webserverToggle.assertIsOff()

        // Verify reload flag is initially false
        assertFalse(
            "loginDevMenuReload should be false initially",
            SalesforceSDKManager.getInstance().loginDevMenuReload
        )
        
        // Click
        webserverToggle.performClick()
        composeTestRule.waitForIdle()

        webserverToggle.assertIsOn()
        
        // Verify the SDK manager was updated
        assertTrue(
            "Use Web Server Authentication should be enabled",
            SalesforceSDKManager.getInstance().useWebServerAuthentication
        )
        
        // Verify the reload flag was set
        assertTrue(
            "loginDevMenuReload should be true after toggle",
            SalesforceSDKManager.getInstance().loginDevMenuReload
        )
    }

    @Test
    fun loginOptionsActivity_HybridAuthTokenToggle_UpdatesSdkManager() {
        // Set initial state via the activity's LiveData
        composeTestRule.activity.runOnUiThread {
            SalesforceSDKManager.getInstance().useHybridAuthentication = false
            composeTestRule.activity.useHybridToken.value = false
        }
        composeTestRule.waitForIdle()
        
        // Verify initial state
        assertFalse(
            "Use Hybrid Authentication should be disabled initially",
            SalesforceSDKManager.getInstance().useHybridAuthentication
        )
        
        // Find and click the toggle
        val toggleDescriptor = composeTestRule.activity.getString(R.string.sf__login_options_hybrid_toggle_content_description)
        val hybridToggle = composeTestRule.onNodeWithContentDescription(toggleDescriptor)
        hybridToggle.assertIsDisplayed()
        hybridToggle.assertIsOff()

        // Verify reload flag is initially false
        assertFalse(
            "loginDevMenuReload should be false initially",
            SalesforceSDKManager.getInstance().loginDevMenuReload
        )
        
        // Click
        hybridToggle.performClick()
        composeTestRule.waitForIdle()
        
        hybridToggle.assertIsOn()
        
        // Verify the SDK manager was updated
        assertTrue(
            "Use Hybrid Authentication should be enabled",
            SalesforceSDKManager.getInstance().useHybridAuthentication
        )
        
        // Verify the reload flag was set
        assertTrue(
            "loginDevMenuReload should be true after toggle",
            SalesforceSDKManager.getInstance().loginDevMenuReload
        )
    }

    @Test
    fun loginOptionsActivity_WelcomeDiscoveryToggle_UpdatesSdkManager() {
        // Set initial state via the activity's LiveData
        composeTestRule.activity.runOnUiThread {
            SalesforceSDKManager.getInstance().supportsWelcomeDiscovery = false
            composeTestRule.activity.supportWelcomeDiscovery.value = false
        }
        composeTestRule.waitForIdle()
        
        // Verify initial state
        assertFalse(
            "Support Welcome Discovery should be disabled initially",
            SalesforceSDKManager.getInstance().supportsWelcomeDiscovery
        )
        
        // Find and click the toggle
        val toggleDescriptor = composeTestRule.activity.getString(R.string.sf__login_options_welcome_toggle_content_description)
        val welcomeToggle = composeTestRule.onNodeWithContentDescription(toggleDescriptor)
        welcomeToggle.assertIsDisplayed()
        welcomeToggle.assertIsOff()

        // Verify reload flag is initially false
        assertFalse(
            "loginDevMenuReload should be false initially",
            SalesforceSDKManager.getInstance().loginDevMenuReload
        )
        
        // Click
        welcomeToggle.performClick()
        composeTestRule.waitForIdle()
        
        welcomeToggle.assertIsOn()
        
        // Verify the SDK manager was updated
        assertTrue(
            "Support Welcome Discovery should be enabled",
            SalesforceSDKManager.getInstance().supportsWelcomeDiscovery
        )
        
        // Verify the reload flag was set
        assertTrue(
            "loginDevMenuReload should be true after toggle",
            SalesforceSDKManager.getInstance().loginDevMenuReload
        )
    }

    @Test
    fun loginOptionsActivity_InitialToggleStates_ReflectSdkManagerValues() {
        // Set known states via the activity's LiveData
        composeTestRule.activity.runOnUiThread {
            SalesforceSDKManager.getInstance().useWebServerAuthentication = true
            SalesforceSDKManager.getInstance().useHybridAuthentication = false
            composeTestRule.activity.useWebServer.value = true
            composeTestRule.activity.useHybridToken.value = false
        }
        composeTestRule.waitForIdle()
        
        // Verify states match SDK manager
        assertEquals(
            true,
            composeTestRule.activity.useWebServer.value
        )
        assertEquals(
            false,
            composeTestRule.activity.useHybridToken.value
        )
    }

    @Test
    fun loginOptionsActivity_DynamicBootConfigToggle_ShowsInputFields() {
        // Get content descriptions
        val dynamicToggleDesc = composeTestRule.activity.getString(R.string.sf__login_options_dynamic_config_toggle_content_description)
        val consumerKeyFieldDesc = composeTestRule.activity.getString(R.string.sf__login_options_consumer_key_field_content_description)
        val redirectUriFieldDesc = composeTestRule.activity.getString(R.string.sf__login_options_redirect_uri_field_content_description)
        val scopesFieldDesc = composeTestRule.activity.getString(R.string.sf__login_options_scopes_field_content_description)
        
        // Initially, dynamic config fields should not exist
        val dynamicToggle = composeTestRule.onNodeWithContentDescription(dynamicToggleDesc)
        dynamicToggle.assertIsDisplayed()
        dynamicToggle.assertIsOff()
        
        composeTestRule.onNodeWithContentDescription(consumerKeyFieldDesc).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(redirectUriFieldDesc).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(scopesFieldDesc).assertDoesNotExist()
        
        // Click to enable dynamic config
        dynamicToggle.performClick()
        composeTestRule.waitForIdle()
        
        dynamicToggle.assertIsOn()
        
        // Now the input fields should be visible and accept text input
        val consumerKeyField = composeTestRule.onNodeWithContentDescription(consumerKeyFieldDesc)
        val redirectUriField = composeTestRule.onNodeWithContentDescription(redirectUriFieldDesc)
        val scopesField = composeTestRule.onNodeWithContentDescription(scopesFieldDesc)
        
        consumerKeyField.assertIsDisplayed()
        redirectUriField.assertIsDisplayed()
        scopesField.assertIsDisplayed()
        
        // Test text input
        consumerKeyField.performTextInput("test_consumer_key")
        redirectUriField.performTextInput("test://redirect")
        scopesField.performTextInput("api web")
        
        composeTestRule.waitForIdle()
        
        // Verify the text was entered
        consumerKeyField.assertTextEquals("Consumer Key", "test_consumer_key")
        redirectUriField.assertTextEquals("Redirect URI", "test://redirect")
        scopesField.assertTextEquals("Scopes", "api web")
    }

    @Test
    fun loginOptionsActivity_MultipleToggles_WorkIndependently() {
        // Set initial states
        composeTestRule.activity.runOnUiThread {
            SalesforceSDKManager.getInstance().useWebServerAuthentication = false
            SalesforceSDKManager.getInstance().useHybridAuthentication = false
            composeTestRule.activity.useWebServer.value = false
            composeTestRule.activity.useHybridToken.value = false
        }
        composeTestRule.waitForIdle()
        
        // Verify reload flag is initially false
        assertFalse(
            "loginDevMenuReload should be false initially",
            SalesforceSDKManager.getInstance().loginDevMenuReload
        )
        
        // Toggle web server flow
        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.sf__login_options_webserver_toggle_content_description)
        ).performClick()
        composeTestRule.waitForIdle()
        
        // Verify only web server flow changed
        assertTrue(SalesforceSDKManager.getInstance().useWebServerAuthentication)
        assertFalse(SalesforceSDKManager.getInstance().useHybridAuthentication)
        
        // Verify the reload flag was set
        assertTrue(
            "loginDevMenuReload should be true after first toggle",
            SalesforceSDKManager.getInstance().loginDevMenuReload
        )
        
        // Toggle hybrid auth token
        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.sf__login_options_hybrid_toggle_content_description)
        ).performClick()
        composeTestRule.waitForIdle()
        
        // Verify both are now enabled
        assertTrue(SalesforceSDKManager.getInstance().useWebServerAuthentication)
        assertTrue(SalesforceSDKManager.getInstance().useHybridAuthentication)
        
        // Verify the reload flag is still true
        assertTrue(
            "loginDevMenuReload should still be true after second toggle",
            SalesforceSDKManager.getInstance().loginDevMenuReload
        )
    }
}
