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

import android.os.Bundle
import androidx.core.os.bundleOf
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.OAuth2
import com.salesforce.androidsdk.config.BootConfig

/**
 * SP app configuration
 */
data class SPConfig (
    val oauthClientId: String,
    val oauthCallbackUrl: String,
    val codeChallenge: String,
    val oauthScopes: Array<String>,
    val loginUrl: String,
    val userHint: String?
) {
    val userHinted: UserAccount? = userFromHint(userHint)

    companion object {
        private const val OAUTH_CLIENT_ID_KEY = "oauth_client_id"
        private const val OAUTH_CALLBACK_URL_KEY = "oauth_callback_url"
        private const val CODE_CHALLENGE_KEY = "code_challenge"
        private const val OAUTH_SCOPES_KEY = "oauth_scopes"
        private const val LOGIN_URL_KEY = "login_url"
        private const val USER_HINT_KEY = "user_hint"

        fun forCurrentApp(codeChallenge: String, userHint:String?):SPConfig {
            val sdkMgr = SalesforceSDKManager.getInstance()
            return with(BootConfig.getBootConfig(sdkMgr.appContext)) {
                SPConfig(
                    oauthClientId = remoteAccessConsumerKey,
                    oauthCallbackUrl = oauthRedirectURI,
                    codeChallenge = codeChallenge,
                    oauthScopes = oauthScopes,
                    loginUrl = sdkMgr.loginServerManager.selectedLoginServer.url.trim(),
                    userHint = userHint
                )
            }
        }

        fun fromBundle(bundle: Bundle?):SPConfig? {
            return if (bundle == null) null else {
                with(bundle) {
                    SPConfig(
                        oauthClientId = getString(OAUTH_CLIENT_ID_KEY)!!,
                        oauthCallbackUrl = getString(OAUTH_CALLBACK_URL_KEY)!!,
                        codeChallenge = getString(CODE_CHALLENGE_KEY)!!,
                        oauthScopes = getStringArray(OAUTH_SCOPES_KEY)!!,
                        loginUrl = getString(LOGIN_URL_KEY)!!,
                        userHint = getString(USER_HINT_KEY)!!
                    )
                }
            }
        }

        fun userToHint(user: UserAccount?) : String? {
            return if (user == null)  null else "${user.orgId}:${user.userId}"
        }
        fun userFromHint(userHint: String?):UserAccount? {
            val sdkMgr = SalesforceSDKManager.getInstance()
            val parts = userHint?.split(":")
            return if (parts != null && parts.size == 2) {
                sdkMgr.userAccountManager.getUserFromOrgAndUserId(parts[0], parts[1])
            } else {
                null
            }
        }
    }

    fun toBundle():Bundle {
        return bundleOf(
            OAUTH_CLIENT_ID_KEY to oauthClientId,
            OAUTH_CALLBACK_URL_KEY to oauthCallbackUrl,
            CODE_CHALLENGE_KEY to codeChallenge,
            OAUTH_SCOPES_KEY to oauthScopes,
            LOGIN_URL_KEY to loginUrl,
            USER_HINT_KEY to userHint
        )
    }
}
