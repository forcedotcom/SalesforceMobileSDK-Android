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
package com.salesforce.androidsdk.auth.dpop

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DPoPURLHelperTest {

    @Test
    fun test_givenUrlWithQuery_whenCanonicalize_thenQueryStripped() {
        assertEquals(
            "https://example.com/token",
            DPoPURLHelper.canonicalize("https://example.com/token?foo=bar")
        )
    }

    @Test
    fun test_givenUrlWithFragment_whenCanonicalize_thenFragmentStripped() {
        assertEquals(
            "https://example.com/token",
            DPoPURLHelper.canonicalize("https://example.com/token#frag")
        )
    }

    @Test
    fun test_givenUrlWithQueryAndFragment_whenCanonicalize_thenBothStripped() {
        assertEquals(
            "https://example.com/token",
            DPoPURLHelper.canonicalize("https://example.com/token?q=1#frag")
        )
    }

    // WORKAROUND (W-24564590): remove the internal site path tests below once the server bug is fixed.
    @Test
    fun test_givenInternalSitePath_whenCanonicalize_thenPublicSitePathUsed() {
        assertEquals(
            "https://example.my.site.com/headless/services/oauth2/token",
            DPoPURLHelper.canonicalize("https://example.my.site.com/headlessvforcesite/services/oauth2/token?q=1")
        )
    }

    @Test
    fun test_givenInternalSitePathOnly_whenCanonicalize_thenPublicSitePathUsed() {
        assertEquals(
            "https://example.my.site.com/headless",
            DPoPURLHelper.canonicalize("https://example.my.site.com/headlessvforcesite")
        )
    }

    @Test
    fun test_givenVforcesiteOutsideFirstSegmentOrBare_whenCanonicalize_thenUnchanged() {
        listOf(
            "https://example.my.site.com/vforcesite/services/data/v66.0/",
            "https://example.my.site.com/headless/services/apexrest/vforcesite",
            "https://example.my.site.com/headlessvforcesitex/services/oauth2/token",
        ).forEach { assertEquals(it, DPoPURLHelper.canonicalize(it)) }
    }

    @Test
    fun test_givenCleanUrl_whenCanonicalize_thenUnchanged() {
        assertEquals(
            "https://example.com/services/oauth2/token",
            DPoPURLHelper.canonicalize("https://example.com/services/oauth2/token")
        )
    }
}
