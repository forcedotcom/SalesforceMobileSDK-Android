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
import androidx.core.net.toUri
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.config.LoginServerManaging
import com.salesforce.androidsdk.config.RuntimeConfig.ConfigKey.AppServiceHosts
import com.salesforce.androidsdk.config.RuntimeConfig.ConfigKey.OnlyShowAuthorizedHosts
import com.salesforce.androidsdk.config.RuntimeConfig.getRuntimeConfig

/**
 * For Salesforce Identity UI Bridge API support, an overriding front door
 * bridge URL to use in place of the default initial URL.
 */
internal class FrontdoorBridgeLoginOverride(
    /**
     * For Salesforce Identity UI Bridge API support, an overriding front door
     * bridge URL to use in place of the default initial URL
     */
    val frontdoorBridgeUrl: Uri,

    /**
     * For Salesforce Identity UI Bridge API support, the optional web server
     * flow code verifier accompanying the front door bridge URL
     */
    val codeVerifier: String? = null,

    /**
     * The selected app login server.  This is intended for test automation only
     */
    selectedAppLoginServer: String = SalesforceSDKManager.getInstance().loginServerManager.selectedLoginServer.url,

    /**
     * The preference for using mobile device management preferences for
     * allowing the addition and switching of app login servers.  This is
     * intended for test automation only
     */
    addingAndSwitchingLoginServersPerMdm: Boolean = true,

    /**
     * The preference for allowing the addition and switching of app login
     * servers when the MDM preference is ignored.  This is intended for test
     * automation only
     */
    addingAndSwitchingLoginServerOverride: Boolean = false
) {

    /**
     * For Salesforce Identity UI Bridge API support, indicates if the
     * overriding front door bridge URL has a consumer key value that matches
     * the app config.
     */
    var matchesConsumerKey: Boolean = false
        private set

    /**
     * For Salesforce Identity UI Bridge API support, indicates if the
     * overriding front door bridge URL has a host that matches the app's
     * selected login server.
     */
    var matchesLoginHost: Boolean = false
        private set

    init {
        val frontdoorBridgeUrlComponents = frontdoorBridgeUrl.toString()
        val frontdoorBridgeUri = frontdoorBridgeUrlComponents.toUri()
        val startUrlParam = frontdoorBridgeUri.getQueryParameter("startURL")

        // Check if the client_id matches the app's consumer key
        startUrlParam?.let { startUrlString ->
            val startUri = startUrlString.toUri()
            val frontdoorBridgeUrlClientId = startUri.getQueryParameter("client_id")

            frontdoorBridgeUrlClientId?.let { clientId ->
                val appConsumerKey = BootConfig.getBootConfig(SalesforceSDKManager.getInstance().appContext).remoteAccessConsumerKey
                matchesConsumerKey = clientId == appConsumerKey
            }
        }

        // Check if the front door URL host matches the app's selected login server
        val addingAndSwitchingLoginServersAllowedResolved = if (!addingAndSwitchingLoginServersPerMdm) {
            addingAndSwitchingLoginServerOverride
        } else {
            addingAndSwitchingLoginServersAllowed
        }

        val frontdoorBridgeUrlAppLoginServerMatch = FrontdoorBridgeUrlAppLoginServerMatch(
            frontdoorBridgeUrl = frontdoorBridgeUrl,
            loginServerManaging = loginServerManager,
            addingAndSwitchingLoginServersAllowed = addingAndSwitchingLoginServersAllowedResolved,
            selectedAppLoginServer = selectedAppLoginServer
        )

        var appLoginServer = frontdoorBridgeUrlAppLoginServerMatch.appLoginServerMatch
        if (appLoginServer == null && addingAndSwitchingLoginServersAllowedResolved) {
            appLoginServer = frontdoorBridgeUrl.host
        }

        appLoginServer?.let { server ->
            matchesLoginHost = true
            // Set the login server on the server manager
            val loginServerManager = SalesforceSDKManager.getInstance().loginServerManager
            val loginUrl = "https://$server"
            loginServerManager.addCustomLoginServer(loginUrl, loginUrl)
        }
    }

    private val addingAndSwitchingLoginServersAllowed: Boolean
        get() {
            val runtimeConfig = getRuntimeConfig(SalesforceSDKManager.getInstance().appContext)
            val onlyShowAuthorizedServers = runtimeConfig.getBoolean(OnlyShowAuthorizedHosts)
            val mdmLoginServers = try {
                runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts)
            } catch (_: Exception) {
                null
            }
            return !onlyShowAuthorizedServers && (mdmLoginServers?.isEmpty() != false)
        }

    private val loginServerManager: LoginServerManaging
        get() = SalesforceSDKManager.getInstance().loginServerManager
}

