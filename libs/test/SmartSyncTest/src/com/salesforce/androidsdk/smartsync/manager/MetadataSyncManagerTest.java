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
package com.salesforce.androidsdk.smartsync.manager;

import androidx.test.filters.MediumTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartsync.model.Metadata;
import com.salesforce.androidsdk.smartsync.util.Constants;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link MetadataSyncManager}.
 *
 * @author bhariharan
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class MetadataSyncManagerTest extends ManagerTestCase {

    private static final String ACCOUNT = "Account";
    private static final String ACCOUNT_KEY_PREFIX = "001";

    private MetadataSyncManager metadataSyncManager;
    private MetadataSyncCallbackQueue metadataSyncCallbackQueue;

    private static class MetadataSyncCallbackQueue implements MetadataSyncManager.MetadataSyncCallback {

        private BlockingQueue<Metadata> results;

        public MetadataSyncCallbackQueue() {
            results = new ArrayBlockingQueue<>(1);
        }

        @Override
        public void onSyncComplete(Metadata metadata) {
            results.offer(metadata);
        }

        public void clearQueue() {
            results.clear();
        }

        public Metadata getResult() {
            try {
                final Metadata result = results.poll(30, TimeUnit.SECONDS);
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
        metadataSyncManager = MetadataSyncManager.getInstance();
        metadataSyncCallbackQueue = new MetadataSyncCallbackQueue();
    }

    @After
    public void tearDown() throws Exception {
        SyncManager.reset();
        metadataSyncManager.getSmartStore().dropAllSoups();
        MetadataSyncManager.reset();
        metadataSyncCallbackQueue.clearQueue();
        metadataSyncCallbackQueue = null;
        super.tearDown();
    }

    /**
     * Test for fetching metadata in CACHE_ONLY mode.
     */
    @Test
    public void testFetchMetadataInCacheOnlyMode() {
        metadataSyncManager.fetchMetadata(ACCOUNT, Constants.Mode.SERVER_FIRST,
                metadataSyncCallbackQueue);
        metadataSyncCallbackQueue.getResult();
        metadataSyncCallbackQueue.clearQueue();
        metadataSyncManager.fetchMetadata(ACCOUNT, Constants.Mode.CACHE_ONLY,
                metadataSyncCallbackQueue);
        validateResult(metadataSyncCallbackQueue.getResult());
    }

    /**
     * Test for fetching metadata in CACHE_FIRST mode with a hydrated cache.
     */
    @Test
    public void testFetchMetadataInCacheFirstModeWithCacheData() {
        metadataSyncManager.fetchMetadata(ACCOUNT, Constants.Mode.SERVER_FIRST,
                metadataSyncCallbackQueue);
        metadataSyncCallbackQueue.getResult();
        metadataSyncCallbackQueue.clearQueue();
        metadataSyncManager.fetchMetadata(ACCOUNT, Constants.Mode.CACHE_FIRST,
                metadataSyncCallbackQueue);
        validateResult(metadataSyncCallbackQueue.getResult());
    }

    /**
     * Test for fetching metadata in CACHE_FIRST mode with an empty cache.
     */
    @Test
    public void testFetchMetadataInCacheFirstModeWithoutCacheData() {
        metadataSyncManager.fetchMetadata(ACCOUNT, Constants.Mode.CACHE_FIRST,
                metadataSyncCallbackQueue);
        validateResult(metadataSyncCallbackQueue.getResult());
    }

    /**
     * Test for fetching metadata in SERVER_FIRST mode.
     */
    @Test
    public void testFetchMetadataInServerFirstMode() {
        metadataSyncManager.fetchMetadata(ACCOUNT, Constants.Mode.SERVER_FIRST,
                metadataSyncCallbackQueue);
        validateResult(metadataSyncCallbackQueue.getResult());
    }

    /**
     * Test for fetching metadata multiple times and ensuring only 1 row is created.
     */
    @Test
    public void testFetchMetadataMultipleTimes() {
        metadataSyncManager.fetchMetadata(ACCOUNT, Constants.Mode.SERVER_FIRST,
                metadataSyncCallbackQueue);
        validateResult(metadataSyncCallbackQueue.getResult());
        metadataSyncManager.fetchMetadata(ACCOUNT, Constants.Mode.SERVER_FIRST,
                metadataSyncCallbackQueue);
        validateResult(metadataSyncCallbackQueue.getResult());
        final QuerySpec querySpec = QuerySpec.buildSmartQuerySpec(String.format(MetadataSyncManager.QUERY,
                ACCOUNT), 2);
        int numRows = metadataSyncManager.getSmartStore().countQuery(querySpec);
        Assert.assertEquals("Number of rows should be 1", 1, numRows);
    }

    private void validateResult(Metadata metadata) {
        Assert.assertNotNull("Metadata should not be null", metadata);
        Assert.assertEquals("Object types should match", ACCOUNT, metadata.getName());
        Assert.assertNotNull("Metadata raw data should not be null", metadata.getRawData());
        Assert.assertTrue("Object should be compact layoutable", metadata.isCompactLayoutable());
        Assert.assertTrue("Object should be createable", metadata.isCreateable());
        Assert.assertNotNull("Child relationships should not be null", metadata.getChildRelationships());
        Assert.assertNotNull("Fields should not be null", metadata.getFields());
        Assert.assertNotNull("URLs should not be null", metadata.getUrls());
        Assert.assertTrue("Object should be searchable", metadata.isSearchable());
        Assert.assertEquals("Object key prefixes should match", ACCOUNT_KEY_PREFIX, metadata.getKeyPrefix());
        Assert.assertEquals("Object labels should match", ACCOUNT, metadata.getLabel());
    }
}
