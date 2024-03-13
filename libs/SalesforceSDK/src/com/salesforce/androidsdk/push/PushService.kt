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

import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.app.Features.FEATURE_PUSH_NOTIFICATIONS
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.HttpAccess.DEFAULT
import com.salesforce.androidsdk.push.PushMessaging.UNREGISTERED_ATTEMPT_COMPLETE_EVENT
import com.salesforce.androidsdk.push.PushMessaging.UNREGISTERED_EVENT
import com.salesforce.androidsdk.push.PushMessaging.clearRegistrationInfo
import com.salesforce.androidsdk.push.PushMessaging.getDeviceId
import com.salesforce.androidsdk.push.PushMessaging.getRegistrationId
import com.salesforce.androidsdk.push.PushMessaging.setRegistrationId
import com.salesforce.androidsdk.push.PushMessaging.setRegistrationInfo
import com.salesforce.androidsdk.push.PushNotificationsRegistrationChangeWorker.PushNotificationsRegistrationAction
import com.salesforce.androidsdk.push.PushNotificationsRegistrationChangeWorker.PushNotificationsRegistrationAction.Register
import com.salesforce.androidsdk.rest.ApiVersionStrings
import com.salesforce.androidsdk.rest.ClientManager.AccMgrAuthTokenProvider
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
import java.util.concurrent.TimeUnit

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
        userAccount: UserAccount
    ) {
        when {
            register ->
                onRegistered(
                    registrationId = getRegistrationId(
                        SalesforceSDKManager.getInstance().appContext,
                        userAccount
                    ) ?: return,
                    account = userAccount
                )

            else -> onUnregistered(
                account = userAccount
            )
        }
    }

    private fun onRegistered(
        registrationId: String,
        account: UserAccount?
    ) {
        if (account == null) {
            SalesforceSDKLogger.e(TAG, "Account is null, will retry registration later")
            return
        }

        val context = SalesforceSDKManager.getInstance().appContext

        runCatching {
            when (val id = registerSFDCPushNotification(
                registrationId,
                account
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
         * Although each user account should be registered with the SFDC API
         * for push notifications on login and Firebase push notifications
         * registration, enqueue an additional SFDC API push notifications
         * registration for all users six days from now.  This may provide
         * an additional level of registration verification, though the
         * actual requirements may need more definition in the future.
         */
        enqueuePushNotificationsRegistrationWork(
            userAccount = null,
            action = Register,
            delayMilliseconds = MILLISECONDS_IN_SIX_DAYS
        )
    }

    private fun onUnregistered(account: UserAccount) {
        val context = SalesforceSDKManager.getInstance().appContext
        val packageName = context.packageName

        runCatching {
            unregisterSFDCPushNotification(
                getDeviceId(context, account),
                account
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
        restClient: RestClient
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
     * Listen for changes in registration status.
     *
     * Subclasses can override this method without calling the super method.
     *
     * @param status the registration status. One of the
     * `REGISTRATION_STATUS_XXX` constants
     * @param userAccount the user account that's performing registration
     */
    @Suppress(
        "unused",
        "MemberVisibilityCanBePrivate",
        "UNUSED_PARAMETER"
    )
    protected fun onPushNotificationRegistrationStatus(
        status: Int,
        userAccount: UserAccount?
    ) {
        // Intentionally Blank.
    }

    private fun registerSFDCPushNotification(
        registrationId: String,
        account: UserAccount
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

            getRestClient(account)?.let { restClient ->
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
                onPushNotificationRegistrationStatus(status, account)

                return id
            }
        }.onFailure { throwable ->
            SalesforceSDKLogger.e(TAG, "Push notification registration failed", throwable)
        }

        onPushNotificationRegistrationStatus(REGISTRATION_STATUS_FAILED, account)

        return null
    }

    @get:Synchronized
    private val rSAPublicKey: String?
        get() {
            val keyStoreWrapper = KeyStoreWrapper.getInstance()

            var rsaPublicKey: String? = null
            if (pushNotificationKeyName.isNotEmpty()) {
                rsaPublicKey = keyStoreWrapper.getRSAPublicString(pushNotificationKeyName)
            }
            return rsaPublicKey
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
        restClient: RestClient
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

    private fun unregisterSFDCPushNotification(
        registeredId: String?,
        account: UserAccount
    ) {
        runCatching {
            getRestClient(account)?.let { restClient ->
                onSendUnregisterPushNotificationRequest(
                    registeredId,
                    restClient
                ).consume()
                onPushNotificationRegistrationStatus(
                    UNREGISTRATION_STATUS_SUCCEEDED,
                    account
                )
            }
        }.onFailure { throwable ->
            onPushNotificationRegistrationStatus(
                UNREGISTRATION_STATUS_FAILED,
                account
            )
            SalesforceSDKLogger.e(
                TAG,
                "Push notification un-registration failed",
                throwable
            )
        }
    }

    private fun getRestClient(
        account: UserAccount
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
                DEFAULT,
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

        // Retry time constants.
        private const val MILLISECONDS_IN_SIX_DAYS = 518400000L

        // Salesforce push notification constants.
        private const val MOBILE_PUSH_SERVICE_DEVICE = "MobilePushServiceDevice"
        private const val ANDROID_GCM = "androidGcm"
        private const val SERVICE_TYPE = "ServiceType"
        private const val NETWORK_ID = "NetworkId"
        private const val RSA_PUBLIC_KEY = "RsaPublicKey"
        private const val CONNECTION_TOKEN = "ConnectionToken"
        private const val APPLICATION_BUNDLE = "ApplicationBundle"
        private const val FIELD_ID = "id"
        private const val NOT_ENABLED = "not_enabled"
        const val PUSH_NOTIFICATION_KEY_NAME = "PushNotificationKey"
        val pushNotificationKeyName = SalesforceKeyGenerator
            .getUniqueId(PUSH_NOTIFICATION_KEY_NAME)
            .replace("[^A-Za-z0-9]".toRegex(), "")

        protected const val REGISTRATION_STATUS_SUCCEEDED = 0
        protected const val REGISTRATION_STATUS_FAILED = 1
        protected const val UNREGISTRATION_STATUS_SUCCEEDED = 2
        protected const val UNREGISTRATION_STATUS_FAILED = 3

        /**
         * Enqueues a change to one or more user accounts' push notifications
         * registration as persistent work via Android background tasks.
         *
         * @param userAccount       The user account or null for all user accounts
         * @param action            The push notifications registration action
         * @param delayMilliseconds The amount of delay before the push registration
         * action is taken, such as in a retry scenario
         */
        internal fun enqueuePushNotificationsRegistrationWork(
            userAccount: UserAccount?,
            action: PushNotificationsRegistrationAction,
            delayMilliseconds: Long?
        ) {
            val context = SalesforceSDKManager.getInstance().appContext
            val workManager = WorkManager.getInstance(context)
            val userAccountJson = userAccount?.toJson()?.toString()
            val workData = Data.Builder().putString(
                "USER_ACCOUNT",
                userAccountJson
            ).putString(
                "ACTION",
                action.name
            ).build()
            val workRequest: OneTimeWorkRequest = OneTimeWorkRequest.Builder(
                PushNotificationsRegistrationChangeWorker::class.java
            ).setInputData(
                workData
            ).setInitialDelay(
                delayMilliseconds ?: 0, TimeUnit.MILLISECONDS
            ).build()
            workManager.enqueue(workRequest)
        }
    }
}
