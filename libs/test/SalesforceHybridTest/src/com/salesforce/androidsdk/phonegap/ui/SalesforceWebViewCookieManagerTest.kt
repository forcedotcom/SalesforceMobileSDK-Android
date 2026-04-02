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
package com.salesforce.androidsdk.phonegap.ui

import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Test class for SalesforceWebViewCookieManager.
 */
@RunWith(JUnit4::class)
@SmallTest
class SalesforceWebViewCookieManagerTest {

    private lateinit var cookieManager: SalesforceWebViewCookieManager

    @Before
    fun setUp() {
        cookieManager = SalesforceWebViewCookieManager()
    }

    /**
     * Test inspectScopes with full scope - should not warn about any missing scopes.
     */
    @Test
    fun testInspectScopesWithFullScope() {
        val warnMessages = mutableListOf<String>()
        val warnFunction: (String) -> Unit = { message -> warnMessages.add(message) }

        cookieManager.inspectScopes("full", warnFunction)

        assertTrue("Should not warn about any missing scopes when 'full' scope is present", 
                   warnMessages.isEmpty())
    }

    /**
     * Test inspectScopes with all individual scopes - should not warn about any missing scopes.
     */
    @Test
    fun testInspectScopesWithAllIndividualScopes() {
        val warnMessages = mutableListOf<String>()
        val warnFunction: (String) -> Unit = { message -> warnMessages.add(message) }

        cookieManager.inspectScopes("web visualforce lightning content", warnFunction)

        assertTrue("Should not warn about any missing scopes when all individual scopes are present", 
                   warnMessages.isEmpty())
    }

    /**
     * Test inspectScopes with web scope - should not warn about web or visualforce.
     */
    @Test
    fun testInspectScopesWithWebScope() {
        val warnMessages = mutableListOf<String>()
        val warnFunction: (String) -> Unit = { message -> warnMessages.add(message) }

        cookieManager.inspectScopes("web lightning content", warnFunction)

        assertTrue("Should not warn about web scope when present", 
                   warnMessages.none { it.contains("web") })
        assertTrue("Should not warn about visualforce scope when web scope is present", 
                   warnMessages.none { it.contains("visualforce") })
        assertTrue("Should not warn about lightning scope when present", 
                   warnMessages.none { it.contains("lightning") })
        assertTrue("Should not warn about content scope when present", 
                   warnMessages.none { it.contains("content") })
    }

    /**
     * Test inspectScopes with visualforce scope but no web scope - should warn about web.
     */
    @Test
    fun testInspectScopesWithVisualforceButNoWebScope() {
        val warnMessages = mutableListOf<String>()
        val warnFunction: (String) -> Unit = { message -> warnMessages.add(message) }

        cookieManager.inspectScopes("visualforce lightning content", warnFunction)

        assertTrue("Should warn about missing web scope", 
                   warnMessages.any { it.contains("Missing web scope") })
        assertTrue("Should not warn about visualforce scope when present", 
                   warnMessages.none { it.contains("visualforce") })
    }

    /**
     * Test inspectScopes with missing web scope - should warn about web and visualforce.
     */
    @Test
    fun testInspectScopesWithMissingWebScope() {
        val warnMessages = mutableListOf<String>()
        val warnFunction: (String) -> Unit = { message -> warnMessages.add(message) }

        cookieManager.inspectScopes("lightning content", warnFunction)

        assertTrue("Should warn about missing web scope", 
                   warnMessages.any { it.contains("Missing web scope") })
        assertTrue("Should warn about missing visualforce scope", 
                   warnMessages.any { it.contains("Missing visualforce scope") })
    }

    /**
     * Test inspectScopes with missing lightning scope - should warn about lightning.
     */
    @Test
    fun testInspectScopesWithMissingLightningScope() {
        val warnMessages = mutableListOf<String>()
        val warnFunction: (String) -> Unit = { message -> warnMessages.add(message) }

        cookieManager.inspectScopes("web content", warnFunction)

        assertTrue("Should warn about missing lightning scope", 
                   warnMessages.any { it.contains("Missing lightning scope") })
    }

    /**
     * Test inspectScopes with missing content scope - should warn about content.
     */
    @Test
    fun testInspectScopesWithMissingContentScope() {
        val warnMessages = mutableListOf<String>()
        val warnFunction: (String) -> Unit = { message -> warnMessages.add(message) }

        cookieManager.inspectScopes("web lightning", warnFunction)

        assertTrue("Should warn about missing content scope", 
                   warnMessages.any { it.contains("Missing content scope") })
    }

    /**
     * Test setCookies with complete user account - should call setCookieValue for all domains.
     */
    @Test
    fun testSetCookiesWithCompleteUserAccount() {
        val setCookieValueCalls = mutableListOf<CookieCall>()
        val syncCookiesCalls = mutableListOf<Unit>()

        val setCookieValueLambda: (String, String?, Boolean, String?, String?) -> Unit = 
            { cookieType, domain, setDomain, name, value -> 
                setCookieValueCalls.add(CookieCall(cookieType, domain, setDomain, name, value))
            }
        val syncCookiesLambda: () -> Unit = { syncCookiesCalls.add(Unit) }

        val userAccount = createTestUserAccount()

        cookieManager.setCookies(userAccount, setCookieValueLambda, syncCookiesLambda)

        // Verify syncCookies was called
        assertEquals("syncCookies should be called once", 1, syncCookiesCalls.size)

        // Verify all expected cookie calls were made
        val expectedCalls = listOf(
            CookieCall("sid for main", "test.salesforce.com", false, "sid", "test_auth_token"),
            CookieCall("clientSrc", "test.salesforce.com", false, "clientSrc", "test_client_src"),
            CookieCall("sid_Client", "test.salesforce.com", false, "sid_Client", "test_sid_client"),
            CookieCall("oid", "test.salesforce.com", false, "oid", "test_org_id"),
            CookieCall("eikoocnekotMob", "test.salesforce.com", false, "eikoocnekotMob", "test_csrf_token"),
            CookieCall("sid for lightning", "lightning.test.salesforce.com", false, "sid", "test_lightning_sid"),
            CookieCall("eikoocnekotMob", "lightning.test.salesforce.com", false, "eikoocnekotMob", "test_csrf_token"),
            CookieCall("sid for content", "content.test.salesforce.com", false, "sid", "test_content_sid"),
            CookieCall("sid for vf", "vf.test.salesforce.com", false, "sid", "test_vf_sid"),
            CookieCall("clientSrc", "vf.test.salesforce.com", false, "clientSrc", "test_client_src"),
            CookieCall("sid_Client", "vf.test.salesforce.com", false, "sid_Client", "test_sid_client"),
            CookieCall("oid", "vf.test.salesforce.com", false, "oid", "test_org_id")
        )

        assertEquals("Should make correct number of setCookieValue calls", 
                     expectedCalls.size, setCookieValueCalls.size)

        // Verify each expected call
        expectedCalls.forEach { expectedCall ->
            assertTrue("Should contain call: $expectedCall", 
                       setCookieValueCalls.contains(expectedCall))
        }
    }

    /**
     * Test setCookies with community URL - should set domain flag to true.
     */
    @Test
    fun testSetCookiesWithCommunityUrl() {
        val setCookieValueCalls = mutableListOf<CookieCall>()
        val communityUserAccount = createTestUserAccountWithCommunity()

        val setCookieValueLambda: (String, String?, Boolean, String?, String?) -> Unit = 
            { cookieType, domain, setDomain, name, value -> 
                setCookieValueCalls.add(CookieCall(cookieType, domain, setDomain, name, value))
            }

        cookieManager.setCookies(communityUserAccount, setCookieValueLambda)

        // Verify that setDomain is true for all calls when community URL is present
        assertTrue("All calls should have setDomain=true when community URL is present",
                   setCookieValueCalls.all { it.setDomain })
    }

    /**
     * Test setCookies with JWT token format - should use parentSid instead of authToken.
     */
    @Test
    fun testSetCookiesWithJWTTokenFormat() {
        val setCookieValueCalls = mutableListOf<CookieCall>()
        val jwtUserAccount = createTestUserAccountWithJWT()

        val setCookieValueLambda: (String, String?, Boolean, String?, String?) -> Unit = 
            { cookieType, domain, setDomain, name, value -> 
                setCookieValueCalls.add(CookieCall(cookieType, domain, setDomain, name, value))
            }

        cookieManager.setCookies(jwtUserAccount, setCookieValueLambda)

        // Find the main SID call
        val mainSidCall = setCookieValueCalls.find { it.cookieType == "sid for main" }
        assertNotNull("Should have main SID call", mainSidCall)
        assertEquals("Should use parentSid for JWT token format", "test_parent_sid", mainSidCall?.value)
    }

    // Helper data class for tracking cookie calls
    private data class CookieCall(
        val cookieType: String,
        val domain: String?,
        val setDomain: Boolean,
        val name: String?,
        val value: String?
    )

    // Helper method to create a test UserAccount with all required fields
    // This mimics the structure of UserAccountTest.createTestAccount() but with test-specific values
    private fun createTestUserAccount(): UserAccount {
        return UserAccountBuilder.getInstance()
            .authToken("test_auth_token")
            .refreshToken("test_refresh_token")
            .loginServer("https://test.salesforce.com")
            .idUrl("https://test.salesforce.com/id")
            .instanceServer("https://test.salesforce.com")
            .apiInstanceServer("https://test.salesforce.com")
            .orgId("test_org_id")
            .userId("test_user_id")
            .username("test_user")
            .accountName("test_account")
            .communityId("test_community_id")
            .communityUrl(null)
            .firstName("Test")
            .lastName("User")
            .displayName("Test User")
            .email("test@example.com")
            .photoUrl("https://test.salesforce.com/photo")
            .thumbnailUrl("https://test.salesforce.com/thumbnail")
            .lightningDomain("lightning.test.salesforce.com")
            .lightningSid("test_lightning_sid")
            .vfDomain("vf.test.salesforce.com")
            .vfSid("test_vf_sid")
            .contentDomain("content.test.salesforce.com")
            .contentSid("test_content_sid")
            .csrfToken("test_csrf_token")
            .nativeLogin(false)
            .language("en")
            .locale("en_US")
            .cookieClientSrc("test_client_src")
            .cookieSidClient("test_sid_client")
            .sidCookieName("sid")
            .clientId("test_client_id")
            .parentSid("test_parent_sid")
            .tokenFormat("access_token")
            .beaconChildConsumerKey("test_beacon_key")
            .beaconChildConsumerSecret("test_beacon_secret")
            .scope("web lightning content")
            .additionalOauthValues(emptyMap())
            .build()
    }

    // Helper method to create a test UserAccount with community URL
    private fun createTestUserAccountWithCommunity(): UserAccount {
        return UserAccountBuilder.getInstance()
            .populateFromUserAccount(createTestUserAccount())
            .communityUrl("https://community.salesforce.com")
            .build()
    }

    // Helper method to create a test UserAccount with JWT token format
    private fun createTestUserAccountWithJWT(): UserAccount {
        return UserAccountBuilder.getInstance()
            .populateFromUserAccount(createTestUserAccount())
            .tokenFormat("jwt")
            .build()
    }

    // Helper method to create a test UserAccount with some null values
    private fun createTestUserAccountWithNullValues(): UserAccount {
        return UserAccountBuilder.getInstance()
            .populateFromUserAccount(createTestUserAccount())
            .lightningDomain(null)
            .lightningSid(null)
            .contentDomain(null)
            .contentSid(null)
            .vfDomain(null)
            .vfSid(null)
            .build()
    }
}