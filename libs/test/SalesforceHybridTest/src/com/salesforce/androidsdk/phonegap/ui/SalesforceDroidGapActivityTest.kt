/*
 * Copyright (c) 2026-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
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
package com.salesforce.androidsdk.phonegap.ui

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.rest.ClientManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class SalesforceDroidGapActivityTest {

    private val context by lazy { SalesforceSDKManager.getInstance().appContext }
    private val userAccountManager by lazy { UserAccountManager.getInstance() }
    private val createdAccounts = mutableListOf<Account>()

    @Before
    fun setUp() {
        removeStaleTestAccounts()
    }

    @After
    fun tearDown() {
        val accountManager = AccountManager.get(context)
        createdAccounts.forEach(accountManager::removeAccountExplicitly)
        createdAccounts.clear()
        userAccountManager.clearStoredCurrentUserInfo()
    }

    @Test
    fun bindCurrentUserClient_rejectsCallbackForPreviousUser() {
        val userA = createUser("A")
        val userB = createUser("B")
        persist(userA)
        val callbackClientA = ClientManager(context, userA).peekRestClient()
        persist(userB)
        assertEquals(userB, userAccountManager.currentUser)
        val activity = createActivity()

        val resolved = activity.bindCurrentUserClient(callbackClientA)

        assertNull(resolved)
        assertNull(activity.restClient)
    }

    @Test
    fun onUserSwitched_rebindsActivityToCurrentUserAndRecreates() {
        val userA = createUser("A")
        val userB = createUser("B")
        persist(userA)
        val activity = createActivity()
        assertNotNull(activity.bindCurrentUserClient())
        assertEquals(userA.userId, activity.restClient?.clientInfo?.userId)
        assertEquals(userA.orgId, activity.restClient?.clientInfo?.orgId)
        var recreateCount = 0
        activity.recreateAfterUserSwitch = { recreateCount++ }

        persist(userB)
        activity.onUserSwitched()

        assertEquals(userB.userId, activity.restClient?.clientInfo?.userId)
        assertEquals(userB.orgId, activity.restClient?.clientInfo?.orgId)
        assertEquals(1, recreateCount)
    }

    @Test
    fun rebuildClientAfterRefresh_rejectsUserWhoStoppedBeingCurrent() {
        val userA = createUser("A")
        val userB = createUser("B")
        persist(userA)
        val managerA = ClientManager(context, userA)
        val activity = createActivity()
        assertNotNull(activity.bindCurrentUserClient(managerA.peekRestClient()))
        activity.recreateAfterUserSwitch = {}

        persist(userB)
        activity.onUserSwitched()

        assertFalse(activity.rebuildClientAfterRefresh(userA, managerA))
        assertEquals(userB.userId, activity.restClient?.clientInfo?.userId)
        assertEquals(userB.orgId, activity.restClient?.clientInfo?.orgId)
    }

    @Test
    fun tokenRefreshReceiver_setsCookiesForActivityBoundCurrentUser() {
        val userA = createUser("A")
        val userB = createUser("B")
        persist(userA)
        persist(userB)
        val activity = createActivity()
        assertNotNull(activity.bindCurrentUserClient())
        var cookieUser: UserAccount? = null
        activity.setAuthenticatedCookies = { user -> cookieUser = user }

        activity.TokenRefreshReceiver().onReceive(
            context,
            Intent(ClientManager.ACCESS_TOKEN_REFRESH_INTENT),
        )

        assertEquals(userB, cookieUser)
    }

    @Test
    fun onUserSwitched_withNoCurrentUser_clearsBindingAndRecreates() {
        val userA = createUser("A")
        persist(userA)
        val activity = createActivity()
        assertNotNull(activity.bindCurrentUserClient())
        var recreateCount = 0
        activity.recreateAfterUserSwitch = { recreateCount++ }

        createdAccounts.forEach(AccountManager.get(context)::removeAccountExplicitly)
        createdAccounts.clear()
        userAccountManager.clearStoredCurrentUserInfo()

        activity.onUserSwitched()

        assertNull(activity.restClient)
        assertNull(activity.boundCurrentUser())
        assertEquals(1, recreateCount)
    }

    private fun persist(user: UserAccount) {
        userAccountManager.createAccount(user)
        createdAccounts += requireNotNull(userAccountManager.buildAccount(user))
    }

    private fun createActivity(): SalesforceDroidGapActivity {
        var activity: SalesforceDroidGapActivity? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            activity = SalesforceDroidGapActivity()
        }
        return requireNotNull(activity)
    }

    private fun createUser(suffix: String) = UserAccountBuilder.getInstance()
        .accountName("hybrid-race-account-$suffix")
        .username("hybrid-race-$suffix@example.com")
        .refreshToken("hybrid-refresh-$suffix")
        .authToken("hybrid-auth-$suffix")
        .instanceServer("https://instance-$suffix.example.com")
        .loginServer("https://login.example.com")
        .idUrl("https://login.example.com/id/org-$suffix/user-$suffix")
        .clientId("hybrid-client-$suffix")
        .orgId("hybrid-org-$suffix")
        .userId("hybrid-user-$suffix")
        .nativeLogin(false)
        .build()

    private fun removeStaleTestAccounts() {
        val sdkManager = SalesforceSDKManager.getInstance()
        val accountManager = AccountManager.get(context)
        accountManager.getAccountsByType(sdkManager.accountType)
            .filter { account -> account.name.startsWith("hybrid-race-account-") }
            .forEach(accountManager::removeAccountExplicitly)
        userAccountManager.clearStoredCurrentUserInfo()
    }
}
