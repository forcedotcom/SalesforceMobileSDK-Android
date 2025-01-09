package com.salesforce.androidsdk.ui.components

import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.LoginViewModel

@Composable
fun LoginView(
    webviewClient: WebViewClient,
    viewModelFactory: ViewModelProvider.Factory = SalesforceSDKManager.getInstance().loginViewModelFactory,
    webviewComposable: @Composable (PaddingValues) -> Unit = {
        innerPadding: PaddingValues -> LoginWebview(innerPadding, webviewClient, viewModelFactory)
    },
) {
    val viewModel: LoginViewModel = viewModel(factory = viewModelFactory)
    var showMenu by remember { mutableStateOf(false) }
    val titleText: String = viewModel.selectedServer.observeAsState().value ?: ""
    val topBarColor: Color = viewModel.topBarColor ?: viewModel.dynamicBackgroundColor.value

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            CenterAlignedTopAppBar(
                expandedHeight = if (viewModel.showTopBar) TopAppBarDefaults.TopAppBarExpandedHeight else 0.dp,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarColor),
                title = viewModel.titleComposable ?:
                    {
                        Text(
                            text = viewModel.titleText ?: titleText,
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
                            onClick = { /* get back function form LoginActivity */ },
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
                // IDP and Bio Auth buttons here
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

        webviewComposable(innerPadding)

        if (viewModel.showServerPicker.value) {
            LoginServerBottomSheet(viewModel)
        }
    }
}