package com.salesforce.androidsdk.app

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.config.LoginServerManager
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
        // Reset all singleton state to ensure test isolation
        // This prevents state leakage between tests
        SalesforceSDKManager.getInstance().apply {
            loginServerManager.reset()
            isBrowserLoginEnabled = false
            isShareBrowserSessionEnabled = false
            forceAdvancedAuthentication = true
        }
        unmockkAll()
    }

    @Test
    fun salesforceSdkManager_Updates_onFetchAuthenticationConfigurationForMyDomainLoginServer() {

        // Legacy behavior: with the force flag off, browser login follows the server's
        // auth-config (which opts out in responseBodyString).
        SalesforceSDKManager.getInstance().forceAdvancedAuthentication = false

        SalesforceSDKManager.getInstance().isBrowserLoginEnabled = true
        SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled = true

        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccess,
                loginServerUrl = "https://www.example.com", // IETF-Reserved Test Domain
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

        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccess,
                loginServerUrl = WELCOME_LOGIN_URL,
            ) {
                /* Completion Does Not Require Verification */
            }.join()
        }

        assertFalse(SalesforceSDKManager.getInstance().isBrowserLoginEnabled)
        assertFalse(SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled)
    }

    @Test
    fun salesforceSdkManager_Updates_onFetchAuthenticationConfigurationForSandboxLoginServer() {

        // Legacy behavior: with the force flag off, a standard sandbox login server
        // disables browser login.
        SalesforceSDKManager.getInstance().forceAdvancedAuthentication = false

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

        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccess,
                loginServerUrl = "http://www.example.com", // IETF-Reserved Test Domain
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

        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccess,
                loginServerUrl = "invalid_url",
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

        // Legacy behavior: with the force flag off, a failed auth-config fetch leaves the
        // (false) browser-login values unchanged.
        SalesforceSDKManager.getInstance().forceAdvancedAuthentication = false

        // Login Server: "My Domain"/Other URL, OkHttpClient Throws And Catch By AuthConfigUtil
        SalesforceSDKManager.getInstance().isBrowserLoginEnabled = false
        SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled = false

        // Mocks
        val httpAccessThrows = mockk<HttpAccess>()
        every { httpAccessThrows.getOkHttpClient() } throws (NullPointerException("Test Exception"))

        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccessThrows,
                loginServerUrl = "https://www.example.com",
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

        // Legacy behavior: with the force flag off, a standard production login server
        // disables browser login.
        SalesforceSDKManager.getInstance().forceAdvancedAuthentication = false

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
    fun fetchAuthenticationConfiguration_withLoginServerUrlOverride_usesOverrideOverPersistedSelectedServer() {

        // Legacy behavior: with the force flag off, the overridden My Domain server follows
        // its auth-config (which opts out in responseBodyString).
        SalesforceSDKManager.getInstance().forceAdvancedAuthentication = false

        SalesforceSDKManager.getInstance().loginServerManager.setSelectedLoginServer(
            LoginServer("Production", PRODUCTION_LOGIN_URL, false)
        )

        SalesforceSDKManager.getInstance().isBrowserLoginEnabled = true
        SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled = true

        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccess,
                loginServerUrl = "https://acme.my.salesforce.com",
            ) {
                /* Completion Does Not Require Verification */
            }.join()
        }

        assertFalse(SalesforceSDKManager.getInstance().isBrowserLoginEnabled)
        assertFalse(SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled)
        assertEquals(
            PRODUCTION_LOGIN_URL,
            SalesforceSDKManager.getInstance().loginServerManager.selectedLoginServer.url,
        )
    }

    @Test
    fun fetchAuthenticationConfiguration_ForceFlagOnAndMyDomainOptsOut_EnablesBrowserLogin() {

        // The force flag is on by default, but set it explicitly for clarity.
        SalesforceSDKManager.getInstance().forceAdvancedAuthentication = true

        SalesforceSDKManager.getInstance().isBrowserLoginEnabled = false
        SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled = false

        // My Domain auth-config that opts out of browser login; the force flag must still
        // enable it.
        val httpAccessOptOut = buildHttpAccessReturning(
            useNativeBrowser = false,
            shareBrowserSession = false,
        )

        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccessOptOut,
                loginServerUrl = "https://www.example.com",
            ) {
                /* Completion Does Not Require Verification */
            }.join()
        }

        assertTrue(SalesforceSDKManager.getInstance().isBrowserLoginEnabled)
        assertFalse(SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled)
    }

    @Test
    fun fetchAuthenticationConfiguration_ForceFlagOnAndMyDomainSharesSession_KeepsShareBrowserSessionEnabled() {

        SalesforceSDKManager.getInstance().forceAdvancedAuthentication = true

        SalesforceSDKManager.getInstance().isBrowserLoginEnabled = false
        SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled = false

        // My Domain auth-config that opts in to a shared browser session.  The force flag must
        // not clobber the server's shared-session value.
        val httpAccessShareSession = buildHttpAccessReturning(
            useNativeBrowser = true,
            shareBrowserSession = true,
        )

        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccessShareSession,
                loginServerUrl = "https://www.example.com",
            ) {
                /* Completion Does Not Require Verification */
            }.join()
        }

        assertTrue(SalesforceSDKManager.getInstance().isBrowserLoginEnabled)
        assertTrue(SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled)
    }

    @Test
    fun fetchAuthenticationConfiguration_ForceFlagOnAndStandardLoginServer_EnablesBrowserLoginWithoutSharedSession() {

        SalesforceSDKManager.getInstance().forceAdvancedAuthentication = true

        SalesforceSDKManager.getInstance().isBrowserLoginEnabled = false
        SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled = false

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

        // Standard login servers have no auth-config, so browser login is gated solely on the
        // force flag and shared session stays false.
        assertTrue(SalesforceSDKManager.getInstance().isBrowserLoginEnabled)
        assertFalse(SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled)
    }

    @Test
    fun fetchAuthenticationConfiguration_ForceFlagOffAndServerOptsOut_DisablesBrowserLogin() {

        // With the force flag off, browser login follows the server's auth-config.
        SalesforceSDKManager.getInstance().forceAdvancedAuthentication = false

        SalesforceSDKManager.getInstance().isBrowserLoginEnabled = true
        SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled = true

        val httpAccessOptOut = buildHttpAccessReturning(
            useNativeBrowser = false,
            shareBrowserSession = false,
        )

        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccessOptOut,
                loginServerUrl = "https://www.example.com",
            ) {
                /* Completion Does Not Require Verification */
            }.join()
        }

        assertFalse(SalesforceSDKManager.getInstance().isBrowserLoginEnabled)
        assertFalse(SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled)
    }

    @Test
    fun fetchAuthenticationConfiguration_ForceFlagOffAndServerOptsIn_EnablesBrowserLogin() {

        // With the force flag off, browser login follows the server's auth-config - the legacy
        // opt-in path must continue to work.
        SalesforceSDKManager.getInstance().forceAdvancedAuthentication = false

        SalesforceSDKManager.getInstance().isBrowserLoginEnabled = false
        SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled = false

        val httpAccessOptIn = buildHttpAccessReturning(
            useNativeBrowser = true,
            shareBrowserSession = false,
        )

        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccessOptIn,
                loginServerUrl = "https://www.example.com",
            ) {
                /* Completion Does Not Require Verification */
            }.join()
        }

        assertTrue(SalesforceSDKManager.getInstance().isBrowserLoginEnabled)
        assertFalse(SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled)
    }

    @Test
    fun forceAdvancedAuthentication_FreshManager_DefaultsToTrue() {

        // A freshly initialized manager defaults the force-advanced-authentication flag to true.
        val salesforceSdkManager = createTestSalesforceSDKManager()

        assertTrue(salesforceSdkManager.forceAdvancedAuthentication)
    }

    @Test
    fun fetchAuthenticationConfiguration_ForceFlagOnAndWelcomeDiscoveryHost_DisablesBrowserLogin() {

        SalesforceSDKManager.getInstance().forceAdvancedAuthentication = true

        SalesforceSDKManager.getInstance().isBrowserLoginEnabled = true
        SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled = true

        // The Welcome Discovery host is excluded from forced advanced authentication (phase-1
        // exclusion) and keeps the legacy disabled behavior even with the force flag on.
        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccess,
                loginServerUrl = WELCOME_LOGIN_URL,
            ) {
                /* Completion Does Not Require Verification */
            }.join()
        }

        assertFalse(SalesforceSDKManager.getInstance().isBrowserLoginEnabled)
        assertFalse(SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled)
    }

    @Test
    fun fetchAuthenticationConfiguration_ForceFlagOnAndNonHttpsServer_DisablesBrowserLogin() {

        SalesforceSDKManager.getInstance().forceAdvancedAuthentication = true

        SalesforceSDKManager.getInstance().isBrowserLoginEnabled = true
        SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled = true

        // Non-HTTPS servers are excluded from forced advanced authentication and keep the legacy
        // disabled behavior even with the force flag on.
        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccess,
                loginServerUrl = "http://www.example.com", // IETF-Reserved Test Domain
            ) {
                /* Completion Does Not Require Verification */
            }.join()
        }

        assertFalse(SalesforceSDKManager.getInstance().isBrowserLoginEnabled)
        assertFalse(SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled)
    }

    @Test
    fun salesforceSdkManager_ClearsAppAttestationHostName_ForNonMyDomainServer() {

        // Create test instance with production server (non-My Domain)
        val salesforceSdkManager = TestSalesforceSDKManagerWithAttestation(
            context = getInstrumentation().targetContext,
            mainActivity = LoginActivity::class.java,
            loginActivity = LoginActivity::class.java,
            googleCloudProjectId = 123456L,
            testLoginServer = LoginServer(
                "Production",
                PRODUCTION_LOGIN_URL,
                false
            )
        )

        // Verify app attestation client exists and get non-null reference
        val appAttestationClient = requireNotNull(salesforceSdkManager.appAttestationClient) {
            "App attestation client should not be null"
        }

        // Set initial hostname value
        appAttestationClient.apiHostName = "test.example.com"
        assertEquals("test.example.com", appAttestationClient.apiHostName)

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

        // Create test instance with My Domain server
        val testLoginServer = LoginServer(
            "Example",
            "https://www.example.com",
            true
        )
        val salesforceSdkManager = TestSalesforceSDKManagerWithAttestation(
            context = getInstrumentation().targetContext,
            mainActivity = LoginActivity::class.java,
            loginActivity = LoginActivity::class.java,
            googleCloudProjectId = 123456L,
            testLoginServer = testLoginServer
        )

        // Verify app attestation client exists and get non-null reference
        val appAttestationClient = requireNotNull(salesforceSdkManager.appAttestationClient) {
            "App attestation client should not be null"
        }

        // Initial hostname should be null
        assertNull(appAttestationClient.apiHostName)

        runBlocking {
            salesforceSdkManager.fetchAuthenticationConfiguration(
                httpAccess = httpAccess,
            ) {
                /* Completion Does Not Require Verification */
            }.join()
        }

        // Verify hostname was set to the My Domain server host
        assertEquals("www.example.com", appAttestationClient.apiHostName)
    }

    @Test
    fun getDevActions_ReturnsAllActions_ForNonLoginActivity() {
        // Arrange: a manager with a signed-in user, so Logout/Switch User are eligible.
        // Stubbing the current user keeps this deterministic instead of depending on
        // whatever Salesforce account happens to be present on the test device.
        val salesforceSdkManager = createTestSalesforceSDKManagerWithCurrentUser(
            mockk<UserAccount>(relaxed = true)
        )
        val mockActivity = mockk<Activity>(relaxed = true)

        // Act
        val devActions = salesforceSdkManager.getDevActions(mockActivity)

        // Assert
        assertEquals(4, devActions.size)
        assertTrue(devActions.containsKey("Show dev info"))
        assertTrue(devActions.containsKey("Login Options"))
        assertTrue(devActions.containsKey("Logout"))
        assertTrue(devActions.containsKey("Switch User"))
        assertNotNull(devActions["Show dev info"])
        assertNotNull(devActions["Login Options"])
        assertNotNull(devActions["Logout"])
        assertNotNull(devActions["Switch User"])
    }

    @Test
    fun getDevActions_ExcludesLogoutAndSwitchUser_ForLoginActivity() {
        // Arrange: even with a signed-in user, the Login screen must never expose
        // Logout/Switch User.  Stubbing the current user proves the LoginActivity check
        // is what excludes them (not merely the absence of a user).
        val salesforceSdkManager = createTestSalesforceSDKManagerWithCurrentUser(
            mockk<UserAccount>(relaxed = true)
        )
        val mockLoginActivity = mockk<LoginActivity>(relaxed = true)

        // Act
        val devActions = salesforceSdkManager.getDevActions(mockLoginActivity)

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
    fun getDevActions_ExcludesLogoutAndSwitchUser_WhenNoCurrentUser() {
        // Arrange: no signed-in user (cachedCurrentUser == null), so Logout/Switch User
        // are excluded even on a non-Login activity.
        val salesforceSdkManager = createTestSalesforceSDKManagerWithCurrentUser(null)
        val mockActivity = mockk<Activity>(relaxed = true)

        // Act
        val devActions = salesforceSdkManager.getDevActions(mockActivity)

        // Assert
        assertEquals(2, devActions.size)
        assertTrue(devActions.containsKey("Show dev info"))
        assertTrue(devActions.containsKey("Login Options"))
        assertFalse(devActions.containsKey("Logout"))
        assertFalse(devActions.containsKey("Switch User"))
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
    fun salesforceSdkManager_appAttestationClient_isCreatedWhenGoogleCloudProjectIdProvided() = runBlocking {

        val salesforceSdkManager = createTestSalesforceSDKManager(googleCloudProjectId = 123456L)

        val appAttestationClient = salesforceSdkManager.appAttestationClient
        assertNotNull(
            "appAttestationClient should be non-null when googleCloudProjectId is provided.",
            appAttestationClient,
        )
        assertEquals(123456L, appAttestationClient?.googleCloudProjectId)
        assertNotNull(appAttestationClient?.deviceId)
        assertEquals("__CONSUMER_KEY__", appAttestationClient?.remoteAccessConsumerKeyProvider?.getRemoteConsumerKey("https://login.salesforce.com"))
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


    @Test
    fun initNative_WithGoogleCloudProjectId_CreatesInstanceWithAppAttestationClient() {
        val instanceField = SalesforceSDKManager::class.java.getDeclaredField("INSTANCE")
        instanceField.isAccessible = true
        val originalInstance = instanceField.get(null)

        try {
            instanceField.set(null, null)

            SalesforceSDKManager.initNative(
                getInstrumentation().targetContext,
                LoginActivity::class.java,
                762473690072L,
            )

            assertNotNull(SalesforceSDKManager.getInstance().appAttestationClient)
        } finally {
            instanceField.set(null, originalInstance)
        }
    }

    @Test
    fun initNative_WithoutGoogleCloudProjectId_CreatesInstanceWithoutAppAttestationClient() {
        val sdkManager = TestSalesforceSDKManagerWithAttestation(
            context = getInstrumentation().targetContext,
            mainActivity = LoginActivity::class.java,
            loginActivity = LoginActivity::class.java,
            googleCloudProjectId = null,
        )

        assertNull(sdkManager.appAttestationClient)
    }

    // -------------------------------------------------------------------------
    // Per-user feature flag tests
    // -------------------------------------------------------------------------

    @Test
    fun test_givenTwoUsers_whenRegisterFeatureForUserA_thenOnlyUserAUAContainsFlag() {
        val sdkManager = createSdkManagerWithMockedAccountManager()

        val userA = buildMinimalUserAccount(orgId = "org1", userId = "user1")
        val userB = buildMinimalUserAccount(orgId = "org2", userId = "user2")

        sdkManager.registerUsedAppFeature("XY", userA)

        try {
            assertTrue(
                "getUserAgent for userA should contain XY",
                sdkManager.getUserAgent("", userA).contains("XY")
            )
            assertFalse(
                "getUserAgent for userB should NOT contain XY",
                sdkManager.getUserAgent("", userB).contains("XY")
            )
        } finally {
            sdkManager.unregisterUsedAppFeature("XY", userA)
        }
    }

    @Test
    fun test_givenGlobalAndPerUserFlags_whenGetUserAgentForUser_thenUnionPresent() {
        val sdkManager = createSdkManagerWithMockedAccountManager()

        val userA = buildMinimalUserAccount(orgId = "org1", userId = "user1")
        val userB = buildMinimalUserAccount(orgId = "org2", userId = "user2")

        sdkManager.registerUsedAppFeature("GL")
        sdkManager.registerUsedAppFeature("PU", userA)

        try {
            val agentA = sdkManager.getUserAgent("", userA)
            assertTrue("getUserAgent for userA should contain global flag GL", agentA.contains("GL"))
            assertTrue("getUserAgent for userA should contain per-user flag PU", agentA.contains("PU"))

            val agentB = sdkManager.getUserAgent("", userB)
            assertTrue("getUserAgent for userB should contain global flag GL", agentB.contains("GL"))
            assertFalse("getUserAgent for userB should NOT contain per-user flag PU", agentB.contains("PU"))
        } finally {
            sdkManager.unregisterUsedAppFeature("GL")
            sdkManager.unregisterUsedAppFeature("PU", userA)
        }
    }

    @Test
    fun test_givenRTRDetected_whenRegisterRTFeature_thenRTFlagAppearsInUserAgentForUser() {
        val sdkManager = createSdkManagerWithMockedAccountManager()

        val userA = buildMinimalUserAccount(orgId = "org1", userId = "user1")
        val userB = buildMinimalUserAccount(orgId = "org2", userId = "user2")

        // Act: simulate what ClientManager does on RTR detection
        sdkManager.registerUsedAppFeature(Features.FEATURE_RTR, userA)

        try {
            // Assert: RT appears in per-user user agent for userA
            val agentA = sdkManager.getUserAgent("", userA)
            assertTrue("User agent for userA should contain ftr_ segment", agentA.contains("ftr_"))
            assertTrue("User agent for userA should contain RT flag", agentA.contains(Features.FEATURE_RTR))

            // Assert: RT does NOT appear in user agent for a different user
            val agentB = sdkManager.getUserAgent("", userB)
            assertFalse(
                "User agent for userB should NOT contain RT flag (per-user isolation)",
                agentB.contains(Features.FEATURE_RTR)
            )
        } finally {
            sdkManager.unregisterUsedAppFeature(Features.FEATURE_RTR, userA)
        }
    }

    @Test
    fun test_givenNullUser_whenRegisterUsedAppFeature_thenGlobalFlagRegistered() {
        val sdkManager = SalesforceSDKManager.getInstance()

        sdkManager.registerUsedAppFeature("GF", null)

        try {
            assertTrue(
                "isGlobalFeatureRegistered should return true for GF",
                sdkManager.isGlobalFeatureRegistered("GF")
            )
        } finally {
            sdkManager.unregisterUsedAppFeature("GF")
        }
    }

    // -------------------------------------------------------------------------
    // Helpers for per-user feature flag tests
    // -------------------------------------------------------------------------

    /**
     * Builds a minimal [UserAccount] for testing using known test constants.
     * orgId and userId are parameterized so tests can create distinct users.
     */
    private fun buildMinimalUserAccount(orgId: String, userId: String): UserAccount =
        UserAccountBuilder.getInstance()
            .authToken("test_auth_token")
            .refreshToken("test_refresh_token")
            .loginServer("https://test.salesforce.com")
            .idUrl("https://test.salesforce.com/$orgId/$userId")
            .instanceServer("https://cs1.salesforce.com")
            .orgId(orgId)
            .userId(userId)
            .username("user_${userId}@example.com")
            .accountName("user_$userId (https://cs1.salesforce.com) (SalesforceSDKTest)")
            .build()

    /**
     * Creates a [SalesforceSDKManager] subclass whose [UserAccountManager] is fully
     * mocked so that [persistUserFeatureFlags] cannot reach [AccountManager].
     * This keeps per-user feature flag tests in-memory only.
     */
    private fun createSdkManagerWithMockedAccountManager(): SalesforceSDKManager =
        TestSalesforceSDKManagerWithMockedAccounts(
            context = getInstrumentation().targetContext,
            mainActivity = LoginActivity::class.java,
            loginActivity = LoginActivity::class.java,
        )

    /**
     * Builds an [HttpAccess] mock whose My Domain auth-config response advertises the provided
     * `UseAndroidNativeBrowserForAuthentication` and `shareBrowserSessionAndroid` values.  This
     * mirrors the default mock wiring in [setup] but lets a test vary the auth-config body so the
     * force-advanced-authentication decision can be exercised against opt-in / opt-out servers.
     */
    private fun buildHttpAccessReturning(
        useNativeBrowser: Boolean,
        shareBrowserSession: Boolean,
    ): HttpAccess {
        val bodyJson =
            "{\"MobileSDK\":{\"UseAndroidNativeBrowserForAuthentication\":$useNativeBrowser,\"shareBrowserSessionAndroid\":$shareBrowserSession}}"

        val responseBody = mockk<ResponseBody>().apply {
            every { contentType() } returns "application/json;charset=UTF-8".toMediaType()
            every { bytes() } returns bodyJson.toByteArray()
        }

        val response = mockk<Response>().apply {
            every { isSuccessful } returns true
            every { body } returns responseBody
            every { close() } just runs
        }

        val call = mockk<Call>().apply {
            every { execute() } returns response
        }

        val okHttpClient = mockk<OkHttpClient>().apply {
            every { newCall(any()) } returns call
        }

        return mockk<HttpAccess>().apply {
            every { getOkHttpClient() } returns okHttpClient
        }
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
     * Builds a test [SalesforceSDKManager] whose [UserAccountManager.getCachedCurrentUser]
     * returns [currentUser] (which may be null).  This lets dev-menu tests control the
     * signed-in state deterministically instead of depending on the accounts present on
     * the test device.
     */
    private fun createTestSalesforceSDKManagerWithCurrentUser(
        currentUser: UserAccount?
    ): SalesforceSDKManager {
        val userAccountManager = mockk<UserAccountManager>(relaxed = true).apply {
            every { cachedCurrentUser } returns currentUser
        }
        return TestSalesforceSDKManagerWithAttestation(
            context = getInstrumentation().targetContext,
            mainActivity = LoginActivity::class.java,
            loginActivity = LoginActivity::class.java,
            testUserAccountManager = userAccountManager,
        )
    }

    /**
     * A minimal subclass of [SalesforceSDKManager] that exposes the protected
     * primary constructor so that tests can supply a [googleCloudProjectId].
     *
     * This subclass also overrides [loginServerManager] to provide an
     * isolated test instance that doesn't share state via SharedPreferences.
     */
    private class TestSalesforceSDKManagerWithAttestation(
        context: android.content.Context,
        mainActivity: Class<out Activity>,
        loginActivity: Class<out Activity>? = null,
        googleCloudProjectId: Long? = null,
        private val testLoginServer: LoginServer? = null,
        private val testUserAccountManager: UserAccountManager? = null,
    ) : SalesforceSDKManager(context, mainActivity, loginActivity, null, googleCloudProjectId) {

        /**
         * Override to provide a test-specific LoginServerManager that uses
         * in-memory storage instead of SharedPreferences for test isolation.
         */
        override val loginServerManager: LoginServerManager by lazy {
            // Create a mock that doesn't use SharedPreferences
            mockk<LoginServerManager>(relaxed = true).apply {
                // Return the test login server when asked
                every { selectedLoginServer } returns (testLoginServer ?: LoginServer(
                    "Test",
                    "https://test.example.com",
                    false
                ))
                // No-op for reset() to avoid SharedPreferences access
                every { reset() } just runs
            }
        }

        /**
         * Override to provide a test-supplied UserAccountManager so tests can control
         * the cached current user deterministically, instead of depending on whatever
         * Salesforce account happens to be present on the device.  Falls back to the
         * default (device-backed) manager when no test instance is supplied.
         */
        override val userAccountManager: UserAccountManager by lazy {
            testUserAccountManager ?: super.userAccountManager
        }
    }

    /**
     * A [SalesforceSDKManager] subclass that replaces [userAccountManager] with a
     * relaxed mock so that [persistUserFeatureFlags] cannot reach [AccountManager].
     * Per-user feature flag tests use this to stay fully in-memory.
     */
    private class TestSalesforceSDKManagerWithMockedAccounts(
        context: android.content.Context,
        mainActivity: Class<out Activity>,
        loginActivity: Class<out Activity>? = null,
    ) : SalesforceSDKManager(context, mainActivity, loginActivity) {

        override val userAccountManager: UserAccountManager by lazy {
            mockk<UserAccountManager>(relaxed = true).apply {
                // buildAccount returns null → persistUserFeatureFlags exits early
                every { buildAccount(any()) } returns null
                // currentUser returns null → getUserAgent falls back to no per-user key
                every { currentUser } returns null
                // authenticatedUsers returns null → hydratePerUserFeatures is a no-op
                every { authenticatedUsers } returns null
            }
        }
    }
}
