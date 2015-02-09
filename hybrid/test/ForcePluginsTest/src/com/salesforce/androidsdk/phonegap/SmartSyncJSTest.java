/*
 * Copyright (c) 2013-2015, salesforce.com, inc.
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

import java.util.Arrays;
import java.util.List;

import com.salesforce.androidsdk.util.test.JSTestCase;


/**
 * Running javascript tests for smart sync library
 */
public class SmartSyncJSTest extends JSTestCase {

    public SmartSyncJSTest() {
        super("SmartSyncTestSuite");
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
                "testSyncDownWithNoOverwrite",
                "testReSync",
				"testSyncUpLocallyUpdated",
				"testSyncUpLocallyUpdatedWithNoOverwrite",
				"testSyncUpLocallyDeleted",
				"testSyncUpLocallyDeletedWithNoOverwrite",
				"testSyncUpLocallyCreated"
				});
	}
    
    public void testStoreCacheInit() {
        runTest("testStoreCacheInit");
    }

    public void testStoreCacheRetrieve() {
        runTest("testStoreCacheRetrieve");
    }

    public void testStoreCacheSave() {
        runTest("testStoreCacheSave");
    }

    public void testStoreCacheSaveAll() {
        runTest("testStoreCacheSaveAll");
    }

    public void testStoreCacheRemove() {
        runTest("testStoreCacheRemove");
    }

    public void testStoreCacheFind() {
        runTest("testStoreCacheFind");
    }

    public void testStoreCacheAddLocalFields() {
        runTest("testStoreCacheAddLocalFields");
    }

    public void testSObjectTypeDescribe() {
        runTest("testSObjectTypeDescribe");
    }

    public void testSObjectTypeGetMetadata() {
        runTest("testSObjectTypeGetMetadata");
    }

    public void testSObjectTypeDescribeLayout() {
        runTest("testSObjectTypeDescribeLayout");
    }

    public void testSObjectTypeCacheOnlyMode() {
        runTest("testSObjectTypeCacheOnlyMode");
    }

    public void testSObjectTypeCacheMerge() {
        runTest("testSObjectTypeCacheMerge");
    }

    public void testMultiSObjectTypes() {
        runTest("testMultiSObjectTypes");
    }

    public void testSObjectTypeReset() {
        runTest("testSObjectTypeReset");
    }

    public void testSyncRemoteObjectWithCacheCreate() {
        runTest("testSyncRemoteObjectWithCacheCreate");
    }

    public void testSyncRemoteObjectWithCacheRead() {
        runTest("testSyncRemoteObjectWithCacheRead");
    }

    public void testSyncRemoteObjectWithCacheUpdate() {
        runTest("testSyncRemoteObjectWithCacheUpdate");
    }

    public void testSyncRemoteObjectWithCacheDelete() {
        runTest("testSyncRemoteObjectWithCacheDelete");
    }

    public void testSyncSObjectWithServerCreate() {
        runTest("testSyncSObjectWithServerCreate");
    }

    public void testSyncSObjectWithServerRead() {
        runTest("testSyncSObjectWithServerRead");
    }

    public void testSyncSObjectWithServerUpdate() {
        runTest("testSyncSObjectWithServerUpdate");
    }

    public void testSyncSObjectWithServerDelete() {
        runTest("testSyncSObjectWithServerDelete");
    }

    public void testSyncSObjectCreate() {
        runTest("testSyncSObjectCreate");
    }

    public void testSyncSObjectRetrieve() {
        runTest("testSyncSObjectRetrieve");
    }

    public void testSyncSObjectUpdate() {
        runTest("testSyncSObjectUpdate");
    }

    public void testSyncSObjectDelete() {
        runTest("testSyncSObjectDelete");
    }

    public void testSyncSObjectDetectConflictCreate() {
        runTest("testSyncSObjectDetectConflictCreate");
    }

    public void testSyncSObjectDetectConflictRetrieve() {
        runTest("testSyncSObjectDetectConflictRetrieve");
    }

    public void testSyncSObjectDetectConflictUpdate() {
        runTest("testSyncSObjectDetectConflictUpdate");
    }

    public void testSyncSObjectDetectConflictDelete() {
        runTest("testSyncSObjectDetectConflictDelete");
    }

    public void testSObjectFetch() {
        runTest("testSObjectFetch");
    }

    public void testSObjectSave() {
        runTest("testSObjectSave");
    }

    public void testSObjectDestroy() {
        runTest("testSObjectDestroy");
    }

    public void testSyncApexRestObjectWithServerCreate() {
        runTest("testSyncApexRestObjectWithServerCreate");
    }

    public void testSyncApexRestObjectWithServerRead() {
        runTest("testSyncApexRestObjectWithServerRead");
    }

    public void testSyncApexRestObjectWithServerUpdate() {
        runTest("testSyncApexRestObjectWithServerUpdate");
    }

    public void testSyncApexRestObjectWithServerDelete() {
        runTest("testSyncApexRestObjectWithServerDelete");
    }

    public void testFetchApexRestObjectsFromServer() {
        runTest("testFetchApexRestObjectsFromServer");
    }

    public void testFetchSObjectsFromServer() {
        runTest("testFetchSObjectsFromServer");
    }

    public void testFetchSObjects() {
        runTest("testFetchSObjects");
    }

    public void testSObjectCollectionFetch() {
        runTest("testSObjectCollectionFetch");
    }

    public void testSyncDown() {
        runTest("testSyncDown");
    }

    public void testSyncDownWithNoOverwrite() {
        runTest("testSyncDownWithNoOverwrite");
    }

    public void testReSync() {
        runTest("testReSync");
    }

    public void testSyncUpLocallyUpdated() {
        runTest("testSyncUpLocallyUpdated");
    }

    public void testSyncUpLocallyUpdatedWithNoOverwrite() {
        runTest("testSyncUpLocallyUpdatedWithNoOverwrite");
    }

    public void testSyncUpLocallyDeleted() {
        runTest("testSyncUpLocallyDeleted");
    }

    public void testSyncUpLocallyDeletedWithNoOverwrite() {
        runTest("testSyncUpLocallyDeletedWithNoOverwrite");
    }

    public void testSyncUpLocallyCreated() {
        runTest("testSyncUpLocallyCreated");
    }
}
