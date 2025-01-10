package com.salesforce.androidsdk.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
        factory = { activity.webView },
        update = {
            it.loadUrl(loginUrl)
        }
    )
}