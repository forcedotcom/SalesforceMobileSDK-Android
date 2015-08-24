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
package com.salesforce.androidsdk.rest;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AccountsException;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.AuthenticatorService;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.rest.RestClient.ClientInfo;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * ClientManager is a factory class for RestClient which stores OAuth credentials in the AccountManager.
 * If no account is found, it kicks off the login flow which creates a new account if successful.
 *
 */
public class ClientManager {

	public static final String ACCESS_TOKEN_REVOKE_INTENT = "access_token_revoked";
    public static final String ACCESS_TOKEN_REFRESH_INTENT = "access_token_refeshed";
    public static final String INSTANCE_URL_UPDATE_INTENT = "instance_url_updated";

    private final AccountManager accountManager;
    private final String accountType;
    private final LoginOptions loginOptions;
    private final boolean revokedTokenShouldLogout;

    /**
     * Construct a ClientManager using a custom account type.
     *
     * @param ctx Context.
     * @param accountType Account type.
     * @param loginOptions Login options.
     */
    @Deprecated
    public ClientManager(Context ctx, String accountType, LoginOptions loginOptions) {
        this(ctx, accountType, loginOptions, true);
    }

    /**
     * Construct a ClientManager using a custom account type.
     *
     * @param ctx Context.
     * @param accountType Account type.
     * @param loginOptions Login options.
     * @param revokedTokenShouldLogout True - if the SDK should logout when the access token is revoked, False - otherwise.
     */
    public ClientManager(Context ctx, String accountType, LoginOptions loginOptions, boolean revokedTokenShouldLogout) {
    	this.accountManager = AccountManager.get(ctx);
        this.accountType = accountType;
        this.loginOptions = loginOptions;
        this.revokedTokenShouldLogout = revokedTokenShouldLogout;
    }

    /**
     * Method to create a RestClient asynchronously. It is intended to be used by code on the UI thread.
     *
     * If no accounts are found, it will kick off the login flow which will create a new account if successful.
     * After the account is created or if an account already existed, it creates a RestClient and returns it through restClientCallback.
     *
     * Note: The work is actually being done by the service registered to handle authentication for this application account type.
     * @see AuthenticatorService
     *
     * @param activityContext        current activity
     * @param restClientCallback     callback invoked once the RestClient is ready
     */
    public void getRestClient(Activity activityContext, RestClientCallback restClientCallback) {
        Account acc = getAccount();

        // Passing the passcodeHash to the authenticator service to that it can encrypt/decrypt oauth tokens
        Bundle options = loginOptions.asBundle();

        // No account found - let's add one - the AuthenticatorService add account method will start the login activity
        if (acc == null) {
            Log.i("ClientManager:getRestClient", "No account of type " + accountType + " found");
            accountManager.addAccount(getAccountType(),
                    AccountManager.KEY_AUTHTOKEN, null /*required features*/, options,
                    activityContext, new AccMgrCallback(restClientCallback),
                    null /* handler */);

        }
        // Account found
        else {
            Log.i("ClientManager:getRestClient", "Found account of type " + accountType);
            accountManager.getAuthToken(acc, AccountManager.KEY_AUTHTOKEN,
                    options, activityContext, new AccMgrCallback(restClientCallback), null /* handler */);

        }
    }

    /**
     * Method to created an unauthenticated RestClient asynchronously
     * @param activityContext
     * @param restClientCallback
     */
    public void getUnauthenticatedRestClient(Activity activityContext, RestClientCallback restClientCallback) {
        restClientCallback.authenticatedRestClient(peekUnauthenticatedRestClient());
    }

    /**
     * Method to create an unauthenticated RestClient.
     * @return
     */
    public RestClient peekUnauthenticatedRestClient() {
        return new RestClient(new RestClient.UnauthenticatedClientInfo(), null, HttpAccess.DEFAULT, null);
    }

    public RestClient peekRestClient() {
        return peekRestClient(getAccount());
    }

    /**
     * Method to create RestClient synchronously. It is intended to be used by code not on the UI thread (e.g. ContentProvider).
     *
     * If there is no account, it will throw an exception.
     *
     * @return
     */

    public RestClient peekRestClient(UserAccount user) {
    	return peekRestClient(getAccountByName(user.getAccountName()));
    }
    
    public RestClient peekRestClient(Account acc) {
        if (acc == null) {
            AccountInfoNotFoundException e = new AccountInfoNotFoundException("No user account found");
            Log.i("ClientManager:peekRestClient", "No user account found");
            throw e;
        }
        if (SalesforceSDKManager.getInstance().isLoggingOut()) {
        	AccountInfoNotFoundException e = new AccountInfoNotFoundException("User is logging out");
            Log.i("ClientManager:peekRestClient", "User is logging out", e);
            throw e;
        }
        String passcodeHash = (SalesforceSDKManager.getInstance().getIsTestRun() ? loginOptions.passcodeHash : SalesforceSDKManager.getInstance().getPasscodeHash());
        String authToken = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(acc, AccountManager.KEY_AUTHTOKEN), passcodeHash);
        String refreshToken = SalesforceSDKManager.decryptWithPasscode(accountManager.getPassword(acc), passcodeHash);

        // We also store the username, instance url, org id, user id and username in the account manager
        String loginServer = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(acc, AuthenticatorService.KEY_LOGIN_URL), passcodeHash);
        String idUrl = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(acc, AuthenticatorService.KEY_ID_URL), passcodeHash);
        String instanceServer = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(acc, AuthenticatorService.KEY_INSTANCE_URL), passcodeHash);
        String orgId = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(acc, AuthenticatorService.KEY_ORG_ID), passcodeHash);
        String userId = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(acc, AuthenticatorService.KEY_USER_ID), passcodeHash);
        String username = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(acc, AuthenticatorService.KEY_USERNAME), passcodeHash);
        String accountName = accountManager.getUserData(acc, AccountManager.KEY_ACCOUNT_NAME);
        String clientId = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(acc, AuthenticatorService.KEY_CLIENT_ID), passcodeHash);
        final String encCommunityId = accountManager.getUserData(acc, AuthenticatorService.KEY_COMMUNITY_ID);
        String communityId = null;
        if (encCommunityId != null) {
        	communityId = SalesforceSDKManager.decryptWithPasscode(encCommunityId, passcodeHash);
        }
        final String encCommunityUrl = accountManager.getUserData(acc, AuthenticatorService.KEY_COMMUNITY_URL);
        String communityUrl = null;
        if (encCommunityUrl != null) {
        	communityUrl = SalesforceSDKManager.decryptWithPasscode(encCommunityUrl, passcodeHash);
        }
        if (authToken == null)
            throw new AccountInfoNotFoundException(AccountManager.KEY_AUTHTOKEN);
        if (instanceServer == null)
            throw new AccountInfoNotFoundException(AuthenticatorService.KEY_INSTANCE_URL);
        if (userId == null)
            throw new AccountInfoNotFoundException(AuthenticatorService.KEY_USER_ID);
        if (orgId == null)
            throw new AccountInfoNotFoundException(AuthenticatorService.KEY_ORG_ID);

        try {
            AccMgrAuthTokenProvider authTokenProvider = new AccMgrAuthTokenProvider(this, instanceServer, authToken, refreshToken);
            ClientInfo clientInfo = new ClientInfo(clientId, new URI(instanceServer),
            		new URI(loginServer), new URI(idUrl), accountName, username,
            		userId, orgId, communityId, communityUrl);
            return new RestClient(clientInfo, authToken, HttpAccess.DEFAULT, authTokenProvider);
        } catch (URISyntaxException e) {
            Log.w("ClientManager:peekRestClient", "Invalid server URL", e);
            throw new AccountInfoNotFoundException("invalid server url", e);
        }
    }

    /**
     * Invalidate current auth token.  The next call to {@link #getRestClient(Activity, RestClientCallback) getRestClient} will do a refresh.
     */
    public void invalidateToken(String lastNewAuthToken) {
        accountManager.invalidateAuthToken(getAccountType(), lastNewAuthToken);
    }

    /**
     * Returns the user account that is currently active.
     *
     * @return The current user account.
     */
    public Account getAccount() {
    	return SalesforceSDKManager.getInstance().getUserAccountManager().getCurrentAccount();
    }

    /**
     * @param name The name associated with the account.
     * @return The account with the application account type and the given name.
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
     * @return All of the accounts found for this application account type.
     */
    public Account[] getAccounts() {
        return accountManager.getAccountsByType(getAccountType());
    }

    /**
     * Remove all of the accounts passed in.
     *
     * @param accounts The array of accounts to remove.
     */
    public void removeAccounts(Account[] accounts) {
        List<AccountManagerFuture<Boolean>> removalFutures = new ArrayList<AccountManagerFuture<Boolean>>();
        for (Account a : accounts) {
            removalFutures.add(accountManager.removeAccount(a, null, null));
        }
        for (AccountManagerFuture<Boolean> f : removalFutures) {
            try {
                f.getResult();
            } catch (Exception ex) {
                Log.w("ClientManager:removeAccounts", "Exception removing old account", ex);
            }
        }
    }

    /**
     * Create a new account and return the details of the new account in a bundle.
     * @param accountName
     * @param username
     * @param refreshToken
     * @param authToken
     * @param instanceUrl
     * @param loginUrl
     * @param idUrl
     * @param clientId
     * @param orgId
     * @param userId
     * @param passcodeHash
     * @return
     */
    public Bundle createNewAccount(String accountName, String username, String refreshToken,
    		String authToken, String instanceUrl, String loginUrl, String idUrl,
    		String clientId, String orgId, String userId, String passcodeHash) {
        return createNewAccount(accountName, username, refreshToken, authToken,
                                instanceUrl, loginUrl, idUrl, clientId, orgId,
                                userId, passcodeHash, null);
    }

    public Bundle createNewAccount(String accountName, String username, String refreshToken,
    		String authToken, String instanceUrl, String loginUrl, String idUrl,
    		String clientId, String orgId, String userId, String passcodeHash,
            String clientSecret) {
        return createNewAccount(accountName, username, refreshToken, authToken,
                instanceUrl, loginUrl, idUrl, clientId, orgId,
                userId, passcodeHash, clientSecret, null, null);
    }

    public Bundle createNewAccount(String accountName, String username, String refreshToken,
    		String authToken, String instanceUrl, String loginUrl, String idUrl,
    		String clientId, String orgId, String userId, String passcodeHash,
            String clientSecret, String communityId, String communityUrl) {
        Bundle extras = new Bundle();
        extras.putString(AccountManager.KEY_ACCOUNT_NAME, accountName);
        extras.putString(AccountManager.KEY_ACCOUNT_TYPE, getAccountType());
        extras.putString(AuthenticatorService.KEY_USERNAME, SalesforceSDKManager.encryptWithPasscode(username, passcodeHash));
        extras.putString(AuthenticatorService.KEY_LOGIN_URL, SalesforceSDKManager.encryptWithPasscode(loginUrl, passcodeHash));
        extras.putString(AuthenticatorService.KEY_ID_URL, SalesforceSDKManager.encryptWithPasscode(idUrl, passcodeHash));
        extras.putString(AuthenticatorService.KEY_INSTANCE_URL, SalesforceSDKManager.encryptWithPasscode(instanceUrl, passcodeHash));
        extras.putString(AuthenticatorService.KEY_CLIENT_ID, SalesforceSDKManager.encryptWithPasscode(clientId, passcodeHash));
        extras.putString(AuthenticatorService.KEY_ORG_ID, SalesforceSDKManager.encryptWithPasscode(orgId, passcodeHash));
        extras.putString(AuthenticatorService.KEY_USER_ID, SalesforceSDKManager.encryptWithPasscode(userId, passcodeHash));
        if (clientSecret != null) {
            extras.putString(AuthenticatorService.KEY_CLIENT_SECRET, SalesforceSDKManager.encryptWithPasscode(clientSecret, passcodeHash));
        }
        if (communityId != null) {
            extras.putString(AuthenticatorService.KEY_COMMUNITY_ID, SalesforceSDKManager.encryptWithPasscode(communityId, passcodeHash));
        }
        if (communityUrl != null) {
            extras.putString(AuthenticatorService.KEY_COMMUNITY_URL, SalesforceSDKManager.encryptWithPasscode(communityUrl, passcodeHash));
        }
        extras.putString(AccountManager.KEY_AUTHTOKEN, SalesforceSDKManager.encryptWithPasscode(authToken, passcodeHash));
        Account acc = new Account(accountName, getAccountType());
        accountManager.addAccountExplicitly(acc, SalesforceSDKManager.encryptWithPasscode(refreshToken, passcodeHash), extras);
        accountManager.setAuthToken(acc, AccountManager.KEY_AUTHTOKEN, authToken);
        SalesforceSDKManager.getInstance().getUserAccountManager().storeCurrentUserInfo(userId, orgId);
        return extras;
    }

    /**
     * Should match the value in authenticator.xml.12
     * @return The account type for this application.
     */
    public String getAccountType() {
        return accountType;
    }

    /**
     * Changes the passcode to a new value and re-encrypts the account manager data with the new passcode.
     *
     * @param oldPass Old passcode.
     * @param newPass New passcode.
     */
    public static synchronized void changePasscode(String oldPass, String newPass) {

        // Update data stored in AccountManager with new encryption key.
        final AccountManager acctManager = AccountManager.get(SalesforceSDKManager.getInstance().getAppContext());
        if (acctManager != null) {
            final Account[] accounts = acctManager.getAccountsByType(SalesforceSDKManager.getInstance().getAccountType());
            if (accounts != null && accounts.length > 0) {
                for (final Account account : accounts) {

                    // Grab existing data stored in AccountManager.
                    final String authToken = SalesforceSDKManager.decryptWithPasscode(acctManager.getUserData(account, AccountManager.KEY_AUTHTOKEN), oldPass);
                    final String refreshToken = SalesforceSDKManager.decryptWithPasscode(acctManager.getPassword(account), oldPass);
                    final String loginServer = SalesforceSDKManager.decryptWithPasscode(acctManager.getUserData(account, AuthenticatorService.KEY_LOGIN_URL), oldPass);
                    final String idUrl = SalesforceSDKManager.decryptWithPasscode(acctManager.getUserData(account, AuthenticatorService.KEY_ID_URL), oldPass);
                    final String instanceServer = SalesforceSDKManager.decryptWithPasscode(acctManager.getUserData(account, AuthenticatorService.KEY_INSTANCE_URL), oldPass);
                    final String orgId = SalesforceSDKManager.decryptWithPasscode(acctManager.getUserData(account, AuthenticatorService.KEY_ORG_ID), oldPass);
                    final String userId = SalesforceSDKManager.decryptWithPasscode(acctManager.getUserData(account, AuthenticatorService.KEY_USER_ID), oldPass);
                    final String username = SalesforceSDKManager.decryptWithPasscode(acctManager.getUserData(account, AuthenticatorService.KEY_USERNAME), oldPass);
                    final String clientId = SalesforceSDKManager.decryptWithPasscode(acctManager.getUserData(account, AuthenticatorService.KEY_CLIENT_ID), oldPass);
                    final String encClientSecret = acctManager.getUserData(account, AuthenticatorService.KEY_CLIENT_SECRET);
                    String clientSecret = null;
                    if (encClientSecret != null) {
                    	clientSecret = SalesforceSDKManager.decryptWithPasscode(encClientSecret, oldPass);
                    }
                    final String encCommunityId = acctManager.getUserData(account, AuthenticatorService.KEY_COMMUNITY_ID);
                    String communityId = null;
                    if (encCommunityId != null) {
                    	communityId = SalesforceSDKManager.decryptWithPasscode(encCommunityId, oldPass);
                    }
                    final String encCommunityUrl = acctManager.getUserData(account, AuthenticatorService.KEY_COMMUNITY_URL);
                    String communityUrl = null;
                    if (encCommunityUrl != null) {
                    	communityUrl = SalesforceSDKManager.decryptWithPasscode(encCommunityUrl, oldPass);
                    }

                    // Encrypt data with new hash and put it back in AccountManager.
                    acctManager.setUserData(account, AccountManager.KEY_AUTHTOKEN, SalesforceSDKManager.encryptWithPasscode(authToken, newPass));
                    acctManager.setPassword(account, SalesforceSDKManager.encryptWithPasscode(refreshToken, newPass));
                    acctManager.setUserData(account, AuthenticatorService.KEY_LOGIN_URL, SalesforceSDKManager.encryptWithPasscode(loginServer, newPass));
                    acctManager.setUserData(account, AuthenticatorService.KEY_ID_URL, SalesforceSDKManager.encryptWithPasscode(idUrl, newPass));
                    acctManager.setUserData(account, AuthenticatorService.KEY_INSTANCE_URL, SalesforceSDKManager.encryptWithPasscode(instanceServer, newPass));
                    acctManager.setUserData(account, AuthenticatorService.KEY_ORG_ID, SalesforceSDKManager.encryptWithPasscode(orgId, newPass));
                    acctManager.setUserData(account, AuthenticatorService.KEY_USER_ID, SalesforceSDKManager.encryptWithPasscode(userId, newPass));
                    acctManager.setUserData(account, AuthenticatorService.KEY_USERNAME, SalesforceSDKManager.encryptWithPasscode(username, newPass));
                    acctManager.setUserData(account, AuthenticatorService.KEY_CLIENT_ID, SalesforceSDKManager.encryptWithPasscode(clientId, newPass));
                    if (clientSecret != null) {
                        acctManager.setUserData(account, AuthenticatorService.KEY_CLIENT_SECRET, SalesforceSDKManager.encryptWithPasscode(clientSecret, newPass));
                    }
                    if (communityId != null) {
                        acctManager.setUserData(account, AuthenticatorService.KEY_COMMUNITY_ID, SalesforceSDKManager.encryptWithPasscode(communityId, newPass));
                    }
                    if (communityUrl != null) {
                        acctManager.setUserData(account, AuthenticatorService.KEY_COMMUNITY_URL, SalesforceSDKManager.encryptWithPasscode(communityUrl, newPass));
                    }
                    acctManager.setAuthToken(account, AccountManager.KEY_AUTHTOKEN, authToken);
                }
            }
        }
    }

    /**
     * @return The AccountManager for the application.
     */
    public AccountManager getAccountManager() {
        return accountManager;
    }

    /**
     * Removes the user account from the account manager. This is an
     * asynchronous process, the callback will be called on completion, if
     * specified.
     *
     * @param acc Account to be removed.
     * @param callback The callback to call when the account removal completes.
     */
    public void removeAccountAsync(Account acc, AccountManagerCallback<Boolean> callback) {
        if (acc != null) {
            accountManager.removeAccount(acc, callback, null);
        }
    }

    /**
     * Callback from either user account creation, or a call to getAuthToken, used
     * by the Android account management components.
     */
    private class AccMgrCallback implements AccountManagerCallback<Bundle> {

        private final RestClientCallback restCallback;

        /**
         * Constructor
         * @param restCallback Who to directly call when we get a result for getAuthToken.
         *
         */
        AccMgrCallback(RestClientCallback restCallback) {
            assert restCallback != null : "you must supply a RestClientAvailable instance";
            this.restCallback = restCallback;
        }

        @Override
        public void run(AccountManagerFuture<Bundle> f) {
            RestClient client = null;
            try {
                f.getResult();
                client = peekRestClient();
            } catch (AccountsException e) {
                Log.w("AccMgrCallback:run", "", e);
            } catch (IOException e) {
                Log.w("AccMgrCallback:run", "", e);
            } catch (AccountInfoNotFoundException e) {
                Log.w("AccMgrCallback:run", "", e);
            }

            // response. if we failed, null
            restCallback.authenticatedRestClient(client);
        }
    }

    /**
     * RestClientCallback interface.
     * You must provide an implementation of this interface when calling
     * {@link ClientManager#getRestClient(Activity, RestClientCallback) getRestClient}.
     */
    public interface RestClientCallback {
        public void authenticatedRestClient(RestClient client);
    }

    /**
     * AuthTokenProvider implementation that calls out to the AccountManager to get a new access token.
     * The AccountManager calls ForceAuthenticatorService to do the actual refresh.
     * @see AuthenticatorService
     */
    public static class AccMgrAuthTokenProvider implements RestClient.AuthTokenProvider {

        private static boolean gettingAuthToken;
        private static final Object lock = new Object();
        private final ClientManager clientManager;
        private static String lastNewAuthToken;
        private final String refreshToken;
        private static String lastNewInstanceUrl;
        private long lastRefreshTime = -1 /* never refreshed */;

        /**
         * Constructor
         * @param clientManager
         * @param refreshToken
         */
        public AccMgrAuthTokenProvider(ClientManager clientManager, String instanceUrl, String authToken, String refreshToken) {
            this.clientManager = clientManager;
            this.refreshToken = refreshToken;
            lastNewAuthToken = authToken;
            lastNewInstanceUrl = instanceUrl;
        }

        /**
         * Fetch a new access token from the account manager.  If another thread
         * is already in the process of doing this, we'll just wait for it to finish and use that access token.
         * @return The auth token, or null if we can't get a new access token for any reason.
         */
        @Override
        public String getNewAuthToken() {
            Log.i("AccMgrAuthTokenProvider:getNewAuthToken", "Need new access token");
            Account acc = clientManager.getAccount();
            if (acc == null)
                return null;

            // Wait if another thread is already fetching an access token
            synchronized (lock) {
                if (gettingAuthToken) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Log.w("ClientManager:Callback:fetchNewAuthToken", "", e);
                    }
                    return lastNewAuthToken;
                }
                gettingAuthToken = true;
            }

            // Invalidate current auth token
            clientManager.invalidateToken(lastNewAuthToken);
            String newAuthToken = null;
            String newInstanceUrl = null;

            try {
                final Bundle bundle = clientManager.accountManager.getAuthToken(acc, AccountManager.KEY_AUTHTOKEN, null, false, null, null).getResult();
                if (bundle == null) {
                    Log.w("AccMgrAuthTokenProvider:fetchNewAuthToken", "accountManager.getAuthToken returned null bundle");
                } else {
                    newAuthToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
                    newInstanceUrl = bundle.getString(AuthenticatorService.KEY_INSTANCE_URL);
                    Intent broadcastIntent;
                    if (newAuthToken == null) {
                        if (clientManager.revokedTokenShouldLogout) {

                        	// Check if a looper exists before trying to prepare another one.
                        	if (Looper.myLooper() == null) {
                                Looper.prepare();	
                        	}
                            SalesforceSDKManager.getInstance().logout(null, false);
                        }

                        // Broadcasts an intent that the access token has been revoked.
                        broadcastIntent = new Intent(ACCESS_TOKEN_REVOKE_INTENT);
                    } else if (newInstanceUrl != null && !newInstanceUrl.equalsIgnoreCase(lastNewInstanceUrl)) {
                        // Broadcasts an intent that the instance server has changed (implicitly token refreshed too)
                        broadcastIntent = new Intent(INSTANCE_URL_UPDATE_INTENT);
                    } else {

                        // Broadcasts an intent that the access token has been refreshed.
                        broadcastIntent = new Intent(ACCESS_TOKEN_REFRESH_INTENT);
                    }

                    broadcastIntent.setPackage(SalesforceSDKManager.getInstance().getAppContext().getPackageName());
                    SalesforceSDKManager.getInstance().getAppContext().sendBroadcast(broadcastIntent);
                }
            } catch (Exception e) {
                Log.w("AccMgrAuthTokenProvider:fetchNewAuthToken:getNewAuthToken",
                        "Exception during getAuthToken call", e);
            } finally {
                synchronized (lock) {
                    gettingAuthToken = false;
                    lastNewAuthToken = newAuthToken;
                    lastNewInstanceUrl = newInstanceUrl;
                    lastRefreshTime  = System.currentTimeMillis();
                    lock.notifyAll();
                }
            }
            return newAuthToken;
        }

        @Override
        public String getRefreshToken() {
            return refreshToken;
        }

        @Override
        public long getLastRefreshTime() {
            return lastRefreshTime;
        }

        @Override
        public String getInstanceUrl() { return lastNewInstanceUrl; }
    }

    /**
     * Exception thrown when no account could be found (during a
     * {@link ClientManager#peekRestClient() peekRestClient} call)
     */
    public static class AccountInfoNotFoundException extends RuntimeException {

    	private static final long serialVersionUID = 1L;

        AccountInfoNotFoundException(String msg) {
            super(msg);
        }

        public AccountInfoNotFoundException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    /**
     * Class encapsulating login options.
     * There are passed in a bundle to the auth service, which passes them as "extras" when starting the login activity.
     */
    public static class LoginOptions {

        private static final String OAUTH_SCOPES = "oauthScopes";
        private static final String OAUTH_CLIENT_ID = "oauthClientId";
        private static final String OAUTH_CALLBACK_URL = "oauthCallbackUrl";
        private static final String PASSCODE_HASH = "passcodeHash";
        private static final String LOGIN_URL = "loginUrl";
        private static final String CLIENT_SECRET = "clientSecret";

        public String loginUrl;
        public String passcodeHash;
        public final String oauthCallbackUrl;
        public final String oauthClientId;
        public final String[] oauthScopes;
        private final Bundle bundle;
        public String clientSecret;

        public LoginOptions(String loginUrl, String passcodeHash, String oauthCallbackUrl, String oauthClientId, String[] oauthScopes) {
            this.loginUrl = loginUrl;
            this.passcodeHash = passcodeHash;
            this.oauthCallbackUrl = oauthCallbackUrl;
            this.oauthClientId = oauthClientId;
            this.oauthScopes = oauthScopes;
            bundle = new Bundle();
            bundle.putString(LOGIN_URL, loginUrl);
            bundle.putString(PASSCODE_HASH, passcodeHash);
            bundle.putString(OAUTH_CALLBACK_URL, oauthCallbackUrl);
            bundle.putString(OAUTH_CLIENT_ID, oauthClientId);
            bundle.putStringArray(OAUTH_SCOPES, oauthScopes);
        }

        public LoginOptions(String loginUrl, String passcodeHash, String oauthCallbackUrl, String oauthClientId, String[] oauthScopes, String clientSecret) {
            this(loginUrl, passcodeHash, oauthCallbackUrl, oauthClientId, oauthScopes);
            this.clientSecret = clientSecret;
            bundle.putString(CLIENT_SECRET, clientSecret);
        }

        public Bundle asBundle() {
            return bundle;
        }

        public static LoginOptions fromBundle(Bundle options) {
            return new LoginOptions(options.getString(LOGIN_URL),
                                    options.getString(PASSCODE_HASH),
                                    options.getString(OAUTH_CALLBACK_URL),
                                    options.getString(OAUTH_CLIENT_ID),
                                    options.getStringArray(OAUTH_SCOPES),
                                    options.getString(CLIENT_SECRET));
        }
    }
}
