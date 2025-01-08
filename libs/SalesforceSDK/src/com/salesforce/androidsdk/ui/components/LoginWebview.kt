package com.salesforce.androidsdk.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salesforce.androidsdk.auth.LoginViewModel
import com.salesforce.androidsdk.auth.LoginWebviewClient

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginWebview(
    paddingValues: PaddingValues,
    webviewClient: LoginWebviewClient,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
) {
    val loginUrl: String = viewModel.loginUrl.observeAsState().value ?: ""

    AndroidView(
        modifier = Modifier
            .padding(paddingValues)
            .alpha(
                if (viewModel.loading.value) 0.0f else 100.0f
            ),
        factory = {
            val webView = WebView(it).apply {
                this.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                this.webViewClient = webviewClient
            }
            webView.setBackgroundColor(Color.Transparent.toArgb())
            webView.settings.javaScriptEnabled = true
            webView
        }, update = {
            it.loadUrl(loginUrl)
        }
    )
}