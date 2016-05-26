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

import java.util.Map;

/**
 * Represents a typical instrumentation event. Transforms can be used to
 * convert this event into a specific library's event format.
 *
 * @author bhariharan
 */
public class InstrumentationEvent {

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
    private String jsonRepresentation;

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
     * Parameterized constructor, to construct an event from its JSON representation.
     * This is meant for internal use. Apps should use InstrumentationEventBuilder
     * to build InstrumentationEvent objects.
     *
     * @param json JSON string.
     */
    public InstrumentationEvent(String json) {
        jsonRepresentation = json;
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
     * @return JSON string.
     */
    public String toJson() {
        /*
         * TODO: Construct a JSON representation of this event.
         */
        return jsonRepresentation;
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
