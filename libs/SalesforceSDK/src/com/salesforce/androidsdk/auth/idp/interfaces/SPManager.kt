package com.salesforce.androidsdk.auth.idp.interfaces

import android.content.Context
import android.content.Intent

interface SPManager {
    /**
     * Process received intent
     */
    fun onReceive(context: Context, intent: Intent)

    /**
     * Kick off SP initiated login flow
     */
    fun kickOffSPInitiatedLoginFlow(context: Context)
}
