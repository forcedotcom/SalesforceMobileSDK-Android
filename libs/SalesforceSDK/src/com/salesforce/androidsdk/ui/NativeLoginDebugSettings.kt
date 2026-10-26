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

import android.content.Context
import androidx.core.content.edit
import com.salesforce.androidsdk.app.Features.FEATURE_AUTH_TYPE_NATIVE
import com.salesforce.androidsdk.app.Features.FEATURE_NATIVE_LOGIN
import com.salesforce.androidsdk.app.SalesforceSDKManager
import org.json.JSONException
import org.json.JSONObject
import java.net.URI

/** How the SDK presents login when the debug Login Options select it. */
enum class AuthenticationMode {
    /** The SDK's web/browser login, configured by the boot config and the browser options. */
    BROWSER,

    /** The SDK's debug Native Login form, configured by [NativeLoginDebugConfig]. */
    NATIVE,
}

/**
 * The External Client App used by the debug Native Login form.  Kept apart from the boot config,
 * which the browser login (including the Native Login browser fallback) keeps using.
 *
 * @param consumerKey The consumer key of an External Client App enabled for headless login
 * @param redirectUri The callback URL of that app, an HTTPS URL
 * @param communityUrl The Experience Cloud site URL, including its path, used for Native Login
 * @param useDPoP True to bind Native Login sessions with DPoP
 * @param useHybridFlow True to request hybrid tokens from the Native Login token exchange
 */
data class NativeLoginDebugConfig(
    val consumerKey: String = "",
    val redirectUri: String = "",
    val communityUrl: String = "",
    val useDPoP: Boolean = true,
    val useHybridFlow: Boolean = true,
) {
    /** The first problem with this configuration, or null when Native Login can use it. */
    val validationError: String?
        get() = when {
            consumerKey.isBlank() -> "Client ID is required"
            !redirectUri.isHttpsUrl() -> "Native Callback URL must be an https:// URL"
            !communityUrl.isHttpsUrl() -> "Community Login URL must be an https:// URL"
            else -> null
        }

    /** True when Native Login can use this configuration. */
    val isComplete get() = validationError == null

    private fun String.isHttpsUrl() = runCatching {
        URI(trim()).let { it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() }
    }.getOrDefault(false)
}

/**
 * Debug-only choice between browser login and the SDK's built-in Native Login form, edited in the
 * Login Options screen of the developer support menu and persisted across launches.
 *
 * The settings are inert, and [mode] always reads [AuthenticationMode.BROWSER], unless developer
 * support is enabled and the app registered no Native Login activity of its own.  When [mode] is
 * [AuthenticationMode.NATIVE], [SalesforceSDKManager.loginActivityClass] is
 * [DebugNativeLoginActivity].
 *
 * The Native Login DPoP and hybrid choices live in [config]; they do not change
 * [SalesforceSDKManager.useDPoP] or [SalesforceSDKManager.useHybridAuthentication], which stay the
 * browser login values.
 */
class NativeLoginDebugSettings internal constructor(
    context: Context,
    private val sdkManager: SalesforceSDKManager,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** True when the settings apply: developer support is on and the app has no native login activity. */
    val isAvailable: Boolean
        get() = sdkManager.isDevSupportEnabled() && sdkManager.nativeLoginActivity == null

    /**
     * The selected authentication mode.  Leaving [AuthenticationMode.NATIVE] clears the Native Login
     * feature markers so later browser logins do not report them.  Writes are ignored when the
     * settings are not [isAvailable].
     */
    var mode: AuthenticationMode
        get() = if (!isAvailable) {
            AuthenticationMode.BROWSER
        } else {
            prefs.getString(KEY_MODE, null)
                ?.let { stored -> AuthenticationMode.entries.firstOrNull { it.name == stored } }
                ?: AuthenticationMode.BROWSER
        }
        set(value) {
            if (!isAvailable) return
            val previous = mode
            prefs.edit { putString(KEY_MODE, value.name) }
            if (previous == AuthenticationMode.NATIVE && value != AuthenticationMode.NATIVE) {
                sdkManager.unregisterUsedAppFeature(FEATURE_NATIVE_LOGIN)
                sdkManager.unregisterUsedAppFeature(FEATURE_AUTH_TYPE_NATIVE)
            }
        }

    /** The Native Login External Client App.  Writes are ignored when the settings are not [isAvailable]. */
    var config: NativeLoginDebugConfig
        get() = NativeLoginDebugConfig(
            consumerKey = prefs.getString(KEY_CONSUMER_KEY, "").orEmpty(),
            redirectUri = prefs.getString(KEY_REDIRECT_URI, "").orEmpty(),
            communityUrl = prefs.getString(KEY_COMMUNITY_URL, "").orEmpty(),
            useDPoP = prefs.getBoolean(KEY_USE_DPOP, true),
            useHybridFlow = prefs.getBoolean(KEY_USE_HYBRID_FLOW, true),
        )
        set(value) {
            if (!isAvailable) return
            prefs.edit {
                putString(KEY_CONSUMER_KEY, value.consumerKey.trim())
                putString(KEY_REDIRECT_URI, value.redirectUri.trim())
                putString(KEY_COMMUNITY_URL, value.communityUrl.trim())
                putBoolean(KEY_USE_DPOP, value.useDPoP)
                putBoolean(KEY_USE_HYBRID_FLOW, value.useHybridFlow)
            }
        }

    /**
     * Applies a JSON configuration, using the same keys as iOS: `consumerKey`, `redirectUri`,
     * `communityUrl`, `useDPoP`, `useHybridFlow` and `authenticationMode` (`native` or `browser`).
     * Absent keys keep their current value.  Nothing is saved when the JSON or the resulting Native
     * Login configuration is invalid.
     *
     * @throws IllegalArgumentException When [json] is malformed, a value has the wrong type, or
     * the resulting mode is Native with an incomplete configuration
     * @throws IllegalStateException When the settings are not [isAvailable]
     */
    fun apply(json: String) {
        check(isAvailable) { "Native Login debug settings require developer support" }
        val values = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw IllegalArgumentException("Native Login configuration is not a JSON object", e)
        }
        val current = config
        val updated = try {
            NativeLoginDebugConfig(
                consumerKey = values.optStringStrict(JSON_CONSUMER_KEY) ?: current.consumerKey,
                redirectUri = values.optStringStrict(JSON_REDIRECT_URI) ?: current.redirectUri,
                communityUrl = values.optStringStrict(JSON_COMMUNITY_URL) ?: current.communityUrl,
                useDPoP = if (values.has(JSON_USE_DPOP)) values.getBoolean(JSON_USE_DPOP) else current.useDPoP,
                useHybridFlow = if (values.has(JSON_USE_HYBRID_FLOW)) {
                    values.getBoolean(JSON_USE_HYBRID_FLOW)
                } else {
                    current.useHybridFlow
                },
            )
        } catch (e: JSONException) {
            throw IllegalArgumentException("Native Login configuration has a value of the wrong type", e)
        }
        val updatedMode = values.optStringStrict(JSON_AUTHENTICATION_MODE)?.let { name ->
            AuthenticationMode.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown authenticationMode '$name'")
        } ?: mode
        if (updatedMode == AuthenticationMode.NATIVE) {
            updated.validationError?.let { throw IllegalArgumentException(it) }
        }
        config = updated
        mode = updatedMode
    }

    /** Restores the defaults: browser mode and no Native Login configuration. */
    fun reset() {
        mode = AuthenticationMode.BROWSER
        prefs.edit { clear() }
    }

    private fun JSONObject.optStringStrict(key: String): String? =
        if (has(key)) getString(key).trim() else null

    companion object {
        private const val PREFS_NAME = "sf__native_login_debug_settings"
        private const val KEY_MODE = "mode"
        private const val KEY_CONSUMER_KEY = "consumerKey"
        private const val KEY_REDIRECT_URI = "redirectUri"
        private const val KEY_COMMUNITY_URL = "communityUrl"
        private const val KEY_USE_DPOP = "useDPoP"
        private const val KEY_USE_HYBRID_FLOW = "useHybridFlow"

        /** JSON key of the External Client App consumer key. */
        const val JSON_CONSUMER_KEY = "consumerKey"

        /** JSON key of the External Client App callback URL. */
        const val JSON_REDIRECT_URI = "redirectUri"

        /** JSON key of the community login URL. */
        const val JSON_COMMUNITY_URL = "communityUrl"

        /** JSON key of the Native Login DPoP choice. */
        const val JSON_USE_DPOP = "useDPoP"

        /** JSON key of the Native Login hybrid token choice. */
        const val JSON_USE_HYBRID_FLOW = "useHybridFlow"

        /** JSON key of the authentication mode, `native` or `browser`. */
        const val JSON_AUTHENTICATION_MODE = "authenticationMode"
    }
}

/**
 * Test tags of the debug Login Options authentication mode section and of
 * [DebugNativeLoginActivity], shared with the iOS accessibility identifiers.
 */
object NativeLoginDebugTestTags {
    const val AUTH_MODE_PICKER = "authModePicker"
    const val AUTH_MODE_BROWSER = "authModeBrowser"
    const val AUTH_MODE_NATIVE = "authModeNative"
    const val CLIENT_ID_FIELD = "nativeClientIdField"
    const val CALLBACK_URL_FIELD = "nativeCallbackUrlField"
    const val COMMUNITY_URL_FIELD = "nativeCommunityUrlField"
    const val CONFIG_ERROR = "nativeConfigError"
    const val IMPORT_CONFIG_BUTTON = "importNativeConfigButton"
    const val IMPORT_CONFIG_FIELD = "importNativeConfigField"
    const val IMPORT_CONFIG_APPLY_BUTTON = "importNativeConfigApplyButton"
    const val USE_DPOP_TOGGLE = "useDPoPToggle"
    const val USE_HYBRID_FLOW_TOGGLE = "useHybridFlowToggle"
    const val USERNAME_FIELD = "nativeUsernameField"
    const val PASSWORD_FIELD = "nativePasswordField"
    const val LOGIN_BUTTON = "nativeLoginButton"
    const val CANCEL_BUTTON = "nativeCancelButton"
    const val BROWSER_FALLBACK_BUTTON = "nativeBrowserFallbackButton"
    const val LOGIN_OPTIONS_BUTTON = "nativeLoginOptionsButton"
    const val LOADING_INDICATOR = "nativeLoadingIndicator"
    const val RESULT_LABEL = "nativeResultLabel"
    const val ERROR_DETAIL = "nativeErrorDetail"
    const val DIAGNOSTICS_LABEL = "nativeDiagnosticsLabel"
}
