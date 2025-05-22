package com.salesforce.androidsdk.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.config.LoginServerManager.LoginServer
import com.salesforce.androidsdk.config.LoginServerManager.PRODUCTION_LOGIN_URL
import com.salesforce.androidsdk.config.LoginServerManager.WELCOME_LOGIN_URL
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for `SalesforceSDKManager`.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class SalesforceSDKManagerTests {

    private val responseBodyString =
        "{\"id\":\"https://login.ietf.reserved.test.example.com/id/1234567890ABCDEFGH/ABCDEFGH1234567890\",\"asserted_user\":true,\"user_id\":\"ABCDEFGH1234567890\",\"organization_id\":\"1234567890ABCDEFGH\",\"username\":\"ietf_reserved_test_domain@example.com\",\"nick_name\":\"username\",\"display_name\":\"Test User\",\"email\":\"ietf_reserved_test_domain@example.com\",\"email_verified\":true,\"first_name\":\"First\",\"last_name\":\"Last\",\"timezone\":\"America/Los_Angeles\",\"photos\":{\"picture\":\"https://ietf.reserved.test.example.com/profilephoto/ZYXWVUTSRQPONML/F\",\"thumbnail\":\"https://ietf.reserved.test.example.com/profilephoto/ZYXWVUTSRQPONML/T\"},\"addr_street\":null,\"addr_city\":null,\"addr_state\":null,\"addr_country\":null,\"addr_zip\":null,\"mobile_phone\":null,\"mobile_phone_verified\":true,\"is_lightning_login_user\":false,\"status\":{\"created_date\":null,\"body\":null},\"urls\":{\"enterprise\":\"https://ietf.reserved.test.example.com/services/Soap/c/{version}/0987654321EDCVA\",\"metadata\":\"https://ietf.reserved.test.example.com/services/Soap/m/{version}/0987654321EDCVA\",\"partner\":\"https://ietf.reserved.test.example.com/services/Soap/u/{version}/0987654321EDCVA\",\"rest\":\"https://ietf.reserved.test.example.com/services/data/v{version}/\",\"sobjects\":\"https://ietf.reserved.test.example.com/services/data/v{version}/sobjects/\",\"search\":\"https://ietf.reserved.test.example.com/services/data/v{version}/search/\",\"query\":\"https://ietf.reserved.test.example.com/services/data/v{version}/query/\",\"recent\":\"https://ietf.reserved.test.example.com/services/data/v{version}/recent/\",\"tooling_soap\":\"https://ietf.reserved.test.example.com/services/Soap/T/{version}/0987654321EDCVA\",\"tooling_rest\":\"https://ietf.reserved.test.example.com/services/data/v{version}/tooling/\",\"profile\":\"https://ietf.reserved.test.example.com/ABCDEFGH1234567890\",\"feeds\":\"https://ietf.reserved.test.example.com/services/data/v{version}/chatter/feeds\",\"groups\":\"https://ietf.reserved.test.example.com/services/data/v{version}/chatter/groups\",\"users\":\"https://ietf.reserved.test.example.com/services/data/v{version}/chatter/users\",\"feed_items\":\"https://ietf.reserved.test.example.com/services/data/v{version}/chatter/feed-items\",\"feed_elements\":\"https://ietf.reserved.test.example.com/services/data/v{version}/chatter/feed-elements\",\"custom_domain\":\"https://ietf.reserved.test.example.com\"},\"active\":true,\"user_type\":\"STANDARD\",\"language\":\"en_US\",\"locale\":\"en_US\",\"utcOffset\":-28800000,\"last_modified_date\":\"2025-02-28T18:14:06Z\"}"

    private val responseBody = mockk<ResponseBody>().apply {
        every { contentType() } returns "application/json;charset=UTF-8".toMediaType()
        every { bytes() } returns this@SalesforceSDKManagerTests.responseBodyString.toByteArray()
    }

    private val response = mockk<Response>().apply {
        every { isSuccessful } returns true
        every { body } returns this@SalesforceSDKManagerTests.responseBody
        every { close() } just runs
    }

    private val call = mockk<Call>().apply {
        every { execute() } returns this@SalesforceSDKManagerTests.response
    }

    private val okHttpClient = mockk<OkHttpClient>().apply {
        every { newCall(any()) } returns this@SalesforceSDKManagerTests.call
    }

    private val httpAccess = mockk<HttpAccess>().apply {
        every { getOkHttpClient() } returns this@SalesforceSDKManagerTests.okHttpClient
    }

    @Before
    fun setup() {
    }

    @After
    fun teardown() {
        SalesforceSDKManager.getInstance().loginServerManager.reset()
    }

    @Test
    fun salesforceSdkManager_Updates_onFetchAuthenticationConfigurationForMyDomainLoginServer() {

        SalesforceSDKManager.getInstance().isBrowserLoginEnabled = true
        SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled = true

        SalesforceSDKManager.getInstance().loginServerManager.setSelectedLoginServer(
            LoginServer(
                "Example",
                "https://www.example.com",
                true
            )
        )

        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccess,
            ) {
                /* Completion Does Not Require Verification */
            }.join()
        }

        assertFalse(SalesforceSDKManager.getInstance().isBrowserLoginEnabled)
        assertFalse(SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled)
    }

    @Test
    fun salesforceSdkManager_Updates_onFetchAuthenticationConfigurationForMyWelcomeLoginServer() {

        SalesforceSDKManager.getInstance().isBrowserLoginEnabled = true
        SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled = true

        SalesforceSDKManager.getInstance().loginServerManager.setSelectedLoginServer(
            LoginServer(
                "Welcome",
                WELCOME_LOGIN_URL,
                true
            )
        )

        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccess,
            ) {
                /* Completion Does Not Require Verification */
            }.join()
        }

        assertFalse(SalesforceSDKManager.getInstance().isBrowserLoginEnabled)
        assertFalse(SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled)
    }

    @Test
    fun salesforceSdkManager_Updates_onFetchAuthenticationConfigurationForSandboxLoginServer() {

        SalesforceSDKManager.getInstance().isBrowserLoginEnabled = true
        SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled = true

        SalesforceSDKManager.getInstance().loginServerManager.useSandbox()

        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccess,
            ) {
                /* Completion Does Not Require Verification */
            }.join()
        }

        assertFalse(SalesforceSDKManager.getInstance().isBrowserLoginEnabled)
        assertFalse(SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled)
    }

    @Test
    fun salesforceSdkManager_Updates_onFetchAuthenticationConfigurationForNonHttpsLoginServer() {

        SalesforceSDKManager.getInstance().isBrowserLoginEnabled = true
        SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled = true

        SalesforceSDKManager.getInstance().loginServerManager.setSelectedLoginServer(
            LoginServer(
                "Non-HTTPS",
                "http://www.example.com", // IETF-Reserved Test Domain
                true
            )
        )

        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccess,
            ) {
                /* Completion Does Not Require Verification */
            }.join()
        }

        assertFalse(SalesforceSDKManager.getInstance().isBrowserLoginEnabled)
        assertFalse(SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled)
    }

    @Test
    fun salesforceSdkManager_Updates_onFetchAuthenticationConfigurationForInvalidUrlLoginServer() {

        SalesforceSDKManager.getInstance().isBrowserLoginEnabled = true
        SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled = true

        SalesforceSDKManager.getInstance().loginServerManager.setSelectedLoginServer(
            LoginServer(
                "Invalid",
                "invalid_url",
                true
            )
        )

        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccess,
            ) {
                /* Completion Does Not Require Verification */
            }.join()
        }

        assertFalse(SalesforceSDKManager.getInstance().isBrowserLoginEnabled)
        assertFalse(SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled)
    }

    @Test
    fun salesforceSdkManager_DoesNotUpdate_onFetchAuthenticationConfigurationWithError() {

        // Login Server: "My Domain"/Other URL, OkHttpClient Throws And Catch By AuthConfigUtil
        SalesforceSDKManager.getInstance().isBrowserLoginEnabled = false
        SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled = false

        SalesforceSDKManager.getInstance().loginServerManager.setSelectedLoginServer(
            LoginServer(
                "Example",
                "https://www.example.com",
                true
            )
        )

        // Mocks
        val httpAccessThrows = mockk<HttpAccess>()
        every { httpAccessThrows.getOkHttpClient() } throws (NullPointerException("Test Exception"))

        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccessThrows,
            ) {
                /* Completion Does Not Require Verification */
            }.join()
        }

        // Assert values haven't changed due to caught exception.
        assertFalse(SalesforceSDKManager.getInstance().isBrowserLoginEnabled)
        assertFalse(SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled)
    }

    @Test
    fun salesforceSdkManager_Updates_onFetchAuthenticationConfigurationForProductionLoginServer() {

        SalesforceSDKManager.getInstance().isBrowserLoginEnabled = true
        SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled = true

        SalesforceSDKManager.getInstance().loginServerManager.setSelectedLoginServer(
            LoginServer(
                "Production",
                PRODUCTION_LOGIN_URL,
                false
            )
        )

        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccess,
            ) {
                /* Completion Does Not Require Verification */
            }.join()
        }

        assertFalse(SalesforceSDKManager.getInstance().isBrowserLoginEnabled)
        assertFalse(SalesforceSDKManager.getInstance().isShareBrowserSessionEnabled)
    }
}
