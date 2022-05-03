/*
 * Copyright (c) 2022-present, salesforce.com, inc.
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

package com.salesforce.androidsdk.mobilesync.target;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import com.salesforce.androidsdk.mobilesync.manager.SyncManagerTestCase;
import com.salesforce.androidsdk.mobilesync.util.BriefcaseObjectInfo;
import com.salesforce.androidsdk.mobilesync.util.Constants;
import com.salesforce.androidsdk.mobilesync.util.SyncState.MergeMode;
import com.salesforce.androidsdk.rest.RestRequest;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test class for BriefcaseSyncDownTarget.
 *
 * NB: They will only pass if you have a briefcase setup to return
 * - accounts owned by current user with name starting with BriefcaseTest_
 * - contacts owned by current user with last name starting with BriefcaseTest_
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class BriefcaseSyncDownTargetTest extends SyncManagerTestCase {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        createAccountsSoup();
        createContactsSoup();
    }

    @After
    public void tearDown() throws Exception {
        dropAccountsSoup();
        dropContactsSoup();
        cleanRecordsOnServer();
        super.tearDown();
    }

    private void cleanRecordsOnServer() throws Exception {
        deleteRecordsByCriteriaFromServer(Constants.ACCOUNT,
            " Name like 'BriefcaseTest_%' AND CreatedById = '" + restClient.getClientInfo().userId + "'");
        deleteRecordsByCriteriaFromServer(Constants.CONTACT,
            " LastName like 'BriefcaseTest_%' AND CreatedById = '" + restClient.getClientInfo().userId + "'");
    }

    // Create accounts on server
    // Run startFetch method of BriefcaseSyncDownTarget that is only interested in accounts
    // Make sure we get the created accounts back
    @Test
    public void testStartFetchNoMaxTimeStamp() throws Exception {
        final int numberAccounts = 12;
        final Map<String, String> accounts = createRecordsOnServer(numberAccounts, Constants.ACCOUNT);
        Assert.assertEquals("Wrong number of accounts created", numberAccounts, accounts.size());

        // Builds briefcase sync down target to fetch the accounts and performs sync.
        BriefcaseSyncDownTarget target = new BriefcaseSyncDownTarget(
            Arrays.asList(new BriefcaseObjectInfo(
                ACCOUNTS_SOUP,
                Constants.ACCOUNT,
                Arrays.asList(Constants.NAME, Constants.DESCRIPTION)
            ))
        );

        JSONArray records = target.startFetch(syncManager, 0);
        Assert.assertEquals(numberAccounts, records.length());
        for (int i=0; i<records.length(); i++) {
            JSONObject record = records.getJSONObject(i);
            String id = record.getString(Constants.ID);
            String name = record.getString(Constants.NAME);
            Assert.assertEquals(accounts.get(id), name);
        }
    }

    // Create accounts on server
    // Run startFetch method of BriefcaseSyncDownTarget that is only interested in accounts
    // Query server to figure out last modified date
    // Create more accounts on server
    // Run startFetch method of BriefcaseSyncDownTarget using maxTimeStamp to only get second group of accounts
    // Make sure we get the second group of accounts only
    @Test
    public void testStartFetchWithMaxTimeStamp() throws Exception {
        final int numberAccounts = 6;

        // Creating some accounts
        Map<String, String> oldAccounts = createRecordsOnServer(numberAccounts, Constants.ACCOUNT);
        Assert.assertEquals("Wrong number of accounts created", numberAccounts, oldAccounts.size());

        // Figure out last modified date
        RestRequest request = RestRequest.getRequestForQuery(apiVersion, "SELECT Max(SystemModStamp) FROM Account WHERE Name like 'BriefcaseTest_%'");
        JSONObject responseJson = restClient.sendSync(request).asJSONObject();
        long maxTimeStamp = Constants.TIMESTAMP_FORMAT.parse(responseJson.getJSONArray(Constants.RECORDS).getJSONObject(0).getString("expr0")).getTime();

        // Waiting a bit
        Thread.sleep(1000); // time stamp precision in seconds

        // Creating more accounts
        Map<String, String> newAccounts = createRecordsOnServer(numberAccounts, Constants.ACCOUNT);
        Assert.assertEquals("Wrong number of accounts created", numberAccounts, newAccounts.size());

        // Make sure old and new accounts exist on server
        List<String> accountIds = getIdsOnServer(Constants.ACCOUNT, " Name like 'BriefcaseTest_%' AND CreatedById = '" + restClient.getClientInfo().userId + "'");
        Assert.assertEquals(numberAccounts*2, accountIds.size());
        Assert.assertTrue(accountIds.containsAll(oldAccounts.keySet()));
        Assert.assertTrue(accountIds.containsAll(newAccounts.keySet()));

        // Builds briefcase sync down target to fetch the accounts and performs sync.
        BriefcaseSyncDownTarget target = new BriefcaseSyncDownTarget(
            Arrays.asList(new BriefcaseObjectInfo(
                ACCOUNTS_SOUP,
                Constants.ACCOUNT,
                Arrays.asList(Constants.NAME, Constants.DESCRIPTION)
            ))
        );

        JSONArray records = target.startFetch(syncManager, maxTimeStamp);
        Assert.assertEquals(numberAccounts, records.length());
        for (int i=0; i<records.length(); i++) {
            JSONObject record = records.getJSONObject(i);
            String id = record.getString(Constants.ID);
            String name = record.getString(Constants.NAME);
            Assert.assertEquals(newAccounts.get(id), name);
        }
    }

    // Create accounts on server
    // Run a sync with a BriefcaseSyncDownTarget that is only interested in accounts
    // Make sure we get the created accounts in the database
    @Test
    public void testSyncDownFetchingOneObjectType() throws Exception {
        final int numberAccounts = 12;
        final Map<String, String> accounts = createRecordsOnServer(numberAccounts, Constants.ACCOUNT);
        Assert.assertEquals("Wrong number of accounts created", numberAccounts, accounts.size());
        final String[] accountIds = accounts.keySet().toArray(new String[0]);

        // Builds briefcase sync down target to fetch the accounts
        BriefcaseSyncDownTarget target = new BriefcaseSyncDownTarget(
            Arrays.asList(new BriefcaseObjectInfo(
                ACCOUNTS_SOUP,
                Constants.ACCOUNT,
                Arrays.asList(Constants.NAME, Constants.DESCRIPTION)
            ))
        );

        // Run sync
        trySyncDown(MergeMode.LEAVE_IF_CHANGED, target, ACCOUNTS_SOUP, accounts.size(), 1, null);

        // Check database
        checkDbExist(ACCOUNTS_SOUP, accountIds, Constants.ID);
    }

    // Create accounts on server
    // Run a sync with a BriefcaseSyncDownTarget with countIdsPerSoql of 2
    // Make sure we get the created accounts in the database
    @Test
    public void testSyncDownFetchingWithMultipleSOQLCalls() throws Exception {
        final int numberAccounts = 11; // using a number that is not a multiple of countIdsPerSoql to make sure the last slice is correctly fetched
        final Map<String, String> accounts = createRecordsOnServer(numberAccounts, Constants.ACCOUNT);
        Assert.assertEquals("Wrong number of accounts created", numberAccounts, accounts.size());
        final String[] accountIds = accounts.keySet().toArray(new String[0]);

        // Builds briefcase sync down target to fetch the accounts with SOQL queries using 2 ids at a time
        BriefcaseSyncDownTarget target = new BriefcaseSyncDownTarget(
            Arrays.asList(new BriefcaseObjectInfo(
                ACCOUNTS_SOUP,
                Constants.ACCOUNT,
                Arrays.asList(Constants.NAME, Constants.DESCRIPTION)
            )),
            2
        );

        // Run sync
        trySyncDown(MergeMode.LEAVE_IF_CHANGED, target, ACCOUNTS_SOUP, accounts.size(), 1, null);

        // Check database
        checkDbExist(ACCOUNTS_SOUP, accountIds, Constants.ID);
    }

    // Create accounts on server
    // Run a sync with a BriefcaseSyncDownTarget that is only interested in accounts
    // Make sure we get the created accounts in the database
    // Delete some accounts from server
    // Create some accounts locally
    // Run a cleanGhosts
    // Make sure the remotely deleted accounts are gone from the database
    // but other synced accounts and locally created accounts are still there
    //
    @Test
    public void testCleanGhostsOneObjectType() throws Exception {
        final int numberAccounts = 4;
        final Map<String, String> accounts = createRecordsOnServer(numberAccounts, Constants.ACCOUNT);
        Assert.assertEquals("Wrong number of accounts created", numberAccounts, accounts.size());
        final String[] accountIds = accounts.keySet().toArray(new String[0]);

        // Builds briefcase sync down target to fetch the accounts
        BriefcaseSyncDownTarget target = new BriefcaseSyncDownTarget(
            Arrays.asList(new BriefcaseObjectInfo(
                ACCOUNTS_SOUP,
                Constants.ACCOUNT,
                Arrays.asList(Constants.NAME, Constants.DESCRIPTION)
            ))
        );

        // Run sync
        long syncId = trySyncDown(MergeMode.LEAVE_IF_CHANGED, target, ACCOUNTS_SOUP, accounts.size(), 1, null);

        // Check database
        checkDbExist(ACCOUNTS_SOUP, accountIds, Constants.ID);

        // Deleting some accounts
        deleteRecordsByIdOnServer(Arrays.asList(accountIds[0], accountIds[1]), Constants.ACCOUNT);

        // Create some accounts locally
        JSONObject[] localAccounts = createAccountsLocally(new String[]{"local-1", "local-2"});

        // Clean ghosts
        tryCleanResyncGhosts(syncId);

        // Check database
        // Ghosts should be gone
        checkDbDeleted(ACCOUNTS_SOUP, new String[] {  accountIds[0], accountIds[1] }, Constants.ID);
        // Other synced records should still be there
        checkDbExist(ACCOUNTS_SOUP, new String[] { accountIds[2], accountIds[3] }, Constants.ID);
        // Locally created records should still be there
        checkDbExist(ACCOUNTS_SOUP, new String[] {localAccounts[0].getString(Constants.ID), localAccounts[1].getString(Constants.ID)}, Constants.ID);
    }

    // Create accounts and contacts on server
    // Run a sync with a BriefcaseSyncDownTarget that is interested in accounts and contacts
    // Make sure we get the created accounts and contacts in the database
    @Test
    public void testSyncDownFetchingTwoObjectTypes() throws Exception {
        final int numberAccounts = 12;
        final Map<String, String> accounts = createRecordsOnServer(numberAccounts, Constants.ACCOUNT);
        Assert.assertEquals("Wrong number of accounts created", numberAccounts, accounts.size());
        final String[] accountIds = accounts.keySet().toArray(new String[0]);

        final int numberContacts = 12;
        final Map<String, String> contacts = createRecordsOnServer(numberAccounts, Constants.CONTACT);
        Assert.assertEquals("Wrong number of contacts created", numberContacts, contacts.size());
        final String[] contactIds = contacts.keySet().toArray(new String[0]);

        // Builds briefcase sync down target to fetch the accounts and contacts
        BriefcaseSyncDownTarget target = new BriefcaseSyncDownTarget(
            Arrays.asList(
                new BriefcaseObjectInfo(
                    ACCOUNTS_SOUP,
                    Constants.ACCOUNT,
                    Arrays.asList(Constants.NAME, Constants.DESCRIPTION)),
                new BriefcaseObjectInfo(
                    CONTACTS_SOUP,
                    Constants.CONTACT,
                    Arrays.asList(Constants.LAST_NAME))
            )
        );

        // Run sync
        trySyncDown(MergeMode.LEAVE_IF_CHANGED, target, ACCOUNTS_SOUP, accounts.size() + contacts.size(), 1, null);

        // Check database
        checkDbExist(ACCOUNTS_SOUP, accountIds, Constants.ID);
        checkDbExist(CONTACTS_SOUP, contactIds, Constants.ID);
    }

    // Create accounts and contacts on server
    // Run a sync with a BriefcaseSyncDownTarget that is interested in accounts and conotacts
    // Make sure we get the created accounts and contacts in the database
    // Delete some accounts and contacts from server
    // Create some accounts and contacts locally
    // Run a cleanGhosts
    // Make sure the remotely deleted accounts and contacts are gone from the database
    // but other synced and locally created accounts and contacts are still there
    //
    @Test
    public void testCleanGhostsTwoObjectTypes() throws Exception {
        final int numberRecords = 4;
        final Map<String, String> accounts = createRecordsOnServer(numberRecords, Constants.ACCOUNT);
        Assert.assertEquals("Wrong number of accounts created", numberRecords, accounts.size());
        final String[] accountIds = accounts.keySet().toArray(new String[0]);

        final Map<String, String> contacts = createRecordsOnServer(numberRecords, Constants.CONTACT);
        Assert.assertEquals("Wrong number of contacts created", numberRecords, contacts.size());
        final String[] contactIds = contacts.keySet().toArray(new String[0]);

        // Builds briefcase sync down target to fetch the accounts and contacts
        BriefcaseSyncDownTarget target = new BriefcaseSyncDownTarget(
            Arrays.asList(
                new BriefcaseObjectInfo(
                    ACCOUNTS_SOUP,
                    Constants.ACCOUNT,
                    Arrays.asList(Constants.NAME, Constants.DESCRIPTION)),
                new BriefcaseObjectInfo(
                    CONTACTS_SOUP,
                    Constants.CONTACT,
                    Arrays.asList(Constants.LAST_NAME))
            )
        );

        // Run sync
        long syncId = trySyncDown(MergeMode.LEAVE_IF_CHANGED, target, ACCOUNTS_SOUP, accounts.size() + contacts.size(), 1, null);

        // Check database
        checkDbExist(ACCOUNTS_SOUP, accountIds, Constants.ID);
        checkDbExist(CONTACTS_SOUP, contactIds, Constants.ID);

        // Deleting some accounts
        deleteRecordsByIdOnServer(Arrays.asList(accountIds[0], accountIds[1]), Constants.ACCOUNT);
        deleteRecordsByIdOnServer(Arrays.asList(contactIds[2], contactIds[3]), Constants.CONTACT);

        // Create some accounts and contacts locally
        JSONObject[] localAccounts = createAccountsLocally(new String[]{"local-1", "local-2"});
        String localAccountId = localAccounts[0].getString(Constants.ID);
        JSONObject[] localContacts = createContactsForAccountsLocally(2,
            localAccountId).get(localAccountId);

        // Clean ghosts
        tryCleanResyncGhosts(syncId);

        // Check database
        // Ghosts should be gone
        checkDbDeleted(ACCOUNTS_SOUP, new String[] {  accountIds[0], accountIds[1] }, Constants.ID);
        checkDbDeleted(CONTACTS_SOUP, new String[] {  contactIds[2], accountIds[3] }, Constants.ID);
        // Other synced records should still be there
        checkDbExist(ACCOUNTS_SOUP, new String[] { accountIds[2], accountIds[3] }, Constants.ID);
        checkDbExist(CONTACTS_SOUP, new String[] { contactIds[0], contactIds[1] }, Constants.ID);
        // Locally created records should still be there
        checkDbExist(ACCOUNTS_SOUP, new String[] {localAccounts[0].getString(Constants.ID), localAccounts[1].getString(Constants.ID)}, Constants.ID);
        checkDbExist(CONTACTS_SOUP, new String[] {localContacts[0].getString(Constants.ID), localContacts[1].getString(Constants.ID)}, Constants.ID);
    }

    protected String createRecordName(String objectType) {
        return String.format(Locale.US, "BriefcaseTest_%s_%d", objectType, System.nanoTime());
    }

}
