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
import android.os.Build
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.RichTooltipColors
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salesforce.androidsdk.R.string.sf__back_button_content_description
import com.salesforce.androidsdk.R.string.sf__clear_cache
import com.salesforce.androidsdk.R.string.sf__clear_cookies
import com.salesforce.androidsdk.R.string.sf__dev_support_title_menu_item
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
import com.salesforce.androidsdk.util.test.ExcludeFromJacocoGeneratedReport

internal const val PADDING_SIZE = 12
internal const val HEADER_TEXT_SIZE = 20
internal const val TEXT_SIZE = 16
internal const val CORNER_RADIUS = 12
internal const val STROKE_WIDTH = 1
internal const val BUTTON_HEIGHT = 48
internal const val LEVEL_3_ELEVATION = 6
internal const val LOADING_ALPHA = 0.2f
internal const val VISIBLE_ALPHA = 1f
internal const val LOADING_INDICATOR_SIZE = 50
internal const val SLOW_ANIMATION_MS = 500

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
    val showDevSupport = with(SalesforceSDKManager.getInstance()) {
        return@with if (isDebugBuild && isDevSupportEnabled()) {
            { showDevSupportDialog(activity) }
        } else {
            null
        }
    }

    val topAppBar = viewModel.topAppBar ?: {
        DefaultTopAppBar(
            backgroundColor = viewModel.topBarColor ?: viewModel.dynamicBackgroundColor.value,
            titleText = titleText,
            titleTextColor = viewModel.titleTextColor ?: viewModel.dynamicHeaderTextColor.value,
            showServerPicker = viewModel.showServerPicker,
            clearCookies = { viewModel.clearCookies() },
            clearWebViewCache = { viewModel.clearWebViewCache(activity.webView) },
            reloadWebView = { viewModel.reloadWebView() },
            shouldShowBackButton = viewModel.shouldShowBackButton,
            showDevSupport = showDevSupport,
            finish = { activity.handleBackBehavior() },
        )
    }

    // Possible Buttons to show in BottomAppBar
    val bioAuthButton: LoginViewModel.BottomBarButton? =
        if (viewModel.isBiometricAuthenticationLocked.value) {
            LoginViewModel.BottomBarButton(
                stringResource(viewModel.biometricAuthenticationButtonText.intValue)
            ) {
                viewModel.biometricAuthenticationButtonAction.value?.invoke() ?: activity.onBioAuthClick()
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
                LoginViewModel.BottomBarButton(title) { onClick() }
            } else null
        }

    val bottomAppBarButton = bioAuthButton ?: idpButton ?: customButton
    val bottomAppBar = viewModel.bottomAppBar ?: {
        DefaultBottomAppBar(
            backgroundColor = viewModel.dynamicBackgroundColor,
            button = bottomAppBarButton,
            loading = viewModel.loading.value,
            showButton = !viewModel.authFinished.value
        )
    }

    LoginView(
        dynamicBackgroundColor = viewModel.dynamicBackgroundColor,
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
    dynamicBackgroundColor: MutableState<Color>,
    loginUrlData: LiveData<String>,
    topAppBar: @Composable () -> Unit,
    webView: WebView,
    loading: Boolean,
    loadingIndicator: @Composable () -> Unit,
    bottomAppBar: @Composable () -> Unit,
    showServerPicker: MutableState<Boolean>,
) {
    val loginUrl = loginUrlData.observeAsState()
    val alpha: Float by animateFloatAsState(
        targetValue = if (loading) LOADING_ALPHA else VISIBLE_ALPHA,
        animationSpec = tween(durationMillis = SLOW_ANIMATION_MS),
    )

    Scaffold(
        bottomBar = bottomAppBar,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = topAppBar,
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Load the WebView as a composable
            AndroidView(
                modifier = Modifier
                    .background(dynamicBackgroundColor.value)
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .applyImePaddingConditionally()
                    .graphicsLayer(alpha = alpha),
                factory = { webView },
                update = { it.loadUrl(loginUrl.value ?: "") },
            )

            if (loading) {
                loadingIndicator()
            }
        }

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
    clearWebViewCache: () -> Unit,
    reloadWebView: () -> Unit,
    shouldShowBackButton: Boolean,
    showDevSupport: (() -> Unit)?,
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
            ToolTipWrapper(sf__more_options) { moreOptionsDescription ->
                IconButton(
                    onClick = { showMenu = !showMenu },
                    colors = IconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = titleTextColor,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = Color.Transparent,
                    ),
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = moreOptionsDescription)
                }
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
                        showMenu = false
                    }
                    MenuItem(stringResource(sf__clear_cache)) {
                        clearWebViewCache()
                        reloadWebView()
                        showMenu = false
                    }
                    MenuItem(stringResource(sf__reload)) {
                        reloadWebView()
                        showMenu = false
                    }
                    showDevSupport?.let {
                        MenuItem(stringResource(sf__dev_support_title_menu_item)) {
                            it.invoke()
                            showMenu = false
                        }
                    }
                }
            }
        },
        navigationIcon = {
            if (shouldShowBackButton) {
                ToolTipWrapper(sf__back_button_content_description) { backButtonDescription ->
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
                            contentDescription = backButtonDescription,
                        )
                    }
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
    loading: Boolean,
    showButton: Boolean,
) {
    val alpha: Float by animateFloatAsState(
        targetValue = if (loading) LOADING_ALPHA else VISIBLE_ALPHA,
        animationSpec = tween(durationMillis = SLOW_ANIMATION_MS),
    )
    val heightModifier = if (button == null || !showButton) {
        Modifier.height(WindowInsets.navigationBars.getBottom(LocalDensity.current).pxToDp())
    } else {
        Modifier.defaultMinSize()
    }

    BottomAppBar(
        containerColor = backgroundColor.value,
        contentPadding = PaddingValues(0.dp),
        modifier = heightModifier.graphicsLayer(alpha = alpha),
    ) {
        AnimatedVisibility(
            visible = showButton,
            enter = fadeIn(animationSpec = tween(durationMillis = SLOW_ANIMATION_MS)),
            exit = fadeOut(animationSpec = tween(durationMillis = SLOW_ANIMATION_MS)),
        ) {
            if (button != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ToolTipWrapper(contentDescription: Int, content: @Composable (description: String) -> Unit) {
    val description = stringResource(contentDescription)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
        tooltip = {
            RichTooltip(
                caretSize = DpSize(PADDING_SIZE.dp, PADDING_SIZE.dp),
                colors = RichTooltipColors(
                    containerColor = colorScheme.outline,
                    contentColor = colorScheme.onSecondary,
                    titleContentColor = Color.Transparent, // Unused
                    actionContentColor = Color.Transparent, // Unused
                )
            ) { Text(description) }
        },
        state = rememberTooltipState(
            isPersistent = true,
            initialIsVisible = LocalInspectionMode.current
        )
    ) {
        content(description)
    }
}

@Composable
internal fun Int.pxToDp() = with(LocalDensity.current) { this@pxToDp.toDp() }

// Get access to host activity from within Compose.  tail rec makes this safe.
private tailrec fun Context.getActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}

@Composable
private fun Modifier.applyImePaddingConditionally() : Modifier =
    // TODO:  Remove when min API is > 29
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        windowInsetsPadding(WindowInsets.ime)
    } else {
        this
    }

@ExcludeFromJacocoGeneratedReport
@Preview // Note: the light and dark previews should look the same.
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
            clearWebViewCache = { },
            reloadWebView = { },
            shouldShowBackButton = false,
            showDevSupport = null,
            finish = { },
        )
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview
@Composable
private fun AppBarLoadingPreview() {
    val loginUrl = "https://login.salesforce.com"
    val backgroundColor = Color(red = 244, green = 246, blue = 249)
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        DefaultTopAppBar(
            backgroundColor = backgroundColor,
            titleText = loginUrl,
            titleTextColor = Color.Black,
            showServerPicker = remember { mutableStateOf(false) },
            clearCookies = { },
            clearWebViewCache = { },
            reloadWebView = { },
            shouldShowBackButton = false,
            showDevSupport = null,
            finish = { },
        )
    }
}

@ExcludeFromJacocoGeneratedReport
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
            clearWebViewCache = { },
            reloadWebView = { },
            shouldShowBackButton = true,
            showDevSupport = null,
            finish = { },
        )
    }
}

@ExcludeFromJacocoGeneratedReport
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
            clearWebViewCache = { },
            reloadWebView = { },
            shouldShowBackButton = true,
            showDevSupport = null,
            finish = { },
        )
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview
@Composable
private fun BlueAppBarPreview() {
    val loginUrl = "https://login.salesforce.com"
    val topBarColor = sfLightColors().primary
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        DefaultTopAppBar(
            backgroundColor = topBarColor,
            titleText = loginUrl,
            titleTextColor = Color.White,
            showServerPicker = remember { mutableStateOf(false) },
            clearCookies = { },
            clearWebViewCache = { },
            reloadWebView = { },
            shouldShowBackButton = true,
            showDevSupport = null,
            finish = { },
        )
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview
@Composable
private fun BlueAppBarLoadingPreview() {
    val loginUrl = "https://login.salesforce.com"
    val topBarColor = sfLightColors().primary
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        DefaultTopAppBar(
            backgroundColor = topBarColor,
            titleText = loginUrl,
            titleTextColor = Color.White,
            showServerPicker = remember { mutableStateOf(false) },
            clearCookies = { },
            clearWebViewCache = { },
            reloadWebView = { },
            shouldShowBackButton = true,
            showDevSupport = null,
            finish = { },
        )
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview
@Composable
private fun CustomTextAppBarPreview() {
    val topBarColor = sfLightColors().primary
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        DefaultTopAppBar(
            backgroundColor = topBarColor,
            titleText = "Log In",
            titleTextColor = Color.White,
            showServerPicker = remember { mutableStateOf(false) },
            clearCookies = { },
            clearWebViewCache = { },
            reloadWebView = { },
            shouldShowBackButton = false,
            showDevSupport = null,
            finish = { },
        )
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview
@Composable
private fun CustomTextAppBarLoadingPreview() {
    val topBarColor = sfLightColors().primary
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        DefaultTopAppBar(
            backgroundColor = topBarColor,
            titleText = "Log In",
            titleTextColor = Color.White,
            showServerPicker = remember { mutableStateOf(false) },
            clearCookies = { },
            clearWebViewCache = { },
            reloadWebView = { },
            shouldShowBackButton = false,
            showDevSupport = null,
            finish = { },
        )
    }
}

@ExcludeFromJacocoGeneratedReport
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
            clearWebViewCache = { },
            reloadWebView = { },
            shouldShowBackButton = true,
            showDevSupport = null,
            finish = { },
        )
    }
}

@ExcludeFromJacocoGeneratedReport
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

@ExcludeFromJacocoGeneratedReport
@Preview // Note: the light and dark previews should look the same.
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
            ),
            loading = false,
            showButton = true,
        )
    }
}

@ExcludeFromJacocoGeneratedReport
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
            ),
            loading = false,
            showButton = true,
        )
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview("Light", showBackground = true, heightDp = 100, widthDp = 100)
@Preview(
    "Dark", showBackground = true, heightDp = 100, widthDp = 100,
    uiMode = Configuration.UI_MODE_NIGHT_YES, backgroundColor = 0xFF181818
)
@Composable
private fun TooltipPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            ToolTipWrapper(sf__loading_indicator) { cd ->
                IconButton(
                    onClick = { },
                    colors = IconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = colorScheme.secondary,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = Color.Transparent,
                    ),
                    modifier = Modifier.size(ICON_SIZE.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = cd,
                    )
                }
            }
        }
    }
}
