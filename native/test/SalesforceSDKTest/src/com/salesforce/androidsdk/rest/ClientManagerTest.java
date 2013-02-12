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
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.test.InstrumentationTestCase;

import com.salesforce.androidsdk.TestCredentials;
import com.salesforce.androidsdk.TestForceApp;
import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.auth.AuthenticatorService;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.rest.ClientManager.AccountInfoNotFoundException;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;
import com.salesforce.androidsdk.rest.ClientManager.RestClientCallback;
import com.salesforce.androidsdk.security.Encryptor;
import com.salesforce.androidsdk.util.EventsListenerQueue;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

public class ClientManagerTest extends InstrumentationTestCase {

    public static final String TEST_PASSCODE_HASH = Encryptor.hash("passcode", "hash-key");
    private static final String TEST_ORG_ID = "test_org_id";
    private static final String TEST_USER_ID = "test_user_id";
    private static final String TEST_ACCOUNT_NAME = "test_accountname";
    private static final String TEST_USERNAME = "test_username";
    private static final String TEST_CLIENT_ID = "test_client_d";
    private static final String TEST_LOGIN_URL = "https://test.salesforce.com";
    private static final String TEST_INSTANCE_URL = "https://tapp0.salesforce.com";
    private static final String TEST_IDENTITY_URL = "https://test.salesforce.com";
    private static final String TEST_AUTH_TOKEN = "test_auth_token";
    private static final String TEST_REFRESH_TOKEN = "test_refresh_token";
    private static final String TEST_OTHER_ACCOUNT_NAME = "test_other_accountname";
    private static final String TEST_OTHER_USERNAME = "test_other_username";
    private static final String TEST_ACCOUNT_TYPE = "com.salesforce.androisdk.test"; // must match authenticator.xml in SalesforceSDK project
    private static final String[] TEST_SCOPES = new String[] {"web"};
    private static final String TEST_CALLBACK_URL = "test://callback";

    private Context targetContext;
    private ClientManager clientManager;
    private AccountManager accountManager;
    private LoginOptions loginOptions;
    private EventsListenerQueue eq;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        TestCredentials.init(getInstrumentation().getContext());
        targetContext = getInstrumentation().getTargetContext();
        loginOptions = new LoginOptions(TEST_LOGIN_URL, TEST_PASSCODE_HASH, TEST_CALLBACK_URL, TEST_CLIENT_ID, TEST_SCOPES);
        clientManager = new ClientManager(targetContext, TEST_ACCOUNT_TYPE, loginOptions, true);
        accountManager = clientManager.getAccountManager();
        eq = new EventsListenerQueue();

        // Wait for app initialization to complete.
        Instrumentation.newApplication(TestForceApp.class, targetContext);
        if (ForceApp.APP == null) {
            eq.waitForEvent(EventType.AppCreateComplete, 5000);
        }
    }

    @Override
    public void tearDown() throws Exception {
        cleanupAccounts();
        assertNoAccounts();
        if (eq != null) {
            eq.tearDown();
            eq = null;
        }
        ForceApp.APP = null;
        super.tearDown();
    }

    /**
     * Test getAccountType
     */
    public void testGetAccountType() {
        assertEquals("Wrong account type", TEST_ACCOUNT_TYPE, clientManager.getAccountType());
    }

    /**
     * Test createNewAccount
     */
    public void testCreateAccount() {
        // Make sure we have no accounts initially
        assertNoAccounts();

        // Call createNewAccount
        createTestAccount();

        // Check that the account did get created
        Account[] accounts = accountManager.getAccountsByType(TEST_ACCOUNT_TYPE);
        assertEquals("One account should have been returned", 1, accounts.length);
        assertEquals("Wrong account name", TEST_ACCOUNT_NAME, accounts[0].name);
        assertEquals("Wrong account type", TEST_ACCOUNT_TYPE, accounts[0].type);
    }

    /**
     * Test getAccount
     */
    public void testGetAccount() {
        // Make sure we have no accounts initially
        assertNoAccounts();

        // Call createNewAccount
        createTestAccount();

        // Call getAccount
        Account account = clientManager.getAccount();
        assertNotNull("Account should have been returned", account);
        assertEquals("Wrong account name", TEST_ACCOUNT_NAME, account.name);
        assertEquals("Wrong account type", TEST_ACCOUNT_TYPE, account.type);

        String encryptedAuthToken = accountManager.getUserData(account, AccountManager.KEY_AUTHTOKEN);
        String decryptedAuthToken = ForceApp.decryptWithPasscode(encryptedAuthToken, TEST_PASSCODE_HASH);
        assertEquals("Wrong auth token", TEST_AUTH_TOKEN, decryptedAuthToken);

        String encryptedRefreshToken = accountManager.getPassword(account);
        String decryptedRefreshToken = ForceApp.decryptWithPasscode(encryptedRefreshToken, TEST_PASSCODE_HASH);
        assertEquals("Wrong refresh token", TEST_REFRESH_TOKEN, decryptedRefreshToken);

        assertEquals("Wrong instance url", TEST_INSTANCE_URL, ForceApp.decryptWithPasscode(accountManager.getUserData(account, AuthenticatorService.KEY_INSTANCE_URL), TEST_PASSCODE_HASH));
        assertEquals("Wrong login url", TEST_LOGIN_URL, ForceApp.decryptWithPasscode(accountManager.getUserData(account, AuthenticatorService.KEY_LOGIN_URL), TEST_PASSCODE_HASH));
        assertEquals("Wrong client id", TEST_CLIENT_ID, ForceApp.decryptWithPasscode(accountManager.getUserData(account, AuthenticatorService.KEY_CLIENT_ID), TEST_PASSCODE_HASH));
        assertEquals("Wrong user id", TEST_USER_ID, ForceApp.decryptWithPasscode(accountManager.getUserData(account, AuthenticatorService.KEY_USER_ID), TEST_PASSCODE_HASH));
        assertEquals("Wrong org id", TEST_ORG_ID, ForceApp.decryptWithPasscode(accountManager.getUserData(account, AuthenticatorService.KEY_ORG_ID), TEST_PASSCODE_HASH));
        assertEquals("Wrong username", TEST_USERNAME, ForceApp.decryptWithPasscode(accountManager.getUserData(account, AuthenticatorService.KEY_USERNAME), TEST_PASSCODE_HASH));
    }


    /**
     * Test getAccounts - when there is only one
     */
    public void testGetAccountsWithSingleAccount() {
        // Make sure we have no accounts initially
        assertNoAccounts();

        // Call createNewAccount
        createTestAccount();

        // Call getAccounts
        Account[] accounts = clientManager.getAccounts();
        assertEquals("One account should have been returned", 1, accounts.length);
        assertEquals("Wrong account name", TEST_ACCOUNT_NAME, accounts[0].name);
        assertEquals("Wrong account type", TEST_ACCOUNT_TYPE, accounts[0].type);
    }


    /**
     * Test getAccounts - when there are several accounts
     */
    public void testGetAccountsWithSeveralAccount() {
        // Make sure we have no accounts initially
        assertNoAccounts();

        // Call two accounts
        createTestAccount();
        createOtherTestAccount();

        // Call getAccounts
        Account[] accounts = clientManager.getAccounts();
        assertEquals("Two accounts should have been returned", 2, accounts.length);

        // Sorting
        Arrays.sort(accounts, new Comparator<Account>() {
            @Override
            public int compare(Account account1, Account account2) {
                return account1.name.compareTo(account2.name);
            }});
        assertEquals("Wrong account name", TEST_ACCOUNT_NAME, accounts[0].name);
        assertEquals("Wrong account name", TEST_OTHER_ACCOUNT_NAME, accounts[1].name);
    }

    /**
     * Test getAccountByName
     */
    public void testGetAccountByName() {
        // Make sure we have no accounts initially
        assertNoAccounts();

        // Create two accounts
        createTestAccount();
        createOtherTestAccount();

        // Check that the accounts did get created
        Account[] accounts = accountManager.getAccountsByType(TEST_ACCOUNT_TYPE);
        assertEquals("Two accounts should have been returned", 2, accounts.length);

        // Get the first one by name
        Account account = clientManager.getAccountByName(TEST_ACCOUNT_NAME);
        assertNotNull("An account should have been returned", account);
        assertEquals("Wrong account name", TEST_ACCOUNT_NAME, account.name);
        assertEquals("Wrong account type", TEST_ACCOUNT_TYPE, account.type);

        // Get the second one by name
        account = clientManager.getAccountByName(TEST_OTHER_ACCOUNT_NAME);
        assertNotNull("An account should have been returned", account);
        assertEquals("Wrong account name", TEST_OTHER_ACCOUNT_NAME, account.name);
        assertEquals("Wrong account type", TEST_ACCOUNT_TYPE, account.type);
    }


    /**
     * Test removeAccounts when there is only one
     */
    public void testRemoveOnlyAccount() {
        // Make sure we have no accounts initially
        assertNoAccounts();

        // Create an account
        createTestAccount();

        // Check that the account did get created
        Account[] accounts = accountManager.getAccountsByType(TEST_ACCOUNT_TYPE);
        assertEquals("One account should have been returned", 1, accounts.length);
        assertEquals("Wrong account name", TEST_ACCOUNT_NAME, accounts[0].name);

        // Remove the account
        clientManager.removeAccounts(accounts);

        // Make sure there are no accounts left
        assertNoAccounts();
    }

    /**
     * Test removeAccounts - removing one account where there are several
     */
    public void testRemoveOneOfSeveralAccount() {
        // Make sure we have no accounts initially
        assertNoAccounts();

        // Create two accounts
        createTestAccount();
        createOtherTestAccount();

        // Check that the accounts did get created
        Account[] accounts = accountManager.getAccountsByType(TEST_ACCOUNT_TYPE);
        assertEquals("Two accounts should have been returned", 2, accounts.length);

        // Remove one of them
        clientManager.removeAccounts(new Account[] {accounts[0]});

        // Make sure the other account is still there
        Account[] accountsLeft = accountManager.getAccountsByType(TEST_ACCOUNT_TYPE);
        assertEquals("One account should have been returned", 1, accountsLeft.length);
        assertEquals("Wrong account name", accounts[1].name, accountsLeft[0].name);
    }

    /**
     * Test removeAccounts - removing two accounts
     */
    public void testRemoveSeveralAccounts() {
        // Make sure we have no accounts initially
        assertNoAccounts();

        // Create two accounts
        createTestAccount();
        createOtherTestAccount();

        // Check that the accounts did get created
        Account[] accounts = accountManager.getAccountsByType(TEST_ACCOUNT_TYPE);
        assertEquals("Two accounts should have been returned", 2, accounts.length);

        // Remove one of them
        clientManager.removeAccounts(accounts);

        // Make sure there are no accounts left
        assertNoAccounts();
    }

    /**
     * Test peekRestClient - when there is no account
     */
    public void testPeekRestClientWhenNoAccounts() {
        // Make sure we have no accounts initially
        assertNoAccounts();

        // Call peekRestClient - expect exception
        try {
            clientManager.peekRestClient();
            fail("Expected AccountInfoNotFoundException");
        } catch (AccountInfoNotFoundException e) {
            // as expected
        }
    }

    /**
     * Test peekRestClient - when there is an account
     * @throws URISyntaxException
     */
    public void testPeekRestClientWithAccountSetup() throws URISyntaxException {
        // Make sure we have no accounts initially
        assertNoAccounts();

        // Create account
        createTestAccount();

        // Call peekRestClient - expect restClient
        try {
            RestClient restClient = clientManager.peekRestClient();
            assertNotNull("RestClient expected", restClient);
            assertEquals("Wrong authToken", TEST_AUTH_TOKEN, restClient.getAuthToken());
            assertEquals("Wrong instance Url", new URI(TEST_INSTANCE_URL), restClient.getClientInfo().instanceUrl);
        }
        catch (AccountInfoNotFoundException e) {
            fail("Did not expect AccountInfoNotFoundException");
        }
    }

    /**
     * Test getRestClient - when there is an account
     * @throws URISyntaxException
     */
    public void testGetRestClientWithAccountSetup() throws URISyntaxException {
        // Make sure we have no accounts initially
        assertNoAccounts();

        // Create account
        createTestAccount();

        // Call getkRestClient - expect restClient
        final BlockingQueue<RestClient> q = new ArrayBlockingQueue<RestClient>(1);
        clientManager.getRestClient(null, new RestClientCallback() {
            @Override
            public void authenticatedRestClient(RestClient client) {
                q.add(client);
            }
        });

        // Wait for getRestClient to complete
        try {
            RestClient restClient = q.poll(5L, TimeUnit.SECONDS);
            assertNotNull("RestClient expected", restClient);
            assertEquals("Wrong authToken", TEST_AUTH_TOKEN, restClient.getAuthToken());
            assertEquals("Wrong instance Url", new URI(TEST_INSTANCE_URL), restClient.getClientInfo().instanceUrl);
        } catch (InterruptedException e) {
            fail("getRestClient did not return after 5s");
        }
    }

    /**
     * Test removeAccountAsync
     */
    public void testRemoveAccountAsync() throws Exception {
        // Make sure we have no accounts initially
        assertNoAccounts();

        // Create account
        createTestAccount();

        // Check that the accounts did get created
        Account[] accounts = accountManager.getAccountsByType(TEST_ACCOUNT_TYPE);
        assertEquals("Two accounts should have been returned", 1, accounts.length);

        // Call removeAccountAsync
        final BlockingQueue<AccountManagerFuture<Boolean>> q = new ArrayBlockingQueue<AccountManagerFuture<Boolean>>(1);
        clientManager.removeAccountAsync(new AccountManagerCallback<Boolean>() {

            @Override
            public void run(AccountManagerFuture<Boolean> future) {
                q.add(future);
            }
        });

        // Wait for removeAccountAsync to complete
        try {
            AccountManagerFuture<Boolean> f = q.poll(5L, TimeUnit.SECONDS);
            assertNotNull("AccountManagerFuture expected", f);
            assertTrue("Removal should have returned true", f.getResult());

            // Make sure there are no accounts left
            assertNoAccounts();

        } catch (InterruptedException e) {
            fail("removeAccountAsync did not return after 5s");
        }
    }

    /**
     * Test fetch new auth token flow
     * We are creating a real account (using real client id/refresh token etc from TestCredentials)
     * @throws AccountInfoNotFoundException
     * @throws IOException
     *
     */
    public void _testFetchNewAuthToken() throws AccountInfoNotFoundException, IOException {
        // Make sure we have no accounts initially
        assertNoAccounts();

        String badToken = "bad token";

        // Create real account
        clientManager.createNewAccount(TestCredentials.ACCOUNT_NAME, TestCredentials.USERNAME,
                TestCredentials.REFRESH_TOKEN, badToken, TestCredentials.INSTANCE_URL,
                TEST_LOGIN_URL, TEST_IDENTITY_URL, TestCredentials.CLIENT_ID, TestCredentials.ORG_ID,
                TestCredentials.USER_ID, TEST_PASSCODE_HASH);

        // Peek rest client
        RestClient restClient = clientManager.peekRestClient();
        restClient.setHttpAccessor(new HttpAccess(null, "dummy-agent")); // clientManager initializes the client with HttpAccess.DEFAULT -- but that's null without an app

        // Check the client
        assertEquals("RestClient should be using authToken from account", badToken, restClient.getAuthToken());

        // Try a call - it should succeed and client should end up with a new auth token
        RestResponse response = restClient.sendSync(RestRequest.getRequestForResources(TestCredentials.API_VERSION));
        assertFalse("RestClient should now be using a new token", badToken.equals(restClient.getAuthToken()));
        assertTrue("Call should have succeeded", response.isSuccess());
    }

    /**
     * Checks there are no test accounts
     */
    private void assertNoAccounts() {
        assertEquals("There should be no accounts", 0, accountManager.getAccountsByType(TEST_ACCOUNT_TYPE).length);
    }

    /**
     * Remove any existing accounts
     */
    private void cleanupAccounts() throws Exception {
        clientManager.removeAccounts(accountManager.getAccountsByType(TEST_ACCOUNT_TYPE));
    }

    /**
     * Create test account
     * @return
     */
    private Bundle createTestAccount() {
        return clientManager.createNewAccount(TEST_ACCOUNT_NAME, TEST_USERNAME, TEST_REFRESH_TOKEN,
                TEST_AUTH_TOKEN, TEST_INSTANCE_URL, TEST_LOGIN_URL, TEST_IDENTITY_URL, TEST_CLIENT_ID,
                TEST_ORG_ID, TEST_USER_ID, TEST_PASSCODE_HASH);
    }

    /**
     * Create other test account
     * @return
     */
    private Bundle createOtherTestAccount() {
        return clientManager.createNewAccount(TEST_OTHER_ACCOUNT_NAME, TEST_OTHER_USERNAME,
                TEST_REFRESH_TOKEN, TEST_AUTH_TOKEN, TEST_INSTANCE_URL, TEST_LOGIN_URL,
                TEST_IDENTITY_URL, TEST_CLIENT_ID, TEST_ORG_ID, TEST_USER_ID, TEST_PASSCODE_HASH);
    }
}
