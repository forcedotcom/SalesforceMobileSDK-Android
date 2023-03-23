package com.salesforce.androidsdk.auth.idp.interfaces

import android.content.Context
import android.content.Intent
import android.net.wifi.EasyConnectStatusCallback

interface SPManager {

    enum class Status {
        LOGIN_REQUEST_SENT_TO_IDP,
        SUCCESS_RESPONSE_RECEIVED_FROM_IDP,
        ERROR_RESPONSE_RECEIVED_FROM_IDP,
        LOGIN_COMPLETE
    }
    interface StatusUpdateCallback {
        fun onStatusUpdate(status: Status)
    }

    /**
     * Process received intent
     */
    fun onReceive(context: Context, intent: Intent)

    /**
     * Kick off SP initiated login flow
     */
    fun kickOffSPInitiatedLoginFlow(context: Context, callback: StatusUpdateCallback)
}
