/*
 * Copyright (c) 2024-present, salesforce.com, inc.
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

import android.accounts.AccountManager
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.OAuth2.AUTHORIZATION
import com.salesforce.androidsdk.auth.OAuth2.AUTHORIZATION_CODE
import com.salesforce.androidsdk.auth.OAuth2.CLIENT_ID
import com.salesforce.androidsdk.auth.OAuth2.CODE
import com.salesforce.androidsdk.auth.OAuth2.CODE_CHALLENGE
import com.salesforce.androidsdk.auth.OAuth2.CODE_VERIFIER
import com.salesforce.androidsdk.auth.OAuth2.GRANT_TYPE
import com.salesforce.androidsdk.auth.OAuth2.OAUTH_AUTH_PATH
import com.salesforce.androidsdk.auth.OAuth2.OAUTH_TOKEN_PATH
import com.salesforce.androidsdk.auth.OAuth2.REDIRECT_URI
import com.salesforce.androidsdk.auth.OAuth2.RESPONSE_TYPE
import com.salesforce.androidsdk.auth.OAuth2.SFDC_COMMUNITY_URL
import com.salesforce.androidsdk.auth.interfaces.NativeLoginManager
import com.salesforce.androidsdk.auth.interfaces.NativeLoginResult
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions
import com.salesforce.androidsdk.rest.RestClient.AsyncRequestCallback
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestResponse
import com.salesforce.androidsdk.security.BiometricAuthenticationManager
import com.salesforce.androidsdk.security.SalesforceKeyGenerator
import com.salesforce.androidsdk.ui.OAuthWebviewHelper
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.jetbrains.annotations.VisibleForTesting
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


internal class NativeLoginManager(
    private val clientId: String,
    private val redirectUri: String,
    private val loginUrl: String,
): NativeLoginManager {

    private val accountManager = SalesforceSDKManager.getInstance().userAccountManager
    private val bioAuthLocked
        get() = SalesforceSDKManager.getInstance().biometricAuthenticationManager?.locked == true
    override val shouldShowBackButton: Boolean
        get() = !bioAuthLocked && accountManager.authenticatedUsers != null
    override val biometricAuthenticationUsername: String?
        get() {
            return if (bioAuthLocked) {
                accountManager.currentUser.username
            } else {
                null
            }
        }

    override suspend fun login(username: String, password: String): NativeLoginResult {
        val trimmedUsername = username.trim()
        val trimmedPassword = password.trim()

        if (!isValidUsername(trimmedUsername)) {
            return NativeLoginResult.InvalidUsername
        }

        if (!isValidPassword(trimmedPassword)) {
            return NativeLoginResult.InvalidPassword
        }

        val creds = "$trimmedUsername:$trimmedPassword".toByteArray()
        val encodedCreds = Base64.encodeToString(creds, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val codeVerifier = SalesforceKeyGenerator.getRandom128ByteKey()
        val codeChallenge = SalesforceKeyGenerator.getSHA256Hash(codeVerifier)

        val authRequestHeaders = hashMapOf(
            AUTH_REQUEST_TYPE to NAMED_USER,
            CONTENT_TYPE to HTTP_POST_CONTENT_TYPE,
            AUTHORIZATION to AUTHORIZATION_TYPE_BASIC + encodedCreds,
        )
        val authRequestBody = createRequestBody(
            RESPONSE_TYPE to CODE_CREDENTIALS,
            CLIENT_ID to clientId,
            REDIRECT_URI to redirectUri,
            CODE_CHALLENGE to codeChallenge,
        )
        val authRequest = RestRequest(
            RestRequest.RestMethod.POST,
            RestRequest.RestEndpoint.LOGIN,
            loginUrl + OAUTH_AUTH_PATH, // Full path for unauthenticated request
            authRequestBody,
            authRequestHeaders,
        )

        // First REST Call - Authorization
        val authResponse: RestResponse = suspendedRestCall(authRequest) ?: return NativeLoginResult.UnknownError
        if (authResponse.isSuccess) {
            val code = authResponse.asJSONObject().get(CODE).toString()
            val authEndpoint = authResponse.asJSONObject().get(SFDC_COMMUNITY_URL).toString()
            val tokenRequestBody = createRequestBody(
                CODE to code,
                GRANT_TYPE to AUTHORIZATION_CODE,
                CLIENT_ID to clientId,
                REDIRECT_URI to redirectUri,
                CODE_VERIFIER to codeVerifier,
            )
            authResponse.consumeQuietly()

            val tokenRequest = RestRequest(
                RestRequest.RestMethod.POST,
                RestRequest.RestEndpoint.LOGIN,
                authEndpoint + OAUTH_TOKEN_PATH,  // Full path for unauthenticated request
                tokenRequestBody,
                 null, // additionalHttpHeaders
            )

            // Second REST Call - token request with code verifier
            val tokenResponse = suspendedRestCall(tokenRequest) ?: return NativeLoginResult.UnknownError
            return if (tokenResponse.isSuccess) {
                // Returns NativeLoginResult.Success or UnknownError based on if the user is created.
                suspendFinishAuthFlow(tokenResponse)
            } else {
                NativeLoginResult.UnknownError
            }
        } else {
            SalesforceSDKLogger.e(TAG, "Native Login Authorization Error: $authResponse")
            return NativeLoginResult.InvalidCredentials
        }
    }

    override fun getFallbackWebAuthenticationIntent(): Intent {
        val context = SalesforceSDKManager.getInstance().appContext
        val intent = Intent(context, SalesforceSDKManager.getInstance().webviewLoginActivityClass)
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val options = SalesforceSDKManager.getInstance().loginOptions.asBundle()
        options.putBoolean(BiometricAuthenticationManager.SHOW_BIOMETRIC, bioAuthLocked)
        intent.putExtras(options)
        Bundle().putParcelable(AccountManager.KEY_INTENT, intent)

        return intent
    }

    @VisibleForTesting
    internal fun isValidUsername(username: String): Boolean {
        if (username.length > MAX_USERNAME_LENGTH) {
            return false
        }

        return Regex(USERNAME_REGEX_PATTERN).matches(username)
    }

    @VisibleForTesting
    internal fun isValidPassword(password: String): Boolean {
        val containsNumber = password.contains("[0-9]".toRegex())
        val containsLetter = password.contains("[A-Za-z]".toRegex())

        return containsNumber && containsLetter && password.length >= MIN_PASSWORD_LENGTH
                && password.toByteArray().size <= MAX_PASSWORD_LENGTH_BYTES
    }

    private suspend fun suspendFinishAuthFlow(tokenResponse: RestResponse): NativeLoginResult {
        val appContext = SalesforceSDKManager.getInstance().appContext
        val loginOptions = LoginOptions(loginUrl, redirectUri, clientId, emptyArray<String>())
        val tokenEndpointResponse = OAuth2.TokenEndpointResponse(tokenResponse.rawResponse)
        tokenResponse.consumeQuietly()

        return suspendCoroutine { continuation ->
            OAuthWebviewHelper(appContext, object : OAuthWebviewHelper.OAuthWebviewHelperEvents {
                override fun loadingLoginPage(loginUrl: String) { /* This will never be called. */ }
                override fun onAccountAuthenticatorResult(authResult: Bundle) { /* Unused */ }

                override fun finish(userAccount: UserAccount?) {
                    if (userAccount != null) {
                        accountManager.switchToUser(userAccount)
                        // Start App's Main Activity
                        appContext.startActivity(
                            Intent(appContext, SalesforceSDKManager.getInstance().mainActivityClass).apply {
                                setPackage(appContext.packageName)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    }

                    /**
                     * We need to wait until the main activity is launched before
                     * returning success back to the app's Native Login Activity.  If we
                     * don't they call finish() too early and the activity will be restarted.
                     * This will cause it to hang around forever behind the main activity.
                     */
                    continuation.resume(
                        when(userAccount) {
                            null -> {
                                SalesforceSDKLogger.e(TAG, "Unable to create user account from successful token response.")
                                NativeLoginResult.UnknownError
                            }
                            else -> NativeLoginResult.Success
                        }
                    )
                }

            }, loginOptions).onAuthFlowComplete(tokenEndpointResponse, nativeLogin = true)
        }
    }

    private suspend fun suspendedRestCall(request: RestRequest): RestResponse? {
        return suspendCoroutine { continuation ->
            SalesforceSDKManager.getInstance().clientManager
                .peekUnauthenticatedRestClient().sendAsync(request, object : AsyncRequestCallback {

                    override fun onSuccess(request: RestRequest?, response: RestResponse?) {
                        continuation.resume(response)
                    }

                    override fun onError(exception: Exception?) {
                        if (exception != null) {
                            SalesforceSDKLogger.e(TAG, "Authentication call was unsuccessful.", exception)
                            continuation.resumeWithException(exception)
                        }
                    }
                })
        }
    }

    private fun createRequestBody(vararg kvPairs: Pair<String, String>): RequestBody {
        val requestBodyString = kvPairs.joinToString("&") { (key, value) -> "$key=$value" }
        val mediaType = HTTP_POST_CONTENT_TYPE.toMediaTypeOrNull()
        return requestBodyString.toRequestBody(mediaType)
    }


    companion object {
        private const val MAX_USERNAME_LENGTH = 80
        private const val MIN_PASSWORD_LENGTH = 8
        private const val MAX_PASSWORD_LENGTH_BYTES = 16000
        private const val USERNAME_REGEX_PATTERN = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
        private const val AUTH_REQUEST_TYPE = "Auth-Request-Type"
        private const val NAMED_USER = "Named-User"
        private const val CONTENT_TYPE = "Content-Type"
        private const val HTTP_POST_CONTENT_TYPE = "application/x-www-form-urlencoded"
        private const val AUTHORIZATION_TYPE_BASIC = "Basic "
        private const val CODE_CREDENTIALS = "code_credentials"
        private const val TAG = "NativeLoginManager"
    }
}