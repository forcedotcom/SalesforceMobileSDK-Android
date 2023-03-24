package com.salesforce.androidsdk.auth.idp

import android.content.Context
import android.content.Intent
import android.webkit.WebView
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.auth.idp.IDPSPMessage.*
import com.salesforce.androidsdk.auth.idp.interfaces.IDPManager.Status
import com.salesforce.androidsdk.auth.idp.interfaces.IDPManager.StatusUpdateCallback
import com.salesforce.androidsdk.util.LogUtil
import com.salesforce.androidsdk.util.SalesforceSDKLogger


/**
 * Class to capture state related to IDP initiated login flow
 * e.g. the context (activity) that started it
 */
internal class IDPInitiatedLoginFlow private constructor(context:Context, val user:UserAccount, val spConfig: SPConfig, val onStatusUpdate:(Status) -> Unit) : ActiveFlow(context) {
    companion object {
        val TAG = IDPInitiatedLoginFlow::class.java.simpleName
        fun kickOff(idpManager:IDPManager, context: Context, user: UserAccount, spConfig: SPConfig, onStatusUpdate: (Status) -> Unit) : IDPInitiatedLoginFlow {
            SalesforceSDKLogger.d(TAG, "Kicking off idp initiated login flow from ${context} for user:${user}, sp app:${spConfig.appPackageName}")

            val idpLoginRequest = IDPLoginRequest(orgId = user.orgId, userId = user.userId)
            val activeFlow = IDPInitiatedLoginFlow(context, user, spConfig, onStatusUpdate)
            idpManager.send(context, idpLoginRequest, spConfig.appPackageName)
            onStatusUpdate(Status.LOGIN_REQUEST_SENT_TO_SP)
            return activeFlow
        }
    }

}

/**
 * Class handling IDP operations within an IDP app
 */
internal class IDPManager(
    val allowedSPApps: List<SPConfig>
): IDPSPManager(), com.salesforce.androidsdk.auth.idp.interfaces.IDPManager {

    companion object {
        private val TAG = IDPManager::class.java.simpleName
    }

    /**
     * Current active login flow if any
     */
    private var activeFlow: IDPInitiatedLoginFlow? = null

    override fun getActiveFlow(): ActiveFlow? {
        return activeFlow
    }

    override fun endActiveFlow() {
        activeFlow = null
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
                is IDPLoginResponse -> {
                    // Handle only if there is an active flow and the message is part of it
                    if (activeFlow?.isPartOfFlow(message) == true) {
                        activeFlow?.let { handleLoginResponse(it, message) }
                    } else {
                        SalesforceSDKLogger.d(TAG, "no idp iniated login flow started - cannot handle $message")
                    }
                }

                is SPLoginRequest -> {
                    handleLoginRequest(context, message, spConfig)
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
    fun handleLoginResponse(activeFlow: IDPInitiatedLoginFlow, message: IDPLoginResponse) {
        SalesforceSDKLogger.d(TAG, "handleLoginResponse $message")
        activeFlow.onStatusUpdate(Status.SP_READY)
        val launchIntent = Intent(Intent.ACTION_VIEW).apply {
            setPackage(activeFlow.spConfig.appPackageName)
            setClassName(activeFlow.spConfig.appPackageName, activeFlow.spConfig.componentName)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        SalesforceSDKLogger.d(TAG, "handleLoginResponse startActivity ${LogUtil.intentToString(launchIntent)}")
        activeFlow.context.startActivity(launchIntent)
    }

    /**
     * Handle request to login coming from SP app
     * We get an auth code from the server and return it to the SP app or an error if that failed
     */
    fun handleLoginRequest(context: Context, message: SPLoginRequest, spConfig: SPConfig) {
        SalesforceSDKLogger.d(TAG, "handleLoginRequest $message")
        sdkMgr.userAccountManager.currentUser?.let {currentUser ->
            // Get auth code for current user
            IDPAuthCodeHelper.generateAuthCode(
                WebView(context),
                currentUser,
                spConfig,
                message.codeChallenge,
                { result ->
                    val spLoginResponse = SPLoginResponse(message.uuid, code = result.code, loginUrl = result.loginUrl)
                    send(context, spLoginResponse, spConfig.appPackageName)
                }
            )

        } ?: run {
            val spLoginResponse = SPLoginResponse(error = "IDP app not logged in")
            send(context, spLoginResponse, spConfig.appPackageName)
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

        val user = sdkMgr.userAccountManager.currentUser
        if (user == null) {
            SalesforceSDKLogger.d(TAG, "Cannot kick off IDP initiated login flow: no current user")
            return
        }

        activeFlow = IDPInitiatedLoginFlow.kickOff(this, context, user, spConfig, callback::onStatusUpdate)
    }
}