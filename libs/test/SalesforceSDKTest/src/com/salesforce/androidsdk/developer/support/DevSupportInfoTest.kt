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
package com.salesforce.androidsdk.developer.support

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.config.RuntimeConfig
import io.mockk.every
import io.mockk.mockk
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class DevSupportInfoTest {

    @Test
    fun devSupportInfo_BasicProperties_AreCorrect() {
        val sdkVersion = "13.2.0"
        val appType = "app_type_native"
        val userAgent = "fake_user_agent"
        val devSupportInfo = createBasicDevSupportInfo(
            sdkVersion = sdkVersion,
            appType = appType,
            userAgent = userAgent,
        )

        assertEquals(sdkVersion, devSupportInfo.sdkVersion)
        assertEquals(appType, devSupportInfo.appType)
        assertEquals(userAgent, devSupportInfo.userAgent)
        assertTrue(devSupportInfo.authenticatedUsers.isEmpty())
        assertTrue(devSupportInfo.authConfig.isNotEmpty())
    }

    @Test
    fun bootConfigValues_NativeApp_ContainsBasicValues() {
        val devSupportInfo = createBasicDevSupportInfo(appType = "Native")

        val bootConfigValues = devSupportInfo.bootConfigValues

        assertTrue(bootConfigValues.any { it.first == "Consumer Key" })
        assertTrue(bootConfigValues.any { it.first == "Redirect URI" })
        assertTrue(bootConfigValues.any { it.first == "Scopes" })
        
        // Native apps should NOT have hybrid-specific fields
        assertFalse(bootConfigValues.any { it.first == "Local" })
        assertFalse(bootConfigValues.any { it.first == "Start Page" })
        assertFalse(bootConfigValues.any { it.first == "Unauthenticated Start Page" })
    }

    @Test
    fun bootConfigValues_HybridApp_ContainsHybridSpecificValues() {
        val devSupportInfo = createBasicDevSupportInfo(appType = "Hybrid")

        val bootConfigValues = devSupportInfo.bootConfigValues

        // Should have basic values
        assertTrue(bootConfigValues.any { it.first == "Consumer Key" })
        assertTrue(bootConfigValues.any { it.first == "Redirect URI" })
        assertTrue(bootConfigValues.any { it.first == "Scopes" })
        
        // Should have hybrid-specific values
        assertTrue(bootConfigValues.any { it.first == "Local" })
        assertTrue(bootConfigValues.any { it.first == "Start Page" })
        assertTrue(bootConfigValues.any { it.first == "Unauthenticated Start Page" })
        assertTrue(bootConfigValues.any { it.first == "Error Page" })
        assertTrue(bootConfigValues.any { it.first == "Should Authenticate" })
        assertTrue(bootConfigValues.any { it.first == "Attempt Offline Load" })
    }

    @Test
    fun bootConfigValues_HybridApp_ValuesAreCorrect() {
        val devSupportInfo = createBasicDevSupportInfo(appType = "Hybrid")

        val bootConfigValues = devSupportInfo.bootConfigValues

        assertEquals("test_consumer_key", bootConfigValues.find { it.first == "Consumer Key" }?.second)
        assertEquals("test://redirect", bootConfigValues.find { it.first == "Redirect URI" }?.second)
        assertEquals("api web", bootConfigValues.find { it.first == "Scopes" }?.second)
        assertEquals("true", bootConfigValues.find { it.first == "Local" }?.second)
        assertEquals("index.html", bootConfigValues.find { it.first == "Start Page" }?.second)
    }

    @Test
    fun runtimeConfigValues_UnmanagedApp_ContainsOnlyManagedFlag() {
        val runtimeConfig = createMockRuntimeConfig(isManagedApp = false)
        val devSupportInfo = createDevSupportInfoWithRuntimeConfig(runtimeConfig)

        val runtimeConfigValues = devSupportInfo.runtimeConfigValues

        assertEquals(1, runtimeConfigValues.size)
        assertEquals("Managed App", runtimeConfigValues[0].first)
        assertEquals("false", runtimeConfigValues[0].second)
    }

    @Test
    fun runtimeConfigValues_ManagedApp_ContainsManagedSpecificValues() {
        val runtimeConfig = createMockRuntimeConfig(
            isManagedApp = true,
            oauthId = "managed_oauth_id",
            callbackUrl = "managed://callback",
            requireCertAuth = true,
            onlyShowAuthorizedHosts = false
        )
        val devSupportInfo = createDevSupportInfoWithRuntimeConfig(runtimeConfig)

        val runtimeConfigValues = devSupportInfo.runtimeConfigValues

        assertTrue(runtimeConfigValues.any { it.first == "Managed App" && it.second == "true" })
        assertTrue(runtimeConfigValues.any { it.first == "OAuth ID" && it.second == "managed_oauth_id" })
        assertTrue(runtimeConfigValues.any { it.first == "Callback URL" && it.second == "managed://callback" })
        assertTrue(runtimeConfigValues.any { it.first == "Require Cert Auth" && it.second == "true" })
        assertTrue(runtimeConfigValues.any { it.first == "Only Show Authorized Hosts" && it.second == "false" })
    }

    @Test
    fun runtimeConfigValues_ManagedApp_NullValues_ShowNA() {
        val runtimeConfig = createMockRuntimeConfig(
            isManagedApp = true,
            oauthId = null,
            callbackUrl = null
        )
        val devSupportInfo = createDevSupportInfoWithRuntimeConfig(runtimeConfig)

        val runtimeConfigValues = devSupportInfo.runtimeConfigValues

        assertTrue(runtimeConfigValues.any { it.first == "OAuth ID" && it.second == "N/A" })
        assertTrue(runtimeConfigValues.any { it.first == "Callback URL" && it.second == "N/A" })
    }

    @Test
    fun authenticatedUsersString_EmptyList_ReturnsEmptyString() {
        val devSupportInfo = createBasicDevSupportInfo()

        assertEquals("", devSupportInfo.authenticatedUsersString)
    }

    @Test
    fun authenticatedUsersString_MultipleUsers_FormatsCorrectly() {
        val user1 = createMockUserAccount("user1@test.com", "User One")
        val user2 = createMockUserAccount("user2@test.com", "User Two")
        val devSupportInfo = createBasicDevSupportInfo(authenticatedUsers = listOf(user1, user2))

        val expected = "User One (user1@test.com),\nUser Two (user2@test.com)"
        assertEquals(expected, devSupportInfo.authenticatedUsersString)
    }

    @Test
    fun currentUserInfo_NoCurrentUser_ReturnsEmptyList() {
        val devSupportInfo = createBasicDevSupportInfo(currentUser = null)

        assertTrue(devSupportInfo.currentUserInfo.isEmpty())
    }

    @Test
    fun currentUserInfo_WithCurrentUser_ContainsAllFields() {
        val user = createMockUserAccount(
            username = "test@salesforce.com",
            displayName = "Test User",
            clientId = "test_client_id",
            scope = "api web refresh_token",
            instanceServer = "https://test.salesforce.com",
            tokenFormat = "oauth2"
        )
        val devSupportInfo = createBasicDevSupportInfo(currentUser = user)

        val currentUserInfo = devSupportInfo.currentUserInfo

        assertTrue(currentUserInfo.any { it.first == "Username" && it.second == "test@salesforce.com" })
        assertTrue(currentUserInfo.any { it.first == "Consumer Key" && it.second == "test_client_id" })
        assertTrue(currentUserInfo.any { it.first == "Scopes" && it.second == "api web refresh_token" })
        assertTrue(currentUserInfo.any { it.first == "Instance URL" && it.second == "https://test.salesforce.com" })
        assertTrue(currentUserInfo.any { it.first == "Token Format" && it.second == "oauth2" })
    }

    @Test
    fun accessTokenExpiration_NoCurrentUser_ReturnsUnknown() {
        val devSupportInfo = createBasicDevSupportInfo(currentUser = null)

        assertEquals("Unknown", devSupportInfo.accessTokenExpiration)
    }

    @Test
    fun accessTokenExpiration_NonJwtToken_ReturnsUnknown() {
        val user = createMockUserAccount(tokenFormat = "oauth2")
        val devSupportInfo = createBasicDevSupportInfo(currentUser = user)

        assertEquals("Unknown", devSupportInfo.accessTokenExpiration)
    }

    @Test
    fun accessTokenExpiration_JwtToken_ReturnsFormattedDate() {
        // Create a JWT token that expires in the future
        val futureTime = Calendar.getInstance().apply {
            add(Calendar.HOUR, 2)
        }.timeInMillis / 1000

        val jwtToken = createMockJwtToken(expirationTime = futureTime)
        val user = createMockUserAccount(
            tokenFormat = "jwt",
            authToken = jwtToken
        )
        val devSupportInfo = createBasicDevSupportInfo(currentUser = user)

        val expiration = devSupportInfo.accessTokenExpiration
        
        // Should not be "Unknown"
        assertNotEquals("Unknown", expiration)
        
        // Should be in the format "yyyy-MM-dd HH:mm:ss"
        assertTrue(expiration.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))
    }

    // Helper methods

    private fun createBasicDevSupportInfo(
        sdkVersion: String = "test_version",
        appType: String = "Native",
        userAgent: String = "TestUserAgent",
        authenticatedUsers: List<UserAccount> = emptyList(),
        authConfig: List<Pair<String, String>> = listOf("Test" to "Config"),
        currentUser: UserAccount? = null
    ): DevSupportInfo {
        val bootConfig = createMockBootConfig()
        val runtimeConfig = createMockRuntimeConfig(isManagedApp = false)

        return DevSupportInfo(
            sdkVersion = sdkVersion,
            appType = appType,
            userAgent = userAgent,
            authenticatedUsers = authenticatedUsers,
            authConfig = authConfig,
            bootConfig = bootConfig,
            currentUser = currentUser,
            runtimeConfig = runtimeConfig
        )
    }

    private fun createDevSupportInfoWithRuntimeConfig(
        runtimeConfig: RuntimeConfig
    ): DevSupportInfo {
        val bootConfig = createMockBootConfig()

        return DevSupportInfo(
            sdkVersion = "test_version",
            appType = "Native",
            userAgent = "TestUserAgent",
            authenticatedUsers = emptyList(),
            authConfig = listOf("Test" to "Config"),
            bootConfig = bootConfig,
            currentUser = null,
            runtimeConfig = runtimeConfig
        )
    }

    private fun createMockBootConfig(): BootConfig {
        return object : BootConfig() {
            override fun getRemoteAccessConsumerKey() = "test_consumer_key"
            override fun getOauthRedirectURI() = "test://redirect"
            override fun getOauthScopes() = arrayOf("api", "web")
            override fun isLocal() = true
            override fun getStartPage() = "index.html"
            override fun getUnauthenticatedStartPage() = "login.html"
            override fun getErrorPage() = "error.html"
            override fun shouldAuthenticate() = true
            override fun attemptOfflineLoad() = false
        }
    }

    private fun createMockRuntimeConfig(
        isManagedApp: Boolean,
        oauthId: String? = null,
        callbackUrl: String? = null,
        requireCertAuth: Boolean = false,
        onlyShowAuthorizedHosts: Boolean = false
    ): RuntimeConfig {
        return mockk<RuntimeConfig>(relaxed = true) {
            every { isManagedApp() } returns isManagedApp
            every { getString(RuntimeConfig.ConfigKey.ManagedAppOAuthID) } returns oauthId
            every { getString(RuntimeConfig.ConfigKey.ManagedAppCallbackURL) } returns callbackUrl
            every { getBoolean(RuntimeConfig.ConfigKey.RequireCertAuth) } returns requireCertAuth
            every { getBoolean(RuntimeConfig.ConfigKey.OnlyShowAuthorizedHosts) } returns onlyShowAuthorizedHosts
        }
    }

    private fun createMockUserAccount(
        username: String = "test@test.com",
        displayName: String = "Test User",
        clientId: String = "test_client_id",
        scope: String = "api web",
        instanceServer: String = "https://test.salesforce.com",
        tokenFormat: String = "oauth2",
        authToken: String = "test_token"
    ): UserAccount {
        return UserAccount(
            Bundle().apply {
                putString(UserAccount.USER_ID, "test_user_id")
                putString(UserAccount.ORG_ID, "test_org_id")
                putString(UserAccount.USERNAME, username)
                putString(UserAccount.ACCOUNT_NAME, displayName)
                putString(UserAccount.CLIENT_ID, clientId)
                putString(UserAccount.LOGIN_SERVER, instanceServer)
                putString(UserAccount.ID_URL, "$instanceServer/id/test_org_id/test_user_id")
                putString(UserAccount.INSTANCE_SERVER, instanceServer)
                putString(UserAccount.AUTH_TOKEN, authToken)
                putString(UserAccount.REFRESH_TOKEN, "test_refresh_token")
                putString(UserAccount.FIRST_NAME, "Test")
                putString(UserAccount.LAST_NAME, "User")
                putString(UserAccount.DISPLAY_NAME, displayName)
                putString(UserAccount.EMAIL, username)
                putString(UserAccount.PHOTO_URL, "$instanceServer/photo")
                putString(UserAccount.THUMBNAIL_URL, "$instanceServer/thumbnail")
                putString(UserAccount.LIGHTNING_DOMAIN, instanceServer)
                putString(UserAccount.LIGHTNING_SID, "test_sid")
                putString(UserAccount.VF_DOMAIN, instanceServer)
                putString(UserAccount.VF_SID, "test_vf_sid")
                putString(UserAccount.CONTENT_DOMAIN, instanceServer)
                putString(UserAccount.CONTENT_SID, "test_content_sid")
                putString(UserAccount.CSRF_TOKEN, "test_csrf_token")
                putString(UserAccount.NATIVE_LOGIN, "false")
                putString(UserAccount.LANGUAGE, "en_US")
                putString(UserAccount.LOCALE, "en_US")
                putString(UserAccount.SCOPE, scope)
                putString(UserAccount.TOKEN_FORMAT, tokenFormat)
            }
        )
    }

    private fun createMockJwtToken(expirationTime: Long): String {
        // Create a simple JWT token with the expiration time
        // JWT format: header.payload.signature
        val header = JSONObject().apply {
            put("alg", "RS256")
            put("typ", "JWT")
        }
        val payload = JSONObject().apply {
            put("exp", expirationTime)
            put("sub", "test_user")
            put("iss", "https://test.salesforce.com")
        }

        // Base64 encode (simplified - not proper base64url encoding for test purposes)
        val headerEncoded = android.util.Base64.encodeToString(
            header.toString().toByteArray(),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
        val payloadEncoded = android.util.Base64.encodeToString(
            payload.toString().toByteArray(),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )

        return "$headerEncoded.$payloadEncoded.fake_signature"
    }
}
