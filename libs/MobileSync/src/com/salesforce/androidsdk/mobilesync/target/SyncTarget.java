/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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

import android.text.TextUtils;

import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.mobilesync.manager.SyncManager;
import com.salesforce.androidsdk.mobilesync.util.Constants;
import com.salesforce.androidsdk.mobilesync.util.MobileSyncLogger;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Abstract super class for SyncUpTarget and SyncDownTarget
 *
 * Targets handle interactions with local store and with remote server.
 *
 * Default targets use SmartStore for local store and __local_*__ fields to flag dirty (i.e. locally created/updated/deleted) records.
 * Custom targets can use a different local store and/or different fields to flag dirty records.
 *
 * Default targets use SObject Rest API to read/write records to the server.
 * Custom targets can use different end points to read/write records to the server.
 */
public abstract class SyncTarget {

    // Sync targets expect the following fields in locally stored records
    public static final String LOCALLY_CREATED = "__locally_created__";
    public static final String LOCALLY_UPDATED = "__locally_updated__";
    public static final String LOCALLY_DELETED = "__locally_deleted__";
    public static final String LOCAL = "__local__";

    // Field added to record to capture last sync error if any
    public static final String LAST_ERROR = "__last_error__";

    // Field added to record to remember sync it came through
    public static final String SYNC_ID = "__sync_id__";

    private static final String TAG = "SyncTarget";

    // Page size used when reading from smartstore
    private static final int PAGE_SIZE = 2000;

    public static final String ANDROID_IMPL = "androidImpl";
    public static final String ID_FIELD_NAME = "idFieldName";
    public static final String MODIFICATION_DATE_FIELD_NAME = "modificationDateFieldName";

    private String idFieldName;
    private String modificationDateFieldName;

    public SyncTarget() {
        this(null, null);
    }

    public SyncTarget(JSONObject target) throws JSONException {
        this(
                target != null ? JSONObjectHelper.optString(target, ID_FIELD_NAME) : null,
                target != null ? JSONObjectHelper.optString(target, MODIFICATION_DATE_FIELD_NAME) : null
        );
    }

    public SyncTarget(String idFieldName, String modificationDateFieldName) {
        this.idFieldName = idFieldName != null ? idFieldName : Constants.ID;
        this.modificationDateFieldName = modificationDateFieldName != null ? modificationDateFieldName : Constants.LAST_MODIFIED_DATE;
    }

    /**
     * @return json representation of target
     * @throws JSONException
     */
    public JSONObject asJSON() throws JSONException {
        JSONObject target = new JSONObject();
        target.put(ANDROID_IMPL, getClass().getName());
        target.put(ID_FIELD_NAME, idFieldName);
        target.put(MODIFICATION_DATE_FIELD_NAME, modificationDateFieldName);
        return target;
    }

    /**
     * @return The field name of the ID field of the record.  Defaults to "Id".
     */
    public String getIdFieldName() {
        return idFieldName;
    }

    /**
     * @return The field name of the modification date field of the record.  Defaults to "LastModifiedDate".
     */
    public String getModificationDateFieldName() {
        return modificationDateFieldName;
    }

    /**
     * Return ids of "dirty" records (records locally created/upated or deleted)
     * @param syncManager
     * @param soupName
     * @param idField
     * @return
     * @throws JSONException
     */
    public SortedSet<String> getDirtyRecordIds(SyncManager syncManager, String soupName, String idField) throws JSONException {
        String dirtyRecordsSql = getDirtyRecordIdsSql(soupName, idField);
        return getIdsWithQuery(syncManager, dirtyRecordsSql);
    }

    /**
     * Return SmartSQL to identify dirty records
     * @param soupName
     * @param idField
     * @return
     */
    protected String getDirtyRecordIdsSql(String soupName, String idField) {
        return String.format("SELECT {%s:%s} FROM {%s} WHERE {%s:%s} = 'true' ORDER BY {%s:%s} ASC", soupName, idField, soupName, soupName, LOCAL, soupName, idField);
    }

    protected SortedSet<String> getIdsWithQuery(SyncManager syncManager, String idsSql) throws JSONException {
        final SortedSet<String> ids = new TreeSet<>();
        final QuerySpec smartQuerySpec = QuerySpec.buildSmartQuerySpec(idsSql, PAGE_SIZE);
        boolean hasMore = true;
        for (int pageIndex = 0; hasMore; pageIndex++) {
            JSONArray results = syncManager.getSmartStore().query(smartQuerySpec, pageIndex);
            hasMore = (results.length() == PAGE_SIZE);
            ids.addAll(toSortedSet(results));
        }

        return ids;
    }

    /**
     * Save cleaned record in local store
     * @param syncManager
     * @param soupName
     * @param record
     */
    public void cleanAndSaveInLocalStore(SyncManager syncManager, String soupName, JSONObject record) throws JSONException {
        cleanAndSaveInSmartStore(syncManager.getSmartStore(), soupName, record, getIdFieldName(), true);
        MobileSyncLogger.d(TAG, "cleanAndSaveInLocalStore", record);
    }

    /**
     * Save record in local store
     * @param syncManager
     * @param soupName
     * @param record
     */
    protected void saveInLocalStore(SyncManager syncManager, String soupName, JSONObject record) throws JSONException {
        saveInSmartStore(syncManager.getSmartStore(), soupName, record, getIdFieldName(), true);
        MobileSyncLogger.d(TAG, "saveInLocalStore", record);
    }

    protected void cleanAndSaveInSmartStore(SmartStore smartStore, String soupName, JSONObject record, String idFieldName, boolean handleTx) throws JSONException {
        cleanRecord(record);
        saveInSmartStore(smartStore, soupName, record, idFieldName, handleTx);
    }

    protected void saveInSmartStore(SmartStore smartStore, String soupName, JSONObject record, String idFieldName, boolean handleTx) throws JSONException {
        if (record.has(SmartStore.SOUP_ENTRY_ID)) {
            // Record came from smartstore
            smartStore.update(soupName, record, record.getLong(SmartStore.SOUP_ENTRY_ID), handleTx);
        }
        else {
            // Record came from server
            smartStore.upsert(soupName, record, idFieldName, handleTx);
        }
    }

    protected void cleanRecord(JSONObject record) throws JSONException {
        record.put(LOCAL, false);
        record.put(LOCALLY_CREATED, false);
        record.put(LOCALLY_UPDATED, false);
        record.put(LOCALLY_DELETED, false);
        record.put(LAST_ERROR, null);
    }

    /**
     * Save records to local store
     * @param syncManager
     * @param soupName
     * @param records
     * @param syncId
     * @throws JSONException
     */
    public void saveRecordsToLocalStore(SyncManager syncManager, String soupName, JSONArray records, long syncId) throws JSONException {
        SmartStore smartStore = syncManager.getSmartStore();
        synchronized(smartStore.getDatabase()) {
            try {
                smartStore.beginTransaction();
                for (int i = 0; i < records.length(); i++) {
                    JSONObject record = new JSONObject(records.getJSONObject(i).toString());
                    addSyncId(record, syncId);
                    cleanAndSaveInSmartStore(syncManager.getSmartStore(), soupName, record, getIdFieldName(), false);
                }
                smartStore.setTransactionSuccessful();
            }
            finally {
                smartStore.endTransaction();
            }
        }
    }

    void addSyncId(JSONObject record, long syncId) throws JSONException {
        if (syncId >= 0) {
            record.put(SYNC_ID, syncId);
        }
    }

    /**
     * Delete the records with the given ids
     * @param syncManager
     * @param soupName
     * @param ids
     * @param idField
     */
    protected void deleteRecordsFromLocalStore(SyncManager syncManager, String soupName, Set<String> ids, String idField) {
        if (ids.size() > 0) {
            String smartSql = String.format("SELECT {%s:%s} FROM {%s} WHERE {%s:%s} IN (%s)",
                    soupName, SmartStore.SOUP_ENTRY_ID, soupName, soupName, idField,
                    "'" + TextUtils.join("', '", ids) + "'");
            QuerySpec querySpec = QuerySpec.buildSmartQuerySpec(smartSql, Integer.MAX_VALUE /* delete all */);
            syncManager.getSmartStore().deleteByQuery(soupName, querySpec);
        }
    }

    private SortedSet<String> toSortedSet(JSONArray jsonArray) throws JSONException {
        SortedSet<String> set = new TreeSet<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            set.add(jsonArray.getJSONArray(i).getString(0));
        }
        return set;
    }

    /**
     * Given a record, return true if it was locally created
     * @param record
     * @return
     */
    public boolean isLocallyCreated(JSONObject record) {
        return record.optBoolean(LOCALLY_CREATED);
    }

    /**
     * Given a record, return true if it was locally updated
     * @param record
     * @return
     */
    public boolean isLocallyUpdated(JSONObject record) {
        return record.optBoolean(LOCALLY_UPDATED);
    }

    /**
     * Given a record, return true if it was locally deleted
     * @param record
     * @return
     */
    public boolean isLocallyDeleted(JSONObject record) {
        return record.optBoolean(LOCALLY_DELETED);
    }

    /**
     * Given a record, return true if it was locally created/updated or deleted
     * @param record
     * @return
     * @throws JSONException
     */
    public boolean isDirty(JSONObject record) throws JSONException {
        return record.getBoolean(LOCAL);
    }

    /**
     * Get record from local store by storeId
     * @param syncManager
     * @param storeId
     * @throws  JSONException
     */
    public JSONObject getFromLocalStore(SyncManager syncManager, String soupName, String storeId) throws JSONException {
        return syncManager.getSmartStore().retrieve(soupName, Long.valueOf(storeId)).getJSONObject(0);
    }

    /**
     * Delete record from local store
     * @param syncManager
     * @param soupName
     * @param record
     */
    public void deleteFromLocalStore(SyncManager syncManager, String soupName, JSONObject record) throws JSONException {
        MobileSyncLogger.d(TAG, "deleteFromLocalStore", record);
        syncManager.getSmartStore().delete(soupName, record.getLong(SmartStore.SOUP_ENTRY_ID));
    }
}
