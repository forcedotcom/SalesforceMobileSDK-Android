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
package com.salesforce.androidsdk.analytics;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.analytics.manager.AnalyticsManager;
import com.salesforce.androidsdk.analytics.model.DeviceAppAttributes;
import com.salesforce.androidsdk.analytics.store.EventStoreManager;
import com.salesforce.androidsdk.app.SalesforceSDKManager;

import java.util.HashMap;
import java.util.Map;

/**
 * This class contains APIs that can be used to interact with
 * the SalesforceAnalytics library.
 *
 * @author bhariharan
 */
public class SalesforceAnalyticsManager {

    private static final String TAG = "AnalyticsManager";

    private static Map<String, SalesforceAnalyticsManager> INSTANCES;

    private AnalyticsManager analyticsManager;
    private EventStoreManager eventStoreManager;

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
        if (account == null) {
            account = SalesforceSDKManager.getInstance().getUserAccountManager().getCurrentUser();
        }
        if (account == null) {
            return null;
        }
        String uniqueId = account.getUserId();
        if (UserAccount.INTERNAL_COMMUNITY_ID.equals(communityId)) {
            communityId = null;
        }
        if (!TextUtils.isEmpty(communityId)) {
            uniqueId = uniqueId + communityId;
        }
        SalesforceAnalyticsManager instance = null;
        if (INSTANCES == null) {
            INSTANCES = new HashMap<String, SalesforceAnalyticsManager>();
            instance = new SalesforceAnalyticsManager(account, communityId);
            INSTANCES.put(uniqueId, instance);
        } else {
            instance = INSTANCES.get(uniqueId);
        }
        if (instance == null) {
            instance = new SalesforceAnalyticsManager(account, communityId);
            INSTANCES.put(uniqueId, instance);
        }
        return instance;
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
        if (account == null) {
            account = SalesforceSDKManager.getInstance().getUserAccountManager().getCurrentUser();
        }
        if (account != null) {
            String uniqueId = account.getUserId();
            if (UserAccount.INTERNAL_COMMUNITY_ID.equals(communityId)) {
                communityId = null;
            }
            if (!TextUtils.isEmpty(communityId)) {
                uniqueId = uniqueId + communityId;
            }
            if (INSTANCES != null) {
                final SalesforceAnalyticsManager manager = INSTANCES.get(uniqueId);
                if (manager != null) {
                    manager.analyticsManager.reset();
                }
                INSTANCES.remove(uniqueId);
            }
        }
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

    private SalesforceAnalyticsManager(UserAccount account, String communityId) {
        final DeviceAppAttributes deviceAppAttributes = buildDeviceAppAttributes();
        final SalesforceSDKManager sdkManager = SalesforceSDKManager.getInstance();
        analyticsManager = new AnalyticsManager(account.getCommunityLevelFilenameSuffix(),
                sdkManager.getAppContext(), sdkManager.getPasscodeHash(), deviceAppAttributes);
        eventStoreManager = analyticsManager.getEventStoreManager();
    }

    private DeviceAppAttributes buildDeviceAppAttributes() {
        final SalesforceSDKManager sdkManager = SalesforceSDKManager.getInstance();
        final Context context = sdkManager.getAppContext();
        String appVersion = "";
        String appName = "";
        try {
            final PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            appVersion = packageInfo.versionName;
            appName = context.getString(packageInfo.applicationInfo.labelRes);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, e);
        } catch (Resources.NotFoundException nfe) {

            // A test harness such as Gradle does NOT have an application name.
            Log.w(TAG, nfe);
        }
        final String osVersion = Build.VERSION.RELEASE;
        final String osName = "android";
        final String appType = sdkManager.getAppType();
        final String mobileSdkVersion = SalesforceSDKManager.SDK_VERSION;
        final String deviceModel = Build.MODEL;
        final String deviceId = sdkManager.getDeviceId();
        return new DeviceAppAttributes(appVersion, appName, osVersion, osName, appType,
                mobileSdkVersion, deviceModel, deviceId);
    }
}
