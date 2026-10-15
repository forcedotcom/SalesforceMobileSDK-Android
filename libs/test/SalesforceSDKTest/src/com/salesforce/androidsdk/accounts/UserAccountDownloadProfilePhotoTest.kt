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
package com.salesforce.androidsdk.accounts

import android.app.Instrumentation.newApplication
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.salesforce.androidsdk.TestForceApp
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.auth.HttpAccess.DEFAULT
import com.salesforce.androidsdk.auth.dpop.DPoPKeyManager.aliasForCredentialsIdentifier
import com.salesforce.androidsdk.auth.dpop.DPoPKeyManager.deleteKeyPair
import com.salesforce.androidsdk.auth.dpop.DPoPKeyManager.generateOrLoadKeyPair
import com.salesforce.androidsdk.auth.dpop.DPoPNonceCache.clear
import com.salesforce.androidsdk.auth.dpop.DPoPProofBuilder
import com.salesforce.androidsdk.auth.downloadProfilePhotoToFile
import com.salesforce.androidsdk.auth.reattachAuthOnRedirect
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.MediaType.Companion.toMediaType
import kotlinx.coroutines.runBlocking
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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.InetAddress
import java.net.ProtocolException
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.UUID.randomUUID
import kotlin.concurrent.thread

/**
 * Coverage for the profile photo download: it must be authenticated like any
 * other Salesforce API call (DPoP proof for DPoP-bound credentials, Bearer
 * otherwise) and must never disturb a cached photo when it fails.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class UserAccountDownloadProfilePhotoTest {

    private lateinit var httpAccess: CapturingHttpAccess
    private lateinit var originalDefaultHttpAccess: HttpAccess
    private lateinit var credentialsIdentifier: String
    private lateinit var alias: String
    private lateinit var destFile: File

    @Before
    fun setUp() {
        val app = newApplication(TestForceApp::class.java, getInstrumentation().context)
        getInstrumentation().callApplicationOnCreate(app)

        originalDefaultHttpAccess = DEFAULT
        httpAccess = CapturingHttpAccess()
        DEFAULT = httpAccess

        credentialsIdentifier = "photo-test-${randomUUID()}"
        alias = aliasForCredentialsIdentifier(credentialsIdentifier)
        clear(credentialsIdentifier)

        destFile = buildAccount(tokenType = null).profilePhotoFile!!
        destFile.delete()
    }

    @After
    fun tearDown() {
        DEFAULT = originalDefaultHttpAccess
        deleteKeyPair(alias)
        destFile.delete()
        File(destFile.parentFile, "${destFile.name}.download").delete()
    }

    @Test
    fun dpopCredential_sendsDPoPSchemeAndProofBoundToRequest() {
        generateOrLoadKeyPair(alias)
        httpAccess.enqueuePhoto()

        val ok = downloadProfilePhotoToFile(PHOTO_URL, destFile, TOKEN, "DPoP", credentialsIdentifier)

        assertTrue(ok)
        assertEquals(PHOTO_BYTES, destFile.readText())
        val request = httpAccess.allRequests().single()
        assertEquals("DPoP $TOKEN", request.header("Authorization"))
        val payload = dpopProofPayload(request)
        assertEquals("GET", payload.getString("htm"))
        assertEquals(PHOTO_URL, payload.getString("htu"))
        val expectedAth = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(TOKEN.toByteArray()),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        assertEquals(expectedAth, payload.getString("ath"))
    }

    @Test
    fun signingFailure_doesNotSendPhotoRequestOrReplaceCache() {
        generateOrLoadKeyPair(alias)
        destFile.writeText("old-photo")
        mockkObject(DPoPProofBuilder)
        try {
            every { DPoPProofBuilder.buildProof(any(), any(), any(), any(), any()) } throws
                IllegalStateException("signing unavailable")

            val ok = downloadProfilePhotoToFile(PHOTO_URL, destFile, TOKEN, "DPoP", credentialsIdentifier)

            assertFalse(ok)
            assertTrue(httpAccess.allRequests().isEmpty())
            assertEquals("old-photo", destFile.readText())
        } finally {
            unmockkObject(DPoPProofBuilder)
        }
    }

    @Test
    fun bearerCredential_sendsBearerAndNoDPoPHeader() {
        httpAccess.enqueuePhoto()

        val ok = downloadProfilePhotoToFile(PHOTO_URL, destFile, TOKEN, null, null)

        assertTrue(ok)
        val request = httpAccess.allRequests().single()
        assertEquals("Bearer $TOKEN", request.header("Authorization"))
        assertNull(request.header("DPoP"))
    }

    @Test
    fun nonceChallenge_retriesOnceWithFreshProofCarryingNonce() {
        generateOrLoadKeyPair(alias)
        httpAccess.enqueue(401, """{"error":"use_dpop_nonce"}""", mapOf("DPoP-Nonce" to "photo-nonce"))
        httpAccess.enqueuePhoto()

        val ok = downloadProfilePhotoToFile(PHOTO_URL, destFile, TOKEN, "DPoP", credentialsIdentifier)

        assertTrue(ok)
        val requests = httpAccess.allRequests()
        assertEquals(2, requests.size)
        val first = dpopProofPayload(requests[0])
        val retry = dpopProofPayload(requests[1])
        assertFalse(first.has("nonce"))
        assertEquals("photo-nonce", retry.getString("nonce"))
        assertNotEquals(first.getString("jti"), retry.getString("jti"))
        assertEquals(PHOTO_URL, retry.getString("htu"))
    }

    @Test
    fun nonceRetrySigningFailure_doesNotSendRetry() {
        val keyPair = generateOrLoadKeyPair(alias)
        val firstProof = DPoPProofBuilder.buildProof("GET", PHOTO_URL, keyPair, null, TOKEN)
        httpAccess.enqueue(401, """{"error":"use_dpop_nonce"}""", mapOf("DPoP-Nonce" to "photo-nonce"))
        var proofAttempts = 0
        mockkObject(DPoPProofBuilder)
        try {
            every { DPoPProofBuilder.buildProof(any(), any(), any(), any(), any()) } answers {
                if (proofAttempts++ == 0) firstProof else throw IllegalStateException("signing unavailable")
            }

            val ok = downloadProfilePhotoToFile(PHOTO_URL, destFile, TOKEN, "DPoP", credentialsIdentifier)

            assertFalse(ok)
            assertEquals(2, proofAttempts)
            assertEquals(1, httpAccess.allRequests().size)
            assertFalse(destFile.exists())
        } finally {
            unmockkObject(DPoPProofBuilder)
        }
    }

    @Test
    fun nonceChallengeRepeated_doesNotRetryMoreThanOnce() {
        generateOrLoadKeyPair(alias)
        repeat(3) {
            httpAccess.enqueue(401, """{"error":"use_dpop_nonce"}""", mapOf("DPoP-Nonce" to "n$it"))
        }

        val ok = downloadProfilePhotoToFile(PHOTO_URL, destFile, TOKEN, "DPoP", credentialsIdentifier)

        assertFalse(ok)
        assertEquals(2, httpAccess.allRequests().size)
    }

    @Test
    fun redirectToSalesforceHost_resignsProofForNewUrl() {
        generateOrLoadKeyPair(alias)
        val redirectedUrl = "https://files.my.salesforce.com/profilephoto/005/F"
        val redirected = Request.Builder().url(redirectedUrl).get().build()
        var captured: Request? = null
        val chain = mockk<Chain> {
            every { request() } returns redirected
            every { proceed(any()) } answers {
                captured = firstArg()
                Response.Builder().request(firstArg()).protocol(HTTP_1_1).code(200).message("OK")
                    .body("".toResponseBody()).build()
            }
        }

        reattachAuthOnRedirect(chain, PHOTO_URL, TOKEN, "DPoP", credentialsIdentifier)

        assertEquals("DPoP $TOKEN", captured?.header("Authorization"))
        assertEquals(redirectedUrl, dpopProofPayload(checkNotNull(captured)).getString("htu"))
    }

    @Test
    fun redirectSigningFailure_doesNotSendStaleProof() {
        generateOrLoadKeyPair(alias)
        val redirected = Request.Builder()
            .url("https://files.my.salesforce.com/profilephoto/005/F")
            .header("Authorization", "DPoP $TOKEN")
            .header("DPoP", "proof-for-previous-url")
            .build()
        val chain = mockk<Chain> {
            every { request() } returns redirected
            every { proceed(any()) } answers {
                Response.Builder().request(firstArg()).protocol(HTTP_1_1).code(200).message("OK")
                    .body("".toResponseBody()).build()
            }
        }
        mockkObject(DPoPProofBuilder)
        try {
            every { DPoPProofBuilder.buildProof(any(), any(), any(), any(), any()) } throws
                IllegalStateException("signing unavailable")

            assertThrows(ProtocolException::class.java) {
                reattachAuthOnRedirect(
                    chain, PHOTO_URL, TOKEN, "DPoP", credentialsIdentifier,
                    requireFreshDPoPProof = true,
                )
            }
            verify(exactly = 0) { chain.proceed(any()) }
        } finally {
            unmockkObject(DPoPProofBuilder)
        }
    }

    @Test
    fun realRedirect_resignsProofForEachRequestUrl() {
        generateOrLoadKeyPair(alias)
        DEFAULT = object : HttpAccess(null, "dummy-agent") {
            override fun createNewClientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
                .dns { listOf(InetAddress.getLoopbackAddress()) }
        }

        ServerSocket(0, 2, InetAddress.getLoopbackAddress()).use { server ->
            server.soTimeout = 15_000
            val initialUrl = "http://files.salesforce.com:${server.localPort}/profilephoto/005/F"
            val redirectedUrl = "http://files.my.salesforce.com:${server.localPort}/profilephoto/005/F"
            val requests = mutableListOf<WireRequest>()
            var serverFailure: Throwable? = null
            val serverThread = thread(name = "profile-photo-redirect-test") {
                try {
                    repeat(2) { hop ->
                        server.accept().use { socket ->
                            socket.soTimeout = 15_000
                            val reader = socket.getInputStream().bufferedReader()
                            val requestLine = reader.readLine()
                            val headers = mutableMapOf<String, String>()
                            while (true) {
                                val line = reader.readLine()
                                if (line.isNullOrEmpty()) break
                                val separator = line.indexOf(':')
                                if (separator > 0) {
                                    headers[line.substring(0, separator).lowercase()] =
                                        line.substring(separator + 1).trim()
                                }
                            }
                            requests += WireRequest(checkNotNull(requestLine), headers)
                            val response = if (hop == 0) {
                                "HTTP/1.1 302 Found\r\nLocation: $redirectedUrl\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                            } else {
                                "HTTP/1.1 200 OK\r\nContent-Length: ${PHOTO_BYTES.length}\r\nConnection: close\r\n\r\n$PHOTO_BYTES"
                            }
                            socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
                        }
                    }
                } catch (failure: Throwable) {
                    serverFailure = failure
                }
            }

            try {
                assertTrue(downloadProfilePhotoToFile(initialUrl, destFile, TOKEN, "DPoP", credentialsIdentifier))
            } finally {
                server.close()
                serverThread.join(15_000)
            }

            assertFalse("Redirect server did not stop", serverThread.isAlive)
            assertNull("Redirect server failed", serverFailure)
            assertEquals(PHOTO_BYTES, destFile.readText())
            assertEquals(2, requests.size)
            val first = requests[0]
            val second = requests[1]
            assertEquals("GET /profilephoto/005/F HTTP/1.1", first.requestLine)
            assertEquals("GET /profilephoto/005/F HTTP/1.1", second.requestLine)
            assertEquals("DPoP $TOKEN", first.headers["authorization"])
            assertEquals("DPoP $TOKEN", second.headers["authorization"])
            val firstProof = dpopProofPayload(first.headers["dpop"])
            val secondProof = dpopProofPayload(second.headers["dpop"])
            assertEquals("GET", firstProof.getString("htm"))
            assertEquals(initialUrl, firstProof.getString("htu"))
            assertEquals(redirectedUrl, secondProof.getString("htu"))
            assertEquals("GET", secondProof.getString("htm"))
            assertEquals(firstProof.getString("ath"), secondProof.getString("ath"))
            assertNotEquals(firstProof.getString("jti"), secondProof.getString("jti"))
        }
    }

    @Test
    fun failedDownload_keepsExistingCachedPhoto() {
        destFile.writeText("old-photo")
        httpAccess.enqueue(404, "{}")

        val ok = downloadProfilePhotoToFile(PHOTO_URL, destFile, TOKEN, null, null)

        assertFalse(ok)
        assertEquals("old-photo", destFile.readText())
        assertFalse(File(destFile.parentFile, "${destFile.name}.download").exists())
    }

    @Test
    fun noContentResponse_keepsExistingCachedPhoto() {
        destFile.writeText("old-photo")
        httpAccess.enqueue(204, "")

        val ok = downloadProfilePhotoToFile(PHOTO_URL, destFile, TOKEN, null, null)

        assertFalse(ok)
        assertEquals("old-photo", destFile.readText())
        assertFalse(File(destFile.parentFile, "${destFile.name}.download").exists())
    }

    @Test
    fun successfulDownload_replacesExistingCachedPhoto() {
        destFile.writeText("old-photo")
        httpAccess.enqueuePhoto()

        assertTrue(downloadProfilePhotoToFile(PHOTO_URL, destFile, TOKEN, null, null))

        assertEquals(PHOTO_BYTES, destFile.readText())
    }

    @Test
    fun refreshWithCachedPhoto_sendsNoRequest() {
        destFile.writeText("old-photo")

        val job = buildAccount(tokenType = null).downloadProfilePhotoAsync(true)

        assertNull(job)
        assertTrue(httpAccess.allRequests().isEmpty())
        assertEquals("old-photo", destFile.readText())
    }

    @Test
    fun refreshWithoutCachedPhoto_sendsOneRequest() {
        httpAccess.enqueuePhoto()

        val job = buildAccount(tokenType = null).downloadProfilePhotoAsync(true)

        assertNotNull(job)
        runBlocking { job!!.join() }
        assertEquals(1, httpAccess.allRequests().size)
        assertEquals(PHOTO_BYTES, destFile.readText())
    }

    @Test
    fun loginDownloadWithCachedPhoto_stillDownloads() {
        destFile.writeText("old-photo")
        httpAccess.enqueuePhoto()

        buildAccount(tokenType = null).downloadProfilePhotoAsync(false)!!.let { runBlocking { it.join() } }

        assertEquals(1, httpAccess.allRequests().size)
        assertEquals(PHOTO_BYTES, destFile.readText())
    }

    private fun buildAccount(tokenType: String?): UserAccount = UserAccountBuilder.getInstance()
        .userId("photo_test_user_id")
        .orgId("photo_test_org_id")
        .authToken(TOKEN)
        .tokenType(tokenType)
        .credentialsIdentifier(credentialsIdentifier)
        .photoUrl(PHOTO_URL)
        .build()

    private fun dpopProofPayload(request: Request): JSONObject = dpopProofPayload(request.header("DPoP"))

    private fun dpopProofPayload(proof: String?): JSONObject {
        val parts = checkNotNull(proof).split(".")
        assertEquals("DPoP proof should be a three-part JWT", 3, parts.size)
        val payload = Base64.decode(
            parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        return JSONObject(String(payload, Charsets.UTF_8))
    }

    private data class WireRequest(val requestLine: String, val headers: Map<String, String>)

    /** Records outbound requests and returns canned responses without touching the network. */
    private class CapturingHttpAccess : HttpAccess(null, "dummy-agent") {

        private val recordedRequests = mutableListOf<Request>()
        private val enqueuedResponses = ArrayDeque<CannedResponse>()

        private val capturingInterceptor = Interceptor { chain ->
            val req = chain.request()
            synchronized(recordedRequests) { recordedRequests += req }
            val canned = synchronized(enqueuedResponses) {
                enqueuedResponses.removeFirstOrNull()
            } ?: CannedResponse(200, "{}", emptyMap())
            val builder = Response.Builder()
                .request(req)
                .protocol(HTTP_1_1)
                .code(canned.code)
                .message(if (canned.code < 300) "OK" else "ERR")
                .body(canned.body.toResponseBody("image/jpeg".toMediaType()))
            canned.headers.forEach { (k, v) -> builder.addHeader(k, v) }
            builder.build()
        }

        override fun createNewClientBuilder(): OkHttpClient.Builder =
            OkHttpClient.Builder().apply { addInterceptor(capturingInterceptor) }

        fun enqueue(code: Int, body: String, headers: Map<String, String> = emptyMap()) {
            synchronized(enqueuedResponses) {
                enqueuedResponses.addLast(CannedResponse(code, body, headers))
            }
        }

        fun enqueuePhoto() = enqueue(200, PHOTO_BYTES)

        fun allRequests(): List<Request> = synchronized(recordedRequests) { recordedRequests.toList() }

        private data class CannedResponse(
            val code: Int,
            val body: String,
            val headers: Map<String, String>,
        )
    }

    private companion object {
        const val TOKEN = "photo-test-access-token"
        const val PHOTO_URL = "https://files.salesforce.com/profilephoto/005/F"
        const val PHOTO_BYTES = "photo-bytes"
    }
}
