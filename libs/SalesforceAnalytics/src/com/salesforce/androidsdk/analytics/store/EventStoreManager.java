/*
 * Copyright (c) 2016, salesforce.com, inc.
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
import android.util.Log;

import com.salesforce.androidsdk.analytics.model.InstrumentationEvent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides APIs to store events in an encrypted store on the filesystem.
 * Each event is stored in a separate file on the filesystem.
 *
 * @author bhariharan
 */
public class EventStoreManager {

    private static final String TAG = "EventStoreManager";

    private String filenameSuffix;
    private File rootDir;
    private EventFileFilter fileFilter;
    private Context context;

    /**
     * Parameterized constructor.
     *
     * @param filenameSuffix Filename suffix to uniquely identify this batch of events.
     *                       Typically this would be used to batch events for a user or an org.
     * @param context Context.
     */
    public EventStoreManager(String filenameSuffix, Context context) {
        this.filenameSuffix = filenameSuffix;
        this.context = context;
        fileFilter = new EventFileFilter(filenameSuffix);
        rootDir = context.getFilesDir();
    }

    /**
     * Stores an event to the filesystem. A combination of event's unique ID and
     * filename suffix is used to generate a unique filename per event.
     *
     * @param event Event to be persisted.
     */
    public void storeEvent(InstrumentationEvent event) {
        if (event == null || TextUtils.isEmpty(event.toJson())) {
            Log.d(TAG, "Invalid event");
            return;
        }
        final String filename = event.getEventId() + filenameSuffix;
        FileOutputStream outputStream;
        try {
            outputStream = context.openFileOutput(filename, Context.MODE_PRIVATE);
            outputStream.write(event.toJson().getBytes());
            outputStream.close();
        } catch (Exception e) {
            Log.e(TAG, "Exception occurred while saving event to filesystem", e);
        }
    }

    /**
     * Stores a list of events to the filesystem.
     *
     * @param events List of events.
     */
    public void storeEvents(List<InstrumentationEvent> events) {
        if (events == null || events.size() == 0) {
            Log.d(TAG, "No events to store");
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
            Log.e(TAG, "Invalid event ID supplied: " + eventId);
            return null;
        }
        final String filename = eventId + filenameSuffix;
        /*
         * TODO: Check if file exists, read and return results if it does.
         */
        return null;
    }

    /**
     * Returns all the events stored on the filesystem for that unique identifier.
     *
     * @return List of events.
     */
    public List<InstrumentationEvent> fetchAllEvents() {
        final List<File> files = getAllFiles();
        for (final File file : files) {
            /*
             * TODO: Read file here and form event.
             */
        }
        return null;
    }

    /**
     * Deletes a specific event stored on the filesystem.
     *
     * @param eventId Unique identifier for the event.
     * @return True - if successful, False - otherwise.
     */
    public boolean deleteEvent(String eventId) {
        if (TextUtils.isEmpty(eventId)) {
            Log.e(TAG, "Invalid event ID supplied: " + eventId);
            return false;
        }
        final String filename = eventId + filenameSuffix;
        final File file = new File(rootDir, filename);
        return file.delete();
    }

    /**
     * Deletes the events stored on the filesystem for that unique identifier.
     */
    public void deleteEvents(List<String> eventIds) {
        if (eventIds == null || eventIds.size() == 0) {
            Log.d(TAG, "No events to delete");
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
        final List<File> files = getAllFiles();
        for (final File file : files) {
            file.delete();
        }
    }

    /**
     * Returns all available files associated with this unique identifier.
     *
     * @return List of all files.
     */
    private List<File> getAllFiles() {
        final List<File> files = new ArrayList<File>();
        for (final File file : rootDir.listFiles()) {
            if (file != null && fileFilter.accept(rootDir, file.getName())) {
                files.add(file);
            }
        }
        return files;
    }

    /**
     * This class acts as a filter to identify only the relevant event files.
     *
     * @author bhariharan
     */
    private static class EventFileFilter implements FilenameFilter {

        private String fileSuffix;

        /**
         * Parameterized constructor.
         *
         * @param fileSuffix Filename suffix.
         */
        public EventFileFilter(String fileSuffix) {
            this.fileSuffix = fileSuffix;
        }

        @Override
        public boolean accept(File dir, String filename) {
            if (filename != null && filename.endsWith(fileSuffix)) {
                return true;
            }
            return false;
        }
    }
}
