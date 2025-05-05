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
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager.ERROR_CODE_CANCELED
import android.accounts.AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE
import android.annotation.SuppressLint
import android.app.Activity
import android.app.admin.DevicePolicyManager.ACTION_SET_NEW_PASSWORD
import android.content.Intent
import android.content.pm.PackageManager.FEATURE_FACE
import android.content.pm.PackageManager.FEATURE_IRIS
import android.graphics.Bitmap
import android.graphics.BitmapFactory.decodeResource
import android.net.http.SslError
import android.net.http.SslError.SSL_EXPIRED
import android.net.http.SslError.SSL_IDMISMATCH
import android.net.http.SslError.SSL_NOTYETVALID
import android.net.http.SslError.SSL_UNTRUSTED
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Build.VERSION_CODES.R
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Bundle
import android.provider.Settings.ACTION_BIOMETRIC_ENROLL
import android.provider.Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED
import android.security.KeyChain.choosePrivateKeyAlias
import android.security.KeyChain.getCertificateChain
import android.security.KeyChain.getPrivateKey
import android.view.Display.FLAG_SECURE
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_BACK
import android.view.ViewGroup
import android.webkit.ClientCertRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast.LENGTH_LONG
import android.widget.Toast.LENGTH_SHORT
import android.widget.Toast.makeText
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED
import androidx.biometric.BiometricManager.BIOMETRIC_STATUS_UNKNOWN
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.AuthenticationCallback
import androidx.biometric.BiometricPrompt.AuthenticationResult
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getMainExecutor
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.salesforce.androidsdk.R.color.sf__background
import com.salesforce.androidsdk.R.color.sf__background_dark
import com.salesforce.androidsdk.R.color.sf__primary_color
import com.salesforce.androidsdk.R.drawable.sf__action_back
import com.salesforce.androidsdk.R.string.sf__biometric_opt_in_title
import com.salesforce.androidsdk.R.string.sf__generic_authentication_error_title
import com.salesforce.androidsdk.R.string.sf__jwt_authentication_error
import com.salesforce.androidsdk.R.string.sf__login_with_biometric
import com.salesforce.androidsdk.R.string.sf__screen_lock_error
import com.salesforce.androidsdk.R.string.sf__setup_biometric_unlock
import com.salesforce.androidsdk.R.string.sf__ssl_error
import com.salesforce.androidsdk.R.string.sf__ssl_expired
import com.salesforce.androidsdk.R.string.sf__ssl_id_mismatch
import com.salesforce.androidsdk.R.string.sf__ssl_not_yet_valid
import com.salesforce.androidsdk.R.string.sf__ssl_unknown_error
import com.salesforce.androidsdk.R.string.sf__ssl_untrusted
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.Features.FEATURE_QR_CODE_LOGIN
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.app.SalesforceSDKManager.Theme.DARK
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.auth.OAuth2.OAuthFailedException
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse
import com.salesforce.androidsdk.auth.OAuth2.swapJWTForTokens
import com.salesforce.androidsdk.auth.idp.interfaces.SPManager.Status
import com.salesforce.androidsdk.auth.idp.interfaces.SPManager.StatusUpdateCallback
import com.salesforce.androidsdk.config.RuntimeConfig.ConfigKey.ManagedAppCertAlias
import com.salesforce.androidsdk.config.RuntimeConfig.ConfigKey.RequireCertAuth
import com.salesforce.androidsdk.config.RuntimeConfig.getRuntimeConfig
import com.salesforce.androidsdk.security.BiometricAuthenticationManager
import com.salesforce.androidsdk.ui.components.LoginView
import com.salesforce.androidsdk.util.EventsObservable
import com.salesforce.androidsdk.util.EventsObservable.EventType.AuthWebViewPageFinished
import com.salesforce.androidsdk.util.EventsObservable.EventType.LoginActivityCreateComplete
import com.salesforce.androidsdk.util.SalesforceSDKLogger.d
import com.salesforce.androidsdk.util.SalesforceSDKLogger.e
import com.salesforce.androidsdk.util.SalesforceSDKLogger.w
import com.salesforce.androidsdk.util.UriFragmentParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * Login activity authenticates a user. Authorization happens inside a web view.
 *
 * Once an authorization code is obtained, it is exchanged for access and
 * refresh tokens to create an account via the account manager which stores
 * them.
 */
open class LoginActivity : FragmentActivity() {
    // View Model
    protected open val viewModel: LoginViewModel
            by viewModels { SalesforceSDKManager.getInstance().loginViewModelFactory }

    // Webview and Clients
    protected open val webViewClient = AuthWebViewClient()
    protected open val webChromeClient = WebChromeClient()
    open val webView: WebView
        @SuppressLint("SetJavaScriptEnabled")
        get() = WebView(this.baseContext).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            webViewClient = this@LoginActivity.webViewClient
            webChromeClient = this@LoginActivity.webChromeClient
            setBackgroundColor(Color.Transparent.toArgb())
            settings.javaScriptEnabled = true
        }

    // Private variables
    private var wasBackgrounded = false
    private var accountAuthenticatorResponse: AccountAuthenticatorResponse? = null
    private var accountAuthenticatorResult: Bundle? = null
    private var newUserIntent = false
    private val sharedBrowserSession: Boolean
        get() = SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled && !newUserIntent

    // KeychainAliasCallback variables
    private var key: PrivateKey? = null
    private var certChain: Array<X509Certificate>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (viewModel.dynamicBackgroundTheme.value == DARK) {
            SalesforceSDKManager.getInstance().setViewNavigationVisibility(this)
        }

        /*
         * For Salesforce Identity API UI Bridge support, the overriding
         * frontdoor bridge URL to use in place of the default initial login URL
         * plus the optional web server flow code verifier accompanying the
         * frontdoor bridge URL.
         */
        viewModel.isUsingFrontDoorBridge = isFrontdoorBridgeUrlIntent(intent) || isQrCodeLoginUrlIntent(intent)
        val uiBridgeApiParameters = if (isQrCodeLoginUrlIntent(intent)) {
            uiBridgeApiParametersFromQrCodeLoginUrl(intent.data?.toString())
        } else intent.getStringExtra(EXTRA_KEY_FRONTDOOR_BRIDGE_URL)?.let { frontdoorBridgeUrl ->
            UiBridgeApiParameters(
                frontdoorBridgeUrl,
                intent.getStringExtra(EXTRA_KEY_PKCE_CODE_VERIFIER)
            )
        }

        // Don't let sharedBrowserSession org setting stop a new user from logging in.
        if (intent.extras?.getBoolean(NEW_USER) == true) {
            newUserIntent = true
        }

        accountAuthenticatorResponse = intent.getParcelableExtra<AccountAuthenticatorResponse?>(
            KEY_ACCOUNT_AUTHENTICATOR_RESPONSE
        )?.apply {
            onRequestContinued()
        }

        // Protect against screenshots
        if (!SalesforceSDKManager.getInstance().isDebugBuild) {
            window.setFlags(FLAG_SECURE, FLAG_SECURE)
        }

        // Set content
        setContent {
            MaterialTheme(colorScheme = SalesforceSDKManager.getInstance().colorScheme()) {
                LoginView()
            }
        }

        // Present Biometric Prompt if necessary.
        val biometricAuthenticationManager =
            SalesforceSDKManager.getInstance().biometricAuthenticationManager as? BiometricAuthenticationManager
        if (biometricAuthenticationManager?.locked == true
            && biometricAuthenticationManager.hasBiometricOptedIn()
            && intent.extras?.getBoolean(BiometricAuthenticationManager.SHOW_BIOMETRIC) != false
        ) {
            presentBiometric()
        }

        // Prompt user with the default login page or log in via other configurations such as using
        // a Salesforce Identity API UI Bridge front door URL.
        when {
            viewModel.isUsingFrontDoorBridge && uiBridgeApiParameters?.frontdoorBridgeUrl != null ->
                loginWithFrontdoorBridgeUrl(
                    uiBridgeApiParameters.frontdoorBridgeUrl,
                    uiBridgeApiParameters.pkceCodeVerifier
                )

            else -> certAuthOrLogin()
        }

        // Take control of the back logic if the device is locked.
        // TODO:  Remove SDK_INT check when min API > 33
        if (SDK_INT >= TIRAMISU && biometricAuthenticationManager?.locked == true) {
            onBackPressedDispatcher.addCallback { handleBackBehavior() }
        }

        val customTabLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            // Check if the user backed out of the custom tab.
            if (result.resultCode == Activity.RESULT_CANCELED) {
                if (viewModel.singleServerCustomTabActivity) {
                    finish()
                } else {
                    // Don't show server picker if we are re-authenticating with cookie.
                    clearWebView(showServerPicker = !sharedBrowserSession)
                }
            }
        }

        // Take action on server change.
        viewModel.selectedServer.observe(this) {
            if (viewModel.singleServerCustomTabActivity) {
                // Skip fetching authorization and show custom tab immediately.
                viewModel.reloadWebView()
                viewModel.loginUrl.value?.let { url ->
                    loadLoginPageInCustomTab(url, customTabLauncher)
                }
            } else {
                with(SalesforceSDKManager.getInstance()) {
                    if (useWebServerAuthentication) {
                        // Fetch well known config and load in custom tab if required.
                        fetchAuthenticationConfiguration {
                            if (isBrowserLoginEnabled) {
                                viewModel.loginUrl.value?.let { url -> loadLoginPageInCustomTab(url, customTabLauncher) }
                            }
                        }
                    }
                }
            }
        }

        // Support magic links
        if (viewModel.jwt != null) {
            swapJWTForAccessToken()
        }

        // Let observers know onCreate is complete.
        EventsObservable.get().notifyEvent(LoginActivityCreateComplete, this)
    }

    override fun onResume() {
        super.onResume()
        wasBackgrounded = false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent) =
        // This allows sub classes to override the behavior by returning false
        when {
            fixBackButtonBehavior(keyCode) -> true
            else -> super.onKeyDown(keyCode, event)
        }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        // If the intent is a callback from Chrome, process it and do nothing else
        if (isCustomTabAuthFinishedCallback(intent)) {
            completeAdvAuthFlow(intent)
            return
        }
    }

    private fun clearWebView(showServerPicker: Boolean = true) {
        runOnUiThread {
            viewModel.loginUrl.value = ABOUT_BLANK
            if (showServerPicker) {
                viewModel.showServerPicker.value = true
            }
        }
    }

    // The code in this block was taken from the deprecated
    // AccountAuthenticatorActivity class to replicate its functionality per the
    // deprecation message
    override fun finish() {
        accountAuthenticatorResponse?.let { accountAuthenticatorResponse ->
            // Send the result bundle back if set, otherwise send an error
            accountAuthenticatorResult?.let { accountAuthenticatorResult ->
                accountAuthenticatorResponse.onResult(accountAuthenticatorResult)
            } ?: accountAuthenticatorResponse.onError(
                ERROR_CODE_CANCELED,
                "canceled"
            )
            this.accountAuthenticatorResponse = null
        }

        viewModel.authFinished.value = false
        super.finish()
    }


    @Deprecated("Deprecated in Java")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        /*
         * Present authentication again after the user has come back from
         * security settings to ensure they actually set up a secure lock screen
         * (pin/pattern/password/etc) instead of swipe or none.
         */
        if (requestCode == SETUP_REQUEST_CODE) {
            viewModel.biometricAuthenticationButtonText.intValue = sf__login_with_biometric
            presentBiometric()
        }
    }

    // region QR Code Login Via UI Bridge API Public Implementation

    /**
     * Automatically log in with a UI Bridge API front door bridge URL and PKCE
     * code verifier.
     *
     * This method is the intended entry point to Salesforce Mobile SDK when
     * using the Salesforce Identity API UI Bridge front door URL.  Usable,
     * default implementations of methods are provided for parsing the UI Bridge
     * parameters from the reference JSON and log in URLs used by the reference
     * QR Code Log In implementation.  However, the URL and JSON structure in
     * the reference implementation is not required.  An app may use a custom
     * structure so long as this entry point is used to log in with the front
     * door URL and optional PKCE code verifier.
     *
     * @param frontdoorBridgeUrl The UI Bridge API front door bridge URL
     * @param pkceCodeVerifier The optional PKCE code verifier, which is not
     * required for User Agent
     * Authorization Flow but is required for Web Server Authorization Flow
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun loginWithFrontdoorBridgeUrl(
        frontdoorBridgeUrl: String,
        pkceCodeVerifier: String?,
    ) = viewModel.loginWithFrontDoorBridgeUrl(frontdoorBridgeUrl, pkceCodeVerifier)

    /**
     * Automatically log in using a QR code login URL and Salesforce Identity
     * API UI Bridge.
     *
     * This method is the intended entry point for login using the reference
     * QR Code Login URL and JSON format.  It will parse the UI Bridge
     * parameters from the login QR code URL and call
     * [LoginActivity.loginWithFrontdoorBridgeUrl].  However, the URL and JSON
     * structure in the reference implementation is not required.  An app may
     * use a custom structure so long as UI Bridge front door URL and optional
     * PKCE code verifier are provided to
     * [LoginActivity.loginWithFrontdoorBridgeUrl].
     *
     * @param qrCodeLoginUrl The QR code login URL
     * @return Boolean true if a log in attempt is possible using the provided
     * QR code login URL, false otherwise
     */
    @Suppress("unused")
    fun loginWithFrontdoorBridgeUrlFromQrCode(qrCodeLoginUrl: String?): Boolean {
        SalesforceSDKManager.getInstance().registerUsedAppFeature(FEATURE_QR_CODE_LOGIN)
        return uiBridgeApiParametersFromQrCodeLoginUrl(
            qrCodeLoginUrl
        )?.let { uiBridgeApiParameters ->
            loginWithFrontdoorBridgeUrl(
                uiBridgeApiParameters.frontdoorBridgeUrl,
                uiBridgeApiParameters.pkceCodeVerifier
            )
            true
        } ?: false
    }

    // endregion

    // End of Public Functions

    protected open fun certAuthOrLogin() {
        when {
            shouldUseCertBasedAuth() -> {
                val managedAppAlias = getRuntimeConfig(this).getString(ManagedAppCertAlias)
                d(TAG, "Cert based login flow being triggered with alias: $managedAppAlias")
                choosePrivateKeyAlias(
                    this,
                    { alias ->
                        runCatching {
                            d(TAG, "Keychain alias callback received")
                            alias?.let { alias ->
                                certChain = getCertificateChain(this, alias)
                                key = getPrivateKey(this, alias)
                            }

                            viewModel.reloadWebView()
                        }.onFailure { throwable ->
                            e(TAG, "Exception thrown while retrieving X.509 certificate", throwable)
                        }
                    },
                    null,
                    null,
                    null,
                    -1,
                    managedAppAlias,
                )
            }

            else -> {
                d(TAG, "Web server or user agent login flow triggered")
            }
        }
    }

    /**
     * Indicates if the certificate based authentication flow should be used.
     *
     * @return True if certificate based authentication flow should be used and
     * false otherwise
     */
    protected open fun shouldUseCertBasedAuth(): Boolean =
        getRuntimeConfig(this).getBoolean(RequireCertAuth)

    /**
     * A fix for the back button's behavior.
     *
     * @return true if the fix was applied and false if the key code was not
     * handled
     */
    protected open fun fixBackButtonBehavior(keyCode: Int) =
        when (keyCode) {
            KEYCODE_BACK -> {
                handleBackBehavior()

                //  Do not execute back button behavior
                true
            }

            else -> false
        }

    /**
     * A callback when the user facing part of the authentication flow completed successfully.
     *
     * @param userAccount The newly created user account.
     */
    protected open fun onAuthFlowSuccess(userAccount: UserAccount) {
        // Create account and save result before switching to new user
        accountAuthenticatorResult = SalesforceSDKManager.getInstance().userAccountManager.createAccount(userAccount)

        setResult(Activity.RESULT_OK)
        finish()
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
    protected open fun onAuthFlowError(
        error: String,
        errorDesc: String?,
        e: Throwable? = null,
    ) {
        // Reset state from previous log in attempt.
        // - Salesforce Identity UI Bridge API log in, such as QR code login.
        viewModel.resetFrontDoorBridgeUrl()
        e(TAG, "$error: $errorDesc", e)

        // Broadcast a notification that the authentication flow failed
        SalesforceSDKManager.getInstance().appContext.sendBroadcast(
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
            }
        )

        viewModel.clearCookies()
        viewModel.resetFrontDoorBridgeUrl()
        // Displays the error in a toast, clears cookies and reloads the login page
        runOnUiThread {
            makeText(this, "$error : $errorDesc", LENGTH_LONG).show()
            viewModel.reloadWebView()
        }
    }

    // End of Public API (protected)

    private fun isCustomTabAuthFinishedCallback(intent: Intent): Boolean {
        return intent.data != null
    }

    private fun completeAdvAuthFlow(intent: Intent) {
        val params = UriFragmentParser.parse(intent.data)
        val error = params["error"]
        // Did we fail?
        when {
            error != null -> onAuthFlowError(error, params["error_description"])

            else -> {
                viewModel.showServerPicker.value = false
                viewModel.loading.value = true
                viewModel.onWebServerFlowComplete(params["code"], ::onAuthFlowError, ::onAuthFlowSuccess)
            }
        }
    }

    private fun handleBackBehavior() {
        // If app is using Native Login this activity is a fallback and can be dismissed.
        if (SalesforceSDKManager.getInstance().nativeLoginActivity != null) {
            setResult(RESULT_CANCELED)
            finish()
            return // If we don't call return here moveTaskToBack can also be called below.
        }

        // Do nothing if locked
        if (SalesforceSDKManager.getInstance().biometricAuthenticationManager?.locked == false) {
            /*
             * If there are no accounts signed in, the login screen needs to go
             * away and go back to the home screen. However, if the login screen
             * has been brought up from the switcher screen, the back button
             * should take the user back to the previous screen.
             */
            wasBackgrounded = true
            when (SalesforceSDKManager.getInstance().userAccountManager.authenticatedUsers) {
                null -> moveTaskToBack(true)
                else -> finish()
            }
        }
    }

    internal inner class SPStatusCallback : StatusUpdateCallback {
        override fun onStatusUpdate(status: Status) {
            runOnUiThread {
                makeText(
                    applicationContext,
                    getString(status.resIdForDescription),
                    LENGTH_SHORT
                ).show()
            }
        }
    }

    // Biometric Authentication Code
    private fun presentBiometric() {
        val biometricPrompt = biometricPrompt
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(authenticators)) {
            BIOMETRIC_ERROR_NO_HARDWARE, BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED, BIOMETRIC_ERROR_UNSUPPORTED, BIOMETRIC_STATUS_UNKNOWN -> {
                // This should never happen
                val error = getString(sf__screen_lock_error)
                e(TAG, "Biometric manager cannot authenticate. $error")
            }

            BIOMETRIC_ERROR_HW_UNAVAILABLE, BIOMETRIC_ERROR_NONE_ENROLLED -> {
                /*
                 * Prompts the user to setup OS screen lock and biometric
                 * TODO: Remove when min API > 29
                 */
                when {
                    SDK_INT >= R -> viewModel.biometricAuthenticationButtonAction.value = {
                        startActivityForResult(
                            Intent(
                                ACTION_BIOMETRIC_ENROLL
                            ).apply {
                                putExtra(
                                    EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                                    authenticators
                                )
                            },
                            SETUP_REQUEST_CODE
                        )
                    }

                    else -> viewModel.biometricAuthenticationButtonAction.value = {
                        startActivityForResult(
                            Intent(ACTION_SET_NEW_PASSWORD),
                            SETUP_REQUEST_CODE
                        )
                    }
                }
                viewModel.biometricAuthenticationButtonText.intValue = sf__setup_biometric_unlock
            }

            BIOMETRIC_SUCCESS -> biometricPrompt.authenticate(promptInfo)
        }
    }

    private val biometricPrompt: BiometricPrompt
        get() = BiometricPrompt(
            this,
            getMainExecutor(this),
            object : AuthenticationCallback() {

                override fun onAuthenticationSucceeded(result: AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    (SalesforceSDKManager.getInstance().biometricAuthenticationManager
                            as? BiometricAuthenticationManager?)?.run {
                        onUnlock()
                    }

                    CoroutineScope(IO).launch {
                        doTokenRefresh(this@LoginActivity)
                    }
                }
            }
        )

    private fun doTokenRefresh(activity: LoginActivity) {
        SalesforceSDKManager.getInstance().clientManager.getRestClient(
            activity
        ) { client ->
            runCatching {
                client.oAuthRefreshInterceptor.refreshAccessToken()
            }.onFailure { e ->
                e(TAG, "Error encountered while unlocking.", e)
            }
            activity.finish()
        }
    }

    private val authenticators
        get() = // TODO: Remove when min API > 29.
            when {
                SDK_INT >= R -> BIOMETRIC_STRONG or DEVICE_CREDENTIAL
                else -> BIOMETRIC_WEAK or DEVICE_CREDENTIAL
            }

    private val promptInfo: PromptInfo
        get() {
            var hasFaceUnlock = false
            if (SDK_INT >= Q) {
                hasFaceUnlock = packageManager.hasSystemFeature(
                    FEATURE_FACE
                ) || packageManager.hasSystemFeature(
                    FEATURE_IRIS
                )
            }
            val subtitle = SalesforceSDKManager.getInstance().userAccountManager.currentUser?.username ?: ""

            return PromptInfo.Builder()
                .setTitle(resources.getString(sf__biometric_opt_in_title))
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators)
                .setConfirmationRequired(hasFaceUnlock)
                .build()
        }

    open fun onBioAuthClick() = presentBiometric()

    /**
     * Called when the IDP login button is clicked.
     */
    open fun onIDPLoginClick() {
        SalesforceSDKManager.getInstance().spManager?.kickOffSPInitiatedLoginFlow(
            this,
            SPStatusCallback()
        )
    }

    private fun loadLoginPageInCustomTab(loginUrl: String, customTabLauncher: ActivityResultLauncher<Intent>) {
        val customTabsIntent = CustomTabsIntent.Builder().apply {
            /*
             * Set a custom animation to slide in and out for Chrome custom tab
             * so it doesn't look like a swizzle out of the app and back in
             */
            setStartAnimations(this@LoginActivity, slide_in_left, slide_out_right)
            setExitAnimations(this@LoginActivity, slide_in_left, slide_out_right)

            // Replace the default 'Close Tab' button with a custom back arrow instead of 'x'
            setCloseButtonIcon(decodeResource(resources, sf__action_back))
            setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            setDefaultColorSchemeParams(
                CustomTabColorSchemeParams.Builder().setToolbarColor(getColor(sf__primary_color)).build()
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

        // Add prompt=login to prevent the browser cookie from bypassing login if it exists.
        val urlString = if (sharedBrowserSession) loginUrl else loginUrl + PROMPT_LOGIN

        runCatching {
            customTabsIntent.intent.setData(urlString.toUri())
            customTabLauncher.launch(customTabsIntent.intent)
        }.onFailure { throwable ->
            e(TAG, "Unable to launch Advanced Authentication, Chrome browser not installed.", throwable)
            runOnUiThread {
                makeText(this@LoginActivity, "To log in, install Chrome.", LENGTH_LONG).show()
            }
            clearWebView()
        }
    }

    private fun doesBrowserExist(customTabBrowser: String?) =
        when (customTabBrowser) {
            null -> false
            else -> runCatching {
                packageManager?.getApplicationInfo(customTabBrowser, 0) != null
            }.onFailure { throwable ->
                w(TAG, "$customTabBrowser does not exist on this device", throwable)
            }.getOrDefault(false)
        }

    private fun swapJWTForAccessToken() {
        CoroutineScope(IO).launch {
            runCatching {
                if (viewModel.jwt.isNullOrBlank()) {
                    return@launch
                } else {
                    swapJWTForTokens(HttpAccess.DEFAULT, URI(viewModel.loginUrl.value), viewModel.jwt)
                }
            }.onFailure { throwable: Throwable ->
                jwtFlowError(throwable)
            }.onSuccess { tokenResponse: TokenEndpointResponse? ->
                if (tokenResponse?.authToken != null) {
                    if (tokenResponse.tokenFormat == "jwt") {
                        e(TAG, "Frontdoor cannot be used with a JWT access tokens.")
                        jwtFlowError()
                    } else {
                        viewModel.authCodeForJwtFlow = tokenResponse.authToken
                        viewModel.reloadWebView()
                    }
                }
            }
        }
    }

    private fun jwtFlowError(throwable: Throwable? = null) {
        viewModel.jwt = null
        onAuthFlowError(
            error = getString(sf__generic_authentication_error_title),
            errorDesc = getString(sf__jwt_authentication_error),
            e = throwable,
        )
    }

    /**
     * A web view client which intercepts the redirect to the OAuth callback URL.  That redirect marks the end of
     * the user facing portion of the authentication flow.
     *
     * AuthWebViewClient is an inner class of LoginActivity because it makes extensive use of the LoginViewModel,
     * which is only available to Activity classes (and composable functions).
     */
    open inner class AuthWebViewClient : WebViewClient() {
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
                        // Show loading while we PKCE and/or create user account.
                        viewModel.authFinished.value = true

                        // Determine if presence of override parameters require the user agent flow.
                        val overrideWithUserAgentFlow = viewModel.isUsingFrontDoorBridge
                                && viewModel.frontdoorBridgeCodeVerifier == null
                        when {
                            SalesforceSDKManager.getInstance().useWebServerAuthentication
                                    && !overrideWithUserAgentFlow ->

                                viewModel.onWebServerFlowComplete(
                                    params["code"],
                                    ::onAuthFlowError,
                                    ::onAuthFlowSuccess
                                )

                            else ->
                                CoroutineScope(Default).launch {
                                    viewModel.onAuthFlowComplete(
                                        TokenEndpointResponse(params),
                                        ::onAuthFlowError,
                                        ::onAuthFlowSuccess
                                    )
                                }
                        }
                    }
                }
            }

            return authFlowFinished
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            viewModel.loading.value = true
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            view?.evaluateJavascript(BACKGROUND_COLOR_JAVASCRIPT) { result ->
                if (url == ABOUT_BLANK) {
                    val isDark = SalesforceSDKManager.getInstance().isDarkTheme
                    viewModel.dynamicBackgroundColor.value = Color(ContextCompat.getColor(baseContext,
                        if (isDark) sf__background_dark else sf__background))

                    return@evaluateJavascript
                }

                viewModel.dynamicBackgroundColor.value = validateAndExtractBackgroundColor(result)
                    ?: return@evaluateJavascript

                // Ensure Status Bar Icons are readable no matter which OS theme is used.
                val titleTextColorLight = viewModel.titleTextColor?.luminance()?.let { it < 0.5 }
                val topAppBarDark = viewModel.topBarColor?.luminance()?.let { it < 0.5 }
                val dynamicThemeIsDark = viewModel.dynamicBackgroundTheme.value == DARK
                val useLightIcons = titleTextColorLight ?: topAppBarDark ?: dynamicThemeIsDark
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = useLightIcons
            }.also {
                if (!viewModel.authFinished.value) {
                    viewModel.loading.value = false
                }
            }

            EventsObservable.get().notifyEvent(AuthWebViewPageFinished, url)
            super.onPageFinished(view, url)
        }

        override fun onReceivedSslError(
            view: WebView,
            handler: SslErrorHandler,
            error: SslError,
        ) {
            val primErrorStringId = when (error.primaryError) {
                SSL_EXPIRED -> sf__ssl_expired
                SSL_IDMISMATCH -> sf__ssl_id_mismatch
                SSL_NOTYETVALID -> sf__ssl_not_yet_valid
                SSL_UNTRUSTED -> sf__ssl_untrusted
                else -> sf__ssl_unknown_error
            }

            // Build the text message
            val text = getString(sf__ssl_error, getString(primErrorStringId))
            e(TAG, "Received SSL error for server: $text")

            // Show the toast
            makeText(baseContext, text, LENGTH_LONG).show()
            handler.cancel()
        }

        override fun onReceivedClientCertRequest(
            view: WebView,
            request: ClientCertRequest,
        ) {
            d(TAG, "Received client certificate request from server")
            request.proceed(key, certChain)
        }

        private fun validateAndExtractBackgroundColor(javaScriptResult: String): Color? {
            val rgbMatch = rgbTextPattern.find(javaScriptResult)

            // groupValues[0] is the entire match.  [1] is red, [2] is green, [3] is green.
            rgbMatch?.groupValues?.get(3) ?: return null
            val red = rgbMatch.groupValues[1].toIntOrNull() ?: return null
            val green = rgbMatch.groupValues[2].toIntOrNull() ?: return null
            val blue = rgbMatch.groupValues[3].toIntOrNull() ?: return null

            return Color(red, green, blue)
        }
    }

    companion object {

        // region General Constants

        internal const val NEW_USER = "new_user"
        private const val SETUP_REQUEST_CODE = 72
        private const val TAG = "LoginActivity"
        private const val PROMPT_LOGIN = "&prompt=login"
        private const val AUTHENTICATION_FAILED_INTENT = "com.salesforce.auth.intent.AUTHENTICATION_ERROR"
        private const val HTTP_ERROR_RESPONSE_CODE_INTENT = "com.salesforce.auth.intent.HTTP_RESPONSE_CODE"
        private const val RESPONSE_ERROR_INTENT = "com.salesforce.auth.intent.RESPONSE_ERROR"
        private const val RESPONSE_ERROR_DESCRIPTION_INTENT = "com.salesforce.auth.intent.RESPONSE_ERROR_DESCRIPTION"

        // This parses the expected "rgb(x, x, x)" string.
        private val rgbTextPattern = "rgb\\((\\d{1,3}), (\\d{1,3}), (\\d{1,3})\\)".toRegex()

        // endregion
        // region LoginWebviewClient Constants

        internal const val ABOUT_BLANK = "about:blank"
        private const val BACKGROUND_COLOR_JAVASCRIPT =
            "(function() { return window.getComputedStyle(document.body, null).getPropertyValue('background-color'); })();"

        // endregion
        // region QR Code Login Via Salesforce Identity API UI Bridge Public Implementation

        /**
         * For QR code login URLs, the URL path which distinguishes them from other URLs provided by
         * the app's internal QR code reader or deep link intents from external QR code readers.
         *
         * Apps may customize this so long as it matches the server-side Apex class or other code
         * generating the QR code.
         *
         * Apps need not use the QR code login URL structure provided in this companion object if
         * they wish to entirely customize the QR code login URL format and implement a custom
         * parsing scheme.
         */
        @Suppress("MemberVisibilityCanBePrivate")
        var qrCodeLoginUrlPath = "/login/qr"

        /**
         * For QR code login URLs, the URL query string parameter name for the Salesforce Identity
         * API UI Bridge parameters JSON object.
         *
         * Apps may customize this so long as it matches the server-side Apex class or other code
         * generating the QR code.
         */
        @Suppress("MemberVisibilityCanBePrivate")
        var qrCodeLoginUrlJsonParameterName = "bridgeJson"

        /**
         * For QR code login URLs, the Salesforce Identity API UI Bridge parameters JSON key for the
         * frontdoor URL.
         *
         * Apps may customize this so long as it matches the server-side Apex class or other code
         * generating the QR code.
         */
        @Suppress("MemberVisibilityCanBePrivate")
        var qrCodeLoginUrlJsonFrontdoorBridgeUrlKey = "frontdoor_bridge_url"

        /**
         * For QR code login URLs, the Salesforce Identity API UI Bridge parameters JSON key for the
         * PKCE code verifier, which is only used when the front door URL was generated for the web
         * server authorization flow.  The user agent flow does not require a value for this
         * parameter.
         *
         * Apps may customize this so long as it matches the server-side Apex class or other code
         * generating the QR code.
         */
        @Suppress("MemberVisibilityCanBePrivate")
        var qrCodeLoginUrlJsonPkceCodeVerifierKey = "pkce_code_verifier"

        /**
         * Determines if the provided intent has QR code login parameters.
         * @param intent The intent to determine QR code login enablement for
         * @return Boolean true if the intent has QR code login parameters or
         * false otherwise
         */
        fun isQrCodeLoginUrlIntent(
            intent: Intent,
        ) = intent.data?.path?.contains(qrCodeLoginUrlPath) == true

        /**
         * Determines if the provided intent has front door bridge URL
         * parameters.
         * @param intent The intent to determine front door bridge URL
         * enablement for
         * @return Boolean true if the intent has front door bridge URL
         * parameters or false otherwise
         */
        private fun isFrontdoorBridgeUrlIntent(
            intent: Intent,
        ) = intent.hasExtra(EXTRA_KEY_FRONTDOOR_BRIDGE_URL)

        /**
         * Parses Salesforce Identity API UI Bridge parameters from the provided login QR code login
         * URL.
         * @param qrCodeLoginUrl The QR code login URL
         * @return The UI Bridge API parameters or null if the QR code login URL cannot provide them
         * for any reason
         */
        fun uiBridgeApiParametersFromQrCodeLoginUrl(
            qrCodeLoginUrl: String?,
        ) = qrCodeLoginUrl?.let { qrCodeLoginUrlUnwrapped ->
            uiBridgeApiJsonFromQrCodeLoginUrl(qrCodeLoginUrlUnwrapped)?.let { uiBridgeApiJson ->
                uiBridgeApiParametersFromUiBridgeApiJson(uiBridgeApiJson)
            }
        }

        /**
         * A data class representing Salesforce Identity API UI Bridge parameters.
         */
        data class UiBridgeApiParameters(

            /** The front door bridge URL */
            val frontdoorBridgeUrl: String,

            /** The PKCE code verifier */
            val pkceCodeVerifier: String?,
        )

        // endregion
        // region QR Code Login Via Salesforce Identity API UI Bridge Private Implementation

        /** Extras key for the Salesforce Identity API UI Bridge front door URL */
        const val EXTRA_KEY_FRONTDOOR_BRIDGE_URL = "frontdoor_bridge_url"

        /** Extras key for the Salesforce Identity API UI PKCE code verifier */
        const val EXTRA_KEY_PKCE_CODE_VERIFIER = "pkce_code_verifier"

        /**
         * For QR code login URLs, a regular expression to extract the Salesforce Identity API UI
         * Bridge parameter JSON string.
         */
        private val qrCodeLoginJsonRegex by lazy {
            """\?$qrCodeLoginUrlJsonParameterName=(%7B.*%7D)""".toRegex()
        }

        /**
         * Parses Salesforce Identity API UI Bridge parameters JSON string from the provided QR code
         * login URL.
         *
         * @param qrCodeLoginUrl The QR code login URL
         * @return String: The UI Bridge API parameter JSON or null if the QR code login URL cannot
         * provide the JSON for any reason
         */
        private fun uiBridgeApiJsonFromQrCodeLoginUrl(
            qrCodeLoginUrl: String,
        ) = qrCodeLoginJsonRegex.find(qrCodeLoginUrl)?.groups?.get(1)?.value?.let {
            URLDecoder.decode(it, "UTF-8")
        }

        /**
         * Creates Salesforce Identity API UI Bridge parameters from the provided JSON string.
         * @param uiBridgeApiParameterJsonString The UI Bridge API parameters JSON string
         * @return The UI Bridge API parameters
         */
        private fun uiBridgeApiParametersFromUiBridgeApiJson(
            uiBridgeApiParameterJsonString: String,
        ) = JSONObject(uiBridgeApiParameterJsonString).let { uiBridgeApiParameterJson ->
            UiBridgeApiParameters(
                uiBridgeApiParameterJson.getString(qrCodeLoginUrlJsonFrontdoorBridgeUrlKey),
                when (uiBridgeApiParameterJson.has(qrCodeLoginUrlJsonPkceCodeVerifierKey)) {
                    true -> uiBridgeApiParameterJson.optString(qrCodeLoginUrlJsonPkceCodeVerifierKey)
                    else -> null
                }
            )
        }

        // endregion
    }
}
