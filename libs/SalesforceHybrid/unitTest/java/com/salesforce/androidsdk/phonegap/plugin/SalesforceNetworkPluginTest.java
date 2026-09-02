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
 * Unit tests for {@link SalesforceNetworkPlugin#isTrustedSalesforceHost(String, String)}.
 * The method is used for both caller-origin and endpoint validation.
 */
public class SalesforceNetworkPluginTest {

    private static final String INSTANCE_SERVER = "https://myorg.my.salesforce.com";

    private SalesforceNetworkPlugin plugin;

    @Before
    public void setUp() {
        plugin = new SalesforceNetworkPlugin();
    }

    // --- localhost is always trusted ---

    @Test
    public void test_givenHttpsLocalhost_whenCheckingHost_thenTrusted() {
        Assert.assertTrue(plugin.isTrustedSalesforceHost("https://localhost/path", INSTANCE_SERVER));
    }

    @Test
    public void test_givenHttpLocalhost_whenCheckingHost_thenTrusted() {
        Assert.assertTrue(plugin.isTrustedSalesforceHost("http://localhost:8080/index.html", INSTANCE_SERVER));
    }

    // --- instance URL host is trusted ---

    @Test
    public void test_givenExactInstanceUrl_whenCheckingHost_thenTrusted() {
        Assert.assertTrue(plugin.isTrustedSalesforceHost("https://myorg.my.salesforce.com/path", INSTANCE_SERVER));
    }

    @Test
    public void test_givenInstanceUrlWithPath_whenCheckingHost_thenTrusted() {
        Assert.assertTrue(plugin.isTrustedSalesforceHost("https://myorg.my.salesforce.com/services/data/v60.0", INSTANCE_SERVER));
    }

    // --- non-instance Salesforce domains are NOT trusted ---

    @Test
    public void test_givenOtherSalesforceOrgUrl_whenCheckingHost_thenBlocked() {
        Assert.assertFalse(plugin.isTrustedSalesforceHost("https://otherorg.my.salesforce.com/path", INSTANCE_SERVER));
    }

    @Test
    public void test_givenWildcardSalesforceComUrl_whenCheckingHost_thenBlocked() {
        Assert.assertFalse(plugin.isTrustedSalesforceHost("https://anything.salesforce.com/path", INSTANCE_SERVER));
    }

    @Test
    public void test_givenForceComUrl_whenCheckingHost_thenBlocked() {
        Assert.assertFalse(plugin.isTrustedSalesforceHost("https://mypage.force.com/path", INSTANCE_SERVER));
    }

    @Test
    public void test_givenVisualforceComUrl_whenCheckingHost_thenBlocked() {
        Assert.assertFalse(plugin.isTrustedSalesforceHost("https://mypage.visualforce.com/path", INSTANCE_SERVER));
    }

    // --- arbitrary hosts are NOT trusted ---

    @Test
    public void test_givenEvilComUrl_whenCheckingHost_thenBlocked() {
        Assert.assertFalse(plugin.isTrustedSalesforceHost("https://evil.com/path", INSTANCE_SERVER));
    }

    @Test
    public void test_givenSpoofedSalesforceInHostname_whenCheckingHost_thenBlocked() {
        Assert.assertFalse(plugin.isTrustedSalesforceHost("https://evil-salesforce.com/path", INSTANCE_SERVER));
    }

    // --- scheme is enforced for non-localhost ---

    @Test
    public void test_givenHttpInstanceUrl_whenCheckingHost_thenBlocked() {
        Assert.assertFalse(plugin.isTrustedSalesforceHost("http://myorg.my.salesforce.com/path", INSTANCE_SERVER));
    }

    // --- edge cases ---

    @Test
    public void test_givenNullUrl_whenCheckingHost_thenBlocked() {
        Assert.assertFalse(plugin.isTrustedSalesforceHost(null, INSTANCE_SERVER));
    }

    @Test
    public void test_givenNullInstanceServer_whenCheckingNonLocalhost_thenBlocked() {
        Assert.assertFalse(plugin.isTrustedSalesforceHost("https://myorg.my.salesforce.com/path", null));
    }

    @Test
    public void test_givenNullInstanceServer_whenCheckingLocalhost_thenTrusted() {
        Assert.assertTrue(plugin.isTrustedSalesforceHost("https://localhost/path", null));
    }
}
