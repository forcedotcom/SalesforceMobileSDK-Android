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

import java.util.Map;

/**
 * Builder class that helps create a new InstrumentationEvent object.
 *
 * @author bhariharan
 */
public class InstrumentationEventBuilder {

    private long startTime;
    private long endTime;
    private String name;
    private Map<String, Object> attributes;
    private int sessionId;
    private String senderId;
    private Map<String, Object> senderContext;
    private InstrumentationEvent.EventType eventType;
    private InstrumentationEvent.Type type;
    private InstrumentationEvent.Subtype subtype;
    private InstrumentationEvent.ErrorType errorType;

    /**
     * Returns an instance of this class.
     *
     * @return Instance of this class.
     */
    public static final InstrumentationEventBuilder getInstance() {
        return new InstrumentationEventBuilder();
    }

    /**
     * Sets start time.
     *
     * @param startTime Start time.
     * @return Instance of this class.
     */
    public InstrumentationEventBuilder startTime(long startTime) {
        this.startTime = startTime;
        return this;
    }

    /**
     * Sets end time.
     *
     * @param endTime End time.
     * @return Instance of this class.
     */
    public InstrumentationEventBuilder endTime(long endTime) {
        this.endTime = endTime;
        return this;
    }

    /**
     * Sets name.
     *
     * @param name Name.
     * @return Instance of this class.
     */
    public InstrumentationEventBuilder name(String name) {
        this.name = name;
        return this;
    }

    /**
     * Sets attributed.
     *
     * @param attributes Attributes.
     * @return Instance of this class.
     */
    public InstrumentationEventBuilder attributes(Map<String, Object> attributes) {
        this.attributes = attributes;
        return this;
    }

    /**
     * Sets session ID.
     *
     * @param sessionId Session ID.
     * @return Instance of this class.
     */
    public InstrumentationEventBuilder sessionId(int sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    /**
     * Sets sender ID.
     *
     * @param senderId Sender ID.
     * @return Instance of this class.
     */
    public InstrumentationEventBuilder senderId(String senderId) {
        this.senderId = senderId;
        return this;
    }

    /**
     * Sets sender context.
     *
     * @param senderContext Sender context.
     * @return Instance of this class.
     */
    public InstrumentationEventBuilder senderContext(Map<String, Object> senderContext) {
        this.senderContext = senderContext;
        return this;
    }

    /**
     * Sets event type.
     *
     * @param eventType Event type.
     * @return Instance of this class.
     */
    public InstrumentationEventBuilder eventType(InstrumentationEvent.EventType eventType) {
        this.eventType = eventType;
        return this;
    }

    /**
     * Sets type.
     *
     * @param type Type.
     * @return Instance of this class.
     */
    public InstrumentationEventBuilder type(InstrumentationEvent.Type type) {
        this.type = type;
        return this;
    }

    /**
     * Sets subtype.
     *
     * @param subtype Subtype.
     * @return Instance of this class.
     */
    public InstrumentationEventBuilder subtype(InstrumentationEvent.Subtype subtype) {
        this.subtype = subtype;
        return this;
    }

    /**
     * Sets error type.
     *
     * @param errorType Error type.
     * @return Instance of this class.
     */
    public InstrumentationEventBuilder errorType(InstrumentationEvent.ErrorType errorType) {
        this.errorType = errorType;
        return this;
    }

    /**
     * Validates and builds an InstrumentationEvent object. Throws EventBuilderException
     * if mandatory fields are not set.
     *
     * @return InstrumentationEvent object.
     * @throws EventBuilderException
     */
    public InstrumentationEvent buildEvent() throws EventBuilderException {

        /*
         * TODO: Generate unique eventId every time using UUID.
         */
        final String eventId = null;
        String errorMessage = null;
        if (eventType == null) {
            errorMessage = "Mandatory field 'event type' not set!";
        }
        if (TextUtils.isEmpty(name)) {
            errorMessage = "Mandatory field 'name' not set!";
        }

        /*
         * TODO: Fetch deviceAppAttributes and set them (should be set when library is initialized).
         */
        final DeviceAppAttributes deviceAppAttributes = null;
        if (deviceAppAttributes == null) {
            errorMessage = "Mandatory field 'device app attributes' not set!";
        }
        if (errorMessage != null) {
            throw new EventBuilderException(errorMessage);
        }

        /*
         * TODO: Increment global sequenceId every time (in memory counter for this library).
         */
        int sequenceId = 0;
        return new InstrumentationEvent(eventId, startTime, endTime, name, attributes, sessionId,
                sequenceId, senderId, senderContext, eventType, type, subtype, errorType,
                deviceAppAttributes, getConnectionType());
    }

    private String getConnectionType() {

        /*
         * TODO: Get connection type (3G/Wifi/LTE).
         */
        return null;
    }

    /**
     * Exception thrown if the event can not be built.
     */
    public static class EventBuilderException extends Exception {

        private static final long serialVersionUID = 1L;

        public EventBuilderException(String message) {
            super(message);
        }
    }
}
