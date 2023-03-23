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
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.util.LogUtil
import com.salesforce.androidsdk.util.SalesforceSDKLogger

/**
 * SP app configuration
 */
data class SPConfig (
    val appPackageName: String,
    val componentName: String,
    val oauthClientId: String,
    val oauthCallbackUrl: String,
    val oauthScopes: Array<String>
) {
    companion object {
        private val TAG = SPConfig::class.java.simpleName
        private const val APP_PACKAGE_NAME = "app_package_name"
        private const val COMPONENT_NAME = "component_name"
        private const val OAUTH_CLIENT_ID_KEY = "oauth_client_id"
        private const val OAUTH_CALLBACK_URL_KEY = "oauth_callback_url"
        private const val OAUTH_SCOPES_KEY = "oauth_scopes"

        @JvmStatic
        fun forCurrentApp(): SPConfig {
            val sdkMgr = SalesforceSDKManager.getInstance()
            return with(BootConfig.getBootConfig(sdkMgr.appContext)) {
                SPConfig(
                    appPackageName = sdkMgr.appContext.packageName,
                    componentName = sdkMgr.mainActivityClass.name,
                    oauthClientId = remoteAccessConsumerKey,
                    oauthCallbackUrl = oauthRedirectURI,
                    oauthScopes = oauthScopes,
                )
            }
        }

        fun fromBundle(bundle: Bundle?): SPConfig? {
            return bundle?.let {
                with(bundle) {
                    val appPackageName = getString(APP_PACKAGE_NAME)
                    val componentName = getString(COMPONENT_NAME)
                    val oauthClientId = getString(OAUTH_CLIENT_ID_KEY)
                    val oauthCallbackUrl = getString(OAUTH_CALLBACK_URL_KEY)
                    val oauthScopes = getStringArray(OAUTH_SCOPES_KEY)

                    if (appPackageName != null
                        && componentName != null
                        && oauthClientId != null
                        && oauthCallbackUrl != null
                        && oauthScopes != null
                    ) {
                        SPConfig(
                            appPackageName,
                            componentName,
                            oauthClientId,
                            oauthCallbackUrl,
                            oauthScopes
                        )
                    } else {
                        SalesforceSDKLogger.d(
                            TAG,
                            "fromBundle could not parse ${LogUtil.bundleToString(bundle)}"
                        )
                        null
                    }
                }
            } ?: run {
                null
            }
        }
    }

    fun toBundle():Bundle {
        return bundleOf(
            APP_PACKAGE_NAME to appPackageName,
            COMPONENT_NAME to componentName,
            OAUTH_CLIENT_ID_KEY to oauthClientId,
            OAUTH_CALLBACK_URL_KEY to oauthCallbackUrl,
            OAUTH_SCOPES_KEY to oauthScopes
        )
    }
}
