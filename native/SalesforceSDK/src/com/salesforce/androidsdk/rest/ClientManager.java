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
package com.salesforce.androidsdk.rest;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AccountsException;
import android.app.Activity;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.salesforce.androidsdk.auth.AuthenticatorService;
import com.salesforce.androidsdk.auth.HttpAccess;

/**
 * Use ClientManager to build RestClient's when authentication information is stored with the AccountManager.
 * When called from an activity, the login flow (which should create a account with the authentication information) is 
 * launched if required.
 * 
 * NB: we only store sensitive data (refresh token) in the account manager when the file system is encrypted
 *     when we don't store the refresh token, the user should expect to see the login screen again as soon as their auth token becomes invalid
 * 
 */
public class ClientManager {

	private final AccountManager accountManager;
	private DevicePolicyManager devicePolicyManager;
	private final String accountType;

	/**
	 * Construct a ClientManager using a custom account type
	 * @param ctx
	 * @param accountType
	 */
	public ClientManager(Context ctx, String accountType) {
		this.accountManager = AccountManager.get(ctx);		
		this.accountType = accountType;
		this.devicePolicyManager = (DevicePolicyManager) ctx.getSystemService(Service.DEVICE_POLICY_SERVICE);
	}

	/**
	 * We will only store sensitive data in the account manager when the file system is encrypted
	 * 
	 * @return true if file system encryption is available and turned on 
	 */
	public boolean isAccountManagerSecure() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			return false;
		}
		else {
			// Note: Following method only exists if linking to an android.jar api 11 and above
			return devicePolicyManager.getStorageEncryptionStatus() == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE;
		}
	}
	
	/**
	 * This will asynchronously create a RestClient for you.
	 * It will kick off the login flow if needed.
	 * Once the user has authenticated, the RestClientCallback will fire passing you the constructed client or null
	 * if authentication failed.
	 * 
	 * The work is actually be done by the service registered to handle authentication for this application account type)
	 * @see AuthenticatorService
	 */
	public void getRestClient(Activity activityContext,
			RestClientCallback callback) {

		Account acc = getAccount();

		if (acc == null) {
			accountManager.addAccount(getAccountType(),
					AccountManager.KEY_AUTHTOKEN, null, null,
					activityContext, new Callback(activityContext, callback),
					null);
		
		} else {
			Log.i("ClientManager:getRestClient",
					"Get auth token for account name: " + acc.name);
			accountManager.getAuthToken(acc, AccountManager.KEY_AUTHTOKEN,
					null, activityContext, new Callback(
							activityContext, callback), null);
		
		}
	}
	
	/**
	 * This will peek for an auth token and build you a rest client if possible,
	 * otherwise it throws exceptions This is intended to be used by code not on
	 * the UI thread (.e.g. ContentProviders).
	 */
	public RestClient peekRestClient(Context ctx)
			throws AccountInfoNotFoundException {
		
		Account acc = getAccount();
		if (acc == null) {
			AccountInfoNotFoundException e = new AccountInfoNotFoundException(
					"No user account found");
			Log.i("ClientManager:peekRestClient", "No user account found", e);
			throw e;
		}

		String authToken = accountManager.getUserData(acc,
				AccountManager.KEY_AUTHTOKEN);
		String server = accountManager.getUserData(acc,
				AuthenticatorService.KEY_INSTANCE_SERVER);
		String userId = accountManager.getUserData(acc, AuthenticatorService.KEY_USER_ID);

		if (authToken == null)
			throw new AccountInfoNotFoundException(AccountManager.KEY_AUTHTOKEN);
		if (server == null)
			throw new AccountInfoNotFoundException(AuthenticatorService.KEY_INSTANCE_SERVER);
		if (userId == null)
			throw new AccountInfoNotFoundException(AuthenticatorService.KEY_USER_ID);

		try {
			return new RestClient(new URI(server), authToken, HttpAccess.DEFAULT, new AccountManagerTokenProvider(this));
		} 
		catch (URISyntaxException e) {
			Log.w("ClientManager:peekRestClient", "Invalid server URL", e);
			throw new AccountInfoNotFoundException("invalid server url", e);
		}
	}

	/**
	 * @return first account found with the application account type
	 */
	public Account getAccount() {
		Account[] accounts = accountManager.getAccountsByType(getAccountType());
		if (accounts == null || accounts.length == 0)
			return null;
		return accounts[0];
	}

	/**
	 * @param name
	 * @return account with the application account type and the given name
	 */
	public Account getAccountByName(String name) {
		Account[] accounts = accountManager.getAccountsByType(getAccountType());
		if (accounts != null) {
			for (Account account : accounts) {
				if (account.name.equals(name)) {
					return account;
				}
			}
		}
		return null;
	}
	
	
	/**
	 * @return all accounts found for this application account type
	 */
	public Account[] getAccounts() {
		return accountManager.getAccountsByType(getAccountType());
	}	
	
	/**
	 * Remove all the accounts passed in
	 * @param accounts
	 */
	public void removeAccounts(Account[] accounts) {
		List<AccountManagerFuture<Boolean>> removalFutures = new ArrayList<AccountManagerFuture<Boolean>>();
		for (Account a : accounts)
			removalFutures.add(accountManager.removeAccount(a, null, null));
		
		for (AccountManagerFuture<Boolean> f : removalFutures) {
			try {
				f.getResult();
			} catch (Exception ex) {
				Log.w("ClientManager:removeAccounts", "Exception removing old account", ex);
			}
		}
	}
	
	/**
	 * Create new account and return bundle that new account details in a bundle
	 * @param username
	 * @param refreshToken
	 * @param authToken
	 * @param instanceUrl
	 * @param loginUrl
	 * @param clientId
	 * @param userId
	 * @return
	 */
	public Bundle createNewAccount(String username, String refreshToken, String authToken,
			String instanceUrl, String loginUrl, String clientId, String userId) {
				
		Bundle extras = new Bundle();
		extras.putString(AccountManager.KEY_ACCOUNT_NAME, username);
		extras.putString(AccountManager.KEY_ACCOUNT_TYPE, getAccountType());
		extras.putString(AccountManager.KEY_AUTHTOKEN, authToken);
		extras.putString(AuthenticatorService.KEY_LOGIN_SERVER, loginUrl);
		extras.putString(AuthenticatorService.KEY_INSTANCE_SERVER, instanceUrl);
		extras.putString(AuthenticatorService.KEY_CLIENT_ID, clientId);
		extras.putString(AuthenticatorService.KEY_USER_ID, userId);

		Account acc = new Account(username, getAccountType());
		accountManager.addAccountExplicitly(acc,
				(isAccountManagerSecure() ? refreshToken : ""), // only storing refresh token when it's secure to do so 
				extras);
		accountManager.setAuthToken(acc, AccountManager.KEY_AUTHTOKEN, authToken);
		
		return extras;
	}
	
	/**
	 * Should match the value in authenticator.xml
	 * @return account type for this application
	 */
	public String getAccountType() {
		return accountType;
	}
	
	/**
	 * @return accountManager
	 */
	public AccountManager getAccountManager() {
		return accountManager;
	}


	/**
	 * Invalidate the account with the given authToken
	 * @param authToken
	 */
	public void invalidateAuthToken(String authToken) {
		Log.i("ClientManager:invalidateAuthToken", "Entering invalidateAuthToken");

		Account acc = getAccount();
		if (acc != null) {
			accountManager.invalidateAuthToken(acc.type, authToken);
		}
	}
	
	/**
	 * Removes the user account from the account manager, this is an
	 * asynchronous process, the callback is called on completion if
	 * specified.
	 */
	public void removeAccountAsync(AccountManagerCallback<Boolean> callback) {
		Account acc = getAccount();
		if (acc != null)
			accountManager.removeAccount(acc, callback, null);
	}
	
	
	/**
	 * Callback from either user account creation or a call to getAuthToken used
	 * by the android account management bits
	 */
	private class Callback implements AccountManagerCallback<Bundle> {

		private final Context context;
		private final RestClientCallback restCallback;

		/**
		 * @param restCallback
		 *            who to directly call when we get a result for getAuthToken
		 * @param accServer
		 */
		Callback(Context ctx, RestClientCallback restCallback) {
			assert restCallback != null : "you must supply a RestClientAvailable instance";
			assert ctx != null : "you must supply a valid Context";
			this.context = ctx;
			this.restCallback = restCallback;
		}

		@Override
		public void run(AccountManagerFuture<Bundle> f) {

			RestClient client = null;

			try {
				f.getResult();
				Log.i("ClientManager:Callback:run",
						"AccountManager callback called");

				// the O.S. strips the auth_token from the response bundle on
				// 2.2, given that we might as well just use peekClient to build
				// the client from the data in the AccountManager, rather than
				// trying to build it from the bundle.
				client = peekRestClient(context);

			} catch (AccountsException e) {
				Log.w("ClientManager:Callback:run", "", e);
			} catch (IOException e) {
				Log.w("ClientManager:Callback:run", "", e);
			} catch (AccountInfoNotFoundException e) {
				Log.w("ClientManager:Callback:run", "", e);
			}

			// response. if we failed, null
			restCallback.authenticatedRestClient(client);
		}
	}

	/**
	 * RestClientCallback interface.
	 * You must provider an implementation of this interface when calling getRestClient.
	 */
	public interface RestClientCallback {
		public void authenticatedRestClient(RestClient client);
	}

	/**
	 * AuthTokenProvider implementation that calls out to the AccountManager to get a new access token.
	 * The AccountManager actually calls ForceAuthenticatorService to do the actual refresh.
	 * @see AuthenticatorService
	 */
	public static class AccountManagerTokenProvider implements RestClient.AuthTokenProvider {

		private static boolean gettingAuthToken;
		private static String lastNewAuthToken;
		private static final Object lock = new Object();
		private final ClientManager clientManager;

		AccountManagerTokenProvider(ClientManager clientManager) {
			this.clientManager = clientManager;
		}

		/**
		 * Fetch a new access token from the account manager, if another thread
		 * is already in the progress of doing this we'll just wait for it to finish and use that access token.
		 * Return null if we can't get a new access token for any reason.
		 * @param acc
		 * @return
		 */
		private String fetchNewAuthToken(Account acc) {
			synchronized (lock) {
				if (gettingAuthToken) {
					// another thread is already fetching an access token, wait
					// for that.
					try {
						lock.wait();
					} catch (InterruptedException e) {
						Log.w("ClientManager:Callback:fetchNewAuthToken", "",
								e);
					}
					return lastNewAuthToken;
				}
				gettingAuthToken = true;
			}

			String newAuthToken = null;
			try {
				// getBlockingAuthToken doesn't do much of a job of
				// transferring errors, you just get a null new token, so
				// don't try and use null as the new token.
				newAuthToken = clientManager.accountManager.blockingGetAuthToken(acc,
						AccountManager.KEY_AUTHTOKEN, false);
			} catch (Exception e) {
				Log.w("ClientManager:Callback:fetchNewAuthToken",
						"Exception during blockingGetAuthToken call", e);
			} finally {
				synchronized (lock) {
					gettingAuthToken = false;
					lastNewAuthToken = newAuthToken;
					lock.notifyAll();
				}
			}
			return newAuthToken;
		}

		/**
		 * WARNING This method can only be called from a background thread, see
		 * blockingGetAuthToken
		 */
		@Override
		public String getNewAuthToken(RestClient client) {
			Log.i("ClientManager:Callback:getNewAuthToken",
					"Need new access token");

			String authToken = client.getAuthToken();
			if (authToken == null) {
				Log.w("ClientManager:Callback:getNewAuthToken", "Access token not set");
			} else {
				clientManager.invalidateAuthToken(authToken);
			}

			Account acc = clientManager.getAccount();
			if (acc == null)
				return null;

			return fetchNewAuthToken(acc);
		}
	}

	/**
	 * Exception thrown when no account could be found (during a peekRestClient call) 
	 */
	public static class AccountInfoNotFoundException extends Exception {
		private static final long serialVersionUID = 1L;

		AccountInfoNotFoundException(String msg) {
			super(msg);
		}

		AccountInfoNotFoundException(String msg, Throwable cause) {
			super(msg, cause);
		}
	}
}
