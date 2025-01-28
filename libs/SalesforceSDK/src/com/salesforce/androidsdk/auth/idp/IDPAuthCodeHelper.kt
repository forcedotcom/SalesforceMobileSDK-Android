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

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.salesforce.androidsdk.R
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.OAuth2.getAuthorizationUrl
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import com.salesforce.androidsdk.util.UriFragmentParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI

/**
 * Helper class used in IDP app to get auth code from server
 */
internal class IDPAuthCodeHelper private constructor(
    val webView: WebView,
    val userAccount: UserAccount,
    val spConfig: SPConfig,
    val codeChallenge: String,
    val onResult:(result:Result) -> Unit
) {
    data class Result(
        val success: Boolean,
        val code: String? = null,
        val loginUrl: String? = null,
        val error: String? = null
    )

    init {
        webView.webViewClient = IDPWebViewClient(webView.webViewClient)
    }

    /**
     * Kicks off auth code generation
     * Callback provided on constructor is invoked when code generation completes or fails
     */
    private fun generateAuthCode() {
        SalesforceSDKLogger.d(TAG, "Generating oauth code")
        CoroutineScope(Dispatchers.IO).launch {
            val restClient = buildRestClient() ?: return@launch onError("Failed to build rest client")
            val authorizationUrlForSP = getAuthorizationPathForSP() ?: return@launch onError("Failed to get authorization url")
            val frontdoorUrl = getFrontdoorUrl(restClient, authorizationUrlForSP) ?: return@launch onError("Failed to get front door url")

            // Launch front door url in web view on the main thread
            withContext(Dispatchers.Main) {
                webView.loadUrl(frontdoorUrl)
            }
        }
    }

    /**
     * Helper function to build a rest client
     *
     * @return a RestClient instance
     */
    private fun buildRestClient(): RestClient? {
        SalesforceSDKLogger.d(TAG, "Building rest client")
        val context = SalesforceSDKManager.getInstance().appContext
        val bootConfig = BootConfig.getBootConfig(context)
        val idpCallbackUrl = bootConfig.oauthRedirectURI
        val idpClientId = bootConfig.remoteAccessConsumerKey
        val idpScopes = bootConfig.oauthScopes
        val loginOptions = ClientManager.LoginOptions(
            userAccount.loginServer, idpCallbackUrl, idpClientId, idpScopes
        )
        val idpAccountType = SalesforceSDKManager.getInstance().accountType
        val clientManager = ClientManager(
            context, idpAccountType,
            loginOptions, false
        )
        return clientManager.peekRestClient(userAccount)
    }

    /**
     * Compute relative path of authorization url for SP
     * @return authorization relative path
     */
    fun getAuthorizationPathForSP(): String? {
        SalesforceSDKLogger.d(TAG, "Getting authorization url")
        val context = SalesforceSDKManager.getInstance().appContext
        val useHybridAuthentication = SalesforceSDKManager.getInstance().useHybridAuthentication
        val authorizationUri = getAuthorizationUrl(
            true, // use web server flow
            useHybridAuthentication,
            URI(userAccount.loginServer),
            spConfig.oauthClientId,
            spConfig.oauthCallbackUrl,
            spConfig.oauthScopes,
            context.getString(R.string.oauth_display_type),
            codeChallenge,
            null
        )

        return authorizationUri?.let {
            it.path + (it.query?.let { query -> "?$query" } ?: "")
        } ?: null
    }

    fun getFrontdoorUrl(restClient:RestClient, redirectUri: String): String? {
        SalesforceSDKLogger.d(TAG, "Getting front door url")
        val singleAccessRequest = RestRequest.getRequestForSingleAccess(redirectUri)
        val restResponse = try {
            restClient.sendSync(singleAccessRequest)
        } catch (e: IOException) {
            SalesforceSDKLogger.e(TAG, "Failed to obtain valid front door url", e)
            null
        }
        return if (restResponse == null || !restResponse.isSuccess) null else restResponse.asJSONObject().getString("frontdoor_uri")
    }

    private fun onError(error: String, exception: java.lang.Exception? = null) {
        SalesforceSDKLogger.e(TAG, "Auth code obtention failed: $error", exception)
        onResult(Result(success = false, error = error))
    }

    @Override
    private fun onSuccess(code: String) {
        SalesforceSDKLogger.d(TAG, "Auth code successfully obtained")
        onResult(Result(success = true, code = code, loginUrl = userAccount.loginServer))
    }

    /**
     * Web view client used to monitor redirects to determine login flow is complete
     * It also parses the last redirect to extract code
     */
    inner class IDPWebViewClient(private val wrappedWebViewContent: WebViewClient) : WebViewClient() {

        fun sanitizeUrl(url: String):String {
            return url.replace("///", "/").lowercase()
        }

        fun isOauthCallbackUrl(url: String):Boolean {
            return sanitizeUrl(url).startsWith(sanitizeUrl(spConfig.oauthCallbackUrl))
        }

        fun extractCode(uri: Uri):String? {
            return UriFragmentParser.parse(uri)[CODE_KEY]
        }

        fun hasUnauthorizedTokenError(uri: Uri): Boolean {
            /*
             * Currently there's no good way to recover from an invalid access token
             * loading a page through frontdoor. Until the server API returns an
             * error response, we look for 'ec=301' or 'ec=302' to handle this error case.
             */
            val ec = uri.getQueryParameter(EC_KEY)
            return ec.equals("301") || ec.equals("302")
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            SalesforceSDKLogger.d(TAG, "Web view navigating to ${request.url}")
            return if (isOauthCallbackUrl(request.url.toString())) {
                val code = extractCode(request.url)
                if (code == null) {
                    onError("Code not returned from server")
                } else {
                    onSuccess(code)
                }
                true
            } else if (hasUnauthorizedTokenError(request.url)) {
                onError("Server returned unauthorized token error - ec=301 or ec=302")
                false
            } else {
                false
            }
        }

        override fun onPageFinished(webView: WebView?, url: String?) {
            wrappedWebViewContent.onPageFinished(webView, url)
        }
    }

    companion object {
        private val TAG = IDPAuthCodeHelper::class.java.simpleName
        private const val CODE_KEY = "code"
        private const val EC_KEY = "ec"

        fun generateAuthCode(webView: WebView,
                             userAccount: UserAccount,
                             spConfig: SPConfig,
                             codeChallenge: String,
                             onResult: (result: Result) -> Unit) {
            IDPAuthCodeHelper(webView, userAccount, spConfig, codeChallenge, onResult).generateAuthCode()
        }
    }
}