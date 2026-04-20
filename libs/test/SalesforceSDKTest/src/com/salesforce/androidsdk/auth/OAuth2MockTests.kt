package com.salesforce.androidsdk.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.OAuth2.exchangeCode
import com.salesforce.androidsdk.auth.OAuth2.getAuthorizationUrl
import com.salesforce.androidsdk.auth.OAuth2.makeTokenEndpointRequest
import com.salesforce.androidsdk.auth.OAuth2.swapJWTForTokens
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI

@RunWith(AndroidJUnit4::class)
class OAuth2MockTests {

    @Test
    fun oauth2_getAuthorizationUrl_includesAttestationParameterWhenNotNull() {

        val appAttestationClient = mockk<AppAttestationClient>(relaxed = true)
        every { appAttestationClient.createSalesforceOAuthAuthorizationAppAttestationBlocking() } returns "__ATTESTATION_TOKEN__"
        val salesforceSdkManager = mockk<SalesforceSDKManager>(relaxed = true)
        every { salesforceSdkManager.appAttestationClient } returns appAttestationClient
        val result = getAuthorizationUrl(
            true,
            false,
            URI.create("https://login.example.com"),
            "__REMOTE_CONSUMER_KEY__",
            "http://app.example.com/callback",
            listOf<String>().toTypedArray(),
            null,
            "__DISPLAY_TYPE__",
            "__CODE_CHALLENGE__",
            mapOf<String, String>(),
            salesforceSdkManager,
        )

        assertTrue(result.query.contains("attestation=__ATTESTATION_TOKEN__"))
    }

    @Test
    fun oauth2_getAuthorizationUrl_excludesAttestationParameterWhenNull() {

        val salesforceSdkManager = mockk<SalesforceSDKManager>(relaxed = true)
        every { salesforceSdkManager.appAttestationClient } returns null
        val result = getAuthorizationUrl(
            true,
            false,
            URI.create("https://login.example.com"),
            "__REMOTE_CONSUMER_KEY__",
            "http://app.example.com/callback",
            listOf<String>().toTypedArray(),
            null,
            "__DISPLAY_TYPE__",
            "__CODE_CHALLENGE__",
            mapOf<String, String>(),
            salesforceSdkManager,
        )

        assertFalse(result.query.contains("attestation=__ATTESTATION_TOKEN__"))
    }

    @Test
    fun oauth2_makeTokenEndpointRequest_includesAttestationParameterWhenNotNull() {
        val appAttestationClient = mockk<AppAttestationClient>(relaxed = true) {
            every { createSalesforceOAuthAuthorizationAppAttestationBlocking() } returns "__ATTESTATION_TOKEN__"
        }
        val salesforceSdkManager = mockk<SalesforceSDKManager>(relaxed = true) {
            every { this@mockk.appAttestationClient } returns appAttestationClient
            every { deviceId } returns "__DEVICE_ID__"
        }

        val responseBody = """{"access_token":"t","instance_url":"https://i","id":"https://i/id/o/u"}"""
            .toResponseBody("application/json; charset=utf-8".toMediaType())
        val okHttpResponse = mockk<Response>(relaxed = true) {
            every { isSuccessful } returns true
            every { body } returns responseBody
        }
        val requestSlot = slot<Request>()
        val httpAccessor = mockk<HttpAccess>(relaxed = true) {
            every { okHttpClient } returns mockk {
                every { newCall(capture(requestSlot)) } returns mockk {
                    every { execute() } returns okHttpResponse
                }
            }
        }

        makeTokenEndpointRequest(
            httpAccessor,
            URI.create("https://login.example.com"),
            FormBody.Builder(),
            salesforceSdkManager,
        )

        val query = requestSlot.captured.url.query ?: ""
        assertTrue(
            "Expected attestation parameter in request URL but got: $query",
            query.contains("attestation=__ATTESTATION_TOKEN__"),
        )
    }

    @Test
    fun oauth2_makeTokenEndpointRequest_excludesAttestationParameterWhenNull() {
        val salesforceSdkManager = mockk<SalesforceSDKManager>(relaxed = true) {
            every { this@mockk.appAttestationClient } returns null
            every { deviceId } returns "__DEVICE_ID__"
        }

        val responseBody = """{"access_token":"t","instance_url":"https://i","id":"https://i/id/o/u"}"""
            .toResponseBody("application/json; charset=utf-8".toMediaType())
        val okHttpResponse = mockk<Response>(relaxed = true) {
            every { isSuccessful } returns true
            every { body } returns responseBody
        }
        val requestSlot = slot<Request>()
        val httpAccessor = mockk<HttpAccess>(relaxed = true) {
            every { okHttpClient } returns mockk {
                every { newCall(capture(requestSlot)) } returns mockk {
                    every { execute() } returns okHttpResponse
                }
            }
        }

        makeTokenEndpointRequest(
            httpAccessor,
            URI.create("https://login.example.com"),
            FormBody.Builder(),
            salesforceSdkManager,
        )

        val query = requestSlot.captured.url.query ?: ""
        assertFalse(
            "Did not expect attestation parameter in request URL but got: $query",
            query.contains("attestation=__ATTESTATION_TOKEN__"),
        )
    }

    @Test
    fun oauth2_exchangeCode_sendsAuthorizationCodeParameters() {
        val responseBody = """{"access_token":"t","instance_url":"https://i","id":"https://i/id/o/u"}"""
            .toResponseBody("application/json; charset=utf-8".toMediaType())
        val okHttpResponse = mockk<Response>(relaxed = true) {
            every { isSuccessful } returns true
            every { body } returns responseBody
        }
        val requestSlot = slot<Request>()
        val httpAccessor = mockk<HttpAccess>(relaxed = true) {
            every { okHttpClient } returns mockk {
                every { newCall(capture(requestSlot)) } returns mockk {
                    every { execute() } returns okHttpResponse
                }
            }
        }

        exchangeCode(
            httpAccessor,
            URI.create("https://login.example.com"),
            "__REMOTE_CONSUMER_KEY__",
            "__AUTH_CODE__",
            "__CODE_VERIFIER__",
            "http://app.example.com/callback",
        )

        val bodyBuffer = Buffer().also { requestSlot.captured.body?.writeTo(it) }
        val formBody = bodyBuffer.readUtf8()
        assertTrue(
            "Expected client_id=__REMOTE_CONSUMER_KEY__ in form body but got: $formBody",
            formBody.contains("client_id=__REMOTE_CONSUMER_KEY__"),
        )
        assertTrue(
            "Expected code=__AUTH_CODE__ in form body but got: $formBody",
            formBody.contains("code=__AUTH_CODE__"),
        )
        assertTrue(
            "Expected code_verifier=__CODE_VERIFIER__ in form body but got: $formBody",
            formBody.contains("code_verifier=__CODE_VERIFIER__"),
        )
    }

    @Test
    fun oauth2_swapJWTForTokens_sendsJwtBearerGrantTypeAndAssertion() {
        val responseBody = """{"access_token":"t","instance_url":"https://i","id":"https://i/id/o/u"}"""
            .toResponseBody("application/json; charset=utf-8".toMediaType())
        val okHttpResponse = mockk<Response>(relaxed = true) {
            every { isSuccessful } returns true
            every { body } returns responseBody
        }
        val requestSlot = slot<Request>()
        val httpAccessor = mockk<HttpAccess>(relaxed = true) {
            every { okHttpClient } returns mockk {
                every { newCall(capture(requestSlot)) } returns mockk {
                    every { execute() } returns okHttpResponse
                }
            }
        }

        swapJWTForTokens(
            httpAccessor,
            URI.create("https://login.example.com"),
            "__JWT_ASSERTION__",
        )

        val bodyBuffer = Buffer().also { requestSlot.captured.body?.writeTo(it) }
        val formBody = bodyBuffer.readUtf8()
        @Suppress("SpellCheckingInspection")
        assertTrue(
            "Expected grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer in form body but got: $formBody",
            formBody.contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer"),
        )
        assertTrue(
            "Expected assertion=__JWT_ASSERTION__ in form body but got: $formBody",
            formBody.contains("assertion=__JWT_ASSERTION__"),
        )
    }
}
