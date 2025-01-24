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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.security.KeyChain.getCertificateChain
import android.security.KeyChain.getPrivateKey
import android.security.KeyChainAliasCallback
import android.text.TextUtils.isEmpty
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Toast.LENGTH_LONG
import android.widget.Toast.makeText
import com.salesforce.androidsdk.R.string.oauth_display_type
import com.salesforce.androidsdk.R.string.sf__biometric_signout_user
import com.salesforce.androidsdk.R.string.sf__generic_authentication_error
import com.salesforce.androidsdk.R.string.sf__generic_authentication_error_title
import com.salesforce.androidsdk.R.string.sf__generic_error
import com.salesforce.androidsdk.R.string.sf__jwt_authentication_error
import com.salesforce.androidsdk.R.string.sf__managed_app_error
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
import com.salesforce.androidsdk.util.SalesforceSDKLogger.d
import com.salesforce.androidsdk.util.SalesforceSDKLogger.e
import com.salesforce.androidsdk.util.SalesforceSDKLogger.w
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request.Builder
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
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
 */
internal class OAuthWebviewHelper(
    val context: Context,
    private val callback: OAuthWebviewHelperEvents,
    val loginOptions: LoginOptions,
) : KeyChainAliasCallback {

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
    internal interface OAuthWebviewHelperEvents {

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

    val webView: WebView? = null

    private var accountOptions: Bundle? = null

    private val activity: Activity? = null

    private var key: PrivateKey? = null

    private var certChain: Array<X509Certificate>? = null

    private fun clearCookies() =
        CookieManager.getInstance().removeAllCookies(null)

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
    fun showError(exception: Throwable) {
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
    private fun loadLoginPage() = loginOptions.let { loginOptions ->
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
            val uri = getAuthorizationUrl(
                useWebServerAuthentication = instance.isBrowserLoginEnabled || instance.useWebServerAuthentication,
                useHybridAuthentication = instance.useHybridAuthentication
            )

            callback.loadingLoginPage(loginOptions.loginUrl)
            webView?.loadUrl(uri.toString())
        }.onFailure { throwable ->
            showError(throwable)
        }
    }

    private val oAuthClientId: String
        get() = loginOptions.oauthClientId

    private fun getAuthorizationUrl(
        useWebServerAuthentication: Boolean,
        useHybridAuthentication: Boolean
    ): URI {

        // Reset log in state,
        // - Salesforce Identity UI Bridge API log in, such as QR code login.
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
    private val authorizationDisplayType
        get() = context.getString(oauth_display_type)

    /** Override to customize the login url */
    val loginUrl: String?
        get() = SalesforceSDKManager.getInstance().loginServerManager.selectedLoginServer?.url?.run {
            trim { it <= ' ' }
        }

    /**
     * Called when the user facing part of the authentication flow completed
     * successfully. The last step is to call the identity service to get the
     * username.
     */
    fun onAuthFlowComplete(tr: TokenEndpointResponse?, nativeLogin: Boolean = false) {
        CoroutineScope(IO).launch {

            // Reset log in state,
            // - Salesforce Identity UI Bridge API log in, such as QR code login.
            resetFrontDoorBridgeUrl()

            FinishAuthTask().execute(tr, nativeLogin)
        }
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
    abstract inner class BaseFinishAuthFlowTask<Parameter> {

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
        return salesforceHosts.map { host.endsWith(it) }.any { it }
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
    fun buildAccountName(
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


        private const val TAG = "OAuthWebViewHelper"
    }
}
