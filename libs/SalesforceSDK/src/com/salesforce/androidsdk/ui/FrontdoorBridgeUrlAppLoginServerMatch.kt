/*
 * Copyright (c) 2025-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.ui

import android.net.Uri
import com.salesforce.androidsdk.config.LoginServerManaging
import java.net.URL

internal data class FrontdoorBridgeUrlAppLoginServerMatch(
    val frontdoorBridgeUrl: Uri,
    val loginServerManaging: LoginServerManaging,
    val addingAndSwitchingLoginServersAllowed: Boolean,
    val selectedAppLoginServer: String
) {

    internal val appLoginServerMatch: String? by lazy {
        appLoginServerForFrontdoorBridgeUrl(
            frontdoorBridgeUrl,
            loginServerManaging,
            addingAndSwitchingLoginServersAllowed,
            selectedAppLoginServer
        )
    }

    private fun appLoginServerForFrontdoorBridgeUrl(
        frontdoorBridgeUrl: Uri,
        loginServerManaging: LoginServerManaging,
        addingAndSwitchingLoginServersAllowed: Boolean,
        selectedAppLoginServer: String
    ): String? {
        val frontdoorBridgeUrlHost = frontdoorBridgeUrl.host ?: return null

        val eligibleAppLoginServers = eligibleAppLoginServersForFrontdoorBridgeUrl(
            loginServerManaging,
            addingAndSwitchingLoginServersAllowed,
            selectedAppLoginServer
        )

        for (eligibleAppLoginServer in eligibleAppLoginServers) {
            if (frontdoorBridgeUrlHost == eligibleAppLoginServer) {
                return eligibleAppLoginServer
            }
        }

        if (frontdoorBridgeUrl.isMyDomain()) {
            val frontdoorBridgeUrlMyDomainSuffix = frontdoorBridgeUrlHost.split("\\.my\\.").last()
            if (frontdoorBridgeUrlMyDomainSuffix.isNotEmpty()) {
                for (eligibleAppLoginServer in eligibleAppLoginServers) {
                    if (eligibleAppLoginServer.endsWith(frontdoorBridgeUrlMyDomainSuffix)) {
                        return eligibleAppLoginServer
                    }
                }
            }
        }

        return null
    }

    private fun eligibleAppLoginServersForFrontdoorBridgeUrl(
        loginHostStore: LoginServerManaging,
        addingAndSwitchingLoginHostsAllowed: Boolean,
        selectedAppLoginHost: String
    ): List<String> {
        val results = mutableListOf<String>()
        if (addingAndSwitchingLoginHostsAllowed) {
            val numberOfHosts = loginHostStore.numberOfLoginServers()
            for (i in 0 until numberOfHosts) {
                val server = loginHostStore.loginServerAtIndex(i)
                server?.let {
                    try {
                        val url = URL(it.url)
                        results.add(url.host)
                    } catch (_: Exception) {
                        // Skip invalid URLs
                    }
                }
            }
        } else {
            results.add(selectedAppLoginHost)
        }
        return results
    }
}

private fun Uri.isMyDomain(): Boolean {
    return host?.contains(".my.") == true
}
