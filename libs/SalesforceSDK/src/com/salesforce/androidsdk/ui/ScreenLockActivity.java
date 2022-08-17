/*
 * Copyright (c) 2021-present, salesforce.com, inc.
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
import static com.salesforce.androidsdk.security.ScreenLockManager.MOBILE_POLICY_PREF;
import static com.salesforce.androidsdk.security.ScreenLockManager.SCREEN_LOCK;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.FragmentActivity;

import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import java.util.List;

/**
 * Locks the app behind OS provided authentication.
 */
public class ScreenLockActivity extends FragmentActivity {
    private static final String TAG = "ScreenLockActivity";
    private static final int SETUP_REQUEST_CODE = 70;
    private static final String appName = SalesforceSDKManager.getInstance().provideAppName();
    private TextView errorMessage;
    private Button logoutButton;
    private Button actionButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Protect against screenshots.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
        boolean isDarkTheme = SalesforceSDKManager.getInstance().isDarkTheme();
        setTheme(isDarkTheme ? R.style.SalesforceSDK_ScreenLock_Dark : R.style.SalesforceSDK_ScreenLock);
        // Makes the navigation bar visible on light themes.
        SalesforceSDKManager.getInstance().setViewNavigationVisibility(this);
        setContentView(R.layout.sf__screen_lock);

        errorMessage = findViewById(R.id.sf__screen_lock_error_message);
        logoutButton = findViewById(R.id.sf__screen_lock_logout_button);
        logoutButton.setOnClickListener(v -> logoutScreenLockUsers());
        actionButton = findViewById(R.id.sf__screen_action_button);
        ImageView appIcon = findViewById(R.id.sf__app_icon);

        try {
            Drawable icon = getPackageManager().getApplicationIcon(getApplicationInfo().packageName);
            appIcon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            SalesforceSDKLogger.e(TAG, "Unable to retrieve host app icon.  NameNotFoundException: " + e.getMessage());
            appIcon.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.sf__salesforce_logo, null));
        }

        presentAuth();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        /*
         * presentAuth again after the user has come back from security settings to ensure they
         * actually set up a secure lock screen (pin/pattern/password/etc) instead of swipe or none.
         */
        if (requestCode == SETUP_REQUEST_CODE) {
            presentAuth();
        }
    }

    @Override
    public void onBackPressed() {
        // purposefully blank
    }

    private void presentAuth() {
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
                setErrorMessage(error);
                break;
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                setErrorMessage(getString(R.string.sf__screen_lock_error_hw_unavailable));
                break;
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                setErrorMessage(getString(R.string.sf__screen_lock_setup_required, appName));

                /*
                 * Prompts the user to setup OS screen lock and biometric.
                 * TODO: Remove when min API > 29.
                 */
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    final Intent biometricIntent = new Intent(Settings.ACTION_BIOMETRIC_ENROLL);
                    biometricIntent.putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, getAuthenticators());
                    actionButton.setOnClickListener(v -> startActivityForResult(biometricIntent, SETUP_REQUEST_CODE));
                } else {
                    final Intent lockScreenIntent = new Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD);
                    actionButton.setOnClickListener(v -> startActivityForResult(lockScreenIntent, SETUP_REQUEST_CODE));
                }
                actionButton.setText(getString(R.string.sf__screen_lock_setup_button));
                actionButton.setVisibility(View.VISIBLE);
                break;
            case BiometricManager.BIOMETRIC_SUCCESS:
                resetUI();
                biometricPrompt.authenticate(getPromptInfo());
                break;
        }
    }

    private BiometricPrompt.PromptInfo getPromptInfo() {
        return new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.sf__screen_lock_title, appName))
                .setSubtitle(getString(R.string.sf__screen_lock_subtitle, appName))
                .setAllowedAuthenticators(getAuthenticators())
                .setConfirmationRequired(false)
                .build();
    }

    private BiometricPrompt getBiometricPrompt() {
        return  new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    onAuthError(errString);
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                finishSuccess();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                setErrorMessage(getString(R.string.sf__screen_lock_auth_failed));
                sendAccessibilityEvent(getString(R.string.sf__screen_lock_auth_failed));
            }
        });
    }

    private void onAuthError(CharSequence errString) {
        String authError = getString(R.string.sf__screen_lock_auth_error);

        if (errString.length() == 0) {
            errString = authError;
        }
        setErrorMessage(errString.toString());
        sendAccessibilityEvent(authError);

        actionButton.setVisibility(View.VISIBLE);
        actionButton.setText(getString(R.string.sf__screen_lock_retry_button));
        actionButton.setOnClickListener(v -> presentAuth());
    }

    private void finishSuccess() {
        resetUI();
        sendAccessibilityEvent(getString(R.string.sf__screen_lock_auth_success));
        finish();
    }

    private int getAuthenticators() {
        // TODO: Remove when min API > 29.
        return (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                ? BIOMETRIC_STRONG | DEVICE_CREDENTIAL
                : BIOMETRIC_WEAK | DEVICE_CREDENTIAL;
    }

    private void logoutScreenLockUsers() {
        final UserAccountManager manager = SalesforceSDKManager.getInstance().getUserAccountManager();
        final List<UserAccount> accounts = manager.getAuthenticatedUsers();
        Context ctx = SalesforceSDKManager.getInstance().getAppContext();

        if (accounts != null) {
            for (UserAccount account : accounts) {
                SharedPreferences accountPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF
                        + account.getUserLevelFilenameSuffix(), Context.MODE_PRIVATE);
                if (accountPrefs.getBoolean(SCREEN_LOCK, false)) {
                    manager.signoutUser(account, null);
                }
            }
        }

        sendAccessibilityEvent("You are logged out.");
        finish();
    }

    private void setErrorMessage(String message) {
        errorMessage.setText(message);
        errorMessage.setVisibility(View.VISIBLE);
        logoutButton.setVisibility(View.VISIBLE);
    }

    private void resetUI() {
        logoutButton.setVisibility(View.GONE);
        errorMessage.setVisibility(View.GONE);
        actionButton.setVisibility(View.GONE);
    }

    private void sendAccessibilityEvent(String text) {
        AccessibilityManager am = (AccessibilityManager) this.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am != null && am.isEnabled()) {
            AccessibilityEvent event = AccessibilityEvent.obtain();
            event.setEventType(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
            event.setClassName(getClass().getName());
            event.setPackageName(this.getPackageName());
            event.getText().add(text);
            am.sendAccessibilityEvent(event);
        }
    }
}
