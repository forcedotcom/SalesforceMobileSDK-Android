/*
 * Copyright (c) 2026-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the copyright notice, conditions and disclaimer are
 * retained. Neither salesforce.com nor contributors' names may be used to endorse
 * derived products without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED "AS IS", WITHOUT ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE LIABLE FOR ANY DAMAGES ARISING
 * FROM USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.samples.authflowtester

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.rest.RestClient
import java.net.URI
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class RequestUserAgentWireTests {
    private val user = UserAccountBuilder.getInstance().orgId("test-org").userId("test-user").build()

    private lateinit var serverCertificates: HandshakeCertificates
    private lateinit var clientCertificates: HandshakeCertificates
    @Before fun createLoopbackTls() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
    }

    @After fun cleanup() {
        TokenRequestCapturingHttpAccess.reset()
        SalesforceSDKManager.getInstance().isUiTesting = false
    }

    private fun builder() = TokenRequestCapturingHttpAccess(
        InstrumentationRegistry.getInstrumentation().targetContext,
    ).createNewClientBuilder().sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)

    private fun request(server: MockWebServer, owned: Boolean = false) = Request.Builder()
        .url(server.url("/services/oauth2/token"))
        .tag(UserAccount::class.java, if (owned) user else null).build()

    private fun runWire(downstreamAgent: Boolean, responses: List<Int>, restClient: Boolean = false) {
        SalesforceSDKManager.getInstance().isUiTesting = true
        TokenRequestCapturingHttpAccess.reset()
        MockWebServer().use { server ->
            server.useHttps(serverCertificates.sslSocketFactory(), false)
            responses.forEach { code ->
                server.enqueue(MockResponse().setResponseCode(code).apply {
                    if (code == 302) addHeader("Location", "/services/oauth2/revoke")
                    if (code == 503) addHeader("Retry-After", "0")
                })
            }
            server.start()
            try {
                val clientBuilder = if (restClient) {
                    val info = RestClient.ClientInfo(
                        URI(server.url("/").toString()), null, null,
                        null, null, "test-user", "test-org", null, null,
                        null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null,
                    )
                    val rest = RestClient(info, null,
                        TokenRequestCapturingHttpAccess(InstrumentationRegistry.getInstrumentation().targetContext), null)
                    rest.okHttpClientBuilder.sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
                } else builder()
                val client = clientBuilder.apply {
                    // RestClient appends its own UA interceptor after createNewClientBuilder().
                    if (downstreamAgent) addNetworkInterceptor(HttpAccess.UserAgentInterceptor("final-wire-agent"))
                }.build()
                client.newCall(request(server)).execute().close()
                val wire = responses.indices.map {
                    requireNotNull(server.takeRequest(10, TimeUnit.SECONDS)).getHeader("User-Agent")
                }
                assertEquals(wire,
                    TokenRequestCapturingHttpAccess.capturedRequests.value.map { it.userAgent })
            } finally { server.shutdown() }
        }
    }

    @Test fun test_givenHttpAccess_whenSent_thenCaptureMatchesWire() = runWire(false, listOf(200))
    @Test fun test_givenDownstreamRestUserAgent_whenSent_thenCaptureMatchesWire() = runWire(true, listOf(200))
    @Test fun test_givenRedirect_whenSent_thenEveryExchangeMatchesWire() = runWire(true, listOf(302, 200))
    @Test fun test_givenRetry_whenSent_thenEveryExchangeMatchesWire() = runWire(true, listOf(503, 200))
    @Test fun test_givenRealRestClient_whenRedirected_thenEveryExchangeMatchesWire() =
        runWire(false, listOf(302, 200), restClient = true)

    @Test fun test_givenOldCall_whenReset_thenLateRequestIsRejected() {
        assertOldCallRejected(owned = false, reset = true)
    }

    @Test fun test_givenOldOwnerCall_whenRetiredAndReloggedIn_thenLateRequestIsRejected() {
        assertOldCallRejected(owned = true, reset = false)
    }

    private fun assertOldCallRejected(owned: Boolean, reset: Boolean) {
        SalesforceSDKManager.getInstance().isUiTesting = true
        TokenRequestCapturingHttpAccess.reset()
        MockWebServer().use { server ->
            server.useHttps(serverCertificates.sslSocketFactory(), false)
            server.enqueue(MockResponse().setResponseCode(200))
            server.start()
            val client = builder().build()
            val old = client.newCall(request(server, owned))
            if (reset) TokenRequestCapturingHttpAccess.reset() else {
                TokenRequestCapturingHttpAccess.retireUser(user)
                TokenRequestCapturingHttpAccess.userLoggedIn(user)
            }
            try {
                old.execute().close()
                server.takeRequest(10, TimeUnit.SECONDS)
                assertTrue("Calls created before reset/retirement must not publish",
                    TokenRequestCapturingHttpAccess.capturedRequests.value.isEmpty())
            } finally { server.shutdown() }
        }
    }
}
