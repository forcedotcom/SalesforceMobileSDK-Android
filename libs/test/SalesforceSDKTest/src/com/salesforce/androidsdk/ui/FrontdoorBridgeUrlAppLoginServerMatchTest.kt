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

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.config.LoginServerManager.LoginServer
import com.salesforce.androidsdk.config.LoginServerManaging
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for appLoginServerForFrontdoorBridgeUrl
 * TODO: Could this be re-written to look like `ParentChildrenOtherSyncTest`? ECJ20250911
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class FrontdoorBridgeUrlAppLoginServerMatchTest {

    companion object {
        private const val PRODUCTION_URL = "https://login.salesforce.com"
        private const val SANDBOX_URL = "https://test.salesforce.com"
        private const val MYDOMAIN_URL = "https://mydomain.my.salesforce.com"
    }

    @Test
    fun testAppLoginServerMatch_NullHost_ReturnsNull() {
        // Arrange
        val uri = "invalid-url-no-host".toUri() // This will have a null host
        val mockLoginServerManaging = mockk<LoginServerManaging>()

        // Act
        val match = appLoginServerForFrontdoorBridgeUrl(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = PRODUCTION_URL
        )

        // Assert
        assertNull(match)
    }

    @Test
    fun testAppLoginServerMatch_ExactHostMatch_AddingSwitchingAllowed_ReturnsMatch() {
        // Arrange
        val uri = PRODUCTION_URL.toUri()

        val mockLoginServer = LoginServer("Production", PRODUCTION_URL, false)

        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.loginServers } returns listOf(
            mockLoginServer,
            LoginServer("Sandbox", SANDBOX_URL, false)
        )

        // Act
        val match = appLoginServerForFrontdoorBridgeUrl(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = "test.salesforce.com"
        )

        // Assert
        assertEquals("login.salesforce.com", match)
    }

    @Test
    fun testAppLoginServerMatch_ExactHostMatch_AddingSwitchingNotAllowed_ReturnsMatch() {
        // Arrange
        val uri = PRODUCTION_URL.toUri()

        val mockLoginServerManaging = mockk<LoginServerManaging>()

        // Act
        val match = appLoginServerForFrontdoorBridgeUrl(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = false,
            selectedAppLoginServer = "login.salesforce.com"
        )

        // Assert
        assertEquals("login.salesforce.com", match)
    }

    @Test
    fun testAppLoginServerMatch_NoExactMatch_AddingSwitchingNotAllowed_ReturnsNull() {
        // Arrange
        val uri = "https://custom.salesforce.com".toUri()

        val mockLoginServerManaging = mockk<LoginServerManaging>()

        // Act
        val match = appLoginServerForFrontdoorBridgeUrl(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = false,
            selectedAppLoginServer = "login.salesforce.com"
        )

        // Assert
        assertNull(match)
    }

    @Test
    fun testAppLoginServerMatch_MyDomainMatch_ReturnsMatch() {
        // Arrange
        val uri = MYDOMAIN_URL.toUri()

        val mockLoginServer1 = LoginServer("Production", PRODUCTION_URL, false)

        val mockLoginServer2 = LoginServer("Custom", "https://login.my.salesforce.com", true)

        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.loginServers } returns listOf(
            mockLoginServer1,
            mockLoginServer2
        )

        // Act
        val match = appLoginServerForFrontdoorBridgeUrl(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = "test.salesforce.com"
        )

        // Assert
        assertEquals("login.my.salesforce.com", match)
    }

    @Test
    fun testAppLoginServerMatch_MyDomainNoMatch_ReturnsNull() {
        // Arrange
        val uri = MYDOMAIN_URL.toUri()

        val mockLoginServer = LoginServer("Different", "https://different.example.com", true)

        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.loginServers } returns listOf(mockLoginServer)

        // Act
        val match = appLoginServerForFrontdoorBridgeUrl(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = "test.salesforce.com"
        )

        // Assert
        assertNull(match)
    }

    @Test
    fun testAppLoginServerMatch_NotMyDomain_NoExactMatch_ReturnsNull() {
        // Arrange
        val uri = "https://custom.example.com".toUri()

        val mockLoginServer = LoginServer("Production", PRODUCTION_URL, false)

        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.loginServers } returns listOf(mockLoginServer)

        // Act
        val match = appLoginServerForFrontdoorBridgeUrl(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = "test.salesforce.com"
        )

        // Assert
        assertNull(match)
    }

    @Test
    fun testAppLoginServerMatch_EmptyLoginServers_AddingSwitchingAllowed_ReturnsNull() {
        // Arrange
        val uri = PRODUCTION_URL.toUri()

        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.loginServers } returns listOf()

        // Act
        val match = appLoginServerForFrontdoorBridgeUrl(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = "test.salesforce.com"
        )

        // Assert
        assertNull(match)
    }

    @Test
    fun testAppLoginServerMatch_InvalidUrlInLoginServer_SkipsInvalidUrl() {
        // Arrange
        val uri = PRODUCTION_URL.toUri()

        val mockInvalidLoginServer = LoginServer("Invalid", "invalid-url", true)

        val mockValidLoginServer = LoginServer("Production", PRODUCTION_URL, false)

        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.loginServers } returns listOf(
            mockInvalidLoginServer,
            mockValidLoginServer
        )

        // Act
        val match = appLoginServerForFrontdoorBridgeUrl(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = "test.salesforce.com"
        )

        // Assert
        assertEquals("login.salesforce.com", match)
    }

    @Test
    fun testAppLoginServerMatch_MyDomainEmptySuffix_ReturnsNull() {
        // Arrange
        val uri = "https://mydomain.my.".toUri()

        val mockLoginServer = LoginServer("Production", PRODUCTION_URL, false)

        val mockLoginServerManaging = mockk<LoginServerManaging>(relaxed = true)
        every { mockLoginServerManaging.loginServers } returns listOf(mockLoginServer)

        // Act
        val match = appLoginServerForFrontdoorBridgeUrl(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = "test.salesforce.com"
        )

        // Assert
        assertNull(match)
    }

    @Test
    fun testAppLoginServerMatch_MultipleServers_ReturnsFirstMatch() {
        // Arrange
        val uri = PRODUCTION_URL.toUri()

        val mockLoginServer1 = LoginServer("Production1", PRODUCTION_URL, false)

        val mockLoginServer2 = LoginServer("Production2", PRODUCTION_URL, false) // Same host, should return first match

        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.loginServers } returns listOf(
            mockLoginServer1,
            mockLoginServer2
        )

        // Act
        val match = appLoginServerForFrontdoorBridgeUrl(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = "test.salesforce.com"
        )

        // Assert
        assertEquals("login.salesforce.com", match)
    }

    @Test
    fun testAppLoginServerMatch_LazyInitialization() {
        // Arrange
        val uri = PRODUCTION_URL.toUri()

        val mockLoginServer = LoginServer("Production", PRODUCTION_URL, false)

        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.loginServers } returns listOf(mockLoginServer)

        // Act
        val match = appLoginServerForFrontdoorBridgeUrl(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = "test.salesforce.com"
        )

        // First access
        val firstResult = match
        // Second access (should use cached value)
        val secondResult = match

        // Assert
        assertEquals("login.salesforce.com", firstResult)
        assertEquals("login.salesforce.com", secondResult)
        assertEquals(firstResult, secondResult)

        // Verify lazy initialization - should only call methods once
        verify(exactly = 1) { mockLoginServerManaging.loginServers }
    }

    @Test
    fun testAppLoginServerMatch_ComplexMyDomainScenario() {
        // Arrange - testing complex MyDomain matching with multiple servers
        val uri = "https://company.my.salesforce.com".toUri()

        val mockLoginServer1 = LoginServer("Production", "https://login.salesforce.com", false)

        val mockLoginServer2 = LoginServer("Sandbox", "https://test.my.salesforce.com", false)

        val mockLoginServer3 = LoginServer("Custom", "https://custom.my.salesforce.com", true)

        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.loginServers } returns listOf(
            mockLoginServer1,
            mockLoginServer2,
            mockLoginServer3
        )

        // Act
        val match = appLoginServerForFrontdoorBridgeUrl(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = "different.salesforce.com"
        )

        // Assert - Should match the first server ending with "my.salesforce.com"
        assertEquals("test.my.salesforce.com", match)
    }

    @Test
    fun testAppLoginServerMatch_EdgeCase_EmptyHostAfterSplit() {
        // Arrange - testing edge case where split results in empty suffix
        val uri = "https://company.my.".toUri()

        val mockLoginServer = LoginServer("Salesforce", "https://salesforce.com", false)

        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.loginServers } returns listOf(mockLoginServer)

        // Act
        val match = appLoginServerForFrontdoorBridgeUrl(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = "test.salesforce.com"
        )

        // Assert - Should return null because suffix is empty
        assertNull(match)
    }
}
