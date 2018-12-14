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
package com.salesforce.androidsdk.smartsync.util;

import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartsync.manager.SyncManager;
import com.salesforce.androidsdk.smartsync.target.SyncDownTarget;
import com.salesforce.androidsdk.smartsync.target.SyncTarget;
import com.salesforce.androidsdk.smartsync.target.SyncUpTarget;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


/**
 * State of a sync-down or sync-up 
 */
public class SyncState {
	// SmartStore
	public static final String SYNCS_SOUP = "syncs_soup";

	public static final String SYNC_NAME = "name";
	public static final String SYNC_TYPE = "type";
	public static final String SYNC_TARGET = "target";
	public static final String SYNC_OPTIONS = "options";
	public static final String SYNC_SOUP_NAME = "soupName";
	public static final String SYNC_STATUS = "status";
	public static final String SYNC_PROGRESS = "progress";
	public static final String SYNC_TOTAL_SIZE = "totalSize";
    public static final String SYNC_MAX_TIME_STAMP = "maxTimeStamp";
	public static final String SYNC_START_TIME = "startTime";
	public static final String SYNC_END_TIME = "endTime";
	public static final String SYNC_ERROR = "error";

	private long id;
	private Type type;
	private String name;
	private SyncTarget target;
	private SyncOptions options;
	private String soupName;
	private Status status;
	private int progress;
	private int totalSize;
    private long maxTimeStamp;

	// Start and end time in milliseconds since 1970
	private long startTime;
	private long endTime;

	//Error return from SFDC API
	private String errorJSON;
	
	/**
	 * Create syncs soup if needed
	 * @param store
	 */
	public static void setupSyncsSoupIfNeeded(SmartStore store) {
    	if (store.hasSoup(SYNCS_SOUP) && store.getSoupIndexSpecs(SYNCS_SOUP).length == 2) {
			return;
		}

		final IndexSpec[] indexSpecs = {
			new IndexSpec(SYNC_TYPE, SmartStore.Type.string),
			new IndexSpec(SYNC_NAME, SmartStore.Type.string)
		};

		// Syncs soup exists but doesn't have all the required indexes
		if (store.hasSoup(SYNCS_SOUP)) {
			try {
				store.alterSoup(SYNCS_SOUP, indexSpecs, false);
			}
			catch (JSONException e) {
				throw new SyncManager.SmartSyncException(e);
			}
		}
		// Syncs soup does not exist
		else {
			store.registerSoup(SYNCS_SOUP, indexSpecs);
		}
	}
	
	/**
	 * Create sync state in database for a sync down and return corresponding SyncState
	 * NB: Throws exception if there is already a sync with the same name (when name is not null)
	 *
	 * @param store
	 * @param target
	 * @param options
	 * @param soupName
	 * @param name
	 * @return
	 * @throws JSONException
	 */
	public static SyncState  createSyncDown(SmartStore store, SyncDownTarget target, SyncOptions options, String soupName, String name) throws JSONException {
    	JSONObject sync = new JSONObject();
    	sync.put(SYNC_TYPE, Type.syncDown);
		if (name != null) sync.put(SYNC_NAME, name);
    	sync.put(SYNC_TARGET, target.asJSON());
        sync.put(SYNC_OPTIONS, options.asJSON());
    	sync.put(SYNC_SOUP_NAME, soupName);
    	sync.put(SYNC_STATUS, Status.NEW.name());
    	sync.put(SYNC_PROGRESS, 0);
    	sync.put(SYNC_TOTAL_SIZE, -1);
        sync.put(SYNC_MAX_TIME_STAMP, -1);
		sync.put(SYNC_START_TIME, 0);
		sync.put(SYNC_END_TIME, 0);
		sync.put(SYNC_ERROR, "");

		if (name != null && hasSyncWithName(store, name)) {
			throw new SyncManager.SmartSyncException("Failed to create sync down: there is already a sync with name:" + name);
		}

    	sync = store.upsert(SYNCS_SOUP, sync);
		if (sync == null) {
			throw new SyncManager.SmartSyncException("Failed to create sync down");
		}

    	return SyncState.fromJSON(sync);
	}

	
	/**
	 * Create sync state in database for a sync up and return corresponding SyncState
	 * NB: Throws exception if there is already a sync with the same name (when name is not null)
	 *
	 * @param store
	 * @param target
	 * @param options
	 * @param soupName
	 * @param name
	 * @return
	 * @throws JSONException
	 */
	public static SyncState createSyncUp(SmartStore store, SyncUpTarget target, SyncOptions options, String soupName, String name) throws JSONException {
    	JSONObject sync = new JSONObject();
    	sync.put(SYNC_TYPE, Type.syncUp);
		if (name != null) sync.put(SYNC_NAME, name);
        sync.put(SYNC_TARGET, target.asJSON());
    	sync.put(SYNC_SOUP_NAME, soupName);
    	sync.put(SYNC_OPTIONS, options.asJSON());
    	sync.put(SYNC_STATUS, Status.NEW.name());
    	sync.put(SYNC_PROGRESS, 0);
    	sync.put(SYNC_TOTAL_SIZE, -1);
        sync.put(SYNC_MAX_TIME_STAMP, -1);
		sync.put(SYNC_START_TIME, 0);
		sync.put(SYNC_END_TIME, 0);
		sync.put(SYNC_ERROR, "");

		if (name != null && hasSyncWithName(store, name)) {
			throw new SyncManager.SmartSyncException("Failed to create sync up: there is already a sync with name:" + name);
		}

    	sync = store.upsert(SYNCS_SOUP, sync);
		if (sync == null) {
			throw new SyncManager.SmartSyncException("Failed to create sync up");
		}
    	return SyncState.fromJSON(sync);
	}
	
	/**
	 * Build SyncState from json
	 * @param sync
	 * @return
	 * @throws JSONException
	 */
	public static SyncState fromJSON(JSONObject sync) throws JSONException {
		SyncState state = new SyncState();
		state.id = sync.getLong(SmartStore.SOUP_ENTRY_ID);
		state.type = Type.valueOf(sync.getString(SYNC_TYPE));
		state.name = JSONObjectHelper.optString(sync, SYNC_NAME);
        final JSONObject jsonTarget = sync.optJSONObject(SYNC_TARGET);
        state.target = (state.type == Type.syncDown ? SyncDownTarget.fromJSON(jsonTarget) : SyncUpTarget.fromJSON(jsonTarget));
		state.options = SyncOptions.fromJSON(sync.optJSONObject(SYNC_OPTIONS));
		state.soupName = sync.getString(SYNC_SOUP_NAME);
		state.status = Status.valueOf(sync.getString(SYNC_STATUS));
		state.progress = sync.getInt(SYNC_PROGRESS);
		state.totalSize = sync.getInt(SYNC_TOTAL_SIZE);
        state.maxTimeStamp = sync.optLong(SYNC_MAX_TIME_STAMP, -1);
		state.startTime = sync.optLong(SYNC_START_TIME, 0);
		state.endTime = sync.optLong(SYNC_START_TIME, 0);
		state.errorJSON = JSONObjectHelper.optString(sync, SYNC_ERROR, "");
		return state;
	}
	
	/**
	 * Get sync state of sync given by id
	 *
	 * @param store
	 * @param id
	 * @return
	 * @throws JSONException
	 */
	public static SyncState byId(SmartStore store, long id) throws JSONException {
    	JSONArray syncs = store.retrieve(SYNCS_SOUP, id);

    	if (syncs == null || syncs.length() == 0) 
    		return null;
    	
    	return SyncState.fromJSON(syncs.getJSONObject(0));
	}

	/**
	 * Get sync state of sync given by name
	 *
	 * @param store
	 * @param name
	 * @return
	 * @throws JSONException
	 */
	public static SyncState byName(SmartStore store, String name) throws JSONException {
		if (name == null) {
			throw new SyncManager.SmartSyncException("name must not be null");
		}

		long syncId = store.lookupSoupEntryId(SYNCS_SOUP, SYNC_NAME, name);

		if (syncId < 0)
			return null;

		return byId(store, syncId);
	}

	/**
	 * Delete row for sync given by id
	 * @param store
	 * @param id
	 */
	public static void deleteSync(SmartStore store, long id) {
		store.delete(SYNCS_SOUP, id);
	}

	/**
	 * Delete row for sync given by name
	 *  @param store
	 * @param name
	 */
	public static void deleteSync(SmartStore store, String name) {
		if (name == null) {
			throw new SyncManager.SmartSyncException("name must not be null");
		}

		long syncId = store.lookupSoupEntryId(SYNCS_SOUP, SYNC_NAME, name);

		if (syncId < 0)
			return;

		deleteSync(store, syncId);
	}

	/**
	 * Return true if there is a sync with the given name
	 *
	 * @param store
	 * @param name
	 * @return
	 */
	public static boolean hasSyncWithName(SmartStore store, String name) {
		if (name == null) {
			throw new SyncManager.SmartSyncException("name must not be null");
		}

		long syncId = store.lookupSoupEntryId(SYNCS_SOUP, SYNC_NAME, name);

		return syncId != -1;
	}

	/**
	 * @return json representation of sync
	 * @throws JSONException
	 */
	public JSONObject asJSON() throws JSONException {
		JSONObject sync = new JSONObject();
		sync.put(SmartStore.SOUP_ENTRY_ID, id);
		sync.put(SYNC_TYPE, type.name());
		if (name != null) sync.put(SYNC_NAME, name);
		if (target != null) sync.put(SYNC_TARGET, target.asJSON());
		if (options != null) sync.put(SYNC_OPTIONS, options.asJSON());
		sync.put(SYNC_SOUP_NAME, soupName);
		sync.put(SYNC_STATUS, status.name());
		sync.put(SYNC_PROGRESS, progress);
		sync.put(SYNC_TOTAL_SIZE, totalSize);
        sync.put(SYNC_MAX_TIME_STAMP, maxTimeStamp);
		sync.put(SYNC_START_TIME, startTime);
		sync.put(SYNC_END_TIME, endTime);
		sync.put(SYNC_ERROR, errorJSON);
		return sync;
	}
	
	/**
	 * Save SyncState to db
	 * @param store
	 * @throws JSONException
	 */
	public void save(SmartStore store) throws JSONException {
		JSONObject sync = store.update(SYNCS_SOUP, asJSON(), getId());
		if (sync == null) {
			throw new SyncManager.SmartSyncException("Failed to save sync state");
		}
	}
	
	public long getId() {
		return id;
	}
	
	public Type getType() {
		return type;
	}
	
	public SyncTarget getTarget() {
		return target;
	}
	
	public SyncOptions getOptions() {
		return options;
	}

    public MergeMode getMergeMode() {
        return (options != null && options.getMergeMode() != null) ? options.getMergeMode() : MergeMode.OVERWRITE;
    }
	
	public String getSoupName() {
		return soupName;
	}
	
	public Status getStatus() {
		return status;
	}
	
	public int getProgress() {
		return progress;
	}

	public int getTotalSize() {
		return totalSize;
	}

    public long getMaxTimeStamp() {
        return maxTimeStamp;
    }

	public double getStartTime() {
		return startTime;
	}

	public double getEndTime() {
		return endTime;
	}

	public String getError() {
		return errorJSON;
	}

	public void setMaxTimeStamp(long maxTimeStamp) {
        this.maxTimeStamp = maxTimeStamp;
    }

    public void setProgress(int progress) {
		this.progress = progress;
	}
	
	public void setTotalSize(int totalSize) {
		this.totalSize = totalSize;
	}
	
	public void setStatus(Status status) {
		if (this.status == Status.NEW && status == Status.RUNNING) {
			this.startTime = System.currentTimeMillis();
		}
		if (this.status == Status.RUNNING && (status == Status.DONE || status == Status.FAILED)) {
			this.endTime = System.currentTimeMillis();
		}

		this.status = status;
	}

	public void setError(String error) {
		this.errorJSON = error;
	}
	
	public boolean isDone() {
		return this.status == Status.DONE;
	}
	
	public boolean hasFailed() {
		return this.status == Status.FAILED;
	}
	
	public boolean isRunning() {
		return this.status == Status.RUNNING;
	}
	
	public SyncState copy() throws JSONException {
		return SyncState.fromJSON(asJSON());
	}	
	
    /**
     * Enum for sync type
     */
    public enum Type {
        syncDown,
        syncUp
    }
    
    /**
     * Enum for sync status
     *
     */
    public enum Status {
    	NEW,
    	RUNNING,
    	DONE,
    	FAILED
    }

    /**
     * Enum for merge modes
     */
    public enum MergeMode {
        OVERWRITE,
        LEAVE_IF_CHANGED;
    }
}
