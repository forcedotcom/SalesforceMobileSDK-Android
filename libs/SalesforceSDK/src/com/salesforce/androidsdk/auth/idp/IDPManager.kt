package com.salesforce.androidsdk.auth.idp

import android.content.Context
import android.content.Intent
import android.webkit.WebView
import com.salesforce.androidsdk.util.LogUtil
import com.salesforce.androidsdk.util.SalesforceSDKLogger

/**
 * Class handling IDP operations within an IDP app
 */
internal class IDPManager(
    val allowedSPApps: List<SPConfig>
): IDPSPManager() {

    companion object {
        private val TAG = IDPManager::class.java.simpleName
    }

    /**
     * Return SP app config given its package
     * Null is returned if it is not one of the allowed SP apps
     */
    fun getSPConfig(spAppPackageName: String): SPConfig? {
        return allowedSPApps.find { it.appPackageName == spAppPackageName }
    }

    /**
     * Return true if given SP app is allowed
     */
    override fun isAllowed(spAppPackageName: String): Boolean {
        return getSPConfig(spAppPackageName) != null
    }

    /**
     * Handle message received
     */
    override fun handle(context: Context, message: IDPSPMessage, srcAppPackageName: String) {
        SalesforceSDKLogger.d(TAG, "handle $message")
        getSPConfig(srcAppPackageName)?.let {spConfig ->
            when (message) {
                is IDPSPMessage.IDPLoginResponse -> {
                    handleIDPLoginResponse(context, message, spConfig)
                }

                is IDPSPMessage.SPLoginRequest -> {
                    handleSPLoginRequest(context, message, spConfig)
                }

                else -> {
                    SalesforceSDKLogger.d(TAG, "cannot handle $message")
                }
            }
        }
    }

    /**
     * Handle case where SP already has user hinted in IDP initiated login request
     * but it is unable to launch activity itself
     */
    fun handleIDPLoginResponse(context:Context, message:IDPSPMessage.IDPLoginResponse, spConfig: SPConfig) {
        SalesforceSDKLogger.d(TAG, "handleIDPLoginResponse $message")
        val launchIntent = Intent(Intent.ACTION_VIEW).apply {
            setPackage(spConfig.appPackageName)
            setClassName(spConfig.appPackageName, spConfig.componentName)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        SalesforceSDKLogger.d(TAG, "handleIDPLoginResponse startActivity ${LogUtil.intentToString(launchIntent)}")
        context.startActivity(launchIntent)
    }

    /**
     * Handle request to login coming from SP app
     * We get an auth code from the server and return it to the SP app or an error if that failed
     */
    fun handleSPLoginRequest(context: Context, message: IDPSPMessage.SPLoginRequest, spConfig: SPConfig) {
        SalesforceSDKLogger.d(TAG, "handleSPLoginRequest $message")
        sdkMgr.userAccountManager.currentUser?.let {currentUser ->
            // Get auth code for current user
            IDPAuthCodeHelper.generateAuthCode(
                WebView(context),
                currentUser,
                spConfig,
                "",
                object : IDPAuthCodeHelper.Callback {
                    override fun onResult(result: IDPAuthCodeHelper.Result) {
                        val spLoginResponse = IDPSPMessage.SPLoginResponse(code = result.code, loginUrl = result.loginUrl)
                        send(context, spLoginResponse, spConfig.appPackageName)
                    }
                })

        } ?: run {
            val spLoginResponse = IDPSPMessage.SPLoginResponse(error = "IDP app not logged in")
            send(context, spLoginResponse, spConfig.appPackageName)
        }
    }
}