package com.salesforce.androidsdk.app

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.auth.AuthenticatorService.KEY_BEACON_CHILD_CONSUMER_KEY
import com.salesforce.androidsdk.auth.AuthenticatorService.KEY_BEACON_CHILD_CONSUMER_SECRET
import com.salesforce.androidsdk.auth.AuthenticatorService.KEY_CONTENT_SID
import com.salesforce.androidsdk.auth.AuthenticatorService.KEY_COOKIE_CLIENT_SRC
import com.salesforce.androidsdk.auth.AuthenticatorService.KEY_COOKIE_SID_CLIENT
import com.salesforce.androidsdk.auth.AuthenticatorService.KEY_CREDENTIALS_IDENTIFIER
import com.salesforce.androidsdk.auth.AuthenticatorService.KEY_CSRF_TOKEN
import com.salesforce.androidsdk.auth.AuthenticatorService.KEY_LIGHTNING_SID
import com.salesforce.androidsdk.auth.AuthenticatorService.KEY_ORG_ID
import com.salesforce.androidsdk.auth.AuthenticatorService.KEY_PARENT_SID
import com.salesforce.androidsdk.auth.AuthenticatorService.KEY_SID_COOKIE_NAME
import com.salesforce.androidsdk.auth.AuthenticatorService.KEY_USER_ID
import com.salesforce.androidsdk.auth.AuthenticatorService.KEY_VF_SID
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.auth.OAuth2
import com.salesforce.androidsdk.auth.OAuth2.LogoutReason.USER_LOGOUT
import com.salesforce.androidsdk.config.LoginServerManager
import com.salesforce.androidsdk.config.LoginServerManager.LoginServer
import com.salesforce.androidsdk.config.LoginServerManager.PRODUCTION_LOGIN_URL
import com.salesforce.androidsdk.config.LoginServerManager.WELCOME_LOGIN_URL
import com.salesforce.androidsdk.push.PushMessaging
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.ui.LoginActivity
import com.salesforce.androidsdk.util.EventsObservable
import com.salesforce.androidsdk.util.EventsObservable.EventType.LogoutComplete
import com.salesforce.androidsdk.util.test.EventsObserver
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import java.io.IOException
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
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean

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
    @Suppress("DEPRECATION") // Exercises the deprecated forceAdvancedAuthentication flag.
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
    @Suppress("DEPRECATION") // Exercises the deprecated forceAdvancedAuthentication flag.
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
    @Suppress("DEPRECATION") // Exercises the deprecated forceAdvancedAuthentication flag.
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
    @Suppress("DEPRECATION") // Exercises the deprecated forceAdvancedAuthentication flag.
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
    @Suppress("DEPRECATION") // Exercises the deprecated forceAdvancedAuthentication flag.
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
    @Suppress("DEPRECATION") // Exercises the deprecated forceAdvancedAuthentication flag.
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
    @Suppress("DEPRECATION") // Exercises the deprecated forceAdvancedAuthentication flag.
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
    @Suppress("DEPRECATION") // Exercises the deprecated forceAdvancedAuthentication flag.
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
    @Suppress("DEPRECATION") // Exercises the deprecated forceAdvancedAuthentication flag.
    fun fetchAuthenticationConfiguration_ForceFlagOnAndStandardLoginServer_EnablesBrowserLoginWithoutSharedSession() {

        SalesforceSDKManager.getInstance().forceAdvancedAuthentication = true

        SalesforceSDKManager.getInstance().isBrowserLoginEnabled = false
        SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled = false

        // Deliberately differs from the My Domain tests above: persist a standard server via
        // setSelectedLoginServer and omit the loginServerUrl override so the default no-override
        // branch of fetchAuthenticationConfiguration is exercised (it resolves the target from
        // loginServerManager.selectedLoginServer).  A standard server (Production) is required to
        // reach the isStandardLoginServer branch; the transient My Domain override used elsewhere
        // would fall through to the My Domain auth-config path instead.
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
    @Suppress("DEPRECATION") // Exercises the deprecated forceAdvancedAuthentication flag.
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
    @Suppress("DEPRECATION") // Exercises the deprecated forceAdvancedAuthentication flag.
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
    @Suppress("DEPRECATION") // Exercises the deprecated forceAdvancedAuthentication flag.
    fun forceAdvancedAuthentication_FreshManager_DefaultsToTrue() {

        // A freshly initialized manager defaults the force-advanced-authentication flag to true.
        val salesforceSdkManager = createTestSalesforceSDKManager()

        assertTrue(salesforceSdkManager.forceAdvancedAuthentication)
    }

    @Test
    @Suppress("DEPRECATION") // Exercises the deprecated forceAdvancedAuthentication flag.
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
    @Suppress("DEPRECATION") // Exercises the deprecated forceAdvancedAuthentication flag.
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
        assertEquals(5, devActions.size)
        assertTrue(devActions.containsKey("Show dev info"))
        assertTrue(devActions.containsKey("Login Options"))
        assertTrue(devActions.containsKey("Logout"))
        assertTrue(devActions.containsKey("Switch User"))
        assertTrue(devActions.containsKey("Force Token Refresh"))
        assertNotNull(devActions["Show dev info"])
        assertNotNull(devActions["Login Options"])
        assertNotNull(devActions["Logout"])
        assertNotNull(devActions["Switch User"])
        assertNotNull(devActions["Force Token Refresh"])
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
    fun clientManager_ReturnsNullWhenNoUserIsCurrent() {
        val salesforceSdkManager = createTestSalesforceSDKManagerWithCurrentUser(null)

        assertNull(salesforceSdkManager.clientManager)
    }

    @Test
    fun getUnauthenticatedRestClient_ReturnsCredentialFreeClient() {
        val client = createTestSalesforceSDKManager().getUnauthenticatedRestClient()

        assertTrue(client.clientInfo is RestClient.UnauthenticatedClientInfo)
        assertNull(client.authToken)
        assertNull(client.refreshToken)
    }

    @Test
    fun logout_RemovesAccountWithoutWaitingForPushUnregistration() {
        val fixture = createLogoutFixture(pushRegistered = true)
        val observedLoggingOut = AtomicBoolean()
        fixture.sdkManager.onCleanUp = {
            observedLoggingOut.set(fixture.sdkManager.isLoggingOut(fixture.account))
        }

        fixture.sdkManager.logout(fixture.account, null, false, USER_LOGOUT)

        assertTrue(fixture.removalCompleted.await(5, SECONDS))
        verify(exactly = 1) {
            PushMessaging.unregisterForLogout(any(), fixture.user, false)
        }
        verify(exactly = 1) {
            fixture.accountManager.removeAccountExplicitly(fixture.account)
        }
        assertTrue("Logout state should cover synchronous local cleanup", observedLoggingOut.get())
        verify(exactly = 0) {
            fixture.userAccountManager.clearStoredCurrentUserInfo()
        }
        assertFalse(fixture.sdkManager.isLoggingOut)
        assertFalse(fixture.sdkManager.isLoggingOut(fixture.account))
        assertEquals(listOf(fixture.user), fixture.sdkManager.cleanedUsers)
    }

    @Test
    fun logout_SuccessfulRemovalSelectsTheLiveRemainingUser() {
        val fixture = createLogoutFixture()

        fixture.sdkManager.logout(fixture.account, null, true, USER_LOGOUT)

        assertTrue(fixture.removalCompleted.await(5, SECONDS))
        verify(exactly = 1) {
            fixture.userAccountManager.switchToUser(
                fixture.otherUser,
                UserAccountManager.USER_SWITCH_TYPE_LOGOUT,
                null,
            )
        }
    }

    @Test
    fun logout_AbsentAccountDoesNothing() {
        val fixture = createLogoutFixture(accountPresent = false)

        fixture.sdkManager.logout(fixture.account, null, false, USER_LOGOUT)

        verify(exactly = 0) { fixture.accountManager.removeAccountExplicitly(any()) }
        verify(exactly = 0) { PushMessaging.unregisterForLogout(any(), any(), any()) }
        verify(exactly = 0) { OAuth2.revokeRefreshToken(any(), any(), any(), any()) }
        assertTrue(fixture.sdkManager.cleanedUsers.isEmpty())
        assertFalse(fixture.sdkManager.isLoggingOut)
    }

    @Test
    fun logout_MalformedPersistedAccountIsPurgedLocally() {
        val fixture = createLogoutFixture(malformed = true)

        fixture.sdkManager.logout(fixture.account, null, false, USER_LOGOUT)

        assertTrue(fixture.removalCompleted.await(5, SECONDS))
        verify(exactly = 1) {
            fixture.accountManager.removeAccountExplicitly(fixture.account)
        }
        verify(exactly = 0) { PushMessaging.unregisterForLogout(any(), any(), any()) }
        verify(exactly = 0) { OAuth2.revokeRefreshToken(any(), any(), any(), any()) }
        verify(exactly = 1) {
            fixture.userAccountManager.clearStoredCurrentUserInfo()
        }
        assertEquals(1, fixture.sdkManager.cleanedUsers.size)
        assertEquals(fixture.user.userId, fixture.sdkManager.cleanedUsers.single()?.userId)
        assertEquals(fixture.user.orgId, fixture.sdkManager.cleanedUsers.single()?.orgId)
        assertFalse(fixture.sdkManager.isLoggingOut)
    }

    @Test
    fun logout_RemainingMalformedAccountPreventsLastPersistedAccountCleanup() {
        val fixture = createLogoutFixture(otherUserMalformed = true)
        val activity = mockk<Activity>(relaxed = true)

        fixture.sdkManager.logout(fixture.account, activity, true, USER_LOGOUT)

        assertTrue(fixture.removalCompleted.await(5, SECONDS))
        verify(exactly = 0) { activity.finish() }
        assertTrue(fixture.sdkManager.loginPageStarted)
    }

    @Test
    fun logout_MalformedAccountRemovalFailureStillCompletesLocalLogout() {
        val fixture = createLogoutFixture(
            malformed = true,
            removeAccountSucceeds = false,
        )
        val logoutReported = AtomicBoolean(false)
        val observer = EventsObserver { event ->
            if (event.type == LogoutComplete) {
                logoutReported.set(true)
            }
        }
        EventsObservable.get().registerObserver(observer)

        try {
            fixture.sdkManager.logout(fixture.account, null, false, USER_LOGOUT)
        } finally {
            EventsObservable.get().unregisterObserver(observer)
        }

        verify(exactly = 1) {
            fixture.accountManager.removeAccountExplicitly(fixture.account)
        }
        verifyPersistedCredentialsCleared(fixture)
        verify(exactly = 0) { PushMessaging.unregisterForLogout(any(), any(), any()) }
        verify(exactly = 0) { OAuth2.revokeRefreshToken(any(), any(), any(), any()) }
        assertEquals(1, fixture.sdkManager.cleanedUsers.size)
        assertTrue(logoutReported.get())
        assertFalse(fixture.sdkManager.isLoggingOut)
    }

    @Test
    fun logout_MalformedAccountWithoutCleanupIdentityStillCompletesLocalLogout() {
        val fixture = createLogoutFixture(
            malformed = true,
            cleanupIdentityAvailable = false,
            removeAccountSucceeds = false,
        )

        fixture.sdkManager.logout(fixture.account, null, false, USER_LOGOUT)

        verifyPersistedCredentialsCleared(fixture)
        verify(exactly = 0) {
            fixture.userAccountManager.clearStoredCurrentUserInfo()
        }
        assertEquals(listOf(null), fixture.sdkManager.cleanedUsers)
        assertFalse(fixture.sdkManager.isLoggingOut)
    }

    @Test
    fun logout_UnusableAccountRemovalFailureClearsCredentialsAndCompletesLocalLogout() {
        val fixture = createLogoutFixture(
            loginServer = null,
            removeAccountSucceeds = false,
        )
        fixture.sdkManager.additionalOauthKeys = listOf("test-session-extra")
        val logoutReported = AtomicBoolean(false)
        val observer = EventsObserver { event ->
            if (event.type == LogoutComplete) {
                logoutReported.set(true)
            }
        }
        EventsObservable.get().registerObserver(observer)

        try {
            fixture.sdkManager.logout(fixture.account, null, true, USER_LOGOUT)
        } finally {
            EventsObservable.get().unregisterObserver(observer)
        }

        verifyPersistedCredentialsCleared(fixture)
        verify(exactly = 1) {
            fixture.accountManager.setUserData(
                fixture.account,
                "test-session-extra",
                null,
            )
        }
        verify(exactly = 1) {
            fixture.userAccountManager.clearStoredCurrentUserInfo()
            fixture.userAccountManager.switchToUser(
                fixture.otherUser,
                UserAccountManager.USER_SWITCH_TYPE_LOGOUT,
                null,
            )
        }
        verify(exactly = 0) { OAuth2.revokeRefreshToken(any(), any(), any(), any()) }
        assertEquals(listOf(fixture.user), fixture.sdkManager.cleanedUsers)
        assertTrue(logoutReported.get())
        assertFalse(fixture.sdkManager.isLoggingOut)
    }

    @Test
    fun logout_RevokesCapturedCredentialsAfterLocalRemoval() {
        val fixture = createLogoutFixture()

        fixture.sdkManager.logout(fixture.account, null, false, USER_LOGOUT)

        assertTrue(fixture.removalCompleted.await(5, SECONDS))
        assertTrue(fixture.revocationCompleted.await(5, SECONDS))
        verify(exactly = 1) {
            OAuth2.revokeRefreshToken(
                any(),
                URI("https://login.example.com"),
                "refresh-token-user",
                USER_LOGOUT,
            )
        }
    }

    @Test
    fun logout_PushUnregisterStartupFailureContinuesLocalLogout() {
        val fixture = createLogoutFixture(pushRegistered = true)
        every {
            PushMessaging.unregisterForLogout(any(), fixture.user, false)
        } throws IllegalStateException("push unregister failed")

        fixture.sdkManager.logout(fixture.account, null, false, USER_LOGOUT)

        assertTrue(fixture.removalCompleted.await(5, SECONDS))
        verify(exactly = 1) {
            fixture.accountManager.removeAccountExplicitly(fixture.account)
        }
        assertFalse(fixture.sdkManager.isLoggingOut)
    }

    @Test
    fun logout_RegisteredAccountWithoutRefreshTokenStillStartsPushUnregistration() {
        val fixture = createLogoutFixture(
            pushRegistered = true,
            refreshToken = null,
        )

        fixture.sdkManager.logout(fixture.account, null, false, USER_LOGOUT)

        assertTrue(fixture.removalCompleted.await(5, SECONDS))
        verify(exactly = 1) {
            PushMessaging.unregisterForLogout(any(), fixture.user, false)
        }
        verify(exactly = 1) {
            fixture.accountManager.removeAccountExplicitly(fixture.account)
        }
        verify(exactly = 0) {
            OAuth2.revokeRefreshToken(any(), any(), any(), any())
        }
        assertFalse(fixture.sdkManager.isLoggingOut(fixture.account))
    }

    @Test
    fun logout_FailedAccountRemovalStillCleansUpAndRevokesToken() {
        val fixture = createLogoutFixture(removeAccountSucceeds = false)
        val logoutReported = AtomicBoolean(false)
        val observer = EventsObserver { event ->
            if (event.type == LogoutComplete) {
                logoutReported.set(true)
            }
        }
        EventsObservable.get().registerObserver(observer)

        try {
            fixture.sdkManager.logout(fixture.account, null, false, USER_LOGOUT)
        } finally {
            EventsObservable.get().unregisterObserver(observer)
        }

        assertTrue(fixture.revocationCompleted.await(5, SECONDS))
        verify(exactly = 1) {
            fixture.accountManager.removeAccountExplicitly(fixture.account)
        }
        verifyPersistedCredentialsCleared(fixture)
        verify(exactly = 1) {
            OAuth2.revokeRefreshToken(
                any(),
                URI("https://login.example.com"),
                "refresh-token-user",
                USER_LOGOUT,
            )
        }
        assertEquals(listOf(fixture.user), fixture.sdkManager.cleanedUsers)
        assertTrue(logoutReported.get())
        assertFalse(fixture.sdkManager.isLoggingOut)
        assertFalse(fixture.sdkManager.isLoggingOut(fixture.account))
    }

    @Test
    fun clearWebViewCookiesAfterLogout_ClearsSharedJar() {
        val cookieManager = mockk<CookieManager>(relaxed = true)
        mockkStatic(CookieManager::class)
        every { CookieManager.getInstance() } returns cookieManager
        val sdkManager = LogoutTestSalesforceSDKManager(
            getInstrumentation().targetContext,
            mockk(relaxed = true),
        )

        sdkManager.clearWebViewCookiesAfterLogout()

        verify(exactly = 1) { cookieManager.removeAllCookies(null) }
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
    fun test_givenDPoPSession_whenRegisterDPFeature_thenDPFlagAppearsInUserAgentForUser() {
        val sdkManager = createSdkManagerWithMockedAccountManager()

        val userA = buildMinimalUserAccount(orgId = "org1", userId = "user1")
        val userB = buildMinimalUserAccount(orgId = "org2", userId = "user2")

        // Act: simulate what LoginActivity does when tokenType == "DPoP"
        sdkManager.registerUsedAppFeature(Features.FEATURE_DPOP, userA)

        try {
            // Assert: DP appears in per-user user agent for userA
            val agentA = sdkManager.getUserAgent("", userA)
            assertTrue("User agent for userA should contain ftr_ segment", agentA.contains("ftr_"))
            assertTrue("User agent for userA should contain DP flag", agentA.contains(Features.FEATURE_DPOP))

            // Assert: DP does NOT appear in user agent for a different user
            val agentB = sdkManager.getUserAgent("", userB)
            assertFalse(
                "User agent for userB should NOT contain DP flag (per-user isolation)",
                agentB.contains(Features.FEATURE_DPOP)
            )
        } finally {
            sdkManager.unregisterUsedAppFeature(Features.FEATURE_DPOP, userA)
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

    /*
     * devSupportInfo RTR section wiring tests
     *
     * These exercise the getter that connects isUserFeatureRegistered to
     * parseRtrSection (the seam DevSupportInfoTest cannot reach, since it
     * passes rtrActive in explicitly). They prove the getter reads the current
     * user's per-user RTR flag and reflects it in the RTR section rows.
     */

    @Test
    fun test_givenNoCurrentUser_whenDevSupportInfo_thenRtrSectionShowsNA() {
        // Arrange: no signed-in user.
        val sdkManager = createTestSalesforceSDKManagerWithCurrentUser(null)

        // Act
        val rtrSection = sdkManager.devSupportInfo.additionalSections.first { it.first == "RTR" }

        // Assert: per-user fields read "N/A".
        assertEquals("N/A", rtrSection.second.first { it.first == "RTR Active" }.second)
        assertEquals("N/A", rtrSection.second.first { it.first == "Last Rotation" }.second)
    }

    @Test
    fun test_givenCurrentUserWithoutRTR_whenDevSupportInfo_thenRtrSectionShowsFalseAndNever() {
        /*
         * Arrange: a signed-in user whose RTR feature flag has never been
         * registered and whose lastTokenRotationTime is unset.
         */
        val user = buildMinimalUserAccount(orgId = "org1", userId = "user1")
        val sdkManager = createTestSalesforceSDKManagerWithCurrentUser(user)

        // Act
        val rtrSection = sdkManager.devSupportInfo.additionalSections.first { it.first == "RTR" }

        // Assert: RTR inactive, no rotation yet.
        assertEquals("false", rtrSection.second.first { it.first == "RTR Active" }.second)
        assertEquals("Never", rtrSection.second.first { it.first == "Last Rotation" }.second)
    }

    @Test
    fun test_givenCurrentUserWithRTRRegistered_whenDevSupportInfo_thenRtrSectionShowsTrue() {
        /*
         * Arrange: a signed-in user for whom RTR has been detected and
         * registered (what ClientManager does on confirmed rotation).
         */
        val user = buildMinimalUserAccount(orgId = "org1", userId = "user1")
        val sdkManager = createTestSalesforceSDKManagerWithCurrentUser(user)
        sdkManager.registerUsedAppFeature(Features.FEATURE_RTR, user)

        try {
            // Act
            val rtrSection = sdkManager.devSupportInfo.additionalSections.first { it.first == "RTR" }

            /*
             * Assert: the getter's rtrActive branch reflects the per-user RT
             * flag.
             */
            assertEquals("true", rtrSection.second.first { it.first == "RTR Active" }.second)
        } finally {
            sdkManager.unregisterUsedAppFeature(Features.FEATURE_RTR, user)
        }
    }

    @Test
    fun test_givenNoUserArgAndNoCurrentUser_whenIsUserFeatureRegistered_thenFalse() {
        /*
         * Exercises isUserFeatureRegistered's default-argument path (no user
         * passed) and its no-current-user fallback — the branch the
         * devSupportInfo getter never reaches because it short-circuits on
         * currentUser == null before ever calling the helper.
         */
        val sdkManager = createTestSalesforceSDKManagerWithCurrentUser(null)

        assertFalse(
            "isUserFeatureRegistered must be false when there is no user to resolve",
            sdkManager.isUserFeatureRegistered(Features.FEATURE_RTR)
        )
    }

    // -------------------------------------------------------------------------
    // A-marker, TM, JT/OT, BN promotion tests
    // -------------------------------------------------------------------------

    @Test
    fun test_givenWebServerNonHybridFlow_whenOnAuthFlowSuccessPromotes_thenA1PerUserAndGlobalCleared() {
        val sdkManager = createSdkManagerWithMockedAccountManager()
        val user = buildMinimalUserAccount(orgId = "org1", userId = "user1")

        // Simulate: global A1 was set before auth completed
        sdkManager.registerUsedAppFeature(Features.FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID)

        // Promote: move global to per-user, clear global
        val allAMarkers = listOf(
            Features.FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID,
            Features.FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID,
            Features.FEATURE_AUTH_TYPE_USER_AGENT_NON_HYBRID,
            Features.FEATURE_AUTH_TYPE_USER_AGENT_HYBRID,
            Features.FEATURE_AUTH_TYPE_NATIVE,
        )
        val activeAMarker = allAMarkers.firstOrNull { sdkManager.isGlobalFeatureRegistered(it) }
        for (marker in allAMarkers) {
            sdkManager.unregisterUsedAppFeature(marker)
            if (marker == activeAMarker) sdkManager.registerUsedAppFeature(marker, user)
            else sdkManager.unregisterUsedAppFeature(marker, user)
        }

        val ua = sdkManager.getUserAgent("", user)
        assertTrue("A1 should appear in per-user UA", ua.contains(Features.FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID))
        assertFalse("A1 global should be cleared", sdkManager.isGlobalFeatureRegistered(Features.FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID))
        listOf(Features.FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID, Features.FEATURE_AUTH_TYPE_USER_AGENT_NON_HYBRID, Features.FEATURE_AUTH_TYPE_USER_AGENT_HYBRID, Features.FEATURE_AUTH_TYPE_NATIVE).forEach { marker ->
            assertFalse("$marker should not appear in per-user UA", ua.contains(marker))
        }
    }

    @Test
    fun test_givenWebServerHybridFlow_whenOnAuthFlowSuccessPromotes_thenA2PerUserAndGlobalCleared() {
        val sdkManager = createSdkManagerWithMockedAccountManager()
        val user = buildMinimalUserAccount(orgId = "org1", userId = "user1")

        sdkManager.registerUsedAppFeature(Features.FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID)

        val allAMarkers = listOf(
            Features.FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID,
            Features.FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID,
            Features.FEATURE_AUTH_TYPE_USER_AGENT_NON_HYBRID,
            Features.FEATURE_AUTH_TYPE_USER_AGENT_HYBRID,
            Features.FEATURE_AUTH_TYPE_NATIVE,
        )
        val activeAMarker = allAMarkers.firstOrNull { sdkManager.isGlobalFeatureRegistered(it) }
        for (marker in allAMarkers) {
            sdkManager.unregisterUsedAppFeature(marker)
            if (marker == activeAMarker) sdkManager.registerUsedAppFeature(marker, user)
            else sdkManager.unregisterUsedAppFeature(marker, user)
        }

        val ua = sdkManager.getUserAgent("", user)
        assertTrue("A2 should appear in per-user UA", ua.contains(Features.FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID))
        assertFalse("A2 global should be cleared", sdkManager.isGlobalFeatureRegistered(Features.FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID))
        listOf(Features.FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID, Features.FEATURE_AUTH_TYPE_USER_AGENT_NON_HYBRID, Features.FEATURE_AUTH_TYPE_USER_AGENT_HYBRID, Features.FEATURE_AUTH_TYPE_NATIVE).forEach { marker ->
            assertFalse("$marker should not appear in per-user UA", ua.contains(marker))
        }
    }

    @Test
    fun test_givenUserAgentFlow_whenOnAuthFlowSuccessPromotes_thenA3PerUserAndGlobalCleared() {
        val sdkManager = createSdkManagerWithMockedAccountManager()
        val user = buildMinimalUserAccount(orgId = "org1", userId = "user1")

        sdkManager.registerUsedAppFeature(Features.FEATURE_AUTH_TYPE_USER_AGENT_NON_HYBRID)

        val allAMarkers = listOf(
            Features.FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID,
            Features.FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID,
            Features.FEATURE_AUTH_TYPE_USER_AGENT_NON_HYBRID,
            Features.FEATURE_AUTH_TYPE_USER_AGENT_HYBRID,
            Features.FEATURE_AUTH_TYPE_NATIVE,
        )
        val activeAMarker = allAMarkers.firstOrNull { sdkManager.isGlobalFeatureRegistered(it) }
        for (marker in allAMarkers) {
            sdkManager.unregisterUsedAppFeature(marker)
            if (marker == activeAMarker) sdkManager.registerUsedAppFeature(marker, user)
            else sdkManager.unregisterUsedAppFeature(marker, user)
        }

        val ua = sdkManager.getUserAgent("", user)
        assertTrue("A3 should appear in per-user UA", ua.contains(Features.FEATURE_AUTH_TYPE_USER_AGENT_NON_HYBRID))
        assertFalse("A3 global should be cleared", sdkManager.isGlobalFeatureRegistered(Features.FEATURE_AUTH_TYPE_USER_AGENT_NON_HYBRID))
        listOf(Features.FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID, Features.FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID, Features.FEATURE_AUTH_TYPE_USER_AGENT_HYBRID, Features.FEATURE_AUTH_TYPE_NATIVE).forEach { marker ->
            assertFalse("$marker should not appear in per-user UA", ua.contains(marker))
        }
    }

    @Test
    fun test_givenTokenMigration_whenOnAuthFlowCompletes_thenTMRegisteredPerUser() {
        val sdkManager = createSdkManagerWithMockedAccountManager()
        val user = buildMinimalUserAccount(orgId = "org1", userId = "user1")

        // Simulate the tokenMigration branch: register TM per-user
        sdkManager.registerUsedAppFeature(Features.FEATURE_TOKEN_MIGRATION, user)

        try {
            val ua = sdkManager.getUserAgent("", user)
            assertTrue("TM should appear in per-user UA after migration", ua.contains(Features.FEATURE_TOKEN_MIGRATION))
        } finally {
            sdkManager.unregisterUsedAppFeature(Features.FEATURE_TOKEN_MIGRATION, user)
        }
    }

    @Test
    fun test_givenFullLogin_whenOnAuthFlowSucceeds_thenTMClearedPerUser() {
        val sdkManager = createSdkManagerWithMockedAccountManager()
        val user = buildMinimalUserAccount(orgId = "org1", userId = "user1")

        // Pre-condition: TM was registered from a prior migration
        sdkManager.registerUsedAppFeature(Features.FEATURE_TOKEN_MIGRATION, user)

        // Simulate full-login path: clear TM per-user
        sdkManager.unregisterUsedAppFeature(Features.FEATURE_TOKEN_MIGRATION, user)

        val ua = sdkManager.getUserAgent("", user)
        assertFalse("TM should be cleared after full login", ua.contains(Features.FEATURE_TOKEN_MIGRATION))
    }

    @Test
    fun test_givenJwtTokenFormat_whenOnAuthFlowSucceeds_thenJTRegisteredAndOTCleared() {
        val sdkManager = createSdkManagerWithMockedAccountManager()
        val user = buildMinimalUserAccount(orgId = "org1", userId = "user1")

        // Simulate JWT token format
        sdkManager.registerUsedAppFeature(Features.FEATURE_TOKEN_FORMAT_JWT, user)
        sdkManager.unregisterUsedAppFeature(Features.FEATURE_TOKEN_FORMAT_OPAQUE, user)

        try {
            val ua = sdkManager.getUserAgent("", user)
            assertTrue("JT should appear in per-user UA for JWT token format", ua.contains(Features.FEATURE_TOKEN_FORMAT_JWT))
            assertFalse("OT should not appear in per-user UA for JWT token format", ua.contains(Features.FEATURE_TOKEN_FORMAT_OPAQUE))
        } finally {
            sdkManager.unregisterUsedAppFeature(Features.FEATURE_TOKEN_FORMAT_JWT, user)
        }
    }

    @Test
    fun test_givenOpaqueTokenFormat_whenOnAuthFlowSucceeds_thenOTRegisteredAndJTCleared() {
        val sdkManager = createSdkManagerWithMockedAccountManager()
        val user = buildMinimalUserAccount(orgId = "org1", userId = "user1")

        // Simulate opaque token format
        sdkManager.registerUsedAppFeature(Features.FEATURE_TOKEN_FORMAT_OPAQUE, user)
        sdkManager.unregisterUsedAppFeature(Features.FEATURE_TOKEN_FORMAT_JWT, user)

        try {
            val ua = sdkManager.getUserAgent("", user)
            assertTrue("OT should appear in per-user UA for opaque token format", ua.contains(Features.FEATURE_TOKEN_FORMAT_OPAQUE))
            assertFalse("JT should not appear in per-user UA for opaque token format", ua.contains(Features.FEATURE_TOKEN_FORMAT_JWT))
        } finally {
            sdkManager.unregisterUsedAppFeature(Features.FEATURE_TOKEN_FORMAT_OPAQUE, user)
        }
    }

    @Test
    fun test_givenBeaconConsumerKey_whenOnAuthFlowSucceeds_thenBNRegistered() {
        val sdkManager = createSdkManagerWithMockedAccountManager()
        val user = buildMinimalUserAccount(orgId = "org1", userId = "user1")

        // Simulate beacon child app
        sdkManager.registerUsedAppFeature(Features.FEATURE_BEACON, user)

        try {
            val ua = sdkManager.getUserAgent("", user)
            assertTrue("BN should appear in per-user UA when beacon child consumer key is set", ua.contains(Features.FEATURE_BEACON))
        } finally {
            sdkManager.unregisterUsedAppFeature(Features.FEATURE_BEACON, user)
        }
    }

    @Test
    fun test_givenExplicitUserWithoutRTR_whenIsUserFeatureRegistered_thenFalse() {
        /*
         * A user with no registered features returns false — the negative
         * per-user lookup branch, complementing the true branch covered via
         * the devSupportInfo getter test.
         */
        val user = buildMinimalUserAccount(orgId = "org1", userId = "user1")
        val sdkManager = createSdkManagerWithMockedAccountManager()

        assertFalse(
            "isUserFeatureRegistered must be false for a user with no registered features",
            sdkManager.isUserFeatureRegistered(Features.FEATURE_RTR, user)
        )
    }

    /*
     * Force Token Refresh dev-action tests
     *
     * forceTokenRefresh is the testable core of the debug-only "Force Token
     * Refresh" dev action; the dev-action lambda only dispatches it onto a
     * coroutine and shows the returned message in a Toast.
     */

    @Test
    fun test_givenRefreshSucceeds_whenForceTokenRefresh_thenReturnsSuccessMessage() {
        // Arrange: a RestClient that refreshes without error.
        val user = mockk<UserAccount>(relaxed = true)
        val restClient = mockk<RestClient>(relaxed = true).apply {
            every { refreshAccessToken() } just runs
        }

        // Act
        val message = SalesforceSDKManager.getInstance().forceTokenRefresh(user, restClient)

        // Assert
        assertTrue(
            "Success message should direct the developer to the RTR section",
            message.contains("check RTR section")
        )
    }

    @Test
    fun test_givenRefreshThrows_whenForceTokenRefresh_thenReturnsFailureMessageWithoutThrowing() {
        /*
         * Arrange: a RestClient whose refresh fails with an IOException
         * carrying a message.
         */
        val user = mockk<UserAccount>(relaxed = true)
        val restClient = mockk<RestClient>(relaxed = true).apply {
            every { refreshAccessToken() } throws IOException("network down")
        }

        /*
         * Act: must not throw — the failure is caught and surfaced as a
         * message.
         */
        val message = SalesforceSDKManager.getInstance().forceTokenRefresh(user, restClient)

        /*
         * Assert: message reports the failure and includes the exception
         * detail.
         */
        assertTrue("Failure message should say the refresh failed", message.contains("failed"))
        assertTrue("Failure message should include the exception detail", message.contains("network down"))
    }

    @Test
    fun test_givenRestClientResolutionThrows_whenForceTokenRefresh_thenReturnsFailureMessageWithoutThrowing() {
        /*
         * Arrange: no restClient argument, so forceTokenRefresh must construct
         * a manager for the supplied user. A user with no backing account
         * produces no client.
         */
        val user = mockk<UserAccount>(relaxed = true)

        /*
         * Act: exercise the production default-argument path. It must not throw
         * — the failure is caught and surfaced as a message.
         */
        val message = SalesforceSDKManager.getInstance().forceTokenRefresh(user)

        // Assert: the failure is reported rather than propagated.
        assertTrue("Failure message should say the refresh failed", message.contains("failed"))
    }

    // -------------------------------------------------------------------------
    // App Attestation (AA) feature flag tests
    // -------------------------------------------------------------------------

    @Test
    fun test_givenAppAttestationGlobalFlagSet_whenOnAuthFlowSuccessPromotes_thenAAFlagPerUserAndGlobalCleared() {
        val sdkManager = createSdkManagerWithMockedAccountManager()
        val userA = buildMinimalUserAccount(orgId = "org1", userId = "user1")

        // Arrange: transient global AA set by OAuth2.java / NativeLoginManager.kt during token exchange
        sdkManager.registerUsedAppFeature(Features.FEATURE_APP_ATTESTATION)
        assertTrue(
            "Precondition: global AA should be set before promotion",
            sdkManager.isGlobalFeatureRegistered(Features.FEATURE_APP_ATTESTATION)
        )

        // Act: simulate onAuthFlowSuccess promotion block (non-refresh path)
        val usedAppAttestation = sdkManager.isGlobalFeatureRegistered(Features.FEATURE_APP_ATTESTATION)
        sdkManager.unregisterUsedAppFeature(Features.FEATURE_APP_ATTESTATION) // always clear global
        if (usedAppAttestation) {
            sdkManager.registerUsedAppFeature(Features.FEATURE_APP_ATTESTATION, userA)
        } else {
            sdkManager.unregisterUsedAppFeature(Features.FEATURE_APP_ATTESTATION, userA)
        }

        try {
            // Assert: AA in per-user user agent
            val agent = sdkManager.getUserAgent("", userA)
            assertTrue("User agent should contain AA flag after promotion", agent.contains(Features.FEATURE_APP_ATTESTATION))

            // Assert: global AA cleared
            assertFalse(
                "Global AA should be cleared after promotion",
                sdkManager.isGlobalFeatureRegistered(Features.FEATURE_APP_ATTESTATION)
            )
        } finally {
            sdkManager.unregisterUsedAppFeature(Features.FEATURE_APP_ATTESTATION, userA)
        }
    }

    @Test
    fun test_givenNoAppAttestation_whenOnAuthFlowSuccessRuns_thenAAFlagAbsentFromUserAgent() {
        val sdkManager = createSdkManagerWithMockedAccountManager()
        val userA = buildMinimalUserAccount(orgId = "org1", userId = "user1")

        // Arrange: no global AA (attestation not available or not used)
        assertFalse(
            "Precondition: global AA should not be set",
            sdkManager.isGlobalFeatureRegistered(Features.FEATURE_APP_ATTESTATION)
        )

        // Act: simulate onAuthFlowSuccess promotion block when no attestation
        val usedAppAttestation = sdkManager.isGlobalFeatureRegistered(Features.FEATURE_APP_ATTESTATION)
        sdkManager.unregisterUsedAppFeature(Features.FEATURE_APP_ATTESTATION) // always clear global (no-op)
        if (usedAppAttestation) {
            sdkManager.registerUsedAppFeature(Features.FEATURE_APP_ATTESTATION, userA)
        } else {
            sdkManager.unregisterUsedAppFeature(Features.FEATURE_APP_ATTESTATION, userA)
        }

        // Assert: AA absent from user agent
        val agent = sdkManager.getUserAgent("", userA)
        assertFalse("User agent should NOT contain AA when attestation was not used", agent.contains(Features.FEATURE_APP_ATTESTATION))
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

    private fun verifyPersistedCredentialsCleared(fixture: LogoutFixture) {
        verify(exactly = 1) {
            fixture.accountManager.clearPassword(fixture.account)
            fixture.accountManager.setAuthToken(
                fixture.account,
                AccountManager.KEY_AUTHTOKEN,
                null,
            )
        }
        listOf(
            AccountManager.KEY_AUTHTOKEN,
            KEY_LIGHTNING_SID,
            KEY_VF_SID,
            KEY_CONTENT_SID,
            KEY_CSRF_TOKEN,
            KEY_COOKIE_SID_CLIENT,
            KEY_COOKIE_CLIENT_SRC,
            KEY_SID_COOKIE_NAME,
            KEY_PARENT_SID,
            KEY_BEACON_CHILD_CONSUMER_KEY,
            KEY_BEACON_CHILD_CONSUMER_SECRET,
            KEY_CREDENTIALS_IDENTIFIER,
        ).forEach { key ->
            verify(exactly = 1) {
                fixture.accountManager.setUserData(fixture.account, key, null)
            }
        }
    }

    private fun createLogoutFixture(
        pushRegistered: Boolean = false,
        malformed: Boolean = false,
        accountPresent: Boolean = true,
        otherUserMalformed: Boolean = false,
        cleanupIdentityAvailable: Boolean = true,
        refreshToken: String? = "refresh-token-user",
        loginServer: String? = "https://login.example.com",
        removeAccountSucceeds: Boolean = true,
    ): LogoutFixture {
        val account = Account("logout-account", "logout-account-type")
        val otherAccount = Account("other-account", "logout-account-type")
        val user = buildLogoutIdentity(
            accountName = account.name,
            userId = "user",
            orgId = "org",
            refreshToken = refreshToken,
            loginServer = loginServer,
        )
        val otherUser = buildLogoutIdentity(
            accountName = otherAccount.name,
            userId = "other-user",
            orgId = "other-org",
        )
        val removalCompleted = CountDownLatch(1)
        val revocationCompleted = CountDownLatch(1)
        val accountStillPresent = AtomicBoolean(accountPresent)
        val credentialsPresent = AtomicBoolean(true)
        val userAccountManager = mockk<UserAccountManager>(relaxed = true).apply {
            every { buildUserAccount(account) } returns if (malformed) null else user
            every { buildUserAccount(otherAccount) } returns otherUser
            every { storedUserId } returns if (cleanupIdentityAvailable) user.userId else null
            every { storedOrgId } returns if (cleanupIdentityAvailable) user.orgId else null
            every { authenticatedUsers } answers {
                buildList {
                    if (accountStillPresent.get() && credentialsPresent.get() && !malformed) {
                        add(user)
                    }
                    if (!otherUserMalformed) {
                        add(otherUser)
                    }
                }
            }
        }
        val accountManager = mockk<AccountManager>(relaxed = true).apply {
            every { getPassword(account) } returns "encrypted-refresh-value"
            every { getUserData(account, any()) } returns "encrypted-login-value"
            every { getUserData(account, KEY_USER_ID) } returns
                    if (cleanupIdentityAvailable) "encrypted-persisted-user-id" else null
            every { getUserData(account, KEY_ORG_ID) } returns
                    if (cleanupIdentityAvailable) "encrypted-persisted-org-id" else null
            every { getAccountsByType(any()) } answers {
                if (accountStillPresent.get()) {
                    arrayOf(account, otherAccount)
                } else {
                    arrayOf(otherAccount)
                }
            }
            every { removeAccountExplicitly(account) } answers {
                if (removeAccountSucceeds) {
                    accountStillPresent.set(false)
                    removalCompleted.countDown()
                }
                removeAccountSucceeds
            }
            every {
                setUserData(account, AccountManager.KEY_AUTHTOKEN, null)
            } answers {
                credentialsPresent.set(false)
                Unit
            }
        }

        mockkStatic(AccountManager::class)
        every { AccountManager.get(any()) } returns accountManager
        mockkStatic(UserAccountManager::class)
        every { UserAccountManager.getInstance() } returns userAccountManager
        mockkObject(SalesforceSDKManager.Companion)
        every { SalesforceSDKManager.encryptionKey } returns "fixture-encryption-key"
        every { SalesforceSDKManager.decrypt("encrypted-refresh-value", any()) } returns "opaque-refresh-value"
        every { SalesforceSDKManager.decrypt("encrypted-login-value", any()) } returns "https://example.invalid"
        every {
            SalesforceSDKManager.decrypt("encrypted-persisted-user-id", any())
        } returns user.userId
        every {
            SalesforceSDKManager.decrypt("encrypted-persisted-org-id", any())
        } returns user.orgId
        // Initialize the Kotlin object before MockK retransforms its @JvmStatic methods.
        PushMessaging.hashCode()
        mockkObject(PushMessaging)
        mockkStatic(PushMessaging::class)
        every { PushMessaging.isRegistered(any(), user) } returns pushRegistered
        every { PushMessaging.unregisterForLogout(any(), user, false) } just runs
        // OAuth2 also has a static initializer that must run before MockK retransformation.
        OAuth2.TIMESTAMP_FORMAT
        mockkStatic(OAuth2::class)
        every { OAuth2.revokeRefreshToken(any(), any(), any(), any()) } answers {
            revocationCompleted.countDown()
        }
        mockkStatic(CookieManager::class)
        every { CookieManager.getInstance() } returns mockk(relaxed = true)

        val sdkManager = LogoutTestSalesforceSDKManager(
            context = getInstrumentation().targetContext,
            testUserAccountManager = userAccountManager,
        )
        every { SalesforceSDKManager.getInstance() } returns sdkManager

        return LogoutFixture(
            sdkManager = sdkManager,
            account = account,
            accountManager = accountManager,
            userAccountManager = userAccountManager,
            user = user,
            otherUser = otherUser,
            removalCompleted = removalCompleted,
            revocationCompleted = revocationCompleted,
        )
    }

    private fun buildLogoutIdentity(
        accountName: String,
        userId: String,
        orgId: String,
        refreshToken: String? = "refresh-token-$userId",
        loginServer: String? = "https://login.example.com",
    ): UserAccount = UserAccountBuilder.getInstance()
        .accountName(accountName)
        .userId(userId)
        .orgId(orgId)
        .authToken("auth-token-$userId")
        .refreshToken(refreshToken)
        .instanceServer("https://instance.example.com")
        .loginServer(loginServer)
        .idUrl("https://id.example.com/$orgId/$userId")
        .build()

    private data class LogoutFixture(
        val sdkManager: LogoutTestSalesforceSDKManager,
        val account: Account,
        val accountManager: AccountManager,
        val userAccountManager: UserAccountManager,
        val user: UserAccount,
        val otherUser: UserAccount,
        val removalCompleted: CountDownLatch,
        val revocationCompleted: CountDownLatch,
    )

    private class LogoutTestSalesforceSDKManager(
        context: android.content.Context,
        private val testUserAccountManager: UserAccountManager,
    ) : SalesforceSDKManager(context, LoginActivity::class.java, LoginActivity::class.java) {

        val cleanedUsers = CopyOnWriteArrayList<UserAccount?>()
        var onCleanUp: (() -> Unit)? = null
        var loginPageStarted = false

        override val userAccountManager: UserAccountManager by lazy { testUserAccountManager }

        override fun cleanUp(userAccount: UserAccount?) {
            onCleanUp?.invoke()
            cleanedUsers += userAccount
        }

        override fun startLoginPage() {
            loginPageStarted = true
        }
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
                // currentUser returns null → getUserAgent falls back to no per-user key
                every { currentUser } returns null
                // No authenticated users → hydratePerUserFeatures is a no-op
                every { authenticatedUsers } returns null
            }
        }
    }
}
