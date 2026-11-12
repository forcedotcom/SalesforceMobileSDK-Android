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
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.annotation.RestrictTo
import androidx.annotation.RequiresApi
import android.os.Build
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import java.security.InvalidKeyException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.concurrent.ConcurrentHashMap

object DPoPKeyManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_STORE_EXCEPTION_CLASS = "android.security.KeyStoreException"

    /**
     * RFC 9449 token type string. Canonical casing — compare via [isDPoPTokenType] since
     * RFC 6749 §5.1 does not mandate casing; servers may return `"dpop"`, `"DPoP"`, or any variant.
     */
    const val DPOP_TOKEN_TYPE = "DPoP"

    /**
     * Returns true iff [tokenType] matches `"DPoP"` case-insensitively (RFC 6749 §5.1).
     * Handles null/empty safely — never returns true for those.
     */
    @JvmStatic
    fun isDPoPTokenType(tokenType: String?): Boolean =
        tokenType?.equals(DPOP_TOKEN_TYPE, ignoreCase = true) == true

    /**
     * Returns whether the persisted fields required to use [tokenType] are internally
     * consistent. A DPoP-bound credential must identify the key pair used for its proof;
     * Bearer and transitional credentials do not require that identifier.
     */
    @JvmStatic
    fun hasCompleteDPoPCredentials(
        credentialsIdentifier: String?,
        tokenType: String?
    ): Boolean = !isDPoPTokenType(tokenType) || !credentialsIdentifier.isNullOrBlank()

    private const val TAG = "DPoPKeyManager"
    private val keyPairCache = ConcurrentHashMap<String, KeyPair>()
    private val keyStoreLock = Any()

    /**
     * Mints a new key pair on a miss. Use ONLY on the binding paths (`/authorize`, native login)
     * where a DPoP key is supposed to be created. On the refresh / resource-decoration paths the
     * key is already bound at login, so use [loadKeyPair] instead — minting there would sign a
     * proof with a thumbprint the server never bound.
     */
    fun generateOrLoadKeyPair(alias: String): KeyPair {
        keyPairCache[alias]?.let { return it }

        return synchronized(keyStoreLock) {
            keyPairCache[alias] ?: loadOrGenerateKeyPair(alias).also {
                keyPairCache[alias] = it
            }
        }
    }

    /**
     * Load-only lookup: returns the key pair already bound for [alias], or `null` if no key is
     * present. Unlike [generateOrLoadKeyPair] this NEVER mints a key on a miss — it is the
     * side-effect-free primitive the refresh and request-decoration paths must use so a credential
     * whose bound key has gone missing (device restore, keystore eviction) fails fast and routes to
     * re-auth instead of silently signing with a non-bound key.
     *
     * A genuine absence (`getKey` returns null) yields `null`. A KeyStore access failure
     * (device lock, keystore error) is rethrown so callers can distinguish "key is gone" from
     * "could not read the keystore right now" and avoid turning a transient error into a logout.
     *
     * Not part of the public SDK surface — [RestrictTo] keeps it out of the public API (so it is
     * safe to introduce in a patch release) while remaining callable from other SDK modules,
     * including the Java OAuth2 refresh path. (Kotlin `internal` is not an option here: it is
     * name-mangled in bytecode and cannot be called from the Java callers.)
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    fun loadKeyPair(alias: String): KeyPair? {
        if (alias.isEmpty()) return null
        keyPairCache[alias]?.let { return it }

        return synchronized(keyStoreLock) {
            keyPairCache[alias] ?: loadKeyPairFromKeyStore(alias)?.also {
                keyPairCache[alias] = it
            }
        }
    }

    private fun loadKeyPairFromKeyStore(alias: String): KeyPair? {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existingKey = keyStore.getKey(alias, null) ?: return null
            val certificate = keyStore.getCertificate(alias)
                // AndroidKeyStore can swallow a metadata-read failure and return null here.
                // It does not prove a permanently incomplete entry, so preserve retryability.
                ?: throw java.io.IOException("DPoP key certificate lookup failed")
            return KeyPair(certificate.publicKey as ECPublicKey, existingKey as PrivateKey)
        } catch (e: Exception) {
            if (isPermanentlyUnavailableKeyError(e)) {
                // Biometric/lock-screen changes can permanently invalidate a key; retrying cannot restore it.
                return null
            }
            throw e
        }
    }

    /** Returns true only for Android KeyStore failures that prove the bound key can never be used. */
    @JvmStatic
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    fun isPermanentlyUnavailableKeyError(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is KeyPermanentlyInvalidatedException) return true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // AndroidKeyStore can wrap backend failures in this exception. Any error without a
                // proven loss code stays retryable.
                Api33KeyStoreErrorClassifier.isPermanentlyUnavailable(current)?.let { return it }
            } else if (current.javaClass.name == KEY_STORE_EXCEPTION_CLASS) {
                // TODO: Remove this block when min API >= 33.
                // Keep pre-33 KeyStore backend failures retryable without referencing the API-33 type.
                return false
            }
            // AndroidKeyStore may wrap invalidation in InvalidKeyException during Signature.initSign.
            if (current is InvalidKeyException && current.cause is KeyPermanentlyInvalidatedException) {
                return true
            }
            current = current.cause
        }
        return false
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private object Api33KeyStoreErrorClassifier {
        fun isPermanentlyUnavailable(error: Throwable): Boolean? {
            if (error !is android.security.KeyStoreException) return null
            return error.numericErrorCode == android.security.KeyStoreException.ERROR_KEY_DOES_NOT_EXIST ||
                error.numericErrorCode == android.security.KeyStoreException.ERROR_KEY_CORRUPTED
        }
    }

    private fun loadOrGenerateKeyPair(alias: String): KeyPair {
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

    /**
     * Idempotently deletes the key pair for [alias].
     *
     * Returns `true` when all AndroidKeyStore operations complete normally, including when the
     * alias is absent. Returns `false` when an ordinary [Exception] prevents completion; physical
     * deletion is not guaranteed in that case. Such failures are logged by exception type without
     * exception details or the alias, and do not escape this method.
     */
    fun deleteKeyPair(alias: String): Boolean = try {
        synchronized(keyStoreLock) {
            keyPairCache.remove(alias)
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        }
        true
    } catch (e: Exception) {
        SalesforceSDKLogger.w(TAG, "DPoP key pair deletion failed (${e.javaClass.simpleName})")
        false
    }

    /** Clears process-local key handles without deleting their Android Keystore entries. */
    internal fun clearInMemoryCache() {
        synchronized(keyStoreLock) {
            keyPairCache.clear()
        }
    }

    /**
     * Drops the cached key handle for [alias] without deleting its AndroidKeyStore entry.
     * Use when signing with a cached handle fails — e.g. the backing keystore entry
     * was evicted out-of-band — so the next [loadKeyPair] re-reads from the keystore and converges
     * on the correct state (reload the real key, or `null` → fail fast to re-auth) instead of
     * repeatedly signing with a dead handle.
     *
     * Not part of the public SDK surface — see [loadKeyPair] for why [RestrictTo] (rather than
     * `internal`) is used here.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    fun evictFromCache(alias: String) {
        synchronized(keyStoreLock) {
            keyPairCache.remove(alias)
        }
    }

    fun aliasForCredentialsIdentifier(id: String): String = "dpop_$id"

    /**
     * Returns true iff a key pair is already present in the AndroidKeyStore for the given alias.
     * Side-effect-free — does NOT mint a key pair on miss.
     */
    fun hasKeyPair(alias: String): Boolean {
        if (alias.isEmpty()) return false
        if (keyPairCache.containsKey(alias)) return true
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore.containsAlias(alias)
        } catch (e: Exception) {
            // A failure here (device lock, entitlement error, KeyStoreException) is NOT the
            // same as "key not present." Returning false means "proceed without a proof," so
            // surface the failure for diagnostics rather than swallowing it silently.
            SalesforceSDKLogger.w(TAG, "DPoPKeyManager: KeyStore lookup failed for $alias", e)
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
     * by `credentialsIdentifier`.
     *
     * An explicit `tokenType` is authoritative:
     * - `tokenType` matches `"DPoP"` case-insensitively (RFC 6749 §5.1) → attach.
     * - any other non-null type (e.g. "Bearer") → do NOT attach, and fast-exit before any
     *   KeyStore I/O. This protects the DPoP→Bearer migration / Bearer re-auth path: a Bearer
     *   credential that reuses a `credentialsIdentifier` still holding a stale DPoP key pair
     *   must never get an unexpected proof.
     *
     * A null `tokenType` is the transient window between `/authorize` (which mints the key pair
     * before tokenType is written) and `/token` (which writes tokenType after the key pair has
     * been used to sign the proof); only then do we fall back to the key-material signal.
     */
    fun shouldAttachDPoP(credentialsIdentifier: String?, tokenType: String?): Boolean {
        if (credentialsIdentifier.isNullOrEmpty()) return false
        if (isDPoPTokenType(tokenType)) return true
        if (tokenType != null) return false // explicit non-DPoP type — fast exit, no KeyStore I/O
        // tokenType is null — transition window between /authorize and /token.
        return hasKeyPairForCredentialsIdentifier(credentialsIdentifier)
    }
}
