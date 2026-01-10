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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.androidsdk.auth.JwtAccessToken
import com.salesforce.androidsdk.ui.theme.sfDarkColors
import com.salesforce.androidsdk.ui.theme.sfLightColors
import com.salesforce.androidsdk.util.test.ExcludeFromJacocoGeneratedReport
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.text.DateFormat
import java.util.Date

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
fun JwtTokenView(
    jwtToken: JwtAccessToken?,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var showExportAlert by remember { mutableStateOf(false) }
    var exportedJSON by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onExpandedChange(!isExpanded) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "JWT Access Token Details",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                IconButton(
                    onClick = {
                        exportedJSON = generateJwtJSON(jwtToken)
                        showExportAlert = true
                    }
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Export JWT Token",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                IconButton(
                    onClick = { onExpandedChange(!isExpanded) }
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.rotate(if (isExpanded) 180f else 0f),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            if (isExpanded) {
                if (jwtToken != null) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // JWT Header
                        Text(
                            text = "${HEADER}:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        JwtHeaderView(token = jwtToken)

                        // JWT Payload
                        Text(
                            text = "${PAYLOAD}:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        JwtPayloadView(token = jwtToken)
                    }
                } else {
                    Text(
                        text = "No JWT Token available",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }

    if (showExportAlert) {

        AlertDialog(
            onDismissRequest = { showExportAlert = false },
            title = { Text("JWT Token JSON") },
            text = {
                Text(
                    text = exportedJSON,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        copyToClipboard(context, exportedJSON)
                        showExportAlert = false
                    }
                ) {
                    Text("Copy to Clipboard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportAlert = false }) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
fun JwtHeaderView(token: JwtAccessToken) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val header = token.header
        header.algorithn?.let { InfoRowView("${ALGORITHM}:", it) }
        header.type?.let { InfoRowView("${TYPE}:", it) }
        header.keyId?.let { InfoRowView("${KEY_ID}:", it) }
        header.tokenType?.let { InfoRowView("${TOKEN_TYPE}:", it) }
        header.tenantKey?.let { InfoRowView("${TENANT_KEY}:", it) }
        header.version?.let { InfoRowView("${VERSION}:", it) }
    }
}

@Composable
fun JwtPayloadView(token: JwtAccessToken) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val payload = token.payload
        
        payload.audience?.let { 
            InfoRowView("${AUDIENCE}:", it.joinToString(", "))
        }
        
        token.expirationDate()?.let {
            InfoRowView("${EXPIRATION_DATE}:", formatDate(it))
        }

        payload.issuer?.let { InfoRowView("${ISSUER}:", it) }
        payload.subject?.let { InfoRowView("${SUBJECT}:", it) }
        payload.scopes?.let { InfoRowView("${SCOPES}:", it) }
        payload.clientId?.let { 
            InfoRowView("${CLIENT_ID}:", it, isSensitive = true)
        }
    }
}

private fun formatDate(date: Date): String {
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(date)
}

private fun generateJwtJSON(jwtToken: JwtAccessToken?): String {
    if (jwtToken == null) return "{}"

    return try {
        val result = buildJsonObject {
            putJsonObject(HEADER) {
                val header = jwtToken.header
                header.algorithn?.let { put(ALGORITHM, it) }
                header.type?.let { put(TYPE, it) }
                header.keyId?.let { put(KEY_ID, it) }
                header.tokenType?.let { put(TOKEN_TYPE, it) }
                header.tenantKey?.let { put(TENANT_KEY, it) }
                header.version?.let { put(VERSION, it) }
            }
            
            putJsonObject(PAYLOAD) {
                val payload = jwtToken.payload
                payload.audience?.let { put(AUDIENCE, it.joinToString(", ")) }
                jwtToken.expirationDate()?.let { put(EXPIRATION_DATE, formatDate(it)) }
                payload.issuer?.let { put(ISSUER, it) }
                payload.subject?.let { put(SUBJECT, it) }
                payload.scopes?.let { put(SCOPES, it) }
                payload.clientId?.let { put(CLIENT_ID, it) }
            }
        }
        Json { prettyPrint = true }.encodeToString(result)
    } catch (_: Exception) {
        "{}"
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("JWT Token JSON", text)
    clipboard.setPrimaryClip(clip)
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
    MaterialTheme(scheme) {
        JwtTokenView(
            jwtToken = null,
            isExpanded = true,
            onExpandedChange = {}
        )
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
        JwtTokenView(
            jwtToken = null,
            isExpanded = true,
            onExpandedChange = {}
        )
    }
}
