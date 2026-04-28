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
import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode.INTERNAL_ERROR
import com.salesforce.androidsdk.rest.AppAttestationChallengeApiException
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestResponse
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@Suppress("OPT_IN_USAGE")
@RunWith(AndroidJUnit4::class)
class AppAttestationClientTest {

    @Test
    fun appAttestationClient_prepareIntegrityTokenProvider_returnsSuccessfully() {

        val integrityTokenProviderTask = mockk<Task<StandardIntegrityTokenProvider>>(relaxed = true).also { task ->
            every { task.addOnSuccessListener(any()) } returns task
            every { task.addOnFailureListener(any()) } returns task
        }
        val integrityManager = mockk<StandardIntegrityManager>(relaxed = true).also { manager ->
            every { manager.prepareIntegrityToken(any()) } returns integrityTokenProviderTask
        }

        createAppAttestationClientForTest(integrityManager = integrityManager)

        verify(exactly = 1) {
            integrityManager.prepareIntegrityToken(match {
                it.toString().contains("cloudProjectNumber=$TEST_GOOGLE_CLOUD_PROJECT_ID")
            })
        }
        verify(exactly = 1) { integrityTokenProviderTask.addOnSuccessListener(any()) }
        verify(exactly = 1) { integrityTokenProviderTask.addOnFailureListener(any()) }
    }

    @Test
    fun appAttestationClient_onPrepareIntegrityTokenProviderSuccess_assignsIntegrityTokenProvider() {

        val integrityTokenProvider = mockk<StandardIntegrityTokenProvider>(relaxed = true)
        val appAttestationClient = createAppAttestationClientForTest()

        appAttestationClient.onPrepareIntegrityTokenProviderSuccess(tokenProvider = integrityTokenProvider)

        assertEquals(integrityTokenProvider, appAttestationClient.integrityTokenProvider)
    }

    @Test
    fun appAttestationClient_onPrepareIntegrityTokenProviderFailure_justRuns() {

        val appAttestationClient = createAppAttestationClientForTest()

        appAttestationClient.onPrepareIntegrityTokenProviderFailure(exception = RuntimeException())

        /* Intentionally Blank */
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun appAttestationClient_createSalesforceOAuthAuthorizationAppAttestation_returnsSuccessfully() = runTest {

        val integrityTokenProvider = createSuccessfulIntegrityTokenProvider()
        val appAttestationClient = createAppAttestationClientForTest()

        val result = appAttestationClient.createAppAttestation(
            appAttestationChallenge = TEST_CHALLENGE_VALUE,
            integrityTokenProvider = integrityTokenProvider,
        )

        advanceUntilIdle()

        assertEquals(EXPECTED_ATTESTATION_RESULT, result)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun appAttestationClient_createSalesforceOAuthAuthorizationAppAttestationThrowingForInvalidIntegrityTokenProvider_returnsSuccessfully() = runTest {

        val throwingIntegrityTokenProvider = createThrowingIntegrityTokenProvider(
            throwable = createIntegrityServiceException(errorCode = INTEGRITY_TOKEN_PROVIDER_INVALID),
        )
        val integrityManager = createMockIntegrityManagerResolvingTo(
            provider = createSuccessfulIntegrityTokenProvider(),
        )
        val appAttestationClient = createAppAttestationClientForTest(integrityManager = integrityManager)

        val result = appAttestationClient.createAppAttestation(
            appAttestationChallenge = TEST_CHALLENGE_VALUE,
            integrityTokenProvider = throwingIntegrityTokenProvider,
        )

        advanceUntilIdle()

        assertEquals(EXPECTED_ATTESTATION_RESULT, result)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun appAttestationClient_createAppAttestation_whenBothProvidersThrowInvalidTokenProvider_retriesAtMostOnceAndReturnsNull() = runTest {

        val throwingIntegrityTokenProvider = createThrowingIntegrityTokenProvider(
            throwable = createIntegrityServiceException(errorCode = INTEGRITY_TOKEN_PROVIDER_INVALID),
        )
        val integrityManager = createMockIntegrityManagerResolvingTo(
            provider = createThrowingIntegrityTokenProvider(
                throwable = createIntegrityServiceException(errorCode = INTEGRITY_TOKEN_PROVIDER_INVALID),
            ),
        )
        val appAttestationClient = createAppAttestationClientForTest(integrityManager = integrityManager)

        val result = appAttestationClient.createAppAttestation(
            appAttestationChallenge = TEST_CHALLENGE_VALUE,
            integrityTokenProvider = throwingIntegrityTokenProvider,
        )

        advanceUntilIdle()

        assertNull(result)
        // integrityManager.prepareIntegrityToken is called exactly twice: once from the constructor's init {} block,
        // and exactly once more for the single inline retry.  A count > 2 would indicate unbounded recursion.
        verify(exactly = 2) { integrityManager.prepareIntegrityToken(any()) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun appAttestationClient_createAppAttestation_whenCanRetryIsFalseAndProviderThrowsInvalid_shortCircuitsWithoutRetry() = runTest {

        val throwingIntegrityTokenProvider = createThrowingIntegrityTokenProvider(
            throwable = createIntegrityServiceException(errorCode = INTEGRITY_TOKEN_PROVIDER_INVALID),
        )
        val integrityManager = createMockIntegrityManagerResolvingTo(
            provider = createSuccessfulIntegrityTokenProvider(),
        )
        val appAttestationClient = createAppAttestationClientForTest(integrityManager = integrityManager)

        val result = appAttestationClient.createAppAttestation(
            appAttestationChallenge = TEST_CHALLENGE_VALUE,
            integrityTokenProvider = throwingIntegrityTokenProvider,
            canRetryOnInvalidTokenProvider = false,
        )

        advanceUntilIdle()

        assertNull(result)
        // Only the constructor's init {} block may call prepareIntegrityToken; no retry is allowed when canRetryOnInvalidTokenProvider = false.
        verify(exactly = 1) { integrityManager.prepareIntegrityToken(any()) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun appAttestationClient_createSalesforceOAuthAuthorizationAppAttestationThrowingForUnknownIntegrityServiceException_returnsSuccessfully() = runTest {

        val throwingIntegrityTokenProvider = createThrowingIntegrityTokenProvider(
            throwable = createIntegrityServiceException(errorCode = INTERNAL_ERROR),
        )
        val appAttestationClient = createAppAttestationClientForTest()

        val result = appAttestationClient.createAppAttestation(
            appAttestationChallenge = TEST_CHALLENGE_VALUE,
            integrityTokenProvider = throwingIntegrityTokenProvider,
        )

        advanceUntilIdle()

        assertNull(result)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun appAttestationClient_createSalesforceOAuthAuthorizationAppAttestationThrowingUnknownException_returnsNull() = runTest {

        val throwingIntegrityTokenProvider = createThrowingIntegrityTokenProvider(
            throwable = RuntimeException("Unknown Exception"),
        )
        val appAttestationClient = createAppAttestationClientForTest()

        val result = appAttestationClient.createAppAttestation(
            appAttestationChallenge = TEST_CHALLENGE_VALUE,
            integrityTokenProvider = throwingIntegrityTokenProvider,
        )

        advanceUntilIdle()

        assertNull(result)
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun appAttestationClient_createSalesforceOAuthAuthorizationAppAttestationWhenIntegrityTokenProviderIsNull_returnsSuccessfully() = runTest {

        val integrityManager = createMockIntegrityManagerResolvingTo(
            provider = createSuccessfulIntegrityTokenProvider(),
        )
        val appAttestationClient = createAppAttestationClientForTest(integrityManager = integrityManager)

        val result = appAttestationClient.createAppAttestation(
            appAttestationChallenge = TEST_CHALLENGE_VALUE,
            integrityTokenProvider = null,
        )

        advanceUntilIdle()

        assertEquals(EXPECTED_ATTESTATION_RESULT, result)
    }

    @Test
    fun appAttestationClient_createAppAttestationBlocking_returnsSuccessfully() {

        val integrityManager = createMockIntegrityManagerResolvingTo(
            provider = createSuccessfulIntegrityTokenProvider(),
        )
        val appAttestationClient = createAppAttestationClientForTest(integrityManager = integrityManager)

        val result = appAttestationClient.createAppAttestationBlocking(
            appAttestationChallenge = TEST_CHALLENGE_VALUE,
        )

        assertEquals(EXPECTED_ATTESTATION_RESULT, result)
    }

    @Test
    fun appAttestationClient_fetchMobileAppAttestationChallenge_OnSuccess_ReturnsChallenge() {

        val requestSlot = slot<RestRequest>()
        val restClient = createRestClientReturning(
            restResponse = createRestResponse(body = TEST_CHALLENGE_VALUE, success = true),
            requestSlot = requestSlot,
        )
        val appAttestationClient = createAppAttestationClientForTest(restClient = restClient)

        val result = appAttestationClient.fetchMobileAppAttestationChallenge()

        assertEquals(TEST_CHALLENGE_VALUE, result)
        val requestedPath = requestSlot.captured.path
        assertTrue(
            "Request URL should target the attestation challenge endpoint at '$TEST_API_HOST_NAME' but was '$requestedPath'.",
            requestedPath.startsWith("https://$TEST_API_HOST_NAME/mobile/attest/challenge"),
        )
        assertTrue(
            "Request URL should contain 'attestationId=$TEST_DEVICE_ID' but was '$requestedPath'.",
            requestedPath.contains("attestationId=$TEST_DEVICE_ID"),
        )
        assertTrue(
            "Request URL should contain 'consumerKey=$TEST_REMOTE_ACCESS_CONSUMER_KEY' but was '$requestedPath'.",
            requestedPath.contains("consumerKey=$TEST_REMOTE_ACCESS_CONSUMER_KEY"),
        )
        verify(exactly = 1) { restClient.sendSync(any()) }
    }

    @Test
    fun appAttestationClient_fetchMobileAppAttestationChallenge_OnFailureResponse_ThrowsException() {

        val restClient = createRestClientReturning(
            restResponse = createRestResponse(body = "__ERROR_BODY__", success = false),
        )
        val appAttestationClient = createAppAttestationClientForTest(restClient = restClient)

        assertThrows(AppAttestationChallengeApiException::class.java) {
            appAttestationClient.fetchMobileAppAttestationChallenge()
        }
    }

    @Test
    fun appAttestationClient_fetchMobileAppAttestationChallenge_OnNullResponseBody_ThrowsException() {

        val restClient = createRestClientReturning(
            restResponse = createRestResponse(body = null, success = true),
        )
        val appAttestationClient = createAppAttestationClientForTest(restClient = restClient)

        assertThrows(AppAttestationChallengeApiException::class.java) {
            appAttestationClient.fetchMobileAppAttestationChallenge()
        }
    }

    @Test
    fun oAuthAuthorizationAttestation_encode_returnsSuccessfully() {

        val result = Json.decodeFromString(
            OAuthAuthorizationAttestation.serializer(),
            TEST_ATTESTATION_JSON,
        )

        assertEquals(TEST_ATTESTATION_ID, result.attestationId)
        assertEquals(TEST_ATTESTATION_DATA, result.attestationData)
    }

    @Test
    fun oAuthAuthorizationAttestation_decodeWithUnknownField_returnsSuccessfully() {

        @Suppress("JSON_FORMAT_REDUNDANT")
        val result = Json { ignoreUnknownKeys = true }.decodeFromString(
            OAuthAuthorizationAttestation.serializer(),
            TEST_ATTESTATION_JSON_WITH_UNKNOWN_FIELD,
        )

        assertEquals(TEST_ATTESTATION_ID, result.attestationId)
        assertEquals(TEST_ATTESTATION_DATA, result.attestationData)
    }

    @Test
    fun oAuthAuthorizationAttestation_serializerDescriptor_hasCorrectElementCount() {
        assertEquals(2, OAuthAuthorizationAttestation.serializer().descriptor.elementsCount)
    }

    // region Helpers

    private fun createAppAttestationClientForTest(
        restClient: RestClient = createSuccessfulRestClientForChallenge(),
        integrityManager: StandardIntegrityManager = createMockIntegrityManagerWithInertProviderTask(),
    ): AppAttestationClient = AppAttestationClient(
        apiHostName = TEST_API_HOST_NAME,
        context = mockk<Context>(relaxed = true),
        deviceId = TEST_DEVICE_ID,
        googleCloudProjectId = TEST_GOOGLE_CLOUD_PROJECT_ID,
        integrityManager = integrityManager,
        remoteAccessConsumerKey = TEST_REMOTE_ACCESS_CONSUMER_KEY,
        restClient = restClient,
    )

    private fun createSuccessfulRestClientForChallenge(): RestClient = createRestClientReturning(
        restResponse = createRestResponse(body = TEST_CHALLENGE_VALUE, success = true),
    )

    private fun createRestResponse(
        body: String?,
        success: Boolean,
    ): RestResponse = mockk<RestResponse>(relaxed = true).also { response ->
        every { response.asString() } returns body
        every { response.isSuccess } returns success
    }

    private fun createRestClientReturning(
        restResponse: RestResponse,
        requestSlot: CapturingSlot<RestRequest> = slot(),
    ): RestClient = mockk<RestClient>(relaxed = true).also { client ->
        every { client.sendSync(capture(requestSlot)) } returns restResponse
    }

    private fun createMockIntegrityToken(): StandardIntegrityToken =
        mockk<StandardIntegrityToken>(relaxed = true).also { token ->
            every { token.token() } returns TEST_INTEGRITY_TOKEN
        }

    private fun createSuccessfulIntegrityTokenTask(): Task<StandardIntegrityToken> {
        val token = createMockIntegrityToken()
        return mockk<Task<StandardIntegrityToken>>(relaxed = true).also { task ->
            every { task.addOnFailureListener(any()) } returns task
            every { task.isComplete } returns true
            every { task.isCanceled } returns false
            every { task.exception } returns null
            every { task.result } returns token
        }
    }

    private fun createThrowingIntegrityTokenTask(
        throwable: Exception,
    ): Task<StandardIntegrityToken> =
        mockk<Task<StandardIntegrityToken>>(relaxed = true).also { task ->
            every { task.addOnFailureListener(any()) } returns task
            every { task.isComplete } returns true
            every { task.isCanceled } returns false
            every { task.exception } returns throwable
        }

    private fun createSuccessfulIntegrityTokenProvider(): StandardIntegrityTokenProvider {
        val task = createSuccessfulIntegrityTokenTask()
        return mockk<StandardIntegrityTokenProvider>(relaxed = true).also { provider ->
            every { provider.request(any()) } returns task
        }
    }

    private fun createThrowingIntegrityTokenProvider(
        throwable: Exception,
    ): StandardIntegrityTokenProvider {
        val task = createThrowingIntegrityTokenTask(throwable = throwable)
        return mockk<StandardIntegrityTokenProvider>(relaxed = true).also { provider ->
            every { provider.request(any()) } returns task
        }
    }

    private fun createIntegrityServiceException(
        errorCode: Int,
    ): IntegrityServiceException = mockk<IntegrityServiceException>(relaxed = true).also { exception ->
        every { exception.errorCode } returns errorCode
    }

    private fun createMockIntegrityManagerWithInertProviderTask(): StandardIntegrityManager {
        val providerTask = mockk<Task<StandardIntegrityTokenProvider>>(relaxed = true).also { task ->
            every { task.addOnSuccessListener(any()) } returns task
            every { task.addOnFailureListener(any()) } returns task
        }
        return mockk<StandardIntegrityManager>(relaxed = true).also { manager ->
            every { manager.prepareIntegrityToken(any()) } returns providerTask
        }
    }

    private fun createMockIntegrityManagerResolvingTo(
        provider: StandardIntegrityTokenProvider,
    ): StandardIntegrityManager {
        val providerTask = mockk<Task<StandardIntegrityTokenProvider>>(relaxed = true).also { task ->
            every { task.addOnSuccessListener(any()) } returns task
            every { task.addOnFailureListener(any()) } returns task
            every { task.isComplete } returns true
            every { task.isCanceled } returns false
            every { task.exception } returns null
            every { task.result } returns provider
        }
        return mockk<StandardIntegrityManager>(relaxed = true).also { manager ->
            every { manager.prepareIntegrityToken(any()) } returns providerTask
        }
    }

    // endregion Helpers

    private companion object {
        const val TEST_API_HOST_NAME = "login.example.com"
        const val TEST_DEVICE_ID = "123456"
        const val TEST_GOOGLE_CLOUD_PROJECT_ID = 654321L
        const val TEST_REMOTE_ACCESS_CONSUMER_KEY = "13579"
        const val TEST_CHALLENGE_VALUE = "__TEST_CHALLENGE_VALUE__"
        const val TEST_INTEGRITY_TOKEN = "__TEST_INTEGRITY_TOKEN__"
        const val EXPECTED_ATTESTATION_RESULT =
            "eyJhdHRlc3RhdGlvbklkIjoiMTIzNDU2IiwiYXR0ZXN0YXRpb25EYXRhIjoiWDE5VVJWTlVYMGxPVkVWSFVrbFVXVjlVVDB0RlRsOWYifQ=="
        const val TEST_ATTESTATION_ID = "123456"
        const val TEST_ATTESTATION_DATA = "W19VVlJTVVhNbExPVkVWSFVrbFVXVjlVVDB0RlRsOWYifQ=="
        const val TEST_ATTESTATION_JSON =
            "{ \"attestationId\": \"$TEST_ATTESTATION_ID\", \"attestationData\": \"$TEST_ATTESTATION_DATA\" }"
        const val TEST_ATTESTATION_JSON_WITH_UNKNOWN_FIELD =
            "{ \"attestationId\": \"$TEST_ATTESTATION_ID\", \"attestationData\": \"$TEST_ATTESTATION_DATA\", \"unknownField\": \"ignored\" }"
    }
}
