package com.salesforce.androidsdk.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.config.LoginServerManager.LoginServer
import com.salesforce.androidsdk.config.LoginServerManager.WELCOME_LOGIN_URL
import com.salesforce.androidsdk.rest.RestResponse
import com.salesforce.androidsdk.util.AuthConfigUtil.MyDomainAuthConfig
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
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for `SalesforceSDKManager`.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class SalesforceSDKManagerTests {

    @Test
    fun expectedValue_Returns_onFetchAuthenticationConfiguration() {

        // Mocks.
        val responseBody = mockk<ResponseBody>()
        every { responseBody.contentType() } returns "application/json;charset=UTF-8".toMediaType()
        every { responseBody.bytes() } returns "{\"id\":\"https://login.salesforce.com/id/00DB0000000ToZ3MAK/005B0000009mWwmIAE\",\"asserted_user\":true,\"user_id\":\"005B0000009mWwmIAE\",\"organization_id\":\"00DB0000000ToZ3MAK\",\"username\":\"johnson.eric@gs0.mobilesdk.com\",\"nick_name\":\"ejohnson\",\"display_name\":\"Eric Johnson\",\"email\":\"johnson.eric@salesforce.com\",\"email_verified\":true,\"first_name\":\"Eric\",\"last_name\":\"Johnson\",\"timezone\":\"America/Los_Angeles\",\"photos\":{\"picture\":\"https://mobilesdk.my.salesforce.com/profilephoto/7291Q0000001oer/F\",\"thumbnail\":\"https://mobilesdk.my.salesforce.com/profilephoto/7291Q0000001oer/T\"},\"addr_street\":null,\"addr_city\":null,\"addr_state\":null,\"addr_country\":null,\"addr_zip\":null,\"mobile_phone\":null,\"mobile_phone_verified\":true,\"is_lightning_login_user\":false,\"status\":{\"created_date\":null,\"body\":null},\"urls\":{\"enterprise\":\"https://mobilesdk.my.salesforce.com/services/Soap/c/{version}/00DB0000000ToZ3\",\"metadata\":\"https://mobilesdk.my.salesforce.com/services/Soap/m/{version}/00DB0000000ToZ3\",\"partner\":\"https://mobilesdk.my.salesforce.com/services/Soap/u/{version}/00DB0000000ToZ3\",\"rest\":\"https://mobilesdk.my.salesforce.com/services/data/v{version}/\",\"sobjects\":\"https://mobilesdk.my.salesforce.com/services/data/v{version}/sobjects/\",\"search\":\"https://mobilesdk.my.salesforce.com/services/data/v{version}/search/\",\"query\":\"https://mobilesdk.my.salesforce.com/services/data/v{version}/query/\",\"recent\":\"https://mobilesdk.my.salesforce.com/services/data/v{version}/recent/\",\"tooling_soap\":\"https://mobilesdk.my.salesforce.com/services/Soap/T/{version}/00DB0000000ToZ3\",\"tooling_rest\":\"https://mobilesdk.my.salesforce.com/services/data/v{version}/tooling/\",\"profile\":\"https://mobilesdk.my.salesforce.com/005B0000009mWwmIAE\",\"feeds\":\"https://mobilesdk.my.salesforce.com/services/data/v{version}/chatter/feeds\",\"groups\":\"https://mobilesdk.my.salesforce.com/services/data/v{version}/chatter/groups\",\"users\":\"https://mobilesdk.my.salesforce.com/services/data/v{version}/chatter/users\",\"feed_items\":\"https://mobilesdk.my.salesforce.com/services/data/v{version}/chatter/feed-items\",\"feed_elements\":\"https://mobilesdk.my.salesforce.com/services/data/v{version}/chatter/feed-elements\",\"custom_domain\":\"https://mobilesdk.my.salesforce.com\"},\"active\":true,\"user_type\":\"STANDARD\",\"language\":\"en_US\",\"locale\":\"en_US\",\"utcOffset\":-28800000,\"last_modified_date\":\"2025-02-28T18:14:06Z\"}".toByteArray()

        val response = mockk<Response>()
        every { response.isSuccessful } returns true
        every { response.body } returns responseBody
        every { response.close() } just runs

        val call = mockk<Call>()
        every { call.execute() } returns response

        val okHttpClient = mockk<OkHttpClient>()
        every { okHttpClient.newCall(any()) } returns call

        val httpAccess = mockk<HttpAccess>()
        every { httpAccess.getOkHttpClient() } returns okHttpClient

        var authConfig: MyDomainAuthConfig? = null


        // Login Server: Welcome
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
                authConfig = MyDomainAuthConfig((RestResponse(response)).asJSONObject())
            }.join()
        }

        assertEquals(
            "{\"id\":\"https:\\/\\/login.salesforce.com\\/id\\/00DB0000000ToZ3MAK\\/005B0000009mWwmIAE\",\"asserted_user\":true,\"user_id\":\"005B0000009mWwmIAE\",\"organization_id\":\"00DB0000000ToZ3MAK\",\"username\":\"johnson.eric@gs0.mobilesdk.com\",\"nick_name\":\"ejohnson\",\"display_name\":\"Eric Johnson\",\"email\":\"johnson.eric@salesforce.com\",\"email_verified\":true,\"first_name\":\"Eric\",\"last_name\":\"Johnson\",\"timezone\":\"America\\/Los_Angeles\",\"photos\":{\"picture\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/profilephoto\\/7291Q0000001oer\\/F\",\"thumbnail\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/profilephoto\\/7291Q0000001oer\\/T\"},\"addr_street\":null,\"addr_city\":null,\"addr_state\":null,\"addr_country\":null,\"addr_zip\":null,\"mobile_phone\":null,\"mobile_phone_verified\":true,\"is_lightning_login_user\":false,\"status\":{\"created_date\":null,\"body\":null},\"urls\":{\"enterprise\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/Soap\\/c\\/{version}\\/00DB0000000ToZ3\",\"metadata\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/Soap\\/m\\/{version}\\/00DB0000000ToZ3\",\"partner\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/Soap\\/u\\/{version}\\/00DB0000000ToZ3\",\"rest\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/\",\"sobjects\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/sobjects\\/\",\"search\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/search\\/\",\"query\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/query\\/\",\"recent\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/recent\\/\",\"tooling_soap\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/Soap\\/T\\/{version}\\/00DB0000000ToZ3\",\"tooling_rest\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/tooling\\/\",\"profile\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/005B0000009mWwmIAE\",\"feeds\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/chatter\\/feeds\",\"groups\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/chatter\\/groups\",\"users\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/chatter\\/users\",\"feed_items\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/chatter\\/feed-items\",\"feed_elements\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/chatter\\/feed-elements\",\"custom_domain\":\"https:\\/\\/mobilesdk.my.salesforce.com\"},\"active\":true,\"user_type\":\"STANDARD\",\"language\":\"en_US\",\"locale\":\"en_US\",\"utcOffset\":-28800000,\"last_modified_date\":\"2025-02-28T18:14:06Z\"}",
            authConfig?.authConfig?.toString()
        )


        // Login Server: Sandbox
        SalesforceSDKManager.getInstance().loginServerManager.useSandbox()

        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccess,
            ) {
                authConfig = MyDomainAuthConfig((RestResponse(response)).asJSONObject())
            }.join()
        }

        assertEquals(
            "{\"id\":\"https:\\/\\/login.salesforce.com\\/id\\/00DB0000000ToZ3MAK\\/005B0000009mWwmIAE\",\"asserted_user\":true,\"user_id\":\"005B0000009mWwmIAE\",\"organization_id\":\"00DB0000000ToZ3MAK\",\"username\":\"johnson.eric@gs0.mobilesdk.com\",\"nick_name\":\"ejohnson\",\"display_name\":\"Eric Johnson\",\"email\":\"johnson.eric@salesforce.com\",\"email_verified\":true,\"first_name\":\"Eric\",\"last_name\":\"Johnson\",\"timezone\":\"America\\/Los_Angeles\",\"photos\":{\"picture\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/profilephoto\\/7291Q0000001oer\\/F\",\"thumbnail\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/profilephoto\\/7291Q0000001oer\\/T\"},\"addr_street\":null,\"addr_city\":null,\"addr_state\":null,\"addr_country\":null,\"addr_zip\":null,\"mobile_phone\":null,\"mobile_phone_verified\":true,\"is_lightning_login_user\":false,\"status\":{\"created_date\":null,\"body\":null},\"urls\":{\"enterprise\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/Soap\\/c\\/{version}\\/00DB0000000ToZ3\",\"metadata\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/Soap\\/m\\/{version}\\/00DB0000000ToZ3\",\"partner\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/Soap\\/u\\/{version}\\/00DB0000000ToZ3\",\"rest\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/\",\"sobjects\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/sobjects\\/\",\"search\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/search\\/\",\"query\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/query\\/\",\"recent\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/recent\\/\",\"tooling_soap\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/Soap\\/T\\/{version}\\/00DB0000000ToZ3\",\"tooling_rest\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/tooling\\/\",\"profile\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/005B0000009mWwmIAE\",\"feeds\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/chatter\\/feeds\",\"groups\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/chatter\\/groups\",\"users\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/chatter\\/users\",\"feed_items\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/chatter\\/feed-items\",\"feed_elements\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/chatter\\/feed-elements\",\"custom_domain\":\"https:\\/\\/mobilesdk.my.salesforce.com\"},\"active\":true,\"user_type\":\"STANDARD\",\"language\":\"en_US\",\"locale\":\"en_US\",\"utcOffset\":-28800000,\"last_modified_date\":\"2025-02-28T18:14:06Z\"}",
            authConfig?.authConfig?.toString()
        )


        // Login Server: Null
        SalesforceSDKManager.getInstance().loginServerManager.run {
            loginServers.forEach {
                removeServer(it)
            }
        }

        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccess,
            ) {
                authConfig = MyDomainAuthConfig((RestResponse(response)).asJSONObject())
            }.join()
        }

        assertEquals(
            "{\"id\":\"https:\\/\\/login.salesforce.com\\/id\\/00DB0000000ToZ3MAK\\/005B0000009mWwmIAE\",\"asserted_user\":true,\"user_id\":\"005B0000009mWwmIAE\",\"organization_id\":\"00DB0000000ToZ3MAK\",\"username\":\"johnson.eric@gs0.mobilesdk.com\",\"nick_name\":\"ejohnson\",\"display_name\":\"Eric Johnson\",\"email\":\"johnson.eric@salesforce.com\",\"email_verified\":true,\"first_name\":\"Eric\",\"last_name\":\"Johnson\",\"timezone\":\"America\\/Los_Angeles\",\"photos\":{\"picture\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/profilephoto\\/7291Q0000001oer\\/F\",\"thumbnail\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/profilephoto\\/7291Q0000001oer\\/T\"},\"addr_street\":null,\"addr_city\":null,\"addr_state\":null,\"addr_country\":null,\"addr_zip\":null,\"mobile_phone\":null,\"mobile_phone_verified\":true,\"is_lightning_login_user\":false,\"status\":{\"created_date\":null,\"body\":null},\"urls\":{\"enterprise\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/Soap\\/c\\/{version}\\/00DB0000000ToZ3\",\"metadata\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/Soap\\/m\\/{version}\\/00DB0000000ToZ3\",\"partner\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/Soap\\/u\\/{version}\\/00DB0000000ToZ3\",\"rest\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/\",\"sobjects\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/sobjects\\/\",\"search\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/search\\/\",\"query\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/query\\/\",\"recent\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/recent\\/\",\"tooling_soap\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/Soap\\/T\\/{version}\\/00DB0000000ToZ3\",\"tooling_rest\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/tooling\\/\",\"profile\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/005B0000009mWwmIAE\",\"feeds\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/chatter\\/feeds\",\"groups\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/chatter\\/groups\",\"users\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/chatter\\/users\",\"feed_items\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/chatter\\/feed-items\",\"feed_elements\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/chatter\\/feed-elements\",\"custom_domain\":\"https:\\/\\/mobilesdk.my.salesforce.com\"},\"active\":true,\"user_type\":\"STANDARD\",\"language\":\"en_US\",\"locale\":\"en_US\",\"utcOffset\":-28800000,\"last_modified_date\":\"2025-02-28T18:14:06Z\"}",
            authConfig?.authConfig?.toString()
        )


        // Login Server: Production
        runBlocking {
            SalesforceSDKManager.getInstance().fetchAuthenticationConfiguration(
                httpAccess = httpAccess,
            ) {
                authConfig = MyDomainAuthConfig((RestResponse(response)).asJSONObject())
            }.join()
        }

        assertEquals(
            "{\"id\":\"https:\\/\\/login.salesforce.com\\/id\\/00DB0000000ToZ3MAK\\/005B0000009mWwmIAE\",\"asserted_user\":true,\"user_id\":\"005B0000009mWwmIAE\",\"organization_id\":\"00DB0000000ToZ3MAK\",\"username\":\"johnson.eric@gs0.mobilesdk.com\",\"nick_name\":\"ejohnson\",\"display_name\":\"Eric Johnson\",\"email\":\"johnson.eric@salesforce.com\",\"email_verified\":true,\"first_name\":\"Eric\",\"last_name\":\"Johnson\",\"timezone\":\"America\\/Los_Angeles\",\"photos\":{\"picture\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/profilephoto\\/7291Q0000001oer\\/F\",\"thumbnail\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/profilephoto\\/7291Q0000001oer\\/T\"},\"addr_street\":null,\"addr_city\":null,\"addr_state\":null,\"addr_country\":null,\"addr_zip\":null,\"mobile_phone\":null,\"mobile_phone_verified\":true,\"is_lightning_login_user\":false,\"status\":{\"created_date\":null,\"body\":null},\"urls\":{\"enterprise\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/Soap\\/c\\/{version}\\/00DB0000000ToZ3\",\"metadata\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/Soap\\/m\\/{version}\\/00DB0000000ToZ3\",\"partner\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/Soap\\/u\\/{version}\\/00DB0000000ToZ3\",\"rest\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/\",\"sobjects\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/sobjects\\/\",\"search\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/search\\/\",\"query\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/query\\/\",\"recent\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/recent\\/\",\"tooling_soap\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/Soap\\/T\\/{version}\\/00DB0000000ToZ3\",\"tooling_rest\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/tooling\\/\",\"profile\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/005B0000009mWwmIAE\",\"feeds\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/chatter\\/feeds\",\"groups\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/chatter\\/groups\",\"users\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/chatter\\/users\",\"feed_items\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/chatter\\/feed-items\",\"feed_elements\":\"https:\\/\\/mobilesdk.my.salesforce.com\\/services\\/data\\/v{version}\\/chatter\\/feed-elements\",\"custom_domain\":\"https:\\/\\/mobilesdk.my.salesforce.com\"},\"active\":true,\"user_type\":\"STANDARD\",\"language\":\"en_US\",\"locale\":\"en_US\",\"utcOffset\":-28800000,\"last_modified_date\":\"2025-02-28T18:14:06Z\"}",
            authConfig?.authConfig?.toString()
        )
    }
}
