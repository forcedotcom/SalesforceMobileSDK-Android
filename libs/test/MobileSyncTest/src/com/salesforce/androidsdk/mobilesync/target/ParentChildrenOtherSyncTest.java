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

package com.salesforce.androidsdk.mobilesync.target;

import androidx.test.filters.LargeTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

/**
 * Test class for ParentChildrenSyncDownTarget and ParentChildrenSyncUpTarget.
 */
@RunWith(Parameterized.class)
@LargeTest
public class ParentChildrenOtherSyncTest extends ParentChildrenSyncTestCase {

    @Parameterized.Parameter(0) public String testName;
    @Parameterized.Parameter(1) public int numberAccounts;
    @Parameterized.Parameter(2) public int numberContactsPerAccount;
    @Parameterized.Parameter(3) public Change localChangeForAccount;
    @Parameterized.Parameter(4) public Change remoteChangeForAccount;
    @Parameterized.Parameter(5) public Change localChangeForContact;
    @Parameterized.Parameter(6) public Change remoteChangeForContact;

    @Parameterized.Parameters(name = "{0}")
        public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"SyncUpLocallyUpdatedChild", 2, 2, Change.NONE, Change.NONE, Change.UPDATE, Change.NONE},
                {"SyncUpLocallyUpdatedChildRemotelyUpdatedChild", 2, 2, Change.NONE, Change.NONE, Change.UPDATE, Change.UPDATE},
                {"SyncUpLocallyUpdatedChildRemotelyDeletedChild", 2, 2, Change.NONE, Change.NONE, Change.UPDATE, Change.DELETE},
                {"SyncUpLocallyDeletedChild", 2, 2, Change.NONE, Change.NONE, Change.DELETE, Change.NONE},
                {"SyncUpLocallyDeletedChildRemotelyUpdatedChild", 2, 2, Change.NONE, Change.NONE, Change.DELETE, Change.UPDATE},
                {"SyncUpLocallyDeletedChildRemotelyDeletedChild", 2, 2, Change.NONE, Change.NONE, Change.DELETE, Change.DELETE},
                {"SyncUpLocallyUpdatedParent", 2, 2, Change.UPDATE, Change.NONE, Change.NONE, Change.NONE},
                {"SyncUpLocallyUpdatedParentRemotelyUpdatedParent", 2, 2, Change.UPDATE, Change.UPDATE, Change.NONE, Change.NONE},
                {"SyncUpLocallyUpdatedParentRemotelyDeletedParent", 2, 2, Change.UPDATE, Change.DELETE, Change.NONE, Change.NONE},
                {"SyncUpLocallyUpdatedParentUpdatedChild", 2, 2, Change.UPDATE, Change.NONE, Change.UPDATE, Change.NONE},
                {"SyncUpLocallyUpdatedParentUpdatedChildRemotelyUpdatedChild", 2, 2, Change.UPDATE, Change.NONE, Change.UPDATE, Change.UPDATE},
                {"SyncUpLocallyUpdatedParentUpdatedChildRemotelyDeletedChild", 2, 2, Change.UPDATE, Change.NONE, Change.UPDATE, Change.DELETE},
                {"SyncUpLocallyUpdatedParentUpdatedChildRemotelyUpdatedParent", 2, 2, Change.UPDATE, Change.UPDATE, Change.UPDATE, Change.NONE},
                {"SyncUpLocallyUpdatedParentUpdatedChildRemotelyUpdatedParentUpdatedChild", 2, 2, Change.UPDATE, Change.UPDATE, Change.UPDATE, Change.UPDATE},
                {"SyncUpLocallyUpdatedParentUpdatedChildRemotelyUpdatedParentDeletedChild", 2, 2, Change.UPDATE, Change.UPDATE, Change.UPDATE, Change.DELETE},
                {"SyncUpLocallyUpdatedParentUpdatedChildRemotelyDeletedParent", 2, 2, Change.UPDATE, Change.DELETE, Change.UPDATE, Change.NONE},
                {"SyncUpLocallyUpdatedParentDeletedChild", 2, 2, Change.UPDATE, Change.NONE, Change.DELETE, Change.NONE},
                {"SyncUpLocallyUpdatedParentDeletedChildRemotelyUpdatedChild", 2, 2, Change.UPDATE, Change.NONE, Change.DELETE, Change.UPDATE},
                {"SyncUpLocallyUpdatedParentDeletedChildRemotelyDeletedChild", 2, 2, Change.UPDATE, Change.NONE, Change.DELETE, Change.DELETE},
                {"SyncUpLocallyUpdatedParentDeletedChildRemotelyUpdatedParent", 2, 2, Change.UPDATE, Change.UPDATE, Change.DELETE, Change.NONE},
                {"SyncUpLocallyUpdatedParentDeletedChildRemotelyUpdatedParentUpdatedChild", 2, 2, Change.UPDATE, Change.UPDATE, Change.DELETE, Change.UPDATE},
                {"SyncUpLocallyUpdatedParentDeletedChildRemotelyUpdatedParentDeletedChild", 2, 2, Change.UPDATE, Change.UPDATE, Change.DELETE, Change.DELETE},
                {"SyncUpLocallyUpdatedParentDeletedChildRemotelyDeletedParent", 2, 2, Change.UPDATE, Change.DELETE, Change.DELETE, Change.NONE},
                {"SyncUpLocallyDeletedParent", 2, 2, Change.DELETE, Change.NONE, Change.NONE, Change.NONE},
                {"SyncUpLocallyDeletedParentRemotelyUpdatedParent", 2, 2, Change.DELETE, Change.UPDATE, Change.NONE, Change.NONE},
                {"SyncUpLocallyDeletedParentRemotelyDeletedParent", 2, 2, Change.DELETE, Change.DELETE, Change.NONE, Change.NONE},
                {"SyncUpLocallyDeletedParentUpdatedChild", 2, 2, Change.DELETE, Change.NONE, Change.UPDATE, Change.NONE},
                {"SyncUpLocallyDeletedParentUpdatedChildRemotelyUpdatedParent", 2, 2, Change.DELETE, Change.UPDATE, Change.UPDATE, Change.NONE},
                {"SyncUpLocallyDeletedParentUpdatedChildRemotelyDeletedParent", 2, 2, Change.DELETE, Change.DELETE, Change.UPDATE, Change.NONE},
                {"SyncUpLocallyDeletedParentDeletedChild", 2, 2, Change.DELETE, Change.NONE, Change.DELETE, Change.NONE},
                {"SyncUpLocallyDeletedParentDeletedChildRemotelyUpdatedParent", 2, 2, Change.DELETE, Change.UPDATE, Change.DELETE, Change.NONE},
                {"SyncUpLocallyDeletedParentDeletedChildRemotelyDeletedParent", 2, 2, Change.DELETE, Change.DELETE, Change.DELETE, Change.NONE},
                {"SyncUpLocallyUpdatedParentNoChildren", 2, 0, Change.UPDATE, Change.NONE, Change.NONE, Change.NONE},
                {"SyncUpLocallyUpdatedParentRemotelyUpdatedParentNoChildren", 2, 0, Change.UPDATE, Change.UPDATE, Change.NONE, Change.NONE},
                {"SyncUpLocallyUpdatedParentRemotelyDeletedParentNoChildren", 2, 0, Change.UPDATE, Change.DELETE, Change.NONE, Change.NONE},
                {"SyncUpLocallyDeletedParentNoChildren", 2, 0, Change.DELETE, Change.NONE, Change.NONE, Change.NONE},
                {"SyncUpLocallyDeletedParentRemotelyUpdatedParentNoChildren", 2, 0, Change.DELETE, Change.UPDATE, Change.NONE, Change.NONE},
                {"SyncUpLocallyDeletedParentRemotelyDeletedParentNoChildren", 2, 0, Change.DELETE, Change.DELETE, Change.NONE, Change.NONE}
            });
    }

    @Test
    public void test() throws Exception {
        trySyncUpsWithVariousChanges(numberAccounts, numberContactsPerAccount, localChangeForAccount, remoteChangeForAccount, localChangeForContact, remoteChangeForContact);
    }
}
