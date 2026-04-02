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
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.salesforce.androidsdk.R
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.config.OAuthConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val CONSUMER_KEY_LABEL = "Consumer Key"
private const val REDIRECT_URI_LABEL = "Redirect URI"
private const val SCOPES_LABEL = "Scopes"

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
    private lateinit var dynamicToggle: SemanticsNodeInteraction
    private lateinit var consumerKeyField: SemanticsNodeInteraction
    private lateinit var redirectUriField: SemanticsNodeInteraction
    private lateinit var scopesField: SemanticsNodeInteraction
    private lateinit var webserverToggle: SemanticsNodeInteraction
    private lateinit var hybridToggle: SemanticsNodeInteraction
    private lateinit var saveButton: SemanticsNodeInteraction

    @Before
    fun setup() {
        // Save original values
        originalUseWebServer = SalesforceSDKManager.getInstance().useWebServerAuthentication
        originalUseHybridToken = SalesforceSDKManager.getInstance().useHybridAuthentication
        SalesforceSDKManager.getInstance().loginDevMenuReload = false

        dynamicToggle = composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.sf__login_options_dynamic_config_toggle_content_description),
        )
        consumerKeyField = composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.sf__login_options_consumer_key_field_content_description),
        )
        redirectUriField = composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.sf__login_options_redirect_uri_field_content_description),
        )
        scopesField = composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.sf__login_options_scopes_field_content_description),
        )
        webserverToggle = composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.sf__login_options_webserver_toggle_content_description),
        )
        hybridToggle = composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.sf__login_options_hybrid_toggle_content_description),
        )
        saveButton = composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.sf__server_url_save),
        )
    }

    @After
    fun teardown() {
        // Restore original values
        SalesforceSDKManager.getInstance().useWebServerAuthentication = originalUseWebServer
        SalesforceSDKManager.getInstance().useHybridAuthentication = originalUseHybridToken
        SalesforceSDKManager.getInstance().debugOverrideAppConfig = null
        SalesforceSDKManager.getInstance().loginDevMenuReload = false
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
        dynamicToggle.assertIsDisplayed()
        dynamicToggle.assertIsOff()
        
        consumerKeyField.assertDoesNotExist()
        redirectUriField.assertDoesNotExist()
        scopesField.assertDoesNotExist()
        
        // Click to enable dynamic config
        dynamicToggle.performClick()
        composeTestRule.waitForIdle()
        
        dynamicToggle.assertIsOn()
        saveButton.performScrollTo()
        consumerKeyField.assertIsDisplayed()
        redirectUriField.assertIsDisplayed()
        scopesField.assertIsDisplayed()
        
        // Test text input
        consumerKeyField.performTextInput("test_consumer_key")
        redirectUriField.performTextInput("test://redirect")
        scopesField.performTextInput("api web")
        
        composeTestRule.waitForIdle()
        
        // Verify the text was entered
        consumerKeyField.assertTextEquals(CONSUMER_KEY_LABEL, "test_consumer_key")
        redirectUriField.assertTextEquals(REDIRECT_URI_LABEL, "test://redirect")
        scopesField.assertTextEquals(SCOPES_LABEL, "api web")
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
        webserverToggle.performClick()
        composeTestRule.waitForIdle()
        
        // Verify only web server flow changed
        assertTrue(SalesforceSDKManager.getInstance().useWebServerAuthentication)
        assertFalse(SalesforceSDKManager.getInstance().useHybridAuthentication)
        
        // Toggle hybrid auth token
        hybridToggle.performClick()
        composeTestRule.waitForIdle()
        
        // Verify both are now enabled
        assertTrue(SalesforceSDKManager.getInstance().useWebServerAuthentication)
        assertTrue(SalesforceSDKManager.getInstance().useHybridAuthentication)
    }

    @Test
    fun bootConfigView_WithEmptyFields_DisablesSaveButton() {
        // Enable dynamic config
        dynamicToggle.performClick()
        composeTestRule.waitForIdle()

        // Save button should be disabled when fields are empty
        saveButton.performScrollTo()
        saveButton.assertIsDisplayed()
        saveButton.assertIsNotEnabled()
    }

    @Test
    fun bootConfigView_TappingSaveButton_SetsDebugOverrideAppConfig() {
        // Enable dynamic config
        dynamicToggle.performClick()
        composeTestRule.waitForIdle()

        consumerKeyField.performTextInput("override_key")
        redirectUriField.performTextInput("override://uri")
        scopesField.performTextInput("api web")
        composeTestRule.waitForIdle()

        // Click save button
        saveButton.performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Verify debugOverrideAppConfig was set
        val overrideConfig = SalesforceSDKManager.getInstance().debugOverrideAppConfig
        assertNotNull("debugOverrideAppConfig should be set", overrideConfig)
        assertEquals("override_key", overrideConfig?.consumerKey)
        assertEquals("override://uri", overrideConfig?.redirectUri)
        assertEquals(listOf("api", "web"), overrideConfig?.scopes)
    }

    @Test
    fun bootConfigView_SaveWithOnlyRequiredFields_CreatesOAuthConfig() {
        // Enable dynamic config
        dynamicToggle.performClick()
        composeTestRule.waitForIdle()

        consumerKeyField.performTextInput("minimal_key")
        redirectUriField.performTextInput("minimal://uri")
        composeTestRule.waitForIdle()

        // Click save button
        saveButton.performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Verify debugOverrideAppConfig was set with null scopes
        val overrideConfig = SalesforceSDKManager.getInstance().debugOverrideAppConfig
        assertNotNull("debugOverrideAppConfig should be set", overrideConfig)
        assertEquals("minimal_key", overrideConfig?.consumerKey)
        assertEquals("minimal://uri", overrideConfig?.redirectUri)
        assertNull("Scopes should be null when not provided", overrideConfig?.scopes)
    }

    @Test
    fun bootConfigView_TogglingOffDebugOverride_ClearsValues() {
        // Set an override first
        SalesforceSDKManager.getInstance().debugOverrideAppConfig = OAuthConfig(
            "existing_key",
            "existing://uri",
            listOf("api")
        )

        // Recreate the activity to show the config we just set
        with(composeTestRule.activity) {
            runOnUiThread { recreate() }
        }

        // Enable dynamic config
        dynamicToggle.performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertNull("Override should be cleared", SalesforceSDKManager.getInstance().debugOverrideAppConfig)

        // Toggle off dynamic config
        dynamicToggle.performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Verify override was cleared
        assertNull("Override should be cleared when toggling off", SalesforceSDKManager.getInstance().debugOverrideAppConfig)
    }

    @Test
    fun bootConfigView_PrePopulatesWithExistingConfig() {
        val overrideKey = "existing_key"
        val overrideUri = "existing://uri"
        val overrideScopes = listOf("api", "web")
        val existingConfig = OAuthConfig(overrideKey, overrideUri, overrideScopes)
        SalesforceSDKManager.getInstance().debugOverrideAppConfig = existingConfig

        // Recreate the activity to show the config we just set
        with(composeTestRule.activity) {
            runOnUiThread { recreate() }
        }

        consumerKeyField.assertTextEquals(CONSUMER_KEY_LABEL, overrideKey)
        redirectUriField.assertTextEquals(REDIRECT_URI_LABEL, overrideUri)
        scopesField.assertTextEquals(SCOPES_LABEL, existingConfig.scopesString)
    }

    @Test
    fun leavingActivity_Sets_loginDevMenuReload() {
        assertFalse(SalesforceSDKManager.getInstance().loginDevMenuReload)
        composeTestRule.activity.finish()
        assertTrue(SalesforceSDKManager.getInstance().loginDevMenuReload)
    }
}