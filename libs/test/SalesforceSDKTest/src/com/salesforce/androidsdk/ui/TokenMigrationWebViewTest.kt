package com.salesforce.androidsdk.ui

import android.content.Context
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.MigrationCallbackRegistry
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.analytics.AnalyticsPublishingWorker
import com.salesforce.androidsdk.analytics.SalesforceAnalyticsManager
import com.salesforce.androidsdk.analytics.logger.SalesforceLogger
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.OAuthConfig
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.rest.RestClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TokenMigrationWebViewTest {

    val mockOAuthConfig = OAuthConfig(
        consumerKey = "test_consumer_key",
        redirectUri = "testapp://oauth/callback",
        scopes = listOf("api", "refresh_token"),
    )

    private lateinit var mockUserAccountManager: UserAccountManager
    private lateinit var mockSdkManager: SalesforceSDKManager
    private lateinit var mockClientManager: ClientManager
    private lateinit var mockRestClient: RestClient
    private lateinit var mockUser: UserAccount
    private lateinit var mockViewModel: LoginViewModel
    private lateinit var mockColorState: MutableState<Color>
    private lateinit var mockWebView: WebView


    @Before
    fun setUp() {
        // Mock SalesforceLogger.getLogger to prevent readLoggerPrefs from being called
        val mockLogger: SalesforceLogger = mockk(relaxed = true)
        mockkStatic(SalesforceLogger::class)
        every { SalesforceLogger.getLogger(any(), any()) } returns mockLogger
        every { SalesforceLogger.getLogger(any(), any(), any()) } returns mockLogger

        // Reset logger prefs as backup
        SalesforceLogger.flushComponents()
        SalesforceLogger.resetLoggerPrefs(getApplicationContext())

        mockUserAccountManager = mockk(relaxed = true)
        mockSdkManager = mockk(relaxed = true)
        mockClientManager = mockk(relaxed = true)
        mockRestClient = mockk(relaxed = true)
        mockUser = mockk(relaxed = true)

        // Mock user properties needed for getAuthorizationUrl
        every { mockUser.instanceServer } returns "https://test.salesforce.com"

        mockkStatic(UserAccountManager::class)
        every { UserAccountManager.getInstance() } returns mockUserAccountManager
        every {
            mockUserAccountManager.getUserFromOrgAndUserId(VALID_ORG, VALID_USER)
        } returns mockUser
        every {
            mockUserAccountManager.getUserFromOrgAndUserId(INVALID_ORG, INVALID_USER)
        } returns null

        mockkObject(SalesforceSDKManager)
        mockkObject(SalesforceSDKManager.Companion)
        every { SalesforceSDKManager.getInstance() } returns mockSdkManager
        every { mockSdkManager.appContext } returns getApplicationContext()
        every { mockSdkManager.clientManager.peekRestClient(any<UserAccount>()) } returns mockRestClient
        every { mockSdkManager.useHybridAuthentication } returns false
        every { mockSdkManager.userAgent } returns "MockUserAgent"
        every { mockSdkManager.isBrowserLoginEnabled } returns false
        every { mockSdkManager.useWebServerAuthentication } returns false

        mockColorState = mockk(relaxed = true)
        every { mockColorState.value } returns Color.White

        // Mock loginViewModelFactory to return a new mock LoginViewModel each time
        mockViewModel = mockk<LoginViewModel>(relaxed = true) {
            coEvery {
                getAuthorizationUrl(any(), any(), any(), any())
            } returns "https://test.salesforce.com/authorize"
            every { dynamicBackgroundColor } returns mockColorState
            every { authFinished } returns mutableStateOf(false)
            every { loadingIndicator } returns null
            every { oAuthConfig } returns mockOAuthConfig
            every { useWebServerFlow } returns true
        }

        // Wire up the factory so the activity's `by viewModels` delegate returns mockViewModel
        every { mockSdkManager.loginViewModelFactory } returns object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return mockViewModel as T
            }
        }

        // Default mock for sendSync to prevent hanging - tests can override this
        val mockResponse = mockk<com.salesforce.androidsdk.rest.RestResponse>(relaxed = true)
        every { mockResponse.isSuccess } returns true
        every { mockResponse.asString() } returns """{"frontdoor_uri": "https://test.salesforce.com/frontdoor"}"""
        every { mockRestClient.sendSync(any()) } returns mockResponse

        // Mock AnalyticsPublishingWorker to prevent NPE during activity lifecycle
        mockkObject(AnalyticsPublishingWorker.Companion)
        every {
            AnalyticsPublishingWorker.enqueueAnalyticsPublishWorkRequest(any(), any())
        } returns UUID.randomUUID()

        SalesforceAnalyticsManager.setAnalyticsPublishingType(SalesforceAnalyticsManager.SalesforceAnalyticsPublishingType.PublishDisabled)

        TokenMigrationActivity.webViewFactory = { context -> TestWebView(context) }

        mockWebView = mockk(relaxed = true) {
            every { loadUrl(any()) } just runs
            every { parent } returns null
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * A WebView subclass that disables network loading and allows state verification.
     */
    open class TestWebView(context: Context) : WebView(context) {
        var onLoadUrlCallback: (() -> Unit)? = null

        override fun loadUrl(url: String) {
            // No-op to prevent network loading
            onLoadUrlCallback?.invoke()
        }

        override fun loadUrl(url: String, additionalHttpHeaders: MutableMap<String, String>) {
            // No-op
            onLoadUrlCallback?.invoke()
        }
    }

    // region shouldOverrideUrlLoading Tests

    @Test
    fun shouldOverrideUrlLoading_returnsFalseForNonCallbackUrl() {
        val activity = launchActivity()
        val mockResultCallback = createMockResultCallback()
        val clientManager = activity.TokenMigrationClientManager(mockResultCallback, "https://test.salesforce.com")
        val mockRequest = createMockWebResourceRequest("https://login.salesforce.com/somepage")

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertFalse(
                "Non-callback URL should not be overridden",
                clientManager.shouldOverrideUrlLoading(mockWebView, mockRequest)
            )

            verify(exactly = 0) {
                mockViewModel.onWebServerFlowComplete(any(), any(), any(), any(), any())
            }
            coVerify(exactly = 0) {
                mockViewModel.onAuthFlowComplete(any(), any(), any(), any(), any())
            }
        }
    }

    @Test
    fun shouldOverrideUrlLoading_returnsTrueForCallbackUrl_webServerFlow() {
        val activity = launchActivity()
        val mockResultCallback = createMockResultCallback()
        val clientManager = activity.TokenMigrationClientManager(mockResultCallback, "instanceServer")

        every { mockViewModel.useWebServerFlow } returns true

        val mockRequest = createMockWebResourceRequest("testapp://oauth/callback?code=test_code")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertTrue(
                "Callback URL should be overridden because migration is finished",
                clientManager.shouldOverrideUrlLoading(mockWebView, mockRequest)
            )

            verify {
                mockViewModel.onWebServerFlowComplete(
                    code = "test_code",
                    onAuthFlowSuccess = mockResultCallback.onMigrationSuccess,
                    onAuthFlowError = mockResultCallback.onMigrationError,
                    loginServer = "instanceServer",
                    tokenMigration = true,
                )
            }

            coVerify(exactly = 0) {
                mockViewModel.onAuthFlowComplete(any(), any(), any(), any(), any())
            }
        }
    }

    @Test
    fun shouldOverrideUrlLoading_returnsTrueForCallbackUrl_userAgentFlow() {
        val activity = launchActivity()
        val mockResultCallback = createMockResultCallback()
        val clientManager = activity.TokenMigrationClientManager(mockResultCallback, "instanceServer")

        every { mockViewModel.useWebServerFlow } returns false

        val mockRequest = createMockWebResourceRequest("testapp://oauth/callback?code=test_code")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertTrue(
                "Callback URL should be overridden because migration is finished",
                clientManager.shouldOverrideUrlLoading(mockWebView, mockRequest)
            )
            coVerify {
                mockViewModel.onAuthFlowComplete(
                    tr = any(),
                    onAuthFlowSuccess = mockResultCallback.onMigrationSuccess,
                    onAuthFlowError = mockResultCallback.onMigrationError,
                    tokenMigration = true,
                )
            }

            verify(exactly = 0) {
                mockViewModel.onWebServerFlowComplete(any(), any(), any(), any(), any())
            }
        }
    }

    @Test
    fun shouldOverrideUrlLoading_callsErrorCallbackOnError() {
        val activity = launchActivity()
        val mockResultCallback = createMockResultCallback()
        val clientManager = activity.TokenMigrationClientManager(mockResultCallback, "https://test.salesforce.com")

        val mockRequest = createMockWebResourceRequest(
            "testapp://oauth/callback?error=access_denied&error_description=User%20denied%20access"
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            clientManager.shouldOverrideUrlLoading(mockWebView, mockRequest)
        }

        verify {
            mockResultCallback.onMigrationError("access_denied", "User denied access", null)
        }


    }

    @Test
    fun shouldOverrideUrlLoading_normalizesTripleSlashes() {
        every { mockViewModel.oAuthConfig } returns OAuthConfig(
            consumerKey = "test",
            redirectUri = "testapp:///oauth/callback",
            scopes = listOf("api"),
        )

        val activity = launchActivity()
        val mockResultCallback = createMockResultCallback()
        val clientManager = activity.TokenMigrationClientManager(mockResultCallback, "instanceServer")

        // URL with triple slash should still match after normalization
        val mockRequest = createMockWebResourceRequest("testapp:///oauth/callback?code=test_code")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertTrue(
                "Triple-slash URL should match after normalization",
                clientManager.shouldOverrideUrlLoading(mockWebView, mockRequest)
            )
            verify {
                mockViewModel.onWebServerFlowComplete(
                    code = "test_code",
                    onAuthFlowSuccess = mockResultCallback.onMigrationSuccess,
                    onAuthFlowError = mockResultCallback.onMigrationError,
                    loginServer = "instanceServer",
                    tokenMigration = true,
                )
            }

            coVerify(exactly = 0) {
                mockViewModel.onAuthFlowComplete(any(), any(), any(), any(), any())
            }
        }
    }

    // endregion

    // region onPageStarted / onPageFinished Tests

    @Test
    fun onPageStarted_setsLoadingTrue() {
        val activity = launchActivity()
        val mockResultCallback = createMockResultCallback()
        val clientManager = activity.TokenMigrationClientManager(mockResultCallback, "https://test.salesforce.com")

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            clientManager.onPageStarted(mockWebView, "https://test.salesforce.com/page", null)
        }

        verify { mockViewModel.loading.value = true }
        verify(exactly = 0) { mockViewModel.authFinished.value = true }
    }

    // endregion

    // region buildAuthWebview Tests

    @Test
    fun buildAuthWebview_enablesJavaScript() {
        val activity = launchActivity()
        val resultCallback = createMockResultCallback()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                val webView = activity.buildAuthWebview(
                    frontDoorUrl = "https://test.salesforce.com/frontdoor",
                    resultCallback = resultCallback,
                    instanceServer = "https://test.salesforce.com",
                )
                assertTrue("JavaScript should be enabled", webView.settings.javaScriptEnabled)
            } catch (e: Throwable) {
                Assert.fail(e.stackTraceToString())
            }
        }
    }

    @Test
    fun buildAuthWebview_setsUserAgent() {
        val activity = launchActivity()
        val resultCallback = createMockResultCallback()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                val webView = activity.buildAuthWebview(
                    frontDoorUrl = "https://test.salesforce.com/frontdoor",
                    resultCallback = resultCallback,
                    instanceServer = "https://test.salesforce.com",
                )
                assertTrue(
                    "User agent should contain MockUserAgent",
                    webView.settings.userAgentString?.contains("MockUserAgent") == true,
                )
            } catch (e: Throwable) {
                Assert.fail(e.stackTraceToString())
            }
        }
    }

    @Test
    fun buildAuthWebview_loadsProvidedUrl() {
        val activity = launchActivity()
        val resultCallback = createMockResultCallback()
        var loadedUrl: String? = null

        TokenMigrationActivity.webViewFactory = { context ->
            object : TestWebView(context) {
                override fun loadUrl(url: String) {
                    loadedUrl = url
                }
            }
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                activity.buildAuthWebview(
                    frontDoorUrl = "https://test.salesforce.com/frontdoor?sid=abc",
                    resultCallback = resultCallback,
                    instanceServer = "https://test.salesforce.com",
                )
            } catch (e: Throwable) {
                Assert.fail(e.stackTraceToString())
            }
        }

        assertEquals(
            "buildAuthWebview should load the provided frontDoorUrl",
            "https://test.salesforce.com/frontdoor?sid=abc",
            loadedUrl,
        )
    }

    @Test
    fun buildAuthWebview_assignsTokenMigrationClientManager() {
        val activity = launchActivity()
        val resultCallback = createMockResultCallback()

        TokenMigrationActivity.webViewFactory = { context ->
            TestWebView(context).also { it.onLoadUrlCallback = { } }
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                val webView = activity.buildAuthWebview(
                    frontDoorUrl = "https://test.salesforce.com/frontdoor",
                    resultCallback = resultCallback,
                    instanceServer = "https://test.salesforce.com",
                )
                assertTrue(
                    "WebViewClient should be TokenMigrationClientManager",
                    webView.webViewClient is TokenMigrationActivity.TokenMigrationClientManager,
                )
            } catch (e: Throwable) {
                Assert.fail(e.stackTraceToString())
            }
        }
    }

    // endregion

    // region Helpers

    /**
     * Launches a bare [TokenMigrationActivity] via [startActivitySync] (instrumentation thread)
     * and returns the activity instance.  The activity's [onCreate] will exit early because no
     * intent extras are provided, but the instance is still usable for direct method calls.
     */
    private fun launchActivity(): TokenMigrationActivity {
        val intent = Intent(getApplicationContext(), TokenMigrationActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return InstrumentationRegistry.getInstrumentation()
            .startActivitySync(intent) as TokenMigrationActivity
    }

    private fun createMockResultCallback(): MigrationCallbackRegistry.MigrationCallbacks =
        mockk {
            every { onMigrationSuccess(any()) } just runs
            every { onMigrationError(any(), any(), any()) } just runs
        }

    private fun createMockWebResourceRequest(url: String): WebResourceRequest =
        mockk {
            every { this@mockk.url } returns url.toUri()
        }

    // endregion
}