/*
 * Copyright (c) 2016-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.analytics.manager;

import android.content.Context;

import com.salesforce.androidsdk.analytics.model.DeviceAppAttributes;
import com.salesforce.androidsdk.analytics.store.EventStoreManager;

/**
 * This class serves as an interface to the various
 * functions of the SalesforceAnalytics library.
 *
 * @author bhariharan
 */
public class AnalyticsManager {

    private EventStoreManager storeManager;
    private DeviceAppAttributes deviceAppAttributes;
    private int globalSequenceId;

    /**
     * Parameterized constructor.
     *
     * @param uniqueId Unique ID that is used to determine where the events are stored.
     * @param context Context.
     * @param encryptionKey Encryption key.
     * @param deviceAppAttributes Device app attributes.
     */
    public AnalyticsManager(String uniqueId, Context context, String encryptionKey,
                             DeviceAppAttributes deviceAppAttributes) {
        storeManager = new EventStoreManager(uniqueId, context, encryptionKey);
        this.deviceAppAttributes = deviceAppAttributes;
        globalSequenceId = 0;
    }

    /**
     * Resets this instance.
     */
    public void reset() {
        storeManager.deleteAllEvents();
    }

    /**
     * Sets the global sequence ID used by events.
     *
     * @param sequenceId Sequence ID.
     */
    public synchronized void setGlobalSequenceId(int sequenceId) {
        globalSequenceId = sequenceId;
    }

    /**
     * Returns the global sequence ID.
     *
     * @return Sequence ID.
     */
    public synchronized int getGlobalSequenceId() {
        return globalSequenceId;
    }

    /**
     * Returns device app attributes.
     *
     * @return Device app attributes.
     */
    public DeviceAppAttributes getDeviceAppAttributes() {
        return deviceAppAttributes;
    }

    /**
     * Returns an instance of event store manager.
     *
     * @return Event store manager.
     */
    public EventStoreManager getEventStoreManager() {
        return storeManager;
    }

    /**
     * Changes the encryption key to a new value.
     *
     * @param oldKey Old encryption key.
     * @param newKey New encryption key.
     */
    public void changeEncryptionKey(String oldKey, String newKey) {
        storeManager.changeEncryptionKey(oldKey, newKey);
    }
}
