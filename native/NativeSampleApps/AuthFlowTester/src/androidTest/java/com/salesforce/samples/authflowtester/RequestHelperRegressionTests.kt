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
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.samples.authflowtester.testUtility.AuthFlowTest
import com.salesforce.samples.authflowtester.testUtility.RequestEndpointPaths
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class RequestHelperRegressionTests {
    private fun clientInfo(communityUrl: String?) = RestClient.ClientInfo(
        URI("https://instance.example.invalid"), URI("https://login.example.invalid"),
        URI("https://login.example.invalid/id/org/user"), null, null, null, null,
        null, communityUrl, null, null, null, null, null, null, emptyMap(),
        null, null, null, null, null, null, null,
    )

    @Test
    fun test_givenCommunityPrefix_whenRestAndRevoke_thenExpectFullEncodedPath() {
        for (base in listOf("https://site.example.invalid/customer%20portal", "https://site.example.invalid/customer%20portal/")) {
            for (path in listOf("/services/data/v65.0/limits", "/services/oauth2/revoke")) {
                assertEquals("/customer%20portal$path", RequestEndpointPaths.rest(clientInfo(base), path))
            }
        }
        assertEquals("/services/data/v65.0/limits", RequestEndpointPaths.rest(clientInfo(null), "/services/data/v65.0/limits"))
        assertEquals("/absolute", RequestEndpointPaths.rest(clientInfo("https://site.example.invalid/portal"), "https://other.example.invalid/absolute?ignored=true"))
    }

    @Test
    fun test_givenCommunityOwner_whenRefresh_thenExpectTokenServerPrefix() {
        for (prefix in listOf("/portal", "/portal/")) {
            val owner = UserAccountBuilder.getInstance()
                .loginServer("https://login.example.invalid/login")
                .instanceServer("https://instance.example.invalid/instance")
                .communityId("community").communityUrl("https://site.example.invalid$prefix").build()
            // OAuth2 appends the endpoint literally; unlike REST, a trailing slash is retained.
            assertEquals("$prefix/services/oauth2/token", RequestEndpointPaths.token(owner))
        }
        val owner = UserAccountBuilder.getInstance().loginServer("https://login.example.invalid/login")
            .instanceServer("https://instance.example.invalid/instance").build()
        assertEquals("/instance/services/oauth2/token", RequestEndpointPaths.token(owner))
        val fallback = UserAccountBuilder.getInstance().loginServer("https://login.example.invalid/login").build()
        assertEquals("/login/services/oauth2/token", RequestEndpointPaths.token(fallback))
    }

    @Test
    fun test_givenAuthenticationAddedAfterAudit_whenSent_thenMissingProofIsDetected() {
        assertEquals(listOf("GET localhost/services/data/v65.0/limits"), audit(proof = false))
    }

    @Test
    fun test_givenAuthenticationAddedAfterAudit_whenProofPresent_thenNoViolation() {
        assertTrue(audit(proof = true).isEmpty())
    }

    private fun audit(proof: Boolean): List<String> {
        val violations = CopyOnWriteArrayList<String>()
        val delegate = object : HttpAccess(null, null) {
            override fun createNewClientBuilder() = OkHttpClient.Builder()
        }
        val access = AuthFlowTest.DPoPProofAuditHttpAccess(delegate, violations)
        val client = access.createNewClientBuilder()
            // RestClient appends its OAuthRefreshInterceptor after obtaining this builder.
            .addInterceptor { chain ->
                val authenticated = chain.request().newBuilder().header("Authorization", "DPoP")
                if (proof) authenticated.header("DPoP", "test-proof")
                chain.proceed(authenticated.build())
            }
            .build()
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverCertificates.sslSocketFactory(), false)
            server.enqueue(MockResponse().setResponseCode(200))
            server.start()
            val secureClient = client.newBuilder()
                .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
                .build()
            assertEquals(200, secureClient.newCall(
                Request.Builder().url(server.url("/services/data/v65.0/limits")).build(),
            ).execute().use { it.code })
            server.takeRequest()
        }
        return violations.toList()
    }
}
