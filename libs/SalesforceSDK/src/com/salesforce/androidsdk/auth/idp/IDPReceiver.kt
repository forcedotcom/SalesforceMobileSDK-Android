package com.salesforce.androidsdk.auth.idp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.util.LogUtil
import com.salesforce.androidsdk.util.SalesforceSDKLogger


/**
 * Receiver running in IDP app handling calls from SP app
 */
class IDPReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SalesforceSDKManager.getInstance().idpManager?.let { idpManager ->
            idpManager.onReceive(context, intent)
        } ?: run {
            SalesforceSDKLogger.d(this::class.java.simpleName, "onReceive no idp manager to handle ${LogUtil.intentToString(intent)}")
        }
    }
}