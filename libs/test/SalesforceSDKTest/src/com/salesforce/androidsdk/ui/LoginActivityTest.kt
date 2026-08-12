/*
 * Copyright (c) 2025-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.ui

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.compose.ui.graphics.Color
import androidx.core.net.toUri
import androidx.lifecycle.MediatorLiveData
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestClient.OAuthRefreshInterceptor
import com.salesforce.androidsdk.ui.LoginActivity.Companion.ABOUT_BLANK
import com.salesforce.androidsdk.ui.LoginActivity.Companion.AUTH_TRIGGER_FORCE_ADVANCED_AUTH
import com.salesforce.androidsdk.ui.LoginActivity.Companion.AUTH_TRIGGER_LOGIN_FOR_ADMIN
import com.salesforce.androidsdk.ui.LoginActivity.Companion.AUTH_TRIGGER_ORG_CONFIG
import com.salesforce.androidsdk.ui.LoginActivity.Companion.EXTRA_KEY_LOGIN_HINT
import com.salesforce.androidsdk.ui.LoginActivity.Companion.EXTRA_KEY_LOGIN_HOST
import com.salesforce.androidsdk.ui.LoginActivity.Companion.SALESFORCE_WELCOME_DISCOVERY_MOBILE_URL_QUERY_PARAMETER_KEY_CALLBACK_URL
import com.salesforce.androidsdk.ui.LoginActivity.Companion.SALESFORCE_WELCOME_DISCOVERY_MOBILE_URL_QUERY_PARAMETER_KEY_CLIENT_ID
import com.salesforce.androidsdk.ui.LoginActivity.Companion.SALESFORCE_WELCOME_DISCOVERY_MOBILE_URL_QUERY_PARAMETER_KEY_CLIENT_VERSION
import com.salesforce.androidsdk.ui.LoginActivity.Companion.SALESFORCE_WELCOME_DISCOVERY_URL_PATH
import com.salesforce.androidsdk.ui.LoginActivity.Companion.isSalesforceWelcomeDiscoveryMobileUrl
import com.salesforce.androidsdk.ui.LoginActivity.Companion.SimulatedDiscoveryResult
import com.salesforce.androidsdk.ui.LoginActivity.Companion.startDefaultLoginWithHintAndHost
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.security.BiometricAuthenticationManager
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginActivityTest {

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun doTokenRefresh_whenClientCannotBeBuilt_finishesActivity() {
        mockkObject(SalesforceSDKManager)
        val clientManager = mockk<ClientManager>()
        every { clientManager.peekRestClient() } returns null
        val sdkManager = mockk<SalesforceSDKManager>()
        every { sdkManager.clientManager } returns clientManager
        every { sdkManager.appContext } returns
                InstrumentationRegistry.getInstrumentation().targetContext
        every { SalesforceSDKManager.getInstance() } returns sdkManager
        var finishCalls = 0

        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val activity = object : LoginActivity() {
                    override fun finish() {
                        finishCalls += 1
                    }
                }
                invokeDoTokenRefresh(activity)
            }

            assertTrue(finishCalls == 1)
        } finally {
            unmockkObject(SalesforceSDKManager)
        }
    }

    @Test
    fun doTokenRefresh_whenRefreshFails_stillFinishesActivity() {
        mockkObject(SalesforceSDKManager)
        val interceptor = mockk<OAuthRefreshInterceptor>()
        every { interceptor.refreshAccessToken() } throws RuntimeException("Refresh failed")
        val client = mockk<RestClient>()
        every { client.oAuthRefreshInterceptor } returns interceptor
        val clientManager = mockk<ClientManager>()
        every { clientManager.peekRestClient() } returns client
        val sdkManager = mockk<SalesforceSDKManager>()
        every { sdkManager.clientManager } returns clientManager
        every { sdkManager.appContext } returns
                InstrumentationRegistry.getInstrumentation().targetContext
        every { SalesforceSDKManager.getInstance() } returns sdkManager
        var finishCalls = 0

        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val activity = object : LoginActivity() {
                    override fun finish() {
                        finishCalls += 1
                    }
                }
                invokeDoTokenRefresh(activity)
            }

            assertTrue(finishCalls == 1)
        } finally {
            unmockkObject(SalesforceSDKManager)
        }
    }

    @Test
    fun loginActivityCustomTabLauncher_withSingleServerCustomTabActivity_setsAboutBlank() {
        val loginUrl = mockk<MediatorLiveData<String>>()
        every { loginUrl.value = any() } just Runs
        val viewModel = mockk<LoginViewModel>(relaxed = true)
        every { viewModel.loginUrl } returns loginUrl
        every { viewModel.singleServerCustomTabActivity } returns true
        val activity = mockk<LoginActivity>(relaxed = true)
        every { activity.viewModel } returns viewModel

        val customTabActivityResult = activity.CustomTabActivityResult(activity)

        customTabActivityResult.onActivityResult(ActivityResult(RESULT_CANCELED, Intent()))

        verify(exactly = 1) { loginUrl.value = ABOUT_BLANK }
    }

    private fun invokeDoTokenRefresh(activity: LoginActivity) =
        LoginActivity::class.java
            .getDeclaredMethod("doTokenRefresh", LoginActivity::class.java)
            .apply { isAccessible = true }
            .invoke(activity, activity)

    @Test
    fun loginActivityCustomTabLauncher_withoutSingleServerCustomTabActivity_clearsWebView() {
        val viewModel = mockk<LoginViewModel>(relaxed = true)
        every { viewModel.singleServerCustomTabActivity } returns false
        val activity = mockk<LoginActivity>(relaxed = true)
        every { activity.viewModel } returns viewModel

        val customTabActivityResult = activity.CustomTabActivityResult(activity)

        customTabActivityResult.onActivityResult(ActivityResult(RESULT_CANCELED, Intent()))

        verify(exactly = 1) { activity.clearWebView(any()) }
    }

    @Test
    fun loginActivityCustomTabLauncher_withoutSingleServerCustomTabActivity_clearsWebViewWithoutShowingServerPicker() {
        val viewModel = mockk<LoginViewModel>(relaxed = true)
        every { viewModel.singleServerCustomTabActivity } returns false
        val activity = mockk<LoginActivity>(relaxed = true)
        every { activity.viewModel } returns viewModel
        every { activity.sharedBrowserSession } returns true

        val customTabActivityResult = activity.CustomTabActivityResult(activity)

        customTabActivityResult.onActivityResult(ActivityResult(RESULT_CANCELED, Intent()))

        verify(exactly = 1) { activity.clearWebView(any()) }
    }

    @Test
    fun loginActivityCustomTabLauncher_unexpectedResult_justRuns() {
        val viewModel = mockk<LoginViewModel>(relaxed = true)
        every { viewModel.singleServerCustomTabActivity } returns false
        val activity = mockk<LoginActivity>(relaxed = true)
        every { activity.viewModel } returns viewModel

        val customTabActivityResult = activity.CustomTabActivityResult(activity)

        customTabActivityResult.onActivityResult(ActivityResult(RESULT_OK, Intent()))

        verify(exactly = 0) { activity.clearWebView(any()) }
    }

    // region Login for Admin

    @Test
    fun adminLoginCustomTabLauncher_onCancel_doesNothing() {
        val loginUrl = mockk<MediatorLiveData<String>>(relaxed = true)
        val viewModel = mockk<LoginViewModel>(relaxed = true)
        every { viewModel.loginUrl } returns loginUrl
        val activity = mockk<LoginActivity>(relaxed = true)
        every { activity.viewModel } returns viewModel

        val adminResult = LoginActivity.AdminCustomTabActivityResult()
        adminResult.onActivityResult(ActivityResult(RESULT_CANCELED, Intent()))

        // Contrast with CustomTabActivityResult which calls these on cancel; the admin
        // launcher must preserve the existing WebView login page.
        verify(exactly = 0) { activity.clearWebView(any()) }
        verify(exactly = 0) { loginUrl.value = any() }
        verify(exactly = 0) { activity.finish() }
    }

    @Test
    fun onLoginForAdminsClick_withNullBrowserCustomTabUrl_doesNotLaunchCustomTab() {
        val browserCustomTabUrl = mockk<MediatorLiveData<String>>()
        every { browserCustomTabUrl.value } returns null
        val selectedServer = mockk<MediatorLiveData<String>>()
        every { selectedServer.value } returns "https://example.com"
        val viewModel = mockk<LoginViewModel>(relaxed = true)
        every { viewModel.browserCustomTabUrl } returns browserCustomTabUrl
        every { viewModel.selectedServer } returns selectedServer

        val activity = mockk<LoginActivity>(relaxed = true)
        every { activity.viewModel } returns viewModel
        every { activity.launchLoginForAdminsAction() } answers { callOriginal() }

        activity.launchLoginForAdminsAction()

        verify(exactly = 0) { activity.loadLoginPageInCustomTab(any(), any()) }
    }

    @Test
    fun onLoginForAdminsClick_withBrowserCustomTabUrl_launchesCustomTab() {
        val testUrl = "https://example.com/services/oauth2/authorize"
        val browserCustomTabUrl = mockk<MediatorLiveData<String>>()
        every { browserCustomTabUrl.value } returns testUrl
        val selectedServer = mockk<MediatorLiveData<String>>()
        every { selectedServer.value } returns "https://example.com"
        val viewModel = mockk<LoginViewModel>(relaxed = true)
        every { viewModel.browserCustomTabUrl } returns browserCustomTabUrl
        every { viewModel.selectedServer } returns selectedServer

        val activity = mockk<LoginActivity>(relaxed = true)
        every { activity.viewModel } returns viewModel
        every { activity.launchLoginForAdminsAction() } answers { callOriginal() }

        activity.launchLoginForAdminsAction()

        // `loadLoginPageInCustomTab` is invoked with the URL from `browserCustomTabUrl.value`.
        // Note: we can't verify the launcher argument via mockk here because Kotlin emits
        // direct field access (GETFIELD) for same-class property reads, bypassing the mocked
        // getter for `adminLoginCustomTabLauncher`. The admin-vs-regular launcher routing is
        // enforced structurally by the 2-line body of `onLoginForAdminsClick`.
        verify(exactly = 1) { activity.loadLoginPageInCustomTab(eq(testUrl), any()) }
    }

    /**
     * Phase 2 of Welcome Discovery: viewModel.selectedServer is the discovered My Domain (even
     * though LoginServerManager still has the Welcome URL selected).  Login for Admin is valid
     * here and MUST launch the Custom Tab.  Regression guard: keying the no-op off
     * LoginServerManager.selectedLoginServer instead of viewModel.selectedServer broke this.
     */
    @Test
    fun onLoginForAdminsClick_inWelcomeDiscoveryPhase2_launchesCustomTabAgainstMyDomain() {
        val testUrl = "https://acme.my.salesforce.com/services/oauth2/authorize"
        val browserCustomTabUrl = mockk<MediatorLiveData<String>>()
        every { browserCustomTabUrl.value } returns testUrl
        val selectedServer = mockk<MediatorLiveData<String>>()
        // Phase 2: selectedServer is the discovered My Domain, NOT the Welcome URL.
        every { selectedServer.value } returns "https://acme.my.salesforce.com"
        val viewModel = mockk<LoginViewModel>(relaxed = true)
        every { viewModel.browserCustomTabUrl } returns browserCustomTabUrl
        every { viewModel.selectedServer } returns selectedServer

        val activity = mockk<LoginActivity>(relaxed = true)
        every { activity.viewModel } returns viewModel
        every { activity.launchLoginForAdminsAction() } answers { callOriginal() }

        activity.launchLoginForAdminsAction()

        verify(exactly = 1) { activity.loadLoginPageInCustomTab(eq(testUrl), any()) }
    }

    /**
     * Phase 1 of Welcome Discovery: viewModel.selectedServer is the Welcome Discovery URL, whose
     * callback (sfdc://discocallback) is not app-unique.  Login for Admin MUST be a no-op here.
     */
    @Test
    fun onLoginForAdminsClick_inWelcomeDiscoveryPhase1_doesNotLaunchCustomTab() {
        val browserCustomTabUrl = mockk<MediatorLiveData<String>>()
        every { browserCustomTabUrl.value } returns "https://welcome.salesforce.com/discovery"
        val selectedServer = mockk<MediatorLiveData<String>>()
        every { selectedServer.value } returns "https://welcome.salesforce.com/discovery"
        val viewModel = mockk<LoginViewModel>(relaxed = true)
        every { viewModel.browserCustomTabUrl } returns browserCustomTabUrl
        every { viewModel.selectedServer } returns selectedServer

        val activity = mockk<LoginActivity>(relaxed = true)
        every { activity.viewModel } returns viewModel
        every { activity.launchLoginForAdminsAction() } answers { callOriginal() }

        activity.launchLoginForAdminsAction()

        verify(exactly = 0) { activity.loadLoginPageInCustomTab(any(), any()) }
    }

    // endregion

    // region Custom Tab toolbar color

    @Test
    fun customTabToolbarColor_withoutTopBarColor_usesLoginBackgroundColor() {
        // The toolbar color is the fixed login.salesforce.com background, NOT the WebView-sampled
        // dynamicBackgroundColor (which is stale at Custom Tab launch time and caused the
        // black/white inconsistency).
        assertEquals(
            LoginActivity.LOGIN_BACKGROUND_COLOR,
            LoginActivity.customTabToolbarColor(topBarColor = null),
        )
    }

    @Test
    fun customTabToolbarColor_withTopBarColor_usesAppProvidedColor() {
        val appColor = Color(red = 10, green = 20, blue = 30)
        assertEquals(appColor, LoginActivity.customTabToolbarColor(topBarColor = appColor))
    }

    // endregion

    // region buildCustomTabAuthorizeUrl

    @Test
    @Suppress("DEPRECATION")
    fun buildCustomTabAuthorizeUrl_forRegularLogin_appendsSdkInfoAndOrgConfigTrigger() {
        val loginUrl = "https://example.com/services/oauth2/authorize?client_id=abc"
        val sdkManager = mockk<SalesforceSDKManager>(relaxed = true)
        every { sdkManager.forceAdvancedAuthentication } returns false
        every { sdkManager.getUserAgent("") } returns "SalesforceMobileSDK/test"
        val activity = mockk<LoginActivity>(relaxed = true)
        every { activity.sharedBrowserSession } returns false
        every {
            activity.buildCustomTabAuthorizeUrl(any(), any(), any())
        } answers { callOriginal() }

        val result = activity.buildCustomTabAuthorizeUrl(loginUrl, false, sdkManager)

        assertTrue("Should append sdkInfo",
            result.contains("&sdkInfo=${Uri.encode("SalesforceMobileSDK/test")}"))
        assertTrue("Should append org_config trigger (no admin/force flag)",
            result.contains("&auth_trigger=$AUTH_TRIGGER_ORG_CONFIG"))
        assertTrue("Should append prompt=login when not sharing browser session",
            result.endsWith("&prompt=login"))
    }

    @Test
    @Suppress("DEPRECATION")
    fun buildCustomTabAuthorizeUrl_withForceAdvancedAuth_appendsForceAdvancedAuthTrigger() {
        val loginUrl = "https://example.com/services/oauth2/authorize?client_id=abc"
        val sdkManager = mockk<SalesforceSDKManager>(relaxed = true)
        every { sdkManager.forceAdvancedAuthentication } returns true
        every { sdkManager.getUserAgent("") } returns "SalesforceMobileSDK/test"
        val activity = mockk<LoginActivity>(relaxed = true)
        every { activity.sharedBrowserSession } returns false
        every {
            activity.buildCustomTabAuthorizeUrl(any(), any(), any())
        } answers { callOriginal() }

        val result = activity.buildCustomTabAuthorizeUrl(loginUrl, false, sdkManager)

        assertTrue("Should append force_advanced_auth trigger",
            result.contains("&auth_trigger=$AUTH_TRIGGER_FORCE_ADVANCED_AUTH"))
    }

    @Test
    @Suppress("DEPRECATION")
    fun buildCustomTabAuthorizeUrl_forAdminLogin_appendsLoginForAdminTriggerEvenWithForceFlag() {
        val loginUrl = "https://example.com/services/oauth2/authorize?client_id=abc"
        val sdkManager = mockk<SalesforceSDKManager>(relaxed = true)
        // Force flag is also set, but admin login should still take priority.
        every { sdkManager.forceAdvancedAuthentication } returns true
        every { sdkManager.getUserAgent("") } returns "SalesforceMobileSDK/test"
        val activity = mockk<LoginActivity>(relaxed = true)
        every { activity.sharedBrowserSession } returns false
        every {
            activity.buildCustomTabAuthorizeUrl(any(), any(), any())
        } answers { callOriginal() }

        val result = activity.buildCustomTabAuthorizeUrl(loginUrl, true, sdkManager)

        assertTrue("Admin login should take priority and append login_for_admin trigger",
            result.contains("&auth_trigger=$AUTH_TRIGGER_LOGIN_FOR_ADMIN"))
    }

    @Test
    @Suppress("DEPRECATION")
    fun buildCustomTabAuthorizeUrl_withSharedBrowserSession_omitsPromptLogin() {
        val loginUrl = "https://example.com/services/oauth2/authorize?client_id=abc"
        val sdkManager = mockk<SalesforceSDKManager>(relaxed = true)
        every { sdkManager.forceAdvancedAuthentication } returns false
        every { sdkManager.getUserAgent("") } returns "SalesforceMobileSDK/test"
        val activity = mockk<LoginActivity>(relaxed = true)
        every { activity.sharedBrowserSession } returns true
        every {
            activity.buildCustomTabAuthorizeUrl(any(), any(), any())
        } answers { callOriginal() }

        val result = activity.buildCustomTabAuthorizeUrl(loginUrl, false, sdkManager)

        assertFalse("prompt=login should be omitted for a shared browser session",
            result.contains("prompt=login"))
    }

    // endregion

    @Test
    fun testIsWelcomeDiscoveryUri() {
        val validUrl = "https://welcome.salesforce.com$SALESFORCE_WELCOME_DISCOVERY_URL_PATH?$SALESFORCE_WELCOME_DISCOVERY_MOBILE_URL_QUERY_PARAMETER_KEY_CLIENT_ID=X&$SALESFORCE_WELCOME_DISCOVERY_MOBILE_URL_QUERY_PARAMETER_KEY_CLIENT_VERSION=Y&$SALESFORCE_WELCOME_DISCOVERY_MOBILE_URL_QUERY_PARAMETER_KEY_CALLBACK_URL=Z"

        val nonHierarchicalUri = "mailto:test@example.com"

        val incorrectPathUrl = "https://welcome.salesforce.com/other/path?$SALESFORCE_WELCOME_DISCOVERY_MOBILE_URL_QUERY_PARAMETER_KEY_CLIENT_ID=X&$SALESFORCE_WELCOME_DISCOVERY_MOBILE_URL_QUERY_PARAMETER_KEY_CLIENT_VERSION=Y&$SALESFORCE_WELCOME_DISCOVERY_MOBILE_URL_QUERY_PARAMETER_KEY_CALLBACK_URL=Z"
        val emptyPathUrl = "https://welcome.salesforce.com?/$SALESFORCE_WELCOME_DISCOVERY_MOBILE_URL_QUERY_PARAMETER_KEY_CLIENT_ID=X&$SALESFORCE_WELCOME_DISCOVERY_MOBILE_URL_QUERY_PARAMETER_KEY_CLIENT_VERSION=Y&$SALESFORCE_WELCOME_DISCOVERY_MOBILE_URL_QUERY_PARAMETER_KEY_CALLBACK_URL=Z"
        val missingPathUrl = "https://welcome.salesforce.com?$SALESFORCE_WELCOME_DISCOVERY_MOBILE_URL_QUERY_PARAMETER_KEY_CLIENT_ID=X&$SALESFORCE_WELCOME_DISCOVERY_MOBILE_URL_QUERY_PARAMETER_KEY_CLIENT_VERSION=Y&$SALESFORCE_WELCOME_DISCOVERY_MOBILE_URL_QUERY_PARAMETER_KEY_CALLBACK_URL=Z"

        val missingClientIdUrl = "https://welcome.salesforce.com$SALESFORCE_WELCOME_DISCOVERY_URL_PATH?$SALESFORCE_WELCOME_DISCOVERY_MOBILE_URL_QUERY_PARAMETER_KEY_CLIENT_VERSION=Y&$SALESFORCE_WELCOME_DISCOVERY_MOBILE_URL_QUERY_PARAMETER_KEY_CALLBACK_URL=Z"
        val missingClientVersionUrl = "https://welcome.salesforce.com$SALESFORCE_WELCOME_DISCOVERY_URL_PATH?$SALESFORCE_WELCOME_DISCOVERY_MOBILE_URL_QUERY_PARAMETER_KEY_CLIENT_ID=X&$SALESFORCE_WELCOME_DISCOVERY_MOBILE_URL_QUERY_PARAMETER_KEY_CALLBACK_URL=Z"
        val missingCallbackUrl = "https://welcome.salesforce.com$SALESFORCE_WELCOME_DISCOVERY_URL_PATH?$SALESFORCE_WELCOME_DISCOVERY_MOBILE_URL_QUERY_PARAMETER_KEY_CLIENT_ID=X&$SALESFORCE_WELCOME_DISCOVERY_MOBILE_URL_QUERY_PARAMETER_KEY_CLIENT_VERSION=Y"

        val otherUrl = "https://login.salesforce.com"

        assertTrue("Valid URI should return true", isSalesforceWelcomeDiscoveryMobileUrl(validUrl.toUri()))

        assertFalse("Non-hierarchical URI should return false", isSalesforceWelcomeDiscoveryMobileUrl(nonHierarchicalUri.toUri()))

        assertFalse("Incorrect path URI should return false", isSalesforceWelcomeDiscoveryMobileUrl(incorrectPathUrl.toUri()))
        assertFalse("Empty path URI should return false", isSalesforceWelcomeDiscoveryMobileUrl(emptyPathUrl.toUri()))
        assertFalse("Missing path URI should return false", isSalesforceWelcomeDiscoveryMobileUrl(missingPathUrl.toUri()))

        assertFalse("Missing client id parameter should return false", isSalesforceWelcomeDiscoveryMobileUrl(missingClientIdUrl.toUri()))

        assertFalse("Missing client version parameter should return false", isSalesforceWelcomeDiscoveryMobileUrl(missingClientVersionUrl.toUri()))

        assertFalse("Missing callback URL parameter should return false", isSalesforceWelcomeDiscoveryMobileUrl(missingCallbackUrl.toUri()))

        assertFalse("Non-welcome URL should return false", isSalesforceWelcomeDiscoveryMobileUrl(otherUrl.toUri()))
    }

    // region Salesforce Welcome Discovery

    @Test
    fun loginActivityPendingServerObserver_appliesPendingServer_onChange() {

        val pendingServer = "https://welcome.example.com/discovery"
        val activity = mockk<LoginActivity>(relaxed = true)
        every { activity.intent.data } returns null
        val observer = activity.PendingServerObserver(activity)
        observer.onChanged(pendingServer)
        verify(exactly = 1) { activity.viewModel.applyPendingServer(pendingLoginServer = pendingServer) }
    }

    @Test
    fun loginActivityPendingServerObserver_returns_onChangeMatchingIntentData() {

        val pendingServer = "https://www.example.com" // IETF-Reserved Test Domain
        val activity = mockk<LoginActivity>(relaxed = true)
        val viewModel = mockk<LoginViewModel>(relaxed = true)
        every { activity.viewModel } returns viewModel
        every { activity.intent.data } returns pendingServer.toUri()
        val observer = activity.PendingServerObserver(activity)
        observer.onChanged(pendingServer)
        verify(exactly = 0) { viewModel.applyPendingServer(pendingLoginServer = any()) }
    }

    @Test
    fun loginActivityPendingServerObserver_switchesDefaultOrSalesforceWelcomeDiscoveryLogin_onChangeIntentDataTogglesWelcomeDiscoveryUrlPath() {

        val pendingServerWelcomeDiscoveryUrlPath = "https://welcome.example.com/discovery"
        val activity = mockk<LoginActivity>(relaxed = true)
        val viewModel = mockk<LoginViewModel>(relaxed = true)
        every { activity.viewModel } returns viewModel
        every { activity.switchDefaultOrSalesforceWelcomeDiscoveryLogin(any()) } returns true
        val observer = activity.PendingServerObserver(activity)
        observer.onChanged(pendingServerWelcomeDiscoveryUrlPath)
        verify(exactly = 0) { viewModel.applyPendingServer(pendingLoginServer = any()) }
    }

    @Test
    fun loginActivityPendingServerObserver_switchesDefaultOrSalesforceWelcomeDiscoveryLogin_onChangeIntentDataPathOnlyTogglesWelcomeDiscoveryUrlPath() {

        val pendingServerWelcomeDiscoveryUrlPath = "https://welcome.example.com/discovery"
        val intent = mockk<Intent>(relaxed = true)
        every { intent.data } returns "https://welcome.example.com/other".toUri()
        val viewModel = mockk<LoginViewModel>(relaxed = true)
        val activity = mockk<LoginActivity>(relaxed = true)
        every { activity.intent } returns intent
        every { activity.viewModel } returns viewModel
        every { activity.switchDefaultOrSalesforceWelcomeDiscoveryLogin(any()) } returns true
        val observer = activity.PendingServerObserver(activity)
        observer.onChanged(pendingServerWelcomeDiscoveryUrlPath)
        verify(exactly = 0) { viewModel.applyPendingServer(pendingLoginServer = any()) }
    }

    @Test
    fun loginActivityPendingServerObserver_switchesDefaultOrSalesforceWelcomeDiscoveryLogin_onChangeIntentDataHostOnlyTogglesWelcomeDiscoveryUrlPath() {

        val pendingServerWelcomeDiscoveryUrlPath = "https://welcome.example.com/discovery"
        val intent = mockk<Intent>(relaxed = true)
        every { intent.data } returns "https://other.example.com/discovery".toUri()
        val viewModel = mockk<LoginViewModel>(relaxed = true)
        val activity = mockk<LoginActivity>(relaxed = true)
        every { activity.intent } returns intent
        every { activity.viewModel } returns viewModel
        every { activity.switchDefaultOrSalesforceWelcomeDiscoveryLogin(any()) } returns true
        val observer = activity.PendingServerObserver(activity)
        observer.onChanged(pendingServerWelcomeDiscoveryUrlPath)
        verify(exactly = 0) { viewModel.applyPendingServer(pendingLoginServer = any()) }
    }

    @Test
    fun loginActivityPendingServerObserver_switchesDefaultOrSalesforceWelcomeDiscoveryLogin_onChangeIntentDataHostPlusLoginHintExtras() {

        val pendingServerWelcomeDiscoveryUrlPath = "https://welcome.example.com/discovery"
        val intent = mockk<Intent>(relaxed = true)
        every { intent.getStringExtra(EXTRA_KEY_LOGIN_HINT) } returns "example_user@example.com"
        every { intent.getStringExtra(EXTRA_KEY_LOGIN_HOST) } returns "welcome.example.com"
        every { intent.data } returns "https://welcome.example.com/discovery".toUri()
        val viewModel = mockk<LoginViewModel>(relaxed = true)
        val activity = mockk<LoginActivity>(relaxed = true)
        every { activity.intent } returns intent
        every { activity.viewModel } returns viewModel
        every { activity.switchDefaultOrSalesforceWelcomeDiscoveryLogin(any()) } returns true
        val observer = activity.PendingServerObserver(activity)
        observer.onChanged(pendingServerWelcomeDiscoveryUrlPath)
        verify(exactly = 0) { viewModel.applyPendingServer(pendingLoginServer = any()) }
    }

    @Test
    fun loginActivity_startsCorrectActivity_onStartDefaultLoginWithHintAndHost() {

        val loginHint = "ExampleUser@Example.com"
        val loginHost = "https://login.example.com"

        val context = mockk<Context>(relaxed = true)

        startDefaultLoginWithHintAndHost(
            context = context,
            loginHint = loginHint, // IETF-Reserved Test Domain
            loginHost = loginHost // IETF-Reserved Test Domain
        )

        verify(exactly = 1) {
            context.startActivity(
                match {
                    it.component?.className == LoginActivity::class.java.name
                    it.getStringExtra(EXTRA_KEY_LOGIN_HINT) == loginHint
                    it.getStringExtra(EXTRA_KEY_LOGIN_HOST) == loginHost
                    it.flags == FLAG_ACTIVITY_SINGLE_TOP
                }
            )
        }
    }

    @Test
    fun applySimulatedDiscoveryResult_noArmedResult_returnsFalse() {
        SalesforceSDKManager.getInstance().simulatedDiscoveryResult = null
        val activity = mockk<LoginActivity>(relaxed = true)
        every { activity.applySimulatedDiscoveryResultIfApplicable() } answers { callOriginal() }

        assertFalse(activity.applySimulatedDiscoveryResultIfApplicable())
    }

    @Test
    fun applySimulatedDiscoveryResult_armedResult_consumesAndReturnsTrue() {
        val sdkManager = SalesforceSDKManager.getInstance()
        sdkManager.simulatedDiscoveryResult = SimulatedDiscoveryResult(
            loginHint = "user@example.com",
            loginHost = "test.my.example.com",
        )
        try {
            val loginUrl = mockk<MediatorLiveData<String>>(relaxed = true)
            val viewModel = mockk<LoginViewModel>(relaxed = true)
            every { viewModel.loginUrl } returns loginUrl
            val activity = mockk<LoginActivity>(relaxed = true)
            every { activity.viewModel } returns viewModel
            every { activity.applySimulatedDiscoveryResultIfApplicable() } answers { callOriginal() }

            assertTrue(activity.applySimulatedDiscoveryResultIfApplicable())
            // Armed result is consumed (cleared) on apply.
            org.junit.Assert.assertNull(sdkManager.simulatedDiscoveryResult)
            // WebView is reset so the next reload isn't suppressed by same-host short-circuit.
            verify(exactly = 1) { loginUrl.value = ABOUT_BLANK }
        } finally {
            sdkManager.simulatedDiscoveryResult = null
        }
    }

    @Test
    fun switchDefaultOrSalesforceWelcomeDiscoveryLogin_consumesArmedSimulationInsteadOfLoadingDiscoveryWebView() {
        val sdkManager = SalesforceSDKManager.getInstance()
        sdkManager.simulatedDiscoveryResult = SimulatedDiscoveryResult(
            loginHint = "user@example.com",
            loginHost = "test.my.example.com",
        )
        try {
            val loginUrl = mockk<MediatorLiveData<String>>(relaxed = true)
            val viewModel = mockk<LoginViewModel>(relaxed = true)
            every { viewModel.loginUrl } returns loginUrl
            val activity = mockk<LoginActivity>(relaxed = true)
            every { activity.viewModel } returns viewModel
            every { activity.applySimulatedDiscoveryResultIfApplicable() } answers { callOriginal() }
            every { activity.switchDefaultOrSalesforceWelcomeDiscoveryLogin(any()) } answers { callOriginal() }

            assertTrue(
                activity.switchDefaultOrSalesforceWelcomeDiscoveryLogin(
                    "https://welcome.example.com/discovery".toUri()
                )
            )
            // Discovery WebView intent should NOT be dispatched - the simulation hook handled it.
            verify(exactly = 0) {
                activity.startActivity(match { it.data?.path?.contains("/discovery") == true })
            }
            org.junit.Assert.assertNull(sdkManager.simulatedDiscoveryResult)
        } finally {
            sdkManager.simulatedDiscoveryResult = null
        }
    }

    @Test
    fun applySalesforceWelcomeLoginHintAndHost_setsPendingServer_andDoesNotPersistToLoginServerManager() {
        val loginHost = "acme.my.salesforce.com"
        val loginHint = "user@acme.com"
        val expectedLoginUrl = "https://$loginHost"

        val intent = mockk<Intent>(relaxed = true)
        every { intent.getStringExtra(EXTRA_KEY_LOGIN_HINT) } returns loginHint
        every { intent.getStringExtra(EXTRA_KEY_LOGIN_HOST) } returns loginHost

        val pendingServer = mockk<MediatorLiveData<String>>(relaxed = true)
        val viewModel = mockk<LoginViewModel>(relaxed = true)
        every { viewModel.pendingServer } returns pendingServer
        every { viewModel.loginHint = any() } just Runs

        val sdkManager = SalesforceSDKManager.getInstance()
        val originalSelectedServer = sdkManager.loginServerManager.selectedLoginServer
        val originalServersCount = sdkManager.loginServerManager.loginServers.size

        val activity = mockk<LoginActivity>(relaxed = true)
        every { activity.viewModel } returns viewModel
        every { activity.applySalesforceWelcomeLoginHintAndHost(intent) } answers { callOriginal() }

        activity.applySalesforceWelcomeLoginHintAndHost(intent)

        verify(exactly = 1) { viewModel.loginHint = loginHint }
        verify(exactly = 1) { pendingServer.value = expectedLoginUrl }
        org.junit.Assert.assertEquals(
            originalSelectedServer,
            sdkManager.loginServerManager.selectedLoginServer
        )
        org.junit.Assert.assertEquals(
            originalServersCount,
            sdkManager.loginServerManager.loginServers.size
        )
        org.junit.Assert.assertNull(
            sdkManager.loginServerManager.getLoginServerFromURL(expectedLoginUrl)
        )
    }

    @Test
    fun applySalesforceWelcomeLoginHintAndHost_withoutLoginHostExtra_doesNotTouchPendingServer() {
        val loginHint = "user@acme.com"

        val intent = mockk<Intent>(relaxed = true)
        every { intent.getStringExtra(EXTRA_KEY_LOGIN_HINT) } returns loginHint
        every { intent.getStringExtra(EXTRA_KEY_LOGIN_HOST) } returns null

        val pendingServer = mockk<MediatorLiveData<String>>(relaxed = true)
        val viewModel = mockk<LoginViewModel>(relaxed = true)
        every { viewModel.pendingServer } returns pendingServer
        every { viewModel.loginHint = any() } just Runs

        val activity = mockk<LoginActivity>(relaxed = true)
        every { activity.viewModel } returns viewModel
        every { activity.applySalesforceWelcomeLoginHintAndHost(intent) } answers { callOriginal() }

        activity.applySalesforceWelcomeLoginHintAndHost(intent)

        verify(exactly = 1) { viewModel.loginHint = loginHint }
        verify(exactly = 0) { pendingServer.value = any() }
    }

    // endregion

    // region handleBackBehavior (W-23731759 — non-dismissable login picker)

    @Test
    fun test_handleBackBehavior_fromPicker_noAccounts_movesTaskToBack() {
        val sdkManager = mockk<SalesforceSDKManager>(relaxed = true)
        mockkObject(SalesforceSDKManager)
        mockkObject(SalesforceSDKManager.Companion)
        every { SalesforceSDKManager.getInstance() } returns sdkManager
        every { sdkManager.nativeLoginActivity } returns null
        val bioAuthManager = mockk<BiometricAuthenticationManager>(relaxed = true)
        every { bioAuthManager.locked } returns false
        every { sdkManager.biometricAuthenticationManager } returns bioAuthManager
        val userAccountManager = mockk<UserAccountManager>(relaxed = true)
        every { sdkManager.userAccountManager } returns userAccountManager
        every { userAccountManager.authenticatedUsers } returns null

        val activity = mockk<LoginActivity>(relaxed = true)
        every { activity.handleBackBehavior() } answers { callOriginal() }

        activity.handleBackBehavior()

        verify(exactly = 1) { activity.moveTaskToBack(true) }
        verify(exactly = 0) { activity.finish() }
    }

    @Test
    fun test_handleBackBehavior_fromPicker_hasAccount_finishes() {
        val sdkManager = mockk<SalesforceSDKManager>(relaxed = true)
        mockkObject(SalesforceSDKManager)
        mockkObject(SalesforceSDKManager.Companion)
        every { SalesforceSDKManager.getInstance() } returns sdkManager
        every { sdkManager.nativeLoginActivity } returns null
        val bioAuthManager = mockk<BiometricAuthenticationManager>(relaxed = true)
        every { bioAuthManager.locked } returns false
        every { sdkManager.biometricAuthenticationManager } returns bioAuthManager
        val userAccountManager = mockk<UserAccountManager>(relaxed = true)
        every { sdkManager.userAccountManager } returns userAccountManager
        every { userAccountManager.authenticatedUsers } returns listOf(mockk(relaxed = true))

        val activity = mockk<LoginActivity>(relaxed = true)
        every { activity.handleBackBehavior() } answers { callOriginal() }

        activity.handleBackBehavior()

        verify(exactly = 1) { activity.setResult(RESULT_CANCELED) }
        verify(exactly = 1) { activity.finish() }
        verify(exactly = 0) { activity.moveTaskToBack(any()) }
    }

    @Test
    fun test_handleBackBehavior_fromPicker_biometricLocked_doesNothing() {
        val sdkManager = mockk<SalesforceSDKManager>(relaxed = true)
        mockkObject(SalesforceSDKManager)
        mockkObject(SalesforceSDKManager.Companion)
        every { SalesforceSDKManager.getInstance() } returns sdkManager
        every { sdkManager.nativeLoginActivity } returns null
        val bioAuthManager = mockk<BiometricAuthenticationManager>(relaxed = true)
        every { bioAuthManager.locked } returns true
        every { sdkManager.biometricAuthenticationManager } returns bioAuthManager

        val viewModel = mockk<LoginViewModel>(relaxed = true)
        val activity = mockk<LoginActivity>(relaxed = true)
        every { activity.viewModel } returns viewModel
        every { activity.handleBackBehavior() } answers { callOriginal() }

        activity.handleBackBehavior()

        verify(exactly = 0) { activity.finish() }
        verify(exactly = 0) { activity.moveTaskToBack(any()) }
    }

    @Test
    fun test_handleBackBehavior_fromPicker_nativeLogin_earlyReturns() {
        val sdkManager = mockk<SalesforceSDKManager>(relaxed = true)
        mockkObject(SalesforceSDKManager)
        mockkObject(SalesforceSDKManager.Companion)
        every { SalesforceSDKManager.getInstance() } returns sdkManager
        every { sdkManager.nativeLoginActivity } returns LoginActivity::class.java

        val viewModel = mockk<LoginViewModel>(relaxed = true)
        val activity = mockk<LoginActivity>(relaxed = true)
        every { activity.viewModel } returns viewModel
        every { activity.handleBackBehavior() } answers { callOriginal() }

        activity.handleBackBehavior()

        verify(exactly = 1) { activity.setResult(RESULT_CANCELED) }
        verify(exactly = 1) { activity.finish() }
        // The early return must prevent moveTaskToBack from also running.
        verify(exactly = 0) { activity.moveTaskToBack(any()) }
    }

    // endregion

    // region clearWebView (W-23731759 — non-dismissable login picker)

    @Test
    fun test_clearWebView_showServerPickerTrue_showsPicker() {
        val loginUrl = mockk<MediatorLiveData<String>>(relaxed = true)
        val showServerPicker = mockk<androidx.compose.runtime.MutableState<Boolean>>(relaxed = true)
        val viewModel = mockk<LoginViewModel>(relaxed = true)
        every { viewModel.loginUrl } returns loginUrl
        every { viewModel.showServerPicker } returns showServerPicker
        val activity = mockk<LoginActivity>(relaxed = true)
        every { activity.viewModel } returns viewModel
        every { activity.runOnUiThread(any()) } answers { firstArg<Runnable>().run() }
        every { activity.clearWebView(any()) } answers { callOriginal() }

        activity.clearWebView(showServerPicker = true)

        verify(exactly = 1) { loginUrl.value = ABOUT_BLANK }
        verify(exactly = 1) { showServerPicker.value = true }
    }

    @Test
    fun test_clearWebView_showServerPickerFalse_doesNotShowPicker() {
        val loginUrl = mockk<MediatorLiveData<String>>(relaxed = true)
        val showServerPicker = mockk<androidx.compose.runtime.MutableState<Boolean>>(relaxed = true)
        val viewModel = mockk<LoginViewModel>(relaxed = true)
        every { viewModel.loginUrl } returns loginUrl
        every { viewModel.showServerPicker } returns showServerPicker
        val activity = mockk<LoginActivity>(relaxed = true)
        every { activity.viewModel } returns viewModel
        every { activity.runOnUiThread(any()) } answers { firstArg<Runnable>().run() }
        every { activity.clearWebView(any()) } answers { callOriginal() }

        activity.clearWebView(showServerPicker = false)

        verify(exactly = 1) { loginUrl.value = ABOUT_BLANK }
        verify(exactly = 0) { showServerPicker.value = any() }
    }

    // endregion
}
