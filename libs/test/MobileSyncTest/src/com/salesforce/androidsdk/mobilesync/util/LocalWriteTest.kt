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
import com.salesforce.androidsdk.mobilesync.manager.SyncManager.MobileSyncException
import com.salesforce.androidsdk.mobilesync.target.SyncTarget
import com.salesforce.androidsdk.smartstore.store.DBHelper
import com.salesforce.androidsdk.smartstore.store.IndexSpec
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import com.salesforce.androidsdk.smartstore.store.SmartStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests of the local write API of SyncManager (createLocal, updateLocal, deleteLocal).
 * Network-free: runs against a global store.
 */
@RunWith(AndroidJUnit4::class)
class LocalWriteTest {

    private lateinit var store: SmartStore
    private lateinit var manager: SyncManager

    @Before
    fun setUp() {
        SyncManager.reset()
        MobileSyncSDKManager.getInstance().removeGlobalSmartStore(DB_NAME)
        store = MobileSyncSDKManager.getInstance().getGlobalSmartStore(DB_NAME)
        manager = SyncManager.getInstance(null, null, store)
        registerRecordSoup(SOUP)
    }

    @After
    fun tearDown() {
        SyncManager.reset()
        MobileSyncSDKManager.getInstance().removeGlobalSmartStore(DB_NAME)
    }

    // region createLocal

    @Test
    fun test_givenRecordWithLocalId_whenCreateLocal_thenRecordAndEntryAreWritten() {
        val saved = manager.createLocal(SOUP, account("local_1").put("Phone", "555"))

        val stored = record("local_1")
        assertEquals("name-local_1", stored.getString("Name"))
        assertEquals(stored.getLong(SmartStore.SOUP_ENTRY_ID), saved.getLong(SmartStore.SOUP_ENTRY_ID))
        checkFlags(stored, local = true, created = true, updated = false, deleted = false)
        assertFalse(stored.has(SyncTarget.LAST_ERROR))

        val entry = singleEntry()
        assertEquals(1L, entry.getLong(SyncQueue.SEQUENCE))
        assertEquals(SOUP, entry.getString(SyncQueue.SOUP_NAME))
        assertEquals("local_1", entry.getString(SyncQueue.RECORD_ID))
        assertEquals("Account", entry.getString(SyncQueue.SOBJECT_TYPE))
        assertEquals("CREATE", entry.getString(SyncQueue.KIND))
        assertEquals("PENDING", entry.getString(SyncQueue.STATUS))
        assertEquals(0, entry.getInt(SyncQueue.ATTEMPTS))
        assertFalse(entry.has(SyncQueue.LAST_ERROR))
        assertFalse(entry.has(SyncQueue.BASE))
        assertFalse(entry.has(SyncQueue.PARENT_REF))
        val changes = entry.getJSONObject(SyncQueue.CHANGES)
        assertEquals(setOf("Name", "Phone"), changes.keys().asSequence().toSet())
        assertEquals("555", changes.getString("Phone"))
    }

    @Test
    fun test_givenRecordWithoutId_whenCreateLocal_thenLocalIdIsGenerated() {
        val saved = manager.createLocal(SOUP, account(null))

        val id = saved.getString("Id")
        assertTrue(SyncTarget.isLocalId(id))
        assertEquals(id, singleEntry().getString(SyncQueue.RECORD_ID))
        assertEquals(1, count(SOUP))
    }

    @Test
    fun test_givenRecordWithNullId_whenCreateLocal_thenLocalIdIsGenerated() {
        val saved = manager.createLocal(SOUP, account(null).put("Id", JSONObject.NULL))

        assertTrue(SyncTarget.isLocalId(saved.getString("Id")))
    }

    @Test
    fun test_givenRecordWithFlagsAndLastError_whenCreateLocal_thenFlagsAreOverriddenAndNotInChanges() {
        val input = account("local_1")
            .put(SyncTarget.LOCALLY_DELETED, true)
            .put(SyncTarget.LAST_ERROR, "boom")

        manager.createLocal(SOUP, input)

        checkFlags(record("local_1"), local = true, created = true, updated = false, deleted = false)
        assertFalse(record("local_1").has(SyncTarget.LAST_ERROR))
        assertEquals(setOf("Name"), singleEntry().getJSONObject(SyncQueue.CHANGES).keys().asSequence().toSet())
    }

    @Test
    fun test_givenRecord_whenCreateLocal_thenCallerRecordIsNotMutated() {
        val input = account("local_1")
        val before = input.toString()

        manager.createLocal(SOUP, input)

        assertEquals(before, input.toString())
    }

    @Test
    fun test_givenRecordWithNullFieldValue_whenCreateLocal_thenNullIsKept() {
        manager.createLocal(SOUP, account("local_1").put("Phone", JSONObject.NULL))

        assertTrue(record("local_1").isNull("Phone"))
        assertTrue(record("local_1").has("Phone"))
        assertTrue(singleEntry().getJSONObject(SyncQueue.CHANGES).has("Phone"))
    }

    @Test
    fun test_givenServerId_whenCreateLocal_thenFailsWithNothingWritten() {
        assertWriteFails { manager.createLocal(SOUP, account("001000000000001")) }
        assertNothingWritten()
    }

    @Test
    fun test_givenExistingId_whenCreateLocal_thenFailsAndExistingRecordIsUntouched() {
        manager.createLocal(SOUP, account("local_1"))
        val before = record("local_1").toString()

        assertWriteFails { manager.createLocal(SOUP, account("local_1").put("Name", "other")) }

        assertEquals(before, record("local_1").toString())
        assertEquals(1, count(SOUP))
        assertEquals(1, count(SyncQueue.QUEUE_SOUP))
    }

    @Test
    fun test_givenRecordWithoutType_whenCreateLocal_thenFailsWithNothingWritten() {
        assertWriteFails { manager.createLocal(SOUP, JSONObject().put("Id", "local_1").put("Name", "n")) }
        assertNothingWritten()
    }

    @Test
    fun test_givenRecordWithSoupEntryId_whenCreateLocal_thenFailsWithNothingWritten() {
        manager.createLocal(SOUP, account("local_1"))
        val before = record("local_1").toString()

        assertWriteFails { manager.createLocal(SOUP, account("local_2").put(SmartStore.SOUP_ENTRY_ID, 1)) }

        assertEquals(before, record("local_1").toString())
        assertEquals(1, count(SOUP))
        assertEquals(1, count(SyncQueue.QUEUE_SOUP))
    }

    @Test
    fun test_givenMissingSoup_whenCreateLocal_thenFails() {
        assertWriteFails { manager.createLocal("noSuchSoup", account("local_1")) }
        assertEquals(0, count(SyncQueue.QUEUE_SOUP))
    }

    @Test
    fun test_givenSoupWithoutIdIndex_whenWriting_thenFailsWithNothingWritten() {
        store.registerSoup("noIdSoup", arrayOf(IndexSpec("Name", SmartStore.Type.string)))

        assertWriteFails { manager.createLocal("noIdSoup", account("local_1")) }
        assertWriteFails { manager.updateLocal("noIdSoup", "local_1", JSONObject().put("Name", "x")) }
        assertWriteFails { manager.deleteLocal("noIdSoup", "local_1") }

        assertEquals(0, count("noIdSoup"))
        assertEquals(0, count(SyncQueue.QUEUE_SOUP))
    }

    @Test
    fun test_givenSoupWithoutFlagIndexes_whenCreateLocal_thenRecordIsWritten() {
        store.registerSoup(SOUP_ID_ONLY, arrayOf(IndexSpec("Id", SmartStore.Type.string)))

        manager.createLocal(SOUP_ID_ONLY, account("local_1"))

        assertEquals(1, count(SOUP_ID_ONLY))
        assertEquals(1, count(SyncQueue.QUEUE_SOUP))
    }

    @Test
    fun test_givenDroppedQueueSoup_whenCreateLocal_thenQueueSoupIsRegisteredAgain() {
        store.dropSoup(SyncQueue.QUEUE_SOUP)

        manager.createLocal(SOUP, account("local_1"))

        assertEquals(1L, singleEntry().getLong(SyncQueue.SEQUENCE))
    }

    // endregion

    // region updateLocal

    @Test
    fun test_givenServerRecord_whenUpdateLocal_thenMergedAndEntryHasChangesOnly() {
        insertCleanRecord("001A", name = "orig", phone = "111")

        val saved = manager.updateLocal(SOUP, "001A", JSONObject().put("Phone", "222"))

        val stored = record("001A")
        assertEquals("222", stored.getString("Phone"))
        assertEquals("orig", stored.getString("Name"))
        assertEquals("222", saved.getString("Phone"))
        assertEquals("orig", saved.getString("Name"))
        checkFlags(stored, local = true, created = false, updated = true, deleted = false)

        val entry = singleEntry()
        assertEquals("UPDATE", entry.getString(SyncQueue.KIND))
        assertEquals("001A", entry.getString(SyncQueue.RECORD_ID))
        assertEquals("Account", entry.getString(SyncQueue.SOBJECT_TYPE))
        assertEquals("PENDING", entry.getString(SyncQueue.STATUS))
        assertEquals(0, entry.getInt(SyncQueue.ATTEMPTS))
        val changes = entry.getJSONObject(SyncQueue.CHANGES)
        assertEquals(setOf("Phone"), changes.keys().asSequence().toSet())
        assertEquals("222", changes.getString("Phone"))
    }

    @Test
    fun test_givenLocallyCreatedRecord_whenUpdateLocal_thenCreatedFlagIsPreserved() {
        manager.createLocal(SOUP, account("local_1"))

        manager.updateLocal(SOUP, "local_1", JSONObject().put("Name", "renamed"))

        checkFlags(record("local_1"), local = true, created = true, updated = true, deleted = false)
        assertEquals("renamed", record("local_1").getString("Name"))
        assertEquals(2, count(SyncQueue.QUEUE_SOUP))
    }

    @Test
    fun test_givenField_whenUpdateLocalWithExplicitNull_thenFieldIsClearedAndChangeRecorded() {
        insertCleanRecord("001A", name = "orig", phone = "111")

        manager.updateLocal(SOUP, "001A", JSONObject().put("Phone", JSONObject.NULL))

        assertTrue(record("001A").isNull("Phone"))
        val changes = singleEntry().getJSONObject(SyncQueue.CHANGES)
        assertTrue(changes.has("Phone"))
        assertTrue(changes.isNull("Phone"))
    }

    @Test
    fun test_givenChangesNotMutated_whenUpdateLocal_thenCallerChangesAreUntouched() {
        insertCleanRecord("001A", name = "orig", phone = "111")
        val changes = JSONObject().put("Phone", "222")
        val before = changes.toString()

        manager.updateLocal(SOUP, "001A", changes)

        assertEquals(before, changes.toString())
    }

    @Test
    fun test_givenEmptyChanges_whenUpdateLocal_thenFailsWithNothingWritten() {
        insertCleanRecord("001A", name = "orig", phone = "111")
        val before = record("001A").toString()

        assertWriteFails { manager.updateLocal(SOUP, "001A", JSONObject()) }

        assertEquals(before, record("001A").toString())
        assertEquals(0, count(SyncQueue.QUEUE_SOUP))
    }

    @Test
    fun test_givenInternalFieldInChanges_whenUpdateLocal_thenFailsWithNothingWritten() {
        insertCleanRecord("001A", name = "orig", phone = "111")
        val before = record("001A").toString()
        val internalFields = listOf(
            "Id", "attributes", SmartStore.SOUP_ENTRY_ID, SmartStore.SOUP_LAST_MODIFIED_DATE,
            SyncTarget.LOCAL, SyncTarget.LOCALLY_CREATED, SyncTarget.LOCALLY_UPDATED,
            SyncTarget.LOCALLY_DELETED, SyncTarget.LAST_ERROR, SyncTarget.SYNC_ID, "__display__"
        )

        for (field in internalFields) {
            assertWriteFails { manager.updateLocal(SOUP, "001A", JSONObject().put("Phone", "222").put(field, "x")) }
        }

        assertEquals(before, record("001A").toString())
        assertEquals(0, count(SyncQueue.QUEUE_SOUP))
    }

    @Test
    fun test_givenMissingRecord_whenUpdateLocal_thenFailsWithNothingWritten() {
        assertWriteFails { manager.updateLocal(SOUP, "001Z", JSONObject().put("Name", "x")) }
        assertNothingWritten()
    }

    @Test
    fun test_givenDeletedRecord_whenUpdateLocal_thenFailsWithNothingChanged() {
        insertCleanRecord("001A", name = "orig", phone = "111")
        manager.deleteLocal(SOUP, "001A")
        val before = record("001A").toString()

        assertWriteFails { manager.updateLocal(SOUP, "001A", JSONObject().put("Name", "x")) }

        assertEquals(before, record("001A").toString())
        assertEquals(1, count(SyncQueue.QUEUE_SOUP))
    }

    @Test
    fun test_givenDuplicateIds_whenUpdateLocal_thenFailsWithNothingWritten() {
        insertCleanRecord("001A", name = "one", phone = "1")
        insertCleanRecord("001A", name = "two", phone = "2")

        assertWriteFails { manager.updateLocal(SOUP, "001A", JSONObject().put("Name", "x")) }

        assertEquals(0, count(SyncQueue.QUEUE_SOUP))
    }

    @Test
    fun test_givenRecordWithoutType_whenUpdateLocal_thenEntryHasNoObjectType() {
        store.upsert(SOUP, JSONObject().put("Id", "001A").put("Name", "orig"))

        manager.updateLocal(SOUP, "001A", JSONObject().put("Name", "new"))

        assertFalse(singleEntry().has(SyncQueue.SOBJECT_TYPE))
    }

    // endregion

    // region deleteLocal

    @Test
    fun test_givenServerRecord_whenDeleteLocal_thenFlaggedAndKeptWithDeleteEntry() {
        insertCleanRecord("001A", name = "orig", phone = "111")

        val saved = manager.deleteLocal(SOUP, "001A")

        val stored = record("001A")
        assertEquals("orig", stored.getString("Name"))
        assertTrue(saved.getBoolean(SyncTarget.LOCALLY_DELETED))
        checkFlags(stored, local = true, created = false, updated = false, deleted = true)

        val entry = singleEntry()
        assertEquals("DELETE", entry.getString(SyncQueue.KIND))
        assertEquals("001A", entry.getString(SyncQueue.RECORD_ID))
        assertEquals("Account", entry.getString(SyncQueue.SOBJECT_TYPE))
        assertEquals("PENDING", entry.getString(SyncQueue.STATUS))
        assertFalse(entry.has(SyncQueue.CHANGES))
    }

    @Test
    fun test_givenLocallyCreatedAndUpdatedRecord_whenDeleteLocal_thenCreatedAndUpdatedFlagsUnchanged() {
        manager.createLocal(SOUP, account("local_1"))
        manager.updateLocal(SOUP, "local_1", JSONObject().put("Name", "renamed"))

        manager.deleteLocal(SOUP, "local_1")

        checkFlags(record("local_1"), local = true, created = true, updated = true, deleted = true)
        assertEquals(3, count(SyncQueue.QUEUE_SOUP))
    }

    @Test
    fun test_givenDeletedRecord_whenDeleteLocal_thenFailsWithNothingChanged() {
        insertCleanRecord("001A", name = "orig", phone = "111")
        manager.deleteLocal(SOUP, "001A")
        val before = record("001A").toString()

        assertWriteFails { manager.deleteLocal(SOUP, "001A") }

        assertEquals(before, record("001A").toString())
        assertEquals(1, count(SyncQueue.QUEUE_SOUP))
    }

    @Test
    fun test_givenMissingRecord_whenDeleteLocal_thenFailsWithNothingWritten() {
        assertWriteFails { manager.deleteLocal(SOUP, "001Z") }
        assertNothingWritten()
    }

    // endregion

    // region ordering

    @Test
    fun test_givenWritesToAThenBThenA_whenQueryingBySequence_thenEntriesAreInWriteOrder() {
        insertCleanRecord("A", name = "a", phone = "1")
        insertCleanRecord("B", name = "b", phone = "2")

        manager.updateLocal(SOUP, "A", JSONObject().put("Phone", "10"))
        manager.updateLocal(SOUP, "B", JSONObject().put("Phone", "20"))
        manager.updateLocal(SOUP, "A", JSONObject().put("Name", "a2"))

        val entries = queueEntries()
        assertEquals(3, entries.length())
        assertEquals(listOf(1L, 2L, 3L), (0 until 3).map { entries.getJSONObject(it).getLong(SyncQueue.SEQUENCE) })
        assertEquals(listOf("A", "B", "A"), (0 until 3).map { entries.getJSONObject(it).getString(SyncQueue.RECORD_ID) })
    }

    @Test
    fun test_givenMoreThanTenWrites_whenQueryingBySequence_thenOrderIsNumeric() {
        insertCleanRecord("A", name = "a", phone = "0")

        (1..12).forEach { manager.updateLocal(SOUP, "A", JSONObject().put("Phone", "$it")) }

        val entries = queueEntries()
        assertEquals(12, entries.length())
        assertEquals((1L..12L).toList(), (0 until 12).map { entries.getJSONObject(it).getLong(SyncQueue.SEQUENCE) })
        assertEquals(
            (1..12).map { "$it" },
            (0 until 12).map { entries.getJSONObject(it).getJSONObject(SyncQueue.CHANGES).getString("Phone") }
        )
    }

    // endregion

    // region atomicity

    @Test
    fun test_givenQueueInsertFailing_whenCreateLocal_thenNeitherRecordNorEntryIsWritten() {
        withFailingQueueInserts {
            assertWriteFails { manager.createLocal(SOUP, account("local_1")) }
        }

        assertEquals(0, count(SOUP))
        assertEquals(0, count(SyncQueue.QUEUE_SOUP))
        assertEquals(1L, SyncQueue.nextSequence(store))
    }

    @Test
    fun test_givenQueueInsertFailing_whenUpdateLocal_thenRecordIsUnchangedAndNoEntry() {
        insertCleanRecord("001A", name = "orig", phone = "111")
        val before = record("001A").toString()

        withFailingQueueInserts {
            assertWriteFails { manager.updateLocal(SOUP, "001A", JSONObject().put("Phone", "222")) }
        }

        assertEquals(before, record("001A").toString())
        assertEquals(0, count(SyncQueue.QUEUE_SOUP))
    }

    @Test
    fun test_givenQueueInsertFailing_whenDeleteLocal_thenRecordIsUnchangedAndNoEntry() {
        insertCleanRecord("001A", name = "orig", phone = "111")
        val before = record("001A").toString()

        withFailingQueueInserts {
            assertWriteFails { manager.deleteLocal(SOUP, "001A") }
        }

        assertEquals(before, record("001A").toString())
        assertEquals(0, count(SyncQueue.QUEUE_SOUP))
    }

    @Test
    fun test_givenRolledBackWrite_whenWritingAgain_thenSequenceIsNotConsumed() {
        withFailingQueueInserts {
            assertWriteFails { manager.createLocal(SOUP, account("local_1")) }
        }

        manager.createLocal(SOUP, account("local_1"))

        assertEquals(1L, singleEntry().getLong(SyncQueue.SEQUENCE))
    }

    // endregion

    private fun withFailingQueueInserts(block: () -> Unit) {
        val db = store.database
        val table = DBHelper.getInstance(db).getSoupTableName(db, SyncQueue.QUEUE_SOUP)
        db.execSQL("CREATE TRIGGER fail_queue_insert BEFORE INSERT ON $table BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        try {
            block()
        } finally {
            db.execSQL("DROP TRIGGER IF EXISTS fail_queue_insert")
        }
    }

    private fun assertWriteFails(write: () -> Unit) {
        try {
            write()
            fail("Expected MobileSyncException")
        } catch (e: MobileSyncException) {
            assertNotNull(e.message)
        }
    }

    private fun assertNothingWritten() {
        assertEquals(0, count(SOUP))
        assertEquals(0, count(SyncQueue.QUEUE_SOUP))
    }

    private fun checkFlags(record: JSONObject, local: Boolean, created: Boolean, updated: Boolean, deleted: Boolean) {
        assertEquals(local, record.getBoolean(SyncTarget.LOCAL))
        assertEquals(created, record.getBoolean(SyncTarget.LOCALLY_CREATED))
        assertEquals(updated, record.getBoolean(SyncTarget.LOCALLY_UPDATED))
        assertEquals(deleted, record.getBoolean(SyncTarget.LOCALLY_DELETED))
    }

    private fun record(id: String): JSONObject {
        val rows = store.query(QuerySpec.buildExactQuerySpec(SOUP, "Id", id, null, null, 10), 0)
        assertEquals(1, rows.length())
        return rows.getJSONObject(0)
    }

    private fun count(soupName: String) =
        store.countQuery(QuerySpec.buildAllQuerySpec(soupName, null, null, 100))

    private fun queueEntries(): JSONArray = store.query(
        QuerySpec.buildAllQuerySpec(SyncQueue.QUEUE_SOUP, SyncQueue.SEQUENCE, QuerySpec.Order.ascending, 100),
        0
    )

    private fun singleEntry(): JSONObject {
        val entries = queueEntries()
        assertEquals(1, entries.length())
        return entries.getJSONObject(0)
    }

    private fun insertCleanRecord(id: String, name: String, phone: String) {
        store.create(
            SOUP,
            account(id).put("Name", name).put("Phone", phone)
                .put(SyncTarget.LOCAL, false)
                .put(SyncTarget.LOCALLY_CREATED, false)
                .put(SyncTarget.LOCALLY_UPDATED, false)
                .put(SyncTarget.LOCALLY_DELETED, false)
        )
    }

    private fun registerRecordSoup(name: String) {
        store.registerSoup(
            name,
            arrayOf(
                IndexSpec("Id", SmartStore.Type.string),
                IndexSpec("Name", SmartStore.Type.string),
                IndexSpec(SyncTarget.LOCAL, SmartStore.Type.string),
                IndexSpec(SyncTarget.LOCALLY_CREATED, SmartStore.Type.string),
                IndexSpec(SyncTarget.LOCALLY_UPDATED, SmartStore.Type.string),
                IndexSpec(SyncTarget.LOCALLY_DELETED, SmartStore.Type.string),
            )
        )
    }

    private fun account(id: String?) = JSONObject()
        .put("Name", "name-$id")
        .put("attributes", JSONObject().put("type", "Account"))
        .also { if (id != null) it.put("Id", id) }

    companion object {
        private const val DB_NAME = "localWriteTestDb"
        private const val SOUP = "accounts"
        private const val SOUP_ID_ONLY = "idOnlyAccounts"
    }
}
