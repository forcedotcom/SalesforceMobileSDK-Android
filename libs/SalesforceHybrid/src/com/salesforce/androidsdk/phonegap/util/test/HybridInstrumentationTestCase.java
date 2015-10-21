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
package com.salesforce.androidsdk.util.test;

import android.util.Log;
import android.webkit.WebView;

import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.Event;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.test.EventsListenerQueue.BlockForEvent;

/**
 * Super class for tests of hybrid application
 */
public abstract class HybridInstrumentationTestCase extends ForceAppInstrumentationTestCase {
	
	protected static String HYBRID_CONTAINER = "hybridContainer";
	protected WebView gapWebView;
	
	protected void login() {
		super.login();
		waitForEvent(EventType.GapWebViewPageFinished);
	}

	protected void launchMainActivity() {
		prepareBridge();
		super.launchMainActivity();
	}

	protected void prepareBridge() {
		// Bridge must be installed before anything gets loaded in the webview
		eq.registerBlock(new BlockForEvent(EventType.GapWebViewCreateComplete) {
			@Override
			public void run(Event evt) {
				Log.i("HybridInstrumentationTestCase.prepareBridge", "addingJavaScriptInterfacce hybridContainer");
				gapWebView = (WebView) evt.getData();
				gapWebView.addJavascriptInterface(new Object() {
					@SuppressWarnings("unused")
					public void send(String msg) {
						EventsObservable.get()
								.notifyEvent(EventType.Other, msg);
					}
				}, HYBRID_CONTAINER);
			}
		});
	 }

	protected void interceptExistingJavaScriptFunction(WebView webView, String functionName) {
		sendJavaScript(gapWebView, "var old" + functionName + "=" +  functionName);
		sendJavaScript(gapWebView, functionName + " = function() { console.log(\"Intercepting " + functionName + "\"); " + HYBRID_CONTAINER + ".send(JSON.stringify(arguments)); old" + functionName + ".apply(null, arguments)}");
	}
    	
	protected String getHTML(String domElt) {
		sendJavaScript(gapWebView, HYBRID_CONTAINER + ".send(" + domElt + ".outerHTML)");
		return (String) waitForEvent(EventType.Other).getData();		
	}
}
