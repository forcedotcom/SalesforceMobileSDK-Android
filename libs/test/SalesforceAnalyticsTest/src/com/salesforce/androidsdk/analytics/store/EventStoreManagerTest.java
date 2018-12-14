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
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.salesforce.androidsdk.analytics.manager.AnalyticsManager;
import com.salesforce.androidsdk.analytics.model.DeviceAppAttributes;
import com.salesforce.androidsdk.analytics.model.InstrumentationEvent;
import com.salesforce.androidsdk.analytics.model.InstrumentationEventBuilder;
import com.salesforce.androidsdk.analytics.security.Encryptor;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tests for EventStoreManager.
 *
 * @author bhariharan
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class EventStoreManagerTest {

    private static final String TEST_FILENAME_SUFFIX = "_test_filename_suffix";
    private static final String TEST_ENCRYPTION_KEY = Encryptor.hash("test_encryption_key", "key");
    private static final DeviceAppAttributes TEST_DEVICE_APP_ATTRIBUTES = new DeviceAppAttributes("TEST_APP_VERSION",
            "TEST_APP_NAME", "TEST_OS_VERSION", "TEST_OS_NAME", "TEST_NATIVE_APP_TYPE",
            "TEST_MOBILE_SDK_VERSION", "TEST_DEVICE_MODEL", "TEST_DEVICE_ID", "TEST_CLIENT_ID");
    private static final String TEST_EVENT_NAME = "TEST_EVENT_NAME_%s";
    private static final String TEST_SENDER_ID = "TEST_SENDER_ID";
    private static final String TEST_SESSION_ID = "TEST_SESSION_ID";

    private Context targetContext;
    private EventStoreManager storeManager;
    private AnalyticsManager analyticsManager;

    @Before
    public void setUp() throws Exception {
        targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final String uniqueId = UUID.randomUUID().toString();
        analyticsManager = new AnalyticsManager(uniqueId,
                targetContext, TEST_ENCRYPTION_KEY, TEST_DEVICE_APP_ATTRIBUTES);
        storeManager = new EventStoreManager(TEST_FILENAME_SUFFIX, targetContext, TEST_ENCRYPTION_KEY);
    }

    @After
    public void tearDown() throws Exception {
        storeManager.deleteAllEvents();
        analyticsManager.reset();
    }

    /**
     * Test for storing one event and retrieving it.
     *
     * @throws Exception
     */
    @Test
    public void testStoreOneEvent() throws Exception {
        final InstrumentationEvent event = createTestEvent();
        Assert.assertNotNull("Generated event stored should not be null", event);
        storeManager.storeEvent(event);
        final List<InstrumentationEvent> events = storeManager.fetchAllEvents();
        Assert.assertNotNull("List of events stored should not be null", events);
        Assert.assertEquals("Number of events stored should be 1", 1, events.size());
        Assert.assertTrue("Stored event should be the same as generated event", event.equals(events.get(0)));
    }

    /**
     * Test for storing many events and retrieving them.
     *
     * @throws Exception
     */
    @Test
    public void testStoreMultipleEvents() throws Exception {
        final InstrumentationEvent event1 = createTestEvent();
        Assert.assertNotNull("Generated event stored should not be null", event1);
        final InstrumentationEvent event2 = createTestEvent();
        Assert.assertNotNull("Generated event stored should not be null", event2);
        final List<InstrumentationEvent> genEvents = new ArrayList<InstrumentationEvent>();
        genEvents.add(event1);
        genEvents.add(event2);
        storeManager.storeEvents(genEvents);
        final List<InstrumentationEvent> events = storeManager.fetchAllEvents();
        Assert.assertNotNull("List of events stored should not be null", events);
        Assert.assertEquals("Number of events stored should be 2", 2, events.size());
        Assert.assertTrue("Stored event should be the same as generated event", event1.equals(events.get(0)));
        Assert.assertTrue("Stored event should be the same as generated event", event2.equals(events.get(1)));
    }

    /**
     * Test for fetching one event by specifying event ID.
     *
     * @throws Exception
     */
    @Test
    public void testFetchOneEvent() throws Exception {
        final InstrumentationEvent event = createTestEvent();
        Assert.assertNotNull("Generated event stored should not be null", event);
        final String eventId = event.getEventId();
        storeManager.storeEvent(event);
        final InstrumentationEvent storedEvent = storeManager.fetchEvent(eventId);
        Assert.assertNotNull("Event stored should not be null", storedEvent);
        Assert.assertTrue("Stored event should be the same as generated event", event.equals(storedEvent));
    }

    /**
     * Test for fetching all stored events.
     *
     * @throws Exception
     */
    @Test
    public void testFetchAllEvents() throws Exception {
        final InstrumentationEvent event1 = createTestEvent();
        Assert.assertNotNull("Generated event stored should not be null", event1);
        storeManager.storeEvent(event1);
        final InstrumentationEvent event2 = createTestEvent();
        Assert.assertNotNull("Generated event stored should not be null", event2);
        storeManager.storeEvent(event2);
        final List<InstrumentationEvent> events = storeManager.fetchAllEvents();
        Assert.assertNotNull("List of events stored should not be null", events);
        Assert.assertEquals("Number of events stored should be 2", 2, events.size());
        Assert.assertTrue("Stored event should be the same as generated event", event1.equals(events.get(0)));
        Assert.assertTrue("Stored event should be the same as generated event", event2.equals(events.get(1)));
    }

    /**
     * Test for deleting one event by specifying event ID.
     *
     * @throws Exception
     */
    @Test
    public void testDeleteOneEvent() throws Exception {
        final InstrumentationEvent event = createTestEvent();
        Assert.assertNotNull("Generated event stored should not be null", event);
        final String eventId = event.getEventId();
        storeManager.storeEvent(event);
        final List<InstrumentationEvent> eventsBeforeDel = storeManager.fetchAllEvents();
        Assert.assertNotNull("List of events stored should not be null", eventsBeforeDel);
        Assert.assertEquals("Number of events stored should be 1", 1, eventsBeforeDel.size());
        Assert.assertTrue("Stored event should be the same as generated event", event.equals(eventsBeforeDel.get(0)));
        storeManager.deleteEvent(eventId);
        final List<InstrumentationEvent> eventsAfterDel = storeManager.fetchAllEvents();
        Assert.assertNotNull("List of events stored should not be null", eventsAfterDel);
        Assert.assertEquals("Number of events stored should be 0", 0, eventsAfterDel.size());
    }

    /**
     * Test for deleting multiple events by specifying event IDs.
     *
     * @throws Exception
     */
    @Test
    public void testDeleteMultipleEvents() throws Exception {
        final InstrumentationEvent event1 = createTestEvent();
        Assert.assertNotNull("Generated event stored should not be null", event1);
        final String eventId1 = event1.getEventId();
        final InstrumentationEvent event2 = createTestEvent();
        Assert.assertNotNull("Generated event stored should not be null", event2);
        final String eventId2 = event2.getEventId();
        final List<InstrumentationEvent> genEvents = new ArrayList<InstrumentationEvent>();
        genEvents.add(event1);
        genEvents.add(event2);
        storeManager.storeEvents(genEvents);
        final List<InstrumentationEvent> eventsBeforeDel = storeManager.fetchAllEvents();
        Assert.assertNotNull("List of events stored should not be null", eventsBeforeDel);
        Assert.assertEquals("Number of events stored should be 2", 2, eventsBeforeDel.size());
        Assert.assertTrue("Stored event should be the same as generated event", event1.equals(eventsBeforeDel.get(0)));
        Assert.assertTrue("Stored event should be the same as generated event", event2.equals(eventsBeforeDel.get(1)));
        final List<String> eventIds = new ArrayList<String>();
        eventIds.add(eventId1);
        eventIds.add(eventId2);
        storeManager.deleteEvents(eventIds);
        final List<InstrumentationEvent> eventsAfterDel = storeManager.fetchAllEvents();
        Assert.assertNotNull("List of events stored should not be null", eventsAfterDel);
        Assert.assertEquals("Number of events stored should be 0", 0, eventsAfterDel.size());
    }

    /**
     * Test for deleting all events stored.
     *
     * @throws Exception
     */
    @Test
    public void testDeleteAllEvents() throws Exception {
        final InstrumentationEvent event1 = createTestEvent();
        Assert.assertNotNull("Generated event stored should not be null", event1);
        final InstrumentationEvent event2 = createTestEvent();
        Assert.assertNotNull("Generated event stored should not be null", event2);
        final List<InstrumentationEvent> genEvents = new ArrayList<InstrumentationEvent>();
        genEvents.add(event1);
        genEvents.add(event2);
        storeManager.storeEvents(genEvents);
        final List<InstrumentationEvent> eventsBeforeDel = storeManager.fetchAllEvents();
        Assert.assertNotNull("List of events stored should not be null", eventsBeforeDel);
        Assert.assertEquals("Number of events stored should be 2", 2, eventsBeforeDel.size());
        Assert.assertTrue("Stored event should be the same as generated event", event1.equals(eventsBeforeDel.get(0)));
        Assert.assertTrue("Stored event should be the same as generated event", event2.equals(eventsBeforeDel.get(1)));
        storeManager.deleteAllEvents();
        final List<InstrumentationEvent> eventsAfterDel = storeManager.fetchAllEvents();
        Assert.assertNotNull("List of events stored should not be null", eventsAfterDel);
        Assert.assertEquals("Number of events stored should be 0", 0, eventsAfterDel.size());
    }

    /**
     * Test for disabling logging.
     *
     * @throws Exception
     */
    @Test
    public void testDisablingLogging() throws Exception {
        final InstrumentationEvent event = createTestEvent();
        Assert.assertNotNull("Generated event stored should not be null", event);
        storeManager.enableLogging(false);
        storeManager.storeEvent(event);
        final List<InstrumentationEvent> events = storeManager.fetchAllEvents();
        Assert.assertNotNull("List of events stored should not be null", events);
        Assert.assertEquals("Number of events stored should be 0", 0, events.size());
    }

    /**
     * Test for enabling logging.
     *
     * @throws Exception
     */
    @Test
    public void testEnablingLogging() throws Exception {
        final InstrumentationEvent event = createTestEvent();
        Assert.assertNotNull("Generated event stored should not be null", event);
        storeManager.enableLogging(false);
        storeManager.storeEvent(event);
        List<InstrumentationEvent> events = storeManager.fetchAllEvents();
        Assert.assertNotNull("List of events stored should not be null", events);
        Assert.assertEquals("Number of events stored should be 0", 0, events.size());
        storeManager.enableLogging(true);
        storeManager.storeEvent(event);
        events = storeManager.fetchAllEvents();
        Assert.assertNotNull("List of events stored should not be null", events);
        Assert.assertEquals("Number of events stored should be 1", 1, events.size());
        Assert.assertTrue("Stored event should be the same as generated event", event.equals(events.get(0)));
    }

    /**
     * Test for event limit exceeded.
     *
     * @throws Exception
     */
    @Test
    public void testEventLimitExceeded() throws Exception {
        final InstrumentationEvent event = createTestEvent();
        Assert.assertNotNull("Generated event stored should not be null", event);
        storeManager.setMaxEvents(0);
        storeManager.storeEvent(event);
        final List<InstrumentationEvent> events = storeManager.fetchAllEvents();
        Assert.assertNotNull("List of events stored should not be null", events);
        Assert.assertEquals("Number of events stored should be 0", 0, events.size());
    }

    /**
     * Test for event limit not exceeded.
     *
     * @throws Exception
     */
    @Test
    public void testEventLimitNotExceeded() throws Exception {
        final InstrumentationEvent event = createTestEvent();
        Assert.assertNotNull("Generated event stored should not be null", event);
        storeManager.setMaxEvents(0);
        storeManager.storeEvent(event);
        List<InstrumentationEvent> events = storeManager.fetchAllEvents();
        Assert.assertNotNull("List of events stored should not be null", events);
        Assert.assertEquals("Number of events stored should be 0", 0, events.size());
        storeManager.setMaxEvents(1);
        storeManager.storeEvent(event);
        events = storeManager.fetchAllEvents();
        Assert.assertNotNull("List of events stored should not be null", events);
        Assert.assertEquals("Number of events stored should be 1", 1, events.size());
        Assert.assertTrue("Stored event should be the same as generated event", event.equals(events.get(0)));
    }

    private InstrumentationEvent createTestEvent() throws Exception {
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
        return eventBuilder.buildEvent();
    }
}
