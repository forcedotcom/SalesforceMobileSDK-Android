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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.text.Html;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.app.SalesforceSDKUpgradeManager;
import com.salesforce.androidsdk.security.PasscodeManager;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Passcode activity: takes care of creating/verifying a user passcode.
 */
public class PasscodeActivity extends Activity implements OnEditorActionListener, OnClickListener {

    private static final String EXTRA_KEY = "input_text";
    private static final String LOGOUT_EXTRA = "logout_key";
    protected static final int MAX_PASSCODE_ATTEMPTS = 10;

    final private int REQUEST_CODE_ASK_PERMISSIONS = 11;

    private PasscodeMode currentMode;
    private TextView title, instr, error;
    private EditText entry;
    private PasscodeManager passcodeManager;
    private String firstPasscode;
    private boolean logoutEnabled;
    private AlertDialog logoutAlertDialog;
    private boolean isLogoutAlertShowing;

    public enum PasscodeMode {
        Create,
        CreateConfirm,
        Check,
        Change
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Protect against screenshots.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(getLayoutId());
        final TextView forgotPasscodeView = getForgotPasscodeView();
        if (forgotPasscodeView != null) {
            forgotPasscodeView.setText(Html.fromHtml(getForgotPasscodeString()));
            forgotPasscodeView.setOnClickListener(this);
        }
        logoutAlertDialog = buildLogoutDialog();
        title = getTitleView();
        error = getErrorView();
        instr = getInstructionsView();
        entry = getEntryView();
        entry.setOnEditorActionListener(this);
        passcodeManager = SalesforceSDKManager.getInstance().getPasscodeManager();
        final Intent i = getIntent();
        boolean shouldChangePasscode = false;
        if (i != null) {
            shouldChangePasscode = i.getBooleanExtra(PasscodeManager.CHANGE_PASSCODE_KEY, false);
        }
        if (shouldChangePasscode) {
            setMode(PasscodeMode.Change);
        } else {
            final PasscodeMode mode = passcodeManager.hasStoredPasscode(this) ? PasscodeMode.Check : PasscodeMode.Create;
            setMode(mode);
            if (mode == PasscodeMode.Check) {
                showFingerprintDialog();
            }
        }
        logoutEnabled = true;
        if (savedInstanceState != null) {
            final String inputText = savedInstanceState.getString(EXTRA_KEY);
            if (entry != null && inputText != null) {
                entry.setText(inputText.trim());
            }
            isLogoutAlertShowing = savedInstanceState.getBoolean(LOGOUT_EXTRA);
            if (isLogoutAlertShowing) {
                logoutAlertDialog.show();
            }
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
        if (entry != null && entry.getText() != null) {
            savedInstance.putString(EXTRA_KEY, entry.getText().toString());
        }
        if (isLogoutAlertShowing) {
        	logoutAlertDialog.dismiss();
            savedInstance.putBoolean(LOGOUT_EXTRA, true);
            isLogoutAlertShowing = false;
        }
    }

    public PasscodeMode getMode() {
        return currentMode;
    }

    public void setMode(PasscodeMode newMode) {
        if (newMode == currentMode) return;
        switch(newMode) {
        case Check:
            title.setText(getEnterTitle());
            instr.setText(getEnterInstructions());
            getForgotPasscodeView().setVisibility(View.VISIBLE);
            break;
        case Create:
            title.setText(getCreateTitle());
            instr.setText(getCreateInstructions());
            getForgotPasscodeView().setVisibility(View.INVISIBLE);
            break;
        case CreateConfirm:
            title.setText(getConfirmTitle());
            instr.setText(getConfirmInstructions());
            getForgotPasscodeView().setVisibility(View.INVISIBLE);
            break;
        case Change:
            title.setText(getCreateTitle());
            instr.setText(getChangeInstructions());
            getForgotPasscodeView().setVisibility(View.INVISIBLE);
        	break;
        }
        entry.setText("");
        error.setText("");
        currentMode = newMode;
        entry.requestFocus();
    }

    /**
     * Used from tests to allow/disallow automatic logout when wrong passcode has been entered too many times.
     *
     * @param b True - if logout is enabled, False - otherwise.
     */
    public void enableLogout(boolean b) {
        logoutEnabled = b;
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {

        // Processing the editor action only on key up to avoid sending events like pass code manager unlock twice.
        if ( actionId ==  EditorInfo.IME_ACTION_GO ||
                (event != null && event.getAction() == KeyEvent.ACTION_UP)) {
            String pc = entry.getText().toString();
            if (pc.length() >= 0 && pc.length() < getMinPasscodeLength()) {
                error.setText(getMinLengthInstructions(getMinPasscodeLength()));
                return true; // return true indicating we consumed the action.
            }
            return (pc.length() > 0 && onSubmit(pc));
        } else {
            return true;
        }
    }

    protected boolean onSubmit(String enteredPasscode) {
        switch (getMode()) {
        case Create:
            firstPasscode = enteredPasscode;
            setMode(PasscodeMode.CreateConfirm);
            return true;

        case CreateConfirm:
            if (enteredPasscode.equals(firstPasscode)) {
                passcodeManager.store(this, enteredPasscode);
                passcodeManager.unlock();
                done();
            } else {
                error.setText(getPasscodesDontMatchError());
            }
            return true;

        case Check:
            if (passcodeManager.check(this, enteredPasscode)) {
                performUpgradeStep(enteredPasscode);
                passcodeManager.unlock();
                done();
            } else {
                int attempts = passcodeManager.addFailedPasscodeAttempt();
                entry.setText("");
                int maxAttempts = getMaxPasscodeAttempts();
                if (attempts < maxAttempts - 1) {
                    error.setText(getPasscodeTryAgainError(maxAttempts - attempts));
                } else if (attempts < maxAttempts) {
                    error.setText(getPasscodeFinalAttemptError());
                } else {
                    passcodeManager.reset(this);
                    if (logoutEnabled) {
                        SalesforceSDKManager.getInstance().logout(this);
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

    /*
     * TODO: Remove this method, along with the one in UpgradeManager, in Mobile SDK 7.0.
     */
    private void performUpgradeStep(String passcode) {
        final String oldKey = passcodeManager.getLegacyEncryptionKey(passcode);
        final String newKey = SalesforceSDKManager.getEncryptionKey();
        final SalesforceSDKUpgradeManager upgradeManager = SalesforceSDKUpgradeManager.getInstance();
        if (upgradeManager.isPasscodeUpgradeRequired()) {

            /*
             * We need to store the new passcode to ensure the old verification
             * hash is overwritten with the new verification hash.
             */
            passcodeManager.store(this, passcode);

            /*
             * Checks if SmartStoreUpgradeManager is available and if it is, invokes it using
             * reflection since it is in a different library. This ensures that the database
             * upgrade happens if required. If it does not exist, falls back on regular upgrade.
             */
            try {
                final String smartStoreUpgradeClassName = "com.salesforce.androidsdk.smartstore.app.SmartStoreUpgradeManager";
                final String upgradeMethodName = "upgradeTo6Dot0";
                final Class<?>[] upgradeMethodArguments = { String.class, String.class };
                final Object[] upgradeArgumentValues = new Object[] { oldKey, newKey };
                final Class<?> clazz = Class.forName(smartStoreUpgradeClassName);
                final Method method = clazz.getMethod(upgradeMethodName, upgradeMethodArguments);
                final Object newInstance = clazz.newInstance();
                method.invoke(newInstance, upgradeArgumentValues);
            } catch (Exception e) {
                upgradeManager.upgradeTo6Dot0(oldKey, newKey);
            }
            upgradeManager.wipeUpgradeSharedPref();
        }
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

    protected TextView getForgotPasscodeView() {
        return (TextView) findViewById(R.id.sf__passcode_forgot);
    }

    protected TextView getErrorView() {
        return (TextView) findViewById(R.id.sf__passcode_error);
    }

    protected TextView getInstructionsView() {
        return (TextView) findViewById(R.id.sf__passcode_instructions);
    }

    protected EditText getEntryView() {
        return (EditText) findViewById(R.id.sf__passcode_text);
    }

    protected String getCreateTitle() {
    	return String.format(getString(R.string.sf__passcode_create_title),
                SalesforceSDKManager.getInstance().getAppDisplayString());
    }

    protected String getEnterTitle() {
    	return String.format(getString(R.string.sf__passcode_enter_title),
                SalesforceSDKManager.getInstance().getAppDisplayString());
    }

    protected String getConfirmTitle() {
    	return String.format(getString(R.string.sf__passcode_confirm_title),
                SalesforceSDKManager.getInstance().getAppDisplayString());
    }

    protected String getEnterInstructions() {
    	return String.format(getString(R.string.sf__passcode_enter_instructions),
                SalesforceSDKManager.getInstance().getAppDisplayString());
    }

    protected String getForgotPasscodeString() {
        return getString(R.string.sf__passcode_forgot_string);
    }

    protected String getLogoutConfirmationString() {
        return getString(R.string.sf__passcode_logout_confirmation);
    }

    protected String getLogoutYesString() {
        return getString(R.string.sf__passcode_logout_yes);
    }

    protected String getLogoutNoString() {
        return getString(R.string.sf__passcode_logout_no);
    }

    protected String getCreateInstructions() {
    	return String.format(getString(R.string.sf__passcode_create_instructions),
                SalesforceSDKManager.getInstance().getAppDisplayString());
    }

    protected String getChangeInstructions() {
    	return getString(R.string.sf__passcode_change_instructions);
    }

    protected String getConfirmInstructions() {
    	return String.format(getString(R.string.sf__passcode_confirm_instructions),
                SalesforceSDKManager.getInstance().getAppDisplayString());
    }

    protected String getMinLengthInstructions(int minPasscodeLength) {
        return getString(R.string.sf__passcode_min_length, minPasscodeLength);
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

    /**
     * @return minimum length of passcode
     */
    protected int getMinPasscodeLength() {
        return passcodeManager.getMinPasscodeLength();
    }

    /**
     * @return maximum number of passcode attempts
     */
    protected int getMaxPasscodeAttempts() {
        return MAX_PASSCODE_ATTEMPTS;
    }

	@Override
	public void onClick(View v) {
		if (v.equals(getForgotPasscodeView())) {
			logoutAlertDialog.show();
			isLogoutAlertShowing = true;
		}
	}

	/**
	 * Returns whether the logout alert dialog is showing.
	 *
	 * @return True - if the logout dialog is showing, False - otherwise.
	 */
	public boolean getIsLogoutDialogShowing() {
		return isLogoutAlertShowing;
	}

	/**
	 * Returns an instance of the logout alert dialog.
	 *
	 * @return Instance of the logout alert dialog.
	 */
	public AlertDialog getLogoutAlertDialog() {
		return logoutAlertDialog;
	}

    private AlertDialog buildLogoutDialog() {
        return new AlertDialog.Builder(this)
        .setMessage(getLogoutConfirmationString())
        .setPositiveButton(getLogoutYesString(),
                new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog,
                    int which) {
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
        }).setNegativeButton(getLogoutNoString(),
        		new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog,
                    int which) {
            	isLogoutAlertShowing = false;
            }
        })
        .create();
    }

    /**
     * Displays the fingerprint dialog. This can be overridden to provide
     * a custom fingerprint auth layout if the app chooses to do so.
     */
    protected void showFingerprintDialog() {
        if (passcodeManager != null && isFingerprintEnabled() && !SalesforceSDKUpgradeManager.getInstance().isPasscodeUpgradeRequired()) {
            final FingerprintAuthDialogFragment fingerprintAuthDialog = new FingerprintAuthDialogFragment();
            fingerprintAuthDialog.setContext(this);
            fingerprintAuthDialog.show(getFragmentManager(), "fingerprintDialog");
        }
    }

    @TargetApi(VERSION_CODES.M)
    private boolean isFingerprintEnabled() {

	    /*
         * TODO: Remove this check once minAPI > 23.
         */
        if (VERSION.SDK_INT >= VERSION_CODES.M) {
            final FingerprintManager fingerprintManager = (FingerprintManager) this.getSystemService(Context.FINGERPRINT_SERVICE);

            // Here, this activity is the current activity.
            if (checkSelfPermission(Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{ permission.USE_FINGERPRINT}, REQUEST_CODE_ASK_PERMISSIONS);
            } else {
                return fingerprintManager != null && fingerprintManager.isHardwareDetected() && fingerprintManager.hasEnrolledFingerprints();
            }
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_ASK_PERMISSIONS && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showFingerprintDialog();
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public void unlockViaFingerprintScan() {
        passcodeManager.unlock();
        done();
    }
}
