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
package com.salesforce.androidsdk.ui.components

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salesforce.androidsdk.R.string.sf__back_button_content_description
import com.salesforce.androidsdk.R.string.sf__clear_cookies
import com.salesforce.androidsdk.R.string.sf__launch_idp
import com.salesforce.androidsdk.R.string.sf__loading_indicator
import com.salesforce.androidsdk.R.string.sf__more_options
import com.salesforce.androidsdk.R.string.sf__pick_server
import com.salesforce.androidsdk.R.string.sf__reload
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.ui.LoginActivity
import com.salesforce.androidsdk.ui.LoginViewModel
import com.salesforce.androidsdk.ui.theme.SFColors
import com.salesforce.androidsdk.ui.theme.sfDarkColors
import com.salesforce.androidsdk.ui.theme.sfLightColors

internal const val PADDING_SIZE = 12
internal const val HEADER_TEXT_SIZE = 20
internal const val TEXT_SIZE = 16
internal const val CORNER_RADIUS = 12
internal const val STROKE_WIDTH = 1
internal const val BUTTON_HEIGHT = 48
internal const val LEVEL_3_ELEVATION = 6
internal const val HIDDEN_ALPHA = 0.0f
internal const val VISIBLE_ALPHA = 100.0f
internal const val LOADING_INDICATOR_SIZE = 50

@Composable
fun LoginView() {
    val activity: LoginActivity = LocalContext.current.getActivity() as LoginActivity
    val viewModel: LoginViewModel =
        viewModel(factory = SalesforceSDKManager.getInstance().loginViewModelFactory)
    val titleText = if (viewModel.isUsingFrontDoorBridge) {
        viewModel.frontdoorBridgeServer ?: ""
    } else {
        viewModel.titleText ?: viewModel.defaultTitleText
    }

    val topAppBar = viewModel.topAppBar ?: {
        DefaultTopAppBar(
            backgroundColor = viewModel.topBarColor ?: viewModel.dynamicBackgroundColor.value,
            titleText = titleText,
            titleTextColor = viewModel.titleTextColor ?: viewModel.dynamicHeaderTextColor.value,
            showServerPicker = viewModel.showServerPicker,
            clearCookies = { viewModel.clearCookies() },
            reloadWebView = { viewModel.reloadWebView() },
            shouldShowBackButton = viewModel.shouldShowBackButton,
            finish = { activity.finish() },
        )
    }

    // Possible Buttons to show in BottomAppBar
    val bioAuthButton: LoginViewModel.BottomBarButton? =
        if (viewModel.isBiometricAuthenticationLocked.value) {
            LoginViewModel.BottomBarButton(
                stringResource(viewModel.biometricAuthenticationButtonText.intValue)
            ) {
                viewModel.biometricAuthenticationButtonAction
            }
        } else null
    val idpButton =
        if (viewModel.isIDPLoginFlowEnabled.value) {
            LoginViewModel.BottomBarButton(stringResource(sf__launch_idp)) {
                activity.onIDPLoginClick()
            }
        } else null
    val customButton =
        with(viewModel.customBottomBarButton.value) {
            if (this != null) {
                LoginViewModel.BottomBarButton(title) { onClick }
            } else null
        }

    val bottomAppBar = viewModel.bottomAppBar ?: {
        DefaultBottomAppBar(
            backgroundColor = viewModel.dynamicBackgroundColor,
            button = bioAuthButton ?: idpButton ?: customButton
        )
    }

    LoginView(
        loginUrlData = viewModel.loginUrl,
        topAppBar = topAppBar,
        webView = activity.webView,
        loading = viewModel.loading.value,
        loadingIndicator = viewModel.loadingIndicator ?: { DefaultLoadingIndicator() },
        bottomAppBar = bottomAppBar,
        showServerPicker = viewModel.showServerPicker,
    )
}

@Composable
internal fun LoginView(
    loginUrlData: LiveData<String>,
    topAppBar: @Composable () -> Unit,
    webView: WebView,
    loading: Boolean,
    loadingIndicator: @Composable () -> Unit,
    bottomAppBar: @Composable () -> Unit,
    showServerPicker: MutableState<Boolean>,
) {
    val loginUrl = loginUrlData.observeAsState()

    Scaffold(
        topBar = topAppBar,
        bottomBar = bottomAppBar,
    ) { innerPadding ->
        if (loading) {
            loadingIndicator()
        }

        // Load the WebView as a composable
        AndroidView(
            modifier = Modifier
                .padding(innerPadding)
                .alpha(if (loading) HIDDEN_ALPHA else VISIBLE_ALPHA),
            factory = { webView },
            update = { it.loadUrl(loginUrl.value ?: "") },
        )

        if (showServerPicker.value) {
            PickerBottomSheet(PickerStyle.LoginServerPicker)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DefaultTopAppBar(
    backgroundColor: Color,
    titleText: String,
    titleTextColor: Color,
    showServerPicker: MutableState<Boolean>,
    clearCookies: () -> Unit,
    reloadWebView: () -> Unit,
    shouldShowBackButton: Boolean,
    finish: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor),
        title = {
            Text(
                text = titleText,
                color = titleTextColor,
                fontSize = HEADER_TEXT_SIZE.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        actions = @Composable {
            IconButton(
                onClick = { showMenu = !showMenu },
                colors = IconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = titleTextColor,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = Color.Transparent,
                ),
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(sf__more_options))
            }
            CompositionLocalProvider(
                LocalRippleConfiguration provides RippleConfiguration(color = colorScheme.onSecondary)
            ) {
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    MenuItem(stringResource(sf__pick_server)) {
                        showServerPicker.value = true
                        showMenu = false
                    }
                    MenuItem(stringResource(sf__clear_cookies)) {
                        clearCookies()
                        reloadWebView()
                    }
                    MenuItem(stringResource(sf__reload)) {
                        reloadWebView()
                    }
                }
            }
        },
        navigationIcon = {
            if (shouldShowBackButton) {
                IconButton(
                    onClick = { finish() },
                    colors = IconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = titleTextColor,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = Color.Transparent,
                    ),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(sf__back_button_content_description),
                    )
                }
            }
        }
    )
}

@Composable
internal fun DefaultLoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val description = stringResource(sf__loading_indicator)
        CircularProgressIndicator(
            modifier = Modifier
                .size(LOADING_INDICATOR_SIZE.dp)
                .fillMaxSize()
                .semantics { contentDescription = description },
        )
    }
}

@Composable
internal fun MenuItem(
    text: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                fontWeight = FontWeight.Light,
                fontSize = TEXT_SIZE.sp
            )
        },
        onClick = onClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DefaultBottomAppBar(
    backgroundColor: MutableState<Color>,
    button: LoginViewModel.BottomBarButton?,
) {
    BottomAppBar(containerColor = backgroundColor.value) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            if (button != null) {
                val buttonShape = RoundedCornerShape(CORNER_RADIUS.dp)
                CompositionLocalProvider(
                     LocalRippleConfiguration provides RippleConfiguration(color = colorScheme.onSecondary)
                ) {
                    OutlinedButton(
                        onClick = button.onClick,
                        modifier = Modifier
                            .padding(PADDING_SIZE.dp)
                            .height(BUTTON_HEIGHT.dp)
                            .fillMaxWidth()
                            .shadow(LEVEL_3_ELEVATION.dp, buttonShape),
                        shape = buttonShape,
                        contentPadding = PaddingValues(PADDING_SIZE.dp),
                        border = BorderStroke(
                            width = STROKE_WIDTH.dp,
                            color = Color(SFColors.outlineColor(LocalContext.current)),
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
                    ) {
                        Text(
                            text = button.title,
                            color = Color(SFColors.primaryColor(LocalContext.current)),
                            fontSize = TEXT_SIZE.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

// Get access to host activity from within Compose.  tail rec makes this safe.
private tailrec fun Context.getActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}

// Note: the light and dark previews should look the same.
@Preview
@Preview("Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, backgroundColor = 0xFF181818)
@Composable
private fun AppBarPreview() {
    val loginUrl = "https://login.salesforce.com"
    val backgroundColor = Color(red = 244, green = 246, blue = 249)
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        DefaultTopAppBar(
            backgroundColor = backgroundColor,
            titleText = loginUrl,
            titleTextColor = Color.Black,
            showServerPicker = remember { mutableStateOf(false) },
            clearCookies = { },
            reloadWebView = { },
            shouldShowBackButton = false,
            finish = { },
        )
    }
}

@Preview
@Composable
private fun AppBarBackButtonPreview() {
    val loginUrl = "https://login.salesforce.com"
    val backgroundColor = Color(red = 244, green = 246, blue = 249)
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        DefaultTopAppBar(
            backgroundColor = backgroundColor,
            titleText = loginUrl,
            titleTextColor = Color.Black,
            showServerPicker = remember { mutableStateOf(false) },
            clearCookies = { },
            reloadWebView = { },
            shouldShowBackButton = true,
            finish = { },
        )
    }
}

@Preview
@Composable
private fun AppBarDarkPreview() {
    val loginUrl = "https://login.salesforce.com"
    val backgroundColor = Color(0xFF181818)
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        DefaultTopAppBar(
            backgroundColor = backgroundColor,
            titleText = loginUrl,
            titleTextColor = Color.White,
            showServerPicker = remember { mutableStateOf(false) },
            clearCookies = { },
            reloadWebView = { },
            shouldShowBackButton = true,
            finish = { },
        )
    }
}

@Preview
@Composable
private fun BlueAppBarPreview() {
    val loginUrl = "https://login.salesforce.com"
    val backgroundColor = sfLightColors().primary
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        DefaultTopAppBar(
            backgroundColor = backgroundColor,
            titleText = loginUrl,
            titleTextColor = Color.White,
            showServerPicker = remember { mutableStateOf(false) },
            clearCookies = { },
            reloadWebView = { },
            shouldShowBackButton = true,
            finish = { },
        )
    }
}

@Preview
@Composable
private fun CustomTextAppBarPreview() {
    val backgroundColor = sfLightColors().primary
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        DefaultTopAppBar(
            backgroundColor = backgroundColor,
            titleText = "Log In",
            titleTextColor = Color.White,
            showServerPicker = remember { mutableStateOf(false) },
            clearCookies = { },
            reloadWebView = { },
            shouldShowBackButton = false,
            finish = { },
        )
    }
}

@Preview
@Composable
private fun LongCustomTextAppBarPreview() {
    val backgroundColor = Color.White
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        DefaultTopAppBar(
            backgroundColor = backgroundColor,
            titleText = "https://mobilesdk.my.salesforce.com",
            titleTextColor = Color.Black,
            showServerPicker = remember { mutableStateOf(false) },
            clearCookies = { },
            reloadWebView = { },
            shouldShowBackButton = true,
            finish = { },
        )
    }
}

@Preview("Light Mode", showBackground = true, widthDp = 100, heightDp = 100)
@Preview(
    "Dark Mode",
    showBackground = true,
    widthDp = 100,
    heightDp = 100,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    backgroundColor = 0xFF181818
)
@Composable
private fun LoadingIndicatorPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        DefaultLoadingIndicator()
    }
}

// Note: the light and dark previews should look the same.
@Preview
@Preview("Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, backgroundColor = 0xFF181818)
@Composable
private fun BottomBarPreview() {
    val backgroundColor = Color.White
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        DefaultBottomAppBar(
            backgroundColor = remember { mutableStateOf(backgroundColor) },
            button = LoginViewModel.BottomBarButton(
                title = "Login Button Preview",
                onClick = { }
            )
        )
    }
}

@Preview
@Composable
private fun BottomBarRedPreview() {
    val backgroundColor = Color(0xFF900603)
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        DefaultBottomAppBar(
            backgroundColor = remember { mutableStateOf(backgroundColor) },
            button = LoginViewModel.BottomBarButton(
                title = "Login Button Preview",
                onClick = { }
            )
        )
    }
}