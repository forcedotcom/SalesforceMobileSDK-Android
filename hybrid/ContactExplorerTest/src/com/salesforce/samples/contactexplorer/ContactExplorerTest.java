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

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityMonitor;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.test.InstrumentationTestCase;
import android.webkit.WebView;

import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.auth.OAuth2;
import com.salesforce.androidsdk.ui.LoginActivity;
import com.salesforce.androidsdk.ui.SalesforceDroidGapActivity;
import com.salesforce.androidsdk.util.EventsListenerQueue;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.Event;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

/**
 * Tests for Contact Explorer
 */
public class ContactExplorerTest extends InstrumentationTestCase {

	private int TIMEOUT = 10000; // ms
    private Instrumentation instrumentation;
	private EventsListenerQueue eq;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        instrumentation = getInstrumentation();
        
        eq = new EventsListenerQueue();
        // Wait for app initialization to complete
        if (ForceApp.APP == null) {
            eq.waitForEvent(EventType.AppCreateComplete, TIMEOUT);
        }
    }	
	
    public void testLoginFlow() throws Exception {
    	ForceApp.APP.logout(null, false);
    	waitForEvent(EventType.LogoutComplete);
    	
    	useSandbox();

		final Intent intent = new Intent(Intent.ACTION_MAIN);
    	intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    	intent.setClassName(instrumentation.getTargetContext(), ForceApp.APP.getMainActivityClass().getName());
 
    	Activity loginActivity = waitForActivity(LoginActivity.class, new Runnable() {
    		@Override public void run() { instrumentation.startActivitySync(intent); }
    	});
    	WebView loginWebView = (WebView) ((LoginActivity) loginActivity).findViewById(ForceApp.APP.getSalesforceR().idLoginWebView());
    	
    	waitForEvent(EventType.LoginWebViewPageFinished);
    	runJavaScript(loginWebView, "document.login.un.value='w@cs0.com';document.login.password.value='123456';document.login.submit();"); // login
    	waitForEvent(EventType.LoginWebViewPageFinished);
    	runJavaScript(loginWebView, "document.editPage.oaapprove.click()"); // approve

    	Activity gapActivity = waitForActivity(SalesforceDroidGapActivity.class, new Runnable() {
    		@Override public void run() { waitForEvent(EventType.GapWebViewPageFinished); }	
    	});
    	
    	WebView gapWebView = ((SalesforceDroidGapActivity) gapActivity).getWebView();
    	installBridge(gapWebView);
    	
    	runJavaScript(gapWebView, "jQuery('#link_fetch_sfdc_contacts').trigger('click')");
    	Event e = waitForEvent(EventType.Other);
    	JSONObject response = new JSONObject(e.getData());
    	
    	
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
	
	/**
	 * Setup a monitor
	 * Run action
	 * Wait for activityClass to be the front activity
	 * Remove monitor
	 * @return active activity
	 */
	private Activity waitForActivity(Class<? extends Activity> activityClass, Runnable action) {
		ActivityMonitor monitor = instrumentation.addMonitor(activityClass.getName(), null, false);
		action.run();
		Activity activity = instrumentation.waitForMonitor(monitor);
		assertEquals("Wrong activity returned", activityClass.getName(), activity.getClass().getName());
		instrumentation.removeMonitor(monitor);
		return activity;
	}

    private Event waitForEvent(EventType evt) {
    	return eq.waitForEvent(evt, TIMEOUT);
    }
  
    
    private void installBridge(WebView webView) {
    	webView.addJavascriptInterface(new Object() {
    		public void notifyEvent(String msg) {
    			EventsObservable.get().notifyEvent(new Event(EventType.Other, msg));
    		}
    	}, "containerObserver");
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
