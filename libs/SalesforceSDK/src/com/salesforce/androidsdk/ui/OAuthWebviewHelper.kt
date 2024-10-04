/*
 * Copyright (c) 2011-present, salesforce.com, inc.
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

import android.R.anim.slide_in_left
import android.R.anim.slide_out_right
import android.app.Activity
import android.app.PendingIntent.FLAG_CANCEL_CURRENT
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.getActivity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory.decodeResource
import android.net.Uri.parse
import android.net.http.SslError
import android.net.http.SslError.SSL_EXPIRED
import android.net.http.SslError.SSL_IDMISMATCH
import android.net.http.SslError.SSL_NOTYETVALID
import android.net.http.SslError.SSL_UNTRUSTED
import android.os.Bundle
import android.security.KeyChain.getCertificateChain
import android.security.KeyChain.getPrivateKey
import android.security.KeyChainAliasCallback
import android.text.TextUtils.isEmpty
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.webkit.ClientCertRequest
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.Toast.LENGTH_LONG
import android.widget.Toast.makeText
import androidx.browser.customtabs.CustomTabsIntent
import com.salesforce.androidsdk.R.color.sf__primary_color
import com.salesforce.androidsdk.R.drawable.sf__action_back
import com.salesforce.androidsdk.R.id.sf__bio_login_button
import com.salesforce.androidsdk.R.id.sf__idp_login_button
import com.salesforce.androidsdk.R.id.sf__loading_spinner
import com.salesforce.androidsdk.R.string.oauth_display_type
import com.salesforce.androidsdk.R.string.sf__biometric_signout_user
import com.salesforce.androidsdk.R.string.sf__generic_authentication_error
import com.salesforce.androidsdk.R.string.sf__generic_authentication_error_title
import com.salesforce.androidsdk.R.string.sf__generic_error
import com.salesforce.androidsdk.R.string.sf__jwt_authentication_error
import com.salesforce.androidsdk.R.string.sf__managed_app_error
import com.salesforce.androidsdk.R.string.sf__pick_server
import com.salesforce.androidsdk.R.string.sf__ssl_error
import com.salesforce.androidsdk.R.string.sf__ssl_expired
import com.salesforce.androidsdk.R.string.sf__ssl_id_mismatch
import com.salesforce.androidsdk.R.string.sf__ssl_not_yet_valid
import com.salesforce.androidsdk.R.string.sf__ssl_unknown_error
import com.salesforce.androidsdk.R.string.sf__ssl_untrusted
import com.salesforce.androidsdk.R.style.SalesforceSDK
import com.salesforce.androidsdk.R.style.SalesforceSDK_Dark_Login
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.analytics.EventBuilderHelper.createAndStoreEventSync
import com.salesforce.androidsdk.app.Features.FEATURE_BIOMETRIC_AUTH
import com.salesforce.androidsdk.app.Features.FEATURE_SCREEN_LOCK
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.HttpAccess.DEFAULT
import com.salesforce.androidsdk.auth.OAuth2
import com.salesforce.androidsdk.auth.OAuth2.IdServiceResponse
import com.salesforce.androidsdk.auth.OAuth2.OAuthFailedException
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse
import com.salesforce.androidsdk.auth.OAuth2.addAuthorizationHeader
import com.salesforce.androidsdk.auth.OAuth2.callIdentityService
import com.salesforce.androidsdk.auth.OAuth2.exchangeCode
import com.salesforce.androidsdk.auth.OAuth2.getAuthorizationUrl
import com.salesforce.androidsdk.auth.OAuth2.getFrontdoorUrl
import com.salesforce.androidsdk.auth.OAuth2.revokeRefreshToken
import com.salesforce.androidsdk.auth.OAuth2.swapJWTForTokens
import com.salesforce.androidsdk.config.RuntimeConfig.getRuntimeConfig
import com.salesforce.androidsdk.push.PushMessaging.register
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions
import com.salesforce.androidsdk.rest.RestClient.clearCaches
import com.salesforce.androidsdk.security.BiometricAuthenticationManager
import com.salesforce.androidsdk.security.BiometricAuthenticationManager.Companion.isBiometricAuthenticationEnabled
import com.salesforce.androidsdk.security.SalesforceKeyGenerator.getRandom128ByteKey
import com.salesforce.androidsdk.security.SalesforceKeyGenerator.getSHA256Hash
import com.salesforce.androidsdk.security.ScreenLockManager
import com.salesforce.androidsdk.ui.LoginActivity.Companion.PICK_SERVER_REQUEST_CODE
import com.salesforce.androidsdk.util.EventsObservable
import com.salesforce.androidsdk.util.EventsObservable.EventType.AuthWebViewPageFinished
import com.salesforce.androidsdk.util.SalesforceSDKLogger.d
import com.salesforce.androidsdk.util.SalesforceSDKLogger.e
import com.salesforce.androidsdk.util.SalesforceSDKLogger.w
import com.salesforce.androidsdk.util.UriFragmentParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request.Builder
import org.json.JSONArray
import org.json.JSONObject
import java.lang.String.format
import java.net.URI
import java.net.URI.create
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.function.Consumer

/**
 * A helper class to manage a web view going through the OAuth login process.
 * The basic flow is:
 * a) Load and show the login page to the user
 * b) User login and app authorization
 * c) Navigate to the authentication completion URL and token fetch
 * d) Call the id service to obtain additional info about the user
 * e) Create a local account and return an authentication result bundle
 *
 * @Deprecated This class will no longer be public starting in Mobile SDK 13.0.  It
 * is no longer necessary to extend or change LoginActivity's instance of this class
 * to support multi-factor authentication.  If there are other uses cases please
 * inform the team via Github or our Trailblazer community.
 */
@Deprecated(
    "This class will no longer be public starting in Mobile SDK 13.0.",
    level = DeprecationLevel.WARNING,
)
open class OAuthWebviewHelper : KeyChainAliasCallback {

    /** The default, locally generated code verifier */
    private var codeVerifier: String? = null

    /** For Salesforce Identity API UI Bridge support, indicates use of an overriding front door bridge URL in place of the default initial URL */
    private var isUsingFrontDoorBridge = false

    /** For Salesforce Identity API UI Bridge support, the optional web server flow code verifier accompanying the front door bridge URL.  This can only be used with `overrideWithFrontDoorBridgeUrl` */
    private var frontDoorBridgeCodeVerifier: String? = null

    /**
     * The host activity/fragment should pass in an implementation of this
     * interface so that it can notify it of things it needs to do as part of
     * the oauth process.
     */
    interface OAuthWebviewHelperEvents {

        /** Called when web view starts loading the login page */
        fun loadingLoginPage(loginUrl: String)

        /**
         * Called when the authentication process completes and the
         * authentication result bundle is returned to the authenticator
         */
        fun onAccountAuthenticatorResult(authResult: Bundle)

        /** Called when the host activity can be finished and closed */
        fun finish(userAccount: UserAccount?)
    }

    /**
     * Construct a new instance and performs initial configuration of the web
     * view.
     *
     * @param activity The activity using this instance
     * @param callback The callbacks for key events
     * @param loginOptions The log in options
     * @param webView The web view
     * @param savedInstanceState The saved instance state
     */
    @JvmOverloads
    constructor(
        activity: Activity,
        callback: OAuthWebviewHelperEvents,
        loginOptions: LoginOptions,
        webView: WebView,
        savedInstanceState: Bundle?,
        shouldReloadPage: Boolean = false,
    ) {
        this.activity = activity
        this.callback = callback
        this.context = webView.context
        this.webView = webView
        this.loginOptions = loginOptions

        webView.apply {
            webView.settings.apply {
                javaScriptEnabled = true
                userAgentString = format(
                    "%s %s",
                    SalesforceSDKManager.getInstance().userAgent,
                    userAgentString ?: ""
                )
            }
            webViewClient = makeWebViewClient()
            webChromeClient = makeWebChromeClient()
        }

        activity.setTheme(
            when {
                SalesforceSDKManager.getInstance().isDarkTheme -> SalesforceSDK_Dark_Login
                else -> SalesforceSDK
            }
        )

        /*
         * Restore web view state if available. This ensures the user is not
         * forced to type in credentials again once the authentication process
         * kicks off.
         */
        when (savedInstanceState) {
            null -> clearCookies()
            else -> {
                webView.restoreState(savedInstanceState)
                accountOptions = savedInstanceState.getBundle(ACCOUNT_OPTIONS)
            }
        }
    }

    constructor(
        context: Context,
        callback: OAuthWebviewHelperEvents,
        loginOptions: LoginOptions
    ) {
        this.context = context
        this.callback = callback
        this.loginOptions = loginOptions
        this.webView = null
        this.activity = null
    }

    private val callback: OAuthWebviewHelperEvents

    protected val loginOptions: LoginOptions

    val webView: WebView?

    private var accountOptions: Bundle? = null

    protected val context: Context

    private val activity: Activity?

    private var key: PrivateKey? = null

    private var certChain: Array<X509Certificate>? = null

    /**
     * This value is no longer needed to support Multi-Factor Authentication via
     * standard or advanced authentication flows.
     *
     * @Deprecated This value is no longer used.
     */
    var shouldReloadPage: Boolean = false
        private set

    internal fun saveState(outState: Bundle) {
        val accountOptions = accountOptions

        webView?.saveState(outState)
        if (accountOptions != null) {
            // The authentication flow is complete but an account has not been created since a pin is needed
            outState.putBundle(
                ACCOUNT_OPTIONS,
                accountOptions
            )
        }
    }

    open fun clearCookies() =
        CookieManager.getInstance().removeAllCookies(null)

    private fun clearView() =
        activity?.runOnUiThread { webView?.loadUrl("about:blank") }

    /**
     * A factory method for the web view client. This can be overridden as
     * needed.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    protected open fun makeWebViewClient() = AuthWebViewClient()

    /**
     * A factory method for the web Chrome client. This can be overridden as
     * needed
     */
    @Suppress("MemberVisibilityCanBePrivate")
    protected open fun makeWebChromeClient() = WebChromeClient()

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
        e: Throwable?
    ) {
        val instance = SalesforceSDKManager.getInstance()

        // Reset state from previous log in attempt.
        // - Salesforce Identity UI Bridge API log in, such as QR code log in.
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

        // Displays the error in a toast, clears cookies and reloads the login page
        activity?.runOnUiThread {
            webView?.let { webView ->
                makeText(
                    webView.context,
                    "$error : $errorDesc",
                    LENGTH_LONG
                ).let { toast ->
                    webView.postDelayed({
                        clearCookies()
                        loadLoginPage()
                    }, toast.duration.toLong())
                    toast.show()
                }
            }
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    protected open fun showError(exception: Throwable) {
        activity?.runOnUiThread {
            makeText(
                context,
                context.getString(
                    sf__generic_error,
                    exception.toString()
                ),
                LENGTH_LONG
            ).show()
        }
    }

    /**
     * Reloads the authorization page in the web view.  Also, updates the window
     * title so it's easier to identify the login system.
     */
    internal fun loadLoginPage() = loginOptions.let { loginOptions ->
        when {
            isEmpty(loginOptions.jwt) -> {
                loginOptions.loginUrl = this@OAuthWebviewHelper.loginUrl
                doLoadPage()
            }

            else -> CoroutineScope(IO).launch {
                SwapJWTForAccessTokenTask().execute(loginOptions)
            }
        }
    }

    private fun doLoadPage() {
        val instance = SalesforceSDKManager.getInstance()
        runCatching {
            var uri = getAuthorizationUrl(
                useWebServerAuthentication = instance.isBrowserLoginEnabled || instance.useWebServerAuthentication,
                useHybridAuthentication = instance.useHybridAuthentication
            )

            callback.loadingLoginPage(loginOptions.loginUrl)

            when {
                instance.isBrowserLoginEnabled -> {
                    if (!instance.isShareBrowserSessionEnabled) {
                        uri = URI("$uri$PROMPT_LOGIN")
                    }
                    loadLoginPageInCustomTab(uri)
                }

                else -> webView?.loadUrl(uri.toString())
            }
        }.onFailure { throwable ->
            showError(throwable)
        }
    }

    private fun loadLoginPageInCustomTab(uri: URI) {
        val activity = activity ?: return

        val customTabsIntent = CustomTabsIntent.Builder().apply {
            /*
             * Set a custom animation to slide in and out for Chrome custom tab
             * so it doesn't look like a swizzle out of the app and back in
             */
            activity.let { activity ->
                setStartAnimations(
                    activity,
                    slide_in_left,
                    slide_out_right
                )
                setExitAnimations(
                    activity,
                    slide_in_left,
                    slide_out_right
                )
            }

            // Replace the default 'Close Tab' button with a custom back arrow instead of 'x'
            setCloseButtonIcon(
                decodeResource(
                    activity.resources,
                    sf__action_back
                )
            )
            setToolbarColor(context.getColor(sf__primary_color))

            // Add a menu item to change the server
            addMenuItem(
                activity.getString(sf__pick_server),
                getActivity(
                    activity,
                    PICK_SERVER_REQUEST_CODE,
                    Intent(activity, ServerPickerActivity::class.java),
                    FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE
                )
            )
        }.build()

        /*
         * Set the package explicitly to the browser configured by the
         * application if any.
         * NB: The default browser on the device is used:
         * - If getCustomTabBrowser() returns null
         * - Or if the specified browser is not installed
         */
        val customTabBrowser = SalesforceSDKManager.getInstance().customTabBrowser
        if (doesBrowserExist(customTabBrowser)) {
            customTabsIntent.intent.setPackage(customTabBrowser)
        }

        runCatching {
            customTabsIntent.launchUrl(
                activity,
                parse(uri.toString())
            )
            // Making the webview blank gives custom tab login a cleaner appearance.
            clearView()
        }.onFailure { throwable ->
            e(TAG, "Unable to launch Advanced Authentication, Chrome browser not installed.", throwable)
            makeText(context, "To log in, install Chrome.", LENGTH_LONG).show()
            callback.finish(null)

            /*
             * Launch server picker again to prevent this error from happening in an infinite loop.  It is impossible to
             * break out of this loop without uninstalling the app.
             *
             * Clear top to prevent multiple server pickers form being on the stack if the user hits back multiple times
             * before selecting a different server.
             */
            val serverPickerIntent = Intent(activity, ServerPickerActivity::class.java)
            serverPickerIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(serverPickerIntent)
        }
    }

    private fun doesBrowserExist(customTabBrowser: String?) =
        when (customTabBrowser) {
            null -> false
            else -> runCatching {
                activity?.packageManager?.getApplicationInfo(customTabBrowser, 0) != null
            }.onFailure { throwable ->
                w(TAG, "$customTabBrowser does not exist on this device", throwable)
            }.getOrDefault(false)
        }

    protected open val oAuthClientId: String
        get() = loginOptions.oauthClientId

    @Suppress("MemberVisibilityCanBePrivate")
    protected open fun getAuthorizationUrl(
        useWebServerAuthentication: Boolean,
        useHybridAuthentication: Boolean
    ): URI {

        // Reset log in state,
        // - Salesforce Identity UI Bridge API log in, such as QR code log in.
        resetFrontDoorBridgeUrl()

        val loginOptions = loginOptions
        val oAuthClientId = oAuthClientId
        val authorizationDisplayType = authorizationDisplayType

        val jwtFlow = !isEmpty(loginOptions.jwt)
        val additionalParams = when {
            jwtFlow -> null
            else -> loginOptions.additionalParameters
        }

        // NB code verifier / code challenge are only used when useWebServerAuthentication is true
        val codeVerifier = getRandom128ByteKey().also { codeVerifier = it }
        val codeChallenge = getSHA256Hash(codeVerifier)
        val authorizationUrl = getAuthorizationUrl(
            useWebServerAuthentication,
            useHybridAuthentication,
            URI(loginOptions.loginUrl),
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

    /**
     * Override to replace the default login web view's display parameter with a
     * custom display parameter. Override by either subclassing this class or
     * adding
     * "<string name="sf__oauth_display_type">desiredDisplayParam</string>"
     * to the app's resources so it overrides the default value in the
     * Salesforce Mobile SDK library.
     *
     * See the OAuth docs for the complete list of valid values
     */
    @Suppress("MemberVisibilityCanBePrivate")
    protected open val authorizationDisplayType
        get() = context.getString(oauth_display_type)

    /** Override to customize the login url */
    protected open val loginUrl: String?
        get() = SalesforceSDKManager.getInstance().loginServerManager.selectedLoginServer?.url?.run {
            trim { it <= ' ' }
        }

    /**
     * A web view client which intercepts the redirect to the OAuth callback
     * URL.  That redirect marks the end of the user facing portion of the
     * authentication flow.
     */
    protected open inner class AuthWebViewClient : WebViewClient() {

        override fun onPageFinished(
            view: WebView,
            url: String
        ) {
            // Hide spinner / show web view
            val parentView = view.parent as? RelativeLayout
            parentView?.run {
                findViewById<ProgressBar>(
                    sf__loading_spinner
                )?.visibility = INVISIBLE
            }
            view.visibility = VISIBLE

            // Remove the native login buttons (biometric, IDP) once on the allow/deny screen
            if (url.contains("frontdoor.jsp")) {
                parentView?.run {
                    findViewById<Button>(
                        sf__idp_login_button
                    )?.visibility = INVISIBLE
                    findViewById<Button>(
                        sf__bio_login_button
                    )?.visibility = INVISIBLE

                }
            }
            EventsObservable.get().notifyEvent(AuthWebViewPageFinished, url)
            super.onPageFinished(view, url)
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            val activity = activity
            val instance = SalesforceSDKManager.getInstance()
            val loginOptions = loginOptions
            val loginUrl = loginUrl

            // The login web view's embedded button has sent the signal to show the biometric prompt
            if (request.url.toString() == BIOMETRIC_PROMPT) {
                instance.biometricAuthenticationManager?.run {
                    if (hasBiometricOptedIn() && hasBiometricOptedIn()) {
                        (activity as? LoginActivity)?.presentBiometric()
                    }
                }
                return true
            }

            // Check if user entered a custom domain
            val host = request.url.host
            val customDomainPattern = instance.customDomainInferencePattern
            if (host != null && loginUrl?.contains(host) != true && customDomainPattern != null && customDomainPattern.matcher(request.url.toString()).find()) {
                runCatching {
                    val baseUrl = "https://${request.url.host}"
                    val serverManager = instance.loginServerManager

                    // Check if the URL is already in the server list
                    when (val loginServer = serverManager.getLoginServerFromURL(baseUrl)) {
                        null ->
                            // Add also sets as selected
                            serverManager.addCustomLoginServer("Custom Domain", baseUrl)

                        else ->
                            serverManager.selectedLoginServer = loginServer
                    }

                    // Set title to the new login URL
                    loginOptions.loginUrl = baseUrl

                    // Check the configuration for the selected login server
                    instance.fetchAuthenticationConfiguration {
                        onAuthConfigFetched()
                    }
                }.onFailure { throwable ->
                    e(TAG, "Unable to retrieve auth config.", throwable)
                }
            }

            val formattedUrl = request.url.toString().replace("///", "/").lowercase()
            val callbackUrl = loginOptions.oauthCallbackUrl.replace("///", "/").lowercase()
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
                            instance.useWebServerAuthentication && !overrideWithUserAgentFlow ->
                                onWebServerFlowComplete(params["code"])

                            else ->
                                onAuthFlowComplete(TokenEndpointResponse(params))
                        }
                    }
                }
            }
            return authFlowFinished
        }

        override fun onReceivedSslError(
            view: WebView,
            handler: SslErrorHandler,
            error: SslError
        ) {
            val primErrorStringId = when (error.primaryError) {
                SSL_EXPIRED -> sf__ssl_expired
                SSL_IDMISMATCH -> sf__ssl_id_mismatch
                SSL_NOTYETVALID -> sf__ssl_not_yet_valid
                SSL_UNTRUSTED -> sf__ssl_untrusted
                else -> sf__ssl_unknown_error
            }

            // Build the text message
            val text = context.getString(
                sf__ssl_error,
                context.getString(primErrorStringId)
            )
            e(TAG, "Received SSL error for server: $text")

            // Show the toast
            makeText(context, text, LENGTH_LONG).show()
            handler.cancel()
        }

        override fun onReceivedClientCertRequest(
            view: WebView,
            request: ClientCertRequest
        ) {
            d(TAG, "Received client certificate request from server")
            request.proceed(key, certChain)
        }

        @Suppress("MemberVisibilityCanBePrivate")
        fun onAuthConfigFetched() {
            if (SalesforceSDKManager.getInstance().isBrowserLoginEnabled) {
                // This load will trigger advanced auth and do all necessary setup
                doLoadPage()
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
            // - Salesforce Identity UI Bridge API log in, such as QR code log in.
            resetFrontDoorBridgeUrl()

            FinishAuthTask().execute(tr, nativeLogin)
        }
    }

    internal fun onWebServerFlowComplete(code: String?) =
        CoroutineScope(IO).launch {
            doCodeExchangeEndpoint(code)
        }

    private suspend fun doCodeExchangeEndpoint(
        code: String?
    ) = withContext(IO) {
        var tokenResponse: TokenEndpointResponse? = null
        runCatching {
            tokenResponse = exchangeCode(
                DEFAULT,
                create(loginOptions.loginUrl),
                loginOptions.oauthClientId,
                code,
                frontDoorBridgeCodeVerifier ?: codeVerifier,
                loginOptions.oauthCallbackUrl
            )
        }.onFailure { throwable ->
            e(TAG, "Exception occurred while making token request", throwable)
            onAuthFlowError("Token Request Error", throwable.message, throwable)
        }
        onAuthFlowComplete(tokenResponse)
    }

    private inner class SwapJWTForAccessTokenTask : BaseFinishAuthFlowTask<LoginOptions?>() {

        override fun doInBackground(
            request: LoginOptions?
        ) = performRequest(loginOptions)

        override fun performRequest(
            param: LoginOptions?
        ) = runCatching {
            swapJWTForTokens(
                DEFAULT,
                URI(param?.loginUrl),
                param?.jwt
            )
        }.onFailure { throwable ->
            backgroundException = throwable
        }.getOrNull()

        override fun onPostExecute(tr: TokenEndpointResponse?, nativeLogin: Boolean) {
            if (backgroundException != null) {
                handleJWTError()
                loginOptions.jwt = null
                return
            }

            when {
                tr?.authToken != null -> {
                    loginOptions.jwt = tr.authToken
                    doLoadPage()
                }

                else -> {
                    doLoadPage()
                    handleJWTError()
                }
            }
            loginOptions.jwt = null
        }

        private fun handleJWTError() =
            onAuthFlowError(
                context.getString(sf__generic_authentication_error_title),
                context.getString(sf__jwt_authentication_error),
                backgroundException
            )
    }

    /**
     * A abstract class for objects that, provided an authentication response,
     * can finish the authentication flow, publish progress and handle errors.
     *
     * By overriding the background work and post-work methods, it is possible
     * to use this class to model other types of background work.
     *
     * The parameter type generic parameter models the type provided by the
     * authentication flow, such as a token endpoint response.  It can also
     * model generic types for implementations that handle tasks other than
     * authentication. The actual type is provided by the implementation.
     *
     * This was once Android's now deprecated async task.  It has been severed
     * from inheriting from deprecated classes.  The implementation now uses
     * Kotlin Coroutines for concurrency needs.  The overall class and method
     * structure remains very similar to that provided by earlier versions, for
     * compatibility and ease of adoption.
     */
    protected abstract inner class BaseFinishAuthFlowTask<Parameter> {

        /**
         * Finishes the authentication flow.
         * @param request The authentication response
         */
        internal suspend fun execute(request: Parameter?, nativeLogin: Boolean = false) = withContext(IO) {
            onPostExecute(doInBackground(request), nativeLogin)
        }

        /** The exception that occurred during background work, if applicable */
        protected var backgroundException: Throwable? = null

        protected var id: IdServiceResponse? = null

        /**
         * Indicates if authentication is blocked for the current user due to
         * the block Salesforce integration user option.
         */
        protected var shouldBlockSalesforceIntegrationUser = false

        open fun doInBackground(request: Parameter?) =
            runCatching {
                publishProgress(true)
                performRequest(request)
            }.onFailure { throwable ->
                handleException(throwable)
            }.getOrNull()

        @Suppress("unused")
        @Throws(Exception::class)
        protected abstract fun performRequest(
            param: Parameter?
        ): TokenEndpointResponse?

        open fun onPostExecute(tr: TokenEndpointResponse?, nativeLogin: Boolean) {
            val instance = SalesforceSDKManager.getInstance()

            // Failure cases
            if (shouldBlockSalesforceIntegrationUser) {
                /*
                 * Salesforce integration users are prohibited from successfully
                 * completing authentication. This alleviates the Restricted
                 * Product Approval requirement on Salesforce Integration add-on
                 * SKUs and conforms to Legal and Product Strategy requirements
                 */
                w(TAG, "Salesforce integration users are prohibited from successfully authenticating.")
                onAuthFlowError( // Issue the generic authentication error
                    context.getString(sf__generic_authentication_error_title),
                    context.getString(sf__generic_authentication_error), backgroundException
                )
                callback.finish(null)
                return
            }

            if (backgroundException != null) {
                w(TAG, "Exception thrown while retrieving token response", backgroundException)
                onAuthFlowError(
                    context.getString(sf__generic_authentication_error_title),
                    context.getString(sf__generic_authentication_error),
                    backgroundException
                )
                callback.finish(null)
                return
            }

            id?.let { id ->
                val mustBeManagedApp = id.customPermissions?.optBoolean(MUST_BE_MANAGED_APP_PERM)
                if (mustBeManagedApp == true && !getRuntimeConfig(context).isManagedApp) {
                    onAuthFlowError(
                        context.getString(sf__generic_authentication_error_title),
                        context.getString(sf__managed_app_error), backgroundException
                    )
                    callback.finish(null)
                    return
                }
            }

            val account = UserAccountBuilder.getInstance()
                .populateFromTokenEndpointResponse(tr)
                .populateFromIdServiceResponse(id)
                .nativeLogin(nativeLogin)
                .accountName(buildAccountName(id?.username, tr?.instanceUrl))
                .loginServer(loginOptions.loginUrl)
                .clientId(loginOptions.oauthClientId)
                .build()

            accountOptions = account.toBundle()

            account.downloadProfilePhoto()

            // Set additional administrator prefs if they exist
            id?.customAttributes?.let { customAttributes ->
                instance.adminSettingsManager?.setPrefs(customAttributes, account)
            }

            id?.customPermissions?.let { customPermissions ->
                instance.adminPermsManager?.setPrefs(customPermissions, account)
            }

            instance.userAccountManager.authenticatedUsers?.let { existingUsers ->
                // Check if the user already exists
                if (existingUsers.contains(account)) {
                    val duplicateUserAccount = existingUsers.removeAt(existingUsers.indexOf(account))
                    clearCaches()
                    UserAccountManager.getInstance().clearCachedCurrentUser()

                    // Revoke existing refresh token
                    if (account.refreshToken != duplicateUserAccount.refreshToken) {
                        runCatching {
                            URI(duplicateUserAccount.instanceServer)
                        }.onFailure { throwable ->
                            w(TAG, "Revoking token failed", throwable)
                        }.onSuccess { uri ->
                            // The user authenticated via webview again, unlock the app.
                            if (isBiometricAuthenticationEnabled(duplicateUserAccount)) {
                                (SalesforceSDKManager.getInstance().biometricAuthenticationManager
                                        as? BiometricAuthenticationManager)?.onUnlock()
                            }
                            CoroutineScope(IO).launch {
                                revokeRefreshToken(
                                    DEFAULT,
                                    uri,
                                    duplicateUserAccount.refreshToken,
                                    OAuth2.LogoutReason.REFRESH_TOKEN_ROTATED,
                                )
                            }
                        }
                    }
                }

                // If this account has biometric authentication enabled remove any others that also have it
                if (id?.biometricAuth == true) {
                    existingUsers.forEach(Consumer { existingUser ->
                        if (isBiometricAuthenticationEnabled(existingUser)) {
                            activity?.runOnUiThread {
                                makeText(
                                    activity,
                                    activity.getString(
                                        sf__biometric_signout_user,
                                        existingUser.username
                                    ),
                                    LENGTH_LONG
                                ).show()
                            }
                            // This is an unexpected logout(s) because we only support one Bio Auth user.
                            instance.userAccountManager.signoutUser(
                                existingUser, activity, false, OAuth2.LogoutReason.UNEXPECTED
                            )
                        }
                    })
                }
            }

            // Save the user account
            addAccount(account)

            // Screen lock required by mobile policy
            if (id?.screenLockTimeout?.compareTo(0) == 1) {
                SalesforceSDKManager.getInstance().registerUsedAppFeature(FEATURE_SCREEN_LOCK)
                val timeoutInMills = (id?.screenLockTimeout ?: 0) * 1000 * 60
                (instance.screenLockManager as ScreenLockManager?)?.storeMobilePolicy(
                    account,
                    id?.screenLock ?: false,
                    timeoutInMills
                )
            }

            // Biometric authorization required by mobile policy
            if (id?.biometricAuth == true) {
                SalesforceSDKManager.getInstance().registerUsedAppFeature(FEATURE_BIOMETRIC_AUTH)
                val timeoutInMills = (id?.biometricAuthTimeout ?: 0) * 60 * 1000
                (instance.biometricAuthenticationManager as BiometricAuthenticationManager?)?.storeMobilePolicy(
                    account,
                    id?.biometricAuth ?: false,
                    timeoutInMills
                )
            }

            // All done
            callback.finish(account)
        }

        private fun publishProgress(
            @Suppress("SameParameterValue") value: Boolean?
        ) = onProgressUpdate(value)

        @Suppress("MemberVisibilityCanBePrivate")
        fun onProgressUpdate(@Suppress("UNUSED_PARAMETER") value: Boolean?) {
        }

        @Suppress("MemberVisibilityCanBePrivate", "unused")
        protected fun handleException(throwable: Throwable) {
            if (throwable.message != null) {
                w(TAG, "Exception thrown", throwable)
            }
            backgroundException = throwable
        }
    }

    /**
     * A background process that will call the identity service to get the info
     * needed from the Identity service and finally wrap up and create the account.
     */
    private inner class FinishAuthTask : BaseFinishAuthFlowTask<TokenEndpointResponse?>() {

        @Suppress("unused")
        override fun performRequest(param: TokenEndpointResponse?): TokenEndpointResponse? {
            runCatching {
                id = callIdentityService(
                    DEFAULT,
                    param?.idUrlWithInstance,
                    param?.authToken
                )

                // Request the authenticated user's information to determine if it is a Salesforce integration user.
                // This is a synchronous network request, so it must be performed here in the background stage.
                shouldBlockSalesforceIntegrationUser = SalesforceSDKManager.getInstance()
                    .shouldBlockSalesforceIntegrationUser && fetchIsSalesforceIntegrationUser(param)
            }.onFailure { throwable ->
                backgroundException = throwable
            }
            return param
        }
    }

    /**
     * Requests the user's information from the network and returns the user's
     * integration user state.
     *
     * @param tokenEndpointResponse The user's authentication token endpoint
     * response
     * @return Boolean true indicates the user is a Salesforce integration user.
     * False indicates otherwise.
     * @throws Exception Any exception that prevents returning the result
     */
    @Throws(Exception::class)
    private fun fetchIsSalesforceIntegrationUser(
        tokenEndpointResponse: TokenEndpointResponse?
    ): Boolean {
        val baseUrl = tokenEndpointResponse?.instanceUrl ?: loginUrl
        val userInfoEndpoint = "$baseUrl/services/oauth2/userinfo"
        val builder: Builder = Builder().url(userInfoEndpoint).get()
        addAuthorizationHeader(
            builder,
            tokenEndpointResponse?.authToken
        )
        val request = builder.build()

        val clientBuilder = DEFAULT.okHttpClient.newBuilder()
        clientBuilder.addNetworkInterceptor { chain: Interceptor.Chain ->
            val url = chain.request().url
            val interceptedRequestBuilder = chain.request().newBuilder()

            // if the url no longer matches we were redirected
            if (url.toString() != userInfoEndpoint && url.isSalesforceUrl()) {
                 addAuthorizationHeader(
                    interceptedRequestBuilder,
                    tokenEndpointResponse?.authToken,
                )
            }

            chain.proceed(interceptedRequestBuilder.build())
        }
        val response = clientBuilder.build().newCall(request).execute()
        val responseString = response.body?.string()
        return responseString != null && JSONObject(responseString).getBoolean("is_salesforce_integration_user")
    }

    private fun HttpUrl.isSalesforceUrl(): Boolean {
        // List from https://help.salesforce.com/s/articleView?language=en_US&id=sf.domain_name_url_formats.htm&type=5
        val salesforceHosts = listOf(".salesforce.com", ".force.com", ".sfdcopens.com", ".site.com", ".lightning.com",
            ".salesforce-sites.com", ".force-user-content.com", ".salesforce-experience.com", ".salesforce-scrt.com")
        return salesforceHosts.map { host.endsWith(it) }.any() { it }
    }

    private fun addAccount(account: UserAccount?) {
        val clientManager = ClientManager(
            context,
            SalesforceSDKManager.getInstance().accountType,
            loginOptions,
            SalesforceSDKManager.getInstance().shouldLogoutWhenTokenRevoked()
        )

        // New account
        val extras = clientManager.createNewAccount(account)

        /*
         * Registers for push notifications if setup by the app. This step needs
         * to happen after the account has been added by client manager, so that
         * the push service has all the account info it needs.
         */
        register(SalesforceSDKManager.getInstance().appContext, account)

        callback.onAccountAuthenticatorResult(extras)

        when {
            SalesforceSDKManager.getInstance().isTestRun -> logAddAccount(account)
            else -> CoroutineScope(IO).launch {
                logAddAccount(account)
            }
        }
    }

    /**
     * Log the addition of a new account.
     *
     * @param account The user account
     */
    private fun logAddAccount(account: UserAccount?) {
        val attributes = JSONObject()
        runCatching {
            val users = UserAccountManager.getInstance().authenticatedUsers
            attributes.put("numUsers", users?.size ?: 0)
            val servers = SalesforceSDKManager.getInstance().loginServerManager.loginServers
            attributes.put("numLoginServers", servers?.size ?: 0)
            servers?.let { serversUnwrapped ->
                val serversJson = JSONArray()
                for (server in serversUnwrapped) {
                    server?.let { serverUnwrapped ->
                        serversJson.put(serverUnwrapped.url)
                    }
                }
                attributes.put("loginServers", serversJson)
            }
            createAndStoreEventSync("addUser", account, TAG, attributes)
        }.onFailure { throwable ->
            e(TAG, "Exception thrown while creating JSON", throwable)
        }
    }

    /**
     * The name to be shown for account in Settings -> Accounts & Sync
     * @return name to be shown for account in Settings -> Accounts & Sync
     */
    protected open fun buildAccountName(
        username: String?,
        instanceServer: String?
    ) = String.format(
        "%s (%s) (%s)", username, instanceServer,
        SalesforceSDKManager.getInstance().applicationName
    )

    override fun alias(alias: String?) {
        runCatching {
            activity?.let { activity ->
                d(TAG, "Keychain alias callback received")
                alias?.let { alias ->
                    certChain = getCertificateChain(activity, alias)
                    key = getPrivateKey(activity, alias)
                }
                activity.runOnUiThread { loadLoginPage() }
            }
        }.onFailure { throwable ->
            e(TAG, "Exception thrown while retrieving X.509 certificate", throwable)
        }
    }

    /**
     * Automatically log in using the provided UI Bridge API parameters.
     * @param frontdoorBridgeUrl The UI Bridge API front door bridge API
     * @param pkceCodeVerifier The PKCE code verifier
     */
    fun loginWithFrontdoorBridgeUrl(
        frontdoorBridgeUrl: String,
        pkceCodeVerifier: String?
    ) {
        isUsingFrontDoorBridge = true

        val uri = URI(frontdoorBridgeUrl)
        loginOptions.loginUrl = "${uri.scheme}://${uri.host}"
        frontDoorBridgeCodeVerifier = pkceCodeVerifier

        webView?.loadUrl(frontdoorBridgeUrl)
    }

    /**
     * Resets all state related to Salesforce Identity API UI Bridge front door bridge URL log in to
     * its default inactive state.
     */
    private fun resetFrontDoorBridgeUrl() {
        isUsingFrontDoorBridge = false
        frontDoorBridgeCodeVerifier = null
    }

    companion object {

        /**
         * Set a custom permission on the connected application with that name
         * for the application to be restricted to managed devices
         */
        const val MUST_BE_MANAGED_APP_PERM = "must_be_managed_app"

        const val AUTHENTICATION_FAILED_INTENT = "com.salesforce.auth.intent.AUTHENTICATION_ERROR"

        const val HTTP_ERROR_RESPONSE_CODE_INTENT = "com.salesforce.auth.intent.HTTP_RESPONSE_CODE"

        const val RESPONSE_ERROR_INTENT = "com.salesforce.auth.intent.RESPONSE_ERROR"

        const val RESPONSE_ERROR_DESCRIPTION_INTENT = "com.salesforce.auth.intent.RESPONSE_ERROR_DESCRIPTION"

        const val BIOMETRIC_PROMPT = "mobilesdk://biometric/authentication/prompt"

        private const val TAG = "OAuthWebViewHelper"

        private const val ACCOUNT_OPTIONS = "accountOptions"

        private const val PROMPT_LOGIN = "&prompt=login"
    }
}
