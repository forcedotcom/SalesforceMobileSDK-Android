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
package com.salesforce.androidsdk.smartsync.manager;

import android.text.TextUtils;

import com.salesforce.androidsdk.rest.ApiVersionStrings;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartsync.target.SyncDownTarget;
import com.salesforce.androidsdk.smartsync.target.SyncTarget;
import com.salesforce.androidsdk.smartsync.target.SyncUpTarget;
import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.androidsdk.smartsync.util.SyncOptions;
import com.salesforce.androidsdk.smartsync.util.SyncState;
import com.salesforce.androidsdk.smartsync.util.SyncUpdateCallbackQueue;
import com.salesforce.androidsdk.util.JSONObjectHelper;
import com.salesforce.androidsdk.util.test.JSONTestHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Abstract super class for all SyncManager test classes.
 */
abstract public class SyncManagerTestCase extends ManagerTestCase {

    protected static final String TYPE = "type";
    protected static final String RECORDS = "records";
    protected static final String LOCAL_ID_PREFIX = "local_";
    protected static final String ACCOUNTS_SOUP = "accounts";
    protected static final int TOTAL_SIZE_UNKNOWN = -2;
    protected static final String REMOTELY_UPDATED = "_r_upd";
    protected static final String LOCALLY_UPDATED = "_l_upd";

    @Override
    public void tearDown() throws Exception {
        deleteSyncs();
        deleteGlobalSyncs();
        super.tearDown();
    }

    /**
     * Create soup for accounts
     */
    protected void createAccountsSoup() {
        createAccountsSoup(ACCOUNTS_SOUP);
    }

    protected void createAccountsSoup(String soupName) {
        final IndexSpec[] indexSpecs = {
                new IndexSpec(Constants.ID, SmartStore.Type.string),
                new IndexSpec(Constants.NAME, SmartStore.Type.string),
                new IndexSpec(Constants.DESCRIPTION, SmartStore.Type.string),
                new IndexSpec(SyncTarget.LOCAL, SmartStore.Type.string),
                new IndexSpec(SyncTarget.SYNC_ID, SmartStore.Type.integer)
        };
        smartStore.registerSoup(soupName, indexSpecs);
    }

    /**
     * Drop soup for accounts
     */
    protected void dropAccountsSoup() {
        dropAccountsSoup(ACCOUNTS_SOUP);
    }

    protected void dropAccountsSoup(String soupName) {
        smartStore.dropSoup(soupName);
    }

    /**
     * Delete all syncs in syncs_soup
     */
    protected void deleteSyncs() {
        smartStore.clearSoup(SyncState.SYNCS_SOUP);
    }

    /**
     * Delete all syncs in syncs_soup
     */
    protected void deleteGlobalSyncs() {
        globalSmartStore.clearSoup(SyncState.SYNCS_SOUP);
    }

    /**
     * @return local id of the form local_number where number is different every time and increasing
     */
    protected String createLocalId() {
        StringBuilder sb = new StringBuilder();
        Formatter formatter = new Formatter(sb, Locale.US);
        formatter.format(LOCAL_ID_PREFIX + System.nanoTime());
        String name = sb.toString();
        return name;
    }

    /**
     * Create accounts locally
     *
     * @param names
     * @return created accounts records
     * @throws JSONException
     */
    protected JSONObject[] createAccountsLocally(String[] names) throws JSONException {
        JSONObject[] createdAccounts = new JSONObject[names.length];
        JSONObject attributes = new JSONObject();
        attributes.put(TYPE, Constants.ACCOUNT);
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            JSONObject account = new JSONObject();
            account.put(Constants.ID, createLocalId());
            account.put(Constants.NAME, name);
            account.put(Constants.DESCRIPTION, "Description_" + name);
            account.put(Constants.ATTRIBUTES, attributes);
            account.put(SyncTarget.LOCAL, true);
            account.put(SyncTarget.LOCALLY_CREATED, true);
            account.put(SyncTarget.LOCALLY_DELETED, false);
            account.put(SyncTarget.LOCALLY_UPDATED, false);
            createdAccounts[i] = smartStore.create(ACCOUNTS_SOUP, account);
        }
        return createdAccounts;
    }

    protected boolean tryCleanResyncGhosts(long syncId) throws JSONException, InterruptedException {
        final ArrayBlockingQueue<Boolean> queue = new ArrayBlockingQueue<>(1);

        syncManager.cleanResyncGhosts(syncId, new SyncManager.CleanResyncGhostsCallback() {
            @Override
            public void onSuccess(int numRecords) {
                queue.offer(true);
            }

            @Override
            public void onError(Exception e) {
                queue.offer(false);
            }
        });

        return queue.take();
    }

    /**
     * Sync down helper.
     *
     * @param mergeMode
     * @param target
     * @param soupName
     * @param totalSize
     * @param numberFetches
     * @return
     * @throws JSONException
     */
    protected long trySyncDown(SyncState.MergeMode mergeMode, SyncDownTarget target, String soupName, int totalSize, int numberFetches) throws JSONException {
        return trySyncDown(mergeMode, target, soupName, totalSize, numberFetches, null);
    }

    /**
     * Sync down helper.
     *
     * @param mergeMode     Merge mode.
     * @param target        Sync down target.
     * @param soupName      Soup name.
     * @param totalSize     Expected total size.
     * @param numberFetches Expected number of fetches.
     * @param syncName      Name for sync or null.
     * @return Sync ID.
     */
    protected long trySyncDown(SyncState.MergeMode mergeMode, SyncDownTarget target, String soupName, int totalSize, int numberFetches, String syncName) throws JSONException {
        final SyncOptions options = SyncOptions.optionsForSyncDown(mergeMode);
        final SyncState sync = SyncState.createSyncDown(smartStore, target, options, soupName, syncName);
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
     * Check that records were deleted from db
     *
     * @param soupName
     * @param ids
     * @param idField
     * @throws JSONException
     */
    protected void checkDbDeleted(String soupName, String[] ids, String idField) throws JSONException {
        QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec("SELECT {" + soupName + ":_soup} FROM {" + soupName + "} WHERE {" + soupName + ":" + idField + "} IN " + makeInClause(ids), ids.length);
        JSONArray records = smartStore.query(smartStoreQuery, 0);
        Assert.assertEquals("No records should have been returned from smartstore", 0, records.length());
    }

    /**
     * Check that records exist in db
     *
     * @param soupName
     * @param ids
     * @param idField
     * @throws JSONException
     */
    protected void checkDbExist(String soupName, String[] ids, String idField) throws JSONException {
        QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec("SELECT {" + soupName + ":_soup} FROM {" + soupName + "} WHERE {" + soupName + ":" + idField + "} IN " + makeInClause(ids), ids.length);
        JSONArray records = smartStore.query(smartStoreQuery, 0);
        Assert.assertEquals("All records should have been returned from smartstore", ids.length, records.length());
    }

    /**
     * Check relationships field of children
     * @param childrenIds
     * @param expectedParentId
     * @param soupName
     * @param idFieldName
     * @param parentIdFieldName
     */
    protected void checkDbRelationships(Collection<String> childrenIds, String expectedParentId, String soupName, String idFieldName, String parentIdFieldName) throws JSONException {
        QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec("SELECT {" + soupName + ":_soup} FROM {" + soupName + "} WHERE {" + soupName + ":" + idFieldName + "} IN " + makeInClause(childrenIds), childrenIds.size());
        JSONArray rows = smartStore.query(smartStoreQuery, 0);
        Assert.assertEquals("All records should have been returned from smartstore", childrenIds.size(), rows.length());
        for (int i = 0; i < rows.length(); i++) {
            JSONObject childRecord = rows.getJSONArray(i).getJSONObject(0);
            Assert.assertEquals("Wrong parent id", expectedParentId, childRecord.getString(parentIdFieldName));
        }
    }

    protected String makeInClause(String[] values) {
        return makeInClause(Arrays.asList(values));
    }

    protected String makeInClause(Collection<String> values) {
        return "('" + TextUtils.join("', '", values) + "')";
    }

    protected long trySyncDown(SyncState.MergeMode mergeMode, SyncDownTarget target, String soupName) throws JSONException {
        return trySyncDown(mergeMode, target, soupName, TOTAL_SIZE_UNKNOWN, 1);
    }

    /**
     * Helper method to check sync state
     *
     * @param sync
     * @param expectedType
     * @param expectedId
     * @param expectedTarget
     * @param expectedOptions
     * @param expectedStatus
     * @param expectedProgress
     * @throws JSONException
     */
    protected void checkStatus(SyncState sync, SyncState.Type expectedType, long expectedId, SyncTarget expectedTarget, SyncOptions expectedOptions, SyncState.Status expectedStatus, int expectedProgress, int expectedTotalSize) throws JSONException {
        Assert.assertEquals("Wrong type", expectedType, sync.getType());
        Assert.assertEquals("Wrong id", expectedId, sync.getId());
        JSONTestHelper.assertSameJSON("Wrong target", (expectedTarget == null ? null : expectedTarget.asJSON()), (sync.getTarget() == null ? null : sync.getTarget().asJSON()));
        JSONTestHelper.assertSameJSON("Wrong options", (expectedOptions == null ? null : expectedOptions.asJSON()), (sync.getOptions() == null ? null : sync.getOptions().asJSON()));
        Assert.assertEquals("Wrong status", expectedStatus, sync.getStatus());
        Assert.assertEquals("Wrong progress", expectedProgress, sync.getProgress());
        if (expectedTotalSize != TOTAL_SIZE_UNKNOWN) {
            Assert.assertEquals("Wrong total size", expectedTotalSize, sync.getTotalSize());
        }
        if (sync.getStatus() != SyncState.Status.NEW) {
            Assert.assertTrue("Wrong start time", sync.getStartTime() > 0);
        }
        if (sync.getStatus() == SyncState.Status.DONE || sync.getStatus() == SyncState.Status.FAILED) {
            Assert.assertTrue("Wrong end time", sync.getEndTime() > 0);
        }
    }

    protected void checkStatus(SyncState sync, SyncState.Type expectedType, long expectedId, SyncTarget expectedTarget, SyncOptions expectedOptions, SyncState.Status expectedStatus, int expectedProgress) throws JSONException {
        checkStatus(sync, expectedType, expectedId, expectedTarget, expectedOptions, expectedStatus, expectedProgress, TOTAL_SIZE_UNKNOWN);
    }

    /**
     * Check records in db
     * @throws JSONException
     * @param expectedIdToFields
     * @param soupName
     */
    protected void checkDb(Map<String, Map<String, Object>> expectedIdToFields, String soupName) throws JSONException {
        String sql = String.format("SELECT {%s:_soup} FROM {%s} WHERE {%s:Id} IN %s",
                soupName, soupName, soupName, makeInClause(expectedIdToFields.keySet()));
        QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec(sql, Integer.MAX_VALUE);
        JSONArray rows = smartStore.query(smartStoreQuery, 0);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject recordFromDb = rows.getJSONArray(i).getJSONObject(0);
            String recordId = recordFromDb.getString("Id");
            Map<String, Object> expectedFields = expectedIdToFields.get(recordId);
            for (String fieldName : expectedFields.keySet()) {
                Assert.assertEquals(String.format("Wrong data in db for field %s on record %s", fieldName, recordId),
                        expectedFields.get(fieldName).toString(), recordFromDb.get(fieldName).toString());
            }
        }
    }

    /**
     * Make remote changes
     * @throws JSONException
     * @param idToFields
     * @param sObjectType
     */
    protected Map<String, Map<String, Object>> makeRemoteChanges(Map<String, Map<String, Object>> idToFields, String sObjectType) throws Exception {
        String[] allIds = idToFields.keySet().toArray(new String[0]);
        Arrays.sort(allIds); // to make the status updates sequence deterministic
        String[] idsToUpdate = new String[] {allIds[0], allIds[2]};
        return makeRemoteChanges(idToFields, sObjectType, idsToUpdate);
    }

    /**
     * Make remote changes
     * @param idToFields
     * @param sObjectType
     * @param idsToUpdate
     * @return
     * @throws Exception
     */
    protected Map<String, Map<String, Object>> makeRemoteChanges(Map<String, Map<String, Object>> idToFields, String sObjectType, String[] idsToUpdate) throws Exception {
        Map<String, Map<String, Object>> idToFieldsUpdated = prepareSomeChanges(idToFields, idsToUpdate, REMOTELY_UPDATED);
        Thread.sleep(1000); // time stamp precision is in seconds
        updateRecordsOnServer(idToFieldsUpdated, sObjectType);
        return idToFieldsUpdated;
    }


    /**
     * Helper method to prepare updated maps of field name to field value
     * @param idToFields
     * @param idsToUpdate
     * @param suffix
     * @return
     */
    protected Map<String, Map<String, Object>> prepareSomeChanges(Map<String, Map<String, Object>> idToFields, String[] idsToUpdate, String suffix) {
        Map<String, Map<String, Object>> idToFieldsUpdated = new HashMap<>();
        for (String idToUpdate : idsToUpdate) {
            idToFieldsUpdated.put(idToUpdate, updatedFields(idToFields.get(idToUpdate), suffix));
        }
        return idToFieldsUpdated;
    }

    /**
     * Helper method to update fields in a map of field name to field value
     * @param fields
     * @param suffix
     * @return
     */
    protected Map<String, Object> updatedFields(Map<String, Object> fields, String suffix) {
        Set<String> fieldNamesUpdatable = new HashSet<>(Arrays.asList(new String[] {Constants.NAME, Constants.DESCRIPTION, Constants.LAST_NAME}));
        Map<String, Object> updatedFields = new HashMap<>();
        for (String fieldName : fields.keySet()) {
            if (fieldNamesUpdatable.contains(fieldName)) {
                updatedFields.put(fieldName, fields.get(fieldName) + suffix);
            }
        }
        return updatedFields;
    }

    /**
	 * Update records locally
	 * @param idToFieldsLocallyUpdated
	 * @param soupName
     * @throws JSONException
	 */
    protected void updateRecordsLocally(Map<String, Map<String, Object>> idToFieldsLocallyUpdated, String soupName) throws JSONException {
		for (String id : idToFieldsLocallyUpdated.keySet()) {
            Map<String, Object> updatedFields = idToFieldsLocallyUpdated.get(id);
			JSONObject record = smartStore.retrieve(soupName, smartStore.lookupSoupEntryId(soupName, Constants.ID, id)).getJSONObject(0);
            for (String fieldName : updatedFields.keySet()) {
                record.put(fieldName, updatedFields.get(fieldName));
            }
			record.put(SyncTarget.LOCAL, true);
			record.put(SyncTarget.LOCALLY_CREATED, false);
			record.put(SyncTarget.LOCALLY_DELETED, false);
			record.put(SyncTarget.LOCALLY_UPDATED, true);
			smartStore.upsert(soupName, record);
		}
	}

    /**
     * Make local changes
     * @throws JSONException
     * @param idToFields
     * @param soupName
     */
    protected Map<String, Map<String, Object>> makeLocalChanges(Map<String, Map<String, Object>> idToFields, String soupName) throws JSONException {
        String[] allIds = idToFields.keySet().toArray(new String[0]);
        Arrays.sort(allIds);
        String[] idsToUpdate = new String[] {allIds[0], allIds[1], allIds[2]};
        return makeLocalChanges(idToFields, soupName, idsToUpdate);
    }

    /**
     * Make local changes
     * @param idToFields
     * @param soupName
     * @param idsToUpdate
     * @return
     * @throws JSONException
     */
    protected Map<String, Map<String, Object>> makeLocalChanges(Map<String, Map<String, Object>> idToFields, String soupName, String[] idsToUpdate) throws JSONException {
        Map<String, Map<String, Object>> idToFieldsUpdated = prepareSomeChanges(idToFields, idsToUpdate, LOCALLY_UPDATED);
        updateRecordsLocally(idToFieldsUpdated, soupName);
        return idToFieldsUpdated;
    }

    /**
     * Check records state in db
     * @param ids
     * @param expectLocallyCreated true if records are expected to be marked as locally created
     * @param expectLocallyUpdated true if records are expected to be marked as locally updated
     * @param expectLocallyDeleted true if records are expected to be marked as locally deleted
     * @param soupName
     * @throws JSONException
     */
    protected void checkDbStateFlags(Collection<String> ids, boolean expectLocallyCreated, boolean expectLocallyUpdated, boolean expectLocallyDeleted, String soupName) throws JSONException {
        boolean expectDirty = expectLocallyCreated || expectLocallyUpdated || expectLocallyDeleted;
        QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec(String.format("SELECT {%s:_soup} FROM {%s} WHERE {%s:Id} IN %s", soupName, soupName, soupName, makeInClause(ids)), ids.size());
        JSONArray accountsFromDb = smartStore.query(smartStoreQuery, 0);
        for (int i=0; i<accountsFromDb.length(); i++) {
            JSONArray row = accountsFromDb.getJSONArray(i);
            JSONObject soupElt = row.getJSONObject(0);
            String id = soupElt.getString(Constants.ID);
            Assert.assertEquals("Wrong local flag", expectDirty, soupElt.getBoolean(SyncTarget.LOCAL));
            Assert.assertEquals("Wrong local flag", expectLocallyCreated, soupElt.getBoolean(SyncTarget.LOCALLY_CREATED));
            Assert.assertEquals("Id was not updated", expectLocallyCreated, id.startsWith(LOCAL_ID_PREFIX));
            Assert.assertEquals("Wrong local flag", expectLocallyUpdated, soupElt.getBoolean(SyncTarget.LOCALLY_UPDATED));
            Assert.assertEquals("Wrong local flag", expectLocallyDeleted, soupElt.getBoolean(SyncTarget.LOCALLY_DELETED));
            // Last error field should be empty for a clean record
            if (!expectDirty) {
                Assert.assertTrue("Last error should be empty", TextUtils.isEmpty(JSONObjectHelper.optString(soupElt, SyncTarget.LAST_ERROR)));
            }
        }
    }

    /**
     * Check records syncId field in db
     * @param ids
     * @param syncId value expected in __sync_id__ field
     * @param soupName
     * @throws JSONException
     */
    protected void checkDbSyncIdField(String[] ids, long syncId, String soupName) throws JSONException {
        QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec(String.format("SELECT {%s:_soup} FROM {%s} WHERE {%s:Id} IN %s", soupName, soupName, soupName, makeInClause(ids)), ids.length);
        JSONArray accountsFromDb = smartStore.query(smartStoreQuery, 0);
        for (int i=0; i<accountsFromDb.length(); i++) {
            JSONArray row = accountsFromDb.getJSONArray(i);
            JSONObject soupElt = row.getJSONObject(0);
            Assert.assertEquals("Wrong sync id", syncId, soupElt.getInt(SyncTarget.SYNC_ID));
        }
    }

    /**
     * Check records last error field in db
     * @param ids
     * @param lastErrorSubString value expected within __last_error__ field
     * @param soupName
     * @throws JSONException
     */
    protected void checkDbLastErrorField(String[] ids, String lastErrorSubString, String soupName) throws JSONException {
        QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec(String.format("SELECT {%s:_soup} FROM {%s} WHERE {%s:Id} IN %s", soupName, soupName, soupName, makeInClause(ids)), ids.length);
        JSONArray accountsFromDb = smartStore.query(smartStoreQuery, 0);
        for (int i=0; i<accountsFromDb.length(); i++) {
            JSONArray row = accountsFromDb.getJSONArray(i);
            JSONObject soupElt = row.getJSONObject(0);
            Assert.assertTrue("Wrong last error", soupElt.getString(SyncTarget.LAST_ERROR).contains(lastErrorSubString));
        }
    }


    /**
     * Check records on server
     * @param idToFields
     * @param sObjectType
     * @throws IOException
     * @throws JSONException
     */
    protected void checkServer(Map<String, Map<String, Object>> idToFields, String sObjectType) throws IOException, JSONException {
        String[] fieldNames = idToFields.get(idToFields.keySet().toArray(new String[0])[0]).keySet().toArray(new String[0]);
        String soql = String.format("SELECT %s, %s FROM %s WHERE %s IN %s", Constants.ID, TextUtils.join(",", fieldNames), sObjectType, Constants.ID, makeInClause(idToFields.keySet()));
        RestRequest request = RestRequest.getRequestForQuery(ApiVersionStrings.getVersionNumber(targetContext), soql);
        RestResponse response = restClient.sendSync(request);
        JSONArray records = response.asJSONObject().getJSONArray(RECORDS);
        Assert.assertEquals("Wrong number of records", idToFields.size(), records.length());
        for (int i=0; i<records.length(); i++) {
            JSONObject row = records.getJSONObject(i);
            Map<String, Object> expectedFields = idToFields.get(row.get(Constants.ID));
            for (String fieldName : fieldNames) {
                Assert.assertEquals("Wrong value for field: " + fieldName, expectedFields.get(fieldName), JSONObjectHelper.opt(row, fieldName));
            }
        }
    }

    /**
     * Check that records were deleted from server
     * @param ids
     * @param sObjectType
     * @throws IOException
     */
    protected void checkServerDeleted(String[] ids, String sObjectType) throws IOException, JSONException {
        String soql = String.format("SELECT %s FROM %s WHERE %s IN %s", Constants.ID, sObjectType, Constants.ID, makeInClause(ids));
        RestRequest request = RestRequest.getRequestForQuery(ApiVersionStrings.getVersionNumber(targetContext), soql);
        RestResponse response = restClient.sendSync(request);
        JSONArray records = response.asJSONObject().getJSONArray(RECORDS);
        Assert.assertEquals("No accounts should have been returned from server", 0, records.length());
    }

    /**
     * Sync up helper
     * @oaram target
     * @param numberChanges
     * @param mergeMode
     * @throws JSONException
     */
    protected void trySyncUp(SyncUpTarget target, int numberChanges, SyncState.MergeMode mergeMode) throws JSONException {
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
    protected void trySyncUp(SyncUpTarget target, int numberChanges, SyncState.MergeMode mergeMode, boolean expectSyncFailure) throws JSONException {
        SyncOptions options = SyncOptions.optionsForSyncUp(Arrays.asList(new String[] { Constants.NAME, Constants.DESCRIPTION }), mergeMode);
        trySyncUp(target, numberChanges, options, expectSyncFailure);
    }

    /**
     * Sync up helper
     * @param target
     * @param numberChanges
     * @param options
     * @param expectSyncFailure - if true, we expect the sync to end up in the FAILED state
     * @throws JSONException
     */
    protected void trySyncUp(SyncUpTarget target, int numberChanges, SyncOptions options, boolean expectSyncFailure) throws JSONException {
        // Create sync
		SyncState sync = SyncState.createSyncUp(smartStore, target, options, ACCOUNTS_SOUP, null);
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
        } else {
            for (int i = 1; i < numberChanges; i++) {
                checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncUp, syncId, target, options, SyncState.Status.RUNNING, i * 100 / numberChanges, numberChanges);
            }
            checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncUp, syncId, target, options, SyncState.Status.DONE, 100, numberChanges);
        }
	}

    /**
     * Return map of id to fields given records names
     * @param soupName
     * @param fieldNames
     * @param nameField
     * @param names
     * @throws JSONException
     */
    protected Map<String, Map<String, Object>> getIdToFieldsByName(String soupName, String[] fieldNames, String nameField, String[] names) throws JSONException {
        QuerySpec smartStoreQuery = QuerySpec.buildSmartQuerySpec(String.format("SELECT {%s:_soup} FROM {%s} WHERE {%s:%s} IN %s", soupName, soupName, soupName, nameField, makeInClause(names)), names.length);
        JSONArray recordsFromDb = smartStore.query(smartStoreQuery, 0);
        Map<String, Map<String, Object>> idToFields = new HashMap<>();
        for (int i=0; i<recordsFromDb.length(); i++) {
            JSONArray row = recordsFromDb.getJSONArray(i);
            JSONObject soupElt = row.getJSONObject(0);
            String id = soupElt.getString(Constants.ID);
            Map<String, Object> fields = new HashMap<>();
            for (String fieldName : fieldNames) {
                fields.put(fieldName, soupElt.get(fieldName));
            }
            idToFields.put(id, fields);
        }
        return idToFields;
    }

    /**
	 * Delete records locally
	 *
     * @param soupName
     * @param ids
     * @throws JSONException
	 */
    protected void deleteRecordsLocally(String soupName, String... ids) throws JSONException {
		for (String id : ids) {
			JSONObject record = smartStore.retrieve(soupName, smartStore.lookupSoupEntryId(soupName, Constants.ID, id)).getJSONObject(0);
			record.put(SyncTarget.LOCAL, true);
			record.put(SyncTarget.LOCALLY_CREATED, false);
			record.put(SyncTarget.LOCALLY_DELETED, true);
			record.put(SyncTarget.LOCALLY_UPDATED, false);
			smartStore.upsert(soupName, record);
		}
	}

    /**
     * Helper method to update a single record on the server
     *
     * @param objectType
     * @param id
     * @param fields
     * @return
     */
    protected Map<String, Map<String, Object>> updateRecordOnServer(String objectType, String id, Map<String, Object> fields) throws Exception {
        Map<String, Map<String, Object>> idToFieldsRemotelyUpdated = new HashMap<>();
        Map<String, Object> updatedFields = updatedFields(fields, REMOTELY_UPDATED);
        idToFieldsRemotelyUpdated.put(id, updatedFields);
        updateRecordsOnServer(idToFieldsRemotelyUpdated, objectType);
        return idToFieldsRemotelyUpdated;
    }

    /**
     * Helper method to update a single record locally
     *
     * @param soupName
     * @param id
     * @param fields
     * @return
     * @throws JSONException
     */
    protected Map<String, Map<String, Object>> updateRecordLocally(String soupName, String id, Map<String, Object> fields) throws JSONException {
        return updateRecordLocally(soupName, id, fields, LOCALLY_UPDATED);
    }

    /**
     * Helper method to update a single record locally by appending the given prefix to the fields
     *
     * @param soupName
     * @param id
     * @param fields
     * @param suffix
     * @return
     * @throws JSONException
     */
    protected Map<String, Map<String, Object>> updateRecordLocally(String soupName, String id, Map<String, Object> fields, String suffix) throws JSONException {
        Map<String, Map<String, Object>> idToFieldsLocallyUpdated = new HashMap<>();
        Map<String, Object> updatedFields = updatedFields(fields, suffix);
        idToFieldsLocallyUpdated.put(id, updatedFields);
        updateRecordsLocally(idToFieldsLocallyUpdated, soupName);
        return idToFieldsLocallyUpdated;
    }
}
