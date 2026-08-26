/*
 * Copyright (c) 2025-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.mobilesync.target

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.salesforce.androidsdk.mobilesync.manager.SyncManager
import com.salesforce.androidsdk.mobilesync.manager.SyncManagerTestCase
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression tests for single-quote escaping in SyncTarget.deleteRecordsFromLocalStore.
 *
 * Verifies that record IDs containing single quotes are correctly escaped when building
 * the SmartSQL IN-clause, so that only the targeted record is deleted.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class SyncTargetSingleQuoteTest : SyncManagerTestCase() {

    /**
     * Test subclass that exposes the protected deleteRecordsFromLocalStore method.
     */
    private class TestTarget : SoqlSyncDownTarget("SELECT Id FROM Account") {
        fun callDeleteRecords(
            syncManager: SyncManager,
            soupName: String,
            ids: Set<String>,
            idField: String?
        ) {
            deleteRecordsFromLocalStore(syncManager, soupName, ids, idField)
        }
    }

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        createAccountsSoup()
    }

    @After
    @Throws(Exception::class)
    override fun tearDown() {
        dropAccountsSoup()
        super.tearDown()
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteRecordsFromLocalStore_whenIdContainsSingleQuote_deletesOnlyTargetedRecord() {
        // Upsert two records: one whose ID contains a single quote and one clean ID
        val quoteId = "001'Quote"
        val normalId = "001Normal"

        for (id in listOf(quoteId, normalId)) {
            val record = JSONObject().apply {
                put(Constants.ID, id)
                put(Constants.NAME, "Test $id")
                put(SyncTarget.LOCAL, false)
                put(SyncTarget.LOCALLY_CREATED, false)
                put(SyncTarget.LOCALLY_DELETED, false)
                put(SyncTarget.LOCALLY_UPDATED, false)
            }
            smartStore.upsert(ACCOUNTS_SOUP, record, Constants.ID, true)
        }

        // Verify both records are present
        val allQuery = QuerySpec.buildAllQuerySpec(ACCOUNTS_SOUP, Constants.ID, QuerySpec.Order.ascending, 10)
        assertEquals("Should have 2 records before delete", 2, smartStore.countQuery(allQuery))

        // Delete only the record with the single-quote ID
        TestTarget().callDeleteRecords(syncManager, ACCOUNTS_SOUP, setOf(quoteId), Constants.ID)

        // Verify only the normal record remains
        assertEquals("Should have 1 record after delete", 1, smartStore.countQuery(allQuery))

        val remaining = smartStore.query(allQuery, 0)
        assertEquals("Remaining record should be normalId", normalId, remaining.getJSONObject(0).getString(Constants.ID))
    }
}
