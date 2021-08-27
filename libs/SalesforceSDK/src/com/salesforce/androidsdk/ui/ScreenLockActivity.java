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
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;
import static com.salesforce.androidsdk.security.ScreenLockManager.MOBILE_POLICY_PREF;
import static com.salesforce.androidsdk.security.ScreenLockManager.SCREEN_LOCK;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
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
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.FragmentActivity;

import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.app.SalesforceSDKManager;

import java.util.List;

/**
 * Locks the app behind OS provided authentication.
 */
public class ScreenLockActivity extends FragmentActivity {
    private static final String TAG = "ScreenLockActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Protect against screenshots.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
        boolean isDarkTheme = SalesforceSDKManager.getInstance().isDarkTheme();
        setTheme(isDarkTheme ? R.style.SalesforceSDK_ScreenLock_Dark : R.style.SalesforceSDK_ScreenLock);
        // This makes the navigation bar visible on light themes.
        SalesforceSDKManager.getInstance().setViewNavigationVisibility(this);
        setContentView(R.layout.sf__screen_lock);

        TextView errorMessage = findViewById(R.id.sf__screen_lock_error_message);
        Button logoutButton = findViewById(R.id.sf__screen_lock_logout_button);
        ImageView appIcon = findViewById(R.id.sf__app_icon);

        logoutButton.setOnClickListener(v -> logoutScreenLockUsers());
        logoutButton.setVisibility(View.GONE);
        errorMessage.setVisibility(View.GONE);
        try {
            Drawable icon = getPackageManager().getApplicationIcon(getApplicationInfo().packageName);
            appIcon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Unable to retrieve host app icon.  NameNotFoundException: " + e.getMessage());
            appIcon.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.sf__salesforce_logo, null));
        }

        BiometricPrompt biometricPrompt = new BiometricPrompt(this, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                String authError = getString(R.string.sf__screen_lock_auth_error);

                if (errString.length() == 0) {
                    errString = authError;
                }
                errorMessage.setText(errString);
                errorMessage.setVisibility(View.VISIBLE);
                logoutButton.setVisibility(View.VISIBLE);
                sendAccessibilityEvent(authError);
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                errorMessage.setVisibility(View.GONE);
                logoutButton.setVisibility(View.GONE);

                sendAccessibilityEvent(getString(R.string.sf__screen_lock_auth_success));
                SalesforceSDKManager.getInstance().getScreenLockManager().setShouldLock(false);
                finish();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                errorMessage.setText(R.string.sf__screen_lock_auth_failed);
                errorMessage.setVisibility(View.VISIBLE);
                logoutButton.setVisibility(View.VISIBLE);
                sendAccessibilityEvent(getString(R.string.sf__screen_lock_auth_failed));
            }
        });

        String appName = SalesforceSDKManager.getInstance().provideAppName();
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.sf__screen_lock_title, appName))
                .setSubtitle(getString(R.string.sf__screen_lock_subtitle, appName))
                .setAllowedAuthenticators(BIOMETRIC_STRONG | DEVICE_CREDENTIAL)
                .build();

        errorMessage.setVisibility(View.GONE);
        logoutButton.setVisibility(View.GONE);
        biometricPrompt.authenticate(promptInfo);
    }

    private void logoutScreenLockUsers() {
        final UserAccountManager manager = SalesforceSDKManager.getInstance().getUserAccountManager();
        final List<UserAccount> accounts = manager.getAuthenticatedUsers();
        Context ctx = SalesforceSDKManager.getInstance().getAppContext();
        for (UserAccount account : accounts) {
            SharedPreferences accountPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF
                    + account.getOrgLevelFilenameSuffix(), Context.MODE_PRIVATE);
            if (accountPrefs.getBoolean(SCREEN_LOCK, false)) {
                manager.signoutUser(account, null, false);
            }
        }

        // TODO:  We may need logic here to save the last (current) account and determine if we need to navigate back to the login screen or not.

        SalesforceSDKManager.getInstance().getScreenLockManager().reset();
        sendAccessibilityEvent("You are logged out.");
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
