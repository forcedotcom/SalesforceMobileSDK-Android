/*
 * Copyright (c) 2016-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.push

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.work.ListenableWorker.Result.failure
import androidx.work.ListenableWorker.Result.success
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.push.PushNotificationsRegistrationChangeWorker.PushNotificationsRegistrationAction.Register
import org.json.JSONObject

/**
 * An Android background tasks worker for push notifications.
 * This class is intended to be instantiated by the background tasks work
 * manager.
 *
 * Use [PushService.enqueuePushNotificationsRegistrationWork] to enqueue a push
 * service worker.
 *
 * @param context The Android context provided by the work manager
 * @param workerParams The worker parameters provided by the work manager
 * @see <a href='https://developer.android.com/guide/background'>Android Background Tasks</a>
 */
internal class PushNotificationsRegistrationChangeWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(
    context,
    workerParams
) {

    override fun doWork(): Result {

        // Fetch worker input data for registration action and user account.
        val pushNotificationsRegistrationAction = PushNotificationsRegistrationAction.valueOf(
            inputData.getString("ACTION") ?: return failure() /* Action is required */
        )

        // Resolve which authenticated accounts this work targets, failing on a
        // bad required input rather than widening the scope to all users.
        val targetAccounts = when (val resolution = resolveTargetAccounts()) {
            is TargetAccounts.Fail -> return failure()
            is TargetAccounts.Accounts -> resolution.users
        }

        /*
         * Instantiate push notifications registrar and change registration for
         * each targeted account.
         */
        val pushNotificationsRegistrar = SalesforceSDKManager.getInstance().pushServiceType.newInstance()
        targetAccounts.forEach { userAccount ->
            pushNotificationsRegistrar.performRegistrationChange(
                pushNotificationsRegistrationAction == Register,
                userAccount
            )
        }

        return success()
    }

    /**
     * Resolves the set of authenticated accounts this work request targets from
     * its input data, without performing any registration change.
     *
     * The enqueuer persists only the non-sensitive org id and user id, so the
     * full user account (with its auth token, refresh token, and session
     * cookies) is never written to WorkManager's unencrypted storage. Accounts
     * are re-resolved from secure storage here:
     *
     * - Absent identifiers legitimately specify all authenticated users.
     * - Present identifiers that no longer resolve to a stored account (for
     *   example, the user logged out before this work ran) are a bad required
     *   input: [TargetAccounts.Fail] rather than silently widening the scope to
     *   all users. This is safe because the SDK re-enqueues registration work
     *   with REPLACE on the next app foreground or login, so a discarded worker
     *   is automatically replaced.
     * - A legacy pre-14.0 payload (see below) is migrated to discrete
     *   identifiers before resolution.
     *
     * Legacy payload migration: SDKs before 14.0 persisted the full user
     * account JSON under "USER_ACCOUNT" instead of the discrete org id and user
     * id. Such a job — most importantly a single-user deregister — can survive
     * an in-place app update and run against this worker, whose absent-identifiers
     * case would otherwise treat it as "all authenticated users" and widen a
     * one-user deregister to every account. Migrate by reading ONLY the
     * identifiers from the legacy blob (never the auth token, refresh token, or
     * session cookies it also carries) and re-resolving the account from secure
     * storage. A present-but-unparseable legacy payload is a bad required input:
     * [TargetAccounts.Fail] rather than widen the scope.
     */
    @VisibleForTesting
    internal fun resolveTargetAccounts(): TargetAccounts {
        var orgId = inputData.getString("ORG_ID")
        var userId = inputData.getString("USER_ID")
        val userAccountManager = SalesforceSDKManager.getInstance().userAccountManager

        if (orgId == null && userId == null) {
            inputData.getString("USER_ACCOUNT")?.let { legacyUserAccountJson ->
                val legacyUserAccount = runCatching {
                    UserAccount(JSONObject(legacyUserAccountJson))
                }.getOrNull() ?: return TargetAccounts.Fail /* Unparseable legacy payload */
                orgId = legacyUserAccount.orgId
                userId = legacyUserAccount.userId
            }
        }

        return if (orgId == null && userId == null) {
            // Absent identifiers specify all authenticated users.
            TargetAccounts.Accounts(userAccountManager.authenticatedUsers ?: emptyList())
        } else {
            val userAccount = userAccountManager.getUserFromOrgAndUserId(orgId, userId)
                ?: return TargetAccounts.Fail /* Unresolvable account */
            TargetAccounts.Accounts(listOf(userAccount))
        }
    }

    /**
     * The outcome of resolving a work request's target accounts: either the
     * exact set of accounts to process, or a signal to fail the work.
     */
    @VisibleForTesting
    internal sealed interface TargetAccounts {

        /** A bad required input — the work must fail without widening scope. */
        object Fail : TargetAccounts

        /** The exact set of authenticated accounts to process (may be empty). */
        data class Accounts(val users: List<UserAccount>) : TargetAccounts
    }

    /**
     * The available push notifications registration action types.
     */
    internal enum class PushNotificationsRegistrationAction {
        /** Deregister */
        Deregister,

        /** Register with retry if needed */
        Register
    }
}
