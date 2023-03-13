package com.salesforce.androidsdk.auth.idp

import android.net.Uri
import android.text.TextUtils
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.auth.idp.IDPRequestHandler.IDPRequestHandlerException
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import com.salesforce.androidsdk.util.UriFragmentParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class IDPCodeGeneratorHelper(
    val webView: WebView,
    val userAccount: UserAccount,
    val spConfig: SPConfig,
    val callback: CodeGeneratorCallback
) {

    interface CodeGeneratorCallback {
        fun onError(error: String?)
        fun onSuccess(code: String?)
    }

    private var loginUrl: String? = null

    init {
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = IDPWebViewClient()
    }

    fun generateCode() {
        val idpRequestHandler = IDPRequestHandler(spConfig, userAccount)
        loginUrl = idpRequestHandler.loginUrl
        CoroutineScope(Dispatchers.IO).launch {
            val accessToken = try {
                idpRequestHandler.validAccessToken
            } catch (e: Exception) {
                SalesforceSDKLogger.e(TAG, "Building IDP request handler failed", e)
                callback.onError("Incomplete SP config or user account data")
                null
            }

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    if (accessToken != null) {
                        idpRequestHandler.makeFrontDoorRequest(accessToken, webView)
                    }
                } catch (e: IDPRequestHandlerException) {
                    SalesforceSDKLogger.e(TAG, "Making frontdoor request failed", e)
                }
            }
        }
    }

    /**
     * This class is used to monitor redirects within the WebView to determine
     * when the login flow is complete, parses the response and passes it back.
     */
    inner class IDPWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            Log.i(TAG, "--> shouldOverrideUrlLoading $url")
            val isDone = url.replace("///", "/").lowercase()
                .startsWith(spConfig.oauthCallbackUrl.replace("///", "/").lowercase())
            if (isDone) {
                val callbackUri = Uri.parse(url)
                val params = UriFragmentParser.parse(callbackUri)

                // Determines if the authentication flow succeeded or failed.
                if (params != null) {
                    val code = params[CODE_KEY]
                    if (TextUtils.isEmpty(code)) {
                        callback.onError("Code not returned from server")
                    } else {
                        callback.onSuccess(code)
                    }
                } else {
                    callback.onError("Code not returned from server")
                }
            } else if (url.contains(EC_301) || url.contains(EC_302)) {
                /*
                 * Currently there's no good way to recover from an invalid access token
                 * loading a page through frontdoor. Until the server API returns an
                 * error response, we look for 'ec=301' or 'ec=302' to handle this error case.
                 */
                callback.onError("Server returned unauthorized token error - ec=301 or ec=302")
            }
            return isDone
        }
    }

    companion object {
        const val CODE_KEY = "code"
        private const val EC_301 = "ec=301"
        private const val EC_302 = "ec=302"
        private const val TAG = "IDPCodeGeneratorActivity"
    }
}