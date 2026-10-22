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

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.salesforce.androidsdk.ui.LoginOptionsExtension
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

/**
 * Adds the Authentication Mode picker (Browser or Native) and the Native Login settings to the top
 * of the SDK's debug Login Options screen. In Native mode the SDK's browser-only options are hidden
 * and the screen shows the native client settings with their own DPoP and hybrid toggles; in
 * Browser mode only the picker is added. Every change is stored right away through
 * [AuthModeStore], so there is no Apply button: closing the screen applies the mode.
 */
object AuthFlowLoginOptionsExtension : LoginOptionsExtension {

    private var mode by mutableStateOf(AuthMode.BROWSER)
    private var modeWhenOpened = AuthMode.BROWSER

    override val showsBrowserOptions: Boolean
        get() = mode == AuthMode.BROWSER

    @Composable
    override fun TopContent() {
        // Read on each opening of the screen (the composition starts fresh), not on recomposition.
        remember {
            mode = AuthModeStore.mode
            modeWhenOpened = mode
        }

        Column {
            Text(
                "Authentication Mode",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp).testTag(AUTH_MODE_PICKER_TAG),
            ) {
                FilterChip(
                    selected = mode == AuthMode.BROWSER,
                    onClick = { select(AuthMode.BROWSER) },
                    label = { Text("Browser") },
                    modifier = Modifier.testTag(AUTH_MODE_BROWSER_TAG),
                )
                FilterChip(
                    selected = mode == AuthMode.NATIVE,
                    onClick = { select(AuthMode.NATIVE) },
                    label = { Text("Native") },
                    modifier = Modifier.testTag(AUTH_MODE_NATIVE_TAG),
                )
            }
            if (mode == AuthMode.NATIVE) NativeOptions()
            HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
        }
    }

    private fun select(newMode: AuthMode) {
        AuthModeStore.applyMode(newMode)
        mode = newMode
    }

    @Composable
    private fun NativeOptions() {
        val stored = AuthModeStore.nativeSettings
        var clientId by remember { mutableStateOf(stored.clientId) }
        var callbackUrl by remember { mutableStateOf(stored.callbackUrl) }
        var communityUrl by remember { mutableStateOf(stored.communityUrl) }
        var useDPoP by remember { mutableStateOf(AuthModeStore.nativeUseDPoP) }
        var useHybrid by remember { mutableStateOf(AuthModeStore.nativeUseHybrid) }
        var importText by remember { mutableStateOf("") }
        var importError by remember { mutableStateOf<String?>(null) }

        fun save() {
            AuthModeStore.nativeSettings = NativeLoginSettings(clientId.trim(), callbackUrl.trim(), communityUrl.trim())
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text("Native Login", style = MaterialTheme.typography.titleMedium)
            ToggleRow("Use DPoP", useDPoP, NATIVE_DPOP_TOGGLE_TAG) {
                useDPoP = it
                AuthModeStore.nativeUseDPoP = it
            }
            ToggleRow("Use Hybrid Auth Token", useHybrid, NATIVE_HYBRID_TOGGLE_TAG) {
                useHybrid = it
                AuthModeStore.nativeUseHybrid = it
            }
            SettingField("Client ID", clientId, NATIVE_CLIENT_ID_TAG) { clientId = it; save() }
            SettingField("Native Callback URL", callbackUrl, NATIVE_CALLBACK_URL_TAG) { callbackUrl = it; save() }
            SettingField("Community Login URL", communityUrl, NATIVE_COMMUNITY_URL_TAG) { communityUrl = it; save() }
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
                        useDPoP = AuthModeStore.nativeUseDPoP
                        useHybrid = AuthModeStore.nativeUseHybrid
                        importError = null
                    } catch (_: JSONException) {
                        importError = "Invalid JSON"
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag(IMPORT_NATIVE_CONFIG_BUTTON_TAG),
            ) { Text("Import Native Config") }
            importError?.let { Text(it) }
        }
    }

    @Composable
    private fun ToggleRow(title: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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

    /**
     * Browser to Native: the SDK's login activity is on screen below this one, so replace the task
     * with the router. (Native to Browser and Native to Native are handled by [NativeLoginActivity]
     * when it resumes.)
     */
    override fun onLoginOptionsDismissed(activity: Activity) {
        if (modeWhenOpened == AuthMode.BROWSER && AuthModeStore.mode == AuthMode.NATIVE) {
            activity.startActivity(
                Intent(activity, LoginRouterActivity::class.java)
                    .putExtra(NativeLoginActivity.EXTRA_RELAUNCH_MAIN, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
        }
        modeWhenOpened = AuthModeStore.mode
    }
}
