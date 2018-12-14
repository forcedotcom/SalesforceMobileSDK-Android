/*
 * Copyright (c) 2017-present, salesforce.com, inc.
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

package com.salesforce.androidsdk.smartsync.config;

import androidx.test.filters.SmallTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.salesforce.androidsdk.smartsync.app.SmartSyncSDKManager;
import com.salesforce.androidsdk.smartsync.manager.SyncManagerTestCase;
import com.salesforce.androidsdk.smartsync.target.SoqlSyncDownTarget;
import com.salesforce.androidsdk.smartsync.target.SyncUpTarget;
import com.salesforce.androidsdk.smartsync.util.SyncOptions;
import com.salesforce.androidsdk.smartsync.util.SyncState;

import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class SyncsConfigTest extends SyncManagerTestCase {

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testSetupGlobalSyncsFromDefaultConfig() throws JSONException {
        Assert.assertFalse(globalSyncManager.hasSyncWithName("globalSync1"));
        Assert.assertFalse(globalSyncManager.hasSyncWithName("globalSync2"));

        // Setting up syncs
        SmartSyncSDKManager.getInstance().setupGlobalSyncsFromDefaultConfig();

        // Checking smartstore
        Assert.assertTrue(globalSyncManager.hasSyncWithName("globalSync1"));
        Assert.assertTrue(globalSyncManager.hasSyncWithName("globalSync2"));

        // Checking first sync in details
        SyncState actualSync1 = globalSyncManager.getSyncStatus("globalSync1");
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, actualSync1.getSoupName());
        checkStatus(actualSync1, SyncState.Type.syncDown, actualSync1.getId(), new SoqlSyncDownTarget("SELECT Id, Name, LastModifiedDate FROM Account"), SyncOptions.optionsForSyncDown(SyncState.MergeMode.OVERWRITE), SyncState.Status.NEW, 0);

        // Checking second sync in details
        SyncState actualSync2 = globalSyncManager.getSyncStatus("globalSync2");
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, actualSync2.getSoupName());
        checkStatus(actualSync2, SyncState.Type.syncUp, actualSync2.getId(),
                new SyncUpTarget(Arrays.asList(new String[]{"Name"}), null),
                SyncOptions.optionsForSyncUp(Arrays.asList(new String[]{"Id", "Name", "LastModifiedDate"}), SyncState.MergeMode.LEAVE_IF_CHANGED),
                SyncState.Status.NEW, 0);
    }

    @Test
    public void testSetupUserSyncsFromDefaultConfig() throws JSONException {
        Assert.assertFalse(syncManager.hasSyncWithName("userSync1"));
        Assert.assertFalse(syncManager.hasSyncWithName("userSync2"));

        // Setting up syncs
        SmartSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig();

        // Checking smartstore
        Assert.assertTrue(syncManager.hasSyncWithName("userSync1"));
        Assert.assertTrue(syncManager.hasSyncWithName("userSync2"));

        // Checking first sync in details
        SyncState actualSync1 = syncManager.getSyncStatus("userSync1");
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, actualSync1.getSoupName());
        checkStatus(actualSync1, SyncState.Type.syncDown, actualSync1.getId(), new SoqlSyncDownTarget("SELECT Id, Name, LastModifiedDate FROM Account"), SyncOptions.optionsForSyncDown(SyncState.MergeMode.OVERWRITE), SyncState.Status.NEW, 0);

        // Checking second sync in details
        SyncState actualSync2 = syncManager.getSyncStatus("userSync2");
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, actualSync2.getSoupName());
        checkStatus(actualSync2, SyncState.Type.syncUp, actualSync2.getId(),
                new SyncUpTarget(Arrays.asList(new String[]{"Name"}), null),
                SyncOptions.optionsForSyncUp(Arrays.asList(new String[]{"Id", "Name", "LastModifiedDate"}), SyncState.MergeMode.LEAVE_IF_CHANGED),
                SyncState.Status.NEW, 0);
    }
}
