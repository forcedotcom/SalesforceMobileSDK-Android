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

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.lifecycle.MutableLiveData
import androidx.test.rule.GrantPermissionRule
import com.salesforce.androidsdk.R
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.AUTH_MODE_BROWSER
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.AUTH_MODE_NATIVE
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.CLIENT_ID_FIELD
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.CONFIG_ERROR
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.IMPORT_CONFIG_APPLY_BUTTON
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.IMPORT_CONFIG_BUTTON
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.IMPORT_CONFIG_FIELD
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.USE_DPOP_TOGGLE
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LoginOptionsAuthenticationModeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // TODO: Remove if when min SDK version is 33
    @get:Rule
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    private val mode = MutableLiveData(AuthenticationMode.BROWSER)
    private val config = MutableLiveData(NativeLoginDebugConfig())
    private val imported = mutableListOf<String>()

    @Before
    fun setUp() {
        composeTestRule.setContent {
            val selectedMode by mode.observeAsState(AuthenticationMode.BROWSER)
            LoginOptionsScreen(
                innerPadding = PaddingValues(0.dp),
                useWebServer = MutableLiveData(true),
                useHybridToken = MutableLiveData(true),
                forceAdvancedAuth = MutableLiveData(false),
                useEphemeralSessionForAdvancedAuth = MutableLiveData(false),
                useDPoP = MutableLiveData(false),
                overrideConfig = null,
                bootConfig = object : BootConfig() {
                    override fun getRemoteAccessConsumerKey() = "boot-consumer-key"
                    override fun getOauthRedirectURI() = "boot://callback"
                },
                sdkManager = null,
                authenticationModeContent = {
                    AuthenticationModeSection(mode, config) { json ->
                        require(json.startsWith("{")) { "Not JSON" }
                        imported.add(json)
                    }
                },
                showBrowserOptions = selectedMode != AuthenticationMode.NATIVE,
            )
        }
    }

    @Test
    fun test_givenBrowserMode_whenShown_thenBrowserOptionsAndNoNativeFields() {
        composeTestRule.onNodeWithTag(AUTH_MODE_BROWSER).assertIsSelected()
        webServerToggle().assertIsDisplayed()
        composeTestRule.onNodeWithTag(CLIENT_ID_FIELD).assertDoesNotExist()
    }

    @Test
    fun test_givenBrowserMode_whenNativeSelected_thenNativeFieldsAndNoBrowserOptions() {
        composeTestRule.onNodeWithTag(AUTH_MODE_NATIVE).performClick()

        assertEquals(AuthenticationMode.NATIVE, mode.value)
        composeTestRule.onNodeWithTag(CLIENT_ID_FIELD).assertIsDisplayed()
        composeTestRule.onNodeWithTag(CONFIG_ERROR).assertIsDisplayed()
        webServerToggle().assertDoesNotExist()
    }

    @Test
    fun test_givenNativeMode_whenFieldsEdited_thenConfigUpdated() {
        composeTestRule.onNodeWithTag(AUTH_MODE_NATIVE).performClick()
        composeTestRule.onNodeWithTag(CLIENT_ID_FIELD).performTextInput("typed-key")
        composeTestRule.onNodeWithTag(USE_DPOP_TOGGLE).performScrollTo().performClick()

        composeTestRule.onNodeWithTag(USE_DPOP_TOGGLE).assertIsOff()
        assertEquals(NativeLoginDebugConfig(consumerKey = "typed-key", useDPoP = false), config.value)
    }

    @Test
    fun test_givenImportDialog_whenInvalidThenValidJson_thenErrorThenApplied() {
        composeTestRule.onNodeWithTag(AUTH_MODE_NATIVE).performClick()
        composeTestRule.onNodeWithTag(IMPORT_CONFIG_BUTTON).performScrollTo().performClick()

        composeTestRule.onNodeWithTag(IMPORT_CONFIG_FIELD).performTextInput("oops")
        composeTestRule.onNodeWithTag(IMPORT_CONFIG_APPLY_BUTTON).performClick()
        composeTestRule.onNodeWithTag(IMPORT_CONFIG_FIELD).assertIsDisplayed()
        assertEquals(emptyList<String>(), imported)

        composeTestRule.onNodeWithTag(IMPORT_CONFIG_FIELD).performTextReplacement("{}")
        composeTestRule.onNodeWithTag(IMPORT_CONFIG_APPLY_BUTTON).performClick()
        composeTestRule.onNodeWithTag(IMPORT_CONFIG_FIELD).assertDoesNotExist()
        assertEquals(listOf("{}"), imported)
    }

    private fun webServerToggle() = composeTestRule.onNodeWithContentDescription(
        composeTestRule.activity.getString(R.string.sf__login_options_webserver_toggle_content_description),
    )
}
