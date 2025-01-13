package com.salesforce.androidsdk.ui

import android.text.TextUtils.isEmpty
import android.webkit.CookieManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.salesforce.androidsdk.R.string.oauth_display_type
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.auth.OAuth2
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse
import com.salesforce.androidsdk.auth.OAuth2.exchangeCode
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.security.SalesforceKeyGenerator.getRandom128ByteKey
import com.salesforce.androidsdk.security.SalesforceKeyGenerator.getSHA256Hash
import com.salesforce.androidsdk.util.SalesforceSDKLogger.e
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

open class LoginViewModel(val bootConfig: BootConfig): ViewModel() {

    // LiveData
    val selectedServer = MediatorLiveData<String>()
    val loginUrl = MediatorLiveData<String>()
    internal var dynamicBackgroundColor = mutableStateOf(Color.White)
    internal var dynamicHeaderTextColor = derivedStateOf { if (dynamicBackgroundColor.value.luminance() > 0.5) Color.Black else Color.White }
    internal var showServerPicker = mutableStateOf(false)
    internal val defaultTitleText: String
        get() = if (loginUrl.value == "about:blank") "" else selectedServer.value ?: ""

    // Public Overrideable LiveData
    open var showTopBar = true
    open var topBarColor: Color? = null
    open var titleText: String? = null
    open var titleComposable: (@Composable () -> Unit)? = null
    open var loading = mutableStateOf(false)

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
    val shouldShowBiometricPromptButton = with(SalesforceSDKManager.getInstance().biometricAuthenticationManager) {
        this?.let { locked && hasBiometricOptedIn() } ?: false
    }
    // todo: add shouldShowIDPButton

    // The default, locally generated code verifier
    private var codeVerifier: String? = null

    // For Salesforce Identity API UI Bridge support, indicates use of an overriding front door bridge URL
    // in place of the default initial URL
    internal var isUsingFrontDoorBridge = false

    // For Salesforce Identity API UI Bridge support, the optional web server flow code verifier accompanying
    // the front door bridge URL.  This can only be used with `overrideWithFrontDoorBridgeUrl`
    internal var frontDoorBridgeCodeVerifier: String? = null

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
            loginUrl.value = getAuthorizationUrl(newServer)
        }
    }

    open fun reloadWebview() {
        loginUrl.value = getAuthorizationUrl(selectedServer.value ?: return)
    }

    open fun clearCookies() =
        CookieManager.getInstance().removeAllCookies(null)

    /**
     * Automatically log in using the provided UI Bridge API parameters.
     * @param frontDoorBridgeUrl The UI Bridge API front door bridge API
     * @param pkceCodeVerifier The PKCE code verifier
     */
    fun loginWithFrontDoorBridgeUrl(
        frontDoorBridgeUrl: String,
        pkceCodeVerifier: String?,
    ) {
        isUsingFrontDoorBridge = true

        val uri = URI(frontDoorBridgeUrl)
        SalesforceSDKManager.getInstance().loginOptions.loginUrl = "${uri.scheme}://${uri.host}"
        frontDoorBridgeCodeVerifier = pkceCodeVerifier
        loginUrl.value = frontDoorBridgeUrl
    }

    private fun getAuthorizationUrl(server: String): String {
        clearCookies()

        val jwtFlow = !isEmpty(jwt)
        val additionalParams = when {
            jwtFlow -> null
            else -> additionalParameters
        }

        // NB code verifier / code challenge are only used when useWebServerAuthentication is true
        val codeVerifier = getRandom128ByteKey().also { codeVerifier = it }
        val codeChallenge = getSHA256Hash(codeVerifier)

        return OAuth2.getAuthorizationUrl(
            SalesforceSDKManager.getInstance().useWebServerAuthentication,
            SalesforceSDKManager.getInstance().useHybridAuthentication,
            URI(server),
            clientId,
            bootConfig.oauthRedirectURI,
            bootConfig.oauthScopes,
            authorizationDisplayType,
            codeChallenge,
            additionalParams
        ).toString()
    }

    /**
     * The name to be shown for account in Settings -> Accounts & Sync
     * @return name to be shown for account in Settings -> Accounts & Sync
     */
    protected open fun buildAccountName(
        username: String?,
        instanceServer: String?,
    ) = String.format(
        "%s (%s) (%s)", username, instanceServer,
        SalesforceSDKManager.getInstance().applicationName
    )

    internal fun onWebServerFlowComplete(
        code: String?,
        onAuthFlowError: (error: String, errorDesc: String?, e: Throwable?) -> Unit,
        onAuthFlowSuccess: (userAccount: UserAccount) -> Unit,
    ) = CoroutineScope(IO).launch {
        doCodeExchange(code, onAuthFlowError, onAuthFlowSuccess)
    }

    private suspend fun doCodeExchange(
        code: String?,
        onAuthFlowError: (error: String, errorDesc: String?, e: Throwable?) -> Unit,
        onAuthFlowSuccess: (userAccount: UserAccount) -> Unit,
    ) = withContext(IO) {
        runCatching {
            val tokenResponse = exchangeCode(
                HttpAccess.DEFAULT,
                URI.create(selectedServer.value),
                clientId,
                code,
                frontDoorBridgeCodeVerifier ?: codeVerifier,
                bootConfig.oauthRedirectURI,
            )

            onAuthFlowComplete(tokenResponse, onAuthFlowError, onAuthFlowSuccess)
        }.onFailure { throwable ->
            e(TAG, "Exception occurred while making token request", throwable)
            onAuthFlowError("Token Request Error", throwable.message, throwable)
        }
    }

    internal fun onAuthFlowComplete(
        tr: TokenEndpointResponse,
        onAuthFlowError: (error: String, errorDesc: String?, e: Throwable?) -> Unit,
        onAuthFlowSuccess: (userAccount: UserAccount) -> Unit,
    ) {
        com.salesforce.androidsdk.auth.onAuthFlowComplete(
            tokenResponse = tr,
            loginServer = selectedServer.value ?: "",
            consumerKey = clientId,
            buildAccountName = ::buildAccountName,
            onAuthFlowError = onAuthFlowError,
            onAuthFlowSuccess = onAuthFlowSuccess,
        )
    }

    /**
     * Resets all state related to Salesforce Identity API UI Bridge front door bridge URL log in to
     * its default inactive state.
     */
    internal fun resetFrontDoorBridgeUrl() {
        isUsingFrontDoorBridge = false
        frontDoorBridgeCodeVerifier = null
    }

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

                return LoginViewModel(BootConfig.getBootConfig(application.baseContext)) as T
            }
        }

        private const val TAG = "LoginViewModel"
    }
}