package com.salesforce.androidsdk.developer.support.notifications.local

import android.Manifest.permission.POST_NOTIFICATIONS
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context.MODE_PRIVATE
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_DEFAULT
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.salesforce.androidsdk.R.drawable.sf__salesforce_logo
import com.salesforce.androidsdk.R.string.sf__notifications_local_show_dev_support_content
import com.salesforce.androidsdk.R.string.sf__notifications_local_show_dev_support_text
import com.salesforce.androidsdk.R.string.sf__notifications_local_show_dev_support_title
import com.salesforce.androidsdk.developer.support.notifications.local.ShowDeveloperSupportNotifier.Companion.NotificationId.SHOW_DEVELOPER_SUPPORT

/**
 * Provides the "Show Developer Support" local notification, including
 * notification permissions and set up.
 */
internal class ShowDeveloperSupportNotifier {

    companion object {

        // region Intents

        /** A broadcast intent action to show the developer support dialog */
        const val BROADCAST_INTENT_ACTION_SHOW_DEVELOPER_SUPPORT = "SHOW_DEVELOPER_SUPPORT"

        // endregion
        // region Notifications

        /** A shared preferences name for developer support preferences */
        private const val SFDC_SHARED_PREFERENCES_NAME_DEVELOPER_SUPPORT = "SFDC_SHARED_PREFERENCES_NAME_DEVELOPER_SUPPORT"

        /** A shared preferences key for developer support post notifications permission requested */
        private const val SFDC_SHARED_PREFERENCES_KEY_DEVELOPER_SUPPORT_POST_NOTIFICATIONS_PERMISSION_REQUESTED = "SFDC_SHARED_PREFERENCES_KEY_DEVELOPER_SUPPORT_POST_NOTIFICATIONS_PERMISSION_REQUESTED"

        /** Notification Channel Ids: Salesforce Mobile SDK Show Developer Support Notification */
        private const val NOTIFICATION_CHANNEL_ID_SHOW_DEVELOPER_SUPPORT = "NotificationChannelShowDeveloperSupport"

        /** Notification Ids */
        enum class NotificationId(val id: Int) {
            SHOW_DEVELOPER_SUPPORT(0)
        }

        /**
         * Creates notification channels.
         *
         * @param application The Android application
         */
        @RequiresApi(O)
        fun createNotificationChannel(application: Application) =
            (application.getSystemService(
                NOTIFICATION_SERVICE
            ) as? NotificationManager)?.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID_SHOW_DEVELOPER_SUPPORT,
                    "Show Developer Support Notification",
                    IMPORTANCE_DEFAULT
                )
            )

        /**
         * Hides the Salesforce Mobile SDK "Show Developer Support"
         * notification.
         * @param activity The Android activity
         */
        fun hideDeveloperSupportNotification(activity: Activity?) {
            // Guards.
            if (activity == null) {
                return
            }

            // Conveniences.
            val notificationManager = NotificationManagerCompat.from(activity)

            // Guard such that no notification will be attempted when notifications are disabled.
            if (!notificationManager.areNotificationsEnabled()) return

            // Build and display the Show Developer Support notification.
            notificationManager.cancel(SHOW_DEVELOPER_SUPPORT.id)
        }

        /**
         * Displays the Salesforce Mobile SDK "Show Developer Support"
         * notification.
         *
         * Note, suppressing `LaunchActivityFromNotification` is intentional.
         * This notification doesn't launch a dedicated activity as the
         * "content" of the notification.  Rather, it has the app's visible
         * activity show the developer support dialog.  In that way, this
         * notification is subtly different than the use case described by
         * Material Design.
         * @param activity The Android activity
         */
        @SuppressLint("LaunchActivityFromNotification")
        fun showDeveloperSupportNotification(activity: Activity?) {
            // Guards.
            if (activity == null) {
                return
            }

            // Conveniences.
            val notificationManager = NotificationManagerCompat.from(activity)

            /*
             * Review required Android post-notifications permission.
             * Note: The permission check and return must be in the same method
             * as the notification manager's "notify" method to pass Android
             * Studio's inspector.
             */
            if (SDK_INT >= TIRAMISU && ActivityCompat.checkSelfPermission(
                    activity,
                    POST_NOTIFICATIONS
                ) != PERMISSION_GRANTED
            ) {
                // Guard such that the permissions prompt is only displayed once as a consideration for the user's chosen preferences.
                activity.getSharedPreferences(
                    SFDC_SHARED_PREFERENCES_NAME_DEVELOPER_SUPPORT,
                    MODE_PRIVATE
                ).run {
                    val postNotificationsPermissionRequested = getBoolean(
                        SFDC_SHARED_PREFERENCES_KEY_DEVELOPER_SUPPORT_POST_NOTIFICATIONS_PERMISSION_REQUESTED,
                        false
                    )
                    if (postNotificationsPermissionRequested) return

                    edit {
                        putBoolean(
                            SFDC_SHARED_PREFERENCES_KEY_DEVELOPER_SUPPORT_POST_NOTIFICATIONS_PERMISSION_REQUESTED,
                            true
                        )
                        apply()
                    }
                }

                // Prompt for the post notifications permission.
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(POST_NOTIFICATIONS),
                    1
                )

                return
            }

            // Guard such that no notification will be attempted when notifications are disabled.
            if (!notificationManager.areNotificationsEnabled()) return

            // Create the notification channel.
            if (SDK_INT >= O) {
                createNotificationChannel(activity.application)
            }

            // Initialize a notification builder for the Show Developer Support local notification.
            NotificationCompat.Builder(
                activity,
                NOTIFICATION_CHANNEL_ID_SHOW_DEVELOPER_SUPPORT
            ).apply {
                priority = PRIORITY_DEFAULT
                setContentIntent(
                    PendingIntent.getBroadcast(
                        activity,
                        0,
                        Intent(BROADCAST_INTENT_ACTION_SHOW_DEVELOPER_SUPPORT),
                        FLAG_IMMUTABLE
                    )
                )
                setContentTitle(
                    activity.getString(sf__notifications_local_show_dev_support_title)
                )
                setContentText(
                    activity.getString(sf__notifications_local_show_dev_support_content)
                )
                setSilent(true) // Set the Show Developer Support notification to silent.
                setSmallIcon(sf__salesforce_logo)
                setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        activity.getString(
                            sf__notifications_local_show_dev_support_text
                        )
                    )
                )
            }.also { builder ->

                // Build and display the Show Developer Support notification.
                notificationManager.notify(
                    SHOW_DEVELOPER_SUPPORT.id,
                    builder.build()
                )
            }
        }

        // endregion
    }
}
