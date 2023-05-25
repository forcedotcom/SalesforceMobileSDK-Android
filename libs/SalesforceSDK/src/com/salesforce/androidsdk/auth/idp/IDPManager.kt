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

import android.content.Context
import android.content.Intent
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.idp.IDPSPMessage.Companion.ACTION_KEY
import com.salesforce.androidsdk.auth.idp.IDPSPMessage.IDPToSPRequest
import com.salesforce.androidsdk.auth.idp.IDPSPMessage.IDPToSPResponse
import com.salesforce.androidsdk.auth.idp.IDPSPMessage.SPToIDPRequest
import com.salesforce.androidsdk.auth.idp.IDPSPMessage.SPToIDPResponse
import com.salesforce.androidsdk.auth.idp.interfaces.IDPAuthCodeActivity
import com.salesforce.androidsdk.auth.idp.interfaces.IDPManager.Status
import com.salesforce.androidsdk.auth.idp.interfaces.IDPManager.StatusUpdateCallback
import com.salesforce.androidsdk.util.LogUtil
import com.salesforce.androidsdk.util.SalesforceSDKLogger


/**
 * Class to capture state related to IDP-SP login in the IDP app
 * e.g. the context (activity) that started it
 *      the web view to get the auth code in
 */
internal class IDPLoginFlow(context:Context, val user:UserAccount, val spConfig: SPConfig, val onStatusUpdate:(Status) -> Unit) : ActiveFlow(context) {

    var authCodeActivity: IDPAuthCodeActivity? = null

    companion object {
        private val TAG: String = IDPLoginFlow::class.java.simpleName
        fun kickOff(idpManager:IDPManager, context: Context, user: UserAccount, spConfig: SPConfig, onStatusUpdate: (Status) -> Unit) {
            SalesforceSDKLogger.d(TAG, "Kicking off login flow from $context")

            val activeFlow = IDPLoginFlow(context, user, spConfig, onStatusUpdate)
            idpManager.startActiveFlow(activeFlow)

            val idpToSpRequest = IDPToSPRequest(orgId = user.orgId, userId = user.userId)
            idpManager.send(context, idpToSpRequest, spConfig.appPackageName)
            onStatusUpdate(Status.LOGIN_REQUEST_SENT_TO_SP)
        }
    }

}

/**
 * Class handling IDP operations within an IDP app
 */
internal class IDPManager(
    val allowedSPApps: List<SPConfig>,
    // the following allows us to decouple IDPManager from other part of the SDK and make it easier to test
    val sdkMgr: SDKManager,
    sendBroadcast: (context:Context, intent:Intent) -> Unit,
    startActivity: (context:Context, intent:Intent) -> Unit
): IDPSPManager(sendBroadcast, startActivity), com.salesforce.androidsdk.auth.idp.interfaces.IDPManager {

    /**
     * Interface to keep IDPManager decoupled from the rest of the SDK
     */
    internal interface SDKManager {
        fun getCurrentUser(): UserAccount?

        fun generateAuthCode(
            authCodeActivity: IDPAuthCodeActivity,
            userAccount: UserAccount,
            spConfig: SPConfig,
            codeChallenge: String,
            onResult: (result: IDPAuthCodeHelper.Result) -> Unit)
    }

    companion object {
        private val TAG = IDPManager::class.java.simpleName
    }

    /**
     * Secondary constructor (all wired)
     */
    constructor(allowedSPApps: List<SPConfig>) : this(
        allowedSPApps,
        object : SDKManager {
            override fun getCurrentUser(): UserAccount? {
                return SalesforceSDKManager.getInstance().userAccountManager.currentUser
            }

            override fun generateAuthCode(
                authCodeActivity: IDPAuthCodeActivity,
                userAccount: UserAccount,
                spConfig: SPConfig,
                codeChallenge: String,
                onResult: (result: IDPAuthCodeHelper.Result) -> Unit
            ) {
                IDPAuthCodeHelper.generateAuthCode(
                    authCodeActivity.webView,
                    userAccount,
                    spConfig,
                    codeChallenge
                ) { result ->
                    authCodeActivity.finish()
                    onResult(result)
                }
            }
        },
        { context, intent ->
            SalesforceSDKLogger.d(TAG, "send broadcast ${LogUtil.intentToString(intent)}")
            context.sendBroadcast(intent)
        },
        { context, intent ->
            SalesforceSDKLogger.d(TAG, "start activity ${LogUtil.intentToString(intent)}")
            context.startActivity(intent)
        }
    )

    /**
     * Current active login flow if any
     */
    private var activeFlow: IDPLoginFlow? = null

    override fun getActiveFlow(): ActiveFlow? {
        return activeFlow
    }

    override fun endActiveFlow() {
        SalesforceSDKLogger.d(TAG, "Ending active flow")
        activeFlow = null
    }

    override fun startActiveFlow(flow: ActiveFlow) {
        activeFlow = flow as IDPLoginFlow
    }

    /**
     * Return SP app config given its package
     * Null is returned if it is not one of the allowed SP apps
     */
    fun getSPConfig(spAppPackageName: String): SPConfig? {
        return allowedSPApps.find { it.appPackageName == spAppPackageName }
    }

    /**
     * Return true if given SP app is allowed
     */
    override fun isAllowed(srcAppPackageName: String): Boolean {
        return getSPConfig(srcAppPackageName) != null
    }

    /**
     * Handle message received
     */
    override fun handle(context: Context, message: IDPSPMessage, srcAppPackageName: String) {
        SalesforceSDKLogger.d(TAG, "handle $message")

        getSPConfig(srcAppPackageName)?.let {spConfig ->
            when (message) {
                is SPToIDPRequest -> {
                    handleLoginRequest(context, message, spConfig)
                }

                is SPToIDPResponse -> {
                    // Handle only if there is an active flow and the message is part of it
                    if (activeFlow?.isPartOfFlow(message) == true) {
                        activeFlow?.let { handleLoginResponse(it, message) }
                    } else {
                        SalesforceSDKLogger.d(TAG, "no idp initiated login flow started - cannot handle $message")
                    }
                }

                else -> {
                    SalesforceSDKLogger.d(TAG, "cannot handle $message")
                }
            }
        } ?: run {
            // we should never get here since IDPSPManager.onReceive checks that
            // the srcAppPackageName is allowed
            SalesforceSDKLogger.d(TAG, "not allowed to handle message from $srcAppPackageName")
        }
    }

    /**
     * Handle case where SP already has user hinted in IDP initiated login request
     * but it is unable to launch activity itself
     */
    fun handleLoginResponse(activeFlow: IDPLoginFlow, message: SPToIDPResponse) {
        SalesforceSDKLogger.d(TAG, "handleLoginResponse $message")
        if (message.error == null) {
            activeFlow.onStatusUpdate(Status.SP_LOGIN_COMPLETE)
            val launchIntent = Intent(Intent.ACTION_VIEW).apply {
                setPackage(activeFlow.spConfig.appPackageName)
                setClassName(activeFlow.spConfig.appPackageName, activeFlow.spConfig.componentName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            startActivity(activeFlow.context, launchIntent)
        } else {
            activeFlow.onStatusUpdate(Status.ERROR_RECEIVED_FROM_SP)
        }
    }

    /**
     * Handle request to login coming from SP app
     * We get an auth code from the server and return it to the SP app or an error if that failed
     */
    fun handleLoginRequest(context: Context, message: SPToIDPRequest, spConfig: SPConfig) {
        SalesforceSDKLogger.d(TAG, "handleLoginRequest $message")
        sdkMgr.getCurrentUser()?.let {currentUser ->

            // During SP initiated login flow:
            // - SP will launch IDPAuthCodeActivity directly with a SPToIDPRequest in the intent
            // - the activity will setup the active flow
            // - the activity will then call this method (through IDP manager onReceive)
            // During IDP initiated login flow:
            // - SP will send a SPToIDPRequest to the IDPReceiver
            // - this method will be invoked which will start the IDPAuthCodeActivity
            // - the activity will setup the active flow
            // - the activity will then call this method again (through IDP manager onReceive)

            activeFlow?.authCodeActivity?.let {authCodeActivity ->
                getAuthCode(context, authCodeActivity, currentUser, spConfig, message)
            } ?: run {
                val intent = message.toIntent().apply {
                    putExtra(SRC_APP_PACKAGE_NAME_KEY, spConfig.appPackageName)
                    // Intent action needs to be ACTION_VIEW, so passing message action through extras
                    putExtra(ACTION_KEY, message.action)
                    action = Intent.ACTION_VIEW
                    setClass(context, com.salesforce.androidsdk.auth.idp.IDPAuthCodeActivity::class.java)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    addCategory(Intent.CATEGORY_DEFAULT)
                }
                // The activity will call onReceive which will call handleLoginRequest but this time
                // with a IDPAuthCodeActivity attached to the flow
                startActivity(context, intent)
            }
        } ?: run {
            send(context, IDPToSPResponse(message.uuid, error = "IDP app not logged in"), spConfig.appPackageName)
        }
    }

    /**
     * Get auth code for the SP app
     */
    fun getAuthCode(
        context: Context,
        authCodeActivity: IDPAuthCodeActivity,
        currentUser: UserAccount,
        spConfig: SPConfig,
        message: SPToIDPRequest
    ) {
        activeFlow?.onStatusUpdate?.let { it(Status.GETTING_AUTH_CODE_FROM_SERVER) }
        sdkMgr.generateAuthCode(
            authCodeActivity,
            currentUser,
            spConfig,
            message.codeChallenge
        ) { result ->
            result.error?.let {error ->
                // We failed to get an auth code
                if (activeFlow?.firstMessage is IDPToSPRequest) {
                    // We are in a IDP initiated flow - let the IDP app know
                    activeFlow?.let { it.onStatusUpdate(Status.ERROR_RECEIVED_FROM_SERVER) }
                } else {
                    // We are NOT in a IDP initiated flow - we need to let the SP app know
                    val response = IDPToSPResponse(message.uuid, error = error)
                    send(context, response, spConfig.appPackageName)
                }
            } ?: run {
                // We successfully got an auth code - send it to the SP app
                val response = IDPToSPResponse(message.uuid, code = result.code, loginUrl = result.loginUrl)
                send(context, response, spConfig.appPackageName)
                // Let the IDP app know
                activeFlow?.let { it.onStatusUpdate(Status.AUTH_CODE_SENT_TO_SP) }
            }
        }
    }

    /**
     * Attach authCodeActivity to IDP manager's active flow (setting up the flow first if needed)
     */
    fun attachToActiveFlow(
        context: Context,
        authCodeActivity: IDPAuthCodeActivity,
        spAppPackageName: String?
    ) {

        activeFlow?.let { flow ->
            // Attach ourself to active flow
            flow.authCodeActivity = authCodeActivity
        } ?: run {
            val spConfig = if (spAppPackageName == null) null  else getSPConfig(spAppPackageName)
            val currentUser = sdkMgr.getCurrentUser()

            // Setup active flow and attach ourself to it
            if (currentUser != null && spConfig != null) {
                val flow = IDPLoginFlow(context, currentUser, spConfig) { }
                startActiveFlow(flow)
                flow.authCodeActivity = authCodeActivity
            }
            // Otherwise close auth code activity
            else {
                authCodeActivity.finish()
            }
        }
    }

    /**
     * Kick off IDP initiated login flow for given SP app
     */
    override fun kickOffIDPInitiatedLoginFlow(context: Context, spAppPackageName: String, callback: StatusUpdateCallback) {
        val spConfig = getSPConfig(spAppPackageName)
        if (spConfig == null) {
            SalesforceSDKLogger.d(TAG, "Cannot kick off IDP initiated login flow: sp app $spAppPackageName not in allowed list")
            return
        }

        val user = sdkMgr.getCurrentUser()
        if (user == null) {
            SalesforceSDKLogger.d(TAG, "Cannot kick off IDP initiated login flow: no current user")
            return
        }

        IDPLoginFlow.kickOff(this, context, user, spConfig, callback::onStatusUpdate)
    }
}