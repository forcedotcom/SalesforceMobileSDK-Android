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
import com.salesforce.androidsdk.util.BroadcastListenerQueue;
import com.salesforce.androidsdk.util.JSONTestHelper;


/**
 * Test class for SyncManager.
 */
public class SyncManagerTest extends ManagerTestCase {
	
    private static final String ACCOUNTS_SOUP = "accounts";
	private static final String NAME = "Name";
	private static final String ID = "Id";
	private static final int COUNT_TEST_ACCOUNTS = 10;
	
	private Map<String, String> idToNames;
	private BroadcastListenerQueue broadcastQueue;

	@Override
    public void setUp() throws Exception {
    	super.setUp();
    	broadcastQueue = setupBroadcastListenerQueue();
    	createAccountsSoup();
    	idToNames = createTestAccounts(COUNT_TEST_ACCOUNTS);
    }
    
    @Override 
    public void tearDown() throws Exception {
    	deleteTestAccounts(idToNames);
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
		// Ids clause
		String idsClause = "('" + Joiner.on("', '").join(idToNames.keySet()) + "')";
		
		// Create sync
		JSONObject target = new JSONObject();
		target.put(SyncManager.QUERY_TYPE, SyncManager.QueryType.soql);
		String soqlQuery = "SELECT Id, Name FROM Account WHERE Id IN " + idsClause;
		target.put(SyncManager.QUERY, soqlQuery);
		JSONObject sync = syncManager.recordSync(SyncManager.Type.syncDown, target, ACCOUNTS_SOUP, null);
		long syncId = getSyncId(sync);
		checkStatus(sync, SyncManager.Type.syncDown, syncId, target, SyncManager.Status.NEW, 0);
		
		// Run sync
		syncManager.runSync(syncId);
		checkStatus(syncManager.getSyncStatus(syncId), SyncManager.Type.syncDown, syncId, target, SyncManager.Status.RUNNING, 0);

		// Wait for initial update
		Intent intent = broadcastQueue.getNextBroadcast();
		JSONObject syncFromIntent = new JSONObject(intent.getStringExtra(SyncManager.SYNC_AS_STRING));
		checkStatus(syncFromIntent, SyncManager.Type.syncDown, syncId, target, SyncManager.Status.RUNNING, 0);
		
		// Wait for next update
		intent = broadcastQueue.getNextBroadcast();
		syncFromIntent = new JSONObject(intent.getStringExtra(SyncManager.SYNC_AS_STRING));
		checkStatus(syncFromIntent, SyncManager.Type.syncDown, syncId, target, SyncManager.Status.RUNNING, 100);

		// Wait for last update
		intent = broadcastQueue.getNextBroadcast();
		syncFromIntent = new JSONObject(intent.getStringExtra(SyncManager.SYNC_AS_STRING));
		checkStatus(syncFromIntent, SyncManager.Type.syncDown, syncId, target, SyncManager.Status.DONE, 100);

		
		// Check that db was correctly populated
		QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec("SELECT {accounts:Id}, {accounts:Name} FROM {accounts} WHERE {accounts:Id} IN " + idsClause, COUNT_TEST_ACCOUNTS);
		JSONArray accountsFromDb = smartStore.query(smartStoreQuery, 0);
		JSONObject idToNamesFromDb = new JSONObject();
		for (int i=0; i<accountsFromDb.length(); i++) {
			JSONArray row = accountsFromDb.getJSONArray(i);
			idToNamesFromDb.put(row.getString(0), row.getString(1));
		}
		JSONTestHelper.assertSameJSONObject("Wrong data in db", new JSONObject(idToNames), idToNamesFromDb);
	}
	
	private void checkStatus(JSONObject sync, Type expectedType, long expectedId, JSONObject expectedTarget, SyncManager.Status expectedStatus, int expectedProgress) throws JSONException {
		assertEquals("Wrong type", expectedType.name(), sync.getString(SyncManager.SYNC_TYPE));
		assertEquals("Wrong id", expectedId, sync.getLong(SmartStore.SOUP_ENTRY_ID));
		JSONTestHelper.assertSameJSON("Wrong target", expectedTarget, sync.getJSONObject(SyncManager.SYNC_TARGET));
		assertEquals("Wrong status", expectedStatus.name(), sync.getString(SyncManager.SYNC_STATUS));
		assertEquals("Wrong progress", expectedProgress, sync.optInt(SyncManager.SYNC_PROGRESS, 0));
	}
	
	/**
	 * Helper methods to create "count" test accounts
	 * @param count
	 * @return map of id to name for the created accounts
	 * @throws Exception
	 */
	private Map<String, String> createTestAccounts(int count) throws Exception {
		Map<String, String> idToNames = new HashMap<String, String>();
		Map<String, Object> fields = new HashMap<String, Object>();
		int base = (int) (Math.random()*1000000);
		for (int i=0; i<count; i++) {
			// Request
			String name = createAccountName(base, i);
			fields.put(NAME, name);
			RestRequest request = RestRequest.getRequestForCreate(ApiVersionStrings.VERSION_NUMBER, "Account", fields);
			// Response
			RestResponse response = restClient.sendSync(request);
			String id = response.asJSONObject().getString("id");
			idToNames.put(id, name);
		}
		return idToNames;
	}

	/**
	 * Delete accounts specified in idToNames
	 * @param idToNames
	 * @throws Exception
	 */
	private void deleteTestAccounts(Map<String, String> idToNames) throws Exception {
		for (String id : idToNames.keySet()) {
			RestRequest request = RestRequest.getRequestForDelete(ApiVersionStrings.VERSION_NUMBER, "Account", id);
			restClient.sendSync(request);
		}
	}

	
	/**
	 * @param base
	 * @param i
	 * @return account name of the form SyncManagerTest<base + i left-padded to be 7 digits long>
	 */
	@SuppressWarnings("resource")
	private String createAccountName(int base, int i) {
		StringBuilder sb = new StringBuilder();
		Formatter formatter = new Formatter(sb, Locale.US);
		formatter.format("SyncManagerTest%07d", base + i);
		String name = sb.toString();
		return name;
	}
	
	/**
	 * Create soup for accounts
	 */
	private void createAccountsSoup() {
    	final IndexSpec[] indexSpecs = {
    			new IndexSpec(ID, SmartStore.Type.string),
    			new IndexSpec(NAME, SmartStore.Type.string)
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
}
