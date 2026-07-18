/*
 * Copyright (c) 2025-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.accounts.UserAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [logoutAllUsersAndClearData].
 *
 * All dependencies are passed as lambdas, so no Android framework objects or
 * SDK singletons need to be mocked — the tests run in the instrumentation
 * process without launching any Activity.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class ManageSpaceActivityTest {

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    fun test_givenSingleUser_whenConfirm_thenSignoutCalledOnceAndClearDataCalled() {
        val user = createUser("user1")
        val signedOut = mutableListOf<UserAccount>()
        var fallbackCalled = false
        var clearDataCalled = false

        logoutAllUsersAndClearData(
            authenticatedUsers = listOf(user),
            signoutUser = { signedOut.add(it) },
            fallbackLogout = { fallbackCalled = true },
            clearData = { clearDataCalled = true },
        )

        assertEquals("signoutUser must be called exactly once", listOf(user), signedOut)
        assertTrue("clearApplicationUserData must be called", clearDataCalled)
        assertTrue("fallback logout must not be called with a known user", !fallbackCalled)
    }

    @Test
    fun test_givenMultipleUsers_whenConfirm_thenSignoutCalledForEachUserAndClearDataCalled() {
        val users = listOf(createUser("user1"), createUser("user2"), createUser("user3"))
        val signedOut = mutableListOf<UserAccount>()
        var clearDataCalled = false

        logoutAllUsersAndClearData(
            authenticatedUsers = users,
            signoutUser = { signedOut.add(it) },
            fallbackLogout = {},
            clearData = { clearDataCalled = true },
        )

        assertEquals("signoutUser must be called for each user", users, signedOut)
        assertTrue("clearApplicationUserData must be called", clearDataCalled)
    }

    @Test
    fun test_givenNoUsers_whenConfirm_thenFallbackLogoutCalledAndClearDataCalled() {
        val signedOut = mutableListOf<UserAccount>()
        var fallbackCalled = false
        var clearDataCalled = false

        logoutAllUsersAndClearData(
            authenticatedUsers = null,
            signoutUser = { signedOut.add(it) },
            fallbackLogout = { fallbackCalled = true },
            clearData = { clearDataCalled = true },
        )

        assertTrue("signoutUser must not be called when no users", signedOut.isEmpty())
        assertTrue("fallback logout must be called when no users", fallbackCalled)
        assertTrue("clearApplicationUserData must be called", clearDataCalled)
    }

    @Test
    fun test_givenEmptyUserList_whenConfirm_thenFallbackLogoutCalledAndClearDataCalled() {
        val signedOut = mutableListOf<UserAccount>()
        var fallbackCalled = false
        var clearDataCalled = false

        logoutAllUsersAndClearData(
            authenticatedUsers = emptyList(),
            signoutUser = { signedOut.add(it) },
            fallbackLogout = { fallbackCalled = true },
            clearData = { clearDataCalled = true },
        )

        assertTrue("signoutUser must not be called for empty list", signedOut.isEmpty())
        assertTrue("fallback logout must be called for empty list", fallbackCalled)
        assertTrue("clearApplicationUserData must be called", clearDataCalled)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun createUser(userId: String): UserAccount =
        com.salesforce.androidsdk.accounts.UserAccountBuilder.getInstance()
            .authToken("token")
            .refreshToken("refresh")
            .loginServer("https://test.salesforce.com")
            .idUrl("https://test.salesforce.com/org/$userId")
            .instanceServer("https://cs1.salesforce.com")
            .orgId("org")
            .userId(userId)
            .username("$userId@example.com")
            .accountName("$userId (https://cs1.salesforce.com) (SalesforceSDKTest)")
            .build()
}
