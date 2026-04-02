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
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.salesforce.androidsdk.R
import com.salesforce.androidsdk.app.SalesforceSDKManager
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DevInfoActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<DevInfoActivity>()

    // TODO: Remove if when min SDK version is 33
    @get:Rule
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    @Test
    fun devInfoActivity_DisplaysTitle() {
        val titleText = composeTestRule.activity.getString(R.string.sf__dev_support_title)
        composeTestRule.onNodeWithText(titleText).assertIsDisplayed()
    }

    @Test
    fun devInfoActivity_DisplaysBasicSdkInfo() {
        val devSupportInfo = SalesforceSDKManager.getInstance().devSupportInfo
        
        // Verify basic SDK information is displayed (non-collapsible)
        val basicInfo = devSupportInfo.basicInfo!!
        
        composeTestRule.onNodeWithText("SDK Version").assertIsDisplayed()
        val sdkVersion = basicInfo.find { it.first == "SDK Version" }?.second!!
        composeTestRule.onNodeWithText(sdkVersion).assertIsDisplayed()
        
        composeTestRule.onNodeWithText("App Type").assertIsDisplayed()
        val appType = basicInfo.find { it.first == "App Type" }?.second!!
        composeTestRule.onNodeWithText(appType).assertIsDisplayed()
        
        composeTestRule.onNodeWithText("User Agent").assertIsDisplayed()
        val userAgent = basicInfo.find { it.first == "User Agent" }?.second!!
        composeTestRule.onNodeWithText(userAgent).assertIsDisplayed()
    }

    @Test
    fun devInfoActivity_DisplaysCollapsibleSections() {
        // Verify all collapsible section headers are displayed
        composeTestRule.onNodeWithText("Authentication Configuration").assertIsDisplayed()
        composeTestRule.onNodeWithText("Boot Configuration").assertIsDisplayed()
        composeTestRule.onNodeWithText("Runtime Configuration").assertIsDisplayed()
    }

    @Test
    fun devInfoActivity_CollapsibleSections_StartCollapsed() {
        val devSupportInfo = SalesforceSDKManager.getInstance().devSupportInfo
        
        // Verify sections start collapsed by checking that content is not initially visible
        // Check that auth config items are not displayed initially
        devSupportInfo.authConfigSection?.let { (_, items) ->
            if (items.isNotEmpty()) {
                val firstAuthConfigKey = items[0].first
                composeTestRule.onNodeWithText(firstAuthConfigKey).assertIsNotDisplayed()
            }
        }
    }

    @Test
    fun devInfoActivity_CollapsibleSection_CanExpand() {
        val devSupportInfo = SalesforceSDKManager.getInstance().devSupportInfo
        
        // Click on Authentication Configuration section to expand it
        composeTestRule.onNodeWithText("Authentication Configuration").performClick()
        
        // Verify content is now visible
        devSupportInfo.authConfigSection?.let { (_, items) ->
            if (items.isNotEmpty()) {
                val firstAuthConfigKey = items[0].first
                composeTestRule.onNodeWithText(firstAuthConfigKey).assertIsDisplayed()
            }
        }
    }

    @Test
    fun devInfoActivity_CollapsibleSection_CanCollapse() {
        val devSupportInfo = SalesforceSDKManager.getInstance().devSupportInfo
        
        // Expand the section
        composeTestRule.onNodeWithText("Boot Configuration").performClick()
        
        // Verify content is visible
        devSupportInfo.bootConfigSection?.let { (_, items) ->
            if (items.isNotEmpty()) {
                val firstBootConfigKey = items[0].first
                composeTestRule.onNodeWithText(firstBootConfigKey).assertIsDisplayed()
            }
        }
        
        // Collapse the section
        composeTestRule.onNodeWithText("Boot Configuration").performClick()
        
        // Verify content is hidden again
        devSupportInfo.bootConfigSection?.let { (_, items) ->
            if (items.isNotEmpty()) {
                val firstBootConfigKey = items[0].first
                composeTestRule.onNodeWithText(firstBootConfigKey).assertIsNotDisplayed()
            }
        }
    }

    @Test
    fun devInfoActivity_DisplaysBootConfigSection() {
        val devSupportInfo = SalesforceSDKManager.getInstance().devSupportInfo
        
        // Expand Boot Configuration section
        composeTestRule.onNodeWithText("Boot Configuration")
            .performScrollTo()
            .performClick()
        
        // Verify boot config items are displayed
        devSupportInfo.bootConfigSection?.let { (_, items) ->
            assertTrue("Boot config should not be empty", items.isNotEmpty())
            
            items.forEach { (key, _) ->
                composeTestRule.onNodeWithText(key)
                    .performScrollTo()
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun devInfoActivity_DisplaysRuntimeConfigSection() {
        val devSupportInfo = SalesforceSDKManager.getInstance().devSupportInfo
        
        // Expand Runtime Configuration section
        composeTestRule.onNodeWithText("Runtime Configuration")
            .performScrollTo()
            .performClick()
        
        // Verify runtime config items are displayed
        devSupportInfo.runtimeConfigSection?.let { (_, items) ->
            assertTrue("Runtime config should not be empty", items.isNotEmpty())
            
            items.forEach { (key, _) ->
                composeTestRule.onNodeWithText(key).assertIsDisplayed()
            }
        }
    }
}
