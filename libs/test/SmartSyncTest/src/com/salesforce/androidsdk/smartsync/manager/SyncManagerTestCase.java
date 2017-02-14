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

import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartsync.target.SyncTarget;
import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.androidsdk.smartsync.util.SyncState;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Formatter;
import java.util.Locale;
import java.util.Map;

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
     * @return local id of the form local_<random number left-padded to be 8 digits long>
     */
    @SuppressWarnings("resource")
    protected String createLocalId() {
        StringBuilder sb = new StringBuilder();
        Formatter formatter = new Formatter(sb, Locale.US);
        formatter.format(LOCAL_ID_PREFIX + "%08d", (int) (Math.random()*10000000));
        String name = sb.toString();
        return name;
    }

    /**
     * Create accounts locally
     * @param names
     * @throws JSONException
     * @return created accounts records
     */
    protected JSONObject[] createAccountsLocally(String[] names) throws JSONException {
        JSONObject[] createdAccounts = new JSONObject[names.length];
        JSONObject attributes = new JSONObject();
        attributes.put(TYPE, Constants.ACCOUNT);

        for (int i=0; i<names.length; i++) {
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
}
