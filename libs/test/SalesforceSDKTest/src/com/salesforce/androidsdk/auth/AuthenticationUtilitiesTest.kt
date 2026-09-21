/*
 * Copyright (c) 2025-present, salesforce.com, inc.
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

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.app.Features.FEATURE_BIOMETRIC_AUTH
import com.salesforce.androidsdk.app.Features.FEATURE_RTR
import com.salesforce.androidsdk.app.Features.FEATURE_SCREEN_LOCK
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.RuntimeConfig
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.security.BiometricAuthenticationManager
import com.salesforce.androidsdk.security.ScreenLockManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI

/**
 * Tests for AuthenticationUtilities.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
@ExperimentalCoroutinesApi
class AuthenticationUtilitiesTest {

    private lateinit var testContext: Context
    private val mockUserAccountManager: UserAccountManager = mockk()
    private val mockRuntimeConfig: RuntimeConfig = mockk()
    private val onAuthFlowError: (String, String?, Throwable?) -> Unit = mockk()
    private val onAuthFlowSuccess: (UserAccount) -> Unit = mockk()
    private val buildAccountName: (String?, String?) -> String = { username, instanceServer -> "$username ($instanceServer)" }
    private val updateLoggingPrefs: (UserAccount) -> Unit = mockk()
    private val fetchUserIdentity: suspend (OAuth2.TokenEndpointResponse) -> OAuth2.IdServiceResponse? = mockk()
    private val startMainActivity: () -> Unit = mockk()
    private val setAdministratorPreferences: (OAuth2.IdServiceResponse?, UserAccount) -> Unit = mockk()
    private val addAccount: (UserAccount) -> Unit = mockk()
    private val handleScreenLockPolicy: (OAuth2.IdServiceResponse?, UserAccount) -> Unit = mockk()
    private val handleBiometricAuthPolicy: (OAuth2.IdServiceResponse?, UserAccount) -> Unit = mockk()
    private val handleDuplicateUserAccount: (UserAccountManager, UserAccount, OAuth2.IdServiceResponse?) -> Unit = mockk()

    @Before
    fun setUp() {
        // Setup test context
        testContext = InstrumentationRegistry.getInstrumentation().targetContext

        // Setup mock runtime config
        every { mockRuntimeConfig.isManagedApp } returns false

        // Setup mock user account manager
        every { mockUserAccountManager.authenticatedUsers } returns mutableListOf()

        // Setup mock behaviors
        every { onAuthFlowError.invoke(any(), any(), any()) } returns Unit
        every { onAuthFlowSuccess.invoke(any()) } returns Unit
        every { updateLoggingPrefs.invoke(any()) } returns Unit
        every { startMainActivity.invoke() } returns Unit
        every { setAdministratorPreferences.invoke(any(), any()) } returns Unit
        every { addAccount.invoke(any()) } returns Unit
        every { handleScreenLockPolicy.invoke(any(), any()) } returns Unit
        every { handleBiometricAuthPolicy.invoke(any(), any()) } returns Unit
        every { handleDuplicateUserAccount.invoke(any(), any(), any()) } returns Unit

        // Setup mock for UserAccountManager methods
        every { mockUserAccountManager.createAccount(any()) } returns mockk<android.os.Bundle>()
        every { mockUserAccountManager.switchToUser(any()) } returns Unit
        every { mockUserAccountManager.sendUserSwitchIntent(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testOnAuthFlowComplete_blockIntegrationUser_shouldCallError() = runTest {

        // When
        callOnAuthFlowComplete(blockIntegrationUser = true)

        // Then
        verify { onAuthFlowError.invoke("Error", "Authentication error. Please try again.", null) }
        verify(exactly = 0) { onAuthFlowSuccess.invoke(any()) }
        verify(exactly = 0) { mockUserAccountManager.createAccount(any()) }
        verify(exactly = 0) { mockUserAccountManager.switchToUser(any()) }
    }

    @Test
    fun testOnAuthFlowComplete_managedAppRequirement_shouldCallError() = runTest {
        // Given
        val userIdentityWithManagedAppRequirement = createIdServiceResponse(
            customPermissions = JSONObject().apply {
                put("must_be_managed_app", true)
            }
        )

        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentityWithManagedAppRequirement
        every { mockRuntimeConfig.isManagedApp } returns false

        // When
        callOnAuthFlowComplete()

        // Then
        verify { onAuthFlowError.invoke("Error", "Authentication only allowed from managed device.", null) }
        verify(exactly = 0) { onAuthFlowSuccess.invoke(any()) }
        verify(exactly = 0) { mockUserAccountManager.createAccount(any()) }
        verify(exactly = 0) { mockUserAccountManager.switchToUser(any()) }
    }

    @Test
    fun testOnAuthFlowComplete_successfulFlow_shouldCallSuccess() = runTest {
        // Given
        val tokenResponse = createTokenEndpointResponse()
        val userIdentity = createIdServiceResponse()
        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentity

        // Create the expected UserAccount object like in AuthenticationUtilities.kt
        val expectedAccount = UserAccountBuilder.getInstance()
            .populateFromTokenEndpointResponse(tokenResponse)
            .populateFromIdServiceResponse(userIdentity)
            .accountName(buildAccountName(userIdentity.username, tokenResponse.instanceUrl))
            .loginServer("https://login.salesforce.com")
            .clientId("test_consumer_key")
            .nativeLogin(false)
            .build()

        // When
        callOnAuthFlowComplete()

        // Then
        verify(exactly = 0) { onAuthFlowError.invoke(any(), any(), any()) }
        verify { onAuthFlowSuccess.invoke(expectedAccount) }
        verify { mockUserAccountManager.createAccount(expectedAccount) }
        verify { mockUserAccountManager.switchToUser(expectedAccount) }
        verify { setAdministratorPreferences.invoke(userIdentity, expectedAccount) }
        verify { handleDuplicateUserAccount.invoke(mockUserAccountManager, expectedAccount, userIdentity) }
        verify { addAccount.invoke(expectedAccount) }
        verify { updateLoggingPrefs.invoke(expectedAccount) }
        verify { startMainActivity.invoke() }
        verify { handleScreenLockPolicy.invoke(userIdentity, expectedAccount) }
        verify { handleBiometricAuthPolicy.invoke(userIdentity, expectedAccount) }
    }

    @Test
    fun testOnAuthFlowComplete_biometricPolicyThrowsAfterSuccess_doesNotResumeCallerWithError() = runTest {
        // Given - onAuthFlowSuccess already ran to completion (per verifyOrder below) before
        // finalization work runs, so a caller resuming a single-shot continuation from both
        // onAuthFlowSuccess and a catch around onAuthFlowComplete would crash on double-resume.
        val userIdentity = createIdServiceResponse()
        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentity
        every { handleBiometricAuthPolicy.invoke(any(), any()) } throws RuntimeException("policy storage failed")

        // When
        callOnAuthFlowComplete()

        // Then - the thrown exception is swallowed, not surfaced as a second terminal outcome
        verify(exactly = 0) { onAuthFlowError.invoke(any(), any(), any()) }
        verifyOrder {
            onAuthFlowSuccess.invoke(any())
            handleBiometricAuthPolicy.invoke(any(), any())
        }
    }

    @Test
    fun testOnAuthFlowComplete_startMainActivityThrowsAfterSuccess_doesNotResumeCallerWithError() = runTest {
        // Given
        val userIdentity = createIdServiceResponse()
        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentity
        every { startMainActivity.invoke() } throws RuntimeException("activity launch failed")

        // When
        callOnAuthFlowComplete()

        // Then
        verify(exactly = 0) { onAuthFlowError.invoke(any(), any(), any()) }
        verify { onAuthFlowSuccess.invoke(any()) }
    }

    @Test
    fun testOnAuthFlowComplete_defaultOnAuthFlowFinished_isNoOp() = runTest {
        // Given
        val userIdentity = createIdServiceResponse()
        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentity

        // When - onAuthFlowFinished is not supplied, so the default no-op is used
        callOnAuthFlowComplete()

        // Then - flow completes successfully with no exception thrown by the default hook
        verify(exactly = 0) { onAuthFlowError.invoke(any(), any(), any()) }
        verify { onAuthFlowSuccess.invoke(any()) }
    }

    @Test
    fun testOnAuthFlowComplete_onAuthFlowFinished_calledAfterBiometricAuthPolicy() = runTest {
        // Given
        val tokenResponse = createTokenEndpointResponse()
        val userIdentity = createIdServiceResponse()
        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentity

        val expectedAccount = UserAccountBuilder.getInstance()
            .populateFromTokenEndpointResponse(tokenResponse)
            .populateFromIdServiceResponse(userIdentity)
            .accountName(buildAccountName(userIdentity.username, tokenResponse.instanceUrl))
            .loginServer("https://login.salesforce.com")
            .clientId("test_consumer_key")
            .nativeLogin(false)
            .build()

        val onAuthFlowFinished: (() -> Unit) -> Unit = mockk(relaxed = true)

        // When
        onAuthFlowComplete(
            tokenResponse = tokenResponse,
            loginServer = "https://login.salesforce.com",
            consumerKey = "test_consumer_key",
            onAuthFlowError = onAuthFlowError,
            onAuthFlowSuccess = onAuthFlowSuccess,
            buildAccountName = buildAccountName,
            context = testContext,
            userAccountManager = mockUserAccountManager,
            runtimeConfig = mockRuntimeConfig,
            updateLoggingPrefs = updateLoggingPrefs,
            fetchUserIdentity = fetchUserIdentity,
            startMainActivity = startMainActivity,
            setAdministratorPreferences = setAdministratorPreferences,
            addAccount = addAccount,
            handleScreenLockPolicy = handleScreenLockPolicy,
            handleBiometricAuthPolicy = handleBiometricAuthPolicy,
            handleDuplicateUserAccount = handleDuplicateUserAccount,
            onAuthFlowFinished = onAuthFlowFinished,
        )

        // Then - called after handleBiometricAuthPolicy, and before proceed
        // (startMainActivity/handleScreenLockPolicy) since onAuthFlowFinished is relaxed and
        // does not invoke the proceed callback it's given
        verifyOrder {
            handleBiometricAuthPolicy.invoke(userIdentity, expectedAccount)
            onAuthFlowFinished.invoke(any())
        }
        verify(exactly = 0) { startMainActivity.invoke() }
        verify(exactly = 0) { handleScreenLockPolicy.invoke(any(), any()) }
    }

    @Test
    fun testOnAuthFlowComplete_onAuthFlowFinished_proceedInvokesStartMainActivityAndScreenLockPolicy() = runTest {
        // Given
        val tokenResponse = createTokenEndpointResponse()
        val userIdentity = createIdServiceResponse()
        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentity

        val onAuthFlowFinished: (() -> Unit) -> Unit = { proceed -> proceed() }

        // When - the proceed callback passed to onAuthFlowFinished is invoked
        onAuthFlowComplete(
            tokenResponse = tokenResponse,
            loginServer = "https://login.salesforce.com",
            consumerKey = "test_consumer_key",
            onAuthFlowError = onAuthFlowError,
            onAuthFlowSuccess = onAuthFlowSuccess,
            buildAccountName = buildAccountName,
            context = testContext,
            userAccountManager = mockUserAccountManager,
            runtimeConfig = mockRuntimeConfig,
            updateLoggingPrefs = updateLoggingPrefs,
            fetchUserIdentity = fetchUserIdentity,
            startMainActivity = startMainActivity,
            setAdministratorPreferences = setAdministratorPreferences,
            addAccount = addAccount,
            handleScreenLockPolicy = handleScreenLockPolicy,
            handleBiometricAuthPolicy = handleBiometricAuthPolicy,
            handleDuplicateUserAccount = handleDuplicateUserAccount,
            onAuthFlowFinished = onAuthFlowFinished,
        )

        // Then - proceed() starts the main activity and applies screen lock policy
        verify { startMainActivity.invoke() }
        verify { handleScreenLockPolicy.invoke(userIdentity, any()) }
    }

    @Test
    fun testOnAuthFlowComplete_withoutIdScopeAndIdentity_shouldCallError() = runTest {
        // Given
        val tokenResponseWithoutIdScope = createTokenEndpointResponse(
            scope = "refresh_token" // Missing id scope
        )

        // Identity is required to create a valid account, regardless of the returned scope list.
        coEvery { fetchUserIdentity.invoke(any()) } returns null

        // When
        callOnAuthFlowComplete(tokenResponseWithoutIdScope)

        // Then
        verify {
            onAuthFlowError.invoke(
                "Error",
                "Authentication error. Please try again.",
                any<IllegalStateException>(),
            )
        }
        verify(exactly = 0) { onAuthFlowSuccess.invoke(any()) }
        verify(exactly = 0) { addAccount.invoke(any()) }
        verify(exactly = 0) { mockUserAccountManager.createAccount(any()) }
        verify(exactly = 0) { mockUserAccountManager.switchToUser(any()) }
        coVerify(exactly = 1) { fetchUserIdentity.invoke(tokenResponseWithoutIdScope) }
    }

    @Test
    fun testOnAuthFlowComplete_withoutIdScopeWithIdentity_shouldSucceed() = runTest {
        val tokenResponseWithoutIdScope = createTokenEndpointResponse(scope = "refresh_token")
        val userIdentity = createIdServiceResponse()
        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentity

        callOnAuthFlowComplete(tokenResponseWithoutIdScope)

        verify(exactly = 0) { onAuthFlowError.invoke(any(), any(), any()) }
        verify { onAuthFlowSuccess.invoke(match { it.username == userIdentity.username }) }
        verify { mockUserAccountManager.createAccount(match { it.username == userIdentity.username }) }
        coVerify(exactly = 1) { fetchUserIdentity.invoke(tokenResponseWithoutIdScope) }
    }

    @Test
    fun testOnAuthFlowComplete_identityFetchFailure_shouldCallErrorWithoutAddingAccount() = runTest {
        val identityError = IllegalStateException("Identity service response was rejected")
        coEvery { fetchUserIdentity.invoke(any()) } throws identityError

        callOnAuthFlowComplete()

        verify {
            onAuthFlowError.invoke(
                "Error",
                "Authentication error. Please try again.",
                identityError,
            )
        }
        verify(exactly = 0) { onAuthFlowSuccess.invoke(any()) }
        verify(exactly = 0) { addAccount.invoke(any()) }
        verify(exactly = 0) { mockUserAccountManager.createAccount(any()) }
        verify(exactly = 0) { mockUserAccountManager.switchToUser(any()) }
    }

    @Test
    fun testOnAuthFlowComplete_identityScopeWithoutIdentity_shouldCallErrorWithoutAddingAccount() = runTest {
        coEvery { fetchUserIdentity.invoke(any()) } returns null

        callOnAuthFlowComplete()

        verify {
            onAuthFlowError.invoke(
                "Error",
                "Authentication error. Please try again.",
                any<IllegalStateException>(),
            )
        }
        verify(exactly = 0) { onAuthFlowSuccess.invoke(any()) }
        verify(exactly = 0) { addAccount.invoke(any()) }
        verify(exactly = 0) { mockUserAccountManager.createAccount(any()) }
        verify(exactly = 0) { mockUserAccountManager.switchToUser(any()) }
    }

    @Test
    fun testFetchUserIdentityWithRetry_identityForbidden_refreshesOnceAndPreservesRtr() = runTest {
        val tokenResponse = createTokenEndpointResponse(
            accessToken = "initial-access-token",
            refreshToken = "initial-refresh-token",
        ).apply {
            tokenType = "DPoP"
            credentialsIdentifier = "test-credentials-id"
        }
        val refreshedResponse = createTokenEndpointResponse(
            accessToken = "refreshed-access-token",
            refreshToken = "rotated-refresh-token",
            instanceUrl = "https://refreshed.my.salesforce.com",
            idUrl = "https://login.salesforce.com/id/00D000000000000EAA/005000000000000AAA",
        ).apply {
            tokenType = "DPoP"
            credentialsIdentifier = "test-credentials-id"
        }
        val expectedIdentity = createIdServiceResponse()
        var identityCalls = 0
        var refreshCalls = 0
        val identityUrls = mutableListOf<String>()

        val result = fetchUserIdentityWithRetry(
            tokenResponse = tokenResponse,
            loginServer = "https://login.salesforce.com",
            consumerKey = "test_consumer_key",
            identityFetcher = { url, response ->
                identityCalls++
                assertSame(tokenResponse, response)
                identityUrls += url
                if (identityCalls == 1) {
                    throw OAuth2.IdentityServiceException(403, "Wrong_Org")
                }
                expectedIdentity
            },
            tokenRefresher = { response, refreshLoginServer, refreshConsumerKey ->
                refreshCalls++
                assertSame(tokenResponse, response)
                assertEquals("https://login.salesforce.com", refreshLoginServer)
                assertEquals("test_consumer_key", refreshConsumerKey)
                refreshedResponse
            },
        )

        assertSame(expectedIdentity, result.identity)
        assertEquals(true, result.refreshTokenRotationTime?.isNotBlank())
        assertEquals(2, identityCalls)
        assertEquals(1, refreshCalls)
        assertEquals(
            listOf(
                "https://test.salesforce.com/id/00D000000000000EAA/005000000000000AAA",
                "https://refreshed.my.salesforce.com/id/00D000000000000EAA/005000000000000AAA",
            ),
            identityUrls,
        )
        assertEquals("refreshed-access-token", tokenResponse.authToken)
        assertEquals("rotated-refresh-token", tokenResponse.refreshToken)
        assertEquals("https://refreshed.my.salesforce.com", tokenResponse.instanceUrl)
        assertEquals(refreshedResponse.idUrl, tokenResponse.idUrl)
        assertEquals("DPoP", tokenResponse.tokenType)
        assertEquals("test-credentials-id", tokenResponse.credentialsIdentifier)
    }

    @Test
    fun testFetchUserIdentityWithRetry_replayForbidden_doesNotRefreshAgain() = runTest {
        val tokenResponse = createTokenEndpointResponse().apply {
            tokenType = "DPoP"
            credentialsIdentifier = "test-credentials-id"
        }
        val refreshedResponse = createTokenEndpointResponse(
            accessToken = "refreshed-access-token",
            refreshToken = "rotated-refresh-token",
            instanceUrl = "https://refreshed.my.salesforce.com",
        ).apply {
            tokenType = "DPoP"
            credentialsIdentifier = "test-credentials-id"
        }
        var identityCalls = 0
        var refreshCalls = 0
        var thrown: OAuth2.IdentityServiceException? = null

        try {
            fetchUserIdentityWithRetry(
                tokenResponse = tokenResponse,
                loginServer = "https://login.salesforce.com",
                consumerKey = "test_consumer_key",
                identityFetcher = { _, _ ->
                    identityCalls++
                    throw OAuth2.IdentityServiceException(403, "Wrong_Org")
                },
                tokenRefresher = { _, _, _ ->
                    refreshCalls++
                    refreshedResponse
                },
            )
        } catch (e: OAuth2.IdentityServiceException) {
            thrown = e
        }

        assertEquals(403, thrown?.httpStatusCode)
        assertEquals(2, identityCalls)
        assertEquals(1, refreshCalls)
    }

    @Test
    fun testFetchUserIdentityWithRetry_unrecognizedForbidden_doesNotRefresh() = runTest {
        val tokenResponse = createTokenEndpointResponse().apply {
            tokenType = "DPoP"
            credentialsIdentifier = "test-credentials-id"
        }
        val identityError = OAuth2.IdentityServiceException(403, "insufficient_scope")
        var refreshCalls = 0
        var thrown: Exception? = null

        try {
            fetchUserIdentityWithRetry(
                tokenResponse = tokenResponse,
                loginServer = "https://login.salesforce.com",
                consumerKey = "test_consumer_key",
                identityFetcher = { _, _ -> throw identityError },
                tokenRefresher = { _, _, _ ->
                    refreshCalls++
                    createTokenEndpointResponse()
                },
            )
        } catch (e: Exception) {
            thrown = e
        }

        assertSame(identityError, thrown)
        assertEquals("insufficient_scope", identityError.responseBody)
        assertEquals(0, refreshCalls)
    }

    @Test
    fun testFetchUserIdentityWithRetry_hybridRefreshMergesCompleteCredentialState() = runTest {
        val tokenResponse = createTokenEndpointResponse(
            accessToken = "initial-access-token",
            refreshToken = "initial-refresh-token",
        ).apply {
            apiInstanceUrl = "https://old-api.example.com"
            code = "old-code"
            communityId = "old-community-id"
            communityUrl = "https://old-community.example.com"
            additionalOauthValues = mapOf("preserved" to "old", "replaced" to "old")
            idToken = "old-id-token"
            lightningDomain = "https://old-lightning.example.com"
            lightningSid = "old-lightning-sid"
            vfDomain = "https://old-vf.example.com"
            vfSid = "old-vf-sid"
            contentDomain = "https://old-content.example.com"
            contentSid = "old-content-sid"
            csrfToken = "old-csrf"
            cookieClientSrc = "old-client-src"
            cookieSidClient = "old-sid-client"
            sidCookieName = "old-cookie-name"
            parentSid = "old-parent-sid"
            uiSid = "old-ui-sid"
            tokenFormat = "opaque"
            beaconChildConsumerKey = "old-beacon-key"
            beaconChildConsumerSecret = "old-beacon-secret"
            tokenType = "DPoP"
            credentialsIdentifier = "old-credentials-id"
        }
        val refreshedResponse = createTokenEndpointResponse(
            accessToken = "refreshed-access-token",
            refreshToken = null,
            instanceUrl = "https://refreshed.my.salesforce.com",
            idUrl = "https://login.salesforce.com/id/00DREFRESHED/005REFRESHED",
            scope = "refreshed-scope",
        ).apply {
            apiInstanceUrl = "https://new-api.example.com"
            code = "new-code"
            communityId = "new-community-id"
            communityUrl = "https://new-community.example.com"
            additionalOauthValues = mapOf("replaced" to "new", "added" to "new")
            idToken = "new-id-token"
            lightningDomain = "https://new-lightning.example.com"
            lightningSid = ""
            vfDomain = "https://new-vf.example.com"
            vfSid = "new-vf-sid"
            contentDomain = "https://new-content.example.com"
            contentSid = ""
            csrfToken = "new-csrf"
            cookieClientSrc = "new-client-src"
            cookieSidClient = "new-sid-client"
            sidCookieName = "new-cookie-name"
            parentSid = "new-parent-sid"
            uiSid = "new-ui-sid"
            tokenFormat = "jwt"
            beaconChildConsumerKey = "new-beacon-key"
            beaconChildConsumerSecret = "new-beacon-secret"
            tokenType = "DPoP"
            credentialsIdentifier = "new-credentials-id"
        }
        val expectedIdentity = createIdServiceResponse()
        var identityCalls = 0

        val result = fetchUserIdentityWithRetry(
            tokenResponse = tokenResponse,
            loginServer = "https://login.salesforce.com",
            consumerKey = "test_consumer_key",
            identityFetcher = { _, _ ->
                identityCalls++
                if (identityCalls == 1) {
                    throw OAuth2.IdentityServiceException(401, "expired_token")
                }
                expectedIdentity
            },
            tokenRefresher = { _, _, _ -> refreshedResponse },
        )

        assertSame(expectedIdentity, result.identity)
        assertEquals(null, result.refreshTokenRotationTime)
        assertEquals("refreshed-access-token", tokenResponse.authToken)
        assertEquals("initial-refresh-token", tokenResponse.refreshToken)
        assertEquals("https://refreshed.my.salesforce.com", tokenResponse.instanceUrl)
        assertEquals("https://new-api.example.com", tokenResponse.apiInstanceUrl)
        assertEquals(refreshedResponse.idUrl, tokenResponse.idUrl)
        assertEquals(refreshedResponse.idUrlWithInstance, tokenResponse.idUrlWithInstance)
        assertEquals("00DREFRESHED", tokenResponse.orgId)
        assertEquals("005REFRESHED", tokenResponse.userId)
        assertEquals("new-code", tokenResponse.code)
        assertEquals("new-community-id", tokenResponse.communityId)
        assertEquals("https://new-community.example.com", tokenResponse.communityUrl)
        assertEquals(
            mapOf("preserved" to "old", "replaced" to "new", "added" to "new"),
            tokenResponse.additionalOauthValues,
        )
        assertEquals("new-id-token", tokenResponse.idToken)
        assertEquals("https://new-lightning.example.com", tokenResponse.lightningDomain)
        assertEquals("", tokenResponse.lightningSid)
        assertEquals("https://new-vf.example.com", tokenResponse.vfDomain)
        assertEquals("new-vf-sid", tokenResponse.vfSid)
        assertEquals("https://new-content.example.com", tokenResponse.contentDomain)
        assertEquals("", tokenResponse.contentSid)
        assertEquals("new-csrf", tokenResponse.csrfToken)
        assertEquals("new-client-src", tokenResponse.cookieClientSrc)
        assertEquals("new-sid-client", tokenResponse.cookieSidClient)
        assertEquals("new-cookie-name", tokenResponse.sidCookieName)
        assertEquals("new-parent-sid", tokenResponse.parentSid)
        assertEquals("new-ui-sid", tokenResponse.uiSid)
        assertEquals("jwt", tokenResponse.tokenFormat)
        assertEquals("new-beacon-key", tokenResponse.beaconChildConsumerKey)
        assertEquals("new-beacon-secret", tokenResponse.beaconChildConsumerSecret)
        assertEquals("refreshed-scope", tokenResponse.scope)
        assertEquals("DPoP", tokenResponse.tokenType)
        assertEquals("new-credentials-id", tokenResponse.credentialsIdentifier)
    }

    @Test
    fun testOnAuthFlowComplete_rotatedRefreshToken_recordsAndRegistersRtrAfterPersistence() = runTest {
        val rotationTime = "2026-09-16T19:45:20Z"
        val userIdentity = createIdServiceResponse()
        val mockSdkManager = setupMockSdkManager()

        callOnAuthFlowComplete(
            identityFetchResult = IdentityFetchResult(userIdentity, rotationTime),
        )

        verifyOrder {
            mockUserAccountManager.createAccount(
                match { it.lastTokenRotationTime == rotationTime },
            )
            mockSdkManager.registerUsedAppFeature(
                FEATURE_RTR,
                match { it.lastTokenRotationTime == rotationTime },
            )
        }
    }

    @Test
    fun testOnAuthFlowComplete_withNativeLogin_shouldCallSuccess() = runTest {
        // Given
        val tokenResponse = createTokenEndpointResponse()
        val userIdentity = createIdServiceResponse()
        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentity

        val expectedAccount = UserAccountBuilder.getInstance()
            .populateFromTokenEndpointResponse(tokenResponse)
            .populateFromIdServiceResponse(userIdentity)
            .accountName(buildAccountName(userIdentity.username, tokenResponse.instanceUrl))
            .loginServer("https://login.salesforce.com")
            .clientId("test_consumer_key")
            .nativeLogin(true) // Expect true
            .build()

        // When
        callOnAuthFlowComplete(
            customTokenResponse = tokenResponse,
            nativeLogin = true,
        )

        // Then
        verify(exactly = 0) { onAuthFlowError.invoke(any(), any(), any()) }
        verify { onAuthFlowSuccess.invoke(expectedAccount) }
        verify { mockUserAccountManager.createAccount(expectedAccount) }
        verify { mockUserAccountManager.switchToUser(expectedAccount) }
        verify { setAdministratorPreferences.invoke(userIdentity, expectedAccount) }
        verify { handleDuplicateUserAccount.invoke(mockUserAccountManager, expectedAccount, userIdentity) }
        verify { addAccount.invoke(expectedAccount) }
        verify { updateLoggingPrefs.invoke(expectedAccount) }
        verify { startMainActivity.invoke() }
        verify { handleScreenLockPolicy.invoke(userIdentity, expectedAccount) }
        verify { handleBiometricAuthPolicy.invoke(userIdentity, expectedAccount) }

        coVerify(exactly = 1) { fetchUserIdentity.invoke(tokenResponse) }
    }


    // region Token Migration Tests

    @Test
    fun testOnAuthFlowComplete_tokenMigration_shouldNotCallStartMainActivity() = runTest {
        // Given
        val userIdentity = createIdServiceResponse()
        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentity
        setupPersistAccountMocks()

        // When - tokenMigration is true
        callOnAuthFlowComplete(tokenMigration = true)

        // Then - startMainActivity should NOT be called during token migration
        verify(exactly = 0) { startMainActivity.invoke() }
        // But onAuthFlowSuccess should still be called
        verify(exactly = 1) { onAuthFlowSuccess.invoke(any()) }
    }

    @Test
    fun testOnAuthFlowComplete_tokenMigration_shouldCallSuccess() = runTest {
        // Given
        val tokenResponse = createTokenEndpointResponse()
        val userIdentity = createIdServiceResponse()
        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentity
        setupPersistAccountMocks()

        // Create the expected UserAccount object
        val expectedAccount = UserAccountBuilder.getInstance()
            .populateFromTokenEndpointResponse(tokenResponse)
            .populateFromIdServiceResponse(userIdentity)
            .accountName(buildAccountName(userIdentity.username, tokenResponse.instanceUrl))
            .loginServer("https://login.salesforce.com")
            .clientId("test_consumer_key")
            .nativeLogin(false)
            .build()

        // When - tokenMigration is true
        callOnAuthFlowComplete(tokenMigration = true)

        // Then - onAuthFlowSuccess should be called with the account
        verify(exactly = 1) { onAuthFlowSuccess.invoke(expectedAccount) }
        verify(exactly = 0) { onAuthFlowError.invoke(any(), any(), any()) }
    }

    @Test
    fun testOnAuthFlowComplete_tokenMigration_shouldPersistAccount() = runTest {
        // Given
        val userIdentity = createIdServiceResponse()
        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentity
        setupPersistAccountMocks()

        // When - tokenMigration is true
        callOnAuthFlowComplete(tokenMigration = true)

        // Then - persistAccount should be used instead of createAccount/switchToUser
        verify(exactly = 1) { addAccount.invoke(any()) }
        verify(exactly = 1) { mockUserAccountManager.updateAccount(any<Account>(), any<UserAccount>()) }
        verify(exactly = 0) { mockUserAccountManager.createAccount(any()) }
        verify(exactly = 0) { mockUserAccountManager.switchToUser(any()) }
    }

    @Test
    fun testOnAuthFlowComplete_tokenMigration_shouldHandleLockPolicies() = runTest {
        // Given
        val userIdentity = createIdServiceResponse()
        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentity
        setupPersistAccountMocks()

        // When - tokenMigration is true
        callOnAuthFlowComplete(tokenMigration = true)

        // Then - policies should still be handled
        verify(exactly = 1) { handleScreenLockPolicy.invoke(any(), any()) }
        verify(exactly = 1) { handleBiometricAuthPolicy.invoke(any(), any()) }
    }

    @Test
    fun testOnAuthFlowComplete_tokenMigration_withBlockedIntegrationUser_shouldCallError() = runTest {
        // When - tokenMigration is true but user is blocked
        callOnAuthFlowComplete(
            blockIntegrationUser = true,
            tokenMigration = true,
        )

        // Then - error should still be called for blocked users
        verify { onAuthFlowError.invoke("Error", "Authentication error. Please try again.", null) }
        verify(exactly = 0) { onAuthFlowSuccess.invoke(any()) }
        verify(exactly = 0) { startMainActivity.invoke() }
    }

    @Test
    fun testOnAuthFlowComplete_tokenMigration_withManagedAppRequirement_shouldCallError() = runTest {
        // Given
        val userIdentityWithManagedAppRequirement = createIdServiceResponse(
            customPermissions = JSONObject().apply {
                put("must_be_managed_app", true)
            }
        )
        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentityWithManagedAppRequirement
        every { mockRuntimeConfig.isManagedApp } returns false

        // When - tokenMigration is true but managed app required
        callOnAuthFlowComplete(tokenMigration = true)

        // Then - error should still be called
        verify { onAuthFlowError.invoke("Error", "Authentication only allowed from managed device.", null) }
        verify(exactly = 0) { onAuthFlowSuccess.invoke(any()) }
        verify(exactly = 0) { startMainActivity.invoke() }
    }

    @Test
    fun testOnAuthFlowComplete_tokenMigration_persistsNewAccountToAccountManager() = runTest {
        // Given
        val userIdentity = createIdServiceResponse()
        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentity
        val mockAcctManager = setupPersistAccountMocks(addAccountExplicitlyReturns = true)

        // When
        callOnAuthFlowComplete(tokenMigration = true)

        // Then - new account should be added to AccountManager
        verify { mockAcctManager.addAccountExplicitly(
            match { it.name == "test@example.com (https://test.salesforce.com)" && it.type == "test_account_type" },
            any(),
            any()
        ) }
        verify(exactly = 0) { mockAcctManager.setPassword(any(), any()) }
        verify { mockAcctManager.setAuthToken(any(), eq(AccountManager.KEY_AUTHTOKEN), any()) }
    }

    @Test
    fun testOnAuthFlowComplete_tokenMigration_existingAccount_updatesPassword() = runTest {
        // Given
        val userIdentity = createIdServiceResponse()
        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentity
        val mockAcctManager = setupPersistAccountMocks(addAccountExplicitlyReturns = false)

        // When
        callOnAuthFlowComplete(tokenMigration = true)

        // Then - existing account should have password updated
        verify { mockAcctManager.setPassword(any(), any()) }
        verify { mockAcctManager.setAuthToken(any(), eq(AccountManager.KEY_AUTHTOKEN), any()) }
        verify { mockUserAccountManager.updateAccount(any<Account>(), any<UserAccount>()) }
    }

    @Test
    fun testOnAuthFlowComplete_tokenMigration_doesNotCallNonMigrationFlowSteps() = runTest {
        // Given
        val userIdentity = createIdServiceResponse()
        coEvery { fetchUserIdentity.invoke(any()) } returns userIdentity
        setupPersistAccountMocks()

        // When
        callOnAuthFlowComplete(tokenMigration = true)

        // Then - non-migration flow steps should NOT be called
        verify(exactly = 0) { mockUserAccountManager.createAccount(any()) }
        verify(exactly = 0) { mockUserAccountManager.switchToUser(any()) }
        verify(exactly = 0) { startMainActivity.invoke() }
        verify(exactly = 0) { updateLoggingPrefs.invoke(any()) }
        verify(exactly = 0) { mockUserAccountManager.sendUserSwitchIntent(any(), any()) }
    }

    // endregion

    // region handleScreenLockPolicy Tests

    @Test
    fun testHandleScreenLockPolicy_positiveTimeout_registersFeatureAndStoresPolicy() {
        // Given
        val mockScreenLockManager = mockk<ScreenLockManager>(relaxed = true)
        val mockSdkManager = setupMockSdkManager(screenLockManager = mockScreenLockManager)

        val userIdentity = createIdServiceResponse()
        userIdentity.screenLock = true
        userIdentity.screenLockTimeout = 10

        val account = mockk<UserAccount>()

        // When
        com.salesforce.androidsdk.auth.handleScreenLockPolicy(userIdentity, account)

        // Then
        verify { mockSdkManager.registerUsedAppFeature(FEATURE_SCREEN_LOCK, account) }
        verify { mockScreenLockManager.storeMobilePolicy(account, enabled = true, 600000) }
    }

    @Test
    fun testHandleScreenLockPolicy_zeroTimeout_enabledManager_unregistersFeatureAndCleansUp() {
        // Given
        val mockScreenLockManager = mockk<ScreenLockManager>(relaxed = true) {
            every { enabled } returns true
        }
        val mockSdkManager = setupMockSdkManager(screenLockManager = mockScreenLockManager)

        val userIdentity = createIdServiceResponse()
        userIdentity.screenLockTimeout = 0

        val account = mockk<UserAccount>()

        // When
        com.salesforce.androidsdk.auth.handleScreenLockPolicy(userIdentity, account)

        // Then
        verify { mockSdkManager.unregisterUsedAppFeature(FEATURE_SCREEN_LOCK, account) }
        verify { mockScreenLockManager.cleanUp(account) }
    }

    @Test
    fun testHandleScreenLockPolicy_negativeTimeout_disabledManager_noOp() {
        // Given
        val mockScreenLockManager = mockk<ScreenLockManager>(relaxed = true) {
            every { enabled } returns false
        }
        val mockSdkManager = setupMockSdkManager(screenLockManager = mockScreenLockManager)

        val userIdentity = createIdServiceResponse()
        val account = mockk<UserAccount>()

        // When
        com.salesforce.androidsdk.auth.handleScreenLockPolicy(userIdentity, account)

        // Then
        verify(exactly = 0) { mockSdkManager.registerUsedAppFeature(any()) }
        verify(exactly = 0) { mockSdkManager.unregisterUsedAppFeature(any()) }
        verify(exactly = 0) { mockScreenLockManager.storeMobilePolicy(any(), any(), any()) }
        verify(exactly = 0) { mockScreenLockManager.cleanUp(any()) }
    }

    @Test
    fun testHandleScreenLockPolicy_nullIdentity_enabledManager_unregistersAndCleansUp() {
        // Given
        val mockScreenLockManager = mockk<ScreenLockManager>(relaxed = true) {
            every { enabled } returns true
        }
        val mockSdkManager = setupMockSdkManager(screenLockManager = mockScreenLockManager)

        val account = mockk<UserAccount>()

        // When
        com.salesforce.androidsdk.auth.handleScreenLockPolicy(null, account)

        // Then
        verify { mockSdkManager.unregisterUsedAppFeature(FEATURE_SCREEN_LOCK, account) }
        verify { mockScreenLockManager.cleanUp(account) }
    }

    @Test
    fun testHandleScreenLockPolicy_nullIdentity_disabledManager_noOp() {
        // Given
        val mockScreenLockManager = mockk<ScreenLockManager>(relaxed = true) {
            every { enabled } returns false
        }
        val mockSdkManager = setupMockSdkManager(screenLockManager = mockScreenLockManager)

        val account = mockk<UserAccount>()

        // When
        com.salesforce.androidsdk.auth.handleScreenLockPolicy(null, account)

        // Then
        verify(exactly = 0) { mockSdkManager.registerUsedAppFeature(any()) }
        verify(exactly = 0) { mockSdkManager.unregisterUsedAppFeature(any()) }
        verify(exactly = 0) { mockScreenLockManager.storeMobilePolicy(any(), any(), any()) }
        verify(exactly = 0) { mockScreenLockManager.cleanUp(any()) }
    }

    // endregion

    // region handleBiometricAuthPolicy Tests

    @Test
    fun testHandleBiometricAuthPolicy_biometricEnabled_registersFeatureAndStoresPolicy() {
        // Given
        val mockBioAuthManager = mockk<BiometricAuthenticationManager>(relaxed = true)
        val mockSdkManager = setupMockSdkManager(biometricAuthenticationManager = mockBioAuthManager)

        val userIdentity = createIdServiceResponse()
        userIdentity.biometricAuth = true
        userIdentity.biometricAuthTimeout = 15

        val account = mockk<UserAccount>()

        // When
        com.salesforce.androidsdk.auth.handleBiometricAuthPolicy(userIdentity, account)

        // Then
        verify { mockSdkManager.registerUsedAppFeature(FEATURE_BIOMETRIC_AUTH, account) }
        verify { mockBioAuthManager.storeMobilePolicy(account, enabled = true, 900000) }
    }

    @Test
    fun testHandleBiometricAuthPolicy_biometricDisabled_enabledManager_unregistersFeatureAndCleansUp() {
        // Given
        val mockBioAuthManager = mockk<BiometricAuthenticationManager>(relaxed = true) {
            every { enabled } returns true
        }
        val mockSdkManager = setupMockSdkManager(biometricAuthenticationManager = mockBioAuthManager)

        val userIdentity = createIdServiceResponse()
        userIdentity.biometricAuth = false

        val account = mockk<UserAccount>()

        // When
        com.salesforce.androidsdk.auth.handleBiometricAuthPolicy(userIdentity, account)

        // Then
        verify { mockSdkManager.unregisterUsedAppFeature(FEATURE_BIOMETRIC_AUTH, account) }
        verify { mockBioAuthManager.cleanUp(account) }
    }

    @Test
    fun testHandleBiometricAuthPolicy_biometricDisabled_disabledManager_noOp() {
        // Given
        val mockBioAuthManager = mockk<BiometricAuthenticationManager>(relaxed = true) {
            every { enabled } returns false
        }
        val mockSdkManager = setupMockSdkManager(biometricAuthenticationManager = mockBioAuthManager)

        val userIdentity = createIdServiceResponse()
        userIdentity.biometricAuth = false

        val account = mockk<UserAccount>()

        // When
        com.salesforce.androidsdk.auth.handleBiometricAuthPolicy(userIdentity, account)

        // Then
        verify(exactly = 0) { mockSdkManager.registerUsedAppFeature(any()) }
        verify(exactly = 0) { mockSdkManager.unregisterUsedAppFeature(any()) }
        verify(exactly = 0) { mockBioAuthManager.storeMobilePolicy(any(), any(), any()) }
        verify(exactly = 0) { mockBioAuthManager.cleanUp(any()) }
    }

    @Test
    fun testHandleBiometricAuthPolicy_nullIdentity_enabledManager_unregistersAndCleansUp() {
        // Given
        val mockBioAuthManager = mockk<BiometricAuthenticationManager>(relaxed = true) {
            every { enabled } returns true
        }
        val mockSdkManager = setupMockSdkManager(biometricAuthenticationManager = mockBioAuthManager)

        val account = mockk<UserAccount>()

        // When
        com.salesforce.androidsdk.auth.handleBiometricAuthPolicy(null, account)

        // Then
        verify { mockSdkManager.unregisterUsedAppFeature(FEATURE_BIOMETRIC_AUTH, account) }
        verify { mockBioAuthManager.cleanUp(account) }
    }

    @Test
    fun testHandleBiometricAuthPolicy_nullIdentity_disabledManager_noOp() {
        // Given
        val mockBioAuthManager = mockk<BiometricAuthenticationManager>(relaxed = true) {
            every { enabled } returns false
        }
        val mockSdkManager = setupMockSdkManager(biometricAuthenticationManager = mockBioAuthManager)

        val account = mockk<UserAccount>()

        // When
        com.salesforce.androidsdk.auth.handleBiometricAuthPolicy(null, account)

        // Then
        verify(exactly = 0) { mockSdkManager.registerUsedAppFeature(any()) }
        verify(exactly = 0) { mockSdkManager.unregisterUsedAppFeature(any()) }
        verify(exactly = 0) { mockBioAuthManager.storeMobilePolicy(any(), any(), any()) }
        verify(exactly = 0) { mockBioAuthManager.cleanUp(any()) }
    }

    // endregion

    // region handleDuplicateUserAccount Tests

    @Test
    fun testHandleDuplicateUserAccount_nullAuthenticatedUsers_noOp() {
        // Given
        val mockUam = mockk<UserAccountManager>(relaxed = true) {
            every { authenticatedUsers } returns null
        }
        val account = buildTestUserAccount()

        // When
        com.salesforce.androidsdk.auth.handleDuplicateUserAccount(mockUam, account, null)

        // Then
        verify(exactly = 0) { mockUam.clearCachedCurrentUser() }
    }

    @Test
    fun testHandleDuplicateUserAccount_noDuplicate_noRemoval() {
        // Given
        val otherUser = buildTestUserAccount(userId = "005OTHER")
        val mockUam = mockk<UserAccountManager>(relaxed = true) {
            every { authenticatedUsers } returns mutableListOf(otherUser)
        }
        val account = buildTestUserAccount()

        // When
        com.salesforce.androidsdk.auth.handleDuplicateUserAccount(mockUam, account, null)

        // Then
        verify(exactly = 0) { mockUam.clearCachedCurrentUser() }
        verify(exactly = 0) { mockUam.buildAccount(any()) }
    }

    @Test
    fun testHandleDuplicateUserAccount_duplicateFound_sameRefreshToken_removesAccountOnly() {
        // Given
        val mockClientManager = mockk<ClientManager>(relaxed = true)
        setupMockSdkManager(clientManager = mockClientManager)

        val duplicateUser = buildTestUserAccount(refreshToken = "same_token")
        val mockUam = mockk<UserAccountManager>(relaxed = true) {
            every { authenticatedUsers } returns mutableListOf(duplicateUser)
        }
        val account = buildTestUserAccount(refreshToken = "same_token")

        // When
        com.salesforce.androidsdk.auth.handleDuplicateUserAccount(mockUam, account, null)

        // Then
        verify { mockUam.clearCachedCurrentUser() }
    }

    @Test
    fun testHandleDuplicateUserAccount_duplicateFound_differentRefreshToken_revokesToken() {
        // Given
        val mockClientManager = mockk<ClientManager>(relaxed = true)
        setupMockSdkManager(clientManager = mockClientManager)
        val mockRevokeRefreshToken = mockk<(HttpAccess, URI, String, OAuth2.LogoutReason) -> Unit>(relaxed = true)

        val duplicateUser = buildTestUserAccount(
            refreshToken = "old_token",
            instanceServer = "https://test.salesforce.com"
        )
        val mockAccount = mockk<Account>()
        val mockUam = mockk<UserAccountManager>(relaxed = true) {
            every { authenticatedUsers } returns mutableListOf(duplicateUser)
            every { buildAccount(duplicateUser) } returns mockAccount
        }
        val account = buildTestUserAccount(refreshToken = "new_token")

        // When
        com.salesforce.androidsdk.auth.handleDuplicateUserAccount(mockUam, account, null, mockRevokeRefreshToken)
        Thread.sleep(500)

        // Then
        verify { mockUam.clearCachedCurrentUser() }
        verify {
            mockRevokeRefreshToken.invoke(
                any(),
                any(),
                eq("old_token"),
                eq(OAuth2.LogoutReason.REFRESH_TOKEN_ROTATED)
            )
        }
    }

    @Test
    fun testHandleDuplicateUserAccount_duplicateFound_differentRefreshToken_biometricEnabled_unlocks() {
        // Given
        val mockBioAuthManager = mockk<BiometricAuthenticationManager>(relaxed = true)
        val mockClientManager = mockk<ClientManager>(relaxed = true)
        val mockSdkManager = setupMockSdkManager(
            biometricAuthenticationManager = mockBioAuthManager,
            clientManager = mockClientManager
        )
        setupBiometricEnabledPrefs(mockSdkManager)
        val mockRevokeRefreshToken = mockk<(HttpAccess, URI, String, OAuth2.LogoutReason) -> Unit>(relaxed = true)

        val duplicateUser = buildTestUserAccount(
            refreshToken = "old_token",
            instanceServer = "https://test.salesforce.com"
        )
        val mockAccount = mockk<Account>()
        val mockUam = mockk<UserAccountManager>(relaxed = true) {
            every { authenticatedUsers } returns mutableListOf(duplicateUser)
            every { buildAccount(duplicateUser) } returns mockAccount
        }
        val account = buildTestUserAccount(refreshToken = "new_token")

        // When
        com.salesforce.androidsdk.auth.handleDuplicateUserAccount(mockUam, account, null, mockRevokeRefreshToken)
        Thread.sleep(500)

        // Then
        verify { mockBioAuthManager.onUnlock() }
        verify {
            mockRevokeRefreshToken.invoke(
                any(),
                any(),
                eq("old_token"),
                eq(OAuth2.LogoutReason.REFRESH_TOKEN_ROTATED)
            )
        }
    }

    @Test
    fun testHandleDuplicateUserAccount_biometricIdentity_signsOutExistingBiometricUsers() {
        // Given
        val mockSdkManager = setupMockSdkManager()
        setupBiometricEnabledPrefs(mockSdkManager)

        val existingBioUser = buildTestUserAccount(userId = "005BIO_USER")

        val mockUam = mockk<UserAccountManager>(relaxed = true) {
            every { authenticatedUsers } returns mutableListOf(existingBioUser)
        }
        val account = buildTestUserAccount()
        val userIdentity = createIdServiceResponse()
        userIdentity.biometricAuth = true

        // When
        com.salesforce.androidsdk.auth.handleDuplicateUserAccount(mockUam, account, userIdentity)

        // Then
        verify { mockUam.signoutUser(existingBioUser, null, false, OAuth2.LogoutReason.UNEXPECTED) }
    }

    @Test
    fun testHandleDuplicateUserAccount_nullIdentity_skipsBiometricSignout() {
        // Given
        val mockSdkManager = setupMockSdkManager()
        setupBiometricEnabledPrefs(mockSdkManager)

        val existingBioUser = buildTestUserAccount(userId = "005BIO_USER")

        val mockUam = mockk<UserAccountManager>(relaxed = true) {
            every { authenticatedUsers } returns mutableListOf(existingBioUser)
        }
        val account = buildTestUserAccount()

        // When
        com.salesforce.androidsdk.auth.handleDuplicateUserAccount(mockUam, account, null)

        // Then
        verify(exactly = 0) { mockUam.signoutUser(any(), any(), any(), any<OAuth2.LogoutReason>()) }
    }

    // endregion

    private fun buildTestUserAccount(
        userId: String = "005000000000000AAA",
        orgId: String = "00D000000000000EAA",
        refreshToken: String = "test_refresh_token",
        instanceServer: String = "https://test.salesforce.com"
    ): UserAccount {
        return UserAccountBuilder.getInstance()
            .userId(userId)
            .orgId(orgId)
            .refreshToken(refreshToken)
            .instanceServer(instanceServer)
            .authToken("test_auth_token")
            .loginServer("https://login.salesforce.com")
            .idUrl("https://test.salesforce.com/id/$orgId/$userId")
            .accountName("test_account")
            .build()
    }

    private fun setupPersistAccountMocks(addAccountExplicitlyReturns: Boolean = true): AccountManager {
        val mockAcctManager = mockk<AccountManager>(relaxed = true)

        mockkObject(SalesforceSDKManager)
        val mockSdkManager = mockk<SalesforceSDKManager>(relaxed = true)
        every { SalesforceSDKManager.getInstance() } returns mockSdkManager
        every { mockSdkManager.accountType } returns "test_account_type"
        every { mockSdkManager.appContext } returns testContext
        every { SalesforceSDKManager.encryptionKey } returns "test_encryption_key"
        every { SalesforceSDKManager.encrypt(any(), any()) } answers { firstArg<String?>() }

        mockkStatic(AccountManager::class)
        every { AccountManager.get(any()) } returns mockAcctManager
        every { mockAcctManager.addAccountExplicitly(any(), any(), any()) } returns addAccountExplicitlyReturns

        every { mockUserAccountManager.updateAccount(any<Account>(), any<UserAccount>()) } returns mockk<android.os.Bundle>()

        return mockAcctManager
    }

    private fun setupBiometricEnabledPrefs(mockSdkManager: SalesforceSDKManager) {
        val mockPrefs = mockk<android.content.SharedPreferences>(relaxed = true) {
            every { getBoolean("bio_auth_enabled", false) } returns true
        }
        val mockContext = mockk<Context>(relaxed = true) {
            every { getSharedPreferences(any<String>(), any()) } returns mockPrefs
        }
        every { mockSdkManager.appContext } returns mockContext
    }

    private fun setupMockSdkManager(
        screenLockManager: ScreenLockManager? = null,
        biometricAuthenticationManager: BiometricAuthenticationManager? = null,
        clientManager: ClientManager? = null
    ): SalesforceSDKManager {
        mockkObject(SalesforceSDKManager)
        val mockSdkManager = mockk<SalesforceSDKManager>(relaxed = true)
        every { SalesforceSDKManager.getInstance() } returns mockSdkManager
        screenLockManager?.let { every { mockSdkManager.screenLockManager } returns it }
        biometricAuthenticationManager?.let { every { mockSdkManager.biometricAuthenticationManager } returns it }
        clientManager?.let { every { mockSdkManager.clientManager } returns it }
        return mockSdkManager
    }

    private suspend fun callOnAuthFlowComplete(
        customTokenResponse: OAuth2.TokenEndpointResponse? = null,
        nativeLogin: Boolean = false,
        tokenMigration: Boolean = false,
        blockIntegrationUser: Boolean = false,
        identityFetchResult: IdentityFetchResult? = null,
    ) {
        onAuthFlowComplete(
            tokenResponse = customTokenResponse ?: createTokenEndpointResponse(),
            loginServer = "https://login.salesforce.com",
            consumerKey = "test_consumer_key",
            onAuthFlowError = onAuthFlowError,
            onAuthFlowSuccess = onAuthFlowSuccess,
            buildAccountName = buildAccountName,
            nativeLogin = nativeLogin,
            tokenMigration = tokenMigration,
            context = testContext,
            userAccountManager = mockUserAccountManager,
            blockIntegrationUser = blockIntegrationUser,
            runtimeConfig = mockRuntimeConfig,
            updateLoggingPrefs = updateLoggingPrefs,
            fetchUserIdentity = fetchUserIdentity,
            fetchUserIdentityResult = identityFetchResult?.let { result ->
                { _: OAuth2.TokenEndpointResponse -> result }
            },
            startMainActivity = startMainActivity,
            setAdministratorPreferences = setAdministratorPreferences,
            addAccount = addAccount,
            handleScreenLockPolicy = handleScreenLockPolicy,
            handleBiometricAuthPolicy = handleBiometricAuthPolicy,
            handleDuplicateUserAccount = handleDuplicateUserAccount
        )
    }

    private fun createTokenEndpointResponse(
        accessToken: String = "test_access_token",
        refreshToken: String? = "test_refresh_token",
        instanceUrl: String = "https://test.salesforce.com",
        idUrl: String = "https://test.salesforce.com/id/00D000000000000EAA/005000000000000AAA",
        scope: String = "refresh_token id"
    ): OAuth2.TokenEndpointResponse {
        val params = mutableMapOf<String, String>(
            "access_token" to accessToken,
            "instance_url" to instanceUrl,
            "id" to idUrl,
            "scope" to scope
        )
        refreshToken?.let { params["refresh_token"] = it }
        return OAuth2.TokenEndpointResponse(params)
    }

    private fun createIdServiceResponse(
        username: String = "test@example.com",
        email: String = "test@example.com",
        firstName: String = "Test",
        lastName: String = "User",
        displayName: String = "Test User",
        nickname: String = "testuser",
        userType: String = "STANDARD",
        language: String = "en_US",
        locale: String = "en_US",
        lastModifiedDate: String = "2023-01-01T00:00:00Z",
        userId: String = "005000000000000AAA",
        organizationId: String = "00D000000000000EAA",
        idUrl: String = "https://test.salesforce.com/id/00D000000000000EAA/005000000000000AAA",
        active: Boolean = true,
        utcOffset: Int = -28800000,
        pictureUrl: String = "https://test.salesforce.com/profilephoto/005/F",
        thumbnailUrl: String = "https://test.salesforce.com/profilephoto/005/T",
        customPermissions: JSONObject? = null
    ): OAuth2.IdServiceResponse {
        return OAuth2.IdServiceResponse(JSONObject().apply {
            put("id", idUrl)
            put("username", username)
            put("email", email)
            put("first_name", firstName)
            put("last_name", lastName)
            put("display_name", displayName)
            put("nick_name", nickname)
            put("user_type", userType)
            put("language", language)
            put("locale", locale)
            put("last_modified_date", lastModifiedDate)
            put("user_id", userId)
            put("organization_id", organizationId)
            put("active", active)
            put("utcOffset", utcOffset)
            put("photos", JSONObject().apply {
                put("picture", pictureUrl)
                put("thumbnail", thumbnailUrl)
            })
            put("urls", JSONObject().apply {
                put("enterprise", "https://test.salesforce.com/services/Soap/c/{version}/00D000000000000EAA")
                put("metadata", "https://test.salesforce.com/services/Soap/m/{version}/00D000000000000EAA")
                put("partner", "https://test.salesforce.com/services/Soap/u/{version}/00D000000000000EAA")
                put("rest", "https://test.salesforce.com/services/data/v{version}/")
                put("sobjects", "https://test.salesforce.com/services/data/v{version}/sobjects/")
                put("search", "https://test.salesforce.com/services/data/v{version}/search/")
                put("query", "https://test.salesforce.com/services/data/v{version}/query/")
                put("recent", "https://test.salesforce.com/services/data/v{version}/recent/")
                put("profile", "https://test.salesforce.com/005000000000000AAA")
                put("feeds", "https://test.salesforce.com/services/data/v{version}/chatter/feeds")
                put("groups", "https://test.salesforce.com/services/data/v{version}/chatter/groups")
                put("users", "https://test.salesforce.com/services/data/v{version}/chatter/users")
                put("feed_items", "https://test.salesforce.com/services/data/v{version}/chatter/feed-items")
            })
            customPermissions?.let { put("custom_permissions", it) }
        })
    }
}
