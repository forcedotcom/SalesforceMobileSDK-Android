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
package com.salesforce.androidsdk.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.app.SalesforceSDKManager.Companion.USER_ACCOUNT_KEY
import com.salesforce.androidsdk.auth.OAuth2.LogoutReason

/**
 * Listens for the logout complete event, and acts on it.
 */
abstract class LogoutCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == SalesforceSDKManager.LOGOUT_COMPLETE_INTENT_ACTION) {
            val reason = intent.getStringExtra(SalesforceSDKManager.LOGOUT_REASON_KEY)?.let {
                LogoutReason.valueOf(it.uppercase())
            } ?: LogoutReason.UNKNOWN
            val userAccount = intent.getBundleExtra(USER_ACCOUNT_KEY)?.let { bundle ->
                UserAccount(bundle)
            }
            @Suppress("DEPRECATION")
            onLogoutComplete(reason)
            onLogoutComplete(reason, userAccount)
        }
    }

    /**
     * Called when logout is complete.
     *
     * @param reason The reason for the logout. If no reason is available, [LogoutReason.UNKNOWN] is used.
     */
    @Deprecated(
        message = "To be removed in 14.0. Use onLogoutComplete method that includes the userAccount that was logged out.",
        replaceWith = ReplaceWith("onLogoutComplete(reason: LogoutReason, userAccount: UserAccount?)")
    )
    protected open fun onLogoutComplete(reason: LogoutReason) {}

    /**
     * Called when logout is complete.
     *
     * @param reason The reason for the logout. If no reason is available, [LogoutReason.UNKNOWN] is used.
     * @param userAccount The user account that was logged out. If no user account is available, null is used.
     */
    protected open fun onLogoutComplete(reason: LogoutReason, userAccount: UserAccount?) {}
}