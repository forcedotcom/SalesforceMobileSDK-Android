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
import org.junit.After
import org.junit.Before
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.X509TrustManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class RequestUserAgentWireTests {
    private val user = UserAccountBuilder.getInstance().orgId("test-org").userId("test-user").build()

    private lateinit var tls: SSLContext
    private lateinit var trust: X509TrustManager
    @Before fun createLoopbackTls() {
        // Disposable localhost-only fixture, not an application credential.
        val password = "changeit".toCharArray()
        val fixture = "MIID9AIBAzCCA54GCSqGSIb3DQEHAaCCA48EggOLMIIDhzCCAS4GCSqGSIb3DQEHAaCCAR8EggEbMIIBFzCCARMGCyqGSIb3DQEMCgECoIG9MIG6MGYGCSqGSIb3DQEFDTBZMDgGCSqGSIb3DQEFDDArBBQjANEe19QIjujJUCtwJvZ10gdIMwICJxACASAwDAYIKoZIhvcNAgkFADAdBglghkgBZQMEASoEEF01d3ljSB9VCzBTp+IkDGYEUMqpwJUutJ0n9TJ/NPF9NMdx44VcxTWJfCWjoY4IxbHbodISoESGmmTWJq2e/+F+VnUieqCDQ0jV10kY/DnjO0BAuzsLnIKLRj6HHcL+cUHrMUQwHwYJKoZIhvcNAQkUMRIeEABsAG8AbwBwAGIAYQBjAGswIQYJKoZIhvcNAQkVMRQEElRpbWUgMTc5MzMxODY2NzEwNDCCAlEGCSqGSIb3DQEHBqCCAkIwggI+AgEAMIICNwYJKoZIhvcNAQcBMGYGCSqGSIb3DQEFDTBZMDgGCSqGSIb3DQEFDDArBBSR4AYoiymwl5YpTS9FjDjzJg6RJAICJxACASAwDAYIKoZIhvcNAgkFADAdBglghkgBZQMEASoEEAXknv0NbVRpj/9j7gySIKeAggHAIamNbcJKYvAhT7FkXIEc0z3i6ItGpsilHtNPFRqtMSdLhR8q69UcRwVvijQj6JhS+TyfqSKCOqwtWm2jNV+XFIS5f0qYlpfC15KkuPlcrAog0L3Uxgn7L4aLhkhq1fe6vNxos4Xak1LZ3xD4u62gb8MGfFXxyPUi45uafzClROcpqsMS0QLZKPEjtUa45tGONo15Gul6t0fOpsjnG5aXgyzeUl/bWpAsIESJ3hOg4iZ7ovcv2kVHK8S/3noPgIhHYVJ1QH9iBMB4KXTAPhXwmd4IxJHaui1HIg407v3bz/b213bWJ3CpB93z5bhO2df4dA2zBOiZq2ljQnDeo9KPgKn82W7MBsxqncsbqXf/3s2fYok9YJwGx22tGk9Or7NdWh4Q0Gx1D4mFHqLmdnOJ+I+Fs99sOPFmt0EO6Jyn1qTS5qKlNA7i+qCG/3FF47puCn25E3cMFZAe3/mw7YMiCMx/Lbk1SVn1B1ygqJnMpjK9DgSKUpmtjdjjw5tMymZva1bEgWjL9AahKUjugY2Fun7JoPTz3fMaUIdoyLB7NXdylkqduENTEPwHCwzKHmHvobvh823LekwNnyUdsde0LjBNMDEwDQYJYIZIAWUDBAIBBQAEICpp9vbsL1dEnI1ctx4hkO4yC0RemAe0UCPq7zFyG+AvBBQp5gPR9iFpfP0jjis/thD4LYAd/gICJxA="
        val keys = KeyStore.getInstance("PKCS12").apply {
            load(android.util.Base64.decode(fixture, android.util.Base64.DEFAULT).inputStream(), password)
        }
        val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keys, password)
        }
        val certificate = keys.getCertificate("loopback") as java.security.cert.X509Certificate
        trust = object : X509TrustManager {
            override fun getAcceptedIssuers() = arrayOf(certificate)
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
                checkServerTrusted(chain, authType)
            }
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
                // Pin this disposable local key rather than rely on fixture expiry dates.
                require(chain.size == 1 && chain[0].publicKey == certificate.publicKey)
                chain[0].verify(certificate.publicKey)
            }
        }
        tls = SSLContext.getInstance("TLS").apply { init(managers.keyManagers, arrayOf(trust), null) }
    }

    @After fun cleanup() {
        TokenRequestCapturingHttpAccess.reset()
        SalesforceSDKManager.getInstance().isUiTesting = false
    }

    private fun builder() = TokenRequestCapturingHttpAccess(
        InstrumentationRegistry.getInstrumentation().targetContext,
    ).createNewClientBuilder().sslSocketFactory(tls.socketFactory, trust).hostnameVerifier { _, _ -> true }

    private fun request(port: Int, owned: Boolean = false) = Request.Builder()
        .url("https://localhost:$port/services/oauth2/token")
        .tag(UserAccount::class.java, if (owned) user else null).build()

    private fun runWire(downstreamAgent: Boolean, responses: List<Int>, restClient: Boolean = false) {
        SalesforceSDKManager.getInstance().isUiTesting = true
        TokenRequestCapturingHttpAccess.reset()
        val executor = Executors.newSingleThreadExecutor()
        tls.serverSocketFactory.createServerSocket(0).use { server ->
            server.soTimeout = 10000
            val wire = executor.submit<List<String>> {
                responses.map { code ->
                    server.accept().use { socket ->
                        socket.soTimeout = 10000
                        val reader = socket.getInputStream().bufferedReader()
                        val headers = generateSequence { reader.readLine().takeUnless { it.isEmpty() } }.toList()
                        val agent = headers.single { it.startsWith("User-Agent:", true) }.substringAfter(":").trim()
                        val location = if (code == 302) "Location: /services/oauth2/revoke\r\n" else ""
                        val retry = if (code == 503) "Retry-After: 0\r\n" else ""
                        socket.getOutputStream().write(
                            "HTTP/1.1 $code Test\r\n${location}${retry}Content-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(),
                        )
                        agent
                    }
                }
            }
            try {
                val clientBuilder = if (restClient) {
                    val info = RestClient.ClientInfo(
                        URI("https://localhost:${server.localPort}"), null, null,
                        null, null, "test-user", "test-org", null, null,
                        null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null,
                    )
                    val rest = RestClient(info, null,
                        TokenRequestCapturingHttpAccess(InstrumentationRegistry.getInstrumentation().targetContext), null)
                    rest.okHttpClientBuilder.sslSocketFactory(tls.socketFactory, trust).hostnameVerifier { _, _ -> true }
                } else builder()
                val client = clientBuilder.apply {
                    // RestClient appends its own UA interceptor after createNewClientBuilder().
                    if (downstreamAgent) addNetworkInterceptor(HttpAccess.UserAgentInterceptor("final-wire-agent"))
                }.build()
                try { client.newCall(request(server.localPort)).execute().close() }
                catch (e: Exception) { wire.get(10, TimeUnit.SECONDS); throw e }
                assertEquals(wire.get(10, TimeUnit.SECONDS),
                    TokenRequestCapturingHttpAccess.capturedRequests.value.map { it.userAgent })
            } finally { executor.shutdownNow() }
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
        tls.serverSocketFactory.createServerSocket(0).use { server ->
            val client = builder().build()
            val old = client.newCall(request(server.localPort, owned))
            if (reset) TokenRequestCapturingHttpAccess.reset() else {
                TokenRequestCapturingHttpAccess.retireUser(user)
                TokenRequestCapturingHttpAccess.userLoggedIn(user)
            }
            val executor = Executors.newSingleThreadExecutor()
            val response = executor.submit {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { /* Drain headers. */ }
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".toByteArray())
                }
            }
            try {
                old.execute().close()
                response.get(10, TimeUnit.SECONDS)
                assertTrue("Calls created before reset/retirement must not publish",
                    TokenRequestCapturingHttpAccess.capturedRequests.value.isEmpty())
            } finally { executor.shutdownNow() }
        }
    }
}
