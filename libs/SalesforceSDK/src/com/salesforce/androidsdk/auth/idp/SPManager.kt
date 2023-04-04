/*
 * Copyright (c) 2023-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
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
 * Class to capture state related to SP login flow
 * e.g. the context that started it
 *      - could be an activity in the case of a SP initiated login
 *      - or the receiver context in the case it was started in response to a IDP login request)
 *      the code verifier
 */
internal class SPLoginFlow private constructor(context:Context, val codeVerifier:String, val onStatusUpdate: (Status) -> Unit)
    : ActiveFlow(context) {

        companion object {
            val TAG = SPLoginFlow::class.java.simpleName

            fun kickOff(spManager:SPManager, context: Context, onStatusUpdate: (Status) -> Unit = { _ -> }, idpLoginRequest:IDPLoginRequest? = null): SPLoginFlow {
                val trigger = if (idpLoginRequest == null) "" else " triggered by ${idpLoginRequest} "
                SalesforceSDKLogger.d(TAG, "Kicking off sp initiated login flow from ${context}${trigger}")

                val codeVerifier = SalesforceKeyGenerator.getRandom128ByteKey()
                val codeChallenge = SalesforceKeyGenerator.getSHA256Hash(codeVerifier)
                val activeFlow = SPLoginFlow(context, codeVerifier, onStatusUpdate)

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
    val idpAppPackageName: String,
    val sdkMgr: SDKManager,
    sendBroadcast: (context:Context, intent:Intent) -> Unit
): IDPSPManager(sendBroadcast), com.salesforce.androidsdk.auth.idp.interfaces.SPManager {

    /**
     * Interface to keep SPManager decoupled from other managers
     */
    internal interface SDKManager {
        fun getCurrentUser(): UserAccount?
        fun getUserFromOrgAndUserId(orgId:String, userId:String): UserAccount?
        fun switchToUser(user: UserAccount)
        fun getMainActivityClass(): Class<out Activity>?
    }
    companion object {
        private val TAG = SPManager::class.java.simpleName
    }

    /**
     * Secondary constructor (called from java)
     */
    constructor(idpAppPackageName: String, sdkMgr: SDKManager) : this(
        idpAppPackageName,
        sdkMgr,
        { context, intent -> context.sendBroadcast(intent) }
    )

    /**
     * Current active login flow if any
     */
    private var activeFlow: SPLoginFlow? = null

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
        val user = sdkMgr.getUserFromOrgAndUserId(
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
        sdkMgr.switchToUser(user)

        // We have an activity context - launch main activity from here
        if (context is Activity) {
            val launchIntent = Intent(context, sdkMgr.getMainActivityClass())
            SalesforceSDKLogger.d(TAG, "start activity ${LogUtil.intentToString(launchIntent)}")
            context.startActivity(launchIntent)
        }

        // Otherwise sends a response back to IDP app to have it launch the activity
        else if (message != null) {
            send(context, IDPLoginResponse(message.uuid))
        }
    }

    /**
     * Handle request to login coming from IDP app - when user hinted is not available in SP app
     * We kick off a SP initiated login flow using the same uuid as the original IDP login request
     */
    fun handleNoUser(context: Context, message: IDPLoginRequest) {
        SalesforceSDKLogger.d(TAG, "handleNoUser $message")
        activeFlow = SPLoginFlow.kickOff(this, context, idpLoginRequest = message)
    }

    /**
     * Handle response to login request initiated from SP app
     * It will contain either an auth code that we need to exchange for auth tokens
     * or an error if the auth code could not be obtained from the server
     */
    fun handleLoginResponse(activeFlow: SPLoginFlow, message: SPLoginResponse) {
        SalesforceSDKLogger.d(TAG, "handleLoginResponse $message")
        if (message.error != null) {
            activeFlow.onStatusUpdate(Status.ERROR_RESPONSE_RECEIVED_FROM_IDP)
        } else if (message.loginUrl != null && message.code != null) {
            activeFlow.onStatusUpdate(Status.AUTH_CODE_RECEIVED_FROM_IDP)
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
    override fun kickOffSPInitiatedLoginFlow(context: Context, callback: StatusUpdateCallback) {
        activeFlow = SPLoginFlow.kickOff(this, context, callback::onStatusUpdate)
    }
}