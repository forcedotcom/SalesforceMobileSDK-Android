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

import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpStatus;
import org.json.JSONObject;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.ApiVersionStrings;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;

/**
 * This class houses functionality related to push notifications.
 * It performs registration and unregistration of push notifications
 * against the Salesforce connected app endpoint. It also receives
 * push notifications sent by the org to the registered user/device.
 *
 * @author bhariharan
 * @author ktanna
 */
public class PushService extends IntentService {

	private static final String TAG = "PushService";

    // Intent actions.
	public static final String GCM_REGISTRATION_CALLBACK_INTENT = "com.google.android.c2dm.intent.REGISTRATION";
    public static final String GCM_RECEIVE_INTENT = "com.google.android.c2dm.intent.RECEIVE";
    public static final String SFDC_REGISTRATION_RETRY_INTENT = "com.salesforce.mobilesdk.c2dm.intent.RETRY";

    // Extras in the registration callback intents.
    private static final String EXTRA_UNREGISTERED = "unregistered";
    private static final String EXTRA_ERROR = "error";
    private static final String EXTRA_REGISTRATION_ID = "registration_id";

    // Error constant when service is not available.
    private static final String ERR_SERVICE_NOT_AVAILABLE = "SERVICE_NOT_AVAILABLE";

	// Retry time constants.
    private static final long MILLISECONDS_IN_SIX_DAYS = 518400000L;
    private static final long SFDC_REGISTRATION_RETRY = 30000;

    // Salesforce push notification constants.
    private static final String MOBILE_PUSH_SERVICE_DEVICE = "MobilePushServiceDevice";
    private static final String ANDROID_GCM = "androidGcm";
    private static final String SERVICE_TYPE = "ServiceType";
    private static final String CONNECTION_TOKEN = "ConnectionToken";
    private static final String FIELD_ID = "id";

    // Wake lock instance.
    private static PowerManager.WakeLock WAKE_LOCK;

    private Context context;
    private RestClient restClient;

    /**
     * This method is called from the broadcast receiver, when a push notification
     * is received, or when we receive a callback from the GCM service. Processing
     * of the message occurs here, and the acquired wake lock is released post processing.
     *
     * @param intent Intent.
     */
    static void runIntentInService(Intent intent) {
        final Context context = SalesforceSDKManager.getInstance().getAppContext();
        if (WAKE_LOCK == null) {
            final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            WAKE_LOCK = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        }
        WAKE_LOCK.acquire();
        intent.setClassName(context, PushService.class.getName());
        final ComponentName name = context.startService(intent);
        if (name == null) {
        	Log.w(TAG, "Could not start GCM service.");
        }
    }

	/**
	 * Default constructor.
	 */
	public PushService() {
		super(TAG);
		context = SalesforceSDKManager.getInstance().getAppContext();
		restClient = getRestClient();
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		final Context context = SalesforceSDKManager.getInstance().getAppContext();
		try {
            if (intent.getAction().equals(GCM_REGISTRATION_CALLBACK_INTENT)) {
                handleRegistration(intent);
            } else if (intent.getAction().equals(GCM_RECEIVE_INTENT)) {
                onMessage(intent);
            } else if (intent.getAction().equals(SFDC_REGISTRATION_RETRY_INTENT)) {
            	final String regId = PushMessaging.getRegistrationId(context);
            	if (regId != null) {
                    onRegistered(regId);
            	}
            }
        } finally {

        	// Releases the wake lock, since processing is complete.
            if (WAKE_LOCK != null && WAKE_LOCK.isHeld()) {
                WAKE_LOCK.release();
            }
        }
	}

	/**
	 * Handles a push notification message.
	 *
	 * @param intent Intent.
	 */
	protected void onMessage(Intent intent) {
		if (intent != null) {
			final Bundle pushMessage = intent.getExtras();
			final PushNotificationInterface pnInterface = SalesforceSDKManager.getInstance().getPushNotificationReceiver();
			if (pnInterface != null && pushMessage != null) {
				pnInterface.onPushMessageReceived(pushMessage);
			}
		}
	}

    /**
     * Handles errors associated with registration or un-registration.
     *
     * @param error Error received from the GCM service.
     */
    private void onError(String error) {
        if (PushMessaging.isRegistered(context)) {
            handleUnRegistrationError(error);
        } else {
            handleRegistrationError(error);
        }
    }

    /**
     * Handles registration errors. Retries on service unavailable, and
     * bails out on other types of errors.
     *
     * @param error Error received from the GCM service.
     */
    private void handleRegistrationError(String error) {
    	if (error != null && ERR_SERVICE_NOT_AVAILABLE.equals(error)) {
    		scheduleGCMRetry(true);
    	}
    }

    /**
     * Handles unregistration errors.
     *
     * @param error Error received from the GCM service.
     */
    private void handleUnRegistrationError(String error) {
    	if (PushMessaging.isRegisteredWithSFDC(context)) {
    		final String id = PushMessaging.getDeviceId(context);
    		if (id != null) {
    			unregisterSFDCPushNotification(id);
    		}
    	}
        context.sendBroadcast(new Intent(PushMessaging.UNREGISTERED_ATTEMPT_COMPLETE_EVENT));
        scheduleGCMRetry(false);
    }

    /**
     * Schedules retry of GCM registration or un-registration.
     *
     * @param register True - for registration retry, False - for un-registration retry.
     */
    private void scheduleGCMRetry(boolean register) {
        long backoffTimeMs = PushMessaging.getBackoff(context);
        final Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MILLISECOND, (int) backoffTimeMs);
        final Intent retryIntent = new Intent(context, register ? RetryRegistrationAlarmReceiver.class
                : UnregisterRetryAlarmReceiver.class);
        final PendingIntent retryPIntent = PendingIntent.getBroadcast(context,
        		1, retryIntent, PendingIntent.FLAG_ONE_SHOT);
        final AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), retryPIntent);

        // Next retry should wait longer.
        backoffTimeMs *= 2;
        PushMessaging.setBackoff(context, backoffTimeMs);
    }

    /**
     * Schedules retry of SFDC registration.
     *
     * @param when When to retry.
     */
    private void scheduleSFDCRegistrationRetry(long when) {
        final Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MILLISECOND, (int) when);
        final Intent retryIntent = new Intent(context, SFDCRegistrationRetryAlarmReceiver.class);
        final PendingIntent retryPIntent = PendingIntent.getBroadcast(context,
        		1, retryIntent, PendingIntent.FLAG_ONE_SHOT);
        final AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), retryPIntent);
    }

    /**
     * This method is called when registration with GCM is successful.
     *
     * @param registrationId Registration ID received from GCM service.
     */
    private void onRegistered(String registrationId) {
    	long retryInterval = SFDC_REGISTRATION_RETRY;
    	try {
        	final String id = registerSFDCPushNotification(registrationId);
        	if (id != null) {
        		retryInterval = MILLISECONDS_IN_SIX_DAYS;
        		PushMessaging.setRegistrationInfo(context, registrationId, id);
        	} else {
            	PushMessaging.setRegistrationId(context, registrationId);
        	}
    	} catch (Exception e) {
    		Log.e(TAG, "Error occurred during SFDC registration.", e);
    	} finally {
            scheduleSFDCRegistrationRetry(retryInterval);
    	}
    }

    /**
     * This method is called when the device has been un-registered.
     */
    private void onUnregistered() {
    	try {
        	final String id = PushMessaging.getDeviceId(context);
        	unregisterSFDCPushNotification(id);
    	} catch (Exception e) {
    		Log.e(TAG, "Error occurred during SFDC un-registration.", e);
    	} finally {
        	PushMessaging.clearRegistrationInfo(context);
            context.sendBroadcast(new Intent(PushMessaging.UNREGISTERED_ATTEMPT_COMPLETE_EVENT));
            context.sendBroadcast(new Intent(PushMessaging.UNREGISTERED_EVENT));
    	}
    }

    /**
     * Hits the Salesforce endpoint to register for push notifications.
     *
     * @param registrationId Registration ID.
     * @return Salesforce ID that uniquely identifies the registered device.
     */
    private String registerSFDCPushNotification(String registrationId) {
    	final Map<String, Object> fields = new HashMap<String, Object>();
    	fields.put(CONNECTION_TOKEN, registrationId);
    	fields.put(SERVICE_TYPE, ANDROID_GCM);
    	try {
        	final RestRequest req = RestRequest.getRequestForCreate(ApiVersionStrings.VERSION_NUMBER,
        			MOBILE_PUSH_SERVICE_DEVICE, fields);
        	if (restClient != null) {
            	final RestResponse res = restClient.sendSync(req);
            	String id = null;
            	if (res.getStatusCode() == HttpStatus.SC_CREATED) {
            		final JSONObject obj = res.asJSONObject();
            		if (obj != null) {
            			id = obj.getString(FIELD_ID);
            		}
            	}
            	return id;
        	}
    	} catch (Exception e) {
    		Log.e(TAG, "Push notification registration failed.", e);
    	}
    	return null;
    }

    /**
     * Hits the Salesforce endpoint to un-register from push notifications.
     *
     * @param registeredId Salesforce ID that uniquely identifies the registered device.
     * @return True - if un-registration was successful, False - otherwise.
     */
    private boolean unregisterSFDCPushNotification(String registeredId) {
    	final RestRequest req = RestRequest.getRequestForDelete(ApiVersionStrings.VERSION_NUMBER,
    			MOBILE_PUSH_SERVICE_DEVICE, registeredId);
    	try {
    		if (restClient != null) {
            	final RestResponse res = restClient.sendSync(req);
            	if (res.getStatusCode() == HttpStatus.SC_NO_CONTENT) {
            		return true;
            	}
    		}
    	} catch (IOException e) {
    		Log.e(TAG, "Push notification un-registration failed.", e);
    	}
    	return false;
    }

    /**
     * Gets an instance of RestClient.
     *
     * @return Instance of RestClient.
     */
    private RestClient getRestClient() {
    	final ClientManager cm = new ClientManager(SalesforceSDKManager.getInstance().getAppContext(),
    			SalesforceSDKManager.getInstance().getAccountType(),
    			SalesforceSDKManager.getInstance().getLoginOptions(), true);
    	RestClient client = null;
    	if (cm != null) {
    		try {
            	client = cm.peekRestClient();
    		} catch (Exception e) {
    			Log.e(TAG, "Failed to get rest client.");
    		}
    	}
    	return client;
    }

    /**
     * Handles registration callback.
     *
     * @param intent Intent.
     */
    private void handleRegistration(Intent intent) {
        final String registrationId = intent.getStringExtra(EXTRA_REGISTRATION_ID);
        final String error = intent.getStringExtra(EXTRA_ERROR);
        final String removed = intent.getStringExtra(EXTRA_UNREGISTERED);
        if (removed != null) {
            onUnregistered();
        } else if (error != null) {
            onError(error);
        } else {
            onRegistered(registrationId);
        }
    }

    /**
     * Broadcast receiver to retry the entire push registration process (GCM + SFDC).
     *
     * @author ktanna
     */
    public static class RetryRegistrationAlarmReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            PushMessaging.register(context);
        }
    }

    /**
     * Broadcast receiver to retry SFDC push registration.
     *
     * @author ktanna
     */
    public static class SFDCRegistrationRetryAlarmReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            PushMessaging.registerSFDCPush(context);
        }
    }

    /**
     * Broadcast receiver to retry GCM registration.
     *
     * @author ktanna
     */
    public static class UnregisterRetryAlarmReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            PushMessaging.unregister(context);
        }
    }
}
