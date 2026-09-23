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
package com.salesforce.androidsdk.auth.dpop

import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.OAuth2
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Public convenience API for app developers stamping DPoP/Bearer authorization
 * headers on HTTP requests built outside the SDK's RestClient.
 *
 * Gating is per-credential via [DPoPKeyManager.shouldAttachDPoP], not the global
 * [com.salesforce.androidsdk.app.SalesforceSDKManager.useDPoP] flag. A DPoP-bound
 * credential normally carries proofs regardless of that flag; when a UI session exists,
 * [SalesforceSDKManager.shouldUseUiSidBearerForPath] may select clean UI-session Bearer
 * authentication for a request path instead.
 */
object DPoPRequestDecorator {

    private const val TAG = "DPoPRequestDecorator"
    const val DPOP_HEADER = "DPoP"
    const val DPOP_NONCE_HEADER = "DPoP-Nonce"
    private const val NONCE_ERROR_VALUE = "use_dpop_nonce"

    /**
     * Stamps [Authorization] and, if the account is DPoP-bound, a [DPoP] proof header
     * on [builder]. No-op when [UserAccount.getAuthToken] is null or empty. Rejects an
     * incomplete DPoP credential with [IOException] before mutating [builder].
     */
    fun applyAuthHeaders(builder: Request.Builder, userAccount: UserAccount) {
        val tokenType = userAccount.tokenType
        val credentialsIdentifier = userAccount.credentialsIdentifier
        requireCompleteDPoPCredentials(credentialsIdentifier, tokenType)

        val authToken = userAccount.authToken ?: return
        if (authToken.isEmpty()) return

        applyAuthHeaders(
            builder,
            credentialsIdentifier,
            tokenType,
            authToken,
            userAccount.uiSid,
        )
    }

    /**
     * Applies the complete authentication header set for one request. A DPoP credential may use
     * `Bearer <ui_sid>` when the SDK manager's path policy selects it; otherwise normal DPoP or
     * Bearer access-token authentication is retained.
     */
    @JvmName("applyAuthHeaders")
    internal fun applyAuthHeaders(
        builder: Request.Builder,
        credentialsIdentifier: String?,
        tokenType: String?,
        authToken: String?,
        uiSid: String?,
    ) {
        requireCompleteDPoPCredentials(credentialsIdentifier, tokenType)
        if (authToken.isNullOrEmpty()) return

        val requestPath = builder.build().url.encodedPath
        val useUiSidBearer = DPoPKeyManager.isDPoPTokenType(tokenType) &&
            !uiSid.isNullOrBlank() &&
            shouldUseUiSidBearerForPath(requestPath)

        // A replay starts from the previously authenticated request. Remove any old proof before
        // selecting the complete header set for this attempt so switching to UI-session Bearer is
        // always clean.
        builder.removeHeader(DPOP_HEADER)
        if (useUiSidBearer) {
            OAuth2.addAuthorizationHeader(builder, uiSid, "Bearer")
            return
        }

        OAuth2.addAuthorizationHeader(builder, authToken, tokenType)
        attachProof(builder, credentialsIdentifier, tokenType, authToken)
    }

    private fun shouldUseUiSidBearerForPath(path: String): Boolean = try {
        SalesforceSDKManager.getInstance().shouldUseUiSidBearerForPath(path)
    } catch (_: Exception) {
        SalesforceSDKLogger.w(
            TAG,
            "UI session path policy failed; using DPoP authentication",
        )
        false
    }

    /**
     * Stores [DPOP_NONCE_HEADER] from [response] in [DPoPNonceCache] when present.
     * No-op if the header, [credentialsIdentifier], or [host] is missing or empty.
     */
    fun harvestNonce(response: Response, credentialsIdentifier: String?, host: String?) {
        if (credentialsIdentifier.isNullOrEmpty() || host.isNullOrEmpty()) return
        val nonce = response.header(DPOP_NONCE_HEADER) ?: return
        if (nonce.isEmpty()) return
        DPoPNonceCache.store(credentialsIdentifier, host, nonce)
    }

    /**
     * Returns true if [response] is a DPoP nonce challenge per RFC 9449 §8:
     * - 400 with body containing `error=use_dpop_nonce`
     * - 401 with a non-empty `DPoP-Nonce` response header
     */
    fun isNonceChallenge(response: Response): Boolean {
        val code = response.code
        if (code != 400 && code != 401) return false
        if (code == 401) {
            val nonce = response.header(DPOP_NONCE_HEADER)
            if (!nonce.isNullOrEmpty()) return true
        }
        val body = response.peekBody(Long.MAX_VALUE).string()
        return body.contains(NONCE_ERROR_VALUE)
    }

    /**
     * Package-private helper used by RestClient.OAuthRefreshInterceptor to delegate
     * the proof-building step without constructing a UserAccount. An incomplete DPoP
     * credential throws [IOException] so OkHttp reports it through normal request failure.
     */
    @JvmName("attachProof")
    internal fun attachProof(
        builder: Request.Builder,
        credentialsIdentifier: String?,
        tokenType: String?,
        authToken: String?
    ) {
        requireCompleteDPoPCredentials(credentialsIdentifier, tokenType)
        if (!DPoPKeyManager.shouldAttachDPoP(credentialsIdentifier, tokenType)) return
        try {
            val request = builder.build()
            val url = request.url.toString()
            val method = request.method
            val htu = DPoPURLHelper.canonicalize(url)
            val host = request.url.host
            val alias = DPoPKeyManager.aliasForCredentialsIdentifier(credentialsIdentifier!!)
            val keyPair = DPoPKeyManager.generateOrLoadKeyPair(alias)
            val nonce = DPoPNonceCache.get(credentialsIdentifier, host)
            val proof = DPoPProofBuilder.buildProof(method, htu, keyPair, nonce, authToken)
            builder.header(DPOP_HEADER, proof)
        } catch (e: Exception) {
            SalesforceSDKLogger.e(TAG, "Failed to attach DPoP proof", e)
        }
    }

    private fun requireCompleteDPoPCredentials(
        credentialsIdentifier: String?,
        tokenType: String?
    ) {
        if (!DPoPKeyManager.hasCompleteDPoPCredentials(credentialsIdentifier, tokenType)) {
            throw IncompleteDPoPCredentialsException()
        }
    }

    private class IncompleteDPoPCredentialsException : IOException(
        "DPoP credentials require a non-blank credentials identifier"
    )
}
