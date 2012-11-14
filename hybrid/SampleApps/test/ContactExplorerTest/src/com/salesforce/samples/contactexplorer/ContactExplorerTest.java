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
package com.salesforce.samples.contactexplorer;

import org.json.JSONException;
import org.json.JSONObject;

import android.webkit.WebView;

import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.util.EventsObservable.Event;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.HybridInstrumentationTestCase;

/**
 * Tests for ContactExplorer
 */
public class ContactExplorerTest extends HybridInstrumentationTestCase {

	public void testFetchSfdcAccounts() throws Exception {
		interceptExistingJavaScriptFunction(gapWebView, "onSuccessSfdcAccounts");
		sendClick(gapWebView, "#link_fetch_sfdc_accounts");
		Event evt = waitForEvent(EventType.Other);
		validateResponse((String) evt.getData(), "Account");
	}

	public void testFetchSfdcContacts() throws Exception {
		interceptExistingJavaScriptFunction(gapWebView, "onSuccessSfdcContacts");
		sendClick(gapWebView, "#link_fetch_sfdc_contacts");
		Event evt = waitForEvent(EventType.Other);
		validateResponse((String) evt.getData(), "Contact");
	}
	
	public void testLogout() throws Exception {
		sendClick(gapWebView, "#link_logout");
		waitForEvent(EventType.LogoutComplete);
		cleanupActivityFollowingLogout();
	}

	/**
	 * Load app and check the user agent of the webview
	 * @throws Exception
	 */
	public void testUserAgentOfWebView() throws Exception {
		String userAgent = gapWebView.getSettings().getUserAgentString();
		assertTrue("User agent should start with SalesforceMobileSDK/<version>", userAgent.startsWith("SalesforceMobileSDK/" + ForceApp.SDK_VERSION));
		assertTrue("User agent should contain ContactExplorer/1.0 Hybrid", userAgent.contains("ContactExplorer/1.0 Hybrid"));
	}
	
	/**
	 * Check the user agent used by http access
	 */
	public void testUserAgentOfHttpAccess() {
		String userAgent = HttpAccess.DEFAULT.getUserAgent();
		assertTrue("User agent should start with SalesforceMobileSDK/<version>", userAgent.startsWith("SalesforceMobileSDK/" + ForceApp.SDK_VERSION));
		assertTrue("User agent should contain ContactExplorer/1.0 Hybrid", userAgent.contains("ContactExplorer/1.0 Hybrid"));
	}	

	private void validateResponse(String data, String expectedType) throws JSONException {
		JSONObject response = (new JSONObject(data)).getJSONObject("0"); // we get the arguments dictionary back from javascript
		assertTrue("response should have records", response.has("records"));
		JSONObject record = response.getJSONArray("records").getJSONObject(0);
		assertEquals("record should be an " + expectedType, expectedType, record.getJSONObject("attributes").getString("type"));
	}

    private void sendClick(WebView webView, String target) {
		sendJavaScript(webView, "jQuery('" + target + "').trigger('click')");
	}

}
