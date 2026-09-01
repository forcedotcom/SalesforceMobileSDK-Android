/*
 * Copyright (c) 2024-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.phonegap.plugin;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link SalesforceNetworkPlugin#isTrustedCallerOrigin(String)}.
 */
public class SalesforceNetworkPluginTest {

    private SalesforceNetworkPlugin plugin;

    @Before
    public void setUp() {
        plugin = new SalesforceNetworkPlugin();
    }

    @Test
    public void test_givenLocalFileUrl_whenCheckingOrigin_thenTrusted() {
        Assert.assertTrue(plugin.isTrustedCallerOrigin("file:///android_asset/www/index.html"));
    }

    @Test
    public void test_givenSalesforceComUrl_whenCheckingOrigin_thenTrusted() {
        Assert.assertTrue(plugin.isTrustedCallerOrigin("https://myorg.salesforce.com/path"));
    }

    @Test
    public void test_givenMySalesforceComUrl_whenCheckingOrigin_thenTrusted() {
        Assert.assertTrue(plugin.isTrustedCallerOrigin("https://myorg.my.salesforce.com/path"));
    }

    @Test
    public void test_givenVisualforceComUrl_whenCheckingOrigin_thenTrusted() {
        Assert.assertTrue(plugin.isTrustedCallerOrigin("https://mypage.visualforce.com/path"));
    }

    @Test
    public void test_givenForceComUrl_whenCheckingOrigin_thenTrusted() {
        Assert.assertTrue(plugin.isTrustedCallerOrigin("https://mypage.force.com/path"));
    }

    @Test
    public void test_givenDocumentforceComUrl_whenCheckingOrigin_thenTrusted() {
        Assert.assertTrue(plugin.isTrustedCallerOrigin("https://docs.documentforce.com/path"));
    }

    @Test
    public void test_givenSalesforceCommunitiesUrl_whenCheckingOrigin_thenTrusted() {
        Assert.assertTrue(plugin.isTrustedCallerOrigin("https://mysite.salesforce-communities.com/path"));
    }

    @Test
    public void test_givenLocalhostUrl_whenCheckingOrigin_thenTrusted() {
        Assert.assertTrue(plugin.isTrustedCallerOrigin("https://localhost/path"));
    }

    @Test
    public void test_givenEvilComUrl_whenCheckingOrigin_thenBlocked() {
        Assert.assertFalse(plugin.isTrustedCallerOrigin("https://evil.com/path"));
    }

    @Test
    public void test_givenNotSalesforceComUrl_whenCheckingOrigin_thenBlocked() {
        Assert.assertFalse(plugin.isTrustedCallerOrigin("https://notsalesforce.com/path"));
    }

    @Test
    public void test_givenSpoofedSalesforceInHostname_whenCheckingOrigin_thenBlocked() {
        Assert.assertFalse(plugin.isTrustedCallerOrigin("https://evil-salesforce.com/path"));
    }

    @Test
    public void test_givenNullUrl_whenCheckingOrigin_thenBlocked() {
        Assert.assertFalse(plugin.isTrustedCallerOrigin(null));
    }
}
