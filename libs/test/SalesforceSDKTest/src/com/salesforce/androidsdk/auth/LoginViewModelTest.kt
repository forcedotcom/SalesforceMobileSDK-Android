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
package com.salesforce.androidsdk.auth

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.R.string.oauth_display_type
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.OAuth2.getFrontdoorUrl
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.security.SalesforceKeyGenerator.getSHA256Hash
import com.salesforce.androidsdk.ui.LoginViewModel
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI

private const val FAKE_SERVER_URL = "shouldMatchNothing.salesforce.com"
private const val FAKE_JWT = "1234"
private const val FAKE_JWT_FLOW_AUTH = "5678"

@RunWith(AndroidJUnit4::class)
class LoginViewModelTest {
    @get:Rule
    val instantExecutorRule: InstantTaskExecutorRule = InstantTaskExecutorRule()

    private val context = InstrumentationRegistry.getInstrumentation().context
    private val bootConfig = BootConfig.getBootConfig(context)
    private val viewModel = LoginViewModel(bootConfig)

    @Before
    fun setup() {
        // This is required for the LiveData to actually update during the test
        // because it isn't actually being observed since there is no lifecycle.
        viewModel.selectedServer.observeForever { }
        viewModel.loginUrl.observeForever { }

    }

    @After
    fun teardown() {
        SalesforceSDKManager.getInstance().loginServerManager.reset()
    }

    // Google's recommended naming scheme for view model test is "thingUnderTest_TriggerOfTest_ResultOfTest"
    @Test
    fun selectedServer_UpdatesOn_loginServerManagerChange() {
        val loginServerManager = SalesforceSDKManager.getInstance().loginServerManager
        Assert.assertEquals(loginServerManager.selectedLoginServer.url, viewModel.selectedServer.value)
        Assert.assertNotEquals(FAKE_SERVER_URL, viewModel.selectedServer.value)

        loginServerManager.addCustomLoginServer("fake", FAKE_SERVER_URL)
        Assert.assertEquals(FAKE_SERVER_URL, viewModel.selectedServer.value)
    }

    @Test
    fun loginUrl_UpdatesOn_selectedServerChange() {
        Assert.assertNotEquals(FAKE_SERVER_URL, viewModel.selectedServer.value)
        Assert.assertTrue(viewModel.loginUrl.value!!.startsWith(viewModel.selectedServer.value!!))
        Assert.assertFalse(viewModel.loginUrl.value!!.startsWith(FAKE_SERVER_URL))

        viewModel.selectedServer.value = FAKE_SERVER_URL
        Assert.assertTrue(viewModel.loginUrl.value!!.startsWith(FAKE_SERVER_URL))
    }

    @Test
    fun selectedServer_Changes_GenerateCorrectAuthorizationUrl() {
        val originalServer = viewModel.selectedServer.value!!
        val originalCodeChallenge = getSHA256Hash(viewModel.codeVerifier)
        val originalAuthUrl = generateExpectedAuthorizationUrl(originalServer, originalCodeChallenge)
        Assert.assertEquals(originalAuthUrl, viewModel.loginUrl.value)

        viewModel.selectedServer.value = FAKE_SERVER_URL
        val newCodeChallenge = getSHA256Hash(viewModel.codeVerifier)
        Assert.assertNotEquals(originalCodeChallenge, newCodeChallenge)
        val newAuthUrl = generateExpectedAuthorizationUrl(FAKE_SERVER_URL, newCodeChallenge)
        Assert.assertEquals(newAuthUrl, viewModel.loginUrl.value)
    }

    @Test
    fun codeVerifier_UpdatesOn_WebViewRefresh() {
        val originalCodeChallenge = getSHA256Hash(viewModel.codeVerifier)
        Assert.assertTrue(viewModel.loginUrl.value!!.contains(originalCodeChallenge))

        viewModel.reloadWebview()
        val newCodeChallenge = getSHA256Hash(viewModel.codeVerifier)
        Assert.assertNotNull(newCodeChallenge)
        Assert.assertNotEquals(originalCodeChallenge, newCodeChallenge)
        Assert.assertTrue(viewModel.loginUrl.value!!.contains(newCodeChallenge))
    }

    @Test
    fun jwtFlow_Changes_loginUrl() {
        val server = viewModel.selectedServer.value!!
        var codeChallenge = getSHA256Hash(viewModel.codeVerifier)
        val expectedUrl = generateExpectedAuthorizationUrl(server, codeChallenge)
        Assert.assertEquals(expectedUrl, viewModel.loginUrl.value)

        viewModel.jwt = FAKE_JWT
        viewModel.authCodeForJwtFlow = FAKE_JWT_FLOW_AUTH
        viewModel.reloadWebview()
        Assert.assertNotEquals(expectedUrl, viewModel.loginUrl.value)

        codeChallenge = getSHA256Hash(viewModel.codeVerifier)
        val authUrl = generateExpectedAuthorizationUrl(server, codeChallenge)
        val expectedJwtFlowUrl = getFrontdoorUrl(URI(authUrl), FAKE_JWT_FLOW_AUTH, server, mapOf<String, String>()).toString()
        Assert.assertEquals(expectedJwtFlowUrl, viewModel.loginUrl.value)
    }

    @Test
    fun testGetValidSeverUrl() {
        Assert.assertNull(viewModel.getValidServerUrl(""))
        Assert.assertNull(viewModel.getValidServerUrl("not_a_url_at_all"))
        Assert.assertNull(viewModel.getValidServerUrl("https://stillnotaurl"))
        Assert.assertNull(viewModel.getValidServerUrl("a.com."))
        Assert.assertEquals("https://a.com", viewModel.getValidServerUrl("a.com"))
        Assert.assertEquals("https://a.b", viewModel.getValidServerUrl("http://a.b"))
        val unchangedUrl = "https://login.salesforce.com"
        Assert.assertEquals(unchangedUrl, viewModel.getValidServerUrl(unchangedUrl))
        val endingSlash = "$unchangedUrl/"
        Assert.assertEquals(unchangedUrl, viewModel.getValidServerUrl(endingSlash))
    }

    private fun generateExpectedAuthorizationUrl(server: String, codeChallenge: String): String = OAuth2.getAuthorizationUrl(
        true,
        true,
        URI(server),
        bootConfig.remoteAccessConsumerKey,
        bootConfig.oauthRedirectURI,
        bootConfig.oauthScopes,
        SalesforceSDKManager.getInstance().appContext.getString(oauth_display_type),
        codeChallenge,
        hashMapOf<String, String>()
    ).toString()
}
