package com.salesforce.androidsdk.auth.idp

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.auth.idp.IDPSPMessage.*
import com.salesforce.androidsdk.auth.idp.interfaces.SPManager.Status
import com.salesforce.androidsdk.auth.idp.interfaces.SPManager.StatusUpdateCallback
import com.salesforce.androidsdk.security.SalesforceKeyGenerator
import com.salesforce.androidsdk.util.LogUtil
import com.salesforce.androidsdk.util.SalesforceSDKLogger

/**
 * Class to capture state related to SP initiated login flow
 * e.g. the context (activity) that started it
 *      the code verifier
 *      the idp login request that trigged it (if applicable)
 */
internal class SPInitiatedLoginFlow private constructor(context:Context, val codeVerifier:String, val onStatusUpdate: (Status) -> Unit)
    : ActiveFlow(context) {

        companion object {
            val TAG = SPInitiatedLoginFlow::class.java.simpleName

            fun kickOff(spManager:SPManager, context: Context, onStatusUpdate: (Status) -> Unit = { _ -> }, idpLoginRequest:IDPLoginRequest? = null): SPInitiatedLoginFlow {
                val trigger = if (idpLoginRequest == null) "" else " triggered by ${idpLoginRequest} "
                SalesforceSDKLogger.d(TAG, "Kicking off sp initiated login flow from ${context}${trigger}")

                val codeVerifier = SalesforceKeyGenerator.getRandom128ByteKey()
                val codeChallenge = SalesforceKeyGenerator.getSHA256Hash(codeVerifier)
                val activeFlow = SPInitiatedLoginFlow(context, codeVerifier, onStatusUpdate)

                val spLoginRequest = if (idpLoginRequest != null) {
                    activeFlow.addMessage(idpLoginRequest)
                    SPLoginRequest(idpLoginRequest.uuid, codeChallenge = codeChallenge).also {
                        activeFlow.addMessage(it)
                    }
                } else {
                    SPLoginRequest(codeChallenge = codeChallenge).also {
                        activeFlow.addMessage(it)
                    }
                }

                // Send SP login request
                spManager.send(context, spLoginRequest)
                onStatusUpdate(Status.LOGIN_REQUEST_SENT_TO_IDP)
                return activeFlow
            }
        }
}

/**
 * Class handling SP operations within a SP app
 */
internal class SPManager(
    val idpAppPackageName: String
): IDPSPManager(), com.salesforce.androidsdk.auth.idp.interfaces.SPManager {

    companion object {
        private val TAG = SPManager::class.java.simpleName
    }

    /**
     * Current active login flow if any
     */
    private var activeFlow: SPInitiatedLoginFlow? = null

    override fun getActiveFlow(): ActiveFlow? {
        return activeFlow
    }

    override fun endActiveFlow() {
        activeFlow = null
    }

    /**
     * Return true if given IDP app is allowed
     */
    override fun isAllowed(srcAppPackageName: String): Boolean {
        return this.idpAppPackageName == srcAppPackageName
    }

    /**
     * Sends message to idp app
     */
    fun send(context: Context, message: IDPSPMessage) {
        send(context, message, idpAppPackageName)
    }

    /**
     * Handle message received
     */
    override fun handle(context: Context, message: IDPSPMessage, srcAppPackageName: String) {
        SalesforceSDKLogger.d(TAG, "handle $message")

        when (message) {
            is IDPLoginRequest -> {
                handleLoginRequest(context, message)
            }

            is SPLoginResponse -> {
                // Handle only if there is an active flow and the message is part of it
                if (activeFlow?.isPartOfFlow(message) == true) {
                    activeFlow?.let { activeFlow -> handleLoginResponse(activeFlow, message) }
                } else {
                    SalesforceSDKLogger.d(TAG, "no sp iniated login flow started - cannot handle $message")
                }
            }

            else -> {
                SalesforceSDKLogger.d(TAG, "cannot handle $message")
            }
        }
    }

    /**
     * Handle request to login coming from IDP app - it contains a org and user id
     * - If the user specified is available in SP app we just switch to it
     * - If the user specified is not available in SP app, we send a login request to IDP app
     */
    fun handleLoginRequest(context: Context, message: IDPLoginRequest) {
        SalesforceSDKLogger.d(TAG, "handleIDPLoginRequest $message")
        val user = sdkMgr.userAccountManager.getUserFromOrgAndUserId(
            message.orgId,
            message.userId
        )
        // We have the user already - switch to it
        if (user != null) {
            handleUserExists(context, message, user)
        }
        // We don't have the user already - send login request to IDP
        else {
            handleNoUser(context, message)
        }
    }

    /**
     * Handle the case when we have the user either from the start or following a login flow
     * - we switch to the user
     * - we launch the main activity if our context allows it
     * - we send a IDPLoginResponse if we got here because of a IDPLoginRequest
     *
     */
    fun handleUserExists(context: Context, message: IDPLoginRequest?, user: UserAccount) {
        SalesforceSDKLogger.d(TAG, "handleUserExists $message")
        sdkMgr.userAccountManager.switchToUser(user)

        // We have an activity context - launch main activity from here
        if (context is Activity) {
            val launchIntent = Intent(context, sdkMgr.mainActivityClass)
            SalesforceSDKLogger.d(TAG, "start activity ${LogUtil.intentToString(launchIntent)}")
            context.startActivity(launchIntent)
        }

        // Otherwise sends a response back to IDP app to have it launch the activity
        else if (message != null) {
            val responseMessage = IDPLoginResponse(message.uuid)
            send(context, responseMessage)
        }
    }

    /**
     * Handle request to login coming from IDP app - when user hinted is not available in SP app
     * We kick off a SP initiated login flow using the same uuid as the original IDP login request
     */
    fun handleNoUser(context: Context, message: IDPLoginRequest) {
        SalesforceSDKLogger.d(TAG, "handleNoUser $message")
        activeFlow = SPInitiatedLoginFlow.kickOff(this, context, idpLoginRequest = message)
    }

    /**
     * Handle response to login request initiated from SP app
     * It will contain either an auth code that we need to exchange for auth tokens
     * or an error if the auth code could not be obtained from the server
     */
    fun handleLoginResponse(activeFlow: SPInitiatedLoginFlow, message: SPLoginResponse) {
        SalesforceSDKLogger.d(TAG, "handleLoginResponse $message")
        if (message.error != null) {
            activeFlow.onStatusUpdate(Status.ERROR_RESPONSE_RECEIVED_FROM_IDP)
        } else if (message.loginUrl != null && message.code != null) {
            activeFlow.onStatusUpdate(Status.SUCCESS_RESPONSE_RECEIVED_FROM_IDP)
            SPAuthCodeHelper.loginWithAuthCode(activeFlow.context, message.loginUrl,
                message.code,
                activeFlow.codeVerifier
            ) { user ->
                with(activeFlow) {
                    activeFlow.onStatusUpdate(Status.LOGIN_COMPLETE)
                    handleUserExists(
                        context,
                        if (firstMessage is IDPLoginRequest) firstMessage as IDPLoginRequest else null,
                        user
                    )
                }
            }
        }
    }

    /**
     * Kick off SP initiated login flow
     */
    override fun kickOffSPInitiatedLoginFlow(context: Context, statusUpdateCallback: StatusUpdateCallback) {
        activeFlow = SPInitiatedLoginFlow.kickOff(this, context, statusUpdateCallback::onStatusUpdate)
    }
}