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
package com.salesforce.androidsdk.smartsync.manager;

import androidx.test.filters.LargeTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartsync.target.LayoutSyncDownTarget;
import com.salesforce.androidsdk.smartsync.target.MetadataSyncDownTarget;
import com.salesforce.androidsdk.smartsync.target.MruSyncDownTarget;
import com.salesforce.androidsdk.smartsync.target.RefreshSyncDownTarget;
import com.salesforce.androidsdk.smartsync.target.SoqlSyncDownTarget;
import com.salesforce.androidsdk.smartsync.target.SoslSyncDownTarget;
import com.salesforce.androidsdk.smartsync.target.SyncDownTarget;
import com.salesforce.androidsdk.smartsync.target.SyncTarget;
import com.salesforce.androidsdk.smartsync.target.SyncUpTarget;
import com.salesforce.androidsdk.smartsync.target.TestSyncUpTarget;
import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.androidsdk.smartsync.util.SOQLBuilder;
import com.salesforce.androidsdk.smartsync.util.SOSLBuilder;
import com.salesforce.androidsdk.smartsync.util.SOSLReturningBuilder;
import com.salesforce.androidsdk.smartsync.util.SyncOptions;
import com.salesforce.androidsdk.smartsync.util.SyncState;
import com.salesforce.androidsdk.smartsync.util.SyncState.MergeMode;
import com.salesforce.androidsdk.smartsync.util.SyncUpdateCallbackQueue;
import com.salesforce.androidsdk.util.test.JSONTestHelper;

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
        deleteRecordsOnServer(idToFields.keySet(), Constants.ACCOUNT);
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
        trySyncDown(MergeMode.LEAVE_IF_CHANGED, new LayoutSyncDownTarget(Constants.ACCOUNT,
                Constants.LAYOUT_TYPE_COMPACT), ACCOUNTS_SOUP);
        final QuerySpec smartStoreQuery = QuerySpec.buildAllQuerySpec(ACCOUNTS_SOUP,
                SyncTarget.SYNC_ID, QuerySpec.Order.ascending, 1);
        final JSONArray rows = smartStore.query(smartStoreQuery, 0);
        Assert.assertEquals("Number of rows should be 1", 1, rows.length());
        final JSONObject layout = rows.optJSONObject(0);
        Assert.assertNotNull("Layout should not be null", layout);
        final String layoutType = layout.optString(LayoutSyncDownTarget.LAYOUT_TYPE);
        Assert.assertEquals("Layout type should be " + Constants.LAYOUT_TYPE_COMPACT,
                Constants.LAYOUT_TYPE_COMPACT, layoutType);
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
        } catch (SyncManager.SmartSyncException e) {
            Assert.assertTrue(e.getMessage().contains("no sync found"));
        }
    }

    /**
     * Create a few records - some with bad names (too long or empty)
     * Sync up
     * Make sure the records with bad names are still marked as locally created and have the last error field populated
     * @throws Exception
     */
    @Test
    public void testSyncUpWithErrors() throws Exception {
        // Build name too long
        StringBuffer buffer = new StringBuffer(256);
        for (int i = 0; i < 256; i++) buffer.append("x");
        String nameTooLong = buffer.toString();

        // Create a few entries locally
        String[] goodNames = new String[]{
                createRecordName(Constants.ACCOUNT),
                createRecordName(Constants.ACCOUNT),
                createRecordName(Constants.ACCOUNT)
        };

        String[] badNames = new String[] {
                nameTooLong,
                "" // empty
        };
        createAccountsLocally(goodNames);
        createAccountsLocally(badNames);

        // Sync up
        trySyncUp(5, MergeMode.OVERWRITE);

        // Check db for records with good names
        Map<String, Map<String, Object>> idToFieldsGoodNames = getIdToFieldsByName(ACCOUNTS_SOUP, new String[]{Constants.NAME, Constants.DESCRIPTION}, Constants.NAME, goodNames);
        checkDbStateFlags(idToFieldsGoodNames.keySet(), false, false, false, ACCOUNTS_SOUP);

        // Check db for records with bad names
        Map<String, Map<String, Object>> idToFieldsBadNames = getIdToFieldsByName(ACCOUNTS_SOUP, new String[]{Constants.NAME, Constants.DESCRIPTION, SyncTarget.LAST_ERROR}, Constants.NAME, badNames);
        checkDbStateFlags(idToFieldsBadNames.keySet(), true, false, false, ACCOUNTS_SOUP);

        for (Map<String, Object> fields : idToFieldsBadNames.values()) {
            String name = (String) fields.get(Constants.NAME);
            String lastError = (String) fields.get(SyncTarget.LAST_ERROR);
            if (name.equals(nameTooLong)) {
                Assert.assertTrue("Name too large error expected", lastError.contains("Account Name: data value too large"));
            }
            else if (name.equals("")) {
                Assert.assertTrue("Missing name error expected", lastError.contains("Required fields are missing: [Name]"));
            }
            else {
                Assert.fail("Unexpected record found: " + name);
            }
        }

        // Check server for records with good names
        checkServer(idToFieldsGoodNames, Constants.ACCOUNT);

        // Adding to idToFields so that they get deleted in tearDown
        idToFields.putAll(idToFieldsGoodNames);
    }

    /**
	 * Sync down the test accounts, modify a few, sync up, check smartstore and server afterwards
	 */
    @Test
	public void testSyncUpWithLocallyUpdatedRecords() throws Exception {
		// First sync down
        trySyncDown(MergeMode.OVERWRITE);
		
		// Update a few entries locally
		Map<String, Map<String, Object>> idToFieldsLocallyUpdated = makeLocalChanges(idToFields, ACCOUNTS_SOUP);

		// Sync up
		trySyncUp(3, MergeMode.OVERWRITE);
		
		// Check that db doesn't show entries as locally modified anymore
        Set<String> ids = idToFieldsLocallyUpdated.keySet();
        checkDbStateFlags(ids, false, false, false, ACCOUNTS_SOUP);

		// Check server
        checkServer(idToFieldsLocallyUpdated, Constants.ACCOUNT);
	}

    /**
     * Sync down the test accounts, update a few locally,
     * update a few on server,
     * Sync up with merge mode LEAVE_IF_CHANGED, check smartstore and server
     * Then sync up again with merge mode OVERWRITE, check smartstore and server
	 */
    @Test
	public void testSyncUpWithLocallyUpdatedRecordsWithoutOverwrite() throws Exception {
		// First sync down
        trySyncDown(MergeMode.LEAVE_IF_CHANGED);
		
		// Update a few entries locally
		Map<String, Map<String, Object>> idToFieldsLocallyUpdated = makeLocalChanges(idToFields, ACCOUNTS_SOUP);

		// Update entries on server
        Thread.sleep(1000); // time stamp precision is in seconds
		final Map<String,  Map<String, Object>> idToFieldsRemotelyUpdated = new HashMap<>();
		final Set<String> ids = idToFieldsLocallyUpdated.keySet();
        Assert.assertNotNull("List of IDs should not be null", ids);
		for (final String id : ids) {
            Map<String, Object> fields = idToFieldsLocallyUpdated.get(id);
            Map<String, Object> updatedFields = new HashMap<>();
            for (final String fieldName : fields.keySet()) {
                updatedFields.put(fieldName, fields.get(fieldName) + "_updated_again");
            }
			idToFieldsRemotelyUpdated.put(id, updatedFields);
        }
        updateRecordsOnServer(idToFieldsRemotelyUpdated, Constants.ACCOUNT);

		// Sync up with leave-if-changed
		trySyncUp(3, MergeMode.LEAVE_IF_CHANGED);

		// Check that db shows entries as locally modified
        checkDbStateFlags(ids, false, true, false, ACCOUNTS_SOUP);

		// Check server still has remote updates
        checkServer(idToFieldsRemotelyUpdated, Constants.ACCOUNT);

        // Sync up with overwrite
        trySyncUp(3, MergeMode.OVERWRITE);

        // Check that db no longer shows entries as locally modified
        checkDbStateFlags(ids, false, false, false, ACCOUNTS_SOUP);

        // Check server has local updates
        checkServer(idToFieldsLocallyUpdated, Constants.ACCOUNT);
	}

    /**
	 * Create accounts locally, sync up with merge mode OVERWRITE, check smartstore and server afterwards
	 */
    @Test
    public void testSyncUpWithLocallyCreatedRecords() throws Exception {
        trySyncUpWithLocallyCreatedRecords(MergeMode.OVERWRITE);
    }

    /**
     * Create accounts locally, sync up with mege mode LEAVE_IF_CHANGED, check smartstore and server afterwards
     */
    @Test
	public void testSyncUpWithLocallyCreatedRecordsWithoutOverwrite() throws Exception {
        trySyncUpWithLocallyCreatedRecords(MergeMode.LEAVE_IF_CHANGED);
    }

    private void trySyncUpWithLocallyCreatedRecords(MergeMode syncUpMergeMode) throws Exception {
		// Create a few entries locally
		String[] names = new String[] { createRecordName(Constants.ACCOUNT),
                createRecordName(Constants.ACCOUNT),
                createRecordName(Constants.ACCOUNT) };
		createAccountsLocally(names);
		
		// Sync up
		trySyncUp(3, syncUpMergeMode);
		
		// Check that db doesn't show entries as locally created anymore and that they use sfdc id
        Map<String, Map<String, Object>> idToFieldsCreated = getIdToFieldsByName(ACCOUNTS_SOUP, new String[]{Constants.NAME, Constants.DESCRIPTION}, Constants.NAME, names);
        checkDbStateFlags(idToFieldsCreated.keySet(), false, false, false, ACCOUNTS_SOUP);
		
		// Check server
        checkServer(idToFieldsCreated, Constants.ACCOUNT);

		// Adding to idToFields so that they get deleted in tearDown
		idToFields.putAll(idToFieldsCreated);
	}

    /**
	 * Sync down the test accounts, delete a few, sync up, check smartstore and server afterwards
	 */
    @Test
	public void testSyncUpWithLocallyDeletedRecords() throws Exception {
		// First sync down
        trySyncDown(MergeMode.OVERWRITE);
		
		// Delete a few entries locally
		String[] allIds = idToFields.keySet().toArray(new String[0]);
		String[] idsLocallyDeleted = new String[] { allIds[0], allIds[1], allIds[2] };
		deleteRecordsLocally(ACCOUNTS_SOUP, idsLocallyDeleted);
		
		// Sync up
		trySyncUp(3, MergeMode.OVERWRITE);
		
		// Check that db doesn't contain those entries anymore
        checkDbDeleted(ACCOUNTS_SOUP, idsLocallyDeleted, Constants.ID);

		// Check server
        checkServerDeleted(idsLocallyDeleted, Constants.ACCOUNT);
	}

    /**
     * Create accounts locally, delete them locally, sync up with merge mode LEAVE_IF_CHANGED, check smartstore
     *
     * Ideally an application that deletes locally created records should simply remove them from the smartstore
     * But if records are kept in the smartstore and are flagged as created and deleted (or just deleted), then
     * sync up should not throw any error and the records should end up being removed from the smartstore
     */
    @Test
    public void testSyncUpWithLocallyCreatedAndDeletedRecords() throws Exception {
        // Create a few entries locally
        String[] names = new String[] { createRecordName(Constants.ACCOUNT), createRecordName(Constants.ACCOUNT), createRecordName(Constants.ACCOUNT)};
        createAccountsLocally(names);
        Map<String, Map<String, Object>> idToFieldsCreated = getIdToFieldsByName(ACCOUNTS_SOUP, new String[]{Constants.NAME, Constants.DESCRIPTION}, Constants.NAME, names);

        String[] allIds = idToFieldsCreated.keySet().toArray(new String[0]);
        String[] idsLocallyDeleted = new String[] { allIds[0], allIds[1], allIds[2] };
        deleteRecordsLocally(ACCOUNTS_SOUP, idsLocallyDeleted);

        // Sync up
        trySyncUp(3, MergeMode.LEAVE_IF_CHANGED);

        // Check that db doesn't contain those entries anymore
        checkDbDeleted(ACCOUNTS_SOUP, idsLocallyDeleted, Constants.ID);
    }

	/**
	 * Sync down the test accounts, delete a few locally,
     * update a few on server,
     * Sync up with merge mode LEAVE_IF_CHANGED, check smartstore and server
     * Then sync up again with merge mode OVERWRITE, check smartstore and server
	 */
    @Test
	public void testSyncUpWithLocallyDeletedRecordsWithoutOverwrite() throws Exception {
		// First sync down
        trySyncDown(MergeMode.LEAVE_IF_CHANGED);
		
		// Delete a few entries locally
		String[] allIds = idToFields.keySet().toArray(new String[0]);
		String[] idsLocallyDeleted = new String[] { allIds[0], allIds[1], allIds[2] };
		deleteRecordsLocally(ACCOUNTS_SOUP, idsLocallyDeleted);

		// Update entries on server
        Thread.sleep(1000); // time stamp precision is in seconds
		final Map<String, Map<String, Object>> idToFieldsRemotelyUpdated = new HashMap<>();
        for (int i = 0; i < idsLocallyDeleted.length; i++) {
            String id = idsLocallyDeleted[i];
            Map<String, Object> updatedFields = updatedFields(idToFields.get(id), REMOTELY_UPDATED);
            idToFieldsRemotelyUpdated.put(id, updatedFields);
        }
        updateRecordsOnServer(idToFieldsRemotelyUpdated, Constants.ACCOUNT);

		// Sync up with leave-if-changed
		trySyncUp(3, MergeMode.LEAVE_IF_CHANGED);
		
		// Check that db still contains those entries
        checkDbStateFlags(Arrays.asList(idsLocallyDeleted), false, false, true, ACCOUNTS_SOUP);

		// Check server
        checkServer(idToFieldsRemotelyUpdated, Constants.ACCOUNT);

        // Sync up with overwrite
        trySyncUp(3, MergeMode.OVERWRITE);

        // Check that db no longer contains deleted records
        checkDbDeleted(ACCOUNTS_SOUP, idsLocallyDeleted, Constants.ID);

        // Check server no longer contains deleted record
        checkServerDeleted(idsLocallyDeleted, Constants.ACCOUNT);
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
     * Sync down the test accounts, delete record on server and locally, sync up, check smartstore and server afterwards
     */
    @Test
    public void testSyncUpWithLocallyDeletedRemotelyDeletedRecords() throws Exception {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE);

        // Delete record locally
        String[] allIds = idToFields.keySet().toArray(new String[0]);
        String[] idsLocallyDeleted = new String[] { allIds[0], allIds[1], allIds[2] };
        deleteRecordsLocally(ACCOUNTS_SOUP, idsLocallyDeleted);

        // Delete same records on server
        deleteRecordsOnServer(idToFields.keySet(), Constants.ACCOUNT);

        // Sync up
        trySyncUp(3, MergeMode.OVERWRITE);

        // Check that db doesn't contain those entries anymore
        checkDbDeleted(ACCOUNTS_SOUP, idsLocallyDeleted, Constants.ID);

        // Check server
        checkServerDeleted(idsLocallyDeleted, Constants.ACCOUNT);
    }

    /**
     * Sync down the test accounts, delete record on server and update same record locally, sync up, check smartstore and server afterwards
     */
    @Test
    public void testSyncUpWithLocallyUpdatedRemotelyDeletedRecords() throws Exception {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE);

        // Update a few entries locally
        Map<String, Map<String, Object>> idToFieldsLocallyUpdated = makeLocalChanges(idToFields, ACCOUNTS_SOUP);

        // Delete record on server
        String remotelyDeletedId = idToFieldsLocallyUpdated.keySet().toArray(new String[0])[0];
        deleteRecordsOnServer(new HashSet<String>(Arrays.asList(remotelyDeletedId)), Constants.ACCOUNT);

        // Name of locally recorded record that was deleted on server
        String locallyUpdatedRemotelyDeletedName = (String) idToFieldsLocallyUpdated.get(remotelyDeletedId).get(Constants.NAME);

        // Sync up
        trySyncUp(3, MergeMode.OVERWRITE);

        // Getting id / fields of updated records looking up by name
        Map<String, Map<String, Object>> idToFieldsUpdated = getIdToFieldsByName(ACCOUNTS_SOUP, new String[]{Constants.NAME, Constants.DESCRIPTION}, Constants.NAME, getNamesFromIdToFields(idToFieldsLocallyUpdated));

        // Check db
        checkDb(idToFieldsUpdated, ACCOUNTS_SOUP);

        // Expect 3 records
        Assert.assertEquals(3, idToFieldsUpdated.size());

        // Expect remotely deleted record to have a new id
        Assert.assertFalse(idToFieldsUpdated.containsKey(remotelyDeletedId));
        for (String accountId : idToFieldsUpdated.keySet()) {
            String accountName = (String) idToFieldsUpdated.get(accountId).get(Constants.NAME);

            // Check that locally updated / remotely deleted record has new id (not in idToFields)
            if (accountName.equals(locallyUpdatedRemotelyDeletedName)) {
                Assert.assertFalse(idToFields.containsKey(accountId));

                //update the record entry using the new id
                idToFields.remove(remotelyDeletedId);
                idToFields.put(accountId, idToFieldsUpdated.get(accountId));
            }
            // Otherwise should be a known id (in idToFields)
            else {
                Assert.assertTrue(idToFields.containsKey(accountId));
            }
        }

        // Check server
        checkServer(idToFieldsUpdated, Constants.ACCOUNT);
    }

    /**
     * Sync down the test accounts, delete record on server and update same record locally, sync up with merge mode LEAVE_IF_CHANGED, check smartstore and server afterwards
     */
    @Test
    public void testSyncUpWithLocallyUpdatedRemotelyDeletedRecordsWithoutOverwrite() throws Exception {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE);

        // Update a few entries locally
        Map<String, Map<String, Object>> idToFieldsLocallyUpdated = makeLocalChanges(idToFields, ACCOUNTS_SOUP);

        // Delete record on server
        String remotelyDeletedId = idToFieldsLocallyUpdated.keySet().toArray(new String[0])[0];
        deleteRecordsOnServer(new HashSet<String>(Arrays.asList(remotelyDeletedId)), Constants.ACCOUNT);

        // Sync up
        trySyncUp(3, MergeMode.LEAVE_IF_CHANGED);

        // Getting id / fields of updated records looking up by name
        Map<String, Map<String, Object>> idToFieldsUpdated = getIdToFieldsByName(ACCOUNTS_SOUP, new String[]{Constants.NAME, Constants.DESCRIPTION}, Constants.NAME, getNamesFromIdToFields(idToFieldsLocallyUpdated));

        // Expect 3 records
        Assert.assertEquals(3, idToFieldsUpdated.size());

        // Expect remotely deleted record to be there
        Assert.assertTrue(idToFieldsUpdated.containsKey(remotelyDeletedId));

        // Checking the remotely deleted record locally
        checkDbStateFlags(Arrays.asList(new String[]{remotelyDeletedId}), false, true, false, ACCOUNTS_SOUP);

        // Check the other 2 records in db
        HashMap<String, Map<String, Object>> otherIdtoFields = new HashMap<>(idToFieldsLocallyUpdated);
        otherIdtoFields.remove(remotelyDeletedId);
        checkDb(otherIdtoFields, ACCOUNTS_SOUP);

        // Check server
        checkServer(otherIdtoFields, Constants.ACCOUNT);
        checkServerDeleted(new String[]{remotelyDeletedId}, Constants.ACCOUNT);
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
        } catch (SyncManager.SmartSyncException e) {
            Assert.assertTrue("Re sync should have failed because sync is already running", e.getMessage().contains("still running"));
        }

        // Wait for sync to complete successfully
        while (!queue.getNextSyncUpdate().isDone());

        // Calling reSync again -- does not expect exception
        try {
            syncManager.reSync(syncId, queue);
        } catch (SyncManager.SmartSyncException e) {
            Assert.fail("Re sync should not have failed");
        }

        // Waiting for reSync to complete successfully
        while (!queue.getNextSyncUpdate().isDone());
    }

    /**
     * Tests if missing fields are added to a SOQL target.
     */
    @Test
    public void testAddMissingFieldsToSOQLTarget() throws Exception {
        final String soqlQueryWithSpecialFields = SOQLBuilder.getInstanceWithFields("Id, LastModifiedDate, FirstName, LastName")
                .from(Constants.CONTACT).limit(10).build();
        final String soqlQueryWithoutSpecialFields = SOQLBuilder.getInstanceWithFields("FirstName, LastName")
                .from(Constants.CONTACT).limit(10).build();
        final SoqlSyncDownTarget target = new SoqlSyncDownTarget(soqlQueryWithoutSpecialFields);
        final String targetSoqlQuery = target.getQuery();
        Assert.assertEquals("SOQL query should contain Id and LastModifiedDate fields", soqlQueryWithSpecialFields, targetSoqlQuery);
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
        deleteRecordsOnServer(new HashSet<>(Arrays.asList(accountIds[0])), Constants.ACCOUNT);
        tryCleanResyncGhosts(syncId);
        checkDbExist(ACCOUNTS_SOUP, new String[] { accountIds[1], accountIds[2]}, Constants.ID);
        checkDbDeleted(ACCOUNTS_SOUP, new String[] { accountIds[0]}, Constants.ID);

        // Deletes the remaining accounts on the server.
        deleteRecordsOnServer(new HashSet<>(Arrays.asList(accountIds[1], accountIds[2])), Constants.ACCOUNT);
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
        deleteRecordsOnServer(new HashSet<>(Arrays.asList(accountIds[0], accountIds[2], accountIds[5])), Constants.ACCOUNT);

        // Cleaning ghosts of first sync (should only remove id0)
        tryCleanResyncGhosts(firstSyncId);
        checkDbExist(ACCOUNTS_SOUP, new String[] { accountIds[1], accountIds[2], accountIds[3], accountIds[4], accountIds[5]}, Constants.ID);
        checkDbDeleted(ACCOUNTS_SOUP, new String[] { accountIds[0]}, Constants.ID);

        // Cleaning ghosts of second sync (should remove id2 and id5)
        tryCleanResyncGhosts(secondSyncId);
        checkDbExist(ACCOUNTS_SOUP, new String[] { accountIds[1], accountIds[3], accountIds[4]}, Constants.ID);
        checkDbDeleted(ACCOUNTS_SOUP, new String[] { accountIds[2], accountIds[5]}, Constants.ID);

        // Deletes the remaining accounts on the server.
        deleteRecordsOnServer(new HashSet<>(Arrays.asList(accountIds[1], accountIds[3], accountIds[4])), Constants.ACCOUNT);
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
        deleteRecordsOnServer(new HashSet<>(singletonList(accountIds[0])), Constants.ACCOUNT);
        tryCleanResyncGhosts(syncId);
        checkDbDeleted(ACCOUNTS_SOUP, new String[] {accountIds[0]}, Constants.ID);

        // Deletes the remaining accounts on the server.
        deleteRecordsOnServer(new HashSet<>(Arrays.asList(accountIds[1], accountIds[2])), Constants.ACCOUNT);
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
        deleteRecordsOnServer(new HashSet<String>(Arrays.asList(accountIds[0])), Constants.ACCOUNT);
        tryCleanResyncGhosts(syncId);
        checkDbDeleted(ACCOUNTS_SOUP, new String[] {accountIds[0]}, Constants.ID);

        // Deletes the remaining accounts on the server.
        deleteRecordsOnServer(new HashSet<String>(Arrays.asList(accountIds[0])), Constants.ACCOUNT);
    }

    /**
     * Tests refresh-sync-down
     * @throws Exception
     */
    @Test
    public void testRefreshSyncDown() throws Exception {
        // Setup has created records on the server
        // Adding soup elements with just ids to soup
        for (String id : idToFields.keySet()) {
            JSONObject soupElement = new JSONObject();
            soupElement.put(Constants.ID, id);
            smartStore.create(ACCOUNTS_SOUP, soupElement);
        }
        // Running a refresh-sync-down for soup
        final SyncDownTarget target = new RefreshSyncDownTarget(REFRESH_FIELDLIST, Constants.ACCOUNT, ACCOUNTS_SOUP);
        trySyncDown(MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, idToFields.size(), 1, null);
        // Make sure the soup has the records with id and names
        checkDb(idToFields, ACCOUNTS_SOUP);
    }

    /**
     * Tests refresh-sync-down when they are more records in the table than can be enumerated in one
     * soql call to the server
     * @throws Exception
     */
    @Test
    public void testRefreshSyncDownWithMultipleRoundTrips() throws Exception {
        // Setup has created records on the server
        // Adding soup elements with just ids to soup
        for (String id : idToFields.keySet()) {
            JSONObject soupElement = new JSONObject();
            soupElement.put(Constants.ID, id);
            smartStore.create(ACCOUNTS_SOUP, soupElement);
        }
        // Running a refresh-sync-down for soup with two ids per soql query (to force multiple round trips)
        final RefreshSyncDownTarget target = new RefreshSyncDownTarget(REFRESH_FIELDLIST, Constants.ACCOUNT, ACCOUNTS_SOUP);
        target.setCountIdsPerSoql(2);
        trySyncDown(MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, idToFields.size(), idToFields.size() / 2, null);

        // Make sure the soup has the records with id and names
        checkDb(idToFields, ACCOUNTS_SOUP);
    }

    /**
     * Tests resync for a refresh-sync-down when they are more records in the table than can be enumerated
     * in one soql call to the server
     * @throws Exception
     */
    @Test
    public void testRefreshReSyncWithMultipleRoundTrips() throws Exception {
        // Setup has created records on the server
        // Adding soup elements with just ids to soup
        for (String id : idToFields.keySet()) {
            JSONObject soupElement = new JSONObject();
            soupElement.put(Constants.ID, id);
            smartStore.create(ACCOUNTS_SOUP, soupElement);
        }
        // Running a refresh-sync-down for soup
        final RefreshSyncDownTarget target = new RefreshSyncDownTarget(REFRESH_FIELDLIST, Constants.ACCOUNT, ACCOUNTS_SOUP);
        target.setCountIdsPerSoql(1); //  to exercise continueFetch
        long syncId = trySyncDown(MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, idToFields.size(), 10, null);

        // Check sync time stamp
        SyncState sync = syncManager.getSyncStatus(syncId);
        SyncOptions options = sync.getOptions();
        long maxTimeStamp = sync.getMaxTimeStamp();
        Assert.assertTrue("Wrong time stamp", maxTimeStamp > 0);

        // Make sure the soup has the records with id and names
        checkDb(idToFields, ACCOUNTS_SOUP);

        // Make some remote change
        Map<String, Map<String, Object>> idToFieldsUpdated = makeRemoteChanges(idToFields, Constants.ACCOUNT);

        // Call reSync
        SyncUpdateCallbackQueue queue = new SyncUpdateCallbackQueue();
        syncManager.reSync(syncId, queue);

        // Check status updates
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0, -1);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0, idToFields.size()); // totalSize is off for resync of sync-down-target if not all recrods got updated
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 10, idToFields.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 10, idToFields.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 20, idToFields.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 20, idToFields.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 20, idToFields.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 20, idToFields.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 20, idToFields.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 20, idToFields.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 20, idToFields.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 20, idToFields.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.DONE, 100, idToFields.size());

        // Check db
        checkDb(idToFieldsUpdated, ACCOUNTS_SOUP);

        // Check sync time stamp
        Assert.assertTrue("Wrong time stamp", syncManager.getSyncStatus(syncId).getMaxTimeStamp() > maxTimeStamp);
    }

    /**
     * Tests if ghost records are cleaned locally for a refresh target.
     */
    @Test
    public void testCleanResyncGhostsForRefreshTarget() throws Exception {
        // Setup has created records on the server
        // Adding soup elements with just ids to soup
        for (String id : idToFields.keySet()) {
            JSONObject soupElement = new JSONObject();
            soupElement.put(Constants.ID, id);
            smartStore.create(ACCOUNTS_SOUP, soupElement);
        }
        // Running a refresh-sync-down for soup
        final RefreshSyncDownTarget target = new RefreshSyncDownTarget(REFRESH_FIELDLIST, Constants.ACCOUNT, ACCOUNTS_SOUP);
        long syncId = trySyncDown(MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, idToFields.size(), 1, null);

        // Make sure the soup has the records with id and names
        checkDb(idToFields, ACCOUNTS_SOUP);

        // Deletes 1 account on the server and verifies the ghost record is cleared from the soup.
        String[] ids = idToFields.keySet().toArray(new String[0]);
        String idDeleted = ids[0];
        deleteRecordsOnServer(new HashSet<String>(Arrays.asList(idDeleted)), Constants.ACCOUNT);
        tryCleanResyncGhosts(syncId);

        // Map of id to names expected to be found in db
        Map<String, Map<String, Object>> idToFieldsLeft = new HashMap<>(idToFields);
        idToFieldsLeft.remove(idDeleted);

        // Make sure the soup doesn't contain the record deleted on the server anymore
        checkDb(idToFieldsLeft, ACCOUNTS_SOUP);
        checkDbDeleted(ACCOUNTS_SOUP, new String[] {idDeleted}, Constants.ID);
    }

    /**
     * Sync down the test accounts, modify a few, sync up specifying update field list, check smartstore and server afterwards
     */
    @Test
    public void testSyncUpWithUpdateFieldList() throws Exception {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE);

        // Update a few entries locally
        Map<String, Map<String, Object>> idToFieldsLocallyUpdated = makeLocalChanges(idToFields, ACCOUNTS_SOUP);

        // Sync up with update field list including only name
        trySyncUp(new SyncUpTarget(null, Arrays.asList(new String[] { Constants.NAME })), idToFieldsLocallyUpdated.size(), MergeMode.OVERWRITE, false);

        // Check that db doesn't show entries as locally modified anymore
        Set<String> ids = idToFieldsLocallyUpdated.keySet();
        checkDbStateFlags(ids, false, false, false, ACCOUNTS_SOUP);

        // Check server - make sure only name was updated
        Map<String, Map<String, Object>> idToFieldsExpectedOnServer = new HashMap<>();
        for (String id : idToFieldsLocallyUpdated.keySet()) {
            // Should have modified name but original description
            Map<String, Object> expectedFields = new HashMap<>();
            expectedFields.put(Constants.NAME, idToFieldsLocallyUpdated.get(id).get(Constants.NAME));
            expectedFields.put(Constants.DESCRIPTION, idToFields.get(id).get(Constants.DESCRIPTION));
            idToFieldsExpectedOnServer.put(id, expectedFields);
        }
        checkServer(idToFieldsExpectedOnServer, Constants.ACCOUNT);
    }

    /**
     * Create accounts locally, sync up specifying create field list, check smartstore and server afterwards
     */
    @Test
    public void testSyncUpWithCreateFieldList() throws Exception {
        // Create a few entries locally
        String[] names = new String[] { createRecordName(Constants.ACCOUNT),
                createRecordName(Constants.ACCOUNT),
                createRecordName(Constants.ACCOUNT) };
        createAccountsLocally(names);

        // Sync up with create field list including only name
        trySyncUp(new SyncUpTarget(Arrays.asList(new String[] { Constants.NAME }), null), 3, MergeMode.OVERWRITE, false);

        // Check that db doesn't show entries as locally created anymore and that they use sfdc id
        Map<String, Map<String, Object>> idToFieldsCreated = getIdToFieldsByName(ACCOUNTS_SOUP, new String[]{Constants.NAME, Constants.DESCRIPTION}, Constants.NAME, names);
        checkDbStateFlags(idToFieldsCreated.keySet(), false, false, false, ACCOUNTS_SOUP);

        // Check server - make sure only name was set
        Map<String, Map<String, Object>> idToFieldsExpectedOnServer = new HashMap<>();
        for (String id : idToFieldsCreated.keySet()) {
            // Should have name but no description
            Map<String, Object> expectedFields = new HashMap<>();
            expectedFields.put(Constants.NAME, idToFieldsCreated.get(id).get(Constants.NAME));
            expectedFields.put(Constants.DESCRIPTION, null);
            idToFieldsExpectedOnServer.put(id, expectedFields);
        }
        checkServer(idToFieldsExpectedOnServer, Constants.ACCOUNT);

        // Adding to idToFields so that they get deleted in tearDown.
        idToFields.putAll(idToFieldsCreated);
    }

    /**
     * Sync down the test accounts, modify a few, create accounts locally, sync up specifying different create and update field list,
     * check smartstore and server afterwards
     */
    @Test
    public void testSyncUpWithCreateAndUpdateFieldList() throws Exception {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE);

        // Update a few entries locally
        Map<String, Map<String, Object>> idToFieldsLocallyUpdated = makeLocalChanges(idToFields, ACCOUNTS_SOUP);
        String[] namesOfUpdated = getNamesFromIdToFields(idToFieldsLocallyUpdated);

        // Create a few entries locally
        String[] namesOfCreated = new String[] { createRecordName(Constants.ACCOUNT),
                createRecordName(Constants.ACCOUNT),
                createRecordName(Constants.ACCOUNT) };
        createAccountsLocally(namesOfCreated);

        // Sync up with different create and update field lists
        trySyncUp(new SyncUpTarget(Arrays.asList(new String[]{Constants.NAME}), Arrays.asList(new String[]{Constants.DESCRIPTION})), namesOfCreated.length + namesOfUpdated.length, MergeMode.OVERWRITE, false);

        // Check that db doesn't show created entries as locally created anymore and that they use sfdc id
        Map<String, Map<String, Object>> idToFieldsCreated = getIdToFieldsByName(ACCOUNTS_SOUP, new String[]{Constants.NAME, Constants.DESCRIPTION}, Constants.NAME, namesOfCreated);
        checkDbStateFlags(idToFieldsCreated.keySet(), false, false, false, ACCOUNTS_SOUP);

        // Check that db doesn't show updated entries as locally modified anymore
        checkDbStateFlags(idToFieldsLocallyUpdated.keySet(), false, false, false, ACCOUNTS_SOUP);

        // Check server - make updated records only have updated description - make sure created records only have name
        Map<String, Map<String, Object>> idToFieldsExpectedOnServer = new HashMap<>();
        for (String id : idToFieldsCreated.keySet()) {
            // Should have name but no description
            Map<String, Object> expectedFields = new HashMap<>();
            expectedFields.put(Constants.NAME, idToFieldsCreated.get(id).get(Constants.NAME));
            expectedFields.put(Constants.DESCRIPTION, null);
            idToFieldsExpectedOnServer.put(id, expectedFields);
        }
        for (String id : idToFieldsLocallyUpdated.keySet()) {
            // Should have modified name but original description
            Map<String, Object> expectedFields = new HashMap<>();
            expectedFields.put(Constants.NAME, idToFields.get(id).get(Constants.NAME));
            expectedFields.put(Constants.DESCRIPTION, idToFieldsLocallyUpdated.get(id).get(Constants.DESCRIPTION));
            idToFieldsExpectedOnServer.put(id, expectedFields);
        }
        checkServer(idToFieldsExpectedOnServer, Constants.ACCOUNT);

        // Adding to idToFields so that they get deleted in tearDown.
        idToFields.putAll(idToFieldsCreated);
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
            Assert.fail("SmartSyncException should have been thrown");
        }
        catch (SyncManager.SmartSyncException e) {
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
            Assert.fail("SmartSyncException should have been thrown");
        }
        catch (SyncManager.SmartSyncException e) {
            Assert.assertTrue(e.getMessage().contains("already a sync with name"));
        }
        // Delete by name
        SyncState.deleteSync(smartStore, syncName);
        Assert.assertNull("Sync should be gone", SyncState.byName(smartStore, syncName));
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
     * Sync up helper
     * @param numberChanges
     * @param mergeMode
     * @throws JSONException
     */
    private void trySyncUp(int numberChanges, MergeMode mergeMode) throws JSONException {
        trySyncUp(new SyncUpTarget(), numberChanges, mergeMode);
    }

    /**
     * Sync up helper
     * @param numberChanges
     * @param options
     * @throws JSONException
     */
    private void trySyncUp(int numberChanges, SyncOptions options) throws JSONException {
        trySyncUp(new SyncUpTarget(), numberChanges, options, false);
    }

    /**
     * Return array of names
     * @param idToFields
     */
    private String[] getNamesFromIdToFields(Map<String, Map<String, Object>> idToFields) {
        String[] names = new String[idToFields.size()];
        int i = 0;
        for (String id : idToFields.keySet()) {
            names[i] = (String) idToFields.get(id).get(Constants.NAME);
            i++;
        }
        return names;
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
