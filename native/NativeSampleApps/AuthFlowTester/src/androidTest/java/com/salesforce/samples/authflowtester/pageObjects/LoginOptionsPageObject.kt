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
package com.salesforce.samples.authflowtester.pageObjects

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso.closeSoftKeyboard
import com.salesforce.androidsdk.R
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig
import com.salesforce.samples.authflowtester.testUtility.ScopeSelection
import com.salesforce.samples.authflowtester.testUtility.testConfig

/**
 * Page object for the SDK's Login Options screen.
 */
class LoginOptionsPageObject(composeTestRule: ComposeTestRule): BasePageObject(composeTestRule) {

    fun enableWebServerFlow() = toggleIfOff(
        getString(R.string.sf__login_options_webserver_toggle_content_description)
    )

    fun disableWebServerFlow() = toggleIfOn(
        getString(R.string.sf__login_options_webserver_toggle_content_description)
    )

    fun enableHybridAuthToken() = toggleIfOff(
        getString(R.string.sf__login_options_hybrid_toggle_content_description)
    )

    fun disableHybridAuthToken() = toggleIfOn(
        getString(R.string.sf__login_options_hybrid_toggle_content_description)
    )

    fun setOverrideBootConfig(knownAppConfig: KnownAppConfig, scopeSelection: ScopeSelection = ScopeSelection.ALL) {
        enableOverrideBootConfig()

        with(testConfig.getApp(knownAppConfig)) {
            composeTestRule.onNodeWithContentDescription(
                getString(R.string.sf__login_options_consumer_key_field_content_description)
            ).performTextReplacement(consumerKey)

            composeTestRule.onNodeWithContentDescription(
                getString(R.string.sf__login_options_redirect_uri_field_content_description)
            ).performTextReplacement(redirectUri)

            composeTestRule.onNodeWithContentDescription(
                getString(R.string.sf__login_options_scopes_field_content_description)
            ).performTextReplacement(scopesToRequest(scopeSelection))
        }

        saveOverrideBootConfig()
    }

    private fun enableOverrideBootConfig() = toggleIfOff(
        getString(R.string.sf__login_options_dynamic_config_toggle_content_description)
    )

    private fun disableOverrideBootConfig() = toggleIfOn(
        getString(R.string.sf__login_options_dynamic_config_toggle_content_description)
    )

    private fun saveOverrideBootConfig() {
        closeSoftKeyboard()
        composeTestRule.onNodeWithContentDescription(
            getString(R.string.sf__login_options_save_button_content_description)
        ).performScrollTo().performClick()
        composeTestRule.waitForIdle()
    }

    private fun isToggleOn(contentDescription: String): Boolean =
        composeTestRule.onNodeWithContentDescription(contentDescription)
            .fetchSemanticsNode()
            .config[SemanticsProperties.ToggleableState] == ToggleableState.On

    /** Clicks the toggle only if it is currently off. */
    private fun toggleIfOff(contentDescription: String) {
        if (!isToggleOn(contentDescription)) {
            composeTestRule.onNodeWithContentDescription(contentDescription).performClick()
            composeTestRule.waitForIdle()
        }
    }

    /** Clicks the toggle only if it is currently on. */
    private fun toggleIfOn(contentDescription: String) {
        if (isToggleOn(contentDescription)) {
            composeTestRule.onNodeWithContentDescription(contentDescription).performClick()
            composeTestRule.waitForIdle()
        }
    }
}