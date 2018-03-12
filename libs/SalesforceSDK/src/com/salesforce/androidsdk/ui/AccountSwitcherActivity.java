/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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
import android.os.Bundle;
import android.view.View;
import android.widget.RadioGroup;

import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.app.SalesforceSDKManager;

import java.util.List;

/**
 * This class provides UI to switch between existing signed in user accounts,
 * or add a new account. This screen is popped off the activity stack
 * once the account switch is made.
 *
 * @author bhariharan
 */
public class AccountSwitcherActivity extends Activity {

	protected UserAccountManager userAccMgr;

	@Override
	public void onCreate(Bundle savedInstance) {
		super.onCreate(savedInstance);
		userAccMgr = SalesforceSDKManager.getInstance().getUserAccountManager();
		setContentView(R.layout.sf__account_switcher);
	}

	@Override
	public void onResume() {
		super.onResume();
		buildAccountList();
	}

	/**
	 * This method is triggered when the 'Apply' button is clicked. It
	 * switches the context to the selected account, if it is different
	 * from the current account.
	 *
	 * @param v View that was clicked.
	 */
	public void switchToExistingAccount(View v) {
        final RadioGroup radioGroup = findViewById(R.id.sf__accounts_group);
        int checkedId = radioGroup.getCheckedRadioButtonId();
		final SalesforceAccountRadioButton rb = radioGroup.findViewById(checkedId);
		if (rb != null) {
			final UserAccount account = rb.getAccount();
			accountSelected(account);
		}
		finishActivity();
	}

	/**
	 * This method is triggered when the 'Add New Account' button is clicked.
	 * It launches the login flow to sign into a new account.
	 *
	 * @param v View that was clicked.
	 */
	public void switchToNewAccount(View v) {
		accountSelected(null);
		finishActivity();
	}

	/**
	 * Finishes this activity.
	 */
	protected void finishActivity() {
		finish();
	}

	/**
	 * Performs the account switch operation. Pass in 'null' to kick off a new user login flow.
	 *
	 * @param account Account to switch to. Pass in 'null' to kick off a new user login flow.
	 */
	protected void accountSelected(UserAccount account) {
		if (account == null) {
			userAccMgr.switchToNewUser();
		} else {
			userAccMgr.switchToUser(account, UserAccountManager.USER_SWITCH_TYPE_DEFAULT, null);
		}
	}

    /**
     * Returns the list of user accounts to display.
     *
     * @return List of user accounts to display.
     */
	protected List<UserAccount> getAccounts() {
        return userAccMgr.getAuthenticatedUsers();
    }

	private void buildAccountList() {
        final RadioGroup radioGroup = findViewById(R.id.sf__accounts_group);
        radioGroup.removeAllViews();
        UserAccount curAccount = userAccMgr.getCurrentUser();
        final List<UserAccount> accounts = getAccounts();
		if (accounts == null || accounts.size() == 0) {
			return;
		}
		if (curAccount == null) {
			curAccount = accounts.get(0);
		}
		int curSelectedIndex = 0;
		for (int i = 0; i < accounts.size(); i++) {
			final UserAccount account = accounts.get(i);
			if (account != null) {
				setRadioState(radioGroup, account);
				if (account.equals(curAccount)) {
					curSelectedIndex = i;
				}
			}
		}

		/*
		 * Sets the current active account to a checked state.
		 */
		final SalesforceAccountRadioButton rb = (SalesforceAccountRadioButton) radioGroup.getChildAt(curSelectedIndex);
    	if (rb != null) {
    		rb.setChecked(true);
    	}
	}

    /**
     * Sets the radio state.
     *
     * @param radioGroup RadioGroup instance.
     * @param account UserAccount instance.
     */
    private void setRadioState(RadioGroup radioGroup, UserAccount account) {
    	final SalesforceAccountRadioButton rb = new SalesforceAccountRadioButton(this, account);
    	radioGroup.addView(rb);
    }
}
