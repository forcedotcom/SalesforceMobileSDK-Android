/*
 * Copyright (c) 2026-present, salesforce.com, inc.
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
package com.salesforce.samples.authflowtester.testUtility

import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.util.ResourceReaderHelper
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class ScopeSelection {
    EMPTY,
    SUBSET,
    ALL,
}

enum class KnownUserConfig {
    FIRST,
    SECOND,
    THIRD,
    FORTH,
    FIFTH,
}

enum class KnownLoginHostConfig {
    REGULAR_AUTH,
    ADVANCED_AUTH,
    COMMUNITY_AUTH,
}

enum class KnownAppConfig {
    ECA_OPAQUE,
    ECA_JWT,
    ECA_OPAQUE_RTR,
    ECA_JWT_RTR,
    BEACON_OPAQUE,
    BEACON_JWT,
    CA_OPAQUE,
    CA_JWT,
    ECA_JWT_DPOP,
    ECA_JWT_DPOP_RTR,
    ECA_JWT_HEADLESS,
    ECA_JWT_DPOP_HEADLESS,
}

private val json = Json { ignoreUnknownKeys = true }

val testConfig: UITestConfig by lazy {
    json.decodeFromString(
        string = ResourceReaderHelper.readAssetFile(
            InstrumentationRegistry.getInstrumentation().targetContext,
            /* assetFilePath = */ "ui_test_config.json",
        ) ?: throw Exception("ui_test_config.json file not found.")
    )
}

@Serializable
data class UITestConfig(
    val loginPoolHost: String? = null,
    val loginHosts: List<LoginHost>,
    val apps: List<AppConfig>,
) {

    /**
     * True when at least one headless ECA (`eca_jwt_headless` or `eca_jwt_dpop_headless`) is in
     * `apps`. Native Login tests skip via `assumeTrue` when neither is, for environments that do not
     * provision them. This only checks names, so a bad Native Login entry never affects other suites.
     */
    val hasNativeLoginApps: Boolean
        get() = NATIVE_LOGIN_APPS.any { findApp(it) != null }

    /**
     * Resolves the Native Login fixtures by name: the `eca_jwt_headless` and `eca_jwt_dpop_headless`
     * apps and the first user of the `community_auth` login host.
     *
     * @throws NativeLoginConfigException When either app, the host or its user is missing.
     */
    fun nativeLoginSetup(): NativeLoginSetup {
        val missingApps = NATIVE_LOGIN_APPS.filter { findApp(it) == null }.map { it.configName }
        if (missingApps.isNotEmpty()) {
            throw NativeLoginConfigException(
                "Native Login tests need both ${NATIVE_LOGIN_APPS.joinToString(" and ") { it.configName }} " +
                    "in apps of ui_test_config.json; missing: ${missingApps.joinToString()}."
            )
        }
        val hostName = KnownLoginHostConfig.COMMUNITY_AUTH.configName
        val host = loginHosts.find { it.name == hostName }
            ?: throw NativeLoginConfigException("Native Login tests need the '$hostName' login host in ui_test_config.json.")
        val hostUser = host.users.firstOrNull()
            ?: throw NativeLoginConfigException("The '$hostName' login host in ui_test_config.json has no users.")
        return NativeLoginSetup(
            host = host,
            user = hostUser,
            ecaJwtHeadless = getApp(KnownAppConfig.ECA_JWT_HEADLESS),
            ecaJwtDPoPHeadless = getApp(KnownAppConfig.ECA_JWT_DPOP_HEADLESS),
        )
    }

    private fun findApp(knownAppConfig: KnownAppConfig): AppConfig? =
        apps.find { it.name == knownAppConfig.configName }

    fun requireLoginPoolHost(): String = loginPoolHost
        ?: throw Exception("loginPoolHost not found in ui_test_config.json.")

    fun getLoginHost(knownLoginHostConfig: KnownLoginHostConfig): LoginHost = loginHosts.find {
        (name, _, _) -> name == knownLoginHostConfig.name.toLowerCase(Locale.current)
    } ?: throw Exception("LoginHost not found.")

    // Not every environment provisions every login host (e.g. community_auth requires a
    // dedicated Experience Cloud site). Tests that depend on an optional host should skip
    // via Assume.assumeTrue(testConfig.hasLoginHost(...)) rather than fail. A host with no
    // configured users counts as absent so that a partially provisioned host skips instead of
    // failing in getUser.
    fun hasLoginHost(knownLoginHostConfig: KnownLoginHostConfig): Boolean = loginHosts.any {
        (name, _, users) -> name == knownLoginHostConfig.name.toLowerCase(Locale.current) && users.isNotEmpty()
    }

    fun getUser(knownLoginHostConfig: KnownLoginHostConfig, knownUserConfig: KnownUserConfig): User =
        getLoginHost(knownLoginHostConfig).users[knownUserConfig.ordinal]

    fun getApp(knownAppConfig: KnownAppConfig): AppConfig = apps.find {
        (name, _, _, _) -> name == knownAppConfig.name.toLowerCase(Locale.current)
    } ?: throw Exception("AppConfig not found.")

    fun getAppWithRequestScopes(knownAppConfig: KnownAppConfig, scopeSelection: ScopeSelection): AppConfig =
        with(getApp(knownAppConfig)) {
          return@with copy(scopes = scopesToRequest(scopeSelection))
        }

}

/** The Native Login fixtures. The user is reused from the host's `users`, never duplicated. */
data class NativeLoginSetup(
    val host: LoginHost,
    val user: User,
    /** Headless ECA that accepts, but does not require, DPoP. */
    val ecaJwtHeadless: AppConfig,
    /** Headless ECA that requires DPoP: authorize without `dpop_jkt` is rejected. */
    val ecaJwtDPoPHeadless: AppConfig,
)

private val NATIVE_LOGIN_APPS = listOf(KnownAppConfig.ECA_JWT_HEADLESS, KnownAppConfig.ECA_JWT_DPOP_HEADLESS)

private val KnownAppConfig.configName: String get() = name.lowercase()
private val KnownLoginHostConfig.configName: String get() = name.lowercase()

class NativeLoginConfigException(message: String) : Exception(message)

@Serializable
data class LoginHost(
    val name: String,
    val url: String,
    val users: List<User>,
)

@Serializable
data class User(val username: String, val password: String)

@Serializable
data class AppConfig(
    val name: String,
    val consumerKey: String,
    val redirectUri: String,
    val scopes: String,
) {
    val issuesJwt = name.contains("_jwt")
    val isBeacon = name.startsWith("beacon_")
    // Config-level capability: does this app configuration rotate the refresh token on refresh?
    // Distinct from the per-user, observed RT feature marker tracked in the test base class.
    val expectsRefreshTokenRotation = name.contains("_rtr")
    val isDpop = name.contains("_dpop")
    val expectedTokenFormat = if (issuesJwt) "jwt" else "Opaque"
    val scopeList = scopes.split(" ")

    fun removeScope(scope: String): String {
        val subsetList = scopeList.toMutableList()
        subsetList.remove(scope)
        return subsetList.joinToString(separator = " ")
    }

    fun scopesToRequest(scopeSelection: ScopeSelection): String =
        when(scopeSelection) {
            ScopeSelection.EMPTY -> ""
            ScopeSelection.SUBSET -> removeScope("sfap_api")
            ScopeSelection.ALL -> scopes
        }

    fun expectedScopesGranted(scopeSelection: ScopeSelection): String =
        when(scopeSelection) {
            ScopeSelection.EMPTY, ScopeSelection.ALL -> scopes
            ScopeSelection.SUBSET -> removeScope("sfap_api")
        }

}