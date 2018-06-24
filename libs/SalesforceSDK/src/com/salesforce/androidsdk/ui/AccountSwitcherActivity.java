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
import android.widget.AdapterView;
import android.widget.ListView;

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
	 * This method is triggered when a user is selected from the list. It
	 * switches the context to the selected account, if it is different
	 * from the current account.
	 *
	 * @param account User account that was selected.
	 */
	public void switchToExistingAccount(UserAccount account) {
        accountSelected(account);
		finishActivity();
	}

	/**
	 * This method is triggered when the 'Add new account' button is clicked.
	 * It launches the login flow to sign into a new account.
	 */
	public void switchToNewAccount() {
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
	    final ListView listView = findViewById(R.id.sf__accounts_group);
	    final List<UserAccount> accounts = getAccounts();
		if (accounts == null || accounts.size() == 0) {
			return;
		}
        final UserAccount[] accountsArr = new UserAccount[accounts.size()];
		accounts.toArray(accountsArr);
        final UserAccountAdapter adapter = new UserAccountAdapter(this,
                R.layout.sf__account_switcher_list_item, accountsArr);
        listView.setAdapter(adapter);
        final View footer = getLayoutInflater().inflate(R.layout.sf__account_switcher_list_footer,
                null);
        listView.addFooterView(footer);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final UserAccount account = (UserAccount) parent.getItemAtPosition(position);

                /*
                 * Fetches the account that was clicked on. If account is null, this means
                 * the footer view was clicked, which will trigger the new user login flow.
                 */
                if (account != null) {
                    switchToExistingAccount(account);
                } else {
                    switchToNewAccount();
                }
            }
        });
	}
}
