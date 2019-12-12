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
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.core.app.JobIntentService;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.app.Features;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.rest.ApiVersionStrings;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.AccMgrAuthTokenProvider;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestClient.ClientInfo;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.security.KeyStoreWrapper;
import com.salesforce.androidsdk.security.SalesforceKeyGenerator;
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
public class PushService extends JobIntentService {

	private static final String TAG = "PushService";

    // Intent actions.
    public static final String SFDC_REGISTRATION_RETRY_INTENT = "com.salesforce.mobilesdk.c2dm.intent.RETRY";
    public static final String SFDC_UNREGISTRATION_INTENT = "com.salesforce.mobilesdk.c2dm.intent.UNREGISTER";

	// Retry time constants.
    private static final long MILLISECONDS_IN_SIX_DAYS = 518400000L;
    private static final long SFDC_REGISTRATION_RETRY = 30000;

    // Unique identifier for this job.
	private static final int JOB_ID = 24;

    // Salesforce push notification constants.
    private static final String MOBILE_PUSH_SERVICE_DEVICE = "MobilePushServiceDevice";
    private static final String ANDROID_GCM = "androidGcm";
    private static final String SERVICE_TYPE = "ServiceType";
    private static final String NETWORK_ID = "NetworkId";
    private static final String RSA_PUBLIC_KEY = "RsaPublicKey";
    private static final String CONNECTION_TOKEN = "ConnectionToken";
    private static final String APPLICATION_BUNDLE = "ApplicationBundle";
    private static final String FIELD_ID = "id";
    private static final String NOT_ENABLED = "not_enabled";
	static final String PUSH_NOTIFICATION_KEY_NAME = "PushNotificationKey";
	protected static final int REGISTRATION_STATUS_SUCCEEDED = 0;
	protected static final int REGISTRATION_STATUS_FAILED = 1;
	protected static final int UNREGISTRATION_STATUS_SUCCEEDED = 2;
	protected static final int UNREGISTRATION_STATUS_FAILED = 3;

    /**
     * This method is called from the broadcast receiver, when a push notification
     * is received, or when we receive a callback from the GCM service. Processing
     * of the message occurs here, and the acquired wake lock is released post processing.
     *
     * @param intent Intent.
     */
    static void runIntentInService(Intent intent) {
        final Context context = SalesforceSDKManager.getInstance().getAppContext();
        intent.setClassName(context, SalesforceSDKManager.getInstance().getPushServiceType().getName());
        enqueueWork(context, SalesforceSDKManager.getInstance().getPushServiceType(), JOB_ID, intent);
    }

	@Override
	protected void onHandleWork(Intent intent) {

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
		boolean register = SFDC_REGISTRATION_RETRY_INTENT.equals(intent.getAction());
		boolean unregister = SFDC_UNREGISTRATION_INTENT.equals(intent.getAction());
		if (register || unregister) {
			if (allAccounts) {
				if (accounts != null) {
					for (final UserAccount userAcc : accounts) {

						/*
						 * If 'register' is true, we are registering, if it's false, we must be
						 * un-registering, because of the if gate above.
						 */
						performRegistrationChange(register, userAcc);
					}
				}
			} else {
				if (account == null) {
					account = userAccMgr.getCurrentUser();
				}

				/*
				 * If 'register' is true, we are registering, if it's false, we must be
				 * un-registering, because of the if gate above.
				 */
				performRegistrationChange(register, account);
			}
		}
	}

	private void performRegistrationChange(boolean register, UserAccount userAccount) {
		if (register) {
			final String regId = PushMessaging.getRegistrationId(SalesforceSDKManager.getInstance().getAppContext(),
                    userAccount);
			if (regId != null) {
				onRegistered(regId, userAccount);
			}
		} else {
			onUnregistered(userAccount);
		}
	}

    private void scheduleSFDCRegistrationRetry(long when, UserAccount account) {
        final Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MILLISECOND, (int) when);
        final Intent retryIntent = new Intent(SalesforceSDKManager.getInstance().getAppContext(),
                SFDCRegistrationRetryAlarmReceiver.class);
        if (account == null) {
			final Bundle bundle = new Bundle();
			bundle.putString(PushMessaging.ACCOUNT_BUNDLE_KEY, PushMessaging.ALL_ACCOUNTS_BUNDLE_VALUE);
			retryIntent.putExtra(PushMessaging.ACCOUNT_BUNDLE_KEY, bundle);
        } else {
            retryIntent.putExtra(PushMessaging.ACCOUNT_BUNDLE_KEY, account.toBundle());
        }
        final PendingIntent retryPIntent = PendingIntent.getBroadcast(SalesforceSDKManager.getInstance().getAppContext(),
        		1, retryIntent, PendingIntent.FLAG_ONE_SHOT);
        final AlarmManager am = (AlarmManager) SalesforceSDKManager.getInstance().getAppContext().getSystemService(Context.ALARM_SERVICE);
        am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), retryPIntent);
    }

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
        		PushMessaging.setRegistrationInfo(SalesforceSDKManager.getInstance().getAppContext(),
                        registrationId, id, account);
        	} else {
            	PushMessaging.setRegistrationId(SalesforceSDKManager.getInstance().getAppContext(),
                        registrationId, account);
        	}
    	} catch (Exception e) {
            SalesforceSDKLogger.e(TAG, "Error occurred during SFDC registration", e);
    	} finally {
            scheduleSFDCRegistrationRetry(retryInterval, null);
    	}
    }

    private void onUnregistered(UserAccount account) {
        final Context context = SalesforceSDKManager.getInstance().getAppContext();
    	try {
        	final String id = PushMessaging.getDeviceId(context, account);
        	unregisterSFDCPushNotification(id, account);
    	} catch (Exception e) {
            SalesforceSDKLogger.e(TAG, "Error occurred during SFDC un-registration", e);
    	} finally {
        	PushMessaging.clearRegistrationInfo(context, account);
            context.sendBroadcast((new Intent(PushMessaging.UNREGISTERED_ATTEMPT_COMPLETE_EVENT)).setPackage(context.getPackageName()));
            context.sendBroadcast((new Intent(PushMessaging.UNREGISTERED_EVENT)).setPackage(context.getPackageName()));
        }
    }

	/**
	 * Send a request to register for push notifications and return the response for further processing.
	 *
	 * <p>
	 * Subclasses can override this method and return a custom response. Calling to the super method
	 * is not required when overriding.
	 * </p>
	 *
	 * @param requestBodyJsonFields the request body represented by a map of root-level JSON fields
	 * @param restClient a {@link RestClient} that can be used to make a new request
	 * @return the response from registration
	 * @throws IOException if the request could not be made
	 */
	protected RestResponse onSendRegisterPushNotificationRequest(
			Map<String, Object> requestBodyJsonFields,
			RestClient restClient) throws IOException {
		return restClient.sendSync(RestRequest.getRequestForCreate(
				ApiVersionStrings.getVersionNumber(SalesforceSDKManager.getInstance().getAppContext()),
                MOBILE_PUSH_SERVICE_DEVICE, requestBodyJsonFields));
	}

    /**
     * Listen for changing in registration status.
     *
     * <p>
     * Subclasses can override this method without calling the super method.
     * </p>
     *
     * @param status the registration status. One of the {@code REGISTRATION_STATUS_XXX} constants
     * @param userAccount the user account that's performing registration
     */
	protected void onPushNotificationRegistrationStatus(int status, UserAccount userAccount) {

		// Do nothing.
	}

    private String registerSFDCPushNotification(String registrationId, UserAccount account) {
    	try {
            final Map<String, Object> fields = new HashMap<>();
            fields.put(CONNECTION_TOKEN, registrationId);
            fields.put(SERVICE_TYPE, ANDROID_GCM);
            fields.put(APPLICATION_BUNDLE, SalesforceSDKManager.getInstance().getAppContext().getPackageName());

            // Adds community ID to the registration payload to allow scoping of notifications per community.
            final String communityId = UserAccountManager.getInstance().getCurrentUser().getCommunityId();
            if (!TextUtils.isEmpty(communityId)) {
            	fields.put(NETWORK_ID, communityId);
			}

            // Adds an RSA public key to the registration payload if available.
            final String rsaPublicKey = getRSAPublicKey();
            if (!TextUtils.isEmpty(rsaPublicKey)) {
				fields.put(RSA_PUBLIC_KEY, rsaPublicKey);
			}
            final RestClient client = getRestClient(account);
        	if (client != null) {
                int status = REGISTRATION_STATUS_FAILED;
                final RestResponse res = onSendRegisterPushNotificationRequest(fields, client);
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
                        status = REGISTRATION_STATUS_SUCCEEDED;
            		}
            	} else if (res.getStatusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                    id = NOT_ENABLED;
                    status = REGISTRATION_STATUS_FAILED;
            	}
            	res.consume();
                SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_PUSH_NOTIFICATIONS);
                onPushNotificationRegistrationStatus(status, account);
            	return id;
        	}
    	} catch (Exception e) {
            SalesforceSDKLogger.e(TAG, "Push notification registration failed", e);
    	}
        onPushNotificationRegistrationStatus(REGISTRATION_STATUS_FAILED, account);
    	return null;
    }

    private synchronized String getRSAPublicKey() {
		String rsaPublicKey = null;
		final String name = SalesforceKeyGenerator.getUniqueId(PUSH_NOTIFICATION_KEY_NAME);
		final String sanitizedName = name.replaceAll("[^A-Za-z0-9]", "");
		if (!TextUtils.isEmpty(sanitizedName)) {
			rsaPublicKey = KeyStoreWrapper.getInstance().getRSAPublicString(sanitizedName);
		}
		return rsaPublicKey;
	}

	/**
	 * Send a request to unregister for push notifications and return the response for further processing.
	 *
	 * <p>
	 * Subclasses can override this method and return a custom response. Calling to the super method
	 * is not required when overriding.
	 * </p>
	 *
	 * @param registeredId the id that identifies this device with the push notification provider
	 * @param restClient a {@link RestClient} that can be used to make a new request
	 * @return the response from unregistration
	 * @throws IOException if the request could not be made
	 */
	protected RestResponse onSendUnregisterPushNotificationRequest(String registeredId,
			RestClient restClient) throws IOException {
		return restClient.sendSync(RestRequest.getRequestForDelete(
				ApiVersionStrings.getVersionNumber(SalesforceSDKManager.getInstance().getAppContext()),
                MOBILE_PUSH_SERVICE_DEVICE, registeredId));
	}

    private void unregisterSFDCPushNotification(String registeredId, UserAccount account) {
    	try {
    		final RestClient client = getRestClient(account);
    		if (client != null) {
                onSendUnregisterPushNotificationRequest(registeredId, client).consume();
                onPushNotificationRegistrationStatus(UNREGISTRATION_STATUS_SUCCEEDED, account);
    		}
    	} catch (IOException e) {
            onPushNotificationRegistrationStatus(UNREGISTRATION_STATUS_FAILED, account);
			SalesforceSDKLogger.e(TAG, "Push notification un-registration failed", e);
    	}
    }

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
    			final ClientInfo clientInfo = new ClientInfo(new URI(account.getInstanceServer()),
						new URI(account.getLoginServer()), new URI(account.getIdUrl()),
                        account.getAccountName(), account.getUsername(),
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
