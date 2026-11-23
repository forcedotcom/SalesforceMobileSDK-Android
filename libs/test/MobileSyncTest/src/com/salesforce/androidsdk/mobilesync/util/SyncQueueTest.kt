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
package com.salesforce.androidsdk.mobilesync.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager
import com.salesforce.androidsdk.mobilesync.manager.SyncManager
import com.salesforce.androidsdk.smartstore.store.IndexSpec
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import com.salesforce.androidsdk.smartstore.store.SmartStore
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests of the sync-up change queue soup: registration and sequence allocation.
 * Network-free: runs against a global store.
 */
@RunWith(AndroidJUnit4::class)
class SyncQueueTest {

    private lateinit var store: SmartStore

    @Before
    fun setUp() {
        SyncManager.reset()
        MobileSyncSDKManager.getInstance().removeGlobalSmartStore(DB_NAME)
        store = MobileSyncSDKManager.getInstance().getGlobalSmartStore(DB_NAME)
    }

    @After
    fun tearDown() {
        SyncManager.reset()
        MobileSyncSDKManager.getInstance().removeGlobalSmartStore(DB_NAME)
    }

    @Test
    fun test_givenNewStore_whenSyncManagerCreated_thenQueueSoupIsRegistered() {
        assertFalse(store.hasSoup(SyncQueue.QUEUE_SOUP))

        SyncManager.getInstance(null, null, store)

        assertTrue(store.hasSoup(SyncQueue.QUEUE_SOUP))
        checkQueueIndexSpecs()
    }

    @Test
    fun test_givenQueueSoupWithEntries_whenSetupCalledAgain_thenSoupAndEntriesUntouched() {
        SyncQueue.setupQueueSoupIfNeeded(store)
        store.create(SyncQueue.QUEUE_SOUP, JSONObject().put(SyncQueue.SEQUENCE, 1).put(SyncQueue.RECORD_ID, "a"))

        SyncQueue.setupQueueSoupIfNeeded(store)

        checkQueueIndexSpecs()
        assertEquals(1, queueEntries().length())
    }

    @Test
    fun test_givenQueueSoupWithWrongIndexSpecCount_whenSetupCalled_thenSoupIsAltered() {
        store.registerSoup(
            SyncQueue.QUEUE_SOUP,
            arrayOf(IndexSpec(SyncQueue.SEQUENCE, SmartStore.Type.string), IndexSpec(SyncQueue.SOUP_NAME, SmartStore.Type.string))
        )
        store.create(SyncQueue.QUEUE_SOUP, JSONObject().put(SyncQueue.SEQUENCE, 7).put(SyncQueue.RECORD_ID, "a"))

        SyncQueue.setupQueueSoupIfNeeded(store)

        checkQueueIndexSpecs()
        assertEquals(1, queueEntries().length())
    }

    @Test
    fun test_givenStore_whenSyncManagerCreated_thenSyncsSoupIsUntouched() {
        SyncManager.getInstance(null, null, store)

        assertTrue(store.hasSoup(SyncState.SYNCS_SOUP))
        assertEquals(3, store.getSoupIndexSpecs(SyncState.SYNCS_SOUP).size)
    }

    @Test
    fun test_givenEmptyQueue_whenNextSequence_thenReturnsOne() {
        SyncQueue.setupQueueSoupIfNeeded(store)

        assertEquals(1L, SyncQueue.nextSequence(store))
    }

    @Test
    fun test_givenQueueWithEntries_whenNextSequence_thenReturnsMaxPlusOne() {
        SyncQueue.setupQueueSoupIfNeeded(store)
        store.create(SyncQueue.QUEUE_SOUP, JSONObject().put(SyncQueue.SEQUENCE, 3))
        store.create(SyncQueue.QUEUE_SOUP, JSONObject().put(SyncQueue.SEQUENCE, 10))
        store.create(SyncQueue.QUEUE_SOUP, JSONObject().put(SyncQueue.SEQUENCE, 9))

        assertEquals(11L, SyncQueue.nextSequence(store))
    }

    @Test
    fun test_givenSeveralRecordSoups_whenWritingToEach_thenSequencesAreMonotonicInWriteOrder() {
        val manager = SyncManager.getInstance(null, null, store)
        registerRecordSoup("soupA")
        registerRecordSoup("soupB")

        manager.createLocal("soupA", account("local_a1"))
        manager.createLocal("soupB", account("local_b1"))
        manager.createLocal("soupA", account("local_a2"))

        val entries = queueEntries()
        assertEquals(3, entries.length())
        assertEquals(listOf(1L, 2L, 3L), (0 until 3).map { entries.getJSONObject(it).getLong(SyncQueue.SEQUENCE) })
        assertEquals(
            listOf("local_a1", "local_b1", "local_a2"),
            (0 until 3).map { entries.getJSONObject(it).getString(SyncQueue.RECORD_ID) }
        )
        assertEquals(
            listOf("soupA", "soupB", "soupA"),
            (0 until 3).map { entries.getJSONObject(it).getString(SyncQueue.SOUP_NAME) }
        )
    }

    @Test
    fun test_givenMoreThanTenEntries_whenQueryingBySequence_thenOrderIsNumeric() {
        val manager = SyncManager.getInstance(null, null, store)
        registerRecordSoup("soupA")
        val ids = (1..12).map { "local_$it" }

        ids.forEach { manager.createLocal("soupA", account(it)) }

        val entries = queueEntries()
        assertEquals(12, entries.length())
        assertEquals((1L..12L).toList(), (0 until 12).map { entries.getJSONObject(it).getLong(SyncQueue.SEQUENCE) })
        assertEquals(ids, (0 until 12).map { entries.getJSONObject(it).getString(SyncQueue.RECORD_ID) })
    }

    private fun checkQueueIndexSpecs() {
        val expected = mapOf(
            SyncQueue.SEQUENCE to SmartStore.Type.integer,
            SyncQueue.SOUP_NAME to SmartStore.Type.string,
            SyncQueue.RECORD_ID to SmartStore.Type.string,
            SyncQueue.SOBJECT_TYPE to SmartStore.Type.string,
            SyncQueue.KIND to SmartStore.Type.string,
            SyncQueue.CHANGES to SmartStore.Type.json1,
            SyncQueue.BASE to SmartStore.Type.json1,
            SyncQueue.STATUS to SmartStore.Type.string,
            SyncQueue.LAST_ERROR to SmartStore.Type.json1,
            SyncQueue.ATTEMPTS to SmartStore.Type.integer,
            SyncQueue.PARENT_REF to SmartStore.Type.string,
        )
        val actual = store.getSoupIndexSpecs(SyncQueue.QUEUE_SOUP).associate { it.path to it.type }
        assertEquals(expected, actual)
    }

    private fun queueEntries() = store.query(
        QuerySpec.buildAllQuerySpec(SyncQueue.QUEUE_SOUP, SyncQueue.SEQUENCE, QuerySpec.Order.ascending, 100),
        0
    )

    private fun registerRecordSoup(name: String) {
        store.registerSoup(
            name,
            arrayOf(
                IndexSpec("Id", SmartStore.Type.string),
                IndexSpec("Name", SmartStore.Type.string),
            )
        )
    }

    private fun account(id: String) = JSONObject()
        .put("Id", id)
        .put("Name", "name-$id")
        .put("attributes", JSONObject().put("type", "Account"))

    companion object {
        private const val DB_NAME = "syncQueueTestDb"
    }
}
