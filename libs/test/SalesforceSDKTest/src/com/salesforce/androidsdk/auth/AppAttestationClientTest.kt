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
import com.google.android.play.core.integrity.IntegrityServiceException
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityToken
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestResponse
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
    fun appAttestationClient_prepareIntegrityTokenProvider_returnsSuccessfully() {

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
            apiHostName = "login.example.com",
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
                it.toString().contains("cloudProjectNumber=654321")
            })
        }
        verify(exactly = 1) { integrityTokenProviderTask.addOnSuccessListener(any()) }
        verify(exactly = 1) { integrityTokenProviderTask.addOnFailureListener(any()) }
    }

    @Test
    fun appAttestationClient_onPrepareIntegrityTokenProviderSuccess_assignsIntegrityTokenProvider() {

        val context = mockk<Context>(relaxed = true)
        val deviceId = "123456"
        val googleCloudProjectId = 654321L
        val remoteAccessConsumerKey = "13579"
        val restClient = mockk<RestClient>(relaxed = true)

        val integrityTokenProvider = mockk<StandardIntegrityTokenProvider>(relaxed = true)

        val appAttestationClient = AppAttestationClient(
            apiHostName = "login.example.com",
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
    fun appAttestationClient_onPrepareIntegrityTokenProviderFailure_justRuns() {

        val context = mockk<Context>(relaxed = true)
        val deviceId = "123456"
        val googleCloudProjectId = 654321L
        val remoteAccessConsumerKey = "13579"
        val restClient = mockk<RestClient>(relaxed = true)

        val appAttestationClient = AppAttestationClient(
            apiHostName = "login.example.com",
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
    fun appAttestationClient_createSalesforceOAuthAuthorizationAppAttestation_returnsSuccessfully() = runTest {

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
            apiHostName = "login.example.com",
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
    fun appAttestationClient_createSalesforceOAuthAuthorizationAppAttestationThrowingForInvalidIntegrityTokenProvider_returnsSuccessfully() = runTest {

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
        val throwingIntegrityTokenTask = mockk<Task<StandardIntegrityToken>>(relaxed = true)
        every { throwingIntegrityTokenTask.addOnFailureListener(any()) } returns throwingIntegrityTokenTask
        every { throwingIntegrityTokenTask.getResult() } returns integrityToken
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
        val integrityServiceException = mockk<IntegrityServiceException>(relaxed = true)
        every { integrityServiceException.errorCode } returns INTEGRITY_TOKEN_PROVIDER_INVALID
        coEvery { throwingIntegrityTokenTask.await() } throws integrityServiceException
        val throwingIntegrityTokenProvider = mockk<StandardIntegrityTokenProvider>(relaxed = true)
        every { throwingIntegrityTokenProvider.request(any()) } returns throwingIntegrityTokenTask

        val successfulIntegrityTokenTask = mockk<Task<StandardIntegrityToken>>(relaxed = true)
        every { successfulIntegrityTokenTask.addOnFailureListener(any()) } returns successfulIntegrityTokenTask
        every { successfulIntegrityTokenTask.getResult() } returns integrityToken
        coEvery { successfulIntegrityTokenTask.await() } returns integrityToken
        val successfulIntegrityTokenProvider = mockk<StandardIntegrityTokenProvider>(relaxed = true)
        every { successfulIntegrityTokenProvider.request(any()) } returns successfulIntegrityTokenTask

        val integrityTokenProviderTask = mockk<Task<StandardIntegrityTokenProvider>>(relaxed = true)
        every { integrityTokenProviderTask.addOnSuccessListener(any()) } returns integrityTokenProviderTask
        every { integrityTokenProviderTask.addOnFailureListener(any()) } returns integrityTokenProviderTask
        coEvery { integrityTokenProviderTask.result } returns successfulIntegrityTokenProvider
        val integrityManager = mockk<StandardIntegrityManager>(relaxed = true)
        every { integrityManager.prepareIntegrityToken(any()) } returns integrityTokenProviderTask

        val appAttestationClient = AppAttestationClient(
            apiHostName = "login.example.com",
            context = context,
            deviceId = deviceId,
            googleCloudProjectId = googleCloudProjectId,
            remoteAccessConsumerKey = remoteAccessConsumerKey,
            restClient = restClient
        )

        val result = appAttestationClient.createSalesforceOAuthAuthorizationAppAttestation(
            integrityManager = integrityManager,
            integrityTokenProvider = throwingIntegrityTokenProvider
        )

        advanceUntilIdle()

        assertEquals("eyJhdHRlc3RhdGlvbklkIjoiMTIzNDU2IiwiYXR0ZXN0YXRpb25EYXRhIjoiWDE5VVJWTlVYMGxPVkVWSFVrbFVXVjlVVDB0RlRsOWYifQ==", result)
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun appAttestationClient_createSalesforceOAuthAuthorizationAppAttestationWhenIntegrityTokenProviderIsNull_returnsSuccessfully() = runTest {

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
            apiHostName = "login.example.com",
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
