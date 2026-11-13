//
//  DPoPMigrationManagerTests.swift
//  SalesforceSDKCoreTests
//
//  Copyright (c) 2026-present, salesforce.com, inc. All rights reserved.
//
//  Redistribution and use of this software in source and binary forms, with or without modification,
//  are permitted provided that the following conditions are met:
//  * Redistributions of source code must retain the above copyright notice, this list of conditions
//  and the following disclaimer.
//  * Redistributions in binary form must reproduce the above copyright notice, this list of
//  conditions and the following disclaimer in the documentation and/or other materials provided
//  with the distribution.
//  * Neither the name of salesforce.com, inc. nor the names of its contributors may be used to
//  endorse or promote products derived from this software without specific prior written
//  permission of salesforce.com, inc.
//
//  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
//  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
//  FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
//  DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
//  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
package com.salesforce.androidsdk.auth.dpop

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.auth.dpop.DpopMigrationManager.Outcome
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
@SmallTest
class DpopMigrationManagerTest {

    private class FakeUpgrade(
        val onSuccess: (UserAccount) -> Unit,
        val onFailure: (String, String?, Throwable?) -> Unit,
    )

    private val upgrades = CopyOnWriteArrayList<FakeUpgrade>()
    private val bearerUser = user("00D1", "005A", "Bearer")
    private val dpopUser = user("00D1", "005B", "DPoP")

    @Before
    fun setUp() {
        DpopMigrationManager.resetForTesting()
        DpopMigrationManager.upgrader = { _, onSuccess, onFailure ->
            upgrades.add(FakeUpgrade(onSuccess, onFailure))
        }
    }

    @After
    fun tearDown() {
        DpopMigrationManager.resetForTesting()
    }

    @Test
    fun test_givenNoUser_whenMigrate_thenNotNeeded() = runBlocking {
        assertEquals(Outcome.NotNeeded("no user"), DpopMigrationManager.migrateIfNeeded(null))
        assertFalse(DpopMigrationManager.isMigrationNeeded(null))
        assertTrue(upgrades.isEmpty())
    }

    @Test
    fun test_givenAlreadyDPoP_whenMigrate_thenNotNeeded() = runBlocking {
        assertFalse(DpopMigrationManager.isMigrationNeeded(dpopUser))
        assertEquals(Outcome.NotNeeded("already DPoP"), DpopMigrationManager.migrateIfNeeded(dpopUser))
        assertTrue(upgrades.isEmpty())
    }

    @Test
    fun test_givenBearerUser_whenUpgradeSucceedsOffMainThread_thenMigratedOnMainThread() = runBlocking {
        assertTrue(DpopMigrationManager.isMigrationNeeded(bearerUser))
        val result = async { DpopMigrationManager.migrateIfNeeded(bearerUser) }
        awaitUpgrades(1)
        thread { upgrades[0].onSuccess(bearerUser) }.join()
        assertEquals(Outcome.Migrated, withTimeout(WAIT_MS) { result.await() })
        assertFalse(DpopMigrationManager.isMigrationNeeded(dpopUser))
    }

    @Test
    fun test_givenBearerUser_whenUpgradeFails_thenFailedAndNextCallRetries() = runBlocking {
        val first = async { DpopMigrationManager.migrateIfNeeded(bearerUser) }
        awaitUpgrades(1)
        thread { upgrades[0].onFailure("denied", "user said no", null) }.join()
        assertEquals(Outcome.Failed("denied: user said no"), withTimeout(WAIT_MS) { first.await() })

        assertTrue(DpopMigrationManager.isMigrationNeeded(bearerUser))
        val second = async { DpopMigrationManager.migrateIfNeeded(bearerUser) }
        awaitUpgrades(2)
        thread { upgrades[1].onSuccess(bearerUser) }.join()
        assertEquals(Outcome.Migrated, withTimeout(WAIT_MS) { second.await() })
    }

    @Test
    fun test_givenSdkNeverCallsBack_whenTimeoutElapses_thenFailedTimedOut() = runBlocking {
        val result = DpopMigrationManager.migrateIfNeeded(bearerUser, timeoutMs = SHORT_TIMEOUT_MS)
        assertEquals(Outcome.Failed(DpopMigrationManager.REASON_TIMED_OUT), result)
        assertTrue(DpopMigrationManager.isMigrationNeeded(bearerUser))
    }

    @Test
    fun test_givenTimedOut_whenLateSuccessArrives_thenCallerNotCompletedTwiceAndNoCrash() = runBlocking {
        val outcomes = CopyOnWriteArrayList<Outcome>()
        val done = CountDownLatch(1)
        DpopMigrationManager.migrateIfNeeded(bearerUser, SHORT_TIMEOUT_MS) {
            outcomes.add(it)
            done.countDown()
        }
        assertTrue(done.await(WAIT_MS, TimeUnit.MILLISECONDS))
        thread { upgrades[0].onSuccess(bearerUser) }.join()
        flushMainThread()
        assertEquals(listOf<Outcome>(Outcome.Failed(DpopMigrationManager.REASON_TIMED_OUT)), outcomes)
        assertFalse(DpopMigrationManager.isMigrationNeeded(dpopUser))
    }

    @Test
    fun test_givenTimedOutAndRetryInFlight_whenOldCallbacksArrive_thenFailureIgnoredAndSuccessCompletesRetry() = runBlocking {
        val first = DpopMigrationManager.migrateIfNeeded(bearerUser, timeoutMs = SHORT_TIMEOUT_MS)
        assertEquals(Outcome.Failed(DpopMigrationManager.REASON_TIMED_OUT), first)

        val retry = async { DpopMigrationManager.migrateIfNeeded(bearerUser) }
        awaitUpgrades(2)

        // Stale failure from the first, timed-out upgrade must not complete the retry.
        thread { upgrades[0].onFailure("stale", null, null) }.join()
        flushMainThread()
        assertFalse(retry.isCompleted)

        // Stale success means the user really is DPoP-bound now: the retry completes as Migrated.
        thread { upgrades[0].onSuccess(bearerUser) }.join()
        assertEquals(Outcome.Migrated, withTimeout(WAIT_MS) { retry.await() })
    }

    @Test
    fun test_givenConcurrentCallers_whenMigrate_thenSingleUpgradeAndEveryCallerCompletedOnce() = runBlocking {
        val callers = List(5) { startOnMain(bearerUser) }
        assertEquals(1, upgrades.size)
        assertTrue(DpopMigrationManager.isMigrationNeeded(bearerUser))
        thread { upgrades[0].onSuccess(bearerUser) }.join()
        val results = withTimeout(WAIT_MS) { callers.awaitAll() }
        assertEquals(List(5) { Outcome.Migrated }, results)
        assertEquals(1, upgrades.size)
    }

    @Test
    fun test_givenCallback_whenUpgradeCompletes_thenInvokedOnMainThreadOnce() {
        val threads = CopyOnWriteArrayList<Looper?>()
        val done = CountDownLatch(1)
        thread {
            DpopMigrationManager.migrateIfNeeded(bearerUser) {
                threads.add(Looper.myLooper())
                done.countDown()
            }
        }.join()
        awaitUpgradesBlocking(1)
        thread { upgrades[0].onSuccess(bearerUser) }.join()
        assertTrue(done.await(WAIT_MS, TimeUnit.MILLISECONDS))
        flushMainThread()
        assertEquals(listOf<Looper?>(Looper.getMainLooper()), threads)
    }

    @Test
    fun test_givenUpgradeInFlight_whenActivityResumesAgain_thenJoinsSameUpgrade() = runBlocking {
        val first = startOnMain(bearerUser)
        // Re-entry (e.g. onResume re-fired when TokenMigrationActivity finishes).
        assertTrue(DpopMigrationManager.isMigrationNeeded(bearerUser))
        val second = startOnMain(bearerUser)
        assertEquals(1, upgrades.size)
        thread { upgrades[0].onSuccess(bearerUser) }.join()
        assertEquals(Outcome.Migrated, withTimeout(WAIT_MS) { first.await() })
        assertEquals(Outcome.Migrated, withTimeout(WAIT_MS) { second.await() })
    }

    @Test
    fun test_givenDifferentUsers_whenMigrate_thenIndependentUpgrades() = runBlocking {
        val other = user("00D1", "005C", "Bearer")
        val a = startOnMain(bearerUser)
        val b = startOnMain(other)
        assertEquals(2, upgrades.size)
        thread { upgrades[0].onSuccess(bearerUser); upgrades[1].onSuccess(other) }.join()
        assertEquals(Outcome.Migrated, withTimeout(WAIT_MS) { a.await() })
        assertEquals(Outcome.Migrated, withTimeout(WAIT_MS) { b.await() })
    }

    // region Helpers

    private fun user(orgId: String, userId: String, tokenType: String) = mockk<UserAccount> {
        every { this@mockk.orgId } returns orgId
        every { this@mockk.userId } returns userId
        every { this@mockk.tokenType } returns tokenType
    }

    /** Waits (without sleeping) until the manager has asked the SDK for [count] upgrades. */
    private suspend fun awaitUpgrades(count: Int) {
        val gate = CompletableDeferred<Unit>()
        val poller = thread {
            while (upgrades.size < count) Thread.yield()
            gate.complete(Unit)
        }
        try {
            withTimeout(WAIT_MS) { gate.await() }
        } finally {
            poller.interrupt()
        }
    }

    private fun awaitUpgradesBlocking(count: Int) = runBlocking { awaitUpgrades(count) }

    /** Calls the callback API on the main thread and returns once the call has been processed. */
    private fun startOnMain(user: UserAccount): CompletableDeferred<Outcome> {
        val result = CompletableDeferred<Outcome>()
        android.os.Handler(Looper.getMainLooper()).post {
            DpopMigrationManager.migrateIfNeeded(user) { result.complete(it) }
        }
        flushMainThread()
        return result
    }

    /** Lets everything already posted to the main looper run. */
    private fun flushMainThread() {
        val latch = CountDownLatch(1)
        android.os.Handler(Looper.getMainLooper()).post { latch.countDown() }
        assertTrue(latch.await(WAIT_MS, TimeUnit.MILLISECONDS))
    }

    // endregion

    private companion object {
        const val WAIT_MS = 10_000L
        const val SHORT_TIMEOUT_MS = 200L
    }
}
