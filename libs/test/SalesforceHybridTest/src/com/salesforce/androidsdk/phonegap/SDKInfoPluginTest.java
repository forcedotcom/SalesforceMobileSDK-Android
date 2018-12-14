/*
 * Copyright (c) 2012-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.phonegap;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.config.BootConfig;
import com.salesforce.androidsdk.phonegap.plugin.SDKInfoPlugin;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for SDKInfoPlugin.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class SDKInfoPluginTest {

	/**
	 * Test for getSDKInfo
	 */
    @Test
	public void testGetSDKInfo() throws NameNotFoundException, JSONException {
		Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
		JSONObject sdkInfo = SDKInfoPlugin.getSDKInfo(ctx);
		BootConfig bootconfig = BootConfig.getBootConfig(ctx);
		Assert.assertEquals("Wrong app name", "SalesforceHybridTest", sdkInfo.getString("appName"));
        Assert.assertEquals("Wrong app version", "1.0", sdkInfo.getString("appVersion"));
        List<String> sdkInfoPlugins = toList(sdkInfo.getJSONArray("forcePluginsAvailable"));
        Assert.assertEquals("Wrong number of plugins", 7, sdkInfoPlugins.size());
        Assert.assertTrue("network plugin should have been returned", sdkInfoPlugins.contains("com.salesforce.network"));
        Assert.assertTrue("oauth plugin should have been returned", sdkInfoPlugins.contains("com.salesforce.oauth"));
        Assert.assertTrue("sdkinfo plugin should have been returned", sdkInfoPlugins.contains("com.salesforce.sdkinfo"));
        Assert.assertTrue("sfaccountmanager plugin should have been returned", sdkInfoPlugins.contains("com.salesforce.sfaccountmanager"));
        Assert.assertTrue("smartstore plugin should have been returned", sdkInfoPlugins.contains("com.salesforce.smartstore"));
        Assert.assertTrue("smartsync plugin should have been returned", sdkInfoPlugins.contains("com.salesforce.smartsync"));
        Assert.assertTrue("testrunner plugin should have been returned", sdkInfoPlugins.contains("com.salesforce.testrunner"));
        Assert.assertEquals("Wrong version", SalesforceSDKManager.SDK_VERSION, sdkInfo.getString("sdkVersion"));
		JSONObject sdkInfoBootConfig = sdkInfo.getJSONObject("bootConfig");
        Assert.assertEquals("Wrong bootconfig shouldAuthenticate", bootconfig.shouldAuthenticate(), sdkInfoBootConfig.getBoolean("shouldAuthenticate"));
        Assert.assertEquals("Wrong bootconfig attemptOfflineLoad", bootconfig.attemptOfflineLoad(), sdkInfoBootConfig.getBoolean("attemptOfflineLoad"));
        Assert.assertEquals("Wrong bootconfig isLocal", bootconfig.isLocal(), sdkInfoBootConfig.getBoolean("isLocal"));
		List<String> sdkInfoOAuthScopes = toList(sdkInfoBootConfig.getJSONArray("oauthScopes"));
        Assert.assertEquals("Wrong bootconfig oauthScopes", 1, sdkInfoOAuthScopes.size());
        Assert.assertTrue("Wrong bootconfig oauthScopes", sdkInfoOAuthScopes.contains("api"));
        Assert.assertEquals("Wrong bootconfig oauthRedirectURI", bootconfig.getOauthRedirectURI(), sdkInfoBootConfig.getString("oauthRedirectURI"));
        Assert.assertEquals("Wrong bootconfig remoteAccessConsumerKey", bootconfig.getRemoteAccessConsumerKey(), sdkInfoBootConfig.getString("remoteAccessConsumerKey"));
        try {
            sdkInfoBootConfig.getString("androidPushNotificationClientId");
            Assert.fail("Wrong bootconfig having androidPushNotificationClientId field");
        } catch (Exception ex) {

            // don't do anything since the exception is expected
        }
        Assert.assertEquals("Wrong bootconfig startPage", "index.html", sdkInfoBootConfig.optString("startPage"));
        Assert.assertEquals("Wrong bootconfig errorPage", "error.html", sdkInfoBootConfig.optString("errorPage"));

	}

	/**
	 * Test for getForcePluginsFromXML
	 */
	@Test
	public void testGetForcePluginsFromXML() {
		List<String> plugins = SDKInfoPlugin.getForcePluginsFromXML(InstrumentationRegistry.getInstrumentation().getTargetContext());
        Assert.assertEquals("Wrong number of force plugins", 7, plugins.size());
        Assert.assertTrue("network plugin should have been returned", plugins.contains("com.salesforce.network"));
        Assert.assertTrue("oauth plugin should have been returned", plugins.contains("com.salesforce.oauth"));
        Assert.assertTrue("sdkinfo plugin should have been returned", plugins.contains("com.salesforce.sdkinfo"));
        Assert.assertTrue("sfaccountmanager plugin should have been returned", plugins.contains("com.salesforce.sfaccountmanager"));
        Assert.assertTrue("smartstore plugin should have been returned", plugins.contains("com.salesforce.smartstore"));
        Assert.assertTrue("smartsync plugin should have been returned", plugins.contains("com.salesforce.smartsync"));
        Assert.assertTrue("testrunner plugin should have been returned", plugins.contains("com.salesforce.testrunner"));
	}

	private List<String> toList(JSONArray jsonArray) throws JSONException {
		List<String> list = new ArrayList<String>(jsonArray.length());
		for (int i = 0; i < jsonArray.length(); i++) {
			list.add(jsonArray.getString(i));
		}
		return list;
	}
}
