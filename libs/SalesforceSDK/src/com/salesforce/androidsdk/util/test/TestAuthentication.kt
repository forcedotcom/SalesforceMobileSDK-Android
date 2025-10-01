package com.salesforce.androidsdk.test

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.accounts.UserAccountManager.USER_SWITCH_TYPE_DEFAULT
import com.salesforce.androidsdk.accounts.UserAccountManager.USER_SWITCH_TYPE_FIRST_LOGIN
import com.salesforce.androidsdk.accounts.UserAccountManager.USER_SWITCH_TYPE_LOGIN
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.rest.ClientManager.AccMgrAuthTokenProvider
import com.salesforce.androidsdk.util.test.TestCredentials

class TestAuthentication : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val creds = intent.getStringExtra("creds") ?: return
        TestCredentials.init(creds, this)

        val account = UserAccountBuilder.getInstance()
            .refreshToken(TestCredentials.REFRESH_TOKEN)
            .instanceServer(TestCredentials.INSTANCE_URL)
            .idUrl(TestCredentials.IDENTITY_URL)
            .orgId(TestCredentials.ORG_ID)
            .userId(TestCredentials.USER_ID)
            .communityUrl(TestCredentials.COMMUNITY_URL)
            .accountName(TestCredentials.ACCOUNT_NAME)
            .clientId(TestCredentials.CLIENT_ID)
            .photoUrl(TestCredentials.PHOTO_URL)
            .language(TestCredentials.LANGUAGE)
            .locale(TestCredentials.LOCALE)
            .loginServer(TestCredentials.LOGIN_URL)
            .authToken("nothing yet")
            .firstName("user")
            .lastName("test")
            .displayName("test user")
            .username("username")
            .build()

        val authTokenProvider = AccMgrAuthTokenProvider(
            SalesforceSDKManager.getInstance().clientManager,
            TestCredentials.INSTANCE_URL, null, TestCredentials.REFRESH_TOKEN
        )
        authTokenProvider.newAuthToken
        account.downloadProfilePhoto()

        val userAccountManager = SalesforceSDKManager.getInstance().userAccountManager
        // Send User Switch Intent, create user and switch to user.
        val numAuthenticatedUsers = userAccountManager.authenticatedUsers?.size ?: 0
        val userSwitchType = when {
            // We've already authenticated the first user, so there should be one
            numAuthenticatedUsers == 1 -> USER_SWITCH_TYPE_FIRST_LOGIN

            // Otherwise we're logging in with an additional user
            numAuthenticatedUsers > 1 -> USER_SWITCH_TYPE_LOGIN

            // This should never happen but if it does, pass in the "unknown" value
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