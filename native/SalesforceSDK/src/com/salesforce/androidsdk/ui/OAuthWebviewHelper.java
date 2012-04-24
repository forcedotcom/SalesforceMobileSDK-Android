/*
 * Copyright (c) 2011-2012, salesforce.com, inc.
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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import android.accounts.Account;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.auth.OAuth2;
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;
import com.salesforce.androidsdk.util.UriFragmentParser;

/**
 * Helper class to manage a WebView instance that is going through the OAuth login process.
 * Basic flow is
 * 	a) load and show the login page to the user
 * 	b) user logins in and authorizes app
 *  c) we see the navigation to the auth complete Url, and grab the tokens
 *  d) we call the Id service to obtain additional info about the user
 *  e) we create a local account, and return an authentication result bundle.
 *  f) done!
 *  
 */
public class OAuthWebviewHelper {

	/**
	 * the host activity/fragment should pass in an implementation of this
	 * interface so that it can notify it of things it needs to do as part of
	 * the oauth process.
	 */
	public interface OAuthWebviewHelperEvents {
		
		/** we're starting to load this login page into the webview */
		void loadingLoginPage(String loginUrl);

		/**
		 * progress update of loading the webview, totalProgress will go from
		 * 0..10000 (you can pass this directly to the activity progressbar)
		 */
		void onLoadingProgress(int totalProgress);
		
		/** We're doing something that takes some unknown amount of time */
		void onIndeterminateProgress(boolean show);
		
		/** We've completed the auth process and here's the resulting Authentication Result bundle to return to the Authenticator */
		void onAccountAuthenticatorResult(Bundle authResult);
		
		/** we're in some end state and requesting that the host activity be finished/closed. */
		void finish();
	}
	
	/**
	 * Construct a new OAuthWebviewHelper and perform the initial configuration of the Webview.
	 */
	public OAuthWebviewHelper(OAuthWebviewHelperEvents callback, LoginOptions options, WebView webview, Bundle savedInstanceState) {
		assert options != null && callback != null && webview != null;
		this.callback = callback;
		this.loginOptions = options;
		this.webview = webview;
		
		webview.getSettings().setJavaScriptEnabled(true);
		webview.setWebViewClient(new AuthWebViewClient());
		webview.setWebChromeClient(makeWebChromeClient());

		// Restore webview's state if available.
		// This ensures the user is not forced to type in credentials again
		// once the auth process has been kicked off.
		if (savedInstanceState != null) {
			webview.restoreState(savedInstanceState);
		} else {
			clearCookies();
		}
	}
	
	private final OAuthWebviewHelperEvents callback;
	private final LoginOptions loginOptions;
	private final WebView webview;
	
	public void saveState(Bundle outState) {
		webview.saveState(outState);
	}

	public WebView getWebView() {
		return webview;
	}
	
	public void clearCookies() {
		CookieManager cm = CookieManager.getInstance();
		cm.removeAllCookie();
	}
	
	public void clearView() {
		webview.clearView();
	}
	
	/** Factory method for the WebChromeClient, you can replace this with something else if you need to */
	protected WebChromeClient makeWebChromeClient() {
		return new AuthWebChromeClient();
	}

	protected Context getContext() {
		return webview.getContext();
	}
	
	/**
	 * Called when the user facing part of the auth flow completed with an error.
	 * We show the user an error and end the activity.
	 */
	protected void onAuthFlowError(String error, String errorDesc) {
		Log.w("LoginActivity:onAuthFlowError", error + ":" + errorDesc);

		// look for deny. kick them back to login, so clear cookies and repoint browser
		if ("access_denied".equals(error)
				&& "end-user denied authorization".equals(errorDesc)) {

			webview.post(new Runnable() {
				@Override
				public void run() {
					clearCookies();
					loadLoginPage();
				}
			});

		} else {

			Toast t = Toast.makeText(webview.getContext(), error + " : " + errorDesc,
					Toast.LENGTH_LONG);

			webview.postDelayed(new Runnable() {
				@Override
				public void run() {
					callback.finish();
				}
			}, t.getDuration());
			t.show();
		}
	}
	
	
	protected void showError(Exception exception) {
		Toast.makeText(getContext(),
				getContext().getString(ForceApp.APP.getSalesforceR().stringGenericError(), exception.toString()),
				Toast.LENGTH_LONG).show();
	}
	
	/**
	 * Tells the webview to load the authorization page. 
	 * We also update the window title, so its easier to 
	 * see which system you're logging in to
	 */
	protected void loadLoginPage() {
		// Filling in loginUrl
		loginOptions.loginUrl = getLoginUrl();

		try {
			URI uri = OAuth2.getAuthorizationUrl(
			        new URI(loginOptions.loginUrl), 
			        loginOptions.oauthClientId, 
			        loginOptions.oauthCallbackUrl,
			        loginOptions.oauthScopes);
			callback.loadingLoginPage(loginOptions.loginUrl);
			webview.loadUrl(uri.toString());
		} catch (URISyntaxException ex) {
			showError(ex);
		}
	}

	/**
	 * Override this method to customize the login url.
	 * @return login url
	 */
	protected String getLoginUrl() {
		SharedPreferences settings = webview.getContext().getSharedPreferences(
				LoginActivity.SERVER_URL_PREFS_SETTINGS, Context.MODE_PRIVATE);

		return settings.getString(LoginActivity.SERVER_URL_CURRENT_SELECTION, OAuth2.DEFAULT_LOGIN_URL);
	}   

	/**
	 * WebViewClient which intercepts the redirect to the oauth callback url.
	 * That redirect marks the end of the user facing portion of the authentication flow.
	 *
	 */
	protected class AuthWebViewClient extends WebViewClient {
		
		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			boolean isDone = url.startsWith(loginOptions.oauthCallbackUrl);
			
			if (isDone) {
				Uri callbackUri = Uri.parse(url);
				Map<String, String> params = UriFragmentParser.parse(callbackUri);
				
				String error = params.get("error");
				// Did we fail?
				if (error != null) {
					String errorDesc = params.get("error_description");
					onAuthFlowError(error, errorDesc);
				}
				// Or succeed?
				else {
					TokenEndpointResponse tr = new TokenEndpointResponse(params);
					onAuthFlowComplete(tr);
				}
			}

			return isDone;
		}
	}
	
	
	/**
	 * Called when the user facing part of the auth flow completed successfully.
	 * The last step is to call the identity service to get the username.
	 */
	protected void onAuthFlowComplete(TokenEndpointResponse tr) {
		FinishAuthTask t = new FinishAuthTask();
		t.execute(tr);
	}
	
	/**
	 * Background task that takes care of finishing the authentication flow
	 */
	protected class FinishAuthTask extends AsyncTask<TokenEndpointResponse, Boolean, Exception> {

		@Override
		protected final Exception doInBackground(TokenEndpointResponse... params) {
			try {
				publishProgress(true);
				TokenEndpointResponse tr = params[0];
				String username = OAuth2.getUsernameFromIdentityService(
					HttpAccess.DEFAULT, tr.idUrlWithInstance, tr.authToken);
				addAccount(username, tr.refreshToken, tr.authToken, tr.instanceUrl,
						loginOptions.loginUrl, loginOptions.oauthClientId, tr.orgId, tr.userId);
				
			} catch (Exception ex) {
				return ex;
			}
			
			return null;
		}

		@Override
		protected void onPostExecute(Exception ex) {
			if (ex != null) {
				// Error
				onAuthFlowError(getContext().getString(ForceApp.APP.getSalesforceR().stringGenericAuthenticationErrorTitle()),
						getContext().getString(ForceApp.APP.getSalesforceR().stringGenericAuthenticationErrorBody()));
			} else {
				// Done
				callback.finish();
			}
		}

		@Override
		protected void onProgressUpdate(Boolean... values) {
			callback.onIndeterminateProgress(values[0]);
		}
	}

	protected void addAccount(String username, String refreshToken, String authToken, String instanceUrl,
			String loginUrl, String clientId, String orgId, String userId) {

		ClientManager clientManager = new ClientManager(getContext(), ForceApp.APP.getAccountType(), loginOptions);
		
		// Old account
		Account[] oldAccounts = clientManager.getAccounts();
		
		// Create account name (shown in Settings -> Accounts & sync)
		String accountName = buildAccountName(username);

		// New account
		Bundle extras = clientManager.createNewAccount(accountName, username, refreshToken, authToken, instanceUrl, loginUrl, clientId, orgId, userId);
		callback.onAccountAuthenticatorResult(extras);

		// Remove old accounts
		clientManager.removeAccounts(oldAccounts);
	}

    /**
     * @return name to be shown for account in Settings -> Accounts & Sync
     */
    protected String buildAccountName(String username) {
    	return String.format("%s (%s)", username, ForceApp.APP.getApplicationName());
    }

	/**
	 * WebChromeClient used to report back loading progress.
	 */
	protected class AuthWebChromeClient extends WebChromeClient {

		@Override
		public void onProgressChanged(WebView view, int newProgress) {
			callback.onLoadingProgress(newProgress * 100);
		}
	}
}
