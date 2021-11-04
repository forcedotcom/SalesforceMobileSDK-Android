package com.salesforce.androidsdk.analytics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

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
