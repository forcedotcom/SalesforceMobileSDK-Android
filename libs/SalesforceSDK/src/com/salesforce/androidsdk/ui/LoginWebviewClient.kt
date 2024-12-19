package com.salesforce.androidsdk.ui

import android.content.Intent
import android.text.TextUtils.isEmpty
import android.view.View.INVISIBLE
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast.LENGTH_LONG
import android.widget.Toast.makeText
import androidx.compose.ui.graphics.Color
import com.salesforce.androidsdk.R.id.sf__bio_login_button
import com.salesforce.androidsdk.R.id.sf__idp_login_button
import com.salesforce.androidsdk.R.string.sf__generic_error
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.auth.OAuth2
import com.salesforce.androidsdk.auth.OAuth2.OAuthFailedException
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse
import com.salesforce.androidsdk.auth.OAuth2.callIdentityService
import com.salesforce.androidsdk.auth.OAuth2.exchangeCode
import com.salesforce.androidsdk.auth.OAuth2.getFrontdoorUrl
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions
import com.salesforce.androidsdk.security.SalesforceKeyGenerator.getRandom128ByteKey
import com.salesforce.androidsdk.security.SalesforceKeyGenerator.getSHA256Hash
import com.salesforce.androidsdk.ui.OAuthWebviewHelper.Companion
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
import kotlinx.coroutines.withContext
import java.net.URI

open class LoginWebviewClient(
    private val viewModel: LoginViewModel,
    private val activity: LoginActivity,
): WebViewClient() {
    /** The default, locally generated code verifier */
    private var codeVerifier: String? = null

    /** For Salesforce Identity API UI Bridge support, indicates use of an overriding front door bridge URL in place of the default initial URL */
    private var isUsingFrontDoorBridge = false

    /** For Salesforce Identity API UI Bridge support, the optional web server flow code verifier accompanying the front door bridge URL.  This can only be used with `overrideWithFrontDoorBridgeUrl` */
    private var frontDoorBridgeCodeVerifier: String? = null

//    init {
//        // equivilent to OAuthWebviewHelper's loadLoginPage
//        loadLoginPage()
//    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
//        val loginOptions = loginOptions
//        val loginUrl = viewModel.loginUrl

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
        val loginContainsHost = request.url.host?.let { viewModel.selectedServer?.contains(it) } ?: false
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
                error != null -> onAuthFlowError(
                    error,
                    params["error_description"],
                    null
                )

                else -> {
                    // Determine if presence of override parameters require the user agent flow.
                    val overrideWithUserAgentFlow = isUsingFrontDoorBridge && frontDoorBridgeCodeVerifier == null
                    when {
                        SalesforceSDKManager.getInstance().useWebServerAuthentication && !overrideWithUserAgentFlow ->
//                            onWebServerFlowComplete(params["code"])
                            viewModel.onWebServerFlowComplete(
                                params["code"],
                                onAuthFlowError =  ::onAuthFlowError,
                                onAuthFlowComplete = ::onAuthFlowComplete,
                            )

                        else ->
                            onAuthFlowComplete(TokenEndpointResponse(params))
                    }
                }
            }
        }

        return authFlowFinished
    }

//    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
//        return url != null && url.startsWith("https://google.com")
//    }


    override fun onPageFinished(view: WebView?, url: String?) {
        view?.evaluateJavascript(backgroundColorJavascript) { result ->
            viewModel.loading.value = false
            viewModel.dynamicBackgroundColor.value = validateAndExtractBackgroundColor(result) ?: return@evaluateJavascript
        }

//        viewModel.loading.value = false

        // Remove the native login buttons (biometric, IDP) once on the allow/deny screen
        if (url?.contains(ALLOW_SCREEN_INDICATOR) == true) {
            // TODO: hide buttons via viewModel
        }
        EventsObservable.get().notifyEvent(AuthWebViewPageFinished, url)

        super.onPageFinished(view, url)
    }

    open fun clearCookies() = CookieManager.getInstance().removeAllCookies(null)

    /**
     * Reloads the authorization page in the web view.  Also, updates the window
     * title so it's easier to identify the login system.
     */
    internal fun loadLoginPage() = activity.loginOptions.let { loginOptions ->
        when {
            isEmpty(loginOptions.jwt) -> {
//                loginOptions.loginUrl = viewModel.loginUrl
                doLoadPage()
            }

//            else -> CoroutineScope(IO).launch {
//                SwapJWTForAccessTokenTask().execute(loginOptions)
//            }
        }
    }

    private fun doLoadPage() {
        runCatching {
            var uri = getAuthorizationUrl(
                useWebServerAuthentication = SalesforceSDKManager.getInstance().isBrowserLoginEnabled
                        || SalesforceSDKManager.getInstance().useWebServerAuthentication,
                useHybridAuthentication = SalesforceSDKManager.getInstance().useHybridAuthentication
            )

//            callback.loadingLoginPage(loginOptions.loginUrl)

            when {
                SalesforceSDKManager.getInstance().isBrowserLoginEnabled -> {
                    if (!SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled) {
                        uri = URI("$uri$PROMPT_LOGIN")
                    }
//                    loadLoginPageInCustomTab(uri)  // TODO: implement this
                }

                else -> viewModel.loginUrl.value = uri.toString()
            }
        }.onFailure { throwable ->
            showError(throwable)
        }
    }


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
        resetFrontDoorBridgeUrl()

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
        loadLoginPage()

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
     * successfully. The last step is to call the identity service to get the
     * username.
     */
    open fun onAuthFlowComplete(tr: TokenEndpointResponse?, nativeLogin: Boolean = false) {
        CoroutineScope(IO).launch {

            // Reset log in state,
            // - Salesforce Identity UI Bridge API log in, such as QR code login.
            resetFrontDoorBridgeUrl()

//            FinishAuthTask().execute(tr, nativeLogin)
            activity.finishAuth(tr)
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    protected open fun getAuthorizationUrl(
        useWebServerAuthentication: Boolean,
        useHybridAuthentication: Boolean
    ): URI {

        // Reset log in state,
        // - Salesforce Identity UI Bridge API log in, such as QR code login.
        resetFrontDoorBridgeUrl()

        with(viewModel) {
            val jwtFlow = !isEmpty(loginOptions.jwt)
            val additionalParams = when {
                jwtFlow -> null
                else -> loginOptions.additionalParameters ?: emptyMap<String, String>()
            }

            // NB code verifier / code challenge are only used when useWebServerAuthentication is true
            val codeVerifier = getRandom128ByteKey().also { codeVerifier = it }
            val codeChallenge = getSHA256Hash(codeVerifier)
            val authorizationUrl = OAuth2.getAuthorizationUrl(
                useWebServerAuthentication,
                useHybridAuthentication,
                URI(viewModel.loginUrl.value),
                oAuthClientId,
                loginOptions.oauthCallbackUrl,
                loginOptions.oauthScopes,
                authorizationDisplayType,
                codeChallenge,
                additionalParams
            )

            return when {
                jwtFlow -> getFrontdoorUrl(
                    authorizationUrl,
                    loginOptions.jwt,
                    loginOptions.loginUrl,
                    loginOptions.additionalParameters
                )

                else -> authorizationUrl
            }
        }
    }

//    internal fun onWebServerFlowComplete(code: String?) =
//        CoroutineScope(IO).launch {
//            doCodeExchangeEndpoint(code)
//        }


    private suspend fun doCodeExchangeEndpoint(
        code: String?
    ) = withContext(IO) {
        var tokenResponse: TokenEndpointResponse? = null
        runCatching {
            tokenResponse = exchangeCode(
                HttpAccess.DEFAULT,
                URI.create("https://login.salesforce.com"),
//                URI.create(activity.loginOptions.loginUrl),
                activity.loginOptions.oauthClientId,
                code,
                frontDoorBridgeCodeVerifier ?: codeVerifier,
                activity.loginOptions.oauthCallbackUrl
            )
        }.onFailure { throwable ->
            e(TAG, "Exception occurred while making token request", throwable)
            onAuthFlowError("Token Request Error", throwable.message, throwable)
        }
        onAuthFlowComplete(tokenResponse)
    }

    /**
     * Resets all state related to Salesforce Identity API UI Bridge front door bridge URL log in to
     * its default inactive state.
     */
    private fun resetFrontDoorBridgeUrl() {
        isUsingFrontDoorBridge = false
        frontDoorBridgeCodeVerifier = null
    }

    /**
     * A background process that will call the identity service to get the info
     * needed from the Identity service and finally wrap up and create the account.
     */
//    private inner class FinishAuthTask : BaseFinishAuthFlowTask<TokenEndpointResponse?>() {
//
//        @Suppress("unused")
//        override fun performRequest(param: TokenEndpointResponse?): TokenEndpointResponse? {
//            runCatching {
//                id = callIdentityService(
//                    DEFAULT,
//                    param?.idUrlWithInstance,
//                    param?.authToken
//                )
//
//                // Request the authenticated user's information to determine if it is a Salesforce integration user.
//                // This is a synchronous network request, so it must be performed here in the background stage.
//                shouldBlockSalesforceIntegrationUser = SalesforceSDKManager.getInstance()
//                    .shouldBlockSalesforceIntegrationUser && fetchIsSalesforceIntegrationUser(param)
//            }.onFailure { throwable ->
//                backgroundException = throwable
//            }
//            return param
//        }
//    }

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

        private const val PROMPT_LOGIN = "&prompt=login"
        private const val ALLOW_SCREEN_INDICATOR = "frontdoor.jsp"
        private const val TAG = "LoginWebviewClient"
        private const val backgroundColorJavascript =
            "(function() { return window.getComputedStyle(document.body, null).getPropertyValue('background-color'); })();"
    }
}