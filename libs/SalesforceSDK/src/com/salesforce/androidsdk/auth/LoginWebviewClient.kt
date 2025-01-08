package com.salesforce.androidsdk.auth

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.graphics.Color
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse
import com.salesforce.androidsdk.util.EventsObservable
import com.salesforce.androidsdk.util.EventsObservable.EventType.AuthWebViewPageFinished
import com.salesforce.androidsdk.util.SalesforceSDKLogger.e
import com.salesforce.androidsdk.util.UriFragmentParser

open class LoginWebviewClient(
    private val viewModel: LoginViewModel,
    private val onAuthFlowError: (error: String, errorDesc: String?, e: Throwable?) -> Unit,
    private val onAuthFlowSuccess: (userAccount: UserAccount) -> Unit,
): WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        // Check if user entered a custom domain
        val customDomainPatternMatch = SalesforceSDKManager.getInstance()
            .customDomainInferencePattern?.matcher(request.url.toString())?.find() ?: false
        val loginContainsHost = request.url.host?.let { viewModel.selectedServer.value?.contains(it) } ?: false
        if (customDomainPatternMatch && !loginContainsHost) {
            runCatching {
                val baseUrl = "https://${request.url.host}"
                val serverManager = SalesforceSDKManager.getInstance().loginServerManager

                // Check if the URL is already in the server list
                when (val loginServer = serverManager.getLoginServerFromURL(baseUrl)) {
                    null ->
                        // Add also sets as selected
                        serverManager.addCustomLoginServer("Custom Domain", baseUrl)
                    else ->
                        serverManager.selectedLoginServer = loginServer
                }
            }.onFailure { throwable ->
                e(TAG, "Unable to retrieve auth config.", throwable)
            }
        }

        val formattedUrl = request.url.toString().replace("///", "/").lowercase()
        val callbackUrl = viewModel.bootConfig.oauthRedirectURI.replace("///", "/").lowercase()
        val authFlowFinished = formattedUrl.startsWith(callbackUrl)

        if (authFlowFinished) {
            val params = UriFragmentParser.parse(request.url)
            val error = params["error"]
            // Did we fail?
            when {
                error != null -> onAuthFlowError(
                    error,
                    params["error_description"],
                    null
                )

                else -> {
                    // Determine if presence of override parameters require the user agent flow.
                    val overrideWithUserAgentFlow = viewModel.isUsingFrontDoorBridge && viewModel.frontDoorBridgeCodeVerifier == null
                    when {
                        SalesforceSDKManager.getInstance().useWebServerAuthentication && !overrideWithUserAgentFlow ->
                            viewModel.onWebServerFlowComplete(params["code"], onAuthFlowError, onAuthFlowSuccess)

                        else ->
                            viewModel.onAuthFlowComplete(TokenEndpointResponse(params), onAuthFlowError, onAuthFlowSuccess)
                    }
                }
            }
        }

        return authFlowFinished
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        view?.evaluateJavascript(backgroundColorJavascript) { result ->
            viewModel.loading.value = false
            if (url == "about:blank") {
                viewModel.dynamicBackgroundColor.value = Color.White
                return@evaluateJavascript
            }

            viewModel.dynamicBackgroundColor.value = validateAndExtractBackgroundColor(result) ?: return@evaluateJavascript
        }

        // Remove the native login buttons (biometric, IDP) once on the allow/deny screen
        if (url?.contains(ALLOW_SCREEN_INDICATOR) == true) {
            // TODO: hide buttons via viewModel
        }
        EventsObservable.get().notifyEvent(AuthWebViewPageFinished, url)

        super.onPageFinished(view, url)
    }

    private fun validateAndExtractBackgroundColor(javaScriptResult: String): Color? {
        // This parses the expected "rgb(x, x, x)" string.
        val rgbTextPattern = "rgb\\((\\d{1,3}), (\\d{1,3}), (\\d{1,3})\\)".toRegex()
        val rgbMatch = rgbTextPattern.find(javaScriptResult)

        // groupValues[0] is the entire match.  [1] is red, [2] is green, [3] is green.
        rgbMatch?.groupValues?.get(3) ?: return null
        val red = rgbMatch.groupValues[1].toIntOrNull() ?: return null
        val green = rgbMatch.groupValues[2].toIntOrNull() ?: return null
        val blue = rgbMatch.groupValues[3].toIntOrNull() ?: return null

        return Color(red, green, blue)
    }

    companion object {
        private const val ALLOW_SCREEN_INDICATOR = "frontdoor.jsp"
        private const val TAG = "LoginWebviewClient"
        private const val backgroundColorJavascript =
            "(function() { return window.getComputedStyle(document.body, null).getPropertyValue('background-color'); })();"
    }
}