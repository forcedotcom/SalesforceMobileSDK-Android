/*
 * Copyright (c) 2026-pffsent, salesforce.com, inc.
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
package com.salesforce.androidsdk.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PROTECTED
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.salesforce.androidsdk.accounts.MigrationCallbackRegistry
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.app.SalesforceSDKManager.Theme.DARK
import com.salesforce.androidsdk.auth.OAuth2.FRONTDOOR_URL_KEY
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse
import com.salesforce.androidsdk.config.OAuthConfig
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.ui.LoginActivity.Companion.BACKGROUND_COLOR_JAVASCRIPT
import com.salesforce.androidsdk.ui.LoginActivity.Companion.validateAndExtractBackgroundColor
import com.salesforce.androidsdk.ui.components.DefaultLoadingIndicator
import com.salesforce.androidsdk.ui.components.LOADING_ALPHA
import com.salesforce.androidsdk.ui.components.SLOW_ANIMATION_MS
import com.salesforce.androidsdk.ui.components.VISIBLE_ALPHA
import com.salesforce.androidsdk.ui.components.applyImePaddingConditionally
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import com.salesforce.androidsdk.util.UriFragmentParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val HALF_ALPHA = 0.5f
internal class TokenMigrationActivity : ComponentActivity() {

    @VisibleForTesting(otherwise = PROTECTED)
    private val viewModel: LoginViewModel
            by viewModels { SalesforceSDKManager.getInstance().loginViewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val callbackKey = intent.getStringExtra(EXTRA_CALLBACK_ID) ?: run {
            SalesforceSDKLogger.e(TAG, "Unable to parse MigrationResult callback id.")
            finish()
            return
        }
        val resultCallback = MigrationCallbackRegistry.consume(callbackKey) ?: run {
            SalesforceSDKLogger.e(TAG, "Unable to retrieve MigrationResult callback.")
            finish()
            return
        }

        // TODO: Move to non-deprecated getParcelableExtra when min API >= 33
        val oAuthConfig = intent.getParcelableExtra<OAuthConfig>(EXTRA_OAUTH_CONFIG) ?: run {
            logMigrationError(resultCallback, "Unable to parse OAuthConfig.", null, null)
            return
        }

        val orgId = intent.getStringExtra(EXTRA_ORG_ID)
        val userId = intent.getStringExtra(EXTRA_USER_ID)
        if ( orgId == null || userId == null) {
            logMigrationError(resultCallback, "Unable to parse OAuthConfig.", null, null)
            return
        }
        val user = UserAccountManager.getInstance().getUserFromOrgAndUserId(orgId, userId) ?: run {
            logMigrationError(resultCallback, "Unable to build user account.", null, null)
            return
        }
        val client = runCatching {
            SalesforceSDKManager.getInstance().clientManager.peekRestClient(user)
        }.getOrElse { e ->
            logMigrationError(resultCallback, "Unable to build RestClient.", null, e as? Exception)
            return
        }

        lifecycleScope.launch {
            val frontDoorUrl = withContext(IO) {
                runCatching {
                    val authorizationUrl = viewModel.getAuthorizationUrl(
                        server = user.instanceServer,
                        migrationOAuthConfig = oAuthConfig,
                    )
                    val authorizationPath = with(authorizationUrl.toUri()) { "$path?$query" }
                    val request = RestRequest.getRequestForSingleAccess(authorizationPath)
                    val singleAccessResponse = client.sendSync(request)

                    singleAccessResponse
                        ?.takeIf { it.isSuccess }
                        ?.let {
                            Json.parseToJsonElement(it.asString())
                                .jsonObject[FRONTDOOR_URL_KEY]
                                ?.jsonPrimitive?.content
                        }
                }.getOrNull()
            } ?: run {
                logMigrationError(
                    resultCallback = resultCallback,
                    error = "Request for single access bridge url failed",
                    errorDesc = "User's existing token may be invalid.",
                    e = null,
                )
                return@launch
            }
            viewModel.dynamicBackgroundColor.value = Color.Transparent.copy(alpha = HALF_ALPHA)
            makeStatusBarVisible()

            setContent {
                MaterialTheme(
                    colorScheme = SalesforceSDKManager.getInstance().colorScheme().copy(
                        background = viewModel.dynamicBackgroundColor.value
                    )
                ) {
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
                            factory = {
                                buildAuthWebview(frontDoorUrl, resultCallback, user.instanceServer)
                            },
                        )

                        if (viewModel.loading.value) {
                            viewModel.loadingIndicator ?: DefaultLoadingIndicator()
                        }
                    }
                }
            }
        }
    }

    @VisibleForTesting
    internal fun buildAuthWebview(
        frontDoorUrl: String,
        resultCallback: MigrationCallbackRegistry.MigrationCallbacks,
        instanceServer: String,
    ): WebView = WebView(this@TokenMigrationActivity).apply {
        @SuppressLint("SetJavaScriptEnabled") // Required by Salesforce
        settings.javaScriptEnabled = true
        settings.userAgentString = SalesforceSDKManager.getInstance().userAgent
        setBackgroundColor(android.graphics.Color.TRANSPARENT)

        // This implementation is very similar to [LoginActivity.AuthWebViewClient] but the
        // code cannot be shared due to the heavy reliance on the ViewModel.
        webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                val url = request.url.toString().replace("///", "/").lowercase()
                val callbackUrl = viewModel.oAuthConfig.redirectUri.replace("///", "/").lowercase()
                val migrationFinished = url.startsWith(callbackUrl)

                if (migrationFinished) {
                    viewModel.authFinished.value = true
                    viewModel.loading.value = true

                    val params = UriFragmentParser.parse(request.url)
                    val error = params["error"]
                    // Did we fail?
                    when {
                        error != null -> {
                            logMigrationError(
                                resultCallback = resultCallback,
                                error = error,
                                errorDesc = params["error_description"],
                                e = null,
                            )
                        }

                        else -> {
                            // Show loading while we PKCE and/or create user account.
                            viewModel.authFinished.value = true
                            viewModel.loading.value = true

                            CoroutineScope(Default).launch {
                                when {
                                    viewModel.useWebServerFlow ->
                                        viewModel.onWebServerFlowComplete(
                                            code = params["code"],
                                            onAuthFlowError = resultCallback.onMigrationError,
                                            onAuthFlowSuccess = resultCallback.onMigrationSuccess,
                                            loginServer = instanceServer,
                                            tokenMigration = true,
                                        ).join()

                                    else ->
                                        viewModel.onAuthFlowComplete(
                                            tr = TokenEndpointResponse(params),
                                            onAuthFlowError = resultCallback.onMigrationError,
                                            onAuthFlowSuccess = resultCallback.onMigrationSuccess,
                                            tokenMigration = true,
                                        )
                                }

                                // Wait until we are completely finished so progress indicator is shown.
                                finish()
                            }
                        }
                    }
                }

                return migrationFinished
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                viewModel.loading.value = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(BACKGROUND_COLOR_JAVASCRIPT) { result ->
                    makeStatusBarVisible()
                    validateAndExtractBackgroundColor(result)?.let { color ->
                        viewModel.dynamicBackgroundColor.value = color

                        // This check is inside validateAndExtractBackgroundColor because we only
                        // want to stop showing the spinner if WebView UI is actually displayed.
                        if (!viewModel.authFinished.value) {
                            viewModel.loading.value = false
                        }
                    }
                }

                super.onPageFinished(view, url)
            }
        }

        loadUrl(frontDoorUrl)
    }

    private fun logMigrationError(
        resultCallback: MigrationCallbackRegistry.MigrationCallbacks,
        error: String,
        errorDesc: String?,
        e: Throwable?,
    ) {
        val message = error + (errorDesc?.let { ": $it" } ?: "")
        SalesforceSDKLogger.e(TAG, message, e)
        resultCallback.onMigrationError(error, errorDesc, e)
        finish()
    }

    // Ensure Status Bar Icons are readable no matter which OS theme is used.
    private fun makeStatusBarVisible() {
        val usingDarkTheme = viewModel.dynamicBackgroundTheme.value == DARK
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = usingDarkTheme
    }

    companion object {
        const val EXTRA_OAUTH_CONFIG = "MIGRATION_OAUTH_CONFIG"
        const val EXTRA_ORG_ID = "MIGRATION_ORG_ID"
        const val EXTRA_USER_ID = "MIGRATION_USER_ID"
        const val EXTRA_CALLBACK_ID = "MIGRATION_CALLBACK"

        const val TAG = "TokenMigrationActivity"
    }
}
