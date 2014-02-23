/*
 * Copyright (c) 2014, salesforce.com, inc.
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
package com.salesforce.androidsdk.accounts;

import java.util.ArrayList;
import java.util.List;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.AuthenticatorService;

/**
 * This class acts as a manager that provides methods to access
 * user accounts that are currently logged in, and can be used
 * to add new user accounts.
 *
 * @author bhariharan
 */
public class UserAccountManager {

	private static final String CURRENT_USER_PREF = "current_user_info";
	private static final String USER_ID_KEY = "user_id";
	private static final String ORG_ID_KEY = "org_id";
	private static UserAccountManager INSTANCE;

	private Context context;
	private AccountManager accountManager;
	private String accountType;
	private String passcodeHash;

	/**
	 * Returns a singleton instance of this class.
	 *
	 * @return Instance of this class.
	 */
	public static UserAccountManager getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new UserAccountManager();
		}
		return INSTANCE;
	}

	/**
	 * Private constructor.
	 */
	private UserAccountManager() {
		context = SalesforceSDKManager.getInstance().getAppContext();
		accountManager = AccountManager.get(context);
		accountType = SalesforceSDKManager.getInstance().getAccountType();
        passcodeHash = SalesforceSDKManager.getInstance().getPasscodeHash();
	}

	/**
	 * Stores the current active user in a shared preference file.
	 *
	 * @param userId User ID.
	 * @param orgId Org ID.
	 */
	public void storeCurrentUser(String userId, String orgId) {
		final SharedPreferences sp = context.getSharedPreferences(CURRENT_USER_PREF,
				Context.MODE_PRIVATE);
        final Editor e = sp.edit();
        e.putString(USER_ID_KEY, userId);
        e.putString(ORG_ID_KEY, orgId);
        e.commit();
	}

	/**
	 * Returns the current user logged in.
	 *
	 * @return Current user that's logged in.
	 */
	public UserAccount getCurrentUser() {
        final Account[] accounts = accountManager.getAccountsByType(accountType);
        if (accounts == null || accounts.length == 0) {
        	return null;
        }

        // Reads the stored user ID and org ID.
        final SharedPreferences sp = context.getSharedPreferences(CURRENT_USER_PREF,
				Context.MODE_PRIVATE);
        final String storedUserId = sp.getString(USER_ID_KEY, "");
        final String storedOrgId = sp.getString(ORG_ID_KEY, "");
        for (final Account account : accounts) {
        	if (account != null) {

        		// Reads the user ID and org ID from account manager.
                final String orgId = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(account,
                		AuthenticatorService.KEY_ORG_ID), passcodeHash);
        		final String userId = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(account,
        				AuthenticatorService.KEY_USER_ID), passcodeHash);
        		if (storedUserId.trim().equals(userId.trim())
        				&& storedOrgId.trim().equals(orgId.trim())) {
        			return buildUserAccount(account);
        		}
        	}
        }
		return null;
	}

	/**
	 * Returns a list of authenticated users.
	 *
	 * @return List of authenticated users.
	 */
	public List<UserAccount> getAuthenticatedUsers() {
        final Account[] accounts = accountManager.getAccountsByType(accountType);
        if (accounts == null || accounts.length == 0) {
        	return null;
        }
        final List<UserAccount> userAccounts = new ArrayList<UserAccount>();
        for (final Account account : accounts) {
        	final UserAccount userAccount = buildUserAccount(account);
        	if (userAccount != null) {
        		userAccounts.add(userAccount);
        	}
        }
        if (userAccounts.size() == 0) {
        	return null;
        }
        return userAccounts;
	}

	public void switchToUser(UserAccount user) {
		/*
		 * TODO:
		 */
		
	}

	public void switchToNewUser() {
		/*
		 * TODO:
		 */
		
	}

	public void signoutCurrentUser() {
		/*
		 * TODO:
		 */
		
	}

	public void signoutUser(UserAccount user) {
		/*
		 * TODO:
		 */
		
	}

	/**
	 * Builds a UserAccount object from the saved account.
	 *
	 * @param account Account object.
	 * @return UserAccount object.
	 */
	private UserAccount buildUserAccount(Account account) {
		if (account == null) {
			return null;
		}
		final String authToken = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(account,AccountManager.KEY_AUTHTOKEN), passcodeHash);
		final String refreshToken = SalesforceSDKManager.decryptWithPasscode(accountManager.getPassword(account), passcodeHash);
		final String loginServer = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(account, AuthenticatorService.KEY_LOGIN_URL), passcodeHash);
		final String idUrl = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(account, AuthenticatorService.KEY_ID_URL), passcodeHash);
		final String instanceServer = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(account, AuthenticatorService.KEY_INSTANCE_URL), passcodeHash);
		final String orgId = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(account, AuthenticatorService.KEY_ORG_ID), passcodeHash);
		final String userId = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(account, AuthenticatorService.KEY_USER_ID), passcodeHash);
		final String username = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(account, AuthenticatorService.KEY_USERNAME), passcodeHash);
		final String accountName = accountManager.getUserData(account, AccountManager.KEY_ACCOUNT_NAME);
		final String clientId = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(account, AuthenticatorService.KEY_CLIENT_ID), passcodeHash);
		if (authToken == null || instanceServer == null || userId == null || orgId == null) {
			return null;
		}
		return new UserAccount(authToken, refreshToken, loginServer, idUrl,
				instanceServer, orgId, userId, username, accountName, clientId);
	}
}
