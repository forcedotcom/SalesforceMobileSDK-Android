/*
 * Copyright (c) 2025-present, salesforce.com, inc.
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

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebView
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.graphics.luminance
import androidx.core.net.toUri
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.salesforce.androidsdk.R.string.oauth_display_type
import com.salesforce.androidsdk.R.string.sf__login_with_biometric
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.app.SalesforceSDKManager.Theme.DARK
import com.salesforce.androidsdk.app.SalesforceSDKManager.Theme.LIGHT
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.auth.OAuth2
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse
import com.salesforce.androidsdk.auth.OAuth2.exchangeCode
import com.salesforce.androidsdk.auth.OAuth2.getFrontdoorUrl
import com.salesforce.androidsdk.auth.defaultBuildAccountName
import com.salesforce.androidsdk.auth.onAuthFlowComplete
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.config.LoginServerManager.LoginServer
import com.salesforce.androidsdk.config.RuntimeConfig.ConfigKey.OnlyShowAuthorizedHosts
import com.salesforce.androidsdk.config.RuntimeConfig.getRuntimeConfig
import com.salesforce.androidsdk.security.SalesforceKeyGenerator.getRandom128ByteKey
import com.salesforce.androidsdk.security.SalesforceKeyGenerator.getSHA256Hash
import com.salesforce.androidsdk.ui.LoginActivity.Companion.ABOUT_BLANK
import com.salesforce.androidsdk.ui.LoginActivity.Companion.isSalesforceWelcomeDiscoveryUrlPath
import com.salesforce.androidsdk.util.SalesforceSDKLogger.e
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI

open class LoginViewModel(val bootConfig: BootConfig) : ViewModel() {

    // region UI Customization

    /** TopAppBar Color.  Defaults to WebView background color. */
    open var topBarColor: Color? = null

    /** TopAppBar text.  Defaults to login server url. */
    open var titleText: String? = null

    /**
     * TopAppBar text Color.  Defaults to black on light backgrounds and white
     * on dark backgrounds.  Back and menu buttons will match this color.
     */
    open var titleTextColor: Color? = null

    /** Loading Indicator */
    open val loadingIndicator: (@Composable () -> Unit)? = null

    // DefaultBottomAppBar Customization

    /**
     * A custom button to display on the login view bottom app bar.
     *
     * Note: This button will not be displayed if the user is locked by Biometric Authentication
     * or the Identity Provider flow is enabled.
     */
    open val customBottomBarButton = mutableStateOf<BottomBarButton?>(null)

    // Override App Bars
    /** TopAppBar that will be used instead of the default. */
    open val topAppBar: (@Composable () -> Unit)? = null

    /** BottomAppBar that will be used instead of the default. */
    open val bottomAppBar: (@Composable () -> Unit)? = null

    // endregion

    // Public LiveData
    /** The login server that has been selected by the login server manager, has an authentication configuration and is ready for use */
    val selectedServer = MediatorLiveData<String>()

    /** The selected login server's OAuth authorization URL for use in the web view when browser-based authentication is inactive */
    val loginUrl = MediatorLiveData<String>()

    /** The selected login server's OAuth authorization URL for use in the external browser custom tab when browser-based authentication is active.  This provides the login flow with the requirements for advanced authentication, such as client certificates */
    val browserCustomTabUrl = MediatorLiveData<String>()

    var showServerPicker = mutableStateOf(false)
    var loading = mutableStateOf(false)

    // Internal LiveData

    /** The Kotlin Coroutine Job fetching the pending login server's authentication configuration */
    private var authenticationConfigurationFetchJob: Job? = null

    /** The login server that has been selected by the login server manager and is pending authentication configuration before becoming the selected login server */
    internal val pendingServer = MediatorLiveData<String>()

    /** The previously observed pending login server for use in switching between default and Salesforce Welcome Discovery log in */
    internal var previousPendingServer: String? = null

    internal val authFinished = mutableStateOf(false)
    internal val isIDPLoginFlowEnabled = derivedStateOf {
        SalesforceSDKManager.getInstance().isIDPLoginFlowEnabled
    }
    internal val isBiometricAuthenticationLocked = derivedStateOf {
        SalesforceSDKManager.getInstance().biometricAuthenticationManager?.let { bioAuthManager ->
            bioAuthManager.locked && bioAuthManager.hasBiometricOptedIn()
        } ?: false
    }
    internal val biometricAuthenticationButtonText = mutableIntStateOf(sf__login_with_biometric)
    internal val biometricAuthenticationButtonAction = mutableStateOf<(() -> Unit)?>(null)
    internal var dynamicBackgroundColor = mutableStateOf(White)
    internal var dynamicBackgroundTheme =
        derivedStateOf { if (dynamicBackgroundColor.value.luminance() > 0.5) DARK else LIGHT }
    internal var dynamicHeaderTextColor =
        derivedStateOf { if (dynamicBackgroundColor.value.luminance() > 0.5) Black else White }
    internal val defaultTitleText: String
        get() = if (loginUrl.value == ABOUT_BLANK) "" else selectedServer.value ?: ""

    internal val serverPickerAddConnectionButtonVisible = !getRuntimeConfig(SalesforceSDKManager.getInstance().appContext).getBoolean(OnlyShowAuthorizedHosts)

    /** Additional Auth Values used for login. */
    open var additionalParameters = hashMapOf<String, String>()

    /** JWT string used for JWT Auth Flow. */
    var jwt: String? = null

    /** Connected App/External Client App client Id. */
    protected open var clientId: String = bootConfig.remoteAccessConsumerKey

    /** Authorization Display Type used for login. */
    protected open val authorizationDisplayType =
        SalesforceSDKManager.getInstance().appContext.getString(oauth_display_type)

    /**
     * Determines use of OAuth 2.0 Web Server Flow or User-Agent Flow.
     *
     * When a Salesforce Identity API UI Bridge Front-Door URL is in use for log
     * in and it has a PKCE/Code verifier Web Server Flow will be Enabled.  For
     * a Front-Door Bridge URL without a PKCE/Code Verifier User-Agent Flow will
     * be enabled.
     *
     * When no Front-Door Bridge URL is in use, Web Server Flow is
     * enabled when web server authentication or browser login are enabled.
     * @return True if Web Server Flow is enabled, false if User-Agent Flow is
     * enabled.
     */
    internal val useWebServerFlow: Boolean
        get() = with(SalesforceSDKManager.getInstance()) {
            // First, an in-use Salesforce Identity API UI Bridge front-door bridge URL takes precedence.
            if (isUsingFrontDoorBridge) {
                // A front-door bridge URL accompanied by a PKCE code verifier requires Web Server Flow.  Otherwise, User Agent-Flow must be used.
                frontdoorBridgeCodeVerifier != null
            }
            // Second, when not using a front-door bridge URL, the app's preferences can be used.
            else {
                useWebServerAuthentication || isBrowserLoginEnabled
            }
        }

    /**
     * Setting this option to true will enable a mode where only a custom tab will be shown.  The first server will be
     * launched in a custom tab immediately and the user will not be able to switch servers.  The LoginActivity is
     * ended if the user attempts to back out of the custom tab.
     *
     * Since browser based authentication requires PKCE (and therefore the Web Server flow) the User Agent flow
     * cannot be used while in this mode.
     */
    open val singleServerCustomTabActivity = false

    /** Value representing if the back button should be shown on the login view. */
    open val shouldShowBackButton = with(SalesforceSDKManager.getInstance()) {
        !(userAccountManager.authenticatedUsers.isNullOrEmpty() || biometricAuthenticationManager?.locked ?: false)
    }

    // The default, locally generated code verifier
    @VisibleForTesting
    internal var codeVerifier: String? = null

    /** The Salesforce Welcome Login hint parameter value for the OAuth authorize endpoint */
    internal var loginHint: String? = null

    // Auth code we receive from the JWT swap for magic links.
    internal var authCodeForJwtFlow: String? = null

    // For Salesforce Identity API UI Bridge support, indicates use of an overriding front door bridge URL.
    internal var isUsingFrontDoorBridge = false

    // The optional server used for code exchange.
    internal var frontdoorBridgeServer: String? = null

    // The optional web server flow code verifier accompanying the front door bridge server.
    internal var frontdoorBridgeCodeVerifier: String? = null


    init {
        // When the login server manager selects a login server, first fetch its authentication configuration by setting the pending login server. Second, the selected login server will be set afterwards.
        pendingServer.addSource(SalesforceSDKManager.getInstance().loginServerManager.selectedServer, PendingServerSource())

        // Update loginUrl when selectedServer updates so webview automatically reloads
        loginUrl.addSource(selectedServer, LoginUrlSource())

        // When selecting a login server, update the browser custom tab URL to match the OAuth authorization URL when browser-based authentication is active.
        browserCustomTabUrl.addSource(selectedServer, BrowserCustomTabUrlSource())
    }

    /** Reloads the WebView with a newly generated authorization URL. */
    open fun reloadWebView() {
        if (!isUsingFrontDoorBridge) {
            // The Web Server Flow code challenge makes the authorization url unique each time,
            // which triggers recomposition.  For User Agent Flow, change it to blank.
            if (!SalesforceSDKManager.getInstance().useWebServerAuthentication) {
                loginUrl.value = ABOUT_BLANK
            }
            loginUrl.value = getAuthorizationUrl(selectedServer.value ?: return)
        }
    }

    /** Clear WebView Cookies. */
    open fun clearCookies() =
        CookieManager.getInstance().removeAllCookies(null)

    /** Clear WebView Cache. */
    open fun clearWebViewCache(webView: WebView) {
        webView.clearCache(true)
    }

    /**
     * Automatically log in using the provided UI Bridge API parameters.
     * @param frontdoorBridgeUrl The UI Bridge API front door bridge API
     * @param pkceCodeVerifier The PKCE code verifier
     */
    fun loginWithFrontDoorBridgeUrl(
        frontdoorBridgeUrl: String,
        pkceCodeVerifier: String?,
    ) {
        isUsingFrontDoorBridge = true
        frontdoorBridgeServer = with(URI(frontdoorBridgeUrl)) { "${scheme}://${host}" }
        frontdoorBridgeCodeVerifier = pkceCodeVerifier
        loginUrl.value = frontdoorBridgeUrl
    }

    /**
     * The name to be shown for account in Settings -> Accounts & Sync
     * @return name to be shown for account in Settings -> Accounts & Sync
     */
    protected open fun buildAccountName(
        username: String?,
        instanceServer: String?,
    ) = defaultBuildAccountName(username, instanceServer)

    /**
     * Applies a new pending login server.  The decision to authenticate in a
     * web browser custom tab will be made, which may require fetching the
     * authentication configuration.  The selected server and login URL (OAuth
     * authorization URL) will set to continue the flow.
     * @param sdkManager The Salesforce SDK manager.  This parameter is intended
     * for testing purposes only. Defaults to the shared instance
     * @param pendingLoginServer The new pending login server value
     */
    internal fun applyPendingServer(
        sdkManager: SalesforceSDKManager = SalesforceSDKManager.getInstance(),
        pendingLoginServer: String?
    ) {
        val pendingLoginServerUnwrapped: String = pendingLoginServer ?: return

        // Recall this pending login server for reference by future updates.
        previousPendingServer = pendingLoginServerUnwrapped

        // When authorization via a single-server, custom tab activity is requested skip fetching the authorization configuration and immediately set the selected login server to generate the OAuth authorization URL.
        if (singleServerCustomTabActivity) {
            selectedServer.postValue(pendingLoginServerUnwrapped)
        }
        // Fetch the pending login server's authentication configuration to set the selected login server and OAuth authorization URL.
        else {
            authenticationConfigurationFetchJob?.cancel()
            authenticationConfigurationFetchJob = sdkManager.fetchAuthenticationConfiguration {
                selectedServer.postValue(pendingLoginServerUnwrapped)
                authenticationConfigurationFetchJob = null
            }
        }
    }

    /**
     * Called when the webview portion of the Web Server flow is finished.  Code exchange
     * passes the result to [onAuthFlowComplete] on success, which handles user creation.
     */
    internal fun onWebServerFlowComplete(
        code: String?,
        onAuthFlowError: (error: String, errorDesc: String?, e: Throwable?) -> Unit,
        onAuthFlowSuccess: (userAccount: UserAccount) -> Unit,
    ) = CoroutineScope(IO).launch {
        doCodeExchange(code, onAuthFlowError, onAuthFlowSuccess)
    }

    /**
     * Called when the webview portion of the User Agent flow or the code exchange
     * portion of the Web Server is finished to create and the user.
     */
    internal suspend fun onAuthFlowComplete(
        tr: TokenEndpointResponse,
        onAuthFlowError: (error: String, errorDesc: String?, e: Throwable?) -> Unit,
        onAuthFlowSuccess: (userAccount: UserAccount) -> Unit,
    ) {
        // Clear cookies when we finish auth to prevent automatic re-login
        // if the user tries to add another user right away.
        clearCookies()
        authCodeForJwtFlow = null
        onAuthFlowComplete(
            tokenResponse = tr,
            loginServer = selectedServer.value ?: "", // This will never actually be null.
            consumerKey = clientId,
            onAuthFlowError = onAuthFlowError,
            onAuthFlowSuccess = onAuthFlowSuccess,
            buildAccountName = ::buildAccountName,
        )
    }

    /**
     * Resets all state related to Salesforce Identity API UI Bridge front door bridge URL log in to
     * its default inactive state.
     */
    internal fun resetFrontDoorBridgeUrl() {
        isUsingFrontDoorBridge = false
        frontdoorBridgeServer = null
        frontdoorBridgeCodeVerifier = null
    }

    /**
     * Returns a valid HTTPS server URL or null if the provided user input is
     * invalid.
     * @param url The user input URL to validate and return
     * @return The validated server URL or null if the provided URL wasn't a
     * valid URL
     */
    internal fun getValidServerUrl(url: String): String? {
        if (!url.contains(".")) return null
        if (url.substringAfterLast(".").isEmpty()) return null
        runCatching {
            URI(url)
        }.onFailure {
            return null
        }

        return when {
            URLUtil.isHttpsUrl(url) -> url
            URLUtil.isHttpUrl(url) -> url.replace("http://", "https://")
            else -> "https://$url".toHttpUrlOrNull()?.toString()
        }?.removeSuffix("/")
    }

    private fun getAuthorizationUrl(server: String): String {
        val jwtFlow = !jwt.isNullOrBlank() && !authCodeForJwtFlow.isNullOrBlank()
        val additionalParams = when {
            jwtFlow -> null
            else -> additionalParameters
        }

        val codeVerifier = getRandom128ByteKey().also { codeVerifier = it }
        val codeChallenge = getSHA256Hash(codeVerifier)

        val authorizationUrl = OAuth2.getAuthorizationUrl(
            useWebServerFlow,
            SalesforceSDKManager.getInstance().useHybridAuthentication,
            URI(server),
            clientId,
            bootConfig.oauthRedirectURI,
            bootConfig.oauthScopes,
            loginHint,
            authorizationDisplayType,
            codeChallenge,
            additionalParams
        )

        // The Salesforce Welcome login hint is only used once.
        loginHint = null

        return when {
            jwtFlow -> getFrontdoorUrl(
                authorizationUrl,
                authCodeForJwtFlow,
                selectedServer.value,
                mapOf<String, String>()
            )

            else -> authorizationUrl
        }.toString()
    }

    private suspend fun doCodeExchange(
        code: String?,
        onAuthFlowError: (error: String, errorDesc: String?, e: Throwable?) -> Unit,
        onAuthFlowSuccess: (userAccount: UserAccount) -> Unit,
    ) = withContext(IO) {
        runCatching {
            val server = if (isUsingFrontDoorBridge) frontdoorBridgeServer else selectedServer.value
            val verifier = if (isUsingFrontDoorBridge) frontdoorBridgeCodeVerifier else codeVerifier

            val tokenResponse = exchangeCode(
                HttpAccess.DEFAULT,
                URI.create(server),
                clientId,
                code,
                verifier,
                bootConfig.oauthRedirectURI,
            )

            onAuthFlowComplete(tokenResponse, onAuthFlowError, onAuthFlowSuccess)
        }.onFailure { throwable ->
            e(TAG, "Exception occurred while making token request", throwable)
            onAuthFlowError("Token Request Error", throwable.message, throwable)
        }
    }

    // region Salesforce Welcome Discovery

    /**
     * Determines if the provided pending login server URL is a switch from
     * Salesforce Welcome Discovery back to default log in.
     * @param pendingLoginServerUri The pending login server URL
     * @return Boolean true if the provided pending login server URL is a
     * switch from Salesforce Welcome Discovery back to the default log in,
     * false otherwise.
     */
    internal fun isSwitchFromSalesforceWelcomeDiscoveryToDefaultLogin(
        pendingLoginServerUri: Uri
    ): Boolean {
        val previousPendingLoginServerUri = previousPendingServer ?: return false
        return isSalesforceWelcomeDiscoveryUrlPath(previousPendingLoginServerUri.toUri()).and(!isSalesforceWelcomeDiscoveryUrlPath(pendingLoginServerUri))
    }

    // endregion

    companion object {

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras,
            ): T {
                // Get the Application object from extras
                val application = checkNotNull(extras[APPLICATION_KEY])
                return LoginViewModel(BootConfig.getBootConfig(application.baseContext)) as T
            }
        }

        private const val TAG = "LoginViewModel"
    }

    /**
     * Models an additional bottom bar button the login view can display.
     * @param title The button's displayable title
     * @param onClick The button's on-click action
     */
    data class BottomBarButton(
        val title: String,
        val onClick: () -> Unit
    )

    /**
     * An observer used to set the web view's OAuth authorization URL from the
     * selected login server.
     * @param sdkManager The Salesforce SDK manager.  This parameter is intended
     * for testing purposes only. Defaults to the shared instance
     * @param viewModel The login activity view model. This parameter is
     * intended for testing purposes only. Defaults to this inner class receiver
     * @param scope The Coroutine scope.  This parameter is intended for testing
     * purposes only. Defaults to the IO scope
     */
    private inner class LoginUrlSource(
        private val sdkManager: SalesforceSDKManager = SalesforceSDKManager.getInstance(),
        private val viewModel: LoginViewModel = this@LoginViewModel,
        private val scope: CoroutineScope = viewModelScope,
    ) : Observer<String?> {
        override fun onChanged(value: String?) {
            if (!sdkManager.isBrowserLoginEnabled && !singleServerCustomTabActivity && !viewModel.isUsingFrontDoorBridge && value != null) {
                val valueUrl = value.toUri()
                val loginUrl = viewModel.loginUrl.value?.toUri()
                val isNewServer = loginUrl?.host != valueUrl.host || loginUrl?.path != valueUrl.path
                if (isNewServer) {
                    scope.launch {
                        viewModel.loginUrl.value = viewModel.getAuthorizationUrl(value)
                    }
                }
            }
        }
    }

    /**
     * An observer used to set the pending login server from the login server
     * manager's selected login server.
     * @param viewModel The login activity view model. This parameter is
     * intended for testing purposes only. Defaults to this inner class receiver
     */
    private inner class PendingServerSource(
        private val viewModel: LoginViewModel = this@LoginViewModel,
    ) : Observer<LoginServer> {
        override fun onChanged(value: LoginServer) {
            val loginServer = value.url.trim()
            if (viewModel.pendingServer.value == loginServer) {
                viewModel.reloadWebView()
            } else {
                viewModel.pendingServer.value = loginServer
            }
        }
    }

    /**
     * An observer used to set the external browser custom tab's OAuth
     * authorization URL from the selected login server.
     * @param sdkManager The Salesforce SDK manager.  This parameter is intended
     * for testing purposes only. Defaults to the shared instance
     * @param viewModel The login activity view model. This parameter is
     * intended for testing purposes only. Defaults to this inner class receiver
     * @param scope The Coroutine scope.  This parameter is intended for testing
     * purposes only. Defaults to the IO scope
     */
    private inner class BrowserCustomTabUrlSource(
        private val sdkManager: SalesforceSDKManager = SalesforceSDKManager.getInstance(),
        private val viewModel: LoginViewModel = this@LoginViewModel,
        private val scope: CoroutineScope = viewModelScope,
    ) : Observer<String> {
        override fun onChanged(value: String) {
            val useBrowserCustomTab = sdkManager.isBrowserLoginEnabled || singleServerCustomTabActivity
            if (useBrowserCustomTab && !viewModel.isUsingFrontDoorBridge) {
                scope.launch {
                    viewModel.browserCustomTabUrl.value = viewModel.getAuthorizationUrl(
                        server = value
                    )
                }
            }
        }
    }
}
