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

package com.salesforce.androidsdk.util.test

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.accounts.UserAccountManager.USER_SWITCH_TYPE_DEFAULT
import com.salesforce.androidsdk.accounts.UserAccountManager.USER_SWITCH_TYPE_FIRST_LOGIN
import com.salesforce.androidsdk.accounts.UserAccountManager.USER_SWITCH_TYPE_LOGIN
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.rest.ClientManager.AccMgrAuthTokenProvider
import com.salesforce.androidsdk.util.test.TestCredentials.ACCOUNT_NAME
import com.salesforce.androidsdk.util.test.TestCredentials.CLIENT_ID
import com.salesforce.androidsdk.util.test.TestCredentials.COMMUNITY_URL
import com.salesforce.androidsdk.util.test.TestCredentials.IDENTITY_URL
import com.salesforce.androidsdk.util.test.TestCredentials.INSTANCE_URL
import com.salesforce.androidsdk.util.test.TestCredentials.LANGUAGE
import com.salesforce.androidsdk.util.test.TestCredentials.LOCALE
import com.salesforce.androidsdk.util.test.TestCredentials.LOGIN_URL
import com.salesforce.androidsdk.util.test.TestCredentials.ORG_ID
import com.salesforce.androidsdk.util.test.TestCredentials.PHOTO_URL
import com.salesforce.androidsdk.util.test.TestCredentials.REFRESH_TOKEN
import com.salesforce.androidsdk.util.test.TestCredentials.USERNAME
import com.salesforce.androidsdk.util.test.TestCredentials.USER_ID

/**
 * An activity that authenticates using credentials provided in the intent
 * rather than user interaction.  This is intended only for test automation in
 * app debug build variants.  This class should not be used in release builds as
 * it will simply finish without any action.
 */
class TestAuthenticationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val credentials = intent.getStringExtra("creds")
        if (!SalesforceSDKManager.getInstance().isDebugBuild || credentials == null) {
            finish()
            return
        }

        TestCredentials.init(credentials, this)

        val account = UserAccountBuilder.getInstance()
            .refreshToken(REFRESH_TOKEN)
            .instanceServer(INSTANCE_URL)
            .idUrl(IDENTITY_URL)
            .orgId(ORG_ID)
            .userId(USER_ID)
            .communityUrl(COMMUNITY_URL)
            .accountName(ACCOUNT_NAME)
            .clientId(CLIENT_ID)
            .photoUrl(PHOTO_URL)
            .language(LANGUAGE)
            .locale(LOCALE)
            .loginServer(LOGIN_URL)
            .authToken("Nothing yet")
            .firstName("User")
            .lastName("Test")
            .displayName("Test user")
            .username(USERNAME)
            .build()

        val authTokenProvider = AccMgrAuthTokenProvider(
            SalesforceSDKManager.getInstance().clientManager,
            INSTANCE_URL,
            null,
            REFRESH_TOKEN
        )
        authTokenProvider.newAuthToken
        account.downloadProfilePhoto()

        val userAccountManager = SalesforceSDKManager.getInstance().userAccountManager
        // Send user switch intent, create and switch to user.
        val numAuthenticatedUsers = userAccountManager.authenticatedUsers?.size ?: 0
        val userSwitchType = when {
            // We've already authenticated the first user, so there should be one.
            numAuthenticatedUsers == 1 -> USER_SWITCH_TYPE_FIRST_LOGIN

            // Otherwise we're logging in with an additional user.
            numAuthenticatedUsers > 1 -> USER_SWITCH_TYPE_LOGIN

            // This should never happen but if it does, pass in the "unknown" value.
            else -> USER_SWITCH_TYPE_DEFAULT
        }
        userAccountManager.sendUserSwitchIntent(userSwitchType, null)
        userAccountManager.createAccount(account)
        userAccountManager.switchToUser(account)

        startActivity(Intent(this, SalesforceSDKManager.getInstance().mainActivityClass).apply {
            setPackage(SalesforceSDKManager.getInstance().appContext.packageName)
            flags = FLAG_ACTIVITY_NEW_TASK
        })
    }
}
