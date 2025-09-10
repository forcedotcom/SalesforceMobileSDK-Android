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
 * Tests for FrontdoorBridgeUrlAppLoginServerMatch
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
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = PRODUCTION_URL
        )

        // Assert
        assertNull(match.appLoginServerMatch)
    }

    @Test
    fun testAppLoginServerMatch_ExactHostMatch_AddingSwitchingAllowed_ReturnsMatch() {
        // Arrange
        val uri = PRODUCTION_URL.toUri()

        val mockLoginServer = LoginServer("Production", PRODUCTION_URL, false)

        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.numberOfLoginServers() } returns 2
        every { mockLoginServerManaging.loginServerAtIndex(0) } returns mockLoginServer
        every { mockLoginServerManaging.loginServerAtIndex(1) } returns LoginServer("Sandbox", SANDBOX_URL, false)

        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = "test.salesforce.com"
        )

        // Assert
        assertEquals("login.salesforce.com", match.appLoginServerMatch)
    }

    @Test
    fun testAppLoginServerMatch_ExactHostMatch_AddingSwitchingNotAllowed_ReturnsMatch() {
        // Arrange
        val uri = PRODUCTION_URL.toUri()

        val mockLoginServerManaging = mockk<LoginServerManaging>()

        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = false,
            selectedAppLoginServer = "login.salesforce.com"
        )

        // Assert
        assertEquals("login.salesforce.com", match.appLoginServerMatch)
    }

    @Test
    fun testAppLoginServerMatch_NoExactMatch_AddingSwitchingNotAllowed_ReturnsNull() {
        // Arrange
        val uri = "https://custom.salesforce.com".toUri()

        val mockLoginServerManaging = mockk<LoginServerManaging>()

        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = false,
            selectedAppLoginServer = "login.salesforce.com"
        )

        // Assert
        assertNull(match.appLoginServerMatch)
    }

    @Test
    fun testAppLoginServerMatch_MyDomainMatch_ReturnsMatch() {
        // Arrange
        val uri = MYDOMAIN_URL.toUri()

        val mockLoginServer1 = LoginServer("Production", PRODUCTION_URL, false)

        val mockLoginServer2 = LoginServer("Custom", "https://login.my.salesforce.com", true)

        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.numberOfLoginServers() } returns 2
        every { mockLoginServerManaging.loginServerAtIndex(0) } returns mockLoginServer1
        every { mockLoginServerManaging.loginServerAtIndex(1) } returns mockLoginServer2

        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = "test.salesforce.com"
        )

        // Assert
        assertEquals("login.my.salesforce.com", match.appLoginServerMatch)
    }

    @Test
    fun testAppLoginServerMatch_MyDomainNoMatch_ReturnsNull() {
        // Arrange
        val uri = MYDOMAIN_URL.toUri()

        val mockLoginServer = LoginServer("Different", "https://different.example.com", true)

        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.numberOfLoginServers() } returns 1
        every { mockLoginServerManaging.loginServerAtIndex(0) } returns mockLoginServer

        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = "test.salesforce.com"
        )

        // Assert
        assertNull(match.appLoginServerMatch)
    }

    @Test
    fun testAppLoginServerMatch_NotMyDomain_NoExactMatch_ReturnsNull() {
        // Arrange
        val uri = "https://custom.example.com".toUri()

        val mockLoginServer = LoginServer("Production", PRODUCTION_URL, false)

        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.numberOfLoginServers() } returns 1
        every { mockLoginServerManaging.loginServerAtIndex(0) } returns mockLoginServer

        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = "test.salesforce.com"
        )

        // Assert
        assertNull(match.appLoginServerMatch)
    }

    @Test
    fun testAppLoginServerMatch_EmptyLoginServers_AddingSwitchingAllowed_ReturnsNull() {
        // Arrange
        val uri = PRODUCTION_URL.toUri()

        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.numberOfLoginServers() } returns 0

        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = "test.salesforce.com"
        )

        // Assert
        assertNull(match.appLoginServerMatch)
    }

    @Test
    fun testAppLoginServerMatch_InvalidUrlInLoginServer_SkipsInvalidUrl() {
        // Arrange
        val uri = PRODUCTION_URL.toUri()

        val mockInvalidLoginServer = LoginServer("Invalid", "invalid-url", true)

        val mockValidLoginServer = LoginServer("Production", PRODUCTION_URL, false)

        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.numberOfLoginServers() } returns 2
        every { mockLoginServerManaging.loginServerAtIndex(0) } returns mockInvalidLoginServer
        every { mockLoginServerManaging.loginServerAtIndex(1) } returns mockValidLoginServer

        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = "test.salesforce.com"
        )

        // Assert
        assertEquals("login.salesforce.com", match.appLoginServerMatch)
    }

    @Test
    fun testAppLoginServerMatch_MyDomainEmptySuffix_ReturnsNull() {
        // Arrange
        val uri = "https://mydomain.my.".toUri()

        val mockLoginServer = LoginServer("Production", PRODUCTION_URL, false)

        val mockLoginServerManaging = mockk<LoginServerManaging>(relaxed = true)
        every { mockLoginServerManaging.numberOfLoginServers() } returns 1
        every { mockLoginServerManaging.loginServerAtIndex(0) } returns mockLoginServer

        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = "test.salesforce.com"
        )

        // Assert
        assertNull(match.appLoginServerMatch)
    }

    @Test
    fun testAppLoginServerMatch_MultipleServers_ReturnsFirstMatch() {
        // Arrange
        val uri = PRODUCTION_URL.toUri()

        val mockLoginServer1 = LoginServer("Production1", PRODUCTION_URL, false)

        val mockLoginServer2 = LoginServer("Production2", PRODUCTION_URL, false) // Same host, should return first match

        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.numberOfLoginServers() } returns 2
        every { mockLoginServerManaging.loginServerAtIndex(0) } returns mockLoginServer1
        every { mockLoginServerManaging.loginServerAtIndex(1) } returns mockLoginServer2

        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = "test.salesforce.com"
        )

        // Assert
        assertEquals("login.salesforce.com", match.appLoginServerMatch)
    }

    @Test
    fun testAppLoginServerMatch_NullLoginServerAtIndex_SkipsNull() {
        // Arrange
        val uri = PRODUCTION_URL.toUri()

        val mockLoginServer = LoginServer("Production", PRODUCTION_URL, false)

        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.numberOfLoginServers() } returns 2
        every { mockLoginServerManaging.loginServerAtIndex(0) } returns null
        every { mockLoginServerManaging.loginServerAtIndex(1) } returns mockLoginServer

        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = "test.salesforce.com"
        )

        // Assert
        assertEquals("login.salesforce.com", match.appLoginServerMatch)
    }

    @Test
    fun testDataClassProperties_AllPropertiesAccessible() {
        // Arrange
        val uri = PRODUCTION_URL.toUri()

        val mockLoginServerManaging = mockk<LoginServerManaging>()
        val selectedServer = "test.salesforce.com"
        val addingSwitchingAllowed = true

        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = addingSwitchingAllowed,
            selectedAppLoginServer = selectedServer
        )

        // Assert
        assertEquals(uri, match.frontdoorBridgeUrl)
        assertEquals(mockLoginServerManaging, match.loginServerManaging)
        assertEquals(addingSwitchingAllowed, match.addingAndSwitchingLoginServersAllowed)
        assertEquals(selectedServer, match.selectedAppLoginServer)
    }

    @Test
    fun testAppLoginServerMatch_LazyInitialization() {
        // Arrange
        val uri = PRODUCTION_URL.toUri()

        val mockLoginServer = LoginServer("Production", PRODUCTION_URL, false)

        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.numberOfLoginServers() } returns 1
        every { mockLoginServerManaging.loginServerAtIndex(0) } returns mockLoginServer

        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = "test.salesforce.com"
        )

        // First access
        val firstResult = match.appLoginServerMatch
        // Second access (should use cached value)
        val secondResult = match.appLoginServerMatch

        // Assert
        assertEquals("login.salesforce.com", firstResult)
        assertEquals("login.salesforce.com", secondResult)
        assertEquals(firstResult, secondResult)

        // Verify lazy initialization - should only call methods once
        verify(exactly = 1) { mockLoginServerManaging.numberOfLoginServers() }
        verify(exactly = 1) { mockLoginServerManaging.loginServerAtIndex(0) }
    }

    @Test
    fun testAppLoginServerMatch_ComplexMyDomainScenario() {
        // Arrange - testing complex MyDomain matching with multiple servers
        val uri = "https://company.my.salesforce.com".toUri()

        val mockLoginServer1 = LoginServer("Production", "https://login.salesforce.com", false)

        val mockLoginServer2 = LoginServer("Sandbox", "https://test.my.salesforce.com", false)

        val mockLoginServer3 = LoginServer("Custom", "https://custom.my.salesforce.com", true)

        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.numberOfLoginServers() } returns 3
        every { mockLoginServerManaging.loginServerAtIndex(0) } returns mockLoginServer1
        every { mockLoginServerManaging.loginServerAtIndex(1) } returns mockLoginServer2
        every { mockLoginServerManaging.loginServerAtIndex(2) } returns mockLoginServer3

        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = "different.salesforce.com"
        )

        // Assert - Should match the first server ending with "my.salesforce.com"
        assertEquals("test.my.salesforce.com", match.appLoginServerMatch)
    }

    @Test
    fun testAppLoginServerMatch_EdgeCase_EmptyHostAfterSplit() {
        // Arrange - testing edge case where split results in empty suffix
        val uri = "https://company.my.".toUri()

        val mockLoginServer = LoginServer("Salesforce", "https://salesforce.com", false)

        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.numberOfLoginServers() } returns 1
        every { mockLoginServerManaging.loginServerAtIndex(0) } returns mockLoginServer

        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = uri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = "test.salesforce.com"
        )

        // Assert - Should return null because suffix is empty
        assertNull(match.appLoginServerMatch)
    }
}
