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

import android.accounts.AccountManager;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.salesforce.androidsdk.TestForceApp;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;
import com.salesforce.androidsdk.rest.ClientManagerTest;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.test.EventsListenerQueue;

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

    private EventsListenerQueue eq;
    private UserAccountManager userAccMgr;
    private LoginOptions loginOptions;
    private ClientManager clientManager;
    private AccountManager accMgr;

    @Before
    public void setUp() throws Exception {
        final Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final Application app = Instrumentation.newApplication(TestForceApp.class,
        		targetContext);
        InstrumentationRegistry.getInstrumentation().callApplicationOnCreate(app);
        eq = new EventsListenerQueue();
        if (!SalesforceSDKManager.hasInstance()) {
            eq.waitForEvent(EventType.AppCreateComplete, 5000);
        }
        userAccMgr = SalesforceSDKManager.getInstance().getUserAccountManager();
        loginOptions = new LoginOptions(ClientManagerTest.TEST_LOGIN_URL,
        		ClientManagerTest.TEST_CALLBACK_URL,
        		ClientManagerTest.TEST_CLIENT_ID,
				ClientManagerTest.TEST_SCOPES);
        clientManager = new ClientManager(targetContext,
        		ClientManagerTest.TEST_ACCOUNT_TYPE, loginOptions, true);
        accMgr = clientManager.getAccountManager();
    }

    @After
    public void tearDown() throws Exception {
        if (eq != null) {
            eq.tearDown();
            eq = null;
        }
        cleanupAccounts();
        userAccMgr = null;
        loginOptions = null;
        clientManager = null;
        accMgr = null;
    }

    /**
     * Test to get all authenticated users.
     */
    @Test
    public void testGetAllUserAccounts() {
    	List<UserAccount> users = userAccMgr.getAuthenticatedUsers();
        Assert.assertNull("There should be no authenticated users", users);
    	createTestAccount();
    	users = userAccMgr.getAuthenticatedUsers();
        Assert.assertEquals("There should be 1 authenticated user", 1, users.size());
    	createOtherTestAccount();
    	users = userAccMgr.getAuthenticatedUsers();
        Assert.assertEquals("There should be 2 authenticated users", 2, users.size());
    }

    /**
     * Test to get the current user account.
     */
    @Test
    public void testGetCurrentUserAccount() {
    	List<UserAccount> users = userAccMgr.getAuthenticatedUsers();
        Assert.assertNull("There should be no authenticated users", users);
    	createTestAccount();
    	users = userAccMgr.getAuthenticatedUsers();
        Assert.assertEquals("There should be 1 authenticated user", 1, users.size());
    	final UserAccount curUser = userAccMgr.getCurrentUser();
        Assert.assertNotNull("Current user should not be null", curUser);
        Assert.assertEquals("User IDs should match", ClientManagerTest.TEST_USER_ID,
    			curUser.getUserId());
        Assert.assertEquals("Org IDs should match", ClientManagerTest.TEST_ORG_ID,
    			curUser.getOrgId());
    }

    /**
     * Test to switch to a user account.
     */
    @Test
    public void testSwitchToUserAccount() {
    	List<UserAccount> users = userAccMgr.getAuthenticatedUsers();
        Assert.assertNull("There should be no authenticated users", users);
    	createTestAccount();
    	users = userAccMgr.getAuthenticatedUsers();
        Assert.assertEquals("There should be 1 authenticated user", 1, users.size());
    	createOtherTestAccount();
    	users = userAccMgr.getAuthenticatedUsers();
        Assert.assertEquals("There should be 2 authenticated users", 2, users.size());
    	UserAccount curUser = userAccMgr.getCurrentUser();
        Assert.assertNotNull("Current user should not be null", curUser);
        Assert.assertEquals("User IDs should match", ClientManagerTest.TEST_USER_ID_2,
    			curUser.getUserId());
        Assert.assertEquals("Org IDs should match", ClientManagerTest.TEST_ORG_ID_2,
    			curUser.getOrgId());
    	userAccMgr.switchToUser(users.get(0));
    	curUser = userAccMgr.getCurrentUser();
        Assert.assertNotNull("Current user should not be null", curUser);
        Assert.assertEquals("User IDs should match", ClientManagerTest.TEST_USER_ID,
    			curUser.getUserId());
        Assert.assertEquals("Org IDs should match", ClientManagerTest.TEST_ORG_ID,
    			curUser.getOrgId());
    }

    /**
     * Test to check if a user account exists.
     */
    @Test
    public void testDoesUserAccountExist() {
    	List<UserAccount> users = userAccMgr.getAuthenticatedUsers();
        Assert.assertNull("There should be no authenticated users", users);
    	createTestAccount();
    	users = userAccMgr.getAuthenticatedUsers();
        Assert.assertEquals("There should be 1 authenticated user", 1, users.size());
		UserAccount userAcc = UserAccountBuilder.getInstance().authToken(ClientManagerTest.TEST_AUTH_TOKEN).
                refreshToken(ClientManagerTest.TEST_REFRESH_TOKEN).loginServer(ClientManagerTest.TEST_LOGIN_URL).
                idUrl(ClientManagerTest.TEST_IDENTITY_URL).instanceServer(ClientManagerTest.TEST_INSTANCE_URL).
                orgId(ClientManagerTest.TEST_ORG_ID).userId(ClientManagerTest.TEST_USER_ID).
                username(ClientManagerTest.TEST_USERNAME).accountName(ClientManagerTest.TEST_ACCOUNT_NAME).
                communityId(null).communityUrl(null).firstName(null).lastName(null).displayName(null).
				email(null).photoUrl(null).thumbnailUrl(null).additionalOauthValues(null).build();
        Assert.assertTrue("User account should exist", userAccMgr.doesUserAccountExist(userAcc));
    	userAcc = UserAccountBuilder.getInstance().authToken(ClientManagerTest.TEST_AUTH_TOKEN).
                refreshToken(ClientManagerTest.TEST_REFRESH_TOKEN).loginServer(ClientManagerTest.TEST_LOGIN_URL).
                idUrl(ClientManagerTest.TEST_IDENTITY_URL).instanceServer(ClientManagerTest.TEST_INSTANCE_URL).
                orgId(ClientManagerTest.TEST_ORG_ID_2).userId(ClientManagerTest.TEST_USER_ID_2).
                username(ClientManagerTest.TEST_OTHER_USERNAME).accountName(ClientManagerTest.TEST_OTHER_ACCOUNT_NAME).
                communityId(null).communityUrl(null).firstName(null).lastName(null).displayName(null).
                email(null).photoUrl(null).thumbnailUrl(null).additionalOauthValues(null).build();
        Assert.assertFalse("User account should not exist", userAccMgr.doesUserAccountExist(userAcc));
    }

    /**
     * Test to signout of the current user.
     */
    @Test
    public void testSignoutCurrentUser() {
    	List<UserAccount> users = userAccMgr.getAuthenticatedUsers();
        Assert.assertNull("There should be no authenticated users", users);
    	createTestAccount();
    	users = userAccMgr.getAuthenticatedUsers();
        Assert.assertEquals("There should be 1 authenticated user", 1, users.size());
    	userAccMgr.signoutCurrentUser(null);
    	eq.waitForEvent(EventType.LogoutComplete, 30000);
    	users = userAccMgr.getAuthenticatedUsers();
        Assert.assertNull("There should be no authenticated users", users);
    }

    /**
     * Test to signout of a background user.
     */
    @Test
    public void testSignoutBackgroundUser() {
    	List<UserAccount> users = userAccMgr.getAuthenticatedUsers();
        Assert.assertNull("There should be no authenticated users", users);
    	createTestAccount();
    	users = userAccMgr.getAuthenticatedUsers();
        Assert.assertNotNull("There should be at least 1 authenticated user", users);
        Assert.assertEquals("There should be 1 authenticated user", 1, users.size());
    	createOtherTestAccount();
    	users = userAccMgr.getAuthenticatedUsers();
        Assert.assertEquals("There should be 2 authenticated users", 2, users.size());
        final UserAccount userAcc = UserAccountBuilder.getInstance().authToken(ClientManagerTest.TEST_AUTH_TOKEN).
                refreshToken(ClientManagerTest.TEST_REFRESH_TOKEN).loginServer(ClientManagerTest.TEST_LOGIN_URL).
                idUrl(ClientManagerTest.TEST_IDENTITY_URL).instanceServer(ClientManagerTest.TEST_INSTANCE_URL).
                orgId(ClientManagerTest.TEST_ORG_ID).userId(ClientManagerTest.TEST_USER_ID).
                username(ClientManagerTest.TEST_USERNAME).accountName(ClientManagerTest.TEST_ACCOUNT_NAME).
                communityId(null).communityUrl(null).firstName(null).lastName(null).displayName(null).
                email(null).photoUrl(null).thumbnailUrl(null).additionalOauthValues(null).build();
		userAccMgr.signoutUser(userAcc, null, false);
    	eq.waitForEvent(EventType.LogoutComplete, 30000);
    	users = userAccMgr.getAuthenticatedUsers();
        Assert.assertEquals("There should be 1 authenticated user", 1, users.size());
    }

    /**
     * Removes any existing accounts.
     */
    private void cleanupAccounts() throws Exception {
        clientManager.removeAccounts(accMgr.getAccountsByType(ClientManagerTest.TEST_ACCOUNT_TYPE));
    }

    /**
     * Create a test account.
     *
     * @return Bundle.
     */
    private Bundle createTestAccount() {
        return clientManager.createNewAccount(ClientManagerTest.TEST_ACCOUNT_NAME,
        		ClientManagerTest.TEST_USERNAME, ClientManagerTest.TEST_REFRESH_TOKEN,
        		ClientManagerTest.TEST_AUTH_TOKEN, ClientManagerTest.TEST_INSTANCE_URL,
        		ClientManagerTest.TEST_LOGIN_URL, ClientManagerTest.TEST_IDENTITY_URL,
        		ClientManagerTest.TEST_CLIENT_ID, ClientManagerTest.TEST_ORG_ID,
        		ClientManagerTest.TEST_USER_ID, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Create a test account.
     *
     * @return Bundle.
     */
    private Bundle createOtherTestAccount() {
        return clientManager.createNewAccount(ClientManagerTest.TEST_OTHER_ACCOUNT_NAME,
        		ClientManagerTest.TEST_OTHER_USERNAME, ClientManagerTest.TEST_REFRESH_TOKEN,
        		ClientManagerTest.TEST_AUTH_TOKEN, ClientManagerTest.TEST_INSTANCE_URL,
        		ClientManagerTest.TEST_LOGIN_URL, ClientManagerTest.TEST_IDENTITY_URL,
        		ClientManagerTest.TEST_CLIENT_ID, ClientManagerTest.TEST_ORG_ID_2,
        		ClientManagerTest.TEST_USER_ID_2, null, null, null, null, null, null, null, null, null);
    }
}
