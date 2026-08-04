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
package com.salesforce.androidsdk.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.app.Features.FEATURE_BROWSER_LOGIN_FOR_ADMIN
import com.salesforce.androidsdk.app.Features.FEATURE_BROWSER_LOGIN_FORCE_FLAG
import com.salesforce.androidsdk.app.Features.FEATURE_BROWSER_LOGIN_SERVER_AUTH_CONFIG
import com.salesforce.androidsdk.app.Features.FEATURE_LOGIN_SERVER_CUSTOM
import com.salesforce.androidsdk.app.Features.FEATURE_LOGIN_SERVER_MY_DOMAIN
import com.salesforce.androidsdk.app.Features.FEATURE_LOGIN_SERVER_PRODUCTION
import com.salesforce.androidsdk.app.Features.FEATURE_LOGIN_SERVER_SANDBOX
import com.salesforce.androidsdk.app.Features.FEATURE_LOGIN_SERVER_WELCOME_DISCOVERY
import com.salesforce.androidsdk.config.LoginServerManager
import com.salesforce.androidsdk.ui.LoginActivity
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit tests for the B-marker (browser login reason) and L-marker (login server type)
 * telemetry selection logic in [LoginActivity].
 *
 * The logic under test lives in [LoginActivity.selectBMarker] and [LoginActivity.selectLMarker],
 * which are `internal` helpers extracted for testability and annotated `@VisibleForTesting`.
 * We call them via a relaxed mockk and `callOriginal()` so the real logic executes without
 * needing a running Android Activity context.
 *
 * L-marker mapping:
 *   L1 = Production server
 *   L2 = Sandbox server
 *   L3 = welcome.salesforce.com (Welcome Discovery flow)
 *   L4 = My Domain (host ending in .my.salesforce.com)
 *   L5 = Everything else (custom)
 *
 * B-marker mapping (Android):
 *   B1 = Server auth config (fallthrough)
 *   B2 = MDM — defined but never selected on Android (MDM forces cert auth, a different path)
 *   B3 = Admin Custom Tab
 *   B4 = Force-advanced-auth flag
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class BrowserLoginTelemetryTest {

    /** A relaxed mock of LoginActivity; individual tests call `callOriginal()` on the method under test. */
    private val activity: LoginActivity = mockk(relaxed = true)

    @After
    fun tearDown() {
        unmockkAll()
    }

    // region B-marker tests

    @Test
    fun test_givenNonBrowserLogin_whenSelectBMarker_thenReturnsNull() {
        every { activity.selectBMarker(any(), any(), any(), any()) } answers { callOriginal() }

        val result = activity.selectBMarker(
            completedViaBrowserTab = false,
            completedViaAdminCustomTab = false,
            isMdmForced = false,
            forceAdvancedAuth = false,
        )
        assertNull("Non-browser login should yield no B-marker", result)
    }

    @Test
    fun test_givenBrowserLoginViaServerAuthConfig_whenSelectBMarker_thenB1Returned() {
        every { activity.selectBMarker(any(), any(), any(), any()) } answers { callOriginal() }

        // B1: browser tab, not admin, not force-flag (MDM ignored on Android)
        val result = activity.selectBMarker(
            completedViaBrowserTab = true,
            completedViaAdminCustomTab = false,
            isMdmForced = false,
            forceAdvancedAuth = false,
        )
        assertEquals("Server auth-config browser login should yield B1",
            FEATURE_BROWSER_LOGIN_SERVER_AUTH_CONFIG, result)
    }

    @Test
    fun test_givenBrowserLoginViaMDM_whenSelectBMarker_thenB1ReturnedNotB2() {
        every { activity.selectBMarker(any(), any(), any(), any()) } answers { callOriginal() }

        // On Android, MDM forces cert auth (different code path — completedViaBrowserTab never
        // becomes true in that flow). When isMdmForced=true is passed, it is ignored and B1
        // is returned as the fallthrough because no real Android MDM-browser-login signal exists.
        val result = activity.selectBMarker(
            completedViaBrowserTab = true,
            completedViaAdminCustomTab = false,
            isMdmForced = true,
            forceAdvancedAuth = false,
        )
        assertEquals("MDM flag is ignored on Android; should fall through to B1",
            FEATURE_BROWSER_LOGIN_SERVER_AUTH_CONFIG, result)
    }

    @Test
    fun test_givenAdminCustomTab_whenSelectBMarker_thenB3Returned() {
        every { activity.selectBMarker(any(), any(), any(), any()) } answers { callOriginal() }

        // B3: browser tab via admin launcher
        val result = activity.selectBMarker(
            completedViaBrowserTab = true,
            completedViaAdminCustomTab = true,
            isMdmForced = false,
            forceAdvancedAuth = false,
        )
        assertEquals("Admin custom tab login should yield B3",
            FEATURE_BROWSER_LOGIN_FOR_ADMIN, result)
    }

    @Test
    fun test_givenBrowserLoginViaForceFlag_whenSelectBMarker_thenB4Returned() {
        every { activity.selectBMarker(any(), any(), any(), any()) } answers { callOriginal() }

        // B4: browser tab, not admin, force flag ON
        val result = activity.selectBMarker(
            completedViaBrowserTab = true,
            completedViaAdminCustomTab = false,
            isMdmForced = false,
            forceAdvancedAuth = true,
        )
        assertEquals("Force-advanced-auth browser login should yield B4",
            FEATURE_BROWSER_LOGIN_FORCE_FLAG, result)
    }

    @Test
    fun test_givenAdminTabAndForceFlag_whenSelectBMarker_thenB3Wins() {
        every { activity.selectBMarker(any(), any(), any(), any()) } answers { callOriginal() }

        // Priority on Android: B3 > B4 > B1
        val result = activity.selectBMarker(
            completedViaBrowserTab = true,
            completedViaAdminCustomTab = true,
            isMdmForced = false,
            forceAdvancedAuth = true,
        )
        assertEquals("Admin tab should take highest priority (B3)",
            FEATURE_BROWSER_LOGIN_FOR_ADMIN, result)
    }

    @Test
    fun test_givenForceFlagAndMdm_whenSelectBMarker_thenB4Wins() {
        every { activity.selectBMarker(any(), any(), any(), any()) } answers { callOriginal() }

        // On Android MDM is ignored; force flag wins over B1 fallthrough
        val result = activity.selectBMarker(
            completedViaBrowserTab = true,
            completedViaAdminCustomTab = false,
            isMdmForced = true,
            forceAdvancedAuth = true,
        )
        assertEquals("Force flag should win over ignored MDM signal (B4)",
            FEATURE_BROWSER_LOGIN_FORCE_FLAG, result)
    }

    // endregion
    // region L-marker tests

    @Test
    fun test_givenProductionServer_whenSelectLMarker_thenL1Returned() {
        every { activity.selectLMarker(any(), any()) } answers { callOriginal() }

        val result = activity.selectLMarker(
            usedWelcomeDiscovery = false,
            loginServerUrl = LoginServerManager.PRODUCTION_LOGIN_URL,
        )
        assertEquals("Production login server should yield L1",
            FEATURE_LOGIN_SERVER_PRODUCTION, result)
    }

    @Test
    fun test_givenSandboxServer_whenSelectLMarker_thenL2Returned() {
        every { activity.selectLMarker(any(), any()) } answers { callOriginal() }

        val result = activity.selectLMarker(
            usedWelcomeDiscovery = false,
            loginServerUrl = LoginServerManager.SANDBOX_LOGIN_URL,
        )
        assertEquals("Sandbox login server should yield L2",
            FEATURE_LOGIN_SERVER_SANDBOX, result)
    }

    @Test
    fun test_givenWelcomeDiscovery_whenSelectLMarker_thenL3Returned() {
        every { activity.selectLMarker(any(), any()) } answers { callOriginal() }

        // L3: Welcome Discovery flow — WD flag takes precedence regardless of the resolved URL
        val result = activity.selectLMarker(
            usedWelcomeDiscovery = true,
            loginServerUrl = "https://myorg.my.salesforce.com",
        )
        assertEquals("Welcome Discovery should yield L3",
            FEATURE_LOGIN_SERVER_WELCOME_DISCOVERY, result)
    }

    @Test
    fun test_givenWelcomeDiscoveryWithProductionUrl_whenSelectLMarker_thenL3Returned() {
        every { activity.selectLMarker(any(), any()) } answers { callOriginal() }

        // WD flag wins even when the resolved URL is production
        val result = activity.selectLMarker(
            usedWelcomeDiscovery = true,
            loginServerUrl = LoginServerManager.PRODUCTION_LOGIN_URL,
        )
        assertEquals("WD flag should override production URL and yield L3",
            FEATURE_LOGIN_SERVER_WELCOME_DISCOVERY, result)
    }

    @Test
    fun test_givenMyDomainServer_whenSelectLMarker_thenL4Returned() {
        every { activity.selectLMarker(any(), any()) } answers { callOriginal() }

        // L4: host ends with .my.salesforce.com
        val result = activity.selectLMarker(
            usedWelcomeDiscovery = false,
            loginServerUrl = "https://myorg.my.salesforce.com",
        )
        assertEquals("My Domain login server should yield L4",
            FEATURE_LOGIN_SERVER_MY_DOMAIN, result)
    }

    @Test
    fun test_givenMyDomainSandboxServer_whenSelectLMarker_thenL4Returned() {
        every { activity.selectLMarker(any(), any()) } answers { callOriginal() }

        // Sandbox My Domain also ends with .my.salesforce.com (L4, not L2, because URL != sandbox constant)
        val result = activity.selectLMarker(
            usedWelcomeDiscovery = false,
            loginServerUrl = "https://myorg.sandbox.my.salesforce.com",
        )
        assertEquals("My Domain sandbox server should yield L4",
            FEATURE_LOGIN_SERVER_MY_DOMAIN, result)
    }

    @Test
    fun test_givenWelcomeLoginUrl_whenSelectLMarker_thenL5Returned() {
        every { activity.selectLMarker(any(), any()) } answers { callOriginal() }

        // The WD URL itself: when usedWelcomeDiscovery=false it falls to L5 (custom)
        val result = activity.selectLMarker(
            usedWelcomeDiscovery = false,
            loginServerUrl = LoginServerManager.WELCOME_LOGIN_URL,
        )
        assertEquals("WD URL without WD flag set should yield L5 (custom)",
            FEATURE_LOGIN_SERVER_CUSTOM, result)
    }

    @Test
    fun test_givenCustomServer_whenSelectLMarker_thenL5Returned() {
        every { activity.selectLMarker(any(), any()) } answers { callOriginal() }

        val result = activity.selectLMarker(
            usedWelcomeDiscovery = false,
            loginServerUrl = "https://custom.example.com",
        )
        assertEquals("Custom server should yield L5",
            FEATURE_LOGIN_SERVER_CUSTOM, result)
    }

    @Test
    fun test_givenExactlyOneL_whenProductionServer_thenOnlyL1Selected() {
        every { activity.selectLMarker(any(), any()) } answers { callOriginal() }

        val allLMarkers = listOf(
            FEATURE_LOGIN_SERVER_PRODUCTION,
            FEATURE_LOGIN_SERVER_SANDBOX,
            FEATURE_LOGIN_SERVER_WELCOME_DISCOVERY,
            FEATURE_LOGIN_SERVER_MY_DOMAIN,
            FEATURE_LOGIN_SERVER_CUSTOM,
        )
        val selected = activity.selectLMarker(
            usedWelcomeDiscovery = false,
            loginServerUrl = LoginServerManager.PRODUCTION_LOGIN_URL,
        )
        val selectedCount = allLMarkers.count { it == selected }
        assertEquals("Exactly one L marker should be selected", 1, selectedCount)
        assertEquals("The selected marker should be L1", FEATURE_LOGIN_SERVER_PRODUCTION, selected)
    }

    // endregion
}
