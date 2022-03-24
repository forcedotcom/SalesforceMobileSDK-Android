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
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
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
    }

    @After
    public void tearDown() throws Exception {
        dropAccountsSoup();
        super.tearDown();
    }

    // Create accounts on server
    // Run startFetch method of BriefcaseSyncDownTarget that is only interested in accounts
    // Make sure we get the created accounts back
    //
    // NB: this test will NOT fail if the server has accounts named Briefcase_ already
    //     AS LONG AS your emulator clock is correct
    @Test
    public void testStartFetchOneObjectType() throws Exception {
        long now = (new Date()).getTime();
        Thread.sleep(1000); // time stamp precision in seconds
        final int numberAccounts = 12;
        final Map<String, String> accounts = createRecordsOnServer(numberAccounts, Constants.ACCOUNT);
        Assert.assertEquals("Wrong number of accounts created", numberAccounts, accounts.size());
        final String[] accountIds = accounts.keySet().toArray(new String[0]);

        // Builds briefcase sync down target to fetch the accounts and performs sync.
        BriefcaseSyncDownTarget target = new BriefcaseSyncDownTarget(
            Arrays.asList(new BriefcaseObjectInfo(
                ACCOUNTS_SOUP,
                Constants.ACCOUNT,
                Arrays.asList(Constants.NAME, Constants.DESCRIPTION)
            ))
        );

        JSONArray records = target.startFetch(syncManager, now);
        Assert.assertEquals(numberAccounts, records.length());
        for (int i=0; i<records.length(); i++) {
            JSONObject record = records.getJSONObject(i);
            String id = record.getString(Constants.ID);
            String name = record.getString(Constants.NAME);
            Assert.assertEquals(accounts.get(id), name);
        }

        // Deletes accounts on the server.
        deleteRecordsOnServer(Arrays.asList(accountIds), Constants.ACCOUNT);
    }

    // Create accounts on server
    // Run a sync with a BriefcaseSyncDownTarget that is only interested in accounts
    // Make sure we get the created accounts in the database
    //
    // NB: this test will fail if the server has accounts named Briefcase_ already
    @Test
    public void testBriefcaseSyncDownFetchingOneObjectType() throws Exception {
        final int numberAccounts = 12;
        final Map<String, String> accounts = createRecordsOnServer(numberAccounts, Constants.ACCOUNT);
        Assert.assertEquals("Wrong number of accounts created", numberAccounts, accounts.size());
        final String[] accountIds = accounts.keySet().toArray(new String[0]);

        // Builds briefcase sync down target to fetch the accounts and performs sync.
        BriefcaseSyncDownTarget target = new BriefcaseSyncDownTarget(
            Arrays.asList(new BriefcaseObjectInfo(
                ACCOUNTS_SOUP,
                Constants.ACCOUNT,
                Arrays.asList(Constants.NAME, Constants.DESCRIPTION)
            ))
        );
        trySyncDown(MergeMode.LEAVE_IF_CHANGED, target, ACCOUNTS_SOUP, accounts.size(), 1, null);
        checkDbExist(ACCOUNTS_SOUP, accountIds, Constants.ID);


        // Deletes accounts on the server.
        deleteRecordsOnServer(Arrays.asList(accountIds), Constants.ACCOUNT);
    }

    protected String createRecordName(String objectType) {
        return String.format(Locale.US, "BriefcaseTest_%s_%d", objectType, System.nanoTime());
    }

}
