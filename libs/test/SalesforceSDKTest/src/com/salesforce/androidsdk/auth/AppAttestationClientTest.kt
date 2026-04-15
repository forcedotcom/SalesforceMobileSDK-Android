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

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityToken
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestResponse
import com.salesforce.androidsdk.util.SalesforceSDKLogger.i
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppAttestationClientTest {

    @Test
    fun testPrepareIntegrityTokenProvider() {

        val context = mockk<Context>(relaxed = true)
        val deviceId = "123456"
        val googleCloudProjectId = 654321L
        val remoteAccessConsumerKey = "13579"
        val restClient = mockk<RestClient>(relaxed = true)

        val integrityTokenProviderTask = mockk<Task<StandardIntegrityTokenProvider>>(relaxed = true)
        every { integrityTokenProviderTask.addOnSuccessListener(any()) } returns integrityTokenProviderTask
        every { integrityTokenProviderTask.addOnFailureListener(any()) } returns integrityTokenProviderTask
        val integrityManager = mockk<StandardIntegrityManager>(relaxed = true)
        every { integrityManager.prepareIntegrityToken(any()) } returns integrityTokenProviderTask

        val appAttestationClient = AppAttestationClient(
            context = context,
            deviceId = deviceId,
            googleCloudProjectId = googleCloudProjectId,
            remoteAccessConsumerKey = remoteAccessConsumerKey,
            restClient = restClient
        )

        appAttestationClient.prepareIntegrityTokenProvider(
            integrityManager = integrityManager
        )

        verify(exactly = 1) {
            integrityManager.prepareIntegrityToken(match {
                i(javaClass.name, it.toString()) // TODO: Determine how to verify the Google Cloud Project ID was set. ECJ20260411
                true
            })
        }
        verify(exactly = 1) { integrityTokenProviderTask.addOnSuccessListener(any()) }
        verify(exactly = 1) { integrityTokenProviderTask.addOnFailureListener(any()) }
    }

    @Test
    fun testOnPrepareIntegrityTokenProviderSuccess() {

        val context = mockk<Context>(relaxed = true)
        val deviceId = "123456"
        val googleCloudProjectId = 654321L
        val remoteAccessConsumerKey = "13579"
        val restClient = mockk<RestClient>(relaxed = true)

        val integrityTokenProvider = mockk<StandardIntegrityTokenProvider>(relaxed = true)

        val appAttestationClient = AppAttestationClient(
            context = context,
            deviceId = deviceId,
            googleCloudProjectId = googleCloudProjectId,
            remoteAccessConsumerKey = remoteAccessConsumerKey,
            restClient = restClient
        )

        appAttestationClient.onPrepareIntegrityTokenProviderSuccess(
            tokenProvider = integrityTokenProvider
        )

        assertEquals(integrityTokenProvider, appAttestationClient.integrityTokenProvider)
    }

    @Test
    fun testOnPrepareIntegrityTokenProviderFailure() {

        val context = mockk<Context>(relaxed = true)
        val deviceId = "123456"
        val googleCloudProjectId = 654321L
        val remoteAccessConsumerKey = "13579"
        val restClient = mockk<RestClient>(relaxed = true)

        val appAttestationClient = AppAttestationClient(
            context = context,
            deviceId = deviceId,
            googleCloudProjectId = googleCloudProjectId,
            remoteAccessConsumerKey = remoteAccessConsumerKey,
            restClient = restClient
        )

        appAttestationClient.onPrepareIntegrityTokenProviderFailure(
            exception = RuntimeException()
        )

        /* Intentionally Blank */
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testCreateSalesforceOAuthAuthorizationAppAttestation() = runTest {

        val context = mockk<Context>(relaxed = true)
        val deviceId = "123456"
        val googleCloudProjectId = 654321L
        val remoteAccessConsumerKey = "13579"
        val restResponse = mockk<RestResponse>(relaxed = true)
        every { restResponse.asString() } returns "__TEST_CHALLENGE_VALUE__"
        every { restResponse.isSuccess } returns true
        val restClient = mockk<RestClient>(relaxed = true)
        every { restClient.sendSync(any()) } returns restResponse

        val integrityToken = mockk<StandardIntegrityToken>(relaxed = true)
        every { integrityToken.token() } returns "__TEST_INTEGRITY_TOKEN__"
        val integrityTokenTask = mockk<Task<StandardIntegrityToken>>(relaxed = true)
        every { integrityTokenTask.addOnFailureListener(any()) } returns integrityTokenTask
        every { integrityTokenTask.getResult() } returns integrityToken
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
        coEvery { integrityTokenTask.await() } returns integrityToken
        val integrityTokenProvider = mockk<StandardIntegrityTokenProvider>(relaxed = true)
        every { integrityTokenProvider.request(any()) } returns integrityTokenTask

        val appAttestationClient = AppAttestationClient(
            context = context,
            deviceId = deviceId,
            googleCloudProjectId = googleCloudProjectId,
            remoteAccessConsumerKey = remoteAccessConsumerKey,
            restClient = restClient
        )

        val result = appAttestationClient.createSalesforceOAuthAuthorizationAppAttestation(
            integrityTokenProvider = integrityTokenProvider
        )

        advanceUntilIdle()

        assertEquals("eyJhdHRlc3RhdGlvbklkIjoiMTIzNDU2IiwiYXR0ZXN0YXRpb25EYXRhIjoiWDE5VVJWTlVYMGxPVkVWSFVrbFVXVjlVVDB0RlRsOWYifQ==", result)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun appAttestationClient_createAttestationWhenIntegrityTokenProviderIsNull_returns() = runTest {

        val context = mockk<Context>(relaxed = true)
        val deviceId = "123456"
        val googleCloudProjectId = 654321L
        val remoteAccessConsumerKey = "13579"
        val restResponse = mockk<RestResponse>(relaxed = true)
        every { restResponse.asString() } returns "__TEST_CHALLENGE_VALUE__"
        every { restResponse.isSuccess } returns true
        val restClient = mockk<RestClient>(relaxed = true)
        every { restClient.sendSync(any()) } returns restResponse

        val integrityToken = mockk<StandardIntegrityToken>(relaxed = true)
        every { integrityToken.token() } returns "__TEST_INTEGRITY_TOKEN__"
        val integrityTokenTask = mockk<Task<StandardIntegrityToken>>(relaxed = true)
        every { integrityTokenTask.addOnFailureListener(any()) } returns integrityTokenTask
        every { integrityTokenTask.getResult() } returns integrityToken
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
        coEvery { integrityTokenTask.await() } returns integrityToken
        val integrityTokenProvider = mockk<StandardIntegrityTokenProvider>(relaxed = true)
        every { integrityTokenProvider.request(any()) } returns integrityTokenTask

        val integrityTokenProviderTask = mockk<Task<StandardIntegrityTokenProvider>>(relaxed = true)
        every { integrityTokenProviderTask.addOnSuccessListener(any()) } returns integrityTokenProviderTask
        every { integrityTokenProviderTask.addOnFailureListener(any()) } returns integrityTokenProviderTask
        coEvery { integrityTokenProviderTask.result } returns integrityTokenProvider
        val integrityManager = mockk<StandardIntegrityManager>(relaxed = true)
        every { integrityManager.prepareIntegrityToken(any()) } returns integrityTokenProviderTask

        val appAttestationClient = AppAttestationClient(
            context = context,
            deviceId = deviceId,
            googleCloudProjectId = googleCloudProjectId,
            remoteAccessConsumerKey = remoteAccessConsumerKey,
            restClient = restClient
        )

        val result = appAttestationClient.createSalesforceOAuthAuthorizationAppAttestation(
            integrityManager = integrityManager,
            integrityTokenProvider = null
        )

        advanceUntilIdle()

        assertEquals("eyJhdHRlc3RhdGlvbklkIjoiMTIzNDU2IiwiYXR0ZXN0YXRpb25EYXRhIjoiWDE5VVJWTlVYMGxPVkVWSFVrbFVXVjlVVDB0RlRsOWYifQ==", result)
    }
}
