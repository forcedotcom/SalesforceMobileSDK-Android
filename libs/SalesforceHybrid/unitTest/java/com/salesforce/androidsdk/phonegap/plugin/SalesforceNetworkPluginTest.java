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
 * Unit tests for {@link SalesforceNetworkPlugin#isTrustedCallerOrigin(String)}
 * and {@link SalesforceNetworkPlugin#isInstanceServer(String, String)}.
 *
 * isTrustedCallerOrigin — broad check, controls bridge access.
 * isInstanceServer — strict check, controls auth token attachment.
 */
public class SalesforceNetworkPluginTest {

    private static final String INSTANCE_SERVER = "https://myorg.my.salesforce.com";

    private SalesforceNetworkPlugin plugin;

    @Before
    public void setUp() {
        plugin = new SalesforceNetworkPlugin();
    }

    // -------------------------------------------------------------------------
    // isTrustedCallerOrigin (broad check — controls bridge access)
    // -------------------------------------------------------------------------

    @Test
    public void testCaller_givenHttpsLocalhost_thenTrusted() {
        Assert.assertTrue(plugin.isTrustedCallerOrigin("https://localhost/path"));
    }

    @Test
    public void testCaller_givenHttpLocalhost_thenTrusted() {
        Assert.assertTrue(plugin.isTrustedCallerOrigin("http://localhost:8080/index.html"));
    }

    @Test
    public void testCaller_givenFileScheme_thenTrusted() {
        Assert.assertTrue(plugin.isTrustedCallerOrigin("file:///android_asset/www/index.html"));
    }

    @Test
    public void testCaller_givenSalesforceCom_thenTrusted() {
        Assert.assertTrue(plugin.isTrustedCallerOrigin("https://myorg.my.salesforce.com/path"));
    }

    @Test
    public void testCaller_givenForceCom_thenTrusted() {
        Assert.assertTrue(plugin.isTrustedCallerOrigin("https://mypage.force.com/path"));
    }

    @Test
    public void testCaller_givenVisualizeForceCom_thenTrusted() {
        Assert.assertTrue(plugin.isTrustedCallerOrigin("https://mypage.visualforce.com/path"));
    }

    @Test
    public void testCaller_givenEvilCom_thenBlocked() {
        Assert.assertFalse(plugin.isTrustedCallerOrigin("https://evil.com/path"));
    }

    @Test
    public void testCaller_givenSpoofedSalesforceInHostname_thenBlocked() {
        Assert.assertFalse(plugin.isTrustedCallerOrigin("https://evil-salesforce.com/path"));
    }

    @Test
    public void testCaller_givenNullUrl_thenBlocked() {
        Assert.assertFalse(plugin.isTrustedCallerOrigin(null));
    }

    // -------------------------------------------------------------------------
    // isInstanceServer (strict check — controls auth token attachment)
    // -------------------------------------------------------------------------

    @Test
    public void testEndpoint_givenExactInstanceUrl_thenTrusted() {
        Assert.assertTrue(plugin.isInstanceServer("https://myorg.my.salesforce.com/path", INSTANCE_SERVER));
    }

    @Test
    public void testEndpoint_givenInstanceUrlWithPath_thenTrusted() {
        Assert.assertTrue(plugin.isInstanceServer("https://myorg.my.salesforce.com/services/data/v60.0", INSTANCE_SERVER));
    }

    @Test
    public void testEndpoint_givenLocalhost_thenNoAuthTokens() {
        Assert.assertFalse(plugin.isInstanceServer("https://localhost/path", INSTANCE_SERVER));
    }

    @Test
    public void testEndpoint_givenOtherSalesforceOrg_thenBlocked() {
        Assert.assertFalse(plugin.isInstanceServer("https://otherorg.my.salesforce.com/path", INSTANCE_SERVER));
    }

    @Test
    public void testEndpoint_givenForceCom_thenBlocked() {
        Assert.assertFalse(plugin.isInstanceServer("https://mypage.force.com/path", INSTANCE_SERVER));
    }

    @Test
    public void testEndpoint_givenVisualizeForceCom_thenBlocked() {
        Assert.assertFalse(plugin.isInstanceServer("https://mypage.visualforce.com/path", INSTANCE_SERVER));
    }

    @Test
    public void testEndpoint_givenEvilCom_thenBlocked() {
        Assert.assertFalse(plugin.isInstanceServer("https://evil.com/path", INSTANCE_SERVER));
    }

    @Test
    public void testEndpoint_givenHttpInstanceUrl_thenBlocked() {
        Assert.assertFalse(plugin.isInstanceServer("http://myorg.my.salesforce.com/path", INSTANCE_SERVER));
    }

    @Test
    public void testEndpoint_givenNullUrl_thenBlocked() {
        Assert.assertFalse(plugin.isInstanceServer(null, INSTANCE_SERVER));
    }

    @Test
    public void testEndpoint_givenNullInstanceServer_thenAlwaysBlocked() {
        Assert.assertFalse(plugin.isInstanceServer("https://myorg.my.salesforce.com/path", null));
    }
}
