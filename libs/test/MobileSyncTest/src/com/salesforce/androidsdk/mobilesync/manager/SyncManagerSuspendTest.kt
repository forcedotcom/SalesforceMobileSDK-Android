/*
 * Copyright (c) 2023-present, salesforce.com, inc.
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
import com.salesforce.androidsdk.mobilesync.target.SoqlSyncDownTarget
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.SyncOptions.Companion.optionsForSyncDown
import com.salesforce.androidsdk.mobilesync.util.SyncState
import com.salesforce.androidsdk.mobilesync.util.SyncState.Companion.createSyncDown
import com.salesforce.androidsdk.mobilesync.util.SyncState.MergeMode
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test class for SyncManager that uses coroutine wrappers.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class SyncManagerSuspendTest : SyncManagerTestCase() {
    private lateinit var idToFields: MutableMap<String, Map<String, Any>>

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        createAccountsSoup()
        idToFields = createRecordsOnServerReturnFields(COUNT_TEST_ACCOUNTS, Constants.ACCOUNT, null)
    }

    @After
    @Throws(Exception::class)
    override fun tearDown() {
        idToFields?.let { deleteRecordsByIdOnServer(it.keys, Constants.ACCOUNT) }
        dropAccountsSoup()
        super.tearDown()
    }

    /**
     * Sync down the test accounts, modify a few on the server, re-sync using sync name, make sure only the updated ones are downloaded
     */
    @Test
    @Throws(Exception::class)
    fun testReSyncByName() {
        val syncName = "syncForTestReSyncByName"

        // first sync down
        val syncId = trySyncDownWithCoroutine(MergeMode.OVERWRITE, syncName)

        // Check sync time stamp
        val sync = syncManager.getSyncStatus(syncId) ?: throw AssertionError("Sync not found")
        val maxTimeStamp = sync.maxTimeStamp
        Assert.assertTrue("Wrong time stamp", maxTimeStamp > 0)

        // Make some remote change
        val idToFieldsUpdated = makeRemoteChanges(idToFields, Constants.ACCOUNT)

        // Call reSync
        val finalState = tryResyncWithCoroutine(syncId)

        // Check final state
        checkStatus(
            finalState,
            SyncState.Type.syncDown,
            syncId,
            sync.target as SyncDownTarget,
            sync.options,
            SyncState.Status.DONE,
            100,
            idToFieldsUpdated.size
        )

        // Check db
        checkDb(idToFieldsUpdated, ACCOUNTS_SOUP)

        // Check sync time stamp
        val dbState = syncManager.getSyncStatus(syncId) ?: throw AssertionError("State should not be null")
        Assert.assertTrue(
            "Wrong time stamp",
            dbState.maxTimeStamp > maxTimeStamp
        )
    }

    /**
     * Call reSync with the name of non-existing sync, expect exception
     */
    @Test
    @Throws(Exception::class)
    fun testReSyncByNameWithWrongName() {
        val syncName = "testReSyncByNameWithWrongName"
        try {
            tryResyncWithCoroutine(syncName)
            Assert.fail("Expected exception")
        } catch (e: SyncManager.ReSyncException.FailedToStart) {
            Assert.assertTrue(e.cause?.message?.contains("does not exist") == true)
        }
    }

    /**
     * Tests if ghost records are cleaned locally for a SOQL target.
     */
    @Test
    @Throws(java.lang.Exception::class)
    fun testCleanResyncGhostsForSOQLTarget() {

        // Creates 3 accounts on the server.
        val numberAccounts = 3
        val accounts = createRecordsOnServer(numberAccounts, Constants.ACCOUNT)
        Assert.assertEquals(
            "Wrong number of accounts created",
            numberAccounts.toLong(),
            accounts.size.toLong()
        )
        val accountIds = accounts.keys.toTypedArray()

        // Builds SOQL sync down target and performs initial sync.
        val soql = "SELECT Id, Name FROM Account WHERE Id IN ${makeInClause(accountIds)}"

        val syncId = trySyncDownWithCoroutine(MergeMode.LEAVE_IF_CHANGED, query = soql)
        checkDbExist(ACCOUNTS_SOUP, accountIds, Constants.ID)

        // Deletes 1 account on the server and verifies the ghost record is cleared from the soup.
        deleteRecordsByIdOnServer(HashSet(listOf(accountIds[0])), Constants.ACCOUNT)
        tryCleanResyncGhostsWithCoroutine(syncId)
        checkDbExist(ACCOUNTS_SOUP, arrayOf(accountIds[1], accountIds[2]), Constants.ID)
        checkDbDeleted(ACCOUNTS_SOUP, arrayOf(accountIds[0]), Constants.ID)

        // Deletes the remaining accounts on the server.
        deleteRecordsByIdOnServer(HashSet(listOf(accountIds[1], accountIds[2])), Constants.ACCOUNT)
    }

    @Throws(JSONException::class)
    private fun trySyncDownWithCoroutine(
        mergeMode: MergeMode,
        syncName: String? = null,
        query: String? = null): Long {
        val queryToUse = query
            ?: "SELECT Id, Name, Description, LastModifiedDate FROM Account WHERE Id IN ${makeInClause(
            idToFields!!.keys)}"
        val target: SyncDownTarget = SoqlSyncDownTarget(queryToUse)
        val options = optionsForSyncDown(mergeMode)
        val sync = createSyncDown(smartStore, target, options, ACCOUNTS_SOUP, syncName)
        val syncId = sync.id

        try {
            runBlocking {
                syncManager.suspendReSync(syncId)
            }
        } catch (e: SyncManager.ReSyncException) {
            Assert.fail(e.message)
        }
        return syncId
    }

    @Throws(JSONException::class, InterruptedException::class)
    fun tryResyncWithCoroutine(syncId: Long): SyncState {
        return runBlocking {
            syncManager.suspendReSync(syncId)
        }
    }

    @Throws(JSONException::class, InterruptedException::class)
    fun tryResyncWithCoroutine(syncName: String): SyncState {
        return runBlocking {
            syncManager.suspendReSync(syncName)
        }
    }

    @Throws(JSONException::class, InterruptedException::class)
    fun tryCleanResyncGhostsWithCoroutine(syncId: Long): Int {
        return runBlocking {
            syncManager.suspendCleanResyncGhosts(syncId)
        }
    }

    companion object {
        // Misc
        private const val COUNT_TEST_ACCOUNTS = 10
    }
}