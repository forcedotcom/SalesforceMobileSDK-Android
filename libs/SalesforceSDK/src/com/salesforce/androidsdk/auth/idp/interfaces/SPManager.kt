package com.salesforce.androidsdk.auth.idp.interfaces

import android.content.Context
import android.content.Intent
import com.salesforce.androidsdk.R

interface SPManager {

    enum class Status(val resIdForDescription:Int) {
        LOGIN_REQUEST_SENT_TO_IDP(R.string.sf__login_request_sent_to_idp),
        AUTH_CODE_RECEIVED_FROM_IDP(R.string.sf__auth_code_received_from_idp),
        ERROR_RESPONSE_RECEIVED_FROM_IDP(R.string.sf__error_received_from_idp),
        LOGIN_COMPLETE(R.string.sf__login_complete)
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
