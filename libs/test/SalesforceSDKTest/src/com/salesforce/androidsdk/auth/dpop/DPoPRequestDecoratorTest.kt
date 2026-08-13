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
package com.salesforce.androidsdk.auth.dpop

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit tests for [DPoPRequestDecorator], the public convenience API for stamping
 * Authorization and DPoP proof headers on requests built outside RestClient.
 */
@RunWith(AndroidJUnit4::class)
class DPoPRequestDecoratorTest {

    private val testScope = "test_dpop_decorator_scope_${System.currentTimeMillis()}"
    private val aliasesToCleanUp = mutableListOf<String>()

    @After
    fun tearDown() {
        DPoPKeyManager.deleteKeyPair(DPoPKeyManager.aliasForCredentialsIdentifier(testScope))
        aliasesToCleanUp.forEach { DPoPKeyManager.deleteKeyPair(it) }
        aliasesToCleanUp.clear()
    }

    private fun seedKeyPair(scope: String) {
        DPoPKeyManager.generateOrLoadKeyPair(DPoPKeyManager.aliasForCredentialsIdentifier(scope))
    }

    private fun requestBuilder(): Request.Builder =
        Request.Builder()
            .url("https://test.salesforce.com/services/data")
            .get()

    private fun userAccount(
        authToken: String? = "__ACCESS_TOKEN__",
        tokenType: String?,
        credentialsIdentifier: String? = testScope,
    ): UserAccount = UserAccountBuilder.getInstance()
        .accountName("account")
        .username("user@example.com")
        .authToken(authToken)
        .refreshToken("__REFRESH_TOKEN__")
        .instanceServer("https://test.salesforce.com")
        .loginServer("https://login.salesforce.com")
        .idUrl("https://login.salesforce.com/id/orgId/userId")
        .clientId("client-id")
        .orgId("orgId")
        .userId("userId")
        .tokenType(tokenType)
        .credentialsIdentifier(credentialsIdentifier)
        .build()

    private fun response(
        code: Int,
        body: String = "",
        dpopNonce: String? = null,
    ): Response {
        val builder = Response.Builder()
            .request(requestBuilder().build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("msg")
            .body(body.toResponseBody("application/json".toMediaType()))
        if (dpopNonce != null) builder.header(DPoPRequestDecorator.DPOP_NONCE_HEADER, dpopNonce)
        return builder.build()
    }

    @Test
    fun applyAuthHeaders_dpopAccount_bothHeadersSet() {
        seedKeyPair(testScope)
        val builder = requestBuilder()

        DPoPRequestDecorator.applyAuthHeaders(builder, userAccount(tokenType = "DPoP"))

        val request = builder.build()
        val auth = request.header("Authorization")
        assertNotNull(auth)
        assertTrue("Expected DPoP scheme but got: $auth", auth!!.startsWith("DPoP "))
        val dpop = request.header(DPoPRequestDecorator.DPOP_HEADER)
        assertNotNull("Expected DPoP proof header", dpop)
        assertEquals("Expected a 3-part JWT proof", 3, dpop!!.split(".").size)
    }

    @Test
    fun applyAuthHeaders_bearerAccount_onlyAuthorizationHeader() {
        val builder = requestBuilder()

        DPoPRequestDecorator.applyAuthHeaders(builder, userAccount(tokenType = "Bearer"))

        val request = builder.build()
        val auth = request.header("Authorization")
        assertNotNull(auth)
        assertTrue("Expected Bearer scheme but got: $auth", auth!!.startsWith("Bearer "))
        assertNull(request.header(DPoPRequestDecorator.DPOP_HEADER))
    }

    @Test
    fun applyAuthHeaders_dpopAccount_isUseDPoPFalse_proofStillAttached() {
        val sdkManager = com.salesforce.androidsdk.app.SalesforceSDKManager.getInstance()
        val original = sdkManager.useDPoP
        try {
            sdkManager.useDPoP = false
            seedKeyPair(testScope)
            val builder = requestBuilder()

            DPoPRequestDecorator.applyAuthHeaders(builder, userAccount(tokenType = "DPoP"))

            assertNotNull(
                "DPoP proof must be attached for a DPoP-bound credential regardless of useDPoP",
                builder.build().header(DPoPRequestDecorator.DPOP_HEADER),
            )
        } finally {
            sdkManager.useDPoP = original
        }
    }

    @Test
    fun applyAuthHeaders_nullTokenType_withKeypair_proofAttached() {
        seedKeyPair(testScope)
        val builder = requestBuilder()

        DPoPRequestDecorator.applyAuthHeaders(builder, userAccount(tokenType = null))

        assertNotNull(
            "DPoP proof must be attached when a key pair exists and tokenType is null",
            builder.build().header(DPoPRequestDecorator.DPOP_HEADER),
        )
    }

    @Test
    fun applyAuthHeaders_nullTokenType_noKeypair_bearerOnly() {
        val scope = "test_dpop_decorator_nokey_${System.currentTimeMillis()}"
        aliasesToCleanUp.add(DPoPKeyManager.aliasForCredentialsIdentifier(scope))
        val builder = requestBuilder()

        DPoPRequestDecorator.applyAuthHeaders(
            builder,
            userAccount(tokenType = null, credentialsIdentifier = scope),
        )

        val request = builder.build()
        val auth = request.header("Authorization")
        assertNotNull(auth)
        assertTrue("Expected Bearer scheme but got: $auth", auth!!.startsWith("Bearer "))
        assertNull(request.header(DPoPRequestDecorator.DPOP_HEADER))
    }

    @Test
    fun isNonceChallenge_400_useDpopNonceBody_returnsTrue() {
        assertTrue(DPoPRequestDecorator.isNonceChallenge(response(400, """{"error":"use_dpop_nonce"}""")))
    }

    @Test
    fun isNonceChallenge_401_dpopNonceHeader_returnsTrue() {
        assertTrue(DPoPRequestDecorator.isNonceChallenge(response(401, dpopNonce = "somevalue")))
    }

    @Test
    fun isNonceChallenge_200_returnsFalse() {
        assertFalse(DPoPRequestDecorator.isNonceChallenge(response(200, "{}")))
    }

    @Test
    fun isNonceChallenge_401_noNonceHeader_returnsFalse() {
        assertFalse(DPoPRequestDecorator.isNonceChallenge(response(401, "{}")))
    }
}
