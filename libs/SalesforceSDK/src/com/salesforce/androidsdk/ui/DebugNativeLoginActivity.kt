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

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_FORWARD_RESULT
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.NativeLoginDiagnostics
import com.salesforce.androidsdk.auth.interfaces.NativeLoginManager
import com.salesforce.androidsdk.auth.interfaces.NativeLoginResult
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.BROWSER_FALLBACK_BUTTON
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.CANCEL_BUTTON
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.DIAGNOSTICS_LABEL
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.ERROR_DETAIL
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.LOADING_INDICATOR
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.LOGIN_BUTTON
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.LOGIN_OPTIONS_BUTTON
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.PASSWORD_FIELD
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.RESULT_LABEL
import com.salesforce.androidsdk.ui.NativeLoginDebugTestTags.USERNAME_FIELD
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the debug Native Login form shows. */
internal sealed interface DebugNativeLoginState {
    data object Idle : DebugNativeLoginState
    data object Loading : DebugNativeLoginState
    data class Failed(val result: NativeLoginResult, val details: String) : DebugNativeLoginState
    data object Success : DebugNativeLoginState
}

/**
 * Holds the debug Native Login manager and the in-flight login so both survive Activity
 * recreation.  The username is saved with [SavedStateHandle]; the password stays in memory only.
 */
internal class DebugNativeLoginViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableState = MutableStateFlow<DebugNativeLoginState>(DebugNativeLoginState.Idle)
    val state: StateFlow<DebugNativeLoginState> = mutableState.asStateFlow()

    private var usernameState by mutableStateOf(savedState.get<String>(KEY_USERNAME).orEmpty())
    var username: String
        get() = usernameState
        set(value) {
            usernameState = value
            savedState[KEY_USERNAME] = value
        }
    var password by mutableStateOf("")

    /** The manager for [configuredWith], or null when the saved configuration is incomplete. */
    var manager by mutableStateOf<NativeLoginManager?>(null)
        private set

    /** The configuration validation error shown instead of the form, or null. */
    var configurationError by mutableStateOf<String?>(null)
        private set

    private var configuredWith: NativeLoginDebugConfig? = null
    private var loginJob: Job? = null

    /** Builds the Native Login manager for [config] unless it is already in use. */
    fun configure(config: NativeLoginDebugConfig, sdkManager: SalesforceSDKManager = SalesforceSDKManager.getInstance()) {
        if (config == configuredWith && manager != null) return
        cancel()
        configuredWith = config
        configurationError = config.validationError
        manager = if (config.isComplete) sdkManager.useDebugNativeLogin(config) else null
    }

    fun login() {
        val manager = manager ?: return
        if (mutableState.value == DebugNativeLoginState.Loading) return
        mutableState.value = DebugNativeLoginState.Loading
        NativeLoginDiagnostics.reset()
        loginJob = viewModelScope.launch {
            val result = runCatching { manager.login(username, password) }
                .getOrDefault(NativeLoginResult.UnknownError)
            mutableState.value = if (result == NativeLoginResult.Success) {
                DebugNativeLoginState.Success
            } else {
                DebugNativeLoginState.Failed(
                    result,
                    NativeLoginDiagnostics.lastFailure?.let { "${result.name} ($it)" } ?: result.name,
                )
            }
        }
    }

    /** Cancels an in-flight login, which then adds no account. */
    fun cancel() {
        loginJob?.cancel()
        loginJob = null
        if (mutableState.value == DebugNativeLoginState.Loading) mutableState.value = DebugNativeLoginState.Idle
    }

    private companion object {
        const val KEY_USERNAME = "username"
    }
}

/**
 * Debug Native Login form, the login activity when the developer support Login Options select
 * Native Login (see [NativeLoginDebugSettings]).  It uses the public Native Login API as an app
 * would, and adds the result details, a redacted OAuth trace and a link back to Login Options.
 */
class DebugNativeLoginActivity : FragmentActivity() {
    private val viewModel: DebugNativeLoginViewModel by viewModels()
    private val sdkManager get() = SalesforceSDKManager.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!sdkManager.isDebugNativeLoginActive) {
            forwardToWebLogin()
            return
        }
        enableEdgeToEdge()
        viewModel.configure(sdkManager.debugLoginSettings.config)
        viewModel.manager?.biometricAuthenticationUsername?.let { viewModel.username = it }
        setContent {
            MaterialTheme(colorScheme = sdkManager.colorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DebugNativeLoginScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isFinishing || !sdkManager.loginDevMenuReload) return
        // Back from Login Options: follow the mode and configuration chosen there.
        sdkManager.loginDevMenuReload = false
        if (sdkManager.isDebugNativeLoginActive) {
            viewModel.configure(sdkManager.debugLoginSettings.config)
        } else {
            forwardToWebLogin()
        }
    }

    /** Hands this login attempt, and its caller's result, to the web/browser login activity. */
    private fun forwardToWebLogin() {
        startActivity(
            Intent(intent)
                .setClass(this, sdkManager.webViewLoginActivityClass)
                .addFlags(FLAG_ACTIVITY_FORWARD_RESULT)
        )
        finish()
    }

    private fun dismiss() {
        viewModel.cancel()
        setResult(RESULT_CANCELED)
        finish()
    }

    @Composable
    private fun DebugNativeLoginScreen() {
        val uiState by viewModel.state.collectAsState()
        val manager = viewModel.manager
        val canDismiss = manager?.shouldShowBackButton == true
        val fallbackLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) finish()
        }

        LaunchedEffect(uiState) {
            // The SDK starts the main activity when the account is created.
            if (uiState == DebugNativeLoginState.Success) finish()
        }
        BackHandler {
            when {
                canDismiss -> dismiss()
                uiState == DebugNativeLoginState.Loading -> viewModel.cancel()
                else -> moveTaskToBack(true)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Native Login (Debug)", style = MaterialTheme.typography.headlineMedium)
            if (manager == null) {
                Text(
                    "Native Login is not configured: ${viewModel.configurationError}. " +
                        "Open Login Options to set the Client ID, Native Callback URL and Community Login URL.",
                    modifier = Modifier.testTag(ERROR_DETAIL),
                )
            } else {
                OutlinedTextField(
                    value = viewModel.username,
                    onValueChange = { viewModel.username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    enabled = manager.biometricAuthenticationUsername == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth().testTag(USERNAME_FIELD),
                )
                OutlinedTextField(
                    value = viewModel.password,
                    onValueChange = { viewModel.password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().testTag(PASSWORD_FIELD),
                )
                Button(
                    onClick = viewModel::login,
                    enabled = uiState != DebugNativeLoginState.Loading,
                    modifier = Modifier.fillMaxWidth().testTag(LOGIN_BUTTON),
                ) { Text("Log In") }
                if (manager.biometricAuthenticationUsername != null) {
                    OutlinedButton(
                        onClick = { manager.presentBiometricAuth(this@DebugNativeLoginActivity) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Use Biometrics") }
                }
                OutlinedButton(
                    onClick = {
                        viewModel.cancel()
                        // The browser login keeps using the boot config and browser options.
                        fallbackLauncher.launch(
                            manager.getFallbackWebAuthenticationIntent().putExtra(EXTRA_BROWSER_FALLBACK, true)
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag(BROWSER_FALLBACK_BUTTON),
                ) { Text("Log In with Browser") }
            }

            when (val current = uiState) {
                DebugNativeLoginState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.testTag(LOADING_INDICATOR),
                )
                is DebugNativeLoginState.Failed -> {
                    Text(
                        "Login failed",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(RESULT_LABEL),
                    )
                    Text(current.details, modifier = Modifier.testTag(ERROR_DETAIL))
                    Text(
                        NativeLoginDiagnostics.summary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag(DIAGNOSTICS_LABEL),
                    )
                }
                else -> Unit
            }

            TextButton(
                onClick = { startActivity(Intent(this@DebugNativeLoginActivity, LoginOptionsActivity::class.java)) },
                modifier = Modifier.testTag(LOGIN_OPTIONS_BUTTON),
            ) { Text("Login Options") }
            if (canDismiss || uiState == DebugNativeLoginState.Loading) {
                TextButton(
                    onClick = { if (canDismiss) dismiss() else viewModel.cancel() },
                    modifier = Modifier.testTag(CANCEL_BUTTON),
                ) { Text("Cancel") }
            }
        }
    }

    companion object {
        /** Intent extra marking the web/browser login started as this form's fallback. */
        internal const val EXTRA_BROWSER_FALLBACK = "com.salesforce.androidsdk.ui.DEBUG_NATIVE_LOGIN_FALLBACK"
    }
}
