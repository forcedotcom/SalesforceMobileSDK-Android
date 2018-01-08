/*
 * Copyright (c) 2013-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.phonegap;

import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

/**
 * Running javascript tests for smart sync plugin.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class SmartSyncJSTest extends JSTestCase {

    public SmartSyncJSTest() {
        super("SmartSyncTestSuite");
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected int getMaxRuntimeInSecondsForTest(String testName) {
        return 30;
    }

    @Override
    public List<String> getTestNames() {
        return Arrays.asList(new String[] {
                "testStoreCacheInit",
                "testStoreCacheRetrieve",
                "testStoreCacheSave",
                "testStoreCacheSaveAll",
                "testStoreCacheRemove",
                "testStoreCacheFind",
                "testStoreCacheAddLocalFields",
                "testStoreCacheWithGlobalStore",
                "testSObjectTypeDescribe",
                "testSObjectTypeGetMetadata",
                "testSObjectTypeDescribeLayout",
                "testSObjectTypeCacheOnlyMode",
                "testSObjectTypeCacheMerge",
                "testMultiSObjectTypes",
                "testSObjectTypeReset",
                "testSyncRemoteObjectWithCacheCreate",
                "testSyncRemoteObjectWithCacheRead",
                "testSyncRemoteObjectWithCacheUpdate",
                "testSyncRemoteObjectWithCacheDelete",
                "testSyncSObjectWithServerCreate",
                "testSyncSObjectWithServerRead",
                "testSyncSObjectWithServerUpdate",
                "testSyncSObjectWithServerDelete",
                "testSyncSObjectCreate",
                "testSyncSObjectRetrieve",
                "testSyncSObjectUpdate",
                "testSyncSObjectDelete",
                "testSyncSObjectDetectConflictCreate",
                "testSyncSObjectDetectConflictRetrieve",
                "testSyncSObjectDetectConflictUpdate",
                "testSyncSObjectDetectConflictDelete",
                "testSObjectFetch",
                "testSObjectSave",
                "testSObjectDestroy",
                "testSyncApexRestObjectWithServerCreate",
                "testSyncApexRestObjectWithServerRead",
                "testSyncApexRestObjectWithServerUpdate",
                "testSyncApexRestObjectWithServerDelete",
                "testFetchApexRestObjectsFromServer",
                "testFetchSObjectsFromServer",
                "testFetchSObjects",
                "testSObjectCollectionFetch",
                "testSyncDown",
                "testSyncDownToGlobalStore",
                "testSyncDownWithNoOverwrite",
                "testReSync",
                "testRefreshSyncDown",
                "testCleanResyncGhosts",
                "testSyncUpLocallyUpdated",
                "testSyncUpLocallyUpdatedWithGlobalStore",
                "testSyncUpLocallyUpdatedWithNoOverwrite",
                "testSyncUpLocallyDeleted",
                "testSyncUpLocallyDeletedWithNoOverwrite",
                "testSyncUpLocallyCreated",
                "testStoreCacheWithGlobalStoreNamed",
                "testSyncDownToGlobalStoreNamed",
                "testSyncUpLocallyUpdatedWithGlobalStoreNamed",
                "testSyncDownGetSyncDeleteSyncById",
                "testSyncDownGetSyncDeleteSyncByName",
                "testSyncUpGetSyncDeleteSyncById",
                "testSyncUpGetSyncDeleteSyncByName"
            });
    }

    @Test
    public void testStoreCacheInit() {
        runTest("testStoreCacheInit");
    }

    @Test
    public void testStoreCacheRetrieve() {
        runTest("testStoreCacheRetrieve");
    }

    @Test
    public void testStoreCacheSave() {
        runTest("testStoreCacheSave");
    }

    @Test
    public void testStoreCacheSaveAll() {
        runTest("testStoreCacheSaveAll");
    }

    @Test
    public void testStoreCacheRemove() {
        runTest("testStoreCacheRemove");
    }

    @Test
    public void testStoreCacheFind() {
        runTest("testStoreCacheFind");
    }

    @Test
    public void testStoreCacheAddLocalFields() {
        runTest("testStoreCacheAddLocalFields");
    }

    @Test
    public void testStoreCacheWithGlobalStore() {
        runTest("testStoreCacheWithGlobalStore");
    }

    @Test
    public void testSObjectTypeDescribe() {
        runTest("testSObjectTypeDescribe");
    }

    @Test
    public void testSObjectTypeGetMetadata() {
        runTest("testSObjectTypeGetMetadata");
    }

    @Test
    public void testSObjectTypeDescribeLayout() {
        runTest("testSObjectTypeDescribeLayout");
    }

    @Test
    public void testSObjectTypeCacheOnlyMode() {
        runTest("testSObjectTypeCacheOnlyMode");
    }

    @Test
    public void testSObjectTypeCacheMerge() {
        runTest("testSObjectTypeCacheMerge");
    }

    @Test
    public void testMultiSObjectTypes() {
        runTest("testMultiSObjectTypes");
    }

    @Test
    public void testSObjectTypeReset() {
        runTest("testSObjectTypeReset");
    }

    @Test
    public void testSyncRemoteObjectWithCacheCreate() {
        runTest("testSyncRemoteObjectWithCacheCreate");
    }

    @Test
    public void testSyncRemoteObjectWithCacheRead() {
        runTest("testSyncRemoteObjectWithCacheRead");
    }

    @Test
    public void testSyncRemoteObjectWithCacheUpdate() {
        runTest("testSyncRemoteObjectWithCacheUpdate");
    }

    @Test
    public void testSyncRemoteObjectWithCacheDelete() {
        runTest("testSyncRemoteObjectWithCacheDelete");
    }

    @Test
    public void testSyncSObjectWithServerCreate() {
        runTest("testSyncSObjectWithServerCreate");
    }

    @Test
    public void testSyncSObjectWithServerRead() {
        runTest("testSyncSObjectWithServerRead");
    }

    @Test
    public void testSyncSObjectWithServerUpdate() {
        runTest("testSyncSObjectWithServerUpdate");
    }

    @Test
    public void testSyncSObjectWithServerDelete() {
        runTest("testSyncSObjectWithServerDelete");
    }

    @Test
    public void testSyncSObjectCreate() {
        runTest("testSyncSObjectCreate");
    }

    @Test
    public void testSyncSObjectRetrieve() {
        runTest("testSyncSObjectRetrieve");
    }

    @Test
    public void testSyncSObjectUpdate() {
        runTest("testSyncSObjectUpdate");
    }

    @Test
    public void testSyncSObjectDelete() {
        runTest("testSyncSObjectDelete");
    }

    @Test
    public void testSyncSObjectDetectConflictCreate() {
        runTest("testSyncSObjectDetectConflictCreate");
    }

    @Test
    public void testSyncSObjectDetectConflictRetrieve() {
        runTest("testSyncSObjectDetectConflictRetrieve");
    }

    @Test
    public void testSyncSObjectDetectConflictUpdate() {
        runTest("testSyncSObjectDetectConflictUpdate");
    }

    @Test
    public void testSyncSObjectDetectConflictDelete() {
        runTest("testSyncSObjectDetectConflictDelete");
    }

    @Test
    public void testSObjectFetch() {
        runTest("testSObjectFetch");
    }

    @Test
    public void testSObjectSave() {
        runTest("testSObjectSave");
    }

    @Test
    public void testSObjectDestroy() {
        runTest("testSObjectDestroy");
    }

    @Test
    public void testSyncApexRestObjectWithServerCreate() {
        runTest("testSyncApexRestObjectWithServerCreate");
    }

    @Test
    public void testSyncApexRestObjectWithServerRead() {
        runTest("testSyncApexRestObjectWithServerRead");
    }

    @Test
    public void testSyncApexRestObjectWithServerUpdate() {
        runTest("testSyncApexRestObjectWithServerUpdate");
    }

    @Test
    public void testSyncApexRestObjectWithServerDelete() {
        runTest("testSyncApexRestObjectWithServerDelete");
    }

    @Test
    public void testFetchApexRestObjectsFromServer() {
        runTest("testFetchApexRestObjectsFromServer");
    }

    @Test
    public void testFetchSObjectsFromServer() {
        runTest("testFetchSObjectsFromServer");
    }

    @Test
    public void testFetchSObjects() {
        runTest("testFetchSObjects");
    }

    @Test
    public void testSObjectCollectionFetch() {
        runTest("testSObjectCollectionFetch");
    }

    @Test
    public void testSyncDown() {
        runTest("testSyncDown");
    }

    @Test
    public void testSyncDownToGlobalStore() {
        runTest("testSyncDownToGlobalStore");
    }

    @Test
    public void testSyncDownWithNoOverwrite() {
        runTest("testSyncDownWithNoOverwrite");
    }

    @Test
    public void testReSync() {
        runTest("testReSync");
    }

    @Test
    public void testRefreshSyncDown() {
        runTest("testRefreshSyncDown");
    }

    @Test
    public void testCleanResyncGhosts() {
        runTest("testCleanResyncGhosts");
    }

    @Test
    public void testSyncUpLocallyUpdated() {
        runTest("testSyncUpLocallyUpdated");
    }

    @Test
    public void testSyncUpLocallyUpdatedWithGlobalStore() {
        runTest("testSyncUpLocallyUpdatedWithGlobalStore");
    }

    @Test
    public void testSyncUpLocallyUpdatedWithNoOverwrite() {
        runTest("testSyncUpLocallyUpdatedWithNoOverwrite");
    }

    @Test
    public void testSyncUpLocallyDeleted() {
        runTest("testSyncUpLocallyDeleted");
    }

    @Test
    public void testSyncUpLocallyDeletedWithNoOverwrite() {
        runTest("testSyncUpLocallyDeletedWithNoOverwrite");
    }

    @Test
    public void testSyncUpLocallyCreated() {
        runTest("testSyncUpLocallyCreated");
    }

    @Test
    public void testStoreCacheWithGlobalStoreNamed() {
        runTest("testStoreCacheWithGlobalStoreNamed");
    }

    @Test
    public void testSyncDownToGlobalStoreNamed() {
        runTest("testSyncDownToGlobalStoreNamed");
    }

    @Test
    public void testSyncUpLocallyUpdatedWithGlobalStoreNamed() {
        runTest("testSyncUpLocallyUpdatedWithGlobalStoreNamed");
    }

    @Test
    public void testSyncDownGetSyncDeleteSyncById() {
        runTest("testSyncDownGetSyncDeleteSyncById");
    }

    @Test
    public void testSyncDownGetSyncDeleteSyncByName() {
        runTest("testSyncDownGetSyncDeleteSyncByName");
    }

    @Test
    public void testSyncUpGetSyncDeleteSyncById() {
        runTest("testSyncUpGetSyncDeleteSyncById");
    }

    @Test
    public void testSyncUpGetSyncDeleteSyncByName() {
        runTest("testSyncUpGetSyncDeleteSyncByName");
    }
}
