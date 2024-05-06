@file:Suppress("DEPRECATION")

package com.salesforce.androidsdk.app

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.auth.OAuth2
import com.salesforce.androidsdk.config.LoginServerManager
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions
import com.salesforce.androidsdk.ui.LoginActivity
import com.salesforce.androidsdk.ui.OAuthWebviewHelper
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI

/*
    Note: The code style in this class is bad on purpose.

    The implementation of the overridden functions and properties does not matter, it is only there
    for the sake of compilation.  What is important is the list of overrideable functions/properties
    that the test ensures still exist, which is easier to read without multi-line declarations and
    implementations.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
internal class PublicOverridesTest {
    private val context = InstrumentationRegistry.getInstrumentation().context
    private val callback = object : OAuthWebviewHelper.OAuthWebviewHelperEvents {
        override fun loadingLoginPage(loginUrl: String) {}
        override fun onAccountAuthenticatorResult(authResult: Bundle) {}
        override fun finish(userAccount: UserAccount?) {}
    }
    private val loginOptions = LoginOptions("", "", "", emptyArray<String>())

    @Test
    fun overrideOAuthWebviewHelper() {
        class Override(context: Context, callback: OAuthWebviewHelperEvents, loginOptions: LoginOptions) : OAuthWebviewHelper(context, callback, loginOptions) {

            override val authorizationDisplayType: String get() = ""
            override val loginUrl: String get() = ""

            // functions/properties below this are used by internal apps
            override fun clearCookies() {}
            override fun getAuthorizationUrl(useWebServerAuthentication: Boolean, useHybridAuthentication: Boolean): URI { return URI("") }
            override fun buildAccountName(username: String?, instanceServer: String?): String { return "" }
            override fun makeWebChromeClient(): WebChromeClient { return WebChromeClient() }
            override fun makeWebViewClient(): AuthWebViewClient { return AuthWebViewClient() }
            override fun onAuthFlowComplete(tr: OAuth2.TokenEndpointResponse?, nativeLogin: Boolean) { }
            override val oAuthClientId: String get() = super.oAuthClientId
            @Suppress("unused")
            private inner class TestClient: AuthWebViewClient()
        }

        // Instantiate to ensure this compiles.
        Override(context, callback, loginOptions)
    }

    @Test
    fun overrideLoginActivity() {
        class Override : LoginActivity() {
            override fun shouldUseCertBasedAuth(): Boolean { return  true }
            override fun onIDPLoginClick(v: View?) { }
            override fun onBioAuthClick(view: View?) { }

            // functions/properties below this are used by internal apps
            override fun fixBackButtonBehavior(keyCode: Int): Boolean { return false }
            override fun certAuthOrLogin() { }
            override fun getOAuthWebviewHelper(callback: OAuthWebviewHelper.OAuthWebviewHelperEvents, loginOptions: LoginOptions, webView: WebView, savedInstanceState: Bundle?): OAuthWebviewHelper { return OAuthWebviewHelper(this, callback, loginOptions, webView, savedInstanceState) }
            override fun onPickServerClick(v: View?) { }
            override fun onClearCookiesClick(v: View?) { }
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // Instantiate to ensure this compiles.
            Override()
        }
    }

    @Test
    fun overrideSalesforceSDKManager() {
        class Override(context: Context, mainActivity: Class<out Activity>) : SalesforceSDKManager(context, mainActivity) {
            override fun shouldLogoutWhenTokenRevoked(): Boolean { return false }
            override val appDisplayString: String get() = ""
            override fun cleanUp(userAccount: UserAccount?) { }
            override fun startLoginPage() { }
            override fun getUserAgent(qualifier: String): String { return "" }
            override fun compactScreen(activity: Activity): Boolean { return true }

            // functions/properties below this are used by internal apps
            override var isBrowserLoginEnabled = false
            override val userAccountManager: UserAccountManager get() = super.userAccountManager
            override fun isDevSupportEnabled(): Boolean { return false }
            override val loginOptions: LoginOptions get() = super.loginOptions
            override fun getLoginOptions(jwt: String?, url: String?): LoginOptions { return loginOptions }
            override fun logout(frontActivity: Activity?, showLoginPage: Boolean) { }
            override fun logout(account: Account?, frontActivity: Activity?, showLoginPage: Boolean) { }
            override val loginServerManager: LoginServerManager get() = super.loginServerManager
            override fun setViewNavigationVisibility(activity: Activity) { }
            override fun onAppBackgrounded() { }
            override fun onAppForegrounded() { }
        }

        // Instantiate to ensure this compiles.
        Override()
    }
}