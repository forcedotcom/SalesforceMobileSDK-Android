package com.salesforce.androidsdk.ui.components

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.BottomAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.LoginViewModel
import com.salesforce.androidsdk.ui.LoginActivity

@Preview
@Composable
fun LoginView(
    viewModelFactory: ViewModelProvider.Factory = SalesforceSDKManager.getInstance().loginViewModelFactory,
    webViewComposable: (@Composable (PaddingValues) -> Unit)? = null,
) {
    val viewModel: LoginViewModel = viewModel(factory = viewModelFactory)
    var showMenu by remember { mutableStateOf(false) }
    val topBarColor: Color = viewModel.topBarColor ?: viewModel.dynamicBackgroundColor.value
    val activity: LoginActivity = LocalContext.current.getActivity() as LoginActivity

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            CenterAlignedTopAppBar(
                expandedHeight = if (viewModel.showTopBar) TopAppBarDefaults.TopAppBarExpandedHeight else 0.dp,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarColor),
                title = viewModel.titleComposable ?:
                    {
                        Text(
                            text = viewModel.titleText ?: viewModel.defaultTitleText,
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

        // Use our default webview if one was not passed in.
        webViewComposable?.invoke(innerPadding) ?: run {
            LoginWebview(innerPadding, activity.webViewClient, activity.webChromeClient, viewModelFactory)
        }

        if (viewModel.showServerPicker.value) {
            LoginServerBottomSheet(viewModel)
        }
    }
}

private tailrec fun Context.getActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}