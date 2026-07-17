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

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class DPoPNonceCacheTest {

    private val id = "user1"
    private val loginHost = "login.salesforce.com"
    private val instanceHost = "org.my.salesforce.com"

    @After
    fun tearDown() {
        DPoPNonceCache.clearAll()
    }

    @Test
    fun test_givenCredentialsIdentifierAndHost_whenStoreAndGet_thenNonceReturned() {
        DPoPNonceCache.store(id, loginHost, "abc123")
        assertEquals("abc123", DPoPNonceCache.get(id, loginHost))
    }

    @Test
    fun test_givenStoredNonce_whenClear_thenAllHostsForThatIdAreRemoved() {
        DPoPNonceCache.store(id, loginHost, "as-nonce")
        DPoPNonceCache.store(id, instanceHost, "rs-nonce")
        DPoPNonceCache.clear(id)
        assertNull(DPoPNonceCache.get(id, loginHost))
        assertNull(DPoPNonceCache.get(id, instanceHost))
    }

    @Test
    fun test_givenTwoDifferentIdentifiers_whenStoreForBoth_thenValuesAreIsolated() {
        val id2 = "user2"
        DPoPNonceCache.store(id, loginHost, "nonce-one")
        DPoPNonceCache.store(id2, loginHost, "nonce-two")
        assertEquals("nonce-one", DPoPNonceCache.get(id, loginHost))
        assertEquals("nonce-two", DPoPNonceCache.get(id2, loginHost))
        DPoPNonceCache.clear(id)
        assertNull(DPoPNonceCache.get(id, loginHost))
        assertEquals("nonce-two", DPoPNonceCache.get(id2, loginHost))
    }

    /*
     * Regression: AS nonce (login host) and RS nonce (instance host) must not overwrite each
     * other. Previously the cache was keyed only by credentialsIdentifier, so a token-refresh
     * response nonce would clobber the API-server nonce, causing the replayed API request to
     * be rejected with 401 by the RS.
     */
    @Test
    fun test_givenASAndRSNonces_whenStoredSeparately_thenEachHostRetainsItsOwnNonce() {
        val asNonce = "as-nonce-xyz"
        val rsNonce = "rs-nonce-abc"
        DPoPNonceCache.store(id, loginHost, asNonce)
        DPoPNonceCache.store(id, instanceHost, rsNonce)
        assertEquals("AS nonce must be retrievable by login host", asNonce, DPoPNonceCache.get(id, loginHost))
        assertEquals("RS nonce must be retrievable by instance host", rsNonce, DPoPNonceCache.get(id, instanceHost))
    }

    @Test
    fun test_givenConcurrentWrites_whenMultipleThreads_thenNoRace() {
        val threadCount = 20
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        for (i in 0 until threadCount) {
            executor.submit {
                DPoPNonceCache.store(id, instanceHost, "nonce-$i")
                latch.countDown()
            }
        }

        latch.await()
        executor.shutdown()

        val result = DPoPNonceCache.get(id, instanceHost)
        assert(result != null && result.startsWith("nonce-")) {
            "Expected a nonce-N value, got: $result"
        }
    }
}
