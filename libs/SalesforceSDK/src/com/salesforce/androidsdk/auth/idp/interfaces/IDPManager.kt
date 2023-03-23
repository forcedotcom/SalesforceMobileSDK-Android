package com.salesforce.androidsdk.auth.idp.interfaces

import android.content.Context
import android.content.Intent

interface IDPManager {
    /**
     * Process received intent
     */
    fun onReceive(context: Context, intent: Intent)

    /**
     * Kick off IDP initiated login flow for given SP app
     */
    fun kickOffIDPInitiatedLoginFlow(context: Context, spAppPackageName: String)
}