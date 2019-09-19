/*
 * Copyright (c) 2018-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.mobilesync.manager;

import androidx.test.filters.MediumTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.mobilesync.model.Layout;
import com.salesforce.androidsdk.mobilesync.util.Constants;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link LayoutSyncManager}.
 *
 * @author bhariharan
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class LayoutSyncManagerTest extends ManagerTestCase {

    private static final String COMPACT = "Compact";
    private static final String ACCOUNT = "Account";

    private LayoutSyncManager layoutSyncManager;
    private LayoutSyncCallbackQueue layoutSyncCallbackQueue;

    private static class LayoutSyncCallbackQueue implements LayoutSyncManager.LayoutSyncCallback {

        private static class Result {

            public String objectType;
            public Layout layout;

            public Result(String objectType, Layout layout) {
                this.objectType = objectType;
                this.layout = layout;
            }
        }

        private BlockingQueue<Result> results;

        public LayoutSyncCallbackQueue() {
            results = new ArrayBlockingQueue<>(1);
        }

        @Override
        public void onSyncComplete(String objectType, Layout layout) {
            if (objectType != null) {
                results.offer(new Result(objectType, layout));
            }
        }

        public void clearQueue() {
            results.clear();
        }

        public Result getResult() {
            try {
                final Result result = results.poll(30, TimeUnit.SECONDS);
                if (result == null) {
                    throw new RuntimeException("Timed out waiting for callback");
                }
                return result;
            } catch (InterruptedException ex) {
                throw new RuntimeException("Interrupted waiting for callback");
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        layoutSyncManager = LayoutSyncManager.getInstance();
        layoutSyncCallbackQueue = new LayoutSyncCallbackQueue();
    }

    @After
    public void tearDown() throws Exception {
        SyncManager.reset();
        layoutSyncManager.getSmartStore().dropAllSoups();
        LayoutSyncManager.reset();
        layoutSyncCallbackQueue.clearQueue();
        layoutSyncCallbackQueue = null;
        super.tearDown();
    }

    /**
     * Test for fetching layout in CACHE_ONLY mode.
     */
    @Test
    public void testFetchLayoutInCacheOnlyMode() {
        layoutSyncManager.fetchLayout(ACCOUNT, COMPACT, Constants.Mode.SERVER_FIRST,
                layoutSyncCallbackQueue);
        layoutSyncCallbackQueue.getResult();
        layoutSyncCallbackQueue.clearQueue();
        layoutSyncManager.fetchLayout(ACCOUNT, COMPACT, Constants.Mode.CACHE_ONLY,
                layoutSyncCallbackQueue);
        validateResult(layoutSyncCallbackQueue.getResult());
    }

    /**
     * Test for fetching layout in CACHE_FIRST mode with a hydrated cache.
     */
    @Test
    public void testFetchLayoutInCacheFirstModeWithCacheData() {
        layoutSyncManager.fetchLayout(ACCOUNT, COMPACT, Constants.Mode.SERVER_FIRST,
                layoutSyncCallbackQueue);
        layoutSyncCallbackQueue.getResult();
        layoutSyncCallbackQueue.clearQueue();
        layoutSyncManager.fetchLayout(ACCOUNT, COMPACT, Constants.Mode.CACHE_FIRST,
                layoutSyncCallbackQueue);
        validateResult(layoutSyncCallbackQueue.getResult());
    }

    /**
     * Test for fetching layout in CACHE_FIRST mode with an empty cache.
     */
    @Test
    public void testFetchLayoutInCacheFirstModeWithoutCacheData() {
        layoutSyncManager.fetchLayout(ACCOUNT, COMPACT, Constants.Mode.CACHE_FIRST,
                layoutSyncCallbackQueue);
        validateResult(layoutSyncCallbackQueue.getResult());
    }

    /**
     * Test for fetching layout in SERVER_FIRST mode.
     */
    @Test
    public void testFetchLayoutInServerFirstMode() {
        layoutSyncManager.fetchLayout(ACCOUNT, COMPACT, Constants.Mode.SERVER_FIRST,
                layoutSyncCallbackQueue);
        validateResult(layoutSyncCallbackQueue.getResult());
    }

    /**
     * Test for fetching layout multiple times and ensuring only 1 row is created.
     */
    @Test
    public void testFetchLayoutMultipleTimes() {
        layoutSyncManager.fetchLayout(ACCOUNT, COMPACT, Constants.Mode.SERVER_FIRST,
                layoutSyncCallbackQueue);
        validateResult(layoutSyncCallbackQueue.getResult());
        layoutSyncManager.fetchLayout(ACCOUNT, COMPACT, Constants.Mode.SERVER_FIRST,
                layoutSyncCallbackQueue);
        validateResult(layoutSyncCallbackQueue.getResult());
        final QuerySpec querySpec = QuerySpec.buildSmartQuerySpec(String.format(LayoutSyncManager.QUERY,
                ACCOUNT, COMPACT), 2);
        int numRows = layoutSyncManager.getSmartStore().countQuery(querySpec);
        Assert.assertEquals("Number of rows should be 1", 1, numRows);
    }

    private void validateResult(LayoutSyncCallbackQueue.Result result) {
        final String objectType = result.objectType;
        final Layout layout = result.layout;
        Assert.assertEquals("Object types should match", ACCOUNT, objectType);
        Assert.assertNotNull("Layout data should not be null", layout);
        Assert.assertEquals("Layout types should match", COMPACT, layout.getLayoutType());
        Assert.assertNotNull("Layout raw data should not be null", layout.getRawData());
        Assert.assertNotNull("Layout sections should not be null", layout.getSections());
        Assert.assertTrue("Number of layout sections should be 1 or more",
                layout.getSections().size() > 0);
        Assert.assertNotNull("Layout rows for a section should not be null",
                layout.getSections().get(0).getLayoutRows());
        Assert.assertTrue("Number of layout rows for a section should be 1 or more",
                layout.getSections().get(0).getLayoutRows().size() > 0);
        Assert.assertNotNull("Layout items for a row should not be null",
                layout.getSections().get(0).getLayoutRows().get(0).getLayoutItems());
        Assert.assertTrue("Number of layout items for a row should be 1 or more",
                layout.getSections().get(0).getLayoutRows().get(0).getLayoutItems().size() > 0);
    }
}
