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
import android.test.InstrumentationTestCase;

import com.salesforce.androidsdk.analytics.SalesforceAnalyticsManager;
import com.salesforce.androidsdk.analytics.model.DeviceAppAttributes;
import com.salesforce.androidsdk.analytics.model.InstrumentationEvent;
import com.salesforce.androidsdk.analytics.model.InstrumentationEventBuilder;

import java.util.UUID;

/**
 * Tests for EventStoreManager.
 *
 * @author bhariharan
 */
public class EventStoreManagerTest extends InstrumentationTestCase {

    private static final String TAG = "EventStoreManagerTest";
    private static final String TEST_FILENAME_SUFFIX = "_test_filename_suffix";
    private static final String TEST_ENCRYPTION_KEY = "test_encryption_key";
    private static final DeviceAppAttributes TEST_DEVICE_APP_ATTRIBUTES = new DeviceAppAttributes("TEST_APP_VERSION",
            "TEST_APP_NAME", "TEST_OS_VERSION", "TEST_OS_NAME", "TEST_NATIVE_APP_TYPE",
            "TEST_MOBILE_SDK_VERSION", "TEST_DEVICE_MODEL", "TEST_DEVICE_ID");

    private String uniqueId;
    private Context targetContext;
    private EventStoreManager storeManager;
    private InstrumentationEvent testEvent;
    private SalesforceAnalyticsManager analyticsManager;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        targetContext = getInstrumentation().getTargetContext();
        uniqueId = UUID.randomUUID().toString();
        analyticsManager = SalesforceAnalyticsManager.getInstance(uniqueId,
                targetContext, TEST_ENCRYPTION_KEY, TEST_DEVICE_APP_ATTRIBUTES);
        createTestEvent();
        storeManager = new EventStoreManager(TEST_FILENAME_SUFFIX, targetContext, TEST_ENCRYPTION_KEY);
    }

    @Override
    public void tearDown() throws Exception {
        storeManager.deleteAllEvents();
        testEvent = null;
        SalesforceAnalyticsManager.reset(uniqueId);
        super.tearDown();
    }

    public void testStoreOneEvent() throws Exception {
        // TODO:
    }

    public void testStoreMultipleEvents() throws Exception {
        // TODO:
    }

    public void testFetchOneEvent() throws Exception {
        // TODO:
    }

    public void testFetchAllEvents() throws Exception {
        // TODO:
    }

    public void testDeleteOneEvent() throws Exception {
        // TODO:
    }

    public void testDeleteMultipleEvents() throws Exception {
        // TODO:
    }

    public void testDeleteAllEvents() throws Exception {
        // TODO:
    }

    private void createTestEvent() throws Exception {
        final InstrumentationEventBuilder eventBuilder = InstrumentationEventBuilder.getInstance(analyticsManager, targetContext);
        eventBuilder.startTime(System.currentTimeMillis());
        eventBuilder.name("TEST_EVENT_NAME");
        eventBuilder.sessionId(1);
        eventBuilder.senderId("TEST_SENDER_ID");
        eventBuilder.eventType(InstrumentationEvent.EventType.error);
        eventBuilder.type(InstrumentationEvent.Type.system);
        eventBuilder.errorType(InstrumentationEvent.ErrorType.warn);
        testEvent = eventBuilder.buildEvent();
    }
}
