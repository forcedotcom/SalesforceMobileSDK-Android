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

import android.content.Context;
import android.test.InstrumentationTestCase;

import com.salesforce.androidsdk.analytics.manager.AnalyticsManager;
import com.salesforce.androidsdk.analytics.security.Encryptor;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tests for InstrumentationEvent.
 *
 * @author bhariharan
 */
public class InstrumentationEventTest extends InstrumentationTestCase {

    private static final String TEST_ENCRYPTION_KEY = Encryptor.hash("test_encryption_key", "key");
    private static final DeviceAppAttributes TEST_DEVICE_APP_ATTRIBUTES = new DeviceAppAttributes("TEST_APP_VERSION",
            "TEST_APP_NAME", "TEST_OS_VERSION", "TEST_OS_NAME", "TEST_NATIVE_APP_TYPE",
            "TEST_MOBILE_SDK_VERSION", "TEST_DEVICE_MODEL", "TEST_DEVICE_ID");
    private static final String TEST_EVENT_NAME = "TEST_EVENT_NAME_%s";
    private static final String TEST_SENDER_ID = "TEST_SENDER_ID";

    private String uniqueId;
    private Context targetContext;
    private AnalyticsManager analyticsManager;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        targetContext = getInstrumentation().getTargetContext();
        uniqueId = UUID.randomUUID().toString();
        analyticsManager = AnalyticsManager.getInstance(uniqueId,
                targetContext, TEST_ENCRYPTION_KEY, TEST_DEVICE_APP_ATTRIBUTES);
    }

    @Override
    public void tearDown() throws Exception {
        AnalyticsManager.reset(uniqueId);
        super.tearDown();
    }

    /**
     * Test for converting a simple map to JSON.
     *
     * @throws Exception
     */
    public void testSimpleMapToJsonConversion() throws Exception {
        final InstrumentationEventBuilder eventBuilder = createTestEventBuilder();
        final Map<String, Object> testMap = new HashMap<String, Object>();
        testMap.put("Key1", "Value1");
        testMap.put("Key2", "Value2");
        eventBuilder.attributes(testMap);
        final InstrumentationEvent event = eventBuilder.buildEvent();
        assertNotNull("Event instance should not be null", event);
        final JSONObject eventJson = event.toJson();
        assertNotNull("JSON representation of event should not be null", eventJson);
        final JSONObject attributesJson = eventJson.optJSONObject(InstrumentationEvent.ATTRIBUTES_KEY);
        assertNotNull("JSON representation of attributes should not be null", attributesJson);
        assertEquals("JSON representation of attributes should have 2 keys", 2, attributesJson.length());
        final String value1 = attributesJson.optString("Key1");
        assertEquals("JSON value should match map value", "Value1", value1);
        final String value2 = attributesJson.optString("Key2");
        assertEquals("JSON value should match map value", "Value2", value2);
    }

    /**
     * Test for converting a complex map to JSON.
     *
     * @throws Exception
     */
    public void testComplexMapToJsonConversion() throws Exception {
        final InstrumentationEventBuilder eventBuilder = createTestEventBuilder();
        final Map<String, Object> testMap = new HashMap<String, Object>();
        testMap.put("Key1", "Value1");
        final Map<String, Object> subMap = new HashMap<String, Object>();
        subMap.put("Key3", "Value3");
        subMap.put("Key4", 666);
        testMap.put("Key2", subMap);
        eventBuilder.attributes(testMap);
        final InstrumentationEvent event = eventBuilder.buildEvent();
        assertNotNull("Event instance should not be null", event);
        final JSONObject eventJson = event.toJson();
        assertNotNull("JSON representation of event should not be null", eventJson);
        final JSONObject attributesJson = eventJson.optJSONObject(InstrumentationEvent.ATTRIBUTES_KEY);
        assertNotNull("JSON representation of attributes should not be null", attributesJson);
        assertEquals("JSON representation of attributes should have 2 keys", 2, attributesJson.length());
        final String value1 = attributesJson.optString("Key1");
        assertEquals("JSON value should match map value", "Value1", value1);
        final JSONObject value2 = attributesJson.optJSONObject("Key2");
        assertNotNull("JSON value should not be null", value2);
        assertEquals("JSON value should have 2 keys", 2, value2.length());
        final String subValue1 = value2.optString("Key3");
        assertEquals("JSON value should match map value", "Value3", subValue1);
        int subValue2 = value2.optInt("Key4");
        assertEquals("JSON value should match map value", 666, subValue2);
    }

    /**
     * Test for converting a simple JSON to map.
     *
     * @throws Exception
     */
    public void testSimpleJsonToMapConversion() throws Exception {
        final JSONObject eventJson = new JSONObject();
        final JSONObject attributesJson = new JSONObject();
        attributesJson.put("Key1", "Value1");
        attributesJson.put("Key2", "Value2");
        eventJson.put(InstrumentationEvent.ATTRIBUTES_KEY, attributesJson);
        final InstrumentationEvent event = new InstrumentationEvent(eventJson);
        final Map<String, Object> attributes = event.getAttributes();
        assertNotNull("Map representation of attributes should not be null", attributes);
        assertEquals("Map representation of attributes should have 2 keys", 2, attributes.size());
        final Object value1 = attributes.get("Key1");
        assertNotNull("Value in map should not be null", value1);
        assertEquals("Map value should match JSON value", "Value1", (String) value1);
        final Object value2 = attributes.get("Key2");
        assertNotNull("Value in map should not be null", value2);
        assertEquals("Map value should match JSON value", "Value2", (String) value2);
    }

    /**
     * Test for converting a complex JSON to map.
     *
     * @throws Exception
     */
    public void testComplexJsonToMapConversion() throws Exception {
        final JSONObject eventJson = new JSONObject();
        final JSONObject attributesJson = new JSONObject();
        attributesJson.put("Key1", "Value1");
        final JSONObject subJson = new JSONObject();
        subJson.put("Key3", "Value3");
        subJson.put("Key4", 666);
        attributesJson.put("Key2", subJson);
        eventJson.put(InstrumentationEvent.ATTRIBUTES_KEY, attributesJson);
        final InstrumentationEvent event = new InstrumentationEvent(eventJson);
        final Map<String, Object> attributes = event.getAttributes();
        assertNotNull("Map representation of attributes should not be null", attributes);
        assertEquals("Map representation of attributes should have 2 keys", 2, attributes.size());
        final Object value1 = attributes.get("Key1");
        assertNotNull("Value in map should not be null", value1);
        assertEquals("Map value should match JSON value", "Value1", (String) value1);
        final Object value2 = attributes.get("Key2");
        assertNotNull("Value in map should not be null", value2);
        final Map<String, Object> value2Map = (Map<String, Object>) value2;
        assertEquals("Map should have 2 keys", 2, value2Map.size());
        final Object value3 = value2Map.get("Key3");
        assertNotNull("Value in map should not be null", value3);
        assertEquals("Map value should match JSON value", "Value3", (String) value3);
        final Object value4 = value2Map.get("Key4");
        assertNotNull("Value in map should not be null", value4);
        assertEquals("Map value should match JSON value", 666, (int) value4);
    }

    private InstrumentationEventBuilder createTestEventBuilder() throws Exception {
        final InstrumentationEventBuilder eventBuilder = InstrumentationEventBuilder.getInstance(analyticsManager, targetContext);
        long curTime = System.currentTimeMillis();
        final String eventName = String.format(TEST_EVENT_NAME, curTime);
        eventBuilder.startTime(curTime);
        eventBuilder.name(eventName);
        eventBuilder.sessionId(1);
        eventBuilder.senderId(TEST_SENDER_ID);
        eventBuilder.schemaType(InstrumentationEvent.SchemaType.error);
        eventBuilder.eventType(InstrumentationEvent.EventType.system);
        eventBuilder.errorType(InstrumentationEvent.ErrorType.warn);
        return eventBuilder;
    }
}
