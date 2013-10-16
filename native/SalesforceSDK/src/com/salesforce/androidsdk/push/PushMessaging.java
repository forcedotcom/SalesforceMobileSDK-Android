/*
 * Copyright (c) 2013, salesforce.com, inc.
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
package com.salesforce.androidsdk.push;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import com.salesforce.androidsdk.rest.BootConfig;

/**
 * This class provides utility functions related to push notifications,
 * such as methods for registration, unregistration, and storage and
 * retrieval of push notification registration information from a
 * private shared preference file.
 *
 * @author bhariharan
 * @author ktanna
 */
public class PushMessaging {

	// Public constants.
    public static final String UNREGISTERED_ATTEMPT_COMPLETE_EVENT = "com.salesfore.mobilesdk.c2dm.UNREGISTERED";
    public static final String UNREGISTERED_EVENT = "com.salesfore.mobilesdk.c2dm.ACTUAL_UNREGISTERED";

    // Private constants.
    private static final String SENDER = "sender";
    private static final String EXTRA_APPLICATION_PENDING_INTENT = "app";
    private static final String REQUEST_UNREGISTRATION_INTENT = "com.google.android.c2dm.intent.UNREGISTER";
    private static final String REQUEST_REGISTRATION_INTENT = "com.google.android.c2dm.intent.REGISTER";
    private static final String LAST_SFDC_REGISTRATION_TIME = "last_registration_change";
    private static final String REGISTRATION_ID = "c2dm_registration_id";
    private static final String BACKOFF = "backoff";
    private static final String DEVICE_ID = "deviceId";
    private static final String IN_PROGRESS = "inprogress";
    private static final long MILLISECONDS_IN_A_DAY = 86400000L;
    private static final String GCM_PREFS = "gcm_prefs";
    private static final long DEFAULT_BACKOFF = 30000;

    /**
     * Initiates push registration, if the application is not already registered.
     * If it has been more than a day since the registration occurred, this method
     * initiates re-registration to the SFDC endpoint, in order to keep it alive.
     *
     * @param context Context.
     */
    public static void register(Context context) {
        if (!isRegistered(context)) {
            setInProgress(context, true);
            final Intent registrationIntent = new Intent(REQUEST_REGISTRATION_INTENT);
            registrationIntent.putExtra(EXTRA_APPLICATION_PENDING_INTENT,
                    PendingIntent.getBroadcast(context, 0, new Intent(), 0));
            registrationIntent.putExtra(SENDER,
            		BootConfig.getBootConfig(context).getPushNotificationClientId());
            context.startService(registrationIntent);
        } else if (hasBeenADaySinceLastSFDCRegistration(context)) {
            registerSFDCPush(context);
        }
    }

    /**
     * Initiates push registration against the SFDC endpoint.
     *
     * @param context Context.
     */
    public static void registerSFDCPush(Context context) {
        if (isRegistered(context)) {
            final Intent registrationIntent = new Intent(PushService.SFDC_REGISTRATION_RETRY_INTENT);
            PushService.runIntentInService(registrationIntent);
        }
    }

    /**
     * Performs GCM and SFDC un-registration from push notifications.
     *
     * @param context Context.
     */
    public static void unregister(Context context) {
        if (isRegistered(context)) {
            setInProgress(context, true);
            final Intent unregIntent = new Intent(REQUEST_UNREGISTRATION_INTENT);
            unregIntent.putExtra(EXTRA_APPLICATION_PENDING_INTENT,
                    PendingIntent.getBroadcast(context, 0, new Intent(), 0));
            context.startService(unregIntent);
        }
    }

    /**
     * Returns the current registration ID, or null, if registration
     * hasn't taken place yet.
     *
     * @return Registration ID.
     */
    public static String getRegistrationId(Context context) {
        final SharedPreferences prefs = context.getSharedPreferences(GCM_PREFS,
        		Context.MODE_PRIVATE);
        return prefs.getString(REGISTRATION_ID, null);
    }

    /**
     * Stores the GCM registration ID, and resets back off time.
     *
     * @param context Context.
     * @param registrationId Registration ID received from the GCM service.
     */
    public static void setRegistrationId(Context context, String registrationId) {
        final SharedPreferences prefs = context.getSharedPreferences(GCM_PREFS,
        		Context.MODE_PRIVATE);
        final Editor editor = prefs.edit();
        editor.putString(REGISTRATION_ID, registrationId);
        editor.putLong(BACKOFF, DEFAULT_BACKOFF);
        editor.commit();
    }

    /**
     * Returns whether the application is currently registered or not.
     *
     * @param context Context.
     * @return True - if the application is registered, False - otherwise.
     */
    public static boolean isRegistered(Context context) {
        final SharedPreferences prefs = context.getSharedPreferences(GCM_PREFS,
        		Context.MODE_PRIVATE);
        return (prefs.getString(REGISTRATION_ID, null) != null);
    }

    /**
     * Clears the stored SFDC device ID.
     *
     * @param context Context.
     */
    public static void clearSFDCRegistrationInfo(Context context) {
        final SharedPreferences prefs = context.getSharedPreferences(GCM_PREFS,
        		Context.MODE_PRIVATE);
        final Editor editor = prefs.edit();
        editor.remove(DEVICE_ID);
        editor.commit();
    }

    /**
     * Returns whether the application is registered with SFDC or not.
     *
     * @param context Context.
     * @return True - if registered, False - otherwise.
     */
    public static boolean isRegisteredWithSFDC(Context context) {
        final SharedPreferences prefs = context.getSharedPreferences(GCM_PREFS,
        		Context.MODE_PRIVATE);
        return (prefs.getString(DEVICE_ID, null) != null);
    }

    /**
     * Returns the SFDC registration ID.
     *
     * @param context Context.
     * @return SFDC registration ID.
     */
    public static String getDeviceId(Context context) {
        final SharedPreferences prefs = context.getSharedPreferences(GCM_PREFS,
        		Context.MODE_PRIVATE);
        return prefs.getString(DEVICE_ID, null);
    }

    /**
     * Sets the last time SFDC registration was successful.
     *
     * @param context Context.
     * @param lastRegistrationTime Last registration time.
     */
    public static void setLastRegistrationTime(Context context, long lastRegistrationTime) {
        final SharedPreferences prefs = context.getSharedPreferences(GCM_PREFS,
        		Context.MODE_PRIVATE);
        final Editor editor = prefs.edit();
        editor.putLong(LAST_SFDC_REGISTRATION_TIME, lastRegistrationTime);
        editor.commit();
    }

    /**
     * Sets a boolean that reflects the status of push notification
     * registration or un-registration (in progress or not).
     *
     * @param context Context.
     * @param inProgress True - if in progress, False - otherwise.
     */
    public static void setInProgress(Context context, boolean inProgress) {
        final SharedPreferences prefs = context.getSharedPreferences(GCM_PREFS,
        		Context.MODE_PRIVATE);
        final Editor editor = prefs.edit();
        editor.putBoolean(IN_PROGRESS, inProgress);
        editor.commit();
    }

    /**
     * Returns whether push notification registration/un-registration is in progress.
     *
     * @param context Context.
     * @return True - if in progress, False - otherwise.
     */
    public static boolean isInProgress(Context context) {
        final SharedPreferences prefs = context.getSharedPreferences(GCM_PREFS,
        		Context.MODE_PRIVATE);
        return prefs.getBoolean(IN_PROGRESS, false);
    }

    /**
     * Clears the stored registration information.
     *
     * @param context Context.
     */
    public static void clearRegistrationInfo(Context context) {
        final SharedPreferences prefs = context.getSharedPreferences(GCM_PREFS,
        		Context.MODE_PRIVATE);
        final Editor editor = prefs.edit();
        editor.clear();
        editor.commit();
    }

    /**
     * Returns the last backoff time.
     *
     * @param context Context.
     * @return Backoff time.
     */
    static long getBackoff(Context context) {
        final SharedPreferences prefs = context.getSharedPreferences(GCM_PREFS,
        		Context.MODE_PRIVATE);
        return prefs.getLong(BACKOFF, DEFAULT_BACKOFF);
    }

    /**
     * Sets the backoff time for registration retry.
     *
     * @param context Context.
     * @param backoff Backoff time to be used.
     */
    static void setBackoff(Context context, long backoff) {
        final SharedPreferences prefs = context.getSharedPreferences(GCM_PREFS,
        		Context.MODE_PRIVATE);
        final Editor editor = prefs.edit();
        editor.putLong(BACKOFF, backoff);
        editor.commit();
    }

    /**
     * Stores the current registration information, and resets the backoff time.
     *
     * @param context Context.
     * @param registrationId GCM registration ID.
     * @param deviceId SFDC device ID.
     */
    static void setRegistrationInfo(Context context, String registrationId, String deviceId) {
        final SharedPreferences prefs = context.getSharedPreferences(GCM_PREFS,
        		Context.MODE_PRIVATE);
        final Editor editor = prefs.edit();
        editor.putString(REGISTRATION_ID, registrationId);
        editor.putString(DEVICE_ID, deviceId);
        editor.putLong(BACKOFF, DEFAULT_BACKOFF);
        editor.putLong(LAST_SFDC_REGISTRATION_TIME, System.currentTimeMillis());
        editor.putBoolean(IN_PROGRESS, false);
        editor.commit();
    }

    /**
     * Returns whether it has been a day since the last SFDC registration.
     *
     * @param context Context.
     * @return True - if it has been a day, False - otherwise.
     */
    private static boolean hasBeenADaySinceLastSFDCRegistration(Context context) {
        final SharedPreferences prefs = context.getSharedPreferences(GCM_PREFS,
        		Context.MODE_PRIVATE);
        long lastRegistrationTimeStamp = prefs.getLong(LAST_SFDC_REGISTRATION_TIME, 0);
        return ((System.currentTimeMillis() - lastRegistrationTimeStamp) > MILLISECONDS_IN_A_DAY);
    }
}