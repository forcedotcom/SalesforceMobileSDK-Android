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

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;

import com.salesforce.androidsdk.analytics.manager.AnalyticsManager;

import org.json.JSONObject;

import java.util.UUID;

/**
 * Builder class that helps create a new InstrumentationEvent object.
 *
 * @author bhariharan
 */
public class InstrumentationEventBuilder {

    private AnalyticsManager analyticsManager;
    private Context context;
    private long startTime;
    private long endTime;
    private String name;
    private JSONObject attributes;
    private String sessionId;
    private String senderId;
    private JSONObject senderContext;
    private InstrumentationEvent.SchemaType schemaType;
    private InstrumentationEvent.EventType eventType;
    private InstrumentationEvent.ErrorType errorType;
    private String senderParentId;
    private long sessionStartTime;
    private JSONObject page;
    private JSONObject previousPage;
    private JSONObject marks;

    /**
     * Returns an instance of this class.
     *
     * @param analyticsManager Instance of AnalyticsManager.
     * @param context Context.
     * @return Instance of this class.
     */
    public static final InstrumentationEventBuilder getInstance(AnalyticsManager analyticsManager,
                                                                Context context) {
        return new InstrumentationEventBuilder(analyticsManager, context);
    }

    private InstrumentationEventBuilder(AnalyticsManager analyticsManager, Context context) {
        this.analyticsManager = analyticsManager;
        this.context = context;
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
     * Sets attributes.
     *
     * @param attributes Attributes.
     * @return Instance of this class.
     */
    public InstrumentationEventBuilder attributes(JSONObject attributes) {
        this.attributes = attributes;
        return this;
    }

    /**
     * Sets session ID.
     *
     * @param sessionId Session ID.
     * @return Instance of this class.
     */
    public InstrumentationEventBuilder sessionId(String sessionId) {
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
    public InstrumentationEventBuilder senderContext(JSONObject senderContext) {
        this.senderContext = senderContext;
        return this;
    }

    /**
     * Sets schema type.
     *
     * @param schemaType Schema type.
     * @return Instance of this class.
     */
    public InstrumentationEventBuilder schemaType(InstrumentationEvent.SchemaType schemaType) {
        this.schemaType = schemaType;
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
     * Sets sender parent ID.
     *
     * @param senderParentId Sender parent ID.
     * @return Instance of this class.
     */
    public InstrumentationEventBuilder senderParentId(String senderParentId) {
        this.senderParentId = senderParentId;
        return this;
    }

    /**
     * Sets session start time.
     *
     * @param sessionStartTime Session start time.
     * @return Instance of this class.
     */
    public InstrumentationEventBuilder sessionStartTime(long sessionStartTime) {
        this.sessionStartTime = sessionStartTime;
        return this;
    }

    /**
     * Sets page.
     *
     * @param page Page.
     * @return Instance of this class.
     */
    public InstrumentationEventBuilder page(JSONObject page) {
        this.page = page;
        return this;
    }

    /**
     * Sets previous page.
     *
     * @param previousPage Previous page.
     * @return Instance of this class.
     */
    public InstrumentationEventBuilder previousPage(JSONObject previousPage) {
        this.previousPage = previousPage;
        return this;
    }

    /**
     * Sets marks.
     *
     * @param marks Marks.
     * @return Instance of this class.
     */
    public InstrumentationEventBuilder marks(JSONObject marks) {
        this.marks = marks;
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
        final String eventId = UUID.randomUUID().toString();
        String errorMessage = null;
        if (schemaType == null) {
            errorMessage = "Mandatory field 'schema type' not set!";
        }
        if (TextUtils.isEmpty(name)) {
            errorMessage = "Mandatory field 'name' not set!";
        }
        final DeviceAppAttributes deviceAppAttributes = analyticsManager.getDeviceAppAttributes();
        if (deviceAppAttributes == null) {
            errorMessage = "Mandatory field 'device app attributes' not set!";
        }
        if ((schemaType == InstrumentationEvent.SchemaType.LightningInteraction
                || schemaType == InstrumentationEvent.SchemaType.LightningPerformance)
                && eventType == null) {
            errorMessage = "Mandatory field 'event type' not set!";
        }
        if (schemaType != InstrumentationEvent.SchemaType.LightningPerformance && page == null) {
            errorMessage = "Mandatory field 'page' not set!";
        }
        if (errorMessage != null) {
            throw new EventBuilderException(errorMessage);
        }
        int sequenceId = analyticsManager.getGlobalSequenceId() + 1;
        analyticsManager.setGlobalSequenceId(sequenceId);

        // Defaults to current time if not explicitly set.
        long curTime = System.currentTimeMillis();
        startTime = (startTime == 0) ? curTime : startTime;
        sessionStartTime = (sessionStartTime == 0) ? curTime : sessionStartTime;
        return new InstrumentationEvent(eventId, startTime, endTime, name, attributes, sessionId,
                sequenceId, senderId, senderContext, schemaType, eventType, errorType,
                deviceAppAttributes, getConnectionType(), senderParentId, sessionStartTime, page,
                previousPage, marks);
    }

    private String getConnectionType() {
        final StringBuilder connectionType = new StringBuilder();
        final ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            final NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            if (networkInfo != null) {
                final String type = networkInfo.getTypeName();
                final String subtype = networkInfo.getSubtypeName();
                if (!TextUtils.isEmpty(type)) {
                    connectionType.append(type);
                }
                if (!TextUtils.isEmpty(subtype)) {
                    connectionType.append(";");
                    connectionType.append(subtype);
                }
            }
        }
        return connectionType.toString();
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
