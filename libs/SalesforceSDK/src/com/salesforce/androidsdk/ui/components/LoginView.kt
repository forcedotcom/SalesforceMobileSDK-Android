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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
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
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salesforce.androidsdk.R.color.sf__on_primary
import com.salesforce.androidsdk.R.color.sf__primary_color
import com.salesforce.androidsdk.R.string.sf__back_button_content_description
import com.salesforce.androidsdk.R.string.sf__clear_cookies
import com.salesforce.androidsdk.R.string.sf__launch_idp
import com.salesforce.androidsdk.R.string.sf__login_title
import com.salesforce.androidsdk.R.string.sf__more_options
import com.salesforce.androidsdk.R.string.sf__pick_server
import com.salesforce.androidsdk.R.string.sf__reload
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.ui.LoginActivity
import com.salesforce.androidsdk.ui.LoginViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun LoginView() {
    val activity: LoginActivity = LocalContext.current.getActivity() as LoginActivity
    val viewModel: LoginViewModel = viewModel(factory = SalesforceSDKManager.getInstance().loginViewModelFactory)

    val loginUrl: String = viewModel.loginUrl.observeAsState().value ?: ""
    var showMenu by remember { mutableStateOf(false) }
    val titleText = if (viewModel.isUsingFrontDoorBridge) {
        viewModel.frontdoorBridgeServer ?: ""
    } else {
        viewModel.titleText ?: viewModel.defaultTitleText
    }

    /*
     * Note: Login view contents comply with two themes: Either the dynamic
     * theme or the system theme based on their location in the layout.
     */
    // Dynamic colors derived from the web view content's background.
    val dynamicBackgroundColor = viewModel.dynamicBackgroundColor.value
    val dynamicHeaderTextColor = viewModel.dynamicHeaderTextColor.value

    // Dynamic colors which may by overridden by the view model.
    val dynamicCustomizableTopBarColor = viewModel.topBarColor ?: viewModel.dynamicBackgroundColor.value

    // System theme colors.
    val themeRippleConfiguration = RippleConfiguration(color = colorScheme.onSecondary)

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            CenterAlignedTopAppBar(
                expandedHeight = if (viewModel.showTopBar) TopAppBarDefaults.TopAppBarExpandedHeight else 0.dp,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = dynamicCustomizableTopBarColor),
                title = viewModel.titleComposable ?: {
                    Text(
                        text = titleText,
                        color = dynamicHeaderTextColor,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = @Composable {
                    IconButton(
                        onClick = { showMenu = !showMenu },
                        colors = IconButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = dynamicHeaderTextColor,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = Color.Transparent,
                        ),
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(sf__more_options))
                    }
                    CompositionLocalProvider(LocalRippleConfiguration provides themeRippleConfiguration) {
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(sf__pick_server)) },
                                onClick = {
                                    viewModel.showServerPicker.value = true
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                onClick = {
                                    viewModel.clearCookies()
                                    viewModel.reloadWebview()
                                },
                                text = { Text(stringResource(sf__clear_cookies)) },
                            )
                            DropdownMenuItem(
                                onClick = { viewModel.reloadWebview() },
                                text = { Text(stringResource(sf__reload)) },
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (viewModel.shouldShowBackButton) {
                        IconButton(
                            onClick = { activity.finish() },
                            colors = IconButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = dynamicHeaderTextColor,
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
        },
        bottomBar = {
            BottomAppBar(containerColor = dynamicBackgroundColor) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (viewModel.showBottomBarButtons.value) {
                        if (viewModel.isBiometricAuthenticationLocked.value) {
                            LoginBottomBarButton(viewModel.biometricAuthenticationButtonText.intValue) {
                                activity.onBioAuthClick()
                            }
                        } else if (viewModel.isIDPLoginFlowEnabled.value) {
                            LoginBottomBarButton(sf__launch_idp) {
                                activity.onIDPLoginClick()
                            }
                        } else if (viewModel.customBottomBarButton.value != null) {
                            viewModel.customBottomBarButton.value?.run {
                                LoginBottomBarButton(title) { onClick }
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        if (viewModel.loading.value) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(50.dp)
                        .fillMaxSize(),
                )
            }
        }

        // Load the webview as a composable
        AndroidView(
            modifier = Modifier
                .padding(innerPadding)
                .alpha(if (viewModel.loading.value) 0.0f else 100.0f),
            factory = { activity.webView },
            update = { it.loadUrl(loginUrl) },
        )

        if (viewModel.showServerPicker.value) {
            PickerBottomSheet(PickerStyle.LoginServerPicker)
        }
    }
}

/**
 * Composes a button for the bottom bar.
 * @param textStringRes The button's text string resource
 * @param onClick The button's on click behavior
 */
@Composable
@Preview
fun LoginBottomBarButton(
    textStringRes: Int = sf__login_title,
    onClick: () -> Unit = {}
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(start = 20.dp, end = 20.dp),
        shape = (RoundedCornerShape(5.dp)),
        colors = ButtonDefaults.buttonColors(
            containerColor = colorResource(id = sf__primary_color),
            contentColor = colorResource(id = sf__on_primary)
        )
    ) {
        Text(
            text = stringResource(id = textStringRes),
            fontSize = 14.sp
        )
    }
}

// Get access to host activity from within Compose.  tailrec makes this safe.
private tailrec fun Context.getActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}

