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

import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.BootConfig

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
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SPConfig

        if (appPackageName != other.appPackageName) return false
        if (componentName != other.componentName) return false
        if (oauthClientId != other.oauthClientId) return false
        if (oauthCallbackUrl != other.oauthCallbackUrl) return false
        if (!oauthScopes.contentEquals(other.oauthScopes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = appPackageName.hashCode()
        result = 31 * result + componentName.hashCode()
        result = 31 * result + oauthClientId.hashCode()
        result = 31 * result + oauthCallbackUrl.hashCode()
        result = 31 * result + oauthScopes.contentHashCode()
        return result
    }
}
