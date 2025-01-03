package com.salesforce.androidsdk.auth

import android.text.TextUtils.isEmpty
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
import com.salesforce.androidsdk.R.string.sf__generic_authentication_error
import com.salesforce.androidsdk.R.string.sf__generic_authentication_error_title
import com.salesforce.androidsdk.R.string.sf__managed_app_error
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.app.Features.FEATURE_BIOMETRIC_AUTH
import com.salesforce.androidsdk.app.Features.FEATURE_SCREEN_LOCK
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.OAuth2.IdServiceResponse
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse
import com.salesforce.androidsdk.auth.OAuth2.addAuthorizationHeader
import com.salesforce.androidsdk.auth.OAuth2.exchangeCode
import com.salesforce.androidsdk.auth.OAuth2.revokeRefreshToken
import com.salesforce.androidsdk.config.RuntimeConfig.getRuntimeConfig
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions
import com.salesforce.androidsdk.rest.RestClient.clearCaches
import com.salesforce.androidsdk.security.BiometricAuthenticationManager
import com.salesforce.androidsdk.security.BiometricAuthenticationManager.Companion.isBiometricAuthenticationEnabled
import com.salesforce.androidsdk.security.SalesforceKeyGenerator.getRandom128ByteKey
import com.salesforce.androidsdk.security.SalesforceKeyGenerator.getSHA256Hash
import com.salesforce.androidsdk.security.ScreenLockManager
import com.salesforce.androidsdk.ui.OAuthWebviewHelper.Companion.MUST_BE_MANAGED_APP_PERM
import com.salesforce.androidsdk.util.SalesforceSDKLogger.e
import com.salesforce.androidsdk.util.SalesforceSDKLogger.w
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request.Builder
import org.json.JSONObject
import java.net.URI
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.analytics.EventBuilderHelper.createAndStoreEventSync
import com.salesforce.androidsdk.push.PushMessaging.register
import com.salesforce.androidsdk.rest.ClientManager
import org.json.JSONArray

open class LoginViewModel(
    var loginOptions: LoginOptions,
    savedStateHandle: SavedStateHandle, // I assume this is needed to save state (url?) between app runs
) : ViewModel() {

    /** Override to customize the login url */
    open val selectedServer: String?
        get() = SalesforceSDKManager.getInstance().loginServerManager.selectedLoginServer?.url?.run {
            trim { it <= ' ' }
        }
//    open val selectedServer: String?
//        get() = "login.salesforce.com"
    open val loginUrl: MutableLiveData<String> by lazy {
//        MutableLiveData<String>(tempSelectedServer.value)
        MutableLiveData<String>(getAuthorizationUrl())
    }

//    getAuthorizationUrl(
//    useWebServerAuthentication = SalesforceSDKManager.getInstance().isBrowserLoginEnabled
//    || SalesforceSDKManager.getInstance().useWebServerAuthentication,
//    useHybridAuthentication = SalesforceSDKManager.getInstance().useHybridAuthentication
//    )

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

    /** The default, locally generated code verifier */
    private var codeVerifier: String? = null


// TODO: get bio auth mgr and show button if necessary.  Also do the same for IDP
//    private bioAuthMgr =


    // endregion


    // from OAuthWebviewHelper

//    open fun clearCookies() =
//        CookieManager.getInstance().removeAllCookies(null)


    @Suppress("MemberVisibilityCanBePrivate")
    internal fun getAuthorizationUrl(): String {

        // Reset log in state,
        // - Salesforce Identity UI Bridge API log in, such as QR code login.
//        resetFrontDoorBridgeUrl()

        val jwtFlow = !isEmpty(loginOptions.jwt)
        val additionalParams = when {
            jwtFlow -> null
            else -> loginOptions.additionalParameters
        }

        // NB code verifier / code challenge are only used when useWebServerAuthentication is true
        val codeVerifier = getRandom128ByteKey().also { codeVerifier = it }
        val codeChallenge = getSHA256Hash(codeVerifier)
        val authorizationUrl = OAuth2.getAuthorizationUrl(
            SalesforceSDKManager.getInstance().useWebServerAuthentication,
            SalesforceSDKManager.getInstance().useHybridAuthentication,
//            URI(loginOptions.loginUrl),
            URI(selectedServer),
            oAuthClientId,
            loginOptions.oauthCallbackUrl,
            loginOptions.oauthScopes,
            authorizationDisplayType,
            codeChallenge,
            additionalParams
        )

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

        return authorizationUrl.toString()
    }

    internal fun onWebServerFlowComplete(
        code: String?,
        onAuthFlowError: (error: String, errorDesc: String?, e: Throwable?) -> Unit,
        onAuthFlowComplete: () -> Unit,
    ) =
        CoroutineScope(IO).launch {
            doCodeExchange(code, onAuthFlowError, onAuthFlowComplete)
        }

    private suspend fun doCodeExchange(
        code: String?,
        onAuthFlowError: (error: String, errorDesc: String?, e: Throwable?) -> Unit,
        onAuthFlowComplete: () -> Unit,
    ) = withContext(IO) {
//        var tokenResponse: TokenEndpointResponse? = null
        runCatching {
            val tokenResponse = exchangeCode(
                HttpAccess.DEFAULT,
                URI.create(selectedServer),
//                URI.create(activity.loginOptions.loginUrl),
                loginOptions.oauthClientId,
                code,
                // frontDoorBridgeCodeVerifier
                codeVerifier,
                loginOptions.oauthCallbackUrl
            )

            onAuthFlowComplete(tokenResponse, onAuthFlowError)
            onAuthFlowComplete()
        }.onFailure { throwable ->
            e(TAG, "Exception occurred while making token request", throwable)
            onAuthFlowError("Token Request Error", throwable.message, throwable)
        }

        // TODO: move all of the activity on auth flow complete to this viewmodel
//        onAuthFlowComplete(tokenResponse)
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

    /**
     * A factory method for the web Chrome client. This can be overridden as
     * needed
     *
     * TODO: deprecate and rename this to `makeBrowserClient`? (since it is not chrome specific)
     */
    @Suppress("MemberVisibilityCanBePrivate")
    protected open fun makeWebChromeClient() = WebChromeClient()


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

        val clientBuilder = HttpAccess.DEFAULT.okHttpClient.newBuilder()
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
            SalesforceSDKManager.getInstance().appContext,
            SalesforceSDKManager.getInstance().accountType,
            loginOptions,
            SalesforceSDKManager.getInstance().shouldLogoutWhenTokenRevoked(),
        )

        // New account
        val extras = clientManager.createNewAccount(account)

        /*
         * Registers for push notifications if setup by the app. This step needs
         * to happen after the account has been added by client manager, so that
         * the push service has all the account info it needs.
         */
        register(SalesforceSDKManager.getInstance().appContext, account)

//        callback.onAccountAuthenticatorResult(extras)

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

    // end of from OAuthWebviewHelper

    internal fun onAuthFlowComplete(
        tr: TokenEndpointResponse,
        onAuthFlowError: (error: String, errorDesc: String?, e: Throwable?) -> Unit,
    ) {
        val context = SalesforceSDKManager.getInstance().appContext
        val blockIntegrationUser = SalesforceSDKManager.getInstance().shouldBlockSalesforceIntegrationUser &&
                fetchIsSalesforceIntegrationUser(tr)

        if (blockIntegrationUser) {
            /*
             * Salesforce integration users are prohibited from successfully
             * completing authentication. This alleviates the Restricted
             * Product Approval requirement on Salesforce Integration add-on
             * SKUs and conforms to Legal and Product Strategy requirements
             */
            w(TAG, "Salesforce integration users are prohibited from successfully authenticating.")
            onAuthFlowError( // Issue the generic authentication error
                context.getString(sf__generic_authentication_error_title),
                context.getString(sf__generic_authentication_error), null
            )

            return
        }

        // do we need to suspend?
        var userIdentity: IdServiceResponse? = null
        runCatching {
            userIdentity = OAuth2.callIdentityService(
                HttpAccess.DEFAULT,
                tr.idUrlWithInstance,
                tr.authToken,
            )
        }

        val mustBeManagedApp = userIdentity?.customPermissions?.optBoolean(MUST_BE_MANAGED_APP_PERM) ?: false
        if (mustBeManagedApp && !getRuntimeConfig(context).isManagedApp) {
            onAuthFlowError(
                context.getString(sf__generic_authentication_error_title),
                context.getString(sf__managed_app_error), null
            )
            return
        }

        // TODO:  loginOptions was used for loginUrl and clientId
        val account = UserAccountBuilder.getInstance()
            .populateFromTokenEndpointResponse(tr)
            .populateFromIdServiceResponse(userIdentity)
            .accountName(buildAccountName(userIdentity?.username, tr.instanceUrl))
            .loginServer("login.salesforce.com")
            .clientId("")
            .build()

//        accountOptions = account.toBundle()

        account.downloadProfilePhoto()

        // Set additional administrator prefs if they exist
        userIdentity?.customAttributes?.let { customAttributes ->
            SalesforceSDKManager.getInstance().adminSettingsManager?.setPrefs(customAttributes, account)
        }

        userIdentity?.customPermissions?.let { customPermissions ->
            SalesforceSDKManager.getInstance().adminPermsManager?.setPrefs(customPermissions, account)
        }

        SalesforceSDKManager.getInstance().userAccountManager.authenticatedUsers?.let { existingUsers ->
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
                                HttpAccess.DEFAULT,
                                uri,
                                duplicateUserAccount.refreshToken,
                                OAuth2.LogoutReason.REFRESH_TOKEN_ROTATED,
                            )
                        }
                    }
                }
            }

            // If this account has biometric authentication enabled remove any others that also have it
//            if (userIdentity?.biometricAuth == true) {
//                existingUsers.forEach(Consumer { existingUser ->
//                    if (isBiometricAuthenticationEnabled(existingUser)) {
//
//                        // TODO: test this
//                        (context as Activity).runOnUiThread {
//                            makeText(
//                                context,
//                                context.getString(
//                                    sf__biometric_signout_user,
//                                    existingUser.username
//                                ),
//                                LENGTH_LONG
//                            ).show()
//                        }
//                        // This is an unexpected logout(s) because we only support one Bio Auth user.
//                        SalesforceSDKManager.getInstance().userAccountManager.signoutUser(
//                            existingUser, activity, false, OAuth2.LogoutReason.UNEXPECTED
//                        )
//                    }
//                })
//            }
        }

        // Save the user account
        addAccount(account)

        // Screen lock required by mobile policy
        if (userIdentity?.screenLockTimeout?.compareTo(0) == 1) {
            SalesforceSDKManager.getInstance().registerUsedAppFeature(FEATURE_SCREEN_LOCK)
            val timeoutInMills = (userIdentity?.screenLockTimeout ?: 0) * 1000 * 60
            (SalesforceSDKManager.getInstance().screenLockManager as ScreenLockManager?)?.storeMobilePolicy(
                account,
                userIdentity?.screenLock ?: false,
                timeoutInMills
            )
        }

        // Biometric authorization required by mobile policy
        if (userIdentity?.biometricAuth == true) {
            SalesforceSDKManager.getInstance().registerUsedAppFeature(FEATURE_BIOMETRIC_AUTH)
            val timeoutInMills = (userIdentity?.biometricAuthTimeout ?: 0) * 60 * 1000
            (SalesforceSDKManager.getInstance().biometricAuthenticationManager as BiometricAuthenticationManager?)?.storeMobilePolicy(
                account,
                userIdentity?.biometricAuth ?: false,
                timeoutInMills
            )
        }

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