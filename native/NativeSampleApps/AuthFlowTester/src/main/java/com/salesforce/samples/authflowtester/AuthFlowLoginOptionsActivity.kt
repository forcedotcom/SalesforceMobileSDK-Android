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
 * specific prior written permission of the copyright holder.
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

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.ui.LoginOptionsActivity
import org.json.JSONException

const val AUTH_MODE_PICKER_TAG = "authModePicker"
const val AUTH_MODE_BROWSER_TAG = "authModeBrowser"
const val AUTH_MODE_NATIVE_TAG = "authModeNative"
const val NATIVE_CLIENT_ID_TAG = "nativeClientIdField"
const val NATIVE_CALLBACK_URL_TAG = "nativeCallbackUrlField"
const val NATIVE_COMMUNITY_URL_TAG = "nativeCommunityUrlField"
const val IMPORT_NATIVE_CONFIG_FIELD_TAG = "importNativeConfigField"
const val IMPORT_NATIVE_CONFIG_BUTTON_TAG = "importNativeConfigButton"
const val NATIVE_DPOP_TOGGLE_TAG = "nativeDpopToggle"
const val NATIVE_HYBRID_TOGGLE_TAG = "nativeHybridToggle"
const val BROWSER_LOGIN_OPTIONS_BUTTON_TAG = "browserLoginOptionsButton"
const val LOGIN_OPTIONS_APPLY_TAG = "loginOptionsApplyButton"

/**
 * App-owned Login Options: picks Browser or Native login and edits the Native Login settings plus
 * the DPoP and hybrid-token toggles. The SDK's own Login Options (browser-flow settings) stays
 * reachable from here and is unchanged.
 */
class AuthFlowLoginOptionsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) { OptionsScreen() }
            }
        }
    }

    @Composable
    private fun OptionsScreen() {
        val sdkManager = SalesforceSDKManager.getInstance()
        val stored = AuthModeStore.nativeSettings
        var mode by remember { mutableStateOf(AuthModeStore.mode) }
        var clientId by remember { mutableStateOf(stored.clientId) }
        var callbackUrl by remember { mutableStateOf(stored.callbackUrl) }
        var communityUrl by remember { mutableStateOf(stored.communityUrl) }
        var useDPoP by remember { mutableStateOf(sdkManager.useDPoP) }
        var useHybrid by remember { mutableStateOf(sdkManager.useHybridAuthentication) }
        var importText by remember { mutableStateOf("") }
        var importError by remember { mutableStateOf<String?>(null) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Login Options", style = MaterialTheme.typography.headlineMedium)

            Text("Login mode")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag(AUTH_MODE_PICKER_TAG),
            ) {
                FilterChip(
                    selected = mode == AuthMode.BROWSER,
                    onClick = { mode = AuthMode.BROWSER },
                    label = { Text("Browser") },
                    modifier = Modifier.testTag(AUTH_MODE_BROWSER_TAG),
                )
                FilterChip(
                    selected = mode == AuthMode.NATIVE,
                    onClick = { mode = AuthMode.NATIVE },
                    label = { Text("Native") },
                    modifier = Modifier.testTag(AUTH_MODE_NATIVE_TAG),
                )
            }

            ToggleRow("Use DPoP", useDPoP, NATIVE_DPOP_TOGGLE_TAG) { useDPoP = it }
            ToggleRow("Use Hybrid Auth Token", useHybrid, NATIVE_HYBRID_TOGGLE_TAG) { useHybrid = it }

            Text("Native Login", style = MaterialTheme.typography.titleMedium)
            SettingField("Consumer Key", clientId, NATIVE_CLIENT_ID_TAG) { clientId = it }
            SettingField("Callback URL", callbackUrl, NATIVE_CALLBACK_URL_TAG) { callbackUrl = it }
            SettingField("Community URL", communityUrl, NATIVE_COMMUNITY_URL_TAG) { communityUrl = it }

            SettingField("Import JSON", importText, IMPORT_NATIVE_CONFIG_FIELD_TAG) { importText = it }
            OutlinedButton(
                onClick = {
                    try {
                        AuthModeStore.importNativeConfig(importText)
                        AuthModeStore.nativeSettings.let {
                            clientId = it.clientId
                            callbackUrl = it.callbackUrl
                            communityUrl = it.communityUrl
                        }
                        useDPoP = sdkManager.useDPoP
                        useHybrid = sdkManager.useHybridAuthentication
                        importError = null
                    } catch (e: JSONException) {
                        importError = "Invalid JSON"
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag(IMPORT_NATIVE_CONFIG_BUTTON_TAG),
            ) { Text("Import Native Config") }
            importError?.let { Text(it) }

            OutlinedButton(
                onClick = { startActivity(Intent(this@AuthFlowLoginOptionsActivity, LoginOptionsActivity::class.java)) },
                modifier = Modifier.fillMaxWidth().testTag(BROWSER_LOGIN_OPTIONS_BUTTON_TAG),
            ) { Text("Browser Login Options") }

            Button(
                onClick = {
                    AuthModeStore.nativeSettings = NativeLoginSettings(
                        clientId.trim(), callbackUrl.trim(), communityUrl.trim(),
                    )
                    AuthModeStore.applyMode(mode)
                    sdkManager.useDPoP = useDPoP
                    sdkManager.useHybridAuthentication = useHybrid
                    setResult(RESULT_OK)
                    finish()
                },
                modifier = Modifier.fillMaxWidth().testTag(LOGIN_OPTIONS_APPLY_TAG),
            ) { Text("Apply") }
        }
    }

    @Composable
    private fun ToggleRow(title: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title)
            Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
        }
    }

    @Composable
    private fun SettingField(label: String, value: String, tag: String, onChange: (String) -> Unit) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(tag),
        )
    }
}
