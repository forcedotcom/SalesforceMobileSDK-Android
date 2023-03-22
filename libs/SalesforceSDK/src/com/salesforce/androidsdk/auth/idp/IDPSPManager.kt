package com.salesforce.androidsdk.auth.idp

import android.content.Context
import android.content.Intent
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.util.LogUtil
import com.salesforce.androidsdk.util.SalesforceSDKLogger

internal abstract class IDPSPManager() {
    companion object {
        private const val SRC_APP_PACKAGE_NAME_KEY = "src_app_package_name"
    }

    val sdkMgr: SalesforceSDKManager
        get() = SalesforceSDKManager.getInstance()

    /**
     * Return true if messages from srcAppPackageName are allowed
     */
    abstract fun isAllowed(srcAppPackageName: String): Boolean

    /**
     * Handle given message
     */
    abstract fun handle(context: Context, message: IDPSPMessage, srcAppPackageName: String)

    /**
     * Sends message
     */
    internal fun send(context: Context, message: IDPSPMessage, destinationAppPackageName: String) {
        val intent = message.toIntent().apply {
            putExtra(SRC_APP_PACKAGE_NAME_KEY, context.applicationInfo.packageName)
            setPackage(destinationAppPackageName)
        }
        SalesforceSDKLogger.d(this::class.java.simpleName, "send ${LogUtil.intentToString(intent)}")
        context.sendBroadcast(intent)
    }

    /**
     * Receive message
     */
    internal fun onReceive(context: Context, intent: Intent) {
        SalesforceSDKLogger.d(this::class.java.simpleName, "onReceive ${LogUtil.intentToString(intent)}")
        intent.getStringExtra(SRC_APP_PACKAGE_NAME_KEY)?.let { srcAppPackageName ->
            if (!isAllowed(srcAppPackageName)) {
                SalesforceSDKLogger.w(this::class.java.simpleName, "onReceive not allowed to handle ${LogUtil.intentToString(intent)}")
            } else {
                IDPSPMessage.fromIntent(intent)?.let { message ->
                    handle(context, message, srcAppPackageName)
                } ?: run {
                    SalesforceSDKLogger.w(this::class.java.simpleName, "onReceive could not parse ${LogUtil.intentToString(intent)}")
                }
            }
        }
    }
}