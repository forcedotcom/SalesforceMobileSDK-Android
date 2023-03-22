package com.salesforce.androidsdk.auth.idp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.webkit.WebView
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.util.LogUtil
import com.salesforce.androidsdk.util.SalesforceSDKLogger

/**
 * Class handling IDP operations within an IDP app
 */
class IDPManager(
    val allowedSPApps: List<SPConfig>
) {

    companion object {
        val TAG = IDPManager::class.java.simpleName
    }

    val sdkMgr: SalesforceSDKManager
        get() = SalesforceSDKManager.getInstance()

    /**
     * Return SP app config given its package
     * Null is returned if it is not one of the allowed SP apps
     */
    fun getSPConfig(spAppPackageName: String): SPConfig? {
        return allowedSPApps.find { it.spAppPackageName == spAppPackageName }
    }

    /**
     * Return true if given SP app is allowed
     */
    fun isAllowed(spAppPackageName: String?): Boolean {
        return spAppPackageName != null && getSPConfig(spAppPackageName) != null
    }

    /**
     * Sends message to destination app
     */
    fun send(context: Context, message: IDPSPMessage, spAppPackageName: String) {
        if (!isAllowed(spAppPackageName)) {
            SalesforceSDKLogger.w(TAG, "Not allowed to send message to ${spAppPackageName}")
            return
        }
        val intent = message.toIntent()
        intent.setPackage(spAppPackageName)
        SalesforceSDKLogger.d(TAG, "send ${LogUtil.intentToString(intent)}")
        context.sendBroadcast(intent)
    }

    /**
     * Handle message received
     */
    fun handle(context: Context, message: IDPSPMessage) {
        when (message) {
            is IDPSPMessage.IDPLoginResponse -> {
                handleIDPLoginResponse(context, message)
            }

            is IDPSPMessage.SPLoginRequest -> {
                handleSPLoginRequest(context, message)
            }
        }
    }

    /**
     * Handle case where SP already has user hinted in IDP initiated login request
     */
    fun handleIDPLoginResponse(context:Context, message:IDPSPMessage.IDPLoginResponse) {
    }

    /**
     * Handle request to login coming from SP app
     * We
     */
    fun handleSPLoginRequest(context: Context, message: IDPSPMessage.SPLoginRequest) {
//        IDPAuthCodeHelper.generateAuthCode(
//            WebView(context),
//            userHinted,
//            spConfig,
//            object : IDPAuthCodeHelper.Callback {
//                override fun onResult(result: IDPAuthCodeHelper.Result) {
//                    IDPReceiver.sendLoginResponseToSP(
//                        context,
//                        spConfig.spAppPackageName,
//                        result
//                    )
//                }
//            })
//
    }
}