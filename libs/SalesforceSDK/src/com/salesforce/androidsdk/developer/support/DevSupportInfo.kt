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
package com.salesforce.androidsdk.developer.support

import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.JwtAccessToken
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.config.RuntimeConfig
import java.text.SimpleDateFormat
import java.util.Locale

typealias DevInfoList = List<Pair<String, String>>
typealias DevInfoSection = Pair<String, DevInfoList>

data class DevSupportInfo(
    val basicInfo: DevInfoList? = null,
    val authConfigSection: DevInfoSection? = null,
    val bootConfigSection: DevInfoSection? = null,
    val currentUserSection: DevInfoSection? = null,
    val runtimeConfigSection: DevInfoSection? = null,
    val additionalSections: MutableList<DevInfoSection> = mutableListOf(),
) {

    constructor(
        basicInfo: DevInfoList,
        authConfig: DevInfoList,
        bootConfig: BootConfig,
        currentUser: UserAccount?,
        runtimeConfig: RuntimeConfig,
    ): this(
        basicInfo,
        "Authentication Configuration" to authConfig,
        "Boot Configuration" to parseBootConfigInfo(bootConfig),
        parseUserInfoSection(currentUser),
        "Runtime Configuration" to parseRuntimeConfig(runtimeConfig),
    )

    companion object {

        // TODO: Remove this in 14.0 when the older devSupportInfos is removed.
        internal fun createFromLegacyDevInfos(devSupportInfos: List<String>): DevSupportInfo {
            val legacyDevInfo: MutableList<Pair<String, String>> = devSupportInfos.chunked(2) { it[0] to it[1] }.toMutableList()
            val authConfigSection = legacyDevInfo.createSection(
                sectionTitle = "Authentication Configuration",
                "Use Web Server Authentication",
                "Use Hybrid Authentication Token",
                "Support Welcome Discovery",
                "Browser Login Enabled",
                "IDP Enabled",
                "Identity Provider",
            )
            val bootConfigSection = legacyDevInfo.createSection(
                sectionTitle = "Boot Configuration",
                /* ...keys = */ "Consumer Key",
                "Redirect URI",
                "Scopes",
                "Local",
                "Start Page",
                "Unauthenticated Start Page",
                "Error Page",
                "Should Authenticate",
                "Attempt Offline Load",
            )
            val currentUserSection = legacyDevInfo.createSection(
                sectionTitle = "Current User",
                /* ...keys = */ "Username",
                "Consumer Key",
                "Scopes",
                "Instance URL",
                "Token Format",
                "Access Token Expiration",
                "Beacon Child Consumer Key",
            )
            val runtimeConfigSection = legacyDevInfo.createSection(
                sectionTitle = "Runtime Configuration",
                /* ...keys = */ "Managed App",
                "OAuth ID",
                "Callback URL",
                "Require Cert Auth",
                "Only Show Authorized Hosts",
            )

            return DevSupportInfo(
                basicInfo = legacyDevInfo,
                authConfigSection,
                bootConfigSection,
                currentUserSection,
                runtimeConfigSection,
            )
        }

        fun parseBootConfigInfo(bootConfig: BootConfig): DevInfoList {
            with(bootConfig) {
                val values = mutableListOf(
                    "Consumer Key" to remoteAccessConsumerKey,
                    "Redirect URI" to oauthRedirectURI,
                    "Scopes" to (oauthScopes?.joinToString(separator = " ") ?: ""),
                )

                if (SalesforceSDKManager.getInstance().appType == "Hybrid") {
                    values.addAll(
                        listOf(
                            "Local" to isLocal.toString(),
                            "Start Page" to startPage,
                            "Unauthenticated Start Page" to unauthenticatedStartPage,
                            "Error Page" to errorPage,
                            "Should Authenticate" to shouldAuthenticate().toString(),
                            "Attempt Offline Load" to attemptOfflineLoad().toString(),
                        )
                    )
                }

                return values
            }
        }

        fun parseUserInfoSection(currentUser: UserAccount?): DevInfoSection? {
            if (currentUser == null) return null

            var accessTokenExpiration = "Unknown"
            if (currentUser.tokenFormat == "jwt") {
                val jwtAccessToken = JwtAccessToken(currentUser.authToken)
                val expirationDate = jwtAccessToken.expirationDate()
                if (expirationDate != null) {
                    val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    accessTokenExpiration = dateFormatter.format(expirationDate)
                }
            }

            return "Current User" to listOf(
                "Username" to currentUser.username,
                "Consumer Key" to currentUser.clientId,
                "Scopes" to currentUser.scope,
                "Instance URL" to currentUser.instanceServer,
                "Token Format" to (currentUser.tokenFormat?.ifBlank { "Opaque" } ?: "Opaque"),
                "Access Token Expiration" to accessTokenExpiration,
                "Beacon Child Consumer Key" to (currentUser.beaconChildConsumerKey ?: "None"),
            )
        }

        fun parseRuntimeConfig(config: RuntimeConfig): DevInfoList {
            val values = mutableListOf(
                "Managed App" to config.isManagedApp.toString()
            )

            if (config.isManagedApp) {
                values.addAll(listOf(
                    "OAuth ID" to (config.getString(RuntimeConfig.ConfigKey.ManagedAppOAuthID) ?: "N/A"),
                    "Callback URL" to (config.getString(RuntimeConfig.ConfigKey.ManagedAppCallbackURL) ?: "N/A"),
                    "Require Cert Auth" to config.getBoolean(RuntimeConfig.ConfigKey.RequireCertAuth).toString(),
                    "Only Show Authorized Hosts" to config.getBoolean(RuntimeConfig.ConfigKey.OnlyShowAuthorizedHosts).toString(),
                ))
            }

            return values
        }
    }
}

/**
 * Finds data pairs given a list of keys.  Pairs are removed from the original list.
 */
private fun MutableList<Pair<String, String>>.createSection(sectionTitle: String, vararg keys: String): DevInfoSection? {
    val values = keys.mapNotNull { key ->
        find { it.first == key }?.let { pair ->
            remove(pair)
            pair
        }
    }

    return if (values.isNotEmpty()) {
        sectionTitle to values
    } else {
        null
    }
}