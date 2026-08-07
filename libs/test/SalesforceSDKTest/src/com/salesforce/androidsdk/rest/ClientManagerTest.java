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
import static com.salesforce.androidsdk.accounts.UserAccountTest.TEST_AUTH_TOKEN;
import static com.salesforce.androidsdk.accounts.UserAccountTest.TEST_CUSTOM_KEY;
import static com.salesforce.androidsdk.accounts.UserAccountTest.TEST_CUSTOM_VALUE;
import static com.salesforce.androidsdk.accounts.UserAccountTest.TEST_INSTANCE_URL;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.salesforce.androidsdk.TestForceApp;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountBuilder;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.accounts.UserAccountTest;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.AuthenticatorService;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.test.EventsListenerQueue;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class ClientManagerTest {

    private ClientManager clientManager;
    private Context targetContext;
    private AccountManager accountManager;
    private UserAccountManager userAccountManager;
    private EventsListenerQueue eq;
    private List<String> testOauthKeys;
    private Map<String, String> testOauthValues;

    @Before
    public void setUp() throws Exception {
        targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final Application app = Instrumentation.newApplication(TestForceApp.class, targetContext);
        InstrumentationRegistry.getInstrumentation().callApplicationOnCreate(app);
        accountManager = AccountManager.get(targetContext);
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
        if (accountManager != null) {
            cleanupAccounts();
            assertNoAccounts();
        }
        testOauthKeys = null;
        testOauthValues = null;
        SalesforceSDKManager.getInstance().setAdditionalOauthKeys(testOauthKeys);
    }

    /** Test that a bound manager retains the exact persisted account type. */
    @Test
    public void testBoundManagerRetainsAccountType() {
        createTestAccountInAccountManager();
        Assert.assertNotNull(clientManager.getAccount());
        Assert.assertEquals("Wrong account type", TEST_ACCOUNT_TYPE, clientManager.getAccount().type);
    }

    /**
     * Test getAccount
     */
    @Test
    public void testGetAccount() {

        UserAccount userAccount = UserAccountTest.createTestAccount();

        // Save to account manager (encrypt fields) and bind a manager to it.
        userAccountManager.createAccount(userAccount);
        clientManager = new ClientManager(targetContext, userAccount);

        // Get account from account manager
        Account account = clientManager.getAccount();
        Assert.assertNotNull(account);

        // Build user account from account (decrypts fields)
        UserAccount restoredUserAccount = userAccountManager.buildUserAccount(account);

        // Make sure all the fields made it through and back
        UserAccountTest.checkSameUserAccount(userAccount, restoredUserAccount);
    }

    /** An unpersisted user leaves the manager unbound without throwing. */
    @Test
    public void testConstructorWithUnpersistedUserIsUnbound() {
        assertNoAccounts();
        assertManagerIsUnbound(new ClientManager(targetContext, UserAccountTest.createTestAccount()));
    }

    /** A supplied identity that does not match its persisted record leaves the manager unbound. */
    @Test
    public void testConstructorWithMismatchedIdentityIsUnbound() {
        final UserAccount persistedUser = createTestAccountInAccountManager();
        final UserAccount mismatchedUser = UserAccountBuilder.getInstance()
                .populateFromUserAccount(persistedUser)
                .userId("different-user-id")
                .build();

        assertManagerIsUnbound(new ClientManager(targetContext, mismatchedUser));
    }

    /** A retained manager remains bound when application-wide current-user selection changes. */
    @Test
    public void testRetainedManagerRemainsBoundAfterCurrentUserSwitch() {
        final UserAccount userA = createTestAccountInAccountManager();
        final UserAccount userB = createOtherTestAccountInAccountManager();
        userAccountManager.storeCurrentUserInfo(userB.getUserId(), userB.getOrgId());

        Assert.assertEquals(userB.getUserId(), userAccountManager.getCurrentUser().getUserId());
        Assert.assertNotNull(clientManager.getAccount());
        Assert.assertEquals(userA.getAccountName(), clientManager.getAccount().name);
    }

    /** Adding another account does not change the manager's exact Account target. */
    @Test
    public void testGetAccountRemainsExactBoundAccountAmongSeveral() {
        final UserAccount userA = createTestAccountInAccountManager();
        createOtherTestAccountInAccountManager();

        Assert.assertNotNull(clientManager.getAccount());
        Assert.assertEquals(userA.getAccountName(), clientManager.getAccount().name);
    }

    /** Client creation uses the bound identity rather than whichever user is current. */
    @Test
    public void testPeekRestClientUsesBoundUserWhenOtherUserIsCurrent() {
        final UserAccount userA = createTestAccountInAccountManager();
        final UserAccount userB = createOtherTestAccountInAccountManager();
        userAccountManager.storeCurrentUserInfo(userB.getUserId(), userB.getOrgId());

        final RestClient client = clientManager.peekRestClient();

        Assert.assertNotNull(client);
        Assert.assertEquals(userA.getUserId(), client.getClientInfo().userId);
        Assert.assertEquals(userA.getOrgId(), client.getClientInfo().orgId);
    }

    /** Provider credentials come only from the manager's bound user. */
    @Test
    public void testProviderInheritsManagerIdentity() {
        final UserAccount userA = createTestAccountInAccountManager();
        final UserAccount userB = UserAccountBuilder.getInstance()
                .populateFromUserAccount(UserAccountTest.createOtherTestAccount())
                .refreshToken("other_refresh_token")
                .build();
        userAccountManager.createAccount(userB);
        userAccountManager.storeCurrentUserInfo(userB.getUserId(), userB.getOrgId());

        final ClientManager.AccMgrAuthTokenProvider provider =
                new ClientManager.AccMgrAuthTokenProvider(clientManager);

        Assert.assertEquals(userA.getRefreshToken(), provider.getRefreshToken());
    }

    /** Missing required identifiers leave the manager unbound without throwing. */
    @Test
    public void testConstructorWithIncompleteIdentityIsUnbound() {
        final UserAccount missingIdentity = UserAccountBuilder.getInstance()
                .populateFromUserAccount(UserAccountTest.createTestAccount())
                .userId(null)
                .build();

        assertManagerIsUnbound(new ClientManager(targetContext, missingIdentity));
    }

    /** Missing client credentials fail closed at client creation without throwing. */
    @Test
    public void testPeekRestClientWithoutAuthTokenReturnsNull() {
        createTestAccountInAccountManager();
        final Account account = clientManager.getAccount();
        Assert.assertNotNull(account);
        accountManager.setUserData(account, AccountManager.KEY_AUTHTOKEN, null);

        Assert.assertNull(clientManager.peekRestClient());
    }

    /** Missing persisted login routing fails closed at client creation without throwing. */
    @Test
    public void testPeekRestClientWithoutLoginUrlReturnsNull() {
        createTestAccountInAccountManager();
        final Account account = clientManager.getAccount();
        Assert.assertNotNull(account);
        accountManager.setUserData(account, AuthenticatorService.KEY_LOGIN_URL, null);

        Assert.assertNull(clientManager.peekRestClient());
    }

    /** Missing persisted identity routing fails closed at client creation without throwing. */
    @Test
    public void testPeekRestClientWithoutIdentityUrlReturnsNull() {
        createTestAccountInAccountManager();
        final Account account = clientManager.getAccount();
        Assert.assertNotNull(account);
        accountManager.setUserData(account, AuthenticatorService.KEY_ID_URL, null);

        Assert.assertNull(clientManager.peekRestClient());
    }

    /** Malformed persisted URLs fail closed at client creation without throwing. */
    @Test
    public void testPeekRestClientWithMalformedInstanceUrlReturnsNull() {
        final UserAccount user = UserAccountBuilder.getInstance()
                .populateFromUserAccount(UserAccountTest.createTestAccount())
                .instanceServer("https://invalid host.example")
                .build();
        userAccountManager.createAccount(user);

        final ClientManager manager = new ClientManager(targetContext, user);

        Assert.assertNotNull(manager.getAccount());
        Assert.assertNull(manager.peekRestClient());
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
        RestClient restClient = clientManager.peekRestClient();
        Assert.assertNotNull("RestClient expected", restClient);
        Assert.assertEquals("Wrong authToken", TEST_AUTH_TOKEN, restClient.getAuthToken());
        Assert.assertEquals("Wrong instance Url", new URI(TEST_INSTANCE_URL), restClient.getClientInfo().instanceUrl);
    }

    /** A specialized client route does not replace the account's identity or persisted route. */
    @Test
    public void testProviderRoutingOverride() {
        final UserAccount user = createTestAccountInAccountManager();
        final ClientManager.AccMgrAuthTokenProvider provider =
                new ClientManager.AccMgrAuthTokenProvider(
                        clientManager,
                        "https://special.route.example"
                );

        Assert.assertEquals("https://special.route.example", provider.getInstanceUrl());
        Assert.assertEquals(user.getRefreshToken(), provider.getRefreshToken());
    }

    /** Removing the bound account invalidates the retained manager. */
    @Test
    public void testRemovedBoundAccountCannotCreateClient() {
        createTestAccountInAccountManager();
        final Account boundAccount = clientManager.getAccount();
        Assert.assertNotNull(boundAccount);
        accountManager.removeAccountExplicitly(boundAccount);

        Assert.assertNull(clientManager.peekRestClient());
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
        removeAccounts(accountManager.getAccountsByType(TEST_ACCOUNT_TYPE));
    }

    /**
     * Create test account
     * @return
     */
    private UserAccount createTestAccountInAccountManager() {
        UserAccount userAccount = UserAccountTest.createTestAccount();
        userAccountManager.createAccount(userAccount);
        clientManager = new ClientManager(targetContext, userAccount);
        return userAccount;
    }

    /**
     * Create other test account
     * @return
     */
    private UserAccount createOtherTestAccountInAccountManager() {
        UserAccount userAccount = UserAccountTest.createOtherTestAccount();
        userAccountManager.createAccount(userAccount);
        return userAccount;
    }

    private void assertManagerIsUnbound(ClientManager manager) {
        Assert.assertNull(manager.getAccount());
        Assert.assertNull(manager.peekRestClient());
    }

    private void removeAccounts(Account[] accounts) {
        for (Account account : accounts) {
            accountManager.removeAccountExplicitly(account);
        }
    }
}
