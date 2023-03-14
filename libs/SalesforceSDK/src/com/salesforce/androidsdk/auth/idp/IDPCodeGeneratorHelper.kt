package com.salesforce.androidsdk.auth.idp

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
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
    lateinit var loginUrl:String
    interface CodeGeneratorCallback {
        fun onResult(resultCode:Int, data: Intent)
    }

    init {
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = IDPWebViewClient()
    }

    fun generateCode() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val idpRequestHandler = IDPRequestHandler(spConfig, userAccount)
                loginUrl = idpRequestHandler.loginUrl
                val accessToken = idpRequestHandler.validAccessToken

                if (accessToken != null) {
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            idpRequestHandler.makeFrontDoorRequest(accessToken, webView)
                        } catch (e: IDPRequestHandlerException) {
                            onError("Making frontdoor request failed", e)
                        }
                    }
                }

            } catch (e: Exception) {
                onError("Building IDP request handler failed", e)
            }
        }
    }

    private fun onError(error: String?, exception: java.lang.Exception? = null) {
        SalesforceSDKLogger.e(TAG, "Code generation failed: ${error}", exception)
        val intent = Intent()
        intent.putExtra(ERROR_KEY, error);
        callback.onResult(RESULT_CANCELED, intent)
    }

    @Override
    private fun onSuccess(code: String?) {
        SalesforceSDKLogger.d(TAG, "Code generation succeeded")
        val intent = Intent()
        intent.putExtra(CODE_KEY, code);
        intent.putExtra(LOGIN_URL_KEY, loginUrl);
        callback.onResult(RESULT_OK, intent);
    }

    /**
     * This class is used to monitor redirects within the WebView to determine
     * when the login flow is complete, parses the response and passes it back.
     */
    inner class IDPWebViewClient : WebViewClient() {

        private fun sanitizeUrl(url: String):String {
            return url.replace("///", "/").lowercase()
        }
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            val isDone = sanitizeUrl(url).startsWith(sanitizeUrl(spConfig.oauthCallbackUrl))
            if (isDone) {
                val callbackUri = Uri.parse(url)
                val params = UriFragmentParser.parse(callbackUri)

                if (params == null || params[CODE_KEY].isNullOrEmpty()) {
                    onError("Code not returned from server")
                } else {
                    onSuccess(params[CODE_KEY])
                }
            } else if (url.contains(EC_301) || url.contains(EC_302)) {
                /*
                 * Currently there's no good way to recover from an invalid access token
                 * loading a page through frontdoor. Until the server API returns an
                 * error response, we look for 'ec=301' or 'ec=302' to handle this error case.
                 */
                onError("Server returned unauthorized token error - ec=301 or ec=302")
            }
            return isDone
        }
    }

    companion object {
        const val ERROR_KEY = "error"
        const val LOGIN_URL_KEY = "login_url"
        const val CODE_KEY = "code"
        private const val EC_301 = "ec=301"
        private const val EC_302 = "ec=302"
        private const val TAG = "IDPCodeGeneratorActivity"
    }
}