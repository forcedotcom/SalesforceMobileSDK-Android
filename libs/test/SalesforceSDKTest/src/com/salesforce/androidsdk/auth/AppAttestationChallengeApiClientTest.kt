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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salesforce.androidsdk.rest.AppAttestationChallengeApiClient
import com.salesforce.androidsdk.rest.AppAttestationChallengeApiException
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestResponse
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppAttestationChallengeApiClientTest {

    @Test
    fun appAttestationChallengeApiClient_fetchChallenge_returnsChallengeOnSuccess() {

        val client = createClient(body = TEST_CHALLENGE_VALUE, success = true)

        val result = client.fetchChallenge(
            attestationId = TEST_ATTESTATION_ID,
            remoteConsumerKey = TEST_REMOTE_CONSUMER_KEY,
        )

        assertEquals(TEST_CHALLENGE_VALUE, result)
    }

    @Test
    fun appAttestationChallengeApiClient_fetchChallenge_throwsWhenRestResponseIsNotSuccess() {

        val client = createClient(body = TEST_CHALLENGE_VALUE, success = false)

        assertThrows(AppAttestationChallengeApiException::class.java) {
            client.fetchChallenge(
                attestationId = TEST_ATTESTATION_ID,
                remoteConsumerKey = TEST_REMOTE_CONSUMER_KEY,
            )
        }
    }

    @Test
    fun appAttestationChallengeApiClient_fetchChallenge_throwsWhenRestResponseBodyStringIsNull() {

        val client = createClient(body = null, success = true)

        assertThrows(AppAttestationChallengeApiException::class.java) {
            client.fetchChallenge(
                attestationId = TEST_ATTESTATION_ID,
                remoteConsumerKey = TEST_REMOTE_CONSUMER_KEY,
            )
        }
    }

    @Test
    fun appAttestationChallengeApiClient_fetchChallenge_throwsWhenRestResponseIsNotSuccessAndBodyStringIsNull() {

        val client = createClient(body = null, success = false)

        assertThrows(AppAttestationChallengeApiException::class.java) {
            client.fetchChallenge(
                attestationId = TEST_ATTESTATION_ID,
                remoteConsumerKey = TEST_REMOTE_CONSUMER_KEY,
            )
        }
    }

    // region Helpers

    private fun createClient(
        body: String?,
        success: Boolean,
    ): AppAttestationChallengeApiClient {
        val restResponse = mockk<RestResponse>(relaxed = true).apply {
            every { asString() } returns body
            every { isSuccess } returns success
        }
        val restClient = mockk<RestClient>(relaxed = true).apply {
            every { sendSync(any()) } returns restResponse
        }
        return AppAttestationChallengeApiClient(
            apiHostName = TEST_API_HOST_NAME,
            restClient = restClient,
        )
    }

    // endregion Helpers

    private companion object {
        const val TEST_API_HOST_NAME = "https://www.example.com"
        const val TEST_ATTESTATION_ID = "__ATTESTATION_ID__"
        const val TEST_REMOTE_CONSUMER_KEY = "__REMOTE_CONSUMER_KEY__"
        const val TEST_CHALLENGE_VALUE = "__TEST_CHALLENGE_VALUE__"
    }
}
