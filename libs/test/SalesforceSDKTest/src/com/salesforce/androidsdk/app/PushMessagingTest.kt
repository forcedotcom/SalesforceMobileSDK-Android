package com.salesforce.androidsdk.app

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.push.PushMessaging
import com.salesforce.androidsdk.rest.NotificationsTypesResponseBody
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for `PushMessaging`.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class PushMessagingTest {

    @Test
    fun testClearNotificationsTypes() {

        PushMessaging.setNotificationTypes(
            userAccount = user,
            notificationsTypes = NotificationsTypesResponseBody.fromJson(NOTIFICATIONS_TYPES_JSON)
        )

        Assert.assertEquals(
            NOTIFICATIONS_TYPES_JSON,
            PushMessaging.getNotificationsTypes(user)?.sourceJson
        )

        PushMessaging.clearNotificationsTypes(user)

        Assert.assertNull(PushMessaging.getNotificationsTypes(user))
    }

    @Test
    fun testSetNotificationsTypes() {

        PushMessaging.setNotificationTypes(
            userAccount = user,
            notificationsTypes = NotificationsTypesResponseBody.fromJson(NOTIFICATIONS_TYPES_JSON)
        )

        Assert.assertEquals(
            NOTIFICATIONS_TYPES_JSON,
            PushMessaging.getNotificationsTypes(user)?.sourceJson
        )
    }

    companion object {

        private const val NOTIFICATIONS_TYPES_JSON =
            "{\"notificationTypes\":[{\"actionGroups\":[{\"actions\":[{\"actionKey\":\"new_acc_and_opp__new_account\",\"label\":\"New Account\",\"name\":\"new_account\",\"type\":\"NotificationApiAction\"},{\"actionKey\":\"new_acc_and_opp__new_opportunity\",\"label\":\"New Opportunity\",\"name\":\"new_opportunity\",\"type\":\"NotificationApiAction\"}],\"name\":\"new_acc_and_opp\"},{\"actions\":[{\"actionKey\":\"updateCase__escalate\",\"label\":\"Escalate\",\"name\":\"escalate\",\"type\":\"NotificationApiAction\"},{\"actionKey\":\"updateCase__raise_priority\",\"label\":\"Raise Priority\",\"name\":\"raise_priority\",\"type\":\"NotificationApiAction\"}],\"name\":\"updateCase\"}],\"apiName\":\"actionable_notif_test_type\",\"label\":\"Actionable Notification Test Type\",\"type\":\"actionable_notif_test_type\"},{\"apiName\":\"approval_request\",\"label\":\"Approval requests\",\"type\":\"approval_request\"},{\"apiName\":\"chatter_comment_on_post\",\"label\":\"New comments on a post\",\"type\":\"chatter_comment_on_post\"},{\"apiName\":\"chatter_group_mention\",\"label\":\"Group mentions on a post\",\"type\":\"chatter_group_mention\"},{\"apiName\":\"chatter_mention\",\"label\":\"Individual mentions on a post\",\"type\":\"chatter_mention\"},{\"apiName\":\"group_announce\",\"label\":\"Group manager announcements\",\"type\":\"group_announce\"},{\"apiName\":\"group_post\",\"label\":\"Posts to a group\",\"type\":\"group_post\"},{\"apiName\":\"personal_analytic\",\"label\":\"Salesforce Classic report updates\",\"type\":\"personal_analytic\"},{\"apiName\":\"profile_post\",\"label\":\"Posts to a profile\",\"type\":\"profile_post\"},{\"apiName\":\"stream_post\",\"label\":\"Posts to a stream\",\"type\":\"stream_post\"},{\"apiName\":\"task_delegated_to\",\"label\":\"Task assignments\",\"type\":\"task_delegated_to\"}]}"

        private val user = UserAccount(Bundle().apply {
            putString("orgId", "org-1")
            putString("userId", "user-1-1")
        })
    }

}
