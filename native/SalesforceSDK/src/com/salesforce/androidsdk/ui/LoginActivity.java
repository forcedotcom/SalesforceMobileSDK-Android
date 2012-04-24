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

import android.accounts.AccountAuthenticatorActivity;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;

import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;
import com.salesforce.androidsdk.ui.OAuthWebviewHelper.OAuthWebviewHelperEvents;

/**
 * Login Activity: takes care of authenticating the user.
 * Authorization happens inside a web view. Once we get our authorization code,
 * we swap it for an access and refresh token a create an account through the
 * account manager to store them.
 * 
 * The bulk of the work for this is actually managed by OAuthWebviewHelper class.
 */
public class LoginActivity extends AccountAuthenticatorActivity implements OAuthWebviewHelperEvents {

	// Key for login servers properties stored in preferences
	public static final String SERVER_URL_PREFS_SETTINGS = "server_url_prefs";
	public static final String SERVER_URL_PREFS_CUSTOM_LABEL = "server_url_custom_label";
	public static final String SERVER_URL_PREFS_CUSTOM_URL = "server_url_custom_url";
	public static final String SERVER_URL_PREFS_WHICH_SERVER = "which_server_index";
	public static final String SERVER_URL_CURRENT_SELECTION = "server_url_current_string";
	
	// Request code when calling server picker activity
    public static final int PICK_SERVER_CODE = 10;	
	
    private SalesforceR salesforceR;
	private boolean wasBackgrounded;
	private OAuthWebviewHelper webviewHelper;
	private LoginOptions loginOptions;

    /**************************************************************************************************
     *
     * Activity lifecycle
     * 
     **************************************************************************************************/
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Object which allows reference to resources living outside the SDK
		salesforceR = ForceApp.APP.getSalesforceR();
		
		// Getting login options from intent's extras
		loginOptions = LoginOptions.fromBundle(getIntent().getExtras());
		
		// We'll show progress in the window title bar
		getWindow().requestFeature(Window.FEATURE_PROGRESS);
		getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		
		// Setup content view
		setContentView(salesforceR.layoutLogin());

		// Setup the WebView.
		WebView webView = (WebView) findViewById(salesforceR.idLoginWebView());
		webviewHelper = new OAuthWebviewHelper(this, loginOptions, webView, savedInstanceState);
		webviewHelper.loadLoginPage();
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (wasBackgrounded) {
			webviewHelper.clearView();
			webviewHelper.loadLoginPage();
			wasBackgrounded = false;
		}
	}

	@Override
	public void onSaveInstanceState(Bundle bundle) {
		super.onSaveInstanceState(bundle);
		webviewHelper.saveState(bundle);
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

    /**************************************************************************************************
     *
     * Callbacks from the OAuthWebviewHelper 
     * 
     **************************************************************************************************/
	
	@Override
	public void loadingLoginPage(String loginUrl) {
		setTitle(loginUrl);
	}

	@Override
	public void onLoadingProgress(int totalProgress) {
		onIndeterminateProgress(false);
		setProgress(totalProgress);
	}

	@Override
	public void onIndeterminateProgress(boolean show) {
		setProgressBarIndeterminateVisibility(show);
		setProgressBarIndeterminate(show);
	}

	@Override
	public void onAccountAuthenticatorResult(Bundle authResult) {
		setAccountAuthenticatorResult(authResult);
	}
 
    
    /**************************************************************************************************
     *
     * Buttons click handlers
     * 
     **************************************************************************************************/

	/**
	 * Called when "Clear cookies" button is clicked.
	 * Clear cookies and reload login page.
	 * @param v
	 */
	public void onClearCookiesClick(View v) {
		webviewHelper.clearCookies();
		webviewHelper.loadLoginPage();
	}

	/**
	 * Called when "Pick server" button is clicked.
	 * Start ServerPickerActivity
	 * @param v
	 */
	public void onPickServerClick(View v) {
		Intent i = new Intent(this, ServerPickerActivity.class);
	    startActivityForResult(i, PICK_SERVER_CODE);
	}
	
	/**
	 * Called when ServerPickerActivity completes.
	 * Reload login page.
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == PICK_SERVER_CODE && resultCode == Activity.RESULT_OK) {
			webviewHelper.loadLoginPage();
		}
		else {
	        super.onActivityResult(requestCode, resultCode, data);
	    }
	}
}
