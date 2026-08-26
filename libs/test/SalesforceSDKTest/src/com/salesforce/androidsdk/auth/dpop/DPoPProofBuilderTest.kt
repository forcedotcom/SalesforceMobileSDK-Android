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

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPair
import java.security.MessageDigest
import java.security.Signature

@RunWith(AndroidJUnit4::class)
class DPoPProofBuilderTest {

    private lateinit var keyPair: KeyPair
    private val alias = "dpop_test_proof_builder"

    @Before
    fun setUp() {
        keyPair = DPoPKeyManager.generateOrLoadKeyPair(alias)
    }

    @After
    fun tearDown() {
        DPoPKeyManager.deleteKeyPair(alias)
    }

    private fun base64UrlDecode(s: String): ByteArray =
        Base64.decode(s, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private fun decodeJson(segment: String): JSONObject =
        JSONObject(String(base64UrlDecode(segment), Charsets.UTF_8))

    private fun rawRsToDer(raw: ByteArray): ByteArray {
        val r = raw.copyOfRange(0, 32)
        val s = raw.copyOfRange(32, 64)
        fun encodeInt(b: ByteArray): ByteArray {
            val stripped = b.dropWhile { it == 0.toByte() }.toByteArray()
            val padded =
                if (stripped.isNotEmpty() && (stripped[0].toInt() and 0x80) != 0)
                    byteArrayOf(0) + stripped
                else stripped
            return byteArrayOf(0x02.toByte(), padded.size.toByte()) + padded
        }
        val rEnc = encodeInt(r)
        val sEnc = encodeInt(s)
        val body = rEnc + sEnc
        return byteArrayOf(0x30.toByte(), body.size.toByte()) + body
    }

    @Test
    fun test_givenKeyPair_whenBuildProof_thenJwsHasThreeSegments() {
        val proof = DPoPProofBuilder.buildProof("POST", "https://example.com/token", keyPair)
        val parts = proof.split(".")
        assertEquals(3, parts.size)
    }

    @Test
    fun test_givenKeyPair_whenBuildProof_thenHeaderHasCorrectFields() {
        val proof = DPoPProofBuilder.buildProof("POST", "https://example.com/token", keyPair)
        val header = decodeJson(proof.split(".")[0])
        assertEquals("dpop+jwt", header.getString("typ"))
        assertEquals("ES256", header.getString("alg"))
        val jwk = header.getJSONObject("jwk")
        assertTrue(jwk.has("kty"))
        assertTrue(jwk.has("crv"))
        assertTrue(jwk.has("x"))
        assertTrue(jwk.has("y"))
    }

    @Test
    fun test_givenKeyPair_whenBuildProof_thenPayloadHasCorrectClaimsAndNoAthOrNonce() {
        val proof = DPoPProofBuilder.buildProof("POST", "https://example.com/token", keyPair)
        val payload = decodeJson(proof.split(".")[1])
        assertEquals("POST", payload.getString("htm"))
        assertTrue(payload.getString("htu").isNotEmpty())
        assertTrue(payload.getLong("iat") > 0)
        assertTrue(payload.getString("jti").isNotEmpty())
        assertFalse(payload.has("ath"))
        assertFalse(payload.has("nonce"))
    }

    @Test
    fun test_givenURLWithQueryAndFragment_whenBuildProof_thenHtuIsCanonical() {
        val htu = "https://host/token?q=1#frag"
        val proof = DPoPProofBuilder.buildProof("POST", htu, keyPair)
        val payload = decodeJson(proof.split(".")[1])
        assertEquals(htu, payload.getString("htu"))
    }

    @Test
    fun test_givenSameKeyPair_whenBuildProofTwice_thenJtisDiffer() {
        val proof1 = DPoPProofBuilder.buildProof("POST", "https://example.com/token", keyPair)
        val proof2 = DPoPProofBuilder.buildProof("POST", "https://example.com/token", keyPair)
        val jti1 = decodeJson(proof1.split(".")[1]).getString("jti")
        val jti2 = decodeJson(proof2.split(".")[1]).getString("jti")
        assertNotEquals(jti1, jti2)
    }

    @Test
    fun test_givenProof_whenSignatureVerifiedWithPublicKey_thenValid() {
        val proof = DPoPProofBuilder.buildProof("POST", "https://example.com/token", keyPair)
        val parts = proof.split(".")
        val signingInput = "${parts[0]}.${parts[1]}".toByteArray(Charsets.UTF_8)
        val rawSig = base64UrlDecode(parts[2])
        assertEquals(64, rawSig.size)
        val derSig = rawRsToDer(rawSig)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(keyPair.public)
        verifier.update(signingInput)
        assertTrue(verifier.verify(derSig))
    }

    @Test
    fun test_givenKeyPair_whenBuildProof_thenJwkCoordinatesAre32BytesEach() {
        val proof = DPoPProofBuilder.buildProof("POST", "https://example.com/token", keyPair)
        val header = decodeJson(proof.split(".")[0])
        val jwk = header.getJSONObject("jwk")
        val xBytes = base64UrlDecode(jwk.getString("x"))
        val yBytes = base64UrlDecode(jwk.getString("y"))
        assertEquals(32, xBytes.size)
        assertEquals(32, yBytes.size)
    }

    @Test
    fun test_givenAccessToken_whenBuildProof_thenPayloadIncludesAth() {
        val accessToken = "test-access-token-abc123"
        val proof = DPoPProofBuilder.buildProof("GET", "https://example.com/api", keyPair, accessToken = accessToken)
        val payload = decodeJson(proof.split(".")[1])
        assertTrue(payload.has("ath"))
        val expectedAth = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(accessToken.toByteArray(Charsets.UTF_8)),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        assertEquals(expectedAth, payload.getString("ath"))
    }

    @Test
    fun test_givenNullAccessToken_whenBuildProof_thenPayloadOmitsAth() {
        val proof = DPoPProofBuilder.buildProof("GET", "https://example.com/api", keyPair, accessToken = null)
        val payload = decodeJson(proof.split(".")[1])
        assertFalse(payload.has("ath"))
    }

    @Test
    fun test_givenEmptyAccessToken_whenBuildProof_thenPayloadOmitsAth() {
        val proof = DPoPProofBuilder.buildProof("GET", "https://example.com/api", keyPair, accessToken = "")
        val payload = decodeJson(proof.split(".")[1])
        assertFalse(payload.has("ath"))
    }

    @Test
    fun test_givenAccessToken_whenBuildProofTwice_thenAthIsIdentical() {
        val accessToken = "deterministic-token"
        val proof1 = DPoPProofBuilder.buildProof("GET", "https://example.com/api", keyPair, accessToken = accessToken)
        val proof2 = DPoPProofBuilder.buildProof("GET", "https://example.com/api", keyPair, accessToken = accessToken)
        val ath1 = decodeJson(proof1.split(".")[1]).getString("ath")
        val ath2 = decodeJson(proof2.split(".")[1]).getString("ath")
        assertEquals(ath1, ath2)
    }
}
