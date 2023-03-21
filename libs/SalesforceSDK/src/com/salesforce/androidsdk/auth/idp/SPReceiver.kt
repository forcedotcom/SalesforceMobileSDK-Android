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
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.auth.OAuth2
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.security.SalesforceKeyGenerator
import com.salesforce.androidsdk.ui.OAuthWebviewHelper
import com.salesforce.androidsdk.ui.OAuthWebviewHelper.OAuthWebviewHelperEvents
import com.salesforce.androidsdk.util.LogUtil
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URI

/**
 * Receiver running in SP app handling calls from IDP app
 */
class SPReceiver : BroadcastReceiver() {
    
    init {
        SalesforceSDKLogger.d(TAG, "Creating receiver")
    }

    companion object {
        val TAG = SPReceiver::class.java.simpleName
        const val IDP_LOGIN_REQUEST = "com.salesforce.IDP_LOGIN_REQUEST"
        const val IDP_LOGIN_RESPONSE = "com.salesforce.IDP_LOGIN_RESPONSE"
        const val USER_HINT_KEY = "user_hint"
        const val SP_ACTVITY_NAME_KEY = "activity_name"
        const val SP_ACTVITY_EXTRAS_KEY = "activity_extras"
        const val IDP_INIT_LOGIN_KEY = "idp_init_login"

        var spConfig: SPConfig? = null   // null means to idp login flow in flight
        var codeVerifier: String? = null

        @JvmStatic
        fun sendLoginRequestToIDP(
            context: Context,
            userHint: String? = null
        ) {
            codeVerifier = SalesforceKeyGenerator.getRandom128ByteKey()
            spConfig = SPConfig.forCurrentApp(SalesforceKeyGenerator.getSHA256Hash(codeVerifier), userHint)
            val intent = Intent(IDPReceiver.SP_LOGIN_REQUEST_ACTION)
            intent.setPackage(SalesforceSDKManager.getInstance().idpAppPackageName)
            intent.putExtra(IDPReceiver.SP_CONFIG_BUNDLE_KEY, spConfig!!.toBundle())
            SalesforceSDKLogger.d(TAG, "sendLoginRequestToIDP " + LogUtil.intentToString(intent))
            context.sendBroadcast(intent)
        }
    }

    private fun handleLoginRequestFromIDP(context:Context, extras: Bundle?) {
        val spActivityName = extras?.getString(SP_ACTVITY_NAME_KEY)
        val spActivityExtras = extras?.getBundle(SP_ACTVITY_EXTRAS_KEY)
        val userHint = extras?.getString(USER_HINT_KEY)
        val userHinted = SPConfig.userFromHint(userHint)

        val sdkMgr = SalesforceSDKManager.getInstance()
        if (userHinted != null) {
            // We have that user already
            UserAccountManager.getInstance().switchToUser(userHinted)
            if (!spActivityName.isNullOrEmpty()) {
                try {
                    val launchIntent = Intent(sdkMgr.appContext, Class.forName(spActivityName))
                    launchIntent.addCategory(Intent.CATEGORY_DEFAULT)
                    launchIntent.putExtra(SP_ACTVITY_EXTRAS_KEY, spActivityExtras)
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    SalesforceSDKLogger.d(TAG, "handleLoginRequest starting activity " + LogUtil.intentToString(launchIntent))
                    sdkMgr.appContext.startActivity(launchIntent)
                } catch (e: Exception) {
                    SalesforceSDKLogger.e(TAG, "", e)
                }
            }
        }
        else {
            // We need to login through the IDP
            sendLoginRequestToIDP(context, userHint)
        }
    }

    private fun handleLoginResponseFromIDP(context:Context, result:IDPAuthCodeHelper.Result) {
        SalesforceSDKLogger.d(TAG, "handleLoginResponseFromIDP ${result}")
        val spConfig = SPReceiver.spConfig
        val codeVerifier = SPReceiver.codeVerifier

        if (result.success && codeVerifier != null && spConfig != null) {
            CoroutineScope(Dispatchers.IO).launch {
                val tokenResponse = OAuth2.getSPCredentials(
                    HttpAccess.DEFAULT,
                    URI.create(result.loginUrl),
                    spConfig.oauthClientId,
                    result.code,
                    codeVerifier,
                    spConfig.oauthCallbackUrl
                )

                SalesforceSDKLogger.d(TAG, "handleLoginResponseFromIDP tokenResponse ${tokenResponse}")

                val loginOptions = ClientManager.LoginOptions(
                    spConfig.loginUrl,
                    spConfig.oauthCallbackUrl,
                    spConfig.oauthClientId,
                    spConfig.oauthScopes,
                    null,
                    null
                )

                SalesforceSDKLogger.d(TAG, "handleLoginResponseFromIDP loginOptions ${loginOptions}")

                val callback = object: OAuthWebviewHelperEvents {
                    override fun loadingLoginPage(loginUrl: String?) {
                        SalesforceSDKLogger.d(TAG, "handleLoginResponseFromIDP callback loadingLoginPage")
                    }

                    override fun onAccountAuthenticatorResult(authResult: Bundle?) {
                        SalesforceSDKLogger.d(TAG, "handleLoginResponseFromIDP callback onAccountAuthenticatorResult")
                    }

                    override fun finish(userAccount: UserAccount?) {
                        SalesforceSDKLogger.d(TAG, "handleLoginResponseFromIDP callback finish")
                        userAccount?.let { user -> launchActivity(context, user) }
                    }
                }
                val oauthHelper = OAuthWebviewHelper(context, callback, loginOptions)

                SalesforceSDKLogger.d(TAG, "handleLoginResponseFromIDP oauthHelper ${oauthHelper}")

                oauthHelper.onAuthFlowComplete(tokenResponse)
            }
        }

         SPReceiver.codeVerifier = null
         SPReceiver.spConfig = null
    }

    fun launchActivity(context:Context, user:UserAccount) {
        UserAccountManager.getInstance().switchToUser(user)
        try {
            val launchIntent = Intent(context, SalesforceSDKManager.getInstance().mainActivityClass)
            launchIntent.addCategory(Intent.CATEGORY_DEFAULT)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            SalesforceSDKLogger.d(TAG, "launchActivity starting activity " + LogUtil.intentToString(launchIntent))
            context.startActivity(launchIntent)
        } catch (e: Exception) {
            SalesforceSDKLogger.e(TAG, "", e)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        SalesforceSDKLogger.d(TAG, "onReceive ${LogUtil.intentToString(intent)}")
        when (intent.action) {
            IDP_LOGIN_REQUEST -> {
                handleLoginRequestFromIDP(context, intent.extras)
            }
            IDP_LOGIN_RESPONSE -> {
                IDPAuthCodeHelper.Result.fromBundle(intent.extras)?.let {result ->
                    handleLoginResponseFromIDP(context, result)
                }
            }
        }
    }

}
