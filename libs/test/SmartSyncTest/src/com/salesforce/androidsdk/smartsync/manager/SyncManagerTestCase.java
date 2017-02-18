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
import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.androidsdk.smartsync.util.SyncOptions;
import com.salesforce.androidsdk.smartsync.util.SyncState;
import com.salesforce.androidsdk.smartsync.util.SyncUpdateCallbackQueue;
import com.salesforce.androidsdk.util.test.JSONTestHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collection;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Abstract super class for all SyncManager test classes
 */
abstract public class SyncManagerTestCase extends ManagerTestCase {

    protected static final String TYPE = "type";
    protected static final String RECORDS = "records";
    protected static final String LID = "id"; // lower case id in create response

    // Local
    protected static final String LOCAL_ID_PREFIX = "local_";
    protected static final String ACCOUNTS_SOUP = "accounts";
    protected static final int TOTAL_SIZE_UNKNOWN = -2;

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
                new IndexSpec(SyncTarget.LOCAL, SmartStore.Type.string)
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
     * @return local id of the form local_number where number is different every time and increasing
     */
    @SuppressWarnings("resource")
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
        assertEquals("No records should have been returned from smartstore", 0, records.length());
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
        assertEquals("All records should have been returned from smartstore", ids.length, records.length());
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
     * Sync down helper.
     *
     * @param mergeMode     Merge mode.
     * @param target        Sync down target.
     * @param soupName      Soup name.
     * @param totalSize     Expected total size
     * @param numberFetches Expected number of fetches
     * @return Sync ID.
     */
    protected long trySyncDown(SyncState.MergeMode mergeMode, SyncDownTarget target, String soupName, int totalSize, int numberFetches) throws JSONException {
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
                assertEquals(String.format("Wrong data in db for field %s on record %s", fieldName, recordId),
                        expectedFields.get(fieldName).toString(), recordFromDb.get(fieldName).toString());
            }
        }
    }

    /**
     * Update records on server
     * @param idToFieldsUpdated
     * @param sObjectType
     * @throws Exception
     */
    protected void updateRecordsOnServer(Map<String, Map<String, Object>> idToFieldsUpdated, String sObjectType) throws Exception {
        for (String id : idToFieldsUpdated.keySet()) {
            RestRequest request = RestRequest.getRequestForUpdate(ApiVersionStrings.getVersionNumber(targetContext), sObjectType, id, idToFieldsUpdated.get(id));
            // Response
            RestResponse response = restClient.sendSync(request);
            assertTrue("Updated failed", response.isSuccess());
        }
    }

    /**
     * Make remote changes
     * @throws JSONException
     * @param idToFields
     * @param sObjectType
     */
    protected Map<String, Map<String, Object>> makeRemoteChanges(Map<String, Map<String, Object>> idToFields, String sObjectType) throws Exception {
        final int[] indicesRecordsToUpdate = {0, 2};
        Map<String, Map<String, Object>> idToFieldsUpdated = prepareSomeChanges(idToFields, indicesRecordsToUpdate);
        Thread.sleep(1000); // time stamp precision is in seconds
        updateRecordsOnServer(idToFieldsUpdated, sObjectType);
        return idToFieldsUpdated;
    }

    protected Map<String, Map<String, Object>> prepareSomeChanges(Map<String, Map<String, Object>> idToFields, int[] indicesRecordsToUpdate) {
        Map<String, Map<String, Object>> idToFieldsUpdated = new HashMap<>();
        String[] allIds = idToFields.keySet().toArray(new String[0]);
        Arrays.sort(allIds); // to make the status updates sequence deterministic

        for (int i = 0; i < indicesRecordsToUpdate.length; i++) {
            String id = allIds[indicesRecordsToUpdate[i]];
            idToFieldsUpdated.put(id, updatedFields(idToFields.get(id)));
        }
        return idToFieldsUpdated;
    }

    protected Map<String, Object> updatedFields(Map<String, Object> fields) {
        Set<String> fieldNamesUpdatable = new HashSet<>(Arrays.asList(new String[] {Constants.NAME, Constants.DESCRIPTION, Constants.LAST_NAME}));

        Map<String, Object> updatedFields = new HashMap<>();
        for (String fieldName : fields.keySet()) {
            if (fieldNamesUpdatable.contains(fieldName)) {
                updatedFields.put(fieldName, fields.get(fieldName) + "_updated");
            }
        }
        return updatedFields;
    }
}
