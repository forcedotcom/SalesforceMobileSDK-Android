/*
 * Copyright (c) 2026-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
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
package com.salesforce.androidsdk.util

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.rest.RestClient.ClientInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI

@RunWith(AndroidJUnit4::class)
@SmallTest
class ExtensionFunctionsTest {

    @Test
    fun isSameUser_comparesAuthenticatedClientIdentity() {
        val user = user()

        assertTrue(user.isSameUser(clientInfo()))
        assertTrue(user.isSameUser(clientInfo(accountName = "other-account")))
        assertFalse(user.isSameUser(clientInfo(userId = "other-user")))
        assertFalse(user.isSameUser(clientInfo(orgId = "other-org")))
        assertFalse(user.isSameUser(clientInfo(userId = null)))
        assertFalse(user(userId = null).isSameUser(clientInfo()))
        assertFalse((null as UserAccount?).isSameUser(clientInfo()))
        assertFalse(user.isSameUser(null))
    }

    private fun user(
        accountName: String? = ACCOUNT_NAME,
        userId: String? = USER_ID,
        orgId: String? = ORG_ID,
    ) = UserAccount(
        Bundle().apply {
            putString(UserAccount.ACCOUNT_NAME, accountName)
            putString(UserAccount.USER_ID, userId)
            putString(UserAccount.ORG_ID, orgId)
        },
    )

    private fun clientInfo(
        accountName: String? = ACCOUNT_NAME,
        userId: String? = USER_ID,
        orgId: String? = ORG_ID,
    ) = ClientInfo(
        /* instanceUrl = */ URI.create("https://instance.example.com"),
        /* loginUrl = */ URI.create("https://login.example.com"),
        /* identityUrl = */ URI.create("https://login.example.com/id/$orgId/$userId"),
        /* accountName = */ accountName,
        /* username = */ null,
        /* userId = */ userId,
        /* orgId = */ orgId,
        /* communityId = */ null,
        /* communityUrl = */ null,
        /* firstName = */ null,
        /* lastName = */ null,
        /* displayName = */ null,
        /* email = */ null,
        /* photoUrl = */ null,
        /* thumbnailUrl = */ null,
        /* additionalOauthValues = */ null,
        /* lightningDomain = */ null,
        /* lightningSid = */ null,
        /* vfDomain = */ null,
        /* vfSid = */ null,
        /* contentDomain = */ null,
        /* contentSid = */ null,
        /* csrfToken = */ null,
    )

    private companion object {
        const val ACCOUNT_NAME = "account"
        const val USER_ID = "user"
        const val ORG_ID = "org"
    }
}
