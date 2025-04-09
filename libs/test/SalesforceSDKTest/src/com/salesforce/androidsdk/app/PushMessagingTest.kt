package com.salesforce.androidsdk.app

import android.accounts.AccountManager
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.accounts.UserAccountManagerTest
import com.salesforce.androidsdk.accounts.UserAccountTest
import com.salesforce.androidsdk.push.PushMessaging
import com.salesforce.androidsdk.push.PushService
import com.salesforce.androidsdk.push.PushService.Companion.REGISTRATION_STATUS_SUCCEEDED
import com.salesforce.androidsdk.push.PushService.Companion.UNREGISTRATION_STATUS_SUCCEEDED
import com.salesforce.androidsdk.rest.NotificationsTypesResponseBody
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for `PushMessaging`.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class PushMessagingTest {

    /** An account manager for test.  TODO: Can this be common with `UserAccountManagerTest`? ECJ20250407 */
    private var accMgr: AccountManager? = null

    /** A user account manager for test.  TODO: Can this be common with `UserAccountManagerTest`? ECJ20250407 */
    private var userAccMgr: UserAccountManager? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {

        // TODO: Can this be common with `UserAccountManagerTest`? ECJ20250407
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        accMgr = AccountManager.get(targetContext)
        userAccMgr = UserAccountManager.getInstance()
        Assert.assertNull("There should be no authenticated users", userAccMgr?.getAuthenticatedUsers())
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {

        // TODO: Can this be common with `UserAccountManagerTest`? ECJ20250407
        cleanupAccounts()
        userAccMgr = null
        accMgr = null
    }

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
    fun testOnPushNotificationRegistrationStatusInternal() {
        var result = false

        object : PushService() {
            override fun onPushNotificationRegistrationStatus(
                status: Int,
                userAccount: UserAccount?
            ) {
                result = true
            }
        }.onPushNotificationRegistrationStatusInternal(
            REGISTRATION_STATUS_SUCCEEDED,
            null
        )

        Assert.assertTrue(result)
    }

    @Test
    fun testGetNotificationsTypesViaSdkManager() {
        var notificationsType = SalesforceSDKManager.getInstance().getNotificationsType(
            "actionable_notif_test_type"
        )

        Assert.assertNull(notificationsType)

        createTestAccountInAccountManager()
        PushMessaging.setNotificationTypes(
            userAccount = SalesforceSDKManager.getInstance().userAccountManager.currentUser,
            notificationsTypes = NotificationsTypesResponseBody.fromJson(NOTIFICATIONS_TYPES_JSON)
        )

        notificationsType = SalesforceSDKManager.getInstance().getNotificationsType(
            "actionable_notif_test_type"
        )

        Assert.assertEquals("actionable_notif_test_type", notificationsType?.apiName)
    }

    @Test
    fun testInvokeServerNotificationActionViaSdkManager() {
        val salesforceSDKManager = SalesforceSDKManager.getInstance()

        var notificationsActionsResponseBody = salesforceSDKManager.invokeServerNotificationAction(
            notificationId = "test_notification_id",
            actionKey = "test_action_key"
        )

        Assert.assertNull(notificationsActionsResponseBody)

        createTestAccountInAccountManager()
        PushMessaging.setNotificationTypes(
            userAccount = SalesforceSDKManager.getInstance().userAccountManager.currentUser,
            notificationsTypes = NotificationsTypesResponseBody.fromJson(NOTIFICATIONS_TYPES_JSON)
        )

        notificationsActionsResponseBody = salesforceSDKManager.invokeServerNotificationAction(
            notificationId = "test_notification_id",
            actionKey = "test_action_key"
        )

        Assert.assertNull(notificationsActionsResponseBody)
    }

    @Test
    fun testRefreshNotificationsTypes() {
        PushService().refreshNotificationsTypes(REGISTRATION_STATUS_SUCCEEDED, null)
        PushService().refreshNotificationsTypes(REGISTRATION_STATUS_SUCCEEDED, UserAccountTest.createTestAccount())
        PushService().refreshNotificationsTypes(UNREGISTRATION_STATUS_SUCCEEDED, null)
        PushService().refreshNotificationsTypes(UNREGISTRATION_STATUS_SUCCEEDED, UserAccountTest.createTestAccount())
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

    /**
     * Removes any existing accounts.
     */
    @Throws(java.lang.Exception::class)
    private fun cleanupAccounts() {
        for (acc in accMgr!!.getAccountsByType(UserAccountManagerTest.TEST_ACCOUNT_TYPE)) {
            accMgr!!.removeAccountExplicitly(acc)
        }
    }

    /**
     * Create a test account.
     * TODO: Can this be common with `UserAccountManagerTest`? ECJ20250407
     *
     * @return UserAccount.
     */
    private fun createTestAccountInAccountManager(): UserAccount {
        val userAccount = UserAccountTest.createTestAccount()
        userAccMgr?.createAccount(userAccount)
        return userAccount
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
