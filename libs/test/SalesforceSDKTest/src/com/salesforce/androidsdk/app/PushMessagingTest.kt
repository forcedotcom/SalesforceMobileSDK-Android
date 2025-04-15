package com.salesforce.androidsdk.app

import android.accounts.AccountManager
import android.app.NotificationManager
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
import com.salesforce.androidsdk.push.PushService.Companion.NOTIFICATION_CHANNEL_GROUP_SALESFORCE_ID
import com.salesforce.androidsdk.push.PushService.Companion.NOT_ENABLED
import com.salesforce.androidsdk.push.PushService.Companion.REGISTRATION_STATUS_FAILED
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
import org.json.JSONObject
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection.HTTP_CREATED
import java.net.HttpURLConnection.HTTP_NOT_FOUND

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
            NotificationsTypesResponseBody.fromJson(NOTIFICATIONS_TYPES_JSON),
            PushMessaging.getNotificationsTypes(user)
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
    fun testPerformRegistrationChange() {
        createTestAccountInAccountManager()
        PushMessaging.setRegistrationId(
            context = SalesforceSDKManager.getInstance().appContext,
            registrationId = "test_registration_id",
            account = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        )

        PushService().performRegistrationChange(
            register = true,
            userAccount = SalesforceSDKManager.getInstance().userAccountManager.currentUser,
        )

        PushService().performRegistrationChange(
            register = false,
            userAccount = SalesforceSDKManager.getInstance().userAccountManager.currentUser,
        )
    }

    @Test
    fun testOnRegistered() {
        createTestAccountInAccountManager()

        val restResponse = mockk<RestResponse>()
        every { restResponse.asString() } returns encodeToString(
            NotificationsActionsResponseBody.serializer(),
            NotificationsActionsResponseBody(
                message = "test_message"
            )
        )
        every { restResponse.consume() } returns Unit
        every { restResponse.isSuccess } returns true
        every { restResponse.statusCode } returns HTTP_CREATED
        every { restResponse.asJSONObject() } returns JSONObject("{\"id\": \"test_id\"}")
        val restClient = mockk<RestClient>()
        every { restClient.sendSync(any()) } returns restResponse

        PushService().onRegistered(
            registrationId = "test_registration_id",
            account = SalesforceSDKManager.getInstance().userAccountManager.currentUser,
            restClient = restClient
        )


        val restResponseNullId = mockk<RestResponse>()
        every { restResponseNullId.asString() } returns encodeToString(
            NotificationsActionsResponseBody.serializer(),
            NotificationsActionsResponseBody(
                message = "test_message"
            )
        )
        every { restResponseNullId.consume() } returns Unit
        every { restResponseNullId.isSuccess } returns true
        every { restResponseNullId.statusCode } returns HTTP_CREATED
        every { restResponseNullId.asJSONObject() } returns JSONObject("{\"id\": \"test_id\"}")
        val restClientNullId = mockk<RestClient>()
        every { restClientNullId.sendSync(any()) } throws Exception()

        PushService().onRegistered(
            registrationId = "test_registration_id",
            account = SalesforceSDKManager.getInstance().userAccountManager.currentUser,
            restClient = restClientNullId
        )


        val restResponseNullUserAccount = mockk<RestResponse>()
        every { restResponseNullUserAccount.asString() } returns encodeToString(
            NotificationsActionsResponseBody.serializer(),
            NotificationsActionsResponseBody(
                message = "test_message"
            )
        )
        every { restResponseNullUserAccount.consume() } returns Unit
        every { restResponseNullUserAccount.isSuccess } returns true
        every { restResponseNullUserAccount.statusCode } returns HTTP_CREATED
        every { restResponseNullUserAccount.asJSONObject() } returns JSONObject("{\"id\": \"test_id\"}")
        val restClientNullUserAccount = mockk<RestClient>()
        every { restClientNullUserAccount.sendSync(any()) } throws Exception()

        PushService().onRegistered(
            registrationId = "test_registration_id",
            account = null,
            restClient = restClientNullUserAccount
        )


        val restResponseCatchException = mockk<RestResponse>()
        every { restResponseCatchException.asString() } returns encodeToString(
            NotificationsActionsResponseBody.serializer(),
            NotificationsActionsResponseBody(
                message = "test_message"
            )
        )
        every { restResponseCatchException.consume() } returns Unit
        every { restResponseCatchException.isSuccess } returns true
        every { restResponseCatchException.statusCode } returns HTTP_CREATED
        every { restResponseCatchException.asJSONObject() } returns JSONObject("{\"id\": \"test_id\"}")
        val restClientCatchException = mockk<RestClient>()
        every { restClientCatchException.sendSync(any()) } returns restResponseCatchException

        object : PushService() {
            override fun onPushNotificationRegistrationStatus(status: Int, userAccount: UserAccount?) {
                super.onPushNotificationRegistrationStatus(status, userAccount)

                throw Exception("Exception to be caught by PushService.onRegistered without handling.")
            }
        }.onRegistered(
            registrationId = "test_registration_id",
            account = SalesforceSDKManager.getInstance().userAccountManager.currentUser,
            restClient = restClientCatchException
        )
    }

    @Test
    fun testRegisterSFDCPushNotification() {
        createTestAccountInAccountManager()

        val restResponse = mockk<RestResponse>()
        every { restResponse.asString() } returns encodeToString(
            NotificationsActionsResponseBody.serializer(),
            NotificationsActionsResponseBody(
                message = "test_message"
            )
        )
        every { restResponse.consume() } returns Unit
        every { restResponse.isSuccess } returns true
        every { restResponse.statusCode } returns HTTP_CREATED
        every { restResponse.asJSONObject() } returns JSONObject("{\"id\": \"test_id\"}")
        val restClient = mockk<RestClient>()
        every { restClient.sendSync(any()) } returns restResponse

        var actualStatus: Int? = null
        val actualId = object : PushService() {
            override fun onPushNotificationRegistrationStatus(
                status: Int,
                userAccount: UserAccount?
            ) {
                super.onPushNotificationRegistrationStatus(status, userAccount)

                actualStatus = status
            }
        }.registerSFDCPushNotification(
            registrationId = "test_registration_id",
            account = createTestAccount(),
            restClient = restClient
        )

        Assert.assertEquals(REGISTRATION_STATUS_SUCCEEDED, actualStatus)
        Assert.assertEquals("test_id", actualId)


        val restResponseUnknownStatus = mockk<RestResponse>()
        every { restResponseUnknownStatus.asString() } returns encodeToString(
            NotificationsActionsResponseBody.serializer(),
            NotificationsActionsResponseBody(
                message = "test_message"
            )
        )
        every { restResponseUnknownStatus.consume() } returns Unit
        every { restResponseUnknownStatus.isSuccess } returns true
        every { restResponseUnknownStatus.statusCode } returns 666
        every { restResponseUnknownStatus.asJSONObject() } returns JSONObject("{\"id\": \"test_id\"}")
        val restClientUnknownStatus = mockk<RestClient>()
        every { restClient.sendSync(any()) } returns restResponseUnknownStatus

        var actualStatusUnknownStatus: Int? = null
        val actualIdUnknownStatus = object : PushService() {
            override fun onPushNotificationRegistrationStatus(
                status: Int,
                userAccount: UserAccount?
            ) {
                super.onPushNotificationRegistrationStatus(status, userAccount)

                actualStatusUnknownStatus = status
            }
        }.registerSFDCPushNotification(
            registrationId = "test_registration_id",
            account = createTestAccount(),
            restClient = restClientUnknownStatus
        )

        Assert.assertEquals(REGISTRATION_STATUS_FAILED, actualStatusUnknownStatus)
        Assert.assertNull(actualIdUnknownStatus)


        val restResponseFailure = mockk<RestResponse>()
        every { restResponseFailure.asString() } returns encodeToString(
            NotificationsActionsResponseBody.serializer(),
            NotificationsActionsResponseBody(
                message = "test_message"
            )
        )
        every { restResponseFailure.consume() } returns Unit
        every { restResponseFailure.isSuccess } returns true
        every { restResponseFailure.statusCode } returns HTTP_NOT_FOUND
        every { restResponseFailure.asJSONObject() } returns JSONObject("{\"id\": \"test_id\"}")
        val restClientFailure = mockk<RestClient>()
        every { restClientFailure.sendSync(any()) } returns restResponseFailure

        var actualStatusForFailure: Int? = null
        val actualIdForFailure = object : PushService() {
            override fun onPushNotificationRegistrationStatus(
                status: Int,
                userAccount: UserAccount?
            ) {
                super.onPushNotificationRegistrationStatus(status, userAccount)

                actualStatusForFailure = status
            }
        }.registerSFDCPushNotification(
            registrationId = "test_registration_id",
            account = createTestAccount(),
            restClient = restClientFailure
        )

        Assert.assertEquals(REGISTRATION_STATUS_FAILED, actualStatusForFailure)
        Assert.assertEquals(NOT_ENABLED, actualIdForFailure)


        val restResponseNullResponseBody = mockk<RestResponse>()
        every { restResponseNullResponseBody.asString() } returns encodeToString(
            NotificationsActionsResponseBody.serializer(),
            NotificationsActionsResponseBody(
                message = "test_message"
            )
        )
        every { restResponseNullResponseBody.consume() } returns Unit
        every { restResponseNullResponseBody.isSuccess } returns true
        every { restResponseNullResponseBody.statusCode } returns HTTP_CREATED
        every { restResponseNullResponseBody.asJSONObject() } returns null
        val restClientNullResponseBody = mockk<RestClient>()
        every { restClientNullResponseBody.sendSync(any()) } returns restResponseNullResponseBody

        var actualStatusForNullResponseBody: Int? = null
        val actualIdForNullResponseBody = object : PushService() {
            override fun onPushNotificationRegistrationStatus(
                status: Int,
                userAccount: UserAccount?
            ) {
                super.onPushNotificationRegistrationStatus(status, userAccount)

                actualStatusForNullResponseBody = status
            }
        }.registerSFDCPushNotification(
            registrationId = "test_registration_id",
            account = createTestAccount(),
            restClient = restClientNullResponseBody
        )

        Assert.assertEquals(REGISTRATION_STATUS_FAILED, actualStatusForNullResponseBody)
        Assert.assertEquals(null, actualIdForNullResponseBody)


        val restResponseException = mockk<RestResponse>()
        every { restResponseException.asString() } returns encodeToString(
            NotificationsActionsResponseBody.serializer(),
            NotificationsActionsResponseBody(
                message = "test_message"
            )
        )
        every { restResponseException.consume() } throws Exception()
        every { restResponseException.isSuccess } returns true
        every { restResponseException.statusCode } returns HTTP_CREATED
        every { restResponseException.asJSONObject() } returns null
        val restClientException = mockk<RestClient>()
        every { restClientException.sendSync(any()) } returns restResponseException

        var actualStatusForException: Int? = null
        val actualIdForException = object : PushService() {
            override fun onPushNotificationRegistrationStatus(
                status: Int,
                userAccount: UserAccount?
            ) {
                super.onPushNotificationRegistrationStatus(status, userAccount)

                actualStatusForException = status
            }
        }.registerSFDCPushNotification(
            registrationId = "test_registration_id",
            account = createTestAccount(),
            restClient = restClientException
        )

        Assert.assertEquals(actualStatusForException, REGISTRATION_STATUS_FAILED)
        Assert.assertEquals(null, actualIdForException)
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
            NotificationsTypesResponseBody.serializer(),
            NotificationsTypesResponseBody.fromJson(NOTIFICATIONS_TYPES_JSON),
        )
        every { restResponse.isSuccess } returns true
        val restClient = mockk<RestClient>()
        every { restClient.sendSync(any()) } returns restResponse


        ApiVersionStrings.VERSION_NUMBER_TEST = null

        PushService().refreshNotificationsTypes(
            status = REGISTRATION_STATUS_SUCCEEDED,
            apiHostName = "",
            restClient = restClient,
            userAccount = null
        )

        Assert.assertNull(PushMessaging.getNotificationsTypes(user))


        ApiVersionStrings.VERSION_NUMBER_TEST = "v64.0"

        PushService().refreshNotificationsTypes(
            status = REGISTRATION_STATUS_SUCCEEDED,
            apiHostName = "",
            restClient = restClient,
            userAccount = null
        )
        Assert.assertNull(PushMessaging.getNotificationsTypes(user))

        createTestAccountInAccountManager()

        PushService().refreshNotificationsTypes(
            status = REGISTRATION_STATUS_SUCCEEDED,
            apiHostName = "",
            restClient = restClient,
            userAccount = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        )
        Assert.assertEquals(
            PushMessaging.getNotificationsTypes(SalesforceSDKManager.getInstance().userAccountManager.currentUser),
            NotificationsTypesResponseBody.fromJson(NOTIFICATIONS_TYPES_JSON)
        )


        PushService().refreshNotificationsTypes(
            status = UNREGISTRATION_STATUS_SUCCEEDED,
            apiHostName = "",
            restClient = restClient,
            userAccount = null
        )
        Assert.assertNull(PushMessaging.getNotificationsTypes(user))


        PushService().refreshNotificationsTypes(
            status = UNREGISTRATION_STATUS_SUCCEEDED,
            apiHostName = "",
            restClient = restClient,
            userAccount = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        )
        Assert.assertNull(PushMessaging.getNotificationsTypes(user))


        ApiVersionStrings.VERSION_NUMBER_TEST = null
    }

    @Test
    fun testRegisterNotificationChannels() {
        val notificationsTypesResponseBody = NotificationsTypesResponseBody.fromJson(NOTIFICATIONS_TYPES_JSON)
        createTestAccountInAccountManager()

        // Run first time to test initial creation of notification channels.
        PushService().registerNotificationChannels(notificationsTypesResponseBody)


        // Run second time to test re-use of existing channels.
        PushService().registerNotificationChannels(notificationsTypesResponseBody)

        SalesforceSDKManager.getInstance().appContext.getSystemService(NotificationManager::class.java).run {
            Assert.assertNotNull(
                getNotificationChannelGroup(
                    NOTIFICATION_CHANNEL_GROUP_SALESFORCE_ID
                )
            )
            notificationsTypesResponseBody.notificationTypes?.forEach {
                Assert.assertNotNull(notificationChannels.firstOrNull { notificationChannel ->
                    notificationChannel.id == it.type
                })
            }
        }


        // Test when no notification types are in the data.
        PushService().removeNotificationsCategories()
        PushService().registerNotificationChannels(notificationsTypesResponseBody.copy(notificationTypes = null))

        SalesforceSDKManager.getInstance().appContext.getSystemService(NotificationManager::class.java).run {
            Assert.assertTrue(
                notificationChannels.isEmpty()
            )
        }
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
            NotificationsTypesResponseBody.fromJson(NOTIFICATIONS_TYPES_JSON),
            PushMessaging.getNotificationsTypes(user)
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
