package com.salesforce.androidsdk.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.OAuthConfig
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TokenMigrationWebViewTest {

    val mockOAuthConfig = OAuthConfig(
        consumerKey = "test_consumer_key",
        redirectUri = "testapp://oauth/callback",
        scopes = listOf("api", "refresh_token"),
    )

    private lateinit var savedFactory: ViewModelProvider.Factory
    private lateinit var mockViewModel: LoginViewModel
    private lateinit var mockWebView: WebView
    private var testActivity: TokenMigrationActivity? = null


    @Before
    fun setUp() {
        // Save the real loginViewModelFactory so it can be restored in tearDown
        savedFactory = SalesforceSDKManager.getInstance().loginViewModelFactory

        // Mock loginViewModelFactory to return a mock LoginViewModel
        mockViewModel = mockk<LoginViewModel>(relaxed = true) {
            every { dynamicBackgroundColor } returns mutableStateOf(Color.White)
            every { loading } returns mutableStateOf(false)
            every { authFinished } returns mutableStateOf(false)
            every { loadingIndicator } returns null
            every { oAuthConfig } returns mockOAuthConfig
            every { useWebServerFlow } returns true
        }

        // Wire up the factory so the activity's `by viewModels` delegate returns mockViewModel
        SalesforceSDKManager.getInstance().loginViewModelFactory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T = mockViewModel as T
            }

        TokenMigrationActivity.webViewFactory = { context -> TestWebView(context) }

        mockWebView = mockk(relaxed = true) {
            every { loadUrl(any()) } just runs
            every { parent } returns null
        }
    }

    @After
    fun tearDown() {
        testActivity?.let { activity ->
            if (!activity.isFinishing && !activity.isDestroyed) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    activity.finish()
                }
            }
        }
        testActivity = null
        // Restore the real loginViewModelFactory
        SalesforceSDKManager.getInstance().loginViewModelFactory = savedFactory
        unmockkAll()  // cleans up any mockk() instances created in tests
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
        val loadingState: MutableState<Boolean> = mockk(relaxed = true)
        val authFinishedState: MutableState<Boolean> = mockk(relaxed = true)
        every { mockViewModel.loading } returns loadingState
        every { mockViewModel.authFinished } returns authFinishedState

        val activity = launchActivity()
        val mockResultCallback = createMockResultCallback()
        val clientManager = activity.TokenMigrationClientManager(mockResultCallback, "https://test.salesforce.com")

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            clientManager.onPageStarted(mockWebView, "https://test.salesforce.com/page", null)
        }

        verify { loadingState.value = true }
        verify(exactly = 0) { authFinishedState.value = true }
    }

    @Test
    fun onPageFinished_setsBackgroundColor() {
        val purpleColor = "rgb(128, 0, 128)"
        val loadingState: MutableState<Boolean> = mockk(relaxed = true)
        val backgroundColor: MutableState<Color> = mockk(relaxed = true)
        every { mockViewModel.loading } returns loadingState
        every { mockViewModel.authFinished } returns mutableStateOf(false)
        every { mockViewModel.dynamicBackgroundColor } returns backgroundColor
        every { mockWebView.evaluateJavascript(any(), any()) } answers {
            secondArg<android.webkit.ValueCallback<String>>().onReceiveValue(purpleColor)
        }

        val activity = launchActivity()
        val mockResultCallback = createMockResultCallback()
        val clientManager = activity.TokenMigrationClientManager(mockResultCallback, "https://test.salesforce.com")

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            clientManager.onPageFinished(mockWebView, "https://test.salesforce.com/page")
        }

        verify { backgroundColor.value = Color(128, 0, 128) }
        verify { loadingState.value = false }
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
                    "User agent should contain SDK user agent",
                    webView.settings.userAgentString?.contains(
                        SalesforceSDKManager.getInstance().userAgent
                    ) == true,
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
     * Launches [TokenMigrationActivity] with intent extras.  [UserAccountManager]
     * returns null for any user lookup, so the activity finishes in [onCreate]
     * before reaching [clientManager.peekRestClient] or the lifecycleScope
     * coroutine.  This avoids Compose rendering and coroutine-related hangs entirely.
     *
     * Uses [Application.ActivityLifecycleCallbacks] to capture the activity
     * instance because [ActivityScenario.onActivity] cannot be used on
     * activities that have already reached the DESTROYED state.
     */
    private fun launchActivity(): TokenMigrationActivity {
        val callbackKey = MigrationCallbackRegistry.register(
            MigrationCallbackRegistry.MigrationCallbacks(
                onMigrationSuccess = { },
                onMigrationError = { _, _, _ -> },
            )
        )
        val intent = Intent(getApplicationContext(), TokenMigrationActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(TokenMigrationActivity.EXTRA_CALLBACK_ID, callbackKey)
            putExtra(TokenMigrationActivity.EXTRA_OAUTH_CONFIG, mockOAuthConfig)
            putExtra(TokenMigrationActivity.EXTRA_ORG_ID, VALID_ORG)
            putExtra(TokenMigrationActivity.EXTRA_USER_ID, VALID_USER)
        }

        var capturedActivity: TokenMigrationActivity? = null
        val latch = CountDownLatch(1)
        val app = getApplicationContext<Application>()

        val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is TokenMigrationActivity) {
                    capturedActivity = activity
                    latch.countDown()
                }
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        }

        app.registerActivityLifecycleCallbacks(lifecycleCallbacks)
        getApplicationContext<Context>().startActivity(intent)
        latch.await(10, TimeUnit.SECONDS)
        app.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)

        testActivity = capturedActivity
            ?: throw RuntimeException("TokenMigrationActivity was not created within timeout")
        return testActivity!!
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