/*
 * Copyright (c) 2018-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.CompletableFuture;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for AuthConfigUtil.
 *
 * @author bhariharan
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class AuthConfigUtilTest {

    private static final String MY_DOMAIN_ENDPOINT = "https://mobilesdk.my.salesforce.com";
    private static final String ALTERNATE_MY_DOMAIN_ENDPOINT = "https://powerofus.force.com";
    private static final String SANDBOX_ENDPOINT = "https://test.salesforce.com";
    private static final String FORWARD_SLASH = "/";

    private static class TestBroadcastReceiver extends BroadcastReceiver {
        private final CompletableFuture<Intent> intentFuture = new CompletableFuture<>();

        @Override
        public void onReceive(Context context, Intent intent) {
            intentFuture.complete(intent);
        }

        Future<Intent> getIntent() {
            return intentFuture;
        }
    }

    @Test
    public void testGetAuthConfigWithoutForwardSlash() {
        final AuthConfigUtil.MyDomainAuthConfig authConfig = AuthConfigUtil.getMyDomainAuthConfig(MY_DOMAIN_ENDPOINT);
        Assert.assertNotNull("Auth config should not be null", authConfig);
        Assert.assertNotNull("Auth config JSON should not be null", authConfig.getAuthConfig());
    }

    @Test
    public void testGetAuthConfigWithForwardSlash() {
        final AuthConfigUtil.MyDomainAuthConfig authConfig = AuthConfigUtil.getMyDomainAuthConfig(MY_DOMAIN_ENDPOINT + FORWARD_SLASH);
        Assert.assertNotNull("Auth config should not be null", authConfig);
        Assert.assertNotNull("Auth config JSON should not be null", authConfig.getAuthConfig());
    }

    @Test
    public void testBrowserBasedLoginEnabled() {
        final AuthConfigUtil.MyDomainAuthConfig authConfig = AuthConfigUtil.getMyDomainAuthConfig(MY_DOMAIN_ENDPOINT);
        Assert.assertNotNull("Auth config should not be null", authConfig);
        Assert.assertNotNull("Auth config JSON should not be null", authConfig.getAuthConfig());
        Assert.assertTrue("Browser based login should be enabled", authConfig.isBrowserLoginEnabled());
    }

    @Test
    public void testGetSSOUrls() {
        final AuthConfigUtil.MyDomainAuthConfig authConfig = AuthConfigUtil.getMyDomainAuthConfig(ALTERNATE_MY_DOMAIN_ENDPOINT);
        Assert.assertNotNull("Auth config should not be null", authConfig);
        Assert.assertNotNull("Auth config JSON should not be null", authConfig.getAuthConfig());
        Assert.assertNotNull("SSO URLs should not be null", authConfig.getSsoUrls());
        Assert.assertEquals("SSO URLs should have 3 valid entries", 3, authConfig.getSsoUrls().size());
    }

    @Test
    public void testGetLoginPageUrl() {
        final AuthConfigUtil.MyDomainAuthConfig authConfig = AuthConfigUtil.getMyDomainAuthConfig(ALTERNATE_MY_DOMAIN_ENDPOINT);
        Assert.assertNotNull("Auth config should not be null", authConfig);
        Assert.assertNotNull("Auth config JSON should not be null", authConfig.getAuthConfig());
        Assert.assertNotNull("Login page URL should not be null", authConfig.getLoginPageUrl());
        Assert.assertTrue("Login page URL should contain correct URL",
                authConfig.getLoginPageUrl().contains(ALTERNATE_MY_DOMAIN_ENDPOINT));
    }

    @Test
    public void testGetNoAuthConfig() {
        final AuthConfigUtil.MyDomainAuthConfig authConfig = AuthConfigUtil.getMyDomainAuthConfig(SANDBOX_ENDPOINT);
        Assert.assertNull("Auth config should be null", authConfig);
    }

    @Test(timeout = 5_000)
    public void testBroadcastSucceeds() throws ExecutionException, InterruptedException {
        testBroadcast(MY_DOMAIN_ENDPOINT, true);
    }

    @Test(timeout = 5_000)
    public void testBroadcastFails() throws ExecutionException, InterruptedException {
        testBroadcast(SANDBOX_ENDPOINT, false);
    }

    private void testBroadcast(String endpoint, Boolean expected) throws InterruptedException, ExecutionException {
        final TestBroadcastReceiver receiver = new TestBroadcastReceiver();
        SalesforceSDKManager.getInstance().getAppContext().registerReceiver(receiver,
                new IntentFilter(AuthConfigUtil.AUTH_CONFIG_COMPLETE_INTENT_ACTION));
        try {
            AuthConfigUtil.getMyDomainAuthConfig(endpoint);

            final Intent intent = receiver.getIntent().get();
            Assert.assertTrue("The intent extra should be set", intent.hasExtra(AuthConfigUtil.WAS_REQUEST_SUCCESSFUL_EXTRA));

            final boolean extra = intent.getBooleanExtra(AuthConfigUtil.WAS_REQUEST_SUCCESSFUL_EXTRA, !expected);
            if (expected) {
                Assert.assertTrue("The auth config request should succeed", extra);
            } else {
                Assert.assertFalse("The auth config request should fail", extra);
            }
        } finally {
            SalesforceSDKManager.getInstance().getAppContext().unregisterReceiver(receiver);
        }
    }
}
