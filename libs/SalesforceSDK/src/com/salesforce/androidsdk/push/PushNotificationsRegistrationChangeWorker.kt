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
import androidx.work.ListenableWorker.Result.failure
import androidx.work.ListenableWorker.Result.success
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager.getInstance
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
        val userAccount = inputData.getString("USER_ACCOUNT")?.let { userAccountJson ->
            UserAccount(JSONObject(userAccountJson))
        } /* User account is optional where null specifies all accounts */

        // Instantiate push notifications registrar...
        val pushNotificationsRegistrar = getInstance().pushServiceType.newInstance()

        // ...Determine scope of user accounts when...
        when (userAccount) {

            // ...The input data didn't provide a user account...
            null ->
                // ...Change push notification registration for all user accounts.
                getInstance().userAccountManager.authenticatedUsers?.forEach { nextUserAccount ->
                    pushNotificationsRegistrar.performRegistrationChange(
                        pushNotificationsRegistrationAction == Register,
                        nextUserAccount
                    )
                }

            // ...The input data provided a specific user account...
            else ->
                // ...Change push notification registration for the specified user account.
                pushNotificationsRegistrar.performRegistrationChange(
                    pushNotificationsRegistrationAction == Register,
                    userAccount
                )
        }

        return success()
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
