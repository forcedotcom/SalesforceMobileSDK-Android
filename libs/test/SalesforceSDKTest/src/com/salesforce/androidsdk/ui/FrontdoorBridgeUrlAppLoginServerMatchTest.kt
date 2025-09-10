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

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.config.LoginServerManaging
import com.salesforce.androidsdk.config.LoginServerManager.LoginServer
import io.mockk.every
import io.mockk.mockk
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
        private const val CUSTOM_URL = "https://custom.my.salesforce.com"
        private const val MYDOMAIN_URL = "https://mydomain.my.salesforce.com"
        private const val MYDOMAIN_SUFFIX = "salesforce.com"
        private const val INVALID_URL = "https://invalid.example.com"
    }

    @Test
    fun testAppLoginServerMatch_NullHost_ReturnsNull() {
        // Arrange
        val mockUri = mockk<Uri>()
        every { mockUri.host } returns null
        val mockLoginServerManaging = mockk<LoginServerManaging>()
        
        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = mockUri,
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
        val mockUri = mockk<Uri>()
        every { mockUri.host } returns "login.salesforce.com"
        every { mockUri.toString() } returns PRODUCTION_URL
        
        val mockLoginServer = mockk<LoginServer>()
        every { mockLoginServer.url } returns PRODUCTION_URL
        
        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.numberOfLoginServers() } returns 2
        every { mockLoginServerManaging.loginServerAtIndex(0) } returns mockLoginServer
        every { mockLoginServerManaging.loginServerAtIndex(1) } returns mockk<LoginServer>().apply {
            every { url } returns SANDBOX_URL
        }
        
        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = mockUri,
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
        val mockUri = mockk<Uri>()
        every { mockUri.host } returns "login.salesforce.com"
        every { mockUri.toString() } returns PRODUCTION_URL
        
        val mockLoginServerManaging = mockk<LoginServerManaging>()
        
        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = mockUri,
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
        val mockUri = mockk<Uri>()
        every { mockUri.host } returns "custom.salesforce.com"
        every { mockUri.toString() } returns "https://custom.salesforce.com"
        
        val mockLoginServerManaging = mockk<LoginServerManaging>()
        
        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = mockUri,
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
        val mockUri = mockk<Uri>()
        every { mockUri.host } returns "mydomain.my.salesforce.com"
        every { mockUri.toString() } returns MYDOMAIN_URL
        
        val mockLoginServer1 = mockk<LoginServer>()
        every { mockLoginServer1.url } returns PRODUCTION_URL
        
        val mockLoginServer2 = mockk<LoginServer>()
        every { mockLoginServer2.url } returns "https://custom.salesforce.com"
        
        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.numberOfLoginServers() } returns 2
        every { mockLoginServerManaging.loginServerAtIndex(0) } returns mockLoginServer1
        every { mockLoginServerManaging.loginServerAtIndex(1) } returns mockLoginServer2
        
        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = mockUri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = "test.salesforce.com"
        )
        
        // Assert
        assertEquals("custom.salesforce.com", match.appLoginServerMatch)
    }

    @Test
    fun testAppLoginServerMatch_MyDomainNoMatch_ReturnsNull() {
        // Arrange
        val mockUri = mockk<Uri>()
        every { mockUri.host } returns "mydomain.my.salesforce.com"
        every { mockUri.toString() } returns MYDOMAIN_URL
        
        val mockLoginServer = mockk<LoginServer>()
        every { mockLoginServer.url } returns "https://different.example.com"
        
        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.numberOfLoginServers() } returns 1
        every { mockLoginServerManaging.loginServerAtIndex(0) } returns mockLoginServer
        
        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = mockUri,
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
        val mockUri = mockk<Uri>()
        every { mockUri.host } returns "custom.example.com"
        every { mockUri.toString() } returns "https://custom.example.com"
        
        val mockLoginServer = mockk<LoginServer>()
        every { mockLoginServer.url } returns PRODUCTION_URL
        
        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.numberOfLoginServers() } returns 1
        every { mockLoginServerManaging.loginServerAtIndex(0) } returns mockLoginServer
        
        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = mockUri,
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
        val mockUri = mockk<Uri>()
        every { mockUri.host } returns "login.salesforce.com"
        every { mockUri.toString() } returns PRODUCTION_URL
        
        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.numberOfLoginServers() } returns 0
        
        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = mockUri,
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
        val mockUri = mockk<Uri>()
        every { mockUri.host } returns "login.salesforce.com"
        every { mockUri.toString() } returns PRODUCTION_URL
        
        val mockInvalidLoginServer = mockk<LoginServer>()
        every { mockInvalidLoginServer.url } returns "invalid-url"
        
        val mockValidLoginServer = mockk<LoginServer>()
        every { mockValidLoginServer.url } returns PRODUCTION_URL
        
        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.numberOfLoginServers() } returns 2
        every { mockLoginServerManaging.loginServerAtIndex(0) } returns mockInvalidLoginServer
        every { mockLoginServerManaging.loginServerAtIndex(1) } returns mockValidLoginServer
        
        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = mockUri,
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
        val mockUri = mockk<Uri>()
        every { mockUri.host } returns "mydomain.my."
        every { mockUri.toString() } returns "https://mydomain.my."
        
        val mockLoginServer = mockk<LoginServer>()
        every { mockLoginServer.url } returns PRODUCTION_URL
        
        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.numberOfLoginServers() } returns 1
        every { mockLoginServerManaging.loginServerAtIndex(0) } returns mockLoginServer
        
        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = mockUri,
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
        val mockUri = mockk<Uri>()
        every { mockUri.host } returns "login.salesforce.com"
        every { mockUri.toString() } returns PRODUCTION_URL
        
        val mockLoginServer1 = mockk<LoginServer>()
        every { mockLoginServer1.url } returns PRODUCTION_URL
        
        val mockLoginServer2 = mockk<LoginServer>()
        every { mockLoginServer2.url } returns PRODUCTION_URL // Same host, should return first match
        
        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.numberOfLoginServers() } returns 2
        every { mockLoginServerManaging.loginServerAtIndex(0) } returns mockLoginServer1
        every { mockLoginServerManaging.loginServerAtIndex(1) } returns mockLoginServer2
        
        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = mockUri,
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
        val mockUri = mockk<Uri>()
        every { mockUri.host } returns "login.salesforce.com"
        every { mockUri.toString() } returns PRODUCTION_URL
        
        val mockLoginServer = mockk<LoginServer>()
        every { mockLoginServer.url } returns PRODUCTION_URL
        
        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.numberOfLoginServers() } returns 2
        every { mockLoginServerManaging.loginServerAtIndex(0) } returns null
        every { mockLoginServerManaging.loginServerAtIndex(1) } returns mockLoginServer
        
        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = mockUri,
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
        val mockUri = mockk<Uri>()
        every { mockUri.host } returns "login.salesforce.com"
        every { mockUri.toString() } returns PRODUCTION_URL
        
        val mockLoginServerManaging = mockk<LoginServerManaging>()
        val selectedServer = "test.salesforce.com"
        val addingSwitchingAllowed = true
        
        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = mockUri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = addingSwitchingAllowed,
            selectedAppLoginServer = selectedServer
        )
        
        // Assert
        assertEquals(mockUri, match.frontdoorBridgeUrl)
        assertEquals(mockLoginServerManaging, match.loginServerManaging)
        assertEquals(addingSwitchingAllowed, match.addingAndSwitchingLoginServersAllowed)
        assertEquals(selectedServer, match.selectedAppLoginServer)
    }

    @Test
    fun testAppLoginServerMatch_LazyInitialization() {
        // Arrange
        val mockUri = mockk<Uri>()
        every { mockUri.host } returns "login.salesforce.com"
        every { mockUri.toString() } returns PRODUCTION_URL
        
        val mockLoginServer = mockk<LoginServer>()
        every { mockLoginServer.url } returns PRODUCTION_URL
        
        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.numberOfLoginServers() } returns 1
        every { mockLoginServerManaging.loginServerAtIndex(0) } returns mockLoginServer
        
        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = mockUri,
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
        val mockUri = mockk<Uri>()
        every { mockUri.host } returns "company.my.salesforce.com"
        every { mockUri.toString() } returns "https://company.my.salesforce.com"
        
        val mockLoginServer1 = mockk<LoginServer>()
        every { mockLoginServer1.url } returns "https://login.salesforce.com"
        
        val mockLoginServer2 = mockk<LoginServer>()
        every { mockLoginServer2.url } returns "https://test.salesforce.com"
        
        val mockLoginServer3 = mockk<LoginServer>()
        every { mockLoginServer3.url } returns "https://custom.salesforce.com"
        
        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.numberOfLoginServers() } returns 3
        every { mockLoginServerManaging.loginServerAtIndex(0) } returns mockLoginServer1
        every { mockLoginServerManaging.loginServerAtIndex(1) } returns mockLoginServer2
        every { mockLoginServerManaging.loginServerAtIndex(2) } returns mockLoginServer3
        
        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = mockUri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = "different.salesforce.com"
        )
        
        // Assert - Should match the first server ending with "salesforce.com"
        assertEquals("login.salesforce.com", match.appLoginServerMatch)
    }

    @Test
    fun testAppLoginServerMatch_EdgeCase_EmptyHostAfterSplit() {
        // Arrange - testing edge case where split results in empty suffix
        val mockUri = mockk<Uri>()
        every { mockUri.host } returns "company.my."
        every { mockUri.toString() } returns "https://company.my."
        
        val mockLoginServer = mockk<LoginServer>()
        every { mockLoginServer.url } returns "https://salesforce.com"
        
        val mockLoginServerManaging = mockk<LoginServerManaging>()
        every { mockLoginServerManaging.numberOfLoginServers() } returns 1
        every { mockLoginServerManaging.loginServerAtIndex(0) } returns mockLoginServer
        
        // Act
        val match = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = mockUri,
            loginServerManaging = mockLoginServerManaging,
            addingAndSwitchingLoginServersAllowed = true,
            selectedAppLoginServer = "test.salesforce.com"
        )
        
        // Assert - Should return null because suffix is empty
        assertNull(match.appLoginServerMatch)
    }
}
