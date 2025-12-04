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

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.config.RuntimeConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
    private var blockIntegrationUser: Boolean = false
    private var nativeLogin: Boolean = false

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

    @Test
    fun testOnAuthFlowComplete_blockIntegrationUser_shouldCallError() = runTest {
        // Given
        blockIntegrationUser = true

        // When
        callOnAuthFlowComplete()

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
            .nativeLogin(nativeLogin)
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
    fun testOnAuthFlowComplete_successfulFlowWithoutIdScope_shouldCallSuccess() = runTest {
        // Given
        val tokenResponseWithoutIdScope = createTokenEndpointResponse(
            scope = "refresh_token" // Missing id scope
        )
        
        // Mock fetchUserIdentity to return null (simulating no id scope)
        coEvery { fetchUserIdentity.invoke(any()) } returns null

        // Create the expected UserAccount object without IdServiceResponse population
        val expectedAccount = UserAccountBuilder.getInstance()
            .populateFromTokenEndpointResponse(tokenResponseWithoutIdScope)
            .populateFromIdServiceResponse(null) // No identity service response
            .accountName(buildAccountName(null, tokenResponseWithoutIdScope.instanceUrl))
            .loginServer("https://login.salesforce.com")
            .clientId("test_consumer_key")
            .nativeLogin(nativeLogin)
            .build()

        // When
        callOnAuthFlowComplete(tokenResponseWithoutIdScope)

        // Then
        verify(exactly = 0) { onAuthFlowError.invoke(any(), any(), any()) }
        verify { onAuthFlowSuccess.invoke(expectedAccount) }
        verify { mockUserAccountManager.createAccount(expectedAccount) }
        verify { mockUserAccountManager.switchToUser(expectedAccount) }
        verify { setAdministratorPreferences.invoke(null, expectedAccount) }
        verify { handleDuplicateUserAccount.invoke(mockUserAccountManager, expectedAccount, null) }
        verify { addAccount.invoke(expectedAccount) }
        verify { updateLoggingPrefs.invoke(expectedAccount) }
        verify { startMainActivity.invoke() }
        verify { handleScreenLockPolicy.invoke(null, expectedAccount) }
        verify { handleBiometricAuthPolicy.invoke(null, expectedAccount) }
        
        // Verify that fetchUserIdentity was called but returned null
        coVerify(exactly = 1) { fetchUserIdentity.invoke(tokenResponseWithoutIdScope) }
    }

    private suspend fun callOnAuthFlowComplete(customTokenResponse: OAuth2.TokenEndpointResponse? = null) {
        onAuthFlowComplete(
            tokenResponse = customTokenResponse ?: createTokenEndpointResponse(),
            loginServer = "https://login.salesforce.com",
            consumerKey = "test_consumer_key",
            onAuthFlowError = onAuthFlowError,
            onAuthFlowSuccess = onAuthFlowSuccess,
            buildAccountName = buildAccountName,
            nativeLogin = nativeLogin,
            context = testContext,
            userAccountManager = mockUserAccountManager,
            blockIntegrationUser = blockIntegrationUser,
            runtimeConfig = mockRuntimeConfig,
            updateLoggingPrefs = updateLoggingPrefs,
            fetchUserIdentity = fetchUserIdentity,
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