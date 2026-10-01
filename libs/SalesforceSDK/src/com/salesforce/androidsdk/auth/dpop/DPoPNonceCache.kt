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

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory nonce cache for DPoP proof JWTs.
 *
 * RFC 9449 §8 allows the AS/RS to supply a `DPoP-Nonce` response header. The
 * client must echo that value in the `nonce` claim of its next DPoP proof for the
 * same endpoint. This cache is keyed by `(credentialsIdentifier, host)` so that
 * the AS nonce (login host) and RS nonce (instance host) never overwrite each other.
 *
 * Some resource servers (e.g. communities/Experience Cloud sites) never issue their
 * own `DPoP-Nonce` challenge; they expect the client to carry forward whatever nonce
 * was last issued for that credential, regardless of host. To support that, [get]
 * falls back to the most recently stored nonce for [credentialsIdentifier] (any host)
 * when there is no entry for the exact `(credentialsIdentifier, host)` pair. An exact
 * host match always takes precedence over the fallback. This matches the credential-scoped
 * fallback used by the iOS implementation (`DPoPNonceCache.swift`'s `nonce(htu:scope:) ??
 * latest(forScope:)`), layered on top of Android's existing per-host isolation.
 */
object DPoPNonceCache {

    private val cache = ConcurrentHashMap<String, String>()
    private val latestByCredential = ConcurrentHashMap<String, String>()

    private fun cacheKey(credentialsIdentifier: String, host: String) =
        "$credentialsIdentifier|$host"

    /**
     * Returns the nonce cached for the exact `(credentialsIdentifier, host)` pair, or,
     * if none was ever stored for that host, the most recently stored nonce for
     * [credentialsIdentifier] on any host. Returns null if neither is available.
     */
    fun get(credentialsIdentifier: String, host: String): String? =
        cache[cacheKey(credentialsIdentifier, host)] ?: latestByCredential[credentialsIdentifier]

    fun store(credentialsIdentifier: String, host: String, nonce: String) {
        cache[cacheKey(credentialsIdentifier, host)] = nonce
        latestByCredential[credentialsIdentifier] = nonce
    }

    fun clear(credentialsIdentifier: String) {
        cache.keys.removeAll { it.startsWith("$credentialsIdentifier|") }
        latestByCredential.remove(credentialsIdentifier)
    }

    fun clearAll() {
        cache.clear()
        latestByCredential.clear()
    }
}
