/*
 * Copyright (c) 2011-present, salesforce.com, inc.
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
import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.security.KeyChain;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.analytics.SalesforceAnalyticsManager;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.config.RuntimeConfig;
import com.salesforce.androidsdk.config.RuntimeConfig.ConfigKey;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;
import com.salesforce.androidsdk.security.PasscodeManager;
import com.salesforce.androidsdk.ui.OAuthWebviewHelper.OAuthWebviewHelperEvents;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

import java.util.List;

/**
 * Login Activity: takes care of authenticating the user.
 * Authorization happens inside a web view. Once we get our authorization code,
 * we swap it for an access and refresh token a create an account through the
 * account manager to store them.
 *
 * The bulk of the work for this is actually managed by OAuthWebviewHelper class.
 */
public class LoginActivity extends AccountAuthenticatorActivity
		implements OAuthWebviewHelperEvents {

	// Request code when calling server picker activity
    public static final int PICK_SERVER_REQUEST_CODE = 10;

    private SalesforceR salesforceR;
	private boolean wasBackgrounded;
	private OAuthWebviewHelper webviewHelper;

    /**************************************************************************************************
     *
     * Activity lifecycle
     *
     **************************************************************************************************/

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Object which allows reference to resources living outside the SDK
		salesforceR = SalesforceSDKManager.getInstance().getSalesforceR();

		// Getting login options from intent's extras
		LoginOptions loginOptions = LoginOptions.fromBundle(getIntent().getExtras());

		requestFeatures();

		// Protect against screenshots
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
				WindowManager.LayoutParams.FLAG_SECURE);

		// Setup content view
		setContentView(salesforceR.layoutLogin());

		// Setup the WebView.
		final WebView webView = (WebView) findViewById(salesforceR.idLoginWebView());
		final WebSettings webSettings = webView.getSettings();
		webSettings.setJavaScriptEnabled(true);
		webSettings.setAllowFileAccessFromFileURLs(true);
		webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
		webSettings.setDatabaseEnabled(true);
		webSettings.setDomStorageEnabled(true);
		EventsObservable.get().notifyEvent(EventType.AuthWebViewCreateComplete, webView);
		webviewHelper = getOAuthWebviewHelper(this, loginOptions, webView, savedInstanceState);

		// Let observers know
		EventsObservable.get().notifyEvent(EventType.LoginActivityCreateComplete, this);
        certAuthOrLogin();
	}

    protected void certAuthOrLogin() {
        if (shouldUseCertBasedAuth()) {
            final String alias = RuntimeConfig.getRuntimeConfig(this).getString(ConfigKey.ManagedAppCertAlias);
            KeyChain.choosePrivateKeyAlias(this, webviewHelper, null, null, null, 0, alias);
        } else {
            webviewHelper.loadLoginPage();
        }
    }

	protected void requestFeatures() {
		// We'll show progress in the window title bar
		getWindow().requestFeature(Window.FEATURE_PROGRESS);
		getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
	}

	/**
     * Returns whether certificate based authentication flow should be used.
     *
     * @return True - if it should be used, False - otherwise.
     */
    protected boolean shouldUseCertBasedAuth() {
		return RuntimeConfig.getRuntimeConfig(this).getBoolean(ConfigKey.RequireCertAuth);
    }

	protected OAuthWebviewHelper getOAuthWebviewHelper(OAuthWebviewHelperEvents callback,
			LoginOptions loginOptions, WebView webView, Bundle savedInstanceState) {
		return new OAuthWebviewHelper(this, callback, loginOptions, webView, savedInstanceState);
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
		// This allows sub classes to override the behavior by returning false.
		if (fixBackButtonBehavior(keyCode)) {
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	/**
	 * A fix for back button behavior
	 *
	 * @return true if the fix was applied
	 *         false if the key code was not handled
	 */
	protected boolean fixBackButtonBehavior(int keyCode) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
		/*
		 * If there are no accounts signed in, we need the login screen
		 * to go away, and go back to the home screen. However, if the
		 * login screen has been brought up from the switcher screen,
		 * the back button should take the user back to the previous screen.
		 */
			final UserAccountManager accMgr = SalesforceSDKManager.getInstance().getUserAccountManager();
			if (accMgr.getAuthenticatedUsers() == null) {
				wasBackgrounded = true;
				moveTaskToBack(true);
				return true;
			} else {
				wasBackgrounded = true;
				finish();
				return true;
			}
		}
		return false;
	}

    /**************************************************************************************************
     *
     * Actions (Changer server / Clear cookies etc) are available through a menu
     *
     **************************************************************************************************/

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(salesforceR.menuLogin(), menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
		if (itemId == salesforceR.idItemClearCookies()) {
        	onClearCookiesClick(null);
        	return true;
        } else if (itemId == salesforceR.idItemPickServer()) {
        	onPickServerClick(null);
        	return true;
        } else if (itemId == salesforceR.idItemReload()) {
        	onReloadClick(null);
        	return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /**************************************************************************************************
     *
     * Callbacks from the OAuthWebviewHelper
     *
     **************************************************************************************************/

	@Override
	public void loadingLoginPage(String loginUrl) {
		final ActionBar ab = getActionBar();
		if (ab != null) {
			ab.setTitle(loginUrl);
		}
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
	 * Called when "Reload" button is clicked.
	 * Reloads login page.
	 * @param v
	 */
	public void onReloadClick(View v) {
		webviewHelper.loadLoginPage();
	}

	/**
	 * Called when "Pick server" button is clicked.
	 * Start ServerPickerActivity
	 * @param v
	 */
	public void onPickServerClick(View v) {
		Intent i = new Intent(this, ServerPickerActivity.class);
	    startActivityForResult(i, PICK_SERVER_REQUEST_CODE);
	}

	/**
	 * Called when ServerPickerActivity completes.
	 * Reload login page.
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == PICK_SERVER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
			webviewHelper.loadLoginPage();
		} else if (requestCode == PasscodeManager.PASSCODE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
			webviewHelper.onNewPasscode();
		} else {
	        super.onActivityResult(requestCode, resultCode, data);
	    }
	}

	@Override
	public void finish() {
        initAnalyticsManager();
        final UserAccountManager userAccountManager = SalesforceSDKManager.getInstance().getUserAccountManager();
        final List<UserAccount> authenticatedUsers = userAccountManager.getAuthenticatedUsers();
        final int numAuthenticatedUsers = authenticatedUsers == null ? 0 : authenticatedUsers.size();

        final int userSwitchType;
        if (numAuthenticatedUsers == 1) {
            // We've already authenticated the first user, so there should be one
            userSwitchType = UserAccountManager.USER_SWITCH_TYPE_FIRST_LOGIN;
        } else if (numAuthenticatedUsers > 1) {
            // Otherwise we're logging in with an additional user
            userSwitchType = UserAccountManager.USER_SWITCH_TYPE_LOGIN;
        } else {
            // This should never happen but if it does, pass in the "unknown" value
            userSwitchType = UserAccountManager.USER_SWITCH_TYPE_DEFAULT;
        }

        userAccountManager.sendUserSwitchIntent(userSwitchType, null);
        super.finish();
	}

    private void initAnalyticsManager() {
        final UserAccount account = SalesforceSDKManager.getInstance().getUserAccountManager().getCurrentUser();
        final SalesforceAnalyticsManager analyticsManager = SalesforceAnalyticsManager.getInstance(account);
	    if (analyticsManager != null) {
            analyticsManager.updateLoggingPrefs();
	    }
    }
}
