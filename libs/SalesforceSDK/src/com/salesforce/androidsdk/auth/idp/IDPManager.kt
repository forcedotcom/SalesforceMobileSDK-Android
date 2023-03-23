package com.salesforce.androidsdk.auth.idp

import android.content.Context
import android.content.Intent
import android.webkit.WebView
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.auth.idp.IDPSPMessage.*
import com.salesforce.androidsdk.util.LogUtil
import com.salesforce.androidsdk.util.SalesforceSDKLogger

/**
 * Class handling IDP operations within an IDP app
 */
internal class IDPManager(
    val allowedSPApps: List<SPConfig>
): IDPSPManager(), com.salesforce.androidsdk.auth.idp.interfaces.IDPManager {

    /**
     * Current active login flow if any
     */
    var activeFlow: IDPInitiatedFlow? = null

    companion object {
        private val TAG = IDPManager::class.java.simpleName
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
    override fun isAllowed(spAppPackageName: String): Boolean {
        return getSPConfig(spAppPackageName) != null
    }

    /**
     * Handle message received
     */
    override fun handle(context: Context, message: IDPSPMessage, srcAppPackageName: String) {
        SalesforceSDKLogger.d(TAG, "handle $message")

        if (activeFlow != null && activeFlow?.uuid == message.uuid) {
            // There is an active flow and the message is part of it
            activeFlow?.messages?.add(message)
        } else {
            // Reset active flow
            activeFlow = null
        }

        // Use active flow context if available
        val contextToUse = activeFlow?.context ?: context

        getSPConfig(srcAppPackageName)?.let {spConfig ->
            when (message) {
                is IDPLoginResponse -> {
                    // Handle only if there is an active flow
                    if (activeFlow != null) {
                        handleIDPLoginResponse(contextToUse, message, spConfig)
                    } else {
                        SalesforceSDKLogger.d(TAG, "cannot handle (no idp iniated login flow started) $message")
                    }
                }

                is SPLoginRequest -> {
                    handleSPLoginRequest(contextToUse, message, spConfig)
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
    fun handleIDPLoginResponse(context:Context, message: IDPLoginResponse, spConfig: SPConfig) {
        SalesforceSDKLogger.d(TAG, "handleIDPLoginResponse $message")
        val launchIntent = Intent(Intent.ACTION_VIEW).apply {
            setPackage(spConfig.appPackageName)
            setClassName(spConfig.appPackageName, spConfig.componentName)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        SalesforceSDKLogger.d(TAG, "handleIDPLoginResponse startActivity ${LogUtil.intentToString(launchIntent)}")
        context.startActivity(launchIntent)
    }

    /**
     * Handle request to login coming from SP app
     * We get an auth code from the server and return it to the SP app or an error if that failed
     */
    fun handleSPLoginRequest(context: Context, message: SPLoginRequest, spConfig: SPConfig) {
        SalesforceSDKLogger.d(TAG, "handleSPLoginRequest $message")
        sdkMgr.userAccountManager.currentUser?.let {currentUser ->
            // Get auth code for current user
            IDPAuthCodeHelper.generateAuthCode(
                WebView(context),
                currentUser,
                spConfig,
                message.codeChallenge,
                object : IDPAuthCodeHelper.Callback {
                    override fun onResult(result: IDPAuthCodeHelper.Result) {
                        val spLoginResponse = SPLoginResponse(code = result.code, loginUrl = result.loginUrl)
                        send(context, spLoginResponse, spConfig.appPackageName)
                    }
                })

        } ?: run {
            val spLoginResponse = SPLoginResponse(error = "IDP app not logged in")
            send(context, spLoginResponse, spConfig.appPackageName)
        }
    }

    /**
     * Kick off IDP initiated login flow for given SP app
     */
    override fun kickOffIDPInitiatedLoginFlow(context: Context, spAppPackageName: String) {
        if (!isAllowed(spAppPackageName)) {
            SalesforceSDKLogger.d(TAG, "Cannot kick off IDP initiated login flow: sp app $spAppPackageName not in allowed list")
            return
        }

        val user = sdkMgr.userAccountManager.currentUser
        if (user == null) {
            SalesforceSDKLogger.d(TAG, "Cannot kick off IDP initiated login flow: no current user")
            return
        }

        SalesforceSDKLogger.d(TAG, "kickOffIDPIniatedLoginFlow for ${spAppPackageName}")
        activeFlow = IDPInitiatedFlow(context, user, spAppPackageName)
    }

    inner class IDPInitiatedFlow(val context: Context, val user: UserAccount, val spAppPackageName: String) {

        val uuid: String
        val messages = ArrayList<IDPSPMessage>()

        init {
            val idpLoginRequest = IDPLoginRequest(orgId = user.orgId, userId = user.userId)
            messages.add(idpLoginRequest)
            uuid = idpLoginRequest.uuid
            send(context, idpLoginRequest, spAppPackageName)
        }
    }
}