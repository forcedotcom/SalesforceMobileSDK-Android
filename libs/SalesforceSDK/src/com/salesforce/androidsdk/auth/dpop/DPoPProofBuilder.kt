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
import org.json.JSONObject
import java.security.KeyPair
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey

object DPoPProofBuilder {

    fun buildProof(
        httpMethod: String,
        htu: String,
        keyPair: KeyPair,
        nonce: String? = null,
        accessToken: String? = null
    ): String {
        val publicKey = keyPair.public as ECPublicKey
        val jwk = buildJwk(publicKey)
        val header = buildHeader(jwk)
        val payload = buildPayload(httpMethod, htu)
        val headerEncoded = base64url(header.toString().toByteArray(Charsets.UTF_8))
        val payloadEncoded = base64url(payload.toString().toByteArray(Charsets.UTF_8))
        val signingInput = "$headerEncoded.$payloadEncoded"
        val signature = sign(signingInput.toByteArray(Charsets.UTF_8), keyPair)
        val signatureEncoded = base64url(derToRawRS(signature))
        return "$headerEncoded.$payloadEncoded.$signatureEncoded"
    }

    private fun buildJwk(publicKey: ECPublicKey): JSONObject {
        val point = publicKey.w
        val xBytes = toUnsigned32(point.affineX.toByteArray())
        val yBytes = toUnsigned32(point.affineY.toByteArray())
        return JSONObject().apply {
            put("kty", "EC")
            put("crv", "P-256")
            put("x", base64url(xBytes))
            put("y", base64url(yBytes))
        }
    }

    private fun buildHeader(jwk: JSONObject): JSONObject =
        JSONObject().apply {
            put("typ", "dpop+jwt")
            put("alg", "ES256")
            put("jwk", jwk)
        }

    private fun buildPayload(httpMethod: String, htu: String): JSONObject {
        val jti = ByteArray(12).also { SecureRandom().nextBytes(it) }
        return JSONObject().apply {
            put("htm", httpMethod.uppercase())
            put("htu", htu)
            put("iat", System.currentTimeMillis() / 1000L)
            put("jti", base64url(jti))
        }
    }

    private fun sign(data: ByteArray, keyPair: KeyPair): ByteArray {
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(keyPair.private)
        sig.update(data)
        return sig.sign()
    }

    internal fun derToRawRS(der: ByteArray): ByteArray {
        var offset = 2
        offset++
        val rLen = der[offset++].toInt() and 0xFF
        val rBytes = der.copyOfRange(offset, offset + rLen)
        offset += rLen
        offset++
        val sLen = der[offset++].toInt() and 0xFF
        val sBytes = der.copyOfRange(offset, offset + sLen)
        return toUnsigned32(rBytes) + toUnsigned32(sBytes)
    }

    internal fun toUnsigned32(bytes: ByteArray): ByteArray {
        val trimmed = bytes.dropWhile { it == 0.toByte() }.toByteArray()
        return ByteArray(32 - trimmed.size) + trimmed
    }

    private fun base64url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
