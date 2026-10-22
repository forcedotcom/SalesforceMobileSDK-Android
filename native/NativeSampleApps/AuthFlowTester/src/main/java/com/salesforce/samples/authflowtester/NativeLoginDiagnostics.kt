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
 * specific prior written permission of the copyright holder.
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
package com.salesforce.samples.authflowtester

import android.util.Log
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * One OAuth network exchange, reduced to values that are safe to log and show: no tokens, proofs,
 * nonces, passwords or request bodies are kept, only whether the nonce and DPoP headers were present.
 */
data class NativeLoginDiagnosticEvent(
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

/** Redacted record of OAuth requests made while logging in, for failure diagnosis in UI tests. */
object NativeLoginDiagnostics {
    private const val TAG = "NativeLoginDiagnostics"
    private const val DPOP_HEADER = "DPoP"
    private const val NONCE_HEADER = "DPoP-Nonce"
    private const val PEEK_LIMIT_BYTES = 4096L
    private val ERROR_FIELD = Regex("\"error\"\\s*:\\s*\"([A-Za-z0-9_.-]{1,64})\"")
    private val ERROR_DESCRIPTION_FIELD = Regex("\"error_description\"\\s*:\\s*\"([^\"]{1,120})")

    private val recorded = CopyOnWriteArrayList<NativeLoginDiagnosticEvent>()
    private val retryCounter = AtomicInteger(0)

    val events: List<NativeLoginDiagnosticEvent> get() = recorded.toList()

    /** Single-line summary of the events, suitable for a label and for assertion messages. */
    val summary: String
        get() = events.joinToString(separator = "; ") { it.toString() }.ifEmpty { "no OAuth requests" }

    fun reset() {
        recorded.clear()
        retryCounter.set(0)
    }

    /** Records [request]/[response] if it targets an OAuth endpoint. Never throws. */
    fun record(request: Request, response: Response) {
        runCatching {
            val path = request.url.encodedPath
            if (!path.contains("/services/oauth2/")) return
            val stage = when {
                path.endsWith("/token") -> "token"
                path.contains("/authorize") -> "authorize"
                else -> path.substringAfterLast('/')
            }
            // peekBody copies a bounded prefix and leaves the real body unread for the caller.
            // Only failure bodies are inspected; they carry error codes, never tokens.
            val failureBody = if (response.isSuccessful) null else response.peekBody(PEEK_LIMIT_BYTES).string()
            val error = failureBody?.let { ERROR_FIELD.find(it)?.groupValues?.get(1) }
            val description = failureBody?.let { ERROR_DESCRIPTION_FIELD.find(it)?.groupValues?.get(1) }
            val event = NativeLoginDiagnosticEvent(
                stage = stage,
                status = response.code,
                oauthError = error,
                dpopHeaderSent = request.header(DPOP_HEADER) != null,
                nonceHeaderReceived = response.header(NONCE_HEADER) != null,
                retryIndex = retryCounter.getAndIncrement(),
                errorDescription = description,
            )
            recorded.add(event)
            Log.i(TAG, event.toString())
        }
    }
}
