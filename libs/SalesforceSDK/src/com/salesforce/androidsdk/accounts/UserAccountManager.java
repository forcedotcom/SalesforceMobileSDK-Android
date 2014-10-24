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
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.AuthenticatorService;
import com.salesforce.androidsdk.rest.ClientManager;

/**
 * This class acts as a manager that provides methods to access
 * user accounts that are currently logged in, and can be used
 * to add new user accounts.
 *
 * @author bhariharan
 */
public class UserAccountManager {

	/*
	 * NOTE:
	 *
	 * In many parts of this file, we repeatedly call
	 * 'SalesforceSDKManager.getInstance().getPasscodeHash()'.
	 * The reason we don't use an instance variable here is because
	 * the passcode hash changes every time the current user account
	 * changes, or when a login occurs. Using an instance variable
	 * leaves us with stale data, and becomes a nightmare to manage
	 * in terms of updating it every time an event that affects this
	 * value occurs. Hence, we always grab the current value from
	 * the source of truth.
	 */
	private static final String CURRENT_USER_PREF = "current_user_info";
	private static final String USER_ID_KEY = "user_id";
	private static final String ORG_ID_KEY = "org_id";

	public static final String USER_SWITCH_INTENT_ACTION = "com.salesforce.USERSWITCHED";

	private static UserAccountManager INSTANCE;

	private Context context;
	private AccountManager accountManager;
	private String accountType;

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
	 * Protected constructor.
	 */
	protected UserAccountManager() {
		context = SalesforceSDKManager.getInstance().getAppContext();
		accountManager = AccountManager.get(context);
		accountType = SalesforceSDKManager.getInstance().getAccountType();
	}

	/**
	 * Stores the current active user's user ID and org ID in a shared preference file.
	 *
	 * @param userId User ID.
	 * @param orgId Org ID.
	 */
	public void storeCurrentUserInfo(String userId, String orgId) {
		final SharedPreferences sp = context.getSharedPreferences(CURRENT_USER_PREF,
				Context.MODE_PRIVATE);
        final Editor e = sp.edit();
        e.putString(USER_ID_KEY, userId);
        e.putString(ORG_ID_KEY, orgId);
        e.commit();
	}

	/**
	 * Returns the stored user ID.
	 *
	 * @return User ID.
	 */
	public String getStoredUserId() {
		final SharedPreferences sp = context.getSharedPreferences(CURRENT_USER_PREF,
				Context.MODE_PRIVATE);
        return sp.getString(USER_ID_KEY, null);
	}

	/**
	 * Returns the stored org ID.
	 *
	 * @return Org ID.
	 */
	public String getStoredOrgId() {
		final SharedPreferences sp = context.getSharedPreferences(CURRENT_USER_PREF,
				Context.MODE_PRIVATE);
        return sp.getString(ORG_ID_KEY, null);
	}

	/**
	 * Returns the current user logged in.
	 *
	 * @return Current user that's logged in.
	 */
	public UserAccount getCurrentUser() {
		return buildUserAccount(getCurrentAccount());
	}

	/**
	 * Returns the current user logged in.
	 *
	 * @return Current user that's logged in.
	 */
	public Account getCurrentAccount() {
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
                String passcodeHash = SalesforceSDKManager.getInstance().getPasscodeHash();
				final String orgId = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(account,
                		AuthenticatorService.KEY_ORG_ID), passcodeHash);
        		final String userId = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(account,
        				AuthenticatorService.KEY_USER_ID), passcodeHash);
        		if (storedUserId.trim().equals(userId)
        				&& storedOrgId.trim().equals(orgId)) {
        			return account;
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

	/**
	 * Returns whether the specified user account exists or not.
	 *
	 * @param account User account.
	 * @return True - if it exists, False - otherwise.
	 */
	public boolean doesUserAccountExist(UserAccount account) {
		if (account == null) {
			return false;
		}
		final List<UserAccount> userAccounts = getAuthenticatedUsers();
		if (userAccounts == null || userAccounts.size() == 0) {
			return false;
		}
		for (final UserAccount userAccount : userAccounts) {
			if (account.equals(userAccount)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Switches to the specified user account. If the specified user account
	 * is invalid/doesn't exist, this method kicks off the login flow
	 * for a new user. When the user account switch is complete, it is
	 * imperative for the app to update its cached references to RestClient,
	 * to avoid holding on to a RestClient from the previous user.
	 *
	 * @param user User account to switch to.
	 */
	public void switchToUser(UserAccount user) {
		if (user == null || !doesUserAccountExist(user)) {
			switchToNewUser();
			return;
		}
		final UserAccount curUser = getCurrentUser();

		/*
		 * Checks if we are attempting to switch to the current user.
		 * In this case, there's nothing to be done.
		 */
		if (user.equals(curUser)) {
			return;
		}
		final ClientManager cm = new ClientManager(context, accountType,
				SalesforceSDKManager.getInstance().getLoginOptions(), true);
		final Account account = cm.getAccountByName(user.getAccountName());
		storeCurrentUserInfo(user.getUserId(), user.getOrgId());
		cm.peekRestClient(account);
		sendUserSwitchIntent();
	}

	/**
	 * Kicks off the login flow to switch to a new user. Once the login
	 * flow is complete, the context will automatically become the
	 * new user's context and a call to peekRestClient() or getRestClient()
	 * in ClientManager will return a RestClient instance for the new user.
	 */
	public void switchToNewUser() {
		final Bundle reply = new Bundle();
		final Intent i = new Intent(context, SalesforceSDKManager.getInstance().getLoginActivityClass());
		i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Bundle options = SalesforceSDKManager.getInstance().getLoginOptions().asBundle();
        i.putExtras(options);
        reply.putParcelable(AccountManager.KEY_INTENT, i);
		context.startActivity(i);
	}

	/**
	 * Logs the current user out.
	 *
	 * @param frontActivity Front activity.
	 */
	public void signoutCurrentUser(Activity frontActivity) {
		SalesforceSDKManager.getInstance().logout(frontActivity);
	}

	/**
	 * Logs the current user out.
	 *
	 * @param frontActivity Front activity.
	 * @param showLoginPage True - if the login page should be shown, False - otherwise.
	 */
	public void signoutCurrentUser(Activity frontActivity, boolean showLoginPage) {
		SalesforceSDKManager.getInstance().logout(frontActivity, showLoginPage);
	}

	/**
	 * Logs the specified user out. If the user specified is not the current
	 * user, push notification un-registration will not take place.
	 *
	 * @param userAccount User account.
	 * @param frontActivity Front activity.
	 */
	public void signoutUser(UserAccount userAccount, Activity frontActivity) {
		final Account account = buildAccount(userAccount);
		SalesforceSDKManager.getInstance().logout(account, frontActivity);
	}

	/**
	 * Logs the specified user out. If the user specified is not the current
	 * user, push notification un-registration will not take place.
	 *
	 * @param userAccount User account.
	 * @param frontActivity Front activity.
	 * @param showLoginPage True - if the login page should be shown, False - otherwise.
	 */
	public void signoutUser(UserAccount userAccount, Activity frontActivity, boolean showLoginPage) {
		final Account account = buildAccount(userAccount);
		SalesforceSDKManager.getInstance().logout(account, frontActivity, showLoginPage);
	}

	/**
	 * Builds a UserAccount object from the saved account.
	 *
	 * @param account Account object.
	 * @return UserAccount object.
	 */
	public UserAccount buildUserAccount(Account account) {
		if (account == null) {
			return null;
		}
		String passcodeHash = SalesforceSDKManager.getInstance().getPasscodeHash();
		final String authToken = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(account, AccountManager.KEY_AUTHTOKEN), passcodeHash);
		final String refreshToken = SalesforceSDKManager.decryptWithPasscode(accountManager.getPassword(account), passcodeHash);
		final String loginServer = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(account, AuthenticatorService.KEY_LOGIN_URL), passcodeHash);
		final String idUrl = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(account, AuthenticatorService.KEY_ID_URL), passcodeHash);
		final String instanceServer = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(account, AuthenticatorService.KEY_INSTANCE_URL), passcodeHash);
		final String orgId = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(account, AuthenticatorService.KEY_ORG_ID), passcodeHash);
		final String userId = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(account, AuthenticatorService.KEY_USER_ID), passcodeHash);
		final String username = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(account, AuthenticatorService.KEY_USERNAME), passcodeHash);
		final String accountName = accountManager.getUserData(account, AccountManager.KEY_ACCOUNT_NAME);
		final String clientId = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(account, AuthenticatorService.KEY_CLIENT_ID), passcodeHash);
		final String encCommunityId = accountManager.getUserData(account, AuthenticatorService.KEY_COMMUNITY_ID);
        String communityId = null;
        if (encCommunityId != null) {
        	communityId = SalesforceSDKManager.decryptWithPasscode(encCommunityId,
        			passcodeHash);
        }
        final String encCommunityUrl = accountManager.getUserData(account, AuthenticatorService.KEY_COMMUNITY_URL);
        String communityUrl = null;
        if (encCommunityUrl != null) {
        	communityUrl = SalesforceSDKManager.decryptWithPasscode(encCommunityUrl,
        			passcodeHash);
        }
		if (authToken == null || instanceServer == null || userId == null || orgId == null) {
			return null;
		}
		return new UserAccount(authToken, refreshToken, loginServer, idUrl,
				instanceServer, orgId, userId, username, accountName, clientId,
				communityId, communityUrl);
	}

	/**
	 * Builds an Account object from the user account passed in.
	 *
	 * @param userAccount UserAccount object.
	 * @return Account object.
	 */
	public Account buildAccount(UserAccount userAccount) {
		final Account[] accounts = accountManager.getAccountsByType(accountType);
		if (userAccount == null) {
			return null;
		}
        if (accounts == null || accounts.length == 0) {
        	return null;
        }

        // Reads the user account's user ID and org ID.
        final String storedUserId = ((userAccount.getUserId() == null) ? "" : userAccount.getUserId());
        final String storedOrgId = ((userAccount.getOrgId() == null) ? "" : userAccount.getOrgId());
        for (final Account account : accounts) {
        	if (account != null) {

        		// Reads the user ID and org ID from account manager.
                String passcodeHash = SalesforceSDKManager.getInstance().getPasscodeHash();
				final String orgId = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(account,
                		AuthenticatorService.KEY_ORG_ID), passcodeHash);
        		final String userId = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(account,
        				AuthenticatorService.KEY_USER_ID), passcodeHash);
        		if (storedUserId.trim().equals(userId.trim())
        				&& storedOrgId.trim().equals(orgId.trim())) {
        			return account;
        		}
        	}
        }
		return null;
	}

	/**
	 * Broadcasts an intent that a user switch has occurred.
	 */
	public void sendUserSwitchIntent() {
		final Intent intent = new Intent(USER_SWITCH_INTENT_ACTION);
		intent.setPackage(context.getPackageName());
		SalesforceSDKManager.getInstance().getAppContext().sendBroadcast(intent);
	}
}
