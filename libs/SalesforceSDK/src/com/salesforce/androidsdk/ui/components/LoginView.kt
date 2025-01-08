package com.salesforce.androidsdk.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salesforce.androidsdk.auth.LoginViewModel
import com.salesforce.androidsdk.auth.LoginWebviewClient

// TODO: split this into multiple components

@OptIn(ExperimentalMaterial3Api::class)
//@Preview
@Composable
fun LoginView(
    webviewClient: LoginWebviewClient,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
    topAppBarColor: Color = viewModel.dynamicBackgroundColor.value,
    titleText: String = viewModel.selectedServer.value.toString(),
    titleComposable: @Composable () -> Unit =
        { Text(text = titleText, color = viewModel.dynamicHeaderTextColor.value, fontWeight = FontWeight.Bold) },
    webviewComposable: @Composable (PaddingValues) -> Unit = {
        innerPadding: PaddingValues -> LoginWebview(innerPadding, webviewClient, viewModel)
    },
    showTopAppBar: Boolean = true,
) {
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                expandedHeight = if (showTopAppBar) TopAppBarDefaults.TopAppBarExpandedHeight else 0.dp,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topAppBarColor),
                title = titleComposable,
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
                            onClick = { /* */ },
                            text = { Text("Clear Cookies", color = Color.Gray) },
                        )
                        DropdownMenuItem(
                            onClick = { /* */ },
                            text = { Text("Reload", color = Color.Gray) },
                        )
                    }
                },
                // conditionally show this when we need a back button
//                    navigationIcon = {
//                        IconButton(
//                            onClick = { this@JetpackLoginActivity.finish() },
//                            colors = IconButtonColors(
//                                containerColor = Color.Transparent,
//                                contentColor = viewModel.headerTextColor.value,
//                                disabledContainerColor = Color.Transparent,
//                                disabledContentColor = Color.Transparent,
//                            ),
//                        ) {
//                            Icon(
//                                Icons.AutoMirrored.Filled.ArrowBack,
//                                contentDescription = "Back",
//                            )
//                        }
//                    }
            )
        },
        bottomBar = {
            val showBottomAppBar = false
            BottomAppBar(containerColor = viewModel.dynamicBackgroundColor.value) {
                // IDP and Bio Auth buttons here
                if (showBottomAppBar) {
                    Button(
                        onClick = { /* Save new server */ },
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        colors = ButtonColors(
                            containerColor = Color.Black,
                            contentColor = Color.Black,
                            disabledContainerColor = Color.Black,
                            disabledContentColor = Color.Black
                        )
                    ) {
                        Text(text = "Save", color = Color.White)
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

        webviewComposable(innerPadding)

        if (viewModel.showServerPicker.value) {
            LoginServerBottomSheet(viewModel)
        }
    }
}