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

import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.salesforce.androidsdk.R
import com.salesforce.androidsdk.ui.components.DefaultBottomAppBar
import com.salesforce.androidsdk.ui.components.DefaultLoadingIndicator
import com.salesforce.androidsdk.ui.components.DefaultTopAppBar
import com.salesforce.androidsdk.ui.components.LoginView
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

private const val DEFAULT_URL = "https://login.salesforce.com"
private const val BUTTON_TITLE = "Test Button"

class LoginViewActivityTest {

    @get:Rule
    val androidComposeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun topAppBar_Default_DisplaysCorrectly() {
        androidComposeTestRule.setContent {
            DefaultTopAppBarTestWrapper()
        }

        val backButton = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__back_button_content_description)
        )
        val titleText = androidComposeTestRule.onNodeWithText(DEFAULT_URL)
        val menu = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__more_options)
        )

        backButton.assertDoesNotExist()
        titleText.assertIsDisplayed()
        menu.assertIsDisplayed()
    }

    @Test
    fun topAppBar_TapBackButton_CallsFinish() {
        var finishCalled = false
        androidComposeTestRule.setContent {
            DefaultTopAppBarTestWrapper(
                shouldShowBackButton = true,
                finish = { finishCalled = true }
            )
        }

        val backButton = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__back_button_content_description)
        )
        val titleText = androidComposeTestRule.onNodeWithText(DEFAULT_URL)
        val menu = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__more_options)
        )

        backButton.assertExists()
        backButton.assertIsDisplayed()
        titleText.assertIsDisplayed()
        menu.assertIsDisplayed()
        Assert.assertFalse("Finish should not be called yet", finishCalled)

        // Tap Back Button
        backButton.performClick()
        Assert.assertTrue("Finish not called.", finishCalled)
    }

    @Test
    fun topAppBar_ChangeServerButton_OpensServerPicker() {
        var showPicker: MutableState<Boolean>? = null
        androidComposeTestRule.setContent {
            showPicker = remember { mutableStateOf(false) }
            DefaultTopAppBarTestWrapper(
                showServerPicker = showPicker!!
            )
        }

        val backButton = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__back_button_content_description)
        )
        val titleText = androidComposeTestRule.onNodeWithText(DEFAULT_URL)
        val menu = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__more_options)
        )
        val changeServerButton = androidComposeTestRule.onNodeWithText(
            androidComposeTestRule.activity.getString(R.string.sf__pick_server)
        )

        backButton.assertDoesNotExist()
        titleText.assertIsDisplayed()
        menu.assertIsDisplayed()

        menu.performClick()
        changeServerButton.assertIsDisplayed()
        Assert.assertFalse("Picker should not be shown yet.", showPicker!!.value)

        changeServerButton.performClick()
        Assert.assertTrue("Picker should be shown.", showPicker!!.value)
    }

    @Test
    fun topAppBar_ClearCookiesButton_ClearsCookiesAndReloads() {
        var clearCookiesCalled = false
        var reloadCalled = false
        androidComposeTestRule.setContent {
            DefaultTopAppBarTestWrapper(
                clearCookies = { clearCookiesCalled = true },
                reloadWebView = { reloadCalled = true },
            )
        }

        val backButton = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__back_button_content_description)
        )
        val titleText = androidComposeTestRule.onNodeWithText(DEFAULT_URL)
        val menu = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__more_options)
        )
        val clearCookiesButton = androidComposeTestRule.onNodeWithText(
            androidComposeTestRule.activity.getString(R.string.sf__clear_cookies)
        )

        backButton.assertDoesNotExist()
        titleText.assertIsDisplayed()
        menu.assertIsDisplayed()

        menu.performClick()
        clearCookiesButton.assertIsDisplayed()
        Assert.assertFalse("Clear cookies should not be called yet.", clearCookiesCalled)
        Assert.assertFalse("Reload should not be called yet.", reloadCalled)

        clearCookiesButton.performClick()
        Assert.assertTrue("Clear cookies should be called.", clearCookiesCalled)
        Assert.assertTrue("Reload should be called.", reloadCalled)
    }

    @Test
    fun topAppBar_ClearCacheButton_ClearsCacheAndReloads() {
        var clearCacheCalled = false
        var reloadCalled = false
        androidComposeTestRule.setContent {
            DefaultTopAppBarTestWrapper(
                clearWebViewCache = { clearCacheCalled = true },
                reloadWebView = { reloadCalled = true },
            )
        }

        val backButton = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__back_button_content_description)
        )
        val titleText = androidComposeTestRule.onNodeWithText(DEFAULT_URL)
        val menu = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__more_options)
        )
        val clearCacheButton = androidComposeTestRule.onNodeWithText(
            androidComposeTestRule.activity.getString(R.string.sf__clear_cache)
        )

        backButton.assertDoesNotExist()
        titleText.assertIsDisplayed()
        menu.assertIsDisplayed()

        menu.performClick()
        clearCacheButton.assertIsDisplayed()
        Assert.assertFalse("Clear cache should not be called yet.", clearCacheCalled)
        Assert.assertFalse("Reload should not be called yet.", reloadCalled)

        clearCacheButton.performClick()
        Assert.assertTrue("Clear cache should be called.", clearCacheCalled)
        Assert.assertTrue("Reload should be called.", reloadCalled)
    }

    @Test
    fun topAppBar_ReloadButton_CallsReload() {
        var reloadCalled = false
        androidComposeTestRule.setContent {
            DefaultTopAppBarTestWrapper(
                reloadWebView = { reloadCalled = true },
            )
        }

        val backButton = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__back_button_content_description)
        )
        val titleText = androidComposeTestRule.onNodeWithText(DEFAULT_URL)
        val menu = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__more_options)
        )
        val reloadButton = androidComposeTestRule.onNodeWithText(
            androidComposeTestRule.activity.getString(R.string.sf__reload)
        )

        backButton.assertDoesNotExist()
        titleText.assertIsDisplayed()
        menu.assertIsDisplayed()

        menu.performClick()
        reloadButton.assertIsDisplayed()
        Assert.assertFalse("Reload should not be called yet.", reloadCalled)

        reloadButton.performClick()
        Assert.assertTrue("Reload should be called.", reloadCalled)
    }

    @Test
    fun bottomAppBar_WithNoButton_DisplaysCorrectly() {
        androidComposeTestRule.setContent {
            DefaultBottomAppBarTestWrapper()
        }

        val button = androidComposeTestRule.onNodeWithText(BUTTON_TITLE)
        button.assertDoesNotExist()
    }

    @Test
    fun bottomAppBar_WithButton_DisplaysCorrectly() {
        var buttonClicked = false
        androidComposeTestRule.setContent {
            DefaultBottomAppBarTestWrapper(button = LoginViewModel.BottomBarButton(BUTTON_TITLE) {
                buttonClicked = true
            })
        }

        val button = androidComposeTestRule.onNodeWithText(BUTTON_TITLE)
        button.assertExists()
        button.assertIsDisplayed()
        button.assertIsEnabled()
        Assert.assertFalse("Button should not be clicked yet.", buttonClicked)

        button.performClick()
        Assert.assertTrue("Button should have been clicked.", buttonClicked)
    }

    @Test
    fun loginView_DefaultComponents_DisplayCorrectly() {
        androidComposeTestRule.setContent {
            LoginViewTestWrapper(
                topAppBar = {
                    DefaultTopAppBarTestWrapper(shouldShowBackButton = true)
                },
                bottomAppBar = {
                    DefaultBottomAppBarTestWrapper(button = LoginViewModel.BottomBarButton(BUTTON_TITLE) {})
                },
                loading = false,
            )
        }

        val backButton = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__back_button_content_description)
        )
        val titleText = androidComposeTestRule.onNodeWithText(DEFAULT_URL)
        val menu = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__more_options)
        )
        val loadingIndicator = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__loading_indicator)
        )
        val button = androidComposeTestRule.onNodeWithText(BUTTON_TITLE)

        backButton.assertIsDisplayed()
        titleText.assertIsDisplayed()
        menu.assertIsDisplayed()
        loadingIndicator.assertIsNotDisplayed()
        button.assertIsDisplayed()
    }

    @Test
    fun loginView_Loading_DisplayCorrectly() {
        androidComposeTestRule.setContent {
            LoginViewTestWrapper(
                topAppBar = {
                    DefaultTopAppBarTestWrapper(shouldShowBackButton = true)
                },
                bottomAppBar = {
                    DefaultBottomAppBarTestWrapper(button = LoginViewModel.BottomBarButton(BUTTON_TITLE) {})
                },
                loading = true,
            )
        }

        val backButton = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__back_button_content_description)
        )
        val titleText = androidComposeTestRule.onNodeWithText(DEFAULT_URL)
        val menu = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__more_options)
        )
        val loadingIndicator = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__loading_indicator)
        )
        val button = androidComposeTestRule.onNodeWithText(BUTTON_TITLE)

        backButton.assertIsDisplayed()
        titleText.assertIsDisplayed()
        menu.assertIsDisplayed()
        loadingIndicator.assertIsDisplayed()
        button.assertIsDisplayed()
    }

    // test (not) loading

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun loginView_CustomComponents_DisplayCorrectly() {
        val customTopAppBar = @Composable {
            LargeTopAppBar(
                title = { Text("Test") },
                modifier = Modifier.semantics { contentDescription = "CustomTopAppBar" }
            )
        }
        val customLoadingIndicator = @Composable {
            LinearProgressIndicator(
                modifier = Modifier.semantics { contentDescription = "CustomLoadingIndicator" }
            )
        }
        val customBottomAppBar = @Composable {
            BottomAppBar(
                content = { },
                modifier = Modifier.semantics { contentDescription = "CustomBottomAppBar" },
            )
        }

        androidComposeTestRule.setContent {
            LoginViewTestWrapper(
                topAppBar = customTopAppBar,
                loading = true,
                loadingIndicator = customLoadingIndicator,
                bottomAppBar = customBottomAppBar,
            )
        }

        val topAppBar = androidComposeTestRule.onNodeWithContentDescription("CustomTopAppBar")
        val loadingIndicator = androidComposeTestRule.onNodeWithContentDescription("CustomLoadingIndicator")
        val bottomAppBar = androidComposeTestRule.onNodeWithContentDescription("CustomBottomAppBar")

        topAppBar.assertIsDisplayed()
        loadingIndicator.assertIsDisplayed()
        bottomAppBar.assertIsDisplayed()
    }

    /**
     * This wrapper makes most component input optional so it is more clear what the test is actually doing.
     */
    @Composable
    private fun DefaultTopAppBarTestWrapper(
        backgroundColor: Color = Color.White,
        titleText: String = DEFAULT_URL,
        titleTextColor: Color = Color.Black,
        showServerPicker: MutableState<Boolean> = remember { mutableStateOf(false) },
        clearCookies: () -> Unit = { },
        clearWebViewCache: () -> Unit = { },
        reloadWebView: () -> Unit = { },
        shouldShowBackButton: Boolean = false,
        finish: () -> Unit = { },
    ) {
        DefaultTopAppBar(
            backgroundColor, titleText, titleTextColor, showServerPicker, clearCookies,
            clearWebViewCache, reloadWebView, shouldShowBackButton, finish
        )
    }

    /**
     * This wrapper makes most component input optional so it is more clear what the test is actually doing.
     */
    @Composable
    private fun DefaultBottomAppBarTestWrapper(
        backgroundColor: MutableState<Color> = mutableStateOf(Color.White),
        button: LoginViewModel.BottomBarButton? = null,
        loading: Boolean = false,
        showButton: Boolean = true,
    ) {
        DefaultBottomAppBar(backgroundColor, button, loading, showButton)
    }

    /**
     * This wrapper makes most component input optional so it is more clear what the test is actually doing.
     */
    @Composable
    private fun LoginViewTestWrapper(
        loginUrlData: LiveData<String> = liveData { DEFAULT_URL },
        topAppBar: @Composable () -> Unit = { DefaultTopAppBarTestWrapper() },
        webView: WebView = WebView(LocalContext.current),
        loading: Boolean = false,
        loadingIndicator: @Composable () -> Unit = { DefaultLoadingIndicator() },
        bottomAppBar: @Composable () -> Unit = { DefaultBottomAppBarTestWrapper() },
        showServerPicker: MutableState<Boolean> = mutableStateOf(false),
    ) {
        LoginView(loginUrlData, topAppBar, webView, loading, loadingIndicator, bottomAppBar, showServerPicker)
    }
}