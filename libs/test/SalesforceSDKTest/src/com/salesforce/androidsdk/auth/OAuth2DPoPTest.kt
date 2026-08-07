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

import android.app.Instrumentation
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.TestForceApp
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.dpop.DPoPKeyManager
import com.salesforce.androidsdk.auth.dpop.DPoPNonceCache
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests that OAuth2 attaches a DPoP proof header on the refresh, nonce-retry, and
 * identity-service paths based on per-credential state — not the process-wide
 * `SalesforceSDKManager.isUseDPoP()` flag.
 */
@RunWith(AndroidJUnit4::class)
class OAuth2DPoPTest {

    private lateinit var httpAccess: CapturingHttpAccess
    private lateinit var credentialsIdentifier: String
    private lateinit var alias: String
    private var originalUseDPoP: Boolean = false

    @Before
    fun setUp() {
        val app = Instrumentation.newApplication(
            TestForceApp::class.java,
            InstrumentationRegistry.getInstrumentation().context
        )
        InstrumentationRegistry.getInstrumentation().callApplicationOnCreate(app)

        originalUseDPoP = SalesforceSDKManager.getInstance().useDPoP
        httpAccess = CapturingHttpAccess()
        credentialsIdentifier = "oauth2-dpop-test-${UUID.randomUUID()}"
        alias = DPoPKeyManager.aliasForCredentialsIdentifier(credentialsIdentifier)
    }

    @After
    fun tearDown() {
        SalesforceSDKManager.getInstance().useDPoP = originalUseDPoP
        DPoPKeyManager.deleteKeyPair(alias)
    }

    /** Post-flip refresh for a DPoP-bound credential still attaches a proof. */
    @Test
    fun test_refreshAuthToken_flagOff_credentialDPoPBound_attachesProof() {
        SalesforceSDKManager.getInstance().useDPoP = false
        DPoPKeyManager.generateOrLoadKeyPair(alias)
        httpAccess.enqueueTokenSuccess()

        OAuth2.refreshAuthToken(
            httpAccess,
            URI.create("https://example-token.test/"),
            "test-client-id",
            "test-refresh-token",
            null,
            credentialsIdentifier,
            "DPoP"
        )

        val recorded = httpAccess.lastRequest()
        assertNotNull("refresh request should have been sent", recorded)
        assertNotNull(
            "DPoP header must be attached on refresh when credential is DPoP-bound",
            recorded!!.header("DPoP")
        )
    }

    /** Bearer credential must never carry DPoP on refresh, regardless of flag. */
    @Test
    fun test_refreshAuthToken_bearerCredential_flagOffOrOn_neverAttachesProof() {
        SalesforceSDKManager.getInstance().useDPoP = false
        httpAccess.enqueueTokenSuccess()

        OAuth2.refreshAuthToken(
            httpAccess,
            URI.create("https://example-token.test/"),
            "test-client-id",
            "test-refresh-token",
            null,
            credentialsIdentifier,
            null
        )
        assertNull(
            "Bearer credential must not carry DPoP header (flag off)",
            httpAccess.lastRequest()!!.header("DPoP")
        )

        SalesforceSDKManager.getInstance().useDPoP = true
        httpAccess.enqueueTokenSuccess()
        OAuth2.refreshAuthToken(
            httpAccess,
            URI.create("https://example-token.test/"),
            "test-client-id",
            "test-refresh-token",
            null,
            credentialsIdentifier,
            null
        )
        assertNull(
            "Bearer credential must not carry DPoP header (flag on)",
            httpAccess.lastRequest()!!.header("DPoP")
        )
    }

    /** `use_dpop_nonce` retry re-attaches a proof and consumes the harvested nonce. */
    @Test
    fun test_refreshAuthToken_nonceChallenge_retryAttachesProof_flagOff() {
        SalesforceSDKManager.getInstance().useDPoP = false
        DPoPKeyManager.generateOrLoadKeyPair(alias)

        val host = "example-nonce.test"
        DPoPNonceCache.clear(credentialsIdentifier)

        // First response: 400 use_dpop_nonce + DPoP-Nonce header for harvest.
        httpAccess.enqueue(
            code = 400,
            body = "{\"error\":\"use_dpop_nonce\"}",
            headers = mapOf("DPoP-Nonce" to "srv-nonce-abc")
        )
        // Second response: 200 success — expected after nonce retry.
        httpAccess.enqueueTokenSuccess()

        OAuth2.refreshAuthToken(
            httpAccess,
            URI.create("https://$host/"),
            "test-client-id",
            "test-refresh-token",
            null,
            credentialsIdentifier,
            "DPoP"
        )

        val requests = httpAccess.allRequests()
        assertEquals("Expected exactly two token endpoint requests", 2, requests.size)
        assertNotNull("Initial request should carry a DPoP proof", requests[0].header("DPoP"))
        assertNotNull("Retry request should carry a DPoP proof", requests[1].header("DPoP"))
        // The retry proof is minted after the nonce is harvested, so it must differ from the first.
        assertTrue(
            "Retry proof header should be re-minted (i.e. differ from the initial one)",
            requests[0].header("DPoP") != requests[1].header("DPoP")
        )
    }

    /** Identity-service call attaches DPoP for a DPoP-bound credential even with flag off. */
    @Test
    fun test_callIdentityService_flagOff_credentialDPoPBound_attachesProof() {
        SalesforceSDKManager.getInstance().useDPoP = false
        DPoPKeyManager.generateOrLoadKeyPair(alias)
        httpAccess.enqueueIdentitySuccess()

        OAuth2.callIdentityService(
            httpAccess,
            "https://example-id.test/id/orgId/userId",
            "test-access-token",
            "DPoP",
            credentialsIdentifier
        )

        val recorded = httpAccess.lastRequest()
        assertNotNull("identity request should have been sent", recorded)
        assertNotNull(
            "DPoP header must be attached on identity fetch when credential is DPoP-bound",
            recorded!!.header("DPoP")
        )
        assertEquals(
            "Authorization scheme should be DPoP when tokenType==\"DPoP\"",
            "DPoP test-access-token",
            recorded.header("Authorization")
        )
    }

    /**
     * Bearer credential must not carry DPoP header on the identity endpoint.
     */
    @Test
    fun test_callIdentityService_bearerCredential_neverAttachesProof() {
        SalesforceSDKManager.getInstance().useDPoP = false
        httpAccess.enqueueIdentitySuccess()

        OAuth2.callIdentityService(
            httpAccess,
            "https://example-id.test/id/orgId/userId",
            "test-access-token",
            null,
            credentialsIdentifier
        )

        val recorded = httpAccess.lastRequest()
        assertNotNull(recorded)
        assertNull("Bearer identity call must not carry DPoP header", recorded!!.header("DPoP"))
        assertEquals(
            "Authorization scheme should be Bearer when tokenType is null",
            "Bearer test-access-token",
            recorded.header("Authorization")
        )
    }

    /**
     * A HttpAccess subclass that installs an OkHttp interceptor to (a) capture every outbound
     * request and (b) return canned responses without touching the network. Avoids the need for
     * the MockWebServer dependency, which is not on this module's test classpath.
     */
    private class CapturingHttpAccess : HttpAccess(null, "dummy-agent") {

        private val recordedRequests = mutableListOf<Request>()
        private val enqueuedResponses = ArrayDeque<CannedResponse>()
        private val cursor = AtomicInteger(0)

        private val capturingInterceptor = Interceptor { chain ->
            val req = chain.request()
            synchronized(recordedRequests) { recordedRequests += req }
            val canned = synchronized(enqueuedResponses) {
                enqueuedResponses.removeFirstOrNull()
            } ?: CannedResponse(200, "{}", emptyMap())
            val builder = Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(canned.code)
                .message(if (canned.code < 300) "OK" else "ERR")
                .body(canned.body.toResponseBody("application/json".toMediaType()))
            canned.headers.forEach { (k, v) -> builder.addHeader(k, v) }
            builder.build()
        }

        override fun createNewClientBuilder(): OkHttpClient.Builder =
            OkHttpClient.Builder().addInterceptor(capturingInterceptor)

        fun enqueue(code: Int, body: String, headers: Map<String, String> = emptyMap()) {
            synchronized(enqueuedResponses) { enqueuedResponses.addLast(CannedResponse(code, body, headers)) }
        }

        fun enqueueTokenSuccess() {
            enqueue(
                200,
                """{"access_token":"new-access-token","instance_url":"https://instance.test",
                    "id":"https://example-id.test/id/00Dxxxxx/005xxxxx",
                    "token_type":"Bearer"}""".trimIndent(),
                mapOf("DPoP-Nonce" to "srv-nonce-post-success")
            )
        }

        fun enqueueIdentitySuccess() {
            // Minimal identity response — enough JSON keys for IdServiceResponse to parse without NPE.
            enqueue(
                200,
                """{"id":"https://example-id.test/id/00Dxxxxx/005xxxxx",
                    "user_id":"005xxxxx","organization_id":"00Dxxxxx",
                    "username":"unit@test.example","display_name":"unit","email":"unit@test.example",
                    "urls":{},"active":true,"user_type":"STANDARD",
                    "language":"en_US","locale":"en_US","utcOffset":0}""".trimIndent()
            )
        }

        fun lastRequest(): Request? = synchronized(recordedRequests) {
            recordedRequests.lastOrNull()
        }

        fun allRequests(): List<Request> = synchronized(recordedRequests) {
            recordedRequests.toList()
        }

        private data class CannedResponse(val code: Int, val body: String, val headers: Map<String, String>)
    }
}
