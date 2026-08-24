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
package com.salesforce.androidsdk.accounts

import android.content.Intent
import com.salesforce.androidsdk.accounts.UserAccountManager.getInstance
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.ScopeParser.Companion.toScopeParser
import com.salesforce.androidsdk.auth.dpop.DPoPKeyManager
import com.salesforce.androidsdk.auth.dpop.DPoPNonceCache
import com.salesforce.androidsdk.config.OAuthConfig
import com.salesforce.androidsdk.ui.TokenMigrationActivity
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.launch
import java.util.UUID

const val TAG = "UserAccountManager"

/**
 * Attempts to migrate the [userAccount] to the provided Connected App or
 * External Client Application [appConfig].
 *
 * This might cause the approve/deny screen to be presented to the user to authorize the
 * new app. If successful a new set of credentials (refresh token, access token) are obtained
 * and replace the existing credentials for the user.
 *
 * This overload preserves the original (pre-DPoP) behavior: the migrated session defers to the
 * global [SalesforceSDKManager.useDPoP] flag for its DPoP posture. To express a per-call DPoP
 * intent, use the [useDPoP]-carrying overload; for the common same-config, DPoP-upgrade case,
 * see [upgradeToDPoP].
 */
fun UserAccountManager.migrateRefreshToken(
    userAccount: UserAccount? = getInstance().currentUser,
    appConfig: OAuthConfig,
    onMigrationSuccess: (userAccount: UserAccount) -> Unit,
    onMigrationError: (error: String, errorDesc: String?, e: Throwable?) -> Unit,
) = migrateRefreshToken(
    userAccount = userAccount,
    appConfig = appConfig,
    useDPoP = null,
    onMigrationSuccess = onMigrationSuccess,
    onMigrationError = onMigrationError,
)

/**
 * Attempts to migrate the [userAccount] to the provided Connected App or
 * External Client Application [appConfig], with an explicit per-call DPoP intent.
 *
 * This might cause the approve/deny screen to be presented to the user to authorize the
 * new app. If successful a new set of credentials (refresh token, access token) are obtained
 * and replace the existing credentials for the user.
 *
 * [useDPoP] expresses the DPoP intent for this specific migration call: `true` binds the
 * migrated session to DPoP, `false` migrates it unbound, and `null` defers to the global
 * [SalesforceSDKManager.useDPoP] flag (the behavior of the overload without this parameter).
 * See [upgradeToDPoP] for the common same-config, `useDPoP = true` case.
 */
@Suppress("UnusedReceiverParameter")
fun UserAccountManager.migrateRefreshToken(
    userAccount: UserAccount? = getInstance().currentUser,
    appConfig: OAuthConfig,
    useDPoP: Boolean?,
    onMigrationSuccess: (userAccount: UserAccount) -> Unit,
    onMigrationError: (error: String, errorDesc: String?, e: Throwable?) -> Unit,
) {
    val loggedOnSuccess: (userAccount: UserAccount) -> Unit = { user ->
        SalesforceSDKLogger.i(TAG, "Token Migration Successful \n\nUser ${user.username} " +
                "(${user.instanceServer}) successfully migrated to: \n$appConfig.")
        onMigrationSuccess.invoke(user)
    }
    val userId = userAccount?.userId
    val orgId = userAccount?.orgId

    if (userId == null || orgId == null) {
        val message = "User account, userId or orgId is null."
        SalesforceSDKLogger.e(TAG, message)
        onMigrationError(message, null, null)
        return
    }

    val callbackKey = MigrationCallbackRegistry.register(
        callbacks = MigrationCallbackRegistry.MigrationCallbacks(
            onMigrationSuccess = loggedOnSuccess,
            onMigrationError = onMigrationError,
        )
    )

    with(SalesforceSDKManager.getInstance().appContext) {
        startActivity(
            Intent(/* packageContext = */ this, TokenMigrationActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(TokenMigrationActivity.EXTRA_ORG_ID, orgId)
                putExtra(TokenMigrationActivity.EXTRA_USER_ID, userId)
                putExtra(TokenMigrationActivity.EXTRA_OAUTH_CONFIG, appConfig)
                putExtra(TokenMigrationActivity.EXTRA_CALLBACK_ID, callbackKey)
                // Only carry a per-call DPoP intent when the caller expressed one; omitting the
                // extra lets TokenMigrationActivity defer to the global SalesforceSDKManager.useDPoP
                // flag (prior behavior).
                useDPoP?.let { putExtra(TokenMigrationActivity.EXTRA_USE_DPOP, it) }
            }
        )
    }
}

/**
 * Upgrades the [userAccount]'s existing Bearer (non-DPoP) refresh token to a DPoP-bound one,
 * in place — same consumer key, redirect URI, and scopes the account already uses. This is a
 * same-config convenience over [migrateRefreshToken] with `useDPoP = true`: no re-consent is
 * expected because nothing about the connected app / External Client App configuration changes.
 *
 * The redirect URI used is the one persisted on [userAccount] at login time (the exact value
 * the connected app / External Client App was configured with for this user); it only falls
 * back to resolving the OAuth configuration for the account's login server for accounts that
 * were persisted before the redirect URI was captured on [UserAccount].
 *
 * This works regardless of the global [SalesforceSDKManager.useDPoP] flag: that flag only sets
 * the default DPoP posture for brand-new logins, while this call is an explicit action on an
 * already-authenticated session. Callers wanting to migrate to a *different* consumer key,
 * redirect URI, or scopes (or to explicitly downgrade a DPoP-bound session back to Bearer)
 * should call [migrateRefreshToken] directly with their own [OAuthConfig] and `useDPoP` value.
 *
 * Note: [onFailure] (and [onSuccess]) may be invoked off the main thread — the synchronous
 * null-check failure below runs on the caller's thread, but the OAuth-config resolution and
 * migration below it run on [Default]. Callers that touch UI from these callbacks must marshal
 * to the main thread themselves.
 *
 * No-op: if [userAccount] is already DPoP-bound, there's nothing to upgrade — [onSuccess] is
 * invoked synchronously with the unchanged account and no migration is attempted.
 */
@Suppress("UnusedReceiverParameter")
fun UserAccountManager.upgradeToDPoP(
    userAccount: UserAccount,
    onSuccess: (userAccount: UserAccount) -> Unit,
    onFailure: (error: String, errorDesc: String?, e: Throwable?) -> Unit,
) {
    if (userAccount.tokenType == DPoPKeyManager.DPOP_TOKEN_TYPE) {
        onSuccess(userAccount)
        return
    }

    val clientId = userAccount.clientId
    val loginServer = userAccount.loginServer

    if (clientId == null || loginServer == null) {
        val message = "User account clientId or loginServer is null."
        SalesforceSDKLogger.e(TAG, message)
        onFailure(message, null, null)
        return
    }

    // Prefer the redirect URI persisted on the account at login time: it's the exact value the
    // connected app / External Client App was configured with for this user, and it doesn't
    // change over time. Only fall back to resolving the OAuth configuration for the user's login
    // server (debug override, per-host app config, or boot config) for accounts persisted before
    // redirect URI was captured on UserAccount. Either way, keep the user's own consumer key and
    // scopes so the upgrade is a true same-config, in-place operation.
    CoroutineScope(Default).launch {
        runCatching {
            val persistedRedirectUri = userAccount.redirectUri
            val redirectUri = if (!persistedRedirectUri.isNullOrBlank()) {
                persistedRedirectUri
            } else {
                SalesforceSDKManager.getInstance()
                    .resolveOAuthConfigForLoginServer(loginServer)
                    .redirectUri
            }

            OAuthConfig(
                consumerKey = clientId,
                redirectUri = redirectUri,
                scopes = userAccount.scope?.toScopeParser()?.scopes?.toList(),
            )
        }.fold(
            onSuccess = { appConfig ->
                migrateRefreshToken(
                    userAccount = userAccount,
                    appConfig = appConfig,
                    useDPoP = true,
                    onMigrationSuccess = onSuccess,
                    onMigrationError = onFailure,
                )
            },
            onFailure = { e ->
                val message = "Failed to resolve OAuth configuration for login server."
                SalesforceSDKLogger.e(TAG, message, e)
                onFailure(message, e.message, e)
            },
        )
    }
}

/**
 * Downgrades the [userAccount]'s existing DPoP-bound refresh token to a Bearer (non-DPoP) one,
 * in place — same consumer key, redirect URI, and scopes the account already uses. This is a
 * same-config convenience over [migrateRefreshToken] with `useDPoP = false`: no re-consent is
 * expected because nothing about the connected app / External Client App configuration changes.
 *
 * This works regardless of the global [SalesforceSDKManager.useDPoP] flag: that flag only sets
 * the default DPoP posture for brand-new logins, while this call is an explicit action on an
 * already-authenticated session — an app can leave the global flag on and still roll one user
 * back to Bearer. The connected app / External Client App must accept Bearer tokens for the
 * downgrade to succeed; a DPoP-enforcing app will reject the resulting session.
 *
 * On success, the pre-downgrade DPoP key pair and DPoP nonce-cache entries (keyed by the
 * account's pre-migration [UserAccount.credentialsIdentifier], since migration mints a new one)
 * are deleted — mirroring the teardown [SalesforceSDKManager] performs on logout. On failure or
 * cancellation, that state is left untouched so the original DPoP-bound session keeps working.
 *
 * Callers wanting to migrate to a *different* consumer key, redirect URI, or scopes (or to
 * explicitly upgrade a Bearer session to DPoP) should call [migrateRefreshToken] directly with
 * their own [OAuthConfig] and `useDPoP` value, or see [upgradeToDPoP].
 *
 * Note: [onFailure] (and [onSuccess]) may be invoked off the main thread — the synchronous
 * null-check failure below runs on the caller's thread, but the OAuth-config resolution and
 * migration below it run on [Default]. Callers that touch UI from these callbacks must marshal
 * to the main thread themselves.
 *
 * No-op: if [userAccount] is already Bearer (non-DPoP), there's nothing to downgrade —
 * [onSuccess] is invoked synchronously with the unchanged account, no migration is attempted,
 * and no DPoP state cleanup runs (there's none to clean up).
 */
@Suppress("UnusedReceiverParameter")
fun UserAccountManager.downgradeFromDPoP(
    userAccount: UserAccount,
    onSuccess: (userAccount: UserAccount) -> Unit,
    onFailure: (error: String, errorDesc: String?, e: Throwable?) -> Unit,
) {
    if (userAccount.tokenType != DPoPKeyManager.DPOP_TOKEN_TYPE) {
        onSuccess(userAccount)
        return
    }

    val clientId = userAccount.clientId
    val loginServer = userAccount.loginServer

    if (clientId == null || loginServer == null) {
        val message = "User account clientId or loginServer is null."
        SalesforceSDKLogger.e(TAG, message)
        onFailure(message, null, null)
        return
    }

    // Capture the pre-migration credentials identifier now: migration mints a NEW identifier for
    // the account, so reading userAccount.credentialsIdentifier after the migration completes
    // would return the wrong (or a null) value. This is what obsolete-DPoP-state cleanup below is
    // keyed on. The account is guaranteed DPoP-bound here (the non-DPoP case returned early above),
    // so cleanup always applies on success.
    val oldCredId = userAccount.credentialsIdentifier

    val onSuccessWithCleanup: (userAccount: UserAccount) -> Unit = { migratedUser ->
        oldCredId?.takeIf { it.isNotEmpty() }?.let { id ->
            runCatching {
                DPoPKeyManager.deleteKeyPair(DPoPKeyManager.aliasForCredentialsIdentifier(id))
                DPoPNonceCache.clear(id)
            }.onFailure { e ->
                SalesforceSDKLogger.w(TAG, "Failed to delete obsolete DPoP state on downgrade", e)
            }
        }
        onSuccess(migratedUser)
    }

    // Prefer the redirect URI persisted on the account at login time: it's the exact value the
    // connected app / External Client App was configured with for this user, and it doesn't
    // change over time. Only fall back to resolving the OAuth configuration for the user's login
    // server (debug override, per-host app config, or boot config) for accounts persisted before
    // redirect URI was captured on UserAccount. Either way, keep the user's own consumer key and
    // scopes so the downgrade is a true same-config, in-place operation.
    CoroutineScope(Default).launch {
        runCatching {
            val persistedRedirectUri = userAccount.redirectUri
            val redirectUri = if (!persistedRedirectUri.isNullOrBlank()) {
                persistedRedirectUri
            } else {
                SalesforceSDKManager.getInstance()
                    .resolveOAuthConfigForLoginServer(loginServer)
                    .redirectUri
            }

            OAuthConfig(
                consumerKey = clientId,
                redirectUri = redirectUri,
                scopes = userAccount.scope?.toScopeParser()?.scopes?.toList(),
            )
        }.fold(
            onSuccess = { appConfig ->
                migrateRefreshToken(
                    userAccount = userAccount,
                    appConfig = appConfig,
                    useDPoP = false,
                    onMigrationSuccess = onSuccessWithCleanup,
                    onMigrationError = onFailure,
                )
            },
            onFailure = { e ->
                val message = "Failed to resolve OAuth configuration for login server."
                SalesforceSDKLogger.e(TAG, message, e)
                onFailure(message, e.message, e)
            },
        )
    }
}

/*
    This mechanism is used to pass a _string_ id to the Activity to retrieve callback functions.

    Lambda functions may appear Parcelable/Serializable but since we cannot guarantee the
    content are they should not be passed.  For instance, if the lambda function contains
    compose state an exception will be thrown.
 */
internal object MigrationCallbackRegistry {
    private val callbacks = mutableMapOf<String, MigrationCallbacks>()

    data class MigrationCallbacks(
        val onMigrationSuccess: (UserAccount) -> Unit,
        val onMigrationError: (String, String?, Throwable?) -> Unit
    )

    fun register(callbacks: MigrationCallbacks): String {
        val key = UUID.randomUUID().toString()
        this.callbacks[key] = callbacks
        return key
    }

    fun consume(key: String): MigrationCallbacks? = callbacks.remove(key)
}