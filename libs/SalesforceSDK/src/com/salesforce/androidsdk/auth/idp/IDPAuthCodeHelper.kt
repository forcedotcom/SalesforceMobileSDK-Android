package com.salesforce.androidsdk.auth.idp

import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.os.bundleOf
import com.salesforce.androidsdk.R
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.OAuth2
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import com.salesforce.androidsdk.util.UriFragmentParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException

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
    ) {
        companion object {
            private const val SUCCESS_KEY = "success"
            private const val CODE_KEY = "code"
            private const val LOGIN_URL_KEY = "login_url"
            private const val ERROR_KEY = "error"

            fun fromBundle(bundle: Bundle?):Result? {
                return if (bundle == null) null else {
                    with(bundle) {
                        Result(
                            getBoolean(SUCCESS_KEY),
                            getString(CODE_KEY),
                            getString(LOGIN_URL_KEY),
                            getString(ERROR_KEY)
                        )
                    }
                }
            }
        }

        fun toBundle():Bundle {
            return bundleOf(
                SUCCESS_KEY to success,
                CODE_KEY to code,
                LOGIN_URL_KEY to loginUrl,
                ERROR_KEY to error
            )
        }
    }

    init {
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = IDPWebViewClient()
    }

    /**
     * Kicks off auth code generation
     * Callback provided on constructor is invoked when code generation completes or fails
     */
    private fun generateAuthCode() {
        SalesforceSDKLogger.d(TAG, "Generating oauth code")
        CoroutineScope(Dispatchers.IO).launch {
            getValidAccessToken()?.let {accessToken ->
                makeFrontDoorRequest(accessToken, webView)
            } ?: run {
                onError("Failed to get an access token")
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
     * Helper function to get a fresh access token
     *
     * @return the valid access token or null
     */
    private fun getValidAccessToken(): String? {
        SalesforceSDKLogger.d(TAG, "Obtaining valid access token")
        buildRestClient()?.let {restClient ->
            val restResponse = try {
                restClient.sendSync(RestRequest.getRequestForUserInfo())
            } catch (e: IOException) {
                SalesforceSDKLogger.e(TAG, "Failed to obtain valid access token", e)
                null
            }

            return if (restResponse == null || !restResponse.isSuccess) null else restClient.authToken
        } ?: run {
            SalesforceSDKLogger.e(TAG, "Cannot get valid access token - failed to build rest client")
            return null
        }
    }

    /**
     * Kicks off the 'frontdoor' request in the supplied WebView instance.
     *
     * @param accessToken Valid access token.
     * @param webView WebView instance.
     */
    fun makeFrontDoorRequest(accessToken: String, webView: WebView) {
        SalesforceSDKLogger.d(TAG, "Making front door request")
        val context = SalesforceSDKManager.getInstance().appContext
        val frontdoorUrl = OAuth2.getIDPFrontdoorUrl(
            userAccount.instanceServer,
            accessToken,
            userAccount.loginServer,
            context.getString(R.string.oauth_display_type),
            spConfig.oauthClientId,
            spConfig.oauthCallbackUrl,
            spConfig.oauthScopes,codeChallenge
        )
        CoroutineScope(Dispatchers.Main).launch {
            webView.loadUrl(frontdoorUrl.toString())
        }
    }

    private fun onError(error: String, exception: java.lang.Exception? = null) {
        SalesforceSDKLogger.e(TAG, "Auth code obtention failed: ${error}", exception)
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
    inner class IDPWebViewClient : WebViewClient() {

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