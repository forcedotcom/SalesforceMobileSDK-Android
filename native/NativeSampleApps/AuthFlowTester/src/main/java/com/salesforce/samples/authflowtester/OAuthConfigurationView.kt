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

package com.salesforce.samples.authflowtester

import android.content.res.Configuration
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import com.salesforce.androidsdk.auth.ScopeParser.Companion.toScopeParameter
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.ui.theme.sfDarkColors
import com.salesforce.androidsdk.ui.theme.sfLightColors
import com.salesforce.androidsdk.util.test.ExcludeFromJacocoGeneratedReport
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val CARD_TITLE = "OAuth Configuration"
private const val CONSUMER_KEY = "Configured Consumer Key"
private const val CALLBACK_URL = "Configured Callback URL"
private const val SCOPES = "Configured Scopes"


@Composable
fun OAuthConfigurationView() {
    val bootConfig = if (LocalInspectionMode.current) {
        null
    } else {
        BootConfig.getBootConfig(LocalContext.current)
    }
    val consumerKey = bootConfig?.remoteAccessConsumerKey
    val redirect = bootConfig?.oauthRedirectURI
    val scopes = bootConfig?.oauthScopes.toScopeParameter()

    ExpandableCard(
        title = CARD_TITLE,
        exportedJSON = generateConfigJSON(consumerKey, redirect, scopes),
    ) {
        InfoSection(title = "") {
            InfoRowView(label = CONSUMER_KEY, value = consumerKey)
            InfoRowView(label = CALLBACK_URL, value = redirect)
            InfoRowView(label = SCOPES, value = scopes)
        }
    }
}

private fun generateConfigJSON(
    consumerKey: String?,
    callbackUrl: String?,
    scopes: String?,
): String {
    return try {
        val result = buildJsonObject {
            put(CONSUMER_KEY, consumerKey)
            put(CALLBACK_URL, callbackUrl)
            put(SCOPES, scopes)
        }
        Json { prettyPrint = true }.encodeToString(result)
    } catch (_: Exception) {
        "{}"
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@ExcludeFromJacocoGeneratedReport
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun OAuthConfigurationViewPreview() {
    val scheme = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        dynamicLightColorScheme(LocalContext.current)
    }
    MaterialTheme(scheme) {
        OAuthConfigurationView()
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun OAuthConfigurationViewFallbackThemePreview() {
    val scheme = if (isSystemInDarkTheme()) {
        sfDarkColors()
    } else {
        sfLightColors()
    }
    MaterialTheme(scheme) {
        OAuthConfigurationView()
    }
}
