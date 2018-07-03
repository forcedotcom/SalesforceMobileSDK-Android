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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.analytics.manager.AnalyticsManager;
import com.salesforce.androidsdk.analytics.model.DeviceAppAttributes;
import com.salesforce.androidsdk.analytics.model.InstrumentationEvent;
import com.salesforce.androidsdk.analytics.store.EventStoreManager;
import com.salesforce.androidsdk.analytics.transform.AILTNTransform;
import com.salesforce.androidsdk.analytics.transform.Transform;
import com.salesforce.androidsdk.app.Features;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.config.AdminSettingsManager;
import com.salesforce.androidsdk.config.BootConfig;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * This class contains APIs that can be used to interact with
 * the SalesforceAnalytics library.
 *
 * @author bhariharan
 */
public class SalesforceAnalyticsManager {

    private static final String ANALYTICS_ON_OFF_KEY = "ailtn_enabled";
    private static final String AILTN_POLICY_PREF = "ailtn_policy";
    private static final int DEFAULT_PUBLISH_FREQUENCY_IN_HOURS = 8;
    private static final String TAG = "AnalyticsManager";
    private static final String UNAUTH_INSTANCE_KEY = "_no_user";

    private static Map<String, SalesforceAnalyticsManager> INSTANCES;
    private static boolean sPublishHandlerActive;
    private static ScheduledFuture sScheduler;
    private static int sPublishFrequencyInHours = DEFAULT_PUBLISH_FREQUENCY_IN_HOURS;

    private AnalyticsManager analyticsManager;
    private EventStoreManager eventStoreManager;
    private UserAccount account;
    private boolean enabled;
    private Map<Class<? extends Transform>, Class<? extends AnalyticsPublisher>> remotes;

    /**
     * Returns the instance of this class associated with an unauthenticated user context.
     *
     * @return Instance of this class.
     */
    public static synchronized SalesforceAnalyticsManager getUnauthenticatedInstance() {
        return getInstance(null);
    }

    /**
     * Returns the instance of this class associated with this user account.
     *
     * @param account User account.
     * @return Instance of this class.
     */
    public static synchronized SalesforceAnalyticsManager getInstance(UserAccount account) {
        return getInstance(account, null);
    }

    /**
     * Returns the instance of this class associated with this user and community.
     *
     * @param account User account.
     * @param communityId Community ID.
     * @return Instance of this class.
     */
    public static synchronized SalesforceAnalyticsManager getInstance(UserAccount account, String communityId) {
        String uniqueId = UNAUTH_INSTANCE_KEY;
        if (account != null) {
            uniqueId = account.getUserId();
            if (UserAccount.INTERNAL_COMMUNITY_ID.equals(communityId)) {
                communityId = null;
            }
            if (!TextUtils.isEmpty(communityId)) {
                uniqueId = uniqueId + communityId;
            }
        }
        SalesforceAnalyticsManager instance;
        if (INSTANCES == null) {
            INSTANCES = new HashMap<>();
            instance = new SalesforceAnalyticsManager(account);
            INSTANCES.put(uniqueId, instance);
        } else {
            instance = INSTANCES.get(uniqueId);
        }
        if (instance == null) {
            instance = new SalesforceAnalyticsManager(account);
            INSTANCES.put(uniqueId, instance);
        }

        // Adds a handler for publishing if not already active.
        if (!sPublishHandlerActive) {
            sScheduler = createPublishHandler();
            sPublishHandlerActive = true;
        }
        return instance;
    }

    /**
     * Resets the instance of this class associated with an unauthenticated user context.
     */
    public static synchronized void resetUnauthenticatedInstance() {
        reset(null);
    }

    /**
     * Resets the instance of this class associated with this user account.
     *
     * @param account User account.
     */
    public static synchronized void reset(UserAccount account) {
        reset(account, null);
    }

    /**
     * Resets the instance of this class associated with this user and community.
     *
     * @param account User account.
     * @param communityId Community ID.
     */
    public static synchronized void reset(UserAccount account, String communityId) {
        String uniqueId = UNAUTH_INSTANCE_KEY;
        if (account != null) {
            uniqueId = account.getUserId();
            if (UserAccount.INTERNAL_COMMUNITY_ID.equals(communityId)) {
                communityId = null;
            }
            if (!TextUtils.isEmpty(communityId)) {
                uniqueId = uniqueId + communityId;
            }
        }
        if (INSTANCES != null) {
            final SalesforceAnalyticsManager manager = INSTANCES.get(uniqueId);
            if (manager != null) {
                manager.analyticsManager.reset();
                manager.resetAnalyticsPolicy();
            }
            INSTANCES.remove(uniqueId);
        }
    }

    /**
     * Sets the publish frequency, in hours.
     *
     * @param publishFrequencyInHours Publish frequency, in hours.
     */
    public static synchronized void setPublishFrequencyInHours(int publishFrequencyInHours) {
        sPublishFrequencyInHours = publishFrequencyInHours;
        if (sScheduler != null) {
            sScheduler.cancel(false);
            sScheduler = createPublishHandler();
        }
    }

    /**
     * Returns the publish frequency currently set, in hours.
     *
     * @return Publish frequency, in hours.
     */
    public static int getPublishFrequencyInHours() {
        return sPublishFrequencyInHours;
    }

    /**
     * Returns an instance of event store manager.
     *
     * @return Event store manager.
     */
    public EventStoreManager getEventStoreManager() {
        return eventStoreManager;
    }

    /**
     * Returns an instance of analytics manager.
     *
     * @return Analytics manager.
     */
    public AnalyticsManager getAnalyticsManager() {
        return analyticsManager;
    }

    /**
     * Disables or enables logging of events. If logging is disabled, no events
     * will be stored. However, publishing of events is still possible.
     *
     * @param enabled True - if logging should be enabled, False - otherwise.
     */
    public void enableLogging(boolean enabled) {
        if (enabled) {
            SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_AILTN_ENABLED);
        } else {
            SalesforceSDKManager.getInstance().unregisterUsedAppFeature(Features.FEATURE_AILTN_ENABLED);
        }
        storeAnalyticsPolicy(enabled);
        eventStoreManager.enableLogging(enabled);
    }

    /**
     * Updates the preferences of this library.
     */
    public void updateLoggingPrefs() {
        final AdminSettingsManager settingsManager = new AdminSettingsManager();
        final String enabled = settingsManager.getPref(SalesforceAnalyticsManager.ANALYTICS_ON_OFF_KEY, account);
        if (!TextUtils.isEmpty(enabled)) {
            if (!Boolean.parseBoolean(enabled)) {
                enableLogging(false);
            } else {
                enableLogging(true);
            }
        }
    }

    /**
     * Returns whether logging is enabled or disabled.
     *
     * @return True - if logging is enabled, False - otherwise.
     */
    public boolean isLoggingEnabled() {
        return enabled;
    }

    /**
     * Publishes all stored events to all registered network endpoints after
     * applying the required event format transforms. Stored events will be
     * deleted if publishing was successful for all registered endpoints.
     * This method should NOT be called from the main thread.
     */
    public synchronized void publishAllEvents() {
        final List<InstrumentationEvent> events = eventStoreManager.fetchAllEvents();
        publishEvents(events);
    }

    /**
     * Publishes a list of events to all registered network endpoints after
     * applying the required event format transforms. Stored events will be
     * deleted if publishing was successful for all registered endpoints.
     * This method should NOT be called from the main thread.
     *
     * @param events List of events.
     */
    public synchronized void publishEvents(List<InstrumentationEvent> events) {
        if (events == null || events.size() == 0) {
            return;
        }
        final List<String> eventsIds = new ArrayList<>();
        boolean success = true;
        final Set<Class<? extends Transform>> remoteKeySet = remotes.keySet();
        for (final Class<? extends Transform> transformClass : remoteKeySet) {
            Transform transformer = null;
            try {
                transformer = transformClass.newInstance();
            } catch (Exception e) {
                SalesforceSDKLogger.e(TAG, "Exception thrown while instantiating class", e);
            }
            if (transformer != null) {
                final JSONArray eventsJSONArray = new JSONArray();
                for (final InstrumentationEvent event : events) {
                    eventsIds.add(event.getEventId());
                    final JSONObject eventJSON = transformer.transform(event);
                    if (eventJSON != null) {
                        eventsJSONArray.put(eventJSON);
                    }
                }
                AnalyticsPublisher networkPublisher = null;
                try {
                    networkPublisher = remotes.get(transformClass).newInstance();
                } catch (Exception e) {
                    SalesforceSDKLogger.e(TAG, "Exception thrown while instantiating class", e);
                }
                if (networkPublisher != null) {
                    boolean networkSuccess = networkPublisher.publish(eventsJSONArray);

                    /*
                     * Updates the success flag only if all previous requests have been
                     * successful. This ensures that the operation is marked success only
                     * if all publishers are successful.
                     */
                    if (success) {
                        success = networkSuccess;
                    }
                }
            }
        }

        /*
         * Deletes events from the event store if the network publishing was successful.
         */
        if (success) {
            eventStoreManager.deleteEvents(eventsIds);
        }
    }

    /**
     * Publishes an event to all registered network endpoints after
     * applying the required event format transforms. Stored event will be
     * deleted if publishing was successful for all registered endpoints.
     * This method should NOT be called from the main thread.
     *
     * @param event Event.
     */
    public synchronized void publishEvent(InstrumentationEvent event) {
        if (event == null) {
            return;
        }
        final List<InstrumentationEvent> events = new ArrayList<>();
        events.add(event);
        publishEvents(events);
    }

    /**
     * Adds a remote publisher to publish events to.
     *
     * @param transformer Transformer class.
     * @param publisher   Publisher class.
     */
    public void addRemotePublisher(Class<? extends Transform> transformer,
                                   Class<? extends AnalyticsPublisher> publisher) {
        if (transformer == null || publisher == null) {
            SalesforceSDKLogger.w(TAG, "Invalid transformer and/or publisher");
            return;
        }
        remotes.put(transformer, publisher);
    }

    private SalesforceAnalyticsManager(UserAccount account) {
        this.account = account;
        final SalesforceSDKManager sdkManager = SalesforceSDKManager.getInstance();
        final DeviceAppAttributes deviceAppAttributes = getDeviceAppAttributes();
        final String filenameSuffix = (account != null) ? account.getCommunityLevelFilenameSuffix()
                : UNAUTH_INSTANCE_KEY;
        analyticsManager = new AnalyticsManager(filenameSuffix, sdkManager.getAppContext(),
                SalesforceSDKManager.getEncryptionKey(), deviceAppAttributes);
        eventStoreManager = analyticsManager.getEventStoreManager();
        remotes = new HashMap<>();
        remotes.put(AILTNTransform.class, AILTNPublisher.class);

        // Reads the existing analytics policy and sets it upon initialization.
        readAnalyticsPolicy();
        enableLogging(enabled);
    }

    /**
     * Returns the device app attributes associated with this device.
     *
     * @return Device app attributes.
     */
    public static DeviceAppAttributes getDeviceAppAttributes() {
        final SalesforceSDKManager sdkManager = SalesforceSDKManager.getInstance();
        final Context context = sdkManager.getAppContext();
        final String osVersion = Build.VERSION.RELEASE;
        final String osName = "android";
        final String appType = sdkManager.getAppType();
        final String mobileSdkVersion = SalesforceSDKManager.SDK_VERSION;
        final String deviceModel = Build.MODEL;
        final String deviceId = sdkManager.getDeviceId();
        final String clientId = BootConfig.getBootConfig(context).getRemoteAccessConsumerKey();
        return new DeviceAppAttributes(sdkManager.getAppVersion(),
                SalesforceSDKManager.getAiltnAppName(), osVersion, osName, appType,
                mobileSdkVersion, deviceModel, deviceId, clientId);
    }

    private synchronized void storeAnalyticsPolicy(boolean enabled) {
        final Context context = SalesforceSDKManager.getInstance().getAppContext();
        final String filenameSuffix = (account != null) ? account.getUserLevelFilenameSuffix()
                : UNAUTH_INSTANCE_KEY;
        final String filename = AILTN_POLICY_PREF + filenameSuffix;
        final SharedPreferences sp = context.getSharedPreferences(filename, Context.MODE_PRIVATE);
        final SharedPreferences.Editor e = sp.edit();
        e.putBoolean(ANALYTICS_ON_OFF_KEY, enabled);
        e.commit();
        this.enabled = enabled;
    }

    private void readAnalyticsPolicy() {
        final Context context = SalesforceSDKManager.getInstance().getAppContext();
        final String filenameSuffix = (account != null) ? account.getUserLevelFilenameSuffix()
                : UNAUTH_INSTANCE_KEY;
        final String filename = AILTN_POLICY_PREF + filenameSuffix;
        final SharedPreferences sp = context.getSharedPreferences(filename, Context.MODE_PRIVATE);
        if (!sp.contains(ANALYTICS_ON_OFF_KEY)) {
            storeAnalyticsPolicy(true);
        }
        enabled = sp.getBoolean(ANALYTICS_ON_OFF_KEY, true);
    }

    private void resetAnalyticsPolicy() {
        final Context context = SalesforceSDKManager.getInstance().getAppContext();
        final String filenameSuffix = (account != null) ? account.getUserLevelFilenameSuffix()
                : UNAUTH_INSTANCE_KEY;
        final String filename = AILTN_POLICY_PREF + filenameSuffix;
        final SharedPreferences sp = context.getSharedPreferences(filename, Context.MODE_PRIVATE);
        final SharedPreferences.Editor e = sp.edit();
        e.clear();
        e.commit();
    }

    private static ScheduledFuture createPublishHandler() {
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        final Runnable publishRunnable = new Runnable() {

            @Override
            public void run() {
                AnalyticsPublisherService.startActionPublish(SalesforceSDKManager.getInstance().getAppContext());
            }
        };
        return scheduler.scheduleAtFixedRate(publishRunnable, 0, sPublishFrequencyInHours, TimeUnit.HOURS);
    }
}
