package com.salesforce.androidsdk.auth

import android.content.Intent
import android.text.TextUtils.isEmpty
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast.LENGTH_LONG
import android.widget.Toast.makeText
import androidx.compose.ui.graphics.Color
import com.salesforce.androidsdk.R.string.sf__generic_error
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.OAuth2.OAuthFailedException
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse
import com.salesforce.androidsdk.auth.OAuth2.getFrontdoorUrl
import com.salesforce.androidsdk.security.SalesforceKeyGenerator.getRandom128ByteKey
import com.salesforce.androidsdk.security.SalesforceKeyGenerator.getSHA256Hash
import com.salesforce.androidsdk.ui.LoginActivity
import com.salesforce.androidsdk.ui.OAuthWebviewHelper.Companion.AUTHENTICATION_FAILED_INTENT
import com.salesforce.androidsdk.ui.OAuthWebviewHelper.Companion.HTTP_ERROR_RESPONSE_CODE_INTENT
import com.salesforce.androidsdk.ui.OAuthWebviewHelper.Companion.RESPONSE_ERROR_DESCRIPTION_INTENT
import com.salesforce.androidsdk.ui.OAuthWebviewHelper.Companion.RESPONSE_ERROR_INTENT
import com.salesforce.androidsdk.util.EventsObservable
import com.salesforce.androidsdk.util.EventsObservable.EventType.AuthWebViewPageFinished
import com.salesforce.androidsdk.util.SalesforceSDKLogger.e
import com.salesforce.androidsdk.util.UriFragmentParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import java.net.URI

open class LoginWebviewClient(
    private val viewModel: LoginViewModel,
    private val activity: LoginActivity,
): WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {

        // The login web view's embedded button has sent the signal to show the biometric prompt
        if (request.url.toString() == BIOMETRIC_PROMPT) {
            SalesforceSDKManager.getInstance().biometricAuthenticationManager?.run {
                if (hasBiometricOptedIn() && hasBiometricOptedIn()) {
                    (activity as? LoginActivity)?.presentBiometric()
                }
            }
            return true
        }

        // Check if user entered a custom domain
        val loginContainsHost = request.url.host?.let { viewModel.selectedServer.value?.contains(it) } ?: false
        val customDomainPatternMatch = SalesforceSDKManager.getInstance()
                .customDomainInferencePattern?.matcher(request.url.toString())?.find() ?: false
        if (loginContainsHost && customDomainPatternMatch) {
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

                // Set title to the new login URL
                activity.loginOptions.loginUrl = baseUrl

                // Check the configuration for the selected login server
                SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration {
                    if (SalesforceSDKManager.getInstance().isBrowserLoginEnabled) {
                        // This load will trigger advanced auth and do all necessary setup
//                            doLoadPage()
                        // TODO: trigger advanced auth
                    }
                }
            }.onFailure { throwable ->
                e(TAG, "Unable to retrieve auth config.", throwable)
            }
        }

        val formattedUrl = request.url.toString().replace("///", "/").lowercase()
        val callbackUrl = activity.loginOptions.oauthCallbackUrl.replace("///", "/").lowercase()
        val authFlowFinished = formattedUrl.startsWith(callbackUrl)

        if (authFlowFinished) {
            val params = UriFragmentParser.parse(request.url)
            val error = params["error"]
            // Did we fail?
            when {
                error != null -> onAuthFlowError(   // TODO: use this method in LoginActivity -- get invocation of error method, don't use activity itself!
                    error,
                    params["error_description"],
                    null
                )

                else -> {
                    // Determine if presence of override parameters require the user agent flow.
                    val overrideWithUserAgentFlow = viewModel.isUsingFrontDoorBridge && viewModel.frontDoorBridgeCodeVerifier == null
                    when {
                        SalesforceSDKManager.getInstance().useWebServerAuthentication && !overrideWithUserAgentFlow ->
                            viewModel.onWebServerFlowComplete(
                                params["code"],
                                onAuthFlowError =  ::onAuthFlowError,
                                onAuthFlowComplete = ::onAuthFlowComplete,
                            )

                        else ->
                            viewModel.onAuthFlowComplete(TokenEndpointResponse(params), ::onAuthFlowError)
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

    open fun clearCookies() = CookieManager.getInstance().removeAllCookies(null)

    /**
     * A callback when the user facing part of the authentication flow completed
     * with an error.
     *
     * Show the user an error and end the activity.
     *
     * @param error The error
     * @param errorDesc The error description
     * @param e The exception
     */
    internal fun onAuthFlowError(
        error: String,
        errorDesc: String?,
        e: Throwable?,
    ) {
        val instance = SalesforceSDKManager.getInstance()

        // Reset state from previous log in attempt.
        // - Salesforce Identity UI Bridge API log in, such as QR code login.
        viewModel.resetFrontDoorBridgeUrl()

        e(TAG, "$error: $errorDesc", e)

        // Broadcast a notification that the authentication flow failed
        instance.appContext.sendBroadcast(
            Intent(AUTHENTICATION_FAILED_INTENT).apply {
                if (e is OAuthFailedException) {
                    putExtra(
                        HTTP_ERROR_RESPONSE_CODE_INTENT,
                        e.httpStatusCode
                    )
                    putExtra(
                        RESPONSE_ERROR_INTENT,
                        e.tokenErrorResponse.error
                    )
                    putExtra(
                        RESPONSE_ERROR_DESCRIPTION_INTENT,
                        e.tokenErrorResponse.errorDescription
                    )
                }
            })

        clearCookies()

        // Displays the error in a toast, clears cookies and reloads the login page
        activity.runOnUiThread {
            makeText(activity.baseContext, "$error : $errorDesc", LENGTH_LONG).show()
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    protected open fun showError(exception: Throwable) {
        with(activity) {
            runOnUiThread {
                makeText(baseContext, baseContext.getString(sf__generic_error, exception.toString()), LENGTH_LONG).show()
            }
        }
    }

    /**
     * Called when the user facing part of the authentication flow completed
     * successfully.
     */
    open fun onAuthFlowComplete() {
        CoroutineScope(IO).launch {
            // Reset log in state,
            // - Salesforce Identity UI Bridge API log in, such as QR code login.
            viewModel.resetFrontDoorBridgeUrl()
            activity.finish()
        }
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
        const val BIOMETRIC_PROMPT = "mobilesdk://biometric/authentication/prompt"

//        private const val PROMPT_LOGIN = "&prompt=login"
        private const val ALLOW_SCREEN_INDICATOR = "frontdoor.jsp"
        private const val TAG = "LoginWebviewClient"
        private const val backgroundColorJavascript =
            "(function() { return window.getComputedStyle(document.body, null).getPropertyValue('background-color'); })();"
    }
}