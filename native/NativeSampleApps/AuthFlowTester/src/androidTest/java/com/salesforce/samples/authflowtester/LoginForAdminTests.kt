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
package com.salesforce.samples.authflowtester

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.salesforce.samples.authflowtester.testUtility.AuthFlowTest
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for the "Login for Admins" menu flow.
 *
 * This menu item lives in the login WebView's overflow menu and hands off to a Chrome
 * Custom Tab using the same OAuth authorize URL (always Web Server Flow + PKCE) while
 * keeping the in-app WebView loaded underneath. It is primarily intended for orgs that
 * require a browser-based admin sign-in (e.g., client certificates, SSO) even when the
 * app itself is otherwise configured to use the in-app WebView.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class LoginForAdminTests : AuthFlowTest() {

    /**
     * Login for Admins with the default WebView flow (Web Server Flow) enabled.
     * The custom tab URL and the WebView URL are equivalent, so this exercises the
     * "matches what the WebView would have loaded" hand-off path.
     */
    @Test
    fun testLoginForAdmin_WebServerFlowEnabled() {
        adminLoginAndValidate()
    }

    /**
     * Login for Admins with the WebView configured for User Agent Flow.
     * The custom tab must still use Web Server Flow (code + PKCE) so the OAuth
     * callback can complete via `onNewIntent` -> `completeAdvAuthFlow`, even though
     * the WebView itself would have used the token response type.
     */
    @Test
    fun testLoginForAdmin_WebServerFlowDisabled() {
        adminLoginAndValidate(useWebServerFlow = false)
    }
}
