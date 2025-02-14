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

import android.webkit.CookieManager
import android.webkit.URLUtil
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
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
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.security.SalesforceKeyGenerator.getRandom128ByteKey
import com.salesforce.androidsdk.security.SalesforceKeyGenerator.getSHA256Hash
import com.salesforce.androidsdk.ui.LoginActivity.Companion.ABOUT_BLANK
import com.salesforce.androidsdk.util.SalesforceSDKLogger.e
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI

open class LoginViewModel(val bootConfig: BootConfig) : ViewModel() {

    // LiveData
    val selectedServer = MediatorLiveData<String>()
    val loginUrl = MediatorLiveData<String>()
    internal val showBottomBarButtons = mutableStateOf(true)
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
    internal var dynamicBackgroundTheme = derivedStateOf { if (dynamicBackgroundColor.value.luminance() > 0.5) DARK else LIGHT }
    internal var dynamicHeaderTextColor = derivedStateOf { if (dynamicBackgroundColor.value.luminance() > 0.5) Black else White }
    internal var showServerPicker = mutableStateOf(false)
    internal val defaultTitleText: String
        get() = if (loginUrl.value == ABOUT_BLANK) "" else selectedServer.value ?: ""

    // Public Overrideable LiveData
    open var showTopBar = true
    open var topBarColor: Color? = null
    open var titleText: String? = null
    open var titleComposable: (@Composable () -> Unit)? = null
    open var loading = mutableStateOf(false)

    /**
     * A custom button to display on the login view bottom app bar.  Note: If
     * biometric authentication is enabled and locked that button will be
     * displayed first.  Also, if IDP authentication is enabled that would also
     * display before the custom button.
     */
    open val customBottomBarButton = mutableStateOf<LoginAdditionalButton?>(null)

    // Additional Auth Values
    protected open var clientId: String = bootConfig.remoteAccessConsumerKey
    protected open val authorizationDisplayType = SalesforceSDKManager.getInstance().appContext.getString(oauth_display_type)

    /**
     * Setting this option to true will enable a mode where only a custom tab will be shown.  The first server will be
     * launched in a custom tab immediately and the user will not be able to switch servers.  The LoginActivity is
     * ended if the user attempts to back out of the custom tab.
     *
     * Since browser based authentication requires PKCE (and therefore the Web Server flow) the User Agent flow
     * cannot be used while in this mode.
     */
    open val singleServerCustomTabActivity = false

    // LoginOptions values
    var jwt: String? = null
    var additionalParameters = hashMapOf<String, String>()

    val shouldShowBackButton = with(SalesforceSDKManager.getInstance()) {
        !(userAccountManager.authenticatedUsers.isNullOrEmpty() || biometricAuthenticationManager?.locked ?: false)
    }

    // The default, locally generated code verifier
    @VisibleForTesting
    internal var codeVerifier: String? = null

    // Auth code we receive from the JWT swap for magic links.
    internal var authCodeForJwtFlow: String? = null

    // For Salesforce Identity API UI Bridge support, indicates use of an overriding front door bridge URL.
    internal var isUsingFrontDoorBridge = false

    // The optional server used for code exchange.
    internal var frontdoorBridgeServer: String? = null

    // The optional web server flow code verifier accompanying the front door bridge server.
    internal var frontdoorBridgeCodeVerifier: String? = null


    init {
        // Update selectedServer when the LoginServerManager value changes
        selectedServer.addSource(SalesforceSDKManager.getInstance().loginServerManager.selectedServer) { newServer ->
            val trimmedServer = newServer.url.run { trim { it <= ' ' } }
            if (selectedServer.value == trimmedServer) {
                reloadWebview()
            } else {
                selectedServer.value = trimmedServer
            }
        }

        // Update loginUrl when selectedServer updates so webview automatically reloads
        loginUrl.addSource(selectedServer) { newServer ->
            val isNewServer = loginUrl.value?.startsWith(newServer) != true
            if (isNewServer && !isUsingFrontDoorBridge) {
                loginUrl.value = getAuthorizationUrl(newServer)
            }
        }
    }

    open fun reloadWebview() {
        if (!isUsingFrontDoorBridge) {
            loginUrl.value = getAuthorizationUrl(selectedServer.value ?: return)
        }
    }

    open fun clearCookies() =
        CookieManager.getInstance().removeAllCookies(null)

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
    internal fun onAuthFlowComplete(
        tr: TokenEndpointResponse,
        onAuthFlowError: (error: String, errorDesc: String?, e: Throwable?) -> Unit,
        onAuthFlowSuccess: (userAccount: UserAccount) -> Unit,
    ) {
        // Clear cookies when we finish auth to prevent automatic re-login
        // if the user tries to add another user right away.
        clearCookies()
        authCodeForJwtFlow = null
        com.salesforce.androidsdk.auth.onAuthFlowComplete(
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

    // returns a valid https server url or null if the users input is invalid.
    internal fun getValidServerUrl(url: String): String? {
        if (!url.contains(".")) return null
        if (url.substringAfterLast(".").isEmpty()) return null

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

        // NB code verifier / code challenge are only used when useWebServerAuthentication is true
        val codeVerifier = getRandom128ByteKey().also { codeVerifier = it }
        val codeChallenge = getSHA256Hash(codeVerifier)

        val authorizationUrl = OAuth2.getAuthorizationUrl(
            SalesforceSDKManager.getInstance().useWebServerAuthentication,
            SalesforceSDKManager.getInstance().useHybridAuthentication,
            URI(server),
            clientId,
            bootConfig.oauthRedirectURI,
            bootConfig.oauthScopes,
            authorizationDisplayType,
            codeChallenge,
            additionalParams
        )

        return when {
            jwtFlow -> getFrontdoorUrl(authorizationUrl, authCodeForJwtFlow, selectedServer.value, mapOf<String, String>())
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
    data class LoginAdditionalButton(
        @StringRes
        val title: Int,
        val onClick: () -> Unit
    )
}
