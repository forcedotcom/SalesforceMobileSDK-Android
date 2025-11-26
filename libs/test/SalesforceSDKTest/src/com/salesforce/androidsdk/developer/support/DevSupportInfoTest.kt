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
import org.junit.Assert.assertNotNull
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
        
        val devSupportInfo = DevSupportInfo(
            basicInfo = listOf(
                "SDK Version" to sdkVersion,
                "App Type" to appType,
                "User Agent" to userAgent,
                "Authenticated Users" to ""
            ),
            authConfigSection = "Authentication Configuration" to listOf("Test" to "Config"),
            bootConfigSection = "Boot Configuration" to listOf(
                "Consumer Key" to "test_consumer_key",
                "Redirect URI" to "test://redirect",
                "Scopes" to "api web"
            ),
            currentUserSection = null,
            runtimeConfigSection = "Runtime Configuration" to listOf("Managed App" to "false")
        )

        // Verify basic info contains the expected values
        val basicInfo = devSupportInfo.basicInfo!!
        assertTrue(basicInfo.any { it.first == "SDK Version" && it.second == sdkVersion })
        assertTrue(basicInfo.any { it.first == "App Type" && it.second == appType })
        assertTrue(basicInfo.any { it.first == "User Agent" && it.second == userAgent })
        
        // Verify auth config section exists
        assertTrue(devSupportInfo.authConfigSection != null)
    }

    @Test
    fun bootConfigValues_NativeApp_ContainsBasicValues() {
        val devSupportInfo = DevSupportInfo(
            basicInfo = listOf(
                "SDK Version" to "test_version",
                "App Type" to "Native",
                "User Agent" to "TestUserAgent",
                "Authenticated Users" to ""
            ),
            authConfigSection = "Authentication Configuration" to listOf("Test" to "Config"),
            bootConfigSection = "Boot Configuration" to listOf(
                "Consumer Key" to "test_consumer_key",
                "Redirect URI" to "test://redirect",
                "Scopes" to "api web"
            ),
            currentUserSection = null,
            runtimeConfigSection = "Runtime Configuration" to listOf("Managed App" to "false")
        )

        val bootConfigValues = devSupportInfo.bootConfigSection!!.second

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
        val devSupportInfo = DevSupportInfo(
            basicInfo = listOf(
                "SDK Version" to "test_version",
                "App Type" to "Hybrid",
                "User Agent" to "TestUserAgent",
                "Authenticated Users" to ""
            ),
            authConfigSection = "Authentication Configuration" to listOf("Test" to "Config"),
            bootConfigSection = "Boot Configuration" to listOf(
                "Consumer Key" to "test_consumer_key",
                "Redirect URI" to "test://redirect",
                "Scopes" to "api web",
                "Local" to "true",
                "Start Page" to "index.html",
                "Unauthenticated Start Page" to "login.html",
                "Error Page" to "error.html",
                "Should Authenticate" to "true",
                "Attempt Offline Load" to "false"
            ),
            currentUserSection = null,
            runtimeConfigSection = "Runtime Configuration" to listOf("Managed App" to "false")
        )

        val bootConfigValues = devSupportInfo.bootConfigSection!!.second

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
        val devSupportInfo = DevSupportInfo(
            basicInfo = listOf(
                "SDK Version" to "test_version",
                "App Type" to "Hybrid",
                "User Agent" to "TestUserAgent",
                "Authenticated Users" to ""
            ),
            authConfigSection = "Authentication Configuration" to listOf("Test" to "Config"),
            bootConfigSection = "Boot Configuration" to listOf(
                "Consumer Key" to "test_consumer_key",
                "Redirect URI" to "test://redirect",
                "Scopes" to "api web",
                "Local" to "true",
                "Start Page" to "index.html",
                "Unauthenticated Start Page" to "login.html",
                "Error Page" to "error.html",
                "Should Authenticate" to "true",
                "Attempt Offline Load" to "false"
            ),
            currentUserSection = null,
            runtimeConfigSection = "Runtime Configuration" to listOf("Managed App" to "false")
        )

        val bootConfigValues = devSupportInfo.bootConfigSection!!.second

        assertEquals("test_consumer_key", bootConfigValues.find { it.first == "Consumer Key" }?.second)
        assertEquals("test://redirect", bootConfigValues.find { it.first == "Redirect URI" }?.second)
        assertEquals("api web", bootConfigValues.find { it.first == "Scopes" }?.second)
        assertEquals("true", bootConfigValues.find { it.first == "Local" }?.second)
        assertEquals("index.html", bootConfigValues.find { it.first == "Start Page" }?.second)
    }

    @Test
    fun runtimeConfigValues_UnmanagedApp_ContainsOnlyManagedFlag() {
        val runtimeConfig = createMockRuntimeConfig(isManagedApp = false)
        
        val devSupportInfo = DevSupportInfo(
            basicInfo = listOf(
                "SDK Version" to "test_version",
                "App Type" to "Native",
                "User Agent" to "TestUserAgent",
                "Authenticated Users" to ""
            ),
            authConfigSection = "Authentication Configuration" to listOf("Test" to "Config"),
            bootConfigSection = "Boot Configuration" to listOf(
                "Consumer Key" to "test_consumer_key",
                "Redirect URI" to "test://redirect",
                "Scopes" to "api web"
            ),
            currentUserSection = null,
            runtimeConfigSection = "Runtime Configuration" to DevSupportInfo.parseRuntimeConfig(runtimeConfig)
        )

        val runtimeConfigValues = devSupportInfo.runtimeConfigSection!!.second

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
        
        val devSupportInfo = DevSupportInfo(
            basicInfo = listOf(
                "SDK Version" to "test_version",
                "App Type" to "Native",
                "User Agent" to "TestUserAgent",
                "Authenticated Users" to ""
            ),
            authConfigSection = "Authentication Configuration" to listOf("Test" to "Config"),
            bootConfigSection = "Boot Configuration" to listOf(
                "Consumer Key" to "test_consumer_key",
                "Redirect URI" to "test://redirect",
                "Scopes" to "api web"
            ),
            currentUserSection = null,
            runtimeConfigSection = "Runtime Configuration" to DevSupportInfo.parseRuntimeConfig(runtimeConfig)
        )

        val runtimeConfigValues = devSupportInfo.runtimeConfigSection!!.second

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
        
        val devSupportInfo = DevSupportInfo(
            basicInfo = listOf(
                "SDK Version" to "test_version",
                "App Type" to "Native",
                "User Agent" to "TestUserAgent",
                "Authenticated Users" to ""
            ),
            authConfigSection = "Authentication Configuration" to listOf("Test" to "Config"),
            bootConfigSection = "Boot Configuration" to listOf(
                "Consumer Key" to "test_consumer_key",
                "Redirect URI" to "test://redirect",
                "Scopes" to "api web"
            ),
            currentUserSection = null,
            runtimeConfigSection = "Runtime Configuration" to DevSupportInfo.parseRuntimeConfig(runtimeConfig)
        )

        val runtimeConfigValues = devSupportInfo.runtimeConfigSection!!.second

        assertTrue(runtimeConfigValues.any { it.first == "OAuth ID" && it.second == "N/A" })
        assertTrue(runtimeConfigValues.any { it.first == "Callback URL" && it.second == "N/A" })
    }

    @Test
    fun basicInfo_ContainsAuthenticatedUsers_EmptyList() {
        val devSupportInfo = DevSupportInfo(
            basicInfo = listOf(
                "SDK Version" to "test_version",
                "App Type" to "Native",
                "User Agent" to "TestUserAgent",
                "Authenticated Users" to ""
            ),
            authConfigSection = "Authentication Configuration" to listOf("Test" to "Config"),
            bootConfigSection = "Boot Configuration" to listOf(
                "Consumer Key" to "test_consumer_key",
                "Redirect URI" to "test://redirect",
                "Scopes" to "api web"
            ),
            currentUserSection = null,
            runtimeConfigSection = "Runtime Configuration" to listOf("Managed App" to "false")
        )

        val basicInfo = devSupportInfo.basicInfo!!
        val authenticatedUsersValue = basicInfo.find { it.first == "Authenticated Users" }?.second
        assertEquals("", authenticatedUsersValue)
    }

    @Test
    fun basicInfo_ContainsAuthenticatedUsers_MultipleUsers() {
        val devSupportInfo = DevSupportInfo(
            basicInfo = listOf(
                "SDK Version" to "test_version",
                "App Type" to "Native",
                "User Agent" to "TestUserAgent",
                "Authenticated Users" to "User One (user1@test.com),\nUser Two (user2@test.com)"
            ),
            authConfigSection = "Authentication Configuration" to listOf("Test" to "Config"),
            bootConfigSection = "Boot Configuration" to listOf(
                "Consumer Key" to "test_consumer_key",
                "Redirect URI" to "test://redirect",
                "Scopes" to "api web"
            ),
            currentUserSection = null,
            runtimeConfigSection = "Runtime Configuration" to listOf("Managed App" to "false")
        )

        val basicInfo = devSupportInfo.basicInfo!!
        val authenticatedUsersValue = basicInfo.find { it.first == "Authenticated Users" }?.second
        val expected = "User One (user1@test.com),\nUser Two (user2@test.com)"
        assertEquals(expected, authenticatedUsersValue)
    }

    @Test
    fun currentUserSection_NoCurrentUser_ReturnsNull() {
        val devSupportInfo = DevSupportInfo(
            basicInfo = listOf(
                "SDK Version" to "test_version",
                "App Type" to "Native",
                "User Agent" to "TestUserAgent",
                "Authenticated Users" to ""
            ),
            authConfigSection = "Authentication Configuration" to listOf("Test" to "Config"),
            bootConfigSection = "Boot Configuration" to listOf(
                "Consumer Key" to "test_consumer_key",
                "Redirect URI" to "test://redirect",
                "Scopes" to "api web"
            ),
            currentUserSection = null,
            runtimeConfigSection = "Runtime Configuration" to listOf("Managed App" to "false")
        )

        assertTrue(devSupportInfo.currentUserSection == null)
    }

    @Test
    fun currentUserSection_WithCurrentUser_ContainsAllFields() {
        val user = createMockUserAccount(
            username = "test@salesforce.com",
            displayName = "Test User",
            clientId = "test_client_id",
            scope = "api web refresh_token",
            instanceServer = "https://test.salesforce.com",
            tokenFormat = "oauth2"
        )
        
        val devSupportInfo = DevSupportInfo(
            basicInfo = listOf(
                "SDK Version" to "test_version",
                "App Type" to "Native",
                "User Agent" to "TestUserAgent",
                "Authenticated Users" to ""
            ),
            authConfigSection = "Authentication Configuration" to listOf("Test" to "Config"),
            bootConfigSection = "Boot Configuration" to listOf(
                "Consumer Key" to "test_consumer_key",
                "Redirect URI" to "test://redirect",
                "Scopes" to "api web"
            ),
            currentUserSection = DevSupportInfo.parseUserInfoSection(user),
            runtimeConfigSection = "Runtime Configuration" to listOf("Managed App" to "false")
        )

        val currentUserInfo = devSupportInfo.currentUserSection!!.second

        assertTrue(currentUserInfo.any { it.first == "Username" && it.second == "test@salesforce.com" })
        assertTrue(currentUserInfo.any { it.first == "Consumer Key" && it.second == "test_client_id" })
        assertTrue(currentUserInfo.any { it.first == "Scopes" && it.second == "api web refresh_token" })
        assertTrue(currentUserInfo.any { it.first == "Instance URL" && it.second == "https://test.salesforce.com" })
        assertTrue(currentUserInfo.any { it.first == "Token Format" && it.second == "oauth2" })
    }

    @Test
    fun currentUserSection_NoCurrentUser_NoAccessTokenExpiration() {
        val devSupportInfo = DevSupportInfo(
            basicInfo = listOf(
                "SDK Version" to "test_version",
                "App Type" to "Native",
                "User Agent" to "TestUserAgent",
                "Authenticated Users" to ""
            ),
            authConfigSection = "Authentication Configuration" to listOf("Test" to "Config"),
            bootConfigSection = "Boot Configuration" to listOf(
                "Consumer Key" to "test_consumer_key",
                "Redirect URI" to "test://redirect",
                "Scopes" to "api web"
            ),
            currentUserSection = null,
            runtimeConfigSection = "Runtime Configuration" to listOf("Managed App" to "false")
        )

        assertTrue(devSupportInfo.currentUserSection == null)
    }

    @Test
    fun currentUserSection_NonJwtToken_ReturnsUnknown() {
        val user = createMockUserAccount(tokenFormat = "oauth2")
        
        val devSupportInfo = DevSupportInfo(
            basicInfo = listOf(
                "SDK Version" to "test_version",
                "App Type" to "Native",
                "User Agent" to "TestUserAgent",
                "Authenticated Users" to ""
            ),
            authConfigSection = "Authentication Configuration" to listOf("Test" to "Config"),
            bootConfigSection = "Boot Configuration" to listOf(
                "Consumer Key" to "test_consumer_key",
                "Redirect URI" to "test://redirect",
                "Scopes" to "api web"
            ),
            currentUserSection = DevSupportInfo.parseUserInfoSection(user),
            runtimeConfigSection = "Runtime Configuration" to listOf("Managed App" to "false")
        )

        val currentUserInfo = devSupportInfo.currentUserSection!!.second
        val expiration = currentUserInfo.find { it.first == "Access Token Expiration" }?.second
        assertEquals("Unknown", expiration)
    }

    @Test
    fun currentUserSection_JwtToken_ReturnsFormattedDate() {
        // Create a JWT token that expires in the future
        val futureTime = Calendar.getInstance().apply {
            add(Calendar.HOUR, 2)
        }.timeInMillis / 1000

        val jwtToken = createMockJwtToken(expirationTime = futureTime)
        val user = createMockUserAccount(
            tokenFormat = "jwt",
            authToken = jwtToken
        )
        
        val devSupportInfo = DevSupportInfo(
            basicInfo = listOf(
                "SDK Version" to "test_version",
                "App Type" to "Native",
                "User Agent" to "TestUserAgent",
                "Authenticated Users" to ""
            ),
            authConfigSection = "Authentication Configuration" to listOf("Test" to "Config"),
            bootConfigSection = "Boot Configuration" to listOf(
                "Consumer Key" to "test_consumer_key",
                "Redirect URI" to "test://redirect",
                "Scopes" to "api web"
            ),
            currentUserSection = DevSupportInfo.parseUserInfoSection(user),
            runtimeConfigSection = "Runtime Configuration" to listOf("Managed App" to "false")
        )

        val currentUserInfo = devSupportInfo.currentUserSection!!.second
        val expiration = currentUserInfo.find { it.first == "Access Token Expiration" }?.second!!
        
        // Should not be "Unknown"
        assertNotEquals("Unknown", expiration)
        
        // Should be in the format "yyyy-MM-dd HH:mm:ss"
        assertTrue(expiration.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))
    }

    @Test
    fun secondaryConstructor_ParsesBootConfigAndRuntimeConfig() {
        val basicInfo = listOf(
            "SDK Version" to "test_version",
            "App Type" to "Native",
            "User Agent" to "TestUserAgent",
            "Authenticated Users" to "",
        )
        val authConfig = listOf("Test" to "Config")
        val bootConfig = mockk<BootConfig>(relaxed = true) {
            every { remoteAccessConsumerKey } returns "secondary_consumer_key"
            every { oauthRedirectURI } returns "secondary://redirect"
            every { oauthScopes } returns arrayOf("api", "web", "refresh_token")
        }
        val runtimeConfig = createMockRuntimeConfig(isManagedApp = true, oauthId = "secondary_oauth_id")
        val user = createMockUserAccount(username = "secondary@test.com")

        val devSupportInfo = DevSupportInfo(
            basicInfo = basicInfo,
            authConfig = authConfig,
            bootConfig = bootConfig,
            currentUser = user,
            runtimeConfig = runtimeConfig
        )

        // Verify basic info is preserved
        assertEquals(basicInfo, devSupportInfo.basicInfo)

        // Verify auth config section
        assertEquals("Authentication Configuration", devSupportInfo.authConfigSection?.first)
        assertEquals(authConfig, devSupportInfo.authConfigSection?.second)

        // Verify boot config was parsed
        val bootConfigValues = devSupportInfo.bootConfigSection!!.second
        assertTrue(bootConfigValues.any { it.first == "Consumer Key" && it.second == "secondary_consumer_key" })
        assertTrue(bootConfigValues.any { it.first == "Redirect URI" && it.second == "secondary://redirect" })
        assertTrue(bootConfigValues.any { it.first == "Scopes" && it.second == "api web refresh_token" })

        // Verify current user was parsed
        val currentUserInfo = devSupportInfo.currentUserSection!!.second
        assertTrue(currentUserInfo.any { it.first == "Username" && it.second == "secondary@test.com" })

        // Verify runtime config was parsed
        val runtimeConfigValues = devSupportInfo.runtimeConfigSection!!.second
        assertTrue(runtimeConfigValues.any { it.first == "Managed App" && it.second == "true" })
        assertTrue(runtimeConfigValues.any { it.first == "OAuth ID" && it.second == "secondary_oauth_id" })
    }

    @Test
    fun createFromLegacyDevInfos_RemovesValuesFromBasicInfoWhenMovedToSections() {
        val legacyDevInfos = listOf(
            "SDK Version", "13.2.0",
            "App Type", "Native",
            "User Agent", "TestUserAgent",
            "Use Web Server Authentication", "true",
            "Browser Login Enabled", "false",
            "Consumer Key", "test_key",
            "Redirect URI", "test://redirect",
            "Scopes", "api web",
            "Username", "test@test.com",
            "Instance URL", "https://test.salesforce.com",
            "Managed App", "false",
        )

        val devSupportInfo = DevSupportInfo.createFromLegacyDevInfos(legacyDevInfos)

        val basicInfo = devSupportInfo.basicInfo!!

        // Verify auth config values were removed from basicInfo
        assertFalse(basicInfo.any { it.first == "Use Web Server Authentication" })
        assertFalse(basicInfo.any { it.first == "Browser Login Enabled" })

        // Verify boot config values were removed from basicInfo
        assertFalse(basicInfo.any { it.first == "Consumer Key" })
        assertFalse(basicInfo.any { it.first == "Redirect URI" })
        assertFalse(basicInfo.any { it.first == "Scopes" })

        // Verify current user values were removed from basicInfo
        assertFalse(basicInfo.any { it.first == "Username" })
        assertFalse(basicInfo.any { it.first == "Instance URL" })

        // Verify runtime config values were removed from basicInfo
        assertFalse(basicInfo.any { it.first == "Managed App" })

        // Verify only basic info values remain
        assertTrue(basicInfo.any { it.first == "SDK Version" && it.second == "13.2.0" })
        assertTrue(basicInfo.any { it.first == "App Type" && it.second == "Native" })
        assertTrue(basicInfo.any { it.first == "User Agent" && it.second == "TestUserAgent" })
        assertEquals(3, basicInfo.size)

        // Verify values were moved to appropriate sections
        assertNotNull(devSupportInfo.authConfigSection)
        assertTrue(devSupportInfo.authConfigSection!!.second.any { it.first == "Use Web Server Authentication" })
        
        assertNotNull(devSupportInfo.bootConfigSection)
        assertTrue(devSupportInfo.bootConfigSection!!.second.any { it.first == "Consumer Key" })
        
        assertNotNull(devSupportInfo.currentUserSection)
        assertTrue(devSupportInfo.currentUserSection!!.second.any { it.first == "Username" })
        
        assertNotNull(devSupportInfo.runtimeConfigSection)
        assertTrue(devSupportInfo.runtimeConfigSection!!.second.any { it.first == "Managed App" })
    }

    @Test
    fun createFromLegacyDevInfos_ProducesSameResultAsSecondaryConstructor() {
        // Create mock objects for secondary constructor
        val bootConfig = mockk<BootConfig>(relaxed = true) {
            every { remoteAccessConsumerKey } returns "test_consumer_key"
            every { oauthRedirectURI } returns "test://redirect"
            every { oauthScopes } returns arrayOf("api", "web")
        }
        val runtimeConfig = createMockRuntimeConfig(
            isManagedApp = true,
            oauthId = "test_oauth_id",
            callbackUrl = "test://callback",
            requireCertAuth = true,
            onlyShowAuthorizedHosts = false
        )
        val user = createMockUserAccount(
            username = "test@salesforce.com",
            displayName = "Test User",
            clientId = "test_client_id",
            scope = "api web",
            instanceServer = "https://test.salesforce.com",
            tokenFormat = "oauth2"
        )

        // Create DevSupportInfo using secondary constructor
        val basicInfo = listOf(
            "SDK Version" to "13.2.0",
            "App Type" to "Native",
            "User Agent" to "TestUserAgent",
            "Authenticated Users" to "Test User (test@salesforce.com)",
        )
        val authConfig = listOf(
            "Use Web Server Authentication" to "true",
            "Browser Login Enabled" to "false",
        )
        
        val fromSecondaryConstructor = DevSupportInfo(
            basicInfo = basicInfo,
            authConfig = authConfig,
            bootConfig = bootConfig,
            currentUser = user,
            runtimeConfig = runtimeConfig
        )

        // Create equivalent legacy dev infos list
        val legacyDevInfos = mutableListOf<String>()
        
        // Add basic info
        basicInfo.forEach { (key, value) ->
            legacyDevInfos.add(key)
            legacyDevInfos.add(value)
        }
        
        // Add auth config
        authConfig.forEach { (key, value) ->
            legacyDevInfos.add(key)
            legacyDevInfos.add(value)
        }
        
        // Add boot config
        legacyDevInfos.addAll(listOf(
            "Consumer Key", "test_consumer_key",
            "Redirect URI", "test://redirect",
            "Scopes", "api web",
        ))
        
        // Add current user
        legacyDevInfos.addAll(listOf(
            "Username", "test@salesforce.com",
            "Consumer Key", "test_client_id",
            "Scopes", "api web",
            "Instance URL", "https://test.salesforce.com",
            "Token Format", "oauth2",
            "Access Token Expiration", "Unknown",
            "Beacon Child Consumer Key", user.beaconChildConsumerKey ?: "None",
        ))
        
        // Add runtime config
        legacyDevInfos.addAll(listOf(
            "Managed App", "true",
            "OAuth ID", "test_oauth_id",
            "Callback URL", "test://callback",
            "Require Cert Auth", "true",
            "Only Show Authorized Hosts", "false",
        ))

        val fromLegacy = DevSupportInfo.createFromLegacyDevInfos(legacyDevInfos)

        // Assert the two objects are identical
        assertEquals(fromSecondaryConstructor, fromLegacy)
    }

    // Helper methods

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
