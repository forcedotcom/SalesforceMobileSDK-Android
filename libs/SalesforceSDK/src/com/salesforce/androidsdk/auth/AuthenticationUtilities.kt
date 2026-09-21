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

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Bundle
import androidx.annotation.VisibleForTesting
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
import com.salesforce.androidsdk.app.Features.FEATURE_AUTH_TYPE_NATIVE
import com.salesforce.androidsdk.app.Features.FEATURE_AUTH_TYPE_USER_AGENT_HYBRID
import com.salesforce.androidsdk.app.Features.FEATURE_AUTH_TYPE_USER_AGENT_NON_HYBRID
import com.salesforce.androidsdk.app.Features.FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID
import com.salesforce.androidsdk.app.Features.FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID
import com.salesforce.androidsdk.app.Features.FEATURE_BEACON
import com.salesforce.androidsdk.app.Features.FEATURE_BIOMETRIC_AUTH
import com.salesforce.androidsdk.app.Features.FEATURE_DPOP
import com.salesforce.androidsdk.app.Features.FEATURE_RTR
import com.salesforce.androidsdk.app.Features.FEATURE_SCREEN_LOCK
import com.salesforce.androidsdk.app.Features.FEATURE_TOKEN_FORMAT_JWT
import com.salesforce.androidsdk.app.Features.FEATURE_TOKEN_FORMAT_OPAQUE
import com.salesforce.androidsdk.app.Features.FEATURE_TOKEN_MIGRATION
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.app.SalesforceSDKManager.Companion.encryptionKey
import com.salesforce.androidsdk.auth.dpop.DPoPKeyManager
import com.salesforce.androidsdk.auth.dpop.DPoPRequestDecorator
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse
import com.salesforce.androidsdk.auth.OAuth2.addAuthorizationHeader
import com.salesforce.androidsdk.auth.OAuth2.callIdentityService
import com.salesforce.androidsdk.config.LoginServerManager
import com.salesforce.androidsdk.config.RuntimeConfig
import com.salesforce.androidsdk.config.RuntimeConfig.getRuntimeConfig
import com.salesforce.androidsdk.push.PushMessaging.register
import com.salesforce.androidsdk.rest.RestClient.clearCaches
import com.salesforce.androidsdk.security.BiometricAuthenticationManager
import com.salesforce.androidsdk.security.BiometricAuthenticationManager.Companion.isBiometricAuthenticationEnabled
import com.salesforce.androidsdk.security.ScreenLockManager
import com.salesforce.androidsdk.util.SalesforceSDKLogger.e
import com.salesforce.androidsdk.util.SalesforceSDKLogger.i
import com.salesforce.androidsdk.util.SalesforceSDKLogger.w
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Request.Builder
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.net.URI
import java.time.Instant
import java.util.function.Consumer

/**
 * Set a custom permission on the connected application with that name
 * for the application to be restricted to managed devices
 */
private const val MUST_BE_MANAGED_APP_PERM = "must_be_managed_app"

private const val TAG = "AuthenticationUtilities"
private const val BAD_OAUTH_TOKEN = "Bad_OAuth_Token"
private const val WRONG_ORG = "Wrong_Org"

@VisibleForTesting
internal data class IdentityFetchResult(
    val identity: OAuth2.IdServiceResponse?,
    val refreshTokenRotationTime: String? = null,
)

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
@VisibleForTesting
internal suspend fun onAuthFlowComplete(
    tokenResponse: TokenEndpointResponse,
    loginServer: String,
    consumerKey: String,
    redirectUri: String? = null,
    onAuthFlowError: (error: String, errorDesc: String?, e: Throwable?) -> Unit,
    onAuthFlowSuccess: (userAccount: UserAccount) -> Unit,
    buildAccountName: (username: String?, instanceServer: String?) -> String = ::defaultBuildAccountName,
    nativeLogin: Boolean = false,
    tokenMigration: Boolean = false,
    credentialsIdentifier: String? = null,
    context: Context = SalesforceSDKManager.getInstance().appContext,
    userAccountManager: UserAccountManager = SalesforceSDKManager.getInstance().userAccountManager,
    blockIntegrationUser: Boolean = (SalesforceSDKManager.getInstance().shouldBlockSalesforceIntegrationUser &&
            fetchIsSalesforceIntegrationUser(tokenResponse, loginServer)),
    runtimeConfig: RuntimeConfig = getRuntimeConfig(SalesforceSDKManager.getInstance().appContext),
    updateLoggingPrefs: (account: UserAccount) -> Unit = ::updateLoggingPrefsHelper,
    fetchUserIdentity: (suspend (tokenResponse: TokenEndpointResponse) -> OAuth2.IdServiceResponse?)? = null,
    fetchUserIdentityResult: (suspend (tokenResponse: TokenEndpointResponse) -> IdentityFetchResult)? = null,
    startMainActivity: () -> Unit = ::startMainActivityHelper,
    setAdministratorPreferences: (userIdentity: OAuth2.IdServiceResponse?, account: UserAccount) -> Unit = ::setAdministratorPreferences,
    addAccount: (account: UserAccount) -> Unit = ::addAccountHelper,
    handleScreenLockPolicy: (userIdentity: OAuth2.IdServiceResponse?, account: UserAccount) -> Unit = ::handleScreenLockPolicy,
    handleBiometricAuthPolicy: (userIdentity: OAuth2.IdServiceResponse?, account: UserAccount) -> Unit = ::handleBiometricAuthPolicy,
    handleDuplicateUserAccount: (userAccountManager: UserAccountManager, account: UserAccount, userIdentity: OAuth2.IdServiceResponse?) -> Unit
        = { uam, acct, identity -> com.salesforce.androidsdk.auth.handleDuplicateUserAccount(uam, acct, identity) },
    onAuthFlowFinished: (proceed: () -> Unit) -> Unit = { proceed -> proceed() },
) {
    // Reset Dev Support LoginOptionsActivity override
    SalesforceSDKManager.getInstance().debugOverrideAppConfig = null

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

    // Create a ScopeParser from tokenResponse.scope
    val scopeParser = ScopeParser(tokenResponse.scope)
    
    // Check that it has the refresh token scope, only warns if it is missing
    if (!scopeParser.hasRefreshTokenScope()) {
        w(TAG, "Missing refresh token scope.")
    }

    val identityResult = try {
        when {
            fetchUserIdentityResult != null -> fetchUserIdentityResult(tokenResponse)
            fetchUserIdentity != null -> IdentityFetchResult(fetchUserIdentity(tokenResponse))
            else -> fetchUserIdentityWithRetry(tokenResponse, loginServer, consumerKey)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        w(TAG, "Cannot complete authentication because user identity could not be retrieved.", e)
        onAuthFlowError(
            context.getString(sf__generic_authentication_error_title),
            context.getString(sf__generic_authentication_error),
            e,
        )
        return
    }
    val userIdentity = identityResult.identity
    if (userIdentity?.username.isNullOrBlank()) {
        val error = IllegalStateException("Identity service did not return a user identity")
        w(TAG, "Cannot complete authentication because user identity is missing.", error)
        onAuthFlowError(
            context.getString(sf__generic_authentication_error_title),
            context.getString(sf__generic_authentication_error),
            error,
        )
        return
    }
    val mustBeManagedApp = userIdentity?.customPermissions?.optBoolean(MUST_BE_MANAGED_APP_PERM) ?: false
    if (mustBeManagedApp && !runtimeConfig.isManagedApp) {
        onAuthFlowError(
            context.getString(sf__generic_authentication_error_title),
            context.getString(sf__managed_app_error), null
        )
        return
    }

    val accountBuilder = UserAccountBuilder.getInstance()
        .populateFromTokenEndpointResponse(tokenResponse)
        .populateFromIdServiceResponse(userIdentity)
        .accountName(buildAccountName(userIdentity?.username, tokenResponse.instanceUrl))
        .loginServer(loginServer)
        .clientId(consumerKey)
        .redirectUri(redirectUri)
        .nativeLogin(nativeLogin)
        .credentialsIdentifier(credentialsIdentifier)
    identityResult.refreshTokenRotationTime?.let(accountBuilder::lastTokenRotationTime)
    val account = accountBuilder.build()

    // Set additional administrator prefs if they exist
    setAdministratorPreferences(userIdentity, account)

    // Handle duplicate user account scenarios
    handleDuplicateUserAccount(userAccountManager, account, userIdentity)

    // Save the user account
    addAccount(account)

    if (tokenMigration) {
        userAccountManager.persistAccount(account)

        // TM: mark that this user was migrated; clear global residue so it does not bleed.
        SalesforceSDKManager.getInstance().registerUsedAppFeature(FEATURE_TOKEN_MIGRATION, account)
        SalesforceSDKManager.getInstance().unregisterUsedAppFeature(FEATURE_TOKEN_MIGRATION)

        // JT/OT: token format may change with new credentials
        if (account.tokenFormat == "jwt") {
            SalesforceSDKManager.getInstance().registerUsedAppFeature(FEATURE_TOKEN_FORMAT_JWT, account)
            SalesforceSDKManager.getInstance().unregisterUsedAppFeature(FEATURE_TOKEN_FORMAT_OPAQUE, account)
        } else {
            SalesforceSDKManager.getInstance().registerUsedAppFeature(FEATURE_TOKEN_FORMAT_OPAQUE, account)
            SalesforceSDKManager.getInstance().unregisterUsedAppFeature(FEATURE_TOKEN_FORMAT_JWT, account)
        }

        // BN: beacon child app
        if (account.beaconChildConsumerKey != null) {
            SalesforceSDKManager.getInstance().registerUsedAppFeature(FEATURE_BEACON, account)
        } else {
            SalesforceSDKManager.getInstance().unregisterUsedAppFeature(FEATURE_BEACON, account)
        }

        // DP: DPoP-bound session. Token migration bypasses LoginActivity.onAuthFlowSuccess (the
        // usual site of this marker), so an in-place upgrade to DPoP would otherwise never advertise
        // the flag. tokenType is a per-session property, so mirror it onto the migrated account here.
        if (DPoPKeyManager.isDPoPTokenType(account.tokenType)) {
            SalesforceSDKManager.getInstance().registerUsedAppFeature(FEATURE_DPOP, account)
        } else {
            SalesforceSDKManager.getInstance().unregisterUsedAppFeature(FEATURE_DPOP, account)
        }
    } else {
        if (nativeLogin) {
            // Native login bypasses LoginActivity.onAuthFlowSuccess, so A-marker per-user
            // promotion and JT/OT/BN/TM writes must happen here.
            val sdkManager = SalesforceSDKManager.getInstance()
            val allAMarkers = listOf(
                FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID,
                FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID,
                FEATURE_AUTH_TYPE_USER_AGENT_NON_HYBRID,
                FEATURE_AUTH_TYPE_USER_AGENT_HYBRID,
                FEATURE_AUTH_TYPE_NATIVE,
            )
            val activeAMarker = allAMarkers.firstOrNull { sdkManager.isGlobalFeatureRegistered(it) }
            for (marker in allAMarkers) {
                sdkManager.unregisterUsedAppFeature(marker)
                if (marker == activeAMarker) {
                    sdkManager.registerUsedAppFeature(marker, account)
                } else {
                    sdkManager.unregisterUsedAppFeature(marker, account)
                }
            }
            sdkManager.unregisterUsedAppFeature(FEATURE_TOKEN_MIGRATION, account)
            if (account.tokenFormat == "jwt") {
                sdkManager.registerUsedAppFeature(FEATURE_TOKEN_FORMAT_JWT, account)
                sdkManager.unregisterUsedAppFeature(FEATURE_TOKEN_FORMAT_OPAQUE, account)
            } else {
                sdkManager.registerUsedAppFeature(FEATURE_TOKEN_FORMAT_OPAQUE, account)
                sdkManager.unregisterUsedAppFeature(FEATURE_TOKEN_FORMAT_JWT, account)
            }
            if (account.beaconChildConsumerKey != null) {
                sdkManager.registerUsedAppFeature(FEATURE_BEACON, account)
            } else {
                sdkManager.unregisterUsedAppFeature(FEATURE_BEACON, account)
            }
        }
        userAccountManager.createAccount(account)
        userAccountManager.switchToUser(account)

        // Init user logging
        updateLoggingPrefs(account)

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
    }

    // Registration persists the per-user feature flag, so it must happen only after the account
    // has been created or updated above. The timestamp was included in the primary persistence.
    if (identityResult.refreshTokenRotationTime != null) {
        SalesforceSDKManager.getInstance().registerUsedAppFeature(FEATURE_RTR, account)
    }

    /*
     * Register for push notifications if setup by the app. This must happen
     * after the account has been persisted (createAccount/persistAccount
     * above), because the push registration worker re-resolves the target
     * account from AccountManager by org id and user id; enqueuing before the
     * account is written races the persistence and makes that re-resolution
     * fail.
     */
    register(context, account)

    // Let the calling process resume. This is the terminal outcome of the auth flow: the
    // account is already created, so any failure in the finalization work below (biometric/
    // screen-lock policy, launching the main activity) must not be reported back to
    // onAuthFlowError/the caller's exception handling — that would turn an already-succeeded
    // login into a reported failure, and callers that resume a single-shot continuation from
    // both onAuthFlowSuccess and a catch around this call would crash resuming it twice.
    onAuthFlowSuccess(account)

    try {
        // Biometric authorization required by mobile policy.  This must run before
        // onAuthFlowFinished so the biometric opt-in dialog's presentation decision
        // (which depends on the freshly-stored policy) can be made while the caller
        // is still in front, i.e. before startMainActivity() below occludes it.
        handleBiometricAuthPolicy(userIdentity, account)

        withContext(Dispatchers.Main) {
            onAuthFlowFinished {
                // Kickoff the end of the flow before storing mobile policy to prevent launching
                // the main activity over/after the screen lock.
                if (!tokenMigration) {
                    startMainActivity()
                }

                // Screen lock required by mobile policy
                handleScreenLockPolicy(userIdentity, account)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        w(TAG, "Post-login finalization failed after successful authentication.", e)
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
@VisibleForTesting
@Throws(Exception::class)
internal fun fetchIsSalesforceIntegrationUser(
    tokenEndpointResponse: TokenEndpointResponse?,
    loginServer: String,
): Boolean {
    val authToken = tokenEndpointResponse?.authToken
    val tokenType = tokenEndpointResponse?.tokenType
    val credentialsIdentifier = tokenEndpointResponse?.credentialsIdentifier
    val baseUrl = tokenEndpointResponse?.instanceUrl ?: loginServer
    val userInfoEndpoint = "$baseUrl/services/oauth2/userinfo"

    fun buildAuthenticatedRequest(): Request {
        val builder: Builder = Builder().url(userInfoEndpoint).get()
        attachAuthHeaders(builder, authToken, tokenType, credentialsIdentifier)
        return builder.build()
    }

    val client = HttpAccess.DEFAULT.okHttpClient.newBuilder()
        .addNetworkInterceptor { chain: Interceptor.Chain ->
            reattachAuthOnRedirect(
                chain, userInfoEndpoint, authToken, tokenType, credentialsIdentifier
            )
        }
        .build()

    var request = buildAuthenticatedRequest()
    var response = client.newCall(request).execute()
    val attachedDPoP = request.header(DPoPRequestDecorator.DPOP_HEADER) != null

    // The nonce is scoped to the host that actually issued it, which after a
    // cross-host Salesforce redirect (e.g. pool server -> instance) is response.request's
    // host, not the pre-redirect request's host.
    if (attachedDPoP) {
        DPoPRequestDecorator.harvestNonce(response, credentialsIdentifier, response.request.url.host)
    }

    /*
     * Cold nonce cache (e.g. after process restart): the server may return a
     * DPoP nonce challenge. Harvest the nonce once and retry with a rebuilt
     * proof, mirroring OAuth2.callIdentityService's nonce-retry handling.
     */
    if (attachedDPoP && DPoPRequestDecorator.isNonceChallenge(response)) {
        response.close()
        request = buildAuthenticatedRequest()
        response = client.newCall(request).execute()
        DPoPRequestDecorator.harvestNonce(response, credentialsIdentifier, response.request.url.host)
    }

    val responseString = response.body.string()
    if (!response.isSuccessful) {
        throw IOException(
            "Integration user check failed with HTTP status ${response.code}: $responseString"
        )
    }

    return try {
        JSONObject(responseString).getBoolean("is_salesforce_integration_user")
    } catch (jsonException: JSONException) {
        throw IOException(
            "Could not parse integration user response as JSON: $responseString",
            jsonException,
        )
    }
}

/**
 * Attaches the Authorization header and, for a DPoP-bound credential, a
 * signed DPoP proof to [builder]. Shared by the initial request and the
 * redirect-reattachment path so both stay in sync.
 */
private fun attachAuthHeaders(
    builder: Builder,
    authToken: String?,
    tokenType: String?,
    credentialsIdentifier: String?,
) {
    addAuthorizationHeader(builder, authToken, tokenType)
    DPoPRequestDecorator.attachProof(
        builder, credentialsIdentifier, tokenType, authToken
    )
}

/**
 * Network interceptor body for [fetchIsSalesforceIntegrationUser]. Re-attaches
 * the Authorization/DPoP headers when [chain]'s request URL no longer matches
 * [originalUrl] (i.e. we were redirected) and the new URL is a Salesforce host.
 */
@VisibleForTesting
internal fun reattachAuthOnRedirect(
    chain: Interceptor.Chain,
    originalUrl: String,
    authToken: String?,
    tokenType: String?,
    credentialsIdentifier: String?,
): Response {
    val url = chain.request().url
    val interceptedRequestBuilder = chain.request().newBuilder()

    // if the url no longer matches we were redirected
    if (url.toString() != originalUrl && url.isSalesforceUrl()) {
        attachAuthHeaders(
            interceptedRequestBuilder, authToken, tokenType, credentialsIdentifier
        )
    }

    return chain.proceed(interceptedRequestBuilder.build())
}

private fun HttpUrl.isSalesforceUrl(): Boolean {
    // List from https://help.salesforce.com/s/articleView?language=en_US&id=sf.domain_name_url_formats.htm&type=5
    val salesforceHosts = listOf(".salesforce.com", ".force.com", ".sfdcopens.com", ".site.com", ".lightning.com",
        ".salesforce-sites.com", ".force-user-content.com", ".salesforce-experience.com", ".salesforce-scrt.com")
    return salesforceHosts.map { host.endsWith(it) }.any { it }
}

private fun addAccount(account: UserAccount?, isTestRun: Boolean, loginServerManager: LoginServerManager) {

    // Download profile photo
    account?.downloadProfilePhoto()

    when {
        isTestRun -> logAddAccount(account, loginServerManager)
        else -> CoroutineScope(IO).launch {
            logAddAccount(account, loginServerManager)
        }
    }
}

/**
 * Log the addition of a new account.
 *
 * @param account The user account
 */
private fun logAddAccount(account: UserAccount?, loginServerManager: LoginServerManager) {
    val attributes = JSONObject()
    runCatching {
        val users = UserAccountManager.getInstance().authenticatedUsers
        attributes.put("numUsers", users?.size ?: 0)
        val servers = loginServerManager.loginServers
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
 * Fetches user identity using the URL appropriate for the login server.
 *
 * Salesforce always puts the pool-server host in the `id` field of the token response
 * regardless of which server issued the token. [TokenEndpointResponse.idUrlWithInstance]
 * corrects this by substituting the issuing server's host, which is correct for My Domain
 * logins and for Bearer tokens on any server.
 *
 * Workaround for
 * [W-23992239](https://gus.my.salesforce.com/lightning/r/ADM_Work__c/a07EE00002imeECYAY/view):
 * for DPoP tokens issued by a pool server, [TokenEndpointResponse.idUrlWithInstance] points
 * to My Domain, which rejects pool-server-issued DPoP tokens with `Bad_OAuth_Token` — it
 * does not issue a nonce challenge the way data endpoints do. The raw
 * [TokenEndpointResponse.idUrl] (pool-server host) must be used for the initial request instead.
 * If that request requires a credential refresh, the refreshed token is issued by the instance
 * token endpoint, so the replay uses [TokenEndpointResponse.idUrlWithInstance].
 *
 * Identity 401 responses and the known `Bad_OAuth_Token`/`Wrong_Org` 403 responses trigger one
 * refresh against the instance token endpoint. The complete refreshed credential state (including
 * a rotated refresh token) is applied before one replay. Other 403 responses are surfaced without
 * retry so authorization failures are not masked.
 */
@VisibleForTesting
internal suspend fun fetchUserIdentityWithRetry(
    tokenResponse: TokenEndpointResponse,
    loginServer: String,
    consumerKey: String,
    identityFetcher: suspend (url: String, response: TokenEndpointResponse) -> OAuth2.IdServiceResponse =
        ::fetchIdentityForAuthentication,
    tokenRefresher: suspend (
        response: TokenEndpointResponse,
        loginServer: String,
        consumerKey: String,
    ) -> TokenEndpointResponse = ::refreshCredentialsForIdentity,
): IdentityFetchResult {
    val initialUrl = if (
        "DPoP".equals(tokenResponse.tokenType, ignoreCase = true) &&
        LoginServerManager.isPoolServer(loginServer)
    ) tokenResponse.idUrl else tokenResponse.idUrlWithInstance

    try {
        return IdentityFetchResult(identityFetcher(initialUrl, tokenResponse))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (!isRefreshableIdentityFailure(e) || tokenResponse.refreshToken.isNullOrBlank()) {
            w(TAG, "Cannot fetch user identity due to an error.", e)
            throw e
        }

        val statusCode = (e as OAuth2.IdentityServiceException).httpStatusCode
        i(TAG, "Identity request returned HTTP $statusCode; refreshing credentials once.")
    }

    val originalRefreshToken = tokenResponse.refreshToken
    val refreshedResponse = tokenRefresher(tokenResponse, loginServer, consumerKey)
    val refreshTokenRotated = !refreshedResponse.refreshToken.isNullOrBlank() &&
        refreshedResponse.refreshToken != originalRefreshToken
    mergeRefreshedCredentials(tokenResponse, refreshedResponse)

    return try {
        IdentityFetchResult(
            identity = identityFetcher(tokenResponse.idUrlWithInstance, tokenResponse),
            refreshTokenRotationTime = if (refreshTokenRotated) Instant.now().toString() else null,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        w(TAG, "Cannot fetch user identity after refreshing credentials.", e)
        throw e
    }
}

private suspend fun fetchIdentityForAuthentication(
    url: String,
    response: TokenEndpointResponse,
): OAuth2.IdServiceResponse = withContext(IO) {
    callIdentityService(
        HttpAccess.DEFAULT,
        url,
        response.authToken,
        response.tokenType,
        response.credentialsIdentifier,
    )
}

private suspend fun refreshCredentialsForIdentity(
    response: TokenEndpointResponse,
    loginServer: String,
    consumerKey: String,
): TokenEndpointResponse = withContext(IO) {
    // Login has not created a UserAccount yet, so ClientManager's per-account refresh coordinator
    // cannot coordinate this request. This flow owns the response and permits exactly one refresh.
    // If Android gains a credential-scoped pre-account coordinator, route this refresh through it.
    OAuth2.refreshAuthToken(
        HttpAccess.DEFAULT,
        OAuth2.overrideLoginServerIfNeeded(
            loginServer,
            response.instanceUrl,
            response.communityId,
            response.communityUrl,
        ),
        consumerKey,
        checkNotNull(response.refreshToken) { "Cannot refresh identity without a refresh token" },
        response.additionalOauthValues,
        response.credentialsIdentifier,
        response.tokenType,
    )
}

private fun isRefreshableIdentityFailure(error: Exception): Boolean {
    val identityError = error as? OAuth2.IdentityServiceException ?: return false
    if (identityError.httpStatusCode == HTTP_UNAUTHORIZED) return true
    if (identityError.httpStatusCode != HTTP_FORBIDDEN) return false

    return identityError.responseBody?.let { body ->
        body.contains(BAD_OAUTH_TOKEN, ignoreCase = true) ||
            body.contains(WRONG_ORG, ignoreCase = true)
    } == true
}

/**
 * Applies the same refresh merge semantics used by ClientManager's UserAccountBuilder path:
 * values omitted as null retain their existing value, values returned as empty strings clear stale
 * session data, and additional OAuth values are merged. The access token is always required.
 */
private fun mergeRefreshedCredentials(
    target: TokenEndpointResponse,
    refreshed: TokenEndpointResponse,
) {
    target.authToken = refreshed.authToken?.takeIf(String::isNotBlank)
        ?: throw IllegalStateException("Token refresh did not return an access token")
    target.refreshToken = refreshed.refreshToken ?: target.refreshToken
    target.instanceUrl = refreshed.instanceUrl ?: target.instanceUrl
    target.apiInstanceUrl = refreshed.apiInstanceUrl ?: target.apiInstanceUrl
    target.idUrl = refreshed.idUrl ?: target.idUrl
    target.idUrlWithInstance = refreshed.idUrlWithInstance ?: target.idUrlWithInstance
    target.orgId = refreshed.orgId ?: target.orgId
    target.userId = refreshed.userId ?: target.userId
    target.code = refreshed.code ?: target.code
    target.communityId = refreshed.communityId ?: target.communityId
    target.communityUrl = refreshed.communityUrl ?: target.communityUrl
    target.additionalOauthValues = when (val refreshedValues = refreshed.additionalOauthValues) {
        null -> target.additionalOauthValues
        else -> (target.additionalOauthValues?.toMutableMap() ?: mutableMapOf()).apply {
            putAll(refreshedValues)
        }
    }
    target.idToken = refreshed.idToken ?: target.idToken
    target.lightningDomain = refreshed.lightningDomain ?: target.lightningDomain
    target.lightningSid = refreshed.lightningSid ?: target.lightningSid
    target.vfDomain = refreshed.vfDomain ?: target.vfDomain
    target.vfSid = refreshed.vfSid ?: target.vfSid
    target.contentDomain = refreshed.contentDomain ?: target.contentDomain
    target.contentSid = refreshed.contentSid ?: target.contentSid
    target.csrfToken = refreshed.csrfToken ?: target.csrfToken
    target.cookieClientSrc = refreshed.cookieClientSrc ?: target.cookieClientSrc
    target.cookieSidClient = refreshed.cookieSidClient ?: target.cookieSidClient
    target.sidCookieName = refreshed.sidCookieName ?: target.sidCookieName
    target.parentSid = refreshed.parentSid ?: target.parentSid
    target.uiSid = refreshed.uiSid ?: target.uiSid
    target.tokenFormat = refreshed.tokenFormat ?: target.tokenFormat
    target.beaconChildConsumerKey =
        refreshed.beaconChildConsumerKey ?: target.beaconChildConsumerKey
    target.beaconChildConsumerSecret =
        refreshed.beaconChildConsumerSecret ?: target.beaconChildConsumerSecret
    target.scope = refreshed.scope ?: target.scope
    target.tokenType = refreshed.tokenType ?: target.tokenType
    target.credentialsIdentifier =
        refreshed.credentialsIdentifier ?: target.credentialsIdentifier
}

/**
 * Helper method to set administrator preferences for a user account.
 */
private fun setAdministratorPreferences(
    userIdentity: OAuth2.IdServiceResponse?,
    account: UserAccount
) {
    // Set additional administrator prefs if they exist
    userIdentity?.customAttributes?.let { customAttributes ->
        SalesforceSDKManager.getInstance().adminSettingsManager?.setPrefs(customAttributes, account)
    }

    userIdentity?.customPermissions?.let { customPermissions ->
        SalesforceSDKManager.getInstance().adminPermsManager?.setPrefs(customPermissions, account)
    }
}

/**
 * Helper method to start main activity.
 */
private fun startMainActivityHelper() {
    with(SalesforceSDKManager.getInstance()) {
        appContext.startActivity(
            Intent(
                appContext,
                mainActivityClass
            ).apply {
                setPackage(appContext.packageName)
                flags = FLAG_ACTIVITY_NEW_TASK
            })
    }
}

/**
 * Helper method to update logging preferences.
 */
private fun updateLoggingPrefsHelper(account: UserAccount) {
    SalesforceAnalyticsManager.getInstance(account)?.updateLoggingPrefs()
}

/**
 * Helper method to handle screen lock mobile policy.
 */
@VisibleForTesting
internal fun handleScreenLockPolicy(
    userIdentity: OAuth2.IdServiceResponse?,
    account: UserAccount,
) {
    val internalScreenLockManager =
        SalesforceSDKManager.getInstance().screenLockManager as ScreenLockManager?

    // compareTo(0) is used to check if screenLockTimeout is non-null and greater than 0.
    if (userIdentity?.screenLockTimeout?.compareTo(0) == 1) {
        SalesforceSDKManager.getInstance().registerUsedAppFeature(FEATURE_SCREEN_LOCK, account)
        val timeoutInMills = userIdentity.screenLockTimeout * 1000 * 60
        internalScreenLockManager?.storeMobilePolicy(
            account,
            enabled = userIdentity.screenLock,
            timeoutInMills,
        )
    } else if (internalScreenLockManager?.enabled == true) {
        SalesforceSDKManager.getInstance().unregisterUsedAppFeature(FEATURE_SCREEN_LOCK, account)
        internalScreenLockManager.cleanUp(account)
    }
}

/**
 * Helper method to handle biometric authentication mobile policy.
 */
@VisibleForTesting
internal fun handleBiometricAuthPolicy(
    userIdentity: OAuth2.IdServiceResponse?,
    account: UserAccount,
) {
    val internalBiometricAuthenticationManager =
        SalesforceSDKManager.getInstance().biometricAuthenticationManager as BiometricAuthenticationManager?

    if (userIdentity?.biometricAuth == true) {
        SalesforceSDKManager.getInstance().registerUsedAppFeature(FEATURE_BIOMETRIC_AUTH, account)
        val timeoutInMills = userIdentity.biometricAuthTimeout * 60 * 1000
        internalBiometricAuthenticationManager?.storeMobilePolicy(
            account,
            enabled = userIdentity.biometricAuth,
            timeoutInMills
        )
    } else if (internalBiometricAuthenticationManager?.enabled == true) {
        SalesforceSDKManager.getInstance().unregisterUsedAppFeature(FEATURE_BIOMETRIC_AUTH, account)
        internalBiometricAuthenticationManager.cleanUp(account)
    }
}

/**
 * Helper method to add account and perform related operations.
 */
private fun addAccountHelper(
    account: UserAccount
) {
    addAccount(
        account,
        SalesforceSDKManager.getInstance().isTestRun,
        SalesforceSDKManager.getInstance().loginServerManager
    )
}

/**
 * Helper method to handle duplicate user account scenarios during authentication.
 * This method manages existing users by:
 * - Removing duplicate accounts and clearing caches
 * - Revoking old refresh tokens when a new one is provided
 * - Unlocking biometric authentication for the duplicate user
 * - Signing out other users with biometric auth when a new biometric user is added
 */
@VisibleForTesting
internal fun handleDuplicateUserAccount(
    userAccountManager: UserAccountManager,
    account: UserAccount,
    userIdentity: OAuth2.IdServiceResponse?,
    revokeRefreshToken: (HttpAccess, URI, String, OAuth2.LogoutReason) -> Unit = OAuth2::revokeRefreshToken,
) {
    userAccountManager.authenticatedUsers?.let { existingUsers ->
        // Check if the user already exists
        if (existingUsers.contains(account)) {
            val duplicateUserAccount = existingUsers.removeAt(existingUsers.indexOf(account))
            clearCaches()
            userAccountManager.clearCachedCurrentUser()

            // Revoke existing refresh token.  Compare the new account's in-memory
            // snapshot (the value just issued by the server) against the duplicate
            // account's snapshot (the value currently persisted).  Using
            // getRefreshToken() here would do a live AccountManager lookup for
            // both UserAccounts and return the same persisted value, suppressing
            // the revocation and leaking the prior refresh token.
            if (account.refreshTokenForPersistence != duplicateUserAccount.refreshTokenForPersistence) {
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
                            duplicateUserAccount.refreshTokenForPersistence,
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
}

/**
 * Persists account data to the Android AccountManager without changing
 * the current user. This is needed for token migration of background
 * users where [UserAccountManager.createAccount] cannot be used because
 * it unconditionally calls [UserAccountManager.storeCurrentUserInfo].
 */
private fun UserAccountManager.persistAccount(
    userAccount: UserAccount,
    accountType: String = SalesforceSDKManager.getInstance().accountType,
    acctManager: AccountManager = AccountManager.get(SalesforceSDKManager.getInstance().appContext),
) {
    val account = Account(userAccount.accountName, accountType)
    // Encrypt the in-memory snapshot rather than getRefreshToken(), which performs a lookup.
    val password = SalesforceSDKManager.encrypt(userAccount.refreshTokenForPersistence, encryptionKey)
    val created = acctManager.addAccountExplicitly(account, password, /* userdata = */ Bundle())

    // addAccountExplicitly fails if the account already exists, so update the refresh token.
    if (!created) {
        acctManager.setPassword(account, password)
    }

    // Cache auth token to avoid an unnecessary refresh on first access.
    acctManager.setAuthToken(
        account,
        /* authTokenType = */ AccountManager.KEY_AUTHTOKEN,
        /* authToken = */ SalesforceSDKManager.encrypt(userAccount.authToken, encryptionKey),
    )

    // Persist all remaining user data via the existing public helper.
    updateAccount(account, userAccount)
}
