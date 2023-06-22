package com.salesforce.androidsdk.auth.idp

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager.LayoutParams
import android.webkit.WebSettings.LayoutAlgorithm
import android.webkit.WebView
import com.salesforce.androidsdk.R
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.util.LogUtil
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import com.salesforce.androidsdk.auth.idp.interfaces.IDPAuthCodeActivity as IDPAuthCodeActivityInterface

/**
 * Activity showing web view through which IDP gets auth code for SP app
 */
class IDPAuthCodeActivity : Activity(), IDPAuthCodeActivityInterface {
    companion object {
        private val TAG: String = IDPAuthCodeActivity::class.java.simpleName
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Protects against screenshots.
        window.setFlags(LayoutParams.FLAG_SECURE, LayoutParams.FLAG_SECURE)

        // Set theme
        val isDarkTheme = SalesforceSDKManager.getInstance().isDarkTheme
        setTheme(if (isDarkTheme) R.style.SalesforceSDK_Dark_Login else R.style.SalesforceSDK)
        SalesforceSDKManager.getInstance().setViewNavigationVisibility(this)

        // Set layout
        setContentView(R.layout.sf__idp_auth_code)

        // Setup web view
        val webView = findViewById<WebView>(R.id.sf__webview)
        val webSettings = webView.settings
        webSettings.useWideViewPort = true
        webSettings.layoutAlgorithm = LayoutAlgorithm.NORMAL

        (SalesforceSDKManager.getInstance().idpManager as? IDPManager)?.let { idpManager ->
            val spAppPackageName = idpManager.getSrcAppPackageName(intent)
            idpManager.attachToActiveFlow(this, this, spAppPackageName)
            // Handing intent to IDPManager
            idpManager.onReceive(this@IDPAuthCodeActivity, intent)
        } ?: run {
            SalesforceSDKLogger.d(this::class.java.simpleName, "no idp manager to handle ${LogUtil.intentToString(intent)}")
        }
    }

    override val webView : WebView  get() = findViewById(R.id.sf__webview)
}