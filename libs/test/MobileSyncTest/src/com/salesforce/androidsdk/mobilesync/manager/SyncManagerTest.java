/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.salesforce.androidsdk.mobilesync.target.LayoutSyncDownTarget;
import com.salesforce.androidsdk.mobilesync.target.MetadataSyncDownTarget;
import com.salesforce.androidsdk.mobilesync.target.MruSyncDownTarget;
import com.salesforce.androidsdk.mobilesync.target.RefreshSyncDownTarget;
import com.salesforce.androidsdk.mobilesync.target.SoqlSyncDownTarget;
import com.salesforce.androidsdk.mobilesync.target.SoslSyncDownTarget;
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget;
import com.salesforce.androidsdk.mobilesync.target.SyncTarget;
import com.salesforce.androidsdk.mobilesync.target.SyncUpTarget;
import com.salesforce.androidsdk.mobilesync.target.TestSyncDownTarget;
import com.salesforce.androidsdk.mobilesync.target.TestSyncUpTarget;
import com.salesforce.androidsdk.mobilesync.util.Constants;
import com.salesforce.androidsdk.mobilesync.util.JSONTestHelper;
import com.salesforce.androidsdk.mobilesync.util.SOSLBuilder;
import com.salesforce.androidsdk.mobilesync.util.SOSLReturningBuilder;
import com.salesforce.androidsdk.mobilesync.util.SyncOptions;
import com.salesforce.androidsdk.mobilesync.util.SyncState;
import com.salesforce.androidsdk.mobilesync.util.SyncState.MergeMode;
import com.salesforce.androidsdk.mobilesync.util.SyncUpdateCallbackQueue;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.singletonList;

/**
 * Test class for SyncManager.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class SyncManagerTest extends SyncManagerTestCase {

    // Misc
    protected static final int COUNT_TEST_ACCOUNTS = 10;
    public static final List<String> REFRESH_FIELDLIST = Arrays.asList(Constants.ID, Constants.NAME, Constants.DESCRIPTION, Constants.LAST_MODIFIED_DATE);

    protected Map<String, Map<String, Object>> idToFields;

    @Before
    public void setUp() throws Exception {
    	super.setUp();
    	createAccountsSoup();
    	idToFields = createRecordsOnServerReturnFields(COUNT_TEST_ACCOUNTS, Constants.ACCOUNT, null);
    }

    @After
    public void tearDown() throws Exception {
        if (idToFields != null) {
            deleteRecordsByIdOnServer(idToFields.keySet(), Constants.ACCOUNT);
        }
    	dropAccountsSoup();
    	super.tearDown();
    }
	
	/**
	 * getSyncStatus should return null for invalid sync id
	 * @throws JSONException
	 */
    @Test
	public void testGetSyncStatusForInvalidSyncId() throws JSONException {
		SyncState sync = syncManager.getSyncStatus(-1);
        Assert.assertNull("Sync status should be null", sync);
	}
	
	/**
	 * Sync down the test accounts, check smart store, check status during sync
	 */
    @Test
	public void testSyncDown() throws Exception {

		// first sync down
		trySyncDown(MergeMode.OVERWRITE);

		// Check that db was correctly populated
        checkDb(idToFields, ACCOUNTS_SOUP);
	}

    /**
     * Sync down the test accounts, make some local changes, sync down again with merge mode LEAVE_IF_CHANGED then sync down with merge mode OVERWRITE
     */
    @Test
    public void testSyncDownWithoutOverwrite() throws Exception {

        // first sync down
        trySyncDown(MergeMode.OVERWRITE);

        // Make some local change
        Map<String, Map<String, Object>> idToFieldsLocallyUpdated = makeLocalChanges(idToFields, ACCOUNTS_SOUP);

        // sync down again with MergeMode.LEAVE_IF_CHANGED
        trySyncDown(MergeMode.LEAVE_IF_CHANGED);

        // Check db
        Map<String, Map<String, Object>> idToFieldsExpected = new HashMap<>(idToFields);
        idToFieldsExpected.putAll(idToFieldsLocallyUpdated);
        checkDb(idToFieldsExpected, ACCOUNTS_SOUP);

        // sync down again with MergeMode.OVERWRITE
        trySyncDown(MergeMode.OVERWRITE);

        // Check db
        checkDb(idToFields, ACCOUNTS_SOUP);
    }

    /**
     * Test for sync down with metadata target.
     */
    @Test
    public void testSyncDownForMetadataTarget() throws Exception {

        // Builds metadata sync down target and performs sync.
        trySyncDown(MergeMode.LEAVE_IF_CHANGED, new MetadataSyncDownTarget(Constants.ACCOUNT), ACCOUNTS_SOUP);
        final QuerySpec smartStoreQuery = QuerySpec.buildAllQuerySpec(ACCOUNTS_SOUP,
                SyncTarget.SYNC_ID, QuerySpec.Order.ascending, 1);
        final JSONArray rows = smartStore.query(smartStoreQuery, 0);
        Assert.assertEquals("Number of rows should be 1", 1, rows.length());
        final JSONObject metadata = rows.optJSONObject(0);
        Assert.assertNotNull("Metadata should not be null", metadata);
        final String keyPrefix = metadata.optString(Constants.KEYPREFIX_FIELD);
        final String label = metadata.optString(Constants.LABEL_FIELD);
        Assert.assertEquals("Key prefix should be 001", Constants.ACCOUNT_KEY_PREFIX, keyPrefix);
        Assert.assertEquals("Label should be " + Constants.ACCOUNT, Constants.ACCOUNT, label);
    }

    /**
     * Test for sync down with layout target.
     */
    @Test
    public void testSyncDownForLayoutTarget() throws Exception {

        // Builds layout sync down target and performs sync.
        trySyncDown(MergeMode.LEAVE_IF_CHANGED, new LayoutSyncDownTarget(Constants.ACCOUNT, Constants.FORM_FACTOR_MEDIUM,
                Constants.LAYOUT_TYPE_COMPACT, Constants.MODE_EDIT, null), ACCOUNTS_SOUP);
        final QuerySpec smartStoreQuery = QuerySpec.buildAllQuerySpec(ACCOUNTS_SOUP,
                SyncTarget.SYNC_ID, QuerySpec.Order.ascending, 1);
        final JSONArray rows = smartStore.query(smartStoreQuery, 0);
        Assert.assertEquals("Number of rows should be 1", 1, rows.length());
        final JSONObject layout = rows.optJSONObject(0);
        Assert.assertNotNull("Layout should not be null", layout);
        final String layoutType = layout.optString(LayoutSyncDownTarget.LAYOUT_TYPE);
        Assert.assertEquals("Layout type should be " + Constants.LAYOUT_TYPE_COMPACT,
                Constants.LAYOUT_TYPE_COMPACT, layoutType);
        final String mode = layout.optString(LayoutSyncDownTarget.MODE);
        Assert.assertEquals("Mode should be " + Constants.MODE_EDIT, Constants.MODE_EDIT, mode);
    }

    /**
     * Sync down the test accounts, modify a few on the server, re-sync, make sure only the updated ones are downloaded
     */
    @Test
    public void testReSync() throws Exception {
        // first sync down
        long syncId = trySyncDown(MergeMode.OVERWRITE);

        // Check sync time stamp
        SyncState sync = syncManager.getSyncStatus(syncId);
        SyncDownTarget target = (SyncDownTarget) sync.getTarget();
        SyncOptions options = sync.getOptions();
        long maxTimeStamp = sync.getMaxTimeStamp();
        Assert.assertTrue("Wrong time stamp", maxTimeStamp > 0);

        // Make some remote change
        Map<String, Map<String, Object>> idToFieldsUpdated = makeRemoteChanges(idToFields, Constants.ACCOUNT);

        // Call reSync
        SyncUpdateCallbackQueue queue = new SyncUpdateCallbackQueue();
        syncManager.reSync(syncId, queue);

        // Check status updates
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0, -1);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0, idToFieldsUpdated.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.DONE, 100, idToFieldsUpdated.size());

        // Check db
        checkDb(idToFieldsUpdated, ACCOUNTS_SOUP);

        // Check sync time stamp
        Assert.assertTrue("Wrong time stamp", syncManager.getSyncStatus(syncId).getMaxTimeStamp() > maxTimeStamp);
    }

    /**
     * Sync down the test accounts, modify a few on the server, re-sync using sync name, make sure only the updated ones are downloaded
     */
    @Test
    public void testReSyncByName() throws Exception {
        String syncName = "syncForTestReSyncByName";

        // first sync down
        long syncId = trySyncDown(MergeMode.OVERWRITE, syncName);

        // Check sync time stamp
        SyncState sync = syncManager.getSyncStatus(syncId);
        SyncDownTarget target = (SyncDownTarget) sync.getTarget();
        SyncOptions options = sync.getOptions();
        long maxTimeStamp = sync.getMaxTimeStamp();
        Assert.assertTrue("Wrong time stamp", maxTimeStamp > 0);

        // Make some remote change
        Map<String, Map<String, Object>> idToFieldsUpdated = makeRemoteChanges(idToFields, Constants.ACCOUNT);

        // Call reSync
        SyncUpdateCallbackQueue queue = new SyncUpdateCallbackQueue();
        syncManager.reSync(syncName, queue);

        // Check status updates
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0, -1);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0, idToFieldsUpdated.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.DONE, 100, idToFieldsUpdated.size());

        // Check db
        checkDb(idToFieldsUpdated, ACCOUNTS_SOUP);

        // Check sync time stamp
        Assert.assertTrue("Wrong time stamp", syncManager.getSyncStatus(syncId).getMaxTimeStamp() > maxTimeStamp);
    }

    /**
     * Call reSync with the name of non-existing sync, expect exception
     */
    @Test
    public void testReSyncByNameWithWrongName() throws Exception {
        String syncName = "testReSyncByNameWithWrongName";
        try {
            syncManager.reSync(syncName, null);
            Assert.fail("Expected exception");
        } catch (SyncManager.MobileSyncException e) {
            Assert.assertTrue(e.getMessage().contains("does not exist"));
        }
    }


    /**
     * Sync down the test accounts, modify a few, sync up using TestSyncUpTarget, check smartstore
     */
    @Test
    public void testCustomSyncUpWithLocallyUpdatedRecords() throws Exception {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE);

        // Update a few entries locally
        Map<String, Map<String, Object>> idToFieldsLocallyUpdated = makeLocalChanges(idToFields, ACCOUNTS_SOUP);

        // Sync up
        TestSyncUpTarget.ActionCollector collector = new TestSyncUpTarget.ActionCollector();
        TestSyncUpTarget target = new TestSyncUpTarget(TestSyncUpTarget.SyncBehavior.NO_FAIL);
        TestSyncUpTarget.setActionCollector(collector);
        trySyncUp(target, 3, MergeMode.OVERWRITE);

        // Check that db doesn't show entries as locally modified anymore
        Set<String> ids = idToFieldsLocallyUpdated.keySet();
        checkDbStateFlags(ids, false, false, false, ACCOUNTS_SOUP);

        // Check what got synched up
        List<String> idsUpdatedByTarget = collector.updatedRecordIds;
        Assert.assertEquals("Wrong number of records updated by target", 3, idsUpdatedByTarget.size());
        for (String idUpdatedByTarget : idsUpdatedByTarget) {
            Assert.assertTrue("Unexpected id:" + idUpdatedByTarget, idToFieldsLocallyUpdated.containsKey(idUpdatedByTarget));
        }
    }

    /**
     * Create accounts locally, sync up using TestSyncUpTarget, check smartstore
     */
    @Test
    public void testCustomSyncUpWithLocallyCreatedRecords() throws Exception {
        // Create a few entries locally
        String[] names = new String[]{createRecordName(Constants.ACCOUNT),
                createRecordName(Constants.ACCOUNT),
                createRecordName(Constants.ACCOUNT)};
        createAccountsLocally(names);

        // Sync up
        TestSyncUpTarget.ActionCollector collector = new TestSyncUpTarget.ActionCollector();
        TestSyncUpTarget target = new TestSyncUpTarget(TestSyncUpTarget.SyncBehavior.NO_FAIL);
        TestSyncUpTarget.setActionCollector(collector);
        trySyncUp(target, 3, MergeMode.OVERWRITE);

        // Check that db doesn't show entries as locally created anymore and that they use sfdc id
        Map<String, Map<String, Object>> idToFieldsCreated = getIdToFieldsByName(ACCOUNTS_SOUP, new String[]{Constants.NAME, Constants.DESCRIPTION}, Constants.NAME, names);
        checkDbStateFlags(idToFieldsCreated.keySet(), false, false, false, ACCOUNTS_SOUP);

        // Check what got synched up
        List<String> idsCreatedByTarget = collector.createdRecordIds;
        Assert.assertEquals("Wrong number of records created by target", 3, idsCreatedByTarget.size());
        for (String idCreatedByTarget : idsCreatedByTarget) {
            Assert.assertTrue("Unexpected id:" + idCreatedByTarget, idToFieldsCreated.containsKey(idCreatedByTarget));
        }

        // Adding to idToFields so that they get deleted in tearDown.
        idToFields.putAll(idToFieldsCreated);
    }

    /**
     * Sync down the test accounts, delete a few, sync up using TestSyncUpTarget, check smartstore
     */
    @Test
    public void testCustomSyncUpWithLocallyDeletedRecords() throws Exception {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE);

        // Delete a few entries locally
        String[] allIds = idToFields.keySet().toArray(new String[0]);
        String[] idsLocallyDeleted = new String[] { allIds[0], allIds[1], allIds[2] };
        deleteRecordsLocally(ACCOUNTS_SOUP, idsLocallyDeleted);

        // Sync up
        TestSyncUpTarget.ActionCollector collector = new TestSyncUpTarget.ActionCollector();
        TestSyncUpTarget target = new TestSyncUpTarget(TestSyncUpTarget.SyncBehavior.NO_FAIL);
        TestSyncUpTarget.setActionCollector(collector);
        trySyncUp(target, 3, MergeMode.OVERWRITE);

        // Check that db doesn't contain those entries anymore
        checkDbDeleted(ACCOUNTS_SOUP, idsLocallyDeleted, Constants.ID);

        // Check what got synched up
        List<String> idsDeletedByTarget = collector.deletedRecordIds;
        Assert.assertEquals("Wrong number of records created by target", 3, idsDeletedByTarget.size());
        for (String idDeleted : idsLocallyDeleted) {
            Assert.assertTrue("Id not synched up" + idDeleted, idsDeletedByTarget.contains(idDeleted));
        }
    }

    /**
     * Sync down the test accounts, modify a few, sync up using a soft failing TestSyncUpTarget, check smartstore
     */
    @Test
    public void testSoftFailingCustomSyncUpWithLocallyUpdatedRecords() throws Exception {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE);

        // Update a few entries locally
        Map<String, Map<String, Object>> idToFieldsLocallyUpdated = makeLocalChanges(idToFields, ACCOUNTS_SOUP);

        // Sync up
        TestSyncUpTarget.ActionCollector collector = new TestSyncUpTarget.ActionCollector();
        TestSyncUpTarget target = new TestSyncUpTarget(TestSyncUpTarget.SyncBehavior.SOFT_FAIL_ON_SYNC);
        TestSyncUpTarget.setActionCollector(collector);
        trySyncUp(target, 3, MergeMode.OVERWRITE);

        // Check that db still shows entries as locally modified anymore
        Set<String> ids = idToFieldsLocallyUpdated.keySet();
        checkDbStateFlags(ids, false, true, false, ACCOUNTS_SOUP);

        // Check what got synched up
        List<String> idsUpdatedByTarget = collector.updatedRecordIds;
        Assert.assertEquals("Wrong number of records updated by target", 0, idsUpdatedByTarget.size());
    }

    /**
     * Sync down the test accounts, modify a few, sync up using a hard failing TestSyncUpTarget, check smartstore
     */
    @Test
    public void testHardFailingCustomSyncUpWithLocallyUpdatedRecords() throws Exception {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE);

        // Update a few entries locally
        Map<String, Map<String, Object>> idToFieldsLocallyUpdated = makeLocalChanges(idToFields, ACCOUNTS_SOUP);

        // Sync up
        TestSyncUpTarget.ActionCollector collector = new TestSyncUpTarget.ActionCollector();
        TestSyncUpTarget target = new TestSyncUpTarget(TestSyncUpTarget.SyncBehavior.HARD_FAIL_ON_SYNC);
        TestSyncUpTarget.setActionCollector(collector);
        trySyncUp(target, 3, MergeMode.OVERWRITE, true /* expect failure */);

        // Check that db still shows entries as locally modified
        Set<String> ids = idToFieldsLocallyUpdated.keySet();
        checkDbStateFlags(ids, false, true, false, ACCOUNTS_SOUP);

        // Check what got synched up
        List<String> idsUpdatedByTarget = collector.updatedRecordIds;
        Assert.assertEquals("Wrong number of records updated by target", 0, idsUpdatedByTarget.size());
    }

    /**
     * Create accounts locally, sync up using soft failing TestSyncUpTarget, check smartstore
     */
    @Test
    public void testSoftFailingCustomSyncUpWithLocallyCreatedRecords() throws Exception {
        // Create a few entries locally
        String[] names = new String[]{createRecordName(Constants.ACCOUNT),
                createRecordName(Constants.ACCOUNT),
                createRecordName(Constants.ACCOUNT)};
        createAccountsLocally(names);

        // Sync up
        TestSyncUpTarget.ActionCollector collector = new TestSyncUpTarget.ActionCollector();
        TestSyncUpTarget target = new TestSyncUpTarget(TestSyncUpTarget.SyncBehavior.SOFT_FAIL_ON_SYNC);
        TestSyncUpTarget.setActionCollector(collector);
        trySyncUp(target, 3, MergeMode.OVERWRITE);

        // Check that db still show show entries as locally created
        Map<String, Map<String, Object>> idToFieldsCreated = getIdToFieldsByName(ACCOUNTS_SOUP, new String[]{Constants.NAME, Constants.DESCRIPTION}, Constants.NAME, names);
        checkDbStateFlags(idToFieldsCreated.keySet(), true, false, false, ACCOUNTS_SOUP);

        // Check what got synched up
        List<String> idsCreatedByTarget = collector.createdRecordIds;
        Assert.assertEquals("Wrong number of records created by target", 0, idsCreatedByTarget.size());

        // Adding to idToFields so that they get deleted in tearDown.
        idToFields.putAll(idToFieldsCreated);
    }

    /**
     * Create accounts locally, sync up using hard failing TestSyncUpTarget, check smartstore
     */
    @Test
    public void testHardFailingCustomSyncUpWithLocallyCreatedRecords() throws Exception {
        // Create a few entries locally
        String[] names = new String[]{createRecordName(Constants.ACCOUNT),
                createRecordName(Constants.ACCOUNT),
                createRecordName(Constants.ACCOUNT)};
        createAccountsLocally(names);

        // Sync up
        TestSyncUpTarget.ActionCollector collector = new TestSyncUpTarget.ActionCollector();
        TestSyncUpTarget target = new TestSyncUpTarget(TestSyncUpTarget.SyncBehavior.HARD_FAIL_ON_SYNC);
        TestSyncUpTarget.setActionCollector(collector);
        trySyncUp(target, 3, MergeMode.OVERWRITE, true /* expect failure */);

        // Check that db still show show entries as locally created
        Map<String, Map<String, Object>> idToFieldsCreated = getIdToFieldsByName(ACCOUNTS_SOUP, new String[]{Constants.NAME, Constants.DESCRIPTION}, Constants.NAME, names);
        checkDbStateFlags(idToFieldsCreated.keySet(), true, false, false, ACCOUNTS_SOUP);

        // Check what got synched up
        List<String> idsCreatedByTarget = collector.createdRecordIds;
        Assert.assertEquals("Wrong number of records created by target", 0, idsCreatedByTarget.size());

        // Adding to idToFields so that they get deleted in tearDown.
        idToFields.putAll(idToFieldsCreated);
    }

    /**
     * Sync down the test accounts, delete a few, sync up using soft failing TestSyncUpTarget, check smartstore
     */
    @Test
    public void testSoftFailingCustomSyncUpWithLocallyDeletedRecords() throws Exception {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE);

        // Delete a few entries locally
        String[] allIds = idToFields.keySet().toArray(new String[0]);
        String[] idsLocallyDeleted = new String[] { allIds[0], allIds[1], allIds[2] };
        deleteRecordsLocally(ACCOUNTS_SOUP, idsLocallyDeleted);

        // Sync up
        TestSyncUpTarget.ActionCollector collector = new TestSyncUpTarget.ActionCollector();
        TestSyncUpTarget target = new TestSyncUpTarget(TestSyncUpTarget.SyncBehavior.SOFT_FAIL_ON_SYNC);
        TestSyncUpTarget.setActionCollector(collector);
        trySyncUp(target, 3, MergeMode.OVERWRITE);

        // Check that db still contains those entries
        Collection<String> ids = Arrays.asList(idsLocallyDeleted);
        checkDbStateFlags(ids, false, false, true, ACCOUNTS_SOUP);

        // Check what got synched up
        List<String> idsDeletedByTarget = collector.deletedRecordIds;
        Assert.assertEquals("Wrong number of records created by target", 0, idsDeletedByTarget.size());
    }

    /**
     * Sync down the test accounts, delete a few, sync up using hard failing TestSyncUpTarget, check smartstore
     */
    @Test
    public void testHardFailingCustomSyncUpWithLocallyDeletedRecords() throws Exception {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE);

        // Delete a few entries locally
        String[] allIds = idToFields.keySet().toArray(new String[0]);
        String[] idsLocallyDeleted = new String[] { allIds[0], allIds[1], allIds[2] };
        deleteRecordsLocally(ACCOUNTS_SOUP, idsLocallyDeleted);

        // Sync up
        TestSyncUpTarget.ActionCollector collector = new TestSyncUpTarget.ActionCollector();
        TestSyncUpTarget target = new TestSyncUpTarget(TestSyncUpTarget.SyncBehavior.HARD_FAIL_ON_SYNC);
        TestSyncUpTarget.setActionCollector(collector);
        trySyncUp(target, 3, MergeMode.OVERWRITE, true /* expect failure */);

        // Check that db still contains those entries
        Collection<String> ids = Arrays.asList(idsLocallyDeleted);
        checkDbStateFlags(ids, false, false, true, ACCOUNTS_SOUP);

        // Check what got synched up
        List<String> idsDeletedByTarget = collector.deletedRecordIds;
        Assert.assertEquals("Wrong number of records created by target", 0, idsDeletedByTarget.size());
    }

    /**
     * Test reSync while sync is running
     */
    @Test
    public void testReSyncRunningSync() throws JSONException {
        // Create sync
        SlowSoqlSyncDownTarget target = new SlowSoqlSyncDownTarget("SELECT Id, Name, LastModifiedDate FROM Account WHERE Id IN " + makeInClause(idToFields.keySet()));
        SyncOptions options = SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED);
        SyncState sync = SyncState.createSyncDown(smartStore, target, options, ACCOUNTS_SOUP, null);
        long syncId = sync.getId();
        checkStatus(sync, SyncState.Type.syncDown, syncId, target, options, SyncState.Status.NEW, 0, -1);

        // Run sync - will freeze during fetch
        SyncUpdateCallbackQueue queue = new SyncUpdateCallbackQueue();
        syncManager.runSync(sync, queue);

        // Wait for sync to be running
        queue.getNextSyncUpdate();

        // Calling reSync -- expect exception
        try {
            syncManager.reSync(syncId, null);
            Assert.fail("Re sync should have failed");
        } catch (SyncManager.MobileSyncException e) {
            Assert.assertTrue("Re sync should have failed because sync is already running", e.getMessage().contains("still running"));
        }

        // Wait for sync to complete successfully
        while (!queue.getNextSyncUpdate().isDone());
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) { }

        // Calling reSync again -- does not expect exception
        try {
            syncManager.reSync(syncId, queue);
        } catch (SyncManager.MobileSyncException e) {
            Assert.fail("Re sync should not have failed");
        }

        // Waiting for reSync to complete successfully
        while (!queue.getNextSyncUpdate().isDone());
    }

    /**
     * Tests if ghost records are cleaned locally for a SOQL target.
     */
    @Test
    public void testCleanResyncGhostsForSOQLTarget() throws Exception {

        // Creates 3 accounts on the server.
        final int numberAccounts = 3;
        final Map<String, String> accounts = createRecordsOnServer(numberAccounts, Constants.ACCOUNT);
        Assert.assertEquals("Wrong number of accounts created", numberAccounts, accounts.size());
        final String[] accountIds = accounts.keySet().toArray(new String[0]);

        // Builds SOQL sync down target and performs initial sync.
        final String soql = "SELECT Id, Name FROM Account WHERE Id IN " + makeInClause(accountIds);
        long syncId = trySyncDown(MergeMode.LEAVE_IF_CHANGED, new SoqlSyncDownTarget(soql), ACCOUNTS_SOUP, accounts.size(), 1, null);
        checkDbExist(ACCOUNTS_SOUP, accountIds, Constants.ID);

        // Deletes 1 account on the server and verifies the ghost record is cleared from the soup.
        deleteRecordsByIdOnServer(new HashSet<>(Arrays.asList(accountIds[0])), Constants.ACCOUNT);
        tryCleanResyncGhosts(syncId);
        checkDbExist(ACCOUNTS_SOUP, new String[] { accountIds[1], accountIds[2]}, Constants.ID);
        checkDbDeleted(ACCOUNTS_SOUP, new String[] { accountIds[0]}, Constants.ID);

        // Deletes the remaining accounts on the server.
        deleteRecordsByIdOnServer(new HashSet<>(Arrays.asList(accountIds[1], accountIds[2])), Constants.ACCOUNT);
    }

    /**
     * Tests clean ghosts when soup is populated through more than one sync down
     */
    @Test
    public void testCleanResyncGhostsWithMultipleSyncs() throws Exception {

        // Creates 6 accounts on the server.
        final int numberAccounts = 6;
        final Map<String, String> accounts = createRecordsOnServer(numberAccounts, Constants.ACCOUNT);
        Assert.assertEquals("Wrong number of accounts created", numberAccounts, accounts.size());
        final String[] accountIds = accounts.keySet().toArray(new String[0]);
        final String[] accountIdsFirstSubset = Arrays.copyOfRange(accountIds, 0, 3); // id0, id1, id2
        final String[] accountIdsSecondSubset = Arrays.copyOfRange(accountIds, 2, 6); //          id2, id3, id4, id5

        // Runs a first SOQL sync down target (bringing down id0, id1, id2)
        long firstSyncId = trySyncDown(MergeMode.LEAVE_IF_CHANGED, new SoqlSyncDownTarget("SELECT Id, Name FROM Account WHERE Id IN " + makeInClause(accountIdsFirstSubset)), ACCOUNTS_SOUP, accountIdsFirstSubset.length, 1, null);
        checkDbExist(ACCOUNTS_SOUP, accountIdsFirstSubset, Constants.ID);
        checkDbSyncIdField(accountIdsFirstSubset, firstSyncId, ACCOUNTS_SOUP);

        // Runs a second SOQL sync down target (bringing down id2, id3, id4, id5)
        long secondSyncId = trySyncDown(MergeMode.LEAVE_IF_CHANGED, new SoqlSyncDownTarget("SELECT Id, Name FROM Account WHERE Id IN " + makeInClause(accountIdsSecondSubset)), ACCOUNTS_SOUP, accountIdsSecondSubset.length, 1, null);
        checkDbExist(ACCOUNTS_SOUP, accountIdsSecondSubset, Constants.ID);
        checkDbSyncIdField(accountIdsSecondSubset, secondSyncId, ACCOUNTS_SOUP);

        // Deletes id0, id2, id5 on the server
        deleteRecordsByIdOnServer(new HashSet<>(Arrays.asList(accountIds[0], accountIds[2], accountIds[5])), Constants.ACCOUNT);

        // Cleaning ghosts of first sync (should only remove id0)
        tryCleanResyncGhosts(firstSyncId);
        checkDbExist(ACCOUNTS_SOUP, new String[] { accountIds[1], accountIds[2], accountIds[3], accountIds[4], accountIds[5]}, Constants.ID);
        checkDbDeleted(ACCOUNTS_SOUP, new String[] { accountIds[0]}, Constants.ID);

        // Cleaning ghosts of second sync (should remove id2 and id5)
        tryCleanResyncGhosts(secondSyncId);
        checkDbExist(ACCOUNTS_SOUP, new String[] { accountIds[1], accountIds[3], accountIds[4]}, Constants.ID);
        checkDbDeleted(ACCOUNTS_SOUP, new String[] { accountIds[2], accountIds[5]}, Constants.ID);

        // Deletes the remaining accounts on the server.
        deleteRecordsByIdOnServer(new HashSet<>(Arrays.asList(accountIds[1], accountIds[3], accountIds[4])), Constants.ACCOUNT);
    }

    /**
     * Tests if ghost records are cleaned locally for a MRU target.
     */
    @Test
    public void testCleanResyncGhostsForMRUTarget() throws Exception {
        // Creates 3 accounts on the server.
        final int numberAccounts = 3;
        final Map<String, String> accounts = createRecordsOnServer(numberAccounts, Constants.ACCOUNT);
        Assert.assertEquals("Wrong number of accounts created", numberAccounts, accounts.size());
        final String[] accountIds = accounts.keySet().toArray(new String[0]);

        // Builds MRU sync down target and performs initial sync.
        final List<String> fieldList = new ArrayList<>();
        fieldList.add(Constants.ID);
        fieldList.add(Constants.NAME);
        long syncId = trySyncDown(MergeMode.LEAVE_IF_CHANGED, new MruSyncDownTarget(fieldList, Constants.ACCOUNT), ACCOUNTS_SOUP);
        checkDbExist(ACCOUNTS_SOUP, accountIds, Constants.ID);

        // Deletes 1 account on the server and verifies the ghost record is cleared from the soup.
        deleteRecordsByIdOnServer(new HashSet<>(singletonList(accountIds[0])), Constants.ACCOUNT);
        tryCleanResyncGhosts(syncId);
        checkDbDeleted(ACCOUNTS_SOUP, new String[] {accountIds[0]}, Constants.ID);

        // Deletes the remaining accounts on the server.
        deleteRecordsByIdOnServer(new HashSet<>(Arrays.asList(accountIds[1], accountIds[2])), Constants.ACCOUNT);
    }

    /**
     * Tests if ghost records are cleaned locally for a SOSL target.
     */
    @Test
    public void testCleanResyncGhostsForSOSLTarget() throws Exception {

        // Creates 1 account on the server.
        final Map<String, String> accounts = createRecordsOnServer(1, Constants.ACCOUNT);
        Assert.assertEquals("1 account should have been created", 1, accounts.size());
        final String[] accountIds = accounts.keySet().toArray(new String[0]);

        // Builds SOSL sync down target and performs initial sync.
        final SOSLBuilder soslBuilder = SOSLBuilder.getInstanceWithSearchTerm(accounts.get(accountIds[0]));
        final SOSLReturningBuilder returningBuilder = SOSLReturningBuilder.getInstanceWithObjectName(Constants.ACCOUNT);
        returningBuilder.fields("Id, Name");
        final String sosl = soslBuilder.returning(returningBuilder).searchGroup("NAME FIELDS").build();
        long syncId = trySyncDown(MergeMode.LEAVE_IF_CHANGED, new SoslSyncDownTarget(sosl), ACCOUNTS_SOUP);
        checkDbExist(ACCOUNTS_SOUP, accountIds, Constants.ID);

        // Deletes 1 account on the server and verifies the ghost record is cleared from the soup.
        deleteRecordsByIdOnServer(new HashSet<String>(Arrays.asList(accountIds[0])), Constants.ACCOUNT);
        tryCleanResyncGhosts(syncId);
        checkDbDeleted(ACCOUNTS_SOUP, new String[] {accountIds[0]}, Constants.ID);

        // Deletes the remaining accounts on the server.
        deleteRecordsByIdOnServer(new HashSet<String>(Arrays.asList(accountIds[0])), Constants.ACCOUNT);
    }

    /**
     * Create sync down, runs it, runs clean ghosts, re-run sync down
     */
    @Test
    public void testSyncCleanGhostsReSync() throws Exception {

        // Creates 3 accounts on the server.
        final int numberAccounts = 3;
        final Map<String, String> accounts = createRecordsOnServer(numberAccounts, Constants.ACCOUNT);
        Assert.assertEquals("Wrong number of accounts created", numberAccounts, accounts.size());
        final String[] accountIds = accounts.keySet().toArray(new String[0]);

        // Builds SOQL sync down target and performs initial sync.
        final String soql = "SELECT Id, Name FROM Account WHERE Id IN " + makeInClause(accountIds);
        long syncId = trySyncDown(MergeMode.LEAVE_IF_CHANGED, new SoqlSyncDownTarget(soql), ACCOUNTS_SOUP, accounts.size(), 1, null);
        checkDbExist(ACCOUNTS_SOUP, accountIds, Constants.ID);

        // Deletes 1 account on the server and verifies the ghost record is cleared from the soup.
        deleteRecordsByIdOnServer(new HashSet<>(Arrays.asList(accountIds[0])), Constants.ACCOUNT);
        tryCleanResyncGhosts(syncId);
        checkDbExist(ACCOUNTS_SOUP, new String[] { accountIds[1], accountIds[2]}, Constants.ID);
        checkDbDeleted(ACCOUNTS_SOUP, new String[] { accountIds[0]}, Constants.ID);

        // Calls reSync
        SyncUpdateCallbackQueue queue = new SyncUpdateCallbackQueue();
        try {
            syncManager.reSync(syncId, queue);
        } catch (SyncManager.MobileSyncException e) {
            Assert.fail("Unexpected exception:" + e);
        }

        // Waiting for reSync to complete successfully
        while (!queue.getNextSyncUpdate().isDone());

        // Deletes the remaining accounts on the server.
        deleteRecordsByIdOnServer(new HashSet<>(Arrays.asList(accountIds[1], accountIds[2])), Constants.ACCOUNT);
    }

    /**
     * Create sync down, get it by id, delete it by id, make sure it's gone
     */
    @Test
    public void testCreateGetDeleteSyncDownById() throws JSONException {
        // Create
        SyncState sync = SyncState.createSyncDown(smartStore, new SoqlSyncDownTarget("SELECT Id, Name from Account"), SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED), ACCOUNTS_SOUP, null);
        long syncId = sync.getId();
        // Get by id
        SyncState fetchedSync = SyncState.byId(smartStore, syncId);
        JSONTestHelper.assertSameJSON("Wrong sync state", sync.asJSON(), fetchedSync.asJSON());
        // Delete by id
        SyncState.deleteSync(smartStore, syncId);
        Assert.assertNull("Sync should be gone", SyncState.byId(smartStore, syncId));
    }

    /**
     * Create sync down with a name, get it by name, delete it by name, make sure it's gone
     */
    @Test
    public void testCreateGetDeleteSyncDownWithName() throws JSONException {
        // Create a named sync down
        String syncName = "MyNamedSyncDown";
        SyncState sync = SyncState.createSyncDown(smartStore, new SoqlSyncDownTarget("SELECT Id, Name from Account"), SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED), ACCOUNTS_SOUP, syncName);
        long syncId = sync.getId();
        // Get by name
        SyncState fetchedSync = SyncState.byName(smartStore, syncName);
        JSONTestHelper.assertSameJSON("Wrong sync state", sync.asJSON(), fetchedSync.asJSON());
        // Delete by name
        SyncState.deleteSync(smartStore, syncName);
        Assert.assertNull("Sync should be gone", SyncState.byId(smartStore, syncId));
        Assert.assertNull("Sync should be gone", SyncState.byName(smartStore, syncName));
    }

    /**
     * Create sync up, get it by id, delete it by id, make sure it's gone
     */
    @Test
    public void testCreateGetDeleteSyncUpById() throws JSONException {
        // Create
        SyncState sync = SyncState.createSyncUp(smartStore, new SyncUpTarget(), SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED), ACCOUNTS_SOUP, null);
        long syncId = sync.getId();
        // Get by id
        SyncState fetchedSync = SyncState.byId(smartStore, syncId);
        JSONTestHelper.assertSameJSON("Wrong sync state", sync.asJSON(), fetchedSync.asJSON());
        // Delete by id
        SyncState.deleteSync(smartStore, syncId);
        Assert.assertNull("Sync should be gone", SyncState.byId(smartStore, syncId));
    }

    /**
     * Create sync up with a name, get it by name, delete it by name, make sure it's gone
     */
    @Test
    public void testCreateGetDeleteSyncUpWithName() throws JSONException {
        // Create a named sync up
        String syncName = "MyNamedSyncUp";
        SyncState sync = SyncState.createSyncUp(smartStore, new SyncUpTarget(), SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED), ACCOUNTS_SOUP, syncName);
        long syncId = sync.getId();
        // Get by name
        SyncState fetchedSync = SyncState.byName(smartStore, syncName);
        JSONTestHelper.assertSameJSON("Wrong sync state", sync.asJSON(), fetchedSync.asJSON());
        // Delete by name
        SyncState.deleteSync(smartStore, syncName);
        Assert.assertNull("Sync should be gone", SyncState.byId(smartStore, syncId));
        Assert.assertNull("Sync should be gone", SyncState.byName(smartStore, syncName));
    }

    /**
     * Create sync with a name, make sure a new sync down with the same name cannot be created
     */
    @Test
    public void testCreateSyncDownWithExistingName() throws JSONException {
        // Create a named sync
        String syncName = "MyNamedSync";
        SyncState.createSyncUp(smartStore, new SyncUpTarget(), SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED), ACCOUNTS_SOUP, syncName);
        // Try to create a sync down with the same name
        try {
            SyncState.createSyncDown(smartStore, new SoqlSyncDownTarget("SELECT Id, Name from Account"), SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED), ACCOUNTS_SOUP, syncName);
            Assert.fail("MobileSyncException should have been thrown");
        }
        catch (SyncManager.MobileSyncException e) {
            Assert.assertTrue(e.getMessage().contains("already a sync with name"));
        }
        // Delete by name
        SyncState.deleteSync(smartStore, syncName);
        Assert.assertNull("Sync should be gone", SyncState.byName(smartStore, syncName));
    }

    /**
     * Create sync with a name, make sure a new sync up with the same name cannot be created
     */
    @Test
    public void testCreateSyncUpWithExistingName() throws JSONException {
        // Create a named sync
        String syncName = "MyNamedSync";
        SyncState.createSyncDown(smartStore, new SoqlSyncDownTarget("SELECT Id, Name from Account"), SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED), ACCOUNTS_SOUP, syncName);
        // Try to create a sync down with the same name
        try {
            SyncState.createSyncUp(smartStore, new SyncUpTarget(), SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED), ACCOUNTS_SOUP, syncName);
            Assert.fail("MobileSyncException should have been thrown");
        }
        catch (SyncManager.MobileSyncException e) {
            Assert.assertTrue(e.getMessage().contains("already a sync with name"));
        }
        // Delete by name
        SyncState.deleteSync(smartStore, syncName);
        Assert.assertNull("Sync should be gone", SyncState.byName(smartStore, syncName));
    }


    /**
     * Run sync down using TestSyncDownTarget
     * @throws JSONException
     */
    @Test
    public void testCustomSyncDownTarget() throws JSONException {
        String syncName = "testCustomSyncDownTarget";
        int numberOfRecords = 30;
        TestSyncDownTarget target = new TestSyncDownTarget("test", numberOfRecords, 10, 0);
        long syncId = trySyncDown(MergeMode.LEAVE_IF_CHANGED, target, ACCOUNTS_SOUP, numberOfRecords, 3, syncName);

        // Check sync time stamp
        SyncState sync = syncManager.getSyncStatus(syncId);
        Assert.assertEquals("Wrong time stamp", target.dateForPosition(numberOfRecords-1).getTime(), sync.getMaxTimeStamp());

        // Check db
        checkDbForAfterTestSyncDown(target, ACCOUNTS_SOUP, numberOfRecords);
    }

    /**
     * Test running and stopping a single sync down (using TestSyncDownTarget)
     * @throws JSONException
     */
    @Test
    public void testStopResumeSingleSyncDown() throws JSONException {
        String syncName = "testStopResumeSingleSyncDown";
        int numberOfRecords = 10;
        TestSyncDownTarget target = new TestSyncDownTarget("test", numberOfRecords, 1, 50);
        SyncOptions options = SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED);
        SyncState sync = SyncState.createSyncDown(smartStore, target, options, ACCOUNTS_SOUP, syncName);
        long syncId = sync.getId();

        // Run sync
        SyncUpdateCallbackQueue queue = new SyncUpdateCallbackQueue();
        syncManager.reSync(syncName, queue);

        // Check status updates
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0, -1);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0, numberOfRecords);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 10, numberOfRecords);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 20, numberOfRecords);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 30, numberOfRecords);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 40, numberOfRecords);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 50, numberOfRecords);

        // Stop sync manager
        stopSyncManager(100);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.STOPPED, 50, numberOfRecords);
        int numberOfRecordsFetched = (int) (numberOfRecords * 0.5);
        int numberOfRecordsLeft = numberOfRecords-numberOfRecordsFetched + 1 /* we refetch records at maxTimeStamp when a sync was stopped */;

        // Check db
        checkDbForAfterTestSyncDown(target, ACCOUNTS_SOUP, numberOfRecordsFetched);

        // Check sync time stamp and status
        checkSyncState(syncId, target.dateForPosition(numberOfRecordsFetched-1).getTime(), SyncState.Status.STOPPED);

        // Try to restart sync while sync manager is paused
        try {
            syncManager.reSync(syncName, queue);
            Assert.fail("Expected exception");
        } catch (SyncManager.MobileSyncException e) {
            Assert.assertTrue("Wrong exception", e instanceof SyncManager.SyncManagerStoppedException);
        }

        // Restarting sync manager without restarting syncs
        syncManager.restart(false, null);
        Assert.assertFalse("Stopped should be false", syncManager.isStopped());

        // Check sync time stamp and status
        checkSyncState(syncId, target.dateForPosition(numberOfRecordsFetched-1).getTime(), SyncState.Status.STOPPED);

        // Stop sync manager
        stopSyncManager(0);

        // Restarting sync manager restarting syncs
        syncManager.restart(true, queue);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0, -1);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0, numberOfRecordsLeft);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 16, numberOfRecordsLeft);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 33, numberOfRecordsLeft);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 50, numberOfRecordsLeft);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 66, numberOfRecordsLeft);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 83, numberOfRecordsLeft);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.DONE, 100, numberOfRecordsLeft);
        checkDbForAfterTestSyncDown(target, ACCOUNTS_SOUP, numberOfRecords);
    }

    /**
     * Test running and stopping multiple (using TestSyncDownTarget)
     * @throws JSONException
     */
    @Test
    public void testStopResumeMultipleSyncDowns() throws JSONException {
        String syncName1 = "testStopResumeMultipleSyncDowns1";
        String syncName2 = "testStopResumeMultipleSyncDowns2";

        int numberRecords1 = 5;
        int numberRecords2 = 4;

        SyncOptions options = SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED);
        TestSyncDownTarget target1 = new TestSyncDownTarget("test1", numberRecords1, 1, 50);
        TestSyncDownTarget target2 = new TestSyncDownTarget("test2", numberRecords2, 1, 50);
        long syncId1 = SyncState.createSyncDown(smartStore, target1, options, ACCOUNTS_SOUP, syncName1).getId();
        long syncId2 = SyncState.createSyncDown(smartStore, target2, options, ACCOUNTS_SOUP, syncName2).getId();

        // Run sync
        SyncUpdateCallbackQueue queue = new SyncUpdateCallbackQueue();
        syncManager.reSync(syncName1, queue);
        try {
            // Sleeping a bit - to make sure it goes first
            Thread.sleep(25);
        } catch (Exception e) {
            Assert.fail("Test interrupted");
        }
        syncManager.reSync(syncName2, queue);

        // Check status updates
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId1, target1, options, SyncState.Status.RUNNING, 0, -1);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId2, target2, options, SyncState.Status.RUNNING, 0, -1);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId1, target1, options, SyncState.Status.RUNNING, 0, numberRecords1);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId1, target1, options, SyncState.Status.RUNNING, 20, numberRecords1);

        // Stop sync manager
        stopSyncManager(200);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId1, target1, options, SyncState.Status.STOPPED, 20, numberRecords1);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId2, target2, options, SyncState.Status.STOPPED, 0, -1);
        int numberOfRecordsFetched1 = (int) (numberRecords1 * 0.2);
        int numberRecordsLeft1 = numberRecords1-numberOfRecordsFetched1 + 1/* we refetch records at maxTimeStamp when a sync was stopped */;

        // Check db
        checkDbForAfterTestSyncDown(target1, ACCOUNTS_SOUP, numberOfRecordsFetched1);
        checkDbForAfterTestSyncDown(target2, ACCOUNTS_SOUP, 0);

        // Check sync time stamp and status
        checkSyncState(syncId1, target1.dateForPosition(numberOfRecordsFetched1-1).getTime(), SyncState.Status.STOPPED);
        checkSyncState(syncId2, -1, SyncState.Status.STOPPED);

        // Restarting sync manager without restarting syncs
        syncManager.restart(false, queue);
        Assert.assertFalse("Stopped should be false", syncManager.isStopped());

        // Manually restart second sync
        syncManager.reSync(syncName2, queue);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId2, target2, options, SyncState.Status.RUNNING, 0, -1);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId2, target2, options, SyncState.Status.RUNNING, 0, numberRecords2);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId2, target2, options, SyncState.Status.RUNNING, 25, numberRecords2);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId2, target2, options, SyncState.Status.RUNNING, 50, numberRecords2);

        // Stop sync manager
        stopSyncManager(200);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId2, target2, options, SyncState.Status.STOPPED, 50, numberRecords2);
        int numberRecordsFetched2 = (int) (numberRecords2 * 0.50);
        int numberRecordsLeft2 = numberRecords2-numberRecordsFetched2 + 1/* we refetch records at maxTimeStamp when a sync was stopped */;

        // Check sync time stamp and status
        checkSyncState(syncId1, target1.dateForPosition(numberOfRecordsFetched1-1).getTime(), SyncState.Status.STOPPED);
        checkSyncState(syncId2, target2.dateForPosition(numberRecordsFetched2-1).getTime(), SyncState.Status.STOPPED);

        // Check db
        checkDbForAfterTestSyncDown(target1, ACCOUNTS_SOUP, numberOfRecordsFetched1);
        checkDbForAfterTestSyncDown(target2, ACCOUNTS_SOUP, numberRecordsFetched2);

        // Restarting sync manager restarting syncs
        syncManager.restart(true, queue);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId1, target1, options, SyncState.Status.RUNNING, 0, -1);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId2, target2, options, SyncState.Status.RUNNING, 0, -1);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId1, target1, options, SyncState.Status.RUNNING, 0, numberRecordsLeft1);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId1, target1, options, SyncState.Status.RUNNING, 20, numberRecordsLeft1);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId1, target1, options, SyncState.Status.RUNNING, 40, numberRecordsLeft1);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId1, target1, options, SyncState.Status.RUNNING, 60, numberRecordsLeft1);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId1, target1, options, SyncState.Status.RUNNING, 80, numberRecordsLeft1);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId1, target1, options, SyncState.Status.DONE, 100, numberRecordsLeft1);

        // sync1 is done, sync2 should run next
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId2, target2, options, SyncState.Status.RUNNING, 0, numberRecordsLeft2);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId2, target2, options, SyncState.Status.RUNNING, 33, numberRecordsLeft2);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId2, target2, options, SyncState.Status.RUNNING, 66, numberRecordsLeft2);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId2, target2, options, SyncState.Status.DONE, 100, numberRecordsLeft2);

        // Check db
        checkDbForAfterTestSyncDown(target1, ACCOUNTS_SOUP, numberRecords1);
        checkDbForAfterTestSyncDown(target2, ACCOUNTS_SOUP, numberRecords2);
    }


    private void checkSyncState(long syncId, long expectedTimeStamp, SyncState.Status expectedStatus) throws JSONException {
        SyncState sync;
        sync = syncManager.getSyncStatus(syncId);
        Assert.assertEquals("Wrong time stamp", expectedTimeStamp, sync.getMaxTimeStamp());
        Assert.assertEquals("Wrong status", expectedStatus, sync.getStatus());
    }

    private void stopSyncManager(int sleepDuration) {
        Assert.assertFalse("Stopped should be false", syncManager.isStopped());
        Assert.assertFalse("Stopping should be false", syncManager.isStopping());
        syncManager.stop();

        if (sleepDuration > 0) {
            // We expect stopping to take a while
            Assert.assertTrue("Stopping should be true", syncManager.isStopping());

            try {
                Thread.sleep(sleepDuration);
            } catch (Exception e) {
                Assert.fail("Test interrupted");
            }
        }

        Assert.assertFalse("Stopping should be false", syncManager.isStopping());
        Assert.assertTrue("Stopped should be true", syncManager.isStopped());
    }

    private void checkDbForAfterTestSyncDown(TestSyncDownTarget target, String soupName, int expectedNumberOfRecords) throws JSONException {
        QuerySpec query = QuerySpec.buildSmartQuerySpec(String.format("SELECT {%1$s:%2$s} from {%1$s} where {%1$s:%2$s} like '%3$s%%' order by {%1$s:%2$s}", soupName, Constants.ID, target.getIdPrefix()), Integer.MAX_VALUE);
        JSONArray result = smartStore.query(query, 0);
        Assert.assertEquals("Wrong number of records", expectedNumberOfRecords, result.length());
        for (int i=0; i<expectedNumberOfRecords; i++) {
            Assert.assertEquals("Wrong id", target.idForPosition(i), result.getJSONArray(i).getString(0));
        }
    }


    /**
	 * Sync down helper
	 * @throws JSONException
     * @param mergeMode
	 */
	private long trySyncDown(MergeMode mergeMode) throws JSONException {
        return trySyncDown(mergeMode, null);
	}

    /**
     * Sync down helper
     * @throws JSONException
     * @param mergeMode
     */
    private long trySyncDown(MergeMode mergeMode, String syncName) throws JSONException {
        final SyncDownTarget target = new SoqlSyncDownTarget("SELECT Id, Name, Description, LastModifiedDate FROM Account WHERE Id IN " + makeInClause(idToFields.keySet()));
        return trySyncDown(mergeMode, target, ACCOUNTS_SOUP, idToFields.size(), 1, syncName);

    }

    /**
     Soql sync down target that pauses for a second at the beginning of the fetch
     */
    public static class SlowSoqlSyncDownTarget extends SoqlSyncDownTarget {

        public SlowSoqlSyncDownTarget(String query) throws JSONException {
            super(query);
            this.queryType = QueryType.custom;
        }

        public SlowSoqlSyncDownTarget(JSONObject target) throws JSONException {
            super(target);
        }

        @Override
        public JSONArray startFetch(SyncManager syncManager, long maxTimeStamp) throws IOException, JSONException {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {

            }
            return super.startFetch(syncManager, maxTimeStamp);
        }
    }
}
