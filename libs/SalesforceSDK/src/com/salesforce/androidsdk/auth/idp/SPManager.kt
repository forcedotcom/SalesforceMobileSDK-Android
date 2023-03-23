package com.salesforce.androidsdk.auth.idp

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.auth.idp.IDPSPMessage.*
import com.salesforce.androidsdk.security.SalesforceKeyGenerator
import com.salesforce.androidsdk.util.LogUtil
import com.salesforce.androidsdk.util.SalesforceSDKLogger

/**
 * Class handling SP operations within a SP app
 */
internal class SPManager(
    val idpAppPackageName: String
): IDPSPManager(), com.salesforce.androidsdk.auth.idp.interfaces.SPManager {

    /**
     * Current active login flow if any
     */
    var activeFlow: SPInitiatedFlow? = null

    companion object {
        private val TAG = SPManager::class.java.simpleName
        private const val SRC_APP_PACKAGE_NAME_KEY = "src_app_package_name"
    }

    /**
     * Return true if given IDP app is allowed
     */
    override fun isAllowed(idpAppPackageName: String): Boolean {
        return this.idpAppPackageName == idpAppPackageName
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

        if (activeFlow != null && activeFlow?.uuid == message.uuid) {
            // There is an active flow and the message is part of it
            activeFlow?.messages?.add(message)
        } else {
            // Reset active flow
            activeFlow = null
        }

        // Use active flow context if available
        val contextToUse = activeFlow?.context ?: context

        when (message) {
            is IDPLoginRequest -> {
                handleIDPLoginRequest(contextToUse, message)
            }

            is SPLoginResponse -> {
                // Handle only if there is an active flow
                if (activeFlow != null) {
                    handleSPLoginResponse(contextToUse, message)
                } else {
                    SalesforceSDKLogger.d(TAG, "cannot handle (no sp iniated login flow started) $message")
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
    fun handleIDPLoginRequest(context: Context, message: IDPLoginRequest) {
        SalesforceSDKLogger.d(TAG, "handleIDPLoginRequest $message")
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
    fun handleIDPLoginRequestSPAlreadyLoggedIn(context: Context, message: IDPLoginRequest, user: UserAccount) {
        SalesforceSDKLogger.d(TAG, "handleIDPLoginRequestSPAlreadyLoggedIn $message")
        sdkMgr.userAccountManager.switchToUser(user)

        // We have an activity context - launch main activity from here
        if (context is Activity) {
            val launchIntent = Intent(context, sdkMgr.mainActivityClass)
            SalesforceSDKLogger.d(TAG, "start activity ${LogUtil.intentToString(launchIntent)}")
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
     * We kick off a SP initiated login flow using the same uuid as the original IDP login request
     */
    fun handleIDPLoginRequestSPNotLoggedIn(context: Context, message: IDPLoginRequest) {
        SalesforceSDKLogger.d(TAG, "handleIDPLoginRequestSPNotLoggedIn $message")
        activeFlow = SPInitiatedFlow(context, message.uuid)
    }

    /**
     * Handle response to login request initiated from SP app
     * It will contain either an auth code that we need to exchange for auth tokens
     * or an error if the auth code could not be obtained from the server
     */
    fun handleSPLoginResponse(context: Context, message: SPLoginResponse) {
        SalesforceSDKLogger.d(TAG, "handleSPLoginResponse $message")
        if (message.error != null) {
            // TODO
        } else {
//            CoroutineScope(Dispatchers.IO).launch {
//                val tokenResponse = OAuth2.getSPCredentials(
//                    HttpAccess.DEFAULT,
//                    URI.create(result.loginUrl),
//                    spConfig.oauthClientId,
//                    result.code,
//                    codeVerifier,
//                    spConfig.oauthCallbackUrl
//                )
//
//                SalesforceSDKLogger.d(TAG, "handleLoginResponseFromIDP tokenResponse ${tokenResponse}")
//
//                val loginOptions = ClientManager.LoginOptions(
//                    spConfig.loginUrl,
//                    spConfig.oauthCallbackUrl,
//                    spConfig.oauthClientId,
//                    spConfig.oauthScopes,
//                    null,
//                    null
//                )
//
//                SalesforceSDKLogger.d(TAG, "handleLoginResponseFromIDP loginOptions ${loginOptions}")
//
//                val callback = object: OAuthWebviewHelperEvents {
//                    override fun loadingLoginPage(loginUrl: String?) {
//                        SalesforceSDKLogger.d(TAG, "handleLoginResponseFromIDP callback loadingLoginPage")
//                    }
//
//                    override fun onAccountAuthenticatorResult(authResult: Bundle?) {
//                        SalesforceSDKLogger.d(TAG, "handleLoginResponseFromIDP callback onAccountAuthenticatorResult")
//                    }
//
//                    override fun finish(userAccount: UserAccount?) {
//                        SalesforceSDKLogger.d(TAG, "handleLoginResponseFromIDP callback finish")
//                        userAccount?.let { user -> launchActivity(context, user) }
//                    }
//                }
//                val oauthHelper = OAuthWebviewHelper(context, callback, loginOptions)
//
//                SalesforceSDKLogger.d(TAG, "handleLoginResponseFromIDP oauthHelper ${oauthHelper}")
//
//                oauthHelper.onAuthFlowComplete(tokenResponse)
        }
    }

    /**
     * Kick off SP initiated login flow
     */
    override fun kickOffSPInitiatedLoginFlow(context: Context) {
        SalesforceSDKLogger.d(TAG, "kickOffSPInitiatedLoginFlow")
        activeFlow = SPInitiatedFlow(context)
    }

    inner class SPInitiatedFlow(val context: Context, idpInitiatedFlowUuid: String? = null) {

        val uuid: String
        val codeVerifier: String
        val messages = ArrayList<IDPSPMessage>()

        init {
            codeVerifier = SalesforceKeyGenerator.getRandom128ByteKey()
            val codeChallenge = SalesforceKeyGenerator.getSHA256Hash(codeVerifier)
            val spLoginRequest = if (idpInitiatedFlowUuid == null) {
                SPLoginRequest(codeChallenge = codeChallenge)
            } else {
                SPLoginRequest(idpInitiatedFlowUuid, codeChallenge)
            }
            messages.add(spLoginRequest)
            uuid = spLoginRequest.uuid
            send(context, spLoginRequest)
        }
    }
}