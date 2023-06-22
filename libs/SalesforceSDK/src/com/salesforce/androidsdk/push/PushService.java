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

import static com.salesforce.androidsdk.push.PushNotificationsRegistrationChangeWorker.PushNotificationsRegistrationAction.Register;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.app.Features;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.push.PushNotificationsRegistrationChangeWorker.PushNotificationsRegistrationAction;
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
import java.util.HashMap;
import java.util.Map;

/**
 * Provides a default implementation of push notifications registration and
 * receipt features using the Salesforce connected app endpoint.
 *
 * @author bhariharan
 * @author ktanna
 * @author Eric C. Johnson <Johnson.Eric@Salesforce.com>
 */
public class PushService {

	private static final String TAG = "PushService";

	// Retry time constants.
    private static final long MILLISECONDS_IN_SIX_DAYS = 518400000L;

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
	 * Enqueues a change to one or more user accounts' push notifications
	 * registration as persistent work via Android background tasks.
	 *
	 * @param userAccount       The user account or null for all user accounts
	 * @param action            The push notifications registration action
	 * @param delayMilliseconds The amount of delay before the push registration
	 *                          action is taken, such as in a retry scenario
	 */
	static void enqueuePushNotificationsRegistrationWork(
			@Nullable UserAccount userAccount,
			@NonNull PushNotificationsRegistrationAction action,
			@Nullable Long delayMilliseconds) {
		final Context context = SalesforceSDKManager.getInstance().getAppContext();
		final WorkManager workManager = WorkManager.getInstance(context);
		final String userAccountJson = userAccount == null ? null : userAccount.toJson().toString();
		final Data workData = new Data.Builder().putString(
				"USER_ACCOUNT",
				userAccountJson
		).putString(
				"ACTION",
				action.name()
		).build();
		final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(
				PushNotificationsRegistrationChangeWorker.class
		).setInputData(
				workData
		).setInitialDelay(
				delayMilliseconds == null ? 0 : delayMilliseconds,
				MILLISECONDS
		).build();

		workManager.enqueue(workRequest);
	}

	void performRegistrationChange(boolean register, UserAccount userAccount) {
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

    private void onRegistered(String registrationId, UserAccount account) {
        if (account == null) {
            SalesforceSDKLogger.e(TAG, "Account is null, will retry registration later");
            return;
        }
    	try {
        	final String id = registerSFDCPushNotification(registrationId, account);
        	if (id != null) {
        		PushMessaging.setRegistrationInfo(SalesforceSDKManager.getInstance().getAppContext(),
                        registrationId, id, account);
        	} else {
            	PushMessaging.setRegistrationId(SalesforceSDKManager.getInstance().getAppContext(),
                        registrationId, account);
        	}
    	} catch (Exception e) {
            SalesforceSDKLogger.e(TAG, "Error occurred during SFDC registration", e);
    	} finally {
			/*
			 * Although each user account should be registered with the SFDC API
			 * for push notifications on login and Firebase push notifications
			 * registration, enqueue an additional SFDC API push notifications
			 * registration for all users six days from now.  This may provide
			 * an additional level of registration verification, though the
			 * actual requirements may need more definition in the future.
			 */
            enqueuePushNotificationsRegistrationWork(
					null, // All user accounts.
					Register,
					MILLISECONDS_IN_SIX_DAYS
			);
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
	@SuppressWarnings("unused")
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
						account.getPhotoUrl(), account.getThumbnailUrl(), account.getAdditionalOauthValues(),
						account.getLightningDomain(), account.getLightningSid(),
						account.getVFDomain(), account.getVFSid(),
						account.getContentDomain(), account.getContentSid(), account.getCSRFToken());
                client = new RestClient(clientInfo, account.getAuthToken(),
                		HttpAccess.DEFAULT, authTokenProvider);
    		} catch (Exception e) {
                SalesforceSDKLogger.e(TAG, "Failed to get rest client", e);
    		}
    	}
    	return client;
    }
}
