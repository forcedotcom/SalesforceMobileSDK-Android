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
import androidx.test.filters.LargeTest
import com.salesforce.androidsdk.mobilesync.target.QueueSyncUpTarget
import com.salesforce.androidsdk.mobilesync.target.SyncTarget
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.SyncOptions
import com.salesforce.androidsdk.mobilesync.util.SyncQueue
import com.salesforce.androidsdk.mobilesync.util.SyncState
import com.salesforce.androidsdk.mobilesync.util.SyncUpdateCallbackQueue
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.smartstore.store.IndexSpec
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import com.salesforce.androidsdk.smartstore.store.SmartStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Live-org tests of [QueueSyncUpTarget]: the queue is replayed against a real server through the
 * real REST client (no fake client). Everything created on the server is deleted in [tearDown].
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class QueueSyncUpOrgTest : SyncManagerTestCase() {

    private val prefix = "QueueOrgTest_${System.nanoTime()}"
    private val serverIds = mutableMapOf<String, MutableSet<String>>() // sobject type -> ids to clean up

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        createSoups()
        clearQueue()
    }

    @After
    @Throws(Exception::class)
    override fun tearDown() {
        try {
            // Contacts first, then accounts and anything else (by tracked ids and by unique name prefix)
            serverIds["Contact"]?.let { deleteQuietly(it, "Contact") }
            deleteByPrefix("Contact", "LastName")
            for ((type, ids) in serverIds) if (type != "Contact") deleteQuietly(ids, type)
            deleteByPrefix("Account", "Name")
        } finally {
            clearQueue()
            dropSoup(ACCOUNTS)
            dropSoup(CONTACTS)
            super.tearDown()
        }
    }

    // region Case 1: create, update, delete

    @Test
    fun testCreateUpdateDeleteReplayedAgainstServer() {
        val name = "$prefix-a"
        val local = syncManager.createLocal(ACCOUNTS, account(name))
        val localId = local.getString(Constants.ID)
        assertTrue(SyncTarget.isLocalId(localId))

        // CREATE alone
        var sync = runQueueSync()
        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals(0, queueEntries().length())
        val serverId = idsByName("Account", "Name", name).single()
        track("Account", serverId)
        assertNull(findRecord(ACCOUNTS, localId))
        var record = findRecord(ACCOUNTS, serverId)!!
        checkFlags(record, local = false, created = false, updated = false, deleted = false)
        assertEquals(name, record.getString("Name"))
        assertEquals(name, serverField("Account", serverId, "Name"))

        // UPDATE alone
        syncManager.updateLocal(ACCOUNTS, serverId, JSONObject().put("Name", "$name-upd").put("Description", "d1"))
        sync = runQueueSync()
        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals(0, queueEntries().length())
        assertEquals("$name-upd", serverField("Account", serverId, "Name"))
        assertEquals("d1", serverField("Account", serverId, "Description"))
        record = findRecord(ACCOUNTS, serverId)!!
        checkFlags(record, local = false, created = false, updated = false, deleted = false)

        // DELETE
        syncManager.deleteLocal(ACCOUNTS, serverId)
        assertEquals(1, queueEntries().length())
        sync = runQueueSync()
        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals(0, queueEntries().length())
        assertNull(findRecord(ACCOUNTS, serverId))
        assertTrue(idsByName("Account", "Id", serverId).isEmpty())
    }

    @Test
    fun testCreateAndUpdateReplayedTogetherThenDelete() {
        val name = "$prefix-b"
        val localId = syncManager.createLocal(ACCOUNTS, account(name)).getString(Constants.ID)
        syncManager.updateLocal(ACCOUNTS, localId, JSONObject().put("Name", "$name-upd").put("Description", "d2"))
        assertEquals(2, queueEntries().length())

        var sync = runQueueSync()
        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals(2, sync.totalSize)
        assertEquals(0, queueEntries().length())
        val serverId = idsByName("Account", "Name", "$name-upd").single()
        track("Account", serverId)
        assertTrue(idsByName("Account", "Name", name).isEmpty()) // never existed with the old name
        assertEquals("d2", serverField("Account", serverId, "Description"))
        assertNull(findRecord(ACCOUNTS, localId))
        checkFlags(findRecord(ACCOUNTS, serverId)!!, local = false, created = false, updated = false, deleted = false)
        assertEquals("$name-upd", findRecord(ACCOUNTS, serverId)!!.getString("Name"))

        syncManager.deleteLocal(ACCOUNTS, serverId)
        sync = runQueueSync()
        assertEquals(SyncState.Status.DONE, sync.status)
        assertNull(findRecord(ACCOUNTS, serverId))
        assertTrue(idsByName("Account", "Id", serverId).isEmpty())
        assertEquals(0, queueEntries().length())
    }

    @Test
    fun testDeleteOfRecordAlreadyDeletedOnServerIsSuccess() {
        val serverId = createRecordsOnServer(1, Constants.ACCOUNT).keys.single()
        track("Account", serverId)
        insertCleanRecord(ACCOUNTS, serverId, "Account")
        deleteRecordsByIdOnServer(listOf(serverId), "Account") // someone else deleted it
        syncManager.deleteLocal(ACCOUNTS, serverId)

        val sync = runQueueSync()

        assertEquals(SyncState.Status.DONE, sync.status) // 404 on DELETE counts as success
        assertEquals(0, queueEntries().length())
        assertNull(findRecord(ACCOUNTS, serverId))
    }

    // endregion

    // region Case 2: temp-id remapping

    @Test
    fun testContactReferencingLocalAccountIdIsRemapped() {
        val accountName = "$prefix-parent"
        val contactName = "$prefix-child"
        val accountLocalId = syncManager.createLocal(ACCOUNTS, account(accountName)).getString(Constants.ID)
        val contactLocalId = syncManager.createLocal(
            CONTACTS,
            JSONObject().put("LastName", contactName).put(ACCOUNT_ID, accountLocalId)
                .put(Constants.ATTRIBUTES, JSONObject().put(Constants.LTYPE, "Contact"))
        ).getString(Constants.ID)

        val sync = runQueueSync()

        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals(0, queueEntries().length())
        val accountId = idsByName("Account", "Name", accountName).single()
        track("Account", accountId)
        val contactId = idsByName("Contact", "LastName", contactName).single()
        track("Contact", contactId)
        assertEquals(accountId, serverField("Contact", contactId, ACCOUNT_ID))
        assertNull(findRecord(ACCOUNTS, accountLocalId))
        assertNull(findRecord(CONTACTS, contactLocalId))
        val localContact = findRecord(CONTACTS, contactId)!!
        // Documented limitation: the soup copy of the contact keeps the local account id until the next sync down
        assertEquals(accountLocalId, localContact.getString(ACCOUNT_ID))
        checkFlags(localContact, local = false, created = false, updated = false, deleted = false)
    }

    // endregion

    // region Case 3: record-level error

    @Test
    fun testRejectedUpdateIsRecordLevelAndOthersReplay() {
        val ids = createRecordsOnServer(2, Constants.ACCOUNT).keys.toList()
        ids.forEach { track("Account", it); insertCleanRecord(ACCOUNTS, it, "Account") }
        val (bad, other) = ids
        val badName = "$prefix-bad-never-applied"
        syncManager.updateLocal(ACCOUNTS, bad, JSONObject().put("NumberOfEmployees", "not-a-number"))
        syncManager.updateLocal(ACCOUNTS, other, JSONObject().put("Description", "other-ok"))
        syncManager.updateLocal(ACCOUNTS, bad, JSONObject().put("Name", badName))
        val before = serverField("Account", bad, "Name")

        val sync = runQueueSync()

        assertEquals(SyncState.Status.DONE, sync.status) // record-level error does not fail the sync
        assertEquals("other-ok", serverField("Account", other, "Description"))
        assertEquals(before, serverField("Account", bad, "Name")) // later entry of the same record deferred
        val entries = queueEntries()
        assertEquals(2, entries.length())
        val first = entries.getJSONObject(0)
        val second = entries.getJSONObject(1)
        assertEquals(bad, first.getString(SyncQueue.RECORD_ID))
        assertEquals(1, first.getInt(SyncQueue.ATTEMPTS))
        assertFalse(first.isNull(SyncQueue.LAST_ERROR))
        println("QueueSyncUpOrgTest live lastError for rejected update: ${first.get(SyncQueue.LAST_ERROR)}")
        assertEquals(bad, second.getString(SyncQueue.RECORD_ID))
        assertEquals(0, second.optInt(SyncQueue.ATTEMPTS, 0))
        assertTrue(second.isNull(SyncQueue.LAST_ERROR))
        assertEquals("PENDING", second.getString(SyncQueue.STATUS))
        val badRecord = findRecord(ACCOUNTS, bad)!!
        assertTrue(badRecord.getBoolean(SyncTarget.LOCAL))
        assertTrue(badRecord.has(SyncTarget.LAST_ERROR) && !badRecord.isNull(SyncTarget.LAST_ERROR))
        checkFlags(findRecord(ACCOUNTS, other)!!, local = false, created = false, updated = false, deleted = false)
    }

    @Test
    fun testUpdateOfNonexistentServerRecordIsRecordLevel() {
        val real = createRecordsOnServer(1, Constants.ACCOUNT).keys.single()
        track("Account", real)
        insertCleanRecord(ACCOUNTS, real, "Account")
        // A valid id of an Account that was deleted on the server meanwhile
        val ghost = createRecordsOnServer(1, Constants.ACCOUNT).keys.single()
        deleteRecordsByIdOnServer(listOf(ghost), "Account")
        insertCleanRecord(ACCOUNTS, ghost, "Account")
        syncManager.updateLocal(ACCOUNTS, ghost, JSONObject().put("Name", "$prefix-ghost"))
        syncManager.updateLocal(ACCOUNTS, real, JSONObject().put("Description", "real-ok"))

        val sync = runQueueSync()

        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals("real-ok", serverField("Account", real, "Description"))
        val entries = queueEntries()
        assertEquals(1, entries.length())
        assertEquals(ghost, entries.getJSONObject(0).getString(SyncQueue.RECORD_ID))
        assertEquals(1, entries.getJSONObject(0).getInt(SyncQueue.ATTEMPTS))
        println("QueueSyncUpOrgTest live lastError for nonexistent record: ${entries.getJSONObject(0).get(SyncQueue.LAST_ERROR)}")
    }

    // endregion

    // region Case 4: upsert with external id (Account.External_Id__c)

    @Test
    fun testUpsertWithExternalId() {
        val field = EXT_FIELD
        val value = "ext-${System.nanoTime()}"
        val target = QueueSyncUpTarget(field, Constants.ID, null)
        assumeUpsertUsable()
        val firstLocalId = syncManager.createLocal(ACCOUNTS, extAccount("$prefix-up1", value)).getString(Constants.ID)
        var sync = runQueueSync(target)
        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals(0, queueEntries().length())
        val serverId = idsByName("Account", field, value).single()
        track("Account", serverId)
        assertEquals("$prefix-up1", serverField("Account", serverId, "Name"))
        assertNull(findRecord(ACCOUNTS, firstLocalId))
        assertNotNull(findRecord(ACCOUNTS, serverId))

        // Probe the real statuses for the same external id value (this also updates the record)
        val probe = restClient.sendSync(
            RestRequest.getRequestForUpsert(apiVersion, "Account", field, value, mapOf<String, Any>("Name" to "$prefix-probe"))
        )
        println("QueueSyncUpOrgTest live repeated upsert status=${probe.statusCode} body=${probe.asString()}")
        val lookup = restClient.sendSync(
            RestRequest(RestRequest.RestMethod.GET, "/services/data/$apiVersion/sobjects/Account/$field/$value?fields=Id")
        )
        println("QueueSyncUpOrgTest live lookup status=${lookup.statusCode} body=${lookup.asString()}")
        val missing = restClient.sendSync(
            RestRequest(RestRequest.RestMethod.GET, "/services/data/$apiVersion/sobjects/Account/$field/nope-$value?fields=Id")
        )
        println("QueueSyncUpOrgTest live lookup of unknown value status=${missing.statusCode} body=${missing.asString()}")

        // Same external id value through the queue: the server updates the existing record
        val secondLocalId = syncManager.createLocal(ACCOUNTS, extAccount("$prefix-up2", value)).getString(Constants.ID)
        sync = runQueueSync(target)
        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals(0, queueEntries().length())
        assertEquals(listOf(serverId), idsByName("Account", field, value)) // still one record
        assertEquals("$prefix-up2", serverField("Account", serverId, "Name"))
        assertNull(findRecord(ACCOUNTS, secondLocalId))
        val local = findRecord(ACCOUNTS, serverId)!!
        assertEquals("$prefix-up2", local.getString("Name"))
        checkFlags(local, local = false, created = false, updated = false, deleted = false)
        assertEquals(1, count(ACCOUNTS) - 0) // no second row for the same server id
    }

    @Test
    fun testUpsertDeferredWhenItsDestinationIsBlockedByRejectedUpdate() {
        assumeUpsertUsable()
        val field = EXT_FIELD
        val value = "ext-${System.nanoTime()}"
        val target = QueueSyncUpTarget(field, Constants.ID, null)
        val created = restClient.sendSync(
            RestRequest.getRequestForCreate(apiVersion, "Account", mapOf<String, Any>("Name" to "$prefix-dest", field to value))
        )
        assertTrue(created.isSuccess)
        val destId = created.asJSONObject().getString("id")
        track("Account", destId)
        insertCleanRecord(ACCOUNTS, destId, "Account")

        // Rejected UPDATE of the destination record, then an upsert CREATE that would hit it,
        // and an upsert CREATE of another value that must go through
        syncManager.updateLocal(ACCOUNTS, destId, JSONObject().put("NumberOfEmployees", "not-a-number"))
        val blockedLocalId = syncManager.createLocal(ACCOUNTS, extAccount("$prefix-blocked", value)).getString(Constants.ID)
        val otherValue = "$value-other"
        val otherLocalId = syncManager.createLocal(ACCOUNTS, extAccount("$prefix-free", otherValue)).getString(Constants.ID)

        val sync = runQueueSync(target)

        assertEquals(SyncState.Status.DONE, sync.status)
        assertEquals("$prefix-dest", serverField("Account", destId, "Name")) // upsert did not overwrite it
        val freeId = idsByName("Account", field, otherValue).single()
        track("Account", freeId)
        assertEquals("$prefix-free", serverField("Account", freeId, "Name"))
        assertNull(findRecord(ACCOUNTS, otherLocalId))
        val entries = queueEntries()
        assertEquals(2, entries.length())
        assertEquals(destId, entries.getJSONObject(0).getString(SyncQueue.RECORD_ID))
        assertEquals(1, entries.getJSONObject(0).getInt(SyncQueue.ATTEMPTS))
        assertEquals(blockedLocalId, entries.getJSONObject(1).getString(SyncQueue.RECORD_ID))
        assertEquals(0, entries.getJSONObject(1).optInt(SyncQueue.ATTEMPTS, 0))
        assertTrue(entries.getJSONObject(1).isNull(SyncQueue.LAST_ERROR))
        assertNotNull(findRecord(ACCOUNTS, blockedLocalId))
    }

    /**
     * Spec case 19 with a UNIQUE external id: X is owned by nobody when the first deferred upsert looks it up (404),
     * then B acquires X, then the second deferred upsert must find B and keep the newer edit of B from going first.
     */
    @Test
    fun testUniqueExternalIdWhenOwnerAcquiredBetweenTwoDeferredUpsertsThenNewerEditOfNewOwnerDeferred() {
        assumeUpsertUsable()
        val field = EXT_FIELD
        val value = "ext-${System.nanoTime()}"
        val target = QueueSyncUpTarget(field, Constants.ID, null)
        val created = restClient.sendSync(RestRequest.getRequestForCreate(apiVersion, "Account", mapOf<String, Any>("Name" to "$prefix-b")))
        assertTrue(created.isSuccess)
        val idB = created.asJSONObject().getString("id")
        track("Account", idB)
        insertCleanRecord(ACCOUNTS, idB, "Account")

        // 1. a create that the server rejects (name too long): every entry that refers to it waits
        val parent = syncManager.createLocal(ACCOUNTS, account("x".repeat(300))).getString(Constants.ID)
        // 2. upsert on X (older), deferred: X is looked up, nobody owns it (404)
        syncManager.createLocal(ACCOUNTS, extAccount("$prefix-older1", value).put("ParentId", parent))
        // 3. B takes X: succeeds
        syncManager.updateLocal(ACCOUNTS, idB, JSONObject().put(field, value))
        // 4. second upsert on X, deferred: X is looked up again (the write cleared the cache) and found on B
        syncManager.createLocal(ACCOUNTS, extAccount("$prefix-older2", value).put("ParentId", parent))
        // 5. newer edit of B: B is blocked, so it waits
        syncManager.updateLocal(ACCOUNTS, idB, JSONObject().put("Name", "$prefix-newer"))

        val sync = runQueueSync(target)

        assertEquals(SyncState.Status.DONE, sync.status)
        val entries = queueEntries()
        assertEquals(listOf("CREATE", "CREATE", "CREATE", "UPDATE"), (0 until entries.length()).map { entries.getJSONObject(it).getString(SyncQueue.KIND) })
        assertEquals(listOf(1, 0, 0, 0), (0 until entries.length()).map { entries.getJSONObject(it).optInt(SyncQueue.ATTEMPTS, 0) })
        assertEquals(listOf(idB), idsByName("Account", field, value))
        assertEquals("$prefix-b", serverField("Account", idB, "Name")) // the newer edit of B was not sent
    }

    // endregion

    // region Helpers


    private fun runQueueSync(target: QueueSyncUpTarget = QueueSyncUpTarget()): SyncState {
        val sync = syncManager.createSyncUp(target, SyncOptions.optionsForSyncUp(emptyList()), SyncQueue.QUEUE_SOUP, null)
        val callbacks = SyncUpdateCallbackQueue(sync.id)
        syncManager.runSync(sync, callbacks)
        while (true) {
            val state = callbacks.getNextSyncUpdate(sync.id)
            if (state.status != SyncState.Status.RUNNING && state.status != SyncState.Status.NEW) {
                println("QueueSyncUpOrgTest sync ${state.id} ended ${state.status}")
                return state
            }
        }
    }

    private fun account(name: String) = JSONObject().put("Name", name)
        .put(Constants.ATTRIBUTES, JSONObject().put(Constants.LTYPE, "Account"))

    private fun createSoups() {
        for (soup in listOf(ACCOUNTS, CONTACTS)) {
            dropSoup(soup)
            smartStore.registerSoup(
                soup,
                arrayOf(
                    IndexSpec(Constants.ID, SmartStore.Type.string),
                    IndexSpec(SyncTarget.LOCAL, SmartStore.Type.string),
                    IndexSpec(SyncTarget.LOCALLY_CREATED, SmartStore.Type.string),
                    IndexSpec(SyncTarget.LOCALLY_UPDATED, SmartStore.Type.string),
                    IndexSpec(SyncTarget.LOCALLY_DELETED, SmartStore.Type.string),
                )
            )
        }
    }

    private fun dropSoup(soup: String) {
        if (smartStore.hasSoup(soup)) smartStore.dropSoup(soup)
    }

    private fun clearQueue() {
        if (smartStore.hasSoup(SyncQueue.QUEUE_SOUP)) smartStore.clearSoup(SyncQueue.QUEUE_SOUP)
    }

    private fun insertCleanRecord(soup: String, id: String, type: String) {
        smartStore.create(
            soup,
            JSONObject().put(Constants.ID, id).put("Name", "name-$id")
                .put(Constants.ATTRIBUTES, JSONObject().put(Constants.LTYPE, type))
                .put(SyncTarget.LOCAL, false).put(SyncTarget.LOCALLY_CREATED, false)
                .put(SyncTarget.LOCALLY_UPDATED, false).put(SyncTarget.LOCALLY_DELETED, false)
        )
    }

    private fun findRecord(soup: String, id: String): JSONObject? {
        val rows = smartStore.query(QuerySpec.buildExactQuerySpec(soup, Constants.ID, id, null, null, 10), 0)
        assertTrue(rows.length() <= 1)
        return rows.optJSONObject(0)
    }

    private fun queueEntries(): JSONArray = smartStore.query(
        QuerySpec.buildAllQuerySpec(SyncQueue.QUEUE_SOUP, SyncQueue.SEQUENCE, QuerySpec.Order.ascending, 100), 0
    )

    private fun checkFlags(record: JSONObject, local: Boolean, created: Boolean, updated: Boolean, deleted: Boolean) {
        assertEquals(local, record.getBoolean(SyncTarget.LOCAL))
        assertEquals(created, record.getBoolean(SyncTarget.LOCALLY_CREATED))
        assertEquals(updated, record.getBoolean(SyncTarget.LOCALLY_UPDATED))
        assertEquals(deleted, record.getBoolean(SyncTarget.LOCALLY_DELETED))
    }

    private fun track(type: String, id: String) {
        serverIds.getOrPut(type) { mutableSetOf() }.add(id)
    }

    /** Ids of records of [type] on the server whose [field] equals [value] */
    private fun idsByName(type: String, field: String, value: String): List<String> =
        getIdsOnServer(type, "$field = '$value'")

    private fun serverField(type: String, id: String, field: String): String? {
        val response = restClient.sendSync(RestRequest.getRequestForRetrieve(apiVersion, type, id, listOf(field)))
        assertTrue("Retrieve failed: $response", response.isSuccess)
        val json = response.asJSONObject()
        return if (json.isNull(field)) null else json.getString(field)
    }

    private fun deleteQuietly(ids: Collection<String>, type: String) {
        try {
            deleteRecordsByIdOnServer(ids, type)
        } catch (e: Exception) {
            println("QueueSyncUpOrgTest cleanup failed for $type: ${e.message}")
        }
    }

    private fun deleteByPrefix(type: String, field: String) {
        try {
            deleteRecordsByCriteriaFromServer(type, "$field LIKE '$prefix%'")
        } catch (e: Exception) {
            println("QueueSyncUpOrgTest cleanup by prefix failed for $type: ${e.message}")
        }
    }

    /** Skips the test (with the server answer) if the org does not accept upsert by [EXT_FIELD] */
    private fun assumeUpsertUsable() {
        val value = "probe-${System.nanoTime()}"
        val response = restClient.sendSync(
            RestRequest.getRequestForUpsert(apiVersion, "Account", EXT_FIELD, value, mapOf<String, Any>("Name" to "$prefix-probe"))
        )
        println("QueueSyncUpOrgTest live probe upsert status=${response.statusCode} body=${response.asString()}")
        // 400/404: the field is not usable for upsert (see docs/mobilesync/TEST_ORG_SETUP.md in the Workspace repo)
        assumeTrue(
            "SKIPPED: Account.$EXT_FIELD is unusable for upsert (${response.statusCode}); " +
                "see docs/mobilesync/TEST_ORG_SETUP.md in the Workspace repo",
            response.statusCode != 400 && response.statusCode != 404
        )
        assertTrue("Unexpected answer to the upsert probe: ${response.statusCode} ${response.asString()}", response.isSuccess)
    }

    private fun extAccount(name: String, extId: String) = account(name).put(EXT_FIELD, extId)

    private fun count(soup: String) = smartStore.countQuery(QuerySpec.buildAllQuerySpec(soup, null, null, 100))

    // endregion

    companion object {
        private const val ACCOUNTS = "queue_org_accounts"
        private const val CONTACTS = "queue_org_contacts"
        private const val EXT_FIELD = "External_Id__c"
    }
}
