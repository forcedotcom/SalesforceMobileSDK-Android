package com.salesforce.androidsdk.app

import android.accounts.AccountManager
import android.app.NotificationManager
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.accounts.UserAccountManagerTest.cleanupAccounts
import com.salesforce.androidsdk.accounts.UserAccountManagerTest.createTestAccountInAccountManager
import com.salesforce.androidsdk.accounts.UserAccountTest.createTestAccount
import com.salesforce.androidsdk.app.PushMessagingTest.Companion.NOTIFICATIONS_TYPES_JSON
import com.salesforce.androidsdk.push.PushMessaging
import com.salesforce.androidsdk.push.PushMessaging.clearNotificationsTypes
import com.salesforce.androidsdk.push.PushMessaging.getNotificationsTypes
import com.salesforce.androidsdk.push.PushNotificationsRegistrationChangeWorker.PushNotificationsRegistrationAction.Deregister
import com.salesforce.androidsdk.push.PushService
import com.salesforce.androidsdk.push.PushService.Companion.NOTIFICATION_CHANNEL_GROUP_SALESFORCE_ID
import com.salesforce.androidsdk.push.PushService.Companion.NOT_ENABLED
import com.salesforce.androidsdk.push.PushService.Companion.REGISTRATION_STATUS_FAILED
import com.salesforce.androidsdk.push.PushService.Companion.REGISTRATION_STATUS_SUCCEEDED
import com.salesforce.androidsdk.push.PushService.Companion.UNREGISTRATION_STATUS_SUCCEEDED
import com.salesforce.androidsdk.push.PushService.Companion.enqueuePushNotificationsRegistrationWork
import com.salesforce.androidsdk.push.PushService.PushNotificationReRegistrationType.ReRegistrationDisabled
import com.salesforce.androidsdk.rest.ApiVersionStrings.VERSION_NUMBER_TEST
import com.salesforce.androidsdk.rest.NotificationsActionsResponseBody
import com.salesforce.androidsdk.rest.NotificationsApiErrorResponseBody
import com.salesforce.androidsdk.rest.NotificationsApiException
import com.salesforce.androidsdk.rest.NotificationsTypesResponseBody
import com.salesforce.androidsdk.rest.NotificationsTypesResponseBody.Companion.fromJson
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestResponse
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json.Default.encodeToJsonElement
import kotlinx.serialization.json.Json.Default.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonArray.Companion.serializer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection.HTTP_CREATED
import java.net.HttpURLConnection.HTTP_NOT_FOUND

/**
 * Tests for `PushService`.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class PushServiceTest {

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
        val userAccountManager = UserAccountManager.getInstance().apply { this@PushServiceTest.userAccountManager = this }

        VERSION_NUMBER_TEST = null
        createTestAccountInAccountManager(userAccountManager)
        clearNotificationsTypes(user)
        cleanupAccounts(accountManager)

        assertNull("There should be no authenticated users.", userAccountManager.getAuthenticatedUsers())
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {

        VERSION_NUMBER_TEST = null
        cleanupAccounts(accountManager)

        userAccountManager = null
        accountManager = null
    }

    @Test
    fun testEnqueuePushNotificationsRegistrationWork_Register() {

        // TODO: This requires `WorkManager`, `HttpAccess` and `Context` mocks to test results in addition to coverage. ECJ20250416
        enqueuePushNotificationsRegistrationWork(
            createTestAccount(),
            Deregister,
            ReRegistrationDisabled,
            0
        )
    }

    @Test
    fun testEnqueuePushNotificationsRegistrationWork_DeRegister() {

        // TODO: This requires `WorkManager`, `HttpAccess` and `Context` mocks to test results in addition to coverage. ECJ20250416
        enqueuePushNotificationsRegistrationWork(
            createTestAccount(),
            Deregister,
            ReRegistrationDisabled,
            0
        )
    }

    @Test
    fun testFetchNotificationsTypes_UnsupportedApiVersion() {

        // Mocks.
        val restResponse = mockk<RestResponse>()
        every { restResponse.asString() } returns NOTIFICATIONS_TYPES_JSON
        every { restResponse.isSuccess } returns true
        val restClient = mockk<RestClient>()
        every { restClient.sendSync(any()) } returns restResponse

        val notificationsTypesResponseBody = PushService().fetchNotificationsTypes(
            restClient = restClient,
            userAccount = createTestAccount()
        )

        assertNull(notificationsTypesResponseBody)
    }

    @Test
    fun testFetchNotificationsTypes() {

        // Mocks.
        val restResponse = mockk<RestResponse>()
        every { restResponse.asString() } returns NOTIFICATIONS_TYPES_JSON
        every { restResponse.isSuccess } returns true
        val restClient = mockk<RestClient>()
        every { restClient.sendSync(any()) } returns restResponse

        // Setup.
        VERSION_NUMBER_TEST = "v64.0"

        val notificationsTypesResponseBody = PushService().fetchNotificationsTypes(
            restClient = restClient,
            userAccount = createTestAccount()
        )

        assertEquals(fromJson(NOTIFICATIONS_TYPES_JSON), notificationsTypesResponseBody)
    }

    @Test
    fun testFetchNotificationsTypes_NullResponseBodyString() {

        // Mocks
        val restResponseNullResponseBodyString = mockk<RestResponse>()
        every { restResponseNullResponseBodyString.asString() } returns null
        every { restResponseNullResponseBodyString.isSuccess } returns true
        val restClientNullResponseBodyString = mockk<RestClient>()
        every { restClientNullResponseBodyString.sendSync(any()) } returns restResponseNullResponseBodyString

        // Setup.
        VERSION_NUMBER_TEST = "v64.0"

        assertThrows(NotificationsApiException::class.java) {
            PushService().fetchNotificationsTypes(
                restClient = restClientNullResponseBodyString,
                userAccount = createTestAccount()
            )
        }
    }

    @Test
    fun testFetchNotificationsTypes_Failure() {

        // Mocks
        val restResponseFailure = mockk<RestResponse>()
        every { restResponseFailure.asString() } returns encodeToString(
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
        every { restResponseFailure.isSuccess } returns false

        val restClientFailure = mockk<RestClient>()
        every { restClientFailure.sendSync(any()) } returns restResponseFailure

        // Setup.
        VERSION_NUMBER_TEST = "v64.0"

        assertThrows(NotificationsApiException::class.java) {
            PushService().fetchNotificationsTypes(
                restClient = restClientFailure,
                userAccount = createTestAccount()
            )
        }
    }

    @Test
    fun testFetchNotificationsTypes_FailureEmpty() {

        // Mocks.
        val restResponseFailureEmpty = mockk<RestResponse>()
        every { restResponseFailureEmpty.asString() } returns encodeToString(
            serializer(),
            JsonArray(listOf())
        )
        every { restResponseFailureEmpty.isSuccess } returns false

        val restClientFailureEmpty = mockk<RestClient>()
        every { restClientFailureEmpty.sendSync(any()) } returns restResponseFailureEmpty

        // Setup.
        VERSION_NUMBER_TEST = "v64.0"

        assertThrows(NotificationsApiException::class.java) {
            PushService().fetchNotificationsTypes(
                restClient = restClientFailureEmpty,
                userAccount = createTestAccount()
            )
        }
    }

    @Test
    fun testFetchNotificationsTypes_FailureNullProperties() {

        // Mocks.
        val restResponseFailureNullProperties = mockk<RestResponse>()
        every { restResponseFailureNullProperties.asString() } returns encodeToString(
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
        every { restResponseFailureNullProperties.isSuccess } returns false

        val restClientFailureNullProperties = mockk<RestClient>()
        every { restClientFailureNullProperties.sendSync(any()) } returns restResponseFailureNullProperties

        // Setup.
        VERSION_NUMBER_TEST = "v64.0"

        assertThrows(NotificationsApiException::class.java) {
            PushService().fetchNotificationsTypes(
                restClient = restClientFailureNullProperties,
                userAccount = createTestAccount()
            )
        }
    }

    @Test
    fun testOnPushNotificationRegistrationStatusInternal() {

        // Mocks.
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
            restClient = restClient,
            userAccount = createTestAccount()
        )

        assertTrue(result)
    }

    @Test
    fun testOnRegistered() {

        // Mocks.
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

        // Setup.
        createTestAccountInAccountManager(userAccountManager)

        // TODO: This requires more mocks for result testing. ECJ20250416
        PushService().onRegistered(
            registrationId = "test_registration_id",
            account = user,
            restClient = restClient
        )
    }

    @Test
    fun testOnRegistered_NullId() {

        // Mocks.
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
        every { restClient.sendSync(any()) } throws Exception()

        // TODO: This requires more mocks for result testing. ECJ20250416
        PushService().onRegistered(
            registrationId = "test_registration_id",
            account = user,
            restClient = restClient
        )
    }

    @Test
    fun testOnRegistered_NullUserAccount() {

        // Mocks.
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
        every { restClient.sendSync(any()) } throws Exception()

        // TODO: This requires more mocks for result testing. ECJ20250416
        PushService().onRegistered(
            registrationId = "test_registration_id",
            account = null,
            restClient = restClient
        )
    }

    @Test
    fun testOnRegistered_CatchException() {

        // Mocks.
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

        // TODO: This requires more mocks for result testing. ECJ20250416
        object : PushService() {
            override fun onPushNotificationRegistrationStatus(status: Int, userAccount: UserAccount?) {
                super.onPushNotificationRegistrationStatus(status, userAccount)

                throw Exception("Exception to be caught by PushService.onRegistered without handling.")
            }
        }.onRegistered(
            registrationId = "test_registration_id",
            account = user,
            restClient = restClient
        )
    }

    @Test
    fun testOnUnRegistered() {

        // Mocks.
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

        // Setup.
        createTestAccountInAccountManager(userAccountManager)

        var statusActual: Int? = null
        object : PushService() {
            override fun onPushNotificationRegistrationStatus(status: Int, userAccount: UserAccount?) {
                super.onPushNotificationRegistrationStatus(status, userAccount)

                statusActual = status
            }
        }.onUnregistered(
            account = user,
            restClient = restClient
        )

        assertEquals(UNREGISTRATION_STATUS_SUCCEEDED, statusActual)
    }

    @Test
    fun testOnUnRegistered_LogsExceptions() {

        // Mocks.
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

        // Setup.
        createTestAccountInAccountManager(userAccountManager)

        // TODO: This requires more mocks for result testing. ECJ20250416
        object : PushService() {
            override fun unregisterSFDCPushNotification(
                registeredId: String?,
                account: UserAccount,
                restClient: RestClient
            ) {
                throw Exception("Test exception for code coverage.")
            }
        }.onUnregistered(
            account = user,
            restClient = restClient
        )
    }

    @Test
    fun testPerformRegistrationChange_RegisterWithoutRegistrationId() {

        createTestAccountInAccountManager(userAccountManager)

        // TODO: This requires more mocks for result testing. ECJ20250416
        PushService().performRegistrationChange(
            register = true,
            userAccount = user
        )
    }

    @Test
    fun testPerformRegistrationChange_Register() {
        val salesforceSdkManager = SalesforceSDKManager.getInstance()

        // Mocks.
        val restClient = mockk<RestClient>()

        // Setup.
        createTestAccountInAccountManager(userAccountManager)

        PushMessaging.setRegistrationId(
            context = salesforceSdkManager.appContext,
            registrationId = "test_registration_id",
            account = user
        )

        // TODO: This requires more mocks for result testing. ECJ20250416
        PushService().performRegistrationChange(
            register = true,
            userAccount = user,
            restClient = restClient
        )
    }

    @Test
    fun testPerformRegistrationChange_DeRegister() {
        val salesforceSdkManager = SalesforceSDKManager.getInstance()

        createTestAccountInAccountManager(userAccountManager)

        PushMessaging.setRegistrationId(
            context = salesforceSdkManager.appContext,
            registrationId = "test_registration_id",
            account = user
        )

        // TODO: This requires more mocks for result testing. ECJ20250416
        PushService().performRegistrationChange(
            register = false,
            userAccount = user
        )
    }

    @Test
    fun testRefreshNotificationsTypes_WithoutUserRequiredApiVersion() {

        // Mocks.
        val restResponse = mockk<RestResponse>()
        every { restResponse.asString() } returns encodeToString(
            NotificationsTypesResponseBody.serializer(),
            fromJson(NOTIFICATIONS_TYPES_JSON),
        )
        every { restResponse.isSuccess } returns true
        val restClient = mockk<RestClient>()
        every { restClient.sendSync(any()) } returns restResponse

        PushService().refreshNotificationsTypes(
            status = REGISTRATION_STATUS_SUCCEEDED,
            restClient = restClient,
            userAccount = null
        )

        assertNull(getNotificationsTypes(user))
    }

    @Test
    fun testRefreshNotificationsTypes_WithoutUserAccount() {

        // Mocks.
        val restResponse = mockk<RestResponse>()
        every { restResponse.asString() } returns encodeToString(
            NotificationsTypesResponseBody.serializer(),
            fromJson(NOTIFICATIONS_TYPES_JSON),
        )
        every { restResponse.isSuccess } returns true
        val restClient = mockk<RestClient>()
        every { restClient.sendSync(any()) } returns restResponse

        // Setup.
        VERSION_NUMBER_TEST = "v64.0"

        PushService().refreshNotificationsTypes(
            status = REGISTRATION_STATUS_SUCCEEDED,
            restClient = restClient,
            userAccount = null
        )

        assertNull(getNotificationsTypes(user))
    }

    @Test
    fun testRefreshNotificationsTypes_RegistrationSucceeded() {

        // Mocks.
        val restResponse = mockk<RestResponse>()
        every { restResponse.asString() } returns encodeToString(
            NotificationsTypesResponseBody.serializer(),
            fromJson(NOTIFICATIONS_TYPES_JSON),
        )
        every { restResponse.isSuccess } returns true
        val restClient = mockk<RestClient>()
        every { restClient.sendSync(any()) } returns restResponse

        // Setup.
        VERSION_NUMBER_TEST = "v64.0"
        createTestAccountInAccountManager(userAccountManager)

        PushService().refreshNotificationsTypes(
            status = REGISTRATION_STATUS_SUCCEEDED,
            restClient = restClient,
            userAccount = user
        )

        assertEquals(
            getNotificationsTypes(user),
            fromJson(NOTIFICATIONS_TYPES_JSON)
        )
    }

    @Test
    fun testRefreshNotificationsTypes_UnRegistrationSucceededWithoutUserAccount() {

        // Mocks.
        val restResponse = mockk<RestResponse>()
        every { restResponse.asString() } returns encodeToString(
            NotificationsTypesResponseBody.serializer(),
            fromJson(NOTIFICATIONS_TYPES_JSON),
        )
        every { restResponse.isSuccess } returns true
        val restClient = mockk<RestClient>()
        every { restClient.sendSync(any()) } returns restResponse

        // Setup.
        VERSION_NUMBER_TEST = "v64.0"

        PushService().refreshNotificationsTypes(
            status = UNREGISTRATION_STATUS_SUCCEEDED,
            restClient = restClient,
            userAccount = null
        )

        assertNull(getNotificationsTypes(user))
    }

    @Test
    fun testRefreshNotificationsTypes_UnRegistrationSucceeded() {

        // Mocks.
        val restResponse = mockk<RestResponse>()
        every { restResponse.asString() } returns encodeToString(
            NotificationsTypesResponseBody.serializer(),
            fromJson(NOTIFICATIONS_TYPES_JSON),
        )
        every { restResponse.isSuccess } returns true
        val restClient = mockk<RestClient>()
        every { restClient.sendSync(any()) } returns restResponse

        // Setup.
        VERSION_NUMBER_TEST = "v64.0"

        PushService().refreshNotificationsTypes(
            status = UNREGISTRATION_STATUS_SUCCEEDED,
            restClient = restClient,
            userAccount = user
        )

        assertNull(getNotificationsTypes(user))
    }

    @Test
    fun testRegisterNotificationChannels() {
        val salesforceSdkManager = SalesforceSDKManager.getInstance()

        // Setup.
        val notificationsTypesResponseBody = fromJson(NOTIFICATIONS_TYPES_JSON)
        createTestAccountInAccountManager(userAccountManager)

        // Run first time to test initial creation of notification channels.
        PushService().removeNotificationsCategories()
        PushService().registerNotificationChannels(notificationsTypesResponseBody)

        // Run second time to test re-use of existing channels.
        PushService().registerNotificationChannels(notificationsTypesResponseBody)

        salesforceSdkManager.appContext.getSystemService(NotificationManager::class.java).run {
            assertNotNull(
                getNotificationChannelGroup(
                    NOTIFICATION_CHANNEL_GROUP_SALESFORCE_ID
                )
            )
            notificationsTypesResponseBody.notificationTypes?.forEach {
                assertNotNull(notificationChannels.firstOrNull { notificationChannel ->
                    notificationChannel.id == it.type
                })
            }
        }
    }

    @Test
    fun testRegisterNotificationChannels_WithoutNotificationTypes() {
        val salesforceSdkManager = SalesforceSDKManager.getInstance()

        // Setup.
        val notificationsTypesResponseBody = fromJson(NOTIFICATIONS_TYPES_JSON)
        createTestAccountInAccountManager(userAccountManager)

        // Test when no notification types are in the data.
        PushService().removeNotificationsCategories()
        PushService().registerNotificationChannels(notificationsTypesResponseBody.copy(notificationTypes = null))

        salesforceSdkManager.appContext.getSystemService(NotificationManager::class.java).run {
            assertTrue(
                notificationChannels.isEmpty()
            )
        }
    }

    @Test
    fun testRegisterSFDCPushNotification() {
        createTestAccountInAccountManager(userAccountManager)

        // Mocks.
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

        assertEquals(REGISTRATION_STATUS_SUCCEEDED, actualStatus)
        assertEquals("test_id", actualId)
    }

    @Test
    fun testRegisterSFDCPushNotification_UnknownHttpStatus() {
        createTestAccountInAccountManager(userAccountManager)

        // Mocks.
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
        every { restClientUnknownStatus.sendSync(any()) } returns restResponseUnknownStatus

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

        assertEquals(REGISTRATION_STATUS_FAILED, actualStatusUnknownStatus)
        assertNull(actualIdUnknownStatus)
    }

    @Test
    fun testRegisterSFDCPushNotification_Failure() {
        createTestAccountInAccountManager(userAccountManager)

        // Mocks.
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

        assertEquals(REGISTRATION_STATUS_FAILED, actualStatusForFailure)
        assertEquals(NOT_ENABLED, actualIdForFailure)
    }

    @Test
    fun testRegisterSFDCPushNotification_NullResponseBody() {
        createTestAccountInAccountManager(userAccountManager)

        // Mocks.
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

        assertEquals(REGISTRATION_STATUS_FAILED, actualStatusForNullResponseBody)
        assertEquals(null, actualIdForNullResponseBody)
    }

    @Test
    fun testRegisterSFDCPushNotification_Exception() {
        createTestAccountInAccountManager(userAccountManager)

        // Mocks.
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

        assertEquals(actualStatusForException, REGISTRATION_STATUS_FAILED)
        assertEquals(null, actualIdForException)
    }

    @Test
    fun testRemoveNotificationsCategories() {
        PushService().removeNotificationsCategories()
    }

    companion object {

        private val user = UserAccount(Bundle().apply {
            putString("orgId", "org-1")
            putString("userId", "user-1-1")
        })
    }
}