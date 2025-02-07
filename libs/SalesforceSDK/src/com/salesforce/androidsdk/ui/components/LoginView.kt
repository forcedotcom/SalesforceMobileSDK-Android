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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.salesforce.androidsdk.R.color.sf__primary_color
import com.salesforce.androidsdk.R.color.sf__secondary_color
import com.salesforce.androidsdk.R.string.sf__launch_idp
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.ui.LoginActivity
import com.salesforce.androidsdk.ui.LoginViewModel

@Preview
@Composable
fun LoginView() {
    val viewModel: LoginViewModel = viewModel(factory = SalesforceSDKManager.getInstance().loginViewModelFactory)
    val loginUrl: String = viewModel.loginUrl.observeAsState().value ?: ""
    var showMenu by remember { mutableStateOf(false) }
    val topBarColor: Color = viewModel.topBarColor ?: viewModel.dynamicBackgroundColor.value
    val activity: LoginActivity = LocalContext.current.getActivity() as LoginActivity
    val titleText = if (viewModel.isUsingFrontDoorBridge) {
        viewModel.frontdoorBridgeServer ?: ""
    } else {
        viewModel.titleText ?: viewModel.defaultTitleText
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            CenterAlignedTopAppBar(
                expandedHeight = if (viewModel.showTopBar) TopAppBarDefaults.TopAppBarExpandedHeight else 0.dp,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarColor),
                title = viewModel.titleComposable ?: {
                    Text(
                        text = titleText,
                        color = viewModel.dynamicHeaderTextColor.value,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = @Composable {
                    IconButton(
                        onClick = { showMenu = !showMenu },
                        colors = IconButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = viewModel.dynamicHeaderTextColor.value,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = Color.Transparent,
                        ),
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = Color.White,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Change Server", color = Color.Gray) },
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
                            text = { Text("Clear Cookies", color = Color.Gray) },
                        )
                        DropdownMenuItem(
                            onClick = { viewModel.reloadWebview() },
                            text = { Text("Reload", color = Color.Gray) },
                        )
                    }
                },
                navigationIcon = {
                    if (viewModel.shouldShowBackButton) {
                        IconButton(
                            onClick = { activity.finish() },
                            colors = IconButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color.Black,  // TODO: fix color
                                disabledContainerColor = Color.Transparent,
                                disabledContentColor = Color.Transparent,
                            ),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(containerColor = viewModel.dynamicBackgroundColor.value) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    // TODO: Restore Biometric Authentication button here.
                    if (viewModel.isIDPLoginFlowEnabled.value) {
                        Button(
                            onClick = { activity.onIDPLoginClick() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .padding(start = 20.dp, end = 20.dp),
                            shape = (RoundedCornerShape(5.dp)),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorResource(id = sf__primary_color),
                                contentColor = colorResource(id = sf__secondary_color)
                            )
                        ) {
                            Text(
                                text = stringResource(id = sf__launch_idp),
                                fontSize = 14.sp
                            )
                        }
                    }
                    viewModel.additionalBottomBarButtons.value.forEach { button ->
                        Button(onClick = {
                            button.onClick()
                        }) {
                            Text(stringResource(button.title))
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
                    color = Color.Black,
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

// Get access to host activity from within Compose.  tailrec makes this safe.
private tailrec fun Context.getActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}

