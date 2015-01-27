/*
 * Copyright (c) 2012, salesforce.com, inc.
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

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.config.BootConfig;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.test.InstrumentationTestCase;


/**
 * Tests for SDKInfoPlugin
 *
 */
public class SDKInfoPluginTest extends InstrumentationTestCase {

	/**
	 * Test for getSDKInfo
	 */
	public void testGetSDKInfo() throws NameNotFoundException, JSONException {
		Context ctx = getInstrumentation().getTargetContext();
		JSONObject sdkInfo = SDKInfoPlugin.getSDKInfo(ctx);
		BootConfig bootconfig = BootConfig.getBootConfig(ctx);
		assertEquals("Wrong app name", "SalesforceSDKTest", sdkInfo.getString("appName"));
		assertEquals("Wrong app version", "1.0", sdkInfo.getString("appVersion"));
		List<String> sdkInfoPlugins = toList(sdkInfo.getJSONArray("forcePluginsAvailable"));
		assertEquals("Wrong number of plugins", 3, sdkInfoPlugins.size());
		assertTrue("oauth plugin should have been returned", sdkInfoPlugins.contains("com.salesforce.oauth"));
		assertTrue("sdkinfo plugin should have been returned", sdkInfoPlugins.contains("com.salesforce.sdkinfo"));
		assertTrue("sfaccountmanager plugin should have been returned", sdkInfoPlugins.contains("com.salesforce.sfaccountmanager"));
		assertEquals("Wrong version", SalesforceSDKManager.SDK_VERSION, sdkInfo.getString("sdkVersion"));
	
		JSONObject sdkInfoBootConfig = sdkInfo.getJSONObject("bootConfig");
		assertEquals("Wrong bootconfig shouldAuthenticate", bootconfig.shouldAuthenticate(), sdkInfoBootConfig.getBoolean("shouldAuthenticate"));
		assertEquals("Wrong bootconfig attemptOfflineLoad", bootconfig.attemptOfflineLoad(), sdkInfoBootConfig.getBoolean("attemptOfflineLoad"));
		assertEquals("Wrong bootconfig isLocal", bootconfig.isLocal(), sdkInfoBootConfig.getBoolean("isLocal"));
		List<String> sdkInfoOAuthScopes = toList(sdkInfoBootConfig.getJSONArray("oauthScopes"));
		assertEquals("Wrong bootconfig oauthScopes", 1, sdkInfoOAuthScopes.size());
		assertTrue("Wrong bootconfig oauthScopes", sdkInfoOAuthScopes.contains("api"));
		assertEquals("Wrong bootconfig oauthRedirectURI", bootconfig.getOauthRedirectURI(), sdkInfoBootConfig.getString("oauthRedirectURI"));
		assertEquals("Wrong bootconfig remoteAccessConsumerKey", bootconfig.getRemoteAccessConsumerKey(), sdkInfoBootConfig.getString("remoteAccessConsumerKey"));
		assertEquals("Wrong bootconfig androidPushNotificationClientId", bootconfig.getPushNotificationClientId(), sdkInfoBootConfig.getString("androidPushNotificationClientId"));
		assertEquals("Wrong bootconfig startPage", "", sdkInfoBootConfig.optString("startPage")); // this is a native app
		assertEquals("Wrong bootconfig errorPage", "", sdkInfoBootConfig.optString("errorPage")); // this is a native app
	}

	/**
	 * Test for getForcePluginsFromXML
	 */
	public void testGetForcePluginsFromXML() {
		List<String> plugins = SDKInfoPlugin.getForcePluginsFromXML(getInstrumentation().getTargetContext());
		assertEquals("Wrong number of force plugins", 3, plugins.size());
		assertTrue("oauth plugin should have been returned", plugins.contains("com.salesforce.oauth"));
		assertTrue("sdkinfo plugin should have been returned", plugins.contains("com.salesforce.sdkinfo"));
		assertTrue("sfaccountmanager plugin should have been returned", plugins.contains("com.salesforce.sfaccountmanager"));
	}
	
	/**
	 * Helper method
	 * @param jsonArray
	 * @return
	 * @throws JSONException 
	 */
	private List<String> toList(JSONArray jsonArray) throws JSONException {
		List<String> list = new ArrayList<String>(jsonArray.length());
		for (int i=0; i<jsonArray.length(); i++) {
			list.add(jsonArray.getString(i));
		}
		return list;
	}
}
