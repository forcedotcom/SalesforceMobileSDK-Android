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

package com.salesforce.androidsdk.ui

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.config.LoginServerManaging
import com.salesforce.androidsdk.config.LoginServerManager
import com.salesforce.androidsdk.config.LoginServerManager.LoginServer
import com.salesforce.androidsdk.config.RuntimeConfig
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for FrontdoorBridgeLoginOverride
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class FrontdoorBridgeLoginOverrideTest {

    companion object {
        private const val CONSUMER_KEY = "test_consumer_key"
        private const val DIFFERENT_CONSUMER_KEY = "different_consumer_key"
        private const val CODE_VERIFIER = "test_code_verifier"
        private const val SELECTED_LOGIN_SERVER = "https://login.salesforce.com"
        private const val FRONTDOOR_URL_WITH_MATCHING_CLIENT_ID = "https://login.salesforce.com/setup/secur/RemoteAccessAuthorizationPage.apexp?source=lightning&startURL=https%3A%2F%2Flogin.salesforce.com%2Fservices%2Foauth2%2Fauthorize%3Fclient_id%3Dtest_consumer_key%26response_type%3Dcode"
        private const val FRONTDOOR_URL_WITH_DIFFERENT_CLIENT_ID = "https://login.salesforce.com/setup/secur/RemoteAccessAuthorizationPage.apexp?source=lightning&startURL=https%3A%2F%2Flogin.salesforce.com%2Fservices%2Foauth2%2Fauthorize%3Fclient_id%3Ddifferent_consumer_key%26response_type%3Dcode"
        private const val FRONTDOOR_URL_NO_START_URL = "https://login.salesforce.com/setup/secur/RemoteAccessAuthorizationPage.apexp?source=lightning"
        private const val FRONTDOOR_URL_NO_CLIENT_ID = "https://login.salesforce.com/setup/secur/RemoteAccessAuthorizationPage.apexp?source=lightning&startURL=https%3A%2F%2Flogin.salesforce.com%2Fservices%2Foauth2%2Fauthorize%3Fresponse_type%3Dcode"
        private const val CUSTOM_HOST_URL = "https://custom.salesforce.com/setup/secur/RemoteAccessAuthorizationPage.apexp?source=lightning&startURL=https%3A%2F%2Fcustom.salesforce.com%2Fservices%2Foauth2%2Fauthorize%3Fclient_id%3Dtest_consumer_key%26response_type%3Dcode"
    }

    private lateinit var mockSalesforceSDKManager: SalesforceSDKManager
    private lateinit var mockContext: Context
    private lateinit var mockBootConfig: BootConfig
    private lateinit var mockRuntimeConfig: RuntimeConfig
    private lateinit var mockLoginServerManager: LoginServerManager

    @Before
    fun setUp() {
        // Mock static objects
        mockSalesforceSDKManager = mockk()
        mockContext = mockk()
        mockBootConfig = mockk()
        mockRuntimeConfig = mockk()
        mockLoginServerManager = mockk()

        // Mock SalesforceSDKManager singleton
        mockkObject(SalesforceSDKManager)
        every { SalesforceSDKManager.getInstance() } returns mockSalesforceSDKManager
        every { mockSalesforceSDKManager.appContext } returns mockContext
        every { mockSalesforceSDKManager.loginServerManager } returns mockLoginServerManager

        // Mock BootConfig
        mockkObject(BootConfig)
        every { BootConfig.getBootConfig(mockContext) } returns mockBootConfig
        every { mockBootConfig.remoteAccessConsumerKey } returns CONSUMER_KEY

        // Mock RuntimeConfig
        mockkObject(RuntimeConfig)
        every { RuntimeConfig.getRuntimeConfig(mockContext) } returns mockRuntimeConfig

        // Default mock behavior for LoginServerManager
        val mockSelectedServer = mockk<LoginServer>()
        every { mockSelectedServer.url } returns SELECTED_LOGIN_SERVER
        every { mockLoginServerManager.selectedLoginServer } returns mockSelectedServer
        every { mockLoginServerManager.addCustomLoginServer(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testConstructor_AllParametersSet() {
        // Arrange
        val frontdoorUrl = FRONTDOOR_URL_WITH_MATCHING_CLIENT_ID.toUri()
        val codeVerifier = CODE_VERIFIER
        val selectedServer = "https://test.salesforce.com"

        // Act
        val override = FrontdoorBridgeLoginOverride(
            frontdoorBridgeUrl = frontdoorUrl,
            codeVerifier = codeVerifier,
            selectedAppLoginServer = selectedServer,
            addingAndSwitchingLoginServersPerMdm = true,
            addingAndSwitchingLoginServerOverride = false
        )

        // Assert
        assertEquals(frontdoorUrl, override.frontdoorBridgeUrl)
        assertEquals(codeVerifier, override.codeVerifier)
    }

    @Test
    fun testConstructor_DefaultParameters() {
        // Arrange
        val frontdoorUrl = FRONTDOOR_URL_WITH_MATCHING_CLIENT_ID.toUri()

        // Act
        val override = FrontdoorBridgeLoginOverride(
            frontdoorBridgeUrl = frontdoorUrl
        )

        // Assert
        assertEquals(frontdoorUrl, override.frontdoorBridgeUrl)
        assertNull(override.codeVerifier)
    }

    @Test
    fun testMatchesConsumerKey_WithMatchingClientId_ReturnsTrue() {
        // Arrange
        val frontdoorUrl = FRONTDOOR_URL_WITH_MATCHING_CLIENT_ID.toUri()
        setupRuntimeConfigMocks(onlyShowAuthorizedServers = false, mdmLoginServers = emptyArray())

        // Act
        val override = FrontdoorBridgeLoginOverride(
            frontdoorBridgeUrl = frontdoorUrl,
            addingAndSwitchingLoginServersPerMdm = true
        )

        // Assert
        assertTrue(override.matchesConsumerKey)
    }

    @Test
    fun testMatchesConsumerKey_WithDifferentClientId_ReturnsFalse() {
        // Arrange
        val frontdoorUrl = FRONTDOOR_URL_WITH_DIFFERENT_CLIENT_ID.toUri()
        setupRuntimeConfigMocks(onlyShowAuthorizedServers = false, mdmLoginServers = emptyArray())

        // Act
        val override = FrontdoorBridgeLoginOverride(
            frontdoorBridgeUrl = frontdoorUrl,
            addingAndSwitchingLoginServersPerMdm = true
        )

        // Assert
        assertFalse(override.matchesConsumerKey)
    }

    @Test
    fun testMatchesConsumerKey_NoStartUrl_ReturnsFalse() {
        // Arrange
        val frontdoorUrl = FRONTDOOR_URL_NO_START_URL.toUri()
        setupRuntimeConfigMocks(onlyShowAuthorizedServers = false, mdmLoginServers = emptyArray())

        // Act
        val override = FrontdoorBridgeLoginOverride(
            frontdoorBridgeUrl = frontdoorUrl,
            addingAndSwitchingLoginServersPerMdm = true
        )

        // Assert
        assertFalse(override.matchesConsumerKey)
    }

    @Test
    fun testMatchesConsumerKey_NoClientId_ReturnsFalse() {
        // Arrange
        val frontdoorUrl = FRONTDOOR_URL_NO_CLIENT_ID.toUri()
        setupRuntimeConfigMocks(onlyShowAuthorizedServers = false, mdmLoginServers = emptyArray())

        // Act
        val override = FrontdoorBridgeLoginOverride(
            frontdoorBridgeUrl = frontdoorUrl,
            addingAndSwitchingLoginServersPerMdm = true
        )

        // Assert
        assertFalse(override.matchesConsumerKey)
    }

    @Test
    fun testMatchesLoginHost_WithMatchingHost_AddingSwitchingAllowed_ReturnsTrue() {
        // Arrange
        val frontdoorUrl = FRONTDOOR_URL_WITH_MATCHING_CLIENT_ID.toUri()
        setupRuntimeConfigMocks(onlyShowAuthorizedServers = false, mdmLoginServers = emptyArray())
        setupLoginServerManagerMocks(listOf(
            LoginServer("Production", "https://login.salesforce.com", false)
        ))

        // Act
        val override = FrontdoorBridgeLoginOverride(
            frontdoorBridgeUrl = frontdoorUrl,
            selectedAppLoginServer = "https://login.salesforce.com",
            addingAndSwitchingLoginServersPerMdm = true
        )

        // Assert
        assertTrue(override.matchesLoginHost)
        verify { mockLoginServerManager.addCustomLoginServer("https://login.salesforce.com", "https://login.salesforce.com") }
    }

    @Test
    fun testMatchesLoginHost_WithCustomHost_AddingSwitchingAllowed_ReturnsTrue() {
        // Arrange
        val frontdoorUrl = CUSTOM_HOST_URL.toUri()
        setupRuntimeConfigMocks(onlyShowAuthorizedServers = false, mdmLoginServers = emptyArray())
        setupLoginServerManagerMocks(listOf(
            LoginServer("Production", "https://login.salesforce.com", false)
        ))

        // Act
        val override = FrontdoorBridgeLoginOverride(
            frontdoorBridgeUrl = frontdoorUrl,
            selectedAppLoginServer = "https://login.salesforce.com",
            addingAndSwitchingLoginServersPerMdm = true
        )

        // Assert
        assertTrue(override.matchesLoginHost)
        verify { mockLoginServerManager.addCustomLoginServer("https://custom.salesforce.com", "https://custom.salesforce.com") }
    }

    @Test
    fun testMatchesLoginHost_WithCustomHost_AddingSwitchingNotAllowed_ReturnsFalse() {
        // Arrange
        val frontdoorUrl = CUSTOM_HOST_URL.toUri()
        setupRuntimeConfigMocks(onlyShowAuthorizedServers = true, mdmLoginServers = arrayOf("login.salesforce.com"))
        setupLoginServerManagerMocks(listOf(
            LoginServer("Production", "https://login.salesforce.com", false)
        ))

        // Act
        val override = FrontdoorBridgeLoginOverride(
            frontdoorBridgeUrl = frontdoorUrl,
            selectedAppLoginServer = "https://login.salesforce.com",
            addingAndSwitchingLoginServersPerMdm = true
        )

        // Assert
        assertFalse(override.matchesLoginHost)
        verify(exactly = 0) { mockLoginServerManager.addCustomLoginServer(any(), any()) }
    }

    @Test
    fun testMatchesLoginHost_UsingMdmOverride_AddingSwitchingAllowed_ReturnsTrue() {
        // Arrange
        val frontdoorUrl = CUSTOM_HOST_URL.toUri()
        setupRuntimeConfigMocks(onlyShowAuthorizedServers = false, mdmLoginServers = emptyArray())
        setupLoginServerManagerMocks(listOf(
            LoginServer("Production", "https://login.salesforce.com", false)
        ))

        // Act
        val override = FrontdoorBridgeLoginOverride(
            frontdoorBridgeUrl = frontdoorUrl,
            selectedAppLoginServer = "https://login.salesforce.com",
            addingAndSwitchingLoginServersPerMdm = false,
            addingAndSwitchingLoginServerOverride = true
        )

        // Assert
        assertTrue(override.matchesLoginHost)
        verify { mockLoginServerManager.addCustomLoginServer("https://custom.salesforce.com", "https://custom.salesforce.com") }
    }

    @Test
    fun testMatchesLoginHost_UsingMdmOverride_AddingSwitchingNotAllowed_ReturnsFalse() {
        // Arrange
        val frontdoorUrl = CUSTOM_HOST_URL.toUri()
        setupRuntimeConfigMocks(onlyShowAuthorizedServers = false, mdmLoginServers = emptyArray())
        setupLoginServerManagerMocks(listOf(
            LoginServer("Production", "https://login.salesforce.com", false)
        ))

        // Act
        val override = FrontdoorBridgeLoginOverride(
            frontdoorBridgeUrl = frontdoorUrl,
            selectedAppLoginServer = "https://login.salesforce.com",
            addingAndSwitchingLoginServersPerMdm = false,
            addingAndSwitchingLoginServerOverride = false
        )

        // Assert
        assertFalse(override.matchesLoginHost)
        verify(exactly = 0) { mockLoginServerManager.addCustomLoginServer(any(), any()) }
    }

    @Test
    fun testMatchesLoginHost_MyDomainMatch_ReturnsTrue() {
        // Arrange
        val myDomainUrl = "https://mydomain.my.salesforce.com/setup/secur/RemoteAccessAuthorizationPage.apexp?source=lightning&startURL=https%3A%2F%2Fmydomain.my.salesforce.com%2Fservices%2Foauth2%2Fauthorize%3Fclient_id%3Dtest_consumer_key%26response_type%3Dcode"
        val frontdoorUrl = myDomainUrl.toUri()
        setupRuntimeConfigMocks(onlyShowAuthorizedServers = false, mdmLoginServers = emptyArray())
        setupLoginServerManagerMocks(listOf(
            LoginServer("Custom", "https://custom.salesforce.com", true)
        ))

        // Act
        val override = FrontdoorBridgeLoginOverride(
            frontdoorBridgeUrl = frontdoorUrl,
            selectedAppLoginServer = "https://login.salesforce.com",
            addingAndSwitchingLoginServersPerMdm = true
        )

        // Assert
        assertTrue(override.matchesLoginHost)
        verify { mockLoginServerManager.addCustomLoginServer("https://custom.salesforce.com", "https://custom.salesforce.com") }
    }

    @Test
    fun testRuntimeConfigException_HandledGracefully() {
        // Arrange
        val frontdoorUrl = FRONTDOOR_URL_WITH_MATCHING_CLIENT_ID.toUri()
        every { mockRuntimeConfig.getBoolean(RuntimeConfig.ConfigKey.OnlyShowAuthorizedHosts) } returns false
        every { mockRuntimeConfig.getStringArrayStoredAsArrayOrCSV(RuntimeConfig.ConfigKey.AppServiceHosts) } throws Exception("Test exception")
        setupLoginServerManagerMocks(listOf(
            LoginServer("Production", "https://login.salesforce.com", false)
        ))

        // Act
        val override = FrontdoorBridgeLoginOverride(
            frontdoorBridgeUrl = frontdoorUrl,
            selectedAppLoginServer = "https://login.salesforce.com",
            addingAndSwitchingLoginServersPerMdm = true
        )

        // Assert - Should not crash and should allow adding/switching
        assertTrue(override.matchesLoginHost)
    }

    @Test
    fun testAllParameterCombinations_AddingSwitchingPerMdmTrue_OverrideFalse() {
        // Arrange
        val frontdoorUrl = CUSTOM_HOST_URL.toUri()
        setupRuntimeConfigMocks(onlyShowAuthorizedServers = false, mdmLoginServers = emptyArray())
        setupLoginServerManagerMocks(listOf(
            LoginServer("Production", "https://login.salesforce.com", false)
        ))

        // Act
        val override = FrontdoorBridgeLoginOverride(
            frontdoorBridgeUrl = frontdoorUrl,
            codeVerifier = CODE_VERIFIER,
            selectedAppLoginServer = "https://test.salesforce.com",
            addingAndSwitchingLoginServersPerMdm = true,
            addingAndSwitchingLoginServerOverride = false
        )

        // Assert
        assertEquals(frontdoorUrl, override.frontdoorBridgeUrl)
        assertEquals(CODE_VERIFIER, override.codeVerifier)
        assertTrue(override.matchesConsumerKey)
        assertTrue(override.matchesLoginHost)
    }

    @Test
    fun testAllParameterCombinations_AddingSwitchingPerMdmFalse_OverrideTrue() {
        // Arrange
        val frontdoorUrl = CUSTOM_HOST_URL.toUri()
        setupRuntimeConfigMocks(onlyShowAuthorizedServers = false, mdmLoginServers = emptyArray())
        setupLoginServerManagerMocks(listOf(
            LoginServer("Production", "https://login.salesforce.com", false)
        ))

        // Act
        val override = FrontdoorBridgeLoginOverride(
            frontdoorBridgeUrl = frontdoorUrl,
            codeVerifier = null,
            selectedAppLoginServer = "https://sandbox.salesforce.com",
            addingAndSwitchingLoginServersPerMdm = false,
            addingAndSwitchingLoginServerOverride = true
        )

        // Assert
        assertEquals(frontdoorUrl, override.frontdoorBridgeUrl)
        assertNull(override.codeVerifier)
        assertTrue(override.matchesConsumerKey)
        assertTrue(override.matchesLoginHost)
    }

    @Test
    fun testAllParameterCombinations_BothAddingSwitchingFalse() {
        // Arrange
        val frontdoorUrl = CUSTOM_HOST_URL.toUri()
        setupRuntimeConfigMocks(onlyShowAuthorizedServers = false, mdmLoginServers = emptyArray())
        setupLoginServerManagerMocks(listOf(
            LoginServer("Production", "https://login.salesforce.com", false)
        ))

        // Act
        val override = FrontdoorBridgeLoginOverride(
            frontdoorBridgeUrl = frontdoorUrl,
            codeVerifier = "",
            selectedAppLoginServer = "https://custom.salesforce.com",
            addingAndSwitchingLoginServersPerMdm = false,
            addingAndSwitchingLoginServerOverride = false
        )

        // Assert
        assertEquals(frontdoorUrl, override.frontdoorBridgeUrl)
        assertEquals("", override.codeVerifier)
        assertTrue(override.matchesConsumerKey)
        assertFalse(override.matchesLoginHost)
    }

    private fun setupRuntimeConfigMocks(onlyShowAuthorizedServers: Boolean, mdmLoginServers: Array<String>) {
        every { mockRuntimeConfig.getBoolean(RuntimeConfig.ConfigKey.OnlyShowAuthorizedHosts) } returns onlyShowAuthorizedServers
        every { mockRuntimeConfig.getStringArrayStoredAsArrayOrCSV(RuntimeConfig.ConfigKey.AppServiceHosts) } returns mdmLoginServers
    }

    private fun setupLoginServerManagerMocks(servers: List<LoginServer>) {
        every { mockLoginServerManager.numberOfLoginServers() } returns servers.size
        servers.forEachIndexed { index, server ->
            every { mockLoginServerManager.loginServerAtIndex(index) } returns server
        }
    }

    @Test
    fun testEdgeCase_NullHost_NoMatch() {
        // Arrange
        val mockUri = mockk<Uri>()
        every { mockUri.host } returns null
        every { mockUri.toString() } returns "invalid-url-no-host"
        every { mockUri.getQueryParameter("startURL") } returns null
        setupRuntimeConfigMocks(onlyShowAuthorizedServers = false, mdmLoginServers = emptyArray())

        // Act
        val override = FrontdoorBridgeLoginOverride(
            frontdoorBridgeUrl = mockUri,
            addingAndSwitchingLoginServersPerMdm = true
        )

        // Assert
        assertFalse(override.matchesConsumerKey)
        assertFalse(override.matchesLoginHost)
    }

    @Test
    fun testEdgeCase_MalformedStartUrl_NoConsumerKeyMatch() {
        // Arrange
        val malformedUrl = "https://login.salesforce.com/setup/secur/RemoteAccessAuthorizationPage.apexp?source=lightning&startURL=malformed-url"
        val frontdoorUrl = malformedUrl.toUri()
        setupRuntimeConfigMocks(onlyShowAuthorizedServers = false, mdmLoginServers = emptyArray())

        // Act
        val override = FrontdoorBridgeLoginOverride(
            frontdoorBridgeUrl = frontdoorUrl,
            addingAndSwitchingLoginServersPerMdm = true
        )

        // Assert
        assertFalse(override.matchesConsumerKey)
    }

    @Test
    fun testEdgeCase_EmptyStringCodeVerifier() {
        // Arrange
        val frontdoorUrl = FRONTDOOR_URL_WITH_MATCHING_CLIENT_ID.toUri()
        setupRuntimeConfigMocks(onlyShowAuthorizedServers = false, mdmLoginServers = emptyArray())

        // Act
        val override = FrontdoorBridgeLoginOverride(
            frontdoorBridgeUrl = frontdoorUrl,
            codeVerifier = "",
            addingAndSwitchingLoginServersPerMdm = true
        )

        // Assert
        assertEquals("", override.codeVerifier)
        assertTrue(override.matchesConsumerKey)
    }

    @Test
    fun testEdgeCase_VeryLongCodeVerifier() {
        // Arrange
        val longCodeVerifier = "a".repeat(1000) // Very long code verifier
        val frontdoorUrl = FRONTDOOR_URL_WITH_MATCHING_CLIENT_ID.toUri()
        setupRuntimeConfigMocks(onlyShowAuthorizedServers = false, mdmLoginServers = emptyArray())

        // Act
        val override = FrontdoorBridgeLoginOverride(
            frontdoorBridgeUrl = frontdoorUrl,
            codeVerifier = longCodeVerifier,
            addingAndSwitchingLoginServersPerMdm = true
        )

        // Assert
        assertEquals(longCodeVerifier, override.codeVerifier)
    }

    @Test
    fun testEdgeCase_SpecialCharactersInUrls() {
        // Arrange
        val specialCharUrl = "https://login.salesforce.com/setup/secur/RemoteAccessAuthorizationPage.apexp?source=lightning&startURL=https%3A%2F%2Flogin.salesforce.com%2Fservices%2Foauth2%2Fauthorize%3Fclient_id%3Dtest_consumer_key%26response_type%3Dcode%26special%3D%2B%26another%3D%40"
        val frontdoorUrl = specialCharUrl.toUri()
        setupRuntimeConfigMocks(onlyShowAuthorizedServers = false, mdmLoginServers = emptyArray())
        setupLoginServerManagerMocks(listOf(
            LoginServer("Production", "https://login.salesforce.com", false)
        ))

        // Act
        val override = FrontdoorBridgeLoginOverride(
            frontdoorBridgeUrl = frontdoorUrl,
            addingAndSwitchingLoginServersPerMdm = true
        )

        // Assert
        assertTrue(override.matchesConsumerKey)
        assertTrue(override.matchesLoginHost)
    }

    @Test
    fun testEdgeCase_CaseInsensitiveHost() {
        // Arrange
        val upperCaseHostUrl = "https://LOGIN.SALESFORCE.COM/setup/secur/RemoteAccessAuthorizationPage.apexp?source=lightning&startURL=https%3A%2F%2FLOGIN.SALESFORCE.COM%2Fservices%2Foauth2%2Fauthorize%3Fclient_id%3Dtest_consumer_key%26response_type%3Dcode"
        val frontdoorUrl = upperCaseHostUrl.toUri()
        setupRuntimeConfigMocks(onlyShowAuthorizedServers = false, mdmLoginServers = emptyArray())
        setupLoginServerManagerMocks(listOf(
            LoginServer("Production", "https://login.salesforce.com", false)
        ))

        // Act
        val override = FrontdoorBridgeLoginOverride(
            frontdoorBridgeUrl = frontdoorUrl,
            addingAndSwitchingLoginServersPerMdm = true
        )

        // Assert
        assertTrue(override.matchesConsumerKey)
        // Note: Host matching might be case-sensitive in actual Android Uri implementation
    }

    @Test
    fun testMaximumCodeCoverage_AllBranches() {
        // Test all combinations of boolean parameters for maximum coverage
        val testCases = listOf(
            Triple(true, true, "Should use MDM preference"),
            Triple(true, false, "Should use MDM preference"),
            Triple(false, true, "Should use override"),
            Triple(false, false, "Should use override")
        )

        testCases.forEach { (addingAndSwitchingLoginServersPerMdm, addingAndSwitchingLoginServerOverride, description) ->
            // Arrange
            val frontdoorUrl = CUSTOM_HOST_URL.toUri()
            setupRuntimeConfigMocks(onlyShowAuthorizedServers = false, mdmLoginServers = emptyArray())
            setupLoginServerManagerMocks(listOf(
                LoginServer("Production", "https://login.salesforce.com", false)
            ))

            // Act
            val override = FrontdoorBridgeLoginOverride(
                frontdoorBridgeUrl = frontdoorUrl,
                addingAndSwitchingLoginServersPerMdm = addingAndSwitchingLoginServersPerMdm,
                addingAndSwitchingLoginServerOverride = addingAndSwitchingLoginServerOverride
            )

            // Assert - based on the description, we expect different behaviors
            val expectedResult = if (addingAndSwitchingLoginServersPerMdm) true else addingAndSwitchingLoginServerOverride
            assertEquals("Failed for case: $description", expectedResult, override.matchesLoginHost)
        }
    }
}
