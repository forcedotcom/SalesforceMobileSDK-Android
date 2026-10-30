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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class RequestUserAgentCaptureStoreTest {
    @Test
    fun test_givenTrailingSlashCommunityBase_whenCaptured_thenExactOAuthPathsAndTokenSummaryRemain() {
        val store = RequestUserAgentCaptureStore()
        val paths = listOf(
            "/portal//services/oauth2/token",
            "/portal//services/oauth2/revoke",
            "/portal%20site//services/oauth2/token",
            "/portal%20site//services/oauth2/revoke",
        )
        paths.forEachIndexed { index, path ->
            store.record(path, "org/a", "agent-$index", true, store.beginRequest("org/a"))
        }
        assertEquals(paths, store.capturedRequests.value.map { it.path })
        assertEquals((1L..4L).toList(), store.capturedRequests.value.map { it.seq })
        assertEquals(4L, store.highWaterSequence.value)
        assertEquals(2, store.capturedTokenRequestCount.value)
        assertEquals("agent-2", store.lastTokenRequestUserAgent.value)

        store.record(TOKEN, "org/b", "other-owner", true)
        store.retireUser("org/b")
        assertEquals("agent-2", store.lastTokenRequestUserAgent.value)
        assertEquals(paths, store.capturedRequests.value.map { it.path })
    }

    @Test
    fun test_givenCompleteObservations_whenCaptured_thenOnlyContractFieldsArePublished() {
        val store = Store()
        listOf(TOKEN, "/services/oauth2/revoke", VERSIONS, REST, "/id/org/user",
            "/community/services/oauth2/token", "/community$VERSIONS", "/community/id/org/user").forEach {
            store.record(it, null, "final-agent")
        }
        assertEquals(8, store.entries.size)
        assertEquals((1L..8L).toList(), store.entries.map { it["seq"] })
        store.entries.forEach {
            assertEquals(setOf("path", "userKey", "userAgent", "seq"), it.keys)
            assertEquals("unknown", it["userKey"])
            assertEquals("final-agent", it["userAgent"])
        }
    }

    @Test
    fun test_givenDisabledOrIncompleteOrUnrelatedObservations_whenCaptured_thenIgnored() {
        val store = Store()
        store.record(TOKEN, "org/a", "agent", enabled = false)
        store.record(null, "org/a", "agent")
        store.record(TOKEN, "org/a", null)
        store.record(TOKEN, "org/a", "")
        listOf("", "/", "/services/oauth2/token/extra", "/services/datax/v1",
            "/id/org", "/id/org/user/extra", "$TOKEN?query=value",
            "https://example.invalid$TOKEN").forEach { store.record(it, null, "agent") }
        assertTrue(store.entries.isEmpty())
        assertEquals(0, store.count)
        assertEquals(null, store.lastToken)
        store.record(TOKEN, "", "agent")
        assertEquals("unknown", store.entries.single()["userKey"])
        assertEquals(1L, store.entries.single()["seq"])
    }

    @Test
    fun test_givenMoreThanCapacity_whenCaptured_thenNewest200AndLifetimeTokenSummaryRemain() {
        val store = Store()
        store.record(TOKEN, "org/a", "token-agent")
        repeat(205) { store.record(REST, "org/b", "rest-agent") }
        assertEquals(200, store.entries.size)
        assertEquals((7L..206L).toList(), store.entries.map { it["seq"] })
        assertEquals(1, store.count)
        assertEquals("token-agent", store.lastToken)
    }

    @Test
    fun test_givenRetiredOwner_whenLateCaptureAndLoginOccur_thenDropUntilLogin() {
        val store = Store()
        store.record(TOKEN, "org/a", "a-agent")
        store.record(REST, "org/b", "b-agent")
        store.record(TOKEN, null, "unknown-agent")
        store.retire("org/a")
        store.record(TOKEN, "org/a", "late-agent")
        assertEquals(listOf("org/b", "unknown"), store.entries.map { it["userKey"] })
        assertEquals(2, store.count)
        assertEquals("unknown-agent", store.lastToken)
        store.login("org/b")
        store.record(REST, "org/a", "still-retired")
        assertEquals(2, store.entries.size)
        store.login("org/a")
        store.record(TOKEN, "org/a", "new-agent")
        assertEquals(4L, store.entries.last()["seq"])
        assertEquals(3, store.count)
    }

    @Test
    fun test_givenRetiredOwnerAndEntries_whenReset_thenClearSummariesAndRetirement() {
        val store = Store()
        store.record(TOKEN, "org/a", "agent")
        val snapshot = store.entries
        store.retire("org/a")
        store.reset()
        assertTrue(store.entries.isEmpty())
        assertEquals(0, store.count)
        assertEquals(null, store.lastToken)
        store.record(TOKEN, "org/a", "new-agent")
        assertEquals(1L, store.entries.single()["seq"])
        assertEquals("agent", snapshot.single()["userAgent"])
    }

    @Test
    fun test_givenConcurrentWriters_whenCaptured_thenSequencesAndTokenCountAreConsistent() {
        val store = Store()
        val executor = Executors.newFixedThreadPool(8)
        try {
            executor.invokeAll((1..400).map {
                Callable { store.record(TOKEN, "org/a", "agent-$it") }
            }).forEach { it.get() }
        } finally {
            executor.shutdown()
        }
        assertEquals(200, store.entries.size)
        assertEquals((201L..400L).toList(), store.entries.map { it["seq"] })
        assertEquals(400, store.count)
        assertEquals(store.entries.last()["userAgent"], store.lastToken)
    }

    @Test
    fun test_givenCallCreatedWhileRetired_whenReloginOccurs_thenStillRejected() {
        val store = RequestUserAgentCaptureStore()
        store.retireUser("org/a")
        val ticket = store.beginRequest("org/a")
        store.userLoggedIn("org/a")
        store.record(TOKEN, "org/a", "old", true, ticket)
        assertTrue(store.capturedRequests.value.isEmpty())
    }

    @Test
    fun test_givenOldCall_whenReset_thenRejectedButNewCallAccepted() {
        val store = RequestUserAgentCaptureStore()
        val ticket = store.beginRequest(null)
        store.reset()
        store.record(TOKEN, null, "old", true, ticket)
        assertTrue(store.capturedRequests.value.isEmpty())
        store.record(TOKEN, null, "new", true, store.beginRequest(null))
        assertEquals("new", store.capturedRequests.value.single().userAgent)
    }

    @Test
    fun test_givenOwnerRetiredAndReloggedIn_whenOldAndNewCallsArrive_thenOnlyNewAccepted() {
        val store = RequestUserAgentCaptureStore()
        val ticket = store.beginRequest("org/a")
        val other = store.beginRequest("org/b")
        store.retireUser("org/a")
        store.userLoggedIn("org/a")
        store.record(TOKEN, "org/a", "old", true, ticket)
        assertTrue(store.capturedRequests.value.isEmpty())
        store.record(TOKEN, "org/b", "unaffected", true, other)
        store.record(TOKEN, "org/a", "new", true, store.beginRequest("org/a"))
        assertEquals(listOf("unaffected", "new"), store.capturedRequests.value.map { it.userAgent })
    }

    @Test
    fun test_givenRowsRemoved_whenRetired_thenHighWaterRemainsUntilReset() {
        val store = RequestUserAgentCaptureStore()
        store.record(TOKEN, "org/a", "agent", true)
        store.retireUser("org/a")
        assertTrue(store.capturedRequests.value.isEmpty())
        assertEquals(1L, store.highWaterSequence.value)
        store.reset()
        assertEquals(0L, store.highWaterSequence.value)
    }

    private class Store {
        private val instance = RequestUserAgentCaptureStore()
        val entries: List<Map<String, Any?>>
            get() = instance.capturedRequests.value.map { entry ->
                entry.javaClass.declaredFields.filterNot {
                    it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers)
                }.associate {
                    it.isAccessible = true
                    it.name to it.get(entry)
                }
            }
        val count get() = instance.capturedTokenRequestCount.value
        val lastToken get() = instance.lastTokenRequestUserAgent.value
        fun record(path: String?, owner: String?, agent: String?, enabled: Boolean = true) {
            instance.record(path, owner, agent, enabled)
        }
        fun retire(owner: String) = instance.retireUser(owner)
        fun login(owner: String) = instance.userLoggedIn(owner)
        fun reset() = instance.reset()
    }

    companion object {
        private const val TOKEN = "/services/oauth2/token"
        private const val VERSIONS = "/services/data/"
        private const val REST = "/services/data/v67.0/sobjects"
    }
}
