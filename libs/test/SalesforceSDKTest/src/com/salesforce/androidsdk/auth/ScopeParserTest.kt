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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.auth.ScopeParser.Companion.toScopeParameter
import com.salesforce.androidsdk.auth.ScopeParser.Companion.toScopeParser
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for ScopeParser.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class ScopeParserTest {

    /**
     * Testing ScopeParser constructor with array of scopes.
     */
    @Test
    fun testScopeParserConstructorWithArray() {
        val parser = ScopeParser(arrayOf("api", "web", "refresh_token", "id"))
        Assert.assertTrue("Should have api scope", parser.hasScope("api"))
        Assert.assertTrue("Should have web scope", parser.hasScope("web"))
        Assert.assertTrue("Should have refresh_token scope", parser.hasRefreshTokenScope())
        Assert.assertTrue("Should have id scope", parser.hasIdentityScope())
        Assert.assertFalse("Should not have unknown scope", parser.hasScope("unknown"))
    }

    /**
     * Testing ScopeParser constructor with empty array.
     */
    @Test
    fun testScopeParserConstructorWithEmptyArray() {
        val parser = ScopeParser(arrayOf())
        Assert.assertFalse("Should not have any scopes", parser.hasScope("api"))
        Assert.assertFalse("Should not have refresh_token scope", parser.hasRefreshTokenScope())
        Assert.assertEquals("Should have empty scope set", 0, parser.scopes.size)
    }

    /**
     * Testing ScopeParser constructor with scope string.
     */
    @Test
    fun testScopeParserConstructorWithString() {
        val parser = ScopeParser("api web refresh_token id")
        Assert.assertTrue("Should have api scope", parser.hasScope("api"))
        Assert.assertTrue("Should have web scope", parser.hasScope("web"))
        Assert.assertTrue("Should have refresh_token scope", parser.hasRefreshTokenScope())
        Assert.assertTrue("Should have id scope", parser.hasIdentityScope())
        Assert.assertFalse("Should not have unknown scope", parser.hasScope("unknown"))
    }

    /**
     * Testing ScopeParser constructor with null string.
     */
    @Test
    fun testScopeParserConstructorWithNullString() {
        val parser = ScopeParser(null as String?)
        Assert.assertFalse("Should not have any scopes", parser.hasScope("api"))
        Assert.assertFalse("Should not have refresh_token scope", parser.hasRefreshTokenScope())
        Assert.assertEquals("Should have empty scope set", 0, parser.scopes.size)
    }

    /**
     * Testing ScopeParser constructor with empty string.
     */
    @Test
    fun testScopeParserConstructorWithEmptyString() {
        val parser = ScopeParser("")
        Assert.assertFalse("Should not have any scopes", parser.hasScope("api"))
        Assert.assertFalse("Should not have refresh_token scope", parser.hasRefreshTokenScope())
        Assert.assertEquals("Should have empty scope set", 0, parser.scopes.size)
    }

    /**
     * Testing ScopeParser constructor with whitespace-only string.
     */
    @Test
    fun testScopeParserConstructorWithWhitespaceString() {
        val parser = ScopeParser("   \t\n  ")
        Assert.assertFalse("Should not have any scopes", parser.hasScope("api"))
        Assert.assertFalse("Should not have refresh_token scope", parser.hasRefreshTokenScope())
        Assert.assertEquals("Should have empty scope set", 0, parser.scopes.size)
    }

    /**
     * Testing ScopeParser parseScopes factory method.
     */
    @Test
    fun testScopeParserParseScopes() {
        val parser = ScopeParser.parseScopes("api web id refresh_token")
        Assert.assertTrue("Should have api scope", parser.hasScope("api"))
        Assert.assertTrue("Should have web scope", parser.hasScope("web"))
        Assert.assertTrue("Should have id scope", parser.hasIdentityScope())
        Assert.assertTrue("Should have refresh_token scope", parser.hasRefreshTokenScope())
    }

    /**
     * Testing ScopeParser hasScope method.
     */
    @Test
    fun testScopeParserHasScope() {
        val parser = ScopeParser("api web refresh_token")
        
        // Test existing scopes
        Assert.assertTrue("Should have api scope", parser.hasScope("api"))
        Assert.assertTrue("Should have web scope", parser.hasScope("web"))
        Assert.assertTrue("Should have refresh_token scope", parser.hasScope("refresh_token"))
        
        // Test non-existing scope
        Assert.assertFalse("Should not have unknown scope", parser.hasScope("unknown"))
        
        // Test null/empty scope
        Assert.assertFalse("Should return false for null scope", parser.hasScope(null))
        Assert.assertFalse("Should return false for empty scope", parser.hasScope(""))
        Assert.assertFalse("Should return false for whitespace scope", parser.hasScope("  "))
        
        // Test trimming
        Assert.assertTrue("Should handle leading/trailing whitespace", parser.hasScope(" api "))
    }

    /**
     * Testing ScopeParser hasRefreshTokenScope method.
     */
    @Test
    fun testScopeParserHasRefreshTokenScope() {
        val parserWithRefresh = ScopeParser("api web refresh_token")
        Assert.assertTrue("Should have refresh_token scope", parserWithRefresh.hasRefreshTokenScope())
        
        val parserWithoutRefresh = ScopeParser("api web")
        Assert.assertFalse("Should not have refresh_token scope", parserWithoutRefresh.hasRefreshTokenScope())
        
        val emptyParser = ScopeParser("")
        Assert.assertFalse("Empty parser should not have refresh_token scope", emptyParser.hasRefreshTokenScope())
    }

    /**
     * Testing ScopeParser hasIdScope method.
     */
    @Test
    fun testScopeParserHasIdentityScope() {
        val parserWithId = ScopeParser("api web id")
        Assert.assertTrue("Should have id scope", parserWithId.hasIdentityScope())
        
        val parserWithoutId = ScopeParser("api web refresh_token")
        Assert.assertFalse("Should not have id scope", parserWithoutId.hasIdentityScope())
        
        val emptyParser = ScopeParser("")
        Assert.assertFalse("Empty parser should not have id scope", emptyParser.hasIdentityScope())
    }

    /**
     * Testing ScopeParser with duplicate scopes.
     */
    @Test
    fun testScopeParserDuplicateScopes() {
        val parser = ScopeParser("api web api web refresh_token")
        Assert.assertTrue("Should have api scope", parser.hasScope("api"))
        Assert.assertTrue("Should have web scope", parser.hasScope("web"))
        Assert.assertTrue("Should have refresh_token scope", parser.hasRefreshTokenScope())
        
        // Should deduplicate - only 3 unique scopes
        Assert.assertEquals("Should deduplicate scopes", 3, parser.scopes.size)
    }

    /**
     * Testing ScopeParser getScopesAsString method.
     */
    @Test
    fun testScopeParserGetScopesAsString() {
        val parser = ScopeParser("web api refresh_token")
        val scopesString = parser.scopesAsString
        // Should be sorted alphabetically
        Assert.assertEquals("Should return sorted scope string", "api refresh_token web", scopesString)
        
        val emptyParser = ScopeParser("")
        Assert.assertEquals("Empty parser should return empty string", "", emptyParser.scopesAsString)
    }

    /**
     * Testing ScopeParser.computeScopeParameter method.
     */
    @Test
    fun testScopeParserComputeScopeParameter() {
        // Test with null
        Assert.assertEquals("Should return empty string for null", "", ScopeParser.computeScopeParameter(null))
        
        // Test with empty array
        Assert.assertEquals("Should return empty string for empty array", "", ScopeParser.computeScopeParameter(arrayOf()))
        
        // Test with single scope
        Assert.assertEquals("Should add refresh_token to single scope", "api refresh_token", 
            ScopeParser.computeScopeParameter(arrayOf("api")))
        
        // Test when refresh_token is not included
        Assert.assertEquals("Should add refresh_token and sort", "api refresh_token visualforce web",
            ScopeParser.computeScopeParameter(arrayOf("web", "api", "visualforce")))
        
        // Test when refresh_token already included
        Assert.assertEquals("Should not duplicate refresh_token", "api refresh_token web",
            ScopeParser.computeScopeParameter(arrayOf("api", "refresh_token", "web")))
        
        // Test with only refresh_token
        Assert.assertEquals("Should return only refresh_token", "refresh_token",
            ScopeParser.computeScopeParameter(arrayOf("refresh_token")))
    }

    @Test
    fun testScopeParserStringExtension() {
        val parser = "api web refresh_token".toScopeParser()

        // Test existing scopes
        Assert.assertTrue("Should have api scope", parser.hasScope("api"))
        Assert.assertTrue("Should have web scope", parser.hasScope("web"))
        Assert.assertTrue("Should have refresh_token scope", parser.hasScope("refresh_token"))

        // Test non-existing scope
        Assert.assertFalse("Should not have unknown scope", parser.hasScope("unknown"))

        // Test null/empty scope
        Assert.assertFalse("Should return false for null scope", parser.hasScope(null))
        Assert.assertFalse("Should return false for empty scope", parser.hasScope(""))
        Assert.assertFalse("Should return false for whitespace scope", parser.hasScope("  "))

        // Test trimming
        Assert.assertTrue("Should handle leading/trailing whitespace", parser.hasScope(" api "))
    }

    @Test
    fun testArrayToScopeParameterExtension() {
        // Test with null
        Assert.assertEquals("Should return empty string for null", "", (null as Array<String>?).toScopeParameter())

        // Test with empty array
        Assert.assertEquals("Should return empty string for empty array", "", arrayOf<String>().toScopeParameter())

        // Test with single scope
        Assert.assertEquals("Should add refresh_token to single scope", "api refresh_token",
            arrayOf("api").toScopeParameter())

        // Test when refresh_token is not included
        Assert.assertEquals("Should add refresh_token and sort", "api refresh_token visualforce web",
            arrayOf("web", "api", "visualforce").toScopeParameter())

        // Test when refresh_token already included
        Assert.assertEquals("Should not duplicate refresh_token", "api refresh_token web",
            arrayOf("api", "refresh_token", "web").toScopeParameter())

        // Test with only refresh_token
        Assert.assertEquals("Should return only refresh_token", "refresh_token",
            arrayOf("refresh_token").toScopeParameter())
    }
}
