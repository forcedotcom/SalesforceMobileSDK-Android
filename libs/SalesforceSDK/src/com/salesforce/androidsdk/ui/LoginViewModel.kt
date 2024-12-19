package com.salesforce.androidsdk.ui

import android.view.View
import android.webkit.WebChromeClient
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.salesforce.androidsdk.R.string.oauth_display_type
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions

open class LoginViewModel(
    var loginOptions: LoginOptions,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    // TODO: create webivew?

    /** Override to customize the login url */
    open val selectedServer: String?
        get() = SalesforceSDKManager.getInstance().loginServerManager.selectedLoginServer?.url?.run {
            trim { it <= ' ' }
        }
//    open val selectedServer: String?
//        get() = "login.salesforce.com"
    open val loginUrl: MutableLiveData<String> by lazy {
        MutableLiveData<String>(tempSelectedServer.value)
    }

    internal open var authorizationDisplayType = SalesforceSDKManager.getInstance().appContext.getString(oauth_display_type)
    internal open val oAuthClientId: String
        get() = loginOptions.oauthClientId

    internal var dynamicBackgroundColor = mutableStateOf(Color.White)
    internal var dynamicHeaderTextColor = derivedStateOf { if (dynamicBackgroundColor.value.luminance() > 0.5) Color.Black else Color.White }
//    internal var dynamicHeaderTextColor = derivedStateOf { Color.White }

//    internal var selectedSever = mutableStateOf("https://msdk-enhanced-dev-ed.my.site.com/headless/login")
    internal val tempSelectedServer = mutableStateOf("login.salesforce.com")
    internal var showBottomSheet = mutableStateOf(false)
    internal var loading = mutableStateOf(false)


// TODO: get bio auth mgr and show button if necessary.  Also do the same for IDP
//    private bioAuthMgr =


    // OAuthWebviewHelperEvents?


    // From LoginActivity

//    /**
//     * Callbacks from the OAuth web view helper
//     */
//    fun loadingLoginPage(loginUrl: String) {
//        this@LoginActivity.runOnUiThread { supportActionBar?.title = loginUrl }
//    }
//
//    /**
//     * Callbacks from the OAuth web view helper
//     */
//    fun onAccountAuthenticatorResult(authResult: Bundle) {
//        accountAuthenticatorResult = authResult
//    }
//
//    override fun finish(userAccount: UserAccount?) {
//        TODO("Not yet implemented")
//    }

    /**
     * Called when the "clear cookies" button is clicked to clear cookies and
     * reload the login page.
     * @param v The view that was clicked
     */
    open fun onClearCookiesClick(v: View?) {
//        webviewHelper?.clearCookies()
//        webviewHelper?.loadLoginPage()
    }

    /**
     * Called when the IDP login button is clicked.
     *
     * @param v IDP login button
     */
    open fun onIDPLoginClick(v: View?) {
//        SalesforceSDKManager.getInstance().spManager?.kickOffSPInitiatedLoginFlow(
//            this,
//            SPStatusCallback()
//        )
    }

    /**
     * Called when "pick server" button is clicked to start the server picker
     * activity.
     * @param v The pick server button
     */
    open fun onPickServerClick(v: View?) {
//        Intent(this, ServerPickerActivity::class.java).also { intent ->
//            startActivityForResult(
//                intent,
//                PICK_SERVER_REQUEST_CODE
//            )
//        }
    }

//    open fun onBioAuthClick(view: View?) = presentBiometric()

//    inner class AuthConfigReceiver : BroadcastReceiver() {
//        override fun onReceive(context: Context, intent: Intent?) {
//            if (intent?.action == AUTH_CONFIG_COMPLETE_INTENT_ACTION) {
//                webviewHelper?.loadLoginPage()
//            }
//        }
//    }



//    internal fun completeAuthFlow(intent: Intent) {
//        val params = parse(intent.data)
//        params["error"]?.let { error ->
//            val errorDesc = params["error_description"]
//            webviewHelper?.onAuthFlowError(error, errorDesc, null)
//        } ?: webviewHelper?.onWebServerFlowComplete(params["code"])
//    }
//
//    /**
//     * Called when the "reload" button is clicked to reloads the login page.
//     * @param v The reload button
//     */
//    internal fun onReloadClick(@Suppress("UNUSED_PARAMETER") v: View?) {
//        webviewHelper?.loadLoginPage()
//    }

    // Region Biometric Authentication

//    internal fun presentBiometric() {
//        val biometricPrompt = biometricPrompt
//        val biometricManager = BiometricManager.from(this)
//        when (biometricManager.canAuthenticate(authenticators)) {
//            BIOMETRIC_ERROR_NO_HARDWARE, BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED, BIOMETRIC_ERROR_UNSUPPORTED, BIOMETRIC_STATUS_UNKNOWN -> {
//                // This should never happen
//                val error = getString(sf__screen_lock_error)
//                e(TAG, "Biometric manager cannot authenticate. $error")
//            }
//
//            BIOMETRIC_ERROR_HW_UNAVAILABLE, BIOMETRIC_ERROR_NONE_ENROLLED ->
//                biometricAuthenticationButton?.let { biometricAuthenticationButton ->
//                    /*
//                     * Prompts the user to setup OS screen lock and biometric
//                     * TODO: Remove when min API > 29
//                     */
//                    when {
//                        SDK_INT >= R -> biometricAuthenticationButton.setOnClickListener {
//                            startActivityForResult(
//                                Intent(
//                                    ACTION_BIOMETRIC_ENROLL
//                                ).apply {
//                                    putExtra(
//                                        EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
//                                        authenticators
//                                    )
//                                },
//                                SETUP_REQUEST_CODE
//                            )
//                        }
//
//                        else -> biometricAuthenticationButton.setOnClickListener {
//                            startActivityForResult(
//                                Intent(ACTION_SET_NEW_PASSWORD),
//                                SETUP_REQUEST_CODE
//                            )
//                        }
//                    }
//                    biometricAuthenticationButton.text = getString(sf__setup_biometric_unlock)
//                }
//
//            BIOMETRIC_SUCCESS -> biometricPrompt.authenticate(promptInfo)
//        }
//    }
//
//    private val biometricPrompt: BiometricPrompt
//        get() = BiometricPrompt(
//            this,
//            ContextCompat.getMainExecutor(this),
//            object : AuthenticationCallback() {
//
//                override fun onAuthenticationSucceeded(result: AuthenticationResult) {
//                    super.onAuthenticationSucceeded(result)
//                    (SalesforceSDKManager.getInstance().biometricAuthenticationManager
//                            as? BiometricAuthenticationManager?)?.run {
//                        onUnlock()
//                    }
//
//                    CoroutineScope(IO).launch {
//                        doTokenRefresh(this@LoginActivity)
//                    }
//                }
//            }
//        )
//
//    private fun doTokenRefresh(activity: LoginActivity) {
//        SalesforceSDKManager.getInstance().clientManager.getRestClient(
//            activity
//        ) { client ->
//            runCatching {
//                client.oAuthRefreshInterceptor.refreshAccessToken()
//            }.onFailure { e ->
//                e(TAG, "Error encountered while unlocking.", e)
//            }
//            activity.finish()
//        }
//    }
//
//    private val authenticators
//        get() = // TODO: Remove when min API > 29.
//            when {
//                SDK_INT >= R -> BIOMETRIC_STRONG or DEVICE_CREDENTIAL
//                else -> BIOMETRIC_WEAK or DEVICE_CREDENTIAL
//            }
//
//    private val promptInfo: PromptInfo
//        get() {
//            var hasFaceUnlock = false
//            if (SDK_INT >= Q) {
//                hasFaceUnlock = packageManager.hasSystemFeature(
//                    FEATURE_FACE
//                ) || packageManager.hasSystemFeature(
//                    FEATURE_IRIS
//                )
//            }
//            val subtitle = SalesforceSDKManager.getInstance().userAccountManager.currentUser.username
//
//            return PromptInfo.Builder()
//                .setTitle(resources.getString(sf__biometric_opt_in_title))
//                .setSubtitle(subtitle)
//                .setAllowedAuthenticators(authenticators)
//                .setConfirmationRequired(hasFaceUnlock)
//                .build()
//        }

    // endregion


    // from OAuthWebviewHelper

//    open fun clearCookies() =
//        CookieManager.getInstance().removeAllCookies(null)
//    @Suppress("MemberVisibilityCanBePrivate")
//    protected open fun getAuthorizationUrl(
//        useWebServerAuthentication: Boolean,
//        useHybridAuthentication: Boolean
//    ): URI {
//
//        // Reset log in state,
//        // - Salesforce Identity UI Bridge API log in, such as QR code login.
//        resetFrontDoorBridgeUrl()
//
//        val loginOptions = loginOptions
//        val oAuthClientId = oAuthClientId
//        val authorizationDisplayType = authorizationDisplayType
//
//        val jwtFlow = !isEmpty(loginOptions.jwt)
//        val additionalParams = when {
//            jwtFlow -> null
//            else -> loginOptions.additionalParameters
//        }
//
//        // NB code verifier / code challenge are only used when useWebServerAuthentication is true
//        val codeVerifier = getRandom128ByteKey().also { codeVerifier = it }
//        val codeChallenge = getSHA256Hash(codeVerifier)
//        val authorizationUrl = OAuth2.getAuthorizationUrl(
//            useWebServerAuthentication,
//            useHybridAuthentication,
//            URI(loginOptions.loginUrl),
//            oAuthClientId,
//            loginOptions.oauthCallbackUrl,
//            loginOptions.oauthScopes,
//            authorizationDisplayType,
//            codeChallenge,
//            additionalParams
//        )
//
//        return when {
//            jwtFlow -> getFrontdoorUrl(
//                authorizationUrl,
//                loginOptions.jwt,
//                loginOptions.loginUrl,
//                loginOptions.additionalParameters
//            )
//
//            else -> authorizationUrl
//        }
//    }

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

    /**
     * A factory method for the web Chrome client. This can be overridden as
     * needed
     *
     * TODO: deprecate and rename this to `makeBrowserClient`? (since it is not chrome specific)
     */
    @Suppress("MemberVisibilityCanBePrivate")
    protected open fun makeWebChromeClient() = WebChromeClient()

//    /**
//     * A factory method for the web view client. This can be overridden as
//     * needed.
//     */
//    @Suppress("MemberVisibilityCanBePrivate")
//    protected open fun makeWebViewClient() = AuthWebViewClient()
//
//
//    /**
//     * Called when the user facing part of the authentication flow completed
//     * successfully. The last step is to call the identity service to get the
//     * username.
//     */
//    open fun onAuthFlowComplete(tr: TokenEndpointResponse?, nativeLogin: Boolean = false) {
//        CoroutineScope(IO).launch {
//
//            // Reset log in state,
//            // - Salesforce Identity UI Bridge API log in, such as QR code login.
//            resetFrontDoorBridgeUrl()
//
//            FinishAuthTask().execute(tr, nativeLogin)
//        }
//    }
//
//    /**
//     * A web view client which intercepts the redirect to the OAuth callback
//     * URL.  That redirect marks the end of the user facing portion of the
//     * authentication flow.
//     */
//    protected open inner class AuthWebViewClient : WebViewClient() {
//
//        override fun onPageFinished(
//            view: WebView,
//            url: String
//        ) {
//            // Hide spinner / show web view
//            val parentView = view.parent as? RelativeLayout
//            parentView?.run {
//                findViewById<ProgressBar>(
//                    sf__loading_spinner
//                )?.visibility = INVISIBLE
//            }
//            view.visibility = VISIBLE
//
//            // Remove the native login buttons (biometric, IDP) once on the allow/deny screen
//            if (url.contains("frontdoor.jsp")) {
//                parentView?.run {
//                    findViewById<Button>(
//                        sf__idp_login_button
//                    )?.visibility = INVISIBLE
//                    findViewById<Button>(
//                        sf__bio_login_button
//                    )?.visibility = INVISIBLE
//
//                }
//            }
//            EventsObservable.get().notifyEvent(AuthWebViewPageFinished, url)
//            super.onPageFinished(view, url)
//        }
//
//        override fun shouldOverrideUrlLoading(
//            view: WebView,
//            request: WebResourceRequest
//        ): Boolean {
////            val activity = activity
////            val instance = SalesforceSDKManager.getInstance()
////            val loginOptions = loginOptions
////            val loginUrl = loginUrl
//
//            // The login web view's embedded button has sent the signal to show the biometric prompt
//            if (request.url.toString() == BIOMETRIC_PROMPT) {
//                SalesforceSDKManager.getInstance().biometricAuthenticationManager?.run {
//                    if (hasBiometricOptedIn() && hasBiometricOptedIn()) {
//                        (activity as? LoginActivity)?.presentBiometric()
//                    }
//                }
//                return true
//            }
//
//            // Check if user entered a custom domain
//            val host = request.url.host
//            val customDomainPattern = instance.customDomainInferencePattern
//            if (host != null && loginUrl?.contains(host) != true && customDomainPattern != null && customDomainPattern.matcher(request.url.toString()).find()) {
//                runCatching {
//                    val baseUrl = "https://${request.url.host}"
//                    val serverManager = instance.loginServerManager
//
//                    // Check if the URL is already in the server list
//                    when (val loginServer = serverManager.getLoginServerFromURL(baseUrl)) {
//                        null ->
//                            // Add also sets as selected
//                            serverManager.addCustomLoginServer("Custom Domain", baseUrl)
//
//                        else ->
//                            serverManager.selectedLoginServer = loginServer
//                    }
//
//                    // Set title to the new login URL
//                    loginOptions.loginUrl = baseUrl
//
//                    // Check the configuration for the selected login server
//                    instance.fetchAuthenticationConfiguration {
//                        onAuthConfigFetched()
//                    }
//                }.onFailure { throwable ->
//                    e(OAuthWebviewHelper.TAG, "Unable to retrieve auth config.", throwable)
//                }
//            }
//
//            val formattedUrl = request.url.toString().replace("///", "/").lowercase()
//            val callbackUrl = loginOptions.oauthCallbackUrl.replace("///", "/").lowercase()
//            val authFlowFinished = formattedUrl.startsWith(callbackUrl)
//
//            if (authFlowFinished) {
//                val params = UriFragmentParser.parse(request.url)
//                val error = params["error"]
//                // Did we fail?
//                when {
//                    error != null -> onAuthFlowError(
//                        error,
//                        params["error_description"],
//                        null
//                    )
//
//                    else -> {
//                        // Determine if presence of override parameters require the user agent flow.
//                        val overrideWithUserAgentFlow = isUsingFrontDoorBridge && frontDoorBridgeCodeVerifier == null
//                        when {
//                            instance.useWebServerAuthentication && !overrideWithUserAgentFlow ->
//                                onWebServerFlowComplete(params["code"])
//
//                            else ->
//                                onAuthFlowComplete(TokenEndpointResponse(params))
//                        }
//                    }
//                }
//            }
//            return authFlowFinished
//        }
//
//        override fun onReceivedSslError(
//            view: WebView,
//            handler: SslErrorHandler,
//            error: SslError
//        ) {
//            val primErrorStringId = when (error.primaryError) {
//                SSL_EXPIRED -> sf__ssl_expired
//                SSL_IDMISMATCH -> sf__ssl_id_mismatch
//                SSL_NOTYETVALID -> sf__ssl_not_yet_valid
//                SSL_UNTRUSTED -> sf__ssl_untrusted
//                else -> sf__ssl_unknown_error
//            }
//
//            // Build the text message
//            val text = context.getString(
//                sf__ssl_error,
//                context.getString(primErrorStringId)
//            )
//            e(Companion.TAG, "Received SSL error for server: $text")
//
//            // Show the toast
//            makeText(context, text, LENGTH_LONG).show()
//            handler.cancel()
//        }
//
//        override fun onReceivedClientCertRequest(
//            view: WebView,
//            request: ClientCertRequest
//        ) {
//            d(Companion.TAG, "Received client certificate request from server")
//            request.proceed(key, certChain)
//        }
//
//        @Suppress("MemberVisibilityCanBePrivate")
//        fun onAuthConfigFetched() {
//            if (SalesforceSDKManager.getInstance().isBrowserLoginEnabled) {
//                // This load will trigger advanced auth and do all necessary setup
//                doLoadPage()
//            }
//        }
//    }

    // end of from OAuthWebviewHelper

    companion object {

        val Factory: ViewModelProvider.Factory = object  : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                // Get the Application object from extras
                val application = checkNotNull(extras[APPLICATION_KEY])
                // Create a SavedStateHandle for this ViewModel from extras
                val savedStateHandle = extras.createSavedStateHandle()

                // Determine login options for Salesforce Identity API UI Bridge front door URL use or choose defaults.
                val loginOptions = SalesforceSDKManager.getInstance().loginOptions
// TODO:  update this to account for frontdoor/QR Code login
//                    when {
//                    isUsingFrontDoorBridge -> salesforceSDKManager.loginOptions
//                    else -> fromBundleWithSafeLoginUrl(intent.extras)
//                }

                return LoginViewModel(loginOptions, savedStateHandle) as T
            }
        }

        private const val TAG = "LoginViewModel"
    }
}