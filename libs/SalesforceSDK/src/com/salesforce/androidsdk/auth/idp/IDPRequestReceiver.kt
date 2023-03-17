/*
 * Copyright (c) 2017-present, salesforce.com, inc.
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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse
import com.salesforce.androidsdk.util.LogUtil
import com.salesforce.androidsdk.util.SalesforceSDKLogger

/**
 * Receiver running in SP app handling calls from IDP app
 */
class IDPRequestReceiver : BroadcastReceiver() {

    companion object {
        const val IDP_LOGIN_REQUEST = "com.salesforce.IDP_LOGIN_REQUEST"
        const val IDP_LOGIN_RESPONSE = "com.salesforce.IDP_LOGIN_RESPONSE"
        const val USER_HINT_KEY = "user_hint"
        const val SP_ACTVITY_NAME_KEY = "activity_name"
        const val SP_ACTVITY_EXTRAS_KEY = "activity_extras"
        const val IDP_INIT_LOGIN_KEY = "idp_init_login"
        val TAG = IDPRequestReceiver::class.java.simpleName

        fun sendLoginRequest(
            context: Context,
            spAppPackageName: String,
            spAppComponentName: String,
            currentUser: UserAccount?,
            spActivityExtras: Bundle?
        ) {
            val intent = Intent(IDP_LOGIN_REQUEST)
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            intent.setPackage(spAppPackageName)
            intent.putExtra(SP_ACTVITY_NAME_KEY, "$spAppPackageName.$spAppComponentName")
            intent.putExtra(USER_HINT_KEY, buildUserHint(currentUser))
            if (spActivityExtras != null) intent.putExtras(spActivityExtras)
            SalesforceSDKLogger.d(TAG, "sendLoginRequest $intent")
            context.sendBroadcast(intent)
        }

        fun sendLoginResponse(context: Context, spAppPackageName: String, data: Bundle) {
            val intent = Intent(IDP_LOGIN_RESPONSE)
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            intent.setPackage(spAppPackageName)
            intent.putExtras(data)
            SalesforceSDKLogger.d(TAG, "sendLoginResponse $intent")
            context.sendBroadcast(intent)
        }

        fun buildUserHint(user: UserAccount?):String? {
            return user?.let { it ->  "${it.orgId}:${it.userId}" }
        }
    }

    fun handleLoginRequest(extras: Bundle?) {
        val spActivityName = extras?.getString(SP_ACTVITY_NAME_KEY)
        val spActivityExtras = extras?.getBundle(SP_ACTVITY_EXTRAS_KEY)

        val userHinted = getUserForHint(extras?.getString(USER_HINT_KEY))
        // Launches login flow if the user doesn't already exist on the SP app.
        if (userHinted == null) {
            val loginServer =
                SalesforceSDKManager.getInstance().loginServerManager.selectedLoginServer.url.trim { it <= ' ' }
            SalesforceSDKLogger.d(
                TAG,
                "Launching IDP app for authentication with login host: $loginServer"
            )
            val spRequestHandler = SPRequestHandler(
                loginServer,
                userHinted,
                object : SPRequestHandler.SPAuthCallback {
                    override fun receivedTokenResponse(tokenResponse: TokenEndpointResponse) {}
                    override fun receivedErrorResponse(errorMessage: String) {}
                })
            spRequestHandler.launchIDPAppWithBroadcast(SalesforceSDKManager.getInstance().appContext)
        } else {

            // If user hint was passed in, switches to the specified user.
            UserAccountManager.getInstance().switchToUser(userForHint)

            /*
             * If the IDP app specified a component to launch after login, launches that
             * component. The activity that is passed in must contain its full package name.
             */if (!TextUtils.isEmpty(spActivityName)) {
                try {
                    val launchIntent = Intent(
                        SalesforceSDKManager.getInstance().appContext,
                        Class.forName(spActivityName)
                    )
                    launchIntent.addCategory(Intent.CATEGORY_DEFAULT)
                    launchIntent.putExtra(SP_ACTVITY_EXTRAS_KEY, spActivityExtras)
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    Log.d(
                        TAG,
                        "onReceive startActivity " + LogUtil.intentToString(launchIntent)
                    )
                    SalesforceSDKManager.getInstance().appContext.startActivity(launchIntent)
                } catch (e: Exception) {
                    SalesforceSDKLogger.e(TAG, "Could not start activity", e)
                }
            }
        }
    }

    fun handleLoginResponse(data: Bundle) {

    }

    override fun onReceive(context: Context, intent: Intent) {
        SalesforceSDKLogger.d(TAG, "onReceive " + LogUtil.intentToString(intent))
            when (intent.action) {
                IDP_LOGIN_REQUEST -> handleLoginRequest(intent.extras)
                IDP_
            }
            if (IDP_LOGIN_REQUEST == intent.action) {
                handleLoginRequest(intent.extras)
            }

        }
    }

    fun getUserForHint(userHint: String?): UserAccount? {
        /*
         * The value for 'user_hint' should be of the format 'orgId:userId' and should
         * use the 18-character versions of 'orgId' and 'userId'.
         */
        val userParts = userHint?.split(":")
        return if (userParts != null && userParts.size == 2) {
            val orgId = userParts[0]
            val userId = userParts[1]
            SalesforceSDKManager.getInstance().userAccountManager.getUserFromOrgAndUserId(
                orgId,
                userId
            )
        } else {
            null
        }
    }
}

//    private fun launchLoginActivity() {
//        val options = SalesforceSDKManager.getInstance().loginOptions.asBundle()
//        val intent = Intent(
//            SalesforceSDKManager.getInstance().appContext,
//            SalesforceSDKManager.getInstance().loginActivityClass
//        )
//        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
//        intent.putExtras(options)
//        intent.putExtra(USER_HINT_KEY, userHint)
//        intent.putExtra(SP_ACTVITY_NAME_KEY, spActivityName)
//        intent.putExtra(SP_ACTVITY_EXTRAS_KEY, spActivityExtras)
//        intent.putExtra(IDP_INIT_LOGIN_KEY, true)
//        Log.d(TAG, "launchLoginActivity " + LogUtil.intentToString(intent))
//        SalesforceSDKManager.getInstance().appContext.startActivity(intent)
//    }

