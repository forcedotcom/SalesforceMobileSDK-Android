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

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.push.PushNotificationsRegistrationChangeWorker.PushNotificationsRegistrationAction
import com.salesforce.androidsdk.push.PushNotificationsRegistrationChangeWorker.PushNotificationsRegistrationAction.Deregister
import com.salesforce.androidsdk.push.PushNotificationsRegistrationChangeWorker.PushNotificationsRegistrationAction.Register
import com.salesforce.androidsdk.push.PushService.Companion.enqueuePushNotificationsRegistrationWork
import com.salesforce.androidsdk.push.PushService.PushNotificationReRegistrationType.ReRegistrationOnAppForeground
import com.salesforce.androidsdk.rest.NotificationsTypesResponseBody
import com.salesforce.androidsdk.security.KeyStoreWrapper
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import kotlinx.serialization.json.Json


/**
 * This class provides utility functions related to push notifications,
 * such as methods for registration, unregistration, and storage and
 * retrieval of push notification registration information from a
 * private shared preference file.
 */
object PushMessaging {
    private const val TAG = "PushMessaging"

    // Public constants.
    const val UNREGISTERED_ATTEMPT_COMPLETE_EVENT = "com.salesforce.mobilesdk.c2dm.UNREGISTERED"
    const val UNREGISTERED_EVENT = "com.salesforce.mobilesdk.c2dm.ACTUAL_UNREGISTERED"

    // Private constants.
    private const val GCM_PREFS = "gcm_prefs"
    private const val LAST_SFDC_REGISTRATION_TIME = "last_registration_change"
    private const val REGISTRATION_ID = "c2dm_registration_id"
    private const val DEVICE_ID = "deviceId"
    private const val IN_PROGRESS = "inprogress"
    private const val NOTIFICATIONS_TYPES = "notifications_types"

    // Re-register once push is setup
    @JvmStatic
    var reRegistrationRequested = false

    @JvmStatic
    fun onPushReceiverSetup(context: Context) {
        if (isPushSetup(context) && reRegistrationRequested) {
            register(context, null, true)
        }
    }

    /**
     * Initiates push registration, if the application is not already registered.
     * Otherwise, this method initiates registration/re-registration to the
     * SFDC endpoint, in order to keep it alive, or to register a new account
     * that just logged in.
     *
     * @param context Context.
     * @param account User account.
     */
    @JvmStatic
    fun register(context: Context, account: UserAccount?) {
        register(context = context, account = account, recreateKey = false)
    }

    /**
     * Initiates push registration, if the application is not already registered.
     * Otherwise, this method initiates registration/re-registration to the
     * SFDC endpoint, in order to keep it alive, or to register a new account
     * that just logged in.
     *
     * @param context Context.
     * @param account User account.
     * @param recreateKey true if RSA key should be regenerated
     */
    @JvmStatic
    fun register(context: Context, account: UserAccount?, recreateKey: Boolean) {
        if (isPushSetup(context)) {
            // Ensure Firebase is initialized
            getFirebaseApp(context)
            val firebaseMessaging = SalesforceSDKManager.getInstance()
                .pushNotificationReceiver?.supplyFirebaseMessaging() ?: FirebaseMessaging.getInstance()
            firebaseMessaging.isAutoInitEnabled = true

            /*
             * Performs registration steps if it is a new account, or if the
             * account hasn't been registered yet. Otherwise, performs
             * re-registration at the SFDC endpoint, to keep it alive.
             */
            if (account != null && !isRegistered(context, account)) {
                setInProgress(context, account)

                firebaseMessaging.token.addOnSuccessListener { token: String? ->
                    try {
                        // Store the new token.
                        setRegistrationId(context, token, account)

                        // Send it to SFDC.
                        registerSFDCPush(context, account)
                    } catch (e: Exception) {
                        SalesforceSDKLogger.e(TAG, "Error during FCM registration", e)
                    }
                }
            } else {
                // Delete existing key if recreateKey is true
                if (recreateKey) {
                    KeyStoreWrapper.getInstance().deleteKey(PushService.pushNotificationKeyName)
                }

                registerSFDCPush(context, account)
            }
        }
    }

    /**
     * Performs SFDC un-registration from push notifications for the specified user account.
     * Performs GCM un-registration only if this is the last account being logged out.
     *
     * @param context Context.
     * @param account User account.
     * @param isLastAccount True - if this is the last logged in account, False - otherwise.
     */
    @JvmStatic
    fun unregister(context: Context, account: UserAccount?, isLastAccount: Boolean) {
        if (isRegistered(context, account)) {
            setInProgress(context, account)

            // Deletes InstanceID only if there are no other logged in accounts.
            if (isLastAccount) {
                val firebaseApp = getFirebaseApp(context)
                val firebaseMessaging = SalesforceSDKManager.getInstance()
                    .pushNotificationReceiver?.supplyFirebaseMessaging() ?: FirebaseMessaging.getInstance()
                firebaseMessaging.isAutoInitEnabled = false
                FirebaseInstallations.getInstance(firebaseApp).delete()
                firebaseApp.delete()
            }

            unregisterSFDCPush(context, account)
        }
    }

    /**
     * Get the app unique name for firebase
     *
     * @param context Context
     * @return appName String
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun getAppNameForFirebase(context: Context): String {
        var appName = "[DEFAULT]"
        try {
            /**
             * TODO:  Remove check when min version of SDK >=  API 33 or the below issue is resolved.
             *
             * The replacement for the deprecated getPackageInfo currently requires API 33:
             * https://issuetracker.google.com/issues/246845196
             */
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            appName = packageInfo.applicationInfo?.labelRes?.let { context.getString(it) }.toString()
        } catch (e: Exception) {
            SalesforceSDKLogger.w(TAG, "Package info could not be retrieved.", e)
        }

        return appName
    }

    /**
     * Initiates push registration against the SFDC endpoint.
     *
     * @param context Context.
     * @param account User account.
     */
    @JvmStatic
    fun registerSFDCPush(context: Context, account: UserAccount?) {
        runPushService(context, account, Register)
    }


    /**
     * Returns the current registration ID, or null, if registration
     * hasn't taken place yet.
     *
     * @param context Context.
     * @param account User account.
     * @return Registration ID.
     */
    @JvmStatic
    fun getRegistrationId(context: Context, account: UserAccount?): String? {
        val prefs = context.getSharedPreferences(
            getSharedPrefFile(account),
            MODE_PRIVATE
        )
        return prefs.getString(REGISTRATION_ID, null)
    }

    /**
     * Clears Salesforce notifications types for the provided user account.
     * @param userAccount the user account
     */
    internal fun clearNotificationsTypes(
        userAccount: UserAccount
    ) {
        val context = SalesforceSDKManager.getInstance().appContext
        val sharedPreferences = context.getSharedPreferences(
            getSharedPrefFile(userAccount),
            MODE_PRIVATE
        )
        sharedPreferences.edit { remove(NOTIFICATIONS_TYPES) }
    }

    /**
     * Returns Salesforce notifications types for the provided user account.
     * @param userAccount the user account
     * @return The Salesforce notifications types or null if there are none
     */
    @JvmStatic
    fun getNotificationsTypes(
        userAccount: UserAccount
    ): NotificationsTypesResponseBody? {
        val context = SalesforceSDKManager.getInstance().appContext
        val sharedPreferences = context.getSharedPreferences(
            getSharedPrefFile(userAccount),
            MODE_PRIVATE
        )

        return sharedPreferences.getString(NOTIFICATIONS_TYPES, null)?.let { json ->
            NotificationsTypesResponseBody.fromJson(json)
        }
    }

    /**
     * Stores the Salesforce notifications types for the provided user account.
     * @param userAccount The user account
     */
    @JvmStatic
    internal fun setNotificationTypes(
        userAccount: UserAccount,
        notificationsTypes: NotificationsTypesResponseBody
    ) {
        val context = SalesforceSDKManager.getInstance().appContext
        val sharedPreferences = context.getSharedPreferences(
            getSharedPrefFile(userAccount),
            MODE_PRIVATE
        )
        sharedPreferences.edit {
            putString(
                NOTIFICATIONS_TYPES,
                Json.encodeToString(
                    NotificationsTypesResponseBody.serializer(),
                    notificationsTypes
                )
            )
        }
    }

    /**
     * Stores the GCM registration ID, and resets back off time.
     *
     * @param context Context.
     * @param registrationId Registration ID received from the GCM service.
     * @param account User account.
     */
    @JvmStatic
    fun setRegistrationId(
        context: Context, registrationId: String?,
        account: UserAccount?,
    ) {
        val prefs = context.getSharedPreferences(
            getSharedPrefFile(account),
            MODE_PRIVATE
        )
        prefs.edit {
            putString(REGISTRATION_ID, registrationId)
        }
    }

    /**
     * Returns whether the specified user account is currently registered or not.
     *
     * @param context Context.
     * @param account User account.
     * @return True - if the user account is registered, False - otherwise.
     */
    @JvmStatic
    fun isRegistered(context: Context, account: UserAccount?): Boolean {
        val prefs = context.getSharedPreferences(
            getSharedPrefFile(account),
            MODE_PRIVATE
        )
        return prefs.getString(REGISTRATION_ID, null) != null
    }

    /**
     * Clears the stored SFDC device ID.
     *
     * @param context Context.
     * @param account User account.
     */
    @Suppress("unused")
    fun clearSFDCRegistrationInfo(context: Context, account: UserAccount?) {
        val prefs = context.getSharedPreferences(
            getSharedPrefFile(account),
            MODE_PRIVATE
        )
        prefs.edit {
            remove(DEVICE_ID)
        }
    }

    /**
     * Returns whether the user account is registered with SFDC or not.
     *
     * @param context Context.
     * @param account User account.
     * @return True - if registered, False - otherwise.
     */
    @Suppress("unused")
    fun isRegisteredWithSFDC(context: Context, account: UserAccount?): Boolean {
        val prefs = context.getSharedPreferences(
            getSharedPrefFile(account),
            MODE_PRIVATE
        )
        return prefs.getString(DEVICE_ID, null) != null
    }

    /**
     * Returns the SFDC registration ID for the specified user account.
     *
     * @param context Context.
     * @param account User account.
     * @return SFDC registration ID.
     */
    @JvmStatic
    fun getDeviceId(context: Context, account: UserAccount?): String? {
        val prefs = context.getSharedPreferences(
            getSharedPrefFile(account),
            MODE_PRIVATE
        )
        return prefs.getString(DEVICE_ID, null)
    }

    /**
     * Sets the last time SFDC registration was successful for the
     * specified user account.
     *
     * @param context Context.
     * @param lastRegistrationTime Last registration time.
     * @param account User account.
     */
    @Suppress("unused")
    fun setLastRegistrationTime(
        context: Context, lastRegistrationTime: Long,
        account: UserAccount?,
    ) {
        val prefs = context.getSharedPreferences(
            getSharedPrefFile(account),
            MODE_PRIVATE
        )
        prefs.edit {
            putLong(LAST_SFDC_REGISTRATION_TIME, lastRegistrationTime)
        }
    }

    /**
     * Returns whether push notification registration/un-registration is in progress.
     *
     * @param context Context.
     * @param account User account.
     * @return True - if in progress, False - otherwise.
     */
    @Suppress("unused")
    fun isInProgress(context: Context, account: UserAccount?): Boolean {
        val prefs = context.getSharedPreferences(
            getSharedPrefFile(account),
            MODE_PRIVATE
        )
        return prefs.getBoolean(IN_PROGRESS, false)
    }

    /**
     * Clears the stored registration information.
     *
     * @param context Context.
     * @param account User account.
     */
    @JvmStatic
    fun clearRegistrationInfo(context: Context, account: UserAccount?) {
        val prefs = context.getSharedPreferences(
            getSharedPrefFile(account),
            MODE_PRIVATE
        )
        prefs.edit {
            clear()
        }
    }

    /**
     * Stores the current registration information, and resets the backoff time.
     *
     * @param context Context.
     * @param registrationId GCM registration ID.
     * @param deviceId SFDC device ID.
     * @param account User account.
     */
    @JvmStatic
    fun setRegistrationInfo(
        context: Context, registrationId: String?,
        deviceId: String?, account: UserAccount?,
    ) {
        val prefs = context.getSharedPreferences(
            getSharedPrefFile(account),
            MODE_PRIVATE
        )
        prefs.edit {
            putString(REGISTRATION_ID, registrationId)
            putString(DEVICE_ID, deviceId)
            putLong(LAST_SFDC_REGISTRATION_TIME, System.currentTimeMillis())
            putBoolean(IN_PROGRESS, false)
        }
    }

    /**
     * Checks if the app has implemented a push receiver and added the google-services.json file to the project.
     *
     * @param context The context to use.
     * @return if the app is setup for push messages to be registered.
     */
    private fun isPushSetup(context: Context): Boolean {
        // If an instance of PushNotificationInterface is not provided the app does not intent to use the feature.
        SalesforceSDKManager.getInstance().pushNotificationReceiver ?: return false

        val firebaseOptions = FirebaseOptions.fromResource(context)
        @Suppress("UselessCallOnNotNull") // FirebaseOptions instance could be from an older version.
        if ((firebaseOptions == null) || firebaseOptions.apiKey.isNullOrBlank()) {
            SalesforceSDKLogger.w(
                TAG, "Unable to retrieve Firebase values.  This usually " +
                        "means that com.google.gms:google-services was not applied to your gradle project."
            )
            return false
        }

        return true
    }

    /**
     * Will make call to Firebase.initializeApp if it hasn't already taken place.
     *
     * @param context Context
     * @return True - if Firebase is now initialized, False - if the operation could not be completed.
     */
    private fun getFirebaseApp(context: Context): FirebaseApp {
        val appName = getAppNameForFirebase(context)
        val firebaseOptions = FirebaseOptions.fromResource(context)!! // checked in isPushSetup

        /*
         * Ensures that Firebase initialization occurs only once for this app. If an exception
         * isn't thrown, this means that the initialization has already been completed.
         */
        return try {
            FirebaseApp.getInstance(appName)
        } catch (e: IllegalStateException) {
            SalesforceSDKLogger.i(TAG, "Firebase hasn't been initialized yet", e)
            FirebaseApp.initializeApp(context, firebaseOptions, appName)
        }
    }

    /**
     * Initiates push un-registration against the SFDC endpoint.
     *
     * @param context Context.
     * @param account User account.
     */
    private fun unregisterSFDCPush(context: Context, account: UserAccount?) {
        runPushService(context, account, Deregister)
    }

    /**
     * Sets a boolean that reflects the status of push notification
     * registration or un-registration (in progress or not).
     *
     * @param context Context.
     * @param account User account.
     */
    private fun setInProgress(
        context: Context,
        account: UserAccount?,
    ) {
        val prefs = context.getSharedPreferences(
            getSharedPrefFile(account),
            MODE_PRIVATE
        )
        prefs.edit {
            putBoolean(IN_PROGRESS, true)
        }
    }

    private fun runPushService(
        context: Context,
        account: UserAccount?,
        action: PushNotificationsRegistrationAction,
    ) {
        if (account == null || isRegistered(context, account)) {
            enqueuePushNotificationsRegistrationWork(
                userAccount = account,
                action = action,
                pushNotificationsRegistrationType = ReRegistrationOnAppForeground,
                delayDays = null
            )
        }
    }

    private fun getSharedPrefFile(account: UserAccount?): String {
        return GCM_PREFS + account?.userLevelFilenameSuffix
    }
}
