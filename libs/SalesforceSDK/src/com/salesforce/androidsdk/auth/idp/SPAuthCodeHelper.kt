package com.salesforce.androidsdk.auth.idp

import android.content.Context
import android.os.Bundle
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.auth.OAuth2
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.ui.OAuthWebviewHelper
import com.salesforce.androidsdk.ui.OAuthWebviewHelper.OAuthWebviewHelperEvents
import com.salesforce.androidsdk.util.LogUtil
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URI

/**
 * Helper class used in SP app to get auth tokens and create user given auth code
 */
internal class SPAuthCodeHelper private constructor (
    val context: Context,
    val loginUrl: String,
    val code: String,
    val codeVerifier: String,
    val onUserCreated: (UserAccount) -> Unit
) : OAuthWebviewHelperEvents {

    companion object {
        val TAG = SPAuthCodeHelper::class.java.simpleName

        fun loginWithAuthCode(context:Context, loginUrl: String, code: String, codeVerifier: String, onUserCreated: (UserAccount) -> Unit) {
            val spAuthCodeHelper = SPAuthCodeHelper(context, loginUrl, code, codeVerifier, onUserCreated)
            CoroutineScope(Dispatchers.IO).launch {
                spAuthCodeHelper.loginWithAuthCode()
            }
        }
    }

    val spConfig: SPConfig

    val sdkMgr: SalesforceSDKManager
        get() = SalesforceSDKManager.getInstance()

    init {
        spConfig = SPConfig.forCurrentApp()
    }

    private fun getTokenResponse(): TokenEndpointResponse {
        val tokenResponse = OAuth2.getSPCredentials(
            HttpAccess.DEFAULT,
            URI.create(loginUrl),
            spConfig.oauthClientId,
            code,
            codeVerifier,
            spConfig.oauthCallbackUrl
        )
        SalesforceSDKLogger.d(TAG, "getTokenResponse ${tokenResponse}")
        return tokenResponse
    }

    private fun completeLogin(tokenResponse: TokenEndpointResponse) {
        val loginOptions = ClientManager.LoginOptions(
            loginUrl,
            spConfig.oauthCallbackUrl,
            spConfig.oauthClientId,
            spConfig.oauthScopes,
            null,
            null
        )

        val oauthHelper = OAuthWebviewHelper(context, this, loginOptions)
        SalesforceSDKLogger.d(TAG, "completeLogin oauthHelper ${oauthHelper}")
        oauthHelper.onAuthFlowComplete(tokenResponse)
    }

    private fun loginWithAuthCode() {
        CoroutineScope(Dispatchers.IO).launch {
            completeLogin(getTokenResponse())
        }
    }

    override fun loadingLoginPage(loginUrl: String?) {
        SalesforceSDKLogger.d(TAG, "loadingLoginPage ${loginUrl}")
    }

    override fun onAccountAuthenticatorResult(authResult: Bundle?) {
        SalesforceSDKLogger.d(TAG, "onAccountAuthenticatorResult ${LogUtil.bundleToString(authResult)}")
    }

    override fun finish(user: UserAccount?) {
        SalesforceSDKLogger.d(TAG, "finish ${user}")
        user?.let { onUserCreated(user) }
    }
}