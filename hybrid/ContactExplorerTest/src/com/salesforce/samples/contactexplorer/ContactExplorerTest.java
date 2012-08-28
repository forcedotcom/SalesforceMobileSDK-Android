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

import org.json.JSONObject;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.test.InstrumentationTestCase;
import android.webkit.WebView;

import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.auth.OAuth2;
import com.salesforce.androidsdk.ui.LoginActivity;
import com.salesforce.androidsdk.util.EventsListenerQueue;
import com.salesforce.androidsdk.util.EventsListenerQueue.BlockForEvent;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.Event;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

/**
 * Tests for Contact Explorer
 */
public class ContactExplorerTest extends InstrumentationTestCase {

	private int TIMEOUT = 15000; // ms
    private Instrumentation instrumentation;
	private EventsListenerQueue eq;
	private WebView gapWebView;
	
	
    @Override
    public void setUp() throws Exception {
        super.setUp();

        instrumentation = getInstrumentation();
        eq = new EventsListenerQueue();

        waitForStartup();
    	logout();
    	useSandbox();
    	prepareBridge();
    	launchMainActivity();
    
    }

	public void testLoginFlow() throws Exception {
		WebView loginWebView = (WebView) waitForEvent(EventType.AuthWebViewCreateComplete).getData();
		waitForEvent(EventType.AuthWebViewPageFinished);
		runJavaScript(loginWebView, "document.login.un.value='w@cs0.com';document.login.password.value='123456';document.login.submit();"); // login
		waitForEvent(EventType.AuthWebViewPageFinished);
		//runJavaScript(loginWebView, "document.editPage.oaapprove.click()"); // approve
		runJavaScript(loginWebView, "document.editPage[6].click()"); // approve
		waitForEvent(EventType.GapWebViewPageFinished);
		runJavaScript(gapWebView, "jQuery('#link_fetch_sfdc_contacts').trigger('click')");
		Event evtWithResponse = waitForEvent(EventType.Other);
		JSONObject response = new JSONObject((String) evtWithResponse.getData());
		
	}

	private void waitForStartup() {
		// Wait for app initialization to complete
        if (ForceApp.APP == null) {
            eq.waitForEvent(EventType.AppCreateComplete, TIMEOUT);
        }
	}	
	
    private void logout() {
		ForceApp.APP.logout(null, false);
		waitForEvent(EventType.LogoutComplete);
	}

	private void useSandbox() {
		// Our username/password is for a sandbox org
		SharedPreferences settings = instrumentation.getTargetContext().getSharedPreferences(
	            LoginActivity.SERVER_URL_PREFS_SETTINGS,
	            Context.MODE_PRIVATE);
	    SharedPreferences.Editor editor = settings.edit();
	    editor.putString(LoginActivity.SERVER_URL_CURRENT_SELECTION, OAuth2.SANDBOX_LOGIN_URL);
	    editor.commit();
	}

	private void launchMainActivity() {
		final Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.setClassName(instrumentation.getTargetContext(), ForceApp.APP.getMainActivityClass().getName());
		instrumentation.startActivitySync(intent);
	}

	private void prepareBridge() {
			// Bridge must be installed before anything gets loaded in the webview
			eq.registerBlock(new BlockForEvent(EventType.GapWebViewCreateComplete) {
				@Override
				public void run(Event evt) {
					gapWebView = (WebView) evt.getData();
					gapWebView.addJavascriptInterface(new Object() {
						@SuppressWarnings("unused")
						public void notifyEvent(String msg) {
							EventsObservable.get()
									.notifyEvent(EventType.Other, msg);
						}
					}, "containerObserver");
				}
			});
	 }

	private Event waitForEvent(EventType evt) {
    	return eq.waitForEvent(evt, TIMEOUT);
    }
  
    
    private void runJavaScript(final WebView webView, final String js) {
    	try {
			runTestOnUiThread(new Runnable() {
				@Override
				public void run() {
					webView.loadUrl("javascript:" + js); // TODO proper escaping
				}				
			});
		} catch (Throwable e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
    }
    	
}
