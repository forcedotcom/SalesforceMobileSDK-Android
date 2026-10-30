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

import android.content.Context
import android.util.Log
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.HttpAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Request

/** Records final SDK request User-Agents for UI-test assertions only. */
class TokenRequestCapturingHttpAccess(context: Context) : HttpAccess(context, null) {

    override fun createNewClientBuilder(): OkHttpClient.Builder =
        super.createNewClientBuilder().eventListenerFactory { call ->
            // Taken at call creation, not execution: queued calls belong to their original session.
            val enabled = SalesforceSDKManager.getInstance().isUiTesting
            val ticket = captureStore.beginRequest(userKey(call.request()))
            object : EventListener() {
                override fun requestHeadersEnd(call: Call, request: Request) {
                    // Runs once per wire exchange, after all network interceptors (including
                    // RestClient's appended UserAgentInterceptor), also for retries/redirects.
                    captureStore.record(
                        request.url.encodedPath,
                        userKey(request),
                        request.header(USER_AGENT_HEADER),
                        enabled && SalesforceSDKManager.getInstance().isUiTesting,
                        ticket,
                    )
                }
            }
        }

    companion object {
        private const val USER_AGENT_HEADER = "User-Agent"
        private val captureStore = RequestUserAgentCaptureStore()

        val capturedRequests = captureStore.capturedRequests
        val lastTokenRequestUserAgent = captureStore.lastTokenRequestUserAgent
        val capturedTokenRequestCount = captureStore.capturedTokenRequestCount
        val highWaterSequence = captureStore.highWaterSequence

        /** Clears observations, sequence boundaries, and retired owners at UI-test launch. */
        fun reset() = captureStore.reset()

        /** Allows captures for an owner that has authenticated again after logout. */
        fun userLoggedIn(user: UserAccount) {
            userKey(user)?.let(captureStore::userLoggedIn)
        }

        /** Removes an owner's observations and rejects its late in-flight requests. */
        fun retireUser(user: UserAccount) {
            userKey(user)?.let(captureStore::retireUser)
        }

        private fun userKey(request: Request): String? =
            userKey(request.tag(UserAccount::class.java))
                ?: request.tag(HttpAccess.RequestUserIdentity::class.java)?.let {
                    userKey(it.orgId, it.userId)
                }

        private fun userKey(user: UserAccount?): String? = userKey(user?.orgId, user?.userId)

        private fun userKey(orgId: String?, userId: String?): String? {
            return if (orgId.isNullOrBlank() || userId.isNullOrBlank()) null else "$orgId/$userId"
        }
    }
}

/** A path-only observation; no other request data is retained. */
data class CapturedRequestUserAgent(
    val path: String,
    val userKey: String,
    val userAgent: String,
    val seq: Long,
)

internal class RequestUserAgentCaptureStore {
    private val lock = Any()
    private var sequence = 0L
    private val retiredOwners = mutableSetOf<String>()
    private var session = 0L
    private val ownerGenerations = mutableMapOf<String, Long>()
    internal data class RequestTicket(val session: Long, val owner: String, val generation: Long)
    fun beginRequest(userKey: String?): RequestTicket = synchronized(lock) {
        val owner = userKey?.takeIf { it.isNotBlank() } ?: UNKNOWN_OWNER
        RequestTicket(session, owner, ownerGenerations[owner] ?: 0L)
    }
    private val highWaterSequenceState = MutableStateFlow(0L)
    val highWaterSequence = highWaterSequenceState.asStateFlow()
    private val capturedRequestsState = MutableStateFlow<List<CapturedRequestUserAgent>>(emptyList())
    val capturedRequests = capturedRequestsState.asStateFlow()
    private val lastTokenRequestUserAgentState = MutableStateFlow<String?>(null)
    val lastTokenRequestUserAgent = lastTokenRequestUserAgentState.asStateFlow()
    private val capturedTokenRequestCountState = MutableStateFlow(0)
    val capturedTokenRequestCount = capturedTokenRequestCountState.asStateFlow()
    private var lastTokenRequest: CapturedRequestUserAgent? = null

    fun record(
        path: String?, userKey: String?, userAgent: String?, enabled: Boolean,
        ticket: RequestTicket = beginRequest(userKey),
    ) {
        if (!enabled || path.isNullOrBlank() || userAgent.isNullOrBlank()) return
        if (!CONTRACT_PATH.matches(path)) {
            Log.d(TAG, "Ignoring request capture outside contract: $path")
            return
        }
        synchronized(lock) {
            val owner = userKey?.takeIf { it.isNotBlank() } ?: UNKNOWN_OWNER
            if (ticket.session != session || ticket.owner != owner ||
                ticket.generation != (ownerGenerations[owner] ?: 0L) || owner in retiredOwners
            ) return
            val entry = CapturedRequestUserAgent(path, owner, userAgent, ++sequence)
            highWaterSequenceState.value = sequence
            capturedRequestsState.value = (capturedRequestsState.value + entry).takeLast(CAPACITY)
            if (path.endsWith(TOKEN_ENDPOINT_PATH)) {
                // Eviction must not reset the lifetime count used by existing RTR/DPoP tests.
                lastTokenRequest = entry
                lastTokenRequestUserAgentState.value = entry.userAgent
                capturedTokenRequestCountState.value += 1
            }
        }
    }

    fun reset() = synchronized(lock) {
        session += 1
        ownerGenerations.clear()
        sequence = 0L
        highWaterSequenceState.value = 0L
        retiredOwners.clear()
        capturedRequestsState.value = emptyList()
        lastTokenRequest = null
        lastTokenRequestUserAgentState.value = null
        capturedTokenRequestCountState.value = 0
    }

    fun userLoggedIn(userKey: String) = synchronized(lock) {
        if (retiredOwners.remove(userKey)) {
            // Requests created during retirement must not become eligible on relogin either.
            ownerGenerations[userKey] = (ownerGenerations[userKey] ?: 0L) + 1
        }
        Unit
    }

    fun retireUser(userKey: String) = synchronized(lock) {
        if (userKey.isBlank() || userKey == UNKNOWN_OWNER) return@synchronized
        retiredOwners.add(userKey)
        ownerGenerations[userKey] = (ownerGenerations[userKey] ?: 0L) + 1
        capturedRequestsState.value = capturedRequestsState.value.filterNot { it.userKey == userKey }
        if (lastTokenRequest?.userKey == userKey) {
            lastTokenRequest = capturedRequestsState.value.lastOrNull { it.path.endsWith(TOKEN_ENDPOINT_PATH) }
            lastTokenRequestUserAgentState.value = lastTokenRequest?.userAgent
        }
    }

    companion object {
        private const val TAG = "RequestUserAgentCapture"
        private const val CAPACITY = 200
        private const val UNKNOWN_OWNER = "unknown"
        private const val TOKEN_ENDPOINT_PATH = "/services/oauth2/token"
        // A trailing-slash community base produces an empty prefix segment on the wire.
        private val CONTRACT_PATH = Regex(
            "^/(?:[^/?#]*/)*(?:services/oauth2/(?:token|revoke)|services/data/[^?#]+|id/[^/?#]+/[^/?#]+)$"
        )
    }
}
