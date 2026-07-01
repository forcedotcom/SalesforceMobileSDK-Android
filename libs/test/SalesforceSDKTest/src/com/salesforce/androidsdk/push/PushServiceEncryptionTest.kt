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
 * specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.push

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountTest.TEST_AUTH_TOKEN
import com.salesforce.androidsdk.accounts.UserAccountTest.TEST_REFRESH_TOKEN
import com.salesforce.androidsdk.accounts.UserAccountTest.createTestAccount
import com.salesforce.androidsdk.push.PushService.Companion.decryptUserAccountJson
import com.salesforce.androidsdk.push.PushService.Companion.encryptUserAccountJson
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for the encryption of the `UserAccount` payload that `PushService`
 * places into the WorkManager input `Data` for
 * [PushNotificationsRegistrationChangeWorker].
 *
 * Regression coverage: full `UserAccount` JSON (including `authToken`,
 * `refreshToken`, and session cookies) was previously written to the
 * `androidx.work` Room database as plaintext. The fix encrypts the payload in
 * transit using the SDK's AES-GCM + Android Keystore crypto.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class PushServiceEncryptionTest {

    /**
     * SC-1: The payload written for a `UserAccount` must be ciphertext — the
     * plaintext auth/refresh tokens must not be recoverable by scanning the
     * encrypted string.
     */
    @Test
    fun testEncryptUserAccountJson_producesCiphertext() {
        val plaintextJson = createTestAccount().toJson().toString()

        val encrypted = encryptUserAccountJson(plaintextJson)

        assertNotNull("Encrypted payload should not be null for a non-null account", encrypted)
        assertFalse(
            "Encrypted payload must not equal the plaintext JSON",
            plaintextJson == encrypted
        )
        assertFalse(
            "Auth token must not be recoverable from the encrypted payload",
            encrypted!!.contains(TEST_AUTH_TOKEN)
        )
        assertFalse(
            "Refresh token must not be recoverable from the encrypted payload",
            encrypted.contains(TEST_REFRESH_TOKEN)
        )
    }

    /**
     * SC-2: Round-trip fidelity — a `UserAccount` encrypted for enqueue must be
     * reconstructed identically after the worker decrypts it. `UserAccount`
     * equality only covers userId + orgId, so the auth and refresh tokens are
     * asserted explicitly.
     */
    @Test
    fun testEncryptThenDecrypt_roundTripsUserAccount() {
        val original = createTestAccount()
        val plaintextJson = original.toJson().toString()

        val encrypted = encryptUserAccountJson(plaintextJson)
        val decryptedJson = decryptUserAccountJson(encrypted)

        assertNotNull("Decrypted JSON should not be null", decryptedJson)
        val restored = UserAccount(JSONObject(decryptedJson!!))
        assertEquals("Restored account should equal the original", original, restored)
        assertEquals(
            "Auth token should survive the encrypt/decrypt round trip",
            original.authToken,
            restored.authToken
        )
        assertEquals(
            "Refresh token should survive the encrypt/decrypt round trip",
            original.refreshToken,
            restored.refreshToken
        )
    }

    /**
     * SC-3 (part a): Null-account behavior is unchanged. A null payload still
     * means "all authenticated users" — encrypt/decrypt of null returns null,
     * preserving the worker's null-means-all-users contract.
     */
    @Test
    fun testEncryptDecryptNull_isNoOp() {
        assertNull("Encrypting a null account should yield null", encryptUserAccountJson(null))
        assertNull("Decrypting a null payload should yield null", decryptUserAccountJson(null))
    }

    /**
     * SC-3 (part b): Decrypt-failure guard — an undecryptable payload must fail
     * safe by returning null rather than throwing or leaking a partial value.
     * A null result routes the worker to its (safe) null-account path rather
     * than crashing the work request.
     */
    @Test
    fun testDecryptMalformedPayload_failsSafe() {
        val garbage = "this-is-not-a-valid-encrypted-payload"

        val decrypted = decryptUserAccountJson(garbage)

        assertNull("Undecryptable payload should decrypt to null (fail safe)", decrypted)
    }
}
