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
package com.salesforce.androidsdk.mobilesync.util;

import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager;
import com.salesforce.androidsdk.mobilesync.target.SoqlSyncDownTarget;
import com.salesforce.androidsdk.mobilesync.target.SyncUpTarget;
import com.salesforce.androidsdk.mobilesync.util.SyncState.MergeMode;

import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import androidx.test.ext.junit.runners.AndroidJUnit4;

/**
 * Test class for SyncState.
 */
@RunWith(AndroidJUnit4.class)
public class SyncStateTest {

    private static final String DB_NAME = "testDb";
    private SmartStore store;

    @Before
    public void setUp() {
        store = MobileSyncSDKManager.getInstance().getGlobalSmartStore(DB_NAME);
    }

    @After
    public void tearDown() {
        MobileSyncSDKManager.getInstance().removeGlobalSmartStore(DB_NAME);
    }


    /**
     * Make sure syncs soup gets properly setup the first time around
     * @throws JSONException
     */
    @Test
    public void testSetupSyncsSoupFirstTime() throws JSONException  {
        // Setup syncs soup
        SyncState.setupSyncsSoupIfNeeded(store);

        // Check the soup
        checkSyncsSoupIndexSpecs(store);
    }

    /**
     * Make sure syncs soup gets properly setup when upgrading to 7.1
     * @throws JSONException
     */
    @Test
    public void testSetupSyncsSoupUpgradeTo71() throws JSONException {
        // Manually syncs soup the pre 7.1 way
        final IndexSpec[] indexSpecs = {
                new IndexSpec(SyncState.SYNC_TYPE, SmartStore.Type.string),
                new IndexSpec(SyncState.SYNC_NAME, SmartStore.Type.string),
        };
        store.registerSoup(SyncState.SYNCS_SOUP, indexSpecs);

        // Fix syncs soup
        SyncState.setupSyncsSoupIfNeeded(store);

        // Check the soup
        checkSyncsSoupIndexSpecs(store);
    }

    /**
     * Make sure syncs marked as running are "cleaned up" after restart
     * @throws JSONException
     */
    @Test
    public void testCleanupSyncsSoupIfNeeded() throws JSONException  {
        // Setup syncs soup
        SyncState.setupSyncsSoupIfNeeded(store);

        // Create syncs - some in the running state
        createSyncChangeStatus("newSyncUp", true, SyncState.Status.NEW);
        createSyncChangeStatus("stoppedSyncUp", true, SyncState.Status.STOPPED);
        createSyncChangeStatus("runningSyncUp", true, SyncState.Status.RUNNING);
        createSyncChangeStatus("failedSyncUp", true, SyncState.Status.FAILED);
        createSyncChangeStatus("doneSyncUp", true, SyncState.Status.DONE);
        createSyncChangeStatus("newSyncDown", false, SyncState.Status.NEW);
        createSyncChangeStatus("stoppedSyncDown", false, SyncState.Status.STOPPED);
        createSyncChangeStatus("runningSyncDown", false, SyncState.Status.RUNNING);
        createSyncChangeStatus("failedSyncDown", false, SyncState.Status.FAILED);
        createSyncChangeStatus("doneSyncDown", false, SyncState.Status.DONE);


        // Cleanup syncs soup
        SyncState.cleanupSyncsSoupIfNeeded(store);

        // Check the syncs
        checkSyncStatus("newSyncUp", SyncState.Status.NEW);
        checkSyncStatus("stoppedSyncUp", SyncState.Status.STOPPED);
        checkSyncStatus("runningSyncUp", SyncState.Status.STOPPED);
        checkSyncStatus("failedSyncUp", SyncState.Status.FAILED);
        checkSyncStatus("doneSyncUp", SyncState.Status.DONE);
        checkSyncStatus("newSyncDown", SyncState.Status.NEW);
        checkSyncStatus("stoppedSyncDown", SyncState.Status.STOPPED);
        checkSyncStatus("runningSyncDown", SyncState.Status.STOPPED);
        checkSyncStatus("failedSyncDown", SyncState.Status.FAILED);
        checkSyncStatus("doneSyncDown", SyncState.Status.DONE);
    }

    private void createSyncChangeStatus(String name, boolean isSyncUp, SyncState.Status status) throws JSONException  {

        SyncState sync;
        if (isSyncUp) {
            sync = SyncState.createSyncUp(store, new SyncUpTarget(), SyncOptions.optionsForSyncUp(Collections.singletonList("Name")), "Accounts", name);
        } else {
            sync = SyncState.createSyncDown(store, new SoqlSyncDownTarget("SELECT Id, Name from Account"), SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED), "Accounts", name);
        }

        sync.setStatus(status);
        sync.save(store);
    }

    private void checkSyncStatus(String name, SyncState.Status expectedStatus) throws JSONException {
        SyncState sync = SyncState.byName(store, name);
        Assert.assertEquals("Wrong status for " + name, expectedStatus, sync.getStatus());
    }

    /**
     * Check syncs soup index specs
     */
    private void checkSyncsSoupIndexSpecs(SmartStore store) {
        IndexSpec[] indexSpecs = store.getSoupIndexSpecs(SyncState.SYNCS_SOUP);
        Assert.assertEquals("Wrong number of index specs", 3, indexSpecs.length);
        List<String> expectedPaths = Arrays.asList(new String[] { SyncState.SYNC_NAME, SyncState.SYNC_TYPE, SyncState.SYNC_STATUS });
        for (IndexSpec indexSpec : indexSpecs) {
            Assert.assertTrue("Wrong index spec path", expectedPaths.contains(indexSpec.path));
            Assert.assertEquals("Wrong index spec type", SmartStore.Type.json1, indexSpec.type);
        }
    }
}
