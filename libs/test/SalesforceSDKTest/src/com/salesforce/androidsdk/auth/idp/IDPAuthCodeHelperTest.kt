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

import android.accounts.Account
import android.accounts.AccountManager
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.AppAttestationClient
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.auth.OAuth2
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.ui.LoginActivity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS

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
    fun idpAuthCodeHelper_getAuthorizationPathForSP_whenFetchChallengeReturnsNull_excludesAttestationFromQuery() = runTest {

        // Simulate apiHostName being null (App Attestation disabled for the current login server).
        val appAttestationClient = mockk<AppAttestationClient>(relaxed = true).apply {
            coEvery { fetchMobileAppAttestationChallenge() } returns null
        }
        val idpAuthCodeHelper = createIdpAuthCodeHelper(appAttestationClient = appAttestationClient)

        val result = idpAuthCodeHelper.getAuthorizationPathForSP()

        val nonNullResult = requireNotNull(result) {
            "Result should be non-null for a valid login server."
        }
        assertFalse(
            "Result should NOT contain an attestation parameter when challenge fetch returns null, but was '$nonNullResult'.",
            nonNullResult.contains("attestation="),
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun idpAuthCodeHelper_getAuthorizationPathForSP_whenAuthorizationUrlIsNull_returnsNull() = runTest {

        stubOAuthAuthorizationUrl(returnValue = null)
        val idpAuthCodeHelper = createIdpAuthCodeHelper(appAttestationClient = null)

        val result = idpAuthCodeHelper.getAuthorizationPathForSP()

        assertNull("Result should be null when OAuth2.getAuthorizationUrl returns null.", result)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun idpAuthCodeHelper_getAuthorizationPathForSP_whenAuthorizationUrlHasNoQuery_returnsPathOnly() = runTest {

        stubOAuthAuthorizationUrl(returnValue = URI("$TEST_LOGIN_SERVER$OAUTH_AUTHORIZE_PATH"))
        val idpAuthCodeHelper = createIdpAuthCodeHelper(appAttestationClient = null)

        val result = idpAuthCodeHelper.getAuthorizationPathForSP()

        assertEquals(OAUTH_AUTHORIZE_PATH, result)
    }

    @Test
    fun idpAuthCodeHelper_buildRestClient_whenAIsExplicitAndBIsCurrent_terminalInvalidGrantLogsOutOnlyA() {
        ensureSdkManagerInitialized()
        val sdkManager = SalesforceSDKManager.getInstance()
        val userAccountManager = sdkManager.userAccountManager
        val accountManager = AccountManager.get(sdkManager.appContext)
        val originalUser = userAccountManager.currentUser?.takeUnless { user ->
            user.accountName.startsWith(TEST_ACCOUNT_PREFIX)
        }
        try {
            removeTestAccounts(accountManager, sdkManager.accountType)
            userAccountManager.clearStoredCurrentUserInfo()
            RestClient.clearCaches()

            val userA = persistUser("idp-a")
            val accountA = requireNotNull(userAccountManager.buildAccount(userA))
            val userB = persistUser("idp-b")
            val accountB = requireNotNull(userAccountManager.buildAccount(userB))
            val helper = IDPAuthCodeHelper(
                webView = mockk(relaxed = true),
                userAccount = userA,
                spConfig = createSPConfig(),
                codeChallenge = TEST_CODE_CHALLENGE,
                onResult = { /* no-op */ },
                appAttestationClient = null,
            )
            val clientA = requireNotNull(helper.buildRestClientForTest())
            val httpClient = mockk<OkHttpClient> {
                every { newCall(any()) } returns mockk<Call> {
                    every { execute() } returns invalidGrantResponse()
                }
            }
            val revokedRefreshToken = slot<String>()
            val revocationCompleted = CountDownLatch(1)
            mockkObject(HttpAccess.DEFAULT)
            every { HttpAccess.DEFAULT.okHttpClient } returns httpClient
            OAuth2.TIMESTAMP_FORMAT
            mockkStatic(OAuth2::class)
            every {
                OAuth2.revokeRefreshToken(
                    any(),
                    URI(TEST_LOGIN_SERVER),
                    capture(revokedRefreshToken),
                    any(),
                )
            } answers {
                revocationCompleted.countDown()
            }

            assertEquals(userA.userId, clientA.clientInfo.userId)
            assertEquals(userB.userId, userAccountManager.currentUser?.userId)

            assertThrows(IOException::class.java) {
                clientA.refreshAccessToken()
            }

            assertTrue("Refresh-token revocation did not complete", revocationCompleted.await(5, SECONDS))
            assertEquals(userA.refreshTokenForPersistence, revokedRefreshToken.captured)
            assertFalse(accountManager.getAccountsByType(accountA.type).contains(accountA))
            assertTrue(accountManager.getAccountsByType(accountB.type).contains(accountB))
            assertEquals(userB.userId, userAccountManager.currentUser?.userId)
            assertEquals(userB.orgId, userAccountManager.currentUser?.orgId)
            verify(exactly = 1) { httpClient.newCall(any()) }
        } finally {
            unmockkAll()
            RestClient.clearCaches()
            removeTestAccounts(accountManager, sdkManager.accountType)
            if (originalUser != null && userAccountManager.buildAccount(originalUser) != null) {
                userAccountManager.storeCurrentUserInfo(originalUser.userId, originalUser.orgId)
            } else {
                userAccountManager.clearStoredCurrentUserInfo()
            }
        }
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

    private fun persistUser(suffix: String): UserAccount = UserAccountBuilder.getInstance()
        .accountName("$TEST_ACCOUNT_PREFIX$suffix")
        .username("idp-$suffix@example.com")
        .authToken("auth-token-$suffix")
        .refreshToken("refresh-token-$suffix")
        .instanceServer("https://instance-$suffix.example.com")
        .loginServer(TEST_LOGIN_SERVER)
        .idUrl("$TEST_LOGIN_SERVER/id/org-$suffix/user-$suffix")
        .clientId("client-$suffix")
        .orgId("org-$suffix")
        .userId("user-$suffix")
        .build()
        .also { user ->
            SalesforceSDKManager.getInstance().userAccountManager.createAccount(user)
            requireNotNull(SalesforceSDKManager.getInstance().userAccountManager.buildAccount(user))
        }

    private fun IDPAuthCodeHelper.buildRestClientForTest(): RestClient? {
        val method = IDPAuthCodeHelper::class.java.getDeclaredMethod("buildRestClient")
        method.isAccessible = true
        return method.invoke(this) as RestClient?
    }

    private fun invalidGrantResponse(): Response {
        val body = """
            {
              "error": "invalid_grant",
              "error_description": "expired refresh token"
            }
        """.trimIndent().toResponseBody("application/json; charset=utf-8".toMediaType())
        return Response.Builder()
            .request(Request.Builder().url("$TEST_LOGIN_SERVER/services/oauth2/token").build())
            .protocol(Protocol.HTTP_1_1)
            .code(400)
            .message("Bad Request")
            .body(body)
            .build()
    }

    private fun removeTestAccounts(accountManager: AccountManager, accountType: String) {
        accountManager.getAccountsByType(accountType)
            .filter { account -> account.name.startsWith(TEST_ACCOUNT_PREFIX) }
            .forEach { account: Account -> accountManager.removeAccountExplicitly(account) }
    }

    private fun ensureSdkManagerInitialized() {
        try {
            SalesforceSDKManager.getInstance()
        } catch (e: RuntimeException) {
            if (e.message?.contains("SalesforceSDKManager.init") == true) {
                SalesforceSDKManager.initNative(
                    getInstrumentation().targetContext,
                    LoginActivity::class.java,
                )
            } else {
                throw e
            }
        }
    }

    private fun createMockAttestationClient(attestation: String?): AppAttestationClient =
        mockk<AppAttestationClient>(relaxed = true).apply {
            coEvery { fetchMobileAppAttestationChallenge() } returns TEST_CHALLENGE_VALUE
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
        const val TEST_ACCOUNT_PREFIX = "idp-client-manager-test-"
        val TEST_SCOPES = arrayOf("api")
    }
}
