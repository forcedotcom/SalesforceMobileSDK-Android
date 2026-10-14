/*
 * Copyright (c) 2026-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
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
package com.salesforce.androidsdk.phonegap.ui

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.phonegap.app.SalesforceHybridSDKManager
import com.salesforce.androidsdk.ui.LoginActivity
import com.salesforce.androidsdk.phonegap.ui.SalesforceHybridTestActivity
import com.salesforce.androidsdk.rest.ApiVersionStrings
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestResponse
import com.salesforce.androidsdk.util.AuthConfigUtil
import org.apache.cordova.AllowListPlugin
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response

@RunWith(AndroidJUnit4::class)
@SmallTest
class SalesforceRedirectDestinationPolicyTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun initializeHybridSdk() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val appContext = instrumentation.targetContext.applicationContext
            if (!SalesforceSDKManager.hasInstance()) {
                instrumentation.runOnMainSync {
                    SalesforceHybridSDKManager.initHybrid(
                        appContext,
                        SalesforceHybridTestActivity::class.java,
                        LoginActivity::class.java,
                    )
                }
            }
            // This intentionally fails rather than replacing an already initialized non-hybrid SDK manager.
            SalesforceHybridSDKManager.getInstance()
        }
    }

    private val context: Context
        get() = SalesforceSDKManager.getInstance().appContext
    private val userAccountManager by lazy { UserAccountManager.getInstance() }
    private val createdAccounts = mutableListOf<Account>()

    @Before
    fun setUp() {
        removeStaleTestAccounts()
    }

    @After
    fun tearDown() {
        val accountManager = AccountManager.get(context)
        createdAccounts.forEach(accountManager::removeAccountExplicitly)
        createdAccounts.clear()
        userAccountManager.clearStoredCurrentUserInfo()
    }

    @Test
    fun productionResolverUsesCordovaAllowlistAndRejectsSalesforceLookalikes() {
        val allowList = AllowListPlugin(context)
        val cordovaPolicy: (String) -> Boolean = { allowList.shouldAllowNavigation(it) == true }
        val clientInfo = clientInfo("allowlist")

        listOf(
            "https://acme.my.salesforce.com/home",
            "https://acme--c.visualforce.com/apex/AccountPage",
            "https://hybrid-test.example.com/start",
        ).forEach { destination ->
            assertEquals(destination, resolveAllowedRedirectDestination(destination, clientInfo, null, cordovaPolicy))
        }

        val lookalike = resolveAllowedRedirectDestination(
            "https://acme.my.salesforce.com.attacker.example/landing",
            clientInfo,
            null,
            cordovaPolicy,
        )
        assertNull(lookalike)

        val pathScoped = resolveAllowedRedirectDestination(
            "https://path-scoped.example.com/allowed/page",
            null,
            null,
            cordovaPolicy,
        )
        assertEquals("https://path-scoped.example.com/allowed/page", pathScoped)

        listOf(
            "https://path-scoped.example.com/allowed/../outside",
            "https://path-scoped.example.com/allowed/%2e%2e/outside",
        ).forEach { traversal ->
            val result = resolveAllowedRedirectDestination(traversal, null, null, cordovaPolicy)
            assertNull("Path traversal must not escape Cordova's path-scoped allowlist: $traversal", result)
        }
    }

    @Test
    fun productionResolverAllowsOnlyExactConfiguredStartPageFallback() {
        val configuredPage = "https://custom.example.com/mobile/start.html"
        val exact = resolveAllowedRedirectDestination(configuredPage, null, configuredPage) { false }
        assertEquals(configuredPage, exact)

        val fallback = resolveAllowedRedirectDestination(
            "https://attacker.example/return",
            null,
            configuredPage,
        ) { false }
        assertEquals(configuredPage, fallback)

        val relativeFallback = resolveAllowedRedirectDestination(
            "/mobile/start.html",
            clientInfo("fallback"),
            "https://instance-fallback.example.com/mobile/start.html",
        ) { false }
        assertEquals("https://instance-fallback.example.com/mobile/start.html", relativeFallback)

        val appendedQuery = resolveAllowedRedirectDestination(
            "$configuredPage?unexpected=1#fragment",
            null,
            configuredPage,
        ) { false }
        assertEquals(configuredPage, appendedQuery)

        val noCordovaPolicy = resolveAllowedRedirectDestination(configuredPage, null, configuredPage, null)
        assertEquals(configuredPage, noCordovaPolicy)
    }

    @Test
    fun productionResolverRejectsMalformedAndUnsupportedDestinationsEvenIfCordovaAllowsThem() {
        val allowEverything: (String) -> Boolean = { true }
        listOf(
            "//attacker.example/landing",
            "javascript:alert(1)",
            "file:///etc/passwd",
            "intent://attacker.example/#Intent;scheme=https;end",
            "https://[invalid-host/landing",
            "https:///missing-host",
        ).forEach { destination ->
            val result = resolveAllowedRedirectDestination(destination, null, null, allowEverything)
            assertNull("Should reject $destination", result)
        }
    }

    @Test
    fun refreshLoadsOnlyTheDestinationApprovedByProductionResolver() {
        val user = createUser("refresh-approved", "https://acme.my.salesforce.com")
        persist(user)
        val activity = createActivity()
        assertTrue(activity.bindCurrentUserClient() != null)
        activity.redirectNavigationPolicy = {
            val allowList = AllowListPlugin(context)
            val cordovaAllows: (String) -> Boolean = { url -> allowList.shouldAllowNavigation(url) == true }
            cordovaAllows
        }

        var callback: RestClient.AsyncRequestCallback? = null
        activity.sendRefreshRequest = { _, requestCallback -> callback = requestCallback }
        activity.refresh("https://acme.my.salesforce.com/home")
        assertTrue("An allowed redirect should trigger the refresh request", callback != null)

        onMain { callback!!.onSuccess(successfulRequest(), successfulResponse()) }
        assertEquals(listOf("https://acme.my.salesforce.com/home"), activity.loadedUrls)
    }

    @Test
    fun rejectedRefreshDoesNotSendRequestOrLoadUrl() {
        val user = createUser("refresh-rejected", "https://instance-refresh-rejected.example.com")
        persist(user)
        val activity = createActivity()
        assertTrue(activity.bindCurrentUserClient() != null)
        activity.redirectNavigationPolicy = {
            val allowList = AllowListPlugin(context)
            val cordovaAllows: (String) -> Boolean = { url -> allowList.shouldAllowNavigation(url) == true }
            cordovaAllows
        }
                var requestCount = 0
        activity.sendRefreshRequest = { _, _ -> requestCount++ }

        activity.refresh("https://instance-refresh-rejected.example.com.attacker.example/landing")

        assertEquals(0, requestCount)
        assertTrue(activity.loadedUrls.isEmpty())
    }

    @Test
    fun refreshCallbackDoesNotLoadAfterCurrentUserChanges() {
        val userA = createUser("refresh-stale-a", "https://acme.my.salesforce.com")
        val userB = createUser("refresh-stale-b", "https://other.my.salesforce.com")
        persist(userA)
        val activity = createActivity()
        assertTrue(activity.bindCurrentUserClient() != null)
        activity.redirectNavigationPolicy = { { true } }
        var callback: RestClient.AsyncRequestCallback? = null
        activity.sendRefreshRequest = { _, requestCallback -> callback = requestCallback }
        activity.refresh("https://acme.my.salesforce.com/home")
        assertTrue(callback != null)

        persist(userB)
        onMain { callback!!.onSuccess(successfulRequest(), successfulResponse()) }

        assertTrue("A stale user callback must not load its captured destination", activity.loadedUrls.isEmpty())
    }

    @Test
    fun refreshWithoutRestClientStartsAuthenticationWithoutLoadingDestination() {
        val activity = createActivity()
        var authenticationCount = 0
        var requestCount = 0
        activity.authenticateForRefresh = { authenticationCount++ }
        activity.sendRefreshRequest = { _, _ -> requestCount++ }

        activity.refresh("https://attacker.example/landing")

        assertEquals(1, authenticationCount)
        assertEquals(0, requestCount)
        assertTrue(activity.loadedUrls.isEmpty())
    }

    @Test
    fun refreshRevalidatesDestinationBeforeLoadingAfterPolicyChanges() {
        val user = createUser("refresh-policy-change", "https://acme.my.salesforce.com")
        persist(user)
        val activity = createActivity()
        assertTrue(activity.bindCurrentUserClient() != null)
        var policyAllows = true
        activity.redirectNavigationPolicy = { { policyAllows } }
        var callback: RestClient.AsyncRequestCallback? = null
        activity.sendRefreshRequest = { _, requestCallback -> callback = requestCallback }
        activity.refresh("https://acme.my.salesforce.com/home")
        assertTrue(callback != null)

        policyAllows = false
        onMain { callback!!.onSuccess(successfulRequest(), successfulResponse()) }

        assertTrue("A destination rejected at callback time must not reach loadUrl", activity.loadedUrls.isEmpty())
    }

    @Test
    fun refreshLoadsExactConfiguredStartPageWhenOriginalDestinationIsRejected() {
        val user = createUser("refresh-fallback", "https://instance-refresh-fallback.example.com")
        persist(user)
        val activity = createActivity()
        assertTrue(activity.bindCurrentUserClient() != null)
        activity.redirectNavigationPolicy = { { false } }
        val configuredStartPage = "https://custom.example.com/mobile/start.html"
        setStartPage(activity, configuredStartPage)
        var callback: RestClient.AsyncRequestCallback? = null
        activity.sendRefreshRequest = { _, requestCallback -> callback = requestCallback }
        activity.refresh("https://attacker.example/return")
        assertTrue(callback != null)

        onMain { callback!!.onSuccess(successfulRequest(), successfulResponse()) }

        assertEquals(listOf(configuredStartPage), activity.loadedUrls)
    }

    @Test
    fun refreshCanonicalizesConfiguredStartPageWhilePreservingQueryAndRouteFragment() {
        val user = createUser("refresh-fallback-spa", "https://instance-refresh-fallback-spa.example.com")
        persist(user)
        val activity = createActivity()
        assertTrue(activity.bindCurrentUserClient() != null)
        activity.redirectNavigationPolicy = { { false } }
        val configuredStartPage = "https://CUSTOM.example.com/mobile/start.html?tab=Accounts#/record/123"
        setStartPage(activity, configuredStartPage)
        var callback: RestClient.AsyncRequestCallback? = null
        activity.sendRefreshRequest = { _, requestCallback -> callback = requestCallback }
        activity.refresh("https://attacker.example/return")
        assertTrue(callback != null)

        onMain { callback!!.onSuccess(successfulRequest(), successfulResponse()) }

        assertEquals(
            listOf("https://custom.example.com/mobile/start.html?tab=Accounts#/record/123"),
            activity.loadedUrls,
        )
    }

    @Test
    fun relativeVisualforceReturnLoadsResolvedUrlThroughRealCordovaPolicy() {
        val user = createUser("refresh-relative-vf", "https://acme.my.salesforce.com")
        persist(user)
        val activity = createActivity()
        assertTrue(activity.bindCurrentUserClient() != null)
        activity.redirectNavigationPolicy = {
            val allowList = AllowListPlugin(context)
            val cordovaAllows: (String) -> Boolean = { url -> allowList.shouldAllowNavigation(url) == true }
            cordovaAllows
        }
        var callback: RestClient.AsyncRequestCallback? = null
        activity.sendRefreshRequest = { _, requestCallback -> callback = requestCallback }
        val destination = "/apex/AccountPage"
        val redirect = "https://acme.my.salesforce.com/redirect?ec=302&retURL=${Uri.encode(destination)}"

        assertTrue(SalesforceWebViewClientHelper.shouldOverrideUrlLoading(activity, null, redirect))
        assertTrue(callback != null)
        onMain { callback!!.onSuccess(successfulRequest(), successfulResponse()) }

        assertEquals(listOf("https://acme.my.salesforce.com/apex/AccountPage"), activity.loadedUrls)
    }

    @Test
    fun ec301And302ReturnUrlsAreParsedThenRejectedByActualRefreshRoute() {
        val user = createUser("refresh-ec", "https://instance-refresh-ec.example.com")
        persist(user)
        val activity = createActivity()
        assertTrue(activity.bindCurrentUserClient() != null)
        activity.redirectNavigationPolicy = {
            val allowList = AllowListPlugin(context)
            val cordovaAllows: (String) -> Boolean = { url -> allowList.shouldAllowNavigation(url) == true }
            cordovaAllows
        }
        var requestCount = 0
        activity.sendRefreshRequest = { _, _ -> requestCount++ }
        val externalUrl = "https://attacker.example/landing"

        listOf("301", "302").forEach { code ->
            listOf("retURL", "startURL").forEach { key ->
                val redirect = "https://instance-refresh-ec.example.com/redirect?ec=$code&$key=${Uri.encode(externalUrl)}"
                assertEquals(externalUrl, SalesforceWebViewClientHelper.isLoginRedirect(activity, redirect))
                assertTrue(SalesforceWebViewClientHelper.shouldOverrideUrlLoading(activity, null, redirect))
            }
        }

        assertEquals("Untrusted destinations extracted from redirect codes must not reach refresh", 0, requestCount)
        assertTrue(activity.loadedUrls.isEmpty())
    }

    @Test
    fun hybridSessionAndConfiguredSsoRedirectsUseSameValidatedRefreshRoute() {
        val user = createUser("refresh-sso", "https://instance-refresh-sso.example.com")
        persist(user)
        val activity = createActivity()
        assertTrue(activity.bindCurrentUserClient() != null)
        activity.redirectNavigationPolicy = {
            val allowList = AllowListPlugin(context)
            val cordovaAllows: (String) -> Boolean = { url -> allowList.shouldAllowNavigation(url) == true }
            cordovaAllows
        }
        var requestCount = 0
        activity.sendRefreshRequest = { _, _ -> requestCount++ }
        val externalUrl = "https://attacker.example/landing"

        val sessionRedirect = "https://instance-refresh-sso.example.com/services/identity/mobileauthredirect?retURL=${Uri.encode(externalUrl)}"
        assertEquals(externalUrl, SalesforceWebViewClientHelper.isLoginRedirect(activity, sessionRedirect))
        assertTrue(SalesforceWebViewClientHelper.shouldOverrideUrlLoading(activity, null, sessionRedirect))

        val ssoUrl = configureSso(activity)
        val ssoRedirect = "$ssoUrl&retURL=${Uri.encode(externalUrl)}"
        assertEquals(externalUrl, SalesforceWebViewClientHelper.isLoginRedirect(activity, ssoRedirect))
        assertTrue(SalesforceWebViewClientHelper.shouldOverrideUrlLoading(activity, null, ssoRedirect))

        assertEquals(0, requestCount)
        assertTrue(activity.loadedUrls.isEmpty())
    }

    @Test
    fun hybridSessionAndConfiguredSsoCanLoadCordovaApprovedReturns() {
        val user = createUser("refresh-allowed-sso", "https://acme.my.salesforce.com")
        persist(user)
        val activity = createActivity()
        assertTrue(activity.bindCurrentUserClient() != null)
        activity.redirectNavigationPolicy = {
            val allowList = AllowListPlugin(context)
            val cordovaAllows: (String) -> Boolean = { url -> allowList.shouldAllowNavigation(url) == true }
            cordovaAllows
        }
        val ssoUrl = configureSso(activity)
        var callback: RestClient.AsyncRequestCallback? = null
        activity.sendRefreshRequest = { _, requestCallback -> callback = requestCallback }

        val sessionReturn = "/apex/SessionPage"
        val sessionRedirect = "https://acme.my.salesforce.com/services/identity/mobileauthredirect?retURL=${Uri.encode(sessionReturn)}"
        assertTrue(SalesforceWebViewClientHelper.shouldOverrideUrlLoading(activity, null, sessionRedirect))
        assertTrue(callback != null)
        onMain { callback!!.onSuccess(successfulRequest(), successfulResponse()) }

        val ssoReturn = "https://acme.my.salesforce.com/apex/SsoPage"
        val ssoRedirect = "$ssoUrl&retURL=${Uri.encode(ssoReturn)}"
        assertTrue(SalesforceWebViewClientHelper.shouldOverrideUrlLoading(activity, null, ssoRedirect))
        assertTrue(callback != null)
        onMain { callback!!.onSuccess(successfulRequest(), successfulResponse()) }

        assertEquals(
            listOf(
                "https://acme.my.salesforce.com/apex/SessionPage",
                ssoReturn,
            ),
            activity.loadedUrls,
        )
    }

    @Test
    fun malformedEcIsOrdinaryNavigationAndDoesNotStartRefresh() {
        val activity = createActivity()
        var requestCount = 0
        activity.sendRefreshRequest = { _, _ -> requestCount++ }
        val url = "https://instance.example.com/?ec=not-a-number&retURL=%2Fhome"

        assertNull(SalesforceWebViewClientHelper.isLoginRedirect(activity, url))
        assertFalse(SalesforceWebViewClientHelper.shouldOverrideUrlLoading(activity, null, url))
        assertEquals(0, requestCount)
    }

    private fun clientInfo(suffix: String): RestClient.ClientInfo {
        val user = createUser("$suffix-client-info", "https://instance-$suffix.example.com")
        persist(user)
        return ClientManager(context, user).peekRestClient()!!.clientInfo
    }

    private fun createUser(suffix: String, instanceUrl: String) = UserAccountBuilder.getInstance()
        .accountName("redirect-test-account-$suffix")
        .username("redirect-test-$suffix@example.com")
        .refreshToken("redirect-refresh-$suffix")
        .authToken("redirect-auth-$suffix")
        .instanceServer(instanceUrl)
        .loginServer("https://login.example.com")
        .idUrl("https://login.example.com/id/org-$suffix/user-$suffix")
        .clientId("redirect-client-$suffix")
        .orgId("redirect-org-$suffix")
        .userId("redirect-user-$suffix")
        .nativeLogin(false)
        .build()

    private fun persist(user: UserAccount) {
        userAccountManager.createAccount(user)
        createdAccounts += requireNotNull(userAccountManager.buildAccount(user))
    }

    private fun createActivity(): RedirectProbeActivity {
        var activity: RedirectProbeActivity? = null
        onMain { activity = RedirectProbeActivity() }
        return requireNotNull(activity)
    }

    private fun onMain(block: () -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { block() }
        instrumentation.waitForIdleSync()
    }

    private fun successfulRequest(): RestRequest = RestRequest.getCheapRequest(ApiVersionStrings.VERSION_NUMBER)

    private fun successfulResponse(): RestResponse {
        val request = Request.Builder().url("https://unit-test.example.com/").build()
        return RestResponse(
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build(),
        )
    }

    private fun removeStaleTestAccounts() {
        val sdkManager = SalesforceSDKManager.getInstance()
        val accountManager = AccountManager.get(context)
        accountManager.getAccountsByType(sdkManager.accountType)
            .filter { it.name.startsWith("redirect-test-account-") }
            .forEach(accountManager::removeAccountExplicitly)
        userAccountManager.clearStoredCurrentUserInfo()
    }

    private class RedirectProbeActivity : SalesforceDroidGapActivity() {
        val loadedUrls = mutableListOf<String>()

        override fun loadUrl(url: String) {
            loadedUrls += url
        }
    }

    private fun configureSso(activity: SalesforceDroidGapActivity): String {
        val ssoUrl = "https://idp.example.com/saml/login?tenant=sample"
        val authConfig = AuthConfigUtil.MyDomainAuthConfig(
            JSONObject().put("SamlProviders", JSONArray().put(JSONObject().put("SsoUrl", ssoUrl))),
        )
        SalesforceDroidGapActivity::class.java.getDeclaredField("authConfig").apply {
            isAccessible = true
            set(activity, authConfig)
        }
        return ssoUrl
    }

    private fun setStartPage(activity: SalesforceDroidGapActivity, startPage: String) {
        val bootConfig = BootConfig::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        BootConfig::class.java.getDeclaredField("startPage").apply {
            isAccessible = true
            set(bootConfig, startPage)
        }
        SalesforceDroidGapActivity::class.java.getDeclaredField("bootConfig").apply {
            isAccessible = true
            set(activity, bootConfig)
        }
    }
}
