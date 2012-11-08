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
package com.salesforce.samples.sfdcaccounts;

import org.json.JSONException;
import org.json.JSONObject;

import android.webkit.WebView;

import com.salesforce.androidsdk.util.EventsObservable.Event;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.HybridInstrumentationTestCase;

/**
 * Tests for ContactExplorer
 */
public class SFDCAccountsTest extends HybridInstrumentationTestCase {
	
    @Override
    public void setUp() throws Exception {
        super.setUp();
        // XXX because we wait a bit after loading the page, we end up installing the "interceptor" too late
        // interceptExistingJavaScriptFunction(gapWebView, "salesforceSessionRefreshed");
        // waitForEvent(EventType.Other); // It's only after salesforceSessionRefreshed gets called that forcetk is initialized
    }

	public void testFetchSfdcAccounts() throws Exception {
		interceptExistingJavaScriptFunction(gapWebView, "onSuccessSfdcAccounts");
		sendClick(gapWebView, "#link_fetch_sfdc_accounts");
		Event evt = waitForEvent(EventType.Other);
		validateResponse((String) evt.getData(), "Account");
	}

	public void testFetchSfdcOpportunities() throws Exception {
		interceptExistingJavaScriptFunction(gapWebView, "onSuccessSfdcOpportunities");
		sendClick(gapWebView, "#link_fetch_sfdc_opportunities");
		Event evt = waitForEvent(EventType.Other);
		validateResponse((String) evt.getData(), "Opportunity");
	}
	
	public void testLogout() throws Exception {
		sendClick(gapWebView, "#link_logout");
		waitForEvent(EventType.LogoutComplete);
		cleanupActivityFollowingLogout();
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
