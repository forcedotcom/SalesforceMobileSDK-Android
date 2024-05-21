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

import android.accounts.AccountManager.KEY_INTENT
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
import android.os.Bundle
import android.util.Base64.NO_PADDING
import android.util.Base64.NO_WRAP
import android.util.Base64.URL_SAFE
import android.util.Base64.encodeToString
import android.util.Patterns.EMAIL_ADDRESS
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.NativeLoginManager.StartRegistrationRequestBody.UserData
import com.salesforce.androidsdk.auth.OAuth2.AUTHORIZATION
import com.salesforce.androidsdk.auth.OAuth2.AUTHORIZATION_CODE
import com.salesforce.androidsdk.auth.OAuth2.CLIENT_ID
import com.salesforce.androidsdk.auth.OAuth2.CODE
import com.salesforce.androidsdk.auth.OAuth2.CODE_CHALLENGE
import com.salesforce.androidsdk.auth.OAuth2.CODE_VERIFIER
import com.salesforce.androidsdk.auth.OAuth2.GRANT_TYPE
import com.salesforce.androidsdk.auth.OAuth2.HYBRID_AUTH_CODE
import com.salesforce.androidsdk.auth.OAuth2.OAUTH_AUTH_PATH
import com.salesforce.androidsdk.auth.OAuth2.OAUTH_ENDPOINT_HEADLESS_FORGOT_PASSWORD
import com.salesforce.androidsdk.auth.OAuth2.OAUTH_ENDPOINT_HEADLESS_INIT_PASSWORDLESS_LOGIN
import com.salesforce.androidsdk.auth.OAuth2.OAUTH_ENDPOINT_HEADLESS_INIT_REGISTRATION
import com.salesforce.androidsdk.auth.OAuth2.OAUTH_TOKEN_PATH
import com.salesforce.androidsdk.auth.OAuth2.REDIRECT_URI
import com.salesforce.androidsdk.auth.OAuth2.RESPONSE_TYPE
import com.salesforce.androidsdk.auth.OAuth2.SFDC_COMMUNITY_URL
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse
import com.salesforce.androidsdk.auth.interfaces.NativeLoginManager
import com.salesforce.androidsdk.auth.interfaces.NativeLoginManager.StartRegistrationResult
import com.salesforce.androidsdk.auth.interfaces.NativeLoginResult
import com.salesforce.androidsdk.auth.interfaces.NativeLoginResult.InvalidCredentials
import com.salesforce.androidsdk.auth.interfaces.NativeLoginResult.InvalidEmail
import com.salesforce.androidsdk.auth.interfaces.NativeLoginResult.InvalidPassword
import com.salesforce.androidsdk.auth.interfaces.NativeLoginResult.InvalidUsername
import com.salesforce.androidsdk.auth.interfaces.NativeLoginResult.Success
import com.salesforce.androidsdk.auth.interfaces.NativeLoginResult.UnknownError
import com.salesforce.androidsdk.auth.interfaces.OtpRequestResult
import com.salesforce.androidsdk.auth.interfaces.OtpVerificationMethod
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions
import com.salesforce.androidsdk.rest.RestClient.AsyncRequestCallback
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestRequest.RestEndpoint.LOGIN
import com.salesforce.androidsdk.rest.RestRequest.RestMethod.POST
import com.salesforce.androidsdk.rest.RestResponse
import com.salesforce.androidsdk.security.BiometricAuthenticationManager.Companion.SHOW_BIOMETRIC
import com.salesforce.androidsdk.security.SalesforceKeyGenerator.getRandom128ByteKey
import com.salesforce.androidsdk.security.SalesforceKeyGenerator.getSHA256Hash
import com.salesforce.androidsdk.ui.OAuthWebviewHelper
import com.salesforce.androidsdk.ui.OAuthWebviewHelper.OAuthWebviewHelperEvents
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.jetbrains.annotations.VisibleForTesting
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * A native login manager implementation for internal use only by the Salesforce
 * Mobile SDK.
 * @param clientId The Salesforce connected app client identifier
 * @param redirectUri The Salesforce connected app redirect URL
 * @param loginUrl The Salesforce site login URL
 * @param reCaptchaSiteKeyId The Google Cloud project reCAPTCHA Key's "Id" as
 * shown in Google Cloud Console under "Products & Solutions", "Security" and
 * "reCAPTCHA Enterprise".  Defaults to null to disable reCAPTCHA use
 * @param googleCloudProjectId The Google Cloud project's "Id" as shown in
 * Google Cloud Console.  Defaults to null to disable reCAPTCHA use
 * @param isReCaptchaEnterprise Specifies if reCAPTCHA uses the enterprise
 * license. Defaults to false to disable reCAPTCHA use
 */
internal class NativeLoginManager(
    private val clientId: String,
    private val redirectUri: String,
    private val loginUrl: String,
    private val reCaptchaSiteKeyId: String? = null,
    private val googleCloudProjectId: String? = null,
    private val isReCaptchaEnterprise: Boolean = false
) : NativeLoginManager {

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

        trimmedUsername.mapInvalidUsernameToResult()?.let { return it }
        trimmedPassword.mapInvalidPasswordToResult()?.let { return it }

        val encodedCreds = generateColonConcatenatedBase64String(
            value1 = trimmedUsername,
            value2 = trimmedPassword
        )
        val codeVerifier = getRandom128ByteKey()
        val codeChallenge = getSHA256Hash(codeVerifier)

        val authRequestHeaders = hashMapOf(
            AUTH_REQUEST_TYPE_HEADER_NAME to AUTH_REQUEST_TYPE_VALUE_VALUE_NAMED_USER,
            CONTENT_TYPE_HEADER_NAME to CONTENT_TYPE_VALUE_HTTP_POST,
            AUTHORIZATION to "$AUTH_AUTHORIZATION_VALUE_BASIC $encodedCreds",
        )
        val authRequestBody = createRequestBody(
            RESPONSE_TYPE to CODE_CREDENTIALS,
            CLIENT_ID to clientId,
            REDIRECT_URI to redirectUri,
            CODE_CHALLENGE to codeChallenge,
        )
        val authRequest = RestRequest(
            POST,
            LOGIN,
            "$loginUrl$OAUTH_AUTH_PATH", // Full path for unauthenticated request
            authRequestBody,
            authRequestHeaders,
        )

        // First REST Call - Authorization
        val authResponse = suspendedRestCall(authRequest) ?: return UnknownError
        return submitAccessTokenRequest(
            authorizationResponse = authResponse,
            codeVerifier = codeVerifier
        )
    }

    override fun getFallbackWebAuthenticationIntent(): Intent {
        val context = SalesforceSDKManager.getInstance().appContext
        val intent = Intent(context, SalesforceSDKManager.getInstance().webviewLoginActivityClass)
        intent.setFlags(FLAG_ACTIVITY_SINGLE_TOP)
        val options = SalesforceSDKManager.getInstance().loginOptions.asBundle()
        options.putBoolean(SHOW_BIOMETRIC, bioAuthLocked)
        intent.putExtras(options)
        Bundle().putParcelable(KEY_INTENT, intent)

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
        val tokenEndpointResponse = TokenEndpointResponse(tokenResponse.rawResponse)
        tokenResponse.consumeQuietly()

        return suspendCoroutine { continuation ->
            OAuthWebviewHelper(appContext, object : OAuthWebviewHelperEvents {
                override fun loadingLoginPage(loginUrl: String) {
                    /* This will never be called. */
                }

                override fun onAccountAuthenticatorResult(authResult: Bundle) {
                    /* Unused */
                }

                override fun finish(userAccount: UserAccount?) {
                    if (userAccount != null) {
                        accountManager.switchToUser(userAccount)
                        // Start App's Main Activity
                        appContext.startActivity(
                            Intent(appContext, SalesforceSDKManager.getInstance().mainActivityClass).apply {
                                setPackage(appContext.packageName)
                                flags = FLAG_ACTIVITY_NEW_TASK
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
                        when (userAccount) {
                            null -> {
                                SalesforceSDKLogger.e(TAG, "Unable to create user account from successful token response.")
                                UnknownError
                            }

                            else -> Success
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
        val mediaType = CONTENT_TYPE_VALUE_HTTP_POST.toMediaTypeOrNull()
        return requestBodyString.toRequestBody(mediaType)
    }

    // region Salesforce Identity API Headless Registration Flow

    override suspend fun startRegistration(
        email: String,
        firstName: String,
        lastName: String,
        username: String,
        newPassword: String,
        reCaptchaToken: String,
        otpVerificationMethod: OtpVerificationMethod
    ): StartRegistrationResult {
        // Validate parameters.
        val trimmedEmail = email.trim()
        if (!EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            return StartRegistrationResult(InvalidEmail)
        }
        val trimmedNewPassword = newPassword.trim()
        trimmedNewPassword.mapInvalidPasswordToResult()?.let { return StartRegistrationResult(it) }
        val trimmedUsername = username.trim()
        trimmedUsername.mapInvalidUsernameToResult()?.let { return StartRegistrationResult(it) }
        val reCaptchaParameterGenerationResult = generateReCaptchaParameters(reCaptchaToken)

        // Determine the OTP verification method.
        val otpVerificationMethodString = otpVerificationMethod.identityApiAuthVerificationTypeHeaderValue
        // Generate the start registration request body.
        val startRegistrationRequestBodyString = runCatching {
            StartRegistrationRequestBody(
                recaptcha = reCaptchaParameterGenerationResult.nonEnterpriseReCaptchaToken,
                recaptchaevent = reCaptchaParameterGenerationResult.enterpriseReCaptchaEvent,
                userData = UserData(
                    email = trimmedEmail,
                    username = trimmedUsername,
                    password = trimmedNewPassword,
                    firstName = firstName.trim(),
                    lastName = lastName.trim()
                ),
                otpVerificationMethod = otpVerificationMethodString
            ).toJson()
        }.onFailure { e ->
            SalesforceSDKLogger.e(TAG, "Cannot JSON encode start registration request body due to an encoding error with message '${e.message}'.", e)
            return StartRegistrationResult(UnknownError)
        }.getOrNull()

        // Create the start registration request.
        val startRegistrationRequest = RestRequest(
            POST,
            LOGIN,
            "$loginUrl$OAUTH_ENDPOINT_HEADLESS_INIT_REGISTRATION",
            startRegistrationRequestBodyString,
            null
        )

        // Submit the start registration request and fetch the response.
        val startRegistrationResponse = suspendedRestCall(startRegistrationRequest) ?: return StartRegistrationResult(UnknownError)

        // React to the start registration response.
        return when (startRegistrationResponse.isSuccess) {
            true -> {
                startRegistrationResponse.consumeQuietly()
                startRegistrationResponse.asJSONObject().let { response ->
                    StartRegistrationResult(
                        nativeLoginResult = Success,
                        email = response.get("email").toString(),
                        requestIdentifier = response.get("identifier").toString()
                    )
                }
            }

            else -> {
                SalesforceSDKLogger.e(
                    TAG,
                    "Cannot start registration as the Salesforce Identity API returned an unsuccessful HTTP status. '${startRegistrationResponse.asString()}'"
                )
                StartRegistrationResult(UnknownError)
            }
        }
    }

    override suspend fun completeRegistration(
        otp: String,
        requestIdentifier: String,
        otpVerificationMethod: OtpVerificationMethod
    ) = submitAuthorizationRequest(
        authRequestType = AUTH_REQUEST_TYPE_VALUE_USER_REGISTRATION,
        otp = otp,
        identifier = requestIdentifier,
        otpVerificationMethod = otpVerificationMethod
    )

    /**
     * A data class for the Salesforce Identity API start registration request
     * body.
     * @param recaptcha The reCAPTCHA token provided by the reCAPTCHA Android
     * SDK.  This is not used with reCAPTCHA Enterprise
     * @param recaptchaevent The reCAPTCHA parameters for use with reCAPTCHA
     * Enterprise
     * @param userData The start registration request user data
     * @param otpVerificationMethod The one-time-password's delivery method for
     * verification in "email" or "sms"
     */
    private data class StartRegistrationRequestBody(
        val recaptcha: String?,
        val recaptchaevent: ReCaptchaEventRequestParameter?,
        val userData: UserData,
        val otpVerificationMethod: String
    ) {
        fun toJson() = JSONObject().apply {
            put("recaptcha", recaptcha)
            put("recaptchaevent", recaptchaevent?.toJson())
            put("userdata", userData.toJson())
            put("verificationmethod", otpVerificationMethod)
        }

        /**
         * A data class for the Salesforce Identity API start registration
         * request body's user info parameter.
         * @param email A valid, user-entered email address
         * @param username A valid Salesforce username or email
         * @param password The user-entered new password
         * @param firstName The user-entered first name
         * @param lastName The user-entered last name
         */
        data class UserData(
            val email: String,
            val username: String,
            val password: String,
            val firstName: String,
            val lastName: String
        ) {
            fun toJson() = JSONObject().apply {
                put("email", email)
                put("username", username)
                put("password", password)
                put("firstName", firstName)
                put("lastName", lastName)
            }
        }
    }

    // endregion
    // region Salesforce Identity API Headless Forgot Password Flow

    override suspend fun startPasswordReset(
        username: String,
        reCaptchaToken: String,
    ): NativeLoginResult {

        // Validate parameters.
        val trimmedUsername = username.trim()
        trimmedUsername.mapInvalidUsernameToResult()?.let { return it }
        val reCaptchaParameterGenerationResult = generateReCaptchaParameters(reCaptchaToken)

        // Generate the start password reset request body.
        val startPasswordResetRequestBodyString = runCatching {
            StartPasswordResetRequestBody(
                recaptcha = reCaptchaParameterGenerationResult.nonEnterpriseReCaptchaToken,
                recaptchaevent = reCaptchaParameterGenerationResult.enterpriseReCaptchaEvent,
                username = trimmedUsername
            ).toJson()
        }.onFailure { e ->
            SalesforceSDKLogger.e(TAG, "Cannot JSON encode start password reset request body due to an encoding error with message '${e.message}'.", e)
            return UnknownError
        }.getOrNull()

        // Create the start password reset request.
        val startPasswordResetRequest = RestRequest(
            POST,
            LOGIN,
            "$loginUrl$OAUTH_ENDPOINT_HEADLESS_FORGOT_PASSWORD",
            startPasswordResetRequestBodyString,
            null
        )

        // Submit the start password reset request and fetch the response.
        val startPasswordResetResponse = suspendedRestCall(startPasswordResetRequest) ?: return UnknownError

        // React to the start password reset response.
        return when (startPasswordResetResponse.isSuccess) {
            true -> {
                startPasswordResetResponse.consumeQuietly()
                Success
            }

            else -> {
                SalesforceSDKLogger.e(
                    TAG,
                    "Cannot start password reset as the Salesforce Identity API returned an unsuccessful HTTP status. '${startPasswordResetResponse.asString()}'"
                )
                UnknownError
            }
        }
    }

    override suspend fun completePasswordReset(
        username: String,
        otp: String,
        newPassword: String
    ): NativeLoginResult {

        // Validate parameters.
        val trimmedUsername = username.trim()
        trimmedUsername.mapInvalidUsernameToResult()?.let { return it }
        val trimmedNewPassword = newPassword.trim()
        trimmedNewPassword.mapInvalidPasswordToResult()?.let { return it }
        val trimmedOtp = otp.trim()

        // Generate the complete password reset request body.
        val completePasswordResetRequestBodyString = runCatching {
            CompletePasswordResetRequestBody(
                username = trimmedUsername,
                otp = trimmedOtp,
                newPassword = newPassword
            ).toJson()
        }.onFailure { e ->
            SalesforceSDKLogger.e(TAG, "Cannot JSON encode complete password reset request body due to an encoding error with message '${e.message}'.", e)
            return UnknownError
        }.getOrNull()

        // Create the complete password reset request.
        val completePasswordResetRequest = RestRequest(
            POST,
            LOGIN,
            "$loginUrl$OAUTH_ENDPOINT_HEADLESS_FORGOT_PASSWORD",
            completePasswordResetRequestBodyString,
            null
        )

        // Submit the complete password reset request and fetch the response.
        val completePasswordResetResponse = suspendedRestCall(completePasswordResetRequest) ?: return UnknownError

        // React to the complete password reset response.
        return when (completePasswordResetResponse.isSuccess) {
            true -> {
                completePasswordResetResponse.consumeQuietly()
                Success
            }

            else -> {
                SalesforceSDKLogger.e(
                    TAG,
                    "Cannot complete password reset as the Salesforce Identity API returned an unsuccessful HTTP status. '${completePasswordResetResponse.asString()}'"
                )
                UnknownError
            }
        }
    }

    // endregion
    // region Salesforce Identity API Headless, Password-Less Login Via One-Time-Passcode

    override suspend fun submitOtpRequest(
        username: String,
        reCaptchaToken: String,
        otpVerificationMethod: OtpVerificationMethod
    ): OtpRequestResult {

        // Validate parameters.
        val trimmedUsername = username.trim()
        trimmedUsername.mapInvalidUsernameToResult()?.let { return OtpRequestResult(it) }
        val reCaptchaParameterGenerationResult = generateReCaptchaParameters(reCaptchaToken)

        // Determine the OTP verification method.
        val otpVerificationMethodString = otpVerificationMethod.identityApiAuthVerificationTypeHeaderValue
        // Determine the start password-less login request headers.
        val startPasswordLessLoginRequestHeaders = hashMapOf(
            CONTENT_TYPE_HEADER_NAME to CONTENT_TYPE_VALUE_APPLICATION_JSON,
        )
        // Generate the start password-less login request body.
        val startPasswordLessLoginRequestBodyString = runCatching {
            OtpRequestBody(
                recaptcha = reCaptchaParameterGenerationResult.nonEnterpriseReCaptchaToken,
                recaptchaevent = reCaptchaParameterGenerationResult.enterpriseReCaptchaEvent,
                username = trimmedUsername,
                verificationMethod = otpVerificationMethodString
            ).toJson()
        }.onFailure { e ->
            SalesforceSDKLogger.e(TAG, "Cannot JSON encode start password-less login request body due to an encoding error with message '${e.message}'.", e)
            return OtpRequestResult(nativeLoginResult = UnknownError)
        }.getOrNull()

        // Create the start password-less login request.
        val startPasswordLessLoginRequest = RestRequest(
            POST,
            LOGIN,
            "$loginUrl$OAUTH_ENDPOINT_HEADLESS_INIT_PASSWORDLESS_LOGIN",
            startPasswordLessLoginRequestBodyString,
            startPasswordLessLoginRequestHeaders
        )

        // Submit the start password-less login request and fetch the OTP response.
        val startPasswordLessLoginResponse = suspendedRestCall(startPasswordLessLoginRequest) ?: return OtpRequestResult(nativeLoginResult = UnknownError)

        // React to the start password-less login response.
        return when (startPasswordLessLoginResponse.isSuccess) {
            true -> {
                runCatching {
                    // Decode the start password-less login response to obtain the OTP identifier.
                    startPasswordLessLoginResponse.consumeQuietly()
                    OtpRequestResult(Success, startPasswordLessLoginResponse.asJSONObject().get("identifier").toString())
                }.getOrElse { e ->
                    SalesforceSDKLogger.e(TAG, "Cannot JSON decode start password-less login response body due to a decoding error with message '${e.message}'.")
                    startPasswordLessLoginResponse.consumeQuietly()
                    OtpRequestResult(UnknownError)
                }
            }

            else -> {
                SalesforceSDKLogger.e(TAG, "Start password-less login request failure.")
                OtpRequestResult(UnknownError)
            }
        }
    }

    override suspend fun submitPasswordlessAuthorizationRequest(
        otp: String,
        otpIdentifier: String,
        otpVerificationMethod: OtpVerificationMethod
    ) = submitAuthorizationRequest(
        authRequestType = AUTH_REQUEST_TYPE_VALUE_PASSWORDLESS_LOGIN,
        otp = otp,
        identifier = otpIdentifier,
        otpVerificationMethod = otpVerificationMethod
    )

    /**
     * A data class for the start password reset request body.
     * @param recaptcha The reCAPTCHA token provided by the reCAPTCHA Android
     * SDK.  This is not used with reCAPTCHA Enterprise
     * @param recaptchaevent The reCAPTCHA parameters for use with reCAPTCHA
     * Enterprise
     * @param username A valid Salesforce username or email
     */
    private data class StartPasswordResetRequestBody(
        val recaptcha: String?,
        val recaptchaevent: ReCaptchaEventRequestParameter?,
        val username: String
    ) {
        fun toJson() = JSONObject().apply {
            put("recaptcha", recaptcha)
            put("recaptchaevent", recaptchaevent?.toJson())
            put("username", username)
        }
    }

    /**
     * A data class for the complete reset password OTP request body.
     * @param username A valid Salesforce username or email
     * @param otp The user-entered one-time-password previously delivered to the
     * user by the Salesforce Identity API forgot password endpoint
     * @param newPassword The user-entered new password
     */
    private data class CompletePasswordResetRequestBody(
        val username: String,
        val otp: String,
        val newPassword: String
    ) {
        fun toJson() = JSONObject().apply {
            put("username", username)
            put("otp", otp)
            put("newpassword", newPassword)
        }
    }

    /**
     * A data class for the start password-less login request body.
     * @param recaptcha The reCAPTCHA token provided by the reCAPTCHA Android
     * SDK.  This is not used with reCAPTCHA Enterprise
     * @param recaptchaevent The reCAPTCHA parameters for use with reCAPTCHA
     * Enterprise
     * @param username A valid Salesforce username or email
     * @param verificationMethod The OTP verification code's delivery method in
     * "email" or "sms"
     */
    private data class OtpRequestBody(
        val recaptcha: String?,
        val recaptchaevent: ReCaptchaEventRequestParameter?,
        val username: String,
        val verificationMethod: String
    ) {
        fun toJson() = JSONObject().apply {
            put("recaptcha", recaptcha)
            put("recaptchaevent", recaptchaevent?.toJson())
            put("username", username)
            put("verificationmethod", verificationMethod)
        }
    }

    /**
     * A data class for the Salesforce Identity API request body reCAPTCHA event
     * parameters.
     * @param token The reCAPTCHA token provided by the reCAPTCHA Android SDK.
     * This is used only with reCAPTCHA Enterprise
     * @param siteKey The Google Cloud project reCAPTCHA Key's "Id" as shown in
     * Google Cloud Console under "Products & Solutions", "Security" and
     * "reCAPTCHA Enterprise"
     * @param expectedAction The user-initiated "Action Name" for the reCAPTCHA
     * event.  A specific value is not required by Google though it is used in
     * reCAPTCHA Metrics.  "login" is a recommended value from Google
     * documentation
     * @param projectId The Google Cloud project's "Id" as shown in Google Cloud
     * Console
     */
    private data class ReCaptchaEventRequestParameter(
        val token: String,
        val siteKey: String,
        val expectedAction: String = "login",
        val projectId: String
    ) {
        fun toJson() = JSONObject().apply {
            put("token", token)
            put("siteKey", siteKey)
            put("expectedAction", expectedAction)
            put("projectId", projectId)
        }
    }

    // endregion
    // region Private Implementation

    /**
     * Generates a Base64 encoded value by concatenating the provided values
     * with a colon, which is commonly used in the Headless Identity API request
     * headers.
     * @param value1 The left-side value
     * @param value2 The right-side value
     * @return The Base64 encoded value
     */
    private fun generateColonConcatenatedBase64String(
        value1: String,
        value2: String
    ) = encodeToString(
        "$value1:$value2".toByteArray(),
        URL_SAFE or NO_WRAP or NO_PADDING
    )

    /**
     * Generates either the reCAPTCHA token parameter for non-enterprise
     * reCAPTCHA configurations or the reCAPTCHA event parameter for enterprise
     * reCAPTCHA configurations.
     * @param reCaptchaToken A reCAPTCHA token provided by the reCAPTCHA SDK
     * @return The reCAPTCHA parameter generation result with exactly one of the
     * non-enterprise reCAPTCHA parameter, enterprise reCAPTCHA parameter or
     * a native login result for generation failure
     */
    private fun generateReCaptchaParameters(
        reCaptchaToken: String
    ): ReCaptchaParameterGenerationResult {

        // Validate state.
        val reCaptchaSiteKeyId = when {
            reCaptchaSiteKeyId == null -> {
                SalesforceSDKLogger.e(TAG, "A reCAPTCHA site key wasn't and must be provided when using enterprise reCAPATCHA.")
                return ReCaptchaParameterGenerationResult(result = UnknownError)
            }

            else -> reCaptchaSiteKeyId
        }
        val googleCloudProjectId = when {
            googleCloudProjectId == null -> {
                SalesforceSDKLogger.e(TAG, "A Google Cloud project id wasn't and must be provided when using enterprise reCAPATCHA.")
                return ReCaptchaParameterGenerationResult(result = UnknownError)
            }

            else -> googleCloudProjectId
        }

        // Generate the Salesforce Identity API reCAPTCHA request parameters.
        return ReCaptchaParameterGenerationResult(
            nonEnterpriseReCaptchaToken = when {
                isReCaptchaEnterprise -> null
                else -> reCaptchaToken
            },
            enterpriseReCaptchaEvent = when {
                isReCaptchaEnterprise -> ReCaptchaEventRequestParameter(
                    token = reCaptchaToken,
                    siteKey = reCaptchaSiteKeyId,
                    projectId = googleCloudProjectId
                )

                else -> null
            }
        )
    }

    /**
     * The result of generating Salesforce Identity API request reCAPTCHA
     * parameters.
     */
    private data class ReCaptchaParameterGenerationResult(
        /** The reCAPTCHA token parameter for non-enterprise reCAPTCHA */
        val nonEnterpriseReCaptchaToken: String? = null,

        /** The reCAPTCHA event parameter for enterprise reCAPTCHA */
        val enterpriseReCaptchaEvent: ReCaptchaEventRequestParameter? = null,

        /** The error native login result of the reCAPTCHA parameter generation or null for successful generation */
        val result: NativeLoginResult? = null
    )

    /**
     * Submits an authorization request to the Salesforce Identity API and, on
     * success, submits the access token request.
     * @param authRequestType The Salesforce Identity API authorization request
     * type header value
     * @param otp A one-time-password (OTP) previously issued by the Salesforce
     * Identity API
     * @param identifier A OTP or request identifier previously issued by the
     * Salesforce Identity API to match the provided OTP
     * @param otpVerificationMethod The OTP verification method used to obtain
     * the identifier from the Salesforce Identity API
     * @return A native login result indicating success or one of several
     * possible failures, including both in-app and Salesforce Identity API
     * results
     */
    private suspend fun submitAuthorizationRequest(
        authRequestType: String,
        otp: String,
        identifier: String,
        otpVerificationMethod: OtpVerificationMethod
    ): NativeLoginResult {
        // Validate parameters.
        val trimmedOtp = otp.trim()

        // Generate code verifier and code challenge.
        val codeVerifier = getRandom128ByteKey()
        val codeChallenge = getSHA256Hash(codeVerifier)

        // Generate the authorization.
        val authorization = generateColonConcatenatedBase64String(
            value1 = identifier,
            value2 = trimmedOtp
        )

        // Generate the authorization request headers.
        val authorizationRequestHeaders = hashMapOf(
            AUTH_REQUEST_TYPE_HEADER_NAME to authRequestType,
            AUTH_VERIFICATION_TYPE_HEADER_NAME to otpVerificationMethod.identityApiAuthVerificationTypeHeaderValue,
            CONTENT_TYPE_HEADER_NAME to CONTENT_TYPE_VALUE_HTTP_POST,
            AUTHORIZATION to "$AUTH_AUTHORIZATION_VALUE_BASIC $authorization"
        )

        // Generate the authorization request body.
        val authorizationRequestBodyString = createRequestBody(
            RESPONSE_TYPE to CODE_CREDENTIALS,
            CLIENT_ID to clientId,
            REDIRECT_URI to redirectUri,
            CODE_CHALLENGE to codeChallenge,
        )

        // Create the authorization request.
        val authorizationRequest = RestRequest(
            POST,
            LOGIN,
            "$loginUrl$OAUTH_AUTH_PATH",
            authorizationRequestBodyString,
            authorizationRequestHeaders,
        )

        /*
         * Submit the authorization request, fetch the authorization response,
         * submit the access token request and return an appropriate login
         * result.
         */
        return submitAccessTokenRequest(
            authorizationResponse = suspendedRestCall(
                authorizationRequest
            ) ?: return UnknownError,
            codeVerifier = codeVerifier
        )
    }

    /**
     * Reacts to a response from the Headless Identity API authorization
     * endpoint to initiate the token exchange, request a granted access token
     * and create the user's session.
     * @param authorizationResponse The result from the Headless Identity API
     * authorization endpoint
     * @param codeVerifier The code verifier
     * @return The native login result
     */
    private suspend fun submitAccessTokenRequest(
        authorizationResponse: RestResponse,
        codeVerifier: String
    ): NativeLoginResult {
        if (authorizationResponse.isSuccess) {
            val code = authorizationResponse.asJSONObject().get(CODE).toString()
            val useHybridAuthentication = SalesforceSDKManager.getInstance().useHybridAuthentication
            val authEndpoint = authorizationResponse.asJSONObject().get(SFDC_COMMUNITY_URL).toString()
            val tokenRequestBody = createRequestBody(
                CODE to code,
                GRANT_TYPE to if (useHybridAuthentication) HYBRID_AUTH_CODE else AUTHORIZATION_CODE,
                CLIENT_ID to clientId,
                REDIRECT_URI to redirectUri,
                CODE_VERIFIER to codeVerifier,
            )
            authorizationResponse.consumeQuietly()

            val tokenRequest = RestRequest(
                POST,
                LOGIN,
                "$authEndpoint$OAUTH_TOKEN_PATH",  // Full path for unauthenticated request
                tokenRequestBody,
                null, // additionalHttpHeaders
            )

            // Second REST Call - token request with code verifier
            val tokenResponse = suspendedRestCall(tokenRequest) ?: return UnknownError
            return when {
                tokenResponse.isSuccess -> {
                    // Returns Success or UnknownError based on if the user is created.
                    suspendFinishAuthFlow(tokenResponse)
                }

                else -> UnknownError
            }
        } else {
            SalesforceSDKLogger.e(TAG, "Native Login Authorization Error: $authorizationResponse")
            return InvalidCredentials
        }
    }


    // endregion
    // region Private String Parameter Validation

    /**
     * Validates this string is valid password.
     * @return null for valid passwords - The invalid password login result
     * otherwise.
     */
    private fun String.mapInvalidPasswordToResult() =
        when {
            !isValidPassword(trim()) -> InvalidPassword
            else -> null
        }

    /**
     * Validates this string is valid username.
     * @return null for valid usernames - The invalid username login result
     * otherwise.
     */
    private fun String.mapInvalidUsernameToResult() =
        when {
            !isValidUsername(trim()) -> InvalidUsername
            else -> null
        }

    // endregion

    companion object {
        private const val MAX_USERNAME_LENGTH = 80
        private const val MIN_PASSWORD_LENGTH = 8
        private const val MAX_PASSWORD_LENGTH_BYTES = 16000
        private const val USERNAME_REGEX_PATTERN = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"

        private const val AUTH_REQUEST_TYPE_HEADER_NAME = "Auth-Request-Type"
        private const val AUTH_REQUEST_TYPE_VALUE_VALUE_NAMED_USER = "Named-User"
        private const val AUTH_REQUEST_TYPE_VALUE_PASSWORDLESS_LOGIN = "passwordless-login"
        private const val AUTH_REQUEST_TYPE_VALUE_USER_REGISTRATION = "user-registration"

        private const val AUTH_VERIFICATION_TYPE_HEADER_NAME = "Auth-Verification-Type"

        private const val AUTH_AUTHORIZATION_VALUE_BASIC = "Basic"

        private const val CONTENT_TYPE_HEADER_NAME = "Content-Type"
        private const val CONTENT_TYPE_VALUE_HTTP_POST = "application/x-www-form-urlencoded"
        private const val CONTENT_TYPE_VALUE_APPLICATION_JSON = "application/json"

        private const val CODE_CREDENTIALS = "code_credentials"
        private const val TAG = "NativeLoginManager"
    }
}
