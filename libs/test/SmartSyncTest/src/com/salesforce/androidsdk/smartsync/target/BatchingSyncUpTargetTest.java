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

package com.salesforce.androidsdk.smartsync.target;

import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.androidsdk.smartsync.util.SyncState;
import com.salesforce.androidsdk.util.test.JSONTestHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

/**
 * Test class for BatchingSyncUpTarget.
 * Running all the same tests as SyncUpTargetTest but using a BatchingSyncUpTarget with batch size of 2
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class BatchingSyncUpTargetTest extends SyncUpTargetTest {

    @Override
    protected void trySyncUp(int numberChanges, SyncState.MergeMode mergeMode, List<String> createFieldlist, List<String> updateFieldlist) throws JSONException {
        trySyncUp(new BatchingSyncUpTarget(createFieldlist, updateFieldlist, 2), numberChanges, mergeMode);
    }

    @Test
    public void testMaxBatchSizeExceeding25() {
        BatchingSyncUpTarget target = new BatchingSyncUpTarget(null, null, 26);

        Assert.assertTrue("Max batch size should be 25", 25 == target.getMaxBatchSize());
    }

    @Test
    public void testMaxBatchSizeExceeding25InJSON() throws Exception {
        JSONObject targetJson = new JSONObject();
        targetJson.put(SyncTarget.ANDROID_IMPL, BatchingSyncUpTarget.class.getName());
        targetJson.put(BatchingSyncUpTarget.MAX_BATCH_SIZE, 26);

        BatchingSyncUpTarget target = new BatchingSyncUpTarget(targetJson);

        Assert.assertTrue("Max batch size should be 25", 25 == target.getMaxBatchSize());
    }


    @Test
    public void testConstructor() {
        String[] createdFieldArr = {Constants.NAME};
        String[] updatedFieldArr = {Constants.NAME, Constants.DESCRIPTION};
        int maxBatchSize = 12;

        BatchingSyncUpTarget target = new BatchingSyncUpTarget( Arrays.asList(createdFieldArr),  Arrays.asList(updatedFieldArr), maxBatchSize);

        Assert.assertArrayEquals("Wrong createFieldList", createdFieldArr, target.createFieldlist.toArray(new String[0]));
        Assert.assertArrayEquals("Wrong updateFieldList", updatedFieldArr, target.updateFieldlist.toArray(new String[0]));
        Assert.assertEquals("Wrong maxBatchSize", maxBatchSize, target.getMaxBatchSize());
    }


    @Test
    public void testConstructorWithJSON() throws Exception {
        String[] createdFieldArr = {Constants.NAME};
        String[] updatedFieldArr = {Constants.NAME, Constants.DESCRIPTION};
        int maxBatchSize = 12;

        JSONObject targetJson = new JSONObject();
        targetJson.put(SyncTarget.ANDROID_IMPL, BatchingSyncUpTarget.class.getName());
        targetJson.put(SyncUpTarget.CREATE_FIELDLIST, new JSONArray(createdFieldArr));
        targetJson.put(SyncUpTarget.UPDATE_FIELDLIST, new JSONArray(updatedFieldArr));
        targetJson.put(BatchingSyncUpTarget.MAX_BATCH_SIZE, maxBatchSize);

        BatchingSyncUpTarget target = new BatchingSyncUpTarget(targetJson);

        Assert.assertArrayEquals("Wrong createFieldList", createdFieldArr, target.createFieldlist.toArray(new String[0]));
        Assert.assertArrayEquals("Wrong updateFieldList", updatedFieldArr, target.updateFieldlist.toArray(new String[0]));
        Assert.assertEquals("Wrong maxBatchSize", maxBatchSize, target.getMaxBatchSize());
    }


    @Test
    public void testConstructorWithJSONWithoutOptionalFields() throws Exception {
        JSONObject targetJson = new JSONObject();
        targetJson.put(SyncTarget.ANDROID_IMPL, BatchingSyncUpTarget.class.getName());

        BatchingSyncUpTarget target = new BatchingSyncUpTarget(targetJson);

        Assert.assertNull("Wrong createFieldList", target.createFieldlist);
        Assert.assertNull("Wrong updateFieldList", target.updateFieldlist);
        Assert.assertEquals("Wrong maxBatchSize", BatchingSyncUpTarget.MAX_SUB_REQUESTS_COMPOSITE_API, target.getMaxBatchSize());
    }


    @Test
    public void testFromJSON() throws Exception {
        int maxBatchSize = 12;

        JSONObject targetJson = new JSONObject();
        targetJson.put(SyncTarget.ANDROID_IMPL, BatchingSyncUpTarget.class.getName());
        targetJson.put(BatchingSyncUpTarget.MAX_BATCH_SIZE, maxBatchSize);

        SyncUpTarget target = SyncUpTarget.fromJSON(targetJson);

        Assert.assertTrue(target instanceof BatchingSyncUpTarget);
        Assert.assertEquals("Wrong maxBatchSize", maxBatchSize, ((BatchingSyncUpTarget) target).getMaxBatchSize());
    }

    @Test
    public void testToJSON() throws Exception {
        String[] createdFieldArr = {Constants.NAME};
        String[] updatedFieldArr = {Constants.NAME, Constants.DESCRIPTION};
        int maxBatchSize = 12;

        BatchingSyncUpTarget target = new BatchingSyncUpTarget( Arrays.asList(createdFieldArr),  Arrays.asList(updatedFieldArr), maxBatchSize);

        JSONObject expectedTargetJson = new JSONObject();
        expectedTargetJson.put(SyncTarget.ANDROID_IMPL, BatchingSyncUpTarget.class.getName());
        expectedTargetJson.put(SyncUpTarget.ID_FIELD_NAME, Constants.ID);
        expectedTargetJson.put(SyncUpTarget.MODIFICATION_DATE_FIELD_NAME, Constants.LAST_MODIFIED_DATE);
        expectedTargetJson.put(SyncUpTarget.CREATE_FIELDLIST, new JSONArray(createdFieldArr));
        expectedTargetJson.put(SyncUpTarget.UPDATE_FIELDLIST, new JSONArray(updatedFieldArr));
        expectedTargetJson.put(BatchingSyncUpTarget.MAX_BATCH_SIZE, maxBatchSize);

        JSONTestHelper.assertSameJSON("Wrong json", expectedTargetJson, target.asJSON());
    }

    @Test
    public void testBatchingSyncUpTargetIsDefault() throws Exception {
        JSONObject targetJson = new JSONObject();

        SyncUpTarget target = SyncUpTarget.fromJSON(targetJson);

        Assert.assertTrue(target instanceof BatchingSyncUpTarget);
        Assert.assertNull("Wrong createFieldList", target.createFieldlist);
        Assert.assertNull("Wrong updateFieldList", target.updateFieldlist);
        Assert.assertEquals("Wrong maxBatchSize", BatchingSyncUpTarget.MAX_SUB_REQUESTS_COMPOSITE_API, ((BatchingSyncUpTarget) target).getMaxBatchSize());
    }
}