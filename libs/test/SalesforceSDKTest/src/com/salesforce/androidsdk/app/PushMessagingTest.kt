package com.salesforce.androidsdk.app

import android.accounts.AccountManager
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.accounts.UserAccountManagerTest.cleanupAccounts
import com.salesforce.androidsdk.accounts.UserAccountManagerTest.createTestAccountInAccountManager
import com.salesforce.androidsdk.push.PushMessaging.clearNotificationsTypes
import com.salesforce.androidsdk.push.PushMessaging.getNotificationsTypes
import com.salesforce.androidsdk.push.PushMessaging.setNotificationTypes
import com.salesforce.androidsdk.rest.ApiVersionStrings
import com.salesforce.androidsdk.rest.NotificationsActionsResponseBody
import com.salesforce.androidsdk.rest.NotificationsApiErrorResponseBody
import com.salesforce.androidsdk.rest.NotificationsApiException
import com.salesforce.androidsdk.rest.NotificationsTypesResponseBody.Companion.fromJson
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestClient.ClientInfo
import com.salesforce.androidsdk.rest.RestResponse
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json.Default.encodeToJsonElement
import kotlinx.serialization.json.Json.Default.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonArray.Companion.serializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Tests for `PushMessaging`.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class PushMessagingTest {

    /** An account manager for test */
    private var accountManager: AccountManager? = null

    /** A user account manager for test */
    private var userAccountManager: UserAccountManager? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {

        accountManager = AccountManager.get(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        val userAccountManager = UserAccountManager.getInstance().apply { this@PushMessagingTest.userAccountManager = this }

        ApiVersionStrings.VERSION_NUMBER_TEST = null
        createTestAccountInAccountManager(userAccountManager)
        clearNotificationsTypes(user)
        cleanupAccounts(accountManager)

        assertNull("There should be no authenticated users.", userAccountManager?.getAuthenticatedUsers())
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {

        cleanupAccounts(accountManager)
        ApiVersionStrings.VERSION_NUMBER_TEST = null

        userAccountManager = null
        accountManager = null
    }

    @Test
    fun testClearNotificationsTypes() {

        // Verify initial state.
        assertNull(getNotificationsTypes(user))

        // Test setting and retrieving notification types.
        setNotificationTypes(
            userAccount = user,
            notificationsTypes = fromJson(NOTIFICATIONS_TYPES_JSON)
        )

        assertEquals(
            fromJson(NOTIFICATIONS_TYPES_JSON),
            getNotificationsTypes(user)
        )

        // Test clearing notifications types.
        clearNotificationsTypes(user)

        assertNull(getNotificationsTypes(user))
    }

    @Test
    fun testGetNotificationsTypesViaSdkManager() {
        val salesforceSdkManager = SalesforceSDKManager.getInstance()

        createTestAccountInAccountManager(userAccountManager)
        clearNotificationsTypes(salesforceSdkManager.userAccountManager.currentUser)

        setNotificationTypes(
            userAccount = salesforceSdkManager.userAccountManager.currentUser,
            notificationsTypes = fromJson(NOTIFICATIONS_TYPES_JSON)
        )

        val notificationsType = salesforceSdkManager.getNotificationsType(
            "actionable_notif_test_type"
        )

        assertEquals("actionable_notif_test_type", notificationsType?.apiName)
    }

    @Test
    fun testGetNotificationsTypesViaSdkManager_WithoutAccount() {
        val salesforceSdkManager = SalesforceSDKManager.getInstance()

        assertNull(
            salesforceSdkManager.getNotificationsType(
                "actionable_notif_test_type"
            )
        )
    }

    @Test
    fun testGetNotificationsTypesViaSdkManager_WithoutNotificationsTypesResponseBody() {
        val salesforceSdkManager = SalesforceSDKManager.getInstance()

        createTestAccountInAccountManager(userAccountManager)
        clearNotificationsTypes(salesforceSdkManager.userAccountManager.currentUser)

        val notificationsType = salesforceSdkManager.getNotificationsType(
            "actionable_notif_test_type"
        )

        assertNull(notificationsType)
    }

    @Test
    fun testGetNotificationsTypesViaSdkManager_WithoutNotificationsTypes() {
        val salesforceSdkManager = SalesforceSDKManager.getInstance()

        createTestAccountInAccountManager(userAccountManager)
        clearNotificationsTypes(salesforceSdkManager.userAccountManager.currentUser)

        setNotificationTypes(
            userAccount = salesforceSdkManager.userAccountManager.currentUser,
            notificationsTypes = fromJson(NOTIFICATIONS_TYPES_JSON).copy(notificationTypes = null)
        )

        val notificationsType = salesforceSdkManager.getNotificationsType(
            "actionable_notif_test_type"
        )

        assertNull(notificationsType)
    }


    @Test
    fun testGetNotificationsTypesViaSdkManager_WithEmptyNotificationsTypes() {
        val salesforceSdkManager = SalesforceSDKManager.getInstance()

        createTestAccountInAccountManager(userAccountManager)
        clearNotificationsTypes(salesforceSdkManager.userAccountManager.currentUser)

        setNotificationTypes(
            userAccount = salesforceSdkManager.userAccountManager.currentUser,
            notificationsTypes = fromJson(NOTIFICATIONS_TYPES_JSON).copy(notificationTypes = arrayOf())
        )

        val notificationsType = salesforceSdkManager.getNotificationsType(
            "actionable_notif_test_type"
        )

        assertNull(notificationsType)
    }

    @Test
    fun testInvokeServerNotificationActionViaSdkManager() {
        val salesforceSdkManager = SalesforceSDKManager.getInstance()

        // Mocks.
        val restResponse = mockk<RestResponse>()
        every { restResponse.asString() } returns encodeToString(
            NotificationsActionsResponseBody.serializer(),
            NotificationsActionsResponseBody(
                message = "test_message"
            )
        )
        every { restResponse.isSuccess } returns true
        val restClient = mockk<RestClient>()
        every { restClient.clientInfo } returns clientInfo
        every { restClient.sendSync(any()) } returns restResponse

        // Setup
        createTestAccountInAccountManager(userAccountManager)

        setNotificationTypes(
            userAccount = salesforceSdkManager.userAccountManager.currentUser,
            notificationsTypes = fromJson(NOTIFICATIONS_TYPES_JSON)
        )

        ApiVersionStrings.VERSION_NUMBER_TEST = "v64.0"

        // Test once for coverage only with the default REST client.
        assertThrows(SSLPeerUnverifiedException::class.java) {
            salesforceSdkManager.invokeServerNotificationAction(
                notificationId = "test_notification_id",
                actionKey = "test_action_key"
            )
        }

        val notificationsActionsResponseBody = salesforceSdkManager.invokeServerNotificationAction(
            notificationId = "test_notification_id",
            actionKey = "test_action_key",
            restClient = restClient
        )

        assertEquals(notificationsActionsResponseBody?.message, "test_message")
    }

    @Test
    fun testInvokeServerNotificationActionViaSdkManager_WithoutAccount() {
        val salesforceSdkManager = SalesforceSDKManager.getInstance()

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

        val notificationsActionsResponseBody = salesforceSdkManager.invokeServerNotificationAction(
            notificationId = "test_notification_id",
            actionKey = "test_action_key",
            restClient = restClient
        )

        assertNull(notificationsActionsResponseBody)
    }

    @Test
    fun testInvokeServerNotificationActionViaSdkManager_WithoutRequiredApiVersionNumber() {
        val salesforceSdkManager = SalesforceSDKManager.getInstance()

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

        createTestAccountInAccountManager(userAccountManager)

        setNotificationTypes(
            userAccount = user,
            notificationsTypes = fromJson(NOTIFICATIONS_TYPES_JSON)
        )

        val notificationsActionsResponseBody = salesforceSdkManager.invokeServerNotificationAction(
            notificationId = "test_notification_id",
            actionKey = "test_action_key",
            restClient = restClient
        )

        assertNull(notificationsActionsResponseBody)
    }

    @Test
    fun testInvokeServerNotificationActionViaSdkManager_NullResponse() {
        val salesforceSdkManager = SalesforceSDKManager.getInstance()

        // Mocks.
        val restClient = mockk<RestClient>()
        every { restClient.clientInfo } returns clientInfo
        every { restClient.sendSync(any()) } returns null

        // Setup.
        createTestAccountInAccountManager(userAccountManager)

        setNotificationTypes(
            userAccount = user,
            notificationsTypes = fromJson(NOTIFICATIONS_TYPES_JSON)
        )

        ApiVersionStrings.VERSION_NUMBER_TEST = "v64.0"

        assertThrows(NotificationsApiException::class.java) {
            salesforceSdkManager.invokeServerNotificationAction(
                notificationId = "test_notification_id",
                actionKey = "test_action_key",
                restClient = restClient
            )
        }
    }

    @Test
    fun testInvokeServerNotificationActionViaSdkManager_NullResponseBodyString() {
        val salesforceSdkManager = SalesforceSDKManager.getInstance()

        // Mocks.
        val restResponse = mockk<RestResponse>()
        every { restResponse.asString() } returns null
        every { restResponse.isSuccess } returns true
        val restClient = mockk<RestClient>()
        every { restClient.clientInfo } returns clientInfo
        every { restClient.sendSync(any()) } returns restResponse

        // Setup.
        createTestAccountInAccountManager(userAccountManager)

        setNotificationTypes(
            userAccount = user,
            notificationsTypes = fromJson(NOTIFICATIONS_TYPES_JSON)
        )

        ApiVersionStrings.VERSION_NUMBER_TEST = "v64.0"

        assertThrows(NotificationsApiException::class.java) {
            salesforceSdkManager.invokeServerNotificationAction(
                notificationId = "test_notification_id",
                actionKey = "test_action_key",
                restClient = restClient
            )
        }
    }

    @Test
    fun testInvokeServerNotificationActionViaSdkManager_Failure() {
        val salesforceSdkManager = SalesforceSDKManager.getInstance()

        // Mocks.
        val restResponse = mockk<RestResponse>()
        every { restResponse.asString() } returns encodeToString(
            serializer(),
            JsonArray(
                listOf(
                    encodeToJsonElement(
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
        every { restResponse.isSuccess } returns false

        val restClient = mockk<RestClient>()
        every { restClient.clientInfo } returns clientInfo
        every { restClient.sendSync(any()) } returns restResponse

        // Setup.
        ApiVersionStrings.VERSION_NUMBER_TEST = "v64.0"

        assertThrows(NotificationsApiException::class.java) {
            salesforceSdkManager.invokeServerNotificationAction(
                notificationId = "test_notification_id",
                actionKey = "test_action_key",
                restClient = restClient
            )
        }
    }

    @Test
    fun testInvokeServerNotificationActionViaSdkManager_FailureEmpty() {
        val salesforceSdkManager = SalesforceSDKManager.getInstance()

        // Mocks.
        val restResponse = mockk<RestResponse>()
        every { restResponse.asString() } returns encodeToString(
            serializer(),
            JsonArray(listOf())
        )
        every { restResponse.isSuccess } returns false

        val restClient = mockk<RestClient>()
        every { restClient.clientInfo } returns clientInfo
        every { restClient.sendSync(any()) } returns restResponse

        // Setup.
        ApiVersionStrings.VERSION_NUMBER_TEST = "v64.0"

        assertThrows(NotificationsApiException::class.java) {
            salesforceSdkManager.invokeServerNotificationAction(
                notificationId = "test_notification_id",
                actionKey = "test_action_key",
                restClient = restClient
            )
        }
    }

    @Test
    fun testInvokeServerNotificationActionViaSdkManager_FailureNullProperties() {
        val salesforceSdkManager = SalesforceSDKManager.getInstance()

        // Mocks.
        val restResponse = mockk<RestResponse>()
        every { restResponse.asString() } returns encodeToString(
            serializer(),
            JsonArray(
                listOf(
                    encodeToJsonElement(
                        NotificationsApiErrorResponseBody.serializer(),
                        NotificationsApiErrorResponseBody()
                    )
                )
            )
        )
        every { restResponse.isSuccess } returns false

        val restClient = mockk<RestClient>()
        every { restClient.clientInfo } returns clientInfo
        every { restClient.sendSync(any()) } returns restResponse

        // Setup.
        ApiVersionStrings.VERSION_NUMBER_TEST = "v64.0"

        assertThrows(NotificationsApiException::class.java) {
            salesforceSdkManager.invokeServerNotificationAction(
                notificationId = "test_notification_id",
                actionKey = "test_action_key",
                restClient = restClient
            )
        }
    }

    @Test
    fun testSetNotificationsTypes() {

        // Setup.
        setNotificationTypes(
            userAccount = user,
            notificationsTypes = fromJson(NOTIFICATIONS_TYPES_JSON)
        )

        assertEquals(
            fromJson(NOTIFICATIONS_TYPES_JSON),
            getNotificationsTypes(user)
        )
    }

    companion object {

        internal const val NOTIFICATIONS_TYPES_JSON =
            "{\"notificationTypes\":[{\"actionGroups\":[{\"actions\":[{\"actionKey\":\"new_acc_and_opp__new_account\",\"label\":\"New Account\",\"name\":\"new_account\",\"type\":\"NotificationApiAction\"},{\"actionKey\":\"new_acc_and_opp__new_opportunity\",\"label\":\"New Opportunity\",\"name\":\"new_opportunity\",\"type\":\"NotificationApiAction\"}],\"name\":\"new_acc_and_opp\"},{\"actions\":[{\"actionKey\":\"updateCase__escalate\",\"label\":\"Escalate\",\"name\":\"escalate\",\"type\":\"NotificationApiAction\"},{\"actionKey\":\"updateCase__raise_priority\",\"label\":\"Raise Priority\",\"name\":\"raise_priority\",\"type\":\"NotificationApiAction\"}],\"name\":\"updateCase\"}],\"apiName\":\"actionable_notif_test_type\",\"label\":\"Actionable Notification Test Type\",\"type\":\"actionable_notif_test_type\"},{\"apiName\":\"approval_request\",\"label\":\"Approval requests\",\"type\":\"approval_request\"},{\"apiName\":\"chatter_comment_on_post\",\"label\":\"New comments on a post\",\"type\":\"chatter_comment_on_post\"},{\"apiName\":\"chatter_group_mention\",\"label\":\"Group mentions on a post\",\"type\":\"chatter_group_mention\"},{\"apiName\":\"chatter_mention\",\"label\":\"Individual mentions on a post\",\"type\":\"chatter_mention\"},{\"apiName\":\"group_announce\",\"label\":\"Group manager announcements\",\"type\":\"group_announce\"},{\"apiName\":\"group_post\",\"label\":\"Posts to a group\",\"type\":\"group_post\"},{\"apiName\":\"personal_analytic\",\"label\":\"Salesforce Classic report updates\",\"type\":\"personal_analytic\"},{\"apiName\":\"profile_post\",\"label\":\"Posts to a profile\",\"type\":\"profile_post\"},{\"apiName\":\"stream_post\",\"label\":\"Posts to a stream\",\"type\":\"stream_post\"},{\"apiName\":\"task_delegated_to\",\"label\":\"Task assignments\",\"type\":\"task_delegated_to\"}]}"

        private val clientInfo = ClientInfo(
            /* instanceUrl = */ URI.create("https://192.0.2.1"), /* RFC 5737 Test URL */
            /* loginUrl = */ URI.create("https://192.0.2.1"),
            /* identityUrl = */ URI.create("https://192.0.2.1"),
            /* accountName = */ null,
            /* username = */ null,
            /* userId = */ null,
            /* orgId = */ null,
            /* communityId = */ null,
            /* communityUrl = */ null,
            /* firstName = */ null,
            /* lastName = */ null,
            /* displayName = */ null,
            /* email = */ null,
            /* photoUrl = */ null,
            /* thumbnailUrl = */ null,
            /* additionalOauthValues = */ null,
            /* lightningDomain = */ null,
            /* lightningSid = */ null,
            /* vfDomain = */ null,
            /* vfSid = */ null,
            /* contentDomain = */ null,
            /* contentSid = */ null,
            /* csrfToken = */ null
        )

        private val user = UserAccount(Bundle().apply {
            putString("orgId", "org-1")
            putString("userId", "user-1-1")
        })
    }
}
