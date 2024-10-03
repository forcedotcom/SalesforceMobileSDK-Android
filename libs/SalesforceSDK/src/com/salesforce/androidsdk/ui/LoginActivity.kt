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

import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager.ERROR_CODE_CANCELED
import android.accounts.AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE
import android.app.Activity
import android.app.admin.DevicePolicyManager.ACTION_SET_NEW_PASSWORD
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager.FEATURE_FACE
import android.content.pm.PackageManager.FEATURE_IRIS
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Build.VERSION_CODES.R
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Bundle
import android.provider.Settings.ACTION_BIOMETRIC_ENROLL
import android.provider.Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED
import android.security.KeyChain.choosePrivateKeyAlias
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_BACK
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.VISIBLE
import android.view.WindowManager.LayoutParams.FLAG_SECURE
import android.webkit.WebSettings.LayoutAlgorithm
import android.webkit.WebView
import android.widget.Button
import android.widget.Toast.LENGTH_SHORT
import android.widget.Toast.makeText
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
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
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getMainExecutor
import androidx.core.content.ContextCompat.registerReceiver
import com.salesforce.androidsdk.R.id.sf__auth_container_phone
import com.salesforce.androidsdk.R.id.sf__bio_login_button
import com.salesforce.androidsdk.R.id.sf__idp_login_button
import com.salesforce.androidsdk.R.id.sf__menu_clear_cookies
import com.salesforce.androidsdk.R.id.sf__menu_pick_server
import com.salesforce.androidsdk.R.id.sf__menu_reload
import com.salesforce.androidsdk.R.id.sf__oauth_webview
import com.salesforce.androidsdk.R.string.sf__biometric_opt_in_title
import com.salesforce.androidsdk.R.string.sf__login_with_biometric
import com.salesforce.androidsdk.R.string.sf__screen_lock_error
import com.salesforce.androidsdk.R.string.sf__setup_biometric_unlock
import com.salesforce.androidsdk.R.style.SalesforceSDK
import com.salesforce.androidsdk.R.style.SalesforceSDK_Dark_Login
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountManager.USER_SWITCH_TYPE_DEFAULT
import com.salesforce.androidsdk.accounts.UserAccountManager.USER_SWITCH_TYPE_FIRST_LOGIN
import com.salesforce.androidsdk.accounts.UserAccountManager.USER_SWITCH_TYPE_LOGIN
import com.salesforce.androidsdk.analytics.SalesforceAnalyticsManager
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.idp.interfaces.SPManager.Status
import com.salesforce.androidsdk.auth.idp.interfaces.SPManager.StatusUpdateCallback
import com.salesforce.androidsdk.config.RuntimeConfig.ConfigKey.ManagedAppCertAlias
import com.salesforce.androidsdk.config.RuntimeConfig.ConfigKey.RequireCertAuth
import com.salesforce.androidsdk.config.RuntimeConfig.getRuntimeConfig
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions.fromBundleWithSafeLoginUrl
import com.salesforce.androidsdk.security.BiometricAuthenticationManager
import com.salesforce.androidsdk.security.BiometricAuthenticationManager.Companion.SHOW_BIOMETRIC
import com.salesforce.androidsdk.ui.OAuthWebviewHelper.OAuthWebviewHelperEvents
import com.salesforce.androidsdk.util.AuthConfigUtil.AUTH_CONFIG_COMPLETE_INTENT_ACTION
import com.salesforce.androidsdk.util.EventsObservable
import com.salesforce.androidsdk.util.EventsObservable.EventType.AuthWebViewCreateComplete
import com.salesforce.androidsdk.util.EventsObservable.EventType.LoginActivityCreateComplete
import com.salesforce.androidsdk.util.SalesforceSDKLogger.d
import com.salesforce.androidsdk.util.SalesforceSDKLogger.e
import com.salesforce.androidsdk.util.UriFragmentParser.parse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLDecoder
import com.salesforce.androidsdk.R.layout.sf__login as sf__login_layout
import com.salesforce.androidsdk.R.menu.sf__login as sf__login_menu

/**
 * Login activity authenticates a user. Authorization happens inside a web view.
 * Once an authorization code is obtained, it is exchanged for access and
 * refresh tokens to create an account via the account manager which stores
 * them.
 *
 * Note, the majority of the work for authorization is actually managed by the
 * OAuth web view helper class.
 */
open class LoginActivity : AppCompatActivity(), OAuthWebviewHelperEvents {
    private var wasBackgrounded = false
    private var webviewHelper: OAuthWebviewHelper? = null
    private var authConfigReceiver: AuthConfigReceiver? = null
    private var receiverRegistered = false
    private var accountAuthenticatorResponse: AccountAuthenticatorResponse? = null
    private var accountAuthenticatorResult: Bundle? = null
    private var biometricAuthenticationButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val salesforceSDKManager = SalesforceSDKManager.getInstance()

        // Determine if the activity was created from a deep link intent with QR code log in via UI bridge API parameters.
        val isDeepLinkedQrCodeLogin = isDeepLinkedQrCodeLogin(intent)

        accountAuthenticatorResponse = intent.getParcelableExtra<AccountAuthenticatorResponse?>(
            KEY_ACCOUNT_AUTHENTICATOR_RESPONSE
        )?.apply {
            onRequestContinued()
        }

        setTheme(
            when (salesforceSDKManager.isDarkTheme) {
                true -> SalesforceSDK_Dark_Login
                else -> SalesforceSDK
            }
        )

        salesforceSDKManager.setViewNavigationVisibility(this)

        // Determine login options for QR code login or the app's usual login.
        val loginOptions = when {
            isDeepLinkedQrCodeLogin -> salesforceSDKManager.loginOptions
            else -> fromBundleWithSafeLoginUrl(intent.extras)
        }

        // Protect against screenshots
        window.setFlags(FLAG_SECURE, FLAG_SECURE)

        // Fetch authentication configuration except for QR code login.
        if (!isDeepLinkedQrCodeLogin) {
            salesforceSDKManager.fetchAuthenticationConfiguration()
        }

        // Setup content view
        setContentView(sf__login_layout)
        if (salesforceSDKManager.isIDPLoginFlowEnabled) {
            findViewById<Button>(sf__idp_login_button).visibility = VISIBLE
        }

        val biometricAuthenticationManager = (salesforceSDKManager.biometricAuthenticationManager as? BiometricAuthenticationManager)
        if (biometricAuthenticationManager?.locked == true && biometricAuthenticationManager.hasBiometricOptedIn()) {
            if (biometricAuthenticationManager.isNativeBiometricLoginButtonEnabled()) {
                biometricAuthenticationButton = findViewById<Button>(sf__bio_login_button)?.apply {
                    visibility = VISIBLE
                }
            }
            if (intent.extras?.getBoolean(SHOW_BIOMETRIC) == true) {
                presentBiometric()
            }
        }

        // Set up the web view
        val webView = findViewById<WebView>(sf__oauth_webview)
        webView.settings.apply {
            useWideViewPort = true
            layoutAlgorithm = LayoutAlgorithm.NORMAL
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            databaseEnabled = true
            domStorageEnabled = true
        }
        EventsObservable.get().notifyEvent(AuthWebViewCreateComplete, webView)
        webviewHelper = getOAuthWebviewHelper(
            this,
            loginOptions,
            webView,
            savedInstanceState
        )

        // Let observers know
        EventsObservable.get().notifyEvent(
            LoginActivityCreateComplete,
            this
        )

        // Prompt user with log in page or log in via other configurations such as QR code.
        when {
            isDeepLinkedQrCodeLogin -> loginFromQrCode("?" + intent.data?.query)
            else -> certAuthOrLogin()
        }

        if (!receiverRegistered) {
            authConfigReceiver = AuthConfigReceiver().also { changeServerReceiver ->
                registerReceiver(
                    this,
                    changeServerReceiver,
                    IntentFilter(AUTH_CONFIG_COMPLETE_INTENT_ACTION),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            }
            receiverRegistered = true
        }

        // Take control of the back logic if the device is locked.
        // TODO:  Remove SDK_INT check when min API > 33
        if (SDK_INT >= TIRAMISU && biometricAuthenticationManager?.locked == true) {
            onBackPressedDispatcher.addCallback { handleBackBehavior() }
        }

        requestedOrientation = if (salesforceSDKManager.compactScreen(this))
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER

        fixEdgeToEdge(findViewById(sf__auth_container_phone))
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(authConfigReceiver)
            receiverRegistered = false
        }

        handleBackBehavior()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        // If the intent is a callback from Chrome, process it and do nothing else
        if (isChromeCallback(intent)) {
            completeAuthFlow(intent)
            return
        }

        /*
         * It is important to not reload if we have a custom tab displayed because that also generates
         * a new code verifier which will break PKCE.
         */
        if (!SalesforceSDKManager.getInstance().isBrowserLoginEnabled) {
            // Reload the login page to ensure the correct login server is displayed in the webview.
            webviewHelper?.run {
                loadLoginPage()
            }
        }
    }

    // The code in this override was taken from the deprecated
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
        super.finish()
    }

    protected open fun certAuthOrLogin() {
        when {
            shouldUseCertBasedAuth() -> {
                val alias = getRuntimeConfig(this).getString(ManagedAppCertAlias)
                d(TAG, "Cert based login flow being triggered with alias: $alias")
                choosePrivateKeyAlias(
                    this,
                    webviewHelper ?: return,
                    null,
                    null,
                    null,
                    -1,
                    alias
                )
            }

            else -> {
                d(TAG, "Web server or user agent login flow triggered")
                webviewHelper?.loadLoginPage()
            }
        }
    }

    private fun isChromeCallback(intent: Intent?): Boolean {
        if (intent == null) {
            return false
        }
        return intent.data != null
    }

    private fun completeAuthFlow(intent: Intent) {
        val params = parse(intent.data)
        params["error"]?.let { error ->
            val errorDesc = params["error_description"]
            webviewHelper?.onAuthFlowError(error, errorDesc, null)
        } ?: webviewHelper?.onWebServerFlowComplete(params["code"])
    }

    /**
     * Indicates if the certificate based authentication flow should be used.
     *
     * @return True if certificate based authentication flow should be used and
     * false otherwise
     */
    protected open fun shouldUseCertBasedAuth(): Boolean =
        getRuntimeConfig(this).getBoolean(RequireCertAuth)

    protected open fun getOAuthWebviewHelper(
        callback: OAuthWebviewHelperEvents,
        loginOptions: LoginOptions,
        webView: WebView,
        savedInstanceState: Bundle?
    ) = OAuthWebviewHelper(
        this,
        callback,
        loginOptions,
        webView,
        savedInstanceState
    )

    override fun onResume() {
        super.onResume()
        wasBackgrounded = false
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webviewHelper?.saveState(outState)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent) =
        // This allows sub classes to override the behavior by returning false
        when {
            fixBackButtonBehavior(keyCode) -> true
            else -> super.onKeyDown(keyCode, event)
        }

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

    private fun handleBackBehavior() {
        // If app is using Native Login this activity is a fallback and can be dismissed.
        if (SalesforceSDKManager.getInstance().nativeLoginActivity != null) {
            setResult(Activity.RESULT_CANCELED)
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

    /**
     * Actions (Changer server / Clear cookies etc) are available through a menu
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(sf__login_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            sf__menu_clear_cookies -> {
                onClearCookiesClick(null)
                true
            }

            sf__menu_pick_server -> {
                onPickServerClick(null)
                true
            }

            sf__menu_reload -> {
                onReloadClick(null)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }

    /**
     * Callbacks from the OAuth web view helper
     */
    override fun loadingLoginPage(loginUrl: String) {
        this@LoginActivity.runOnUiThread { supportActionBar?.title = loginUrl }
    }

    /**
     * Callbacks from the OAuth web view helper
     */
    override fun onAccountAuthenticatorResult(authResult: Bundle) {
        accountAuthenticatorResult = authResult
    }

    /**
     * Called when the "clear cookies" button is clicked to clear cookies and
     * reload the login page.
     * @param v The view that was clicked
     */
    open fun onClearCookiesClick(v: View?) {
        webviewHelper?.clearCookies()
        webviewHelper?.loadLoginPage()
    }

    /**
     * Called when the IDP login button is clicked.
     *
     * @param v IDP login button
     */
    open fun onIDPLoginClick(v: View?) {
        SalesforceSDKManager.getInstance().spManager?.kickOffSPInitiatedLoginFlow(
            this,
            SPStatusCallback()
        )
    }

    /**
     * Called when the "reload" button is clicked to reloads the login page.
     * @param v The reload button
     */
    private fun onReloadClick(@Suppress("UNUSED_PARAMETER") v: View?) {
        webviewHelper?.loadLoginPage()
    }

    /**
     * Called when "pick server" button is clicked to start the server picker
     * activity.
     * @param v The pick server button
     */
    open fun onPickServerClick(v: View?) {
        Intent(this, ServerPickerActivity::class.java).also { intent ->
            startActivityForResult(
                intent,
                PICK_SERVER_REQUEST_CODE
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        /*
         * Present authentication again after the user has come back from
         * security settings to ensure they actually set up a secure lock screen
         * (pin/pattern/password/etc) instead of swipe or none.
         */
        if (requestCode == SETUP_REQUEST_CODE) {
            biometricAuthenticationButton?.setText(sf__login_with_biometric)
            presentBiometric()
        }
    }

    override fun finish(userAccount: UserAccount?) {
        initAnalyticsManager(userAccount)
        val userAccountManager = SalesforceSDKManager.getInstance().userAccountManager
        val authenticatedUsers = userAccountManager.authenticatedUsers
        val numAuthenticatedUsers = authenticatedUsers?.size ?: 0
        val userSwitchType = when {
            // We've already authenticated the first user, so there should be one
            numAuthenticatedUsers == 1 -> USER_SWITCH_TYPE_FIRST_LOGIN

            // Otherwise we're logging in with an additional user
            numAuthenticatedUsers > 1 -> USER_SWITCH_TYPE_LOGIN

            // This should never happen but if it does, pass in the "unknown" value
            else -> USER_SWITCH_TYPE_DEFAULT
        }
        userAccountManager.sendUserSwitchIntent(userSwitchType, null)
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun initAnalyticsManager(account: UserAccount?) =
        SalesforceAnalyticsManager.getInstance(account)?.updateLoggingPrefs()

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

    inner class AuthConfigReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action == AUTH_CONFIG_COMPLETE_INTENT_ACTION) {
                webviewHelper?.loadLoginPage()
            }
        }
    }

    internal fun presentBiometric() {
        val biometricPrompt = biometricPrompt
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(authenticators)) {
            BIOMETRIC_ERROR_NO_HARDWARE, BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED, BIOMETRIC_ERROR_UNSUPPORTED, BIOMETRIC_STATUS_UNKNOWN -> {
                // This should never happen
                val error = getString(sf__screen_lock_error)
                e(TAG, "Biometric manager cannot authenticate. $error")
            }

            BIOMETRIC_ERROR_HW_UNAVAILABLE, BIOMETRIC_ERROR_NONE_ENROLLED ->
                biometricAuthenticationButton?.let { biometricAuthenticationButton ->
                    /*
                     * Prompts the user to setup OS screen lock and biometric
                     * TODO: Remove when min API > 29
                     */
                    when {
                        SDK_INT >= R -> biometricAuthenticationButton.setOnClickListener {
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

                        else -> biometricAuthenticationButton.setOnClickListener {
                            startActivityForResult(
                                Intent(ACTION_SET_NEW_PASSWORD),
                                SETUP_REQUEST_CODE
                            )
                        }
                    }
                    biometricAuthenticationButton.text = getString(sf__setup_biometric_unlock)
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
            val subtitle = SalesforceSDKManager.getInstance().userAccountManager.currentUser.username

            return PromptInfo.Builder()
                .setTitle(resources.getString(sf__biometric_opt_in_title))
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators)
                .setConfirmationRequired(hasFaceUnlock)
                .build()
        }

    open fun onBioAuthClick(view: View?) = presentBiometric()

    // region QR Code Login Via UI Bridge API Public Implementation

    /**
     * Automatically log in with a UI Bridge API login QR code.
     * @param loginQrCodeContent The login QR code content.  This should be
     * either a URL or URL query containing the UI Bridge API JSON parameter.
     * The UI Bridge API JSON parameter should contain URL-encoded JSON with two
     * values:
     * - frontdoor_bridge_url
     * - pkce_code_verifier
     *
     * If pkce_code_verifier is not specified then the user agent flow is used
     * @return Boolean true if a log in attempt is possible using the provided QR
     * code content, false otherwise
     */
    fun loginFromQrCode(
        loginQrCodeContent: String?
    ) = uiBridgeApiParametersFromLoginQrCodeContent(
        loginQrCodeContent
    )?.let { uiBridgeApiParameters ->
        loginWithFrontdoorBridgeUrl(
            uiBridgeApiParameters.frontdoorBridgeUrl,
            uiBridgeApiParameters.pkceCodeVerifier
        )
        true
    } ?: false

    /**
     * Automatically log in with a UI Bridge API front door bridge URL and PKCE
     * code verifier.
     * @param frontdoorBridgeUrl The UI Bridge API front door bridge URL
     * @param pkceCodeVerifier The PKCE code verifier
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun loginWithFrontdoorBridgeUrl(
        frontdoorBridgeUrl: String,
        pkceCodeVerifier: String?
    ) = webviewHelper?.loginWithFrontdoorBridgeUrl(frontdoorBridgeUrl, pkceCodeVerifier)

    // endregion
    // region QR Code Login Via UI Bridge API Private Implementation

    /**
     * Determines if QR code login is enabled for the provided intent.
     * @param intent The intent to determine QR code login enablement for
     * @return Boolean true if QR code login is enabled for the the intent or
     * false otherwise
     */
    private fun isDeepLinkedQrCodeLogin(
        intent: Intent
    ) = SalesforceSDKManager.getInstance().isQrCodeLoginEnabled
            && intent.data?.path?.contains(LOGIN_QR_PATH) == true

    /**
     * Parses UI Bridge API parameters from the provided login QR code content.
     * @param loginQrCodeContent The login QR code content string
     * @return UiBridgeApiParameters: The UI Bridge API parameters or null if the QR code
     * content cannot provide them for any reason
     */
    private fun uiBridgeApiParametersFromLoginQrCodeContent(
        loginQrCodeContent: String?
    ) = loginQrCodeContent?.let { loginQrCodeContentUnwrapped ->
        uiBridgeApiJsonFromQrCodeContent(loginQrCodeContentUnwrapped)?.let { uiBridgeApiJson ->
            uiBridgeApiParametersFromUiBridgeApiJson(uiBridgeApiJson)
        }
    }

    /**
     * Parses UI Bridge API parameters JSON from the provided string, which may
     * be formatted to match either QR code content provided by the intent or
     * the app's QR code library.
     *
     * 1. From intent (external QR reader): ?bridgeJson={...}
     * 2. From the app's QR reader: ?bridgeJson=%7B...%7D
     *
     * @param qrCodeContent The QR code content string
     * @return String: The UI Bridge API parameter JSON or null if the string
     * cannot provide the JSON for any reason
     */
    private fun uiBridgeApiJsonFromQrCodeContent(
        qrCodeContent: String
    ) = qrCodeBridgeJsonRegexExternal.find(qrCodeContent)?.groups?.get(1)?.value
        ?: qrCodeBridgeJsonRegexInternal.find(qrCodeContent)?.groups?.get(1)?.value?.let {
            URLDecoder.decode(it, "UTF-8")
        }

    /**
     * Creates UI Bridge API parameters from the provided JSON string.
     * @param uiBridgeApiParameterJsonString The UI Bridge API parameters JSON
     * string
     * @return The UI Bridge API parameters
     */
    private fun uiBridgeApiParametersFromUiBridgeApiJson(
        uiBridgeApiParameterJsonString: String
    ) = JSONObject(uiBridgeApiParameterJsonString).let { uiBridgeApiParameterJson ->
        UiBridgeApiParameters(
            uiBridgeApiParameterJson.getString(FRONTDOOR_BRIDGE_URL_KEY),
            when (uiBridgeApiParameterJson.has(PKCE_CODE_VERIFIER_KEY)) {
                true -> uiBridgeApiParameterJson.optString(PKCE_CODE_VERIFIER_KEY)
                else -> null
            }
        )
    }

    /**
     * A data class representing UI Bridge API parameters provided by a login QR
     * code.
     */
    private data class UiBridgeApiParameters(

        /** The front door bridge URL provided by the login QR code */
        val frontdoorBridgeUrl: String,

        /** The PKCE code verifier provided by the login QR code */
        val pkceCodeVerifier: String?
    )

    // endregion

    companion object {
        const val PICK_SERVER_REQUEST_CODE = 10
        private const val SETUP_REQUEST_CODE = 72
        private const val TAG = "LoginActivity"

        // region QR Code Login Via UI Bridge API Constants

        /** The QR code login intent path */
        private const val LOGIN_QR_PATH = "/login/qr"

        /** The login QR code's UI Bridge API parameter's JSON frontdoor bridge URL key */
        private const val FRONTDOOR_BRIDGE_URL_KEY = "frontdoor_bridge_url"

        /** The login QR code's UI Bridge API parameter's JSON PKCE code verifier key */
        private const val PKCE_CODE_VERIFIER_KEY = "pkce_code_verifier"

        /** A regular expression to extract the UI Bridge API parameter JSON from the intent's login QR code content */
        private val qrCodeBridgeJsonRegexExternal by lazy { """\?bridgeJson=(\{.*\})""".toRegex() }

        /** A regular expression to extract the UI Bridge API parameter JSON from the app's login QR code content */
        private val qrCodeBridgeJsonRegexInternal by lazy { """\?bridgeJson=(%7B.*%7D)""".toRegex() }

        // endregion
    }
}
