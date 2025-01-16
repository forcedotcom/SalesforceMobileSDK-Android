package com.salesforce.androidsdk.auth

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.R.string.oauth_display_type
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.ui.LoginViewModel
import com.salesforce.androidsdk.security.SalesforceKeyGenerator.getSHA256Hash
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI

@RunWith(AndroidJUnit4::class)
class LoginViewModelTest {
    @get:Rule
    val instantExecutorRule: InstantTaskExecutorRule = InstantTaskExecutorRule()

    private val FAKE_SERVER_URL = "shouldMatchNothing.salesforce.com"
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
    fun selectedServer_updatesOn_loginServerManagerChange() {
        val loginServerManager = SalesforceSDKManager.getInstance().loginServerManager
        Assert.assertEquals(loginServerManager.selectedLoginServer.url, viewModel.selectedServer.value)
        Assert.assertNotEquals(FAKE_SERVER_URL, viewModel.selectedServer.value)

        loginServerManager.addCustomLoginServer("fake", FAKE_SERVER_URL)
        Assert.assertEquals(FAKE_SERVER_URL, viewModel.selectedServer.value)
    }

    @Test
    fun loginUrl_updatesOn_selectedServerChange() {
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
    fun codeVerifier_UpdatesOn_WebviewRefresh() {
        val originalCodeChallenge = getSHA256Hash(viewModel.codeVerifier)
        Assert.assertTrue(viewModel.loginUrl.value!!.contains(originalCodeChallenge))

        viewModel.reloadWebview()
        val newCodeChallenge = getSHA256Hash(viewModel.codeVerifier)
        Assert.assertNotNull(newCodeChallenge)
        Assert.assertNotEquals(originalCodeChallenge, newCodeChallenge)
        Assert.assertTrue(viewModel.loginUrl.value!!.contains(newCodeChallenge))
    }

    @Test
    fun testGetValidSeverUrl() {
        Assert.assertNull(viewModel.getValidServerUrl(""))
        Assert.assertNull(viewModel.getValidServerUrl("notaurlatall"))
        Assert.assertNull(viewModel.getValidServerUrl("https://stillnotaurl"))
        Assert.assertEquals("https://a.com", viewModel.getValidServerUrl("a.com"))
        Assert.assertEquals("https://a.b", viewModel.getValidServerUrl("http://a.b"))
        val unchangedUrl = "https://login.salesforce.com"
        Assert.assertEquals(unchangedUrl, viewModel.getValidServerUrl(unchangedUrl))
        val endingSlash = "$unchangedUrl/"
        Assert.assertEquals(unchangedUrl, viewModel.getValidServerUrl(endingSlash))
    }

    private fun generateExpectedAuthorizationUrl(server: String, codeChallenge: String): String
        = OAuth2.getAuthorizationUrl(
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