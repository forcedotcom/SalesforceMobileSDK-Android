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
package com.salesforce.androidsdk.analytics.model;

import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Represents a typical instrumentation event. Transforms can be used to
 * convert this event into a specific library's event format.
 *
 * @author bhariharan
 */
public class InstrumentationEvent {

    private static final String TAG = "InstrumentationEvent";
    private static final String EVENT_ID_KEY = "eventId";
    private static final String START_TIME_KEY = "startTime";
    private static final String END_TIME_KEY = "endTime";
    private static final String NAME_KEY = "name";
    private static final String ATTRIBUTES_KEY = "attributes";
    private static final String SESSION_ID_KEY = "sessionId";
    private static final String SEQUENCE_ID_KEY = "sequenceId";
    private static final String SENDER_ID_KEY = "senderId";
    private static final String SENDER_CONTEXT_KEY = "senderContext";
    private static final String EVENT_TYPE_KEY = "eventType";
    private static final String TYPE_KEY = "type";
    private static final String SUBTYPE_KEY = "subtype";
    private static final String ERROR_TYPE_KEY = "errorType";
    private static final String CONNECTION_TYPE_KEY = "connectionType";
    private static final String DEVICE_APP_ATTRIBUTES_KEY = "deviceAppAttributes";

    private String eventId;
    private long startTime;
    private long endTime;
    private String name;
    private Map<String, Object> attributes;
    private int sessionId;
    private int sequenceId;
    private String senderId;
    private Map<String, Object> senderContext;
    private EventType eventType;
    private Type type;
    private Subtype subtype;
    private ErrorType errorType;
    private DeviceAppAttributes deviceAppAttributes;
    private String connectionType;

    InstrumentationEvent(String eventId, long startTime, long endTime, String name,
                                Map<String, Object> attributes, int sessionId, int sequenceId,
                                String senderId, Map<String, Object> senderContext,
                                EventType eventType, Type type, Subtype subtype, ErrorType errorType,
                                DeviceAppAttributes deviceAppAttributes, String connectionType) {
        this.eventId = eventId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.name = name;
        this.attributes = attributes;
        this.sessionId = sessionId;
        this.sequenceId = sequenceId;
        this.senderId = senderId;
        this.senderContext = senderContext;
        this.eventType = eventType;
        this.type = type;
        this.subtype = subtype;
        this.errorType = errorType;
        this.deviceAppAttributes = deviceAppAttributes;
        this.connectionType = connectionType;
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
            attributes = convertJsonToMap(json.optJSONObject(ATTRIBUTES_KEY));
            sessionId = json.optInt(SESSION_ID_KEY);
            sequenceId = json.optInt(SEQUENCE_ID_KEY);
            senderId = json.optString(SENDER_ID_KEY);
            senderContext = convertJsonToMap(json.optJSONObject(SENDER_CONTEXT_KEY));
            final String eventTypeString = json.optString(EVENT_TYPE_KEY);
            if (!TextUtils.isEmpty(eventTypeString)) {
                eventType = EventType.valueOf(eventTypeString);
            }
            final String typeString = json.optString(TYPE_KEY);
            if (!TextUtils.isEmpty(typeString)) {
                type = Type.valueOf(typeString);
            }
            final String subtypeString = json.optString(SUBTYPE_KEY);
            if (!TextUtils.isEmpty(subtypeString)) {
                subtype = Subtype.valueOf(subtypeString);
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
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * Returns session ID.
     *
     * @return Session ID.
     */
    public int getSessionId() {
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
    public Map<String, Object> getSenderContext() {
        return senderContext;
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
     * Returns type.
     *
     * @return Type.
     */
    public Type getType() {
        return type;
    }

    /**
     * Returns subtype.
     *
     * @return Subtype.
     */
    public Subtype getSubtype() {
        return subtype;
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
                final JSONObject attributesJson = new JSONObject(attributes);
                json.put(ATTRIBUTES_KEY, attributesJson);
            }
            json.put(SESSION_ID_KEY, sessionId);
            json.put(SEQUENCE_ID_KEY, sequenceId);
            json.put(SENDER_ID_KEY, senderId);
            if (senderContext != null) {
                final JSONObject senderContextJson = new JSONObject(senderContext);
                json.put(SENDER_CONTEXT_KEY, senderContextJson);
            }
            if (eventType != null) {
                json.put(EVENT_TYPE_KEY, eventType.name());
            }
            if (type != null) {
                json.put(TYPE_KEY, type.name());
            }
            if (subtype != null) {
                json.put(SUBTYPE_KEY, subtype.name());
            }
            if (errorType != null) {
                json.put(ERROR_TYPE_KEY, errorType.name());
            }
            json.put(DEVICE_APP_ATTRIBUTES_KEY, deviceAppAttributes.toJson());
            json.put(CONNECTION_TYPE_KEY, connectionType);
        } catch (JSONException e) {
            Log.e(TAG, "Exception thrown while attempting to convert to JSON", e);
        }
        return json;
    }

    private Map<String, Object> convertJsonToMap(JSONObject json) {
        if (json == null) {
            return null;
        }
        final Map<String, Object> map = new HashMap<String, Object>();
        final Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            final String key = keys.next();
            final Object value = json.opt(key);
            map.put(key, value);
        }
        return map;
    }

    /**
     * Represents the type of interaction being logged.
     */
    public enum Type {
        user,
        system,
        error,
        crud
    }

    /**
     * Represents the subtype of interaction being logged.
     */
    public enum Subtype {
        click,
        mouseover,
        create,
        swipe
    }

    /**
     * Represents the type of event being measured.
     */
    public enum EventType {
        interaction,
        pageView,
        perf,
        error
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
