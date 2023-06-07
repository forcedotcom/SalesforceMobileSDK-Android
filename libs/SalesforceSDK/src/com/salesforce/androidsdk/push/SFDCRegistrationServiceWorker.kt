package com.salesforce.androidsdk.push

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.FirebaseApp
import com.google.firebase.iid.FirebaseInstanceId
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.push.PushMessaging.getAppNameForFirebase
import com.salesforce.androidsdk.push.PushMessaging.registerSFDCPush
import com.salesforce.androidsdk.push.PushMessaging.setRegistrationId
import com.salesforce.androidsdk.util.SalesforceSDKLogger


internal class SFDCRegistrationServiceWorker(context: Context, workerParams: WorkerParameters)
    : Worker(context, workerParams) {
    override fun doWork(): Result {
        try {

            /*
             * Initializes the push configuration for this application and kicks off
             * Firebase initialization flow. This is required before attempting to
             * register for FCM. The other alternative is to supply a 'google-services.json'
             * file and use the Google Services plugin to initialize, but this approach
             * only works for apps. Since we're a library project, the programmatic
             * approach works better for us.
             */
            val context = SalesforceSDKManager.getInstance().appContext
            val appName = getAppNameForFirebase(context)

            // Fetches an instance ID from Firebase once the initialization is complete.
            val instanceID = FirebaseInstanceId.getInstance(FirebaseApp.getInstance(appName))
            val token = instanceID.getToken(BootConfig.getBootConfig(context).pushNotificationClientId, FCM)
            val account = SalesforceSDKManager.getInstance().userAccountManager.currentUser

            // Store the new token.
            setRegistrationId(context, token, account)

            // Send it to SFDC.
            registerSFDCPush(context, account)
            return Result.success()
        } catch (e: Exception) {
            SalesforceSDKLogger.e(TAG, "Error during FCM registration", e)
            return Result.failure()
        }
    }

    companion object {
        val TAG: String = this::class.java.name
        const val FCM = "FCM"
    }
}