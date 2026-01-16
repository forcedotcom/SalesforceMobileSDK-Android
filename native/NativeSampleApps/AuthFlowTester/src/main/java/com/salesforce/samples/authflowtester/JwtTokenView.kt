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
import androidx.compose.ui.tooling.preview.Preview
import com.salesforce.androidsdk.auth.JwtAccessToken
import com.salesforce.androidsdk.ui.theme.sfDarkColors
import com.salesforce.androidsdk.ui.theme.sfLightColors
import com.salesforce.androidsdk.util.test.ExcludeFromJacocoGeneratedReport
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private const val CARD_TITLE = "JWT Details"

// Section titles
private const val HEADER = "Header"
private const val PAYLOAD = "Payload"

// Header fields
private const val ALGORITHM = "Algorithm (alg)"
private const val TYPE = "Type (typ)"
private const val KEY_ID = "Key ID (kid)"
private const val TOKEN_TYPE = "Token Type (tty)"
private const val TENANT_KEY = "Tenant Key (tnk)"
private const val VERSION = "Version (ver)"

// Payload fields
private const val AUDIENCE = "Audience (aud)"
private const val EXPIRATION_DATE = "Expiration Date (exp)"
private const val ISSUER = "Issuer (iss)"
private const val SUBJECT = "Subject (sub)"
private const val SCOPES = "Scopes (scp)"
private const val CLIENT_ID = "Client ID (client_id)"

@Composable
fun JwtTokenView(jwtToken: JwtAccessToken?) {
    ExpandableCard(
        title = CARD_TITLE,
        exportedJSON = generateJwtJSON(jwtToken)
    ) {
        InfoSection(title = HEADER) {
            InfoRowView(label = ALGORITHM, value = jwtToken?.header?.algorithn)
            InfoRowView(label = TYPE, value = jwtToken?.header?.type)
            InfoRowView(label = KEY_ID, value = jwtToken?.header?.keyId)
            InfoRowView(label = TOKEN_TYPE, value = jwtToken?.header?.tokenType)
            InfoRowView(label = TENANT_KEY, value = jwtToken?.header?.tenantKey)
            InfoRowView(label = VERSION, value = jwtToken?.header?.version)
        }

        InfoSection(title = PAYLOAD) {
            InfoRowView(label = AUDIENCE, value = jwtToken?.payload?.audience?.joinToString(", "))
            InfoRowView(label = EXPIRATION_DATE, value = jwtToken?.expirationDate().toString())
            InfoRowView(label = ISSUER, value = jwtToken?.payload?.issuer)
            InfoRowView(label = SUBJECT, value = jwtToken?.payload?.subject)
            InfoRowView(label = SCOPES, value = jwtToken?.payload?.scopes)
            InfoRowView(label = CLIENT_ID, value = jwtToken?.payload?.clientId, isSensitive = true)
        }
    }
}

private fun generateJwtJSON(jwtToken: JwtAccessToken?): String {
    if (jwtToken == null) return "{}"

    return try {
        val result = buildJsonObject {
            putJsonObject(HEADER) {
                with(jwtToken.header) {
                    put(ALGORITHM, algorithn)
                    put(TYPE, type)
                    put(KEY_ID, keyId)
                    put(TOKEN_TYPE, tokenType)
                    put(TENANT_KEY, tenantKey)
                    put(VERSION, version)
                }
            }
            
            putJsonObject(PAYLOAD) {
                with(jwtToken.payload) {
                    put(AUDIENCE, audience?.joinToString(", "))
                    put(EXPIRATION_DATE, jwtToken.expirationDate().toString())
                    put(ISSUER, issuer)
                    put(SUBJECT, subject)
                    put(SCOPES, scopes)
                    put(CLIENT_ID, clientId)
                }
            }
        }
        Json { prettyPrint = true }.encodeToString(result)
    } catch (_: Exception) {
        "{}"
    }
}


@RequiresApi(Build.VERSION_CODES.S)
@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun JwtTokenViewPreview() {
    val scheme = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        dynamicLightColorScheme(LocalContext.current)
    }

    // Use Interactive mode to see preview data
    MaterialTheme(scheme) {
        JwtTokenView(jwtToken = null)
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun JwtTokenViewFallbackThemePreview() {
    val scheme = if (isSystemInDarkTheme()) {
        sfDarkColors()
    } else {
        sfLightColors()
    }

    MaterialTheme(scheme) {
        JwtTokenView(jwtToken = null)
    }
}
