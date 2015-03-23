/*
 * Copyright (c) 2014-2015, salesforce.com, inc.
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
import com.salesforce.androidsdk.smartsync.util.SoqlSyncDownTarget;
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

import java.util.Arrays;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
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
	
	private Map<String, String> idToNames;

	@Override
    public void setUp() throws Exception {
    	super.setUp();
    	createAccountsSoup();
    	idToNames = createTestAccountsOnServer(COUNT_TEST_ACCOUNTS);
    }
    
    @Override 
    public void tearDown() throws Exception {
    	deleteTestAccountsOnServer(idToNames);
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
		String idsClause = "('" + TextUtils.join("', '", ids) + "')";
		QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec("SELECT {accounts:_soup} FROM {accounts} WHERE {accounts:Id} IN " + idsClause, ids.size());
		JSONArray accountsFromDb = smartStore.query(smartStoreQuery, 0);
		for (int i=0; i<accountsFromDb.length(); i++) {
			JSONArray row = accountsFromDb.getJSONArray(i);
			JSONObject soupElt = row.getJSONObject(0);
			assertEquals("Wrong local flag", false, soupElt.getBoolean(SyncManager.LOCAL));
			assertEquals("Wrong local flag", false, soupElt.getBoolean(SyncManager.LOCALLY_UPDATED));
		}
		
		// Check server
		String soql = "SELECT Id, Name FROM Account WHERE Id IN " + idsClause;
		RestRequest request = RestRequest.getRequestForQuery(ApiVersionStrings.VERSION_NUMBER, soql);
		JSONObject idToNamesFromServer = new JSONObject();
		RestResponse response = restClient.sendSync(request);
		JSONArray records = response.asJSONObject().getJSONArray(RECORDS);
		for (int i=0; i<records.length(); i++) {
			JSONObject row = records.getJSONObject(i);
			idToNamesFromServer.put(row.getString(Constants.ID), row.getString(Constants.NAME));
		}
		JSONTestHelper.assertSameJSONObject("Wrong data on server", new JSONObject(idToNamesLocallyUpdated), idToNamesFromServer);
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
		String idsClause = "('" + TextUtils.join("', '", ids) + "')";
		QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec("SELECT {accounts:_soup} FROM {accounts} WHERE {accounts:Id} IN " + idsClause, ids.size());
		JSONArray accountsFromDb = smartStore.query(smartStoreQuery, 0);
		for (int i = 0; i < accountsFromDb.length(); i++) {
			JSONArray row = accountsFromDb.getJSONArray(i);
			JSONObject soupElt = row.getJSONObject(0);
			assertTrue("Wrong local flag", soupElt.getBoolean(SyncManager.LOCAL));
			assertTrue("Wrong local flag", soupElt.getBoolean(SyncManager.LOCALLY_UPDATED));
		}

		// Check server
		String soql = "SELECT Id, Name FROM Account WHERE Id IN " + idsClause;
		RestRequest request = RestRequest.getRequestForQuery(ApiVersionStrings.VERSION_NUMBER, soql);
		JSONObject idToNamesFromServer = new JSONObject();
		RestResponse response = restClient.sendSync(request);
		JSONArray records = response.asJSONObject().getJSONArray(RECORDS);
		for (int i=0; i<records.length(); i++) {
			JSONObject row = records.getJSONObject(i);
			idToNamesFromServer.put(row.getString(Constants.ID), row.getString(Constants.NAME));
		}
		JSONTestHelper.assertSameJSONObject("Wrong data on server",
                new JSONObject(idToNamesRemotelyUpdated), idToNamesFromServer);
	}

    /**
	 * Create accounts locally, sync up, check smartstore and server afterwards
	 */
	public void testSyncUpWithLocallyCreatedRecords() throws Exception {
		// Create a few entries locally
		String[] names = new String[] { createAccountName(), createAccountName(), createAccountName() };  
		createAccountsLocally(names);
		
		// Sync up
		trySyncUp(3, MergeMode.OVERWRITE);
		
		// Check that db doesn't show entries as locally created anymore and that they use sfdc id
		String namesClause = "('" + TextUtils.join("', '", names) + "')";
		QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec("SELECT {accounts:_soup} FROM {accounts} WHERE {accounts:Name} IN " + namesClause, names.length);
		JSONArray accountsFromDb = smartStore.query(smartStoreQuery, 0);
		Map<String, String> idToNamesCreated = new HashMap<String, String>();
		for (int i=0; i<accountsFromDb.length(); i++) {
			JSONArray row = accountsFromDb.getJSONArray(i);
			JSONObject soupElt = row.getJSONObject(0);
			String id = soupElt.getString(Constants.ID);
			idToNamesCreated .put(id, soupElt.getString(Constants.NAME));
			assertEquals("Wrong local flag", false, soupElt.getBoolean(SyncManager.LOCAL));
			assertEquals("Wrong local flag", false, soupElt.getBoolean(SyncManager.LOCALLY_CREATED));
			assertEquals("Id was not updated", false, id.startsWith(LOCAL_ID_PREFIX));
		}
		
		// Check server
		String soql = "SELECT Id, Name FROM Account WHERE Name IN " + namesClause;
		RestRequest request = RestRequest.getRequestForQuery(ApiVersionStrings.VERSION_NUMBER, soql);
		JSONObject idToNamesFromServer = new JSONObject();
		RestResponse response = restClient.sendSync(request);
		JSONArray records = response.asJSONObject().getJSONArray(RECORDS);
		for (int i=0; i<records.length(); i++) {
			JSONObject row = records.getJSONObject(i);
			idToNamesFromServer.put(row.getString(Constants.ID), row.getString(Constants.NAME));
		}
		JSONTestHelper.assertSameJSONObject("Wrong data on server", new JSONObject(idToNamesCreated), idToNamesFromServer);
		
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
		String idsClause = "('" + TextUtils.join("', '", idsLocallyDeleted) + "')";
		QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec("SELECT {accounts:_soup}, {accounts:Name} FROM {accounts} WHERE {accounts:Id} IN " + idsClause, idsLocallyDeleted.length);
		JSONArray accountsFromDb = smartStore.query(smartStoreQuery, 0);
		assertEquals("No accounts should have been returned from smartstore", 0, accountsFromDb.length());
		
		// Check server
		String soql = "SELECT Id, Name FROM Account WHERE Id IN " + idsClause;
		RestRequest request = RestRequest.getRequestForQuery(ApiVersionStrings.VERSION_NUMBER, soql);
		RestResponse response = restClient.sendSync(request);
		JSONArray records = response.asJSONObject().getJSONArray(RECORDS);
		assertEquals("No accounts should have been returned from server", 0, records.length());
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
		String idsClause = "('" + TextUtils.join("', '", idsLocallyDeleted) + "')";
		QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec("SELECT {accounts:_soup}, {accounts:Name} FROM {accounts} WHERE {accounts:Id} IN " + idsClause, idsLocallyDeleted.length);
		JSONArray accountsFromDb = smartStore.query(smartStoreQuery, 0);
		assertEquals("3 accounts should have been returned from smartstore", 3, accountsFromDb.length());

		// Check server
		String soql = "SELECT Id, Name FROM Account WHERE Id IN " + idsClause;
		RestRequest request = RestRequest.getRequestForQuery(ApiVersionStrings.VERSION_NUMBER, soql);
		RestResponse response = restClient.sendSync(request);
		JSONArray records = response.asJSONObject().getJSONArray(RECORDS);
		assertEquals("3 accounts should have been returned from server", 3, records.length());
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
        TestSyncUpTarget target = new TestSyncUpTarget(false);
        TestSyncUpTarget.setActionCollector(collector);
        trySyncUp(target, 3, MergeMode.OVERWRITE);

        // Check that db doesn't show entries as locally modified anymore
        Set<String> ids = idToNamesLocallyUpdated.keySet();
        String idsClause = "('" + TextUtils.join("', '", ids) + "')";
        QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec("SELECT {accounts:_soup} FROM {accounts} WHERE {accounts:Id} IN " + idsClause, ids.size());
        JSONArray accountsFromDb = smartStore.query(smartStoreQuery, 0);
        for (int i=0; i<accountsFromDb.length(); i++) {
            JSONArray row = accountsFromDb.getJSONArray(i);
            JSONObject soupElt = row.getJSONObject(0);
            assertEquals("Wrong local flag", false, soupElt.getBoolean(SyncManager.LOCAL));
            assertEquals("Wrong local flag", false, soupElt.getBoolean(SyncManager.LOCALLY_UPDATED));
        }

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
        String[] names = new String[]{createAccountName(), createAccountName(), createAccountName()};
        createAccountsLocally(names);

        // Sync up
        TestSyncUpTarget.ActionCollector collector = new TestSyncUpTarget.ActionCollector();
        TestSyncUpTarget target = new TestSyncUpTarget(false);
        TestSyncUpTarget.setActionCollector(collector);
        trySyncUp(target, 3, MergeMode.OVERWRITE);

        // Check that db doesn't show entries as locally created anymore and that they use sfdc id
        String namesClause = "('" + TextUtils.join("', '", names) + "')";
        QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec("SELECT {accounts:_soup} FROM {accounts} WHERE {accounts:Name} IN " + namesClause, names.length);
        JSONArray accountsFromDb = smartStore.query(smartStoreQuery, 0);
        Map<String, String> idToNamesCreated = new HashMap<String, String>();
        for (int i = 0; i < accountsFromDb.length(); i++) {
            JSONArray row = accountsFromDb.getJSONArray(i);
            JSONObject soupElt = row.getJSONObject(0);
            String id = soupElt.getString(Constants.ID);
            idToNamesCreated.put(id, soupElt.getString(Constants.NAME));
            assertEquals("Wrong local flag", false, soupElt.getBoolean(SyncManager.LOCAL));
            assertEquals("Wrong local flag", false, soupElt.getBoolean(SyncManager.LOCALLY_CREATED));
            assertEquals("Id was not updated", false, id.startsWith(LOCAL_ID_PREFIX));
        }

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
        TestSyncUpTarget target = new TestSyncUpTarget(false);
        TestSyncUpTarget.setActionCollector(collector);
        trySyncUp(target, 3, MergeMode.OVERWRITE);

        // Check that db doesn't contain those entries anymore
        String idsClause = "('" + TextUtils.join("', '", idsLocallyDeleted) + "')";
        QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec("SELECT {accounts:_soup}, {accounts:Name} FROM {accounts} WHERE {accounts:Id} IN " + idsClause, idsLocallyDeleted.length);
        JSONArray accountsFromDb = smartStore.query(smartStoreQuery, 0);
        assertEquals("No accounts should have been returned from smartstore", 0, accountsFromDb.length());

        // Check what got synched up
        List<String> idsDeletedByTarget = collector.deletedRecordIds;
        assertEquals("Wrong number of records created by target", 3, idsDeletedByTarget.size());
        for (String idDeleted : idsLocallyDeleted) {
            assertTrue("Id not synched up" + idDeleted, idsDeletedByTarget.contains(idDeleted));
        }
    }

    /**
     * Sync down the test accounts, modify a few, sync up using a failing TestSyncUpTarget, check smartstore
     */
    public void testFailingCustomSyncUpWithLocallyUpdatedRecords() throws Exception {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE);

        // Update a few entries locally
        Map<String, String> idToNamesLocallyUpdated = makeSomeLocalChanges();

        // Sync up
        TestSyncUpTarget.ActionCollector collector = new TestSyncUpTarget.ActionCollector();
        TestSyncUpTarget target = new TestSyncUpTarget(true);
        TestSyncUpTarget.setActionCollector(collector);
        trySyncUp(target, 3, MergeMode.OVERWRITE);

        // Check that db still shows entries as locally modified anymore
        Set<String> ids = idToNamesLocallyUpdated.keySet();
        String idsClause = "('" + TextUtils.join("', '", ids) + "')";
        QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec("SELECT {accounts:_soup} FROM {accounts} WHERE {accounts:Id} IN " + idsClause, ids.size());
        JSONArray accountsFromDb = smartStore.query(smartStoreQuery, 0);
        for (int i=0; i<accountsFromDb.length(); i++) {
            JSONArray row = accountsFromDb.getJSONArray(i);
            JSONObject soupElt = row.getJSONObject(0);
            assertEquals("Wrong local flag", true, soupElt.getBoolean(SyncManager.LOCAL));
            assertEquals("Wrong local flag", true, soupElt.getBoolean(SyncManager.LOCALLY_UPDATED));
        }

        // Check what got synched up
        List<String> idsUpdatedByTarget = collector.updatedRecordIds;
        assertEquals("Wrong number of records updated by target", 0, idsUpdatedByTarget.size());
    }

    /**
     * Create accounts locally, sync up using failing TestSyncUpTarget, check smartstore
     */
    public void testFailingCustomSyncUpWithLocallyCreatedRecords() throws Exception {
        // Create a few entries locally
        String[] names = new String[]{createAccountName(), createAccountName(), createAccountName()};
        createAccountsLocally(names);

        // Sync up
        TestSyncUpTarget.ActionCollector collector = new TestSyncUpTarget.ActionCollector();
        TestSyncUpTarget target = new TestSyncUpTarget(true);
        TestSyncUpTarget.setActionCollector(collector);
        trySyncUp(target, 3, MergeMode.OVERWRITE);

        // Check that db still show show entries as locally created anymore and that they use sfdc id
        String namesClause = "('" + TextUtils.join("', '", names) + "')";
        QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec("SELECT {accounts:_soup} FROM {accounts} WHERE {accounts:Name} IN " + namesClause, names.length);
        JSONArray accountsFromDb = smartStore.query(smartStoreQuery, 0);
        Map<String, String> idToNamesCreated = new HashMap<String, String>();
        for (int i = 0; i < accountsFromDb.length(); i++) {
            JSONArray row = accountsFromDb.getJSONArray(i);
            JSONObject soupElt = row.getJSONObject(0);
            String id = soupElt.getString(Constants.ID);
            idToNamesCreated.put(id, soupElt.getString(Constants.NAME));
            assertEquals("Wrong local flag", true, soupElt.getBoolean(SyncManager.LOCAL));
            assertEquals("Wrong local flag", true, soupElt.getBoolean(SyncManager.LOCALLY_CREATED));
            assertEquals("Id was not updated", true, id.startsWith(LOCAL_ID_PREFIX));
        }

        // Check what got synched up
        List<String> idsCreatedByTarget = collector.createdRecordIds;
        assertEquals("Wrong number of records created by target", 0, idsCreatedByTarget.size());
    }

    /**
     * Sync down the test accounts, delete a few, sync up using failing TestSyncUpTarget, check smartstore
     */
    public void testFailingCustomSyncUpWithLocallyDeletedRecords() throws Exception {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE);

        // Delete a few entries locally
        String[] allIds = idToNames.keySet().toArray(new String[0]);
        String[] idsLocallyDeleted = new String[] { allIds[0], allIds[1], allIds[2] };
        deleteAccountsLocally(idsLocallyDeleted);

        // Sync up
        TestSyncUpTarget.ActionCollector collector = new TestSyncUpTarget.ActionCollector();
        TestSyncUpTarget target = new TestSyncUpTarget(true);
        TestSyncUpTarget.setActionCollector(collector);
        trySyncUp(target, 3, MergeMode.OVERWRITE);

        // Check that db doesn't contain those entries anymore
        String idsClause = "('" + TextUtils.join("', '", idsLocallyDeleted) + "')";
        QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec("SELECT {accounts:_soup}, {accounts:Name} FROM {accounts} WHERE {accounts:Id} IN " + idsClause, idsLocallyDeleted.length);
        JSONArray accountsFromDb = smartStore.query(smartStoreQuery, 0);
        for (int i = 0; i < accountsFromDb.length(); i++) {
            JSONArray row = accountsFromDb.getJSONArray(i);
            JSONObject soupElt = row.getJSONObject(0);
            assertEquals("Wrong local flag", true, soupElt.getBoolean(SyncManager.LOCAL));
            assertEquals("Wrong local flag", true, soupElt.getBoolean(SyncManager.LOCALLY_DELETED));
        }

        // Check what got synched up
        List<String> idsDeletedByTarget = collector.deletedRecordIds;
        assertEquals("Wrong number of records created by target", 0, idsDeletedByTarget.size());
    }

    /**
     * Test addFilterForReSync with various queries
     */
    public void testAddFilterForResync() {
        Date date = new Date();
        long dateLong = date.getTime();
        String dateStr = Constants.TIMESTAMP_FORMAT.format(date);
        assertEquals("Wrong result for addFilterForReSync", "select Id from Account where LastModifiedDate > " + dateStr, SoqlSyncDownTarget.addFilterForReSync("select Id from Account", dateLong));
        assertEquals("Wrong result for addFilterForReSync", "select Id from Account where LastModifiedDate > " + dateStr + " limit 100", SoqlSyncDownTarget.addFilterForReSync("select Id from Account limit 100", dateLong));
        assertEquals("Wrong result for addFilterForReSync", "select Id from Account where LastModifiedDate > " + dateStr + " and Name = 'John'", SoqlSyncDownTarget.addFilterForReSync("select Id from Account where Name = 'John'", dateLong));
        assertEquals("Wrong result for addFilterForReSync", "select Id from Account where LastModifiedDate > " + dateStr + " and Name = 'John' limit 100", SoqlSyncDownTarget.addFilterForReSync("select Id from Account where Name = 'John' limit 100", dateLong));
        assertEquals("Wrong result for addFilterForReSync", "SELECT Id FROM Account where LastModifiedDate > " + dateStr, SoqlSyncDownTarget.addFilterForReSync("SELECT Id FROM Account", dateLong));
        assertEquals("Wrong result for addFilterForReSync", "SELECT Id FROM Account where LastModifiedDate > " + dateStr + " LIMIT 100", SoqlSyncDownTarget.addFilterForReSync("SELECT Id FROM Account LIMIT 100", dateLong));
        assertEquals("Wrong result for addFilterForReSync", "SELECT Id FROM Account WHERE LastModifiedDate > " + dateStr + " and Name = 'John'", SoqlSyncDownTarget.addFilterForReSync("SELECT Id FROM Account WHERE Name = 'John'", dateLong));
        assertEquals("Wrong result for addFilterForReSync", "SELECT Id FROM Account WHERE LastModifiedDate > " + dateStr + " and Name = 'John' LIMIT 100", SoqlSyncDownTarget.addFilterForReSync("SELECT Id FROM Account WHERE Name = 'John' LIMIT 100", dateLong));
    }

	/**
	 * Sync down helper
	 * @throws JSONException
     * @param mergeMode
	 */
	private long trySyncDown(MergeMode mergeMode) throws JSONException {
		// Ids clause
		String idsClause = "('" + TextUtils.join("', '", idToNames.keySet()) + "')";
		
		// Create sync
		SyncDownTarget target = SoqlSyncDownTarget.targetForSOQLSyncDown("SELECT Id, Name, LastModifiedDate FROM Account WHERE Id IN " + idsClause);
        SyncOptions options = SyncOptions.optionsForSyncDown(mergeMode);
		SyncState sync = SyncState.createSyncDown(smartStore, target, options, ACCOUNTS_SOUP);
		long syncId = sync.getId();
		checkStatus(sync, SyncState.Type.syncDown, syncId, target, options, SyncState.Status.NEW, 0, -1);
		
		// Run sync
		SyncUpdateCallbackQueue queue = new SyncUpdateCallbackQueue();
		syncManager.runSync(sync, queue);

		// Check status updates
		checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0, -1); // we get an update right away before getting records to sync
		checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0, idToNames.size());
		checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.DONE, 100, idToNames.size());

        return syncId;
	}

    /**
     * Sync up helper
     * @param numberChanges
     * @param mergeMode
     * @throws JSONException
     */
    private void trySyncUp(int numberChanges, MergeMode mergeMode) throws JSONException {
        trySyncUp(SyncUpTarget.defaultSyncUpTarget(), numberChanges, mergeMode);
    }
	/**
	 * Sync up helper
     * @oaram target
	 * @param numberChanges
	 * @param mergeMode
	 * @throws JSONException
	 */
	private void trySyncUp(SyncUpTarget target, int numberChanges, MergeMode mergeMode) throws JSONException {
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
		for (int i = 1; i < numberChanges; i++) {
			checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncUp, syncId, target, options, SyncState.Status.RUNNING, i*100/numberChanges, numberChanges);
		}
		checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncUp, syncId, target, options, SyncState.Status.DONE, 100, numberChanges);
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
		assertEquals("Wrong total size", expectedTotalSize, sync.getTotalSize());
	}
	
	/**
	 * Helper methods to create "count" test accounts
	 * @param count
	 * @return map of id to name for the created accounts
	 * @throws Exception
	 */
	private Map<String, String> createTestAccountsOnServer(int count) throws Exception {
		Map<String, String> idToNames = new HashMap<String, String>();
		for (int i=0; i<count; i++) {
			// Request
			String name = createAccountName();
			Map<String, Object> fields = new HashMap<String, Object>();
			fields.put(Constants.NAME, name);
			RestRequest request = RestRequest.getRequestForCreate(ApiVersionStrings.VERSION_NUMBER, Constants.ACCOUNT, fields);
			// Response
			RestResponse response = restClient.sendSync(request);
			String id = response.asJSONObject().getString(LID);
			idToNames.put(id, name);
		}
		return idToNames;
	}

	/**
	 * Delete accounts specified in idToNames
	 * @param idToNames
	 * @throws Exception
	 */
	private void deleteTestAccountsOnServer(Map<String, String> idToNames) throws Exception {
		for (String id : idToNames.keySet()) {
			RestRequest request = RestRequest.getRequestForDelete(ApiVersionStrings.VERSION_NUMBER, Constants.ACCOUNT, id);
			restClient.sendSync(request);
		}
	}

	
	/**
	 * @return account name of the form SyncManagerTest<random number left-padded to be 8 digits long>
	 */
	@SuppressWarnings("resource")
	private String createAccountName() {
		StringBuilder sb = new StringBuilder();
		Formatter formatter = new Formatter(sb, Locale.US);
		formatter.format("SyncManagerTest%08d", (int) (Math.random()*10000000));
		String name = sb.toString();
		return name;
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
    	final IndexSpec[] indexSpecs = {
    			new IndexSpec(Constants.ID, SmartStore.Type.string),
    			new IndexSpec(Constants.NAME, SmartStore.Type.string),
    			new IndexSpec(SyncManager.LOCAL, SmartStore.Type.string)
    	};    	
    	smartStore.registerSoup(ACCOUNTS_SOUP, indexSpecs);
	}
	
	/**
	 * Drop soup for accounts
	 */
	private void dropAccountsSoup() {
		smartStore.dropSoup(ACCOUNTS_SOUP);
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
            RestRequest request = RestRequest.getRequestForUpdate(ApiVersionStrings.VERSION_NUMBER, Constants.ACCOUNT, id, fields);
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
     * Check db
     * @throws JSONException
     * @param expectedIdToNames
     */
    private void checkDb(Map<String, String> expectedIdToNames) throws JSONException {
        String idsClause = "('" + TextUtils.join("', '", expectedIdToNames.keySet()) + "')";
        QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec("SELECT {accounts:Id}, {accounts:Name} FROM {accounts} WHERE {accounts:Id} IN " + idsClause, COUNT_TEST_ACCOUNTS);
        JSONArray accountsFromDb = smartStore.query(smartStoreQuery, 0);
        JSONObject idToNamesFromDb = new JSONObject();
        for (int i=0; i<accountsFromDb.length(); i++) {
            JSONArray row = accountsFromDb.getJSONArray(i);
            idToNamesFromDb.put(row.getString(0), row.getString(1));
        }
        JSONTestHelper.assertSameJSONObject("Wrong data in db", new JSONObject(expectedIdToNames), idToNamesFromDb);
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
}
