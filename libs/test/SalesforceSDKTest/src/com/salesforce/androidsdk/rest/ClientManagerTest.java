/*
 * Copyright (c) 2011-present, salesforce.com, inc.
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

import static com.salesforce.androidsdk.accounts.UserAccountManagerTest.TEST_ACCOUNT_TYPE;
import static com.salesforce.androidsdk.accounts.UserAccountTest.TEST_ACCOUNT_NAME;
import static com.salesforce.androidsdk.accounts.UserAccountTest.TEST_ACCOUNT_NAME_2;
import static com.salesforce.androidsdk.accounts.UserAccountTest.TEST_AUTH_TOKEN;
import static com.salesforce.androidsdk.accounts.UserAccountTest.TEST_CLIENT_ID;
import static com.salesforce.androidsdk.accounts.UserAccountTest.TEST_CUSTOM_KEY;
import static com.salesforce.androidsdk.accounts.UserAccountTest.TEST_CUSTOM_VALUE;
import static com.salesforce.androidsdk.accounts.UserAccountTest.TEST_INSTANCE_URL;
import static com.salesforce.androidsdk.accounts.UserAccountTest.TEST_LOGIN_URL;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.salesforce.androidsdk.TestForceApp;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.accounts.UserAccountTest;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.ClientManager.AccountInfoNotFoundException;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;
import com.salesforce.androidsdk.rest.ClientManager.RestClientCallback;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.LogUtil;
import com.salesforce.androidsdk.util.test.EventsListenerQueue;
import com.salesforce.androidsdk.util.test.TestCredentials;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class ClientManagerTest {

    public static final String[] TEST_SCOPES = new String[] {"web"};
    public static final String TEST_CALLBACK_URL = "test://callback";

    private ClientManager clientManager;
    private AccountManager accountManager;
    private UserAccountManager userAccountManager;
    private EventsListenerQueue eq;
    private List<String> testOauthKeys;
    private Map<String, String> testOauthValues;

    @Before
    public void setUp() throws Exception {
        final Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final Application app = Instrumentation.newApplication(TestForceApp.class, targetContext);
        InstrumentationRegistry.getInstrumentation().callApplicationOnCreate(app);
        TestCredentials.init(InstrumentationRegistry.getInstrumentation().getContext());
        final LoginOptions loginOptions = new LoginOptions(TEST_LOGIN_URL, TEST_CALLBACK_URL, TEST_CLIENT_ID, TEST_SCOPES);
        clientManager = new ClientManager(targetContext, TEST_ACCOUNT_TYPE, loginOptions, true);
        accountManager = clientManager.getAccountManager();
        eq = new EventsListenerQueue();
        if (!SalesforceSDKManager.hasInstance()) {
            eq.waitForEvent(EventType.AppCreateComplete, 5000);
        }
        userAccountManager = SalesforceSDKManager.getInstance().getUserAccountManager();
        testOauthKeys = new ArrayList<>();
        testOauthKeys.add(TEST_CUSTOM_KEY);
        testOauthValues = new HashMap<>();
        testOauthValues.put(TEST_CUSTOM_KEY, TEST_CUSTOM_VALUE);
        SalesforceSDKManager.getInstance().setAdditionalOauthKeys(testOauthKeys);
    }

    @After
    public void tearDown() throws Exception {
        if (eq != null) {
            eq.tearDown();
            eq = null;
        }
        cleanupAccounts();
        assertNoAccounts();
        testOauthKeys = null;
        testOauthValues = null;
        SalesforceSDKManager.getInstance().setAdditionalOauthKeys(testOauthKeys);
    }

    /**
     * Test getAccountType
     */
    @Test
    public void testGetAccountType() {
        Assert.assertEquals("Wrong account type", TEST_ACCOUNT_TYPE, clientManager.getAccountType());
    }

    /**
     * Test setting/get of Login Options as a bundle
     */
    @Test
    public void testLoginOptionsWithAdditionalParams() {
        Map<String,String> additionalParams = new HashMap<>();
        additionalParams.put("p1","v1");
        additionalParams.put("p2","v2");
        additionalParams.put("p3",null);
        LoginOptions loginOptions = new LoginOptions(TEST_LOGIN_URL,
                TEST_CALLBACK_URL, TEST_CLIENT_ID, TEST_SCOPES, null, additionalParams);
        Assert.assertNotNull("LoginOptions must not be null",loginOptions);
        Assert.assertNotNull("LoginOptions must not be null",loginOptions.getAdditionalParameters());
        Assert.assertEquals("# of LoginOptions must be correct",additionalParams.size(),loginOptions.getAdditionalParameters().size());
        Assert.assertEquals("LoginOptions must be correct",additionalParams.get("p1"),loginOptions.getAdditionalParameters().get("p1"));
        additionalParams = new HashMap<>();
        additionalParams.put("p4","v1");
        additionalParams.put("p5","v2");
        loginOptions.setAdditionalParameters(additionalParams);
        Assert.assertEquals("# of LoginOptions must be correct",additionalParams.size(),loginOptions.getAdditionalParameters().size());
        Bundle bundle = loginOptions.asBundle();
        Assert.assertNotNull("LoginOptions Bundle must not be null",bundle);
        Assert.assertNotNull("LoginOptions Bundle must have parameter map",bundle.getSerializable("addlParams"));
        loginOptions = LoginOptions.fromBundle(bundle);
        Assert.assertNotNull("LoginOptions from bundle should not be null",loginOptions);
        Assert.assertNotNull("LoginOptions.additionalParameters from bundle should not be null",loginOptions.getAdditionalParameters());
        Assert.assertEquals("LoginOptions.additionalParameters from bundle should not be null",additionalParams.size(),loginOptions.getAdditionalParameters().size());
        Assert.assertEquals("LoginOptions.additionalParameters must have parameter",additionalParams.get("p4"),loginOptions.getAdditionalParameters().get("p4"));
    }

    /**
     * Test createNewAccount
     */
    @Test
    public void testCreateAccount() {

        // Make sure we have no accounts initially
        assertNoAccounts();

        // Call createNewAccount
        createTestAccountInAccountManager();

        // Check that the account did get created
        Account[] accounts = clientManager.getAccounts();
        Assert.assertEquals("One account should have been returned", 1, accounts.length);
        Assert.assertEquals("Wrong account name", TEST_ACCOUNT_NAME, accounts[0].name);
        Assert.assertEquals("Wrong account type", TEST_ACCOUNT_TYPE, accounts[0].type);
    }

    /**
     * Test getAccount
     */
    @Test
    public void testGetAccount() {

        UserAccount userAccount = UserAccountTest.createTestAccount();

        // Save to account manager (encrypt fields)
        clientManager.createNewAccount(userAccount);

        // Get account from account manager
        Account account = clientManager.getAccount();

        // Build user account from account (decrypts fields)
        UserAccount restoredUserAccount = userAccountManager.buildUserAccount(account);

        // Make sure all the fields made it through and back
        UserAccountTest.checkSameUserAccount(userAccount, restoredUserAccount);
    }

    /**
     * Test getAccounts - when there is only one
     */
    @Test
    public void testGetAccountsWithSingleAccount() {

        // Make sure we have no accounts initially
        assertNoAccounts();

        // Call createNewAccount
        createTestAccountInAccountManager();

        // Call getAccounts
        Account[] accounts = clientManager.getAccounts();
        Assert.assertEquals("One account should have been returned", 1, accounts.length);
        Assert.assertEquals("Wrong account name", TEST_ACCOUNT_NAME, accounts[0].name);
        Assert.assertEquals("Wrong account type", TEST_ACCOUNT_TYPE, accounts[0].type);
    }

    /**
     * Test getAccounts - when there are several accounts
     */
    @Test
    public void testGetAccountsWithSeveralAccounts() {

        // Make sure we have no accounts initially
        assertNoAccounts();

        // Call two accounts
        createTestAccountInAccountManager();
        createOtherTestAccountInAccountManager();

        // Call getAccounts
        Account[] accounts = clientManager.getAccounts();
        Assert.assertEquals("Two accounts should have been returned", 2, accounts.length);

        // Sorting
        Arrays.sort(accounts, new Comparator<Account>() {
            @Override
            public int compare(Account account1, Account account2) {
                return account1.name.compareTo(account2.name);
            }
        });

        Assert.assertEquals("Wrong account name", TEST_ACCOUNT_NAME, accounts[0].name);
        Assert.assertEquals("Wrong account name", TEST_ACCOUNT_NAME_2, accounts[1].name);
    }

    /**
     * Test getAccountByName
     */
    @Test
    public void testGetAccountByName() {

        // Make sure we have no accounts initially
        assertNoAccounts();

        // Create two accounts
        createTestAccountInAccountManager();
        createOtherTestAccountInAccountManager();

        // Check that the accounts did get created
        Account[] accounts = clientManager.getAccounts();
        Assert.assertEquals("Two accounts should have been returned", 2, accounts.length);

        // Get the first one by name
        Account account = clientManager.getAccountByName(TEST_ACCOUNT_NAME);
        Assert.assertNotNull("An account should have been returned", account);
        Assert.assertEquals("Wrong account name", TEST_ACCOUNT_NAME, account.name);
        Assert.assertEquals("Wrong account type", TEST_ACCOUNT_TYPE, account.type);

        // Get the second one by name
        account = clientManager.getAccountByName(TEST_ACCOUNT_NAME_2);
        Assert.assertNotNull("An account should have been returned", account);
        Assert.assertEquals("Wrong account name", TEST_ACCOUNT_NAME_2, account.name);
        Assert.assertEquals("Wrong account type", TEST_ACCOUNT_TYPE, account.type);
    }


    /**
     * Test removeAccounts when there is only one
     */
    @Test
    public void testRemoveOnlyAccount() {

        // Make sure we have no accounts initially
        assertNoAccounts();

        // Create an account
        createTestAccountInAccountManager();

        // Check that the account did get created
        Account[] accounts = clientManager.getAccounts();
        Assert.assertEquals("One account should have been returned", 1, accounts.length);
        Assert.assertEquals("Wrong account name", TEST_ACCOUNT_NAME, accounts[0].name);

        // Remove the account
        clientManager.removeAccounts(accounts);

        // Make sure there are no accounts left
        assertNoAccounts();
    }

    /**
     * Test removeAccounts - removing one account where there are several
     */
    @Test
    public void testRemoveOneOfSeveralAccounts() {

        // Make sure we have no accounts initially
        assertNoAccounts();

        // Create two accounts
        createTestAccountInAccountManager();
        createOtherTestAccountInAccountManager();

        // Check that the accounts did get created
        Account[] accounts = clientManager.getAccounts();
        Assert.assertEquals("Two accounts should have been returned", 2, accounts.length);

        // Remove one of them
        clientManager.removeAccounts(new Account[]{accounts[0]});

        // Make sure the other account is still there
        Account[] accountsLeft = clientManager.getAccounts();
        Assert.assertEquals("One account should have been returned", 1, accountsLeft.length);
        Assert.assertEquals("Wrong account name", accounts[1].name, accountsLeft[0].name);
    }

    /**
     * Test removeAccounts - removing two accounts
     */
    @Test
    public void testRemoveSeveralAccounts() {

        // Make sure we have no accounts initially
        assertNoAccounts();

        // Create two accounts
        createTestAccountInAccountManager();
        createOtherTestAccountInAccountManager();

        // Check that the accounts did get created
        Account[] accounts = clientManager.getAccounts();
        Assert.assertEquals("Two accounts should have been returned", 2, accounts.length);

        // Remove one of them
        clientManager.removeAccounts(accounts);

        // Make sure there are no accounts left
        assertNoAccounts();
    }

    /**
     * Test peekRestClient - when there is no account
     */
    @Test
    public void testPeekRestClientWhenNoAccounts() {

        // Make sure we have no accounts initially
        assertNoAccounts();

        // Call peekRestClient - expect exception
        try {
            clientManager.peekRestClient();
            Assert.fail("Expected AccountInfoNotFoundException");
        } catch (AccountInfoNotFoundException e) {
            // as expected
        }
    }

    /**
     * Test peekRestClient - when there is an account
     * @throws URISyntaxException
     */
    @Test
    public void testPeekRestClientWithAccountSetup() throws URISyntaxException {

        // Make sure we have no accounts initially
        assertNoAccounts();

        // Create account
        createTestAccountInAccountManager();

        // Call peekRestClient - expect restClient
        try {
            RestClient restClient = clientManager.peekRestClient();
            Assert.assertNotNull("RestClient expected", restClient);
            Assert.assertEquals("Wrong authToken", TEST_AUTH_TOKEN, restClient.getAuthToken());
            Assert.assertEquals("Wrong instance Url", new URI(TEST_INSTANCE_URL), restClient.getClientInfo().instanceUrl);
        } catch (AccountInfoNotFoundException e) {
            Assert.fail("Did not expect AccountInfoNotFoundException");
        }
    }

    /**
     * Test getRestClient - when there is an account
     * @throws URISyntaxException
     */
    @Test
    public void testGetRestClientWithAccountSetup() throws URISyntaxException {

        // Make sure we have no accounts initially
        assertNoAccounts();

        // Create account
        createTestAccountInAccountManager();

        // Call getRestClient - expect restClient
        final BlockingQueue<RestClient> q = new ArrayBlockingQueue<RestClient>(1);
        clientManager.getRestClient(null, new RestClientCallback() {
            @Override
            public void authenticatedRestClient(RestClient client) {
                q.add(client);
            }
        });

        // Wait for getRestClient to complete
        try {
            RestClient restClient = q.poll(10L, TimeUnit.SECONDS);
            Assert.assertNotNull("RestClient expected", restClient);
            Assert.assertEquals("Wrong authToken", TEST_AUTH_TOKEN, restClient.getAuthToken());
            Assert.assertEquals("Wrong instance Url", new URI(TEST_INSTANCE_URL), restClient.getClientInfo().instanceUrl);
        } catch (InterruptedException e) {
            Assert.fail("getRestClient did not return after 5s");
        }
    }

    /**
     * Test removeAccount
     */
    @Test
    public void testRemoveAccount() throws Exception {

        // Make sure we have no accounts initially
        assertNoAccounts();

        // Create account
        createTestAccountInAccountManager();

        // Check that the accounts did get created
        Account[] accounts = clientManager.getAccounts();
        Assert.assertEquals("Two accounts should have been returned", 1, accounts.length);

        // Call removeAccount
        clientManager.removeAccount(clientManager.getAccount());

        // Make sure there are no accounts left
        assertNoAccounts();
    }

    @Test
    public void testLoginOptionsToFromBundle() {
        Bundle options = new Bundle();
        options.putString("loginUrl", "some-login-url");
        options.putString("oauthCallbackUrl", "some-oauth-callback-url");
        options.putString("oauthClientId", "some-oauth-client-id");
        options.putStringArray("oauthScopes", new String[] { "some-scope", "some-other-scope"});
        options.putString("jwt", "some-jwt");

        LoginOptions loginOptions = LoginOptions.fromBundle(options);
        Assert.assertEquals("some-login-url", loginOptions.getLoginUrl());
        Assert.assertEquals("some-oauth-callback-url", loginOptions.getOauthCallbackUrl());
        Assert.assertEquals("some-oauth-client-id", loginOptions.getOauthClientId());
        Assert.assertArrayEquals(new String[] { "some-scope", "some-other-scope"}, loginOptions.getOauthScopes());
        Assert.assertEquals("some-jwt", loginOptions.getJwt());

        Bundle recreatedBundle = loginOptions.asBundle();
        Assert.assertEquals(LogUtil.bundleToString(recreatedBundle), LogUtil.bundleToString(options));
    }

    @Test
    public void testLoginOptionsFromBundleWithSafeLoginUrl() {
        // First using a bundle with a known login url (test.salesforce.com)
        Bundle options = new Bundle();
        options.putString("loginUrl", "https://test.salesforce.com");
        options.putString("oauthCallbackUrl", "some-oauth-callback-url");
        options.putString("oauthClientId", "some-oauth-client-id");
        options.putStringArray("oauthScopes", new String[] { "some-scope", "some-other-scope"});
        options.putString("jwt", "some-jwt");

        // Expect the LoginOptions to have the same login url (test.salesforce.com)
        LoginOptions loginOptions = LoginOptions.fromBundleWithSafeLoginUrl(options);
        Assert.assertEquals("https://test.salesforce.com", loginOptions.getLoginUrl());
        Assert.assertEquals("some-oauth-callback-url", loginOptions.getOauthCallbackUrl());
        Assert.assertEquals("some-oauth-client-id", loginOptions.getOauthClientId());
        Assert.assertArrayEquals(new String[] { "some-scope", "some-other-scope"}, loginOptions.getOauthScopes());
        Assert.assertEquals("some-jwt", loginOptions.getJwt());

        // Now using a bundle with an unknown login url
        options.putString("loginUrl", "some-login-url");

        // Expect the LoginOptions to have the selected login server url (login.salesforce.com)
        loginOptions = LoginOptions.fromBundleWithSafeLoginUrl(options);
        Assert.assertEquals("https://login.salesforce.com", SalesforceSDKManager.getInstance().getLoginServerManager().getSelectedLoginServer().url);
        Assert.assertEquals("https://login.salesforce.com", loginOptions.getLoginUrl());
        Assert.assertEquals("some-oauth-callback-url", loginOptions.getOauthCallbackUrl());
        Assert.assertEquals("some-oauth-client-id", loginOptions.getOauthClientId());
        Assert.assertArrayEquals(new String[] { "some-scope", "some-other-scope"}, loginOptions.getOauthScopes());
        Assert.assertEquals("some-jwt", loginOptions.getJwt());

    }

    /**
     * Checks there are no test accounts
     */
    private void assertNoAccounts() {
        Assert.assertEquals("There should be no accounts", 0, accountManager.getAccountsByType(TEST_ACCOUNT_TYPE).length);
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
    private UserAccount createTestAccountInAccountManager() {
        UserAccount userAccount = UserAccountTest.createTestAccount();
        clientManager.createNewAccount(userAccount);
        return userAccount;
    }

    /**
     * Create other test account
     * @return
     */
    private UserAccount createOtherTestAccountInAccountManager() {
        UserAccount userAccount = UserAccountTest.createOtherTestAccount();
        clientManager.createNewAccount(userAccount);
        return userAccount;
    }
}
