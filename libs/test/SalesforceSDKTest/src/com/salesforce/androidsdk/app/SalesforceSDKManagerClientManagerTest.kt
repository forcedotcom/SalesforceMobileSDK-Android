/*
 * Copyright (c) 2026-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
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
package com.salesforce.androidsdk.app

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.auth.AuthenticatorService
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.ui.LoginActivity
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** Credential-free behavior coverage for the SDK-level authenticated-client factories. */
@RunWith(AndroidJUnit4::class)
@SmallTest
class SalesforceSDKManagerClientManagerTest {

    private val sdkManager by lazy { SalesforceSDKManager.getInstance() }
    private val userAccountManager by lazy { sdkManager.userAccountManager }
    private val accountManager by lazy { AccountManager.get(sdkManager.appContext) }

    private var originalUser: UserAccount? = null

    @Before
    fun setUp() {
        ensureSdkManagerInitialized()
        originalUser = userAccountManager.currentUser?.takeUnless {
            it.accountName.startsWith(TEST_ACCOUNT_PREFIX)
        }
        removeTestAccounts()
        userAccountManager.clearStoredCurrentUserInfo()
        RestClient.clearCaches()
    }

    @After
    fun tearDown() {
        RestClient.clearCaches()
        removeTestAccounts()
        val userToRestore = originalUser
        if (userToRestore != null && userAccountManager.buildAccount(userToRestore) != null) {
            userAccountManager.storeCurrentUserInfo(userToRestore.userId, userToRestore.orgId)
        } else {
            userAccountManager.clearStoredCurrentUserInfo()
        }
    }

    @Test
    fun clientManager_afterCurrentUserSwitch_retainsAAndFreshGetterUsesB() {
        val userA = persistUser("a")
        val retainedManagerA = requireNotNull(sdkManager.clientManager)

        val userB = persistUser("b")
        val freshManagerB = requireNotNull(sdkManager.clientManager)

        assertManagerBoundTo(retainedManagerA, userA)
        assertManagerBoundTo(freshManagerB, userB)
        assertManagerBoundTo(retainedManagerA, userA)
    }

    @Test
    fun retainedClient_refreshesPersistedAWhileBRemainsCurrent() {
        val userA = persistUser("refresh-a")
        val managerA = requireNotNull(sdkManager.clientManager)
        val clientA = requireNotNull(managerA.peekRestClient())
        val accountA = requireNotNull(managerA.account)

        val userB = persistUser("refresh-b")
        val accountB = requireNotNull(userAccountManager.buildAccount(userB))
        val submittedRequest = slot<Request>()
        val httpClient = mockRefreshHttpClient(
            submittedRequest,
            successfulRefreshResponse(
                suffix = "refresh-a",
                accessToken = "refreshed-auth-token-a",
                refreshToken = "rotated-refresh-token-a",
            ),
        )

        try {
            clientA.refreshAccessToken()
        } finally {
            unmockkObject(HttpAccess.DEFAULT)
        }

        val refreshedA = requireNotNull(userAccountManager.buildUserAccount(accountA))
        val unchangedB = requireNotNull(userAccountManager.buildUserAccount(accountB))
        assertEquals(
            "refresh-token-refresh-a",
            requestFormValue(submittedRequest.captured, "refresh_token"),
        )
        assertEquals("refreshed-auth-token-a", refreshedA.authToken)
        assertEquals("rotated-refresh-token-a", refreshedA.refreshTokenForPersistence)
        assertEquals(userB.authToken, unchangedB.authToken)
        assertEquals(userB.refreshTokenForPersistence, unchangedB.refreshTokenForPersistence)
        assertEquals(userB.userId, userAccountManager.currentUser?.userId)
        assertEquals(userB.orgId, userAccountManager.currentUser?.orgId)
        verify(exactly = 1) { httpClient.newCall(any()) }
    }

    @Test
    fun retainedClient_afterAIsRemoved_failsWithoutTouchingCurrentB() {
        persistUser("removed-a")
        val managerA = requireNotNull(sdkManager.clientManager)
        val clientA = requireNotNull(managerA.peekRestClient())
        val accountA = requireNotNull(managerA.account)

        val userB = persistUser("removed-b")
        val accountB = requireNotNull(userAccountManager.buildAccount(userB))
        assertTrue(accountManager.removeAccountExplicitly(accountA))

        val httpClient = mockk<OkHttpClient>(relaxed = true)
        mockkObject(HttpAccess.DEFAULT)
        every { HttpAccess.DEFAULT.okHttpClient } returns httpClient

        try {
            assertThrows(IOException::class.java) {
                clientA.refreshAccessToken()
            }
        } finally {
            unmockkObject(HttpAccess.DEFAULT)
        }

        val unchangedB = requireNotNull(userAccountManager.buildUserAccount(accountB))
        assertEquals(userB.authToken, unchangedB.authToken)
        assertEquals(userB.refreshTokenForPersistence, unchangedB.refreshTokenForPersistence)
        assertEquals(userB.userId, userAccountManager.currentUser?.userId)
        assertEquals(userB.orgId, userAccountManager.currentUser?.orgId)
        verify(exactly = 0) { httpClient.newCall(any()) }
    }

    @Test
    fun retainedClient_afterTransportFailure_retriesAWithoutTouchingCurrentB() {
        val userA = persistUser("retry-a")
        val managerA = requireNotNull(sdkManager.clientManager)
        val clientA = requireNotNull(managerA.peekRestClient())
        val accountA = requireNotNull(managerA.account)

        val userB = persistUser("retry-b")
        val accountB = requireNotNull(userAccountManager.buildAccount(userB))
        var attempts = 0
        val submittedRequests = mutableListOf<Request>()
        val httpClient = mockk<OkHttpClient> {
            every { newCall(capture(submittedRequests)) } answers {
                mockk<Call> {
                    every { execute() } answers {
                        attempts++
                        if (attempts == 1) {
                            throw IOException("offline")
                        }
                        successfulRefreshResponse(
                            suffix = "retry-a",
                            accessToken = "refreshed-auth-token-retry-a",
                            refreshToken = "rotated-refresh-token-retry-a",
                        )
                    }
                }
            }
        }
        mockkObject(HttpAccess.DEFAULT)
        every { HttpAccess.DEFAULT.okHttpClient } returns httpClient

        try {
            assertThrows(IOException::class.java) {
                clientA.refreshAccessToken()
            }
            val unchangedA = requireNotNull(userAccountManager.buildUserAccount(accountA))
            val unchangedB = requireNotNull(userAccountManager.buildUserAccount(accountB))
            assertEquals(userA.authToken, unchangedA.authToken)
            assertEquals(userA.refreshTokenForPersistence, unchangedA.refreshTokenForPersistence)
            assertEquals(userB.authToken, unchangedB.authToken)
            assertEquals(userB.refreshTokenForPersistence, unchangedB.refreshTokenForPersistence)
            assertEquals(userB, userAccountManager.currentUser)

            clientA.refreshAccessToken()
        } finally {
            unmockkObject(HttpAccess.DEFAULT)
        }

        val refreshedA = requireNotNull(userAccountManager.buildUserAccount(accountA))
        val unchangedB = requireNotNull(userAccountManager.buildUserAccount(accountB))
        assertEquals("refreshed-auth-token-retry-a", refreshedA.authToken)
        assertEquals("rotated-refresh-token-retry-a", refreshedA.refreshTokenForPersistence)
        assertEquals(userB.authToken, unchangedB.authToken)
        assertEquals(userB.refreshTokenForPersistence, unchangedB.refreshTokenForPersistence)
        assertEquals(userB, userAccountManager.currentUser)
        assertEquals(2, attempts)
        assertEquals(2, submittedRequests.size)
        submittedRequests.forEach { request ->
            assertEquals(
                "refresh-token-retry-a",
                requestFormValue(request, "refresh_token"),
            )
        }
        verify(exactly = 2) { httpClient.newCall(any()) }
    }

    @Test
    fun refreshToken_withNullUser_refreshesCurrentPersistedUser() {
        val currentUser = persistUser("current-refresh")
        val currentAccount = requireNotNull(userAccountManager.buildAccount(currentUser))
        val submittedRequest = slot<Request>()
        val httpClient = mockRefreshHttpClient(
            submittedRequest,
            successfulRefreshResponse(
                suffix = "current-refresh",
                accessToken = "refreshed-current-access-token",
                refreshToken = "rotated-current-refresh-token",
            ),
        )

        try {
            userAccountManager.refreshToken(null)
        } finally {
            unmockkObject(HttpAccess.DEFAULT)
        }

        val refreshedUser = requireNotNull(
            userAccountManager.buildUserAccount(currentAccount)
        )
        assertEquals(
            currentUser.refreshTokenForPersistence,
            requestFormValue(submittedRequest.captured, "refresh_token"),
        )
        assertEquals(
            if (sdkManager.useHybridAuthentication) "hybrid_refresh" else "refresh_token",
            requestFormValue(submittedRequest.captured, "grant_type"),
        )
        assertEquals("refreshed-current-access-token", refreshedUser.authToken)
        assertEquals(
            "rotated-current-refresh-token",
            refreshedUser.refreshTokenForPersistence,
        )
        assertEquals(currentUser.userId, userAccountManager.currentUser?.userId)
        assertEquals(currentUser.orgId, userAccountManager.currentUser?.orgId)
        verify(exactly = 1) { httpClient.newCall(any()) }
    }

    @Test
    fun getRestClient_withCurrentUser_returnsThatUsersClient() {
        val user = persistUser("callback")
        val activity = mockk<Activity>(relaxed = true)
        val clients = mutableListOf<RestClient>()

        sdkManager.getRestClient(activity) { client -> clients += client }

        assertEquals(1, clients.size)
        assertClientFor(clients.single(), user)
        verify(exactly = 0) {
            activity.startActivityForResult(any<Intent>(), any())
        }
    }

    @Test
    fun getRestClient_withUnusableCurrentUser_removesExactAccountWithoutCallback() {
        val user = persistUser("unusable")
        val account = requireNotNull(userAccountManager.buildAccount(user))
        accountManager.setUserData(account, AuthenticatorService.KEY_LOGIN_URL, null)
        val activity = mockk<Activity>(relaxed = true)
        var callbackCount = 0

        sdkManager.getRestClient(activity) { callbackCount++ }

        assertEquals(0, callbackCount)
        assertFalse(accountManager.getAccountsByType(account.type).contains(account))
        verify(exactly = 0) {
            activity.startActivityForResult(any<Intent>(), any())
        }
    }

    @Test
    fun getRestClient_withMalformedCurrentAccount_removesItWithoutCallback() {
        val user = persistUser("malformed")
        val account = requireNotNull(userAccountManager.buildAccount(user))
        accountManager.setUserData(account, AccountManager.KEY_AUTHTOKEN, null)
        val activity = mockk<Activity>(relaxed = true)
        var callbackCount = 0

        sdkManager.getRestClient(activity) { callbackCount++ }

        assertEquals(0, callbackCount)
        assertFalse(accountManager.getAccountsByType(account.type).contains(account))
        verify(exactly = 0) {
            activity.startActivityForResult(any<Intent>(), any())
        }
    }

    @Test
    fun getRestClient_withNoCurrentUser_launchesConfiguredLoginWithoutCallback() {
        userAccountManager.clearStoredCurrentUserInfo()
        val activity = mockk<Activity>(relaxed = true)
        every { activity.packageName } returns sdkManager.appContext.packageName
        val launchedIntent = slot<Intent>()
        var callbackCount = 0

        sdkManager.getRestClient(activity) { callbackCount++ }

        assertEquals(0, callbackCount)
        verify(exactly = 1) {
            activity.startActivityForResult(capture(launchedIntent), 0)
        }
        assertEquals(
            sdkManager.loginActivityClass.name,
            launchedIntent.captured.component?.className,
        )
        assertEquals(sdkManager.appContext.packageName, launchedIntent.captured.`package`)
        assertTrue(
            launchedIntent.captured.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0,
        )
    }

    private fun persistUser(suffix: String): UserAccount = buildUser(suffix).also { user ->
        userAccountManager.createAccount(user)
        requireNotNull(userAccountManager.buildAccount(user))
    }

    private fun assertManagerBoundTo(manager: ClientManager, user: UserAccount) {
        assertEquals(user.accountName, requireNotNull(manager.account).name)
        assertClientFor(requireNotNull(manager.peekRestClient()), user)
    }

    private fun assertClientFor(client: RestClient, user: UserAccount) {
        assertEquals(user.accountName, client.clientInfo.accountName)
        assertEquals(user.userId, client.clientInfo.userId)
        assertEquals(user.orgId, client.clientInfo.orgId)
    }

    private fun mockRefreshHttpClient(
        request: CapturingSlot<Request>,
        response: Response,
    ): OkHttpClient {
        val client = mockk<OkHttpClient> {
            every { newCall(capture(request)) } returns mockk<Call> {
                every { execute() } returns response
            }
        }
        mockkObject(HttpAccess.DEFAULT)
        every { HttpAccess.DEFAULT.okHttpClient } returns client
        return client
    }

    private fun successfulRefreshResponse(
        suffix: String,
        accessToken: String,
        refreshToken: String,
    ): Response {
        val body = """
            {
              "access_token": "$accessToken",
              "refresh_token": "$refreshToken",
              "instance_url": "https://instance-$suffix.example.com",
              "id": "https://login.example.com/id/org-$suffix/user-$suffix",
              "token_type": "Bearer",
              "issued_at": "1234567890",
              "signature": "test-signature"
            }
        """.trimIndent().toResponseBody("application/json; charset=utf-8".toMediaType())
        return Response.Builder()
            .request(Request.Builder().url("https://login.example.com/services/oauth2/token").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()
    }

    private fun requestFormValue(request: Request, key: String): String? {
        val body = request.body as? FormBody ?: return null
        return (0 until body.size)
            .firstOrNull { index -> body.name(index) == key }
            ?.let(body::value)
    }

    private fun buildUser(suffix: String): UserAccount = UserAccountBuilder.getInstance()
        .accountName("$TEST_ACCOUNT_PREFIX$suffix")
        .username("sdk-manager-$suffix@example.com")
        .authToken("auth-token-$suffix")
        .refreshToken("refresh-token-$suffix")
        .instanceServer("https://instance-$suffix.example.com")
        .loginServer("https://login.example.com")
        .idUrl("https://login.example.com/id/org-$suffix/user-$suffix")
        .clientId("client-$suffix")
        .orgId("org-$suffix")
        .userId("user-$suffix")
        .build()

    private fun removeTestAccounts() {
        accountManager.getAccountsByType(sdkManager.accountType)
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

    companion object {
        private const val TEST_ACCOUNT_PREFIX = "sdk-manager-client-test-"
    }
}
