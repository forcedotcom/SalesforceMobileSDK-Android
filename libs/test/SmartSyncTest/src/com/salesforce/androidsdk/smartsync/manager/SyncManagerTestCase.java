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

}
