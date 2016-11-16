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

import android.text.TextUtils;

import com.salesforce.androidsdk.rest.ApiVersionStrings;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.androidsdk.smartsync.util.MruSyncDownTarget;
import com.salesforce.androidsdk.smartsync.util.RefreshSyncDownTarget;
import com.salesforce.androidsdk.smartsync.util.SOQLBuilder;
import com.salesforce.androidsdk.smartsync.util.SOSLBuilder;
import com.salesforce.androidsdk.smartsync.util.SOSLReturningBuilder;
import com.salesforce.androidsdk.smartsync.util.SoqlSyncDownTarget;
import com.salesforce.androidsdk.smartsync.util.SoslSyncDownTarget;
import com.salesforce.androidsdk.smartsync.util.SyncDownTarget;
import com.salesforce.androidsdk.smartsync.util.SyncOptions;
import com.salesforce.androidsdk.smartsync.util.SyncState;
import com.salesforce.androidsdk.smartsync.util.SyncState.MergeMode;
import com.salesforce.androidsdk.smartsync.util.SyncTarget;
import com.salesforce.androidsdk.smartsync.util.SyncUpTarget;
import com.salesforce.androidsdk.smartsync.util.SyncUpdateCallbackQueue;
import com.salesforce.androidsdk.util.test.JSONTestHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Test class for SyncState.
 */
public class SyncManagerTest extends ManagerTestCase {

	private static final String TYPE = "type";
	private static final String RECORDS = "records";
	private static final String LID = "id"; // lower case id in create response

	// Local
	private static final String LOCAL_ID_PREFIX = "local_";
	private static final String ACCOUNTS_SOUP = "accounts";
	
	// Misc
	private static final int COUNT_TEST_ACCOUNTS = 10;
    private static final int TOTAL_SIZE_UNKNOWN = -2;
	
	private Map<String, String> idToNames;

	@Override
    public void setUp() throws Exception {
    	super.setUp();
    	createAccountsSoup();
    	idToNames = createRecordsOnServer(COUNT_TEST_ACCOUNTS, Constants.ACCOUNT);
    }
    
    @Override 
    public void tearDown() throws Exception {
        deleteRecordsOnServer(idToNames.keySet(), Constants.ACCOUNT);
    	dropAccountsSoup();
    	deleteSyncs();
    	super.tearDown();
    }
	
	/**
	 * getSyncStatus should return null for invalid sync id
	 * @throws JSONException
	 */
	public void testGetSyncStatusForInvalidSyncId() throws JSONException {
		SyncState sync = syncManager.getSyncStatus(-1);
		assertNull("Sync status should be null", sync);
	}
	
	/**
	 * Sync down the test accounts, check smart store, check status during sync
	 */
	public void testSyncDown() throws Exception {
		// first sync down
		trySyncDown(MergeMode.OVERWRITE);

		// Check that db was correctly populated
        checkDb(idToNames);
	}

    /**
     * Sync down the test accounts, make some local changes, sync down again with merge mode LEAVE_IF_CHANGED then sync down with merge mode OVERWRITE
     */
    public void testSyncDownWithoutOverwrite() throws Exception {
        // first sync down
        trySyncDown(MergeMode.OVERWRITE);

        // Make some local change
        Map<String, String> idToNamesLocallyUpdated = makeSomeLocalChanges();

        // sync down again with MergeMode.LEAVE_IF_CHANGED
        trySyncDown(MergeMode.LEAVE_IF_CHANGED);

        // Check db
        Map<String, String> idToNamesExpected = new HashMap<String, String>(idToNames);
        idToNamesExpected.putAll(idToNamesLocallyUpdated);
        checkDb(idToNamesExpected);

        // sync down again with MergeMode.OVERWRITE
        trySyncDown(MergeMode.OVERWRITE);

        // Check db
        checkDb(idToNames);
    }

    /**
     * Sync down the test accounts, modify a few on the server, re-sync, make sure only the updated ones are downloaded
     */
    public void testReSync() throws Exception {
        // first sync down
        long syncId = trySyncDown(MergeMode.OVERWRITE);

        // Check sync time stamp
        SyncState sync = syncManager.getSyncStatus(syncId);
        SyncDownTarget target = (SyncDownTarget) sync.getTarget();
        SyncOptions options = sync.getOptions();
        long maxTimeStamp = sync.getMaxTimeStamp();
        assertTrue("Wrong time stamp", maxTimeStamp > 0);

        // Make some remote change
        Thread.sleep(1000); // time stamp precision is in seconds
        Map<String, String> idToNamesUpdated = new HashMap<String, String>();
        String[] allIds = idToNames.keySet().toArray(new String[0]);
        String[] ids = new String[]{allIds[0], allIds[2]};
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            idToNamesUpdated.put(id, idToNames.get(id) + "_updated");
        }
        updateAccountsOnServer(idToNamesUpdated);

        // Call reSync
        SyncUpdateCallbackQueue queue = new SyncUpdateCallbackQueue();
        syncManager.reSync(syncId, queue);

        // Check status updates
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0, -1);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0, idToNamesUpdated.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.DONE, 100, idToNamesUpdated.size());

        // Check db
        checkDb(idToNamesUpdated);

        // Check sync time stamp
        assertTrue("Wrong time stamp", syncManager.getSyncStatus(syncId).getMaxTimeStamp() > maxTimeStamp);
    }

    /**
	 * Sync down the test accounts, modify a few, sync up, check smartstore and server afterwards
	 */
	public void testSyncUpWithLocallyUpdatedRecords() throws Exception {
		// First sync down
        trySyncDown(MergeMode.OVERWRITE);
		
		// Update a few entries locally
		Map<String, String> idToNamesLocallyUpdated = makeSomeLocalChanges();

		// Sync up
		trySyncUp(3, MergeMode.OVERWRITE);
		
		// Check that db doesn't show entries as locally modified anymore
        Set<String> ids = idToNamesLocallyUpdated.keySet();
        checkDbStateFlags(ids, false, false, false);

		// Check server
        checkServer(idToNamesLocallyUpdated);
	}

    /**
	 * Sync down the test accounts, modify a few, sync up with merge mode LEAVE_IF_CHANGED, check smartstore and server afterwards
	 */
	public void testSyncUpWithLocallyUpdatedRecordsWithoutOverwrite() throws Exception {
		// First sync down
        trySyncDown(MergeMode.LEAVE_IF_CHANGED);
		
		// Update a few entries locally
		Map<String, String> idToNamesLocallyUpdated = makeSomeLocalChanges();

		// Update entries on server
        Thread.sleep(1000); // time stamp precision is in seconds
		final Map<String, String> idToNamesRemotelyUpdated = new HashMap<String, String>();
		final Set<String> ids = idToNamesLocallyUpdated.keySet();
		assertNotNull("List of IDs should not be null", ids);
		for (final String id : ids) {
			idToNamesRemotelyUpdated.put(id,
            		idToNamesLocallyUpdated.get(id) + "_updated_again");
        }
        updateAccountsOnServer(idToNamesRemotelyUpdated);

		// Sync up
		trySyncUp(3, MergeMode.LEAVE_IF_CHANGED);

		// Check that db shows entries as locally modified
        checkDbStateFlags(ids, false, true, false);

		// Check server
        checkServer(idToNamesRemotelyUpdated);
	}

    /**
	 * Create accounts locally, sync up with merge mode OVERWRITE, check smartstore and server afterwards
	 */
    public void testSyncUpWithLocallyCreatedRecords() throws Exception {
        trySyncUpWithLocallyCreatedRecords(MergeMode.OVERWRITE);
    }

    /**
     * Create accounts locally, sync up with mege mode LEAVE_IF_CHANGED, check smartstore and server afterwards
     */
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
        Map<String, String> idToNamesCreated = getIdsForNames(names);
        checkDbStateFlags(idToNamesCreated.keySet(), false, false, false);
		
		// Check server
        checkServer(idToNamesCreated);

		// Adding to idToNames so that they get deleted in tearDown
		idToNames.putAll(idToNamesCreated);
	}

    /**
	 * Sync down the test accounts, delete a few, sync up, check smartstore and server afterwards
	 */
	public void testSyncUpWithLocallyDeletedRecords() throws Exception {
		// First sync down
        trySyncDown(MergeMode.OVERWRITE);
		
		// Delete a few entries locally
		String[] allIds = idToNames.keySet().toArray(new String[0]);
		String[] idsLocallyDeleted = new String[] { allIds[0], allIds[1], allIds[2] };
		deleteAccountsLocally(idsLocallyDeleted);
		
		// Sync up
		trySyncUp(3, MergeMode.OVERWRITE);
		
		// Check that db doesn't contain those entries anymore
        checkDbDeleted(idsLocallyDeleted);

		// Check server
        checkServerDeleted(idsLocallyDeleted);
	}

    /**
     * Create accounts locally, delete them locally, sync up with merge mode LEAVE_IF_CHANGED, check smartstore
     *
     * Ideally an application that deletes locally created records should simply remove them from the smartstore
     * But if records are kept in the smartstore and are flagged as created and deleted (or just deleted), then
     * sync up should not throw any error and the records should end up being removed from the smartstore
     *
     */
    public void testSyncUpWithLocallyCreatedAndDeletedRecords() throws Exception {
        // Create a few entries locally
        String[] names = new String[] { createRecordName(Constants.ACCOUNT), createRecordName(Constants.ACCOUNT), createRecordName(Constants.ACCOUNT)};
        createAccountsLocally(names);
        Map<String, String> idToNamesCreated = getIdsForNames(names);

        String[] allIds = idToNamesCreated.keySet().toArray(new String[0]);
        String[] idsLocallyDeleted = new String[] { allIds[0], allIds[1], allIds[2] };
        deleteAccountsLocally(idsLocallyDeleted);

        // Sync up
        trySyncUp(3, MergeMode.LEAVE_IF_CHANGED);

        // Check that db doesn't contain those entries anymore
        checkDbDeleted(idsLocallyDeleted);
    }

	/**
	 * Sync down the test accounts, delete a few, sync up with merge mode LEAVE_IF_CHANGED, check smartstore and server afterwards
	 */
	public void testSyncUpWithLocallyDeletedRecordsWithoutOverwrite() throws Exception {
		// First sync down
        trySyncDown(MergeMode.LEAVE_IF_CHANGED);
		
		// Delete a few entries locally
		String[] allIds = idToNames.keySet().toArray(new String[0]);
		String[] idsLocallyDeleted = new String[] { allIds[0], allIds[1], allIds[2] };
		deleteAccountsLocally(idsLocallyDeleted);

		// Update entries on server
        Thread.sleep(1000); // time stamp precision is in seconds
		final Map<String, String> idToNamesRemotelyUpdated = new HashMap<String, String>();
        for (int i = 0; i < idsLocallyDeleted.length; i++) {
            String id = idsLocallyDeleted[i];
            idToNamesRemotelyUpdated.put(id, idToNames.get(id) + "_updated");
        }
        updateAccountsOnServer(idToNamesRemotelyUpdated);

		// Sync up
		trySyncUp(3, MergeMode.LEAVE_IF_CHANGED);
		
		// Check that db still contains those entries
        checkDbStateFlags(Arrays.asList(idsLocallyDeleted), false, false, true);

		// Check server
        checkServer(idToNamesRemotelyUpdated);
	}

    /**
     * Sync down the test accounts, modify a few, sync up using TestSyncUpTarget, check smartstore
     */
    public void testCustomSyncUpWithLocallyUpdatedRecords() throws Exception {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE);

        // Update a few entries locally
        Map<String, String> idToNamesLocallyUpdated = makeSomeLocalChanges();

        // Sync up
        TestSyncUpTarget.ActionCollector collector = new TestSyncUpTarget.ActionCollector();
        TestSyncUpTarget target = new TestSyncUpTarget(TestSyncUpTarget.SyncBehavior.NO_FAIL);
        TestSyncUpTarget.setActionCollector(collector);
        trySyncUp(target, 3, MergeMode.OVERWRITE);

        // Check that db doesn't show entries as locally modified anymore
        Set<String> ids = idToNamesLocallyUpdated.keySet();
        checkDbStateFlags(ids, false, false, false);

        // Check what got synched up
        List<String> idsUpdatedByTarget = collector.updatedRecordIds;
        assertEquals("Wrong number of records updated by target", 3, idsUpdatedByTarget.size());
        for (String idUpdatedByTarget : idsUpdatedByTarget) {
            assertTrue("Unexpected id:" + idUpdatedByTarget, idToNamesLocallyUpdated.containsKey(idUpdatedByTarget));
        }
    }

    /**
     * Create accounts locally, sync up using TestSyncUpTarget, check smartstore
     */
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
        Map<String, String> idToNamesCreated = getIdsForNames(names);
        checkDbStateFlags(idToNamesCreated.keySet(), false, false, false);

        // Check what got synched up
        List<String> idsCreatedByTarget = collector.createdRecordIds;
        assertEquals("Wrong number of records created by target", 3, idsCreatedByTarget.size());
        for (String idCreatedByTarget : idsCreatedByTarget) {
            assertTrue("Unexpected id:" + idCreatedByTarget, idToNamesCreated.containsKey(idCreatedByTarget));
        }
    }

    /**
     * Sync down the test accounts, delete a few, sync up using TestSyncUpTarget, check smartstore
     */
    public void testCustomSyncUpWithLocallyDeletedRecords() throws Exception {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE);

        // Delete a few entries locally
        String[] allIds = idToNames.keySet().toArray(new String[0]);
        String[] idsLocallyDeleted = new String[] { allIds[0], allIds[1], allIds[2] };
        deleteAccountsLocally(idsLocallyDeleted);

        // Sync up
        TestSyncUpTarget.ActionCollector collector = new TestSyncUpTarget.ActionCollector();
        TestSyncUpTarget target = new TestSyncUpTarget(TestSyncUpTarget.SyncBehavior.NO_FAIL);
        TestSyncUpTarget.setActionCollector(collector);
        trySyncUp(target, 3, MergeMode.OVERWRITE);

        // Check that db doesn't contain those entries anymore
        checkDbDeleted(idsLocallyDeleted);

        // Check what got synched up
        List<String> idsDeletedByTarget = collector.deletedRecordIds;
        assertEquals("Wrong number of records created by target", 3, idsDeletedByTarget.size());
        for (String idDeleted : idsLocallyDeleted) {
            assertTrue("Id not synched up" + idDeleted, idsDeletedByTarget.contains(idDeleted));
        }
    }

    /**
     * Sync down the test accounts, modify a few, sync up using a soft failing TestSyncUpTarget, check smartstore
     */
    public void testSoftFailingCustomSyncUpWithLocallyUpdatedRecords() throws Exception {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE);

        // Update a few entries locally
        Map<String, String> idToNamesLocallyUpdated = makeSomeLocalChanges();

        // Sync up
        TestSyncUpTarget.ActionCollector collector = new TestSyncUpTarget.ActionCollector();
        TestSyncUpTarget target = new TestSyncUpTarget(TestSyncUpTarget.SyncBehavior.SOFT_FAIL_ON_SYNC);
        TestSyncUpTarget.setActionCollector(collector);
        trySyncUp(target, 3, MergeMode.OVERWRITE);

        // Check that db still shows entries as locally modified anymore
        Set<String> ids = idToNamesLocallyUpdated.keySet();
        checkDbStateFlags(ids, false, true, false);

        // Check what got synched up
        List<String> idsUpdatedByTarget = collector.updatedRecordIds;
        assertEquals("Wrong number of records updated by target", 0, idsUpdatedByTarget.size());
    }

    /**
     * Sync down the test accounts, modify a few, sync up using a hard failing TestSyncUpTarget, check smartstore
     */
    public void testHardFailingCustomSyncUpWithLocallyUpdatedRecords() throws Exception {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE);

        // Update a few entries locally
        Map<String, String> idToNamesLocallyUpdated = makeSomeLocalChanges();

        // Sync up
        TestSyncUpTarget.ActionCollector collector = new TestSyncUpTarget.ActionCollector();
        TestSyncUpTarget target = new TestSyncUpTarget(TestSyncUpTarget.SyncBehavior.HARD_FAIL_ON_SYNC);
        TestSyncUpTarget.setActionCollector(collector);
        trySyncUp(target, 3, MergeMode.OVERWRITE, true /* expect failure */);

        // Check that db still shows entries as locally modified anymore
        Set<String> ids = idToNamesLocallyUpdated.keySet();
        checkDbStateFlags(ids, false, true, false);

        // Check what got synched up
        List<String> idsUpdatedByTarget = collector.updatedRecordIds;
        assertEquals("Wrong number of records updated by target", 0, idsUpdatedByTarget.size());
    }

    /**
     * Create accounts locally, sync up using soft failing TestSyncUpTarget, check smartstore
     */
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

        // Check that db still show show entries as locally created anymore and that they use sfdc id
        Map<String, String> idToNamesCreated = getIdsForNames(names);
        checkDbStateFlags(idToNamesCreated.keySet(), true, false, false);

        // Check what got synched up
        List<String> idsCreatedByTarget = collector.createdRecordIds;
        assertEquals("Wrong number of records created by target", 0, idsCreatedByTarget.size());
    }

    /**
     * Create accounts locally, sync up using hard failing TestSyncUpTarget, check smartstore
     */
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

        // Check that db still show show entries as locally created anymore and that they use sfdc id
        Map<String, String> idToNamesCreated = getIdsForNames(names);
        checkDbStateFlags(idToNamesCreated.keySet(), true, false, false);

        // Check what got synched up
        List<String> idsCreatedByTarget = collector.createdRecordIds;
        assertEquals("Wrong number of records created by target", 0, idsCreatedByTarget.size());
    }

    /**
     * Sync down the test accounts, delete a few, sync up using soft failing TestSyncUpTarget, check smartstore
     */
    public void testSoftFailingCustomSyncUpWithLocallyDeletedRecords() throws Exception {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE);

        // Delete a few entries locally
        String[] allIds = idToNames.keySet().toArray(new String[0]);
        String[] idsLocallyDeleted = new String[] { allIds[0], allIds[1], allIds[2] };
        deleteAccountsLocally(idsLocallyDeleted);

        // Sync up
        TestSyncUpTarget.ActionCollector collector = new TestSyncUpTarget.ActionCollector();
        TestSyncUpTarget target = new TestSyncUpTarget(TestSyncUpTarget.SyncBehavior.SOFT_FAIL_ON_SYNC);
        TestSyncUpTarget.setActionCollector(collector);
        trySyncUp(target, 3, MergeMode.OVERWRITE);

        // Check that db still contains those entries
        Collection<String> ids = Arrays.asList(idsLocallyDeleted);
        checkDbStateFlags(ids, false, false, true);

        // Check what got synched up
        List<String> idsDeletedByTarget = collector.deletedRecordIds;
        assertEquals("Wrong number of records created by target", 0, idsDeletedByTarget.size());
    }

    /**
     * Sync down the test accounts, delete a few, sync up using hard failing TestSyncUpTarget, check smartstore
     */
    public void testHardFailingCustomSyncUpWithLocallyDeletedRecords() throws Exception {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE);

        // Delete a few entries locally
        String[] allIds = idToNames.keySet().toArray(new String[0]);
        String[] idsLocallyDeleted = new String[] { allIds[0], allIds[1], allIds[2] };
        deleteAccountsLocally(idsLocallyDeleted);

        // Sync up
        TestSyncUpTarget.ActionCollector collector = new TestSyncUpTarget.ActionCollector();
        TestSyncUpTarget target = new TestSyncUpTarget(TestSyncUpTarget.SyncBehavior.HARD_FAIL_ON_SYNC);
        TestSyncUpTarget.setActionCollector(collector);
        trySyncUp(target, 3, MergeMode.OVERWRITE, true /* expect failure */);

        // Check that db still contains those entries
        Collection<String> ids = Arrays.asList(idsLocallyDeleted);
        checkDbStateFlags(ids, false, false, true);

        // Check what got synched up
        List<String> idsDeletedByTarget = collector.deletedRecordIds;
        assertEquals("Wrong number of records created by target", 0, idsDeletedByTarget.size());
    }

    /**
     * Sync down the test accounts, delete record on server and locally, sync up, check smartstore and server afterwards
     */
    public void testSyncUpWithLocallyDeletedRemotelyDeletedRecords() throws Exception {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE);

        // Delete record locally
        String[] allIds = idToNames.keySet().toArray(new String[0]);
        String[] idsLocallyDeleted = new String[] { allIds[0], allIds[1], allIds[2] };
        deleteAccountsLocally(idsLocallyDeleted);

        // Delete same records on server
        deleteRecordsOnServer(idToNames.keySet(), Constants.ACCOUNT);

        // Sync up
        trySyncUp(3, MergeMode.OVERWRITE);

        // Check that db doesn't contain those entries anymore
        checkDbDeleted(idsLocallyDeleted);

        // Check server
        checkServerDeleted(idsLocallyDeleted);
    }

    /**
     * Sync down the test accounts, delete record on server and update same record locally, sync up, check smartstore and server afterwards
     */
    public void testSyncUpWithLocallyUpdatedRemotelyDeletedRecords() throws Exception {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE);

        // Update a few entries locally
        Map<String, String> idToNamesLocallyUpdated = makeSomeLocalChanges();

        // Delete record on server
        String remotelyDeletedId = idToNamesLocallyUpdated.keySet().toArray(new String[0])[0];
        deleteRecordsOnServer(new HashSet<String>(Arrays.asList(remotelyDeletedId)), Constants.ACCOUNT);

        // Name of locally recorded record that was deleted on server
        String locallyUpdatedRemotelyDeletedName = idToNamesLocallyUpdated.get(remotelyDeletedId);

        // Sync up
        trySyncUp(3, MergeMode.OVERWRITE);

        // Getting id / names of updated records looking up by name
        Map<String, String> idToNamesUpdated = getIdsForNames(idToNamesLocallyUpdated.values().toArray(new String[0]));

        // Check db
        checkDb(idToNamesUpdated);

        // Expect 3 records
        assertEquals(3, idToNamesUpdated.size());

        // Expect remotely deleted record to have a new id
        assertFalse(idToNamesUpdated.containsKey(remotelyDeletedId));
        for (Entry<String, String> idName : idToNamesUpdated.entrySet()) {
            String accountId = idName.getKey();
            String accountName = idName.getValue();

            // Check that locally updated / remotely deleted record has new id (not in idToNames)
            if (accountName.equals(locallyUpdatedRemotelyDeletedName)) {
                assertFalse(idToNames.containsKey(accountId));

                //update the record entry using the new id
                idToNames.remove(remotelyDeletedId);
                idToNames.put(accountId, accountName);
            }
            // Otherwise should be a known id (in idToNames)
            else {
                 assertTrue(idToNames.containsKey(accountId));
            }
        }

        // Check server
        checkServer(idToNamesUpdated);
    }

    /**
     * Sync down the test accounts, delete record on server and update same record locally, sync up with merge mode LEAVE_IF_CHANGED, check smartstore and server afterwards
     */
    public void testSyncUpWithLocallyUpdatedRemotelyDeletedRecordsWithoutOverwrite() throws Exception {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE);

        // Update a few entries locally
        Map<String, String> idToNamesLocallyUpdated = makeSomeLocalChanges();

        // Delete record on server
        String remotelyDeletedId = idToNamesLocallyUpdated.keySet().toArray(new String[0])[0];
        deleteRecordsOnServer(new HashSet<String>(Arrays.asList(remotelyDeletedId)), Constants.ACCOUNT);

        // Sync up
        trySyncUp(3, MergeMode.LEAVE_IF_CHANGED);

        // Getting id / names of updated records looking up by name
        Map<String, String> idToNamesUpdated = getIdsForNames(idToNamesLocallyUpdated.values().toArray(new String[0]));

        // Expect 3 records
        assertEquals(3, idToNamesUpdated.size());

        // Expect remotely deleted record to be there
        assertTrue(idToNamesUpdated.containsKey(remotelyDeletedId));

        // Checking the remotely deleted record locally
        checkDbStateFlags(Arrays.asList(new String[]{remotelyDeletedId}), false, true, false);

        // Check the other 2 records in db
        idToNamesUpdated.remove(remotelyDeletedId);
        checkDb(idToNamesUpdated);

        // Check server
        checkServer(idToNamesUpdated);
        checkServerDeleted(new String[]{remotelyDeletedId});
    }

    /**
     * Test addFilterForReSync with various queries
     */
    public void testAddFilterForResync() {
        Date date = new Date();
        long dateLong = date.getTime();
        String dateStr = Constants.TIMESTAMP_FORMAT.format(date);
        assertEquals("Wrong result for addFilterForReSync", "select Id from Account where LastModifiedDate > " + dateStr, SoqlSyncDownTarget.addFilterForReSync("select Id from Account", "LastModifiedDate", dateLong));
        assertEquals("Wrong result for addFilterForReSync", "select Id from Account where otherDate > " + dateStr, SoqlSyncDownTarget.addFilterForReSync("select Id from Account", "otherDate", dateLong));
        assertEquals("Wrong result for addFilterForReSync", "select Id from Account where LastModifiedDate > " + dateStr + " limit 100", SoqlSyncDownTarget.addFilterForReSync("select Id from Account limit 100", "LastModifiedDate", dateLong));
        assertEquals("Wrong result for addFilterForReSync", "select Id from Account where LastModifiedDate > " + dateStr + " and Name = 'John'", SoqlSyncDownTarget.addFilterForReSync("select Id from Account where Name = 'John'", "LastModifiedDate", dateLong));
        assertEquals("Wrong result for addFilterForReSync", "select Id from Account where LastModifiedDate > " + dateStr + " and Name = 'John' limit 100", SoqlSyncDownTarget.addFilterForReSync("select Id from Account where Name = 'John' limit 100", "LastModifiedDate", dateLong));
        assertEquals("Wrong result for addFilterForReSync", "SELECT Id FROM Account where LastModifiedDate > " + dateStr, SoqlSyncDownTarget.addFilterForReSync("SELECT Id FROM Account", "LastModifiedDate", dateLong));
        assertEquals("Wrong result for addFilterForReSync", "SELECT Id FROM Account where LastModifiedDate > " + dateStr + " LIMIT 100", SoqlSyncDownTarget.addFilterForReSync("SELECT Id FROM Account LIMIT 100", "LastModifiedDate", dateLong));
        assertEquals("Wrong result for addFilterForReSync", "SELECT Id FROM Account WHERE LastModifiedDate > " + dateStr + " and Name = 'John'", SoqlSyncDownTarget.addFilterForReSync("SELECT Id FROM Account WHERE Name = 'John'", "LastModifiedDate", dateLong));
        assertEquals("Wrong result for addFilterForReSync", "SELECT Id FROM Account WHERE LastModifiedDate > " + dateStr + " and Name = 'John' LIMIT 100", SoqlSyncDownTarget.addFilterForReSync("SELECT Id FROM Account WHERE Name = 'John' LIMIT 100", "LastModifiedDate", dateLong));
    }

    /**
     * Test reSync while sync is running
     */
    public void testReSyncRunningSync() throws JSONException {
        // Create sync
        SlowSoqlSyncDownTarget target = new SlowSoqlSyncDownTarget("SELECT Id, Name, LastModifiedDate FROM Account WHERE Id IN " + makeInClause(idToNames.keySet()));
        SyncOptions options = SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED);
        SyncState sync = SyncState.createSyncDown(smartStore, target, options, ACCOUNTS_SOUP);
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
            fail("Re sync should have failed");
        } catch (SyncManager.SmartSyncException e) {
            assertTrue("Re sync should have failed because sync is already running", e.getMessage().contains("still running"));
        }

        // Wait for sync to complete successfully
        while (!queue.getNextSyncUpdate().isDone());

        // Calling reSync again -- does not expect exception
        try {
            syncManager.reSync(syncId, queue);
        } catch (SyncManager.SmartSyncException e) {
            fail("Re sync should not have failed");
        }

        // Waiting for reSync to complete successfully
        while (!queue.getNextSyncUpdate().isDone());
    }

    /**
     * Test query with "From_customer__c" field
     */
    public void testQueryWithFromFieldtoSOQLTarget() throws Exception {
        final String soqlQueryWithFromField = SOQLBuilder.getInstanceWithFields("From_customer__c, Id")
                .from(Constants.ACCOUNT).limit(10).build();
        final SoqlSyncDownTarget target = new SoqlSyncDownTarget(soqlQueryWithFromField);
        Set<String> result = target.getListOfRemoteIds(syncManager, new HashSet<String>());
        assertTrue("Wrong query was generate", result.size()>0);
    }

    /**
     * Tests if missing fields are added to a SOQL target.
     */
    public void testAddMissingFieldsToSOQLTarget() throws Exception {
        final String soqlQueryWithSpecialFields = SOQLBuilder.getInstanceWithFields("Id, LastModifiedDate, FirstName, LastName")
                .from(Constants.CONTACT).limit(10).build();
        final String soqlQueryWithoutSpecialFields = SOQLBuilder.getInstanceWithFields("FirstName, LastName")
                .from(Constants.CONTACT).limit(10).build();
        final SoqlSyncDownTarget target = new SoqlSyncDownTarget(soqlQueryWithoutSpecialFields);
        final String targetSoqlQuery = target.getQuery();
        assertEquals("SOQL query should contain Id and LastModifiedDate fields", soqlQueryWithSpecialFields, targetSoqlQuery);
    }

    /**
     * Tests if ghost records are cleaned locally for a SOQL target.
     */
    public void testCleanResyncGhostsForSOQLTarget() throws Exception {

        // Creates 3 accounts on the server.
        final Map<String, String> accounts = createRecordsOnServer(3, Constants.ACCOUNT);
        assertEquals("3 accounts should have been created", accounts.size(), 3);
        final Set<String> keySet = accounts.keySet();
        final String[] accountIds = new String[3];
        keySet.toArray(accountIds);
        final String soupName = "Accounts";
        createAccountsSoup(soupName);

        // Builds SOQL sync down target and performs initial sync.
        final String soql = "SELECT Id, Name FROM Account WHERE Id IN ('" + accountIds[0] + "', '" + accountIds[1] + "', '" + accountIds[2] + "')";
        long syncId = trySyncDown(MergeMode.LEAVE_IF_CHANGED, new SoqlSyncDownTarget(soql), soupName, accounts.size(), 1);
        int numRecords = smartStore.countQuery(QuerySpec.buildAllQuerySpec(soupName, "Id", QuerySpec.Order.ascending, 10));
        assertEquals("3 accounts should be stored in the soup", numRecords, 3);

        // Deletes 1 account on the server and verifies the ghost record is cleared from the soup.
        deleteRecordsOnServer(new HashSet<String>(Arrays.asList(accountIds[0])), Constants.ACCOUNT);
        syncManager.cleanResyncGhosts(syncId);
        numRecords = smartStore.countQuery(QuerySpec.buildAllQuerySpec(soupName, "Id", QuerySpec.Order.ascending, 10));
        assertEquals("2 accounts should be stored in the soup", numRecords, 2);

        // Deletes the remaining accounts on the server.
        deleteRecordsOnServer(new HashSet<String>(Arrays.asList(accountIds[1], accountIds[2])), Constants.ACCOUNT);
        dropAccountsSoup(soupName);
        deleteSyncs();
    }

    /**
     * Tests if ghost records are cleaned locally for a MRU target.
     */
    public void testCleanResyncGhostsForMRUTarget() throws Exception {

        // Creates 3 accounts on the server.
        final Map<String, String> accounts = createRecordsOnServer(3, Constants.ACCOUNT);
        assertEquals("3 accounts should have been created", accounts.size(), 3);
        final Set<String> keySet = accounts.keySet();
        final String[] accountIds = new String[3];
        keySet.toArray(accountIds);
        final String soupName = "Accounts";
        createAccountsSoup(soupName);

        // Builds MRU sync down target and performs initial sync.
        final List<String> fieldList = new ArrayList<String>();
        fieldList.add("Id");
        fieldList.add("Name");
        long syncId = trySyncDown(MergeMode.LEAVE_IF_CHANGED, new MruSyncDownTarget(fieldList, Constants.ACCOUNT), soupName);
        int preNumRecords = smartStore.countQuery(QuerySpec.buildAllQuerySpec(soupName, "Id", QuerySpec.Order.ascending, 10));
        assertTrue("At least 1 account should be stored in the soup", preNumRecords > 0);

        // Deletes 1 account on the server and verifies the ghost record is cleared from the soup.
        deleteRecordsOnServer(new HashSet<String>(Arrays.asList(accountIds[0])), Constants.ACCOUNT);
        syncManager.cleanResyncGhosts(syncId);
        int postNumRecords = smartStore.countQuery(QuerySpec.buildAllQuerySpec(soupName, "Id", QuerySpec.Order.ascending, 10));
        assertEquals("1 less account should be stored in the soup", postNumRecords, preNumRecords - 1);

        // Deletes the remaining accounts on the server.
        deleteRecordsOnServer(new HashSet<String>(Arrays.asList(accountIds[1], accountIds[2])), Constants.ACCOUNT);
        dropAccountsSoup(soupName);
        deleteSyncs();
    }

    /**
     * Tests if ghost records are cleaned locally for a SOSL target.
     */
    public void testCleanResyncGhostsForSOSLTarget() throws Exception {

        // Creates 1 account on the server.
        final Map<String, String> accounts = createRecordsOnServer(1, Constants.ACCOUNT);
        assertEquals("1 account should have been created", accounts.size(), 1);
        final Set<String> keySet = accounts.keySet();
        final String[] accountIds = new String[1];
        keySet.toArray(accountIds);
        final String soupName = "Accounts";
        createAccountsSoup(soupName);

        // Builds SOSL sync down target and performs initial sync.
        final SOSLBuilder soslBuilder = SOSLBuilder.getInstanceWithSearchTerm(accounts.get(accountIds[0]));
        final SOSLReturningBuilder returningBuilder = SOSLReturningBuilder.getInstanceWithObjectName(Constants.ACCOUNT);
        returningBuilder.fields("Id, Name");
        final String sosl = soslBuilder.returning(returningBuilder).searchGroup("NAME FIELDS").build();
        long syncId = trySyncDown(MergeMode.LEAVE_IF_CHANGED, new SoslSyncDownTarget(sosl), soupName);
        int numRecords = smartStore.countQuery(QuerySpec.buildAllQuerySpec(soupName, "Id", QuerySpec.Order.ascending, 10));
        assertEquals("1 account should be stored in the soup", numRecords, 1);

        // Deletes 1 account on the server and verifies the ghost record is cleared from the soup.
        deleteRecordsOnServer(new HashSet<String>(Arrays.asList(accountIds[0])), Constants.ACCOUNT);
        syncManager.cleanResyncGhosts(syncId);
        numRecords = smartStore.countQuery(QuerySpec.buildAllQuerySpec(soupName, "Id", QuerySpec.Order.ascending, 10));
        assertEquals("No accounts should be stored in the soup", numRecords, 0);

        // Deletes the remaining accounts on the server.
        deleteRecordsOnServer(new HashSet<String>(Arrays.asList(accountIds[0])), Constants.ACCOUNT);
        dropAccountsSoup(soupName);
        deleteSyncs();
    }

    /**
     * Tests refresh-sync-down
     * @throws Exception
     */
    public void testRefreshSyncDown() throws Exception {
        // Setup has created records on the server
        // Adding soup elements with just ids to soup
        for (String id : idToNames.keySet()) {
            JSONObject soupElement = new JSONObject();
            soupElement.put(Constants.ID, id);
            smartStore.create(ACCOUNTS_SOUP, soupElement);
        }
        // Running a refresh-sync-down for soup
        final SyncDownTarget target = new RefreshSyncDownTarget(Arrays.asList(Constants.ID, Constants.NAME), Constants.ACCOUNT, ACCOUNTS_SOUP);
        trySyncDown(MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, idToNames.size(), 1);
        // Make sure the soup has the records with id and names
        checkDb(idToNames);
    }

    /**
     * Tests refresh-sync-down when they are more records in the table than can be enumerated in one
     * soql call to the server
     * @throws Exception
     */
    public void testRefreshSyncDownWithMultipleRoundTrips() throws Exception {
        // Setup has created records on the server
        // Adding soup elements with just ids to soup
        for (String id : idToNames.keySet()) {
            JSONObject soupElement = new JSONObject();
            soupElement.put(Constants.ID, id);
            smartStore.create(ACCOUNTS_SOUP, soupElement);
        }
        // Running a refresh-sync-down for soup with two ids per soql query (to force multiple round trips)
        final RefreshSyncDownTarget target = new RefreshSyncDownTarget(Arrays.asList(Constants.ID, Constants.NAME), Constants.ACCOUNT, ACCOUNTS_SOUP);
        target.setCountIdsPerSoql(2);
        trySyncDown(MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, idToNames.size(), idToNames.size() / 2);

        // Make sure the soup has the records with id and names
        checkDb(idToNames);
    }

    /**
     * Tests resync for a refresh-sync-down when they are more records in the table than can be enumerated
     * in one soql call to the server
     * @throws Exception
     */
    public void testRefreshReSyncWithMultipleRoundTrips() throws Exception {
        // Setup has created records on the server
        // Adding soup elements with just ids to soup
        for (String id : idToNames.keySet()) {
            JSONObject soupElement = new JSONObject();
            soupElement.put(Constants.ID, id);
            smartStore.create(ACCOUNTS_SOUP, soupElement);
        }
        // Running a refresh-sync-down for soup
        final RefreshSyncDownTarget target = new RefreshSyncDownTarget(Arrays.asList(Constants.ID, Constants.NAME, Constants.LAST_MODIFIED_DATE), Constants.ACCOUNT, ACCOUNTS_SOUP);
        target.setCountIdsPerSoql(1); //  to exercise continueFetch
        long syncId = trySyncDown(MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, idToNames.size(), 10);

        // Check sync time stamp
        SyncState sync = syncManager.getSyncStatus(syncId);
        SyncOptions options = sync.getOptions();
        long maxTimeStamp = sync.getMaxTimeStamp();
        assertTrue("Wrong time stamp", maxTimeStamp > 0);

        // Make sure the soup has the records with id and names
        checkDb(idToNames);

        // Make some remote change
        Thread.sleep(1000); // time stamp precision is in seconds
        Map<String, String> idToNamesUpdated = new HashMap<String, String>();
        String[] allIds = idToNames.keySet().toArray(new String[0]);
        Arrays.sort(allIds); // to make the status updates sequence deterministic
        String[] ids = new String[]{allIds[0], allIds[2]};
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            idToNamesUpdated.put(id, idToNames.get(id) + "_updated");
        }
        updateAccountsOnServer(idToNamesUpdated);

        // Call reSync
        SyncUpdateCallbackQueue queue = new SyncUpdateCallbackQueue();
        syncManager.reSync(syncId, queue);

        // Check status updates
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0, -1);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0, idToNames.size()); // totalSize is off for resync of sync-down-target if not all recrods got updated
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 10, idToNames.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 10, idToNames.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 20, idToNames.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 20, idToNames.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 20, idToNames.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 20, idToNames.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 20, idToNames.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 20, idToNames.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 20, idToNames.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 20, idToNames.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.DONE, 100, idToNames.size());

        // Check db
        checkDb(idToNamesUpdated);

        // Check sync time stamp
        assertTrue("Wrong time stamp", syncManager.getSyncStatus(syncId).getMaxTimeStamp() > maxTimeStamp);
    }


    /**
     * Tests if ghost records are cleaned locally for a refresh target.
     */
    public void testCleanResyncGhostsForRefreshTarget() throws Exception {
        // Setup has created records on the server
        // Adding soup elements with just ids to soup
        for (String id : idToNames.keySet()) {
            JSONObject soupElement = new JSONObject();
            soupElement.put(Constants.ID, id);
            smartStore.create(ACCOUNTS_SOUP, soupElement);
        }
        // Running a refresh-sync-down for soup
        final RefreshSyncDownTarget target = new RefreshSyncDownTarget(Arrays.asList(Constants.ID, Constants.NAME), Constants.ACCOUNT, ACCOUNTS_SOUP);
        long syncId = trySyncDown(MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, idToNames.size(), 1);

        // Make sure the soup has the records with id and names
        checkDb(idToNames);

        // Deletes 1 account on the server and verifies the ghost record is cleared from the soup.
        String[] ids = idToNames.keySet().toArray(new String[0]);
        String idDeleted = ids[0];
        deleteRecordsOnServer(new HashSet<String>(Arrays.asList(idDeleted)), Constants.ACCOUNT);
        syncManager.cleanResyncGhosts(syncId);

        // Map of id to names expected to be found in db
        Map<String, String> idToNamesLeft = new HashMap<>(idToNames);
        idToNamesLeft.remove(idDeleted);

        // Make sure the soup doesn't contain the record deleted on the server anymore
        checkDb(idToNamesLeft);
        int numRecords = smartStore.countQuery(QuerySpec.buildAllQuerySpec(ACCOUNTS_SOUP, "Id", QuerySpec.Order.ascending, 10));
        assertEquals("Wrong number of accounts found in soup", numRecords, idToNamesLeft.size());
    }

	/**
	 * Sync down helper
	 * @throws JSONException
     * @param mergeMode
	 */
	private long trySyncDown(MergeMode mergeMode) throws JSONException {
		final SyncDownTarget target = new SoqlSyncDownTarget("SELECT Id, Name, LastModifiedDate FROM Account WHERE Id IN " + makeInClause(idToNames.keySet()));
        return trySyncDown(mergeMode, target, ACCOUNTS_SOUP, idToNames.size(), 1);

	}

    private long trySyncDown(MergeMode mergeMode, SyncDownTarget target, String soupName) throws JSONException {
        return trySyncDown(mergeMode, target, soupName, TOTAL_SIZE_UNKNOWN, 1);
    }

    /**
     * Sync down helper.
     *
     * @param mergeMode Merge mode.
     * @param target Sync down target.
     * @param soupName Soup name.
     * @param totalSize Expected total size
     * @param numberFetches Expected number of fetches
     * @return Sync ID.
     */
    private long trySyncDown(MergeMode mergeMode, SyncDownTarget target, String soupName, int totalSize, int numberFetches) throws JSONException {
        final SyncOptions options = SyncOptions.optionsForSyncDown(mergeMode);
        final SyncState sync = SyncState.createSyncDown(smartStore, target, options, soupName);
        long syncId = sync.getId();
        checkStatus(sync, SyncState.Type.syncDown, syncId, target, options, SyncState.Status.NEW, 0, -1);

        // Runs sync.
        final SyncUpdateCallbackQueue queue = new SyncUpdateCallbackQueue();
        syncManager.runSync(sync, queue);

        // Checks status updates.
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0, -1);
        if (totalSize != TOTAL_SIZE_UNKNOWN) {
            for (int i = 0; i < numberFetches; i++) {
                checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, i * 100 / numberFetches, totalSize);
            }
            checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.DONE, 100, totalSize);
        } else {
            checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0);
            checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.DONE, 100);
        }
        return syncId;
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
     * @oaram target
	 * @param numberChanges
	 * @param mergeMode
	 * @throws JSONException
	 */
    private void trySyncUp(SyncUpTarget target, int numberChanges, MergeMode mergeMode) throws JSONException {
        trySyncUp(target, numberChanges, mergeMode, false);
    }

    /**
     * Sync up helper
     * @param target
     * @param numberChanges
     * @param mergeMode
     * @param expectSyncFailure - if true, we expect the sync to end up in the FAILED state
     * @throws JSONException
     */
    private void trySyncUp(SyncUpTarget target, int numberChanges, MergeMode mergeMode, boolean expectSyncFailure) throws JSONException {
		// Create sync
		SyncOptions options = SyncOptions.optionsForSyncUp(Arrays.asList(new String[] { Constants.NAME }), mergeMode);
		SyncState sync = SyncState.createSyncUp(smartStore, target, options, ACCOUNTS_SOUP);
		long syncId = sync.getId();
		checkStatus(sync, SyncState.Type.syncUp, syncId, target, options, SyncState.Status.NEW, 0, -1);
		
		// Run sync
		SyncUpdateCallbackQueue queue = new SyncUpdateCallbackQueue();
		syncManager.runSync(sync, queue);
		
		// Check status updates
		checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncUp, syncId, target, options, SyncState.Status.RUNNING, 0, -1); // we get an update right away before getting records to sync
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncUp, syncId, target, options, SyncState.Status.RUNNING, 0, numberChanges);

        if (expectSyncFailure) {
            checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncUp, syncId, target, options, SyncState.Status.FAILED, 0, numberChanges);
        }
        else {
            for (int i = 1; i < numberChanges; i++) {
                checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncUp, syncId, target, options, SyncState.Status.RUNNING, i * 100 / numberChanges, numberChanges);
            }
            checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncUp, syncId, target, options, SyncState.Status.DONE, 100, numberChanges);
        }
	}

	/**
	 * Helper method to check sync state
	 * @param sync
	 * @param expectedType
	 * @param expectedId
	 * @param expectedTarget
	 * @param expectedOptions
	 * @param expectedStatus
	 * @param expectedProgress
	 * @throws JSONException
	 */
	private void checkStatus(SyncState sync, SyncState.Type expectedType, long expectedId, SyncTarget expectedTarget, SyncOptions expectedOptions, SyncState.Status expectedStatus, int expectedProgress, int expectedTotalSize) throws JSONException {
		assertEquals("Wrong type", expectedType, sync.getType());
		assertEquals("Wrong id", expectedId, sync.getId());
		JSONTestHelper.assertSameJSON("Wrong target", (expectedTarget == null ? null : expectedTarget.asJSON()), (sync.getTarget() == null ? null : sync.getTarget().asJSON()));
		JSONTestHelper.assertSameJSON("Wrong options", (expectedOptions == null ? null : expectedOptions.asJSON()), (sync.getOptions() == null ? null : sync.getOptions().asJSON()));
		assertEquals("Wrong status", expectedStatus, sync.getStatus());
		assertEquals("Wrong progress", expectedProgress, sync.getProgress());
        if (expectedTotalSize != TOTAL_SIZE_UNKNOWN) {
            assertEquals("Wrong total size", expectedTotalSize, sync.getTotalSize());
        }
	}

    private void checkStatus(SyncState sync, SyncState.Type expectedType, long expectedId, SyncTarget expectedTarget, SyncOptions expectedOptions, SyncState.Status expectedStatus, int expectedProgress) throws JSONException {
        checkStatus(sync, expectedType, expectedId, expectedTarget, expectedOptions, expectedStatus, expectedProgress, TOTAL_SIZE_UNKNOWN);
    }

	/**
	 * @return local id of the form local_<random number left-padded to be 8 digits long>
	 */
	@SuppressWarnings("resource")
	private String createLocalId() {
		StringBuilder sb = new StringBuilder();
		Formatter formatter = new Formatter(sb, Locale.US);
		formatter.format(LOCAL_ID_PREFIX + "%08d", (int) (Math.random()*10000000));
		String name = sb.toString();
		return name;
	}
	
	/**
	 * Create soup for accounts
	 */
	private void createAccountsSoup() {
        createAccountsSoup(ACCOUNTS_SOUP);
	}

    private void createAccountsSoup(String soupName) {
        final IndexSpec[] indexSpecs = {
                new IndexSpec(Constants.ID, SmartStore.Type.string),
                new IndexSpec(Constants.NAME, SmartStore.Type.string),
                new IndexSpec(SyncManager.LOCAL, SmartStore.Type.string)
        };
        smartStore.registerSoup(soupName, indexSpecs);
    }

	/**
	 * Drop soup for accounts
	 */
	private void dropAccountsSoup() {
        dropAccountsSoup(ACCOUNTS_SOUP);
	}

    private void dropAccountsSoup(String soupName) {
        smartStore.dropSoup(soupName);
    }

	/**
	 * Delete all syncs in syncs_soup
	 */
	private void deleteSyncs() {
		smartStore.clearSoup(SyncState.SYNCS_SOUP);
	}

	/**
	 * Create accounts locally
	 * @param names
	 * @throws JSONException 
	 */
	private void createAccountsLocally(String[] names) throws JSONException {
		JSONObject attributes = new JSONObject();
		attributes.put(TYPE, Constants.ACCOUNT);

		for (String name : names) {
			JSONObject account = new JSONObject();
			account.put(Constants.ID, createLocalId());
			account.put(Constants.NAME, name);
			account.put(Constants.ATTRIBUTES, attributes);
			account.put(SyncManager.LOCAL, true);
			account.put(SyncManager.LOCALLY_CREATED, true);
			account.put(SyncManager.LOCALLY_DELETED, false);
			account.put(SyncManager.LOCALLY_UPDATED, false);
			smartStore.create(ACCOUNTS_SOUP, account);
		}
	}

	/**
	 * Update accounts locally
	 * @param idToNamesLocallyUpdated
	 * @throws JSONException
	 */
	private void updateAccountsLocally(Map<String, String> idToNamesLocallyUpdated) throws JSONException {
		for (Entry<String, String> idAndName : idToNamesLocallyUpdated.entrySet()) {
			String id = idAndName.getKey();
			String updatedName = idAndName.getValue();
			JSONObject account = smartStore.retrieve(ACCOUNTS_SOUP, smartStore.lookupSoupEntryId(ACCOUNTS_SOUP, Constants.ID, id)).getJSONObject(0);
			account.put(Constants.NAME, updatedName);
			account.put(SyncManager.LOCAL, true);
			account.put(SyncManager.LOCALLY_CREATED, false);
			account.put(SyncManager.LOCALLY_DELETED, false);
			account.put(SyncManager.LOCALLY_UPDATED, true);
			smartStore.upsert(ACCOUNTS_SOUP, account);
		}
	}

    /**
     * Update accounts on server
     * @param idToNamesUpdated
     * @throws Exception
     */
    private void updateAccountsOnServer(Map<String, String> idToNamesUpdated) throws Exception {
        for (Entry<String, String> idAndName : idToNamesUpdated.entrySet()) {
            String id = idAndName.getKey();
            String updatedName = idAndName.getValue();

            Map<String, Object> fields = new HashMap<String, Object>();
            fields.put(Constants.NAME, updatedName);
            RestRequest request = RestRequest.getRequestForUpdate(ApiVersionStrings.getVersionNumber(targetContext), Constants.ACCOUNT, id, fields);
            // Response
            RestResponse response = restClient.sendSync(request);
            assertTrue("Updated failed", response.isSuccess());
        }
    }

	/**
	 * Delete accounts locally
	 * @param idsLocallyDeleted
	 * @throws JSONException 
	 */
	private void deleteAccountsLocally(String[] idsLocallyDeleted) throws JSONException {
		for (String id : idsLocallyDeleted) {
			JSONObject account = smartStore.retrieve(ACCOUNTS_SOUP, smartStore.lookupSoupEntryId(ACCOUNTS_SOUP, Constants.ID, id)).getJSONObject(0);
			account.put(SyncManager.LOCAL, true);
			account.put(SyncManager.LOCALLY_CREATED, false);
			account.put(SyncManager.LOCALLY_DELETED, true);
			account.put(SyncManager.LOCALLY_UPDATED, false);
			smartStore.upsert(ACCOUNTS_SOUP, account);
		}
	}

    /**
     * Check records in db
     * @throws JSONException
     * @param expectedIdToNames
     */
    private void checkDb(Map<String, String> expectedIdToNames) throws JSONException {
        QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec("SELECT {accounts:Id}, {accounts:Name} FROM {accounts} WHERE {accounts:Id} IN " + makeInClause(expectedIdToNames.keySet()), COUNT_TEST_ACCOUNTS);
        JSONArray accountsFromDb = smartStore.query(smartStoreQuery, 0);
        JSONObject idToNamesFromDb = new JSONObject();
        for (int i=0; i<accountsFromDb.length(); i++) {
            JSONArray row = accountsFromDb.getJSONArray(i);
            idToNamesFromDb.put(row.getString(0), row.getString(1));
        }
        JSONTestHelper.assertSameJSONObject("Wrong data in db", new JSONObject(expectedIdToNames), idToNamesFromDb);
    }

    /**
     * Check records state in db
     * @param ids
     * @param expectLocallyCreated true if records are expected to be marked as locally created
     * @param expectLocallyUpdated true if records are expected to be marked as locally updated
     * @param expectLocallyDeleted true if records are expected to be marked as locally deleted
     * @throws JSONException
     */
    private void checkDbStateFlags(Collection<String> ids, boolean expectLocallyCreated, boolean expectLocallyUpdated, boolean expectLocallyDeleted) throws JSONException {
        QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec("SELECT {accounts:_soup} FROM {accounts} WHERE {accounts:Id} IN " + makeInClause(ids), ids.size());
        JSONArray accountsFromDb = smartStore.query(smartStoreQuery, 0);
        for (int i=0; i<accountsFromDb.length(); i++) {
            JSONArray row = accountsFromDb.getJSONArray(i);
            JSONObject soupElt = row.getJSONObject(0);
            String id = soupElt.getString(Constants.ID);
            assertEquals("Wrong local flag", expectLocallyCreated || expectLocallyUpdated || expectLocallyDeleted, soupElt.getBoolean(SyncManager.LOCAL));
            assertEquals("Wrong local flag", expectLocallyCreated, soupElt.getBoolean(SyncManager.LOCALLY_CREATED));
            assertEquals("Id was not updated", expectLocallyCreated, id.startsWith(LOCAL_ID_PREFIX));
            assertEquals("Wrong local flag", expectLocallyUpdated, soupElt.getBoolean(SyncManager.LOCALLY_UPDATED));
            assertEquals("Wrong local flag", expectLocallyDeleted, soupElt.getBoolean(SyncManager.LOCALLY_DELETED));
        }
    }

    /**
     * Check that records were deleted from db
     * @param ids
     * @throws JSONException
     */
    private void checkDbDeleted(String[] ids) throws JSONException {
        QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec("SELECT {accounts:_soup}, {accounts:Name} FROM {accounts} WHERE {accounts:Id} IN " + makeInClause(ids), ids.length);
        JSONArray accountsFromDb = smartStore.query(smartStoreQuery, 0);
        assertEquals("No accounts should have been returned from smartstore",0, accountsFromDb.length());
    }

    /**
     * Check records on server
     * @param idToNames
     * @throws IOException
     * @throws JSONException
     */
    private void checkServer(Map<String, String> idToNames) throws IOException, JSONException {
        String soql = "SELECT Id, Name FROM Account WHERE Id IN " + makeInClause(idToNames.keySet());
        RestRequest request = RestRequest.getRequestForQuery(ApiVersionStrings.getVersionNumber(targetContext), soql);
        JSONObject idToNamesFromServer = new JSONObject();
        RestResponse response = restClient.sendSync(request);
        JSONArray records = response.asJSONObject().getJSONArray(RECORDS);
        for (int i=0; i<records.length(); i++) {
            JSONObject row = records.getJSONObject(i);
            idToNamesFromServer.put(row.getString(Constants.ID), row.getString(Constants.NAME));
        }
        JSONTestHelper.assertSameJSONObject("Wrong data on server", new JSONObject(idToNames), idToNamesFromServer);
    }

    /**
     * Check that records were deleted from server
     * @param ids
     * @throws IOException
     */
    private void checkServerDeleted(String[] ids) throws IOException, JSONException {
        String soql = "SELECT Id, Name FROM Account WHERE Id IN " + makeInClause(ids);
        RestRequest request = RestRequest.getRequestForQuery(ApiVersionStrings.getVersionNumber(targetContext), soql);
        RestResponse response = restClient.sendSync(request);
        JSONArray records = response.asJSONObject().getJSONArray(RECORDS);
        assertEquals("No accounts should have been returned from server", 0, records.length());
    }

    /**
     * Make local changes
     * @throws JSONException
     */
    private Map<String, String> makeSomeLocalChanges() throws JSONException {
        Map<String, String> idToNamesLocallyUpdated = new HashMap<String, String>();
        String[] allIds = idToNames.keySet().toArray(new String[0]);
        String[] ids = new String[] { allIds[0], allIds[1], allIds[2] };
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            idToNamesLocallyUpdated.put(id, idToNames.get(id) + "_updated");
        }
        updateAccountsLocally(idToNamesLocallyUpdated);
        return idToNamesLocallyUpdated;
    }

    /**
     * Return map of id to name given records names
     * @param names
     * @throws JSONException
     */
    private Map<String, String> getIdsForNames(String[] names) throws JSONException {
        QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec("SELECT {accounts:_soup} FROM {accounts} WHERE {accounts:Name} IN " + makeInClause(names), names.length);
        JSONArray accountsFromDb = smartStore.query(smartStoreQuery, 0);
        Map<String, String> idToNames = new HashMap<String, String>();
        for (int i=0; i<accountsFromDb.length(); i++) {
            JSONArray row = accountsFromDb.getJSONArray(i);
            JSONObject soupElt = row.getJSONObject(0);
            String id = soupElt.getString(Constants.ID);
            idToNames.put(id, soupElt.getString(Constants.NAME));
        }
        return idToNames;
    }

    private String makeInClause(String[] values) {
        return makeInClause(Arrays.asList(values));
    }

    private String makeInClause(Collection<String> values) {
        return "('" + TextUtils.join("', '", values) + "')";
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
