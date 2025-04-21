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
package com.salesforce.androidsdk.push

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PRIVATE
import androidx.annotation.VisibleForTesting.Companion.PROTECTED
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy.UPDATE
import androidx.work.ExistingWorkPolicy.REPLACE
import androidx.work.NetworkType.CONNECTED
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.analytics.security.Encryptor
import com.salesforce.androidsdk.app.Features.FEATURE_PUSH_NOTIFICATIONS
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.push.PushMessaging.UNREGISTERED_ATTEMPT_COMPLETE_EVENT
import com.salesforce.androidsdk.push.PushMessaging.UNREGISTERED_EVENT
import com.salesforce.androidsdk.push.PushMessaging.clearNotificationsTypes
import com.salesforce.androidsdk.push.PushMessaging.clearRegistrationInfo
import com.salesforce.androidsdk.push.PushMessaging.getDeviceId
import com.salesforce.androidsdk.push.PushMessaging.getRegistrationId
import com.salesforce.androidsdk.push.PushMessaging.setNotificationTypes
import com.salesforce.androidsdk.push.PushMessaging.setRegistrationId
import com.salesforce.androidsdk.push.PushMessaging.setRegistrationInfo
import com.salesforce.androidsdk.push.PushNotificationsRegistrationChangeWorker.PushNotificationsRegistrationAction
import com.salesforce.androidsdk.push.PushNotificationsRegistrationChangeWorker.PushNotificationsRegistrationAction.Register
import com.salesforce.androidsdk.push.PushService.PushNotificationReRegistrationType.ReRegisterPeriodically
import com.salesforce.androidsdk.push.PushService.PushNotificationReRegistrationType.ReRegistrationDisabled
import com.salesforce.androidsdk.push.PushService.PushNotificationReRegistrationType.ReRegistrationOnAppForeground
import com.salesforce.androidsdk.rest.ApiVersionStrings
import com.salesforce.androidsdk.rest.ClientManager.AccMgrAuthTokenProvider
import com.salesforce.androidsdk.rest.NotificationsApiClient
import com.salesforce.androidsdk.rest.NotificationsTypesResponseBody
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestClient.ClientInfo
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestResponse
import com.salesforce.androidsdk.security.KeyStoreWrapper
import com.salesforce.androidsdk.security.SalesforceKeyGenerator
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import java.io.IOException
import java.net.HttpURLConnection.HTTP_CREATED
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.URI
import java.util.concurrent.TimeUnit.DAYS

/**
 * Provides a default implementation of push notifications registration and
 * receipt features using the Salesforce connected app endpoint.
 *
 * @author bhariharan
 * @author ktanna
 * @author Eric C. Johnson (Johnson.Eric>@Salesforce.com)
 */
open class PushService {
    fun performRegistrationChange(
        register: Boolean,
        userAccount: UserAccount,
        restClient: RestClient? = getRestClient(userAccount)
    ) {
        val restClientUnwrapped = restClient ?: return
        when {
            register ->
                onRegistered(
                    registrationId = getRegistrationId(
                        SalesforceSDKManager.getInstance().appContext,
                        userAccount
                    ) ?: return,
                    account = userAccount,
                    restClient = restClientUnwrapped,
                )

            else -> onUnregistered(
                account = userAccount,
                restClient = restClientUnwrapped,
            )
        }
    }

    @VisibleForTesting
    internal fun onRegistered(
        registrationId: String,
        account: UserAccount?,
        restClient: RestClient
    ) {
        if (account == null) {
            SalesforceSDKLogger.e(TAG, "Account is null, will retry registration later")
            return
        }

        val context = SalesforceSDKManager.getInstance().appContext

        runCatching {
            when (val id = registerSFDCPushNotification(
                registrationId,
                account,
                restClient
            )) {
                null -> setRegistrationId(
                    context,
                    registrationId, account
                )

                else -> setRegistrationInfo(
                    context,
                    registrationId, id, account
                )
            }
        }.onFailure { throwable ->
            SalesforceSDKLogger.e(
                TAG,
                "Error occurred during SFDC registration",
                throwable
            )
        }

        /*
         * When optionally specified, enqueue periodic SFDC API push
         * notifications re-registration for all users every six days via a
         * Android Background Tasks work request.
         */
        if (pushNotificationsRegistrationType == ReRegisterPeriodically) {
            enqueuePushNotificationsRegistrationWork(
                userAccount = null,
                action = Register,
                delayDays = 6
            )
        }
    }

    @VisibleForTesting
    internal fun onUnregistered(
        account: UserAccount,
        restClient: RestClient
    ) {
        val context = SalesforceSDKManager.getInstance().appContext
        val packageName = context.packageName

        runCatching {
            unregisterSFDCPushNotification(
                getDeviceId(context, account),
                account,
                restClient
            )
        }.onFailure { throwable ->
            SalesforceSDKLogger.e(
                TAG,
                "Error occurred during SFDC un-registration",
                throwable
            )
        }

        clearRegistrationInfo(context, account)
        context.sendBroadcast(
            Intent(
                UNREGISTERED_ATTEMPT_COMPLETE_EVENT
            ).setPackage(packageName)
        )
        context.sendBroadcast(
            Intent(
                UNREGISTERED_EVENT
            ).setPackage(packageName)
        )
    }

    /**
     * Send a request to register for push notifications and return the response
     * for further processing.
     *
     * Subclasses can override this method and return a custom response. Calling
     * to the super method is not required when overriding.
     *
     * @param requestBodyJsonFields the request body represented by a map of
     * root-level JSON fields
     * @param restClient a [RestClient] that can be used to make a new request
     * @return the response from registration
     * @throws IOException if the request could not be made
     */
    @Throws(IOException::class)
    protected fun onSendRegisterPushNotificationRequest(
        requestBodyJsonFields: Map<String, Any?>?,
        restClient: RestClient,
    ): RestResponse = restClient.sendSync(
        RestRequest.getRequestForCreate(
            ApiVersionStrings.getVersionNumber(
                SalesforceSDKManager.getInstance().appContext
            ),
            MOBILE_PUSH_SERVICE_DEVICE,
            requestBodyJsonFields
        )
    )

    /**
     * Called after Salesforce push notification registration.
     *
     * @param status the registration status. One of the
     * `REGISTRATION_STATUS_XXX` constants
     * @param restClient The REST client to use for network APIs
     * @param userAccount the user account that's performing registration
     */
    @VisibleForTesting
    internal fun onPushNotificationRegistrationStatusInternal(
        status: Int,
        restClient: RestClient,
        userAccount: UserAccount?
    ) {
        // Fetch and store or clear Salesforce notifications types, as applicable.
        refreshNotificationsTypes(
            status = status,
            restClient = restClient,
            userAccount = userAccount
        )

        // Allow subclass implementation.
        onPushNotificationRegistrationStatus(status, userAccount)
    }

    /**
     * Refreshes Salesforce notification types or clears them based on the
     * provided Salesforce push notification registration status.
     * @param status the registration status. One of the
     * `REGISTRATION_STATUS_XXX` constants
     * @param restClient The REST client to use for network APIs
     * @param userAccount the user account that's performing registration
     */
    @VisibleForTesting
    internal fun refreshNotificationsTypes(
        status: Int,
        restClient: RestClient,
        userAccount: UserAccount?
    ) {
        when (status) {
            REGISTRATION_STATUS_SUCCEEDED ->
                registerNotificationChannels(
                    fetchNotificationsTypes(
                        restClient = restClient,
                        userAccount = userAccount ?: return
                    ) ?: return
                )

            UNREGISTRATION_STATUS_SUCCEEDED -> {
                removeNotificationsCategories()
                clearNotificationsTypes(userAccount ?: return)
            }
        }
    }

    /**
     * Registers Android notification channels and notification groups for the
     * provided Salesforce notifications API notifications types.
     * @param notificationsTypesResponseBody The Salesforce notifications API
     * notifications types
     */
    @VisibleForTesting
    internal fun registerNotificationChannels(
        notificationsTypesResponseBody: NotificationsTypesResponseBody
    ) {
        val context = SalesforceSDKManager.getInstance().appContext
        context.getSystemService(NotificationManager::class.java).run {

            val notificationChannelGroup = getNotificationChannelGroup(
                NOTIFICATION_CHANNEL_GROUP_SALESFORCE_ID
            ) ?: run {
                createNotificationChannelGroup(
                    NotificationChannelGroup(
                        NOTIFICATION_CHANNEL_GROUP_SALESFORCE_ID,
                        NOTIFICATION_CHANNEL_GROUP_SALESFORCE_NAME
                    )
                )
                getNotificationChannelGroup(NOTIFICATION_CHANNEL_GROUP_SALESFORCE_ID)
            }

            notificationsTypesResponseBody.notificationTypes?.forEach { notificationType ->
                createNotificationChannel(
                    NotificationChannel(
                        notificationType.type,
                        notificationType.label,
                        IMPORTANCE_HIGH
                    ).apply {
                        group = notificationChannelGroup.id
                    }
                )
            }
        }
    }

    /**
     * Removes previously registered Android notification channels and
     * notification groups for Salesforce notifications API notifications types.
     */
    @VisibleForTesting
    internal fun removeNotificationsCategories() {
        val context = SalesforceSDKManager.getInstance().appContext
        context.getSystemService(NotificationManager::class.java).run {
            deleteNotificationChannelGroup(NOTIFICATION_CHANNEL_GROUP_SALESFORCE_ID)
        }
    }

    /**
     * Fetches notifications types and stores them for the provided user
     * account.
     * @param restClient The REST client to use for network APIs
     * @param userAccount The user account that's performing registration
     */
    @VisibleForTesting
    internal fun fetchNotificationsTypes(
        restClient: RestClient,
        userAccount: UserAccount
    ): NotificationsTypesResponseBody? {

        val notificationsTypes = NotificationsApiClient(
            restClient = restClient
        ).fetchNotificationsTypes()

        when (notificationsTypes) {
            null -> clearNotificationsTypes(userAccount)
            else -> setNotificationTypes(
                userAccount = userAccount,
                notificationsTypes = notificationsTypes
            )
        }

        return notificationsTypes
    }

    /**
     * Listen for changes in registration status.
     *
     * Subclasses can override this method without calling the super method.
     *
     * @param status the registration status. One of the
     * `REGISTRATION_STATUS_XXX` constants
     * @param userAccount the user account that's performing registration
     */
    @Suppress(
        "MemberVisibilityCanBePrivate",
        "unused"
    )
    @VisibleForTesting(otherwise = PROTECTED)
    internal open fun onPushNotificationRegistrationStatus(
        status: Int,
        userAccount: UserAccount?,
    ) {
        // Intentionally Blank.
    }

    @VisibleForTesting
    internal fun registerSFDCPushNotification(
        registrationId: String,
        account: UserAccount,
        restClient: RestClient
    ): String? {

        val sdkManager = SalesforceSDKManager.getInstance()
        val accountManager = UserAccountManager.getInstance()

        runCatching {
            val fields: MutableMap<String, String?> = mutableMapOf(
                CONNECTION_TOKEN to registrationId,
                SERVICE_TYPE to ANDROID_GCM,
                APPLICATION_BUNDLE to sdkManager.appContext.packageName
            )

            // Adds community ID to the registration payload to allow scoping of notifications per community.
            val communityId = accountManager.currentUser.communityId
            if (communityId?.isNotEmpty() == true) {
                fields[NETWORK_ID] = communityId
            }

            // Adds an RSA public key to the registration payload if available.
            rSAPublicKey?.let { rSAPublicKey ->
                if (rSAPublicKey.isNotEmpty()) {
                    fields[RSA_PUBLIC_KEY] = rSAPublicKey
                }
            }

            fields[CIPHER_NAME] = Encryptor.CipherMode.RSA_OAEP_SHA256.name

            var status = REGISTRATION_STATUS_FAILED
            val response = onSendRegisterPushNotificationRequest(fields, restClient)
            var id: String? = null

            /*
             * If the push notification device object has been created,
             * reads the device registration ID. If the status code
             * indicates that the resource is not found, push notifications
             * are not enabled for this connected app, which means we
             * should not attempt to re-register a few minutes later.
             */
            when (response.statusCode) {
                HTTP_CREATED -> {
                    response.asJSONObject()?.let { jsonObject ->
                        id = jsonObject.getString(FIELD_ID)
                        status = REGISTRATION_STATUS_SUCCEEDED
                    }
                }

                HTTP_NOT_FOUND -> id = NOT_ENABLED
            }

            response.consume()
            sdkManager.registerUsedAppFeature(FEATURE_PUSH_NOTIFICATIONS)
            onPushNotificationRegistrationStatusInternal(status = status, restClient = restClient, userAccount = account)

            return id
        }.onFailure { throwable ->
            SalesforceSDKLogger.e(TAG, "Push notification registration failed", throwable)
        }

        onPushNotificationRegistrationStatusInternal(status = REGISTRATION_STATUS_FAILED, restClient = restClient, userAccount = account)

        return null
    }

    @get:Synchronized
    private val rSAPublicKey: String?
        get() {
            val keyStoreWrapper = KeyStoreWrapper.getInstance()

            var publicKey: String? = null
            if (pushNotificationKeyName.isNotEmpty()) {
                publicKey = keyStoreWrapper.getRSAPublicString(pushNotificationKeyName)
            }
            return publicKey
        }

    /**
     * Send a request to unregister for push notifications and return the
     * response for further processing.
     *
     * Subclasses can override this method and return a custom response. Calling
     * to the super method is not required when overriding.
     *
     * @param registeredId the id that identifies this device with the push
     * notification provider
     * @param restClient a [RestClient] that can be used to make a new request
     * @return the response from un-registration
     * @throws IOException if the request could not be made
     */
    @Throws(IOException::class)
    protected fun onSendUnregisterPushNotificationRequest(
        registeredId: String?,
        restClient: RestClient,
    ): RestResponse {
        return restClient.sendSync(
            RestRequest.getRequestForDelete(
                ApiVersionStrings.getVersionNumber(
                    SalesforceSDKManager.getInstance().appContext
                ),
                MOBILE_PUSH_SERVICE_DEVICE,
                registeredId
            )
        )
    }

    @VisibleForTesting
    protected open fun unregisterSFDCPushNotification(
        registeredId: String?,
        account: UserAccount,
        restClient: RestClient
    ) {
        runCatching {
            onSendUnregisterPushNotificationRequest(
                registeredId,
                restClient
            ).consume()
            onPushNotificationRegistrationStatusInternal(status = UNREGISTRATION_STATUS_SUCCEEDED, restClient = restClient, userAccount = account)
        }.onFailure { throwable ->
            onPushNotificationRegistrationStatusInternal(status = UNREGISTRATION_STATUS_FAILED, restClient = restClient, userAccount = account)

            SalesforceSDKLogger.e(
                TAG,
                "Push notification un-registration failed",
                throwable
            )
        }
    }

    private fun getRestClient(
        account: UserAccount,
    ) = SalesforceSDKManager.getInstance().clientManager.let { clientManager ->

        /*
         * The reason we can't directly call 'peekRestClient()' here is because
         * ClientManager does not hand out a REST client when a logout is in
         * progress. Hence, we build a REST client here manually, with the
         * available data in the 'account' object.
         */
        runCatching {
            RestClient(
                ClientInfo(
                    URI(account.instanceServer),
                    URI(account.loginServer),
                    URI(account.idUrl),
                    account.accountName,
                    account.username,
                    account.userId,
                    account.orgId,
                    account.communityId,
                    account.communityUrl,
                    account.firstName,
                    account.lastName,
                    account.displayName,
                    account.email,
                    account.photoUrl,
                    account.thumbnailUrl,
                    account.additionalOauthValues,
                    account.lightningDomain,
                    account.lightningSid,
                    account.vfDomain,
                    account.vfSid,
                    account.contentDomain,
                    account.contentSid,
                    account.csrfToken
                ),
                account.authToken,
                HttpAccess.DEFAULT,
                AccMgrAuthTokenProvider(
                    clientManager,
                    account.instanceServer,
                    account.authToken,
                    account.refreshToken
                )
            )
        }.onFailure { throwable ->
            SalesforceSDKLogger.e(
                TAG,
                "Failed to get REST client",
                throwable
            )
        }.getOrNull()
    }

    companion object {
        private const val TAG = "PushService"

        /** The active push notifications registration type */
        var pushNotificationsRegistrationType = ReRegistrationOnAppForeground

        // Salesforce push notification constants.
        private const val MOBILE_PUSH_SERVICE_DEVICE = "MobilePushServiceDevice"
        private const val ANDROID_GCM = "androidGcm"
        private const val SERVICE_TYPE = "ServiceType"
        private const val NETWORK_ID = "NetworkId"
        private const val RSA_PUBLIC_KEY = "RsaPublicKey"
        private const val CONNECTION_TOKEN = "ConnectionToken"
        private const val APPLICATION_BUNDLE = "ApplicationBundle"
        private const val CIPHER_NAME = "CipherName"
        private const val FIELD_ID = "id"

        @VisibleForTesting
        internal const val NOT_ENABLED = "not_enabled"
        const val PUSH_NOTIFICATION_KEY_NAME = "PushNotificationKey"
        val pushNotificationKeyName = SalesforceKeyGenerator
            .getUniqueId(PUSH_NOTIFICATION_KEY_NAME)
            .replace("[^A-Za-z0-9]".toRegex(), "")

        /**
         * The push notification channel group id for push notification channels
         * registered from Salesforce Notification API notifications types
         */
        @VisibleForTesting
        internal const val NOTIFICATION_CHANNEL_GROUP_SALESFORCE_ID = "NOTIFICATION_GROUP_SALESFORCE"

        /**
         * The push notification channel group name for push notification
         * channels registered from Salesforce Notification API notifications
         * types
         */
        private const val NOTIFICATION_CHANNEL_GROUP_SALESFORCE_NAME = "Salesforce Notifications"

        @VisibleForTesting(otherwise = PROTECTED)
        internal const val REGISTRATION_STATUS_SUCCEEDED = 0

        @VisibleForTesting(otherwise = PROTECTED)
        internal const val REGISTRATION_STATUS_FAILED = 1

        @VisibleForTesting(otherwise = PROTECTED)
        internal const val UNREGISTRATION_STATUS_SUCCEEDED = 2

        @VisibleForTesting(otherwise = PROTECTED)
        internal const val UNREGISTRATION_STATUS_FAILED = 3

        /**
         * The Android background tasks name of the push notifications
         * unregistration work request
         */
        private const val PUSH_NOTIFICATIONS_UNREGISTRATION_WORK_NAME = "SalesforcePushNotificationsUnregistrationWork"

        /**
         * The Android background tasks name of the push notifications
         * registration work request
         */
        private const val PUSH_NOTIFICATIONS_REGISTRATION_WORK_NAME = "SalesforcePushNotificationsRegistrationWork"

        /**
         * Enqueues a change to one or more user accounts' push notifications
         * registration as persistent work via Android background tasks.
         *
         * @param userAccount The user account or null for all user accounts
         * @param action The push notifications registration action
         * @param pushNotificationsRegistrationType Optionally, a specific
         * push notification registration type.  Defaults to the current
         * push notification registration type applied to this class
         * @param delayDays For registration actions, the interval in days
         * between periodic registration or null for immediate one-time
         * registration
         */
        internal fun enqueuePushNotificationsRegistrationWork(
            userAccount: UserAccount?,
            action: PushNotificationsRegistrationAction,
            pushNotificationsRegistrationType: PushNotificationReRegistrationType = this.pushNotificationsRegistrationType,
            delayDays: Long?,
        ) {
            val context = SalesforceSDKManager.getInstance().appContext
            val workManager = WorkManager.getInstance(context)
            // Require network connectivity in case the user is logging out while offline.
            val constraints = Constraints.Builder().setRequiredNetworkType(CONNECTED).build()
            val userAccountJson = userAccount?.toJson()?.toString()
            val workData = Data.Builder()
                .putString("USER_ACCOUNT", userAccountJson)
                .putString("ACTION", action.name)
                .build()

            if (action == Register) {
                when (pushNotificationsRegistrationType) {
                    ReRegistrationDisabled -> {
                        /* Intentionally Blank */
                    }

                    ReRegistrationOnAppForeground -> OneTimeWorkRequest.Builder(
                        workerClass = PushNotificationsRegistrationChangeWorker::class.java
                    ).setInputData(workData)
                        .setConstraints(constraints)
                        .build().also { workRequest ->
                            workManager.enqueueUniqueWork(
                                PUSH_NOTIFICATIONS_REGISTRATION_WORK_NAME,
                                REPLACE,
                                workRequest
                            )
                        }

                    ReRegisterPeriodically -> PeriodicWorkRequest.Builder(
                        workerClass = PushNotificationsRegistrationChangeWorker::class.java,
                        repeatInterval = delayDays ?: 6,
                        repeatIntervalTimeUnit = DAYS
                    ).setInputData(workData)
                        .setConstraints(constraints)
                        .build().also { workRequest ->
                            workManager.enqueueUniquePeriodicWork(
                                PUSH_NOTIFICATIONS_REGISTRATION_WORK_NAME,
                                UPDATE,
                                workRequest
                            )
                        }
                }
            } else {
                // Deregister
                OneTimeWorkRequest.Builder(PushNotificationsRegistrationChangeWorker::class.java)
                    .setInputData(workData)
                    .setConstraints(constraints)
                    .build().also { workRequest ->
                        workManager.enqueueUniqueWork(
                            PUSH_NOTIFICATIONS_UNREGISTRATION_WORK_NAME,
                            REPLACE,
                            workRequest
                        )
                    }

                // Send broadcast now to finish logout if we are offline.
                if (!HttpAccess.DEFAULT.hasNetwork()) {
                    context.sendBroadcast(
                        Intent(
                            UNREGISTERED_ATTEMPT_COMPLETE_EVENT
                        ).setPackage(context.packageName)
                    )
                }
            }
        }
    }

    enum class PushNotificationReRegistrationType {

        /** Specifies push notification (re-)registration should not occur */
        ReRegistrationDisabled,

        /**
         * Specifies push notification (re-)registration should occur one time when the app is
         * brought to the foreground
         */
        ReRegistrationOnAppForeground,

        /**
         * Specifies push notification (re-)registration should occur for all users every six days
         * via a Android Background Tasks work request.  This re-registers users for push
         * notifications so long as the app is installed even though SFDC API periodically
         * de-registers push notifications
         */
        ReRegisterPeriodically
    }
}
