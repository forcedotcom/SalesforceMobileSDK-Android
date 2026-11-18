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
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPair
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.UnrecoverableKeyException
import java.security.interfaces.ECPublicKey
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import android.security.keystore.KeyPermanentlyInvalidatedException

@RunWith(AndroidJUnit4::class)
class DPoPKeyManagerTest {

    private val aliasesToCleanUp = mutableListOf<String>()

    @After
    fun tearDown() {
        aliasesToCleanUp.forEach { DPoPKeyManager.deleteKeyPair(it) }
        aliasesToCleanUp.clear()
    }

    private fun trackedAlias(name: String): String {
        val alias = "dpop_test_$name"
        aliasesToCleanUp.add(alias)
        return alias
    }

    @Test
    fun test_givenAlias_whenGenerateOrLoad_thenKeyPairIsECSecp256r1() {
        val alias = trackedAlias("ec_secp256r1")
        val keyPair = DPoPKeyManager.generateOrLoadKeyPair(alias)
        assertTrue(keyPair.public is ECPublicKey)
        assertEquals("EC", keyPair.public.algorithm)
    }

    @Test
    fun test_givenAlias_whenGenerateOrLoadTwice_thenSamePublicKeyReturned() {
        val alias = trackedAlias("same_pubkey")
        val first = DPoPKeyManager.generateOrLoadKeyPair(alias)
        val second = DPoPKeyManager.generateOrLoadKeyPair(alias)
        assertSame(first, second)
        assertEquals(
            first.public.encoded.toList(),
            second.public.encoded.toList()
        )
    }

    @Test
    fun test_givenConcurrentCallers_whenGenerateOrLoad_thenSameCachedKeyPairReturned() {
        val alias = trackedAlias("concurrent_${UUID.randomUUID()}")
        val workerCount = 8
        val executor = Executors.newFixedThreadPool(workerCount)
        val ready = CountDownLatch(workerCount)
        val start = CountDownLatch(1)

        try {
            val futures = List(workerCount) {
                executor.submit<KeyPair> {
                    ready.countDown()
                    start.await()
                    DPoPKeyManager.generateOrLoadKeyPair(alias)
                }
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            val keyPairs = futures.map { it.get(30, TimeUnit.SECONDS) }
            keyPairs.drop(1).forEach { assertSame(keyPairs.first(), it) }
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun test_givenMemoryCacheCleared_whenGenerateOrLoad_thenPersistentKeyReloaded() {
        val alias = trackedAlias("memory_reset_${UUID.randomUUID()}")
        val first = DPoPKeyManager.generateOrLoadKeyPair(alias)

        DPoPKeyManager.clearInMemoryCache()
        val reloaded = DPoPKeyManager.generateOrLoadKeyPair(alias)

        assertEquals(
            first.public.encoded.toList(),
            reloaded.public.encoded.toList()
        )
        assertSame(reloaded, DPoPKeyManager.generateOrLoadKeyPair(alias))
    }

    @Test
    fun test_givenKeyPair_whenDeleted_thenNextCallGeneratesNewKeyPair() {
        val alias = trackedAlias("delete_regen")
        val first = DPoPKeyManager.generateOrLoadKeyPair(alias)
        assertTrue(DPoPKeyManager.deleteKeyPair(alias))
        val second = DPoPKeyManager.generateOrLoadKeyPair(alias)
        assertNotEquals(
            first.public.encoded.toList(),
            second.public.encoded.toList()
        )
    }

    @Test
    fun test_givenNeverCreatedAlias_whenDeleted_thenReturnsTrue() {
        val alias = "dpop_test_absent_${UUID.randomUUID()}"

        assertTrue(DPoPKeyManager.deleteKeyPair(alias))
    }

    @Test
    fun test_givenAliasBearingKeyStoreDeletionFailure_whenDeleted_thenReturnsFalseEvictsCacheAndLogsSanitizedType() {
        val alias = trackedAlias("sensitive-credentials-identifier")
        val cachedKeyPair = DPoPKeyManager.generateOrLoadKeyPair(alias)
        val failure = KeyStoreException("Failed to delete entry: $alias")
        val keyStore = mockk<KeyStore>()
        mockkStatic(KeyStore::class)
        mockkStatic(SalesforceSDKLogger::class)
        try {
            every { KeyStore.getInstance("AndroidKeyStore") } returns keyStore
            every { keyStore.load(null) } returns Unit
            every { keyStore.containsAlias(any()) } returns true
            every { keyStore.deleteEntry(any()) } throws failure
            every { SalesforceSDKLogger.w(any(), any()) } returns Unit
            every { SalesforceSDKLogger.w(any(), any(), any()) } returns Unit

            assertFalse(DPoPKeyManager.deleteKeyPair(alias))
            verify(exactly = 1) {
                SalesforceSDKLogger.w(any(), "DPoP key pair deletion failed (KeyStoreException)")
            }
            verify(exactly = 0) { SalesforceSDKLogger.w(any(), any(), any()) }
        } finally {
            unmockkStatic(SalesforceSDKLogger::class)
            unmockkStatic(KeyStore::class)
        }

        val reloadedKeyPair = DPoPKeyManager.generateOrLoadKeyPair(alias)
        assertNotSame(cachedKeyPair, reloadedKeyPair)
        assertEquals(cachedKeyPair.public.encoded.toList(), reloadedKeyPair.public.encoded.toList())
    }

    @Test
    fun test_givenDifferentAliases_thenDifferentKeyPairs() {
        val aliasA = trackedAlias("alias_a")
        val aliasB = trackedAlias("alias_b")
        val kpA = DPoPKeyManager.generateOrLoadKeyPair(aliasA)
        val kpB = DPoPKeyManager.generateOrLoadKeyPair(aliasB)
        assertNotEquals(
            kpA.public.encoded.toList(),
            kpB.public.encoded.toList()
        )
    }

    @Test
    fun test_givenCredentialsIdentifier_whenComputingAlias_thenPrefixedWithDpop() {
        assertEquals("dpop_user-123", DPoPKeyManager.aliasForCredentialsIdentifier("user-123"))
    }

    @Test
    fun test_hasKeyPair_falseBeforeCreation_trueAfter_falseAfterDeletion() {
        val alias = trackedAlias("has_keypair_${UUID.randomUUID()}")
        assertFalse(DPoPKeyManager.hasKeyPair(alias))
        DPoPKeyManager.generateOrLoadKeyPair(alias)
        assertTrue(DPoPKeyManager.hasKeyPair(alias))
        DPoPKeyManager.deleteKeyPair(alias)
        assertFalse(DPoPKeyManager.hasKeyPair(alias))
    }

    @Test
    fun test_hasKeyPair_emptyAlias_returnsFalse() {
        assertFalse(DPoPKeyManager.hasKeyPair(""))
    }

    @Test
    fun test_hasKeyPairForCredentialsIdentifier_nullOrEmpty_returnsFalse() {
        assertFalse(DPoPKeyManager.hasKeyPairForCredentialsIdentifier(null))
        assertFalse(DPoPKeyManager.hasKeyPairForCredentialsIdentifier(""))
    }

    @Test
    fun test_hasKeyPairForCredentialsIdentifier_matchesHasKeyPairForComputedAlias() {
        val id = "pred_${UUID.randomUUID()}"
        val alias = DPoPKeyManager.aliasForCredentialsIdentifier(id)
        aliasesToCleanUp.add(alias)
        assertFalse(DPoPKeyManager.hasKeyPairForCredentialsIdentifier(id))
        DPoPKeyManager.generateOrLoadKeyPair(alias)
        assertTrue(DPoPKeyManager.hasKeyPairForCredentialsIdentifier(id))
    }

    @Test
    fun test_shouldAttachDPoP_predicate_covers_all_cases() {
        val id = "predicate_${UUID.randomUUID()}"
        val alias = DPoPKeyManager.aliasForCredentialsIdentifier(id)
        aliasesToCleanUp.add(alias)

        // Null or empty id short-circuits to false regardless of tokenType.
        assertFalse(DPoPKeyManager.shouldAttachDPoP(null, "DPoP"))
        assertFalse(DPoPKeyManager.shouldAttachDPoP("", "DPoP"))

        // Valid id, tokenType="DPoP", no key material yet → true (tokenType alone is sufficient).
        assertTrue(DPoPKeyManager.shouldAttachDPoP(id, "DPoP"))

        // Valid id, no tokenType, no key material → false.
        assertFalse(DPoPKeyManager.shouldAttachDPoP(id, null))
        assertFalse(DPoPKeyManager.shouldAttachDPoP(id, "Bearer"))

        // Once a key pair exists, the hasKeyPair leg kicks in only for a null tokenType.
        DPoPKeyManager.generateOrLoadKeyPair(alias)
        assertTrue(DPoPKeyManager.shouldAttachDPoP(id, null))
        // Key material present, but an explicit "Bearer" tokenType takes priority — no proof.
        assertFalse(DPoPKeyManager.shouldAttachDPoP(id, "Bearer"))
        assertTrue(DPoPKeyManager.shouldAttachDPoP(id, "DPoP"))

        // After deletion the predicate falls back to tokenType-only.
        DPoPKeyManager.deleteKeyPair(alias)
        assertFalse(DPoPKeyManager.shouldAttachDPoP(id, null))
        assertFalse(DPoPKeyManager.shouldAttachDPoP(id, "Bearer"))
        assertTrue(DPoPKeyManager.shouldAttachDPoP(id, "DPoP"))
    }

    // --- load-only lookup must never mint a key on a miss ---

    @Test
    fun test_loadKeyPair_absentAlias_returnsNullAndDoesNotMint() {
        val alias = "dpop_test_loadonly_absent_${UUID.randomUUID()}"
        aliasesToCleanUp.add(alias)
        // No key exists for this alias.
        assertFalse(DPoPKeyManager.hasKeyPair(alias))
        // Load-only lookup must report absent...
        assertNull(DPoPKeyManager.loadKeyPair(alias))
        // ...and must NOT have minted one as a side effect (the bug).
        assertFalse(DPoPKeyManager.hasKeyPair(alias))
    }

    @Test
    fun test_loadKeyPair_presentAlias_returnsBoundKeyWithoutMinting() {
        val alias = trackedAlias("loadonly_present_${UUID.randomUUID()}")
        val bound = DPoPKeyManager.generateOrLoadKeyPair(alias)
        val loaded = DPoPKeyManager.loadKeyPair(alias)
        assertNotNull("Expected the bound key to load", loaded)
        assertEquals(
            bound.public.encoded.toList(),
            loaded!!.public.encoded.toList()
        )
    }

    @Test
    fun test_loadKeyPair_afterDeletion_returnsNullAndDoesNotRegenerate() {
        val alias = trackedAlias("loadonly_deleted_${UUID.randomUUID()}")
        DPoPKeyManager.generateOrLoadKeyPair(alias)
        assertTrue(DPoPKeyManager.deleteKeyPair(alias))
        assertNull(DPoPKeyManager.loadKeyPair(alias))
        assertFalse(DPoPKeyManager.hasKeyPair(alias))
    }

    @Test
    fun test_loadKeyPair_emptyAlias_returnsNull() {
        assertNull(DPoPKeyManager.loadKeyPair(""))
    }

    @Test
    fun test_loadKeyPair_unrecoverableKeyWithoutProvenCauseRemainsRetryable() {
        assertAmbiguousLookupFailureRemainsRetryable(UnrecoverableKeyException("key is unrecoverable"))
    }

    @Test
    fun test_loadKeyPair_unrecoverableKeyWrappingNonLossKeyStoreFailureRemainsRetryable() {
        val alias = "dpop_test_loadonly_transient_${UUID.randomUUID()}"
        val keyStore = mockk<KeyStore>()
        val transientFailure = mockk<android.security.KeyStoreException>()
        every { transientFailure.numericErrorCode } returns android.security.KeyStoreException.ERROR_INTERNAL_SYSTEM_ERROR
        val failure = UnrecoverableKeyException("temporary backend failure").apply {
            initCause(transientFailure)
        }
        mockkStatic(KeyStore::class)
        try {
            every { KeyStore.getInstance("AndroidKeyStore") } returns keyStore
            every { keyStore.load(null) } returns Unit
            every { keyStore.getKey(alias, null) } throws failure

            assertSame(failure, assertThrows(Exception::class.java) {
                DPoPKeyManager.loadKeyPair(alias)
            })
        } finally {
            unmockkStatic(KeyStore::class)
        }
    }

    @Test
    fun test_loadKeyPair_permanentlyInvalidatedKeyTreatsBoundKeyAsAbsent() {
        assertKnownPermanentLookupFailureIsAbsent(KeyPermanentlyInvalidatedException())
    }

    @Test
    fun test_loadKeyPair_privateKeyWithoutCertificateRemainsRetryable() {
        val alias = "dpop_test_loadonly_no_certificate_${UUID.randomUUID()}"
        val keyStore = mockk<KeyStore>()
        mockkStatic(KeyStore::class)
        try {
            every { KeyStore.getInstance("AndroidKeyStore") } returns keyStore
            every { keyStore.load(null) } returns Unit
            every { keyStore.getKey(alias, null) } returns mockk()
            every { keyStore.getCertificate(alias) } returns null

            assertThrows(java.io.IOException::class.java) {
                DPoPKeyManager.loadKeyPair(alias)
            }
        } finally {
            unmockkStatic(KeyStore::class)
        }
    }

    @Test
    fun test_loadKeyPair_coldCache_readsFromKeyStoreWithoutMinting() {
        val alias = trackedAlias("loadonly_coldcache_${UUID.randomUUID()}")
        val bound = DPoPKeyManager.generateOrLoadKeyPair(alias)
        // Drop the in-memory handle so loadKeyPair must hit the AndroidKeyStore, not the cache.
        DPoPKeyManager.clearInMemoryCache()

        val loaded = DPoPKeyManager.loadKeyPair(alias)

        assertNotNull("Cold-cache load must read the bound key from the keystore", loaded)
        assertEquals(
            "Cold-cache load must return the SAME bound key, not a minted one",
            bound.public.encoded.toList(),
            loaded!!.public.encoded.toList()
        )
    }

    @Test
    fun test_evictFromCache_dropsHandleButKeepsKeyStoreEntry() {
        val alias = trackedAlias("evict_${UUID.randomUUID()}")
        val bound = DPoPKeyManager.generateOrLoadKeyPair(alias)

        // Evict only the cached handle — the AndroidKeyStore entry must survive.
        DPoPKeyManager.evictFromCache(alias)
        assertTrue("Keystore entry must remain after cache eviction", DPoPKeyManager.hasKeyPair(alias))

        // A subsequent load re-reads the same key from the keystore.
        val reloaded = DPoPKeyManager.loadKeyPair(alias)
        assertNotNull(reloaded)
        assertEquals(
            bound.public.encoded.toList(),
            reloaded!!.public.encoded.toList()
        )
    }

    @Test
    fun test_isDPoPTokenType_matchesCaseInsensitivelyAndRejectsNilEmpty() {
        // RFC 6749 §5.1 does not mandate casing — servers may return "dpop" or "DPoP".
        assertTrue(DPoPKeyManager.isDPoPTokenType("DPoP"))
        assertTrue(DPoPKeyManager.isDPoPTokenType("dpop"))
        assertTrue(DPoPKeyManager.isDPoPTokenType("DPOP"))

        assertFalse(DPoPKeyManager.isDPoPTokenType(null))
        assertFalse(DPoPKeyManager.isDPoPTokenType(""))
        assertFalse(DPoPKeyManager.isDPoPTokenType("Bearer"))
        assertFalse(DPoPKeyManager.isDPoPTokenType("bearer"))
    }

    private fun assertKnownPermanentLookupFailureIsAbsent(failure: Exception) {
        val alias = "dpop_test_loadonly_permanent_${UUID.randomUUID()}"
        val keyStore = mockk<KeyStore>()
        mockkStatic(KeyStore::class)
        try {
            every { KeyStore.getInstance("AndroidKeyStore") } returns keyStore
            every { keyStore.load(null) } returns Unit
            every { keyStore.getKey(alias, null) } throws failure

            assertNull(DPoPKeyManager.loadKeyPair(alias))
        } finally {
            unmockkStatic(KeyStore::class)
        }
    }

    private fun assertAmbiguousLookupFailureRemainsRetryable(failure: Exception) {
        val alias = "dpop_test_loadonly_ambiguous_${UUID.randomUUID()}"
        val keyStore = mockk<KeyStore>()
        mockkStatic(KeyStore::class)
        try {
            every { KeyStore.getInstance("AndroidKeyStore") } returns keyStore
            every { keyStore.load(null) } returns Unit
            every { keyStore.getKey(alias, null) } throws failure

            assertSame(failure, assertThrows(Exception::class.java) {
                DPoPKeyManager.loadKeyPair(alias)
            })
        } finally {
            unmockkStatic(KeyStore::class)
        }
    }

    @Test
    fun test_hasCompleteDPoPCredentials_requiresNonBlankIdentifierOnlyForDPoP() {
        assertTrue(DPoPKeyManager.hasCompleteDPoPCredentials("credential-id", "DPoP"))
        assertTrue(DPoPKeyManager.hasCompleteDPoPCredentials("credential-id", "dPoP"))
        assertFalse(DPoPKeyManager.hasCompleteDPoPCredentials(null, "DPoP"))
        assertFalse(DPoPKeyManager.hasCompleteDPoPCredentials("", "dpop"))
        assertFalse(DPoPKeyManager.hasCompleteDPoPCredentials("   ", "DPOP"))

        assertTrue(DPoPKeyManager.hasCompleteDPoPCredentials(null, "Bearer"))
        assertTrue(DPoPKeyManager.hasCompleteDPoPCredentials("", null))
    }

    @Test
    fun test_shouldAttachDPoP_lowercaseDPoPTokenType_returnsTrue() {
        // Regression for W-24027018: server returns lowercase "dpop" in refresh responses.
        val id = "lowercase_dpop_${UUID.randomUUID()}"
        aliasesToCleanUp.add(DPoPKeyManager.aliasForCredentialsIdentifier(id))
        assertTrue(DPoPKeyManager.shouldAttachDPoP(id, "dpop"))
        assertTrue(DPoPKeyManager.shouldAttachDPoP(id, "DPOP"))
    }
}
