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
package com.salesforce.androidsdk.rest

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salesforce.androidsdk.auth.dpop.DPoPKeyManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Unit tests for RestClient's per-request DPoP gate at API-call time.
 *
 * Exercises OAuthRefreshInterceptor.buildAuthenticatedRequest via
 * a mocked OkHttp Chain, capturing the outbound Request to assert
 * on Authorization scheme and presence/absence of the DPoP header.
 */
@RunWith(AndroidJUnit4::class)
class RestClientDPoPGateTests {

    private val aliasesToCleanUp = mutableListOf<String>()

    @After
    fun tearDown() {
        aliasesToCleanUp.forEach { DPoPKeyManager.deleteKeyPair(it) }
        aliasesToCleanUp.clear()
    }

    private fun trackId(name: String): String {
        val id = "restclient_${name}_${UUID.randomUUID()}"
        aliasesToCleanUp.add(DPoPKeyManager.aliasForCredentialsIdentifier(id))
        return id
    }

    private fun captureAuthenticatedRequest(interceptor: RestClient.OAuthRefreshInterceptor): Request {
        val outbound = Request.Builder()
            .url("https://instance.example.com/services/data/v65.0/query")
            .get()
            .build()
        val outboundSlot = slot<Request>()
        val response = Response.Builder()
            .request(outbound)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()
        val chain = mockk<Interceptor.Chain>(relaxed = true) {
            every { request() } returns outbound
            every { proceed(capture(outboundSlot)) } returns response
        }
        interceptor.intercept(chain)
        return outboundSlot.captured
    }

    // Credential is DPoP-bound (tokenType="DPoP") — outbound API request
    // carries a DPoP header AND uses the DPoP Authorization scheme.
    @Test
    fun test_givenDPoPBoundCredential_whenIntercept_thenAttachesProofAndDPoPScheme() {
        val id = trackId("sc2_dpop_bound")
        val interceptor = RestClient.OAuthRefreshInterceptor(
            null,
            "__ACCESS_TOKEN__",
            "DPoP",
            id,
            null,
        )

        val captured = captureAuthenticatedRequest(interceptor)

        assertNotNull("Expected DPoP header on outbound request", captured.header("DPoP"))
        assertTrue(
            "Expected DPoP header to be non-blank",
            !captured.header("DPoP").isNullOrBlank(),
        )
        val auth = captured.header("Authorization")
        assertNotNull(auth)
        assertTrue(
            "Expected Authorization to use DPoP scheme but got: $auth",
            auth!!.startsWith("DPoP "),
        )
    }

    // Bearer credential — no DPoP header, Bearer Authorization scheme.
    @Test
    fun test_givenBearerCredential_whenIntercept_thenNoDPoPHeaderAndBearerScheme() {
        val id = trackId("sc3_bearer")
        val interceptor = RestClient.OAuthRefreshInterceptor(
            null,
            "__ACCESS_TOKEN__",
            "Bearer",
            id,
            null,
        )

        val captured = captureAuthenticatedRequest(interceptor)

        assertNull(
            "Did not expect DPoP header for Bearer credential",
            captured.header("DPoP"),
        )
        val auth = captured.header("Authorization")
        assertNotNull(auth)
        assertTrue(
            "Expected Authorization to use Bearer scheme but got: $auth",
            auth!!.startsWith("Bearer "),
        )
    }

    // Belt-and-suspenders: no tokenType, but a key pair exists for the credential
    // → interceptor still attaches a DPoP proof (Authorization stays Bearer
    // because setAuthHeader keys off exact tokenType match — this asymmetry
    // is intentional and documented).
    @Test
    fun test_givenNoTokenTypeButExistingKeyPair_whenIntercept_thenAttachesProof() {
        val id = trackId("belt_haskeypair")
        DPoPKeyManager.generateOrLoadKeyPair(DPoPKeyManager.aliasForCredentialsIdentifier(id))
        val interceptor = RestClient.OAuthRefreshInterceptor(
            null,
            "__ACCESS_TOKEN__",
            null,
            id,
            null,
        )

        val captured = captureAuthenticatedRequest(interceptor)

        assertNotNull(
            "Expected DPoP header when a key pair exists for the credential",
            captured.header("DPoP"),
        )
    }

    // Null credentialsIdentifier short-circuits — no DPoP header regardless of tokenType.
    @Test
    fun test_givenNullCredentialsIdentifier_whenIntercept_thenNoDPoPHeader() {
        val interceptor = RestClient.OAuthRefreshInterceptor(
            null,
            "__ACCESS_TOKEN__",
            "DPoP",
            null,
            null,
        )

        val captured = captureAuthenticatedRequest(interceptor)

        assertNull(
            "Did not expect DPoP header when credentialsIdentifier is null",
            captured.header("DPoP"),
        )
    }
}
