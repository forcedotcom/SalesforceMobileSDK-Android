/*
 * Copyright (c) 2026-present, salesforce.com, inc.
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
package com.salesforce.samples.authflowtester.pageObjects

import android.util.Log
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig.REGULAR_AUTH
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig.ADVANCED_AUTH
import com.salesforce.androidsdk.R as sdkR

private const val TAG = "AuthorizationPageObject"
private const val MAX_RETRIES = 5

/**
 * Handles the OAuth authorization "Allow" button that may appear
 * after login or token migration.
 */
class AuthorizationPageObject(composeTestRule: ComposeTestRule) : BasePageObject(composeTestRule) {

    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val allowButton = device.findObject(
        UiSelector().className("android.widget.Button").textContains("Allow")
    )

    /**
     * After login: the authorization WebView is expected.  Scrolls and
     * polls for the Allow button.  Returns early if the main app UI
     * appears (approval was auto-granted or previously remembered).
     */
    fun tapAllowAfterLogin(knownLoginHostConfig: KnownLoginHostConfig) {
        // Let the WebView redirect to authorization page.
        Thread.sleep(TIMEOUT_MS * 2)

        when(knownLoginHostConfig) {
            REGULAR_AUTH -> tapAllowInWebView()
            ADVANCED_AUTH -> {
                dismissSavePasswordDialog()
                tapAllowInCustomTab()
            }
        }
    }

    private fun dismissSavePasswordDialog() {
        val infoBar = device.findObject(
            UiSelector().resourceId("com.android.chrome:id/infobar_message")
        )
        val neverButton = device.findObject(
            UiSelector().resourceId("com.android.chrome:id/button_secondary")
        )
        infoBar.waitForExists(TIMEOUT_MS)
        if (neverButton.waitForExists(TIMEOUT_MS)) {
            neverButton.click()
        }
    }

    /** Scrolls within the Custom Tab to find and tap Allow. */
    private fun tapAllowInCustomTab() {
        val app = AuthFlowTesterPageObject(composeTestRule)
        swipeUp()

        repeat(MAX_RETRIES) {
            if (app.isAppLoaded()) {
                Log.i(TAG, "Left login screen — no approval needed.")
                return
            }

            if (allowButton.waitForExists(TIMEOUT_MS * 2)) {
                allowButton.click()
                Log.i(TAG, "Tapped Allow after login.")
                return
            }
        }
    }

    /** Original flow: scrolls and polls for Allow in the in-app WebView. */
    private fun tapAllowInWebView() {
        swipeUp()

        repeat(MAX_RETRIES) {
            if (!loginActivityExists()) {
                Log.i(TAG, "Left login screen — no approval needed.")
                return
            }

            if (allowButton.waitForExists(TIMEOUT_MS * 2)) {
                allowButton.click()
                Log.i(TAG, "Tapped Allow after login.")
                return
            }
        }
        Log.w(TAG, "Allow button not found after login within retry limit.")
    }

    /**
     * After migration: approval may or may not be required.  If no
     * visible WebView appeared, returns immediately.  Otherwise scrolls
     * and taps Allow.
     */
    fun tapAllowAfterMigration() {
        // Wait for the page to load, swipe, then poll for the Allow button.
        allowButton.waitForExists(TIMEOUT_MS * 5)
        swipeUp()

        repeat(MAX_RETRIES) {
            // WebView dismissed means approval completed without us clicking
            val webView = device.findObject(UiSelector().className("android.webkit.WebView"))
            if (!webView.exists()) {
                Log.i(TAG, "WebView gone — approval no longer needed.")
                return
            }

            if (allowButton.waitForExists(TIMEOUT_MS * 2)) {
                allowButton.click()
                Log.i(TAG, "Tapped Allow after migration.")
                return
            }
        }
        Log.w(TAG, "Migration approval check completed without action.")
    }

    /** Swipe up from the bottom half of the screen. */
    private fun swipeUp() =
        with(device) {
            swipe(
                /* startX = */ displayWidth / 2,
                /* startY = */ displayHeight * 3 / 4,
                /* endX = */ displayWidth / 2,
                /* endY = */ displayHeight / 4,
                /* steps = */ 30,
            )
        }

    /** True when the "More Options" button is in the LoginActivity top bar. */
    private fun loginActivityExists(): Boolean =
        try {
            composeTestRule
                .onAllNodesWithContentDescription(getString(sdkR.string.sf__more_options))
                .fetchSemanticsNodes()
                .isNotEmpty()
        } catch (_: Throwable) {
            false
        }
}
