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
package com.salesforce.androidsdk.analytics;

import static org.junit.Assert.assertEquals;

import com.salesforce.androidsdk.analytics.model.InstrumentationEvent;
import com.salesforce.androidsdk.analytics.transform.AILTNTransform;
import com.salesforce.androidsdk.analytics.transform.Transform;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SalesforceAnalyticsManagerTest {
    SalesforceAnalyticsManager manager;

    @Before
    public void setup() {
        manager = SalesforceAnalyticsManager.getUnauthenticatedInstance();
        manager.removeRemotePublisher(AILTNTransform.class);
        manager.addRemotePublisher(TestTransform.class, TestPublisher.class);
        SalesforceAnalyticsManager.setEventPublishBatchSize(2);
    }

    @After
    public void teardown() {
        TestPublisher.reset();
        manager.removeRemotePublisher(TestTransform.class);
        manager.addRemotePublisher(AILTNTransform.class, AILTNPublisher.class);
    }

    @Test
    public void testBatchPublish() {
        storeEvents(3);
        // Sanity Check
        assertEquals(3, manager.getEventStoreManager().getNumStoredEvents());

        manager.publishAllEvents();

        assertEquals(0, manager.getEventStoreManager().getNumStoredEvents());
        assertEquals(2, TestPublisher.publishedEvents.size());
        assertEquals(2, TestPublisher.publishedEvents.get(0).length());
        assertEquals(1, TestPublisher.publishedEvents.get(1).length());
    }

    @Test
    public void testFailedBatchPublish() {
        storeEvents(3);
        // Sanity Check
        assertEquals(3, manager.getEventStoreManager().getNumStoredEvents());

        TestPublisher.publishSuccessResult = false;
        manager.publishAllEvents();

        assertEquals(3, manager.getEventStoreManager().getNumStoredEvents());
        assertEquals(1, TestPublisher.publishedEvents.size());
        assertEquals(2, TestPublisher.publishedEvents.get(0).length());
        manager.getEventStoreManager().deleteAllEvents();
    }

    private void storeEvents(int count) {
        for (int i = 0; i < count; i++) {
            try {
                manager.getEventStoreManager().storeEvent(new InstrumentationEvent(new JSONObject("{\"eventId\": \"" + i + "\"}")));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class TestTransform implements Transform {
        @Override
        public JSONObject transform(InstrumentationEvent event) {
            try {
                JSONObject result = new JSONObject();
                result.put("id", event.getEventId());
                return result;
            } catch (JSONException e) {
                return null;
            }
        }
    }

    public static class TestPublisher implements AnalyticsPublisher {
        protected static List<JSONArray> publishedEvents = new ArrayList<>();
        protected static boolean publishSuccessResult = true;
        @Override
        public boolean publish(JSONArray events) {
            publishedEvents.add(events);
            return publishSuccessResult;
        }

        public static void reset() {
            publishedEvents = new ArrayList<>();
            publishSuccessResult = true;
        }
    }
}
