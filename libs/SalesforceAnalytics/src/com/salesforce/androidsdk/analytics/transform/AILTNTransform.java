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
package com.salesforce.androidsdk.analytics.transform;

import android.text.TextUtils;

import com.salesforce.androidsdk.analytics.model.DeviceAppAttributes;
import com.salesforce.androidsdk.analytics.model.InstrumentationEvent;
import com.salesforce.androidsdk.analytics.util.SalesforceAnalyticsLogger;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents a transformation of generic event to the AILTN format.
 *
 * @author bhariharan
 */
public class AILTNTransform implements Transform {

    private static final String TAG = "AILTNTransform";
    private static final String CONNECTION_TYPE_KEY = "connectionType";
    private static final String VERSION_KEY = "version";
    private static final String VERSION_VALUE = "0.2";
    private static final String SCHEMA_TYPE_KEY = "schemaType";
    private static final String ID_KEY = "id";
    private static final String EVENT_SOURCE_KEY = "eventSource";
    private static final String TS_KEY = "ts";
    private static final String PAGE_START_TIME_KEY = "pageStartTime";
    private static final String DURATION_KEY = "duration";
    private static final String EPT_KEY = "ept";
    private static final String CLIENT_SESSION_ID_KEY = "clientSessionId";
    private static final String SEQUENCE_KEY = "sequence";
    private static final String ATTRIBUTES_KEY = "attributes";
    private static final String LOCATOR_KEY = "locator";
    private static final String PAGE_KEY = "page";
    private static final String PREVIOUS_PAGE_KEY = "previousPage";
    private static final String MARKS_KEY = "marks";
    private static final String EVENT_TYPE_KEY = "eventType";
    private static final String ERROR_TYPE_KEY = "errorType";
    private static final String TARGET_KEY = "target";
    private static final String SCOPE_KEY = "scope";
    private static final String CONTEXT_KEY = "context";
    private static final String DEVICE_ATTRIBUTES_KEY = "deviceAttributes";
    private static final String PERF_EVENT_TYPE = "defs";

    @Override
    public JSONObject transform(InstrumentationEvent event) {
        if (event == null) {
            return null;
        }
        JSONObject logLine = buildPayload(event);
        try {
            if (logLine != null) {
                logLine.put(DEVICE_ATTRIBUTES_KEY, buildDeviceAttributes(event));
            }
        } catch (JSONException e) {
            logLine = null;
            SalesforceAnalyticsLogger.e(null, TAG, "Exception occurred while transforming JSON", e);
        }
        return logLine;
    }

    private JSONObject buildDeviceAttributes(InstrumentationEvent event) {
        JSONObject deviceAttributes = new JSONObject();
        try {
            final DeviceAppAttributes deviceAppAttributes = event.getDeviceAppAttributes();
            if (deviceAppAttributes != null) {
                deviceAttributes = deviceAppAttributes.toJson();
            }
            deviceAttributes.put(CONNECTION_TYPE_KEY, event.getConnectionType());
        } catch (JSONException e) {
            SalesforceAnalyticsLogger.e(null, TAG, "Exception occurred while transforming JSON", e);
        }
        return deviceAttributes;
    }

    private JSONObject buildPayload(InstrumentationEvent event) {
        JSONObject payload = new JSONObject();
        try {
            payload.put(VERSION_KEY, VERSION_VALUE);
            final InstrumentationEvent.SchemaType schemaType = event.getSchemaType();
            payload.put(SCHEMA_TYPE_KEY, schemaType.name());
            payload.put(ID_KEY, event.getEventId());
            payload.put(EVENT_SOURCE_KEY, event.getName());
            long startTime = event.getStartTime();
            payload.put(TS_KEY, startTime);
            payload.put(PAGE_START_TIME_KEY, event.getSessionStartTime());
            long endTime = event.getEndTime();
            long duration = endTime - startTime;
            if (duration > 0) {
                if (schemaType == InstrumentationEvent.SchemaType.LightningInteraction
                        || schemaType == InstrumentationEvent.SchemaType.LightningPerformance) {
                    payload.put(DURATION_KEY, duration);
                } else if (schemaType == InstrumentationEvent.SchemaType.LightningPageView) {
                    payload.put(EPT_KEY, duration);
                }
            }
            final String sessionId = event.getSessionId();
            if (!TextUtils.isEmpty(sessionId)) {
                payload.put(CLIENT_SESSION_ID_KEY, sessionId);
            }
            if (schemaType != InstrumentationEvent.SchemaType.LightningPerformance) {
                payload.put(SEQUENCE_KEY, event.getSequenceId());
            }
            final JSONObject attributes = event.getAttributes();
            if (attributes != null) {
                payload.put(ATTRIBUTES_KEY, attributes);
            }
            if (schemaType != InstrumentationEvent.SchemaType.LightningPerformance) {
                payload.put(PAGE_KEY, event.getPage());
            }
            final JSONObject previousPage = event.getPreviousPage();
            if (previousPage != null && schemaType == InstrumentationEvent.SchemaType.LightningPageView) {
                payload.put(PREVIOUS_PAGE_KEY, previousPage);
            }
            final JSONObject marks = event.getMarks();
            if (marks != null && (schemaType == InstrumentationEvent.SchemaType.LightningPageView
                    || schemaType == InstrumentationEvent.SchemaType.LightningPerformance)) {
                payload.put(MARKS_KEY, marks);
            }
            if (schemaType == InstrumentationEvent.SchemaType.LightningInteraction
                    || schemaType == InstrumentationEvent.SchemaType.LightningPageView) {
                final JSONObject locator = buildLocator(event);
                if (locator != null) {
                    payload.put(LOCATOR_KEY, locator);
                }
            }
            final InstrumentationEvent.EventType eventType = event.getEventType();
            String eventTypeString = null;
            if (schemaType == InstrumentationEvent.SchemaType.LightningPerformance) {
                eventTypeString = PERF_EVENT_TYPE;
            } else if (schemaType == InstrumentationEvent.SchemaType.LightningInteraction
                    && eventType != null) {
                eventTypeString = eventType.name();
            }
            if (!TextUtils.isEmpty(eventTypeString)) {
                payload.put(EVENT_TYPE_KEY, eventTypeString);
            }
            final InstrumentationEvent.ErrorType errorType = event.getErrorType();
            if (errorType != null && schemaType == InstrumentationEvent.SchemaType.LightningError) {
                payload.put(ERROR_TYPE_KEY, errorType.name());
            }
        } catch (JSONException e) {
            payload = null;
            SalesforceAnalyticsLogger.e(null, TAG, "Exception occurred while transforming JSON", e);
        }
        return payload;
    }

    private JSONObject buildLocator(InstrumentationEvent event) {
        JSONObject locator = new JSONObject();
        try {
            final String senderId = event.getSenderId();
            final String senderParentId = event.getSenderParentId();
            if (TextUtils.isEmpty(senderId) || TextUtils.isEmpty(senderParentId)) {
                return null;
            }
            locator.put(TARGET_KEY, senderId);
            locator.put(SCOPE_KEY, senderParentId);
            final JSONObject senderContext = event.getSenderContext();
            if (senderContext != null) {
                locator.put(CONTEXT_KEY, senderContext);
            }
        } catch (JSONException e) {
            locator = null;
            SalesforceAnalyticsLogger.e(null, TAG, "Exception occurred while transforming JSON", e);
        }
        return locator;
    }
}
