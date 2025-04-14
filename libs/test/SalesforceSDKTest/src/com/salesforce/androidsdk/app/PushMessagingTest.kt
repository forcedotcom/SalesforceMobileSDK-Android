package com.salesforce.androidsdk.app

import android.accounts.AccountManager
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.accounts.UserAccountManagerTest
import com.salesforce.androidsdk.accounts.UserAccountTest.createTestAccount
import com.salesforce.androidsdk.push.PushMessaging
import com.salesforce.androidsdk.push.PushNotificationsRegistrationChangeWorker.PushNotificationsRegistrationAction.Deregister
import com.salesforce.androidsdk.push.PushNotificationsRegistrationChangeWorker.PushNotificationsRegistrationAction.Register
import com.salesforce.androidsdk.push.PushService
import com.salesforce.androidsdk.push.PushService.Companion.REGISTRATION_STATUS_SUCCEEDED
import com.salesforce.androidsdk.push.PushService.Companion.UNREGISTRATION_STATUS_SUCCEEDED
import com.salesforce.androidsdk.push.PushService.PushNotificationReRegistrationType.ReRegistrationDisabled
import com.salesforce.androidsdk.rest.ApiVersionStrings
import com.salesforce.androidsdk.rest.NotificationsActionsResponseBody
import com.salesforce.androidsdk.rest.NotificationsApiErrorResponseBody
import com.salesforce.androidsdk.rest.NotificationsApiException
import com.salesforce.androidsdk.rest.NotificationsTypesResponseBody
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestResponse
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default.encodeToString
import kotlinx.serialization.json.JsonArray
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
    fun testEnqueuePushNotificationsRegistrationWork() {

        PushService.enqueuePushNotificationsRegistrationWork(
            createTestAccount(),
            Register,
            ReRegistrationDisabled,
            0
        )

        PushService.enqueuePushNotificationsRegistrationWork(
            createTestAccount(),
            Deregister,
            ReRegistrationDisabled,
            0
        )
    }

    @Test
    fun testFetchNotificationsTypes() {

        val restResponse = mockk<RestResponse>()
        every { restResponse.asString() } returns NOTIFICATIONS_TYPES_JSON
        every { restResponse.isSuccess } returns true
        val restClient = mockk<RestClient>()
        every { restClient.sendSync(any()) } returns restResponse

        PushService().fetchNotificationsTypes(
            apiHostName = "",
            restClient = restClient,
            userAccount = createTestAccount()
        )


        ApiVersionStrings.VERSION_NUMBER_TEST = "v64.0"


        PushService().fetchNotificationsTypes(
            apiHostName = "",
            restClient = restClient,
            userAccount = createTestAccount()
        )


        val restResponseNullResponseBodyString = mockk<RestResponse>()
        every { restResponseNullResponseBodyString.asString() } returns null
        every { restResponseNullResponseBodyString.isSuccess } returns true
        val restClientNullResponseBodyString = mockk<RestClient>()
        every { restClientNullResponseBodyString.sendSync(any()) } returns restResponseNullResponseBodyString

        Assert.assertThrows(NotificationsApiException::class.java) {
            PushService().fetchNotificationsTypes(
                apiHostName = "",
                restClient = restClientNullResponseBodyString,
                userAccount = createTestAccount()
            )
        }


        val restResponseFailure = mockk<RestResponse>()
        every { restResponseFailure.asString() } returns encodeToString(
            JsonArray.serializer(),
            JsonArray(
                listOf(
                    Json.encodeToJsonElement(
                        NotificationsApiErrorResponseBody.serializer(),
                        NotificationsApiErrorResponseBody(
                            errorCode = "test_error_code",
                            message = "test_message",
                            messageCode = "test_message_code"
                        )
                    )
                )
            )
        )
        every { restResponseFailure.isSuccess } returns false

        val restClientFailure = mockk<RestClient>()
        every { restClientFailure.sendSync(any()) } returns restResponseFailure

        Assert.assertThrows(NotificationsApiException::class.java) {
            PushService().fetchNotificationsTypes(
                apiHostName = "",
                restClient = restClientFailure,
                userAccount = createTestAccount()
            )
        }


        val restResponseFailureEmpty = mockk<RestResponse>()
        every { restResponseFailureEmpty.asString() } returns encodeToString(
            JsonArray.serializer(),
            JsonArray(listOf())
        )
        every { restResponseFailureEmpty.isSuccess } returns false

        val restClientFailureEmpty = mockk<RestClient>()
        every { restClientFailureEmpty.sendSync(any()) } returns restResponseFailureEmpty

        Assert.assertThrows(NotificationsApiException::class.java) {
            PushService().fetchNotificationsTypes(
                apiHostName = "",
                restClient = restClientFailureEmpty,
                userAccount = createTestAccount()
            )
        }


        val restResponseFailureNullProperties = mockk<RestResponse>()
        every { restResponseFailureNullProperties.asString() } returns encodeToString(
            JsonArray.serializer(),
            JsonArray(
                listOf(
                    Json.encodeToJsonElement(
                        NotificationsApiErrorResponseBody.serializer(),
                        NotificationsApiErrorResponseBody()
                    )
                )
            )
        )
        every { restResponseFailureNullProperties.isSuccess } returns false

        val restClientFailureNullProperties = mockk<RestClient>()
        every { restClientFailureNullProperties.sendSync(any()) } returns restResponseFailureNullProperties

        Assert.assertThrows(NotificationsApiException::class.java) {
            PushService().fetchNotificationsTypes(
                apiHostName = "",
                restClient = restClientFailureNullProperties,
                userAccount = createTestAccount()
            )
        }


        ApiVersionStrings.VERSION_NUMBER_TEST = null
    }

    @Test
    fun testOnPushNotificationRegistrationStatus() {
        PushService().onPushNotificationRegistrationStatus(
            REGISTRATION_STATUS_SUCCEEDED,
            createTestAccount()
        )

        PushService().onPushNotificationRegistrationStatus(
            UNREGISTRATION_STATUS_SUCCEEDED,
            createTestAccount()
        )
    }

    @Test
    fun testOnPushNotificationRegistrationStatusInternal() {
        val restResponse = mockk<RestResponse>()
        every { restResponse.asString() } returns NOTIFICATIONS_TYPES_JSON
        every { restResponse.isSuccess } returns true
        val restClient = mockk<RestClient>()
        every { restClient.sendSync(any()) } returns restResponse

        var result = false

        object : PushService() {
            override fun onPushNotificationRegistrationStatus(
                status: Int,
                userAccount: UserAccount?
            ) {
                result = true
            }
        }.onPushNotificationRegistrationStatusInternal(
            status = REGISTRATION_STATUS_SUCCEEDED,
            apiHostName = "",
            restClient = restClient,
            userAccount = createTestAccount()
        )

        Assert.assertTrue(result)
    }

    @Test
    fun testApiVersionStrings() {
        val result = ApiVersionStrings.getVersionNumber(null)
        Assert.assertEquals(result, "v63.0")
    }

    @Test
    fun testGetNotificationsTypesViaSdkManager() {

        var notificationsType = SalesforceSDKManager.getInstance().getNotificationsType(
            "actionable_notif_test_type"
        )

        Assert.assertNull(notificationsType)


        createTestAccountInAccountManager()
        PushMessaging.clearNotificationsTypes(SalesforceSDKManager.getInstance().userAccountManager.currentUser)


        notificationsType = SalesforceSDKManager.getInstance().getNotificationsType(
            "actionable_notif_test_type"
        )

        Assert.assertNull(notificationsType?.apiName)


        PushMessaging.setNotificationTypes(
            userAccount = SalesforceSDKManager.getInstance().userAccountManager.currentUser,
            notificationsTypes = NotificationsTypesResponseBody.fromJson(NOTIFICATIONS_TYPES_JSON).copy(notificationTypes = null)
        )

        notificationsType = SalesforceSDKManager.getInstance().getNotificationsType(
            "actionable_notif_test_type"
        )

        Assert.assertNull(notificationsType?.apiName)


        PushMessaging.setNotificationTypes(
            userAccount = SalesforceSDKManager.getInstance().userAccountManager.currentUser,
            notificationsTypes = NotificationsTypesResponseBody.fromJson(NOTIFICATIONS_TYPES_JSON).copy(notificationTypes = arrayOf())
        )

        notificationsType = SalesforceSDKManager.getInstance().getNotificationsType(
            "actionable_notif_test_type"
        )

        Assert.assertNull(notificationsType?.apiName)


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

        val restResponse = mockk<RestResponse>()
        every { restResponse.asString() } returns encodeToString(
            NotificationsActionsResponseBody.serializer(),
            NotificationsActionsResponseBody(
                message = "test_message"
            )
        )
        every { restResponse.isSuccess } returns true
        val restClient = mockk<RestClient>()
        every { restClient.sendSync(any()) } returns restResponse

        var notificationsActionsResponseBody = salesforceSDKManager.invokeServerNotificationAction(
            notificationId = "test_notification_id",
            actionKey = "test_action_key",
            instanceHost = "",
            restClient = restClient
        )
        Assert.assertNull(notificationsActionsResponseBody)


        createTestAccountInAccountManager()

        PushMessaging.setNotificationTypes(
            userAccount = SalesforceSDKManager.getInstance().userAccountManager.currentUser,
            notificationsTypes = NotificationsTypesResponseBody.fromJson(NOTIFICATIONS_TYPES_JSON)
        )

        notificationsActionsResponseBody = salesforceSDKManager.invokeServerNotificationAction(
            notificationId = "test_notification_id",
            actionKey = "test_action_key",
            instanceHost = "",
            restClient = restClient
        )
        Assert.assertNull(notificationsActionsResponseBody)


        ApiVersionStrings.VERSION_NUMBER_TEST = "v64.0"


        notificationsActionsResponseBody = salesforceSDKManager.invokeServerNotificationAction(
            notificationId = "test_notification_id",
            actionKey = "test_action_key",
            instanceHost = "",
            restClient = restClient
        )
        Assert.assertEquals(notificationsActionsResponseBody?.message, "test_message")


        val restResponseNullResponseBodyString = mockk<RestResponse>()
        every { restResponseNullResponseBodyString.asString() } returns null
        every { restResponseNullResponseBodyString.isSuccess } returns true
        val restClientNullResponseBodyString = mockk<RestClient>()
        every { restClientNullResponseBodyString.sendSync(any()) } returns restResponseNullResponseBodyString

        Assert.assertThrows(NotificationsApiException::class.java) {
            salesforceSDKManager.invokeServerNotificationAction(
                notificationId = "test_notification_id",
                actionKey = "test_action_key",
                instanceHost = "",
                restClient = restClientNullResponseBodyString
            )
        }

        val restResponseFailure = mockk<RestResponse>()
        every { restResponseFailure.asString() } returns encodeToString(
            JsonArray.serializer(),
            JsonArray(
                listOf(
                    Json.encodeToJsonElement(
                        NotificationsApiErrorResponseBody.serializer(),
                        NotificationsApiErrorResponseBody(
                            errorCode = "test_error_code",
                            message = "test_message",
                            messageCode = "test_message_code"
                        )
                    )
                )
            )
        )
        every { restResponseFailure.isSuccess } returns false

        val restClientFailure = mockk<RestClient>()
        every { restClientFailure.sendSync(any()) } returns restResponseFailure

        Assert.assertThrows(NotificationsApiException::class.java) {
            salesforceSDKManager.invokeServerNotificationAction(
                notificationId = "test_notification_id",
                actionKey = "test_action_key",
                instanceHost = "",
                restClient = restClientFailure
            )
        }


        val restResponseFailureEmpty = mockk<RestResponse>()
        every { restResponseFailureEmpty.asString() } returns encodeToString(
            JsonArray.serializer(),
            JsonArray(listOf())
        )
        every { restResponseFailureEmpty.isSuccess } returns false

        val restClientFailureEmpty = mockk<RestClient>()
        every { restClientFailureEmpty.sendSync(any()) } returns restResponseFailureEmpty

        Assert.assertThrows(NotificationsApiException::class.java) {
            salesforceSDKManager.invokeServerNotificationAction(
                notificationId = "test_notification_id",
                actionKey = "test_action_key",
                instanceHost = "",
                restClient = restClientFailureEmpty
            )
        }


        val restResponseFailureNullProperties = mockk<RestResponse>()
        every { restResponseFailureNullProperties.asString() } returns encodeToString(
            JsonArray.serializer(),
            JsonArray(
                listOf(
                    Json.encodeToJsonElement(
                        NotificationsApiErrorResponseBody.serializer(),
                        NotificationsApiErrorResponseBody()
                    )
                )
            )
        )
        every { restResponseFailureNullProperties.isSuccess } returns false

        val restClientFailureNullProperties = mockk<RestClient>()
        every { restClientFailureNullProperties.sendSync(any()) } returns restResponseFailureNullProperties

        Assert.assertThrows(NotificationsApiException::class.java) {
            salesforceSDKManager.invokeServerNotificationAction(
                notificationId = "test_notification_id",
                actionKey = "test_action_key",
                instanceHost = "",
                restClient = restClientFailureNullProperties
            )
        }


        ApiVersionStrings.VERSION_NUMBER_TEST = null
    }

    @Test
    fun testRefreshNotificationsTypes() {
        val restResponse = mockk<RestResponse>()
        every { restResponse.asString() } returns encodeToString(
            NotificationsActionsResponseBody.serializer(),
            NotificationsActionsResponseBody(
                message = "test_message"
            )
        )
        every { restResponse.isSuccess } returns true
        val restClient = mockk<RestClient>()
        every { restClient.sendSync(any()) } returns restResponse

        PushService().refreshNotificationsTypes(
            status = REGISTRATION_STATUS_SUCCEEDED,
            apiHostName = "",
            restClient = restClient,
            userAccount = null
        )
        PushService().refreshNotificationsTypes(
            status = REGISTRATION_STATUS_SUCCEEDED,
            apiHostName = "",
            restClient = restClient,
            userAccount = createTestAccount()
        )
        PushService().refreshNotificationsTypes(
            status = REGISTRATION_STATUS_SUCCEEDED,
            apiHostName = "",
            restClient = restClient,
            userAccount = createTestAccount()
        )
        PushService().refreshNotificationsTypes(
            status = UNREGISTRATION_STATUS_SUCCEEDED,
            apiHostName = "",
            restClient = restClient,
            userAccount = null
        )
        PushService().refreshNotificationsTypes(
            status = UNREGISTRATION_STATUS_SUCCEEDED,
            apiHostName = "",
            restClient = restClient,
            userAccount = createTestAccount()
        )
    }

    @Test
    fun testRegisterNotificationChannels() {

        PushService().registerNotificationChannels(NotificationsTypesResponseBody.fromJson(NOTIFICATIONS_TYPES_JSON))
    }

    @Test
    fun testRemoveNotificationsCategories() {
        PushService().removeNotificationsCategories()
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
        val userAccount = createTestAccount()
        userAccMgr?.createAccount(userAccount)
        return userAccount
    }

    companion object {

        internal const val NOTIFICATIONS_TYPES_JSON =
            "{\"notificationTypes\":[{\"actionGroups\":[{\"actions\":[{\"actionKey\":\"new_acc_and_opp__new_account\",\"label\":\"New Account\",\"name\":\"new_account\",\"type\":\"NotificationApiAction\"},{\"actionKey\":\"new_acc_and_opp__new_opportunity\",\"label\":\"New Opportunity\",\"name\":\"new_opportunity\",\"type\":\"NotificationApiAction\"}],\"name\":\"new_acc_and_opp\"},{\"actions\":[{\"actionKey\":\"updateCase__escalate\",\"label\":\"Escalate\",\"name\":\"escalate\",\"type\":\"NotificationApiAction\"},{\"actionKey\":\"updateCase__raise_priority\",\"label\":\"Raise Priority\",\"name\":\"raise_priority\",\"type\":\"NotificationApiAction\"}],\"name\":\"updateCase\"}],\"apiName\":\"actionable_notif_test_type\",\"label\":\"Actionable Notification Test Type\",\"type\":\"actionable_notif_test_type\"},{\"apiName\":\"approval_request\",\"label\":\"Approval requests\",\"type\":\"approval_request\"},{\"apiName\":\"chatter_comment_on_post\",\"label\":\"New comments on a post\",\"type\":\"chatter_comment_on_post\"},{\"apiName\":\"chatter_group_mention\",\"label\":\"Group mentions on a post\",\"type\":\"chatter_group_mention\"},{\"apiName\":\"chatter_mention\",\"label\":\"Individual mentions on a post\",\"type\":\"chatter_mention\"},{\"apiName\":\"group_announce\",\"label\":\"Group manager announcements\",\"type\":\"group_announce\"},{\"apiName\":\"group_post\",\"label\":\"Posts to a group\",\"type\":\"group_post\"},{\"apiName\":\"personal_analytic\",\"label\":\"Salesforce Classic report updates\",\"type\":\"personal_analytic\"},{\"apiName\":\"profile_post\",\"label\":\"Posts to a profile\",\"type\":\"profile_post\"},{\"apiName\":\"stream_post\",\"label\":\"Posts to a stream\",\"type\":\"stream_post\"},{\"apiName\":\"task_delegated_to\",\"label\":\"Task assignments\",\"type\":\"task_delegated_to\"}]}"

        private val user = UserAccount(Bundle().apply {
            putString("orgId", "org-1")
            putString("userId", "user-1-1")
        })
    }
}
