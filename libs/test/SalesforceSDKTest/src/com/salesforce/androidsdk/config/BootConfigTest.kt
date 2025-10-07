/*
 * Copyright (c) 2017-present, salesforce.com, inc.
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

import android.content.Context
import android.content.res.Resources
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.R
import io.mockk.every
import io.mockk.mockk
import org.json.JSONException
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for BootConfig.
 *
 * @author khawkins
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class BootConfigTest {

    private val bootconfigAssetsPathPrefix = "www${System.getProperty("file.separator")}"
    private lateinit var testContext: Context

    @Before
    fun setUp() {
        testContext = InstrumentationRegistry.getInstrumentation().context
    }

    @After
    fun tearDown() {
    }

    @Test
    fun testNoBootConfig() {
        try {
            BootConfig.validateBootConfig(null)
            fail("Validation should fail with no boot config.")
        } catch (e: BootConfig.BootConfigException) {
            // Expected
        }
    }

    @Test
    fun testAbsoluteStartPage() {
        val config = BootConfig.getHybridBootConfig(
            testContext,
            bootconfigAssetsPathPrefix + "bootconfig_absoluteStartPage.json"
        )
        validateBootConfig(config, "Validation should fail with absolute URL start page.")
    }

    @Test
    fun testRemoteDeferredAuthNoUnauthenticatedStartPage() {
        val config = BootConfig.getHybridBootConfig(
            testContext,
            bootconfigAssetsPathPrefix + "bootconfig_remoteDeferredAuthNoUnauthenticatedStartPage.json"
        )
        validateBootConfig(config, "Validation should fail with no unauthenticatedStartPage value in remote deferred auth.")
    }

    @Test
    fun testRelativeUnauthenticatedStartPage() {
        val config = BootConfig.getHybridBootConfig(
            testContext,
            bootconfigAssetsPathPrefix + "bootconfig_relativeUnauthenticatedStartPage.json"
        )
        validateBootConfig(config, "Validation should fail with relative unauthenticatedStartPage value.")
    }

    fun testBootConfigJsonWithOauthScopes() {
        // Tests with bootconfig.json which has oauth scopes defined
        val config = BootConfig.getHybridBootConfig(
            testContext,
            bootconfigAssetsPathPrefix + "bootconfig_absoluteStartPage.json"
        )
        assertNotNull("Boot config should not be null.", config)
        assertNotNull("OAuth scopes should not be null when specified in XML.", config.oauthScopes)
        assertTrue("OAuth scopes should have at least one scope.", config.oauthScopes!!.isNotEmpty())
    }


    @Test
    fun testBootConfigJsonWithNoOauthScopes() {
        val config = BootConfig.getHybridBootConfig(
            testContext,
            bootconfigAssetsPathPrefix + "bootconfig_noOauthScopes.json"
        )
        assertNotNull("Boot config should not be null.", config)
        assertNull("OAuth scopes should be null when not specified in JSON.", config.oauthScopes)
        // Should validate successfully
        BootConfig.validateBootConfig(config)
    }

    @Test
    fun testBootConfigJsonWithEmptyOauthScopes() {
        val config = BootConfig.getHybridBootConfig(
            testContext,
            bootconfigAssetsPathPrefix + "bootconfig_emptyOauthScopes.json"
        )
        assertNotNull("Boot config should not be null.", config)
        assertNotNull("OAuth scopes should not be null when empty array specified in JSON.", config.oauthScopes)
        assertEquals("OAuth scopes should be empty array when empty array specified in JSON.", 0, config.oauthScopes?.size)
        // Should validate successfully
        BootConfig.validateBootConfig(config)
    }

    @Test
    fun testBootConfigXmlWithOauthScopes() {
        // Tests the default bootconfig.xml which has oauth scopes defined
        val config = BootConfig.getNativeBootConfig(testContext)
        assertNotNull("Boot config should not be null.", config)
        assertNotNull("OAuth scopes should not be null when specified in XML.", config.oauthScopes)
        assertTrue("OAuth scopes should have at least one scope.", config.oauthScopes!!.isNotEmpty())
        assertArrayEquals("Incorrect OAuth scopes.", arrayOf("api", "web", "openid"), config.oauthScopes)
    }

    @Test
    fun testBootConfigXmlWithNoOauthScopes() {
        // Create mock context and resources
        val mockContext = mockk<Context>()
        val mockResources = mockk<Resources>()

        // Setup mock to return test values for strings
        every { mockContext.resources } returns mockResources
        every { mockResources.getString(R.string.remoteAccessConsumerKey) } returns "test_consumer_key"
        every { mockResources.getString(R.string.oauthRedirectURI) } returns "test://redirect"

        // Simulate missing oauthScopes resource by throwing NotFoundException
        every { mockResources.getStringArray(R.array.oauthScopes) } throws Resources.NotFoundException("oauthScopes not found")

        // Test reading from XML with missing scopes
        val config = BootConfig.getNativeBootConfig(mockContext)
        assertNotNull("Boot config should not be null.", config)
        assertNull("OAuth scopes should be null when not specified in XML.", config.oauthScopes)
        assertEquals("Consumer key should match.", "test_consumer_key", config.remoteAccessConsumerKey)
        assertEquals("Redirect URI should match.", "test://redirect", config.oauthRedirectURI)
    }

    @Test
    fun testBootConfigXmlWithEmptyOauthScopes() {
        // Create mock context and resources
        val mockContext = mockk<Context>()
        val mockResources = mockk<Resources>()

        // Setup mock to return test values
        every { mockContext.resources } returns mockResources
        every { mockResources.getString(R.string.remoteAccessConsumerKey) } returns "test_consumer_key"
        every { mockResources.getString(R.string.oauthRedirectURI) } returns "test://redirect"

        // Return an empty array for oauthScopes
        every { mockResources.getStringArray(R.array.oauthScopes) } returns emptyArray()

        // Test reading from XML with empty scopes array
        val config = BootConfig.getNativeBootConfig(mockContext)
        assertNotNull("Boot config should not be null.", config)
        assertNotNull("OAuth scopes should not be null when empty array specified in XML.", config.oauthScopes)
        assertEquals("OAuth scopes should be empty array when empty array specified in XML.", 0, config.oauthScopes?.size)
        assertEquals("Consumer key should match.", "test_consumer_key", config.remoteAccessConsumerKey)
        assertEquals("Redirect URI should match.", "test://redirect", config.oauthRedirectURI)
    }

    @Test
    fun testAsJSONWithNoOauthScopes() {
        // Test that asJSON properly handles missing oauth scopes
        val config = BootConfig.getHybridBootConfig(
            testContext,
            bootconfigAssetsPathPrefix + "bootconfig_noOauthScopes.json"
        )
        assertNotNull("Boot config should not be null.", config)

        val json = config.asJSON()
        assertNotNull("JSON representation should not be null.", json)
        assertFalse("JSON should not contain oauthScopes key when scopes are null.", json.has("oauthScopes"))
    }

    @Test
    fun testAsJSONWithEmptyOauthScopes() {
        // Test that asJSON properly handles empty oauth scopes array
        val config = BootConfig.getHybridBootConfig(
            testContext,
            bootconfigAssetsPathPrefix + "bootconfig_emptyOauthScopes.json"
        )
        assertNotNull("Boot config should not be null.", config)

        val json = config.asJSON()
        assertNotNull("JSON representation should not be null.", json)
        assertTrue("JSON should contain oauthScopes key when scopes are empty array.", json.has("oauthScopes"))
        try {
            val scopes = json.getJSONArray("oauthScopes")
            assertEquals("JSON oauthScopes should be empty array.", 0, scopes.length())
        } catch (e: JSONException) {
            fail("Should be able to get oauthScopes as JSONArray: ${e.message}")
        }
    }

    private fun validateBootConfig(config: BootConfig, errorMessage: String) {
        assertNotNull("Boot config should not be null.", config)
        try {
            BootConfig.validateBootConfig(config)
            fail(errorMessage)
        } catch (e: BootConfig.BootConfigException) {
            // Expected
        }
    }
}

