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

import static com.salesforce.androidsdk.accounts.UserAccountTest.checkSameUserAccount;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.salesforce.androidsdk.auth.OAuth2;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Tests for UserAccountManager.
 *
 * @author bhariharan
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class UserAccountManagerTest {

    public static final String TEST_ACCOUNT_TYPE = "com.salesforce.androidsdk.salesforcesdktest.login"; // must match authenticator.xml in SalesforceSDK project

    private UserAccountManager userAccMgr;
    private AccountManager accMgr;

    @Before
    public void setUp() throws Exception {
        final Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        accMgr = AccountManager.get(targetContext);
        userAccMgr = UserAccountManager.getInstance();
        Assert.assertNull("There should be no authenticated users", userAccMgr.getAuthenticatedUsers());
    }

    @After
    public void tearDown() throws Exception {
        cleanupAccounts(accMgr);
        userAccMgr = null;
        accMgr = null;
    }

    /**
     * Test creating an account from a UserAccount and vice versa
     */
    @Test
    public void testUserAccountToAccountToUserAccount() {
        UserAccount userAccount = UserAccountTest.createTestAccount();
        // Save to account manager (encrypt fields)
        userAccMgr.createAccount(userAccount);
        // Get account from account manager
        Account account = userAccMgr.getCurrentAccount();
        // Build user account from account (decrypts fields)
        UserAccount restoredUserAccount = userAccMgr.buildUserAccount(account);
        // Make sure all the fields made it through and back
        checkSameUserAccount(userAccount, restoredUserAccount);
    }

    /**
     * Test to get all authenticated users.
     */
    @Test
    public void testGetAllUserAccounts() {
        UserAccount firstUser = createTestAccountInAccountManager(userAccMgr);
        List<UserAccount> users = userAccMgr.getAuthenticatedUsers();
        Assert.assertEquals("There should be 1 authenticated user", 1, users.size());
        checkSameUserAccount(firstUser, users.get(0));
        UserAccount secondUser = createOtherTestAccountInAccountManager();
        users = userAccMgr.getAuthenticatedUsers();
        Assert.assertEquals("There should be 2 authenticated users", 2, users.size());
        checkSameUserAccount(secondUser, users.get(1));
    }

    /**
     * Test to get the current user account.
     */
    @Test
    public void testGetCurrentUserAccount() {
        UserAccount userAccount = createTestAccountInAccountManager(userAccMgr);
        checkSameUserAccount(userAccount, userAccMgr.getCurrentUser());
    }

    /**
     * Test to switch to a user account.
     */
    @Test
    public void testSwitchToUserAccount() {
        UserAccount firstUser = createTestAccountInAccountManager(userAccMgr);
        checkSameUserAccount(firstUser, userAccMgr.getCurrentUser());

        UserAccount secondUser = createOtherTestAccountInAccountManager();
        checkSameUserAccount(secondUser, userAccMgr.getCurrentUser());

        userAccMgr.switchToUser(firstUser);
        checkSameUserAccount(firstUser, userAccMgr.getCurrentUser());

        userAccMgr.switchToUser(secondUser);
        checkSameUserAccount(secondUser, userAccMgr.getCurrentUser());
    }

    /**
     * Test to check if a user account exists.
     */
    @Test
    public void testDoesUserAccountExist() {
        // Creating UserAccount objects - but not in AccountManager
        UserAccount firstUser = UserAccountTest.createTestAccount();
        UserAccount secondUser = UserAccountTest.createOtherTestAccount();

        Assert.assertFalse("User should not exist yet", userAccMgr.doesUserAccountExist(firstUser));
        Assert.assertFalse("User should not exist yet", userAccMgr.doesUserAccountExist(secondUser));

        // Saving first to AccountManager
        userAccMgr.createAccount(firstUser);
        Assert.assertTrue("User should exist now", userAccMgr.doesUserAccountExist(firstUser));
        Assert.assertFalse("User should not exist yet", userAccMgr.doesUserAccountExist(secondUser));

        // Saving second to AccountManager
        userAccMgr.createAccount(secondUser);
        Assert.assertTrue("User should exist now", userAccMgr.doesUserAccountExist(firstUser));
        Assert.assertTrue("User should exist now", userAccMgr.doesUserAccountExist(secondUser));
    }

    /**
     * Test to signout of the current user.
     */
    @Test
    public void testSignoutCurrentUser() {
        createTestAccountInAccountManager(userAccMgr);
        Assert.assertEquals("There should be 1 authenticated user", 1, userAccMgr.getAuthenticatedUsers().size());
        userAccMgr.signoutCurrentUser(null, true, OAuth2.LogoutReason.USER_LOGOUT);
        Assert.assertNull("There should be no authenticated users", userAccMgr.getAuthenticatedUsers());
    }

    /**
     * Test to signout of a background user.
     */
    @Test
    public void testSignoutBackgroundUser() {
        UserAccount firstUser = createTestAccountInAccountManager(userAccMgr);
        UserAccount secondUser = createOtherTestAccountInAccountManager();
        userAccMgr.signoutUser(firstUser, null, false, OAuth2.LogoutReason.USER_LOGOUT);
        Assert.assertEquals("There should be 1 authenticated user", 1, userAccMgr.getAuthenticatedUsers().size());
        checkSameUserAccount(secondUser, userAccMgr.getCurrentUser());
    }

    /**
     * Removes any existing accounts.
     */
    public static void cleanupAccounts(AccountManager accountManager) {
        for (Account account : accountManager.getAccountsByType(TEST_ACCOUNT_TYPE)) {
            accountManager.removeAccountExplicitly(account);
        }
    }

    /**
     * Create a test account.
     *
     * @return UserAccount.
     */
    public static UserAccount createTestAccountInAccountManager(UserAccountManager userAccountManager) {
        UserAccount userAccount = UserAccountTest.createTestAccount();
        userAccountManager.createAccount(userAccount);
        return userAccount;
    }

    /**
     * Create a test account.
     *
     * @return UserAccount.
     */
    private UserAccount createOtherTestAccountInAccountManager() {
        UserAccount userAccount = UserAccountTest.createOtherTestAccount();
        userAccMgr.createAccount(userAccount);
        return userAccount;
    }

}
