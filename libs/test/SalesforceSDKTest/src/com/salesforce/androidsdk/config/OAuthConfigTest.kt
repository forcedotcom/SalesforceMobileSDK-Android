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
package com.salesforce.androidsdk.config

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for OAuthConfig.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class OAuthConfigTest {

    @Test
    fun testPrimaryConstructorWithScopes() {
        val config = OAuthConfig(
            consumerKey = "test_consumer_key",
            redirectUri = "test://callback",
            scopes = listOf("api", "web", "refresh_token")
        )

        assertEquals("test_consumer_key", config.consumerKey)
        assertEquals("test://callback", config.redirectUri)
        assertEquals(listOf("api", "web", "refresh_token"), config.scopes)
        assertEquals("api web refresh_token", config.scopesString)
    }

    @Test
    fun testPrimaryConstructorWithoutScopes() {
        val config = OAuthConfig(
            consumerKey = "test_consumer_key",
            redirectUri = "test://callback"
        )

        assertEquals("test_consumer_key", config.consumerKey)
        assertEquals("test://callback", config.redirectUri)
        assertNull(config.scopes)
        assert(config.scopesString.isEmpty())
    }

    @Test
    fun testBootConfigConstructorWithScopes() {
        val bootConfig = mockk<BootConfig>()
        every { bootConfig.remoteAccessConsumerKey } returns "boot_consumer_key"
        every { bootConfig.oauthRedirectURI } returns "boot://redirect"
        every { bootConfig.oauthScopes } returns arrayOf("api", "web", "refresh_token")

        val config = OAuthConfig(bootConfig)

        assertEquals("boot_consumer_key", config.consumerKey)
        assertEquals("boot://redirect", config.redirectUri)
        assertEquals(listOf("api", "web", "refresh_token"), config.scopes)
        assertEquals("api web refresh_token", config.scopesString)
    }

    @Test
    fun testBootConfigConstructorWithEmptyScopes() {
        val bootConfig = mockk<BootConfig>()
        every { bootConfig.remoteAccessConsumerKey } returns "boot_consumer_key"
        every { bootConfig.oauthRedirectURI } returns "boot://redirect"
        every { bootConfig.oauthScopes } returns arrayOf()

        val config = OAuthConfig(bootConfig)

        assertEquals("boot_consumer_key", config.consumerKey)
        assertEquals("boot://redirect", config.redirectUri)
        assertNull(config.scopes)
        assert(config.scopesString.isEmpty())
    }

    @Test
    fun testStringConstructorWithCommaSeparatedScopes() {
        val config = OAuthConfig(
            consumerKey = "test_consumer_key",
            redirectUri = "test://callback",
            scopes = "api,web,refresh_token"
        )

        assertEquals("test_consumer_key", config.consumerKey)
        assertEquals("test://callback", config.redirectUri)
        assertEquals(listOf("api", "web", "refresh_token"), config.scopes)
    }

    @Test
    fun testStringConstructorWithSpaceSeparatedScopes() {
        val config = OAuthConfig(
            consumerKey = "test_consumer_key",
            redirectUri = "test://callback",
            scopes = "api web refresh_token"
        )

        assertEquals("test_consumer_key", config.consumerKey)
        assertEquals("test://callback", config.redirectUri)
        assertEquals(listOf("api", "web", "refresh_token"), config.scopes)
    }

    @Test
    fun testStringConstructorWithCommaSeparatedScopesAndWhitespace() {
        val config = OAuthConfig(
            consumerKey = "test_consumer_key",
            redirectUri = "test://callback",
            scopes = " api , web , refresh_token "
        )

        assertEquals("test_consumer_key", config.consumerKey)
        assertEquals("test://callback", config.redirectUri)
        assertEquals(listOf("api", "web", "refresh_token"), config.scopes)
    }

    @Test
    fun testStringConstructorWithEmptyString() {
        val config = OAuthConfig(
            consumerKey = "test_consumer_key",
            redirectUri = "test://callback",
            scopes = ""
        )

        assertEquals("test_consumer_key", config.consumerKey)
        assertEquals("test://callback", config.redirectUri)
        assertNull(config.scopes)
    }

    @Test
    fun testStringConstructorWithBlankString() {
        val config = OAuthConfig(
            consumerKey = "test_consumer_key",
            redirectUri = "test://callback",
            scopes = "   "
        )

        assertEquals("test_consumer_key", config.consumerKey)
        assertEquals("test://callback", config.redirectUri)
        assertNull(config.scopes)
    }

    @Test
    fun testStringConstructorTrimsConsumerKeyAndRedirectUri() {
        val config = OAuthConfig(
            consumerKey = "  test_consumer_key  ",
            redirectUri = "  test://callback  ",
            scopes = "api"
        )

        assertEquals("test_consumer_key", config.consumerKey)
        assertEquals("test://callback", config.redirectUri)
        assertEquals(listOf("api"), config.scopes)
    }

    @Test
    fun testScopesStringWithNullScopes() {
        val config = OAuthConfig(
            consumerKey = "test_consumer_key",
            redirectUri = "test://callback",
            scopes = null
        )

        assertEquals("", config.scopesString)
    }

    @Test
    fun testScopesStringWithEmptyScopes() {
        val config = OAuthConfig(
            consumerKey = "test_consumer_key",
            redirectUri = "test://callback",
            scopes = emptyList()
        )

        assertEquals("", config.scopesString)
    }
}
