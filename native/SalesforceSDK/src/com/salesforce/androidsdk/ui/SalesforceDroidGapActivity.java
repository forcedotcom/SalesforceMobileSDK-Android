/*
 * Copyright (c) 2011-12, salesforce.com, inc.
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.cordova.CordovaChromeClient;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaWebViewClient;
import org.apache.cordova.DroidGap;
import org.apache.cordova.api.CallbackContext;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
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
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.AccountInfoNotFoundException;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;
import com.salesforce.androidsdk.rest.ClientManager.RestClientCallback;
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
	
    // Used in refresh REST call
    private static final String API_VERSION = "v26.0";
	
    // For periodic auto-refresh - every 10 minutes
    private static final long AUTO_REFRESH_PERIOD_MILLISECONDS = 10*60*1000;

    // Min refresh interval (needs to be shorter than shortest session setting)
    private static final int MIN_REFRESH_INTERVAL_MILLISECONDS = 10*60*1000; // 10 minutes

    // Refresh handling
    private long lastRefreshTime = -1;
    private Handler periodicAutoRefreshHandler;
    private PeriodicAutoRefresher periodicAutoRefresher;
	
	// Rest client
    private RestClient client;
	private ClientManager clientManager;

	// Passcode
	private PasscodeManager passcodeManager;
    
    // Config
	private BootConfig bootconfig;

	// Web app loaded?
	private boolean webAppLoaded = false;	
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	Log.i("SalesforceDroidGapActivity.onCreate", "onCreate called");
        super.onCreate(savedInstanceState);

		// Get bootconfig
		bootconfig = BootConfig.getBootConfig(this);

		// Get clientManager
        LoginOptions loginOptions = new LoginOptions(
                null, // set by app
                ForceApp.APP.getPasscodeHash(),
                bootconfig.getOauthRedirectURI(),
                bootconfig.getRemoteAccessConsumerKey(),
                bootconfig.getOauthScopes());
        Log.i("SalesforceDroidGapActivity.onCreate", "loginOptions:" + loginOptions.toString());
        
        clientManager = new ClientManager(this, ForceApp.APP.getAccountType(), loginOptions);
		
        // Get client (if already logged in)
        try {
			client = clientManager.peekRestClient();
		} catch (AccountInfoNotFoundException e) {
			client = null;
		}
        
        // Passcode manager
        passcodeManager = ForceApp.APP.getPasscodeManager();

        // Ensure we have a CookieSyncManager
        CookieSyncManager.createInstance(this);

        // Periodic auto-refresh handler setup
    	if (bootconfig.autoRefreshPeriodically()) {
	        periodicAutoRefreshHandler = new Handler();
	        periodicAutoRefresher = new PeriodicAutoRefresher();
    	}
    	
		// Let observers know
		EventsObservable.get().notifyEvent(EventType.MainActivityCreateComplete, this);
    }

    @Override
    public void init(CordovaWebView webView, CordovaWebViewClient webViewClient, CordovaChromeClient webChromeClient) {
    	Log.i("SalesforceDroidGapActivity.init", "init called");
        super.init(webView, new SalesforceGapViewClient(this, webView), webChromeClient);
        final String uaStr = ForceApp.APP.getUserAgent();
        if (null != this.appView) {
            WebSettings webSettings = this.appView.getSettings();
            String origUserAgent = webSettings.getUserAgentString();
            final String extendedUserAgentString = uaStr + " Hybrid " + (origUserAgent == null ? "" : origUserAgent);
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
    	Log.i("SalesforceDroidGapActivity.onResume", "onResume called");    	
        if (passcodeManager.onResume(this)) {
        	// Logged in
        	if (client != null) {
        		// Web app never loaded
        		if (!webAppLoaded) {
                	Log.i("SalesforceDroidGapActivity.onResume", "Already logged in / web app never loaded");
        			loadUrl(getStartPageUrl());
        			webAppLoaded  = true;
        		}
        		// Web app already loaded
        		else {
                	Log.i("SalesforceDroidGapActivity.onResume", "Already logged in / web app already loaded");
        			if (shouldAutoRefreshOnForeground()) {
		                autoRefresh(null);
		            }
        		}
        		
	        	if (bootconfig.autoRefreshPeriodically()) {
	        		schedulePeriodicAutoRefresh();
	    		}
        	}
        	// Not logged in
        	else {
        		// Connected
            	if (ForceApp.APP.hasNetwork()) {
                	Log.i("SalesforceDroidGapActivity.onResume", "Not logged in / Connected");
            		if (bootconfig.shouldAuthenticate()) {
            			authenticate(null);
            		}
            	}
            	// Not connected
            	else {
                	Log.i("SalesforceDroidGapActivity.onResume", "Not logged in / Not connected");
            		// Not supported??
            	}
        	}
            CookieSyncManager.getInstance().startSync();
        }

        super.onResume();
    }

    @Override
    public void onPause() {
    	Log.i("SalesforceDroidGapActivity.onPause", "onPause called");
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
     * Get a RestClient and refresh the auth token
     * @param callbackContext when not null credentials/errors are sent through to callbackContext.success()/error()
     */
    public void authenticate(final CallbackContext callbackContext) {
    	Log.i("SalesforceDroidGapActivity.authenticate", "authenticate called");
    	clientManager.getRestClient(this, new RestClientCallback() {
			@Override
			public void authenticatedRestClient(RestClient client) {
				if (client == null) {
			    	Log.i("SalesforceDroidGapActivity.authenticate", "authenticatedRestClient called with null client");
					ForceApp.APP.logout(SalesforceDroidGapActivity.this);
	            }
	            else {
			    	Log.i("SalesforceDroidGapActivity.authenticate", "authenticatedRestClient called with actual client");
	            	boolean justLoggedIn = (SalesforceDroidGapActivity.this.client == null);
	                SalesforceDroidGapActivity.this.client = client;
	                
	                if (!justLoggedIn) {
	                	autoRefresh(callbackContext);
	                }
	            }
			}
    	});
    }
    
    /**
     * Does a cheap rest call:
     * - if session has already expired, the access token will be refreshed
     * - otherwise it will get extended
     * @param callbackContext when not null credentials are send back through callbackContext.success()
     *                        otherwise they are sent back through a
     */
    public void autoRefresh(final CallbackContext callbackContext) {
    	Log.i("SalesforceDroidGapActivity.autoRefresh", "autoRefresh called");
    	client.sendAsync(RestRequest.getRequestForResources(API_VERSION), new AsyncRequestCallback() {
            @Override
            public void onSuccess(RestRequest request, RestResponse response) {
            	Log.i("SalesforceDroidGapActivity.autoRefresh", "cheap api call successful");
                updateRefreshTime();
                setSidCookies();
                
                JSONObject credentials = getJSONCredentials();
                if (callbackContext != null) {
                	callbackContext.success(credentials);
                }
                else {
                	sendJavascript("cordova.fireDocumentEvent('salesforceSessionRefresh'," + credentials.toString() + ");");
                }
            }

            @Override
            public void onError(Exception exception) {
            	Log.i("SalesforceDroidGapActivity.autoRefresh", "cheap api call failed");

            	if (callbackContext != null) {
            		callbackContext.error(exception.getMessage());
            	}
            	else {
	            	// Only logout if we are NOT offline
	                if (!(exception instanceof NoNetworkException)) {
	                    ForceApp.APP.logout(SalesforceDroidGapActivity.this);
	                }
            	}
            }
        });
    }
    
    /**
     * @return true if auto-refresh on foreground should take place now
     */
    public boolean shouldAutoRefreshOnForeground() {
        boolean b = bootconfig.autoRefreshOnForeground()
                && client != null
                &&  (System.currentTimeMillis() - lastRefreshTime > MIN_REFRESH_INTERVAL_MILLISECONDS);
        Log.i("SalesforceDroidGapActivity.shouldAutoRefreshOnForeground", "" + b);
        return b;
    }
    
    /**
     * @return start page url 
     */
    public String getStartPageUrl() {
    	Log.i("SalesforceDroidGapActivity.getStartPageUrl", "getStartPageUrl called");
    	String startPage = bootconfig.getStartPage();
    	String url;
    	// Local
    	if (bootconfig.isLocal()) {
        	Log.i("SalesforceDroidGapActivity.getStartPageUrl", "local web app: " + startPage);
    		url = "file:///android_asset/www/" + startPage;
    	}
    	// Remote
    	else {
    		// Connected
    		if (ForceApp.APP.hasNetwork()) {
            	Log.i("SalesforceDroidGapActivity.getStartPageUrl", "remote web app: " + startPage);
    			url = client.getClientInfo().instanceUrl.toString() + "/secur/frontdoor.jsp?";
	    		List<NameValuePair> params = new LinkedList<NameValuePair>();
	    		params.add(new BasicNameValuePair("sid", client.getAuthToken()));
	    		params.add(new BasicNameValuePair("retURL", startPage));
	    		params.add(new BasicNameValuePair("display", "touch"));
	    		url += URLEncodedUtils.format(params, "UTF-8");
    		}
    		// Not connected
    		else {
            	Log.i("SalesforceDroidGapActivity.getStartPageUrl", "starting from cache");

    			// Should load offline
    			if (bootconfig.attemptOfflineLoad()) {
    				url = SalesforceGapViewClient.getAppHomeUrl(this);
    				if (url == null) {
    					throw new HybridAppLoadException("Remote web app never loaded cannot start while offline");
    				}
                	Log.i("SalesforceDroidGapActivity.getStartPageUrl", "remote web app cached: " + url);
    			}
    			// Should not load offline
    			else {
					throw new HybridAppLoadException("Remote web app with attemptOfflineLoad=false cannot start while offline");
    			}
    		}
    	}
    	return url;
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
   public JSONObject getJSONCredentials() {
	   if (client != null) {
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
	   else {
		   return null;
	   }
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
                    autoRefresh(null);
                }
            } finally {
                schedulePeriodicAutoRefresh();
            }
        }
    }
    
    /**
     * Exception thrown if initial web page load fails
     *
     */
    public static class HybridAppLoadException extends RuntimeException {

		public HybridAppLoadException(String msg) {
			super(msg);
		}

		private static final long serialVersionUID = 1L;
    	
    }
}