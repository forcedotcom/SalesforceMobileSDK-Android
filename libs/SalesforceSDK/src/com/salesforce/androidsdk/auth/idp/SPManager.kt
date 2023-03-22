package com.salesforce.androidsdk.auth.idp

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.util.LogUtil
import com.salesforce.androidsdk.util.SalesforceSDKLogger

/**
 * Class handling SP operations within a SP app
 */
class SPManager(
    val idpAppPackageName: String
) {
    companion object {
        val TAG = SPManager::class.java.simpleName
    }

    val sdkMgr: SalesforceSDKManager
        get() = SalesforceSDKManager.getInstance()

    /**
     * Return true if given IDP app is allowed
     */
    fun isAllowed(idpAppPackageName: String?): Boolean {
        return this.idpAppPackageName == idpAppPackageName
    }

    /**
     * Sends message to idp app
     */
    internal fun send(context: Context, message: IDPSPMessage) {
        val intent = message.toIntent()
        intent.setPackage(idpAppPackageName)
        SalesforceSDKLogger.d(TAG, "send ${LogUtil.intentToString(intent)}")
        context.sendBroadcast(intent)
    }

    /**
     * Handle message received
     */
    fun handle(context: Context, message: IDPSPMessage) {
        when (message) {
            is IDPSPMessage.IDPLoginRequest -> {
                handleIDPLoginRequest(context, message)
            }

            is IDPSPMessage.SPLoginResponse -> {
                handleSPLoginResponse(context, message)
            }
        }
    }

    /**
     * Handle request to login coming from IDP app - it contains a org and user id
     * - If the user specified is available in SP app we just switch to it
     * - If the user specified is not available in SP app, we send a login request to IDP app
     */
    fun handleIDPLoginRequest(context: Context, message: IDPSPMessage.IDPLoginRequest) {
        val user = sdkMgr.userAccountManager.getUserFromOrgAndUserId(
            message.orgId,
            message.userId
        )
        // We have the user already - switch to it
        if (user != null) {
            handleIDPLoginRequestSPAlreadyLoggedIn(context, message, user)
        }
        // We don't have the user already - send login request to IDP
        else {
            handleIDPLoginRequestSPNotLoggedIn(context, message)
        }
    }

    /**
     * Handle request to login coming from IDP app - when user hinted is already available in SP app
     * - We switch to the user
     * - if the flow was started from SP app (and context is an activity), we launch main activity
     * - if the flow was started from IDP app, we send a response to it so that it can launch our main activity
     */
    fun handleIDPLoginRequestSPAlreadyLoggedIn(context: Context, message: IDPSPMessage.IDPLoginRequest, user: UserAccount) {
        sdkMgr.userAccountManager.switchToUser(user)

        // We have an activity context - launch main activity from here
        if (context is Activity) {
            val launchIntent = Intent(context, sdkMgr.mainActivityClass)
            SalesforceSDKLogger.d(
                TAG,
                "handleIDPLoginRequestSPAlreadyLoggedIn start activity " + LogUtil.intentToString(
                    launchIntent
                )
            )
            context.startActivity(launchIntent)
        }

        // Otherwise sends a response back to IDP app to have it launch the activity
        else {
            val responseMessage = IDPSPMessage.IDPLoginResponse(message.uuid)
            send(context, responseMessage)
        }
    }

    /**
     * Handle request to login coming from IDP app - when user hinted is not available in SP app
     * We send a login request to IDP app (contains a code challenge)
     */
    fun handleIDPLoginRequestSPNotLoggedIn(context: Context, message: IDPSPMessage.IDPLoginRequest) {
//        handleIDPLoginRequestSPNotLoggedIn(context, message)
//        val loginUrl = sdkMgr.loginServerManager.selectedLoginServer.url.trim()
//        val spRequestIntent = IDPSPMessage.SPLoginRequest(message.uuid, loginUrl = loginUrl)
//        send(context, spRequestIntent)
    }

    /**
     * Handle ....
     */
    fun handleSPLoginResponse(context: Context, message:IDPSPMessage.SPLoginResponse) {

    }

}