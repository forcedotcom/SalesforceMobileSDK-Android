package com.salesforce.androidsdk.auth.idp.interfaces

import android.content.Context
import android.content.Intent

interface IDPManager {

    enum class Status {
        LOGIN_REQUEST_SENT_TO_SP,
        SP_READY,
        // TODO more statuses
    }
    interface StatusUpdateCallback {
        fun onStatusUpdate(status: Status)
    }


    /**
     * Process received intent
     */
    fun onReceive(context: Context, intent: Intent)

    /**
     * Kick off IDP initiated login flow for given SP app
     */
    fun kickOffIDPInitiatedLoginFlow(context: Context, spAppPackageName: String, callback: StatusUpdateCallback)
}