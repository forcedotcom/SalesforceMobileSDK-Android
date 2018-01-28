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

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

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

    private static final String SSO_ENDPOINT = "https://mobsdktest.my.salesforce.com";
    private static final String PROD_ENDPOINT = "https://login.salesforce.com";
    private static final String FORWARD_SLASH = "/";

    @Test
    public void testGetAuthConfigWithoutForwardSlash() {
        final AuthConfigUtil.SSOAuthConfig authConfig = AuthConfigUtil.getSSOAuthConfig(SSO_ENDPOINT);
        Assert.assertNotNull("Auth config should not be null", authConfig);
        Assert.assertNotNull("Auth config JSON should not be null", authConfig.getAuthConfig());
    }

    @Test
    public void testGetAuthConfigWithForwardSlash() {
        final AuthConfigUtil.SSOAuthConfig authConfig = AuthConfigUtil.getSSOAuthConfig(SSO_ENDPOINT + FORWARD_SLASH);
        Assert.assertNotNull("Auth config should not be null", authConfig);
        Assert.assertNotNull("Auth config JSON should not be null", authConfig.getAuthConfig());
    }

    @Test
    public void testGetNoAuthConfig() {
        final AuthConfigUtil.SSOAuthConfig authConfig = AuthConfigUtil.getSSOAuthConfig(PROD_ENDPOINT);
        Assert.assertNull("Auth config should be null", authConfig);
    }
}
