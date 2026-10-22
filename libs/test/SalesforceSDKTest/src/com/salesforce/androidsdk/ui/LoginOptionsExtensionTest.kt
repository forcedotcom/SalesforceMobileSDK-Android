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

import android.app.Activity
import android.Manifest
import android.os.Build
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.salesforce.androidsdk.R
import com.salesforce.androidsdk.app.SalesforceSDKManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests the [LoginOptionsExtension] hook of [LoginOptionsActivity]. */
@RunWith(AndroidJUnit4::class)
class LoginOptionsExtensionTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    // TODO: Remove if when min SDK version is 33
    @get:Rule
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    private class TestExtension : LoginOptionsExtension {
        var browserOptionsVisible by mutableStateOf(true)
        val dismissed = mutableListOf<Activity>()

        @Composable
        override fun TopContent() {
            Text(TOP_CONTENT_TEXT)
        }

        override val showsBrowserOptions: Boolean
            get() = browserOptionsVisible

        override fun onLoginOptionsDismissed(activity: Activity) {
            dismissed.add(activity)
        }
    }

    private val sdkManager get() = SalesforceSDKManager.getInstance()

    @Before
    fun setup() {
        sdkManager.loginOptionsExtension = null
        sdkManager.loginDevMenuReload = false
    }

    @After
    fun teardown() {
        sdkManager.loginOptionsExtension = null
        sdkManager.loginDevMenuReload = false
    }

    private fun webServerToggle() = composeTestRule.onNodeWithContentDescription(
        InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.sf__login_options_webserver_toggle_content_description),
    )

    @Test
    fun test_givenNoExtension_whenLoginOptionsShown_thenDefaultsAreUnchanged() {
        assertNull(sdkManager.loginOptionsExtension)

        ActivityScenario.launch(LoginOptionsActivity::class.java).use {
            webServerToggle().assertIsDisplayed()
        }
    }

    @Test
    fun test_givenExtension_whenLoginOptionsShown_thenTopContentAndBrowserOptionsAreShown() {
        sdkManager.loginOptionsExtension = TestExtension()

        ActivityScenario.launch(LoginOptionsActivity::class.java).use {
            composeTestRule.onNodeWithText(TOP_CONTENT_TEXT).assertIsDisplayed()
            webServerToggle().assertIsDisplayed()
        }
    }

    @Test
    fun test_givenExtensionHidesBrowserOptions_whenToggled_thenBrowserOptionsAreRemovedAndRestored() {
        val extension = TestExtension()
        sdkManager.loginOptionsExtension = extension

        ActivityScenario.launch(LoginOptionsActivity::class.java).use {
            extension.browserOptionsVisible = false
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(TOP_CONTENT_TEXT).assertIsDisplayed()
            webServerToggle().assertDoesNotExist()

            extension.browserOptionsVisible = true
            composeTestRule.waitForIdle()
            webServerToggle().assertIsDisplayed()
        }
    }

    @Test
    fun test_givenExtension_whenLoginOptionsFinishes_thenDismissalIsReported() {
        val extension = TestExtension()
        sdkManager.loginOptionsExtension = extension

        ActivityScenario.launch(LoginOptionsActivity::class.java).use { scenario ->
            scenario.onActivity { it.finish() }
            assertEquals(1, extension.dismissed.size)
            assertEquals(LoginOptionsActivity::class.java, extension.dismissed.single()::class.java)
        }
    }

    private companion object {
        const val TOP_CONTENT_TEXT = "Extension top content"
    }
}
