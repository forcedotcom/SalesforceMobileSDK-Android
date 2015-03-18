/*
 * Copyright (c) 2014, salesforce.com, inc.
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


/**
 * State of a sync-down or sync-up 
 */
public class SyncState {
	// SmartStore
	public static final String SYNCS_SOUP = "syncs_soup";
	
	public static final String SYNC_TYPE = "type";
	public static final String SYNC_TARGET = "target";
	public static final String SYNC_OPTIONS = "options";
	public static final String SYNC_SOUP_NAME = "soupName";
	public static final String SYNC_STATUS = "status";
	public static final String SYNC_PROGRESS = "progress";
	public static final String SYNC_TOTAL_SIZE = "totalSize";
    public static final String SYNC_MAX_TIME_STAMP = "maxTimeStamp";

	private long id;
	private Type type;
	private SyncTarget target;
	private SyncOptions options;
	private String soupName;
	private Status status;
	private int progress;
	private int totalSize;
    private long maxTimeStamp;
	
	
	/**
	 * Create syncs soup if needed
	 * @param store
	 */
	public static void setupSyncsSoupIfNeeded(SmartStore store) {
    	if (store.hasSoup(SYNCS_SOUP)) 
    		return;
    	
    	final IndexSpec[] indexSpecs = {
    			new IndexSpec(SYNC_TYPE, SmartStore.Type.string)
    	};    	
		store.registerSoup(SYNCS_SOUP, indexSpecs);
	}
	
	/**
	 * Create sync state in database for a sync down and return corresponding SyncState
	 * @return
	 * @throws JSONException 
	 */
	public static SyncState createSyncDown(SmartStore store, SyncDownTarget target, SyncOptions options, String soupName) throws JSONException {
    	JSONObject sync = new JSONObject();
    	sync.put(SYNC_TYPE, Type.syncDown);
    	sync.put(SYNC_TARGET, target.asJSON());
        sync.put(SYNC_OPTIONS, options.asJSON());
    	sync.put(SYNC_SOUP_NAME, soupName);
    	sync.put(SYNC_STATUS, Status.NEW.name());
    	sync.put(SYNC_PROGRESS, 0);
    	sync.put(SYNC_TOTAL_SIZE, -1);
        sync.put(SYNC_MAX_TIME_STAMP, -1);

    	sync = store.upsert(SYNCS_SOUP, sync);
    	return SyncState.fromJSON(sync);
	}

	
	/**
	 * Create sync state in database for a sync up and return corresponding SyncState
	 * @return
	 * @throws JSONException 
	 */
	public static SyncState createSyncUp(SmartStore store, SyncUpTarget target, SyncOptions options, String soupName) throws JSONException {
    	JSONObject sync = new JSONObject();
    	sync.put(SYNC_TYPE, Type.syncUp);
        sync.put(SYNC_TARGET, target.asJSON());
    	sync.put(SYNC_SOUP_NAME, soupName);
    	sync.put(SYNC_OPTIONS, options.asJSON());
    	sync.put(SYNC_STATUS, Status.NEW.name());
    	sync.put(SYNC_PROGRESS, 0);
    	sync.put(SYNC_TOTAL_SIZE, -1);
        sync.put(SYNC_MAX_TIME_STAMP, -1);

    	sync = store.upsert(SYNCS_SOUP, sync);
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
        final JSONObject jsonTarget = sync.optJSONObject(SYNC_TARGET);
        state.target = (state.type == Type.syncDown ? SyncDownTarget.fromJSON(jsonTarget) : SyncUpTarget.fromJSON(jsonTarget));
		state.options = SyncOptions.fromJSON(sync.optJSONObject(SYNC_OPTIONS));
		state.soupName = sync.getString(SYNC_SOUP_NAME);
		state.status = Status.valueOf(sync.getString(SYNC_STATUS));
		state.progress = sync.getInt(SYNC_PROGRESS);
		state.totalSize = sync.getInt(SYNC_TOTAL_SIZE);
        state.maxTimeStamp = sync.optLong(SYNC_MAX_TIME_STAMP, -1);
		return state;
	}
	
	/**
	 * Build SyncState from store sync given by id
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
	 * @return json representation of sync
	 * @throws JSONException
	 */
	public JSONObject asJSON() throws JSONException {
		JSONObject sync = new JSONObject();
		sync.put(SmartStore.SOUP_ENTRY_ID, id);
		sync.put(SYNC_TYPE, type.name());
		if (target != null) sync.put(SYNC_TARGET, target.asJSON());
		if (options != null) sync.put(SYNC_OPTIONS, options.asJSON());
		sync.put(SYNC_SOUP_NAME, soupName);
		sync.put(SYNC_STATUS, status.name());
		sync.put(SYNC_PROGRESS, progress);
		sync.put(SYNC_TOTAL_SIZE, totalSize);
        sync.put(SYNC_MAX_TIME_STAMP, maxTimeStamp);
		return sync;
	}
	
	/**
	 * Save SyncState to db
	 * @param store
	 * @throws JSONException
	 */
	public void save(SmartStore store) throws JSONException {
		store.update(SYNCS_SOUP, asJSON(), getId());
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
		this.status = status;
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