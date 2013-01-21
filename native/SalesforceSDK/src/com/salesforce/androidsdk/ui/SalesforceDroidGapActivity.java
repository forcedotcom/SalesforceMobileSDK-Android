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
package com.salesforce.androidsdk.ui;

import org.apache.cordova.CordovaChromeClient;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaWebViewClient;
import org.apache.cordova.DroidGap;

import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.webkit.CookieSyncManager;
import android.webkit.WebSettings;

import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.security.PasscodeManager;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.TokenRevocationReceiver;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

/**
 * Class that defines the main activity for a PhoneGap-based application.
 */
public class SalesforceDroidGapActivity extends DroidGap {

    /**
     * The URL to the bootstrap page for hybrid apps.
     */
    public static final String BOOTSTRAP_START_PAGE = "file:///android_asset/www/bootstrap.html";

    private PasscodeManager passcodeManager;
    private TokenRevocationReceiver tokenRevocationReceiver;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Passcode manager
        passcodeManager = ForceApp.APP.getPasscodeManager();
        tokenRevocationReceiver = new TokenRevocationReceiver(this);

        // Ensure we have a CookieSyncManager
        CookieSyncManager.createInstance(this);
        
		// Let observers know
		EventsObservable.get().notifyEvent(EventType.MainActivityCreateComplete, this);
        
        // Load bootstrap
        super.loadUrl(getStartPageUrl());
    }

    /** Returns the start page url for the application.
     * Must be overridden if you want a different start page.
     * @return Start page url of the app.
     */
    public String getStartPageUrl() {
    	return BOOTSTRAP_START_PAGE;
    }
    
    @Override
    public void init(CordovaWebView webView, CordovaWebViewClient webViewClient, CordovaChromeClient webChromeClient) {
        super.init(webView, new SalesforceGapViewClient(this, webView), webChromeClient);
        final String uaStr = ForceApp.APP.getUserAgent();
        if (null != this.appView) {
            WebSettings webSettings = this.appView.getSettings();
            String origUserAgent = webSettings.getUserAgentString();
            final String extendedUserAgentString = uaStr + (origUserAgent == null ? "" : " " + origUserAgent);
            Log.d("SalesforceDroidGapActivity:init", "User-Agent string: " + extendedUserAgentString);
            webSettings.setUserAgentString(extendedUserAgentString);

            // Configure HTML5 cache support.
            webSettings.setDomStorageEnabled(true);
            String cachePath = getApplicationContext().getCacheDir().getAbsolutePath();
            webSettings.setAppCachePath(cachePath);
            webSettings.setAppCacheEnabled(true);
            webSettings.setAppCacheMaxSize(1024 * 1024 * 8);
            webSettings.setAllowFileAccess(true);
            webSettings.setSavePassword(false);
            webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
            EventsObservable.get().notifyEvent(EventType.GapWebViewCreateComplete, appView);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    	registerReceiver(tokenRevocationReceiver, new IntentFilter(ClientManager.ACCESS_TOKEN_REVOKE_INTENT));
    	if (passcodeManager.onResume(this)) {
            CookieSyncManager.getInstance().startSync();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        passcodeManager.onPause(this);
        CookieSyncManager.getInstance().stopSync();
    	unregisterReceiver(tokenRevocationReceiver);
    }

    @Override
    public void onUserInteraction() {
        passcodeManager.recordUserInteraction();
    }
}
