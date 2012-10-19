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

import java.util.HashMap;
import java.util.Map;

import org.apache.cordova.CordovaChromeClient;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaWebViewClient;
import org.apache.cordova.DroidGap;
import org.json.JSONObject;

import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebSettings;

import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.auth.HttpAccess.NoNetworkException;
import com.salesforce.androidsdk.phonegap.BootConfig;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestClient.AsyncRequestCallback;
import com.salesforce.androidsdk.rest.RestClient.ClientInfo;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.security.PasscodeManager;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

/**
 * Class that defines the main activity for a PhoneGap-based application.
 */
public class SalesforceDroidGapActivity extends DroidGap {

    // Keys in credentials map
    private static final String USER_AGENT = "userAgent";
    private static final String INSTANCE_URL = "instanceUrl";
    private static final String LOGIN_URL = "loginUrl";
    private static final String IDENTITY_URL = "identityUrl";
    private static final String CLIENT_ID = "clientId";
    private static final String ORG_ID = "orgId";
    private static final String USER_ID = "userId";
    private static final String REFRESH_TOKEN = "refreshToken";
    private static final String ACCESS_TOKEN = "accessToken";
	
	// The URL to the bootstrap page for hybrid apps.
    public static final String BOOTSTRAP_START_PAGE = "file:///android_asset/www/bootstrap.html";

    // Used in refresh REST call
    private static final String API_VERSION = "v26.0";
	
    // For periodic auto-refresh - every 10 minutes
    private static final long AUTO_REFRESH_PERIOD_MILLISECONDS = 10*60*1000;

    // Min refresh interval (needs to be shorter than shortest session setting)
    private static final int MIN_REFRESH_INTERVAL_MILLISECONDS = 10*60*1000; // 10 minutes

    private long lastRefreshTime = -1;
    private Handler periodicAutoRefreshHandler;
    private PeriodicAutoRefresher periodicAutoRefresher;
    private PasscodeManager passcodeManager;
	private BootConfig bootconfig;
    private RestClient client;
	

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		// Get bootconfig
		bootconfig = BootConfig.getBootConfig(this);
		
        // Passcode manager
        passcodeManager = ForceApp.APP.getPasscodeManager();

        // Ensure we have a CookieSyncManager
        CookieSyncManager.createInstance(this);

		// Let observers know
		EventsObservable.get().notifyEvent(EventType.MainActivityCreateComplete, this);

        // Load bootstrap
		super.loadUrl(getStartPageUrl());

        // Periodic auto-refresh
    	if (bootconfig.autoRefreshPeriodically()) {
	        periodicAutoRefreshHandler = new Handler();
	        periodicAutoRefresher = new PeriodicAutoRefresher();
    	}
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
            final String extendedUserAgentString = uaStr + " Hybrid " + (origUserAgent == null ? "" : origUserAgent);
            Log.d("SalesforceDroidGapActivity:init", "User-Agent string: " + extendedUserAgentString);
            webSettings.setUserAgentString(extendedUserAgentString);

            // Configure HTML5 cache support.
            webSettings.setDomStorageEnabled(true);
            String cachePath = getApplicationContext().getCacheDir().getAbsolutePath();
            webSettings.setAppCachePath(cachePath);
            webSettings.setAppCacheEnabled(true);
            webSettings.setAppCacheMaxSize(1024 * 1024 * 8);
            webSettings.setAllowFileAccess(true);
            webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
            EventsObservable.get().notifyEvent(EventType.GapWebViewCreateComplete, appView);
        }
    }

    @Override
    public void onResume() {
        if (passcodeManager.onResume(this)) {
        	if (shouldAutoRefreshOnForeground()) {
                autoRefresh();
            }
        	if (bootconfig.autoRefreshPeriodically()) {
        		schedulePeriodicAutoRefresh();
    		}	
            CookieSyncManager.getInstance().startSync();
        }

        super.onResume();
    }

    @Override
    public void onPause() {
        passcodeManager.onPause(this);

        // Disable session refresh when app is backgrounded
    	if (bootconfig.autoRefreshPeriodically()) {
    		unschedulePeriodicAutoRefresh();
    	}
        CookieSyncManager.getInstance().stopSync();
        super.onPause();
    }

    @Override
    public void onUserInteraction() {
        passcodeManager.recordUserInteraction();
    }
    
    /**
     * If auto-refresh on foreground is enabled, this method will be called when the app resumes.
     * If auto-refresh periodically is enabled, this method will be called periodically.
     * Does a cheap rest call:
     * - if session has already expired, the access token will be refreshed
     * - otherwise it will get extended
     */
    public void autoRefresh() {
        Log.i("SalesforceDroidGapActivity.autoRefresh", "autoRefresh called");
        // Do a cheap rest call - access token will be refreshed if needed
        client.sendAsync(RestRequest.getRequestForResources(API_VERSION), new AsyncRequestCallback() {
            @Override
            public void onSuccess(RestRequest request, RestResponse response) {
                Log.i("SalesforceDroidGapActivity.autoRefresh", "Auto-refresh succeeded");
                updateRefreshTime();
                setSidCookies();
                Log.i("SalesforceDroidGapActivity.autoRefresh", "Firing salesforceSessionRefresh event");
                sendJavascript("cordova.fireDocumentEvent('salesforceSessionRefresh'," + getJSONCredentials(client).toString() + ");");
            }

            @Override
            public void onError(Exception exception) {
                Log.w("SalesforceDroidGapActivity.autoRefresh", "Auto-refresh failed - " + exception);

                // Only logout if we are NOT offline
                if (!(exception instanceof NoNetworkException)) {
                    ForceApp.APP.logout(SalesforceDroidGapActivity.this);
                }
            }
        });
    }
    
    /**
     * @return true if auto-refresh on foreground should take place now
     */
    public boolean shouldAutoRefreshOnForeground() {
        boolean b = bootconfig.autoRefreshOnForeground()
                && lastRefreshTime > 0 // we have authenticated
                &&  (System.currentTimeMillis() - lastRefreshTime > MIN_REFRESH_INTERVAL_MILLISECONDS);
        Log.i("SalesforceDroidGapActivity.shouldAutoRefreshOnForeground", "" + b);
        return b;
    }

   /**
    * Set cookies on cookie manager
    * @param client
    */
   private void setSidCookies() {
       Log.i("SalesforceDroidGapActivity.setSidCookies", "setting cookies");
       CookieSyncManager cookieSyncMgr = CookieSyncManager.getInstance();
       CookieManager cookieMgr = CookieManager.getInstance();
       cookieMgr.setAcceptCookie(true);  // Required to set additional cookies that the auth process will return.
       cookieMgr.removeSessionCookie();

       SystemClock.sleep(250); // removeSessionCookies kicks out a thread - let it finish
       String accessToken = client.getAuthToken();

       // Android 3.0+ clients want to use the standard .[domain] format. Earlier clients will only work
       // with the [domain] format.  Set them both; each platform will leverage its respective format.
       addSidCookieForDomain(cookieMgr,"salesforce.com", accessToken);
       addSidCookieForDomain(cookieMgr,".salesforce.com", accessToken);
       // Log.i("SalesforceOAuthPlugin.setSidCookies", "accessToken=" + accessToken);

       cookieSyncMgr.sync();
   }

   private void addSidCookieForDomain(CookieManager cookieMgr, String domain, String sid) {
       String cookieStr = "sid=" + sid;
       cookieMgr.setCookie(domain, cookieStr);
   }    
    
   /**
    * @return credentials as JSONObject
    */
   private JSONObject getJSONCredentials(RestClient client) {
       ClientInfo clientInfo = client.getClientInfo();
       Map<String, String> data = new HashMap<String, String>();
       data.put(ACCESS_TOKEN, client.getAuthToken());
       data.put(REFRESH_TOKEN, client.getRefreshToken());
       data.put(USER_ID, clientInfo.userId);
       data.put(ORG_ID, clientInfo.orgId);
       data.put(CLIENT_ID, clientInfo.clientId);
       data.put(LOGIN_URL, clientInfo.loginUrl.toString());
       data.put(IDENTITY_URL, clientInfo.identityUrl.toString());
       data.put(INSTANCE_URL, clientInfo.instanceUrl.toString());
       data.put(USER_AGENT, ForceApp.APP.getUserAgent());
       return new JSONObject(data);
   }
  
    /**
     * Update last refresh time to avoid un-necessary auto-refresh
     */
    private void updateRefreshTime() {
        Log.i("SalesforceDroidGapActivity.updateRefreshTime", "lastRefreshTime before: " + lastRefreshTime);
        lastRefreshTime = System.currentTimeMillis();
        Log.i("SalesforceDroidGapActivity.updateRefreshTime", "lastRefreshTime after: " + lastRefreshTime);
    }

    
    /**
     * Schedule auto-refresh runnable
     */
    protected void schedulePeriodicAutoRefresh() {
        Log.i("SalesforceDroidGapActivity.schedulePeriodicAutoRefresh", "schedulePeriodicAutoRefresh called");
        periodicAutoRefreshHandler.postDelayed(periodicAutoRefresher, AUTO_REFRESH_PERIOD_MILLISECONDS);
    }

    /**
     * Unschedule auto-refresh runnable
     */
    protected void unschedulePeriodicAutoRefresh() {
        Log.i("SalesforceDroidGapActivity.unschedulePeriodicAutoRefresh", "unschedulePeriodicAutoRefresh called");
        periodicAutoRefreshHandler.removeCallbacks(periodicAutoRefresher);
    }

    /**
     * Runnable that automatically refresh session
      */
    private class PeriodicAutoRefresher implements Runnable {
        public void run() {
            try {
                Log.i("SalesforceDroidGapActivity.PeriodicAutoRefresher.run", "run called");
                if (bootconfig.autoRefreshPeriodically()) {
                    autoRefresh();
                }
            } finally {
                schedulePeriodicAutoRefresh();
            }
        }
    }
}
