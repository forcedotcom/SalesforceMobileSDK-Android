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

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.ui.LoginActivity;
import com.salesforce.androidsdk.util.EventsObservable.Event;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

/**
 * Super class for tests of an application using the Salesforce Mobile SDK
 */
public class ForceAppInstrumentationTestCase extends InstrumentationTestCase {
	
	protected int TIMEOUT = 30000; // ms

	protected EventsListenerQueue eq;
	protected Instrumentation instrumentation;

	protected Activity mainActivity;
	protected Activity loginActivity;
	
    @Override
    public void setUp() throws Exception {
        super.setUp();

        instrumentation = getInstrumentation();
        eq = new EventsListenerQueue();

        try {
        	waitForStartup();
			logout();
			SalesforceSDKManager.getInstance().getLoginServerManager().useSandbox();
			launchMainActivity();
			login();
        }
        catch (Exception e) {
        	cleanup();
        	throw e;
        }
    }

    @Override
    public void tearDown() throws Exception {
    	cleanup();
        super.tearDown();
    }

	private void cleanup() {
		if (loginActivity != null) {
    		loginActivity.finish();
    		loginActivity = null;
    	}
    	
    	if (mainActivity != null) {
    		mainActivity.finish();
    		mainActivity = null;
    	}
    	
        if (eq != null) {
            eq.tearDown();
            eq = null;
        }
	}

    protected String getTestUsername() {
    	return "readonly@cs1.mobilesdk.ee.org";
    }
    
    protected String getTestPassword() {
    	return "123456"; // shouldn't check in
    }
    
	protected void waitForStartup() {
		// Wait for app initialization to complete
	    if (SalesforceSDKManager.getInstance() == null) {
	    	waitForEvent(EventType.AppCreateComplete);
	    }
	}

	protected void logout() {
		SalesforceSDKManager.getInstance().logout(null, false);
		waitForEvent(EventType.LogoutComplete);
	}

	protected void login() {
		WebView loginWebView = (WebView) waitForEvent(EventType.AuthWebViewCreateComplete).getData();
		loginActivity = (LoginActivity) waitForEvent(EventType.LoginActivityCreateComplete).getData();
		waitForEvent(EventType.AuthWebViewPageFinished);
		sendJavaScript(loginWebView, "document.login.un.value='" + getTestUsername() + "';document.login.password.value='" + getTestPassword() + "';document.login.submit();"); // login
		waitForEvent(EventType.AuthWebViewPageFinished);
		sendJavaScript(loginWebView, "document.editPage.oaapprove.click()"); // approve
	}

	protected void launchMainActivity() {
		final Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.setClassName(instrumentation.getTargetContext(), SalesforceSDKManager.getInstance().getMainActivityClass().getName());
		mainActivity = instrumentation.startActivitySync(intent);
	}

	protected Event waitForEvent(EventType type) {
		Log.i("ForceAppInstrumentationTestCase.waitForEvent", "Waiting for " + type);
		Event evt = eq.waitForEvent(type, getWaitTimeout());
		
    	if (type == EventType.AuthWebViewPageFinished || type == EventType.GapWebViewPageFinished) {
    		waitSome();
    		// When page finished is fired, DOM is not ready :-(
    	}
		Log.i("ForceAppInstrumentationTestCase.waitForEvent", "Got " + evt.getType());
    	return evt;
    }

	private int getWaitTimeout() {
		return TIMEOUT;
	}
  
    protected void waitSome() {
        try {
            Thread.sleep(2000);
        }
        catch (InterruptedException e) {
            fail("Test interrupted");
        }
    }
	
	protected void sendJavaScript(final WebView webView, final String js) {
    	try {
			runTestOnUiThread(new Runnable() {
				@Override
				public void run() {
					Log.i("ForceAppInstrumentationTestCase:sendJavaScript", js);
					webView.loadUrl("javascript:" + js); // TODO proper escaping
				}				
			});
		} catch (Throwable e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
    }
	
    protected void clickView(final View v) {
        try {
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    v.performClick();
                }
            });
        }
        catch (Throwable t) {
            fail("Failed to click view " + v);
        }
    }

	protected void cleanupActivityFollowingLogout() {
		Activity activity = (Activity) waitForEvent(EventType.MainActivityCreateComplete).getData();
		activity.finish();
	}	
}
