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
import com.salesforce.androidsdk.auth.idp.IDPReceiver.Companion.sendLoginRequestToIDP
import com.salesforce.androidsdk.security.SalesforceKeyGenerator
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

    var spConfig:SPConfig?   = null // null means to idp login flow in flight
    var codeVerifier:String? = null

    companion object {
        val TAG = SPReceiver::class.java.simpleName
        const val IDP_LOGIN_REQUEST = "com.salesforce.IDP_LOGIN_REQUEST"
        const val IDP_LOGIN_RESPONSE = "com.salesforce.IDP_LOGIN_RESPONSE"
        const val USER_HINT_KEY = "user_hint"
        const val SP_ACTVITY_NAME_KEY = "activity_name"
        const val SP_ACTVITY_EXTRAS_KEY = "activity_extras"
        const val IDP_INIT_LOGIN_KEY = "idp_init_login"

        fun sendLoginRequestToSP(
            context: Context,
            spAppPackageName: String,
            spAppComponentName: String,
            currentUser: UserAccount?,
            spActivityExtras: Bundle?
        ) {
            val intent = Intent(IDP_LOGIN_REQUEST)
            intent.setPackage(spAppPackageName)
            intent.putExtra(SP_ACTVITY_NAME_KEY, "$spAppPackageName.$spAppComponentName")
            intent.putExtra(USER_HINT_KEY, SPConfig.userToHint(currentUser))
            if (spActivityExtras != null) intent.putExtras(spActivityExtras)
            SalesforceSDKLogger.d(TAG, "sendLoginRequestToSP ${LogUtil.intentToString(intent)}")
            context.sendBroadcast(intent)
        }

        internal fun sendLoginResponseToSP(context: Context, spAppPackageName: String, result: IDPAuthCodeHelper.Result) {
            val intent = Intent(IDP_LOGIN_RESPONSE)
            intent.setPackage(spAppPackageName)
            intent.putExtras(result.toBundle())
            SalesforceSDKLogger.d(TAG, "sendLoginResponseToSP ${LogUtil.intentToString(intent)}")
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
            codeVerifier = SalesforceKeyGenerator.getRandom128ByteKey()
            val codeChallenge = SalesforceKeyGenerator.getSHA256Hash(codeVerifier)
            spConfig = SPConfig.forCurrentApp(codeChallenge, userHint)
            sendLoginRequestToIDP(context, sdkMgr.idpAppPackageName, spConfig!!)
        }
    }

    private fun handleLoginResponseFromIDP(result:IDPAuthCodeHelper.Result) {
        SalesforceSDKLogger.d(TAG, "handleLoginResponseFromIDP ${result}")
        val spConfig = spConfig
        val codeVerifier = codeVerifier

        if (result.success && spConfig !=null && codeVerifier != null) {
            CoroutineScope(Dispatchers.IO).launch {
                val tokenResponse = OAuth2.getSPCredentials(
                    HttpAccess.DEFAULT,
                    URI.create(result.loginUrl),
                    spConfig.oauthClientId,
                    result.code, codeVerifier,
                    spConfig.oauthCallbackUrl
                )

                SalesforceSDKLogger.d(TAG, "handleLoginResponseFromIDP tokenResponse ${tokenResponse}")
            }
        }

        this.spConfig = null
        this.codeVerifier = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        SalesforceSDKLogger.d(TAG, "onReceive ${LogUtil.intentToString(intent)}")
        when (intent.action) {
            IDP_LOGIN_REQUEST -> {
                handleLoginRequestFromIDP(context, intent.extras)
            }
            IDP_LOGIN_RESPONSE -> {
                IDPAuthCodeHelper.Result.fromBundle(intent.extras)?.let {result ->
                    handleLoginResponseFromIDP(result)
                }
            }
        }
    }
}
