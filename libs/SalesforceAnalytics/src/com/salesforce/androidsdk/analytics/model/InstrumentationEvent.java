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
package com.salesforce.androidsdk.analytics.model;

import android.text.TextUtils;

import com.salesforce.androidsdk.analytics.util.SalesforceAnalyticsLogger;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents a typical instrumentation event. Transforms can be used to
 * convert this event into a specific library's event format.
 *
 * @author bhariharan
 */
public class InstrumentationEvent {

    private static final String TAG = "InstrumentationEvent";
    public static final String EVENT_ID_KEY = "eventId";
    public static final String START_TIME_KEY = "startTime";
    public static final String END_TIME_KEY = "endTime";
    public static final String NAME_KEY = "name";
    public static final String ATTRIBUTES_KEY = "attributes";
    public static final String SESSION_ID_KEY = "sessionId";
    public static final String SEQUENCE_ID_KEY = "sequenceId";
    public static final String SENDER_ID_KEY = "senderId";
    public static final String SENDER_CONTEXT_KEY = "senderContext";
    public static final String SCHEMA_TYPE_KEY = "schemaType";
    public static final String EVENT_TYPE_KEY = "eventType";
    public static final String ERROR_TYPE_KEY = "errorType";
    public static final String CONNECTION_TYPE_KEY = "connectionType";
    public static final String DEVICE_APP_ATTRIBUTES_KEY = "deviceAppAttributes";
    public static final String SENDER_PARENT_ID_KEY = "senderParentId";
    public static final String SESSION_START_TIME_KEY = "sessionStartTime";
    public static final String PAGE_KEY = "page";
    public static final String PREVIOUS_PAGE_KEY = "previousPage";
    public static final String MARKS_KEY = "marks";

    private String eventId;
    private long startTime;
    private long endTime;
    private String name;
    private JSONObject attributes;
    private String sessionId;
    private int sequenceId;
    private String senderId;
    private JSONObject senderContext;
    private SchemaType schemaType;
    private EventType eventType;
    private ErrorType errorType;
    private DeviceAppAttributes deviceAppAttributes;
    private String connectionType;
    private String senderParentId;
    private long sessionStartTime;
    private JSONObject page;
    private JSONObject previousPage;
    private JSONObject marks;

    InstrumentationEvent(String eventId, long startTime, long endTime, String name,
                         JSONObject attributes, String sessionId, int sequenceId,
                         String senderId, JSONObject senderContext,
                         SchemaType schemaType, EventType eventType, ErrorType errorType,
                         DeviceAppAttributes deviceAppAttributes, String connectionType,
                         String senderParentId, long sessionStartTime, JSONObject page,
                         JSONObject previousPage, JSONObject marks) {
        this.eventId = eventId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.name = name;
        this.attributes = attributes;
        this.sessionId = sessionId;
        this.sequenceId = sequenceId;
        this.senderId = senderId;
        this.senderContext = senderContext;
        this.schemaType = schemaType;
        this.eventType = eventType;
        this.errorType = errorType;
        this.deviceAppAttributes = deviceAppAttributes;
        this.connectionType = connectionType;
        this.senderParentId = senderParentId;
        this.sessionStartTime = sessionStartTime;
        this.page = page;
        this.previousPage = previousPage;
        this.marks = marks;
    }

    /**
     * Constructs an event from its JSON representation.
     * This is meant for internal use. Apps should use InstrumentationEventBuilder
     * to build InstrumentationEvent objects.
     *
     * @param json JSON object.
     */
    public InstrumentationEvent(JSONObject json) {
        if (json != null) {
            eventId = json.optString(EVENT_ID_KEY);
            startTime = json.optLong(START_TIME_KEY);
            endTime = json.optLong(END_TIME_KEY);
            name = json.optString(NAME_KEY);
            attributes = json.optJSONObject(ATTRIBUTES_KEY);
            sessionId = json.optString(SESSION_ID_KEY);
            sequenceId = json.optInt(SEQUENCE_ID_KEY);
            senderId = json.optString(SENDER_ID_KEY);
            senderContext = json.optJSONObject(SENDER_CONTEXT_KEY);
            final String schemaTypeString = json.optString(SCHEMA_TYPE_KEY);
            if (!TextUtils.isEmpty(schemaTypeString)) {
                schemaType = SchemaType.valueOf(schemaTypeString);
            }
            final String eventTypeString = json.optString(EVENT_TYPE_KEY);
            if (!TextUtils.isEmpty(eventTypeString)) {
                eventType = EventType.valueOf(eventTypeString);
            }
            final String errorTypeString = json.optString(ERROR_TYPE_KEY);
            if (!TextUtils.isEmpty(errorTypeString)) {
                errorType = ErrorType.valueOf(errorTypeString);
            }
            final JSONObject deviceAttributesJson = json.optJSONObject(DEVICE_APP_ATTRIBUTES_KEY);
            if (deviceAttributesJson != null) {
                deviceAppAttributes = new DeviceAppAttributes(deviceAttributesJson);
            }
            connectionType = json.optString(CONNECTION_TYPE_KEY);
            senderParentId = json.optString(SENDER_PARENT_ID_KEY);
            sessionStartTime = json.optLong(SESSION_START_TIME_KEY);
            page = json.optJSONObject(PAGE_KEY);
            previousPage = json.optJSONObject(PREVIOUS_PAGE_KEY);
            marks = json.optJSONObject(MARKS_KEY);
        }
    }

    /**
     * Returns event ID.
     *
     * @return Event ID.
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * Returns start time.
     *
     * @return Start time.
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Returns end time.
     *
     * @return End time.
     */
    public long getEndTime() {
        return endTime;
    }

    /**
     * Returns name.
     *
     * @return Name.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns attributes.
     *
     * @return Attributes.
     */
    public JSONObject getAttributes() {
        return attributes;
    }

    /**
     * Returns session ID.
     *
     * @return Session ID.
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Returns sequence ID.
     *
     * @return Sequence ID.
     */
    public int getSequenceId() {
        return sequenceId;
    }

    /**
     * Returns sender ID.
     *
     * @return Sender ID.
     */
    public String getSenderId() {
        return senderId;
    }

    /**
     * Returns sender context.
     *
     * @return Sender context.
     */
    public JSONObject getSenderContext() {
        return senderContext;
    }

    /**
     * Returns schema type.
     *
     * @return Schema type.
     */
    public SchemaType getSchemaType() {
        return schemaType;
    }

    /**
     * Returns event type.
     *
     * @return Event type.
     */
    public EventType getEventType() {
        return eventType;
    }

    /**
     * Returns error type.
     *
     * @return Error type.
     */
    public ErrorType getErrorType() {
        return errorType;
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
     * Returns connection type.
     *
     * @return Connection type.
     */
    public String getConnectionType() {
        return connectionType;
    }

    /**
     * Returns sender parent ID.
     *
     * @return Sender parent ID.
     */
    public String getSenderParentId() {
        return senderParentId;
    }

    /**
     * Returns session start time.
     *
     * @return Session start time.
     */
    public long getSessionStartTime() {
        return sessionStartTime;
    }

    /**
     * Returns page.
     *
     * @return Page.
     */
    public JSONObject getPage() {
        return page;
    }

    /**
     * Returns previous page.
     *
     * @return Previous page.
     */
    public JSONObject getPreviousPage() {
        return previousPage;
    }

    /**
     * Returns marks.
     *
     * @return Marks.
     */
    public JSONObject getMarks() {
        return marks;
    }

    /**
     * Returns a JSON representation of this event.
     *
     * @return JSON object.
     */
    public JSONObject toJson() {
        final JSONObject json = new JSONObject();
        try {
            json.put(EVENT_ID_KEY, eventId);
            json.put(START_TIME_KEY, startTime);
            json.put(END_TIME_KEY, endTime);
            json.put(NAME_KEY, name);
            if (attributes != null) {
                json.put(ATTRIBUTES_KEY, attributes);
            }
            if (sessionId != null) {
                json.put(SESSION_ID_KEY, sessionId);
            }
            json.put(SEQUENCE_ID_KEY, sequenceId);
            json.put(SENDER_ID_KEY, senderId);
            if (senderContext != null) {
                json.put(SENDER_CONTEXT_KEY, senderContext);
            }
            if (schemaType != null) {
                json.put(SCHEMA_TYPE_KEY, schemaType.name());
            }
            if (eventType != null) {
                json.put(EVENT_TYPE_KEY, eventType.name());
            }
            if (errorType != null) {
                json.put(ERROR_TYPE_KEY, errorType.name());
            }
            json.put(DEVICE_APP_ATTRIBUTES_KEY, deviceAppAttributes.toJson());
            json.put(CONNECTION_TYPE_KEY, connectionType);
            json.put(SENDER_PARENT_ID_KEY, senderParentId);
            json.put(SESSION_START_TIME_KEY, sessionStartTime);
            if (page != null) {
                json.put(PAGE_KEY, page);
            }
            if (previousPage != null) {
                json.put(PREVIOUS_PAGE_KEY, previousPage);
            }
            if (marks != null) {
                json.put(MARKS_KEY, marks);
            }
        } catch (JSONException e) {
            SalesforceAnalyticsLogger.e(null, TAG, "Exception thrown while attempting to convert to JSON", e);
        }
        return json;
    }

    @Override
    public boolean equals(Object object) {
        if (object == null || !(object instanceof InstrumentationEvent)) {
            return false;
        }
        final InstrumentationEvent event = (InstrumentationEvent) object;
        if (TextUtils.isEmpty(eventId)) {
            return false;
        }

        /*
         * Since event ID is globally unique and is set during construction of the event,
         * if the event IDs of both events are equal, the events themselves are the same.
         */
        if (eventId.equals(event.getEventId())) {
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return eventId.hashCode();
    }

    /**
     * Represents the type of event being logged.
     */
    public enum EventType {
        user,
        system,
        error,
        crud
    }

    /**
     * Represents the type of schema being logged.
     */
    public enum SchemaType {
        LightningInteraction,
        LightningPageView,
        LightningPerformance,
        LightningError
    }

    /**
     * Represents the type of error being logged.
     */
    public enum ErrorType {
        info,
        warn,
        error
    }
}
