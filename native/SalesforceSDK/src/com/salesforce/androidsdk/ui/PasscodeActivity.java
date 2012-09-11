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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.security.PasscodeManager;
import com.salesforce.androidsdk.security.PasscodeManager.PasscodeChangeReceiver;

/**
 * Passcode activity: takes care of creating/verifying a user passcode.
 */
public class PasscodeActivity extends Activity implements OnEditorActionListener {

    private static final String EXTRA_KEY = "input_text";
    protected static final int MAX_PASSCODE_ATTEMPTS = 3;

    private PasscodeMode currentMode;
    private TextView title, instr, error;
    private EditText entry;
    private PasscodeManager passcodeManager;
    private String firstPasscode;
    private SalesforceR salesforceR;
    private boolean logoutEnabled;

    public enum PasscodeMode {
        Create,
        CreateConfirm,
        Check;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Object which allows reference to resources living outside the SDK
        salesforceR = ForceApp.APP.getSalesforceR();
        setContentView(getLayoutId());
        title = getTitleView();
        error = getErrorView();
        instr = getInstructionsView();
        entry = getEntryView();
        entry.setOnEditorActionListener(this);
        passcodeManager = ForceApp.APP.getPasscodeManager();
        setMode(passcodeManager.hasStoredPasscode(this) ? PasscodeMode.Check : PasscodeMode.Create);
        logoutEnabled = true;
        if (savedInstanceState != null) {
            final String inputText = savedInstanceState.getString(EXTRA_KEY);
            if (entry != null && inputText != null) {
                entry.setText(inputText.trim());
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
                passcodeManager.unlock(enteredPasscode);
                final Intent intent = new Intent(PasscodeChangeReceiver.PASSCODE_FLOW_INTENT);
                intent.putExtra(PasscodeChangeReceiver.OLD_PASSCODE_EXTRA, oldPass);
                intent.putExtra(PasscodeChangeReceiver.NEW_PASSCODE_EXTRA, passcodeManager.getPasscodeHash());
                sendBroadcast(intent);
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
                        ForceApp.APP.logout(this);
                    }
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

    protected int getLayoutId() {
        return salesforceR.layoutPasscode();
    }

    protected TextView getTitleView() {
        return (TextView) findViewById(salesforceR.idPasscodeTitle());
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
        return getString(salesforceR.stringPasscodeCreateTitle());
    }

    protected String getEnterTitle() {
        return getString(salesforceR.stringPasscodeEnterTitle());
    }

    protected String getConfirmTitle() {
        return getString(salesforceR.stringPasscodeConfirmTitle());
    }

    protected String getEnterInstructions() {
        return getString(salesforceR.stringPasscodeEnterInstructions());
    }

    protected String getCreateInstructions() {
        return getString(salesforceR.stringPasscodeCreateInstructions());
    }

    protected String getConfirmInstructions() {
        return getString(salesforceR.stringPasscodeConfirmInstructions());
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
}
