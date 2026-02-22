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
}

enum class KnownAppConfig {
    ECA_OPAQUE,
    ECA_JWT,
    BEACON_OPAQUE,
    BEACON_JWT,
    CA_OPAQUE,
    CA_JWT,
}

val testConfig: UITestConfig by lazy {
    Json.decodeFromString(
        string = ResourceReaderHelper.readAssetFile(
            InstrumentationRegistry.getInstrumentation().targetContext,
            /* assetFilePath = */ "ui_test_config.json",
        ) ?: throw Exception("ui_test_config.json file not found.")
    )
}

@Serializable
data class UITestConfig(val loginHosts: List<LoginHost>, val apps: List<AppConfig>) {

    fun getLoginHost(knownLoginHostConfig: KnownLoginHostConfig): LoginHost = loginHosts.find {
        (name, _, _) -> name == knownLoginHostConfig.name.toLowerCase(Locale.current)
    } ?: throw Exception("LoginHost not found.")

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