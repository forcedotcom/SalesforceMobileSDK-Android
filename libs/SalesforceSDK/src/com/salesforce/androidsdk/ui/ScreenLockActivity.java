package com.salesforce.androidsdk.ui;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;
import static com.salesforce.androidsdk.security.ScreenLockManager.MOBILE_POLICY_PREF;
import static com.salesforce.androidsdk.security.ScreenLockManager.SCREEN_LOCK;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.FragmentActivity;

import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.app.SalesforceSDKManager;

import java.util.List;

public class ScreenLockActivity extends FragmentActivity {
    private static final String TAG = "ScreenLockActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Protect against screenshots.
//        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
//                WindowManager.LayoutParams.FLAG_SECURE);
        boolean isDarkTheme = SalesforceSDKManager.getInstance().isDarkTheme();
        setTheme(isDarkTheme ? R.style.SalesforceSDK_ScreenLock_Dark : R.style.SalesforceSDK_ScreenLock);
        // This makes the navigation bar visible on light themes.
        SalesforceSDKManager.getInstance().setViewNavigationVisibility(this);
        setContentView(R.layout.sf__screen_lock);

        TextView errorMessage = findViewById(R.id.sf__screenlock_error_message);
        Button logoutButton = findViewById(R.id.sf__screenlock_logout_button);
        ImageView appIcon = findViewById(R.id.sf__app_icon);

        logoutButton.setOnClickListener(v -> logoutScreenLockUsers());
        logoutButton.setVisibility(View.GONE);
        errorMessage.setVisibility(View.GONE);
        try {
            Drawable icon = getPackageManager().getApplicationIcon(getApplicationInfo().packageName);
            appIcon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Unable to retrieve host app icon.  NameNotFoundException: " + e.getMessage());
        }

        BiometricPrompt biometricPrompt = new BiometricPrompt(this, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);

                // TODO: handle:
                //  * biometric cancelled by showing logout button
                //  * "no fingerprints enrolled" when no lock at all
                Toast.makeText(getApplicationContext(),
                        "Authentication error: " + errString, Toast.LENGTH_SHORT)
                        .show();

                if (errString.length() == 0) {
                    errString = "Authenticate to use the app.";
                }
                errorMessage.setText(errString);
                errorMessage.setVisibility(View.VISIBLE);
                logoutButton.setVisibility(View.VISIBLE);
                sendAccessibilityEvent("Authentication error.");
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                errorMessage.setVisibility(View.GONE);
                logoutButton.setVisibility(View.GONE);

                sendAccessibilityEvent("Authentication succeeded.");
                SalesforceSDKManager.getInstance().getScreenLockManager().setShouldLock(false);
                finish();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();

                // TODO: Too many attempts failure
                Toast.makeText(getApplicationContext(), "Authentication failed",
                        Toast.LENGTH_SHORT)
                        .show();

                // TODO: what string goes here?
                errorMessage.setText("Authentication Failed.");
                errorMessage.setVisibility(View.VISIBLE);
                logoutButton.setVisibility(View.VISIBLE);
                sendAccessibilityEvent("Authentication failed");
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(SalesforceSDKManager.getInstance().provideAppName() + " Lock")
                .setSubtitle("Some prompt to the user to authenticate to continue.")
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
        sendAccessibilityEvent(getString(R.string.sf__accessibility_logged_out_announcement));
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
