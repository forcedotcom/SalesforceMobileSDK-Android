package com.salesforce.androidsdk.ui.components

import android.webkit.WebView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.ui.LoginViewModel

@Composable
internal fun TokenMigrationView(webViewFactory: () -> WebView) {
    val viewModel: LoginViewModel =
        viewModel(factory = SalesforceSDKManager.getInstance().loginViewModelFactory)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        val alpha: Float by animateFloatAsState(
            targetValue = if (viewModel.loading.value) LOADING_ALPHA else VISIBLE_ALPHA,
            animationSpec = tween(durationMillis = SLOW_ANIMATION_MS),
        )

        AndroidView(
            modifier = Modifier
                .background(Color.Transparent)
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .applyImePaddingConditionally()
                .graphicsLayer(alpha = alpha),
            factory = { webViewFactory.invoke() },
        )

        if (viewModel.loading.value) {
            viewModel.loadingIndicator ?: DefaultLoadingIndicator()
        }
    }
}