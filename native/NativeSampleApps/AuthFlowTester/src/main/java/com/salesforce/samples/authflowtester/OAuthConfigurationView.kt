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

package com.salesforce.samples.authflowtester

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.RequiresApi
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salesforce.androidsdk.auth.ScopeParser.Companion.toScopeParameter
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.ui.theme.sfDarkColors
import com.salesforce.androidsdk.ui.theme.sfLightColors
import com.salesforce.androidsdk.util.test.ExcludeFromJacocoGeneratedReport
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val PADDING_SIZE = 12

object OAuthConfigLabels {
    const val CONSUMER_KEY = "Configured Consumer Key"
    const val CALLBACK_URL = "Configured Callback URL"
    const val SCOPES = "Configured Scopes"
}

@Composable
fun OAuthConfigurationView(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    var showExportAlert by remember { mutableStateOf(false) }
    var exportedJSON by remember { mutableStateOf("") }
    val bootConfig = if (isPreview) {
        null
    } else {
        BootConfig.getBootConfig(context)
    }
    val consumerKey = when {
        bootConfig == null -> "preview consumer key"
        bootConfig.remoteAccessConsumerKey.isBlank() -> "(none)"
        else -> bootConfig.remoteAccessConsumerKey
    }
    val redirect = when {
        bootConfig == null -> "preview redirect uri"
        bootConfig.remoteAccessConsumerKey.isBlank() -> "(none)"
        else -> bootConfig.oauthRedirectURI
    }
    val scopes = when {
        bootConfig == null -> "preview scopes"
        bootConfig.remoteAccessConsumerKey.isBlank() -> "(none)"
        else -> bootConfig.oauthScopes.toScopeParameter()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onExpandedChange(!isExpanded) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OAuth Configuration",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                IconButton(
                    onClick = {
                        exportedJSON = generateConfigJSON(
                            consumerKey,
                            redirect,
                            scopes,
                        )
                        showExportAlert = true
                    }
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Export OAuth Config",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                IconButton(
                    onClick = { onExpandedChange(!isExpanded) }
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.rotate(if (isExpanded) 180f else 0f),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            
            if (isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    InfoRowView(
                        label = OAuthConfigLabels.CONSUMER_KEY,
                        value = consumerKey,
                        isSensitive = true,
                    )

                    InfoRowView(
                        label = OAuthConfigLabels.CALLBACK_URL,
                        value = redirect,
                    )

                    InfoRowView(
                        label = OAuthConfigLabels.SCOPES,
                        value = scopes,
                    )
                }
            }
        }
    }
    
    if (showExportAlert) {
        AlertDialog(
            onDismissRequest = { showExportAlert = false },
            title = { Text("OAuth Configuration JSON") },
            text = { Text(
                text = exportedJSON,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) },
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
            }
        )
    }
}

@Composable
fun InfoItem(name: String, value: String?) {
    val coroutineScope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PADDING_SIZE.dp, end = PADDING_SIZE.dp, top = PADDING_SIZE.dp)
            .clickable {
                // Copy non-null and non-boolean values to clipboard.
                value?.let {
                    if (it.toBooleanStrictOrNull() == null) {
                        val clipData = ClipData.newPlainText(name, it)
                        coroutineScope.launch {
                            clipboard.setClipEntry(ClipEntry(clipData))
                        }
                    }
                }
            }
    ) {
        Text(
            text = name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = value ?: "",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier,
        )
    }
}

private fun generateConfigJSON(
    consumerKey: String,
    callbackUrl: String,
    scopes: String
): String {
    return try {
        val result = buildJsonObject {
            put(OAuthConfigLabels.CONSUMER_KEY, consumerKey)
            put(OAuthConfigLabels.CALLBACK_URL, callbackUrl)
            put(OAuthConfigLabels.SCOPES, scopes)
        }
        Json { prettyPrint = true }.encodeToString(result)
    } catch (_: Exception) {
        "{}"
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("OAuth Configuration JSON", text)
    clipboard.setPrimaryClip(clip)
}

@RequiresApi(Build.VERSION_CODES.S)
@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun InfoItemPreview() {
    val scheme = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        dynamicLightColorScheme(LocalContext.current)
    }
    MaterialTheme(scheme) {
        InfoItem("name", "some value")
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@ExcludeFromJacocoGeneratedReport
@Preview
@Composable
private fun OAuthConfigurationViewPreview() {
    val scheme = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        dynamicLightColorScheme(LocalContext.current)
    }
    MaterialTheme(scheme) {
        OAuthConfigurationView(
            isExpanded = false,
            onExpandedChange = {}
        )
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@ExcludeFromJacocoGeneratedReport
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun OAuthConfigurationViewPreviewExpanded() {
    val scheme = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        dynamicLightColorScheme(LocalContext.current)
    }
    MaterialTheme(scheme) {
        OAuthConfigurationView(
            isExpanded = true,
            onExpandedChange = {}
        )
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
        OAuthConfigurationView(
            isExpanded = true,
            onExpandedChange = {}
        )
    }
}
