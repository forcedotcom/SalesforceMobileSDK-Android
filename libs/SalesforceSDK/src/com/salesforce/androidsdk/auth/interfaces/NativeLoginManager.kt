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
package com.salesforce.androidsdk.auth.interfaces

import android.content.Intent

enum class NativeLoginResult {
    InvalidUsername,      // Username does not meet Salesforce criteria (length, email format, ect).
    InvalidPassword,      // Password does not meet the weakest Salesforce criteria.
    InvalidCredentials,   // Username/password combination is incorrect.
    UnknownError,
    Success,
}

/**
 *  Manage native login flow.
 */
interface NativeLoginManager {

    /**
     * If the native login view should show a back button.
     */
    val shouldShowBackButton: Boolean

    /**
     * The username of the locked account or null.  Can be used to pre-populate the username field
     * or in a message telling the user which account biometric will unlock.
     */
    val biometricAuthenticationUsername: String?

    /**
     * Initiate a login with user provided username and password.
     *
     * @param username User provided Salesforce username.
     * @param password User provided Salesforce password.
     * @return NativeLoginResult
     */
    suspend fun login(username: String, password: String): NativeLoginResult

    /**
     * Use this function to get the intent for fallback web based authentication.
     *
     * In your native login activity start this intent in such a way as to [get a result callback](https://developer.android.com/training/basics/intents/result).
     * A result code of `Activity.RESULT_OK` means the user has successfully authenticated via the webview and you
     * can finish your activity.
     *
     * @return the intent to be started for fallback web based authentication.
     */
    fun getFallbackWebAuthenticationIntent(): Intent

    // region Headless, Password-Less Login Via One-Time-Passcode

    /**
     * Submits a request for a one-time-passcode to the Salesforce headless
     * password-less login flow. This fulfills step three of the headless
     * password-less login flow.
     *
     * See https://help.salesforce.com/s/articleView?id=sf.remoteaccess_headless_passwordless_login_public_clients.htm&type=5
     *
     * @param username A valid Salesforce username.  Note that email may be used
     * for community users
     * @param reCaptchaToken A reCAPTCHA token provided by the reCAPTCHA SDK
     * @param otpVerificationMethod: The delivery method for the OTP
     * @return An OTP request result with the overall login result and the OTP
     * identifier for successful OTP requests
     */
    suspend fun submitOtpRequest(
        username: String,
        reCaptchaToken: String,
        otpVerificationMethod: OtpVerificationMethod
    ): OtpRequestResult

    /**
     * Submits a request for a one-time-passcode to the Salesforce headless
     * password-less login flow. This fulfills steps eight, eleven and thirteen
     * of the headless password-less login flow.
     *
     * See https://help.salesforce.com/s/articleView?id=sf.remoteaccess_headless_passwordless_login_public_clients.htm&type=5
     *
     * @param otp A user-entered OTP
     * @param otpIdentifier The OTP identifier issued by the Headless Identity
     * API
     * @param otpVerificationMethod The OTP verification method used to obtain
     * the OTP identifier
     * @return A login result indicating the outcome of the authorization and
     * access token requests
     */
    suspend fun submitPasswordlessAuthorizationRequest(
        otp: String,
        otpIdentifier: String,
        otpVerificationMethod: OtpVerificationMethod
    ): NativeLoginResult
}

/**
 * An OTP request result.
 * @param nativeLoginResult The overall result of the OTP request
 * @param otpIdentifier On success result, the OTP identifier provided by the
 * API
 */
data class OtpRequestResult(
    val nativeLoginResult: NativeLoginResult,
    val otpIdentifier: String? = null
)

/**
 * The possible OTP verification methods.
 * @param identityApiAuthVerificationTypeHeaderValue The expected string value
 * when used in the Salesforce Identity API `Auth-Verification-Type` request
 * header
 */
@Suppress("unused")
enum class OtpVerificationMethod(val identityApiAuthVerificationTypeHeaderValue: String) {
    Email("email"),
    Sms("sms")
}
