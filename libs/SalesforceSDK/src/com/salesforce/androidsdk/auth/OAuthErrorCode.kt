/*
 * Copyright (c) 2026-present, salesforce.com, inc.
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

/**
 * Typed representation of the OAuth token endpoint error values defined by the
 * Salesforce server in OauthErrorCode.java (core/identity-common-api).
 *
 * Use [from] to parse the raw `error` string from a token endpoint response.
 */
enum class OAuthErrorCode(val error: String?) {
    ACCESS_DENIED("access_denied"),
    APP_BLOCKED("app_blocked"),
    APP_NOT_FOUND("app_not_found"),
    AUTHORIZATION_PENDING("authorization_pending"),
    BAD_JTI_CLAIM("bad_jti_claim"),
    APP_ATTESTATION_FAILED("app_attest_failed"),
    APP_ATTESTATION_FAILED_RETRY("app_attest_failed_retry"),
    ECAPP_POLICY_NOT_FOUND("ecapp_policy_not_found"),
    EXCEEDED_REGISTRATION_LIMIT("exceeded_registration_limit"),
    FAIL_CLOSE_APP_BLOCKED("fail_close_app_blocked"),
    FAILED_REGISTRATION("failed_registration"),
    IMMEDIATE_UNSUCCESSFUL("immediate_unsuccessful"),
    INSTALLATION_ERROR("installation_error"),
    INVALID_APP_ACCESS("invalid_app_access"),
    INVALID_ASSERTION_TYPE("invalid_assertion_type"),
    INVALID_BASIC_AUTH_HEADER("invalid_basic_auth_header"),
    INVALID_DPOP_PROOF("invalid_dpop_proof"),
    INVALID_CLIENT("invalid_client"),
    INVALID_CLIENT_ID("invalid_client_id"),
    INVALID_DISTRIBUTION_STATE("invalid_distribution_state"),
    INVALID_EXPID("invalid_expid"),
    INVALID_GRANT("invalid_grant"),
    INVALID_OTP("invalid_otp"),
    INVALID_REQUEST("invalid_request"),
    INVALID_SCOPE("invalid_scope"),
    INVALID_SESSION_LEVEL("invalid_session_level"),
    INVALID_TOKEN("invalid_token"),
    LOGIN_ERROR("login_error"),
    OAUTH_FLOW_DISABLED("oauth_flow_disabled"),
    OAUTH_POLICY_NOT_FOUND("oauth_policy_not_found"),
    OTP_ERROR("otp_error"),
    REDIRECT_URI_MISSING("redirect_uri_missing"),
    REDIRECT_URI_MISMATCH("redirect_uri_mismatch"),
    REGISTRATION_ERROR("registration_error"),
    SERVER_ERROR("server_error"),
    SERVICE_UNAVAILABLE("service_unavailable"),
    SLOW_DOWN("slow_down"),
    SYSTEM_DOWN("system_down"),
    UNKNOWN_ERROR("unknown_error"),
    UNSUPPORTED_EXPID("unsupported_expid"),
    UNSUPPORTED_GRANT_TYPE("unsupported_grant_type"),
    UNSUPPORTED_RESPONSE_TYPE("unsupported_response_type"),
    UNSUPPORTED_TOKEN_TYPE("unsupported_token_type"),
    USE_DPOP_NONCE("use_dpop_nonce"),
    UNKNOWN(null),
    ;

    companion object {
        /**
         * Returns the [OAuthErrorCode] whose [error] string matches [error], or
         * [UNKNOWN] if the value is null, empty, or not recognized.
         */
        @JvmStatic
        fun from(error: String?): OAuthErrorCode =
            if (error.isNullOrEmpty()) UNKNOWN
            else entries.firstOrNull { it.error == error } ?: UNKNOWN
    }
}
