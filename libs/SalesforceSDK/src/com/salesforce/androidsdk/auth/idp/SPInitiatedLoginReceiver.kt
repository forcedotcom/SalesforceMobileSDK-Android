package com.salesforce.androidsdk.auth.idp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.webkit.WebView
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.idp.IDPCodeGeneratorHelper.CodeGeneratorCallback
import com.salesforce.androidsdk.util.LogUtil
import com.salesforce.androidsdk.util.SalesforceSDKLogger

class SPInitiatedLoginReceiver : BroadcastReceiver() {

    companion object {
        const val SP_LOGIN_REQUEST_ACTION = "com.salesforce.SP_LOGIN_REQUEST"
        const val SP_CONFIG_BUNDLE_KEY = "sp_config_bundle"

        val TAG = SPInitiatedLoginReceiver::class.java.simpleName


        @JvmStatic
        fun sendLoginRequest(
            context: Context,
            idpAppPackageName: String,
            spConfig: SPConfig
        ) {
            val intent = Intent(SP_LOGIN_REQUEST_ACTION)
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            intent.setPackage(idpAppPackageName)
            intent.putExtra(SP_CONFIG_BUNDLE_KEY, spConfig.toBundle())
            SalesforceSDKLogger.d(TAG, "sendLoginRequest " + LogUtil.intentToString(intent))
            context.sendBroadcast(intent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        SalesforceSDKLogger.d(TAG, "onReceive ${LogUtil.intentToString(intent)}")
        if (SP_LOGIN_REQUEST_ACTION.equals(intent.action)) {
            val spConfig = SPConfig(intent.getBundleExtra(SP_CONFIG_BUNDLE_KEY))
            val userHinted = spConfig.userHinted

            if (userHinted != null) {
                val codeGenerator = IDPCodeGeneratorHelper(
                    WebView(context),
                    userHinted,
                    SPConfig(intent.getBundleExtra(SP_CONFIG_BUNDLE_KEY)),
                    object : CodeGeneratorCallback {
                        override fun onResult(resultCode: Int, data: Intent) {
                            // TBD
                        }
                    })
            } else {
                val launchIntent = Intent(context, IDPAccountPickerActivity::class.java)
                launchIntent.addCategory(Intent.CATEGORY_DEFAULT)
                launchIntent.putExtra(
                    SP_CONFIG_BUNDLE_KEY,
                    intent.getBundleExtra(SP_CONFIG_BUNDLE_KEY)
                )
                launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                SalesforceSDKLogger.d(
                    TAG,
                    "onReceive startActivity " + LogUtil.intentToString(launchIntent)
                )
                context.startActivity(launchIntent)
            }
        }
    }
}