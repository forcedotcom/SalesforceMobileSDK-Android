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

import androidx.test.filters.LargeTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.salesforce.androidsdk.smartstore.store.SmartStore;
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
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Test class for ParentChildrenSyncDownTarget and ParentChildrenSyncUpTarget.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class ParentChildrenSyncTest extends ParentChildrenSyncTestCase {

    /**
     * Test getQuery for ParentChildrenSyncDownTarget
     */
    @Test
    public void testGetQuery() {
        ParentChildrenSyncDownTarget target = new ParentChildrenSyncDownTarget(
                new ParentInfo("Parent", "parentsSoup", "ParentId", "ParentModifiedDate"),
                Arrays.asList("ParentName", "Title"),
                "School = 'MIT'",
                new ChildrenInfo("Child", "Children", "childrenSoup", "parentId", "ChildId", "ChildLastModifiedDate"),
                Arrays.asList("ChildName", "School"),
                RelationshipType.LOOKUP);
        Assert.assertEquals("select ParentName, Title, ParentId, ParentModifiedDate, (select ChildName, School, ChildId, ChildLastModifiedDate from Children) from Parent where School = 'MIT'", target.getQuery());

        // With default id and modification date fields
        target = new ParentChildrenSyncDownTarget(
                new ParentInfo("Parent", "parentsSoup"),
                Arrays.asList("ParentName", "Title"),
                "School = 'MIT'",
                new ChildrenInfo("Child", "Children", "childrenSoup", "parentId"),
                Arrays.asList("ChildName", "School"),
                RelationshipType.LOOKUP);
        Assert.assertEquals("select ParentName, Title, Id, LastModifiedDate, (select ChildName, School, Id, LastModifiedDate from Children) from Parent where School = 'MIT'", target.getQuery());
    }

    /**
     * Test query for reSync by calling getQuery with maxTimeStamp for ParentChildrenSyncDownTarget
     */
    @Test
    public void testGetQueryWithMaxTimeStamp() {
        Date date = new Date();
        String dateStr = Constants.TIMESTAMP_FORMAT.format(date);
        long dateLong = date.getTime();
        ParentChildrenSyncDownTarget target = new ParentChildrenSyncDownTarget(
                new ParentInfo("Parent", "parentsSoup", "ParentId", "ParentModifiedDate"),
                Arrays.asList("ParentName", "Title"),
                "School = 'MIT'",
                new ChildrenInfo("Child", "Children", "childrenSoup", "parentId", "ChildId", "ChildLastModifiedDate"),
                Arrays.asList("ChildName", "School"),
                RelationshipType.LOOKUP);
        Assert.assertEquals("select ParentName, Title, ParentId, ParentModifiedDate, (select ChildName, School, ChildId, ChildLastModifiedDate from Children where ChildLastModifiedDate > " + dateStr + ") from Parent where ParentModifiedDate > " + dateStr + " and School = 'MIT'", target.getQuery(dateLong));

        // With default id and modification date fields
        target = new ParentChildrenSyncDownTarget(
                new ParentInfo("Parent", "parentsSoup"),
                Arrays.asList("ParentName", "Title"),
                "School = 'MIT'",
                new ChildrenInfo("Child", "Children", "childrenSoup", "parentId"),
                Arrays.asList("ChildName", "School"),
                RelationshipType.LOOKUP);
        Assert.assertEquals("select ParentName, Title, Id, LastModifiedDate, (select ChildName, School, Id, LastModifiedDate from Children where LastModifiedDate > " + dateStr + ") from Parent where LastModifiedDate > " + dateStr + " and School = 'MIT'", target.getQuery(dateLong));
    }

    /**
     * Test getSoqlForRemoteIds for ParentChildrenSyncDownTarget
     */
    @Test
    public void testGetSoqlForRemoteIds() {
        ParentChildrenSyncDownTarget target = new ParentChildrenSyncDownTarget(
                new ParentInfo("Parent", "parentsSoup", "ParentId", "ParentModifiedDate"),
                Arrays.asList("ParentName", "Title"),
                "School = 'MIT'",
                new ChildrenInfo("Child", "Children", "childrenSoup", "ChildParentId", "ChildId", "ChildLastModifiedDate"),
                Arrays.asList("ChildName", "School"),
                RelationshipType.LOOKUP);
        Assert.assertEquals("select ParentId from Parent where School = 'MIT'", target.getSoqlForRemoteIds());

        // With default id and modification date fields
        target = new ParentChildrenSyncDownTarget(
                new ParentInfo("Parent", "parentsSoup"),
                Arrays.asList("ParentName", "Title"),
                "School = 'MIT'",
                new ChildrenInfo("Child", "Children", "childrenSoup", "ChildParentId"),
                Arrays.asList("ChildName", "School"),
                RelationshipType.LOOKUP);
        Assert.assertEquals("select Id from Parent where School = 'MIT'", target.getSoqlForRemoteIds());
    }

    /**
     * Test getDirtyRecordIdsSql for ParentChildrenSyncDownTarget
     */
    @Test
    public void testGetDirtyRecordIdsSql() {
        ParentChildrenSyncDownTarget target = new ParentChildrenSyncDownTarget(
                new ParentInfo("Parent", "parentsSoup", "ParentId", "ParentModifiedDate"),
                Arrays.asList("ParentName", "Title"),
                "School = 'MIT'",
                new ChildrenInfo("Child", "Children", "childrenSoup", "ChildParentId", "ChildId", "ChildLastModifiedDate"),
                Arrays.asList("ChildName", "School"),
                RelationshipType.LOOKUP);
        Assert.assertEquals("SELECT DISTINCT {parentsSoup:IdForQuery} FROM {parentsSoup} WHERE {parentsSoup:__local__} = 'true' OR EXISTS (SELECT {childrenSoup:ChildId} FROM {childrenSoup} WHERE {childrenSoup:ChildParentId} = {parentsSoup:ParentId} AND {childrenSoup:__local__} = 'true')",
                target.getDirtyRecordIdsSql("parentsSoup", "IdForQuery"));
    }

    /**
     * Test getNonDirtyRecordIdsSql for ParentChildrenSyncDownTarget
     */
    @Test
    public void testGetNonDirtyRecordIdsSql() {
        ParentChildrenSyncDownTarget target = new ParentChildrenSyncDownTarget(
                new ParentInfo("Parent", "parentsSoup", "ParentId", "ParentModifiedDate"),
                Arrays.asList("ParentName", "Title"),
                "School = 'MIT'",
                new ChildrenInfo("Child", "Children", "childrenSoup", "ChildParentId", "ChildId", "ChildLastModifiedDate"),
                Arrays.asList("ChildName", "School"),
                RelationshipType.LOOKUP);
        Assert.assertEquals("SELECT DISTINCT {parentsSoup:IdForQuery} FROM {parentsSoup} WHERE {parentsSoup:__local__} = 'false' AND {parentsSoup:__sync_id__} = 123 AND NOT EXISTS (SELECT {childrenSoup:ChildId} FROM {childrenSoup} WHERE {childrenSoup:ChildParentId} = {parentsSoup:ParentId} AND {childrenSoup:__local__} = 'true')",
                target.getNonDirtyRecordIdsSql("parentsSoup", "IdForQuery", "AND {parentsSoup:__sync_id__} = 123"));

    }

    /**
     * Test getDirtyRecordIds and getNonDirtyRecordIds for ParentChildrenSyncDownTarget when parent and/or all and/or some children are dirty
     */
    @Test
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
    @Test
    public void testSaveRecordsToLocalStore() throws JSONException {
        // Putting together a JSONArray of accounts with contacts
        // looking like what we would get back from startFetch/continueFetch
        // - not having local fields
        // - not have _soupEntryId field
        final int numberAccounts = 4;
        final int numberContactsPerAccount = 3;
        final long syncId = 123;
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
        target.saveRecordsToLocalStore(syncManager, ACCOUNTS_SOUP, records, syncId);

        // Checking accounts and contacts soup
        // Making sure local fields are populated
        // Making sure accountId and accountLocalId fields are populated on contacts
        JSONObject[] accountsFromDb = queryWithInClause(ACCOUNTS_SOUP, Constants.ID, JSONObjectHelper.pluck(accounts, Constants.ID).toArray(new String[0]), null);
        Assert.assertEquals("Wrong number of accounts in db", accounts.length, accountsFromDb.length);
        for (int i = 0; i < accountsFromDb.length; i++) {
            JSONObject account = accounts[i];
            JSONObject accountFromDb = accountsFromDb[i];
            Assert.assertEquals(account.getString(Constants.ID), accountFromDb.getString(Constants.ID));
            Assert.assertEquals(Constants.ACCOUNT, accountFromDb.getJSONObject(Constants.ATTRIBUTES).getString(TYPE));
            Assert.assertEquals(false, accountFromDb.getBoolean(SyncTarget.LOCAL));
            Assert.assertEquals(false, accountFromDb.getBoolean(SyncTarget.LOCALLY_CREATED));
            Assert.assertEquals(false, accountFromDb.getBoolean(SyncTarget.LOCALLY_DELETED));
            Assert.assertEquals(false, accountFromDb.getBoolean(SyncTarget.LOCALLY_UPDATED));
            Assert.assertEquals(syncId, accountFromDb.getLong(SyncTarget.SYNC_ID));
            JSONObject[] contactsFromDb = queryWithInClause(CONTACTS_SOUP, ACCOUNT_ID, new String[]{account.getString(Constants.ID)}, SmartStore.SOUP_ENTRY_ID);
            JSONObject[] contacts = mapAccountContacts.get(account);
            Assert.assertEquals("Wrong number of contacts in db", contacts.length, contactsFromDb.length);
            for (int j = 0; j < contactsFromDb.length; j++) {
                JSONObject contact = contacts[j];
                JSONObject contactFromDb = contactsFromDb[j];
                Assert.assertEquals(contact.getString(Constants.ID), contactFromDb.getString(Constants.ID));
                Assert.assertEquals(Constants.CONTACT, contactFromDb.getJSONObject(Constants.ATTRIBUTES).getString(TYPE));
                Assert.assertEquals(false, contactFromDb.getBoolean(SyncTarget.LOCAL));
                Assert.assertEquals(false, contactFromDb.getBoolean(SyncTarget.LOCALLY_CREATED));
                Assert.assertEquals(false, contactFromDb.getBoolean(SyncTarget.LOCALLY_DELETED));
                Assert.assertEquals(false, contactFromDb.getBoolean(SyncTarget.LOCALLY_UPDATED));
                Assert.assertEquals(syncId, contactFromDb.getLong(SyncTarget.SYNC_ID));
                Assert.assertEquals(accountFromDb.getString(Constants.ID), contactFromDb.getString(ACCOUNT_ID));
            }
        }
    }

    /**
     * Test getLatestModificationTimeStamp
     */
    @Test
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
        Assert.assertEquals(
                timeStamps[3],
                getAccountContactsSyncDownTarget("AccountTimeStamp1", "ContactTimeStamp1", null).getLatestModificationTimeStamp(records)
        );

        // Get max time stamps based on fields AccountTimeStamp1 / ContactTimeStamp2
        Assert.assertEquals(
                timeStamps[3],
                getAccountContactsSyncDownTarget("AccountTimeStamp1", "ContactTimeStamp2", null).getLatestModificationTimeStamp(records)
        );

        // Get max time stamps based on fields AccountTimeStamp2 / ContactTimeStamp1
        Assert.assertEquals(
                timeStamps[1],
                getAccountContactsSyncDownTarget("AccountTimeStamp2", "ContactTimeStamp1", null).getLatestModificationTimeStamp(records)
        );

        // Get max time stamps based on fields AccountTimeStamp2 / ContactTimeStamp2
        Assert.assertEquals(
                timeStamps[2],
                getAccountContactsSyncDownTarget("AccountTimeStamp2", "ContactTimeStamp2", null).getLatestModificationTimeStamp(records)
        );
    }

    /**
     * Test ParentChildrenSyncDownTarget's constructor that takes only a SOQL query
     * An exception is expected
     */
    @Test
    public void testConstructorWithQuery() {
        try {
            new ParentChildrenSyncDownTarget("SELECT Name FROM Account");
            Assert.fail("Exception should have been thrown");
        } catch (UnsupportedOperationException e) {
        }
    }

    /**
     * Sync down the test accounts and contacts, check smart store, check status during sync
     */
    @Test
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
            checkDb(accountIdContactIdToFields.get(accountId), CONTACTS_SOUP);
        }
    }

    /**
     * Sync down the test accounts that do not have children contacts, check smart store, check status during sync
     */
    @Test
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
    @Test
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
                checkDbStateFlags(Arrays.asList(accountId), false, true, false, ACCOUNTS_SOUP);
                checkDb(contactIdToFieldsUpdated, CONTACTS_SOUP);
                checkDbStateFlags(contactIdToFieldsUpdated.keySet(), false, true, false, CONTACTS_SOUP);
            } else if (accountId.equals(otherAccountId)) {
                checkDbStateFlags(Arrays.asList(accountId), false, false, false, ACCOUNTS_SOUP);
                checkDb(otherContactIdToFieldsUpdated, CONTACTS_SOUP);
                checkDbStateFlags(otherContactIdToFieldsUpdated.keySet(), false, true, false, CONTACTS_SOUP);
            } else {
                checkDbStateFlags(Arrays.asList(accountId), false, false, false, ACCOUNTS_SOUP);
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
    @Test
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
        Assert.assertTrue("Wrong time stamp", maxTimeStamp > 0);

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
        Assert.assertTrue("Wrong time stamp", syncManager.getSyncStatus(syncId).getMaxTimeStamp() > maxTimeStamp);
    }

    /**
     * Sync down the test accounts and contacts
     * Modify an account and some of its contacts and modify other contacts (without changing parent account)
     * Make sure only the modified account and its modified contacts are re-synced
     */
    @Test
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
        Assert.assertTrue("Wrong time stamp", maxTimeStamp > 0);

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
        Assert.assertTrue("Wrong time stamp", syncManager.getSyncStatus(syncId).getMaxTimeStamp() > maxTimeStamp);
    }

    /**
     * Sync down the test accounts and contacts
     * Delete account from server - run cleanResyncGhosts
     */
    @Test
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
        tryCleanResyncGhosts(syncId);

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
     * Tests clean ghosts when soup is populated through more than one sync down
     */
    @Test
    public void testCleanResyncGhostsForParentChildrenWithMultipleSyncs() throws Exception {
        final int numberAccounts = 6;
        final int numberContactsPerAccount = 3;

        // Creating up test accounts and contacts on server
        createAccountsAndContactsOnServer(numberAccounts, numberContactsPerAccount);

        final String[] accountIds = accountIdToFields.keySet().toArray(new String[0]);
        final String[] accountIdsFirstSubset = Arrays.copyOfRange(accountIds, 0, 3); // id0, id1, id2
        final String[] accountIdsSecondSubset = Arrays.copyOfRange(accountIds, 2, 6); //          id2, id3, id4, id5

        // Runs a first sync down (bringing down accounts id0, id1, id2 and their contacts)
        ParentChildrenSyncDownTarget firstTarget = getAccountContactsSyncDownTarget(
                String.format("%s IN %s", Constants.ID, makeInClause(accountIdsFirstSubset)));
        long firstSyncId = trySyncDown(SyncState.MergeMode.OVERWRITE, firstTarget, ACCOUNTS_SOUP, accountIdsFirstSubset.length, 1);
        checkDbExist(ACCOUNTS_SOUP, accountIdsFirstSubset, Constants.ID);
        checkDbSyncIdField(accountIdsFirstSubset, firstSyncId, ACCOUNTS_SOUP);

        // Runs a second sync down (bringing down accounts id2, id3, id4, id5 and their contacts)
        ParentChildrenSyncDownTarget secondTarget = getAccountContactsSyncDownTarget(
                String.format("%s IN %s", Constants.ID, makeInClause(accountIdsSecondSubset)));
        long secondSyncId = trySyncDown(SyncState.MergeMode.OVERWRITE, secondTarget, ACCOUNTS_SOUP, accountIdsSecondSubset.length, 1);
        checkDbExist(ACCOUNTS_SOUP, accountIdsSecondSubset, Constants.ID);
        checkDbSyncIdField(accountIdsSecondSubset, secondSyncId, ACCOUNTS_SOUP);

        // Deletes id0, id2, id5 on the server
        deleteRecordsOnServer(new HashSet<>(Arrays.asList(accountIds[0], accountIds[2], accountIds[5])), Constants.ACCOUNT);

        // Cleaning ghosts of first sync (should only remove id0 and its contacts)
        tryCleanResyncGhosts(firstSyncId);
        checkDbExist(ACCOUNTS_SOUP, new String[] { accountIds[1], accountIds[2], accountIds[3], accountIds[4], accountIds[5]}, Constants.ID);
        checkDbDeleted(ACCOUNTS_SOUP, new String[] { accountIds[0]}, Constants.ID);
        for (String accountId : accountIdContactIdToFields.keySet()) {
            if (accountId == accountIds[0]) {
                checkDbDeleted(CONTACTS_SOUP, accountIdContactIdToFields.get(accountId).keySet().toArray(new String[0]), Constants.ID);
            } else {
                checkDb(accountIdContactIdToFields.get(accountId), CONTACTS_SOUP);

            }
        }

        // Cleaning ghosts of second sync (should remove id2 and id5 and their contacts)
        tryCleanResyncGhosts(secondSyncId);
        checkDbExist(ACCOUNTS_SOUP, new String[] { accountIds[1], accountIds[3], accountIds[4]}, Constants.ID);
        checkDbDeleted(ACCOUNTS_SOUP, new String[] { accountIds[2], accountIds[5]}, Constants.ID);
        for (String accountId : accountIdContactIdToFields.keySet()) {
            if (accountId == accountIds[0] || accountId == accountIds[2] || accountId == accountIds[5]) {
                checkDbDeleted(CONTACTS_SOUP, accountIdContactIdToFields.get(accountId).keySet().toArray(new String[0]), Constants.ID);
            } else {
                checkDb(accountIdContactIdToFields.get(accountId), CONTACTS_SOUP);

            }
        }
    }


    /**
     * Create accounts and contacts locally, sync up with merge mode OVERWRITE, check smartstore and server afterwards
     */
    @Test
    public void testSyncUpWithLocallyCreatedRecords() throws Exception {
        trySyncUpWithLocallyCreatedRecords(SyncState.MergeMode.OVERWRITE);
    }

    /**
     * Create accounts and contacts locally, sync up with mege mode LEAVE_IF_CHANGED, check smartstore and server afterwards
     */
    @Test
    public void testSyncUpWithLocallyCreatedRecordsWithoutOverwrite() throws Exception {
        trySyncUpWithLocallyCreatedRecords(SyncState.MergeMode.LEAVE_IF_CHANGED);
    }

    /**
     * Create contacts on server, sync down
     * Create accounts locally, update contacts locally to be associated with them
     * Run sync up
     * Check smartstore and server afterwards
     */
    @Test
    public void testSyncUpWithLocallyCreatedParentRecords() throws Exception {
        // Create contacts on server
        final Map<String, String> contactIdToName = createRecordsOnServer(6, Constants.CONTACT);

        // Sync down remote contacts
        final SyncDownTarget contactSyncDownTarget = new SoqlSyncDownTarget("SELECT Id, LastName, LastModifiedDate FROM Contact WHERE Id IN " + makeInClause(contactIdToName.keySet()));
        trySyncDown(SyncState.MergeMode.OVERWRITE, contactSyncDownTarget, CONTACTS_SOUP, contactIdToName.size(), 1);

        // Create a few accounts locally
        String[] accountNames = new String[]{
                createRecordName(Constants.ACCOUNT),
                createRecordName(Constants.ACCOUNT),
        };
        JSONObject[] localAccounts = createAccountsLocally(accountNames);

        // Build account name to id map
        Map<String, String> accountNameToServerId = new HashMap<>();
        for (JSONObject localAccount : localAccounts) {
            accountNameToServerId.put(localAccount.getString(Constants.NAME), localAccount.getString(Constants.ID));
        }

        // Update contacts locally to use locally created accounts
        Map<String, String> contactIdToAccountName = new HashMap<>();
        Map<String, Map<String, Object>> idToFieldsLocallyUpdated = new HashMap<>();
        int i=0;
        for (String contactId : contactIdToName.keySet()) {
            Map<String, Object> fieldsLocallyUpdated = new HashMap<>();
            String accountName = accountNames[i % accountNames.length];
            fieldsLocallyUpdated.put(ACCOUNT_ID, accountNameToServerId.get(accountName));
            idToFieldsLocallyUpdated.put(contactId, fieldsLocallyUpdated);
            contactIdToAccountName.put(contactId, accountName);
            i++;
        }
        updateRecordsLocally(idToFieldsLocallyUpdated, CONTACTS_SOUP);

        // Sync up
        ParentChildrenSyncUpTarget target = getAccountContactsSyncUpTarget();
        trySyncUp(target, accountNames.length, SyncState.MergeMode.OVERWRITE);

        // Check that db doesn't show account entries as locally created anymore and that they use sfdc id
        Map<String, Map<String, Object>> accountIdToFieldsCreated = getIdToFieldsByName(ACCOUNTS_SOUP, new String[]{Constants.NAME, Constants.DESCRIPTION}, Constants.NAME, accountNames);
        checkDbStateFlags(accountIdToFieldsCreated.keySet(), false, false, false, ACCOUNTS_SOUP);

        // Updated account name to server id map
        for (String accountId : accountIdToFieldsCreated.keySet()) {
            accountNameToServerId.put((String) accountIdToFieldsCreated.get(accountId).get(Constants.NAME), accountId);
        }

        // Check accounts on server
        checkServer(accountIdToFieldsCreated, Constants.ACCOUNT);

        // Check that db doesn't show contact entries as locally updated anymore
        Map<String, Map<String, Object>> contactIdToFieldsUpdated= getIdToFieldsByName(CONTACTS_SOUP, new String[]{Constants.LAST_NAME, ACCOUNT_ID}, Constants.LAST_NAME, contactIdToName.values().toArray(new String[0]));
        checkDbStateFlags(contactIdToFieldsUpdated.keySet(), false, false, false, CONTACTS_SOUP);

        // Check that contact use server account id in accountId field
        for (String contactId : contactIdToFieldsUpdated.keySet()) {
            Assert.assertEquals("Wrong accountId", accountNameToServerId.get(contactIdToAccountName.get(contactId)), contactIdToFieldsUpdated.get(contactId).get(ACCOUNT_ID));
        }

        // Check contacts on server
        checkServer(contactIdToFieldsUpdated, Constants.CONTACT);

        // Cleanup
        deleteRecordsOnServer(accountIdToFieldsCreated.keySet(), Constants.ACCOUNT);
        deleteRecordsOnServer(contactIdToFieldsUpdated.keySet(), Constants.CONTACT);
    }

    /**
     * Create accounts on server, sync down
     * Create contacts locally, associates them with the accounts and run sync up
     * Check smartstore and server afterwards
     */
    @Test
    public void testSyncUpWithLocallyCreatedChildrenRecords() throws Exception {
        // Create accounts on server
        final Map<String, String> accountIdToName = createRecordsOnServer(2, Constants.ACCOUNT);
        String[] accountNames = accountIdToName.values().toArray(new String[0]);

        // Sync down remote accounts
        final SyncDownTarget accountSyncDownTarget = new SoqlSyncDownTarget("SELECT Id, Name, LastModifiedDate FROM Account WHERE Id IN " + makeInClause(accountIdToName.keySet()));
        trySyncDown(SyncState.MergeMode.OVERWRITE, accountSyncDownTarget, ACCOUNTS_SOUP, accountIdToName.size(), 1);

        // Create a few contacts locally associated with existing accounts
        Map<String, Map<String, Object>> accountIdToFieldsCreated = getIdToFieldsByName(ACCOUNTS_SOUP, new String[]{Constants.NAME}, Constants.NAME, accountNames);
        final Map<String, JSONObject[]> contactsForAccountsLocally = createContactsForAccountsLocally(3, accountIdToFieldsCreated.keySet().toArray(new String[0]));
        List<String> contactNamesList = new ArrayList<>();
        for (JSONObject[] contacts : contactsForAccountsLocally.values()) {
            for (JSONObject contact : contacts) {
                contactNamesList.add(contact.getString(Constants.LAST_NAME));
            }
        }
        String[] contactNames = contactNamesList.toArray(new String[0]);

        // Sync up
        ParentChildrenSyncUpTarget target = getAccountContactsSyncUpTarget();
        trySyncUp(target, accountNames.length, SyncState.MergeMode.OVERWRITE);

        // Check that db doesn't show contact entries as locally created anymore
        Map<String, Map<String, Object>> contactIdToFieldsCreated = getIdToFieldsByName(CONTACTS_SOUP, new String[]{Constants.LAST_NAME, ACCOUNT_ID}, Constants.LAST_NAME, contactNames);
        checkDbStateFlags(contactIdToFieldsCreated.keySet(), false, false, false, CONTACTS_SOUP);

        // Check contacts on server
        checkServer(contactIdToFieldsCreated, Constants.CONTACT);

        // Cleanup
        deleteRecordsOnServer(accountIdToFieldsCreated.keySet(), Constants.ACCOUNT);
        deleteRecordsOnServer(contactIdToFieldsCreated.keySet(), Constants.CONTACT);
    }


    /**
     * Create account on server, sync down
     * Remotely delete account
     * Create contacts locally, associates them with the account and run sync up
     * Check smartstore and server afterwards
     * The account should be recreated and the contacts should be associated to the new account id
     */
    @Test
    public void testSyncUpWithLocallyCreatedChildrenRemotelyDeletedParent() throws Exception {
        // Create account on server
        final Map<String, String> accountIdToName = createRecordsOnServer(1, Constants.ACCOUNT);
        String accountId = accountIdToName.keySet().toArray(new String[0])[0];
        String accountName = accountIdToName.values().toArray(new String[0])[0];

        // Sync down remote accounts
        final SyncDownTarget accountSyncDownTarget = new SoqlSyncDownTarget("SELECT Id, Name, LastModifiedDate FROM Account WHERE Id = '" + accountId + "'");
        trySyncDown(SyncState.MergeMode.OVERWRITE, accountSyncDownTarget, ACCOUNTS_SOUP, accountIdToName.size(), 1);

        // Create a few contacts locally associated with account
        final Map<String, JSONObject[]> contactsForAccountsLocally = createContactsForAccountsLocally(3, new String[]{accountId});
        List<String> contactNamesList = new ArrayList<>();
        for (JSONObject[] contacts : contactsForAccountsLocally.values()) {
            for (JSONObject contact : contacts) {
                contactNamesList.add(contact.getString(Constants.LAST_NAME));
            }
        }
        String[] contactNames = contactNamesList.toArray(new String[0]);

        // Delete account remotely
        deleteRecordsOnServer(new HashSet<>(Arrays.asList(accountId)), Constants.ACCOUNT);

        // Sync up
        ParentChildrenSyncUpTarget target = getAccountContactsSyncUpTarget();
        trySyncUp(target, 1, SyncState.MergeMode.OVERWRITE);

        // Make sure account got recreated
        Map<String, Object> accountFields = new HashMap<>();
        accountFields.put(Constants.NAME, accountName);
        String newAccountId = checkRecordRecreated(accountId, accountFields, Constants.NAME, ACCOUNTS_SOUP, Constants.ACCOUNT, null, null);

        // Check that db doesn't show contact entries as locally created anymore
        Map<String, Map<String, Object>> contactIdToFieldsCreated = getIdToFieldsByName(CONTACTS_SOUP, new String[]{Constants.LAST_NAME, ACCOUNT_ID}, Constants.LAST_NAME, contactNames);
        checkDbStateFlags(contactIdToFieldsCreated.keySet(), false, false, false, CONTACTS_SOUP);

        // Check contacts on server
        checkServer(contactIdToFieldsCreated, Constants.CONTACT);

        // Check that contact use new account id in accountId field


        // Check that contact use new account id in accountId field
        for (String contactId : contactIdToFieldsCreated.keySet()) {
            Assert.assertEquals("Wrong accountId", newAccountId, contactIdToFieldsCreated.get(contactId).get(ACCOUNT_ID));
        }

        // Cleanup
        deleteRecordsOnServer(new HashSet<String>(Arrays.asList(newAccountId)), Constants.ACCOUNT);
        deleteRecordsOnServer(contactIdToFieldsCreated.keySet(), Constants.CONTACT);
    }

    /**
     * Create accounts and contacts on server, sync down
     * Update some of the accounts and contacts - using bad names (too long) for some
     * Sync up
     * Check smartstore and server afterwards
     */
    @Test
    public void testSyncUpWithErrors() throws Exception {
        // Creating test accounts and contacts on server
        createAccountsAndContactsOnServer(3, 3);

        // Sync down
        ParentChildrenSyncDownTarget syncDownTarget = getAccountContactsSyncDownTarget(
                String.format("%s IN %s", Constants.ID, makeInClause(accountIdToFields.keySet())));
        trySyncDown(SyncState.MergeMode.OVERWRITE, syncDownTarget, ACCOUNTS_SOUP, 3, 1);

        // Picking accounts / contacts
        String[] accountIds = accountIdToFields.keySet().toArray(new String[0]);
        String account1Id = accountIds[0];
        String[] contactIdsOfAccount1 = accountIdContactIdToFields.get(account1Id).keySet().toArray(new String[0]);
        String contact11Id = contactIdsOfAccount1[0];
        String contact12Id = contactIdsOfAccount1[1];

        String account2Id = accountIds[1];
        String[] contactIdsOfAccount2 = accountIdContactIdToFields.get(account2Id).keySet().toArray(new String[0]);
        String contact21Id = contactIdsOfAccount2[0];
        String contact22Id = contactIdsOfAccount2[1];

        // Build long suffix
        StringBuffer buffer = new StringBuffer(255);
        for (int i = 0; i < 255; i++) buffer.append("x");
        String suffixTooLong = buffer.toString();

        // Updating with valid values
        Map<String, Object> updatedAccount1Fields = updateRecordLocally(ACCOUNTS_SOUP, account1Id, accountIdToFields.get(account1Id)).get(account1Id);
        Map<String, Object> updatedContact11Fields = updateRecordLocally(CONTACTS_SOUP, contact11Id, accountIdContactIdToFields.get(account1Id).get(contact11Id)).get(contact11Id);
        Map<String, Object> updatedContact21Fields = updateRecordLocally(CONTACTS_SOUP, contact21Id, accountIdContactIdToFields.get(account2Id).get(contact21Id)).get(contact21Id);

        // Updating with invalid values
        updateRecordLocally(ACCOUNTS_SOUP, account2Id, accountIdToFields.get(account2Id), suffixTooLong);
        updateRecordLocally(CONTACTS_SOUP, contact12Id, accountIdContactIdToFields.get(account1Id).get(contact12Id), suffixTooLong);
        updateRecordLocally(CONTACTS_SOUP, contact22Id, accountIdContactIdToFields.get(account2Id).get(contact22Id), suffixTooLong);

        // Sync up
        trySyncUp(getAccountContactsSyncUpTarget(), 2, SyncState.MergeMode.OVERWRITE);

        // Check valid records in db: should no longer be marked as dirty
        checkDbStateFlags(Arrays.asList(account1Id), false, false, false, ACCOUNTS_SOUP);
        checkDbStateFlags(Arrays.asList(contact11Id, contact21Id), false, false, false, CONTACTS_SOUP);

        // Check invalid records in db
        // Should still be marked as dirty
        checkDbStateFlags(Arrays.asList(account2Id), false, true, false, ACCOUNTS_SOUP);
        checkDbStateFlags(Arrays.asList(contact12Id, contact22Id), false, true, false, CONTACTS_SOUP);
        // Should have populated last error fields
        checkDbLastErrorField(new String[] { account2Id }, "Account Name: data value too large", ACCOUNTS_SOUP);
        checkDbLastErrorField(new String[] { contact12Id, contact22Id }, "Last Name: data value too large", CONTACTS_SOUP);

        // Check server
        Map<String, Map<String, Object>> accountIdToFieldsExpectedOnServer = new HashMap<>();
        for (String id : accountIds) {
            // Only update to account1 should have gone through
            if (id.equals(account1Id)) {
                accountIdToFieldsExpectedOnServer.put(id, updatedAccount1Fields);
            }
            else {
                accountIdToFieldsExpectedOnServer.put(id, accountIdToFields.get(id));
            }
        }
        checkServer(accountIdToFieldsExpectedOnServer, Constants.ACCOUNT);
        Map<String, Map<String, Object>> contactIdToFieldsExpectedOnServer = new HashMap<>();
        for (String id : accountIds) {
            Map<String, Map<String, Object>> contactIdToFields = accountIdContactIdToFields.get(id);
            for (String cid : contactIdToFields.keySet()) {
                // Only update to contact11 and contact21 should have gone through
                if (cid.equals(contact11Id)) {
                    contactIdToFieldsExpectedOnServer.put(cid, updatedContact11Fields);
                } else if (cid.equals(contact21Id)) {
                    contactIdToFieldsExpectedOnServer.put(cid, updatedContact21Fields);
                } else {
                    contactIdToFieldsExpectedOnServer.put(cid, contactIdToFields.get(cid));
                }
            }
        }
        checkServer(contactIdToFieldsExpectedOnServer, Constants.CONTACT);
    }
}