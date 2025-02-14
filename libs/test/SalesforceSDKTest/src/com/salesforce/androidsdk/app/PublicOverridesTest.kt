package com.salesforce.androidsdk.app

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.config.LoginServerManager
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions
import com.salesforce.androidsdk.ui.LoginActivity
import com.salesforce.androidsdk.ui.LoginViewModel
import org.junit.Test
import org.junit.runner.RunWith

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

    @Test
    fun overrideLoginActivity() {
        class OverrideLoginActivity : LoginActivity() {
            override val webView = WebView(context)
            override val webChromeClient = WebChromeClient()
            override val webViewClient = OverrideWebViewClient()

            override fun shouldUseCertBasedAuth(): Boolean { return  true }
            override fun onIDPLoginClick() { }
            override fun onBioAuthClick() { }
            override fun fixBackButtonBehavior(keyCode: Int): Boolean { return false }
            override fun certAuthOrLogin() { }
            override fun onAuthFlowSuccess(userAccount: UserAccount) { }

            private inner class OverrideWebViewClient: AuthWebViewClient() {
                override fun toString(): String {
                    return "non-redundant override"
                }
            }
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // Instantiate to ensure this compiles.
            OverrideLoginActivity()
        }
    }

    @Test
    fun overrideLoginViewModel() {
        class OverrideLoginViewModel(bootConfig: BootConfig): LoginViewModel(bootConfig) {
            override val authorizationDisplayType = ""
            override var clientId = ""
            override fun clearCookies() { }
            override fun buildAccountName(username: String?, instanceServer: String?): String {
                return super.buildAccountName("", "")
            }

            // UI
            override var titleText: String? = ""
            override var topBarColor: Color? = Color.Black
        }

        // Instantiate to ensure this compiles.  Note: factory should be used in production but cannot be added to inner class.
        OverrideLoginViewModel(BootConfig.getBootConfig(context))
    }

    @Test
    fun overrideSalesforceSDKManager() {
        class OverrideSalesforceSDKManager(context: Context, mainActivity: Class<out Activity>) : SalesforceSDKManager(context, mainActivity) {
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
        }

        // Instantiate to ensure this compiles.
        OverrideSalesforceSDKManager(context, LoginActivity::class.java)
    }
}
