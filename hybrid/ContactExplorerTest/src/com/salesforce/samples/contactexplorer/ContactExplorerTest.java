/*
 * Copyright (c) 2011, salesforce.com, inc.
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

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityMonitor;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.webkit.WebView;

import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.ui.LoginActivity;
import com.salesforce.androidsdk.ui.SalesforceDroidGapActivity;
import com.salesforce.androidsdk.util.EventsListenerQueue;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

/**
 * Tests for Contact Explorer
 */
public class ContactExplorerTest extends InstrumentationTestCase {

	private int TIMEOUT = 10000; // ms
    private EventsListenerQueue eq;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        eq = new EventsListenerQueue();
        // Wait for app initialization to complete
        if (ForceApp.APP == null) {
            eq.waitForEvent(EventType.AppCreateComplete, TIMEOUT);
        }
    }	
	
    public void testLoginFlow() {
    	Instrumentation instrumentation = getInstrumentation();
    	ActivityMonitor monitor = instrumentation.addMonitor(LoginActivity.class.getName(), null, false);
    	
    	// Start the authentication activity as the first activity...
    	Intent intent = new Intent(Intent.ACTION_MAIN);
    	intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    	intent.setClassName(instrumentation.getTargetContext(), SalesforceDroidGapActivity.class.getName());
    	instrumentation.startActivitySync(intent);
    	
    	Activity activity = getInstrumentation().waitForMonitor(monitor);
    	WebView webView = (WebView) ((LoginActivity) activity).findViewById(ForceApp.APP.getSalesforceR().idLoginWebView());
    	waitForEvent(EventType.LoginWebViewPageFinished);
    	webView.loadUrl("javascript:document.login.un.value='w@cs0.com';document.login.password.value='123456';document.login.submit();"); // login
    	waitForEvent(EventType.LoginWebViewPageFinished);
    	webView.loadUrl("javascript:document.editPage.oaapprove.click()"); // approve
    	waitForEvent(EventType.GapWebViewPageFinished);
    }
    
    private void waitForEvent(EventType evt) {
    	eq.waitForEvent(evt, TIMEOUT);
    }
  
}
