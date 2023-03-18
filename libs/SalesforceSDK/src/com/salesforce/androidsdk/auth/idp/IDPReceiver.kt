package com.salesforce.androidsdk.auth.idp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.webkit.WebView
import com.salesforce.androidsdk.auth.idp.IDPAuthCodeHelper.Callback
import com.salesforce.androidsdk.util.LogUtil
import com.salesforce.androidsdk.util.SalesforceSDKLogger

/**
 * Receiver running in IDP app handling calls from SP app
 */
class IDPReceiver : BroadcastReceiver() {

    companion object {
        const val SP_LOGIN_REQUEST_ACTION = "com.salesforce.SP_LOGIN_REQUEST"
        const val SP_CONFIG_BUNDLE_KEY = "sp_config_bundle"

        val TAG = IDPReceiver::class.java.simpleName

        @JvmStatic
        fun sendLoginRequestToIDP(
            context: Context,
            idpAppPackageName: String,
            spConfig: SPConfig
        ) {
            val intent = Intent(SP_LOGIN_REQUEST_ACTION)
            intent.setPackage(idpAppPackageName)
            intent.putExtra(SP_CONFIG_BUNDLE_KEY, spConfig.toBundle())
            SalesforceSDKLogger.d(TAG, "sendLoginRequestToIDP " + LogUtil.intentToString(intent))
            context.sendBroadcast(intent)
        }
    }

    fun handleLoginRequestFromSP(context:Context, spAppPackageName: String, spConfig: SPConfig) {
        val userHinted = spConfig.userHinted

        if (userHinted != null) {
            IDPAuthCodeHelper.generateAuthCode(
                WebView(context),
                userHinted,
                spConfig,
                object : Callback {
                    override fun onResult(result: IDPAuthCodeHelper.Result) {
                        SPReceiver.sendLoginResponseToSP(context, spAppPackageName, result)
                    }
                })
        } else {
//            val launchIntent = Intent(context, IDPAccountPickerActivity::class.java)
//            launchIntent.addCategory(Intent.CATEGORY_DEFAULT)
//            launchIntent.putExtra(
//                SP_CONFIG_BUNDLE_KEY,
//                intent.getBundleExtra(SP_CONFIG_BUNDLE_KEY)
//            )
//            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//            SalesforceSDKLogger.d(
//                TAG,
//                "onReceive startActivity " + LogUtil.intentToString(launchIntent)
//            )
//            context.startActivity(launchIntent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        SalesforceSDKLogger.d(TAG, "onReceive ${LogUtil.intentToString(intent)}")
        when (intent.action) {
            SP_LOGIN_REQUEST_ACTION -> {
                val spAppPackageName = intent.`package`
                val spConfig = SPConfig.fromBundle(intent.getBundleExtra(SP_CONFIG_BUNDLE_KEY))
                if (spAppPackageName != null && spConfig != null) {
                    handleLoginRequestFromSP(context, spAppPackageName, spConfig)
                }
            }
        }
    }
}