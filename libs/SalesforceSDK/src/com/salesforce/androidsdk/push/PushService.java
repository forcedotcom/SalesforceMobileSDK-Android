/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.rest.ApiVersionStrings;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.AccMgrAuthTokenProvider;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestClient.ClientInfo;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	private static final String FEATURE_PUSH_NOTIFICATIONS = "PN";

    // Intent actions.
    public static final String SFDC_REGISTRATION_RETRY_INTENT = "com.salesforce.mobilesdk.c2dm.intent.RETRY";
    public static final String SFDC_UNREGISTRATION_INTENT = "com.salesforce.mobilesdk.c2dm.intent.UNREGISTER";

	// Retry time constants.
    private static final long MILLISECONDS_IN_SIX_DAYS = 518400000L;
    private static final long SFDC_REGISTRATION_RETRY = 30000;

    // Salesforce push notification constants.
    private static final String MOBILE_PUSH_SERVICE_DEVICE = "MobilePushServiceDevice";
    private static final String ANDROID_GCM = "androidGcm";
    private static final String SERVICE_TYPE = "ServiceType";
    private static final String CONNECTION_TOKEN = "ConnectionToken";
    private static final String FIELD_ID = "id";
    private static final String NOT_ENABLED = "not_enabled";

    // Wake lock instance.
    private static PowerManager.WakeLock WAKE_LOCK;

    private Context context;

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
            SalesforceSDKLogger.w(TAG, "Could not start GCM service");
        }
    }

	/**
	 * Default constructor.
	 */
	public PushService() {
		super(TAG);
		context = SalesforceSDKManager.getInstance().getAppContext();
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		/*
		 * Grabs the extras from the intent, and determines based on the
		 * bundle whether to perform the operation for all accounts or
		 * just the specified account that's passed in.
		 */
		final Bundle bundle = intent.getBundleExtra(PushMessaging.ACCOUNT_BUNDLE_KEY);
		UserAccount account = null;
		boolean allAccounts = false;
		if (bundle != null) {
			final String allAccountsValue = bundle.getString(PushMessaging.ACCOUNT_BUNDLE_KEY);
			if (PushMessaging.ALL_ACCOUNTS_BUNDLE_VALUE.equals(allAccountsValue)) {
				allAccounts = true;
			} else {
				account = new UserAccount(bundle);
			}
		}
		final UserAccountManager userAccMgr = SalesforceSDKManager.getInstance().getUserAccountManager();
		final List<UserAccount> accounts = userAccMgr.getAuthenticatedUsers();
		try {
            boolean register = SFDC_REGISTRATION_RETRY_INTENT.equals(intent.getAction());
            boolean unregister = SFDC_UNREGISTRATION_INTENT.equals(intent.getAction());
            if (register || unregister) {
                if (allAccounts) {
                    if (accounts != null) {
                        for (final UserAccount userAcc : accounts) {
                            // If 'register' is true, we are registering, if it's false, we must be unregistering, because of the if gate above
                            performRegistrationChange(register, userAcc);
                        }
                    }
                } else {
                    if (account == null) {
                        account = userAccMgr.getCurrentUser();
                    }
                    // If 'register' is true, we are registering, if it's false, we must be unregistering, because of the if gate above
                    performRegistrationChange(register, account);
                }
            }
        } finally {

        	// Releases the wake lock, since processing is complete.
            if (WAKE_LOCK != null && WAKE_LOCK.isHeld()) {
                WAKE_LOCK.release();
            }
        }
	}

	private void performRegistrationChange(boolean register, UserAccount userAccount) {
		if (register) {
			final String regId = PushMessaging.getRegistrationId(context, userAccount);
			if (regId != null) {
				onRegistered(regId, userAccount);
			}
		} else {
			onUnregistered(userAccount);
		}
	}

    /**
     * Schedules retry of SFDC registration.
     *
     * @param when When to retry.
     * @param account User account.
     */
    private void scheduleSFDCRegistrationRetry(long when, UserAccount account) {
        final Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MILLISECOND, (int) when);
        final Intent retryIntent = new Intent(context, SFDCRegistrationRetryAlarmReceiver.class);
        if (account == null) {
			final Bundle bundle = new Bundle();
			bundle.putString(PushMessaging.ACCOUNT_BUNDLE_KEY, PushMessaging.ALL_ACCOUNTS_BUNDLE_VALUE);
			retryIntent.putExtra(PushMessaging.ACCOUNT_BUNDLE_KEY, bundle);
        } else {
            retryIntent.putExtra(PushMessaging.ACCOUNT_BUNDLE_KEY, account.toBundle());
        }
        final PendingIntent retryPIntent = PendingIntent.getBroadcast(context,
        		1, retryIntent, PendingIntent.FLAG_ONE_SHOT);
        final AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), retryPIntent);
    }

    /**
     * This method is called when registration with GCM is successful.
     *
     * @param registrationId Registration ID received from GCM service.
     * @param account User account.
     */
    private void onRegistered(String registrationId, UserAccount account) {
        if (account == null) {
            SalesforceSDKLogger.e(TAG, "Account is null, will retry registration later");
            return;
        }
    	long retryInterval = SFDC_REGISTRATION_RETRY;
    	try {
        	final String id = registerSFDCPushNotification(registrationId, account);
        	if (id != null) {
        		retryInterval = MILLISECONDS_IN_SIX_DAYS;
        		PushMessaging.setRegistrationInfo(context, registrationId, id,
        				account);
        	} else {
            	PushMessaging.setRegistrationId(context, registrationId, account);
        	}
    	} catch (Exception e) {
            SalesforceSDKLogger.e(TAG, "Error occurred during SFDC registration", e);
    	} finally {
            scheduleSFDCRegistrationRetry(retryInterval, null);
    	}
    }

    /**
     * This method is called when the device has been un-registered.
     *
     * @param account User account.
     */
    private void onUnregistered(UserAccount account) {
    	try {
        	final String id = PushMessaging.getDeviceId(context, account);
        	unregisterSFDCPushNotification(id, account);
    	} catch (Exception e) {
            SalesforceSDKLogger.e(TAG, "Error occurred during SFDC unregistration", e);
    	} finally {
        	PushMessaging.clearRegistrationInfo(context, account);
            context.sendBroadcast((new Intent(PushMessaging.UNREGISTERED_ATTEMPT_COMPLETE_EVENT)).setPackage(context.getPackageName()));
            context.sendBroadcast((new Intent(PushMessaging.UNREGISTERED_EVENT)).setPackage(context.getPackageName()));
        }
    }

    /**
     * Hits the Salesforce endpoint to register for push notifications.
     *
     * @param registrationId Registration ID.
     * @param account User account.
     * @return Salesforce ID that uniquely identifies the registered device.
     */
    private String registerSFDCPushNotification(String registrationId,
    		UserAccount account) {
    	final Map<String, Object> fields = new HashMap<String, Object>();
    	fields.put(CONNECTION_TOKEN, registrationId);
    	fields.put(SERVICE_TYPE, ANDROID_GCM);
    	try {
    		final RestClient client = getRestClient(account);
        	final RestRequest req = RestRequest.getRequestForCreate(ApiVersionStrings.getVersionNumber(context),
        			MOBILE_PUSH_SERVICE_DEVICE, fields);
        	if (client != null) {
            	final RestResponse res = client.sendSync(req);
            	String id = null;

            	/*
            	 * If the push notification device object has been created,
            	 * reads the device registration ID. If the status code
            	 * indicates that the resource is not found, push notifications
            	 * are not enabled for this connected app, which means we
            	 * should not attempt to re-register a few minutes later.
            	 */
            	if (res.getStatusCode() == HttpURLConnection.HTTP_CREATED) {
            		final JSONObject obj = res.asJSONObject();
            		if (obj != null) {
            			id = obj.getString(FIELD_ID);
            		}
            	} else if (res.getStatusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
            		id = NOT_ENABLED;
            	}
            	res.consume();
                SalesforceSDKManager.getInstance().registerUsedAppFeature(FEATURE_PUSH_NOTIFICATIONS);
            	return id;
        	}
    	} catch (Exception e) {
            SalesforceSDKLogger.e(TAG, "Push notification registration failed", e);
    	}
    	return null;
    }

    /**
     * Hits the Salesforce endpoint to un-register from push notifications.
     *
     * @param registeredId Salesforce ID that uniquely identifies the registered device.
     * @param account User account.
     * @return True - if un-registration was successful, False - otherwise.
     */
    private boolean unregisterSFDCPushNotification(String registeredId,
    		UserAccount account) {
    	final RestRequest req = RestRequest.getRequestForDelete(ApiVersionStrings.getVersionNumber(context),
    			MOBILE_PUSH_SERVICE_DEVICE, registeredId);
    	try {
    		final RestClient client = getRestClient(account);
    		if (client != null) {
            	final RestResponse res = client.sendSync(req);
            	if (res.getStatusCode() == HttpURLConnection.HTTP_NO_CONTENT) {
            		return true;
            	}
            	res.consume();
    		}
    	} catch (IOException e) {
			SalesforceSDKLogger.e(TAG, "Push notification unregistration failed", e);
    	}
    	return false;
    }

    /**
     * Gets an instance of RestClient.
     *
     * @param account User account.
     * @return Instance of RestClient.
     */
    private RestClient getRestClient(UserAccount account) {
    	final ClientManager cm = SalesforceSDKManager.getInstance().getClientManager();
    	RestClient client = null;

    	/*
    	 * The reason we can't directly call 'peekRestClient()' here is because
    	 * ClientManager does not hand out a rest client when a logout is in
    	 * progress. Hence, we build a rest client here manually, with the
    	 * available data in the 'account' object.
    	 */
    	if (cm != null) {
    		try {
    	        final AccMgrAuthTokenProvider authTokenProvider = new AccMgrAuthTokenProvider(cm,
						account.getInstanceServer(), account.getAuthToken(), account.getRefreshToken());
    			final ClientInfo clientInfo = new ClientInfo(account.getClientId(),
    					new URI(account.getInstanceServer()), new URI(account.getLoginServer()),
    					new URI(account.getIdUrl()), account.getAccountName(), account.getUsername(),
    	        		account.getUserId(), account.getOrgId(),
    	        		account.getCommunityId(), account.getCommunityUrl(),
						account.getFirstName(), account.getLastName(), account.getDisplayName(), account.getEmail(),
						account.getPhotoUrl(), account.getThumbnailUrl(), account.getAdditionalOauthValues());
                client = new RestClient(clientInfo, account.getAuthToken(),
                		HttpAccess.DEFAULT, authTokenProvider);
    		} catch (Exception e) {
                SalesforceSDKLogger.e(TAG, "Failed to get rest client", e);
    		}
    	}
    	return client;
    }

    /**
     * Broadcast receiver to retry SFDC push registration.
     *
     * @author ktanna
     */
    public static class SFDCRegistrationRetryAlarmReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
			if (intent != null) {
				final Bundle accBundle = intent.getBundleExtra(PushMessaging.ACCOUNT_BUNDLE_KEY);
				if (accBundle != null) {
					final String allAccountsValue = accBundle.getString(PushMessaging.ACCOUNT_BUNDLE_KEY);
					if (PushMessaging.ALL_ACCOUNTS_BUNDLE_VALUE.equals(allAccountsValue)) {
						PushMessaging.registerSFDCPush(context, null);
					} else {
						PushMessaging.registerSFDCPush(context, new UserAccount(accBundle));
					}
				}
			}
		}
	}
}
