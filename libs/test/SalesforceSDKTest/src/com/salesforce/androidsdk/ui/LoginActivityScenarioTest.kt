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

import android.content.Intent
import android.net.Uri.parse
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salesforce.androidsdk.app.Features
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.LoginServerManager.PRODUCTION_LOGIN_URL
import com.salesforce.androidsdk.config.LoginServerManager.WELCOME_LOGIN_URL
import com.salesforce.androidsdk.ui.LoginActivity.Companion.EXTRA_KEY_LOGIN_HINT
import com.salesforce.androidsdk.ui.LoginActivity.Companion.EXTRA_KEY_LOGIN_HOST
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginActivityScenarioTest {

    @Test
    fun viewModelLoginHint_UpdatesOn_onCreateWithSalesforceWelcomeLoginHintIntentExtras() {
        val expectedLoginHint = "ietf_example_domain_reserved_for_test@example.com"
        val expectedLoginServerHostname = "welcome.salesforce.com"

        launch<LoginActivity>(
            Intent(
                getApplicationContext(),
                LoginActivity::class.java
            ).apply {
                putExtra(EXTRA_KEY_LOGIN_HINT, expectedLoginHint)
                putExtra(EXTRA_KEY_LOGIN_HOST, expectedLoginServerHostname)
            }).use { activityScenario ->

            activityScenario.onActivity { activity ->

                val actualLoginHint = activity.viewModel.loginHint
                val actualLoginServerHostname = SalesforceSDKManager.getInstance().loginServerManager.selectedLoginServer

                assertEquals(expectedLoginHint, actualLoginHint)
                assertEquals(expectedLoginServerHostname, parse(actualLoginServerHostname.url).host)
            }
        }
    }

    @Test
    fun viewModelIsUsingFrontDoorBridge_DefaultValue_onCreateWithoutQrCodeLoginIntent() {
        launch<LoginActivity>(
            Intent(
                getApplicationContext(),
                LoginActivity::class.java
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->

                assertFalse(activity.viewModel.isUsingFrontDoorBridge)
            }
        }
    }

    @Test
    fun onBrowserCustomTabReady_IsSetOnCreate() {
        // The activity wires the callback as part of onCreate so that the ViewModel can push
        // the browser-custom-tab URL back up when it's ready (replacing the deleted
        // BrowserCustomTabUrlObserver). Verifying the callback is non-null after onCreate
        // protects against someone accidentally removing the wiring. The *behavior* of the
        // lambda (routing through the regular customTabLauncher, not the admin one) is
        // exercised by the `onLoginForAdminsClick_*` tests in LoginActivityTest which verify
        // the two launchers are used by the right code paths.
        launch<LoginActivity>(
            Intent(getApplicationContext(), LoginActivity::class.java)
        ).use { activityScenario ->
            activityScenario.onActivity { activity ->
                assertNotNull(
                    "onBrowserCustomTabReady should be set in LoginActivity.onCreate",
                    activity.viewModel.onBrowserCustomTabReady,
                )
            }
        }
    }

    @Test
    fun viewModelFrontDoorBridgeCodeVerifier_UpdatesOn_onCreateWithQrCodeLoginIntent() {
        val uri = "app://android/login/qr/?bridgeJson=%7B%22pkce_code_verifier%22%3A%22__CODE_VERIFIER__%22%2C%22frontdoor_bridge_url%22%3A%22https%3A%2F%2Fmobilesdk.my.salesforce.com%2Fsecur%2Ffrontdoor.jsp%3Fotp%3D__OTP__%26startURL%3D%252Fservices%252Foauth2%252Fauthorize%253Fresponse_type%253Dcode%2526client_id%253D__CONSUMER_KEY__%2526redirect_uri%253Dtestsfdc%25253A%25252F%25252F%25252Fmobilesdk%25252Fdetect%25252Foauth%25252Fdone%2526code_challenge%253D__CODE_CHALLENGE__%26cshc%3D__CSHC__%22%7D".toUri()

        launch<LoginActivity>(
            Intent(
                getApplicationContext(),
                LoginActivity::class.java
            ).apply {
                data = uri
            }).use { activityScenario ->

            activityScenario.onActivity { activity ->

                assertTrue(activity.viewModel.isUsingFrontDoorBridge)
                assertEquals("__CODE_VERIFIER__", activity.viewModel.frontdoorBridgeCodeVerifier)
                assertEquals("https://mobilesdk.my.salesforce.com", activity.viewModel.frontdoorBridgeServer)
                assertEquals("https://mobilesdk.my.salesforce.com/secur/frontdoor.jsp?otp=__OTP__&startURL=%2Fservices%2Foauth2%2Fauthorize%3Fresponse_type%3Dcode%26client_id%3D__CONSUMER_KEY__%26redirect_uri%3Dtestsfdc%253A%252F%252F%252Fmobilesdk%252Fdetect%252Foauth%252Fdone%26code_challenge%3D__CODE_CHALLENGE__&cshc=__CSHC__", activity.viewModel.frontDoorBridgeUrl.value)
            }
        }
    }

    @Test
    fun viewModelIsUsingFrontDoorBridge_DefaultValue_onCreateWithQrCodeLoginIntentAndMismatchedConsumerKey() {
        val uri = "app://android/login/qr/?bridgeJson=%7B%22pkce_code_verifier%22%3A%22__CODE_VERIFIER__%22%2C%22frontdoor_bridge_url%22%3A%22https%3A%2F%2Fmobilesdk.my.salesforce.com%2Fsecur%2Ffrontdoor.jsp%3Fotp%3D__OTP__%26startURL%3D%252Fservices%252Foauth2%252Fauthorize%253Fresponse_type%253Dcode%2526client_id%253D__MISMATCHED_CONSUMER_KEY__%2526redirect_uri%253Dtestsfdc%25253A%25252F%25252F%25252Fmobilesdk%25252Fdetect%25252Foauth%25252Fdone%2526code_challenge%253D__CODE_CHALLENGE__%26cshc%3D__CSHC__%22%7D".toUri()

        launch<LoginActivity>(
            Intent(
                getApplicationContext(),
                LoginActivity::class.java
            ).apply {
                data = uri
            }).use { activityScenario ->

            activityScenario.onActivity { activity ->

                assertFalse(activity.viewModel.isUsingFrontDoorBridge)
            }
        }
    }

    @Test
    fun viewModelIsUsingFrontDoorBridge_UpdatesOn_onCreateWithQrCodeLoginIntentAndMissingStartUrl() {
        val uri = "app://android/login/qr/?bridgeJson=%7B%22pkce_code_verifier%22%3A%22__CODE_VERIFIER__%22%2C%22frontdoor_bridge_url%22%3A%22https%3A%2F%2Fmobilesdk.my.salesforce.com%2Fsecur%2Ffrontdoor.jsp%3Fotp%3D__OTP__%26missingStartURL%3D%252Fservices%252Foauth2%252Fauthorize%253Fresponse_type%253Dcode%2526client_id%253D__CONSUMER_KEY__%2526redirect_uri%253Dtestsfdc%25253A%25252F%25252F%25252Fmobilesdk%25252Fdetect%25252Foauth%25252Fdone%2526code_challenge%253D__CODE_CHALLENGE__%26cshc%3D__CSHC__%22%7D".toUri()

        launch<LoginActivity>(
            Intent(
                getApplicationContext(),
                LoginActivity::class.java
            ).apply {
                data = uri
            }).use { activityScenario ->

            activityScenario.onActivity { activity ->

                assertTrue(activity.viewModel.isUsingFrontDoorBridge)
            }
        }
    }

    @Test
    fun viewModelIsUsingFrontDoorBridge_UpdatesOn_onCreateWithQrCodeLoginIntentAndMissingStartUrlClientId() {
        val uri = "app://android/login/qr/?bridgeJson=%7B%22pkce_code_verifier%22%3A%22__CODE_VERIFIER__%22%2C%22frontdoor_bridge_url%22%3A%22https%3A%2F%2Fmobilesdk.my.salesforce.com%2Fsecur%2Ffrontdoor.jsp%3Fotp%3D__OTP__%26startURL%3D%252Fservices%252Foauth2%252Fauthorize%253Fresponse_type%253Dcode%2526missing_client_id%253D__CONSUMER_KEY__%2526redirect_uri%253Dtestsfdc%25253A%25252F%25252F%25252Fmobilesdk%25252Fdetect%25252Foauth%25252Fdone%2526code_challenge%253D__CODE_CHALLENGE__%26cshc%3D__CSHC__%22%7D".toUri()

        launch<LoginActivity>(
            Intent(
                getApplicationContext(),
                LoginActivity::class.java
            ).apply {
                data = uri
            }).use { activityScenario ->

            activityScenario.onActivity { activity ->

                assertTrue(activity.viewModel.isUsingFrontDoorBridge)
            }
        }
    }

//    *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***
//    Build fingerprint: 'google/sdk_gphone64_arm64/emu64a:15/AE3A.240806.043/12960925:userdebug/dev-keys'
//    Revision: '0'
//    ABI: 'arm64'
//    Timestamp: 2026-04-24 14:50:27.342453036-0700
//    Process uptime: 0s
//    Cmdline: com.google.android.bluetooth
//    pid: 8824, tid: 8843, name: bt_stack_manage  >>> com.google.android.bluetooth <<<
//    uid: 1002
//    tagged_addr_ctrl: 0000000000000001 (PR_TAGGED_ADDR_ENABLE)
//    pac_enabled_keys: 000000000000000f (PR_PAC_APIAKEY, PR_PAC_APIBKEY, PR_PAC_APDAKEY, PR_PAC_APDBKEY)
//    signal 6 (SIGABRT), code -1 (SI_QUEUE), fault addr --------
//    Abort message: 'system/gd/stack_manager.cc:57 StartUp: Can't start stack, last instance: starting HciHal'
//    x0  0000000000000000  x1  000000000000228b  x2  0000000000000006  x3  0000007a0c4d87e0
//    x4  73521f3634396262  x5  73521f3634396262  x6  73521f3634396262  x7  7f7f7f7f7f7f7f7f
//    x8  00000000000000f0  x9  0000007cab2eb468  x10 ffffff80fffffb9f  x11 0000000000000000
//    x12 0000007a0c4d76f0  x13 0000000000000059  x14 0000007a0c4d8938  x15 000182e65e501381
//    x16 0000007cab39aff8  x17 0000007cab3851c0  x18 00000078f8de8088  x19 0000000000002278
//    x20 000000000000228b  x21 00000000ffffffff  x22 0000007a1160e180  x23 0000000000000024
//    x24 00000078fb43e6c8  x25 0000007a0c4d8da0  x26 0000007a0c4d8938  x27 0000007a0c4d9a80
//    x28 00000078fbf67d40  x29 0000007a0c4d8860
//    lr  0000007cab3236a4  sp  0000007a0c4d87c0  pc  0000007cab3236d4  pst 0000000000001000
//    22 total frames
//    backtrace:
    @Ignore // ✅ Passes locally. ECJ20260430
    @Test
    fun testWebviewSettings() {
        launch<LoginActivity>(
            Intent(
                getApplicationContext(),
                LoginActivity::class.java
            )
        ).use { activityScenario ->

            activityScenario.onActivity { activity ->
                val defaultWebview = WebView(activity)
                val expectedUserAgent = "${SalesforceSDKManager.getInstance().userAgent} ${defaultWebview.settings.userAgentString}"

                assertEquals(activity.webViewClient, activity.webView.webViewClient)
                assertEquals(activity.webChromeClient, activity.webView.webChromeClient)

                assertTrue(activity.webView.settings.domStorageEnabled)
                assertTrue(activity.webView.settings.javaScriptEnabled)
                assertEquals(expectedUserAgent, activity.webView.settings.userAgentString)
            }
        }
    }

    @Ignore // ✅ Passes locally. ECJ20260430
    @Test
    fun loginActivity_ReloadsWebview_OnResumeWithLoginOptionChanges() {
        // Set loginDevMenuReload to false initially
        SalesforceSDKManager.getInstance().loginDevMenuReload = false

        launch<LoginActivity>(
            Intent(
                getApplicationContext(),
                LoginActivity::class.java
            )
        ).use { activityScenario ->
            // Get the initial login URL
            var initialUrl: String? = null
            activityScenario.onActivity { activity ->
                initialUrl = activity.viewModel.loginUrl.value
            }

            // Pause the activity (simulating going to dev menu)
            activityScenario.moveToState(STARTED)

            // Simulate changing login options in dev menu
            activityScenario.onActivity { _ ->
                SalesforceSDKManager.getInstance().loginDevMenuReload = true
            }

            // Resume the activity
            activityScenario.moveToState(RESUMED)

            // Verify the webview was reloaded (URL should be regenerated)
            activityScenario.onActivity { activity ->
                // The reload flag should be reset to false
                assertFalse(
                    "loginDevMenuReload should be reset to false after reload",
                    SalesforceSDKManager.getInstance().loginDevMenuReload
                )

                // For Web Server Flow, the URL changes each time due to code challenge
                // Verify that reloadWebView was called by checking the URL changed
                val newUrl = activity.viewModel.loginUrl.value
                if (SalesforceSDKManager.getInstance().useWebServerAuthentication) {
                    // Web Server Flow generates a new code challenge each time
                    assertTrue(
                        "Login URL should have changed after reload for Web Server Flow",
                        newUrl != initialUrl
                    )
                }
            }
        }
    }

    // region Salesforce Welcome Discovery

    @Test
    fun loginActivity_startsWscDiscovery_onCreateWithSelectedServer() {

        val activityScenario = launch<LoginActivity>(
            Intent(
                getApplicationContext(),
                LoginActivity::class.java
            )
        )

        activityScenario.onActivity { activity ->

            activity.viewModel.previousPendingServer = PRODUCTION_LOGIN_URL
            assertTrue(activity.switchDefaultOrSalesforceWelcomeDiscoveryLogin(WELCOME_LOGIN_URL.toUri()))

            activity.viewModel.previousPendingServer = WELCOME_LOGIN_URL
            assertTrue(activity.switchDefaultOrSalesforceWelcomeDiscoveryLogin(PRODUCTION_LOGIN_URL.toUri()))
        }
    }

    @Test
    fun userAgent_hasWelcomeDiscoveryFlag_withWelcomeDiscoveryLoginServer() {
        val uri = "https://welcome.salesforce.com/discovery?client_id=aaa&callback_url=bbb&client_version=ccc".toUri()

        launch<LoginActivity>(
            Intent(
                getApplicationContext(),
                LoginActivity::class.java
            ).apply {
                data = uri
            }).use { activityScenario ->

            activityScenario.onActivity { activity ->
                val userAgentString = activity.webView.settings.userAgentString
                val featureFlags = extractFeatureFlags(userAgentString)
                assertTrue(
                    "WD (Welcome Discovery) should be present in $userAgentString",
                    featureFlags.contains(Features.FEATURE_WELCOME_DISCOVERY_LOGIN)
                )
            }
        }
    }

    @Test
    fun userAgent_hasWelcomeDiscoveryFlag_withLoginHostHint() {
        launch<LoginActivity>(
            Intent(
                getApplicationContext(),
                LoginActivity::class.java
            ).apply {
                putExtra(EXTRA_KEY_LOGIN_HOST, "mobilesdk.my.salesforce.com")
            }).use { activityScenario ->

            activityScenario.onActivity { activity ->
                val userAgentString = activity.webView.settings.userAgentString
                val featureFlags = extractFeatureFlags(userAgentString)
                assertTrue(
                    "WD (Welcome Discovery) should be present in $userAgentString",
                    featureFlags.contains(Features.FEATURE_WELCOME_DISCOVERY_LOGIN)
                )
            }
        }
    }

    @Test
    fun userAgent_doesNotHaveWelcomeDiscoveryFlag_withMyDomainLoginServer() {
        val uri = "https://mobilesdk.my.salesforce.com".toUri()

        launch<LoginActivity>(
            Intent(
                getApplicationContext(),
                LoginActivity::class.java
            ).apply {
                data = uri
            }).use { activityScenario ->

            activityScenario.onActivity { activity ->
                val userAgentString = activity.webView.settings.userAgentString
                val featureFlags = extractFeatureFlags(userAgentString)
                assertFalse(
                    "WD (Welcome Discovery) should NOT be present in $userAgentString",
                    featureFlags.contains(Features.FEATURE_WELCOME_DISCOVERY_LOGIN)
                )
            }
        }
    }

    fun extractFeatureFlags(userAgentString: String): List<String> {
        val ftrMatch = Regex("ftr_([^\\s]*)").find(userAgentString)
        assertNotNull("User agent should contain ftr_ field: $userAgentString", ftrMatch)
        val ftrValue = ftrMatch!!.groupValues[1]
        return ftrValue.split(".")
    }
}
