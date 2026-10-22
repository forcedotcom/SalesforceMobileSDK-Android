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
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.ui.LoginOptionsActivity
import com.salesforce.androidsdk.auth.interfaces.NativeLoginManager
import com.salesforce.androidsdk.auth.interfaces.NativeLoginResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val NATIVE_USERNAME_TAG = "nativeUsernameField"
const val NATIVE_PASSWORD_TAG = "nativePasswordField"
const val NATIVE_LOGIN_BUTTON_TAG = "nativeLoginButton"
const val NATIVE_CANCEL_BUTTON_TAG = "nativeCancelButton"
const val NATIVE_BROWSER_FALLBACK_TAG = "nativeBrowserFallbackButton"
const val NATIVE_LOGIN_OPTIONS_TAG = "nativeLoginOptionsButton"
const val NATIVE_LOADING_TAG = "nativeLoadingIndicator"
const val NATIVE_RESULT_TAG = "nativeResultLabel"
const val NATIVE_ERROR_DETAIL_TAG = "nativeErrorDetail"
const val NATIVE_DIAGNOSTICS_TAG = "nativeDiagnosticsLabel"

/** What the Native Login form is showing. */
sealed interface NativeLoginUiState {
    data object Idle : NativeLoginUiState
    data object Loading : NativeLoginUiState
    data class Failed(val result: NativeLoginResult?, val message: String) : NativeLoginUiState
    data object Succeeded : NativeLoginUiState
}

/**
 * Holds the in-flight login so it survives Activity recreation. The username is saved with
 * [SavedStateHandle]; the password is deliberately kept only in memory.
 */
class NativeLoginViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableState = MutableStateFlow<NativeLoginUiState>(NativeLoginUiState.Idle)
    val state: StateFlow<NativeLoginUiState> = mutableState.asStateFlow()

    var username: String
        get() = savedState[KEY_USERNAME] ?: ""
        set(value) { savedState[KEY_USERNAME] = value }
    var password by mutableStateOf("")

    fun login(manager: NativeLoginManager) {
        if (mutableState.value == NativeLoginUiState.Loading) return
        mutableState.value = NativeLoginUiState.Loading
        NativeLoginDiagnostics.reset()
        viewModelScope.launch {
            val result = runCatching { manager.login(username, password) }
            mutableState.value = result.fold(
                onSuccess = { it.toUiState() },
                onFailure = { NativeLoginUiState.Failed(null, "Login threw ${it.javaClass.simpleName}") },
            )
        }
    }

    fun reset() {
        mutableState.value = NativeLoginUiState.Idle
    }

    private fun NativeLoginResult.toUiState() = when (this) {
        NativeLoginResult.Success -> NativeLoginUiState.Succeeded
        else -> NativeLoginUiState.Failed(this, name)
    }

    private companion object {
        const val KEY_USERNAME = "username"
    }
}

/**
 * App-owned Native Login form. Reached through [LoginRouterActivity] when [AuthModeStore.mode] is
 * [AuthMode.NATIVE]. The SDK Native Login API is used as documented; the screen only adds test tags
 * and a redacted OAuth trace.
 */
class NativeLoginActivity : FragmentActivity() {
    private val viewModel: NativeLoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = ensureNativeLoginManager()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NativeLoginScreen(manager)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from Login Options: follow the mode and settings chosen there.
        val settings = AuthModeStore.nativeSettings
        when {
            AuthModeStore.mode != AuthMode.NATIVE -> {
                startActivity(Intent(this, LoginRouterActivity::class.java))
                finish()
            }
            settings.isComplete && settings != registeredSettings -> recreate()
        }
    }

    /** Ends the login screen; relaunches the main activity when the task was replaced for this login. */
    private fun finishLogin() {
        if (intent.getBooleanExtra(EXTRA_RELAUNCH_MAIN, false)) {
            startActivity(Intent(this, SalesforceSDKManager.getInstance().mainActivityClass))
        }
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    @Composable
    private fun NativeLoginScreen(manager: NativeLoginManager?) {
        val uiState by viewModel.state.collectAsState()
        var username by remember { mutableStateOf(viewModel.username) }
        val fallbackLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) finishLogin()
        }

        LaunchedEffect(uiState) {
            if (uiState == NativeLoginUiState.Succeeded) finishLogin()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Native Login", style = MaterialTheme.typography.headlineMedium)
            if (manager == null) {
                Text(
                    "Native Login is not configured. Open Login Options to set the consumer key, callback URL and community URL.",
                    modifier = Modifier.testTag(NATIVE_ERROR_DETAIL_TAG),
                )
            } else {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; viewModel.username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth().testTag(NATIVE_USERNAME_TAG),
                )
                OutlinedTextField(
                    value = viewModel.password,
                    onValueChange = { viewModel.password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().testTag(NATIVE_PASSWORD_TAG),
                )
                Button(
                    onClick = { viewModel.login(manager) },
                    enabled = uiState != NativeLoginUiState.Loading,
                    modifier = Modifier.fillMaxWidth().testTag(NATIVE_LOGIN_BUTTON_TAG),
                ) { Text("Log In") }
                OutlinedButton(
                    onClick = { fallbackLauncher.launch(manager.getFallbackWebAuthenticationIntent()) },
                    modifier = Modifier.fillMaxWidth().testTag(NATIVE_BROWSER_FALLBACK_TAG),
                ) { Text("Log In with Browser") }
            }

            when (val current = uiState) {
                NativeLoginUiState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.testTag(NATIVE_LOADING_TAG),
                )
                is NativeLoginUiState.Failed -> {
                    Text("Login failed", modifier = Modifier.testTag(NATIVE_RESULT_TAG))
                    Text(current.message, modifier = Modifier.testTag(NATIVE_ERROR_DETAIL_TAG))
                    Text(
                        NativeLoginDiagnostics.summary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag(NATIVE_DIAGNOSTICS_TAG),
                    )
                }
                else -> Unit
            }

            Spacer(Modifier.height(8.dp))
            // The SDK's Login Options screen is a debug-only developer tool, like its Dev Support menu.
            if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                TextButton(
                    onClick = { startActivity(Intent(this@NativeLoginActivity, LoginOptionsActivity::class.java)) },
                    modifier = Modifier.testTag(NATIVE_LOGIN_OPTIONS_TAG),
                ) { Text("Login Options") }
            }
            if (manager?.shouldShowBackButton == true) {
                TextButton(
                    onClick = { setResult(RESULT_CANCELED); finishLogin() },
                    modifier = Modifier.testTag(NATIVE_CANCEL_BUTTON_TAG),
                ) { Text("Cancel") }
            }
        }
    }

    companion object {
        /** Intent extra: the task was replaced for this login, so relaunch the main activity when it ends. */
        const val EXTRA_RELAUNCH_MAIN = "RELAUNCH_MAIN"

        /** Settings last passed to `useNativeLogin`, so recreation does not rebuild the manager. */
        private var registeredSettings: NativeLoginSettings? = null

        /** Returns the SDK manager for the stored settings, or null when they are incomplete. */
        private fun ensureNativeLoginManager(): NativeLoginManager? {
            val settings = AuthModeStore.nativeSettings
            if (!settings.isComplete) return null
            val sdkManager = SalesforceSDKManager.getInstance()
            if (registeredSettings != settings || sdkManager.nativeLoginManager == null) {
                sdkManager.useNativeLogin(settings.clientId, settings.callbackUrl, settings.communityUrl)
                registeredSettings = settings
            }
            return sdkManager.nativeLoginManager
        }
    }
}
