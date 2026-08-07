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
package com.salesforce.androidsdk.app

import android.accounts.Account
import android.app.Activity
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.auth.OAuth2
import com.salesforce.androidsdk.auth.OAuth2.LogoutReason
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.rest.ClientManager.AccMgrAuthTokenProvider
import com.salesforce.androidsdk.ui.LoginViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Modifier
import java.net.URI

/** Guards the intentional public API changes made at the Mobile SDK 14.0 boundary. */
@RunWith(AndroidJUnit4::class)
@SmallTest
class MobileSdk14ApiSurfaceTest {

    @Test
    fun authenticatedManagerAndProviderExposeBoundAndCompatibilityConstructors() {
        assertEquals(
            setOf(listOf(Context::class.java, UserAccount::class.java)),
            ClientManager::class.java.constructors
                .map { it.parameterTypes.toList() }
                .toSet(),
        )
        assertEquals(
            setOf(
                listOf(ClientManager::class.java),
                listOf(ClientManager::class.java, String::class.java),
                listOf(
                    ClientManager::class.java,
                    String::class.java,
                    String::class.java,
                    String::class.java,
                ),
            ),
            AccMgrAuthTokenProvider::class.java.constructors
                .map { it.parameterTypes.toList() }
                .toSet(),
        )
        val compatibilityConstructor = AccMgrAuthTokenProvider::class.java.getConstructor(
            ClientManager::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
        )
        assertTrue(
            compatibilityConstructor.isAnnotationPresent(java.lang.Deprecated::class.java),
        )
    }

    @Test
    fun unboundAndMovedClientManagerMethodsAreNotPublicApi() {
        val removedMethodNames = setOf(
            "getRestClient",
            "getUnauthenticatedRestClient",
            "peekUnauthenticatedRestClient",
            "invalidateToken",
            "getAccountByName",
            "getAccounts",
            "removeAccounts",
            "createNewAccount",
            "getAccountType",
            "getAccountManager",
            "removeAccount",
        )
        assertTrue(
            ClientManager::class.java.methods.none { it.name in removedMethodNames },
        )
        assertFalse(ClientManager::class.java.hasPublicMethod("peekRestClient", UserAccount::class.java))
        assertFalse(ClientManager::class.java.hasPublicMethod("peekRestClient", Account::class.java))
        assertTrue(ClientManager::class.java.hasPublicMethod("peekRestClient"))
    }

    @Test
    fun removedManagerAndMagicLinkConveniencesStayAbsent() {
        assertFalse(
            SalesforceSDKManager::class.java.hasPublicMethod(
                "getClientManager",
                String::class.java,
                String::class.java,
            ),
        )
        assertTrue(SalesforceSDKManager::class.java.hasPublicMethod("getClientManager"))
        assertTrue(
            SalesforceSDKManager::class.java.methods.none {
                it.name == "shouldLogoutWhenTokenRevoked"
            },
        )

        val removedLoginAccessorPrefixes = setOf(
            "getJwt",
            "setJwt",
            "getAuthCodeForJwtFlow",
            "setAuthCodeForJwtFlow",
        )
        assertTrue(
            LoginViewModel::class.java.declaredMethods.none { method ->
                removedLoginAccessorPrefixes.any(method.name::startsWith)
            },
        )
        val openIdToken = OAuth2::class.java.getMethod(
            "getOpenIDToken",
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
        )
        assertTrue(Modifier.isPublic(openIdToken.modifiers))
        assertTrue(Modifier.isStatic(openIdToken.modifiers))
    }

    @Test
    fun lowLevelJwtSwapRemainsAvailable() {
        val jwtSwap = OAuth2::class.java.getMethod(
            "swapJWTForTokens",
            HttpAccess::class.java,
            URI::class.java,
            String::class.java,
        )

        assertTrue(Modifier.isPublic(jwtSwap.modifiers))
        assertTrue(Modifier.isStatic(jwtSwap.modifiers))
        assertEquals(ClientManager::class.java, SalesforceSDKManager::class.java
            .getMethod("getClientManager").returnType)
        assertTrue(SalesforceSDKManager::class.java.hasPublicMethod("isLoggingOut"))
        assertTrue(
            SalesforceSDKManager::class.java.hasPublicMethod(
                "isLoggingOut",
                Account::class.java,
            ),
        )
        assertTrue(
            SalesforceSDKManager::class.java.methods.none {
                it.name == "isLoggingOut" &&
                        it.parameterTypes.toList() != emptyList<Class<*>>() &&
                        it.parameterTypes.toList() != listOf(Account::class.java)
            },
        )
        assertTrue(
            SalesforceSDKManager::class.java.methods.none {
                it.name == "setLoggingOut"
            },
        )
    }

    @Test
    fun logoutExposesOnlyTheAccountBasedOverride() {
        assertEquals(
            setOf(
                listOf(
                    Account::class.java,
                    Activity::class.java,
                    Boolean::class.javaPrimitiveType,
                    LogoutReason::class.java,
                ),
            ),
            SalesforceSDKManager::class.java.methods
                .filter { it.name == "logout" }
                .map { it.parameterTypes.toList() }
                .toSet(),
        )
        assertTrue(
            SalesforceSDKManager::class.java.methods.none {
                it.name == "logoutUser"
            },
        )
    }

    private fun Class<*>.hasPublicMethod(
        name: String,
        vararg parameterTypes: Class<*>,
    ): Boolean = methods.any {
        it.name == name && it.parameterTypes.toList() == parameterTypes.toList()
    }
}
