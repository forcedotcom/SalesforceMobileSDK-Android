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

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.salesforce.androidsdk.app.Features
import com.salesforce.androidsdk.app.SalesforceSDKManager
import org.json.JSONException
import org.json.JSONObject

/** How the next login attempt is presented: SDK web/browser login or the app's own native form. */
enum class AuthMode { BROWSER, NATIVE }

/** Native Login client settings. DPoP and hybrid are kept apart, see [AuthModeStore.nativeUseDPoP]. */
data class NativeLoginSettings(
    val clientId: String = "",
    val callbackUrl: String = "",
    val communityUrl: String = "",
) {
    val isComplete get() = clientId.isNotBlank() && callbackUrl.isNotBlank() && communityUrl.isNotBlank()
}

/**
 * App-owned store for the login mode (Browser or Native) and the Native Login settings.
 *
 * Native and Browser keep separate DPoP and hybrid-token choices. [SalesforceSDKManager] carries
 * the values of the active mode: entering Native saves the browser values and applies the native
 * ones, leaving Native restores the browser values.
 *
 * Persisted so a cold restart, such as process death mid-login, lands on the same login surface.
 * [LoginRouterActivity] reads [mode] for every login attempt, so changing it needs no restart.
 */
object AuthModeStore {
    private const val PREFS_NAME = "authflowtester_login_options"
    private const val KEY_MODE = "mode"
    private const val KEY_CLIENT_ID = "nativeClientId"
    private const val KEY_CALLBACK_URL = "nativeCallbackUrl"
    private const val KEY_COMMUNITY_URL = "nativeCommunityUrl"
    private const val KEY_NATIVE_DPOP = "nativeUseDPoP"
    private const val KEY_NATIVE_HYBRID = "nativeUseHybrid"
    private const val KEY_BROWSER_DPOP = "browserUseDPoP"
    private const val KEY_BROWSER_HYBRID = "browserUseHybrid"

    // JSON keys accepted by [importNativeConfig]; shared with the iOS AuthFlowTester.
    const val JSON_CONSUMER_KEY = "consumerKey"
    const val JSON_REDIRECT_URI = "redirectUri"
    const val JSON_COMMUNITY_URL = "communityUrl"
    const val JSON_USE_DPOP = "useDPoP"
    const val JSON_USE_HYBRID_FLOW = "useHybridFlow"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var mode: AuthMode
        get() = prefs.getString(KEY_MODE, null)?.let { runCatching { AuthMode.valueOf(it) }.getOrNull() }
            ?: AuthMode.BROWSER
        set(value) = prefs.edit { putString(KEY_MODE, value.name) }

    var nativeSettings: NativeLoginSettings
        get() = NativeLoginSettings(
            clientId = prefs.getString(KEY_CLIENT_ID, "").orEmpty(),
            callbackUrl = prefs.getString(KEY_CALLBACK_URL, "").orEmpty(),
            communityUrl = prefs.getString(KEY_COMMUNITY_URL, "").orEmpty(),
        )
        set(value) = prefs.edit {
            putString(KEY_CLIENT_ID, value.clientId)
            putString(KEY_CALLBACK_URL, value.callbackUrl)
            putString(KEY_COMMUNITY_URL, value.communityUrl)
        }

    /** DPoP choice for Native Login, independent of the browser one. */
    var nativeUseDPoP: Boolean
        get() = prefs.getBoolean(KEY_NATIVE_DPOP, true)
        set(value) {
            prefs.edit { putBoolean(KEY_NATIVE_DPOP, value) }
            if (mode == AuthMode.NATIVE) SalesforceSDKManager.getInstance().useDPoP = value
        }

    /** Hybrid-token choice for Native Login, independent of the browser one. */
    var nativeUseHybrid: Boolean
        get() = prefs.getBoolean(KEY_NATIVE_HYBRID, true)
        set(value) {
            prefs.edit { putBoolean(KEY_NATIVE_HYBRID, value) }
            if (mode == AuthMode.NATIVE) SalesforceSDKManager.getInstance().useHybridAuthentication = value
        }

    /**
     * Stores [newMode] and swaps the DPoP and hybrid values on [SalesforceSDKManager]; leaving
     * Native also clears its SDK feature markers so browser users do not carry them.
     */
    fun applyMode(newMode: AuthMode) {
        val previous = mode
        val sdkManager = SalesforceSDKManager.getInstance()
        if (newMode == AuthMode.NATIVE && previous != AuthMode.NATIVE) {
            prefs.edit {
                putBoolean(KEY_BROWSER_DPOP, sdkManager.useDPoP)
                putBoolean(KEY_BROWSER_HYBRID, sdkManager.useHybridAuthentication)
            }
        }
        mode = newMode
        if (newMode == AuthMode.NATIVE) {
            sdkManager.useDPoP = nativeUseDPoP
            sdkManager.useHybridAuthentication = nativeUseHybrid
        } else if (previous == AuthMode.NATIVE) {
            if (prefs.contains(KEY_BROWSER_DPOP)) sdkManager.useDPoP = prefs.getBoolean(KEY_BROWSER_DPOP, true)
            if (prefs.contains(KEY_BROWSER_HYBRID)) {
                sdkManager.useHybridAuthentication = prefs.getBoolean(KEY_BROWSER_HYBRID, true)
            }
            with(sdkManager) {
                unregisterUsedAppFeature(Features.FEATURE_NATIVE_LOGIN)
                unregisterUsedAppFeature(Features.FEATURE_AUTH_TYPE_NATIVE)
            }
        }
    }

    /** Back to the default: Browser mode, no Native Login settings. */
    fun reset() {
        applyMode(AuthMode.BROWSER)
        prefs.edit { clear() }
    }

    /**
     * Applies a JSON configuration to the native settings and, when present, to the native DPoP
     * and hybrid options.
     *
     * @throws JSONException When [json] is malformed.
     */
    fun importNativeConfig(json: String) {
        val values = JSONObject(json)
        nativeSettings = NativeLoginSettings(
            clientId = values.optString(JSON_CONSUMER_KEY, nativeSettings.clientId),
            callbackUrl = values.optString(JSON_REDIRECT_URI, nativeSettings.callbackUrl),
            communityUrl = values.optString(JSON_COMMUNITY_URL, nativeSettings.communityUrl),
        )
        if (values.has(JSON_USE_DPOP)) nativeUseDPoP = values.getBoolean(JSON_USE_DPOP)
        if (values.has(JSON_USE_HYBRID_FLOW)) nativeUseHybrid = values.getBoolean(JSON_USE_HYBRID_FLOW)
    }
}
