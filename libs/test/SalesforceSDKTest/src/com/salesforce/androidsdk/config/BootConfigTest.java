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
package com.salesforce.androidsdk.config;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.salesforce.androidsdk.TestForceApp;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.auth.OAuth2;
import com.salesforce.androidsdk.rest.ApiVersionStrings;
import com.salesforce.androidsdk.util.test.TestCredentials;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

import okhttp3.Request;
import okhttp3.Response;

/**
 * Tests for BootConfig.
 *
 * @author khawkins
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class BootConfigTest {

    private static final String BOOTCONFIG_ASSETS_PATH_PREFIX = "www" + System.getProperty("file.separator");
    private Context testContext;

    @Before
    public void setUp() throws Exception {
        testContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    @After
    public void tearDown() throws Exception {
        testContext = null;
    }

    @Test
    public void testNoBootConfig() {
        try {
            BootConfig.validateBootConfig(null);
            Assert.fail("Validation should fail with no boot config.");
        } catch (BootConfig.BootConfigException e) {
            // Expected
        }
    }

    @Test
    public void testAbsoluteStartPage() {
        BootConfig config = BootConfig.getHybridBootConfig(testContext, BOOTCONFIG_ASSETS_PATH_PREFIX + "bootconfig_absoluteStartPage.json");
        validateBootConfig(config, "Validation should fail with absolute URL start page.");
    }

    @Test
    public void testRemoteDeferredAuthNoUnauthenticatedStartPage() {
        BootConfig config = BootConfig.getHybridBootConfig(testContext, BOOTCONFIG_ASSETS_PATH_PREFIX + "bootconfig_remoteDeferredAuthNoUnauthenticatedStartPage.json");
        validateBootConfig(config, "Validation should fail with no unauthenticatedStartPage value in remote deferred auth.");
    }

    @Test
    public void testRelativeUnauthenticatedStartPage() {
        BootConfig config = BootConfig.getHybridBootConfig(testContext, BOOTCONFIG_ASSETS_PATH_PREFIX + "bootconfig_relativeUnauthenticatedStartPage.json");
        validateBootConfig(config, "Validation should fail with relative unauthenticatedStartPage value.");
    }

    /**
     * Test overriding Token Endpoint.
     *
     * Note:  This is here and not in OAuth2Tests because that would require tokenEndpointUrl to be public (at
     * least in Java) due to how @VisibleForTesting works.
     */
    @Test
    public void testOverridingTokenEndpoint() throws URISyntaxException, OAuth2.OAuthFailedException, IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        // Setup
        final Application app = Instrumentation.newApplication(TestForceApp.class, testContext);
        InstrumentationRegistry.getInstrumentation().callApplicationOnCreate(app);
        TestCredentials.init(testContext);
        HttpAccess httpAccess = new HttpAccess(null, "dummy-agent");

        // Modify config
        BootConfig config = BootConfig.getBootConfig(testContext);
        config.tokenEndpointUrl = TestCredentials.LOGIN_URL;
        URI badUri = new URI("https://google.com");

        // Make a refresh call with a URI that should fail (unless overridden)
        OAuth2.TokenEndpointResponse refreshResponse = OAuth2.refreshAuthToken(httpAccess,badUri,
                TestCredentials.CLIENT_ID, TestCredentials.REFRESH_TOKEN, null);
        Assert.assertNotNull("Auth token should not be null", refreshResponse.authToken);

        // Let's try it out.
        Request request = new Request.Builder()
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + refreshResponse.authToken)
                .url(TestCredentials.INSTANCE_URL + "/services/data/" + ApiVersionStrings.VERSION_NUMBER)
                .get()
                .build();
        Response resourcesResponse = httpAccess.getOkHttpClient().newCall(request).execute();
        Assert.assertEquals("HTTP response status code should have been 200 (OK)", HttpURLConnection.HTTP_OK, resourcesResponse.code());
    }

    private void validateBootConfig(BootConfig config, String errorMessage) {
        Assert.assertNotNull("Boot config should not be null.", config);
        try {
            BootConfig.validateBootConfig(config);
            Assert.fail(errorMessage);
        } catch (BootConfig.BootConfigException e) {
            // Expected
        }
    }
}
