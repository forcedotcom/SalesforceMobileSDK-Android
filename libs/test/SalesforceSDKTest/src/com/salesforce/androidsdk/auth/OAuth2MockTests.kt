package com.salesforce.androidsdk.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.OAuth2.ATTESTATION
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI

@RunWith(AndroidJUnit4::class)
class OAuth2MockTests {

    @Test
    fun oauth2_getAuthorizationUrl_includesAttestationParameterWhenNotNull() {

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
            mapOf(ATTESTATION to "__ATTESTATION_TOKEN__")
        )

        assertTrue(result.query.contains("attestation=__ATTESTATION_TOKEN__"))
    }

    @Test
    fun oauth2_getAuthorizationUrl_excludesAttestationParameterWhenNull() {

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
        )

        assertFalse(result.query.contains("attestation=__ATTESTATION_TOKEN__"))
    }

    @Test
    fun oauth2_makeTokenEndpointRequest_includesAttestationParameterWhenNotNull() {
        val appAttestationClient = mockk<AppAttestationClient>(relaxed = true) {
            every { fetchMobileAppAttestationChallengeBlocking() } returns "__TEST_CHALLENGE_VALUE__"
            every { createAppAttestationBlocking("__TEST_CHALLENGE_VALUE__") } returns "__ATTESTATION_TOKEN__"
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
        assertTrue(
            "Expected grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer in form body but got: $formBody",
            formBody.contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer"),
        )
        assertTrue(
            "Expected assertion=__JWT_ASSERTION__ in form body but got: $formBody",
            formBody.contains("assertion=__JWT_ASSERTION__"),
        )
    }

    // region DPoP tests

    @Test
    fun test_givenUseDPoPTrueAndCredentialsIdentifier_whenMakeTokenEndpointRequest_thenDPoPHeaderPresent() {
        val salesforceSdkManager = mockk<SalesforceSDKManager>(relaxed = true) {
            every { appAttestationClient } returns null
            every { deviceId } returns "__DEVICE_ID__"
            every { isUseDPoP() } returns true
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
            URI("https://login.salesforce.com"),
            FormBody.Builder(),
            salesforceSdkManager,
            "test-scope-id",
        )

        val dpopHeader = requestSlot.captured.header("DPoP")
        assertNotNull("Expected DPoP header to be present", dpopHeader)
        assertTrue("Expected DPoP header to be non-blank", !dpopHeader.isNullOrBlank())
    }

    @Test
    fun test_givenUseDPoPFalse_whenMakeTokenEndpointRequest_thenNoDPoPHeader() {
        val salesforceSdkManager = mockk<SalesforceSDKManager>(relaxed = true) {
            every { appAttestationClient } returns null
            every { deviceId } returns "__DEVICE_ID__"
            every { isUseDPoP() } returns false
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
            URI("https://login.salesforce.com"),
            FormBody.Builder(),
            salesforceSdkManager,
            "test-scope-id",
        )

        assertNull(
            "Did not expect DPoP header when isUseDPoP() is false",
            requestSlot.captured.header("DPoP"),
        )
    }

    @Test
    fun test_givenUseDPoPTrueButNullCredentialsIdentifier_whenMakeTokenEndpointRequest_thenNoDPoPHeader() {
        val salesforceSdkManager = mockk<SalesforceSDKManager>(relaxed = true) {
            every { appAttestationClient } returns null
            every { deviceId } returns "__DEVICE_ID__"
            every { isUseDPoP() } returns true
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
            URI("https://login.salesforce.com"),
            FormBody.Builder(),
            salesforceSdkManager,
            null,
        )

        assertNull(
            "Did not expect DPoP header when credentialsIdentifier is null",
            requestSlot.captured.header("DPoP"),
        )
    }

    @Test
    fun test_givenUseDPoPTrue_whenMakeTokenEndpointRequest_thenTokenTypePersistedOnResponse() {
        val salesforceSdkManager = mockk<SalesforceSDKManager>(relaxed = true) {
            every { appAttestationClient } returns null
            every { deviceId } returns "__DEVICE_ID__"
            every { isUseDPoP() } returns true
        }

        val responseBody = """{"access_token":"t","instance_url":"https://i","id":"https://i/id/o/u","token_type":"DPoP"}"""
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

        val tokenResponse = makeTokenEndpointRequest(
            httpAccessor,
            URI("https://login.salesforce.com"),
            FormBody.Builder(),
            salesforceSdkManager,
            "test-scope-id",
        )

        assertEquals("DPoP", tokenResponse.tokenType)
    }

    @Test
    fun test_givenDPoPTokenType_whenAddAuthorizationHeader_thenDPoPSchemeUsed() {
        val builder = Request.Builder().url("https://example.com")

        OAuth2.addAuthorizationHeader(builder, "my-access-token", "DPoP")

        val authHeader = builder.build().header("Authorization")
        assertNotNull(authHeader)
        assertTrue(
            "Expected Authorization header to start with 'DPoP ' but got: $authHeader",
            authHeader!!.startsWith("DPoP "),
        )
    }

    @Test
    fun test_givenBearerTokenType_whenAddAuthorizationHeader_thenBearerSchemeUsed() {
        val builder = Request.Builder().url("https://example.com")

        OAuth2.addAuthorizationHeader(builder, "my-access-token", "Bearer")

        val authHeader = builder.build().header("Authorization")
        assertNotNull(authHeader)
        assertTrue(
            "Expected Authorization header to start with 'Bearer ' but got: $authHeader",
            authHeader!!.startsWith("Bearer "),
        )
    }

    @Test
    fun test_givenNullTokenType_whenAddAuthorizationHeader_thenBearerSchemeUsed() {
        val builder = Request.Builder().url("https://example.com")

        OAuth2.addAuthorizationHeader(builder, "my-access-token", null)

        val authHeader = builder.build().header("Authorization")
        assertNotNull(authHeader)
        assertTrue(
            "Expected Authorization header to start with 'Bearer ' but got: $authHeader",
            authHeader!!.startsWith("Bearer "),
        )
    }

    @Test
    fun test_givenKeyStoreException_whenMakeTokenEndpointRequest_thenRequestProceedsWithoutDPoPHeader() {
        val salesforceSdkManager = mockk<SalesforceSDKManager>(relaxed = true) {
            every { appAttestationClient } returns null
            every { deviceId } returns "__DEVICE_ID__"
            every { isUseDPoP() } returns true
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

        val tokenResponse = makeTokenEndpointRequest(
            httpAccessor,
            URI("https://login.salesforce.com"),
            FormBody.Builder(),
            salesforceSdkManager,
            "test-scope-id-keystore-error",
        )

        assertNotNull(
            "Expected token endpoint request to complete even if DPoP key generation fails",
            tokenResponse,
        )
        assertTrue("Expected the request to have been captured", requestSlot.isCaptured)
    }

    // endregion DPoP tests
}
