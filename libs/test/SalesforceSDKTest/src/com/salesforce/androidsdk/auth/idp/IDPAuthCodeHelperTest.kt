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
package com.salesforce.androidsdk.auth.idp

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.auth.AppAttestationClient
import com.salesforce.androidsdk.auth.OAuth2
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI

@Suppress("OPT_IN_USAGE")
@RunWith(AndroidJUnit4::class)
class IDPAuthCodeHelperTest {

    @After
    fun tearDown() {
        unmockkAll()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun idpAuthCodeHelper_getAuthorizationPathForSP_whenNoAttestationClient_returnsPathAndQueryWithoutAttestation() = runTest {

        val idpAuthCodeHelper = createIdpAuthCodeHelper(appAttestationClient = null)

        val result = idpAuthCodeHelper.getAuthorizationPathForSP()

        advanceUntilIdle()

        val nonNullResult = requireNotNull(result) {
            "Result should be non-null for a valid login server."
        }
        assertTrue(
            "Result should start with the OAuth authorize path but was '$nonNullResult'.",
            nonNullResult.startsWith(OAUTH_AUTHORIZE_PATH),
        )
        assertTrue(
            "Result should contain the client id but was '$nonNullResult'.",
            nonNullResult.contains("client_id=$TEST_CLIENT_ID"),
        )
        assertTrue(
            "Result should contain the code challenge but was '$nonNullResult'.",
            nonNullResult.contains("code_challenge=$TEST_CODE_CHALLENGE"),
        )
        assertTrue(
            "Result should contain the redirect URI but was '$nonNullResult'.",
            nonNullResult.contains("redirect_uri=$TEST_CALLBACK_URL"),
        )
        assertFalse(
            "Result should NOT contain an attestation parameter but was '$nonNullResult'.",
            nonNullResult.contains("attestation="),
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun idpAuthCodeHelper_getAuthorizationPathForSP_whenAttestationClientReturnsAttestation_includesAttestationInQuery() = runTest {

        val appAttestationClient = createMockAttestationClient(attestation = TEST_APP_ATTESTATION)
        val idpAuthCodeHelper = createIdpAuthCodeHelper(appAttestationClient = appAttestationClient)

        val result = idpAuthCodeHelper.getAuthorizationPathForSP()

        advanceUntilIdle()

        val nonNullResult = requireNotNull(result) {
            "Result should be non-null for a valid login server."
        }
        assertTrue(
            "Result should contain 'attestation=$TEST_APP_ATTESTATION' but was '$nonNullResult'.",
            nonNullResult.contains("attestation=$TEST_APP_ATTESTATION"),
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun idpAuthCodeHelper_getAuthorizationPathForSP_whenCreateAppAttestationReturnsNull_excludesAttestationFromQuery() = runTest {

        val appAttestationClient = createMockAttestationClient(attestation = null)
        val idpAuthCodeHelper = createIdpAuthCodeHelper(appAttestationClient = appAttestationClient)

        val result = idpAuthCodeHelper.getAuthorizationPathForSP()

        advanceUntilIdle()

        val nonNullResult = requireNotNull(result) {
            "Result should be non-null for a valid login server."
        }
        assertFalse(
            "Result should NOT contain an attestation parameter but was '$nonNullResult'.",
            nonNullResult.contains("attestation="),
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun idpAuthCodeHelper_getAuthorizationPathForSP_whenAuthorizationUrlIsNull_returnsNull() = runTest {

        stubOAuthAuthorizationUrl(returnValue = null)
        val idpAuthCodeHelper = createIdpAuthCodeHelper(appAttestationClient = null)

        val result = idpAuthCodeHelper.getAuthorizationPathForSP()

        advanceUntilIdle()

        assertNull("Result should be null when OAuth2.getAuthorizationUrl returns null.", result)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun idpAuthCodeHelper_getAuthorizationPathForSP_whenAuthorizationUrlHasNoQuery_returnsPathOnly() = runTest {

        stubOAuthAuthorizationUrl(returnValue = URI("$TEST_LOGIN_SERVER$OAUTH_AUTHORIZE_PATH"))
        val idpAuthCodeHelper = createIdpAuthCodeHelper(appAttestationClient = null)

        val result = idpAuthCodeHelper.getAuthorizationPathForSP()

        advanceUntilIdle()

        assertEquals(OAUTH_AUTHORIZE_PATH, result)
    }

    // region Helpers

    private fun createSPConfig(): SPConfig = SPConfig(
        appPackageName = TEST_SP_APP_PACKAGE,
        componentName = TEST_SP_COMPONENT_NAME,
        oauthClientId = TEST_CLIENT_ID,
        oauthCallbackUrl = TEST_CALLBACK_URL,
        oauthScopes = TEST_SCOPES,
    )

    private fun createMockUserAccount(): UserAccount = mockk<UserAccount>(relaxed = true).apply {
        every { loginServer } returns TEST_LOGIN_SERVER
    }

    private fun createMockAttestationClient(attestation: String?): AppAttestationClient =
        mockk<AppAttestationClient>(relaxed = true).apply {
            every { fetchMobileAppAttestationChallenge() } returns TEST_CHALLENGE_VALUE
            coEvery {
                createAppAttestation(appAttestationChallenge = TEST_CHALLENGE_VALUE)
            } returns attestation
        }

    private fun createIdpAuthCodeHelper(
        appAttestationClient: AppAttestationClient?,
    ): IDPAuthCodeHelper = IDPAuthCodeHelper(
        webView = mockk<WebView>(relaxed = true),
        userAccount = createMockUserAccount(),
        spConfig = createSPConfig(),
        codeChallenge = TEST_CODE_CHALLENGE,
        onResult = { /* no-op */ },
        appAttestationClient = appAttestationClient,
    )

    private fun stubOAuthAuthorizationUrl(returnValue: URI?) {
        mockkStatic(OAuth2::class)
        every {
            OAuth2.getAuthorizationUrl(
                any(), any(), any(), any(), any(), any(), any(), any(), any(),
            )
        } returns returnValue
    }

    // endregion Helpers

    private companion object {
        const val TEST_LOGIN_SERVER = "https://login.example.com"
        const val TEST_CLIENT_ID = "__TEST_CLIENT_ID__"
        const val TEST_CALLBACK_URL = "sfdc://callback"
        const val TEST_CODE_CHALLENGE = "__TEST_CODE_CHALLENGE__"
        const val TEST_CHALLENGE_VALUE = "__TEST_CHALLENGE_VALUE__"
        const val TEST_APP_ATTESTATION = "__TEST_APP_ATTESTATION__"
        const val TEST_SP_APP_PACKAGE = "com.example.sp"
        const val TEST_SP_COMPONENT_NAME = "com.example.sp.MainActivity"
        const val OAUTH_AUTHORIZE_PATH = "/services/oauth2/authorize"
        val TEST_SCOPES = arrayOf("api")
    }
}
