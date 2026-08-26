/*
 * Copyright (c) 2026-present, salesforce.com, inc.
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

import com.salesforce.androidsdk.auth.OAuth2.overrideLoginServerIfNeeded
import org.junit.Assert.assertEquals
import org.junit.Test

class OAuth2OverrideLoginServerTests {

    private val loginServer = "https://login.salesforce.com"
    private val instanceServer = "https://myco.my.salesforce.com"
    private val communityUrl = "https://myco.force.com/community"
    private val communityId = "0DBxx0000000001"

    @Test
    fun test_givenInstanceServerPopulated_whenOverrideLoginServerIfNeeded_thenReturnsInstanceServer() {
        val result = overrideLoginServerIfNeeded(loginServer, instanceServer, null, null)
        assertEquals(instanceServer, result.toString())
    }

    @Test
    fun test_givenInstanceServerNull_whenOverrideLoginServerIfNeeded_thenReturnsLoginServer() {
        val result = overrideLoginServerIfNeeded(loginServer, null, null, null)
        assertEquals(loginServer, result.toString())
    }

    @Test
    fun test_givenCommunityIdAndUrl_whenOverrideLoginServerIfNeeded_thenReturnsCommunityUrl() {
        // communityUrl wins even when instanceServer is also set
        val result = overrideLoginServerIfNeeded(loginServer, instanceServer, communityId, communityUrl)
        assertEquals(communityUrl, result.toString())
    }

    @Test
    fun test_givenCommunityIdButNullCommunityUrl_whenOverrideLoginServerIfNeeded_thenFallsBackToInstanceServer() {
        val result = overrideLoginServerIfNeeded(loginServer, instanceServer, communityId, null)
        assertEquals(instanceServer, result.toString())
    }

    @Test
    fun test_givenAllNull_codeExchangePath_whenOverrideLoginServerIfNeeded_thenReturnsLoginServer() {
        val result = overrideLoginServerIfNeeded(loginServer, null, null, null)
        assertEquals(loginServer, result.toString())
    }

    @Test
    fun test_givenMalformedCommunityUrl_whenOverrideLoginServerIfNeeded_thenFallsBackToInstanceServer() {
        val result = overrideLoginServerIfNeeded(loginServer, instanceServer, communityId, "not a valid uri ://")
        assertEquals(instanceServer, result.toString())
    }

    @Test
    fun test_givenEmptyInstanceServer_whenOverrideLoginServerIfNeeded_thenReturnsLoginServer() {
        val result = overrideLoginServerIfNeeded(loginServer, "", null, null)
        assertEquals(loginServer, result.toString())
    }
}
