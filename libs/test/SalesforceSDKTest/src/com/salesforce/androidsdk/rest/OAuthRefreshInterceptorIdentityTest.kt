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
import com.salesforce.androidsdk.security.BiometricAuthenticationManager
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI

@SmallTest
@RunWith(AndroidJUnit4::class)
class OAuthRefreshInterceptorIdentityTest {

    private val instanceUrl = "https://instance.example.com"
    private val identityUrl = "https://identity.example.com/id/orgId/userId"
    private fun clientInfo(configuredIdentityUrl: URI?) = RestClient.ClientInfo(
        URI.create(instanceUrl), URI.create("https://login.example.com"), configuredIdentityUrl,
        "account", "user", "userId", "orgId",
        null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null,
    )
    private lateinit var sdkManager: SalesforceSDKManager
    private val credentialsId = "identity-refresh-test"
    private val keyAlias = DPoPKeyManager.aliasForCredentialsIdentifier(credentialsId)

    @Before
    fun setUp() {
        mockkObject(SalesforceSDKManager)
        sdkManager = mockk(relaxed = true)
        every { SalesforceSDKManager.getInstance() } returns sdkManager
        every { sdkManager.useDPoP } returns true
        mockkStatic(SalesforceSDKLogger::class)
        every { SalesforceSDKLogger.d(any(), any()) } just runs
    }

    @After
    fun tearDown() {
        DPoPKeyManager.deleteKeyPair(keyAlias)
        unmockkAll()
    }

    private fun response(request: Request, code: Int, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body(body.toResponseBody())
            .build()

    private fun exercise(
        url: String,
        code: Int = 403,
        body: String = "Bad_OAuth_Token",
        allowRefresh: Boolean = true,
        secondCode: Int = 200,
        tokenType: String = "Bearer",
        configuredIdentityUrl: URI? = URI.create(identityUrl),
        hasRefreshProvider: Boolean = true,
    ): Triple<Response, List<Request>, Int> {
        val biometricManager = mockk<BiometricAuthenticationManager>()
        every { biometricManager.shouldAllowRefresh() } returns allowRefresh
        every { sdkManager.biometricAuthenticationManager } returns biometricManager

        var refreshCount = 0
        val provider = mockk<RestClient.AuthTokenProvider>()
        every { provider.getNewAuthToken() } answers {
            refreshCount++
            "new-token"
        }
        every { provider.getInstanceUrl() } returns instanceUrl
        every { provider.getTokenType() } returns tokenType
        every { provider.getUiSid() } returns null
        val interceptor = RestClient.OAuthRefreshInterceptor(
            clientInfo(configuredIdentityUrl), "old-token", tokenType,
            if (tokenType == "DPoP") credentialsId else null,
            if (hasRefreshProvider) provider else null,
        )
        val request = Request.Builder().url(url).get().build()
        val sent = mutableListOf<Request>()
        val chain = mockk<Interceptor.Chain> {
            every { request() } returns request
            every { proceed(any()) } answers {
                val outbound = firstArg<Request>()
                sent.add(outbound)
                if (sent.size == 1) response(outbound, code, body)
                else response(outbound, secondCode, body)
            }
        }
        return Triple(interceptor.intercept(chain), sent, refreshCount)
    }

    @Test
    fun test_givenIdentity403BadOAuthToken_whenIntercept_thenRefreshesAndRetriesOnce() {
        val (result, sent, refreshCount) = exercise(identityUrl, secondCode = 403)

        assertEquals(403, result.code)
        assertEquals(1, refreshCount)
        assertEquals(2, sent.size)
        assertEquals("Bearer old-token", sent[0].header("Authorization"))
        assertEquals("Bearer new-token", sent[1].header("Authorization"))
    }

    @Test
    fun test_givenUnrelated403BadOAuthToken_whenIntercept_thenDoesNotRefresh() {
        val (result, sent, refreshCount) = exercise("$instanceUrl/services/data/v65.0/query")

        assertEquals(403, result.code)
        assertEquals(0, refreshCount)
        assertEquals(1, sent.size)
        assertEquals("Bad_OAuth_Token", result.body.string())
    }

    @Test
    fun test_givenIdentity403WithDPoP_whenIntercept_thenRebuildsProofForRefreshedToken() {
        DPoPKeyManager.generateOrLoadKeyPair(keyAlias)
        val (result, sent, refreshCount) = exercise(identityUrl, tokenType = "DPoP")

        assertEquals(200, result.code)
        assertEquals(1, refreshCount)
        assertEquals(2, sent.size)
        assertEquals("DPoP old-token", sent[0].header("Authorization"))
        assertEquals("DPoP new-token", sent[1].header("Authorization"))
        assertNotNull(sent[0].header("DPoP"))
        assertNotNull(sent[1].header("DPoP"))
        assertNotEquals(sent[0].header("DPoP"), sent[1].header("DPoP"))
    }

    @Test
    fun test_givenDataEndpoint401_whenIntercept_thenStillRefreshes() {
        val (_, sent, refreshCount) = exercise(
            "$instanceUrl/services/data/v65.0/query", code = 401, body = "Unauthorized",
        )

        assertEquals(1, refreshCount)
        assertEquals(2, sent.size)
        assertNull(sent[0].header("DPoP"))
    }

    @Test
    fun test_givenDifferentIdentity403_whenIntercept_thenRefreshes() {
        val (_, sent, refreshCount) = exercise("https://identity.example.com/id/otherOrg/otherUser")

        assertEquals(1, refreshCount)
        assertEquals(2, sent.size)
    }

    @Test
    fun test_givenCommunityIdentity403_whenIntercept_thenRefreshes() {
        val communityIdentityUrl = "https://identity.example.com/customers/id/orgId/userId"
        val (_, sent, refreshCount) = exercise(
            communityIdentityUrl,
            configuredIdentityUrl = URI.create(communityIdentityUrl),
        )

        assertEquals(1, refreshCount)
        assertEquals(2, sent.size)
    }

    @Test
    fun test_givenCommunityIdentity403WithQuery_whenIntercept_thenRefreshes() {
        val communityIdentityUrl = "https://identity.example.com/customers/id/orgId/userId"
        val (_, sent, refreshCount) = exercise(
            "$communityIdentityUrl?format=json&version=latest",
            configuredIdentityUrl = URI.create(communityIdentityUrl),
        )

        assertEquals(1, refreshCount)
        assertEquals(2, sent.size)
    }

    @Test
    fun test_givenIdentity403WithoutConfiguredIdentityUrl_whenIntercept_thenRefreshes() {
        val (_, sent, refreshCount) = exercise(identityUrl, configuredIdentityUrl = null)

        assertEquals(1, refreshCount)
        assertEquals(2, sent.size)
    }

    @Test
    fun test_givenOtherIdPath403_whenIntercept_thenDoesNotRefresh() {
        val (_, sent, refreshCount) = exercise("https://identity.example.com/id/orgId/userId/other")

        assertEquals(0, refreshCount)
        assertEquals(1, sent.size)
    }

    @Test
    fun test_givenCommunityIdentityPathWithExtraSegment_whenIntercept_thenDoesNotRefresh() {
        val communityIdentityUrl = "https://identity.example.com/customers/id/orgId/userId"
        val (_, sent, refreshCount) = exercise(
            "$communityIdentityUrl/other",
            configuredIdentityUrl = URI.create(communityIdentityUrl),
        )

        assertEquals(0, refreshCount)
        assertEquals(1, sent.size)
    }

    @Test
    fun test_givenCommunityIdentityPathOnDifferentHost_whenIntercept_thenDoesNotRefresh() {
        val communityIdentityUrl = "https://identity.example.com/customers/id/orgId/userId"
        val (_, sent, refreshCount) = exercise(
            "https://other.example.com/customers/id/orgId/userId?format=json",
            configuredIdentityUrl = URI.create(communityIdentityUrl),
        )

        assertEquals(0, refreshCount)
        assertEquals(1, sent.size)
    }

    @Test
    fun test_givenIdentity403OtherBody_whenIntercept_thenDoesNotRefresh() {
        val (result, sent, refreshCount) = exercise(identityUrl, body = "Forbidden")

        assertEquals(0, refreshCount)
        assertEquals(1, sent.size)
        assertEquals("Forbidden", result.body.string())
    }

    @Test
    fun test_givenIdentity403TokenPrefixWithExtraText_thenDoesNotRefresh() {
        val body = "Bad_OAuth_Token_extra"
        val (result, sent, refreshCount) = exercise(identityUrl, body = body)

        assertEquals(0, refreshCount)
        assertEquals(1, sent.size)
        assertEquals(body, result.body.string())
    }

    @Test
    fun test_givenBiometricLock_whenIdentity403_thenDoesNotRefresh() {
        val (result, sent, refreshCount) = exercise(identityUrl, allowRefresh = false)

        assertEquals(0, refreshCount)
        assertEquals(1, sent.size)
        assertEquals("Bad_OAuth_Token", result.body.string())
    }

    @Test
    fun test_givenNoRefreshProvider_whenIdentity403_thenReturnsOriginalResponse() {
        val (result, sent, refreshCount) = exercise(identityUrl, hasRefreshProvider = false)

        assertEquals(403, result.code)
        assertEquals(0, refreshCount)
        assertEquals(1, sent.size)
        assertEquals("Bad_OAuth_Token", result.body.string())
    }

    @Test
    fun test_givenNoRefreshProvider_whenData401_thenDoesNotRetry() {
        val (result, sent, refreshCount) = exercise(
            "$instanceUrl/services/data/v65.0/query",
            code = 401,
            body = "Unauthorized",
            hasRefreshProvider = false,
        )

        assertEquals(401, result.code)
        assertEquals(0, refreshCount)
        assertEquals(1, sent.size)
        assertEquals("Unauthorized", result.body.string())
    }

    @Test
    fun test_givenNoRefreshProvider_whenOAuth403_thenDoesNotRetry() {
        val (result, sent, refreshCount) = exercise(
            "$instanceUrl/services/oauth2/userinfo",
            hasRefreshProvider = false,
        )

        assertEquals(403, result.code)
        assertEquals(0, refreshCount)
        assertEquals(1, sent.size)
        assertEquals("Bad_OAuth_Token", result.body.string())
    }

    @Test
    fun test_givenOAuthEndpoint403_whenIntercept_thenStillRefreshes() {
        val (_, sent, refreshCount) = exercise("$instanceUrl/services/oauth2/userinfo")

        assertEquals(1, refreshCount)
        assertEquals(2, sent.size)
    }
}
