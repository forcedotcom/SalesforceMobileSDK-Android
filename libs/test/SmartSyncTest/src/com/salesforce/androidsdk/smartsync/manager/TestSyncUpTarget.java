/*
 * Copyright (c) 2015, salesforce.com, inc.
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

import com.salesforce.androidsdk.smartsync.util.SyncUpTarget;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * Custom sync up target for tests
 */
public class TestSyncUpTarget extends SyncUpTarget {

    public static final String FAIL_FETCH_DATE = "FAIL_FETCH_DATE";
    public static final String FAIL_SYNC = "FAIL_SYNC";

    private static int seq = 0;
    private static ActionCollector actionCollector;

    private final boolean failSync;

    public TestSyncUpTarget(boolean failSync) {
        this.failSync = failSync;
    }

    public static SyncUpTarget fromJSON(JSONObject target) throws JSONException {
        boolean failSync = target.getBoolean(FAIL_SYNC);
        return new TestSyncUpTarget(failSync);
    }

    @Override
    public JSONObject asJSON() throws JSONException {
        JSONObject target = new JSONObject();
        target.put(ANDROID_IMPL, getClass().getName());
        target.put(FAIL_SYNC, failSync);
        return target;
    }

    @Override
    public String createOnServer(SyncManager syncManager, String objectType, Map<String, Object> fields) throws JSONException, IOException {
        if (failSync) {
            return null;
        }

        String id = "ID." + seq++;
        if (actionCollector != null) {
            actionCollector.createdRecordIds.add(id);
        }
        return id;
    }

    @Override
    public boolean deleteOnServer(SyncManager syncManager, String objectType, String objectId) throws JSONException, IOException {
        if (failSync) {
            return false;
        }

        if (actionCollector != null) {
            actionCollector.deletedRecordIds.add(objectId);
        }

        return true;
    }

    @Override
    public boolean updateOnServer(SyncManager syncManager, String objectType, String objectId, Map<String, Object> fields) throws JSONException, IOException {
        if (failSync) {
            return false;
        }

        if (actionCollector != null) {
            actionCollector.updatedRecordIds.add(objectId);
        }

        return true;
    }

    //
    // Test support
    //

    public static void setActionCollector(ActionCollector collector) {
        actionCollector = collector;
    }

    static class ActionCollector {
        public List<String> createdRecordIds = new ArrayList<String>();
        public List<String> updatedRecordIds = new ArrayList<String>();
        public List<String> deletedRecordIds = new ArrayList<String>();
        public List<String> fetchLastModifiedDateRecordIds = new ArrayList<String>();
    }

}