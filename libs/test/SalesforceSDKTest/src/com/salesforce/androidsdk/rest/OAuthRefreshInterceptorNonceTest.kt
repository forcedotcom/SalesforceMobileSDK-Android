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
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.dpop.DPoPKeyManager
import com.salesforce.androidsdk.auth.dpop.DPoPNonceCache
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI

/**
 * Tests for DPoP nonce handling in OAuthRefreshInterceptor.
 *
 * The interceptor proactively includes a cached nonce in each DPoP proof. On HTTP 400
 * use_dpop_nonce it harvests DPoP-Nonce and retries once. It does not harvest from
 * successful resource-server responses and does not treat 401 as a nonce retry.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class OAuthRefreshInterceptorNonceTest {

    private val credentialsId = "test-credentials-id"
    private val instanceHost = "test.salesforce.com"
    private val authToken = "test-access-token"
    private val dpopKeyAlias = DPoPKeyManager.aliasForCredentialsIdentifier(credentialsId)

    private lateinit var clientInfo: RestClient.ClientInfo

    @Before
    fun setUp() {
        DPoPNonceCache.clearAll()
        DPoPKeyManager.generateOrLoadKeyPair(dpopKeyAlias)

        clientInfo = RestClient.ClientInfo(
            URI.create("https://test.salesforce.com"),
            URI.create("https://login.salesforce.com"),
            URI.create("https://test.salesforce.com/id/orgId/userId"),
            "test@example.com",
            "test@example.com",
            "userId",
            "orgId",
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null
        )

        mockkObject(SalesforceSDKManager)
        val mockSdkManager = mockk<SalesforceSDKManager>(relaxed = true) {
            every { useDPoP } returns true
        }
        every { SalesforceSDKManager.getInstance() } returns mockSdkManager
    }

    @After
    fun tearDown() {
        DPoPNonceCache.clearAll()
        DPoPKeyManager.deleteKeyPair(dpopKeyAlias)
        unmockkAll()
    }

    // ---- helpers ----

    private fun buildInterceptor(tokenType: String = "DPoP"): RestClient.OAuthRefreshInterceptor =
        RestClient.OAuthRefreshInterceptor(clientInfo, authToken, tokenType, credentialsId, null)

    private fun buildRequest(): Request =
        Request.Builder()
            .url("https://test.salesforce.com/services/data/v62.0/query?q=SELECT+Id+FROM+Account")
            .get()
            .build()

    private fun successResponse(request: Request, dpopNonce: String? = null): Response {
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("{}".toResponseBody("application/json".toMediaType()))
        if (dpopNonce != null) builder.header("DPoP-Nonce", dpopNonce)
        return builder.build()
    }

    // ---- tests ----

    /**
     * When no nonce is in the cache, the DPoP proof is still attached but contains no nonce claim.
     * No retry happens — the resource server will fall through to the normal 401 refresh path
     * if it requires a nonce (which it won't, since nonce enforcement is only on /token).
     */
    @Test
    fun test_givenNoCachedNonce_whenRequestSent_thenProofHasNoNonceClaim() {
        val request = buildRequest()
        val interceptor = buildInterceptor()
        val chain = mockk<Interceptor.Chain> {
            every { request() } returns request
            every { proceed(any()) } returns successResponse(request)
        }

        interceptor.intercept(chain)

        assertNull(DPoPNonceCache.get(credentialsId, instanceHost))
    }

    /**
     * When a nonce was previously cached by the token exchange (OAuth2.java), the interceptor
     * includes it in the DPoP proof on every subsequent API call — no extra round-trip needed.
     */
    @Test
    fun test_givenNonceCachedByTokenExchange_whenRequestSent_thenProofContainsNonceClaim() {
        // Simulate a nonce already populated by the token exchange in OAuth2.java.
        DPoPNonceCache.store(credentialsId, instanceHost, "token-exchange-nonce")
        val request = buildRequest()
        val interceptor = buildInterceptor()

        var capturedRequest: Request? = null
        val chain = mockk<Interceptor.Chain> {
            every { request() } returns request
            every { proceed(any()) } answers {
                capturedRequest = firstArg()
                successResponse(firstArg())
            }
        }

        interceptor.intercept(chain)

        val dpopHeader = capturedRequest?.header("DPoP")
        assertNotNull("DPoP header must be present", dpopHeader)
        val parts = dpopHeader!!.split(".")
        assertEquals(3, parts.size)
        val payloadJson = String(
            android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING),
            Charsets.UTF_8
        )
        assert(payloadJson.contains("token-exchange-nonce")) {
            "DPoP proof payload should contain the cached nonce. Payload: $payloadJson"
        }
    }

    /**
     * A DPoP-Nonce header on a successful resource-server response is not harvested.
     * Harvest happens only on HTTP 400 use_dpop_nonce.
     */
    @Test
    fun test_givenResourceServerResponseWithDPoPNonceHeader_whenRequestCompletes_thenNonceIsNotCached() {
        val request = buildRequest()
        val interceptor = buildInterceptor()
        val chain = mockk<Interceptor.Chain> {
            every { request() } returns request
            every { proceed(any()) } returns successResponse(request, dpopNonce = "resource-server-nonce")
        }

        interceptor.intercept(chain)

        // The interceptor must not harvest nonces from resource-server responses.
        assertNull(DPoPNonceCache.get(credentialsId, instanceHost))
    }

    /**
     * Bearer token flows are completely unaffected — no DPoP proof is attached,
     * and no nonce logic runs.
     */
    @Test
    fun test_givenBearerTokenType_whenRequestSent_thenNoDPoPHeaderAndNoCacheWrite() {
        val request = buildRequest()
        val interceptor = buildInterceptor(tokenType = "Bearer")

        var callCount = 0
        val chain = mockk<Interceptor.Chain> {
            every { request() } returns request
            every { proceed(any()) } answers {
                callCount++
                successResponse(firstArg())
            }
        }

        interceptor.intercept(chain)

        assertEquals(1, callCount)
        assertNull(DPoPNonceCache.get(credentialsId, instanceHost))
    }

    /**
     * Bearer session receives 400 use_dpop_nonce. The nonce retry guard requires
     * request.header("DPoP") != null — Bearer requests carry no DPoP header, so
     * the harvest+retry branch must not fire.
     */
    @Test
    fun test_givenBearerTokenType_when400UseDpopNonce_thenNoHarvestAndNoRetry() {
        val request = buildRequest()
        val interceptor = buildInterceptor(tokenType = "Bearer")
        var callCount = 0
        val chain = mockk<Interceptor.Chain> {
            every { request() } returns request
            every { proceed(any()) } answers {
                callCount++
                Response.Builder()
                    .request(firstArg())
                    .protocol(Protocol.HTTP_1_1)
                    .code(400)
                    .message("Bad Request")
                    .header("DPoP-Nonce", "should-not-be-harvested")
                    .body("""{"error":"use_dpop_nonce"}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
        }

        val response = interceptor.intercept(chain)

        assertEquals(1, callCount)
        assertEquals(400, response.code)
        assertNull(DPoPNonceCache.get(credentialsId, instanceHost))
    }

    private fun nonceChallengeResponse(request: Request, nonce: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(400)
            .message("Bad Request")
            .header("DPoP-Nonce", nonce)
            .body("""{"error":"use_dpop_nonce"}""".toResponseBody("application/json".toMediaType()))
            .build()

    @Test
    fun test_given400UseDpopNonce_whenIntercept_thenHarvestsNonceAndRetriesOnceWithProof() {
        val request = buildRequest()
        val interceptor = buildInterceptor()
        val captured = mutableListOf<Request>()
        val chain = mockk<Interceptor.Chain> {
            every { request() } returns request
            every { proceed(any()) } answers {
                val sent = firstArg<Request>()
                captured.add(sent)
                if (captured.size == 1) nonceChallengeResponse(sent, "n1")
                else successResponse(sent)
            }
        }

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        assertEquals(2, captured.size)
        assertEquals("n1", DPoPNonceCache.get(credentialsId, instanceHost))
        val retryProof = captured[1].header("DPoP")
        assertNotNull(retryProof)
        val payloadJson = String(
            android.util.Base64.decode(
                retryProof!!.split(".")[1],
                android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING
            ),
            Charsets.UTF_8
        )
        assert(payloadJson.contains("n1")) { "Retry proof should contain harvested nonce. Payload: $payloadJson" }
    }

    @Test
    fun test_given400UseDpopNonceTwice_whenIntercept_thenDoesNotLoop() {
        val request = buildRequest()
        val interceptor = buildInterceptor()
        var callCount = 0
        val chain = mockk<Interceptor.Chain> {
            every { request() } returns request
            every { proceed(any()) } answers {
                callCount++
                nonceChallengeResponse(firstArg(), "n-loop")
            }
        }

        val response = interceptor.intercept(chain)

        assertEquals(2, callCount)
        assertEquals(400, response.code)
    }

}
