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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.security.KeyChain;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.analytics.SalesforceAnalyticsManager;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.OAuth2;
import com.salesforce.androidsdk.auth.idp.interfaces.SPManager;
import com.salesforce.androidsdk.config.RuntimeConfig;
import com.salesforce.androidsdk.config.RuntimeConfig.ConfigKey;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;
import com.salesforce.androidsdk.ui.OAuthWebviewHelper.OAuthWebviewHelperEvents;
import com.salesforce.androidsdk.util.AuthConfigTask;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.LogUtil;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;
import com.salesforce.androidsdk.util.UriFragmentParser;

import java.util.List;
import java.util.Map;

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

    public static final int PICK_SERVER_REQUEST_CODE = 10;
    private static final String TAG = "LoginActivity";

	private boolean wasBackgrounded;
	private OAuthWebviewHelper webviewHelper;
    private ChangeServerReceiver changeServerReceiver;
    private boolean receiverRegistered;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate " + LogUtil.intentToString(getIntent()));
        boolean isDarkTheme = SalesforceSDKManager.getInstance().isDarkTheme();
        setTheme(isDarkTheme ? R.style.SalesforceSDK_Dark_Login : R.style.SalesforceSDK);
        SalesforceSDKManager.getInstance().setViewNavigationVisibility(this);

        // Getting login options from intent's extras.
        final LoginOptions loginOptions = LoginOptions.fromBundle(getIntent().getExtras());

        // Protect against screenshots.
//        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
//                WindowManager.LayoutParams.FLAG_SECURE);

        // Fetches auth config if required.
        try {
            (new AuthConfigTask(null)).execute().get();
        } catch (Exception e) {
            SalesforceSDKLogger.e(TAG, "Exception occurred while fetching auth config", e);
        }

        // Setup content view.
        setContentView(R.layout.sf__login);
		if (SalesforceSDKManager.getInstance().isIDPLoginFlowEnabled()) {
            final Button button = findViewById(R.id.sf__idp_login_button);
            button.setVisibility(View.VISIBLE);
        }

        // Setup the WebView.
        final WebView webView = findViewById(R.id.sf__oauth_webview);
        final WebSettings webSettings = webView.getSettings();
        webSettings.setUseWideViewPort(true);
        webSettings.setLayoutAlgorithm(LayoutAlgorithm.NORMAL);
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
        if (!receiverRegistered) {
			changeServerReceiver = new ChangeServerReceiver();
            final IntentFilter changeServerFilter = new IntentFilter(ServerPickerActivity.CHANGE_SERVER_INTENT);
            registerReceiver(changeServerReceiver, changeServerFilter);
            receiverRegistered = true;
        }
	}

	@Override
	protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (receiverRegistered) {
            unregisterReceiver(changeServerReceiver);
            receiverRegistered = false;
        }
        super.onDestroy();
    }

	@Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        Log.d(TAG, "onCreate " + LogUtil.intentToString(intent));

        // If this is a callback from Chrome, processes it and does nothing else.
        if (isChromeCallback(intent)) {
            completeAuthFlow(intent);
            return;
        }

        // Reloads login page for every new intent to ensure the correct login server is selected.
        if (webviewHelper.shouldReloadPage()) {
            webviewHelper.loadLoginPage();
        }
    }

    protected void certAuthOrLogin() {
        if (shouldUseCertBasedAuth()) {
            final String alias = RuntimeConfig.getRuntimeConfig(this).getString(ConfigKey.ManagedAppCertAlias);
            SalesforceSDKLogger.d(TAG, "Cert based login flow being triggered with alias: " + alias);
            KeyChain.choosePrivateKeyAlias(this, webviewHelper, null, null, null, -1, alias);
        } else {
            SalesforceSDKLogger.d(TAG, "User agent login flow being triggered");
            webviewHelper.loadLoginPage();
        }
    }

    private boolean isChromeCallback(Intent intent) {
        if (intent == null) {
            return false;
        }
        final Uri uri = intent.getData();
        return (uri != null);
    }

    private void completeAuthFlow(Intent intent) {
        Log.d(TAG, "completeAuthFlow");

        final Uri uri = intent.getData();
        final Map<String, String> params = UriFragmentParser.parse(uri);
        final String error = params.get("error");
        if (error != null) {
            final String errorDesc = params.get("error_description");
            webviewHelper.onAuthFlowError(error, errorDesc, null);
        } else {
            final OAuth2.TokenEndpointResponse tr = new OAuth2.TokenEndpointResponse(params);
            webviewHelper.onAuthFlowComplete(tr);
        }
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
        Log.d(TAG, "onResume");
		if (wasBackgrounded) {
		    if (webviewHelper.shouldReloadPage()) {
                webviewHelper.clearView();
                webviewHelper.loadLoginPage();
            }
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
			wasBackgrounded = true;
			if (accMgr.getAuthenticatedUsers() == null) {
				moveTaskToBack(true);
			} else {
				finish();
			}
			return true;
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
        getMenuInflater().inflate(R.menu.sf__login, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
		if (itemId == R.id.sf__menu_clear_cookies) {
        	onClearCookiesClick(null);
        	return true;
        } else if (itemId == R.id.sf__menu_pick_server) {
        	onPickServerClick(null);
        	return true;
        } else if (itemId == R.id.sf__menu_reload) {
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
        Log.d(TAG, "loadingLoginPage " + loginUrl);

		final ActionBar ab = getActionBar();
		if (ab != null) {
			ab.setTitle(loginUrl);
		}
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
     * Called when the IDP login button is clicked.
     *
     * @param v IDP login button.
     */
    public void onIDPLoginClick(View v) {
        Log.d(TAG, "onIDPLoginClick");
        SalesforceSDKManager.getInstance().getSPManager().kickOffSPInitiatedLoginFlow(
                this,
                new SPStatusCallback());
    }

    /**
	 * Called when "Reload" button is clicked.
	 * Reloads login page.
	 * @param v
	 */
	public void onReloadClick(View v) {
        Log.d(TAG, "onReloadClick");
		webviewHelper.loadLoginPage();
	}

	/**
	 * Called when "Pick server" button is clicked.
	 * Start ServerPickerActivity
	 * @param v
	 */
	public void onPickServerClick(View v) {
		final Intent i = new Intent(this, ServerPickerActivity.class);
	    startActivityForResult(i, PICK_SERVER_REQUEST_CODE);
	}

	@Override
	public void finish(UserAccount userAccount) {
        Log.d(TAG, "finish " + userAccount.getUsername());

        initAnalyticsManager(userAccount);
        final UserAccountManager userAccountManager = SalesforceSDKManager.getInstance().getUserAccountManager();
        final List<UserAccount> authenticatedUsers = userAccountManager.getAuthenticatedUsers();
        final int numAuthenticatedUsers = authenticatedUsers == null ? 0 : authenticatedUsers.size();

        final int userSwitchType;
        if (numAuthenticatedUsers == 1) {

            // We've already authenticated the first user, so there should be one.
            userSwitchType = UserAccountManager.USER_SWITCH_TYPE_FIRST_LOGIN;
        } else if (numAuthenticatedUsers > 1) {

            // Otherwise we're logging in with an additional user.
            userSwitchType = UserAccountManager.USER_SWITCH_TYPE_LOGIN;
        } else {

            // This should never happen but if it does, pass in the "unknown" value.
            userSwitchType = UserAccountManager.USER_SWITCH_TYPE_DEFAULT;
        }
        userAccountManager.sendUserSwitchIntent(userSwitchType, null);

        finish();
	}

    private void initAnalyticsManager(UserAccount account) {
        final SalesforceAnalyticsManager analyticsManager = SalesforceAnalyticsManager.getInstance(account);
	    if (analyticsManager != null) {
            analyticsManager.updateLoggingPrefs();
	    }
    }

    class SPStatusCallback implements SPManager.StatusUpdateCallback {
        private String getText(SPManager.Status status) {
            int resId = R.string.sf__login_request_sent_to_idp;
            switch(status) {
                case LOGIN_REQUEST_SENT_TO_IDP:
                    resId = R.string.sf__login_request_sent_to_idp;
                    break;
                case SUCCESS_RESPONSE_RECEIVED_FROM_IDP:
                    resId = R.string.sf__success_response_from_idp;
                    break;
                case ERROR_RESPONSE_RECEIVED_FROM_IDP:
                    resId = R.string.sf__error_response_from_idp;
                    break;
                case LOGIN_COMPLETE:
                    resId = R.string.sf__login_complete;
                    break;
            }

            return getString(resId);
        }
        @Override
        public void onStatusUpdate(@NonNull SPManager.Status status) {
            runOnUiThread(() -> Toast.makeText(
                getApplicationContext(),
                getText(status),
                Toast.LENGTH_LONG
            ).show());
        }
    }

    public class ChangeServerReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getAction() != null) {
                final String action = intent.getAction();
                if (ServerPickerActivity.CHANGE_SERVER_INTENT.equals(action)) {
                    webviewHelper.loadLoginPage();
                }
            }
        }
    }
}
