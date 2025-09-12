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

internal fun appLoginServerForFrontdoorBridgeUrl(
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

    // TODO: "Would be more efficient to combine this so you aren't iterating through the list twice." ECJ20250911
    for (eligibleAppLoginServer in eligibleAppLoginServers) {
        if (frontdoorBridgeUrlHost == eligibleAppLoginServer) {
            return eligibleAppLoginServer
        }
    }

    // TODO: Complete review of both versions of login host soft-matching logic below. ECJ20250911
    // Original Notes From Slack
//    Let me recap what I have, soft matching is defined as:
//    if QR is not a my domain, existing login server must match exactly
//    if QR is a my domain, existing login server must either match exactly or match everything after the .my.
//    (a) If adding and switching are disallowed, only let the QR through if its login server "soft-matches" the currently selected login server.
//    (b) If adding is disallowed but switching is allowed, let the QR through if its login server "soft-matches" any of the login server and switch to it.
//    (c) If adding is allowed and switching is allowed, try (b) first, but if no match are found add the QR login server and switch to it.

    // Newer Notes From Github
    // [Soft match]
    // Look at part of the hostname in the QR code that comes after .my. and make sure it appears in the currently selected login server
    //   also as long as the currently selected login server does not have .my, itself.
    // When the currently login server has a .my. the whole hostname should match.
    // So mydomain.my.salesforce.com would be allowed if login.salesforce.com is currently selected
    //   but not if myotherdomain.my.salesforce.com is selected.


    if (frontdoorBridgeUrl.isMyDomain()) {
        val frontdoorBridgeUrlMyDomainSuffix = "my.${frontdoorBridgeUrlHost.split(".my.").last()}"
        for (eligibleAppLoginServer in eligibleAppLoginServers) {
            if (eligibleAppLoginServer.endsWith(frontdoorBridgeUrlMyDomainSuffix)) {
                return eligibleAppLoginServer
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
        for (loginServer in loginHostStore.loginServers ?: return emptyList()) {
            runCatching {
                val url = URL(loginServer.url)
                results.add(url.host)
            }
        }
    } else {
        runCatching {
            val url = URL(selectedAppLoginHost)
            results.add(url.host)
        }
    }
    return results
}

private fun Uri.isMyDomain(): Boolean {
    return host?.contains(".my.") == true
}
