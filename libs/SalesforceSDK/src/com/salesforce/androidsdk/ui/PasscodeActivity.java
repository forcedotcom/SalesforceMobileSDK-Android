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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
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
import android.widget.Button;
import android.widget.ImageView;
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
    private ImageView fingerImage;
    private PasscodeManager passcodeManager;
    private String firstPasscode;
    private boolean logoutEnabled;

    public enum PasscodeMode {
        Create,
        CreateConfirm,
        Check,
        Change,
        EnableBiometric,
        BiometricCheck
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        passcodeManager = SalesforceSDKManager.getInstance().getPasscodeManager();

        // Protect against screenshots.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(getLayoutId());

        title = getTitleView();
        instr = getInstructionsView();
        passcodeField = getPasscodeField();
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
        passcodeBox = getPasscodeBox();
        logoutButton = getLogoutButton();
        logoutButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                signoutAllUsers();
            }
        });
        verifyButton = getVerifyButton();
        verifyButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Editable passcode = passcodeField.getText();
                if (passcode != null) {
                    onSubmit(passcode.toString());
                }
            }
        });

        fingerImage = getFingerImage();
        bioInstrTitle =  getBioInstrTitle();
        bioInstr = getBioInstr();
        bioInstr.setText(getBioInstrMessage());
        biometricBox = getBiometricBox();
        notNowButton = getNotNowButton();
        notNowButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                biometricDeclined();
            }
        });
        enableButton = getEnableButton();
        enableButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                passcodeManager.setBiometricEnabled(PasscodeActivity.this, true);
                launchBiometricAuth();
            }
        });

        clearUi();
        // Asking passcode manager is a change passcode flow is required
        if (passcodeManager.isPasscodeChangeRequired()) {
            setMode(PasscodeMode.Change);
        } else {
            if (passcodeManager.hasStoredPasscode(this)) {
                PasscodeMode mode = passcodeManager.getBiometricEnabled() ? PasscodeMode.BiometricCheck : PasscodeMode.Check;
                setMode(mode);
            } else {
                setMode(PasscodeMode.Create);
            }
        }
        logoutEnabled = true;
        if (savedInstanceState != null) {
            final String inputText = savedInstanceState.getString(EXTRA_KEY);
            if (passcodeField != null && inputText != null) {
                passcodeField.setText(inputText.trim());
            }
        }
    }

    protected void biometricDeclined() {
        if (passcodeManager.getBiometricEnabled()) {
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

        clearUi();
        switch(newMode) {
        case Check:
            title.setText(getEnterTitle());
            title.setVisibility(View.VISIBLE);
            instr.setText(getEnterInstructions());
            instr.setVisibility(View.VISIBLE);
            passcodeBox.setVisibility(View.VISIBLE);
            passcodeField.setVisibility(View.VISIBLE);

            if (!passcodeManager.getPasscodeLengthKnown()) {
                verifyButton.setVisibility(View.VISIBLE);
            }
            passcodeField.requestFocus();
            break;
        case Create:
            title.setText(getCreateTitle());
            title.setVisibility(View.VISIBLE);
            instr.setText(getCreateInstructions());
            instr.setVisibility(View.VISIBLE);
            passcodeBox.setVisibility(View.VISIBLE);
            passcodeField.setVisibility(View.VISIBLE);
            passcodeField.requestFocus();
            break;
        case CreateConfirm:
            title.setText(getConfirmTitle());
            title.setVisibility(View.VISIBLE);
            instr.setText(getConfirmInstructions());
            instr.setVisibility(View.VISIBLE);
            passcodeBox.setVisibility(View.VISIBLE);
            passcodeField.setVisibility(View.VISIBLE);
            passcodeField.requestFocus();
            break;
        case Change:
            title.setText(getCreateTitle());
            title.setVisibility(View.VISIBLE);
            instr.setText(getChangeInstructions());
            instr.setVisibility(View.VISIBLE);
            passcodeBox.setVisibility(View.VISIBLE);
            passcodeField.setVisibility(View.VISIBLE);
            passcodeField.requestFocus();
        	break;
        case EnableBiometric:
            title.setText(getBiometricTitle());
            title.setVisibility(View.VISIBLE);
            biometricBox.setVisibility(View.VISIBLE);
            bioInstrTitle.setVisibility(View.VISIBLE);
            bioInstr.setVisibility(View.VISIBLE);
            notNowButton.setVisibility(View.VISIBLE);
            enableButton.setVisibility(View.VISIBLE);
            fingerImage.setVisibility(View.VISIBLE);
            passcodeManager.setBiometricEnrollmentShown(this, true);
            break;
        case BiometricCheck:
            if (canShowBiometric()) {
                launchBiometricAuth();
            } else {
                setMode(PasscodeMode.Check);
            }
            break;
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

    protected boolean onSubmit(String enteredPasscode) {
        boolean showBiometricEnrollment = !passcodeManager.getBiometricEnabled() &&
                                          !passcodeManager.getBiometricEnrollmentShown() &&
                                          passcodeManager.getBiometricAllowed() &&
                                          canShowBiometric();

        switch (getMode()) {
        case Create:
            firstPasscode = enteredPasscode;
            setMode(PasscodeMode.CreateConfirm);
            return true;

        case CreateConfirm:
            if (enteredPasscode.equals(firstPasscode)) {
                passcodeManager.store(this, enteredPasscode);

                if (showBiometricEnrollment) {
                    setMode(PasscodeMode.EnableBiometric);
                } else {
                    if (!passcodeManager.getPasscodeLengthKnown()) {
                        passcodeManager.setPasscodeLength(this, enteredPasscode.length());
                    }

                    passcodeManager.unlock();
                    done();
                }
            } else {
                instr.setText(getPasscodesDontMatchError());
            }
            return true;

        case Check:
            if (passcodeManager.check(this, enteredPasscode)) {
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
                    instr.setText(getPasscodeTryAgainError(maxAttempts - attempts));
                } else if (attempts < maxAttempts) {
                    instr.setText(getPasscodeFinalAttemptError());
                } else {
                    passcodeManager.reset(this);
                    if (logoutEnabled) {
                        signoutAllUsers();
                    }
                }
            }
            return true;

        case Change:
            firstPasscode = enteredPasscode;
            setMode(PasscodeMode.CreateConfirm);
            return true;
        }
        return false;
    }

    protected void done() {
        setResult(RESULT_OK);
        finish();
    }

    protected int getLayoutId() {
        return R.layout.sf__passcode;
    }

    protected TextView getTitleView() {
        return (TextView) findViewById(R.id.sf__passcode_title);
    }

    protected TextView getInstructionsView() {
        return (TextView) findViewById(R.id.sf__passcode_instructions);
    }

    protected PasscodeField getPasscodeField() {
        return (PasscodeField) findViewById(R.id.sf__passcode_text);
    }

    protected LinearLayout getPasscodeBox() {
        return findViewById(R.id.sf__passcode_box);
    }

    protected String getCreateTitle() {
    	return getString(R.string.sf__passcode_create_title);
    }

    protected String getEnterTitle() {
    	return getString(R.string.sf__passcode_enter_title);
    }

    protected String getConfirmTitle() {
    	return getString(R.string.sf__passcode_confirm_title);
    }

    protected String getEnterInstructions() {
    	return getString(R.string.sf__passcode_enter_instructions);
    }

    protected String getCreateInstructions() {
    	return getString(R.string.sf__passcode_create_instructions);
    }

    protected String getChangeInstructions() {
    	return getString(R.string.sf__passcode_change_instructions);
    }

    protected String getConfirmInstructions() {
    	return getString(R.string.sf__passcode_confirm_instructions);
    }

    protected String getPasscodeTryAgainError(int countAttemptsLeft) {
        return getString(R.string.sf__passcode_try_again, countAttemptsLeft);
    }

    protected String getPasscodeFinalAttemptError() {
        return getString(R.string.sf__passcode_final);
    }

    protected String getPasscodesDontMatchError() {
        return getString(R.string.sf__passcodes_dont_match);
    }

    protected Button getLogoutButton() {
        return findViewById(R.id.sf__passcode_logout_button);
    }

    protected Button getVerifyButton() {
        return findViewById(R.id.sf_passcode_verify_button);
    }

    protected TextView getBioInstrTitle() {
        return findViewById(R.id.sf__biometric_instructions_title);
    }

    protected TextView getBioInstr() {
        return findViewById(R.id.sf__biometric_instructions);
    }

    protected String getBioInstrMessage() {
        return getString(R.string.sf__biometric_allow_instructiuons, SalesforceSDKManager.getInstance().provideAppName());
    }

    protected LinearLayout getBiometricBox() {
        return findViewById(R.id.sf__biometric_box);
    }

    protected Button getNotNowButton() {
        return findViewById(R.id.sf__biometric_not_now_button);
    }

    protected Button getEnableButton() {
        return findViewById(R.id.sf__biometric_enable_button);
    }

    protected ImageView getFingerImage() {
        return findViewById(R.id.sf__fingerprint_icon);
    }

    protected String getBiometricTitle() {
        return getString(R.string.sf__biometric_title);
    }

    protected String getFingerprintDescription() {
        ApplicationInfo applicationInfo = getApplicationInfo();
        int resId = applicationInfo.labelRes;
        String appName = resId == 0 ? "" : getString(resId);

        return getString(R.string.sf__fingerprint_description, appName);
    }

    /**
     * @return maximum number of passcode attempts
     */
    protected int getMaxPasscodeAttempts() {
        return MAX_PASSCODE_ATTEMPTS;
    }

    private void signoutAllUsers() {
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
     * using {@link android.hardware.biometrics.BiometricPrompt}.
     */
    @TargetApi(VERSION_CODES.P)
    protected void showBiometricDialog() {

        /*
         * TODO: Remove this check once minAPI >= 28.
         */
        if (VERSION.SDK_INT >= VERSION_CODES.P) {
            final BiometricPrompt.Builder bioBuilder = new BiometricPrompt.Builder(this);
            bioBuilder.setDescription(getFingerprintDescription());
            bioBuilder.setTitle(getString(R.string.sf__fingerprint_title));
            bioBuilder.setNegativeButton(getString(R.string.sf__fingerprint_cancel), getMainExecutor(),
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

    @TargetApi(VERSION_CODES.M)
    private boolean isFingerprintEnabled() {
	    /*
         * TODO: Remove this check once minAPI >= 23.
         */
        if (VERSION.SDK_INT >= VERSION_CODES.M) {
            final FingerprintManager fingerprintManager = (FingerprintManager) this.getSystemService(Context.FINGERPRINT_SERVICE);

            // Here, this activity is the current activity.
            if (checkSelfPermission(Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{ permission.USE_FINGERPRINT}, REQUEST_CODE_ASK_PERMISSIONS);
            } else {
                return fingerprintManager != null && fingerprintManager.isHardwareDetected()
                        && fingerprintManager.hasEnrolledFingerprints();
            }
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
        if (!passcodeManager.getBiometricEnabled()) {
            passcodeManager.setBiometricEnabled(this, true);
        }
        passcodeManager.unlock();
        done();
    }

    private boolean canShowBiometric() {
        return passcodeManager.getBiometricAllowed() && isFingerprintEnabled();
    }

    private void launchBiometricAuth() {
        if (passcodeManager != null && canShowBiometric()) {
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
    }
}