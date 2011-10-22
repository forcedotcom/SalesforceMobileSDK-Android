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
package com.salesforce.androidsdk.auth;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse;
import com.salesforce.androidsdk.rest.ClientManager;

/**
 * Abstract super class that takes care of authenticating the user.
 * Authorization happens inside a web view. Once we get our authorization code,
 * we swap it for an access and refresh token a create an account through the
 * account manager to store them.
 */
public abstract class AbstractLoginActivity extends
		AccountAuthenticatorActivity {

	// Actual login activity should filter for this intent
	public static final String ACTION_LOGIN = "salesforce.intent.action.LOGIN";

	private WebView webView;
	private String loginServerUrl;
	private boolean wasBackgrounded;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// we'll show progress in the window title bar.
		getWindow().requestFeature(Window.FEATURE_PROGRESS);
		getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		
		// Setup content view
		setContentView(getLayoutId());

		// Setup the WebView.
		webView = (WebView) findViewById(getWebViewId());
		webView.getSettings().setJavaScriptEnabled(true);
		webView.setWebViewClient(new AuthWebViewClient());
		webView.setWebChromeClient(new AuthWebChromeClient());

		// Restore webview's state if available.
		// This ensures the user is not forced to type in credentials again
		// once the auth process has been kicked off.
		if (savedInstanceState != null) {
			webView.restoreState(savedInstanceState);
		}
		loadLoginPage();
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (wasBackgrounded) {
			webView.clearView();
			loadLoginPage();
			wasBackgrounded = false;
		}
	}

	@Override
	public void onSaveInstanceState(Bundle bundle) {
		super.onSaveInstanceState(bundle);
		webView.saveState(bundle);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			wasBackgrounded = true;
			moveTaskToBack(true);
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	/**
	 * Tells the webview to load the authorization page. 
	 * We also update the window title, so its easier to 
	 * see which system you're logging in to
	 */
	protected void loadLoginPage() {

		loginServerUrl = getLoginServerUrl();

		try {
			URI uri = OAuth2.getAuthorizationUrl(
			        new URI(loginServerUrl), 
			        getOAuthClientId(), 
			        getOAuthCallbackUrl(),
			        getOAuthScopes());
			setTitle(loginServerUrl);
			webView.loadUrl(uri.toString());
		} catch (URISyntaxException ex) {
			showError(ex);
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
	 * Called when the user facing part of the auth flow completed with an error.
	 * We show the user an error and end the activity.
	 */
	protected void onAuthFlowError(String error, String errorDesc) {
		Log.w("AbstractLoginActivity:onAuthFlowError", error + ":" + errorDesc);

		// look for deny. kick them back to login, so clear cookies and repoint browser
		if ("access_denied".equals(error)
				&& "end-user denied authorization".equals(errorDesc)) {

			webView.post(new Runnable() {
				@Override
				public void run() {
					clearCookies();
				}
			});

		} else {

			Toast t = Toast.makeText(this, error + " : " + errorDesc,
					Toast.LENGTH_LONG);

			webView.postDelayed(new Runnable() {
				@Override
				public void run() {
					finish();
				}
			}, t.getDuration());
			t.show();

		}
	}
	
	protected void addAccount(String username, String refreshToken, String authToken,
			String instanceUrl, String loginUrl, String clientId, String userId,
			String apiVersion) {

		ClientManager clientManager = new ClientManager(this, getAccountType());
		
		// Old account
		Account[] oldAccounts = clientManager.getAccounts();

		// New account
		Bundle extras = clientManager.createNewAccount(username, refreshToken, authToken, instanceUrl, loginUrl, clientId, userId);
		setAccountAuthenticatorResult(extras);

		// Remove old accounts
		clientManager.removeAccounts(oldAccounts);
	}

	protected void clearCookies() {
		CookieManager cm = CookieManager.getInstance();
		cm.removeAllCookie();
		loadLoginPage();
	}

	/**************************************************************************************************
	 * 
	 * Helper inner classes
	 * 
	 **************************************************************************************************/
	
	/**
	 * Background task that takes care of finishing the authentication flow
	 */
	protected class FinishAuthTask extends
			AsyncTask<TokenEndpointResponse, Boolean, TokenEndpointResponse> {

		protected volatile Exception backgroundException;

		@Override
		protected final TokenEndpointResponse doInBackground(
				TokenEndpointResponse... params) {
			try {
				publishProgress(true);
				return performRequest(params[0]);
			} catch (Exception ex) {
				backgroundException = ex;
			}
			return null;
		}

		@Override
		protected void onPostExecute(OAuth2.TokenEndpointResponse tr) {
			if (backgroundException != null) {
				onAuthFlowError(getGenericAuthErrorTitle(),
						getGenericAuthErrorBody());
			} else {
				String userId = tr.id.substring(tr.id.lastIndexOf('/') + 1,
						tr.id.length());
				addAccount(tr.username, tr.refreshToken, tr.authToken,
						tr.instanceUrl, loginServerUrl, getOAuthClientId(), userId, getApiVersion());

				finish();
			}
		}

		@Override
		protected void onProgressUpdate(Boolean... values) {
			setProgressBarIndeterminateVisibility(values[0]);
			setProgressBarIndeterminate(values[0]);
		}

		protected TokenEndpointResponse performRequest(TokenEndpointResponse tr)
				throws Exception {
			// Plugging instanceUrl's host in identity url (seems necessary on sandbox)
			URI idUri = new URI(tr.id);
			URI instanceUri = new URI(tr.instanceUrl);
			String modifiedIdUrl = tr.id.replace(idUri.getHost(), instanceUri.getHost());
			
			tr.username = OAuth2.getUsernameFromIdentityService(
					HttpAccess.DEFAULT, modifiedIdUrl, tr.authToken);
			return tr;
		}
	}

	/**
	 * WebChromeClient used to report back progress.
	 *
	 */
	class AuthWebChromeClient extends WebChromeClient {

		@Override
		public void onProgressChanged(WebView view, int newProgress) {
			setProgress(newProgress * 100);
		}
	}

	/**
	 * WebViewClient which intercepts the redirect to the oauth callback url.
	 * That redirect marks the end of the user facing portion of the authentication flow.
	 *
	 */
	class AuthWebViewClient extends WebViewClient {
		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			boolean isDone = url.startsWith(getOAuthCallbackUrl());
			
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
					TokenEndpointResponse tr = new TokenEndpointResponse();
					tr.authToken = params.get("access_token");
					tr.refreshToken = params.get("refresh_token");
					tr.instanceUrl = params.get("instance_url");
					tr.id = params.get("id");
					
					onAuthFlowComplete(tr);
				}
			}

			return isDone;
		}
	}

	/**
	 * This parses a Uri fragment that uses a queryString style foo=bar&bar=foo
	 * parameter passing (e.g. OAuth2)
	 */
	static class UriFragmentParser {

		/**
		 * look for # error fragments and standard url param errors, like the
		 * user clicked deny on the auth page
		 * 
		 * @param uri
		 * @return
		 */
		public static Map<String, String> parse(Uri uri) {
			Map<String, String> retval = parse(uri.getEncodedFragment());
			if (retval.size() == 0) {
				retval = parse(uri.getEncodedQuery());
			}
			return retval;
		}

		public static Map<String, String> parse(String fragmentString) {
			Map<String, String> res = new HashMap<String, String>();
			if (fragmentString == null)
				return res;
			fragmentString = fragmentString.trim();
			if (fragmentString.length() == 0)
				return res;
			String[] params = fragmentString.split("&");
			for (String param : params) {
				String[] parts = param.split("=");
				res.put(URLDecoder.decode(parts[0]),
						parts.length > 1 ? URLDecoder.decode(parts[1]) : "");
			}
			return res;
		}

		private UriFragmentParser() {
			assert false : "don't construct me!";
		}
	}

	/**************************************************************************************************
	 * 
	 * Abstract methods: to be implemented by subclass
	 * 
	 **************************************************************************************************/

	/**
	 * @return id of layout to use for login screen
	 */
	protected abstract int getLayoutId();

	/**
	 * @return id of web view in login screen to use to show the server login page
	 */
	protected abstract int getWebViewId();

	/**
	 * @return oauth client id for this application
	 */
	protected abstract String getOAuthClientId();

	/**
	 * @return oauth callback url
	 */
	protected abstract String getOAuthCallbackUrl();
	
	/**
	 * @return account type
	 */
	protected abstract String getAccountType();

	/**************************************************************************************************
	 * 
	 * Other methods: likely to be overridden by sub class
	 * 
	 **************************************************************************************************/
	/**
	 * The method is called when an unexpected error takes place.
	 * Default implementation shows a toast with the exception message.
	 * Override if you want a different behavior or a user friendly message to be shown instead.
	 * @param exception
	 */
	protected void showError(Exception exception) {
		Toast.makeText(this,
				exception.toString(),
				Toast.LENGTH_LONG).show();
	}
	
	/**
	 * Override to have a localized error message title.
	 * @return english generic error message title.
	 */
	protected String getGenericAuthErrorTitle() {
		return "Error";
	}

	/**
	 * Override to have a localized error message.
	 * @return english generic error message.
	 */
	protected String getGenericAuthErrorBody() {
		return "Authentication error. Please try again.";		
	}

	/**
	 * Override if you want to use a different api version
	 * @return string for api version v23.0
	 */
	protected String getApiVersion() {
		return "v23.0";
	}
	
	/**
	 * Override if you want to use a different server (e.g. production).x
	 * @return sandbox login server url to use
	 */
    protected String getLoginServerUrl() {
    	return "https://test.salesforce.com";
    }
	
	/**
	 * Override this method to configure which scopes your application requires.
	 * By default you obtain "api" scope.
     * (@see <a href="https://help.salesforce.com/apex/HTViewHelpDoc?language=en&id=remoteaccess_oauth_scopes.htm">RemoteAccess OAuth Scopes</a> )
	 * 
	 * @return An array of scopes to use for your oauth token, eg {"visualforce","api"}.  You need not provide refresh_token scope as it is always added automatically.
	 */
    protected String[] getOAuthScopes() {
	    return null;
	}
	
}
