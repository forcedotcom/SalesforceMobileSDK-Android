/*
 * Copyright (c) 2014, salesforce.com, inc.
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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.config.BootConfig;

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
    public static final String ACCOUNT_BUNDLE_KEY = "account_bundle";
    public static final String ALL_ACCOUNTS_BUNDLE_VALUE = "all_accounts";
    public static final String GCM_PREFS = "gcm_prefs";

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
    private static final long DEFAULT_BACKOFF = 30000;

    /**
     * Initiates push registration, if the application is not already registered.
     * Otherwise, this method initiates registration/re-registration to the
     * SFDC endpoint, in order to keep it alive, or to register a new account
     * that just logged in.
     *
     * @param context Context.
     * @param account User account.
     */
    public static void register(Context context, UserAccount account) {

    	/*
    	 * Performs registration steps if it is a new account, or if the
    	 * account hasn't been registered yet. Otherwise, performs
    	 * re-registration at the SFDC endpoint, to keep it alive.
    	 */
        if (account != null && !isRegistered(context, account)) {
            setInProgress(context, true, account);
            final Intent registrationIntent = new Intent(REQUEST_REGISTRATION_INTENT);
            registrationIntent.putExtra(EXTRA_APPLICATION_PENDING_INTENT,
                    PendingIntent.getBroadcast(context, 0, new Intent(), 0));
            registrationIntent.putExtra(SENDER,
            		BootConfig.getBootConfig(context).getPushNotificationClientId());
            registrationIntent.putExtra(ACCOUNT_BUNDLE_KEY, account.toBundle());
            final ServiceInfo si = getServiceInfo(context, registrationIntent);
            if (si != null) {
        		final ComponentName component = new ComponentName(si.packageName, si.name);
                registrationIntent.setComponent(component);
                context.startService(registrationIntent);
    		}
        } else {
            registerSFDCPush(context, account);
        }
    }

    /**
     * Initiates push registration against the SFDC endpoint.
     *
     * @param context Context.
     * @param account User account.
     */
    public static void registerSFDCPush(Context context, UserAccount account) {
        final Intent registrationIntent = new Intent(PushService.SFDC_REGISTRATION_RETRY_INTENT);
    	if (account == null) {
    		final Bundle bundle = new Bundle();
            bundle.putString(ACCOUNT_BUNDLE_KEY, ALL_ACCOUNTS_BUNDLE_VALUE);
            registrationIntent.putExtra(ACCOUNT_BUNDLE_KEY, bundle);
            PushService.runIntentInService(registrationIntent);
    	} else if (isRegistered(context, account)) {
            registrationIntent.putExtra(ACCOUNT_BUNDLE_KEY, account.toBundle());
            PushService.runIntentInService(registrationIntent);
        }
    }

    /**
     * Performs GCM and SFDC un-registration from push notifications for the
     * specified user account.
     *
     * @param context Context.
     * @param account User account.
     */
    public static void unregister(Context context, UserAccount account) {
        if (isRegistered(context, account)) {
            setInProgress(context, true, account);
            final Intent unregIntent = new Intent(REQUEST_UNREGISTRATION_INTENT);
            unregIntent.putExtra(EXTRA_APPLICATION_PENDING_INTENT,
                    PendingIntent.getBroadcast(context, 0, new Intent(), 0));
            unregIntent.putExtra(ACCOUNT_BUNDLE_KEY, account.toBundle());
            final ServiceInfo si = getServiceInfo(context, unregIntent);
            if (si != null) {
        		final ComponentName component = new ComponentName(si.packageName, si.name);
                unregIntent.setComponent(component);
                context.startService(unregIntent);
    		}
        }
    }

    /**
     * Returns the current registration ID, or null, if registration
     * hasn't taken place yet.
     *
     * @param context Context.
     * @param account User account.
     * @return Registration ID.
     */
    public static String getRegistrationId(Context context, UserAccount account) {
    	final SharedPreferences prefs = context.getSharedPreferences(getSharedPrefFile(account),
        		Context.MODE_PRIVATE);
        return prefs.getString(REGISTRATION_ID, null);
    }

    /**
     * Stores the GCM registration ID, and resets back off time.
     *
     * @param context Context.
     * @param registrationId Registration ID received from the GCM service.
     * @param account User account.
     */
    public static void setRegistrationId(Context context, String registrationId,
    		UserAccount account) {
    	final SharedPreferences prefs = context.getSharedPreferences(getSharedPrefFile(account),
        		Context.MODE_PRIVATE);
        final Editor editor = prefs.edit();
        editor.putString(REGISTRATION_ID, registrationId);
        editor.putLong(BACKOFF, DEFAULT_BACKOFF);
        editor.commit();
    }

    /**
     * Returns whether the specified user account is currently registered or not.
     *
     * @param context Context.
     * @param account User account.
     * @return True - if the user account is registered, False - otherwise.
     */
    public static boolean isRegistered(Context context, UserAccount account) {
    	final SharedPreferences prefs = context.getSharedPreferences(getSharedPrefFile(account),
        		Context.MODE_PRIVATE);
        return (prefs.getString(REGISTRATION_ID, null) != null);
    }

    /**
     * Clears the stored SFDC device ID.
     *
     * @param context Context.
     * @param account User account.
     */
    public static void clearSFDCRegistrationInfo(Context context, UserAccount account) {
    	final SharedPreferences prefs = context.getSharedPreferences(getSharedPrefFile(account),
        		Context.MODE_PRIVATE);
        final Editor editor = prefs.edit();
        editor.remove(DEVICE_ID);
        editor.commit();
    }

    /**
     * Returns whether the user account is registered with SFDC or not.
     *
     * @param context Context.
     * @param account User account.
     * @return True - if registered, False - otherwise.
     */
    public static boolean isRegisteredWithSFDC(Context context, UserAccount account) {
    	final SharedPreferences prefs = context.getSharedPreferences(getSharedPrefFile(account),
        		Context.MODE_PRIVATE);
        return (prefs.getString(DEVICE_ID, null) != null);
    }

    /**
     * Returns the SFDC registration ID for the specified user account.
     *
     * @param context Context.
     * @param account User account.
     * @return SFDC registration ID.
     */
    public static String getDeviceId(Context context, UserAccount account) {
    	final SharedPreferences prefs = context.getSharedPreferences(getSharedPrefFile(account),
        		Context.MODE_PRIVATE);
        return prefs.getString(DEVICE_ID, null);
    }

    /**
     * Sets the last time SFDC registration was successful for the
     * specified user account.
     *
     * @param context Context.
     * @param lastRegistrationTime Last registration time.
     * @param account User account.
     */
    public static void setLastRegistrationTime(Context context, long lastRegistrationTime,
    		UserAccount account) {
    	final SharedPreferences prefs = context.getSharedPreferences(getSharedPrefFile(account),
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
     * @param account User account.
     */
    public static void setInProgress(Context context, boolean inProgress,
    		UserAccount account) {
    	final SharedPreferences prefs = context.getSharedPreferences(getSharedPrefFile(account),
        		Context.MODE_PRIVATE);
        final Editor editor = prefs.edit();
        editor.putBoolean(IN_PROGRESS, inProgress);
        editor.commit();
    }

    /**
     * Returns whether push notification registration/un-registration is in progress.
     *
     * @param context Context.
     * @param account User account.
     * @return True - if in progress, False - otherwise.
     */
    public static boolean isInProgress(Context context, UserAccount account) {
    	final SharedPreferences prefs = context.getSharedPreferences(getSharedPrefFile(account),
        		Context.MODE_PRIVATE);
        return prefs.getBoolean(IN_PROGRESS, false);
    }

    /**
     * Clears the stored registration information.
     *
     * @param context Context.
     * @param account User account.
     */
    public static void clearRegistrationInfo(Context context, UserAccount account) {
    	final SharedPreferences prefs = context.getSharedPreferences(getSharedPrefFile(account),
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
     * @param account User account.
     */
    static long getBackoff(Context context, UserAccount account) {
    	final SharedPreferences prefs = context.getSharedPreferences(getSharedPrefFile(account),
        		Context.MODE_PRIVATE);
        return prefs.getLong(BACKOFF, DEFAULT_BACKOFF);
    }

    /**
     * Sets the backoff time for registration retry.
     *
     * @param context Context.
     * @param backoff Backoff time to be used.
     * @param account User account.
     */
    static void setBackoff(Context context, long backoff, UserAccount account) {
        final SharedPreferences prefs = context.getSharedPreferences(getSharedPrefFile(account),
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
     * @param account User account.
     */
    static void setRegistrationInfo(Context context, String registrationId,
    		String deviceId, UserAccount account) {
        final SharedPreferences prefs = context.getSharedPreferences(getSharedPrefFile(account),
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
     * Returns the name of the shared pref file for the specified account.
     *
     * @param account User account.
     * @return Name of the shared pref file.
     */
    private static String getSharedPrefFile(UserAccount account) {
    	String sharedPrefFile = GCM_PREFS;
    	if (account != null) {
    		sharedPrefFile = sharedPrefFile + account.getUserLevelFilenameSuffix();
    	}
    	return sharedPrefFile;
    }

    /**
     * Returns the service info associated with an intent.
     *
     * @param context Context.
     * @param intent Intent.
     * @return Service info.
     */
    private static ServiceInfo getServiceInfo(Context context, Intent intent) {
    	ServiceInfo si = null;
    	final PackageManager pm = context.getPackageManager();
        if (pm != null) {
        	final ResolveInfo ri = pm.resolveService(intent, 0);
        	if (ri != null) {
        		si = ri.serviceInfo;
        	}
        }
        return si;
    }
}