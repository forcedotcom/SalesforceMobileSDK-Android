/*
 * Copyright (c) 2014, salesforce.com, inc.
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

import java.util.Formatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Intent;
import android.content.IntentFilter;

import com.google.common.base.Joiner;
import com.salesforce.androidsdk.rest.ApiVersionStrings;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartsync.manager.SyncManager.Type;
import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.androidsdk.util.test.BroadcastListenerQueue;
import com.salesforce.androidsdk.util.test.JSONTestHelper;


/**
 * Test class for SyncManager.
 */
public class SyncManagerTest extends ManagerTestCase {

	private static final String TYPE = "type";
	private static final String RECORDS = "records";
	private static final String LID = "id"; // lower case id in create response

	// Local
	private static final String LOCAL_ID_PREFIX = "local_";
	private static final String FIELDLIST = "fieldlist";
	private static final String ACCOUNTS_SOUP = "accounts";
	
	// Misc
	private static final int COUNT_TEST_ACCOUNTS = 10;
	
	private Map<String, String> idToNames;
	private BroadcastListenerQueue broadcastQueue;

	@Override
    public void setUp() throws Exception {
    	super.setUp();
    	broadcastQueue = setupBroadcastListenerQueue();
    	createAccountsSoup();
    	idToNames = createTestAccountsOnServer(COUNT_TEST_ACCOUNTS);
    }
    
    @Override 
    public void tearDown() throws Exception {
    	deleteTestAccountsOnServer(idToNames);
    	dropAccountsSoup();
    	deleteSyncs();
    	tearDownBroadcastListenerQueue(broadcastQueue);
    	super.tearDown();
    }
	
	/**
	 * getSyncStatus should return null for invalid sync id
	 * @throws JSONException
	 */
	public void testGetSyncStatusForInvalidSyncId() throws JSONException {
		JSONObject syncStatus = syncManager.getSyncStatus(-1);
		assertNull("Sync status should be null", syncStatus);
	}
	
	/**
	 * Sync down the test accounts, check smart store, check status during sync
	 */
	public void testSyncDown() throws Exception {
		// first sync down
		trySyncDown();

		
		// Check that db was correctly populated
		String idsClause = "('" + Joiner.on("', '").join(idToNames.keySet()) + "')";
		QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec("SELECT {accounts:Id}, {accounts:Name} FROM {accounts} WHERE {accounts:Id} IN " + idsClause, COUNT_TEST_ACCOUNTS);
		JSONArray accountsFromDb = smartStore.query(smartStoreQuery, 0);
		JSONObject idToNamesFromDb = new JSONObject();
		for (int i=0; i<accountsFromDb.length(); i++) {
			JSONArray row = accountsFromDb.getJSONArray(i);
			idToNamesFromDb.put(row.getString(0), row.getString(1));
		}
		JSONTestHelper.assertSameJSONObject("Wrong data in db", new JSONObject(idToNames), idToNamesFromDb);
	}

	/**
	 * Sync down the test accounts, modify a few, sync up, check smartstore and server afterwards
	 */
	public void testSyncUpWithLocallyUpdatedRecords() throws Exception {
		// First sync down
		trySyncDown();
		
		// Update a few entries locally
		Map<String, String> idToNamesLocallyUpdated = new HashMap<String, String>();
		String[] allIds = idToNames.keySet().toArray(new String[0]);
		String[] ids = new String[] { allIds[0], allIds[1], allIds[2] };
		for (int i = 0; i < ids.length; i++) {
			String id = ids[i];
			idToNamesLocallyUpdated.put(id, idToNames.get(id) + "_updated");
		}
		updateAccountsLocally(idToNamesLocallyUpdated);
		
		// Sync up
		trySyncUp(3);
		
		// Check that db doesn't show entries as locally modified anymore
		String idsClause = "('" + Joiner.on("', '").join(ids) + "')";
		QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec("SELECT {accounts:_soup} FROM {accounts} WHERE {accounts:Id} IN " + idsClause, ids.length);
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
	 * Create accounts locally, sync up, check smartstore and server afterwards
	 */
	public void testSyncUpWithLocallyCreatedRecords() throws Exception {
		// Create a few entries locally
		String[] names = new String[] { createAccountName(), createAccountName(), createAccountName() };  
		createAccountsLocally(names);
		
		// Sync up
		trySyncUp(3);
		
		// Check that db doesn't show entries as locally created anymore and that they use sfdc id
		String namesClause = "('" + Joiner.on("', '").join(names) + "')";
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
			assertEquals("Id was not udpated", false, id.startsWith(LOCAL_ID_PREFIX));
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
		trySyncDown();
		
		// Delete a few entries locally
		String[] allIds = idToNames.keySet().toArray(new String[0]);
		String[] idsLocallyDeleted = new String[] { allIds[0], allIds[1], allIds[2] };
		deleteAccountsLocally(idsLocallyDeleted);
		
		// Sync up
		trySyncUp(3);
		
		// Check that db doesn't contain those entries anymore
		String idsClause = "('" + Joiner.on("', '").join(idsLocallyDeleted) + "')";
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
	 * Sync down helper
	 * @throws JSONException
	 */
	private void trySyncDown() throws JSONException {
		// Ids clause
		String idsClause = "('" + Joiner.on("', '").join(idToNames.keySet()) + "')";
		
		// Create sync
		JSONObject target = new JSONObject();
		target.put(SyncManager.QUERY_TYPE, SyncManager.QueryType.soql);
		String soqlQuery = "SELECT Id, Name FROM Account WHERE Id IN " + idsClause;
		target.put(SyncManager.QUERY, soqlQuery);
		JSONObject sync = syncManager.recordSync(SyncManager.Type.syncDown, target, ACCOUNTS_SOUP, null);
		long syncId = getSyncId(sync);
		checkStatus(sync, SyncManager.Type.syncDown, syncId, target, null, SyncManager.Status.NEW, 0);
		
		// Run sync
		syncManager.runSync(syncId);
		checkStatus(syncManager.getSyncStatus(syncId), SyncManager.Type.syncDown, syncId, target, null, SyncManager.Status.RUNNING, 0);
	
		// Wait for running broadcast
		checkStatus(waitForNextBroadcast(), SyncManager.Type.syncDown, syncId, target, null, SyncManager.Status.RUNNING, 0);

		// Wait for done broadcast
		checkStatus(waitForNextBroadcast(), SyncManager.Type.syncDown, syncId, target, null, SyncManager.Status.DONE, 100);
	}

	/**
	 * Sync up helper
	 * @param numberChanges
	 * @throws JSONException
	 */
	private void trySyncUp(int numberChanges) throws JSONException {
		// Create sync
		JSONObject options = new JSONObject();
		JSONArray fieldlist = new JSONArray();
		fieldlist.put(Constants.NAME);
		options.put(FIELDLIST, fieldlist);
		JSONObject sync = syncManager.recordSync(SyncManager.Type.syncUp, null, ACCOUNTS_SOUP, options);
		long syncId = getSyncId(sync);
		checkStatus(sync, SyncManager.Type.syncUp, syncId, null, options, SyncManager.Status.NEW, 0);
		
		// Run sync
		syncManager.runSync(syncId);
		checkStatus(syncManager.getSyncStatus(syncId), SyncManager.Type.syncUp, syncId, null, options, SyncManager.Status.RUNNING, 0);
		
		for (int i=0; i<numberChanges; i++) {
			checkStatus(waitForNextBroadcast(), SyncManager.Type.syncUp, syncId, null, options, SyncManager.Status.RUNNING, i*100/numberChanges);
		}
		
		// Wait for done update
		checkStatus(waitForNextBroadcast(), SyncManager.Type.syncUp, syncId, null, options, SyncManager.Status.DONE, 100);
	}

	/**
	 * Blocks until next sync broadcast
	 * @return sync from broadcast
	 * @throws JSONException
	 */
	private JSONObject waitForNextBroadcast() throws JSONException {
		Intent intent = broadcastQueue.getNextBroadcast();
		return new JSONObject(intent.getStringExtra(SyncManager.SYNC_AS_STRING));
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
	private void checkStatus(JSONObject sync, Type expectedType, long expectedId, JSONObject expectedTarget, JSONObject expectedOptions, SyncManager.Status expectedStatus, int expectedProgress) throws JSONException {
		assertEquals("Wrong type", expectedType.name(), sync.getString(SyncManager.SYNC_TYPE));
		assertEquals("Wrong id", expectedId, sync.getLong(SmartStore.SOUP_ENTRY_ID));
		JSONTestHelper.assertSameJSON("Wrong target", expectedTarget, sync.optJSONObject(SyncManager.SYNC_TARGET));
		JSONTestHelper.assertSameJSON("Wrong options", expectedOptions, sync.optJSONObject(SyncManager.SYNC_OPTIONS));
		assertEquals("Wrong status", expectedStatus.name(), sync.getString(SyncManager.SYNC_STATUS));
		assertEquals("Wrong progress", expectedProgress, sync.optInt(SyncManager.SYNC_PROGRESS, 0));
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
		smartStore.clearSoup(SyncManager.SYNCS_SOUP);
	}

	/**
	 * Setup broadcast listener queue to synchronize code with broadcasts from SyncManager
	 * @return
	 */
	private BroadcastListenerQueue setupBroadcastListenerQueue() {
		BroadcastListenerQueue queue = new BroadcastListenerQueue(); 
		targetContext.registerReceiver(queue, new IntentFilter(SyncManager.SYNC_INTENT_ACTION));
		return queue;
	}

	/**
	 * Unregister broadcast listener queue from broadcasts from SyncManager
	 */
	private void tearDownBroadcastListenerQueue(BroadcastListenerQueue queue) {
		targetContext.unregisterReceiver(queue);
	}
	
	/**
	 * @param sync
	 * @return syn id
	 * @throws JSONException 
	 */
	private long getSyncId(JSONObject sync) throws JSONException {
		return sync.getLong(SmartStore.SOUP_ENTRY_ID);
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
	 * @param id
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
}
