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
package com.salesforce.samples.vfconnector;

import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.util.HybridInstrumentationTestCase;

/**
 * Tests for VFConnector
 */
public class VFConnectorTest extends HybridInstrumentationTestCase {

	/**
	 * Load app and check the body of the webview
	 * @throws Exception
	 */
	public void testLoad() throws Exception {
		String bodyHTML = getHTML("document.body");
		assertTrue("Page should contain 'This is your new Page'", bodyHTML.contains("This is your new Page"));
	}

	/**
	 * Load app and check the user agent of the webview
	 * @throws Exception
	 */
	public void testUserAgentOfWebView() throws Exception {
		String userAgent = gapWebView.getSettings().getUserAgentString();
		assertTrue("User agent should start with SalesforceMobileSDK/<version>", userAgent.startsWith("SalesforceMobileSDK/" + ForceApp.SDK_VERSION));
		assertTrue("User agent should contain VFConnector/1.0 Hybrid", userAgent.contains("VFConnector/1.0 Hybrid"));
	}
	
	/**
	 * Check the user agent used by http access
	 */
	public void testUserAgentOfHttpAccess() {
		String userAgent = HttpAccess.DEFAULT.getUserAgent();
		assertTrue("User agent should start with SalesforceMobileSDK/<version>", userAgent.startsWith("SalesforceMobileSDK/" + ForceApp.SDK_VERSION));
		assertTrue("User agent should contain VFConnector/1.0 Hybrid", userAgent.contains("VFConnector/1.0 Hybrid"));
	}
}
