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
package com.salesforce.androidsdk.auth

import android.app.Instrumentation.newApplication
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.salesforce.androidsdk.TestForceApp
import com.salesforce.androidsdk.auth.HttpAccess.DEFAULT
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse
import com.salesforce.androidsdk.auth.dpop.DPoPKeyManager.aliasForCredentialsIdentifier
import com.salesforce.androidsdk.auth.dpop.DPoPKeyManager.deleteKeyPair
import com.salesforce.androidsdk.auth.dpop.DPoPKeyManager.generateOrLoadKeyPair
import com.salesforce.androidsdk.auth.dpop.DPoPNonceCache.clear
import com.salesforce.androidsdk.auth.dpop.DPoPNonceCache.get
import com.salesforce.androidsdk.auth.dpop.DPoPNonceCache.store
import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol.HTTP_1_1
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID.randomUUID

/**
 * Tests that [fetchIsSalesforceIntegrationUser] attaches a DPoP proof (not
 * just a bare Bearer header) for the `/services/oauth2/userinfo` integration-
 * user check when the authenticated credential is DPoP-bound.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class AuthenticationUtilitiesIntegrationUserTest {

    private lateinit var httpAccess: CapturingHttpAccess
    private lateinit var credentialsIdentifier: String
    private lateinit var alias: String
    private lateinit var originalDefaultHttpAccess: HttpAccess

    @Before
    fun setUp() {
        val app = newApplication(
            TestForceApp::class.java,
            getInstrumentation().context
        )
        getInstrumentation().callApplicationOnCreate(app)

        originalDefaultHttpAccess = DEFAULT
        httpAccess = CapturingHttpAccess()
        DEFAULT = httpAccess

        credentialsIdentifier = "integration-user-test-${randomUUID()}"
        alias = aliasForCredentialsIdentifier(credentialsIdentifier)
    }

    @After
    fun tearDown() {
        DEFAULT = originalDefaultHttpAccess
        deleteKeyPair(alias)
    }

    /**
     * DPoP-bound credential: request must carry Authorization: DPoP plus a
     * signed proof.
     */
    @Test
    fun test_fetchIsSalesforceIntegrationUser_dpopBoundCredential_attachesProofAndDPoPScheme() {
        generateOrLoadKeyPair(alias)
        httpAccess.enqueueIntegrationUserSuccess(isIntegrationUser = false)

        val tokenResponse = buildTokenEndpointResponse(tokenType = "DPoP")

        val result = fetchIsSalesforceIntegrationUser(tokenResponse, "https://login.salesforce.com")

        assertFalse(result)
        val recorded = httpAccess.lastRequest()
        assertNotNull("userinfo request should have been sent", recorded)
        assertEquals(
            "Authorization scheme should be DPoP when tokenType==\"DPoP\"",
            "DPoP test-access-token",
            recorded!!.header("Authorization")
        )
        assertNotNull(
            "DPoP proof header must be attached for a DPoP-bound credential",
            recorded.header("DPoP")
        )
    }

    /**
     * Bearer credential (DPoP off / not bound): never attaches a DPoP
     * proof.
     */
    @Test
    fun test_fetchIsSalesforceIntegrationUser_bearerCredential_neverAttachesProof() {
        httpAccess.enqueueIntegrationUserSuccess(isIntegrationUser = true)

        val tokenResponse = buildTokenEndpointResponse(tokenType = null)

        val result = fetchIsSalesforceIntegrationUser(tokenResponse, "https://login.salesforce.com")

        assertTrue(result)
        val recorded = httpAccess.lastRequest()
        assertNotNull(recorded)
        assertNull("Bearer request must not carry a DPoP header", recorded!!.header("DPoP"))
        assertEquals(
            "Authorization scheme should be Bearer when tokenType is null",
            "Bearer test-access-token",
            recorded.header("Authorization")
        )
    }

    /**
     * A cold nonce cache triggers exactly one retry with a rebuilt proof
     * that actually carries the harvested nonce (a fresh `jti`, distinct
     * from the initial proof's) and targets the expected `htu`.
     */
    @Test
    fun test_fetchIsSalesforceIntegrationUser_nonceChallenge_retriesOnceWithNonce() {
        generateOrLoadKeyPair(alias)
        clear(credentialsIdentifier)
        httpAccess.enqueue(
            code = 401,
            body = """{"error":"use_dpop_nonce"}""",
            headers = mapOf("DPoP-Nonce" to "userinfo-nonce")
        )
        httpAccess.enqueueIntegrationUserSuccess(isIntegrationUser = false)

        val tokenResponse = buildTokenEndpointResponse(tokenType = "DPoP")

        val result = fetchIsSalesforceIntegrationUser(tokenResponse, "https://login.salesforce.com")

        assertFalse(result)
        val requests = httpAccess.allRequests()
        assertEquals("Expected one initial request and one nonce retry", 2, requests.size)

        val initialProof = dpopProofPayload(requests[0])
        val retryProof = dpopProofPayload(requests[1])
        val expectedHtu = "${tokenResponse.instanceUrl}/services/oauth2/userinfo"

        assertFalse(
            "Initial proof must not carry a nonce (cold cache)",
            initialProof.has("nonce")
        )
        assertEquals(
            "Retry proof must echo the nonce harvested from the challenge",
            "userinfo-nonce",
            retryProof.getString("nonce")
        )
        assertEquals(
            "Initial proof's htu must target the userinfo endpoint",
            expectedHtu,
            initialProof.getString("htu")
        )
        assertEquals(
            "Retry proof's htu must target the userinfo endpoint",
            expectedHtu,
            retryProof.getString("htu")
        )
        assertNotEquals(
            "Retry proof must use a fresh jti, not replay the initial proof's",
            initialProof.getString("jti"),
            retryProof.getString("jti")
        )
    }

    /**
     * When the final response comes from a different host than the initial
     * request (a cross-host Salesforce redirect, e.g. pool server ->
     * instance), the harvested nonce must be stored under the response's
     * host, not the pre-redirect request's host — otherwise a retry proof
     * built for the response's host never finds it.
     */
    @Test
    fun test_fetchIsSalesforceIntegrationUser_crossHostRedirect_harvestsNonceUnderResponseHost() {
        generateOrLoadKeyPair(alias)
        clear(credentialsIdentifier)
        httpAccess.enqueueIntegrationUserSuccess(
            isIntegrationUser = false,
            headers = mapOf("DPoP-Nonce" to "instance-nonce"),
            responseHost = "my-instance.salesforce.com",
        )

        val tokenResponse = buildTokenEndpointResponse(tokenType = "DPoP")

        fetchIsSalesforceIntegrationUser(tokenResponse, "https://login.salesforce.com")

        assertNull(
            "Nonce must not be stored under the pre-redirect request host",
            get(credentialsIdentifier, "instance.test")
        )
        assertEquals(
            "Nonce must be stored under the host that actually returned it",
            "instance-nonce",
            get(credentialsIdentifier, "my-instance.salesforce.com")
        )
    }

    /**
     * On redirect to a Salesforce host, [reattachAuthOnRedirect] re-attaches
     * Authorization/DPoP.
     */
    @Test
    fun test_reattachAuthOnRedirect_salesforceRedirect_reattachesAuthAndDPoPHeaders() {
        generateOrLoadKeyPair(alias)
        val originalUrl = "https://login.salesforce.com/services/oauth2/userinfo"
        val redirectedRequest = Request.Builder()
            .url("https://my-instance.salesforce.com/services/oauth2/userinfo")
            .get()
            .build()

        var capturedRequest: Request? = null
        val chain = mockk<Chain> {
            every { request() } returns redirectedRequest
            every { proceed(any()) } answers {
                capturedRequest = firstArg()
                Response.Builder()
                    .request(firstArg())
                    .protocol(HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
        }

        reattachAuthOnRedirect(chain, originalUrl, "test-access-token", "DPoP", credentialsIdentifier)

        assertEquals("DPoP test-access-token", capturedRequest?.header("Authorization"))
        assertNotNull("DPoP proof must be reattached after a Salesforce redirect", capturedRequest?.header("DPoP"))
    }

    /**
     * After a cross-host redirect, the nonce harvested under the response
     * host must be the one [reattachAuthOnRedirect] echoes back to that same
     * host — a nonce cached under the wrong host would silently omit the
     * `nonce` claim from the reattached proof instead of failing loudly.
     */
    @Test
    fun test_reattachAuthOnRedirect_crossHostRedirect_reattachedProofUsesResponseHostNonce() {
        generateOrLoadKeyPair(alias)
        val originalUrl = "https://login.salesforce.com/services/oauth2/userinfo"
        val redirectedUrl = "https://my-instance.salesforce.com/services/oauth2/userinfo"
        store(credentialsIdentifier, "my-instance.salesforce.com", "instance-nonce")
        val redirectedRequest = Request.Builder().url(redirectedUrl).get().build()

        var capturedRequest: Request? = null
        val chain = mockk<Chain> {
            every { request() } returns redirectedRequest
            every { proceed(any()) } answers {
                capturedRequest = firstArg()
                Response.Builder()
                    .request(firstArg())
                    .protocol(HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
        }

        reattachAuthOnRedirect(chain, originalUrl, "test-access-token", "DPoP", credentialsIdentifier)

        val payload = dpopProofPayload(checkNotNull(capturedRequest))
        assertEquals(
            "Reattached proof's htu must match the redirected host",
            redirectedUrl,
            payload.getString("htu")
        )
        assertEquals(
            "Reattached proof must echo the nonce cached under the redirected host",
            "instance-nonce",
            payload.getString("nonce")
        )
    }

    /**
     * No redirect (URL unchanged): [reattachAuthOnRedirect] must not add
     * any auth headers.
     */
    @Test
    fun test_reattachAuthOnRedirect_noRedirect_doesNotAttachHeaders() {
        val originalUrl = "https://login.salesforce.com/services/oauth2/userinfo"
        val sameRequest = Request.Builder().url(originalUrl).get().build()

        var capturedRequest: Request? = null
        val chain = mockk<Chain> {
            every { request() } returns sameRequest
            every { proceed(any()) } answers {
                capturedRequest = firstArg()
                Response.Builder()
                    .request(firstArg())
                    .protocol(HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
        }

        reattachAuthOnRedirect(chain, originalUrl, "test-access-token", "DPoP", credentialsIdentifier)

        assertNull(capturedRequest?.header("Authorization"))
        assertNull(capturedRequest?.header("DPoP"))
    }

    /** Decodes [request]'s DPoP proof JWT payload. */
    private fun dpopProofPayload(request: Request): JSONObject {
        val proof = checkNotNull(request.header("DPoP"))
        val parts = proof.split(".")
        assertEquals("DPoP proof should be a three-part JWT", 3, parts.size)
        val payload = android.util.Base64.decode(
            parts[1],
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or
                android.util.Base64.NO_WRAP
        )
        return JSONObject(String(payload, Charsets.UTF_8))
    }

    private fun buildTokenEndpointResponse(tokenType: String?): TokenEndpointResponse {
        val params = mutableMapOf(
            "access_token" to "test-access-token",
            "instance_url" to "https://instance.test",
            "id" to "https://instance.test/id/00Dxxxxx/005xxxxx",
            "scope" to "refresh_token id",
        )
        tokenType?.let { params["token_type"] = it }
        return TokenEndpointResponse(params).apply {
            this.tokenType = tokenType
            this.credentialsIdentifier = this@AuthenticationUtilitiesIntegrationUserTest.credentialsIdentifier
        }
    }

    /**
     * A HttpAccess subclass that installs an OkHttp interceptor to (a)
     * capture every outbound request and (b) return canned responses
     * without touching the network. Avoids the need for the MockWebServer
     * dependency, which is not on this module's test classpath.
     */
    private class CapturingHttpAccess : HttpAccess(null, "dummy-agent") {

        private val recordedRequests = mutableListOf<Request>()
        private val enqueuedResponses = ArrayDeque<CannedResponse>()

        private val capturingInterceptor = Interceptor { chain ->
            val req = chain.request()
            synchronized(recordedRequests) { recordedRequests += req }
            val canned = synchronized(enqueuedResponses) {
                enqueuedResponses.removeFirstOrNull()
            } ?: CannedResponse(200, "{}", emptyMap(), responseHost = null)
            /*
             * responseHost stands in for a cross-host Salesforce redirect:
             * OkHttp's Response.request reflects whichever physical request
             * actually produced it, which after a real redirect differs from
             * the pre-redirect request.
             */
            val responseRequest = canned.responseHost?.let {
                req.newBuilder().url(req.url.newBuilder().host(it).build()).build()
            } ?: req
            val builder = Response.Builder()
                .request(responseRequest)
                .protocol(HTTP_1_1)
                .code(canned.code)
                .message(if (canned.code < 300) "OK" else "ERR")
                .body(canned.body.toResponseBody("application/json".toMediaType()))
            canned.headers.forEach { (k, v) -> builder.addHeader(k, v) }
            builder.build()
        }

        override fun createNewClientBuilder(): OkHttpClient.Builder =
            OkHttpClient.Builder().apply {
                addInterceptor(capturingInterceptor)
            }

        fun enqueue(
            code: Int,
            body: String,
            headers: Map<String, String> = emptyMap(),
            responseHost: String? = null,
        ) {
            synchronized(enqueuedResponses) {
                enqueuedResponses.addLast(CannedResponse(code, body, headers, responseHost))
            }
        }

        fun enqueueIntegrationUserSuccess(
            isIntegrationUser: Boolean,
            headers: Map<String, String> = emptyMap(),
            responseHost: String? = null,
        ) {
            enqueue(200, """{"is_salesforce_integration_user":$isIntegrationUser}""", headers, responseHost)
        }

        fun lastRequest(): Request? = synchronized(recordedRequests) {
            recordedRequests.lastOrNull()
        }

        fun allRequests(): List<Request> = synchronized(recordedRequests) {
            recordedRequests.toList()
        }

        private data class CannedResponse(
            val code: Int,
            val body: String,
            val headers: Map<String, String>,
            val responseHost: String?,
        )
    }
}
