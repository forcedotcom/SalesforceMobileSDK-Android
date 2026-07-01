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
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.salesforce.androidsdk.accounts.UserAccountTest.createTestAccount
import com.salesforce.androidsdk.push.PushNotificationsRegistrationChangeWorker.PushNotificationsRegistrationAction.Register
import com.salesforce.androidsdk.push.PushService.Companion.encryptUserAccountJson
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [PushNotificationsRegistrationChangeWorker]'s handling of the
 * encrypted `USER_ACCOUNT` payload.
 *
 * Regression coverage: the worker must fail (rather than silently widen the
 * registration scope to all users) when a specific-account payload cannot be
 * decrypted, and must still treat an absent payload as the "all authenticated
 * users" signal.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class PushNotificationsRegistrationChangeWorkerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun buildWorker(inputData: androidx.work.Data) =
        TestListenableWorkerBuilder<PushNotificationsRegistrationChangeWorker>(
            context = context,
            inputData = inputData
        ).build()

    /**
     * A present-but-undecryptable `USER_ACCOUNT` payload is a bad required
     * input: the worker must return [Result.failure] rather than fall through
     * to the all-users path. (Registration work is re-enqueued with REPLACE on
     * the next foreground/login, so the discarded worker is replaced.)
     */
    @Test
    fun testDoWork_undecryptableUserAccount_returnsFailure() {
        val worker = buildWorker(
            workDataOf(
                "ACTION" to Register.name,
                "USER_ACCOUNT" to "this-is-not-a-valid-encrypted-payload"
            )
        )

        val result = worker.doWork()

        assertEquals(Result.failure(), result)
    }

    /**
     * An absent `USER_ACCOUNT` payload legitimately specifies all authenticated
     * users. With no authenticated users present, the worker completes
     * successfully (iterating an empty user set) rather than failing.
     */
    @Test
    fun testDoWork_absentUserAccount_returnsSuccess() {
        val worker = buildWorker(
            workDataOf(
                "ACTION" to Register.name
            )
        )

        val result = worker.doWork()

        assertEquals(Result.success(), result)
    }

    /**
     * A present, decryptable `USER_ACCOUNT` payload is reconstructed into a
     * specific account and the worker completes successfully. Exercises the
     * decrypt-then-deserialize path for a specified account.
     */
    @Test
    fun testDoWork_validEncryptedUserAccount_returnsSuccess() {
        val encryptedUserAccount = encryptUserAccountJson(
            createTestAccount().toJson().toString()
        )
        val worker = buildWorker(
            workDataOf(
                "ACTION" to Register.name,
                "USER_ACCOUNT" to encryptedUserAccount
            )
        )

        val result = worker.doWork()

        assertEquals(Result.success(), result)
    }

    /**
     * A missing `ACTION` is a required-input failure — pre-existing behavior,
     * asserted here to guard against regressions from the payload changes.
     */
    @Test
    fun testDoWork_missingAction_returnsFailure() {
        val worker = buildWorker(workDataOf())

        val result = worker.doWork()

        assertEquals(Result.failure(), result)
    }
}
