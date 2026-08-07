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

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

object DPoPKeyManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val DPOP_TOKEN_TYPE = "DPoP"

    fun generateOrLoadKeyPair(alias: String): KeyPair {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getKey(alias, null)
        if (existingKey != null) {
            val privateKey = existingKey as PrivateKey
            val publicKey = keyStore.getCertificate(alias).publicKey as ECPublicKey
            return KeyPair(publicKey, privateKey)
        }
        val keyPairGenerator =
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .build()
        keyPairGenerator.initialize(spec)
        return keyPairGenerator.generateKeyPair()
    }

    fun deleteKeyPair(alias: String) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }

    fun aliasForCredentialsIdentifier(id: String): String = "dpop_$id"

    /**
     * Returns true iff a key pair is already present in the AndroidKeyStore for the given alias.
     * Side-effect-free — does NOT mint a key pair on miss.
     */
    fun hasKeyPair(alias: String): Boolean {
        if (alias.isEmpty()) return false
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore.containsAlias(alias)
        } catch (e: Exception) {
            false
        }
    }

    /** Convenience overload — computes the alias for `credentialsIdentifier` and calls `hasKeyPair`. */
    fun hasKeyPairForCredentialsIdentifier(credentialsIdentifier: String?): Boolean {
        if (credentialsIdentifier.isNullOrEmpty()) return false
        return hasKeyPair(aliasForCredentialsIdentifier(credentialsIdentifier))
    }

    /**
     * Decides whether a DPoP proof should be attached to a request for a credential identified
     * by `credentialsIdentifier`. Returns true when either the credential's tokenType is "DPoP"
     * or a DPoP key pair has already been minted for the credential. Either signal alone is
     * sufficient — they cover the transient window between `/authorize` (which mints the key
     * pair before tokenType is written) and `/token` (which writes tokenType after the key pair
     * has been used to sign the proof).
     */
    fun shouldAttachDPoP(credentialsIdentifier: String?, tokenType: String?): Boolean {
        if (credentialsIdentifier.isNullOrEmpty()) return false
        if (DPOP_TOKEN_TYPE == tokenType) return true
        return hasKeyPairForCredentialsIdentifier(credentialsIdentifier)
    }
}
