/*
 * Copyright (c) 2023-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.auth.idp

import android.content.Context
import android.os.Bundle
import com.salesforce.androidsdk.accounts.UserAccount
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
    val onResult:(result:Result) -> Unit
) : OAuthWebviewHelperEvents {
    data class Result(
        val success: Boolean,
        val user: UserAccount? = null,
        val error: String? = null
    )
    companion object {
        private val TAG: String = SPAuthCodeHelper::class.java.simpleName

        fun loginWithAuthCode(context:Context,
                              loginUrl: String, code:
                              String, codeVerifier: String,
                              onResult: (Result) -> Unit
        ) {
            val spAuthCodeHelper = SPAuthCodeHelper(context, loginUrl, code, codeVerifier, onResult)
            CoroutineScope(Dispatchers.IO).launch {
                spAuthCodeHelper.loginWithAuthCode()
            }
        }
    }

    val spConfig: SPConfig = SPConfig.forCurrentApp()

    private fun getTokenResponse(): TokenEndpointResponse {
        val tokenResponse = OAuth2.exchangeCode(
            HttpAccess.DEFAULT,
            URI.create(loginUrl),
            spConfig.oauthClientId,
            code,
            codeVerifier,
            spConfig.oauthCallbackUrl
        )
        SalesforceSDKLogger.d(TAG, "getTokenResponse $tokenResponse")
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
        SalesforceSDKLogger.d(TAG, "completeLogin oauthHelper $oauthHelper")
        oauthHelper.onAuthFlowComplete(tokenResponse)
    }

    private fun loginWithAuthCode() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                completeLogin(getTokenResponse())
            } catch (e: Exception) {
                SalesforceSDKLogger.e(TAG, "loginWithAuthCode failed", e)
                onResult(Result(success = false, error = e.message))
            }
        }
    }

    override fun loadingLoginPage(loginUrl: String?) {
        SalesforceSDKLogger.d(TAG, "loadingLoginPage $loginUrl")
    }

    override fun onAccountAuthenticatorResult(authResult: Bundle?) {
        SalesforceSDKLogger.d(TAG, "onAccountAuthenticatorResult ${LogUtil.bundleToString(authResult)}")
    }

    override fun finish(user: UserAccount?) {
        SalesforceSDKLogger.d(TAG, "finish $user")
        user?.let {
            onResult(Result(success = true, user = it))
        }
    }
}