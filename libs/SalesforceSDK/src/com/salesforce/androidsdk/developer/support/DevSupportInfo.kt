package com.salesforce.androidsdk.developer.support

import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.auth.JwtAccessToken
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.config.RuntimeConfig
import java.text.SimpleDateFormat
import java.util.Locale

data class DevSupportInfo(
    val sdkVersion: String,
    val appType: String,
    val userAgent: String,
    val authenticatedUsers: List<UserAccount>,
    val authConfig: List<Pair<String, String>>,
    val bootConfig: BootConfig,
    val currentUser: UserAccount?,
    val runtimeConfig: RuntimeConfig,
) {
    val bootConfigValues: List<Pair<String, String>> by lazy {
        with(bootConfig) {
            val values = mutableListOf(
                "Consumer Key" to remoteAccessConsumerKey,
                "Redirect URI" to oauthRedirectURI,
                "Scopes" to oauthScopes.joinToString(separator = " "),
            )

            if (appType == "Hybrid") {
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

            return@lazy values
        }
    }
    
    val authenticatedUsersString: String = authenticatedUsers.joinToString(separator = ",\n") {
        "${it.displayName} (${it.username})"
    }

    val currentUserInfo:  List<Pair<String, String>> by lazy {
        if (currentUser != null) {
            with(currentUser) {
                return@lazy mutableListOf(
                    "Username" to username,
                    "Consumer Key" to clientId,
                    "Scopes" to scope,
                    "Instance URL" to instanceServer,
                    "Token Format" to tokenFormat,
                    "Access Token Expiration" to accessTokenExpiration,
                    "Beacon Child Consumer Key" to beaconChildConsumerKey,
                )
            }
        } else {
            emptyList()
        }
    }

    val runtimeConfigValues: List<Pair<String, String>> by lazy {
        with(runtimeConfig) {
            val values = mutableListOf(
                "Managed App" to isManagedApp.toString()
            )
            
            if (isManagedApp) {
                values.addAll(listOf(
                    "OAuth ID" to (getString(RuntimeConfig.ConfigKey.ManagedAppOAuthID) ?: "N/A"),
                    "Callback URL" to (getString(RuntimeConfig.ConfigKey.ManagedAppCallbackURL) ?: "N/A"),
                    "Require Cert Auth" to getBoolean(RuntimeConfig.ConfigKey.RequireCertAuth).toString(),
                    "Only Show Authorized Hosts" to getBoolean(RuntimeConfig.ConfigKey.OnlyShowAuthorizedHosts).toString(),
                ))
            }
            
            return@lazy values
        }
    }

    val accessTokenExpiration: String
        get() {
            var expiration = "Unknown"

            if (currentUser?.tokenFormat == "jwt") {
                val jwtAccessToken = JwtAccessToken(currentUser.authToken)
                val expirationDate = jwtAccessToken.expirationDate()
                if (expirationDate != null) {
                    val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    expiration = dateFormatter.format(expirationDate)
                }
            }

            return expiration
    }
}