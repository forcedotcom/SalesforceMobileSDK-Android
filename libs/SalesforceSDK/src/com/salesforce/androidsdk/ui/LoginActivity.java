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

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.security.KeyChain;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.analytics.SalesforceAnalyticsManager;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.idp.interfaces.SPManager;
import com.salesforce.androidsdk.config.RuntimeConfig;
import com.salesforce.androidsdk.config.RuntimeConfig.ConfigKey;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;
import com.salesforce.androidsdk.security.BiometricAuthenticationManager;
import com.salesforce.androidsdk.ui.OAuthWebviewHelper.OAuthWebviewHelperEvents;
import com.salesforce.androidsdk.util.AuthConfigTask;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;
import com.salesforce.androidsdk.util.UriFragmentParser;

import java.io.IOException;
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
public class LoginActivity extends AppCompatActivity
		implements OAuthWebviewHelperEvents {

    public static final int PICK_SERVER_REQUEST_CODE = 10;
    private static final int SETUP_REQUEST_CODE = 72;
    private static final String TAG = "LoginActivity";

    private boolean wasBackgrounded;
    private OAuthWebviewHelper webviewHelper;
    private ChangeServerReceiver changeServerReceiver;
    private boolean receiverRegistered;
    private AccountAuthenticatorResponse accountAuthenticatorResponse = null;
    private Bundle accountAuthenticatorResult = null;
    private Button biometricAuthenticationButton = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        accountAuthenticatorResponse = getIntent().getParcelableExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE);
        if (accountAuthenticatorResponse != null) {
            accountAuthenticatorResponse.onRequestContinued();
        }

		boolean isDarkTheme = SalesforceSDKManager.getInstance().isDarkTheme();
        setTheme(isDarkTheme ? R.style.SalesforceSDK_Dark_Login : R.style.SalesforceSDK);
        SalesforceSDKManager.getInstance().setViewNavigationVisibility(this);

        // Getting login options from intent's extras.
        final LoginOptions loginOptions = LoginOptions.fromBundleWithSafeLoginUrl(getIntent().getExtras());

        // Protect against screenshots.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

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

        BiometricAuthenticationManager bioAuthManager =
                (BiometricAuthenticationManager) SalesforceSDKManager.getInstance().getBiometricAuthenticationManager();
        if (bioAuthManager.isLocked() && bioAuthManager.hasBiometricOptedIn()) {
            if (bioAuthManager.isNativeBiometricLoginButtonEnabled()) {
                biometricAuthenticationButton = findViewById(R.id.sf__bio_login_button);
                biometricAuthenticationButton.setVisibility(View.VISIBLE);
            }

            if (getIntent().getExtras().getBoolean(BiometricAuthenticationManager.SHOW_BIOMETRIC)) {
                presentBiometric();
            }
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
        if (receiverRegistered) {
            unregisterReceiver(changeServerReceiver);
            receiverRegistered = false;
        }
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

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

    // The code in this override was taken from the deprecated AccountAuthenticatorActivity
    // class to replicate its functionality per the deprecation message.
    @Override
    public void finish() {
        if (accountAuthenticatorResponse != null) {
            // send the result bundle back if set, otherwise send an error.
            if (accountAuthenticatorResult != null) {
                accountAuthenticatorResponse.onResult(accountAuthenticatorResult);
            } else {
                accountAuthenticatorResponse.onError(AccountManager.ERROR_CODE_CANCELED, "canceled");
            }
            accountAuthenticatorResponse = null;
        }

        super.finish();
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
        final Uri uri = intent.getData();
        final Map<String, String> params = UriFragmentParser.parse(uri);
        final String error = params.get("error");
        if (error != null) {
            final String errorDesc = params.get("error_description");
            webviewHelper.onAuthFlowError(error, errorDesc, null);
        } else {
            String code = params.get("code");
            webviewHelper.onWebServerFlowComplete(code);
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

            if (!SalesforceSDKManager.getInstance().getBiometricAuthenticationManager().isLocked()) {

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
            }

            //  Do not execute back button behavior.
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
        final ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setTitle(loginUrl);
        }
    }

	@Override
	public void onAccountAuthenticatorResult(Bundle authResult) {
		accountAuthenticatorResult = authResult;
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
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        /*
         * presentAuth again after the user has come back from security settings to ensure they
         * actually set up a secure lock screen (pin/pattern/password/etc) instead of swipe or none.
         */
        if (requestCode == SETUP_REQUEST_CODE) {
            biometricAuthenticationButton.setText(R.string.sf__login_with_biometric);
            presentBiometric();
        }
    }

	@Override
	public void finish(UserAccount userAccount) {
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
        @Override
        public void onStatusUpdate(@NonNull SPManager.Status status) {
            runOnUiThread(() -> Toast.makeText(
                    getApplicationContext(),
                    getString(status.getResIdForDescription()),
                    Toast.LENGTH_SHORT
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

    protected void presentBiometric() {
        BiometricPrompt biometricPrompt = getBiometricPrompt();
        BiometricManager biometricManager = BiometricManager.from(this);

        switch (biometricManager.canAuthenticate(getAuthenticators())) {
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
            case BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED:
            case BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED:
            case BiometricManager.BIOMETRIC_STATUS_UNKNOWN:
                // This should never happen.
                String error = getString(R.string.sf__screen_lock_error);
                SalesforceSDKLogger.e(TAG, "Biometric manager cannot authenticate. " + error);
                break;
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                /*
                 * Prompts the user to setup OS screen lock and biometric.
                 * TODO: Remove when min API > 29.
                 */
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    final Intent biometricIntent = new Intent(Settings.ACTION_BIOMETRIC_ENROLL);
                    biometricIntent.putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, getAuthenticators());
                    biometricAuthenticationButton.setOnClickListener(v ->
                            startActivityForResult(biometricIntent, SETUP_REQUEST_CODE));
                } else {
                    final Intent lockScreenIntent = new Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD);
                    biometricAuthenticationButton.setOnClickListener(v ->
                            startActivityForResult(lockScreenIntent, SETUP_REQUEST_CODE));
                }
                biometricAuthenticationButton.setText(getString(R.string.sf__setup_biometric_unlock));
                break;
            case BiometricManager.BIOMETRIC_SUCCESS:
                biometricPrompt.authenticate(getPromptInfo());
                break;
        }
    }

    private BiometricPrompt getBiometricPrompt() {
        LoginActivity loginActivity = this;
        return new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                    }

                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        ((BiometricAuthenticationManager) SalesforceSDKManager.getInstance()
                                .getBiometricAuthenticationManager()).setLocked(false);
                        new RefreshTokenTask(loginActivity).execute();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                    }
                });
    }

    private class RefreshTokenTask extends AsyncTask<Void, Void, Void> {

        private final LoginActivity activity;

        public RefreshTokenTask(LoginActivity activity) {
            this.activity = activity;;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            SalesforceSDKManager.getInstance().getClientManager().getRestClient(activity,
                    client -> {
                        try {
                            client.getOAuthRefreshInterceptor().refreshAccessToken();
                        } catch (IOException e) {
                            SalesforceSDKLogger.e(TAG, "Error encountered while unlocking.", e);
                        } finally {
                            activity.finish();
                        }
                    });

            return null;
        }
    }

    private int getAuthenticators() {
        // TODO: Remove when min API > 29.
        return (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                ? BIOMETRIC_STRONG | DEVICE_CREDENTIAL
                : BIOMETRIC_WEAK | DEVICE_CREDENTIAL;
    }

    private BiometricPrompt.PromptInfo getPromptInfo() {
        boolean hasFaceUnlock = false;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            hasFaceUnlock = getPackageManager().hasSystemFeature(PackageManager.FEATURE_FACE) ||
                    (getPackageManager().hasSystemFeature(PackageManager.FEATURE_IRIS));
        }

        String subtitle = SalesforceSDKManager.getInstance().getUserAccountManager()
                .getCurrentUser().getUsername();
        return new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getResources().getString(R.string.sf__biometric_opt_in_title))
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(getAuthenticators())
                .setConfirmationRequired(hasFaceUnlock)
                .build();
    }

    public void onBioAuthClick(View view) {
        presentBiometric();
    }
}
