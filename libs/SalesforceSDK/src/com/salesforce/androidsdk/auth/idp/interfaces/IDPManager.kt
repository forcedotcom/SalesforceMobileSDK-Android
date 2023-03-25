package com.salesforce.androidsdk.auth.idp.interfaces

import android.content.Context
import android.content.Intent
import com.salesforce.androidsdk.R

interface IDPManager {

    enum class Status(val resIdForDescription:Int) {
        LOGIN_REQUEST_SENT_TO_SP(R.string.sf__login_request_sent_to_sp),
        GETTING_AUTH_CODE_FROM_SERVER(R.string.sf__getting_auth_code_from_server),
        ERROR_RECEIVED_FROM_SERVER(R.string.sf__error_received_from_server),
        AUTH_CODE_SENT_TO_SP(R.string.sf__auth_code_sent_to_sp),
        SP_READY(R.string.sf__sp_ready)
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