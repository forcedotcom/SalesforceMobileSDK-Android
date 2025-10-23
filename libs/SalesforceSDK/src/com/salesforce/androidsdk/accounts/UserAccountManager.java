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
package com.salesforce.androidsdk.accounts;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.salesforce.androidsdk.app.Features;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.AuthenticatorService;
import com.salesforce.androidsdk.auth.OAuth2;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.security.BiometricAuthenticationManager;
import com.salesforce.androidsdk.security.ScreenLockManager;
import com.salesforce.androidsdk.ui.LoginActivity;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	private static final String TAG = "UserAccountManager";

	public static final String USER_SWITCH_INTENT_ACTION = "com.salesforce.USERSWITCHED";

	/**
	 * Represents how the current user has been switched to, as found in an intent sent to a {@link BroadcastReceiver}
	 * filtering {@link #USER_SWITCH_INTENT_ACTION}. User switching including logging in, logging out and switching between authenticated
	 * users. For backwards compatibility, the case where the last user has logged out is not included, as this currently does not
	 * send a broadcast.
	 */
	public static final String EXTRA_USER_SWITCH_TYPE = "com.salesforce.USER_SWITCH_TYPE";

	/**
	 * A switch has occurred between two authenticated users.
	 *
	 * <p>Use this constant with {@link #EXTRA_USER_SWITCH_TYPE}.</p>
	 */
	public static final int USER_SWITCH_TYPE_DEFAULT = -1;

	/**
	 * The first user has logged in and is being switched to. There were no users authenticated before this switch.
	 *
	 * <p>Use this constant with {@link #EXTRA_USER_SWITCH_TYPE}.</p>
	 */
	public static final int USER_SWITCH_TYPE_FIRST_LOGIN = 0;

	/**
	 * An additional user has logged in and is being switched to. There was at least one user authenticated before this switch.
	 *
	 * <p>Use this constant with {@link #EXTRA_USER_SWITCH_TYPE}.</p>
	 */
	public static final int USER_SWITCH_TYPE_LOGIN = 1;

	/**
	 * A user has a logged out and another authenticated user is being switched to.
	 *
	 * <p>Use this constant with {@link #EXTRA_USER_SWITCH_TYPE}.</p>
	 */
	public static final int USER_SWITCH_TYPE_LOGOUT = 2;

	private static UserAccountManager INSTANCE;

	private final Context context;
	private final AccountManager accountManager;
	private final String accountType;
	private UserAccount cachedCurrentUserAccount;

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
		clearCachedCurrentUser();
		final SharedPreferences sp = context.getSharedPreferences(CURRENT_USER_PREF,
				Context.MODE_PRIVATE);
		final Editor e = sp.edit();
		e.putString(USER_ID_KEY, userId);
		e.putString(ORG_ID_KEY, orgId);
		e.apply();
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
		cachedCurrentUserAccount = buildUserAccount(getCurrentAccount());
		return cachedCurrentUserAccount;
	}

	/**
	 * Returns a cached value of the current user.
	 *
	 * NB: The oauth tokens might be outdated
	 *     Should be used by methods that only care about the current user's identity (org id, user id etc)
	 *     Is faster than getCurrentUser()
	 *
	 * @return Current user that's logged in (with potentially outdated oauth tokens)
	 */
	public UserAccount getCachedCurrentUser() {
		return cachedCurrentUserAccount != null ? cachedCurrentUserAccount : getCurrentUser() /* will populate cachedCurrentUserAccount */ ;
	}

	/**
	 * Get rid of cached current user account
	 */
	public void clearCachedCurrentUser() {
		cachedCurrentUserAccount = null;
	}

	/**
	 * Returns the current user logged in.
	 *
	 * @return Current user that's logged in.
	 */
	public Account getCurrentAccount() {
		final Account[] accounts = accountManager.getAccountsByType(accountType);
		if (accounts.length == 0) {
			return null;
		}

		// Register feature MU if more than one user
		if (accounts.length > 1) {
			SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_MULTI_USERS);
		} else {
			SalesforceSDKManager.getInstance().unregisterUsedAppFeature(Features.FEATURE_MULTI_USERS);
		}

		// Reads the stored user ID and org ID.
		final SharedPreferences sp = context.getSharedPreferences(CURRENT_USER_PREF,
				Context.MODE_PRIVATE);
		final String storedUserId = sp.getString(USER_ID_KEY, "");
		final String storedOrgId = sp.getString(ORG_ID_KEY, "");
		for (final Account account : accounts) {
			if (account != null) {

				// Reads the user ID and org ID from account manager.
				final String encryptionKey = SalesforceSDKManager.getEncryptionKey();
				final String orgId = SalesforceSDKManager.decrypt(accountManager.getUserData(account,
						AuthenticatorService.KEY_ORG_ID), encryptionKey);
				final String userId = SalesforceSDKManager.decrypt(accountManager.getUserData(account,
						AuthenticatorService.KEY_USER_ID), encryptionKey);
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
		if (accounts.length == 0) {
			return null;
		}
		final List<UserAccount> userAccounts = new ArrayList<>();
		for (final Account account : accounts) {
			final UserAccount userAccount = buildUserAccount(account);
			if (userAccount != null) {
				userAccounts.add(userAccount);
			}
		}
		if (userAccounts.isEmpty()) {
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
		if (userAccounts == null || userAccounts.isEmpty()) {
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
		switchToUser(user, USER_SWITCH_TYPE_DEFAULT, null);
	}

	/**
	 * Switches to the specified user account.
	 *
	 * @param user the user account to switch to
	 * @param userSwitchType a {@code USER_SWITCH_TYPE} constant
	 * @param extras a optional Bundle of extras to pass additional
	 *               information during user switch
	 *
	 * @see #switchToUser(UserAccount)
	 */
	public void switchToUser(UserAccount user, int userSwitchType, Bundle extras) {
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
		final ClientManager cm = new ClientManager(context, accountType, true);
		final Account account = cm.getAccountByName(user.getAccountName());
		storeCurrentUserInfo(user.getUserId(), user.getOrgId());
		cm.peekRestClient(account);
		sendUserSwitchIntent(userSwitchType, extras);

		// Check if User has ScreenLock or Biometric Auth
		BiometricAuthenticationManager bioAuthManager =
				(BiometricAuthenticationManager) SalesforceSDKManager.getInstance().getBiometricAuthenticationManager();
		ScreenLockManager screenLockManager = (ScreenLockManager) SalesforceSDKManager.getInstance().getScreenLockManager();
		if (bioAuthManager != null && bioAuthManager.isEnabled()) {
			bioAuthManager.lock();
		} else if (screenLockManager != null && screenLockManager.isEnabled()) {
			screenLockManager.lock();
		}
	}

	/**
	 * Kicks off the login flow to switch to a new user. Once the login
	 * flow is complete, the context will automatically become the
	 * new user's context and a call to peekRestClient() or getRestClient()
	 * in ClientManager will return a RestClient instance for the new user.
	 */
	public void switchToNewUser() {
		final Bundle options = new Bundle();
		final Bundle reply = new Bundle();
		final Intent i = new Intent(context, SalesforceSDKManager.getInstance().getLoginActivityClass());
		i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		options.putBoolean(BiometricAuthenticationManager.SHOW_BIOMETRIC, false);
		options.putBoolean(LoginActivity.NEW_USER, true);
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
	 * Logs the current user out.
	 *
	 * @param frontActivity Front activity.
	 * @param showLoginPage True - if the login page should be shown, False - otherwise.
	 * @param reason The reason for the logout.
	 */
	public void signoutCurrentUser(Activity frontActivity, boolean showLoginPage, OAuth2.LogoutReason reason) {
		SalesforceSDKManager.getInstance().logout(null, frontActivity, showLoginPage, reason);
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
	 * Logs the specified user out. If the user specified is not the current
	 * user, push notification un-registration will not take place.
	 *
	 * @param userAccount User account.
	 * @param frontActivity Front activity.
	 * @param showLoginPage True - if the login page should be shown, False - otherwise.
	 * @param reason The reason for the logout.
	 */
	public void signoutUser(UserAccount userAccount, Activity frontActivity, boolean showLoginPage, OAuth2.LogoutReason reason) {
		final Account account = buildAccount(userAccount);
		SalesforceSDKManager.getInstance().logout(account, frontActivity, showLoginPage, reason);
	}

	/**
	 * Create AccountManager Account from the given UserAccount
	 *
	 * @param userAccount UserAccount object.
	 *
	 * @return auth bundle with encrypted values
	 */
	public Bundle createAccount(UserAccount userAccount) {
		final String encryptionKey = SalesforceSDKManager.getEncryptionKey();
		final Bundle extras = buildAuthBundle(userAccount);

		Account acc = new Account(userAccount.getAccountName(), accountType);
		String password = SalesforceSDKManager.encrypt(userAccount.getRefreshToken(), encryptionKey);
		boolean success = accountManager.addAccountExplicitly(acc, password, new Bundle());

		// Add account will fail if the account already exists, so update refresh token.
		if (!success) {
			accountManager.setPassword(acc, password);
		}

		/*
		 * Caching auth token otherwise the first call to 'accountManager.getAuthToken()' will go
		 * to the AuthenticatorService which will do a refresh. That is problematic when the
		 * refresh token is set to expire immediately.
		 */
		accountManager.setAuthToken(acc, AccountManager.KEY_AUTHTOKEN, SalesforceSDKManager.encrypt(userAccount.getAuthToken(), encryptionKey));

		// There is a bug in AccountManager::addAccountExplicitly() that sometimes causes user data to not be
		// saved when the user data is passed in through that method. The work-around is to call setUserData()
		// for all the user data manually after passing in empty user data into addAccountExplicitly().
		for (final String key : extras.keySet()) {

			// WARNING! This assumes all user data is a String!
			accountManager.setUserData(acc, key, extras.getString(key));
		}

		/*
		 * Sets this user as the current user
		 */
		storeCurrentUserInfo(userAccount.getUserId(), userAccount.getOrgId());
		return extras;
	}

	/**
	 * Update AccountManager Account for the given UserAccount
	 *
	 * @param account Account object to update.
	 * @param userAccount UserAccount object.
	 *
	 * @return auth bundle with encrypted values
	 */
	public Bundle updateAccount(Account account, UserAccount userAccount) {
		final Bundle extras = buildAuthBundle(userAccount);

		for (final String key : extras.keySet()) {
			accountManager.setUserData(account, key, extras.getString(key));
		}

		return extras;
	}

	/**
	 * Builds a UserAccount object from the saved account.
	 *
	 * @param account Account object.
	 * @return UserAccount object.
	 */
	public @Nullable UserAccount buildUserAccount(Account account) {
		if (account == null) {
			return null;
		}

		final String encryptionKey = SalesforceSDKManager.getEncryptionKey();
		final String accountName = accountManager.getUserData(account, AccountManager.KEY_ACCOUNT_NAME);

		// Maintenance Note: All account values are nullable by default.  If a value requires a default value when user's of older versions experience access token refresh, provide that here.
		final String refreshToken = SalesforceSDKManager.decrypt(accountManager.getPassword(account), encryptionKey);
		final String authToken = decryptUserData(account, AccountManager.KEY_AUTHTOKEN, encryptionKey);
		final String loginServer = decryptUserData(account, AuthenticatorService.KEY_LOGIN_URL, encryptionKey);
		final String idUrl = decryptUserData(account, AuthenticatorService.KEY_ID_URL, encryptionKey);
		final String instanceServer = decryptUserData(account, AuthenticatorService.KEY_INSTANCE_URL, encryptionKey);
		final String apiInstanceServer = decryptUserData(account, AuthenticatorService.KEY_API_INSTANCE_URL, encryptionKey);
		final String orgId = decryptUserData(account, AuthenticatorService.KEY_ORG_ID, encryptionKey);
		final String userId = decryptUserData(account, AuthenticatorService.KEY_USER_ID, encryptionKey);
		final String username = decryptUserData(account, AuthenticatorService.KEY_USERNAME, encryptionKey);
		final String lastName = decryptUserData(account, AuthenticatorService.KEY_LAST_NAME, encryptionKey);
		final String email = decryptUserData(account, AuthenticatorService.KEY_EMAIL, encryptionKey);
		final String language = decryptUserData(account, AuthenticatorService.KEY_LANGUAGE, encryptionKey);
		final String locale = decryptUserData(account, AuthenticatorService.KEY_LOCALE, encryptionKey);
		final boolean nativeLogin = Boolean.parseBoolean(decryptUserData(account, AuthenticatorService.KEY_NATIVE_LOGIN, encryptionKey));
		final String firstName = decryptUserData(account, AuthenticatorService.KEY_FIRST_NAME, encryptionKey);
		final String displayName = decryptUserData(account, AuthenticatorService.KEY_DISPLAY_NAME, encryptionKey);
		final String photoUrl = decryptUserData(account, AuthenticatorService.KEY_PHOTO_URL, encryptionKey);
		final String thumbnailUrl = decryptUserData(account, AuthenticatorService.KEY_THUMBNAIL_URL, encryptionKey);
		final String communityId = decryptUserData(account, AuthenticatorService.KEY_COMMUNITY_ID, encryptionKey);
		final String communityUrl = decryptUserData(account, AuthenticatorService.KEY_COMMUNITY_URL, encryptionKey);
		final String lightningDomain = decryptUserData(account, AuthenticatorService.KEY_LIGHTNING_DOMAIN, encryptionKey);
		final String lightningSid = decryptUserData(account, AuthenticatorService.KEY_LIGHTNING_SID, encryptionKey);
		final String vfDomain = decryptUserData(account, AuthenticatorService.KEY_VF_DOMAIN, encryptionKey);
		final String vfSid = decryptUserData(account, AuthenticatorService.KEY_VF_SID, encryptionKey);
		final String contentDomain = decryptUserData(account, AuthenticatorService.KEY_CONTENT_DOMAIN, encryptionKey);
		final String contentSid = decryptUserData(account, AuthenticatorService.KEY_CONTENT_SID, encryptionKey);
		final String csrfToken = decryptUserData(account, AuthenticatorService.KEY_CSRF_TOKEN, encryptionKey);
		final String cookieClientSrc = decryptUserData(account, AuthenticatorService.KEY_COOKIE_CLIENT_SRC, encryptionKey);
		final String cookieSidClient = decryptUserData(account, AuthenticatorService.KEY_COOKIE_SID_CLIENT, encryptionKey);
		final String sidCookieName = decryptUserData(account, AuthenticatorService.KEY_SID_COOKIE_NAME, encryptionKey);
		final String clientId = decryptUserData(account, AuthenticatorService.KEY_CLIENT_ID, encryptionKey);

		final String parentSid = decryptUserData(account, AuthenticatorService.KEY_PARENT_SID, encryptionKey);
		final String tokenFormat = decryptUserData(account, AuthenticatorService.KEY_TOKEN_FORMAT, encryptionKey);
		final String beaconChildConsumerKey = decryptUserData(account, AuthenticatorService.KEY_BEACON_CHILD_CONSUMER_KEY, encryptionKey);
		final String beaconChildConsumerSecret = decryptUserData(account, AuthenticatorService.KEY_BEACON_CHILD_CONSUMER_SECRET, encryptionKey);
		final String scope = decryptUserData(account, AuthenticatorService.KEY_SCOPE, encryptionKey);

		Map<String, String> additionalOauthValues = null;
		List<String> additionalOauthKeys = SalesforceSDKManager.getInstance().getAdditionalOauthKeys();

		if (additionalOauthKeys != null && !additionalOauthKeys.isEmpty()) {
			additionalOauthValues = new HashMap<>();
			for (String key : additionalOauthKeys) {
				if (!TextUtils.isEmpty(key)) {
					String value = decryptUserData(account, key, encryptionKey);
					additionalOauthValues.put(key, value);
				}
			}
		}


		if (authToken == null || instanceServer == null || userId == null || orgId == null) {
			return null;
		} else {
			return UserAccountBuilder.getInstance()
					.authToken(authToken)
					.refreshToken(refreshToken)
					.loginServer(loginServer)
					.idUrl(idUrl)
					.instanceServer(instanceServer)
					.apiInstanceServer(apiInstanceServer)
					.orgId(orgId)
					.userId(userId)
					.username(username)
					.accountName(accountName)
					.communityId(communityId)
					.communityUrl(communityUrl)
					.firstName(firstName)
					.lastName(lastName)
					.displayName(displayName)
					.email(email).photoUrl(photoUrl)
					.thumbnailUrl(thumbnailUrl)
					.lightningDomain(lightningDomain)
					.lightningSid(lightningSid)
					.vfDomain(vfDomain)
					.vfSid(vfSid)
					.contentDomain(contentDomain)
					.contentSid(contentSid)
					.csrfToken(csrfToken)
					.nativeLogin(nativeLogin)
					.language(language)
					.locale(locale)
					.cookieClientSrc(cookieClientSrc)
					.cookieSidClient(cookieSidClient)
					.sidCookieName(sidCookieName)
					.clientId(clientId)
					.parentSid(parentSid)
					.tokenFormat(tokenFormat)
					.beaconChildConsumerKey(beaconChildConsumerKey)
					.beaconChildConsumerSecret(beaconChildConsumerSecret)
					.scope(scope)
					.additionalOauthValues(additionalOauthValues)
					.build();
		}
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
		if (accounts.length == 0) {
			return null;
		}

		// Reads the user account's user ID and org ID.
		final String storedUserId = ((userAccount.getUserId() == null) ? "" : userAccount.getUserId());
		final String storedOrgId = ((userAccount.getOrgId() == null) ? "" : userAccount.getOrgId());
		for (final Account account : accounts) {
			if (account != null) {

				// Reads the user ID and org ID from account manager.
				final String encryptionKey = SalesforceSDKManager.getEncryptionKey();
				final String orgId = SalesforceSDKManager.decrypt(accountManager.getUserData(account,
						AuthenticatorService.KEY_ORG_ID), encryptionKey);
				final String userId = SalesforceSDKManager.decrypt(accountManager.getUserData(account,
						AuthenticatorService.KEY_USER_ID), encryptionKey);
				if (storedUserId.trim().equals(userId != null ? userId.trim() : null)
						&& storedOrgId.trim().equals(orgId != null ? orgId.trim() : null)) {
					return account;
				}
			}
		}
		return null;
	}

	/**
	 * Broadcasts an intent that a user switch has occurred.
	 *
	 * @param userSwitchType A {@code USER_SWITCH_TYPE} constant.
	 * @param extras An optional Bundle of extras to add to the broadcast intent.
	 */
	public final void sendUserSwitchIntent(int userSwitchType, Bundle extras) {
		final Intent intent = new Intent(USER_SWITCH_INTENT_ACTION);
		intent.setPackage(context.getPackageName());
		intent.putExtra(EXTRA_USER_SWITCH_TYPE, userSwitchType);
		if (extras != null) {
			intent.putExtras(extras);
		}
		SalesforceSDKManager.getInstance().getAppContext().sendBroadcast(intent);
	}

	/**
	 * Retrieves a stored user account from org ID and user ID.
	 *
	 * @param orgId Org ID.
	 * @param userId User ID.
	 * @return User account.
	 */
	public UserAccount getUserFromOrgAndUserId(String orgId, String userId) {
		if (TextUtils.isEmpty(orgId) || TextUtils.isEmpty(userId)) {
			return null;
		}
		final List<UserAccount> userAccounts = getAuthenticatedUsers();
		if (userAccounts == null || userAccounts.isEmpty()) {
			return null;
		}
		for (final UserAccount userAccount : userAccounts) {
			if (orgId.equals(userAccount.getOrgId()) && userId.equals(userAccount.getUserId())) {
				return userAccount;
			}
		}
		return null;
	}

	/**
	 * Attempts to refresh the access token for this user by making an API call
	 * to the "/token" endpoint. If the call succeeds, the new token is persisted.
	 * If the call fails and the refresh token is no longer valid, the user is logged out.
	 * This should NOT be called from the main thread because it makes a network request.
	 *
	 * @param userAccount User account whose token should be refreshed. Use 'null' for current user.
	 */
	public synchronized void refreshToken(UserAccount userAccount) {
		userAccount = (userAccount == null) ? getCurrentUser() : userAccount;
		if (userAccount == null) {
			return;
		}
		try {
			final ClientManager clientManager = SalesforceSDKManager.getInstance().getClientManager();
			final ClientManager.AccMgrAuthTokenProvider authTokenProvider = new ClientManager.AccMgrAuthTokenProvider(clientManager,
					userAccount.getInstanceServer(), userAccount.getAuthToken(), userAccount.getRefreshToken());
			authTokenProvider.getNewAuthToken();
		} catch (Exception e) {
			SalesforceSDKLogger.e(TAG, "Exception thrown while attempting to refresh token", e);
		}
	}

	/**
	 * Create bundle for authenticator service
	 * - it uses keys understood by authenticator service
	 * - it encrypts most values
	 * @param userAccount
	 * @return
	 */
	private Bundle buildAuthBundle(UserAccount userAccount) {
		final String encryptionKey = SalesforceSDKManager.getEncryptionKey();
		final Bundle extras = new Bundle();
		extras.putString(AccountManager.KEY_ACCOUNT_NAME, userAccount.getAccountName());
		extras.putString(AccountManager.KEY_ACCOUNT_TYPE, accountType);
		extras.putString(AuthenticatorService.KEY_USERNAME, SalesforceSDKManager.encrypt(userAccount.getUsername(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_LOGIN_URL, SalesforceSDKManager.encrypt(userAccount.getLoginServer(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_ID_URL, SalesforceSDKManager.encrypt(userAccount.getIdUrl(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_INSTANCE_URL, SalesforceSDKManager.encrypt(userAccount.getInstanceServer(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_API_INSTANCE_URL, SalesforceSDKManager.encrypt(userAccount.getApiInstanceServer(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_CLIENT_ID, SalesforceSDKManager.encrypt(userAccount.getClientId(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_ORG_ID, SalesforceSDKManager.encrypt(userAccount.getOrgId(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_USER_ID, SalesforceSDKManager.encrypt(userAccount.getUserId(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_COMMUNITY_ID, SalesforceSDKManager.encrypt(userAccount.getCommunityId(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_COMMUNITY_URL, SalesforceSDKManager.encrypt(userAccount.getCommunityUrl(), encryptionKey));
		extras.putString(AccountManager.KEY_AUTHTOKEN, SalesforceSDKManager.encrypt(userAccount.getAuthToken(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_FIRST_NAME, SalesforceSDKManager.encrypt(userAccount.getFirstName(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_LAST_NAME, SalesforceSDKManager.encrypt(userAccount.getLastName(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_DISPLAY_NAME, SalesforceSDKManager.encrypt(userAccount.getDisplayName(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_EMAIL, SalesforceSDKManager.encrypt(userAccount.getEmail(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_LANGUAGE, SalesforceSDKManager.encrypt(userAccount.getLanguage(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_LOCALE, SalesforceSDKManager.encrypt(userAccount.getLocale(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_PHOTO_URL, SalesforceSDKManager.encrypt(userAccount.getPhotoUrl(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_THUMBNAIL_URL, SalesforceSDKManager.encrypt(userAccount.getThumbnailUrl(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_LIGHTNING_DOMAIN, SalesforceSDKManager.encrypt(userAccount.getLightningDomain(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_LIGHTNING_SID, SalesforceSDKManager.encrypt(userAccount.getLightningSid(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_VF_DOMAIN, SalesforceSDKManager.encrypt(userAccount.getVFDomain(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_VF_SID, SalesforceSDKManager.encrypt(userAccount.getVFSid(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_CONTENT_DOMAIN, SalesforceSDKManager.encrypt(userAccount.getContentDomain(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_CONTENT_SID, SalesforceSDKManager.encrypt(userAccount.getContentSid(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_CSRF_TOKEN, SalesforceSDKManager.encrypt(userAccount.getCSRFToken(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_NATIVE_LOGIN,  SalesforceSDKManager.encrypt(userAccount.getNativeLogin().toString(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_COOKIE_SID_CLIENT, SalesforceSDKManager.encrypt(userAccount.getCookieSidClient(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_COOKIE_CLIENT_SRC, SalesforceSDKManager.encrypt(userAccount.getCookieClientSrc(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_SID_COOKIE_NAME, SalesforceSDKManager.encrypt(userAccount.getSidCookieName(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_PARENT_SID, SalesforceSDKManager.encrypt(userAccount.getParentSid(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_TOKEN_FORMAT, SalesforceSDKManager.encrypt(userAccount.getTokenFormat(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_BEACON_CHILD_CONSUMER_KEY, SalesforceSDKManager.encrypt(userAccount.getBeaconChildConsumerKey(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_BEACON_CHILD_CONSUMER_SECRET, SalesforceSDKManager.encrypt(userAccount.getBeaconChildConsumerSecret(), encryptionKey));
		extras.putString(AuthenticatorService.KEY_SCOPE, SalesforceSDKManager.encrypt(userAccount.getScope(), encryptionKey));

		final List<String> additionalOauthKeys = SalesforceSDKManager.getInstance().getAdditionalOauthKeys();
		if (additionalOauthKeys != null && !additionalOauthKeys.isEmpty()) {
			if (userAccount.getAdditionalOauthValues() != null && !userAccount.getAdditionalOauthValues().isEmpty()) {
				for (final String key : additionalOauthKeys) {
					final String value = userAccount.getAdditionalOauthValues().get(key);
					if (value != null) {
						extras.putString(key, SalesforceSDKManager.encrypt(value, encryptionKey));
					}
				}
			}
		}

		return extras;
	}

	private String decryptUserData(Account account, String key, String encryptionKey) {
		return  SalesforceSDKManager.decrypt(accountManager.getUserData(account, key), encryptionKey);
	}
}
