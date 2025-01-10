package com.salesforce.androidsdk.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.LoginViewModel
import com.salesforce.androidsdk.ui.LoginActivity

@Composable
fun LoginWebview(paddingValues: PaddingValues) {
    val viewModel: LoginViewModel = viewModel(factory = SalesforceSDKManager.getInstance().loginViewModelFactory)
    val loginUrl: String = viewModel.loginUrl.observeAsState().value ?: ""
    val activity: LoginActivity = LocalContext.current.getActivity() as LoginActivity

    AndroidView(
        modifier = Modifier
            .padding(paddingValues)
            .alpha(
                if (viewModel.loading.value) 0.0f else 100.0f
            ),
        factory = { activity.webViewFactory },
        update = {
            it.loadUrl(loginUrl)
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
fun loginWebViewFactory(context: Context, webViewClient: WebViewClient, webChromeClient: WebChromeClient): WebView {
    val webView = WebView(context).apply {
        this.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        this.webViewClient = webViewClient
        this.webChromeClient = webChromeClient
    }
    webView.setBackgroundColor(Color.Transparent.toArgb())
    webView.settings.javaScriptEnabled = true
    return webView
}