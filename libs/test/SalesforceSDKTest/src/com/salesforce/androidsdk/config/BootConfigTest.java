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

import android.test.InstrumentationTestCase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

/**
 * Tests for BootConfig.
 *
 * @author khawkins
 */

public class BootConfigTest extends InstrumentationTestCase {

    @Override
    public void setUp() throws Exception {

    }

    @Override
    public void tearDown() throws Exception {

    }

    public void testNoBootConfig() {
        try {
            BootConfig.validateBootConfig(null);
            fail("Validation should fail with no boot config.");
        } catch (BootConfig.BootConfigException e) {
            // Expected
        }
    }

    public void testAbsoluteStartPage() {
        BootConfig config = createHybridBootConfig(true, "https://www.salesforce.com/test.html", true, null);
        validateBootConfig(config, "Validation should fail with absolute URL start page.");
    }

    public void testRemoteDeferredAuthNoUnauthenticatedStartPage() {
        BootConfig config = createHybridBootConfig(false, "/apex/TestPage", false, null);
        validateBootConfig(config, "Validation should fail with no unauthenticatedStartPage value in remote deferred auth.");
    }

    public void testRelativeUnauthenticatedStartPage() {
        BootConfig config = createHybridBootConfig(false, "/apex/TestPage", false, "/RelativeStartPage.html");
        validateBootConfig(config, "Validation should fail with relative unauthenticatedStartPage value.");
    }

    private void validateBootConfig(BootConfig config, String errorMessage) {
        assertNotNull("Boot config should not be null.", config);
        try {
            BootConfig.validateBootConfig(config);
            fail(errorMessage);
        } catch (BootConfig.BootConfigException e) {
            // Expected
        }
    }

    private BootConfig createHybridBootConfig(boolean isLocal, String startPage, boolean shouldAuthenticate, String unauthenticatedStartPage) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("remoteAccessConsumerKey", "TestConsumerKey");
            obj.put("oauthRedirectURI", "testRedirect:///uri/test");
            obj.put("oauthScopes", new JSONArray(Arrays.asList(new String[] { "api", "web" })));
            obj.put("isLocal", isLocal);
            obj.put("startPage", startPage);
            obj.put("errorPage", "TestErrorPage.html");
            obj.put("shouldAuthenticate", shouldAuthenticate);
            obj.put("attemptOfflineLoad", true);
            obj.put("unauthenticatedStartPage", unauthenticatedStartPage);
            return new BootConfig(obj, true);
        } catch (JSONException e) {
            fail("Error creating JSON boot config: " + e.getMessage());
            return null;
        }
    }
}
