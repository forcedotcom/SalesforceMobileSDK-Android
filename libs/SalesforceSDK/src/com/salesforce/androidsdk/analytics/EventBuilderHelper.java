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

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.analytics.model.InstrumentationEvent;
import com.salesforce.androidsdk.analytics.model.InstrumentationEventBuilder;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A simple helper class to facilitate creation of common types of events.
 */
public class EventBuilderHelper {

    private static final String TAG = "EventBuilderHelper";
    private static boolean enabled = true;

    // background executor
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(2);

    /**
     * This method allows event creation/storage to be disabled across the board.
     *
     * It is meant for tests.
     * It allows tests to be run individually.
     * When running individual tests in Android Studio the SalesforceSDKManager.init() is not called
     * As a result, if the test cause createAndStoreEvent to run, it will fail because
     * the call to UserAccountManager.getInstance() does a SalesforceSDKManager.getInstance().
     */
    public static void enableDisable(boolean b) {
        enabled = b;
    }

    /**
     * Creates and stores an analytics event with the supplied parameters.  By default all createAndStoreEvent's are placed
     * into a background thread pool for posting.
     *
     * @param name Event name.
     * @param userAccount User account.
     * @param className Class name or context where the event was generated.
     * @param attributes Addiitonal attributes.
     */
    public static void createAndStoreEvent(final String name, final UserAccount userAccount, final String className,
            final JSONObject attributes) {
        // Do nothing if not enabled
        if (!enabled)
            return;
        
        // don't run on background if this is a test run
        if (SalesforceSDKManager.getInstance().getIsTestRun()) {
            createAndStore(name, userAccount, className, attributes);
        } else {
            threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    createAndStore(name, userAccount, className, attributes);
                }
            });
        }
    }

    /**
     * Creates and stores an analytics event with the supplied parameters.
     *
     * @param name Event name.
     * @param userAccount User account.
     * @param className Class name or context where the event was generated.
     * @param attributes Addiitonal attributes.
     */
    public static void createAndStoreEventSync(String name, UserAccount userAccount, String className,
                                           JSONObject attributes) {
        createAndStore(name, userAccount, className, attributes);
    }

    private static void createAndStore(String name, UserAccount userAccount, String className,
            JSONObject attributes) {

        // Do nothing if not enabled
        if (!enabled)
            return;

        UserAccount account = userAccount;
        if (account == null) {
            account = UserAccountManager.getInstance().getCurrentUser();
        }
        if (account == null) {
            return;
        }
        final SalesforceAnalyticsManager manager = SalesforceAnalyticsManager.getInstance(account);
        final InstrumentationEventBuilder builder = InstrumentationEventBuilder.getInstance(manager.getAnalyticsManager(),
                                                                                            SalesforceSDKManager.getInstance().getAppContext());
        builder.name(name);
        builder.startTime(System.currentTimeMillis());
        final JSONObject page = new JSONObject();
        try {
            page.put("context", className);
        } catch (JSONException e) {
            SalesforceSDKLogger.e(TAG, "Exception thrown while building page object", e);
        }
        builder.page(page);
        if (attributes != null) {
            builder.attributes(attributes);
        }
        builder.schemaType(InstrumentationEvent.SchemaType.LightningInteraction);
        builder.eventType(InstrumentationEvent.EventType.system);
        try {
            final InstrumentationEvent event = builder.buildEvent();
            manager.getAnalyticsManager().getEventStoreManager().storeEvent(event);
        } catch (InstrumentationEventBuilder.EventBuilderException e) {
            SalesforceSDKLogger.e(TAG, "Exception thrown while building event", e);
        }
    }
}
