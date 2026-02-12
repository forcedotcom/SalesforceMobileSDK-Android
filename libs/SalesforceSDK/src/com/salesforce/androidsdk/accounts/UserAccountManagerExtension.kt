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
import com.salesforce.androidsdk.config.OAuthConfig
import com.salesforce.androidsdk.ui.TokenMigrationActivity
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import java.util.UUID

const val TAG = "UserAccountManager"

/**
 * Attempts to migrate the [userAccount] to the provided Connected App or
 * External Client Application [appConfig].
 *
 * This might cause the approve/deny screen to be presented to the user to authorize the
 * new app. If successful a new set of credentials (refresh token, access token) are obtained
 * and replace the existing credentials for the user.
 */
@Suppress("UnusedReceiverParameter")
fun UserAccountManager.migrateRefreshToken(
    userAccount: UserAccount? = getInstance().currentUser,
    appConfig: OAuthConfig,
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
            }
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