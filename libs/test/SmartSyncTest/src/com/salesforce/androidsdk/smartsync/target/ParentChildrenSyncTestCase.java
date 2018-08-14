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

package com.salesforce.androidsdk.smartsync.target;

import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartSqlHelper;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartsync.manager.SyncManagerTestCase;
import com.salesforce.androidsdk.smartsync.target.ParentChildrenSyncTargetHelper.RelationshipType;
import com.salesforce.androidsdk.smartsync.util.ChildrenInfo;
import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.androidsdk.smartsync.util.ParentInfo;
import com.salesforce.androidsdk.smartsync.util.SyncState;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

/**
 * Test class for ParentChildrenSyncDownTarget and ParentChildrenSyncUpTarget.
 */
public class ParentChildrenSyncTestCase extends SyncManagerTestCase {

    protected static final String CONTACTS_SOUP = "contacts";
    protected static final String ACCOUNT_ID = "AccountId";

    protected Map<String, Map<String, Object>> accountIdToFields;
    protected Map<String, Map<String, Map<String, Object>>> accountIdContactIdToFields;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        createAccountsSoup();
        createContactsSoup();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        dropContactsSoup();
        dropAccountsSoup();

        // accountIdToFields and accountIdContactIdToFields are not used by all tests
        if (accountIdToFields != null) {
            deleteRecordsOnServer(accountIdToFields.keySet(), Constants.ACCOUNT);
        }
        if (accountIdContactIdToFields != null) {
            for (String accountId : accountIdContactIdToFields.keySet()) {
                Map<String, Map<String, Object>> contactIdToFields = accountIdContactIdToFields.get(accountId);
                deleteRecordsOnServer(contactIdToFields.keySet(), Constants.CONTACT);
            }
        }
    }

    /**
     * Helper for various sync up test
     *
     * Create accounts and contacts on server
     * Run sync down
     * Then locally and/or remotely delete and/or update an account or contact
     * Run sync up with leave-if-changed (if requested)
     * Check db and server
     * Run sync up with overwrite
     * Check db and server
     *
     * @param numberAccounts
     * @param numberContactsPerAccount
     * @param localChangeForAccount
     * @param remoteChangeForAccount
     * @param localChangeForContact
     * @param remoteChangeForContact
     */
    protected void trySyncUpsWithVariousChanges(int numberAccounts,
                                                int numberContactsPerAccount,
                                                Change localChangeForAccount,
                                                Change remoteChangeForAccount,
                                                Change localChangeForContact,
                                                Change remoteChangeForContact) throws Exception {
        // Creating test accounts and contacts on server
        createAccountsAndContactsOnServer(numberAccounts, numberContactsPerAccount);

        // Sync down
        ParentChildrenSyncDownTarget syncDownTarget = getAccountContactsSyncDownTarget(
                String.format("%s IN %s", Constants.ID, makeInClause(accountIdToFields.keySet())));
        trySyncDown(SyncState.MergeMode.OVERWRITE, syncDownTarget, ACCOUNTS_SOUP, numberAccounts, 1);

        // Pick an account and contact
        String[] accountIds = accountIdToFields.keySet().toArray(new String[0]);
        String accountId = accountIds[0];
        Map<String, Object> accountFields = accountIdToFields.get(accountId);
        String[] contactIdsOfAccount = numberContactsPerAccount > 0 ? accountIdContactIdToFields.get(accountId).keySet().toArray(new String[0]) : null;
        String contactId = contactIdsOfAccount != null ? contactIdsOfAccount[0] : null;
        String otherContactId = contactIdsOfAccount != null ? contactIdsOfAccount[1] : null;
        Map<String, Object> contactFields = contactId != null ? accountIdContactIdToFields.get(accountId).get(contactId) : null;

        // Build sync up target
        ParentChildrenSyncUpTarget syncUpTarget = getAccountContactsSyncUpTarget();

        // Apply localChangeForAccount
        Map<String, Map<String, Object>> localUpdatesAccount = null;
        switch (localChangeForAccount) {
            case NONE:
                break;
            case UPDATE:
                localUpdatesAccount = updateRecordLocally(ACCOUNTS_SOUP, accountId, accountFields);
                break;
            case DELETE:
                deleteRecordsLocally(ACCOUNTS_SOUP, accountId);
                break;
        }

        // Apply localChangeForContact
        Map<String, Map<String, Object>> localUpdatesContact = null;
        if (contactId != null) {
            switch (localChangeForContact) {
                case NONE:
                    break;
                case UPDATE:
                    localUpdatesContact = updateRecordLocally(CONTACTS_SOUP, contactId, contactFields);
                    break;
                case DELETE:
                    deleteRecordsLocally(CONTACTS_SOUP, contactId);
                    break;
            }
        }

        // Sleep before doing remote changes
        if (remoteChangeForAccount != Change.NONE || remoteChangeForContact != Change.NONE) {
            Thread.sleep(1000); // time stamp precision is in seconds
        }

        // Apply remoteChangeForAccount
        Map<String, Map<String, Object>> remoteUpdatesAccount = null;
        switch (remoteChangeForAccount) {
            case NONE:
                break;
            case UPDATE:
                remoteUpdatesAccount = updateRecordOnServer(Constants.ACCOUNT, accountId, accountFields);
                break;
            case DELETE:
                deleteRecordsOnServer(Collections.singleton(accountId), Constants.ACCOUNT);
                break;
        }

        Map<String, Map<String, Object>> remoteUpdatesContact = null;
        if (contactId != null) {
            switch (remoteChangeForContact) {
                case NONE:
                    break;
                case UPDATE:
                    remoteUpdatesContact = updateRecordOnServer(Constants.CONTACT, contactId, contactFields);
                    break;
                case DELETE:
                    deleteRecordsOnServer(Collections.singleton(contactId), Constants.CONTACT);
                    break;
            }
        }

        // Sync up

        // In some cases, leave-if-changed will succeed
        if ((remoteChangeForAccount == Change.NONE || (remoteChangeForAccount == Change.DELETE && localChangeForAccount == Change.DELETE))          // no remote parent change or it's a delete and we did a local delete also
                && (remoteChangeForContact == Change.NONE || (remoteChangeForContact == Change.DELETE && localChangeForContact == Change.DELETE)))  // no remote child change  or it's a delete and we did a local delete also
        {
            // Sync up with leave-if-changed
            trySyncUp(syncUpTarget, 1, SyncState.MergeMode.LEAVE_IF_CHANGED);

            // Check db and server - local changes should have made it over
            checkDbAndServerAfterCompletedSyncUp(accountId, contactId, otherContactId, remoteChangeForAccount, localChangeForContact, remoteChangeForContact, localUpdatesAccount, localUpdatesContact, localChangeForAccount);

            // Sync up with overwrite - there should be no dirty records found
            trySyncUp(syncUpTarget, 0, SyncState.MergeMode.OVERWRITE);
        }
        // In all other cases, leave-if-changed will fail
        else {

            // Sync up with leave-if-changed
            trySyncUp(syncUpTarget, 1, SyncState.MergeMode.LEAVE_IF_CHANGED);

            // Check db and server - nothing should have changed
            checkDbAndServerAfterBlockedSyncUp(accountId, contactId, localChangeForAccount, remoteChangeForAccount, localChangeForContact, remoteChangeForContact, localUpdatesAccount, remoteUpdatesAccount, localUpdatesContact, remoteUpdatesContact);

            // Sync up with overwrite
            trySyncUp(syncUpTarget, 1, SyncState.MergeMode.OVERWRITE);

            // Check db and server - local changes should have made it over
            checkDbAndServerAfterCompletedSyncUp(accountId, contactId, otherContactId, remoteChangeForAccount, localChangeForContact, remoteChangeForContact, localUpdatesAccount, localUpdatesContact, localChangeForAccount);
        }
    }

    private void checkDbAndServerAfterBlockedSyncUp(String accountId, String contactId, Change localChangeForAccount, Change remoteChangeForAccount, Change localChangeForContact, Change remoteChangeForContact, Map<String, Map<String, Object>> localUpdatesAccount, Map<String, Map<String, Object>> remoteUpdatesAccount, Map<String, Map<String, Object>> localUpdatesContact, Map<String, Map<String, Object>> remoteUpdatesContact) throws JSONException, IOException {

        //
        // Check parent
        //

        // Check db
        if (localChangeForAccount == Change.UPDATE) {
            checkDb(localUpdatesAccount, ACCOUNTS_SOUP);
        }

        checkDbStateFlags(Arrays.asList(accountId), false, localChangeForAccount == Change.UPDATE, localChangeForAccount == Change.DELETE, ACCOUNTS_SOUP);

        // Check server
        switch (remoteChangeForAccount) {
            case NONE:
                break;
            case UPDATE:
                checkServer(remoteUpdatesAccount, Constants.ACCOUNT);
                break;
            case DELETE:
                checkServerDeleted(new String[]{accountId}, Constants.ACCOUNT);
                break;
        }

        //
        // Check children if any
        //

        if (contactId != null) {

            Set<String> contactIdsOfAccount = accountIdContactIdToFields.get(accountId).keySet();
            Set<String> otherContactIdsOfAccount = new HashSet<>(contactIdsOfAccount);
            otherContactIdsOfAccount.remove(contactId);

            // Check db

            if (localChangeForContact == Change.UPDATE) {
                checkDb(localUpdatesContact, CONTACTS_SOUP);
            }

            checkDbStateFlags(Arrays.asList(contactId), false, localChangeForContact == Change.UPDATE, localChangeForContact == Change.DELETE, CONTACTS_SOUP);
            checkDbRelationships(contactIdsOfAccount, accountId, CONTACTS_SOUP, Constants.ID, ACCOUNT_ID);

            // Check server

            if (remoteChangeForAccount == Change.DELETE) {
                // Master delete => deletes children
                checkServerDeleted(contactIdsOfAccount.toArray(new String[0]), Constants.CONTACT);
            }
            else {
                switch (remoteChangeForContact) {
                    case NONE:
                        break;
                    case UPDATE:
                        checkServer(remoteUpdatesContact, Constants.CONTACT);
                        break;
                    case DELETE:
                        checkServerDeleted(new String[]{contactId}, Constants.CONTACT);
                        break;
                }
            }
        }
    }

    private void checkDbAndServerAfterCompletedSyncUp(String accountId, String contactId, String otherContactId, Change remoteChangeForAccount, Change localChangeForContact, Change remoteChangeForContact, Map<String, Map<String, Object>> localUpdatesAccount, Map<String, Map<String, Object>> localUpdatesContact, Change localChangeForAccount) throws Exception {
        String newAccountId = null;
        String newContactId = null;
        String newOtherContactId = null;

        try {

            //
            // Check parent
            //

            switch (localChangeForAccount) {
                case NONE:
                    checkRecordAfterSync(accountId, accountIdToFields.get(accountId), ACCOUNTS_SOUP, Constants.ACCOUNT, null, null);
                    break;
                case UPDATE:
                    if (remoteChangeForAccount == Change.DELETE) {
                        newAccountId = checkRecordRecreated(accountId, localUpdatesAccount.get(accountId), Constants.NAME, ACCOUNTS_SOUP, Constants.ACCOUNT, null, null);
                    }
                    else {
                        checkRecordAfterSync(accountId, localUpdatesAccount.get(accountId), ACCOUNTS_SOUP, Constants.ACCOUNT, null, null);
                    }
                    break;
                case DELETE:
                    checkDeletedRecordAfterSync(accountId, ACCOUNTS_SOUP, Constants.ACCOUNT);
                    break;
            }

            //
            // Check children if any
            //

            if (contactId != null) {

                if (localChangeForAccount == Change.DELETE) {
                    // Master delete => deletes children
                    String[] contactIdsOfAcccount = accountIdContactIdToFields.get(accountId).keySet().toArray(new String[0]);
                    checkDbDeleted(CONTACTS_SOUP, contactIdsOfAcccount, Constants.ID);
                    checkServerDeleted(contactIdsOfAcccount, Constants.CONTACT);
                }
                else {
                    switch (localChangeForContact) {
                        case NONE:
                            if (remoteChangeForAccount == Change.DELETE || remoteChangeForContact == Change.DELETE) {
                                newContactId = checkRecordRecreated(contactId, accountIdContactIdToFields.get(accountId).get(contactId), Constants.LAST_NAME, CONTACTS_SOUP, Constants.CONTACT, newAccountId == null ? accountId : newAccountId, ACCOUNT_ID);
                            } else {
                                checkRecordAfterSync(contactId, accountIdContactIdToFields.get(accountId).get(contactId), CONTACTS_SOUP, Constants.CONTACT, accountId, ACCOUNT_ID);
                            }
                            break;
                        case UPDATE:
                            if (remoteChangeForAccount == Change.DELETE || remoteChangeForContact == Change.DELETE) {
                                newContactId = checkRecordRecreated(contactId, localUpdatesContact.get(contactId), Constants.LAST_NAME, CONTACTS_SOUP, Constants.CONTACT, newAccountId == null ? accountId : newAccountId, ACCOUNT_ID);
                            } else {
                                checkRecordAfterSync(contactId, localUpdatesContact.get(contactId), CONTACTS_SOUP, Constants.CONTACT, accountId, ACCOUNT_ID);
                            }
                            break;
                        case DELETE:
                            checkDeletedRecordAfterSync(contactId, CONTACTS_SOUP, Constants.CONTACT);
                            break;
                    }

                    if (remoteChangeForAccount == Change.DELETE) {
                        // Check that other contact was recreated also
                        newOtherContactId = checkRecordRecreated(otherContactId, accountIdContactIdToFields.get(accountId).get(otherContactId), Constants.LAST_NAME, CONTACTS_SOUP, Constants.CONTACT, newAccountId, ACCOUNT_ID);
                    }
                }
            }
        }
        finally {
            // Cleaning "recreated" records
            if (newAccountId != null) deleteRecordsOnServer(Collections.singleton(newAccountId), Constants.ACCOUNT);
            if (newContactId != null) deleteRecordsOnServer(Collections.singleton(newContactId), Constants.CONTACT);
            if (newOtherContactId != null) deleteRecordsOnServer(Collections.singleton(newOtherContactId), Constants.CONTACT);
        }
    }

    /**
     * Check record that were "recreated"
     * A record is "recreated" when synced up locally updated and remotely deleted
     *
     * Make sure old record is gone
     * Make sure sync flags are false
     * Make sure fields are as expected on db and server (including parent id field if provided)
     *
     * @param recordId
     * @param fields
     * @param nameField
     * @param soupName
     * @param objectType
     * @param parentId
     * @param parentIdField
     *
     * @return new record id
     * @throws JSONException
     * @throws IOException
     */
    protected String checkRecordRecreated(String recordId, Map<String, Object> fields, String nameField, String soupName, String objectType, String parentId, String parentIdField) throws JSONException, IOException {
        String updatedName = (String) fields.get(nameField);
        Map<String, Map<String, Object>> newIdToFields = getIdToFieldsByName(soupName, new String[]{nameField}, nameField, new String[]{updatedName});
        String newRecordId = newIdToFields.keySet().toArray(new String[0])[0];

        // Make sure new id is really new
        Assert.assertFalse("Record should have new id", newRecordId.equals(recordId));

        // Make sure old id is gone from db and server
        checkDbDeleted(soupName, new String[]{recordId}, Constants.ID);
        checkServerDeleted(new String[]{recordId}, objectType);

        // Make sure record with new id is correct in db and server
        checkRecordAfterSync(newRecordId, newIdToFields.get(newRecordId), soupName, objectType, parentId, parentIdField);

        return newRecordId;
    }

    /**
     * Check record after a sync
     *
     * Make sure sync flags are false
     * Make sure fields are as expected on db and server
     * Make sure parent id field has correct value on db and server (if provided)
     *
     * @param recordId
     * @param fields
     * @param soupName
     * @param objectType
     * @param parentId
     * @param parentIdField @return
     * @throws JSONException
     * @throws IOException
     */
    private void checkRecordAfterSync(String recordId, Map<String, Object> fields, String soupName, String objectType, String parentId, String parentIdField) throws JSONException, IOException {

        // Check record is no longer marked as dirty
        checkDbStateFlags(Arrays.asList(recordId), false, false, false, soupName);

        // Prepare fields map to check (add parentId if provided)
        Map<String, Object> fieldsCopy = new HashMap<>(fields);
        if (parentId != null) {
            fieldsCopy.put(parentIdField, parentId);
        }
        Map<String, Map<String, Object>> idToFields = new HashMap<>();
        idToFields.put(recordId, fieldsCopy);

        // Check db
        checkDb(idToFields, soupName);

        // Check server
        checkServer(idToFields, objectType);
    }

    /**
     * Check that deleted record is truly gone from db and server
     *
     * @param recordId
     * @param soupName
     * @param objectType
     * @return
     * @throws JSONException
     * @throws IOException
     */
    private void checkDeletedRecordAfterSync(String recordId, String soupName, String objectType) throws JSONException, IOException {
        checkDbDeleted(soupName, new String[]{recordId}, Constants.ID);
        checkServerDeleted(new String[]{recordId}, objectType);
    }

    /**
     * Useful enum for trySyncUpsWithVariousChanges
     */
    protected enum Change {
        NONE,
        UPDATE,
        DELETE
    }

    /**
     * Helper method for testSyncUpWithLocallyCreatedRecords*
     *
     * @param syncUpMergeMode
     * @throws Exception
     */
    protected void trySyncUpWithLocallyCreatedRecords(SyncState.MergeMode syncUpMergeMode) throws Exception {
        final int numberContactsPerAccount = 3;

        // Create a few entries locally
        String[] accountNames = new String[]{
                createRecordName(Constants.ACCOUNT),
                createRecordName(Constants.ACCOUNT),
                createRecordName(Constants.ACCOUNT),
                createRecordName(Constants.ACCOUNT),
                createRecordName(Constants.ACCOUNT),
                createRecordName(Constants.ACCOUNT)
        };
        Map<JSONObject, JSONObject[]> mapAccountToContacts = createAccountsAndContactsLocally(accountNames, numberContactsPerAccount);
        String[] contactNames = new String[numberContactsPerAccount * accountNames.length];
        int i = 0;
        for (JSONObject[] contacts : mapAccountToContacts.values()) {
            for (JSONObject contact : contacts) {
                contactNames[i] = contact.getString(Constants.LAST_NAME);
                i++;
            }
        }

        // Sync up
        ParentChildrenSyncUpTarget target = getAccountContactsSyncUpTarget();
        trySyncUp(target, accountNames.length, syncUpMergeMode);

        // Check that db doesn't show account entries as locally created anymore and that they use sfdc id
        Map<String, Map<String, Object>> accountIdToFieldsCreated = getIdToFieldsByName(ACCOUNTS_SOUP, new String[]{Constants.NAME, Constants.DESCRIPTION}, Constants.NAME, accountNames);
        checkDbStateFlags(accountIdToFieldsCreated.keySet(), false, false, false, ACCOUNTS_SOUP);

        // Check accounts on server
        checkServer(accountIdToFieldsCreated, Constants.ACCOUNT);

        // Check that db doesn't show contact entries as locally created anymore and that they use sfc id
        Map<String, Map<String, Object>> contactIdToFieldsCreated = getIdToFieldsByName(CONTACTS_SOUP, new String[]{Constants.LAST_NAME, ACCOUNT_ID}, Constants.LAST_NAME, contactNames);
        checkDbStateFlags(contactIdToFieldsCreated.keySet(), false, false, false, CONTACTS_SOUP);

        // Check contacts on server
        checkServer(contactIdToFieldsCreated, Constants.CONTACT);

        // Cleanup
        deleteRecordsOnServer(accountIdToFieldsCreated.keySet(), Constants.ACCOUNT);
        deleteRecordsOnServer(contactIdToFieldsCreated.keySet(), Constants.CONTACT);
    }

    protected void tryGetDirtyRecordIds(JSONObject[] expectedRecords) throws JSONException {
        ParentChildrenSyncDownTarget target = getAccountContactsSyncDownTarget();
        SortedSet<String> dirtyRecordIds = target.getDirtyRecordIds(syncManager, ACCOUNTS_SOUP, Constants.ID);
        Assert.assertEquals("Wrong number of dirty records", expectedRecords.length, dirtyRecordIds.size());
        for (JSONObject expectedRecord : expectedRecords) {
            Assert.assertTrue(dirtyRecordIds.contains(expectedRecord.getString(Constants.ID)));
        }
    }

    protected void tryGetNonDirtyRecordIds(JSONObject[] expectedRecords) throws JSONException {
        ParentChildrenSyncDownTarget target = getAccountContactsSyncDownTarget();
        SortedSet<String> nonDirtyRecordIds = target.getNonDirtyRecordIds(syncManager, ACCOUNTS_SOUP, Constants.ID, "");
        Assert.assertEquals("Wrong number of non-dirty records", expectedRecords.length, nonDirtyRecordIds.size());
        for (JSONObject expectedRecord : expectedRecords) {
            Assert.assertTrue(nonDirtyRecordIds.contains(expectedRecord.getString(Constants.ID)));
        }
    }

    protected void cleanRecords(String soupName, JSONObject[] records) throws JSONException {
        for (JSONObject record : records) {
            cleanRecord(soupName, record);
        }
    }

    protected void cleanRecord(String soupName, JSONObject record) throws JSONException {
        record.put(SyncTarget.LOCAL, false);
        record.put(SyncTarget.LOCALLY_CREATED, false);
        record.put(SyncTarget.LOCALLY_UPDATED, false);
        record.put(SyncTarget.LOCALLY_DELETED, false);
        syncManager.getSmartStore().upsert(soupName, record);
    }

    private void createContactsSoup() {
        final IndexSpec[] contactsIndexSpecs = {
                new IndexSpec(Constants.ID, SmartStore.Type.string),
                new IndexSpec(Constants.LAST_NAME, SmartStore.Type.string),
                new IndexSpec(SyncTarget.LOCAL, SmartStore.Type.string),
                new IndexSpec(SyncTarget.SYNC_ID, SmartStore.Type.integer),
                new IndexSpec(ACCOUNT_ID, SmartStore.Type.string)
        };
        smartStore.registerSoup(CONTACTS_SOUP, contactsIndexSpecs);
    }

    private void dropContactsSoup() {
        smartStore.dropSoup(CONTACTS_SOUP);
    }

    protected ParentChildrenSyncDownTarget getAccountContactsSyncDownTarget() {
        return getAccountContactsSyncDownTarget("");
    }

    protected ParentChildrenSyncDownTarget getAccountContactsSyncDownTarget(String parentSoqlFilter) {
        return getAccountContactsSyncDownTarget(Constants.LAST_MODIFIED_DATE, Constants.LAST_MODIFIED_DATE, parentSoqlFilter);
    }

    protected ParentChildrenSyncDownTarget getAccountContactsSyncDownTarget(String accountModificationDateFieldName, String contactModificationDateFieldName, String parentSoqlFilter) {
        return new ParentChildrenSyncDownTarget(
                new ParentInfo(Constants.ACCOUNT, ACCOUNTS_SOUP, Constants.ID, accountModificationDateFieldName),
                Arrays.asList(Constants.ID, Constants.NAME, Constants.DESCRIPTION),
                parentSoqlFilter,
                new ChildrenInfo(Constants.CONTACT, Constants.CONTACT + "s", CONTACTS_SOUP, ACCOUNT_ID, Constants.ID, contactModificationDateFieldName),
                Arrays.asList(Constants.LAST_NAME, ACCOUNT_ID),
                RelationshipType.MASTER_DETAIL); // account-contacts are master-detail
    }

    protected ParentChildrenSyncUpTarget getAccountContactsSyncUpTarget() {
        return getAccountContactsSyncUpTarget("");
    }

    private ParentChildrenSyncUpTarget getAccountContactsSyncUpTarget(String parentSoqlFilter) {
        return getAccountContactsSyncUpTarget(Constants.LAST_MODIFIED_DATE, Constants.LAST_MODIFIED_DATE);
    }

    private ParentChildrenSyncUpTarget getAccountContactsSyncUpTarget(String accountModificationDateFieldName, String contactModificationDateFieldName) {
        return new ParentChildrenSyncUpTarget(
                new ParentInfo(Constants.ACCOUNT, ACCOUNTS_SOUP, Constants.ID, accountModificationDateFieldName),
                Arrays.asList(Constants.ID, Constants.NAME, Constants.DESCRIPTION),
                Arrays.asList(Constants.NAME, Constants.DESCRIPTION),
                new ChildrenInfo(Constants.CONTACT, Constants.CONTACT + "s", CONTACTS_SOUP, ACCOUNT_ID, Constants.ID, contactModificationDateFieldName),
                Arrays.asList(Constants.LAST_NAME, ACCOUNT_ID),
                Arrays.asList(Constants.LAST_NAME, ACCOUNT_ID),
                RelationshipType.MASTER_DETAIL); // account-contacts are master-detail
    }

    protected Map<JSONObject, JSONObject[]> createAccountsAndContactsLocally(String[] names, int numberOfContactsPerAccount) throws JSONException {
        JSONObject[] accounts = createAccountsLocally(names);
        String[] accountIds = JSONObjectHelper.pluck(accounts, Constants.ID).toArray(new String[0]);
        Map<String, JSONObject[]> accountIdsToContacts = createContactsForAccountsLocally(numberOfContactsPerAccount, accountIds);
        Map<JSONObject, JSONObject[]> accountToContacts = new HashMap<>();
        for (JSONObject account : accounts) {
            accountToContacts.put(account, accountIdsToContacts.get(account.getString(Constants.ID)));
        }
        return accountToContacts;
    }

    protected Map<String, JSONObject[]> createContactsForAccountsLocally(int numberOfContactsPerAccount, String... accountIds) throws JSONException {
        Map<String, JSONObject[]> accountIdToContacts = new HashMap<>();
        JSONObject attributes = new JSONObject();
        attributes.put(TYPE, Constants.CONTACT);
        for (String accountId : accountIds) {
            JSONObject[] contacts = new JSONObject[numberOfContactsPerAccount];
            for (int i = 0; i < numberOfContactsPerAccount; i++) {
                JSONObject contact = new JSONObject();
                contact.put(Constants.ID, createLocalId());
                contact.put(Constants.LAST_NAME, createRecordName(Constants.CONTACT));
                contact.put(Constants.ATTRIBUTES, attributes);
                contact.put(SyncTarget.LOCAL, true);
                contact.put(SyncTarget.LOCALLY_CREATED, true);
                contact.put(SyncTarget.LOCALLY_DELETED, false);
                contact.put(SyncTarget.LOCALLY_UPDATED, false);
                contact.put(ACCOUNT_ID, accountId);
                contacts[i] = smartStore.create(CONTACTS_SOUP, contact);
            }
            accountIdToContacts.put(accountId, contacts);
        }
        return accountIdToContacts;
    }

    protected JSONObject[] queryWithInClause(String soupName, String fieldName, String[] values, String orderBy) throws JSONException {
        final String sql = String.format("SELECT {%s:%s} FROM {%s} WHERE {%s:%s} IN %s %s",
                soupName, SmartSqlHelper.SOUP, soupName, soupName, fieldName,
                makeInClause(values),
                orderBy == null ? "" : String.format(" ORDER BY {%s:%s} ASC", soupName, orderBy)
        );
        QuerySpec querySpec = QuerySpec.buildSmartQuerySpec(sql, Integer.MAX_VALUE);
        JSONArray rows = smartStore.query(querySpec, 0);
        JSONObject[] arr = new JSONObject[rows.length()];
        for (int i = 0; i < rows.length(); i++) {
            arr[i] = rows.getJSONArray(i).getJSONObject(0);
        }
        return arr;
    }

    protected void createAccountsAndContactsOnServer(int numberAccounts, int numberContactsPerAccount) throws Exception {
        accountIdToFields = new HashMap<>();
        accountIdContactIdToFields = new HashMap<>();
        Map<String, Map<String, Object>> refIdToFields = new HashMap<>();
        List<RestRequest.SObjectTree> accountTrees = new ArrayList<>();
        List<Map<String, Object>> listAccountFields = buildFieldsMapForRecords(numberAccounts, Constants.ACCOUNT, null);
        for (int i = 0; i<listAccountFields.size(); i++) {
            List<Map<String, Object>> listContactFields = buildFieldsMapForRecords(numberContactsPerAccount, Constants.CONTACT, null);
            String refIdAccount = "refAccount_" + i;
            Map<String, Object> accountFields = listAccountFields.get(i);
            refIdToFields.put(refIdAccount, accountFields);
            List<RestRequest.SObjectTree> contactTrees = new ArrayList<>();
            for (int j = 0; j<listContactFields.size(); j++) {
                String refIdContact = refIdAccount + ":refContact_" + j;
                Map<String, Object> contactFields = listContactFields.get(j);
                refIdToFields.put(refIdContact, contactFields);
                contactTrees.add(new RestRequest.SObjectTree(Constants.CONTACT, Constants.CONTACTS, refIdContact, contactFields, null));
            }
            accountTrees.add(new RestRequest.SObjectTree(Constants.ACCOUNT, null, refIdAccount, accountFields, contactTrees));
        }
        RestRequest request = RestRequest.getRequestForSObjectTree(apiVersion, Constants.ACCOUNT, accountTrees);

        // Send request
        RestResponse response =  restClient.sendSync(request);

        // Parse response
        Map<String, String> refIdToId = new HashMap<>();
        JSONArray results = response.asJSONObject().getJSONArray("results");
        for (int i = 0; i < results.length(); i++) {
            JSONObject result = results.getJSONObject(i);
            String refId = result.getString(RestRequest.REFERENCE_ID);
            String id = result.getString(Constants.LID);
            refIdToId.put(refId, id);
        }

        // Populate accountIdToFields and accountIdContactIdToFields
        for (String refId : refIdToId.keySet()) {
            Map<String, Object> fields = refIdToFields.get(refId);
            String[] parts = refId.split(":");
            String accountId = refIdToId.get(parts[0]);
            String contactId = parts.length > 1 ? refIdToId.get(refId) : null;
            if (contactId == null) {
                accountIdToFields.put(accountId, fields);
            } else {
                if (!accountIdContactIdToFields.containsKey(accountId))
                    accountIdContactIdToFields.put(accountId, new HashMap<String, Map<String, Object>>());
                accountIdContactIdToFields.get(accountId).put(contactId, fields);
            }
        }
    }
}
