package com.salesforce.androidsdk.app

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.config.LoginServerManager.LoginServer
import com.salesforce.androidsdk.config.LoginServerManager.PRODUCTION_LOGIN_URL
import com.salesforce.androidsdk.config.LoginServerManager.WELCOME_LOGIN_URL
import com.salesforce.androidsdk.ui.LoginActivity
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for `SalesforceSDKManager`.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class SalesforceSDKManagerTests {

    private val responseBodyString =
        "{\"MobileSDK\":{\"UseAndroidNativeBrowserForAuthentication\":false,\"shareBrowserSessionAndroid\":false}}"

    private lateinit var responseBody: ResponseBody
    private lateinit var response: Response
    private lateinit var call: Call
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var httpAccess: HttpAccess

    @Before
    fun setup() {
        // Ensure the singleton SalesforceSDKManager is properly initialized
        // This is needed because AuthConfigUtil.getMyDomainAuthConfig() uses the singleton
        try {
            SalesforceSDKManager.getInstance()
        } catch (e: RuntimeException) {
            // Only initialize if this is the expected "not initialized" exception
            // Re-throw any other RuntimeException (memory issues, context problems, etc.)
            if (e.message?.contains("SalesforceSDKManager.init") == true) {
                SalesforceSDKManager.initNative(
                    getInstrumentation().targetContext,
                    LoginActivity::class.java
                )
            } else {
                throw e
            }
        }

        // Initialize mocks fresh for each test to avoid stale mock state
        // Using strict mocking (no relaxed = true) to catch unexpected method calls
        responseBody = mockk<ResponseBody>().apply {
            every { contentType() } returns "application/json;charset=UTF-8".toMediaType()
            every { bytes() } returns this@SalesforceSDKManagerTests.responseBodyString.toByteArray()
        }

        response = mockk<Response>().apply {
            every { isSuccessful } returns true
            every { body } returns this@SalesforceSDKManagerTests.responseBody
            every { close() } just runs
        }

        call = mockk<Call>().apply {
            every { execute() } returns this@SalesforceSDKManagerTests.response
        }

        okHttpClient = mockk<OkHttpClient>().apply {
            every { newCall(any()) } returns this@SalesforceSDKManagerTests.call
        }

        httpAccess = mockk<HttpAccess>().apply {
            every { getOkHttpClient() } returns this@SalesforceSDKManagerTests.okHttpClient
        }
    }

    @After
    fun teardown() {
        SalesforceSDKManager.getInstance().loginServerManager.reset()
        unmockkAll()
    }

    @Test
    fun salesforceSdkManager_Updates_onFetchAuthenticationConfigurationForMyDomainLoginServer() {

        SalesforceSDKManager.getInstance().isBrowserLoginEnabled = true
        SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled = true

        SalesforceSDKManager.getInstance().loginServerManager.setSelectedLoginServer(
            LoginServer(
                "Example",
                "https://www.example.com",
                true
            )
        )

        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccess,
            ) {
                /* Completion Does Not Require Verification */
            }.join()
        }

        assertFalse(SalesforceSDKManager.getInstance().isBrowserLoginEnabled)
        assertFalse(SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled)
    }

    @Test
    fun salesforceSdkManager_Updates_onFetchAuthenticationConfigurationForMyWelcomeLoginServer() {

        SalesforceSDKManager.getInstance().isBrowserLoginEnabled = true
        SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled = true

        SalesforceSDKManager.getInstance().loginServerManager.setSelectedLoginServer(
            LoginServer(
                "Welcome",
                WELCOME_LOGIN_URL,
                true
            )
        )

        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccess,
            ) {
                /* Completion Does Not Require Verification */
            }.join()
        }

        assertFalse(SalesforceSDKManager.getInstance().isBrowserLoginEnabled)
        assertFalse(SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled)
    }

    @Test
    fun salesforceSdkManager_Updates_onFetchAuthenticationConfigurationForSandboxLoginServer() {

        SalesforceSDKManager.getInstance().isBrowserLoginEnabled = true
        SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled = true

        SalesforceSDKManager.getInstance().loginServerManager.useSandbox()

        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccess,
            ) {
                /* Completion Does Not Require Verification */
            }.join()
        }

        assertFalse(SalesforceSDKManager.getInstance().isBrowserLoginEnabled)
        assertFalse(SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled)
    }

    @Test
    fun salesforceSdkManager_Updates_onFetchAuthenticationConfigurationForNonHttpsLoginServer() {

        SalesforceSDKManager.getInstance().isBrowserLoginEnabled = true
        SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled = true

        SalesforceSDKManager.getInstance().loginServerManager.setSelectedLoginServer(
            LoginServer(
                "Non-HTTPS",
                "http://www.example.com", // IETF-Reserved Test Domain
                true
            )
        )

        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccess,
            ) {
                /* Completion Does Not Require Verification */
            }.join()
        }

        assertFalse(SalesforceSDKManager.getInstance().isBrowserLoginEnabled)
        assertFalse(SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled)
    }

    @Test
    fun salesforceSdkManager_Updates_onFetchAuthenticationConfigurationForInvalidUrlLoginServer() {

        SalesforceSDKManager.getInstance().isBrowserLoginEnabled = true
        SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled = true

        SalesforceSDKManager.getInstance().loginServerManager.setSelectedLoginServer(
            LoginServer(
                "Invalid",
                "invalid_url",
                true
            )
        )

        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccess,
            ) {
                /* Completion Does Not Require Verification */
            }.join()
        }

        assertFalse(SalesforceSDKManager.getInstance().isBrowserLoginEnabled)
        assertFalse(SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled)

        // No verification for invalid URL - the fetch is skipped
    }

    @Test
    fun salesforceSdkManager_DoesNotUpdate_onFetchAuthenticationConfigurationWithError() {

        // Login Server: "My Domain"/Other URL, OkHttpClient Throws And Catch By AuthConfigUtil
        SalesforceSDKManager.getInstance().isBrowserLoginEnabled = false
        SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled = false

        SalesforceSDKManager.getInstance().loginServerManager.setSelectedLoginServer(
            LoginServer(
                "Example",
                "https://www.example.com",
                true
            )
        )

        // Mocks
        val httpAccessThrows = mockk<HttpAccess>()
        every { httpAccessThrows.getOkHttpClient() } throws (NullPointerException("Test Exception"))

        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccessThrows,
            ) {
                /* Completion Does Not Require Verification */
            }.join()
        }

        // Assert values haven't changed due to caught exception.
        assertFalse(SalesforceSDKManager.getInstance().isBrowserLoginEnabled)
        assertFalse(SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled)
    }

    @Test
    fun salesforceSdkManager_Updates_onFetchAuthenticationConfigurationForProductionLoginServer() {

        SalesforceSDKManager.getInstance().isBrowserLoginEnabled = true
        SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled = true

        SalesforceSDKManager.getInstance().loginServerManager.setSelectedLoginServer(
            LoginServer(
                "Production",
                PRODUCTION_LOGIN_URL,
                false
            )
        )

        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccess,
            ) {
                /* Completion Does Not Require Verification */
            }.join()
        }

        assertFalse(SalesforceSDKManager.getInstance().isBrowserLoginEnabled)
        assertFalse(SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled)
    }

    @Test
    fun salesforceSdkManager_ClearsAppAttestationHostName_ForNonMyDomainServer() {

        val salesforceSdkManager = createTestSalesforceSDKManager(googleCloudProjectId = 123456L)

        // Verify app attestation client exists and get non-null reference
        val appAttestationClient = requireNotNull(salesforceSdkManager.appAttestationClient) {
            "App attestation client should not be null"
        }

        // Set initial hostname value
        appAttestationClient.apiHostName = "test.example.com"
        assertEquals("test.example.com", appAttestationClient.apiHostName)

        // Initialize and set login server to production (non-My Domain)
        salesforceSdkManager.loginServerManager.reset()
        salesforceSdkManager.loginServerManager.setSelectedLoginServer(
            LoginServer(
                "Production",
                PRODUCTION_LOGIN_URL,
                false
            )
        )

        runBlocking {
            salesforceSdkManager.fetchAuthenticationConfiguration(
                httpAccess = httpAccess,
            ) {
                /* Completion Does Not Require Verification */
            }.join()
        }

        // Verify hostname was cleared for non-My Domain server
        assertNull(appAttestationClient.apiHostName)
    }

    @Test
    fun salesforceSdkManager_SetsAppAttestationHostName_ForMyDomainServer() {

        val salesforceSdkManager = createTestSalesforceSDKManager(googleCloudProjectId = 123456L)

        // Verify app attestation client exists and get non-null reference
        val appAttestationClient = requireNotNull(salesforceSdkManager.appAttestationClient) {
            "App attestation client should not be null"
        }

        // Initial hostname should be null
        assertNull(appAttestationClient.apiHostName)

        // Initialize and set login server to a My Domain server
        salesforceSdkManager.loginServerManager.reset()
        salesforceSdkManager.loginServerManager.setSelectedLoginServer(
            LoginServer(
                "Example",
                "https://www.example.com",
                true
            )
        )

        runBlocking {
            salesforceSdkManager.fetchAuthenticationConfiguration(
                httpAccess = httpAccess,
            ) {
                /* Completion Does Not Require Verification */
            }.join()

            // Small delay to ensure all async operations complete
            // including broadcast handling in AuthConfigUtil
            kotlinx.coroutines.delay(100)
        }

        // Verify hostname was set to the My Domain server host
        assertEquals("www.example.com", appAttestationClient.apiHostName)
    }

    @Test
    fun getDevActions_ReturnsAllActions_ForNonLoginActivity() {
        // Arrange
        val mockActivity = mockk<Activity>(relaxed = true)

        // Act
        val devActions = SalesforceSDKManager.getInstance().getDevActions(mockActivity)

        // Assert
        // Note: Logout and Switch User are only shown when there's a cached current user.
        // Since no user is logged in during tests, only 2 actions are expected.
        assertEquals(2, devActions.size)
        assertTrue(devActions.containsKey("Show dev info"))
        assertTrue(devActions.containsKey("Login Options"))
        assertFalse(devActions.containsKey("Logout"))
        assertFalse(devActions.containsKey("Switch User"))
        assertNotNull(devActions["Show dev info"])
        assertNotNull(devActions["Login Options"])
    }

    @Test
    fun getDevActions_ExcludesLogoutAndSwitchUser_ForLoginActivity() {
        // Arrange
        val mockLoginActivity = mockk<LoginActivity>(relaxed = true)

        // Act
        val devActions = SalesforceSDKManager.getInstance().getDevActions(mockLoginActivity)

        // Assert
        assertEquals(2, devActions.size)
        assertTrue(devActions.containsKey("Show dev info"))
        assertTrue(devActions.containsKey("Login Options"))
        assertFalse(devActions.containsKey("Logout"))
        assertFalse(devActions.containsKey("Switch User"))
        assertNotNull(devActions["Show dev info"])
        assertNotNull(devActions["Login Options"])
    }

    @Test
    fun salesforceSdkManager_appAttestationClient_isNullWhenNoGoogleCloudProjectIdProvided() {

        val salesforceSdkManager = createTestSalesforceSDKManager()

        assertNull(
            "appAttestationClient should be null when no googleCloudProjectId is provided.",
            salesforceSdkManager.appAttestationClient,
        )
    }

    @Test
    fun salesforceSdkManager_appAttestationClient_isCreatedWhenGoogleCloudProjectIdProvided() {

        val salesforceSdkManager = createTestSalesforceSDKManager(googleCloudProjectId = 123456L)

        val appAttestationClient = salesforceSdkManager.appAttestationClient
        assertNotNull(
            "appAttestationClient should be non-null when googleCloudProjectId is provided.",
            appAttestationClient,
        )
        assertEquals(123456L, appAttestationClient?.googleCloudProjectId)
        assertNotNull(appAttestationClient?.deviceId)
        assertEquals("__CONSUMER_KEY__", appAttestationClient?.remoteAccessConsumerKeyProvider?.getRemoteConsumerKey())
        assertNotNull(appAttestationClient?.restClient)
        // apiHostName starts null — it is set later by fetchAuthenticationConfiguration.
        assertNull(
            "apiHostName should initially be null before fetchAuthenticationConfiguration is called.",
            appAttestationClient?.apiHostName,
        )
    }

    @Test
    fun salesforceSdkManager_createAppAttestationClient_returnsNullForNullGoogleCloudProjectId() {

        val salesforceSdkManager = createTestSalesforceSDKManager()

        assertNull(salesforceSdkManager.createAppAttestationClient(googleCloudProjectId = null))
    }

    @Test
    fun salesforceSdkManager_createAppAttestationClient_returnsNullWhenCalledWithoutParameter() {

        val salesforceSdkManager = createTestSalesforceSDKManager()

        assertNull(salesforceSdkManager.createAppAttestationClient())
    }

    @Test
    fun salesforceSdkManager_createAppAttestationClient_returnsClientForNonNullGoogleCloudProjectId() {

        val salesforceSdkManager = createTestSalesforceSDKManager()

        val client = salesforceSdkManager.createAppAttestationClient(googleCloudProjectId = 654321L)
        assertNotNull(client)
        assertEquals(654321L, client?.googleCloudProjectId)
    }


    /**
     * Helper to create a test [SalesforceSDKManager] instance with optional
     * [googleCloudProjectId] for app attestation tests.
     */
    private fun createTestSalesforceSDKManager(
        googleCloudProjectId: Long? = null
    ): SalesforceSDKManager = if (googleCloudProjectId != null) {
        TestSalesforceSDKManagerWithAttestation(
            context = getInstrumentation().targetContext,
            mainActivity = LoginActivity::class.java,
            loginActivity = LoginActivity::class.java,
            googleCloudProjectId = googleCloudProjectId,
        )
    } else {
        SalesforceSDKManager(
            context = getInstrumentation().targetContext,
            mainActivity = LoginActivity::class.java,
            loginActivity = LoginActivity::class.java,
        )
    }

    /**
     * A minimal subclass of [SalesforceSDKManager] that exposes the protected
     * primary constructor so that tests can supply a [googleCloudProjectId].
     */
    private class TestSalesforceSDKManagerWithAttestation(
        context: android.content.Context,
        mainActivity: Class<out Activity>,
        loginActivity: Class<out Activity>? = null,
        googleCloudProjectId: Long? = null,
    ) : SalesforceSDKManager(context, mainActivity, loginActivity, null, googleCloudProjectId)
}
