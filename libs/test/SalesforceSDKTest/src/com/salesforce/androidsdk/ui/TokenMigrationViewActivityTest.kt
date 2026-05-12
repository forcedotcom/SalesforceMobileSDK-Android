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
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color.Companion.Red
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.rule.GrantPermissionRule
import com.salesforce.androidsdk.R
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.ui.components.TokenMigrationView
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TokenMigrationViewActivityTest {

    @get:Rule
    val androidComposeTestRule = createAndroidComposeRule<ComponentActivity>()

    // TODO: Remove if when min SDK version is 33
    @get:Rule
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    private lateinit var savedFactory: ViewModelProvider.Factory
    private lateinit var mockViewModel: LoginViewModel

    @Before
    fun setUp() {
        savedFactory = SalesforceSDKManager.getInstance().loginViewModelFactory

        mockViewModel = mockk<LoginViewModel>(relaxed = true) {
            every { loading } returns mutableStateOf(false)
            every { loadingIndicator } returns null
            every { dynamicBackgroundColor } returns mutableStateOf(Transparent)
            every { dynamicBackgroundTheme } returns mutableStateOf(SalesforceSDKManager.Theme.LIGHT)
        }

        SalesforceSDKManager.getInstance().loginViewModelFactory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T = mockViewModel as T
            }
    }

    @After
    fun tearDown() {
        SalesforceSDKManager.getInstance().loginViewModelFactory = savedFactory
        unmockkAll()
    }

    @Test
    fun tokenMigrationView_NotLoading_HidesLoadingIndicator() {
        every { mockViewModel.loading } returns mutableStateOf(false)

        androidComposeTestRule.setContent {
            val context = LocalContext.current
            TokenMigrationView(webViewFactory = { WebView(context) })
        }

        val loadingIndicator = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__loading_indicator)
        )

        loadingIndicator.assertIsNotDisplayed()
    }

    @Test
    fun tokenMigrationView_Loading_ShowsLoadingIndicator() {
        every { mockViewModel.loading } returns mutableStateOf(true)

        androidComposeTestRule.setContent {
            val context = LocalContext.current
            TokenMigrationView(webViewFactory = { WebView(context) })
        }

        val loadingIndicator = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__loading_indicator)
        )

        loadingIndicator.assertIsDisplayed()
    }

    @Test
    fun tokenMigrationView_DefaultBackgroundColor_IsTransparent() {
        val backgroundColor = mutableStateOf(Transparent)
        every { mockViewModel.dynamicBackgroundColor } returns backgroundColor

        androidComposeTestRule.setContent {
            val context = LocalContext.current
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    background = backgroundColor.value
                )
            ) {
                TokenMigrationView(webViewFactory = { WebView(context) })
            }
        }

        assertEquals(
            "Default background color should be Transparent",
            Transparent,
            backgroundColor.value,
        )
    }

    @Test
    fun tokenMigrationView_BackgroundColorUpdates_ReflectsNewColor() {
        val backgroundColor = mutableStateOf(Transparent)
        every { mockViewModel.dynamicBackgroundColor } returns backgroundColor

        androidComposeTestRule.setContent {
            val context = LocalContext.current
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    background = backgroundColor.value
                )
            ) {
                TokenMigrationView(webViewFactory = { WebView(context) })
            }
        }

        androidComposeTestRule.runOnIdle {
            backgroundColor.value = White
        }

        androidComposeTestRule.runOnIdle {
            assertEquals(
                "Background color should update to White",
                White,
                backgroundColor.value,
            )
        }
    }

    @Test
    fun tokenMigrationView_BackgroundColorUpdatesToRed_ReflectsNewColor() {
        val backgroundColor = mutableStateOf(Transparent)
        every { mockViewModel.dynamicBackgroundColor } returns backgroundColor

        androidComposeTestRule.setContent {
            val context = LocalContext.current
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    background = backgroundColor.value
                )
            ) {
                TokenMigrationView(webViewFactory = { WebView(context) })
            }
        }

        androidComposeTestRule.runOnIdle {
            backgroundColor.value = Red
        }

        androidComposeTestRule.runOnIdle {
            assertEquals(
                "Background color should update to Red",
                Red,
                backgroundColor.value,
            )
        }
    }
}
