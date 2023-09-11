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
package com.salesforce.androidsdk.mobilesync.util;

import static java.util.Collections.singletonList;

import com.salesforce.androidsdk.mobilesync.manager.SyncManager.SyncUpdateCallback;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * A SyncUpdateCallback which queues SyncStates, then serves them filtered by
 * the SyncState id.  This can be useful for tests which assert the sequence of
 * SyncStates, possibly for multiple and concurrent SyncStates.
 */
public class SyncUpdateCallbackQueue implements SyncUpdateCallback {

    /**
     * The map of queued SyncStates by the SyncState id which provided them
     */
    private final Map<Long, BlockingQueue<SyncState>> syncStatesById = new TreeMap<>();

    /**
     * Initializes a new instance with a pre-allocated queue for the provided
     * SyncState id.
     *
     * @param syncStateId The SyncState id
     */
    public SyncUpdateCallbackQueue(
            final Long syncStateId
    ) {
        this(new TreeSet<>(singletonList(syncStateId)));
    }

    /**
     * Initializes a new instance with pre-allocated queues for each provided
     * SyncState id.
     *
     * @param syncStateIds The list of SyncState ids
     */
    public SyncUpdateCallbackQueue(
            final Set<Long> syncStateIds
    ) {
        for (final Long syncStateId : syncStateIds) {
            syncStatesById.put(
                    syncStateId,
                    new ArrayBlockingQueue<>(1000, true)
            );
        }

        if (syncStatesById.isEmpty()) {
            throw new IllegalStateException("At least one SyncState id must be provided.");
        }
    }

    /**
     * Queues a new SyncState by its id.
     *
     * @param syncState The new SyncState to queue
     */
    public void onUpdate(final SyncState syncState) {

        final long syncStateId = syncState.getId();

        try {
            final BlockingQueue<SyncState> syncStates = syncStatesById.get(syncStateId);
            if (syncStates == null) {
                throw new IllegalStateException("Cannot queue SyncState with unexpected id '" + syncStateId + "'.  Verify the expected ids are provided at initialization.");
            }

            syncStates.put(syncState.copy());

        } catch (Exception e) {
            throw new RuntimeException("Unable to queue SyncState due to an unexpected exception with message '" + e.getMessage() + "'.", e);
        }
    }

    /**
     * Returns the next queued SyncEvent event within a reasonable timeout.  The
     * first SyncState id provided at initialization is used.
     */
    public SyncState getNextSyncUpdate() {
        return getNextSyncUpdate(syncStatesById.keySet().iterator().next());
    }

    /**
     * Returns the next queued SyncEvent event within a reasonable timeout.
     *
     * @param syncStateId The SyncState id
     */
    public SyncState getNextSyncUpdate(final Long syncStateId) {

        try {
            final BlockingQueue<SyncState> syncStates = syncStatesById.get(syncStateId);
            if (syncStates == null) {
                throw new IllegalStateException("Cannot get SyncState with unexpected id '" + syncStateId + "'.  Verify the expected ids are provided at initialization.");
            }

            final SyncState syncState = syncStates.take();

            if (syncState == null)
                throw new RuntimeException("SyncState with id '" + syncStateId + "' was not received on time.");

            return syncState;

        } catch (InterruptedException e) {
            throw new RuntimeException("SyncState with id '" + syncStateId + "' could not be received due to an unexpected exception with message '" + e.getMessage() + "'.", e);
        }
    }
}
