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

package com.salesforce.androidsdk.mobilesync.config;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager;
import com.salesforce.androidsdk.mobilesync.manager.SyncManagerTestCase;
import com.salesforce.androidsdk.mobilesync.target.BatchSyncUpTarget;
import com.salesforce.androidsdk.mobilesync.target.BriefcaseSyncDownTarget;
import com.salesforce.androidsdk.mobilesync.target.CollectionSyncUpTarget;
import com.salesforce.androidsdk.mobilesync.target.LayoutSyncDownTarget;
import com.salesforce.androidsdk.mobilesync.target.MetadataSyncDownTarget;
import com.salesforce.androidsdk.mobilesync.target.MruSyncDownTarget;
import com.salesforce.androidsdk.mobilesync.target.ParentChildrenSyncDownTarget;
import com.salesforce.androidsdk.mobilesync.target.ParentChildrenSyncTargetHelper;
import com.salesforce.androidsdk.mobilesync.target.ParentChildrenSyncUpTarget;
import com.salesforce.androidsdk.mobilesync.target.RefreshSyncDownTarget;
import com.salesforce.androidsdk.mobilesync.target.SoqlSyncDownTarget;
import com.salesforce.androidsdk.mobilesync.target.SoslSyncDownTarget;
import com.salesforce.androidsdk.mobilesync.target.SyncUpTarget;
import com.salesforce.androidsdk.mobilesync.util.BriefcaseObjectInfo;
import com.salesforce.androidsdk.mobilesync.util.ChildrenInfo;
import com.salesforce.androidsdk.mobilesync.util.ParentInfo;
import com.salesforce.androidsdk.mobilesync.util.SyncOptions;
import com.salesforce.androidsdk.mobilesync.util.SyncState;
import com.salesforce.androidsdk.mobilesync.util.SyncState.MergeMode;
import java.util.Arrays;
import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

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
        MobileSyncSDKManager.getInstance().setupGlobalSyncsFromDefaultConfig();

        // Checking smartstore
        Assert.assertTrue(globalSyncManager.hasSyncWithName("globalSync1"));
        Assert.assertTrue(globalSyncManager.hasSyncWithName("globalSync2"));

        // Checking first sync in details
        SyncState actualSync1 = globalSyncManager.getSyncStatus("globalSync1");
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, actualSync1.getSoupName());
        checkStatus(actualSync1, SyncState.Type.syncDown, actualSync1.getId(), new SoqlSyncDownTarget("SELECT Id, Name, LastModifiedDate FROM Account"), SyncOptions.optionsForSyncDown(MergeMode.OVERWRITE), SyncState.Status.NEW, 0);

        // Checking second sync in details
        SyncState actualSync2 = globalSyncManager.getSyncStatus("globalSync2");
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, actualSync2.getSoupName());
        checkStatus(actualSync2, SyncState.Type.syncUp, actualSync2.getId(),
                new CollectionSyncUpTarget(Arrays.asList("Name"), null),
                SyncOptions.optionsForSyncUp(Arrays.asList("Id", "Name", "LastModifiedDate"), MergeMode.LEAVE_IF_CHANGED),
                SyncState.Status.NEW, 0);
    }

    @Test
    public void testSetupUserSyncsFromDefaultConfig() {
        Assert.assertFalse(syncManager.hasSyncWithName("soqlSyncDown"));
        Assert.assertFalse(syncManager.hasSyncWithName("soslSyncDown"));
        Assert.assertFalse(syncManager.hasSyncWithName("mruSyncDown"));
        Assert.assertFalse(syncManager.hasSyncWithName("refreshSyncDown"));
        Assert.assertFalse(syncManager.hasSyncWithName("layoutSyncDown"));
        Assert.assertFalse(syncManager.hasSyncWithName("metadataSyncDown"));
        Assert.assertFalse(syncManager.hasSyncWithName("parentChildrenSyncDown"));
        Assert.assertFalse(syncManager.hasSyncWithName("briefcaseSyncDown"));
        Assert.assertFalse(syncManager.hasSyncWithName("singleRecordSyncUp"));
        Assert.assertFalse(syncManager.hasSyncWithName("batchSyncUp"));
        Assert.assertFalse(syncManager.hasSyncWithName("collectionSyncUp"));
        Assert.assertFalse(syncManager.hasSyncWithName("parentChildrenSyncUp"));

        // Setting up syncs
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig();

        // Checking smartstore
        Assert.assertTrue(syncManager.hasSyncWithName("soqlSyncDown"));
        Assert.assertTrue(syncManager.hasSyncWithName("soslSyncDown"));
        Assert.assertTrue(syncManager.hasSyncWithName("mruSyncDown"));
        Assert.assertTrue(syncManager.hasSyncWithName("refreshSyncDown"));
        Assert.assertTrue(syncManager.hasSyncWithName("layoutSyncDown"));
        Assert.assertTrue(syncManager.hasSyncWithName("metadataSyncDown"));
        Assert.assertTrue(syncManager.hasSyncWithName("parentChildrenSyncDown"));
        Assert.assertTrue(syncManager.hasSyncWithName("briefcaseSyncDown"));
        Assert.assertTrue(syncManager.hasSyncWithName("singleRecordSyncUp"));
        Assert.assertTrue(syncManager.hasSyncWithName("batchSyncUp"));
        Assert.assertTrue(syncManager.hasSyncWithName("collectionSyncUp"));
        Assert.assertTrue(syncManager.hasSyncWithName("parentChildrenSyncUp"));
    }

    @Test
    public void testSoqlSyncDownFromConfig() throws JSONException {
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig();

        SyncState sync = syncManager.getSyncStatus("soqlSyncDown");
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, sync.getSoupName());
        checkStatus(sync, SyncState.Type.syncDown, sync.getId(),
                new SoqlSyncDownTarget(null, null, "SELECT Id, Name, LastModifiedDate FROM Account"),
                SyncOptions.optionsForSyncDown(MergeMode.OVERWRITE),
                SyncState.Status.NEW, 0);
    }

    @Test
    public void testSoqlSyncDownWithBatchSizeFromConfig() throws JSONException {
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig();

        SyncState sync = syncManager.getSyncStatus("soqlSyncDownWithBatchSize");
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, sync.getSoupName());
        checkStatus(sync, SyncState.Type.syncDown, sync.getId(),
            new SoqlSyncDownTarget(null, null, "SELECT Id, Name, LastModifiedDate FROM Account", 200),
            SyncOptions.optionsForSyncDown(MergeMode.OVERWRITE),
            SyncState.Status.NEW, 0);
    }

    @Test
    public void testSoslSyncDownFromConfig() throws JSONException {
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig();

        SyncState sync = syncManager.getSyncStatus("soslSyncDown");
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, sync.getSoupName());
        checkStatus(sync, SyncState.Type.syncDown, sync.getId(),
                new SoslSyncDownTarget("FIND {Joe} IN NAME FIELDS RETURNING Account"),
                SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED),
                SyncState.Status.NEW, 0);
    }

    @Test
    public void testMruSyncDownFromConfig() throws JSONException {
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig();

        SyncState sync = syncManager.getSyncStatus("mruSyncDown");
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, sync.getSoupName());
        checkStatus(sync, SyncState.Type.syncDown, sync.getId(),
                new MruSyncDownTarget(Arrays.asList("Name", "Description"), "Account"),
                SyncOptions.optionsForSyncDown(MergeMode.OVERWRITE),
                SyncState.Status.NEW, 0);
    }

    @Test
    public void testRefreshSyncDownFromConfig() throws JSONException {
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig();

        SyncState sync = syncManager.getSyncStatus("refreshSyncDown");
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, sync.getSoupName());
        checkStatus(sync, SyncState.Type.syncDown, sync.getId(),
                new RefreshSyncDownTarget(Arrays.asList("Name", "Description"), "Account", "accounts"),
                SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED),
                SyncState.Status.NEW, 0);
    }

    @Test
    public void testLayoutSyncDownFromConfig() throws JSONException {
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig();

        SyncState sync = syncManager.getSyncStatus("layoutSyncDown");
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, sync.getSoupName());
        checkStatus(sync, SyncState.Type.syncDown, sync.getId(),
                new LayoutSyncDownTarget("Account", "Medium", "Compact", "Edit", null),
                SyncOptions.optionsForSyncDown(MergeMode.OVERWRITE),
                SyncState.Status.NEW, 0);
    }

    @Test
    public void testMetadataSyncDownFromConfig() throws JSONException {
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig();

        SyncState sync = syncManager.getSyncStatus("metadataSyncDown");
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, sync.getSoupName());
        checkStatus(sync, SyncState.Type.syncDown, sync.getId(),
                new MetadataSyncDownTarget("Account"),
                SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED),
                SyncState.Status.NEW, 0);
    }

    @Test
    public void testParentChildrenSyncDownFromConfig() throws JSONException {
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig();

        SyncState sync = syncManager.getSyncStatus("parentChildrenSyncDown");
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, sync.getSoupName());
        checkStatus(sync, SyncState.Type.syncDown, sync.getId(),
                new ParentChildrenSyncDownTarget(
                        new ParentInfo("Account", "accounts", "IdX", "LastModifiedDateX"),
                        Arrays.asList("IdX", "Name", "Description"),
                        "NameX like 'James%'",
                        new ChildrenInfo("Contact", "Contacts", "contacts", "AccountId", "IdY", "LastModifiedDateY"),
                        Arrays.asList("LastName", "AccountId"),
                        ParentChildrenSyncTargetHelper.RelationshipType.MASTER_DETAIL),
                SyncOptions.optionsForSyncDown(MergeMode.OVERWRITE),
                SyncState.Status.NEW, 0);

    }

    @Test
    public void testBriefcaseSyncDownFromConfig() throws JSONException {
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig();

        SyncState sync = syncManager.getSyncStatus("briefcaseSyncDown");

        checkStatus(sync, SyncState.Type.syncDown, sync.getId(),
            new BriefcaseSyncDownTarget(
                Arrays.asList(
                    new BriefcaseObjectInfo("accounts", "Account", Arrays.asList("Name", "Description")),
                    new BriefcaseObjectInfo("contacts", "Contact", Arrays.asList("FirstName"), "IdX", "LastModifiedDateX")
                )
            ),
            SyncOptions.optionsForSyncDown(MergeMode.OVERWRITE),
            SyncState.Status.NEW, 0);
    }

    @Test
    public void testSingleRecordSyncUpFromConfig() throws JSONException {
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig();

        SyncState sync = syncManager.getSyncStatus("singleRecordSyncUp");
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, sync.getSoupName());
        checkStatus(sync, SyncState.Type.syncUp, sync.getId(),
                new SyncUpTarget(Arrays.asList("Name"), Arrays.asList("Description")),
                SyncOptions.optionsForSyncUp(Arrays.asList(new String[]{}), MergeMode.LEAVE_IF_CHANGED),
                SyncState.Status.NEW, 0);
    }

    @Test
    public void testBatchSyncUpFromConfig() throws JSONException {
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig();

        SyncState sync = syncManager.getSyncStatus("batchSyncUp");
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, sync.getSoupName());
        checkStatus(sync, SyncState.Type.syncUp, sync.getId(),
                new BatchSyncUpTarget(null, null, "IdX", "LastModifiedDateX", "ExternalIdX", BatchSyncUpTarget.MAX_SUB_REQUESTS_COMPOSITE_API),
                SyncOptions.optionsForSyncUp(Arrays.asList("Name", "Description"), MergeMode.OVERWRITE),
                SyncState.Status.NEW, 0);
    }

    @Test
    public void testCollectionSyncUpFromConfig() throws JSONException {
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig();

        SyncState sync = syncManager.getSyncStatus("collectionSyncUp");
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, sync.getSoupName());
        checkStatus(sync, SyncState.Type.syncUp, sync.getId(),
            new CollectionSyncUpTarget(null, null, "IdX", "LastModifiedDateX", "ExternalIdX", CollectionSyncUpTarget.MAX_RECORDS_SOBJECT_COLLECTION_API),
            SyncOptions.optionsForSyncUp(Arrays.asList("Name", "Description"), MergeMode.OVERWRITE),
            SyncState.Status.NEW, 0);
    }

    @Test
    public void testParentChildrenSyncUpFromConfig() throws JSONException {
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig();

        SyncState sync = syncManager.getSyncStatus("parentChildrenSyncUp");
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, sync.getSoupName());
        checkStatus(sync, SyncState.Type.syncUp, sync.getId(),
                new ParentChildrenSyncUpTarget(
                        new ParentInfo("Account", "accounts", "IdX", "LastModifiedDateX", "ExternalIdX"),
                        Arrays.asList("IdX", "Name", "Description"),
                        Arrays.asList("Name", "Description"),
                        new ChildrenInfo("Contact", "Contacts", "contacts", "AccountId", "IdY", "LastModifiedDateY", "ExternalIdY"),
                        Arrays.asList("LastName", "AccountId"),
                        Arrays.asList("FirstName", "AccountId"),
                        ParentChildrenSyncTargetHelper.RelationshipType.MASTER_DETAIL),
                SyncOptions.optionsForSyncUp(Arrays.asList(new String[]{}), MergeMode.LEAVE_IF_CHANGED),
                SyncState.Status.NEW, 0);
    }
}
