/*
 * Copyright (c) 2019-present, salesforce.com, inc.
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
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.mobilesync.target.BatchSyncUpTarget.Companion.MAX_BATCH_SIZE
import com.salesforce.androidsdk.mobilesync.target.BatchSyncUpTarget.Companion.MAX_SUB_REQUESTS_COMPOSITE_API
import com.salesforce.androidsdk.mobilesync.target.SyncTarget.Companion.ANDROID_IMPL
import com.salesforce.androidsdk.mobilesync.target.SyncTarget.Companion.ID_FIELD_NAME
import com.salesforce.androidsdk.mobilesync.target.SyncTarget.Companion.MODIFICATION_DATE_FIELD_NAME
import com.salesforce.androidsdk.mobilesync.target.SyncUpTarget.Companion.CREATE_FIELDLIST
import com.salesforce.androidsdk.mobilesync.target.SyncUpTarget.Companion.UPDATE_FIELDLIST
import com.salesforce.androidsdk.mobilesync.target.SyncUpTarget.Companion.fromJSON
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.Constants.DESCRIPTION
import com.salesforce.androidsdk.mobilesync.util.Constants.LAST_MODIFIED_DATE
import com.salesforce.androidsdk.mobilesync.util.Constants.NAME
import com.salesforce.androidsdk.mobilesync.util.JSONTestHelper.assertSameJSON
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test class for BatchSyncUpTarget constructors.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class BatchSyncUpTargetConstructorTest {
    @Test
    fun testMaxBatchSizeExceedingLimit() {
        val target = BatchSyncUpTarget(
            null,
            null,
            26
        )

        assertTrue(
            "Max batch size should be 25",
            25 == target.maxBatchSize
        )
    }

    @Test
    fun testMaxBatchSizeExceedingLimitInJSON() {
        val target = BatchSyncUpTarget(
            JSONObject().apply {
                put(
                    ANDROID_IMPL, BatchSyncUpTarget::class.java.name
                )
                put(
                    MAX_BATCH_SIZE, 26
                )
            })

        assertTrue(
            "Max batch size should be 25",
            25 == target.maxBatchSize
        )
    }

    @Test
    fun testConstructorDefault() {
        val target = BatchSyncUpTarget()

        assertNull(
            "Wrong createFieldList",
            target.createFieldlist
        )
        assertNull(
            "Wrong updateFieldList",
            target.updateFieldlist
        )
        assertEquals(
            "Wrong maxBatchSize",
            25,
            target.maxBatchSize.toLong()
        )
    }

    @Test
    fun testConstructorFieldLists() {
        val createdFieldArr = arrayOf(NAME)
        val updatedFieldArr = arrayOf(NAME, DESCRIPTION)
        val target = BatchSyncUpTarget(
            listOf(*createdFieldArr),
            listOf(*updatedFieldArr)
        )

        val targetCreateFieldlist = target.createFieldlist ?: throw AssertionError("Target create field list is null")
        val targetUpdateFieldlist = target.updateFieldlist ?: throw AssertionError("Target update field list is null")

        assertArrayEquals(
            "Wrong createFieldList",
            createdFieldArr,
            targetCreateFieldlist.toTypedArray()
        )
        assertArrayEquals(
            "Wrong updateFieldList",
            updatedFieldArr,
            targetUpdateFieldlist.toTypedArray()
        )
        assertEquals(
            "Wrong maxBatchSize",
            25,
            target.maxBatchSize.toLong()
        )
    }

    @Test
    fun testConstructorMaxBatchSize() {
        val createdFieldArr = arrayOf(NAME)
        val updatedFieldArr = arrayOf(NAME, DESCRIPTION)
        val maxBatchSize = 12
        val target = BatchSyncUpTarget(
            listOf(*createdFieldArr),
            listOf(*updatedFieldArr),
            maxBatchSize
        )

        val targetCreateFieldlist = target.createFieldlist ?: throw AssertionError("Target create field list is null")
        val targetUpdateFieldlist = target.updateFieldlist ?: throw AssertionError("Target update field list is null")

        assertArrayEquals(
            "Wrong createFieldList",
            createdFieldArr,
            targetCreateFieldlist.toTypedArray()
        )
        assertArrayEquals(
            "Wrong updateFieldList",
            updatedFieldArr,
            targetUpdateFieldlist.toTypedArray()
        )
        assertEquals(
            "Wrong maxBatchSize",
            maxBatchSize.toLong(),
            target.maxBatchSize.toLong()
        )
    }

    @Test
    fun testConstructorWithJSON() {
        val createdFieldArr = arrayOf(NAME)
        val updatedFieldArr = arrayOf(NAME, DESCRIPTION)
        val maxBatchSize = 12
        val target = BatchSyncUpTarget(JSONObject().apply {
            put(ANDROID_IMPL, BatchSyncUpTarget::class.java.name)
            put(CREATE_FIELDLIST, JSONArray(createdFieldArr))
            put(UPDATE_FIELDLIST, JSONArray(updatedFieldArr))
            put(MAX_BATCH_SIZE, maxBatchSize)
        })

        val targetCreateFieldlist = target.createFieldlist ?: throw AssertionError("Target create field list is null")
        val targetUpdateFieldlist = target.updateFieldlist ?: throw AssertionError("Target update field list is null")

        assertArrayEquals(
            "Wrong createFieldList",
            createdFieldArr,
            targetCreateFieldlist.toTypedArray()
        )
        assertArrayEquals(
            "Wrong updateFieldList",
            updatedFieldArr,
            targetUpdateFieldlist.toTypedArray()
        )
        assertEquals(
            "Wrong maxBatchSize",
            maxBatchSize.toLong(),
            target.maxBatchSize.toLong()
        )
    }

    @Test
    fun testConstructorWithJSONWithoutOptionalFields() {
        val target = BatchSyncUpTarget(JSONObject().apply {
            put(ANDROID_IMPL, BatchSyncUpTarget::class.java.name)
        })

        assertNull(
            "Wrong createFieldList",
            target.createFieldlist
        )
        assertNull(
            "Wrong updateFieldList",
            target.updateFieldlist
        )
        assertEquals(
            "Wrong maxBatchSize",
            MAX_SUB_REQUESTS_COMPOSITE_API.toLong(),
            target.maxBatchSize.toLong()
        )
    }

    @Test
    fun testFromJSON() {
        val maxBatchSize = 12
        val target = fromJSON(JSONObject().apply {
            put(ANDROID_IMPL, BatchSyncUpTarget::class.java.name)
            put(MAX_BATCH_SIZE, maxBatchSize)
        })

        assertTrue(target is BatchSyncUpTarget)
        assertEquals(
            "Wrong maxBatchSize",
            maxBatchSize.toLong(),
            (target as BatchSyncUpTarget).maxBatchSize.toLong()
        )
    }

    @Test
    fun testToJSON() {
        val createdFieldArr = arrayOf(NAME)
        val updatedFieldArr = arrayOf(NAME, DESCRIPTION)
        val maxBatchSize = 12
        val target = BatchSyncUpTarget(
            listOf(*createdFieldArr),
            listOf(*updatedFieldArr),
            maxBatchSize
        )
        val expectedTargetJson = JSONObject().apply {
            put(ANDROID_IMPL, BatchSyncUpTarget::class.java.name)
            put(ID_FIELD_NAME, Constants.ID)
            put(MODIFICATION_DATE_FIELD_NAME, LAST_MODIFIED_DATE)
            put(CREATE_FIELDLIST, JSONArray(createdFieldArr))
            put(UPDATE_FIELDLIST, JSONArray(updatedFieldArr))
            put(MAX_BATCH_SIZE, maxBatchSize)
        }

        assertSameJSON(
            "Wrong json",
            expectedTargetJson,
            target.asJSON()
        )
    }
}
