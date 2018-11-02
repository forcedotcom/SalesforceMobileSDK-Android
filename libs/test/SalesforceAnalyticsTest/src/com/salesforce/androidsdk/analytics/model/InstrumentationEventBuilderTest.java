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
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import android.text.TextUtils;
import android.util.Log;

import com.salesforce.androidsdk.analytics.manager.AnalyticsManager;
import com.salesforce.androidsdk.analytics.security.Encryptor;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

/**
 * Tests for InstrumentationEventBuilder.
 *
 * @author bhariharan
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class InstrumentationEventBuilderTest {

    private static final String TAG = "EventBuilderTest";
    private static final String TEST_ENCRYPTION_KEY = Encryptor.hash("test_encryption_key", "key");
    private static final DeviceAppAttributes TEST_DEVICE_APP_ATTRIBUTES = new DeviceAppAttributes("TEST_APP_VERSION",
            "TEST_APP_NAME", "TEST_OS_VERSION", "TEST_OS_NAME", "TEST_NATIVE_APP_TYPE",
            "TEST_MOBILE_SDK_VERSION", "TEST_DEVICE_MODEL", "TEST_DEVICE_ID", "TEST_CLIENT_ID");
    private static final String TEST_EVENT_NAME = "TEST_EVENT_NAME_%s";
    private static final String TEST_SENDER_ID = "TEST_SENDER_ID";
    private static final String TEST_SESSION_ID = "TEST_SESSION_ID";

    private String uniqueId;
    private Context targetContext;
    private AnalyticsManager analyticsManager;

    @Before
    public void setUp() throws Exception {
        targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        uniqueId = UUID.randomUUID().toString();
        analyticsManager = new AnalyticsManager(uniqueId,
                targetContext, TEST_ENCRYPTION_KEY, TEST_DEVICE_APP_ATTRIBUTES);
    }

    @After
    public void tearDown() throws Exception {
        analyticsManager.reset();
    }

    /**
     * Test for missing mandatory field 'schema type'.
     */
    @Test
    public void testMissingSchemaType() {
        final InstrumentationEventBuilder eventBuilder = InstrumentationEventBuilder.getInstance(analyticsManager, targetContext);
        long curTime = System.currentTimeMillis();
        final String eventName = String.format(TEST_EVENT_NAME, curTime);
        eventBuilder.startTime(curTime);
        eventBuilder.name(eventName);
        eventBuilder.sessionId(TEST_SESSION_ID);
        eventBuilder.senderId(TEST_SENDER_ID);
        eventBuilder.page(new JSONObject());
        eventBuilder.eventType(InstrumentationEvent.EventType.system);
        eventBuilder.errorType(InstrumentationEvent.ErrorType.warn);
        try {
            eventBuilder.buildEvent();
            Assert.fail("Exception should have been thrown for missing mandatory field 'schema type'");
        } catch (InstrumentationEventBuilder.EventBuilderException e) {
            Log.v(TAG, "Exception thrown as expected");
        }
    }

    /**
     * Test for missing mandatory field 'event type' in interaction event.
     */
    @Test
    public void testMissingEventTypeInInteraction() {
        final InstrumentationEventBuilder eventBuilder = InstrumentationEventBuilder.getInstance(analyticsManager, targetContext);
        long curTime = System.currentTimeMillis();
        final String eventName = String.format(TEST_EVENT_NAME, curTime);
        eventBuilder.schemaType(InstrumentationEvent.SchemaType.LightningInteraction);
        eventBuilder.startTime(curTime);
        eventBuilder.name(eventName);
        eventBuilder.sessionId(TEST_SESSION_ID);
        eventBuilder.page(new JSONObject());
        eventBuilder.senderId(TEST_SENDER_ID);
        try {
            eventBuilder.buildEvent();
            Assert.fail("Exception should have been thrown for missing mandatory field 'event type'");
        } catch (InstrumentationEventBuilder.EventBuilderException e) {
            Log.v(TAG, "Exception thrown as expected");
        }
    }

    /**
     * Test for missing optional field 'event type' in error event.
     */
    @Test
    public void testMissingEventTypeInError() {
        final InstrumentationEventBuilder eventBuilder = InstrumentationEventBuilder.getInstance(analyticsManager, targetContext);
        long curTime = System.currentTimeMillis();
        final String eventName = String.format(TEST_EVENT_NAME, curTime);
        eventBuilder.schemaType(InstrumentationEvent.SchemaType.LightningError);
        eventBuilder.startTime(curTime);
        eventBuilder.name(eventName);
        eventBuilder.sessionId(TEST_SESSION_ID);
        eventBuilder.page(new JSONObject());
        eventBuilder.senderId(TEST_SENDER_ID);
        InstrumentationEvent event = null;
        try {
            event = eventBuilder.buildEvent();
        } catch (InstrumentationEventBuilder.EventBuilderException e) {
            Assert.fail("Exception should not have been thrown");
        }
        Assert.assertNotNull("Event should not be null", event);
    }

    /**
     * Test for missing mandatory field 'page'.
     */
    @Test
    public void testMissingPage() {
        final InstrumentationEventBuilder eventBuilder = InstrumentationEventBuilder.getInstance(analyticsManager, targetContext);
        long curTime = System.currentTimeMillis();
        final String eventName = String.format(TEST_EVENT_NAME, curTime);
        eventBuilder.name(eventName);
        eventBuilder.startTime(curTime);
        eventBuilder.sessionId(TEST_SESSION_ID);
        eventBuilder.senderId(TEST_SENDER_ID);
        eventBuilder.schemaType(InstrumentationEvent.SchemaType.LightningError);
        eventBuilder.eventType(InstrumentationEvent.EventType.system);
        eventBuilder.errorType(InstrumentationEvent.ErrorType.warn);
        try {
            eventBuilder.buildEvent();
            Assert.fail("Exception should have been thrown for missing mandatory field 'page'");
        } catch (InstrumentationEventBuilder.EventBuilderException e) {
            Log.v(TAG, "Exception thrown as expected");
        }
    }

    /**
     * Test for missing mandatory field 'name'.
     */
    @Test
    public void testMissingName() {
        final InstrumentationEventBuilder eventBuilder = InstrumentationEventBuilder.getInstance(analyticsManager, targetContext);
        long curTime = System.currentTimeMillis();
        eventBuilder.startTime(curTime);
        eventBuilder.sessionId(TEST_SESSION_ID);
        eventBuilder.senderId(TEST_SENDER_ID);
        eventBuilder.schemaType(InstrumentationEvent.SchemaType.LightningError);
        eventBuilder.eventType(InstrumentationEvent.EventType.system);
        eventBuilder.errorType(InstrumentationEvent.ErrorType.warn);
        eventBuilder.page(new JSONObject());
        try {
            eventBuilder.buildEvent();
            Assert.fail("Exception should have been thrown for missing mandatory field 'name'");
        } catch (InstrumentationEventBuilder.EventBuilderException e) {
            Log.v(TAG, "Exception thrown as expected");
        }
    }

    /**
     * Test for missing mandatory field 'device app attributes'.
     */
    @Test
    public void testMissingDeviceAppAttributes() {
        analyticsManager.reset();
        analyticsManager = new AnalyticsManager(uniqueId, targetContext, TEST_ENCRYPTION_KEY, null);
        final InstrumentationEventBuilder eventBuilder = InstrumentationEventBuilder.getInstance(analyticsManager, targetContext);
        long curTime = System.currentTimeMillis();
        final String eventName = String.format(TEST_EVENT_NAME, curTime);
        eventBuilder.startTime(curTime);
        eventBuilder.name(eventName);
        eventBuilder.sessionId(TEST_SESSION_ID);
        eventBuilder.senderId(TEST_SENDER_ID);
        eventBuilder.schemaType(InstrumentationEvent.SchemaType.LightningError);
        eventBuilder.eventType(InstrumentationEvent.EventType.system);
        eventBuilder.errorType(InstrumentationEvent.ErrorType.warn);
        eventBuilder.page(new JSONObject());
        try {
            eventBuilder.buildEvent();
            Assert.fail("Exception should have been thrown for missing mandatory field 'device app attributes'");
        } catch (InstrumentationEventBuilder.EventBuilderException e) {
            Log.v(TAG, "Exception thrown as expected");
        } finally {
            analyticsManager.reset();
        }
    }

    /**
     * Test for auto population of mandatory field 'start time'.
     *
     * @throws Exception
     */
    @Test
    public void testAutoPopulateStartTime() throws Exception {
        final InstrumentationEventBuilder eventBuilder = InstrumentationEventBuilder.getInstance(analyticsManager, targetContext);
        long curTime = System.currentTimeMillis();
        final String eventName = String.format(TEST_EVENT_NAME, curTime);
        eventBuilder.name(eventName);
        eventBuilder.sessionId(TEST_SESSION_ID);
        eventBuilder.senderId(TEST_SENDER_ID);
        eventBuilder.schemaType(InstrumentationEvent.SchemaType.LightningError);
        eventBuilder.eventType(InstrumentationEvent.EventType.system);
        eventBuilder.errorType(InstrumentationEvent.ErrorType.warn);
        eventBuilder.page(new JSONObject());
        final InstrumentationEvent event = eventBuilder.buildEvent();
        long startTime = event.getStartTime();
        Assert.assertTrue("Start time should have been auto populated", startTime > 0);
    }

    /**
     * Test for auto population of mandatory field 'event ID'.
     *
     * @throws Exception
     */
    @Test
    public void testAutoPopulateEventId() throws Exception {
        final InstrumentationEventBuilder eventBuilder = InstrumentationEventBuilder.getInstance(analyticsManager, targetContext);
        long curTime = System.currentTimeMillis();
        final String eventName = String.format(TEST_EVENT_NAME, curTime);
        eventBuilder.startTime(curTime);
        eventBuilder.name(eventName);
        eventBuilder.sessionId(TEST_SESSION_ID);
        eventBuilder.senderId(TEST_SENDER_ID);
        eventBuilder.schemaType(InstrumentationEvent.SchemaType.LightningError);
        eventBuilder.eventType(InstrumentationEvent.EventType.system);
        eventBuilder.errorType(InstrumentationEvent.ErrorType.warn);
        eventBuilder.page(new JSONObject());
        final InstrumentationEvent event = eventBuilder.buildEvent();
        final String eventId = event.getEventId();
        Assert.assertFalse("Event ID should have been auto populated", TextUtils.isEmpty(eventId));
    }

    /**
     * Test for auto population of mandatory field 'sequence ID'.
     *
     * @throws Exception
     */
    @Test
    public void testAutoPopulateSequenceId() throws Exception {
        final InstrumentationEventBuilder eventBuilder = InstrumentationEventBuilder.getInstance(analyticsManager, targetContext);
        long curTime = System.currentTimeMillis();
        final String eventName = String.format(TEST_EVENT_NAME, curTime);
        eventBuilder.startTime(curTime);
        eventBuilder.name(eventName);
        eventBuilder.sessionId(TEST_SESSION_ID);
        eventBuilder.senderId(TEST_SENDER_ID);
        eventBuilder.schemaType(InstrumentationEvent.SchemaType.LightningError);
        eventBuilder.eventType(InstrumentationEvent.EventType.system);
        eventBuilder.errorType(InstrumentationEvent.ErrorType.warn);
        eventBuilder.page(new JSONObject());
        final InstrumentationEvent event = eventBuilder.buildEvent();
        int sequenceId = event.getSequenceId();
        Assert.assertTrue("Sequence ID should have been auto populated", sequenceId > 0);
        int globalSequenceId = analyticsManager.getGlobalSequenceId();
        Assert.assertEquals("Global sequence ID should have been updated", 0, globalSequenceId - sequenceId);
    }
}
