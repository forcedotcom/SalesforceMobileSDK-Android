package com.salesforce.androidsdk.auth.idp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.idp.IDPAuthCodeHelper.Callback
import com.salesforce.androidsdk.util.LogUtil
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import java.util.*


/**
 * Receiver running in IDP app handling calls from SP app
 */
class IDPReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SalesforceSDKLogger.d(TAG, "onReceive ${LogUtil.intentToString(intent)}")
        val idpManager = SalesforceSDKManager.getInstance().idpManager
        if (idpManager != null) {
            if (idpManager.isAllowed(intent.`package`)) {
                IDPSPMessage.fromIntent(intent)?.let { message ->
                    idpManager.handle(context, message)
                }
            } else {
                SalesforceSDKLogger.w(TAG, "Not allowed to handle message from ${intent.`package`}")
            }
        } else {
            SalesforceSDKLogger.w(TAG, "No idp manager to message")
        }
    }

    companion object {
        val TAG = IDPReceiver::class.java.simpleName
    }
//
//    companion object {
//        const val SP_LOGIN_REQUEST_ACTION = "com.salesforce.SP_LOGIN_REQUEST"
//        const val SP_LOGIN_RESPONSE_ACTION = "com.salesforce.SP_LOGIN_RESPONSE"
//        const val SP_CONFIG_BUNDLE_KEY = "sp_config_bundle"
//
//        val TAG: String = IDPReceiver::class.java.simpleName
//
//        fun sendLoginRequestToSP(
//            context: Context,
//            spAppPackageName: String,
//            spAppComponentName: String,
//            currentUser: UserAccount?,
//            spActivityExtras: Bundle?
//        ) {
//            val intent = Intent(SPReceiver.IDP_LOGIN_REQUEST)
//            intent.setPackage(spAppPackageName)
//            intent.putExtra(SPReceiver.SP_ACTVITY_NAME_KEY, "$spAppPackageName.$spAppComponentName")
//            intent.putExtra(SPReceiver.USER_HINT_KEY, SPConfig.userToHint(currentUser))
//            if (spActivityExtras != null) intent.putExtras(spActivityExtras)
//            SalesforceSDKLogger.d(TAG, "sendLoginRequestToSP ${LogUtil.intentToString(intent)}")
//            context.sendBroadcast(intent)
//        }
//
//        internal fun sendLoginResponseToSP(context: Context, spAppPackageName: String, result: IDPAuthCodeHelper.Result) {
//            val intent = Intent(SPReceiver.IDP_LOGIN_RESPONSE)
//            intent.setPackage(spAppPackageName)
//            intent.putExtras(result.toBundle())
//            SalesforceSDKLogger.d(TAG, "sendLoginResponseToSP ${LogUtil.intentToString(intent)}")
//            context.sendBroadcast(intent)
//        }
//    }
//
//    fun handleLoginRequestFromSP(context:Context, spConfig: SPConfig) {
//        val userHinted = spConfig.userHinted
//
//        if (userHinted != null) {
//            IDPAuthCodeHelper.generateAuthCode(
//                WebView(context),
//                userHinted,
//                spConfig,
//                object : Callback {
//                    override fun onResult(result: IDPAuthCodeHelper.Result) {
//                        sendLoginResponseToSP(context, spConfig.spAppPackageName, result)
//                    }
//                })
//        } else {
////            val launchIntent = Intent(context, IDPAccountPickerActivity::class.java)
////            launchIntent.addCategory(Intent.CATEGORY_DEFAULT)
////            launchIntent.putExtra(
////                SP_CONFIG_BUNDLE_KEY,
////                intent.getBundleExtra(SP_CONFIG_BUNDLE_KEY)
////            )
////            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
////            SalesforceSDKLogger.d(
////                TAG,
////                "onReceive startActivity " + LogUtil.intentToString(launchIntent)
////            )
////            context.startActivity(launchIntent)
//        }
//    }
//
//    override fun onReceive(context: Context, intent: Intent) {
//        SalesforceSDKLogger.d(TAG, "onReceive ${LogUtil.intentToString(intent)}")
//        when (intent.action) {
//            SP_LOGIN_REQUEST_ACTION -> {
//                val spConfig = SPConfig.fromBundle(intent.getBundleExtra(SP_CONFIG_BUNDLE_KEY))
//                if (spConfig != null) {
//                    handleLoginRequestFromSP(context, spConfig)
//                }
//            }
//            SP_LOGIN_RESPONSE_ACTION -> {
//                context.sendBroadcast(Intent("sp_app_logged_in").apply { `package` = context.packageName})
//            }
//        }
//    }
}