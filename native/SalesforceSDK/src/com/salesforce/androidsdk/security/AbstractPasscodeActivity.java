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
package com.salesforce.androidsdk.security;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.salesforce.androidsdk.app.ForceApp;

/**
 * Abstract super class that takes care of creating/verifying a user passcode.
 */
public abstract class AbstractPasscodeActivity extends Activity implements OnEditorActionListener {

	protected static final int MAX_PASSCODE_ATTEMPTS = 3;
	protected static final int MIN_PASSCODE_LENGTH = 6;

	private PasscodeMode currentMode;
	private TextView title, instr, error;
	private EditText entry;
	private PasscodeManager passcodeManager;
	private String firstPasscode;
	
	enum PasscodeMode {
		Create,
		CreateConfirm,
		Check;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(getLayoutId());
		title = getTitleView();
		error = getErrorView();
		instr = getInstructionsView();
		entry = getEntryView();
		entry.setOnEditorActionListener(this);
		
		passcodeManager = ForceApp.APP.getPasscodeManager();
		setMode(passcodeManager.hasStoredPasscode(this) ? PasscodeMode.Check : PasscodeMode.Create);
	}
	
	protected PasscodeMode getMode() {
		return currentMode;
	}
	
	protected void setMode(PasscodeMode newMode) {
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
			title.setText(getEnterTitle());
			instr.setText(getConfirmInstructions());
			break;
		}
		
		entry.setText("");
		error.setText("");
		currentMode = newMode;
		entry.requestFocus();
	}
	
	@Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
    	Log.i("onEditorAction", "view=" + v + " actionId=" + actionId + " event=" + event);
    	String pc = entry.getText().toString();
    	if (pc.length() > 0 && pc.length() < getMinPasscodeLength()) {
    		Toast.makeText(this, getMinLengthInstructions(getMinPasscodeLength()), Toast.LENGTH_SHORT).show();
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
				passcodeManager.store(this, enteredPasscode);
				passcodeManager.unlock(enteredPasscode);
				finish();
			}
			else {
				error.setText(getPasscodesDontMatchError());
			}
			return true;

		case Check:
			if (passcodeManager.check(this, enteredPasscode)) {
				passcodeManager.unlock(enteredPasscode);
				finish();
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
					ForceApp.APP.logout(this);
				}
			}
			return true;
		}
		return false;
    }

	/**************************************************************************************************
	 * 
	 * Abstract methods: to be implemented by subclass
	 * 
	 **************************************************************************************************/

    /**
	 * @return id of layout to use for passcode screen
	 */
	abstract protected int getLayoutId();
    
	/**
	 * @return TextView showing title
	 */
	abstract protected TextView getTitleView();

	/**
	 * @return TextView showing error
	 */
	abstract protected TextView getErrorView();

	/**
	 * @return TextView showing instructions
	 */
	abstract protected TextView getInstructionsView();
	
	/**
	 * @return EditText to enter passcode
	 */
	abstract protected EditText getEntryView();

	
	/**************************************************************************************************
	 * 
	 * Other methods: likely to be overridden by sub class
	 * 
	 **************************************************************************************************/

	/**
	 * Override to have a localized error message.
	 * @return english create title
	 */
	protected String getCreateTitle() {
		return "Create Salesforce Passcode";
	}
	
	/**
	 * Override to have a localized error message.
	 * @return english enter title
	 */
	protected String getEnterTitle() {
		return "Enter Salesforce Passcode";
	}
	
	/**
	 * Override to have a localized error message.
	 * @return english enter instructions
	 */
	protected String getEnterInstructions() {
		return "Enter your mobile passcode for Salesforce";
	}
	
	/**
	 * Override to have a localized error message.
	 * @return english create instructions
	 */
	protected String getCreateInstructions() {
		return "For increased security, please create a passcode that you will use to access Salesforce when the session has timed out due to inactivity.";
	}
	
	/**
	 * Override to have a localized error message.
	 * @return english confirm instructions
	 */
	protected String getConfirmInstructions() {
		return "Confirm your mobile passcode for Salesforce";
	}
	
	/**
	 * Override to have a localized error message.
	 * @param minPasscodeLength
	 * @return english min length instructions
	 */
	protected String getMinLengthInstructions(int minPasscodeLength) {
		return String.format("The passcode must be at least %d characters long", minPasscodeLength);
	}
	
	/**
	 * Override to have a localized error message.
	 * @param countAttemptsLeft
	 * @return english error message for wrong passcode when more than one attempt is left
	 */
	protected String getPasscodeTryAgainError(int countAttemptsLeft) {
		return String.format("The passcode you entered is incorrect. Please try again. You have %d attempts remaining.", countAttemptsLeft);
	}

	/**
	 * Override to have a localized error message.
	 * @return english error message for wrong passcode when only one attempt is left
	 */
	protected String getPasscodeFinalAttemptError() {
		return "Final passcode attempt";
	}

	/**
	 * Override to have a localized error message.
	 * @return english error message for wrong passcode when only one attempt is left
	 */
	protected String getPasscodesDontMatchError() {
		return "Passcodes don't match!";
	}

	/**
	 * @return minimum length of passcode
	 */
	protected int getMinPasscodeLength() {
		return MIN_PASSCODE_LENGTH;
	}
	
	/**
	 * @return maximum number of passcode attempts
	 */
	protected int getMaxPasscodeAttempts() {
		return MAX_PASSCODE_ATTEMPTS;
	}
    
	
}