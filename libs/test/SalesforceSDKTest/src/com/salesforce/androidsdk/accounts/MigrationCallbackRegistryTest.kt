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
package com.salesforce.androidsdk.accounts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for MigrationCallbackRegistry.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class MigrationCallbackRegistryTest {

    @Test
    fun register_returnsUniqueKey() {
        // Given
        val onSuccess: (UserAccount) -> Unit = mockk(relaxed = true)
        val onError: (String, String?, Throwable?) -> Unit = mockk(relaxed = true)
        val callbacks = MigrationCallbackRegistry.MigrationCallbacks(
            onMigrationSuccess = onSuccess,
            onMigrationError = onError
        )

        // When
        val key1 = MigrationCallbackRegistry.register(callbacks)
        val key2 = MigrationCallbackRegistry.register(callbacks)

        // Then
        assertNotNull("Key should not be null", key1)
        assertNotNull("Key should not be null", key2)
        assertNotEquals("Keys should be unique", key1, key2)

        // Cleanup
        MigrationCallbackRegistry.consume(key1)
        MigrationCallbackRegistry.consume(key2)
    }

    @Test
    fun consume_returnsCallbacksAndRemovesThem() {
        // Given
        val onSuccess: (UserAccount) -> Unit = mockk(relaxed = true)
        val onError: (String, String?, Throwable?) -> Unit = mockk(relaxed = true)
        val callbacks = MigrationCallbackRegistry.MigrationCallbacks(
            onMigrationSuccess = onSuccess,
            onMigrationError = onError
        )
        val key = MigrationCallbackRegistry.register(callbacks)

        // When
        val consumedCallbacks = MigrationCallbackRegistry.consume(key)

        // Then
        assertNotNull("Consumed callbacks should not be null", consumedCallbacks)
        assertEquals("Consumed callbacks should match registered callbacks", callbacks, consumedCallbacks)
    }

    @Test
    fun consume_returnsNullForNonExistentKey() {
        // When
        val result = MigrationCallbackRegistry.consume("non-existent-key")

        // Then
        assertNull("Should return null for non-existent key", result)
    }

    @Test
    fun consume_returnsNullOnSecondAttempt() {
        // Given
        val onSuccess: (UserAccount) -> Unit = mockk(relaxed = true)
        val onError: (String, String?, Throwable?) -> Unit = mockk(relaxed = true)
        val callbacks = MigrationCallbackRegistry.MigrationCallbacks(
            onMigrationSuccess = onSuccess,
            onMigrationError = onError
        )
        val key = MigrationCallbackRegistry.register(callbacks)

        // When
        val firstConsume = MigrationCallbackRegistry.consume(key)
        val secondConsume = MigrationCallbackRegistry.consume(key)

        // Then
        assertNotNull("First consume should return callbacks", firstConsume)
        assertNull("Second consume should return null", secondConsume)
    }

    @Test
    fun migrationCallbacks_onMigrationSuccess_invokesCallback() {
        // Given
        val mockAccount: UserAccount = mockk()
        val onSuccess: (UserAccount) -> Unit = mockk(relaxed = true)
        val onError: (String, String?, Throwable?) -> Unit = mockk(relaxed = true)
        val callbacks = MigrationCallbackRegistry.MigrationCallbacks(
            onMigrationSuccess = onSuccess,
            onMigrationError = onError
        )

        // When
        callbacks.onMigrationSuccess(mockAccount)

        // Then
        verify(exactly = 1) { onSuccess(mockAccount) }
    }

    @Test
    fun migrationCallbacks_onMigrationError_invokesCallbackWithAllParams() {
        // Given
        val error = "Test error"
        val errorDesc = "Test error description"
        val throwable = RuntimeException("Test exception")
        val onSuccess: (UserAccount) -> Unit = mockk(relaxed = true)
        val onError: (String, String?, Throwable?) -> Unit = mockk(relaxed = true)
        val callbacks = MigrationCallbackRegistry.MigrationCallbacks(
            onMigrationSuccess = onSuccess,
            onMigrationError = onError
        )

        // When
        callbacks.onMigrationError(error, errorDesc, throwable)

        // Then
        verify(exactly = 1) { onError(error, errorDesc, throwable) }
    }

    @Test
    fun migrationCallbacks_onMigrationError_invokesCallbackWithNullParams() {
        // Given
        val error = "Test error"
        val onSuccess: (UserAccount) -> Unit = mockk(relaxed = true)
        val onError: (String, String?, Throwable?) -> Unit = mockk(relaxed = true)
        val callbacks = MigrationCallbackRegistry.MigrationCallbacks(
            onMigrationSuccess = onSuccess,
            onMigrationError = onError
        )

        // When
        callbacks.onMigrationError(error, null, null)

        // Then
        verify(exactly = 1) { onError(error, null, null) }
    }

    @Test
    fun multipleCallbacks_canBeRegisteredAndConsumedIndependently() {
        // Given
        val onSuccess1: (UserAccount) -> Unit = mockk(relaxed = true)
        val onError1: (String, String?, Throwable?) -> Unit = mockk(relaxed = true)
        val callbacks1 = MigrationCallbackRegistry.MigrationCallbacks(
            onMigrationSuccess = onSuccess1,
            onMigrationError = onError1
        )

        val onSuccess2: (UserAccount) -> Unit = mockk(relaxed = true)
        val onError2: (String, String?, Throwable?) -> Unit = mockk(relaxed = true)
        val callbacks2 = MigrationCallbackRegistry.MigrationCallbacks(
            onMigrationSuccess = onSuccess2,
            onMigrationError = onError2
        )

        // When
        val key1 = MigrationCallbackRegistry.register(callbacks1)
        val key2 = MigrationCallbackRegistry.register(callbacks2)

        // Then - consume in reverse order
        val consumed2 = MigrationCallbackRegistry.consume(key2)
        val consumed1 = MigrationCallbackRegistry.consume(key1)

        assertEquals("Callbacks 2 should be consumed correctly", callbacks2, consumed2)
        assertEquals("Callbacks 1 should be consumed correctly", callbacks1, consumed1)
    }
}
