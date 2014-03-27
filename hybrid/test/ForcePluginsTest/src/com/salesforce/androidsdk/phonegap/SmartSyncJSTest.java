/*
 * Copyright (c) 2013, salesforce.com, inc.
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

import com.salesforce.androidsdk.util.JSTestCase;


/**
 * Running javascript tests for smart sync library
 */
public class SmartSyncJSTest extends JSTestCase {

    public SmartSyncJSTest() {
        super("SmartSyncTestSuite");
    }
    
    @Override
    public List<String> getTestNames() {
		return Arrays.asList(new String[] { "testSObjectCollectionFetch",
				"testFetchSObjects", "testFetchSObjectsFromServer",
				"testSObjectDestroy", "testSObjectFetch", "testSObjectSave",
				"testSObjectTypeDescribe", "testSObjectTypeGetMetadata",
				"testSObjectTypeDescribeLayout", "testSObjectTypeCacheMerge",
				"testMultiSObjectTypes", "testSObjectTypeReset",
				"testStoreCacheAddLocalFields", "testStoreCacheFind",
				"testStoreCacheInit", "testStoreCacheRemove",
				"testStoreCacheRetrieve", "testStoreCacheSave",
				"testStoreCacheSaveAll", "testSyncSObjectCreate",
				"testSyncSObjectDelete", "testSyncSObjectDetectConflictCreate",
				"testSyncSObjectDetectConflictDelete",
				"testSyncSObjectDetectConflictRetrieve",
				"testSyncSObjectDetectConflictUpdate",
				"testSyncSObjectRetrieve", "testSyncSObjectUpdate",
				"testSyncRemoteObjectWithCacheCreate",
				"testSyncRemoteObjectWithCacheDelete",
				"testSyncRemoteObjectWithCacheRead",
				"testSyncRemoteObjectWithCacheUpdate",
				"testSyncSObjectWithServerCreate",
				"testSyncSObjectWithServerDelete",
				"testSyncSObjectWithServerRead",
				"testSyncSObjectWithServerUpdate" });
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

    public void testFetchSObjectsFromServer() {
        runTest("testFetchSObjectsFromServer");
    }

    public void testFetchSObjects() {
        runTest("testFetchSObjects");
    }

    public void testCollectionFetch() {
        runTest("testSObjectCollectionFetch");
    }
}
