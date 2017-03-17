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
import com.salesforce.androidsdk.smartsync.util.SyncOptions;
import com.salesforce.androidsdk.smartsync.util.SyncState;
import com.salesforce.androidsdk.smartsync.util.SyncUpdateCallbackQueue;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

/**
 * Test class for ParentChildrenSyncDownTarget and ParentChildrenSyncUpTarget
 */
public class ParentChildrenSyncTest extends SyncManagerTestCase {

    private static final String CONTACTS_SOUP = "contacts";
    private static final String ACCOUNT_ID = "AccountId";
    private static final String ACCOUNT_LOCAL_ID = "AccountLocalId";

    protected Map<String, Map<String, Object>> accountIdToFields;
    protected Map<String, Map<String, Map<String, Object>>> accountIdContactIdToFields;


    @Override
    public void setUp() throws Exception {
        super.setUp();
        createAccountsSoup();
        createContactsSoup();
    }

    @Override
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
     * Test getQuery for ParentChildrenSyncDownTarget
     */
    public void testGetQuery() {
        ParentChildrenSyncDownTarget target = new ParentChildrenSyncDownTarget(
                new ParentInfo("Parent", "parentsSoup", "ParentId", "ParentModifiedDate"),
                Arrays.asList("ParentName", "Title"),
                "School = 'MIT'",
                new ChildrenInfo("Child", "Children", "childrenSoup", "parentId", "parentLocalId", "ChildId", "ChildLastModifiedDate"),
                Arrays.asList("ChildName", "School"),
                RelationshipType.LOOKUP);

        assertEquals("select ParentName, Title, ParentId, ParentModifiedDate, (select ChildName, School, ChildId, ChildLastModifiedDate from Children) from Parent where School = 'MIT'", target.getQuery());

        // With default id and modification date fields
        target = new ParentChildrenSyncDownTarget(
                new ParentInfo("Parent", "parentsSoup"),
                Arrays.asList("ParentName", "Title"),
                "School = 'MIT'",
                new ChildrenInfo("Child", "Children", "childrenSoup", "parentId", "parentLocalId"),
                Arrays.asList("ChildName", "School"),
                RelationshipType.LOOKUP);


        assertEquals("select ParentName, Title, Id, LastModifiedDate, (select ChildName, School, Id, LastModifiedDate from Children) from Parent where School = 'MIT'", target.getQuery());
    }


    /**
     * Test query for reSync by calling getQuery with maxTimeStamp for ParentChildrenSyncDownTarget
     */
    public void testGetQueryWithMaxTimeStamp() {
        Date date = new Date();
        String dateStr = Constants.TIMESTAMP_FORMAT.format(date);
        long dateLong = date.getTime();

        ParentChildrenSyncDownTarget target = new ParentChildrenSyncDownTarget(
                new ParentInfo("Parent", "parentsSoup", "ParentId", "ParentModifiedDate"),
                Arrays.asList("ParentName", "Title"),
                "School = 'MIT'",
                new ChildrenInfo("Child", "Children", "childrenSoup", "parentId", "parentLocalId", "ChildId", "ChildLastModifiedDate"),
                Arrays.asList("ChildName", "School"),
                RelationshipType.LOOKUP);

        assertEquals("select ParentName, Title, ParentId, ParentModifiedDate, (select ChildName, School, ChildId, ChildLastModifiedDate from Children where ChildLastModifiedDate > " + dateStr + ") from Parent where ParentModifiedDate > " + dateStr + " and School = 'MIT'", target.getQuery(dateLong));

        // With default id and modification date fields
        target = new ParentChildrenSyncDownTarget(
                new ParentInfo("Parent", "parentsSoup"),
                Arrays.asList("ParentName", "Title"),
                "School = 'MIT'",
                new ChildrenInfo("Child", "Children", "childrenSoup", "parentId", "parentLocalId"),
                Arrays.asList("ChildName", "School"),
                RelationshipType.LOOKUP);


        assertEquals("select ParentName, Title, Id, LastModifiedDate, (select ChildName, School, Id, LastModifiedDate from Children where LastModifiedDate > " + dateStr + ") from Parent where LastModifiedDate > " + dateStr + " and School = 'MIT'", target.getQuery(dateLong));
    }


    /**
     * Test getSoqlForRemoteIds for ParentChildrenSyncDownTarget
     */
    public void testGetSoqlForRemoteIds() {
        ParentChildrenSyncDownTarget target = new ParentChildrenSyncDownTarget(
                new ParentInfo("Parent", "parentsSoup", "ParentId", "ParentModifiedDate"),
                Arrays.asList("ParentName", "Title"),
                "School = 'MIT'",
                new ChildrenInfo("Child", "Children", "childrenSoup", "parentId", "parentLocalId", "ChildId", "ChildLastModifiedDate"),
                Arrays.asList("ChildName", "School"),
                RelationshipType.LOOKUP);

        assertEquals("select ParentId from Parent where School = 'MIT'", target.getSoqlForRemoteIds());

        // With default id and modification date fields
        target = new ParentChildrenSyncDownTarget(
                new ParentInfo("Parent", "parentsSoup"),
                Arrays.asList("ParentName", "Title"),
                "School = 'MIT'",
                new ChildrenInfo("Child", "Children", "childrenSoup", "parentId", "parentLocalId"),
                Arrays.asList("ChildName", "School"),
                RelationshipType.LOOKUP);

        assertEquals("select Id from Parent where School = 'MIT'", target.getSoqlForRemoteIds());
    }

    /**
     * Test getDirtyRecordIdsSql for ParentChildrenSyncDownTarget
     */
    public void testGetDirtyRecordIdsSql() {
        ParentChildrenSyncDownTarget target = new ParentChildrenSyncDownTarget(
                new ParentInfo("Parent", "parentsSoup", "ParentId", "ParentModifiedDate"),
                Arrays.asList("ParentName", "Title"),
                "School = 'MIT'",
                new ChildrenInfo("Child", "Children", "childrenSoup", "parentId", "parentLocalId", "ChildId", "ChildLastModifiedDate"),
                Arrays.asList("ChildName", "School"),
                RelationshipType.LOOKUP);

        assertEquals("SELECT DISTINCT {ParentSoup:IdForQuery} FROM {ParentSoup} WHERE {ParentSoup:__local__} = 'true' OR EXISTS (SELECT {childrenSoup:ChildId} FROM {childrenSoup} WHERE {childrenSoup:parentLocalId} = {ParentSoup:_soupEntryId} AND {childrenSoup:__local__} = 'true')", target.getDirtyRecordIdsSql("ParentSoup", "IdForQuery"));
    }


    /**
     * Test getNonDirtyRecordIdsSql for ParentChildrenSyncDownTarget
     */
    public void testGetNonDirtyRecordIdsSql() {
        ParentChildrenSyncDownTarget target = new ParentChildrenSyncDownTarget(
                new ParentInfo("Parent", "parentsSoup", "ParentId", "ParentModifiedDate"),
                Arrays.asList("ParentName", "Title"),
                "School = 'MIT'",
                new ChildrenInfo("Child", "Children", "childrenSoup", "parentId", "parentLocalId", "ChildId", "ChildLastModifiedDate"),
                Arrays.asList("ChildName", "School"),
                RelationshipType.LOOKUP);

        assertEquals("SELECT DISTINCT {ParentSoup:IdForQuery} FROM {ParentSoup} WHERE {ParentSoup:__local__} = 'false' AND NOT EXISTS (SELECT {childrenSoup:ChildId} FROM {childrenSoup} WHERE {childrenSoup:parentLocalId} = {ParentSoup:_soupEntryId} AND {childrenSoup:__local__} = 'true')", target.getNonDirtyRecordIdsSql("ParentSoup", "IdForQuery"));

    }

    /**
     * Test getDirtyRecordIds and getNonDirtyRecordIds for ParentChildrenSyncDownTarget when parent and/or all and/or some children are dirty
     */
    public void testGetDirtyAndNonDirtyRecordIds() throws JSONException {
        String[] accountNames = new String[]{
                createRecordName(Constants.ACCOUNT),
                createRecordName(Constants.ACCOUNT),
                createRecordName(Constants.ACCOUNT),
                createRecordName(Constants.ACCOUNT),
                createRecordName(Constants.ACCOUNT),
                createRecordName(Constants.ACCOUNT)
        };
        Map<JSONObject, JSONObject[]> mapAccountToContacts = createAccountsAndContactsLocally(accountNames, 3);
        JSONObject[] accounts = mapAccountToContacts.keySet().toArray(new JSONObject[]{});

        // All Accounts should be returned
        tryGetDirtyRecordIds(accounts);

        // No accounts should be returned
        tryGetNonDirtyRecordIds(new JSONObject[]{});


        // Cleaning up:
        // accounts[0]: dirty account and dirty contacts
        // accounts[1]: clean account and dirty contacts
        // accounts[2]: dirty account and clean contacts
        // accounts[3]: clean account and clean contacts
        // accounts[4]: dirty account and some dirty contacts
        // accounts[5]: clean account and some dirty contacts

        cleanRecord(ACCOUNTS_SOUP, accounts[1]);
        cleanRecords(CONTACTS_SOUP, mapAccountToContacts.get(accounts[2]));
        cleanRecord(ACCOUNTS_SOUP, accounts[3]);
        cleanRecords(CONTACTS_SOUP, mapAccountToContacts.get(accounts[3]));
        cleanRecord(CONTACTS_SOUP, mapAccountToContacts.get(accounts[4])[0]);
        cleanRecord(ACCOUNTS_SOUP, accounts[5]);
        cleanRecord(CONTACTS_SOUP, mapAccountToContacts.get(accounts[5])[0]);

        // Only clean account with clean contacts should not be returned
        tryGetDirtyRecordIds(new JSONObject[]{accounts[0], accounts[1], accounts[2], accounts[4], accounts[5]});

        // Only clean account with clean contacts should be returned
        tryGetNonDirtyRecordIds(new JSONObject[]{accounts[3]});
    }

    /**
     * Test saveRecordsToLocalStore
     */
    public void testSaveRecordsToLocalStore() throws JSONException {
        // Putting together a JSONArray of accounts with contacts
        // looking like what we would get back from startFetch/continueFetch
        // - not having local fields
        // - not have _soupEntryId field
        final int numberAccounts = 4;
        final int numberContactsPerAccount = 3;

        JSONObject accountAttributes = new JSONObject();
        accountAttributes.put(TYPE, Constants.ACCOUNT);

        JSONObject contactAttributes = new JSONObject();
        contactAttributes.put(TYPE, Constants.CONTACT);

        JSONObject[] accounts = new JSONObject[numberAccounts];
        Map<JSONObject, JSONObject[]> mapAccountContacts = new HashMap<>();

        for (int i = 0; i < numberAccounts; i++) {
            JSONObject account = new JSONObject();
            account.put(Constants.ID, createLocalId());
            account.put(Constants.ATTRIBUTES, accountAttributes);

            JSONObject[] contacts = new JSONObject[numberContactsPerAccount];
            for (int j = 0; j < numberContactsPerAccount; j++) {
                JSONObject contact = new JSONObject();
                contact.put(Constants.ID, createLocalId());
                contact.put(Constants.ATTRIBUTES, contactAttributes);
                contact.put(ACCOUNT_ID, account.get(Constants.ID));
                contacts[j] = contact;
            }
            mapAccountContacts.put(account, contacts);
            accounts[i] = account;
        }

        JSONArray records = new JSONArray();
        for (JSONObject account : accounts) {
            JSONObject record = new JSONObject(account.toString());
            JSONArray contacts = new JSONArray();
            for (JSONObject contact : mapAccountContacts.get(account)) {
                contacts.put(contact);
            }
            record.put("Contacts", contacts);
            records.put(record);
        }

        // Now calling saveRecordsToLocalStore
        ParentChildrenSyncDownTarget target = getAccountContactsSyncDownTarget();
        target.saveRecordsToLocalStore(syncManager, ACCOUNTS_SOUP, records);

        // Checking accounts and contacts soup
        // Making sure local fields are populated
        // Making sure accountId and accountLocalId fields are populated on contacts

        JSONObject[] accountsFromDb = queryWithInClause(ACCOUNTS_SOUP, Constants.ID, JSONObjectHelper.pluck(accounts, Constants.ID).toArray(new String[0]), null);
        assertEquals("Wrong number of accounts in db", accounts.length, accountsFromDb.length);
        for (int i = 0; i < accountsFromDb.length; i++) {
            JSONObject account = accounts[i];
            JSONObject accountFromDb = accountsFromDb[i];

            assertEquals(account.getString(Constants.ID), accountFromDb.getString(Constants.ID));
            assertEquals(Constants.ACCOUNT, accountFromDb.getJSONObject(Constants.ATTRIBUTES).getString(TYPE));
            assertEquals(false, accountFromDb.getBoolean(SyncTarget.LOCAL));
            assertEquals(false, accountFromDb.getBoolean(SyncTarget.LOCALLY_CREATED));
            assertEquals(false, accountFromDb.getBoolean(SyncTarget.LOCALLY_DELETED));
            assertEquals(false, accountFromDb.getBoolean(SyncTarget.LOCALLY_UPDATED));

            JSONObject[] contactsFromDb = queryWithInClause(CONTACTS_SOUP, ACCOUNT_ID, new String[]{account.getString(Constants.ID)}, SmartStore.SOUP_ENTRY_ID);
            JSONObject[] contacts = mapAccountContacts.get(account);
            assertEquals("Wrong number of contacts in db", contacts.length, contactsFromDb.length);
            for (int j = 0; j < contactsFromDb.length; j++) {
                JSONObject contact = contacts[j];
                JSONObject contactFromDb = contactsFromDb[j];

                assertEquals(contact.getString(Constants.ID), contactFromDb.getString(Constants.ID));
                assertEquals(Constants.CONTACT, contactFromDb.getJSONObject(Constants.ATTRIBUTES).getString(TYPE));
                assertEquals(false, contactFromDb.getBoolean(SyncTarget.LOCAL));
                assertEquals(false, contactFromDb.getBoolean(SyncTarget.LOCALLY_CREATED));
                assertEquals(false, contactFromDb.getBoolean(SyncTarget.LOCALLY_DELETED));
                assertEquals(false, contactFromDb.getBoolean(SyncTarget.LOCALLY_UPDATED));
                assertEquals(accountFromDb.getString(Constants.ID), contactFromDb.getString(ACCOUNT_ID));
                assertEquals(accountFromDb.getString(SmartStore.SOUP_ENTRY_ID), contactFromDb.getString(ACCOUNT_LOCAL_ID));
            }

        }
    }

    /**
     * Test getLatestModificationTimeStamp
     */
    public void testGetLatestModificationTimeStamp() throws JSONException {
        // Putting together a JSONArray of accounts with contacts
        // looking like what we would get back from startFetch/continueFetch
        // with different fields for last modified time
        final int numberAccounts = 4;
        final int numberContactsPerAccount = 3;

        final long[] timeStamps = new long[]{
                100000000,
                200000000,
                300000000,
                400000000
        };


        final String[] timeStampStrs = new String[]{
                Constants.TIMESTAMP_FORMAT.format(new Date(timeStamps[0])),
                Constants.TIMESTAMP_FORMAT.format(new Date(timeStamps[1])),
                Constants.TIMESTAMP_FORMAT.format(new Date(timeStamps[2])),
                Constants.TIMESTAMP_FORMAT.format(new Date(timeStamps[3])),
        };

        JSONObject accountAttributes = new JSONObject();
        accountAttributes.put(TYPE, Constants.ACCOUNT);

        JSONObject contactAttributes = new JSONObject();
        contactAttributes.put(TYPE, Constants.CONTACT);

        JSONObject[] accounts = new JSONObject[numberAccounts];
        Map<JSONObject, JSONObject[]> mapAccountContacts = new HashMap<>();

        for (int i = 0; i < numberAccounts; i++) {
            JSONObject account = new JSONObject();
            account.put(Constants.ID, createLocalId());
            account.put("AccountTimeStamp1", timeStampStrs[i % timeStampStrs.length]);
            account.put("AccountTimeStamp2", timeStampStrs[0]);

            JSONObject[] contacts = new JSONObject[numberContactsPerAccount];
            for (int j = 0; j < numberContactsPerAccount; j++) {
                JSONObject contact = new JSONObject();
                contact.put(Constants.ID, createLocalId());
                contact.put(ACCOUNT_ID, account.get(Constants.ID));
                contact.put("ContactTimeStamp1", timeStampStrs[1]);
                contact.put("ContactTimeStamp2", timeStampStrs[j % timeStampStrs.length]);
                contacts[j] = contact;
            }
            mapAccountContacts.put(account, contacts);
            accounts[i] = account;
        }

        JSONArray records = new JSONArray();
        for (JSONObject account : accounts) {
            JSONObject record = new JSONObject(account.toString());
            JSONArray contacts = new JSONArray();
            for (JSONObject contact : mapAccountContacts.get(account)) {
                contacts.put(contact);
            }
            record.put("Contacts", contacts);
            records.put(record);
        }

        // Maximums

        // Get max time stamps based on fields AccountTimeStamp1 / ContactTimeStamp1
        assertEquals(
                timeStamps[3],
                getAccountContactsSyncDownTarget("AccountTimeStamp1", "ContactTimeStamp1", null).getLatestModificationTimeStamp(records)
        );

        // Get max time stamps based on fields AccountTimeStamp1 / ContactTimeStamp2
        assertEquals(
                timeStamps[3],
                getAccountContactsSyncDownTarget("AccountTimeStamp1", "ContactTimeStamp2", null).getLatestModificationTimeStamp(records)
        );

        // Get max time stamps based on fields AccountTimeStamp2 / ContactTimeStamp1
        assertEquals(
                timeStamps[1],
                getAccountContactsSyncDownTarget("AccountTimeStamp2", "ContactTimeStamp1", null).getLatestModificationTimeStamp(records)
        );

        // Get max time stamps based on fields AccountTimeStamp2 / ContactTimeStamp2
        assertEquals(
                timeStamps[2],
                getAccountContactsSyncDownTarget("AccountTimeStamp2", "ContactTimeStamp2", null).getLatestModificationTimeStamp(records)
        );
    }

    /**
     * Test ParentChildrenSyncDownTarget's constructor that takes only a SOQL query
     * An exception is expected
     */
    public void testConstructorWithQuery() {
        try {
            new ParentChildrenSyncDownTarget("SELECT Name FROM Account");
            fail("Exception should have been thrown");
        } catch (UnsupportedOperationException e) {
        }
    }

    /**
     * Sync down the test accounts and contacts, check smart store, check status during sync
     */
    public void testSyncDown() throws Exception {
        final int numberAccounts = 4;
        final int numberContactsPerAccount = 3;

        // Creating test accounts and contacts on server
        createAccountsAndContactsOnServer(numberAccounts, numberContactsPerAccount);

        // Sync down
        ParentChildrenSyncDownTarget target = getAccountContactsSyncDownTarget(
                String.format("%s IN %s", Constants.ID, makeInClause(accountIdToFields.keySet())));
        trySyncDown(SyncState.MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, numberAccounts, 1);

        // Check that db was correctly populated
        checkDb(accountIdToFields, ACCOUNTS_SOUP);
        for (String accountId : accountIdToFields.keySet()) {
            long accountLocalId = syncManager.getSmartStore().lookupSoupEntryId(ACCOUNTS_SOUP, Constants.ID, accountId);
            Map<String, Map<String, Object>> contactIdToFields = accountIdContactIdToFields.get(accountId);
            for (String contactId : contactIdToFields.keySet()) {
                Map<String, Object> fields = contactIdToFields.get(contactId);
                // we expect to find the accountLocalId populated
                fields.put(ACCOUNT_LOCAL_ID, "" + accountLocalId);
            }
            checkDb(contactIdToFields, CONTACTS_SOUP);
        }
    }

    /**
     * Sync down the test accounts that do not have children contacts, check smart store, check status during sync
     */
    public void testSyncDownNoChildren() throws Exception {
        // Creating test accounts on server
        final int numberAccounts = 4;
        accountIdToFields = createRecordsOnServerReturnFields(numberAccounts, Constants.ACCOUNT, null);

        // Sync down
        ParentChildrenSyncDownTarget target = getAccountContactsSyncDownTarget(
                String.format("%s IN %s", Constants.ID, makeInClause(accountIdToFields.keySet())));
        trySyncDown(SyncState.MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, numberAccounts, 1);

        // Check that db was correctly populated
        checkDb(accountIdToFields, ACCOUNTS_SOUP);
    }

    /**
     * Sync down the test accounts and contacts, make some local changes,
     * then sync down again with merge mode LEAVE_IF_CHANGED then sync down with merge mode OVERWRITE
     */
    public void testSyncDownWithoutOverwrite() throws Exception {
        final int numberAccounts = 4;
        final int numberContactsPerAccount = 3;

        // Creating test accounts and contacts on server
        createAccountsAndContactsOnServer(numberAccounts, numberContactsPerAccount);

        // Sync down
        ParentChildrenSyncDownTarget target = getAccountContactsSyncDownTarget(
                String.format("%s IN %s", Constants.ID, makeInClause(accountIdToFields.keySet())));
        trySyncDown(SyncState.MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, numberAccounts, 1);

        // Make some local changes
        String[] accountIds = accountIdToFields.keySet().toArray(new String[0]);
        String accountIdUpdated = accountIds[0]; // account that will updated along with some of the children
        Map<String, Map<String, Object>> accountIdToFieldsUpdated = makeLocalChanges(accountIdToFields, ACCOUNTS_SOUP, new String[]{accountIdUpdated});
        Map<String, Map<String, Object>> contactIdToFieldsUpdated = makeLocalChanges(accountIdContactIdToFields.get(accountIdUpdated), CONTACTS_SOUP);
        String otherAccountId = accountIds[1]; // account that will not be updated but will have updated children
        Map<String, Map<String, Object>> otherContactIdToFieldsUpdated = makeLocalChanges(accountIdContactIdToFields.get(otherAccountId), CONTACTS_SOUP);

        // Sync down again with MergeMode.LEAVE_IF_CHANGED
        trySyncDown(SyncState.MergeMode.LEAVE_IF_CHANGED, target, ACCOUNTS_SOUP, numberAccounts, 1);

        // Check db - if an account and/or its children was locally modified then that account and all its children should be left alone
        Map<String, Map<String, Object>> accountIdToFieldsExpected = new HashMap<>(accountIdToFields);
        accountIdToFieldsExpected.putAll(accountIdToFieldsUpdated);
        checkDb(accountIdToFieldsExpected, ACCOUNTS_SOUP);

        for (String accountId : accountIdToFields.keySet()) {
            if (accountId.equals(accountIdUpdated)) {
                checkDbStateFlags(Arrays.asList(new String[]{accountId}), false, true, false, ACCOUNTS_SOUP);
                checkDb(contactIdToFieldsUpdated, CONTACTS_SOUP);
                checkDbStateFlags(contactIdToFieldsUpdated.keySet(), false, true, false, CONTACTS_SOUP);
            } else if (accountId.equals(otherAccountId)) {
                checkDbStateFlags(Arrays.asList(new String[]{accountId}), false, false, false, ACCOUNTS_SOUP);
                checkDb(otherContactIdToFieldsUpdated, CONTACTS_SOUP);
                checkDbStateFlags(otherContactIdToFieldsUpdated.keySet(), false, true, false, CONTACTS_SOUP);
            } else {
                checkDbStateFlags(Arrays.asList(new String[]{accountId}), false, false, false, ACCOUNTS_SOUP);
                checkDb(accountIdContactIdToFields.get(accountId), CONTACTS_SOUP);
                checkDbStateFlags(accountIdContactIdToFields.get(accountId).keySet(), false, false, false, CONTACTS_SOUP);
            }
        }


        // Sync down again with MergeMode.OVERWRITE
        trySyncDown(SyncState.MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, numberAccounts, 1);

        // Check db - all local changes should have been written over
        checkDb(accountIdToFields, ACCOUNTS_SOUP);
        checkDbStateFlags(accountIdToFields.keySet(), false, false, false, ACCOUNTS_SOUP);

        for (String accountId : accountIdToFields.keySet()) {
            checkDb(accountIdContactIdToFields.get(accountId), CONTACTS_SOUP);
            checkDbStateFlags(accountIdContactIdToFields.get(accountId).keySet(), false, false, false, CONTACTS_SOUP);
        }
    }

    /**
     * Sync down the test accounts and contacts, modify accounts, re-sync, make sure only the updated ones are downloaded
     */
    public void testReSyncWithUpdatedParents() throws Exception {
        final int numberAccounts = 4;
        final int numberContactsPerAccount = 3;

        // Creating up test accounts and contacts on server
        createAccountsAndContactsOnServer(numberAccounts, numberContactsPerAccount);

        // Sync down
        ParentChildrenSyncDownTarget target = getAccountContactsSyncDownTarget(
                String.format("%s IN %s", Constants.ID, makeInClause(accountIdToFields.keySet())));
        long syncId = trySyncDown(SyncState.MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, numberAccounts, 1);

        // Check sync time stamp
        SyncState sync = syncManager.getSyncStatus(syncId);
        SyncOptions options = sync.getOptions();
        long maxTimeStamp = sync.getMaxTimeStamp();
        assertTrue("Wrong time stamp", maxTimeStamp > 0);

        // Make some remote change to accounts
        Map<String, Map<String, Object>> idToFieldsUpdated = makeRemoteChanges(accountIdToFields, Constants.ACCOUNT);

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
        assertTrue("Wrong time stamp", syncManager.getSyncStatus(syncId).getMaxTimeStamp() > maxTimeStamp);
    }

    /**
     * Sync down the test accounts and contacts
     * Modify an account and some of its contacts and modify other contacts (without changing parent account)
     * Make sure only the modified account and its modified contacts are re-synced
     */
    public void testReSyncWithUpdatedChildren() throws Exception {
        final int numberAccounts = 4;
        final int numberContactsPerAccount = 3;

        // Creating up test accounts and contacts on server
        createAccountsAndContactsOnServer(numberAccounts, numberContactsPerAccount);

        // Sync down
        ParentChildrenSyncDownTarget target = getAccountContactsSyncDownTarget(
                String.format("%s IN %s", Constants.ID, makeInClause(accountIdToFields.keySet())));
        long syncId = trySyncDown(SyncState.MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, numberAccounts, 1);

        // Check sync time stamp
        SyncState sync = syncManager.getSyncStatus(syncId);
        SyncOptions options = sync.getOptions();
        long maxTimeStamp = sync.getMaxTimeStamp();
        assertTrue("Wrong time stamp", maxTimeStamp > 0);

        // Make some remote changes
        String[] accountIds = accountIdToFields.keySet().toArray(new String[0]);
        String accountId = accountIds[0]; // account that will updated along with some of the children
        Map<String, Map<String, Object>> accountIdToFieldsUpdated = makeRemoteChanges(accountIdToFields, Constants.ACCOUNT, new String[]{accountId});
        Map<String, Map<String, Object>> contactIdToFieldsUpdated = makeRemoteChanges(accountIdContactIdToFields.get(accountId), Constants.CONTACT);
        String otherAccountId = accountIds[1]; // account that will not be updated but will have updated children
        Map<String, Map<String, Object>> otherContactIdToFieldsUpdated = makeRemoteChanges(accountIdContactIdToFields.get(otherAccountId), Constants.CONTACT);

        // Call reSync
        SyncUpdateCallbackQueue queue = new SyncUpdateCallbackQueue();
        syncManager.reSync(syncId, queue);

        // Check status updates
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0, -1);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0, 1);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.DONE, 100, 1);

        // Check db
        checkDb(accountIdToFieldsUpdated, ACCOUNTS_SOUP); // updated account should be updated in db
        checkDb(contactIdToFieldsUpdated, CONTACTS_SOUP); // updated contacts of updated account should be updated in db
        checkDb(accountIdContactIdToFields.get(otherAccountId), CONTACTS_SOUP); // updated contacts of non-updated account should not be updated in db

        // Check sync time stamp
        assertTrue("Wrong time stamp", syncManager.getSyncStatus(syncId).getMaxTimeStamp() > maxTimeStamp);
    }


    /**
     * Sync down the test accounts and contacts
     * Delete account from server - run cleanResyncGhosts
     */
    public void testCleanResyncGhostsForParentChildrenTarget() throws Exception {
        final int numberAccounts = 4;
        final int numberContactsPerAccount = 3;

        // Creating up test accounts and contacts on server
        createAccountsAndContactsOnServer(numberAccounts, numberContactsPerAccount);

        // Sync down
        ParentChildrenSyncDownTarget target = getAccountContactsSyncDownTarget(
                String.format("%s IN %s", Constants.ID, makeInClause(accountIdToFields.keySet())));
        long syncId = trySyncDown(SyncState.MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, numberAccounts, 1);

        // Deletes 1 account on the server and verifies the ghost record is cleared from the soup.
        String accountIdDeleted = accountIdToFields.keySet().toArray(new String[0])[0];
        deleteRecordsOnServer(new HashSet<String>(Arrays.asList(accountIdDeleted)), Constants.ACCOUNT);
        syncManager.cleanResyncGhosts(syncId);

        // Accounts and contacts expected to still be in db
        Map<String, Map<String, Object>> accountIdToFieldsLeft = new HashMap<>(accountIdToFields);
        accountIdToFieldsLeft.remove(accountIdDeleted);

        // Checking db
        checkDb(accountIdToFieldsLeft, ACCOUNTS_SOUP);
        checkDbDeleted(ACCOUNTS_SOUP, new String[]{accountIdDeleted}, Constants.ID);
        for (String accountId : accountIdContactIdToFields.keySet()) {
            if (accountId.equals(accountIdDeleted)) {
                checkDbDeleted(CONTACTS_SOUP, accountIdContactIdToFields.get(accountId).keySet().toArray(new String[0]), Constants.ID);
            } else {
                checkDb(accountIdContactIdToFields.get(accountId), CONTACTS_SOUP);

            }
        }
    }

    /**
     * Create accounts and contacts locally, sync up with merge mode OVERWRITE, check smartstore and server afterwards
     */
    public void testSyncUpWithLocallyCreatedRecords() throws Exception {
        trySyncUpWithLocallyCreatedRecords(SyncState.MergeMode.OVERWRITE);
    }

    /**
     * Create accounts and contacts locally, sync up with mege mode LEAVE_IF_CHANGED, check smartstore and server afterwards
     */
    public void testSyncUpWithLocallyCreatedRecordsWithoutOverwrite() throws Exception {
        trySyncUpWithLocallyCreatedRecords(SyncState.MergeMode.LEAVE_IF_CHANGED);
    }

    /**
     * Sync up with locally updated child
     *
     * @throws Exception
     */
    public void testSyncUpLocallyUpdatedChild() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.NONE, Change.NONE, Change.UPDATE, Change.NONE);
    }

    /**
     * Sync up with locally updated child remotely updated child
     *
     * @throws Exception
     */
    public void testSyncUpLocallyUpdatedChildRemotelyUpdatedChild() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.NONE, Change.NONE, Change.UPDATE, Change.UPDATE);
    }

    /**
     * Sync up with locally updated child remotely deleted child
     *
     * @throws Exception
     */
    public void testSyncUpLocallyUpdatedChildRemotelyDeletedChild() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.NONE, Change.NONE, Change.UPDATE, Change.DELETE);
    }

    /**
     * Sync up with locally deleted child
     *
     * @throws Exception
     */
    public void testSyncUpLocallyDeletedChild() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.NONE, Change.NONE, Change.DELETE, Change.NONE);
    }

    /**
     * Sync up with locally deleted child remotely updated child
     *
     * @throws Exception
     */
    public void testSyncUpLocallyDeletedChildRemotelyUpdatedChild() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.NONE, Change.NONE, Change.DELETE, Change.UPDATE);
    }

    /**
     * Sync up with locally deleted child remotely deleted child
     *
     * @throws Exception
     */
    public void testSyncUpLocallyDeletedChildRemotelyDeletedChild() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.NONE, Change.NONE, Change.DELETE, Change.DELETE);
    }

    /**
     * Sync up with locally updated parent
     *
     * @throws Exception
     */
    public void testSyncUpLocallyUpdatedParent() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.UPDATE, Change.NONE, Change.NONE, Change.NONE);
    }

    /**
     * Sync up with locally updated parent remotely updated parent
     *
     * @throws Exception
     */
    public void testSyncUpLocallyUpdatedParentRemotelyUpdatedParent() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.UPDATE, Change.UPDATE, Change.NONE, Change.NONE);
    }

    /**
     * Sync up with locally updated parent remotely deleted parent
     *
     * @throws Exception
     */
    public void testSyncUpLocallyUpdatedParentRemotelyDeletedParent() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.UPDATE, Change.DELETE, Change.NONE, Change.NONE);
    }

    /**
     * Sync up with locally updated parent updated child
     *
     * @throws Exception
     */
    public void testSyncUpLocallyUpdatedParentUpdatedChild() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.UPDATE, Change.NONE, Change.UPDATE, Change.NONE);
    }

    /**
     * Sync up with locally updated parent updated child remotely updated child
     *
     * @throws Exception
     */
    public void testSyncUpLocallyUpdatedParentUpdatedChildRemotelyUpdatedChild() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.UPDATE, Change.NONE, Change.UPDATE, Change.UPDATE);
    }

    /**
     * Sync up with locally updated parent updated child remotely deleted child
     *
     * @throws Exception
     */
    public void testSyncUpLocallyUpdatedParentUpdatedChildRemotelyDeletedChild() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.UPDATE, Change.NONE, Change.UPDATE, Change.DELETE);
    }

    /**
     * Sync up with locally updated parent updated child remotely updated parent
     *
     * @throws Exception
     */
    public void testSyncUpLocallyUpdatedParentUpdatedChildRemotelyUpdatedParent() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.UPDATE, Change.UPDATE, Change.UPDATE, Change.NONE);
    }

    /**
     * Sync up with locally updated parent updated child remotely updated parent updated child
     *
     * @throws Exception
     */
    public void testSyncUpLocallyUpdatedParentUpdatedChildRemotelyUpdatedParentUpdatedChild() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.UPDATE, Change.UPDATE, Change.UPDATE, Change.UPDATE);
    }

    /**
     * Sync up with locally updated parent updated child remotely updated parent deleted child
     *
     * @throws Exception
     */
    public void testSyncUpLocallyUpdatedParentUpdatedChildRemotelyUpdatedParentDeletedChild() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.UPDATE, Change.UPDATE, Change.UPDATE, Change.DELETE);
    }

    /**
     * Sync up with locally updated parent updated child remotely deleted parent
     *
     * @throws Exception
     */
    public void testSyncUpLocallyUpdatedParentUpdatedChildRemotelyDeletedParent() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.UPDATE, Change.DELETE, Change.UPDATE, Change.NONE);
    }

    /**
     * Sync up with locally updated parent deleted child
     *
     * @throws Exception
     */
    public void testSyncUpLocallyUpdatedParentDeletedChild() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.UPDATE, Change.NONE, Change.DELETE, Change.NONE);
    }

    /**
     * Sync up with locally updated parent deleted child remotely updated child
     *
     * @throws Exception
     */
    public void testSyncUpLocallyUpdatedParentDeletedChildRemotelyUpdatedChild() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.UPDATE, Change.NONE, Change.DELETE, Change.UPDATE);
    }

    /**
     * Sync up with locally updated parent deleted child remotely deleted child
     *
     * @throws Exception
     */
    public void testSyncUpLocallyUpdatedParentDeletedChildRemotelyDeletedChild() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.UPDATE, Change.NONE, Change.DELETE, Change.DELETE);
    }

    /**
     * Sync up with locally updated parent deleted child remotely updated parent
     *
     * @throws Exception
     */
    public void testSyncUpLocallyUpdatedParentDeletedChildRemotelyUpdatedParent() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.UPDATE, Change.UPDATE, Change.DELETE, Change.NONE);
    }

    /**
     * Sync up with locally updated parent deleted child remotely updated parent updated child
     *
     * @throws Exception
     */
    public void testSyncUpLocallyUpdatedParentDeletedChildRemotelyUpdatedParentUpdatedChild() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.UPDATE, Change.UPDATE, Change.DELETE, Change.UPDATE);
    }

    /**
     * Sync up with locally updated parent deleted child remotely updated parent deleted child
     *
     * @throws Exception
     */
    public void testSyncUpLocallyUpdatedParentDeletedChildRemotelyUpdatedParentDeletedChild() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.UPDATE, Change.UPDATE, Change.DELETE, Change.DELETE);
    }

    /**
     * Sync up with locally updated parent deleted child remotely deleted parent
     *
     * @throws Exception
     */
    public void testSyncUpLocallyUpdatedParentDeletedChildRemotelyDeletedParent() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.UPDATE, Change.DELETE, Change.DELETE, Change.NONE);
    }

    /**
     * Sync up with locally deleted parent
     *
     * @throws Exception
     */
    public void testSyncUpLocallyDeletedParent() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.DELETE, Change.NONE, Change.NONE, Change.NONE);
    }

    /**
     * Sync up with locally deleted parent remotely updated parent
     *
     * @throws Exception
     */
    public void testSyncUpLocallyDeletedParentRemotelyUpdatedParent() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.DELETE, Change.UPDATE, Change.NONE, Change.NONE);
    }

    /**
     * Sync up with locally deleted parent remotely deleted parent
     *
     * @throws Exception
     */
    public void testSyncUpLocallyDeletedParentRemotelyDeletedParent() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.DELETE, Change.DELETE, Change.NONE, Change.NONE);
    }

    /**
     * Sync up with locally deleted parent updated child
     *
     * @throws Exception
     */
    public void testSyncUpLocallyDeletedParentUpdatedChild() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.DELETE, Change.NONE, Change.UPDATE, Change.NONE);
    }

    /**
     * Sync up with locally deleted parent updated child remotely updated parent
     *
     * @throws Exception
     */
    public void testSyncUpLocallyDeletedParentUpdatedChildRemotelyUpdatedParent() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.DELETE, Change.UPDATE, Change.UPDATE, Change.NONE);
    }

    /**
     * Sync up with locally deleted parent updated child remotely deleted parent
     *
     * @throws Exception
     */
    public void testSyncUpLocallyDeletedParentUpdatedChildRemotelyDeletedParent() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.DELETE, Change.DELETE, Change.UPDATE, Change.NONE);
    }

    /**
     * Sync up with locally deleted parent deleted child
     *
     * @throws Exception
     */
    public void testSyncUpLocallyDeletedParentDeletedChild() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.DELETE, Change.NONE, Change.DELETE, Change.NONE);
    }

    /**
     * Sync up with locally deleted parent deleted child remotely updated parent
     *
     * @throws Exception
     */
    public void testSyncUpLocallyDeletedParentDeletedChildRemotelyUpdatedParent() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.DELETE, Change.UPDATE, Change.DELETE, Change.NONE);
    }

    /**
     * Sync up with locally deleted parent deleted child remotely deleted parent
     *
     * @throws Exception
     */
    public void testSyncUpLocallyDeletedParentDeletedChildRemotelyDeletedParent() throws Exception {
        trySyncUpsWithVariousChanges(2, 2, Change.DELETE, Change.DELETE, Change.DELETE, Change.NONE);
    }

    /**
     * Sync up with locally updated parent and no children
     *
     * @throws Exception
     */
    public void testSyncUpLocallyUpdatedParentNoChildren() throws Exception {
        trySyncUpsWithVariousChanges(2, 0, Change.UPDATE, Change.NONE, Change.NONE, Change.NONE);
    }

    /**
     * Sync up with locally updated parent remotely updated parent and no children
     *
     * @throws Exception
     */
    public void testSyncUpLocallyUpdatedParentRemotelyUpdatedParentNoChildren() throws Exception {
        trySyncUpsWithVariousChanges(2, 0, Change.UPDATE, Change.UPDATE, Change.NONE, Change.NONE);
    }

    /**
     * Sync up with locally updated parent remotely deleted parent and no children
     *
     * @throws Exception
     */
    public void testSyncUpLocallyUpdatedParentRemotelyDeletedParentNoChildren() throws Exception {
        trySyncUpsWithVariousChanges(2, 0, Change.UPDATE, Change.DELETE, Change.NONE, Change.NONE);
    }

    /**
     * Sync up with locally deleted parent and no children
     *
     * @throws Exception
     */
    public void testSyncUpLocallyDeletedParentNoChildren() throws Exception {
        trySyncUpsWithVariousChanges(2, 0, Change.DELETE, Change.NONE, Change.NONE, Change.NONE);
    }

    /**
     * Sync up with locally deleted parent remotely updated parent and no children
     *
     * @throws Exception
     */
    public void testSyncUpLocallyDeletedParentRemotelyUpdatedParentNoChildren() throws Exception {
        trySyncUpsWithVariousChanges(2, 0, Change.DELETE, Change.UPDATE, Change.NONE, Change.NONE);
    }

    /**
     * Sync up with locally deleted parent remotely deleted parent and no children
     *
     * @throws Exception
     */
    public void testSyncUpLocallyDeletedParentRemotelyDeletedParentNoChildren() throws Exception {
        trySyncUpsWithVariousChanges(2, 0, Change.DELETE, Change.DELETE, Change.NONE, Change.NONE);
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
    private void trySyncUpsWithVariousChanges(int numberAccounts,
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

        // If there was no remote change or it's remote delete with a local delete
        if ((remoteChangeForAccount == Change.NONE && remoteChangeForContact == Change.NONE)
                || (remoteChangeForAccount == Change.DELETE && localChangeForAccount == Change.DELETE)
                || (remoteChangeForContact == Change.DELETE && localChangeForContact == Change.DELETE))
        {
            // Sync up with leave-if-changed
            trySyncUp(syncUpTarget, 1, SyncState.MergeMode.LEAVE_IF_CHANGED);

            // Check db and server - local changes should have made it over
            checkDbAndServerAfterCompletedSyncUp(accountId, contactId, otherContactId, remoteChangeForAccount, localChangeForContact, remoteChangeForContact, localUpdatesAccount, localUpdatesContact, localChangeForAccount);

            // Sync up with overwrite - there should be dirty records found
            trySyncUp(syncUpTarget, 0, SyncState.MergeMode.OVERWRITE);
        }
        // If there was a remote change
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
    private String checkRecordRecreated(String recordId, Map<String, Object> fields, String nameField, String soupName, String objectType, String parentId, String parentIdField) throws JSONException, IOException {
        String updatedName = (String) fields.get(nameField);
        Map<String, Map<String, Object>> newIdToFields = getIdToFieldsByName(soupName, new String[]{nameField}, nameField, new String[]{updatedName});
        String newRecordId = newIdToFields.keySet().toArray(new String[0])[0];

        // Make sure new id is really new
        assertFalse("Record should have new id", newRecordId.equals(recordId));

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
    enum Change {
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
    private void trySyncUpWithLocallyCreatedRecords(SyncState.MergeMode syncUpMergeMode) throws Exception {
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

        // Check accounts on server
        checkServer(contactIdToFieldsCreated, Constants.CONTACT);

        // Cleanup
        deleteRecordsOnServer(accountIdToFieldsCreated.keySet(), Constants.ACCOUNT);
        deleteRecordsOnServer(contactIdToFieldsCreated.keySet(), Constants.CONTACT);
    }

    private void tryGetDirtyRecordIds(JSONObject[] expectedRecords) throws JSONException {
        ParentChildrenSyncDownTarget target = getAccountContactsSyncDownTarget();
        SortedSet<String> dirtyRecordIds = target.getDirtyRecordIds(syncManager, ACCOUNTS_SOUP, Constants.ID);
        assertEquals("Wrong number of dirty records", expectedRecords.length, dirtyRecordIds.size());
        for (JSONObject expectedRecord : expectedRecords) {
            assertTrue(dirtyRecordIds.contains(expectedRecord.getString(Constants.ID)));
        }
    }

    private void tryGetNonDirtyRecordIds(JSONObject[] expectedRecords) throws JSONException {
        ParentChildrenSyncDownTarget target = getAccountContactsSyncDownTarget();
        SortedSet<String> nonDirtyRecordIds = target.getNonDirtyRecordIds(syncManager, ACCOUNTS_SOUP, Constants.ID);
        assertEquals("Wrong number of non-dirty records", expectedRecords.length, nonDirtyRecordIds.size());
        for (JSONObject expectedRecord : expectedRecords) {
            assertTrue(nonDirtyRecordIds.contains(expectedRecord.getString(Constants.ID)));
        }
    }

    private void cleanRecords(String soupName, JSONObject[] records) throws JSONException {
        for (JSONObject record : records) {
            cleanRecord(soupName, record);
        }
    }

    private void cleanRecord(String soupName, JSONObject record) throws JSONException {
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
                new IndexSpec(ACCOUNT_ID, SmartStore.Type.string),
                new IndexSpec(ACCOUNT_LOCAL_ID, SmartStore.Type.integer)
        };
        smartStore.registerSoup(CONTACTS_SOUP, contactsIndexSpecs);
    }

    private void dropContactsSoup() {
        smartStore.dropSoup(CONTACTS_SOUP);
    }

    private ParentChildrenSyncDownTarget getAccountContactsSyncDownTarget() {
        return getAccountContactsSyncDownTarget("");
    }

    private ParentChildrenSyncDownTarget getAccountContactsSyncDownTarget(String parentSoqlFilter) {
        return getAccountContactsSyncDownTarget(Constants.LAST_MODIFIED_DATE, Constants.LAST_MODIFIED_DATE, parentSoqlFilter);
    }

    private ParentChildrenSyncDownTarget getAccountContactsSyncDownTarget(String accountModificationDateFieldName, String contactModificationDateFieldName, String parentSoqlFilter) {
        return new ParentChildrenSyncDownTarget(
                new ParentInfo(Constants.ACCOUNT, ACCOUNTS_SOUP, Constants.ID, accountModificationDateFieldName),
                Arrays.asList(Constants.ID, Constants.NAME, Constants.DESCRIPTION),
                parentSoqlFilter,
                new ChildrenInfo(Constants.CONTACT, Constants.CONTACT + "s", CONTACTS_SOUP, ACCOUNT_ID, ACCOUNT_LOCAL_ID, Constants.ID, contactModificationDateFieldName),
                Arrays.asList(Constants.LAST_NAME),
                RelationshipType.MASTER_DETAIL); // account-contacts are master-detail
    }

    private ParentChildrenSyncUpTarget getAccountContactsSyncUpTarget() {
        return getAccountContactsSyncUpTarget("");
    }

    private ParentChildrenSyncUpTarget getAccountContactsSyncUpTarget(String parentSoqlFilter) {
        return getAccountContactsSyncUpTarget(Constants.LAST_MODIFIED_DATE, Constants.LAST_MODIFIED_DATE, parentSoqlFilter);
    }

    private ParentChildrenSyncUpTarget getAccountContactsSyncUpTarget(String accountModificationDateFieldName, String contactModificationDateFieldName, String parentSoqlFilter) {
        return new ParentChildrenSyncUpTarget(
                new ParentInfo(Constants.ACCOUNT, ACCOUNTS_SOUP, Constants.ID, accountModificationDateFieldName),
                Arrays.asList(Constants.ID, Constants.NAME, Constants.DESCRIPTION),
                Arrays.asList(Constants.NAME, Constants.DESCRIPTION),
                new ChildrenInfo(Constants.CONTACT, Constants.CONTACT + "s", CONTACTS_SOUP, ACCOUNT_ID, ACCOUNT_LOCAL_ID, Constants.ID, contactModificationDateFieldName),
                Arrays.asList(Constants.LAST_NAME),
                Arrays.asList(Constants.LAST_NAME),
                RelationshipType.MASTER_DETAIL); // account-contacts are master-detail
    }

    private Map<JSONObject, JSONObject[]> createAccountsAndContactsLocally(String[] names, int numberOfContactsPerAccount) throws JSONException {
        Map<JSONObject, JSONObject[]> mapAccountContacts = new HashMap<>();
        JSONObject[] accounts = createAccountsLocally(names);

        JSONObject attributes = new JSONObject();
        attributes.put(TYPE, Constants.CONTACT);

        for (JSONObject account : accounts) {
            JSONObject[] contacts = new JSONObject[numberOfContactsPerAccount];
            for (int i = 0; i < numberOfContactsPerAccount; i++) {
                JSONObject contact = new JSONObject();
                contact.put(Constants.ID, createLocalId());
                contact.put(Constants.LAST_NAME, "Contact_" + account.get(Constants.NAME) + "_" + i);
                contact.put(Constants.ATTRIBUTES, attributes);
                contact.put(SyncTarget.LOCAL, true);
                contact.put(SyncTarget.LOCALLY_CREATED, true);
                contact.put(SyncTarget.LOCALLY_DELETED, false);
                contact.put(SyncTarget.LOCALLY_UPDATED, false);
                contact.put(ACCOUNT_ID, account.get(Constants.ID));
                contact.put(ACCOUNT_LOCAL_ID, account.get(SmartStore.SOUP_ENTRY_ID));
                contacts[i] = smartStore.create(CONTACTS_SOUP, contact);
            }
            mapAccountContacts.put(account, contacts);
        }
        return mapAccountContacts;
    }

    private JSONObject[] queryWithInClause(String soupName, String fieldName, String[] values, String orderBy) throws JSONException {
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
        for (int i=0; i<results.length(); i++) {
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
            }
            else {
                if (!accountIdContactIdToFields.containsKey(accountId))
                    accountIdContactIdToFields.put(accountId, new HashMap<String, Map<String, Object>>());
                accountIdContactIdToFields.get(accountId).put(contactId, fields);
            }
        }
    }

}
