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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.interfaces.ECPublicKey
import java.util.UUID

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
        assertEquals(
            first.public.encoded.toList(),
            second.public.encoded.toList()
        )
    }

    @Test
    fun test_givenKeyPair_whenDeleted_thenNextCallGeneratesNewKeyPair() {
        val alias = trackedAlias("delete_regen")
        val first = DPoPKeyManager.generateOrLoadKeyPair(alias)
        DPoPKeyManager.deleteKeyPair(alias)
        val second = DPoPKeyManager.generateOrLoadKeyPair(alias)
        assertNotEquals(
            first.public.encoded.toList(),
            second.public.encoded.toList()
        )
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
}
