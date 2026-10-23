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
package com.salesforce.androidsdk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.MutableLiveData
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.AUTH_MODE_BROWSER
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.AUTH_MODE_NATIVE
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.AUTH_MODE_PICKER
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.CALLBACK_URL_FIELD
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.CLIENT_ID_FIELD
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.COMMUNITY_URL_FIELD
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.CONFIG_ERROR
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.IMPORT_CONFIG_APPLY_BUTTON
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.IMPORT_CONFIG_BUTTON
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.IMPORT_CONFIG_FIELD
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.USE_DPOP_TOGGLE
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.USE_HYBRID_FLOW_TOGGLE

/**
 * The Authentication Mode section of the debug Login Options: Browser or Native, and in Native
 * mode the Native Login External Client App with its own DPoP and hybrid choices.
 *
 * @param onImport Applies pasted JSON (see [NativeLoginDebugSettings.apply]); throws
 * [IllegalArgumentException] for invalid input
 */
@Composable
internal fun AuthenticationModeSection(
    mode: MutableLiveData<AuthenticationMode>,
    config: MutableLiveData<NativeLoginDebugConfig>,
    onImport: (String) -> Unit,
) {
    val selectedMode by mode.observeAsState(initial = AuthenticationMode.BROWSER)
    val nativeConfig by config.observeAsState(initial = NativeLoginDebugConfig())
    var showImport by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(PADDING_SIZE.dp)) {
        Text(text = "Authentication Mode", fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.testTag(AUTH_MODE_PICKER),
            horizontalArrangement = Arrangement.spacedBy(PADDING_SIZE.dp),
        ) {
            FilterChip(
                selected = selectedMode == AuthenticationMode.BROWSER,
                onClick = { mode.value = AuthenticationMode.BROWSER },
                label = { Text("Browser") },
                modifier = Modifier.testTag(AUTH_MODE_BROWSER),
            )
            FilterChip(
                selected = selectedMode == AuthenticationMode.NATIVE,
                onClick = { mode.value = AuthenticationMode.NATIVE },
                label = { Text("Native") },
                modifier = Modifier.testTag(AUTH_MODE_NATIVE),
            )
        }

        if (selectedMode == AuthenticationMode.NATIVE) {
            NativeConfigField("Client ID", nativeConfig.consumerKey, CLIENT_ID_FIELD) {
                config.value = nativeConfig.copy(consumerKey = it)
            }
            NativeConfigField("Native Callback URL", nativeConfig.redirectUri, CALLBACK_URL_FIELD) {
                config.value = nativeConfig.copy(redirectUri = it)
            }
            NativeConfigField("Community Login URL", nativeConfig.communityUrl, COMMUNITY_URL_FIELD) {
                config.value = nativeConfig.copy(communityUrl = it)
            }
            nativeConfig.validationError?.let {
                Text(text = it, color = colorScheme.error, modifier = Modifier.testTag(CONFIG_ERROR))
            }
            NativeOptionToggle("Use DPoP", nativeConfig.useDPoP, USE_DPOP_TOGGLE) {
                config.value = nativeConfig.copy(useDPoP = it)
            }
            NativeOptionToggle("Use Hybrid Authentication", nativeConfig.useHybridFlow, USE_HYBRID_FLOW_TOGGLE) {
                config.value = nativeConfig.copy(useHybridFlow = it)
            }
            OutlinedButton(
                onClick = { showImport = true },
                modifier = Modifier.fillMaxWidth().testTag(IMPORT_CONFIG_BUTTON),
            ) { Text("Import JSON Configuration") }
        }
    }

    if (showImport) {
        ImportConfigDialog(onImport = onImport, onDismiss = { showImport = false })
    }
}

@Composable
private fun NativeConfigField(label: String, value: String, tag: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = PADDING_SIZE.dp).testTag(tag),
    )
}

@Composable
private fun NativeOptionToggle(title: String, checked: Boolean, tag: String, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = PADDING_SIZE.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, modifier = Modifier.height(50.dp).wrapContentHeight(align = Alignment.CenterVertically))
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun ImportConfigDialog(onImport: (String) -> Unit, onDismiss: () -> Unit) {
    var json by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import JSON Configuration") },
        text = {
            Column {
                OutlinedTextField(
                    value = json,
                    onValueChange = { json = it; error = null },
                    label = { Text("consumerKey, redirectUri, communityUrl, useDPoP, useHybridFlow") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag(IMPORT_CONFIG_FIELD),
                )
                error?.let { Text(text = it, color = colorScheme.error, modifier = Modifier.testTag(CONFIG_ERROR)) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        onImport(json)
                        onDismiss()
                    } catch (e: IllegalArgumentException) {
                        error = e.message
                    }
                },
                modifier = Modifier.testTag(IMPORT_CONFIG_APPLY_BUTTON),
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
