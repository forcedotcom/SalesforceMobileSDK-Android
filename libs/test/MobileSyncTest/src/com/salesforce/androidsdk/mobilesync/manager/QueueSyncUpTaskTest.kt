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
package com.salesforce.androidsdk.mobilesync.manager

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager
import com.salesforce.androidsdk.mobilesync.target.QueueSyncUpTarget
import com.salesforce.androidsdk.mobilesync.target.SyncTarget
import com.salesforce.androidsdk.mobilesync.target.SyncUpTarget
import com.salesforce.androidsdk.mobilesync.util.SyncOptions
import com.salesforce.androidsdk.mobilesync.util.SyncQueue
import com.salesforce.androidsdk.mobilesync.util.SyncState
import com.salesforce.androidsdk.mobilesync.util.SyncUpdateCallbackQueue
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestResponse
import com.salesforce.androidsdk.smartstore.store.IndexSpec
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import com.salesforce.androidsdk.smartstore.store.SmartStore
import okhttp3.Interceptor
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Tests of [QueueSyncUpTarget] and [QueueSyncUpTask] (strict-order replay and temp-id remapping).
 * Network-free: requests go to a fake rest client, records live in a global store.
 */
@RunWith(AndroidJUnit4::class)
class QueueSyncUpTaskTest {

    private lateinit var store: SmartStore
    private lateinit var manager: SyncManager
    private lateinit var client: FakeRestClient

    @Before
    fun setUp() {
        SyncManager.reset()
        MobileSyncSDKManager.getInstance().removeGlobalSmartStore(DB_NAME)
        store = MobileSyncSDKManager.getInstance().getGlobalSmartStore(DB_NAME)
        manager = SyncManager.getInstance(null, null, store)
        client = FakeRestClient()
        manager.restClient = client
        registerRecordSoup(ACCOUNTS)
        registerRecordSoup(CONTACTS)
    }

    @After
    fun tearDown() {
        SyncManager.reset()
        MobileSyncSDKManager.getInstance().removeGlobalSmartStore(DB_NAME)
    }

    // region Replay order and requests (acceptance criteria 1 and 2)

    @Test
    fun test_givenEntriesInSeveralSoups_whenSyncUp_thenReplayedInSequenceOrder() {
        insertCleanRecord(ACCOUNTS, "001A", "Account")
        insertCleanRecord(ACCOUNTS, "001B", "Account")
        insertCleanRecord(CONTACTS, "003C", "Contact")
        manager.updateLocal(ACCOUNTS, "001A", JSONObject().put("Name", "a1"))
        manager.updateLocal(CONTACTS, "003C", JSONObject().put("LastName", "c1"))
        manager.updateLocal(ACCOUNTS, "001B", JSONObject().put("Name", "b1"))
        manager.updateLocal(ACCOUNTS, "001A", JSONObject().put("Phone", "a2"))

        val sync = runQueueSync()

        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals(4, sync.totalSize)
        assertEquals(
            listOf("PATCH Account/001A", "PATCH Contact/003C", "PATCH Account/001B", "PATCH Account/001A"),
            client.requestLines()
        )
        assertEquals(0, queueEntries().length())
    }

    @Test
    fun test_givenCreateUpdateDelete_whenSyncUp_thenRequestsBuiltFromEntries() {
        insertCleanRecord(ACCOUNTS, "001U", "Account", mapOf("Phone" to "old", "Description" to "keep"))
        insertCleanRecord(ACCOUNTS, "001D", "Account")
        manager.createLocal(ACCOUNTS, account("local_1").put("Phone", "555"))
        manager.updateLocal(ACCOUNTS, "001U", JSONObject().put("Phone", "new"))
        manager.deleteLocal(ACCOUNTS, "001D")

        runQueueSync()

        assertEquals(listOf("POST Account", "PATCH Account/001U", "DELETE Account/001D"), client.requestLines())
        val create = client.requests[0].getRequestBodyAsJson()
        assertEquals(setOf("Name", "Phone"), create.keys().asSequence().toSet())
        assertEquals("555", create.getString("Phone"))
        val update = client.requests[1].getRequestBodyAsJson()
        assertEquals(setOf("Phone"), update.keys().asSequence().toSet())
        assertEquals("new", update.getString("Phone"))
        assertNull(client.requests[2].requestBody)
    }

    @Test
    fun test_givenExternalIdFieldName_whenCreateSyncedUp_thenUpsertIsUsed() {
        manager.createLocal(ACCOUNTS, account("local_1").put("ExtId__c", "ext-1"))
        manager.createLocal(ACCOUNTS, account("local_2"))

        runQueueSync(QueueSyncUpTarget("ExtId__c"))

        assertEquals(2, client.requests.size)
        assertEquals(RestRequest.RestMethod.PATCH, client.requests[0].method)
        assertTrue(client.requests[0].path.endsWith("/sobjects/Account/ExtId__c/ext-1"))
        assertEquals(RestRequest.RestMethod.POST, client.requests[1].method)
        assertTrue(client.requests[1].path.endsWith("/sobjects/Account"))
    }

    @Test
    fun test_givenUpsertAnswering201_whenSyncUp_thenIdComesFromBody() {
        manager.createLocal(ACCOUNTS, account("local_1").put("ExtId__c", "ext-1"))
        client.nextIds.add("001SERVER")

        val sync = runQueueSync(QueueSyncUpTarget("ExtId__c"))

        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals(1, client.requests.size)
        assertNotNull(findRecord(ACCOUNTS, "001SERVER"))
    }

    @Test
    fun test_givenUpsertAnswering204_whenSyncUp_thenIdIsLookedUpAndRecordRemapped() {
        manager.createLocal(ACCOUNTS, account("local_1").put("ExtId__c", "ext 1"))
        manager.createLocal(CONTACTS, contact("local_c").put("AccountId", "local_1"))
        client.responder = {
            when {
                it.method == RestRequest.RestMethod.PATCH -> Reply(204, "")
                it.method == RestRequest.RestMethod.GET -> Reply(200, """{"Id":"001EXIST","attributes":{}}""")
                else -> null
            }
        }

        val sync = runQueueSync(QueueSyncUpTarget("ExtId__c"))

        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals(3, client.requests.size)
        val lookup = client.requests[1]
        assertEquals(RestRequest.RestMethod.GET, lookup.method)
        assertTrue(lookup.path.contains("/sobjects/Account/ExtId__c/ext%201"))
        assertTrue(lookup.path.endsWith("?fields=Id"))
        assertEquals("001EXIST", client.requests[2].getRequestBodyAsJson().getString("AccountId"))
        assertNull(findRecord(ACCOUNTS, "local_1"))
        checkFlags(findRecord(ACCOUNTS, "001EXIST")!!, local = false, created = false, updated = false, deleted = false)
        assertEquals(0, queueEntries().length())
    }

    @Test
    fun test_givenUpsertAnswering204AndRecordLevelLookupFailure_whenSyncUp_thenRecordLevelError() {
        manager.createLocal(ACCOUNTS, account("local_1").put("ExtId__c", "ext-1"))
        client.responder = {
            if (it.method == RestRequest.RestMethod.PATCH) Reply(204, "") else Reply(404, "not found")
        }

        val sync = runQueueSync(QueueSyncUpTarget("ExtId__c"))

        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals(1, queueEntries().length())
        assertEquals(1, queueEntries().getJSONObject(0).getInt(SyncQueue.ATTEMPTS))
        assertNotNull(findRecord(ACCOUNTS, "local_1"))
    }

    @Test
    fun test_givenUpsertAnswering204AndTransportLookupFailure_whenSyncUp_thenSyncFails() {
        manager.createLocal(ACCOUNTS, account("local_1").put("ExtId__c", "ext-1"))
        client.responder = {
            if (it.method == RestRequest.RestMethod.PATCH) Reply(204, "") else throw IOException("offline")
        }

        val sync = runQueueSync(QueueSyncUpTarget("ExtId__c"))

        assertEquals(SyncState.Status.FAILED, sync.status)
        assertEquals(1, queueEntries().length())
        assertNotNull(findRecord(ACCOUNTS, "local_1"))
    }

    @Test
    fun test_givenUpsertAnswering300_whenSyncUp_thenRecordLevelError() {
        manager.createLocal(ACCOUNTS, account("local_1").put("ExtId__c", "ext-1"))
        client.responder = { Reply(300, """["001A","001B"]""") }

        val sync = runQueueSync(QueueSyncUpTarget("ExtId__c"))

        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals(1, client.requests.size)
        assertEquals("""["001A","001B"]""", queueEntries().getJSONObject(0).getString(SyncQueue.LAST_ERROR))
    }

    @Test
    fun test_givenFieldlistInOptions_whenSyncUp_thenIgnored() {
        insertCleanRecord(ACCOUNTS, "001U", "Account", mapOf("Phone" to "old"))
        manager.updateLocal(ACCOUNTS, "001U", JSONObject().put("Phone", "new"))

        runQueueSync(options = SyncOptions.optionsForSyncUp(listOf("Name", "Description")))

        assertEquals(setOf("Phone"), client.requests[0].getRequestBodyAsJson().keys().asSequence().toSet())
    }

    // endregion

    // region Success handling (acceptance criterion 3)

    @Test
    fun test_givenCreate_whenSyncUp_thenRecordHasServerIdAndIsCleanAndEntryRemoved() {
        manager.createLocal(ACCOUNTS, account("local_1"))
        client.nextIds.add("001SERVER1")

        runQueueSync()

        assertEquals(1, count(ACCOUNTS))
        assertNull(findRecord(ACCOUNTS, "local_1"))
        val record = findRecord(ACCOUNTS, "001SERVER1")!!
        checkFlags(record, local = false, created = false, updated = false, deleted = false)
        assertFalse(record.has(SyncTarget.LAST_ERROR))
        assertEquals(0, queueEntries().length())
    }

    @Test
    fun test_givenUpdateAndDelete_whenSyncUp_thenRecordCleanedAndRemoved() {
        insertCleanRecord(ACCOUNTS, "001U", "Account")
        insertCleanRecord(ACCOUNTS, "001D", "Account")
        manager.updateLocal(ACCOUNTS, "001U", JSONObject().put("Name", "new"))
        manager.deleteLocal(ACCOUNTS, "001D")

        runQueueSync()

        val updated = findRecord(ACCOUNTS, "001U")!!
        assertEquals("new", updated.getString("Name"))
        checkFlags(updated, local = false, created = false, updated = false, deleted = false)
        assertNull(findRecord(ACCOUNTS, "001D"))
        assertEquals(0, queueEntries().length())
    }

    @Test
    fun test_givenDeleteReturning404_whenSyncUp_thenSuccess() {
        insertCleanRecord(ACCOUNTS, "001D", "Account")
        manager.deleteLocal(ACCOUNTS, "001D")
        client.responder = { Reply(404, """[{"errorCode":"ENTITY_IS_DELETED"}]""") }

        val sync = runQueueSync()

        assertEquals(SyncState.Status.DONE, sync.status)
        assertNull(findRecord(ACCOUNTS, "001D"))
        assertEquals(0, queueEntries().length())
    }

    @Test
    fun test_givenDeleteOfUnsentRecord_whenSyncUp_thenNoServerCallForTheDelete() {
        manager.createLocal(ACCOUNTS, account("local_1"))
        manager.deleteLocal(ACCOUNTS, "local_1")
        client.responder = { Reply(400, "bad") }

        val sync = runQueueSync()

        // The CREATE is rejected; the DELETE of a record that never reached the server needs no call
        // and takes the failed CREATE with it
        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals(listOf("POST Account"), client.requestLines())
        assertNull(findRecord(ACCOUNTS, "local_1"))
        assertEquals(0, queueEntries().length())
    }

    @Test
    fun test_givenUpdateWithLaterEntryForSameRecord_whenSyncUp_thenRecordStaysDirtyUntilLast() {
        insertCleanRecord(ACCOUNTS, "001U", "Account")
        manager.updateLocal(ACCOUNTS, "001U", JSONObject().put("Name", "n1"))
        manager.updateLocal(ACCOUNTS, "001U", JSONObject().put("Phone", "p2"))
        var dirtyWhenSendingSecond: Boolean? = null
        client.onRequest = {
            if (client.requests.size == 2) {
                dirtyWhenSendingSecond = findRecord(ACCOUNTS, "001U")!!.getBoolean(SyncTarget.LOCALLY_UPDATED)
            }
        }

        runQueueSync()

        // The first entry was replayed already, yet the record is still dirty for the second one
        assertEquals(true, dirtyWhenSendingSecond)
        checkFlags(findRecord(ACCOUNTS, "001U")!!, local = false, created = false, updated = false, deleted = false)
    }

    // endregion

    // region Errors (acceptance criterion 4)

    @Test
    fun test_givenRecordLevelError_whenSyncUp_thenSyncCompletesAndNextEntriesRun() {
        insertCleanRecord(ACCOUNTS, "001A", "Account")
        insertCleanRecord(ACCOUNTS, "001B", "Account")
        manager.updateLocal(ACCOUNTS, "001A", JSONObject().put("Name", "a1"))
        manager.updateLocal(ACCOUNTS, "001B", JSONObject().put("Name", "b1"))
        val body = """[{"message":"bad value","errorCode":"INVALID_FIELD"}]"""
        client.responder = { if (it.path.endsWith("001A")) Reply(400, body) else null }

        val sync = runQueueSync()

        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals(listOf("PATCH Account/001A", "PATCH Account/001B"), client.requestLines())
        val entries = queueEntries()
        assertEquals(1, entries.length())
        val entry = entries.getJSONObject(0)
        assertEquals("001A", entry.getString(SyncQueue.RECORD_ID))
        assertEquals(body, entry.getString(SyncQueue.LAST_ERROR))
        assertEquals(1, entry.getInt(SyncQueue.ATTEMPTS))
        assertEquals("PENDING", entry.getString(SyncQueue.STATUS))
        assertEquals(body, findRecord(ACCOUNTS, "001A")!!.getString(SyncTarget.LAST_ERROR))
        assertTrue(findRecord(ACCOUNTS, "001A")!!.getBoolean(SyncTarget.LOCALLY_UPDATED))
        checkFlags(findRecord(ACCOUNTS, "001B")!!, local = false, created = false, updated = false, deleted = false)
    }

    @Test
    fun test_givenRecordLevelErrorOnRetry_whenSyncUpAgain_thenAttemptsGrow() {
        insertCleanRecord(ACCOUNTS, "001A", "Account")
        manager.updateLocal(ACCOUNTS, "001A", JSONObject().put("Name", "a1"))
        client.responder = { Reply(400, "boom") }

        val sync = runQueueSync()
        runResync(sync)

        assertEquals(2, queueEntries().getJSONObject(0).getInt(SyncQueue.ATTEMPTS))
    }

    @Test
    fun test_givenMissingRecord_whenSyncUp_thenRecordLevelErrorAndNextEntriesRun() {
        insertCleanRecord(ACCOUNTS, "001A", "Account")
        insertCleanRecord(ACCOUNTS, "001B", "Account")
        manager.updateLocal(ACCOUNTS, "001A", JSONObject().put("Name", "a1"))
        manager.updateLocal(ACCOUNTS, "001B", JSONObject().put("Name", "b1"))
        store.delete(ACCOUNTS, store.lookupSoupEntryId(ACCOUNTS, "Id", "001A"))

        val sync = runQueueSync()

        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals(listOf("PATCH Account/001B"), client.requestLines())
        val entry = queueEntries().getJSONObject(0)
        assertEquals("001A", entry.getString(SyncQueue.RECORD_ID))
        assertEquals(1, entry.getInt(SyncQueue.ATTEMPTS))
        assertTrue(entry.getString(SyncQueue.LAST_ERROR).contains("001A"))
    }

    @Test
    fun test_givenEntryWithoutSobjectType_whenSyncUp_thenRecordLevelError() {
        insertCleanRecord(ACCOUNTS, "001A", "Account")
        manager.updateLocal(ACCOUNTS, "001A", JSONObject().put("Name", "a1"))
        val entry = queueEntries().getJSONObject(0)
        entry.remove(SyncQueue.SOBJECT_TYPE)
        store.update(SyncQueue.QUEUE_SOUP, entry, entry.getLong(SmartStore.SOUP_ENTRY_ID))

        val sync = runQueueSync()

        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals(0, client.requests.size)
        assertEquals(1, queueEntries().getJSONObject(0).getInt(SyncQueue.ATTEMPTS))
    }

    @Test
    fun test_givenTransportError_whenSyncUp_thenSyncFailsEntryStaysAtHeadAndReSyncResumes() {
        insertCleanRecord(ACCOUNTS, "001A", "Account")
        insertCleanRecord(ACCOUNTS, "001B", "Account")
        insertCleanRecord(ACCOUNTS, "001C", "Account")
        manager.updateLocal(ACCOUNTS, "001A", JSONObject().put("Name", "a1"))
        manager.updateLocal(ACCOUNTS, "001B", JSONObject().put("Name", "b1"))
        manager.updateLocal(ACCOUNTS, "001C", JSONObject().put("Name", "c1"))
        var failB = true
        client.responder = {
            if (it.path.endsWith("001B") && failB) throw IOException("offline") else null
        }

        val sync = runQueueSync()

        assertEquals(SyncState.Status.FAILED, sync.status)
        assertNotNull(sync.error)
        assertEquals(listOf("PATCH Account/001A", "PATCH Account/001B"), client.requestLines())
        val entries = queueEntries()
        assertEquals(2, entries.length())
        assertEquals("001B", entries.getJSONObject(0).getString(SyncQueue.RECORD_ID))
        assertEquals(1, entries.getJSONObject(0).getInt(SyncQueue.ATTEMPTS))
        assertEquals("001C", entries.getJSONObject(1).getString(SyncQueue.RECORD_ID))

        failB = false
        client.requests.clear()
        val resync = runResync(sync)

        assertEquals(SyncState.Status.DONE, resync.status)
        assertEquals(2, resync.totalSize)
        assertEquals(listOf("PATCH Account/001B", "PATCH Account/001C"), client.requestLines())
        assertEquals(0, queueEntries().length())
    }

    @Test
    fun test_given5xxOrRequestLimit_whenSyncUp_thenSyncFailsAndLaterEntriesAreNotSent() {
        for (reply in listOf(Reply(503, "unavailable"), Reply(403, """[{"errorCode":"REQUEST_LIMIT_EXCEEDED"}]"""))) {
            setUp()
            insertCleanRecord(ACCOUNTS, "001A", "Account")
            insertCleanRecord(ACCOUNTS, "001B", "Account")
            manager.updateLocal(ACCOUNTS, "001A", JSONObject().put("Name", "a1"))
            manager.updateLocal(ACCOUNTS, "001B", JSONObject().put("Name", "b1"))
            client.responder = { reply }

            val sync = runQueueSync()

            assertEquals(SyncState.Status.FAILED, sync.status)
            assertEquals(1, client.requests.size)
            assertEquals(2, queueEntries().length())
            assertEquals(1, queueEntries().getJSONObject(0).getInt(SyncQueue.ATTEMPTS))
        }
    }

    @Test
    fun test_givenTransportErrorOnCreate_whenSyncUp_thenRecordKeepsLocalIdAndIsNotCleaned() {
        manager.createLocal(ACCOUNTS, account("local_1"))
        client.responder = { throw IOException("offline") }

        val sync = runQueueSync()

        assertEquals(SyncState.Status.FAILED, sync.status)
        checkFlags(findRecord(ACCOUNTS, "local_1")!!, local = true, created = true, updated = false, deleted = false)
        assertEquals(1, queueEntries().length())
    }

    // endregion

    // region Opt-in (acceptance criterion 5)

    @Test
    fun test_givenFlaggedRecordWithoutEntry_whenQueueSyncUp_thenNothingIsSentOrTouched() {
        insertCleanRecord(ACCOUNTS, "001F", "Account")
        val flagged = findRecord(ACCOUNTS, "001F")!!
        flagged.put(SyncTarget.LOCAL, true).put(SyncTarget.LOCALLY_UPDATED, true)
        store.update(ACCOUNTS, flagged, flagged.getLong(SmartStore.SOUP_ENTRY_ID))
        val before = findRecord(ACCOUNTS, "001F")!!.toString()

        val sync = runQueueSync()

        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals(0, sync.totalSize)
        assertEquals(0, client.requests.size)
        assertEquals(before, findRecord(ACCOUNTS, "001F")!!.toString())
    }

    @Test
    fun test_givenFlagBasedTargetAndQueue_whenSyncUp_thenBehaviorUnchanged() {
        insertCleanRecord(ACCOUNTS, "001F", "Account")
        val flagged = findRecord(ACCOUNTS, "001F")!!
        flagged.put(SyncTarget.LOCAL, true).put(SyncTarget.LOCALLY_UPDATED, true).put("Name", "flagged")
        store.update(ACCOUNTS, flagged, flagged.getLong(SmartStore.SOUP_ENTRY_ID))
        insertCleanRecord(ACCOUNTS, "001Q", "Account")
        manager.updateLocal(ACCOUNTS, "001Q", JSONObject().put("Name", "queued"))

        val sync = runSyncUp(SyncUpTarget(), SyncOptions.optionsForSyncUp(listOf("Name")), ACCOUNTS)

        // Flag-based: both dirty records are sent, one request each; the queue is not read
        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals(listOf("PATCH Account/001F", "PATCH Account/001Q"), client.requestLines().sorted())
        assertEquals(1, queueEntries().length())
        checkFlags(findRecord(ACCOUNTS, "001F")!!, local = false, created = false, updated = false, deleted = false)
    }

    // endregion

    // region Names and reSync (acceptance criterion 6)

    @Test
    fun test_givenNamedQueueSync_whenReSyncById_thenTargetIsRebuiltAndReplays() {
        insertCleanRecord(ACCOUNTS, "001A", "Account")
        manager.updateLocal(ACCOUNTS, "001A", JSONObject().put("Name", "a1"))

        val sync = runQueueSync(name = "queueSync")
        assertTrue(manager.hasSyncWithName("queueSync"))
        assertTrue(manager.getSyncStatus(sync.id)!!.target is QueueSyncUpTarget)
        assertEquals(SyncQueue.QUEUE_SOUP, manager.getSyncStatus(sync.id)!!.soupName)

        manager.updateLocal(ACCOUNTS, "001A", JSONObject().put("Name", "a2"))
        client.requests.clear()
        val resync = runResync(sync)

        assertEquals(SyncState.Status.DONE, resync.status)
        assertEquals(listOf("PATCH Account/001A"), client.requestLines())
        assertEquals(0, queueEntries().length())
    }

    @Test
    fun test_givenNamedQueueSync_whenReSyncByName_thenReplays() {
        insertCleanRecord(ACCOUNTS, "001A", "Account")
        runQueueSync(name = "queueSync")
        manager.updateLocal(ACCOUNTS, "001A", JSONObject().put("Name", "a2"))
        client.requests.clear()

        val id = manager.getSyncStatus("queueSync")!!.id
        val callbacks = SyncUpdateCallbackQueue(id)
        manager.reSync("queueSync", callbacks)
        val state = waitTerminal(callbacks, id)

        assertEquals(SyncState.Status.DONE, state.status)
        assertEquals(listOf("PATCH Account/001A"), client.requestLines())
    }

    @Test
    fun test_givenQueueTarget_whenAsJSONAndBack_thenSameClassAndExternalId() {
        val json = QueueSyncUpTarget("ExtId__c").asJSON()

        val rebuilt = SyncUpTarget.fromJSON(json)

        assertTrue(rebuilt is QueueSyncUpTarget)
        assertEquals("ExtId__c", rebuilt.externalIdFieldName)
    }

    // endregion

    // region Temp-id remapping (acceptance criterion 7)

    @Test
    fun test_givenCreateThenEntriesReferencingLocalId_whenSyncUp_thenIdsAreRemapped() {
        manager.createLocal(ACCOUNTS, account("local_a"))
        manager.createLocal(CONTACTS, contact("local_c").put("AccountId", "local_a"))
        manager.updateLocal(CONTACTS, "local_c", JSONObject().put("Title", "boss"))
        manager.updateLocal(ACCOUNTS, "local_a", JSONObject().put("Phone", "555"))
        client.nextIds.addAll(listOf("001SA", "003SC"))

        val sync = runQueueSync()

        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals(
            listOf("POST Account", "POST Contact", "PATCH Contact/003SC", "PATCH Account/001SA"),
            client.requestLines()
        )
        assertEquals("001SA", client.requests[1].getRequestBodyAsJson().getString("AccountId"))
        assertEquals(1, count(ACCOUNTS))
        assertEquals(1, count(CONTACTS))
        val account = findRecord(ACCOUNTS, "001SA")!!
        checkFlags(account, local = false, created = false, updated = false, deleted = false)
        assertEquals("555", account.getString("Phone"))
        assertEquals(0, queueEntries().length())
    }

    @Test
    fun test_givenCreateThenUpdate_whenSyncUp_thenDirtyAfterCreateCleanAfterUpdate() {
        manager.createLocal(ACCOUNTS, account("local_1"))
        manager.updateLocal(ACCOUNTS, "local_1", JSONObject().put("Phone", "1"))
        client.nextIds.add("001S")
        var duringUpdate: JSONObject? = null
        client.onRequest = { if (client.requests.size == 2) duringUpdate = findRecord(ACCOUNTS, "001S") }

        runQueueSync()

        checkFlags(duringUpdate!!, local = true, created = false, updated = true, deleted = false)
        checkFlags(findRecord(ACCOUNTS, "001S")!!, local = false, created = false, updated = false, deleted = false)
    }

    @Test
    fun test_givenCreateThenDelete_whenSyncUp_thenStaysDeletedFlaggedUntilDeleteReplays() {
        manager.createLocal(ACCOUNTS, account("local_1"))
        manager.deleteLocal(ACCOUNTS, "local_1")
        client.nextIds.add("001S")
        var duringDelete: JSONObject? = null
        client.onRequest = { if (client.requests.size == 2) duringDelete = findRecord(ACCOUNTS, "001S") }

        runQueueSync()

        checkFlags(duringDelete!!, local = true, created = false, updated = false, deleted = true)
        assertEquals(listOf("POST Account", "DELETE Account/001S"), client.requestLines())
        assertNull(findRecord(ACCOUNTS, "001S"))
    }

    @Test
    fun test_givenCreateAndTwoUpdates_whenSyncUp_thenDirtyUntilLastEntry() {
        manager.createLocal(ACCOUNTS, account("local_1"))
        manager.updateLocal(ACCOUNTS, "local_1", JSONObject().put("Phone", "1"))
        manager.updateLocal(ACCOUNTS, "local_1", JSONObject().put("Phone", "2"))
        client.nextIds.add("001S")
        val seen = mutableListOf<JSONObject>()
        client.onRequest = { if (client.requests.size > 1) seen.add(findRecord(ACCOUNTS, "001S")!!) }

        runQueueSync()

        assertEquals(2, seen.size)
        seen.forEach { checkFlags(it, local = true, created = false, updated = true, deleted = false) }
        checkFlags(findRecord(ACCOUNTS, "001S")!!, local = false, created = false, updated = false, deleted = false)
    }

    @Test
    fun test_givenCreateSucceeds_whenEntriesRewritten_thenPendingEntriesInAnySoupGetServerId() {
        manager.createLocal(ACCOUNTS, account("local_a"))
        manager.createLocal(CONTACTS, contact("local_c").put("AccountId", "local_a").put("Other", "local_zzz"))
        manager.updateLocal(ACCOUNTS, "local_a", JSONObject().put("Phone", "555"))
        manager.deleteLocal(ACCOUNTS, "local_a")
        var seen: List<JSONObject>? = null
        client.nextIds.add("001SA")
        // Stop the sync at its second request: the CREATE of the account was replayed and rewrote the queue
        client.onRequest = {
            if (client.requests.size == 2) {
                seen = (0 until queueEntries().length()).map { queueEntries().getJSONObject(it) }
            }
        }
        client.responder = { if (client.requests.size > 1) throw IOException("stop") else null }

        runQueueSync()

        val entries = seen!!
        assertEquals(3, entries.size)
        val contactEntry = entries[0]
        assertEquals("local_c", contactEntry.getString(SyncQueue.RECORD_ID))
        assertEquals("001SA", contactEntry.getJSONObject(SyncQueue.CHANGES).getString("AccountId"))
        // Only values that are exactly a known local id are replaced
        assertEquals("local_zzz", contactEntry.getJSONObject(SyncQueue.CHANGES).getString("Other"))
        assertEquals("001SA", entries[1].getString(SyncQueue.RECORD_ID))
        assertEquals("001SA", entries[2].getString(SyncQueue.RECORD_ID))
        assertEquals("UPDATE", entries[1].getString(SyncQueue.KIND))
        assertEquals("DELETE", entries[2].getString(SyncQueue.KIND))
    }

    @Test
    fun test_givenLocalIdWithSpecialCharacters_whenSyncUp_thenIdsAreRemapped() {
        val localId = "local_a/b'c\"d"
        manager.createLocal(ACCOUNTS, account(localId))
        manager.createLocal(CONTACTS, contact("local_c").put("AccountId", localId))
        manager.updateLocal(ACCOUNTS, localId, JSONObject().put("Phone", "1"))
        client.nextIds.addAll(listOf("001SA", "003SC"))

        val sync = runQueueSync()

        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals("001SA", client.requests[1].getRequestBodyAsJson().getString("AccountId"))
        assertEquals("PATCH Account/001SA", client.requestLines()[2])
    }

    @Test
    fun test_givenRecordRewritten_whenCreateSucceeds_thenSameSoupEntryIsUpdatedNoSecondRow() {
        manager.createLocal(ACCOUNTS, account("local_a"))
        manager.updateLocal(ACCOUNTS, "local_a", JSONObject().put("Phone", "1"))
        val soupEntryId = findRecord(ACCOUNTS, "local_a")!!.getLong(SmartStore.SOUP_ENTRY_ID)
        client.nextIds.add("001SA")

        runQueueSync()

        assertEquals(1, count(ACCOUNTS))
        assertEquals(soupEntryId, findRecord(ACCOUNTS, "001SA")!!.getLong(SmartStore.SOUP_ENTRY_ID))
    }

    @Test
    fun test_givenCreateFails_whenLaterEntryReferencesLocalId_thenSentAsIsAndFailsAtRecordLevel() {
        manager.createLocal(ACCOUNTS, account("local_a"))
        manager.createLocal(CONTACTS, contact("local_c").put("AccountId", "local_a"))
        client.responder = {
            if (it.path.endsWith("/Account")) Reply(400, "bad account") else Reply(400, "bad lookup")
        }

        val sync = runQueueSync()

        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals("local_a", client.requests[1].getRequestBodyAsJson().getString("AccountId"))
        assertEquals(2, queueEntries().length())
        assertNotNull(findRecord(ACCOUNTS, "local_a"))
    }

    @Test
    fun test_givenRecordRemovedByApp_whenCreateSucceeds_thenOnlyQueueEntriesAreRewritten() {
        manager.createLocal(ACCOUNTS, account("local_a"))
        manager.createLocal(CONTACTS, contact("local_c").put("AccountId", "local_a"))
        client.nextIds.addAll(listOf("001SA", "003SC"))
        client.onRequest = {
            if (client.requests.size == 1) {
                store.delete(ACCOUNTS, store.lookupSoupEntryId(ACCOUNTS, "Id", "local_a"))
            }
        }

        val sync = runQueueSync()

        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals(0, count(ACCOUNTS))
        assertEquals("001SA", client.requests[1].getRequestBodyAsJson().getString("AccountId"))
    }

    // endregion

    // region Review fixes

    @Test
    fun test_givenEarlierUpdateFailed_whenLaterUpdateSucceeds_thenStillDirtyWithErrorUntilEarlierSucceeds() {
        insertCleanRecord(ACCOUNTS, "001A", "Account")
        manager.updateLocal(ACCOUNTS, "001A", JSONObject().put("Name", "n1"))
        manager.updateLocal(ACCOUNTS, "001A", JSONObject().put("Phone", "p2"))
        client.responder = { if (client.requests.size == 1) Reply(400, "bad name") else null }

        val sync = runQueueSync()

        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals(1, queueEntries().length())
        val record = findRecord(ACCOUNTS, "001A")!!
        checkFlags(record, local = true, created = false, updated = true, deleted = false)
        assertEquals("bad name", record.getString(SyncTarget.LAST_ERROR))

        client.responder = null
        runResync(sync)

        assertEquals(0, queueEntries().length())
        val cleaned = findRecord(ACCOUNTS, "001A")!!
        checkFlags(cleaned, local = false, created = false, updated = false, deleted = false)
        assertFalse(cleaned.has(SyncTarget.LAST_ERROR))
    }

    @Test
    fun test_givenFailedCreateThenDeleteLocal_whenSyncUp_thenAllEntriesAndRecordAreGone() {
        manager.createLocal(ACCOUNTS, account("local_1"))
        client.responder = { Reply(400, "bad") }
        val sync = runQueueSync()
        assertEquals(1, queueEntries().length())
        manager.deleteLocal(ACCOUNTS, "local_1")
        assertEquals(2, queueEntries().length())

        runResync(sync)

        assertEquals(0, queueEntries().length())
        assertNull(findRecord(ACCOUNTS, "local_1"))
        assertEquals(0, count(ACCOUNTS))
        // Only the CREATE was sent again: the DELETE of a local id never reaches the server
        assertEquals(listOf("POST Account", "POST Account"), client.requestLines())
    }

    @Test
    fun test_givenExternalIdsWithSpecialCharacters_whenUpsert_thenPathSegmentIsEncoded() {
        val values = listOf("a/b", "a?b", "a%b", "a#b", "a b")
        values.forEachIndexed { i, v -> manager.createLocal(ACCOUNTS, account("local_$i").put("ExtId__c", v)) }

        val sync = runQueueSync(QueueSyncUpTarget("ExtId__c"))

        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals(
            listOf("a%2Fb", "a%3Fb", "a%25b", "a%23b", "a%20b"),
            client.requests.map { it.path.substringAfter("ExtId__c/") }
        )
    }

    @Test
    fun test_givenStatuses_whenSyncUp_thenClassifiedAsTransportOrRecordLevel() {
        val transport = listOf(
            Reply(401, "expired"), Reply(408, "timeout"), Reply(429, "slow down"), Reply(500, "boom"),
            Reply(403, """[{"errorCode":"REQUEST_LIMIT_EXCEEDED"}]""")
        )
        val recordLevel = listOf(
            Reply(400, "bad"), Reply(403, """[{"errorCode":"INSUFFICIENT_ACCESS"}]"""), Reply(404, "gone"),
            Reply(409, "conflict"), Reply(422, "invalid")
        )
        for (reply in transport + recordLevel) {
            setUp()
            insertCleanRecord(ACCOUNTS, "001A", "Account")
            insertCleanRecord(ACCOUNTS, "001B", "Account")
            manager.updateLocal(ACCOUNTS, "001A", JSONObject().put("Name", "a1"))
            manager.updateLocal(ACCOUNTS, "001B", JSONObject().put("Name", "b1"))
            client.responder = { if (it.path.endsWith("001A")) reply else null }

            val sync = runQueueSync()

            val message = "status ${reply.status} ${reply.body}"
            if (reply in transport) {
                assertEquals(message, SyncState.Status.FAILED, sync.status)
                assertEquals(message, 1, client.requests.size)
            } else {
                assertEquals(message, SyncState.Status.DONE, sync.status)
                assertEquals(message, 2, client.requests.size)
            }
        }
    }

    @Test
    fun test_givenSuccessWithoutUsableId_whenCreate_thenSyncFailsAndEntryKeepsErrorAndAttempt() {
        for (body in listOf("""{"success":true}""", "<html>not json</html>")) {
            setUp()
            manager.createLocal(ACCOUNTS, account("local_1"))
            client.responder = { Reply(201, body) }

            val sync = runQueueSync()

            assertEquals(body, SyncState.Status.FAILED, sync.status)
            val entry = queueEntries().getJSONObject(0)
            assertEquals(1, entry.getInt(SyncQueue.ATTEMPTS))
            assertFalse(entry.optString(SyncQueue.LAST_ERROR).isEmpty())
            assertNotNull(findRecord(ACCOUNTS, "local_1"))
        }
    }

    @Test
    fun test_givenTransportError_whenSyncUp_thenEntryKeepsLastError() {
        insertCleanRecord(ACCOUNTS, "001A", "Account")
        manager.updateLocal(ACCOUNTS, "001A", JSONObject().put("Name", "a1"))
        client.responder = { Reply(503, "unavailable") }
        runQueueSync()
        assertEquals("unavailable", queueEntries().getJSONObject(0).getString(SyncQueue.LAST_ERROR))

        client.responder = { throw IOException("offline") }
        runQueueSync()
        val entry = queueEntries().getJSONObject(0)
        assertEquals("offline", entry.getString(SyncQueue.LAST_ERROR))
        assertEquals(2, entry.getInt(SyncQueue.ATTEMPTS))
    }

    @Test
    fun test_givenServerRecordAlreadyInSoup_whenCreateSucceeds_thenDuplicateRowIsRemoved() {
        insertCleanRecord(ACCOUNTS, "001SA", "Account")
        manager.createLocal(ACCOUNTS, account("local_a"))
        val soupEntryId = findRecord(ACCOUNTS, "local_a")!!.getLong(SmartStore.SOUP_ENTRY_ID)
        client.nextIds.add("001SA")

        val sync = runQueueSync()

        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals(1, count(ACCOUNTS))
        assertEquals(soupEntryId, findRecord(ACCOUNTS, "001SA")!!.getLong(SmartStore.SOUP_ENTRY_ID))
    }

    @Test
    fun test_givenStopRequested_whenSyncUp_thenStoppedAndReSyncResumes() {
        insertCleanRecord(ACCOUNTS, "001A", "Account")
        insertCleanRecord(ACCOUNTS, "001B", "Account")
        manager.updateLocal(ACCOUNTS, "001A", JSONObject().put("Name", "a1"))
        manager.updateLocal(ACCOUNTS, "001B", JSONObject().put("Name", "b1"))
        client.onRequest = { if (client.requests.size == 1) manager.stop() }

        val sync = runQueueSync()

        assertEquals(SyncState.Status.STOPPED, sync.status)
        assertEquals(listOf("PATCH Account/001A"), client.requestLines())
        assertEquals(1, queueEntries().length())

        client.onRequest = null
        manager.restart(false, null)
        val resync = runResync(sync)

        assertEquals(SyncState.Status.DONE, resync.status)
        assertEquals(listOf("PATCH Account/001A", "PATCH Account/001B"), client.requestLines())
        assertEquals(0, queueEntries().length())
    }

    @Test
    fun test_givenLocalIdsThatArePrefixesOfEachOther_whenSyncUp_thenOnlyExactMatchesAreRemapped() {
        manager.createLocal(ACCOUNTS, account("local_1"))
        manager.createLocal(ACCOUNTS, account("local_10"))
        manager.createLocal(CONTACTS, contact("local_c").put("AccountId", "local_10").put("Other", "local_1"))
        client.nextIds.addAll(listOf("001ONE", "001TEN", "003C"))

        val sync = runQueueSync()

        assertEquals(SyncState.Status.DONE, sync.status)
        val body = client.requests[2].getRequestBodyAsJson()
        assertEquals("001TEN", body.getString("AccountId"))
        assertEquals("001ONE", body.getString("Other"))
    }

    @Test
    fun test_givenSeveralEntries_whenSyncUp_thenTotalSizeAndProgressAreReported() {
        for (i in 1..3) manager.createLocal(ACCOUNTS, account("local_$i"))
        val sync = manager.createSyncUp(QueueSyncUpTarget(), SyncOptions.optionsForSyncUp(emptyList()), SyncQueue.QUEUE_SOUP, null)
        val callbacks = SyncUpdateCallbackQueue(sync.id)
        manager.runSync(sync, callbacks)
        val updates = mutableListOf<SyncState>()
        do {
            updates.add(callbacks.getNextSyncUpdate(sync.id))
        } while (updates.last().status == SyncState.Status.RUNNING || updates.last().status == SyncState.Status.NEW)

        assertEquals(SyncState.Status.DONE, updates.last().status)
        assertEquals(3, updates.last().totalSize)
        assertEquals(100, updates.last().progress)
        val progress = updates.map { it.progress }
        assertEquals(progress.sorted(), progress)
        assertTrue(updates.any { it.status == SyncState.Status.RUNNING && it.progress in 1..99 })
    }

    // endregion

    // region Helpers

    private fun runQueueSync(
        target: QueueSyncUpTarget = QueueSyncUpTarget(),
        options: SyncOptions = SyncOptions.optionsForSyncUp(emptyList()),
        name: String? = null,
    ): SyncState {
        return runSyncUp(target, options, SyncQueue.QUEUE_SOUP, name)
    }

    private fun runSyncUp(target: SyncUpTarget, options: SyncOptions, soupName: String, name: String? = null): SyncState {
        // Create the sync first so that the callback queue knows its id before any update
        val sync = manager.createSyncUp(target, options, soupName, name)
        val callbacks = SyncUpdateCallbackQueue(sync.id)
        manager.runSync(sync, callbacks)
        return waitTerminal(callbacks, sync.id)
    }

    private fun runResync(sync: SyncState): SyncState {
        val callbacks = SyncUpdateCallbackQueue(sync.id)
        manager.reSync(sync.id, callbacks)
        return waitTerminal(callbacks, sync.id)
    }

    private fun waitTerminal(callbacks: SyncUpdateCallbackQueue, id: Long): SyncState {
        while (true) {
            val state = callbacks.getNextSyncUpdate(id)
            if (state.status != SyncState.Status.RUNNING && state.status != SyncState.Status.NEW) return state
        }
    }

    private fun checkFlags(record: JSONObject, local: Boolean, created: Boolean, updated: Boolean, deleted: Boolean) {
        assertEquals(local, record.getBoolean(SyncTarget.LOCAL))
        assertEquals(created, record.getBoolean(SyncTarget.LOCALLY_CREATED))
        assertEquals(updated, record.getBoolean(SyncTarget.LOCALLY_UPDATED))
        assertEquals(deleted, record.getBoolean(SyncTarget.LOCALLY_DELETED))
    }

    private fun findRecord(soupName: String, id: String): JSONObject? {
        val rows = store.query(QuerySpec.buildExactQuerySpec(soupName, "Id", id, null, null, 10), 0)
        assertTrue(rows.length() <= 1)
        return rows.optJSONObject(0)
    }

    private fun count(soupName: String) =
        store.countQuery(QuerySpec.buildAllQuerySpec(soupName, null, null, 100))

    private fun queueEntries(): JSONArray = store.query(
        QuerySpec.buildAllQuerySpec(SyncQueue.QUEUE_SOUP, SyncQueue.SEQUENCE, QuerySpec.Order.ascending, 100),
        0
    )

    private fun insertCleanRecord(soup: String, id: String, type: String, extra: Map<String, String> = emptyMap()) {
        val record = JSONObject()
            .put("Id", id)
            .put("Name", "name-$id")
            .put("attributes", JSONObject().put("type", type))
            .put(SyncTarget.LOCAL, false)
            .put(SyncTarget.LOCALLY_CREATED, false)
            .put(SyncTarget.LOCALLY_UPDATED, false)
            .put(SyncTarget.LOCALLY_DELETED, false)
        extra.forEach { (k, v) -> record.put(k, v) }
        store.create(soup, record)
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

    private fun account(id: String) = JSONObject()
        .put("Id", id)
        .put("Name", "name-$id")
        .put("attributes", JSONObject().put("type", "Account"))

    private fun contact(id: String) = JSONObject()
        .put("Id", id)
        .put("LastName", "last-$id")
        .put("attributes", JSONObject().put("type", "Contact"))

    /** A scripted answer of the fake server */
    class Reply(val status: Int, val body: String)

    /**
     * Rest client that does not touch the network: [responder] answers each request (null for the
     * default answer: a created record with the next id of [nextIds], or 204), or throws IOException.
     */
    class FakeRestClient : RestClient(
        RestClient.ClientInfo(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null
        ),
        null,
        HttpAccess.DEFAULT,
        null
    ) {
        val requests = mutableListOf<RestRequest>()
        val nextIds = ArrayDeque<String>()
        var responder: ((RestRequest) -> Reply?)? = null
        var onRequest: ((RestRequest) -> Unit)? = null
                private var counter = 0

        override fun getClientInfo(): ClientInfo? = null

        override fun sendSync(restRequest: RestRequest, vararg interceptors: Interceptor): RestResponse {
            requests.add(restRequest)
            onRequest?.invoke(restRequest)
            val reply = responder?.invoke(restRequest) ?: defaultReply(restRequest)
            return object : RestResponse(null) {
                override fun getStatusCode() = reply.status
                override fun isSuccess() = reply.status / 100 == 2
                override fun asString() = reply.body
            }
        }

        private fun defaultReply(request: RestRequest): Reply =
            if (request.method == RestRequest.RestMethod.POST ||
                (request.method == RestRequest.RestMethod.PATCH && request.path.contains("__c/"))
            ) {
                val id = nextIds.removeFirstOrNull() ?: "001GEN${++counter}"
                Reply(201, """{"id":"$id","success":true}""")
            } else {
                Reply(204, "")
            }

        /** "METHOD Type" for a create, "METHOD Type/id" otherwise (the upsert path ends with its external id) */
        fun requestLines(): List<String> = requests.map { r ->
            val parts = r.path.substringAfter("/sobjects/")
            "${r.method} $parts"
        }
    }

    companion object {
        private const val DB_NAME = "queueSyncUpTaskTestDb"
        private const val ACCOUNTS = "accounts"
        private const val CONTACTS = "contacts"
    }

    // endregion
}
