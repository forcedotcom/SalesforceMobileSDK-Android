/*
 * Copyright (c) 2015-present, salesforce.com, inc.
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

import com.salesforce.androidsdk.smartsync.manager.SyncManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Custom sync up target for tests.
 */
public class TestSyncUpTarget extends SyncUpTarget {

    public static final String SYNC_BEHAVIOR = "SYNC_BEHAVIOR";

    private static int seq = 0;
    private static ActionCollector actionCollector;

    private final SyncBehavior syncBehavior;

    public enum SyncBehavior {
        SOFT_FAIL_ON_SYNC, // doesn't update server but doesn't throw exception, so sync should not end up in failed state
        HARD_FAIL_ON_SYNC, // doesn't update server and throw exception, so sync should end up in failed state
        NO_FAIL
    }

    public TestSyncUpTarget(JSONObject target) throws JSONException {
        super(target);
        this.syncBehavior = SyncBehavior.valueOf(target.getString(SYNC_BEHAVIOR));
    }

    public TestSyncUpTarget(SyncBehavior syncBehavior) {
        super();
        this.syncBehavior = syncBehavior;
    }

    @Override
    public JSONObject asJSON() throws JSONException {
        JSONObject target = new JSONObject();
        target.put(ANDROID_IMPL, getClass().getName());
        target.put(SYNC_BEHAVIOR, syncBehavior.name());
        return target;
    }

    @Override
    public String createOnServer(SyncManager syncManager, String objectType, Map<String, Object> fields) throws JSONException, IOException {
        switch (syncBehavior) {
            case SOFT_FAIL_ON_SYNC:
                return null;
            case HARD_FAIL_ON_SYNC:
                throw new RuntimeException("create hard fail");
            default: // case NO_FAIL:
                String id = "ID." + seq++;
                if (actionCollector != null) {
                    actionCollector.createdRecordIds.add(id);
                }
                return id;
        }
    }

    @Override
    public int deleteOnServer(SyncManager syncManager, String objectType, String objectId) throws IOException {
        switch (syncBehavior) {
            case SOFT_FAIL_ON_SYNC:
                return HttpURLConnection.HTTP_NOT_FOUND;
            case HARD_FAIL_ON_SYNC:
                throw new RuntimeException("delete hard fail");
            default: // case NO_FAIL:
                if (actionCollector != null) {
                    actionCollector.deletedRecordIds.add(objectId);
                }
                return HttpURLConnection.HTTP_OK;
        }
    }

    @Override
    public int updateOnServer(SyncManager syncManager, String objectType, String objectId, Map<String, Object> fields) throws IOException {
        switch (syncBehavior) {
            case SOFT_FAIL_ON_SYNC:
                return HttpURLConnection.HTTP_NOT_FOUND;
            case HARD_FAIL_ON_SYNC:
                throw new RuntimeException("update hard fail");
            default: // case NO_FAIL:
                if (actionCollector != null) {
                    actionCollector.updatedRecordIds.add(objectId);
                }
                return HttpURLConnection.HTTP_OK;
        }
    }

    public static void setActionCollector(ActionCollector collector) {
        actionCollector = collector;
    }

    public static class ActionCollector {
        public List<String> createdRecordIds = new ArrayList<>();
        public List<String> updatedRecordIds = new ArrayList<>();
        public List<String> deletedRecordIds = new ArrayList<>();
    }
}