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
package com.salesforce.androidsdk.ui.sfhybrid;

import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaActivity;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaWebViewClient;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;

import android.content.IntentFilter;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.HttpAccess.NoNetworkException;
import com.salesforce.androidsdk.config.BootConfig;
import com.salesforce.androidsdk.rest.ApiVersionStrings;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.AccountInfoNotFoundException;
import com.salesforce.androidsdk.rest.ClientManager.RestClientCallback;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestClient.AsyncRequestCallback;
import com.salesforce.androidsdk.rest.RestClient.ClientInfo;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.security.PasscodeManager;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.UserSwitchReceiver;

/**
 * Class that defines the main activity for a PhoneGap-based application.
 */
public class SalesforceDroidGapActivity extends CordovaActivity {

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
    private static final String COMMUNITY_ID = "communityId";
    private static final String COMMUNITY_URL = "communityUrl";
	
    // Used in refresh REST call
    private static final String API_VERSION = ApiVersionStrings.VERSION_NUMBER;
	
	// Rest client
    private RestClient client;
	private ClientManager clientManager;
    
    // Config
	private BootConfig bootconfig;
    private PasscodeManager passcodeManager;
    private UserSwitchReceiver userSwitchReceiver;

	// Web app loaded?
	private boolean webAppLoaded = false;	
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	Log.i("SalesforceDroidGapActivity.onCreate", "onCreate called");
        super.onCreate(savedInstanceState);
        init();

		// Get bootconfig
		bootconfig = BootConfig.getBootConfig(this);

        // Get clientManager
        clientManager = buildClientManager();

        // Passcode manager
        passcodeManager = SalesforceSDKManager.getInstance().getPasscodeManager();
        userSwitchReceiver = new DroidGapUserSwitchReceiver();
        registerReceiver(userSwitchReceiver, new IntentFilter(UserAccountManager.USER_SWITCH_INTENT_ACTION));

		// Let observers know
		EventsObservable.get().notifyEvent(EventType.MainActivityCreateComplete, this);
    }

	protected ClientManager buildClientManager() {
		return new ClientManager(this, SalesforceSDKManager.getInstance().getAccountType(),
				SalesforceSDKManager.getInstance().getLoginOptions(),
				SalesforceSDKManager.getInstance().shouldLogoutWhenTokenRevoked());
	}

	@Override
	public void init()
	{
    	Log.i("SalesforceDroidGapActivity.init", "init called");
    	super.init();
    	final String uaStr = SalesforceSDKManager.getInstance().getUserAgent();
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
    		webSettings.setAllowFileAccess(true);
    		webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
    		EventsObservable.get().notifyEvent(EventType.GapWebViewCreateComplete, appView);
      	}
	}

	@Override
    protected CordovaWebViewClient makeWebViewClient(CordovaWebView webView) {
        return new SalesforceIceCreamWebViewClient(this, webView);
    }

    @Override
    public void onResume() {
        super.onResume();
    	if (passcodeManager.onResume(this)) {

            // Get client (if already logged in)
            try {
    			client = clientManager.peekRestClient();
    		} catch (AccountInfoNotFoundException e) {
    			client = null;
    		}

    		// Not logged in
        	if (client == null) {
        		onResumeNotLoggedIn();
        	}

        	// Logged in
        	else {

        		// Web app never loaded
        		if (!webAppLoaded) {
        			onResumeLoggedInNotLoaded();
        		}

        		// Web app already loaded
        		else {
                	Log.i("SalesforceDroidGapActivity.onResume", "Already logged in / web app already loaded");
        		}
        	}
        }
    }

    /**
     * Restarts the activity if the user has been switched.
     */
	private void restartIfUserSwitched() {
		if (client != null) {
            try {
    			RestClient currentClient = clientManager.peekRestClient();
    			if (currentClient != null && !currentClient.getClientInfo().userId.equals(client.getClientInfo().userId)) {
    				this.recreate();
    			}
    		} catch (AccountInfoNotFoundException e) {
            	Log.i("SalesforceDroidGapActivity.restartIfUserSwitched", "No user account found");
    		}
        }
	}

	/**
	 * Called when resuming activity and user is not authenticated
	 */
	private void onResumeNotLoggedIn() {

		// Need to be authenticated
		if (bootconfig.shouldAuthenticate()) {

			// Online
			if (SalesforceSDKManager.getInstance().hasNetwork()) {
		    	Log.i("SalesforceDroidGapActivity.onResumeNotLoggedIn", "Should authenticate / online - authenticating");
				authenticate(null);
			}

			// Offline
			else {
				Log.w("SalesforceDroidGapActivity.onResumeNotLoggedIn", "Should authenticate / offline - cannot proceed");
				loadErrorPage();
			}
		}

		// Does not need to be authenticated
		else {

			// Local
			if (bootconfig.isLocal()) {
				Log.i("SalesforceDroidGapActivity.onResumeNotLoggedIn", "Should not authenticate / local start page - loading web app");
				loadLocalStartPage();
			}

			// Remote
			else {
				Log.w("SalesforceDroidGapActivity.onResumeNotLoggedIn", "Should not authenticate / remote start page - cannot proceed");
				loadErrorPage();
			}
		}
	}

	/**
	 * Called when resuming activity and user is authenticated but webview has not been loaded yet
	 */
	private void onResumeLoggedInNotLoaded() {

		// Local
		if (bootconfig.isLocal()) {
			Log.i("SalesforceDroidGapActivity.onResumeLoggedInNotLoaded", "Local start page - loading web app");
			loadLocalStartPage();
		}

		// Remote
		else {

			// Online
			if (SalesforceSDKManager.getInstance().hasNetwork()) {
		    	Log.i("SalesforceDroidGapActivity.onResumeLoggedInNotLoaded", "Remote start page / online - loading web app");
		    	loadRemoteStartPage();
			}

			// Offline
			else {
				// Has cached version
				if (SalesforceWebViewClientHelper.hasCachedAppHome(this)) {
		        	Log.i("SalesforceDroidGapActivity.onResumeLoggedInNotLoaded", "Remote start page / offline / cached - loading cached web app");
					loadCachedStartPage();
				}

				// No cached version
				else {
		        	Log.w("SalesforceDroidGapActivity.onResumeLoggedInNotLoaded", "Remote start page / offline / not cached - cannot proceed");
		        	loadErrorPage();
				}
			}
		}
	}

    @Override
    public void onPause() {
        super.onPause();
        passcodeManager.onPause(this);
    }

    @Override
    public void onDestroy() {
    	unregisterReceiver(userSwitchReceiver);
    	super.onDestroy();
    }

    @Override
    public void onUserInteraction() {
        passcodeManager.recordUserInteraction();
    }
    
	public BootConfig getBootConfig() {
		return bootconfig;
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
					SalesforceSDKManager.getInstance().logout(SalesforceDroidGapActivity.this);
	            } else {
			    	Log.i("SalesforceDroidGapActivity.authenticate", "authenticatedRestClient called with actual client");
	                SalesforceDroidGapActivity.this.client = client;

	                /*
                     * Do a cheap REST call to refresh the access token if needed.
                     * If the login took place a while back (e.g. the already logged
                     * in application was restarted), then the returned session ID
                     * (access token) might be stale. This is not an issue if one
                     * uses exclusively RestClient for calling the server because
                     * it takes care of refreshing the access token when needed,
                     * but a stale session ID will cause the WebView to redirect
                     * to the web login.
                     */
	                SalesforceDroidGapActivity.this.client.sendAsync(RestRequest.getRequestForResources(API_VERSION), new AsyncRequestCallback() {

                        @Override
                        public void onSuccess(RestRequest request, RestResponse response) {

                        	/*
                        	 * The client instance being used here needs to be
                        	 * refreshed, to ensure we use the new access token. 
                        	 */
                        	SalesforceDroidGapActivity.this.client = SalesforceDroidGapActivity.this.clientManager.peekRestClient();
                        	setSidCookies();
                            loadVFPingPage();
                            if (callbackContext != null) {
                                callbackContext.success(getJSONCredentials());
                            }
                        }

                        @Override
                        public void onError(Exception exception) {
                        	if (callbackContext != null) {
                            	callbackContext.error(exception.getMessage());
                        	}
                        }
                    });
	            }
			}
    	});
    }
    
    /**
     * If an action causes a redirect to the login page, this method will be called.
     * It causes the session to be refreshed and reloads url through the front door.
     * @param url the page to load once the session has been refreshed.
     */
    public void refresh(final String url) {
        Log.i("SalesforceDroidGapActivity.refresh", "refresh called");
        client.sendAsync(RestRequest.getRequestForResources(API_VERSION), new AsyncRequestCallback() {
    
        	@Override
            public void onSuccess(RestRequest request, RestResponse response) {
        		Log.i("SalesforceDroidGapActivity.refresh", "Refresh succeeded");

            	/*
            	 * The client instance being used here needs to be
            	 * refreshed, to ensure we use the new access token. 
            	 */
                SalesforceDroidGapActivity.this.client = SalesforceDroidGapActivity.this.clientManager.peekRestClient();
                setSidCookies();
                loadVFPingPage();
                final String frontDoorUrl = getFrontDoorUrl(url, true);
                loadUrl(frontDoorUrl);
            }

        	@Override
            public void onError(Exception exception) {
        		Log.w("SalesforceDroidGapActivity.refresh", "Refresh failed - " + exception);

        		// Only logout if we are NOT offline
                if (!(exception instanceof NoNetworkException)) {
                	SalesforceSDKManager.getInstance().logout(SalesforceDroidGapActivity.this);
                }
            }
        });
    }        

    /**
     * Loads the VF ping page and sets cookies.
     */
    private void loadVFPingPage() {
    	if (!bootconfig.isLocal()) {
        	final ClientInfo clientInfo = SalesforceDroidGapActivity.this.client.getClientInfo();
            URI instanceUrl = null;
            if (clientInfo != null) {
            	instanceUrl = clientInfo.getInstanceUrl();
            }
            setVFCookies(instanceUrl);
    	}
    }

    /**
     * Sets VF domain cookies by loading the VF ping page on an invisible WebView.
     *
     * @param instanceUrl Instance URL.
     */
    private static void setVFCookies(URI instanceUrl) {
    	if (instanceUrl != null) {
        	final WebView view = new WebView(SalesforceSDKManager.getInstance().getAppContext());
        	view.setVisibility(View.GONE);
        	view.setWebViewClient(new WebViewClient() {

        		@Override
        		public boolean shouldOverrideUrlLoading(WebView view, String url) {
                	final CookieManager cookieMgr = CookieManager.getInstance();
                    cookieMgr.setAcceptCookie(true);
                    SalesforceSDKManager.getInstance().syncCookies();
        			return true;
        		}
        	});
        	view.loadUrl(instanceUrl.toString() + "/visualforce/session?url=/apexpages/utils/ping.apexp&autoPrefixVFDomain=true");
    	}
    }

    /**
     * Load local start page
     */
    public void loadLocalStartPage() {
    	assert bootconfig.isLocal();
    	String startPage = bootconfig.getStartPage();
    	Log.i("SalesforceDroidGapActivity.loadLocalStartPage", "loading: " + startPage);
    	loadUrl("file:///android_asset/www/" + startPage);
    	webAppLoaded = true;
    }		

    /**
     * Load remote start page (front-doored)
     */
    public void loadRemoteStartPage() {
    	assert !bootconfig.isLocal();
    	String startPage = bootconfig.getStartPage();
    	Log.i("SalesforceDroidGapActivity.loadRemoteStartPage", "loading: " + startPage);
		String url = getFrontDoorUrl(startPage, false);
		loadUrl(url);
    	webAppLoaded = true;
    }
    
    /**
     * Returns the front-doored URL of a URL passed in.
     *
     * @param url URL to be front-doored.
     * @param isAbsUrl True - if the URL should be used as is, False - otherwise.
     * @return Front-doored URL.
     */
    public String getFrontDoorUrl(String url, boolean isAbsUrl) {
		String frontDoorUrl = client.getClientInfo().getInstanceUrlAsString() + "/secur/frontdoor.jsp?";
		List<NameValuePair> params = new LinkedList<NameValuePair>();
		params.add(new BasicNameValuePair("sid", client.getAuthToken()));

		/*
		 * We need to use the absolute URL in some cases and relative URL in some
		 * other cases, because of differences between instance URL and community
		 * URL. Community URL can be custom and the logic of determining which
		 * URL to use is in the 'resolveUrl' method in 'ClientInfo'.
		 */
        url = (isAbsUrl ? url : client.getClientInfo().resolveUrl(url).toString());
		params.add(new BasicNameValuePair("retURL", url));
		params.add(new BasicNameValuePair("display", "touch"));
		frontDoorUrl += URLEncodedUtils.format(params, "UTF-8");
    	return frontDoorUrl;
    }

	/**
	 * Load cached start page
	 */
	private void loadCachedStartPage() {
		String url = SalesforceWebViewClientHelper.getAppHomeUrl(this);
		loadUrl(url);
    	webAppLoaded = true;
	}
    
    /**
     * Load error page 
     */
    public void loadErrorPage() {
    	Log.i("SalesforceDroidGapActivity.getErrorPageUrl", "getErrorPageUrl called");
    	String errorPage = bootconfig.getErrorPage();
    	Log.i("SalesforceDroidGapActivity.getErrorPageUrl", "local error page: " + errorPage);
		loadUrl("file:///android_asset/www/" + errorPage);
    }
    
   /**
    * Set cookies on cookie manager
    * @param client
    */
   private void setSidCookies() {
       Log.i("SalesforceDroidGapActivity.setSidCookies", "setting cookies");
       CookieManager cookieMgr = CookieManager.getInstance();
       cookieMgr.setAcceptCookie(true);  // Required to set additional cookies that the auth process will return.
       SalesforceSDKManager.getInstance().removeSessionCookies();
       SystemClock.sleep(250); // removeSessionCookies kicks out a thread - let it finish
       String accessToken = client.getAuthToken();
       addSidCookieForInstance(cookieMgr,".salesforce.com", accessToken);
       SalesforceSDKManager.getInstance().syncCookies();
   }

   private void addSidCookieForInstance(CookieManager cookieMgr, String domain, String sid) {
	   final ClientInfo clientInfo = SalesforceDroidGapActivity.this.client.getClientInfo();
       URI instanceUrl = null;
       if (clientInfo != null) {
    	   instanceUrl = clientInfo.getInstanceUrl();
       }
       String host = null;
       if (instanceUrl != null) {
    	   host = instanceUrl.getHost();
       }
       if (host != null) {
    	   addSidCookieForDomain(cookieMgr, host, sid);
       }
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
	       data.put(USER_AGENT, SalesforceSDKManager.getInstance().getUserAgent());
	       data.put(COMMUNITY_ID, clientInfo.communityId);
	       data.put(COMMUNITY_URL, clientInfo.communityUrl);
	       return new JSONObject(data);
	   } else {
		   return null;
	   }
   }

    /**
     * Exception thrown if initial web page load fails.
     */
    public static class HybridAppLoadException extends RuntimeException {

		public HybridAppLoadException(String msg) {
			super(msg);
		}

		private static final long serialVersionUID = 1L;
    }

    /**
     * Acts on the user switch event.
     *
     * @author bhariharan
     */
    private class DroidGapUserSwitchReceiver extends UserSwitchReceiver {

		@Override
		protected void onUserSwitch() {
			restartIfUserSwitched();
		}
    }
}
