/*
 * Copyright (c) 2011, salesforce.com, inc.
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

import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.security.PasscodeManager;

/**
 * Passcode activity: takes care of creating/verifying a user passcode.
 */
public class PasscodeActivity extends Activity implements OnEditorActionListener, OnClickListener {

    private static final String EXTRA_KEY = "input_text";
    private static final String LOGOUT_EXTRA = "logout_key";
    protected static final int MAX_PASSCODE_ATTEMPTS = 10;

    private PasscodeMode currentMode;
    private TextView title, instr, error;
    private EditText entry;
    private PasscodeManager passcodeManager;
    private String firstPasscode;
    private SalesforceR salesforceR;
    private boolean logoutEnabled;
    private AlertDialog logoutAlertDialog;
    private boolean isLogoutAlertShowing;

    public enum PasscodeMode {
        Create,
        CreateConfirm,
        Check,
        Change;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Object which allows reference to resources living outside the SDK.
        salesforceR = SalesforceSDKManager.getInstance().getSalesforceR();
        setContentView(getLayoutId());
        final TextView forgotPasscodeView = getForgotPasscodeView();
        if (forgotPasscodeView != null) {
            forgotPasscodeView.setText(Html.fromHtml(getForgotPasscodeString()));
        }
        forgotPasscodeView.setOnClickListener(this);
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
        	shouldChangePasscode = i.getBooleanExtra(PasscodeManager.CHANGE_PASSCODE_KEY,
        			false);
        }
        if (shouldChangePasscode) {
        	setMode(PasscodeMode.Change);
        } else {
            setMode(passcodeManager.hasStoredPasscode(this) ? PasscodeMode.Check : PasscodeMode.Create);
        }
        Log.i("PasscodeActivity:onCreate", "Mode: " + getMode());
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
            break;
        case Create:
            title.setText(getCreateTitle());
            instr.setText(getCreateInstructions());
            break;
        case CreateConfirm:
            title.setText(getConfirmTitle());
            instr.setText(getConfirmInstructions());
            break;
        case Change:
            title.setText(getCreateTitle());
            instr.setText(getChangeInstructions());
        	break;
        }
        entry.setText("");
        error.setText("");
        currentMode = newMode;
        entry.requestFocus();
    }

    /**
     * Used from tests to allow/disallow automatic logout when wrong passcode has been entered too many times
     * @param b
     */
    public void enableLogout(boolean b) {
        logoutEnabled = b;
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        Log.i("onEditorAction", "view=" + v + " actionId=" + actionId + " event=" + event);
        String pc = entry.getText().toString();
        if (pc.length() > 0 && pc.length() < getMinPasscodeLength()) {
            error.setText(getMinLengthInstructions(getMinPasscodeLength()));
            return false;
        }
        return pc.length() > 0 ? onSubmit(pc) : false;
    }

    protected boolean onSubmit(String enteredPasscode) {
        switch (getMode()) {
        case Create:
            firstPasscode = enteredPasscode;
            setMode(PasscodeMode.CreateConfirm);
            return false;

        case CreateConfirm:
            if (enteredPasscode.equals(firstPasscode)) {
                final String oldPass = passcodeManager.getPasscodeHash();
                passcodeManager.store(this, enteredPasscode);
                SalesforceSDKManager.getInstance().changePasscode(oldPass,
                		passcodeManager.hashForEncryption(enteredPasscode));
                passcodeManager.unlock(enteredPasscode);
                done();
            } else {
                error.setText(getPasscodesDontMatchError());
            }
            return true;

        case Check:
            if (passcodeManager.check(this, enteredPasscode)) {
                passcodeManager.unlock(enteredPasscode);
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
            return false;
        }
        return false;
    }

    protected void done() {
        setResult(RESULT_OK);
        finish();
    }

    protected int getLayoutId() {
        return salesforceR.layoutPasscode();
    }

    protected TextView getTitleView() {
        return (TextView) findViewById(salesforceR.idPasscodeTitle());
    }

    protected TextView getForgotPasscodeView() {
        return (TextView) findViewById(salesforceR.idPasscodeForgot());
    }

    protected TextView getErrorView() {
        return (TextView) findViewById(salesforceR.idPasscodeError());
    }

    protected TextView getInstructionsView() {
        return (TextView) findViewById(salesforceR.idPasscodeInstructions());
    }

    protected EditText getEntryView() {
        return (EditText) findViewById(salesforceR.idPasscodeText());
    }

    protected String getCreateTitle() {
    	return String.format(getString(salesforceR.stringPasscodeCreateTitle()), SalesforceSDKManager.getInstance().getAppDisplayString());
    }

    protected String getEnterTitle() {
    	return String.format(getString(salesforceR.stringPasscodeEnterTitle()), SalesforceSDKManager.getInstance().getAppDisplayString());
    }

    protected String getConfirmTitle() {
    	return String.format(getString(salesforceR.stringPasscodeConfirmTitle()), SalesforceSDKManager.getInstance().getAppDisplayString());
    }

    protected String getEnterInstructions() {
    	return String.format(getString(salesforceR.stringPasscodeEnterInstructions()), SalesforceSDKManager.getInstance().getAppDisplayString());
    }

    protected String getForgotPasscodeString() {
        return getString(salesforceR.stringPasscodeForgot());
    }

    protected String getLogoutConfirmationString() {
        return getString(salesforceR.stringPasscodeLogoutConfirmation());
    }

    protected String getLogoutYesString() {
        return getString(salesforceR.stringPasscodeLogoutYes());
    }

    protected String getLogoutNoString() {
        return getString(salesforceR.stringPasscodeLogoutNo());
    }

    protected String getCreateInstructions() {
    	return String.format(getString(salesforceR.stringPasscodeCreateInstructions()), SalesforceSDKManager.getInstance().getAppDisplayString());
    }

    protected String getChangeInstructions() {
    	return getString(salesforceR.stringPasscodeChangeInstructions());
    }

    protected String getConfirmInstructions() {
    	return String.format(getString(salesforceR.stringPasscodeConfirmInstructions()), SalesforceSDKManager.getInstance().getAppDisplayString());
    }

    protected String getMinLengthInstructions(int minPasscodeLength) {
        return getString(salesforceR.stringPasscodeMinLength(), minPasscodeLength);
    }

    protected String getPasscodeTryAgainError(int countAttemptsLeft) {
        return getString(salesforceR.stringPasscodeTryAgain(), countAttemptsLeft);
    }

    protected String getPasscodeFinalAttemptError() {
        return getString(salesforceR.stringPasscodeFinal());
    }

    protected String getPasscodesDontMatchError() {
        return getString(salesforceR.stringPasscodesDontMatch());
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
}