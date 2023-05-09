package com.salesforce.androidsdk.auth.idp

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager.LayoutParams
import android.webkit.WebSettings.LayoutAlgorithm
import android.webkit.WebView
import com.salesforce.androidsdk.R
import com.salesforce.androidsdk.app.SalesforceSDKManager

/**
 * Activity showing web view through which IDP gets auth code for SP app
 */
class IDPAuthCodeActivity : Activity(), com.salesforce.androidsdk.auth.idp.interfaces.IDPAuthCodeActivity {
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

        SalesforceSDKManager.getInstance().idpManager?.let {
            it.onReceive(this@IDPAuthCodeActivity, intent)
        }
    }

    override fun getWebView(): WebView {
        return findViewById(R.id.sf__webview) as WebView
    }
}