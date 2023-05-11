package com.salesforce.androidsdk.auth.idp

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.WindowManager.LayoutParams
import android.webkit.WebSettings.LayoutAlgorithm
import android.webkit.WebView
import com.salesforce.androidsdk.R
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.util.LogUtil
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import com.salesforce.androidsdk.auth.idp.interfaces.IDPAuthCodeActivity as IDPAuthCodeActivityInterface

/**
 * Activity showing web view through which IDP gets auth code for SP app
 */
class IDPAuthCodeActivity : Activity(), IDPAuthCodeActivityInterface {
    companion object {
        val TAG:String = IDPAuthCodeActivity::class.java.simpleName

        /**
         * Attach authCodeActivity to IDP manager's active flow (setting up the flow first if needed)
         */
        internal fun attachToActiveFlow(
            context: Context,
            authCodeActivity: IDPAuthCodeActivityInterface,
            idpManager: IDPManager,
            currentUser: UserAccount?,
            spConfig: SPConfig?
        ) {
            (idpManager.getActiveFlow() as? IDPLoginFlow)?.let { flow ->
                // Attach ourself to active flow
                flow.authCodeActivity = authCodeActivity
            } ?: run {
                // Setup active flow and attach ourself to it
                if (currentUser != null && spConfig != null) {
                    val flow = IDPLoginFlow(context, currentUser, spConfig, { _ -> })
                    idpManager.startActiveFlow(flow)
                    flow.authCodeActivity = authCodeActivity
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Protects against screenshots.
        window.setFlags(LayoutParams.FLAG_SECURE, LayoutParams.FLAG_SECURE)

        // Set theme
        val isDarkTheme = SalesforceSDKManager.getInstance().isDarkTheme;
        setTheme(if (isDarkTheme) R.style.SalesforceSDK_Dark_Login else R.style.SalesforceSDK);
        SalesforceSDKManager.getInstance().setViewNavigationVisibility(this);

        // Set layout
        setContentView(R.layout.sf__idp_auth_code)

        // Setup web view
        val webView = findViewById(R.id.sf__webview) as WebView
        val webSettings = webView.settings
        webSettings.useWideViewPort = true
        webSettings.layoutAlgorithm = LayoutAlgorithm.NORMAL

        (SalesforceSDKManager.getInstance().idpManager as? IDPManager)?.let { idpManager ->
            val currentUser = SalesforceSDKManager.getInstance().userAccountManager.currentUser
            val spConfig = idpManager.getSPConfig(intent)

            // Attach to IDP manager's active flow (setting up the flow first if needed)
            attachToActiveFlow(
                this@IDPAuthCodeActivity,
                this@IDPAuthCodeActivity,
                idpManager,
                currentUser,
                spConfig)

            // Handing intent to IDPManager
            idpManager.onReceive(this@IDPAuthCodeActivity, intent)

        } ?: run {
            SalesforceSDKLogger.d(this::class.java.simpleName, "no idp manager to handle ${LogUtil.intentToString(intent)}")
        }
    }

    override fun getWebView(): WebView {
        return findViewById(R.id.sf__webview) as WebView
    }
}