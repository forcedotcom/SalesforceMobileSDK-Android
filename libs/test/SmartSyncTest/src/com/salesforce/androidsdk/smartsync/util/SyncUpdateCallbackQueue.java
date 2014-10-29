package com.salesforce.androidsdk.smartsync.util;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;

import com.salesforce.androidsdk.smartsync.manager.SyncManager.SyncUpdateCallback;

/**
 * This tracks sync updates using a queue, allowing for tests to wait for certain sync updates to turn up.
 */
public class SyncUpdateCallbackQueue implements SyncUpdateCallback {

	private BlockingQueue<SyncState> syncs; 
	
	public SyncUpdateCallbackQueue() {
		syncs = new ArrayBlockingQueue<SyncState>(10);
	}
	
	public void onUpdate(SyncState sync) {
		try {
			syncs.offer(sync.copy());
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}
	
    // remove any events in the queue
    public void clearQueue() {
    	syncs.clear();
    }

    /** will return the next event in the queue, waiting if needed for a reasonable amount of time */
    public SyncState getNextSyncUpdate() {
        try {
        	SyncState sync = syncs.poll(30, TimeUnit.SECONDS);
            if (sync == null)
                throw new RuntimeException("Failure ** Timeout waiting for a broadcast ");
            return sync;
        } catch (InterruptedException ex) {
            throw new RuntimeException("Was interrupted waiting for broadcast");
        }
    }
}