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
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.ui.LoginActivity.Companion.EXTRA_KEY_LOGIN_HINT
import com.salesforce.androidsdk.ui.LoginActivity.Companion.EXTRA_KEY_LOGIN_HOST
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginActivityTest {

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
                assertEquals("https://mobilesdk.my.salesforce.com/secur/frontdoor.jsp?otp=__OTP__&startURL=%2Fservices%2Foauth2%2Fauthorize%3Fresponse_type%3Dcode%26client_id%3D__CONSUMER_KEY__%26redirect_uri%3Dtestsfdc%253A%252F%252F%252Fmobilesdk%252Fdetect%252Foauth%252Fdone%26code_challenge%3D__CODE_CHALLENGE__&cshc=__CSHC__", activity.viewModel.loginUrl.value)
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
}
