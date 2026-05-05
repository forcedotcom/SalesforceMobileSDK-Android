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

import android.Manifest.permission.POST_NOTIFICATIONS
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues.Absolute
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.rule.GrantPermissionRule
import androidx.test.rule.GrantPermissionRule.grant
import com.salesforce.androidsdk.R.drawable.sf__salesforce_logo
import com.salesforce.androidsdk.R.string.sf__application_icon
import com.salesforce.androidsdk.R.string.sf__logout
import com.salesforce.androidsdk.R.string.sf__screen_lock_setup_button
import com.salesforce.androidsdk.R.string.sf__screen_lock_setup_required
import com.salesforce.androidsdk.ui.components.ScreenLockView
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScreenLockViewTest {

    @get:Rule
    val androidComposeTestRule = createAndroidComposeRule<ComponentActivity>()

    // TODO: Remove if when min SDK version is 33
    @get:Rule
    val permissionRule: GrantPermissionRule = if (SDK_INT >= TIRAMISU) {
        grant(POST_NOTIFICATIONS)
    } else {
        grant()
    }

    @Test
    fun screenLockView_Default_DisplaysCorrectly() {
        androidComposeTestRule.setContent {
            TestableScreenLockView()
        }

        val logoutButton = androidComposeTestRule.onNodeWithText(
            androidComposeTestRule.activity.getString(sf__logout)
        )
        val applicationIcon = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(sf__application_icon)
        )
        val setupMessageText = androidComposeTestRule.onNodeWithText(
            androidComposeTestRule.activity.getString(sf__screen_lock_setup_required, "App")
        )
        val setupActionButton = androidComposeTestRule.onNodeWithText(
            androidComposeTestRule.activity.getString(sf__screen_lock_setup_button)
        )

        logoutButton.assertIsDisplayed()
        applicationIcon.assertIsDisplayed()
        setupMessageText.assertIsDisplayed()
        setupActionButton.assertIsDisplayed()
    }

    @Test
    fun screenLockView_Custom_DisplaysCorrectly() {
        val setupMessage = "Custom Set Up Message"
        val setupButton = "Custom Set Up Button"

        val viewModel = ScreenLockViewModel()
        viewModel.setupMessageText.value = setupMessage
        viewModel.setupButtonLabel.value = setupButton

        androidComposeTestRule.setContent {
            TestableScreenLockView(
                viewModel = viewModel
            )
        }

        val logoutButton = androidComposeTestRule.onNodeWithText(
            androidComposeTestRule.activity.getString(sf__logout)
        )
        val applicationIcon = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(sf__application_icon)
        )
        val setupMessageText = androidComposeTestRule.onNodeWithText(
            setupMessage
        )
        val setupActionButton = androidComposeTestRule.onNodeWithText(
            setupButton
        )

        logoutButton.assertIsDisplayed()
        applicationIcon.assertIsDisplayed()
        setupMessageText.assertIsDisplayed()
        setupActionButton.assertIsDisplayed()
    }

    @Test
    fun screenLockView_TapLogoutButton_CallsLogout() {
        var logoutCalled = false
        androidComposeTestRule.setContent {
            TestableScreenLockView(
                logoutAction = { logoutCalled = true }
            )
        }

        val logoutButton = androidComposeTestRule.onNodeWithText(
            androidComposeTestRule.activity.getString(sf__logout)
        )

        logoutButton.assertIsDisplayed()
        logoutButton.performClick()
        assertTrue("Logout not called.", logoutCalled)
    }

    @Test
    fun screenLockView_TapSetupButton_CallsSetup() {
        var setupCalled = false
        val viewModel = ScreenLockViewModel()
        viewModel.setupButtonAction.value = { setupCalled = true }
        androidComposeTestRule.setContent {
            TestableScreenLockView(
                viewModel = viewModel
            )
        }

        val setupButton = androidComposeTestRule.onNodeWithText(
            androidComposeTestRule.activity.getString(sf__screen_lock_setup_button)
        )

        setupButton.assertIsDisplayed()
        setupButton.performClick()
        assertTrue("Setup not called.", setupCalled)
    }

    /**
     * A test convenience for ScreenLockView.
     * @param logoutAction The action to perform when the logout button is
     * clicked
     * @param viewModel The view model to use
     */
    @Composable
    private fun TestableScreenLockView(
        logoutAction: () -> Unit = {},
        viewModel: ScreenLockViewModel = ScreenLockViewModel(),
    ) {
        ScreenLockView(
            appName = "App",
            innerPadding = Absolute(0.dp),
            appIcon = painterResource(id = sf__salesforce_logo),
            viewModel = viewModel,
            logoutAction = logoutAction,
        )
    }
}
