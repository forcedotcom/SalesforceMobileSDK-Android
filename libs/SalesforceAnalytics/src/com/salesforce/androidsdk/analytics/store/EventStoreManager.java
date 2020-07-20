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
package com.salesforce.androidsdk.analytics.store;

import android.content.Context;
import android.text.TextUtils;

import com.salesforce.androidsdk.analytics.model.InstrumentationEvent;
import com.salesforce.androidsdk.analytics.security.Encryptor;
import com.salesforce.androidsdk.analytics.util.SalesforceAnalyticsLogger;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import io.paperdb.Book;
import io.paperdb.Paper;

/**
 * Provides APIs to store events in an encrypted store on the filesystem.
 * Each event is stored in a separate file on the filesystem.
 *
 * @author bhariharan
 */
public class EventStoreManager {

    private static final String FILENAME = "event_store";
    private static final String TAG = "EventStoreManager";

    private Context context;
    private String encryptionKey;
    private Book book;
    private boolean isLoggingEnabled = true;
    private int maxEvents = 10000;

    /**
     * Parameterized constructor.
     *
     * @param filenameSuffix Filename suffix to uniquely identify this batch of events.
     *                       Typically this would be used to batch events for a user or an org.
     * @param context Context.
     * @param encryptionKey Encryption key (must be Base 64 encoded).
     */
    public EventStoreManager(String filenameSuffix, Context context, String encryptionKey) {
        this.context = context;
        this.encryptionKey = encryptionKey;
        book = Paper.bookOn(context.getFilesDir().getAbsolutePath(), FILENAME + filenameSuffix);
    }

    /**
     * Stores an event to the filesystem. A combination of event's unique ID and
     * filename suffix is used to generate a unique filename per event.
     *
     * @param event Event to be persisted.
     */
    public void storeEvent(InstrumentationEvent event) {
        if (event == null || TextUtils.isEmpty(event.toJson().toString())) {
            SalesforceAnalyticsLogger.d(context, TAG, "Invalid event");
            return;
        }
        if (!shouldStoreEvent()) {
            return;
        }
        book.write(event.getEventId(), encrypt(event.toJson().toString()));
    }

    /**
     * Stores a list of events to the filesystem.
     *
     * @param events List of events.
     */
    public void storeEvents(List<InstrumentationEvent> events) {
        if (events == null || events.size() == 0) {
            SalesforceAnalyticsLogger.d(context, TAG, "No events to store");
            return;
        }
        if (!shouldStoreEvent()) {
            return;
        }
        for (final InstrumentationEvent event : events) {
            storeEvent(event);
        }
    }

    /**
     * Returns a specific event stored on the filesystem.
     *
     * @param eventId Unique identifier for the event.
     * @return Event.
     */
    public InstrumentationEvent fetchEvent(String eventId) {
        if (TextUtils.isEmpty(eventId)) {
            SalesforceAnalyticsLogger.e(context, TAG, "Invalid event ID supplied: " + eventId);
            return null;
        }
        InstrumentationEvent event = null;
        final String encryptedEvent = book.read(eventId, null);
        if (!TextUtils.isEmpty(encryptedEvent)) {
            final String decryptedEvent = decrypt(encryptedEvent);
            if (!TextUtils.isEmpty(decryptedEvent)) {
                try {
                    final JSONObject jsonObject = new JSONObject(decryptedEvent);
                    event = new InstrumentationEvent(jsonObject);
                } catch (JSONException e) {
                    SalesforceAnalyticsLogger.e(context, TAG, "Exception occurred while attempting to convert to JSON", e);
                }
            }
        }
        return event;
    }

    /**
     * Returns all the events stored on the filesystem for that unique identifier.
     *
     * @return List of events.
     */
    public List<InstrumentationEvent> fetchAllEvents() {
        final List<String> eventIds = book.getAllKeys();
        final List<InstrumentationEvent> events = new ArrayList<>();
        for (final String eventId : eventIds) {
            final InstrumentationEvent event = fetchEvent(eventId);
            if (event != null) {
                events.add(event);
            }
        }
        return events;
    }

    /**
     * Deletes a specific event stored on the filesystem.
     *
     * @param eventId Unique identifier for the event.
     * @return True - if successful, False - otherwise.
     */
    public boolean deleteEvent(String eventId) {
        book.delete(eventId);
        return !book.contains(eventId);
    }

    /**
     * Deletes the events stored on the filesystem for that unique identifier.
     */
    public void deleteEvents(List<String> eventIds) {
        if (eventIds == null || eventIds.size() == 0) {
            SalesforceAnalyticsLogger.d(context, TAG, "No events to delete");
            return;
        }
        for (final String eventId : eventIds) {
            deleteEvent(eventId);
        }
    }

    /**
     * Deletes all the events stored on the filesystem for that unique identifier.
     */
    public void deleteAllEvents() {
        book.destroy();
    }

    /**
     * Disables or enables logging of events. If logging is disabled, no events
     * will be stored. However, publishing of events is still possible.
     *
     * @param enabled True - if logging should be enabled, False - otherwise.
     */
    public synchronized void enableLogging(boolean enabled) {
        isLoggingEnabled = enabled;
    }

    /**
     * Returns whether logging is enabled or disabled.
     *
     * @return True - if logging is enabled, False - otherwise.
     */
    public synchronized boolean isLoggingEnabled() {
        return isLoggingEnabled;
    }

    /**
     * Sets the maximum number of events that can be stored.
     *
     * @param maxEvents Maximum number of events.
     */
    public synchronized void setMaxEvents(int maxEvents) {
        this.maxEvents = maxEvents;
    }

    /**
     * Returns the maximum number of events that can be stored.
     *
     * @return Maximum number of events.
     */
    public int getMaxEvents() {
        return maxEvents;
    }

    /**
     * Returns number of stored events.
     *
     * @return Number of stored events.
     */
    public int getNumStoredEvents() {
        return book.getAllKeys().size();
    }

    private boolean shouldStoreEvent() {
        return (isLoggingEnabled && (getNumStoredEvents() < maxEvents));
    }

    private String encrypt(String data) {
        return Encryptor.encrypt(data, encryptionKey);
    }

    private String decrypt(String data) {
        return Encryptor.decrypt(data, encryptionKey);
    }
}
