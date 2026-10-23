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

import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.dpop.DPoPRequestDecorator
import com.salesforce.androidsdk.rest.RestResponse
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * One Native Login OAuth exchange, reduced to values that are safe to log and show: no tokens,
 * codes, proofs, nonces, cookies, passwords or request bodies are kept, only whether the DPoP and
 * nonce headers were present.
 */
internal data class NativeLoginDiagnosticEvent(
    val stage: String,
    val status: Int,
    val oauthError: String?,
    val dpopHeaderSent: Boolean,
    val nonceHeaderReceived: Boolean,
    val retryIndex: Int,
    val errorDescription: String? = null,
) {
    override fun toString() = buildString {
        append("stage=$stage status=$status")
        oauthError?.let { append(" error=$it") }
        append(" dpop=$dpopHeaderSent nonce=$nonceHeaderReceived retry=$retryIndex")
        errorDescription?.let { append(" description=\"$it\"") }
    }
}

/**
 * Debug-build record of the Native Login authorize and token requests, shown by
 * [com.salesforce.androidsdk.ui.DebugNativeLoginActivity] to explain a rejected login.
 */
internal object NativeLoginDiagnostics {
    private const val TAG = "NativeLoginDiagnostics"
    private const val PEEK_LIMIT_BYTES = 4096L
    private val ERROR_FIELD = Regex("\"error\"\\s*:\\s*\"([A-Za-z0-9_.-]{1,64})\"")
    private val ERROR_DESCRIPTION_FIELD = Regex("\"error_description\"\\s*:\\s*\"([^\"]{1,120})")

    private val recorded = CopyOnWriteArrayList<NativeLoginDiagnosticEvent>()
    private val requestCounter = AtomicInteger(0)

    val events: List<NativeLoginDiagnosticEvent> get() = recorded.toList()

    /** Single-line summary of the events, for a label and for logs. */
    val summary: String
        get() = events.joinToString(separator = "; ").ifEmpty { "no OAuth requests" }

    /** The last OAuth error and description, for example `400 invalid_request: ...`, or null. */
    val lastFailure: String?
        get() = events.lastOrNull { it.status >= 400 }?.let { event ->
            buildString {
                append("HTTP ${event.status}")
                event.oauthError?.let { append(" $it") }
                event.errorDescription?.let { append(": $it") }
            }
        }

    fun reset() {
        recorded.clear()
        requestCounter.set(0)
    }

    /** Records the exchange behind [response] in debug builds.  Never throws. */
    fun record(response: RestResponse) {
        runCatching {
            if (!SalesforceSDKManager.getInstance().isDebugBuild) return
            val raw = response.rawResponse
            val path = raw.request.url.encodedPath
            val stage = when {
                path.endsWith("/token") -> "token"
                path.contains("/authorize") -> "authorize"
                else -> path.substringAfterLast('/')
            }
            // peekBody copies a bounded prefix and leaves the body for the caller.  Only failure
            // bodies are read; they carry error codes, never tokens.
            val failureBody = if (raw.isSuccessful) null else raw.peekBody(PEEK_LIMIT_BYTES).string()
            val event = NativeLoginDiagnosticEvent(
                stage = stage,
                status = raw.code,
                oauthError = failureBody?.let { ERROR_FIELD.find(it)?.groupValues?.get(1) },
                dpopHeaderSent = raw.request.header(DPoPRequestDecorator.DPOP_HEADER) != null,
                nonceHeaderReceived = raw.header(DPoPRequestDecorator.DPOP_NONCE_HEADER) != null,
                retryIndex = requestCounter.getAndIncrement(),
                errorDescription = failureBody?.let { ERROR_DESCRIPTION_FIELD.find(it)?.groupValues?.get(1) },
            )
            recorded.add(event)
            SalesforceSDKLogger.i(TAG, event.toString())
        }
    }
}
