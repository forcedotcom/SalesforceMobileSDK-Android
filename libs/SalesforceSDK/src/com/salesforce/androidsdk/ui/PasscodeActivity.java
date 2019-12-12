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

import android.Manifest;
import android.Manifest.permission;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.security.PasscodeManager;

import java.util.List;


/**
 * Passcode activity: takes care of creating/verifying a user passcode.
 */
public class PasscodeActivity extends Activity {

    private static final String EXTRA_KEY = "input_text";
    protected static final int MAX_PASSCODE_ATTEMPTS = 10;

    final private int REQUEST_CODE_ASK_PERMISSIONS = 11;

    private PasscodeMode currentMode;
    private TextView title, instr, bioInstrTitle, bioInstr;
    private PasscodeField passcodeField;
    private LinearLayout passcodeBox, biometricBox;
    private Button logoutButton, notNowButton, enableButton, verifyButton;
    private View fingerImage, faceImage;
    private PasscodeManager passcodeManager;
    private String firstPasscode, biometricTitle, biometricDescription;
    private boolean logoutEnabled;
    private boolean forceBiometric;
    private BiometricType biometricType = BiometricType.Fingerprint;

    public enum PasscodeMode {
        Create,
        CreateConfirm,
        Check,
        Change,
        EnableBiometric,
        BiometricCheck
    }

    private enum BiometricType {
        Fingerprint,
        FaceUnlock,
        Iris
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        passcodeManager = SalesforceSDKManager.getInstance().getPasscodeManager();
        boolean isDarkTheme = SalesforceSDKManager.getInstance().isDarkTheme();
        setTheme(isDarkTheme ? R.style.SalesforceSDK_Passcode_Dark : R.style.SalesforceSDK_Passcode);

        // This makes the navigation bar visible on light themes.
        SalesforceSDKManager.getInstance().setViewNavigationVisibility(this);

        // Protect against screenshots.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.sf__passcode);

        title = findViewById(R.id.sf__passcode_title);
        instr = findViewById(R.id.sf__passcode_instructions);
        passcodeField = findViewById(R.id.sf__passcode_text);
        passcodeField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                final String passcode = s.toString();
                if (passcodeManager.getPasscodeLengthKnown() && passcode.length() == passcodeManager.getPasscodeLength()) {
                    onSubmit(passcode);
                }
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
        if (passcodeManager.getPasscodeLengthKnown()) {
            passcodeField.setHint(getString(R.string.sf__accessibility_passcode_length_hint, passcodeManager.getPasscodeLength()));
        }
        passcodeBox = findViewById(R.id.sf__passcode_box);
        logoutButton = findViewById(R.id.sf__passcode_logout_button);
        logoutButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                signoutAllUsers();
            }
        });
        verifyButton = findViewById(R.id.sf__passcode_verify_button);
        verifyButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Editable passcode = passcodeField.getText();
                if (passcode != null) {
                    onSubmit(passcode.toString());
                }
            }
        });

        fingerImage = findViewById(R.id.sf__fingerprint_icon);
        faceImage = findViewById(R.id.sf__face_unlock_icon);
        bioInstrTitle = findViewById(R.id.sf__biometric_instructions_title);
        bioInstr = findViewById(R.id.sf__biometric_instructions);
        bioInstr.setText(getString(R.string.sf__biometric_allow_instructions, SalesforceSDKManager.getInstance().provideAppName()));
        passcodeField.announceForAccessibility(bioInstrTitle.getText());
        biometricBox = findViewById(R.id.sf__biometric_box);
        notNowButton = findViewById(R.id.sf__biometric_not_now_button);
        notNowButton.setTextColor(getResources().getColor(isDarkTheme ? R.color.sf__secondary_color_dark
                : R.color.sf__primary_color, null));
        notNowButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                biometricDeclined();
            }
        });
        enableButton = findViewById(R.id.sf__biometric_enable_button);
        enableButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                launchBiometricAuth();
            }
        });

        clearUi();
        // Asking passcode manager is a change passcode flow is required
        if (passcodeManager.isPasscodeChangeRequired()) {
            setMode(PasscodeMode.Change);
        } else {
            if (passcodeManager.hasStoredPasscode(this)) {
                PasscodeMode mode = passcodeManager.biometricEnabled() ? PasscodeMode.BiometricCheck : PasscodeMode.Check;
                setMode(mode);
            } else {
                setMode(PasscodeMode.Create);
            }
        }
        logoutEnabled = true;
        forceBiometric = false;
        if (savedInstanceState != null) {
            final String inputText = savedInstanceState.getString(EXTRA_KEY);
            if (passcodeField != null && inputText != null) {
                passcodeField.setText(inputText.trim());
            }
        }

        // Determine biometric hardware, default is fingerprint.
        // TODO: Remove check when min API >= 29
        if (VERSION.SDK_INT >= VERSION_CODES.Q) {
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_FACE)) {
                biometricType = BiometricType.FaceUnlock;
            } else if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_IRIS)) {
                biometricType = BiometricType.Iris;
            }
        }
    }

    protected void biometricDeclined() {
        if (passcodeManager.biometricEnabled()) {
            setMode(PasscodeMode.Check);
        } else {
            passcodeManager.setBiometricEnabled(PasscodeActivity.this, false);
            passcodeManager.unlock();
            done();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            moveTaskToBack(true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Saves the entered text before activity rotation.
     */
    @Override
    protected void onSaveInstanceState(Bundle savedInstance) {
        if (passcodeField != null && passcodeField.getText() != null) {
            savedInstance.putString(EXTRA_KEY, passcodeField.getText().toString());
        }
    }

    public PasscodeMode getMode() {
        return currentMode;
    }

    public void setMode(PasscodeMode newMode) {
        if (newMode == currentMode) return;
        if (newMode == PasscodeMode.EnableBiometric && !canShowBiometric()) {
            return;
        }
        if (newMode == PasscodeMode.BiometricCheck && !canShowBiometric()) {
            newMode = PasscodeMode.Check;
        }

        clearUi();
        switch(newMode) {
        case Check:
            title.setText(getString(R.string.sf__passcode_enter_title));
            title.setVisibility(View.VISIBLE);
            instr.setText(getString(R.string.sf__passcode_enter_instructions));
            instr.setVisibility(View.VISIBLE);
            passcodeBox.setVisibility(View.VISIBLE);
            passcodeField.setVisibility(View.VISIBLE);

            if (!passcodeManager.getPasscodeLengthKnown()) {
                verifyButton.setVisibility(View.VISIBLE);
            }
            showKeyboard();
            sendAccessibilityEvent(instr.getText().toString());
            break;
        case Create:
            title.setText(getString(R.string.sf__passcode_create_title));
            title.setVisibility(View.VISIBLE);
            // Check if passcodes did not match
            int instructionText = (currentMode == PasscodeMode.CreateConfirm) ? R.string.sf__passcodes_dont_match
                                                                              : R.string.sf__passcode_create_instructions;
            instr.setText(getString(instructionText));
            instr.setVisibility(View.VISIBLE);
            passcodeBox.setVisibility(View.VISIBLE);
            passcodeField.setVisibility(View.VISIBLE);
            passcodeField.requestFocus();
            showKeyboard();
            sendAccessibilityEvent(instr.getText().toString());
            break;
        case CreateConfirm:
            title.setText(getString(R.string.sf__passcode_confirm_title));
            title.setVisibility(View.VISIBLE);
            instr.setText(getString(R.string.sf__passcode_confirm_instructions));
            instr.setVisibility(View.VISIBLE);
            passcodeBox.setVisibility(View.VISIBLE);
            passcodeField.setVisibility(View.VISIBLE);
            passcodeField.requestFocus();
            showKeyboard();
            sendAccessibilityEvent(instr.getText().toString());
            break;
        case Change:
            title.setText(getString(R.string.sf__passcode_change_title));
            title.setVisibility(View.VISIBLE);
            instr.setText(getString(R.string.sf__passcode_change_instructions));
            instr.setVisibility(View.VISIBLE);
            passcodeBox.setVisibility(View.VISIBLE);
            passcodeField.setVisibility(View.VISIBLE);
            passcodeField.requestFocus();
            showKeyboard();
            sendAccessibilityEvent(instr.getText().toString());
        	break;
        case EnableBiometric:
            switch(biometricType) {
                case FaceUnlock:
                    title.setText(getString(R.string.sf__biometric_face_title));
                    bioInstrTitle.setText(getString(R.string.sf__biometric_face_allow_instructions_title));
                    faceImage.setVisibility(View.VISIBLE);
                    break;
                case Iris:
                    title.setText(getString(R.string.sf__biometric_iris_title));
                    bioInstrTitle.setText(getString(R.string.sf__biometric_iris_allow_instructions_title));
                    faceImage.setVisibility(View.VISIBLE);
                    break;
                case Fingerprint:
                    title.setText(getString(R.string.sf__biometric_fingerprint_title));
                    bioInstrTitle.setText(getString(R.string.sf__biometric_fingerprint_allow_instructions_title));
                    fingerImage.setVisibility(View.VISIBLE);
            }

            hideKeyboard();
            title.setVisibility(View.VISIBLE);
            biometricBox.setVisibility(View.VISIBLE);
            bioInstrTitle.setVisibility(View.VISIBLE);
            sendAccessibilityEvent(bioInstrTitle.getText().toString());
            bioInstr.setVisibility(View.VISIBLE);
            notNowButton.setVisibility(View.VISIBLE);
            enableButton.setVisibility(View.VISIBLE);
            passcodeManager.setBiometricEnrollmentShown(this, true);
            break;
        case BiometricCheck:
            hideKeyboard();
            launchBiometricAuth();
        }
        passcodeField.setText("");
        currentMode = newMode;
    }

    /**
     * Used from tests to allow/disallow automatic logout when wrong passcode has been entered too many times.
     *
     * @param b True - if logout is enabled, False - otherwise.
     */
    public void enableLogout(boolean b) {
        logoutEnabled = b;
    }

    /**
     * Used for tests to allow biometric when the device is not set up
     *
     * @param b True - if biometric checks skipped, False - otherwise.
     */
    public void forceBiometric(boolean b) {
        forceBiometric = b;
    }

    protected boolean onSubmit(String enteredPasscode) {
        boolean showBiometricEnrollment = !passcodeManager.biometricEnabled() &&
                                          !passcodeManager.biometricEnrollmentShown() &&
                                          passcodeManager.biometricAllowed() &&
                                          canShowBiometric();

        switch (getMode()) {
        case Create:
        case Change:
            firstPasscode = enteredPasscode;
            setMode(PasscodeMode.CreateConfirm);
            return true;

        case CreateConfirm:
            if (enteredPasscode.equals(firstPasscode)) {
                passcodeManager.store(this, enteredPasscode);

                if (showBiometricEnrollment) {
                    setMode(PasscodeMode.EnableBiometric);
                } else {
                    passcodeManager.unlock();
                    done();
                }
            } else {
                setMode(PasscodeMode.Create);
            }
            return true;

        case Check:
            if (passcodeManager.check(this, enteredPasscode)) {
                sendAccessibilityEvent(getString(R.string.sf__accessibility_unlock_announcement));
                if (!passcodeManager.getPasscodeLengthKnown()) {
                    passcodeManager.setPasscodeLength(this, enteredPasscode.length());
                }

                if (showBiometricEnrollment) {
                    setMode(PasscodeMode.EnableBiometric);
                } else {
                    passcodeManager.unlock();
                    done();
                }
            } else {
                logoutButton.setVisibility(View.VISIBLE);
                int attempts = passcodeManager.addFailedPasscodeAttempt();
                passcodeField.setText("");
                int maxAttempts = getMaxPasscodeAttempts();
                if (attempts < maxAttempts - 1) {
                    instr.setText(getString(R.string.sf__passcode_try_again, (maxAttempts - attempts)));
                    sendAccessibilityEvent(instr.getText().toString());
                } else if (attempts < maxAttempts) {
                    instr.setText(getString(R.string.sf__passcode_final));
                    sendAccessibilityEvent(instr.getText().toString());
                } else {
                    signoutAllUsers();
                }
            }
            return true;
        }
        return false;
    }

    protected void done() {
        setResult(RESULT_OK);
        finish();
    }

    /**
     * @return maximum number of passcode attempts
     */
    protected int getMaxPasscodeAttempts() {
        return MAX_PASSCODE_ATTEMPTS;
    }

    private void signoutAllUsers() {
        passcodeManager.reset(this);
        sendAccessibilityEvent(getString(R.string.sf__accessibility_logged_out_announcement));

        // Used for tests
        if (!logoutEnabled) {
            return;
        }

        final UserAccountManager userAccMgr = SalesforceSDKManager.getInstance().getUserAccountManager();
        final List<UserAccount> userAccounts = userAccMgr.getAuthenticatedUsers();

        /*
         * If the user forgot his/her passcode, we log all the authenticated
         * users out. All the existing accounts except the last account
         * are removed without dismissing the PasscodeActivity. The last
         * account is removed, after which the PasscodeActivity is dismissed,
         * and the login page is brought up at this point.
         */
        if (userAccounts != null) {
            int numAccounts = userAccounts.size();
            if (numAccounts > 0) {
                for (int i = 0; i < numAccounts - 1; i++) {
                    final UserAccount account = userAccounts.get(i);
                    userAccMgr.signoutUser(account, null, false);
                }
                final UserAccount lastAccount = userAccounts.get(numAccounts - 1);
                userAccMgr.signoutUser(lastAccount, PasscodeActivity.this);
            }
        } else {
            userAccMgr.signoutCurrentUser(PasscodeActivity.this);
        }
    }

    /**
     * Displays the fingerprint dialog. This can be overridden to provide
     * a custom fingerprint auth layout if the app chooses to do so.
     */
    protected void showFingerprintDialog() {
        final FingerprintAuthDialogFragment fingerprintAuthDialog = new FingerprintAuthDialogFragment();
        fingerprintAuthDialog.setContext(this);
        fingerprintAuthDialog.show(getFragmentManager(), "fingerprintDialog");
    }

    /**
     * Displays the dialog provided by the OS for biometric authentication
     * using {@link BiometricPrompt}.
     */
    @TargetApi(VERSION_CODES.P)
    protected void showBiometricDialog() {
        // TODO: Remove this check once minAPI >= 28.
        if (VERSION.SDK_INT >= VERSION_CODES.P) {
            switch(biometricType) {
                case FaceUnlock:
                    biometricTitle = getString(R.string.sf__face_unlock_title);
                    biometricDescription = getString(R.string.sf__face_unlock_description,
                            SalesforceSDKManager.getInstance().provideAppName());
                    break;
                case Iris:
                    biometricTitle = getString(R.string.sf__iris_title);
                    biometricDescription = getString(R.string.sf__iris_description,
                            SalesforceSDKManager.getInstance().provideAppName());
                    break;
                case Fingerprint:
                    biometricTitle = getString(R.string.sf__fingerprint_title);
                    biometricDescription = getString(R.string.sf__fingerprint_description,
                            SalesforceSDKManager.getInstance().provideAppName());
            }

            final BiometricPrompt.Builder bioBuilder = new BiometricPrompt.Builder(this);
            bioBuilder.setDescription(biometricDescription);
            bioBuilder.setTitle(biometricTitle);
            bioBuilder.setNegativeButton(getString(R.string.sf__biometric_cancel), getMainExecutor(),
                    new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    biometricDeclined();
                }
            });

            final BiometricPrompt bioPrompt = bioBuilder.build();
            bioPrompt.authenticate(new CancellationSignal(), getMainExecutor(),
                    new BiometricPrompt.AuthenticationCallback() {

                @Override
                public void onAuthenticationError(int errorCode, CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    biometricDeclined();
                }

                @Override
                public void onAuthenticationHelp(int helpCode, CharSequence helpString) {
                    super.onAuthenticationHelp(helpCode, helpString);
                }

                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    unlockViaFingerprintScan();
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                }
            });
        }
    }

    @TargetApi(VERSION_CODES.Q)
    private boolean isBiometricEnabled() {
        // Used for tests
        if (forceBiometric) {
            return true;
        }

        // TODO: Remove this check once minAPI >= 29.
        if (VERSION.SDK_INT >= VERSION_CODES.Q) {
            final BiometricManager biometricManager = (BiometricManager) this.getSystemService(Context.BIOMETRIC_SERVICE);

            if (checkSelfPermission(permission.USE_BIOMETRIC) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{permission.USE_BIOMETRIC}, REQUEST_CODE_ASK_PERMISSIONS);
            } else {
                return biometricManager != null && biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS;
            }
        }
        return false;
    }

    private boolean isFingerprintEnabled() {
        // Used for tests
        if (forceBiometric) {
            return true;
        }
        final FingerprintManager fingerprintManager = (FingerprintManager) this.getSystemService(Context.FINGERPRINT_SERVICE);

        // Here, this activity is the current activity.
        if (checkSelfPermission(Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{ permission.USE_FINGERPRINT}, REQUEST_CODE_ASK_PERMISSIONS);
        } else {
            return fingerprintManager != null && fingerprintManager.isHardwareDetected()
                    && fingerprintManager.hasEnrolledFingerprints();
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_ASK_PERMISSIONS && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchBiometricAuth();
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public void unlockViaFingerprintScan() {
        if (!passcodeManager.biometricEnabled()) {
            passcodeManager.setBiometricEnabled(this, true);
        }
        passcodeManager.unlock();
        done();
    }

    private boolean canShowBiometric() {
        boolean bioEnabled = (VERSION.SDK_INT >= VERSION_CODES.Q ? isBiometricEnabled() : isFingerprintEnabled());
        return passcodeManager.biometricAllowed() && bioEnabled;
    }

    private void launchBiometricAuth() {
        if (passcodeManager != null && canShowBiometric()) {
            // TODO: Remove this check once minAPI >= 28.
            if (VERSION.SDK_INT >= VERSION_CODES.P) {
                showBiometricDialog();
            } else if (VERSION.SDK_INT >= VERSION_CODES.M) {
                showFingerprintDialog();
            }
        }
    }

    private void clearUi() {
        title.setVisibility(View.GONE);
        instr.setVisibility(View.GONE);
        passcodeField.setVisibility(View.GONE);
        passcodeBox.setVisibility(View.GONE);
        logoutButton.setVisibility(View.GONE);
        verifyButton.setVisibility(View.GONE);
        bioInstrTitle.setVisibility(View.GONE);
        bioInstr.setVisibility(View.GONE);
        notNowButton.setVisibility(View.GONE);
        enableButton.setVisibility(View.GONE);
        biometricBox.setVisibility(View.GONE);
        fingerImage.setVisibility(View.GONE);
        faceImage.setVisibility(View.GONE);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) this.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (imm != null && this.passcodeField != null) {
            imm.hideSoftInputFromWindow(this.passcodeField.getWindowToken(), 0);
        }
    }

    private void showKeyboard() {
        AccessibilityManager am = (AccessibilityManager) this.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am != null && am.isEnabled()) {
            // Check if keyboard is shown based on verify button, which is oriented to the bottom of
            // the layout.  Checking window instead of screen even works for split screen.
            int[] location = new int[2];
            verifyButton.getLocationInWindow(location);
            if (location[1] == 0) {
                passcodeField.requestFocus();
            }
        } else {
            passcodeField.requestFocus();
        }
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