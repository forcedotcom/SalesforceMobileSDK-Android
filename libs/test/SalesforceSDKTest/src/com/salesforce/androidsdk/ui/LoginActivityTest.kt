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
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.core.net.toUri
import androidx.lifecycle.MediatorLiveData
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salesforce.androidsdk.ui.LoginActivity.Companion.ABOUT_BLANK
import com.salesforce.androidsdk.ui.LoginActivity.Companion.EXTRA_KEY_LOGIN_HINT
import com.salesforce.androidsdk.ui.LoginActivity.Companion.EXTRA_KEY_LOGIN_HOST
import com.salesforce.androidsdk.ui.LoginActivity.Companion.SALESFORCE_WELCOME_DISCOVERY_MOBILE_URL_QUERY_PARAMETER_KEY_CALLBACK_URL
import com.salesforce.androidsdk.ui.LoginActivity.Companion.SALESFORCE_WELCOME_DISCOVERY_MOBILE_URL_QUERY_PARAMETER_KEY_CLIENT_ID
import com.salesforce.androidsdk.ui.LoginActivity.Companion.SALESFORCE_WELCOME_DISCOVERY_MOBILE_URL_QUERY_PARAMETER_KEY_CLIENT_VERSION
import com.salesforce.androidsdk.ui.LoginActivity.Companion.SALESFORCE_WELCOME_DISCOVERY_URL_PATH
import com.salesforce.androidsdk.ui.LoginActivity.Companion.isSalesforceWelcomeDiscoveryMobileUrl
import com.salesforce.androidsdk.ui.LoginActivity.Companion.startDefaultLoginWithHintAndHost
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginActivityTest {

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
    fun loginActivityBrowserCustomTabObserver_startsBrowserCustomTabAuthorization_onChange() {

        val exampleUrl = "https://www.example.com" // IETF-Reserved Test Domain

        val activity = mockk<LoginActivity>(relaxed = true)
        val activityResultLauncher = mockk<ActivityResultLauncher<Intent>>(relaxed = true)
        every { activity.customTabLauncher } returns activityResultLauncher

        val observer = activity.BrowserCustomTabUrlObserver(activity)

        observer.onChanged(exampleUrl)
        verify(exactly = -1) {
            activity.startBrowserCustomTabAuthorization(
                match { it == exampleUrl },
                match { it == activityResultLauncher }
            )
        }
    }

    @Test
    fun loginActivityBrowserCustomTabObserver_returns_onChangeWithAboutBlank() {

        val activity = mockk<LoginActivity>(relaxed = true)
        val observer = activity.BrowserCustomTabUrlObserver(activity)

        observer.onChanged(ABOUT_BLANK)
        verify(exactly = 0) { activity.startBrowserCustomTabAuthorization(any(), any(), any()) }
    }

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

    // endregion
}
