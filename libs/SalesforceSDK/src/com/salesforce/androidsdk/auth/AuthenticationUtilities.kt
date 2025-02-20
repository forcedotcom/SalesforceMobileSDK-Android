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
package com.salesforce.androidsdk.auth

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import com.salesforce.androidsdk.R.string.sf__generic_authentication_error
import com.salesforce.androidsdk.R.string.sf__generic_authentication_error_title
import com.salesforce.androidsdk.R.string.sf__managed_app_error
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.accounts.UserAccountManager.USER_SWITCH_TYPE_DEFAULT
import com.salesforce.androidsdk.accounts.UserAccountManager.USER_SWITCH_TYPE_FIRST_LOGIN
import com.salesforce.androidsdk.accounts.UserAccountManager.USER_SWITCH_TYPE_LOGIN
import com.salesforce.androidsdk.analytics.EventBuilderHelper.createAndStoreEventSync
import com.salesforce.androidsdk.analytics.SalesforceAnalyticsManager
import com.salesforce.androidsdk.app.Features.FEATURE_BIOMETRIC_AUTH
import com.salesforce.androidsdk.app.Features.FEATURE_SCREEN_LOCK
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.OAuth2.IdServiceResponse
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse
import com.salesforce.androidsdk.auth.OAuth2.addAuthorizationHeader
import com.salesforce.androidsdk.auth.OAuth2.revokeRefreshToken
import com.salesforce.androidsdk.config.RuntimeConfig.getRuntimeConfig
import com.salesforce.androidsdk.push.PushMessaging.register
import com.salesforce.androidsdk.rest.RestClient.clearCaches
import com.salesforce.androidsdk.security.BiometricAuthenticationManager
import com.salesforce.androidsdk.security.BiometricAuthenticationManager.Companion.isBiometricAuthenticationEnabled
import com.salesforce.androidsdk.security.ScreenLockManager
import com.salesforce.androidsdk.util.SalesforceSDKLogger.e
import com.salesforce.androidsdk.util.SalesforceSDKLogger.w
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request.Builder
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.function.Consumer

/**
 * Set a custom permission on the connected application with that name
 * for the application to be restricted to managed devices
 */
private const val MUST_BE_MANAGED_APP_PERM = "must_be_managed_app"

private const val TAG = "AuthenticationUtilities"

/**
 * Called when any (User Agent flow, Web Server after PKCE, Native Login, IDP, ect) authentication
 * method has received a [TokenEndpointResponse] from the server.
 *
 * This function:
 *  * Blocks integration and managed app users.
 *  * Retrieves the user's identity.
 *  * Creates an Account.
 *  * Checks for any CA/ECA settings such as Screen Lock or Biometric Authentication.
 */
internal fun onAuthFlowComplete(
    tokenResponse: TokenEndpointResponse,
    loginServer: String,
    consumerKey: String,
    onAuthFlowError: (error: String, errorDesc: String?, e: Throwable?) -> Unit,
    onAuthFlowSuccess: (userAccount: UserAccount) -> Unit,
    buildAccountName: (username: String?, instanceServer: String?) -> String = ::defaultBuildAccountName,
    nativeLogin: Boolean = false,
) {
    val context = SalesforceSDKManager.getInstance().appContext
    val userAccountManager = SalesforceSDKManager.getInstance().userAccountManager
    val blockIntegrationUser = SalesforceSDKManager.getInstance().shouldBlockSalesforceIntegrationUser &&
            fetchIsSalesforceIntegrationUser(tokenResponse, loginServer)

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

    var userIdentity: IdServiceResponse? = null
    runCatching {
        userIdentity = OAuth2.callIdentityService(
            HttpAccess.DEFAULT,
            tokenResponse.idUrlWithInstance,
            tokenResponse.authToken,
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

    val account = UserAccountBuilder.getInstance()
        .populateFromTokenEndpointResponse(tokenResponse)
        .populateFromIdServiceResponse(userIdentity)
        .accountName(buildAccountName(userIdentity?.username, tokenResponse.instanceUrl))
        .loginServer(loginServer)
        .clientId(consumerKey)
        .nativeLogin(nativeLogin)
        .build()
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
            userAccountManager.clearCachedCurrentUser()

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
        if (userIdentity?.biometricAuth == true) {
            existingUsers.forEach(Consumer { existingUser ->
                if (isBiometricAuthenticationEnabled(existingUser)) {
                    // This is an unexpected logout(s) because we only support one Bio Auth user.
                    userAccountManager.signoutUser(
                        existingUser, null, false, OAuth2.LogoutReason.UNEXPECTED
                    )
                }
            })
        }
    }

    // Save the user account
    addAccount(account)

    // Init user logging
    SalesforceAnalyticsManager.getInstance(account)?.updateLoggingPrefs()

    // Send User Switch Intent, create user and switch to user.
    val numAuthenticatedUsers = userAccountManager.authenticatedUsers?.size ?: 0
    val userSwitchType = when {
        // We've already authenticated the first user, so there should be one
        numAuthenticatedUsers == 1 -> USER_SWITCH_TYPE_FIRST_LOGIN

        // Otherwise we're logging in with an additional user
        numAuthenticatedUsers > 1 -> USER_SWITCH_TYPE_LOGIN

        // This should never happen but if it does, pass in the "unknown" value
        else -> USER_SWITCH_TYPE_DEFAULT
    }
    userAccountManager.sendUserSwitchIntent(userSwitchType, null)
    userAccountManager.createAccount(account)
    userAccountManager.switchToUser(account)

    // Kickoff the end of the flow before storing mobile policy to prevent launching
    // the main activity over/after the screen lock.
    with(SalesforceSDKManager.getInstance()) {
        appContext.startActivity(Intent(appContext, mainActivityClass).apply {
            setPackage(appContext.packageName)
            flags = FLAG_ACTIVITY_NEW_TASK
        })
    }

    // Let the calling process resume
    onAuthFlowSuccess(account)

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

internal fun defaultBuildAccountName(
    username: String?,
    instanceServer: String?,
) = String.format(
    "%s (%s) (%s)", username, instanceServer,
    SalesforceSDKManager.getInstance().applicationName
)

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
    tokenEndpointResponse: TokenEndpointResponse?,
    loginServer: String,
): Boolean {
    val baseUrl = tokenEndpointResponse?.instanceUrl ?: loginServer
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
    /*
     * Registers for push notifications if setup by the app. This step needs
     * to happen after the account has been added by client manager, so that
     * the push service has all the account info it needs.
     */
    register(SalesforceSDKManager.getInstance().appContext, account)

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
