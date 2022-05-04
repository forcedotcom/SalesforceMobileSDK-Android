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
import com.salesforce.androidsdk.mobilesync.util.SyncOptions;
import com.salesforce.androidsdk.mobilesync.util.SyncState;
import com.salesforce.androidsdk.mobilesync.util.SyncState.MergeMode;
import com.salesforce.androidsdk.mobilesync.util.SyncUpdateCallbackQueue;
import com.salesforce.androidsdk.rest.RestRequest;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
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
 * Test class for RefreshSyncDownTarget.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class RefreshSyncDownTargetTest extends SyncManagerTestCase {

    protected static final int COUNT_TEST_ACCOUNTS = 10;
    public static final List<String> REFRESH_FIELDLIST = Arrays.asList(Constants.ID, Constants.NAME, Constants.DESCRIPTION, Constants.LAST_MODIFIED_DATE);

    protected Map<String, Map<String, Object>> idToFields;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        createAccountsSoup();
        idToFields = createRecordsOnServerReturnFields(COUNT_TEST_ACCOUNTS, Constants.ACCOUNT, null);
    }

    @After
    public void tearDown() throws Exception {
        if (idToFields != null) {
            deleteRecordsByIdOnServer(idToFields.keySet(), Constants.ACCOUNT);
        }
        dropAccountsSoup();
        super.tearDown();
    }

    /**
     * Tests refresh-sync-down
     * @throws Exception
     */
    @Test
    public void testRefreshSyncDown() throws Exception {
        // Setup has created records on the server
        // Adding soup elements with just ids to soup
        for (String id : idToFields.keySet()) {
            JSONObject soupElement = new JSONObject();
            soupElement.put(Constants.ID, id);
            smartStore.create(ACCOUNTS_SOUP, soupElement);
        }
        // Running a refresh-sync-down for soup
        final SyncDownTarget target = new RefreshSyncDownTarget(REFRESH_FIELDLIST, Constants.ACCOUNT, ACCOUNTS_SOUP);
        trySyncDown(MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, idToFields.size(), 1, null);
        // Make sure the soup has the records with id and names
        checkDb(idToFields, ACCOUNTS_SOUP);
    }

    /**
     * Tests refresh-sync-down when they are more records in the table than can be enumerated in one
     * soql call to the server
     * @throws Exception
     */
    @Test
    public void testRefreshSyncDownWithMultipleRoundTrips() throws Exception {
        // Setup has created records on the server
        // Adding soup elements with just ids to soup
        for (String id : idToFields.keySet()) {
            JSONObject soupElement = new JSONObject();
            soupElement.put(Constants.ID, id);
            smartStore.create(ACCOUNTS_SOUP, soupElement);
        }
        // Running a refresh-sync-down for soup with two ids per soql query (to force multiple round trips)
        final RefreshSyncDownTarget target = new RefreshSyncDownTarget(REFRESH_FIELDLIST, Constants.ACCOUNT, ACCOUNTS_SOUP, 2);
        trySyncDown(MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, idToFields.size(), idToFields.size() / 2, null);

        // Make sure the soup has the records with id and names
        checkDb(idToFields, ACCOUNTS_SOUP);
    }

    /**
     * Tests resync for a refresh-sync-down when they are more records in the table than can be enumerated
     * in one soql call to the server
     * @throws Exception
     */
    @Test
    public void testRefreshReSyncWithMultipleRoundTrips() throws Exception {
        // Setup has created records on the server
        // Adding soup elements with just ids to soup
        for (String id : idToFields.keySet()) {
            JSONObject soupElement = new JSONObject();
            soupElement.put(Constants.ID, id);
            smartStore.create(ACCOUNTS_SOUP, soupElement);
        }
        // Running a refresh-sync-down for soup
        final RefreshSyncDownTarget target = new RefreshSyncDownTarget(REFRESH_FIELDLIST, Constants.ACCOUNT, ACCOUNTS_SOUP, 1);
        long syncId = trySyncDown(MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, idToFields.size(), 10, null);

        // Check sync time stamp
        SyncState sync = syncManager.getSyncStatus(syncId);
        SyncOptions options = sync.getOptions();
        long maxTimeStamp = sync.getMaxTimeStamp();
        Assert.assertTrue("Wrong time stamp", maxTimeStamp > 0);

        // Make sure the soup has the records with id and names
        checkDb(idToFields, ACCOUNTS_SOUP);

        // Make some remote change
        Map<String, Map<String, Object>> idToFieldsUpdated = makeRemoteChanges(idToFields, Constants.ACCOUNT);

        // Call reSync
        SyncUpdateCallbackQueue queue = new SyncUpdateCallbackQueue();
        syncManager.reSync(syncId, queue);

        // Check status updates
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0, -1);
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0, idToFields.size()); // totalSize is off for resync of sync-down-target if not all records got updated
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 10, idToFields.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 10, idToFields.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 20, idToFields.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 20, idToFields.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 20, idToFields.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 20, idToFields.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 20, idToFields.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 20, idToFields.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 20, idToFields.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 20, idToFields.size());
        checkStatus(queue.getNextSyncUpdate(), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.DONE, 100, idToFields.size());

        // Check db
        checkDb(idToFieldsUpdated, ACCOUNTS_SOUP);

        // Check sync time stamp
        Assert.assertTrue("Wrong time stamp", syncManager.getSyncStatus(syncId).getMaxTimeStamp() > maxTimeStamp);
    }

    /**
     * Tests if ghost records are cleaned locally for a refresh target.
     */
    @Test
    public void testCleanResyncGhostsForRefreshTarget() throws Exception {
        // Setup has created records on the server
        // Adding soup elements with just ids to soup
        for (String id : idToFields.keySet()) {
            JSONObject soupElement = new JSONObject();
            soupElement.put(Constants.ID, id);
            smartStore.create(ACCOUNTS_SOUP, soupElement);
        }
        // Running a refresh-sync-down for soup
        final RefreshSyncDownTarget target = new RefreshSyncDownTarget(REFRESH_FIELDLIST, Constants.ACCOUNT, ACCOUNTS_SOUP);
        long syncId = trySyncDown(MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, idToFields.size(), 1, null);

        // Make sure the soup has the records with id and names
        checkDb(idToFields, ACCOUNTS_SOUP);

        // Deletes 1 account on the server and verifies the ghost record is cleared from the soup.
        String[] ids = idToFields.keySet().toArray(new String[0]);
        String idDeleted = ids[0];
        deleteRecordsByIdOnServer(new HashSet<String>(Arrays.asList(idDeleted)), Constants.ACCOUNT);
        tryCleanResyncGhosts(syncId);

        // Map of id to names expected to be found in db
        Map<String, Map<String, Object>> idToFieldsLeft = new HashMap<>(idToFields);
        idToFieldsLeft.remove(idDeleted);

        // Make sure the soup doesn't contain the record deleted on the server anymore
        checkDb(idToFieldsLeft, ACCOUNTS_SOUP);
        checkDbDeleted(ACCOUNTS_SOUP, new String[] {idDeleted}, Constants.ID);
    }
}